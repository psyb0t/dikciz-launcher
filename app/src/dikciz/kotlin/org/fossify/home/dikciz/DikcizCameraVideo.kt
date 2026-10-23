package org.fossify.home.dikciz

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal class DikcizCameraVideo(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    @Suppress("DEPRECATION")
    fun record(request: JSONObject): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_cameraAndMicrophone", "Android camera permission is required")
        }
        val withAudio = request.optBoolean("withAudio", false)
        if (withAudio) DikcizNativeCommands.requirePermission(context, Manifest.permission.RECORD_AUDIO, "cameraAndMicrophone")
        foreground()
        val manager = context.getSystemService(CameraManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Camera service unavailable")
        val id = request.optString("cameraId").ifEmpty { manager.cameraIdList.firstOrNull().orEmpty() }
        if (id !in manager.cameraIdList) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Camera not found")
        val info = manager.getCameraCharacteristics(id)
        val maximum = request.optInt("maxDimension", DEFAULT_DIMENSION)
        val size = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java)
            ?.filter { maxOf(it.width, it.height) <= maximum }?.maxByOrNull { it.width.toLong() * it.height }
            ?: DikcizNativeCommands.failure("camera_video_size_unavailable", "No recorder size fits maxDimension")
        val fps = request.optInt("frameRate", DEFAULT_FRAME_RATE)
        val range = info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.contains(fps) }?.minWithOrNull(compareBy<Range<Int>> { it.upper - it.lower }.thenBy { it.upper })
            ?: DikcizNativeCommands.failure("camera_video_frame_rate_unsupported", "No camera frame-rate range contains frameRate")
        val owner = Any()
        if (!DikcizMediaCapture.cameraOwner.compareAndSet(null, owner)) {
            DikcizNativeCommands.failure("hardware_busy", "Another request owns the camera")
        }
        val deadline = SystemClock.elapsedRealtime() + request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT)
        val lifecycle = Any()
        val ready = CountDownLatch(1)
        val failure = AtomicReference<String?>()
        val frames = AtomicLong()
        val thread = HandlerThread("dikciz-camera-video").apply { start() }
        val handler = Handler(thread.looper)
        var active = true
        var opening = false
        var openSettled = false
        var camera: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var recorder: MediaRecorder? = null
        var surface: Surface? = null
        var target: File? = null
        var microphoneOwned = false
        var saved = false
        fun fail(code: String) {
            failure.compareAndSet(null, code)
            ready.countDown()
        }
        try {
            if (withAudio) {
                DikcizMediaCapture.acquireMicrophone()
                microphoneOwned = true
            }
            val root = context.getExternalFilesDir("captures")
                ?: DikcizNativeCommands.failure("capture_storage_unavailable", "App capture storage unavailable")
            val file = File.createTempFile("video-", ".mp4", root)
            target = file
            val recording = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            recorder = recording
            if (withAudio) recording.setAudioSource(MediaRecorder.AudioSource.MIC)
            recording.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recording.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recording.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recording.setVideoSize(size.width, size.height)
            recording.setVideoFrameRate(fps)
            recording.setVideoEncodingBitRate(request.optInt("bitRate", DEFAULT_BIT_RATE))
            recording.setOrientationHint(request.optInt("orientation", 0))
            if (withAudio) {
                recording.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recording.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                recording.setAudioEncodingBitRate(AUDIO_BIT_RATE)
                recording.setAudioChannels(1)
            }
            recording.setOutputFile(file.absolutePath)
            recording.setOnErrorListener { _, _, _ -> fail("camera_video_recorder_failed") }
            recording.prepare()
            val input = recording.surface
            surface = input
            if (SystemClock.elapsedRealtime() >= deadline) timeout()
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    synchronized(lifecycle) {
                        openSettled = true
                        if (!active) {
                            device.close()
                            DikcizMediaCapture.cameraOwner.compareAndSet(owner, null)
                            thread.quitSafely()
                            return
                        }
                        camera = device
                        try {
                            device.createCaptureSession(listOf(input), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(configured: CameraCaptureSession) {
                                    synchronized(lifecycle) {
                                        if (!active) { configured.close(); return }
                                        session = configured
                                        try {
                                            val capture = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                                            capture.addTarget(input)
                                            capture.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
                                            configured.setRepeatingRequest(capture.build(), object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    captureSession: CameraCaptureSession,
                                                    captureRequest: CaptureRequest,
                                                    result: TotalCaptureResult,
                                                ) { frames.incrementAndGet() }

                                                override fun onCaptureFailed(
                                                    captureSession: CameraCaptureSession,
                                                    captureRequest: CaptureRequest,
                                                    captureFailure: CaptureFailure,
                                                ) {
                                                    synchronized(lifecycle) {
                                                        if (active) fail("camera_video_frame_failed")
                                                    }
                                                }
                                            }, handler)
                                            recording.start()
                                            ready.countDown()
                                        } catch (exception: RuntimeException) {
                                            fail("camera_video_start_failed")
                                        }
                                    }
                                }

                                override fun onConfigureFailed(configured: CameraCaptureSession) {
                                    configured.close()
                                    fail("camera_video_configuration_failed")
                                }
                            }, handler)
                        } catch (exception: Exception) {
                            fail("camera_video_session_failed")
                        }
                    }
                }

                override fun onDisconnected(device: CameraDevice) = closed(device, "camera_disconnected")
                override fun onError(device: CameraDevice, error: Int) = closed(device, "camera_android_error_" + error)

                private fun closed(device: CameraDevice, code: String) {
                    synchronized(lifecycle) {
                        openSettled = true
                        device.close()
                        fail(code)
                        if (!active) {
                            DikcizMediaCapture.cameraOwner.compareAndSet(owner, null)
                            thread.quitSafely()
                        }
                    }
                }
            }, handler)
            opening = true
            if (!ready.await(maxOf(1L, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS)) timeout()
            failure.get()?.let { DikcizNativeCommands.failure(it, "Android did not start camera video recording") }
            val end = SystemClock.elapsedRealtime() + request.optLong("durationMilliseconds", DEFAULT_DURATION)
            while (SystemClock.elapsedRealtime() < end) {
                foreground()
                failure.get()?.let { DikcizNativeCommands.failure(it, "Android interrupted camera video recording") }
                if (SystemClock.elapsedRealtime() >= deadline) timeout()
                if (withAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    recording.activeRecordingConfiguration?.isClientSilenced == true) {
                    DikcizNativeCommands.failure("microphone_silenced", "Android silenced the video's microphone")
                }
                Thread.sleep(minOf(CHECK_INTERVAL, maxOf(1L, end - SystemClock.elapsedRealtime())))
            }
            synchronized(lifecycle) {
                active = false
                session?.stopRepeating()
                session?.abortCaptures()
                recording.stop()
            }
            val result = inspect(file).put("cameraId", id).put("observedCaptureFrames", frames.get())
                .put("requestedDurationMilliseconds", request.optLong("durationMilliseconds", DEFAULT_DURATION))
                .put("requestedFrameRate", fps)
                .put("cameraFrameRateMinimum", range.lower).put("cameraFrameRateMaximum", range.upper)
            if (frames.get() == 0L) DikcizNativeCommands.failure("camera_video_empty", "Camera returned no completed video frames")
            if (withAudio && !result.getBoolean("hasAudio")) {
                DikcizNativeCommands.failure("camera_video_audio_missing", "Finalized video has no requested audio track")
            }
            saved = true
            return result
        } finally {
            try {
                synchronized(lifecycle) {
                    active = false
                    try { session?.close() }
                    finally {
                        try { camera?.close() }
                        finally {
                            try { recorder?.release() }
                            finally { surface?.release() }
                        }
                    }
                }
            } finally {
                synchronized(lifecycle) {
                    if (!opening || openSettled || camera != null) {
                        thread.quitSafely()
                        DikcizMediaCapture.cameraOwner.compareAndSet(owner, null)
                    }
                }
                try {
                    if (!thread.isAlive || !opening || synchronized(lifecycle) { openSettled || camera != null }) {
                        thread.join(CLEANUP_TIMEOUT)
                    }
                } finally {
                    if (microphoneOwned) DikcizMediaCapture.releaseMicrophone()
                    if (!saved) target?.let {
                        if (it.exists() && !it.delete()) logger.warn("camera_video_incomplete_file_cleanup_failed")
                    }
                }
            }
        }
    }

    private fun inspect(file: File): JSONObject {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (file.length() == 0L || duration == null || duration <= 0 || width == null || height == null) {
                DikcizNativeCommands.failure("camera_video_empty", "Android could not confirm finalized video metadata")
            }
            return JSONObject().put("state", "saved").put("path", file.absolutePath).put("mimeType", "video/mp4")
                .put("bytes", file.length()).put("durationMilliseconds", duration).put("width", width).put("height", height)
                .put("rotation", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: JSONObject.NULL)
                .put("hasAudio", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
        } finally {
            retriever.release()
        }
    }

    private fun foreground() {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        if (state.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz before camera video recording")
        }
    }

    private fun timeout(): Nothing = DikcizNativeCommands.failure("capture_timeout", "Camera video recording exceeded its overall deadline")

    companion object {
        const val TYPE = "cameraVideoRecord"
        private const val DEFAULT_DIMENSION = 1280
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_BIT_RATE = 4_000_000
        private const val DEFAULT_DURATION = 3000L
        private const val DEFAULT_TIMEOUT = 30000L
        private const val CHECK_INTERVAL = 100L
        private const val CLEANUP_TIMEOUT = 2000L
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_BIT_RATE = 128000
        val DEFINITION = DikcizNativeCommands.Definition(TYPE, "dikciz_camera_video_record",
            "Record bounded foreground camera H.264 MP4, optionally with microphone AAC. Returns only after finalization and Android metadata inspection. Shares camera/microphone ownership; incomplete files are removed. No background service or preview. Use jobStart for reconnectable results.",
            mapOf(
                "cameraId" to JSONObject().put("type", "string").put("maxLength", 128),
                "durationMilliseconds" to integer(1000, 120000).put("default", DEFAULT_DURATION),
                "timeoutMilliseconds" to integer(1, 300000).put("default", DEFAULT_TIMEOUT),
                "maxDimension" to integer(320, 1920).put("default", DEFAULT_DIMENSION),
                "frameRate" to integer(5, 60).put("default", DEFAULT_FRAME_RATE),
                "bitRate" to integer(500000, 20000000).put("default", DEFAULT_BIT_RATE),
                "orientation" to integer(0, 270).put("default", 0),
                "withAudio" to JSONObject().put("type", "boolean").put("default", false),
            ), readOnly = false)

        fun validate(request: JSONObject) {
            if (request.optInt("orientation", 0) !in setOf(0, 90, 180, 270)) {
                DikcizNativeCommands.invalid("Video orientation must be 0, 90, 180 or 270")
            }
            if (request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT) < request.optLong("durationMilliseconds", DEFAULT_DURATION)) {
                DikcizNativeCommands.invalid("Video timeout must cover the requested recording duration")
            }
        }

        private fun integer(minimum: Int, maximum: Int) =
            JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    }
}
