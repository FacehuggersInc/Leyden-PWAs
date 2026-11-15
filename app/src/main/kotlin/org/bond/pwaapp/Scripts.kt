package org.bond.pwaapp

import org.json.JSONArray
import org.json.JSONObject

object Scripts {
    private const val MATCH = "function m(p,path){if(!p||p==='*'||p==='/')return true;if(p.indexOf('*')<0)return path.indexOf(p)===0;" +
        "var r=new RegExp('^'+p.split('*').map(function(x){return x.replace(/[.+?^\${}()|[\\]\\\\]/g,'\\\\$&')}).join('.*'));return r.test(path);}"

    private const val SHEET = "function sheet(id,css){try{var map=window.__pwaSheets||(window.__pwaSheets={});var sh=map[id];" +
        "if(!sh){sh=new CSSStyleSheet();map[id]=sh;}sh.replaceSync(css);if(document.adoptedStyleSheets.indexOf(sh)<0)document.adoptedStyleSheets=document.adoptedStyleSheets.concat([sh]);return;}catch(e){}" +
        "var el=document.getElementById(id);if(!el){el=document.createElement('style');el.id=id;(document.head||document.documentElement).appendChild(el);}el.textContent=css;}"

    const val BOOT = "(function(){if(window.__pwaBoot)return;window.__pwaBoot=1;$SHEET" +
        "try{var c=window.PwaApp&&PwaApp.css(location.href);if(c)sheet('__pwa_lists',c);}catch(e){}})();"

    fun origins(site: Site): Set<String> {
        val host = site.host
        val scheme = if (site.url.startsWith("http:")) "http" else "https"
        val port = try {
            java.net.URI(site.url).port
        } catch (error: Exception) {
            -1
        }
        val numeric = host.all { it.isDigit() || it == '.' } || ':' in host
        val suffix = if (port > 0) ":$port" else ""
        if (numeric || '.' !in host) return setOf("$scheme://$host$suffix")
        val base = baseDomain(host)
        return setOf("$scheme://$base$suffix", "$scheme://*.$base$suffix")
    }

    fun hidden(site: Site): String {
        val rules = JSONArray()
        for (item in site.hidden) rules.put(JSONObject().put("s", item.selector).put("p", item.path))
        return "(function(){$MATCH $SHEET var rules=$rules;function apply(){var css='';rules.forEach(function(r){if(m(r.p,location.pathname))css+=r.s+'{display:none!important}\\n';});" +
            "sheet('__pwa_hidden',css);rules.forEach(function(r){if(!m(r.p,location.pathname))return;try{document.querySelectorAll(r.s).forEach(function(e){e.style.setProperty('display','none','important');});}catch(x){}});}" +
            "window.__pwaHidden=apply;apply();if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply);})();"
    }

    fun lists(css: String): String =
        "(function(){$SHEET sheet('__pwa_lists',${JSONObject.quote(css)});})();"

    const val THEME = "(function(){var m=document.querySelector('meta[name=\"theme-color\"]');return m?m.getAttribute('content'):'';})()"

    const val LOGIN = "(function(){if(window.__pwaLogin||!window.PwaApp)return;window.__pwaLogin=1;function kind(e){if(!e||e.tagName!=='INPUT')return '';var t=(e.type||'').toLowerCase(),a=(e.getAttribute('autocomplete')||'').toLowerCase(),n=((e.name||'')+' '+(e.id||'')).toLowerCase();" +
        "if(t==='password')return 'password';if(/username|email|webauthn/.test(a)||t==='email'||/user|login|email|account/.test(n))return 'user';return '';}" +
        "document.addEventListener('focusin',function(v){var k=kind(v.target);if(k){try{PwaApp.field(k);}catch(x){}}},true);document.addEventListener('focusout',function(v){if(kind(v.target)){try{PwaApp.field('');}catch(x){}}},true);})();"

    const val HIDER = """window.__pwaHider||(window.__pwaHider=(function(){var el=null,trail=[],boxes=[],chosen='',custom=false,mode='hide',hits=[];
var LIVE='button,a[href],input,select,textarea,label,summary,[role=button],[role=link],[role=checkbox],[role=switch],[role=tab],[role=menuitem],[role=option],[contenteditable=true],[onclick],[tabindex]';
function block(e){e.preventDefault();e.stopPropagation();}
document.addEventListener('click',block,true);document.addEventListener('auxclick',block,true);document.addEventListener('submit',block,true);
function word(x){return /^[A-Za-z_-][A-Za-z0-9_-]*$/.test(x);}
function junk(x){if(!word(x)||x.length>32)return true;if(/\d{3,}/.test(x))return true;if(/^(css|sc|jsx|svelte|emotion|tw|styled|chakra|mui|Mui)-/.test(x)&&/\d/.test(x))return true;
if(/\d/.test(x)&&/[a-z]/i.test(x)&&!/[-_]/.test(x)&&x.length>=5)return true;if(/^(active|hover|focus|open|show|shown|visible|selected|is-|has-)/.test(x))return true;return false;}
function q(s){return '"'+s.replace(/\\/g,'\\\\').replace(/"/g,'\\"')+'"';}
function words(e){return (e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\s+/g,' ').trim();}
function said(e,m,v){var w=words(e);return m==='^'?w.indexOf(v)===0:m==='*'?w.indexOf(v)>=0:w===v;}
function all(s){var t=/^text([*^]?):([a-z0-9*-]+)\|([\s\S]*)$/i.exec(s);if(t){try{return [].filter.call(document.querySelectorAll(t[2]),function(e){return said(e,t[1],t[3]);});}catch(x){return []}}try{return [].slice.call(document.querySelectorAll(s))}catch(x){return []}}
function count(s){return all(s).length;}
function path(e){var s=e.tagName.toLowerCase();var cls=[].slice.call(e.classList).filter(function(c){return !junk(c)}).slice(0,2);if(cls.length)s+='.'+cls.join('.');
var p=e.parentElement;if(p&&count(s)>1&&p!==document.body&&p!==document.documentElement){var kin=[].filter.call(p.children,function(c){return c.tagName===e.tagName});if(kin.length>1)s+=':nth-of-type('+(kin.indexOf(e)+1)+')';return path(p)+' > '+s;}return s;}
function options(e){var t=e.tagName.toLowerCase(),out=[],seen={};function add(s,why,stab){if(!s||seen[s])return;var n=count(s);if(n<1)return;seen[s]=1;out.push({s:s,n:n,why:why,stab:stab});}
if(e.id&&!junk(e.id))add('#'+e.id,'its id','stable');
['aria-label','data-testid','data-test','data-qa','name','placeholder','title','role'].forEach(function(a){var v=e.getAttribute(a);if(v&&v.length<=40)add(t+'['+a+'='+q(v)+']',a==='aria-label'?'its label, set by the site':a,'stable');});
var w=words(e);if(mode==='target'&&w&&w.length<=40&&!e.children.length||mode==='target'&&w&&w.length<=40&&/^(button|a|label|summary)$/.test(t))add('text:'+t+'|'+w,'what it says; matched by its words','stable');
var good=[].slice.call(e.classList).filter(function(c){return !junk(c)});
if(good.length>1)add(t+'.'+good.join('.'),'all its classes','may change');
good.forEach(function(c){add(t+'.'+c,'its class','may change');add('.'+c,'class, any tag','may change');});
add(path(e),'its place on the page','fragile');return out.slice(0,10);}
function label(e){var s=e.tagName.toLowerCase();if(e.id&&!junk(e.id))return s+'#'+e.id;var c=[].slice.call(e.classList).filter(function(x){return !junk(x)})[0];return c?s+'.'+c:s;}
function chain(e){var out=[],x=e;while(x&&x!==document.body&&x!==document.documentElement&&out.length<6){out.unshift(label(x));x=x.parentElement;}return out;}
function clear(){boxes.forEach(function(b){b.remove()});boxes=[];}
function box(r,strong){var b=document.createElement('div');b.style.cssText='position:fixed;z-index:2147483647;pointer-events:none;border-radius:4px;left:'+r.left+'px;top:'+r.top+'px;width:'+r.width+'px;height:'+r.height+'px;'+
(strong?'border:2px solid #E8590C;background:rgba(232,89,12,0.18)':'border:1px dashed #E8590C;background:rgba(232,89,12,0.08)');document.documentElement.appendChild(b);boxes.push(b);}
function draw(){clear();if(chosen){all(chosen).slice(0,60).forEach(function(x){if(x!==el)box(x.getBoundingClientRect(),false)})}if(el)box(el.getBoundingClientRect(),true);}
function kind(e){var t=e.tagName.toLowerCase();if(/^(input|textarea|select)$/.test(t)||e.isContentEditable)return 'can be typed in';if(e.matches(LIVE))return 'can be clicked';return '';}
function info(snapped){if(!el)return '';var r=el.getBoundingClientRect();var opts=options(el);if(!chosen||(!custom&&!opts.some(function(o){return o.s===chosen})))chosen=opts.length?opts[0].s:'';draw();
return JSON.stringify({tag:el.tagName.toLowerCase(),w:Math.round(r.width),h:Math.round(r.height),text:words(el).slice(0,48),kind:kind(el),snapped:!!snapped,options:opts,chosen:chosen,custom:custom,cn:count(chosen),chain:chain(el),
prev:!!el.previousElementSibling,next:!!el.nextElementSibling,up:!!(el.parentElement&&el.parentElement!==document.body),down:!!el.firstElementChild});}
function go(x,snapped){if(!x||x===document.body||x===document.documentElement)return info();el=x;chosen='';custom=false;return info(snapped);}
window.addEventListener('scroll',function(){if(el)draw()},true);
return{mode:function(m){mode=m;chosen='';custom=false;return info();},count:function(s){return String(count(s));},
pick:function(fx,fy){var e=document.elementFromPoint(fx*innerWidth,fy*innerHeight);if(!e||e===document.documentElement||e===document.body)return '';trail=[];
if(mode==='target'){var live=e.closest(LIVE);if(live&&live!==e)return go(live,true);}return go(e);},
up:function(){if(el&&el.parentElement&&el.parentElement!==document.body){trail.push(el);return go(el.parentElement);}return info();},
down:function(){if(trail.length)return go(trail.pop());return el?go(el.firstElementChild):'';},
prev:function(){trail=[];return el?go(el.previousElementSibling):'';},next:function(){trail=[];return el?go(el.nextElementSibling):'';},
level:function(i){var x=el,list=[];while(x&&x!==document.body&&x!==document.documentElement&&list.length<6){list.unshift(x);x=x.parentElement;}trail=[];return go(list[i]);},
choose:function(s,own){chosen=s;custom=!!own;draw();return info();},
search:function(q){hits=[];q=String(q||'').trim();clear();if(!q){if(el)draw();return '{"by":"","items":[]}';}var list=[],low=q.toLowerCase(),css=null;
if(/[#.\[>:]/.test(q)||/^[a-z]+[0-9]?$/i.test(q)){try{css=[].slice.call(document.querySelectorAll(q));}catch(x){css=null;}}
if(css&&css.length){list=css;}else{var every=document.body?document.body.getElementsByTagName('*'):[];
for(var i=0;i<every.length&&list.length<200;i++){var e=every[i];if(/^(script|style|noscript|template|svg|path)$/i.test(e.tagName))continue;
var attrs=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('placeholder')||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('alt')||'')+' '+(e.value&&typeof e.value==='string'?e.value:'')).toLowerCase();
var own=attrs.indexOf(low)>=0;if(!own){var t=(e.textContent||'').toLowerCase();if(t.indexOf(low)<0)continue;var deeper=false;for(var c=0;c<e.children.length;c++){if((e.children[c].textContent||'').toLowerCase().indexOf(low)>=0){deeper=true;break;}}if(deeper)continue;}
if(mode==='target'){var live=e.closest(LIVE);if(live)e=live;}if(list.indexOf(e)<0)list.push(e);}}
list=list.filter(function(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;});hits=list.slice(0,40);
hits.forEach(function(e){box(e.getBoundingClientRect(),false);});
return JSON.stringify({by:css&&css.length?'selector':'text',items:hits.map(function(e,i){var r=e.getBoundingClientRect();var w=(e.innerText||e.value||'').replace(/\s+/g,' ').trim(),l=e.getAttribute('aria-label')||e.getAttribute('placeholder')||e.getAttribute('title')||'';return {i:i,tag:label(e),text:(w&&l&&w!==l?w+' · “'+l+'”':w||(l?'“'+l+'”':'')).slice(0,80),kind:kind(e),seen:r.bottom>0&&r.top<innerHeight};})});},
found:function(i){var e=hits[i];hits=[];if(!e)return info();try{e.scrollIntoView({block:'center'});}catch(x){}trail=[];return go(e);},
hide:function(s){all(s).forEach(function(x){x.style.setProperty('display','none','important')});return '';},
done:function(){clear();el=null;trail=[];chosen='';custom=false;document.removeEventListener('click',block,true);document.removeEventListener('auxclick',block,true);document.removeEventListener('submit',block,true);window.__pwaHider=null;return '';}};})());"""
}
