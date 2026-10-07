package org.fossify.home.dikciz

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.Build
import org.json.JSONObject

internal object DikcizAutomationApi {
    const val CURRENT_VERSION = 1
}

internal object DikcizAutomationEventNames {
    const val MAXIMUM_CUSTOM_EVENT_NAME_CHARACTERS = 128

    private val customEventNamePattern = Regex("^[A-Za-z][A-Za-z0-9_.:-]*$")

    fun isValidCustomName(value: String): Boolean {
        return value.length in 1..MAXIMUM_CUSTOM_EVENT_NAME_CHARACTERS &&
            NUL_CHARACTER !in value &&
            customEventNamePattern.matches(value)
    }

    private const val NUL_CHARACTER = '\u0000'
}

internal object DikcizAutomationEventPayload {
    const val ACCURACY = "accuracy"
    const val ALTITUDE = "altitude"
    const val BEARING = "bearing"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val PACKAGE_NAME = "packageName"
    const val PROVIDER = "provider"
    const val SENSOR_TYPE = "sensorType"
    const val SPEED = "speed"
}

internal object DikcizAutomationSensorPermission {
    const val HEALTH_READ_HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
    const val HEALTH_READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION =
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    fun hasActivityRecognitionPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    fun hasLegacyBodySensorsPermission(context: Context): Boolean {
        return !usesGranularHealthPermissions() && context.hasPermission(Manifest.permission.BODY_SENSORS)
    }

    fun hasLegacyBodySensorsBackgroundPermission(context: Context): Boolean {
        return !usesGranularHealthPermissions() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.hasPermission(Manifest.permission.BODY_SENSORS_BACKGROUND)
    }

    fun hasHeartRatePermission(context: Context): Boolean {
        if (usesGranularHealthPermissions()) {
            return context.hasPermission(HEALTH_READ_HEART_RATE_PERMISSION)
        }
        return hasLegacyBodySensorsPermission(context)
    }

    fun hasHeartRateBackgroundPermission(context: Context): Boolean {
        if (usesGranularHealthPermissions()) {
            return context.hasPermission(HEALTH_READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return hasLegacyBodySensorsBackgroundPermission(context)
    }

    fun hasPermission(context: Context, sensorType: Int): Boolean {
        if (sensorType == Sensor.TYPE_STEP_COUNTER || sensorType == Sensor.TYPE_STEP_DETECTOR) {
            return hasActivityRecognitionPermission(context)
        }
        if (sensorType == Sensor.TYPE_HEART_RATE) {
            return hasHeartRatePermission(context) && hasHeartRateBackgroundPermission(context)
        }
        return true
    }

    fun requiresActivityRecognition(sensorTypes: Collection<Int>): Boolean {
        return sensorTypes.any { sensorType ->
            sensorType == Sensor.TYPE_STEP_COUNTER || sensorType == Sensor.TYPE_STEP_DETECTOR
        }
    }

    fun requiresHeartRate(sensorTypes: Collection<Int>): Boolean {
        return Sensor.TYPE_HEART_RATE in sensorTypes
    }

    fun usesGranularHealthPermissions(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    }

    private fun Context.hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

internal data class DikcizAutomationConfiguration(
    val apiVersion: Int = DikcizAutomationApi.CURRENT_VERSION,
    val policies: List<DikcizAutomationPolicy> = emptyList(),
    val scripts: List<DikcizAutomationScript> = emptyList(),
) {
    fun policy(id: String): DikcizAutomationPolicy? = policies.firstOrNull { it.id == id }

    fun isEnabled(): Boolean = scripts.any { script ->
        script.enabled
    }

    fun requiresBackgroundService(): Boolean {
        return scripts.any { script ->
            script.enabled && script.subscriptions.any { subscription ->
                subscription.event != DikcizAutomationEventType.NotificationPosted &&
                    subscription.event != DikcizAutomationEventType.NotificationRemoved &&
                subscription.event != DikcizAutomationEventType.MediaSession &&
                subscription.event != DikcizAutomationEventType.SmsReceived &&
                    subscription.event != DikcizAutomationEventType.DeviceAdminState &&
                    subscription.event != DikcizAutomationEventType.ClipboardChanged &&
                    subscription.event != DikcizAutomationEventType.WidgetChanged &&
                    subscription.event != DikcizAutomationEventType.Custom &&
                    subscription.event != DikcizAutomationEventType.Manual
            }
        }
    }

    fun requiresActivityRecognitionSensorAccess(): Boolean {
        return DikcizAutomationSensorPermission.requiresActivityRecognition(activeSensorTypes())
    }

    fun requiresHeartRateSensorAccess(): Boolean {
        return DikcizAutomationSensorPermission.requiresHeartRate(activeSensorTypes())
    }

    private fun activeSensorTypes(): Set<Int> {
        return scripts.asSequence()
            .filter(DikcizAutomationScript::enabled)
            .flatMap(DikcizAutomationScript::subscriptions)
            .filter { subscription -> subscription.event == DikcizAutomationEventType.Sensor }
            .flatMap(DikcizAutomationSubscription::sensorTypes)
            .toSet()
    }
}

internal data class DikcizAutomationPolicy(
    val id: String,
    val title: String,
    val enabled: Boolean,
    val capabilities: Set<DikcizAutomationCapability>,
    val actions: Set<DikcizAutomationActionCapability>,
    val mediaSessionPackages: List<String> = emptyList(),
    val notificationActionPackages: List<String> = emptyList(),
)

internal data class DikcizAutomationScript(
    val scriptID: String,
    val policyID: String,
    val enabled: Boolean,
    val subscriptions: List<DikcizAutomationSubscription>,
)

internal data class DikcizAutomationSubscription(
    val event: DikcizAutomationEventType,
    val minimumIntervalMilliseconds: Long,
    val coalescingKey: String?,
    val packageNames: List<String> = emptyList(),
    val sensorTypes: List<Int> = emptyList(),
    val samplingPeriodMicroseconds: Int? = null,
    val alarmIntervalMilliseconds: Long? = null,
    val locationPrecision: DikcizLocationPrecision? = null,
    val healthRefreshIntervalMilliseconds: Long? = null,
    val customEventName: String? = null,
)

internal enum class DikcizAutomationCapability(
    val persistedValue: String,
) {
    Time("time"),
    Battery("battery"),
    Charging("charging"),
    PowerSaveState("powerSaveState"),
    DeviceIdleState("deviceIdleState"),
    NightModeState("nightModeState"),
    DeviceConfiguration("deviceConfiguration"),
    AppPackagesMetadata("appPackagesMetadata"),
    RingerModeState("ringerModeState"),
    InterruptionFilterState("interruptionFilterState"),
    Connectivity("connectivity"),
    ThermalState("thermalState"),
    BluetoothState("bluetoothState"),
    Screen("screen"),
    UserPresence("userPresence"),
    Alarm("alarm"),
    Sensors("sensors"),
    HealthSteps("healthSteps"),
    LocationApproximate("locationApproximate"),
    LocationPrecise("locationPrecise"),
    CalendarEventsMetadata("calendarEventsMetadata"),
    CalendarEventsContent("calendarEventsContent"),
    ContactsMetadata("contactsMetadata"),
    PhoneState("phoneState"),
    SmsMetadata("smsMetadata"),
    SmsContent("smsContent"),
    NotificationsMetadata("notificationsMetadata"),
    NotificationsContent("notificationsContent"),
    MediaSessionsMetadata("mediaSessionsMetadata"),
    MediaSessionsContent("mediaSessionsContent"),
    ClipboardMetadata("clipboardMetadata"),
    ClipboardContent("clipboardContent"),
    Accessibility("accessibility"),
    WidgetState("widgetState"),
    DeviceAdministration("deviceAdministration"),
    ManualTrigger("manualTrigger"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAutomationCapability? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal enum class DikcizAutomationActionCapability(
    val persistedValue: String,
) {
    SelectPage("selectPage"),
    PatchWidget("patchWidget"),
    PatchState("patchState"),
    PatchDom("patchDom"),
    LaunchApp("launchApp"),
    AppAction("appAction"),
    PostNotification("postNotification"),
    SendSms("sendSms"),
    ExplicitIntent("explicitIntent"),
    EmitEvent("emitEvent"),
    MediaControl("mediaControl"),
    MediaVolume("mediaVolume"),
    NotificationControl("notificationControl"),
    LockDevice("lockDevice"),
    AccessibilityAction("accessibilityAction"),
    AccessibilityGesture("accessibilityGesture"),
    AccessibilityGlobalAction("accessibilityGlobalAction"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAutomationActionCapability? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal enum class DikcizAutomationEventType(
    val persistedValue: String,
    val capability: DikcizAutomationCapability,
) {
    Time("time", DikcizAutomationCapability.Time),
    Battery("battery", DikcizAutomationCapability.Battery),
    Charging("charging", DikcizAutomationCapability.Charging),
    PowerSaveMode("powerSaveMode", DikcizAutomationCapability.PowerSaveState),
    DeviceIdleMode("deviceIdleMode", DikcizAutomationCapability.DeviceIdleState),
    NightMode("nightMode", DikcizAutomationCapability.NightModeState),
    DeviceConfiguration("deviceConfiguration", DikcizAutomationCapability.DeviceConfiguration),
    PackageChanged("packageChanged", DikcizAutomationCapability.AppPackagesMetadata),
    RingerMode("ringerMode", DikcizAutomationCapability.RingerModeState),
    InterruptionFilter("interruptionFilter", DikcizAutomationCapability.InterruptionFilterState),
    Connectivity("connectivity", DikcizAutomationCapability.Connectivity),
    ThermalStatus("thermalStatus", DikcizAutomationCapability.ThermalState),
    BluetoothState("bluetoothState", DikcizAutomationCapability.BluetoothState),
    Screen("screen", DikcizAutomationCapability.Screen),
    UserPresent("userPresent", DikcizAutomationCapability.UserPresence),
    Alarm("alarm", DikcizAutomationCapability.Alarm),
    Sensor("sensor", DikcizAutomationCapability.Sensors),
    HealthDailySteps("healthDailySteps", DikcizAutomationCapability.HealthSteps),
    Location("location", DikcizAutomationCapability.LocationApproximate),
    CalendarEvent("calendarEvent", DikcizAutomationCapability.CalendarEventsMetadata),
    ContactsChanged("contactsChanged", DikcizAutomationCapability.ContactsMetadata),
    PhoneState("phoneState", DikcizAutomationCapability.PhoneState),
    SmsReceived("smsReceived", DikcizAutomationCapability.SmsMetadata),
    NotificationPosted("notificationPosted", DikcizAutomationCapability.NotificationsMetadata),
    NotificationRemoved("notificationRemoved", DikcizAutomationCapability.NotificationsMetadata),
    MediaSession("mediaSession", DikcizAutomationCapability.MediaSessionsMetadata),
    ClipboardChanged("clipboardChanged", DikcizAutomationCapability.ClipboardMetadata),
    AccessibilityWindow("accessibilityWindow", DikcizAutomationCapability.Accessibility),
    AccessibilityServiceState("accessibilityServiceState", DikcizAutomationCapability.Accessibility),
    WidgetChanged("widgetChanged", DikcizAutomationCapability.WidgetState),
    DeviceAdminState("deviceAdminState", DikcizAutomationCapability.DeviceAdministration),
    Manual("manual", DikcizAutomationCapability.ManualTrigger),
    Custom("custom", DikcizAutomationCapability.ManualTrigger),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAutomationEventType? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal enum class DikcizLocationPrecision(
    val persistedValue: String,
    val capability: DikcizAutomationCapability,
) {
    Approximate("approximate", DikcizAutomationCapability.LocationApproximate),
    Precise("precise", DikcizAutomationCapability.LocationPrecise),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizLocationPrecision? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal data class DikcizAutomationEvent(
    val type: DikcizAutomationEventType,
    val source: String,
    val timestampMilliseconds: Long,
    val coalescingKey: String,
    val payload: JSONObject,
    val locationPrecision: DikcizLocationPrecision? = null,
    val customEventName: String? = null,
    val targetScriptID: String? = null,
    val customEventDepth: Int = 0,
) {
    val name: String
        get() = customEventName ?: type.persistedValue
}

internal fun DikcizAutomationSubscription.matches(event: DikcizAutomationEvent): Boolean {
    if (event.type != this.event) {
        return false
    }
    if (
        event.type == DikcizAutomationEventType.Location &&
        locationPrecision != event.locationPrecision
    ) {
        return false
    }
    if (
        this.event == DikcizAutomationEventType.Custom &&
        customEventName != event.customEventName
    ) {
        return false
    }
    if (packageNames.isNotEmpty()) {
        val packageName = event.payload.optString(DikcizAutomationEventPayload.PACKAGE_NAME)
        if (packageName !in packageNames) {
            return false
        }
    }
    if (sensorTypes.isNotEmpty()) {
        val sensorType = event.payload.optInt(DikcizAutomationEventPayload.SENSOR_TYPE, INVALID_SENSOR_TYPE)
        if (sensorType !in sensorTypes) {
            return false
        }
    }
    return true
}

private const val INVALID_SENSOR_TYPE = -1

internal sealed interface DikcizLuaAutomationAction {
    data class SelectPage(
        val pageID: String,
    ) : DikcizLuaAutomationAction

    data class PatchWidget(
        val widgetAddress: String,
        val values: JSONObject,
    ) : DikcizLuaAutomationAction

    data class PatchState(
        val values: JSONObject,
    ) : DikcizLuaAutomationAction

    data class PatchDom(
        val widgetAddress: String,
        val selector: String,
        val values: JSONObject,
    ) : DikcizLuaAutomationAction

    data class LaunchApp(
        val component: String,
    ) : DikcizLuaAutomationAction

    data class AppAction(
        val action: DikcizAppActionType,
        val component: String,
    ) : DikcizLuaAutomationAction

    data class PostNotification(
        val title: String,
        val text: String,
    ) : DikcizLuaAutomationAction

    /**
     * Sends one SMS to a dialable recipient.
     *
     * Dikciz is not the default SMS app, so the message leaves the device but
     * is not written to the Android SMS provider and does not appear in the
     * owner's messaging history.
     */
    data class SendSms(
        val recipient: String,
        val body: String,
    ) : DikcizLuaAutomationAction

    data class ExplicitIntent(
        val intentType: DikcizAutomationIntentType,
        val component: String,
        val action: String?,
    ) : DikcizLuaAutomationAction

    data class MediaControl(
        val packageName: String,
        val command: DikcizMediaControlCommand,
        val positionMilliseconds: Long?,
    ) : DikcizLuaAutomationAction

    data class SetMediaVolume(
        val levelPercent: Int,
    ) : DikcizLuaAutomationAction

    data class NotificationControl(
        val actionToken: String,
    ) : DikcizLuaAutomationAction

    data class EmitEvent(
        val eventName: String,
        val payload: JSONObject,
        val coalescingKey: String?,
    ) : DikcizLuaAutomationAction

    data class AccessibilityAction(
        val snapshotID: String,
        val nodeID: String,
        val action: String,
        val text: String?,
    ) : DikcizLuaAutomationAction

    data class AccessibilityGesture(
        val gesture: DikcizAccessibilityGesture,
    ) : DikcizLuaAutomationAction

    data class AccessibilityGlobalAction(
        val action: DikcizAccessibilityGlobalAction,
    ) : DikcizLuaAutomationAction

    data object LockDevice : DikcizLuaAutomationAction
}

internal enum class DikcizMediaControlCommand(
    val persistedValue: String,
) {
    Play("play"),
    Pause("pause"),
    PlayPause("playPause"),
    SkipNext("skipNext"),
    SkipPrevious("skipPrevious"),
    SeekTo("seekTo"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizMediaControlCommand? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal enum class DikcizAutomationIntentType(
    val persistedValue: String,
) {
    Activity("activity"),
    Broadcast("broadcast"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAutomationIntentType? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal fun Context.hasAndroidSubscriptionPermission(
    subscription: DikcizAutomationSubscription,
    sensorType: Int? = null,
): Boolean {
    if (subscription.event == DikcizAutomationEventType.MediaSession) {
        return DikcizMediaSessionMonitor.hasAccess(this)
    }
    if (subscription.event == DikcizAutomationEventType.BluetoothState) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
    if (subscription.event == DikcizAutomationEventType.InterruptionFilter) {
        return hasNotificationPolicyAccess()
    }
    if (subscription.event == DikcizAutomationEventType.CalendarEvent) {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    if (subscription.event == DikcizAutomationEventType.ContactsChanged) {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    if (subscription.event == DikcizAutomationEventType.PhoneState) {
        return checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    if (subscription.event == DikcizAutomationEventType.SmsReceived) {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }
    if (subscription.event == DikcizAutomationEventType.Sensor) {
        if (sensorType != null) {
            return sensorType in subscription.sensorTypes &&
                DikcizAutomationSensorPermission.hasPermission(this, sensorType)
        }
        return subscription.sensorTypes.any { configuredSensorType ->
            DikcizAutomationSensorPermission.hasPermission(this, configuredSensorType)
        }
    }
    if (subscription.event != DikcizAutomationEventType.Location) {
        return true
    }
    return when (subscription.locationPrecision) {
        DikcizLocationPrecision.Approximate -> {
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        DikcizLocationPrecision.Precise -> {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        null -> false
    }
}

internal fun Context.hasNotificationPolicyAccess(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return false
    }
    val notificationManager = getSystemService(NotificationManager::class.java) ?: return false
    return notificationManager.isNotificationPolicyAccessGranted
}
