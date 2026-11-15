package org.bond.pwaapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.net.http.SslError
import android.os.Message
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

object Nav {
    private const val COUNT = "nav-count"

    private fun lockIcon(activity: WebAppActivity): String = if (activity.pageUrl.startsWith("https:")) Glyphs.LOCK else Glyphs.UNLOCK

    fun pill(activity: WebAppActivity): View {
        val pill = activity.line(8)
        val r = activity.dpf(28f)
        val layers = LayerDrawable(arrayOf(rounded(T.lift, r), rounded(T.border, r), rounded(T.surface, r)))
        layers.setLayerInset(1, 0, 0, 0, activity.dp(2))
        layers.setLayerInset(2, activity.dp(1), activity.dp(1), activity.dp(1), activity.dp(3))
        pill.background = layers
        pill.elevation = activity.dpf(6f)
        pill.setPadding(activity.dp(8), activity.dp(6), activity.dp(8), activity.dp(8))
        val home = activity.appIcon(activity.site, 36, 18f)
        home.contentDescription = "Back to ${activity.site.label}"
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        home.setOnClickListener { activity.returnToApp() }
        pill.put(home, activity.dp(36), activity.dp(36))
        val middle = activity.line(6)
        middle.setPadding(activity.dp(4), 0, activity.dp(4), 0)
        middle.put(activity.glyph(lockIcon(activity), T.muted, 13, 2f), activity.dp(13), activity.dp(13))
        val words = activity.column(1)
        words.put(activity.text(hostOf(activity.pageUrl), 13f, T.ink, Fonts.monoMedium).oneLine())
        words.put(activity.text("from ${activity.site.label} · tap for more", 10f, T.muted).oneLine())
        middle.put(words, WRAP_CONTENT)
        middle.isClickable = true
        middle.contentDescription = "${hostOf(activity.pageUrl)}, more"
        middle.setOnClickListener { panel(activity) }
        pill.put(middle, WRAP_CONTENT, MATCH_PARENT)
        val count = activity.badge(activity.blockedHere.get().toString(), T.accent)
        count.tag = COUNT
        count.visibility = if (activity.blockedHere.get() > 0) View.VISIBLE else View.GONE
        pill.put(count, WRAP_CONTENT)
        pill.put(activity.squareKey(Glyphs.CHEVRON_LEFT, "Back a page", 36) { if (activity.webView.canGoBack()) activity.webView.goBack() else activity.returnToApp() }, activity.dp(36), activity.dp(36))
        return pill
    }

    fun bar(activity: WebAppActivity, top: Boolean): View {
        val bar = activity.line(6)
        bar.setBackgroundColor(T.surface)
        bar.setPadding(activity.dp(8), activity.dp(6), activity.dp(8), activity.dp(6))
        val home = activity.appIcon(activity.site, 34, 10f)
        home.contentDescription = "Back to ${activity.site.label}"
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        home.setOnClickListener { activity.returnToApp() }
        bar.put(home, activity.dp(34), activity.dp(34))
        val middle = activity.line(6)
        middle.setPadding(activity.dp(6), 0, activity.dp(4), 0)
        middle.put(activity.glyph(lockIcon(activity), T.muted, 13, 2f), activity.dp(13), activity.dp(13))
        val words = activity.column(1)
        words.put(activity.text(hostOf(activity.pageUrl) + pathOf(activity.pageUrl).let { if (it == "/") "" else it }, 12f, T.ink, Fonts.mono).oneLine())
        words.put(activity.text("outside ${activity.site.label}", 10f, T.muted).oneLine())
        middle.put(words, 0, WRAP_CONTENT, 1f)
        middle.isClickable = true
        middle.setOnClickListener { panel(activity) }
        bar.put(middle, 0, MATCH_PARENT, 1f)
        val count = activity.badge(activity.blockedHere.get().toString(), T.accent)
        count.tag = COUNT
        count.visibility = if (activity.blockedHere.get() > 0) View.VISIBLE else View.GONE
        bar.put(count, WRAP_CONTENT)
        bar.put(activity.squareKey(Glyphs.CHEVRON_LEFT, "Back a page", 36) { if (activity.webView.canGoBack()) activity.webView.goBack() else activity.returnToApp() }, activity.dp(36), activity.dp(36))
        bar.put(activity.squareKey(Glyphs.CHEVRON, "Forward", 36) { if (activity.webView.canGoForward()) activity.webView.goForward() }, activity.dp(36), activity.dp(36))
        bar.put(activity.squareKey(Glyphs.MORE, "More", 36) { panel(activity) }, activity.dp(36), activity.dp(36))
        val holder = activity.column()
        val rule = View(activity)
        rule.setBackgroundColor(T.rule)
        if (!top) holder.put(rule, MATCH_PARENT, activity.dp(1))
        holder.put(bar)
        if (top) holder.put(rule, MATCH_PARENT, activity.dp(1))
        return holder
    }

    fun refreshCount(activity: WebAppActivity) {
        val count = activity.stage.findViewWithTag<TextView>(COUNT) ?: return
        val blocked = activity.blockedHere.get()
        count.text = blocked.toString()
        count.visibility = if (blocked > 0) View.VISIBLE else View.GONE
    }

    fun panel(activity: WebAppActivity) {
        val site = activity.site
        val defaults = activity.defaults
        val host = hostOf(activity.pageUrl)
        val base = baseDomain(host)
        val sheet = Sheet(activity)
        val top = activity.line(12)
        top.put(activity.glyph(lockIcon(activity), T.muted, 18, 2f), activity.dp(18), activity.dp(18))
        val words = activity.column(1)
        words.put(activity.text(host, 16f, T.ink, Fonts.monoMedium).oneLine())
        words.put(activity.text(if (activity.pageUrl.startsWith("https:")) "outside ${site.label} · encrypted" else "outside ${site.label} · not encrypted", 12f, T.muted).oneLine())
        top.put(words, 0, WRAP_CONTENT, 1f)
        sheet.body.put(top)
        val rest = activity.column(12)
        val quick = QuickSheet(activity)
        var query = ""
        lateinit var paint: () -> Unit
        fun outsideEntries(): List<QuickEntry> {
            val level = Blocker.level(site, defaults, activity.pageUrl)
            return listOf(
                QuickEntry(Glyphs.CHEVRON_LEFT, "back a page", null, "previous history") {
                    sheet.dismiss()
                    if (activity.webView.canGoBack()) activity.webView.goBack()
                },
                QuickEntry(Glyphs.CHEVRON, "forward", null, "next history") {
                    sheet.dismiss()
                    if (activity.webView.canGoForward()) activity.webView.goForward()
                },
                QuickEntry(Glyphs.COPY, "copy the address", activity.pageUrl, "link url") {
                    sheet.dismiss()
                    activity.copy(activity.pageUrl)
                },
                QuickEntry(Glyphs.SHIELD, "block ads and trackers on $base", null, "blocking blocked", level != "off") {
                    if (level == "off") {
                        site.unblocked.removeAll { Filters.within(host, it) }
                        if (site.otherBlockingNow(defaults) == "off") site.otherBlocking = "always"
                    } else {
                        site.unblocked += base
                    }
                    activity.saveSite()
                    activity.webView.reload()
                },
                QuickEntry(Glyphs.GLOBE, "keep $base inside ${site.label}", null, "stay inside always open here", site.keptInside(activity.pageUrl)) {
                    if (site.keptInside(activity.pageUrl)) site.keepInside.removeAll { Filters.within(host, it) } else site.keepInside += base
                    activity.saveSite()
                },
                QuickEntry(Glyphs.BAR, "show the way back as", null, "pill bar top bottom nav", options = listOf(Option("pill"), Option("top", "top bar"), Option("bottom", "bottom bar")), chosen = site.navStyleNow(defaults), pick = { key ->
                    site.navStyle = key
                    activity.saveSite()
                    sheet.dismiss()
                    activity.updateChrome()
                }) { },
                QuickEntry(Glyphs.ARROW_LEFT, "back to ${site.label}", null, "home return app") {
                    sheet.dismiss()
                    activity.returnToApp()
                },
            )
        }
        val search = activity.field("", "Search, or find on this page", false, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        search.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        search.setOnEditorActionListener { view, _, _ ->
            val typed = view.text.toString().trim()
            if (typed.isNotEmpty()) {
                sheet.dismiss()
                FindBar.show(activity, typed)
            }
            true
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString().orEmpty().trim()
                paint()
            }
        })
        sheet.body.put(search, MATCH_PARENT, activity.dp(46))
        sheet.body.put(rest)
        fun normal() {
            val keys = activity.line(8)
            keys.put(activity.toggleKey(Glyphs.CHEVRON_LEFT, "back", false) {
                sheet.dismiss()
                if (activity.webView.canGoBack()) activity.webView.goBack()
            }, 0, WRAP_CONTENT, 1f)
            keys.put(activity.toggleKey(Glyphs.CHEVRON, "forward", false) {
                sheet.dismiss()
                if (activity.webView.canGoForward()) activity.webView.goForward()
            }, 0, WRAP_CONTENT, 1f)
            keys.put(activity.toggleKey(Glyphs.RELOAD, "reload", false) {
                sheet.dismiss()
                activity.webView.reload()
            }, 0, WRAP_CONTENT, 1f)
            keys.put(activity.toggleKey(Glyphs.COPY, "copy", false) {
                sheet.dismiss()
                activity.copy(activity.pageUrl)
            }, 0, WRAP_CONTENT, 1f)
            keys.put(activity.toggleKey(Glyphs.BROWSER, "browser", false) {
                sheet.dismiss()
                activity.openBrowser(activity.pageUrl)
            }, 0, WRAP_CONTENT, 1f)
            rest.put(keys)
            val level = Blocker.level(site, defaults, activity.pageUrl)
            val list = activity.card()
            list.switch(Glyphs.SHIELD, "block ads and trackers here", "${plural(activity.blockedHere.get(), "thing")} blocked on this page", level != "off") { on ->
                if (on) {
                    site.unblocked.removeAll { Filters.within(host, it) }
                    if (site.otherBlockingNow(defaults) == "off") site.otherBlocking = "always"
                } else {
                    site.unblocked += base
                }
                activity.saveSite()
                activity.webView.reload()
            }
            list.switch(Glyphs.GLOBE, "keep $base inside ${site.label}", "without asking or warning next time", site.keptInside(activity.pageUrl)) { on ->
                if (on) site.keepInside += base else site.keepInside.removeAll { Filters.within(host, it) }
                activity.saveSite()
            }
            val secure = activity.pageUrl.startsWith("https:") || isSelfHosted(host)
            val listed = Blocker.listed(host)
            val problems = listOfNotNull(if (!secure) "not encrypted" else null, if (listed) "on your blocking lists" else null)
            list.value(if (problems.isEmpty()) Glyphs.CHECK else Glyphs.WARN, "this site", if (problems.isEmpty()) "fine" else "risky",
                if (problems.isEmpty()) "Nothing to warn about: encrypted, not on your blocking lists, and you got here with a tap." else "Warnings would stop you here: " + problems.joinToString(" and ") + ".")
            list.switch(Glyphs.WARN, "warn before risky sites", "only sites that aren’t encrypted, are known for ads or tracking, or send you on without a tap. Ordinary sites open without a warning.", site.warnRiskyNow(defaults)) { on ->
                site.warnRisky = on
                activity.saveSite()
            }
            list.choice(Glyphs.LINK, "links to other sites", if (site.linksNow(defaults) == "ask") "asks the first time you leave ${site.label}" else null, listOf(Option("inside"), Option("browser"), Option("ask")), site.linksNow(defaults)) {
                site.links = it
                activity.saveSite()
                sheet.dismiss()
                panel(activity)
            }
            list.choice(Glyphs.BAR, "show as", null, listOf(Option("pill"), Option("top", "top bar"), Option("bottom", "bottom bar")), site.navStyleNow(defaults)) {
                site.navStyle = it
                activity.saveSite()
                sheet.dismiss()
                activity.updateChrome()
            }
            list.link(Glyphs.SETTINGS, "${site.label} quick settings", null, "search, toggles, select, screenshot") {
                sheet.dismiss()
                QuickSheet(activity).show()
            }
            rest.put(list.view)
            rest.put(activity.primaryButton("Back to ${site.label}", Glyphs.ARROW_LEFT) {
                sheet.dismiss()
                activity.returnToApp()
            }, MATCH_PARENT, activity.dp(52))
        }
        fun results() {
            rest.put(activity.card().apply {
                link(Glyphs.SEARCH, "find “$query” on this page", null, "highlights every match on $host") {
                    sheet.dismiss()
                    FindBar.show(activity, query)
                }
            }.view)
            val terms = QuickEntry.terms(query)
            val here = outsideEntries().filter { it.matches(terms) }
            val skip = here.map { it.name }.toSet()
            val app = quick.entries().filter { it.name !in skip && it.matches(terms) }
            if (here.isEmpty() && app.isEmpty()) {
                rest.put(activity.text("Nothing matches “$query”.", 13f, T.muted))
                return
            }
            val dismissFirst = { entry: QuickEntry -> if (entry.opens) sheet.dismiss() }
            if (here.isNotEmpty()) {
                rest.put(activity.sectionTitle("ON $host".uppercase(), Glyphs.GLOBE))
                val list = activity.card()
                QuickEntry.render(list, here) { }
                rest.put(list.view)
            }
            if (app.isNotEmpty()) {
                rest.put(activity.sectionTitle("${site.label.uppercase()} QUICK SETTINGS", Glyphs.SETTINGS, app.size.toString()))
                val list = activity.card()
                QuickEntry.render(list, app.take(30), dismissFirst)
                rest.put(list.view)
            }
        }
        paint = {
            rest.removeAllViews()
            if (query.isEmpty()) normal() else results()
        }
        paint()
        sheet.show()
    }
}

class LinkSheet(private val activity: WebAppActivity, private val url: String, private val image: String?) {
    fun show() {
        val site = activity.site
        val sheet = Sheet(activity)
        val top = activity.line(10)
        top.put(activity.glyph(if (image != null) Glyphs.SPLASH else Glyphs.LINK, T.muted, 18, 2f), activity.dp(18), activity.dp(18))
        val words = activity.column(2)
        words.put(activity.mono(if (image != null && image == url) "IMAGE" else "LINK", 9f))
        words.put(activity.text(url, 12f, T.ink, Fonts.mono).apply { maxLines = 3 })
        top.put(words, 0, WRAP_CONTENT, 1f)
        sheet.body.put(top)
        val keys = activity.line(8)
        keys.put(activity.toggleKey(Glyphs.BROWSER, "browser", false) {
            sheet.dismiss()
            activity.openBrowser(url)
        }, 0, WRAP_CONTENT, 1f)
        keys.put(activity.toggleKey(Glyphs.COPY, "copy", false) {
            sheet.dismiss()
            activity.copy(url)
        }, 0, WRAP_CONTENT, 1f)
        keys.put(activity.toggleKey(Glyphs.SHARE, "share", false) {
            sheet.dismiss()
            activity.share(url, null)
        }, 0, WRAP_CONTENT, 1f)
        keys.put(activity.toggleKey(Glyphs.ARROW_RIGHT, "open", false) {
            sheet.dismiss()
            activity.allowOnce = url
            activity.webView.loadUrl(url)
        }, 0, WRAP_CONTENT, 1f)
        sheet.body.put(keys)
        val list = activity.card()
        val host = hostOf(url)
        if (host.isNotEmpty() && !site.inScope(url)) {
            val base = baseDomain(host)
            list.switch(Glyphs.GLOBE, "open here instead, always, for $base", "links there stay inside ${site.label}", site.keptInside(url)) { on ->
                if (on) site.keepInside += base else site.keepInside.removeAll { Filters.within(host, it) }
                activity.saveSite()
            }
        }
        for (other in SiteStore.all(activity).filter { it.id != site.id && it.power.shareText && !it.locks }.take(3)) {
            list.link(Glyphs.SHARE, "send to ${other.label}", null, "opens it in ${other.label}") {
                sheet.dismiss()
                activity.startActivity(android.content.Intent(activity, ShareActivity::class.java).setAction(android.content.Intent.ACTION_SEND)
                    .putExtra(android.content.Intent.EXTRA_TEXT, url).putExtra(ShareActivity.SITE, other.id))
            }
        }
        list.link(Glyphs.PICKER, "select this element", "to hide it, target it with a script, or copy a way to find it") {
            sheet.dismiss()
            val spot = activity.lastTouch()
            activity.startHider(spot?.first, spot?.second)
        }
        sheet.body.put(list.view)
        val more = activity.line(10)
        more.put(activity.appIcon(site, 28, 8f), activity.dp(28), activity.dp(28))
        val label = activity.column(1)
        label.put(activity.text("${site.label} quick settings", 13f, T.ink, Fonts.sansMedium))
        label.put(activity.text("reload · shortcuts · toggles", 11f, T.muted))
        more.put(label, 0, WRAP_CONTENT, 1f)
        more.put(activity.glyph(Glyphs.CHEVRON, T.faint, 15, 2f), activity.dp(15), activity.dp(15))
        more.background = activity.secondaryBack()
        more.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(12))
        more.isClickable = true
        more.setOnClickListener {
            sheet.dismiss()
            QuickSheet(activity).show()
        }
        sheet.body.put(more)
        sheet.show()
    }
}

class Hider(private val activity: WebAppActivity, private val forItem: String? = null) {
    private val panel = activity.column(10)
    private var data: JSONObject? = null
    private var chosen = ""
    private var mode = if (forItem != null) "target" else "hide"
    private var searching = false
    private var searchField: android.widget.EditText? = null
    private val searchResults = activity.column(6)
    private var searchRun: Runnable? = null
    private val detector = android.view.GestureDetector(activity, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            val web = activity.webView
            searching = false
            if (web.width > 0 && web.height > 0) call("pick(${event.x / web.width},${event.y / web.height})")
            return false
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    fun start(fx: Float?, fy: Float?) {
        activity.webView.evaluateJavascript(Scripts.HIDER.replace("#E8590C", colorHex(T.accent)).replace("232,89,12", Accents.rgb(T.accent)), null)
        activity.webView.evaluateJavascript("window.__pwaHider&&__pwaHider.mode(${JSONObject.quote(mode)})", null)
        activity.webView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
        panel.background = activity.surface(22f)
        panel.elevation = activity.dpf(8f)
        panel.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        panel.isClickable = true
        build()
        activity.stage.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        place()
        if (fx != null && fy != null) activity.webView.postDelayed({ call("pick($fx,$fy)") }, 150)
    }

    private val atTop: Boolean
        get() = activity.defaults.selectAt == "top"

    private fun place() {
        val params = panel.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = if (atTop) Gravity.TOP else Gravity.BOTTOM
        params.setMargins(activity.dp(10), if (atTop) activity.dp(10) + activity.topInset() else 0, activity.dp(10), activity.dp(10))
        panel.layoutParams = params
    }

    private fun move() {
        val defaults = activity.defaults
        defaults.selectAt = if (atTop) "bottom" else "top"
        Defaults.save(activity, defaults)
        place()
        build()
    }

    private fun searchView(): View {
        val box = activity.column(8)
        val field = searchField ?: activity.field("", "Words on the page, or a selector", false, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).also { made ->
            made.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            made.setOnEditorActionListener { view, _, _ ->
                runSearch(view.text.toString())
                true
            }
            made.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    searchRun?.let { made.removeCallbacks(it) }
                    val typed = s?.toString().orEmpty()
                    val run = Runnable { runSearch(typed) }
                    searchRun = run
                    made.postDelayed(run, 300)
                }
            })
            searchField = made
        }
        (field.parent as? ViewGroup)?.removeView(field)
        box.put(field, MATCH_PARENT, activity.dp(46))
        (searchResults.parent as? ViewGroup)?.removeView(searchResults)
        val scroll = android.widget.ScrollView(activity)
        scroll.addView(searchResults)
        box.put(scroll, MATCH_PARENT, activity.dp(220))
        return box
    }

    private fun runSearch(query: String) {
        activity.webView.evaluateJavascript("window.__pwaHider?__pwaHider.search(${JSONObject.quote(query)}):''") { value ->
            val text = try {
                org.json.JSONArray("[$value]").getString(0)
            } catch (error: Exception) {
                ""
            }
            val parsed = try {
                JSONObject(text)
            } catch (error: Exception) {
                null
            }
            paintResults(query, parsed)
        }
    }

    private fun paintResults(query: String, parsed: JSONObject?) {
        searchResults.removeAllViews()
        val items = parsed?.optJSONArray("items")
        if (query.isBlank()) {
            searchResults.put(activity.text(if (mode == "target") "Type what a button or field says, its label, or a selector. Matches jump to the clickable part." else "Type words you can see, a label, or a selector.", 12f, T.muted))
            return
        }
        if (items == null || items.length() == 0) {
            searchResults.put(activity.text("Nothing on this page matches “$query”.", 12f, T.muted))
            return
        }
        searchResults.put(activity.mono((if (parsed.optString("by") == "selector") "AS A SELECTOR" else "BY WORDS") + " · " + plural(items.length(), "match", "matches") + if (items.length() >= 40) " · first 40" else "", 9f))
        for (index in 0 until items.length()) {
            val hit = items.optJSONObject(index) ?: continue
            val row = activity.line(8)
            row.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(9))
            row.background = activity.secondaryBack(10f)
            val words = activity.column(1)
            words.put(activity.text(hit.optString("text").ifEmpty { "(no words)" }, 12f, T.ink).apply { maxLines = 2 })
            words.put(activity.text(hit.optString("tag") + hit.optString("kind").let { if (it.isEmpty()) "" else " · $it" }, 10f, T.muted, Fonts.mono).oneLine())
            row.put(words, 0, WRAP_CONTENT, 1f)
            if (!hit.optBoolean("seen")) row.put(activity.tagView("off screen"), WRAP_CONTENT)
            row.isClickable = true
            row.setOnClickListener {
                searching = false
                hideKeyboard()
                call("found(${hit.optInt("i")})")
            }
            searchResults.put(row)
        }
    }

    private fun hideKeyboard() {
        searchField?.let { field -> activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0) }
    }

    private fun call(what: String) {
        activity.webView.evaluateJavascript("window.__pwaHider?__pwaHider.$what:''") { read(it) }
    }

    private fun read(value: String?) {
        val text = value?.takeIf { it != "null" && it != "\"\"" } ?: return
        val inner = try {
            org.json.JSONArray("[$text]").getString(0)
        } catch (error: Exception) {
            return
        }
        if (inner.isEmpty()) return
        data = try {
            JSONObject(inner)
        } catch (error: Exception) {
            return
        }
        chosen = data?.optString("chosen").orEmpty()
        build()
    }

    private fun key(icon: String, label: String, enabled: Boolean, what: String): View {
        val key = activity.toggleKey(icon, label, false) { if (enabled) call(what) }
        key.alpha = if (enabled) 1f else 0.4f
        return key
    }

    private fun build() {
        panel.removeAllViews()
        val info = data
        val head = activity.line(8)
        head.put(activity.glyph(Glyphs.PICKER, T.accent, 16, 2f), activity.dp(16), activity.dp(16))
        head.put(activity.text("Select", 15f, T.ink, Fonts.sansBold), WRAP_CONTENT)
        head.put(activity.spacer(), 0, 1, 1f)
        head.put(activity.squareKey(Glyphs.SEARCH, if (searching) "Back to the pick" else "Search the page", 34) {
            searching = !searching
            if (searching) {
                build()
                searchField?.requestFocus()
                runSearch(searchField?.text?.toString().orEmpty())
            } else {
                hideKeyboard()
                call("search('')")
                build()
            }
        }, activity.dp(34), activity.dp(34))
        head.put(activity.squareKey(if (atTop) Glyphs.CHEVRON_DOWN else Glyphs.CHEVRON_UP, if (atTop) "Move to the bottom" else "Move to the top", 34) { move() }, activity.dp(34), activity.dp(34))
        head.put(activity.squareKey(Glyphs.CLOSE, "Cancel", 34) { stop() }, activity.dp(34), activity.dp(34))
        panel.put(head)
        panel.put(activity.chips(listOf(Option("hide"), Option("target", "target for a script"), Option("copy")), mode) {
            mode = it
            call("mode(${JSONObject.quote(it)})")
            if (searching) runSearch(searchField?.text?.toString().orEmpty())
            build()
        }, WRAP_CONTENT)
        if (searching) {
            panel.put(searchView())
            return
        }
        if (info == null) {
            panel.put(activity.text(when (mode) {
                "target" -> "Tap a button, field or link a script should use. Scroll to find it; taps here won’t open links."
                "copy" -> "Tap something to copy a way to find it."
                else -> "Tap the part of the page to hide. Scroll to find it; taps here won’t open links."
            }, 12f, T.muted))
            return
        }
        if (mode == "target" && info.optBoolean("snapped")) {
            panel.put(activity.text("Snapped to the ${info.optString("tag")} you tapped inside of, since that’s what a script can use.", 11f, T.good))
        }
        val about = "${info.optString("tag")} · ${info.optInt("w")}×${info.optInt("h")}" + info.optString("kind").let { if (it.isEmpty()) "" else " · $it" } +
            info.optString("text").let { if (it.isEmpty()) "" else " · “$it”" }
        panel.put(activity.text(about, 11f, T.muted).oneLine())

        val chain = info.optJSONArray("chain")
        if (chain != null && chain.length() > 1) {
            val scroller = android.widget.HorizontalScrollView(activity)
            scroller.isHorizontalScrollBarEnabled = false
            val row = activity.line(4)
            for (index in 0 until chain.length()) {
                if (index > 0) row.put(activity.glyph(Glyphs.CHEVRON, T.faint, 11, 2f), activity.dp(11), activity.dp(11))
                val last = index == chain.length() - 1
                val chip = activity.text(chain.optString(index), 11f, if (last) T.ink else T.muted, if (last) Fonts.monoMedium else Fonts.mono)
                chip.setPadding(activity.dp(7), activity.dp(4), activity.dp(7), activity.dp(4))
                chip.background = rounded(if (last) T.bg else 0, activity.dpf(6f))
                chip.setOnClickListener { call("level($index)") }
                row.put(chip, WRAP_CONTENT)
            }
            scroller.addView(row)
            panel.put(scroller)
            scroller.post { scroller.fullScroll(View.FOCUS_RIGHT) }
        }

        val keys = activity.line(6)
        keys.put(key(Glyphs.PLUS, "bigger", info.optBoolean("up"), "up()"), 0, WRAP_CONTENT, 1f)
        keys.put(key(Glyphs.MINUS, "smaller", info.optBoolean("down"), "down()"), 0, WRAP_CONTENT, 1f)
        keys.put(key(Glyphs.CHEVRON_LEFT, "previous", info.optBoolean("prev"), "prev()"), 0, WRAP_CONTENT, 1f)
        keys.put(key(Glyphs.CHEVRON, "next", info.optBoolean("next"), "next()"), 0, WRAP_CONTENT, 1f)
        panel.put(keys)

        val findHead = activity.line(8)
        findHead.put(activity.mono(if (mode == "hide") "HIDE WHAT MATCHES" else "FIND IT BY · MOST STABLE FIRST", 9f), 0, WRAP_CONTENT, 1f)
        findHead.put(activity.quietButton("edit", Glyphs.PEN, T.ink) { edit() }, WRAP_CONTENT, activity.dp(32))
        panel.put(findHead)
        val options = info.optJSONArray("options")
        val list = activity.column(6)
        var rows = 0
        fun option(selector: String, why: String, stab: String, n: Int, own: Boolean) {
            val picked = selector == chosen
            val row = activity.line(8)
            row.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(9))
            row.background = if (picked) rounded(T.accentSoft, activity.dpf(10f), activity.dp(1), T.accent) else activity.secondaryBack(10f)
            val texts = activity.column(1)
            val shown = Selectors.describe(selector)
            texts.put(activity.text(shown, 12f, T.ink, Fonts.mono).apply { maxLines = 3 })
            texts.put(activity.text(why, 10f, T.muted))
            row.put(texts, 0, WRAP_CONTENT, 1f)
            val tone = when (stab) {
                "stable" -> T.good
                "fragile" -> T.danger
                else -> 0xFF8A6A00.toInt()
            }
            row.put(activity.tagView(stab, tone, tone and 0x00FFFFFF or 0x1F000000), WRAP_CONTENT)
            row.put(activity.badge(n.toString(), if (n == 0) T.danger else if (picked) T.accent else T.faint), WRAP_CONTENT)
            row.isClickable = true
            row.contentDescription = "$shown, $stab, ${plural(n, "match", "matches")}"
            row.setOnClickListener { call("choose(${JSONObject.quote(selector)},$own)") }
            row.setOnLongClickListener {
                edit(selector)
                true
            }
            list.put(row)
            rows++
        }
        if (info.optBoolean("custom") && chosen.isNotEmpty()) option(chosen, "yours, edited · long-press any row to edit it", Selectors.stability(chosen), info.optInt("cn"), true)
        if (options != null) {
            for (index in 0 until options.length()) {
                val each = options.optJSONObject(index) ?: continue
                if (info.optBoolean("custom") && each.optString("s") == chosen) continue
                option(each.optString("s"), each.optString("why"), each.optString("stab"), each.optInt("n"), false)
            }
        }
        val scroll = android.widget.ScrollView(activity)
        scroll.addView(list)
        val height = minOf(rows * activity.dp(60), activity.dp(220))
        panel.put(scroll, MATCH_PARENT, height)

        val bar = activity.line(8)
        val ready = chosen.isNotEmpty()
        when (mode) {
            "hide" -> {
                bar.put(dim(activity.secondaryButton("This page", Glyphs.FULL) { save(true) }, ready), 0, activity.dp(48), 1f)
                bar.put(dim(activity.primaryButton("Every page", Glyphs.CHECK) { save(false) }, ready), 0, activity.dp(48), 1f)
            }
            "target" -> {
                bar.put(dim(activity.secondaryButton("Copy", Glyphs.COPY) { copy() }, ready), WRAP_CONTENT, activity.dp(48))
                bar.put(dim(activity.primaryButton(if (forItem != null) "Use it" else "New auto target", Glyphs.ARROW_RIGHT) { target(options) }, ready), 0, activity.dp(48), 1f)
            }
            else -> bar.put(dim(activity.primaryButton("Copy", Glyphs.COPY) { copy() }, ready), 0, activity.dp(48), 1f)
        }
        panel.put(bar)
    }

    private fun dim(view: View, ready: Boolean): View {
        view.isEnabled = ready
        view.alpha = if (ready) 1f else 0.4f
        return view
    }

    private fun edit(start: String = chosen) {
        val counter: (String, (Int) -> Unit) -> Unit = { selector, done ->
            activity.webView.evaluateJavascript("window.__pwaHider?__pwaHider.count(${JSONObject.quote(selector)}):'0'") { value ->
                done(value?.trim('"')?.toIntOrNull() ?: 0)
            }
        }
        activity.editSelector(if (mode == "hide") "What to hide" else "How to find it", start, mode != "hide", counter) { edited ->
            if (edited.isNotEmpty()) call("choose(${JSONObject.quote(edited)},true)")
        }
    }

    private fun copy() {
        activity.copy(chosen)
        activity.toast("Copied $chosen")
    }

    private fun target(options: org.json.JSONArray?) {
        val picked = arrayListOf(chosen)
        if (options != null) {
            for (index in 0 until options.length()) {
                val option = options.optJSONObject(index) ?: continue
                val selector = option.optString("s")
                if (selector != chosen && option.optString("stab") != "fragile" && option.optInt("n") == 1 && picked.size < 3) picked += selector
            }
        }
        val intent = android.content.Intent(activity, BuilderActivity::class.java)
            .putExtra(AppSettingsActivity.SITE, activity.site.id)
            .putStringArrayListExtra(BuilderActivity.TARGETS, picked)
            .putExtra(BuilderActivity.PATH, pathOf(activity.pageUrl).let { if (it == "/") "" else it.substringBeforeLast('/') + "/*" })
            .putExtra(BuilderActivity.NAME, data?.optString("text")?.take(30).orEmpty())
        if (forItem != null) intent.putExtra(ItemActivity.ITEM, forItem)
        stop()
        activity.startActivity(intent)
    }

    private fun save(onlyHere: Boolean) {
        if (chosen.isEmpty()) return
        val site = activity.site
        val path = if (onlyHere) pathOf(activity.pageUrl) else ""
        site.hidden.removeAll { it.selector == chosen && it.path == path }
        site.hidden += Hidden(chosen, path)
        activity.saveSite()
        activity.installScripts()
        activity.webView.evaluateJavascript("window.__pwaHider&&__pwaHider.hide(${JSONObject.quote(chosen)})", null)
        activity.webView.evaluateJavascript(Scripts.hidden(site), null)
        activity.toast(if (onlyHere) "Hidden on this page. Undo it in insertions › hidden elements." else "Hidden on every page. Undo it in insertions › hidden elements.")
        stop()
    }

    fun stop() {
        activity.webView.setOnTouchListener(null)
        activity.webView.evaluateJavascript("window.__pwaHider&&__pwaHider.done()", null)
        activity.stage.removeView(panel)
        activity.hider = null
        activity.restoreTouch()
    }
}

class Warning(val title: String, val detail: String, val icon: String)

object Warnings {
    fun check(activity: WebAppActivity, url: String, gesture: Boolean, redirect: Boolean): Warning? {
        val site = activity.site
        val defaults = activity.defaults
        val host = hostOf(url)
        if (site.warnPlainNow(defaults) && url.startsWith("http:") && !isSelfHosted(host)) {
            return Warning("This page isn’t encrypted", "$host uses plain http. Anyone on your network could read or change what you see and send there.", Glyphs.UNLOCK)
        }
        if (!site.warnRiskyNow(defaults)) return null
        if (Blocker.listed(host)) {
            return Warning("$host is on your blocking lists", "It’s known for ads or tracking. Sites like this rarely have anything you came for.", Glyphs.SHIELD)
        }
        if (!gesture && !redirect) {
            val from = hostOf(activity.pageUrl).ifEmpty { site.host }
            return Warning("$from is sending you to $host", "This happened without a tap from you.", Glyphs.ARROW_RIGHT)
        }
        return null
    }

    fun show(activity: WebAppActivity, url: String, warning: Warning) {
        val site = activity.site
        val host = hostOf(url)
        val base = baseDomain(host)
        val sheet = Sheet(activity)
        val top = activity.line(12)
        val mark = FrameLayout(activity)
        mark.background = rounded(T.danger and 0x00FFFFFF or 0x22000000, activity.dpf(10f))
        mark.addView(activity.glyph(warning.icon, T.danger, 18, 2f), FrameLayout.LayoutParams(activity.dp(18), activity.dp(18), Gravity.CENTER))
        top.put(mark, activity.dp(36), activity.dp(36))
        top.put(activity.text(warning.title, 17f, T.ink, Fonts.sansBold), 0, WRAP_CONTENT, 1f)
        sheet.body.put(top)
        sheet.body.put(activity.text(warning.detail, 13f, T.muted))
        sheet.body.put(activity.text(url, 11f, T.body, Fonts.mono).apply {
            maxLines = 3
            background = rounded(T.surface, activity.dpf(10f))
            setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
        })
        var trust = false
        val list = activity.card()
        list.switch(Glyphs.GLOBE, "don’t warn me about $base again", "keeps it inside ${site.label}", false) { trust = it }
        sheet.body.put(list.view)
        val bar = activity.line(8)
        bar.put(activity.quietButton("Stay", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, activity.dp(48))
        bar.put(activity.secondaryButton("Browser", Glyphs.BROWSER) {
            sheet.dismiss()
            activity.openBrowser(url)
        }, 0, activity.dp(48), 1f)
        bar.put(activity.primaryButton("Continue", Glyphs.ARROW_RIGHT) {
            sheet.dismiss()
            if (trust) {
                site.keepInside += base
                activity.saveSite()
            }
            activity.allowOnce = url
            activity.webView.loadUrl(url)
        }, 0, activity.dp(48), 1f)
        sheet.body.put(bar)
        sheet.show()
    }

    fun where(activity: WebAppActivity, url: String, gesture: Boolean) {
        val site = activity.site
        val host = hostOf(url)
        val base = baseDomain(host)
        val sheet = Sheet(activity)
        sheet.body.put(activity.text("Open $host?", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text(if (gesture) "A link in ${site.label} goes to another site." else "${site.label} is sending you to another site without a tap.", 13f, T.muted))
        var always = false
        val list = activity.card()
        list.switch(Glyphs.GLOBE, "always open $base here", "keeps it inside ${site.label}, no asking", false) { always = it }
        sheet.body.put(list.view)
        val bar = activity.line(8)
        bar.put(activity.quietButton("Stay", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, activity.dp(48))
        bar.put(activity.secondaryButton("Here", Glyphs.ARROW_RIGHT) {
            sheet.dismiss()
            if (always) {
                site.keepInside += base
                activity.saveSite()
            }
            val warning = check(activity, url, gesture, false)
            if (warning != null && !always) {
                show(activity, url, warning)
            } else {
                activity.allowOnce = url
                activity.webView.loadUrl(url)
            }
        }, 0, activity.dp(48), 1f)
        bar.put(activity.primaryButton("Browser", Glyphs.BROWSER) {
            sheet.dismiss()
            activity.openBrowser(url)
        }, 0, activity.dp(48), 1f)
        sheet.body.put(bar)
        sheet.show()
    }

    fun certificate(activity: WebAppActivity, error: SslError) {
        val site = activity.site
        val host = hostOf(error.url)
        val own = sameSite(host, site.host)
        val described = Trust.describe(error.certificate)
        val sheet = Sheet(activity)
        sheet.body.put(activity.text("$host’s certificate isn’t trusted", 18f, T.ink, Fonts.sansBold))
        val reason = when (error.primaryError) {
            SslError.SSL_EXPIRED -> "It has expired."
            SslError.SSL_IDMISMATCH -> "It was made for a different name."
            SslError.SSL_NOTYETVALID -> "It isn’t valid yet."
            SslError.SSL_UNTRUSTED -> "Android doesn’t know who issued it."
            else -> "Android couldn’t check it."
        }
        sheet.body.put(activity.text(reason + if (own) " If this is your own server with a certificate you made, compare the fingerprint, then trust it for ${site.label} only." else " The page was stopped.", 13f, T.muted))
        if (described != null) {
            val info = activity.card()
            info.value(Glyphs.TAG, "issued to", described.subject.ifEmpty { "?" })
            info.value(Glyphs.TAG, "issued by", described.issuer.ifEmpty { "?" })
            info.custom(activity.text("SHA-256 " + described.fingerprint, 11f, T.body, Fonts.mono).apply { setPadding(0, activity.dp(10), 0, activity.dp(10)) })
            sheet.body.put(info.view)
        }
        val bar = activity.line(8)
        bar.put(activity.quietButton("Close", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, activity.dp(48))
        bar.put(activity.spacer(), 0, 1, 1f)
        if (own && described != null) {
            bar.put(activity.secondaryButton("Trust it for ${site.label}", Glyphs.LOCK) {
                sheet.dismiss()
                site.trustedCertificate = described.fingerprint
                activity.saveSite()
                activity.webView.loadUrl(error.url)
            }, WRAP_CONTENT, activity.dp(48))
        }
        sheet.body.put(bar)
        sheet.show()
    }

    private fun setting(site: Site, kind: String): String = when (kind) {
        "camera" -> site.camera
        "microphone" -> site.microphone
        else -> site.location
    }

    fun permission(activity: WebAppActivity, request: PermissionRequest) {
        val site = activity.site
        val wants = request.resources.mapNotNull {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "camera"
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "microphone"
                else -> null
            }
        }
        val resources = request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }.toTypedArray()
        if (wants.isEmpty() || !site.inScope(request.origin.toString())) {
            request.deny()
            return
        }
        if (wants.any { setting(site, it) == "never" }) {
            request.deny()
            activity.toast("${site.label} can’t use the ${wants.joinToString(" or ")}. Change that in its settings.")
            return
        }
        if (wants.all { it in site.granted }) {
            activity.grant(request, resources)
            return
        }
        askFor(activity, wants, { activity.grant(request, resources) }, { request.deny() })
    }

    fun location(activity: WebAppActivity, origin: String, callback: GeolocationPermissions.Callback) {
        val site = activity.site
        if (site.location == "never" || !site.inScope(origin)) {
            callback.invoke(origin, false, false)
            return
        }
        if ("location" in site.granted) {
            activity.grantLocation(origin, callback)
            return
        }
        askFor(activity, listOf("location"), { activity.grantLocation(origin, callback) }, { callback.invoke(origin, false, false) })
    }

    private fun askFor(activity: WebAppActivity, wants: List<String>, allow: () -> Unit, deny: () -> Unit) {
        val site = activity.site
        val sheet = Sheet(activity)
        var answered = false
        val icon = when (wants.first()) {
            "camera" -> Glyphs.CAMERA
            "microphone" -> Glyphs.MIC
            else -> Glyphs.PIN
        }
        val top = activity.line(12)
        top.put(activity.appIcon(site, 40, 12f), activity.dp(40), activity.dp(40))
        top.put(activity.text("${site.label} wants to use your ${wants.joinToString(" and ")}", 17f, T.ink, Fonts.sansBold), 0, WRAP_CONTENT, 1f)
        top.put(activity.glyph(icon, T.muted, 20, 2f), activity.dp(20), activity.dp(20))
        sheet.body.put(top)
        var remember = false
        val list = activity.card()
        list.switch(Glyphs.CHECK, "allow every time", "you can take it back on its memory page", false) { remember = it }
        sheet.body.put(list.view)
        val bar = activity.line(8)
        bar.put(activity.quietButton("Not now", Glyphs.CLOSE) {
            answered = true
            sheet.dismiss()
            deny()
        }, WRAP_CONTENT, activity.dp(48))
        bar.put(activity.spacer(), 0, 1, 1f)
        bar.put(activity.primaryButton("Allow", Glyphs.CHECK) {
            answered = true
            sheet.dismiss()
            if (remember) {
                site.granted += wants
                activity.saveSite()
            }
            allow()
        }, activity.dp(150), activity.dp(48))
        sheet.body.put(bar)
        sheet.setOnDismissListener { if (!answered) deny() }
        sheet.show()
    }
}

object Popups {
    private val open = mutableListOf<Pair<WebView, Dialog>>()

    @SuppressLint("SetJavaScriptEnabled")
    fun open(activity: WebAppActivity, message: Message, profile: String?, isDialog: Boolean, gesture: Boolean): Boolean {
        val site = activity.site
        val transport = message.obj as? WebView.WebViewTransport ?: return false
        if (gesture && !isDialog && site.popups != "browser") {
            val temp = WebView(activity)
            Storage.attach(temp, profile)
            var taken = false
            fun take(url: String) {
                if (taken || !url.startsWith("http")) return
                taken = true
                activity.openLink(url, true)
                temp.post {
                    temp.stopLoading()
                    temp.destroy()
                }
            }
            temp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    take(request.url.toString())
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    take(url)
                }
            }
            transport.webView = temp
            message.sendToTarget()
            return true
        }
        when (site.popups) {
            "block" -> {
                activity.toast("${site.label} tried to open a pop-up; pop-ups are blocked")
                return false
            }
            "browser" -> {
                val temp = WebView(activity)
                Storage.attach(temp, profile)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        activity.openBrowser(request.url.toString())
                        view.post { view.destroy() }
                        return true
                    }
                }
                transport.webView = temp
                message.sendToTarget()
                return true
            }
        }
        val child = WebView(activity)
        Storage.attach(child, profile)
        child.settings.javaScriptEnabled = true
        child.settings.domStorageEnabled = true
        child.settings.userAgentString = activity.webView.settings.userAgentString
        child.settings.setSupportMultipleWindows(false)
        child.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        Storage.cookies(profile).setAcceptThirdPartyCookies(child, true)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = activity.column()
        box.setBackgroundColor(T.bg)
        val head = activity.line(8)
        head.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
        val lock = activity.glyph(Glyphs.POPUP, T.muted, 15, 2f)
        head.put(lock, activity.dp(15), activity.dp(15))
        val words = activity.column(1)
        val title = activity.text("opening…", 13f, T.ink, Fonts.monoMedium).oneLine()
        words.put(title)
        words.put(activity.text("a window ${site.label} opened · it can talk back to ${site.label}", 10f, T.muted).oneLine())
        head.put(words, 0, WRAP_CONTENT, 1f)
        head.put(activity.squareKey(Glyphs.LOCK, "Passwords", 36) { Passwords.fill(activity, child) }, activity.dp(36), activity.dp(36))
        head.put(activity.squareKey(Glyphs.ARROW_RIGHT, "Open it in ${site.label} instead", 36) {
            val address = child.url
            dialog.dismiss()
            if (address != null) activity.openLink(address, true)
        }, activity.dp(36), activity.dp(36))
        head.put(activity.squareKey(Glyphs.BROWSER, "Open in the browser", 36) {
            child.url?.let { activity.openBrowser(it) }
            dialog.dismiss()
        }, activity.dp(36), activity.dp(36))
        head.put(activity.squareKey(Glyphs.CLOSE, "Close", 36) { dialog.dismiss() }, activity.dp(36), activity.dp(36))
        box.put(head)
        box.put(child, MATCH_PARENT, 0, 1f)
        child.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? =
                activity.client.shouldInterceptRequest(view, request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (!url.startsWith("http")) {
                    activity.openOutside(url)
                    return true
                }
                val warning = Warnings.check(activity, url, request.hasGesture(), request.isRedirect)
                if (warning != null && !site.inScope(url) && !site.keptInside(url)) {
                    dialog.dismiss()
                    Warnings.show(activity, url, warning)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                title.text = hostOf(url).ifEmpty { "pop-up from ${site.label}" }
                val secure = url.startsWith("https:")
                lock.setImageDrawable(Glyph(if (secure) Glyphs.LOCK else Glyphs.UNLOCK, if (secure) T.muted else T.danger, 2f))
            }
        }
        child.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) {
                dialog.dismiss()
            }
        }
        dialog.setContentView(box)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(T.bg))
            window.setLayout(MATCH_PARENT, MATCH_PARENT)
        }
        val entry = child to dialog
        dialog.setOnDismissListener {
            open.remove(entry)
            child.destroy()
        }
        open += entry
        dialog.show()
        transport.webView = child
        message.sendToTarget()
        return true
    }

    fun close(window: WebView) {
        open.firstOrNull { it.first == window }?.second?.dismiss()
    }

    fun closeAll() {
        open.toList().forEach { it.second.dismiss() }
    }
}
