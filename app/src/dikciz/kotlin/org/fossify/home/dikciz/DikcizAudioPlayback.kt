package org.fossify.home.dikciz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.FileInputStream
import java.util.UUID
import org.json.JSONObject

/** One process-owned playback session, serialized with Android media callbacks. */
internal object DikcizAudioPlayback {
    const val TYPE = "audioPlayback"
    private const val KEY_ACTION = "action"
    private const val KEY_SESSION = "sessionId"
    private const val KEY_PATH = "path"
    private const val KEY_VOLUME = "volumePercent"
    private const val KEY_POSITION = "positionMilliseconds"
    private const val KEY_DEVICE = "outputDeviceId"
    private const val KEY_DURATION = "maximumDurationMilliseconds"
    private const val KEY_LOOP = "loop"
    private const val KEY_STATE = "state"
    private const val ACTION_PLAY = "play"
    private const val ACTION_STATUS = "status"
    private const val ACTION_PAUSE = "pause"
    private const val ACTION_RESUME = "resume"
    private const val ACTION_SEEK = "seek"
    private const val ACTION_VOLUME = "volume"
    private const val ACTION_STOP = "stop"
    private const val STATE_IDLE = "idle"
    private const val STATE_PREPARING = "preparing"
    private const val STATE_PLAYING = "playing"
    private const val STATE_PAUSED = "paused"
    private const val STATE_COMPLETED = "completed"
    private const val STATE_STOPPED = "stopped"
    private const val STATE_FAILED = "failed"
    private const val STATE_EXPIRED = "expired"
    private const val DEFAULT_DURATION = 3_600_000L
    private const val MAXIMUM_DURATION = 86_400_000L
    private val actions = setOf(ACTION_PLAY, ACTION_STATUS, ACTION_PAUSE, ACTION_RESUME, ACTION_SEEK, ACTION_VOLUME, ACTION_STOP)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var manager: AudioManager? = null
    private var focus: AudioFocusRequest? = null
    private var logger: DikcizLogger? = null
    private var expiry: Runnable? = null
    private var record = JSONObject().put(KEY_STATE, STATE_IDLE)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    fun execute(context: Context, log: DikcizLogger, request: JSONObject): JSONObject {
        check(Looper.myLooper() == Looper.getMainLooper())
        logger = log
        val action = request.getString(KEY_ACTION)
        if (action == ACTION_PLAY) return play(context.applicationContext, request)
        if (request.has(KEY_SESSION) && request.getString(KEY_SESSION) != record.optString(KEY_SESSION)) {
            DikcizNativeCommands.failure("audio_session_not_found", "Playback session is no longer current")
        }
        if (action == ACTION_STATUS) return snapshot()
        if (action == ACTION_STOP) {
            if (player != null) end(STATE_STOPPED)
            return snapshot()
        }
        val current = player ?: DikcizNativeCommands.failure("audio_not_active", "No active playback session")
        if (record.getString(KEY_STATE) == STATE_PREPARING) DikcizNativeCommands.failure("audio_not_ready", "Playback is still preparing")
        when (action) {
            ACTION_PAUSE -> {
                if (record.getString(KEY_STATE) == STATE_PLAYING) current.pause()
                record.put(KEY_STATE, STATE_PAUSED)
                abandonFocus()
            }
            ACTION_RESUME -> {
                if (record.getString(KEY_STATE) != STATE_PLAYING) {
                    acquireFocus()
                    current.start()
                    record.put(KEY_STATE, STATE_PLAYING).remove("pauseReason")
                }
            }
            ACTION_SEEK -> {
                val position = request.getLong(KEY_POSITION)
                if (position > current.duration) DikcizNativeCommands.invalid("Seek position exceeds media duration")
                current.seekTo(position, MediaPlayer.SEEK_CLOSEST)
                record.put("seeking", true)
            }
            ACTION_VOLUME -> {
                val volume = request.getInt(KEY_VOLUME)
                current.setVolume(volume / 100f, volume / 100f)
                record.put(KEY_VOLUME, volume)
            }
        }
        return snapshot()
    }

    private fun play(context: Context, request: JSONObject): JSONObject {
        if (player != null) DikcizNativeCommands.failure("hardware_busy", "Stop the current playback session before starting another")
        val source = DikcizFileCommands.file(request)
        if (!source.isFile) DikcizNativeCommands.failure("file_not_found", "Playback requires a readable regular file")
        val audio = context.getSystemService(AudioManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Android audio service unavailable")
        val output = if (request.has(KEY_DEVICE)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) DikcizNativeCommands.failure("unsupported_android_version", "Playback output selection requires Android 9")
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == request.getInt(KEY_DEVICE) }
                ?: DikcizNativeCommands.failure("audio_device_not_found", "Selected audio output is unavailable")
        } else null
        val current = MediaPlayer()
        player = current
        manager = audio
        record = JSONObject().put(KEY_SESSION, UUID.randomUUID().toString()).put(KEY_STATE, STATE_PREPARING)
            .put(KEY_PATH, source.path).put(KEY_VOLUME, request.optInt(KEY_VOLUME, 100))
            .put(KEY_LOOP, request.optBoolean(KEY_LOOP, false)).put("startedAtMilliseconds", System.currentTimeMillis())
            .put("seeking", false).put(KEY_POSITION, 0)
        try {
            current.setAudioAttributes(attributes)
            current.isLooping = request.optBoolean(KEY_LOOP, false)
            val volume = request.optInt(KEY_VOLUME, 100) / 100f
            current.setVolume(volume, volume)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && output != null && !current.setPreferredDevice(output)) {
                DikcizNativeCommands.failure("audio_route_rejected", "Android rejected the preferred audio output")
            }
            record.put("preferredOutputDeviceId", output?.id ?: JSONObject.NULL)
            current.setOnPreparedListener { ready ->
                if (player !== ready) return@setOnPreparedListener
                try {
                    record.put("durationMilliseconds", ready.duration)
                    acquireFocus()
                    ready.start()
                    record.put(KEY_STATE, STATE_PLAYING)
                    logger?.info("audio_playback_started", mapOf("session_id" to record.getString(KEY_SESSION)))
                } catch (exception: Exception) {
                    record.put("error", if (exception is DikcizAutomationRequestException) exception.code else "audio_start_failed")
                    end(STATE_FAILED)
                    logger?.error("audio_playback_start_failed", mapOf("error_class" to exception.javaClass.simpleName))
                }
            }
            current.setOnCompletionListener { completed -> if (player === completed) end(STATE_COMPLETED) }
            current.setOnSeekCompleteListener { completed ->
                if (player === completed) record.put("seeking", false).put(KEY_POSITION, completed.currentPosition)
            }
            current.setOnErrorListener { failed, what, extra ->
                if (player === failed) {
                    record.put("error", "audio_media_error").put("androidError", what).put("androidExtra", extra)
                    end(STATE_FAILED)
                    logger?.warn("audio_playback_media_error", mapOf("android_error" to what, "android_extra" to extra))
                }
                true
            }
            FileInputStream(source).use { current.setDataSource(it.fd) }
            current.prepareAsync()
            expiry = Runnable {
                if (player === current) end(STATE_EXPIRED)
            }.also { handler.postDelayed(it, request.optLong(KEY_DURATION, DEFAULT_DURATION)) }
            return snapshot()
        } catch (exception: Exception) {
            end(STATE_FAILED)
            throw exception
        }
    }

    private fun acquireFocus() {
        val audio = requireNotNull(manager)
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
                if (change < 0 && player != null) {
                    try {
                        if (record.optString(KEY_STATE) == STATE_PLAYING) {
                            player?.pause()
                            record.put(KEY_STATE, STATE_PAUSED).put("pauseReason", "audio_focus_lost")
                            abandonFocus()
                        }
                    } catch (exception: RuntimeException) {
                        record.put("error", "audio_focus_pause_failed")
                        end(STATE_FAILED)
                        logger?.error("audio_focus_pause_failed", mapOf("error_class" to exception.javaClass.simpleName))
                    }
                }
            }, handler).build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audio.abandonAudioFocusRequest(request)
            DikcizNativeCommands.failure("audio_focus_denied", "Android denied audio focus; open Dikciz or resolve competing playback")
        }
        focus = request
    }

    private fun abandonFocus() {
        val owned = focus ?: return
        focus = null
        try {
            if (manager?.abandonAudioFocusRequest(owned) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                logger?.warn("audio_focus_release_rejected")
            }
        } catch (exception: RuntimeException) {
            logger?.warn("audio_focus_release_failed", mapOf("error_class" to exception.javaClass.simpleName))
        }
    }

    private fun end(state: String) {
        expiry?.let(handler::removeCallbacks)
        expiry = null
        val current = player
        player = null
        try {
            if (state != STATE_FAILED && current != null && record.optString(KEY_STATE) in setOf(STATE_PLAYING, STATE_PAUSED)) {
                record.put(KEY_POSITION, current.currentPosition)
            }
        } catch (exception: RuntimeException) {
            logger?.warn("audio_final_position_unavailable", mapOf("error_class" to exception.javaClass.simpleName))
        } finally {
            try { current?.release() }
            catch (exception: RuntimeException) {
                logger?.warn("audio_player_release_failed", mapOf("error_class" to exception.javaClass.simpleName))
            }
            finally {
                abandonFocus()
                record.put(KEY_STATE, state).put("finishedAtMilliseconds", System.currentTimeMillis())
                logger?.info("audio_playback_finished", mapOf("state" to state))
            }
        }
    }

    private fun snapshot(): JSONObject {
        val current = player
        if (current != null && record.optString(KEY_STATE) in setOf(STATE_PLAYING, STATE_PAUSED)) {
            record.put(KEY_POSITION, current.currentPosition).put("isPlaying", current.isPlaying)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                record.put("routedOutputDeviceId", current.routedDevice?.id ?: JSONObject.NULL)
            }
        } else record.put("isPlaying", false)
        return JSONObject(record.toString())
    }

    val DEFINITIONS = listOf(DikcizNativeCommands.Definition(
        TYPE, "dikciz_audio_playback",
        "Manage one process-owned local audio playback session. play prepares asynchronously; poll status for playing/completed/failed. Supports pause/resume, seek, volume and stop, owner-selected output, audio focus and a maximum lifetime. Sessions do not survive process death. Does not play URLs or bypass Android routing/focus restrictions.",
        mapOf(
            KEY_ACTION to JSONObject().put("type", "string").put("enum", org.json.JSONArray(actions.toList())).put("maxLength", 16),
            KEY_SESSION to JSONObject().put("type", "string").put("maxLength", 36),
            KEY_PATH to JSONObject().put("type", "string").put("maxLength", DikcizFileCommands.MAXIMUM_PATH_CHARACTERS),
            KEY_VOLUME to integer(0, 100).put("default", 100),
            KEY_POSITION to integer(0, MAXIMUM_DURATION),
            KEY_DEVICE to integer(0, Int.MAX_VALUE.toLong()),
            KEY_DURATION to integer(1, MAXIMUM_DURATION).put("default", DEFAULT_DURATION),
            KEY_LOOP to JSONObject().put("type", "boolean").put("default", false),
        ),
        setOf(KEY_ACTION), readOnly = false, mainThread = true,
    ))

    fun validate(request: JSONObject) {
        val action = request.getString(KEY_ACTION)
        if (action !in actions) DikcizNativeCommands.invalid("Unsupported audio playback action")
        val supplied = setOf(KEY_PATH, KEY_VOLUME, KEY_POSITION, KEY_DEVICE, KEY_DURATION, KEY_LOOP)
        val allowed = when (action) {
            ACTION_PLAY -> setOf(KEY_PATH, KEY_VOLUME, KEY_DEVICE, KEY_DURATION, KEY_LOOP)
            ACTION_VOLUME -> setOf(KEY_VOLUME)
            ACTION_SEEK -> setOf(KEY_POSITION)
            else -> emptySet()
        }
        if (supplied.any { request.has(it) && it !in allowed }) DikcizNativeCommands.invalid("Playback fields do not match the selected action")
        if (action == ACTION_PLAY) {
            if (request.has(KEY_SESSION)) DikcizNativeCommands.invalid("New playback cannot select an existing session")
            DikcizFileCommands.file(request)
        } else if (action !in setOf(ACTION_STATUS, ACTION_STOP) && !request.has(KEY_SESSION)) {
            DikcizNativeCommands.invalid("Playback controls require the current sessionId")
        }
        if (action == ACTION_VOLUME && !request.has(KEY_VOLUME) || action == ACTION_SEEK && !request.has(KEY_POSITION)) {
            DikcizNativeCommands.invalid("Playback action requires its value")
        }
        if (request.has(KEY_SESSION)) {
            val id = request.getString(KEY_SESSION)
            try {
                if (UUID.fromString(id).toString() != id) DikcizNativeCommands.invalid("Invalid playback session ID")
            } catch (exception: IllegalArgumentException) {
                if (exception is DikcizAutomationRequestException) throw exception
                throw DikcizAutomationRequestException("validation_failed", "Invalid playback session ID").apply { initCause(exception) }
            }
        }
    }

    private fun integer(minimum: Long, maximum: Long) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
}
