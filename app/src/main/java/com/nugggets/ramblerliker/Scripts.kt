package com.nugggets.ramblerliker

import java.util.Date

// Скрипты, которые приложение вставляет в страницы сайтов (перехват ответов, нажатия кнопок, автовход)

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
  limits:function(url,body){
   ensure().then(function(){return F(url,opts('POST',body))})
    .then(function(r){return r.text()}).then(function(t){Rec.apiLimits(t)}).catch(function(e){Rec.apiErr('limits',String(e))});
  },
  like:function(login,url,method,body){
   ensure().then(function(){return F(url,opts(method,body))})
    .then(function(r){var st=r.status;return r.text().then(function(t){Rec.apiLike2(login,st,t)})}).catch(function(e){Rec.apiErr('like',String(e))});
  }
 };
})();
"""

// Love.ru: лайк ставится нажатием на кнопку «Симпатия» самой страницы (она сама шлёт ?action=sympathy-send и подгружает следующих).
// Ответ сайта на sympathy-send перехватываем и отдаём движку как ответ на лайк
const val LOVE_JS = """
(function(){
 if(!window.__lk) return;
 // скрытое окно не рисует кадры: скрипты страницы, ждущие requestAnimationFrame или видимость вкладки, зависают после первого лайка
 try{
  if(!window.__rafPoly){
   window.__rafPoly=1;
   window.requestAnimationFrame=function(cb){ return setTimeout(function(){ try{ cb(Date.now()); }catch(e){} },16); };
   window.cancelAnimationFrame=function(id){ clearTimeout(id); };
   try{ Object.defineProperty(document,'hidden',{get:function(){ return false; },configurable:true}); }catch(e){}
   try{ Object.defineProperty(document,'visibilityState',{get:function(){ return 'visible'; },configurable:true}); }catch(e){}
  }
 }catch(e){}
 var pend=null, tok=0;
 function info(){
  var t=''; try{ var mn=document.querySelector('#main'); t=((mn&&(mn.innerText||mn.textContent))||'').replace(/\s+/g,' ').trim(); }catch(e){}
  var m=/^(.{1,40}?)\s+(\d{2})\s*(?:лет|год|года)\s*,?\s*([^\d%]{0,40})/.exec(t);
  return m?{name:m[1],age:m[2],city:m[3].trim()}:{name:t.slice(0,30),age:'',city:''};
 }
 function done(body,status,text){
  if(!pend) return;
  var p=pend; pend=null; clearTimeout(p.t);
  var m=/login_to=([^&]+)/.exec(body); var login=m?decodeURIComponent(m[1]):p.tok;
  var o; try{ o=JSON.parse(text); }catch(e){ Rec.apiLike2(login,status,String(text).slice(0,600)); return; }
  o._name=p.name; o._age=p.age; o._city=p.city;
  Rec.apiLike2(login,status,JSON.stringify(o));
 }
 function hook(){
  if(window.__loveHook) return; window.__loveHook=1;
  var oF=window.fetch;
  if(oF){ window.fetch=function(i,init){
    var url=(typeof i==='string')?i:((i&&i.url)||''); var b=(init&&init.body)||'';
    var p=oF.apply(this,arguments);
    if(String(url).indexOf('sympathy-send')>=0){ window.__sent=(window.__sent||0)+1; p.then(function(r){ try{ r.clone().text().then(function(t){ done(String(b),r.status,t); }); }catch(e){} }).catch(function(){}); }
    return p; }; }
  var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(m,u){ this.__lu=String(u||''); try{ var q=(this.__lu.match(/action=([\w-]+)/)||[])[1]; if(q){ (window.__lx=window.__lx||[]).push(q); if(window.__lx.length>8) window.__lx.shift(); } }catch(e){} return xo.apply(this,arguments); };
  XMLHttpRequest.prototype.send=function(b){ var x=this;
   if((x.__lu||'').indexOf('sympathy-send')>=0){ window.__sent=(window.__sent||0)+1; x.addEventListener('loadend',function(){ var t=''; try{t=x.responseText||'';}catch(e){} done(String(b||''),x.status,t); }); }
   return xs.apply(this,arguments); };
 }
 function closeWins(){ try{ var l=document.querySelectorAll('a.close_window, .win_closeable a.top_back, .win_closable a.top_back'); for(var i=0;i<l.length;i++){ l[i].click(); } }catch(e){} }
 function snap(){
  try{
   var c=document.querySelector('#sympathy_flow_btn_container'); var b=document.querySelector('#sympathy_btn_send'); var w=document.querySelectorAll('.win'); var wt='';
   for(var i=0;i<w.length&&i<2;i++){ wt+=(w[i].id||'')+':'+String(w[i].innerText||w[i].textContent||'').replace(/\s+/g,' ').slice(0,50)+' | '; }
   var sy='', fn='';
   try{ if(window.sympathy){ var pk=[], fk=[]; for(var k in window.sympathy){ var v=window.sympathy[k]; if(typeof v==='function'){ fk.push(k); continue; } pk.push(k+'='+(v===null?'null':(typeof v==='object'?'obj':String(v).slice(0,16)))); } sy=pk.slice(0,25).join(','); fn=fk.slice(0,30).join(','); } else { sy='нет объекта sympathy'; } }catch(e){ sy='sympathy: '+e; }
   var src='';
   try{ if(window.sympathy&&!window.__srcDumped){ window.__srcDumped=1; var nm=['sendButtonClick','sendSympathy','sympathySendSuccessful']; for(var q=0;q<nm.length;q++){ var fq=window.sympathy[nm[q]]; if(typeof fq==='function'){ src+=' || '+nm[q]+': '+String(fq).replace(/\s+/g,' ').slice(0,700); } } } }catch(e){}
   return 'контейнер: '+(c?c.className:'нет')+', кнопка: '+(b?(b.disabled?'выключена':'есть'):'нет')+', окон: '+w.length+' '+wt+'html: '+document.documentElement.className+', запросы: '+(window.__lx||[]).join(',')+', hidden: '+document.hidden+', поля sympathy: '+sy+', методы sympathy: '+fn+src;
  }catch(e){ return 'снимок: '+e; }
 }
 window.__lk.boot=function(){
  try{
   hook();
   var b=document.querySelector('#sympathy_btn_send');
   if(b){ Rec.apiBatch(JSON.stringify({love:true,ids:['L'+Date.now()+'_'+(tok++)]})); return; }
   var t=((document.body&&(document.body.innerText||document.body.textContent))||'').replace(/\s+/g,' ').trim().slice(0,300);
   Rec.apiBatch(JSON.stringify({love:true,ids:[],stub:t||'(пустая страница)'}));
  }catch(e){ Rec.apiErr('boot',String(e)); }
 };
 window.__lk.like=function(login,url,method,body){
  try{
   hook();
   var b=document.querySelector('#sympathy_btn_send');
   if(!b){ Rec.apiErr('notready-like',login); return; }
   // пока сайт обрабатывает прошлую оценку, блок кнопки помечен «disabled» и нажатия игнорируются: ждём до 5 с
   var c=document.querySelector('#sympathy_flow_btn_container');
   if(c&&String(c.className).indexOf('disabled')>=0&&(window.__lw||0)<15){ window.__lw=(window.__lw||0)+1; setTimeout(function(){ window.__lk.like(login,url,method,body); },200); return; }
   // 3 секунды кнопка так и не включилась: снимаем блокировку сами
   if(c&&String(c.className).indexOf('disabled')>=0){ try{ c.classList.remove('disabled'); }catch(e){} }
   window.__lw=0;
   if(window.__loveClose!==false) closeWins();
   var inf=info();
   pend={tok:login,name:inf.name,age:inf.age,city:inf.city,t:setTimeout(function(){ if(pend){ pend=null; Rec.apiErr('like','сайт не ответил на нажатие «Симпатия» за 15 с | '+snap()); } },15000)};
   var s0=window.__sent||0;
   b.click();
   setTimeout(function(){
    if(pend&&(window.__sent||0)===s0){ var pp=pend; pend=null; clearTimeout(pp.t); Rec.apiErr('clickignored',snap()); }
   },3000);
  }catch(e){ Rec.apiErr('like',String(e)); }
 };
})();
"""

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

// Beboo: анкеты берём из поиска с фильтрами (ссылки на анкеты), потом открываем анкету и нажимаем на ней кнопку лайка.
// Страницу сайта не знаем заранее, поэтому ссылки и кнопка ищутся по признакам; при неудаче в журнал пишется снимок страницы.
// Селекторы можно задать вручную в «Адреса и подсказки»
const val BEBOO_JS = """
(function(){
 if(!window.__lk) return;
 var NAV=/^\/(search|meets|messeger|messages|chat|agreement|switcher|settings|login|logout|registration|register|help|faq|rules|payment|pay|vip|gifts|visitors|favorites|blacklist|support|privacy|policy|cookie|about|contacts|partners|app|apps|download|rating|top|news|blog|forum|paid|buy)(\/|$)/i;
 var BADURL=/(mc\.yandex|google|doubleclick|metrika|counter|analytics|top-fwz|tns-counter|gstatic|facebook|vk\.com\/rtrg|adriver|adfox)/i;
 var LIMIT=/(\/paid\/|лимит|закончил|исчерпан|превышен|только для vip|только для вип|купите vip|оформите vip)/i;
 function vis(e){ try{ var r=e.getBoundingClientRect(); var cs=getComputedStyle(e); return r.width>0&&r.height>0&&cs.visibility!=='hidden'&&cs.display!=='none'; }catch(x){ return false; } }
 function txt(e){ return ((e.tagName==='INPUT')?(e.value||''):(e.innerText||e.textContent||'')).replace(/\s+/g,' ').trim(); }
 function body(){ return ((document.body&&(document.body.innerText||document.body.textContent))||'').replace(/\s+/g,' ').trim(); }
 function absU(a){ try{ return new URL(a.getAttribute('href'),location.href); }catch(e){ return null; } }
 function fire(e){
  try{ e.scrollIntoView({block:'center'}); }catch(x){}
  var r=e.getBoundingClientRect(), cx=r.left+r.width/2, cy=r.top+r.height/2;
  var o={bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy};
  ['pointerdown','mousedown','pointerup','mouseup'].forEach(function(n){
   try{ e.dispatchEvent(n.indexOf('pointer')===0 ? new PointerEvent(n,o) : new MouseEvent(n,o)); }catch(z){}
  });
  e.click();
 }
 function anchorOf(el){
  if(!el) return null;
  if(el.tagName==='A'&&el.getAttribute('href')) return el;
  try{ var c=el.closest('a[href]'); if(c) return c; }catch(e){}
  try{ return el.querySelector('a[href]'); }catch(e){ return null; }
 }
 function shapeOf(u){ return u.pathname.replace(/\d+/g,'N'); }
 // ссылки на анкеты: свой селектор, иначе самая многочисленная группа однотипных ссылок (одинаковый вид пути)
 function links(sel){
  var out=[], dup={}, groups={}, order=[];
  function put(a){
   var u=absU(a); if(!u||u.host!==location.host) return null;
   var k=u.pathname+u.search; if(dup[k]) return null; dup[k]=1; out.push(k); return k;
  }
  if(sel){
   try{ var els=document.querySelectorAll(sel); for(var i=0;i<els.length;i++){ var a=anchorOf(els[i]); if(a) put(a); } }catch(e){}
   if(out.length) return {ids:out,diag:'селектор «'+sel+'»'};
  }
  var as=document.querySelectorAll('a[href]');
  for(var j=0;j<as.length;j++){
   var a2=as[j]; var h=a2.getAttribute('href')||'';
   if(!h||h.charAt(0)==='#'||/^(javascript|mailto|tel):/i.test(h)) continue;
   var u2=absU(a2); if(!u2||u2.host!==location.host) continue;
   var p=u2.pathname; if(p==='/'||p===''||NAV.test(p)) continue;
   try{ if(a2.closest('nav,header,footer,[class*=header],[class*=footer],[class*=menu],[class*=navbar]')) continue; }catch(e){}
   var sh=shapeOf(u2); var g=groups[sh];
   if(!g){ g=groups[sh]={n:0,keys:{},img:0,sample:u2.pathname+u2.search,list:[]}; order.push(sh); }
   var key=u2.pathname+u2.search; if(g.keys[key]) continue; g.keys[key]=1; g.n++; g.list.push(a2);
   if(a2.querySelector('img')||(a2.style&&a2.style.backgroundImage)) g.img++;
  }
  var best=null,bs=-1;
  for(var q=0;q<order.length;q++){ var gg=groups[order[q]]; var sc=gg.n+gg.img*2+(order[q].indexOf('N')>=0?3:0); if(sc>bs){ bs=sc; best=order[q]; } }
  var diag=order.map(function(s){ return s+' ×'+groups[s].n+(groups[s].img?' (с фото '+groups[s].img+')':''); }).sort().slice(0,6).join(' ; ');
  if(!best) return {ids:[],diag:diag||'ссылок нет'};
  var bg=groups[best]; if(bg.n<2&&best.indexOf('N')<0) return {ids:[],diag:diag};
  for(var z=0;z<bg.list.length;z++) put(bg.list[z]);
  return {ids:out,diag:'группа '+best+'; '+diag};
 }
 window.__lk.boot=function(sel){
  try{
   var r=links(sel);
   var stub=''; if(!r.ids.length) stub=body().slice(0,300)||'(пустая страница)';
   Rec.apiBatch(JSON.stringify({beboo:true,onSearch:/\/search/i.test(location.pathname),ids:r.ids,count:r.ids.length,stub:stub,diag:r.diag,url:location.href}));
  }catch(e){ Rec.apiErr('boot',String(e)); }
 };
 // подгрузить следующие анкеты: кнопка «Ещё»/«Далее»/следующая страница, иначе прокрутка вниз
 window.__lk.more=function(){
  try{
   var done='';
   var cand=document.querySelectorAll('a[rel=next],[class*=next],[class*=more],[class*=Next],[class*=More],[class*=pager] a,[class*=pagin] a,button,a[href]');
   var re=/^(ещё|еще|показать ещё|показать еще|загрузить ещё|загрузить еще|далее|следующая|вперёд|вперед|»|›|>|more|next)(\s|$)/i;
   for(var i=0;i<cand.length&&!done;i++){
    var e=cand[i]; if(!vis(e)) continue;
    var t=txt(e); var rel=(e.getAttribute('rel')||'');
    if(rel==='next'||(t.length<=25&&re.test(t))){ done=t||rel; try{ fire(e); }catch(x){ try{ e.click(); }catch(y){} } }
   }
   try{ window.scrollTo(0,document.documentElement.scrollHeight); window.dispatchEvent(new Event('scroll')); }catch(e){}
  }catch(e){}
 };
 function hookNet(){
  if(window.__bhook) return; window.__bhook=1; window.__bcap={on:false,list:[]};
  function rec(url,method){ if(!window.__bcap.on||BADURL.test(String(url))) return null; var o={url:String(url),method:method||'GET',status:0,text:null}; window.__bcap.list.push(o); return o; }
  var oF=window.fetch;
  if(oF){ window.fetch=function(i,init){
    var url=(typeof i==='string')?i:((i&&i.url)||''); var o=rec(url,(init&&init.method)||(i&&i.method)||'GET');
    var p=oF.apply(this,arguments);
    if(o){ p.then(function(r){ o.status=r.status; try{ r.clone().text().then(function(t){ o.text=String(t).slice(0,600); }); }catch(e){ o.text=''; } }).catch(function(){ o.status=-1; o.text=''; }); }
    return p; }; }
  var xo=XMLHttpRequest.prototype.open, xs=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(m,u){ this.__bm=m; this.__bu=u; return xo.apply(this,arguments); };
  XMLHttpRequest.prototype.send=function(b){ var x=this; var o=rec(x.__bu||'',x.__bm);
   if(o){ x.addEventListener('loadend',function(){ o.status=x.status; var t=''; try{ t=x.responseText||''; }catch(e){} o.text=String(t).slice(0,600); }); }
   return xs.apply(this,arguments); };
 }
 function attrs(e){
  var s=(e.className&&e.className.baseVal!==undefined?e.className.baseVal:e.className)+' '+(e.id||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('href')||'')+' '+(e.getAttribute('onclick')||'');
  try{ for(var i=0;i<e.attributes.length;i++){ var n=e.attributes[i].name; if(n.indexOf('data-')===0) s+=' '+n+'='+e.attributes[i].value; } }catch(x){}
  return String(s).toLowerCase();
 }
 var POS=/(like|heart|sympath|симпат|нравит|нравл|лайк|оцен|rate|vote|♥|❤|♡)/i;
 var NEG=/(dislike|unlike|не нравит|не нравл|снять|убрать|удалить|пропустить|skip|nope|report|жалоб|блок|block|share|поделить)/i;
 var DONE=/(^|[\s_-])(active|liked|checked|selected|done|pressed|on|voted)([\s_-]|$)/i;
 function findBtn(sel){
  if(sel){
   try{ var l=document.querySelectorAll(sel); for(var i=0;i<l.length;i++){ if(vis(l[i])) return {el:l[i],liked:DONE.test(attrs(l[i]))||l[i].getAttribute('aria-pressed')==='true',how:'селектор'}; } }catch(e){}
   return {el:null};
  }
  var cs=document.querySelectorAll('button,a,[role=button],[onclick],input[type=button],input[type=submit],[class*=like],[class*=Like],[class*=heart],[class*=sympath],[data-action],[data-like]');
  var best=null,bs=0;
  for(var j=0;j<cs.length;j++){
   var e=cs[j]; if(!vis(e)) continue;
   try{ if(e.closest('nav,header,footer')) continue; }catch(x){}
   if(e.tagName==='A'){ var uu=absU(e); if(uu&&(NAV.test(uu.pathname)||uu.pathname==='/')) continue; }
   var t=txt(e); if(t.length>40) continue;
   var a=attrs(e), all=(t+' '+a);
   if(NEG.test(all)) continue;
   var sc=0;
   if(POS.test(t)) sc+=6;
   if(POS.test(a)) sc+=4;
   if(sc===0) continue;
   var r=e.getBoundingClientRect(); sc-=Math.min(3,(r.width*r.height)/40000);
   if(e.tagName==='BUTTON'||e.tagName==='A'||e.getAttribute('role')==='button') sc+=1;
   if(sc>bs){ bs=sc; best=e; }
  }
  if(!best) return {el:null};
  return {el:best,liked:DONE.test(attrs(best))||best.getAttribute('aria-pressed')==='true'||best.disabled===true,how:'признаки'};
 }
 function info(){
  var n=''; try{ var h=document.querySelector('h1,[class*=userName],[class*=user-name],[class*=name],[class*=Name]'); n=h?txt(h):''; }catch(e){}
  if(!n) n=(document.title||'').split(/[-|—,]/)[0].trim();
  var t=body(); var m=/(\d{2})\s*(?:лет|год|года)/.exec(t); var c='';
  var cm=/(?:лет|год|года)\s*,?\s*([А-ЯЁA-Z][^\d,|]{2,30})/.exec(t); if(cm) c=cm[1].trim();
  var ma=/^(.*?),\s*(\d{2})$/.exec(n);
  if(ma){ n=ma[1]; if(!m) m=[0,ma[2]]; }
  return {name:n.slice(0,30),age:m?m[1]:'',city:c.slice(0,30)};
 }
 function snap(sel){
  try{
   var cs=document.querySelectorAll('button,a,[role=button],[onclick]'), out=[];
   for(var i=0;i<cs.length&&out.length<14;i++){ var e=cs[i]; if(!vis(e)) continue; var t=txt(e).slice(0,18); var c=String(e.className&&e.className.baseVal!==undefined?e.className.baseVal:e.className).slice(0,30); out.push(e.tagName.toLowerCase()+(c?'.'+c:'')+(t?'«'+t+'»':'')); }
   return 'url: '+location.pathname+location.search+' | заголовок: '+(document.title||'').slice(0,40)+' | кнопки: '+out.join(' ; ')+' | текст: '+body().slice(0,160);
  }catch(e){ return 'снимок: '+e; }
 }
 window.__lk.like=function(login,url,method,sel){
  try{
   hookNet();
   var pr=info(); var closed=false;
   function fin(obj,http){ if(closed) return; closed=true; window.__bcap.on=false; obj._name=pr.name; obj._age=pr.age; obj._city=pr.city; Rec.apiLike2(login,http||200,JSON.stringify(obj)); }
   if(/\/(login|auth|enter|signin|registration|reg)(\/|\?|$)/i.test(location.pathname)){ fin({status:0,auth:true,msg:'страница входа'},401); return; }
   var tries=0;
   (function find(){
    if(/^\/paid\//.test(location.pathname)){ fin({status:0,limit:true,msg:'VIP-страница вместо анкеты: '+location.pathname}); return; }
    var b=findBtn(sel);
    if(!b.el){
     if(++tries<20){ setTimeout(find,300); return; }
     var bt=body();
     if(LIMIT.test(bt.slice(0,400))){ fin({status:0,limit:true,msg:bt.slice(0,200)}); return; }
     Rec.apiErr('like','не нашёл кнопку лайка на странице анкеты | '+snap(sel)); return;
    }
    if(b.liked){ Rec.apiErr('skip',login+' ('+(txt(b.el)||attrs(b.el).slice(0,30))+')'); return; }
    var before=attrs(b.el), el=b.el, a=(el.tagName==='A')?el:null;
    window.__bcap.list=[]; window.__bcap.on=true;
    var href=a?(a.getAttribute('href')||''):'';
    var plain=a&&href&&href.charAt(0)!=='#'&&!/^javascript:/i.test(href)&&!a.getAttribute('onclick')&&/(like|sympath|accept|vote)/i.test(href);
    if(plain){
     // обычная ссылка-лайк: идём по ней сами, чтобы страница не уходила на другой адрес
     var u=absU(a);
     (window.__origFetch||window.fetch).call(window,u.href,{credentials:'include',headers:{'X-Requested-With':'XMLHttpRequest'}})
      .then(function(r){ var st=r.status; return r.text().then(function(t){
        var s=String(t).slice(0,600);
        if(st===401){ fin({status:0,auth:true,msg:s},401); return; }
        if(LIMIT.test(s)){ fin({status:0,limit:true,msg:s}); return; }
        if(s.trim().charAt(0)==='<'&&s.length>300){ fin({status:0,http:st,how:'ссылка вернула страницу, а не лайк',text:s.slice(0,100)}); return; }
        fin({status:(st>=200&&st<300)?1:0,http:st,how:'ссылка '+u.pathname,text:s.slice(0,200)}); }); })
      .catch(function(e){ Rec.apiErr('like','ссылка лайка не открылась: '+e); });
     return;
    }
    fire(el);
    var t0=Date.now();
    (function wait(){
     var L=window.__bcap.list, got=null;
     for(var i=0;i<L.length;i++){ if(L[i].status!==0&&L[i].text!==null){ got=L[i]; break; } }
     if(got){
      if(got.status===401){ fin({status:0,auth:true,msg:got.text},401); return; }
      if(LIMIT.test(got.text||'')){ fin({status:0,limit:true,msg:got.text}); return; }
      fin({status:(got.status>=200&&got.status<300)?1:0,http:got.status,how:got.method+' '+got.url.slice(0,60),text:(got.text||'').slice(0,200)}); return;
     }
     if(/^\/paid\//.test(location.pathname)){ fin({status:0,limit:true,msg:'VIP-страница после лайка: '+location.pathname}); return; }
     var after=''; try{ after=attrs(el); }catch(e){}
     var gone=!document.body.contains(el)||!vis(el);
     if(Date.now()-t0>1200&&L.length===0&&(after!==before||gone||el.disabled)){ fin({status:1,how:'по состоянию кнопки',text:(after!==before?'класс изменился':'кнопка исчезла')}); return; }
     var dlg=''; try{ var d=document.querySelector('[role=dialog],[class*=modal],[class*=popup],[class*=Modal],[class*=Popup]'); if(d&&vis(d)) dlg=txt(d); }catch(e){}
     if(dlg&&LIMIT.test(dlg)){ fin({status:0,limit:true,msg:dlg.slice(0,200)}); return; }
     if(Date.now()-t0>5000){
      if(L.length>0){ fin({status:1,how:'запрос без ответа: '+L[0].url.slice(0,60),text:''}); return; }
      window.__bcap.on=false; closed=true; Rec.apiErr('like','нажал «'+(txt(el)||attrs(el).slice(0,40))+'», но сайт не ответил и вид не изменился | '+snap(sel)); return;
     }
     setTimeout(wait,200);
    })();
   })();
  }catch(e){ Rec.apiErr('like',String(e)); }
 };
})();
"""

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
