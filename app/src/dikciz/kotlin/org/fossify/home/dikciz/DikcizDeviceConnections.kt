package org.fossify.home.dikciz

import android.Manifest
import android.app.Activity
import android.content.Context
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.nfc.NdefMessage
import android.nfc.NfcManager
import android.nfc.tech.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizNfcLease { val busy = AtomicBoolean() }

internal abstract class DikcizDeviceConnection(val kind: String) : Closeable {
    val id: String = UUID.randomUUID().toString()
    val createdAt: Long = System.currentTimeMillis()
    @Volatile var closed = false
    @Volatile var releaseConfirmed = false
    var releaseAttempted = false
    var expiresAt: Long = 0
    val operation = AtomicBoolean()
    abstract fun exchange(request: JSONObject): JSONObject
    open fun details(): JSONObject = JSONObject()
    fun document(): JSONObject = details().put("id", id).put("kind", kind).put("createdAt", createdAt)
        .put("expiresAt", expiresAt).put("active", !closed).put("busy", operation.get()).put("hardwareReleaseConfirmed", releaseConfirmed)
}

internal object DikcizDeviceConnections {
    const val OPEN = "deviceConnectionOpen"
    const val EXCHANGE = "deviceConnectionExchange"
    const val CLOSE = "deviceConnectionClose"
    const val LIST = "deviceConnectionsList"
    const val READ = "deviceConnectionRead"
    internal const val MAX_BYTES = 65536
    internal const val DEFAULT_TIMEOUT = 30000L
    private const val DEFAULT_READ_BYTES = 4096
    private const val MAX_CONNECTIONS = 8
    private val sessions = ConcurrentHashMap<String, DikcizDeviceConnection>()
    private val timers = Executors.newSingleThreadScheduledExecutor()
    private var openings = 0
    fun execute(context: Context, logger: DikcizLogger, type: String, request: JSONObject, autoRequest: Boolean): JSONObject {
        if (type == LIST) return DikcizNativeCommands.page(sessions.values.sortedBy { it.createdAt }.map { it.document() }, request)
        if (type == OPEN) {
            validateOpen(request)
            if (request.getString("kind") == "usb") {
                val manager=context.getSystemService(UsbManager::class.java)
                val device=manager?.deviceList?.values?.find { it.deviceId==request.getInt("deviceId") }
                    ?: DikcizNativeCommands.failure("native_record_not_found","USB device is not attached")
                if (!manager.hasPermission(device)) {
                    val recovery=if (autoRequest) DikcizUsbControl(context,logger).requestAccess(request) else JSONObject().put("state","permission_required")
                    return JSONObject().put("state","permission_required").put("permissionRequest",recovery.put("command","usbRequestAccess")
                        .put("arguments",JSONObject().put("deviceId",device.deviceId)).put("retryRequired",true))
                }
            }
            return open(context, logger, request)
        }
        val connection = sessions[request.getString("id")] ?: DikcizNativeCommands.failure("device_connection_not_found", "Connection not found or expired")
        if (type == READ) return connection.document()
        if (type == CLOSE) {
            close(connection, logger)
            return connection.document().put("state", "closed")
        }
        if (connection.closed) DikcizNativeCommands.failure("device_connection_closed", "Open a new connection")
        validateExchange(connection.kind, request)
        if (!connection.operation.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "An operation is already running on this connection")
        val timedOut = AtomicBoolean()
        val timeout = request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT)
        val deadline = timers.schedule({
            timedOut.set(true)
            close(connection, logger)
        }, timeout, TimeUnit.MILLISECONDS)
        return try {
            connection.exchange(request).put("connectionId", connection.id).put("active", !connection.closed)
        } catch (exception: IOException) {
            close(connection, logger)
            DikcizNativeCommands.failure(if (timedOut.get()) "device_connection_timeout" else "device_connection_io_failed",
                "Connection transfer failed. Accepted writes can have partial effects; inspect the peer before retrying.")
        } catch (exception: java.util.concurrent.TimeoutException) {
            close(connection, logger)
            DikcizNativeCommands.failure("device_connection_timeout", "Connection transfer timed out")
        } finally {
            deadline.cancel(false)
            connection.operation.set(false)
        }
    }
    private fun validateExchange(kind: String, request: JSONObject) {
        val common = setOf("id", "timeoutMilliseconds", "type", "requestId", "autoRequestPermissions")
        val required = mutableSetOf<String>()
        val allowed = mutableSetOf<String>()
        allowed.addAll(common)
        val action = request.optString("action")
        when (kind) {
            "rfcomm" -> allowed.addAll(setOf("dataBase64", "readBytes"))
            "gatt" -> {
                required.add("action"); allowed.add("action")
                if (action !in setOf("discover", "notifications", "read", "write", "subscribe", "unsubscribe")) DikcizNativeCommands.invalid("Choose a supported GATT action")
                if (action == "notifications") allowed.add("cursor")
                if (action in setOf("read", "write", "subscribe", "unsubscribe")) {
                    required.addAll(setOf("serviceUuid", "characteristicUuid"))
                    allowed.addAll(setOf("serviceUuid", "characteristicUuid", "serviceInstanceId", "characteristicInstanceId"))
                }
                if (action == "write") { required.add("dataBase64"); allowed.add("dataBase64") }
            }
            "usb" -> {
                required.add("action"); allowed.addAll(setOf("action", "readBytes", "dataBase64"))
                if (action == "control") {
                    required.addAll(setOf("requestType", "request", "value", "index"))
                    allowed.addAll(setOf("requestType", "request", "value", "index"))
                } else if (action in setOf("bulk", "interrupt")) {
                    required.add("endpointAddress"); allowed.add("endpointAddress")
                } else DikcizNativeCommands.invalid("Choose control, bulk or interrupt")
                val direction = request.optInt(if (action == "control") "requestType" else "endpointAddress", -1)
                if (direction >= 0) {
                    val input = direction and UsbConstants.USB_DIR_IN != 0
                    if (!input) required.add("dataBase64")
                    if (input && request.has("dataBase64") || !input && request.has("readBytes")) DikcizNativeCommands.invalid("Transfer bytes do not match the endpoint direction")
                }
            }
            "nfc" -> {
                required.add("action"); allowed.add("action")
                if (action !in setOf("transceive", "readNdef", "writeNdef")) DikcizNativeCommands.invalid("Choose transceive, readNdef or writeNdef")
                if (action != "readNdef") { required.add("dataBase64"); allowed.add("dataBase64") }
            }
        }
        if (required.any { !request.has(it) }) DikcizNativeCommands.invalid("Missing required transfer fields: " + required.joinToString())
        if (request.keys().asSequence().any { it !in allowed }) DikcizNativeCommands.invalid("Field does not apply to this transfer")
        for (key in listOf("serviceUuid", "characteristicUuid")) if (request.has(key)) {
            try { UUID.fromString(request.getString(key)) }
            catch (exception: IllegalArgumentException) { DikcizNativeCommands.invalid("Invalid transfer UUID") }
        }
        if (request.has("dataBase64")) {
            val bytes = decode(request)
            if (kind == "gatt" && bytes.size > 512) DikcizNativeCommands.invalid("GATT writes are limited to 512 bytes")
        }
    }
    private fun validateOpen(request: JSONObject) {
        val kind = request.getString("kind")
        val required = when (kind) {
            "rfcomm" -> setOf("address", "serviceUuid")
            "gatt" -> setOf("address")
            "usb" -> setOf("deviceId")
            else -> emptySet()
        }
        if (required.any { !request.has(it) }) DikcizNativeCommands.invalid("Missing required connection fields: " + required.joinToString())
        val allowed = setOf("kind", "timeoutMilliseconds", "maximumDurationMilliseconds", "type", "requestId", "autoRequestPermissions") + when (kind) {
            "rfcomm" -> setOf("address", "serviceUuid", "secure")
            "gatt" -> setOf("address")
            "usb" -> setOf("deviceId", "interfaceIndex")
            else -> setOf("technology")
        }
        if (request.keys().asSequence().any { it !in allowed }) DikcizNativeCommands.invalid("Field does not apply to this connection kind")
        if (kind in setOf("rfcomm", "gatt") && !android.bluetooth.BluetoothAdapter.checkBluetoothAddress(
            request.getString("address").uppercase(java.util.Locale.ROOT))) DikcizNativeCommands.invalid("Invalid Bluetooth address")
        if (request.has("serviceUuid")) {
            try { UUID.fromString(request.getString("serviceUuid")) }
            catch (exception: IllegalArgumentException) { DikcizNativeCommands.invalid("Invalid service UUID") }
        }
    }
    private fun open(context: Context, logger: DikcizLogger, request: JSONObject): JSONObject {
        synchronized(this) {
            if (sessions.values.count { !it.closed } + openings >= MAX_CONNECTIONS) DikcizNativeCommands.failure("device_connection_capacity", "At most eight connections can be active")
            if (sessions.size >= 64) {
                sessions.values.filter { it.closed }.sortedBy { it.createdAt }.take(sessions.size - 63).forEach { sessions.remove(it.id, it) }
                if (sessions.size >= 64) DikcizNativeCommands.failure("device_connection_capacity", "Retained connection capacity reached")
            }
            openings++
        }
        var created: DikcizDeviceConnection? = null
        try {
            val timeout = request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT)
            val kind = request.getString("kind")
            created = when (kind) {
                "rfcomm" -> rfcomm(context, logger, request, timeout)
                "gatt" -> DikcizGattConnection.open(context.applicationContext, request, timeout)
                "usb" -> usb(context, logger, request)
                "nfc" -> nfc(context, logger, request, timeout)
                else -> DikcizNativeCommands.invalid("Choose rfcomm, gatt, usb or nfc")
            }
            val session = created
            val duration = request.optLong("maximumDurationMilliseconds", 300000)
            session.expiresAt = System.currentTimeMillis() + duration
            sessions[session.id] = session
            try { DikcizConnectionsService.start(context) } catch (exception: RuntimeException) { close(session,logger); sessions.remove(session.id); throw exception }
            timers.schedule({ close(session, logger) }, duration, TimeUnit.MILLISECONDS)
            timers.schedule({ sessions.remove(session.id, session) }, duration + 300000, TimeUnit.MILLISECONDS)
            logger.info("device_connection_opened", mapOf("kind" to kind))
            return session.document().put("state", "connected")
        } catch (exception: Exception) {
            created?.let { close(it, logger) }
            throw exception
        } finally { synchronized(this) { openings-- } }
    }
    private fun close(connection: DikcizDeviceConnection, logger: DikcizLogger) {
        synchronized(connection) {
            if (connection.releaseAttempted) return
            connection.releaseAttempted = true
            try { connection.close(); connection.releaseConfirmed = true }
            catch (exception: Exception) { logger.warn("device_connection_close_failed", mapOf("kind" to connection.kind,"error_class" to exception.javaClass.simpleName)) }
        }
    }
    fun hasActive()=sessions.values.any { !it.closed }
    fun closeAll(logger: DikcizLogger) { sessions.values.forEach { close(it,logger) } }

    internal fun decode(request: JSONObject, key: String = "dataBase64"): ByteArray {
        val value = try { Base64.getDecoder().decode(request.getString(key)) }
            catch (exception: IllegalArgumentException) { DikcizNativeCommands.invalid("Invalid base64 bytes") }
        if (value.size > MAX_BYTES) DikcizNativeCommands.invalid("Transfer exceeds 65536 bytes")
        return value
    }
    internal fun bluetooth(context: Context): android.bluetooth.BluetoothAdapter {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DikcizNativeCommands.requirePermission(context, Manifest.permission.BLUETOOTH_CONNECT, "bluetooth")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: DikcizNativeCommands.failure("native_unavailable", "Bluetooth hardware unavailable")
        if (!adapter.isEnabled) DikcizNativeCommands.failure("bluetooth_disabled", "Enable Bluetooth first")
        return adapter
    }
    private fun rfcomm(context: Context, logger: DikcizLogger, request: JSONObject, timeout: Long): DikcizDeviceConnection {
        val adapter = bluetooth(context)
        val address = request.optString("address").uppercase(java.util.Locale.ROOT)
        if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address.uppercase(java.util.Locale.ROOT))) DikcizNativeCommands.invalid("Invalid Bluetooth address")
        val uuid = try { UUID.fromString(request.getString("serviceUuid")) }
            catch (exception: IllegalArgumentException) { DikcizNativeCommands.invalid("Invalid service UUID") }
        val device = adapter.getRemoteDevice(address)
        val socket = if (request.optBoolean("secure", true)) device.createRfcommSocketToServiceRecord(uuid)
            else device.createInsecureRfcommSocketToServiceRecord(uuid)
        val deadline = timers.schedule({ try { socket.close() } catch (exception: IOException) { logger.warn("bluetooth_connect_close_failed") } }, timeout, TimeUnit.MILLISECONDS)
        try { socket.connect() } catch (exception: Exception) { socket.close(); throw exception }
        finally { deadline.cancel(false) }
        return object : DikcizDeviceConnection("rfcomm") {
            override fun details() = JSONObject().put("address", address).put("serviceUuid", uuid.toString())
            override fun exchange(request: JSONObject): JSONObject {
                if (request.has("dataBase64")) { socket.outputStream.write(decode(request)); socket.outputStream.flush() }
                val maximum = request.optInt("readBytes", DEFAULT_READ_BYTES)
                val buffer = ByteArray(maximum)
                val count = if (maximum == 0) 0 else socket.inputStream.read(buffer)
                if (count < 0) { close(); throw IOException("Peer disconnected") }
                return JSONObject().put("state", "transferred").put("bytesRead", count)
                    .put("dataBase64", Base64.getEncoder().encodeToString(buffer.copyOf(count)))
                    .put("completeMessage", false)
            }
            override fun close() { closed = true; socket.close() }
        }
    }
    private fun usb(context: Context, logger: DikcizLogger, request: JSONObject): DikcizDeviceConnection {
        val manager = context.getSystemService(UsbManager::class.java) ?: DikcizNativeCommands.failure("native_unavailable", "USB host unavailable")
        val device = manager.deviceList.values.find { it.deviceId == request.getInt("deviceId") }
            ?: DikcizNativeCommands.failure("native_record_not_found", "USB device is not attached")
        if (!manager.hasPermission(device)) DikcizNativeCommands.failure("native_permission_usbDevice", "Use usbRequestAccess for this attached device")
        val index = request.optInt("interfaceIndex", 0)
        if (index !in 0 until device.interfaceCount) DikcizNativeCommands.invalid("USB interface is unavailable")
        val target = device.getInterface(index)
        val connection = manager.openDevice(device) ?: DikcizNativeCommands.failure("usb_open_failed", "Android did not open the USB device")
        if (!connection.claimInterface(target, false)) {
            connection.close(); DikcizNativeCommands.failure("usb_interface_busy", "USB interface is already owned")
        }
        return object : DikcizDeviceConnection("usb") {
            override fun details() = JSONObject().put("deviceId", device.deviceId).put("interfaceIndex", index)
            override fun exchange(request: JSONObject): JSONObject {
                val action = request.getString("action")
                val timeout = request.optInt("timeoutMilliseconds", DEFAULT_TIMEOUT.toInt())
                val input: Boolean
                val buffer: ByteArray
                val count: Int
                if (action == "control") {
                    for (key in listOf("requestType", "request", "value", "index")) if (!request.has(key)) DikcizNativeCommands.invalid("Missing USB control field")
                    input = request.getInt("requestType") and UsbConstants.USB_DIR_IN != 0
                    buffer = if (input) ByteArray(request.optInt("readBytes", DEFAULT_READ_BYTES)) else decode(request)
                    count = connection.controlTransfer(request.getInt("requestType"), request.getInt("request"), request.getInt("value"), request.getInt("index"), buffer, buffer.size, timeout)
                } else {
                    if (action !in setOf("bulk", "interrupt")) DikcizNativeCommands.invalid("Choose control, bulk or interrupt")
                    val endpoint = (0 until target.endpointCount).map { target.getEndpoint(it) }.find { it.address == request.getInt("endpointAddress") }
                        ?: DikcizNativeCommands.failure("native_record_not_found", "Endpoint is not on the claimed interface")
                    if (endpoint.type != if (action == "bulk") UsbConstants.USB_ENDPOINT_XFER_BULK else UsbConstants.USB_ENDPOINT_XFER_INT) DikcizNativeCommands.invalid("Endpoint type does not match the transfer")
                    input = endpoint.direction == UsbConstants.USB_DIR_IN
                    buffer = if (input) ByteArray(request.optInt("readBytes", DEFAULT_READ_BYTES)) else decode(request)
                    if (action == "bulk") count = connection.bulkTransfer(endpoint, buffer, buffer.size, timeout)
                    else {
                        val transfer = UsbRequest()
                        try {
                            if (!transfer.initialize(connection, endpoint)) DikcizNativeCommands.failure("usb_transfer_failed", "Could not initialize interrupt request")
                            val bytes = ByteBuffer.wrap(buffer)
                            if (!transfer.queue(bytes)) DikcizNativeCommands.failure("usb_transfer_failed", "Interrupt transfer was rejected")
                            if (connection.requestWait(timeout.toLong()) !== transfer) DikcizNativeCommands.failure("usb_transfer_failed", "Interrupt transfer did not complete")
                            count = bytes.position()
                        } finally { transfer.cancel(); transfer.close() }
                    }
                }
                if (count < 0) DikcizNativeCommands.failure("usb_transfer_failed", "USB transfer failed or timed out; writes may be partial")
                return JSONObject().put("state", "transferred").put("bytesTransferred", count)
                    .put("dataBase64", if (input) Base64.getEncoder().encodeToString(buffer.copyOf(count)) else JSONObject.NULL)
            }
            override fun close() {
                if (closed) return
                closed = true
                try { if (!connection.releaseInterface(target)) logger.warn("usb_interface_release_failed") }
                finally { connection.close() }
            }
        }
    }
    private fun nfc(context: Context, logger: DikcizLogger, request: JSONObject, timeout: Long): DikcizDeviceConnection {
        val adapter = context.getSystemService(NfcManager::class.java)?.defaultAdapter
            ?: DikcizNativeCommands.failure("native_unavailable", "NFC hardware unavailable")
        if (!adapter.isEnabled) DikcizNativeCommands.failure("nfc_disabled", "Enable NFC first")
        val activity = context as? Activity ?: DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz for NFC")
        if (!activity.hasWindowFocus()) DikcizNativeCommands.failure("capture_foreground_required", "Open and unlock Dikciz for NFC")
        if (!DikcizNfcLease.busy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "NFC reader is already owned")
        val active = AtomicBoolean(true)
        val done = CountDownLatch(1)
        val selected = AtomicReference<TagTechnology?>()
        val failure = AtomicReference<Exception?>()
        val claimed = AtomicBoolean()
        val owner = WeakReference(activity)
        val techName = request.optString("technology", "IsoDep")
        val handler = Handler(Looper.getMainLooper())
        try {
            handler.post {
                try {
                    if (!active.get()) return@post
                    adapter.enableReaderMode(activity, { tag ->
                        if (!active.get() || !claimed.compareAndSet(false, true)) return@enableReaderMode
                        try {
                            val tech: TagTechnology? = when (techName) {
                                "IsoDep" -> IsoDep.get(tag); "NfcA" -> NfcA.get(tag); "NfcB" -> NfcB.get(tag)
                                "NfcF" -> NfcF.get(tag); "NfcV" -> NfcV.get(tag); "Ndef" -> Ndef.get(tag)
                                "MifareClassic" -> MifareClassic.get(tag); "MifareUltralight" -> MifareUltralight.get(tag)
                                else -> DikcizNativeCommands.invalid("Unsupported NFC technology")
                            }
                            if (tech == null) DikcizNativeCommands.failure("nfc_technology_unavailable", "Tag lacks the selected technology")
                            selected.set(tech)
                            if (!active.get()) { tech.close(); return@enableReaderMode }
                            tech.connect()
                            if (!active.get()) tech.close()
                        } catch (exception: Exception) { failure.set(exception) }
                        finally { done.countDown() }
                    }, android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_NFC_B or
                        android.nfc.NfcAdapter.FLAG_READER_NFC_F or android.nfc.NfcAdapter.FLAG_READER_NFC_V or android.nfc.NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null)
                } catch (exception: Exception) { failure.set(exception); done.countDown() }
            }
            if (!done.await(timeout, TimeUnit.MILLISECONDS)) DikcizNativeCommands.failure("nfc_timeout", "No matching tag was presented")
            failure.get()?.let { throw it }
            val technology = selected.get() ?: DikcizNativeCommands.failure("nfc_technology_unavailable", "No tag technology connected")
            return object : DikcizDeviceConnection("nfc") {
                override fun details() = JSONObject().put("technology", techName)
                override fun exchange(request: JSONObject): JSONObject {
                    if (owner.get()?.hasWindowFocus() != true) { close(); DikcizNativeCommands.failure("capture_foreground_required", "Keep Dikciz foreground for NFC") }
                    if (!technology.isConnected) { close(); DikcizNativeCommands.failure("nfc_tag_lost", "Present the tag again with a new connection") }
                    val action = request.optString("action", "transceive")
                    val bytes = if (technology is Ndef) {
                        if (action == "writeNdef") {
                            if (!technology.isWritable) DikcizNativeCommands.failure("nfc_tag_read_only", "Tag is not writable")
                            val message = NdefMessage(decode(request))
                            if (message.toByteArray().size > technology.maxSize) DikcizNativeCommands.invalid("NDEF exceeds tag capacity")
                            technology.writeNdefMessage(message)
                        } else if (action != "readNdef") DikcizNativeCommands.invalid("Choose readNdef or writeNdef")
                        technology.ndefMessage?.toByteArray() ?: ByteArray(0)
                    } else {
                        if (action != "transceive") DikcizNativeCommands.invalid("Raw technology requires transceive")
                        val data = decode(request)
                        when (technology) {
                            is IsoDep -> { technology.timeout = request.optInt("timeoutMilliseconds", DEFAULT_TIMEOUT.toInt()); technology.transceive(data) }
                            is NfcA -> { technology.timeout = request.optInt("timeoutMilliseconds", DEFAULT_TIMEOUT.toInt()); technology.transceive(data) }
                            is NfcB -> technology.transceive(data)
                            is NfcF -> technology.transceive(data)
                            is NfcV -> technology.transceive(data)
                            is MifareClassic -> technology.transceive(data)
                            is MifareUltralight -> technology.transceive(data)
                            else -> DikcizNativeCommands.invalid("Unsupported NFC technology")
                        }
                    }
                    if (bytes.size > MAX_BYTES) DikcizNativeCommands.failure("nfc_response_too_large", "NFC response exceeds 65536 bytes")
                    return JSONObject().put("state", "transferred").put("dataBase64", Base64.getEncoder().encodeToString(bytes))
                }
                override fun close() {
                    if (closed) return
                    closed = true; active.set(false)
                    try { technology.close() } finally {
                        handler.post { owner.get()?.let { adapter.disableReaderMode(it) } }
                        DikcizNfcLease.busy.set(false)
                    }
                }
            }
        } catch (exception: Exception) {
            active.set(false)
            try { selected.get()?.close() } finally {
                handler.post { adapter.disableReaderMode(activity) }
                DikcizNfcLease.busy.set(false)
            }
            throw exception
        }
    }
    private fun text(max: Int) = JSONObject().put("type", "string").put("minLength", 1).put("maxLength", max)
    private fun integer(min: Long, max: Long, default: Long? = null) = JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)
        .apply { default?.let { put("default", it) } }
    private val identity = mapOf("id" to text(80))
    private val page = mapOf("offset" to integer(0, 1000000, 0), "limit" to integer(1, 200, 50))
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(OPEN, "dikciz_device_connection_open",
            "Open a retained rfcomm, gatt, usb or nfc connection for multiple commands. At most eight active; default lifetime five minutes, configurable up to one hour. Process death closes hardware. Bluetooth uses address and serviceUuid for rfcomm; USB requires deviceId and owner-granted access; NFC requires foreground and a presented tag. No payment interpretation or automatic retries.",
            mapOf("kind" to text(16).put("enum", JSONArray(listOf("rfcomm", "gatt", "usb", "nfc"))), "address" to text(32),
                "serviceUuid" to text(40), "secure" to JSONObject().put("type", "boolean"), "deviceId" to integer(0, Int.MAX_VALUE.toLong()),
                "interfaceIndex" to integer(0, 255), "technology" to text(32).put("enum", JSONArray(listOf("IsoDep","NfcA","NfcB","NfcF","NfcV","Ndef","MifareClassic","MifareUltralight"))),
                "timeoutMilliseconds" to integer(1, 120000, DEFAULT_TIMEOUT), "maximumDurationMilliseconds" to integer(1000, 3600000, 300000)),
            setOf("kind"), false),
        DikcizNativeCommands.Definition(EXCHANGE, "dikciz_device_connection_exchange",
            "One serialized operation on a retained connection. RFCOMM writes optional dataBase64 and reads at most readBytes; a chunk is not a complete message. USB action control/bulk/interrupt uses the claimed interface. NFC action transceive/readNdef/writeNdef. GATT action discover/read/write/subscribe/unsubscribe/notifications, with exact service/characteristic UUIDs. Timeout closes the connection; accepted writes can be partial. All exchange actions are mutating for access control.",
            identity + mapOf("action" to text(32), "dataBase64" to text((MAX_BYTES+2)/3*4).put("minLength", 0), "readBytes" to integer(0, MAX_BYTES.toLong(), DEFAULT_READ_BYTES.toLong()),
                "timeoutMilliseconds" to integer(1, 120000, DEFAULT_TIMEOUT), "endpointAddress" to integer(0,255), "requestType" to integer(0,255),
                "request" to integer(0,255), "value" to integer(0,65535), "index" to integer(0,65535),
                "serviceUuid" to text(40), "characteristicUuid" to text(40), "serviceInstanceId" to integer(0, Int.MAX_VALUE.toLong()),
                "characteristicInstanceId" to integer(0, Int.MAX_VALUE.toLong()), "cursor" to integer(0,Long.MAX_VALUE,0)),
            setOf("id"), false),
        DikcizNativeCommands.Definition(CLOSE, "dikciz_device_connection_close", "Close a retained device connection and release claimed hardware. Completed effects remain.", identity, setOf("id"), false),
        DikcizNativeCommands.Definition(READ, "dikciz_device_connection_read", "Read connection lifecycle metadata without opening or transferring data.", identity, setOf("id")),
        DikcizNativeCommands.Definition(LIST, "dikciz_device_connections_list", "Read a paginated list of retained connection metadata. Inactive entries expire after five minutes.", page),
    )
    val TYPES = DEFINITIONS.map { it.type }.toSet()
}
