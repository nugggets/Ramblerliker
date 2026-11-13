package com.nugggets.ramblerliker

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Единственное уведомление приложения (кроме обязательного значка службы): «лимит обновился».
// Напоминание ставится на время сброса лимита и срабатывает, даже когда лайки остановлены или процесс убит.
object LimitNotify {
    private const val CHANNEL = "liker_limits"

    private fun alarmIntent(ctx: Context, svc: Svc, named: Boolean): PendingIntent {
        val i = Intent(ctx, LimitResetReceiver::class.java).putExtra("svc", svc.id).putExtra("named", named)
        return PendingIntent.getBroadcast(ctx, svc.nidOff + 4, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // Ставит напоминание на время сброса (atMs: обычное время, мс). Старое напоминание этого сервиса заменяется
    fun schedule(ctx: Context, svc: Svc, atMs: Long, named: Boolean) {
        if (atMs <= System.currentTimeMillis()) return
        try {
            ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, alarmIntent(ctx, svc, named))
        } catch (e: Exception) {
        }
    }

    fun cancel(ctx: Context, svc: Svc) {
        try {
            ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, svc, false))
        } catch (e: Exception) {
        }
    }

    // named: сайт сам назвал время сброса. Иначе время расчётное, и текст осторожнее
    fun show(ctx: Context, svc: Svc, named: Boolean) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Лимиты обновились", NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(ctx, svc.activityCls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(ctx, svc.nidOff + 4, open, PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, CHANNEL)
                .setContentTitle("${svc.title}: лимит обновился")
                .setContentText(if (named) "Сайт снова принимает лайки" else "По расчёту лимит сбросился, можно ставить лайки")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(svc.nidOff + 4, n)
        } catch (e: Exception) {
        }
    }

    // После перезагрузки телефона напоминания пропадают: ставим заново по сохранённому времени сброса
    fun restoreAll(ctx: Context) {
        for (svc in SERVICES) {
            val p = ctx.getSharedPreferences(svc.prefsName, Context.MODE_PRIVATE)
            schedule(ctx, svc, p.getLong("siteLimitUntil", 0L), p.getBoolean("siteResetNamed", false))
        }
    }
}

class LimitResetReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val svc = SERVICES.firstOrNull { it.id == intent.getStringExtra("svc") } ?: return
        LimitNotify.show(ctx, svc, intent.getBooleanExtra("named", false))
    }
}
