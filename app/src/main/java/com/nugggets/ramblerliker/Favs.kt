package com.nugggets.ramblerliker

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// Избранные ссылки сервиса и архив уже лайкнутых. Хранятся в настройках сервиса (favs и favArchive).
// Анкеты из избранного движок лайкает первыми, потом переносит их в архив.
class FavItem(val title: String, val url: String, val at: Long = 0L, val ok: Boolean = true)

object Favs {
    const val KEY = "favs"
    const val ARCHIVE = "favArchive"
    private const val MAX = 100
    private const val MAX_ARCHIVE = 300

    fun load(p: SharedPreferences, key: String = KEY): MutableList<FavItem> {
        val out = ArrayList<FavItem>()
        try {
            val a = JSONArray(p.getString(key, "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(FavItem(o.optString("t"), o.optString("u"), o.optLong("at", 0L), o.optBoolean("ok", true)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(p: SharedPreferences, key: String, list: List<FavItem>) {
        val a = JSONArray()
        for (f in list.take(if (key == ARCHIVE) MAX_ARCHIVE else MAX)) {
            a.put(JSONObject().put("t", f.title).put("u", f.url).put("at", f.at).put("ok", f.ok))
        }
        p.edit().putString(key, a.toString()).apply()
    }

    // Лайк поставлен (ok) или три попытки не удались: ссылка уходит из избранного в архив
    fun archive(p: SharedPreferences, item: FavItem, ok: Boolean) {
        save(p, KEY, load(p, KEY).filter { it.url != item.url })
        val arch = load(p, ARCHIVE).filter { it.url != item.url }.toMutableList()
        arch.add(0, FavItem(item.title, item.url, System.currentTimeMillis(), ok))
        save(p, ARCHIVE, arch)
    }

    // Из архива обратно в избранное: анкета будет лайкнута первой ещё раз
    fun restore(p: SharedPreferences, item: FavItem) {
        save(p, ARCHIVE, load(p, ARCHIVE).filter { it.url != item.url })
        val list = load(p, KEY).filter { it.url != item.url }.toMutableList()
        list.add(0, FavItem(item.title, item.url))
        save(p, KEY, list)
    }
}
