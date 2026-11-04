package org.fossify.home.dikciz

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
internal class DikcizCameraPreview(context: Context, request: JSONObject) : AutoCloseable {
    private val owner = Any()
    private val ready = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    private val openSettled = AtomicBoolean()
    private val openCallFailed = AtomicBoolean()
    private val active = AtomicBoolean(true)
    private val device = AtomicReference<CameraDevice?>()
    private val session = AtomicReference<CameraCaptureSession?>()
    private val error = AtomicReference<String?>()
    private val frames = ArrayBlockingQueue<ByteArray>(1)
    private val thread: HandlerThread
    private val handler: Handler
    private val reader: ImageReader
    private val cameraID: String
    val orientation: Int

    init {
        if (!DikcizMediaCapture.cameraOwner.compareAndSet(null, owner)) {
            DikcizNativeCommands.failure("hardware_busy", "Another operation owns the camera")
        }
        val manager = context.getSystemService(CameraManager::class.java)
        var startedThread: HandlerThread? = null
        try {
            cameraID = request.optString("cameraId").ifBlank { manager.cameraIdList.firstOrNull().orEmpty() }
            if (cameraID !in manager.cameraIdList) DikcizNativeCommands.failure("native_record_not_found", "Camera not found")
            val info = manager.getCameraCharacteristics(cameraID)
            orientation = info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val maximum = request.optInt("maxDimension", DEFAULT_DIMENSION)
            val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG).orEmpty()
            val size = sizes.filter { maxOf(it.width, it.height) <= maximum }.maxByOrNull { it.width.toLong() * it.height }
                ?: DikcizNativeCommands.failure("camera_size_unavailable", "No camera JPEG size fits maxDimension")
            thread = HandlerThread("dikciz-camera-preview").apply { start(); startedThread = this }
            handler = Handler(thread.looper)
            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        } catch (exception: Exception) {
            startedThread?.quitSafely()
            DikcizMediaCapture.cameraOwner.compareAndSet(owner, null)
            throw exception
        }
        reader.setOnImageAvailableListener({ source ->
            try {
                source.acquireLatestImage()?.use { image ->
                    if (active.get()) {
                        val buffer = image.planes[0].buffer
                        if (buffer.remaining() > MAXIMUM_IMAGE_BYTES) fail("camera_frame_too_large")
                        else {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            frames.poll()
                            frames.offer(bytes)
                        }
                    }
                }
            } catch (_: RuntimeException) { fail("camera_image_failed") }
        }, handler)
        try {
            manager.openCamera(cameraID, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    openSettled.set(true)
                    device.set(camera)
                    if (!active.get()) { camera.close(); return }
                    try {
                        camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(configured: CameraCaptureSession) {
                                if (!active.get()) { configured.close(); return }
                                session.set(configured)
                                ready.countDown()
                            }
                            override fun onConfigureFailed(configured: CameraCaptureSession) {
                                configured.close(); fail("camera_configuration_failed")
                            }
                        }, handler)
                    } catch (_: Exception) { fail("camera_session_failed") }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    openSettled.set(true); fail("camera_disconnected"); camera.close()
                }
                override fun onError(camera: CameraDevice, errorCode: Int) {
                    openSettled.set(true); fail("camera_android_error_" + errorCode); camera.close()
                }
                override fun onClosed(camera: CameraDevice) {
                    DikcizMediaCapture.cameraOwner.compareAndSet(owner, null)
                    closed.countDown()
                    if (!active.get()) thread.quitSafely()
                }
            }, handler)
        } catch (exception: Exception) {
            openSettled.set(true)
            openCallFailed.set(true)
            close()
            throw exception
        }
    }

    fun frame(timeout: Long): ByteArray {
        if (!ready.await(timeout, TimeUnit.MILLISECONDS)) DikcizNativeCommands.failure("capture_timeout", "Camera preview did not become ready")
        error.get()?.let { DikcizNativeCommands.failure(it, "Camera preview failed") }
        val camera = device.get() ?: DikcizNativeCommands.failure("camera_closed", "Camera closed")
        frames.clear()
        val capture = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY.toByte())
        }.build()
        session.get()?.capture(capture, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                fail("camera_capture_failed")
            }
        }, handler) ?: DikcizNativeCommands.failure("camera_closed", "Camera capture session closed")
        val bytes = frames.poll(timeout, TimeUnit.MILLISECONDS)
        error.get()?.let { DikcizNativeCommands.failure(it, "Camera preview failed") }
        return bytes ?: DikcizNativeCommands.failure("capture_timeout", "Camera preview frame timed out")
    }

    private fun fail(code: String) { error.compareAndSet(null, code); ready.countDown() }

    override fun close() {
        if (!active.compareAndSet(true, false)) return
        session.getAndSet(null)?.close()
        device.getAndSet(null)?.close()
        if (openCallFailed.get()) {
            DikcizMediaCapture.cameraOwner.compareAndSet(owner, null)
            closed.countDown()
        }
        var interrupted = false
        try { closed.await(CLEANUP_MILLISECONDS, TimeUnit.MILLISECONDS) }
        catch (_: InterruptedException) { interrupted = true }
        finally {
            reader.close()
            if (closed.count == 0L) thread.quitSafely()
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    fun hardwareReleaseConfirmed(): Boolean = DikcizMediaCapture.cameraOwner.get() !== owner

    private companion object {
        const val DEFAULT_DIMENSION = 640
        const val MAXIMUM_IMAGE_BYTES = 2097152
        const val JPEG_QUALITY = 70
        const val CLEANUP_MILLISECONDS = 3000L
    }
}
