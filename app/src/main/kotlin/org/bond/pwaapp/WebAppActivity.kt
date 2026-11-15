package org.bond.pwaapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class Stage(context: Context) : FrameLayout(context) {
    var twoFinger = false
    var onHold: (() -> Unit)? = null
    var onTwoDown: (() -> Unit)? = null
    private var downStart = -1f
    private var downFired = false
    private var tracking = false
    private var triggered = false
    private var cancelSent = false
    private val start = FloatArray(4)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private val hold = Runnable {
        if (tracking) {
            tracking = false
            triggered = true
            cancelSent = false
            onHold?.invoke()
        }
    }

    private fun stopHold() {
        tracking = false
        removeCallbacks(hold)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val swipeDown = onTwoDown
        if (swipeDown != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                    downStart = (event.getY(0) + event.getY(1)) / 2f
                    downFired = false
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 2 && downStart >= 0 && !downFired) {
                    val now = (event.getY(0) + event.getY(1)) / 2f
                    if (now - downStart > context.dp(140)) {
                        downFired = true
                        removeCallbacks(hold)
                        tracking = false
                        swipeDown()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> downStart = -1f
            }
        }
        if (!twoFinger) return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                triggered = false
                stopHold()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    for (index in 0..1) {
                        start[index * 2] = event.getX(index)
                        start[index * 2 + 1] = event.getY(index)
                    }
                    tracking = true
                    postDelayed(hold, HOLD)
                } else {
                    stopHold()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && event.pointerCount >= 2) {
                    for (index in 0..1) {
                        val dx = event.getX(index) - start[index * 2]
                        val dy = event.getY(index) - start[index * 2 + 1]
                        if (dx * dx + dy * dy > slop * slop) stopHold()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopHold()
        }
        if (triggered) {
            if (!cancelSent) {
                cancelSent = true
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) triggered = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    companion object {
        private const val HOLD = 550L
    }
}

open class WebAppActivity : Activity() {
    lateinit var site: Site
    lateinit var defaults: Defaults
    lateinit var stage: Stage
    lateinit var webView: WebView
    lateinit var refresher: SwipeRefreshLayout
    private lateinit var column: LinearLayout
    private lateinit var progress: View
    lateinit var topBar: FrameLayout
    lateinit var bottomBar: FrameLayout
    private var pill: View? = null
    private var handle: View? = null
    private var splashView: SplashView? = null
    private var lockPanel: LockPanel? = null
    private lateinit var pageHolder: FrameLayout
    private var loadPage: (() -> Unit)? = null
    private var loaded = false
    private var pageVisible = false
    private var splashSince = 0L
    private var firstStart = true
    private var errorView: View? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var pendingResources: Array<String> = arrayOf()
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null
    private var pendingDownload: (() -> Unit)? = null
    private val handlers = mutableListOf<ScriptHandler>()
    private val main = Handler(Looper.getMainLooper())
    private var backCallback: Any? = null
    private var profile: String? = null
    private var sessionStart = 0L
    private var locked = false
    private var promptOnResume = false
    private var awaitingResult = false
    private var shownStyle = ""
    private var startedTheme = ""
    private var startedStorage = true
    private var lastDesktop = false
    var hider: Hider? = null

    @Volatile
    var pageUrl = ""

    @Volatile
    var snapshot: Site = Site()
    var pageTitle = ""
    var pageColor: Int? = null
    var allowOnce: String? = null
    val blockedHere = AtomicInteger()
    val fpCounts = linkedMapOf<String, Int>()
    val client by lazy { Client() }
    private val blockedUnsaved = AtomicInteger()
    private val cssCache = HashMap<String, String>()

    val outside: Boolean
        get() = pageUrl.startsWith("http") && !site.inScope(pageUrl)

    override fun onCreate(savedInstanceState: Bundle?) {
        Look.refresh(this)
        defaults = Defaults.get(this)
        val id = intent?.data?.lastPathSegment
        val found = if (intent?.data?.host == "temp") {
            intent.getStringExtra(Sessions.EXTRA)?.let { text -> try { Site.fromJson(org.json.JSONObject(text)) } catch (error: Exception) { null } }
        } else {
            SiteStore.get(this, id)
        }
        if (found == null) {
            setTheme(android.R.style.Theme_Material_Light_NoActionBar)
            super.onCreate(savedInstanceState)
            missing()
            return
        }
        site = found
        snapshot = found.copy()
        startedTheme = found.dark
        startedStorage = found.ownStorage
        lastDesktop = found.desktop
        setTheme(
            when (found.dark) {
                "dark" -> R.style.Theme_Web_Dark
                "light" -> R.style.Theme_Web_Light
                else -> R.style.Theme_Web
            }
        )
        super.onCreate(savedInstanceState)
        Bars.edgeToEdge(window, isLight(site.color))
        Blocker.ensure(this)
        build()
        setUpWeb()
        applySettings()
        describeTask()
        registerBack()
        val saved = savedInstanceState
        loadPage = {
            val wipe = site.privateMode || site.clearOnClose
            val go = {
                if (saved != null) webView.restoreState(saved) else (intent.getStringExtra(ADDRESS) ?: startAddress())?.let { webView.loadUrl(it) }
            }
            if (wipe && Storage.isDirty(this, site) && saved == null) {
                Storage.clearAll(this, site, webView) {
                    Storage.markDirty(this, site, true)
                    go()
                }
            } else {
                if (wipe) Storage.markDirty(this, site, true)
                go()
            }
        }
        val needsLock = LockState.needed(this, site)
        if ((site.splash && saved == null) || needsLock) showSplash()
        if (needsLock) lockNow() else begin()
        if (saved == null && !temp) MemoryStore.opened(this, site)
        takeExtras(intent)
        if (!temp) Updater.checkAll(this, site.id)
        if (temp && saved == null) toast(if (site.session == "private") "Private: nothing is kept, and closing it erases everything" else "Peek: closing it erases everything")
    }

    private fun missing() {
        val box = column(12).centered()
        box.setBackgroundColor(T.bg)
        box.setPadding(dp(24), dp(24), dp(24), dp(24))
        box.put(text("This app was removed", 18f, T.ink, Fonts.sansBold), WRAP_CONTENT)
        box.put(text("Add it again in Leyden.", 13f, T.muted), WRAP_CONTENT)
        setContentView(box)
    }

    private fun plainStart(): String {
        if (site.onOpen == "resume" && site.keeps(Kinds.RECENT)) {
            val last = MemoryStore.get(this, site.id).recent.firstOrNull()?.url
            if (last != null && site.inScope(last)) return last
        }
        return site.url
    }

    private fun startAddress(): String? {
        val address = plainStart()
        if (site.power.networks.isEmpty()) return address
        val choice = Net.choose(this, site)
        val base = choice.url
        if (base != null) {
            site.power.lastAddress = base
            SiteStore.save(this, site)
            return Net.swapOrigin(address, base)
        }
        if (choice.ask) {
            stage.post { askAddress(address) }
            return null
        }
        if (site.power.elseMode == "none") {
            stage.post { showError("None of ${site.label}’s networks are here, so it wasn’t opened. Change this in its settings › address by network.") }
            return null
        }
        return address
    }

    private fun askAddress(address: String) {
        val sheet = Sheet(this)
        sheet.body.put(text("Which address?", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("None of ${site.label}’s networks are here right now.", 13f, T.muted))
        val list = card()
        val options = (site.power.networks.map { it.url } + site.url).filter { it.isNotEmpty() }.distinct()
        for (option in options) list.link(Glyphs.ROUTE, option, null, if (option == site.power.lastAddress) "used last" else null) {
            sheet.dismiss()
            site.power.lastAddress = option
            SiteStore.save(this, site)
            webView.loadUrl(Net.swapOrigin(address, option))
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun build() {
        stage = Stage(this)
        stage.setBackgroundColor(site.color)
        stage.onHold = { QuickSheet(this).show() }
        column = column()
        topBar = FrameLayout(this)
        topBar.hide()
        column.put(topBar, MATCH_PARENT, WRAP_CONTENT)
        val holder = FrameLayout(this)
        pageHolder = holder
        refresher = SwipeRefreshLayout(this)
        refresher.setColorSchemeColors(T.accent)
        refresher.setProgressBackgroundColorSchemeColor(T.surface)
        webView = WebView(this)
        profile = Storage.profileName(site)
        Storage.attach(webView, profile)
        refresher.addView(webView, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        refresher.setOnRefreshListener { webView.reload() }
        refresher.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        holder.addView(refresher, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        progress = View(this)
        progress.setBackgroundColor(T.accent)
        progress.pivotX = 0f
        progress.scaleX = 0f
        holder.addView(progress, FrameLayout.LayoutParams(MATCH_PARENT, dp(2)))
        column.put(holder, MATCH_PARENT, 0, 1f)
        bottomBar = FrameLayout(this)
        bottomBar.hide()
        column.put(bottomBar, MATCH_PARENT, WRAP_CONTENT)
        stage.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        stage.setOnApplyWindowInsetsListener { view, insets ->
            val edges = Bars.edges(insets, !site.fullScreen && customView == null)
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            insets
        }
        setContentView(stage)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWeb() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        webView.addJavascriptInterface(Bridge(), "PwaApp")
        webView.webViewClient = client
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { url, agent, disposition, mime, length -> download(url, agent, disposition, mime, length) }
        webView.setOnLongClickListener { longPress() }
        installScripts()
    }

    var relaxed = false
    private var relaxLoads = 0
    private var relaxChip: View? = null
    private var agentHidden: Boolean? = null

    private fun wantsHidden(url: String?): Boolean = site.fp.model && !relaxed && !Relax.matches(site, url)

    private fun applyAgent(url: String?) {
        val settings = webView.settings
        val hidden = wantsHidden(url)
        val shape = Fingerprint.fromJson(site.fp.toJson()).also { it.model = hidden }
        val agent = WebSettings.getDefaultUserAgent(this)
        settings.userAgentString = if (site.desktop) desktopAgent(Fingerprint.agent(agent, shape)) else Fingerprint.agent(agent, shape)
        Fingerprint.native(settings, shape)
        agentHidden = hidden
    }

    private fun fixAgent(url: String): Boolean {
        if (wantsHidden(url) == agentHidden) return false
        applyAgent(url)
        return true
    }

    fun relaxNow(loads: Int) {
        relaxed = true
        relaxLoads = loads
        cssCache.clear()
        applyAgent(pageUrl)
        installScripts()
        showRelaxChip()
        webView.reload()
    }

    fun protectAgain(reload: Boolean) {
        if (!relaxed) return
        relaxed = false
        relaxLoads = 0
        cssCache.clear()
        applyAgent(pageUrl)
        installScripts()
        relaxChip?.let { stage.removeView(it) }
        relaxChip = null
        if (reload) webView.reload() else toast("Protections are back on from the next page")
    }

    private fun showRelaxChip() {
        relaxChip?.let { stage.removeView(it) }
        val chip = line(8)
        chip.background = surface(16f)
        chip.elevation = dpf(6f)
        chip.setPadding(dp(12), dp(6), dp(6), dp(6))
        chip.put(glyph(Glyphs.SHIELD, T.danger, 14, 2f), dp(14), dp(14))
        chip.put(text("Protections lowered for a security check", 12f, T.ink).oneLine(), 0, WRAP_CONTENT, 1f)
        chip.put(primaryButtonTiny("Protect again") { protectAgain(false) }, WRAP_CONTENT, dp(32))
        stage.addView(chip, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply { setMargins(dp(10), dp(10) + topInset(), dp(10), 0) })
        relaxChip = chip
    }

    private fun primaryButtonTiny(label: String, onClick: () -> Unit): View {
        val view = text(label, 12f, T.onAccent, Fonts.sansMedium)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(12), 0, dp(12), dp(1))
        view.background = primaryBack(9f)
        view.isClickable = true
        view.setOnClickListener { onClick() }
        return view
    }

    fun applySettings() {
        val settings = webView.settings
        settings.textZoom = site.textSize
        settings.setSupportZoom(site.zoom)
        settings.builtInZoomControls = site.zoom
        settings.safeBrowsingEnabled = defaults.checkSafeBrowsing
        settings.setGeolocationEnabled(site.location != "never")
        applyAgent(pageUrl.ifEmpty { site.url })
        if (Build.VERSION.SDK_INT >= 33) {
            settings.isAlgorithmicDarkeningAllowed = site.dark == "dark"
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            settings.forceDark = if (site.dark == "dark") WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
        Storage.cookies(profile).setAcceptCookie(true)
        Storage.cookies(profile).setAcceptThirdPartyCookies(webView, site.thirdPartyCookies)
        requestedOrientation = when (site.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (site.screenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applySecure()
        Bars.fullScreen(window, site.fullScreen && customView == null)
        stage.requestApplyInsets()
        refresher.isEnabled = site.pullToRefresh
        stage.onTwoDown = if (site.power.swipeDown.value != "nothing" || site.power.swipeDown.kind != "builtin") ({ perform(site.power.swipeDown) }) else null
        restoreTouch()
        showButtons()
        val gesture = site.gestureNow(defaults)
        stage.twoFinger = gesture == "two" || gesture == "both"
        if (gesture == "handle" || gesture == "both") showHandle() else removeHandle()
        paintBars()
        updateChrome()
    }

    private fun describeTask() {
        val bitmap: Bitmap? = SiteStore.face(this, site)
        @Suppress("DEPRECATION")
        val label = if (temp) Sessions.name(site.session) + " · " + site.label else site.label
        setTaskDescription(ActivityManager.TaskDescription(label, bitmap, if (temp) 0xFF1C1C1A.toInt() else site.color or 0xFF000000.toInt()))
    }

    fun barColor(): Int = if (site.barColor == "page" && !outside) pageColor ?: site.color else site.color

    fun paintBars() {
        val color = barColor()
        stage.setBackgroundColor(color)
        Bars.appearance(window, isLight(color))
    }

    private fun registerBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { back() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!this::site.isInitialized) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
            return
        }
        back()
    }

    private fun back() {
        if (!this::webView.isInitialized) {
            finish()
            return
        }
        val picking = hider
        if (picking != null) {
            picking.stop()
            return
        }
        if (customView != null) {
            customCallback?.onCustomViewHidden()
            return
        }
        if (site.backGoesBack && webView.canGoBack()) {
            webView.goBack()
            return
        }
        moveTaskToBack(true)
    }

    fun returnToApp() {
        val list = webView.copyBackForwardList()
        for (index in list.currentIndex - 1 downTo 0) {
            if (site.inScope(list.getItemAtIndex(index).url)) {
                webView.goBackOrForward(index - list.currentIndex)
                return
            }
        }
        webView.loadUrl(site.url)
    }

    val temp: Boolean
        get() = this::site.isInitialized && site.session.isNotEmpty()

    fun reloadSite() {
        if (temp) return
        val fresh = SiteStore.get(this, site.id) ?: return
        site = fresh
        snapshot = fresh.copy()
        defaults = Defaults.get(this)
        cssCache.clear()
    }

    fun saveSite() {
        if (temp) {
            intent.putExtra(Sessions.EXTRA, site.toJson().toString())
            snapshot = site.copy()
            cssCache.clear()
            return
        }
        SiteStore.save(this, site)
        snapshot = site.copy()
        cssCache.clear()
    }

    fun needsRestart(): Boolean = site.dark != startedTheme || site.ownStorage != startedStorage

    fun afterChange() {
        saveSite()
        if (needsRestart()) {
            recreate()
            return
        }
        applySettings()
        if (site.desktop != lastDesktop) {
            lastDesktop = site.desktop
            webView.reload()
        }
    }

    private val token = java.util.UUID.randomUUID().toString()
    private val mediaKey = java.util.UUID.randomUUID().toString()
    var safeOnce = false
    private var networkLabel = ""
    private val noted = HashSet<String>()
    private var openedFired = false
    private var unlockOnLoad = false
    private var pendingRun: String? = null
    private var pendingSelect: String? = null
    private var pendingTest: String? = null
    var testPanel: TestPanel? = null
    var adopted = false
    private var stepUpShown = false

    private val pendingFiles = mutableListOf<Uri>()

    private fun takeExtras(intent: Intent) {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(FILES_SHARED)?.let { pendingFiles += it }
        intent.getStringExtra(Shortcuts.RUN)?.let { pendingRun = it }
        intent.getStringExtra(Shortcuts.SELECT)?.let { pendingSelect = it }
        intent.getStringExtra(Shortcuts.TEST)?.let { pendingTest = it }
    }

    private fun pageScripts(): List<Pair<Boolean, String>> {
        val out = mutableListOf<Pair<Boolean, String>>()
        val power = site.power
        val key = "window.__pwaMediaKey=${org.json.JSONObject.quote(mediaKey)};"
        if (power.audio || power.pip || site.keeps(Kinds.PLAYING) || temp) out += true to key + Media.SHIM
        if (!power.insertionsOn || safeOnce) return out
        if (power.builtinPip) out += true to key + Media.PIP
        if (site.fp.active) out += true to Fingerprint.COUNT
        if (site.fp.needsScript && !relaxed) out += true to Fingerprint.script(site.fp, Relax.skips(site))
        if (power.builtinLists) out += true to Scripts.BOOT
        if (power.builtinLogin) out += true to Scripts.LOGIN
        if (power.builtinHidden && site.hidden.isNotEmpty()) out += false to Scripts.hidden(site)
        if (site.insertions.any { it.on }) {
            out += false to Runtime.core(site, token, networkLabel)
            for (item in site.insertions) if (item.on && item.kind != "css") out += false to Runtime.register(item)
            out += false to Runtime.START
        }
        return out
    }

    fun installScripts() {
        handlers.forEach { it.remove() }
        handlers.clear()
        networkLabel = Net.label(this, site)
        if (!Storage.earlyScripts) return
        for ((everywhere, script) in pageScripts()) {
            handlers += WebViewCompat.addDocumentStartJavaScript(webView, script, if (everywhere) setOf("*") else Scripts.origins(site))
        }
    }

    private fun fallbackScripts(url: String) {
        if (Storage.earlyScripts) return
        for ((everywhere, script) in pageScripts()) {
            if (everywhere || site.inScope(url)) webView.evaluateJavascript(script, null)
        }
    }

    fun fire(kind: String, value: String = "") {
        if (this::webView.isInitialized) webView.evaluateJavascript(Runtime.fire(kind, value), null)
    }

    fun runInsertion(id: String) {
        webView.evaluateJavascript(Runtime.runOne(id), null)
    }

    fun testInsertion(id: String) {
        reloadSite()
        val item = site.insertions.firstOrNull { it.id == id } ?: run {
            toast("That insertion is gone")
            return
        }
        val panel = testPanel?.takeIf { it.matches(id) } ?: TestPanel(this, id).also { made ->
            testPanel?.close()
            testPanel = made
            made.show()
        }
        panel.restart()
        val core = Runtime.core(site, token, networkLabel)
        webView.evaluateJavascript("if(!window.__pwaRT){$core}", null)
        if (item.kind != "css") webView.evaluateJavascript(Runtime.register(item), null)
        webView.evaluateJavascript(Runtime.test(Runtime.meta(site, item)), null)
    }

    fun openWithoutInsertions() {
        safeOnce = true
        installScripts()
        webView.reload()
        toast("Opening once without insertions")
    }

    private fun failedInsertion(id: String, failure: Failure) {
        val item = site.insertions.firstOrNull { it.id == id } ?: return
        val name = item.name.ifEmpty { item.kindName }
        when (item.onFail) {
            "off" -> {
                item.on = false
                saveSite()
                installScripts()
                toast("Turned off “$name”: ${failure.message}")
            }
            "note" -> if (noted.add(id)) toast("“$name” failed" + (if (failure.line > 0) " on line ${failure.line}" else "") + ": ${failure.message}")
        }
    }

    private fun canUse(item: Insertion, grant: String): Boolean =
        item.source == null || item.source?.detached == true || item.grants.any { it.lowercase().replace(".", "_").contains(grant) }

    fun listCss(href: String): String {
        val host = hostOf(href)
        if (host.isEmpty()) return ""
        val level = if (relaxed) "off" else Blocker.level(snapshot, defaults, pageUrl.ifEmpty { href })
        val key = "$level|$host"
        synchronized(cssCache) {
            cssCache[key]?.let { return it }
        }
        val css = Blocker.css(snapshot, defaults, level, host)
        synchronized(cssCache) {
            if (cssCache.size > 30) cssCache.clear()
            cssCache[key] = css
        }
        return css
    }

    inner class Bridge {
        @JavascriptInterface
        fun css(href: String?): String = if (href == null) "" else listCss(href)

        private fun item(key: String?, id: String?): Insertion? = if (key != token || id == null) null else snapshot.insertions.firstOrNull { it.id == id }

        @JavascriptInterface
        fun gmGet(key: String?, id: String?, name: String?): String? {
            val item = item(key, id) ?: return null
            if (!canUse(item, "getvalue") && !canUse(item, "setvalue")) return null
            return AddonState.getValue(applicationContext, snapshot.id, item.id, name.orEmpty())
        }

        @JavascriptInterface
        fun gmSet(key: String?, id: String?, name: String?, value: String?) {
            val item = item(key, id) ?: return
            if (canUse(item, "setvalue")) AddonState.setValue(applicationContext, snapshot.id, item.id, name.orEmpty(), value)
        }

        @JavascriptInterface
        fun gmDel(key: String?, id: String?, name: String?) {
            val item = item(key, id) ?: return
            if (canUse(item, "deletevalue") || canUse(item, "setvalue")) AddonState.setValue(applicationContext, snapshot.id, item.id, name.orEmpty(), null)
        }

        @JavascriptInterface
        fun gmList(key: String?, id: String?): String {
            val item = item(key, id) ?: return "[]"
            return org.json.JSONArray(AddonState.keys(applicationContext, snapshot.id, item.id)).toString()
        }

        @JavascriptInterface
        fun gmClip(key: String?, id: String?, text: String?) {
            val item = item(key, id) ?: return
            if (canUse(item, "clipboard")) runOnUiThread { copy(text.orEmpty()) }
        }

        @JavascriptInterface
        fun gmOpen(key: String?, id: String?, url: String?) {
            val item = item(key, id) ?: return
            val address = url ?: return
            if (!canUse(item, "openintab") || !address.startsWith("http")) return
            runOnUiThread { if (site.inScope(address)) webView.loadUrl(address) else openBrowser(address) }
        }

        @JavascriptInterface
        fun gmFetch(key: String?, id: String?, request: Int, json: String?) {
            val item = item(key, id) ?: return
            val reply = { result: org.json.JSONObject -> runOnUiThread { webView.evaluateJavascript("window.__pwaGMReply&&__pwaGMReply($request,$result)", null) } }
            val options = try {
                org.json.JSONObject(json.orEmpty())
            } catch (error: Exception) {
                return
            }
            val url = options.optString("url")
            val host = hostOf(url)
            val allowed = canUse(item, "xmlhttprequest") && host.isNotEmpty() && (item.source == null || item.source?.detached == true ||
                sameSite(host, snapshot.host) || item.connects.any { it == "*" || it == "self" && sameSite(host, snapshot.host) || Filters.within(host, it.lowercase()) })
            if (!allowed) {
                reply(org.json.JSONObject().put("error", "not allowed: $host isn’t in its @connect list"))
                return
            }
            Thread { reply(Fetcher.gm(options, Storage.cookies(profile).getCookie(url))) }.start()
        }

        @JavascriptInterface
        fun failed(key: String?, id: String?, message: String?, line: Int) {
            val item = item(key, id) ?: return
            val failure = AddonState.failed(applicationContext, snapshot.id, item.id, message.orEmpty().take(200), (line - 1).coerceAtLeast(0))
            runOnUiThread { failedInsertion(item.id, failure) }
        }

        @JavascriptInterface
        fun media(key: String?, state: String?, title: String?, artist: String?, kind: String?) {
            if (key != mediaKey) return
            runOnUiThread { mediaChanged(state == "playing", title.orEmpty(), artist.orEmpty(), kind.orEmpty()) }
        }

        @JavascriptInterface
        fun ranToday(key: String?, id: String?): Boolean {
            val item = item(key, id) ?: return false
            return AddonState.ranToday(applicationContext, snapshot.id, item.id)
        }

        @JavascriptInterface
        fun pip(key: String?, width: Int, height: Int): Boolean {
            if (key != mediaKey || !canFloat()) return false
            runOnUiThread { float(width, height) }
            return true
        }

        @JavascriptInterface
        fun fpSeen(json: String?) {
            val parsed = try {
                org.json.JSONObject(json.orEmpty())
            } catch (error: Exception) {
                return
            }
            runOnUiThread {
                for (name in parsed.keys()) fpCounts[name] = (fpCounts[name] ?: 0) + parsed.optInt(name)
            }
        }

        @JavascriptInterface
        fun field(kind: String?) {
            runOnUiThread { loginField(kind.orEmpty()) }
        }

        @JavascriptInterface
        fun status(key: String?, id: String?, state: String?, text: String?) {
            if (key != token || id == null) return
            runOnUiThread { testPanel?.update(id, state.orEmpty(), text.orEmpty().take(200)) }
        }

        @JavascriptInterface
        fun ran(key: String?, id: String?) {
            val item = item(key, id) ?: return
            AddonState.markRan(applicationContext, snapshot.id, item.id)
        }
    }

    private fun showSplash() {
        if (splashView != null) return
        val view = SplashView(this, site)
        stage.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        splashView = view
    }

    private fun lockNow() {
        locked = true
        showSplash()
        val view = splashView ?: return
        view.alpha = 1f
        val panel = LockPanel(this, site, view.ink, true) {
            stage.post {
                val wasLoaded = loaded
                begin()
                if (wasLoaded) fire("unlock") else unlockOnLoad = true
            }
        }
        lockPanel = panel
        view.showLock(panel.view)
        applySecure()
        promptOnResume = true
    }

    private fun begin() {
        locked = false
        lockPanel = null
        applySecure()
        splashView?.let { view ->
            view.showLoading(site.host)
            splashSince = System.currentTimeMillis()
        }
        if (!loaded) {
            loaded = true
            loadPage?.invoke()
        } else {
            pageVisible = true
        }
        if (pageVisible) pageShown()
        main.postDelayed({ reveal() }, 8000)
    }

    private fun pageShown() {
        pageVisible = true
        if (locked || splashView == null) return
        val wait = if (site.splash) site.splashAtLeast - (System.currentTimeMillis() - splashSince) else 0L
        main.postDelayed({ reveal() }, wait.coerceAtLeast(0L))
    }

    private fun reveal() {
        if (locked) return
        val view = splashView ?: return
        splashView = null
        when {
            site.reveal == "cut" -> stage.removeView(view)
            site.reveal == "blur" && Build.VERSION.SDK_INT >= 31 -> {
                val target = pageHolder
                val blur = android.animation.ValueAnimator.ofFloat(28f, 0f)
                blur.duration = 560
                blur.addUpdateListener {
                    val radius = it.animatedValue as Float
                    target.setRenderEffect(if (radius < 0.5f) null else android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP))
                }
                blur.start()
                view.animate().alpha(0f).setDuration(420).withEndAction { stage.removeView(view) }.start()
            }
            else -> view.animate().alpha(0f).setDuration(300).withEndAction { stage.removeView(view) }.start()
        }
    }

    private fun applySecure() {
        if ((site.locks && site.hideRecents) || site.privateMode || (temp && site.sessionSecure)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun showHandle() {
        if (handle != null) return
        val tab = FrameLayout(this)
        val r = dpf(10f)
        tab.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(T.surface)
            cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            setStroke(dp(1), T.border)
        }
        tab.alpha = 0.88f
        tab.contentDescription = "Quick settings for ${site.label}"
        tab.isClickable = true
        tab.setOnClickListener { QuickSheet(this).show() }
        val grip = View(this)
        grip.background = rounded(T.switchOff, dpf(2f))
        tab.addView(grip, FrameLayout.LayoutParams(dp(4), dp(28), Gravity.CENTER))
        stage.addView(tab, FrameLayout.LayoutParams(dp(18), dp(64), Gravity.END or Gravity.CENTER_VERTICAL))
        handle = tab
    }

    private fun removeHandle() {
        handle?.let { stage.removeView(it) }
        handle = null
    }

    fun updateChrome() {
        if (!this::webView.isInitialized) return
        val away = outside
        val style = if (away) site.navStyleNow(defaults) else ""
        if (style != shownStyle) {
            pill?.let { stage.removeView(it) }
            pill = null
            topBar.removeAllViews()
            bottomBar.removeAllViews()
            topBar.hide()
            bottomBar.hide()
            shownStyle = style
        }
        when (style) {
            "pill" -> {
                pill?.let { stage.removeView(it) }
                val made = Nav.pill(this)
                val spot = splashView?.let { stage.indexOfChild(it) } ?: -1
                stage.addView(made, spot, FrameLayout.LayoutParams(WRAP_CONTENT, dp(56), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(14) })
                pill = made
            }
            "top" -> {
                topBar.removeAllViews()
                topBar.addView(Nav.bar(this, true), FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                topBar.show()
            }
            "bottom" -> {
                bottomBar.removeAllViews()
                bottomBar.addView(Nav.bar(this, false), FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                bottomBar.show()
            }
        }
        paintBars()
    }

    private fun showError(description: String) {
        hideError()
        val box = column(10).centered()
        box.setBackgroundColor(T.bg)
        box.isClickable = true
        box.setPadding(dp(28), dp(28), dp(28), dp(28))
        box.put(glyph(Glyphs.WARN, T.muted, 32), dp(32), dp(32))
        box.put(text("The page didn’t load", 17f, T.ink, Fonts.sansBold), WRAP_CONTENT)
        val detail = text(description, 13f, T.muted)
        detail.gravity = Gravity.CENTER
        box.put(detail, WRAP_CONTENT)
        box.put(primaryButton("Try again", Glyphs.RELOAD) {
            hideError()
            webView.reload()
        }, dp(200), dp(52))
        if (!relaxed) {
            box.put(secondaryButton("Lower them just for a security check", Glyphs.SHIELD) {
                hideError()
                relaxNow(2)
            }, WRAP_CONTENT, dp(48))
        }
        if (!StepUp.isLowest(site)) {
            box.put(secondaryButton("Try with lowest protections", Glyphs.SHIELD) {
                hideError()
                StepUp.lowest(site)
                saveSite()
                applySettings()
                installScripts()
                stepUpShown = false
                webView.reload()
            }, WRAP_CONTENT, dp(48))
            box.put(text("Turns off ad blocking and fingerprinting protection, then lets you turn them back on one at a time.", 12f, T.muted).apply { gravity = Gravity.CENTER }, WRAP_CONTENT)
        }
        box.put(quietButton("Open in the browser", Glyphs.BROWSER) { openBrowser(webView.url ?: site.url) }, WRAP_CONTENT, dp(44))
        (refresher.parent as ViewGroup).addView(box, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        errorView = box
    }

    private fun hideError() {
        errorView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        errorView = null
    }

    fun openBrowser(url: String) {
        openOutside(Rewrite.apply(defaults.rewrites, url, "outgoing", site.id))
    }

    private var keyChip: View? = null
    private val hideChip = Runnable {
        keyChip?.let { stage.removeView(it) }
        keyChip = null
    }

    private fun loginField(kind: String) {
        main.removeCallbacks(hideChip)
        if (kind.isEmpty()) {
            main.postDelayed(hideChip, 400)
            return
        }
        if (keyChip != null || hider != null) return
        val chip = secondaryButton("passwords", Glyphs.LOCK) { Passwords.fill(this, webView) }
        chip.elevation = dpf(6f)
        stage.addView(chip, FrameLayout.LayoutParams(WRAP_CONTENT, dp(40), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(12), dp(10)) })
        keyChip = chip
    }

    fun openLink(url: String, gesture: Boolean) {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            openOutside(url)
            return
        }
        val target = Rewrite.apply(defaults.rewrites, url, "inside", site.id)
        if (site.inScope(target) || site.keptInside(target)) {
            allowOnce = target
            webView.loadUrl(target)
            return
        }
        if (!outside) {
            when (site.linksNow(defaults)) {
                "browser" -> {
                    openBrowser(target)
                    return
                }
                "ask" -> {
                    Warnings.where(this, target, gesture)
                    return
                }
            }
        }
        val warning = Warnings.check(this, target, gesture, false)
        if (warning != null) {
            Warnings.show(this, target, warning)
            return
        }
        allowOnce = target
        webView.loadUrl(target)
    }

    fun openOutside(url: String) {
        val uri = Uri.parse(url)
        val intent = if (uri.scheme == "intent") {
            try {
                val parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                parsed.addCategory(Intent.CATEGORY_BROWSABLE)
                parsed.component = null
                parsed.selector = null
                parsed
            } catch (error: Exception) {
                null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (intent == null) return
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: ActivityNotFoundException) {
            val fallback = if (uri.scheme == "intent") intent.getStringExtra("browser_fallback_url") else null
            if (fallback != null && fallback.startsWith("http")) webView.loadUrl(fallback) else toast("No app on this phone opens that link")
        }
    }

    fun share(url: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        if (!title.isNullOrEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title)
        startActivity(Intent.createChooser(send, null))
    }

    fun copy(url: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
        if (Build.VERSION.SDK_INT < 33) toast("Link copied")
    }

    private fun longPress(): Boolean {
        if (hider != null) return true
        val hit = webView.hitTestResult
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                val url = hit.extra ?: return false
                LinkSheet(this, url, null).show()
                true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val image = hit.extra
                val reply = Handler(Looper.getMainLooper()) { answer ->
                    val url = answer.data.getString("url") ?: image
                    if (url != null) LinkSheet(this, url, image).show()
                    true
                }
                webView.requestFocusNodeHref(reply.obtainMessage())
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                val url = hit.extra ?: return false
                if (!url.startsWith("http")) return false
                LinkSheet(this, url, url).show()
                true
            }
            else -> false
        }
    }

    fun startHider(fx: Float? = null, fy: Float? = null, forItem: String? = null) {
        if (hider != null) return
        val picking = Hider(this, forItem)
        hider = picking
        picking.start(fx, fy)
    }

    fun lastTouch(): Pair<Float, Float>? = touch

    private var touch: Pair<Float, Float>? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && this::webView.isInitialized) {
            val spot = IntArray(2)
            webView.getLocationOnScreen(spot)
            if (webView.width > 0 && webView.height > 0) {
                touch = ((event.rawX - spot[0]) / webView.width) to ((event.rawY - spot[1]) / webView.height)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    var mediaPlaying = false
    var mediaKind = ""
    private var buttonsView: View? = null
    private var network: android.net.ConnectivityManager.NetworkCallback? = null
    private var keyDownAt = 0L
    private var keyHeld = false

    private val swipes by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(first: MotionEvent?, last: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = first ?: return false
                val dx = last.x - start.x
                val dy = last.y - start.y
                val edge = dp(28)
                if (start.x < edge || start.x > webView.width - edge) return false
                if (kotlin.math.abs(dx) < dp(110) || kotlin.math.abs(dy) > kotlin.math.abs(dx) * 0.6f || kotlin.math.abs(velocityX) < 800) return false
                val action = if (dx < 0) site.power.swipeLeft else site.power.swipeRight
                if (action.kind == "builtin" && action.value == "nothing") return false
                perform(action)
                return true
            }
        })
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun restoreTouch() {
        if (hider != null) return
        val power = site.power
        val active = listOf(power.swipeLeft, power.swipeRight).any { !(it.kind == "builtin" && it.value == "nothing") }
        webView.setOnTouchListener(if (active) View.OnTouchListener { _, event ->
            swipes.onTouchEvent(event)
            false
        } else null)
    }

    fun perform(action: Action) {
        when (action.kind) {
            "button" -> site.power.buttons.firstOrNull { it.id == action.value }?.let { press(it) }
            "insertion" -> runInsertion(action.value)
            else -> when (action.value) {
                "pageup" -> webView.pageUp(false)
                "pagedown" -> webView.pageDown(false)
                "back" -> if (webView.canGoBack()) webView.goBack()
                "forward" -> if (webView.canGoForward()) webView.goForward()
                "reload" -> webView.reload()
                "top" -> webView.pageUp(true)
                "playpause" -> webView.evaluateJavascript(Media.action(if (mediaPlaying) "pause" else "play"), null)
                "quick" -> QuickSheet(this).show()
                "screenshot" -> screenshot()
            }
        }
    }

    fun press(button: Btn) {
        when (button.does) {
            "open" -> SiteLookup.resolveOrNull(site.url, button.value)?.let { webView.loadUrl(it) }
            "script" -> runInsertion(button.value)
            else -> {
                val target = org.json.JSONObject.quote(button.value)
                webView.evaluateJavascript("(function(){var s=$target;var t=/^text([*^]?):([a-z0-9*-]+)\\|([\\s\\S]*)$/i.exec(s);var e=null;if(t){e=[].filter.call(document.querySelectorAll(t[2]),function(x){var w=(x.innerText||x.value||x.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim();return t[1]==='^'?w.indexOf(t[3])===0:t[1]==='*'?w.indexOf(t[3])>=0:w===t[3];})[0];}else{try{e=document.querySelector(s);}catch(x){}}if(e){e.click();return 1;}return 0;})()") { found ->
                    if (found == "0") toast("“${button.name}” didn’t find anything to click here")
                }
            }
        }
        fire("button", button.id)
    }

    fun showButtons() {
        buttonsView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        buttonsView = null
        val floating = site.power.buttons.filter { it.show == "floating" }
        val toolbar = site.power.buttons.filter { it.show == "toolbar" }
        if (floating.isEmpty() && toolbar.isEmpty()) return
        if (toolbar.isNotEmpty()) {
            val strip = android.widget.HorizontalScrollView(this)
            strip.setBackgroundColor(T.surface)
            val row = line(8)
            row.setPadding(dp(10), dp(6), dp(10), dp(6))
            for (button in toolbar) row.put(secondaryButton(button.name, Glyphs.HAND) { press(button) }, WRAP_CONTENT, dp(40))
            strip.addView(row)
            column.addView(strip, column.indexOfChild(bottomBar), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            buttonsView = strip
            return
        }
        val stack = column(10)
        stack.gravity = Gravity.END
        for (button in floating) {
            val chip = line(8)
            chip.background = rounded(T.surface, dpf(22f))
            chip.elevation = dpf(6f)
            chip.setPadding(dp(14), 0, dp(16), 0)
            chip.put(glyph(Glyphs.HAND, T.accent, 16, 2f), dp(16), dp(16))
            chip.put(text(button.name, 13f, T.ink, Fonts.sansBold), WRAP_CONTENT)
            chip.isClickable = true
            chip.contentDescription = button.name
            chip.setOnClickListener { press(button) }
            stack.put(chip, WRAP_CONTENT, dp(44))
        }
        val spot = splashView?.let { stage.indexOfChild(it) } ?: -1
        stage.addView(stack, spot, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(14), dp(84))
        })
        buttonsView = stack
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if (this::site.isInitialized && site.power.keysOn && !locked && (code == android.view.KeyEvent.KEYCODE_VOLUME_UP || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val action = if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP) site.power.volumeUp else site.power.volumeDown
            val manager = getSystemService(android.media.AudioManager::class.java)
            when (event.action) {
                android.view.KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        keyDownAt = System.currentTimeMillis()
                        keyHeld = false
                    } else if (event.repeatCount >= 2) {
                        keyHeld = true
                        manager?.adjustSuggestedStreamVolume(if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.USE_DEFAULT_STREAM_TYPE, android.media.AudioManager.FLAG_SHOW_UI)
                    }
                    return true
                }
                android.view.KeyEvent.ACTION_UP -> {
                    if (!keyHeld) {
                        perform(action)
                        fire("key", if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP) "up" else "down")
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    var mediaTitle = ""
    var mediaArtist = ""

    private fun mediaChanged(playing: Boolean, title: String, artist: String, kind: String) {
        mediaPlaying = playing
        mediaKind = kind
        mediaTitle = title
        mediaArtist = artist
        NowPlaying.update(site, title, artist, playing, kind, hostOf(pageUrl))
        if (!site.power.audio) return
        if (playing || audioOwner == site.id) {
            audioOwner = site.id
            AudioService.controller = { action -> runOnUiThread { webView.evaluateJavascript(Media.action(action), null) } }
            AudioService.update(this, site.label, title.ifEmpty { site.label }, artist, playing)
        }
    }

    private fun canFloat(): Boolean =
        this::site.isInitialized && !locked && !isInPictureInPictureMode && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) && this !is BubbleActivity

    private fun float(width: Int, height: Int): Boolean {
        if (!canFloat()) return false
        val ratio = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
        val clamped = ratio.coerceIn(1f / 2.39f, 2.39f)
        val rational = android.util.Rational((clamped * 1000).toInt(), 1000)
        return try {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(rational).build())
        } catch (error: IllegalStateException) {
            false
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (this::site.isInitialized && site.power.pip && mediaPlaying && mediaKind == "video" && canFloat()) {
            if (customView == null && site.power.insertionsOn && site.power.builtinPip && !safeOnce) webView.evaluateJavascript(Media.FILL, null)
            float(16, 9)
        }
    }

    private val hiddenForPip = mutableListOf<View>()

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hiddenForPip.clear()
            for (index in 0 until stage.childCount) {
                val child = stage.getChildAt(index)
                if (child !== webView && child !== customView && child.visibility == View.VISIBLE && !isAncestor(child, webView)) {
                    child.visibility = View.GONE
                    hiddenForPip += child
                }
            }
            topBar.hide()
            bottomBar.hide()
        } else {
            hiddenForPip.forEach { it.visibility = View.VISIBLE }
            hiddenForPip.clear()
            webView.evaluateJavascript("window.__pwaPip&&window.__pwaPip.leave()", null)
            updateChrome()
        }
    }

    private fun isAncestor(view: View, of: View): Boolean {
        var parent = of.parent
        while (parent != null) {
            if (parent === view) return true
            parent = parent.parent
        }
        return false
    }

    private fun watchNetwork() {
        if (network != null || site.insertions.none { item -> item.triggers.any { it.kind == "network" } || item.network.isNotEmpty() }) return
        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = changed()
            override fun onLost(network: android.net.Network) = changed()
            private fun changed() {
                runOnUiThread {
                    val label = Net.label(this@WebAppActivity, site)
                    if (label == networkLabel) return@runOnUiThread
                    networkLabel = label
                    webView.evaluateJavascript("window.__pwaRT&&(window.__pwaRT.net=${org.json.JSONObject.quote(label)});", null)
                    fire("network")
                }
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
            network = callback
        } catch (error: RuntimeException) {
            return
        }
    }

    fun screenshot() {
        if ((site.locks && site.hideRecents) || site.privateMode || (temp && site.sessionSecure)) {
            toast("${site.label} blocks screenshots while it’s ${if (site.privateMode || temp) "private" else "locked"}")
            return
        }
        toast("Taking the whole page…")
        Shot.take(this) { bitmap ->
            if (bitmap == null) {
                toast("Couldn’t take that page")
                return@take
            }
            val name = "${site.label.replace(Regex("[^A-Za-z0-9 _-]"), "")} ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.US).format(java.util.Date())}.png"
            Thread {
                val result = try {
                    "Saved to " + Files.savePicture(this, site.power.shotFolder, name, bitmap)
                } catch (error: Exception) {
                    "Couldn’t save: ${error.message}"
                }
                runOnUiThread { toast(result) }
            }.start()
        }
    }

    private fun sendFiles(list: List<Uri>) {
        Thread {
            val files = mutableListOf<Triple<String, String, String>>()
            var total = 0L
            for (uri in list) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                    total += bytes.size
                    if (total > 15L * 1024 * 1024) break
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    files += Triple(name, contentResolver.getType(uri) ?: "application/octet-stream", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                } catch (error: Exception) {
                    continue
                }
            }
            val script = ShareTargets.postScript(site, files)
            runOnUiThread {
                if (script == null || files.isEmpty()) toast("${site.label} couldn’t take those files") else webView.evaluateJavascript(script, null)
            }
        }.start()
    }

    inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) return null
            val url = request.url.toString()
            if (!url.startsWith("http")) return null
            val current = snapshot
            val page = pageUrl
            val level = if (relaxed) "off" else Blocker.level(current, defaults, page)
            val host = request.url.host?.lowercase().orEmpty()
            val verdict = Blocker.check(current, defaults, url, page, level)
            val third = !sameSite(host, hostOf(page).ifEmpty { current.host })
            if (verdict != null || third) Live.add(current.id, Hit(url, host, verdict?.blocked == true, verdict?.source.orEmpty(), verdict?.rule.orEmpty()))
            if (verdict?.blocked == true) {
                blockedHere.incrementAndGet()
                blockedUnsaved.incrementAndGet()
                if (pill != null || shownStyle.isNotEmpty()) main.post { Nav.refreshCount(this@WebAppActivity) }
                return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            val scheme = request.url.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return false
                openOutside(url)
                return true
            }
            if (url == allowOnce) {
                allowOnce = null
                return false
            }
            if (fixAgent(url)) {
                allowOnce = url
                view.loadUrl(url)
                return true
            }
            val rewritten = Rewrite.apply(defaults.rewrites, url, "inside", site.id)
            if (rewritten != url) {
                allowOnce = rewritten
                view.loadUrl(rewritten)
                return true
            }
            if (site.inScope(url) || site.keptInside(url)) return false
            if (!outside) {
                when (site.linksNow(defaults)) {
                    "browser" -> {
                        openBrowser(url)
                        return true
                    }
                    "ask" -> {
                        Warnings.where(this@WebAppActivity, url, request.hasGesture())
                        return true
                    }
                }
            }
            val warning = Warnings.check(this@WebAppActivity, url, request.hasGesture(), request.isRedirect)
            if (warning != null) {
                Warnings.show(this@WebAppActivity, url, warning)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageUrl = url
            pageColor = null
            blockedHere.set(0)
            fpCounts.clear()
            Live.start(site.id, url)
            hideError()
            noted.clear()
            fallbackScripts(url)
            updateChrome()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            pageShown()
        }

        override fun onPageFinished(view: WebView, url: String) {
            refresher.isRefreshing = false
            pageShown()
            if (relaxed && relaxLoads > 0) {
                relaxLoads--
                if (relaxLoads == 0) protectAgain(false)
            }
            if (site.stepUp && !stepUpShown && errorView == null && !locked) {
                stepUpShown = true
                main.postDelayed({ if (!isFinishing) StepUpSheet(this@WebAppActivity).show() }, 600)
            }
            fallbackScripts(url)
            if (site.power.insertionsOn && site.power.builtinTheme && !safeOnce) {
                view.evaluateJavascript(Scripts.THEME) { value ->
                    val color = parseCssColor(value?.trim('"'))
                    pageColor = color
                    paintBars()
                }
            }
            if (!openedFired && site.inScope(url)) {
                openedFired = true
                fire("open")
            }
            if (unlockOnLoad) {
                unlockOnLoad = false
                fire("unlock")
            }
            if (pendingFiles.isNotEmpty() && site.inScope(url)) {
                val list = pendingFiles.toList()
                pendingFiles.clear()
                sendFiles(list)
            }
            pendingRun?.let { id ->
                pendingRun = null
                runInsertion(id)
                toast("Ran it once on this page")
            }
            pendingTest?.let { id ->
                pendingTest = null
                testInsertion(id)
            }
            pendingSelect?.let { id ->
                pendingSelect = null
                startHider(forItem = id)
            }
            if (safeOnce) {
                safeOnce = false
                installScripts()
            }
            remember(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (url.startsWith("http")) pageUrl = url
            if (site.hidden.isNotEmpty() && site.power.builtinHidden) view.evaluateJavascript("window.__pwaHidden&&window.__pwaHidden()", null)
            updateChrome()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            refresher.isRefreshing = false
            pageShown()
            showError(error.description?.toString().orEmpty())
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val print = Trust.fingerprint(error.certificate)
            if (print != null && print == site.trustedCertificate && sameSite(hostOf(error.url), site.host)) {
                handler.proceed()
                return
            }
            handler.cancel()
            pageShown()
            Warnings.certificate(this@WebAppActivity, error)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recreate()
            return true
        }
    }

    private fun remember(url: String) {
        MemoryStore.page(this, site, pageTitle, url)
        MemoryStore.blocked(this, site, blockedUnsaved.getAndSet(0))
        MemoryStore.save(this, site.id)
    }

    inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.animate().cancel()
            if (newProgress >= 100) {
                progress.animate().alpha(0f).setDuration(200).start()
            } else {
                progress.alpha = 1f
                progress.scaleX = newProgress / 100f
            }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            pageTitle = title.orEmpty()
            MemoryStore.page(this@WebAppActivity, site, pageTitle, view.url)
            Nav.refreshCount(this@WebAppActivity)
        }

        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                awaitingResult = true
                @Suppress("DEPRECATION")
                startActivityForResult(fileChooserParams.createIntent(), FILES)
                true
            } catch (error: ActivityNotFoundException) {
                awaitingResult = false
                fileCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread { Warnings.permission(this@WebAppActivity, request) }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            Warnings.location(this@WebAppActivity, origin, callback)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView?.let { stage.removeView(it) }
            customView = view
            customCallback = callback
            view.setBackgroundColor(Color.BLACK)
            stage.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            Bars.fullScreen(window, true)
            stage.requestApplyInsets()
        }

        override fun onHideCustomView() {
            customView?.let { stage.removeView(it) }
            customView = null
            customCallback = null
            Bars.fullScreen(window, site.fullScreen)
            stage.requestApplyInsets()
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
            Popups.open(this@WebAppActivity, resultMsg, profile, isDialog, isUserGesture)

        override fun onCloseWindow(window: WebView) {
            Popups.close(window)
        }
    }

    fun grant(request: PermissionRequest, resources: Array<String>) {
        val needed = mutableListOf<String>()
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources) needed += Manifest.permission.CAMERA
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources) {
            needed += Manifest.permission.RECORD_AUDIO
            needed += Manifest.permission.MODIFY_AUDIO_SETTINGS
        }
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            request.grant(resources)
            return
        }
        pendingPermission = request
        pendingResources = resources
        awaitingResult = true
        requestPermissions(missing.toTypedArray(), MEDIA)
    }

    fun grantLocation(origin: String, callback: GeolocationPermissions.Callback) {
        val needed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (needed.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeo = origin to callback
        awaitingResult = true
        requestPermissions(needed.toTypedArray(), LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        awaitingResult = false
        val granted = grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            MEDIA -> {
                val request = pendingPermission ?: return
                pendingPermission = null
                val all = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (all) request.grant(pendingResources) else {
                    request.deny()
                    toast("Android didn’t allow it. You can change that in Android’s settings for Leyden.")
                }
            }
            LOCATION -> {
                val (origin, callback) = pendingGeo ?: return
                pendingGeo = null
                callback.invoke(origin, granted, false)
            }
            STORAGE -> {
                val next = pendingDownload
                pendingDownload = null
                if (granted) next?.invoke() else toast("Android didn’t allow saving downloads")
            }
            Bubbles.PERMISSION -> if (granted) Bubbles.show(this, site) else toast("Bubbles need notifications, which Android didn’t allow")
        }
    }

    private fun download(url: String, agent: String, disposition: String?, mime: String?, length: Long) {
        if (!url.startsWith("http")) {
            toast("This download can’t be saved from here")
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mime)
        val start = {
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mime)
                    .addRequestHeader("User-Agent", agent)
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                Storage.cookies(profile).getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                getSystemService(DownloadManager::class.java)?.enqueue(request)
                toast("Downloading $name")
            } catch (error: Exception) {
                toast("Couldn’t start the download")
            }
        }
        val withPermission = {
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = start
                awaitingResult = true
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE)
            } else {
                start()
            }
        }
        val folder = site.power.downloadFolder
        val go = if (folder.isNotEmpty()) {
            {
                toast("Downloading $name")
                Files.download(this, folder, url, name, mime, agent, Storage.cookies(profile).getCookie(url)) { problem ->
                    toast(problem?.let { "Download failed: $it" } ?: "Saved $name to ${site.power.downloadFolderName}")
                }
            }
        } else withPermission
        if (site.downloads == "allow") {
            go()
            return
        }
        val size = if (length > 0) " · " + bytes(length) else ""
        ask("Download $name?", "From ${hostOf(url)}$size. It goes to " + (if (folder.isNotEmpty()) site.power.downloadFolderName else "your Downloads folder") + ".", "Download") { go() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        awaitingResult = false
        if (requestCode == Lock.REQUEST) {
            Lock.result(resultCode)
            return
        }
        if (requestCode == FILES) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!this::webView.isInitialized) return
        takeExtras(intent)
        if ((pendingRun != null || pendingSelect != null || pendingTest != null) && loaded && pageVisible) {
            pendingRun?.let { runInsertion(it) }
            pendingSelect?.let { startHider(forItem = it) }
            pendingTest?.let { testInsertion(it) }
            pendingRun = null
            pendingSelect = null
            pendingTest = null
            return
        }
        val address = intent.getStringExtra(ADDRESS)
        if (address != null) {
            webView.loadUrl(address)
        } else if (site.onOpen == "start") {
            webView.loadUrl(site.url)
        }
        if (!temp) MemoryStore.opened(this, site)
    }

    override fun onStart() {
        super.onStart()
        if (!this::site.isInitialized) return
        sessionStart = System.currentTimeMillis()
        watchNetwork()
        if (firstStart) {
            firstStart = false
            return
        }
        if (!locked && !awaitingResult && LockState.needed(this, site)) lockNow() else if (!locked) fire("back")
    }

    override fun onResume() {
        super.onResume()
        if (!this::webView.isInitialized) return
        webView.onResume()
        if (locked && promptOnResume) {
            promptOnResume = false
            stage.post { lockPanel?.prompt() }
        }
        val before = site.toJson().toString()
        reloadSite()
        if (site.toJson().toString() != before) {
            if (needsRestart()) {
                recreate()
                return
            }
            installScripts()
            applySettings()
            describeTask()
        }
    }

    override fun onPause() {
        if (this::webView.isInitialized) {
            if (!isInPictureInPictureMode && !temp) Snapshots.take(this, site, webView, locked)
            if (site.locks && !locked) LockState.touch(this, site.id)
            if (!(site.power.audio && mediaPlaying) && !isInPictureInPictureMode) webView.onPause()
            Storage.cookies(profile).flush()
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!this::site.isInitialized || temp) return
        MemoryStore.spent(this, site, System.currentTimeMillis() - sessionStart)
        MemoryStore.blocked(this, site, blockedUnsaved.getAndSet(0))
        MemoryStore.save(this, site.id)

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::webView.isInitialized && !site.privateMode && !temp) webView.saveState(outState)
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            Live.stop(site.id)
            if (!temp) NowPlaying.clear(site.id)
            Popups.closeAll()
            val wipe = isFinishing && (site.privateMode || site.clearOnClose)
            if (wipe) {
                webView.clearCache(true)
                webView.clearHistory()
                val app = applicationContext
                val target = site
                Storage.clearCookies(target) { Storage.clearSiteStorage(target) { Storage.markDirty(app, target, false) } }
            }
            handlers.forEach { it.remove() }
            handlers.clear()
            if (audioOwner == site.id) {
                AudioService.controller = null
                AudioService.stop(this)
                audioOwner = null
            }
            network?.let { getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
            if (temp && isFinishing && !adopted) {
                webView.clearCache(true)
                webView.clearHistory()
                Storage.cookies(profile).removeAllCookies(null)
                Storage.webStorage(profile).deleteAllData()
            }
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            if (temp && isFinishing && !adopted) Sessions.forget(profile)
        }
        super.onDestroy()
    }

    companion object {
        const val ADDRESS = "address"
        const val FILES_SHARED = "files_shared"
        var audioOwner: String? = null
        private const val FILES = 5101
        private const val MEDIA = 5102
        private const val LOCATION = 5103
        private const val STORAGE = 5104
    }
}

fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }

class BubbleActivity : WebAppActivity()
