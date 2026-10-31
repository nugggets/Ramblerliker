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

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Switch
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

private const val HOOK_JS = """
(function(){
 if(window.__rec) return; window.__rec=1;
 var send=function(o){try{Rec.push(JSON.stringify(o))}catch(e){}};
 var hs=function(h){ var out=[]; try{ if(!h) return '';
   if(Array.isArray(h)){ h.forEach(function(p){out.push(p[0]+': '+p[1])}); }
   else if(typeof h.forEach==='function'){ h.forEach(function(v,k){out.push(k+': '+v)}); }
   else { for(var k in h) out.push(k+': '+h[k]); }
  }catch(e){} return out.join('\n'); };
 var bs=function(b){ try{ if(typeof FormData!=='undefined' && b instanceof FormData){ var o=[]; b.forEach(function(v,k){ var t=(typeof v==='string')?v:'[file]'; if(t.length>40) t=t.slice(0,12)+'...[len '+t.length+']'; o.push(k+'='+t); }); return 'FormData: '+o.join('&'); } }catch(e){} return String(b||''); };
 var dsc=function(e){ try{ var out=[], n=e, i=0;
   while(n&&n.nodeType===1&&i<5){
    var c=(typeof n.className==='string')?n.className:((n.className&&n.className.baseVal)||'');
    var t=(n.innerText||n.textContent||'').replace(/\s+/g,' ').trim().slice(0,30);
    var a=(n.getAttribute&&(n.getAttribute('aria-label')||n.getAttribute('data-testid')||n.getAttribute('title')))||'';
    out.push(n.tagName.toLowerCase()+(n.id?'#'+n.id:'')+(c?'.'+String(c).trim().split(/\s+/).slice(0,3).join('.'):'')+(a?'['+a+']':'')+(t?' ['+t+']':''));
    n=n.parentElement; i++; }
   return out.join(' < '); }catch(x){ return 'err'; } };
 var ck=0;
 try{ document.addEventListener('click',function(e){ if(ck++>60) return;
   var r=(e.target&&e.target.getBoundingClientRect)?e.target.getBoundingClientRect():{left:0,top:0,width:0,height:0};
   send({m:'CLICK',u:String(location.href).slice(0,90),s:0,p:'CLICK '+dsc(e.target)+' | rect '+Math.round(r.left)+','+Math.round(r.top)+','+Math.round(r.width)+'x'+Math.round(r.height),h:'',r:''}); },true); }catch(e){}
 var rn=0, rc=0;
 var resPoll=function(){ try{ var l=performance.getEntriesByType('resource');
   for(;rn<l.length;rn++){ var e=l[rn], it=e.initiatorType||'';
    if(it==='img'||it==='css'||it==='script'||it==='link'||it==='font'||it==='video'||it==='audio') continue;
    if(rc++>150) return;
    send({m:'RES',u:String(e.name).slice(0,160),s:0,p:'DIAG '+it+' '+Math.round(e.duration)+'ms '+(e.transferSize||0)+'b @'+String(location.hostname),h:'',r:''}); } }catch(x){} };
 try{ setInterval(resPoll,2000); }catch(e){}
 try{ var OWk=window.Worker; if(OWk){ window.Worker=function(u,o){
   try{ send({m:'WORKER',u:String(u).slice(0,160),s:0,p:'DIAG worker @'+String(location.hostname),h:'',r:''}); }catch(e){}
   return o?new OWk(u,o):new OWk(u); }; window.Worker.prototype=OWk.prototype; } }catch(e){}
 try{ var OES=window.EventSource; if(OES){ window.EventSource=function(u,o){
   try{ send({m:'SSE',u:String(u).slice(0,160),s:0,p:'DIAG eventsource @'+String(location.hostname),h:'',r:''}); }catch(e){}
   return o?new OES(u,o):new OES(u); }; window.EventSource.prototype=OES.prototype; } }catch(e){}
 try{ setTimeout(function(){
   var out=[]; try{ for(var i=0;i<localStorage.length&&i<40;i++){ var k=localStorage.key(i), v=localStorage.getItem(k)||''; out.push(k.slice(0,40)+'='+v.length+'b:'+v.slice(0,40).replace(/\s+/g,' ')); } }catch(e){ out.push('нет доступа'); }
   send({m:'LS',u:String(location.hostname),s:0,p:'DIAG localStorage '+out.join(' | ').slice(0,1800),h:'',r:''});
   try{ if(window.indexedDB&&indexedDB.databases){ indexedDB.databases().then(function(d){ send({m:'IDB',u:String(location.hostname),s:0,p:'DIAG indexedDB '+d.map(function(x){return x.name}).join(', '),h:'',r:''}); }); } }catch(e){}
  },4000); }catch(e){}
 var wsn=0, OW=window.WebSocket;
 if(OW){ window.WebSocket=function(u,pr){ var ws=pr?new OW(u,pr):new OW(u);
   try{ send({m:'WS',u:String(u),s:0,p:'open',h:'',r:''});
    ws.addEventListener('message',function(e){ if(wsn++>80) return; var d=e.data; send({m:'WS',u:String(u),s:1,p:'in',h:'',r:(typeof d==='string'?d:'[binary]').slice(0,1500)}); });
    var os=ws.send; ws.send=function(d){ if(wsn++<=80) send({m:'WS',u:String(u),s:2,p:(typeof d==='string'?d:'[binary]').slice(0,1000),h:'',r:''}); return os.apply(ws,arguments); };
   }catch(e){}
   return ws; };
  window.WebSocket.prototype=OW.prototype;
  ['CONNECTING','OPEN','CLOSING','CLOSED'].forEach(function(k){ window.WebSocket[k]=OW[k]; }); }
 var of=window.fetch;
 window.__origFetch=of;
 if(of){ window.fetch=function(i,init){
   var url=(typeof i==='string')?i:((i&&i.url)||'');
   var m=(init&&init.method)||(i&&i.method)||'GET';
   var b=(init&&init.body)||'';
   var hh=hs((init&&init.headers)||(i&&i.headers));
   return of.apply(this,arguments).then(function(r){
     try{ r.clone().text().then(function(t){ send({m:m,u:url,s:r.status,p:bs(b).slice(0,1000),h:hh,r:t.slice(0,4000)}); }); }catch(e){}
     return r; });
 }; }
 var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
 XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;this.__h=[];return xo.apply(this,arguments)};
 var xsh=XMLHttpRequest.prototype.setRequestHeader;
 XMLHttpRequest.prototype.setRequestHeader=function(n,v){try{(this.__h=this.__h||[]).push(n+': '+v)}catch(e){} return xsh.apply(this,arguments)};
 XMLHttpRequest.prototype.send=function(b){
   var x=this;
   this.addEventListener('loadend',function(){
     var t=''; try{t=x.responseText||''}catch(e){}
     send({m:x.__m,u:x.__u,s:x.status,p:bs(b).slice(0,1000),h:(x.__h||[]).join('\n'),r:t.slice(0,4000)});
   });
   return xs.apply(this,arguments)};
})();
"""

private var lastRecreate = 0L

open class MainActivity : Activity() {
    // Какой сервис ведёт это окно: у Mamba и Tabor свои окна в своих процессах, лайки идут одновременно
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
    private lateinit var navIcons: List<TextView>
    private lateinit var navLabels: List<TextView>
    private lateinit var recText: TextView
    private lateinit var recFilter: EditText
    private lateinit var etLimit: EditText
    private lateinit var etRate: EditText
    private lateinit var etWinFrom: EditText
    private lateinit var etWinTo: EditText
    private lateinit var etLikeUrl: EditText
    private lateinit var logText: TextView
    private lateinit var logStats: TextView
    private lateinit var segAll: TextView
    private lateinit var segErr: TextView
    private lateinit var segToday: TextView
    private lateinit var segEver: TextView
    private lateinit var barSub: TextView
    private lateinit var barChip: TextView
    private lateinit var stTitle: TextView
    private lateinit var stVerdict: TextView
    private lateinit var stCount: TextView
    private lateinit var stLimit: TextView
    private lateinit var stLeft: TextView
    private lateinit var stBar: BarView
    private lateinit var stErr: TextView
    private lateinit var tHour: TextView
    private lateinit var tSess: TextView
    private lateinit var tQueue: TextView
    private lateinit var tErr: TextView
    private lateinit var tNext: TextView
    private lateinit var tRun: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var repNumToday: TextView
    private lateinit var repNumHour: TextView
    private lateinit var repNumSess: TextView
    private lateinit var repMeta: TextView
    private lateinit var repHours: HoursView
    private lateinit var repText: TextView
    private var accent = 0
    private var onlyToday = true
    private var logRenderedVer = -1
    private var renderedOnlyErr = false
    private var lastRestored = 0
    private var curTab = 0
    private var onlyErr = false
    private var hideNoise = true
    private var onlyPost = false
    private var webLoaded = false // страница в окне «Браузер» грузится только при первом открытии вкладки
    private var lastRunningUi: Boolean? = null
    private var settingAuto = false
    private var repKey = ""
    private val rec = ArrayList<JSONObject>()
    private val h = Handler(Looper.getMainLooper())
    private val timeF = SimpleDateFormat("HH:mm:ss", Locale.US)
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

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun lbl(t: String): TextView = tv(t, 12f, Pal.MUTED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Engine.get(this, serviceId())
        prefs = engine.prefs
        accent = svcColor(engine.svc.id)
        window.statusBarColor = Pal.SURFACE
        window.navigationBarColor = Pal.SURFACE
        try {
            val nmc = getSystemService(NotificationManager::class.java)
            nmc.cancel(engine.svc.nidOff + 2)
            nmc.cancel(engine.svc.nidOff + 3)
        } catch (e: Exception) {
        }
        val askPerms = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notifAsked", false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
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

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Pal.BG)
        root.addView(buildAppBar(), lp(MATCH, WRAP))
        val strip = View(this)
        strip.setBackgroundColor(accent)
        root.addView(strip, lp(MATCH, dp(2)))
        val content = FrameLayout(this)
        panels = listOf(buildControlTab(), buildLogTab(), buildReportTab(), buildBrowserTab(), buildRecTab())
        for (p in panels) content.addView(p, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(content, lp(MATCH, 0, 1f))
        root.addView(buildNav(), lp(MATCH, WRAP))
        setContentView(root)
        show(intent.getIntExtra("tab", 0).coerceIn(0, 4))
        engine.ensureRunning() // по умолчанию лайки идут всегда, если не нажимали «Стоп»
        askBackgroundOnce()
    }

    // ---------- сборка экрана ----------
    private fun buildAppBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Pal.SURFACE)
        bar.setPadding(dp(6), dp(8), dp(14), dp(8))
        val back = TextView(this)
        back.text = "←"
        back.textSize = 24f
        back.gravity = Gravity.CENTER
        back.isClickable = true
        back.paintBtn(0, Pal.TEXT, 0, 22)
        back.setOnClickListener { leave() }
        bar.addView(back, lp(dp(44), dp(44), r = 4))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(engine.svc.title, 18f, Pal.TEXT, true))
        barSub = tv("", 12f, Pal.MUTED)
        col.addView(barSub)
        bar.addView(col, lp(0, WRAP, 1f))
        barChip = chip("…", Pal.MUTED)
        bar.addView(barChip)
        return bar
    }

    private fun buildNav(): View {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Pal.SURFACE)
        val items = listOf("🎛" to "Управление", "📜" to "Журнал", "📊" to "Отчёт", "🌐" to "Браузер", "🧪" to "Запросы")
        val ic = ArrayList<TextView>()
        val lb = ArrayList<TextView>()
        for ((i, item) in items.withIndex()) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, dp(8), 0, dp(8))
            cell.isClickable = true
            cell.setOnClickListener { show(i) }
            val a = tv(item.first, 18f)
            a.gravity = Gravity.CENTER
            val b = tv(item.second, 10f, Pal.MUTED, true)
            b.gravity = Gravity.CENTER
            cell.addView(a, lp(WRAP, WRAP))
            cell.addView(b, lp(WRAP, WRAP))
            nav.addView(cell, lp(0, WRAP, 1f))
            ic.add(a)
            lb.add(b)
        }
        navIcons = ic
        navLabels = lb
        return nav
    }

    private fun paintSeg(v: TextView, on: Boolean) {
        if (on) v.paintBtn(tint(accent, 0x33), accent, accent, 10) else v.paintBtn(Pal.SURFACE2, Pal.MUTED, Pal.STROKE, 10)
    }

    private fun seg(text: String, on: Boolean, f: () -> Unit): TextView {
        val v = pill(text, Pal.SURFACE2, Pal.MUTED, Pal.STROKE, 12f, 8, f)
        paintSeg(v, on)
        return v
    }

    private fun tile(parent: LinearLayout, label: String): TextView {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(10), dp(8), dp(10), dp(8))
        box.background = roundRect(Pal.SURFACE2, dpf(10f))
        val v = tv("-", 15f, Pal.TEXT, true)
        box.addView(lbl(label).apply { textSize = 11f })
        box.addView(v, lp(MATCH, WRAP, t = 2))
        val first = parent.childCount == 0
        parent.addView(box, lp(0, WRAP, 1f, l = if (first) 0 else 8))
        return v
    }

    private fun bigNum(parent: LinearLayout, label: String): TextView {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val v = tv("0", 28f, Pal.TEXT, true)
        box.addView(v)
        box.addView(lbl(label))
        parent.addView(box, lp(0, WRAP, 1f))
        return v
    }

    // Вкладка «Управление»: статус, главная кнопка, темп и лимит, аккаунт, адреса
    private fun buildControlTab(): View {
        val sv = engine.svc
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(12), dp(12), dp(24))

        // --- статус и главная кнопка ---
        val st = card()
        stTitle = tv("", 22f, Pal.TEXT, true)
        stVerdict = tv("", 13f, Pal.MUTED)
        st.addView(stTitle)
        st.addView(stVerdict, lp(MATCH, WRAP, t = 2))
        val cr = LinearLayout(this)
        cr.orientation = LinearLayout.HORIZONTAL
        cr.gravity = Gravity.BOTTOM
        stCount = tv("0", 34f, Pal.TEXT, true)
        stLimit = tv(" / 0", 16f, Pal.MUTED)
        stLeft = lbl("")
        cr.addView(stCount)
        cr.addView(stLimit, lp(WRAP, WRAP, l = 4, b = 6))
        cr.addView(View(this), lp(0, 0, 1f))
        cr.addView(stLeft, lp(WRAP, WRAP, b = 6))
        st.addView(cr, lp(MATCH, WRAP, t = 12))
        stBar = BarView(this)
        stBar.barColor = accent
        st.addView(stBar, lp(MATCH, dp(8), t = 6))
        val t1 = LinearLayout(this)
        tHour = tile(t1, "За час")
        tSess = tile(t1, "За сессию")
        tQueue = tile(t1, "В очереди")
        st.addView(t1, lp(MATCH, WRAP, t = 12))
        val t2 = LinearLayout(this)
        tErr = tile(t2, "Ошибок")
        tNext = tile(t2, "Следующее")
        tRun = tile(t2, "Работает")
        st.addView(t2, lp(MATCH, WRAP, t = 8))
        stErr = tv("", 12f, Pal.ERR)
        stErr.visibility = View.GONE
        st.addView(stErr, lp(MATCH, WRAP, t = 10))
        toggleBtn = pill("▶  Запустить", accent, Pal.WHITE, 0, 16f, 14) { onToggleClicked() }
        st.addView(toggleBtn, lp(MATCH, WRAP, t = 14))
        st.addView(pill("📋  Скопировать диагностику (лимит, остаток, запросы)", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 10) { copyDiag() }, lp(MATCH, WRAP, t = 8))
        col.addView(st, lp(MATCH, WRAP))

        // --- темп и лимит ---
        val tempo = card()
        tempo.addView(tv("Темп и лимит", 16f, Pal.TEXT, true))
        tempo.addView(lbl("Лимит лайков в сутки (максимум $MAX_LIMIT)"), lp(MATCH, WRAP, t = 12))
        etLimit = inp("60", prefs.getInt("limit", DEFAULT_LIMIT).toString(), true)
        val lr = LinearLayout(this)
        lr.gravity = Gravity.CENTER_VERTICAL
        lr.addView(pill("−10", Pal.SURFACE2, Pal.TEXT, Pal.STROKE) { stepLimit(-10) }, lp(dp(56), WRAP, r = 8))
        lr.addView(etLimit, lp(0, WRAP, 1f))
        lr.addView(pill("+10", Pal.SURFACE2, Pal.TEXT, Pal.STROKE) { stepLimit(10) }, lp(dp(56), WRAP, l = 8))
        tempo.addView(lr, lp(MATCH, WRAP, t = 6))
        tempo.addView(lbl("Лайков в минуту, от–до (пауза случайная, поэтому темп плавает)"), lp(MATCH, WRAP, t = 14))
        etRate = inp("60-80", engine.rateText(), false)
        tempo.addView(etRate, lp(MATCH, WRAP, t = 6))
        tempo.addView(lbl("Окно работы по Москве, часы от и до (0 и 24 = круглосуточно)"), lp(MATCH, WRAP, t = 14))
        etWinFrom = inp("с (час)", prefs.getInt("winFrom", DEFAULT_WIN_FROM).toString(), true)
        etWinTo = inp("до (час)", prefs.getInt("winTo", DEFAULT_WIN_TO).toString(), true)
        val wr = LinearLayout(this)
        wr.gravity = Gravity.CENTER_VERTICAL
        wr.addView(etWinFrom, lp(0, WRAP, 1f))
        wr.addView(tv("—", 16f, Pal.MUTED), lp(WRAP, WRAP, l = 10, r = 10))
        wr.addView(etWinTo, lp(0, WRAP, 1f))
        tempo.addView(wr, lp(MATCH, WRAP, t = 6))
        tempo.addView(
            pill("Сохранить настройки", tint(accent, 0x33), accent, accent) { saveSettings() },
            lp(MATCH, WRAP, t = 14)
        )
        tempo.addView(lbl("Настройки действуют сразу, перезапускать не нужно."), lp(MATCH, WRAP, t = 8))
        col.addView(tempo, lp(MATCH, WRAP, t = 12))

        // --- аккаунт и фон ---
        val acc = card()
        acc.addView(tv("Аккаунт и фон", 16f, Pal.TEXT, true))
        val sr = LinearLayout(this)
        sr.gravity = Gravity.CENTER_VERTICAL
        val sc = LinearLayout(this)
        sc.orientation = LinearLayout.VERTICAL
        sc.addView(tv("Автовход", 14f, Pal.TEXT, true))
        sc.addView(lbl("Сам нажимает «Войти» и «Продолжить», если вход слетел"))
        sr.addView(sc, lp(0, WRAP, 1f))
        autoSwitch = Switch(this)
        sr.addView(autoSwitch, lp(WRAP, WRAP, l = 8))
        acc.addView(sr, lp(MATCH, WRAP, t = 12))
        refreshAutoSwitch()
        autoSwitch.setOnCheckedChangeListener { _, on ->
            if (!settingAuto) {
                prefs.edit().putBoolean("autoLogin", on).apply()
                engine.pushAuto()
                engine.logMsg(if (on) "Автовход включён: сам нажимаю «Войти» и «Продолжить»" else "Автовход выключен")
            }
        }
        acc.addView(pill("🔋  Фоновая работа: разрешить", Pal.SURFACE2, Pal.TEXT, Pal.STROKE) { setupBackground() }, lp(MATCH, WRAP, t = 14))
        acc.addView(pill("📋  Скопировать сессию для сервера", Pal.SURFACE2, Pal.TEXT, Pal.STROKE) { copySession() }, lp(MATCH, WRAP, t = 8))
        acc.addView(pill("🚪  Выйти из аккаунта", tint(Pal.ERR, 0x22), Pal.ERR) { confirmLogout() }, lp(MATCH, WRAP, t = 8))
        col.addView(acc, lp(MATCH, WRAP, t = 12))

        // --- адреса и подсказки (свёрнуто) ---
        val adv = card()
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.visibility = View.GONE
        val head = tv("Адреса и подсказки  ▾", 16f, Pal.TEXT, true)
        head.isClickable = true
        head.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            head.text = if (open) "Адреса и подсказки  ▴" else "Адреса и подсказки  ▾"
        }
        adv.addView(head)
        etLikeUrl = inp("URL лайка", prefs.getString("likeUrl", sv.defLikeUrl) ?: sv.defLikeUrl, false)
        etLikeMethod = inp("GET или POST", prefs.getString("likeMethod", sv.defLikeMethod) ?: sv.defLikeMethod, false)
        etLikeBody = inp("тело запроса лайка (если есть)", prefs.getString("likeBody", sv.defLikeBody) ?: sv.defLikeBody, false)
        etSearchUrl = inp("URL поиска", prefs.getString("searchUrl", sv.defSearchUrl) ?: sv.defSearchUrl, false)
        body.addView(lbl("URL лайка ({login} подставляется сам)"), lp(MATCH, WRAP, t = 12))
        body.addView(etLikeUrl, lp(MATCH, WRAP, t = 6))
        body.addView(lbl("Метод лайка"), lp(MATCH, WRAP, t = 12))
        body.addView(etLikeMethod, lp(MATCH, WRAP, t = 6))
        body.addView(lbl("Тело запроса лайка ({login} подставляется сам)"), lp(MATCH, WRAP, t = 12))
        body.addView(etLikeBody, lp(MATCH, WRAP, t = 6))
        body.addView(lbl("URL поиска ({offset} подставляется сам)"), lp(MATCH, WRAP, t = 12))
        body.addView(etSearchUrl, lp(MATCH, WRAP, t = 6))
        body.addView(
            pill("URL лайка по умолчанию", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { etLikeUrl.setText(sv.defLikeUrl) },
            lp(MATCH, WRAP, t = 12)
        )
        if (sv.id == "mamba") {
            body.addView(
                lbl(
                    "Адреса ${sv.title} уже заданы: лента «Рейтинг» и лайк фото. " +
                        "1) Вкладка «Браузер»: войди и открой «Рейтинг». " +
                        "2) Фильтры (пол, возраст, город) задай один раз на самом сайте: приложение берёт их оттуда. " +
                        "3) Поставь лимит 3 и жми «Запустить». Лента листается по id последнего фото, лайк ставится на фото."
                ), lp(MATCH, WRAP, t = 14)
            )
        }
        if (sv.id == "ok") {
            body.addView(
                lbl(
                    "Адреса ${sv.title} заданы по записи запросов: лента dating.getRecommendedUsers, лайк dating.like. " +
                        "Токен и остальные параметры приложение берёт у самого мини-приложения знакомств, ничего вводить не нужно. " +
                        "1) Вкладка «Браузер»: войди в ОК и открой знакомства. " +
                        "2) Поставь лимит 3 и жми «Запустить». В журнале должно появиться «OK: мини-приложение загружено, токен получен». " +
                        "3) Если адрес лайка окажется другим, поставь в «Браузере» один лайк вручную: приложение запомнит адрес само (строка «🔎 OK: запомнил адрес лайка»)."
                ), lp(MATCH, WRAP, t = 14)
            )
        }
        if (sv.id == "tabor") {
            body.addView(
                lbl(
                    "Адреса ${sv.title} заданы по записи запросов. " +
                        "1) Вкладка «Браузер»: войди (капчу вводи сам) и открой голосование «Симпатии». " +
                        "2) Поставь лимит 3 и жми «Запустить». Первые анкеты приложение берёт со страницы, дальше каждая оценка возвращает следующую. " +
                        "3) Если в журнале «Табор: не нашёл анкеты на странице», пришли эту строку целиком."
                ), lp(MATCH, WRAP, t = 14)
            )
        }
        body.addView(
            lbl(
                "Приложение берёт анкеты из поиска сайта (с твоими фильтрами) и шлёт лайки напрямую. " +
                    "Лайки ставит фоновая служба: она работает, даже когда окно закрыто, и сама запускается " +
                    "после перезагрузки телефона и после закрытия приложения системой. Остановить её можно " +
                    "только кнопкой «Остановить». Лайки идут только в окне работы по Москве. Первый тест: лимит 3."
            ), lp(MATCH, WRAP, t = 14)
        )
        adv.addView(body, lp(MATCH, WRAP))
        col.addView(adv, lp(MATCH, WRAP, t = 12))

        val scroll = ScrollView(this)
        scroll.addView(col)
        return scroll
    }

    private fun buildLogTab(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(12), dp(12), dp(8))
        val sc = card()
        logStats = tv("", 12f, Pal.TEXT)
        sc.addView(logStats)
        col.addView(sc, lp(MATCH, WRAP))
        val r1 = LinearLayout(this)
        segAll = seg("Все записи", true) {
            onlyErr = false
            paintSeg(segAll, true)
            paintSeg(segErr, false)
            renderLog()
        }
        segErr = seg("Только ошибки", false) {
            onlyErr = true
            paintSeg(segAll, false)
            paintSeg(segErr, true)
            renderLog()
        }
        r1.addView(segAll, lp(0, WRAP, 1f, r = 6))
        r1.addView(segErr, lp(0, WRAP, 1f))
        col.addView(r1, lp(MATCH, WRAP, t = 10))
        val lc = card()
        lc.setPadding(dp(10), dp(10), dp(10), dp(10))
        logText = tv("", 11f, Pal.TEXT)
        logText.typeface = Typeface.MONOSPACE
        logText.setTextIsSelectable(true)
        val sv = ScrollView(this)
        sv.addView(logText)
        lc.addView(sv, lp(MATCH, MATCH))
        col.addView(lc, lp(MATCH, 0, 1f, t = 10))
        val r2 = LinearLayout(this)
        r2.addView(pill("Копировать", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { copyLog() }, lp(0, WRAP, 1f, r = 6))
        r2.addView(pill("Поделиться", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { shareLog() }, lp(0, WRAP, 1f, r = 6))
        r2.addView(pill("Очистить", tint(Pal.ERR, 0x22), Pal.ERR, 0, 12f, 9) { clearLog() }, lp(0, WRAP, 1f))
        col.addView(r2, lp(MATCH, WRAP, t = 10))
        return col
    }

    private fun buildReportTab(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(12), dp(12), dp(24))
        val sc = card()
        val nums = LinearLayout(this)
        repNumToday = bigNum(nums, "Сегодня")
        repNumHour = bigNum(nums, "За час")
        repNumSess = bigNum(nums, "За сессию")
        repNumToday.setTextColor(accent)
        sc.addView(nums)
        sc.addView(lbl("По часам сегодня (МСК)"), lp(MATCH, WRAP, t = 14))
        repHours = HoursView(this)
        repHours.barColor = accent
        sc.addView(repHours, lp(MATCH, dp(84), t = 6))
        repMeta = lbl("")
        sc.addView(repMeta, lp(MATCH, WRAP, t = 10))
        col.addView(sc, lp(MATCH, WRAP))
        val ar = LinearLayout(this)
        ar.addView(pill("Копировать", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { copyReport() }, lp(0, WRAP, 1f, r = 6))
        ar.addView(pill("Поделиться", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { shareReport() }, lp(0, WRAP, 1f, r = 6))
        ar.addView(pill("Очистить", tint(Pal.ERR, 0x22), Pal.ERR, 0, 12f, 9) { clearReport() }, lp(0, WRAP, 1f))
        col.addView(ar, lp(MATCH, WRAP, t = 12))
        val sr = LinearLayout(this)
        segToday = seg("Только сегодня", onlyToday) {
            onlyToday = true
            paintSeg(segToday, true)
            paintSeg(segEver, false)
            renderReport()
        }
        segEver = seg("За всё время", !onlyToday) {
            onlyToday = false
            paintSeg(segToday, false)
            paintSeg(segEver, true)
            renderReport()
        }
        sr.addView(segToday, lp(0, WRAP, 1f, r = 6))
        sr.addView(segEver, lp(0, WRAP, 1f))
        col.addView(sr, lp(MATCH, WRAP, t = 12))
        val lc = card()
        repText = tv("", 12f, Pal.TEXT)
        repText.setTextIsSelectable(true)
        lc.addView(repText)
        col.addView(lc, lp(MATCH, WRAP, t = 12))
        if (engine.svc.id == "rambler") {
            col.addView(
                pill("🔍  Найти «ищу от–до» на открытой анкете", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 10) { findAgeFields() },
                lp(MATCH, WRAP, t = 12)
            )
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        return scroll
    }

    private fun buildBrowserTab(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(web, lp(MATCH, 0, 1f))
        val tb = LinearLayout(this)
        tb.setBackgroundColor(Pal.SURFACE)
        tb.setPadding(dp(6), dp(6), dp(6), dp(6))
        tb.addView(pill("◀  Назад", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 9) { if (web.canGoBack()) web.goBack() }, lp(0, WRAP, 1f, r = 6))
        tb.addView(pill("⟳  Обновить", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 9) { web.reload() }, lp(0, WRAP, 1f, r = 6))
        tb.addView(pill("⌂  Домой", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 13f, 9) { web.loadUrl(engine.svc.site) }, lp(0, WRAP, 1f))
        box.addView(tb, lp(MATCH, WRAP))
        return box
    }

    private fun buildRecTab(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(12), dp(12), dp(8))
        recFilter = inp("фильтр: like, search...", "", false)
        col.addView(recFilter, lp(MATCH, WRAP))
        val noiseBtn = pill("Шум: скрыт", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 8) {}
        noiseBtn.setOnClickListener {
            hideNoise = !hideNoise
            noiseBtn.text = if (hideNoise) "Шум: скрыт" else "Шум: виден"
            renderRec()
        }
        val postBtn = pill("Все методы", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 8) {}
        postBtn.setOnClickListener {
            onlyPost = !onlyPost
            postBtn.text = if (onlyPost) "Только POST" else "Все методы"
            renderRec()
        }
        val r1 = LinearLayout(this)
        r1.addView(noiseBtn, lp(0, WRAP, 1f, r = 6))
        r1.addView(postBtn, lp(0, WRAP, 1f))
        col.addView(r1, lp(MATCH, WRAP, t = 8))
        val lc = card()
        lc.setPadding(dp(10), dp(10), dp(10), dp(10))
        recText = tv("", 11f, Pal.TEXT)
        recText.typeface = Typeface.MONOSPACE
        recText.setTextIsSelectable(true)
        val sv = ScrollView(this)
        sv.addView(recText)
        lc.addView(sv, lp(MATCH, MATCH))
        col.addView(lc, lp(MATCH, 0, 1f, t = 8))
        val r2 = LinearLayout(this)
        r2.addView(pill("Обновить", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { renderRec() }, lp(0, WRAP, 1f, r = 6))
        r2.addView(pill("Копировать", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { copyRec() }, lp(0, WRAP, 1f, r = 6))
        r2.addView(pill("Поделиться", Pal.SURFACE2, Pal.TEXT, Pal.STROKE, 12f, 9) { shareRec() }, lp(0, WRAP, 1f, r = 6))
        r2.addView(pill("Очистить", tint(Pal.ERR, 0x22), Pal.ERR, 0, 12f, 9) {
            rec.clear()
            renderRec()
        }, lp(0, WRAP, 1f))
        col.addView(r2, lp(MATCH, WRAP, t = 8))
        recFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderRec()
            }
        })
        return col
    }

    // ---------- управление ----------
    private fun stepLimit(d: Int) {
        val cur = etLimit.text.toString().trim().toIntOrNull() ?: prefs.getInt("limit", DEFAULT_LIMIT)
        etLimit.setText((cur + d).coerceIn(1, MAX_LIMIT).toString())
    }

    private fun onToggleClicked() {
        if (engine.running) {
            engine.stopLikes("остановлено")
        } else {
            onStartClicked()
        }
        updateControl()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setMessage("Выйти из аккаунта ${engine.svc.title}? Сессия и данные сайта будут удалены, лайки остановятся.")
            .setPositiveButton("Выйти") { _, _ -> clearSession() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearSession() {
        engine.clearSession()
        web.clearCache(true)
        if (webLoaded) web.loadUrl(engine.svc.site)
        refreshAutoSwitch()
        toast("Сессия очищена")
    }

    private fun refreshAutoSwitch() {
        settingAuto = true
        autoSwitch.isChecked = prefs.getBoolean("autoLogin", engine.svc.id == "rambler")
        settingAuto = false
    }

    // Выход в главное окно (если это окно открыто из уведомления, главное окно запускаем)
    private fun leave() {
        if (isTaskRoot) startActivity(Intent(this, HubActivity::class.java))
        finish()
    }

    // ---------- обновление экрана ----------
    private fun updateTop() {
        val st = engine.stateCode()
        barChip.setChip(stateLabel(st), stateColor(st))
        barSub.setIf("v$verName  ·  сегодня ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}")
    }

    private fun runTime(): String {
        if (!engine.running) return "-"
        val m = (SystemClock.elapsedRealtime() - engine.sessStart) / 60000
        return if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин"
    }

    private fun updateControl() {
        val st = engine.stateCode()
        stTitle.setIf(stateLabel(st))
        stTitle.setTextColor(stateColor(st))
        stVerdict.setIf(engine.verdict())
        val cnt = prefs.getInt("count", 0)
        val lim = prefs.getInt("limit", DEFAULT_LIMIT)
        stCount.setIf(cnt.toString())
        stLimit.setIf(" / $lim")
        stBar.frac = if (lim > 0) cnt.toFloat() / lim else 0f
        stLeft.setIf(engine.limitInfo())
        tHour.setIf(if (engine.running) engine.hourCount.toString() else "-")
        tSess.setIf(engine.sessLikes.toString())
        tQueue.setIf(engine.queue.size.toString())
        tErr.setIf(engine.errors.toString())
        tNext.setIf(engine.nextInfo())
        tRun.setIf(runTime())
        val le = engine.lastError
        if (le.isEmpty()) {
            stErr.visibility = View.GONE
        } else {
            stErr.visibility = View.VISIBLE
            stErr.setIf("Последняя ошибка: $le")
        }
        if (lastRunningUi != engine.running) {
            lastRunningUi = engine.running
            if (engine.running) {
                toggleBtn.text = "■  Остановить"
                toggleBtn.paintBtn(tint(Pal.ERR, 0x33), Pal.ERR, Pal.ERR)
            } else {
                toggleBtn.text = "▶  Запустить"
                toggleBtn.paintBtn(accent, Pal.WHITE)
            }
        }
    }

    // Вызывается движком при любом изменении (и раз в 3 секунды)
    private fun refresh() {
        updateTop()
        when (curTab) {
            0 -> updateControl()
            1 -> renderLog()
            2 -> renderReport()
        }
        if (engine.loginRestoredCount != lastRestored) {
            lastRestored = engine.loginRestoredCount
            if (webLoaded) web.reload() // служба вошла в аккаунт, обновляем страницу в окне
        }
    }

    private fun renderLog() {
        logStats.setIf(engine.logStatsText())
        if (logRenderedVer == engine.logVer && renderedOnlyErr == onlyErr) return
        logRenderedVer = engine.logVer
        renderedOnlyErr = onlyErr
        val l = (if (onlyErr) engine.logs.filter { it.contains("⚠") } else engine.logs).takeLast(200).reversed()
        if (l.isEmpty()) {
            logText.text = "Журнал пуст."
            return
        }
        val sb = SpannableStringBuilder()
        for (line in l) {
            val s0 = sb.length
            sb.append(line)
            val e0 = sb.length
            sb.append('\n')
            val c = when {
                line.contains("⚠") -> Pal.ERR
                line.contains("🔒") || line.contains("⏸") || line.contains("🌙") -> Pal.WARN
                line.contains("❤") || line.contains("✅") -> Pal.OK
                else -> 0
            }
            if (c != 0) sb.setSpan(ForegroundColorSpan(c), s0, e0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        logText.text = sb
    }

    private fun renderReport() {
        val now = System.currentTimeMillis()
        val off = 3 * 3600000L // Москва: UTC+3 без перехода на летнее время
        val dayStart = ((now + off) / 86400000L) * 86400000L - off
        val hours = IntArray(24)
        var today = 0
        var lastHour = 0
        for (r in engine.reports) {
            if (r.ts >= dayStart) {
                today++
                hours[(((r.ts + off) / 3600000L) % 24).toInt()]++
            }
            if (now - r.ts < 3600000L) lastHour++
        }
        repNumToday.setIf(today.toString())
        repNumHour.setIf(lastHour.toString())
        repNumSess.setIf(engine.sessLikes.toString())
        repMeta.setIf(
            "Счётчик приложения: ${prefs.getInt("count", 0)}/${prefs.getInt("limit", DEFAULT_LIMIT)}  ·  " +
                "темп ${engine.rateText()} в минуту  ·  ошибок за сессию: ${engine.errors}  ·  всего в отчёте: ${engine.reports.size}"
        )
        repHours.data = hours
        val last = engine.reports.lastOrNull()?.ts ?: 0L
        val key = "${engine.reports.size}|$last|$onlyToday|$dayStart"
        if (key == repKey) return
        repKey = key
        val tf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
        val list = (if (onlyToday) engine.reports.filter { it.ts >= dayStart } else engine.reports).takeLast(150).reversed()
        repText.text = if (list.isEmpty()) "Пока нет лайков." else list.joinToString("\n") { r ->
            tf.format(Date(r.ts)) + "  " + fmtProf(r.p) + "\n        " + (if (r.p.city.isEmpty()) "" else r.p.city + " · ") + r.login
        }
    }

    private fun show(i: Int) {
        val prev = curTab
        curTab = i
        for ((k, p) in panels.withIndex()) p.visibility = if (k == i) View.VISIBLE else View.GONE
        for (k in navIcons.indices) {
            val on = k == i
            navIcons[k].alpha = if (on) 1f else 0.45f
            navLabels[k].setTextColor(if (on) accent else Pal.MUTED)
        }
        if (i == 3) {
            // экран не гаснет только пока открыт браузер (вход, капча)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!webLoaded) {
                webLoaded = true
                web.loadUrl(engine.svc.site)
            }
            web.onResume()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (prev == 3 && webLoaded) web.onPause()
        }
        updateTop()
        when (i) {
            0 -> updateControl()
            1 -> renderLog()
            2 -> renderReport()
            4 -> renderRec()
        }
    }

    // ---------- перенесено без изменений: запросы, сессия, настройки, фон, отчёт ----------
    private fun onStartClicked() {
        if (!saveSettings(false)) return
        if (!engine.startLikes()) toast("Уже работает")
        askBackgroundOnce()
    }

    private fun askBackgroundOnce() {
        if (!engine.batteryOk() && !prefs.getBoolean("batAsked", false)) {
            prefs.edit().putBoolean("batAsked", true).apply()
            askBackground(false)
        }
    }

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
        // OK: знакомства открываются во вложенном окне (iframe), поэтому запись запросов ставим во все окна страницы
        if (engine.svc.id == "ok") {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, setOf("*"))
                } else {
                    engine.logMsg("⚠ Эта версия WebView не умеет писать запросы внутри вложенных окон. Обнови «Android System WebView» в Google Play")
                }
            } catch (e: Exception) {
                engine.logMsg("⚠ Не удалось включить запись во вложенных окнах: ${e.message}")
            }
        }
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
    }

    // OK: если в «Браузере» поставлен лайк вручную, запоминаем его адрес и параметры (user_id -> {login}, meta -> {meta})
    private fun learnOkLike(o: JSONObject) {
        try {
            val u = o.optString("u")
            val i = u.indexOf("okdating-app.ru/api/")
            if (i < 0) return
            val m = u.substring(i + 20).substringBefore("?")
            val ml = m.lowercase()
            if (!ml.startsWith("dating.") || !(ml.contains("like") || ml.contains("react") || ml.contains("vote"))) return
            if (listOf("dislike", "unlike", "get", "list", "super", "skip", "remove", "revoke", "delete", "count").any { ml.contains(it) }) return
            val p = o.optString("p")
            if (!p.startsWith("FormData: ") || !p.contains("user_id=")) return
            val parts = ArrayList<String>()
            for (kv in p.removePrefix("FormData: ").split("&")) {
                val k = kv.substringBefore("=")
                val v = kv.substringAfter("=", "")
                if (k.isEmpty() || k.startsWith("_")) continue
                when {
                    k == "user_id" -> parts.add("user_id={login}")
                    k == "meta" -> parts.add("meta={meta}")
                    v.contains("...[len") -> continue
                    else -> parts.add("$k=$v")
                }
            }
            if (!parts.contains("user_id={login}")) return
            val url = "https://okdating-app.ru/api/$m"
            val body = parts.joinToString("&")
            if (prefs.getString("likeUrl", "") == url && prefs.getString("likeBody", "") == body) return
            prefs.edit().putString("likeUrl", url).putString("likeMethod", "POST").putString("likeBody", body).apply()
            runOnUiThread {
                etLikeUrl.setText(url)
                etLikeMethod.setText("POST")
                etLikeBody.setText(body)
                engine.logMsg("🔎 OK: запомнил адрес лайка из твоего лайка: $m, тело: $body")
            }
        } catch (e: Exception) {
        }
    }

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
                if (engine.svc.id == "ok") learnOkLike(o)
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
                if (webLoaded) web.reload()
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

    // Всё нужное для разбора одним куском: статус, лимиты, настройки, последние запросы и ответы службы, запись из «Браузера», журнал
    private fun copyDiag() {
        try {
            val sb = StringBuilder()
            sb.append("=== Диагностика ${engine.svc.title} v$verName ===\n")
            sb.append(engine.statusText()).append("\n")
            sb.append("лимит приложения: ${prefs.getInt("limit", DEFAULT_LIMIT)}, окно ${prefs.getInt("winFrom", DEFAULT_WIN_FROM)}–${prefs.getInt("winTo", DEFAULT_WIN_TO)}, темп ${engine.rateText()}\n")
            sb.append("адрес лайка: ${prefs.getString("likeUrl", engine.svc.defLikeUrl)} ${prefs.getString("likeBody", engine.svc.defLikeBody)}\n")
            sb.append("адрес ленты: ${prefs.getString("searchUrl", engine.svc.defSearchUrl)}\n")
            sb.append("\n=== Запросы службы (последние) ===\n").append(engine.trace.takeLast(25).joinToString("\n")).append("\n")
            val recs = rec.filter { it.optString("u").contains("okdating") || it.optString("m") == "CLICK" }.takeLast(14)
            if (engine.svc.id != "ok" || recs.isNotEmpty()) {
                sb.append("\n=== Запись из «Браузера» ===\n")
                for (o in recs) {
                    sb.append(o.optString("t")).append(" ").append(o.optString("m")).append(" ").append(o.optString("u").takeLast(60))
                        .append(" -> ").append(o.optInt("s")).append("\n  ").append(o.optString("p").take(250)).append("\n  ")
                        .append(o.optString("r").take(1200)).append("\n")
                }
            }
            sb.append("\n=== Журнал (последние 60) ===\n").append(engine.logs.takeLast(60).joinToString("\n"))
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("diag", sb.toString()))
            toast("Диагностика скопирована, вставь её мне в чат")
        } catch (e: Exception) {
            toast("Не получилось: ${e.message}")
        }
    }

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
            c.put("r", c.optString("r").take(if (c.optString("u").contains("okdating")) 3000 else 700))
            val hd = c.optString("h")
            if (hd.isNotEmpty()) {
                c.put("h", hd.split("\n").joinToString("\n") { ln ->
                    val i = ln.indexOf(": ")
                    if (i > 0 && ln.length - i - 2 > 24) ln.substring(0, i + 2 + 10) + "…[длина " + (ln.length - i - 2) + "]" else ln
                })
            }
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

    override fun onDestroy() {
        engine.onChange = null
        try {
            web.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------- жизненный цикл ----------
    override fun onResume() {
        super.onResume()
        engine.onChange = { refresh() }
        refresh()
        if (curTab == 3 && webLoaded) web.onResume()
    }

    override fun onPause() {
        super.onPause()
        engine.onChange = null
        if (engine.sessionGood) engine.saveCookies()
        CookieManager.getInstance().flush()
        if (webLoaded) web.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (curTab == 3 && webLoaded && web.canGoBack()) {
            web.goBack()
        } else if (curTab != 0) {
            show(0)
        } else {
            leave()
        }
    }
}
