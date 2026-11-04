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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors

internal class DikcizConnectionsService : Service() {
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var startId=0
    private var running=false
    private lateinit var logger: DikcizLogger
    private val refresh=object : Runnable {
        override fun run() {
            if (!running) return
            if (!DikcizDeviceConnections.hasActive() && !DikcizWifiNetworks.hasActive()) { stopSelfResult(startId); return }
            handler.postDelayed(this,1000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        logger=DikcizLogger("DikcizConnections",HomeConfigStore(applicationContext).logDirectory)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Device connections",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        this.startId=startId
        val stop=PendingIntent.getService(this,ID,Intent(this,javaClass).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,ID,Intent(this,DikcizHomeActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_share).setContentTitle("Dikciz device connections")
            .setContentText("Owner-started connections are active").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null,"Stop all",stop).build()).build()
        try {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(ID,notification)
        } catch (exception: RuntimeException) {
            logger.warn("connection_foreground_rejected",mapOf("error_class" to exception.javaClass.simpleName))
            worker.execute { closeAll(); stopSelfResult(startId) }
            return START_NOT_STICKY
        }
        running=true
        if (intent?.action==STOP) worker.execute { closeAll(); stopSelfResult(startId) }
        else { handler.removeCallbacks(refresh); handler.post(refresh) }
        return START_NOT_STICKY
    }
    private fun closeAll() {
        DikcizDeviceConnections.closeAll(logger)
        DikcizWifiNetworks.closeAll(applicationContext,logger)
    }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onDestroy() {
        running=false; handler.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy()
    }
    companion object {
        private const val CHANNEL="dikciz-device-connections"
        private const val ID=7313
        private const val STOP="org.fossify.home.dikciz.CONNECTIONS_STOP"
        fun start(context: Context) {
            try { context.startForegroundService(Intent(context,DikcizConnectionsService::class.java)) }
            catch (exception: RuntimeException) { DikcizNativeCommands.failure("connection_foreground_required","Open Dikciz before retaining device connections") }
        }
    }
}
