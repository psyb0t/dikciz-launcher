package org.fossify.home.dikciz

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizSensorReadings(context: Context) {
    private val context: Context = context
    fun read(request: JSONObject): JSONObject {
        val manager = context.getSystemService(SensorManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Sensor service unavailable")
        val inventory = (manager.getSensorList(Sensor.TYPE_ALL) + manager.getDynamicSensorList(Sensor.TYPE_ALL)).distinct()
        val selectedID = request.optInt("sensorId", -1)
        if (selectedID >= inventory.size) {
            DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Sensor ID is not in this device inventory")
        }
        val selected = inventory.withIndex().filter { selectedID < 0 || it.index == selectedID }
        val offset = request.optInt(DikcizNativeCommands.KEY_OFFSET, 0)
        val limit = request.optInt(DikcizNativeCommands.KEY_LIMIT, DikcizNativeCommands.DEFAULT_LIMIT)
        val page = selected.drop(offset).take(limit)
        val records = page.associate { (index, sensor) -> sensor to metadata(index, sensor) }
        val lock = Any()
        var acceptingSamples = true
        val completion = CountDownLatch(page.size)
        val delivered = mutableSetOf<Sensor>()
        val thread = HandlerThread("dikciz-sensor-snapshot").apply { start() }
        val handler = Handler(thread.looper)
        val triggers = mutableMapOf<Sensor, TriggerEventListener>()
        fun sample(sensor: Sensor, timestamp: Long, values: FloatArray, accuracy: Int?) {
            synchronized(lock) {
                if (!acceptingSamples) return
                val record = records[sensor] ?: return
                record.put("available", true).put("reason", JSONObject.NULL)
                    .put("values", JSONArray(values.map { if (it.isFinite()) it.toDouble() else JSONObject.NULL }))
                    .put("accuracy", accuracy ?: JSONObject.NULL)
                    .put("timestampNanoseconds", timestamp)
                    .put("ageMilliseconds", maxOf(0L, SystemClock.elapsedRealtimeNanos() - timestamp) / NANOS_PER_MILLISECOND)
                if (delivered.add(sensor)) completion.countDown()
            }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = sample(event.sensor, event.timestamp, event.values, event.accuracy)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        try {
            for ((_, sensor) in page) {
                val record = records.getValue(sensor)
                val permitted = when (sensor.type) {
                    Sensor.TYPE_HEART_RATE -> DikcizAutomationSensorPermission.hasHeartRatePermission(context)
                    else -> DikcizAutomationSensorPermission.hasPermission(context, sensor.type)
                }
                if (!permitted) {
                    if (selectedID >= 0) {
                        val requirement = if (sensor.type == Sensor.TYPE_HEART_RATE) "bodySensors" else "activityRecognition"
                        DikcizNativeCommands.failure("native_permission_$requirement", "Android permission is required for this sensor")
                    }
                    record.put("reason", "permission_required").put("requirementId",
                        if (sensor.type == Sensor.TYPE_HEART_RATE) "bodySensors" else "activityRecognition")
                    completion.countDown()
                    continue
                }
                try {
                    val registered = if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
                        val trigger = object : TriggerEventListener() {
                            override fun onTrigger(event: TriggerEvent) = sample(event.sensor, event.timestamp, event.values, null)
                        }
                        triggers[sensor] = trigger
                        manager.requestTriggerSensor(trigger, sensor)
                    } else {
                        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
                    }
                    if (!registered) {
                        record.put("reason", "registration_rejected")
                        completion.countDown()
                    }
                } catch (exception: SecurityException) {
                    record.put("reason", "android_access_denied")
                    completion.countDown()
                }
            }
            completion.await(request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)
            synchronized(lock) {
                acceptingSamples = false
                return JSONObject().put("items", JSONArray(page.map { records.getValue(it.value) }))
                    .put("offset", offset).put("limit", limit).put("total", selected.size)
                    .put("hasMore", offset.toLong() + page.size < selected.size)
                    .put("sampledAtMilliseconds", System.currentTimeMillis())
            }
        } finally {
            synchronized(lock) { acceptingSamples = false }
            manager.unregisterListener(listener)
            triggers.forEach { (sensor, trigger) -> manager.cancelTriggerSensor(trigger, sensor) }
            thread.quitSafely()
        }
    }

    private fun metadata(index: Int, sensor: Sensor): JSONObject = JSONObject()
        .put("sensorId", index).put("type", sensor.type).put("stringType", sensor.stringType)
        .put("name", sensor.name.take(MAXIMUM_TEXT)).put("vendor", sensor.vendor.take(MAXIMUM_TEXT))
        .put("version", sensor.version).put("reportingMode", sensor.reportingMode)
        .put("dynamic", sensor.isDynamicSensor).put("wakeUp", sensor.isWakeUpSensor).put("powerMilliamp", sensor.power.toDouble())
        .put("resolution", sensor.resolution.toDouble()).put("maximumRange", sensor.maximumRange.toDouble())
        .put("minimumDelayMicroseconds", sensor.minDelay).put("maximumDelayMicroseconds", sensor.maxDelay)
        .put("available", false).put("values", JSONArray()).put("accuracy", JSONObject.NULL)
        .put("timestampNanoseconds", JSONObject.NULL).put("ageMilliseconds", JSONObject.NULL)
        .put("reason", if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) "waiting_for_trigger" else "no_sample_in_window")

    companion object {
        private const val DEFAULT_TIMEOUT = 1000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val MAXIMUM_TEXT = 128
    }
}
