package org.bond.pwaapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import org.json.JSONObject

class MemoryActivity : Screen() {
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

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: run {
            finish()
            return
        }
        val memory = MemoryStore.get(this, site.id)
        header("What ${site.label} remembers", site.host, site = site)
        val own = Storage.profileName(site) != null
        val strip = readouts(listOf(
            Triple("BY THE APP", bytes(MemoryStore.size(this, site.id) + SiteStore.setupSize(this, site)), Glyphs.GRID),
            Triple("BY THE SITE", "…", Glyphs.GLOBE),
            Triple("KEPT", "${site.remember.size} of ${Kinds.ALL.size}", Glyphs.EYE),
        ))
        content.put(strip)
        Storage.measure(site) { measured ->
            strip.findViewWithTag<TextView>("BY THE SITE")?.text = bytes(measured.storage)
            cookieRow?.text = plural(measured.cookies, "cookie") + " for " + site.host
            storageRow?.text = bytes(measured.storage) + " in " + plural(measured.origins, "place")
        }

        content.put(secondaryButton("Remember nothing for ${site.label}", Glyphs.HIDDEN) {
            site.remember.clear()
            MemoryStore.forgetAll(this, site.id)
            save()
            toast("Nothing is kept for ${site.label} now")
        }, android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        content.put(note("Turns off everything in the first list and clears what was kept. What the site itself stores is below."))

        content.put(sectionTitle("HISTORY AND COUNTS", Glyphs.HISTORY, "off means never stored"))
        val history = card()
        if (site.privateMode) history.custom(note("Private mode is on, so none of this is kept right now.").apply { setPadding(0, dp(12), 0, dp(12)) })
        for (kind in Kinds.ALL) {
            val on = kind in site.remember
            val now = if (on) MemoryStore.describe(memory, kind) else "not kept"
            history.switchWith(Kinds.icon(kind), Kinds.name(kind), now, on, "clear", on, onAction = {
                MemoryStore.forget(this, site.id, kind)
                redraw()
            }) { keep ->
                if (keep) {
                    site.remember += kind
                } else {
                    site.remember -= kind
                    MemoryStore.forget(this, site.id, kind)
                }
                save()
            }
        }
        content.put(history.view)

        if (site.keeps(Kinds.RECENT) && memory.recent.isNotEmpty()) {
            content.put(sectionTitle("RECENT PAGES", Glyphs.HISTORY))
            val pages = card()
            for (visit in memory.recent.take(6)) {
                pages.value(null, visit.title, shortAgo(visit.at), pathOf(visit.url))
            }
            content.put(pages.view)
        }

        content.put(sectionTitle("YOUR SETUP", Glyphs.SETTINGS, "kept until you remove ${site.label}"))
        val setup = card()
        setup.value(Glyphs.TAG, "name, icon and settings", bytes(SiteStore.setupSize(this, site)), "everything on its settings page")
        setup.value(Glyphs.CODE, "insertions", plural(site.styleCount, "style") + ", " + plural(site.scriptCount, "script") + ", " + site.hidden.size + " hidden")
        setup.value(Glyphs.SHIELD, "blocking rules", plural(site.rules.size, "rule") + " of your own")
        setup.value(Glyphs.GLOBE, "sites kept inside", site.keepInside.size.toString())
        content.put(setup.view)

        content.put(sectionTitle("LOCK", Glyphs.LOCK, "removed with ${site.label}"))
        val lock = card()
        lock.value(Glyphs.LOCK, "lock with", Lock.modeName(site.lockMode))
        lock.value(Glyphs.GRID, "PIN", if (site.pinHash == null) "none" else "${Pin.length(site.pinHash)} digits", if (site.pinHash == null) null else "a salted hash (PBKDF2), never the digits; it can’t be read back")
        if (site.locks) {
            lock.value(Glyphs.CLOCK, "ask again", Lock.afterName(site.relockAfter))
            val last = LockState.last(this, site.id)
            lock.action(Glyphs.HISTORY, "last unlocked or used", if (last == 0L) "not yet" else ago(last), "forget", enabled = last > 0) {
                LockState.forgetUnlock(this, site.id)
                toast("It asks again next time")
                redraw()
            }
            val fails = LockState.fails(this, site.id)
            lock.action(Glyphs.WARN, "wrong tries since the last unlock", fails.toString(), "reset", enabled = fails > 0) {
                LockState.clear(this, site.id)
                LockState.touch(this, site.id)
                redraw()
            }
        }
        content.put(lock.view)

        content.put(sectionTitle("YOUR SETUP AND ADD-ONS", Glyphs.CODE, "kept until you remove ${site.label}"))
        val addons = card()
        val yours = site.insertions.filter { it.source == null || it.source?.detached == true }
        val outside = site.insertions.filter { it.fromOutside }
        addons.value(Glyphs.CODE, "your styles, scripts and auto targets", bytes(yours.sumOf { it.code.length }.toLong()), plural(yours.size, "item"))
        if (outside.isNotEmpty()) {
            addons.value(Glyphs.DOWNLOAD, "from the web and repositories", bytes(outside.sumOf { it.code.length + (it.source?.pending?.length ?: 0) }.toLong()),
                outside.joinToString(", ") { it.name.ifEmpty { "untitled" } + (if (it.version.isNotEmpty()) " v${it.version}" else "") } + "; their sources and last check are kept for updating")
        }
        val tokens = site.insertions.count { it.source?.token?.isNotEmpty() == true }
        if (tokens > 0) addons.value(Glyphs.LOCK, "repository access tokens", plural(tokens, "token"), "read-only tokens for private repositories")
        val kept = AddonState.valuesSize(this, site.id, null)
        addons.action(Glyphs.BOX, "kept by add-ons", if (kept > 0) bytes(kept.toLong()) + " of their own data" else "nothing", "clear", enabled = kept > 0) {
            AddonState.clearValues(this, site.id, null)
            redraw()
        }
        addons.value(Glyphs.ROUTE, "rewrites, buttons and keys", "${Defaults.get(this).rewrites.count { it.site == site.id }} rewrites · ${plural(site.power.buttons.size, "button")}" + (if (site.power.keysOn) " · volume keys set" else ""))
        if (site.power.networks.isNotEmpty()) {
            addons.value(Glyphs.WIFI, "addresses by network", plural(site.power.networks.size, "address"),
                site.power.networks.filter { it.kind == "wifi" }.joinToString(", ") { "the Wi-Fi name “${it.ssid}”" }.ifEmpty { "Tailscale only" } + (if (site.power.lastAddress.isNotEmpty()) "; last used ${site.power.lastAddress}" else ""))
        }
        if (site.power.downloadFolder.isNotEmpty() || site.power.shotFolder.isNotEmpty()) {
            addons.action(Glyphs.FOLDER, "folder access", listOf(site.power.downloadFolderName, site.power.shotFolderName).filter { it.isNotEmpty() }.joinToString(", "), "take back") {
                Files.release(this, site.power.downloadFolder)
                Files.release(this, site.power.shotFolder)
                site.power.downloadFolder = ""
                site.power.downloadFolderName = ""
                site.power.shotFolder = ""
                site.power.shotFolderName = ""
                save()
            }
        }
        content.put(addons.view)

        content.put(sectionTitle("FROM WHEN YOU ADDED IT", Glyphs.HISTORY))
        val checked = card()
        val record = site.power.check
        if (record == null) {
            checked.custom(text("No check was kept for ${site.label}. Compare now runs one and keeps it.", 12f, T.muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        } else {
            checked.value(Glyphs.HISTORY, "the check", java.text.DateFormat.getDateInstance().format(java.util.Date(record.at)),
                "${record.scriptsOwn + record.scriptsInline + record.scriptsOther} scripts, ${plural(record.trackers, "tracker")}, ${plural(record.cookies, "cookie")} from other sites")
            checked.value(Glyphs.SCRIPT, "scripts it saw", "", "${record.scriptsOwn} own, ${record.scriptsOther} from ${plural(record.scriptHosts.size, "site")}, ${record.scriptsInline} inline" +
                if (record.scriptHosts.isNotEmpty()) ": " + record.scriptHosts.keys.joinToString(", ") else "")
        }
        checked.action(Glyphs.RELOAD, "compare now", "runs the same check again and shows what changed", "compare") { compare() }
        content.put(checked.view)

        content.put(sectionTitle("PERMISSIONS YOU GAVE", Glyphs.LOCK))
        val given = card()
        val location = Net.canReadWifi(this) && site.power.networks.any { it.kind == "wifi" }
        given.action(Glyphs.PIN, "location, for the Wi-Fi name", if (location) "allowed for Leyden" else "not given", "Android settings", enabled = Net.canReadWifi(this)) {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        }
        given.value(Glyphs.BELL, "notification while audio plays", if (site.power.audio) "on" else "not here")
        given.value(Glyphs.HAND, "camera, microphone, location for the site", if (site.granted.isEmpty()) "nothing allowed every time" else site.granted.joinToString(", "))
        content.put(given.view)

        content.put(sectionTitle("STORED BY THE SITE", Glyphs.GLOBE, if (own) "in ${site.label}’s own storage" else "in storage shared with other apps"))
        val stored = card()
        val cookies = stored.action(Glyphs.COOKIE, "cookies", "counting…", "clear") {
            Storage.clearCookies(site) {
                toast("Cookies cleared")
                redraw()
            }
        }
        cookieRow = detailOf(cookies)
        val storage = stored.action(Glyphs.BOX, "site storage", "counting…", "clear") {
            Storage.clearSiteStorage(site) {
                toast("Site storage cleared")
                redraw()
            }
        }
        storageRow = detailOf(storage)
        stored.action(Glyphs.LIST, "cache", bytes(Storage.cacheSize(this)) + ", shared by all apps", "clear") {
            Storage.clearCache(this)
            toast("Cache cleared")
            redraw()
        }
        stored.action(Glyphs.HAND, "permissions", if (site.granted.isEmpty()) "nothing allowed every time" else site.granted.joinToString(", "), "forget", enabled = site.granted.isNotEmpty()) {
            site.granted.clear()
            save()
        }
        content.put(stored.view)

        bottom.put(secondaryButton("Export", Glyphs.DOWNLOAD) { export() }, 0, dp(48), 1f)
        bottom.put(secondaryButton("Clear everything", Glyphs.TRASH, T.danger) {
            ask("Clear everything for ${site.label}?", "Its memory, cookies and site storage. You’ll be signed out. Settings stay.", "Clear", danger = true) {
                MemoryStore.forgetAll(this, site.id)
                Storage.clearAll(this, site, null) {
                    toast("Cleared")
                    redraw()
                }
            }
        }, 0, dp(48), 1f)
        showBottom()
    }

    private var checking: SiteCheck? = null

    private fun compare() {
        toast("Checking ${site.host}…")
        val before = site.power.check
        val run = SiteCheck(this, root, site.url, site.trustedCertificate, Defaults.get(this).checkSafeBrowsing) { result ->
            checking = null
            if (!result.ran) {
                toast(result.reason)
                return@SiteCheck
            }
            val now = result.record()
            val sheet = Sheet(this)
            sheet.body.put(text("What changed", 18f, T.ink, Fonts.sansBold))
            val list = card()
            if (before == null) {
                list.value(Glyphs.SCRIPT, "scripts now", "${now.scriptsOwn + now.scriptsInline + now.scriptsOther}", now.scriptHosts.keys.joinToString(", ").ifEmpty { null })
            } else {
                val added = now.scriptHosts.keys - before.scriptHosts.keys
                val gone = before.scriptHosts.keys - now.scriptHosts.keys
                list.value(Glyphs.SCRIPT, "scripts", "${before.scriptsOwn + before.scriptsInline + before.scriptsOther} → ${now.scriptsOwn + now.scriptsInline + now.scriptsOther}")
                list.value(Glyphs.PLUS, "new script sites", added.size.toString(), added.joinToString(", ").ifEmpty { "none" })
                list.value(Glyphs.MINUS, "script sites gone", gone.size.toString(), gone.joinToString(", ").ifEmpty { "none" })
                list.value(Glyphs.SHIELD, "trackers", "${before.trackers} → ${now.trackers}")
                list.value(Glyphs.COOKIE, "cookies from other sites", "${before.cookies} → ${now.cookies}")
            }
            sheet.body.put(list.view)
            sheet.body.put(text("Pages you visit later can load more scripts, or different ones.", 12f, T.muted))
            val bar = line(8)
            bar.put(quietButton("Close", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
            bar.put(spacer(), 0, 1, 1f)
            bar.put(primaryButton("Keep this one", Glyphs.CHECK) {
                sheet.dismiss()
                site.power.check = now
                save()
            }, dp(190), dp(48))
            sheet.body.put(bar)
            sheet.show()
        }
        checking = run
        run.start()
    }

    override fun onDestroy() {
        checking?.cancel()
        super.onDestroy()
    }

    private var cookieRow: TextView? = null
    private var storageRow: TextView? = null

    private fun detailOf(row: android.view.View): TextView? {
        val group = row as? android.view.ViewGroup ?: return null
        val words = (0 until group.childCount).map { group.getChildAt(it) }.firstOrNull { it is android.widget.LinearLayout } as? android.widget.LinearLayout ?: return null
        return if (words.childCount > 1) words.getChildAt(1) as? TextView else null
    }

    private fun export() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "${site.label.replace(Regex("[^A-Za-z0-9 _-]"), "")} memory.json")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EXPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val json = JSONObject()
            .put("app", site.label)
            .put("address", site.url)
            .put("kept", org.json.JSONArray(site.remember.toList()))
            .put("memory", MemoryStore.get(this, site.id).toJson())
            .put("settings", site.toJson().apply { remove("pinHash") })
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
            toast("Exported")
        } catch (error: Exception) {
            toast("Couldn’t write the file")
        }
    }

    companion object {
        private const val EXPORT = 7301
    }
}
