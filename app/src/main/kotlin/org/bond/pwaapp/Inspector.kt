package org.bond.pwaapp

import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject

class Req(
    val url: String,
    val method: String,
    val host: String,
    val main: Boolean,
    val blocked: Boolean,
    val list: String,
    val rule: String,
    val other: Boolean,
    val headers: Map<String, String>,
    val at: Long,
) {
    var status = 0
    var reason = ""
    var error = ""
    var kind = ""
    var size = -1L
    var time = -1L

    fun guess(): String {
        if (kind.isNotEmpty()) return kind
        if (main) return "page"
        val path = url.substringBefore('?').lowercase()
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value.orEmpty()
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> "script"
            path.endsWith(".css") -> "style"
            Regex("\\.(png|jpe?g|gif|webp|avif|svg|ico)$").containsMatchIn(path) || accept.startsWith("image/") -> "image"
            Regex("\\.(woff2?|ttf|otf)$").containsMatchIn(path) -> "font"
            Regex("\\.(mp4|webm|m3u8|mpd|mp3|m4a|ogg|ts)$").containsMatchIn(path) -> "media"
            accept.contains("json") || path.endsWith(".json") -> "data"
            accept.contains("text/html") -> "frame"
            else -> "other"
        }
    }
}

class Line(val level: String, val text: String, val source: String, val line: Int, val at: Long)

class Inspector {
    val requests: MutableList<Req> = java.util.Collections.synchronizedList(mutableListOf())
    val console: MutableList<Line> = java.util.Collections.synchronizedList(mutableListOf())
    var pageAt = System.currentTimeMillis()
    var page = ""

    fun newPage(url: String) {
        page = url
        pageAt = System.currentTimeMillis()
        synchronized(requests) {
            val kept = requests.lastOrNull { it.main && it.url == url }
            requests.clear()
            if (kept != null) requests += kept
        }
        console.clear()
    }

    fun request(request: WebResourceRequest, blocked: Boolean, list: String, rule: String, other: Boolean) {
        val url = request.url.toString()
        if (!url.startsWith("http")) return
        val made = Req(url, request.method ?: "GET", request.url.host.orEmpty(), request.isForMainFrame, blocked, list, rule, other, request.requestHeaders ?: emptyMap(), System.currentTimeMillis())
        synchronized(requests) {
            requests += made
            while (requests.size > 600) requests.removeAt(0)
        }
    }

    fun status(url: String, code: Int, reason: String) {
        synchronized(requests) {
            requests.lastOrNull { it.url == url }?.let {
                it.status = code
                it.reason = reason
            }
        }
    }

    fun failed(url: String, description: String) {
        synchronized(requests) { requests.lastOrNull { it.url == url }?.error = description }
    }

    fun console(level: String, text: String, source: String, line: Int) {
        synchronized(console) {
            console += Line(level, text, source, line, System.currentTimeMillis())
            while (console.size > 300) console.removeAt(0)
        }
    }

    fun merge(entries: JSONArray) {
        synchronized(requests) {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONArray(index) ?: continue
                val url = entry.optString(0)
                val found = requests.lastOrNull { it.url == url } ?: continue
                found.kind = when (entry.optString(1)) {
                    "script" -> "script"
                    "link", "css" -> "style"
                    "img", "image" -> "image"
                    "fetch", "xmlhttprequest", "beacon" -> "data"
                    "iframe" -> "frame"
                    "video", "audio" -> "media"
                    "navigation" -> "page"
                    else -> found.guess()
                }
                found.time = entry.optLong(2, -1)
                val size = entry.optLong(3, -1)
                if (size > 0) found.size = size
                val code = entry.optInt(4, 0)
                if (code > 0 && found.status == 0) found.status = code
            }
        }
    }

    companion object {
        const val RESOURCES = "JSON.stringify(performance.getEntriesByType('navigation').concat(performance.getEntriesByType('resource')).map(function(e){return [e.name,e.initiatorType||e.entryType,Math.round(e.duration),e.transferSize||e.encodedBodySize||0,e.responseStatus||0];}))"

        const val PAGE = """(function(){function q(s){return document.querySelector(s);}function attr(s,a){var e=q(s);return e?e.getAttribute(a)||'':'';}
var n=performance.getEntriesByType('navigation')[0]||{};function ms(a,b){return a&&b?Math.round(b-a):-1;}
var ls=0,lk=0;try{lk=localStorage.length;for(var i=0;i<lk;i++){var k=localStorage.key(i);ls+=k.length+(localStorage.getItem(k)||'').length;}}catch(e){lk=-1;}
var ss=0;try{ss=sessionStorage.length;}catch(e){ss=-1;}
return JSON.stringify({title:document.title,url:location.href,ready:document.readyState,charset:document.characterSet,doctype:document.doctype?document.doctype.name:'',lang:document.documentElement.lang||'',
viewport:attr('meta[name=viewport]','content'),theme:attr('meta[name=theme-color]','content'),manifest:attr('link[rel=manifest]','href'),description:attr('meta[name=description]','content'),
elements:document.getElementsByTagName('*').length,scripts:[].map.call(document.scripts,function(s){return s.src||'(inline)';}),styles:document.styleSheets.length,
frames:[].map.call(document.querySelectorAll('iframe'),function(f){return f.src||'(empty)';}),forms:document.forms.length,images:document.images.length,links:document.links.length,
local:lk,localSize:ls,session:ss,sw:!!(navigator.serviceWorker&&navigator.serviceWorker.controller),
dns:ms(n.domainLookupStart,n.domainLookupEnd),connect:ms(n.connectStart,n.connectEnd),ttfb:ms(n.requestStart,n.responseStart),dom:Math.round(n.domContentLoadedEventEnd||0),load:Math.round(n.loadEventEnd||0),
protocol:n.nextHopProtocol||'',transfer:n.transferSize||0,
nav:{ua:navigator.userAgent,platform:navigator.platform,cores:navigator.hardwareConcurrency,memory:navigator.deviceMemory||0,langs:(navigator.languages||[]).join(', '),
screen:screen.width+'×'+screen.height,dpr:devicePixelRatio,inner:innerWidth+'×'+innerHeight,tz:Intl.DateTimeFormat().resolvedOptions().timeZone,cookies:navigator.cookieEnabled,webdriver:!!navigator.webdriver,
online:navigator.onLine,rtc:typeof RTCPeerConnection,touch:navigator.maxTouchPoints}});})()"""
    }
}

class InspectSheet(private val activity: WebAppActivity) {
    private val sheet = Sheet(activity)
    private val area = activity.column(10)
    private var tab = "network"
    private var filter = "all"

    fun show() {
        sheet.body.put(activity.text("Inspect", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text(activity.pageUrl, 11f, T.muted, Fonts.mono).apply { maxLines = 2 })
        sheet.body.put(area)
        paint()
        sheet.show()
    }

    private fun paint() {
        area.removeAllViews()
        area.put(activity.chips(listOf(Option("network"), Option("page"), Option("browser"), Option("console")), tab) {
            tab = it
            paint()
        }, WRAP_CONTENT)
        when (tab) {
            "page" -> page()
            "browser" -> browser()
            "console" -> console()
            else -> network()
        }
    }

    private fun copyRow(text: () -> String): View = activity.quietButton("copy all", Glyphs.COPY, T.ink) {
        activity.copy(text())
        activity.toast("Copied")
    }

    private fun network() {
        activity.webView.evaluateJavascript(Inspector.RESOURCES) { value ->
            val parsed = try {
                JSONArray(JSONArray("[$value]").getString(0))
            } catch (error: Exception) {
                null
            }
            if (parsed != null) activity.inspector.merge(parsed)
            if (tab == "network") paintNetwork()
        }
    }

    private fun paintNetwork() {
        while (area.childCount > 1) area.removeViewAt(1)
        val all = synchronized(activity.inspector.requests) { activity.inspector.requests.toList() }
        val blocked = all.count { it.blocked }
        val others = all.filter { it.other }.map { it.host }.distinct().size
        val bytes = all.filter { it.size > 0 }.sumOf { it.size }
        area.put(activity.mono("${plural(all.size, "request")} · $blocked blocked · ${plural(others, "other site")} · ${bytes(bytes)} seen", 10f))
        area.put(activity.chips(listOf(Option("all"), Option("blocked"), Option("other", "other sites"), Option("script", "scripts"), Option("data"), Option("failed")), filter) {
            filter = it
            paintNetwork()
        }, WRAP_CONTENT)
        val shown = all.filter {
            when (filter) {
                "blocked" -> it.blocked
                "other" -> it.other
                "script" -> it.guess() == "script"
                "data" -> it.guess() == "data"
                "failed" -> it.status >= 400 || it.error.isNotEmpty()
                else -> true
            }
        }.asReversed()
        val list = activity.card()
        if (shown.isEmpty()) list.custom(activity.text("Nothing here for this page.", 13f, T.muted).apply { setPadding(0, activity.dp(14), 0, activity.dp(14)) })
        for (req in shown.take(150)) list.custom(row(req))
        area.put(list.view)
        if (shown.size > 150) area.put(activity.text("Showing the newest 150 of ${shown.size}.", 11f, T.faint))
        area.put(activity.text("Sizes, times and status come from the page’s own timing data, so some are missing for other sites and for requests that didn’t finish. Blocked requests never leave your phone.", 11f, T.faint))
        area.put(copyRow { shown.joinToString("\n") { "${it.method} ${if (it.blocked) "BLOCKED" else it.status.takeIf { s -> s > 0 }?.toString() ?: "-"} ${it.guess()} ${it.url}" } })
    }

    private fun row(req: Req): View {
        val row = activity.line(10)
        row.setPadding(0, activity.dp(8), 0, activity.dp(8))
        row.background = activity.ripple(null, 8f)
        val tone = when {
            req.blocked -> T.danger
            req.status >= 400 || req.error.isNotEmpty() -> 0xFF8A6A00.toInt()
            req.other -> T.info
            else -> T.good
        }
        row.put(activity.tagView(req.guess(), tone, tone and 0x00FFFFFF or 0x1F000000), WRAP_CONTENT)
        val words = activity.column(1)
        val path = req.url.substringAfter(req.host).ifEmpty { "/" }
        words.put(activity.text(req.host, 12f, T.ink, Fonts.monoMedium).oneLine())
        words.put(activity.text(path, 11f, T.muted, Fonts.mono).oneLine())
        row.put(words, 0, WRAP_CONTENT, 1f)
        val state = when {
            req.blocked -> "blocked"
            req.error.isNotEmpty() -> "failed"
            req.status > 0 -> req.status.toString()
            else -> ""
        }
        val extra = listOfNotNull(state.ifEmpty { null }, req.size.takeIf { it > 0 }?.let { bytes(it) }, req.time.takeIf { it >= 0 }?.let { "$it ms" }).joinToString(" · ")
        row.put(activity.mono(extra, 10f, T.muted, 0f), WRAP_CONTENT)
        row.setOnClickListener { detail(req) }
        return row
    }

    private fun detail(req: Req) {
        val inner = Sheet(activity)
        inner.body.put(activity.text(req.host, 18f, T.ink, Fonts.sansBold))
        inner.body.put(activity.text(req.url, 12f, T.body, Fonts.mono).apply { setTextIsSelectable(true) })
        val info = activity.card()
        info.value(null, "method", req.method)
        info.value(null, "kind", req.guess())
        info.value(null, "result", when {
            req.blocked -> "blocked"
            req.error.isNotEmpty() -> "failed: ${req.error}"
            req.status > 0 -> "${req.status} ${req.reason}".trim()
            else -> "loaded, status not shown"
        })
        if (req.blocked) info.value(null, "blocked by", req.list.ifEmpty { "your rules" }, req.rule.ifEmpty { null })
        if (req.size > 0) info.value(null, "size", bytes(req.size))
        if (req.time >= 0) info.value(null, "time", "${req.time} ms")
        info.value(null, "from", if (req.main) "the page itself" else if (req.other) "another site" else "this site")
        info.value(null, "at", "+${req.at - activity.inspector.pageAt} ms after the page started")
        inner.body.put(info.view)
        if (req.headers.isNotEmpty()) {
            inner.body.put(activity.sectionTitle("REQUEST HEADERS"))
            inner.body.put(activity.text(req.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }, 11f, T.body, Fonts.mono).apply { setTextIsSelectable(true) })
        }
        val bar = activity.line(8)
        bar.put(activity.secondaryButton("Copy", Glyphs.COPY) {
            activity.copy(req.url)
            activity.toast("Copied")
        }, 0, activity.dp(46), 1f)
        val host = req.host
        val site = activity.site
        if (req.blocked) {
            bar.put(activity.secondaryButton("Allow $host", Glyphs.CHECK) {
                site.rules += "@@||$host^"
                activity.saveSite()
                inner.dismiss()
                activity.toast("Allowed from the next load")
            }, 0, activity.dp(46), 1f)
        } else if (!req.main) {
            bar.put(activity.secondaryButton("Block $host", Glyphs.SHIELD, T.danger) {
                site.rules += "||$host^"
                activity.saveSite()
                inner.dismiss()
                activity.toast("Blocked from the next load")
            }, 0, activity.dp(46), 1f)
        }
        inner.body.put(bar)
        inner.show()
    }

    private fun page() {
        val cert = activity.webView.certificate
        activity.webView.evaluateJavascript(Inspector.PAGE) { value ->
            if (tab != "page") return@evaluateJavascript
            val json = try {
                JSONObject(JSONArray("[$value]").getString(0))
            } catch (error: Exception) {
                JSONObject()
            }
            while (area.childCount > 1) area.removeViewAt(1)
            val about = activity.card()
            about.value(null, "title", json.optString("title").ifEmpty { "none" })
            about.value(null, "state", json.optString("ready"))
            about.value(null, "language", json.optString("lang").ifEmpty { "not set" })
            about.value(null, "charset", json.optString("charset"))
            about.value(null, "viewport", json.optString("viewport").ifEmpty { "none, so no mobile layout" })
            about.value(null, "theme color", json.optString("theme").ifEmpty { "none" })
            about.value(null, "manifest", json.optString("manifest").ifEmpty { "none" })
            about.value(null, "service worker", if (json.optBoolean("sw")) "controls this page" else "none")
            area.put(activity.sectionTitle("PAGE"))
            area.put(about.view)

            val timing = activity.card()
            fun ms(key: String) = json.optLong(key, -1).let { if (it < 0) "—" else "$it ms" }
            timing.value(null, "protocol", json.optString("protocol").ifEmpty { "—" })
            timing.value(null, "address lookup", ms("dns"))
            timing.value(null, "connecting", ms("connect"))
            timing.value(null, "first byte", ms("ttfb"))
            timing.value(null, "ready", ms("dom"))
            timing.value(null, "fully loaded", ms("load"))
            timing.value(null, "page size", json.optLong("transfer").let { if (it > 0) bytes(it) else "—" })
            area.put(activity.sectionTitle("TIMING"))
            area.put(timing.view)

            val content = activity.card()
            content.value(null, "elements", json.optInt("elements").toString())
            content.value(null, "scripts", json.optJSONArray("scripts")?.length()?.toString() ?: "0", json.optJSONArray("scripts")?.let { list -> (0 until list.length()).joinToString("\n") { list.optString(it) } }?.ifEmpty { null })
            content.value(null, "style sheets", json.optInt("styles").toString())
            content.value(null, "frames", json.optJSONArray("frames")?.length()?.toString() ?: "0", json.optJSONArray("frames")?.let { list -> (0 until list.length()).joinToString("\n") { list.optString(it) } }?.ifEmpty { null })
            content.value(null, "forms · images · links", "${json.optInt("forms")} · ${json.optInt("images")} · ${json.optInt("links")}")
            area.put(activity.sectionTitle("CONTENT"))
            area.put(content.view)

            val kept = activity.card()
            val cookies = Storage.cookies(activity.profile).getCookie(activity.pageUrl).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
            kept.value(null, "cookies", cookies.size.toString(), cookies.joinToString(", ").ifEmpty { null })
            kept.value(null, "local storage", json.optInt("local").let { if (it < 0) "blocked" else "$it keys · ${bytes(json.optLong("localSize") * 2)}" })
            kept.value(null, "session storage", json.optInt("session").let { if (it < 0) "blocked" else "$it keys" })
            area.put(activity.sectionTitle("STORED BY THE SITE"))
            area.put(kept.view)

            val secure = activity.card()
            if (cert == null) {
                secure.value(Glyphs.UNLOCK, "connection", if (activity.pageUrl.startsWith("https:")) "secure" else "not encrypted")
            } else {
                secure.value(Glyphs.LOCK, "issued to", cert.issuedTo?.cName.orEmpty().ifEmpty { "—" })
                secure.value(null, "issued by", cert.issuedBy?.oName?.ifEmpty { null } ?: cert.issuedBy?.cName.orEmpty())
                secure.value(null, "valid until", cert.validNotAfterDate?.let { java.text.DateFormat.getDateInstance().format(it) } ?: "—")
            }
            area.put(activity.sectionTitle("CERTIFICATE"))
            area.put(secure.view)
            area.put(copyRow { json.toString(2) })
        }
    }

    private fun browser() {
        val site = activity.site
        val webview = try {
            androidx.webkit.WebViewCompat.getCurrentWebViewPackage(activity)?.versionName.orEmpty()
        } catch (error: Throwable) {
            ""
        }
        activity.webView.evaluateJavascript(Inspector.PAGE) { value ->
            if (tab != "browser") return@evaluateJavascript
            val nav = try {
                JSONObject(JSONArray("[$value]").getString(0)).optJSONObject("nav") ?: JSONObject()
            } catch (error: Exception) {
                JSONObject()
            }
            while (area.childCount > 1) area.removeViewAt(1)
            val engine = activity.card()
            engine.value(null, "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            engine.value(null, "WebView", webview.ifEmpty { "unknown" })
            engine.value(null, "storage", activity.profile ?: "shared with apps without their own")
            engine.value(null, "separate storage", if (Storage.separate) "works" else "not here")
            engine.value(null, "early scripts", if (Storage.earlyScripts) "works" else "after load")
            engine.value(null, "sent as", activity.webView.settings.userAgentString)
            area.put(activity.sectionTitle("THIS PHONE’S BROWSER"))
            area.put(engine.view)

            val seen = activity.card()
            seen.value(null, "user agent", nav.optString("ua"))
            seen.value(null, "platform", nav.optString("platform"))
            seen.value(null, "cores · memory", "${nav.optInt("cores")} · ${nav.optInt("memory").let { if (it > 0) "$it GB" else "hidden" }}")
            seen.value(null, "screen · window", "${nav.optString("screen")} at ${nav.optDouble("dpr")}× · ${nav.optString("inner")}")
            seen.value(null, "timezone", nav.optString("tz"))
            seen.value(null, "languages", nav.optString("langs"))
            seen.value(null, "WebRTC", if (nav.optString("rtc") == "undefined") "off" else "on")
            seen.value(null, "touch points", nav.optInt("touch").toString())
            seen.value(null, "automation flag", if (nav.optBoolean("webdriver")) "set" else "not set")
            area.put(activity.sectionTitle("WHAT THIS PAGE SEES", null, "after fingerprinting protection"))
            area.put(seen.view)

            val leyden = activity.card()
            leyden.value(null, "blocking", if (activity.relaxed) "off for a check" else Blocker.level(site, activity.defaults, activity.pageUrl))
            leyden.value(null, "fingerprinting", if (activity.relaxed) "off for a check" else site.fp.summary(), Fingerprint.describe(activity.fpCounts).ifEmpty { null }?.let { "read here: $it" })
            leyden.value(null, "insertions", if (!site.power.insertionsOn || activity.safeOnce) "off" else plural(site.insertions.count { it.on }, "on"))
            leyden.value(null, "proxy", if (Proxy.covers(activity, activity.pageUrl)) "this site goes through it" else "direct")
            leyden.value(null, "session", if (activity.temp) Sessions.name(site.session) else "normal app")
            area.put(activity.sectionTitle("LEYDEN"))
            area.put(leyden.view)
            area.put(copyRow { nav.toString(2) + "\nsent as: " + activity.webView.settings.userAgentString })
        }
    }

    private fun console() {
        val lines = synchronized(activity.inspector.console) { activity.inspector.console.toList() }
        val box = activity.column(4)
        box.background = activity.surface()
        box.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
        if (lines.isEmpty()) box.put(activity.text("Nothing logged on this page.", 12f, T.muted))
        for (line in lines.takeLast(150)) {
            val tone = when (line.level) {
                "error" -> T.danger
                "warning" -> 0xFF8A6A00.toInt()
                "debug", "tip" -> T.faint
                else -> T.body
            }
            val view = activity.text(line.text, 11f, tone, Fonts.mono)
            view.setTextIsSelectable(true)
            box.put(view)
            if (line.source.isNotEmpty()) box.put(activity.text("${line.source.substringAfterLast('/').take(60)}:${line.line}", 9f, T.faint, Fonts.mono).oneLine())
        }
        area.put(box)
        val run = activity.line(8)
        val input = activity.field("", "Run JavaScript on this page", true, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        input.imeOptions = EditorInfo.IME_ACTION_GO
        val go = {
            val code = input.text.toString()
            if (code.isNotBlank()) {
                activity.inspector.console("log", "› $code", "", 0)
                activity.webView.evaluateJavascript("(function(){try{var r=eval(${JSONObject.quote(code)});return typeof r==='object'?JSON.stringify(r):String(r);}catch(e){return 'Error: '+e.message;}})()") { value ->
                    val shown = try {
                        JSONArray("[$value]").getString(0)
                    } catch (error: Exception) {
                        value.orEmpty()
                    }
                    activity.inspector.console(if (shown.startsWith("Error:")) "error" else "log", "‹ $shown", "", 0)
                    if (tab == "console") paint()
                }
            }
        }
        input.setOnEditorActionListener { _, _, _ ->
            go()
            true
        }
        run.put(input, 0, activity.dp(46), 1f)
        run.put(activity.squareKey(Glyphs.PLAY, "Run", 46) { go() }, activity.dp(46), activity.dp(46))
        area.put(run)
        area.put(activity.text("Runs in this page only, with its access. Don’t paste code you don’t understand.", 11f, T.faint))
        area.put(copyRow { lines.joinToString("\n") { "[${it.level}] ${it.text}" + if (it.source.isNotEmpty()) " (${it.source}:${it.line})" else "" } })
    }
}
