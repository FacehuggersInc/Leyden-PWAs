package org.bond.pwaapp

import android.annotation.SuppressLint
import android.app.Activity
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.util.Collections

class CheckResult {
    var ran = false
    var reason = ""
    var listsReady = false
    val trackers: MutableSet<String> = Collections.synchronizedSet(HashSet())
    val trackerDomains: MutableSet<String> = Collections.synchronizedSet(HashSet())
    val thirdHosts: MutableSet<String> = Collections.synchronizedSet(HashSet())
    var thirdCookies = 0
    @Volatile var mixed = 0
    @Volatile var safeBrowsing = false
    @Volatile var google = false
    @Volatile var loaded = false
    @Volatile var requests = 0
    @Volatile var phase = "opening"
    var startedAt = 0L
    @Volatile var shownAt = 0L
    @Volatile var loadedAt = 0L
    var scriptsOwn = 0
    var scriptsInline = 0
    val scriptHosts = LinkedHashMap<String, Int>()
    val fingerprinting: MutableMap<String, Int> = Collections.synchronizedMap(LinkedHashMap())

    fun record(): CheckRecord = CheckRecord(System.currentTimeMillis(), scriptsOwn, scriptsInline, LinkedHashMap(scriptHosts),
        scriptHosts.keys.filter { host -> trackerDomains.any { Filters.within(host, it) } }.toSet(), trackers.size, thirdCookies)
}

class SiteCheck(
    private val activity: Activity,
    private val parent: ViewGroup,
    private val address: String,
    private val trusted: String?,
    private val safeBrowsing: Boolean,
    private val progress: (CheckResult) -> Unit = {},
    private val done: (CheckResult) -> Unit,
) {
    private val result = CheckResult()
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var finished = false
    private val probe = Site(url = address, scope = "").apply { blocking = "standard" }
    private val noRules = Defaults()
    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            progress(result)
            main.postDelayed(this, 400)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (!Storage.separate) {
            result.reason = "Counting trackers and cookies needs Android System WebView 110 or newer."
            done(result)
            return
        }
        result.ran = true
        result.listsReady = Blocker.ready
        val web = WebView(activity)
        Storage.attach(web, PROFILE)
        view = web
        web.alpha = 0f
        web.isFocusable = false
        web.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        parent.addView(web, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.safeBrowsingEnabled = safeBrowsing
        web.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun fpSeen(json: String?) {
                val parsed = try {
                    org.json.JSONObject(json.orEmpty())
                } catch (error: Exception) {
                    return
                }
                for (name in parsed.keys()) result.fingerprinting[name] = (result.fingerprinting[name] ?: 0) + parsed.optInt(name)
            }
        }, "PwaApp")
        if (Storage.earlyScripts) {
            try {
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, Fingerprint.COUNT, setOf("*"))
            } catch (error: RuntimeException) {
                Unit
            }
        }
        val cookies = Storage.cookies(PROFILE)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)
        val home = hostOf(address)
        val secure = address.startsWith("https:")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                val host = request.url.host?.lowercase().orEmpty()
                if (host.isEmpty()) return null
                result.requests++
                if (host == "accounts.google.com") result.google = true
                if (secure && url.startsWith("http:")) result.mixed++
                if (!sameSite(host, home)) result.thirdHosts += host
                if (!request.isForMainFrame) {
                    val verdict = Blocker.check(probe, noRules, url, address, "standard")
                    if (verdict?.blocked == true) {
                        result.trackers += url.substringBefore('?')
                        result.trackerDomains += baseDomain(host)
                    }
                }
                return null
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                if (trusted != null && Trust.fingerprint(error.certificate) == trusted) handler.proceed() else handler.cancel()
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (result.shownAt == 0L) result.shownAt = System.currentTimeMillis()
                if (result.phase == "opening") result.phase = "loading"
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (result.loaded) return
                result.loaded = true
                result.loadedAt = System.currentTimeMillis()
                if (result.shownAt == 0L) result.shownAt = result.loadedAt
                result.phase = "settling"
                main.postDelayed({ finish() }, SETTLE)
            }

            override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
                if (request.isForMainFrame) result.safeBrowsing = true
                callback.backToSafety(false)
                if (request.isForMainFrame) main.post { finish() }
            }
        }
        main.postDelayed({ finish() }, TIMEOUT)
        result.startedAt = System.currentTimeMillis()
        web.loadUrl(address)
        main.post(tick)
    }

    fun stopEarly() {
        main.post { finish() }
    }

    fun cancel() {
        finished = true
        main.removeCallbacks(tick)
        cleanUp()
    }

    private fun finish() {
        if (finished) return
        finished = true
        result.phase = "counting"
        progress(result)
        val web = view
        if (web == null) {
            complete()
            return
        }
        web.evaluateJavascript("JSON.stringify([].map.call(document.scripts,function(s){return s.src||''}))") { value ->
            try {
                val list = org.json.JSONArray(org.json.JSONArray("[$value]").getString(0))
                val home = hostOf(address)
                for (index in 0 until list.length()) {
                    val source = list.optString(index)
                    if (source.isEmpty()) {
                        result.scriptsInline++
                        continue
                    }
                    val host = hostOf(source)
                    if (host.isEmpty() || sameSite(host, home)) result.scriptsOwn++ else result.scriptHosts[host] = (result.scriptHosts[host] ?: 0) + 1
                }
            } catch (error: Exception) {
                Unit
            }
            complete()
        }
    }

    private fun complete() {
        val cookies = Storage.cookies(PROFILE)
        var count = 0
        for (host in result.thirdHosts.toList()) {
            val text = cookies.getCookie("https://$host") ?: continue
            count += text.split(';').count { it.isNotBlank() }
        }
        result.thirdCookies = count
        cleanUp()
        done(result)
    }

    private fun cleanUp() {
        val web = view ?: return
        view = null
        web.stopLoading()
        parent.removeView(web)
        web.destroy()
        try {
            Storage.cookies(PROFILE).removeAllCookies(null)
            Storage.webStorage(PROFILE).deleteAllData()
        } catch (error: RuntimeException) {
            return
        }
    }

    companion object {
        const val PROFILE = "check"
        private const val SETTLE = 2500L
        private const val TIMEOUT = 15000L

        val supported: Boolean
            get() = Build.VERSION.SDK_INT >= 27
    }
}
