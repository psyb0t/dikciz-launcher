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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors

internal class DikcizJourneyService : Service(), LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var logger: DikcizLogger
    private var registered = false
    private var busy = false
    private var currentStartId = 0
    private var lastText = ""

    private val tick = object : Runnable {
        override fun run() {
            if (!running || busy) return
            busy = true
            worker.execute {
                var text = "Monitoring stopped"
                var active = false
                try {
                    text = DikcizJourneys.tick(applicationContext, logger)
                    active = DikcizJourneys.active(applicationContext)
                } catch (exception: Exception) {
                    logger.error("journey_monitor_failed", mapOf("error_class" to exception.javaClass.simpleName))
                    DikcizJourneys.stopAll(applicationContext, "monitor_failed")
                }
                handler.post {
                    busy = false
                    if (!running) return@post
                    if (!active) { stopSelfResult(currentStartId); return@post }
                    if (lastText != text) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
                        lastText = text
                    }
                    handler.postDelayed(this, TICK_MILLISECONDS)
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        logger = DikcizLogger("DikcizJourneys", HomeConfigStore(applicationContext).logDirectory)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Safety and journeys", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentStartId = startId
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification("Starting monitoring"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(NOTIFICATION_ID, notification("Starting monitoring"))
        } catch (exception: RuntimeException) {
            logger.warn("journey_foreground_rejected", mapOf("error_class" to exception.javaClass.simpleName))
            DikcizJourneys.stopAll(applicationContext, "foreground_start_rejected")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        running = true
        if (intent?.action == ACTION_STOP) {
            DikcizJourneys.stopAll(applicationContext, "owner_stopped")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        try {
            if (!registered) {
                val manager = getSystemService(LocationManager::class.java)
                manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }.forEach {
                    manager.requestLocationUpdates(it, LOCATION_INTERVAL, 0f, this, Looper.getMainLooper())
                }
                registered = true
            }
        } catch (exception: SecurityException) {
            logger.warn("journey_location_access_lost")
            DikcizJourneys.stopAll(applicationContext, "location_access_lost")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        if (!busy) handler.post(tick)
        return START_STICKY
    }
    override fun onLocationChanged(location: Location) {
        val copy = Location(location)
        worker.execute {
            try { DikcizJourneys.sample(applicationContext, copy) }
            catch (exception: RuntimeException) {
                logger.error("journey_location_rejected", mapOf("error_class" to exception.javaClass.simpleName))
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (registered) getSystemService(LocationManager::class.java).removeUpdates(this)
        getSystemService(AlarmManager::class.java).cancel(alarmIntent(this))
        worker.shutdown()
        super.onDestroy()
    }
    private fun notification(text: String): Notification {
        val open = Intent(this, DikcizHomeActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(this, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, NOTIFICATION_ID, Intent(this, javaClass).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Dikciz safety and journeys").setContentText(text).setContentIntent(pendingOpen)
            .setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null, "Stop all", stop).build()).build()
    }
    companion object {
        const val ACTION_STOP = "org.fossify.home.dikciz.JOURNEYS_STOP"
        private const val CHANNEL = "dikciz-safety-journeys"
        private const val NOTIFICATION_ID = 7310
        private const val TICK_MILLISECONDS = 1000L
        private const val LOCATION_INTERVAL = 1000L
        @Volatile var running = false
            private set
        fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID,
            Intent(context, DikcizJourneyAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
internal class DikcizJourneyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DikcizJourneys.active(context)) return
        try { context.startForegroundService(Intent(context, DikcizJourneyService::class.java)) }
        catch (exception: RuntimeException) {
            DikcizJourneys.stopAll(context, "background_restart_rejected")
            DikcizLogger("DikcizJourneys", HomeConfigStore(context).logDirectory)
                .warn("journey_alarm_restart_rejected", mapOf("error_class" to exception.javaClass.simpleName))
        }
    }
}
