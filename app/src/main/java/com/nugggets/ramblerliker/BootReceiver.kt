package com.nugggets.ramblerliker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Запускает службу лайков каждого сервиса после включения телефона и после обновления приложения (если не нажимали «Стоп»)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        for (svc in SERVICES) {
            val prefs = ctx.getSharedPreferences(svc.prefsName, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("wantRunning", svc.id == "rambler")) continue // нажимали «Стоп»
            try {
                ctx.startForegroundService(Intent(ctx, svc.keepCls))
            } catch (e: Exception) {
                // запасной вариант: система не дала запустить службу из фона, оставляем уведомление и пробуем открыть окно
                val open = Intent(ctx, svc.activityCls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(open)
                } catch (e2: Exception) {
                }
                try {
                    val nm = ctx.getSystemService(NotificationManager::class.java)
                    nm.createNotificationChannel(
                        NotificationChannel("liker_alert", "Liker: важное", NotificationManager.IMPORTANCE_HIGH)
                    )
                    val pi = PendingIntent.getActivity(ctx, svc.nidOff, open, PendingIntent.FLAG_IMMUTABLE)
                    val n = Notification.Builder(ctx, "liker_alert")
                        .setContentTitle(svc.title + " Liker")
                        .setContentText("Нажми, чтобы запустить лайки")
                        .setSmallIcon(android.R.drawable.ic_menu_send)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(svc.nidOff + 2, n)
                } catch (e3: Exception) {
                }
            }
        }
    }
}
