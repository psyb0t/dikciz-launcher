package org.fossify.home.dikciz

import android.Manifest
import android.app.ActivityManager
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizMediaCapture(context: Context) {
    private val context: Context = context

    fun cameras(): JSONObject {
        val manager = cameraManager()
        return JSONObject().put("items", JSONArray(manager.cameraIdList.map { id ->
            val info = manager.getCameraCharacteristics(id)
            JSONObject().put("cameraId", id).put("facing", when (info.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                else -> "external"
            }).put("flashAvailable", info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true)
        }))
    }

    @Suppress("DEPRECATION")
    fun photo(request: JSONObject): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_cameraAndMicrophone", "Android camera permission is required")
        }
        requireForeground()
        val manager = cameraManager()
        val id = request.optString("cameraId").ifEmpty { manager.cameraIdList.firstOrNull().orEmpty() }
        if (id !in manager.cameraIdList) {
            DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Camera not found")
        }
        val characteristics = manager.getCameraCharacteristics(id)
        val controls = DikcizCameraControls(request, characteristics)
        val owner = Any()
        if (!cameraOwner.compareAndSet(null, owner)) busy()
        val output = AtomicReference<File?>()
        val camera = AtomicReference<CameraDevice?>()
        val session = AtomicReference<CameraCaptureSession?>()
        val failure = AtomicReference<String?>()
        val active = AtomicBoolean(true)
        val openSettled = AtomicBoolean(false)
        val metadata = AtomicReference<JSONObject?>()
        val completed = CountDownLatch(2)
        fun fail(code: String) {
            failure.compareAndSet(null, code)
            while (completed.count > 0L) completed.countDown()
        }
        val thread = HandlerThread("dikciz-camera-capture").apply { start() }
        val handler = Handler(thread.looper)
        var reader: ImageReader? = null
        var opening = false
        try {
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG).orEmpty()
            val maximum = request.optInt("maxDimension", DEFAULT_DIMENSION)
            val size = sizes.filter { maxOf(it.width, it.height) <= maximum }.maxByOrNull { it.width.toLong() * it.height }
                ?: DikcizNativeCommands.failure("camera_size_unavailable", "No supported JPEG size fits maxDimension")
            val target = captureFile(".jpg")
            output.set(target)
            val images = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            reader = images
            images.setOnImageAvailableListener({ source ->
                try {
                    val image = source.acquireNextImage()
                    if (image != null) {
                        image.use {
                            if (active.get()) {
                                val buffer = it.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                target.outputStream().use { stream -> stream.write(bytes) }
                            }
                        }
                        completed.countDown()
                    }
                } catch (exception: Exception) {
                    fail("camera_image_failed")
                }
            }, handler)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera.set(device)
                    openSettled.set(true)
                    if (!active.get()) {
                        device.close()
                        cameraOwner.compareAndSet(owner, null)
                        thread.quitSafely()
                        return
                    }
                    try {
                        device.createCaptureSession(listOf(images.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(configured: CameraCaptureSession) {
                                session.set(configured)
                                if (!active.get()) { configured.close(); return }
                                try {
                                    val capture = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    capture.addTarget(images.surface)
                                    controls.apply(capture)
                                    configured.capture(capture.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(
                                            captureSession: CameraCaptureSession,
                                            captureRequest: CaptureRequest,
                                            result: TotalCaptureResult,
                                        ) {
                                            if (!active.get()) return
                                            metadata.set(DikcizCameraControls.observed(result))
                                            completed.countDown()
                                        }

                                        override fun onCaptureFailed(
                                            captureSession: CameraCaptureSession,
                                            captureRequest: CaptureRequest,
                                            captureFailure: CaptureFailure,
                                        ) {
                                            fail("camera_capture_failed")
                                        }

                                        override fun onCaptureSequenceAborted(
                                            captureSession: CameraCaptureSession,
                                            sequenceId: Int,
                                        ) {
                                            fail("camera_capture_aborted")
                                        }
                                    }, handler)
                                } catch (exception: Exception) {
                                    fail("camera_capture_failed")
                                }
                            }
                            override fun onConfigureFailed(configured: CameraCaptureSession) {
                                configured.close()
                                fail("camera_configuration_failed")
                            }
                        }, handler)
                    } catch (exception: Exception) {
                        fail("camera_session_failed")
                    }
                }
                override fun onDisconnected(device: CameraDevice) {
                    openSettled.set(true)
                    device.close()
                    fail("camera_disconnected")
                    if (!active.get()) { cameraOwner.compareAndSet(owner, null); thread.quitSafely() }
                }
                override fun onError(device: CameraDevice, error: Int) {
                    openSettled.set(true)
                    device.close()
                    fail("camera_android_error_$error")
                    if (!active.get()) { cameraOwner.compareAndSet(owner, null); thread.quitSafely() }
                }
            }, handler)
            opening = true
            if (!completed.await(request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                DikcizNativeCommands.failure("capture_timeout", "Camera capture timed out")
            }
            failure.get()?.let { DikcizNativeCommands.failure(it, "Camera did not complete capture") }
            if (target.length() == 0L) DikcizNativeCommands.failure("camera_empty_image", "Camera returned no image")
            return mediaResult(target, "image/jpeg").put("cameraId", id).put("width", size.width).put("height", size.height)
                .put("requestedControls", controls.requested())
                .put("captureMetadata", metadata.get()
                    ?: DikcizNativeCommands.failure("camera_metadata_missing", "Camera returned no completed capture metadata"))
                .also { output.set(null) }
        } finally {
            active.set(false)
            val opened = camera.getAndSet(null)
            val callbackOwnsCleanup = opening && !openSettled.get() && opened == null
            try {
                session.getAndSet(null)?.close()
                opened?.close()
                reader?.close()
                if (!callbackOwnsCleanup) {
                    thread.quitSafely()
                    thread.join(CLEANUP_TIMEOUT)
                }
                output.get()?.let { if (it.exists() && !it.delete()) throw java.io.IOException("Could not remove incomplete capture") }
            } finally {
                // Keep late open callbacks alive so they can close the camera after a timeout.
                if (!callbackOwnsCleanup) cameraOwner.compareAndSet(owner, null)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun microphone(request: JSONObject): JSONObject {
        DikcizNativeCommands.requirePermission(context, Manifest.permission.RECORD_AUDIO, "cameraAndMicrophone")
        requireForeground()
        acquireMicrophone()
        var recorder: MediaRecorder? = null
        var target: File? = null
        var success = false
        try {
            target = captureFile(".m4a")
            val duration = request.optLong("durationMilliseconds", DEFAULT_DURATION)
            val recording = MediaRecorder()
            recorder = recording
            recording.setAudioSource(MediaRecorder.AudioSource.MIC)
            recording.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recording.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recording.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            recording.setAudioEncodingBitRate(AUDIO_BIT_RATE)
            recording.setOutputFile(target.absolutePath)
            recording.prepare()
            recording.start()
            val deadline = SystemClock.elapsedRealtime() + duration
            while (SystemClock.elapsedRealtime() < deadline) {
                requireForeground()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recording.activeRecordingConfiguration?.isClientSilenced == true) {
                    DikcizNativeCommands.failure("microphone_silenced", "Android silenced microphone capture")
                }
                Thread.sleep(minOf(AUDIO_CHECK_INTERVAL, maxOf(1L, deadline - SystemClock.elapsedRealtime())))
            }
            recording.stop()
            if (target.length() == 0L) DikcizNativeCommands.failure("microphone_empty_recording", "Microphone returned no audio")
            success = true
            return mediaResult(target, "audio/mp4").put("durationMilliseconds", duration)
                .put("source", "microphone").put("callAudio", false)
        } finally {
            try {
                recorder?.release()
                if (!success) target?.let { if (it.exists() && !it.delete()) throw java.io.IOException("Could not remove incomplete recording") }
            } finally {
                releaseMicrophone()
            }
        }
    }

    fun callAudio(request: JSONObject): JSONObject {
        if (request.getString("action") != "status") {
            DikcizNativeCommands.failure("call_audio_unsupported", "Cellular call capture and audio injection require privileged system integration; microphone recording and device speech do not provide call audio")
        }
        return JSONObject().put("captureSupported", false).put("injectionSupported", false)
            .put("reason", "requires_privileged_system_integration")
    }

    private fun requireForeground() {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        if (state.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz before camera or microphone capture; Android requires foreground eligibility")
        }
    }

    private fun cameraManager(): CameraManager = context.getSystemService(CameraManager::class.java)
        ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Camera service unavailable")

    private fun captureFile(suffix: String): File {
        val root = context.getExternalFilesDir("captures")
            ?: DikcizNativeCommands.failure("capture_storage_unavailable", "App capture storage unavailable")
        return File.createTempFile("capture-", suffix, root)
    }

    private fun mediaResult(file: File, mimeType: String): JSONObject = JSONObject()
        .put("state", "saved").put("path", file.absolutePath).put("mimeType", mimeType).put("bytes", file.length())

    private fun busy(): Nothing = DikcizNativeCommands.failure("hardware_busy", "A request already owns this hardware resource")

    companion object {
        internal val cameraOwner = AtomicReference<Any?>()
        private val microphoneBusy = AtomicBoolean()
        fun acquireMicrophone() {
            if (!microphoneBusy.compareAndSet(false, true)) {
                DikcizNativeCommands.failure("hardware_busy", "A request already owns the microphone")
            }
        }

        fun releaseMicrophone() {
            microphoneBusy.set(false)
        }

        private const val DEFAULT_DIMENSION = 1280
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val DEFAULT_DURATION = 3000L
        private const val CLEANUP_TIMEOUT = 2000L
        private const val AUDIO_CHECK_INTERVAL = 100L
        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AUDIO_BIT_RATE = 128_000
    }
}
