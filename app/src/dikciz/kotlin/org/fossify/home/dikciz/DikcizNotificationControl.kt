package org.fossify.home.dikciz

import android.content.Context
import org.json.JSONObject

internal class DikcizNotificationControl(private val context: Context) {
    fun execute(request: JSONObject): JSONObject {
        if (!DikcizAutomationStatus.hasNotificationListenerAccess(context)) {
            DikcizNativeCommands.failure("native_permission_notificationListener", "Enable Android notification-listener access")
        }
        val action = request.getString(KEY_ACTION)
        if (action == ACTION_REPLY) {
            return when (DikcizNotificationActionRegistry.reply(context, request.getString(KEY_TOKEN), request.getString(KEY_TEXT))) {
                DikcizNotificationActionResult.Executed -> JSONObject().put("state", "submitted").put("delivered", false)
                DikcizNotificationActionResult.TargetUnavailable -> DikcizNativeCommands.failure("notification_action_unavailable", "Notification action expired, was consumed or is no longer available")
                DikcizNotificationActionResult.AndroidRejected -> DikcizNativeCommands.failure("notification_action_rejected", "Android rejected this notification action")
                DikcizNotificationActionResult.ReplyUnsupported -> DikcizNativeCommands.failure("notification_reply_unsupported", "This action does not accept free-form inline replies")
            }
        }
        return DikcizNotificationListenerService.control(
            request.getString(KEY_NOTIFICATION),
            if (action == ACTION_SNOOZE) request.getLong(KEY_DURATION) else null,
        )
    }

    companion object {
        const val TYPE = "notificationManage"
        const val KEY_ACTION = "action"
        const val KEY_NOTIFICATION = "notificationKey"
        const val KEY_TOKEN = "actionToken"
        const val KEY_TEXT = "text"
        const val KEY_DURATION = "durationMilliseconds"
        const val ACTION_REPLY = "reply"
        const val ACTION_SNOOZE = "snooze"
        const val ACTION_DISMISS = "dismiss"
        private const val MAXIMUM_SNOOZE = 604_800_000
        val DEFINITIONS = listOf(DikcizNativeCommands.Definition(
            TYPE, "dikciz_notification_manage",
            "Dismiss or snooze an active notification, or submit a free-form inline reply through a short-lived action token from notificationsList.actions. Submission does not confirm remote delivery. Android listener access is required.",
            mapOf(
                KEY_ACTION to text("dismiss, snooze or reply.", 16),
                KEY_NOTIFICATION to text("Exact key from notificationsList; dismiss/snooze only.", 4096),
                KEY_TOKEN to text("Single-use action token supporting inline reply; reply only.", 128),
                KEY_TEXT to text("Nonblank reply text; reply only.", DikcizNativeCommands.MAXIMUM_TEXT),
                KEY_DURATION to JSONObject().put("type", "integer").put("minimum", 1000).put("maximum", MAXIMUM_SNOOZE),
            ), setOf(KEY_ACTION), readOnly = false,
        ))

        fun validate(request: JSONObject) {
            val action = request.getString(KEY_ACTION)
            val required = when (action) {
                ACTION_REPLY -> setOf(KEY_TOKEN, KEY_TEXT)
                ACTION_SNOOZE -> setOf(KEY_NOTIFICATION, KEY_DURATION)
                ACTION_DISMISS -> setOf(KEY_NOTIFICATION)
                else -> DikcizNativeCommands.invalid("Unsupported notification action")
            }
            val fields = setOf(KEY_NOTIFICATION, KEY_TOKEN, KEY_TEXT, KEY_DURATION)
            if (required.any { !request.has(it) } || fields.any { request.has(it) && it !in required }) {
                DikcizNativeCommands.invalid("Notification action fields do not match the selected action")
            }
            if (required.any { it != KEY_DURATION && request.getString(it).isBlank() }) {
                DikcizNativeCommands.invalid("Notification action values cannot be blank")
            }
        }

        private fun text(description: String, maximum: Int) = JSONObject()
            .put("type", "string").put("description", description).put("maxLength", maximum)
    }
}
