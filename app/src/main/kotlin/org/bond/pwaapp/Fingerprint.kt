package org.bond.pwaapp

import android.os.Bundle
import android.webkit.WebSettings
import org.json.JSONObject

class Fingerprint(var level: String = "standard") {
    var model = true
    var requestedWith = true
    var hardware = true
    var webrtc = "local"
    var canvas = false
    var webgl = false
    var audio = false
    var screen = false
    var devices = false
    var battery = false
    var timezone = false
    var language = false
    var seed = (Math.random() * Int.MAX_VALUE).toInt()
    var relaxChecks = true

    init {
        apply(level)
    }

    fun apply(preset: String) {
        level = preset
        when (preset) {
            "off" -> set(false, "allow", false)
            "standard" -> set(true, "local", false)
            "strict" -> set(true, "off", true)
        }
    }

    private fun set(base: Boolean, rtc: String, strong: Boolean) {
        model = base
        requestedWith = base
        hardware = base
        webrtc = rtc
        canvas = strong
        webgl = strong
        audio = strong
        screen = strong
        devices = strong
        battery = strong
        timezone = false
        language = false
    }

    val active: Boolean
        get() = model || requestedWith || hardware || webrtc != "allow" || canvas || webgl || audio || screen || devices || battery || timezone || language

    val needsScript: Boolean
        get() = hardware || webrtc != "allow" || canvas || webgl || audio || screen || devices || battery || timezone || language

    fun summary(): String = when (level) {
        "custom" -> "custom"
        else -> level
    }

    fun toJson(): JSONObject = JSONObject().put("level", level).put("model", model).put("requestedWith", requestedWith).put("hardware", hardware)
        .put("webrtc", webrtc).put("canvas", canvas).put("webgl", webgl).put("audio", audio).put("screen", screen).put("devices", devices)
        .put("battery", battery).put("timezone", timezone).put("language", language).put("seed", seed).put("relaxChecks", relaxChecks)

    fun copyFresh(): Fingerprint {
        val made = fromJson(toJson())
        made.seed = (Math.random() * Int.MAX_VALUE).toInt()
        return made
    }

    fun config(skip: List<String> = emptyList()): String = JSONObject().put("skip", org.json.JSONArray(skip.map { Relax.regex(it).pattern })).put("seed", seed).put("hardware", hardware).put("webrtc", webrtc).put("canvas", canvas).put("webgl", webgl)
        .put("audio", audio).put("screen", screen).put("devices", devices).put("battery", battery).put("timezone", timezone).put("language", language).toString()

    companion object {
        val LEVELS = listOf(Option("off"), Option("standard", "std"), Option("strict"), Option("custom"))

        fun fromJson(json: JSONObject?): Fingerprint {
            val made = Fingerprint(json?.optString("level", "standard")?.ifEmpty { "standard" } ?: "standard")
            if (json == null) return made
            made.model = json.optBoolean("model", made.model)
            made.requestedWith = json.optBoolean("requestedWith", made.requestedWith)
            made.hardware = json.optBoolean("hardware", made.hardware)
            made.webrtc = json.optString("webrtc", made.webrtc).takeIf { it in listOf("allow", "local", "off") } ?: made.webrtc
            made.canvas = json.optBoolean("canvas", made.canvas)
            made.webgl = json.optBoolean("webgl", made.webgl)
            made.audio = json.optBoolean("audio", made.audio)
            made.screen = json.optBoolean("screen", made.screen)
            made.devices = json.optBoolean("devices", made.devices)
            made.battery = json.optBoolean("battery", made.battery)
            made.timezone = json.optBoolean("timezone", made.timezone)
            made.language = json.optBoolean("language", made.language)
            if (json.has("seed")) made.seed = json.optInt("seed", made.seed)
            made.relaxChecks = json.optBoolean("relaxChecks", true)
            return made
        }

        fun agent(original: String, fp: Fingerprint): String {
            if (!fp.model) return original
            val chrome = Regex("Chrome/(\\d+)").find(original)?.groupValues?.get(1) ?: return original
            val wv = if (original.contains("; wv)")) "; wv" else ""
            val version = if (original.contains("Version/4.0")) "Version/4.0 " else ""
            val mobile = if (original.contains("Mobile")) "Mobile " else ""
            return "Mozilla/5.0 (Linux; Android 10; K$wv) AppleWebKit/537.36 (KHTML, like Gecko) ${version}Chrome/$chrome.0.0.0 ${mobile}Safari/537.36"
        }

        fun native(settings: WebSettings, fp: Fingerprint) {
            try {
                val compat = Class.forName("androidx.webkit.WebSettingsCompat")
                if (fp.requestedWith) {
                    compat.getMethod("setRequestedWithHeaderOriginAllowList", WebSettings::class.java, Set::class.java).invoke(null, settings, emptySet<String>())
                }
            } catch (error: Throwable) {
                Unit
            }
            if (!fp.model) return
            try {
                val compat = Class.forName("androidx.webkit.WebSettingsCompat")
                val metaClass = Class.forName("androidx.webkit.UserAgentMetadata")
                val current = compat.getMethod("getUserAgentMetadata", WebSettings::class.java).invoke(null, settings)
                val builderClass = Class.forName("androidx.webkit.UserAgentMetadata\$Builder")
                val builder = builderClass.getConstructor(metaClass).newInstance(current)
                builderClass.getMethod("setModel", String::class.java).invoke(builder, "")
                builderClass.getMethod("setPlatformVersion", String::class.java).invoke(builder, "10.0.0")
                val built = builderClass.getMethod("build").invoke(builder)
                compat.getMethod("setUserAgentMetadata", WebSettings::class.java, metaClass).invoke(null, settings, built)
            } catch (error: Throwable) {
                Unit
            }
        }

        const val COUNT = """(function(){if(window.__pwaFpCount)return;var seen={},timer=null;window.__pwaFpCount=function(k){seen[k]=(seen[k]||0)+1;if(!timer)timer=setTimeout(function(){timer=null;var s=JSON.stringify(seen);seen={};try{PwaApp.fpSeen(s);}catch(e){}},700);};
function wrap(o,n,k,only){try{var f=o&&o[n];if(typeof f!=='function')return;o[n]=function(){if(!window.__pwaFpQuiet&&(!only||only(arguments)))window.__pwaFpCount(k);return f.apply(this,arguments);};}catch(e){}}
var ids=function(a){var p=a[0];return p===0x9245||p===0x9246||p===0x1F00||p===0x1F01;};
if(window.HTMLCanvasElement){wrap(HTMLCanvasElement.prototype,'toDataURL','canvas');wrap(HTMLCanvasElement.prototype,'toBlob','canvas');}
if(window.CanvasRenderingContext2D){wrap(CanvasRenderingContext2D.prototype,'getImageData','canvas');wrap(CanvasRenderingContext2D.prototype,'measureText','fonts');}
['WebGLRenderingContext','WebGL2RenderingContext'].forEach(function(n){var c=window[n];if(c){wrap(c.prototype,'getParameter','webgl',ids);wrap(c.prototype,'readPixels','webgl');}});
if(window.AudioBuffer)wrap(AudioBuffer.prototype,'getChannelData','audio');if(window.AnalyserNode)wrap(AnalyserNode.prototype,'getFloatFrequencyData','audio');
if(navigator.mediaDevices)wrap(navigator.mediaDevices,'enumerateDevices','devices');if(navigator.getBattery)wrap(navigator,'getBattery','battery');
try{if(window.RTCPeerConnection){var R=window.RTCPeerConnection;window.RTCPeerConnection=function(){window.__pwaFpCount('webrtc');return new (Function.prototype.bind.apply(R,[null].concat([].slice.call(arguments))))();};window.RTCPeerConnection.prototype=R.prototype;}}catch(e){}})();"""

        const val GUARD = """(function(){var C=@CONFIG@;if(window.__pwaFp)return;try{var here=location.host+location.pathname;if((C.skip||[]).some(function(p){return new RegExp(p,'i').test(here);}))return;}catch(e){}window.__pwaFp=1;var count=window.__pwaFpCount||function(){};
function def(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v;},configurable:true});}catch(e){}}
if(C.hardware){def(Navigator.prototype,'hardwareConcurrency',8);def(Navigator.prototype,'deviceMemory',4);}
if(C.screen){var w=412,h=915;def(Screen.prototype,'width',w);def(Screen.prototype,'height',h);def(Screen.prototype,'availWidth',w);def(Screen.prototype,'availHeight',h);def(Screen.prototype,'colorDepth',24);def(Screen.prototype,'pixelDepth',24);def(window,'devicePixelRatio',2.625);}
if(C.language){def(Navigator.prototype,'language','en-US');def(Navigator.prototype,'languages',['en-US','en']);}
if(C.timezone){try{var R=Intl.DateTimeFormat.prototype.resolvedOptions;Intl.DateTimeFormat.prototype.resolvedOptions=function(){var o=R.apply(this,arguments);o.timeZone='UTC';return o;};Date.prototype.getTimezoneOffset=function(){return 0;};}catch(e){}}
if(C.battery&&navigator.getBattery){navigator.getBattery=function(){count('battery');return Promise.resolve({charging:true,chargingTime:0,dischargingTime:Infinity,level:1,addEventListener:function(){},removeEventListener:function(){},onchargingchange:null,onlevelchange:null});};}
if(C.devices&&navigator.mediaDevices&&navigator.mediaDevices.enumerateDevices){navigator.mediaDevices.enumerateDevices=function(){count('devices');return Promise.resolve([{deviceId:'',groupId:'',kind:'audioinput',label:''},{deviceId:'',groupId:'',kind:'videoinput',label:''},{deviceId:'',groupId:'',kind:'audiooutput',label:''}]);};}
if(C.webrtc==='off'){try{delete window.RTCPeerConnection;delete window.webkitRTCPeerConnection;window.RTCPeerConnection=undefined;window.webkitRTCPeerConnection=undefined;}catch(e){}}
else if(C.webrtc==='local'&&window.RTCPeerConnection){var P=window.RTCPeerConnection;var priv=/(^|\s)(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.|fd[0-9a-f]{2}:|fe80:)/i;
var Q=function(){var pc=new (Function.prototype.bind.apply(P,[null].concat([].slice.call(arguments))))();var add=pc.addEventListener;pc.addEventListener=function(t,f,o){if(t==='icecandidate'){var g=f;f=function(e){if(e&&e.candidate&&priv.test(e.candidate.candidate))return;return g.apply(this,arguments);};}return add.call(this,t,f,o);};
var d=Object.getOwnPropertyDescriptor(P.prototype,'onicecandidate');if(d&&d.set){Object.defineProperty(pc,'onicecandidate',{set:function(f){d.set.call(pc,function(e){if(e&&e.candidate&&priv.test(e.candidate.candidate))return;return f&&f.apply(this,arguments);});},get:function(){return d.get.call(pc);},configurable:true});}return pc;};
Q.prototype=P.prototype;window.RTCPeerConnection=Q;}
function mix(h,v){h=Math.imul(h^v,16777619)>>>0;return h;}
function noise(data){var h=C.seed>>>0;for(var i=0;i<data.length;i+=61)h=mix(h,data[i]);var t=h;function r(){t=mix(t+0x9E3779B9,t>>>13);return t/4294967296;}for(var j=0;j<data.length;j+=4){if(r()<0.02){var k=(r()*3)|0;data[j+k]=data[j+k]^1;}}}
if(C.canvas&&window.CanvasRenderingContext2D){var G=CanvasRenderingContext2D.prototype.getImageData;CanvasRenderingContext2D.prototype.getImageData=function(){var img=G.apply(this,arguments);try{noise(img.data);}catch(e){}return img;};
function noisy(cv){window.__pwaFpQuiet=1;try{var w=cv.width,h=cv.height;if(!w||!h)return cv;var c=document.createElement('canvas');c.width=w;c.height=h;var x=c.getContext('2d');x.drawImage(cv,0,0);var img=G.call(x,0,0,w,h);noise(img.data);x.putImageData(img,0,0);return c;}catch(e){return cv;}finally{window.__pwaFpQuiet=0;}}
var U=HTMLCanvasElement.prototype.toDataURL,B=HTMLCanvasElement.prototype.toBlob;HTMLCanvasElement.prototype.toDataURL=function(){return U.apply(noisy(this),arguments);};HTMLCanvasElement.prototype.toBlob=function(){return B.apply(noisy(this),arguments);};}
if(C.webgl){['WebGLRenderingContext','WebGL2RenderingContext'].forEach(function(n){var c=window[n];if(!c)return;var P2=c.prototype.getParameter,RP=c.prototype.readPixels;c.prototype.getParameter=function(p){if(p===0x9245||p===0x9246){count('webgl');return p===0x9245?'Google Inc.':'ANGLE (Google, Vulkan, Generic)';}return P2.apply(this,arguments);};
c.prototype.readPixels=function(){var r=RP.apply(this,arguments);try{var a=arguments[6];if(a&&a.length)noise(a);}catch(e){}return r;};});}
if(C.audio){if(window.AudioBuffer){var CD=AudioBuffer.prototype.getChannelData;AudioBuffer.prototype.getChannelData=function(){var d=CD.apply(this,arguments);try{if(!d.__pwa){for(var i=0;i<d.length;i+=97)d[i]+=((mix(C.seed>>>0,i)%1000)/1000-0.5)*1e-7;d.__pwa=1;}}catch(e){}return d;};}
if(window.AnalyserNode){var FF=AnalyserNode.prototype.getFloatFrequencyData;AnalyserNode.prototype.getFloatFrequencyData=function(a){FF.apply(this,arguments);try{for(var i=0;i<a.length;i+=7)a[i]+=((mix(C.seed>>>0,i)%1000)/1000-0.5)*1e-4;}catch(e){}};}}})();"""

        fun script(fp: Fingerprint, skip: List<String> = emptyList()): String = GUARD.replace("@CONFIG@", fp.config(skip))

        val NAMES = linkedMapOf(
            "canvas" to "canvas", "webgl" to "WebGL", "audio" to "audio", "fonts" to "font measuring",
            "devices" to "media devices", "battery" to "battery", "webrtc" to "WebRTC",
        )

        fun describe(counts: Map<String, Int>): String =
            counts.entries.filter { it.value > 0 }.sortedByDescending { it.value }.joinToString(", ") { "${NAMES[it.key] ?: it.key} ${it.value}×" }
    }
}

class FingerprintActivity : Screen() {
    private lateinit var site: Site

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        if (found == null) {
            finish()
            return
        }
        site = found
        guarded = found
    }

    private fun save() {
        SiteStore.save(this, site)
        redraw()
    }

    private fun custom(change: (Fingerprint) -> Unit) {
        change(site.fp)
        site.fp.level = "custom"
        save()
    }

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: return
        val fp = site.fp
        header("Fingerprinting", "${site.label} · ${site.host}", site = site)
        content.put(note("Sites can tell your device apart by reading small details: your phone model, screen, graphics, audio and more. Leyden can hide or blur these. Each one is up to you; some can break sites, and none can make you invisible."))
        val level = card()
        level.choice(Glyphs.SHIELD, "protection", when (fp.level) {
            "off" -> "sites see your real details"
            "standard" -> "hides what rarely breaks anything"
            "strict" -> "also blurs canvas, WebGL and audio; may break some sites"
            else -> "your own mix, below"
        }, Fingerprint.LEVELS.filter { it.key != "custom" || fp.level == "custom" }, fp.level) {
            fp.apply(it)
            save()
        }
        content.put(level.view)

        content.put(sectionTitle("SENT WITH EVERY REQUEST", Glyphs.GLOBE))
        val sent = card()
        sent.switch(Glyphs.PHONE, "hide your phone model", "sends a generic Android browser name instead of yours, like “Pixel 8”. Client hints follow on WebView versions that allow it.", fp.model) { on -> custom { it.model = on } }
        sent.switch(Glyphs.TAG, "hide that it’s Leyden", "drops the header that names the app making the request, on WebView versions that send it", fp.requestedWith) { on -> custom { it.requestedWith = on } }
        content.put(sent.view)

        content.put(sectionTitle("READ BY PAGE SCRIPTS", Glyphs.CODE))
        val read = card()
        read.switch(Glyphs.GRID, "round hardware details", "reports 8 cores and 4 GB like most phones", fp.hardware) { on -> custom { it.hardware = on } }
        read.choice(Glyphs.WIFI, "WebRTC", when (fp.webrtc) {
            "off" -> "video calls and some chat sites stop working"
            "local" -> "hides your local network address; calls still work"
            else -> "sites can see your local network address"
        }, listOf(Option("allow"), Option("local", "hide local"), Option("off")), fp.webrtc) { value -> custom { it.webrtc = value } }
        read.switch(Glyphs.SPLASH, "blur canvas", "drawings read back with tiny changes, same within this app. Can upset image editors and some captchas.", fp.canvas) { on -> custom { it.canvas = on } }
        read.switch(Glyphs.BOX, "blur WebGL", "generic graphics name and tiny changes to 3D read-backs. Can upset games and maps.", fp.webgl) { on -> custom { it.webgl = on } }
        read.switch(Glyphs.MUSIC, "blur audio", "tiny changes to processed audio, far below hearing. May affect audio tools.", fp.audio) { on -> custom { it.audio = on } }
        read.switch(Glyphs.FULL, "common screen size", "reports a common phone screen. Layout still uses your real size, so it may not fool everything.", fp.screen) { on -> custom { it.screen = on } }
        read.switch(Glyphs.CAMERA, "generic media devices", "lists one camera, mic and speaker with no names. Picking a specific device inside a site won’t work.", fp.devices) { on -> custom { it.devices = on } }
        read.switch(Glyphs.SUN, "hide battery", "always reports full and charging", fp.battery) { on -> custom { it.battery = on } }
        read.switch(Glyphs.CLOCK, "timezone as UTC", "scripts see UTC. Calendars, bookings and times shown on the page will be off. Your real timezone still shows in some date text.", fp.timezone) { on -> custom { it.timezone = on } }
        read.switch(Glyphs.TEXT, "language as English (US)", "scripts see en-US. Requests still say your real language, so sites may still notice.", fp.language) { on -> custom { it.language = on } }
        content.put(read.view)

        val checks = card()
        checks.switch(Glyphs.SHIELD, "let security checks through", "Cloudflare, hCaptcha, reCAPTCHA and similar get your real details and are never blocked. They exist to tell people from bots, and often refuse protected browsers.", fp.relaxChecks) { on ->
            fp.relaxChecks = on
            save()
        }
        checks.link(Glyphs.LIST, "pages with lower protections", site.relaxPages.size.toString(), "a site’s own check page, for example; everything else stays protected") {
            relaxPagesSheet(site, null) { save() }
        }
        content.put(checks.view)
        val other = card()
        other.action(Glyphs.HISTORY, "new random pattern", "the blurring is the same every visit so sites don’t see you change; this starts a new one", "new") {
            fp.seed = (Math.random() * Int.MAX_VALUE).toInt()
            save()
            toast("New pattern from the next page load")
        }
        content.put(other.view)

        content.put(callout(Glyphs.WARN, "What Leyden can’t change", "Your IP address (a VPN or the proxy can). The way the connection itself looks, which marks it as Chrome. Scripts running in background workers. Fonts can only be counted, not hidden. A determined site can tell values were changed: the aim is to stop tracking across sites, not to be invisible.", T.info))
        content.put(note("Peek uses this app’s choice with a fresh pattern. Private always uses strict with a fresh pattern each time. Changes apply from the next page load."))
    }
}
