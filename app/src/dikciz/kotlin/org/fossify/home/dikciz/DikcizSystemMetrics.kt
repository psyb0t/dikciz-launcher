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
    private var previousCpu: Map<String, DikcizCpuCounters> = emptyMap()
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
        val sampleWindow = previous?.let { now - it.first }
        if (previous == null || (sampleWindow ?: 0) >= MINIMUM_SAMPLE_MILLISECONDS) {
            previousAppCpu = now to appCpu
        }
        val appPercent = previous?.takeIf {
            (sampleWindow ?: 0) >= MINIMUM_SAMPLE_MILLISECONDS && appCpu >= it.second
        }?.let {
            (appCpu - it.second).toDouble() * PERCENT / (now - it.first)
        }
        val logicalProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
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
                .put("logicalProcessors", logicalProcessors)
                .put("appPercentOfCapacity", appPercent?.let { (it / logicalProcessors).coerceIn(0.0, PERCENT) } ?: JSONObject.NULL)
                .put("appSampleWindowMilliseconds", if (appPercent == null) JSONObject.NULL else sampleWindow)
                .put("appScope", "dikciz_process")
                .put("permissionRequestAvailable", false)
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
        var reason = "warming_up"
        val counters = try {
            File(CPU_STAT_PATH).bufferedReader().useLines { lines ->
                lines.take(MAXIMUM_CORES + 1).mapNotNull { line ->
                    val value = DikcizCpuCounters.parse(line) ?: return@mapNotNull null
                    line.trim().takeWhile { !it.isWhitespace() } to value
                }.toMap()
            }
        } catch (_: IOException) {
            reason = "android_restricted"
            emptyMap()
        } catch (_: SecurityException) {
            reason = "android_restricted"
            emptyMap()
        }
        val aggregate = counters[CPU_AGGREGATE]
        if (aggregate == null && reason != "android_restricted") reason = "invalid_cpu_counters"
        val percent = aggregate?.percentSince(previousCpu[CPU_AGGREGATE])
        val directories = File(CPU_DIRECTORY).listFiles { file -> CPU_DIRECTORY_NAME.matches(file.name) }
            ?.sortedBy { it.name.removePrefix(CPU_AGGREGATE).toInt() }.orEmpty()
        val identifiers = if (directories.isEmpty()) {
            (0 until Runtime.getRuntime().availableProcessors().coerceIn(1, MAXIMUM_CORES)).toList()
        } else {
            directories.take(MAXIMUM_CORES).map { it.name.removePrefix(CPU_AGGREGATE).toInt() }
        }
        val cores = JSONArray()
        identifiers.forEach { id ->
            val key = "$CPU_AGGREGATE$id"
            val frequency = cpuNumber(id, "cpufreq/scaling_cur_freq")
                ?: cpuNumber(id, "cpufreq/cpuinfo_cur_freq")
            val maximum = cpuNumber(id, "cpufreq/cpuinfo_max_freq")
            val online = cpuNumber(id, "online")?.takeIf { it == 0L || it == 1L }
            val load = counters[key]?.percentSince(previousCpu[key])
            cores.put(JSONObject()
                .put("id", id)
                .put("online", online?.let { it == 1L } ?: JSONObject.NULL)
                .put("frequencyKilohertz", frequency?.takeIf { it > 0 } ?: JSONObject.NULL)
                .put("maximumFrequencyKilohertz", maximum?.takeIf { it > 0 } ?: JSONObject.NULL)
                .put("usagePercent", load ?: JSONObject.NULL)
                .put("usageAvailable", load != null))
        }
        previousCpu = counters
        return JSONObject()
            .put("systemAvailable", percent != null)
            .put("systemPercent", percent ?: JSONObject.NULL)
            .put("reason", if (percent == null) reason else JSONObject.NULL)
            .put("cores", cores)
            .put("coresTruncated", directories.size > MAXIMUM_CORES)
            .put("coreInventorySource", if (directories.isEmpty()) "runtime_available_processors" else "sysfs")
    }

    private fun cpuNumber(id: Int, suffix: String): Long? {
        return try {
            File("$CPU_DIRECTORY/cpu$id/$suffix").bufferedReader().use { reader ->
                val buffer = CharArray(MAXIMUM_CPU_VALUE_CHARACTERS)
                val count = reader.read(buffer)
                if (count <= 0) null else String(buffer, 0, count).trim().toLongOrNull()?.takeIf { it >= 0 }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private companion object {
        const val CPU_STAT_PATH = "/proc/stat"
        const val CPU_DIRECTORY = "/sys/devices/system/cpu"
        const val CPU_AGGREGATE = "cpu"
        const val MAXIMUM_CORES = 64
        const val MAXIMUM_CPU_VALUE_CHARACTERS = 64
        const val MINIMUM_SAMPLE_MILLISECONDS = 250L
        val CPU_DIRECTORY_NAME = Regex("cpu[0-9]{1,3}")
        const val MAXIMUM_SENSORS = 64
        const val MAXIMUM_SENSOR_TEXT = 128
        const val PERCENT = 100.0
    }
}
