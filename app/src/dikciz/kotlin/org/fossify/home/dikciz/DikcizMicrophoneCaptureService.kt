package org.fossify.home.dikciz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.fossify.home.R

internal class DikcizMicrophoneCaptureService : Service() {
    private var streamId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedId = intent?.getStringExtra(EXTRA_STREAM_ID)
        if (intent?.action == ACTION_OWNER_STOP) {
            requestedId?.let(DikcizMicrophoneStreams::stopFromOwner)
            if (streamId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            if (streamId == null || streamId == requestedId) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (requestedId == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        streamId = requestedId
        try {
            val notification = notification(requestedId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            if (!DikcizMicrophoneStreams.serviceReady(requestedId)) stopSelfResult(startId)
        } catch (exception: RuntimeException) {
            DikcizMicrophoneStreams.serviceFailed(requestedId, exception)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        streamId?.let(DikcizMicrophoneStreams::serviceDestroyed)
        super.onDestroy()
    }

    private fun notification(id: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
            ?: error(MESSAGE_NOTIFICATION_SERVICE_UNAVAILABLE)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, getString(R.string.dikciz_microphone_stream_channel), NotificationManager.IMPORTANCE_LOW,
        ))
        val stop = PendingIntent.getService(
            this, NOTIFICATION_ID,
            Intent(this, DikcizMicrophoneCaptureService::class.java)
                .setAction(ACTION_OWNER_STOP).putExtra(EXTRA_STREAM_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, NOTIFICATION_ID,
            Intent(this, DikcizHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dikciz_app_name))
            .setContentText(getString(R.string.dikciz_microphone_stream_active))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                getString(R.string.dikciz_microphone_stream_stop), stop,
            ).build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 7303
        private const val CHANNEL_ID = "dikciz-microphone-stream"
        private const val EXTRA_STREAM_ID = "stream_id"
        private const val ACTION_START = "org.fossify.home.dikciz.MICROPHONE_STREAM_START"
        private const val ACTION_STOP = "org.fossify.home.dikciz.MICROPHONE_STREAM_STOP"
        internal const val ACTION_OWNER_STOP = "org.fossify.home.dikciz.MICROPHONE_STREAM_OWNER_STOP"
        private const val MESSAGE_NOTIFICATION_SERVICE_UNAVAILABLE = "Android notification service unavailable"

        fun start(context: Context, id: String) {
            context.startForegroundService(Intent(context, DikcizMicrophoneCaptureService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_STREAM_ID, id))
        }

        fun stop(context: Context, id: String) {
            // Queue stop after startup so a pending foreground start is satisfied first.
            context.startService(Intent(context, DikcizMicrophoneCaptureService::class.java)
                .setAction(ACTION_STOP).putExtra(EXTRA_STREAM_ID, id))
        }
    }
}
