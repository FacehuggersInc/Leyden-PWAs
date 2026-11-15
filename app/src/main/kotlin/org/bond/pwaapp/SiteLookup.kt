package org.bond.pwaapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

class Found(
    val url: String,
    val name: String,
    val scope: String,
    val color: Int?,
    val background: Int?,
    val orientation: String,
    val icon: Bitmap?,
    val iconSize: Int,
    val maskable: Boolean,
    val fromManifest: Boolean,
    val shortcuts: List<Shortcut>,
    val mobileLayout: Boolean,
    val redirectedTo: List<String>,
    val googleSignIn: Boolean,
    val shareTarget: String = "",
)

class CertificateProblem(val address: String, message: String) : IOException(message)

class LookupEvent(val key: String, val title: String, val detail: String, val state: String)

object SiteLookup {
    private const val AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
    private const val PAGE_LIMIT = 1_048_576
    private const val ICON_LIMIT = 5_242_880

    private val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val META = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val SIZE = Regex("(\\d+)[xX](\\d+)")
    private val SPACES = Regex("\\s+")
    private val GOOGLE = Regex("accounts\\.google\\.com/(gsi|o/oauth2|signin)", RegexOption.IGNORE_CASE)

    private class Page(val url: String, val bytes: ByteArray, val hops: List<String>) {
        val text: String
            get() = String(bytes, Charsets.UTF_8)
    }

    private class Candidate(val url: String, val size: Int, val maskable: Boolean)

    fun lookup(input: String, trusted: String? = null, report: (LookupEvent) -> Unit = {}): Found {
        val address = normalizeAddress(input)
        report(LookupEvent("respond", "Asking ${hostOf(address).ifEmpty { address }}…", "waiting for an answer", "running"))
        val page = fetch(address, PAGE_LIMIT, trusted, report)
        val base = page.url
        val html = page.text
        val links = LINK.findAll(html).map { attributes(it.value) }.toList()
        val metas = META.findAll(html).map { attributes(it.value) }.toList()
        val title = TITLE.find(html)?.groupValues?.get(1)?.let { unescape(it).trim() }.orEmpty()
        val themeMeta = metas.firstOrNull { it["name"].equals("theme-color", ignoreCase = true) }?.get("content")
        val viewport = metas.any { it["name"].equals("viewport", ignoreCase = true) && it["content"].orEmpty().contains("width", ignoreCase = true) }
        report(LookupEvent("page", "Read the page", listOf(
            title.ifEmpty { "no title" }.let { if (title.isEmpty()) it else "“${it.take(40)}”" },
            bytes(page.bytes.size.toLong()),
            if (viewport) "mobile layout" else "no mobile layout",
        ).joinToString(" · "), "done"))

        var manifest: JSONObject? = null
        var manifestUrl = base
        val manifestHref = links.firstOrNull { "manifest" in rels(it) }?.get("href")
        if (manifestHref != null) {
            report(LookupEvent("manifest", "Reading its app manifest…", manifestHref.substringAfterLast('/').take(40), "running"))
            manifest = try {
                manifestUrl = resolve(base, manifestHref)
                JSONObject(fetch(manifestUrl, PAGE_LIMIT, trusted).text)
            } catch (error: Exception) {
                manifestUrl = base
                null
            }
            if (manifest == null) {
                report(LookupEvent("manifest", "Its app manifest couldn’t be read", "the name, icon and color come from the page instead", "note"))
            } else {
                val shown = text(manifest, "name") ?: text(manifest, "short_name") ?: "no name"
                val extras = listOfNotNull(
                    manifest.optJSONArray("icons")?.length()?.let { plural(it, "icon") },
                    manifest.optJSONArray("shortcuts")?.length()?.takeIf { it > 0 }?.let { plural(it, "shortcut") },
                    if (manifest.has("share_target")) "takes shares" else null,
                )
                report(LookupEvent("manifest", "Found its app manifest", (listOf("“$shown”") + extras).joinToString(" · "), "done"))
            }
        } else {
            report(LookupEvent("manifest", "No app manifest", "the name, icon and color come from the page", "note"))
        }

        val start = manifest?.let { text(it, "start_url") }?.let { resolveOrNull(manifestUrl, it) } ?: base
        val scope = manifest?.let { text(it, "scope") }?.let { resolveOrNull(manifestUrl, it) } ?: (origin(start) + "/")
        val host = URL(start).host
        val full = manifest?.let { text(it, "name") }
        val short = manifest?.let { text(it, "short_name") }
        val name = when {
            full != null && full.length <= 18 -> full
            short != null -> short
            full != null -> full
            title.isNotBlank() -> title.substringBefore(" - ").substringBefore(" | ").trim()
            else -> host
        }
        val orientation = when {
            manifest?.let { text(it, "orientation") }?.startsWith("portrait") == true -> "portrait"
            manifest?.let { text(it, "orientation") }?.startsWith("landscape") == true -> "landscape"
            else -> "any"
        }
        val shortcuts = mutableListOf<Shortcut>()
        manifest?.optJSONArray("shortcuts")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = text(item, "short_name") ?: text(item, "name") ?: continue
                val address = text(item, "url")?.let { resolveOrNull(manifestUrl, it) } ?: continue
                shortcuts += Shortcut(label, address)
            }
        }
        report(LookupEvent("icon", "Finding the best icon…", "", "running"))
        val picked = bestIcon(candidates(manifest, manifestUrl, links, base), trusted)
        if (picked == null) {
            report(LookupEvent("icon", "No usable icon", "it gets a letter, which you can change in step 2", "note"))
        } else {
            report(LookupEvent("icon", "Got its icon", "${picked.first.width} px" + if (picked.second) " · made for app icons" else "", "done"))
        }
        val startHost = hostOf(normalizeAddress(input))
        return Found(
            url = start,
            name = name,
            scope = scope,
            color = parseCssColor(manifest?.let { text(it, "theme_color") }) ?: parseCssColor(themeMeta),
            background = parseCssColor(manifest?.let { text(it, "background_color") }),
            orientation = orientation,
            icon = picked?.first,
            iconSize = picked?.first?.width ?: 0,
            maskable = picked?.second ?: false,
            fromManifest = manifest != null,
            shortcuts = shortcuts.take(8),
            mobileLayout = viewport,
            redirectedTo = page.hops.map { hostOf(it) }.filter { it.isNotEmpty() && !sameSite(it, startHost) }.distinct(),
            googleSignIn = GOOGLE.containsMatchIn(html),
            shareTarget = manifest?.optJSONObject("share_target")?.toString().orEmpty(),
        )
    }

    private fun candidates(manifest: JSONObject?, manifestUrl: String, links: List<Map<String, String>>, base: String): List<Candidate> {
        val found = mutableListOf<Candidate>()
        val icons = manifest?.optJSONArray("icons")
        if (icons != null) {
            for (index in 0 until icons.length()) {
                val icon = icons.optJSONObject(index) ?: continue
                val source = text(icon, "src") ?: continue
                if (svg(source) || text(icon, "type")?.contains("svg") == true) continue
                val purpose = text(icon, "purpose").orEmpty().lowercase().split(SPACES)
                val address = resolveOrNull(manifestUrl, source) ?: continue
                found += Candidate(address, largest(text(icon, "sizes")), "maskable" in purpose)
            }
        }
        for (link in links) {
            val href = link["href"] ?: continue
            if (svg(href)) continue
            val address = resolveOrNull(base, href) ?: continue
            val rel = rels(link)
            val size = largest(link["sizes"])
            when {
                "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel ->
                    found += Candidate(address, if (size > 0) size else 180, false)
                "icon" in rel ->
                    found += Candidate(address, if (size > 0) size else 32, false)
            }
        }
        found += Candidate(resolve(base, "/apple-touch-icon.png"), 180, false)
        found += Candidate(resolve(base, "/favicon.ico"), 16, false)
        return found
            .distinctBy { it.url }
            .sortedWith(compareBy<Candidate>({ if (it.maskable && it.size >= 144) 0 else 1 }, { -it.size }))
    }

    private fun bestIcon(candidates: List<Candidate>, trusted: String?): Pair<Bitmap, Boolean>? {
        for (candidate in candidates) {
            val bytes = try {
                fetch(candidate.url, ICON_LIMIT, trusted).bytes
            } catch (error: Exception) {
                continue
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bitmap.width >= 16 && bitmap.height >= 16) return bitmap to candidate.maskable
        }
        return null
    }

    fun plainError(error: Exception): String = when (error) {
        is java.net.UnknownHostException -> "No site by that name. Check the address, or that you’re on the network it lives on."
        is java.net.SocketTimeoutException -> "No answer within 10 seconds."
        is java.net.ConnectException -> "It refused the connection. Nothing may be running at that address or port."
        is java.net.NoRouteToHostException -> "That address can’t be reached from this network."
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun fetch(start: String, limit: Int, trusted: String?, report: ((LookupEvent) -> Unit)? = null): Page {
        var address = start
        val hops = mutableListOf<String>()
        repeat(6) {
            val began = System.currentTimeMillis()
            val connection = URL(address).openConnection() as HttpURLConnection
            if (connection is HttpsURLConnection) Trust.use(connection, trusted)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/manifest+json,application/json,image/*,*/*;q=0.8")
            try {
                val code = try {
                    connection.responseCode
                } catch (error: SSLException) {
                    report?.invoke(LookupEvent("respond", "It answered, with a certificate Android doesn’t trust", "${hostOf(address)} · after ${System.currentTimeMillis() - began} ms", "bad"))
                    throw CertificateProblem(address, "Its certificate isn’t trusted by Android")
                } catch (error: IOException) {
                    report?.invoke(LookupEvent("respond", "No answer from ${hostOf(address)}", plainError(error), "bad"))
                    throw IOException(plainError(error), error)
                }
                val took = System.currentTimeMillis() - began
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: throw IOException("It redirected nowhere")
                    val from = hostOf(address)
                    address = resolve(address, next)
                    hops += address
                    report?.invoke(LookupEvent("respond", "Asking ${hostOf(address)}…", "$from answered $code in $took ms and sent it on", "running"))
                    return@repeat
                }
                if (code >= 400) {
                    report?.invoke(LookupEvent("respond", "It answered with an error", "HTTP $code from ${hostOf(address)} in $took ms", "bad"))
                    throw IOException("The site answered $code")
                }
                report?.invoke(LookupEvent("respond", "It responded", "HTTP $code from ${hostOf(address)} in $took ms" + if (hops.isNotEmpty()) " · after ${plural(hops.size, "redirect")}" else "", "done"))
                report?.invoke(LookupEvent("page", "Reading the page…", "", "running"))
                val bytes = connection.inputStream.use { readUpTo(it, limit) }
                return Page(address, bytes, hops)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("It redirected too many times")
    }

    private fun readUpTo(stream: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun attributes(tag: String): Map<String, String> =
        ATTRIBUTE.findAll(tag).associate { match ->
            val value = match.groups[3]?.value ?: match.groups[4]?.value ?: match.groups[5]?.value ?: ""
            match.groupValues[1].lowercase() to unescape(value)
        }

    private fun rels(attributes: Map<String, String>): Set<String> =
        attributes["rel"].orEmpty().lowercase().split(SPACES).toSet()

    private fun largest(sizes: String?): Int =
        SIZE.findAll(sizes.orEmpty()).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0

    private fun svg(path: String): Boolean = path.substringBefore('?').endsWith(".svg", ignoreCase = true)

    private fun text(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key).trim().takeIf { it.isNotEmpty() }

    private fun resolve(base: String, href: String): String = URL(URL(base), href).toString()

    fun resolveOrNull(base: String, href: String): String? = try {
        resolve(base, href)
    } catch (error: Exception) {
        null
    }

    private fun origin(url: String): String = URL(url).let { "${it.protocol}://${it.authority}" }

    private fun unescape(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
