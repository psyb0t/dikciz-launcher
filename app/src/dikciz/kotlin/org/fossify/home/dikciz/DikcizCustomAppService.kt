package org.fossify.home.dikciz

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import org.json.JSONObject

/** Visible native lifetime, monotonic alarms and window hosting for every custom app. */
internal class DikcizCustomAppService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var logger: DikcizLogger
    private lateinit var warning: DikcizSosWarning
    private var lastAlert: JSONObject? = null
    private var busy = false
    private var tickRequested = false
    private var startId = 0
    private var alarmAt = 0L
    private var episode = ""
    private val tick = object : Runnable {
        override fun run() {
            if (!running || busy) return
            busy = true
            val currentStartId = startId
            worker.execute {
                var alive = false
                var alert: JSONObject? = null
                var deadline = 0L
                var alertRevision = -1L
                try {
                    DikcizCustomApps.deliverDue(applicationContext)
                    alertRevision = DikcizCustomApps.alertRevision.get()
                    alert = DikcizCustomApps.alert(applicationContext)
                    alert?.let { value ->
                        if (value.optInt("boot") != DikcizCustomApps.boot(applicationContext) ||
                            value.getLong("deadlineElapsed") + ALERT_EXPIRY_MILLISECONDS < SystemClock.elapsedRealtime()) {
                            DikcizCustomApps.closeAlert(applicationContext, value.getString("appId")); alert = null
                        } else value.put("remainingSeconds", ((value.getLong("deadlineElapsed") - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000)
                    }
                    val pending = DikcizCustomApps.timers(applicationContext).filter { it.optString("state") == "pending" }
                    deadline = pending.minOfOrNull { it.getLong("deadlineElapsed") } ?: 0
                    alive = pending.isNotEmpty() || alert != null || DikcizSensorStreams.usesBackgroundService()
                } catch (exception: Exception) {
                    logger.error("custom_app_service_failed", mapOf("error_class" to exception.javaClass.simpleName))
                }
                val observed = alert
                handler.post {
                    busy = false
                    if (!running) return@post
                    if (alertRevision == DikcizCustomApps.alertRevision.get()) {
                        lastAlert = observed
                        warning.update(observed)
                        updateAlertNotification(observed)
                    }
                    if (tickRequested || currentStartId != startId) {
                        tickRequested = false
                        handler.post(this)
                        return@post
                    }
                    if (!alive && stopSelfResult(currentStartId)) return@post
                    updateAlarm(deadline)
                    val timerDelay = if (deadline == 0L) Long.MAX_VALUE
                        else (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                    val delay = if (observed != null) minOf(timerDelay, TICK_MILLISECONDS) else timerDelay
                    if (delay != Long.MAX_VALUE) handler.postDelayed(this, delay)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        logger = DikcizLogger("DikcizAppHost", HomeConfigStore(this).logDirectory)
        warning = DikcizSosWarning(this, logger, ::cancelAlert)
        DikcizSosWarning.createChannel(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Custom app background work", NotificationManager.IMPORTANCE_LOW))
        worker.execute { DikcizCustomApps.recover(applicationContext) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, currentId: Int): Int {
        startId = currentId
        try {
            var types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            if (intent?.getBooleanExtra(LOCATION, false) == true) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && types != 0) startForeground(NOTIFICATION_ID, notification(), types)
            else startForeground(NOTIFICATION_ID, notification())
        } catch (exception: SecurityException) {
            return rejectStart(exception, currentId)
        } catch (exception: RuntimeException) {
            return rejectStart(exception, currentId)
        }
        running = true
        when (intent?.action) {
            ACTION_STOP -> {
                DikcizCustomApps.signalAllCancellations()
                worker.execute {
                    DikcizCustomApps.pausePending(applicationContext)
                    handler.post { stopSelfResult(currentId) }
                }
            }
            ACTION_CANCEL -> cancelAlert()
        }
        requestTick()
        return START_STICKY
    }

    private fun requestTick() {
        if (!running) return
        handler.removeCallbacks(tick)
        if (busy) tickRequested = true else handler.post(tick)
    }

    private fun rejectStart(exception: RuntimeException, currentId: Int): Int {
        logger.warn("custom_app_foreground_rejected", mapOf("error_class" to exception.javaClass.simpleName))
        worker.execute { DikcizCustomApps.cancelTimers(applicationContext, null, "foreground_start_rejected") }
        stopSelfResult(currentId)
        return START_NOT_STICKY
    }

    private fun cancelAlert() {
        val observed = lastAlert
        observed?.optString("appId")?.let(DikcizCustomApps::signalCancellation)
        worker.execute {
            try {
                val alert = DikcizCustomApps.alert(applicationContext) ?: return@execute
                val appId = alert.getString("appId")
                val episodeId = alert.getString("episodeId")
                if (observed != null && (observed.optString("appId") != appId ||
                        observed.optString("episodeId") != episodeId)) return@execute
                DikcizCustomApps.signalCancellation(appId)
                val request = JSONObject().put("type", DikcizCustomAppCommands.EVENT)
                    .put("appId", appId).put("event", alert.getString("cancelEvent"))
                    .put("payload", JSONObject().put("episodeId", episodeId))
                DikcizCustomApps.execute(
                    applicationContext, DikcizCustomAppCommands.EVENT, request, retryBeforeCommands = true,
                )
                DikcizCustomApps.closeAlert(applicationContext, appId, episodeId)
            } catch (exception: Exception) {
                logger.error("custom_app_alert_cancel_failed", mapOf("error_class" to exception.javaClass.simpleName))
                handler.post { warning.cancelFailed() }
            }
        }
    }

    private fun updateAlarm(deadline: Long) {
        if (deadline == alarmAt) return
        alarmAt = deadline
        val manager = getSystemService(AlarmManager::class.java)
        val pending = alarm(this)
        manager.cancel(pending)
        if (deadline == 0L) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending)
        } else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, NOTIFICATION_ID, Intent(this, DikcizCustomAppActivity::class.java), FLAGS)
        val stop = PendingIntent.getService(this, NOTIFICATION_ID, Intent(this, javaClass).setAction(ACTION_STOP), FLAGS)
        val detail = if (DikcizSensorStreams.usesBackgroundService()) "Background sensor capture is active."
            else "Scheduled background work. Tap to inspect apps."
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Dikciz background work").setContentText(detail)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Pause apps", stop).build()).build()
    }

    private fun updateAlertNotification(value: JSONObject?) {
        val manager = getSystemService(NotificationManager::class.java)
        if (value == null) { manager.cancel(DikcizSosWarning.NOTIFICATION_ID); episode = ""; return }
        val open = PendingIntent.getActivity(this, DikcizSosWarning.NOTIFICATION_ID,
            DikcizCustomAppHostActivity.intent(this, value.getString("appId")), FLAGS)
        val cancel = PendingIntent.getService(this, DikcizSosWarning.NOTIFICATION_ID, Intent(this, javaClass).setAction(ACTION_CANCEL), FLAGS)
        manager.notify(DikcizSosWarning.NOTIFICATION_ID, Notification.Builder(this, DikcizSosWarning.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(value.getString("title"))
            .setContentText(value.getString("detail")).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(value.getLong("deadlineAt")).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(episode == value.getString("episodeId")).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, value.getString("cancelLabel"), cancel).build()).build())
        episode = value.getString("episodeId")
    }

    override fun onConfigurationChanged(configuration: android.content.res.Configuration) { super.onConfigurationChanged(configuration); warning.relayout() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (activeService === this) activeService = null
        running = false
        DikcizSensorStreams.stopBackground("foreground_unavailable")
        handler.removeCallbacksAndMessages(null)
        warning.close()
        getSystemService(NotificationManager::class.java).cancel(DikcizSosWarning.NOTIFICATION_ID)
        getSystemService(AlarmManager::class.java).cancel(alarm(this))
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "dikciz-custom-apps"
        private const val NOTIFICATION_ID = 7330
        private const val TICK_MILLISECONDS = 1000L
        private const val ALERT_EXPIRY_MILLISECONDS = 60000L
        private const val LOCATION = "locationMonitoring"
        private const val ACTION_STOP = "dikciz.custom.apps.STOP"
        private const val ACTION_CANCEL = "dikciz.custom.apps.CANCEL"
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        @Volatile var running = false
            private set
        fun start(context: Context) {
            context.startForegroundService(Intent(context, DikcizCustomAppService::class.java).putExtra(LOCATION, DikcizCustomApps.locationMonitoring(context)))
        }
        @android.annotation.SuppressLint("StaticFieldLeak")
        private var activeService: DikcizCustomAppService? = null
        private val main = Handler(Looper.getMainLooper())

        fun resourcesChanged() {
            main.post { activeService?.requestTick() }
        }
        fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID,
            Intent(context, DikcizCustomAppAlarmReceiver::class.java), FLAGS)
    }
}

internal class DikcizCustomAppAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try { DikcizCustomAppService.start(context) }
        catch (exception: RuntimeException) {
            DikcizCustomApps.cancelTimers(context, null, "background_restart_rejected")
            DikcizLogger("DikcizAppHost", HomeConfigStore(context).logDirectory).warn("custom_app_alarm_rejected", mapOf("error_class" to exception.javaClass.simpleName))
        }
    }
}
