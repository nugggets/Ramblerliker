package com.nugggets.ramblerliker

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone

private const val SITE = "https://m.dating.rambler.ru"
private const val DEFAULT_LIKE_URL = "/api/a-likes?likes=1&login={login}&num=1"
private const val DEFAULT_SBODY = "pol=1&bage=60&tage=99&purp=2&geo=3159%2C4312%2C4400&online=0"
private const val DEFAULT_LIMIT = 60
private const val MAX_LIMIT = 300

// Записывает все fetch/XHR запросы страницы и ответы на них
private const val HOOK_JS = """
(function(){
 if(window.__rec) return; window.__rec=1;
 var send=function(o){try{Rec.push(JSON.stringify(o))}catch(e){}};
 var of=window.fetch;
 if(of){ window.fetch=function(i,init){
   var url=(typeof i==='string')?i:((i&&i.url)||'');
   var m=(init&&init.method)||(i&&i.method)||'GET';
   var b=(init&&init.body)||'';
   return of.apply(this,arguments).then(function(r){
     try{ r.clone().text().then(function(t){ send({m:m,u:url,s:r.status,p:String(b).slice(0,1000),r:t.slice(0,700)}); }); }catch(e){}
     return r; });
 }; }
 var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
 XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;return xo.apply(this,arguments)};
 XMLHttpRequest.prototype.send=function(b){
   var x=this;
   this.addEventListener('loadend',function(){
     var t=''; try{t=x.responseText||''}catch(e){}
     send({m:x.__m,u:x.__u,s:x.status,p:String(b||'').slice(0,1000),r:t.slice(0,700)});
   });
   return xs.apply(this,arguments)};
})();
"""

// Прямые запросы к API сайта из контекста страницы (с её cookie и заголовками)
private const val API_JS = """
(function(){
 var H={'X-Requested-With':'XMLHttpRequest'};
 window.__lk={
  batch:function(offset,body){
   var h2={'X-Requested-With':'XMLHttpRequest','Content-Type':'application/x-www-form-urlencoded; charset=UTF-8'};
   fetch('/api/a-search?foto=1&offset='+offset+'&save=1&fotopref1=200x200&fotopref2=BP_FILE',{method:'POST',credentials:'include',headers:h2,body:body})
    .then(function(r){return r.text()}).then(function(t){Rec.apiBatch(t)}).catch(function(e){Rec.apiErr('batch',String(e))});
  },
  like:function(login,url){
   fetch(url,{credentials:'include',headers:H})
    .then(function(r){return r.text()}).then(function(t){Rec.apiLike(login,t)}).catch(function(e){Rec.apiErr('like',String(e))});
  }
 };
})();
"""

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var panels: List<View>
    private lateinit var recText: TextView
    private lateinit var recFilter: EditText
    private lateinit var status: TextView
    private lateinit var etLimit: EditText
    private lateinit var etLikeUrl: EditText

    private val rec = ArrayList<JSONObject>()
    private val h = Handler(Looper.getMainLooper())
    private val rnd = Random()

    private var hideNoise = true
    private var onlyPost = false
    private val noiseHosts = listOf(
        "mc.yandex.ru", "kraken.rambler.ru", "cnt.rambler.ru", "atlas.rambler.ru", "clickstream.sbrf.ru",
        "logger.id.sber.ru", "top100", "/locales/", "id.sber.ru/cms", "googletagmanager",
        "doubleclick", ".png", ".jpg", ".svg", ".woff", "/a-page/popup"
    )

    // состояние лайкера
    private var running = false
    private var waiting = false
    private var errors = 0
    private var rowErr = 0
    private var lastError = ""
    private var sinceBreak = 0
    private var breakAt = 10
    private var offset = 0
    private var hasMore = true
    private var likesLeft = -1
    private val queue = ArrayDeque<String>()
    private val seen = HashSet<String>()

    private val tick = Runnable { step() }
    private val wd = Runnable {
        if (waiting && running) {
            waiting = false
            web.reload()
            handleError("нет ответа от страницы")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 13f
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
        prefs = getSharedPreferences("liker", Context.MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setupWeb()

        // --- вкладка «Браузер» ---
        val browser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                row(
                    btn("◀ Назад") { if (web.canGoBack()) web.goBack() },
                    btn("⟳ Обновить") { web.reload() },
                    btn("Домой") { web.loadUrl(SITE) }
                )
            )
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        // --- вкладка «Запросы» ---
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
            setPadding(dp(8), dp(8), dp(8), dp(8))
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
            addView(ScrollView(this@MainActivity).apply { addView(recText) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        // --- вкладка «Лайки» ---
        status = TextView(this).apply { textSize = 15f }
        etLimit = EditText(this).apply {
            hint = "лимит лайков в сутки"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("limit", DEFAULT_LIMIT).toString())
        }
        etLikeUrl = EditText(this).apply {
            hint = "URL лайка"
            setSingleLine()
            setText(prefs.getString("likeUrl", DEFAULT_LIKE_URL))
        }
        val likes = ScrollView(this).apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    addView(status)
                    addView(row(btn("▶ Старт") { startLikes() }, btn("■ Стоп") { stopLikes("остановлено") }))
                    addView(TextView(this@MainActivity).apply { text = "\nЛимит в сутки (макс. $MAX_LIMIT):" })
                    addView(etLimit)
                    addView(TextView(this@MainActivity).apply { text = "URL лайка ({login} подставляется сам):" })
                    addView(etLikeUrl)
                    addView(btn("Сохранить настройки") { saveSettings() })
                    addView(btn("Сбросить URL по умолчанию") { etLikeUrl.setText(DEFAULT_LIKE_URL) })
                    addView(btn("Выйти из аккаунта (очистить сессию)") { clearSession() })
                    addView(TextView(this@MainActivity).apply {
                        textSize = 12f
                        text = "\nПриложение берёт анкеты из поиска сайта (с твоими фильтрами) и шлёт лайки " +
                            "напрямую. Паузы 8–25 сек, перерыв 2–6 мин каждые 8–15 лайков, работа с 9:00 до " +
                            "23:00 по Москве. Держи приложение открытым, экран включён, ты залогинен на " +
                            "вкладке «Браузер». Первый тест: лимит 3. При капче или предупреждении — «Стоп»."
                    })
                }
            )
        }

        panels = listOf(browser, recPanel, likes)
        val tabs = row(
            btn("Браузер") { show(0) },
            btn("Запросы") { show(1) },
            btn("Лайки") { show(2) }
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabs)
            for (p in panels) addView(p, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        show(0)
        updateStatus()
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
        web.addJavascriptInterface(Bridge(), "Rec")
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.evaluateJavascript(HOOK_JS + API_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HOOK_JS + API_JS, null)
                CookieManager.getInstance().flush()
            }
        }
        web.loadUrl(SITE)
    }

    inner class Bridge {
        @JavascriptInterface
        fun push(s: String) {
            try {
                val o = JSONObject(s)
                o.put("t", SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
                // запоминаем фильтры поиска, которые шлёт сам сайт
                if (o.optString("u").contains("/api/a-search") && o.optString("p").contains("pol=")) {
                    prefs.edit().putString("sbody", o.optString("p")).apply()
                }
                runOnUiThread {
                    rec.add(o)
                    if (rec.size > 300) rec.removeAt(0)
                }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun apiBatch(text: String) {
            runOnUiThread { onBatch(text) }
        }

        @JavascriptInterface
        fun apiLike(login: String, text: String) {
            runOnUiThread { onLike(text) }
        }

        @JavascriptInterface
        fun apiErr(kind: String, msg: String) {
            runOnUiThread {
                h.removeCallbacks(wd)
                waiting = false
                if (running) handleError("$kind: $msg")
            }
        }
    }

    private fun show(i: Int) {
        for ((k, p) in panels.withIndex()) p.visibility = if (k == i) View.VISIBLE else View.GONE
        if (i == 1) renderRec()
        if (i == 2) updateStatus()
    }

    private fun clearSession() {
        stopLikes("остановлено")
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        web.clearCache(true)
        web.loadUrl(SITE)
        toast("Сессия очищена")
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
        for (o in filtered()) a.put(o)
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
    private fun saveSettings(): Boolean {
        val lim = (etLimit.text.toString().trim().toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val url = etLikeUrl.text.toString().trim().ifEmpty { DEFAULT_LIKE_URL }
        if (!url.contains("{login}")) {
            toast("В URL лайка должно быть {login}")
            return false
        }
        prefs.edit().putInt("limit", lim).putString("likeUrl", url).apply()
        etLimit.setText(lim.toString())
        etLikeUrl.setText(url)
        toast("Сохранено")
        return true
    }

    private fun startLikes() {
        if (running) return
        if (!saveSettings()) return
        running = true
        waiting = false
        errors = 0
        rowErr = 0
        lastError = ""
        offset = 0
        hasMore = true
        queue.clear()
        seen.clear()
        startForegroundService(Intent(this, KeepAliveService::class.java))
        h.post(tick)
        updateStatus()
    }

    private fun stopLikes(reason: String) {
        running = false
        waiting = false
        h.removeCallbacks(tick)
        h.removeCallbacks(wd)
        stopService(Intent(this, KeepAliveService::class.java))
        if (reason != "остановлено") lastError = reason
        updateStatus()
    }

    private fun step() {
        if (!running || waiting) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString("day", "") != today) {
            prefs.edit().putString("day", today).putInt("count", 0).apply()
        }
        val limit = prefs.getInt("limit", DEFAULT_LIMIT)
        if (prefs.getInt("count", 0) >= limit) {
            stopLikes("дневной лимит достигнут")
            return
        }
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 23) {
            updateStatus()
            h.postDelayed(tick, 30000)
            return
        }
        if (web.url?.startsWith(SITE) != true) {
            web.loadUrl(SITE)
            h.postDelayed(tick, 6000)
            return
        }
        if (queue.isEmpty()) {
            fetchBatch()
        } else {
            sendLike(queue.removeFirst())
        }
        updateStatus()
    }

    private fun fetchBatch() {
        val body = (prefs.getString("sbody", "") ?: "").ifEmpty { DEFAULT_SBODY }
        waiting = true
        h.postDelayed(wd, 20000)
        web.evaluateJavascript("window.__lk && window.__lk.batch($offset, ${JSONObject.quote(body)});", null)
    }

    private fun sendLike(login: String) {
        val tmpl = prefs.getString("likeUrl", DEFAULT_LIKE_URL) ?: DEFAULT_LIKE_URL
        val url = tmpl.replace("{login}", URLEncoder.encode(login, "UTF-8"))
        waiting = true
        h.postDelayed(wd, 20000)
        web.evaluateJavascript(
            "window.__lk && window.__lk.like(${JSONObject.quote(login)}, ${JSONObject.quote(url)});", null
        )
    }

    private fun onBatch(text: String) {
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        try {
            val j = JSONObject(text)
            val arr = j.optJSONArray("data") ?: j.optJSONArray("user")
            if (arr == null) {
                handleError("поиск: нет списка анкет: ${text.take(100)}")
                return
            }
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lg = o.optString("login")
                if (lg.isEmpty()) continue
                if (o.optInt("own_like", 0) == 0 && seen.add(lg)) {
                    queue.addLast(lg)
                    added++
                }
            }
            offset += arr.length()
            hasMore = j.optInt("has_more", 0) == 1
            rowErr = 0
            if (added == 0 && !hasMore) {
                stopLikes("анкеты закончились — поменяй фильтры поиска на сайте")
                return
            }
            scheduleNext(quick = true)
        } catch (e: Exception) {
            handleError("поиск не разобран: ${text.take(100)}")
        }
    }

    private fun onLike(text: String) {
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        try {
            val j = JSONObject(text)
            if (j.optInt("errno", -1) == 0) {
                prefs.edit().putInt("count", prefs.getInt("count", 0) + 1).apply()
                rowErr = 0
                likesLeft = j.optInt("likesleft", -1)
                if (j.has("likesleft") && likesLeft <= 0) {
                    stopLikes("лайки на сегодня закончились (по данным сайта)")
                    return
                }
                scheduleNext(extraSec = j.optInt("likestimeout", 0))
            } else {
                handleError("errno=${j.optInt("errno", -1)}: ${text.take(100)}")
            }
        } catch (e: Exception) {
            handleError("ответ лайка не разобран: ${text.take(100)}")
        }
    }

    private fun handleError(msg: String) {
        errors++
        rowErr++
        lastError = msg
        if (rowErr >= 3) {
            stopLikes("3 ошибки подряд: $msg")
            return
        }
        scheduleNext()
    }

    private fun scheduleNext(extraSec: Int = 0, quick: Boolean = false) {
        var d = if (quick) 1500L + rnd.nextInt(2000) else 8000L + rnd.nextInt(17000)
        if (!quick) {
            sinceBreak++
            if (sinceBreak >= breakAt) {
                d = 120000L + rnd.nextInt(240000)
                sinceBreak = 0
                breakAt = 8 + rnd.nextInt(8)
            }
            if (extraSec > 0) d = maxOf(d, minOf(extraSec, 900) * 1000L)
        }
        h.postDelayed(tick, d)
        updateStatus()
    }

    private fun updateStatus() {
        status.text = (if (running) "РАБОТАЕТ" else "остановлен") +
            "\nлайков сегодня: ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}" +
            "\nосталось у сайта: ${if (likesLeft < 0) "?" else likesLeft.toString()}" +
            "\nв очереди анкет: ${queue.size}" +
            "\nошибок: $errors" +
            "\nпоследняя ошибка: ${if (lastError.isEmpty()) "-" else lastError}"
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
