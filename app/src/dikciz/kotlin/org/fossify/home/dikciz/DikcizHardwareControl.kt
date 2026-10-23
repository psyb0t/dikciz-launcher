package org.fossify.home.dikciz

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizHardwareControl(context: Context) {
    private val context: Context = context.applicationContext

    fun torch(request: JSONObject): JSONObject = DikcizTorchControl.execute(context, request)

    fun vibration(request: JSONObject): JSONObject = DikcizHapticsControl.execute(context, request)

    fun settings(request: JSONObject): JSONObject {
        val key = request.getString(KEY_SETTING)
        val field = SETTINGS.getValue(key)
        val resolver = context.contentResolver
        if (request.getString(KEY_ACTION) == ACTION_SET) {
            if (!Settings.System.canWrite(context)) {
                DikcizNativeCommands.failure("native_permission_systemSettings", "Owner must grant Modify system settings")
            }
            if (!Settings.System.putInt(resolver, field.first, request.getInt(KEY_VALUE))) {
                DikcizNativeCommands.failure("settings_write_rejected", "Android rejected the setting write")
            }
        }
        val value = Settings.System.getString(resolver, field.first)
        return JSONObject().put(KEY_SETTING, key).put(KEY_VALUE, value?.toIntOrNull() ?: JSONObject.NULL)
            .put("writeAccess", Settings.System.canWrite(context))
    }

    fun bluetooth(request: JSONObject): JSONObject {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return DikcizNativeCommands.page(emptyList<JSONObject>(), request).put("available", false).put("total", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_bluetooth", "Android Bluetooth permission is required")
        }
        val devices = adapter.bondedDevices.sortedBy { it.address }.map { device ->
            JSONObject().put("address", device.address).put("name", device.name ?: JSONObject.NULL)
                .put("type", device.type).put("bondState", device.bondState)
        }
        return DikcizNativeCommands.page(devices, request).put("available", true).put("enabled", adapter.isEnabled)
            .put("total", devices.size)
    }

    fun usb(request: JSONObject): JSONObject {
        val manager = context.getSystemService(UsbManager::class.java) ?: unavailable("USB service unavailable")
        val devices = manager.deviceList.values.sortedBy { it.deviceId }.map { device ->
            JSONObject().put("deviceId", device.deviceId).put("name", device.deviceName)
                .put("vendorId", device.vendorId).put("productId", device.productId)
                .put("deviceClass", device.deviceClass).put("interfaceCount", device.interfaceCount)
                .put("permissionGranted", manager.hasPermission(device))
                .put("interfaces", JSONArray((0 until device.interfaceCount).map { index ->
                    val intf = device.getInterface(index)
                    JSONObject().put("index", index).put("id", intf.id).put("alternateSetting", intf.alternateSetting)
                        .put("interfaceClass", intf.interfaceClass).put("interfaceSubclass", intf.interfaceSubclass)
                        .put("endpoints", JSONArray((0 until intf.endpointCount).map { endpointIndex ->
                            val endpoint = intf.getEndpoint(endpointIndex)
                            JSONObject().put("address", endpoint.address).put("direction", endpoint.direction)
                                .put("type", endpoint.type).put("maximumPacketSize", endpoint.maxPacketSize).put("interval", endpoint.interval)
                        }))
                }))
        }
        return DikcizNativeCommands.page(devices, request).put("total", devices.size)
    }

    fun nfc(): JSONObject {
        val adapter = context.getSystemService(NfcManager::class.java)?.defaultAdapter
        return JSONObject().put("available", adapter != null).put("enabled", adapter?.isEnabled ?: false)
    }

    @Suppress("DEPRECATION")
    fun wifi(request: JSONObject): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_locationForeground", "Android fine location permission is required")
        }
        val manager = context.getSystemService(WifiManager::class.java) ?: unavailable("Wi-Fi service unavailable")
        val results = manager.scanResults.sortedBy { it.BSSID }.map { network ->
            JSONObject().put("ssid", network.SSID).put("bssid", network.BSSID).put("frequencyMHz", network.frequency)
                .put("signalDbm", network.level).put("capabilities", network.capabilities)
                .put("timestampMicroseconds", network.timestamp)
        }
        return DikcizNativeCommands.page(results, request).put("total", results.size)
            .put("enabled", manager.isWifiEnabled).put("source", "cached_scan_results")
    }

    fun audioDevices(request: JSONObject): JSONObject {
        val manager = context.getSystemService(AudioManager::class.java) ?: unavailable("Audio service unavailable")
        val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS).sortedBy { it.id }.map { device ->
            JSONObject().put("deviceId", device.id).put("type", device.type).put("name", device.productName.toString().take(MAXIMUM_DEVICE_NAME))
                .put("input", device.isSource).put("output", device.isSink)
                .put("sampleRates", JSONArray(device.sampleRates.toList())).put("channelCounts", JSONArray(device.channelCounts.toList()))
        }
        return DikcizNativeCommands.page(devices, request).put("total", devices.size)
    }

    fun cameraCapabilities(request: JSONObject): JSONObject {
        val manager = context.getSystemService(CameraManager::class.java) ?: unavailable("Camera service unavailable")
        val id = request.getString(KEY_CAMERA)
        if (id !in manager.cameraIdList) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Camera ID not found")
        val info = manager.getCameraCharacteristics(id)
        val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(android.graphics.ImageFormat.JPEG).orEmpty()
        val exposure = info.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val zoom = info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        return JSONObject().put(KEY_CAMERA, id).put("jpegSizes", JSONArray(sizes.map { JSONObject().put("width", it.width).put("height", it.height) }))
            .put("maximumDigitalZoom", zoom?.toDouble() ?: JSONObject.NULL)
            .put("exposureCompensationMinimum", exposure?.lower ?: JSONObject.NULL)
            .put("exposureCompensationMaximum", exposure?.upper ?: JSONObject.NULL)
            .put("autofocusModes", JSONArray(info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList().orEmpty()))
            .put("exposureCompensationStepEv", info.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toDouble() ?: JSONObject.NULL)
            .put("sensorOrientation", info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: JSONObject.NULL)
            .put("videoSizes", JSONArray(info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(android.media.MediaRecorder::class.java).orEmpty().map {
                    JSONObject().put("width", it.width).put("height", it.height)
                }))
            .put("frameRateRanges", JSONArray(info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                .orEmpty().map { JSONObject().put("minimum", it.lower).put("maximum", it.upper) }))
    }

    private fun unavailable(message: String): Nothing = DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, message)

    companion object {
        const val TYPE_TORCH = "torchControl"
        const val TYPE_VIBRATION = "vibrationControl"
        const val TYPE_SETTINGS = "systemSetting"
        const val TYPE_BLUETOOTH = "bluetoothDevices"
        const val TYPE_USB = "usbDevices"
        const val TYPE_NFC = "nfcStatus"
        const val TYPE_WIFI = "wifiScanResults"
        const val TYPE_AUDIO = "audioDevices"
        const val TYPE_CAMERA = "cameraCapabilities"
        const val KEY_ACTION = "action"
        const val KEY_CAMERA = "cameraId"
        const val KEY_DURATION = "durationMilliseconds"
        const val KEY_AMPLITUDE = "amplitude"
        const val KEY_SETTING = "setting"
        const val KEY_VALUE = "value"
        const val ACTION_STATUS = "status"
        const val ACTION_ON = "on"
        const val ACTION_OFF = "off"
        const val ACTION_CANCEL = "cancel"
        const val ACTION_VIBRATE = "vibrate"
        const val ACTION_GET = "get"
        const val ACTION_SET = "set"
        private const val MAXIMUM_DEVICE_NAME = 256
        val SETTINGS = mapOf(
            "brightness" to (Settings.System.SCREEN_BRIGHTNESS to 0..255),
            "automaticBrightness" to (Settings.System.SCREEN_BRIGHTNESS_MODE to 0..1),
            "automaticRotation" to (Settings.System.ACCELEROMETER_ROTATION to 0..1),
            "rotation" to (Settings.System.USER_ROTATION to 0..3),
            "screenTimeoutMilliseconds" to (Settings.System.SCREEN_OFF_TIMEOUT to 1..Int.MAX_VALUE),
        )
    }
}
