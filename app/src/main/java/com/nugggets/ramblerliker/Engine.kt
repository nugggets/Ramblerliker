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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
const val SITE_LIMIT_MS = 24L * 3600000L // лимит лайков у сайта: ждём сутки до сброса
const val DEFAULT_WIN_FROM = 9
const val DEFAULT_WIN_TO = 23
const val LOG_MAX = 500
const val REP_MAX = 3000
// Счётчики и аналитика: в скрытом окне службы их не грузим (меньше трафика, процессора и батареи)
val BLOCK_HOSTS = listOf(
    "mc.yandex.ru", "googletagmanager.com", "google-analytics.com", "doubleclick.net",
    "kraken.rambler.ru", "cnt.rambler.ru", "top100.rambler.ru", "counter.yadro.ru"
)
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
  boot:function(){
   try{
    var ids=[],seen={};
    var add=function(v){v=String(v); if(/^\d{5,}$/.test(v)&&!seen[v]){seen[v]=1;ids.push(v);}};
    var roots=document.querySelectorAll('.sticky-miniature, .card-content, [class*="sympathy"], [class*="vote"]');
    for(var i=0;i<roots.length;i++){
     var as=roots[i].querySelectorAll('a[href^="/id"]');
     for(var j=0;j<as.length;j++){var m=/^\/id(\d+)/.exec(as[j].getAttribute('href')||''); if(m)add(m[1]);}
    }
    if(!ids.length){
     for(var i2=0;i2<roots.length;i2++){
      var re=/(?:user[_-]?id|data-user[_-]?id|data-id)["'\s:=]+(\d{5,})/g, mm, html=roots[i2].outerHTML;
      while((mm=re.exec(html)))add(mm[1]);
     }
    }
    var stubEl=document.querySelector('.vote-error-stub');
    var stub=stubEl?((stubEl.innerText||stubEl.textContent||'').replace(/\s+/g,' ').trim().slice(0,300)||'(без текста)'):'';
    var diag='';
    if(!ids.length&&!stub){
     diag=(document.title||'')+' | '+location.href+' | блоков: '+roots.length+' | текст: '+((document.body&&document.body.innerText)||'').replace(/\s+/g,' ').trim().slice(0,300);
    }
    Rec.apiBatch(JSON.stringify({tabor:true,ids:ids,diag:diag,stub:stub}));
   }catch(e){Rec.apiErr('boot',String(e));}
  },
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

const val OK_API = "https://okdating-app.ru/api/"

// OK: скрипт для КАЖДОГО окна страницы (мини-приложение живёт во вложенном окне другого домена).
// Он подсматривает, с какими общими параметрами (_token, _session, _device_info ...) приложение
// ходит на okdating-app.ru, и выполняет наши запросы от имени этого же окна: с тем же токеном и тем же адресом страницы
const val OK_JS = """
(function(){
 if(window.__okj) return; window.__okj=1;
 if(typeof Rec==='undefined'||!Rec.okPoll) return;
 var H=''; try{H=String(location.hostname)}catch(e){}
 var API='okdating-app.ru/api/';
 var st={common:null,last:{},ready:false};
 var OF=window.fetch;
 var rawFetch=function(){return OF.apply(window,arguments)};
 function pairs(body,cb){
  try{
   if(!body) return false;
   if(typeof body==='string'){ body=new URLSearchParams(body); }
   if((typeof FormData!=='undefined'&&body instanceof FormData)||(typeof URLSearchParams!=='undefined'&&body instanceof URLSearchParams)){ body.forEach(function(v,k){cb(k,v)}); return true; }
  }catch(e){}
  return false;
 }
 function grab(url,body){
  try{
   url=String(url); var i=url.indexOf(API); if(i<0) return;
   var m=url.substring(i+API.length).split('?')[0];
   var com={}, own={}, any=false;
   pairs(body,function(k,v){ if(typeof v!=='string') return; any=true; if(k.charAt(0)==='_') com[k]=v; else own[k]=v; });
   if(!any||!com._token) return;
   st.common=com;
   if(m) st.last[m]=own;
   if(!st.ready){ st.ready=true; setTimeout(function(){ try{Rec.okReady(H)}catch(e){} },3000); }
  }catch(e){}
 }
 if(OF){ window.fetch=function(i,init){ try{ grab((typeof i==='string')?i:((i&&i.url)||''),(init&&init.body)||null); }catch(e){} return OF.apply(this,arguments); }; }
 try{
  var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(m,u){ this.__ou=u; return xo.apply(this,arguments); };
  XMLHttpRequest.prototype.send=function(b){ try{ grab(this.__ou,b); }catch(e){} return xs.apply(this,arguments); };
 }catch(e){}
 var busy=false;
 function run(c){
  busy=true;
  try{
   var fd=new FormData(), p=c.params||{}, com=st.common||{};
   var u=String(c.url), i=u.indexOf(API), m=i<0?'':u.substring(i+API.length).split('?')[0];
   var base=st.last[m]||{};
   for(var k in base){ if(!(k in p)&&k!=='user_id'&&k!=='meta') fd.append(k,base[k]); }
   for(var k2 in p) fd.append(k2,p[k2]);
   for(var k3 in com) fd.append(k3,com[k3]);
   rawFetch(u,{method:'POST',body:fd}).then(function(r){
    var s=r.status;
    return r.text().then(function(t){ Rec.okResult(c.id,s,t); });
   }).catch(function(e){ try{Rec.okErr(c.id,String(e))}catch(x){} }).then(function(){ busy=false; });
  }catch(e){ busy=false; try{Rec.okErr(c.id,String(e))}catch(x){} }
 }
 setInterval(function(){
  if(busy||!st.ready) return;
  var s=''; try{ s=Rec.okPoll(); }catch(e){ return; }
  if(!s) return;
  var c; try{ c=JSON.parse(s); }catch(e){ return; }
  run(c);
 },400);
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
    // Табор: голосование «Симпатии». Первые анкеты берём со страницы, дальше каждая оценка возвращает следующую
    Svc(
        "tabor", "Tabor", "https://m.tabor.ru/sympathy/vote.php",
        listOf("https://m.tabor.ru", "https://tabor.ru", "https://www.tabor.ru"), "tabor.ru", "liker_tabor", "_tabor",
        listOf("https://m.tabor.ru/", "https://tabor.ru/", "https://www.tabor.ru/"), ".tabor.ru",
        // оценка возвращает в ответе следующую анкету; в запросе перечисляются остальные уже показанные анкеты
        "/api/sympathy-vote.php?method=vote", "POST", "user_id={login}&type=like&ad_user={ad}{exclude}",
        "/sympathy/vote.php", "GET", "",
        20, TaborActivity::class.java, TaborKeepAliveService::class.java
    ),
    // OK: «ОК Знакомства» это мини-приложение внутри ok.ru (окно другого домена). Лента: dating.getRecommendedUsers,
    // лайк: dating.like (user_id + meta из ленты). Токен и прочие параметры берутся у самого приложения (см. OK_JS)
    Svc(
        "ok", "OK", "https://ok.ru/app/dating",
        listOf("https://ok.ru", "https://www.ok.ru", "https://m.ok.ru"), "ok.ru", "liker_ok", "_ok",
        listOf("https://ok.ru/", "https://www.ok.ru/", "https://m.ok.ru/"), ".ok.ru",
        OK_API + "dating.like", "POST", "user_id={login}&meta={meta}",
        OK_API + "dating.getRecommendedUsers", "POST", "count=20",
        30, OkActivity::class.java, OkKeepAliveService::class.java
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
    if (p.from == "tabor" || p.from == "ok") return "${p.name.ifEmpty { "имя ?" }}, ${if (p.age.isEmpty()) "? лет" else yearsText(p.age)}" + if (p.city.isNotEmpty()) ", ${p.city}" else ""
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
    // снимок состояния для главного окна (оно живёт в другом процессе и читает этот файл)
    private val statusFile = File(app.filesDir, "status${svc.suffix}.json")
    private var lastStatus = ""

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
    private val adUsers = HashSet<String>()
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

    // Лимит лайков у сайта (у каждого сайта свой, число мы не знаем: ориентируемся на ответ сайта).
    // Конец ожидания хранится в настройках по настенным часам, чтобы отсчёт переживал перезапуск приложения
    fun siteLimitLeftMs(): Long = maxOf(0L, prefs.getLong("siteLimitUntil", 0L) - System.currentTimeMillis())
    fun siteLimitActive(): Boolean = siteLimitLeftMs() > 0L
    fun siteLimitLeftText(): String {
        val m = (siteLimitLeftMs() + 59999L) / 60000L
        return if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин"
    }
    // Факт по лимиту сайта: сколько лайков удалось поставить за окно до отказа (запоминается при каждом отказе)
    fun siteMax(): Int = prefs.getInt("siteMax", 0)
    fun winCount(): Int = prefs.getInt("winCount", 0)
    fun limitInfo(): String {
        val mx = siteMax()
        val left = if (likesLeft >= 0) "осталось ${likesLeft}" else "осталось ?"
        return "лимит сайта: " + (if (mx > 0) "~$mx лайков (по факту)" else "ещё не выяснен") + " · в окне поставлено ${winCount()} · $left"
    }

    private fun siteLimitClock(): String {
        val f = SimpleDateFormat("HH:mm, d MMM", Locale("ru"))
        f.timeZone = mskTz
        return f.format(Date(prefs.getLong("siteLimitUntil", 0L))) + " МСК"
    }
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

    // OK: очередь команд для окна мини-приложения и состояние обмена с ним
    private val okCmds = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var okReadyFlag = false
    private var okSeq = 0
    private var okInId = 0
    private var okInKind = ""
    private var okInLogin = ""
    private val okMeta = HashMap<String, String>()

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
            writeStatus()
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
        writeStatus(true)
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
        if (siteLimitActive()) return "⏸ Лайки у сайта закончились. До сброса ${siteLimitLeftText()} (около ${siteLimitClock()})"
        if (limitWait && pauseUntil > SystemClock.elapsedRealtime()) return "⏸ лимит исчерпан, продолжу около $pauseClock"
        if (nightLogged) return "🌙 вне окна ${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)} МСК, сейчас ${mskNow()} МСК"
        if (autoOn) return "🔑 вход слетел, захожу сам (попытка $authFails из 4)"
        if (rowErr > 0) return "⚠ ошибки подряд: $rowErr, ${lastError.take(60)}"
        if (lastLikeAt == 0L) return "… ждём первый лайк"
        return "✅ лайки ставятся, последний ${ago((SystemClock.elapsedRealtime() - lastLikeAt) / 1000)} назад"
    }

    // Код состояния для цвета и подписи: off, wait, ok, limit, night, auth, err
    fun stateCode(): String {
        if (!running) return "off"
        if (siteLimitActive()) return "limit"
        if (limitWait && pauseUntil > SystemClock.elapsedRealtime()) return "limit"
        if (nightLogged) return "night"
        if (autoOn) return "auth"
        if (rowErr > 0) return "err"
        if (lastLikeAt == 0L) return "wait"
        return "ok"
    }

    // Есть ли сохранённый вход (cookie) или лайк уже проходил в этой сессии
    fun hasSession(): Boolean = sessionGood || prefs.getString("cookies", null) != null

    // Пишет снимок состояния в файл. Пока служба стоит, файл переписывается только при изменении
    fun writeStatus(force: Boolean = false) {
        try {
            val j = JSONObject()
            j.put("running", running)
            j.put("state", stateCode())
            j.put("verdict", verdict())
            j.put("count", prefs.getInt("count", 0))
            j.put("limit", prefs.getInt("limit", DEFAULT_LIMIT))
            j.put("left", likesLeft)
            j.put("siteMax", siteMax())
            j.put("winCount", winCount())
            j.put("day", prefs.getString("day", "") ?: "")
            j.put("sess", sessLikes)
            j.put("hour", hourCount)
            j.put("queue", queue.size)
            j.put("errors", errors)
            j.put("rate", rateText())
            j.put("next", nextInfo())
            j.put("win", "${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)}")
            j.put("session", hasSession())
            val body = j.toString()
            if (!force && !running && body == lastStatus) return
            lastStatus = body
            j.put("ts", System.currentTimeMillis())
            // сначала во временный файл, потом переименование: окно не прочитает наполовину записанный
            val tmp = File(statusFile.path + ".tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(statusFile)
        } catch (e: Exception) {
        }
    }

    fun nextInfo(): String {
        if (!running) return "-"
        if (siteLimitActive()) return "сброс лимита через ${siteLimitLeftText()}"
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
            "\n" + limitInfo() +
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
        okReadyFlag = false
        okCmds.clear()
        okInId = 0
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
        // OK: мини-приложение во вложенном окне, поэтому наш скрипт ставим во все окна страницы
        if (svc.id == "ok") {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(v, OK_JS, setOf("*"))
                } else {
                    logMsg("⚠ OK: эта версия WebView не умеет работать во вложенных окнах. Обнови «Android System WebView» в Google Play")
                }
            } catch (e: Exception) {
                logMsg("⚠ OK: не удалось включить работу во вложенных окнах: ${e.message}")
            }
        }
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

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host ?: return null
                if (BLOCK_HOSTS.any { host.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return null
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

        // OK: окно мини-приложения забирает команду (вызывается не из главного потока)
        @JavascriptInterface
        fun okPoll(): String = okCmds.poll() ?: ""

        @JavascriptInterface
        fun okReady(host: String) {
            h.post { onOkReady(host) }
        }

        @JavascriptInterface
        fun okResult(id: Int, status: Int, text: String) {
            h.post { onOkResult(id, status, text) }
        }

        @JavascriptInterface
        fun okErr(id: Int, msg: String) {
            h.post { onOkErr(id, msg) }
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
            siteLimitActive() -> "лимит сайта, сброс через ${siteLimitLeftText()}"
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
                .setContentTitle("${svc.title}: лайки идут")
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
        if (siteLimitActive()) {
            limitWait = true
            pauseUntil = SystemClock.elapsedRealtime() + siteLimitLeftMs()
        }
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
        writeStatus(true)
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
        writeStatus(true)
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
            if (limitWait && !siteLimitActive()) {
                limitPause("Лимит обновился, сразу не начинаю", false)
                return
            }
        }
        if (siteLimitActive()) {
            // ждём не одним длинным таймером, а короткими шагами: отсчёт идёт по часам и не зависит от сна телефона
            val d = minOf(siteLimitLeftMs(), 600000L) + 1000L
            nextAt = SystemClock.elapsedRealtime() + d
            h.removeCallbacks(tick)
            h.postDelayed(tick, d)
            onChange?.invoke()
            return
        }
        if (prefs.getLong("siteLimitUntil", 0L) > 0L) {
            prefs.edit().remove("siteLimitUntil").putLong("winStartAt", 0L).putInt("winCount", 0).apply()
            limitWait = false
            pauseUntil = 0L
            likesLeft = -1
            logMsg("▶ Время сброса лимита сайта наступило. Продолжаю")
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
        if (svc.id == "ok" && !okReadyFlag) {
            // мини-приложение ещё грузится и не показало свой токен
            notReady++
            if (notReady == 1) logMsg("… OK: жду, пока загрузится мини-приложение знакомств и выдаст токен")
            if (notReady > 45) {
                notReady = 0
                val u = page.url ?: ""
                logMsg("⚠ OK: мини-приложение не загрузилось за 90 с (страница: ${u.take(80)}). " +
                    (if (u.contains("login", true) || u.contains("st.cmd=anonym", true)) "Похоже, нет входа в ОК: войди во вкладке «Браузер». " else "") +
                    "Перезагружаю страницу")
                loadSite()
            }
            nextAt = SystemClock.elapsedRealtime() + 2000
            h.postDelayed(tick, 2000)
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
        if (svc.id == "ok") return okVal("sbody", svc.defSbody)
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
        if (svc.id == "tabor") {
            // у Табора списка нет: первые анкеты берём со страницы, дальше их присылает каждая оценка
            waiting = true
            h.postDelayed(wd, 20000)
            w().evaluateJavascript("if(window.__lk&&window.__lk.boot){window.__lk.boot();}else{Rec.apiErr('notready-batch','');}", null)
            return
        }
        if (svc.id == "ok") {
            okSend("batch", okVal("searchUrl", svc.defSearchUrl), okParams(searchBody(), "", "", offset), "")
            return
        }
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
        if (svc.id == "ok") {
            okSend("like", okVal("likeUrl", svc.defLikeUrl), okParams(okVal("likeBody", svc.defLikeBody), login, okMeta[login] ?: "", 0L), login)
            return
        }
        val tmpl = prefs.getString("likeUrl", svc.defLikeUrl) ?: svc.defLikeUrl
        val enc = URLEncoder.encode(login, "UTF-8")
        val url = tmpl.replace("{login}", enc)
        val method = prefs.getString("likeMethod", svc.defLikeMethod) ?: svc.defLikeMethod
        val bodyT = prefs.getString("likeBody", svc.defLikeBody) ?: ""
        val isJson = bodyT.trimStart().let { it.startsWith("{") || it.startsWith("[") }
        var body = bodyT.replace("{login}", if (isJson) login else enc)
        if (svc.id == "tabor") {
            // остальные уже показанные анкеты исключаем, как это делает сайт
            body = body.replace("{ad}", adUsers.contains(login).toString())
                .replace("{exclude}", queue.joinToString("") { "&exclude_user_ids%5B%5D=" + URLEncoder.encode(it, "UTF-8") })
        }
        waiting = true
        likeSentAt = SystemClock.elapsedRealtime()
        h.postDelayed(wd, 20000)
        w().evaluateJavascript(
            "if(window.__lk){window.__lk.like(${JSONObject.quote(login)}, ${JSONObject.quote(url)}, ${JSONObject.quote(method)}, ${JSONObject.quote(body)});}else{Rec.apiErr('notready-like', ${JSONObject.quote(login)});}", null
        )
    }

    // ---------- OK: обмен с окном мини-приложения ----------
    // Значение настройки; пустое считается «по умолчанию»
    private fun okVal(key: String, def: String): String = (prefs.getString(key, def) ?: def).ifBlank { def }

    // «user_id={login}&meta={meta}» -> JSON с параметрами (значения подставляются как есть, без перекодирования)
    private fun okParams(tmpl: String, login: String, meta: String, off: Long): JSONObject {
        val o = JSONObject()
        for (part in tmpl.split("&")) {
            if (part.isBlank()) continue
            val i = part.indexOf('=')
            val k = if (i < 0) part else part.substring(0, i)
            val raw = if (i < 0) "" else part.substring(i + 1)
            val v = java.net.URLDecoder.decode(raw, "UTF-8").replace("{login}", login).replace("{meta}", meta).replace("{offset}", off.toString())
            o.put(java.net.URLDecoder.decode(k, "UTF-8"), v)
        }
        return o
    }

    private fun okSend(kind: String, url: String, params: JSONObject, login: String) {
        okSeq++
        okInId = okSeq
        okInKind = kind
        okInLogin = login
        val c = JSONObject()
        c.put("id", okSeq)
        c.put("url", if (url.startsWith("http")) url else "https://okdating-app.ru$url")
        c.put("params", params)
        okCmds.clear()
        okCmds.add(c.toString())
        waiting = true
        likeSentAt = SystemClock.elapsedRealtime()
        h.postDelayed(wd, 20000)
    }

    private fun onOkReady(host: String) {
        if (okReadyFlag) return
        okReadyFlag = true
        notReady = 0
        logMsg("🔑 OK: мини-приложение загружено ($host), токен получен")
    }

    private fun okAuthBad(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return t.contains("\"error") && (t.contains("token") || t.contains("auth") || t.contains("session") || t.contains("unauthor"))
    }

    // Токен не принят: перезагружаем страницу, приложение само получит новый
    private fun okReauth(login: String?) {
        if (!running) return
        sessionGood = false
        authFails++
        errors++
        lastError = "OK: токен не принят"
        if (login != null) queue.addFirst(login)
        if (authFails > 4) {
            authPause("OK: не удалось получить новый токен (попыток: ${authFails - 1})")
            return
        }
        logMsg("⚠ OK: токен не принят, перезагружаю страницу и беру новый (попытка $authFails из 4)")
        if (authFails == 1) {
            val n = restoreCookies()
            if (n > 0) logMsg("🔑 Вернул сохранённую сессию (cookie: $n)")
        }
        loadSite()
        nextAt = SystemClock.elapsedRealtime() + 15000
        h.removeCallbacks(tick)
        h.postDelayed(tick, 15000)
        onChange?.invoke()
    }

    private fun onOkResult(id: Int, status: Int, text: String) {
        if (id != okInId) return
        okInId = 0
        if (okInKind == "batch") {
            h.removeCallbacks(wd)
            waiting = false
            if (!running) return
            if (status == 401 || status == 403 || okAuthBad(text)) {
                okReauth(null)
                return
            }
            if (status !in 200..299) {
                handleError("поиск: HTTP $status ${text.take(100)}")
                return
            }
            onBatch(text)
        } else {
            onLike(okInLogin, text, status)
        }
    }

    private fun onOkErr(id: Int, msg: String) {
        if (id != okInId) return
        okInId = 0
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        handleError("OK: запрос не прошёл ($msg)")
    }

    // Анкета из ленты OK: имя, возраст, расстояние
    private fun okProf(o: JSONObject): Prof {
        val d = o.optJSONObject("extra")?.optDouble("distance", -1.0) ?: -1.0
        val km = if (d >= 0) (if (d >= 10000) "${(d / 1000).toInt()} км" else String.format(Locale.US, "%.1f км", d / 1000)) else ""
        return Prof(o.optString("name"), o.optString("age"), "ok", "", km)
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
        if (svc.id == "tabor") {
            if (status == 401 || status == 403) return 24
            if (status !in 200..299) return status
            if (text.trimStart().startsWith("<")) return 24 // вместо ответа страница (вход слетел)
            return if (j.optBoolean("error", false)) -1 else 0
        }
        if (status == 401 || status == 403) return 24
        if (status !in 200..299) return status
        if (text.trimStart().startsWith("<")) return -1 // вместо ответа пришла HTML-страница (вход, капча)
        if (svc.id == "ok") {
            if (!text.trimStart().startsWith("{")) return -1
            if (okAuthBad(text)) return 24
            if (j.has("error_code") && j.optInt("error_code", 0) != 0) return -1
        }
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

    private fun onTaborBoot(text: String) {
        try {
            val j = JSONObject(text)
            val ids = j.optJSONArray("ids")
            var added = 0
            if (ids != null) {
                for (i in 0 until ids.length()) {
                    val id = ids.optString(i)
                    if (id.isNotEmpty() && seen.add(id)) {
                        queue.addLast(id)
                        added++
                    }
                }
            }
            if (added > 0) {
                rowErr = 0
                logMsg("🔎 Табор: на странице найдено анкет: $added")
                scheduleNext(quick = true)
                return
            }
            val stub = j.optString("stub", "")
            if (stub.isNotEmpty() && looksLikeLimit(stub)) {
                logMsg("⚠ Табор вместо анкет показывает сообщение: «$stub»")
                siteLimitPause("Лайки у сайта закончились", stub)
                return
            }
            if (stub.isNotEmpty()) {
                // сайт сам показал экран ошибки вместо анкет (нет геопозиции, лимит, анкеты закончились и т.п.)
                val d = 900000L + rnd.nextInt(600000)
                logMsg("⚠ Табор вместо анкет показывает сообщение: «$stub». Обновлю страницу и проверю через ${d / 60000} мин")
                if (!hasLoc()) logMsg("   У приложения нет разрешения на геопозицию: разреши её в настройках приложения (Tabor Liker)")
                loadSite()
                nextAt = SystemClock.elapsedRealtime() + d
                h.postDelayed(tick, d)
                return
            }
            val diag = j.optString("diag", "")
            if (diag.isNotEmpty()) {
                handleError("Табор: не нашёл анкеты на странице: ${diag.take(400)}")
                return
            }
            // анкеты на странице есть, но все уже оценены: обновим страницу позже
            val d = 1800000L + rnd.nextInt(1800000)
            logMsg("⏸ Табор: новых анкет нет. Обновлю страницу и проверю снова через ${d / 60000} мин")
            loadSite()
            nextAt = SystemClock.elapsedRealtime() + d
            h.postDelayed(tick, d)
        } catch (ex: Exception) {
            handleError("Табор: страница не разобрана: ${text.take(100)}")
        }
    }

    private fun onBatch(text: String) {
        h.removeCallbacks(wd)
        waiting = false
        if (!running) return
        notReady = 0
        if (svc.id == "tabor") {
            onTaborBoot(text)
            return
        }
        try {
            val mamba = svc.id == "mamba"
            val ok = svc.id == "ok"
            val j = JSONObject(text)
            val arr = if (mamba) mambaList(j) else if (ok) j.optJSONArray("users") else (j.optJSONArray("data") ?: j.optJSONArray("user"))
            if (arr == null) {
                handleError("поиск: нет списка анкет: ${text.take(if (mamba) 400 else 100)}")
                return
            }
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lg = if (mamba) mambaPhotoId(o) else if (ok) o.optString("id") else o.optString("login")
                if (lg.isEmpty()) continue
                if (ok && (o.optBoolean("is_deleted", false) || o.optBoolean("is_blocked", false))) continue
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
                    if (ok) okMeta[lg] = o.optJSONObject("extra")?.optString("meta") ?: ""
                    profInfo[lg] = if (mamba) Prof("фото $lg", "", "", "", "") else if (ok) okProf(o) else profileInfo(o)
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
            } else if (ok) {
                // лента сама отдаёт следующих; если новых анкет нет, считаем, что на сейчас всё
                offset += arr.length()
                hasMore = added > 0
            } else {
                offset += arr.length()
                hasMore = j.optInt("has_more", 0) == 1
            }
            rowErr = 0
            if (added > 0) resumedScan = false
            if (added == 0) emptyStreak++ else emptyStreak = 0
            if (seen.size > 30000) seen.clear()
            if (okMeta.size > 3000) okMeta.keys.retainAll(queue.toSet())
            // в журнал пишем не каждый пустой пролистанный блок
            if (added > 0 || emptyStreak <= 2 || emptyStreak % 5 == 0) {
                logMsg("🔎 Поиск: позиции $startOff–$offset, новых для лайка: $added, в очереди: ${queue.size}" +
                    (if (ok && j.has("remaining")) ", осталось у сайта: ${j.optInt("remaining", -1)}" else "") +
                    if (added == 0) " (все уже лайкнуты, листаю дальше)" else "")
            }
            if (!mamba) {
                prefs.edit().putInt("scanOffset", offset.toInt()).putLong("scanAt", System.currentTimeMillis())
                    .putInt("scanSig", searchBody().hashCode()).apply()
            }
            if (ok && arr.length() == 0 && j.has("remaining") && j.optInt("remaining", 1) <= 0) {
                likesLeft = 0
                siteLimitPause("Лимит сайта исчерпан (лента пуста, осталось 0)", text)
                return
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
                if (svc.id == "tabor") {
                    val u = j.optJSONObject("user")
                    val nid = u?.optString("id") ?: ""
                    if (u != null && nid.isNotEmpty() && seen.add(nid)) {
                        queue.addLast(nid)
                        if (j.optBoolean("ad_user", false)) adUsers.add(nid)
                        profInfo[nid] = Prof(u.optString("username"), u.optString("age"), "tabor", "", u.optString("city"))
                    }
                    adUsers.remove(login)
                }
                if (svc.id == "mamba") {
                    val ow = j.optString("photoOwnerId", "")
                    if (ow.isNotEmpty()) profInfo[login] = Prof("профиль $ow (фото $login)", "", "", "", "")
                }
                val ed = prefs.edit().putInt("count", prefs.getInt("count", 0) + 1).putInt("winCount", winCount() + 1)
                if (prefs.getLong("winStartAt", 0L) == 0L) ed.putLong("winStartAt", System.currentTimeMillis())
                ed.apply()
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
                if (svc.id == "ok" && j.has("remaining")) likesLeft = j.optInt("remaining", -1)
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
                if ((j.has("likesleft") || (svc.id == "ok" && j.has("remaining"))) && likesLeft <= 0) {
                    siteLimitPause("Лайки у сайта закончились", text)
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
        if (svc.id == "ok") {
            authFails--
            okReauth(login)
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
        if (likesLeft == 0 || looksLikeLimit(msg)) {
            logMsg("⚠ $msg")
            siteLimitPause("Лайки у сайта закончились", msg)
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

    private fun looksLikeLimit(msg: String): Boolean {
        val t = msg.lowercase(Locale.ROOT)
        return listOf("лимит", "limit", "исчерп", "закончил").any { t.contains(it) }
    }

    // Ищем в ответе сайта время сброса лимита (поля вида reset_after, resetAt, retry_after, until).
    // Нулевые и странные даты не берём. Возвращает, через сколько миллисекунд сайт сбросит лимит, или null
    private fun parseResetMs(raw: String): Long? {
        val i = raw.indexOf('{')
        if (i < 0) return null
        val now = System.currentTimeMillis()
        var best: Long? = null
        fun consider(key: String, v: Any?) {
            val k = key.lowercase(Locale.ROOT)
            if (!(k.contains("reset") || k.contains("until") || k.contains("retry"))) return
            var at: Long? = null
            try {
                if (v is String) {
                    val n = v.trim().toDoubleOrNull()
                    at = if (n != null) numToAt(k, n, now) else java.time.Instant.parse(v.trim()).toEpochMilli()
                } else if (v is Number) {
                    at = numToAt(k, v.toDouble(), now)
                }
            } catch (e: Exception) {
            }
            val d = if (at == null) 0L else at - now
            if (d in 60000L..259200000L && (best == null || d > best!!)) best = d
        }
        fun walk(o: JSONObject, depth: Int) {
            val it = o.keys()
            while (it.hasNext()) {
                val key = it.next()
                val v = o.opt(key)
                if (v is JSONObject && depth < 3) walk(v, depth + 1) else consider(key, v)
            }
        }
        try {
            walk(JSONObject(raw.substring(i)), 0)
        } catch (e: Exception) {
        }
        return best
    }

    // Число в поле сброса: время в секундах/миллисекундах от 1970 года или «через N секунд»
    private fun numToAt(key: String, n: Double, now: Long): Long? = when {
        n > 1.0e12 -> n.toLong()
        n > 1.0e9 -> (n * 1000).toLong()
        n > 0 && (key.contains("in") || key.contains("after") || key.contains("retry")) -> now + (n * 1000).toLong()
        else -> null
    }

    // Сайт сказал, что лайки закончились: сообщаем и ждём до сброса. Время сброса берём из ответа сайта,
    // а если сайт его не назвал, ждём 24 часа
    private fun siteLimitPause(reason: String, raw: String = "") {
        val fromSite = parseResetMs(raw)
        // запоминаем, сколько лайков дал сайт до отказа
        if (winCount() > 0) prefs.edit().putInt("siteMax", winCount()).apply()
        // сайт время не назвал: окно считаем от первого лайка (+24 ч). Если оно уже прошло, а отказ остался, проверяем каждые 30 мин
        val winEnd = prefs.getLong("winStartAt", 0L).let { if (it > 0L) it + SITE_LIMIT_MS - System.currentTimeMillis() else 0L }
        val dur = fromSite ?: (if (winEnd > 600000L) winEnd else 1800000L)
        val until = System.currentTimeMillis() + dur + (if (fromSite != null) 30000L else 0L)
        prefs.edit().putLong("siteLimitUntil", until).apply()
        limitWait = true
        pauseUntil = SystemClock.elapsedRealtime() + (until - System.currentTimeMillis())
        pauseClock = SimpleDateFormat("HH:mm", Locale.US).format(Date(until))
        likesLeft = 0
        val src = if (fromSite != null) "Сайт сам назвал время сброса" else if (winEnd > 600000L) "Время сброса сайт не назвал: считаю сутки от первого лайка окна" else "Сутки от первого лайка уже прошли, а сайт всё ещё отказывает: проверяю каждые 30 мин"
        logMsg("⏸ $reason. Лайков в окне до отказа: ${winCount()}. $src, продолжу около ${siteLimitClock()}")
        if (raw.isNotEmpty()) logMsg("   ответ сайта: ${raw.take(300)}")
        notifyAlert(4, "${svc.title}: лайки у сайта закончились", "Сброс через ${siteLimitLeftText()} (около ${siteLimitClock()}), потом продолжу сам")
        nextAt = SystemClock.elapsedRealtime() + 600000L
        h.removeCallbacks(tick)
        h.postDelayed(tick, 600000L)
        writeStatus(true)
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
