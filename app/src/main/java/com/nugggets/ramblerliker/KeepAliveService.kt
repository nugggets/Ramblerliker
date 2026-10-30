package com.nugggets.ramblerliker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

open class KeepAliveService : Service() {
    // Для Mamba служба в своём процессе со своими id уведомлений (см. MambaKeepAliveService)
    protected open val notifId: Int = 1
    protected open val alertId: Int = 2
    protected open val target: Class<*> get() = MainActivity::class.java
    protected open val lockTag: String = "liker"
    private var wl: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("liker", "Liker", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("liker_alert", "Liker: важное", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, target), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "liker")
            .setContentTitle("Лайки идут")
            .setContentText("Приложение работает в фоне")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            // specialUse: без тайм-аута, в отличие от dataSync на новых версиях Android
            startForeground(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notifId, n)
        }
        if (intent == null) {
            // Систему перезапустила службу после закрытия приложения: лайки не идут, сообщаем об этом
            try {
                startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
            val alert = Notification.Builder(this, "liker_alert")
                .setContentTitle("Лайки остановились")
                .setContentText("Система закрыла приложение. Нажми, чтобы продолжить")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(alertId, alert)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireLocks()
        return START_STICKY
    }

    private fun acquireLocks() {
        if (wl == null) {
            val pm = getSystemService(PowerManager::class.java)
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$lockTag:run").also {
                it.setReferenceCounted(false)
                it.acquire()
            }
        }
        if (wifi == null) {
            try {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val mode = if (Build.VERSION.SDK_INT >= 29) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifi = wm.createWifiLock(mode, "$lockTag:wifi").also {
                    it.setReferenceCounted(false)
                    it.acquire()
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        try {
            wl?.release()
        } catch (e: Exception) {
        }
        wl = null
        try {
            wifi?.release()
        } catch (e: Exception) {
        }
        wifi = null
        super.onDestroy()
    }
}
