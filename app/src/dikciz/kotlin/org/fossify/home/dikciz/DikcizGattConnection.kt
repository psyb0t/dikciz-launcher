package org.fossify.home.dikciz

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class DikcizGattConnection private constructor() : DikcizDeviceConnection("gatt") {
    private var gatt: BluetoothGatt? = null
    private data class Pending(val kind: String, val target: String?, val latch: CountDownLatch = CountDownLatch(1),
        var result: JSONObject? = null, var failure: Int? = null)
    private var pending = Pending("discover", null)
    private var nextSequence = 1L
    private val notifications = ArrayDeque<JSONObject>()
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    if (!connection.discoverServices()) finish(null, -1)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    closed = true; finish(null, if (status == BluetoothGatt.GATT_SUCCESS) -1 else status); connection.close()
                }
            } catch (exception: SecurityException) {
                closed = true
                finish(null, -1)
                android.util.Log.w("DikcizGatt", "Bluetooth access was revoked during a callback")
            }
        }
        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            finish(if (status == BluetoothGatt.GATT_SUCCESS) inventory(connection) else null, status)
        }
        override fun onCharacteristicRead(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            received(characteristic, characteristic.value ?: ByteArray(0), status)
        }
        override fun onCharacteristicRead(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            received(characteristic, value, status)
        }
        override fun onCharacteristicWrite(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            synchronized(this@DikcizGattConnection) {
                if (pending.kind == "write" && pending.target == identity(characteristic)) finish(JSONObject().put("state", "written"), status)
            }
        }
        override fun onDescriptorWrite(connection: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            synchronized(this@DikcizGattConnection) {
                if (pending.kind in setOf("subscribe", "unsubscribe") && pending.target == identity(descriptor.characteristic)) {
                    finish(JSONObject().put("state", if (pending.kind == "subscribe") "subscribed" else "unsubscribed"), status)
                }
            }
        }
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            notification(characteristic, characteristic.value ?: ByteArray(0))
        }
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            notification(characteristic, value)
        }
    }
    @Synchronized private fun received(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (pending.kind == "read" && pending.target == identity(characteristic)) {
            finish(JSONObject().put("state", "read").put("dataBase64", Base64.getEncoder().encodeToString(value)), status)
        }
    }
    @Synchronized private fun notification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (closed || value.size > DikcizDeviceConnections.MAX_BYTES) return
        notifications.addLast(JSONObject().put("sequence", nextSequence++).put("characteristicUuid", characteristic.uuid.toString())
            .put("characteristicInstanceId", characteristic.instanceId).put("serviceUuid", characteristic.service.uuid.toString())
            .put("serviceInstanceId", characteristic.service.instanceId).put("receivedAt", System.currentTimeMillis())
            .put("dataBase64", Base64.getEncoder().encodeToString(value)))
        while (notifications.size > MAX_NOTIFICATIONS) notifications.removeFirst()
    }
    @Synchronized private fun finish(value: JSONObject?, status: Int) {
        if (pending.latch.count == 0L) return
        pending.result = value
        if (status != BluetoothGatt.GATT_SUCCESS) pending.failure = status
        pending.latch.countDown()
    }
    private fun await(timeout: Long): JSONObject {
        val operation = synchronized(this) { pending }
        if (!operation.latch.await(timeout, TimeUnit.MILLISECONDS)) {
            close(); DikcizNativeCommands.failure("device_connection_timeout", "GATT operation timed out and connection was closed")
        }
        operation.failure?.let {
            DikcizNativeCommands.failure("bluetooth_gatt_failed", "Android GATT returned status " + it)
        }
        return operation.result ?: DikcizNativeCommands.failure("bluetooth_gatt_failed", "GATT returned no result")
    }
    override fun exchange(request: JSONObject): JSONObject = try {
        exchangeGranted(request)
    } catch (exception: SecurityException) {
        DikcizNativeCommands.failure("native_permission_bluetooth", "Bluetooth access was revoked")
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun exchangeGranted(request: JSONObject): JSONObject {
        val connection = gatt ?: throw IOException("GATT closed")
        val action = request.getString("action")
        if (action == "discover") return inventory(connection)
        if (action == "notifications") return synchronized(this) {
            val cursor = request.optLong("cursor", 0)
            val first = notifications.firstOrNull()?.getLong("sequence") ?: nextSequence
            if (cursor >= nextSequence) DikcizNativeCommands.invalid("Cursor is ahead of this connection")
            val rows = notifications.filter { it.getLong("sequence") > cursor }
            JSONObject().put("items", JSONArray(rows)).put("nextCursor", rows.lastOrNull()?.getLong("sequence") ?: cursor)
                .put("lost", cursor < first - 1).put("firstAvailableSequence", first)
        }
        if (action !in setOf("read", "write", "subscribe", "unsubscribe")) DikcizNativeCommands.invalid("Unsupported GATT connection action")
        val serviceUuid = uuid(request, "serviceUuid")
        val characteristicUuid = uuid(request, "characteristicUuid")
        val services = connection.services.filter { it.uuid == serviceUuid && (!request.has("serviceInstanceId") || it.instanceId == request.getInt("serviceInstanceId")) }
        if (services.size > 1) DikcizNativeCommands.failure("bluetooth_gatt_ambiguous", "Select serviceInstanceId")
        val service = services.singleOrNull() ?: DikcizNativeCommands.failure("native_record_not_found", "Service not found")
        val candidates = service.characteristics.filter { it.uuid == characteristicUuid && (!request.has("characteristicInstanceId") || it.instanceId == request.getInt("characteristicInstanceId")) }
        if (candidates.size > 1) DikcizNativeCommands.failure("bluetooth_gatt_ambiguous", "Select characteristicInstanceId")
        val characteristic = candidates.singleOrNull() ?: DikcizNativeCommands.failure("native_record_not_found", "Characteristic not found")
        synchronized(this) { pending = Pending(action, identity(characteristic)) }
        val accepted = when (action) {
            "read" -> connection.readCharacteristic(characteristic)
            "write" -> {
                val value = DikcizDeviceConnections.decode(request)
                if (value.size > 512) DikcizNativeCommands.invalid("GATT characteristic writes are limited to 512 bytes")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    connection.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; characteristic.value = value
                    connection.writeCharacteristic(characteristic)
                }
            }
            else -> {
                val enabled = action == "subscribe"
                val notify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val indicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                if (enabled && !notify && !indicate) DikcizNativeCommands.failure("bluetooth_gatt_unsupported", "Characteristic has no notification support")
                val descriptor = characteristic.getDescriptor(CCCD) ?: DikcizNativeCommands.failure("native_record_not_found", "Client configuration descriptor is missing")
                if (!connection.setCharacteristicNotification(characteristic, enabled)) DikcizNativeCommands.failure("bluetooth_gatt_rejected", "Android rejected notification routing")
                val value = if (!enabled) BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    else if (notify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) connection.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
                else { descriptor.value = value; connection.writeDescriptor(descriptor) }
            }
        }
        if (!accepted) DikcizNativeCommands.failure("bluetooth_gatt_rejected", "Android rejected the operation")
        return await(request.optLong("timeoutMilliseconds", DikcizDeviceConnections.DEFAULT_TIMEOUT))
    }
    override fun close() {
        if (closed && gatt == null) return
        closed = true
        val connection = gatt; gatt = null
        try {
            try { connection?.disconnect() } finally { connection?.close(); finish(null, -1) }
        } catch (exception: SecurityException) {
            finish(null, -1)
            throw exception
        }
    }
    companion object {
        private const val MAX_NOTIFICATIONS = 64
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private fun uuid(request: JSONObject, key: String): UUID = try { UUID.fromString(request.getString(key)) }
            catch (exception: IllegalArgumentException) { DikcizNativeCommands.invalid("Invalid UUID") }
        private fun identity(characteristic: BluetoothGattCharacteristic) = characteristic.service.uuid.toString() + ":" +
            characteristic.service.instanceId + ":" + characteristic.uuid + ":" + characteristic.instanceId
        private fun inventory(connection: BluetoothGatt) = JSONObject().put("state", "discovered")
            .put("services", JSONArray(connection.services.map { service ->
                JSONObject().put("uuid", service.uuid.toString()).put("instanceId", service.instanceId)
                    .put("characteristics", JSONArray(service.characteristics.map {
                        JSONObject().put("uuid", it.uuid.toString()).put("instanceId", it.instanceId).put("properties", it.properties)
                    }))
            }))
        fun open(context: Context, request: JSONObject, timeout: Long): DikcizGattConnection {
            val adapter = DikcizDeviceConnections.bluetooth(context)
            val address = request.getString("address").uppercase(java.util.Locale.ROOT)
            if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address.uppercase(java.util.Locale.ROOT))) DikcizNativeCommands.invalid("Invalid Bluetooth address")
            val session = DikcizGattConnection()
            try {
                session.gatt = adapter.getRemoteDevice(address).connectGatt(context.applicationContext, false, session.callback, BluetoothDevice.TRANSPORT_LE)
                    ?: DikcizNativeCommands.failure("bluetooth_gatt_unavailable", "Android did not create GATT")
                session.await(timeout)
                if (session.closed) throw IOException("GATT disconnected")
                return session
            } catch (exception: SecurityException) {
                try { session.close() } catch (cleanup: SecurityException) {
                    android.util.Log.w("DikcizGatt", "Bluetooth access prevented closing the opening session")
                }
                DikcizNativeCommands.failure("native_permission_bluetooth", "Bluetooth access was revoked while connecting")
            } catch (exception: Exception) { session.close(); throw exception }
        }
    }
}
