package org.bond.pwaapp

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import org.json.JSONObject
import java.io.File

object Kept {
    private fun folder(context: Context): File = File(context.filesDir, "kept").apply { mkdirs() }

    private fun file(context: Context, id: String): File = File(folder(context), "$id.json")

    private fun iconFile(context: Context, id: String): File = File(folder(context), "$id.png")

    fun strip(site: Site): Site {
        val kept = site.copy()
        kept.insertions = mutableListOf()
        kept.lockMode = "off"
        kept.pinHash = null
        kept.hideRecents = true
        kept.granted = mutableSetOf()
        kept.trustedCertificate = null
        val power = kept.power
        power.buttons.removeAll { it.does == "script" }
        val actions = listOf(power.volumeUp, power.volumeDown, power.swipeLeft, power.swipeRight, power.swipeDown)
        for (action in actions) {
            if (action.kind == "insertion") {
                action.kind = "builtin"
                action.value = "nothing"
            }
        }
        power.downloadFolder = ""
        power.downloadFolderName = ""
        power.shotFolder = ""
        power.shotFolderName = ""
        power.lastAddress = ""
        power.check = null
        return kept
    }

    fun save(context: Context, site: Site) {
        val kept = strip(site)
        file(context, kept.id).writeText(JSONObject().put("at", System.currentTimeMillis()).put("site", kept.toJson()).toString())
        val icon = SiteStore.iconFile(context, site.id)
        if (icon.isFile) icon.copyTo(iconFile(context, kept.id), overwrite = true)
    }

    fun all(context: Context): List<Pair<Site, Long>> =
        folder(context).listFiles { found -> found.extension == "json" }.orEmpty().mapNotNull { found ->
            try {
                val json = JSONObject(found.readText())
                Site.fromJson(json.getJSONObject("site"))?.let { it to json.optLong("at") }
            } catch (error: Exception) {
                null
            }
        }.sortedByDescending { it.second }

    fun forAddress(context: Context, url: String): Site? {
        val host = hostOf(url)
        if (host.isEmpty()) return null
        return all(context).map { it.first }.firstOrNull { it.host == host } ?: all(context).map { it.first }.firstOrNull { sameSite(it.host, host) }
    }

    fun icon(context: Context, id: String): Bitmap? = iconFile(context, id).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }

    fun drop(context: Context, id: String) {
        file(context, id).delete()
        iconFile(context, id).delete()
    }

    fun size(context: Context): Long = folder(context).listFiles().orEmpty().sumOf { it.length() }
}

fun Activity.removeSheet(site: Site, after: () -> Unit) {
    val sheet = Sheet(this)
    sheet.body.put(text("Remove ${site.label}?", 18f, T.ink, Fonts.sansBold))
    sheet.body.put(text("Either way its home screen icon stops working.", 13f, T.muted))
    val list = card()
    list.link(Glyphs.TRASH, "remove it and all its data", null, "the app, its settings, lock, scripts and keys, and everything it and the site stored", T.danger) {
        sheet.dismiss()
        MainActivity.removeSite(this, site, false)
        after()
    }
    list.link(Glyphs.SHIELD, "remove it, keep its settings", null, "erases logins, cookies, site storage, the lock and PIN, scripts and styles, access tokens, history and permissions. Keeps the name, icon, look, blocking, rules, hidden elements, controls and addresses for when you add it again.") {
        sheet.dismiss()
        MainActivity.removeSite(this, site, true)
        toast("${site.label} removed. Its settings are kept in Settings › kept settings.")
        after()
    }
    sheet.body.put(list.view)
    sheet.body.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
    sheet.show()
}

fun Activity.keptSheet(onChange: () -> Unit) {
    val sheet = Sheet(this)
    sheet.body.put(text("Kept settings", 18f, T.ink, Fonts.sansBold))
    sheet.body.put(text("From apps you removed but kept the settings of. Adding the same site again offers to use them. Nothing here can sign in or run scripts.", 13f, T.muted))
    val kept = Kept.all(this)
    val list = card()
    if (kept.isEmpty()) list.custom(text("Nothing kept.", 13f, T.muted).apply { setPadding(0, dp(14), 0, dp(14)) })
    for ((site, at) in kept) {
        list.link(Glyphs.GLOBE, site.label, null, site.host + " · removed " + ago(at)) {
            sheet.dismiss()
            val inner = Sheet(this)
            inner.body.put(text(site.label, 18f, T.ink, Fonts.sansBold))
            inner.body.put(text(site.url, 12f, T.muted, Fonts.mono))
            inner.body.put(primaryButton("Add it again", Glyphs.PLUS) {
                inner.dismiss()
                startActivity(android.content.Intent(this, AddActivity::class.java).putExtra(AddActivity.ADDRESS, site.url))
            }, MATCH_PARENT, dp(50))
            inner.body.put(secondaryButton("Forget these settings", Glyphs.TRASH, T.danger) {
                inner.dismiss()
                Kept.drop(this, site.id)
                onChange()
            }, MATCH_PARENT, dp(46))
            inner.show()
        }
    }
    sheet.body.put(list.view)
    sheet.show()
}
