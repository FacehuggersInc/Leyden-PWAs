package org.bond.pwaapp

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class GlobalActivity : Screen() {
    private val defaults by lazy { Defaults.get(this) }

    private fun save(redraw: Boolean = true) {
        Defaults.save(this, defaults)
        if (redraw) redraw()
    }

    override fun render() {
        header("Settings", "defaults for every app; each app can change its own")

        content.put(sectionTitle("PRIVACY", Glyphs.EYE, "for new apps"))
        val privacy = card()
        privacy.link(Glyphs.EYE, "what apps remember", when (defaults.rememberMode) {
            "nothing" -> "nothing"
            "everything" -> "everything"
            else -> "let me pick"
        }, "${defaults.remember.size} of ${Kinds.ALL.size} kinds kept for new apps") { rememberSheet() }
        privacy.choice(Glyphs.COOKIE, "cookies from other sites", null, listOf(Option("allow"), Option("block")), if (defaults.thirdPartyCookies) "allow" else "block") {
            defaults.thirdPartyCookies = it == "allow"
            save()
        }
        privacy.switch(Glyphs.BOX, "own storage for each new app", if (Storage.separate) null else "needs Android System WebView 110 or newer", defaults.ownStorage && Storage.separate, Storage.separate) {
            defaults.ownStorage = it
            save(false)
        }
        privacy.switch(Glyphs.TRASH, "clear site data when an app closes", if (Storage.separate) null else "needs own storage", defaults.clearOnClose && Storage.separate, Storage.separate) {
            defaults.clearOnClose = it
            save(false)
        }
        content.put(privacy.view)

        content.put(sectionTitle("AD AND TRACKER BLOCKING", Glyphs.SHIELD))
        val blocking = card()
        blocking.choice(Glyphs.SHIELD, "level for new apps", null, listOf(Option("off"), Option("standard", "std"), Option("strict")), defaults.blocking) {
            defaults.blocking = it
            save()
        }
        val last = Blocker.lastUpdate(this)
        blocking.link(Glyphs.LIST, "lists", "${defaults.lists.size} on", if (last == 0L) "not downloaded yet" else "updated " + ago(last)) { openBlocking() }
        blocking.switch(Glyphs.WIFI, "update lists on Wi-Fi only", null, defaults.wifiOnly) {
            defaults.wifiOnly = it
            save(false)
        }
        blocking.link(Glyphs.PEN, "rules for every app", defaults.rules.size.toString()) { openBlocking() }
        content.put(blocking.view)

        content.put(sectionTitle("OTHER SITES INSIDE AN APP", Glyphs.LINK))
        val other = card()
        other.choice(Glyphs.LINK, "open other sites", null, listOf(Option("inside"), Option("browser"), Option("ask")), defaults.links) {
            defaults.links = it
            save()
        }
        other.choice(Glyphs.BAR, "show the way back as", null, listOf(Option("pill"), Option("top"), Option("bottom")), defaults.navStyle) {
            defaults.navStyle = it
            save()
        }
        other.choice(Glyphs.SHIELD, "blocking there", if (defaults.otherBlocking == "always") "on even in apps with blocking off" else null, listOf(Option("app", "as app"), Option("always"), Option("off")), defaults.otherBlocking) {
            defaults.otherBlocking = it
            save()
        }
        other.switch(Glyphs.WARN, "warn before risky sites", "known for ads or tracking, or sending you away without a tap", defaults.warnRisky) {
            defaults.warnRisky = it
            save(false)
        }
        other.switch(Glyphs.UNLOCK, "warn when a page isn’t encrypted", null, defaults.warnPlain) {
            defaults.warnPlain = it
            save(false)
        }
        content.put(other.view)

        content.put(sectionTitle("OPEN LINKS IN THE RIGHT APP", Glyphs.LINK))
        val links = card()
        val chosen = Browsers.isDefault(this)
        links.action(Glyphs.GLOBE, "Leyden as your browser choice", if (chosen) "it is; links for your apps open in them" else "pick it once in Android’s default apps", if (chosen) "change" else "open") { Browsers.openDefaultsSettings(this) }
        val browsers = Browsers.list(this)
        links.link(Glyphs.BROWSER, "everything else goes to", browsers.firstOrNull { it.first == defaults.browser }?.second ?: browsers.firstOrNull()?.second ?: "none found") {
            val sheet = Sheet(this)
            sheet.body.put(text("Everything else goes to", 18f, T.ink, Fonts.sansBold))
            val list = card()
            for ((pkg, name) in browsers) list.link(Glyphs.BROWSER, name, if (pkg == defaults.browser) "chosen" else null) {
                defaults.browser = pkg
                sheet.dismiss()
                save()
            }
            sheet.body.put(list.view)
            sheet.show()
        }
        links.switch(Glyphs.EYE, "show which app opened a link", "a small note for a second", defaults.linkNote) {
            defaults.linkNote = it
            save(false)
        }
        links.link(Glyphs.ROUTE, "rewrites and proxy", defaults.rewrites.size.toString(), "change addresses before they load") { startActivity(Intent(this, RewritesActivity::class.java)) }
        content.put(links.view)
        content.put(note("Each app chooses which links open in it, in its settings › sharing and links. Locked apps still ask to unlock first."))

        content.put(sectionTitle("CHECKS WHEN ADDING A SITE", Glyphs.SEARCH))
        val checks = card()
        checks.switch(Glyphs.SHIELD, "count trackers and cookies", "opens the site once, out of sight, with its own throwaway storage", defaults.checkTrackers) {
            defaults.checkTrackers = it
            save(false)
        }
        checks.switch(Glyphs.LOCK, "report redirects and plain-http parts", null, defaults.checkCertificate) {
            defaults.checkCertificate = it
            save(false)
        }
        checks.switch(Glyphs.WARN, "check against known bad sites", "Android’s built-in Safe Browsing, also used while apps are open", defaults.checkSafeBrowsing) {
            defaults.checkSafeBrowsing = it
            save(false)
        }
        content.put(checks.view)

        content.put(sectionTitle("QUICK SETTINGS", Glyphs.HAND))
        val quick = card()
        quick.choice(Glyphs.HAND, "open with", "2-finger: hold two fingers still on the page · handle: a small tab on the right edge", listOf(Option("two", "2-finger"), Option("handle"), Option("both")), defaults.gesture) {
            defaults.gesture = it
            save()
        }
        quick.choice(Glyphs.LIST, "opens as", "small: the keys you use most, drag it up for everything", listOf(Option("small"), Option("full", "everything")), defaults.quickSize) {
            defaults.quickSize = it
            save()
        }
        quick.link(Glyphs.GRID, "keys shown", "${defaults.quickKeys.size} of ${Defaults.QUICK_KEYS.size}") { keysSheet() }
        content.put(quick.view)

        content.put(sectionTitle("LOOKS", Glyphs.MOON))
        val looks = card()
        looks.choice(Glyphs.MOON, "theme", "for Leyden’s own screens", listOf(Option("system"), Option("light"), Option("dark")), defaults.theme) {
            defaults.theme = it
            save(false)
            recreate()
        }
        looks.link(Glyphs.SPLASH, "color", Accents.name(defaults.accent), "buttons, switches and highlights in Leyden") { colorSheet() }
        content.put(looks.view)

        val keptCount = Kept.all(this).size
        if (keptCount > 0) {
            content.put(sectionTitle("REMOVED APPS", Glyphs.HISTORY))
            val removed = card()
            removed.link(Glyphs.SHIELD, "kept settings", keptCount.toString(), "settings from apps you removed, for adding them again") { keptSheet { redraw() } }
            content.put(removed.view)
        }

        content.put(sectionTitle("ABOUT THIS PHONE", Glyphs.PHONE))
        val about = card()
        about.value(Glyphs.BOX, "separate storage per app", if (Storage.separate) "works" else "not here", if (Storage.separate) null else "update Android System WebView to 110 or newer")
        about.value(Glyphs.CODE, "styles before the page loads", if (Storage.earlyScripts) "works" else "after start", null)
        about.action(Glyphs.LOCK, "passwords from your password manager", if (Passwords.ready(this)) "Android’s autofill is on" else "no autofill service is on, so login pages get nothing", if (Passwords.ready(this)) "change" else "choose") { Passwords.choose(this) }
        about.value(Glyphs.MOON, "dark mode for sites", if (Build.VERSION.SDK_INT >= 29) "works" else "Android 10+", null)
        content.put(about.view)

        bottom.put(secondaryButton("Export all apps", Glyphs.DOWNLOAD) { export() }, 0, dp(48), 1f)
        bottom.put(secondaryButton("Import", Glyphs.UPLOAD) { import() }, 0, dp(48), 1f)
        showBottom()
    }

    private fun openBlocking() {
        startActivity(Intent(this, BlockingActivity::class.java))
    }

    private fun rememberSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("What new apps remember", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("This is what Leyden keeps about each app. Apps you already added keep their own choice; change those on each app’s memory page.", 13f, T.muted))
        val list = card()
        for (kind in Kinds.ALL) {
            list.switch(Kinds.icon(kind), Kinds.name(kind), Kinds.detail(kind), kind in defaults.remember) { on ->
                if (on) defaults.remember += kind else defaults.remember -= kind
                defaults.rememberMode = when (defaults.remember.size) {
                    0 -> "nothing"
                    Kinds.ALL.size -> "everything"
                    else -> "pick"
                }
                save(false)
            }
        }
        sheet.body.put(list.view)
        sheet.setOnDismissListener { redraw() }
        sheet.show()
    }

    private fun keysSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("Keys in quick settings", 18f, T.ink, Fonts.sansBold))
        val list = card()
        val names = mapOf(
            "dark" to ("dark mode" to Glyphs.MOON),
            "desktop" to ("desktop site" to Glyphs.DESKTOP),
            "full" to ("full screen" to Glyphs.FULL),
            "screen" to ("screen on" to Glyphs.SUN),
            "pull" to ("pull to refresh" to Glyphs.PULL),
            "block" to ("block ads" to Glyphs.SHIELD),
        )
        for (key in Defaults.QUICK_KEYS) {
            val (name, icon) = names.getValue(key)
            list.switch(icon, name, null, key in defaults.quickKeys) { on ->
                if (on) defaults.quickKeys += key else defaults.quickKeys -= key
                save(false)
            }
        }
        sheet.body.put(list.view)
        sheet.setOnDismissListener { redraw() }
        sheet.show()
    }

    private fun export() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "Leyden apps.json")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EXPORT)
    }

    private fun import() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == EXPORT) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toString(2).toByteArray()) }
                toast("Exported ${plural(SiteStore.all(this).size, "app")}")
            } catch (error: Exception) {
                toast("Couldn’t write the file")
            }
        }
        if (requestCode == IMPORT) {
            val json = try {
                contentResolver.openInputStream(uri)?.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            } catch (error: Exception) {
                null
            }
            if (json == null || !json.has("apps")) {
                toast("That isn’t a Leyden export")
                return
            }
            val count = json.optJSONArray("apps")?.length() ?: 0
            ask("Import ${plural(count, "app")}?", "Apps with the same id are replaced. Your defaults are replaced too. Logins don’t come along; you’ll sign in again.", "Import") {
                val added = Backup.import(this, json)
                Blocker.reload(this)
                toast("Imported ${plural(added, "app")}. Add them to your home screen from the list.")
                redraw()
            }
        }
    }

    companion object {
        private const val EXPORT = 7401
        private const val IMPORT = 7402
    }

    private fun colorSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("Color", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("Used for Leyden’s own buttons, switches and highlights. Each app keeps its own color for its bar and splash.", 13f, T.muted))
        fun pick(choice: String) {
            defaults.accent = choice
            save(false)
            sheet.dismiss()
            Look.refresh(this)
            recreate()
        }
        val grid = Flow(this)
        val options = (if (Accents.phoneWorks) listOf("phone" to Accents.resolve(this, "phone", T.dark)) else emptyList()) + Accents.PRESETS
        for ((key, color) in options) {
            val chip = column(4)
            chip.gravity = android.view.Gravity.CENTER_HORIZONTAL
            val dot = View(this)
            val chosen = defaults.accent == key
            dot.background = rounded(color, dpf(20f), dp(if (chosen) 3 else 0), T.ink)
            chip.put(dot, dp(40), dp(40))
            chip.put(text(if (key == "phone") "phone" else key, 11f, if (chosen) T.ink else T.muted, if (chosen) Fonts.sansBold else Fonts.sans), WRAP_CONTENT)
            chip.setPadding(dp(4), dp(4), dp(4), dp(4))
            chip.minimumWidth = dp(60)
            chip.isClickable = true
            chip.contentDescription = key + if (chosen) ", chosen" else ""
            chip.setOnClickListener { pick(key) }
            grid.addView(chip)
        }
        sheet.body.put(grid)
        if (Accents.phoneWorks) sheet.body.put(text("“phone” follows your wallpaper colors and changes when they do.", 12f, T.faint))
        sheet.body.put(secondaryButton("Your own color", Glyphs.PEN) {
            sheet.dismiss()
            promptText("A color like #2F6FEB", if (defaults.accent.startsWith("#")) defaults.accent else "#", "#2F6FEB", true) { typed ->
                val color = parseCssColor(typed.trim())
                if (color == null) toast("That isn’t a color Leyden can read") else pick(colorHex(color))
            }
        }, android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        sheet.show()
    }

}

object Backup {
    fun export(activity: Activity): JSONObject {
        val apps = JSONArray()
        for (site in SiteStore.all(activity)) {
            val json = site.toJson()
            SiteStore.iconBytes(activity, site.id)?.let { json.put("iconPng", Base64.encodeToString(it, Base64.NO_WRAP)) }
            apps.put(json)
        }
        return JSONObject()
            .put("format", "pwa-app")
            .put("version", 1)
            .put("defaults", Defaults.get(activity).toJson())
            .put("apps", apps)
    }

    fun import(activity: Activity, json: JSONObject): Int {
        json.optJSONObject("defaults")?.let {
            val made = Defaults.fromJson(it)
            made.firstRunDone = true
            Defaults.save(activity, made)
        }
        val apps = json.optJSONArray("apps") ?: return 0
        var count = 0
        for (index in 0 until apps.length()) {
            val item = apps.optJSONObject(index) ?: continue
            val site = Site.fromJson(item) ?: continue
            val png = item.optString("iconPng")
            if (png.isNotEmpty()) {
                try {
                    val bytes = Base64.decode(png, Base64.DEFAULT)
                    SiteStore.iconFile(activity, site.id).writeBytes(bytes)
                } catch (error: IllegalArgumentException) {
                    Unit
                }
            }
            SiteStore.save(activity, site)
            Shortcuts.refresh(activity, site)
            count++
        }
        return count
    }

}
