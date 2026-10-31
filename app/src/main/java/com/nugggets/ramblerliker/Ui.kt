package com.nugggets.ramblerliker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- палитра ----------
object Pal {
    val BG = 0xFF0E1116.toInt()
    val SURFACE = 0xFF171B22.toInt()
    val SURFACE2 = 0xFF222936.toInt()
    val STROKE = 0xFF2B3342.toInt()
    val TEXT = 0xFFE8ECF3.toInt()
    val MUTED = 0xFF8D97A9.toInt()
    val OK = 0xFF3DDC84.toInt()
    val WARN = 0xFFFFB020.toInt()
    val ERR = 0xFFFF6B6B.toInt()
    val INFO = 0xFF5BA4FF.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
}

// Цвет сервиса: по нему узнаётся карточка и окно
fun svcColor(id: String): Int = when (id) {
    "mamba" -> 0xFFFF5FA2.toInt()
    "tabor" -> 0xFF2EC4B6.toInt()
    "ok" -> 0xFFEE8208.toInt()
    else -> 0xFF4C8DFF.toInt()
}

// Тот же цвет с прозрачностью a (0..255)
fun tint(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

const val MATCH = -1
const val WRAP = -2

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

// Параметры в LinearLayout: w и h в пикселях (или MATCH/WRAP), отступы в dp
fun Context.lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h, weight)
    p.setMargins(dp(l), dp(t), dp(r), dp(b))
    return p
}

fun roundRect(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.cornerRadius = radiusPx
    d.setColor(color)
    if (strokePx > 0) d.setStroke(strokePx, strokeColor)
    return d
}

// Кнопка-текст: заливка, цвет текста, рамка и волна при нажатии
fun TextView.paintBtn(fill: Int, txt: Int, stroke: Int = 0, radiusDp: Int = 12) {
    setTextColor(txt)
    val r = context.dpf(radiusDp.toFloat())
    val d = roundRect(fill, r, stroke, if (stroke != 0) context.dp(1) else 0)
    background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), d, roundRect(-1, r))
}

fun Context.tv(text: String, size: Float = 14f, color: Int = Pal.TEXT, bold: Boolean = false): TextView {
    val v = TextView(this)
    v.text = text
    v.textSize = size
    v.setTextColor(color)
    if (bold) v.setTypeface(null, Typeface.BOLD)
    return v
}

fun Context.pill(
    text: String,
    fill: Int,
    txt: Int,
    stroke: Int = 0,
    size: Float = 14f,
    vpad: Int = 11,
    onClick: () -> Unit
): TextView {
    val v = TextView(this)
    v.text = text
    v.textSize = size
    v.gravity = Gravity.CENTER
    v.isClickable = true
    v.setTypeface(null, Typeface.BOLD)
    v.setPadding(dp(12), dp(vpad), dp(12), dp(vpad))
    v.paintBtn(fill, txt, stroke)
    v.setOnClickListener { onClick() }
    return v
}

// Метка-«таблетка» (состояние сервиса)
fun Context.chip(text: String, color: Int): TextView {
    val v = TextView(this)
    v.text = text
    v.textSize = 11f
    v.setTypeface(null, Typeface.BOLD)
    v.setTextColor(color)
    v.setPadding(dp(10), dp(4), dp(10), dp(4))
    v.background = roundRect(tint(color, 0x2E), dpf(20f))
    v.tag = color
    return v
}

fun TextView.setChip(t: String, color: Int) {
    if (text.toString() != t) text = t
    if (tag != color) {
        tag = color
        setTextColor(color)
        background = roundRect(tint(color, 0x2E), context.dpf(20f))
    }
}

// Не трогаем TextView, если текст тот же (меньше перерисовок)
fun TextView.setIf(t: CharSequence) {
    if (text.toString() != t.toString()) text = t
}

fun Context.card(): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(dp(14), dp(14), dp(14), dp(14))
    c.background = roundRect(Pal.SURFACE, dpf(16f), Pal.STROKE, dp(1))
    return c
}

fun Context.inp(hint: String, text: String, number: Boolean): EditText {
    val e = EditText(this)
    e.hint = hint
    e.inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
    e.setSingleLine()
    e.setText(text)
    e.textSize = 14f
    e.setTextColor(Pal.TEXT)
    e.setHintTextColor(Pal.MUTED)
    e.background = roundRect(Pal.SURFACE2, dpf(10f), Pal.STROKE, dp(1))
    e.setPadding(dp(12), dp(10), dp(12), dp(10))
    return e
}

fun Context.dot(color: Int): View {
    val v = View(this)
    val d = GradientDrawable()
    d.shape = GradientDrawable.OVAL
    d.setColor(color)
    v.background = d
    return v
}

// Полоса прогресса
class BarView(ctx: Context) : View(ctx) {
    var frac: Float = 0f
        set(v) {
            val x = v.coerceIn(0f, 1f)
            if (x != field) {
                field = x
                invalidate()
            }
        }
    var barColor: Int = Pal.OK
        set(v) {
            field = v
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = Pal.SURFACE2
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, h / 2, h / 2, p)
        if (frac > 0f) {
            p.color = barColor
            r.set(0f, 0f, maxOf(h, w * frac), h)
            c.drawRoundRect(r, h / 2, h / 2, p)
        }
    }
}

// Столбики по часам суток (0–23)
class HoursView(ctx: Context) : View(ctx) {
    var data: IntArray = IntArray(24)
        set(v) {
            field = v
            invalidate()
        }
    var barColor: Int = Pal.INFO
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        tp.textSize = ctx.dpf(10f)
        tp.color = Pal.MUTED
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val chartH = h - tp.textSize - 6f
        var mx = 1
        for (v in data) if (v > mx) mx = v
        val slot = w / 24f
        val bw = slot * 0.62f
        for (i in 0 until 24) {
            val v = data[i]
            val bh = if (v <= 0) 3f else maxOf(6f, chartH * v / mx)
            val x = i * slot + (slot - bw) / 2f
            p.color = if (v > 0) barColor else Pal.SURFACE2
            r.set(x, chartH - bh, x + bw, chartH)
            c.drawRoundRect(r, bw / 3f, bw / 3f, p)
        }
        for (i in intArrayOf(0, 6, 12, 18, 23)) {
            c.drawText(i.toString(), i * slot + 2f, h - 2f, tp)
        }
    }
}

// ---------- статус сервисов (его пишет движок каждого сервиса, читает главное окно) ----------
class SvcStatus(
    val exists: Boolean,
    val running: Boolean,
    val state: String,
    val verdict: String,
    val count: Int,
    val limit: Int,
    val sess: Int,
    val hour: Int,
    val queue: Int,
    val errors: Int,
    val rate: String,
    val next: String,
    val win: String,
    val hasSession: Boolean,
    val ts: Long,
    val day: String,
    val site: String = ""
)

fun readStatus(ctx: Context, sv: Svc): SvcStatus {
    try {
        val f = File(ctx.filesDir, "status${sv.suffix}.json")
        if (f.exists()) {
            val j = JSONObject(f.readText())
            return SvcStatus(
                true,
                j.optBoolean("running", false),
                j.optString("state", "off"),
                j.optString("verdict", ""),
                j.optInt("count", 0),
                j.optInt("limit", DEFAULT_LIMIT),
                j.optInt("sess", 0),
                j.optInt("hour", 0),
                j.optInt("queue", 0),
                j.optInt("errors", 0),
                j.optString("rate", "60-80"),
                j.optString("next", "-"),
                j.optString("win", "$DEFAULT_WIN_FROM–$DEFAULT_WIN_TO"),
                j.optBoolean("session", false),
                j.optLong("ts", 0L),
                j.optString("day", ""),
                j.optString("site", "")
            )
        }
    } catch (e: Exception) {
    }
    return SvcStatus(
        false, false, "off", "", 0, DEFAULT_LIMIT, 0, 0, 0, 0, "60-80", "-",
        "$DEFAULT_WIN_FROM–$DEFAULT_WIN_TO", false, 0L, ""
    )
}

// Служба числится работающей, но давно не обновляла статус: процесс закрыт системой
fun effectiveState(s: SvcStatus): String =
    if (s.running && System.currentTimeMillis() - s.ts > 25000L) "stale" else s.state

fun stateLabel(st: String): String = when (st) {
    "ok" -> "Лайки идут"
    "wait" -> "Запуск…"
    "limit" -> "Лимит"
    "night" -> "Вне окна"
    "auth" -> "Вход…"
    "err" -> "Ошибки"
    "stale" -> "Нет отклика"
    else -> "Остановлен"
}

fun stateColor(st: String): Int = when (st) {
    "ok" -> Pal.OK
    "wait" -> Pal.INFO
    "limit", "night", "auth", "stale" -> Pal.WARN
    "err" -> Pal.ERR
    else -> Pal.MUTED
}

fun todayMsk(): String {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    f.timeZone = TimeZone.getTimeZone("Europe/Moscow")
    return f.format(Date())
}

// Счётчик «сегодня»: если день в статусе вчерашний, то сегодня ещё 0
fun countToday(s: SvcStatus): Int = if (s.day == todayMsk()) s.count else 0
