package com.nugggets.ramblerliker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// После включения телефона и после обновления приложения запускает службу каждого сервиса, у которого не нажимали «Стоп».
// Служба сама показывает невидимое окно и открывает окно приложения (из приёмника загрузки Android 15 этого не позволяет)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val targets = listOf(
            Pair("liker", KeepAliveService::class.java),
            Pair("liker_mamba", MambaKeepAliveService::class.java)
        )
        for ((name, cls) in targets) {
            val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("wantRunning", name == "liker")) continue // нажимали «Стоп»
            try {
                ctx.startForegroundService(Intent(ctx, cls).putExtra("launch", true))
            } catch (e: Exception) {
            }
        }
    }
}
