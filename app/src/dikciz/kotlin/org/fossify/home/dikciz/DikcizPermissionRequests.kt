package org.fossify.home.dikciz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.fossify.home.R
import org.json.JSONObject

internal class DikcizPermissionRequests(
    private val context: Context,
    private val logger: DikcizLogger,
    private val onPending: () -> Unit,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val recentRequests = mutableMapOf<DikcizDeviceAccessRequirement, Pair<Long, Boolean>>()

    @Synchronized
    fun request(requested: DikcizDeviceAccessRequirement, autoShow: Boolean = true): JSONObject {
        val requirement = if (
            requested == DikcizDeviceAccessRequirement.LocationBackground &&
            DikcizDeviceAccess.state(context, DikcizDeviceAccessRequirement.LocationForeground) != DikcizDeviceAccessState.Granted
        ) {
            DikcizDeviceAccessRequirement.LocationForeground
        } else {
            requested
        }
        val state = DikcizDeviceAccess.state(context, requirement)
        val result = JSONObject()
            .put(KEY_REQUIREMENT, requirement.persistedValue)
            .put("packageName", context.packageName)
            .put("permissionName", context.getString(requirement.titleResourceID))
            .put("requestedRequirementId", requested.persistedValue)
            .put(KEY_AUTO_REQUESTED, false)
            .put(KEY_DEDUPLICATED, false)
            .put(KEY_PROMPT_STATE, "not_requested")
            .put(KEY_STATE, state.persistedValue)
            .put(KEY_NOTIFICATION_POSTED, false)
            .put(KEY_RETRY_REQUIRED, state == DikcizDeviceAccessState.NeedsSetup)
        if (state != DikcizDeviceAccessState.NeedsSetup) {
            result.put(KEY_INSTRUCTIONS, if (state == DikcizDeviceAccessState.OnDemand)
                "This access is managed by the device or a root provider, not an Android runtime permission."
            else "No Android permission prompt was posted.")
            return result
        }
        if (!autoShow) {
            return result.put(KEY_STATE, "permission_required")
                .put(KEY_INSTRUCTIONS, "Android access is missing. Set autoRequestPermissions to true or call requestDeviceAccess to ask the phone owner. Then check access and retry the authorized operation.")
        }
        val now = SystemClock.elapsedRealtime()
        val recent = recentRequests[requirement]
        val duplicate = recent != null && now - recent.first < COOLDOWN_MILLISECONDS
        val pendingNow = preferences.getStringSet(KEY_PENDING, emptySet()).orEmpty()
        if (duplicate && requirement.persistedValue !in pendingNow) {
            return result.put(KEY_STATE, "request_dismissed")
                .put(KEY_AUTO_REQUESTED, true)
                .put(KEY_DEDUPLICATED, true)
                .put(KEY_PROMPT_STATE, "dismissed")
                .put(KEY_INSTRUCTIONS, "The previous permission review was closed. Access is still missing and no operation ran. Ask the owner before requesting it again after the 30-second cooldown.")
        }
        if (duplicate) {
            preferences.edit().putString(KEY_PRIORITY, requirement.persistedValue).apply()
            onPending()
        }
        val posted = if (duplicate) recent!!.second && notificationsAvailable() else {
            val pending = preferences.getStringSet(KEY_PENDING, emptySet()).orEmpty().toMutableSet()
            pending.add(requirement.persistedValue)
            preferences.edit().putStringSet(KEY_PENDING, pending).putString(KEY_PRIORITY, requirement.persistedValue).apply()
            postNotification(requirement).also { success ->
                recentRequests[requirement] = now to success
                onPending()
            }
        }
        logger.info("remote_permission_requested", mapOf(
            "requirement_id" to requirement.persistedValue,
            "notification_posted" to posted,
            KEY_DEDUPLICATED to duplicate,
        ))
        return result
            .put(KEY_AUTO_REQUESTED, true)
            .put(KEY_STATE, "user_action_required")
            .put(KEY_NOTIFICATION_POSTED, posted)
            .put(KEY_DEDUPLICATED, duplicate)
            .put(KEY_PROMPT_STATE, if (posted) "notification_posted" else "queued_for_foreground")
            .put(KEY_INSTRUCTIONS, if (posted)
                "Check your phone. Tap Allow access in the Dikciz permission prompt or notification, review the request and grant access in Android. Then retry the original operation."
            else "Check your phone and open Dikciz. Review the pending access request and tap Allow access. Notifications are unavailable or blocked. Then retry the original operation.")
    }

    fun forFailure(code: String, autoShow: Boolean): JSONObject? {
        val requirement = when (code) {
            "native_permission_cameraAndMicrophone" -> DikcizDeviceAccessRequirement.CameraAndMicrophone
            "native_permission_bodySensors" -> DikcizDeviceAccessRequirement.BodySensors
            "native_permission_activityRecognition" -> DikcizDeviceAccessRequirement.ActivityRecognition
            "native_permission_contacts" -> DikcizDeviceAccessRequirement.Contacts
            "native_permission_sms" -> DikcizDeviceAccessRequirement.Sms
            "native_permission_calendar" -> DikcizDeviceAccessRequirement.Calendar
            "native_permission_telephony" -> DikcizDeviceAccessRequirement.Telephony
            "native_permission_callHistory" -> DikcizDeviceAccessRequirement.CallHistory
            "native_permission_mediaLibrary" -> DikcizDeviceAccessRequirement.MediaLibrary
            "native_permission_locationForeground" -> DikcizDeviceAccessRequirement.LocationForeground
            "native_permission_locationBackground" -> DikcizDeviceAccessRequirement.LocationBackground
            "native_permission_notificationListener" -> DikcizDeviceAccessRequirement.NotificationListener
            "accessibility_not_enabled" -> DikcizDeviceAccessRequirement.Accessibility
            DikcizFileCommands.ERROR_ACCESS_DENIED -> DikcizDeviceAccessRequirement.SharedStorage
            "device_admin_inactive" -> DikcizDeviceAccessRequirement.DeviceAdministration
            "root_unavailable" -> return request(DikcizDeviceAccessRequirement.RootShell, autoShow)
            else -> return null
        }
        if (DikcizDeviceAccess.state(context, requirement) != DikcizDeviceAccessState.NeedsSetup) return null
        return request(requirement, autoShow)
    }

    fun forAction(type: String, outcome: String, autoShow: Boolean): JSONObject? {
        forFailure(outcome, autoShow)?.let { return it }
        if (outcome != "android_permission_denied") return null
        val requirement = when (type) {
            "sendSms" -> DikcizDeviceAccessRequirement.Sms
            "postNotification" -> DikcizDeviceAccessRequirement.Notifications
            "notificationControl", "mediaControl" -> DikcizDeviceAccessRequirement.NotificationListener
            else -> return null
        }
        if (DikcizDeviceAccess.state(context, requirement) != DikcizDeviceAccessState.NeedsSetup) return null
        return request(requirement, autoShow)
    }

    @Synchronized
    fun peekPending(): DikcizDeviceAccessRequirement? {
        val pending = preferences.getStringSet(KEY_PENDING, emptySet()).orEmpty().toMutableSet()
        val requested = (context as? android.app.Activity)?.intent?.getStringExtra(EXTRA_REQUIREMENT)
            ?: preferences.getString(KEY_PRIORITY, null)
        (context as? android.app.Activity)?.intent?.removeExtra(EXTRA_REQUIREMENT)
        val requirement = DikcizDeviceAccessRequirement.entries.firstOrNull {
            it.persistedValue == requested && it.persistedValue in pending
        } ?: DikcizDeviceAccessRequirement.entries.firstOrNull { it.persistedValue in pending }
        if (requirement != null) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + requirement.ordinal)
        }
        return requirement
    }

    @Synchronized
    fun clearPending(requirement: DikcizDeviceAccessRequirement) {
        val pending = preferences.getStringSet(KEY_PENDING, emptySet()).orEmpty().toMutableSet()
        pending.remove(requirement.persistedValue)
        preferences.edit().putStringSet(KEY_PENDING, pending).apply()
    }

    private fun notificationsAvailable(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun postNotification(requirement: DikcizDeviceAccessRequirement): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.dikciz_permission_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
            val review = PendingIntent.getActivity(
                context,
                NOTIFICATION_BASE + requirement.ordinal,
                Intent(context, DikcizHomeActivity::class.java)
                    .setAction(ACTION_REVIEW)
                    .putExtra(EXTRA_REQUIREMENT, requirement.persistedValue)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = context.getString(R.string.dikciz_permission_message, context.getString(requirement.titleResourceID))
            manager.notify(NOTIFICATION_BASE + requirement.ordinal,
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(context.getString(R.string.dikciz_permission_title))
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(review)
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.dikciz_permission_allow), review).build())
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build(),
            )
            true
        } catch (exception: RuntimeException) {
            logger.warn("remote_permission_notification_failed", mapOf("error_class" to exception.javaClass.simpleName))
            false
        }
    }

    companion object {
        const val TYPE_REQUEST = "requestDeviceAccess"
        const val TOOL_REQUEST = "dikciz_request_device_access"
        const val KEY_INSTRUCTIONS = "instructions"
        private const val KEY_STATE = "state"
        private const val KEY_AUTO_REQUESTED = "autoRequested"
        private const val KEY_DEDUPLICATED = "deduplicated"
        private const val KEY_PROMPT_STATE = "promptState"
        private const val KEY_RETRY_REQUIRED = "retryRequired"
        const val KEY_REQUIREMENT = "requirementId"
        const val KEY_RECOVERY = "permissionRequest"
        const val KEY_AUTO_REQUEST = "autoRequestPermissions"
        const val SEMANTIC_MESSAGE = "permission:message"
        const val SEMANTIC_ALLOW = "permission:allow"
        const val SEMANTIC_DISMISS = "permission:dismiss"
        private const val KEY_NOTIFICATION_POSTED = "notificationPosted"
        private const val KEY_PRIORITY = "priority"
        private const val KEY_PENDING = "pending"
        private const val PREFERENCES = "dikciz_permission_requests"
        private const val CHANNEL_ID = "dikciz_permission_requests"
        private const val ACTION_REVIEW = "org.fossify.home.dikciz.REVIEW_PERMISSION"
        private const val EXTRA_REQUIREMENT = "dikciz_permission_requirement"
        private const val NOTIFICATION_BASE = 7500
        private const val COOLDOWN_MILLISECONDS = 30_000L

        fun operation(request: JSONObject): JSONObject {
            autoShow(request)
            return JSONObject(request.toString()).apply { remove(KEY_AUTO_REQUEST) }
        }

        private fun autoShow(request: JSONObject): Boolean {
            if (!request.has(KEY_AUTO_REQUEST)) return false
            return request.opt(KEY_AUTO_REQUEST) as? Boolean
                ?: throw DikcizAutomationRequestException("validation_failed", "autoRequestPermissions must be a Boolean")
        }

        fun command(type: String, request: JSONObject): DikcizAutomationCommand {
            return DikcizAutomationCommand(type, operation(request), autoShow(request))
        }

        fun requirement(request: JSONObject): DikcizDeviceAccessRequirement {
            val id = request.opt(KEY_REQUIREMENT)
            return DikcizDeviceAccessRequirement.entries.firstOrNull { it.persistedValue == id }
                ?: throw DikcizAutomationRequestException("validation_failed", "requirementId must name a deviceAccess requirement from automationStatus")
        }
    }
}
