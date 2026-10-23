package org.fossify.home.dikciz

import android.os.SystemClock
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Pull-based sessions keep the existing process runner usable across ordinary bindings. */
internal object DikcizShellSessions {
    const val TYPE_START = "shellSessionStart"
    const val TYPE_READ = "shellSessionRead"
    const val TYPE_LIST = "shellSessionsList"
    const val TYPE_STOP = "shellSessionStop"
    const val TYPE_FORGET = "shellSessionForget"
    private const val KEY_ID = "streamId"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_WAIT = "waitMilliseconds"
    private const val KEY_BUFFER = "bufferBytes"
    private const val KEY_EVENTS = "maximumEvents"
    private const val KEY_RETENTION = "retentionMinutes"
    private const val KEY_STATE = "state"
    private const val DEFAULT_TIMEOUT = 30_000L
    private const val DEFAULT_BUFFER = 1_048_576
    private const val MINIMUM_BUFFER = 65_536
    private const val MAXIMUM_BUFFER = 8_388_608
    private const val MAXIMUM_TOTAL_BUFFER = 33_554_432L
    private const val MAXIMUM_RECORDS = 16
    private const val DEFAULT_EVENTS = 1024
    private const val MAXIMUM_EVENTS = 4096
    private const val DEFAULT_RETENTION = 60
    private const val MAXIMUM_RETENTION = 1440
    private const val MINUTE_MILLISECONDS = 60_000L
    private const val MAXIMUM_PAGE_BYTES = 393_216
    private const val EVENT_OVERHEAD_BYTES = 1024
    private const val OWNER = "ordinary-shell-sessions"
    private val records = LinkedHashMap<String, Session>()
    private val workers = Executors.newFixedThreadPool(DikcizShellStreams.MAXIMUM_STREAM_COUNT) { action ->
        Thread(action, "dikciz-shell-session").apply { isDaemon = true }
    }
    private var runner: DikcizShellStreams? = null
    @Volatile private var currentLogger: DikcizLogger? = null

    fun attachLogger(logger: DikcizLogger) {
        currentLogger = logger
    }

    fun execute(logger: DikcizLogger, type: String, request: JSONObject): JSONObject {
        attachLogger(logger)
        return when (type) {
            TYPE_START -> start(logger, request)
            TYPE_READ -> session(request).read(request)
            TYPE_LIST -> list(request)
            TYPE_STOP -> {
                val current = session(request)
                val active = !current.isFinished()
                if (active) current.stream.cancel(DikcizShellStreams.STATE_CANCELLED)
                current.metadata().put("cancellationRequested", active)
            }
            TYPE_FORGET -> forget(request)
            else -> DikcizNativeCommands.invalid()
        }
    }

    @Synchronized
    private fun start(logger: DikcizLogger, request: JSONObject): JSONObject {
        prune()
        if (records.size >= MAXIMUM_RECORDS) DikcizNativeCommands.failure("shell_session_capacity", "Forget a terminal shell session or wait for retention expiry")
        val capacity = request.optInt(KEY_BUFFER, DEFAULT_BUFFER)
        if (records.values.sumOf { it.capacity.toLong() } + capacity > MAXIMUM_TOTAL_BUFFER) {
            DikcizNativeCommands.failure("shell_session_buffer_capacity", "Retained shell buffer budget reached")
        }
        val manager = runner ?: DikcizShellStreams(logger, { requireNotNull(currentLogger) }).also { runner = it }
        val command = JSONObject()
        DikcizShellStreams.START_FIELDS.filter(request::has).forEach { command.put(it, request.get(it)) }
        if (!command.has(DikcizShellStreams.KEY_TIMEOUT)) command.put(DikcizShellStreams.KEY_TIMEOUT, DEFAULT_TIMEOUT)
        val stream = manager.open(OWNER, command)
        val current = Session(stream, capacity, request.optInt(KEY_EVENTS, DEFAULT_EVENTS), request.optInt(KEY_RETENTION, DEFAULT_RETENTION), request.optBoolean(DikcizShellStreams.KEY_ROOT, true))
        current.append(stream.description())
        records[stream.id] = current
        workers.execute {
            try {
                val result = stream.consume({ true }, {}, current::append)
                current.append(result)
            } catch (exception: Exception) {
                logger.error("shell_session_failed", mapOf("stream_id" to stream.id, "error_class" to exception.javaClass.simpleName))
                stream.cancel(DikcizShellStreams.STATE_FAILED)
                current.fail()
            }
        }
        return current.metadata()
    }

    @Synchronized
    private fun session(request: JSONObject): Session {
        prune()
        return records[request.getString(KEY_ID)]
            ?: DikcizNativeCommands.failure("shell_session_not_found", "No retained shell session with that ID")
    }

    @Synchronized
    private fun list(request: JSONObject): JSONObject {
        prune()
        val rows = records.values.toList().asReversed().map(Session::metadata)
        return DikcizNativeCommands.page(rows, request).put("total", rows.size)
    }

    @Synchronized
    private fun forget(request: JSONObject): JSONObject {
        val current = session(request)
        if (!current.isFinished()) DikcizNativeCommands.failure("shell_session_active", "Stop and observe completion before forgetting a shell session")
        records.remove(current.stream.id)
        return JSONObject().put(KEY_ID, current.stream.id).put(KEY_STATE, "forgotten")
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        records.entries.removeAll { it.value.expired(now) }
    }

    private class StoredEvent(val sequence: Long, val json: String, val size: Int)

    private class Session(
        val stream: DikcizShellStreams.Stream,
        val capacity: Int,
        private val maximumEvents: Int,
        private val retentionMinutes: Int,
        private val root: Boolean,
    ) {
        private val events = ArrayDeque<StoredEvent>()
        private var changed = CountDownLatch(1)
        private val created = System.currentTimeMillis()
        private var finishedAt: Long? = null
        private var nextSequence = 0L
        private var bufferedBytes = 0
        private var outputBytes = 0L
        private var state = DikcizShellStreams.STATE_STARTING
        private var exitCode: Any = JSONObject.NULL
        private var error: String? = null

        @Synchronized
        fun append(event: JSONObject) {
            if (finishedAt != null) return
            val sequence = event.getLong(DikcizShellStreams.KEY_SEQUENCE)
            check(sequence == nextSequence)
            val json = event.toString()
            val size = json.toByteArray(Charsets.UTF_8).size
            check(size <= capacity)
            events.addLast(StoredEvent(sequence, json, size))
            nextSequence++
            bufferedBytes += size
            while (bufferedBytes > capacity || events.size > maximumEvents) bufferedBytes -= events.removeFirst().size
            when (event.getString(DikcizShellStreams.KEY_EVENT)) {
                DikcizShellStreams.EVENT_COMPLETE -> {
                    state = event.getString(KEY_STATE)
                    exitCode = event.opt(DikcizShellStreams.KEY_EXIT_CODE) ?: JSONObject.NULL
                    error = event.optString("code").takeIf(String::isNotEmpty)
                    finishedAt = System.currentTimeMillis()
                }
                DikcizShellStreams.EVENT_OUTPUT -> {
                    state = "running"
                    outputBytes += Base64.getDecoder().decode(event.getString(DikcizShellStreams.KEY_OUTPUT_BASE64)).size
                }
                DikcizShellStreams.EVENT_HEARTBEAT -> state = "running"
            }
            changed.countDown()
            changed = CountDownLatch(1)
        }

        @Synchronized
        fun fail() {
            if (finishedAt != null) return
            append(JSONObject().put(KEY_ID, stream.id).put(DikcizShellStreams.KEY_SEQUENCE, nextSequence)
                .put(DikcizShellStreams.KEY_EVENT, DikcizShellStreams.EVENT_COMPLETE)
                .put(KEY_STATE, DikcizShellStreams.STATE_FAILED).put("code", "shell_session_failed"))
        }

        @Synchronized
        fun isFinished() = finishedAt != null

        @Synchronized
        fun expired(now: Long) = finishedAt?.let { now - it >= retentionMinutes * MINUTE_MILLISECONDS } == true

        @Synchronized
        fun metadata(): JSONObject = JSONObject().put(KEY_ID, stream.id).put(KEY_STATE, state)
            .put("active", finishedAt == null).put("root", root).put("createdAtMilliseconds", created)
            .put("finishedAtMilliseconds", finishedAt ?: JSONObject.NULL)
            .put("expiresAtMilliseconds", finishedAt?.plus(retentionMinutes * MINUTE_MILLISECONDS) ?: JSONObject.NULL)
            .put(KEY_BUFFER, capacity).put("bufferedBytes", bufferedBytes)
            .put("firstSequence", events.peekFirst()?.sequence ?: nextSequence)
            .put("nextSequence", nextSequence).put("outputBytes", outputBytes)
            .put("exitCode", exitCode).put("code", error ?: JSONObject.NULL)

        fun read(request: JSONObject): JSONObject {
            val cursor = request.optLong(KEY_CURSOR, 0)
            val deadline = SystemClock.elapsedRealtime() + request.optLong(KEY_WAIT, 0)
            while (true) {
                val waiter = synchronized(this) {
                    if (cursor > nextSequence) DikcizNativeCommands.invalid("Shell session cursor is ahead of produced events")
                    if (cursor < nextSequence || finishedAt != null || SystemClock.elapsedRealtime() >= deadline) return page(cursor, request)
                    changed
                }
                waiter.await(maxOf(1, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS)
            }
        }

        @Synchronized
        private fun page(cursor: Long, request: JSONObject): JSONObject {
            val first = events.peekFirst()?.sequence ?: nextSequence
            val selected = JSONArray()
            var bytes = 0
            var next = maxOf(cursor, first)
            for (event in events) {
                if (event.sequence < next) continue
                if (selected.length() >= request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT) || bytes + event.size > MAXIMUM_PAGE_BYTES) break
                selected.put(JSONObject(event.json))
                bytes += event.size
                next = event.sequence + 1
            }
            return metadata().put("events", selected).put("nextCursor", next)
                .put("historyGap", cursor < first).put("lostEvents", maxOf(0, first - cursor))
                .put("hasMore", next < nextSequence)
        }
    }

    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_START, "dikciz_shell_session_start",
            "Start a process-owned shell session using the existing shell-stream runner. Read incremental events from any ordinary binding, including HTML/Lua, after transport disconnects. root defaults true; use false for the app sandbox. Output history is bounded and gaps are explicit. Sessions end with the app process.",
            mapOf(
                DikcizShellStreams.KEY_COMMAND to text(DikcizAutomationControlPlane.MAXIMUM_SHELL_COMMAND_CHARACTERS),
                DikcizShellStreams.KEY_ROOT to JSONObject().put("type", "boolean").put("default", true),
                DikcizShellStreams.KEY_TIMEOUT to integer(0, Int.MAX_VALUE.toLong(), DEFAULT_TIMEOUT),
                DikcizShellStreams.KEY_CHUNK_BYTES to integer(1, DikcizShellStreams.MAXIMUM_CHUNK_BYTES.toLong(), DikcizShellStreams.DEFAULT_CHUNK_BYTES.toLong()),
                KEY_BUFFER to integer(MINIMUM_BUFFER.toLong(), MAXIMUM_BUFFER.toLong(), DEFAULT_BUFFER.toLong()),
                KEY_EVENTS to integer(1, MAXIMUM_EVENTS.toLong(), DEFAULT_EVENTS.toLong()),
                KEY_RETENTION to integer(1, MAXIMUM_RETENTION.toLong(), DEFAULT_RETENTION.toLong()),
            ), setOf(DikcizShellStreams.KEY_COMMAND), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_READ, "dikciz_shell_session_read",
            "Read retained shell events from an integer cursor. Follow nextCursor/hasMore; historyGap/lostEvents exposes overwritten history. Optional waitMilliseconds long-polls for an event or completion. Pages also respect the shared response-byte budget.",
            mapOf(KEY_ID to text(36), KEY_CURSOR to integer(0, Long.MAX_VALUE, 0), KEY_WAIT to integer(0, 30000, 0),
                "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT.toLong(), DikcizNativeCommands.DEFAULT_LIMIT.toLong())), setOf(KEY_ID)),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_shell_sessions_list",
            "List process-owned active and retained terminal shell sessions, newest first, without output contents.",
            mapOf("offset" to integer(0, DikcizNativeCommands.MAXIMUM_OFFSET.toLong(), 0),
                "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT.toLong(), DikcizNativeCommands.DEFAULT_LIMIT.toLong()))),
        DikcizNativeCommands.Definition(TYPE_STOP, "dikciz_shell_session_stop",
            "Request cancellation of one process-owned shell session. Read until active=false and inspect the terminal event. Cancellation does not roll back side effects.",
            mapOf(KEY_ID to text(36)), setOf(KEY_ID), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_FORGET, "dikciz_shell_session_forget",
            "Remove one terminal shell session and its retained output. Refuses active sessions; stop and observe completion first.",
            mapOf(KEY_ID to text(36)), setOf(KEY_ID), readOnly = false),
    )
    val TYPES = setOf(TYPE_START, TYPE_READ, TYPE_LIST, TYPE_STOP, TYPE_FORGET)

    fun validate(type: String, request: JSONObject) {
        if (type == TYPE_START) {
            val arguments = JSONObject()
            DikcizShellStreams.START_FIELDS.filter(request::has).forEach { arguments.put(it, request.get(it)) }
            DikcizShellStreams.validate(arguments)
            val chunk = request.optInt(DikcizShellStreams.KEY_CHUNK_BYTES, DikcizShellStreams.DEFAULT_CHUNK_BYTES)
            if (request.optInt(KEY_BUFFER, DEFAULT_BUFFER) < chunk * 2 + EVENT_OVERHEAD_BYTES) {
                DikcizNativeCommands.invalid("Shell buffer must fit an encoded chunk and event metadata")
            }
        }
        if (request.has(KEY_ID)) {
            val id = request.getString(KEY_ID)
            try {
                if (UUID.fromString(id).toString() != id) DikcizNativeCommands.invalid("Invalid shell session ID")
            } catch (exception: IllegalArgumentException) {
                if (exception is DikcizAutomationRequestException) throw exception
                throw DikcizAutomationRequestException("validation_failed", "Invalid shell session ID").apply { initCause(exception) }
            }
        }
    }

    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Long, maximum: Long, default: Long) =
        JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum).put("default", default)
}
