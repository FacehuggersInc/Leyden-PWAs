package org.bond.pwaapp

import org.json.JSONArray
import org.json.JSONObject

object Runtime {
    private val CORE = """
(function(){
if (window.__pwaRT) return;
var T='@TOKEN@', B=window.PwaApp, items=@ITEMS@, top=(window.top===window);
var fns={}, done={}, count={}, waiting={}, busy={}, watch={}, watchers=[], timers=[], started=false;
function sheet(id,css){try{var map=window.__pwaSheets||(window.__pwaSheets={});var sh=map[id];if(!sh){sh=new CSSStyleSheet();map[id]=sh;}sh.replaceSync(css);if(document.adoptedStyleSheets.indexOf(sh)<0)document.adoptedStyleSheets=document.adoptedStyleSheets.concat([sh]);return;}catch(e){}
var el=document.getElementById(id);if(!el){el=document.createElement('style');el.id=id;(document.head||document.documentElement).appendChild(el);}el.textContent=css;}
function match(p,path){if(!p||p==='*'||p==='/')return true;if(p.indexOf('*')<0)return path.indexOf(p)===0;var r=new RegExp('^'+p.split('*').map(function(x){return x.replace(/[.+?^{}()|[\]\\]/g,'\\$&')}).join('.*'));return r.test(path);}
function words(e){return (e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\s+/g,' ').trim();}
function said(e,m,v){var w=words(e);return m==='^'?w.indexOf(v)===0:m==='*'?w.indexOf(v)>=0:w===v;}
function findAll(sel){if(!sel)return [];var t=/^text([*^]?):([a-z0-9*-]+)\|([\s\S]*)$/i.exec(sel);if(t){var list=[].slice.call(document.querySelectorAll(t[2]));return list.filter(function(e){return said(e,t[1],t[3]);});}try{return [].slice.call(document.querySelectorAll(sel));}catch(e){return [];}}
function find(sel){return findAll(sel)[0]||null;}
function waitFor(test,upTo){return new Promise(function(ok){var v=test();if(v||!upTo)return ok(v||null);var tm=null;var ob=new MutationObserver(function(){var r=test();if(r){ob.disconnect();if(tm)clearTimeout(tm);ok(r);}});
ob.observe(document.documentElement||document,{childList:true,subtree:true,attributes:true});if(upTo>0)tm=setTimeout(function(){ob.disconnect();ok(test()||null);},upTo);});}
function docs(css){if(css.indexOf('/*pwa-doc ')<0)return css;return css.replace(/\/\*pwa-doc ([\s\S]*?) \*\/([\s\S]*?)\/\*pwa-end\*\//g,function(all,rules,body){var href=location.href,host=location.hostname,okay=false;
rules.replace(/(url-prefix|url|domain|regexp)\(\s*['"]?([^'")]*)['"]?\s*\)/g,function(x,k,v){if(k==='url'&&href===v)okay=true;if(k==='url-prefix'&&href.indexOf(v)===0)okay=true;if(k==='domain'&&(host===v||host.slice(-v.length-1)==='.'+v))okay=true;if(k==='regexp'){try{if(new RegExp('^'+v+'$').test(href))okay=true;}catch(e){}}return x;});return okay?body:'';});}
var H={find:find,findAll:findAll,waitFor:waitFor,sheet:sheet,words:words};
function timeOk(it){if(!it.from||!it.to)return true;function mm(s){var a=s.split(':');return (+a[0])*60+(+a[1]||0);}var d=new Date(),n=d.getHours()*60+d.getMinutes(),f=mm(it.from),t=mm(it.to);return f<=t?(n>=f&&n<=t):(n>=f||n<=t);}
function fail(it,e){var line=0;try{var s=String(e&&e.stack||'');var m=/<anonymous>:(\d+)/.exec(s)||/:(\d+):\d+\)?\s*$/m.exec(s);if(m)line=+m[1];}catch(x){}say(it,'failed',String(e&&e.message||e)+(line>1?' · line '+(line-1):''));try{B&&B.failed(T,it.id,String(e&&e.message||e),line);}catch(x){}}
function reason(it){if(!top&&!it.frames)return 'only runs in the main page, not frames';if(it.path&&!match(it.path,location.pathname))return 'this page isn’t '+it.path;if(it.net&&('|'+RT.net+'|').indexOf('|'+it.net+'|')<0)return 'not on the network it needs';if(!timeOk(it))return 'outside '+it.from+'–'+it.to;
if(it.testing)return '';if(it.most&&(count[it.id]||0)>=it.most)return 'already ran '+it.most+' times on this page';if(it.often==='page'&&done[it.id])return 'already ran on this page';
if(it.often==='visit'){try{if(sessionStorage.getItem('__pwa_v_'+it.id))return 'already ran this visit';}catch(x){}}if(it.often==='day'){try{if(B.ranToday(T,it.id))return 'already ran today';}catch(x){}}return '';}
function allowed(it){return !reason(it);}
function say(it,state,text){if(!watch[it.id])return;try{B.status(T,it.id,state,String(text));}catch(x){}}
function nameOf(id){for(var i=0;i<items.length;i++)if(items[i].id===id)return items[i].name||'untitled';return 'another insertion';}
function helper(it){var h={};for(var k in H)h[k]=H[k];h.say=function(t,s){say(it,s||'note',t);};return h;}
function finish(it){say(it,'done',it.css!=null?'style applied':'finished');done[it.id]=1;count[it.id]=(count[it.id]||0)+1;if(it.often==='visit'){try{sessionStorage.setItem('__pwa_v_'+it.id,'1');}catch(x){}}if(it.often==='day'){try{B.ran(T,it.id);}catch(x){}}
var w=waiting[it.id];if(w){delete waiting[it.id];w.forEach(function(f){f();});}}
function run(it,why,el){if(RT.off){say(it,'skipped','insertions are off');return;}var no=reason(it);if(no){say(it,'skipped',no);return;}
if(it.after&&!done[it.after]){say(it,'waiting','waits for “'+nameOf(it.after)+'” to run first');(waiting[it.after]=waiting[it.after]||[]).push(function(){run(it,why,el);});return;}if(busy[it.id]){say(it,'busy','already running');return;}
var act=function(target){busy[it.id]=1;say(it,'running',why==='test'?'running it now':'started by '+why);try{if(it.css!=null){sheet('__pwa_'+it.id,docs(it.css));}else{var f=fns[it.id];if(!f){busy[it.id]=0;say(it,'failed','its code didn’t load, so there’s nothing to run');return;}var r=f(gm(it),helper(it),target||null);
if(r&&typeof r.then==='function'){r.then(function(){busy[it.id]=0;finish(it);},function(e){busy[it.id]=0;fail(it,e);});return;}}busy[it.id]=0;finish(it);}catch(e){busy[it.id]=0;fail(it,e);}};
var go=function(target){if(it.pause){say(it,'waiting','pausing '+it.pause+' ms first');setTimeout(function(){act(target);},it.pause);}else act(target);};
if(it.exists){if(!find(it.exists))say(it,'waiting','waiting for '+it.exists+' to exist'+(it.wait>0?' · up to '+Math.round(it.wait/100)/10+' s':it.wait<0?' · as long as it takes':''));
waitFor(function(){return find(it.exists);},it.wait).then(function(found){if(found)go(el||found);else say(it,'gave up',it.exists+' wasn’t on the page');});}else go(el);}
var reqs=0,pending={};
window.__pwaGMReply=function(id,res){var o=pending[id];if(!o)return;delete pending[id];try{if(res.error){o.onerror&&o.onerror(res);o.reject&&o.reject(res);}else{o.onload&&o.onload(res);o.resolve&&o.resolve(res);}}catch(e){}};
function gm(it){var id=it.id,n=0;function get(k,d){var v=null;try{v=B.gmGet(T,id,String(k));}catch(e){}return v==null?d:JSON.parse(v);}
function set(k,v){try{B.gmSet(T,id,String(k),JSON.stringify(v));}catch(e){}}function del(k){try{B.gmDel(T,id,String(k));}catch(e){}}
function list(){try{return JSON.parse(B.gmList(T,id));}catch(e){return [];}}function clip(t){try{B.gmClip(T,id,String(t));}catch(e){}}function open(u){try{B.gmOpen(T,id,String(u));}catch(e){}}
function style(css){sheet('__pwa_gm_'+id+'_'+(n++),String(css));}
function request(o,resolve,reject){var rid=++reqs;o.resolve=resolve;o.reject=reject;pending[rid]=o;try{B.gmFetch(T,id,rid,JSON.stringify({method:o.method||'GET',url:String(o.url),headers:o.headers||{},data:o.data==null?null:String(o.data)}));}catch(e){delete pending[rid];o.onerror&&o.onerror({error:String(e)});}return {abort:function(){delete pending[rid];}};}
var info={script:{name:it.name,version:it.ver||''},scriptHandler:'Leyden PWAs',version:'2'};
var sync={info:info,getValue:get,setValue:set,deleteValue:del,listValues:list,setClipboard:clip,openInTab:open,addStyle:style,xmlhttpRequest:function(o){return request(o);}};
return {info:info,sync:sync,getValue:function(k,d){return Promise.resolve(get(k,d));},setValue:function(k,v){set(k,v);return Promise.resolve();},deleteValue:function(k){del(k);return Promise.resolve();},
listValues:function(){return Promise.resolve(list());},setClipboard:function(t){clip(t);return Promise.resolve();},openInTab:function(u){open(u);return Promise.resolve();},addStyle:function(c){style(c);return Promise.resolve();},
xmlHttpRequest:function(o){return new Promise(function(a,b){request(o,a,b);});}};}
function address(){var last=location.href;function check(){if(location.href!==last){last=location.href;watchers.forEach(function(f){f();});}}
['pushState','replaceState'].forEach(function(k){var orig=history[k];history[k]=function(){var r=orig.apply(this,arguments);setTimeout(check,0);return r;};});
window.addEventListener('popstate',check);window.addEventListener('hashchange',check);}
function wire(it){it.trig.forEach(function(t){var k=t[0],v=t[1];
if(k==='before')run(it,k);
else if(k==='ready'){if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){run(it,k);});else run(it,k);}
else if(k==='loaded'){if(document.readyState!=='complete')window.addEventListener('load',function(){run(it,k);});else run(it,k);}
else if(k==='appears'){var sel=v||it.exists;if(!sel)return;var marked=new WeakSet();var check=function(){findAll(sel).forEach(function(e){if(!marked.has(e)){marked.add(e);run(it,k,e);}});};
var begin=function(){check();var ob=new MutationObserver(check);ob.observe(document.documentElement,{childList:true,subtree:true});if(it.wait>0)setTimeout(function(){ob.disconnect();},it.wait);};
if(document.documentElement)begin();else document.addEventListener('DOMContentLoaded',begin);}
else if(k==='address')watchers.push(function(){run(it,k);});
else if(k==='every'){var s=Math.max(1,+v||30);timers.push(setInterval(function(){run(it,k);},s*1000));}});}
var RT={net:'@NET@',off:false,
fire:function(kind,value){items.forEach(function(it){if(it.trig.some(function(t){return t[0]===kind&&(!t[1]||!value||t[1]===value);}))run(it,kind);});},
runOne:function(id){items.forEach(function(it){if(it.id===id)run(it,'manual');});},
test:function(m){m.testing=true;watch[m.id]=1;var i=-1;items.forEach(function(x,k){if(x.id===m.id)i=k;});if(i>=0)items[i]=m;else items.push(m);busy[m.id]=0;say(m,'start','test started on '+location.pathname);run(m,'test');},
start:function(){if(started)return;started=true;items.forEach(function(it){if(it.css==null&&!fns[it.id])fail(it,{message:'didn’t load: its code has a mistake, so it never started'});});
address();items.forEach(wire);}};
window.__pwaQ={push:function(e){if(e&&e.id)fns[e.id]=e.fn;}};
window.__pwaRT=RT;
})();
"""

    private const val SYNC = "var GM_info=GM.info,GM_getValue=GM.sync.getValue,GM_setValue=GM.sync.setValue,GM_deleteValue=GM.sync.deleteValue,GM_listValues=GM.sync.listValues," +
        "GM_setClipboard=GM.sync.setClipboard,GM_openInTab=GM.sync.openInTab,GM_addStyle=GM.sync.addStyle,GM_xmlhttpRequest=GM.sync.xmlhttpRequest,unsafeWindow=window;"

    fun meta(site: Site, item: Insertion): JSONObject {
        val json = JSONObject()
            .put("id", item.id).put("name", item.name).put("ver", item.version)
            .put("trig", JSONArray(item.triggers.map { JSONArray().put(it.kind).put(it.value) }))
            .put("path", item.path).put("exists", item.exists).put("net", item.network).put("from", item.timeFrom).put("to", item.timeTo)
            .put("after", item.after).put("wait", if (item.waitMs < 0) -1 else item.waitMs).put("pause", item.pauseMs).put("often", item.often)
            .put("most", item.atMost).put("frames", item.frames)
        if (item.kind == "css") json.put("css", item.code)
        return json
    }

    fun core(site: Site, token: String, network: String): String {
        val list = JSONArray()
        for (item in site.insertions) if (item.on) list.put(meta(site, item))
        return CORE.replace("@TOKEN@", token).replace("@NET@", network).replace("@ITEMS@", list.toString())
    }

    fun code(item: Insertion): String = if (item.kind == "auto") autoCode(item) else item.code

    fun register(item: Insertion): String =
        "(function(){var q=window.__pwaQ;if(!q)return;q.push({id:${JSONObject.quote(item.id)},fn:function(GM,H,el){$SYNC\n${code(item)}\n}});})();"

    const val START = "window.__pwaRT&&window.__pwaRT.start();"

    fun fire(kind: String, value: String = ""): String = "window.__pwaRT&&window.__pwaRT.fire(${JSONObject.quote(kind)},${JSONObject.quote(value)});"

    fun runOne(id: String): String = "window.__pwaRT&&window.__pwaRT.runOne(${JSONObject.quote(id)});"

    fun test(meta: JSONObject): String = "window.__pwaRT&&window.__pwaRT.test($meta);"

    fun autoCode(item: Insertion): String {
        val spec = item.auto ?: AutoSpec()
        val targets = JSONArray(spec.targets).toString()
        val wait = if (item.waitMs < 0) -1 else item.waitMs
        val action = when (spec.action) {
            "focus" -> "el.focus();"
            "type" -> "el.focus();var setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');if(setter&&setter.set)setter.set.call(el,${JSONObject.quote(spec.text)});else el.value=${JSONObject.quote(spec.text)};" +
                "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));"
            "check" -> "if(!el.checked&&el.getAttribute('aria-checked')!=='true')el.click();"
            "scroll" -> "el.scrollIntoView({block:'center',behavior:'smooth'});"
            else -> "el.click();"
        }
        val verb = when (spec.action) {
            "focus" -> "focusing"
            "type" -> "typing into"
            "check" -> "checking"
            "scroll" -> "scrolling to"
            else -> "clicking"
        }
        return "var targets=$targets,say=H.say||function(){};\n" +
            "if(!el)say('looking for '+targets.join(' or ')+($wait>0?' · up to '+($wait/1000)+' s':''),'waiting');\n" +
            "return (el?Promise.resolve(el):H.waitFor(function(){for(var i=0;i<targets.length;i++){var found=H.find(targets[i]);if(found)return found;}return null;},$wait))\n" +
            "  .then(function(el){if(!el){say('nothing matched','gave up');return;}say('found a '+el.tagName.toLowerCase()+((el.innerText||'').trim()?' “'+(el.innerText||'').trim().slice(0,40)+'”':'')+', $verb it');$action});"
    }
}
