package org.fossify.home.dikciz

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.fossify.home.dikciz.DikcizMicrophoneStreamFields as StreamFields
import org.fossify.home.dikciz.DikcizMicrophoneStreamOptions as StreamOptions
import org.fossify.home.dikciz.DikcizReplayFields as ReplayFields

internal class DikcizMicrophoneStream(
    val id: String,
    private val context: Context,
    val options: StreamOptions,
    private val logger: () -> DikcizLogger,
) {
    private val state = AtomicReference(DikcizMicrophoneStreamState.Starting)
    private val failure = AtomicReference<DikcizAutomationRequestException?>()
    private val stopRequested = AtomicBoolean()
    private val stopReason = AtomicReference(REASON_STOPPED)
    private val serviceStarted = CountDownLatch(1)
    private val recordingStarted = CountDownLatch(1)
    private val startedSuccessfully = AtomicBoolean()
    private val foregroundService = AtomicBoolean()
    private val hardwareReleased = AtomicBoolean()
    private val cleanupFailed = AtomicBoolean()
    private val recorder = AtomicReference<AudioRecord?>()
    private val audioLock = Any()
    private val lifecycleLock = Any()
    private val totalFrames = AtomicLong()
    private val replay = DikcizBoundedReplay(options.bufferBytes, options.maximumEvents)
    @Volatile private var finishedAt = Long.MAX_VALUE
    @Volatile private var routedInput: Int? = null
    @Volatile private var sampleRate = options.sampleRate
    @Volatile private var channels = options.channels

    fun launch() {
        workers.execute(::consume)
    }

    fun awaitStart(): JSONObject {
        try {
            if (!recordingStarted.await(options.startupTimeout, TimeUnit.MILLISECONDS)) {
                requestStop(REASON_STARTUP_TIMEOUT)
                DikcizNativeCommands.failure(ERROR_START_TIMEOUT, MESSAGE_START_TIMEOUT)
            }
        } catch (exception: InterruptedException) {
            requestStop(REASON_CANCELLED)
            throw exception
        }
        if (!startedSuccessfully.get()) {
            throw failure.get() ?: DikcizAutomationRequestException(ERROR_START_CANCELLED, MESSAGE_START_CANCELLED)
        }
        return metadata()
    }

    fun serviceReady(): Boolean {
        foregroundService.set(true)
        serviceStarted.countDown()
        return !stopRequested.get() && !state.get().terminal
    }

    fun serviceFailed(exception: RuntimeException) {
        fail(ERROR_SERVICE_START, MESSAGE_SERVICE_START, exception)
        serviceStarted.countDown()
    }

    fun serviceDestroyed() {
        foregroundService.set(false)
        if (!state.get().terminal && !stopRequested.get()) {
            fail(ERROR_SERVICE_STOPPED, MESSAGE_SERVICE_STOPPED)
            requestStop(REASON_SERVICE_STOPPED)
        }
        serviceStarted.countDown()
    }

    fun requestStop(reason: String = REASON_STOPPED): Boolean {
        synchronized(lifecycleLock) {
            if (state.get().terminal || stopRequested.get()) return false
            stopReason.set(reason)
            stopRequested.set(true)
            state.set(DikcizMicrophoneStreamState.Stopping)
        }
        controls.execute {
            synchronized(audioLock) {
                val current = recorder.get()
                if (current != null && current.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    try {
                        current.stop()
                    } catch (exception: IllegalStateException) {
                        logger().warn(EVENT_STOP_PENDING, mapOf(ReplayFields.STREAM_ID to id, ERROR_CLASS to exception.javaClass.simpleName))
                    }
                }
            }
        }
        return true
    }

    fun metadata(): JSONObject = replay.metadata()
        .put(ReplayFields.STREAM_ID, id).put(ReplayFields.STATE, state.get().wireValue).put(ReplayFields.ACTIVE, !state.get().terminal)
        .put(StreamFields.SAMPLE_RATE, sampleRate).put(StreamFields.CHANNELS, channels).put(StreamFields.ENCODING, PCM_ENCODING)
        .put(StreamFields.SOURCE, SOURCE_MICROPHONE).put(StreamFields.CALL_AUDIO, false)
        .put(StreamFields.TOTAL_FRAMES, totalFrames.get()).put(StreamFields.BYTES, totalFrames.get() * channels * StreamOptions.BYTES_PER_SAMPLE)
        .put(StreamFields.INPUT_DEVICE, options.inputDeviceId ?: JSONObject.NULL)
        .put(StreamFields.ROUTED_INPUT, routedInput ?: JSONObject.NULL)
        .put(StreamFields.FOREGROUND_SERVICE, foregroundService.get())
        .put(StreamFields.NOTIFICATION_POSTING, context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true)
        .put(StreamFields.CLEANUP_FAILED, cleanupFailed.get()).put(StreamFields.RELEASE_CONFIRMED, hardwareReleased.get())
        .put(StreamFields.ERROR, failure.get()?.code ?: JSONObject.NULL).put(StreamFields.REASON, if (state.get().terminal) failure.get()?.code ?: stopReason.get() else JSONObject.NULL)
        .put(StreamFields.BUFFER_BYTES, options.bufferBytes).put(StreamFields.MAXIMUM_EVENTS, options.maximumEvents)
        .put(StreamFields.RETENTION, options.retentionMinutes).put(StreamFields.DURATION, options.maximumDuration)

    fun read(cursor: Long, limit: Int, wait: Long): JSONObject {
        val snapshot = metadata()
        val page = replay.read(cursor, limit, wait)
        return snapshot.apply { page.keys().forEach { key -> put(key, page.get(key)) } }
    }

    fun isFinished(): Boolean = state.get().terminal

    fun expired(now: Long): Boolean =
        isFinished() && !cleanupFailed.get() && now - finishedAt >= options.retentionMinutes * StreamOptions.MILLISECONDS_PER_MINUTE

    private fun consume() {
        var serviceRequested = false
        try {
            DikcizMicrophoneCaptureService.start(context, id)
            serviceRequested = true
            if (!serviceStarted.await(options.startupTimeout, TimeUnit.MILLISECONDS)) {
                DikcizNativeCommands.failure(ERROR_START_TIMEOUT, MESSAGE_START_TIMEOUT)
            }
            failure.get()?.let { throw it }
            if (stopRequested.get()) return
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                DikcizNativeCommands.failure(ERROR_PERMISSION, MESSAGE_PERMISSION)
            }
            val format = AudioFormat.Builder().setSampleRate(options.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            if (options.channels == MONO_CHANNELS) format.setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            else format.setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            val minimumBuffer = AudioRecord.getMinBufferSize(
                options.sampleRate,
                if (options.channels == MONO_CHANNELS) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minimumBuffer !in 1..MAXIMUM_PLATFORM_BUFFER) {
                DikcizNativeCommands.failure(ERROR_FORMAT, MESSAGE_FORMAT)
            }
            val current = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format.build())
                .setBufferSizeInBytes(maxOf(minimumBuffer, options.chunkBytes) * BUFFER_MULTIPLIER).build()
            recorder.set(current)
            if (current.state != AudioRecord.STATE_INITIALIZED) DikcizNativeCommands.failure(ERROR_FORMAT, MESSAGE_FORMAT)
            if (options.inputDeviceId != null) {
                val audio = context.getSystemService(AudioManager::class.java)
                    ?: DikcizNativeCommands.failure(ERROR_AUDIO_SERVICE, MESSAGE_AUDIO_SERVICE)
                val selected = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).find { it.id == options.inputDeviceId }
                    ?: DikcizNativeCommands.failure(ERROR_INPUT_NOT_FOUND, MESSAGE_INPUT_NOT_FOUND)
                if (!current.setPreferredDevice(selected)) DikcizNativeCommands.failure(ERROR_INPUT_REJECTED, MESSAGE_INPUT_REJECTED)
            }
            synchronized(audioLock) {
                if (stopRequested.get()) return
                current.startRecording()
            }
            if (current.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                DikcizNativeCommands.failure(ERROR_NOT_RECORDING, MESSAGE_NOT_RECORDING)
            }
            sampleRate = current.sampleRate
            channels = current.channelCount
            synchronized(lifecycleLock) {
                if (stopRequested.get()) return
                state.set(DikcizMicrophoneStreamState.Recording)
                startedSuccessfully.set(true)
                replay.append(JSONObject().put(ReplayFields.EVENT, EVENT_START).put(StreamFields.SAMPLE_RATE, sampleRate)
                    .put(StreamFields.CHANNELS, channels).put(StreamFields.ENCODING, PCM_ENCODING))
                recordingStarted.countDown()
            }
            logger().info(EVENT_STARTED, mapOf(ReplayFields.STREAM_ID to id))
            val deadline = SystemClock.elapsedRealtime() + options.maximumDuration
            val samples = ShortArray(options.chunkSamples)
            val bytes = ByteBuffer.allocate(options.chunkBytes).order(ByteOrder.LITTLE_ENDIAN)
            while (!stopRequested.get()) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    stopReason.set(REASON_EXPIRED)
                    break
                }
                if (!foregroundService.get()) DikcizNativeCommands.failure(ERROR_SERVICE_STOPPED, MESSAGE_SERVICE_STOPPED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    current.activeRecordingConfiguration?.isClientSilenced == true) {
                    DikcizNativeCommands.failure(ERROR_SILENCED, MESSAGE_SILENCED)
                }
                val received = current.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (stopRequested.get()) break
                if (received < 0) DikcizNativeCommands.failure(ERROR_READ, MESSAGE_READ)
                if (received == 0) {
                    Thread.sleep(EMPTY_READ_BACKOFF)
                    continue
                }
                if (received % channels != 0) DikcizNativeCommands.failure(ERROR_ALIGNMENT, MESSAGE_ALIGNMENT)
                bytes.clear()
                for (index in 0 until received) bytes.putShort(samples[index])
                val frames = received / channels
                val firstFrame = totalFrames.getAndAdd(frames.toLong())
                routedInput = current.routedDevice?.id
                val timestamp = AudioTimestamp()
                val hasTimestamp = current.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
                replay.append(JSONObject().put(ReplayFields.EVENT, EVENT_AUDIO)
                    .put(StreamFields.AUDIO_BASE64, Base64.getEncoder().encodeToString(bytes.array().copyOf(received * StreamOptions.BYTES_PER_SAMPLE)))
                    .put(StreamFields.FIRST_FRAME, firstFrame).put(StreamFields.FRAMES, frames)
                    .put(StreamFields.OBSERVED_AT, SystemClock.elapsedRealtimeNanos())
                    .put(StreamFields.AUDIO_TIMESTAMP, if (hasTimestamp) timestamp.nanoTime else JSONObject.NULL)
                    .put(StreamFields.AUDIO_FRAME_POSITION, if (hasTimestamp) timestamp.framePosition else JSONObject.NULL)
                    .put(StreamFields.AUDIO_TIMEBASE, TIMEBASE_MONOTONIC))
                logger().debug(EVENT_CHUNK, mapOf(ReplayFields.STREAM_ID to id, StreamFields.FRAMES to frames))
            }
        } catch (exception: Exception) {
            if (exception is DikcizAutomationRequestException) failure.compareAndSet(null, exception)
            else fail(ERROR_CAPTURE, MESSAGE_CAPTURE, exception)
            logger().warn(EVENT_FAILED, mapOf(ReplayFields.STREAM_ID to id, ERROR_CODE to (failure.get()?.code ?: ERROR_CAPTURE), ERROR_CLASS to exception.javaClass.simpleName))
        } finally {
            synchronized(audioLock) {
                try {
                    recorder.get()?.release()
                    recorder.set(null)
                    hardwareReleased.set(true)
                } catch (exception: RuntimeException) {
                    cleanupFailed.set(true)
                    fail(ERROR_CLEANUP, MESSAGE_CLEANUP, exception)
                    logger().error(EVENT_CLEANUP_FAILED, mapOf(ReplayFields.STREAM_ID to id, ERROR_CLASS to exception.javaClass.simpleName))
                }
            }
            if (hardwareReleased.get()) DikcizMediaCapture.releaseMicrophone()
            val terminal = synchronized(lifecycleLock) {
                val result = when {
                    failure.get() != null -> DikcizMicrophoneStreamState.Failed
                    stopReason.get() == REASON_EXPIRED -> DikcizMicrophoneStreamState.Expired
                    else -> DikcizMicrophoneStreamState.Stopped
                }
                replay.append(JSONObject().put(ReplayFields.EVENT, EVENT_EXIT).put(ReplayFields.STATE, result.wireValue)
                    .put(StreamFields.ERROR, failure.get()?.code ?: JSONObject.NULL).put(StreamFields.REASON, failure.get()?.code ?: stopReason.get()))
                finishedAt = SystemClock.elapsedRealtime()
                state.set(result)
                replay.finish()
                recordingStarted.countDown()
                result
            }
            if (serviceRequested && !cleanupFailed.get()) {
                try { DikcizMicrophoneCaptureService.stop(context, id) }
                catch (exception: RuntimeException) {
                    logger().warn(EVENT_SERVICE_STOP_PENDING, mapOf(ReplayFields.STREAM_ID to id, ERROR_CLASS to exception.javaClass.simpleName))
                }
            }
            logger().info(EVENT_FINISHED, mapOf(ReplayFields.STREAM_ID to id, ReplayFields.STATE to terminal.wireValue, StreamFields.TOTAL_FRAMES to totalFrames.get()))
        }
    }

    private fun fail(code: String, message: String, cause: Throwable? = null) {
        val exception = DikcizAutomationRequestException(code, message)
        if (cause != null) exception.initCause(cause)
        failure.compareAndSet(null, exception)
    }

    companion object {
        private val workers = Executors.newSingleThreadExecutor { task -> Thread(task, WORKER_THREAD).apply { isDaemon = true } }
        private val controls = Executors.newSingleThreadExecutor { task -> Thread(task, CONTROL_THREAD).apply { isDaemon = true } }
        private const val WORKER_THREAD = "dikciz-microphone-stream"
        private const val CONTROL_THREAD = "dikciz-microphone-stop"
        private const val MAXIMUM_PLATFORM_BUFFER = 8388608
        private const val BUFFER_MULTIPLIER = 2
        private const val MONO_CHANNELS = 1
        private const val EMPTY_READ_BACKOFF = 10L
        private const val PCM_ENCODING = "pcm_s16le"
        private const val SOURCE_MICROPHONE = "microphone"
        private const val TIMEBASE_MONOTONIC = "monotonic"
        private const val EVENT_START = "start"
        private const val EVENT_AUDIO = "audio"
        private const val EVENT_EXIT = "exit"
        private const val REASON_STOPPED = "stopped"
        private const val REASON_EXPIRED = "duration_limit"
        private const val REASON_STARTUP_TIMEOUT = "startup_timeout"
        private const val REASON_CANCELLED = "cancelled"
        private const val REASON_SERVICE_STOPPED = "service_stopped"
        private const val ERROR_CLASS = "error_class"
        private const val ERROR_CODE = "error_code"
        private const val EVENT_STARTED = "microphone_stream_started"
        private const val EVENT_CHUNK = "microphone_stream_chunk"
        private const val EVENT_FAILED = "microphone_stream_failed"
        private const val EVENT_FINISHED = "microphone_stream_finished"
        private const val EVENT_STOP_PENDING = "microphone_stream_stop_pending"
        private const val EVENT_CLEANUP_FAILED = "microphone_stream_cleanup_failed"
        private const val EVENT_SERVICE_STOP_PENDING = "microphone_stream_service_stop_pending"
        private const val ERROR_START_TIMEOUT = "microphone_stream_start_timeout"
        private const val ERROR_START_CANCELLED = "microphone_stream_start_cancelled"
        private const val ERROR_SERVICE_START = "microphone_service_start_failed"
        private const val ERROR_SERVICE_STOPPED = "microphone_service_stopped"
        private const val ERROR_PERMISSION = "native_permission_cameraAndMicrophone"
        private const val ERROR_FORMAT = "microphone_stream_format_unavailable"
        private const val ERROR_AUDIO_SERVICE = "native_unavailable"
        private const val ERROR_INPUT_NOT_FOUND = "audio_device_not_found"
        private const val ERROR_INPUT_REJECTED = "audio_input_rejected"
        private const val ERROR_NOT_RECORDING = "microphone_stream_not_recording"
        private const val ERROR_SILENCED = "microphone_silenced"
        private const val ERROR_READ = "microphone_stream_read_failed"
        private const val ERROR_ALIGNMENT = "microphone_stream_frame_invalid"
        private const val ERROR_CAPTURE = "microphone_stream_capture_failed"
        private const val ERROR_CLEANUP = "microphone_stream_cleanup_failed"
        private const val MESSAGE_START_TIMEOUT = "Microphone stream startup timed out; inspect retained streams for cleanup"
        private const val MESSAGE_START_CANCELLED = "Microphone stream stopped before recording began"
        private const val MESSAGE_SERVICE_START = "Android rejected the microphone foreground service"
        private const val MESSAGE_SERVICE_STOPPED = "Android stopped the microphone foreground service"
        private const val MESSAGE_PERMISSION = "Android microphone permission is required"
        private const val MESSAGE_FORMAT = "Android cannot initialize this PCM recording format"
        private const val MESSAGE_AUDIO_SERVICE = "Android audio service unavailable"
        private const val MESSAGE_INPUT_NOT_FOUND = "Audio input device not found"
        private const val MESSAGE_INPUT_REJECTED = "Android rejected the preferred microphone input"
        private const val MESSAGE_NOT_RECORDING = "Android did not enter microphone recording state"
        private const val MESSAGE_SILENCED = "Android silenced microphone capture"
        private const val MESSAGE_READ = "Android microphone read failed"
        private const val MESSAGE_ALIGNMENT = "Android returned an incomplete PCM frame"
        private const val MESSAGE_CAPTURE = "Android microphone capture failed"
        private const val MESSAGE_CLEANUP = "Microphone release was not confirmed; restart Dikciz before another capture"
    }
}
