package org.bond.pwaapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

private fun JSONObject.strings(key: String): MutableList<String> {
    val array = optJSONArray(key) ?: return mutableListOf()
    return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }.toMutableList()
}

private fun <T> JSONObject.objects(key: String, make: (JSONObject) -> T): MutableList<T> {
    val array = optJSONArray(key) ?: return mutableListOf()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(make) }.toMutableList()
}

class Trigger(var kind: String, var value: String = "") {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("value", value)

    val group: String
        get() = when (kind) {
            in PAGE -> "page"
            in YOU -> "you"
            else -> "app"
        }

    fun describe(site: Site?): Pair<String, String> = when (kind) {
        "before" -> "before the page loads" to "before the site’s own scripts"
        "ready" -> "when the page is ready" to "built, images may still load"
        "loaded" -> "when it’s fully loaded" to "everything, including images"
        "appears" -> "when an element appears" to value.ifEmpty { "pick it with Select" }
        "address" -> "when the address changes" to "pages that switch without loading"
        "every" -> "every ${value.ifEmpty { "30" }} s" to "while the app is open"
        "button" -> "a button" to (site?.power?.buttons?.firstOrNull { it.id == value }?.name ?: "pick a button")
        "key" -> "volume ${value.ifEmpty { "down" }}" to "only while the app is open"
        "swipe" -> "swipe ${Controls.swipeName(value)}" to "across the page"
        "quick" -> "from quick settings" to "in its run list"
        "open" -> "when the app opens" to ""
        "unlock" -> "after unlocking" to ""
        "back" -> "when you come back to it" to "from the background"
        else -> "when the network changes" to "home Wi-Fi, Tailscale, other"
    }

    companion object {
        val PAGE = setOf("before", "ready", "loaded", "appears", "address", "every")
        val YOU = setOf("button", "key", "swipe", "quick")
        val APP = setOf("open", "unlock", "back", "network")
        val PHASES = listOf("before", "ready", "loaded")

        fun fromJson(json: JSONObject): Trigger = Trigger(json.optString("kind"), json.optString("value"))
    }
}

class Source(
    var type: String,
    var url: String,
    var repo: String = "",
    var ref: String = "",
    var path: String = "",
    var commit: String = "",
    var follow: String = "branch",
    var updates: String = "ask",
    var every: String = "daily",
    var lastCheck: Long = 0L,
    var upstream: String = "",
    var pending: String = "",
    var detached: Boolean = false,
    var token: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type).put("url", url).put("repo", repo).put("ref", ref).put("path", path).put("commit", commit)
        .put("follow", follow).put("updates", updates).put("every", every).put("lastCheck", lastCheck)
        .put("upstream", upstream).put("pending", pending).put("detached", detached).put("token", token)

    val label: String
        get() = if (type == "git") "${hostOf(repo).ifEmpty { repo }} · ${ref.ifEmpty { "default" }}" else hostOf(url).ifEmpty { url }

    companion object {
        fun fromJson(json: JSONObject): Source = Source(
            json.optString("type"), json.optString("url"), json.optString("repo"), json.optString("ref"), json.optString("path"),
            json.optString("commit"), json.optString("follow", "branch"), json.optString("updates", "ask"), json.optString("every", "daily"),
            json.optLong("lastCheck"), json.optString("upstream"), json.optString("pending"), json.optBoolean("detached"), json.optString("token"),
        )
    }
}

class AutoSpec(var targets: MutableList<String> = mutableListOf(), var action: String = "click", var text: String = "") {
    fun toJson(): JSONObject = JSONObject().put("targets", JSONArray(targets)).put("action", action).put("text", text)

    companion object {
        fun fromJson(json: JSONObject): AutoSpec = AutoSpec(json.strings("targets"), json.optString("action", "click"), json.optString("text"))
    }
}

class Insertion(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var kind: String = "css",
    var code: String = "",
    var on: Boolean = true,
) {
    var triggers = mutableListOf(Trigger("before"))
    var path = ""
    var exists = ""
    var network = ""
    var timeFrom = ""
    var timeTo = ""
    var after = ""
    var waitMs = 0
    var pauseMs = 0
    var often = "every"
    var atMost = 0
    var frames = false
    var onFail = "note"
    var auto: AutoSpec? = null
    var source: Source? = null
    var version = ""
    var grants = mutableListOf<String>()
    var connects = mutableListOf<String>()
    var matches = mutableListOf<String>()

    val phase: String
        get() = triggers.firstOrNull { it.kind in Trigger.PHASES }?.kind ?: if (triggers.any { it.kind in Trigger.PAGE }) "ready" else "manual"

    val runsOnItsOwn: Boolean
        get() = triggers.any { it.kind in Trigger.PAGE || it.kind in Trigger.APP }

    val fromOutside: Boolean
        get() = source != null && source?.detached != true

    val kindName: String
        get() = when {
            kind == "auto" -> "auto target"
            source?.type == "git" -> if (kind == "css") "style · from git" else "script · from git"
            source != null -> if (kind == "css") "userstyle" else "userscript"
            kind == "css" -> "style"
            else -> "script"
        }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("id", id).put("name", name).put("kind", kind).put("code", code).put("on", on)
            .put("triggers", JSONArray(triggers.map { it.toJson() }))
            .put("path", path).put("exists", exists).put("network", network).put("timeFrom", timeFrom).put("timeTo", timeTo).put("after", after)
            .put("waitMs", waitMs).put("pauseMs", pauseMs).put("often", often).put("atMost", atMost).put("frames", frames).put("onFail", onFail)
            .put("version", version).put("grants", JSONArray(grants)).put("connects", JSONArray(connects)).put("matches", JSONArray(matches))
        auto?.let { json.put("auto", it.toJson()) }
        source?.let { json.put("source", it.toJson()) }
        return json
    }

    fun copy(): Insertion = fromJson(toJson())

    companion object {
        fun fromJson(json: JSONObject): Insertion {
            val item = Insertion(json.optString("id").ifEmpty { UUID.randomUUID().toString() }, json.optString("name"), json.optString("kind", "css"), json.optString("code"), json.optBoolean("on", true))
            if (json.has("triggers")) item.triggers = json.objects("triggers") { Trigger.fromJson(it) }
            item.path = json.optString("path")
            item.exists = json.optString("exists")
            item.network = json.optString("network")
            item.timeFrom = json.optString("timeFrom")
            item.timeTo = json.optString("timeTo")
            item.after = json.optString("after")
            item.waitMs = json.optInt("waitMs")
            item.pauseMs = json.optInt("pauseMs")
            item.often = json.optString("often", "every")
            item.atMost = json.optInt("atMost")
            item.frames = json.optBoolean("frames")
            item.onFail = json.optString("onFail", "note")
            item.version = json.optString("version")
            item.grants = json.strings("grants")
            item.connects = json.strings("connects")
            item.matches = json.strings("matches")
            json.optJSONObject("auto")?.let { item.auto = AutoSpec.fromJson(it) }
            json.optJSONObject("source")?.let { item.source = Source.fromJson(it) }
            return item
        }

        fun legacy(json: JSONObject, kind: String): Insertion {
            val item = Insertion(json.optString("id").ifEmpty { UUID.randomUUID().toString() }, json.optString("name"), kind, json.optString("code"), json.optBoolean("on", true))
            item.triggers = mutableListOf(Trigger(if (json.optBoolean("early", true)) "before" else "loaded"))
            item.path = json.optString("path")
            return item
        }
    }
}

class Action(var kind: String = "builtin", var value: String = "nothing") {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("value", value)

    fun describe(site: Site): String = when (kind) {
        "builtin" -> "built-in: " + Controls.builtinName(value)
        "button" -> "button: " + (site.power.buttons.firstOrNull { it.id == value }?.name ?: "?")
        else -> "custom: " + (site.insertions.firstOrNull { it.id == value }?.name?.ifEmpty { "untitled" } ?: "?")
    }

    companion object {
        fun fromJson(json: JSONObject?): Action = if (json == null) Action() else Action(json.optString("kind", "builtin"), json.optString("value", "nothing"))
    }
}

class Btn(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var does: String = "click",
    var value: String = "",
    var show: String = "floating",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("does", does).put("value", value).put("show", show)

    companion object {
        fun fromJson(json: JSONObject): Btn = Btn(json.optString("id").ifEmpty { UUID.randomUUID().toString() }, json.optString("name"), json.optString("does", "click"), json.optString("value"), json.optString("show", "floating"))
    }
}

class Rewrite(
    val id: String = UUID.randomUUID().toString(),
    var from: String = "",
    var to: String = "",
    var on: Boolean = true,
    var scopes: MutableSet<String> = mutableSetOf("incoming", "inside"),
    var site: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("from", from).put("to", to).put("on", on).put("scopes", JSONArray(scopes.toList())).put("site", site)

    companion object {
        val SCOPES = listOf("incoming", "inside", "outgoing")

        fun scopeName(scope: String): String = when (scope) {
            "incoming" -> "from other apps"
            "inside" -> "inside"
            else -> "out to browser"
        }

        fun fromJson(json: JSONObject): Rewrite = Rewrite(json.optString("id").ifEmpty { UUID.randomUUID().toString() }, json.optString("from"), json.optString("to"), json.optBoolean("on", true), json.strings("scopes").toMutableSet(), json.optString("site"))

        fun apply(rules: List<Rewrite>, url: String, scope: String, siteId: String?): String {
            if (!url.startsWith("http")) return url
            for (rule in rules) {
                if (!rule.on || scope !in rule.scopes) continue
                if (rule.site.isNotEmpty() && rule.site != siteId) continue
                val made = rewrite(rule, url) ?: continue
                return made
            }
            return url
        }

        fun rewrite(rule: Rewrite, url: String): String? {
            val uri = try {
                java.net.URI(url)
            } catch (error: Exception) {
                return null
            }
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }
            val target = rule.to.trim().removePrefix("https://").removePrefix("http://")
            val toHost = target.substringBefore('/')
            val toPath = if ('/' in target) "/" + target.substringAfter('/') else ""
            for (pattern in rule.from.split(',').map { it.trim().removePrefix("https://").removePrefix("http://") }.filter { it.isNotEmpty() }) {
                val fromHost = pattern.substringBefore('/').lowercase().removePrefix("www.")
                val fromPath = if ('/' in pattern) "/" + pattern.substringAfter('/') else ""
                if (host != fromHost && !host.endsWith(".$fromHost")) continue
                if (fromPath.isNotEmpty() && !path.startsWith(fromPath)) continue
                val rest = if (fromPath.isNotEmpty()) path.removePrefix(fromPath) else path
                val scheme = if (rule.to.startsWith("http://")) "http" else "https"
                val joined = (if (toPath.isNotEmpty()) toPath.trimEnd('/') + (if (rest.startsWith("/") || rest.isEmpty()) rest else "/$rest") else rest).ifEmpty { "/" }
                val query = uri.rawQuery?.let { "?$it" }.orEmpty()
                val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
                return "$scheme://$toHost$joined$query$fragment"
            }
            return null
        }
    }
}

class NetAddr(var kind: String = "wifi", var ssid: String = "", var url: String = "") {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("ssid", ssid).put("url", url)

    companion object {
        fun fromJson(json: JSONObject): NetAddr = NetAddr(json.optString("kind", "wifi"), json.optString("ssid"), json.optString("url"))
    }
}

class CheckRecord(
    val at: Long,
    val scriptsOwn: Int,
    val scriptsInline: Int,
    val scriptHosts: Map<String, Int>,
    val trackerHosts: Set<String>,
    val trackers: Int,
    val cookies: Int,
) {
    val scriptsOther: Int
        get() = scriptHosts.values.sum()

    fun toJson(): JSONObject = JSONObject()
        .put("at", at).put("scriptsOwn", scriptsOwn).put("scriptsInline", scriptsInline)
        .put("scriptHosts", JSONObject(scriptHosts as Map<*, *>)).put("trackerHosts", JSONArray(trackerHosts.toList()))
        .put("trackers", trackers).put("cookies", cookies)

    companion object {
        fun fromJson(json: JSONObject): CheckRecord {
            val hosts = LinkedHashMap<String, Int>()
            json.optJSONObject("scriptHosts")?.let { map -> map.keys().forEach { hosts[it] = map.optInt(it) } }
            return CheckRecord(json.optLong("at"), json.optInt("scriptsOwn"), json.optInt("scriptsInline"), hosts, json.strings("trackerHosts").toSet(), json.optInt("trackers"), json.optInt("cookies"))
        }
    }
}

class Power {
    var insertionsOn = true
    var builtinLists = true
    var builtinHidden = true
    var builtinTheme = true
    var builtinLogin = true
    var builtinPip = true
    var badge = "none"
    var copyOf = ""
    var networks = mutableListOf<NetAddr>()
    var elseMode = "ask"
    var lastAddress = ""
    var keysOn = false
    var volumeUp = Action()
    var volumeDown = Action()
    var swipeLeft = Action()
    var swipeRight = Action()
    var swipeDown = Action()
    var buttons = mutableListOf<Btn>()
    var audio = false
    var pip = true
    var shareText = true
    var shareFiles = true
    var shareTemplate = ""
    var shareAsk = true
    var shareTarget = ""
    var linkHosts = mutableListOf<String>()
    var routeLinks = true
    var downloadFolder = ""
    var downloadFolderName = ""
    var shotFolder = ""
    var shotFolderName = ""
    var check: CheckRecord? = null

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("insertionsOn", insertionsOn).put("builtinLists", builtinLists).put("builtinHidden", builtinHidden).put("builtinTheme", builtinTheme).put("builtinLogin", builtinLogin).put("builtinPip", builtinPip)
            .put("badge", badge).put("copyOf", copyOf)
            .put("networks", JSONArray(networks.map { it.toJson() })).put("elseMode", elseMode).put("lastAddress", lastAddress)
            .put("keysOn", keysOn).put("volumeUp", volumeUp.toJson()).put("volumeDown", volumeDown.toJson())
            .put("swipeLeft", swipeLeft.toJson()).put("swipeRight", swipeRight.toJson()).put("swipeDown", swipeDown.toJson())
            .put("buttons", JSONArray(buttons.map { it.toJson() }))
            .put("audio", audio).put("pip", pip)
            .put("shareText", shareText).put("shareFiles", shareFiles).put("shareTemplate", shareTemplate).put("shareAsk", shareAsk).put("shareTarget", shareTarget)
            .put("linkHosts", JSONArray(linkHosts)).put("routeLinks", routeLinks)
            .put("downloadFolder", downloadFolder).put("downloadFolderName", downloadFolderName)
            .put("shotFolder", shotFolder).put("shotFolderName", shotFolderName)
        check?.let { json.put("check", it.toJson()) }
        return json
    }

    companion object {
        fun fromJson(json: JSONObject?): Power {
            val power = Power()
            if (json == null) return power
            power.insertionsOn = json.optBoolean("insertionsOn", true)
            power.builtinLists = json.optBoolean("builtinLists", true)
            power.builtinHidden = json.optBoolean("builtinHidden", true)
            power.builtinTheme = json.optBoolean("builtinTheme", true)
            power.builtinLogin = json.optBoolean("builtinLogin", true)
            power.builtinPip = json.optBoolean("builtinPip", true)
            power.badge = json.optString("badge", "none")
            power.copyOf = json.optString("copyOf")
            power.networks = json.objects("networks") { NetAddr.fromJson(it) }
            power.elseMode = json.optString("elseMode", "ask")
            power.lastAddress = json.optString("lastAddress")
            power.keysOn = json.optBoolean("keysOn")
            power.volumeUp = Action.fromJson(json.optJSONObject("volumeUp"))
            power.volumeDown = Action.fromJson(json.optJSONObject("volumeDown"))
            power.swipeLeft = Action.fromJson(json.optJSONObject("swipeLeft"))
            power.swipeRight = Action.fromJson(json.optJSONObject("swipeRight"))
            power.swipeDown = Action.fromJson(json.optJSONObject("swipeDown"))
            power.buttons = json.objects("buttons") { Btn.fromJson(it) }
            power.audio = json.optBoolean("audio")
            power.pip = json.optBoolean("pip", true)
            power.shareText = json.optBoolean("shareText", true)
            power.shareFiles = json.optBoolean("shareFiles", true)
            power.shareTemplate = json.optString("shareTemplate")
            power.shareAsk = json.optBoolean("shareAsk", true)
            power.shareTarget = json.optString("shareTarget")
            power.linkHosts = json.strings("linkHosts")
            power.routeLinks = json.optBoolean("routeLinks", true)
            power.downloadFolder = json.optString("downloadFolder")
            power.downloadFolderName = json.optString("downloadFolderName")
            power.shotFolder = json.optString("shotFolder")
            power.shotFolderName = json.optString("shotFolderName")
            json.optJSONObject("check")?.let { power.check = CheckRecord.fromJson(it) }
            return power
        }
    }
}

class Header(
    val kind: String,
    val name: String,
    val version: String,
    val matches: List<String>,
    val grants: List<String>,
    val connects: List<String>,
    val runAt: String,
    val updateUrl: String,
    val preprocessor: String,
)

object Headers {
    private val LINE = Regex("^\\s*(?://|)\\s*@([\\w:.-]+)\\s+(.*?)\\s*$")

    fun read(code: String): Header? {
        val script = code.indexOf("==UserScript==")
        val style = code.indexOf("==UserStyle==")
        if (script < 0 && style < 0) return null
        val start = if (script >= 0) script else style
        val endMark = if (script >= 0) "==/UserScript==" else "==/UserStyle=="
        val end = code.indexOf(endMark, start)
        if (end < 0) return null
        val block = code.substring(start, end)
        val values = mutableMapOf<String, MutableList<String>>()
        for (raw in block.lines()) {
            val match = LINE.find(raw.trim().removePrefix("*").trim()) ?: continue
            values.getOrPut(match.groupValues[1].lowercase()) { mutableListOf() }.add(match.groupValues[2])
        }
        fun one(key: String): String = values[key]?.firstOrNull().orEmpty()
        return Header(
            if (script >= 0) "js" else "css",
            one("name"),
            one("version"),
            (values["match"].orEmpty() + values["include"].orEmpty()),
            values["grant"].orEmpty().filter { it != "none" },
            values["connect"].orEmpty(),
            one("run-at"),
            one("updateurl").ifEmpty { one("downloadurl") },
            one("preprocessor").ifEmpty { "default" },
        )
    }

    fun disclosures(item: Insertion): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        val grants = item.grants.map { it.lowercase() }
        if (grants.any { "xmlhttprequest" in it }) {
            val hosts = item.connects.joinToString(", ").ifEmpty { "any server" }
            out += Triple("talk to $hosts on its own", "sends requests outside the page", "network")
        }
        if (grants.any { "setvalue" in it || "getvalue" in it }) out += Triple("keep its own data", "listed under the app’s storage", "storage")
        if (grants.any { "clipboard" in it }) out += Triple("write to your clipboard", "when the script decides to", "clipboard")
        if (grants.any { "openintab" in it }) out += Triple("open new pages", "in the app or your browser", "pages")
        if (grants.any { "addstyle" in it }) out += Triple("add styles", "changes how pages look", "style")
        if (item.triggers.any { it.kind == "before" }) out += Triple("run before the page loads", "", "early")
        val others = item.matches.filter { pattern -> pattern.isNotEmpty() && pattern != "*" }
        if (others.isEmpty() && item.kind == "js" && item.source != null) out += Triple("runs on every page of this app", "it lists no pages of its own", "pages")
        return out
    }

    fun risky(line: String): Boolean {
        val text = line.lowercase()
        return "xmlhttprequest" in text || "fetch(" in text || "clipboard" in text || "sendbeacon" in text || "websocket" in text ||
            "localstorage" in text || "document.cookie" in text || "gm_setvalue" in text || "gm.setvalue" in text || "eval(" in text || "new function" in text
    }

    fun fromUserCss(code: String): Pair<String, String?> {
        val header = read(code)
        if (header != null && header.preprocessor !in listOf("default", "")) return "" to "It uses the ${header.preprocessor} preprocessor, which Leyden can’t run."
        var body = code
        val headEnd = code.indexOf("==/UserStyle==")
        if (headEnd >= 0) body = code.substring(code.indexOf("*/", headEnd).let { if (it < 0) headEnd + 14 else it + 2 })
        body = Regex("/\\*\\[\\[[\\w-]+\\]\\]\\*/").replace(body, "")
        if (!body.contains("@-moz-document")) return body.trim() to null
        val out = StringBuilder()
        var at = 0
        while (true) {
            val start = body.indexOf("@-moz-document", at)
            if (start < 0) break
            out.append(body, at, start)
            val open = body.indexOf('{', start)
            if (open < 0) break
            var depth = 1
            var index = open + 1
            while (index < body.length && depth > 0) {
                if (body[index] == '{') depth++ else if (body[index] == '}') depth--
                index++
            }
            val inner = body.substring(open + 1, (index - 1).coerceAtLeast(open + 1))
            val rules = body.substring(start + 14, open).trim()
            out.append("/*pwa-doc ").append(rules.replace("*/", "")).append(" */\n").append(inner).append("\n/*pwa-end*/\n")
            at = index
        }
        if (at < body.length) out.append(body.substring(at))
        return out.toString().trim() to null
    }
}

object Diff {
    fun lines(old: String, new: String): List<Pair<Char, String>> {
        val a = old.lines()
        val b = new.lines()
        if (a.size * b.size > 4_000_000) return listOf('-' to "(too long to compare line by line)", '+' to "${b.size} lines")
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0) {
            table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
        }
        val out = mutableListOf<Pair<Char, String>>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    out += ' ' to a[i]
                    i++
                    j++
                }
                table[i + 1][j] >= table[i][j + 1] -> out += '-' to a[i++]
                else -> out += '+' to b[j++]
            }
        }
        while (i < a.size) out += '-' to a[i++]
        while (j < b.size) out += '+' to b[j++]
        return out
    }

    fun changes(old: String, new: String): List<Pair<Char, String>> = lines(old, new).filter { it.first != ' ' }
}

class Failure(var day: String, var count: Int, var message: String, var line: Int)

object AddonState {
    private val cache = HashMap<String, JSONObject>()

    private fun file(context: Context, siteId: String): File = File(File(context.filesDir, "addons").apply { mkdirs() }, "$siteId.json")

    @Synchronized
    private fun load(context: Context, siteId: String): JSONObject {
        cache[siteId]?.let { return it }
        val file = file(context, siteId)
        val json = if (file.isFile) try {
            JSONObject(file.readText())
        } catch (error: JSONException) {
            JSONObject()
        } else JSONObject()
        cache[siteId] = json
        return json
    }

    @Synchronized
    private fun store(context: Context, siteId: String) {
        val json = cache[siteId] ?: return
        val file = file(context, siteId)
        if (json.length() == 0) file.delete() else file.writeText(json.toString())
    }

    private fun section(json: JSONObject, name: String): JSONObject = json.optJSONObject(name) ?: JSONObject().also { json.put(name, it) }

    @Synchronized
    fun getValue(context: Context, siteId: String, id: String, key: String): String? {
        val values = load(context, siteId).optJSONObject("values")?.optJSONObject(id) ?: return null
        return if (values.has(key)) values.optString(key) else null
    }

    @Synchronized
    fun setValue(context: Context, siteId: String, id: String, key: String, value: String?) {
        val json = load(context, siteId)
        val values = section(section(json, "values"), id)
        if (value == null) values.remove(key) else values.put(key, value)
        store(context, siteId)
    }

    @Synchronized
    fun keys(context: Context, siteId: String, id: String): List<String> =
        load(context, siteId).optJSONObject("values")?.optJSONObject(id)?.keys()?.asSequence()?.toList().orEmpty()

    @Synchronized
    fun valuesSize(context: Context, siteId: String, id: String?): Int {
        val values = load(context, siteId).optJSONObject("values") ?: return 0
        return if (id == null) values.toString().length else values.optJSONObject(id)?.toString()?.length ?: 0
    }

    @Synchronized
    fun clearValues(context: Context, siteId: String, id: String?) {
        val json = load(context, siteId)
        if (id == null) json.remove("values") else json.optJSONObject("values")?.remove(id)
        store(context, siteId)
    }

    @Synchronized
    fun failed(context: Context, siteId: String, id: String, message: String, line: Int): Failure {
        val json = load(context, siteId)
        val failures = section(json, "failures")
        val today = MemoryStore.today()
        val old = failures.optJSONObject(id)
        val count = if (old?.optString("day") == today) old.optInt("count") + 1 else 1
        failures.put(id, JSONObject().put("day", today).put("count", count).put("message", message).put("line", line))
        store(context, siteId)
        return Failure(today, count, message, line)
    }

    @Synchronized
    fun failure(context: Context, siteId: String, id: String): Failure? {
        val item = load(context, siteId).optJSONObject("failures")?.optJSONObject(id) ?: return null
        if (item.optString("day") != MemoryStore.today()) return null
        return Failure(item.optString("day"), item.optInt("count"), item.optString("message"), item.optInt("line"))
    }

    @Synchronized
    fun clearFailure(context: Context, siteId: String, id: String) {
        load(context, siteId).optJSONObject("failures")?.remove(id)
        store(context, siteId)
    }

    @Synchronized
    fun ranToday(context: Context, siteId: String, id: String): Boolean = load(context, siteId).optJSONObject("days")?.optString(id) == MemoryStore.today()

    @Synchronized
    fun markRan(context: Context, siteId: String, id: String) {
        section(load(context, siteId), "days").put(id, MemoryStore.today())
        store(context, siteId)
    }

    fun size(context: Context, siteId: String): Long = file(context, siteId).takeIf { it.isFile }?.length() ?: 0L

    @Synchronized
    fun drop(context: Context, siteId: String) {
        cache.remove(siteId)
        file(context, siteId).delete()
    }
}

object Controls {
    val BUILTINS = listOf("nothing", "pageup", "pagedown", "back", "forward", "reload", "top", "playpause", "quick", "screenshot")

    fun builtinName(key: String): String = when (key) {
        "pageup" -> "page up"
        "pagedown" -> "page down"
        "back" -> "back"
        "forward" -> "forward"
        "reload" -> "reload"
        "top" -> "top of page"
        "playpause" -> "play / pause"
        "quick" -> "quick settings"
        "screenshot" -> "screenshot"
        else -> "nothing"
    }

    fun swipeName(key: String): String = when (key) {
        "left" -> "left"
        "right" -> "right"
        else -> "down with two fingers"
    }
}
