package org.bond.pwaapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

class AddActivity : Screen() {
    private val main = Handler(Looper.getMainLooper())
    private val defaults by lazy { Defaults.get(this) }
    private var step = 1
    private var address = ""
    private var busy = ""
    private var found: Found? = null
    private var problem: String? = null
    private var certificate: Certificate? = null
    private var check: CheckResult? = null
    private var running: SiteCheck? = null
    private var draft = Site()
    private var useSiteIcon = true
    private var blockingNote = "from your defaults"
    private var addressField: EditText? = null
    private val events = LinkedHashMap<String, LookupEvent>()
    private var progressBox: LinearLayout? = null
    private var early = false
    private var kept: Site? = null
    private var useKept = false

    private class Item(val title: String, val detail: String, val icon: String, val tone: Int, val action: String? = null, val done: Boolean = false, val onAction: (() -> Unit)? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = Site.fresh(defaults)
        Blocker.ensure(this)
        intent?.getStringExtra(ADDRESS)?.let { given ->
            address = given
            main.post { lookUp() }
        }
        if (intent?.action == Intent.ACTION_SEND) {
            address = sharedAddress(intent.getStringExtra(Intent.EXTRA_TEXT))
            if (address.isNotEmpty()) main.post { lookUp() }
        }
    }

    override fun onDestroy() {
        running?.cancel()
        super.onDestroy()
    }

    override fun onBack(): Boolean {
        if (step > 1) {
            step--
            redrawTop()
            return true
        }
        return false
    }

    override fun render() {
        when (step) {
            1 -> stepOne()
            2 -> stepTwo()
            else -> stepThree()
        }
    }

    private fun stepper(now: Int) {
        val names = listOf("check", "name and look", "privacy")
        val row = line(6)
        names.forEachIndexed { index, name ->
            val number = index + 1
            val part = column(6)
            val bar = View(this)
            bar.background = rounded(if (number <= now) T.accent else T.switchOff, dpf(2f))
            part.put(bar, MATCH_PARENT, dp(4))
            val label = line(5)
            val dot = mono(if (number < now) "✓" else number.toString(), 9f, if (number <= now) T.onAccent else T.muted, 0f)
            dot.gravity = Gravity.CENTER
            dot.background = rounded(if (number <= now) T.accent else T.key, dpf(8f))
            label.put(dot, dp(16), dp(16))
            label.put(text(name, 11f, if (number == now) T.ink else T.faint, if (number == now) Fonts.sansBold else Fonts.sans).oneLine(), WRAP_CONTENT)
            part.put(label)
            row.put(part, 0, WRAP_CONTENT, 1f)
        }
        content.put(row)
    }

    private fun stepOne() {
        header("Add a site", "step 1 of 3 · check it before it’s an app")
        stepper(1)
        val entry = line(8)
        val input = field(address, "example.com or an address on your network", true, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input.imeOptions = EditorInfo.IME_ACTION_GO
        input.setOnEditorActionListener { _, _, _ ->
            address = input.text.toString()
            lookUp()
            true
        }
        addressField = input
        entry.put(input, 0, dp(50), 1f)
        entry.put(secondaryButton("Check", Glyphs.SEARCH) {
            address = input.text.toString()
            lookUp()
        }, WRAP_CONTENT, dp(50))
        content.put(entry)

        if (events.isNotEmpty()) {
            content.put(sectionTitle(if (busy.isNotEmpty()) "CHECKING" else "WHAT IT CHECKED", Glyphs.SEARCH, if (busy.isNotEmpty()) "as it happens" else null))
            val box = column()
            box.background = surface()
            box.setPadding(dp(14), dp(4), dp(14), dp(6))
            progressBox = box
            paintProgress()
            content.put(box)
            if (running != null) {
                content.put(quietButton("Stop counting and show what it found", Glyphs.CLOSE, T.ink) {
                    early = true
                    running?.stopEarly()
                }, MATCH_PARENT, dp(40))
            }
        } else {
            progressBox = null
        }

        val error = problem
        if (error != null && busy.isEmpty()) {
            val card = card()
            val cert = certificate
            if (cert != null) {
                card.custom(itemView(Item("Its certificate isn’t trusted by Android", "Issued to ${cert.subject.ifEmpty { "?" }} by ${cert.issuer.ifEmpty { "?" }}, valid until ${cert.until}.\nSHA-256 ${cert.fingerprint.take(23)}…", Glyphs.WARN, T.danger)))
                card.custom(note("If this is your own server with a certificate you made, check the fingerprint matches, then trust it for this app only. Anything else showing this is a reason to stop."))
                card.action(Glyphs.LOCK, "Trust this certificate for this app", null, "trust it") {
                    draft.trustedCertificate = cert.fingerprint
                    lookUp()
                }
            } else {
                card.custom(itemView(Item("Couldn’t read the site", error, Glyphs.WARN, T.danger)))
                card.custom(note("Some sites refuse anything that isn’t a full browser, or are only reachable in certain ways. You can add it anyway: the name, icon and color can be set by hand in the next step."))
                card.action(Glyphs.ARROW_RIGHT, "Add it anyway", "with your usual protections", "continue") { anyway(false) }
                card.action(Glyphs.SHIELD, "Add it with the lowest protections", "no ad blocking or fingerprinting protection; when it opens, you turn them on one at a time", "continue") { anyway(true) }
            }
            content.put(card.view)
        }

        val site = found
        if (site != null) results(site)

        bottom.put(quietButton("Cancel", Glyphs.CLOSE) { finish() }, WRAP_CONTENT, dp(52))
        bottom.put(spacer(), 0, 1, 1f)
        val next = primaryButton("Next · name and look", Glyphs.ARROW_RIGHT) {
            step = 2
            redrawTop()
        }
        next.isEnabled = site != null && busy.isEmpty()
        next.alpha = if (next.isEnabled) 1f else 0.4f
        bottom.put(next, dp(230), dp(52))
        showBottom()
    }

    private fun results(site: Found) {
        kept?.let { saved ->
            val card = card()
            card.switch(Glyphs.HISTORY, "use the settings you kept", "From when you removed ${saved.label}: name, icon, look, blocking, rules, hidden elements, controls and addresses. Its lock, scripts, keys and logins weren’t kept.", useKept) { on ->
                useKept = on
                if (on) {
                    applyKept()
                } else {
                    draft = Site.fresh(defaults)
                    fill(site)
                }
                redraw()
            }
            content.put(card.view)
        }
        val result = check
        val https = site.url.startsWith("https:")
        val trackers = if (result?.ran == true && result.listsReady) result.trackers.size.toString() else "—"
        content.put(readouts(listOf(
            Triple("MANIFEST", if (site.fromManifest) "found" else "none", Glyphs.SCRIPT),
            Triple("ICON", if (site.iconSize > 0) "${site.iconSize}px" else "letter", Glyphs.SPLASH),
            Triple("HTTPS", if (https) "yes" else "no", Glyphs.LOCK),
            Triple("TRACKERS", trackers, Glyphs.SHIELD),
        )))
        if (result != null && result.ran) scriptsCard(result)

        val decide = mutableListOf<Item>()
        val know = mutableListOf<Item>()
        val fine = mutableListOf<String>()
        val self = isSelfHosted(hostOf(site.url))
        if (result?.safeBrowsing == true) decide += Item("On Google Safe Browsing’s list of unsafe sites", "Android warns about this site because it’s known for phishing or harmful downloads.", Glyphs.WARN, T.danger)
        if (result != null && result.ran && result.listsReady && result.trackers.isNotEmpty()) {
            val on = draft.blocking != "off"
            decide += Item(
                "${plural(result.trackers.size, "tracker")} on the first page",
                "From ${plural(result.trackerDomains.size, "company", "companies")}, going by the filter lists.",
                Glyphs.SHIELD, T.accent, if (on) "blocking on" else "turn blocking on", on,
            ) {
                if (draft.blocking == "off") {
                    draft.blocking = "strict"
                    draft.lastLevel = "strict"
                    blockingNote = "strict, because of step 1"
                } else {
                    draft.blocking = "off"
                    blockingNote = "off, by your choice in step 1"
                }
                redraw()
            }
        }
        if (result != null && result.ran && result.thirdCookies > 0) {
            val separate = !draft.thirdPartyCookies && (draft.ownStorage || !Storage.separate)
            decide += Item(
                "${plural(result.thirdCookies, "cookie")} from other sites",
                "Set before you did anything at all. Keeping it separate blocks them and keeps its storage away from your other apps.",
                Glyphs.COOKIE, T.accent, if (separate) "kept separate" else "keep separate", separate,
            ) {
                if (separate) {
                    draft.thirdPartyCookies = true
                } else {
                    draft.thirdPartyCookies = false
                    draft.ownStorage = true
                }
                redraw()
            }
        }
        val prints = result?.fingerprinting?.toMap().orEmpty()
        if (result != null && result.ran && prints.isNotEmpty()) {
            val strong = prints.keys.any { it in setOf("canvas", "webgl", "audio") }
            val on = draft.fp.level == "strict"
            val item = Item(
                "It read details that can identify your device",
                "On the first page: ${Fingerprint.describe(prints)}. Not every read is tracking, but these are the usual ways sites tell devices apart.",
                Glyphs.EYE, if (strong) T.accent else T.info, if (strong) (if (on) "strict protection on" else "use strict protection") else null, on,
            ) {
                draft.fp.apply(if (on) "standard" else "strict")
                redraw()
            }
            if (strong) decide += item else know += item
        }
        if (!https && !self) decide += Item("It isn’t encrypted", "It uses plain http, so anyone on the same network can read what you send and see.", Glyphs.UNLOCK, T.danger)

        if (!https && self) know += Item("Plain http on your own network", "Fine on a private network like Tailscale or your LAN. Others on that network could read it.", Glyphs.UNLOCK, T.info)
        if (defaults.checkCertificate && result != null && result.mixed > 0) know += Item("${plural(result.mixed, "thing")} load over plain http", "Everything else on the page is encrypted.", Glyphs.UNLOCK, T.info)
        if (defaults.checkCertificate && site.redirectedTo.isNotEmpty()) know += Item("It sent you to ${site.redirectedTo.joinToString(", ")}", "The address you typed redirects there, so that’s what the app opens.", Glyphs.ARROW_RIGHT, T.info)
        if (site.googleSignIn || result?.google == true) know += Item("Google sign-in opens in your browser", "Google blocks it inside apps like this one, so that step happens in your browser.", Glyphs.GLOBE, T.info)
        if (!site.fromManifest) know += Item("No app manifest", "The name, icon and color were guessed from the page.", Glyphs.SCRIPT, T.info)
        if (!site.mobileLayout) know += Item("Made for big screens", "It has no mobile layout. Pinch to zoom stays on so you can read it.", Glyphs.DESKTOP, T.info)
        if (site.shortcuts.isNotEmpty()) know += Item("${plural(site.shortcuts.size, "shortcut")} from the site", site.shortcuts.joinToString(", ") { it.name } + ". They show in quick settings.", Glyphs.GRID, T.info)
        if (result != null && !result.ran) know += Item("Trackers and cookies weren’t counted", result.reason, Glyphs.SHIELD, T.info)
        if (result != null && result.ran && early) know += Item("You stopped counting early", "Trackers, cookies and scripts are what it saw before you stopped, so there may be more.", Glyphs.CLOCK, T.info)
        if (result != null && result.ran && !result.listsReady) know += Item("Trackers weren’t counted", "The filter lists aren’t downloaded yet. Get them in Settings › lists.", Glyphs.LIST, T.info)

        if (https) fine += "https"
        if (defaults.checkCertificate && site.redirectedTo.isEmpty()) fine += "no redirects"
        if (result != null && result.ran && defaults.checkSafeBrowsing && !result.safeBrowsing && !Blocker.listed(hostOf(site.url))) fine += "not on any blocklist"
        if (site.mobileLayout) fine += "mobile layout"
        if (site.fromManifest) fine += "app manifest"
        if (result != null && result.ran && result.listsReady && result.trackers.isEmpty()) fine += "no trackers"
        if (result != null && result.ran && result.thirdCookies == 0) fine += "no cookies from other sites"
        if (result != null && result.ran && Storage.earlyScripts && result.fingerprinting.isEmpty()) fine += "no fingerprinting seen"
        if (result != null && result.ran && defaults.checkCertificate && result.mixed == 0 && https) fine += "all encrypted"

        val summary = column(4)
        summary.background = surface()
        summary.setPadding(dp(16), dp(14), dp(16), dp(15))
        summary.put(mono("BEFORE YOU ADD IT", 9f, T.muted))
        val headline = when {
            running != null -> "Still opening it out of sight…"
            else -> when (decide.size) {
                0 -> "Nothing to decide. It looks fine."
            1 -> "1 thing to decide before adding it"
                else -> "${decide.size} things to decide before adding it"
            }
        }
        summary.put(text(headline, 17f, T.ink, Fonts.sansBold))
        summary.put(text("Each one below says what it means. Anything you choose here carries into step 3, where you can change it again.", 12f, T.muted))
        content.put(summary)

        group("DECIDE", decide, T.accent)
        group("GOOD TO KNOW", know, T.info)
        if (fine.isNotEmpty()) {
            content.put(sectionTitle("✓ CHECKED AND FINE"))
            val wrap = Flow(this)
            for (name in fine) {
                val chip = line(5)
                chip.background = rounded(T.surface, dpf(8f), dp(1), T.goodLine)
                chip.setPadding(dp(9), dp(6), dp(10), dp(6))
                chip.put(glyph(Glyphs.CHECK, T.good, 12, 2.4f), dp(12), dp(12))
                chip.put(text(name, 12f, T.ink), WRAP_CONTENT)
                wrap.addView(chip)
            }
            content.put(wrap)
        }
    }

    private fun scriptsCard(result: CheckResult) {
        val total = result.scriptsOwn + result.scriptsInline + result.scriptHosts.values.sum()
        content.put(sectionTitle("SCRIPTS ON THE FIRST PAGE · $total", Glyphs.SCRIPT, "other pages may differ"))
        val box = card()
        box.value(Glyphs.HOME, "${result.scriptsOwn} from its own site", result.scriptsOwn.toString(), hostOf(draft.url))
        if (result.scriptHosts.isNotEmpty()) {
            val row = box.value(Glyphs.GLOBE, "${result.scriptHosts.values.sum()} from ${plural(result.scriptHosts.size, "other site")}", result.scriptHosts.values.sum().toString())
            val tags = Flow(this)
            for ((host, count) in result.scriptHosts.entries.sortedByDescending { it.value }) {
                val tracker = result.trackerDomains.any { Filters.within(host, it) }
                tags.addView(tagView("$host · $count" + if (tracker) " · tracker" else "", if (tracker) T.danger else T.body, if (tracker) T.danger and 0x00FFFFFF or 0x1A000000 else T.bg))
            }
            ((row as android.view.ViewGroup).getChildAt(1) as? android.widget.LinearLayout)?.put(tags)
        }
        box.value(Glyphs.CODE, "${result.scriptsInline} inline", result.scriptsInline.toString(), "written into the page itself")
        content.put(box.view)
        content.put(note("Counted while it opened once, out of sight. Pages you visit later can load more scripts, or different ones. The count is kept with the app’s data so you can compare later."))
    }

    private fun group(title: String, items: List<Item>, color: Int) {
        if (items.isEmpty()) return
        val head = line(8)
        head.setPadding(dp(4), dp(4), dp(4), 0)
        head.put(mono(title), WRAP_CONTENT)
        head.put(badge(items.size.toString(), color), WRAP_CONTENT)
        content.put(head)
        val card = card()
        items.forEach { card.custom(itemView(it)) }
        content.put(card.view)
    }

    private fun itemView(item: Item): View {
        val row = line(12)
        row.gravity = Gravity.TOP
        row.setPadding(0, dp(12), 0, dp(12))
        val mark = FrameLayout(this)
        mark.background = rounded(item.tone and 0x00FFFFFF or 0x22000000, dpf(9f))
        mark.addView(glyph(item.icon, item.tone, 16, 2f), FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        row.put(mark, dp(32), dp(32))
        val words = column(3)
        words.put(text(item.title, 13f, T.ink, Fonts.sansMedium))
        words.put(text(item.detail, 12f, T.muted))
        val action = item.action
        if (action != null) {
            val chip = line(5)
            chip.setPadding(dp(10), 0, dp(11), dp(1))
            if (item.done) {
                chip.background = rounded(T.surface, dpf(8f), dp(1), T.goodLine)
                chip.put(glyph(Glyphs.CHECK, T.good, 12, 2.4f), dp(12), dp(12))
                chip.put(text(action, 12f, T.good, Fonts.sansMedium), WRAP_CONTENT)
            } else {
                chip.background = primaryBack(8f)
                chip.put(text(action, 12f, T.onAccent, Fonts.sansMedium), WRAP_CONTENT)
            }
            chip.isClickable = true
            chip.contentDescription = action
            chip.setOnClickListener { item.onAction?.invoke() }
            words.put(chip, WRAP_CONTENT, dp(30)).margins(0, dp(6), 0, 0)
        }
        row.put(words, 0, WRAP_CONTENT, 1f)
        return row
    }

    private fun lookUp() {
        val typed = address.trim()
        if (typed.isEmpty()) {
            toast("Type the site’s address")
            return
        }
        running?.cancel()
        running = null
        found = null
        problem = null
        certificate = null
        check = null
        early = false
        kept = null
        useKept = false
        draft = Site.fresh(defaults)
        events.clear()
        events["respond"] = LookupEvent("respond", "Asking ${hostOf(normalizeAddress(typed)).ifEmpty { typed }}…", "waiting for an answer", "running")
        busy = "Reading the site…"
        addressField?.let { hideKeyboard(it) }
        redraw()
        val trusted = draft.trustedCertificate
        Thread {
            val outcome = try {
                Result.success(SiteLookup.lookup(typed, trusted) { event ->
                    main.post {
                        if (isDestroyed) return@post
                        events[event.key] = event
                        paintProgress()
                    }
                })
            } catch (error: Exception) {
                Result.failure(error)
            }
            val cert = (outcome.exceptionOrNull() as? CertificateProblem)?.let { Trust.capture(it.address) }
            main.post {
                if (isDestroyed) return@post
                outcome.onSuccess { site -> foundSite(site) }
                outcome.onFailure { error ->
                    busy = ""
                    events.entries.filter { it.value.state == "running" }.forEach { (key, event) -> events[key] = LookupEvent(key, event.title.removeSuffix("…"), "stopped here", "bad") }
                    problem = (error as? Exception)?.let { SiteLookup.plainError(it) } ?: error.javaClass.simpleName
                    certificate = cert
                    redraw()
                }
            }
        }.start()
    }

    private fun anyway(lowest: Boolean) {
        val url = normalizeAddress(address.trim())
        val host = hostOf(url)
        if (host.isEmpty()) {
            toast("That isn’t an address Leyden can open")
            return
        }
        val made = Found(
            url = url,
            name = host.removePrefix("www."),
            scope = url.substringBefore("://") + "://" + host + "/",
            color = null,
            background = null,
            orientation = "any",
            icon = null,
            iconSize = 0,
            maskable = false,
            fromManifest = false,
            shortcuts = emptyList(),
            mobileLayout = true,
            redirectedTo = emptyList(),
            googleSignIn = false,
        )
        found = made
        problem = null
        certificate = null
        draft = Site.fresh(defaults)
        fill(made)
        kept = Kept.forAddress(this, url)
        useKept = kept != null
        if (useKept) applyKept()
        check = CheckResult().apply { reason = "The site couldn’t be read, so it wasn’t checked." }
        events["skip"] = LookupEvent("skip", "Continuing without the check", if (lowest) "lowest protections; you’ll turn them on when it opens" else "with your usual protections", "note")
        if (lowest) {
            StepUp.lowest(draft)
            blockingNote = "off for now; you turn it on step by step when it opens"
        }
        step = 2
        redrawTop()
    }

    private fun fill(site: Found) {
        draft.name = site.name
        draft.url = site.url
        draft.scope = site.scope
        draft.color = site.color ?: Site.DEFAULT_COLOR
        draft.background = site.background ?: draft.color
        draft.orientation = site.orientation
        draft.shortcuts = site.shortcuts.toMutableList()
        draft.power.shareTarget = site.shareTarget
        draft.zoom = true
        useSiteIcon = site.icon != null
    }

    private fun applyKept() {
        val saved = kept ?: return
        val site = found ?: return
        draft = saved.copy()
        draft.power.shareTarget = site.shareTarget
        if (draft.shortcuts.isEmpty()) draft.shortcuts = site.shortcuts.toMutableList()
    }

    private fun foundSite(site: Found) {
        found = site
        fill(site)
        kept = Kept.forAddress(this, site.url)
        useKept = kept != null
        if (useKept) applyKept()
        if (defaults.checkTrackers || defaults.checkSafeBrowsing) {
            busy = "Opening it once, out of sight, to count trackers and cookies…"
            events["open"] = LookupEvent("open", "Opening it out of sight…", "in a throwaway storage, to count trackers and cookies", "running")
            val checker = SiteCheck(this, root, site.url, draft.trustedCertificate, defaults.checkSafeBrowsing, { result ->
                events["open"] = live(result)
                paintProgress()
            }) { result ->
                running = null
                check = result
                busy = ""
                events["open"] = finished(result)
                redraw()
            }
            running = checker
            redraw()
            checker.start()
        } else {
            busy = ""
            redraw()
        }
    }

    private fun counts(result: CheckResult): String {
        val parts = mutableListOf(plural(result.requests, "request"))
        if (result.listsReady) parts += plural(result.trackers.size, "tracker")
        parts += plural(result.thirdHosts.size, "other site")
        if (result.mixed > 0) parts += "${result.mixed} plain http"
        return parts.joinToString(" · ")
    }

    private fun seconds(from: Long, to: Long): String = String.format(java.util.Locale.US, "%.1f s", (to - from).coerceAtLeast(0) / 1000.0)

    private fun live(result: CheckResult): LookupEvent {
        val now = System.currentTimeMillis()
        return when (result.phase) {
            "opening" -> LookupEvent("open", "Opening it out of sight…", "${seconds(result.startedAt, now)} · " + counts(result), "running")
            "loading" -> LookupEvent("open", "Loading the page…", "showed something after ${seconds(result.startedAt, result.shownAt)} · " + counts(result), "running")
            "settling" -> LookupEvent("open", "Loaded in ${seconds(result.startedAt, result.loadedAt)}, watching for late scripts…", counts(result), "running")
            else -> LookupEvent("open", "Counting scripts and cookies…", counts(result), "running")
        }
    }

    private fun finished(result: CheckResult): LookupEvent = when {
        !result.ran -> LookupEvent("open", "Didn’t open it out of sight", result.reason, "note")
        result.safeBrowsing -> LookupEvent("open", "Android flagged it as unsafe", "Safe Browsing stopped it loading", "bad")
        !result.loaded -> LookupEvent("open", if (early) "Stopped early" else "It never finished loading", counts(result) + if (early) "" else " · counted what loaded in 15 s", "note")
        else -> LookupEvent("open", if (early) "Stopped early" else "Opened and counted", "loaded in ${seconds(result.startedAt, result.loadedAt)} · " + counts(result) +
            " · ${plural(result.scriptsOwn + result.scriptsInline + result.scriptHosts.values.sum(), "script")} · ${plural(result.thirdCookies, "cookie")} from other sites", "done")
    }

    private fun paintProgress() {
        val box = progressBox ?: return
        box.removeAllViews()
        var first = true
        for (event in events.values) {
            if (!first) {
                val rule = View(this)
                rule.setBackgroundColor(T.rule)
                box.put(rule, MATCH_PARENT, dp(1))
            }
            first = false
            val row = line(12)
            row.gravity = Gravity.TOP
            row.setPadding(0, dp(10), 0, dp(10))
            val mark = FrameLayout(this)
            when (event.state) {
                "running" -> {
                    val spinner = android.widget.ProgressBar(this)
                    spinner.indeterminateTintList = android.content.res.ColorStateList.valueOf(T.accent)
                    mark.addView(spinner, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                }
                else -> {
                    val tone = when (event.state) {
                        "done" -> T.good
                        "bad" -> T.danger
                        else -> T.info
                    }
                    mark.background = rounded(tone and 0x00FFFFFF or 0x22000000, dpf(8f))
                    mark.addView(glyph(when (event.state) {
                        "done" -> Glyphs.CHECK
                        "bad" -> Glyphs.WARN
                        else -> Glyphs.INFO
                    }, tone, 13, 2.4f), FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER))
                }
            }
            row.put(mark, dp(24), dp(24))
            val words = column(2)
            words.put(text(event.title, 13f, T.ink, Fonts.sansMedium))
            if (event.detail.isNotEmpty()) words.put(text(event.detail, 11f, T.muted))
            row.put(words, 0, WRAP_CONTENT, 1f)
            row.contentDescription = event.title + ", " + event.detail
            box.put(row)
        }
    }

    private fun hideKeyboard(view: View) {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun iconBitmap(): Bitmap {
        val site = found
        if (useKept) kept?.let { saved -> Kept.icon(this, saved.id)?.let { return it } }
        val source = if (useSiteIcon) site?.icon else null
        return LauncherIcons.make(source, site?.maskable == true && useSiteIcon, draft.background, draft.label)
    }

    private fun stepTwo() {
        header("Add a site", "step 2 of 3 · name and look")
        stepper(2)
        val preview = column(8)
        preview.gravity = Gravity.CENTER_HORIZONTAL
        preview.background = surface()
        preview.setPadding(dp(16), dp(18), dp(16), dp(16))
        val image = android.widget.ImageView(this)
        val bitmap = iconBitmap()
        val inset = bitmap.width / 6
        image.setImageBitmap(Bitmap.createBitmap(bitmap, inset, inset, bitmap.width - inset * 2, bitmap.height - inset * 2))
        image.background = rounded(draft.background, dpf(20f))
        image.clipToOutline = true
        preview.put(image, dp(72), dp(72))
        preview.put(text(draft.label, 15f, T.ink, Fonts.sansMedium).apply { gravity = Gravity.CENTER })
        preview.put(mono(hostOf(draft.url), 11f, T.muted, 0f).apply { gravity = Gravity.CENTER })
        content.put(preview)

        content.put(sectionTitle("NAME", Glyphs.TAG))
        val name = field(draft.name, "Name")
        name.setOnFocusChangeListener { _, focused -> if (!focused) draft.name = name.text.toString().trim() }
        name.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                draft.name = s?.toString()?.trim().orEmpty()
            }
        })
        content.put(name)

        content.put(sectionTitle("LOOK", Glyphs.COLOR))
        val look = card()
        if (found?.icon != null) {
            look.choice(Glyphs.SPLASH, "icon", null, listOf(Option("site", "site’s own"), Option("letter")), if (useSiteIcon) "site" else "letter") {
                useSiteIcon = it == "site"
                draft.siteIcon = useSiteIcon
                redraw()
            }
        }
        look.custom(swatches())
        look.choice(Glyphs.BAR, "bar color", "the strip behind the status bar", listOf(Option("page", "follows page"), Option("app", "app color")), draft.barColor) {
            draft.barColor = it
            redraw()
        }
        look.switch(Glyphs.SPLASH, "splash while it opens", "its icon on its color until the page shows", draft.splash) { draft.splash = it }
        look.choice(Glyphs.PHONE, "screen", null, listOf(Option("any"), Option("portrait", "up"), Option("landscape", "side")), draft.orientation) {
            draft.orientation = it
            redraw()
        }
        content.put(look.view)

        content.put(sectionTitle("WHERE IT OPENS", Glyphs.LINK))
        val where = card()
        where.link(Glyphs.HOME, "start page", draft.url.removePrefix("https://").removePrefix("http://")) {
            promptText("Start page", draft.url, "https://…", true) {
                draft.url = normalizeAddress(it)
                redraw()
            }
        }
        where.link(Glyphs.GLOBE, "stays in the app", draft.scope.removePrefix("https://").removePrefix("http://").ifEmpty { hostOf(draft.url) }, "pages under this address count as the app") {
            promptText("Stays in the app", draft.scope, "https://…/", true) {
                draft.scope = it.trim()
                redraw()
            }
        }
        content.put(where.view)

        bottom.put(quietButton("Back", Glyphs.ARROW_LEFT) {
            draft.name = name.text.toString().trim()
            step = 1
            redrawTop()
        }, WRAP_CONTENT, dp(52))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Next · privacy", Glyphs.ARROW_RIGHT) {
            draft.name = name.text.toString().trim()
            step = 3
            redrawTop()
        }, dp(200), dp(52))
        showBottom()
    }

    private fun swatches(): View {
        val box = column(8)
        box.setPadding(0, dp(10), 0, dp(10))
        val top = line(10)
        top.put(glyph(Glyphs.COLOR), dp(17), dp(17))
        top.put(text("color", 13f), 0, WRAP_CONTENT, 1f)
        top.put(mono(colorHex(draft.background), 11f, T.muted, 0f), WRAP_CONTENT)
        box.put(top)
        val site = found
        val colors = linkedSetOf<Int>()
        site?.color?.let { colors += it }
        site?.background?.let { colors += it }
        colors += listOf(0xFF1C1C1C, 0xFFFFFFFF, 0xFF2563EB, 0xFF0B7F72, 0xFFC2410C, 0xFF7C3AED, 0xFFC9A24A).map { it.toInt() }
        val row = line(8)
        for (color in colors.take(8)) {
            val dot = View(this)
            val picked = color == draft.background
            dot.background = rounded(color, dpf(8f), dp(if (picked) 3 else 1), if (picked) T.accent else T.border)
            dot.contentDescription = colorHex(color)
            dot.setOnClickListener {
                draft.background = color
                draft.color = color
                redraw()
            }
            row.put(dot, dp(30), dp(30))
        }
        val custom = text("#", 13f, T.ink, Fonts.monoMedium)
        custom.gravity = Gravity.CENTER
        custom.background = secondaryBack(8f)
        custom.contentDescription = "type a color"
        custom.setOnClickListener {
            promptText("Color", colorHex(draft.background), "#1E2A24", true) { typed ->
                val color = parseCssColor(typed)
                if (color == null) {
                    toast("That isn’t a color")
                } else {
                    draft.background = color
                    draft.color = color
                    redraw()
                }
            }
        }
        row.put(custom, dp(30), dp(30))
        box.put(row)
        return box
    }

    private fun stepThree() {
        header("Add a site", "step 3 of 3 · privacy")
        stepper(3)
        content.put(sectionTitle("STORAGE", Glyphs.BOX))
        val storage = card()
        storage.switch(Glyphs.BOX, "its own storage, apart from your other apps", if (Storage.separate) "separate cookies, logins and site data" else "needs Android System WebView 110 or newer", draft.ownStorage && Storage.separate, Storage.separate) {
            draft.ownStorage = it
            redraw()
        }
        storage.choice(Glyphs.COOKIE, "cookies from other sites", null, listOf(Option("allow"), Option("block")), if (draft.thirdPartyCookies) "allow" else "block") {
            draft.thirdPartyCookies = it == "allow"
            redraw()
        }
        val canClear = draft.ownStorage && Storage.separate
        storage.switch(Glyphs.TRASH, "clear what it stored when closed", if (canClear) "you’ll sign in each time" else "needs its own storage", draft.clearOnClose && canClear, canClear) { draft.clearOnClose = it }
        content.put(storage.view)

        content.put(sectionTitle("WHAT PWA APP KEEPS FOR IT", Glyphs.EYE, "from your defaults"))
        val keeps = card()
        for (kind in Kinds.ALL) {
            keeps.switch(Kinds.icon(kind), Kinds.name(kind), null, kind in draft.remember) { on ->
                if (on) draft.remember += kind else draft.remember -= kind
            }
        }
        keeps.custom(fullButton(secondaryButton("keep nothing for this app", Glyphs.HIDDEN) {
            draft.remember.clear()
            redraw()
        }))
        content.put(keeps.view)

        content.put(sectionTitle("BLOCKING AND WARNINGS", Glyphs.SHIELD, blockingNote))
        val blocking = card()
        blocking.choice(Glyphs.SHIELD, "ads and trackers", null, listOf(Option("off"), Option("standard", "std"), Option("strict")), draft.blocking) {
            draft.blocking = it
            if (it != "off") draft.lastLevel = it
            blockingNote = "your choice"
            redraw()
        }
        blocking.choice(Glyphs.LINK, "links to other sites", null, listOf(Option("default"), Option("inside"), Option("browser"), Option("ask")), draft.links ?: "default") {
            draft.links = if (it == "default") null else it
            redraw()
        }
        blocking.choice(Glyphs.WARN, "warn before risky other sites", "known bad, tracker-heavy, or sending you away", listOf(Option("default"), Option("on"), Option("off")), when (draft.warnRisky) {
            null -> "default"
            true -> "on"
            else -> "off"
        }) {
            draft.warnRisky = when (it) {
                "on" -> true
                "off" -> false
                else -> null
            }
            redraw()
        }
        content.put(blocking.view)

        content.put(sectionTitle("IT CAN ASK FOR", Glyphs.HAND))
        val asks = card()
        val askOptions = listOf(Option("ask"), Option("never"))
        asks.choice(Glyphs.CAMERA, "camera", null, askOptions, draft.camera) {
            draft.camera = it
            redraw()
        }
        asks.choice(Glyphs.MIC, "microphone", null, askOptions, draft.microphone) {
            draft.microphone = it
            redraw()
        }
        asks.choice(Glyphs.PIN, "location", null, askOptions, draft.location) {
            draft.location = it
            redraw()
        }
        asks.choice(Glyphs.DOWNLOAD, "downloads", null, listOf(Option("ask"), Option("allow")), draft.downloads) {
            draft.downloads = it
            redraw()
        }
        content.put(asks.view)

        content.put(sectionTitle("LOCK", Glyphs.LOCK))
        val lock = card()
        lock.choice(Glyphs.LOCK, "lock with", Locking.detail(draft), Locking.MODES, draft.lockMode) { key -> Locking.choose(this, draft, key) { redraw() } }
        if (draft.locks) {
            lock.choice(Glyphs.CLOCK, "ask again after", "time away before it locks again", Locking.AFTER, draft.relockAfter.toString()) {
                draft.relockAfter = it.toInt()
                redraw()
            }
        }
        lock.switch(Glyphs.HIDDEN, "private mode, forget everything on close", if (canClear) "nothing kept by Leyden or the site" else "needs its own storage", draft.privateMode && canClear, canClear) { draft.privateMode = it }
        content.put(lock.view)

        bottom.put(quietButton("Back", Glyphs.ARROW_LEFT) {
            step = 2
            redrawTop()
        }, WRAP_CONTENT, dp(52))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(secondaryButton("Save only") { save(false) }, WRAP_CONTENT, dp(52))
        bottom.put(primaryButton("Add to home", Glyphs.HOME) { save(true) }, dp(160), dp(52))
        showBottom()
    }

    private fun fullButton(button: View): View {
        val holder = LinearLayout(this)
        holder.setPadding(0, dp(10), 0, dp(12))
        holder.addView(button, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)))
        return holder
    }

    private fun save(pin: Boolean) {
        val site = draft
        if (site.url.isEmpty()) return
        if (!Storage.separate) {
            site.ownStorage = false
            site.clearOnClose = false
            site.privateMode = false
        }
        if (!useKept) site.siteIcon = useSiteIcon && found?.icon != null
        check?.takeIf { it.ran }?.let { site.power.check = it.record() }
        SiteStore.saveIcon(this, site.id, iconBitmap())
        SiteStore.save(this, site)
        if (useKept) kept?.let { Kept.drop(this, it.id) }
        if (pin) {
            if (!Shortcuts.pin(this, site)) toast("This launcher can’t add icons to the home screen. Open it from Leyden.")
        } else {
            toast("${site.label} saved. Open it from your apps.")
        }
        finish()
    }

    companion object {
        const val ADDRESS = "address"
    }
}

class Flow(context: android.content.Context) : android.view.ViewGroup(context) {
    private val gap = context.dp(6)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var y = 0
        var lineHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += lineHeight + gap
                lineHeight = 0
            }
            x += child.measuredWidth + gap
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
        setMeasuredDimension(width, y + lineHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        var x = 0
        var y = 0
        var lineHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += lineHeight + gap
                lineHeight = 0
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
    }
}
