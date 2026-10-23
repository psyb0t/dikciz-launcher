package org.fossify.home.dikciz

internal object DikcizMicrophoneStreamFields {
    const val SAMPLE_RATE = "sampleRateHz"
    const val CHANNELS = "channels"
    const val CHUNK_MILLISECONDS = "chunkMilliseconds"
    const val DURATION = "maximumDurationMilliseconds"
    const val STARTUP_TIMEOUT = "startupTimeoutMilliseconds"
    const val BUFFER_BYTES = "bufferBytes"
    const val MAXIMUM_EVENTS = "maximumEvents"
    const val RETENTION = "retentionMinutes"
    const val INPUT_DEVICE = "inputDeviceId"
    const val AUDIO_BASE64 = "audioBase64"
    const val FRAMES = "frames"
    const val FIRST_FRAME = "firstFrame"
    const val TOTAL_FRAMES = "totalFrames"
    const val ENCODING = "encoding"
    const val SOURCE = "source"
    const val CALL_AUDIO = "callAudio"
    const val ROUTED_INPUT = "routedInputDeviceId"
    const val FOREGROUND_SERVICE = "foregroundService"
    const val NOTIFICATION_POSTING = "notificationPostingEnabled"
    const val CLEANUP_FAILED = "cleanupFailed"
    const val RELEASE_CONFIRMED = "hardwareReleaseConfirmed"
    const val ERROR = "error"
    const val REASON = "reason"
    const val CANCELLATION_REQUESTED = "cancellationRequested"
    const val OBSERVED_AT = "observedAtElapsedNanoseconds"
    const val AUDIO_TIMESTAMP = "audioTimestampNanoseconds"
    const val AUDIO_FRAME_POSITION = "audioFramePosition"
    const val AUDIO_TIMEBASE = "audioTimestampTimebase"
    const val BYTES = "bytes"
}

internal data class DikcizMicrophoneStreamOptions(
    val sampleRate: Int,
    val channels: Int,
    val chunkMilliseconds: Int,
    val maximumDuration: Long,
    val startupTimeout: Long,
    val bufferBytes: Int,
    val maximumEvents: Int,
    val retentionMinutes: Int,
    val inputDeviceId: Int?,
) {
    val chunkSamples = sampleRate * chunkMilliseconds / MILLISECONDS_PER_SECOND * channels
    val chunkBytes = chunkSamples * BYTES_PER_SAMPLE

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_CHANNELS = 1
        const val DEFAULT_CHUNK_MILLISECONDS = 100
        const val DEFAULT_DURATION = 30000L
        const val DEFAULT_STARTUP_TIMEOUT = 30000L
        const val DEFAULT_BUFFER = 2097152
        const val MINIMUM_BUFFER = 65536
        const val MAXIMUM_BUFFER = 8388608
        const val DEFAULT_EVENTS = 1024
        const val MAXIMUM_EVENTS = 4096
        const val DEFAULT_RETENTION = 60
        const val MAXIMUM_RETENTION = 1440
        const val MINIMUM_CHUNK_MILLISECONDS = 20
        const val MAXIMUM_CHUNK_MILLISECONDS = 1000
        const val MINIMUM_DURATION = 100L
        const val MAXIMUM_STARTUP_TIMEOUT = 120000L
        const val MILLISECONDS_PER_SECOND = 1000
        const val MILLISECONDS_PER_MINUTE = 60000L
        const val BYTES_PER_SAMPLE = 2
        const val EVENT_OVERHEAD_BYTES = 2048
        const val MINIMUM_SAMPLE_RATE = 4000
        const val MAXIMUM_SAMPLE_RATE = 192000
    }
}

internal enum class DikcizMicrophoneStreamState(val wireValue: String, val terminal: Boolean = false) {
    Starting("starting"),
    Recording("recording"),
    Stopping("stopping"),
    Stopped("stopped", true),
    Expired("expired", true),
    Failed("failed", true),
}
