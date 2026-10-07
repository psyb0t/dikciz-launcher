package org.fossify.home.dikciz

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizAutomationStatus {
    fun document(context: Context): JSONObject {
        val sensors = availableSensors(context)
        val activeMediaSessionCount = DikcizMediaSessionMonitor.activeSessionCount(context)
        return JSONObject()
            .put(KEY_API_VERSION, DikcizAutomationApi.CURRENT_VERSION)
            .put(
                KEY_CAPABILITIES,
                persistedValues(DikcizAutomationCapability.entries.map(DikcizAutomationCapability::persistedValue)),
            )
            .put(
                KEY_ACTIONS,
                persistedValues(DikcizAutomationActionCapability.entries.map(DikcizAutomationActionCapability::persistedValue)),
            )
            .put(KEY_EVENTS, eventSpecifications())
            .put(
                KEY_INTENT_TYPES,
                persistedValues(DikcizAutomationIntentType.entries.map(DikcizAutomationIntentType::persistedValue)),
            )
            .put(KEY_ANDROID_ACCESS, androidAccessDocument(context))
            .put(KEY_DEVICE_ACCESS, DikcizDeviceAccess.document(context))
            .put(KEY_AVAILABLE_SENSOR_COUNT, sensors.size)
            .put(KEY_AVAILABLE_SENSOR_LIMIT, MAXIMUM_AVAILABLE_SENSOR_COUNT)
            .put(KEY_AVAILABLE_SENSORS_TRUNCATED, sensors.size > MAXIMUM_AVAILABLE_SENSOR_COUNT)
            .put(KEY_AVAILABLE_SENSORS, sensors.take(MAXIMUM_AVAILABLE_SENSOR_COUNT).toJson())
            .put(KEY_ACTIVE_MEDIA_SESSION_COUNT, activeMediaSessionCount)
            .put(KEY_ACTIVE_MEDIA_SESSION_LIMIT, MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT)
            .put(KEY_ACTIVE_MEDIA_SESSIONS_TRUNCATED, activeMediaSessionCount > MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT)
    }

    private fun availableSensors(context: Context): List<Sensor> {
        val sensorManager = context.getSystemService(SensorManager::class.java) ?: return emptyList()
        return sensorManager.getSensorList(Sensor.TYPE_ALL)
            .sortedWith(compareBy<Sensor> { it.type }.thenBy(Sensor::getName).thenBy(Sensor::getVendor))
    }

    private fun persistedValues(values: List<String>): JSONArray = JSONArray(values.sorted())

    private fun eventSpecifications(): JSONArray {
        return JSONArray().apply {
            DikcizAutomationEventType.entries
                .filterNot { event -> event == DikcizAutomationEventType.Custom }
                .sortedBy(DikcizAutomationEventType::persistedValue)
                .forEach { event ->
                    put(
                        JSONObject()
                            .put(KEY_TYPE, event.persistedValue)
                            .put(KEY_REQUIRED_CAPABILITY, event.capability.persistedValue)
                            .put(
                                KEY_REQUIRED_SUBSCRIPTION_FIELDS,
                                JSONArray(event.requiredSubscriptionFields()),
                            )
                            .put(
                                KEY_OPTIONAL_SUBSCRIPTION_FIELDS,
                                JSONArray(event.optionalSubscriptionFields()),
                            ),
                    )
                }
        }
    }

    fun androidAccessDocument(context: Context): JSONObject {
        return JSONObject()
            .put(KEY_HOME_ROLE, context.hasHomeRole())
            .put(KEY_SHARED_STORAGE, HomeConfigStore(context).hasSharedStorageAccess())
            .put(KEY_LOCATION_APPROXIMATE, context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            .put(KEY_LOCATION_PRECISE, context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            .put(KEY_MEDIA_VOLUME_MUTABLE, context.hasMutableMediaVolume())
            .put(KEY_BLUETOOTH_CONNECT, context.hasBluetoothConnectPermission())
            .put(KEY_CALENDAR_READ, context.hasPermission(Manifest.permission.READ_CALENDAR))
            .put(KEY_CONTACTS_READ, context.hasPermission(Manifest.permission.READ_CONTACTS))
            .put(KEY_PHONE_STATE_READ, context.hasPermission(Manifest.permission.READ_PHONE_STATE))
            .put(KEY_SMS_RECEIVE, context.hasPermission(Manifest.permission.RECEIVE_SMS))
            .put(KEY_SMS_SEND, context.hasPermission(Manifest.permission.SEND_SMS))
            .put(KEY_HEALTH_CONNECT_AVAILABLE, DikcizHealthConnectAutomation.isAvailable(context))
            .put(KEY_CLIPBOARD_MONITOR_ACTIVE, DikcizClipboardMonitor.isActive())
            .put(KEY_DEVICE_ADMIN_ACTIVE, DikcizDeviceAdministration.isActive(context))
            .put(KEY_ACTIVITY_RECOGNITION, DikcizAutomationSensorPermission.hasActivityRecognitionPermission(context))
            .put(KEY_BODY_SENSORS, DikcizAutomationSensorPermission.hasLegacyBodySensorsPermission(context))
            .put(
                KEY_BODY_SENSORS_BACKGROUND,
                DikcizAutomationSensorPermission.hasLegacyBodySensorsBackgroundPermission(context),
            )
            .put(KEY_HEART_RATE_READ, DikcizAutomationSensorPermission.hasHeartRatePermission(context))
            .put(
                KEY_HEALTH_DATA_BACKGROUND,
                DikcizAutomationSensorPermission.hasHeartRateBackgroundPermission(context),
            )
            .put(
                KEY_HIGH_SAMPLING_RATE_SENSORS,
                context.hasPermission(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS),
            )
            .put(KEY_NOTIFICATION_POSTING, context.hasNotificationPostingPermission())
            .put(KEY_NOTIFICATION_LISTENER, hasNotificationListenerAccess(context))
            .put(KEY_NOTIFICATION_POLICY_ACCESS, context.hasNotificationPolicyAccess())
            .put(KEY_ACCESSIBILITY, DikcizAccessibilityAutomation.isEnabled(context))
    }

    private fun Context.hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun Context.hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun Context.hasMutableMediaVolume(): Boolean {
        val audioManager = getSystemService(AudioManager::class.java) ?: return false
        return !audioManager.isVolumeFixed
    }

    private fun Context.hasNotificationPostingPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun Context.hasHomeRole(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java) ?: return false
            return roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val homeActivity = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return homeActivity?.activityInfo?.packageName == packageName
    }

    fun hasNotificationListenerAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            SECURE_SETTING_ENABLED_NOTIFICATION_LISTENERS,
        ).orEmpty()
        val ownComponent = ComponentName(context, DikcizNotificationListenerService::class.java).flattenToString()
        return enabledListeners.split(ENABLED_NOTIFICATION_LISTENER_SEPARATOR).any(ownComponent::equals)
    }

    private fun List<Sensor>.toJson(): JSONArray {
        return JSONArray().apply {
            this@toJson.forEach { sensor ->
                put(
                    JSONObject()
                        .put(KEY_TYPE, sensor.type)
                        .put(KEY_NAME, sensor.name.take(MAXIMUM_SENSOR_METADATA_CHARACTERS))
                        .put(KEY_VENDOR, sensor.vendor.take(MAXIMUM_SENSOR_METADATA_CHARACTERS))
                        .put(KEY_MINIMUM_DELAY_MICROSECONDS, sensor.minDelay)
                        .put(KEY_MAXIMUM_DELAY_MICROSECONDS, sensor.maxDelay)
                        .put(KEY_WAKE_UP, sensor.isWakeUpSensor),
                )
            }
        }
    }

    private fun DikcizAutomationEventType.requiredSubscriptionFields(): List<String> {
        return when (this) {
            DikcizAutomationEventType.Alarm -> listOf(FIELD_ALARM_INTERVAL_MILLISECONDS)
            DikcizAutomationEventType.HealthDailySteps -> listOf(FIELD_HEALTH_REFRESH_INTERVAL_MILLISECONDS)
            DikcizAutomationEventType.Location -> listOf(FIELD_LOCATION_PRECISION)
            DikcizAutomationEventType.Sensor -> listOf(
                FIELD_SENSOR_TYPES,
                FIELD_SAMPLING_PERIOD_MICROSECONDS,
            )

            else -> emptyList()
        }
    }

    private fun DikcizAutomationEventType.optionalSubscriptionFields(): List<String> {
        return when (this) {
            DikcizAutomationEventType.NotificationPosted,
            DikcizAutomationEventType.NotificationRemoved,
            DikcizAutomationEventType.MediaSession,
            -> listOf(FIELD_PACKAGES)

            else -> emptyList()
        }
    }

    private const val ENABLED_NOTIFICATION_LISTENER_SEPARATOR = ":"
    private const val FIELD_ALARM_INTERVAL_MILLISECONDS = "alarmIntervalMilliseconds"
    private const val FIELD_HEALTH_REFRESH_INTERVAL_MILLISECONDS = "healthRefreshIntervalMilliseconds"
    private const val FIELD_LOCATION_PRECISION = "locationPrecision"
    private const val FIELD_PACKAGES = "packages"
    private const val FIELD_SAMPLING_PERIOD_MICROSECONDS = "samplingPeriodMicroseconds"
    private const val FIELD_SENSOR_TYPES = "sensorTypes"
    private const val KEY_ACTIONS = "actions"
    private const val KEY_ACTIVE_MEDIA_SESSION_COUNT = "activeMediaSessionCount"
    private const val KEY_ACTIVE_MEDIA_SESSION_LIMIT = "activeMediaSessionLimit"
    private const val KEY_ACTIVE_MEDIA_SESSIONS_TRUNCATED = "activeMediaSessionsTruncated"
    private const val KEY_ACTIVITY_RECOGNITION = "activityRecognition"
    private const val KEY_ACCESSIBILITY = "accessibility"
    private const val KEY_ANDROID_ACCESS = "androidAccess"
    private const val KEY_API_VERSION = "apiVersion"
    private const val KEY_AVAILABLE_SENSOR_COUNT = "availableSensorCount"
    private const val KEY_AVAILABLE_SENSOR_LIMIT = "availableSensorLimit"
    private const val KEY_AVAILABLE_SENSORS = "availableSensors"
    private const val KEY_AVAILABLE_SENSORS_TRUNCATED = "availableSensorsTruncated"
    private const val KEY_BODY_SENSORS = "bodySensors"
    private const val KEY_BODY_SENSORS_BACKGROUND = "bodySensorsBackground"
    private const val KEY_BLUETOOTH_CONNECT = "bluetoothConnect"
    private const val KEY_CAPABILITIES = "capabilities"
    private const val KEY_DEVICE_ACCESS = "deviceAccess"
    private const val KEY_CALENDAR_READ = "calendarRead"
    private const val KEY_CONTACTS_READ = "contactsRead"
    private const val KEY_CLIPBOARD_MONITOR_ACTIVE = "clipboardMonitorActive"
    private const val KEY_DEVICE_ADMIN_ACTIVE = "deviceAdminActive"
    private const val KEY_EVENTS = "events"
    private const val KEY_HIGH_SAMPLING_RATE_SENSORS = "highSamplingRateSensors"
    private const val KEY_HEALTH_CONNECT_AVAILABLE = "healthConnectAvailable"
    private const val KEY_HEALTH_DATA_BACKGROUND = "healthDataBackground"
    private const val KEY_HEART_RATE_READ = "heartRateRead"
    private const val KEY_HOME_ROLE = "homeRole"
    private const val KEY_INTENT_TYPES = "intentTypes"
    private const val KEY_LOCATION_APPROXIMATE = "locationApproximate"
    private const val KEY_LOCATION_PRECISE = "locationPrecise"
    private const val KEY_MEDIA_VOLUME_MUTABLE = "mediaVolumeMutable"
    private const val KEY_MAXIMUM_DELAY_MICROSECONDS = "maximumDelayMicroseconds"
    private const val KEY_MINIMUM_DELAY_MICROSECONDS = "minimumDelayMicroseconds"
    private const val KEY_NAME = "name"
    private const val KEY_NOTIFICATION_LISTENER = "notificationListener"
    private const val KEY_NOTIFICATION_POLICY_ACCESS = "notificationPolicyAccess"
    private const val KEY_NOTIFICATION_POSTING = "notificationPosting"
    private const val KEY_PHONE_STATE_READ = "phoneStateRead"
    private const val KEY_SHARED_STORAGE = "sharedStorage"
    private const val KEY_SMS_RECEIVE = "smsReceive"
    private const val KEY_SMS_SEND = "smsSend"
    private const val KEY_OPTIONAL_SUBSCRIPTION_FIELDS = "optionalSubscriptionFields"
    private const val KEY_REQUIRED_CAPABILITY = "requiredCapability"
    private const val KEY_REQUIRED_SUBSCRIPTION_FIELDS = "requiredSubscriptionFields"
    private const val KEY_TYPE = "type"
    private const val KEY_VENDOR = "vendor"
    private const val KEY_WAKE_UP = "wakeUp"
    private const val MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT = 64
    private const val MAXIMUM_AVAILABLE_SENSOR_COUNT = 64
    private const val MAXIMUM_SENSOR_METADATA_CHARACTERS = 256
    private const val SECURE_SETTING_ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
}
