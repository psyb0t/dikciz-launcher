package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizTorchControl {
    private const val DEFAULT_TIMEOUT = 1000L
    private const val CLEANUP_TIMEOUT = 2000L
    val FIELDS = mapOf(
        "action" to JSONObject().put("type", "string").put("maxLength", 16)
            .put("description", "status, on or off."),
        "cameraId" to JSONObject().put("type", "string").put("maxLength", 128)
            .put("description", "Flash-capable camera ID; status defaults to all flash cameras."),
        "timeoutMilliseconds" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 5000)
            .put("default", DEFAULT_TIMEOUT).put("description", "Status callback wait only."),
    )

    fun execute(context: Context, request: JSONObject): JSONObject {
        val manager = context.getSystemService(CameraManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Camera service unavailable")
        val cameras = manager.cameraIdList.filter {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        val action = request.getString("action")
        if (request.has("cameraId") && request.getString("cameraId") !in cameras) {
            DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "No flash-capable camera with that ID")
        }
        if (action == "status") {
            val selected = if (request.has("cameraId")) listOf(request.getString("cameraId")) else cameras
            return observe(manager, selected, request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT))
        }
        DikcizNativeCommands.requirePermission(context, Manifest.permission.CAMERA, "cameraAndMicrophone")
        val id = request.optString("cameraId").ifEmpty { cameras.firstOrNull().orEmpty() }
        if (id !in cameras) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "No flash-capable camera available")
        val enabled = action == "on"
        manager.setTorchMode(id, enabled)
        return JSONObject().put("state", "submitted").put("cameraId", id).put("enabled", enabled)
    }

    private fun observe(manager: CameraManager, cameras: List<String>, timeout: Long): JSONObject {
        val observed = ConcurrentHashMap<String, JSONObject>()
        val complete = CountDownLatch(cameras.size)
        val active = AtomicBoolean(true)
        val thread = HandlerThread("dikciz-torch-status").apply { start() }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) =
                update(cameraId, if (enabled) "enabled" else "disabled", enabled)

            override fun onTorchModeUnavailable(cameraId: String) = update(cameraId, "unavailable", null)

            private fun update(id: String, state: String, enabled: Boolean?) {
                if (!active.get() || id !in cameras) return
                val item = JSONObject().put("cameraId", id).put("state", state)
                    .put("enabled", enabled ?: JSONObject.NULL).put("observed", true)
                    .put("observedAtElapsedMilliseconds", SystemClock.elapsedRealtime())
                if (observed.put(id, item) == null) complete.countDown()
            }
        }
        var registered = false
        try {
            if (cameras.isNotEmpty()) {
                manager.registerTorchCallback(callback, Handler(thread.looper))
                registered = true
                complete.await(timeout, TimeUnit.MILLISECONDS)
            }
            return JSONObject().put("cameraIds", JSONArray(cameras)).put("complete", observed.size == cameras.size)
                .put("items", JSONArray(cameras.map { id ->
                    observed[id] ?: JSONObject().put("cameraId", id).put("state", "unknown")
                        .put("observed", false).put("enabled", JSONObject.NULL).put("reason", "no_callback_in_window")
                }))
        } finally {
            active.set(false)
            try {
                if (registered) manager.unregisterTorchCallback(callback)
            } finally {
                thread.quitSafely()
                thread.join(CLEANUP_TIMEOUT)
            }
        }
    }

    fun validate(request: JSONObject) {
        val action = request.getString("action")
        if (action !in setOf("status", "on", "off")) DikcizNativeCommands.invalid("Unsupported torch action")
        if (action != "status" && request.has("timeoutMilliseconds")) {
            DikcizNativeCommands.invalid("Torch timeoutMilliseconds applies only to status")
        }
        if (request.has("cameraId") && request.getString("cameraId").isBlank()) {
            DikcizNativeCommands.invalid("Camera ID cannot be blank")
        }
    }
}
