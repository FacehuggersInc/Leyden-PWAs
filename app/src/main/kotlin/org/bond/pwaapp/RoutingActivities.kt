package org.bond.pwaapp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class ShareActivity : Screen() {
    private var title = ""
    private var text = ""
    private var link = ""
    private var files = arrayListOf<Uri>()
    private var processText = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = intent
        processText = incoming.action == Intent.ACTION_PROCESS_TEXT
        if (processText) {
            text = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        } else {
            title = incoming.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            text = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            @Suppress("DEPRECATION")
            when (incoming.action) {
                Intent.ACTION_SEND -> (incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { files += it }
                Intent.ACTION_SEND_MULTIPLE -> incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { files += it }
            }
        }
        link = Regex("https?://\\S+").find(text)?.value.orEmpty()
        val chosen = SiteStore.get(this, incoming.getStringExtra(SITE) ?: incoming.getStringExtra(Intent.EXTRA_SHORTCUT_ID)?.removePrefix("share-"))
        if (chosen != null) send(chosen)
    }

    private fun takes(site: Site): Boolean = if (files.isNotEmpty()) site.power.shareFiles && site.power.shareTarget.isNotEmpty() else site.power.shareText

    override fun render() {
        header(if (processText) "Send to an app" else "Share into an app", when {
            files.isNotEmpty() -> plural(files.size, "file")
            link.isNotEmpty() -> hostOf(link)
            else -> text.take(40)
        })
        val sites = SiteStore.all(this).filter { takes(it) }
        if (sites.isEmpty()) {
            content.put(empty(if (files.isNotEmpty()) "None of your apps take files. An app takes files when its site says it can, in its manifest." else "None of your apps take shared text. Turn it on in an app’s settings › sharing and links."))
        } else {
            val list = card()
            for (site in sites) {
                list.link(null, site.label, null, if (site.locks) "locked · it asks to unlock first" else site.host) { send(site) }
            }
            content.put(list.view)
        }
        if (!processText && files.isEmpty() && link.isNotEmpty()) {
            content.put(secondaryButton("Make it an app instead", Glyphs.PLUS) {
                startActivity(Intent(this, AddActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link))
                finish()
            }, MATCH_PARENT, dp(46))
        }
        content.put(note("Only what you share goes to the app you pick. It can’t see anything else on your phone."))
    }

    private fun send(site: Site) {
        val go = {
            val target = if (files.isNotEmpty()) null else Share.destination(site, title, text, link)
                ?: ShareTargets.textUrl(site, title, text, link)
                ?: link.takeIf { it.isNotEmpty() && site.inScope(it) }
            val intent = Shortcuts.intent(this, site).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (files.isNotEmpty()) {
                intent.putParcelableArrayListExtra(WebAppActivity.FILES_SHARED, files)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = android.content.ClipData.newRawUri("shared", files.first()).apply { files.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
                startActivity(intent)
                finish()
            } else if (target == null) {
                toast("Set where shared things go, in ${site.label}’s settings › sharing and links")
            } else {
                startActivity(intent.putExtra(WebAppActivity.ADDRESS, Rewrite.apply(Defaults.get(this).rewrites, target, "incoming", site.id)))
                finish()
            }
        }
        if (site.power.shareAsk) {
            ask("Send to ${site.label}?", if (files.isNotEmpty()) "${plural(files.size, "file")} go to ${site.label}." else "“${text.take(120)}”", "Send") { go() }
        } else {
            go()
        }
    }

    companion object {
        const val SITE = "site"
    }
}

object ShareTargets {
    fun textUrl(site: Site, title: String, text: String, link: String): String? {
        val target = try {
            org.json.JSONObject(site.power.shareTarget)
        } catch (error: Exception) {
            return null
        }
        if (target.optString("method", "GET").uppercase() != "GET") return null
        val action = SiteLookup.resolveOrNull(site.url, target.optString("action")) ?: return null
        val params = target.optJSONObject("params") ?: return action
        fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
        val parts = mutableListOf<String>()
        params.optString("title").takeIf { it.isNotEmpty() }?.let { parts += "$it=${enc(title)}" }
        params.optString("text").takeIf { it.isNotEmpty() }?.let { parts += "$it=${enc(text)}" }
        params.optString("url").takeIf { it.isNotEmpty() }?.let { parts += "$it=${enc(link)}" }
        return action + (if ('?' in action) "&" else "?") + parts.joinToString("&")
    }

    fun postScript(site: Site, files: List<Triple<String, String, String>>): String? {
        val target = try {
            org.json.JSONObject(site.power.shareTarget)
        } catch (error: Exception) {
            return null
        }
        val action = SiteLookup.resolveOrNull(site.url, target.optString("action")) ?: return null
        val field = target.optJSONObject("params")?.optJSONArray("files")?.optJSONObject(0)?.optString("name") ?: return null
        val list = org.json.JSONArray()
        for ((name, mime, data) in files) list.put(org.json.JSONObject().put("n", name).put("t", mime).put("d", data))
        return "(function(){var files=$list;var fd=new FormData();files.forEach(function(f){var b=atob(f.d),a=new Uint8Array(b.length);for(var i=0;i<b.length;i++)a[i]=b.charCodeAt(i);" +
            "fd.append(${org.json.JSONObject.quote(field)},new File([a],f.n,{type:f.t}));});fetch(${org.json.JSONObject.quote(action)},{method:'POST',body:fd,credentials:'include'})" +
            ".then(function(r){location.href=r.url;}).catch(function(e){alert('Sharing failed: '+e);});})();"
    }
}

class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data == null) {
            finish()
            return
        }
        val defaults = Defaults.get(this)
        val original = data.toString()
        val sites = SiteStore.all(this)
        val match = sites.firstOrNull { site -> site.power.routeLinks && opens(site, original) }
        if (match != null) {
            val address = Rewrite.apply(defaults.rewrites, original, "incoming", match.id)
            startActivity(Shortcuts.intent(this, match).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(WebAppActivity.ADDRESS, address))
            if (defaults.linkNote && !match.locks) toast("Opened in ${match.label}")
            finish()
            return
        }
        val address = Rewrite.apply(defaults.rewrites, original, "incoming", null)
        Browsers.open(this, address)
        finish()
    }

    private fun opens(site: Site, url: String): Boolean {
        val host = hostOf(url)
        if (host.isEmpty()) return false
        if (site.power.linkHosts.any { Filters.within(host, it.lowercase()) }) return true
        return sameSite(host, site.host)
    }
}

object Browsers {
    fun list(activity: Activity): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val flags = if (Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0
        @Suppress("DEPRECATION")
        return activity.packageManager.queryIntentActivities(intent, flags)
            .filter { it.activityInfo.packageName != activity.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(activity.packageManager).toString() }
            .distinctBy { it.first }
    }

    fun open(activity: Activity, url: String) {
        val defaults = Defaults.get(activity)
        val chosen = defaults.browser.ifEmpty { list(activity).firstOrNull()?.first.orEmpty() }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (chosen.isNotEmpty()) intent.setPackage(chosen)
        try {
            activity.startActivity(intent)
        } catch (error: Exception) {
            activity.toast("No browser could open that link")
        }
    }

    fun isDefault(activity: Activity): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolved = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == activity.packageName
    }

    fun openDefaultsSettings(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (error: Exception) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

class NetworkActivity : Screen() {
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
        site = SiteStore.get(this, site.id) ?: return
        val power = site.power
        header(site.label, "address by network", site = site)
        content.put(note("Opens the address that works where you are, like the LAN address at home and the Tailscale one away. Checked top to bottom when ${site.label} opens."))
        content.put(sectionTitle("ADDRESSES", Glyphs.ROUTE, "drag to order"))
        val box = column()
        box.background = surface()
        box.setPadding(dp(14), dp(2), dp(14), dp(4))
        val list = DragList(this) { from, to ->
            power.networks.add(to, power.networks.removeAt(from))
            save()
        }
        if (power.networks.isEmpty()) box.put(text("None yet. ${site.label} always opens ${site.url}.", 12f, T.muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        for (entry in power.networks.toList()) {
            val row = line(10)
            row.minimumHeight = dp(52)
            row.setPadding(0, dp(6), 0, dp(6))
            val grip = gripIcon()
            row.put(grip, dp(24), dp(40))
            list.grip(row, grip)
            row.put(glyph(if (entry.kind == "wifi") Glyphs.WIFI else Glyphs.ROUTE), dp(17), dp(17))
            val words = column(2)
            words.put(text(if (entry.kind == "wifi") "on Wi-Fi “${entry.ssid}”" else "when Tailscale is on", 13f))
            words.put(text(entry.url, 11f, T.faint, Fonts.mono).oneLine())
            words.isClickable = true
            words.setOnClickListener { edit(entry) }
            row.put(words, 0, WRAP_CONTENT, 1f)
            row.put(squareKey(Glyphs.PEN, "Edit", 32) { edit(entry) }, dp(32), dp(32))
            row.put(squareKey(Glyphs.CLOSE, "Remove", 32) {
                power.networks.remove(entry)
                save()
            }, dp(32), dp(32))
            list.put(row)
        }
        box.put(list)
        content.put(box)
        val adds = line(8)
        adds.put(secondaryButton("Wi-Fi", Glyphs.WIFI) { addWifi() }, 0, dp(46), 1f)
        adds.put(secondaryButton("Tailscale", Glyphs.ROUTE) {
            power.networks += NetAddr("tailscale", "", "")
            SiteStore.save(this, site)
            edit(power.networks.last())
        }, 0, dp(46), 1f)
        content.put(adds)
        val rest = card()
        rest.choice(Glyphs.GLOBE, "anywhere else", null, listOf(Option("ask"), Option("last", "last one"), Option("none", "don’t open")), power.elseMode) {
            power.elseMode = it
            save()
        }
        rest.value(Glyphs.HISTORY, "last address used", power.lastAddress.ifEmpty { "none yet" })
        content.put(rest.view)
        if (power.networks.any { it.kind == "wifi" }) {
            if (!Net.canReadWifi(this)) {
                content.put(callout(Glyphs.PIN, "New permission: location, to read the Wi-Fi name",
                    "Android only tells apps the Wi-Fi name with location access. Leyden reads the name when ${site.label} opens, never your position, and doesn’t keep it. Tailscale is seen without it.", T.accent))
                content.put(primaryButton("Allow location", Glyphs.CHECK) {
                    requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 7501)
                }, MATCH_PARENT, dp(50))
            } else {
                content.put(note("Location is allowed for reading the Wi-Fi name. Take it back in Android’s settings for Leyden; Wi-Fi addresses then stop matching."))
            }
        }
        content.put(note("Logins are kept per address, so you may need to sign in once on each."))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        redraw()
    }

    private fun addWifi() {
        val current = Net.ssid(this).orEmpty()
        promptText("Wi-Fi name", current, "Home-5G", true) { name ->
            if (name.isBlank()) return@promptText
            site.power.networks += NetAddr("wifi", name.trim(), "")
            SiteStore.save(this, site)
            edit(site.power.networks.last())
        }
    }

    private fun edit(entry: NetAddr) {
        if (entry.kind == "wifi" && entry.url.isNotEmpty()) {
            val sheet = Sheet(this)
            sheet.body.put(text("On Wi-Fi “${entry.ssid}”", 18f, T.ink, Fonts.sansBold))
            val list = card()
            list.link(Glyphs.WIFI, "Wi-Fi name", entry.ssid) {
                sheet.dismiss()
                promptText("Wi-Fi name", entry.ssid, "Home-5G", true) { name ->
                    if (name.isNotBlank()) entry.ssid = name.trim()
                    save()
                }
            }
            list.link(Glyphs.LINK, "address", entry.url) {
                sheet.dismiss()
                editAddress(entry)
            }
            sheet.body.put(list.view)
            sheet.show()
            return
        }
        editAddress(entry)
    }

    private fun editAddress(entry: NetAddr) {
        promptText(if (entry.kind == "wifi") "Address on “${entry.ssid}”" else "Address with Tailscale on", entry.url.ifEmpty { if (entry.kind == "wifi") "http://192.168.1." else "https://" }, "http://192.168.1.20", true) {
            entry.url = normalizeAddress(it)
            save()
        }
    }
}

class RewritesActivity : Screen() {
    private val defaults by lazy { Defaults.get(this) }

    private fun save() {
        Defaults.save(this, defaults)
        Proxy.apply(this)
        redraw()
    }

    override fun render() {
        header("Rewrites", "for every app · each rule can be for one app")
        content.put(sectionTitle("WHEN REWRITES APPLY", Glyphs.ROUTE, "defaults for new rules"))
        val scopes = card()
        for (scope in Rewrite.SCOPES) {
            val (name, detail) = when (scope) {
                "incoming" -> "links from other apps" to "opened or shared into one of your apps"
                "inside" -> "browsing inside your apps" to "links you tap, and pages that send you on"
                else -> "links going out to your browser" to "rewritten before they’re handed over"
            }
            scopes.switch(if (scope == "incoming") Glyphs.SHARE else if (scope == "inside") Glyphs.GLOBE else Glyphs.ARROW_RIGHT, name, detail, scope in defaults.rewriteScopes) { on ->
                if (on) defaults.rewriteScopes += scope else defaults.rewriteScopes -= scope
                Defaults.save(this, defaults)
            }
        }
        content.put(scopes.view)
        content.put(sectionTitle("RULES", Glyphs.ROUTE, "first match wins"))
        val box = column()
        box.background = surface()
        box.setPadding(dp(14), dp(2), dp(14), dp(4))
        val list = DragList(this) { from, to ->
            defaults.rewrites.add(to, defaults.rewrites.removeAt(from))
            save()
        }
        if (defaults.rewrites.isEmpty()) box.put(text("None yet. A rule like reddit.com → old.reddit.com changes addresses before they load.", 12f, T.muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        val sites = SiteStore.all(this)
        for (rule in defaults.rewrites.toList()) {
            val row = line(10)
            row.minimumHeight = dp(52)
            row.setPadding(0, dp(6), 0, dp(6))
            val grip = gripIcon()
            row.put(grip, dp(24), dp(40))
            list.grip(row, grip)
            val words = column(2)
            words.put(text("${rule.from} → ${rule.to}", 12f, T.ink, Fonts.mono))
            val owner = sites.firstOrNull { it.id == rule.site }?.label
            words.put(text((owner?.let { "only in $it" } ?: "every app") + " · " + rule.scopes.joinToString(", ") { Rewrite.scopeName(it) }.ifEmpty { "never" }, 11f, T.faint))
            words.isClickable = true
            words.setOnClickListener { edit(rule) }
            row.put(words, 0, WRAP_CONTENT, 1f)
            val toggle = Switch(this)
            toggle.checked = rule.on
            toggle.onChange = {
                rule.on = it
                Defaults.save(this, defaults)
            }
            row.put(toggle, WRAP_CONTENT)
            list.put(row)
        }
        box.put(list)
        content.put(box)
        content.put(secondaryButton("Add a rewrite", Glyphs.PLUS) {
            val rule = Rewrite(scopes = defaults.rewriteScopes.toMutableSet())
            edit(rule, true)
        }, MATCH_PARENT, dp(46))

        content.put(sectionTitle("THROUGH A PROXY", Glyphs.SHIELD))
        val proxy = card()
        val works = Proxy.supported() && Proxy.onlyListed()
        proxy.link(Glyphs.ROUTE, "proxy", defaults.proxy.ifEmpty { "none" }, if (works) "like socks5://10.0.0.2:1080 or http://proxy:3128" else "needs a newer Android System WebView") {
            promptText("Proxy", defaults.proxy, "socks5://10.0.0.2:1080", true) {
                defaults.proxy = it.trim()
                save()
            }
        }
        proxy.link(Glyphs.GLOBE, "sites that use it", defaults.proxySites.size.toString(), defaults.proxySites.joinToString(", ").ifEmpty { "none" }) {
            promptText("Sites, separated by commas", defaults.proxySites.joinToString(", "), "*.ts.net, plex.tv", true) { typed ->
                defaults.proxySites = typed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                save()
            }
        }
        proxy.choice(Glyphs.WARN, "if the proxy is down", null, listOf(Option("stop"), Option("direct", "go direct")), defaults.proxyFallback) {
            defaults.proxyFallback = it
            save()
        }
        content.put(proxy.view)
        content.put(callout(Glyphs.WARN, "One proxy for all of Leyden", "Android lets a WebView have a single proxy setting, so the list decides which sites use it, in every app. Everything else goes direct.", T.info))
    }

    private fun edit(rule: Rewrite, isNew: Boolean = false) {
        val sheet = Sheet(this)
        sheet.body.put(text(if (isNew) "New rewrite" else "Rewrite", 18f, T.ink, Fonts.sansBold))
        val from = field(rule.from, "reddit.com  or  youtube.com/watch, a second.com", true)
        val to = field(rule.to, "old.reddit.com  or  yt.home.lan/watch", true)
        sheet.body.put(text("From", 12f, T.muted))
        sheet.body.put(from)
        sheet.body.put(text("To", 12f, T.muted))
        sheet.body.put(to)
        sheet.body.put(text("The rest of the address, like the video id, is kept.", 11f, T.faint))
        val scopes = card()
        for (scope in Rewrite.SCOPES) scopes.switch(null, Rewrite.scopeName(scope), null, scope in rule.scopes) { on -> if (on) rule.scopes += scope else rule.scopes -= scope }
        val sites = SiteStore.all(this)
        scopes.link(Glyphs.GRID, "for", sites.firstOrNull { it.id == rule.site }?.label ?: "every app") {
            pickMany("Only for one app (pick one, or none for every app)", sites.map { it.label }) { picked ->
                rule.site = picked.firstOrNull()?.let { sites[it].id }.orEmpty()
            }
        }
        sheet.body.put(scopes.view)
        val bar = line(8)
        if (!isNew) bar.put(quietButton("Delete", Glyphs.TRASH, T.danger) {
            defaults.rewrites.remove(rule)
            sheet.dismiss()
            save()
        }, WRAP_CONTENT, dp(48)) else bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
        bar.put(spacer(), 0, 1, 1f)
        bar.put(primaryButton("Save", Glyphs.CHECK) {
            rule.from = from.text.toString().trim()
            rule.to = to.text.toString().trim()
            if (rule.from.isEmpty() || rule.to.isEmpty()) {
                toast("Both sides are needed")
                return@primaryButton
            }
            if (isNew) defaults.rewrites += rule
            sheet.dismiss()
            save()
        }, dp(140), dp(48))
        sheet.body.put(bar)
        sheet.show()
    }
}
