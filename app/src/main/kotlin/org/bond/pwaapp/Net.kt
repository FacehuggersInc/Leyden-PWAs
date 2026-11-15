package org.bond.pwaapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

object Net {
    fun canReadWifi(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun ssid(context: Context): String? {
        if (!canReadWifi(context)) return null
        val manager = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val name = manager.connectionInfo?.ssid?.trim('"') ?: return null
        if (name.isEmpty() || name == "<unknown ssid>") return null
        return name
    }

    fun tailscale(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        for (network in manager.allNetworks) {
            val capabilities = manager.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val links = manager.getLinkProperties(network) ?: continue
            for (link in links.linkAddresses) {
                val address = link.address as? Inet4Address ?: continue
                val bytes = address.address
                val first = bytes[0].toInt() and 0xFF
                val second = bytes[1].toInt() and 0xFF
                if (first == 100 && second in 64..127) return true
            }
        }
        return false
    }

    fun label(context: Context, site: Site): String {
        val parts = mutableListOf<String>()
        ssid(context)?.let { parts += "wifi:$it" }
        if (tailscale(context)) parts += "tailscale"
        if (parts.isEmpty()) parts += "other"
        return parts.joinToString("|")
    }

    fun describe(value: String): String = when {
        value.isEmpty() -> "any"
        value == "tailscale" -> "Tailscale on"
        value == "other" -> "other networks"
        value.startsWith("wifi:") -> "Wi-Fi “${value.removePrefix("wifi:")}”"
        else -> value
    }

    class Choice(val url: String?, val ask: Boolean, val reason: String)

    fun choose(context: Context, site: Site): Choice {
        val power = site.power
        if (power.networks.isEmpty()) return Choice(null, false, "")
        val name = ssid(context)
        val tunnel = tailscale(context)
        for (entry in power.networks) {
            if (entry.url.isEmpty()) continue
            if (entry.kind == "wifi" && name != null && entry.ssid == name) return Choice(entry.url, false, "on Wi-Fi “$name”")
            if (entry.kind == "tailscale" && tunnel) return Choice(entry.url, false, "Tailscale is on")
        }
        return when (power.elseMode) {
            "last" -> Choice(power.lastAddress.ifEmpty { null }, false, "the last address that worked")
            "none" -> Choice(null, false, "none of its networks are here")
            else -> Choice(null, true, "none of its networks are here")
        }
    }

    fun swapOrigin(original: String, base: String): String = try {
        val from = java.net.URI(original)
        val to = java.net.URI(normalizeAddress(base))
        java.net.URI(to.scheme, from.rawUserInfo, to.host, to.port, from.rawPath, from.rawQuery, from.rawFragment).toString()
    } catch (error: Exception) {
        base
    }
}

object Fetcher {
    fun text(address: String, headers: Map<String, String> = emptyMap(), limit: Int = 2_000_000): String {
        var url = address
        repeat(5) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            for ((key, value) in headers) connection.setRequestProperty(key, value)
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), connection.getHeaderField("Location") ?: throw java.io.IOException("redirect without an address")).toString()
                    return@repeat
                }
                if (code >= 400) throw java.io.IOException("the server answered $code")
                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (out.size() > limit) throw java.io.IOException("the file is too big")
                    }
                }
                return out.toString("UTF-8")
            } finally {
                connection.disconnect()
            }
        }
        throw java.io.IOException("too many redirects")
    }

    fun gm(options: JSONObject, cookie: String?): JSONObject {
        val url = options.optString("url")
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = options.optString("method", "GET").uppercase()
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            options.optJSONObject("headers")?.let { headers -> headers.keys().forEach { connection.setRequestProperty(it, headers.optString(it)) } }
            if (cookie != null) connection.setRequestProperty("Cookie", cookie)
            val data = if (options.isNull("data")) null else options.optString("data")
            if (data != null && connection.requestMethod != "GET") {
                connection.doOutput = true
                connection.outputStream.use { it.write(data.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val headers = StringBuilder()
            connection.headerFields.forEach { (key, values) -> if (key != null) headers.append(key).append(": ").append(values.joinToString(", ")).append("\r\n") }
            JSONObject().put("status", code).put("statusText", connection.responseMessage.orEmpty()).put("responseText", body).put("response", body)
                .put("finalUrl", connection.url.toString()).put("responseHeaders", headers.toString()).put("readyState", 4)
        } catch (error: Exception) {
            JSONObject().put("error", error.message ?: "request failed").put("status", 0)
        }
    }
}
