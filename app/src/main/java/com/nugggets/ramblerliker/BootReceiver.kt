package com.nugggets.ramblerliker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Запускает приложение после включения телефона и после обновления приложения: каждый сервис, у которого не нажимали «Стоп»
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val targets = listOf(
            Triple("liker", MainActivity::class.java, 2),
            Triple("liker_mamba", MambaActivity::class.java, 12)
        )
        for ((name, cls, nid) in targets) {
            val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("wantRunning", name == "liker")) continue // нажимали «Стоп»
            val open = Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                val pi = PendingIntent.getActivity(ctx, nid, open, PendingIntent.FLAG_IMMUTABLE)
                val n = Notification.Builder(ctx, "liker_alert")
                    .setContentTitle(if (name == "liker") "Rambler Liker" else "Mamba Liker")
                    .setContentText("Нажми, чтобы запустить лайки")
                    .setSmallIcon(android.R.drawable.ic_menu_send)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm.notify(nid, n)
            } catch (e: Exception) {
            }
        }
    }
}
