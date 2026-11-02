package com.nugggets.ramblerliker

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Общие настройки для всех сайтов. Лежат в одном файле, который читают все процессы приложения
 * (у каждого сайта свой процесс, общие SharedPreferences между ними ненадёжны).
 * Пока включён флаг "on", значения отсюда важнее личных настроек сайта.
 */
object Common {
    private var cache = JSONObject()
    private var stamp = -1L
    private var checkedAt = 0L

    private fun file(ctx: Context) = File(ctx.filesDir, "common_settings.json")

    @Synchronized
    fun read(ctx: Context): JSONObject {
        val now = System.currentTimeMillis()
        if (stamp != -1L && now - checkedAt < 2000L) return cache
        checkedAt = now
        try {
            val f = file(ctx)
            val m = if (f.exists()) f.lastModified() else 0L
            if (m != stamp) {
                stamp = m
                cache = if (f.exists()) JSONObject(f.readText()) else JSONObject()
            }
        } catch (e: Exception) {
            cache = JSONObject()
        }
        return cache
    }

    fun on(ctx: Context): Boolean = read(ctx).let { it.optBoolean("on", false) && it.length() > 1 }

    @Synchronized
    fun save(ctx: Context, j: JSONObject) {
        try {
            val f = file(ctx)
            val tmp = File(f.path + ".tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(f)
        } catch (e: Exception) {
        }
        stamp = -1L
        checkedAt = 0L
    }
}
