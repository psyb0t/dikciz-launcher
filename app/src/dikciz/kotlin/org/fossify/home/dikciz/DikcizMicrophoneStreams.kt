package org.fossify.home.dikciz

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import java.util.UUID
import org.json.JSONObject
import org.fossify.home.dikciz.DikcizMicrophoneStreamFields as StreamFields
import org.fossify.home.dikciz.DikcizMicrophoneStreamOptions as StreamOptions
import org.fossify.home.dikciz.DikcizReplayFields as ReplayFields

internal object DikcizMicrophoneStreams {
    const val TYPE_START = "microphoneStreamStart"
    const val TYPE_READ = "microphoneStreamRead"
    const val TYPE_STOP = "microphoneStreamStop"
    const val TYPE_LIST = "microphoneStreamsList"
    const val TYPE_FORGET = "microphoneStreamForget"
    private const val MAXIMUM_RECORDS = 8
    private const val MAXIMUM_TOTAL_BUFFER = 33554432L
    private const val MAXIMUM_WAIT = 30000
    private const val BASE64_INPUT_GROUP = 3
    private const val BASE64_OUTPUT_GROUP = 4
    private const val SCHEMA_TYPE = "type"
    private const val SCHEMA_MINIMUM = "minimum"
    private const val SCHEMA_MAXIMUM = "maximum"
    private const val SCHEMA_MAX_LENGTH = "maxLength"
    private const val SCHEMA_DEFAULT = "default"
    private const val SCHEMA_DESCRIPTION = "description"
    private const val JSON_INTEGER = "integer"
    private const val JSON_STRING = "string"
    private const val KEY_TOTAL = "total"
    private const val STATE_FORGOTTEN = "forgotten"
    private const val ERROR_PERMISSION = "native_permission_cameraAndMicrophone"
    private const val ERROR_FOREGROUND = "capture_foreground_required"
    private const val ERROR_CAPACITY = "microphone_stream_capacity"
    private const val ERROR_BUFFER_CAPACITY = "microphone_stream_buffer_capacity"
    private const val ERROR_NOT_FOUND = "microphone_stream_not_found"
    private const val ERROR_ACTIVE = "microphone_stream_active"
    private const val ERROR_CLEANUP_PENDING = "microphone_stream_cleanup_pending"
    private const val MESSAGE_PERMISSION = "Android microphone permission is required"
    private const val MESSAGE_FOREGROUND = "Open Dikciz before starting microphone capture; an active stream can continue under its foreground service"
    private const val MESSAGE_CAPACITY = "Forget a terminal microphone stream or wait for retention expiry"
    private const val MESSAGE_BUFFER_CAPACITY = "Retained microphone replay budget reached"
    private const val MESSAGE_NOT_FOUND = "No retained microphone stream with that ID"
    private const val MESSAGE_ACTIVE = "Stop and observe stream completion before forgetting audio"
    private const val MESSAGE_CLEANUP_PENDING = "Microphone release is unconfirmed; restart Dikciz before forgetting the stream"
    private const val MESSAGE_ID = "Invalid microphone stream ID"
    private const val MESSAGE_BUFFER = "Replay must fit one complete encoded audio chunk and its metadata"
    private val records = LinkedHashMap<String, DikcizMicrophoneStream>()
    @Volatile private var currentLogger: DikcizLogger? = null

    fun attachLogger(logger: DikcizLogger) {
        currentLogger = logger
    }

    fun execute(context: Context, logger: DikcizLogger, type: String, request: JSONObject): JSONObject {
        attachLogger(logger)
        return when (type) {
            TYPE_START -> {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    DikcizNativeCommands.failure(ERROR_PERMISSION, MESSAGE_PERMISSION)
                }
                val process = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(process)
                if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    DikcizNativeCommands.failure(ERROR_FOREGROUND, MESSAGE_FOREGROUND)
                }
                create(context.applicationContext, options(request)).apply { launch() }.awaitStart()
            }
            TYPE_READ -> session(request).read(
                request.optLong(ReplayFields.CURSOR, 0), request.optInt(ReplayFields.LIMIT, DikcizNativeCommands.DEFAULT_LIMIT),
                request.optLong(ReplayFields.WAIT, 0),
            )
            TYPE_STOP -> {
                val current = session(request)
                val requested = current.requestStop()
                current.metadata().put(StreamFields.CANCELLATION_REQUESTED, requested)
            }
            TYPE_LIST -> list(request)
            TYPE_FORGET -> forget(request)
            else -> DikcizNativeCommands.invalid()
        }
    }

    @Synchronized
    private fun create(context: Context, options: StreamOptions): DikcizMicrophoneStream {
        prune()
        if (records.size >= MAXIMUM_RECORDS) DikcizNativeCommands.failure(ERROR_CAPACITY, MESSAGE_CAPACITY)
        if (records.values.sumOf { it.options.bufferBytes.toLong() } + options.bufferBytes > MAXIMUM_TOTAL_BUFFER) {
            DikcizNativeCommands.failure(ERROR_BUFFER_CAPACITY, MESSAGE_BUFFER_CAPACITY)
        }
        DikcizMediaCapture.acquireMicrophone()
        try {
            val stream = DikcizMicrophoneStream(UUID.randomUUID().toString(), context, options) {
                requireNotNull(currentLogger)
            }
            records[stream.id] = stream
            return stream
        } catch (exception: RuntimeException) {
            DikcizMediaCapture.releaseMicrophone()
            throw exception
        }
    }

    @Synchronized
    private fun session(request: JSONObject): DikcizMicrophoneStream {
        prune()
        return records[request.getString(ReplayFields.STREAM_ID)]
            ?: DikcizNativeCommands.failure(ERROR_NOT_FOUND, MESSAGE_NOT_FOUND)
    }

    @Synchronized
    private fun list(request: JSONObject): JSONObject {
        prune()
        val items = records.values.toList().asReversed().map { it.metadata() }
        return DikcizNativeCommands.page(items, request).put(KEY_TOTAL, items.size)
    }

    @Synchronized
    private fun forget(request: JSONObject): JSONObject {
        val current = session(request)
        if (!current.isFinished()) DikcizNativeCommands.failure(ERROR_ACTIVE, MESSAGE_ACTIVE)
        if (current.metadata().getBoolean(StreamFields.CLEANUP_FAILED)) {
            DikcizNativeCommands.failure(ERROR_CLEANUP_PENDING, MESSAGE_CLEANUP_PENDING)
        }
        records.remove(current.id)
        return JSONObject().put(ReplayFields.STREAM_ID, current.id).put(ReplayFields.STATE, STATE_FORGOTTEN)
    }

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        records.entries.removeAll { it.value.expired(now) }
    }

    @Synchronized
    fun serviceReady(id: String): Boolean = records[id]?.serviceReady() ?: false

    @Synchronized
    fun serviceFailed(id: String, exception: RuntimeException) {
        records[id]?.serviceFailed(exception)
    }

    @Synchronized
    fun serviceDestroyed(id: String) {
        records[id]?.serviceDestroyed()
    }

    @Synchronized
    fun stopFromOwner(id: String) {
        records[id]?.requestStop()
    }

    private fun options(request: JSONObject) = StreamOptions(
        request.optInt(StreamFields.SAMPLE_RATE, StreamOptions.DEFAULT_SAMPLE_RATE),
        request.optInt(StreamFields.CHANNELS, StreamOptions.DEFAULT_CHANNELS),
        request.optInt(StreamFields.CHUNK_MILLISECONDS, StreamOptions.DEFAULT_CHUNK_MILLISECONDS),
        request.optLong(StreamFields.DURATION, StreamOptions.DEFAULT_DURATION),
        request.optLong(StreamFields.STARTUP_TIMEOUT, StreamOptions.DEFAULT_STARTUP_TIMEOUT),
        request.optInt(StreamFields.BUFFER_BYTES, StreamOptions.DEFAULT_BUFFER),
        request.optInt(StreamFields.MAXIMUM_EVENTS, StreamOptions.DEFAULT_EVENTS),
        request.optInt(StreamFields.RETENTION, StreamOptions.DEFAULT_RETENTION),
        if (request.has(StreamFields.INPUT_DEVICE)) request.getInt(StreamFields.INPUT_DEVICE) else null,
    )

    private val ID_FIELDS = mapOf(ReplayFields.STREAM_ID to text(36))
    private val PAGE_FIELDS = mapOf(
        ReplayFields.LIMIT to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT.toLong()).put(SCHEMA_DEFAULT, DikcizNativeCommands.DEFAULT_LIMIT),
        "offset" to integer(0, DikcizNativeCommands.MAXIMUM_OFFSET.toLong()).put(SCHEMA_DEFAULT, 0),
    )
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_START, "dikciz_microphone_stream_start",
            "Start actual microphone PCM16 little-endian capture with a dedicated Android foreground service and Stop notification. Start requires foreground eligibility and microphone access; recording can continue in the background. Replay survives client disconnect, not process death. Microphone ownership is shared with recording, recognition and video. No cellular call audio.",
            mapOf(
                StreamFields.SAMPLE_RATE to integer(StreamOptions.MINIMUM_SAMPLE_RATE.toLong(), StreamOptions.MAXIMUM_SAMPLE_RATE.toLong()).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_SAMPLE_RATE),
                StreamFields.CHANNELS to integer(1, 2).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_CHANNELS),
                StreamFields.CHUNK_MILLISECONDS to integer(StreamOptions.MINIMUM_CHUNK_MILLISECONDS.toLong(), StreamOptions.MAXIMUM_CHUNK_MILLISECONDS.toLong()).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_CHUNK_MILLISECONDS),
                StreamFields.DURATION to integer(StreamOptions.MINIMUM_DURATION, Int.MAX_VALUE.toLong()).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_DURATION),
                StreamFields.STARTUP_TIMEOUT to integer(1, StreamOptions.MAXIMUM_STARTUP_TIMEOUT).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_STARTUP_TIMEOUT),
                StreamFields.BUFFER_BYTES to integer(StreamOptions.MINIMUM_BUFFER.toLong(), StreamOptions.MAXIMUM_BUFFER.toLong()).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_BUFFER),
                StreamFields.MAXIMUM_EVENTS to integer(1, StreamOptions.MAXIMUM_EVENTS.toLong()).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_EVENTS),
                StreamFields.RETENTION to integer(1, StreamOptions.MAXIMUM_RETENTION.toLong()).put(SCHEMA_DEFAULT, StreamOptions.DEFAULT_RETENTION),
                StreamFields.INPUT_DEVICE to integer(0, Int.MAX_VALUE.toLong()).put(SCHEMA_DESCRIPTION, "Preferred audio input ID from audioDevices; inspect the actual routed input."),
            ), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_READ, "dikciz_microphone_stream_read",
            "Read whole PCM chunks and terminal events after cursor. Save nextCursor, drain hasMore and inspect historyGap/lostEvents. Format and monotonic timing metadata accompany actual AudioRecord samples. Optional bounded long polling works across every ordinary binding.",
            ID_FIELDS + mapOf(
                ReplayFields.CURSOR to integer(0, MAXIMUM_SAFE_CURSOR).put(SCHEMA_DEFAULT, 0),
                ReplayFields.LIMIT to PAGE_FIELDS.getValue(ReplayFields.LIMIT), ReplayFields.WAIT to integer(0, MAXIMUM_WAIT.toLong()).put(SCHEMA_DEFAULT, 0),
            ), setOf(ReplayFields.STREAM_ID)),
        DikcizNativeCommands.Definition(TYPE_STOP, "dikciz_microphone_stream_stop",
            "Request microphone-stream stop. Poll read/list until terminal and hardwareReleaseConfirmed. Does not discard retained audio.", ID_FIELDS, setOf(ReplayFields.STREAM_ID), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_microphone_streams_list",
            "Page process-owned microphone-stream metadata without returning audio. Follow hasMore. Process death loses these records.",
            PAGE_FIELDS),
        DikcizNativeCommands.Definition(TYPE_FORGET, "dikciz_microphone_stream_forget",
            "Release retained audio and metadata for one terminal stream. Active or unconfirmed hardware cleanup is refused.",
            ID_FIELDS, setOf(ReplayFields.STREAM_ID), readOnly = false),
    )
    val TYPES = setOf(TYPE_START, TYPE_READ, TYPE_STOP, TYPE_LIST, TYPE_FORGET)

    fun validate(type: String, request: JSONObject) {
        if (request.has(ReplayFields.STREAM_ID)) {
            val id = request.getString(ReplayFields.STREAM_ID)
            if (!UUID_PATTERN.matches(id)) DikcizNativeCommands.invalid(MESSAGE_ID)
        }
        if (type != TYPE_START) return
        val selected = options(request)
        val encoded = (selected.chunkBytes.toLong() + BASE64_INPUT_GROUP - 1) / BASE64_INPUT_GROUP * BASE64_OUTPUT_GROUP
        val required = encoded + StreamOptions.EVENT_OVERHEAD_BYTES
        if (required > selected.bufferBytes || required > DikcizBoundedReplay.MAXIMUM_PAGE_BYTES) {
            DikcizNativeCommands.invalid(MESSAGE_BUFFER)
        }
    }

    private const val MAXIMUM_SAFE_CURSOR = 9007199254740991L
    private val UUID_PATTERN = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private fun text(maximum: Int) = JSONObject().put(SCHEMA_TYPE, JSON_STRING).put(SCHEMA_MAX_LENGTH, maximum)
    private fun integer(minimum: Long, maximum: Long) =
        JSONObject().put(SCHEMA_TYPE, JSON_INTEGER).put(SCHEMA_MINIMUM, minimum).put(SCHEMA_MAXIMUM, maximum)
}
