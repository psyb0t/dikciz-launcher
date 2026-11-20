package org.fossify.home.dikciz

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizCustomAppMotion {
    const val COMMAND = "customAppMotion"
    const val HIT = "app.motion.hit"
    const val STOPPED = "app.motion.stopped"
    private const val PERIOD_US = 5_000
    private const val WINDOW_NS = 2_000_000_000L
    private const val CAPACITY = 400
    private const val NS_PER_MS = 1_000_000L
    private const val MAX_DURATION_MS = 7_200_000L
    private const val CHECK_MS = 1_000L
    private const val G = 9.80665
    private val delivery = Executors.newSingleThreadExecutor()
    private var session: Session? = null

    @Synchronized
    fun command(context: Context, appID: String, request: JSONObject): JSONObject {
        val action = request.optString("action")
        if (action == "status") return status(appID)
        if (action == "stop") {
            stop(appID)
            return JSONObject().put("active", false)
        }
        if (action != "start") DikcizNativeCommands.invalid("Expected start, stop or status")
        val id = request.opt("monitorId") as? String
        val threshold = (request.opt("thresholdG") as? Number)?.toDouble()
        if (id == null || !DikcizAutomationEventNames.isValidCustomName("motion.$id") ||
            threshold == null || !threshold.isFinite() || threshold !in 1.0..12.0
        ) DikcizNativeCommands.invalid("A monitor ID and a threshold from 1 to 12 g are required")
        val duration = DikcizNativeCommands.strictInteger(request.opt("durationMilliseconds"), CHECK_MS, MAX_DURATION_MS)
        if (session != null) DikcizNativeCommands.failure("motion_monitor_busy", "A motion monitor is already active")
        val application = context.applicationContext as Application
        val manager = application.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, false)
            ?: DikcizNativeCommands.failure("motion_sensor_unavailable", "Linear acceleration sensor unavailable")
        val power = application.getSystemService(PowerManager::class.java)
        val lock = if (sensor.isWakeUpSensor) null else {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dikciz:app-motion").apply { setReferenceCounted(false) }
        }
        val monitor = Session(application, appID, id, threshold * G, duration, manager, sensor, lock)
        session = monitor
        try {
            monitor.start()
            return monitor.status()
        } catch (exception: Exception) {
            stop(appID)
            throw exception
        }
    }

    @Synchronized
    fun stop(appID: String? = null, monitorID: String? = null) {
        val current = session ?: return
        if (appID != null && current.appID != appID || monitorID != null && current.id != monitorID) return
        session = null
        current.close()
    }

    @Synchronized
    fun status(appID: String): JSONObject = session?.takeIf { it.appID == appID }?.status()
        ?: JSONObject().put("active", false)

    @Synchronized
    fun owner(): String? = session?.appID

    private data class Sample(val timestamp: Long, val x: Float, val y: Float, val z: Float) {
        val magnitudeSquared = x.toDouble() * x + y.toDouble() * y + z.toDouble() * z
    }

    private class Session(
        private val application: Application,
        val appID: String,
        val id: String,
        threshold: Double,
        private val duration: Long,
        private val manager: SensorManager,
        private val sensor: Sensor,
        private val wakeLock: PowerManager.WakeLock?,
    ) : SensorEventListener {
        private val thresholdSquared = threshold * threshold
        private val thread = HandlerThread("dikciz-app-motion").apply { start() }
        private val handler = Handler(thread.looper)
        private val first = CountDownLatch(1)
        private val closed = AtomicBoolean()
        private val triggered = AtomicBoolean()
        private val samples = arrayOfNulls<Sample>(CAPACITY)
        private var index = 0
        @Volatile private var latestAt = 0L
        private val startedAt = SystemClock.elapsedRealtimeNanos()
        private val deadline = SystemClock.elapsedRealtime() + duration
        private val watch = object : Runnable {
            override fun run() {
                if (closed.get() || triggered.get()) return
                val now = SystemClock.elapsedRealtime()
                when {
                    now >= deadline -> notifyStop("expired")
                    SystemClock.elapsedRealtimeNanos() - latestAt > WINDOW_NS -> notifyStop("sensor_feed_stalled")
                    else -> handler.postDelayed(this, CHECK_MS)
                }
            }
        }

        @android.annotation.SuppressLint("WakelockTimeout")
        fun start() {
            wakeLock?.acquire(duration)
            if (!manager.registerListener(this, sensor, PERIOD_US, 0, handler) ||
                !first.await(CHECK_MS, TimeUnit.MILLISECONDS)
            ) DikcizNativeCommands.failure("motion_feed_unavailable", "No live accelerometer samples arrived")
            handler.postDelayed(watch, CHECK_MS)
        }

        @Synchronized
        fun status(): JSONObject {
            val now = SystemClock.elapsedRealtimeNanos()
            val recent = samples.filterNotNull().filter { now - it.timestamp in 0..WINDOW_NS }
            return JSONObject().put("active", !closed.get() && !triggered.get())
                .put("monitorId", id).put("samples", recent.size).put("samplingPeriodMicroseconds", PERIOD_US)
                .put("windowMilliseconds", WINDOW_NS / NS_PER_MS)
                .put("peakG", recent.maxOfOrNull { kotlin.math.sqrt(it.magnitudeSquared) / G } ?: JSONObject.NULL)
                .put("observedSampleRateHz", if (recent.size > 1) {
                    (recent.size - 1) * 1_000_000_000.0 / (recent.maxOf { it.timestamp } - recent.minOf { it.timestamp })
                } else JSONObject.NULL)
                .put("latestSampleAgeMilliseconds", ((now - latestAt) / NS_PER_MS).takeIf { latestAt != 0L } ?: JSONObject.NULL)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent) {
            if (closed.get() || triggered.get() || event.values.size < 3) return
            val sample = Sample(event.timestamp, event.values[0], event.values[1], event.values[2])
            if (!sample.magnitudeSquared.isFinite() || sample.timestamp < startedAt || sample.timestamp <= latestAt) return
            val peak = synchronized(this) {
                samples[index] = sample
                index = (index + 1) % CAPACITY
                latestAt = sample.timestamp
                samples.asSequence().filterNotNull().filter { sample.timestamp - it.timestamp in 0..WINDOW_NS }
                    .maxByOrNull(Sample::magnitudeSquared)
            }
            first.countDown()
            if (peak != null && peak.magnitudeSquared >= thresholdSquared) {
                val age = (SystemClock.elapsedRealtimeNanos() - peak.timestamp).coerceAtLeast(0) / NS_PER_MS
                val payload = JSONObject().put("values", JSONArray(listOf(peak.x, peak.y, peak.z)))
                    .put("sampledAtMilliseconds", System.currentTimeMillis() - age).put("deliveryAgeMilliseconds", age)
                deliver(HIT, payload)
            }
        }

        private fun notifyStop(reason: String) = deliver(STOPPED, JSONObject().put("reason", reason))

        private fun deliver(event: String, payload: JSONObject) {
            if (!triggered.compareAndSet(false, true)) return
            payload.put("monitorId", id)
            delivery.execute {
                try {
                    if (!closed.get()) DikcizCustomApps.deliverMotion(application, appID, event, payload)
                } finally {
                    stop(appID, id)
                }
            }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            manager.unregisterListener(this)
            if (wakeLock?.isHeld == true) wakeLock.release()
            thread.quitSafely()
        }
    }
}
