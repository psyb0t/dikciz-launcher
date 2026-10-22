package org.fossify.home.dikciz

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizSystemMetrics(private val context: Context) {
    private var previousCpu: Pair<Long, Long>? = null
    private var previousAppCpu: Pair<Long, Long>? = null

    @Synchronized
    fun read(): JSONObject {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val storage = StatFs(context.filesDir.absolutePath)
        val sensors = context.getSystemService(SensorManager::class.java)
            .getSensorList(Sensor.TYPE_ALL)
        val now = SystemClock.elapsedRealtime()
        val appCpu = Process.getElapsedCpuTime()
        val previous = previousAppCpu
        previousAppCpu = now to appCpu
        val appPercent = previous?.takeIf { now > it.first && appCpu >= it.second }?.let {
            (appCpu - it.second).toDouble() * PERCENT / (now - it.first)
        }
        return JSONObject()
            .put("sampledAtMilliseconds", System.currentTimeMillis())
            .put("uptimeMilliseconds", now)
            .put("memory", JSONObject()
                .put("totalBytes", memory.totalMem)
                .put("availableBytes", memory.availMem)
                .put("usedBytes", memory.totalMem - memory.availMem)
                .put("lowMemory", memory.lowMemory))
            .put("storage", JSONObject()
                .put("scope", "app_data_filesystem")
                .put("totalBytes", storage.totalBytes)
                .put("availableBytes", storage.availableBytes)
                .put("freeBytes", storage.freeBytes))
            .put("cpu", systemCpu()
                .put("logicalProcessors", Runtime.getRuntime().availableProcessors())
                .put("appPercentOfOneCore", appPercent ?: JSONObject.NULL)
                .put("appSampleAvailable", appPercent != null))
            .put("sensors", JSONObject()
                .put("count", sensors.size)
                .put("truncated", sensors.size > MAXIMUM_SENSORS)
                .put("readingsAvailable", false)
                .put("reason", "inventory_only")
                .put("items", JSONArray().apply {
                    sensors.take(MAXIMUM_SENSORS).forEach { sensor ->
                        put(JSONObject()
                            .put("type", sensor.type)
                            .put("name", sensor.name.take(MAXIMUM_SENSOR_TEXT))
                            .put("vendor", sensor.vendor.take(MAXIMUM_SENSOR_TEXT))
                            .put("wakeUp", sensor.isWakeUpSensor))
                    }
                }))
    }

    private fun systemCpu(): JSONObject {
        val unavailable = JSONObject().put("systemAvailable", false)
            .put("systemPercent", JSONObject.NULL)
        val counters = try {
            File(CPU_STAT_PATH).bufferedReader().use { it.readLine() }
                ?.trim()?.split(WHITESPACE)?.drop(1)?.take(CPU_COUNTER_COUNT)
                ?.map { it.toLongOrNull() ?: return unavailable.put("reason", "invalid_cpu_counters") }
        } catch (_: IOException) {
            return unavailable.put("reason", "android_restricted")
        } catch (_: SecurityException) {
            return unavailable.put("reason", "android_restricted")
        }
        if (counters == null || counters.size < CPU_COUNTER_COUNT || counters.any { it < 0 }) {
            return unavailable.put("reason", "invalid_cpu_counters")
        }
        val total = counters.sum()
        val idle = counters[CPU_IDLE_INDEX] + counters[CPU_IOWAIT_INDEX]
        val previous = previousCpu
        previousCpu = total to idle
        if (previous == null) return unavailable.put("reason", "warming_up")
        val totalDelta = total - previous.first
        val idleDelta = idle - previous.second
        if (totalDelta <= 0 || idleDelta !in 0..totalDelta) {
            return unavailable.put("reason", "warming_up")
        }
        return JSONObject().put("systemAvailable", true)
            .put("systemPercent", (totalDelta - idleDelta).toDouble() * PERCENT / totalDelta)
    }

    private companion object {
        const val CPU_STAT_PATH = "/proc/stat"
        const val CPU_COUNTER_COUNT = 8
        const val CPU_IDLE_INDEX = 3
        const val CPU_IOWAIT_INDEX = 4
        const val MAXIMUM_SENSORS = 64
        const val MAXIMUM_SENSOR_TEXT = 128
        const val PERCENT = 100.0
        val WHITESPACE = Regex("\\s+")
    }
}
