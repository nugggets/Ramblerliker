package com.nugggets.ramblerliker

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/** Общие настройки: одни значения темпа, окна работы, перерывов и пауз сразу для всех сайтов. */
class CommonSettingsActivity : Activity() {
    private var on = true
    private var wifi = false
    private lateinit var swOn: TextView
    private lateinit var swWifi: TextView
    private lateinit var etRate: EditText
    private lateinit var etWinFrom: EditText
    private lateinit var etWinTo: EditText
    private lateinit var etDays: EditText
    private lateinit var etMaxHour: EditText
    private lateinit var etBreakEvery: EditText
    private lateinit var etBreakMin: EditText
    private lateinit var etMaxErr: EditText
    private lateinit var etErrPause: EditText
    private lateinit var etFeedPause: EditText
    private lateinit var etAuthPause: EditText
    private lateinit var etProbe: EditText

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun fmt(f: Float): String = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    private fun field(card: LinearLayout, label: String, e: EditText) {
        card.addView(tv(label, 12f, Pal.MUTED), lp(MATCH, WRAP, t = 14))
        card.addView(e, lp(MATCH, WRAP, t = 6))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        val c = Common.read(this)
        on = c.optBoolean("on", true)
        wifi = c.optBoolean("wifiOnly", false)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(22), dp(16), dp(28))
        col.addView(tv("Общие настройки", 26f, Pal.TEXT, true))
        col.addView(
            tv(
                "Одни значения для всех сайтов сразу. Пока переключатель включён, они важнее личных настроек каждого сайта. " +
                    "Действуют сразу, перезапускать лайки не нужно.", 13f, Pal.MUTED
            ), lp(MATCH, WRAP, t = 6, b = 14)
        )

        val card = card()
        swOn = pill("", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 10) {
            on = !on
            paintSwitches()
        }
        card.addView(swOn, lp(MATCH, WRAP))
        etRate = inp("10-30", fmt(c.optDouble("rateMin", 10.0).toFloat()) + "-" + fmt(c.optDouble("rateMax", 30.0).toFloat()), false)
        field(card, "Лайков в минуту, от–до (пауза между лайками случайная)", etRate)
        etWinFrom = inp("с (час)", c.optInt("winFrom", 0).toString(), true)
        etWinTo = inp("до (час)", c.optInt("winTo", 24).toString(), true)
        card.addView(tv("Окно работы по Москве, часы от и до (0 и 24 = круглосуточно)", 12f, Pal.MUTED), lp(MATCH, WRAP, t = 14))
        val wr = LinearLayout(this)
        wr.addView(etWinFrom, lp(0, WRAP, 1f, r = 8))
        wr.addView(etWinTo, lp(0, WRAP, 1f))
        card.addView(wr, lp(MATCH, WRAP, t = 6))
        etDays = inp("1234567", c.optString("days", "1234567"), false)
        field(card, "Дни недели: цифры 1–7, где 1 понедельник (1234567 = каждый день)", etDays)
        col.addView(card, lp(MATCH, WRAP))

        val br = card()
        br.addView(tv("Перерывы и паузы", 16f, Pal.TEXT, true))
        etMaxHour = inp("0", c.optInt("maxHour", 0).toString(), true)
        field(br, "Лайков в час, не больше (0 = без ограничения)", etMaxHour)
        etBreakEvery = inp("лайков", c.optInt("breakEvery", 0).toString(), true)
        etBreakMin = inp("минут", c.optInt("breakMin", 0).toString(), true)
        br.addView(tv("Перерыв: после скольких лайков и на сколько минут (0 = без перерывов)", 12f, Pal.MUTED), lp(MATCH, WRAP, t = 14))
        val brr = LinearLayout(this)
        brr.addView(etBreakEvery, lp(0, WRAP, 1f, r = 8))
        brr.addView(etBreakMin, lp(0, WRAP, 1f))
        br.addView(brr, lp(MATCH, WRAP, t = 6))
        etMaxErr = inp("3", c.optInt("maxErr", 3).toString(), true)
        field(br, "Ошибок подряд до паузы", etMaxErr)
        etErrPause = inp("0", c.optInt("errPauseMin", 0).toString(), true)
        field(br, "Пауза после серии ошибок, минут (0 = нарастающая 5, 10, 20, 30)", etErrPause)
        etFeedPause = inp("45", c.optInt("feedPauseMin", 45).toString(), true)
        field(br, "Пауза, когда анкеты закончились, минут (поиск начнётся заново; плюс-минус треть случайно)", etFeedPause)
        etAuthPause = inp("30", c.optInt("authPauseMin", 30).toString(), true)
        field(br, "Пауза после неудачного входа, минут", etAuthPause)
        etProbe = inp("30", c.optInt("probeMin", 30).toString(), true)
        field(br, "Проверять, не сбросился ли лимит сайта, раз в столько минут (0 = не проверять)", etProbe)
        swWifi = pill("", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 10) {
            wifi = !wifi
            paintSwitches()
        }
        br.addView(swWifi, lp(MATCH, WRAP, t = 14))
        col.addView(br, lp(MATCH, WRAP, t = 12))

        col.addView(pill("Сохранить", Pal.INFO, Pal.WHITE) { save() }, lp(MATCH, WRAP, t = 16))
        col.addView(pill("Назад", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 10) { finish() }, lp(MATCH, WRAP, t = 8))
        paintSwitches()

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Pal.BG)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun paintSwitches() {
        swOn.text = if (on) "✅  Общие настройки включены для всех сайтов" else "⛔  Общие настройки выключены, у каждого сайта свои"
        swWifi.text = if (wifi) "📶  Только по Wi-Fi: да" else "📶  Только по Wi-Fi: нет"
    }

    private fun num(e: EditText, def: Int, lo: Int, hi: Int): Int = e.text.toString().trim().toIntOrNull()?.coerceIn(lo, hi) ?: def

    private fun save() {
        val parts = etRate.text.toString().replace(',', '.').split('-', '–', '—').mapNotNull { it.trim().toFloatOrNull() }
        if (parts.isEmpty() || parts.any { it <= 0f }) {
            toast("Темп: два числа через дефис, например 10-30")
            return
        }
        val a = parts[0]
        val b = if (parts.size > 1) parts[1] else a
        val wf = num(etWinFrom, 0, 0, 23)
        val wt = num(etWinTo, 24, 1, 24)
        if (wt <= wf) {
            toast("Окно работы: «до» должно быть больше «с»")
            return
        }
        val days = etDays.text.toString().filter { it in '1'..'7' }.toSet().sorted().joinToString("").ifEmpty { "1234567" }
        val j = JSONObject()
        j.put("on", on)
        j.put("rateMin", minOf(a, b).coerceIn(0.5f, 600f).toDouble())
        j.put("rateMax", maxOf(a, b).coerceIn(0.5f, 600f).toDouble())
        j.put("winFrom", wf)
        j.put("winTo", wt)
        j.put("days", days)
        j.put("maxHour", num(etMaxHour, 0, 0, 100000))
        j.put("breakEvery", num(etBreakEvery, 0, 0, 100000))
        j.put("breakMin", num(etBreakMin, 0, 0, 1440))
        j.put("maxErr", num(etMaxErr, 3, 1, 50))
        j.put("errPauseMin", num(etErrPause, 0, 0, 1440))
        j.put("feedPauseMin", num(etFeedPause, 45, 1, 1440))
        j.put("authPauseMin", num(etAuthPause, 30, 1, 1440))
        j.put("probeMin", num(etProbe, 30, 0, 1440))
        j.put("wifiOnly", wifi)
        Common.save(this, j)
        toast(if (on) "Сохранено, действует на всех сайтах" else "Сохранено, но переключатель выключен: у сайтов свои настройки")
    }
}
