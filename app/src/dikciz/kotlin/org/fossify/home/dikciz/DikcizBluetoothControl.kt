package org.fossify.home.dikciz

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

// Every operation checks version-specific grants in adapter() before calling Bluetooth.
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
internal class DikcizBluetoothControl(private val context: Context, private val logger: DikcizLogger) {
    fun pair(request: JSONObject): JSONObject {
        val device = adapter().getRemoteDevice(request.getString(KEY_ADDRESS).uppercase(Locale.ROOT))
        if (device.bondState == BluetoothDevice.BOND_BONDED) return JSONObject().put("state", "paired").put("bondState", device.bondState)
        if (!device.createBond()) DikcizNativeCommands.failure("bluetooth_pair_rejected", "Android did not start pairing")
        return JSONObject().put("state", "submitted").put("bondState", device.bondState)
    }

    fun scan(request: JSONObject): JSONObject {
        val adapter = adapter(scan = true)
        if (!scanBusy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "A Dikciz Bluetooth scan is already active")
        val result = LinkedHashMap<String, JSONObject>()
        val maximum = request.optInt(KEY_MAXIMUM_DEVICES, DEFAULT_MAXIMUM_DEVICES)
        val complete = CountDownLatch(1)
        val active = AtomicBoolean(true)
        val limited = AtomicBoolean()
        val failed = AtomicReference<Int?>()
        val accessRevoked = AtomicBoolean()
        fun found(device: BluetoothDevice, rssi: Int) {
            if (!active.get()) return
            synchronized(result) {
                if (!result.containsKey(device.address) && result.size >= maximum) {
                    limited.set(true); complete.countDown(); return
                }
                try {
                    result[device.address] = JSONObject().put(KEY_ADDRESS, device.address)
                        .put("name", device.name?.take(MAXIMUM_NAME) ?: JSONObject.NULL)
                        .put("type", device.type).put("bondState", device.bondState)
                        .put("rssiDbm", if (rssi == Short.MIN_VALUE.toInt()) JSONObject.NULL else rssi)
                } catch (_: SecurityException) {
                    accessRevoked.set(true); complete.countDown()
                }
            }
        }
        val ble = request.optString(KEY_MODE, MODE_BLE) == MODE_BLE
        var receiver: BroadcastReceiver? = null
        var callback: ScanCallback? = null
        var ownsClassic = false
        var receiverRegistered = false
        var scanner: android.bluetooth.le.BluetoothLeScanner? = null
        try {
            if (ble) {
                scanner = adapter.bluetoothLeScanner ?: DikcizNativeCommands.failure("bluetooth_scan_unavailable", "Bluetooth LE scanner unavailable")
                callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, observed: ScanResult) { found(observed.device, observed.rssi) }
                    override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { found(it.device, it.rssi) } }
                    override fun onScanFailed(errorCode: Int) { failed.set(errorCode); complete.countDown() }
                }
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), callback)
            } else {
                if (adapter.isDiscovering) DikcizNativeCommands.failure("hardware_busy", "Android discovery is already active; this request will not cancel it")
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) { complete.countDown(); return }
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        found(device, intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt())
                    }
                }
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply { addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                else context.registerReceiver(receiver, filter)
                receiverRegistered = true
                if (!adapter.startDiscovery()) DikcizNativeCommands.failure("bluetooth_scan_rejected", "Android did not start discovery")
                ownsClassic = true
            }
            complete.await(request.optLong(KEY_DURATION, DEFAULT_SCAN_DURATION), TimeUnit.MILLISECONDS)
            if (accessRevoked.get()) DikcizNativeCommands.failure("native_permission_bluetooth", "Bluetooth access changed during discovery")
            failed.get()?.let { DikcizNativeCommands.failure("bluetooth_scan_error_" + it, "Android Bluetooth scan failed") }
            val rows = synchronized(result) { result.values.sortedBy { it.getString(KEY_ADDRESS) } }
            return JSONObject().put("items", JSONArray(rows)).put("count", rows.size).put("limitReached", limited.get())
                .put(KEY_MODE, if (ble) MODE_BLE else MODE_CLASSIC).put(KEY_MAXIMUM_DEVICES, maximum)
        } finally {
            active.set(false)
            try {
                try {
                    callback?.let { scanner?.stopScan(it) }
                    if (ownsClassic && !adapter.cancelDiscovery()) logger.warn("bluetooth_discovery_cancel_rejected")
                } catch (exception: RuntimeException) {
                    logger.warn("bluetooth_scan_cleanup_failed", mapOf("error_class" to exception.javaClass.simpleName))
                }
            } finally {
                try { if (receiverRegistered) receiver?.let { context.unregisterReceiver(it) } }
                finally { scanBusy.set(false) }
            }
        }
    }

    fun permittedAdapter(): BluetoothAdapter = adapter()

    private fun adapter(scan: Boolean = false): BluetoothAdapter {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                scan && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                DikcizNativeCommands.failure("native_permission_bluetooth", "Android Bluetooth access is required")
            }
        } else if (scan && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_locationForeground", "Location access is required for Bluetooth discovery on this Android version")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Bluetooth hardware unavailable")
        if (!adapter.isEnabled) DikcizNativeCommands.failure("bluetooth_disabled", "Enable Bluetooth in Android settings")
        return adapter
    }

    fun exchange(request: JSONObject): JSONObject {
        val device = adapter().getRemoteDevice(request.getString(KEY_ADDRESS).uppercase(Locale.ROOT))
        if (!socketBusy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "A Dikciz Bluetooth socket transaction is active")
        val expired = AtomicBoolean()
        var connection: android.bluetooth.BluetoothSocket? = null
        var deadline: java.util.concurrent.ScheduledFuture<*>? = null
        try {
            val uuid = UUID.fromString(request.getString(KEY_UUID))
            val socket = if (request.optBoolean(KEY_SECURE, true)) device.createRfcommSocketToServiceRecord(uuid)
                else device.createInsecureRfcommSocketToServiceRecord(uuid)
            connection = socket
            deadline = deadlines.schedule({
                expired.set(true)
                try { socket.close() } catch (exception: IOException) {
                    logger.warn("bluetooth_deadline_close_failed", mapOf("error_class" to exception.javaClass.simpleName))
                }
            }, request.optLong(KEY_TIMEOUT, DEFAULT_SOCKET_TIMEOUT), TimeUnit.MILLISECONDS)
            socket.connect()
            val data = if (request.has(KEY_DATA)) decode(request) else ByteArray(0)
            if (data.isNotEmpty()) {
                socket.outputStream.write(data)
                socket.outputStream.flush()
            }
            val maximum = request.optInt(KEY_READ_BYTES, DEFAULT_READ_BYTES)
            val buffer = ByteArray(maximum)
            val count = if (maximum == 0) 0 else socket.inputStream.read(buffer)
            return JSONObject().put("state", "completed").put("bytesSent", data.size).put("bytesReceived", maxOf(0, count))
                .put("eof", count < 0).put(KEY_DATA, Base64.getEncoder().encodeToString(buffer.copyOf(maxOf(0, count))))
        } catch (exception: IOException) {
            throw DikcizAutomationRequestException(if (expired.get()) "bluetooth_timeout" else "bluetooth_socket_failed",
                "Bluetooth socket transaction failed; a write may have partial effects").apply { initCause(exception) }
        } finally {
            deadline?.cancel(false)
            try { connection?.close() } catch (exception: IOException) {
                logger.warn("bluetooth_socket_close_failed", mapOf("error_class" to exception.javaClass.simpleName))
            } finally { socketBusy.set(false) }
        }
    }

    companion object {
        const val TYPE_SCAN = "bluetoothScan"
        const val TYPE_PAIR = "bluetoothPair"
        const val TYPE_EXCHANGE = "bluetoothExchange"
        private const val KEY_ADDRESS = "address"
        private const val KEY_MODE = "mode"
        private const val KEY_DURATION = "durationMilliseconds"
        private const val KEY_MAXIMUM_DEVICES = "maximumDevices"
        private const val KEY_UUID = "serviceUuid"
        private const val KEY_DATA = "dataBase64"
        private const val KEY_READ_BYTES = "readBytes"
        private const val KEY_TIMEOUT = "timeoutMilliseconds"
        private const val KEY_SECURE = "secure"
        private const val MODE_BLE = "ble"
        private const val MODE_CLASSIC = "classic"
        private const val DEFAULT_MAXIMUM_DEVICES = 50
        private const val DEFAULT_SCAN_DURATION = 5000L
        private const val DEFAULT_SOCKET_TIMEOUT = 30000L
        private const val DEFAULT_READ_BYTES = 4096
        private const val MAXIMUM_BYTES = 65536
        private const val MAXIMUM_NAME = 256
        private val scanBusy = AtomicBoolean()
        private val socketBusy = AtomicBoolean()
        private val deadlines = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "dikciz-bluetooth-deadlines").apply { isDaemon = true }
        }
        val DEFINITIONS = listOf(
            DikcizNativeCommands.Definition(TYPE_SCAN, "dikciz_bluetooth_scan", "Observe nearby BLE or classic devices during a bounded scan window. Requires Android Bluetooth access, plus location on older Android. Returns actual observed address/name/type/bond/RSSI metadata. Stops only its own scan.", mapOf(
                KEY_MODE to text("ble (default) or classic.", 16),
                KEY_DURATION to integer(1, 120000, 5000), KEY_MAXIMUM_DEVICES to integer(1, 200, DEFAULT_MAXIMUM_DEVICES))),
            DikcizNativeCommands.Definition(TYPE_PAIR, "dikciz_bluetooth_pair", "Ask Android to start pairing with a selected Bluetooth address. Owner PIN/confirmation may be required. submitted is not paired; inspect bluetoothDevices for the final bond.", mapOf(KEY_ADDRESS to text("Explicit Bluetooth MAC address.", 17)), setOf(KEY_ADDRESS), readOnly = false),
            DikcizNativeCommands.Definition(TYPE_EXCHANGE, "dikciz_bluetooth_exchange", "Connect one RFCOMM service, optionally write bytes, then read one bounded chunk and close. Requires a device address and service UUID. Secure pairing/encryption defaults on. The deadline closes the owned socket. Errors can follow partial external writes; do not retry automatically.", mapOf(
                KEY_ADDRESS to text("Explicit Bluetooth MAC address.", 17), KEY_UUID to text("Canonical service UUID.", 36),
                KEY_DATA to text("Optional Base64 output, at most 65536 decoded bytes.", (MAXIMUM_BYTES + 2) / 3 * 4),
                KEY_READ_BYTES to integer(0, MAXIMUM_BYTES, DEFAULT_READ_BYTES),
                KEY_TIMEOUT to integer(1, 120000, 30000), KEY_SECURE to JSONObject().put("type", "boolean").put("default", true)),
                setOf(KEY_ADDRESS, KEY_UUID), readOnly = false),
        )
        val TYPES = setOf(TYPE_SCAN, TYPE_PAIR, TYPE_EXCHANGE)

        fun validate(type: String, request: JSONObject) {
            if (type == TYPE_SCAN && request.optString(KEY_MODE, MODE_BLE) !in setOf(MODE_BLE, MODE_CLASSIC)) DikcizNativeCommands.invalid("Unsupported Bluetooth scan mode")
            if (request.has(KEY_ADDRESS) && !BluetoothAdapter.checkBluetoothAddress(request.getString(KEY_ADDRESS).uppercase(Locale.ROOT))) DikcizNativeCommands.invalid("Invalid Bluetooth address")
            if (type == TYPE_EXCHANGE) {
                val uuid = request.getString(KEY_UUID)
                try {
                    if (UUID.fromString(uuid).toString() != uuid.lowercase(Locale.ROOT)) DikcizNativeCommands.invalid("Invalid Bluetooth service UUID")
                } catch (exception: IllegalArgumentException) {
                    if (exception is DikcizAutomationRequestException) throw exception
                    throw DikcizAutomationRequestException("validation_failed", "Invalid Bluetooth service UUID").apply { initCause(exception) }
                }
                if (request.has(KEY_DATA)) decode(request)
            }
        }

        private fun decode(request: JSONObject): ByteArray {
            val bytes = try { Base64.getDecoder().decode(request.getString(KEY_DATA)) } catch (exception: IllegalArgumentException) {
                throw DikcizAutomationRequestException("validation_failed", "Invalid Bluetooth base64 data").apply { initCause(exception) }
            }
            if (bytes.size > MAXIMUM_BYTES) DikcizNativeCommands.invalid("Bluetooth data exceeds its byte limit")
            return bytes
        }
        private fun text(description: String, maximum: Int) = JSONObject().put("type", "string").put("description", description).put("maxLength", maximum)
        private fun integer(minimum: Int, maximum: Int, default: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum).put("default", default)
    }
}
