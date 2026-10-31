package com.nugggets.ramblerliker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.webkit.RenderProcessGoneDetail
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone

private const val HOOK_JS = """
(function(){
 if(window.__rec) return; window.__rec=1;
 var send=function(o){try{Rec.push(JSON.stringify(o))}catch(e){}};
 var of=window.fetch;
 window.__origFetch=of;
 if(of){ window.fetch=function(i,init){
   var url=(typeof i==='string')?i:((i&&i.url)||'');
   var m=(init&&init.method)||(i&&i.method)||'GET';
   var b=(init&&init.body)||'';
   return of.apply(this,arguments).then(function(r){
     try{ r.clone().text().then(function(t){ send({m:m,u:url,s:r.status,p:String(b).slice(0,1000),r:t.slice(0,4000)}); }); }catch(e){}
     return r; });
 }; }
 var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
 XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;return xo.apply(this,arguments)};
 XMLHttpRequest.prototype.send=function(b){
   var x=this;
   this.addEventListener('loadend',function(){
     var t=''; try{t=x.responseText||''}catch(e){}
     send({m:x.__m,u:x.__u,s:x.status,p:String(b||'').slice(0,1000),r:t.slice(0,4000)});
   });
   return xs.apply(this,arguments)};
})();
"""

private var lastRecreate = 0L

open class MainActivity : Activity() {
    // Какой сервис ведёт это окно. Окно Mamba (MambaActivity) живёт в своём процессе, поэтому оба сервиса идут одновременно
    protected open fun serviceId(): String = "rambler"
    private lateinit var engine: Engine
    private lateinit var etLikeMethod: EditText
    private lateinit var etLikeBody: EditText
    private lateinit var etSearchUrl: EditText
    private var geoCb: GeolocationPermissions.Callback? = null
    private var geoOrigin = ""
    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var panels: List<View>
    private lateinit var recText: TextView
    private lateinit var recFilter: EditText
    private lateinit var status: TextView
    private lateinit var etLimit: EditText
    private lateinit var etRate: EditText
    private lateinit var etWinFrom: EditText
    private lateinit var etWinTo: EditText
    private lateinit var etLikeUrl: EditText
    private lateinit var tabBtns: List<Button>
    private lateinit var logText: TextView
    private lateinit var logStats: TextView
    private lateinit var topBar: TextView
    private lateinit var repStats: TextView
    private lateinit var repText: TextView
    private lateinit var autoBtn: Button
    private var onlyToday = true
    private var lastTop = ""
    private var logRenderedVer = -1
    private var renderedOnlyErr = false
    private var lastRestored = 0
    private var curTab = 0
    private var onlyErr = false
    private var hideNoise = true
    private var onlyPost = false
    private val rec = ArrayList<JSONObject>()
    private val h = Handler(Looper.getMainLooper())
    private val timeF = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val mskTz: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val verName: String by lazy {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
    private val noiseHosts = listOf(
        "mc.yandex.ru", "kraken.rambler.ru", "cnt.rambler.ru", "atlas.rambler.ru", "clickstream.sbrf.ru",
        "logger.id.sber.ru", "top100", "/locales/", "id.sber.ru/cms", "googletagmanager",
        "doubleclick", ".png", ".jpg", ".svg", ".woff", "/a-page/popup",
        ".js", ".css", "/api/cubstat", "/api/client_stat", "/api/seo/", "/api/uni-notice", "/api/features", "/api/banners", "/api/gdpr"
    )


    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { f() }
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Engine.get(this, serviceId())
        prefs = engine.prefs
        try {
            val nmc = getSystemService(NotificationManager::class.java)
            nmc.cancel(engine.svc.nidOff + 2)
            nmc.cancel(engine.svc.nidOff + 3)
        } catch (e: Exception) {
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val askPerms = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notifAsked", false)) {
            prefs.edit().putBoolean("notifAsked", true).apply()
            askPerms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // геопозиция нужна сайтам знакомств (Mamba без неё не работает)
        if (engine.svc.id != "rambler" && !engine.hasLoc() && !prefs.getBoolean("locAsked", false)) {
            prefs.edit().putBoolean("locAsked", true).apply()
            askPerms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            askPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (askPerms.isNotEmpty()) requestPermissions(askPerms.toTypedArray(), 1)

        setupWeb()

        // --- шапка-заглушка сверху (только показывает статус) ---
        topBar = TextView(this).apply {
            textSize = 13f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(0xFF263238.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

        // --- вкладка «Браузер»: страница сверху, кнопки внизу ---
        val browser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(
                row(
                    btn("◀ Назад") { if (web.canGoBack()) web.goBack() },
                    btn("⟳ Обновить") { web.reload() },
                    btn("Домой") { web.loadUrl(engine.svc.site) }
                )
            )
        }

        // --- вкладка «Запросы»: список сверху, фильтр и кнопки внизу ---
        recFilter = EditText(this).apply {
            hint = "фильтр: like, search..."
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        recText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        val noiseBtn = btn("Шум: скрыт") {}
        noiseBtn.setOnClickListener {
            hideNoise = !hideNoise
            noiseBtn.text = if (hideNoise) "Шум: скрыт" else "Шум: виден"
            renderRec()
        }
        val postBtn = btn("Все методы") {}
        postBtn.setOnClickListener {
            onlyPost = !onlyPost
            postBtn.text = if (onlyPost) "Только POST" else "Все методы"
            renderRec()
        }
        val recPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
            addView(ScrollView(this@MainActivity).apply { addView(recText) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(recFilter)
            addView(row(noiseBtn, postBtn))
            addView(
                row(
                    btn("Обновить") { renderRec() },
                    btn("Копировать") { copyRec() },
                    btn("Поделиться") { shareRec() },
                    btn("Очистить") { rec.clear(); renderRec() }
                )
            )
        }

        // --- вкладка «Лайки»: статус и настройки сверху, кнопки внизу ---
        status = TextView(this).apply { textSize = 15f }
        etLimit = EditText(this).apply {
            hint = "лимит лайков в сутки"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("limit", DEFAULT_LIMIT).toString())
        }
        etRate = EditText(this).apply {
            hint = "60-80"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setText(engine.rateText())
        }
        etWinFrom = EditText(this).apply {
            hint = "с (час)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("winFrom", DEFAULT_WIN_FROM).toString())
        }
        etWinTo = EditText(this).apply {
            hint = "до (час)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("winTo", DEFAULT_WIN_TO).toString())
        }
        etLikeUrl = EditText(this).apply {
            hint = "URL лайка"
            setSingleLine()
            setText(prefs.getString("likeUrl", engine.svc.defLikeUrl))
        }
        etLikeMethod = EditText(this).apply {
            hint = "GET или POST"
            setSingleLine()
            setText(prefs.getString("likeMethod", engine.svc.defLikeMethod))
        }
        etLikeBody = EditText(this).apply {
            hint = "тело запроса лайка (если есть)"
            setSingleLine()
            setText(prefs.getString("likeBody", engine.svc.defLikeBody))
        }
        etSearchUrl = EditText(this).apply {
            hint = "URL поиска"
            setSingleLine()
            setText(prefs.getString("searchUrl", engine.svc.defSearchUrl))
        }
        val likesScroll = ScrollView(this).apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    addView(TextView(this@MainActivity).apply { text = "Сервис (у каждого своё окно, оба работают одновременно):" })
                    addView(
                        row(
                            *SERVICES.map { sv ->
                                btn(sv.title) { switchService(sv.id) }.apply {
                                    alpha = if (sv.id == engine.svc.id) 1f else 0.5f
                                    setTypeface(null, if (sv.id == engine.svc.id) Typeface.BOLD else Typeface.NORMAL)
                                }
                            }.toTypedArray()
                        )
                    )
                    addView(status)
                    addView(TextView(this@MainActivity).apply { text = "\nЛимит в сутки (макс. $MAX_LIMIT):" })
                    addView(etLimit)
                    addView(TextView(this@MainActivity).apply { text = "Лайков в минуту (от-до; пауза между лайками случайная, поэтому число каждую минуту разное, например 60-80):" })
                    addView(etRate)
                    addView(TextView(this@MainActivity).apply { text = "Окно работы по Москве, часы от и до (0 и 24 = круглосуточно):" })
                    addView(row(etWinFrom, etWinTo))
                    addView(TextView(this@MainActivity).apply { text = "URL лайка ({login} подставляется сам):" })
                    addView(etLikeUrl)
                    addView(TextView(this@MainActivity).apply { text = "Метод лайка:" })
                    addView(etLikeMethod)
                    addView(TextView(this@MainActivity).apply { text = "Тело запроса лайка ({login} подставляется сам):" })
                    addView(etLikeBody)
                    addView(TextView(this@MainActivity).apply { text = "URL поиска ({offset} подставляется сам):" })
                    addView(etSearchUrl)
                    if (engine.svc.id == "mamba") {
                        addView(TextView(this@MainActivity).apply {
                            textSize = 12f
                            text = "Адреса ${engine.svc.title} уже заданы: лента «Рейтинг» и лайк фото. " +
                                "1) Вкладка «Браузер»: войди и открой «Рейтинг». " +
                                "2) Фильтры (пол, возраст, город) задай один раз на самом сайте: приложение берёт их оттуда. " +
                                "3) Поставь лимит 3 и жми «Старт». Лента листается по id последнего фото, лайк ставится на фото."
                        })
                    }
                    if (engine.svc.id == "tabor") {
                        addView(TextView(this@MainActivity).apply {
                            textSize = 12f
                            text = "Адреса ${engine.svc.title} заданы по записи запросов. " +
                                "1) Вкладка «Браузер»: войди (капчу вводи сам) и открой голосование «Симпатии». " +
                                "2) Поставь лимит 3 и жми «Старт». Первые анкеты приложение берёт со страницы, дальше каждая оценка возвращает следующую. " +
                                "3) Если в журнале «Табор: не нашёл анкеты на странице», пришли эту строку целиком."
                        })
                    }
                    addView(TextView(this@MainActivity).apply {
                        textSize = 12f
                        text = "\nПриложение берёт анкеты из поиска сайта (с твоими фильтрами) и шлёт лайки напрямую. " +
                            "Лайки ставит фоновая служба: она работает, даже когда окно закрыто, и сама запускается " +
                            "после перезагрузки телефона и после закрытия приложения системой. Остановить её можно " +
                            "только кнопкой «Стоп». Лайки идут только в окне работы по Москве. Первый тест: лимит 3. " +
                            "Ставятся ли лайки, видно в шапке и на вкладке «Журнал»."
                    })
                }
            )
        }
        val likes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(likesScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(row(btn("▶ Старт") { onStartClicked() }, btn("■ Стоп") { engine.stopLikes("остановлено") }))
            addView(row(btn("Сохранить") { saveSettings() }, btn("URL по умолчанию") { etLikeUrl.setText(engine.svc.defLikeUrl) }))
            autoBtn = btn("") {}
            autoBtn.setOnClickListener {
                val on = !prefs.getBoolean("autoLogin", engine.svc.id == "rambler")
                prefs.edit().putBoolean("autoLogin", on).apply()
                refreshAutoBtn()
                engine.pushAuto()
                engine.logMsg(if (on) "Автовход включён: сам нажимаю «Войти» и «Продолжить»" else "Автовход выключен")
            }
            refreshAutoBtn()
            addView(autoBtn)
            addView(btn("Фоновая работа: разрешить") { setupBackground() })
            addView(btn("Скопировать сессию для сервера") { copySession() })
            addView(btn("Выйти из аккаунта (очистить сессию)") { clearSession() })
        }

        // --- вкладка «Журнал»: записи сверху, кнопки внизу ---
        logStats = TextView(this).apply {
            textSize = 13f
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        logText = TextView(this).apply {
            textSize = 11f
            setPadding(dp(8), 0, dp(8), dp(8))
            setTextIsSelectable(true)
        }
        val errBtn = btn("Все записи") {}
        errBtn.setOnClickListener {
            onlyErr = !onlyErr
            errBtn.text = if (onlyErr) "Только ошибки" else "Все записи"
            renderLog()
        }
        val logPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(logStats)
            addView(ScrollView(this@MainActivity).apply { addView(logText) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(row(errBtn, btn("Очистить") { clearLog() }))
            addView(row(btn("Копировать") { copyLog() }, btn("Поделиться") { shareLog() }))
        }

        // --- вкладка «Отчёт»: статистика и список лайкнутых анкет ---
        repStats = TextView(this).apply {
            textSize = 13f
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        repText = TextView(this).apply {
            textSize = 12f
            setPadding(dp(8), 0, dp(8), dp(8))
            setTextIsSelectable(true)
        }
        val todayBtn = btn("Только сегодня") {}
        todayBtn.setOnClickListener {
            onlyToday = !onlyToday
            todayBtn.text = if (onlyToday) "Только сегодня" else "За всё время"
            renderReport()
        }
        val repPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ScrollView(this@MainActivity).apply {
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(repStats)
                            addView(repText)
                        }
                    )
                },
                LinearLayout.LayoutParams(-1, 0, 1f)
            )
            addView(row(todayBtn, btn("Очистить") { clearReport() }))
            addView(row(btn("Копировать") { copyReport() }, btn("Поделиться") { shareReport() }))
            addView(btn("🔍 Найти «ищу от–до» на открытой анкете") { findAgeFields() })
        }

        panels = listOf(browser, recPanel, likes, logPanel, repPanel)
        tabBtns = listOf(
            btn("Браузер") { show(0) },
            btn("Запросы") { show(1) },
            btn("Лайки") { show(2) },
            btn("Журнал") { show(3) },
            btn("Отчёт") { show(4) }
        )
        val tabs = row(*tabBtns.toTypedArray())
        // сверху шапка-заглушка, снизу вся панель управления
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar)
            for (p in panels) addView(p, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(tabs)
        }
        setContentView(root)
        show(0)
        engine.ensureRunning() // по умолчанию лайки идут всегда, если не нажимали «Стоп»
        askBackgroundOnce()
    }

    private fun onStartClicked() {
        if (!saveSettings(false)) return
        if (!engine.startLikes()) toast("Уже работает")
        askBackgroundOnce()
    }

    // Один раз просим разрешить работу без ограничений батареи
    private fun askBackgroundOnce() {
        if (!engine.batteryOk() && !prefs.getBoolean("batAsked", false)) {
            prefs.edit().putBoolean("batAsked", true).apply()
            askBackground(false)
        }
    }

    // Автовход в окне «Браузер» работает, только пока служба лайков остановлена (иначе входит сама служба)
    private fun uiAuto(): Boolean = engine.autoFlag() && !engine.running

    private fun injectUi(view: WebView?) {
        view?.evaluateJavascript(HOOK_JS, null)
        view?.evaluateJavascript(AUTO_JS + "window.__autoOn=" + uiAuto() + ";window.__autoDiag=false;", null)
    }

    private fun setupWeb() {
        web = WebView(this)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.settings.setGeolocationEnabled(true)
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val o = origin ?: return
                if (callback == null) return
                if (!o.contains(engine.svc.hostSuffix)) {
                    callback.invoke(o, false, false) // чужие сайты геопозицию не получают
                    return
                }
                if (engine.hasLoc()) {
                    callback.invoke(o, true, true)
                } else {
                    geoCb = callback
                    geoOrigin = o
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2)
                }
            }
        }
        web.addJavascriptInterface(UiBridge(), "Rec")
        web.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                engine.logMsg("⚠ Процесс страницы упал, пересоздаю окно (служба лайков работает отдельно)")
                val nowE = SystemClock.elapsedRealtime()
                val wait = if (nowE - lastRecreate < 20000L) 15000L else 0L
                lastRecreate = nowE
                runOnUiThread { h.postDelayed({ recreate() }, wait) }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                injectUi(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectUi(view)
                CookieManager.getInstance().flush()
            }
        }
        web.loadUrl(engine.svc.site)
    }

    // Связь страницы вкладки «Браузер» с приложением: запись запросов и автонажатие
    inner class UiBridge {
        @JavascriptInterface
        fun push(s: String) {
            try {
                val o = JSONObject(s)
                // запоминаем фильтры поиска, которые шлёт сам сайт
                if (engine.svc.id == "rambler" && o.optString("u").contains("/api/a-search") && o.optString("p").contains("pol=")) {
                    prefs.edit().putString("sbody", o.optString("p")).apply()
                }
                if (engine.svc.id == "mamba" && o.optString("u").contains("/api/graphql") && o.optString("p").contains("\"operationName\":\"Rating\"")) {
                    // фильтры ленты (возраст, пол, город) берём из настроек самого сайта
                    val mv = Regex("\"variables\":(\\{[^{}]*\\})").find(o.optString("p"))
                    if (mv != null && prefs.getString("mvars", "") != mv.groupValues[1]) {
                        prefs.edit().putString("mvars", mv.groupValues[1]).apply()
                    }
                }
                runOnUiThread {
                    o.put("t", timeF.format(Date()))
                    rec.add(o)
                    if (rec.size > 300) rec.removeAt(0)
                }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun autoInfo(t: String) {
            runOnUiThread { engine.logMsg("🖱 Автовход (окно «Браузер»): кнопки «Войти»/«Продолжить» не вижу. $t") }
        }

        @JavascriptInterface
        fun autoClick(t: String, host: String) {
            runOnUiThread { engine.logMsg("🖱 Автовход (окно «Браузер»): нажал «$t» ($host)") }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = engine.hasLoc()
        val cb = geoCb
        if (cb != null) {
            cb.invoke(geoOrigin, ok, ok)
            geoCb = null
        }
        if (permissions.any { it.contains("LOCATION") }) {
            engine.logMsg(if (ok) "📍 Геопозиция разрешена" else "⚠ Геопозиция запрещена: ${engine.svc.title} без неё может не работать. Разреши её в настройках приложения")
            if (ok) {
                web.reload()
                // перезапускаем службу, чтобы она получила тип «геопозиция»
                if (engine.running) {
                    try {
                        startForegroundService(Intent(this, engine.svc.keepCls))
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    // Открывает окно другого сервиса: оно живёт в своём процессе, текущее не останавливается
    private fun switchService(id: String) {
        if (id == engine.svc.id) return
        val sv = SERVICES.firstOrNull { it.id == id } ?: return
        startActivity(Intent(this, sv.activityCls))
    }

    // ---------- обновление экрана ----------
    private fun updateTop() {
        val t = "${engine.svc.title} Liker $verName  ·  сегодня ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}\n" + engine.verdict()
        if (t != lastTop) {
            lastTop = t
            topBar.text = t
        }
    }

    private fun updateStatus() {
        if (curTab == 2) status.text = engine.statusText()
        updateTop()
    }

    // Вызывается движком при любом изменении (и раз в 3 секунды)
    private fun refresh() {
        when (curTab) {
            2 -> status.text = engine.statusText()
            3 -> renderLog()
            4 -> renderReport()
        }
        if (engine.loginRestoredCount != lastRestored) {
            lastRestored = engine.loginRestoredCount
            web.reload() // служба вошла в аккаунт, обновляем страницу в окне
        }
        updateTop()
    }

    private fun renderLog() {
        logStats.text = engine.logStatsText()
        if (logRenderedVer == engine.logVer && renderedOnlyErr == onlyErr) return
        logRenderedVer = engine.logVer
        renderedOnlyErr = onlyErr
        val l = (if (onlyErr) engine.logs.filter { it.contains("⚠") } else engine.logs).takeLast(200).reversed()
        logText.text = if (l.isEmpty()) "Журнал пуст." else l.joinToString("\n")
    }

    private fun copyLog() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("log", redact(engine.logPlain(onlyErr))))
        toast("Журнал скопирован")
    }

    private fun shareLog() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, redact(engine.logPlain(onlyErr)))
        }
        startActivity(Intent.createChooser(i, "Поделиться"))
    }

    private fun clearLog() {
        engine.clearLogs()
        renderLog()
        toast("Журнал очищен")
    }

    private fun clearSession() {
        engine.clearSession()
        web.clearCache(true)
        web.loadUrl(engine.svc.site)
        refreshAutoBtn()
        toast("Сессия очищена")
    }

    // Сессия для сервера: cookie + настройки поиска и лайка одним JSON (вставить боту после /cookies)
    private fun copySession() {
        try {
            val m = engine.collectCookies()
            if (m.isEmpty()) {
                toast("Нет cookie: сначала войди во вкладке «Браузер»")
                return
            }
            val cj = JSONObject()
            for ((k, v) in m) cj.put(k, v)
            val j = JSONObject()
            j.put("cookies", cj)
            j.put("service", engine.svc.id)
            j.put("sbody", (prefs.getString("sbody", "") ?: "").ifEmpty { engine.svc.defSbody })
            j.put("like_url", prefs.getString("likeUrl", engine.svc.defLikeUrl) ?: engine.svc.defLikeUrl)
            j.put("search_url", prefs.getString("searchUrl", engine.svc.defSearchUrl) ?: engine.svc.defSearchUrl)
            j.put("ua", web.settings.userAgentString)
            j.put("age_from_key", prefs.getString("ageFromKey", "") ?: "")
            j.put("age_to_key", prefs.getString("ageToKey", "") ?: "")
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("session", j.toString()))
            toast("Сессия скопирована. Отправь боту /cookies и вставь её")
        } catch (e: Exception) {
            toast("Не получилось: ${e.message}")
        }
    }


    private fun show(i: Int) {
        curTab = i
        for ((k, p) in panels.withIndex()) p.visibility = if (k == i) View.VISIBLE else View.GONE
        for ((k, b) in tabBtns.withIndex()) {
            b.alpha = if (k == i) 1f else 0.5f
            b.setTypeface(null, if (k == i) Typeface.BOLD else Typeface.NORMAL)
        }
        if (i == 1) renderRec()
        if (i == 2) updateStatus()
        if (i == 3) renderLog()
        if (i == 4) renderReport()
    }

    // ---------- запросы ----------
    private fun isNoise(o: JSONObject): Boolean {
        val u = o.optString("u")
        if (noiseHosts.any { u.contains(it) }) return true
        return o.optString("r").startsWith("GIF89a")
    }

    private fun filtered(): List<JSONObject> {
        val f = recFilter.text.toString().trim().lowercase()
        return rec.filter {
            (!hideNoise || !isNoise(it)) &&
                (!onlyPost || it.optString("m") == "POST") &&
                (f.isEmpty() || (it.optString("u") + it.optString("p")).lowercase().contains(f))
        }
    }

    private fun renderRec() {
        val l = filtered().takeLast(120).reversed()
        recText.text = if (l.isEmpty()) "Пусто. Открой сайт на вкладке «Браузер» и понажимай." else
            l.joinToString("\n") {
                "${it.optString("t")} ${it.optString("m")} ${it.optInt("s")} ${it.optString("u").take(110)}"
            }
    }

    private fun redact(src: String): String {
        var t = src
        t = Regex("""(\\*"(?:code|phone|wstoken|hittoken|state|nonce|apiKey|uuid|sub|email|authOperationId|hashUserLoginId|login)\\*"\s*:\s*\\*")[^"\\]*""")
            .replace(t, "\$1***")
        t = Regex("""\b(code|state|nonce|wstoken|hittoken|authOperationId|Process-ID|login)=[^&"\\]*""")
            .replace(t, "\$1=***")
        t = Regex("""\+?7\d{10}""").replace(t, "7**********")
        return t
    }

    private fun recJson(): String {
        val a = JSONArray()
        for (o in filtered()) {
            val c = JSONObject(o.toString())
            c.put("r", c.optString("r").take(700))
            a.put(c)
        }
        return redact(a.toString(1))
    }

    private fun copyRec() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("requests", recJson()))
        toast("Скопировано: ${filtered().size} запросов")
    }

    private fun shareRec() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, recJson())
        }
        startActivity(Intent.createChooser(i, "Поделиться"))
    }

    // ---------- лайки через API ----------
    private fun saveSettings(notify: Boolean = true): Boolean {
        val lim = (etLimit.text.toString().trim().toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val url = etLikeUrl.text.toString().trim().ifEmpty { engine.svc.defLikeUrl }
        val lm = etLikeMethod.text.toString().trim().uppercase().let { if (it == "GET" || it == "POST" || it == "PUT") it else engine.svc.defLikeMethod }
        val lb = etLikeBody.text.toString().trim()
        val su = etSearchUrl.text.toString().trim().ifEmpty { engine.svc.defSearchUrl }
        if (!url.contains("{login}") && !lb.contains("{login}")) {
            toast(if (engine.svc.defLikeUrl.isEmpty()) "Адреса ${engine.svc.title} ещё не настроены, см. подсказку ниже" else "В URL или теле лайка должно быть {login}")
            return false
        }
        if (su.isEmpty()) {
            toast("Не задан URL поиска")
            return false
        }
        val (pa, pb) = engine.parseRate(etRate.text.toString())
        val wf = (etWinFrom.text.toString().trim().toIntOrNull() ?: DEFAULT_WIN_FROM).coerceIn(0, 23)
        val wt = (etWinTo.text.toString().trim().toIntOrNull() ?: DEFAULT_WIN_TO).coerceIn(wf + 1, 24)
        prefs.edit().putInt("limit", lim).putFloat("rateMin", pa).putFloat("rateMax", pb)
            .putInt("winFrom", wf).putInt("winTo", wt).putString("likeUrl", url)
            .putString("likeMethod", lm).putString("likeBody", lb).putString("searchUrl", su).apply()
        etWinFrom.setText(wf.toString())
        etWinTo.setText(wt.toString())
        etLimit.setText(lim.toString())
        etRate.setText(engine.rateText())
        etLikeUrl.setText(url)
        etLikeMethod.setText(lm)
        etLikeBody.setText(lb)
        etSearchUrl.setText(su)
        if (notify) toast("Сохранено")
        return true
    }

    // Находит на открытой анкете «Я ищу от X до Y лет» и ищет эти числа в записанных ответах сайта
    private fun findAgeFields() {
        web.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { raw ->
            val text = try {
                JSONArray("[" + raw + "]").optString(0)
            } catch (e: Exception) {
                ""
            }
            val m = Regex("ищу\\s+от\\s+(\\d{1,3})\\s+до\\s+(\\d{1,3})", RegexOption.IGNORE_CASE).find(text)
            if (m == null) {
                engine.logMsg("🔍 На странице нет текста «Я ищу от … до … лет». Открой анкету во вкладке «Браузер» (после «Обновить») и нажми кнопку ещё раз")
                toast("Сначала открой анкету в «Браузере»")
            } else {
                analyseAge(m.groupValues[1], m.groupValues[2])
            }
        }
    }

    private fun analyseAge(x: String, y: String) {
        engine.logMsg("🔍 На странице: ищу от $x до $y лет. Ищу эти числа в записанных ответах сайта…")
        val rx = Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"?" + x + "\"?\\s*[,}]")
        val ry = Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"?" + y + "\"?\\s*[,}]")
        var strict = false
        var loose = false
        for (o in rec.reversed()) {
            val r = o.optString("r")
            if (r.isEmpty()) continue
            val kx = rx.findAll(r).map { it.groupValues[1] }.toList()
            val ky = ry.findAll(r).map { it.groupValues[1] }.toList()
            val fx = kx.firstOrNull { it != "age" && it.contains("age", true) }
            val fy = ky.firstOrNull { it != "age" && it.contains("age", true) }
            if (fx != null && fy != null && fx != fy) {
                prefs.edit().putString("ageFromKey", fx).putString("ageToKey", fy).apply()
                engine.logMsg("🔍 Найдено: поля «$fx» = $x и «$fy» = $y. Запрос: ${o.optString("m")} ${o.optString("u").take(120)}")
                engine.logMsg("   тело запроса: ${o.optString("p").take(200)}")
                engine.logMsg("   начало ответа: ${r.take(300)}")
                engine.logMsg("   Теперь «ищет от–до» берётся из этих полей. Если в отчёте останется «нет данных», пришли мне эти строки журнала")
                toast("Нашёл поля возраста: $fx / $fy")
                strict = true
                break
            }
            if (!loose && kx.isNotEmpty() && ky.isNotEmpty()) {
                loose = true
                engine.logMsg("🔍 Числа $x и $y есть в ответе: ${o.optString("m")} ${o.optString("u").take(120)}; поля: ${kx.first()} / ${ky.first()}")
                engine.logMsg("   начало ответа: ${r.take(300)}")
            }
        }
        if (!strict && !loose) {
            val last = rec.filter { !isNoise(it) }.takeLast(8).joinToString(" | ") { "${it.optString("m")} ${it.optString("u").take(70)}" }
            engine.logMsg("🔍 Числа $x и $y в записанных ответах не найдены. Нажми «Обновить» в «Браузере», открой анкету и повтори. Последние запросы: $last")
            toast("Не нашёл, см. журнал")
        } else if (!strict) {
            toast("Нашёл частично, см. журнал")
        }
    }

    // Батарея без ограничений -> настройки приложения (автозапуск и работа в фоне у производителя телефона)
    private fun setupBackground() {
        if (!engine.batteryOk()) {
            toast("Разреши работу без ограничений батареи")
            askBackground(false)
        } else {
            toast("Батарея разрешена. Если телефон всё равно закрывает приложение, включи «Автозапуск» и «Работа в фоне» в настройках приложения")
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
        }
    }

    // Разрешение работать в фоне без ограничений батареи; force=true — по кнопке
    private fun askBackground(force: Boolean) {
        if (engine.batteryOk()) {
            if (force) {
                toast("Ограничений батареи нет. Если телефон всё равно закрывает приложение, включи автозапуск и фоновую работу в настройках приложения")
                openAppSettings()
            }
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun refreshAutoBtn() {
        autoBtn.text = if (prefs.getBoolean("autoLogin", engine.svc.id == "rambler")) "Автовход: вкл" else "Автовход: выкл"
    }

    private fun renderReport() {
        val hourF = SimpleDateFormat("HH", Locale.US)
        hourF.timeZone = mskTz
        val tf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
        val cal = Calendar.getInstance(mskTz)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val todayList = engine.reports.filter { it.ts >= dayStart }
        val now = System.currentTimeMillis()
        val lastHour = engine.reports.count { now - it.ts < 3600000L }
        val byHour = todayList.groupBy { hourF.format(Date(it.ts)) }.toSortedMap()
            .entries.joinToString(", ") { e -> "${e.key}ч: ${e.value.size}" }
        repStats.text = "Сегодня (с 00:00 МСК) лайкнуто: ${todayList.size} (счётчик приложения ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)})" +
            "\nЗа последний час: $lastHour (темп ${engine.rateText()} в минуту)" +
            "\nЗа сессию: ${engine.sessLikes}, ошибок: ${engine.errors}" +
            "\nВсего в отчёте: ${engine.reports.size}" +
            "\nПо часам сегодня (МСК): ${if (byHour.isEmpty()) "-" else byHour}"
        val list = (if (onlyToday) todayList else engine.reports).takeLast(150).reversed()
        val t = if (list.isEmpty()) "Пока нет лайков." else list.joinToString("\n") { r ->
            tf.format(Date(r.ts)) + "  " + fmtProf(r.p) + "\n        " + (if (r.p.city.isEmpty()) "" else r.p.city + " · ") + r.login
        }
        if (repText.text.toString() != t) repText.text = t
    }

    private fun copyReport() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("report", engine.reportPlain()))
        toast("Отчёт скопирован")
    }

    private fun shareReport() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, engine.reportPlain())
        }
        startActivity(Intent.createChooser(i, "Поделиться"))
    }

    private fun clearReport() {
        AlertDialog.Builder(this)
            .setMessage("Очистить весь отчёт по лайкам?")
            .setPositiveButton("Очистить") { _, _ ->
                engine.clearReports()
                renderReport()
                toast("Отчёт очищен")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- жизненный цикл ----------
    override fun onResume() {
        super.onResume()
        engine.onChange = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        engine.onChange = null
        if (engine.sessionGood) engine.saveCookies()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        engine.onChange = null
        try {
            web.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
