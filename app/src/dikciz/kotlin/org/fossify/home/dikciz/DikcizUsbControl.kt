package org.fossify.home.dikciz

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import java.util.Base64
import org.json.JSONObject

internal class DikcizUsbControl(private val context: Context, private val logger: DikcizLogger) {
    fun requestAccess(request: JSONObject): JSONObject {
        val manager = manager()
        val device = device(manager, request)
        if (manager.hasPermission(device)) return JSONObject().put("state", "granted").put(KEY_DEVICE, device.deviceId)
        val intent = Intent(context, DikcizUsbPermissionReceiver::class.java).setAction(ACTION_PERMISSION)
            .setData(Uri.parse("dikciz-usb://permission/" + device.deviceId))
        val pending = PendingIntent.getBroadcast(context, device.deviceId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.requestPermission(device, pending)
        return JSONObject().put("state", "user_action_required").put(KEY_DEVICE, device.deviceId)
            .put("instructions", "Check your phone and review the Android USB access dialog. Then query usbDevices and retry the intended transfer.")
    }

    fun transfer(request: JSONObject, autoRequestPermissions: Boolean): JSONObject {
        val manager = manager()
        val device = device(manager, request)
        if (!manager.hasPermission(device)) {
            val recovery = if (autoRequestPermissions) requestAccess(request) else JSONObject().put("state", "permission_required")
            recovery.put("requirementId", "usbDevice").put(KEY_DEVICE, device.deviceId).put("autoRequested", autoRequestPermissions)
                .put("retryRequired", true)
            return JSONObject().put("state", "permission_required").put("permissionRequest", recovery)
        }
        val connection = manager.openDevice(device) ?: DikcizNativeCommands.failure("usb_open_failed", "Android did not open the selected USB device")
        var claimed: android.hardware.usb.UsbInterface? = null
        try {
            val input: Boolean
            val result: Int
            val buffer: ByteArray
            val timeout = request.optInt(KEY_TIMEOUT, DEFAULT_TIMEOUT)
            if (request.getString(KEY_KIND) == KIND_CONTROL) {
                input = request.getInt(KEY_REQUEST_TYPE) and UsbConstants.USB_ENDPOINT_DIR_MASK == UsbConstants.USB_DIR_IN
                buffer = if (input) ByteArray(request.getInt(KEY_LENGTH)) else decode(request)
                result = connection.controlTransfer(request.getInt(KEY_REQUEST_TYPE), request.getInt(KEY_REQUEST), request.getInt(KEY_VALUE), request.getInt(KEY_INDEX), buffer, buffer.size, timeout)
            } else {
                val index = request.getInt(KEY_INTERFACE)
                if (index >= device.interfaceCount) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "USB interface is not attached")
                val target = device.getInterface(index)
                val endpoint = (0 until target.endpointCount).map { target.getEndpoint(it) }.firstOrNull { it.address == request.getInt(KEY_ENDPOINT) }
                    ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "USB endpoint is not attached")
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) DikcizNativeCommands.invalid("Bulk transfer requires a bulk endpoint")
                input = endpoint.direction == UsbConstants.USB_DIR_IN
                if (!connection.claimInterface(target, false)) DikcizNativeCommands.failure("usb_interface_busy", "Android did not grant exclusive interface access")
                claimed = target
                buffer = if (input) ByteArray(request.getInt(KEY_LENGTH)) else decode(request)
                result = connection.bulkTransfer(endpoint, buffer, buffer.size, timeout)
            }
            if (result < 0) DikcizNativeCommands.failure("usb_transfer_failed", "USB transfer failed or timed out; no byte count is confirmed")
            return JSONObject().put("state", "completed").put("bytesTransferred", result)
                .put(KEY_DEVICE, device.deviceId).put("dataBase64", if (input) Base64.getEncoder().encodeToString(buffer.copyOf(result)) else JSONObject.NULL)
        } finally {
            try {
                claimed?.let { if (!connection.releaseInterface(it)) logger.warn("usb_interface_release_failed") }
            } finally { connection.close() }
        }
    }

    private fun manager(): UsbManager = context.getSystemService(UsbManager::class.java)
        ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "USB host service unavailable")

    private fun device(manager: UsbManager, request: JSONObject): UsbDevice = manager.deviceList.values.firstOrNull { it.deviceId == request.getInt(KEY_DEVICE) }
        ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Selected USB device is no longer attached")

    companion object {
        const val TYPE_ACCESS = "usbRequestAccess"
        const val TYPE_TRANSFER = "usbTransfer"
        const val ACTION_PERMISSION = "org.fossify.home.dikciz.USB_PERMISSION"
        const val KEY_DEVICE = "deviceId"
        private const val KEY_KIND = "kind"
        private const val KIND_CONTROL = "control"
        private const val KIND_BULK = "bulk"
        private const val KEY_INTERFACE = "interfaceIndex"
        private const val KEY_ENDPOINT = "endpointAddress"
        private const val KEY_REQUEST_TYPE = "requestType"
        private const val KEY_REQUEST = "request"
        private const val KEY_VALUE = "value"
        private const val KEY_INDEX = "index"
        private const val KEY_LENGTH = "length"
        private const val KEY_DATA = "dataBase64"
        private const val KEY_TIMEOUT = "timeoutMilliseconds"
        private const val DEFAULT_TIMEOUT = 30_000
        private const val MAXIMUM_BYTES = 65_536
        val DEFINITIONS = listOf(
            DikcizNativeCommands.Definition(TYPE_ACCESS, "dikciz_usb_request_access", "Request Android owner approval for one currently attached USB-host device. Poll usbDevices.permissionGranted, then retry explicitly. Access expires when the device disconnects.", mapOf(KEY_DEVICE to integer(0, Int.MAX_VALUE)), setOf(KEY_DEVICE), readOnly = false),
            DikcizNativeCommands.Definition(TYPE_TRANSFER, "dikciz_usb_transfer", "Perform one bounded USB control or bulk transfer using owner-granted access. Use descriptors from usbDevices. Select length for input or dataBase64 for output. Connections and claimed interfaces are always released. Failed writes can have partial effects; do not retry automatically.", mapOf(
                KEY_DEVICE to integer(0, Int.MAX_VALUE), KEY_KIND to text("control or bulk.", 16),
                KEY_INTERFACE to integer(0, 255), KEY_ENDPOINT to integer(0, 255), KEY_REQUEST_TYPE to integer(0, 255), KEY_REQUEST to integer(0, 255),
                KEY_VALUE to integer(0, 65535), KEY_INDEX to integer(0, 65535), KEY_LENGTH to integer(0, MAXIMUM_BYTES),
                KEY_DATA to text("Base64 bytes for an output transfer, at most 65536 decoded bytes.", (MAXIMUM_BYTES + 2) / 3 * 4),
                KEY_TIMEOUT to integer(1, 120_000).put("default", DEFAULT_TIMEOUT),
            ), setOf(KEY_DEVICE, KEY_KIND), readOnly = false),
        )

        fun validate(request: JSONObject) {
            val kind = request.getString(KEY_KIND)
            val fields = when (kind) {
                KIND_CONTROL -> setOf(KEY_REQUEST_TYPE, KEY_REQUEST, KEY_VALUE, KEY_INDEX)
                KIND_BULK -> setOf(KEY_INTERFACE, KEY_ENDPOINT)
                else -> DikcizNativeCommands.invalid("Unsupported USB transfer kind")
            }
            val alternatives = setOf(KEY_REQUEST_TYPE, KEY_REQUEST, KEY_VALUE, KEY_INDEX, KEY_INTERFACE, KEY_ENDPOINT)
            if (fields.any { !request.has(it) } || alternatives.any { request.has(it) && it !in fields }) DikcizNativeCommands.invalid("USB fields do not match transfer kind")
            val direction = request.getInt(if (kind == KIND_CONTROL) KEY_REQUEST_TYPE else KEY_ENDPOINT) and UsbConstants.USB_ENDPOINT_DIR_MASK
            if (direction == UsbConstants.USB_DIR_IN) {
                if (!request.has(KEY_LENGTH) || request.has(KEY_DATA)) DikcizNativeCommands.invalid("USB input requires length and refuses dataBase64")
            } else {
                if (!request.has(KEY_DATA) || request.has(KEY_LENGTH)) DikcizNativeCommands.invalid("USB output requires dataBase64 and refuses length")
                decode(request)
            }
        }

        private fun decode(request: JSONObject): ByteArray {
            val bytes = try { Base64.getDecoder().decode(request.getString(KEY_DATA)) } catch (exception: IllegalArgumentException) {
                throw DikcizAutomationRequestException("validation_failed", "Invalid USB base64 data").apply { initCause(exception) }
            }
            if (bytes.size > MAXIMUM_BYTES) DikcizNativeCommands.invalid("USB data exceeds the byte limit")
            return bytes
        }
        private fun text(description: String, maximum: Int) = JSONObject().put("type", "string").put("description", description).put("maxLength", maximum)
        private fun integer(minimum: Int, maximum: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    }
}

internal class DikcizUsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The authoritative grant is queried from UsbManager; broadcast extras are not trusted.
        if (intent.action != DikcizUsbControl.ACTION_PERMISSION) return
    }
}
