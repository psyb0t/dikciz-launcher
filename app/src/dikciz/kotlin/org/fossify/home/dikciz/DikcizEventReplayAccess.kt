package org.fossify.home.dikciz

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fossify.home.dikciz.DikcizAutomationEventType as Event

/** Recheck platform access before disclosing retained event payloads. */
internal class DikcizEventReplayAccess(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    private var stepsAllowed: Boolean? = null

    fun require(type: Event, precision: DikcizLocationPrecision?, sensorType: Int?) {
        when (type) {
            Event.NotificationPosted, Event.NotificationRemoved -> {
                if (!DikcizAutomationStatus.hasNotificationListenerAccess(context)) denied("notificationListener")
            }
            Event.MediaSession -> {
                if (!DikcizMediaSessionMonitor.hasAccess(context)) denied("notificationListener")
            }
            Event.AccessibilityWindow -> {
                if (!DikcizAccessibilityAutomation.isEnabled(context)) {
                    DikcizNativeCommands.failure("accessibility_not_enabled", "Enable Android Accessibility before reading retained UI events")
                }
            }
            Event.ClipboardChanged -> {
                val state = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(state)
                if (state.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    DikcizNativeCommands.failure("event_journal_foreground_required", "Open Dikciz before reading retained clipboard events")
                }
            }
            Event.HealthDailySteps -> {
                if (stepsAllowed == null) stepsAllowed = healthStepsAccess()
                if (stepsAllowed != true) DikcizNativeCommands.failure(
                    "health_connect_permission_required",
                    "Grant Health Connect step reading in Dikciz automation settings before reading retained health events",
                )
            }
            else -> {
                val subscription = DikcizAutomationSubscription(
                    event = type, minimumIntervalMilliseconds = 0L, coalescingKey = null,
                    locationPrecision = precision, sensorTypes = listOfNotNull(sensorType),
                )
                if (type == Event.Sensor && (sensorType == null || sensorType < 0)) {
                    DikcizNativeCommands.failure("event_sensor_access_unknown", "Retained sensor event has no valid permission identity")
                }
                if (context.hasAndroidSubscriptionPermission(subscription, sensorType)) return
                denied(when (type) {
                    Event.CalendarEvent -> "calendar"
                    Event.ContactsChanged -> "contacts"
                    Event.PhoneState -> "telephony"
                    Event.SmsReceived -> "sms"
                    Event.BluetoothState -> "bluetooth"
                    Event.InterruptionFilter -> "notificationPolicy"
                    Event.Location -> "locationForeground"
                    Event.Sensor -> if (sensorType in setOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)) {
                        "activityRecognition"
                    } else {
                        "bodySensors"
                    }
                    else -> "eventSource"
                })
            }
        }
    }

    private fun healthStepsAccess(): Boolean {
        if (!DikcizHealthConnectAutomation.isAvailable(context)) return false
        return try {
            runBlocking {
                withTimeout(HEALTH_PERMISSION_TIMEOUT) {
                    HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                        .containsAll(DikcizHealthConnectAutomation.requestedStepsPermissions())
                }
            }
        } catch (exception: Exception) {
            if (exception is InterruptedException) throw exception
            logger.warn("event_replay_health_access_unconfirmed", mapOf("error_class" to exception.javaClass.simpleName))
            false
        }
    }

    private fun denied(requirement: String): Nothing = DikcizNativeCommands.failure(
        "native_permission_" + requirement,
        "Current Android source access is required before reading retained events",
    )

    private companion object {
        const val HEALTH_PERMISSION_TIMEOUT = 5000L
    }
}
