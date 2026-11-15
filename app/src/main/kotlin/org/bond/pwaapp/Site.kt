package org.bond.pwaapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

object Kinds {
    const val RECENT = "recent"
    const val TITLE = "title"
    const val UNREAD = "unread"
    const val BLOCKED = "blocked"
    const val OPENS = "opens"
    const val SHORTCUTS = "shortcuts"

    val ALL = listOf(RECENT, TITLE, UNREAD, BLOCKED, OPENS, SHORTCUTS)
    val HELPFUL = setOf(RECENT, TITLE, UNREAD)

    fun name(kind: String): String = when (kind) {
        RECENT -> "recent pages"
        TITLE -> "last page title and preview"
        UNREAD -> "unread count"
        BLOCKED -> "blocked counts"
        OPENS -> "when and how often opened"
        else -> "shortcut use"
    }

    fun detail(kind: String): String = when (kind) {
        RECENT -> "for jumping back, and for reopening where you left off"
        TITLE -> "shown on your list while it runs: the title and a small picture of the page"
        UNREAD -> "read from the page title, like “Inbox (3)”"
        BLOCKED -> "how many ads and trackers were stopped, by day"
        OPENS -> "times opened and time spent, for sorting the list"
        else -> "which of the site’s shortcuts you use, for ordering them"
    }

    fun icon(kind: String): String = when (kind) {
        RECENT -> Glyphs.HISTORY
        TITLE -> Glyphs.TEXT
        UNREAD -> Glyphs.MAIL
        BLOCKED -> Glyphs.SHIELD
        OPENS -> Glyphs.CLOCK
        else -> Glyphs.SORT
    }
}

class Hidden(var selector: String, var path: String = "") {
    fun toJson(): JSONObject = JSONObject().put("selector", selector).put("path", path)

    companion object {
        fun fromJson(json: JSONObject): Hidden = Hidden(json.optString("selector"), json.optString("path"))
    }
}

class Shortcut(val name: String, val url: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("url", url)

    companion object {
        fun fromJson(json: JSONObject): Shortcut = Shortcut(json.optString("name"), json.optString("url"))
    }
}

class Site(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var url: String = "",
    var scope: String = "",
    var color: Int = DEFAULT_COLOR,
    var background: Int = DEFAULT_COLOR,
    var siteIcon: Boolean = false,
    var created: Long = System.currentTimeMillis(),
) {
    var orientation = "any"
    var group = ""
    var barColor = "page"
    var splash = true
    var dark = "site"
    var textSize = 100
    var zoom = true
    var fullScreen = false

    var gesture: String? = null
    var links: String? = null
    var navStyle: String? = null
    var keepInside = mutableListOf<String>()
    var popups = "sheet"
    var pullToRefresh = false
    var backGoesBack = true
    var screenOn = false
    var onOpen = "resume"
    var desktop = false

    var remember = mutableSetOf<String>()
    var ownStorage = true
    var thirdPartyCookies = false
    var clearOnClose = false
    var lockMode = "off"
    var pinHash: String? = null
    var relockAfter = 60
    var hideRecents = true
    var splashAtLeast = 1000
    var splashMotion = "drift"
    var reveal = "blur"
    var privateMode = false
    var camera = "ask"
    var microphone = "ask"
    var location = "ask"
    var downloads = "ask"
    var granted = mutableSetOf<String>()

    var blocking = "standard"
    var lastLevel = "standard"
    var otherBlocking: String? = null
    var warnRisky: Boolean? = null
    var warnPlain: Boolean? = null
    var unblocked = mutableSetOf<String>()
    var rules = mutableListOf<String>()

    var insertions = mutableListOf<Insertion>()
    var power = Power()
    var hidden = mutableListOf<Hidden>()
    var shortcuts = mutableListOf<Shortcut>()
    var trustedCertificate: String? = null

    val host: String
        get() = hostOf(url).ifEmpty { url }

    val locks: Boolean
        get() = lockMode != "off"

    val usesPin: Boolean
        get() = (lockMode == "pin" || lockMode == "either") && pinHash != null

    val usesFinger: Boolean
        get() = lockMode == "fingerprint" || lockMode == "either" || (lockMode == "pin" && pinHash == null)

    val label: String
        get() = name.ifBlank { host }

    fun keeps(kind: String): Boolean = kind in remember && !privateMode

    fun inScope(url: String?): Boolean {
        val address = url ?: return true
        if (address.startsWith("about:") || address.startsWith("data:") || address.startsWith("blob:") || address.startsWith("javascript:")) return true
        if (scope.isNotEmpty() && address.startsWith(scope)) return true
        val visited = hostOf(address)
        if (visited.isEmpty()) return true
        if (sameSite(visited, host)) return true
        return power.networks.any { hostOf(it.url).isNotEmpty() && sameSite(visited, hostOf(it.url)) }
    }

    val styleCount: Int
        get() = insertions.count { it.kind == "css" }

    val scriptCount: Int
        get() = insertions.count { it.kind != "css" }

    fun keptInside(url: String?): Boolean {
        val visited = hostOf(url)
        if (visited.isEmpty()) return false
        return keepInside.any { domain -> visited == domain || visited.endsWith(".$domain") }
    }

    fun gestureNow(defaults: Defaults): String = gesture ?: defaults.gesture

    fun linksNow(defaults: Defaults): String = links ?: defaults.links

    fun navStyleNow(defaults: Defaults): String = navStyle ?: defaults.navStyle

    fun otherBlockingNow(defaults: Defaults): String = otherBlocking ?: defaults.otherBlocking

    fun warnRiskyNow(defaults: Defaults): Boolean = warnRisky ?: defaults.warnRisky

    fun warnPlainNow(defaults: Defaults): Boolean = warnPlain ?: defaults.warnPlain

    fun copy(): Site = fromJson(toJson()) ?: this

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("url", url)
            .put("scope", scope)
            .put("color", colorHex(color))
            .put("background", colorHex(background))
            .put("siteIcon", siteIcon)
            .put("created", created)
            .put("orientation", orientation)
            .put("group", group)
            .put("barColor", barColor)
            .put("splash", splash)
            .put("dark", dark)
            .put("textSize", textSize)
            .put("zoom", zoom)
            .put("fullScreen", fullScreen)
            .put("keepInside", JSONArray(keepInside))
            .put("popups", popups)
            .put("pullToRefresh", pullToRefresh)
            .put("backGoesBack", backGoesBack)
            .put("screenOn", screenOn)
            .put("onOpen", onOpen)
            .put("desktop", desktop)
            .put("remember", JSONArray(remember.toList()))
            .put("ownStorage", ownStorage)
            .put("thirdPartyCookies", thirdPartyCookies)
            .put("clearOnClose", clearOnClose)
            .put("lockMode", lockMode)
            .put("relockAfter", relockAfter)
            .put("hideRecents", hideRecents)
            .put("splashAtLeast", splashAtLeast)
            .put("splashMotion", splashMotion)
            .put("reveal", reveal)
            .put("privateMode", privateMode)
            .put("camera", camera)
            .put("microphone", microphone)
            .put("location", location)
            .put("downloads", downloads)
            .put("granted", JSONArray(granted.toList()))
            .put("blocking", blocking)
            .put("lastLevel", lastLevel)
            .put("unblocked", JSONArray(unblocked.toList()))
            .put("rules", JSONArray(rules))
            .put("insertions", JSONArray(insertions.map { it.toJson() }))
            .put("power", power.toJson())
            .put("hidden", JSONArray(hidden.map { it.toJson() }))
            .put("shortcuts", JSONArray(shortcuts.map { it.toJson() }))
        gesture?.let { json.put("gesture", it) }
        links?.let { json.put("links", it) }
        navStyle?.let { json.put("navStyle", it) }
        otherBlocking?.let { json.put("otherBlocking", it) }
        trustedCertificate?.let { json.put("trustedCertificate", it) }
        pinHash?.let { json.put("pinHash", it) }
        warnRisky?.let { json.put("warnRisky", it) }
        warnPlain?.let { json.put("warnPlain", it) }
        return json
    }

    companion object {
        val DEFAULT_COLOR = opaque(0x1E2A24)
        val LOCK_MODES = listOf("off", "fingerprint", "pin", "either")

        fun fresh(defaults: Defaults): Site {
            val site = Site()
            site.remember = defaults.remember.toMutableSet()
            site.ownStorage = defaults.ownStorage
            site.thirdPartyCookies = defaults.thirdPartyCookies
            site.clearOnClose = defaults.clearOnClose
            site.blocking = defaults.blocking
            site.lastLevel = if (defaults.blocking == "off") "standard" else defaults.blocking
            return site
        }

        private fun strings(json: JSONObject, key: String): MutableList<String> {
            val array = json.optJSONArray(key) ?: return mutableListOf()
            return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }.toMutableList()
        }

        private fun <T> objects(json: JSONObject, key: String, make: (JSONObject) -> T): MutableList<T> {
            val array = json.optJSONArray(key) ?: return mutableListOf()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(make) }.toMutableList()
        }

        private fun pick(json: JSONObject, key: String, allowed: List<String>, fallback: String): String =
            json.optString(key).takeIf { it in allowed } ?: fallback

        private fun pickOrNull(json: JSONObject, key: String, allowed: List<String>): String? =
            if (json.has(key)) json.optString(key).takeIf { it in allowed } else null

        private fun flagOrNull(json: JSONObject, key: String): Boolean? = if (json.has(key)) json.optBoolean(key) else null

        fun fromJson(json: JSONObject): Site? {
            val id = json.optString("id")
            if (id.isEmpty()) return null
            val color = parseCssColor(json.optString("color")) ?: DEFAULT_COLOR
            val site = Site(
                id = id,
                name = json.optString("name"),
                url = json.optString("url"),
                scope = json.optString("scope"),
                color = color,
                background = parseCssColor(json.optString("background")) ?: color,
                siteIcon = json.optBoolean("siteIcon"),
                created = json.optLong("created", System.currentTimeMillis()),
            )
            site.orientation = pick(json, "orientation", listOf("any", "portrait", "landscape"), "any")
            site.group = json.optString("group")
            site.barColor = pick(json, "barColor", listOf("page", "app"), "page")
            site.splash = json.optBoolean("splash", true)
            site.dark = pick(json, "dark", listOf("site", "dark", "light"), "site")
            site.textSize = json.optInt("textSize", 100).coerceIn(50, 200)
            site.zoom = json.optBoolean("zoom", true)
            site.fullScreen = json.optBoolean("fullScreen", false)
            site.gesture = pickOrNull(json, "gesture", Defaults.GESTURES)
            site.links = pickOrNull(json, "links", Defaults.LINKS)
            if (site.links == null && json.optBoolean("keepLinks")) site.links = "inside"
            site.navStyle = pickOrNull(json, "navStyle", Defaults.NAV_STYLES)
            site.keepInside = strings(json, "keepInside")
            site.popups = pick(json, "popups", listOf("sheet", "browser", "block"), "sheet")
            site.pullToRefresh = json.optBoolean("pullToRefresh", false)
            site.backGoesBack = json.optBoolean("backGoesBack", true)
            site.screenOn = json.optBoolean("screenOn", false)
            site.onOpen = pick(json, "onOpen", listOf("resume", "start"), "resume")
            site.desktop = json.optBoolean("desktop", false)
            site.remember = if (json.has("remember")) strings(json, "remember").filter { it in Kinds.ALL }.toMutableSet() else Kinds.HELPFUL.toMutableSet()
            site.ownStorage = json.optBoolean("ownStorage", false)
            site.thirdPartyCookies = json.optBoolean("thirdPartyCookies", false)
            site.clearOnClose = json.optBoolean("clearOnClose", false)
            site.lockMode = pick(json, "lockMode", LOCK_MODES, if (json.optBoolean("lock", false)) "fingerprint" else "off")
            site.pinHash = json.optString("pinHash").ifEmpty { null }
            site.relockAfter = json.optInt("relockAfter", 60).coerceIn(0, 3600)
            site.hideRecents = json.optBoolean("hideRecents", true)
            site.splashAtLeast = json.optInt("splashAtLeast", 1000).coerceIn(0, 5000)
            site.splashMotion = pick(json, "splashMotion", listOf("still", "drift", "pulse"), "drift")
            site.reveal = pick(json, "reveal", listOf("cut", "fade", "blur"), "blur")
            site.privateMode = json.optBoolean("privateMode", false)
            site.camera = pick(json, "camera", listOf("ask", "never"), "ask")
            site.microphone = pick(json, "microphone", listOf("ask", "never"), "ask")
            site.location = pick(json, "location", listOf("ask", "never"), "ask")
            site.downloads = pick(json, "downloads", listOf("ask", "allow"), "ask")
            site.granted = strings(json, "granted").toMutableSet()
            site.blocking = pick(json, "blocking", Defaults.LEVELS, "standard")
            site.lastLevel = pick(json, "lastLevel", listOf("standard", "strict"), "standard")
            site.otherBlocking = pickOrNull(json, "otherBlocking", Defaults.OTHER_BLOCKING)
            site.warnRisky = flagOrNull(json, "warnRisky")
            site.warnPlain = flagOrNull(json, "warnPlain")
            site.unblocked = strings(json, "unblocked").toMutableSet()
            site.rules = strings(json, "rules")
            site.insertions = if (json.has("insertions")) {
                objects(json, "insertions") { Insertion.fromJson(it) }
            } else {
                (objects(json, "styles") { Insertion.legacy(it, "css") } + objects(json, "scripts") { Insertion.legacy(it, "js") }).toMutableList()
            }
            site.power = Power.fromJson(json.optJSONObject("power"))
            site.hidden = objects(json, "hidden") { Hidden.fromJson(it) }
            site.shortcuts = objects(json, "shortcuts") { Shortcut.fromJson(it) }
            site.trustedCertificate = json.optString("trustedCertificate").ifEmpty { null }
            return site
        }
    }
}

object SiteStore {
    private const val PREFERENCES = "sites"
    private const val LIST = "list"
    private val faces = HashMap<String, Bitmap>()

    fun all(context: Context): List<Site> {
        val text = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(LIST, null) ?: return emptyList()
        val array = try {
            JSONArray(text)
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { Site.fromJson(it) } }
    }

    fun get(context: Context, id: String?): Site? = if (id == null) null else all(context).firstOrNull { it.id == id }

    fun save(context: Context, site: Site) {
        val sites = all(context).toMutableList()
        val at = sites.indexOfFirst { it.id == site.id }
        if (at >= 0) sites[at] = site else sites.add(site)
        write(context, sites)
    }

    fun remove(context: Context, id: String) {
        write(context, all(context).filter { it.id != id })
        iconFile(context, id).delete()
        faces.remove(id)
    }

    fun saveIcon(context: Context, id: String, bitmap: Bitmap) {
        iconFile(context, id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        faces.remove(id)
    }

    fun icon(context: Context, id: String): Bitmap? {
        val file = iconFile(context, id)
        return if (file.isFile) BitmapFactory.decodeFile(file.path) else null
    }

    fun iconBytes(context: Context, id: String): ByteArray? {
        val file = iconFile(context, id)
        return if (file.isFile) file.readBytes() else null
    }

    fun face(context: Context, site: Site): Bitmap? {
        faces[site.id]?.let { return it }
        val full = icon(context, site.id) ?: return null
        val inset = full.width / 6
        val made = Bitmap.createBitmap(full, inset, inset, full.width - inset * 2, full.height - inset * 2)
        faces[site.id] = made
        return made
    }

    fun iconFile(context: Context, id: String): File {
        val folder = File(context.filesDir, "icons")
        folder.mkdirs()
        return File(folder, "$id.png")
    }

    fun setupSize(context: Context, site: Site): Long = site.toJson().toString().length.toLong() + (iconFile(context, site.id).takeIf { it.isFile }?.length() ?: 0L)

    fun write(context: Context, sites: List<Site>) {
        val array = JSONArray()
        sites.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(LIST, array.toString()).commit()
    }
}
