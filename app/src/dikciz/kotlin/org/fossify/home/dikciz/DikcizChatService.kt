package org.fossify.home.dikciz

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.net.Uri
import org.fossify.home.R

internal class DikcizChatService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        val notification = Notification.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.dikciz_brand_mark)
            .setContentTitle("Dikciz agent")
            .setContentText("Handling your conversations and configured events")
            .setContentIntent(openChat(this, null))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ACTIVE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ACTIVE, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = DikcizChatRuntime.get(this)
        if (!runtime.busy() && !runtime.hasRules()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        runtime.wake()
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ACTIVE = "dikciz-chat-active"
        private const val CHANNEL_REPLIES = "dikciz-chat-replies"
        private const val NOTIFICATION_ACTIVE = 78001
        private const val NOTIFICATION_REPLY = 78002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DikcizChatService::class.java))
        }

        fun refresh(context: Context) {
            val runtime = DikcizChatRuntime.get(context)
            if (!runtime.busy() && !runtime.hasRules()) context.stopService(Intent(context, DikcizChatService::class.java))
        }

        fun notifyReply(context: Context, conversationID: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            channels(context)
            context.getSystemService(NotificationManager::class.java).notify(conversationID, NOTIFICATION_REPLY,
                Notification.Builder(context, CHANNEL_REPLIES)
                    .setSmallIcon(R.drawable.dikciz_brand_mark)
                    .setContentTitle("Dikciz has a reply")
                    .setContentText("Open your conversation to read it")
                    .setContentIntent(openChat(context, conversationID))
                    .setAutoCancel(true)
                    .build())
        }

        fun clearReply(context: Context, conversationID: String) {
            context.getSystemService(NotificationManager::class.java).cancel(conversationID, NOTIFICATION_REPLY)
        }

        private fun channels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ACTIVE, "Agent activity", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_REPLIES, "Agent replies", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun openChat(context: Context, conversationID: String?): PendingIntent {
            return PendingIntent.getActivity(context, conversationID?.hashCode() ?: 0,
                Intent(context, DikcizChatActivity::class.java).setData(Uri.parse("dikciz-chat:" + (conversationID ?: "home"))).putExtra("conversationId", conversationID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
