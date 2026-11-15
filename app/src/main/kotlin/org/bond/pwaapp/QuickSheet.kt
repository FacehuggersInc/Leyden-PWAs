package org.bond.pwaapp

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout

class QuickEntry(
    val icon: String?,
    val name: String,
    val detail: String?,
    val words: String,
    val on: Boolean? = null,
    val options: List<Option>? = null,
    val chosen: String = "",
    val pick: ((String) -> Unit)? = null,
    val run: () -> Unit,
) {
    val opens: Boolean
        get() = on == null && options == null

    fun matches(terms: List<String>): Boolean {
        val hay = (name + " " + detail.orEmpty() + " " + words).lowercase()
        return terms.all { it in hay }
    }

    companion object {
        fun terms(query: String): List<String> = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        fun render(list: Card, entries: List<QuickEntry>, before: (QuickEntry) -> Unit = {}) {
            for (entry in entries) {
                val pick = entry.pick
                when {
                    entry.options != null && pick != null -> list.choice(entry.icon, entry.name, entry.detail, entry.options, entry.chosen) {
                        before(entry)
                        pick(it)
                    }
                    entry.on != null -> list.switch(entry.icon, entry.name, entry.detail, entry.on) {
                        before(entry)
                        entry.run()
                    }
                    else -> list.link(entry.icon, entry.name, null, entry.detail) {
                        before(entry)
                        entry.run()
                    }
                }
            }
        }
    }
}

class QuickSheet(private val activity: WebAppActivity, private val refresh: (() -> Unit)? = null) {
    private val sheet = Sheet(activity)
    private val rest: LinearLayout = activity.column(12)
    private var full = activity.defaults.quickSize == "full"
    private var query = ""
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun show() {
        if (activity.hider != null) return
        top()
        sheet.body.put(rest)
        if (!full) sheet.startCompact(0.55f)
        sheet.onExpand = {
            if (!full) {
                full = true
                fill()
            }
        }
        fill()
        sheet.show()
    }

    private fun change(block: (Site) -> Unit) {
        block(activity.site)
        if (activity.needsRestart()) {
            sheet.dismiss()
            activity.afterChange()
            return
        }
        activity.afterChange()
        fill()
        refresh?.invoke()
    }

    private fun close(then: () -> Unit) {
        sheet.dismiss()
        then()
    }

    private fun top() {
        val site = activity.site
        val body = sheet.body
        val head = activity.line(12)
        head.put(activity.appIcon(site, 44, 14f), activity.dp(44), activity.dp(44))
        val words = activity.column(1)
        words.put(activity.text(activity.pageTitle.ifEmpty { site.label }, 15f, T.ink, Fonts.sansBold).oneLine())
        words.put(activity.text(hostOf(activity.pageUrl) + pathOf(activity.pageUrl), 11f, T.muted, Fonts.mono).oneLine())
        if (activity.temp) words.put(activity.mono(Sessions.name(site.session).uppercase() + " · NOTHING KEPT", 9f, T.accent))
        head.put(words, 0, WRAP_CONTENT, 1f)
        head.put(activity.secondaryButton("select", Glyphs.PICKER) { close { activity.startHider() } }, WRAP_CONTENT, activity.dp(38))
        body.put(head)
        val search = activity.field("", "Search quick settings, or find on this page", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        search.imeOptions = EditorInfo.IME_ACTION_SEARCH
        search.setOnEditorActionListener { view, _, _ ->
            val typed = view.text.toString().trim()
            if (typed.isNotEmpty()) close { FindBar.show(activity, typed) }
            true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                pending?.let { main.removeCallbacks(it) }
                val typed = s?.toString().orEmpty().trim()
                val run = Runnable {
                    query = typed
                    if (typed.isNotEmpty()) sheet.expand()
                    fill()
                }
                pending = run
                main.postDelayed(run, 150)
            }
        })
        body.put(search, MATCH_PARENT, activity.dp(46))
    }

    private fun fill() {
        rest.removeAllViews()
        when {
            query.isNotEmpty() -> results()
            activity.temp -> session()
            full -> everything()
            else -> starter()
        }
    }

    private fun actions() {
        val row = activity.line(8)
        row.put(activity.toggleKey(Glyphs.RELOAD, "reload", false) { close { activity.webView.reload() } }, 0, WRAP_CONTENT, 1f)
        row.put(activity.toggleKey(Glyphs.SHARE, "share", false) { close { activity.share(activity.pageUrl, activity.pageTitle) } }, 0, WRAP_CONTENT, 1f)
        row.put(activity.toggleKey(Glyphs.BROWSER, "browser", false) { close { activity.openBrowser(activity.pageUrl) } }, 0, WRAP_CONTENT, 1f)
        rest.put(row)
    }

    private fun toggles(titled: Boolean) {
        val site = activity.site
        val keys = activity.defaults.quickKeys
        val page = listOf(
            Triple("dark", "dark mode", Glyphs.MOON) to (site.dark == "dark"),
            Triple("desktop", "desktop", Glyphs.DESKTOP) to site.desktop,
            Triple("full", "full screen", Glyphs.FULL) to site.fullScreen,
        ).filter { it.first.first in keys }
        val behavior = listOf(
            Triple("screen", "screen on", Glyphs.SUN) to site.screenOn,
            Triple("pull", "pull refresh", Glyphs.PULL) to site.pullToRefresh,
            Triple("block", "block ads", Glyphs.SHIELD) to (site.blocking != "off"),
        ).filter { it.first.first in keys }
        val groups = if (titled) listOf("THIS PAGE" to page, "BEHAVIOR" to behavior) else listOf("" to page + behavior)
        for ((title, group) in groups) {
            if (group.isEmpty()) continue
            if (title.isNotEmpty()) rest.put(activity.sectionTitle(title))
            group.chunked(3).forEach { chunk ->
                val row = activity.line(8)
                for ((info, on) in chunk) {
                    val (key, name, icon) = info
                    row.put(activity.toggleKey(icon, name, on) { toggle(key) }, 0, WRAP_CONTENT, 1f)
                }
                repeat(3 - chunk.size) { row.put(activity.spacer(), 0, 1, 1f) }
                rest.put(row)
            }
        }
    }

    private fun runList() {
        val site = activity.site
        val runnable = site.insertions.filter { item -> item.on && item.triggers.any { it.kind == "quick" } }
        if (runnable.isEmpty()) return
        rest.put(activity.sectionTitle("RUN", Glyphs.PLAY))
        val flow = Flow(activity)
        for (item in runnable) {
            val chip = activity.secondaryButton(item.name.ifEmpty { "untitled" }, Glyphs.PLAY) { close { activity.runInsertion(item.id) } }
            chip.minimumHeight = activity.dp(40)
            flow.addView(chip)
        }
        rest.put(flow)
    }

    private fun levels(): List<Option> =
        if (activity.site.session == "private") listOf(Option("standard"), Option("strict")) else listOf(Option("off"), Option("standard", "std"), Option("strict"))

    private fun playingCard() {
        val site = activity.site
        if (!site.keeps(Kinds.PLAYING) && !activity.temp) return
        if (activity.mediaTitle.isEmpty() && !activity.mediaPlaying) return
        rest.put(activity.sectionTitle("NOW PLAYING", Glyphs.MUSIC))
        val card = activity.column(8)
        card.background = activity.surface()
        card.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        card.put(activity.text(activity.mediaTitle.ifEmpty { "Untitled" }, 14f, T.ink, Fonts.sansMedium).apply { maxLines = 2 })
        val by = listOf(activity.mediaArtist, if (activity.mediaPlaying) "playing" else "paused", activity.mediaKind).filter { it.isNotEmpty() }.joinToString(" · ")
        card.put(activity.text(by, 12f, T.muted).oneLine())
        val keys = activity.line(8)
        keys.put(activity.toggleKey(Glyphs.PREV, "back", false) { activity.webView.evaluateJavascript(Media.action("previoustrack"), null) }, 0, WRAP_CONTENT, 1f)
        keys.put(activity.toggleKey(if (activity.mediaPlaying) Glyphs.PAUSE else Glyphs.PLAY, if (activity.mediaPlaying) "pause" else "play", activity.mediaPlaying) {
            activity.webView.evaluateJavascript(Media.action(if (activity.mediaPlaying) "pause" else "play"), null)
            activity.webView.postDelayed({ fill() }, 300)
        }, 0, WRAP_CONTENT, 1f)
        keys.put(activity.toggleKey(Glyphs.NEXT, "ahead", false) { activity.webView.evaluateJavascript(Media.action("nexttrack"), null) }, 0, WRAP_CONTENT, 1f)
        card.put(keys)
        rest.put(card)
    }

    private fun adopt() {
        val site = activity.site
        val inner = Sheet(activity)
        inner.body.put(activity.text("Make it a full app", 18f, T.ink, Fonts.sansBold))
        inner.body.put(activity.text("It becomes a normal app on your list, with these privacy choices and its own settings. It starts remembering what you choose for new apps.", 13f, T.muted))
        val name = activity.field(site.label, "Name")
        inner.body.put(name)
        var keep = true
        val options = activity.card()
        options.switch(Glyphs.COOKIE, "keep what you’re signed into", "this peek’s logins, cookies and site data move into the new app instead of being erased", keep) { keep = it }
        inner.body.put(options.view)
        inner.body.put(activity.primaryButton("Make it an app", Glyphs.PLUS) {
            inner.dismiss()
            sheet.dismiss()
            Sessions.adopt(activity, name.text.toString().trim().ifEmpty { site.label }, keep)
        }, MATCH_PARENT, activity.dp(50))
        inner.show()
    }

    private fun session() {
        val site = activity.site
        playingCard()
        actions()
        toggles(false)
        val card = activity.card()
        card.choice(Glyphs.SHIELD, "blocking", if (site.session == "private") "private can step down to standard, never off" else null, levels(), site.blocking) { blocking(it) }
        card.stepper(Glyphs.TEXT, "text size", "${site.textSize}%", { change { it.textSize = (it.textSize - 10).coerceAtLeast(50) } }, { change { it.textSize = (it.textSize + 10).coerceAtMost(200) } })
        card.switch(Glyphs.ZOOM, "pinch to zoom", null, site.zoom) { on -> change { it.zoom = on } }
        card.action(Glyphs.SEARCH, "find on this page", null, "find") { close { FindBar.show(activity, "") } }
        security(card)
        card.link(Glyphs.SHIELD, "turn protections on step by step", null, "start from the lowest, then add one at a time") { stepUp() }
        card.value(Glyphs.EYE, "fingerprinting", site.fp.summary(), Fingerprint.describe(activity.fpCounts).let { if (it.isEmpty()) "nothing read on this page yet" else "this page read: $it" })
        if (site.session == "peek") {
            card.link(Glyphs.PLUS, "make it a full app", null, "keeps these privacy choices, and what you’re signed into if you like") { adopt() }
        } else if (SiteStore.get(activity, site.id) == null) {
            card.link(Glyphs.PLUS, "make it an app", null, "a normal app with its own settings; nothing from this private session comes along") {
                close { activity.startActivity(Intent(activity, AddActivity::class.java).putExtra(AddActivity.ADDRESS, activity.pageUrl.ifEmpty { site.url })) }
            }
        }
        rest.put(card.view)
        rest.put(activity.text(Sessions.about(site.session), 12f, T.muted))
        rest.put(activity.secondaryButton("End and erase", Glyphs.TRASH, T.danger) { close { activity.finishAndRemoveTask() } }, MATCH_PARENT, activity.dp(46))
    }

    private fun starter() {
        playingCard()
        actions()
        toggles(false)
        runList()
        rest.put(activity.secondaryButton("Everything else", Glyphs.CHEVRON_UP) { sheet.expand() }, MATCH_PARENT, activity.dp(44))
        rest.put(activity.text("Drag the handle up for everything. Settings › quick settings can open it big every time.", 12f, T.muted))
    }

    private fun everything() {
        val site = activity.site
        val defaults = activity.defaults
        playingCard()
        actions()
        if (site.shortcuts.isNotEmpty()) {
            rest.put(activity.sectionTitle("SHORTCUTS", Glyphs.GRID))
            val memory = MemoryStore.get(activity, site.id)
            val ordered = if (site.keeps(Kinds.SHORTCUTS)) site.shortcuts.sortedByDescending { memory.shortcuts[it.name] ?: 0 } else site.shortcuts
            val flow = Flow(activity)
            for (shortcut in ordered) {
                val chip = activity.secondaryButton(shortcut.name, Glyphs.ARROW_RIGHT) {
                    close {
                        MemoryStore.shortcut(activity, site, shortcut.name)
                        activity.webView.loadUrl(shortcut.url)
                    }
                }
                chip.minimumHeight = activity.dp(40)
                flow.addView(chip)
            }
            rest.put(flow)
        }
        toggles(true)

        rest.put(activity.sectionTitle("MORE FOR ${site.label.uppercase()}", Glyphs.SETTINGS))
        val more = activity.card()
        more.stepper(Glyphs.TEXT, "text size", "${site.textSize}%", { change { it.textSize = (it.textSize - 10).coerceAtLeast(50) } }, { change { it.textSize = (it.textSize + 10).coerceAtMost(200) } })
        more.switch(Glyphs.ZOOM, "pinch to zoom", null, site.zoom) { on -> change { it.zoom = on } }
        more.choice(Glyphs.LINK, "other sites open", null, listOf(Option("inside"), Option("browser"), Option("ask")), site.linksNow(defaults)) { key -> change { it.links = key } }
        more.choice(Glyphs.BAR, "when outside ${site.label}", null, listOf(Option("pill"), Option("top"), Option("bottom")), site.navStyleNow(defaults)) { key -> change { it.navStyle = key } }
        more.choice(Glyphs.SHIELD, "blocking", null, listOf(Option("off"), Option("standard", "std"), Option("strict")), site.blocking) { key -> blocking(key) }
        more.switch(Glyphs.WARN, "warn before risky other sites", null, site.warnRiskyNow(defaults)) { on -> change { it.warnRisky = on } }
        more.choice(Glyphs.CLOCK, "on open", null, listOf(Option("resume"), Option("start")), site.onOpen) { key -> change { it.onOpen = key } }
        more.choice(Glyphs.PHONE, "screen", null, listOf(Option("any"), Option("portrait", "up"), Option("landscape", "side")), site.orientation) { key -> change { it.orientation = key } }
        val canPrivate = site.ownStorage && Storage.separate
        more.switch(Glyphs.HIDDEN, "private mode", if (canPrivate) "applies from the next time it opens" else "needs its own storage", site.privateMode && canPrivate, canPrivate) { on -> change { it.privateMode = on } }
        more.link(Glyphs.EYE, "what ${site.label} remembers", "${site.remember.size} of ${Kinds.ALL.size}") { openMemory() }
        more.link(Glyphs.HAND, "controls", if (site.power.keysOn) "keys on" else null, "volume keys, swipes, your buttons") { openControls() }
        more.action(Glyphs.CAMERA, "full-page screenshot", if ((site.locks && site.hideRecents) || site.privateMode) "blocked while locked or private" else "the whole page as one image", "take") { close { activity.screenshot() } }
        if (Bubbles.supported()) more.action(Glyphs.BUBBLE, "open as a bubble", "floats over other apps", "open") { close { Bubbles.show(activity, site) } }
        more.link(Glyphs.SHIELD, "blocked on this page", activity.blockedHere.get().toString()) { openBlocking() }
        security(more)
        more.link(Glyphs.SHIELD, "turn protections on step by step", if (site.stepUp) "in progress" else null, "for when the site breaks: start from the lowest, then add one at a time") { stepUp() }
        more.link(Glyphs.EYE, "fingerprinting", site.fp.summary(), Fingerprint.describe(activity.fpCounts).let { if (it.isEmpty()) "nothing read on this page yet" else "this page read: $it" }) {
            close { activity.startActivity(Intent(activity, FingerprintActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id)) }
        }
        rest.put(more.view)

        runList()

        rest.put(activity.sectionTitle("INSERTIONS", Glyphs.CODE, "in load order"))
        val inserts = activity.card()
        inserts.switch(Glyphs.CODE, "insertions for ${site.label}", "all of them, built-in and yours", site.power.insertionsOn) { on ->
            site.power.insertionsOn = on
            insertionsChanged()
        }
        for (item in site.insertions) {
            val failure = AddonState.failure(activity, site.id, item.id)
            val detail = item.kindName + " · " + Describe.where(item) + (failure?.let { " · failed: ${it.message}" } ?: "")
            inserts.switch(Describe.icon(item), item.name.ifEmpty { "untitled" }, detail, item.on, site.power.insertionsOn) { on ->
                item.on = on
                insertionsChanged()
            }
        }
        inserts.link(Glyphs.HIDDEN, "hidden elements", site.hidden.size.toString(), site.hidden.take(3).joinToString(", ") { it.selector }.ifEmpty { "none yet; use select above" }) { openInsertions("hidden") }
        inserts.link(Glyphs.LIST, "all insertions", plural(site.insertions.size, "item"), "order, add from the web or a repository, auto targets") { openInsertions("") }
        inserts.action(Glyphs.WARN, "open once without insertions", "for when something on the page stopped working", "open") { close { activity.openWithoutInsertions() } }
        rest.put(inserts.view)

        val memory = MemoryStore.get(activity, site.id)
        if (site.keeps(Kinds.RECENT) && memory.recent.size > 1) {
            rest.put(activity.sectionTitle("RECENT PAGES", Glyphs.HISTORY))
            val recent = activity.card()
            for (visit in memory.recent.drop(1).take(4)) {
                recent.link(null, visit.title, shortAgo(visit.at)) { close { activity.webView.loadUrl(visit.url) } }
            }
            rest.put(recent.view)
        }

        rest.put(activity.primaryButton("All settings for ${site.label}", Glyphs.ARROW_RIGHT) { openSettings() }, MATCH_PARENT, activity.dp(52))
        val bar = activity.line(8)
        bar.put(activity.secondaryButton("clear data", Glyphs.TRASH, T.danger) { clearData() }, 0, activity.dp(48), 1f)
        rest.put(bar)
    }

    fun entries(): List<QuickEntry> {
        val site = activity.site
        val defaults = activity.defaults
        val out = mutableListOf<QuickEntry>()
        out += QuickEntry(Glyphs.RELOAD, "reload", null, "refresh page again") { close { activity.webView.reload() } }
        out += QuickEntry(Glyphs.SHARE, "share this page", null, "send link") { close { activity.share(activity.pageUrl, activity.pageTitle) } }
        out += QuickEntry(Glyphs.BROWSER, "open in browser", null, "chrome firefox external") { close { activity.openBrowser(activity.pageUrl) } }
        out += QuickEntry(Glyphs.PICKER, "select", "hide, target or copy part of the page", "hide element picker target copy selector") { close { activity.startHider() } }
        out += QuickEntry(Glyphs.MOON, "dark mode", null, "theme night", site.dark == "dark") { toggle("dark") }
        out += QuickEntry(Glyphs.DESKTOP, "desktop site", null, "desktop mode big screen", site.desktop) { toggle("desktop") }
        out += QuickEntry(Glyphs.FULL, "full screen", null, "immersive hide bars", site.fullScreen) { toggle("full") }
        out += QuickEntry(Glyphs.SUN, "keep the screen on", null, "screen on awake", site.screenOn) { toggle("screen") }
        out += QuickEntry(Glyphs.PULL, "pull to refresh", null, "pull refresh swipe down", site.pullToRefresh) { toggle("pull") }
        out += QuickEntry(Glyphs.SHIELD, "blocking", "ads and trackers", "block ads trackers filter", options = levels(), chosen = site.blocking, pick = { blocking(it) }) { }
        out += QuickEntry(Glyphs.ZOOM, "pinch to zoom", null, "zoom", site.zoom) { change { it.zoom = !it.zoom } }
        out += QuickEntry(Glyphs.TEXT, "text size", "${site.textSize}%", "font bigger smaller", options = listOf(Option("80", "80%"), Option("100", "100%"), Option("120", "120%"), Option("150", "150%")), chosen = site.textSize.toString(), pick = { key -> change { it.textSize = key.toInt() } }) { }
        out += QuickEntry(Glyphs.LINK, "other sites open", null, "links outside inside browser ask", options = listOf(Option("inside"), Option("browser"), Option("ask")), chosen = site.linksNow(defaults), pick = { key -> change { it.links = key } }) { }
        out += QuickEntry(Glyphs.BAR, "when outside ${site.label}", null, "pill bar navigation way back", options = listOf(Option("pill"), Option("top"), Option("bottom")), chosen = site.navStyleNow(defaults), pick = { key -> change { it.navStyle = key } }) { }
        out += QuickEntry(Glyphs.WARN, "warn before risky other sites", null, "warnings", site.warnRiskyNow(defaults)) { change { it.warnRisky = !it.warnRiskyNow(defaults) } }
        out += QuickEntry(Glyphs.CLOCK, "on open", null, "resume start page", options = listOf(Option("resume"), Option("start")), chosen = site.onOpen, pick = { key -> change { it.onOpen = key } }) { }
        out += QuickEntry(Glyphs.PHONE, "screen orientation", null, "rotate portrait landscape", options = listOf(Option("any"), Option("portrait", "up"), Option("landscape", "side")), chosen = site.orientation, pick = { key -> change { it.orientation = key } }) { }
        if (activity.temp) {
            out += QuickEntry(Glyphs.TRASH, "end and erase", null, "close private peek session") { close { activity.finishAndRemoveTask() } }
            return out
        }
        if (site.ownStorage && Storage.separate) out += QuickEntry(Glyphs.HIDDEN, "private mode", "applies from the next time it opens", "incognito private", site.privateMode) { change { it.privateMode = !it.privateMode } }
        out += QuickEntry(Glyphs.EYE, "what ${site.label} remembers", null, "memory storage history privacy cookies") { openMemory() }
        out += QuickEntry(Glyphs.HAND, "controls", "volume keys, swipes, your buttons", "buttons keys swipe volume") { openControls() }
        out += QuickEntry(Glyphs.CAMERA, "full-page screenshot", null, "screenshot capture image picture") { close { activity.screenshot() } }
        if (Bubbles.supported()) out += QuickEntry(Glyphs.BUBBLE, "open as a bubble", null, "float window bubble") { close { Bubbles.show(activity, site) } }
        out += QuickEntry(Glyphs.SHIELD, "blocked on this page", activity.blockedHere.get().toString(), "blocked requests allow") { openBlocking() }
        out += QuickEntry(Glyphs.CODE, "insertions for ${site.label}", "all of them, built-in and yours", "scripts styles userscripts", site.power.insertionsOn) {
            site.power.insertionsOn = !site.power.insertionsOn
            insertionsChanged()
        }
        for (item in site.insertions) {
            out += QuickEntry(Describe.icon(item), item.name.ifEmpty { "untitled" }, item.kindName, "insertion script style ${item.kindName}", item.on) {
                item.on = !item.on
                insertionsChanged()
            }
            if (item.on && item.kind != "css") out += QuickEntry(Glyphs.PLAY, "run “${item.name.ifEmpty { "untitled" }}”", null, "run script now") { close { activity.runInsertion(item.id) } }
        }
        out += QuickEntry(Glyphs.HIDDEN, "hidden elements", site.hidden.size.toString(), "hidden hide elements") { openInsertions("hidden") }
        out += QuickEntry(Glyphs.LIST, "all insertions", null, "scripts styles order repository") { openInsertions("") }
        out += QuickEntry(Glyphs.WARN, "open once without insertions", null, "broken safe mode") { close { activity.openWithoutInsertions() } }
        for (shortcut in site.shortcuts) out += QuickEntry(Glyphs.ARROW_RIGHT, shortcut.name, "shortcut", "shortcut go") { close { activity.webView.loadUrl(shortcut.url) } }
        val memory = MemoryStore.get(activity, site.id)
        if (site.keeps(Kinds.RECENT)) for (visit in memory.recent.drop(1).take(10)) out += QuickEntry(Glyphs.HISTORY, visit.title, pathOf(visit.url), "recent history page " + visit.url) { close { activity.webView.loadUrl(visit.url) } }
        out += QuickEntry(Glyphs.SETTINGS, "all settings for ${site.label}", null, "settings preferences options") { openSettings() }
        out += QuickEntry(Glyphs.TRASH, "clear data", "signs you out", "clear cookies delete reset") { clearData() }
        return out
    }

    private fun results() {
        rest.put(activity.card().apply {
            link(Glyphs.SEARCH, "find “$query” on this page", null, "highlights every match") { close { FindBar.show(activity, query) } }
        }.view)
        val terms = QuickEntry.terms(query)
        val found = entries().filter { it.matches(terms) }
        if (found.isEmpty()) {
            rest.put(activity.text("Nothing in quick settings matches “$query”.", 13f, T.muted))
            return
        }
        rest.put(activity.sectionTitle("IN QUICK SETTINGS", Glyphs.LIST, found.size.toString()))
        val list = activity.card()
        QuickEntry.render(list, found.take(30))
        rest.put(list.view)
    }

    private fun security(card: Card) {
        val site = activity.site
        if (activity.relaxed) {
            card.action(Glyphs.SHIELD, "protections are lowered for a check", "through the check? put them back", "protect") { close { activity.protectAgain(false) } }
        } else {
            card.action(Glyphs.SHIELD, "lower protections for a security check", "until you’re through: this load and the next, then they come back on their own", "lower") { close { activity.relaxNow(2) } }
        }
        card.link(Glyphs.LIST, "pages with lower protections", site.relaxPages.size.toString(), "always lower them on these pages only, like a site’s check page") {
            close { activity.relaxPagesSheet(site, activity.pageUrl) { activity.saveSite(); activity.installScripts() } }
        }
    }

    private fun stepUp() {
        val site = activity.site
        sheet.dismiss()
        if (StepUp.isLowest(site) || site.stepUp) {
            StepUpSheet(activity).show()
            return
        }
        activity.ask("Start from the lowest protections?", "Ad blocking and fingerprinting protection turn off, then you turn each back on and see what breaks.", "Start") {
            StepUp.lowest(site)
            activity.saveSite()
            activity.applySettings()
            activity.installScripts()
            activity.webView.reload()
            StepUpSheet(activity).show()
        }
    }

    private fun blocking(key: String) {
        change {
            it.blocking = key
            if (key != "off") it.lastLevel = key
        }
        activity.webView.reload()
    }

    private fun openMemory() = close { activity.startActivity(Intent(activity, MemoryActivity::class.java).putExtra(AppSettingsActivity.SITE, activity.site.id)) }

    private fun openControls() = close { activity.startActivity(Intent(activity, ControlsActivity::class.java).putExtra(AppSettingsActivity.SITE, activity.site.id)) }

    private fun openBlocking() = close { activity.startActivity(Intent(activity, BlockingActivity::class.java).putExtra(AppSettingsActivity.SITE, activity.site.id)) }

    private fun openSettings() = close { activity.startActivity(Intent(activity, AppSettingsActivity::class.java).putExtra(AppSettingsActivity.SITE, activity.site.id)) }

    private fun openInsertions(tab: String) = close {
        activity.startActivity(Intent(activity, InsertionsActivity::class.java).putExtra(AppSettingsActivity.SITE, activity.site.id).putExtra(InsertionsActivity.TAB, tab))
    }

    private fun clearData() {
        val site = activity.site
        close {
            activity.ask("Clear ${site.label}’s data?", "You’ll be signed out, and what Leyden kept for it is cleared.", "Clear", danger = true) {
                MemoryStore.forgetAll(activity, site.id)
                Storage.clearAll(activity, site, activity.webView) { activity.webView.loadUrl(site.url) }
            }
        }
    }

    private fun insertionsChanged() {
        activity.saveSite()
        activity.installScripts()
        activity.webView.reload()
        fill()
        refresh?.invoke()
    }

    private fun toggle(key: String) {
        when (key) {
            "dark" -> change { it.dark = if (it.dark == "dark") "site" else "dark" }
            "desktop" -> change { it.desktop = !it.desktop }
            "full" -> change { it.fullScreen = !it.fullScreen }
            "screen" -> change { it.screenOn = !it.screenOn }
            "pull" -> change { it.pullToRefresh = !it.pullToRefresh }
            "block" -> {
                change {
                    if (it.session == "private") {
                        it.blocking = if (it.blocking == "strict") "standard" else "strict"
                    } else if (it.blocking == "off") {
                        it.blocking = it.lastLevel
                    } else {
                        it.lastLevel = it.blocking
                        it.blocking = "off"
                    }
                }
                activity.webView.reload()
            }
        }
    }
}
