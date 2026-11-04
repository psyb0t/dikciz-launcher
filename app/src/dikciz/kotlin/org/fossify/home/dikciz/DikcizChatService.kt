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
    private lateinit var runtime: DikcizChatRuntime
    private var displayedProgress: DikcizChatRuntime.Progress? = null
    private val observer: () -> Unit = { updateProgress() }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        runtime = DikcizChatRuntime.get(this)
        runtime.observe(observer)
        promote()
    }

    private fun promote() {
        val notification = progressNotification(runtime.progress())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ACTIVE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ACTIVE, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        if (intent?.action == ACTION_STOP) {
            val conversationID = intent.getStringExtra("conversationId")
            val turnID = intent.getStringExtra("turnId")
            if (conversationID != null && turnID != null) runtime.stopTurn(conversationID, turnID)
        }
        updateProgress()
        if (!runtime.busy() && !runtime.hasRules()) {
            if (stopSelfResult(startId)) stopForeground(STOP_FOREGROUND_REMOVE)
            return START_NOT_STICKY
        }
        runtime.wake()
        return START_STICKY
    }

    override fun onDestroy() {
        runtime.unobserve(observer)
        super.onDestroy()
    }

    private fun updateProgress() {
        val progress = runtime.progress()
        if (displayedProgress == progress) return
        displayedProgress = progress
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ACTIVE, progressNotification(progress))
    }

    private fun progressNotification(progress: DikcizChatRuntime.Progress): Notification {
        val working = progress.conversationID != null && progress.turnID != null
        val builder = Notification.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.dikciz_brand_mark)
            .setContentTitle(if (working) "Dikciz is working" else "Dikciz event listener")
            .setContentText(if (working) progress.text else "Waiting for your configured events")
            .setContentIntent(openChat(this, progress.conversationID))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
        if (working) {
            val stop = PendingIntent.getService(this, 0,
                Intent(this, DikcizChatService::class.java)
                    .setAction(ACTION_STOP)
                    .setData(Uri.parse("dikciz-chat-stop:" + progress.turnID))
                    .putExtra("conversationId", progress.conversationID)
                    .putExtra("turnId", progress.turnID),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val ACTION_STOP = "org.fossify.home.dikciz.chat.STOP"
        private const val CHANNEL_ACTIVE = "dikciz-chat-active"
        private const val CHANNEL_REPLIES = "dikciz-chat-replies"
        private const val CHANNEL_SHORTCUT = "dikciz-chat-shortcut"
        private const val NOTIFICATION_ACTIVE = 78001
        private const val NOTIFICATION_REPLY = 78002
        private const val NOTIFICATION_SHORTCUT = 78003

        fun showShortcut(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            channels(context)
            val open = openChat(context, null)
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_SHORTCUT,
                Notification.Builder(context, CHANNEL_SHORTCUT)
                    .setSmallIcon(R.drawable.dikciz_brand_mark)
                    .setContentTitle("Dikciz chat")
                    .setContentText("Open chat over your current app")
                    .setContentIntent(open)
                    .addAction(Notification.Action.Builder(null, "Open chat", open).build())
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .build())
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DikcizChatService::class.java))
        }

        fun refresh(context: Context) {
            val runtime = DikcizChatRuntime.get(context)
            if (!runtime.busy() && !runtime.hasRules()) context.stopService(Intent(context, DikcizChatService::class.java))
        }

        fun notifyReply(context: Context, conversationID: String, outcome: String?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            channels(context)
            context.getSystemService(NotificationManager::class.java).notify(conversationID, NOTIFICATION_REPLY,
                Notification.Builder(context, CHANNEL_REPLIES)
                    .setSmallIcon(R.drawable.dikciz_brand_mark)
                    .setContentTitle(when (outcome) {
                        "cancelled" -> "Dikciz stopped"
                        "failed", "interrupted" -> "Dikciz needs attention"
                        else -> "Dikciz has a reply"
                    })
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
            manager.createNotificationChannel(NotificationChannel(CHANNEL_SHORTCUT, "Chat shortcut", NotificationManager.IMPORTANCE_LOW))
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
