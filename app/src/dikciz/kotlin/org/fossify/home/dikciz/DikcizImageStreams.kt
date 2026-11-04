package org.fossify.home.dikciz

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

internal object DikcizImageStreams {
    const val TYPE_START = "imageStreamStart"
    const val TYPE_READ = "imageStreamRead"
    const val TYPE_STOP = "imageStreamStop"
    const val TYPE_LIST = "imageStreamsList"
    const val TYPE_FORGET = "imageStreamForget"
    private const val MAXIMUM_STREAMS = 4
    private const val RETENTION_MILLISECONDS = 300000L
    private const val MAXIMUM_JPEG_BYTES = 196608
    private const val CLEANUP_MILLISECONDS = 5000L
    private val streams = linkedMapOf<String, Session>()
    private val IDS = Regex("[a-f0-9-]{36}")

    private class Session(val context: Context, val request: JSONObject) {
        val id = UUID.randomUUID().toString()
        val source = request.getString("source")
        val started = SystemClock.elapsedRealtime()
        val ready = CountDownLatch(1)
        val replay = DikcizBoundedReplay(request.optInt("bufferBytes", 1048576), request.optInt("maximumEvents", 16))
        @Volatile var state = "starting"
        @Volatile var error: String? = null
        @Volatile var finishedAt = 0L
        @Volatile var cancelled = false
        @Volatile var released = false
        @Volatile var camera: DikcizCameraPreview? = null
        fun hardwareReleased(): Boolean = camera?.hardwareReleaseConfirmed() ?: released
        @Volatile var thread: Thread? = null
        fun metadata() = replay.metadata().put("streamId", id).put("source", source).put("state", state)
            .put("active", finishedAt == 0L).put("error", error ?: JSONObject.NULL).put("hardwareReleaseConfirmed", hardwareReleased())
            .put("frameIntervalMilliseconds", request.optLong("frameIntervalMilliseconds", 1000))
    }

    fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        if (type == TYPE_START) return start(context.applicationContext, request)
        val session = synchronized(this) {
            prune()
            if (type == TYPE_LIST) return DikcizNativeCommands.page(streams.values.toList(), request, Session::metadata).put("total", streams.size)
            val id = request.getString("streamId")
            if (!IDS.matches(id)) DikcizNativeCommands.invalid("Invalid image stream ID")
            streams[id] ?: DikcizNativeCommands.failure("image_stream_not_found", "No retained visual stream")
        }
        return when (type) {
            TYPE_READ -> session.replay.read(request.optLong("cursor", 0), request.optInt("limit", 1), request.optLong("waitMilliseconds", 0))
                .put("streamId", session.id).put("source", session.source).put("state", session.state)
                .put("active", session.finishedAt == 0L).put("error", session.error ?: JSONObject.NULL)
                .put("hardwareReleaseConfirmed", session.hardwareReleased())
            TYPE_STOP -> {
                stopFromOwner(session.id)
                session.thread?.join(CLEANUP_MILLISECONDS)
                session.metadata()
            }
            TYPE_FORGET -> {
                if (session.finishedAt == 0L) DikcizNativeCommands.failure("image_stream_active", "Stop the visual stream before forgetting it")
                synchronized(this) { streams.remove(session.id) }
                JSONObject().put("streamId", session.id).put("forgotten", true)
            }
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun start(context: Context, request: JSONObject): JSONObject {
        val source = request.getString("source")
        if (source !in setOf("camera", "display")) DikcizNativeCommands.invalid("Choose camera or display")
        val foreground = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(foreground)
        if (foreground.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz before starting live capture")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            DikcizNativeCommands.requirePermission(context, Manifest.permission.POST_NOTIFICATIONS, "notifications")
        }
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled() ||
            notifications.getNotificationChannel("dikciz-image-stream")?.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
            DikcizNativeCommands.failure("capture_notifications_blocked", "Enable Dikciz live capture notifications so the owner Stop action remains available")
        }
        if (source == "camera") DikcizNativeCommands.requirePermission(context, Manifest.permission.CAMERA, "cameraAndMicrophone")
        else if (!DikcizAccessibilityAutomation.isEnabled(context)) {
            DikcizNativeCommands.failure("accessibility_not_enabled", "Enable Dikciz Accessibility before display streaming")
        }
        val session = synchronized(this) {
            prune()
            if (streams.values.any { it.finishedAt == 0L }) DikcizNativeCommands.failure("hardware_busy", "Stop the active visual stream first")
            while (streams.size >= MAXIMUM_STREAMS) streams.remove(streams.keys.first())
            Session(context, JSONObject(request.toString())).also { streams[it.id] = it }
        }
        try {
            DikcizImageCaptureService.start(context, session.id, source == "camera")
            if (!session.ready.await(request.optLong("startupTimeoutMilliseconds", 10000), TimeUnit.MILLISECONDS)) {
                session.cancelled = true
                session.error = "capture_service_timeout"
                session.state = "failed"
                session.finishedAt = SystemClock.elapsedRealtime()
                session.replay.finish()
                DikcizImageCaptureService.stop(context, session.id)
                DikcizNativeCommands.failure("capture_service_timeout", "Android did not start the live capture service")
            }
            session.error?.let { DikcizNativeCommands.failure(it, "Android did not start live capture") }
            return session.metadata()
        } catch (exception: InterruptedException) {
            stopFromOwner(session.id)
            DikcizImageCaptureService.stop(context, session.id)
            if (session.thread == null) {
                session.state = "cancelled"; session.finishedAt = SystemClock.elapsedRealtime(); session.replay.finish()
            }
            throw exception
        } catch (exception: RuntimeException) {
            if (session.thread == null) {
                session.state = "failed"; session.error = "capture_service_failed"
                session.finishedAt = SystemClock.elapsedRealtime(); session.replay.finish()
            }
            if (exception is DikcizAutomationRequestException) throw exception
            DikcizNativeCommands.failure("capture_service_failed", "Open Dikciz and check camera/foreground-service access before retrying")
        }
    }

    fun serviceReady(id: String): Boolean {
        val session = synchronized(this) { streams[id] } ?: return false
        synchronized(session) {
            if (session.cancelled || session.finishedAt != 0L) { session.ready.countDown(); return false }
            if (session.thread == null) session.thread = Thread({ produce(session) }, "dikciz-image-stream").apply { isDaemon = true; start() }
            session.ready.countDown()
        }
        return true
    }

    fun serviceFailed(id: String, exception: RuntimeException) {
        val session = synchronized(this) { streams[id] } ?: return
        android.util.Log.e("DikcizImageStreams", "Capture service failed: " + exception.javaClass.simpleName)
        session.error = "capture_service_failed"; session.state = "failed"; session.finishedAt = SystemClock.elapsedRealtime()
        session.replay.finish(); session.ready.countDown()
    }

    fun serviceDestroyed(id: String) { stopFromOwner(id) }

    fun stopFromOwner(id: String) {
        val session = synchronized(this) { streams[id] } ?: return
        if (session.finishedAt != 0L) return
        session.cancelled = true
        session.thread?.interrupt()
    }

    private fun produce(session: Session) {
        var camera: DikcizCameraPreview? = null
        try {
            if (session.cancelled) throw InterruptedException()
            if (session.source == "camera") {
                camera = DikcizCameraPreview(session.context, session.request)
                session.camera = camera
            }
            session.state = "streaming"
            val duration = session.request.optLong("maximumDurationMilliseconds", 60000)
            val interval = session.request.optLong("frameIntervalMilliseconds", 1000)
            val maximum = session.request.optInt("maxDimension", 640)
            while (!session.cancelled && SystemClock.elapsedRealtime() - session.started < duration) {
                val before = SystemClock.elapsedRealtime()
                val original = if (camera != null) {
                    val bytes = camera.frame(session.request.optLong("startupTimeoutMilliseconds", 10000))
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: DikcizNativeCommands.failure("camera_image_invalid", "Camera returned an invalid JPEG")
                } else DikcizAccessibilityAutomation.screenshot()
                val scale = minOf(1.0, maximum.toDouble() / maxOf(original.width, original.height))
                var image = if (scale < 1.0) Bitmap.createScaledBitmap(original,
                    (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true) else original
                if (image !== original) original.recycle()
                try {
                    var bytes: ByteArray
                    while (true) {
                        val output = ByteArrayOutputStream()
                        image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                        bytes = output.toByteArray()
                        if (bytes.size <= MAXIMUM_JPEG_BYTES) break
                        if (minOf(image.width, image.height) <= MINIMUM_FRAME_EDGE) DikcizNativeCommands.failure("image_frame_too_large", "Visual frame exceeds replay capacity")
                        val reduced = Bitmap.createScaledBitmap(image, (image.width / 2).coerceAtLeast(1), (image.height / 2).coerceAtLeast(1), true)
                        image.recycle(); image = reduced
                    }
                    session.replay.append(JSONObject().put("event", "frame").put("mimeType", "image/jpeg")
                        .put("width", image.width).put("height", image.height).put("capturedAt", System.currentTimeMillis())
                        .put("sensorOrientation", camera?.orientation ?: 0)
                        .put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP)))
                } finally { image.recycle() }
                val remaining = interval - (SystemClock.elapsedRealtime() - before)
                if (remaining > 0) Thread.sleep(remaining)
            }
            session.state = if (session.cancelled) "cancelled" else "completed"
        } catch (_: InterruptedException) {
            session.state = "cancelled"
        } catch (exception: DikcizAccessibilityAutomationException) {
            session.state = "failed"; session.error = exception.code
        } catch (exception: DikcizAutomationRequestException) {
            session.state = "failed"; session.error = exception.code
        } catch (exception: Exception) {
            android.util.Log.e("DikcizImageStreams", "Frame producer failed: " + exception.javaClass.simpleName)
            session.state = "failed"; session.error = "image_stream_failed"
        } finally {
            try { camera?.close() } catch (exception: Exception) {
                android.util.Log.e("DikcizImageStreams", "Camera cleanup pending: " + exception.javaClass.simpleName)
            }
            session.released = camera?.hardwareReleaseConfirmed() ?: true
            session.finishedAt = SystemClock.elapsedRealtime()
            session.replay.finish()
            try { DikcizImageCaptureService.stop(session.context, session.id) }
            catch (exception: RuntimeException) {
                android.util.Log.w("DikcizImageStreams", "Capture service stop already unavailable: " + exception.javaClass.simpleName)
            }
        }
    }

    @Synchronized private fun prune() {
        val cutoff = SystemClock.elapsedRealtime() - RETENTION_MILLISECONDS
        streams.entries.removeAll { it.value.finishedAt in 1..cutoff }
    }

    private const val JPEG_QUALITY = 70
    private const val MINIMUM_FRAME_EDGE = 160
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Long, maximum: Long) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    private val ID_FIELD = mapOf("streamId" to text(36))
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_START, "dikciz_image_stream_start",
            "Start bounded camera JPEG preview or Accessibility display frames with an owner Stop notification. Requires source camera/display and foreground startup. A persistent camera session is reused; display follows Android screenshot limits and secure-window restrictions. Poll imageStreamRead for actual frames/errors. Owner only.",
            mapOf("source" to text(16).put("enum", org.json.JSONArray(listOf("camera", "display"))), "cameraId" to text(128),
                "maxDimension" to integer(320, 1280).put("default", 640), "frameIntervalMilliseconds" to integer(500, 10000).put("default", 1000),
                "maximumDurationMilliseconds" to integer(1000, 3600000).put("default", 60000),
                "bufferBytes" to integer(262144, 8388608).put("default", 1048576), "maximumEvents" to integer(1, 64).put("default", 16),
                "startupTimeoutMilliseconds" to integer(1000, 30000).put("default", 10000)), setOf("source"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_READ, "dikciz_image_stream_read",
            "Read complete bounded JPEG frames by exclusive cursor, with replay loss and actual terminal/hardware-cleanup state. No capture is started. Frames stay in process memory for five minutes after completion. Owner only.",
            ID_FIELD + mapOf("cursor" to integer(0, 9007199254740991), "limit" to integer(1, 200).put("default", 1), "waitMilliseconds" to integer(0, 30000)),
            setOf("streamId")),
        DikcizNativeCommands.Definition(TYPE_STOP, "dikciz_image_stream_stop", "Stop one live visual stream, close its camera session and read hardwareReleaseConfirmed. Completed frames remain until forgotten or retention expiry.",
            ID_FIELD, setOf("streamId"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_image_streams_list", "List retained visual stream metadata without frame payloads.",
            mapOf("limit" to integer(1, 200), "offset" to integer(0, 1000000))),
        DikcizNativeCommands.Definition(TYPE_FORGET, "dikciz_image_stream_forget", "Discard one finished visual stream and its retained images. Stop active capture first.",
            ID_FIELD, setOf("streamId"), readOnly = false),
    )
    val TYPES = setOf(TYPE_START, TYPE_READ, TYPE_STOP, TYPE_LIST, TYPE_FORGET)
}
