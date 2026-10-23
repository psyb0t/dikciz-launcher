package org.fossify.home.dikciz

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** A microphone session owns the same hardware slot as bounded recordings. */
@Suppress("DEPRECATION")
internal object DikcizMicrophoneSession {
    const val TYPE = "microphoneSession"
    private const val KEY_ACTION = "action"
    private const val KEY_SESSION = "sessionId"
    private const val KEY_DURATION = "maximumDurationMilliseconds"
    private const val KEY_BYTES = "maximumBytes"
    private const val KEY_INPUT = "inputDeviceId"
    private const val KEY_RATE = "sampleRateHz"
    private const val KEY_BIT_RATE = "bitRate"
    private const val KEY_CHANNELS = "channels"
    private const val KEY_STATE = "state"
    private const val ACTION_START = "start"
    private const val ACTION_STATUS = "status"
    private const val ACTION_STOP = "stop"
    private const val ACTION_CANCEL = "cancel"
    private const val STATE_IDLE = "idle"
    private const val STATE_RECORDING = "recording"
    private const val STATE_COMPLETED = "completed"
    private const val STATE_FAILED = "failed"
    private const val STATE_CANCELLED = "cancelled"
    private const val DEFAULT_DURATION = 60_000L
    private const val MAXIMUM_DURATION = 86_400_000L
    private const val DEFAULT_BYTES = 67_108_864L
    private const val MAXIMUM_BYTES = 1_073_741_824L
    private const val DEFAULT_RATE = 44_100
    private const val DEFAULT_BIT_RATE = 128_000
    private const val CHECK_INTERVAL = 250L
    private val handler = Handler(Looper.getMainLooper())
    private val actions = setOf(ACTION_START, ACTION_STATUS, ACTION_STOP, ACTION_CANCEL)
    private val rates = setOf(8000, 16000, 22050, 44100, 48000)
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var logger: DikcizLogger? = null
    private var elapsedStart = 0L
    private var deadline = 0L
    private var record = JSONObject().put(KEY_STATE, STATE_IDLE)
    private val watchdog = object : Runnable {
        override fun run() {
            val current = recorder ?: return
            try {
                if (!foreground()) {
                    finish(STATE_FAILED, "capture_foreground_required")
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && current.activeRecordingConfiguration?.isClientSilenced == true) {
                    finish(STATE_FAILED, "microphone_silenced")
                    return
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    finish(STATE_COMPLETED, "maximum_duration")
                    return
                }
            } catch (exception: RuntimeException) {
                logger?.error("microphone_watchdog_failed", mapOf("error_class" to exception.javaClass.simpleName))
                finish(STATE_FAILED, "microphone_status_failed")
                return
            }
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    fun execute(context: Context, log: DikcizLogger, request: JSONObject): JSONObject {
        check(Looper.myLooper() == Looper.getMainLooper())
        logger = log
        val action = request.getString(KEY_ACTION)
        if (action == ACTION_START) return start(context.applicationContext, request)
        if (request.has(KEY_SESSION) && request.getString(KEY_SESSION) != record.optString(KEY_SESSION)) {
            DikcizNativeCommands.failure("microphone_session_not_found", "Recording session is no longer current")
        }
        if (action == ACTION_STOP && recorder != null) finish(STATE_COMPLETED, "explicit_stop")
        if (action == ACTION_CANCEL && recorder != null) finish(STATE_CANCELLED, "explicit_cancel")
        if (recorder != null) record.put("durationMilliseconds", SystemClock.elapsedRealtime() - elapsedStart)
        return JSONObject(record.toString())
    }

    private fun start(context: Context, request: JSONObject): JSONObject {
        DikcizNativeCommands.requirePermission(context, Manifest.permission.RECORD_AUDIO, "cameraAndMicrophone")
        if (!foreground()) DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz before recording")
        val manager = context.getSystemService(AudioManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Android audio service unavailable")
        val input = if (request.has(KEY_INPUT)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) DikcizNativeCommands.failure("unsupported_android_version", "Input selection requires Android 9")
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == request.getInt(KEY_INPUT) }
                ?: DikcizNativeCommands.failure("audio_device_not_found", "Selected microphone input is unavailable")
        } else null
        DikcizMediaCapture.acquireMicrophone()
        elapsedStart = SystemClock.elapsedRealtime()
        record = JSONObject().put(KEY_SESSION, UUID.randomUUID().toString()).put(KEY_STATE, STATE_RECORDING)
        try {
            val root = context.getExternalFilesDir("captures")
                ?: DikcizNativeCommands.failure("capture_storage_unavailable", "App capture storage unavailable")
            output = File.createTempFile("recording-", ".m4a", root)
            val current = MediaRecorder()
            recorder = current
            record = JSONObject().put(KEY_SESSION, UUID.randomUUID().toString()).put(KEY_STATE, STATE_RECORDING)
                .put("source", "microphone").put("callAudio", false)
                .put("preferredInputDeviceId", input?.id ?: JSONObject.NULL)
                .put("startedAtMilliseconds", System.currentTimeMillis())
            current.setAudioSource(MediaRecorder.AudioSource.MIC)
            current.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            current.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            current.setAudioSamplingRate(request.optInt(KEY_RATE, DEFAULT_RATE))
            current.setAudioEncodingBitRate(request.optInt(KEY_BIT_RATE, DEFAULT_BIT_RATE))
            current.setAudioChannels(request.optInt(KEY_CHANNELS, 1))
            current.setMaxDuration(request.optLong(KEY_DURATION, DEFAULT_DURATION).toInt())
            current.setMaxFileSize(request.optLong(KEY_BYTES, DEFAULT_BYTES))
            current.setOutputFile(requireNotNull(output).path)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && input != null && !current.setPreferredDevice(input)) {
                DikcizNativeCommands.failure("audio_route_rejected", "Android rejected the preferred recording input")
            }
            current.setOnErrorListener { failed, what, extra ->
                if (recorder === failed) {
                    record.put("androidError", what).put("androidExtra", extra)
                    finish(STATE_FAILED, "microphone_recorder_error")
                }
            }
            current.setOnInfoListener { active, what, _ ->
                if (recorder === active && what in setOf(MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)) {
                    finish(STATE_COMPLETED, if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) "maximum_duration" else "maximum_bytes")
                }
            }
            current.prepare()
            current.start()
            elapsedStart = SystemClock.elapsedRealtime()
            deadline = elapsedStart + request.optLong(KEY_DURATION, DEFAULT_DURATION)
            handler.postDelayed(watchdog, CHECK_INTERVAL)
            logger?.info("microphone_session_started", mapOf("session_id" to record.getString(KEY_SESSION)))
            return JSONObject(record.toString())
        } catch (exception: Exception) {
            finish(STATE_FAILED, "microphone_start_failed")
            throw exception
        }
    }

    private fun finish(state: String, reason: String) {
        handler.removeCallbacks(watchdog)
        val current = recorder
        recorder = null
        val file = output
        output = null
        var finalState = state
        try {
            if (state == STATE_COMPLETED) {
                try {
                    requireNotNull(current).stop()
                    if (file == null || file.length() == 0L) throw java.io.IOException("Recording file is empty")
                    record.put("path", file.path).put("bytes", file.length()).put("mimeType", "audio/mp4")
                } catch (exception: Exception) {
                    finalState = STATE_FAILED
                    record.put("error", "microphone_finalize_failed")
                    logger?.error("microphone_finalize_failed", mapOf("error_class" to exception.javaClass.simpleName))
                }
            }
        } finally {
            try {
                current?.release()
            } catch (exception: RuntimeException) {
                logger?.warn("microphone_release_failed", mapOf("error_class" to exception.javaClass.simpleName))
            } finally {
                if (finalState != STATE_COMPLETED && file != null && file.exists() && !file.delete()) {
                    record.put("cleanupFailed", true)
                    logger?.error("microphone_incomplete_file_cleanup_failed")
                }
                if (finalState != STATE_COMPLETED) {
                    record.remove("path")
                    record.remove("bytes")
                }
                record.put(KEY_STATE, finalState).put("reason", reason)
                    .put("durationMilliseconds", maxOf(0, SystemClock.elapsedRealtime() - elapsedStart))
                    .put("finishedAtMilliseconds", System.currentTimeMillis())
                if (finalState == STATE_FAILED && !record.has("error")) record.put("error", reason)
                DikcizMediaCapture.releaseMicrophone()
                logger?.info("microphone_session_finished", mapOf("state" to finalState, "reason" to reason))
            }
        }
    }

    private fun foreground(): Boolean {
        val process = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(process)
        return process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    val DEFINITIONS = listOf(DikcizNativeCommands.Definition(
        TYPE, "dikciz_microphone_session",
        "Start, inspect, stop or cancel one process-owned microphone recording. Requires foreground microphone access. Saved M4A is reported only after successful finalization. Cancellation/failure removes incomplete output. Shares ownership with microphoneRecord. Not call audio or a background recording service.",
        mapOf(
            KEY_ACTION to JSONObject().put("type", "string").put("enum", org.json.JSONArray(actions.toList())).put("maxLength", 16),
            KEY_SESSION to JSONObject().put("type", "string").put("maxLength", 36),
            KEY_DURATION to integer(1, MAXIMUM_DURATION).put("default", DEFAULT_DURATION),
            KEY_BYTES to integer(4096, MAXIMUM_BYTES).put("default", DEFAULT_BYTES),
            KEY_INPUT to integer(0, Int.MAX_VALUE.toLong()),
            KEY_RATE to JSONObject().put("type", "integer").put("enum", org.json.JSONArray(rates.toList())).put("default", DEFAULT_RATE),
            KEY_BIT_RATE to integer(8000, 320000).put("default", DEFAULT_BIT_RATE),
            KEY_CHANNELS to integer(1, 2).put("default", 1),
        ), setOf(KEY_ACTION), readOnly = false, mainThread = true,
    ))

    fun validate(request: JSONObject) {
        val action = request.getString(KEY_ACTION)
        if (action !in actions) DikcizNativeCommands.invalid("Unsupported microphone session action")
        val startFields = setOf(KEY_DURATION, KEY_BYTES, KEY_INPUT, KEY_RATE, KEY_BIT_RATE, KEY_CHANNELS)
        if (action == ACTION_START) {
            if (request.has(KEY_SESSION)) DikcizNativeCommands.invalid("New recording cannot select an existing session")
            if (request.has(KEY_RATE) && request.getInt(KEY_RATE) !in rates) DikcizNativeCommands.invalid("Unsupported recording sample rate")
        } else {
            if (startFields.any(request::has)) DikcizNativeCommands.invalid("Recording configuration applies only to start")
            if (action != ACTION_STATUS && !request.has(KEY_SESSION)) DikcizNativeCommands.invalid("Recording stop/cancel requires sessionId")
        }
        if (request.has(KEY_SESSION)) {
            val id = request.getString(KEY_SESSION)
            try {
                if (UUID.fromString(id).toString() != id) DikcizNativeCommands.invalid("Invalid recording session ID")
            } catch (exception: IllegalArgumentException) {
                if (exception is DikcizAutomationRequestException) throw exception
                throw DikcizAutomationRequestException("validation_failed", "Invalid recording session ID").apply { initCause(exception) }
            }
        }
    }

    private fun integer(minimum: Long, maximum: Long) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
}
