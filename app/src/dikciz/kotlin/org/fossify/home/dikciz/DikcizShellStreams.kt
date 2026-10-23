package org.fossify.home.dikciz

import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.BufferedInputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal class DikcizShellStreams(
    logger: DikcizLogger,
    private val loggerProvider: () -> DikcizLogger = { logger },
) {
    private val logger: DikcizLogger get() = loggerProvider()
    private val streams = ConcurrentHashMap<String, Stream>()

    @Synchronized
    fun open(owner: String, request: JSONObject): Stream {
        validate(request, envelope = true)
        if (streams.size >= MAXIMUM_STREAM_COUNT) {
            throw failure(ERROR_STREAM_LIMIT, "too many active shell streams")
        }
        val stream = Stream(owner, request)
        streams[stream.id] = stream
        return stream
    }

    fun stop(owner: String, request: JSONObject): JSONObject {
        validate(request, stop = true, envelope = true)
        val id = request.getString(KEY_STREAM_ID)
        val stream = streams[id]?.takeIf { it.owner == owner }
            ?: throw failure(ERROR_STREAM_NOT_FOUND, "shell stream was not found")
        stream.cancel(STATE_CANCELLED)
        return JSONObject().put(KEY_STREAM_ID, id).put(KEY_STATE, STATE_CANCELLING)
    }

    fun closeOwner(owner: String) {
        streams.values.filter { it.owner == owner }.forEach { it.cancel(STATE_CANCELLED) }
    }

    fun close() {
        streams.values.forEach { it.cancel(STATE_CANCELLED) }
    }

    inner class Stream(val owner: String, private val request: JSONObject) {
        val id: String = UUID.randomUUID().toString()
        private val reason = AtomicReference<String?>()
        private val isFinished = AtomicBoolean()
        private val consumed = AtomicBoolean()
        private val killed = AtomicBoolean()
        private val started = SystemClock.elapsedRealtime()
        private val timeout = request.optLong(KEY_TIMEOUT, DEFAULT_TIMEOUT_MILLISECONDS)
        private val useRoot = request.optBoolean(KEY_ROOT, true)
        private var sequence = 0L
        private var outputBytes = 0L
        @Volatile private var process: Process? = null
        @Volatile private var processGroup = 0
        @Volatile private var writingSince = 0L
        @Volatile private var hasHandshake = false

        fun description(): JSONObject = event(EVENT_STARTED).put(KEY_STATE, STATE_STARTING)

        fun cancel(state: String) {
            reason.compareAndSet(null, state)
            if (!consumed.get()) {
                streams.remove(id, this)
            }
        }

        fun consume(
            isAllowed: () -> Boolean,
            abortConnection: () -> Unit,
            emit: (JSONObject) -> Unit,
        ): JSONObject {
            check(consumed.compareAndSet(false, true))
            val watchdog = Thread({
                try {
                    while (!isFinished.get()) {
                        val now = SystemClock.elapsedRealtime()
                        if (!isAllowed()) cancel(STATE_CANCELLED)
                        if (timeout > 0 && now - started >= timeout) cancel(STATE_TIMED_OUT)
                        if (process != null && !hasHandshake && now - started >= STARTUP_TIMEOUT_MILLISECONDS) {
                            cancel(STATE_FAILED)
                        }
                        if (writingSince > 0 && now - writingSince >= DELIVERY_TIMEOUT_MILLISECONDS) {
                            cancel(STATE_SLOW_CONSUMER)
                            abortConnection()
                        }
                        if (reason.get() != null) terminate()
                        Thread.sleep(POLL_MILLISECONDS)
                    }
                } catch (exception: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (exception: Exception) {
                    logger.error(EVENT_STREAM_FAILED, mapOf(FIELD_STREAM_ID to id, FIELD_ERROR_CLASS to exception::class.java.simpleName))
                    cancel(STATE_FAILED)
                    terminate()
                    abortConnection()
                }
            }, THREAD_WATCHDOG).apply { isDaemon = true }
            var exitCode: Int? = null
            logger.info(EVENT_STREAM_STARTED, mapOf(FIELD_STREAM_ID to id, KEY_ROOT to useRoot))
            try {
                if (reason.get() != null || !isAllowed()) {
                    cancel(STATE_CANCELLED)
                    return completion(exitCode)
                }
                val shell = DikcizPrivilegedShell()
                if (useRoot && !shell.isRootAvailable()) throw shell.rootUnavailable()
                process = startProcess(request.getString(KEY_COMMAND), useRoot)
                watchdog.start()
                BufferedInputStream(process!!.inputStream).use { input ->
                    val header = StringBuilder()
                    while (header.length <= MAXIMUM_HANDSHAKE_CHARACTERS) {
                        val byte = input.read()
                        if (byte < 0 || byte == NEWLINE_BYTE) break
                        header.append(byte.toChar())
                    }
                    val group = header.toString().takeIf { it.startsWith(HANDSHAKE_PREFIX) }
                        ?.removePrefix(HANDSHAKE_PREFIX)?.toIntOrNull()
                    if (group == null || group <= 1) throw IOException("shell stream did not establish its process group")
                    processGroup = group
                    hasHandshake = true
                    killed.set(false)
                    val buffer = ByteArray(request.optInt(KEY_CHUNK_BYTES, DEFAULT_CHUNK_BYTES))
                    var lastHeartbeat = SystemClock.elapsedRealtime()
                    var lastProgressLog = lastHeartbeat
                    while (reason.get() == null) {
                        if (SystemClock.elapsedRealtime() - lastProgressLog >= PROGRESS_LOG_MILLISECONDS) {
                            logger.info(EVENT_STREAM_PROGRESS, mapOf(FIELD_STREAM_ID to id, FIELD_OUTPUT_BYTES to outputBytes))
                            lastProgressLog = SystemClock.elapsedRealtime()
                        }
                        val available = input.available()
                        if (available > 0) {
                            val count = input.read(buffer, 0, minOf(available, buffer.size))
                            if (count < 0) break
                            outputBytes += count
                            val encoded = Base64.getEncoder().encodeToString(buffer.copyOf(count))
                            writingSince = SystemClock.elapsedRealtime()
                            emit(event(EVENT_OUTPUT).put(KEY_OUTPUT_BASE64, encoded))
                            writingSince = 0
                            lastHeartbeat = SystemClock.elapsedRealtime()
                            continue
                        }
                        if (!process!!.isAlive) break
                        if (SystemClock.elapsedRealtime() - lastHeartbeat >= HEARTBEAT_MILLISECONDS) {
                            writingSince = SystemClock.elapsedRealtime()
                            emit(event(EVENT_HEARTBEAT))
                            writingSince = 0
                            lastHeartbeat = SystemClock.elapsedRealtime()
                        }
                        Thread.sleep(POLL_MILLISECONDS)
                    }
                }
                terminate()
                process!!.waitFor(TERMINATION_WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)
                if (!process!!.isAlive) exitCode = process!!.exitValue()
            } catch (exception: DikcizAutomationRequestException) {
                reason.compareAndSet(null, exception.code)
                logger.warn(EVENT_STREAM_REJECTED, mapOf(FIELD_STREAM_ID to id, KEY_STATE to exception.code))
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                cancel(STATE_CANCELLED)
            } catch (exception: Exception) {
                reason.compareAndSet(null, STATE_FAILED)
                logger.error(EVENT_STREAM_FAILED, mapOf(FIELD_STREAM_ID to id, FIELD_ERROR_CLASS to exception::class.java.simpleName))
            } finally {
                terminate()
                try {
                    process?.outputStream?.close()
                } catch (exception: IOException) {
                    logger.warn(EVENT_CLEANUP_FAILED, mapOf(FIELD_STREAM_ID to id, FIELD_ERROR_CLASS to exception::class.java.simpleName))
                }
                isFinished.set(true)
                watchdog.interrupt()
                streams.remove(id, this)
                logger.info(
                    EVENT_STREAM_COMPLETED,
                    mapOf(
                        FIELD_STREAM_ID to id,
                        KEY_STATE to (reason.get() ?: STATE_EXITED),
                        FIELD_OUTPUT_BYTES to outputBytes,
                        FIELD_DURATION_MILLISECONDS to (SystemClock.elapsedRealtime() - started),
                    ),
                )
            }
            return completion(exitCode)
        }

        private fun event(kind: String): JSONObject = JSONObject()
            .put(KEY_STREAM_ID, id)
            .put(KEY_SEQUENCE, sequence++)
            .put(KEY_EVENT, kind)

        private fun completion(exitCode: Int?): JSONObject {
            val outcome = reason.get() ?: STATE_EXITED
            val knownStates = setOf(STATE_EXITED, STATE_CANCELLED, STATE_TIMED_OUT, STATE_FAILED, STATE_SLOW_CONSUMER)
            val result = event(EVENT_COMPLETE)
                .put(KEY_STATE, if (outcome in knownStates) outcome else STATE_FAILED)
                .put(KEY_EXIT_CODE, exitCode ?: JSONObject.NULL)
                .put(KEY_OUTPUT_BYTES, outputBytes)
            if (outcome !in knownStates) result.put(DikcizAutomationControlPlane.KEY_CODE, outcome)
            return result
        }

        private fun terminate() {
            val current = process ?: return
            if (processGroup > 1 && killed.compareAndSet(false, true)) {
                if (useRoot) {
                    try {
                        DikcizPrivilegedShell().run("kill -KILL -- -$processGroup", true, TERMINATION_WAIT_MILLISECONDS)
                    } catch (exception: Exception) {
                        logger.warn(EVENT_CLEANUP_FAILED, mapOf(FIELD_STREAM_ID to id, FIELD_ERROR_CLASS to exception::class.java.simpleName))
                    }
                } else {
                    try {
                        Os.kill(-processGroup, OsConstants.SIGKILL)
                    } catch (exception: ErrnoException) {
                        if (exception.errno != OsConstants.ESRCH) {
                            logger.warn(EVENT_CLEANUP_FAILED, mapOf(FIELD_STREAM_ID to id, FIELD_ERROR_CLASS to exception::class.java.simpleName))
                        }
                    }
                }
            }
            if (current.isAlive) current.destroyForcibly()
        }
    }

    companion object {
        const val TYPE_START = "shellStream"
        const val TYPE_STOP = "shellStreamStop"
        const val TYPE_EVENT = "shellStreamEvent"
        const val KEY_STREAM_ID = "streamId"
        const val KEY_CHUNK_BYTES = "chunkBytes"
        const val KEY_EVENT = "event"
        const val KEY_STATE = "state"
        const val KEY_SEQUENCE = "sequence"
        const val KEY_OUTPUT_BASE64 = "outputBase64"
        const val KEY_OUTPUT_BYTES = "outputBytes"
        const val KEY_EXIT_CODE = "exitCode"
        const val KEY_COMMAND = DikcizAutomationControlPlane.KEY_COMMAND
        const val KEY_ROOT = DikcizAutomationControlPlane.KEY_ROOT
        const val KEY_TIMEOUT = DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS
        const val DEFAULT_TIMEOUT_MILLISECONDS = 0L
        const val DEFAULT_CHUNK_BYTES = 4096
        const val MAXIMUM_CHUNK_BYTES = 65536
        const val MAXIMUM_STREAM_COUNT = 4
        const val EVENT_STARTED = "started"
        const val EVENT_OUTPUT = "output"
        const val EVENT_COMPLETE = "complete"
        const val EVENT_HEARTBEAT = "heartbeat"
        const val STATE_STARTING = "starting"
        const val STATE_EXITED = "exited"
        const val STATE_CANCELLING = "cancelling"
        const val STATE_CANCELLED = "cancelled"
        const val STATE_TIMED_OUT = "timed_out"
        const val STATE_FAILED = "failed"
        const val STATE_SLOW_CONSUMER = "slow_consumer"
        const val ERROR_STREAM_NOT_FOUND = "shell_stream_not_found"
        const val ERROR_STREAM_LIMIT = "shell_stream_limit"
        val START_FIELDS = setOf(KEY_COMMAND, KEY_ROOT, KEY_TIMEOUT, KEY_CHUNK_BYTES)
        val STOP_FIELDS = setOf(KEY_STREAM_ID)
        private const val PROGRESS_LOG_MILLISECONDS = 30000L
        private const val HEARTBEAT_MILLISECONDS = 1000L
        private const val POLL_MILLISECONDS = 50L
        private const val STARTUP_TIMEOUT_MILLISECONDS = 5000L
        private const val DELIVERY_TIMEOUT_MILLISECONDS = 10000L
        private const val TERMINATION_WAIT_MILLISECONDS = 1000L
        private const val MAXIMUM_HANDSHAKE_CHARACTERS = 64
        private const val NEWLINE_BYTE = 10
        private const val HANDSHAKE_PREFIX = "DIKCIZ_STREAM_PID:"
        // The pipe closes when the app dies, including when a root child survives its UID.
        private const val GROUP_WRAPPER = "printf 'DIKCIZ_STREAM_PID:%s\n' \"\$\$\"; exec 3<&0; " +
            "(cat >/dev/null; kill -KILL -- -\$\$) <&3 3<&- >/dev/null 2>&1 & " +
            "exec /system/bin/sh -c \"\$1\" 3<&- </dev/null"
        private const val THREAD_WATCHDOG = "dikciz-shell-stream-watchdog"
        private const val EVENT_STREAM_PROGRESS = "shell_stream_progress"
        private const val EVENT_STREAM_STARTED = "shell_stream_started"
        private const val EVENT_STREAM_COMPLETED = "shell_stream_completed"
        private const val EVENT_STREAM_REJECTED = "shell_stream_rejected"
        private const val EVENT_STREAM_FAILED = "shell_stream_failed"
        private const val EVENT_CLEANUP_FAILED = "shell_stream_cleanup_failed"
        private const val FIELD_ERROR_CLASS = "error_class"
        private const val FIELD_DURATION_MILLISECONDS = "duration_milliseconds"
        private const val FIELD_STREAM_ID = "stream_id"
        private const val FIELD_OUTPUT_BYTES = "output_bytes"

        fun validate(request: JSONObject, stop: Boolean = false, envelope: Boolean = false) {
            val fields = if (stop) STOP_FIELDS else START_FIELDS
            val allowed = if (envelope) fields + setOf(DikcizAutomationControlPlane.KEY_TYPE, DikcizAutomationControlPlane.KEY_REQUEST_ID) else fields
            if (request.keys().asSequence().any { it !in allowed }) invalid()
            if (stop) {
                val id = request.opt(KEY_STREAM_ID) as? String ?: invalid()
                if (id.length != 36 || runCatching { UUID.fromString(id).toString() }.getOrNull() != id) invalid()
                return
            }
            val command = request.opt(KEY_COMMAND) as? String ?: invalid()
            if (command.isBlank() || command.length > DikcizAutomationControlPlane.MAXIMUM_SHELL_COMMAND_CHARACTERS || command.contains('\u0000')) invalid()
            if (request.has(KEY_ROOT) && request.opt(KEY_ROOT) !is Boolean) invalid()
            for ((key, range) in mapOf(KEY_TIMEOUT to 0L..Int.MAX_VALUE.toLong(), KEY_CHUNK_BYTES to 1L..MAXIMUM_CHUNK_BYTES.toLong())) {
                if (!request.has(key)) continue
                val value = request.opt(key)
                if ((value !is Int && value !is Long) || (value as Number).toLong() !in range) invalid()
            }
        }

        private fun startProcess(command: String, root: Boolean): Process {
            val arguments = listOf("/system/bin/setsid", "-w", "/system/bin/sh", "-c", GROUP_WRAPPER, "dikciz-stream", command)
            val actual = if (root) listOf("su", "-c", arguments.joinToString(" ", transform = ::quoteArgument)) else arguments
            return ProcessBuilder(actual).redirectErrorStream(true).start()
        }

        private fun quoteArgument(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

        private fun invalid(): Nothing = throw failure(DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, "invalid shell stream arguments")

        private fun failure(code: String, message: String): DikcizAutomationRequestException = DikcizAutomationRequestException(code, message)
    }
}
