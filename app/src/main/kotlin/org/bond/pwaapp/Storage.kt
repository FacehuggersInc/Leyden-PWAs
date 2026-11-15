package org.bond.pwaapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

class Measured(val storage: Long, val cookies: Int, val origins: Int)

object Storage {
    private val main = Handler(Looper.getMainLooper())

    val separate: Boolean by lazy {
        try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (error: Throwable) {
            false
        }
    }

    val earlyScripts: Boolean by lazy {
        try {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        } catch (error: Throwable) {
            false
        }
    }

    fun profileName(site: Site): String? = if (site.ownStorage && separate) "site-${site.id}" else null

    fun attach(webView: WebView, name: String?) {
        if (name == null) return
        ProfileStore.getInstance().getOrCreateProfile(name)
        WebViewCompat.setProfile(webView, name)
    }

    fun attach(webView: WebView, site: Site) = attach(webView, profileName(site))

    fun cookies(name: String?): CookieManager =
        if (name == null) CookieManager.getInstance() else ProfileStore.getInstance().getOrCreateProfile(name).cookieManager

    fun cookies(site: Site): CookieManager = cookies(profileName(site))

    fun webStorage(name: String?): WebStorage =
        if (name == null) WebStorage.getInstance() else ProfileStore.getInstance().getOrCreateProfile(name).webStorage

    fun webStorage(site: Site): WebStorage = webStorage(profileName(site))

    fun cookieCount(site: Site): Int {
        val text = cookies(site).getCookie(site.url) ?: return 0
        return text.split(';').count { it.isNotBlank() }
    }

    fun measure(site: Site, done: (Measured) -> Unit) {
        val count = cookieCount(site)
        val own = profileName(site) != null
        webStorage(site).getOrigins { result ->
            var total = 0L
            var origins = 0
            if (result is Map<*, *>) {
                for (value in result.values) {
                    val origin = value as? WebStorage.Origin ?: continue
                    if (!own && !site.inScope(origin.origin)) continue
                    total += origin.usage
                    origins++
                }
            }
            main.post { done(Measured(total, count, origins)) }
        }
    }

    fun clearCookies(site: Site, done: () -> Unit) {
        val manager = cookies(site)
        if (profileName(site) != null) {
            manager.removeAllCookies { main.post(done) }
            return
        }
        val text = manager.getCookie(site.url)
        if (text != null) {
            val hosts = listOf(site.host, baseDomain(site.host), "." + baseDomain(site.host))
            for (pair in text.split(';')) {
                val name = pair.substringBefore('=').trim()
                if (name.isEmpty()) continue
                for (host in hosts) {
                    manager.setCookie(site.url, "$name=; Max-Age=0; Path=/; Domain=$host")
                }
                manager.setCookie(site.url, "$name=; Max-Age=0; Path=/")
            }
        }
        manager.flush()
        main.post(done)
    }

    fun clearSiteStorage(site: Site, done: () -> Unit) {
        val storage = webStorage(site)
        if (profileName(site) != null) {
            storage.deleteAllData()
            main.post(done)
            return
        }
        storage.getOrigins { result ->
            if (result is Map<*, *>) {
                for (value in result.values) {
                    val origin = value as? WebStorage.Origin ?: continue
                    if (site.inScope(origin.origin)) storage.deleteOrigin(origin.origin)
                }
            }
            main.post(done)
        }
    }

    fun clearAll(context: Context, site: Site, webView: WebView?, done: () -> Unit) {
        clearCookies(site) {
            clearSiteStorage(site) {
                webView?.clearHistory()
                webView?.clearFormData()
                if (webView != null) webView.clearCache(true)
                done()
            }
        }
    }

    fun cacheSize(context: Context): Long = folderSize(File(context.cacheDir, "WebView")) + folderSize(File(context.cacheDir, "org.chromium.android_webview"))

    fun clearCache(context: Context) {
        val view = WebView(context)
        view.clearCache(true)
        view.destroy()
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }

    fun forget(site: Site) {
        if (!separate) return
        try {
            ProfileStore.getInstance().deleteProfile("site-${site.id}")
        } catch (error: IllegalStateException) {
            return
        } catch (error: IllegalArgumentException) {
            return
        }
    }

    private const val DIRTY = "dirty"

    fun markDirty(context: Context, site: Site, dirty: Boolean) {
        val preferences = context.getSharedPreferences(DIRTY, Context.MODE_PRIVATE)
        if (dirty) preferences.edit().putBoolean(site.id, true).commit() else preferences.edit().remove(site.id).commit()
    }

    fun isDirty(context: Context, site: Site): Boolean = context.getSharedPreferences(DIRTY, Context.MODE_PRIVATE).getBoolean(site.id, false)
}
