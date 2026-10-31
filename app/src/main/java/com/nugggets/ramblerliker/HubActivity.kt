package com.nugggets.ramblerliker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// Одна карточка сервиса на главном экране
private class SvcCard(
    val sv: Svc,
    val chip: TextView,
    val verdict: TextView,
    val site: TextView,
    val count: TextView,
    val rate: TextView,
    val bar: BarView,
    val stats: TextView,
    val hint: TextView,
    val toggle: TextView
) {
    var mode = ""
}

/**
 * Главный экран: все три сервиса в одном месте. Здесь видно, что делает каждый, и можно запустить или
 * остановить его одной кнопкой. Сами лайки по-прежнему ставят службы в своих процессах (у каждого сервиса
 * свои cookie), а этот экран только читает их статус и шлёт им команды «старт» и «стоп».
 */
class HubActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val cards = HashMap<String, SvcCard>()
    private val pending = ArrayList<Svc>()
    private lateinit var totalNum: TextView
    private lateinit var runChip: TextView
    private lateinit var bat: LinearLayout

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 2000)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        askNotifOnce()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(22), dp(16), dp(28))

        // --- заголовок ---
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(tv("Liker", 30f, Pal.TEXT, true))
        titles.addView(tv("Rambler · Mamba · Tabor в одном приложении", 13f, Pal.MUTED))
        head.addView(titles, lp(0, WRAP, 1f))
        head.addView(tv("v" + versionName(), 12f, Pal.MUTED))
        col.addView(head, lp(MATCH, WRAP, b = 16))

        // --- итог и общие кнопки ---
        val sum = card()
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv("лайков сегодня, всего", 12f, Pal.MUTED))
        totalNum = tv("0", 44f, Pal.TEXT, true)
        left.addView(totalNum)
        top.addView(left, lp(0, WRAP, 1f))
        runChip = chip("…", Pal.MUTED)
        top.addView(runChip)
        sum.addView(top)
        val btns = LinearLayout(this)
        btns.addView(pill("▶  Запустить все", Pal.INFO, Pal.WHITE) { startAll() }, lp(0, WRAP, 1f, r = 8))
        btns.addView(pill("■  Остановить все", tint(Pal.ERR, 0x22), Pal.ERR) { stopAll() }, lp(0, WRAP, 1f))
        sum.addView(btns, lp(MATCH, WRAP, t = 14))
        col.addView(sum, lp(MATCH, WRAP))

        // --- предупреждение про батарею ---
        bat = LinearLayout(this)
        bat.orientation = LinearLayout.VERTICAL
        bat.setPadding(dp(14), dp(12), dp(14), dp(12))
        bat.background = roundRect(tint(Pal.WARN, 0x1F), dpf(16f), Pal.WARN, dp(1))
        bat.addView(tv("Телефон может закрывать приложение в фоне", 14f, Pal.WARN, true))
        bat.addView(tv("Разреши работу без ограничений батареи, иначе лайки будут останавливаться.", 12f, Pal.MUTED), lp(MATCH, WRAP, t = 4))
        bat.addView(pill("Разрешить", tint(Pal.WARN, 0x33), Pal.WARN, 0, 13f, 9) { askBattery() }, lp(MATCH, WRAP, t = 10))
        bat.visibility = View.GONE
        col.addView(bat, lp(MATCH, WRAP, t = 12))

        // --- сервисы ---
        for (sv in SERVICES) col.addView(buildCard(sv), lp(MATCH, WRAP, t = 12))

        col.addView(
            tv(
                "Каждый сервис работает в своём процессе со своим входом и настройками, лайки идут даже при закрытом окне. " +
                    "Нажми на карточку: там настройки, журнал, отчёт и браузер для входа.",
                12f, Pal.MUTED
            ), lp(MATCH, WRAP, t = 16)
        )

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Pal.BG)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun buildCard(sv: Svc): View {
        val ac = svcColor(sv.id)
        val c = card()
        val r1 = LinearLayout(this)
        r1.gravity = Gravity.CENTER_VERTICAL
        r1.addView(dot(ac), lp(dp(12), dp(12), r = 10))
        r1.addView(tv(sv.title, 19f, Pal.TEXT, true), lp(0, WRAP, 1f))
        val chipV = chip("…", Pal.MUTED)
        r1.addView(chipV)
        c.addView(r1)
        val verdict = tv("", 13f, Pal.MUTED)
        verdict.maxLines = 2
        verdict.ellipsize = TextUtils.TruncateAt.END
        c.addView(verdict, lp(MATCH, WRAP, t = 8))
        val site = tv("", 14f, ac, true)
        c.addView(site, lp(MATCH, WRAP, t = 8))
        val r2 = LinearLayout(this)
        r2.gravity = Gravity.BOTTOM
        val count = tv("0 / 0", 22f, Pal.TEXT, true)
        val rate = tv("", 12f, Pal.MUTED)
        r2.addView(count, lp(0, WRAP, 1f))
        r2.addView(rate, lp(WRAP, WRAP, b = 3))
        c.addView(r2, lp(MATCH, WRAP, t = 12))
        val bar = BarView(this)
        bar.barColor = ac
        c.addView(bar, lp(MATCH, dp(8), t = 6))
        val stats = tv("", 12f, Pal.MUTED)
        c.addView(stats, lp(MATCH, WRAP, t = 8))
        val hint = tv("", 12f, Pal.WARN)
        hint.visibility = View.GONE
        c.addView(hint, lp(MATCH, WRAP, t = 8))
        val r3 = LinearLayout(this)
        val toggle = pill("▶  Старт", ac, Pal.WHITE) { onToggle(sv) }
        val open = pill("Открыть", Pal.SURFACE2, Pal.TEXT, Pal.STROKE) { openSvc(sv, 0) }
        r3.addView(toggle, lp(0, WRAP, 1.5f, r = 8))
        r3.addView(open, lp(0, WRAP, 1f))
        c.addView(r3, lp(MATCH, WRAP, t = 12))
        c.setOnClickListener { openSvc(sv, 0) }
        cards[sv.id] = SvcCard(sv, chipV, verdict, site, count, rate, bar, stats, hint, toggle)
        return c
    }

    // ---------- обновление ----------
    private fun refresh() {
        var total = 0
        var running = 0
        for (sv in SERVICES) {
            val s = readStatus(this, sv)
            val cd = cards[sv.id] ?: continue
            val cnt = countToday(s)
            total += cnt
            if (s.running) running++
            bind(cd, s, cnt)
        }
        totalNum.setIf(total.toString())
        runChip.setChip("Работает $running из ${SERVICES.size}", if (running > 0) Pal.OK else Pal.MUTED)
        bat.visibility = if (batteryOk()) View.GONE else View.VISIBLE
    }

    private fun bind(cd: SvcCard, s: SvcStatus, cnt: Int) {
        val st = effectiveState(s)
        val ac = svcColor(cd.sv.id)
        cd.chip.setChip(stateLabel(st), stateColor(st))
        cd.verdict.setIf(
            if (s.exists) s.verdict
            else "Ещё не запускался. Нажми «Открыть», войди в аккаунт на вкладке «Браузер» и запусти."
        )
        cd.site.setIf(if (s.exists && s.site.isNotEmpty()) s.site else if (cd.sv.siteLimit > 0) "Лимит сайта: ${cd.sv.siteLimit} лайков за ${cd.sv.limitWindowMs / 3600000L} ч\nОкно считается от первого лайка" else "Лимит сайта: появится после первого запуска")
        cd.count.setIf(if (s.limit <= 0 || s.limit >= MAX_LIMIT) "$cnt" else "$cnt / ${s.limit}")
        cd.rate.setIf("темп ${s.rate} в мин  ·  окно ${s.win} МСК")
        cd.bar.frac = if (s.limit > 0 && s.limit < MAX_LIMIT) cnt.toFloat() / s.limit else 0f
        cd.stats.setIf("за час ${s.hour}  ·  за сессию ${s.sess}  ·  в очереди ${s.queue}  ·  ошибок ${s.errors}")
        val needLogin = s.exists && !s.running && !s.hasSession
        if (needLogin) {
            cd.hint.setIf("Нет сохранённого входа: открой сервис и войди на вкладке «Браузер».")
            cd.hint.visibility = View.VISIBLE
        } else {
            cd.hint.visibility = View.GONE
        }
        val mode = if (s.running) "stop" else if (needLogin) "login" else "start"
        if (cd.mode != mode) {
            cd.mode = mode
            when (mode) {
                "stop" -> {
                    cd.toggle.text = "■  Остановить"
                    cd.toggle.paintBtn(tint(Pal.ERR, 0x33), Pal.ERR, Pal.ERR)
                }
                "login" -> {
                    cd.toggle.text = "🔑  Войти"
                    cd.toggle.paintBtn(tint(ac, 0x33), ac, ac)
                }
                else -> {
                    cd.toggle.text = "▶  Старт"
                    cd.toggle.paintBtn(ac, Pal.WHITE)
                }
            }
        }
    }

    // ---------- команды сервисам ----------
    private fun openSvc(sv: Svc, tab: Int) {
        startActivity(Intent(this, sv.activityCls).putExtra("tab", tab))
    }

    private fun sendCmd(sv: Svc, action: String) {
        try {
            startForegroundService(Intent(this, sv.keepCls).setAction(action))
        } catch (e: Exception) {
            toast("Не удалось: ${e.message}")
        }
        h.postDelayed({ refresh() }, 900)
    }

    private fun onToggle(sv: Svc) {
        val s = readStatus(this, sv)
        if (s.running) {
            sendCmd(sv, KeepAliveService.ACT_STOP)
            return
        }
        if (s.exists && !s.hasSession) {
            openSvc(sv, 3) // без входа лайки не пойдут: сразу открываем браузер
            return
        }
        startSvc(sv)
    }

    private fun startSvc(sv: Svc) {
        val p = getSharedPreferences("hub", MODE_PRIVATE)
        if (sv.id != "rambler" && !hasLoc() && !p.getBoolean("locAsked", false)) {
            // сайтам Mamba и Tabor нужна геопозиция: спрашиваем один раз, потом запускаем
            p.edit().putBoolean("locAsked", true).apply()
            pending.add(sv)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 3)
            return
        }
        sendCmd(sv, KeepAliveService.ACT_START)
    }

    private fun startAll() {
        var skipped = ""
        for (sv in SERVICES) {
            val s = readStatus(this, sv)
            if (s.running) continue
            if (s.exists && !s.hasSession) {
                skipped += (if (skipped.isEmpty()) "" else ", ") + sv.title
                continue
            }
            startSvc(sv)
        }
        if (skipped.isNotEmpty()) toast("Нужен вход: $skipped")
    }

    private fun stopAll() {
        for (sv in SERVICES) {
            if (readStatus(this, sv).running) sendCmd(sv, KeepAliveService.ACT_STOP)
        }
    }

    // ---------- разрешения ----------
    private fun hasLoc(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askNotifOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val p = getSharedPreferences("hub", MODE_PRIVATE)
        if (p.getBoolean("notifAsked", false)) return
        p.edit().putBoolean("notifAsked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3) {
            val list = ArrayList(pending)
            pending.clear()
            for (sv in list) sendCmd(sv, KeepAliveService.ACT_START)
        }
    }

    private fun batteryOk(): Boolean = try {
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) {
        true
    }

    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
            }
        }
    }

    // ---------- жизненный цикл ----------
    override fun onResume() {
        super.onResume()
        h.removeCallbacks(ticker)
        h.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(ticker)
    }
}
