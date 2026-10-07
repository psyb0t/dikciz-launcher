package org.fossify.home.dikciz

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.StringRes
import org.fossify.home.R
import org.json.JSONArray
import org.json.JSONObject

internal enum class DikcizDeviceAccessState(
    val persistedValue: String,
) {
    Granted("granted"),
    NeedsSetup("needsSetup"),
    Unavailable("unavailable"),
    OnDemand("onDemand"),
}

internal enum class DikcizDeviceAccessKind {
    RuntimePermission,
    SystemSettings,
    Role,
    DeviceAdministration,
    OnDemand,
}

internal enum class DikcizDeviceAccessRequirement(
    val persistedValue: String,
    val kind: DikcizDeviceAccessKind,
    @StringRes val titleResourceID: Int,
) {
    SharedStorage(
        "sharedStorage",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_shared_storage,
    ),
    LocationForeground(
        "locationForeground",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_location_foreground,
    ),
    LocationBackground(
        "locationBackground",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_location_background,
    ),
    CameraAndMicrophone(
        "cameraAndMicrophone",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_camera_and_microphone,
    ),
    Contacts(
        "contacts",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_contacts,
    ),
    Calendar(
        "calendar",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_calendar,
    ),
    Telephony(
        "telephony",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_telephony,
    ),
    CallHistory(
        "callHistory",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_call_history,
    ),
    Sms(
        "sms",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_sms,
    ),
    SmsRole(
        "smsRole",
        DikcizDeviceAccessKind.Role,
        R.string.dikciz_device_access_sms_role,
    ),
    Bluetooth(
        "bluetooth",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_bluetooth,
    ),
    NearbyWifi(
        "nearbyWifi",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_nearby_wifi,
    ),
    MediaLibrary(
        "mediaLibrary",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_media_library,
    ),
    Notifications(
        "notifications",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_notifications,
    ),
    ActivityRecognition(
        "activityRecognition",
        DikcizDeviceAccessKind.RuntimePermission,
        R.string.dikciz_device_access_activity_recognition,
    ),
    ExactAlarms(
        "exactAlarms",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_exact_alarms,
    ),
    Overlay(
        "overlay",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_overlay,
    ),
    SystemSettings(
        "systemSettings",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_system_settings,
    ),
    UsageAccess(
        "usageAccess",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_usage_access,
    ),
    IgnoreBatteryOptimizations(
        "ignoreBatteryOptimizations",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_ignore_battery_optimizations,
    ),
    NotificationListener(
        "notificationListener",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_notification_listener,
    ),
    NotificationPolicy(
        "notificationPolicy",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_notification_policy,
    ),
    Accessibility(
        "accessibility",
        DikcizDeviceAccessKind.SystemSettings,
        R.string.dikciz_device_access_accessibility,
    ),
    DeviceAdministration(
        "deviceAdministration",
        DikcizDeviceAccessKind.DeviceAdministration,
        R.string.dikciz_device_access_device_administration,
    ),
    RootShell(
        "rootShell",
        DikcizDeviceAccessKind.OnDemand,
        R.string.dikciz_device_access_root_shell,
    ),
}

internal data class DikcizDeviceAccessEntry(
    val requirement: DikcizDeviceAccessRequirement,
    val state: DikcizDeviceAccessState,
)

internal object DikcizDeviceAccess {
    fun entries(context: Context): List<DikcizDeviceAccessEntry> {
        return DikcizDeviceAccessRequirement.entries.map { requirement ->
            DikcizDeviceAccessEntry(requirement, state(context, requirement))
        }
    }

    fun nextSetupRequirement(context: Context): DikcizDeviceAccessRequirement? {
        return entries(context)
            .firstOrNull { entry -> entry.state == DikcizDeviceAccessState.NeedsSetup }
            ?.requirement
    }

    fun runtimePermissions(requirement: DikcizDeviceAccessRequirement): Array<String> {
        return permissions(requirement).toTypedArray()
    }

    fun settingsIntent(
        context: Context,
        requirement: DikcizDeviceAccessRequirement,
    ): Intent? {
        val packageUri = android.net.Uri.parse("package:${context.packageName}")
        return when (requirement) {
            DikcizDeviceAccessRequirement.SharedStorage -> {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
            }

            DikcizDeviceAccessRequirement.ExactAlarms -> {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            }

            DikcizDeviceAccessRequirement.Overlay -> {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            }

            DikcizDeviceAccessRequirement.SystemSettings -> {
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri)
            }

            DikcizDeviceAccessRequirement.UsageAccess -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            DikcizDeviceAccessRequirement.IgnoreBatteryOptimizations -> {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            }

            DikcizDeviceAccessRequirement.NotificationListener -> {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }

            DikcizDeviceAccessRequirement.NotificationPolicy -> {
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }

            DikcizDeviceAccessRequirement.Accessibility -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }

            else -> null
        }
    }

    fun smsRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            return null
        }
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }

    fun document(context: Context): JSONObject {
        val entries = entries(context)
        val actionableEntries = entries.filter { entry ->
            entry.state != DikcizDeviceAccessState.Unavailable &&
                entry.state != DikcizDeviceAccessState.OnDemand
        }
        val grantedCount = actionableEntries.count { entry ->
            entry.state == DikcizDeviceAccessState.Granted
        }
        return JSONObject()
            .put(KEY_GRANTED_COUNT, grantedCount)
            .put(KEY_REQUIREMENT_COUNT, actionableEntries.size)
            .put(
                KEY_REQUIREMENTS,
                JSONArray().apply {
                    entries.forEach { entry ->
                        put(
                            JSONObject()
                                .put(KEY_ID, entry.requirement.persistedValue)
                                .put(KEY_KIND, entry.requirement.kind.persistedValue)
                                .put(KEY_STATE, entry.state.persistedValue),
                        )
                    }
                },
            )
    }

    private fun state(
        context: Context,
        requirement: DikcizDeviceAccessRequirement,
    ): DikcizDeviceAccessState {
        if (requirement == DikcizDeviceAccessRequirement.RootShell) {
            return DikcizDeviceAccessState.OnDemand
        }
        if (!isSupported(requirement)) {
            return DikcizDeviceAccessState.Unavailable
        }
        if (requirement.kind == DikcizDeviceAccessKind.RuntimePermission) {
            return permissionState(context, permissions(requirement))
        }
        return if (hasSpecialAccess(context, requirement)) {
            DikcizDeviceAccessState.Granted
        } else {
            DikcizDeviceAccessState.NeedsSetup
        }
    }

    private fun isSupported(requirement: DikcizDeviceAccessRequirement): Boolean {
        return when (requirement) {
            DikcizDeviceAccessRequirement.LocationBackground,
            DikcizDeviceAccessRequirement.ActivityRecognition,
            -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            DikcizDeviceAccessRequirement.Bluetooth -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            DikcizDeviceAccessRequirement.NearbyWifi,
            DikcizDeviceAccessRequirement.Notifications,
            -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

            DikcizDeviceAccessRequirement.ExactAlarms -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            DikcizDeviceAccessRequirement.SmsRole -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            else -> true
        }
    }

    private fun permissions(requirement: DikcizDeviceAccessRequirement): List<String> {
        return when (requirement) {
            DikcizDeviceAccessRequirement.LocationForeground -> listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

            DikcizDeviceAccessRequirement.LocationBackground -> {
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            DikcizDeviceAccessRequirement.CameraAndMicrophone -> listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )

            DikcizDeviceAccessRequirement.Contacts -> listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
            )

            DikcizDeviceAccessRequirement.Calendar -> listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            )

            DikcizDeviceAccessRequirement.Telephony -> listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS,
            )

            DikcizDeviceAccessRequirement.CallHistory -> listOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
            )

            DikcizDeviceAccessRequirement.Sms -> listOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
            )

            DikcizDeviceAccessRequirement.Bluetooth -> listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )

            DikcizDeviceAccessRequirement.NearbyWifi -> {
                listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            DikcizDeviceAccessRequirement.MediaLibrary -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                    )
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            DikcizDeviceAccessRequirement.Notifications -> {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            }

            DikcizDeviceAccessRequirement.ActivityRecognition -> {
                listOf(Manifest.permission.ACTIVITY_RECOGNITION)
            }

            else -> emptyList()
        }
    }

    private fun permissionState(
        context: Context,
        permissions: List<String>,
    ): DikcizDeviceAccessState {
        if (permissions.isEmpty()) {
            return DikcizDeviceAccessState.Granted
        }
        val isGranted = permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        return if (isGranted) {
            DikcizDeviceAccessState.Granted
        } else {
            DikcizDeviceAccessState.NeedsSetup
        }
    }

    private fun hasSpecialAccess(
        context: Context,
        requirement: DikcizDeviceAccessRequirement,
    ): Boolean {
        return when (requirement) {
            DikcizDeviceAccessRequirement.SharedStorage -> hasSharedStorageAccess(context)
            DikcizDeviceAccessRequirement.ExactAlarms -> {
                val alarmManager = context.getSystemService(AlarmManager::class.java)
                alarmManager?.canScheduleExactAlarms() == true
            }

            DikcizDeviceAccessRequirement.Overlay -> Settings.canDrawOverlays(context)
            DikcizDeviceAccessRequirement.SystemSettings -> Settings.System.canWrite(context)
            DikcizDeviceAccessRequirement.UsageAccess -> hasUsageAccess(context)
            DikcizDeviceAccessRequirement.IgnoreBatteryOptimizations -> {
                val powerManager = context.getSystemService(PowerManager::class.java)
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }

            DikcizDeviceAccessRequirement.NotificationListener -> {
                DikcizAutomationStatus.hasNotificationListenerAccess(context)
            }

            DikcizDeviceAccessRequirement.NotificationPolicy -> context.hasNotificationPolicyAccess()
            DikcizDeviceAccessRequirement.Accessibility -> {
                DikcizAccessibilityAutomation.isEnabled(context)
            }
            DikcizDeviceAccessRequirement.DeviceAdministration -> {
                DikcizDeviceAdministration.isActive(context)
            }

            DikcizDeviceAccessRequirement.SmsRole -> hasSmsRole(context)
            else -> false
        }
    }

    private fun hasSharedStorageAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java) ?: return false
        return appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun hasSmsRole(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private val DikcizDeviceAccessKind.persistedValue: String
        get() = when (this) {
            DikcizDeviceAccessKind.RuntimePermission -> "runtimePermission"
            DikcizDeviceAccessKind.SystemSettings -> "systemSettings"
            DikcizDeviceAccessKind.Role -> "role"
            DikcizDeviceAccessKind.DeviceAdministration -> "deviceAdministration"
            DikcizDeviceAccessKind.OnDemand -> "onDemand"
        }

    private const val KEY_GRANTED_COUNT = "grantedCount"
    private const val KEY_ID = "id"
    private const val KEY_KIND = "kind"
    private const val KEY_REQUIREMENT_COUNT = "requirementCount"
    private const val KEY_REQUIREMENTS = "requirements"
    private const val KEY_STATE = "state"
}
