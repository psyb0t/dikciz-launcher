package org.fossify.home.dikciz

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizNotificationListenerService : NotificationListenerService() {
    private lateinit var mediaSessionMonitor: DikcizMediaSessionMonitor

    override fun onCreate() {
        super.onCreate()
        mediaSessionMonitor = DikcizMediaSessionMonitor(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaSessionMonitor.start()
    }

    override fun onListenerDisconnected() {
        mediaSessionMonitor.stop()
        DikcizNotificationActionRegistry.clear()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        mediaSessionMonitor.stop()
        DikcizNotificationActionRegistry.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (isOwnForegroundServiceNotification(notification)) {
            return
        }
        dispatch(
            DikcizAutomationEventType.NotificationPosted,
            notification,
            DikcizNotificationActionRegistry.replace(notification),
        )
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        if (isOwnForegroundServiceNotification(notification)) {
            return
        }
        DikcizNotificationActionRegistry.remove(notification.key)
        dispatch(DikcizAutomationEventType.NotificationRemoved, notification)
    }

    /**
     * Answers whether this is the launcher's own automation-service
     * notification, which is never an automation event.
     *
     * Android requires startForeground on every start command the service
     * receives, and each call re-posts this notification. Reporting it would
     * hand every script an event the launcher caused by configuring itself,
     * and a script that writes anything in response closes a loop: the write
     * reloads the configuration, the reload restarts the service, and the
     * service posts the notification again. Notifications this launcher posts
     * on a script's behalf are ordinary content and still reported.
     */
    private fun isOwnForegroundServiceNotification(
        notification: StatusBarNotification,
    ): Boolean {
        return notification.packageName == packageName &&
            notification.id == DikcizAutomationForegroundNotification.ID &&
            notification.notification.channelId == DikcizAutomationForegroundNotification.CHANNEL_ID
    }

    private fun dispatch(
        type: DikcizAutomationEventType,
        notification: StatusBarNotification,
        actionTokens: List<String> = emptyList(),
    ) {
        val extras = notification.notification.extras
        val payload = JSONObject()
            .put(PAYLOAD_CATEGORY, notification.notification.category ?: JSONObject.NULL)
            .put(PAYLOAD_NOTIFICATION_ID, notification.id)
            .put(PAYLOAD_NOTIFICATION_KEY, notification.key)
            .put(PAYLOAD_PACKAGE_NAME, notification.packageName)
            .put(PAYLOAD_POST_TIME, notification.postTime)
            .put(PAYLOAD_NOTIFICATION_TEXT, extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString())
            .put(PAYLOAD_NOTIFICATION_TITLE, extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString())
            .put(PAYLOAD_NOTIFICATION_ACTION_TOKENS, JSONArray(actionTokens))
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            DikcizAutomationEvent(
                type = type,
                source = "$SOURCE_NOTIFICATION_PREFIX${notification.packageName}",
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = "$COALESCING_NOTIFICATION_PREFIX${notification.key}",
                payload = payload,
            ),
        )
    }

    private companion object {
        const val COALESCING_NOTIFICATION_PREFIX = "notification:"
        const val PAYLOAD_CATEGORY = "category"
        const val PAYLOAD_NOTIFICATION_ACTION_TOKENS = "actionTokens"
        const val PAYLOAD_NOTIFICATION_ID = "notificationId"
        const val PAYLOAD_NOTIFICATION_KEY = "notificationKey"
        const val PAYLOAD_NOTIFICATION_TEXT = "text"
        const val PAYLOAD_NOTIFICATION_TITLE = "title"
        const val PAYLOAD_PACKAGE_NAME = "packageName"
        const val PAYLOAD_POST_TIME = "postTime"
        const val SOURCE_NOTIFICATION_PREFIX = "notification:"
    }
}
