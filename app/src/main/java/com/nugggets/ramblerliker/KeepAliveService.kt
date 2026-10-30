package com.nugggets.ramblerliker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.app.Service
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.IBinder
import android.os.PowerManager

// Сколько окон приложения сейчас живо в этом процессе (служба проверяет, поднялось ли окно после перезапуска)
object LikerState {
    @Volatile
    var activities = 0
}

open class KeepAliveService : Service() {
    // Для Mamba служба в своём процессе со своими id уведомлений (см. MambaKeepAliveService)
    protected open val notifId: Int = 1
    protected open val alertId: Int = 2
    protected open val target: Class<*> get() = MainActivity::class.java
    protected open val lockTag: String = "liker"
    protected open val wantLocation: Boolean = false
    private var wl: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var overlay: View? = null
    private val hh = Handler(Looper.getMainLooper())

    // Крошечное невидимое окно поверх всего. С ним система (Android 15) разрешает открыть окно приложения из фона,
    // а ещё процесс с таким окном система закрывает реже
    private fun showOverlay() {
        if (overlay != null) return
        try {
            if (!Settings.canDrawOverlays(this)) return
            val v = View(this)
            v.setBackgroundColor(0x01000000)
            val lp = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.START or Gravity.TOP
            getSystemService(WindowManager::class.java).addView(v, lp)
            overlay = v
        } catch (e: Exception) {
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(v)
        } catch (e: Exception) {
        }
        overlay = null
    }

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
        // Тип «location» нужен, чтобы сайт получал геопозицию, пока окно свёрнуто (только если разрешение выдано)
        val loc = if (wantLocation && Build.VERSION.SDK_INT >= 29 &&
            (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        ) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            goForeground(n, loc)
        } catch (e: Exception) {
            if (loc != 0) goForeground(n, 0) else throw e
        }
        showOverlay()
        acquireLocks()
        if (intent == null || intent.getBooleanExtra("launch", false)) {
            // Службу подняла система после закрытия приложения (или телефон загрузился): сами открываем окно,
            // оно запустит лайки (если не нажимали «Стоп»). Уведомление «остановились» только если окно так и не открылось
            hh.postDelayed({
                try {
                    startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }, 600)
            hh.postDelayed({
                if (LikerState.activities <= 0) {
                    val alert = Notification.Builder(this, "liker_alert")
                        .setContentTitle("Лайки остановились")
                        .setContentText("Система закрыла приложение. Нажми, чтобы продолжить")
                        .setSmallIcon(android.R.drawable.ic_menu_send)
                        .setContentIntent(pi)
                        .setFullScreenIntent(pi, true)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(alertId, alert)
                }
            }, 15000)
        }
        return START_STICKY
    }

    private fun goForeground(n: Notification, extra: Int) {
        if (Build.VERSION.SDK_INT >= 34) {
            // specialUse: без тайм-аута, в отличие от dataSync на новых версиях Android
            startForeground(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or extra)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or extra)
        } else {
            startForeground(notifId, n)
        }
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
        hh.removeCallbacksAndMessages(null)
        hideOverlay()
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
