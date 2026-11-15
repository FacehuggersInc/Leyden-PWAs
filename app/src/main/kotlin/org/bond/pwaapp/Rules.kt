package org.bond.pwaapp

private val NAMED_COLORS = mapOf(
    "black" to 0x000000,
    "white" to 0xFFFFFF,
    "red" to 0xFF0000,
    "green" to 0x008000,
    "blue" to 0x0000FF,
    "gray" to 0x808080,
    "grey" to 0x808080,
    "silver" to 0xC0C0C0,
    "navy" to 0x000080,
    "teal" to 0x008080,
    "purple" to 0x800080,
    "orange" to 0xFFA500,
    "yellow" to 0xFFFF00,
    "maroon" to 0x800000,
    "olive" to 0x808000,
    "lime" to 0x00FF00,
    "aqua" to 0x00FFFF,
    "fuchsia" to 0xFF00FF,
)

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

private val RGB = Regex("rgba?\\(\\s*(\\d{1,3})[,\\s]+(\\d{1,3})[,\\s]+(\\d{1,3})")

private val ADDRESS = Regex("https?://\\S+")

private val NUMERIC_HOST = Regex("^[0-9.]+$")

fun opaque(rgb: Int): Int = (0xFF shl 24) or (rgb and 0xFFFFFF)

fun parseCssColor(text: String?): Int? {
    val value = text?.trim()?.lowercase() ?: return null
    if (value.isEmpty()) return null
    if (value.startsWith("#")) {
        val hex = value.substring(1)
        val full = when (hex.length) {
            3, 4 -> hex.take(3).map { "$it$it" }.joinToString("")
            6, 8 -> hex.take(6)
            else -> return null
        }
        return full.toIntOrNull(16)?.let { opaque(it) }
    }
    RGB.find(value)?.let { match ->
        val (red, green, blue) = match.destructured.toList().map { it.toInt().coerceIn(0, 255) }
        return opaque((red shl 16) or (green shl 8) or blue)
    }
    return NAMED_COLORS[value]?.let { opaque(it) }
}

fun colorHex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

fun isLight(color: Int): Boolean {
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    return (red * 299 + green * 587 + blue * 114) / 1000 > 160
}

fun normalizeAddress(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return ""
    return if (SCHEME.containsMatchIn(text)) text else "https://$text"
}

fun sharedAddress(text: String?): String {
    val value = text?.trim().orEmpty()
    return ADDRESS.find(value)?.value ?: value
}

private val SHARED_SUFFIXES = setOf(
    "ts.net", "github.io", "gitlab.io", "netlify.app", "vercel.app", "pages.dev", "workers.dev", "web.app", "firebaseapp.com",
    "herokuapp.com", "onrender.com", "fly.dev", "glitch.me", "duckdns.org", "ddns.net", "no-ip.org", "blogspot.com",
    "azurewebsites.net", "cloudfront.net", "appspot.com", "ngrok.io", "ngrok-free.app", "trycloudflare.com", "myqnapcloud.com",
    "synology.me", "tplinkdns.com", "dynv6.net", "home-assistant.io", "ui.nabu.casa",
)

fun baseDomain(host: String): String {
    val name = host.lowercase().trimEnd('.')
    if (NUMERIC_HOST.matches(name) || ':' in name) return name
    val labels = name.split('.')
    if (labels.size <= 2) return name
    val lastTwo = labels.takeLast(2).joinToString(".")
    val lastThree = labels.takeLast(3).joinToString(".")
    if (lastThree in SHARED_SUFFIXES) return labels.takeLast(4).joinToString(".")
    if (lastTwo in SHARED_SUFFIXES) return lastThree
    val second = labels[labels.size - 2]
    val country = labels.last().length == 2
    val keep = if (country && second.length <= 3) 3 else 2
    return labels.takeLast(keep).joinToString(".")
}

fun sameSite(host: String?, home: String?): Boolean {
    if (host.isNullOrEmpty() || home.isNullOrEmpty()) return true
    val visited = host.lowercase()
    val base = baseDomain(home)
    return visited == home.lowercase() || visited == base || visited.endsWith(".$base")
}

fun desktopAgent(agent: String): String =
    agent.replace(Regex("\\([^)]*Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")

private val PRIVATE_V4 = Regex("^(10\\.|127\\.|192\\.168\\.|169\\.254\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.)")

private val UNREAD = Regex("\\((\\d{1,5})\\+?\\)")

private val SELF_HOSTED_ENDINGS = listOf(".lan", ".local", ".home", ".internal", ".home.arpa", ".ts.net", ".localdomain")

fun hostOf(url: String?): String = try {
    java.net.URI(url ?: "").host?.lowercase().orEmpty()
} catch (error: Exception) {
    ""
}

fun pathOf(url: String?): String = try {
    java.net.URI(url ?: "").rawPath.orEmpty().ifEmpty { "/" }
} catch (error: Exception) {
    "/"
}

fun isSelfHosted(host: String): Boolean {
    val name = host.lowercase().trimEnd('.')
    if (name.isEmpty()) return false
    if (name == "localhost" || '.' !in name) return true
    if (PRIVATE_V4.containsMatchIn(name)) return true
    if (name.startsWith("[fd") || name.startsWith("fd") && ':' in name) return true
    return SELF_HOSTED_ENDINGS.any { name.endsWith(it) }
}

fun unreadFrom(title: String?): Int? {
    val value = title ?: return null
    return UNREAD.find(value)?.groupValues?.get(1)?.toIntOrNull()
}

fun pathMatches(pattern: String, path: String): Boolean {
    val wanted = pattern.trim()
    if (wanted.isEmpty() || wanted == "*" || wanted == "/") return true
    if ('*' !in wanted) return path.startsWith(wanted)
    val regex = wanted.split('*').joinToString(".*") { Regex.escape(it) }
    return Regex("^$regex").containsMatchIn(path)
}

fun ago(then: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - then) / 60_000
    return when {
        then <= 0 -> "never"
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 48 -> "yesterday"
        else -> "${minutes / (60 * 24)} days ago"
    }
}

fun shortAgo(then: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - then) / 60_000
    return when {
        then <= 0 -> "never"
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min"
        minutes < 60 * 24 -> "${minutes / 60} h"
        else -> "${minutes / (60 * 24)} d"
    }
}

fun bytes(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    size < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", size / 1048576.0)
    else -> String.format(java.util.Locale.US, "%.1f GB", size / 1073741824.0)
}

fun duration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

fun plural(count: Int, one: String, many: String = one + "s"): String = "$count " + if (count == 1) one else many
