package org.fossify.home.dikciz

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

// The adapter provider enforces version-specific CONNECT access before opening GATT.
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class DikcizGattControl(
    private val context: Context,
    private val logger: DikcizLogger,
    private val adapter: () -> BluetoothAdapter,
) {
    fun execute(request: JSONObject): JSONObject {
        val device = adapter().getRemoteDevice(request.getString(KEY_ADDRESS).uppercase(java.util.Locale.ROOT))
        if (!busy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "A Dikciz GATT transaction is already active")
        val session = Session(request)
        var connection: BluetoothGatt? = null
        try {
            connection = device.connectGatt(context.applicationContext, false, session, BluetoothDevice.TRANSPORT_LE)
                ?: DikcizNativeCommands.failure("bluetooth_gatt_unavailable", "Android did not create a GATT connection")
            if (!session.complete.await(request.optLong(KEY_TIMEOUT, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                if (session.subscribed.get()) return session.observations().put("windowExpired", true)
                DikcizNativeCommands.failure("bluetooth_timeout", "GATT operation did not complete before the deadline")
            }
            session.failure.get()?.let { throw it }
            return session.result.get() ?: DikcizNativeCommands.failure("bluetooth_gatt_failed", "GATT produced no result")
        } finally {
            session.active.set(false)
            try {
                try { connection?.disconnect() } catch (exception: RuntimeException) {
                    logger.warn("bluetooth_gatt_disconnect_failed", mapOf("error_class" to exception.javaClass.simpleName))
                }
                connection?.close()
            } finally { busy.set(false) }
        }
    }

    private inner class Session(private val request: JSONObject) : BluetoothGattCallback() {
        val complete = CountDownLatch(1)
        val active = AtomicBoolean(true)
        val subscribed = AtomicBoolean()
        val failure = AtomicReference<DikcizAutomationRequestException?>()
        val result = AtomicReference<JSONObject?>()
        private val terminal = AtomicBoolean()
        private val events = ArrayList<JSONObject>()
        private var mtu: Int? = null

        private fun fail(code: String, message: String) {
            if (active.get() && terminal.compareAndSet(false, true)) {
                failure.set(DikcizAutomationRequestException(code, message))
                complete.countDown()
            }
        }

        private fun finish(value: JSONObject) {
            if (active.get() && terminal.compareAndSet(false, true)) {
                result.set(value.put("mtu", mtu ?: JSONObject.NULL))
                complete.countDown()
            }
        }

        private fun safe(action: () -> Unit) {
            if (!active.get() || terminal.get()) return
            try { action() } catch (exception: DikcizAutomationRequestException) {
                fail(exception.code, exception.message.orEmpty())
            } catch (_: SecurityException) {
                fail("native_permission_bluetooth", "Bluetooth access changed during GATT work")
            } catch (exception: Exception) {
                logger.warn("bluetooth_gatt_callback_failed", mapOf("error_class" to exception.javaClass.simpleName))
                fail("bluetooth_gatt_failed", "Android rejected the GATT callback operation")
            }
        }

        private fun requireStatus(status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) DikcizNativeCommands.failure("bluetooth_gatt_status_" + status, "Android GATT returned a failure status")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = safe {
            requireStatus(status)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (request.has(KEY_MTU)) {
                    if (!gatt.requestMtu(request.getInt(KEY_MTU))) fail("bluetooth_gatt_rejected", "Android rejected MTU negotiation")
                } else if (!gatt.discoverServices()) fail("bluetooth_gatt_rejected", "Android rejected service discovery")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail("bluetooth_disconnected", "The GATT peer disconnected")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, value: Int, status: Int) = safe {
            requireStatus(status)
            mtu = value
            if (request.has(KEY_MTU) && !gatt.discoverServices()) fail("bluetooth_gatt_rejected", "Android rejected service discovery")
        }

        fun observations(): JSONObject = synchronized(events) {
            JSONObject().put("state", "observed").put("items", JSONArray(events.toList())).put("count", events.size).put("mtu", mtu ?: JSONObject.NULL)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = safe {
            requireStatus(status)
            if (request.getString(KEY_ACTION) == ACTION_DISCOVER) {
                finish(discovery(gatt)); return@safe
            }
            val services = gatt.services.filter { it.uuid == UUID.fromString(request.getString(KEY_SERVICE)) &&
                (!request.has(KEY_SERVICE_ID) || it.instanceId == request.getInt(KEY_SERVICE_ID)) }
            if (services.size > 1) DikcizNativeCommands.failure("bluetooth_gatt_ambiguous", "Select a serviceInstanceId from discovery")
            val service = services.singleOrNull() ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "GATT service not found")
            val characteristics = service.characteristics.filter { it.uuid == UUID.fromString(request.getString(KEY_CHARACTERISTIC)) &&
                (!request.has(KEY_CHARACTERISTIC_ID) || it.instanceId == request.getInt(KEY_CHARACTERISTIC_ID)) }
            if (characteristics.size > 1) DikcizNativeCommands.failure("bluetooth_gatt_ambiguous", "Select a characteristicInstanceId from discovery")
            val characteristic = characteristics.singleOrNull() ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "GATT characteristic not found")
            selected = characteristic
            when (request.getString(KEY_ACTION)) {
                ACTION_READ -> if (!gatt.readCharacteristic(characteristic)) fail("bluetooth_gatt_rejected", "Android rejected the characteristic read")
                ACTION_WRITE -> {
                    val writeType = WRITE_TYPES.getValue(request.optString(KEY_WRITE_TYPE, "default"))
                    val data = decode(request)
                    val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(characteristic, data, writeType) == BluetoothStatusCodes.SUCCESS
                    } else {
                        characteristic.writeType = writeType
                        characteristic.value = data
                        gatt.writeCharacteristic(characteristic)
                    }
                    if (!accepted) fail("bluetooth_gatt_rejected", "Android rejected the characteristic write")
                }
                ACTION_READ_DESCRIPTOR, ACTION_WRITE_DESCRIPTOR -> {
                    val descriptors = characteristic.descriptors.filter { it.uuid == UUID.fromString(request.getString(KEY_DESCRIPTOR)) }
                    if (descriptors.size > 1) DikcizNativeCommands.failure("bluetooth_gatt_ambiguous", "Descriptor UUID is ambiguous")
                    val descriptor = descriptors.singleOrNull() ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "GATT descriptor not found")
                    if (request.getString(KEY_ACTION) == ACTION_READ_DESCRIPTOR) {
                        if (!gatt.readDescriptor(descriptor)) fail("bluetooth_gatt_rejected", "Android rejected the descriptor read")
                    } else writeDescriptor(gatt, descriptor, decode(request))
                }
                ACTION_OBSERVE -> {
                    val notify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val indicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (!notify && !indicate) DikcizNativeCommands.failure("bluetooth_gatt_unsupported", "This characteristic does not provide notifications or indications")
                    val descriptor = characteristic.getDescriptor(CCCD)
                        ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Client configuration descriptor not found")
                    if (!gatt.setCharacteristicNotification(characteristic, true)) fail("bluetooth_gatt_rejected", "Android rejected notification registration")
                    else writeDescriptor(gatt, descriptor, if (notify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                }
            }
        }

        private var selected: BluetoothGattCharacteristic? = null

        private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
            if (!accepted) fail("bluetooth_gatt_rejected", "Android rejected the descriptor write")
        }

        private fun read(value: ByteArray?, status: Int) = safe {
            requireStatus(status)
            if (value == null) DikcizNativeCommands.failure("bluetooth_gatt_empty_value", "Android returned no GATT value")
            if (value.size > MAXIMUM_VALUE_BYTES) DikcizNativeCommands.failure("bluetooth_gatt_value_too_large", "GATT value exceeds the result limit")
            finish(JSONObject().put("state", "read").put(KEY_DATA, Base64.getEncoder().encodeToString(value)).put("bytesReceived", value.size))
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) = read(value, status)
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = read(characteristic.value, status)
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) = read(value, status)
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = read(descriptor.value, status)

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = safe {
            requireStatus(status); finish(JSONObject().put("state", "written"))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = safe {
            requireStatus(status)
            if (request.getString(KEY_ACTION) == ACTION_OBSERVE) subscribed.set(true)
            else finish(JSONObject().put("state", "written"))
        }

        private fun changed(characteristic: BluetoothGattCharacteristic, value: ByteArray) = safe {
            val target = selected ?: return@safe
            if (request.getString(KEY_ACTION) != ACTION_OBSERVE || characteristic.uuid != target.uuid || characteristic.instanceId != target.instanceId) return@safe
            if (value.size > MAXIMUM_VALUE_BYTES) DikcizNativeCommands.failure("bluetooth_gatt_value_too_large", "GATT notification exceeds its byte limit")
            synchronized(events) {
                events.add(JSONObject().put(KEY_DATA, Base64.getEncoder().encodeToString(value)).put("receivedAtMilliseconds", System.currentTimeMillis()))
                if (events.size >= request.optInt(KEY_MAXIMUM_EVENTS, 1)) finish(observations().put("windowExpired", false))
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = changed(characteristic, value)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) = changed(characteristic, characteristic.value ?: ByteArray(0))

        private fun discovery(gatt: BluetoothGatt): JSONObject {
            val offset = request.optInt("offset", 0)
            val limit = request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT)
            var total = 0
            val rows = JSONArray()
            fun record(document: () -> JSONObject) {
                if (total >= offset && rows.length() < limit) rows.put(document())
                total++
            }
            for (service in gatt.services.sortedWith(compareBy({ it.uuid.toString() }, { it.instanceId }))) {
                if (request.has(KEY_SERVICE) && service.uuid != UUID.fromString(request.getString(KEY_SERVICE))) continue
                record { JSONObject().put("kind", "service").put(KEY_SERVICE, service.uuid.toString()).put(KEY_SERVICE_ID, service.instanceId).put("serviceType", service.type) }
                for (characteristic in service.characteristics.sortedWith(compareBy({ it.uuid.toString() }, { it.instanceId }))) {
                    fun identity() = JSONObject().put(KEY_SERVICE, service.uuid.toString()).put(KEY_SERVICE_ID, service.instanceId)
                        .put(KEY_CHARACTERISTIC, characteristic.uuid.toString()).put(KEY_CHARACTERISTIC_ID, characteristic.instanceId)
                    record { identity().put("kind", "characteristic").put("properties", characteristic.properties).put("permissions", characteristic.permissions) }
                    for (descriptor in characteristic.descriptors.sortedBy { it.uuid.toString() }) {
                        record { identity().put("kind", "descriptor").put(KEY_DESCRIPTOR, descriptor.uuid.toString()).put("permissions", descriptor.permissions) }
                    }
                }
            }
            return JSONObject().put("state", "discovered").put("items", rows).put("offset", offset).put("limit", limit)
                .put("total", total).put("hasMore", offset.toLong() + rows.length() < total)
        }
    }

    companion object {
        const val TYPE = "bluetoothGatt"
        private const val KEY_ACTION = "action"
        private const val KEY_ADDRESS = "address"
        private const val KEY_SERVICE = "serviceUuid"
        private const val KEY_SERVICE_ID = "serviceInstanceId"
        private const val KEY_CHARACTERISTIC = "characteristicUuid"
        private const val KEY_CHARACTERISTIC_ID = "characteristicInstanceId"
        private const val KEY_DESCRIPTOR = "descriptorUuid"
        private const val KEY_TIMEOUT = "timeoutMilliseconds"
        private const val KEY_DATA = "dataBase64"
        private const val KEY_WRITE_TYPE = "writeType"
        private const val KEY_MAXIMUM_EVENTS = "maximumEvents"
        private const val KEY_MTU = "mtu"
        private const val ACTION_DISCOVER = "discover"
        private const val ACTION_READ = "read"
        private const val ACTION_WRITE = "write"
        private const val ACTION_READ_DESCRIPTOR = "readDescriptor"
        private const val ACTION_WRITE_DESCRIPTOR = "writeDescriptor"
        private const val ACTION_OBSERVE = "observe"
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val MAXIMUM_VALUE_BYTES = 512
        private val busy = AtomicBoolean()
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val WRITE_TYPES = mapOf("default" to BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            "noResponse" to BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, "signed" to BluetoothGattCharacteristic.WRITE_TYPE_SIGNED)
        private val ACTIONS = setOf(ACTION_DISCOVER, ACTION_READ, ACTION_WRITE, ACTION_READ_DESCRIPTOR, ACTION_WRITE_DESCRIPTOR, ACTION_OBSERVE)
        val DEFINITIONS = listOf(DikcizNativeCommands.Definition(TYPE, "dikciz_bluetooth_gatt",
            "Connect a selected BLE peer for paginated GATT discovery, characteristic/descriptor read/write or bounded notification observation. Uses an owned connection with a deadline, then disconnects/closes. UUID ambiguity requires discovered instance IDs. Writes may have partial effects. Owner Bluetooth access and peripheral security still apply.",
            mapOf(KEY_ACTION to text("discover, read, write, readDescriptor, writeDescriptor or observe.", 32),
                KEY_ADDRESS to text("Explicit Bluetooth MAC address.", 17), KEY_SERVICE to text("Canonical service UUID.", 36),
                KEY_SERVICE_ID to integer(0,65535), KEY_CHARACTERISTIC to text("Canonical characteristic UUID.",36),
                KEY_CHARACTERISTIC_ID to integer(0,65535), KEY_DESCRIPTOR to text("Canonical descriptor UUID.",36),
                KEY_DATA to text("Base64 output, at most 512 decoded bytes.",(MAXIMUM_VALUE_BYTES+2)/3*4),
                KEY_WRITE_TYPE to text("Characteristic writes: default, noResponse or signed.",16),
                KEY_MAXIMUM_EVENTS to integer(1,100).put("default",1), KEY_MTU to integer(23,517),
                KEY_TIMEOUT to integer(1,120000).put("default",DEFAULT_TIMEOUT),
                "offset" to integer(0,DikcizNativeCommands.MAXIMUM_OFFSET).put("default",0),
                "limit" to integer(1,DikcizNativeCommands.MAXIMUM_LIMIT).put("default",DikcizNativeCommands.DEFAULT_LIMIT)),
            setOf(KEY_ACTION,KEY_ADDRESS), readOnly=false))

        fun validate(request: JSONObject) {
            DikcizBluetoothControl.validate(DikcizBluetoothControl.TYPE_PAIR, request)
            val action=request.getString(KEY_ACTION)
            if (action !in ACTIONS) DikcizNativeCommands.invalid("Unsupported GATT action")
            val fields = when (action) {
                ACTION_DISCOVER -> setOf(KEY_SERVICE,"offset","limit")
                ACTION_READ -> setOf(KEY_SERVICE,KEY_SERVICE_ID,KEY_CHARACTERISTIC,KEY_CHARACTERISTIC_ID)
                ACTION_WRITE -> setOf(KEY_SERVICE,KEY_SERVICE_ID,KEY_CHARACTERISTIC,KEY_CHARACTERISTIC_ID,KEY_DATA,KEY_WRITE_TYPE)
                ACTION_READ_DESCRIPTOR -> setOf(KEY_SERVICE,KEY_SERVICE_ID,KEY_CHARACTERISTIC,KEY_CHARACTERISTIC_ID,KEY_DESCRIPTOR)
                ACTION_WRITE_DESCRIPTOR -> setOf(KEY_SERVICE,KEY_SERVICE_ID,KEY_CHARACTERISTIC,KEY_CHARACTERISTIC_ID,KEY_DESCRIPTOR,KEY_DATA)
                else -> setOf(KEY_SERVICE,KEY_SERVICE_ID,KEY_CHARACTERISTIC,KEY_CHARACTERISTIC_ID,KEY_MAXIMUM_EVENTS)
            }
            val optional=setOf(KEY_SERVICE,KEY_SERVICE_ID,KEY_CHARACTERISTIC,KEY_CHARACTERISTIC_ID,KEY_DESCRIPTOR,KEY_DATA,KEY_WRITE_TYPE,KEY_MAXIMUM_EVENTS,"offset","limit")
            if (optional.any { request.has(it) && it !in fields }) DikcizNativeCommands.invalid("GATT fields do not match the selected action")
            if (action != ACTION_DISCOVER && (!request.has(KEY_SERVICE) || !request.has(KEY_CHARACTERISTIC))) DikcizNativeCommands.invalid("Select service and characteristic UUIDs")
            if (action in setOf(ACTION_READ_DESCRIPTOR,ACTION_WRITE_DESCRIPTOR) && !request.has(KEY_DESCRIPTOR)) DikcizNativeCommands.invalid("Descriptor UUID is required")
            for (key in listOf(KEY_SERVICE,KEY_CHARACTERISTIC,KEY_DESCRIPTOR)) {
                if (!request.has(key)) continue
                try {
                    if (UUID.fromString(request.getString(key)).toString() != request.getString(key).lowercase(java.util.Locale.ROOT)) DikcizNativeCommands.invalid("Invalid GATT UUID")
                } catch (exception: IllegalArgumentException) {
                    if (exception is DikcizAutomationRequestException) throw exception
                    throw DikcizAutomationRequestException("validation_failed","Invalid GATT UUID").apply { initCause(exception) }
                }
            }
            if (action in setOf(ACTION_WRITE,ACTION_WRITE_DESCRIPTOR)) {
                if (!request.has(KEY_DATA)) DikcizNativeCommands.invalid("Write data is required")
                decode(request)
            }
            if (request.has(KEY_WRITE_TYPE) && request.getString(KEY_WRITE_TYPE) !in WRITE_TYPES) DikcizNativeCommands.invalid("Unsupported GATT write type")
        }

        private fun decode(request: JSONObject): ByteArray {
            val bytes=try { Base64.getDecoder().decode(request.getString(KEY_DATA)) } catch (exception: IllegalArgumentException) {
                throw DikcizAutomationRequestException("validation_failed","Invalid GATT base64 data").apply { initCause(exception) }
            }
            if (bytes.size>MAXIMUM_VALUE_BYTES) DikcizNativeCommands.invalid("GATT attribute exceeds 512 bytes")
            return bytes
        }
        private fun text(description:String,maximum:Int)=JSONObject().put("type","string").put("description",description).put("maxLength",maximum)
        private fun integer(minimum:Int,maximum:Int)=JSONObject().put("type","integer").put("minimum",minimum).put("maximum",maximum)
    }
}
