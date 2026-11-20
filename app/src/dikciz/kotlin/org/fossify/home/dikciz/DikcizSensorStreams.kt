package org.fossify.home.dikciz

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizSensorStreams {
    const val START = "sensorStreamStart"
    const val STATUS = "sensorStreamStatus"
    const val READ = "sensorStreamRead"
    const val STOP = "sensorStreamStop"
    const val SAMPLES_EVENT = "app.sensor.samples"
    const val STOPPED_EVENT = "app.sensor.stopped"
    val TYPES = setOf(START, STATUS, READ, STOP)
    val APP_EVENTS = setOf(SAMPLES_EVENT, STOPPED_EVENT)
    const val SOCKET_SAMPLES_EVENT = "sensorStreamSamples"
    const val SOCKET_STOPPED_EVENT = "sensorStreamStopped"

    const val DEFAULT_PERIOD_US = 20_000
    const val MAX_PERIOD_US = 10_000_000
    const val DEFAULT_HISTORY_MS = 2_000
    const val MAX_HISTORY_MS = 60_000
    const val DEFAULT_CAPACITY = 400
    const val MAX_CAPACITY = 4_096
    const val MIN_DURATION_MS = 1_000
    const val DEFAULT_DURATION_MS = 60_000
    const val MAX_DURATION_MS = 7_200_000
    const val MIN_DELIVERY_MS = 50
    const val MAX_DELIVERY_MS = 5_000
    const val MAX_BATCH_MS = 10_000
    const val DEFAULT_PAGE = 64
    const val MAX_PAGE = 128

    private const val MAX_VALUES = 64
    private const val MAX_ACTIVE = 8
    private const val MAX_PER_OWNER = 4
    private const val MAX_COMPLETED = 8
    private const val COMPLETED_MS = 30_000L
    private const val CLOCK_NS_PER_MS = 1_000_000L
    private const val CLOCK_NS_PER_SECOND = 1_000_000_000.0
    private const val CHECK_MS = 250L
    private const val MIN_STALL_MS = 2_000L
    private const val FOREGROUND_START_GRACE_MS = 5_000L
    private const val OWNER_LOCAL = "local-owner"
    private const val APP_PREFIX = "app:"
    private const val ACTIVE = "active"
    private const val STOPPED = "stopped"

    private val guard = Any()
    private val active = linkedMapOf<String, Session>()
    private val completed = linkedMapOf<String, Session>()
    private val handler by lazy {
        Handler(HandlerThread("dikciz-sensor-capture").apply { start() }.looper)
    }
    private val lifecycle by lazy {
        Handler(HandlerThread("dikciz-sensor-lifecycle").apply { start() }.looper)
    }
    private val deliveries = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(MAX_ACTIVE + MAX_COMPLETED),
    )
    private val prune = object : Runnable {
        override fun run() = synchronized(guard) {
            pruneCompleted()
            if (completed.isNotEmpty()) lifecycle.postDelayed(this, COMPLETED_MS)
            Unit
        }
    }
    private val sockets = java.util.concurrent.CopyOnWriteArraySet<DikcizAutomationControlPlane>()

    fun attachSocket(socket: DikcizAutomationControlPlane) { sockets.add(socket) }
    fun detachSocket(socket: DikcizAutomationControlPlane) { sockets.remove(socket) }

    fun inventory(manager: SensorManager): List<Sensor> =
        (manager.getSensorList(Sensor.TYPE_ALL) + manager.getDynamicSensorList(Sensor.TYPE_ALL)).distinct()

    fun execute(context: Context, command: DikcizAutomationCommand): JSONObject {
        val actor = DikcizOperations.currentActor() ?: OWNER_LOCAL
        val owner = command.access?.let { "remote:" + it.id } ?: actor
        return when (command.type) {
            START -> start(context.applicationContext as Application, command, owner)
            STATUS -> find(command.request, owner).status()
            READ -> find(command.request, owner).read(
                command.request.optLong("afterSequence", 0),
                command.request.optInt("limit", DEFAULT_PAGE),
            )
            STOP -> find(command.request, owner).also { finish(it, STOPPED, notify = true) }.status()
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun find(request: JSONObject, owner: String): Session {
        val id = request.getString("sessionId")
        return synchronized(guard) {
            pruneCompleted()
            (active[id] ?: completed[id])?.takeIf { it.owner == owner }
                ?: DikcizNativeCommands.failure("sensor_session_not_found", "No sensor session with that ID belongs to this caller.")
        }
    }

    private fun start(application: Application, command: DikcizAutomationCommand, owner: String): JSONObject {
        val request = command.request
        val manager = application.getSystemService(SensorManager::class.java)
        val sensorId = request.getInt("sensorId")
        val sensor = inventory(manager).getOrNull(sensorId)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Sensor ID is not in this device inventory.")
        val background = request.optBoolean("background")
        if (!background && DikcizForegroundActivity.current(application) == null) {
            DikcizNativeCommands.failure("sensor_foreground_required", "Open Dikciz or explicitly request background capture.")
        }
        val interval = request.optInt("deliveryIntervalMilliseconds", 0)
        val appId = owner.takeIf { it.startsWith(APP_PREFIX) }?.removePrefix(APP_PREFIX)
        if (interval != 0 && appId == null && command.access == null) {
            DikcizNativeCommands.invalid("Event delivery requires a custom app or authenticated remote caller.")
        }
        if (interval != 0) command.access?.requireCommand(READ)
        val batch = request.optInt("batchLatencyMilliseconds", 0)
        if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT && batch != 0) {
            DikcizNativeCommands.invalid("One-shot sensors do not support batching.")
        }
        if (!permitted(application, sensor, background)) {
            val requirement = if (sensor.type == Sensor.TYPE_HEART_RATE) "bodySensors" else "activityRecognition"
            DikcizNativeCommands.failure("native_permission_$requirement", "Android access is required for this sensor.")
        }

        val session = Session(
            application, manager, sensor, sensorId, owner, command.access, appId,
            request.optInt("samplingPeriodMicroseconds", DEFAULT_PERIOD_US),
            batch, request.optInt("historyMilliseconds", DEFAULT_HISTORY_MS),
            request.optInt("capacity", DEFAULT_CAPACITY), request.optInt("durationMilliseconds", DEFAULT_DURATION_MS),
            background, interval,
        )
        synchronized(guard) {
            if (active.size >= MAX_ACTIVE || active.values.count { it.owner == owner } >= MAX_PER_OWNER) {
                DikcizNativeCommands.failure("sensor_session_capacity", "Stop an existing sensor session before starting another.")
            }
            active[session.id] = session
        }
        try {
            if (!session.register()) {
                DikcizNativeCommands.failure("sensor_registration_rejected", "Android rejected the sensor registration.")
            }
            if (background) DikcizCustomAppService.start(application)
            lifecycle.post(session.watch)
        } catch (_: SecurityException) {
            finish(session, "access_denied", notify = false)
            DikcizNativeCommands.failure("sensor_access_denied", "Android denied the requested sensor mode or rate.")
        } catch (exception: RuntimeException) {
            finish(session, "start_failed", notify = false)
            if (exception is DikcizAutomationRequestException) throw exception
            DikcizNativeCommands.failure("sensor_start_failed", "Android could not start sensor capture.")
        }
        return session.status()
    }

    fun usesBackgroundService(): Boolean = synchronized(guard) { active.values.any(Session::background) }

    fun stopBackground(reason: String) {
        matching { it.background }.forEach { finish(it, reason) }
    }

    fun stopOwner(owner: String) {
        matching { it.owner == owner }.forEach { finish(it, STOPPED, notify = false) }
    }

    fun stopMissingOwners(prefix: String, retainedIds: Set<String>) {
        matching { it.owner.startsWith(prefix) && it.owner.removePrefix(prefix) !in retainedIds }
            .forEach { finish(it, STOPPED, notify = false) }
    }

    fun appIsActive(appId: String): Boolean =
        synchronized(guard) { active.values.any { it.appId == appId } }

    private fun matching(filter: (Session) -> Boolean): List<Session> =
        synchronized(guard) { active.values.filter(filter) }

    private fun pruneCompleted() {
        val cutoff = SystemClock.elapsedRealtime() - COMPLETED_MS
        completed.values.removeAll { it.endedAt < cutoff }
        while (completed.size > MAX_COMPLETED) completed.remove(completed.keys.first())
    }

    private fun finish(session: Session, reason: String, notify: Boolean = reason != STOPPED) {
        if (!session.finish(reason, notify)) return
        synchronized(guard) {
            active.remove(session.id)
            val schedulePrune = completed.isEmpty()
            completed[session.id] = session
            pruneCompleted()
            if (schedulePrune) lifecycle.postDelayed(prune, COMPLETED_MS + CHECK_MS)
        }
        session.unregister()
        session.deliver()
    }

    private fun permitted(context: Context, sensor: Sensor, background: Boolean): Boolean {
        if (sensor.type == Sensor.TYPE_HEART_RATE) {
            return DikcizAutomationSensorPermission.hasHeartRatePermission(context) &&
                (!background || DikcizAutomationSensorPermission.hasHeartRateBackgroundPermission(context))
        }
        return DikcizAutomationSensorPermission.hasPermission(context, sensor.type)
    }

    private data class Sample(val sequence: Long, val timestamp: Long, val values: FloatArray, val accuracy: Int?) {
        fun json(): JSONObject = JSONObject().put("sequence", sequence).put("timestampNanoseconds", timestamp.toString())
            .put("accuracy", accuracy ?: JSONObject.NULL).put("values", JSONArray().apply {
                values.forEach { put(if (it.isFinite()) it.toDouble() else JSONObject.NULL) }
            })
    }

    private class Session(
        private val application: Application,
        private val manager: SensorManager,
        private val sensor: Sensor,
        private val sensorId: Int,
        val owner: String,
        private val access: DikcizRemoteAccess?,
        val appId: String?,
        private val period: Int,
        private val batch: Int,
        private val history: Int,
        private val capacity: Int,
        duration: Int,
        val background: Boolean,
        private val interval: Int,
    ) {
        val id = UUID.randomUUID().toString()
        private val window = ArrayDeque<Sample>()
        private val started = SystemClock.elapsedRealtime()
        private val deadline = started + duration
        private val deliveryPending = AtomicBoolean(false)
        private var wakeLock: PowerManager.WakeLock? = null
        private var received = 0L
        private var unrecorded = 0L
        private var lastArrival = started
        private var lastTimestamp = 0L
        private var phase = ACTIVE
        private var terminalPending = false
        @Volatile private var delivered = 0L
        @Volatile private var deliveredDiscarded = 0L
        private var lastDelivery = started
        @Volatile var endedAt = 0L
            private set

        private val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = sample(event.timestamp, event.values, event.accuracy)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        private val trigger = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent) {
                sample(event.timestamp, event.values, null)
                lifecycle.post { DikcizSensorStreams.finish(this@Session, "triggered") }
            }
        }

        val watch = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                val reason = failure(now)
                if (reason != null) {
                    DikcizSensorStreams.finish(this@Session, reason)
                    return
                }
                deliver()
                if (synchronized(this@Session) { phase == ACTIVE }) {
                    val check = if (interval > 0) minOf(CHECK_MS, interval.toLong()) else CHECK_MS
                    lifecycle.postDelayed(this, minOf(check, (deadline - now).coerceAtLeast(1)))
                }
            }
        }

        private fun failure(now: Long): String? {
            val last = synchronized(this) {
                if (phase != ACTIVE) return phase
                lastArrival
            }
            return when {
                now >= deadline -> "expired"
                access?.isCurrent() == false -> "access_revoked"
                !permitted(application, sensor, background) -> "access_revoked"
                sensor.isDynamicSensor && sensor !in manager.getDynamicSensorList(sensor.type) -> "sensor_removed"
                background && now - started >= FOREGROUND_START_GRACE_MS && !DikcizCustomAppService.running -> "foreground_unavailable"
                !background && DikcizForegroundActivity.current(application) == null -> "foreground_lost"
                sensor.reportingMode == Sensor.REPORTING_MODE_CONTINUOUS &&
                    now - last > maxOf(MIN_STALL_MS, batch + 4L * period / 1_000) -> "sensor_feed_stalled"
                else -> null
            }
        }

        @android.annotation.SuppressLint("WakelockTimeout")
        @Synchronized
        fun register(): Boolean {
            if (phase != ACTIVE) return false
            if (background && !sensor.isWakeUpSensor) {
                wakeLock = application.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dikciz:sensor-session")
                    .apply { setReferenceCounted(false); acquire((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
            }
            return if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
                manager.requestTriggerSensor(trigger, sensor)
            } else {
                manager.registerListener(listener, sensor, period, batch * 1_000, handler)
            }
        }

        @Synchronized
        private fun sample(timestamp: Long, values: FloatArray, accuracy: Int?) {
            val now = SystemClock.elapsedRealtime()
            if (phase != ACTIVE || now >= deadline) return
            lastArrival = now
            lastTimestamp = timestamp
            if (values.size > MAX_VALUES) {
                unrecorded++
                return
            }
            pruneSamples()
            if (window.size == capacity) window.removeFirst()
            window.addLast(Sample(++received, timestamp, values.copyOf(), accuracy))
        }

        private fun pruneSamples() {
            val cutoff = SystemClock.elapsedRealtimeNanos() - history * CLOCK_NS_PER_MS
            while (window.firstOrNull()?.let { it.timestamp < cutoff } == true) window.removeFirst()
        }

        @Synchronized
        fun status(): JSONObject {
            pruneSamples()
            val first = window.firstOrNull()
            val last = window.lastOrNull()
            val span = if (first != null && last != null) last.timestamp - first.timestamp else 0L
            return JSONObject().put("sessionId", id).put("state", phase)
                .put("sensorId", sensorId).put("sensorType", sensor.type).put("reportingMode", sensor.reportingMode)
                .put("requestedSamplingPeriodMicroseconds", period).put("batchLatencyMilliseconds", batch)
                .put("historyMilliseconds", history).put("capacity", capacity).put("background", background)
                .put("deadlineElapsedMilliseconds", deadline)
                .put("observedSampleRateHz", if (span > 0) (window.size - 1) * CLOCK_NS_PER_SECOND / span else JSONObject.NULL)
                .put("latestSampleAgeMilliseconds", if (lastTimestamp != 0L) {
                    ((SystemClock.elapsedRealtimeNanos() - lastTimestamp) / CLOCK_NS_PER_MS).coerceAtLeast(0)
                } else JSONObject.NULL)
                .put("retainedSamples", window.size).put("latestSequence", received)
                .put("evictedSamples", received - window.size).put("discardedSamples", unrecorded)
        }

        fun read(after: Long, limit: Int, terminal: Boolean = false): JSONObject {
            val allowed = permitted(application, sensor, background)
            if (!allowed && !terminal) {
                DikcizNativeCommands.failure("sensor_access_denied", "Android no longer grants access to this sensor.")
            }
            val page = synchronized(this) {
                if (!allowed) return@synchronized Page(emptyList(), after, 0, 0, false, phase)
                pruneSamples()
                val first = window.firstOrNull()?.sequence ?: (received + 1)
                val samples = window.asSequence().filter { it.sequence > after }.take(limit).toList()
                val cursor = maxOf(after, samples.lastOrNull()?.sequence ?: first - 1)
                Page(samples, cursor, (first - 1 - after).coerceAtLeast(0), unrecorded, cursor < received, phase)
            }
            return JSONObject().put("sessionId", id).put("state", page.phase).put("nextSequence", page.cursor)
                .put("lostSamples", page.lost).put("discardedSamples", page.discarded).put("hasMore", page.more)
                .put("samples", JSONArray(page.samples.map(Sample::json)))
        }

        private data class Page(
            val samples: List<Sample>,
            val cursor: Long,
            val lost: Long,
            val discarded: Long,
            val more: Boolean,
            val phase: String,
        )

        @Synchronized
        fun finish(reason: String, notify: Boolean): Boolean {
            if (phase != ACTIVE) return false
            phase = reason
            terminalPending = notify
            endedAt = SystemClock.elapsedRealtime()
            return true
        }

        @Synchronized
        fun unregister() {
            lifecycle.removeCallbacks(watch)
            if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) manager.cancelTriggerSensor(trigger, sensor)
            else manager.unregisterListener(listener)
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
        }

        private fun deliverPage(page: JSONObject, terminal: Boolean): Boolean {
            val app = appId
            if (app != null) {
                DikcizCustomApps.deliverSensorStream(
                    application, app, if (terminal) STOPPED_EVENT else SAMPLES_EVENT, page,
                )
                return true
            }
            val accessId = access?.id ?: return false
            if (!access.isCurrent()) return false
            val event = if (terminal) SOCKET_STOPPED_EVENT else SOCKET_SAMPLES_EVENT
            return sockets.map { it.publishSensorStream(accessId, event, page) }.any { it }
        }

        fun deliver() {
            if (interval == 0 || !deliveryPending.compareAndSet(false, true)) return
            val terminal = synchronized(this) {
                val now = SystemClock.elapsedRealtime()
                val stopped = terminalPending
                if (!stopped && (phase != ACTIVE || (received == delivered && unrecorded == deliveredDiscarded) ||
                        now - lastDelivery < interval)) {
                    deliveryPending.set(false)
                    return
                }
                lastDelivery = now
                terminalPending = false
                stopped
            }
            try {
                deliveries.execute { dispatch(terminal) }
            } catch (_: RejectedExecutionException) {
                synchronized(this) { if (terminal) terminalPending = true }
                deliveryPending.set(false)
                if (terminal && SystemClock.elapsedRealtime() - endedAt < COMPLETED_MS) {
                    lifecycle.postDelayed(::deliver, CHECK_MS)
                }
            }
        }

        private fun dispatch(terminal: Boolean) {
            try {
                if (!terminal && !permitted(application, sensor, background)) return
                val page = read(delivered, MAX_PAGE, terminal)
                val notify = synchronized(this) { terminal || phase == ACTIVE }
                if (notify && deliverPage(page, terminal)) {
                    delivered = page.getLong("nextSequence")
                    deliveredDiscarded = page.getLong("discardedSamples")
                }
            } finally {
                deliveryPending.set(false)
                if (synchronized(this) { terminalPending }) deliver()
            }
        }
    }
}
