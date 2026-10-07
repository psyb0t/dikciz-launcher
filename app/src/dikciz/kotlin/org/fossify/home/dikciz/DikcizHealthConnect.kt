package org.fossify.home.dikciz

import android.content.Context
import android.os.RemoteException
import android.os.Handler
import android.os.Looper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

internal object DikcizHealthConnectAutomation {
    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    fun requestedStepsPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    fun requestedHeartRatePermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun requestedBackgroundPermissions(): Set<String> = setOf(
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    fun enqueueDailySteps(
        context: Context,
        dayStartMilliseconds: Long,
        steps: Long,
    ): Boolean {
        if (dayStartMilliseconds < MINIMUM_TIMESTAMP_MILLISECONDS || steps !in MINIMUM_STEPS..MAXIMUM_STEPS) {
            return false
        }
        DikcizAutomationEventBus.enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.HealthDailySteps,
                source = SOURCE_HEALTH_CONNECT,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = COALESCING_DAILY_STEPS,
                payload = JSONObject()
                    .put(PAYLOAD_DAY_START_MILLISECONDS, dayStartMilliseconds)
                    .put(PAYLOAD_STEPS, steps),
            ),
        )
        return true
    }

    fun currentLocalDayStartMilliseconds(nowMilliseconds: Long = System.currentTimeMillis()): Long {
        return Instant.ofEpochMilli(nowMilliseconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    const val MAXIMUM_STEPS = 1_000_000L
    const val MINIMUM_STEPS = 0L
    const val PAYLOAD_DAY_START_MILLISECONDS = "dayStartMilliseconds"
    const val PAYLOAD_STEPS = "steps"

    private const val COALESCING_DAILY_STEPS = "health-daily-steps"
    private const val MINIMUM_TIMESTAMP_MILLISECONDS = 0L
    private const val SOURCE_HEALTH_CONNECT = "health-connect"
}

internal class DikcizHealthConnectDailyStepsSource(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generation = INITIAL_GENERATION
    private var refreshIntervalMilliseconds: Long? = null
    private var refreshJob: Job? = null

    fun configure(configuration: HomeConfiguration) {
        stop()
        refreshIntervalMilliseconds = configuredRefreshInterval(configuration)
        val currentGeneration = generation
        if (refreshIntervalMilliseconds != null) {
            refresh(currentGeneration)
        }
    }

    fun stop() {
        generation += NEXT_GENERATION_INCREMENT
        refreshJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        refreshIntervalMilliseconds = null
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun refresh(currentGeneration: Long) {
        refreshJob = scope.launch {
            val result = readDailySteps()
            if (currentGeneration != generation) {
                return@launch
            }
            when (result) {
                is DailyStepsReadResult.Success -> {
                    DikcizHealthConnectAutomation.enqueueDailySteps(
                        applicationContext,
                        result.dayStartMilliseconds,
                        result.steps,
                    )
                }

                is DailyStepsReadResult.Rejected -> {
                    logger.debug(
                        EVENT_HEALTH_CONNECT_REFRESH_REJECTED,
                        mapOf(FIELD_REASON to result.reason),
                    )
                }
            }
            mainHandler.post { scheduleNext(currentGeneration) }
        }
    }

    private fun scheduleNext(currentGeneration: Long) {
        val delay = refreshIntervalMilliseconds ?: return
        if (currentGeneration != generation) {
            return
        }
        mainHandler.postDelayed({ refresh(currentGeneration) }, delay)
    }

    private suspend fun readDailySteps(): DailyStepsReadResult {
        if (!DikcizHealthConnectAutomation.isAvailable(applicationContext)) {
            return DailyStepsReadResult.Rejected(REASON_PROVIDER_UNAVAILABLE)
        }
        return try {
            val client = HealthConnectClient.getOrCreate(applicationContext)
            val requiredPermissions = DikcizHealthConnectAutomation.requestedStepsPermissions() +
                DikcizHealthConnectAutomation.requestedBackgroundPermissions()
            if (!client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)) {
                return DailyStepsReadResult.Rejected(REASON_PERMISSION_MISSING)
            }
            val dayStartMilliseconds = DikcizHealthConnectAutomation.currentLocalDayStartMilliseconds()
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(dayStartMilliseconds),
                        Instant.now(),
                    ),
                ),
            )
            val steps = response[StepsRecord.COUNT_TOTAL] ?: DikcizHealthConnectAutomation.MINIMUM_STEPS
            if (steps !in DikcizHealthConnectAutomation.MINIMUM_STEPS..DikcizHealthConnectAutomation.MAXIMUM_STEPS) {
                DailyStepsReadResult.Rejected(REASON_VALUE_REJECTED)
            } else {
                DailyStepsReadResult.Success(dayStartMilliseconds, steps)
            }
        } catch (_: SecurityException) {
            DailyStepsReadResult.Rejected(REASON_PERMISSION_MISSING)
        } catch (_: IOException) {
            DailyStepsReadResult.Rejected(REASON_QUERY_REJECTED)
        } catch (_: RemoteException) {
            DailyStepsReadResult.Rejected(REASON_QUERY_REJECTED)
        } catch (_: IllegalArgumentException) {
            DailyStepsReadResult.Rejected(REASON_QUERY_REJECTED)
        } catch (_: IllegalStateException) {
            DailyStepsReadResult.Rejected(REASON_QUERY_REJECTED)
        }
    }

    private fun configuredRefreshInterval(configuration: HomeConfiguration): Long? {
        return configuration.automation.scripts.asSequence()
            .filter(DikcizAutomationScript::enabled)
            .mapNotNull { script ->
                configuration.automation.policy(script.policyID)?.takeIf(DikcizAutomationPolicy::enabled)
                    ?.let { policy -> script to policy }
            }
            .flatMap { (script, policy) ->
                script.subscriptions.asSequence().filter { subscription ->
                    subscription.event == DikcizAutomationEventType.HealthDailySteps &&
                        DikcizAutomationCapability.HealthSteps in policy.capabilities
                }
            }
            .mapNotNull(DikcizAutomationSubscription::healthRefreshIntervalMilliseconds)
            .minOrNull()
    }

    private sealed interface DailyStepsReadResult {
        data class Success(
            val dayStartMilliseconds: Long,
            val steps: Long,
        ) : DailyStepsReadResult

        data class Rejected(
            val reason: String,
        ) : DailyStepsReadResult
    }

    private companion object {
        const val EVENT_HEALTH_CONNECT_REFRESH_REJECTED = "automation_health_connect_refresh_rejected"
        const val FIELD_REASON = "reason"
        const val INITIAL_GENERATION = 0L
        const val NEXT_GENERATION_INCREMENT = 1L
        const val REASON_PERMISSION_MISSING = "permission_missing"
        const val REASON_PROVIDER_UNAVAILABLE = "provider_unavailable"
        const val REASON_QUERY_REJECTED = "query_rejected"
        const val REASON_VALUE_REJECTED = "value_rejected"
    }
}
