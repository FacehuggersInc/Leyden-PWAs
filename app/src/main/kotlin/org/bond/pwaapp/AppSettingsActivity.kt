package org.bond.pwaapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView

class AppSettingsActivity : Screen() {
    private lateinit var site: Site
    private val defaults by lazy { Defaults.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(SITE))
        super.onCreate(savedInstanceState)
        if (found == null) {
            finish()
            return
        }
        site = found
        guarded = found
        if (intent.getBooleanExtra(COPY, false)) window.decorView.post { if (!LockState.needed(this, site)) copySheet() }
    }

    private fun save(redraw: Boolean = true) {
        SiteStore.save(this, site)
        if (redraw) redraw()
    }

    private fun flag(value: Boolean?): String = when (value) {
        null -> "default"
        true -> "on"
        else -> "off"
    }

    private fun unflag(key: String): Boolean? = when (key) {
        "on" -> true
        "off" -> false
        else -> null
    }

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: run {
            finish()
            return
        }
        val open = squareKey(Glyphs.PLAY, "Open ${site.label}") { Shortcuts.open(this, site) }
        header(site.label, site.host, site = site, trailing = open)

        val memory = MemoryStore.get(this, site.id)
        val strip = readouts(listOf(
            Triple("STORAGE", "…", Glyphs.BOX),
            Triple("BLOCKED", if (site.keeps(Kinds.BLOCKED)) memory.blockedToday().toString() else "off", Glyphs.SHIELD),
            Triple("OPENED", if (site.keeps(Kinds.OPENS)) memory.opens.size.toString() else "off", Glyphs.CLOCK),
        ))
        content.put(strip)
        Storage.measure(site) { strip.findViewWithTag<TextView>("STORAGE")?.text = bytes(it.storage) }

        content.put(sectionTitle("NAME AND ICON", Glyphs.TAG))
        val name = card()
        name.link(Glyphs.TAG, "name", site.label) {
            promptText("Name", site.name, "Name") {
                site.name = it.trim()
                save()
                Shortcuts.refresh(this, site)
            }
        }
        name.link(Glyphs.HOME, "start page", site.url.removePrefix("https://").removePrefix("http://")) {
            promptText("Start page", site.url, "https://…", true) {
                site.url = normalizeAddress(it)
                save()
            }
        }
        name.link(Glyphs.GLOBE, "stays in the app", site.scope.removePrefix("https://").removePrefix("http://").ifEmpty { site.host }, "pages under this address count as the app") {
            promptText("Stays in the app", site.scope, "https://…/", true) {
                site.scope = it.trim()
                save()
            }
        }
        name.action(Glyphs.SPLASH, "icon and color", colorHex(site.background), "fetch again") { refetch() }
        content.put(name.view)

        content.put(sectionTitle("LOOKS", Glyphs.COLOR))
        val looks = card()
        looks.choice(Glyphs.COLOR, "bar color", if (site.barColor == "page") "follows the page’s theme color" else "always the app’s color", listOf(Option("page"), Option("app")), site.barColor) {
            site.barColor = it
            save()
        }
        val darkWorks = Build.VERSION.SDK_INT >= 29
        looks.choice(Glyphs.MOON, "dark mode", if (darkWorks) "dark darkens sites that have no dark look" else "needs Android 10 or newer", listOf(Option("site", "site’s own"), Option("dark"), Option("light")), site.dark, darkWorks) {
            site.dark = it
            save()
        }
        looks.stepper(Glyphs.TEXT, "text size", "${site.textSize}%", {
            site.textSize = (site.textSize - 10).coerceAtLeast(50)
            save()
        }, {
            site.textSize = (site.textSize + 10).coerceAtMost(200)
            save()
        })
        looks.switch(Glyphs.ZOOM, "pinch to zoom", null, site.zoom) {
            site.zoom = it
            save(false)
        }
        looks.switch(Glyphs.FULL, "full screen", "hide the status and navigation bars", site.fullScreen) {
            site.fullScreen = it
            save(false)
        }
        content.put(looks.view)

        content.put(sectionTitle("BEHAVIOR", Glyphs.HAND))
        val behavior = card()
        behavior.choice(Glyphs.HAND, "quick settings opens with", "2-finger: hold two fingers still · handle: a tab on the right edge", listOf(Option("default"), Option("two", "2-finger"), Option("handle"), Option("both")), site.gesture ?: "default") {
            site.gesture = if (it == "default") null else it
            save()
        }
        behavior.choice(Glyphs.POPUP, "pop-ups", "windows the site opens, like sign-in", listOf(Option("sheet"), Option("browser"), Option("block")), site.popups) {
            site.popups = it
            save()
        }
        behavior.switch(Glyphs.PULL, "pull to refresh", null, site.pullToRefresh) {
            site.pullToRefresh = it
            save(false)
        }
        behavior.switch(Glyphs.BACK_PAGE, "back goes back a page", "off: back leaves the app right away", site.backGoesBack) {
            site.backGoesBack = it
            save(false)
        }
        behavior.switch(Glyphs.SUN, "keep the screen on", null, site.screenOn) {
            site.screenOn = it
            save(false)
        }
        val resumeDetail = when {
            site.onOpen == "start" -> "always the start page"
            site.keeps(Kinds.RECENT) -> "where you left off, even after it was closed"
            else -> "where you left off while it’s open; keep recent pages to resume after closing"
        }
        behavior.choice(Glyphs.CLOCK, "on open", resumeDetail, listOf(Option("resume"), Option("start")), site.onOpen) {
            site.onOpen = it
            save()
        }
        behavior.choice(Glyphs.PHONE, "screen", null, listOf(Option("any"), Option("portrait", "up"), Option("landscape", "side")), site.orientation) {
            site.orientation = it
            save()
        }
        behavior.switch(Glyphs.DESKTOP, "desktop site", "asks sites for their big-screen version", site.desktop) {
            site.desktop = it
            save(false)
        }
        content.put(behavior.view)

        content.put(sectionTitle("OTHER SITES", Glyphs.LINK, "default follows Settings"))
        val other = card()
        other.choice(Glyphs.LINK, "open other sites", "now: " + site.linksNow(defaults), listOf(Option("default"), Option("inside"), Option("browser"), Option("ask")), site.links ?: "default") {
            site.links = if (it == "default") null else it
            save()
        }
        other.choice(Glyphs.BAR, "show the way back as", "now: " + site.navStyleNow(defaults), listOf(Option("default"), Option("pill"), Option("top"), Option("bottom")), site.navStyle ?: "default") {
            site.navStyle = if (it == "default") null else it
            save()
        }
        other.choice(Glyphs.SHIELD, "blocking there", "now: " + blockingThere(site.otherBlockingNow(defaults)), listOf(Option("default"), Option("app", "as app"), Option("always"), Option("off")), site.otherBlocking ?: "default") {
            site.otherBlocking = if (it == "default") null else it
            save()
        }
        other.choice(Glyphs.WARN, "warn before risky sites", "known for ads or tracking, or sending you away", listOf(Option("default"), Option("on"), Option("off")), flag(site.warnRisky)) {
            site.warnRisky = unflag(it)
            save()
        }
        other.choice(Glyphs.UNLOCK, "warn when a page isn’t encrypted", null, listOf(Option("default"), Option("on"), Option("off")), flag(site.warnPlain)) {
            site.warnPlain = unflag(it)
            save()
        }
        other.link(Glyphs.GLOBE, "sites kept inside", site.keepInside.size.toString(), site.keepInside.joinToString(", ").ifEmpty { "none yet" }) { keptInside() }
        content.put(other.view)

        content.put(sectionTitle("POWER", Glyphs.SPARK))
        val more = card()
        more.link(Glyphs.ROUTE, "address by network", if (site.power.networks.isEmpty()) "off" else site.power.networks.size.toString(), "home Wi-Fi, Tailscale, anywhere else") {
            startActivity(Intent(this, NetworkActivity::class.java).putExtra(SITE, site.id))
        }
        val keys = if (site.power.keysOn) "volume keys on" else "volume keys off"
        more.link(Glyphs.HAND, "controls", null, "$keys · ${plural(site.power.buttons.size, "button")} · swipes") {
            startActivity(Intent(this, ControlsActivity::class.java).putExtra(SITE, site.id))
        }
        more.link(Glyphs.ROUTE, "rewrites and proxy", Defaults.get(this).rewrites.count { it.site.isEmpty() || it.site == site.id }.toString(), "shared with every app; rules can be for one") {
            startActivity(Intent(this, RewritesActivity::class.java))
        }
        content.put(more.view)

        content.put(sectionTitle("SHARING AND LINKS", Glyphs.SHARE))
        val share = card()
        share.switch(Glyphs.SHARE, "takes shared links and text", "it shows in Android’s share menu", site.power.shareText) {
            site.power.shareText = it
            save()
            Share.publish(this)
        }
        val canFiles = site.power.shareTarget.contains("files")
        share.switch(Glyphs.FOLDER, "takes shared photos and files", if (canFiles) "uses the site’s own share target, from its manifest" else "the site doesn’t say it takes files", site.power.shareFiles && canFiles, canFiles) {
            site.power.shareFiles = it
            save()
            Share.publish(this)
        }
        share.link(Glyphs.ROUTE, "where shared things go", site.power.shareTemplate.ifEmpty { if (site.power.shareTarget.isNotEmpty()) "the site’s target" else "not set" }, "use {title}, {text} and {url}") {
            promptText("Address to open", site.power.shareTemplate, "/compose?subject={title}&body={text}", true) {
                site.power.shareTemplate = it.trim()
                save()
            }
        }
        share.switch(Glyphs.EYE, "ask before sending", null, site.power.shareAsk) {
            site.power.shareAsk = it
            save()
        }
        share.switch(Glyphs.LINK, "links for ${site.host} open here", "when Leyden is your browser choice", site.power.routeLinks) {
            site.power.routeLinks = it
            save()
        }
        share.link(Glyphs.GLOBE, "more sites that open here", site.power.linkHosts.size.toString(), site.power.linkHosts.joinToString(", ").ifEmpty { "none" }) {
            promptText("Sites, separated by commas", site.power.linkHosts.joinToString(", "), "photos.home.lan, immich.app", true) { typed ->
                site.power.linkHosts = typed.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableList()
                save()
            }
        }
        content.put(share.view)

        content.put(sectionTitle("FILES", Glyphs.FOLDER))
        val files = card()
        if (site.power.downloadFolder.isEmpty()) {
            files.action(Glyphs.DOWNLOAD, "save downloads in", "Downloads, through Android", "choose") { Files.pickFolder(this, DOWNLOADS) }
        } else {
            files.action(Glyphs.DOWNLOAD, "save downloads in", site.power.downloadFolderName, "take back") {
                Files.release(this, site.power.downloadFolder)
                site.power.downloadFolder = ""
                site.power.downloadFolderName = ""
                save()
            }
        }
        if (site.power.shotFolder.isEmpty()) {
            files.action(Glyphs.CAMERA, "full-page screenshots go to", "Pictures/Screenshots", "choose") { Files.pickFolder(this, SHOTS) }
        } else {
            files.action(Glyphs.CAMERA, "full-page screenshots go to", site.power.shotFolderName, "take back") {
                Files.release(this, site.power.shotFolder)
                site.power.shotFolder = ""
                site.power.shotFolderName = ""
                save()
            }
        }
        files.value(Glyphs.LOCK, "screenshots while locked or private", "blocked")
        content.put(files.view)
        content.put(note("Picking a folder is a new permission: ${site.label} can write only there, and you can take it back here."))

        content.put(sectionTitle("AUDIO AND WINDOWS", Glyphs.MUSIC))
        val audio = card()
        audio.switch(Glyphs.MUSIC, "keep playing with the screen off", "shows a notification with controls while it plays; Android requires it", site.power.audio) { on ->
            if (on) {
                ask("Background audio for ${site.label}?", "It will keep audio and video that’s already playing going when you leave or lock the phone, and show a notification with play, pause and skip while it does. It can’t do anything else in the background.", "Turn on") {
                    site.power.audio = true
                    save()
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7603)
                    }
                }
                redraw()
            } else {
                site.power.audio = false
                save()
            }
        }
        audio.switch(Glyphs.POPUP, "video keeps playing in a small window", "when you leave ${site.label} while a video plays", site.power.pip) {
            site.power.pip = it
            save()
        }
        if (Bubbles.supported()) {
            audio.action(Glyphs.BUBBLE, "open as a bubble", "floats over other apps; Android may ask you to allow bubbles", "open") { Bubbles.show(this, site) }
        }
        content.put(audio.view)

        content.put(sectionTitle("LOCK", Glyphs.LOCK))
        val lock = card()
        lock.choice(Glyphs.LOCK, "lock with", Locking.detail(site), Locking.MODES, site.lockMode) { key ->
            Locking.choose(this, site, key) {
                if (site.locks) LockState.unlocked(this, site.id)
                save()
            }
        }
        if (site.pinHash != null || site.lockMode == "pin" || site.lockMode == "either") {
            lock.link(Glyphs.GRID, "${site.label}’s PIN", if (site.pinHash == null) "not set" else "${Pin.length(site.pinHash)} digits", "kept as a salted hash, never the digits") { pinSheet() }
        }
        if (site.locks) {
            lock.choice(Glyphs.CLOCK, "ask again after", "time away before it locks again", Locking.AFTER, site.relockAfter.toString()) {
                site.relockAfter = it.toInt()
                save()
            }
            lock.switch(Glyphs.HIDDEN, "hide it in recents while locked", "also stops screenshots of it", site.hideRecents) {
                site.hideRecents = it
                save(false)
            }
        }
        content.put(lock.view)

        content.put(sectionTitle("SPLASH", Glyphs.SPLASH))
        val splash = card()
        splash.switch(Glyphs.SPLASH, "splash while it opens", if (site.locks) "the lock always shows on it" else null, site.splash) {
            site.splash = it
            save()
        }
        if (site.splash) {
            splash.choice(Glyphs.CLOCK, "show it at least", "after unlocking, while the page loads", listOf(Option("0"), Option("500", "0.5 s"), Option("1000", "1 s"), Option("2000", "2 s")), site.splashAtLeast.toString()) {
                site.splashAtLeast = it.toInt()
                save()
            }
        }
        splash.choice(Glyphs.SPARK, "background", "soft shapes in its color behind the icon", listOf(Option("still"), Option("drift"), Option("pulse")), site.splashMotion) {
            site.splashMotion = it
            save()
        }
        splash.choice(Glyphs.EYE, "into the app", if (Build.VERSION.SDK_INT >= 31) "blur: the page sharpens as the splash fades" else "blur needs Android 12; it fades here", listOf(Option("cut"), Option("fade"), Option("blur")), site.reveal) {
            site.reveal = it
            save()
        }
        content.put(splash.view)

        content.put(sectionTitle("DATA AND PRIVACY", Glyphs.EYE))
        val privacy = card()
        privacy.link(Glyphs.EYE, "what ${site.label} remembers", "${site.remember.size} of ${Kinds.ALL.size}", "see and switch off everything this app keeps") {
            startActivity(Intent(this, MemoryActivity::class.java).putExtra(SITE, site.id))
        }
        privacy.switch(Glyphs.BOX, "own storage", if (Storage.separate) "separate cookies and logins; switching starts it signed out" else "needs Android System WebView 110 or newer", site.ownStorage && Storage.separate, Storage.separate) {
            site.ownStorage = it
            if (!it) {
                site.clearOnClose = false
                site.privateMode = false
            }
            save()
        }
        privacy.choice(Glyphs.COOKIE, "cookies from other sites", null, listOf(Option("allow"), Option("block")), if (site.thirdPartyCookies) "allow" else "block") {
            site.thirdPartyCookies = it == "allow"
            save()
        }
        val own = site.ownStorage && Storage.separate
        privacy.switch(Glyphs.TRASH, "clear what it stored when closed", if (own) "you’ll sign in each time" else "needs its own storage", site.clearOnClose && own, own) {
            site.clearOnClose = it
            save(false)
        }
        privacy.switch(Glyphs.HIDDEN, "private mode", if (own) "nothing kept after closing, by Leyden or the site" else "needs its own storage", site.privateMode && own, own) {
            site.privateMode = it
            save(false)
        }
        privacy.link(Glyphs.TRASH, "clear data", null, "cookies, site storage and this app’s memory", T.danger) {
            ask("Clear ${site.label}’s data?", "You’ll be signed out, and what Leyden kept for it is cleared. Your settings stay.", "Clear", danger = true) {
                Storage.clearAll(this, site, null) {
                    MemoryStore.forgetAll(this, site.id)
                    toast("Cleared")
                    redraw()
                }
            }
        }
        content.put(privacy.view)

        content.put(sectionTitle("IT CAN ASK FOR", Glyphs.HAND))
        val asks = card()
        val askOptions = listOf(Option("ask"), Option("never"))
        asks.choice(Glyphs.CAMERA, "camera", if ("camera" in site.granted) "allowed every time" else null, askOptions, site.camera) {
            site.camera = it
            if (it == "never") site.granted -= "camera"
            save()
        }
        asks.choice(Glyphs.MIC, "microphone", if ("microphone" in site.granted) "allowed every time" else null, askOptions, site.microphone) {
            site.microphone = it
            if (it == "never") site.granted -= "microphone"
            save()
        }
        asks.choice(Glyphs.PIN, "location", if ("location" in site.granted) "allowed every time" else null, askOptions, site.location) {
            site.location = it
            if (it == "never") site.granted -= "location"
            save()
        }
        asks.choice(Glyphs.DOWNLOAD, "downloads", null, listOf(Option("ask"), Option("allow")), site.downloads) {
            site.downloads = it
            save()
        }
        if (site.granted.isNotEmpty()) {
            asks.action(Glyphs.CHECK, "allowed every time", site.granted.joinToString(", "), "forget") {
                site.granted.clear()
                save()
            }
        }
        if (site.trustedCertificate != null) {
            asks.action(Glyphs.LOCK, "trusted certificate", site.trustedCertificate?.take(23) + "…", "forget") {
                site.trustedCertificate = null
                save()
            }
        }
        content.put(asks.view)

        content.put(sectionTitle("INSERTIONS", Glyphs.CODE))
        val insertions = card()
        insertions.link(Glyphs.CODE, "styles, scripts and auto targets", "${site.insertions.count { it.on }} on", if (site.power.insertionsOn) "in load order; add from the web or a repository" else "all insertions are off") { insertions("") }
        insertions.link(Glyphs.HIDDEN, "hidden elements", site.hidden.size.toString()) { insertions("hidden") }
        insertions.link(Glyphs.SHIELD, "ad and tracker blocking", site.blocking, "${plural(site.rules.size, "rule")} of your own") {
            startActivity(Intent(this, BlockingActivity::class.java).putExtra(SITE, site.id))
        }
        content.put(insertions.view)

        content.put(secondaryButton("Make a second copy", Glyphs.COPY) { copySheet() }, MATCH_PARENT, dp(48))
        val actions = line(8)
        if (Shortcuts.canPin(this)) {
            actions.put(secondaryButton("Add to home screen", Glyphs.HOME) {
                if (!Shortcuts.pin(this, site)) toast("This launcher can’t add icons to the home screen")
            }, 0, dp(48), 1f)
        }
        actions.put(secondaryButton("Remove", Glyphs.TRASH, T.danger) {
            removeSheet(site) { finish() }
        }, 0, dp(48), 1f)
        content.put(actions)
    }

    private fun pinSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("${site.label}’s PIN", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("It’s only for ${site.label}, separate from your phone’s lock.", 13f, T.muted))
        val list = card()
        list.link(Glyphs.PEN, if (site.pinHash == null) "set a PIN" else "change it") {
            sheet.dismiss()
            PinSheet.set(this, site.label) { pin ->
                site.pinHash = Pin.hash(pin)
                if (site.lockMode == "off" || site.lockMode == "fingerprint") site.lockMode = "pin"
                save()
            }
        }
        if (site.pinHash != null) {
            list.link(Glyphs.TRASH, "forget it", null, if (site.lockMode == "pin") "the lock turns off" else "fingerprint stays", T.danger) {
                sheet.dismiss()
                site.pinHash = null
                site.lockMode = when (site.lockMode) {
                    "pin" -> "off"
                    "either" -> "fingerprint"
                    else -> site.lockMode
                }
                save()
            }
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun blockingThere(key: String): String = when (key) {
        "app" -> "as the app"
        "always" -> "always on"
        else -> "off"
    }

    private fun insertions(tab: String) {
        startActivity(Intent(this, InsertionsActivity::class.java).putExtra(SITE, site.id).putExtra(InsertionsActivity.TAB, tab))
    }

    private fun keptInside() {
        val sheet = Sheet(this)
        sheet.body.put(text("Sites kept inside ${site.label}", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("Links to these open inside the app without asking or warning, for things like sign-in pages.", 13f, T.muted))
        val list = card()
        if (site.keepInside.isEmpty()) list.custom(text("None yet.", 13f, T.muted).apply { setPadding(0, dp(14), 0, dp(14)) })
        for (domain in site.keepInside.toList()) {
            list.action(Glyphs.GLOBE, domain, null, "remove") {
                site.keepInside.remove(domain)
                save()
                sheet.dismiss()
                keptInside()
            }
        }
        sheet.body.put(list.view)
        sheet.body.put(secondaryButton("Add a site", Glyphs.PLUS) {
            sheet.dismiss()
            promptText("Keep inside", "", "accounts.example.com", true) { typed ->
                val domain = hostOf(normalizeAddress(typed)).ifEmpty { typed.trim().lowercase() }
                if (domain.isNotEmpty() && domain !in site.keepInside) site.keepInside += domain
                save()
            }
        }, MATCH_PARENT, dp(46))
        sheet.show()
    }

    private fun copySheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("A second copy of ${site.label}", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("Same site, its own storage and login. Good for a work account next to a personal one.", 13f, T.muted))
        val name = field("${site.label} (2)", "Name")
        sheet.body.put(name)
        var badge = "2"
        var settings = true
        var insertions = true
        var rules = true
        var lock = false
        val options = card()
        options.choice(Glyphs.TAG, "mark on its icon", "so you can tell them apart", listOf(Option("none"), Option("dot"), Option("2"), Option("W")), badge) { badge = it }
        options.switch(Glyphs.SETTINGS, "bring its settings", "looks, behavior, other sites", settings) { settings = it }
        options.switch(Glyphs.CODE, "bring insertions", "${site.insertions.size} insertions, ${site.hidden.size} hidden", insertions) { insertions = it }
        options.switch(Glyphs.SHIELD, "bring blocking rules", "${site.rules.size} of your own", rules) { rules = it }
        options.switch(Glyphs.LOCK, "bring the lock", "its PIN is copied too", lock) { lock = it }
        options.value(Glyphs.EYE, "what it remembers", "starts empty")
        options.value(Glyphs.COOKIE, "logins", "never copied")
        sheet.body.put(options.view)
        if (!Storage.separate) sheet.body.put(text("This phone’s WebView can’t keep storage separate, so both copies share logins.", 12f, T.danger))
        val bar = line(8)
        bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
        bar.put(spacer(), 0, 1, 1f)
        bar.put(primaryButton("Make the copy", Glyphs.COPY) {
            sheet.dismiss()
            val made = Copies.make(this, site, name.text.toString().trim().ifEmpty { "${site.label} (2)" }, badge, settings, insertions, rules, lock)
            if (Shortcuts.canPin(this)) Shortcuts.pin(this, made)
            toast("Made ${made.label}")
            startActivity(Intent(this, AppSettingsActivity::class.java).putExtra(SITE, made.id))
        }, dp(190), dp(48))
        sheet.body.put(bar)
        sheet.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val name = try {
            Files.keep(this, uri)
        } catch (error: SecurityException) {
            toast("Android didn’t allow that folder")
            return
        }
        site = SiteStore.get(this, site.id) ?: return
        if (requestCode == DOWNLOADS) {
            Files.release(this, site.power.downloadFolder)
            site.power.downloadFolder = uri.toString()
            site.power.downloadFolderName = name
        } else if (requestCode == SHOTS) {
            Files.release(this, site.power.shotFolder)
            site.power.shotFolder = uri.toString()
            site.power.shotFolderName = name
        }
        save()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Bubbles.PERMISSION && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) Bubbles.show(this, site)
        if (requestCode == 7603 && grantResults.firstOrNull() != android.content.pm.PackageManager.PERMISSION_GRANTED) toast("Without notifications Android may stop the audio when you leave")
    }

    private fun refetch() {
        toast("Reading ${site.host}…")
        val id = site.id
        Thread {
            val outcome = try {
                Result.success(SiteLookup.lookup(site.url, site.trustedCertificate))
            } catch (error: Exception) {
                Result.failure(error)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                outcome.onSuccess { found ->
                    val current = SiteStore.get(this, id) ?: return@onSuccess
                    found.color?.let { current.color = it }
                    current.background = found.background ?: current.color
                    current.shortcuts = found.shortcuts.toMutableList()
                    current.power.shareTarget = found.shareTarget
                    val bitmap = LauncherIcons.make(if (current.siteIcon) found.icon else null, found.maskable && current.siteIcon, current.background, current.label)
                    SiteStore.saveIcon(this, id, bitmap)
                    SiteStore.save(this, current)
                    Shortcuts.refresh(this, current)
                    toast("Updated the icon and color")
                    redraw()
                }
                outcome.onFailure { toast("Couldn’t read the site: ${it.message}") }
            }
        }.start()
    }

    companion object {
        const val SITE = "site"
        const val COPY = "copy"
        private const val DOWNLOADS = 7601
        private const val SHOTS = 7602
    }
}

fun View.hide() {
    visibility = View.GONE
}

fun View.show() {
    visibility = View.VISIBLE
}
