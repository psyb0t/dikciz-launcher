package org.fossify.home.dikciz

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.fossify.home.R

internal enum class DikcizScriptKind(
    val persistedValue: String,
) {
    Automation("automation"),
    Widget("widget"),
}

internal object DikcizScriptFailureNotifier {
    private val lastNotificationElapsedMilliseconds = mutableMapOf<String, Long>()

    fun report(
        context: Context,
        logger: DikcizLogger,
        logging: DikcizLoggingConfiguration,
        scriptID: String,
        scriptKind: DikcizScriptKind,
        failure: DikcizLuaFailure,
    ) {
        val fields = mapOf(
            FIELD_REASON to failure.persistedValue,
            FIELD_SCRIPT_ID to scriptID,
            FIELD_SCRIPT_KIND to scriptKind.persistedValue,
        )
        if (failure == DikcizLuaFailure.Cancelled) {
            logger.debug(EVENT_SCRIPT_EXECUTION_CANCELLED, fields)
            return
        }
        reportFailure(
            context = context,
            logger = logger,
            logging = logging,
            scriptID = scriptID,
            scriptKind = scriptKind,
            failureReason = failure.persistedValue,
        )
    }

    fun reportActionFailure(
        context: Context,
        logger: DikcizLogger,
        logging: DikcizLoggingConfiguration,
        scriptID: String,
        scriptKind: DikcizScriptKind,
        failureReason: String,
    ) {
        reportFailure(
            context = context,
            logger = logger,
            logging = logging,
            scriptID = scriptID,
            scriptKind = scriptKind,
            failureReason = failureReason,
        )
    }

    private fun reportFailure(
        context: Context,
        logger: DikcizLogger,
        logging: DikcizLoggingConfiguration,
        scriptID: String,
        scriptKind: DikcizScriptKind,
        failureReason: String,
    ) {
        val fields = mapOf(
            FIELD_REASON to failureReason,
            FIELD_SCRIPT_ID to scriptID,
            FIELD_SCRIPT_KIND to scriptKind.persistedValue,
        )
        logger.error(EVENT_SCRIPT_EXECUTION_FAILED, fields)
        if (!logging.notifyOnScriptError) {
            logger.debug(
                EVENT_SCRIPT_ERROR_NOTIFICATION_SKIPPED,
                fields + (FIELD_NOTIFICATION_REASON to REASON_DISABLED_BY_CONFIGURATION),
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            logger.warn(
                EVENT_SCRIPT_ERROR_NOTIFICATION_SKIPPED,
                fields + (FIELD_NOTIFICATION_REASON to REASON_ANDROID_PERMISSION_DENIED),
            )
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            logger.warn(
                EVENT_SCRIPT_ERROR_NOTIFICATION_SKIPPED,
                fields + (FIELD_NOTIFICATION_REASON to REASON_NOTIFICATION_MANAGER_UNAVAILABLE),
            )
            return
        }
        val notificationKey = notificationKey(scriptKind, scriptID)
        val notificationElapsedMilliseconds = SystemClock.elapsedRealtime()
        if (!reserveNotification(
                notificationKey,
                notificationElapsedMilliseconds,
                logging.scriptErrorNotificationMinimumIntervalMilliseconds,
            )
        ) {
            logger.debug(
                EVENT_SCRIPT_ERROR_NOTIFICATION_SKIPPED,
                fields + (FIELD_NOTIFICATION_REASON to REASON_RATE_LIMITED),
            )
            return
        }
        try {
            createNotificationChannel(context, notificationManager)
            notificationManager.notify(
                notificationKey.hashCode(),
                NotificationCompat.Builder(context, SCRIPT_ERROR_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(context.getString(R.string.dikciz_script_error_notification_title))
                    .setContentText(
                        context.getString(
                            R.string.dikciz_script_error_notification_text,
                            scriptID,
                            failureReason,
                        ),
                    )
                    .setAutoCancel(true)
                    .build(),
            )
            logger.info(EVENT_SCRIPT_ERROR_NOTIFICATION_POSTED, fields)
        } catch (exception: SecurityException) {
            releaseNotification(notificationKey, notificationElapsedMilliseconds)
            logger.warn(
                EVENT_SCRIPT_ERROR_NOTIFICATION_SKIPPED,
                fields + (FIELD_NOTIFICATION_REASON to REASON_ANDROID_REJECTED),
            )
        }
    }

    private fun createNotificationChannel(
        context: Context,
        notificationManager: NotificationManager,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                SCRIPT_ERROR_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.dikciz_script_error_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun notificationKey(scriptKind: DikcizScriptKind, scriptID: String): String {
        return "${scriptKind.persistedValue}:$scriptID"
    }

    private fun reserveNotification(
        notificationKey: String,
        now: Long,
        minimumIntervalMilliseconds: Int,
    ): Boolean {
        synchronized(lastNotificationElapsedMilliseconds) {
            val previous = lastNotificationElapsedMilliseconds[notificationKey]
            if (previous != null && now - previous < minimumIntervalMilliseconds.toLong()) {
                return false
            }
            lastNotificationElapsedMilliseconds[notificationKey] = now
            return true
        }
    }

    private fun releaseNotification(notificationKey: String, elapsedMilliseconds: Long) {
        synchronized(lastNotificationElapsedMilliseconds) {
            if (lastNotificationElapsedMilliseconds[notificationKey] == elapsedMilliseconds) {
                lastNotificationElapsedMilliseconds.remove(notificationKey)
            }
        }
    }

    private const val EVENT_SCRIPT_ERROR_NOTIFICATION_POSTED = "script_error_notification_posted"
    private const val EVENT_SCRIPT_ERROR_NOTIFICATION_SKIPPED = "script_error_notification_skipped"
    private const val EVENT_SCRIPT_EXECUTION_CANCELLED = "script_execution_cancelled"
    private const val EVENT_SCRIPT_EXECUTION_FAILED = "script_execution_failed"
    private const val FIELD_NOTIFICATION_REASON = "notification_reason"
    private const val FIELD_REASON = "reason"
    private const val FIELD_SCRIPT_ID = "script_id"
    private const val FIELD_SCRIPT_KIND = "script_kind"
    private const val REASON_ANDROID_PERMISSION_DENIED = "android_permission_denied"
    private const val REASON_ANDROID_REJECTED = "android_rejected"
    private const val REASON_DISABLED_BY_CONFIGURATION = "disabled_by_configuration"
    private const val REASON_NOTIFICATION_MANAGER_UNAVAILABLE = "notification_manager_unavailable"
    private const val REASON_RATE_LIMITED = "rate_limited"
    private const val SCRIPT_ERROR_NOTIFICATION_CHANNEL_ID = "dikciz-script-errors"
}
