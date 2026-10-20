package org.fossify.home.dikciz

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Separates Android launch acceptance from an observed, visible target window. */
internal object DikcizActivityLaunches {
    const val KEY_LAUNCHES = "activityLaunches"
    const val REQUESTED = "launch_requested"
    const val LAUNCHED = "launched"
    const val UNVERIFIED = "launch_unverified"
    const val NOT_OBSERVED = "launch_not_observed"
    private const val LOCKED = "device_locked"
    private const val TIMEOUT_MILLISECONDS = 5_000L
    private const val POLL_MILLISECONDS = 100L
    private const val STABLE_MILLISECONDS = 300L
    private const val COMPLETION_GRACE_MILLISECONDS = 1_000L
    private const val MAXIMUM_RETAINED_LAUNCHES = 256
    private const val KEY_ID = "launchId"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_COMPONENT = "component"
    private const val KEY_EXPECTED_PACKAGE = "expectedPackageName"
    private const val KEY_OBSERVED_PACKAGE = "observedPackageName"
    private const val KEY_ACCEPTED = "accepted"
    private const val KEY_VERIFIED = "verified"
    private const val KEY_REASON = "reason"
    private const val KEY_DURATION = "durationMilliseconds"
    private const val EVENT_REQUESTED = "activity_launch_requested"
    private const val EVENT_COMPLETED = "activity_launch_observed"
    private const val FIELD_LAUNCH_ID = "launch_id"
    private const val FIELD_COMPONENT_PACKAGE = "component_package"
    private const val FIELD_OBSERVED_PACKAGE = "observed_package"
    private const val FIELD_DURATION = "duration_milliseconds"
    private const val TARGET_UNAVAILABLE = "target_unavailable"
    private const val ANDROID_REJECTED = "android_rejected"
    private const val REASON_OBSERVATION_TIMEOUT = "observation_timeout"
    private const val REASON_INTERRUPTED = "observation_interrupted"
    private const val REASON_TARGET_UNRESOLVED = "target_package_unresolved"
    private const val REASON_NOT_ENABLED = "accessibility_not_enabled"
    private const val REASON_NOT_FOREGROUND = "target_not_foreground"
    private val handler = Handler(Looper.getMainLooper())
    private val launches = ConcurrentHashMap<String, Launch>()
    private val scope = ThreadLocal<MutableList<Launch>>()

    fun capture(action: () -> JSONObject): JSONObject {
        val previous = scope.get()
        val captured = mutableListOf<Launch>()
        scope.set(captured)
        try {
            val result = action()
            if (captured.isNotEmpty()) {
                result.put(KEY_LAUNCHES, JSONArray(captured.map(Launch::document)))
                previous?.addAll(captured)
            }
            return result
        } finally {
            scope.set(previous)
        }
    }

    fun complete(result: JSONObject): JSONObject {
        val records = result.optJSONArray(KEY_LAUNCHES) ?: return result
        val completed = JSONArray()
        for (index in 0 until records.length()) {
            val original = records.getJSONObject(index)
            val launch = launches[original.getString(KEY_ID)]
            launch?.awaitOutcome()
            completed.put(launch?.document() ?: original
                .put(KEY_OUTCOME, UNVERIFIED)
                .put(KEY_VERIFIED, false)
                .put(KEY_REASON, REASON_OBSERVATION_TIMEOUT))
        }
        result.put(KEY_LAUNCHES, completed)
        if (completed.length() == 1) {
            val outcome = completed.getJSONObject(0).getString(KEY_OUTCOME)
            when (result.optString(KEY_OUTCOME)) {
                REQUESTED -> result.put(KEY_OUTCOME, outcome)
                DikcizAppActionOutcome.APP_INFO_REQUESTED -> result.put(
                    KEY_OUTCOME,
                    if (outcome == LAUNCHED) DikcizAppActionOutcome.APP_INFO_OPENED else outcome,
                )
            }
            if (result.has(LAUNCHED)) result.put(LAUNCHED, outcome == LAUNCHED)
        }
        return result
    }

    fun appIntent(component: ComponentName): Intent = Intent.makeMainActivity(component).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
    )

    fun start(context: Context, intent: Intent, logger: DikcizLogger): Launch {
        val target = intent.component ?: intent.resolveActivity(context.packageManager)
        val launch = Launch(context.applicationContext, target?.packageName, target?.flattenToString(), logger)
        if (launches.size >= MAXIMUM_RETAINED_LAUNCHES) {
            launches.values.filter { it.isComplete() }.sortedBy { it.startedAt }.take(
                launches.size - MAXIMUM_RETAINED_LAUNCHES + 1,
            ).forEach { launches.remove(it.id, it) }
        }
        launches[launch.id] = launch
        scope.get()?.add(launch)
        try {
            // External activities must never inherit a HOME task type.
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            launch.accepted = true
            logger.info(
                EVENT_REQUESTED,
                mapOf(
                    FIELD_LAUNCH_ID to launch.id,
                    FIELD_COMPONENT_PACKAGE to (target?.packageName ?: JSONObject.NULL),
                ),
            )
            handler.post { launch.observe() }
        } catch (exception: ActivityNotFoundException) {
            launch.finish(TARGET_UNAVAILABLE, TARGET_UNAVAILABLE)
            throw exception
        } catch (exception: SecurityException) {
            launch.finish(ANDROID_REJECTED, ANDROID_REJECTED)
            throw exception
        }
        return launch
    }

    internal class Launch(
        private val context: Context,
        private val expectedPackage: String?,
        private val component: String?,
        private val logger: DikcizLogger,
    ) {
        val id: String = UUID.randomUUID().toString()
        val startedAt: Long = SystemClock.elapsedRealtime()
        @Volatile var accepted = false
        private val completion = CountDownLatch(1)
        private var outcome = REQUESTED
        private var reason: String? = null
        private var observedPackage: String? = null
        private var stableSince: Long? = null
        private var duration = 0L

        fun isComplete(): Boolean = completion.count == 0L

        fun awaitOutcome(): String {
            if (Looper.myLooper() != handler.looper) {
                try {
                    if (!completion.await(TIMEOUT_MILLISECONDS + COMPLETION_GRACE_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                        finish(UNVERIFIED, REASON_OBSERVATION_TIMEOUT)
                    }
                } catch (exception: InterruptedException) {
                    Thread.currentThread().interrupt()
                    finish(UNVERIFIED, REASON_INTERRUPTED)
                }
            }
            return synchronized(this) { outcome }
        }

        fun observe() {
            if (isComplete()) return
            if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
                finish(LOCKED, LOCKED)
                return
            }
            if (expectedPackage == null) {
                finish(UNVERIFIED, REASON_TARGET_UNRESOLVED)
                return
            }
            val observed = try {
                DikcizAccessibilityAutomation.foregroundPackage()
            } catch (exception: DikcizAccessibilityAutomationException) {
                if (exception.code == REASON_NOT_ENABLED) {
                    finish(UNVERIFIED, exception.code)
                    return
                }
                null
            }
            val now = SystemClock.elapsedRealtime()
            synchronized(this) { observedPackage = observed }
            if (observed == expectedPackage) {
                val since = stableSince ?: now.also { stableSince = it }
                if (now - since >= STABLE_MILLISECONDS) {
                    finish(LAUNCHED, null)
                    return
                }
            } else {
                stableSince = null
            }
            if (now - startedAt >= TIMEOUT_MILLISECONDS) {
                finish(NOT_OBSERVED, REASON_NOT_FOREGROUND)
                return
            }
            handler.postDelayed(::observe, POLL_MILLISECONDS)
        }

        @Synchronized
        fun finish(outcome: String, reason: String?) {
            if (isComplete()) return
            this.outcome = outcome
            this.reason = reason
            duration = SystemClock.elapsedRealtime() - startedAt
            val fields = mapOf(
                FIELD_LAUNCH_ID to id, FIELD_COMPONENT_PACKAGE to (expectedPackage ?: JSONObject.NULL),
                FIELD_OBSERVED_PACKAGE to (observedPackage ?: JSONObject.NULL), KEY_OUTCOME to outcome,
                KEY_REASON to (reason ?: JSONObject.NULL), FIELD_DURATION to duration,
            )
            if (outcome == LAUNCHED) logger.info(EVENT_COMPLETED, fields)
            else logger.warn(EVENT_COMPLETED, fields)
            completion.countDown()
        }

        @Synchronized
        fun document(): JSONObject = JSONObject()
            .put(KEY_ID, id)
            .put(KEY_COMPONENT, component ?: JSONObject.NULL)
            .put(KEY_EXPECTED_PACKAGE, expectedPackage ?: JSONObject.NULL)
            .put(KEY_OBSERVED_PACKAGE, observedPackage ?: JSONObject.NULL)
            .put(KEY_ACCEPTED, accepted)
            .put(KEY_VERIFIED, outcome == LAUNCHED)
            .put(KEY_OUTCOME, outcome)
            .put(KEY_REASON, reason ?: JSONObject.NULL)
            .put(KEY_DURATION, duration)
    }
}
