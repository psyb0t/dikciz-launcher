package org.fossify.home.dikciz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import org.fossify.home.R

class DikcizPackageUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val logger = DikcizLogger(LOG_TAG, HomeConfigStore(context).logDirectory)
        logger.info(EVENT_UPDATE_RECEIVED)
        val launch = Intent(context, DikcizHomeActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        postRecoveryNotification(context, launch, logger)
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val canOpen = home?.activityInfo?.packageName == context.packageName ||
            Settings.canDrawOverlays(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        if (!canOpen) {
            logger.warn(EVENT_OPEN_REQUIRED)
            return
        }
        try {
            context.startActivity(launch)
            logger.info(EVENT_RESTART_REQUESTED)
        } catch (exception: RuntimeException) {
            // OEM activity policies can reject startup even when the standard exemption applies.
            logger.error(EVENT_RESTART_FAILED, mapOf(FIELD_REASON to exception.javaClass.simpleName))
        }
    }

    private fun postRecoveryNotification(context: Context, launch: Intent, logger: DikcizLogger) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            logger.warn(EVENT_NOTIFICATION_UNAVAILABLE)
            return
        }
        try {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.dikciz_update_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val open = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(context.getString(R.string.dikciz_update_title))
                .setContentText(context.getString(R.string.dikciz_update_open))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (exception: RuntimeException) {
            // Notification denial must not prevent an otherwise permitted restart.
            logger.warn(EVENT_NOTIFICATION_UNAVAILABLE, mapOf(FIELD_REASON to exception.javaClass.simpleName))
        }
    }

    companion object {
        private const val LOG_TAG = "DikcizPackageUpdate"
        private const val CHANNEL_ID = "dikciz_package_updates"
        private const val NOTIFICATION_ID = 7402
        private const val FIELD_REASON = "reason"
        private const val EVENT_UPDATE_RECEIVED = "package_update_received"
        private const val EVENT_RESTART_REQUESTED = "package_update_restart_requested"
        private const val EVENT_RESTART_FAILED = "package_update_restart_failed"
        private const val EVENT_OPEN_REQUIRED = "package_update_open_required"
        private const val EVENT_NOTIFICATION_UNAVAILABLE = "package_update_notification_unavailable"

        internal fun clearRecoveryNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
