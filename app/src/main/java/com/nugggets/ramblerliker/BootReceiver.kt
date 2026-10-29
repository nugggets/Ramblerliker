package com.nugggets.ramblerliker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Запускает приложение после включения телефона и после обновления приложения
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = ctx.getSharedPreferences("liker", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("wantRunning", true)) return // нажимали «Стоп»
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(open)
        } catch (e: Exception) {
        }
        // запасной вариант: если система не даёт открыть окно из фона, остаётся уведомление
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("liker_alert", "Liker: важное", NotificationManager.IMPORTANCE_HIGH)
            )
            val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, "liker_alert")
                .setContentTitle("Rambler Liker")
                .setContentText("Нажми, чтобы запустить лайки")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(2, n)
        } catch (e: Exception) {
        }
    }
}
