package com.nugggets.ramblerliker

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Накопитель диагностики одного сайта (0.77). Копит то, что нужно для разбора и улучшения кода, и переживает перезапуски:
 * историю отказов лимита (сколько лайков, за какое окно, что ответил сайт), фактический сброс против расчётного,
 * задержки и неудачи запросов, пройденную ленту, ошибки, сессии и перезапуски процесса.
 * Хранится в stats_<сайт>.json. Из него же строится «автоанализ» с выводами для диагностики.
 * Все вызовы из главного потока своего процесса.
 */
class Stats(private val file: File, private val title: String) {
    private val msk: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).also { it.timeZone = msk }
    private val tsFmt = SimpleDateFormat("d.MM HH:mm", Locale("ru")).also { it.timeZone = msk }
    private val hourFmt = SimpleDateFormat("H", Locale.US).also { it.timeZone = msk }
    private var root = JSONObject()
    private var dirty = false
    private var savedAt = 0L

    // метки лайков только в памяти: нужны, чтобы к моменту отказа знать, сколько лайков было за последний час и 10 минут
    private val likeTs = ArrayList<Long>()
    private var lastLikeTs = 0L
    private val startTs = System.currentTimeMillis()

    init {
        try {
            if (file.exists()) root = JSONObject(file.readText())
        } catch (e: Exception) {
            root = JSONObject()
        }
        try {
            if (!root.has("since")) root.put("since", System.currentTimeMillis())
        } catch (e: Exception) {
        }
    }

    // ---------- вспомогательное ----------
    private fun obj(parent: JSONObject, key: String): JSONObject {
        var o = parent.optJSONObject(key)
        if (o == null) {
            o = JSONObject()
            parent.put(key, o)
        }
        return o
    }

    private fun arr(parent: JSONObject, key: String): JSONArray {
        var a = parent.optJSONArray(key)
        if (a == null) {
            a = JSONArray()
            parent.put(key, a)
        }
        return a
    }

    private fun add(o: JSONObject, key: String, by: Long = 1L) {
        o.put(key, o.optLong(key, 0L) + by)
    }

    private fun capArr(a: JSONArray, max: Int) {
        while (a.length() > max) a.remove(0)
    }

    private fun hourOfDay(t: Long): Int = hourFmt.format(Date(t)).toIntOrNull() ?: 0

    private fun today(): JSONObject {
        val days = obj(root, "days")
        val d = obj(days, dayFmt.format(Date()))
        if (days.length() > 16) {
            val keys = ArrayList<String>()
            val kit = days.keys()
            while (kit.hasNext()) keys.add(kit.next())
            keys.sort()
            while (keys.size > 14) days.remove(keys.removeAt(0))
        }
        return d
    }

    private fun hoursArr(d: JSONObject): JSONArray {
        var a = d.optJSONArray("hours")
        if (a == null || a.length() < 24) {
            a = JSONArray()
            for (i in 0 until 24) a.put(0)
            d.put("hours", a)
        }
        return a
    }

    // из словаря счётчиков оставляем не больше max записей, выкидываем давно не встречавшиеся
    private fun trimMap(m: JSONObject, max: Int) {
        while (m.length() > max) {
            var worst: String? = null
            var worstAt = Long.MAX_VALUE
            val kit = m.keys()
            while (kit.hasNext()) {
                val k = kit.next()
                val at = m.optJSONObject(k)?.optLong("last", 0L) ?: 0L
                if (at < worstAt) {
                    worstAt = at
                    worst = k
                }
            }
            if (worst == null) return
            m.remove(worst)
        }
    }

    private fun nums(a: JSONArray?): List<Long> {
        val r = ArrayList<Long>()
        if (a != null) for (i in 0 until a.length()) r.add(a.optLong(i))
        return r
    }

    private fun pct(sorted: List<Long>, p: Double): Long = if (sorted.isEmpty()) 0L else sorted[((sorted.size - 1) * p).toInt()]

    private fun med(l: List<Long>): Long = pct(l.sorted(), 0.5)

    private fun ts(t: Long): String = if (t > 0L) tsFmt.format(Date(t)) else "?"

    private fun hm(min: Long): String {
        val a = Math.abs(min)
        val s = if (min < 0) "-" else ""
        return if (a >= 60) "$s${a / 60} ч ${a % 60} мин" else "$s$a мин"
    }

    // ---------- запись ----------
    // Итог одного запроса к сайту: kind = like, search, boot, limits; ms = сколько ждали ответа (-1 если неизвестно)
    fun request(kind: String, ok: Boolean, ms: Long, outcome: String) {
        try {
            val r = obj(obj(today(), "req"), kind)
            add(r, "n")
            if (ok) {
                add(r, "ok")
            } else {
                add(r, "bad")
                val f = obj(obj(root, "fails"), kind)
                val e = obj(f, outcome.take(60).ifEmpty { "?" })
                add(e, "n")
                e.put("last", System.currentTimeMillis())
                trimMap(f, 25)
            }
            if (ms in 0L..600000L) {
                add(r, "ms", ms)
                add(r, "cnt")
                if (ms > r.optLong("max", 0L)) r.put("max", ms)
                val a = arr(obj(root, "lat"), kind)
                a.put(ms)
                capArr(a, 200)
            }
            dirty = true
        } catch (e: Exception) {
        }
    }

    // Лайк принят сайтом
    fun likeOk(ms: Long) {
        request("like", true, ms, "")
        try {
            val now = System.currentTimeMillis()
            val d = today()
            add(d, "likes")
            val hs = hoursArr(d)
            val hr = hourOfDay(now)
            hs.put(hr, hs.optInt(hr, 0) + 1)
            if (lastLikeTs > 0L && now - lastLikeTs < 600000L) {
                val a = arr(obj(root, "lat"), "ivl")
                a.put(now - lastLikeTs)
                capArr(a, 200)
            }
            lastLikeTs = now
            likeTs.add(now)
            while (likeTs.size > 6000) likeTs.removeAt(0)
            val cur = root.optJSONObject("curSess")
            if (cur != null) cur.put("likes", cur.optInt("likes", 0) + 1)
            dirty = true
        } catch (e: Exception) {
        }
    }

    // Сколько лайков поставлено за последние ms миллисекунд. Метки живут только в памяти процесса:
    // если процесс работает меньше ms, точно сказать нельзя, возвращаем -1
    fun likesSince(ms: Long): Int {
        if (System.currentTimeMillis() - startTs < ms) return -1
        val from = System.currentTimeMillis() - ms
        var n = 0
        for (t in likeTs) if (t >= from) n++
        return n
    }

    // отметка «процесс жив»: нужна, чтобы по времени последней записи понять, когда оборвалась сессия
    fun touch() {
        dirty = true
    }

    fun event(name: String) {
        try {
            add(obj(today(), "ev"), name)
            dirty = true
        } catch (e: Exception) {
        }
    }

    // Разобранный блок ленты: сколько позиций пришло, сколько уже лайкнуто сайтом, сколько повторов, сколько взято в очередь
    fun feed(total: Int, liked: Int, dup: Int, added: Int) {
        try {
            val f = obj(today(), "feed")
            add(f, "batches")
            add(f, "seen", total.toLong())
            add(f, "liked", liked.toLong())
            add(f, "dup", dup.toLong())
            add(f, "added", added.toLong())
            if (added == 0) add(f, "empty")
            dirty = true
        } catch (e: Exception) {
        }
    }

    // Ошибка по тексту: одинаковые складываются в одну строку (длинные числа и пробелы нормализуются)
    fun error(msg: String) {
        try {
            val key = maskSecrets(msg).replace(Regex("[0-9]{4,}"), "#").replace(Regex("\\s+"), " ").trim().take(90).ifEmpty { "?" }
            val m = obj(root, "errs")
            val e = obj(m, key)
            val now = System.currentTimeMillis()
            if (!e.has("first")) e.put("first", now)
            add(e, "n")
            e.put("last", now)
            trimMap(m, 30)
            dirty = true
        } catch (e: Exception) {
        }
    }

    fun limitEvent(j: JSONObject) {
        try {
            val a = arr(root, "limits")
            a.put(j)
            capArr(a, 20)
            add(obj(today(), "ev"), "limit")
            dirty = true
            flush(true)
        } catch (e: Exception) {
        }
    }

    fun resetOutcome(j: JSONObject) {
        try {
            val a = arr(root, "resets")
            a.put(j)
            capArr(a, 20)
            dirty = true
            flush(true)
        } catch (e: Exception) {
        }
    }

    fun processStart() {
        try {
            val a = arr(root, "starts")
            a.put(System.currentTimeMillis())
            capArr(a, 40)
            event("procStart")
            dirty = true
        } catch (e: Exception) {
        }
    }

    private fun closeSession(cur: JSONObject, end: Long, why: String) {
        val s = JSONObject()
        val st = cur.optLong("start", end)
        s.put("start", st)
        s.put("end", end)
        s.put("likes", cur.optInt("likes", 0))
        s.put("why", why)
        val a = arr(root, "sessions")
        a.put(s)
        capArr(a, 15)
    }

    fun sessionStart() {
        try {
            val cur = root.optJSONObject("curSess")
            // прошлая сессия не закрыта «Стопом»: процесс убила система или он упал
            if (cur != null) closeSession(cur, root.optLong("lastSeen", cur.optLong("start", 0L)), "прервана без «Стоп» (процесс убит или упал)")
            val c = JSONObject()
            c.put("start", System.currentTimeMillis())
            c.put("likes", 0)
            root.put("curSess", c)
            lastLikeTs = 0L
            flush(true)
        } catch (e: Exception) {
        }
    }

    fun sessionEnd(reason: String) {
        try {
            val cur = root.optJSONObject("curSess") ?: return
            closeSession(cur, System.currentTimeMillis(), reason)
            root.remove("curSess")
            flush(true)
        } catch (e: Exception) {
        }
    }

    fun flush(force: Boolean) {
        if (!dirty && !force) return
        val now = System.currentTimeMillis()
        if (!force && now - savedAt < 30000L) return
        try {
            root.put("lastSeen", now)
            val tmp = File(file.path + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
            savedAt = now
            dirty = false
        } catch (e: Exception) {
        }
    }

    // ---------- чтение ----------
    private fun dayKeys(): List<String> {
        val days = root.optJSONObject("days") ?: return emptyList()
        val keys = ArrayList<String>()
        val kit = days.keys()
        while (kit.hasNext()) keys.add(kit.next())
        keys.sort()
        return keys
    }

    private fun dayObj(k: String): JSONObject = root.optJSONObject("days")?.optJSONObject(k) ?: JSONObject()

    private fun reqSum(d: JSONObject, kind: String, field: String): Long = d.optJSONObject("req")?.optJSONObject(kind)?.optLong(field, 0L) ?: 0L

    private fun reqTotal(kind: String, field: String): Long {
        var s = 0L
        for (k in dayKeys()) s += reqSum(dayObj(k), kind, field)
        return s
    }

    private fun evTotal(name: String): Long {
        var s = 0L
        for (k in dayKeys()) s += dayObj(k).optJSONObject("ev")?.optLong(name, 0L) ?: 0L
        return s
    }

    private fun latLine(kind: String, label: String): String {
        val l = nums(root.optJSONObject("lat")?.optJSONArray(kind)).sorted()
        if (l.isEmpty()) return ""
        return "$label: ответ сайта, мс: медиана ${pct(l, 0.5)}, p95 ${pct(l, 0.95)}, макс ${l.last()} (по последним ${l.size})\n"
    }

    private fun ratio(a: Long, b: Long): String = if (b <= 0L) "-" else String.format(Locale.US, "%.1f%%", a * 100.0 / b)

    /**
     * Весь автоанализ одним текстом. rateMin/rateMax: заданный темп (лайков в минуту), winMs: окно лимита сайта,
     * siteMax: лимит, который приложение знает, workHours: длина рабочего окна в часах
     */
    fun report(rateMin: Float, rateMax: Float, winMs: Long, siteMax: Int, workHours: Int): String {
        val sb = StringBuilder()
        val hints = ArrayList<String>()
        val now = System.currentTimeMillis()
        sb.append("=== АВТОАНАЛИЗ $title (что стоит поправить в коде; накопитель с ${ts(root.optLong("since", now))} МСК) ===\n")

        // --- 1. отказы лимита ---
        val lim = root.optJSONArray("limits")
        val limN = lim?.length() ?: 0
        val made = ArrayList<Long>()
        val wins = ArrayList<Long>()
        val hrBefore = ArrayList<Long>()
        var named = 0
        if (lim != null) for (i in 0 until lim.length()) {
            val e = lim.optJSONObject(i) ?: continue
            if (e.optInt("made", 0) > 0) made.add(e.optInt("made", 0).toLong())
            if (e.optLong("winMin", 0L) > 0L) wins.add(e.optLong("winMin", 0L))
            if (e.optInt("hourBefore", -1) >= 0) hrBefore.add(e.optInt("hourBefore", 0).toLong())
            if (e.optLong("namedS", -1L) >= 0L) named++
        }
        sb.append("\n--- Лимит сайта по истории отказов ---\n")
        if (limN == 0) {
            sb.append("отказов лимита ещё не было (накопитель начал работать недавно)\n")
        } else {
            sb.append("отказов записано: $limN, сайт сам назвал время сброса в $named из $limN\n")
            if (made.isNotEmpty()) {
                val s = made.sorted()
                sb.append("лайков до отказа: мин ${s.first()}, медиана ${pct(s, 0.5)}, макс ${s.last()}, последний ${made.last()}\n")
            }
            if (wins.isNotEmpty()) {
                val s = wins.sorted()
                sb.append("возраст окна к моменту отказа (от первого лайка окна): мин ${hm(s.first())}, медиана ${hm(pct(s, 0.5))}, макс ${hm(s.last())}\n")
            }
            if (hrBefore.isNotEmpty()) sb.append("лайков за последний час перед отказом: медиана ${med(hrBefore)}\n")
            val from = maxOf(0, limN - 8)
            for (i in from until limN) {
                val e = lim?.optJSONObject(i) ?: continue
                sb.append("  ${ts(e.optLong("ts", 0L))} МСК: в окне ${e.optInt("made", 0)}, окно ${hm(e.optLong("winMin", 0L))}, за час ${e.optInt("hourBefore", -1)}, за 10 мин ${e.optInt("min10", -1)}, ")
                sb.append("за день ${e.optInt("day", 0)}, сайт назвал сброс: ${if (e.optLong("namedS", -1L) >= 0L) "да, ${e.optLong("namedS", 0L) / 60} мин" else "нет"}, ")
                sb.append("остаток до отказа ${e.optInt("left", -1)}, ответ: ${e.optString("raw").take(140)}\n")
            }
        }

        // --- 2. фактический сброс против расчёта ---
        val rs = root.optJSONArray("resets")
        val rsN = rs?.length() ?: 0
        val waits = ArrayList<Long>()
        val deltas = ArrayList<Long>()
        var viaProbe = 0
        if (rs != null) for (i in 0 until rs.length()) {
            val e = rs.optJSONObject(i) ?: continue
            // замеры с остановками приложения и с расчётным сбросом вне рабочего окна неточные: в итоги не берём
            if (e.optBoolean("gap", false) || e.optBoolean("offHours", false)) continue
            if (e.has("waitMin")) waits.add(e.optLong("waitMin", 0L))
            if (e.has("deltaMin")) deltas.add(e.optLong("deltaMin", 0L))
            if (e.optBoolean("probe", false)) viaProbe++
        }
        sb.append("\n--- Сброс лимита: по факту и по расчёту приложения ---\n")
        if (rsN == 0) {
            sb.append("полных замеров «отказ → первый принятый лайк» пока нет\n")
        } else {
            sb.append("чистых замеров ${waits.size} из $rsN (остальные с остановками приложения или со сбросом вне рабочего окна, в итоги не входят)\n")
            if (waits.isNotEmpty()) {
                val s = waits.sorted()
                sb.append("ожидание от отказа до первого принятого лайка: мин ${hm(s.first())}, медиана ${hm(pct(s, 0.5))}, макс ${hm(s.last())}\n")
            }
            if (deltas.isNotEmpty()) {
                val abs = deltas.map { Math.abs(it) }
                val early = deltas.count { it < -2 }
                val late = deltas.count { it > 2 }
                sb.append("факт минус расчёт: медиана ${hm(med(deltas))}, в среднем по модулю ${hm(abs.sum() / abs.size)}; раньше расчёта $early раз, позже $late раз; поймано пробой: $viaProbe из ${waits.size}\n")
            }
            val from = maxOf(0, rsN - 8)
            for (i in from until rsN) {
                val e = rs?.optJSONObject(i) ?: continue
                sb.append("  отказ ${ts(e.optLong("ref", 0L))} → принят ${ts(e.optLong("acc", 0L))} (ждали ${hm(e.optLong("waitMin", 0L))}), расчёт был на ${ts(e.optLong("pred", 0L))}, отклонение ${hm(e.optLong("deltaMin", 0L))}, ")
                sb.append("${if (e.optBoolean("probe", false)) "поймано пробой" else "сам по расписанию"}, сайт назвал сброс: ${if (e.optBoolean("named", false)) "да" else "нет"}")
                if (e.optBoolean("gap", false)) sb.append(", были остановки приложения (неточно)")
                if (e.optBoolean("offHours", false)) sb.append(", сброс вне рабочего окна (неточно)")
                sb.append("\n")
            }
        }

        // --- 3. по дням ---
        sb.append("\n--- По дням (МСК) ---\n")
        val keys = dayKeys()
        for (k in keys.takeLast(14)) {
            val d = dayObj(k)
            val ev = d.optJSONObject("ev")
            val fd = d.optJSONObject("feed")
            sb.append("$k: лайков ${d.optLong("likes", 0L)}, отказов лимита ${ev?.optLong("limit", 0L) ?: 0L}, ")
            sb.append("запросов лайка ${reqSum(d, "like", "n")} (неудач ${reqSum(d, "like", "bad")}), поиска ${reqSum(d, "search", "n")} (неудач ${reqSum(d, "search", "bad")})")
            if (fd != null) sb.append(", лента: блоков ${fd.optLong("batches", 0L)}, позиций ${fd.optLong("seen", 0L)}, уже лайкнуто ${fd.optLong("liked", 0L)}, повторов ${fd.optLong("dup", 0L)}, взято ${fd.optLong("added", 0L)}, пустых ${fd.optLong("empty", 0L)}")
            sb.append("\n")
        }
        if (keys.isEmpty()) sb.append("данных по дням ещё нет\n")

        // --- 4. по часам суток ---
        val hours = LongArray(24)
        for (k in keys) {
            val a = dayObj(k).optJSONArray("hours") ?: continue
            for (i in 0 until minOf(24, a.length())) hours[i] += a.optLong(i, 0L)
        }
        if (hours.sum() > 0L) {
            sb.append("\n--- Лайки по часам суток (МСК, за все дни накопителя) ---\n")
            val parts = ArrayList<String>()
            for (i in 0 until 24) if (hours[i] > 0L) parts.add(String.format(Locale.US, "%02d:%d", i, hours[i]))
            sb.append(parts.joinToString("  ")).append("\n")
        }

        // --- 5. запросы и задержки ---
        sb.append("\n--- Запросы к сайту ---\n")
        var anyReq = false
        for ((kind, label) in listOf("like" to "лайк", "search" to "поиск", "boot" to "первая страница", "limits" to "запрос лимитов")) {
            val n = reqTotal(kind, "n")
            if (n == 0L) continue
            anyReq = true
            val bad = reqTotal(kind, "bad")
            val cnt = reqTotal(kind, "cnt")
            val avg = if (cnt > 0L) reqTotal(kind, "ms") / cnt else 0L
            sb.append("$label: всего $n, неудач $bad (${ratio(bad, n)}), среднее время ответа $avg мс, максимум за день ${keys.maxOfOrNull { reqSum(dayObj(it), kind, "max") } ?: 0L} мс\n")
            sb.append(latLine(kind, "  $label"))
        }
        if (!anyReq) sb.append("запросов ещё не было\n")
        val iv = nums(root.optJSONObject("lat")?.optJSONArray("ivl")).sorted()
        if (iv.isNotEmpty()) {
            val m = pct(iv, 0.5)
            val effRate = if (m > 0L) 60000.0 / m else 0.0
            sb.append("фактический интервал между лайками: медиана $m мс (≈${String.format(Locale.US, "%.1f", effRate)} в минуту), p95 ${pct(iv, 0.95)} мс; задано ${rateMin.toInt()}–${rateMax.toInt()} в минуту\n")
            if (rateMin > 0f && effRate > 0.0 && effRate < rateMin * 0.7) {
                hints.add("Фактический темп ${String.format(Locale.US, "%.0f", effRate)} лайков/мин заметно ниже заданного нижнего ${rateMin.toInt()}: тормозят ответы сайта, сеть или телефон (см. задержки выше).")
            }
        }
        val fails = root.optJSONObject("fails")
        if (fails != null) {
            val fk = fails.keys()
            while (fk.hasNext()) {
                val kind = fk.next()
                val m = fails.optJSONObject(kind) ?: continue
                val rows = ArrayList<Pair<String, Long>>()
                val kit = m.keys()
                while (kit.hasNext()) {
                    val k2 = kit.next()
                    rows.add(k2 to (m.optJSONObject(k2)?.optLong("n", 0L) ?: 0L))
                }
                rows.sortByDescending { it.second }
                sb.append("причины неудач ($kind): ").append(rows.take(8).joinToString("; ") { "${it.first} ×${it.second}" }).append("\n")
            }
        }

        // --- 6. события и стабильность ---
        sb.append("\n--- События (за дни накопителя / сегодня) ---\n")
        val names = linkedMapOf(
            "procStart" to "запусков процесса", "renderGone" to "падений страницы сайта", "wdTimeout" to "нет ответа от страницы 20 с",
            "watchdog" to "завис цикл (сторож)", "authLost" to "вход слетел", "authPause" to "паузы из-за входа", "errPause" to "паузы после серии ошибок",
            "limit" to "отказов лимита", "probe" to "проверок лимита", "probeOk" to "проверка: лимит уже сброшен", "probeFail" to "проверка: сайт всё ещё отказывает",
            "probeErr" to "проверка: не удалась", "soft11" to "errno=11 (пропущено)", "pageLoad" to "загрузок страницы"
        )
        val tdy = today().optJSONObject("ev")
        val parts = ArrayList<String>()
        for ((k, label) in names) {
            val all = evTotal(k)
            if (all > 0L) parts.add("$label: $all / ${tdy?.optLong(k, 0L) ?: 0L}")
        }
        sb.append(if (parts.isEmpty()) "событий не было\n" else parts.joinToString("\n") + "\n")
        val starts = nums(root.optJSONArray("starts"))
        val starts24 = starts.count { it > now - 86400000L }
        if (starts.isNotEmpty()) sb.append("последние запуски процесса: ").append(starts.takeLast(6).joinToString(", ") { ts(it) }).append(" (за сутки: $starts24)\n")
        val ss = root.optJSONArray("sessions")
        val cur = root.optJSONObject("curSess")
        sb.append("\n--- Сессии (старт → стоп) ---\n")
        var interrupted = 0
        if (ss != null) for (i in 0 until ss.length()) if (ss.optJSONObject(i)?.optString("why", "")?.startsWith("прервана") == true) interrupted++
        if (ss != null) for (i in maxOf(0, ss.length() - 8) until ss.length()) {
            val e = ss.optJSONObject(i) ?: continue
            sb.append("  ${ts(e.optLong("start", 0L))} → ${ts(e.optLong("end", 0L))} (${hm((e.optLong("end", 0L) - e.optLong("start", 0L)) / 60000L)}), лайков ${e.optInt("likes", 0)}, ${e.optString("why")}\n")
        }
        if (cur != null) sb.append("  сейчас идёт: с ${ts(cur.optLong("start", 0L))}, лайков ${cur.optInt("likes", 0)}\n")
        if ((ss?.length() ?: 0) == 0 && cur == null) sb.append("сессий ещё не было\n")

        // --- 7. частые ошибки ---
        val errs = root.optJSONObject("errs")
        sb.append("\n--- Частые ошибки (всего за время накопителя) ---\n")
        if (errs == null || errs.length() == 0) {
            sb.append("ошибок не было\n")
        } else {
            val rows = ArrayList<Pair<String, JSONObject>>()
            val kit = errs.keys()
            while (kit.hasNext()) {
                val k = kit.next()
                val e = errs.optJSONObject(k) ?: continue
                rows.add(k to e)
            }
            rows.sortByDescending { it.second.optLong("n", 0L) }
            for (r in rows.take(10)) sb.append("  ×${r.second.optLong("n", 0L)} (${ts(r.second.optLong("first", 0L))} … ${ts(r.second.optLong("last", 0L))}): ${r.first}\n")
        }

        // --- 8. выводы ---
        if (made.size >= 2) {
            val s = made.sorted()
            val mm = pct(s, 0.5)
            val spread = (s.last() - s.first()) * 100.0 / maxOf(mm, 1L)
            val avgRate = (rateMin + rateMax) / 2f
            hints.add("Лимит сайта по факту около $mm лайков за окно (разброс ${s.first()}–${s.last()}, ${if (spread < 15) "стабильный" else "плавающий"})." +
                (if (siteMax > 0 && Math.abs(siteMax - mm) * 100 / maxOf(mm, 1L) > 10L) " Приложение считает лимитом $siteMax: стоит сверить." else "") +
                (if (avgRate > 0f) " При темпе ${rateMin.toInt()}–${rateMax.toInt()} в минуту он набирается примерно за ${hm((mm / avgRate).toLong())}." else "") +
                (if (workHours > 0 && mm > 0L) " Чтобы растянуть его на рабочее окно $workHours ч, темп ≈ ${String.format(Locale.US, "%.1f", mm / (workHours * 60.0))} в минуту." else ""))
        }
        if (winMs > 0L && wins.size >= 2 && Math.abs(med(wins) - winMs / 60000L) > 90L) {
            hints.add("Окно лимита по отказам (медиана ${hm(med(wins))}) расходится с принятым в приложении (${hm(winMs / 60000L)}): окно к моменту отказа считается от первого лайка окна, проверь, откуда ошибка.")
        }
        if (deltas.size >= 2 && Math.abs(med(deltas)) > 15L) {
            hints.add("Расчёт времени сброса ошибается: факт ${if (med(deltas) < 0) "раньше" else "позже"} расчёта в среднем на ${hm(Math.abs(med(deltas)))} (замеров ${deltas.size}). Расчёт окна стоит поправить.")
        }
        if (waits.size >= 3 && viaProbe * 2 > waits.size) hints.add("Сброс чаще ловится пробой ($viaProbe из ${waits.size}), а не расписанием: расчёт слишком осторожный, лайки теряются на простое.")
        val ln = reqTotal("like", "n")
        if (ln >= 50 && reqTotal("like", "bad") * 100 / ln >= 3L) hints.add("Доля неудачных лайков ${ratio(reqTotal("like", "bad"), ln)}: смотри причины неудач и частые ошибки выше.")
        val sn = reqTotal("search", "n")
        if (sn >= 20 && reqTotal("search", "bad") * 100 / sn >= 5L) hints.add("Доля неудачных запросов поиска ${ratio(reqTotal("search", "bad"), sn)}: смотри причины неудач выше.")
        val ll = nums(root.optJSONObject("lat")?.optJSONArray("like")).sorted()
        if (ll.size >= 20 && pct(ll, 0.95) > 3000L) hints.add("Сайт отвечает на лайк медленно: p95 ${pct(ll, 0.95)} мс. Это съедает темп.")
        if (starts24 >= 3) hints.add("За сутки процесс запускался $starts24 раз: система часто его убивает (см. «Завершения процесса» и «Батарея»). Нужно держать службу живой надёжнее.")
        if (interrupted >= 2) hints.add("Сессий прервано без «Стоп»: $interrupted. Служба умирает сама: проверь ограничения батареи и фоновой работы.")
        if (evTotal("renderGone") > 0L) hints.add("Страница сайта падала ${evTotal("renderGone")} раз: нехватка памяти у WebView.")
        if (evTotal("wdTimeout") >= 5L) hints.add("Страница не отвечала 20 с уже ${evTotal("wdTimeout")} раз: проверь сеть и зависание скрытого окна.")
        var fSeen = 0L
        var fLiked = 0L
        var fBatches = 0L
        var fEmpty = 0L
        for (k in keys) {
            val f = dayObj(k).optJSONObject("feed") ?: continue
            fSeen += f.optLong("seen", 0L)
            fLiked += f.optLong("liked", 0L)
            fBatches += f.optLong("batches", 0L)
            fEmpty += f.optLong("empty", 0L)
        }
        if (fSeen >= 200L && fLiked * 100 / fSeen >= 60L) hints.add("В ленте ${ratio(fLiked, fSeen)} позиций уже лайкнуто сайтом: поиск впустую листает одно и то же, нужны другие срезы или фильтры.")
        if (fBatches >= 20L && fEmpty * 100 / fBatches >= 60L) hints.add("Пустых блоков ленты ${ratio(fEmpty, fBatches)}: новых анкет по текущему поиску почти нет.")
        if (keys.size >= 3) {
            val last = keys.dropLast(1).takeLast(1).firstOrNull()
            val prev = keys.dropLast(1).dropLast(1).takeLast(6).map { dayObj(it).optLong("likes", 0L) }
            if (last != null && prev.isNotEmpty()) {
                val lv = dayObj(last).optLong("likes", 0L)
                val pv = med(prev)
                if (pv >= 100L && lv * 2 < pv) hints.add("Вчера ($last) лайков $lv, это меньше половины обычного (медиана $pv): смотри события и ошибки за тот день.")
            }
        }
        sb.append("\n--- Выводы ---\n")
        if (hints.isEmpty()) sb.append("явных проблем по накопленным данным нет (данных может быть ещё мало)\n") else for (h in hints) sb.append("• ").append(h).append("\n")
        return sb.toString()
    }
}

// ---------- устройство и процесс ----------

private fun mskStamp(): String {
    val f = SimpleDateFormat("dd.MM HH:mm:ss", Locale("ru"))
    f.timeZone = TimeZone.getTimeZone("Europe/Moscow")
    return f.format(Date())
}

// Всё об устройстве и состоянии системы, что влияет на фоновую работу: батарея, сеть, память, WebView, ограничения
fun envText(ctx: Context): String {
    val sb = StringBuilder()
    sb.append("время сбора: ${mskStamp()} МСК, часовой пояс телефона ${TimeZone.getDefault().id}, язык ${Locale.getDefault()}\n")
    sb.append("телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), патч безопасности ${Build.VERSION.SECURITY_PATCH}\n")
    try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val f = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
        f.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        sb.append("приложение: версия ${pi.versionName}, установлено ${f.format(Date(pi.firstInstallTime))}, обновлено ${f.format(Date(pi.lastUpdateTime))} МСК\n")
    } catch (e: Exception) {
    }
    try {
        val wp = WebView.getCurrentWebViewPackage()
        sb.append("WebView: ${wp?.packageName ?: "?"} ${wp?.versionName ?: "?"}\n")
    } catch (e: Exception) {
        sb.append("WebView: не удалось узнать\n")
    }
    try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        sb.append("память телефона: свободно ${mi.availMem shr 20} из ${mi.totalMem shr 20} МБ, порог ${mi.threshold shr 20} МБ, мало памяти: ${mi.lowMemory}, лимит кучи приложения ${am.memoryClass} МБ\n")
        if (Build.VERSION.SDK_INT >= 28) sb.append("фоновая работа ограничена пользователем: ${am.isBackgroundRestricted}\n")
    } catch (e: Exception) {
    }
    try {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        sb.append("батарея: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%, ${if (plugged != 0) "на зарядке" else "не на зарядке"}, температура ${String.format(Locale.US, "%.1f", temp)} °C\n")
        val pm = ctx.getSystemService(PowerManager::class.java)
        sb.append("энергосбережение: ${pm.isPowerSaveMode}, без ограничений батареи для приложения: ${pm.isIgnoringBatteryOptimizations(ctx.packageName)}, сон Doze сейчас: ${pm.isDeviceIdleMode}\n")
    } catch (e: Exception) {
        sb.append("батарея: не удалось узнать (${e.message})\n")
    }
    try {
        if (Build.VERSION.SDK_INT >= 28) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val b = usm.appStandbyBucket
            val name = when {
                b <= 10 -> "активное"
                b <= 20 -> "рабочий набор"
                b <= 30 -> "частые"
                b <= 40 -> "редкие"
                b <= 45 -> "ограниченное"
                else -> "не используется"
            }
            sb.append("корзина ожидания приложения: $name ($b)\n")
        }
    } catch (e: Exception) {
    }
    try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) {
            sb.append("сеть: нет\n")
        } else {
            val tr = ArrayList<String>()
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) tr.add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) tr.add("мобильная")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) tr.add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) tr.add("Ethernet")
            sb.append("сеть: ${if (tr.isEmpty()) "другая" else tr.joinToString("+")}, проверена системой: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}, ")
            sb.append("без счётчика трафика: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}, скорость вниз ${caps.linkDownstreamBandwidthKbps} кбит/с, вверх ${caps.linkUpstreamBandwidthKbps} кбит/с, экономия трафика: ${cm.restrictBackgroundStatus}\n")
        }
    } catch (e: Exception) {
    }
    try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val loc = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        sb.append("уведомления разрешены: ${nm.areNotificationsEnabled()}, геолокация разрешена: $loc, свободно места в приложении ${ctx.filesDir.usableSpace shr 20} МБ\n")
    } catch (e: Exception) {
    }
    sb.append(exitReasonsText(ctx)).append("\n")
    return sb.toString()
}

// Состояние процесса, в котором работает сайт (у каждого сайта свой процесс)
fun procLine(): String {
    val rt = Runtime.getRuntime()
    val used = (rt.totalMemory() - rt.freeMemory()) shr 20
    val max = rt.maxMemory() shr 20
    val up = (SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 60000L
    return "процесс ${android.os.Process.myPid()}: работает $up мин, куча $used из $max МБ, потоков ${Thread.activeCount()}"
}

// Почему система в последний раз завершала процессы приложения (Android 11+): убийство из-за памяти, остановка пользователем, сбой
fun exitReasonsText(ctx: Context): String {
    if (Build.VERSION.SDK_INT < 30) return "завершения процессов: журнал системы есть только на Android 11 и новее"
    return try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val list = am.getHistoricalProcessExitReasons(null, 0, 10)
        if (list.isEmpty()) {
            "завершения процессов (по журналу системы): записей нет"
        } else {
            val f = SimpleDateFormat("d.MM HH:mm", Locale("ru"))
            f.timeZone = TimeZone.getTimeZone("Europe/Moscow")
            "завершения процессов (по журналу системы, свежие сверху):\n" + list.joinToString("\n") { x ->
                val why = when (x.reason) {
                    1 -> "сам вышел"
                    2 -> "убит сигналом"
                    3 -> "нехватка памяти"
                    4 -> "сбой (Java)"
                    5 -> "сбой (native)"
                    6 -> "не отвечал (ANR)"
                    7 -> "не смог запуститься"
                    8 -> "смена разрешений"
                    9 -> "чрезмерное потребление ресурсов"
                    10 -> "завершён пользователем"
                    11 -> "остановлен пользователем"
                    12 -> "умер связанный процесс"
                    13 -> "прочее"
                    14 -> "заморожен системой"
                    else -> "неизвестно"
                }
                "  ${f.format(Date(x.timestamp))} ${x.processName}: $why, важность ${x.importance}, ${(x.description ?: "").take(100)}"
            }
        }
    } catch (e: Exception) {
        "завершения процессов: не удалось прочитать (${e.message})"
    }
}


// Короткая версия автоанализа: выводы, частые ошибки, причины неудач, лимиты, последние сессии. Остальное (по дням, по часам, события) не нужно
fun compactReport(rep: String): String {
    val out = StringBuilder()
    var name = ""
    var lines = ArrayList<String>()
    fun flush() {
        if (name.isEmpty()) return
        val keep: List<String>? = when {
            name.startsWith("Выводы") -> lines
            name.startsWith("Частые ошибки") -> lines.take(6)
            name.startsWith("Лимит сайта по истории") -> lines.take(5)
            name.startsWith("Запросы к сайту") -> lines.filter { it.startsWith("лайк:") || it.startsWith("поиск:") || it.startsWith("первая страница:") || it.startsWith("причины") || it.startsWith("фактический") }.take(6)
            name.startsWith("Сессии") -> lines.takeLast(4)
            else -> null
        }
        if (keep != null && keep.isNotEmpty()) {
            out.append("--- ").append(name.substringBefore(" (")).append(" ---\n")
            for (l in keep) out.append(l.take(220)).append("\n")
        }
        lines = ArrayList()
        name = ""
    }
    for (l in rep.split("\n")) {
        if (l.startsWith("--- ") && l.trimEnd().endsWith(" ---")) {
            flush()
            name = l.trim().removePrefix("--- ").removeSuffix(" ---")
        } else if (name.isNotEmpty() && l.isNotBlank()) {
            lines.add(l)
        }
    }
    flush()
    return out.toString()
}

// Короткое описание устройства: без списка завершений процессов, места, корзины ожидания и прочих мелочей
fun compactEnv(env: String): String {
    val keep = listOf("телефон:", "приложение:", "WebView:", "память телефона:", "фоновая работа", "батарея:", "энергосбережение:", "сеть:", "уведомления разрешены")
    val sb = StringBuilder()
    var kills = 0
    for (l in env.split("\n")) {
        if (l.startsWith("  ")) {
            // завершения процессов: оставляем только процессы приложения и не больше 3
            if (l.contains("nugggets") && kills < 3) {
                sb.append(l.take(170)).append("\n")
                kills++
            }
        } else if (l.startsWith("завершения процессов")) {
            sb.append("завершения процессов приложения (свежие сверху):\n")
        } else if (keep.any { l.startsWith(it) }) {
            sb.append(l.take(200)).append("\n")
        }
    }
    return sb.toString()
}


// Убираем из User-Agent признак встроенного окна: «; wv» и «Version/4.0». Часть сайтов (входы через Mail.ru, VK, Google) на таком окне показывает белый экран
fun cleanUa(ua: String): String = ua.replace("; wv", "").replace(" Version/4.0", "")

// Геопозицию отдаём сайту сервиса и его вложенным окнам с другого домена (Love.Mail.Ru показывает ленту Mamba)
fun geoHostOk(svcId: String, hostSuffix: String, origin: String): Boolean {
    if (origin.contains(hostSuffix)) return true
    val extra = when (svcId) {
        "lmail" -> listOf("mamba.ru", "wamba.com", "mail.ru", "imgsmail.ru")
        "lplanet" -> listOf("loveplanet")
        else -> emptyList()
    }
    return extra.any { origin.contains(it) }
}
