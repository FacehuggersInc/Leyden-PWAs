package org.bond.pwaapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FilterList(val key: String, val name: String, val detail: String, val url: String, val custom: Boolean = false)

object Blocker {
    private const val UPDATE_AFTER = 4L * 24 * 60 * 60 * 1000
    private const val LIMIT = 16L * 1024 * 1024

    val BUILT_IN = listOf(
        FilterList("easylist", "EasyList", "ads", "https://easylist.to/easylist/easylist.txt"),
        FilterList("easyprivacy", "EasyPrivacy", "trackers", "https://easylist.to/easylist/easyprivacy.txt"),
        FilterList("cookies", "Cookie banners", "hides consent pop-ups", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt"),
    )

    @Volatile
    private var loaded: Map<String, Filters> = emptyMap()

    @Volatile
    var loading = false
        private set

    @Volatile
    var updating = false
        private set

    private val userCache = HashMap<String, Filters>()
    private val main = Handler(Looper.getMainLooper())

    fun lists(context: Context): List<FilterList> =
        BUILT_IN + Defaults.get(context).customLists.map { FilterList(customKey(it), hostOf(it).ifEmpty { it }, "your list by address", it, true) }

    fun customKey(url: String): String = "custom-" + Integer.toHexString(url.hashCode())

    private fun folder(context: Context): File = File(context.filesDir, "lists").apply { mkdirs() }

    private fun file(context: Context, key: String): File = File(folder(context), "$key.txt")

    fun downloaded(context: Context, key: String): Boolean = file(context, key).isFile

    fun anyDownloaded(context: Context): Boolean = lists(context).any { downloaded(context, it.key) }

    fun lastUpdate(context: Context): Long =
        lists(context).mapNotNull { file(context, it.key).takeIf { file -> file.isFile }?.lastModified() }.maxOrNull() ?: 0L

    fun ruleCount(key: String): Int = loaded[key]?.let { it.networkCount + it.cosmeticCount } ?: 0

    val ready: Boolean
        get() = loaded.isNotEmpty()

    fun ensure(context: Context) {
        val app = context.applicationContext
        if (loaded.isEmpty() && !loading && anyDownloaded(app)) reload(app)
        val defaults = Defaults.get(app)
        if (!updating && defaults.firstRunDone && defaults.lists.isNotEmpty() && System.currentTimeMillis() - lastUpdate(app) > UPDATE_AFTER) update(app, false) {}
    }

    fun reload(context: Context, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        loading = true
        Thread {
            val wanted = Defaults.get(app).lists
            val made = HashMap<String, Filters>()
            for (list in lists(app)) {
                if (list.key !in wanted) continue
                val file = file(app, list.key)
                if (!file.isFile) continue
                val filters = Filters(list.name)
                try {
                    file.bufferedReader().useLines { filters.addAll(it) }
                } catch (error: IOException) {
                    continue
                }
                made[list.key] = filters
            }
            loaded = made
            loading = false
            done?.let { main.post(it) }
        }.start()
    }

    fun onWifi(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun update(context: Context, force: Boolean, done: (String?) -> Unit) {
        val app = context.applicationContext
        val defaults = Defaults.get(app)
        if (!force && defaults.wifiOnly && !onWifi(app)) {
            done("Waiting for Wi-Fi to update the lists")
            return
        }
        if (updating) {
            done(null)
            return
        }
        updating = true
        Thread {
            val failed = mutableListOf<String>()
            for (list in lists(app)) {
                if (list.key !in defaults.lists) continue
                try {
                    download(list.url, file(app, list.key))
                } catch (error: IOException) {
                    failed += list.name
                }
            }
            updating = false
            reload(app) {
                done(if (failed.isEmpty()) null else "Could not get " + failed.joinToString(", "))
            }
        }.start()
    }

    private fun download(address: String, target: File) {
        var url = address
        repeat(5) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), connection.getHeaderField("Location") ?: throw IOException("no location")).toString()
                    return@repeat
                }
                if (code >= 400) throw IOException("answered $code")
                val part = File(target.path + ".part")
                var total = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > LIMIT) throw IOException("too big")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val head = part.bufferedReader().use { it.readLine().orEmpty() }
                if (!head.startsWith("[") && !head.startsWith("!")) {
                    part.delete()
                    throw IOException("not a filter list")
                }
                if (!part.renameTo(target)) throw IOException("could not save")
                return
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    fun forget(context: Context, key: String) {
        file(context, key).delete()
        loaded = loaded - key
    }

    private fun user(rules: List<String>, source: String): Filters? {
        if (rules.isEmpty()) return null
        val key = source + "\n" + rules.joinToString("\n")
        synchronized(userCache) {
            userCache[key]?.let { return it }
            val made = Filters(source)
            rules.forEach { made.add(it) }
            if (userCache.size > 40) userCache.clear()
            userCache[key] = made
            return made
        }
    }

    fun level(site: Site, defaults: Defaults, pageUrl: String?): String {
        val pageHost = hostOf(pageUrl)
        if (pageHost.isNotEmpty() && site.unblocked.any { Filters.within(pageHost, it) }) return "off"
        if (Relax.matches(site, pageUrl)) return "off"
        if (site.inScope(pageUrl)) return site.blocking
        return when (site.otherBlockingNow(defaults)) {
            "off" -> "off"
            "always" -> if (site.blocking == "off") "standard" else site.blocking
            else -> site.blocking
        }
    }

    fun check(site: Site, defaults: Defaults, url: String, pageUrl: String?, level: String): Verdict? {
        if (level == "off") return null
        if (site.fp.relaxChecks && Relax.known(url)) return null
        val host = hostOf(url)
        if (host.isEmpty()) return null
        val pageHost = hostOf(pageUrl).ifEmpty { site.host }
        for (filters in listOfNotNull(user(site.rules, "your rule"), user(defaults.rules, "your rule for every app"))) {
            filters.match(url, host, pageHost)?.let { return it }
        }
        for (filters in loaded.values) {
            val verdict = filters.match(url, host, pageHost) ?: continue
            if (verdict.blocked) return verdict
        }
        return null
    }

    fun css(site: Site, defaults: Defaults, level: String, pageHost: String): String {
        if (level == "off") return ""
        val out = StringBuilder()
        user(site.rules, "your rule")?.let { out.append(it.css(pageHost, true)) }
        user(defaults.rules, "your rule for every app")?.let { out.append(it.css(pageHost, true)) }
        for ((key, filters) in loaded) {
            if (level == "strict" || key == "cookies") out.append(filters.css(pageHost, true))
        }
        return out.toString()
    }

    fun listed(host: String): Boolean = loaded.values.any { it.hostListed(host) }
}
