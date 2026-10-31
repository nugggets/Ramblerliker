package com.nugggets.ramblerliker

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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Фоновая служба: держит процесс живым (wake lock, Wi-Fi lock, постоянное уведомление) и запускает движок лайков.
 * Если систему закрыла процесс, служба перезапускается сама (START_STICKY) и движок продолжает без окна.
 */
open class KeepAliveService : Service() {
    // Для Mamba служба в своём процессе (см. MambaKeepAliveService): свой движок, свои id уведомлений
    protected open val svcId: String = "rambler"
    private var wl: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = Engine.get(this, svcId)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("liker", "Liker", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("liker_alert", "Liker: важное", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, engine.svc.activityCls), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "liker")
            .setContentTitle("Лайки идут")
            .setContentText("Приложение работает в фоне")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        // Тип «location» нужен, чтобы сайт получал геопозицию, пока окно закрыто (только если разрешение выдано)
        val loc = if (svcId != "rambler" && Build.VERSION.SDK_INT >= 29 &&
            (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        ) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            goForeground(n, engine.svc.nidOff + 1, loc)
        } catch (e: Exception) {
            if (loc != 0) goForeground(n, engine.svc.nidOff + 1, 0) else throw e
        }
        acquireLocks()
        // запуск после перезагрузки телефона, после закрытия приложения системой или из окна:
        // движок сам решает, нужно ли работать (по умолчанию да, если не нажимали «Стоп»)
        engine.ensureRunning()
        if (!engine.running) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun goForeground(n: Notification, id: Int, extra: Int) {
        if (Build.VERSION.SDK_INT >= 34) {
            // specialUse: без тайм-аута, в отличие от dataSync на новых версиях Android
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or extra)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or extra)
        } else {
            startForeground(id, n)
        }
    }

    private fun acquireLocks() {
        if (wl == null) {
            val pm = getSystemService(PowerManager::class.java)
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$svcId:run").also {
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
                wifi = wm.createWifiLock(mode, "$svcId:wifi").also {
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
