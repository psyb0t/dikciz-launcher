package org.fossify.home.dikciz

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import org.json.JSONObject

internal class DikcizCameraControls(
    private val request: JSONObject,
    characteristics: CameraCharacteristics,
) {
    private val quality = request.optInt("jpegQuality", DEFAULT_JPEG_QUALITY)
    private val crop: Rect?

    init {
        if (request.has("exposureCompensation")) {
            val range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            if (range == null || !range.contains(request.getInt("exposureCompensation"))) {
                unsupported("Exposure compensation is outside this camera's range")
            }
        }
        if (request.has("autofocusMode")) {
            val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            if (request.getInt("autofocusMode") !in modes) unsupported("Autofocus mode is unavailable on this camera")
        }
        crop = if (request.has("zoomPercent")) {
            val zoom = request.getInt("zoomPercent") / ZOOM_PERCENT_SCALE
            val maximum = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            val active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (maximum == null || zoom > maximum || active == null || active.isEmpty) {
                unsupported("Sensor crop zoom is unavailable or exceeds this camera's range")
            }
            val width = (active.width() / zoom).toInt().coerceAtLeast(MINIMUM_CROP_SIZE)
            val height = (active.height() / zoom).toInt().coerceAtLeast(MINIMUM_CROP_SIZE)
            val left = active.left + (active.width() - width) / 2
            val top = active.top + (active.height() - height) / 2
            Rect(left, top, left + width, top + height)
        } else {
            null
        }
    }

    fun apply(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.JPEG_QUALITY, quality.toByte())
        if (request.has("jpegOrientation")) {
            builder.set(CaptureRequest.JPEG_ORIENTATION, request.getInt("jpegOrientation"))
        }
        if (request.has("exposureCompensation")) {
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, request.getInt("exposureCompensation"))
        }
        if (request.has("autofocusMode")) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, request.getInt("autofocusMode"))
        }
        crop?.let { builder.set(CaptureRequest.SCALER_CROP_REGION, it) }
    }

    fun requested(): JSONObject = JSONObject().put("jpegQuality", quality).apply {
        for (key in OPTIONAL_FIELDS) {
            if (request.has(key)) put(key, request.get(key))
        }
        crop?.let { put("cropRegion", rectangle(it)) }
    }

    companion object {
        const val DEFAULT_JPEG_QUALITY = 95
        private const val ZOOM_PERCENT_SCALE = 100f
        private const val MINIMUM_CROP_SIZE = 2
        private val OPTIONAL_FIELDS = setOf("jpegOrientation", "zoomPercent", "exposureCompensation", "autofocusMode")

        fun validate(request: JSONObject) {
            if (request.has("jpegOrientation") && request.getInt("jpegOrientation") !in setOf(0, 90, 180, 270)) {
                DikcizNativeCommands.invalid("JPEG orientation must be 0, 90, 180 or 270")
            }
        }

        fun observed(result: TotalCaptureResult): JSONObject = JSONObject()
            .put("frameNumber", result.frameNumber)
            .put("sensorTimestampNanoseconds", result.get(CaptureResult.SENSOR_TIMESTAMP) ?: JSONObject.NULL)
            .put("jpegQuality", result.get(CaptureResult.JPEG_QUALITY)?.toInt() ?: JSONObject.NULL)
            .put("jpegOrientation", result.get(CaptureResult.JPEG_ORIENTATION) ?: JSONObject.NULL)
            .put("exposureCompensation", result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: JSONObject.NULL)
            .put("autofocusMode", result.get(CaptureResult.CONTROL_AF_MODE) ?: JSONObject.NULL)
            .put("autofocusState", result.get(CaptureResult.CONTROL_AF_STATE) ?: JSONObject.NULL)
            .put("autoExposureState", result.get(CaptureResult.CONTROL_AE_STATE) ?: JSONObject.NULL)
            .put("cropRegion", result.get(CaptureResult.SCALER_CROP_REGION)?.let(::rectangle) ?: JSONObject.NULL)

        private fun rectangle(rect: Rect): JSONObject = JSONObject()
            .put("left", rect.left).put("top", rect.top).put("right", rect.right).put("bottom", rect.bottom)

        private fun unsupported(message: String): Nothing =
            DikcizNativeCommands.failure("camera_control_unsupported", message)
    }
}
