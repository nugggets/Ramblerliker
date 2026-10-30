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
import android.webkit.JavascriptInterface
import android.webkit.GeolocationPermissions
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

private const val SITE = "https://m.dating.rambler.ru"
private const val DEFAULT_LIKE_URL = "/api/a-likes?likes=1&login={login}&num=1"
private const val DEFAULT_SEARCH_URL = "/api/a-search?foto=1&offset={offset}&save=1&fotopref1=200x200&fotopref2=BP_FILE"
private const val DEFAULT_SBODY = "pol=1&bage=60&tage=99&purp=2&geo=3159%2C4312%2C4400&online=0"
private const val DEFAULT_LIMIT = 60
private const val DEFAULT_RATE_MIN = 60f
private const val DEFAULT_RATE_MAX = 80f
private const val MAX_LIMIT = 2000
private const val DEFAULT_WIN_FROM = 9
private const val DEFAULT_WIN_TO = 23
private const val LOG_MAX = 500
private var crashHookSet = false
private var cookiesRestoredFor = ""
private var liveInstance: MainActivity? = null
private const val REP_MAX = 3000
private const val LIKED_MAX = 20000
private val COOKIE_URLS = listOf(
    "https://m.dating.rambler.ru/",
    "https://dating.rambler.ru/",
    "https://auth.rambler.ru/",
    "https://id.rambler.ru/",
    "https://rambler.ru/"
)

private const val MAMBA_VARS = "{\"ageFrom\":18,\"ageTo\":80,\"gender\":\"F\",\"heightFrom\":150,\"heightTo\":220,\"location\":\"3159_4312_4400_0\",\"limit\":15}"
private const val MAMBA_QUERY = "query Rating(\$ageFrom: Int!, \$ageTo: Int!, \$gender: String!, \$heightFrom: Int, \$heightTo: Int, " +
    "\$offsetPhotoId: Int, \$location: String, \$coordinates: GeoPointArg, \$limit: Int!) { photoRating { queue(ageFrom: \$ageFrom, " +
    "ageTo: \$ageTo, gender: \$gender, heightFrom: \$heightFrom, heightTo: \$heightTo, location: \$location, coordinates: \$coordinates, " +
    "offsetPhotoId: \$offsetPhotoId, limit: \$limit) { photos { photo { photoId } } } } }"

// Профиль сервиса: адрес сайта, куки и значения по умолчанию. У каждого сервиса свои настройки и свои файлы.
private class Svc(
    val id: String,
    val title: String,
    val site: String,
    val hosts: List<String>,
    val hostSuffix: String,
    val prefsName: String,
    val suffix: String,
    val cookieUrls: List<String>,
    val cookieDomain: String,
    val defLikeUrl: String,
    val defLikeMethod: String,
    val defLikeBody: String,
    val defSearchUrl: String,
    val defSearchMethod: String,
    val defSbody: String
)

private val SERVICES = listOf(
    Svc(
        "rambler", "Rambler", SITE, listOf(SITE), "rambler.ru", "liker", "",
        COOKIE_URLS, ".rambler.ru",
        DEFAULT_LIKE_URL, "GET", "", DEFAULT_SEARCH_URL, "POST", DEFAULT_SBODY
    ),
    // Адреса поиска и лайка Mamba пустые: их снимает кнопка «Определить запросы с сайта»
    Svc(
        "mamba", "Mamba", "https://www.mamba.ru",
        listOf("https://www.mamba.ru", "https://mamba.ru", "https://m.mamba.ru"), "mamba.ru", "liker_mamba", "_mamba",
        listOf("https://www.mamba.ru/", "https://mamba.ru/", "https://m.mamba.ru/"), ".mamba.ru",
        // лайк фото в «Рейтинге»; поиск: GraphQL-запрос Rating (тело собирает mambaBody())
        "/api/ratings/v2/voting/photos/{login}/like?_loc%5Blocale%5D=ru", "POST", "{\"voteSource\":1}",
        "/api/graphql/?_loc[locale]=ru", "POST", ""
    )
)

private class Prof(val name: String, val age: String, val from: String, val to: String, val city: String)
private class Rep(val ts: Long, val login: String, val p: Prof)

// Записывает все fetch/XHR запросы страницы и ответы на них
private const val HOOK_JS = """
(function(){
 if(window.__rec) return; window.__rec=1;
 var send=function(o){try{Rec.push(JSON.stringify(o))}catch(e){}};
 window.__hdrs=window.__hdrs||{};
 var put=function(k,v){k=String(k).toLowerCase(); if(k==='content-type'||k==='content-length'||k==='accept'||k==='accept-language')return; window.__hdrs[k]=String(v);
  if(!window.__hl){window.__hl=1; try{Rec.hdr(k)}catch(e){}}};
 var cap=function(h){try{ if(!h)return;
  if(Array.isArray(h)){h.forEach(function(p){put(p[0],p[1])})}
  else if(typeof h.forEach==='function'){h.forEach(function(v,k){put(k,v)})}
  else{for(var k in h)put(k,h[k])} }catch(e){}};
 var of=window.fetch;
 if(of){ window.fetch=function(i,init){
   cap((init&&init.headers)||(i&&i.headers));
   var url=(typeof i==='string')?i:((i&&i.url)||'');
   var m=(init&&init.method)||(i&&i.method)||'GET';
   var b=(init&&init.body)||'';
   return of.apply(this,arguments).then(function(r){
     try{
       var ct=(r.headers&&r.headers.get('content-type'))||'';
       if(/image|font|video|audio|octet|pdf/i.test(ct)){ send({m:m,u:url,s:r.status,p:'',r:''}); return r; }
       r.clone().text().then(function(t){ send({m:m,u:url,s:r.status,p:String(b).slice(0,1000),r:t.slice(0,4000)}); });
     }catch(e){}
     return r; });
 }; }
 var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
 XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;return xo.apply(this,arguments)};
 var xh=XMLHttpRequest.prototype.setRequestHeader;
 XMLHttpRequest.prototype.setRequestHeader=function(n,v){put(n,v);return xh.apply(this,arguments)};
 XMLHttpRequest.prototype.send=function(b){
   var x=this;
   this.addEventListener('loadend',function(){
     var t=''; try{t=x.responseText||''}catch(e){}
     send({m:x.__m,u:x.__u,s:x.status,p:String(b||'').slice(0,1000),r:t.slice(0,4000)});
   });
   return xs.apply(this,arguments)};
})();
"""

// Прямые запросы к API сайта из контекста страницы (с её cookie и заголовками)
private fun apiJs(merge: Boolean): String = "window.__merge=" + merge + ";" + """
(function(){
 function opts(method,body){
  var o={method:method||'GET',credentials:'include',headers:{'X-Requested-With':'XMLHttpRequest'}};
  if(o.method!=='GET'&&o.method!=='HEAD'&&body){
   var b=String(body).replace(/^\s+/,'');
   o.headers['Content-Type']=(b.charAt(0)==='{'||b.charAt(0)==='[')?'application/json; charset=UTF-8':'application/x-www-form-urlencoded; charset=UTF-8';
   o.body=body;
  }
  if(window.__merge&&window.__hdrs){ for(var k in window.__hdrs){ if(!(k in o.headers)) o.headers[k]=window.__hdrs[k]; } }
  return o;
 }
 function ensure(){
  var a=window.__merge&&window.__hdrs&&window.__hdrs['authorization'];
  if(!a||a.indexOf('Bearer eyJ')!==0) return Promise.resolve();
  try{ var p=JSON.parse(atob(a.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'))); if(p.exp*1000-Date.now()>120000) return Promise.resolve(); }catch(e){ return Promise.resolve(); }
  return fetch('/api/auth-jwt/generateToken',{method:'POST',credentials:'include',headers:{'X-Requested-With':'XMLHttpRequest'}})
   .then(function(r){return r.json()}).then(function(j){ if(j&&j.token) window.__hdrs['authorization']='Bearer '+j.token; }).catch(function(){});
 }
 window.__lk={
  batch:function(url,method,body){
   ensure().then(function(){return fetch(url,opts(method,body))})
    .then(function(r){return r.text()}).then(function(t){Rec.apiBatch(t)}).catch(function(e){Rec.apiErr('batch',String(e))});
  },
  like:function(login,url,method,body){
   ensure().then(function(){return fetch(url,opts(method,body))})
    .then(function(r){var st=r.status;return r.text().then(function(t){Rec.apiLike2(login,st,t)})}).catch(function(e){Rec.apiErr('like',String(e))});
  }
 };
})();
"""

// Автовход: нажимает «Войти» и «Продолжить» на страницах rambler.ru / sber.ru, когда включён window.__autoOn
private const val AUTO_JS = """
(function(){
 if(window.__auto) return; window.__auto=1;
 var clicks=0, last=0, lastInfo=0, infos=0;
 var SEL='button,a,[role=button],input[type=submit],input[type=button],[class*=button],[class*=Button],[class*=btn],[class*=Btn]';
 var BAD=/(через|с помощью|vk|google|apple|яндекс|yandex|mail|facebook|телефон|регистр|создать|без )/i;
 function vis(e){ var r=e.getBoundingClientRect(); return r.width>0 && r.height>0 && getComputedStyle(e).visibility!=='hidden'; }
 function txt(e){ return ((e.tagName==='INPUT')?(e.value||''):(e.innerText||e.textContent||'')).replace(/\s+/g,' ').trim(); }
 function ok(t){
  if(!t||t.length>40) return false;
  if(/^войти( в (аккаунт|профиль))?$/i.test(t)) return true;
  if(/^продолжить/i.test(t) && !BAD.test(t)) return true;
  return false;
 }
 function fire(e){
  try{ e.scrollIntoView({block:'center'}); }catch(x){}
  var r=e.getBoundingClientRect(), cx=r.left+r.width/2, cy=r.top+r.height/2;
  var o={bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy};
  ['pointerdown','mousedown','pointerup','mouseup'].forEach(function(n){
   try{ e.dispatchEvent(n.indexOf('pointer')===0 ? new PointerEvent(n,o) : new MouseEvent(n,o)); }catch(z){}
  });
  e.click();
 }
 function tryClick(){
  if(!window.__autoOn || document.hidden) return;
  if(!/(^|\.)(rambler\.ru|sber\.ru|sberbank\.ru|sberid\.ru|mamba\.ru)$/.test(location.hostname)) return;
  var now=Date.now();
  var els=document.querySelectorAll(SEL), seen=[], hit=null;
  for(var i=0;i<els.length;i++){
   var e=els[i], t=txt(e);
   if(!t||t.length>40||!vis(e)) continue;
   if(seen.length<12 && seen.indexOf(t)<0) seen.push(t);
   if(!hit && ok(t)) hit=[e,t];
  }
  if(hit && now-last>=2500 && clicks<8){
   last=now; clicks++;
   try{Rec.autoClick(hit[1],location.hostname)}catch(x){}
   fire(hit[0]); return;
  }
  var diag = window.__autoDiag || location.hostname.indexOf('dating.rambler.ru')<0;
  if(!hit && diag && infos<6 && now-lastInfo>8000){
   lastInfo=now; infos++;
   try{Rec.autoInfo(location.hostname+' | кнопки: '+(seen.join(' ; ')||'нет')+' | iframe: '+document.querySelectorAll('iframe').length)}catch(x){}
  }
 }
 setInterval(tryClick,1500);
})();
"""

open class MainActivity : Activity() {
    // Какой сервис ведёт это окно. Окно Mamba (MambaActivity) работает в своём процессе, поэтому оба идут одновременно
    protected open fun serviceId(): String = "rambler"
    private fun selfCls(): Class<*> = if (serviceId() == "mamba") MambaActivity::class.java else MainActivity::class.java
    private fun keepCls(): Class<*> = if (serviceId() == "mamba") MambaKeepAliveService::class.java else KeepAliveService::class.java
    private fun nid(n: Int): Int = if (serviceId() == "mamba") n + 10 else n

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
    private lateinit var etLikeMethod: EditText
    private lateinit var etLikeBody: EditText
    private lateinit var etSearchUrl: EditText
    private lateinit var svc: Svc
    private lateinit var tabBtns: List<Button>
    private lateinit var logText: TextView
    private lateinit var logStats: TextView
    private lateinit var logFile: File
    private lateinit var topBar: TextView
    private var lastLikeAt = 0L
    private val logs = ArrayList<String>()
    private val profInfo = HashMap<String, Prof>()
    private val reports = ArrayList<Rep>()
    private lateinit var repFile: File
    private lateinit var repStats: TextView
    private lateinit var repText: TextView
    private var onlyToday = true
    private var sessionGood = false
    private var authFails = 0
    private var autoOn = false
    private var errLevel = 0
    private var likeSentAt = 0L
    private var aggCount = 0
    private var aggStart = 0L
    private var lastNotifAt = 0L
    private var lastNotif = ""
    private lateinit var autoBtn: Button
    private var limitWait = false
    private var pauseUntil = 0L
    private var pauseClock = ""
    private var authStart = 0L
    private var lastCookieSave = 0L
    private var cookieSaveLogged = false
    private var keysLogged = false
    private var curTab = 0
    private var onlyErr = false
    private var sessLikes = 0
    private var sessStart = 0L
    private var nextAt = 0L
    private var nightLogged = false

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
    private var hourStart = 0L
    private var hourCount = 0
    private var hourTarget = 0
    private var offset = 0L
    private var hasMore = true
    private var likesLeft = -1
    private val queue = ArrayDeque<String>()
    private val seen = HashSet<String>()
    private val liked = LinkedHashSet<String>()   // уже лайкнутые анкеты, переживает перезапуск
    private lateinit var likedFile: File
    private var inflight = ""      // анкета, на которую сейчас идёт запрос лайка
    private var inflightKind = 0   // 0 нет, 1 поиск, 2 лайк
    private val verName: String by lazy {
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }

    private val tick = Runnable { step() }
    // Единственный способ запланировать шаг: всегда ровно один цикл, дублей не бывает
    private fun post(d: Long) {
        h.removeCallbacks(tick)
        h.removeCallbacks(errResume)
        h.postDelayed(tick, d)
    }

    private val errResume = Runnable {
        if (running) {
            web.loadUrl(svc.site)
            nextAt = SystemClock.elapsedRealtime() + 8000
            post(8000)
        }
    }
    private val uiTick = object : Runnable {
        override fun run() {
            if (curTab == 3) renderLog()
            if (curTab == 2) updateStatus()
            if (curTab == 4) renderReport()
            if (running && !waiting && nextAt > 0L && SystemClock.elapsedRealtime() - nextAt > 300000L) {
                logMsg("⚠ Цикл завис, перезапускаю")
                post(0)
            }
            updateNotif()
            updateTop()
            h.postDelayed(this, 3000)
        }
    }
    private val wd = Runnable {
        if (waiting && running) {
            waiting = false
            if (inflightKind == 2 && inflight.isNotEmpty()) queue.addFirst(inflight)
            inflightKind = 0
            web.reload()
            handleError("нет ответа от страницы")
        }
    }

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
        svc = SERVICES.firstOrNull { it.id == serviceId() } ?: SERVICES[0]
        // у каждого сервиса свои cookie и данные сайта: отдельная папка WebView (нужна до создания первого WebView в процессе)
        if (svc.id != "rambler" && Build.VERSION.SDK_INT >= 28) {
            try {
                WebView.setDataDirectorySuffix(svc.id)
            } catch (e: Exception) {
            }
        }
        prefs = getSharedPreferences(svc.prefsName, Context.MODE_PRIVATE)
        logFile = File(filesDir, "liker${svc.suffix}.log")
        try {
            if (logFile.exists()) logs.addAll(logFile.readLines().takeLast(LOG_MAX))
        } catch (e: Exception) {
        }
        likedFile = File(filesDir, "liked${svc.suffix}.txt")
        try {
            if (likedFile.exists()) {
                for (l in likedFile.readLines().takeLast(LIKED_MAX)) if (l.isNotBlank()) liked.add(l.trim())
            }
        } catch (e: Exception) {
        }
        repFile = File(filesDir, "likes_report${svc.suffix}.tsv")
        try {
            if (repFile.exists()) {
                for (line in repFile.readLines().takeLast(REP_MAX)) {
                    val f = line.split("\t")
                    if (f.size < 7) continue
                    val ts = f[0].toLongOrNull() ?: continue
                    reports.add(Rep(ts, f[1], Prof(f[2], f[3], f[4], f[5], f[6])))
                }
            }
        } catch (e: Exception) {
        }
        if (!crashHookSet) {
            crashHookSet = true
            val file = logFile
            val old = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { th, err ->
                try {
                    file.appendText(stamp() + "  ⚠ КРАШ приложения: $err\n")
                } catch (ex: Exception) {
                }
                old?.uncaughtException(th, err)
            }
        }
        logMsg("Приложение открыто")
        liveInstance?.let { if (it !== this) it.retire() }
        liveInstance = this
        try {
            val nmc = getSystemService(NotificationManager::class.java)
            nmc.cancel(nid(2))
            nmc.cancel(nid(3))
        } catch (e: Exception) {
        }
        loadSearch()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val askPerms = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) askPerms.add(Manifest.permission.POST_NOTIFICATIONS)
        // геопозиция нужна сайтам знакомств (Mamba без неё не работает)
        if (svc.id != "rambler" && !hasLoc()) {
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
                    btn("Домой") { web.loadUrl(svc.site) }
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
            setText(rateText())
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
            setText(prefs.getString("likeUrl", svc.defLikeUrl))
        }
        etLikeMethod = EditText(this).apply {
            hint = "GET или POST"
            setSingleLine()
            setText(prefs.getString("likeMethod", svc.defLikeMethod))
        }
        etLikeBody = EditText(this).apply {
            hint = "тело запроса лайка (если есть)"
            setSingleLine()
            setText(prefs.getString("likeBody", svc.defLikeBody))
        }
        etSearchUrl = EditText(this).apply {
            hint = "URL поиска"
            setSingleLine()
            setText(prefs.getString("searchUrl", svc.defSearchUrl))
        }
        val likesScroll = ScrollView(this).apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    addView(TextView(this@MainActivity).apply { text = "Сервис (открывается отдельное окно, оба сервиса могут работать одновременно):" })
                    addView(
                        row(
                            *SERVICES.map { sv ->
                                btn(sv.title) { switchService(sv.id) }.apply {
                                    alpha = if (sv.id == svc.id) 1f else 0.5f
                                    setTypeface(null, if (sv.id == svc.id) Typeface.BOLD else Typeface.NORMAL)
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
                    addView(btn("🎯 Определить запросы с сайта") { captureRequests() })
                    if (svc.id != "rambler") {
                        addView(TextView(this@MainActivity).apply {
                            textSize = 12f
                            text = "Адреса ${svc.title} уже заданы: лента «Рейтинг» и лайк фото. " +
                                "1) Вкладка «Браузер»: войди и открой «Рейтинг» (знакомства, оценка фото). " +
                                "2) Фильтры (пол, возраст, город) задай один раз на самом сайте: приложение берёт их оттуда. " +
                                "3) Поставь лимит 3 и жми «Старт». Лента листается по id последнего фото, лайк ставится на фото."
                        })
                    }
                    addView(TextView(this@MainActivity).apply {
                        textSize = 12f
                        text = "\nПриложение берёт анкеты из поиска сайта (с твоими фильтрами) и шлёт лайки " +
                            "напрямую. Ставит заданное число лайков в час: паузы " +
                            "между лайками растягиваются на весь час; когда норма часа выполнена, приложение ждёт " +
                            "до конца часа. Лайки ставятся только в окне работы по Москве. Держи приложение открытым, экран включён, ты залогинен на " +
                            "вкладке «Браузер». Первый тест: лимит 3. При капче или предупреждении — «Стоп». " +
                            "Ставятся ли лайки, видно в шапке и на вкладке «Журнал»."
                    })
                }
            )
        }
        val likes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(likesScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(row(btn("▶ Старт") { startLikes() }, btn("■ Стоп") { stopLikes("остановлено") }))
            addView(row(btn("Сохранить") { saveSettings() }, btn("URL по умолчанию") { etLikeUrl.setText(svc.defLikeUrl) }))
            addView(btn("Начать поиск анкет с начала") {
                resetSearch(false)
                logMsg("🔄 Поиск сброшен вручную: следующий запрос с первой анкеты (уже лайкнутые, ${liked.size}, пропускаются)")
                toast("Поиск сброшен")
            })
            autoBtn = btn("") {}
            autoBtn.setOnClickListener {
                val on = !prefs.getBoolean("autoLogin", svc.id == "rambler")
                prefs.edit().putBoolean("autoLogin", on).apply()
                refreshAutoBtn()
                pushAuto()
                logMsg(if (on) "Автовход включён: сам нажимаю «Войти» и «Продолжить»" else "Автовход выключен")
            }
            refreshAutoBtn()
            addView(autoBtn)
            addView(btn("Фоновая работа и автозапуск: разрешить") { setupBackground() })
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
        updateStatus()
        h.postDelayed(uiTick, 3000)
        // по умолчанию лайки всегда запускаются сами; не запускаются только после нажатия «Стоп»
        if (prefs.getBoolean("wantRunning", svc.id == "rambler")) {
            h.postDelayed({
                if (!running) {
                    logMsg("▶ Автозапуск лайков")
                    startLikes()
                }
            }, 6000)
        }
    }

    private fun setupWeb() {
        web = WebView(this)
        if (cookiesRestoredFor != svc.id) {
            cookiesRestoredFor = svc.id
            val n = restoreCookies()
            if (n > 0) logMsg("🔑 Восстановлена сохранённая сессия (cookie: $n)")
        }
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "Rec")
        s.setGeolocationEnabled(true)
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val o = origin ?: return
                if (callback == null) return
                if (!o.contains(svc.hostSuffix)) {
                    callback.invoke(o, false, false) // чужие сайты геопозицию не получают
                    return
                }
                if (hasLoc()) {
                    callback.invoke(o, true, true)
                    logMsg("📍 Геопозиция выдана сайту ${o.take(40)}")
                } else {
                    geoCb = callback
                    geoOrigin = o
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2)
                }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                logMsg("⚠ Процесс страницы упал, пересоздаю окно (работа продолжится сама)")
                runOnUiThread { recreate() }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.evaluateJavascript(HOOK_JS + apiJs(svc.id != "rambler"), null)
                view?.evaluateJavascript(AUTO_JS + "window.__autoOn=" + autoFlag() + ";window.__autoDiag=" + autoOn + ";", null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HOOK_JS + apiJs(svc.id != "rambler"), null)
                view?.evaluateJavascript(AUTO_JS + "window.__autoOn=" + autoFlag() + ";window.__autoDiag=" + autoOn + ";", null)
                CookieManager.getInstance().flush()
                logMsg("Страница загружена: ${(url ?: "").substringBefore('?').take(70)}")
            }
        }
        web.loadUrl(svc.site)
    }

    inner class Bridge {
        @JavascriptInterface
        fun push(s: String) {
            try {
                val o = JSONObject(s)
                o.put("t", SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
                // запоминаем фильтры поиска, которые шлёт сам сайт
                if (svc.id == "mamba" && o.optString("u").contains("/api/graphql") && o.optString("p").contains("\"operationName\":\"Rating\"")) {
                    // фильтры ленты (возраст, пол, город) берём из настроек самого сайта
                    val mv = Regex("\"variables\":(\\{[^{}]*\\})").find(o.optString("p"))
                    if (mv != null && prefs.getString("mvars", "") != mv.groupValues[1]) {
                        prefs.edit().putString("mvars", mv.groupValues[1]).apply()
                    }
                }
                if (svc.id == "rambler" && o.optString("u").contains("/api/a-search") && o.optString("p").contains("pol=")) {
                    val nb = o.optString("p")
                    if (prefs.getString("sbody", "") != nb) prefs.edit().putString("sbody", nb).apply()
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
            runOnUiThread { onLike(login, text, 200) }
        }

        @JavascriptInterface
        fun hdr(name: String) {
            if (svc.id != "rambler") runOnUiThread { logMsg("🔑 Заголовки сайта перехвачены (первый: $name), будут добавляться к запросам") }
        }

        @JavascriptInterface
        fun apiLike2(login: String, status: Int, text: String) {
            runOnUiThread { onLike(login, text, status) }
        }

        @JavascriptInterface
        fun autoInfo(t: String) {
            runOnUiThread { logMsg("🖱 Автовход: кнопки «Войти»/«Продолжить» не вижу. $t") }
        }

        @JavascriptInterface
        fun autoClick(t: String, host: String) {
            runOnUiThread { logMsg("🖱 Автовход: нажал «$t» ($host)") }
        }

        @JavascriptInterface
        fun apiErr(kind: String, msg: String) {
            runOnUiThread {
                if (!waiting) return@runOnUiThread
                h.removeCallbacks(wd)
                waiting = false
                if (inflightKind == 2 && inflight.isNotEmpty()) queue.addFirst(inflight)
                inflightKind = 0
                if (running) handleError("$kind: $msg")
            }
        }
    }

    private var geoCb: GeolocationPermissions.Callback? = null
    private var geoOrigin = ""

    private fun hasLoc(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = hasLoc()
        val cb = geoCb
        if (cb != null) {
            cb.invoke(geoOrigin, ok, ok)
            geoCb = null
        }
        if (permissions.any { it.contains("LOCATION") }) {
            logMsg(if (ok) "📍 Геопозиция разрешена" else "⚠ Геопозиция запрещена: ${svc.title} без неё не работает. Разреши её в настройках приложения")
            if (ok) {
                web.reload()
                // перезапускаем службу, чтобы она получила тип «геопозиция»
                if (running) startForegroundService(Intent(this, keepCls()))
            }
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

    // ---------- журнал ----------
    private fun stamp() = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())

    private fun mask(s: String) = if (s.length <= 3) s else s.take(3) + "…"

    private fun logMsg(msg: String) {
        val line = stamp() + "  " + msg
        logs.add(line)
        while (logs.size > LOG_MAX) logs.removeAt(0)
        try {
            if (logFile.length() > 150000) {
                logFile.writeText(logs.joinToString("\n") + "\n")
            } else {
                logFile.appendText(line + "\n")
            }
        } catch (e: Exception) {
        }
        if (curTab == 3) renderLog()
        updateTop()
    }

    private fun mskNow(): String {
        val f = SimpleDateFormat("HH:mm", Locale.US)
        f.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        return f.format(Date())
    }

    private fun ago(sec: Long) = if (sec < 60) "$sec с" else "${sec / 60} мин"

    // Короткий ответ на главный вопрос: ставятся ли лайки
    private fun verdict(): String {
        if (!running) return if (sessLikes > 0) "■ остановлен, лайков за сессию: $sessLikes" else "■ остановлен"
        if (limitWait && pauseUntil > SystemClock.elapsedRealtime()) return "⏸ лимит исчерпан, продолжу около $pauseClock"
        if (nightLogged) return "🌙 вне окна ${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)} МСК, сейчас ${mskNow()} МСК"
        if (autoOn) return "🔑 вход слетел, захожу сам (попытка $authFails из 4)"
        if (rowErr > 0) return "⚠ ошибки подряд: $rowErr, ${lastError.take(60)}"
        if (lastLikeAt == 0L) return "… ждём первый лайк"
        return "✅ лайки ставятся, последний ${ago((SystemClock.elapsedRealtime() - lastLikeAt) / 1000)} назад"
    }

    private fun updateTop() {
        if (!::topBar.isInitialized) return
        val t = "${svc.title} Liker $verName  ·  сегодня ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}\n" + verdict()
        if (topBar.text.toString() != t) topBar.text = t
    }

    private fun nextInfo(): String {
        if (!running) return "-"
        if (waiting) return "ждём ответ сайта"
        val left = (nextAt - SystemClock.elapsedRealtime()) / 1000
        return if (left > 3600) "через ${left / 3600} ч ${left % 3600 / 60} мин" else if (left > 120) "через ${left / 60} мин" else if (left > 0) "через $left с" else "сейчас"
    }

    private fun logPlain(): String =
        (if (onlyErr) logs.filter { it.contains("⚠") } else logs).joinToString("\n")

    private fun renderLog() {
        val mins = if (running) (SystemClock.elapsedRealtime() - sessStart) / 60000 else 0
        logStats.text = verdict() + "\n" + (if (running) "работает $mins мин" else "остановлен") +
            "\nлайков за сессию: $sessLikes, сегодня: ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}" +
            "\nэтот час: ${hourInfo()}" +
            "\nв очереди: ${queue.size}, ошибок: $errors, следующее действие: ${nextInfo()}"
        val l = (if (onlyErr) logs.filter { it.contains("⚠") } else logs).takeLast(200).reversed()
        val t = if (l.isEmpty()) "Журнал пуст." else l.joinToString("\n")
        // не трогаем текст, если он не изменился, чтобы не сбивать выделение
        if (logText.text.toString() != t) logText.text = t
    }

    private fun copyLog() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("log", redact(logPlain())))
        toast("Журнал скопирован")
    }

    private fun shareLog() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, redact(logPlain()))
        }
        startActivity(Intent.createChooser(i, "Поделиться"))
    }

    private fun clearLog() {
        logs.clear()
        try {
            logFile.writeText("")
        } catch (e: Exception) {
        }
        renderLog()
        toast("Журнал очищен")
    }

    private fun clearSession() {
        stopLikes("остановлено")
        resetSearch(true)
        CookieManager.getInstance().removeAllCookies(null)
        prefs.edit().remove("cookies").apply()
        sessionGood = false
        lastCookieSave = 0L
        cookieSaveLogged = false
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        web.clearCache(true)
        prefs.edit().putBoolean("autoLogin", false).apply()
        refreshAutoBtn()
        pushAuto()
        web.loadUrl(svc.site)
        logMsg("Сессия очищена (cookie и данные сайта удалены). Автовход выключен, чтобы не войти сразу обратно: включи его кнопкой на вкладке «Лайки»")
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

    // ---------- сервисы ----------
    private fun onSite(): Boolean {
        val u = web.url ?: return false
        return svc.hosts.any { u.startsWith(it) }
    }

    // Открывает окно другого сервиса: оно живёт в своём процессе, текущее не останавливается
    private fun switchService(id: String) {
        if (id == svc.id) return
        val cls = if (id == "mamba") MambaActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, cls))
    }

    private val listKeys = listOf("data", "user", "users", "items", "result", "profiles", "list", "contacts", "anketas")

    // Список анкет в ответе поиска: на верхнем уровне или на один уровень глубже
    private fun findList(j: JSONObject): JSONArray? {
        for (k in listKeys) {
            val v = j.opt(k)
            if (v is JSONArray) return v
            if (v is JSONObject) {
                for (k2 in listKeys) {
                    val w = v.opt(k2)
                    if (w is JSONArray) return w
                }
            }
        }
        return null
    }

    // Если анкета лежит во вложенном объекте («user», «profile»), берём его
    private fun unwrap(o: JSONObject): JSONObject {
        if (svc.id == "rambler" || idOf(o).isNotEmpty()) return o
        for (nk in listOf("user", "profile", "anketa")) {
            val n = o.optJSONObject(nk) ?: continue
            if (idOf(n).isNotEmpty()) return n
        }
        return o
    }

    private fun mambaList(j: JSONObject): JSONArray? =
        j.optJSONObject("data")?.optJSONObject("photoRating")?.optJSONObject("queue")?.optJSONArray("photos")

    private fun idOf(o: JSONObject): String {
        if (svc.id == "rambler") return o.optString("login")
        if (svc.id == "mamba") return o.optJSONObject("photo")?.optString("photoId") ?: ""
        val k = prefs.getString("idKey", "") ?: ""
        if (k.isNotEmpty()) {
            val v = pickStr(o, k)
            if (v.isNotEmpty()) return v
        }
        return pickStr(o, "login", "id", "user_id", "uid", "oid", "userId", "anketa_id")
    }

    private fun alreadyLiked(o: JSONObject): Boolean {
        if (svc.id == "rambler") return o.optInt("own_like", 0) != 0
        for (k in listOf("own_like", "liked", "is_liked", "isLiked")) {
            val v = o.opt(k) ?: continue
            if (v == true || v == 1 || v == "1" || v == "true") return true
        }
        return false
    }

    private fun parseLike(text: String): JSONObject {
        if (svc.id == "rambler") return JSONObject(text)
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    // Итог лайка: 0 успех, 24 вход слетел, остальное ошибка. Rambler: как раньше, по полю errno
    private fun likeCode(j: JSONObject, text: String, status: Int): Int {
        if (svc.id == "rambler") return j.optInt("errno", -1)
        if (status == 401 || status == 403) return 24
        if (status !in 200..299) return status
        if (text.trimStart().startsWith("<")) return -1 // вместо ответа пришла HTML-страница (вход, капча)
        if (j.has("errno")) return j.optInt("errno", -1)
        if (!j.optBoolean("success", true) || !j.optBoolean("ok", true)) return -1
        val st = j.optString("status", "").lowercase()
        if (st == "error" || st == "fail" || st == "failed") return -1
        val er = j.opt("error")
        if (er != null && er != JSONObject.NULL) {
            val t = er.toString()
            if (t.isNotEmpty() && t != "0" && t != "false" && t != "[]" && t != "{}") return -1
        }
        return 0
    }

    // Адрес запроса относительно сайта; null, если запрос ушёл на чужой домен
    private fun relUrl(u: String): String? {
        if (u.isEmpty() || u.startsWith("//")) return null
        if (!u.startsWith("http")) return u
        val p = Uri.parse(u)
        val host = p.host ?: return null
        if (!host.endsWith(svc.hostSuffix)) return null
        val q = p.encodedQuery
        return (p.encodedPath ?: "/") + (if (q != null) "?$q" else "")
    }

    // Находит в записанных запросах поиск анкет и лайк и подставляет их в настройки.
    // Сначала в «Браузере» открой ленту анкет и поставь один лайк вручную.
    private fun captureRequests() {
        val sample = rec.filter { !isNoise(it) }
        if (sample.isEmpty()) {
            toast("Нет записанных запросов: открой сайт во вкладке «Браузер»")
            return
        }
        val idRx = Regex("\"(id|login|user_id|uid|oid|userId|anketa_id)\"\\s*:\\s*\"?([A-Za-z0-9_\\-]{3,})\"?")
        var listReq: JSONObject? = null
        var idKey = ""
        val ids = HashSet<String>()
        for (o in sample.reversed()) {
            val r = o.optString("r")
            if (!(r.startsWith("{") || r.startsWith("["))) continue
            val ms = idRx.findAll(r).toList()
            if (ms.size >= 3) {
                listReq = o
                idKey = ms[0].groupValues[1]
                for (m in ms) ids.add(m.groupValues[2])
                break
            }
        }
        val likeRx = Regex("(?<![A-Za-z])(like|vote|encounter|rating|sympath)", RegexOption.IGNORE_CASE)
        var likeReq: JSONObject? = null
        for (o in sample.reversed()) {
            if (o === listReq) continue
            if (o.optInt("s") !in 200..299) continue
            if (o.optString("r").length > 600) continue
            if (likeRx.containsMatchIn(o.optString("u") + " " + o.optString("p"))) {
                likeReq = o
                break
            }
        }
        val e = prefs.edit()
        var did = false

        if (listReq == null) {
            logMsg("🎯 Поиск анкет не нашёл: нужен ответ со списком анкет. Открой ленту/поиск в «Браузере», пролистай и повтори")
        } else {
            val su0 = relUrl(listReq.optString("u"))
            if (su0 == null) {
                logMsg("🎯 Поиск идёт на другой домен (${listReq.optString("u").take(80)}): из страницы сайта он не вызовется, пришли мне эту строку")
            } else {
                var su: String = su0
                var sb = listReq.optString("p")
                val sm = listReq.optString("m").uppercase().ifEmpty { "GET" }
                val offRx = Regex("(?<![A-Za-z])(offset|start|skip)=\\d+")
                val offJs = Regex("\"(offset|start|skip)\"\\s*:\\s*\\d+")
                var paged = false
                if (offRx.containsMatchIn(su)) {
                    su = offRx.replace(su) { it.groupValues[1] + "={offset}" }
                    paged = true
                } else if (offRx.containsMatchIn(sb)) {
                    sb = offRx.replace(sb) { it.groupValues[1] + "={offset}" }
                    paged = true
                } else if (offJs.containsMatchIn(sb)) {
                    sb = offJs.replace(sb) { "\"" + it.groupValues[1] + "\":{offset}" }
                    paged = true
                }
                e.putString("searchUrl", su).putString("searchMethod", sm).putString("sbody", sb).putString("idKey", idKey)
                did = true
                logMsg("🎯 Поиск: $sm ${su.take(120)}")
                if (sb.isNotEmpty()) logMsg("   тело: ${sb.take(200)}")
                logMsg("   поле id анкеты: «$idKey»" + if (paged) "" else ". ⚠ Параметр смещения (offset/start/skip) не найден: если лента листается иначе, впиши {offset} в URL поиска сам")
            }
        }

        if (likeReq == null) {
            logMsg("🎯 Лайк не нашёл: поставь один лайк вручную в «Браузере» и нажми кнопку ещё раз")
        } else {
            val lu0 = relUrl(likeReq.optString("u"))
            if (lu0 == null) {
                logMsg("🎯 Лайк идёт на другой домен (${likeReq.optString("u").take(80)}): из страницы сайта он не вызовется")
            } else {
                var lu: String = lu0
                var lb = likeReq.optString("p")
                val lm = likeReq.optString("m").uppercase().ifEmpty { "GET" }
                var replaced = false
                for (id in ids.filter { it.length >= 4 }.sortedByDescending { it.length }) {
                    if (lu.contains(id)) {
                        lu = lu.replaceFirst(id, "{login}")
                        replaced = true
                        break
                    }
                    if (lb.contains(id)) {
                        lb = lb.replaceFirst(id, "{login}")
                        replaced = true
                        break
                    }
                }
                if (!replaced) {
                    val d = Regex("\\d{5,}")
                    val m1 = d.find(lu)
                    val m2 = d.find(lb)
                    if (m1 != null) {
                        lu = lu.replaceFirst(m1.value, "{login}")
                        replaced = true
                    } else if (m2 != null) {
                        lb = lb.replaceFirst(m2.value, "{login}")
                        replaced = true
                    }
                }
                if (!replaced) {
                    logMsg("🎯 Лайк найден ($lm ${lu.take(100)}), но id анкеты в нём не нашёл: впиши {login} в URL или тело сам")
                } else {
                    e.putString("likeUrl", lu).putString("likeMethod", lm).putString("likeBody", lb)
                    did = true
                    logMsg("🎯 Лайк: $lm ${lu.take(120)}")
                    if (lb.isNotEmpty()) logMsg("   тело: ${lb.take(200)}")
                    logMsg("   id анкеты заменён на {login}. Проверь запись и сделай тест с лимитом 3")
                }
            }
        }

        if (did) {
            e.apply()
            etLikeUrl.setText(prefs.getString("likeUrl", svc.defLikeUrl))
            etLikeMethod.setText(prefs.getString("likeMethod", svc.defLikeMethod))
            etLikeBody.setText(prefs.getString("likeBody", svc.defLikeBody))
            etSearchUrl.setText(prefs.getString("searchUrl", svc.defSearchUrl))
            toast("Запросы определены, смотри журнал")
        } else {
            toast("Ничего не нашёл, смотри журнал")
        }
    }

    // ---------- лайки через API ----------
    private fun saveSettings(silent: Boolean = false): Boolean {
        val lim = (etLimit.text.toString().trim().toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val url = etLikeUrl.text.toString().trim().ifEmpty { svc.defLikeUrl }
        val lm = etLikeMethod.text.toString().trim().uppercase().let { if (it == "GET" || it == "POST" || it == "PUT") it else svc.defLikeMethod }
        val lb = etLikeBody.text.toString().trim()
        val su = etSearchUrl.text.toString().trim().ifEmpty { svc.defSearchUrl }
        if (!url.contains("{login}") && !lb.contains("{login}")) {
            toast("В URL или теле лайка должно быть {login}. Для ${svc.title} нажми «Определить запросы с сайта»")
            return false
        }
        if (su.isEmpty()) {
            toast("Не задан URL поиска. Нажми «Определить запросы с сайта»")
            return false
        }
        val (pa, pb) = parseRate(etRate.text.toString())
        val wf = (etWinFrom.text.toString().trim().toIntOrNull() ?: DEFAULT_WIN_FROM).coerceIn(0, 23)
        val wt = (etWinTo.text.toString().trim().toIntOrNull() ?: DEFAULT_WIN_TO).coerceIn(wf + 1, 24)
        prefs.edit().putInt("limit", lim).putFloat("rateMin", pa).putFloat("rateMax", pb)
            .putInt("winFrom", wf).putInt("winTo", wt).putString("likeUrl", url)
            .putString("likeMethod", lm).putString("likeBody", lb).putString("searchUrl", su).apply()
        etWinFrom.setText(wf.toString())
        etWinTo.setText(wt.toString())
        etLimit.setText(lim.toString())
        etRate.setText(rateText())
        etLikeUrl.setText(url)
        etLikeMethod.setText(lm)
        etLikeBody.setText(lb)
        etSearchUrl.setText(su)
        if (!silent) toast("Сохранено")
        return true
    }

    private fun startLikes() {
        if (running) return
        if (!saveSettings(true)) return
        running = true
        waiting = false
        errors = 0
        rowErr = 0
        lastError = ""
        hasMore = true
        keysLogged = false
        sessLikes = 0
        aggCount = 0
        aggStart = 0L
        authFails = 0
        lastLikeAt = 0L
        sessStart = SystemClock.elapsedRealtime()
        nextAt = 0L
        nightLogged = false
        logMsg("▶ Старт. Лимит ${prefs.getInt("limit", DEFAULT_LIMIT)}/сутки, темп ${rateText()} лайков в минуту, окно ${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)} МСК (сейчас ${mskNow()} МСК), уже сегодня ${prefs.getInt("count", 0)}")
        logMsg("🔎 Продолжаю поиск с анкеты №$offset, в очереди: ${queue.size}, уже лайкнуто ранее: ${liked.size}")
        startForegroundService(Intent(this, keepCls()))
        prefs.edit().putBoolean("wantRunning", true).apply()
        lastNotif = ""
        errLevel = 0
        logMsg(if (batteryOk()) "Батарея: без ограничений" else "⚠ Батарея ограничивает работу в фоне: нажми «Фоновая работа» на вкладке «Лайки»")
        logMsg(if (overlayOk()) "Автозапуск после перезагрузки: разрешён" else "⚠ Для автозапуска после перезагрузки разреши «Поверх других окон»: кнопка «Фоновая работа и автозапуск»")
        if (!batteryOk() && !prefs.getBoolean("batAsked", false)) {
            prefs.edit().putBoolean("batAsked", true).apply()
            askBackground(false)
        }
        post(0)
        updateStatus()
    }

    private fun stopLikes(reason: String) {
        val was = running
        running = false
        if (waiting && inflightKind == 2 && inflight.isNotEmpty() && !liked.contains(inflight)) queue.addFirst(inflight)
        saveSearch()
        limitWait = false
        pauseUntil = 0L
        setAuto(false)
        nextAt = 0L
        waiting = false
        h.removeCallbacks(tick)
        h.removeCallbacks(wd)
        h.removeCallbacks(errResume)
        prefs.edit().putBoolean("wantRunning", false).apply()
        try {
            getSystemService(NotificationManager::class.java).cancel(nid(2))
        } catch (e: Exception) {
        }
        stopService(Intent(this, keepCls()))
        if (reason != "остановлено") lastError = reason
        if (was) {
            logMsg("■ Стоп: ${if (reason == "остановлено") "остановлено вручную" else reason}. Лайков за сессию: $sessLikes")
        }
        updateStatus()
    }

    private fun step() {
        if (!running || waiting) return
        nextAt = 0L
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString("day", "") != today) {
            if (!prefs.getString("day", "").isNullOrEmpty()) logMsg("Новый день, счётчик лайков сброшен")
            prefs.edit().putString("day", today).putInt("count", 0).apply()
            if (limitWait) {
                limitPause("Лимит обновился, сразу не начинаю", false)
                return
            }
        }
        val limit = prefs.getInt("limit", DEFAULT_LIMIT)
        if (prefs.getInt("count", 0) >= limit) {
            limitPause("Дневной лимит $limit достигнут")
            return
        }
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).get(Calendar.HOUR_OF_DAY)
        val wf = prefs.getInt("winFrom", DEFAULT_WIN_FROM)
        val wt = prefs.getInt("winTo", DEFAULT_WIN_TO)
        if (hour < wf || hour >= wt) {
            if (!nightLogged) {
                nightLogged = true
                logMsg("🌙 Вне окна работы $wf:00–$wt:00 МСК (сейчас ${mskNow()} МСК), жду. Окно можно поменять на вкладке «Лайки»")
            }
            nextAt = SystemClock.elapsedRealtime() + 30000
            updateStatus()
            post(30000)
            return
        }
        if (nightLogged) {
            nightLogged = false
            logMsg("☀ Окно работы открылось, продолжаю")
        }
        if (autoOn) {
            if (SystemClock.elapsedRealtime() - authStart > 150000L) {
                authPause("Автовход не завершился за 2.5 минуты")
                return
            }
            if (!onSite()) {
                nextAt = SystemClock.elapsedRealtime() + 5000
                post(5000)
                updateStatus()
                return
            }
        }
        if (!onSite()) {
            logMsg("Страница не на сайте ${svc.title}, загружаю заново")
            web.loadUrl(svc.site)
            nextAt = SystemClock.elapsedRealtime() + 6000
            post(6000)
            return
        }
        hourOk()
        var next: String? = null
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (liked.contains(c)) {
                profInfo.remove(c)
            } else {
                next = c
                break
            }
        }
        if (next == null) fetchBatch() else sendLike(next)
        updateStatus()
    }

    // Только счётчик лайков «за этот час» для статуса (ограничения по часам больше нет)
    private fun hourOk(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (hourStart == 0L || now - hourStart >= 3600000L) {
            hourStart = now
            hourCount = 0
        }
        return true
    }

    private fun hourInfo(): String {
        if (!running) return "-"
        return "$hourCount за этот час, темп ${rateText()} в минуту"
    }

    private fun fetchBatch() {
        val body = curBody()
        if (prefs.getString("offBody", "") != body && offset > 0) {
            logMsg("🔎 Фильтры поиска изменились, начинаю с первой анкеты")
            offset = 0
            queue.clear()
            seen.clear()
            profInfo.clear()
        }
        waiting = true
        inflightKind = 1
        inflight = ""
        logMsg("🔎 Запрашиваю анкеты из поиска (offset=$offset)")
        h.postDelayed(wd, 20000)
        val surl = (prefs.getString("searchUrl", svc.defSearchUrl) ?: svc.defSearchUrl).replace("{offset}", offset.toString())
        val smeth = prefs.getString("searchMethod", svc.defSearchMethod) ?: svc.defSearchMethod
        val sbod = body.replace("{offset}", offset.toString())
        web.evaluateJavascript(
            "window.__lk && window.__lk.batch(${JSONObject.quote(surl)}, ${JSONObject.quote(smeth)}, ${JSONObject.quote(sbod)});", null
        )
    }

    private fun sendLike(login: String) {
        val tmpl = prefs.getString("likeUrl", svc.defLikeUrl) ?: svc.defLikeUrl
        val enc = URLEncoder.encode(login, "UTF-8")
        val url = tmpl.replace("{login}", enc)
        val method = prefs.getString("likeMethod", svc.defLikeMethod) ?: svc.defLikeMethod
        val bodyT = prefs.getString("likeBody", svc.defLikeBody) ?: ""
        val isJson = bodyT.trimStart().let { it.startsWith("{") || it.startsWith("[") }
        val body = bodyT.replace("{login}", if (isJson) login else enc)
        waiting = true
        inflight = login
        inflightKind = 2
        likeSentAt = SystemClock.elapsedRealtime()
        h.postDelayed(wd, 20000)
        web.evaluateJavascript(
            "window.__lk && window.__lk.like(${JSONObject.quote(login)}, ${JSONObject.quote(url)}, " +
                "${JSONObject.quote(method)}, ${JSONObject.quote(body)});", null
        )
    }

    private fun onBatch(text: String) {
        if (!waiting || inflightKind != 1) return // запоздалый или чужой ответ
        h.removeCallbacks(wd)
        waiting = false
        inflightKind = 0
        if (!running) return
        try {
            val j = if (svc.id != "rambler" && text.trimStart().startsWith("[")) JSONObject().put("data", JSONArray(text)) else JSONObject(text)
            val arr = if (svc.id == "rambler") (j.optJSONArray("data") ?: j.optJSONArray("user")) else if (svc.id == "mamba") mambaList(j) else findList(j)
            if (arr == null) {
                handleError("поиск: нет списка анкет: ${text.take(100)}")
                return
            }
            var added = 0
            for (i in 0 until arr.length()) {
                val o0 = arr.optJSONObject(i) ?: continue
                val o = unwrap(o0)
                val lg = idOf(o)
                if (lg.isEmpty()) continue
                if (!keysLogged) {
                    keysLogged = true
                    val keys = ArrayList<String>()
                    val it = o.keys()
                    while (it.hasNext()) keys.add(it.next())
                    logMsg("🔎 Поля анкеты: ${keys.joinToString(", ")}")
                    logMsg("   пример: ${o.toString().replace(lg, mask(lg)).take(600)}")
                }
                if (!alreadyLiked(o) && !liked.contains(lg) && seen.add(lg)) {
                    queue.addLast(lg)
                    profInfo[lg] = if (svc.id == "mamba") Prof("фото $lg", "", "", "", "") else profileInfo(o)
                    added++
                }
            }
            var progressed = true
            if (svc.id == "mamba") {
                // лента листается по id последнего фото, а не по номеру
                val last = if (arr.length() > 0) idOf(unwrap(arr.optJSONObject(arr.length() - 1) ?: JSONObject())).toLongOrNull() else null
                progressed = last != null && last != offset
                if (last != null) offset = last
            } else {
                offset += arr.length()
            }
            hasMore = when {
                svc.id == "rambler" -> j.optInt("has_more", 0) == 1
                svc.id == "mamba" -> arr.length() > 0 && progressed
                j.has("has_more") -> j.optInt("has_more", 0) == 1
                j.has("hasMore") -> j.optBoolean("hasMore", false)
                else -> arr.length() > 0
            }
            rowErr = 0
            logMsg("🔎 Получено анкет: ${arr.length()}, новых для лайка: $added, в очереди: ${queue.size}, есть ещё: ${if (hasMore) "да" else "нет"}")
            if (added == 0 && !hasMore) {
                offset = 0
                seen.clear()
                hasMore = true
                val d = 1800000L + rnd.nextInt(1800000)
                logMsg("⏸ Анкеты в поиске закончились. Начну поиск заново через ${d / 60000} мин (фильтры можно поменять на сайте)")
                nextAt = SystemClock.elapsedRealtime() + d
                saveSearch()
                post(d)
                return
            }
            saveSearch()
            scheduleNext(quick = true, empty = added == 0)
        } catch (e: Exception) {
            handleError("поиск не разобран: ${text.take(100)}")
        }
    }

    private fun curBody(): String {
        if (svc.id == "mamba") return mambaBody()
        return (prefs.getString("sbody", "") ?: "").ifEmpty { svc.defSbody }
    }

    // GraphQL-запрос ленты «Рейтинг». Запрос урезан до id фото (остальные поля сайта нам не нужны),
    // переменные (возраст, пол, город) берутся из последнего запроса самого сайта. offsetPhotoId = id последнего фото
    private fun mambaBody(): String {
        val saved = prefs.getString("mvars", "") ?: ""
        val src = try {
            JSONObject(saved.ifEmpty { MAMBA_VARS })
        } catch (e: Exception) {
            JSONObject(MAMBA_VARS)
        }
        val v = JSONObject()
        for (k in listOf("ageFrom", "ageTo", "gender", "heightFrom", "heightTo", "location", "coordinates", "limit")) {
            if (src.has(k)) v.put(k, src.get(k))
        }
        v.put("offsetPhotoId", 0)
        val vars = v.toString().replace("\"offsetPhotoId\":0", "\"offsetPhotoId\":{offset}")
        return "{\"operationName\":\"Rating\",\"variables\":" + vars + ",\"query\":" + JSONObject.quote(MAMBA_QUERY) + "}"
    }

    // Запоминаем лайкнутую анкету навсегда (файл), чтобы после стопа/перезапуска не запрашивать её снова
    private fun markLiked(login: String) {
        if (login.isEmpty() || !liked.add(login)) return
        try {
            if (liked.size > LIKED_MAX + 500) {
                val it = liked.iterator()
                var n = liked.size - LIKED_MAX
                while (n > 0 && it.hasNext()) {
                    it.next()
                    it.remove()
                    n--
                }
                likedFile.writeText(liked.joinToString("\n") + "\n")
            } else {
                likedFile.appendText(login + "\n")
            }
        } catch (e: Exception) {
        }
    }

    // Позиция в поиске и очередь анкет сохраняются: «Стоп → Старт» продолжает с того же места
    private fun saveSearch() {
        try {
            val a = JSONArray()
            for (lg in queue) {
                val p = profInfo[lg]
                a.put(
                    JSONObject().put("l", lg).put("n", p?.name ?: "").put("a", p?.age ?: "")
                        .put("f", p?.from ?: "").put("t", p?.to ?: "").put("c", p?.city ?: "")
                )
            }
            prefs.edit().putLong("offsetL", offset).putString("offBody", curBody()).putString("queue", a.toString()).apply()
        } catch (e: Exception) {
        }
    }

    private fun loadSearch() {
        try {
            if (prefs.getString("offBody", "") != curBody()) return
            offset = prefs.getLong("offsetL", prefs.getInt("offset", 0).toLong())
            val a = JSONArray(prefs.getString("queue", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val lg = o.optString("l")
                if (lg.isEmpty() || liked.contains(lg)) continue
                queue.addLast(lg)
                seen.add(lg)
                profInfo[lg] = Prof(o.optString("n"), o.optString("a"), o.optString("f"), o.optString("t"), o.optString("c"))
            }
        } catch (e: Exception) {
        }
    }

    private fun resetSearch(clearLiked: Boolean) {
        offset = 0
        hasMore = true
        queue.clear()
        seen.clear()
        profInfo.clear()
        prefs.edit().remove("offset").remove("offsetL").remove("queue").remove("offBody").apply()
        if (clearLiked) {
            liked.clear()
            try {
                likedFile.writeText("")
            } catch (e: Exception) {
            }
        }
    }

    private fun pickStr(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k) ?: continue
            if (v == JSONObject.NULL) continue
            val t = v.toString().trim()
            if (t.isNotEmpty() && t != "0" && t != "null") return t
        }
        return ""
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
                logMsg("🔍 На странице нет текста «Я ищу от … до … лет». Открой анкету во вкладке «Браузер» (после «Обновить») и нажми кнопку ещё раз")
                toast("Сначала открой анкету в «Браузере»")
            } else {
                analyseAge(m.groupValues[1], m.groupValues[2])
            }
        }
    }

    private fun analyseAge(x: String, y: String) {
        logMsg("🔍 На странице: ищу от $x до $y лет. Ищу эти числа в записанных ответах сайта…")
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
                logMsg("🔍 Найдено: поля «$fx» = $x и «$fy» = $y. Запрос: ${o.optString("m")} ${o.optString("u").take(120)}")
                logMsg("   тело запроса: ${o.optString("p").take(200)}")
                logMsg("   начало ответа: ${r.take(300)}")
                logMsg("   Теперь «ищет от–до» берётся из этих полей. Если в отчёте останется «нет данных», пришли мне эти строки журнала")
                toast("Нашёл поля возраста: $fx / $fy")
                strict = true
                break
            }
            if (!loose && kx.isNotEmpty() && ky.isNotEmpty()) {
                loose = true
                logMsg("🔍 Числа $x и $y есть в ответе: ${o.optString("m")} ${o.optString("u").take(120)}; поля: ${kx.first()} / ${ky.first()}")
                logMsg("   начало ответа: ${r.take(300)}")
            }
        }
        if (!strict && !loose) {
            val last = rec.filter { !isNoise(it) }.takeLast(8).joinToString(" | ") { "${it.optString("m")} ${it.optString("u").take(70)}" }
            logMsg("🔍 Числа $x и $y в записанных ответах не найдены. Нажми «Обновить» в «Браузере», открой анкету и повтори. Последние запросы: $last")
            toast("Не нашёл, см. журнал")
        } else if (!strict) {
            toast("Нашёл частично, см. журнал")
        }
    }

    private fun findNested(o: JSONObject, names: List<String>, depth: Int): String {
        val keysIt = o.keys()
        while (keysIt.hasNext()) {
            val k = keysIt.next()
            val v = o.opt(k)
            if (v is JSONObject) {
                if (depth < 3) {
                    val r = findNested(v, names, depth + 1)
                    if (r.isNotEmpty()) return r
                }
            } else if (v != null && v != JSONObject.NULL && v !is JSONArray && names.any { n -> k == n || k.endsWith("_$n") }) {
                val t = v.toString().trim()
                if (t.isNotEmpty() && t != "0") return t
            }
        }
        return ""
    }

    // Данные анкеты из объекта в ответе поиска: имя, возраст, возраст поиска, город
    private fun profileInfo(o: JSONObject): Prof {
        val name = pickStr(o, "name", "fname", "first_name", "nick", "nickname")
        val age = pickStr(o, "age", "years")
        val lf = prefs.getString("ageFromKey", "") ?: ""
        val lt = prefs.getString("ageToKey", "") ?: ""
        var from = if (lf.isNotEmpty()) findNested(o, listOf(lf), 0) else ""
        var to = if (lt.isNotEmpty()) findNested(o, listOf(lt), 0) else ""
        if (from.isEmpty()) from = pickStr(o, "bage", "age_from", "agefrom")
        if (to.isEmpty()) to = pickStr(o, "tage", "age_to", "ageto")
        if (from.isEmpty()) from = findNested(o, listOf("bage", "age_from"), 0)
        if (to.isEmpty()) to = findNested(o, listOf("tage", "age_to"), 0)
        val city = if (svc.id == "rambler") pickStr(o, "city_n", "city_name") else pickStr(o, "city_n", "city_name", "city")
        return Prof(name, age, from, to, city)
    }

    private fun yearsText(a: String): String {
        val n = a.toIntOrNull() ?: return "$a лет"
        val w = if (n % 100 in 11..14) {
            "лет"
        } else {
            when (n % 10) {
                1 -> "год"
                2, 3, 4 -> "года"
                else -> "лет"
            }
        }
        return "$n $w"
    }

    // «Katerina, 30 лет — ищет от 29 до 40 лет»
    private fun fmtProf(p: Prof): String {
        if (svc.id == "mamba") return p.name.ifEmpty { "профиль ?" }
        val seek = when {
            p.from.isNotEmpty() && p.to.isNotEmpty() -> "ищет от ${p.from} до ${p.to} лет"
            p.from.isNotEmpty() -> "ищет от ${p.from} лет"
            p.to.isNotEmpty() -> "ищет до ${p.to} лет"
            else -> "ищет: нет данных"
        }
        return "${p.name.ifEmpty { "имя ?" }}, ${if (p.age.isEmpty()) "? лет" else yearsText(p.age)} — $seek"
    }

    // ---------- сохранение входа (cookie переживают перезапуск приложения) ----------
    // Сессия для сервера: cookie + настройки поиска и лайка одним JSON (вставить боту после /cookies)
    private fun copySession() {
        try {
            val cm = CookieManager.getInstance()
            val m = LinkedHashMap<String, String>()
            for (u in svc.cookieUrls) {
                val c = cm.getCookie(u) ?: continue
                for (part in c.split(";")) {
                    val t = part.trim()
                    val i = t.indexOf('=')
                    if (i <= 0) continue
                    m[t.substring(0, i)] = t.substring(i + 1)
                }
            }
            if (m.isEmpty()) {
                toast("Нет cookie: сначала войди во вкладке «Браузер»")
                return
            }
            val cj = JSONObject()
            for ((k, v) in m) cj.put(k, v)
            val j = JSONObject()
            j.put("cookies", cj)
            j.put("service", svc.id)
            j.put("search_url", prefs.getString("searchUrl", svc.defSearchUrl) ?: svc.defSearchUrl)
            j.put("sbody", (prefs.getString("sbody", "") ?: "").ifEmpty { svc.defSbody })
            j.put("like_url", prefs.getString("likeUrl", svc.defLikeUrl) ?: svc.defLikeUrl)
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

    private fun saveCookies() {
        try {
            val cm = CookieManager.getInstance()
            val m = LinkedHashMap<String, String>()
            for (u in svc.cookieUrls) {
                val c = cm.getCookie(u) ?: continue
                for (part in c.split(";")) {
                    val t = part.trim()
                    val i = t.indexOf('=')
                    if (i <= 0) continue
                    m[t.substring(0, i)] = t.substring(i + 1)
                }
            }
            if (m.isEmpty()) return
            val j = JSONObject()
            for ((k, v) in m) j.put(k, v)
            prefs.edit().putString("cookies", j.toString()).apply()
            lastCookieSave = SystemClock.elapsedRealtime()
            if (!cookieSaveLogged) {
                cookieSaveLogged = true
                logMsg("🔑 Сессия сохранена (cookie: ${m.size}), после перезапуска вход восстановится сам")
            }
        } catch (e: Exception) {
        }
    }

    private fun restoreCookies(): Int {
        val raw = prefs.getString("cookies", null) ?: return 0
        var n = 0
        try {
            val j = JSONObject(raw)
            val cm = CookieManager.getInstance()
            val keysIt = j.keys()
            while (keysIt.hasNext()) {
                val k = keysIt.next()
                val v = j.optString(k)
                val attrs = when {
                    k.startsWith("__Host-") -> "; Path=/; Secure; Max-Age=2592000"
                    k.startsWith("__Secure-") -> "; Domain=${svc.cookieDomain}; Path=/; Secure; Max-Age=2592000"
                    else -> "; Domain=${svc.cookieDomain}; Path=/; Max-Age=2592000"
                }
                cm.setCookie(svc.site + "/", "$k=$v$attrs")
                n++
            }
            cm.flush()
        } catch (e: Exception) {
        }
        return n
    }

    private fun fmtNum(f: Float): String = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    private fun rateMin(): Float = prefs.getFloat("rateMin", DEFAULT_RATE_MIN)

    private fun rateMax(): Float = prefs.getFloat("rateMax", DEFAULT_RATE_MAX)

    // «60-80» (или одно число, если границы равны)
    private fun rateText(): String =
        if (rateMin() == rateMax()) fmtNum(rateMin()) else fmtNum(rateMin()) + "-" + fmtNum(rateMax())

    // Разбор «60-80», «60 – 80», «1,5-3» или одного числа
    private fun parseRate(t: String): Pair<Float, Float> {
        val nums = Regex("\\d+(?:[.,]\\d+)?").findAll(t).map { it.value.replace(',', '.').toFloat() }.toList()
        var a = (nums.getOrNull(0) ?: DEFAULT_RATE_MIN).coerceIn(0.05f, 300f)
        var b = (nums.getOrNull(1) ?: a).coerceIn(0.05f, 300f)
        if (b < a) {
            val x = a
            a = b
            b = x
        }
        return Pair(a, b)
    }

    private fun batteryOk(): Boolean {
        return try {
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) {
            true
        }
    }

    private fun overlayOk(): Boolean {
        return try {
            Settings.canDrawOverlays(this)
        } catch (e: Exception) {
            true
        }
    }

    // Пошагово: батарея -> «Поверх других окон» (нужно, чтобы приложение само открывалось из фона) -> настройки приложения
    private fun setupBackground() {
        when {
            !batteryOk() -> {
                toast("1/2: разреши работу без ограничений батареи")
                askBackground(false)
            }
            !overlayOk() -> {
                toast("2/2: разреши «Поверх других окон» — так приложение само запустится после перезагрузки")
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
            else -> {
                toast("Всё разрешено. Если телефон всё равно закрывает приложение, включи «Автозапуск» в настройках приложения")
                openAppSettings()
            }
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
        if (batteryOk()) {
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

    // Статус в постоянном уведомлении (обновляется, только когда текст изменился)
    private fun updateNotif() {
        if (!running) return
        val state = when {
            limitWait && pauseUntil > SystemClock.elapsedRealtime() -> "пауза до $pauseClock"
            nightLogged -> "вне окна работы"
            autoOn -> "захожу в аккаунт"
            else -> "работает"
        }
        val text = "$state · сегодня ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)} · за час $hourCount"
        if (text == lastNotif || SystemClock.elapsedRealtime() - lastNotifAt < 15000L) return
        lastNotif = text
        lastNotifAt = SystemClock.elapsedRealtime()
        try {
            val pi = PendingIntent.getActivity(this, 0, Intent(this, selfCls()), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(this, "liker")
                .setContentTitle("Лайки идут")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(nid(1), n)
        } catch (e: Exception) {
        }
    }

    // Старое окно (процесс жив, а Activity пересоздана): гасим его цикл, чтобы не было двух циклов лайков
    private fun retire() {
        h.removeCallbacksAndMessages(null)
        running = false
        waiting = false
        try {
            web.destroy()
        } catch (e: Exception) {
        }
    }

    private fun autoFlag(): Boolean = autoOn || prefs.getBoolean("autoLogin", svc.id == "rambler")

    private fun pushAuto() {
        try {
            web.evaluateJavascript("window.__autoOn=" + autoFlag() + ";window.__autoDiag=" + autoOn + ";", null)
        } catch (e: Exception) {
        }
    }

    private fun refreshAutoBtn() {
        autoBtn.text = if (prefs.getBoolean("autoLogin", svc.id == "rambler")) "Автовход: вкл" else "Автовход: выкл"
    }

    private fun setAuto(on: Boolean) {
        if (on && !autoOn) authStart = SystemClock.elapsedRealtime()
        autoOn = on
        pushAuto()
    }

    // errno=24 на лайке: вход слетел. Возвращаем сохранённую сессию и сами проходим вход («Войти», «Продолжить»)
    private fun onAuthLost(login: String) {
        sessionGood = false
        authFails++
        errors++
        lastError = "errno=24 (вход слетел?)"
        if (authFails > 4) {
            authPause("Не удалось войти автоматически (попыток: ${authFails - 1})")
            return
        }
        setAuto(true)
        logMsg("⚠ errno=24, похоже слетел вход. Захожу сам: нажимаю «Войти» / «Продолжить», попытка $authFails из 4")
        if (authFails == 1) {
            val n = restoreCookies()
            if (n > 0) logMsg("🔑 Вернул сохранённую сессию (cookie: $n)")
            web.loadUrl(svc.site)
        }
        queue.addFirst(login)
        nextAt = SystemClock.elapsedRealtime() + 25000
        post(25000)
        updateStatus()
    }

    // ---------- отчёт по лайкам ----------
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun repLine(r: Rep) = listOf(
        r.ts.toString(), clean(r.login), clean(r.p.name), clean(r.p.age), clean(r.p.from), clean(r.p.to), clean(r.p.city)
    ).joinToString("\t")

    private fun addReport(login: String) {
        val p = profInfo[login] ?: Prof("", "", "", "", "")
        val r = Rep(System.currentTimeMillis(), login, p)
        reports.add(r)
        while (reports.size > REP_MAX) reports.removeAt(0)
        try {
            if (repFile.length() > 300000) {
                repFile.writeText(reports.joinToString("\n") { repLine(it) } + "\n")
            } else {
                repFile.appendText(repLine(r) + "\n")
            }
        } catch (e: Exception) {
        }
        if (curTab == 4) renderReport()
    }

    private fun renderReport() {
        val hourF = SimpleDateFormat("HH", Locale.US)
        val tf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayList = reports.filter { it.ts >= dayStart }
        val now = System.currentTimeMillis()
        val lastHour = reports.count { now - it.ts < 3600000L }
        val byHour = todayList.groupBy { hourF.format(Date(it.ts)) }.toSortedMap()
            .entries.joinToString(", ") { e -> "${e.key}ч: ${e.value.size}" }
        repStats.text = "Сегодня лайкнуто: ${todayList.size} (счётчик приложения ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)})" +
            "\nЗа последний час: $lastHour (темп ${rateText()} в минуту)" +
            "\nЗа сессию: $sessLikes, ошибок: $errors" +
            "\nВсего в отчёте: ${reports.size}" +
            "\nПо часам сегодня: ${if (byHour.isEmpty()) "-" else byHour}"
        val list = (if (onlyToday) todayList else reports).takeLast(150).reversed()
        val t = if (list.isEmpty()) "Пока нет лайков." else list.joinToString("\n") { r ->
            tf.format(Date(r.ts)) + "  " + fmtProf(r.p) + "\n        " + (if (r.p.city.isEmpty()) "" else r.p.city + " · ") + r.login
        }
        if (repText.text.toString() != t) repText.text = t
    }

    private fun reportPlain(): String {
        val tf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
        return reports.joinToString("\n") { r ->
            tf.format(Date(r.ts)) + " | " + fmtProf(r.p) + " | " + r.p.city + " | " + r.login
        }
    }

    private fun copyReport() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("report", reportPlain()))
        toast("Отчёт скопирован")
    }

    private fun shareReport() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, reportPlain())
        }
        startActivity(Intent.createChooser(i, "Поделиться"))
    }

    private fun clearReport() {
        AlertDialog.Builder(this)
            .setMessage("Очистить весь отчёт по лайкам?")
            .setPositiveButton("Очистить") { _, _ ->
                reports.clear()
                try {
                    repFile.writeText("")
                } catch (e: Exception) {
                }
                renderReport()
                toast("Отчёт очищен")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun onLike(login: String, text: String, status: Int = 200) {
        val stale = !waiting || inflightKind != 2 || login != inflight
        if (stale) {
            // Запоздалый ответ (после стопа или тайм-аута): лайк мог реально пройти, учитываем, но цикл не трогаем
            try {
                if (login.isNotEmpty() && !liked.contains(login) && likeCode(parseLike(text), text, status) == 0) {
                    prefs.edit().putInt("count", prefs.getInt("count", 0) + 1).apply()
                    markLiked(login)
                    addReport(login)
                    profInfo.remove(login)
                    logMsg("↩ Запоздалый ответ: лайк всё же прошёл (${mask(login)}), учтён")
                }
            } catch (e: Exception) {
            }
            return
        }
        h.removeCallbacks(wd)
        waiting = false
        inflightKind = 0
        inflight = ""
        if (!running) return
        try {
            val j = parseLike(text)
            val code = likeCode(j, text, status)
            if (code == 0) {
                if (svc.id == "mamba") {
                    val ow = j.optString("photoOwnerId", "")
                    if (ow.isNotEmpty()) profInfo[login] = Prof("профиль $ow (фото $login)", "", "", "", "")
                }
                prefs.edit().putInt("count", prefs.getInt("count", 0) + 1).apply()
                sessLikes++
                hourCount++
                lastLikeAt = SystemClock.elapsedRealtime()
                sessionGood = true
                errLevel = 0
                limitWait = false
                authFails = 0
                if (autoOn) {
                    setAuto(false)
                    saveCookies()
                    logMsg("✅ Вход восстановлен, продолжаю")
                }
                rowErr = 0
                likesLeft = j.optInt("likesleft", -1)
                val prof = profInfo[login]?.let { fmtProf(it) } ?: "данных анкеты нет"
                val left = if (likesLeft < 0) "?" else likesLeft.toString()
                val cnt = prefs.getInt("count", 0)
                if (maxOf(rateMin(), rateMax()) <= 6f || sessLikes <= 3) {
                    logMsg("❤ Лайк $cnt/${prefs.getInt("limit", DEFAULT_LIMIT)}: $prof ($login), осталось у сайта: $left")
                } else {
                    aggCount++
                    val nowE = SystemClock.elapsedRealtime()
                    if (aggStart == 0L) aggStart = nowE
                    if (nowE - aggStart >= 60000L) {
                        logMsg("❤ За минуту лайков: $aggCount. Сегодня $cnt/${prefs.getInt("limit", DEFAULT_LIMIT)}, осталось у сайта: $left. Последний: $prof ($login)")
                        aggCount = 0
                        aggStart = nowE
                    }
                }
                if (sessLikes <= 3) logMsg("   ответ сайта: ${text.take(150)}")
                addReport(login)
                markLiked(login)
                profInfo.remove(login)
                if (lastCookieSave == 0L || SystemClock.elapsedRealtime() - lastCookieSave > 300000L) saveCookies()
                if (j.has("likesleft") && likesLeft <= 0) {
                    limitPause("Лайки у сайта закончились")
                    return
                }
                scheduleNext(extraSec = j.optInt("likestimeout", 0))
            } else if (code == 24) {
                onAuthLost(login)
            } else {
                handleError("errno=$code: ${text.take(100)}")
            }
        } catch (e: Exception) {
            handleError("ответ лайка не разобран: ${text.take(100)}")
        }
    }

    private fun handleError(msg: String) {
        if (likesLeft == 0) {
            logMsg("⚠ $msg")
            limitPause("Лайки у сайта закончились")
            return
        }
        errors++
        rowErr++
        lastError = msg
        logMsg("⚠ Ошибка (подряд $rowErr, всего $errors): $msg")
        if (rowErr >= 3) {
            errorPause(msg)
            return
        }
        scheduleNext()
    }

    // Не получилось войти: не останавливаемся, а через 30 минут пробуем снова и просим войти вручную
    private fun authPause(reason: String) {
        setAuto(false)
        authFails = 0
        val d = 1800000L
        logMsg("🔒 $reason. Повторю попытку через 30 мин (если нужен код из СМС — открой приложение и войди вручную)")
        notifyAlert(3, "Нужен вход в аккаунт", "Открой приложение и войди. Повторная попытка через 30 мин")
        nextAt = SystemClock.elapsedRealtime() + d
        h.removeCallbacks(tick)
        h.removeCallbacks(errResume)
        h.postDelayed(errResume, d)
        updateStatus()
    }

    private fun notifyAlert(id: Int, title: String, text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(android.app.NotificationChannel("liker_alert", "Liker: важное", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 0, Intent(this, selfCls()), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(this, "liker_alert")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(nid(id), n)
        } catch (e: Exception) {
        }
    }

    // Серия ошибок (нет сети, сбой сайта): не останавливаемся, а ждём 5/10/20/30 минут и пробуем снова
    private fun errorPause(msg: String) {
        errLevel++
        val min = minOf(30, 5 shl (errLevel - 1))
        rowErr = 0
        logMsg("⏸ Много ошибок подряд ($msg). Пауза $min мин, потом продолжу сам")
        nextAt = SystemClock.elapsedRealtime() + min * 60000L
        h.removeCallbacks(tick)
        h.removeCallbacks(errResume)
        h.postDelayed(errResume, min * 60000L)
        updateStatus()
    }

    // Лимит исчерпан: не останавливаемся, а засыпаем на случайные 1–4 часа
    private fun limitPause(reason: String, again: Boolean = true) {
        limitWait = again
        val d = 3600000L + rnd.nextInt(3 * 3600000 + 1)
        pauseUntil = SystemClock.elapsedRealtime() + d
        pauseClock = SimpleDateFormat("HH:mm", Locale.US).format(Date(System.currentTimeMillis() + d))
        logMsg("⏸ $reason. Продолжу через ${d / 3600000} ч ${d % 3600000 / 60000} мин (около $pauseClock)")
        nextAt = SystemClock.elapsedRealtime() + d
        post(d)
        updateStatus()
    }

    private fun scheduleNext(extraSec: Int = 0, quick: Boolean = false, empty: Boolean = false) {
        var d: Long
        if (quick) {
            // пустая страница (все уже лайкнуты) — листаем быстрее
            d = if (empty) 900L + rnd.nextInt(900) else 1500L + rnd.nextInt(2000)
        } else {
            // темп «лайков в минуту» от–до: пауза между лайками случайная (от 60000/hi до 60000/lo мс),
            // из неё вычитаем время, уже потраченное на сам запрос
            val lo = rateMin().toDouble()
            val hi = maxOf(lo, rateMax().toDouble())
            val interval = 60000.0 / hi + rnd.nextDouble() * (60000.0 / lo - 60000.0 / hi)
            val spent = if (likeSentAt > 0L) SystemClock.elapsedRealtime() - likeSentAt else 0L
            d = maxOf((interval - spent).toLong(), 50L)
            likeSentAt = 0L
            if (extraSec > 0) {
                val e = maxOf(d, minOf(extraSec, 900) * 1000L)
                if (e > d) logMsg("⏳ Сайт просит подождать ${e / 1000} с")
                d = e
            }
        }
        nextAt = SystemClock.elapsedRealtime() + d
        post(d)
        updateStatus()
    }

    private fun updateStatus() {
        if (curTab != 2) {
            updateTop()
            return
        }
        status.text = verdict() + "\n" + (if (running) "РАБОТАЕТ" else "остановлен") +
            "\nлайков сегодня: ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}" +
            "\nосталось у сайта: ${if (likesLeft < 0) "?" else likesLeft.toString()}" +
            "\nлайков за сессию: $sessLikes" +
            "\nэтот час: ${hourInfo()}" +
            "\nв очереди анкет: ${queue.size}" +
            "\nследующее действие: ${nextInfo()}" +
            "\nошибок: $errors" +
            "\nпоследняя ошибка: ${if (lastError.isEmpty()) "-" else lastError}"
        updateTop()
    }

    override fun onDestroy() {
        saveSearch()
        if (!running) h.removeCallbacks(uiTick)
        logMsg("Окно приложения закрыто" + if (running) " (лайки продолжают работать в фоне)" else "")
        super.onDestroy()
        if (liveInstance === this && !running) liveInstance = null
    }

    override fun onPause() {
        super.onPause()
        saveSearch()
        if (sessionGood) saveCookies()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
