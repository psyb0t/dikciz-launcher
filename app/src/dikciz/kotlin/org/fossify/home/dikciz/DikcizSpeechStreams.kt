package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.os.SystemClock
import java.util.UUID
import org.json.JSONObject

internal object DikcizSpeechStreams {
    const val TYPE_START = "speechStreamStart"
    const val TYPE_READ = "speechStreamRead"
    const val TYPE_STOP = "speechStreamStop"
    const val TYPE_LIST = "speechStreamsList"
    const val TYPE_FORGET = "speechStreamForget"
    private const val MAXIMUM_STREAMS = 4
    private const val RETENTION_MILLISECONDS = 300000L
    private const val BUFFER_BYTES = 1048576
    private const val MAXIMUM_EVENTS = 64
    private val streams = linkedMapOf<String, Session>()
    private val IDS = Regex("[a-f0-9-]{36}")

    private class Session(val id: String, val recognizer: DikcizSpeechRecognition) {
        val replay = DikcizBoundedReplay(BUFFER_BYTES, MAXIMUM_EVENTS)
        val started = SystemClock.elapsedRealtime()
        @Volatile var state = "starting"
        @Volatile var error: String? = null
        @Volatile var finishedAt = 0L
        @Volatile var cancellationRequested = false
        @Volatile var released = false
        lateinit var thread: Thread
        fun metadata() = replay.metadata().put("streamId", id).put("state", state)
            .put("active", finishedAt == 0L).put("error", error ?: JSONObject.NULL)
            .put("hardwareReleaseConfirmed", released).put("partialResultsRequested", true)
    }

    fun execute(context: Context, recognizer: DikcizSpeechRecognition, type: String, request: JSONObject): JSONObject {
        if (type == TYPE_START) {
            DikcizNativeCommands.validateRecognitionOptions(request)
            recognizer.requireCaptureAccess()
            val status = recognizer.status()
            val offline = request.optString("mode", DikcizSpeechRecognition.MODE_ON_DEVICE) == DikcizSpeechRecognition.MODE_ON_DEVICE
            if (!status.getBoolean(if (offline) "onDeviceAvailable" else "recognitionAvailable")) {
                DikcizNativeCommands.failure(if (offline) "speech_on_device_unavailable" else "speech_service_unavailable",
                    "Install a compatible speech recognition provider")
            }
            return synchronized(this) {
                prune()
                if (streams.values.any { it.finishedAt == 0L }) DikcizNativeCommands.failure("hardware_busy", "A speech stream is already active")
                while (streams.size >= MAXIMUM_STREAMS) streams.remove(streams.keys.first())
                val session = Session(UUID.randomUUID().toString(), recognizer)
                streams[session.id] = session
                session.thread = Thread({
                    try {
                        if (session.cancellationRequested) throw InterruptedException()
                        session.state = "listening"
                        val result = recognizer.recognize(request, { session.replay.append(it) }, session.id)
                        session.replay.append(JSONObject().put("event", "final").put("result", result))
                        session.state = "completed"
                    } catch (_: InterruptedException) {
                        session.state = "cancelled"
                        session.error = "speech_cancelled"
                    } catch (exception: DikcizAutomationRequestException) {
                        session.state = if (session.cancellationRequested) "cancelled" else "failed"
                        session.error = exception.code
                    } catch (exception: Exception) {
                        android.util.Log.e("DikcizSpeechStreams", "Speech stream failed: " + exception.javaClass.simpleName)
                        session.state = "failed"
                        session.error = "speech_stream_failed"
                    } finally {
                        try {
                            recognizer.stop(session.id)
                            session.released = true
                        } catch (exception: Exception) {
                            android.util.Log.e("DikcizSpeechStreams", "Speech cleanup pending: " + exception.javaClass.simpleName)
                        }
                        session.finishedAt = SystemClock.elapsedRealtime()
                        session.replay.finish()
                    }
                }, "dikciz-speech-stream").apply { isDaemon = true; start() }
                session.metadata()
            }
        }
        val session = synchronized(this) {
            prune()
            if (type == TYPE_LIST) return DikcizNativeCommands.page(streams.values.toList(), request, Session::metadata)
                .put("total", streams.size)
            val id = request.getString("streamId")
            if (!IDS.matches(id)) DikcizNativeCommands.invalid("Invalid speech stream ID")
            streams[id] ?: DikcizNativeCommands.failure("speech_stream_not_found", "No retained speech stream")
        }
        return when (type) {
            TYPE_READ -> session.replay.read(request.optLong("cursor", 0), request.optInt("limit", 50), request.optLong("waitMilliseconds", 0))
                .put("streamId", session.id).put("state", session.state).put("active", session.finishedAt == 0L)
                .put("error", session.error ?: JSONObject.NULL).put("hardwareReleaseConfirmed", session.released)
            TYPE_STOP -> {
                if (session.finishedAt == 0L) {
                    session.cancellationRequested = true
                    session.thread.interrupt()
                    session.recognizer.stop(session.id)
                    session.thread.join(CLEANUP_MILLISECONDS)
                }
                session.metadata()
            }
            TYPE_FORGET -> {
                if (session.finishedAt == 0L) DikcizNativeCommands.failure("speech_stream_active", "Stop the stream before forgetting it")
                synchronized(this) { streams.remove(session.id) }
                JSONObject().put("streamId", session.id).put("forgotten", true)
            }
            else -> DikcizNativeCommands.invalid()
        }
    }

    @Synchronized private fun prune() {
        val cutoff = SystemClock.elapsedRealtime() - RETENTION_MILLISECONDS
        streams.entries.removeAll { it.value.finishedAt in 1..cutoff }
    }

    private const val CLEANUP_MILLISECONDS = 5000L
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Long, maximum: Long) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    private val ID_FIELD = mapOf("streamId" to text(36))
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_START, "dikciz_speech_stream_start",
            "Start foreground microphone speech recognition with partial-result replay. Requires Android microphone access and a recognition provider. Accepted is not recognized; read state/events. Provider may emit only a final result. No automatic fallback from onDevice to system.",
            mapOf("mode" to text(16).put("enum", org.json.JSONArray(listOf("onDevice", "system"))).put("default", "onDevice"),
                "language" to text(64), "timeoutMilliseconds" to integer(1000, 120000).put("default", 30000)),
            readOnly = false),
        DikcizNativeCommands.Definition(TYPE_READ, "dikciz_speech_stream_read",
            "Read bounded partial/final speech events with exclusive cursor, historyGap, lostEvents and terminal state. Does not start capture. Retained in memory for five minutes after completion. Owner only.",
            ID_FIELD + mapOf("cursor" to integer(0, 9007199254740991), "limit" to integer(1, 200), "waitMilliseconds" to integer(0, 30000)), setOf("streamId")),
        DikcizNativeCommands.Definition(TYPE_STOP, "dikciz_speech_stream_stop", "Stop one owned speech session and report actual cleanup state. Does not cancel another session. Owner only.",
            ID_FIELD, setOf("streamId"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_speech_streams_list", "List at most four retained speech streams without transcript payloads.",
            mapOf("limit" to integer(1, 200), "offset" to integer(0, 1000000))),
        DikcizNativeCommands.Definition(TYPE_FORGET, "dikciz_speech_stream_forget", "Remove one completed speech stream and its replayed transcripts. Active sessions must stop first.",
            ID_FIELD, setOf("streamId"), readOnly = false),
    )
    val TYPES = setOf(TYPE_START, TYPE_READ, TYPE_STOP, TYPE_LIST, TYPE_FORGET)
}
