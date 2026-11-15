package org.bond.pwaapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Defaults {
    var firstRunDone = false
    var rememberMode = "pick"
    var remember = Kinds.HELPFUL.toMutableSet()
    var thirdPartyCookies = false
    var ownStorage = true
    var clearOnClose = false
    var blocking = "standard"
    var lists = mutableSetOf("easylist", "easyprivacy")
    var customLists = mutableListOf<String>()
    var wifiOnly = true
    var rules = mutableListOf<String>()
    var links = "ask"
    var navStyle = "pill"
    var otherBlocking = "app"
    var warnRisky = true
    var warnPlain = true
    var checkTrackers = true
    var checkCertificate = true
    var checkSafeBrowsing = true
    var gesture = "two"
    var quickKeys = QUICK_KEYS.toMutableSet()
    var theme = "system"
    var rewrites = mutableListOf<Rewrite>()
    var rewriteScopes = mutableSetOf("incoming", "inside")
    var proxy = ""
    var proxySites = mutableListOf<String>()
    var proxyFallback = "stop"
    var browser = ""
    var linkNote = true
    var quickSize = "small"
    var accent = "orange"
    var groups = mutableListOf<Group>()
    var homeTab = "all"
    var fingerprint = "standard"
    var selectAt = "bottom"

    fun toJson(): JSONObject = JSONObject()
        .put("firstRunDone", firstRunDone)
        .put("rememberMode", rememberMode)
        .put("remember", JSONArray(remember.toList()))
        .put("thirdPartyCookies", thirdPartyCookies)
        .put("ownStorage", ownStorage)
        .put("clearOnClose", clearOnClose)
        .put("blocking", blocking)
        .put("lists", JSONArray(lists.toList()))
        .put("customLists", JSONArray(customLists))
        .put("wifiOnly", wifiOnly)
        .put("rules", JSONArray(rules))
        .put("links", links)
        .put("navStyle", navStyle)
        .put("otherBlocking", otherBlocking)
        .put("warnRisky", warnRisky)
        .put("warnPlain", warnPlain)
        .put("checkTrackers", checkTrackers)
        .put("checkCertificate", checkCertificate)
        .put("checkSafeBrowsing", checkSafeBrowsing)
        .put("gesture", gesture)
        .put("quickKeys", JSONArray(quickKeys.toList()))
        .put("theme", theme)
        .put("rewrites", JSONArray(rewrites.map { it.toJson() }))
        .put("rewriteScopes", JSONArray(rewriteScopes.toList()))
        .put("proxy", proxy)
        .put("proxySites", JSONArray(proxySites))
        .put("proxyFallback", proxyFallback)
        .put("browser", browser)
        .put("linkNote", linkNote)
        .put("quickSize", quickSize)
        .put("accent", accent)
        .put("groups", JSONArray(groups.map { it.toJson() }))
        .put("homeTab", homeTab)
        .put("fingerprint", fingerprint)
        .put("selectAt", selectAt)

    companion object {
        val GESTURES = listOf("two", "handle", "both")
        val LINKS = listOf("inside", "browser", "ask")
        val NAV_STYLES = listOf("pill", "top", "bottom")
        val LEVELS = listOf("off", "standard", "strict")
        val OTHER_BLOCKING = listOf("app", "always", "off")
        val QUICK_KEYS = listOf("dark", "desktop", "full", "screen", "pull", "block")

        private const val PREFERENCES = "defaults"
        private const val KEY = "json"
        private var cached: Defaults? = null

        fun get(context: Context): Defaults {
            cached?.let { return it }
            val text = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
            val made = if (text == null) Defaults() else try {
                fromJson(JSONObject(text))
            } catch (error: JSONException) {
                Defaults()
            }
            cached = made
            return made
        }

        fun save(context: Context, defaults: Defaults) {
            cached = defaults
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, defaults.toJson().toString()).commit()
        }

        private fun strings(json: JSONObject, key: String): List<String>? {
            val array = json.optJSONArray(key) ?: return null
            return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
        }

        private fun pick(json: JSONObject, key: String, allowed: List<String>, fallback: String): String =
            json.optString(key).takeIf { it in allowed } ?: fallback

        fun fromJson(json: JSONObject): Defaults {
            val made = Defaults()
            made.firstRunDone = json.optBoolean("firstRunDone", false)
            made.rememberMode = pick(json, "rememberMode", listOf("nothing", "pick", "everything"), "pick")
            strings(json, "remember")?.let { list -> made.remember = list.filter { it in Kinds.ALL }.toMutableSet() }
            made.thirdPartyCookies = json.optBoolean("thirdPartyCookies", false)
            made.ownStorage = json.optBoolean("ownStorage", true)
            made.clearOnClose = json.optBoolean("clearOnClose", false)
            made.blocking = pick(json, "blocking", LEVELS, "standard")
            strings(json, "lists")?.let { made.lists = it.toMutableSet() }
            strings(json, "customLists")?.let { made.customLists = it.toMutableList() }
            made.wifiOnly = json.optBoolean("wifiOnly", true)
            strings(json, "rules")?.let { made.rules = it.toMutableList() }
            made.links = pick(json, "links", LINKS, "ask")
            made.navStyle = pick(json, "navStyle", NAV_STYLES, "pill")
            made.otherBlocking = pick(json, "otherBlocking", OTHER_BLOCKING, "app")
            made.warnRisky = json.optBoolean("warnRisky", true)
            made.warnPlain = json.optBoolean("warnPlain", true)
            made.checkTrackers = json.optBoolean("checkTrackers", true)
            made.checkCertificate = json.optBoolean("checkCertificate", true)
            made.checkSafeBrowsing = json.optBoolean("checkSafeBrowsing", true)
            made.gesture = pick(json, "gesture", GESTURES, "two")
            strings(json, "quickKeys")?.let { list -> made.quickKeys = list.filter { it in QUICK_KEYS }.toMutableSet() }
            made.theme = pick(json, "theme", listOf("system", "light", "dark"), "system")
            json.optJSONArray("rewrites")?.let { array -> made.rewrites = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { item -> Rewrite.fromJson(item) } }.toMutableList() }
            strings(json, "rewriteScopes")?.let { made.rewriteScopes = it.toMutableSet() }
            made.proxy = json.optString("proxy")
            strings(json, "proxySites")?.let { made.proxySites = it.toMutableList() }
            made.proxyFallback = pick(json, "proxyFallback", listOf("stop", "direct"), "stop")
            made.quickSize = pick(json, "quickSize", listOf("small", "full"), "small")
            made.accent = json.optString("accent", "orange").ifEmpty { "orange" }
            json.optJSONArray("groups")?.let { array -> made.groups = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { item -> Group.fromJson(item) } }.toMutableList() }
            made.homeTab = json.optString("homeTab", "all").ifEmpty { "all" }
            made.fingerprint = pick(json, "fingerprint", listOf("off", "standard", "strict"), "standard")
            made.selectAt = pick(json, "selectAt", listOf("top", "bottom"), "bottom")
            made.browser = json.optString("browser")
            made.linkNote = json.optBoolean("linkNote", true)
            return made
        }

        fun gestureName(key: String): String = when (key) {
            "two" -> "2-finger"
            "handle" -> "handle"
            else -> "both"
        }
    }
}

class Group(val id: String = java.util.UUID.randomUUID().toString(), var name: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)

    companion object {
        fun fromJson(json: JSONObject): Group? = json.optString("id").takeIf { it.isNotEmpty() }?.let { Group(it, json.optString("name")) }
    }
}
