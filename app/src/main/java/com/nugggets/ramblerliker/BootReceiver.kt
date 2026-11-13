package com.nugggets.ramblerliker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// После включения телефона и после обновления приложения: запускает службы лайков (если не нажимали «Стоп»)
// и заново ставит напоминания «лимит обновился»
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        LimitNotify.restoreAll(ctx)
        for (svc in SERVICES) {
            val prefs = ctx.getSharedPreferences(svc.prefsName, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("wantRunning", svc.id == "rambler")) continue // нажимали «Стоп»
            try {
                ctx.startForegroundService(Intent(ctx, svc.keepCls))
            } catch (e: Exception) {
            }
        }
    }
}
