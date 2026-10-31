package com.nugggets.ramblerliker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
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

const val SITE = "https://m.dating.rambler.ru"
const val DEFAULT_LIKE_URL = "/api/a-likes?likes=1&login={login}&num=1"
const val DEFAULT_SEARCH_URL = "/api/a-search?foto=1&offset={offset}&save=1&fotopref1=200x200&fotopref2=BP_FILE"
const val DEFAULT_SBODY = "pol=1&bage=60&tage=99&purp=2&geo=3159%2C4312%2C4400&online=0"
const val DEFAULT_LIMIT = 60
const val DEFAULT_RATE_MIN = 60f
const val DEFAULT_RATE_MAX = 80f
const val MAX_LIMIT = 2000
const val DEFAULT_WIN_FROM = 9
const val DEFAULT_WIN_TO = 23
const val LOG_MAX = 500
const val REP_MAX = 3000
val COOKIE_URLS = listOf(
    "https://m.dating.rambler.ru/",
    "https://dating.rambler.ru/",
    "https://auth.rambler.ru/",
    "https://id.rambler.ru/",
    "https://rambler.ru/"
)

// Прямые запросы к API сайта из страницы (с её cookie). merge=true: подмешивать заголовки, которые шлёт сам сайт (Mamba)
fun apiJs(merge: Boolean): String = "window.__merge=" + merge + ";" + """
(function(){
 var F=function(){return (window.__origFetch||window.fetch).apply(window,arguments)};
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
  return F('/api/auth-jwt/generateToken',{method:'POST',credentials:'include',headers:{'X-Requested-With':'XMLHttpRequest'}})
   .then(function(r){return r.json()}).then(function(j){ if(j&&j.token) window.__hdrs['authorization']='Bearer '+j.token; }).catch(function(){});
 }
 window.__lk={
  batch:function(url,method,body){
   ensure().then(function(){return F(url,opts(method,body))})
    .then(function(r){return r.text()}).then(function(t){Rec.apiBatch(t)}).catch(function(e){Rec.apiErr('batch',String(e))});
  },
  like:function(login,url,method,body){
   ensure().then(function(){return F(url,opts(method,body))})
    .then(function(r){var st=r.status;return r.text().then(function(t){Rec.apiLike2(login,st,t)})}).catch(function(e){Rec.apiErr('like',String(e))});
  }
 };
})();
"""

// Для скрытой страницы Mamba: запоминаем заголовки, которые шлёт сам сайт (авторизация и т.п.), чтобы слать их же
const val HDR_JS = """
(function(){
 if(window.__hdrHook) return; window.__hdrHook=1;
 window.__hdrs=window.__hdrs||{};
 var put=function(k,v){k=String(k).toLowerCase(); if(k==='content-type'||k==='content-length'||k==='accept'||k==='accept-language')return; window.__hdrs[k]=String(v);
  if(!window.__hl){window.__hl=1; try{Rec.hdr(k)}catch(e){}}};
 var cap=function(h){try{ if(!h)return;
  if(Array.isArray(h)){h.forEach(function(p){put(p[0],p[1])})}
  else if(typeof h.forEach==='function'){h.forEach(function(v,k){put(k,v)})}
  else{for(var k in h)put(k,h[k])} }catch(e){}};
 var of=window.fetch;
 window.__origFetch=window.__origFetch||of;
 if(of){ window.fetch=function(i,init){ cap((init&&init.headers)||(i&&i.headers)); return of.apply(this,arguments); }; }
 var xh=XMLHttpRequest.prototype.setRequestHeader;
 XMLHttpRequest.prototype.setRequestHeader=function(n,v){put(n,v);return xh.apply(this,arguments)};
})();
"""

const val AUTO_JS = """
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
  if(!window.__autoOn) return;
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
 window.__autoTick=tryClick;
 setInterval(tryClick,1500);
})();
"""

const val MAMBA_VARS = "{\"ageFrom\":18,\"ageTo\":80,\"gender\":\"F\",\"heightFrom\":150,\"heightTo\":220,\"location\":\"3159_4312_4400_0\",\"limit\":15}"
// Запрос ленты «Рейтинг» урезан до id фото: остальные поля сайта нам не нужны
const val MAMBA_QUERY = "query Rating(\$ageFrom: Int!, \$ageTo: Int!, \$gender: String!, \$heightFrom: Int, \$heightTo: Int, " +
    "\$offsetPhotoId: Int, \$location: String, \$coordinates: GeoPointArg, \$limit: Int!) { photoRating { queue(ageFrom: \$ageFrom, " +
    "ageTo: \$ageTo, gender: \$gender, heightFrom: \$heightFrom, heightTo: \$heightTo, location: \$location, coordinates: \$coordinates, " +
    "offsetPhotoId: \$offsetPhotoId, limit: \$limit) { photos { photo { photoId: id } } } } }"

// Профиль сервиса: адрес сайта, куки и значения по умолчанию. У каждого сервиса свой процесс, свои настройки и файлы
class Svc(
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
    val defSbody: String,
    val nidOff: Int,
    val activityCls: Class<*>,
    val keepCls: Class<*>
)

val SERVICES = listOf(
    Svc(
        "rambler", "Rambler", SITE, listOf(SITE), "rambler.ru", "liker", "",
        COOKIE_URLS, ".rambler.ru",
        DEFAULT_LIKE_URL, "GET", "", DEFAULT_SEARCH_URL, "POST", DEFAULT_SBODY,
        0, MainActivity::class.java, KeepAliveService::class.java
    ),
    // Mamba: лайк ставится на фото в «Рейтинге», лента листается по id последнего фото
    Svc(
        "mamba", "Mamba", "https://www.mamba.ru",
        listOf("https://www.mamba.ru", "https://mamba.ru", "https://m.mamba.ru"), "mamba.ru", "liker_mamba", "_mamba",
        listOf("https://www.mamba.ru/", "https://mamba.ru/", "https://m.mamba.ru/"), ".mamba.ru",
        "/api/ratings/v2/voting/photos/{login}/like?_loc%5Blocale%5D=ru", "POST", "{\"voteSource\":1}",
        "/api/graphql/?_loc[locale]=ru", "POST", "",
        10, MambaActivity::class.java, MambaKeepAliveService::class.java
    ),
    // Табор: заготовка. Адреса поиска и лайка пока пустые, их нужно снять с сайта (вкладки «Запросы» и «Журнал»)
    Svc(
        "tabor", "Tabor", "https://m.tabor.ru/sympathy/vote.php",
        listOf("https://m.tabor.ru", "https://tabor.ru", "https://www.tabor.ru"), "tabor.ru", "liker_tabor", "_tabor",
        listOf("https://m.tabor.ru/", "https://tabor.ru/", "https://www.tabor.ru/"), ".tabor.ru",
        "", "GET", "", "", "GET", "",
        20, TaborActivity::class.java, TaborKeepAliveService::class.java
    )
)

class Prof(val name: String, val age: String, val from: String, val to: String, val city: String)
class Rep(val ts: Long, val login: String, val p: Prof)

fun yearsText(a: String): String {
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
fun fmtProf(p: Prof): String {
    if (p.age.isEmpty() && p.from.isEmpty() && p.to.isEmpty() && (p.name.startsWith("фото ") || p.name.startsWith("профиль "))) return p.name
    val seek = when {
        p.from.isNotEmpty() && p.to.isNotEmpty() -> "ищет от ${p.from} до ${p.to} лет"
        p.from.isNotEmpty() -> "ищет от ${p.from} лет"
        p.to.isNotEmpty() -> "ищет до ${p.to} лет"
        else -> "ищет: нет данных"
    }
    return "${p.name.ifEmpty { "имя ?" }}, ${if (p.age.isEmpty()) "? лет" else yearsText(p.age)} — $seek"
}

/**
 * Движок лайков. Живёт в процессе приложения как одиночка и работает внутри фоновой службы
 * (KeepAliveService) со своим скрытым WebView, поэтому окно приложения для работы не нужно:
 * если система закрыла процесс, служба перезапускается и движок продолжает сам.
 * Все вызовы только из главного потока.
 */
class Engine private constructor(private val app: Context, val svc: Svc) {
    companion object {
        @Volatile
        private var inst: Engine? = null

        // В каждом процессе один движок: в основном процессе Rambler, в процессе :mamba Mamba
        fun get(ctx: Context, svcId: String): Engine {
            val e = inst
            if (e != null) return e
            return synchronized(this) {
                inst ?: Engine(ctx.applicationContext, SERVICES.firstOrNull { it.id == svcId } ?: SERVICES[0]).also { inst = it }
            }
        }
    }

    val prefs: SharedPreferences = app.getSharedPreferences(svc.prefsName, Context.MODE_PRIVATE)
    private val h = Handler(Looper.getMainLooper())
    private val rnd = Random()
    var onChange: (() -> Unit)? = null

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val mskTz: TimeZone = TimeZone.getTimeZone("Europe/Moscow")

    // журнал и отчёт
    val logs = ArrayList<String>()
    var logVer = 0
    private val logFile = File(app.filesDir, "liker${svc.suffix}.log")
    val reports = ArrayList<Rep>()
    private val repFile = File(app.filesDir, "likes_report${svc.suffix}.tsv")

    // скрытое окно со страницей сайта
    private var web: WebView? = null
    private var pageReady = false
    private var loadStartedAt = 0L

    // состояние лайкера
    var running = false
    var waiting = false
    var errors = 0
    var rowErr = 0
    var lastError = ""
    var offset = 0L
    var hasMore = true
    var likesLeft = -1
    val queue = ArrayDeque<String>()
    private val seen = HashSet<String>()
    private val profInfo = HashMap<String, Prof>()
    var sessLikes = 0
    var sessStart = 0L
    var lastLikeAt = 0L
    var hourStart = 0L
    var hourCount = 0
    var nextAt = 0L
    var limitWait = false
    var pauseUntil = 0L
    var pauseClock = ""
    var nightLogged = false
    var autoOn = false
    var authFails = 0
    var authStart = 0L
    var sessionGood = false
    var errLevel = 0
    var notReady = 0
    var emptyStreak = 0
    var resumedScan = false
    var keysLogged = false
    var likeSentAt = 0L
    var aggCount = 0
    var aggStart = 0L
    var lastNotif = ""
    var lastNotifAt = 0L
    var lastCookieSave = 0L
    var cookieSaveLogged = false
    var loginRestoredCount = 0

    private val tick = Runnable { step() }

    private val errResume = Runnable {
        if (running) {
            loadSite()
            nextAt = SystemClock.elapsedRealtime() + 8000
            h.postDelayed(tick, 8000)
        }
    }

    private val wd = Runnable {
        if (waiting && running) {
            waiting = false
            loadSite()
            handleError("нет ответа от страницы")
        }
    }

    // раз в 3 секунды: сторожевой таймер цикла, нажатие «Войти», уведомление, обновление окна
    private val maint = object : Runnable {
        override fun run() {
            try {
                if (running && !waiting && nextAt > 0L && SystemClock.elapsedRealtime() - nextAt > 300000L) {
                    logMsg("⚠ Цикл завис, перезапускаю")
                    h.removeCallbacks(tick)
                    h.post(tick)
                }
                if (running && autoFlag()) {
                    web?.evaluateJavascript("window.__autoTick&&window.__autoTick()", null)
                }
                updateNotif()
            } catch (e: Exception) {
            }
            onChange?.invoke()
            h.postDelayed(this, 3000)
        }
    }

    init {
        // у каждого сервиса свои cookie и данные сайта: отдельная папка WebView (до создания первого WebView в процессе)
        if (svc.id != "rambler" && android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                WebView.setDataDirectorySuffix(svc.id)
            } catch (ex: Exception) {
            }
        }
        dayFmt.timeZone = mskTz
        try {
            if (logFile.exists()) logs.addAll(logFile.readLines().takeLast(LOG_MAX))
        } catch (e: Exception) {
        }
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
        val file = logFile
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, err ->
            try {
                file.appendText(stamp() + "  ⚠ КРАШ приложения: $err\n")
            } catch (ex: Exception) {
            }
            old?.uncaughtException(th, err)
        }
        logMsg("⚙ Служба лайков ${svc.title} запущена (процесс приложения создан)")
        val n = restoreCookies()
        if (n > 0) logMsg("🔑 Восстановлена сохранённая сессия (cookie: $n)")
        h.postDelayed(maint, 3000)
    }

    // ---------- журнал ----------
    private fun stamp() = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())

    fun logMsg(msg: String) {
        val line = stamp() + "  " + msg
        logs.add(line)
        logVer++
        while (logs.size > LOG_MAX) logs.removeAt(0)
        try {
            if (logFile.length() > 150000) {
                logFile.writeText(logs.joinToString("\n") + "\n")
            } else {
                logFile.appendText(line + "\n")
            }
        } catch (e: Exception) {
        }
        onChange?.invoke()
    }

    fun logPlain(onlyErr: Boolean): String =
        (if (onlyErr) logs.filter { it.contains("⚠") } else logs).joinToString("\n")

    fun clearLogs() {
        logs.clear()
        logVer++
        try {
            logFile.writeText("")
        } catch (e: Exception) {
        }
        onChange?.invoke()
    }

    fun mskNow(): String {
        val f = SimpleDateFormat("HH:mm", Locale.US)
        f.timeZone = mskTz
        return f.format(Date())
    }

    private fun ago(sec: Long) = if (sec < 60) "$sec с" else "${sec / 60} мин"

    // ---------- статус ----------
    // Короткий ответ на главный вопрос: ставятся ли лайки
    fun verdict(): String {
        if (!running) return if (sessLikes > 0) "■ остановлен, лайков за сессию: $sessLikes" else "■ остановлен"
        if (limitWait && pauseUntil > SystemClock.elapsedRealtime()) return "⏸ лимит исчерпан, продолжу около $pauseClock"
        if (nightLogged) return "🌙 вне окна ${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)} МСК, сейчас ${mskNow()} МСК"
        if (autoOn) return "🔑 вход слетел, захожу сам (попытка $authFails из 4)"
        if (rowErr > 0) return "⚠ ошибки подряд: $rowErr, ${lastError.take(60)}"
        if (lastLikeAt == 0L) return "… ждём первый лайк"
        return "✅ лайки ставятся, последний ${ago((SystemClock.elapsedRealtime() - lastLikeAt) / 1000)} назад"
    }

    fun nextInfo(): String {
        if (!running) return "-"
        if (waiting) return "ждём ответ сайта"
        val left = (nextAt - SystemClock.elapsedRealtime()) / 1000
        return if (left > 3600) "через ${left / 3600} ч ${left % 3600 / 60} мин" else if (left > 120) "через ${left / 60} мин" else if (left > 0) "через $left с" else "сейчас"
    }

    fun hourInfo(): String {
        if (!running) return "-"
        return "$hourCount за этот час, темп ${rateText()} в минуту"
    }

    fun statusText(): String =
        verdict() + "\n" + (if (running) "РАБОТАЕТ" else "остановлен") +
            "\nлайков сегодня: ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}" +
            "\nосталось у сайта: ${if (likesLeft < 0) "?" else likesLeft.toString()}" +
            "\nлайков за сессию: $sessLikes" +
            "\nэтот час: ${hourInfo()}" +
            "\nв очереди анкет: ${queue.size}" +
            "\nследующее действие: ${nextInfo()}" +
            "\nошибок: $errors" +
            "\nпоследняя ошибка: ${if (lastError.isEmpty()) "-" else lastError}"

    fun logStatsText(): String {
        val mins = if (running) (SystemClock.elapsedRealtime() - sessStart) / 60000 else 0
        return verdict() + "\n" + (if (running) "работает $mins мин" else "остановлен") +
            "\nлайков за сессию: $sessLikes, сегодня: ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}" +
            "\nэтот час: ${hourInfo()}" +
            "\nв очереди: ${queue.size}, ошибок: $errors, следующее действие: ${nextInfo()}"
    }

    // ---------- настройки темпа ----------
    private fun fmtNum(f: Float): String = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    fun rateMin(): Float = prefs.getFloat("rateMin", DEFAULT_RATE_MIN)

    fun rateMax(): Float = prefs.getFloat("rateMax", DEFAULT_RATE_MAX)

    // «60-80» (или одно число, если границы равны)
    fun rateText(): String =
        if (rateMin() == rateMax()) fmtNum(rateMin()) else fmtNum(rateMin()) + "-" + fmtNum(rateMax())

    // Разбор «60-80», «60 – 80», «1,5-3» или одного числа
    fun parseRate(t: String): Pair<Float, Float> {
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

    fun batteryOk(): Boolean {
        return try {
            app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName)
        } catch (e: Exception) {
            true
        }
    }

    // ---------- скрытое окно со страницей ----------
    private fun w(): WebView {
        val x = web
        if (x != null) return x
        val n = newWeb()
        web = n
        loadSite()
        return n
    }

    private fun loadSite() {
        pageReady = false
        loadStartedAt = SystemClock.elapsedRealtime()
        w().loadUrl(svc.site)
    }

    private fun destroyWeb() {
        try {
            web?.destroy()
        } catch (e: Exception) {
        }
        web = null
        pageReady = false
    }

    private fun inject(view: WebView?) {
        if (svc.id != "rambler") view?.evaluateJavascript(HDR_JS, null)
        view?.evaluateJavascript(apiJs(svc.id != "rambler"), null)
        view?.evaluateJavascript(AUTO_JS + "window.__autoOn=" + autoFlag() + ";window.__autoDiag=" + autoOn + ";", null)
    }

    private fun newWeb(): WebView {
        val v = WebView(app)
        val s = v.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.blockNetworkImage = true
        s.setGeolocationEnabled(true)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(v, true)
        v.addJavascriptInterface(Bridge(), "Rec")
        // геопозицию отдаём только сайту сервиса и только если приложению её разрешили
        v.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val o = origin ?: return
                if (callback == null) return
                val ok = o.contains(svc.hostSuffix) && hasLoc()
                callback.invoke(o, ok, ok)
            }
        }
        v.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                logMsg("⚠ Процесс страницы упал, пересоздаю скрытое окно")
                h.post {
                    destroyWeb()
                    if (running) {
                        w()
                        nextAt = SystemClock.elapsedRealtime() + 6000
                        h.removeCallbacks(tick)
                        h.postDelayed(tick, 6000)
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageReady = false
                inject(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                inject(view)
                CookieManager.getInstance().flush()
            }
        }
        // у скрытого окна нет экрана, задаём размер, чтобы страница считала вёрстку
        v.layout(0, 0, 1080, 1920)
        v.onResume()
        v.resumeTimers()
        return v
    }

    inner class Bridge {
        @JavascriptInterface
        fun apiBatch(text: String) {
            h.post { onBatch(text) }
        }

        @JavascriptInterface
        fun apiLike(login: String, text: String) {
            h.post { onLike(login, text, 200) }
        }

        @JavascriptInterface
        fun apiLike2(login: String, status: Int, text: String) {
            h.post { onLike(login, text, status) }
        }

        @JavascriptInterface
        fun hdr(name: String) {
            h.post { logMsg("🔑 Заголовки сайта перехвачены (первый: $name), будут добавляться к запросам") }
        }

        @JavascriptInterface
        fun apiErr(kind: String, msg: String) {
            h.post { onApiErr(kind, msg) }
        }

        @JavascriptInterface
        fun autoInfo(t: String) {
            h.post { logMsg("🖱 Автовход: кнопки «Войти»/«Продолжить» не вижу. $t") }
        }

        @JavascriptInterface
        fun autoClick(t: String, host: String) {
            h.post { logMsg("🖱 Автовход: нажал «$t» ($host)") }
        }
    }

    fun hasLoc(): Boolean =
        app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun onSite(url: String?): Boolean = url != null && svc.hosts.any { url.startsWith(it) }

    // ---------- автовход ----------
    fun autoFlag(): Boolean = autoOn || prefs.getBoolean("autoLogin", svc.id == "rambler")

    fun pushAuto() {
        try {
            web?.evaluateJavascript("window.__autoOn=" + autoFlag() + ";window.__autoDiag=" + autoOn + ";", null)
        } catch (e: Exception) {
        }
    }

    private fun setAuto(on: Boolean) {
        if (on && !autoOn) authStart = SystemClock.elapsedRealtime()
        autoOn = on
        pushAuto()
    }

    // ---------- сохранение входа (cookie переживают перезапуск процесса) ----------
    fun collectCookies(): LinkedHashMap<String, String> {
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
        return m
    }

    fun saveCookies() {
        try {
            val m = collectCookies()
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

    fun restoreCookies(): Int {
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

    fun clearSession() {
        stopLikes("остановлено")
        CookieManager.getInstance().removeAllCookies(null)
        prefs.edit().remove("cookies").putBoolean("autoLogin", false).apply()
        sessionGood = false
        lastCookieSave = 0L
        cookieSaveLogged = false
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        logMsg("Сессия очищена (cookie и данные сайта удалены). Автовход выключен, чтобы не войти сразу обратно: включи его кнопкой на вкладке «Лайки»")
    }

    // ---------- данные анкеты ----------
    private fun pickStr(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k) ?: continue
            if (v == JSONObject.NULL) continue
            val t = v.toString().trim()
            if (t.isNotEmpty() && t != "0" && t != "null") return t
        }
        return ""
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
        val city = pickStr(o, "city_n", "city_name")
        return Prof(name, age, from, to, city)
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
    }

    fun clearReports() {
        reports.clear()
        try {
            repFile.writeText("")
        } catch (e: Exception) {
        }
        onChange?.invoke()
    }

    fun reportPlain(): String {
        val tf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
        return reports.joinToString("\n") { r ->
            tf.format(Date(r.ts)) + " | " + fmtProf(r.p) + " | " + r.p.city + " | " + r.login
        }
    }

    // ---------- уведомления ----------
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
            val pi = PendingIntent.getActivity(app, 0, Intent(app, svc.activityCls), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(app, "liker")
                .setContentTitle("Лайки идут")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            app.getSystemService(NotificationManager::class.java).notify(svc.nidOff + 1, n)
        } catch (e: Exception) {
        }
    }

    private fun notifyAlert(id: Int, title: String, text: String) {
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("liker_alert", "Liker: важное", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(app, 0, Intent(app, svc.activityCls), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(app, "liker_alert")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(svc.nidOff + id, n)
        } catch (e: Exception) {
        }
    }

    // ---------- запуск и остановка ----------
    // По умолчанию лайки должны идти всегда: запускаем, если не нажимали «Стоп»
    fun ensureRunning() {
        if (!running && prefs.getBoolean("wantRunning", svc.id == "rambler")) {
            logMsg("▶ Автозапуск лайков")
            startLikes()
        }
    }

    fun startLikes(): Boolean {
        if (running) return false
        running = true
        waiting = false
        errors = 0
        rowErr = 0
        lastError = ""
        hasMore = true
        queue.clear()
        seen.clear()
        for (r in reports) seen.add(r.login) // кого уже лайкнули, заново в очередь не берём
        profInfo.clear()
        emptyStreak = 0
        notReady = 0
        sessLikes = 0
        aggCount = 0
        aggStart = 0L
        authFails = 0
        lastLikeAt = 0L
        sessStart = SystemClock.elapsedRealtime()
        nextAt = 0L
        nightLogged = false
        limitWait = false
        pauseUntil = 0L
        errLevel = 0
        lastNotif = ""
        logMsg("▶ Старт. Лимит ${prefs.getInt("limit", DEFAULT_LIMIT)}/сутки, темп ${rateText()} лайков в минуту, окно ${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)} МСК (сейчас ${mskNow()} МСК), уже сегодня ${prefs.getInt("count", 0)}")
        offset = resumeOffset()
        prefs.edit().putBoolean("wantRunning", true).apply()
        try {
            app.startForegroundService(Intent(app, svc.keepCls))
        } catch (e: Exception) {
            logMsg("⚠ Не удалось запустить фоновую службу: ${e.message}")
        }
        logMsg(if (batteryOk()) "Батарея: без ограничений" else "⚠ Батарея ограничивает работу в фоне: нажми «Фоновая работа: разрешить» на вкладке «Лайки»")
        val fresh = web == null
        w()
        h.postDelayed(tick, if (fresh) 5000L else 0L)
        onChange?.invoke()
        return true
    }

    fun stopLikes(reason: String) {
        val was = running
        running = false
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
            app.getSystemService(NotificationManager::class.java).cancel(svc.nidOff + 2)
        } catch (e: Exception) {
        }
        try {
            app.stopService(Intent(app, svc.keepCls))
        } catch (e: Exception) {
        }
        destroyWeb()
        if (reason != "остановлено") lastError = reason
        if (was) {
            logMsg("■ Стоп: ${if (reason == "остановлено") "остановлено вручную" else reason}. Лайков за сессию: $sessLikes")
        }
        onChange?.invoke()
    }

    // ---------- шаг цикла ----------
    private fun step() {
        if (!running || waiting) return
        nextAt = 0L
        val today = dayFmt.format(Date())
        if (prefs.getString("day", "") != today) {
            if (!prefs.getString("day", "").isNullOrEmpty()) logMsg("Новый день, счётчик лайков сброшен")
            prefs.edit().putString("day", today).putInt("count", 0).apply()
            likesLeft = -1
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
        val hour = Calendar.getInstance(mskTz).get(Calendar.HOUR_OF_DAY)
        val wf = prefs.getInt("winFrom", DEFAULT_WIN_FROM)
        val wt = prefs.getInt("winTo", DEFAULT_WIN_TO)
        if (hour < wf || hour >= wt) {
            if (!nightLogged) {
                nightLogged = true
                logMsg("🌙 Вне окна работы $wf:00–$wt:00 МСК (сейчас ${mskNow()} МСК), жду. Окно можно поменять на вкладке «Лайки»")
            }
            nextAt = SystemClock.elapsedRealtime() + 30000
            h.postDelayed(tick, 30000)
            return
        }
        if (nightLogged) {
            nightLogged = false
            logMsg("☀ Окно работы открылось, продолжаю")
        }
        val page = w()
        if (!pageReady) {
            if (SystemClock.elapsedRealtime() - loadStartedAt > 45000L) {
                logMsg("Страница долго грузится, загружаю заново")
                loadSite()
            }
            nextAt = SystemClock.elapsedRealtime() + 2000
            h.postDelayed(tick, 2000)
            return
        }
        if (autoOn) {
            if (SystemClock.elapsedRealtime() - authStart > 150000L) {
                authPause("Автовход не завершился за 2.5 минуты")
                return
            }
            if (!onSite(page.url)) {
                nextAt = SystemClock.elapsedRealtime() + 5000
                h.postDelayed(tick, 5000)
                return
            }
        }
        if (!onSite(page.url)) {
            logMsg("Страница не на сайте ${svc.title}, загружаю заново")
            loadSite()
            nextAt = SystemClock.elapsedRealtime() + 6000
            h.postDelayed(tick, 6000)
            return
        }
        hourOk()
        if (queue.isEmpty()) {
            fetchBatch()
        } else {
            sendLike(queue.removeFirst())
        }
    }

    // Только счётчик лайков «за этот час» для статуса (ограничения по часам нет)
    private fun hourOk() {
        val now = SystemClock.elapsedRealtime()
        if (hourStart == 0L || now - hourStart >= 3600000L) {
            hourStart = now
            hourCount = 0
        }
    }

    private fun searchBody(): String {
        if (svc.id == "mamba") return mambaBody()
        return (prefs.getString("sbody", "") ?: "").ifEmpty { DEFAULT_SBODY }
    }

    // GraphQL-запрос ленты «Рейтинг» Mamba. Переменные (возраст, пол, город) берутся из последнего запроса самого сайта
    // (их запоминает вкладка «Браузер»), offsetPhotoId = id последнего фото из прошлого ответа
    private fun mambaBody(): String {
        val saved = prefs.getString("mvars", "") ?: ""
        val src = try {
            JSONObject(saved.ifEmpty { MAMBA_VARS })
        } catch (ex: Exception) {
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

    // С какого места продолжать просмотр поиска: всё до прошлой позиции уже лайкнуто, заново его не листаем
    private fun resumeOffset(): Long {
        if (svc.id != "rambler") return 0L
        val saved = prefs.getInt("scanOffset", 0)
        val fresh = System.currentTimeMillis() - prefs.getLong("scanAt", 0L) < 6 * 3600000L
        val same = prefs.getInt("scanSig", 0) == searchBody().hashCode()
        resumedScan = false
        if (saved > 100 && fresh && same) {
            resumedScan = true
            logMsg("↪ Продолжаю просмотр поиска с позиции ${saved - 100} (прошлый раз дошёл до $saved), лайкнутых заново не листаю")
            return (saved - 100).toLong()
        }
        return 0L
    }

    private fun fetchBatch() {
        val body = searchBody()
        val surl = (prefs.getString("searchUrl", svc.defSearchUrl) ?: svc.defSearchUrl).replace("{offset}", offset.toString())
        val smeth = prefs.getString("searchMethod", svc.defSearchMethod) ?: svc.defSearchMethod
        val sbod = body.replace("{offset}", offset.toString())
        waiting = true
        h.postDelayed(wd, 20000)
        w().evaluateJavascript(
            "if(window.__lk){window.__lk.batch(${JSONObject.quote(surl)}, ${JSONObject.quote(smeth)}, ${JSONObject.quote(sbod)});}else{Rec.apiErr('notready-batch','');}", null
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
        likeSentAt = SystemClock.elapsedRealtime()
        h.postDelayed(wd, 20000)
        w().evaluateJavascript(
            "if(window.__lk){window.__lk.like(${JSONObject.quote(login)}, ${JSONObject.quote(url)}, ${JSONObject.quote(method)}, ${JSONObject.quote(body)});}else{Rec.apiErr('notready-like', ${JSONObject.quote(login)});}", null
        )
    }

    private fun onApiErr(kind: String, msg: String) {
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        if (kind.startsWith("notready")) {
            // страница ещё не готова: это не ошибка, пробуем снова через 2 с
            if (kind == "notready-like" && msg.isNotEmpty()) queue.addFirst(msg)
            notReady++
            if (notReady > 15) {
                notReady = 0
                logMsg("⚠ Страница не отвечает на запросы, перезагружаю")
                loadSite()
            }
            nextAt = SystemClock.elapsedRealtime() + 2000
            h.postDelayed(tick, 2000)
        } else {
            handleError("$kind: $msg")
        }
    }

    private fun mambaList(j: JSONObject): JSONArray? =
        j.optJSONObject("data")?.optJSONObject("photoRating")?.optJSONObject("queue")?.optJSONArray("photos")

    private fun mambaPhotoId(o: JSONObject?): String = o?.optJSONObject("photo")?.optString("photoId") ?: ""

    private fun parseLike(text: String): JSONObject {
        if (svc.id == "rambler") return JSONObject(text)
        return try {
            JSONObject(text)
        } catch (ex: Exception) {
            JSONObject()
        }
    }

    // Итог лайка: 0 успех, 24 вход слетел, остальное ошибка. Rambler: по полю errno, как раньше
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

    private fun onBatch(text: String) {
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        notReady = 0
        try {
            val mamba = svc.id == "mamba"
            val j = JSONObject(text)
            val arr = if (mamba) mambaList(j) else (j.optJSONArray("data") ?: j.optJSONArray("user"))
            if (arr == null) {
                handleError("поиск: нет списка анкет: ${text.take(if (mamba) 400 else 100)}")
                return
            }
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lg = if (mamba) mambaPhotoId(o) else o.optString("login")
                if (lg.isEmpty()) continue
                if (!keysLogged) {
                    keysLogged = true
                    val keys = ArrayList<String>()
                    val it = o.keys()
                    while (it.hasNext()) keys.add(it.next())
                    logMsg("🔎 Поля анкеты: ${keys.joinToString(", ")}")
                }
                val already = !mamba && o.optInt("own_like", 0) != 0
                if (!already && seen.add(lg)) {
                    queue.addLast(lg)
                    profInfo[lg] = if (mamba) Prof("фото $lg", "", "", "", "") else profileInfo(o)
                    added++
                }
            }
            val startOff = offset
            if (mamba) {
                // лента листается по id последнего фото, а не по номеру
                val last = if (arr.length() > 0) mambaPhotoId(arr.optJSONObject(arr.length() - 1)).toLongOrNull() else null
                val progressed = last != null && last != offset
                if (last != null) offset = last
                hasMore = arr.length() > 0 && progressed
            } else {
                offset += arr.length()
                hasMore = j.optInt("has_more", 0) == 1
            }
            rowErr = 0
            if (added > 0) resumedScan = false
            if (added == 0) emptyStreak++ else emptyStreak = 0
            if (seen.size > 30000) seen.clear()
            // в журнал пишем не каждый пустой пролистанный блок
            if (added > 0 || emptyStreak <= 2 || emptyStreak % 5 == 0) {
                logMsg("🔎 Поиск: позиции $startOff–$offset, новых для лайка: $added, в очереди: ${queue.size}" + if (added == 0) " (все уже лайкнуты, листаю дальше)" else "")
            }
            if (!mamba) {
                prefs.edit().putInt("scanOffset", offset.toInt()).putLong("scanAt", System.currentTimeMillis())
                    .putInt("scanSig", searchBody().hashCode()).apply()
            }
            if (added == 0 && !hasMore && resumedScan) {
                // сохранённая позиция оказалась за концом списка: начинаем с начала сразу
                resumedScan = false
                offset = 0
                hasMore = true
                prefs.edit().putInt("scanOffset", 0).apply()
                logMsg("↺ Сохранённая позиция оказалась за концом списка, листаю с начала")
                scheduleNext(quick = true)
                return
            }
            if (added == 0 && !hasMore) {
                prefs.edit().putInt("scanOffset", 0).apply()
                offset = 0
                seen.clear()
                if (mamba) for (r in reports) seen.add(r.login) // у Mamba повторно ту же фото не лайкаем
                hasMore = true
                val d = 1800000L + rnd.nextInt(1800000)
                logMsg("⏸ Анкеты в поиске закончились. Начну поиск заново через ${d / 60000} мин (фильтры можно поменять на сайте)")
                nextAt = SystemClock.elapsedRealtime() + d
                h.postDelayed(tick, d)
                return
            }
            scheduleNext(quick = true)
        } catch (ex: Exception) {
            handleError("поиск не разобран: ${text.take(100)}")
        }
    }

    private fun onLike(login: String, text: String, status: Int = 200) {
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        notReady = 0
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
                    loginRestoredCount++
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
        } catch (ex: Exception) {
            handleError("ответ лайка не разобран: ${text.take(100)}")
        }
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
            loadSite()
        }
        queue.addFirst(login)
        nextAt = SystemClock.elapsedRealtime() + 25000
        h.postDelayed(tick, 25000)
        onChange?.invoke()
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
        h.postDelayed(errResume, d)
        onChange?.invoke()
    }

    // Серия ошибок (нет сети, сбой сайта): не останавливаемся, а ждём 5/10/20/30 минут и пробуем снова
    private fun errorPause(msg: String) {
        errLevel++
        val min = minOf(30, 5 shl (errLevel - 1))
        rowErr = 0
        logMsg("⏸ Много ошибок подряд ($msg). Пауза $min мин, потом продолжу сам")
        nextAt = SystemClock.elapsedRealtime() + min * 60000L
        h.removeCallbacks(tick)
        h.postDelayed(errResume, min * 60000L)
        onChange?.invoke()
    }

    // Лимит исчерпан: не останавливаемся, а засыпаем на случайные 1–4 часа
    private fun limitPause(reason: String, again: Boolean = true) {
        limitWait = again
        val d = 3600000L + rnd.nextInt(3 * 3600000 + 1)
        pauseUntil = SystemClock.elapsedRealtime() + d
        pauseClock = SimpleDateFormat("HH:mm", Locale.US).format(Date(System.currentTimeMillis() + d))
        logMsg("⏸ $reason. Продолжу через ${d / 3600000} ч ${d % 3600000 / 60000} мин (около $pauseClock)")
        nextAt = SystemClock.elapsedRealtime() + d
        h.removeCallbacks(tick)
        h.postDelayed(tick, d)
        onChange?.invoke()
    }

    private fun scheduleNext(extraSec: Int = 0, quick: Boolean = false) {
        var d: Long
        if (quick) {
            d = if (emptyStreak > 0) 300L + rnd.nextInt(500) else 500L + rnd.nextInt(900)
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
        h.postDelayed(tick, d)
    }
}
