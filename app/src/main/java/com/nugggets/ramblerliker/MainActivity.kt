package com.nugggets.ramblerliker

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone

private const val SITE = "https://m.dating.rambler.ru"
private const val LIMIT = 60

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

// Ждёт тап по странице и сообщает CSS-селектор нажатого элемента
private const val PICK_JS = """
(function(kind){
 function part(e){
   if(e.id) return '#'+CSS.escape(e.id);
   var cls=[].slice.call(e.classList).filter(function(k){return k.length<40&&!/\d{3,}/.test(k)}).slice(0,3);
   return e.tagName.toLowerCase()+cls.map(function(k){return '.'+CSS.escape(k)}).join('');
 }
 function h(ev){
   ev.preventDefault(); ev.stopPropagation(); ev.stopImmediatePropagation();
   document.removeEventListener('click',h,true);
   var el=ev.target.closest('button,a,[role=button],[onclick],[class*=like],[class*=heart]')||ev.target;
   var sel=part(el), n=0;
   try{n=document.querySelectorAll(sel).length}catch(e){}
   Rec.picked(kind,sel,n,(el.innerText||'').trim().slice(0,30));
 }
 document.addEventListener('click',h,true);
})('KIND');
"""

private fun clickJs(sel: String): String = """
(function(sel){
 try{
  var els=document.querySelectorAll(sel);
  for(var i=0;i<els.length;i++){
    var r=els[i].getBoundingClientRect();
    if(r.width>0&&r.height>0){
      ['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(t){
        els[i].dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window}));
      });
      return 'ok';
    }
  }
  return 'none';
 }catch(e){return 'err:'+e.message}
})(${JSONObject.quote(sel)});
"""

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var panels: List<View>
    private lateinit var recText: TextView
    private lateinit var recFilter: EditText
    private lateinit var status: TextView
    private lateinit var etLike: EditText
    private lateinit var etNext: EditText
    private lateinit var pickInfo: TextView

    private val rec = ArrayList<JSONObject>()
    private val h = Handler(Looper.getMainLooper())
    private val rnd = Random()

    private var hideNoise = true
    private var onlyPost = false
    private val noiseHosts = listOf(
        "mc.yandex.ru", "kraken.rambler.ru", "atlas.rambler.ru", "clickstream.sbrf.ru",
        "logger.id.sber.ru", "top100", "/locales/", "id.sber.ru/cms", "googletagmanager",
        "doubleclick", ".png", ".jpg", ".svg", ".woff"
    )

    private var running = false
    private var errors = 0
    private var rowErr = 0
    private var lastError = ""
    private var sinceBreak = 0
    private var breakAt = 10

    private val tick = Runnable { step() }

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
        pickInfo = TextView(this).apply { setTextColor(Color.LTGRAY) }
        val browser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                row(
                    btn("◀") { if (web.canGoBack()) web.goBack() },
                    btn("⟳") { web.reload() },
                    btn("Домой") { web.loadUrl(SITE) },
                    btn("❤ выбрать") { startPick("like") },
                    btn("▶ выбрать") { startPick("next") }
                )
            )
            addView(pickInfo)
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        // --- вкладка «Запросы» ---
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
        recFilter = EditText(this).apply {
            hint = "фильтр: like, vote, heart..."
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        recText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
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
        etLike = EditText(this).apply {
            hint = "селектор ❤"
            setSingleLine()
            setText(prefs.getString("like", ""))
        }
        etNext = EditText(this).apply {
            hint = "селектор ▶ (необязательно)"
            setSingleLine()
            setText(prefs.getString("next", ""))
        }
        val likes = ScrollView(this).apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    addView(status)
                    addView(row(btn("▶ Старт") { startLikes() }, btn("■ Стоп") { stopLikes("остановлено") }))
                    addView(TextView(this@MainActivity).apply { text = "\nСелекторы:" })
                    addView(etLike)
                    addView(etNext)
                    addView(btn("Сохранить селекторы") {
                        prefs.edit()
                            .putString("like", etLike.text.toString().trim())
                            .putString("next", etNext.text.toString().trim())
                            .apply()
                        toast("Сохранено")
                    })
                    addView(btn("Выйти из аккаунта (очистить сессию)") { clearSession() })
                    addView(TextView(this@MainActivity).apply {
                        textSize = 12f
                        text = "\nЛимит $LIMIT в сутки, паузы 8–25 сек, перерыв 2–6 мин каждые 8–15 лайков, " +
                            "работа с 9:00 до 23:00 по Москве. Держи приложение открытым, экран включён. " +
                            "Если появилась капча или предупреждение — жми «Стоп»."
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
                view?.evaluateJavascript(HOOK_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HOOK_JS, null)
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
                runOnUiThread {
                    rec.add(o)
                    if (rec.size > 300) rec.removeAt(0)
                }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun picked(kind: String, sel: String, n: Int, text: String) {
            runOnUiThread {
                prefs.edit().putString(kind, sel).apply()
                if (kind == "like") etLike.setText(sel) else etNext.setText(sel)
                pickInfo.text = "$kind = $sel (совпадений: $n) $text"
                toast("Селектор сохранён")
            }
        }
    }

    private fun show(i: Int) {
        for ((k, p) in panels.withIndex()) p.visibility = if (k == i) View.VISIBLE else View.GONE
        if (i == 1) renderRec()
        if (i == 2) updateStatus()
    }

    private fun startPick(kind: String) {
        toast("Тапни на " + (if (kind == "like") "сердечко" else "кнопку «дальше»"))
        web.evaluateJavascript(PICK_JS.replace("KIND", kind), null)
    }

    private fun clearSession() {
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

    // ---------- лайки ----------
    private fun startLikes() {
        if (running) return
        prefs.edit()
            .putString("like", etLike.text.toString().trim())
            .putString("next", etNext.text.toString().trim())
            .apply()
        if ((prefs.getString("like", "") ?: "").isEmpty()) {
            toast("Сначала выбери сердечко на вкладке «Браузер»")
            return
        }
        running = true
        errors = 0
        rowErr = 0
        lastError = ""
        startForegroundService(Intent(this, KeepAliveService::class.java))
        h.post(tick)
        updateStatus()
    }

    private fun stopLikes(reason: String) {
        running = false
        h.removeCallbacks(tick)
        stopService(Intent(this, KeepAliveService::class.java))
        if (reason != "остановлено") lastError = reason
        updateStatus()
    }

    private fun step() {
        if (!running) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString("day", "") != today) {
            prefs.edit().putString("day", today).putInt("count", 0).apply()
        }
        if (prefs.getInt("count", 0) >= LIMIT) {
            stopLikes("дневной лимит достигнут")
            return
        }
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 23) {
            updateStatus()
            h.postDelayed(tick, 30000)
            return
        }
        val sel = prefs.getString("like", "") ?: ""
        if (sel.isEmpty()) {
            stopLikes("не выбран селектор ❤")
            return
        }
        web.evaluateJavascript(clickJs(sel)) { r ->
            if (!running) return@evaluateJavascript
            if (r != null && r.contains("ok")) {
                prefs.edit().putInt("count", prefs.getInt("count", 0) + 1).apply()
                rowErr = 0
                val nx = prefs.getString("next", "") ?: ""
                if (nx.isNotEmpty()) {
                    h.postDelayed({
                        if (running) web.evaluateJavascript(clickJs(nx), null)
                    }, 1200L + rnd.nextInt(1500))
                }
            } else {
                errors++
                rowErr++
                lastError = "сердечко не найдено ($r)"
                if (rowErr >= 5) {
                    stopLikes("5 ошибок подряд: $lastError")
                    return@evaluateJavascript
                }
                web.reload()
            }
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        var d = 8000L + rnd.nextInt(17000)
        sinceBreak++
        if (sinceBreak >= breakAt) {
            d = 120000L + rnd.nextInt(240000)
            sinceBreak = 0
            breakAt = 8 + rnd.nextInt(8)
        }
        h.postDelayed(tick, d)
        updateStatus()
    }

    private fun updateStatus() {
        status.text = (if (running) "РАБОТАЕТ" else "остановлен") +
            "\nлайков сегодня: ${prefs.getInt("count", 0)}/$LIMIT" +
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
