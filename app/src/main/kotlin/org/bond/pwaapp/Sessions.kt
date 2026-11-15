package org.bond.pwaapp

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.webkit.ProfileStore
import java.util.UUID

object Sessions {
    const val PREFIX = "temp-"
    const val EXTRA = "session"

    fun name(kind: String): String = if (kind == "private") "Private" else "Peek"

    private fun make(context: Context, base: Site?, address: String, kind: String): Site {
        val site = base?.copy() ?: Site.fresh(Defaults.get(context)).also { made ->
            made.url = normalizeAddress(address)
            val host = hostOf(made.url)
            made.name = host.removePrefix("www.")
            made.scope = made.url.substringBefore("://") + "://" + host + "/"
        }
        site.session = kind
        site.sessionId = UUID.randomUUID().toString()
        site.remember = mutableSetOf()
        site.ownStorage = true
        site.privateMode = false
        site.clearOnClose = false
        site.onOpen = "start"
        site.lockMode = "off"
        site.pinHash = null
        site.granted = mutableSetOf()
        site.thirdPartyCookies = false
        site.insertions = mutableListOf()
        site.unblocked = mutableSetOf()
        site.group = ""
        val power = site.power
        power.buttons.removeAll { it.does == "script" }
        for (action in listOf(power.volumeUp, power.volumeDown, power.swipeLeft, power.swipeRight, power.swipeDown)) {
            if (action.kind == "insertion") {
                action.kind = "builtin"
                action.value = "nothing"
            }
        }
        power.networks = mutableListOf()
        power.downloadFolder = ""
        power.shotFolder = ""
        power.audio = false
        site.fp = site.fp.copyFresh()
        if (kind == "private") {
            site.fp = Fingerprint("strict")
            site.blocking = "strict"
            site.lastLevel = "strict"
            site.sessionSecure = true
            site.camera = "never"
            site.microphone = "never"
            site.location = "never"
        }
        return site
    }

    private fun usable(activity: Activity): Boolean {
        if (Storage.separate) return true
        activity.toast("Peek and Private need Android System WebView 110 or newer, for storage of their own")
        return false
    }

    fun open(activity: Activity, site: Site) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("pwaapp://temp/${site.sessionId}"), activity, WebAppActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA, site.toJson().toString())
        activity.startActivity(intent)
    }

    fun start(activity: Activity, kind: String, base: Site?, address: String? = null) {
        if (!usable(activity)) return
        if (base == null && address.isNullOrBlank()) {
            ask(activity, kind)
            return
        }
        val begin = {
            val site = make(activity, base, address.orEmpty(), kind)
            if (kind == "peek") settings(activity, site) else open(activity, site)
        }
        if (base != null && base.locks) Gate.open(activity, base) { begin() } else begin()
    }

    private fun ask(activity: Activity, kind: String) {
        val sheet = Sheet(activity)
        sheet.body.put(activity.text(if (kind == "private") "Open privately" else "Peek at a site", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text(about(kind), 13f, T.muted))
        val field = activity.field("", "example.com", true, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        sheet.body.put(field, MATCH_PARENT, activity.dp(50))
        sheet.body.put(activity.primaryButton(if (kind == "private") "Open privately" else "Next · privacy", Glyphs.ARROW_RIGHT) {
            val typed = field.text.toString().trim()
            if (typed.isEmpty()) {
                activity.toast("Type the site’s address")
                return@primaryButton
            }
            sheet.dismiss()
            start(activity, kind, null, typed)
        }, MATCH_PARENT, activity.dp(50))
        sheet.show()
        field.requestFocus()
        sheet.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun about(kind: String): String = if (kind == "private")
        "Strict blocking, nothing remembered, and storage of its own that’s erased when you close it. It never touches your apps’ logins or data."
    else
        "Opens with no logins, scripts or saved data attached. You choose the privacy settings first. Its storage is erased when you close it."

    private fun settings(activity: Activity, site: Site) {
        val sheet = Sheet(activity)
        sheet.body.put(activity.text("Peek at ${site.label}", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text(about("peek"), 13f, T.muted))
        val list = activity.card()
        lateinit var paint: () -> Unit
        paint = {
            list.view.removeAllViews()
            val fresh = activity.card()
            fresh.choice(Glyphs.SHIELD, "blocking", null, listOf(Option("off"), Option("standard", "std"), Option("strict")), site.blocking) {
                site.blocking = it
                if (it != "off") site.lastLevel = it
                paint()
            }
            fresh.switch(Glyphs.COOKIE, "cookies from other sites", "off keeps other sites from following you", site.thirdPartyCookies) { site.thirdPartyCookies = it }
            fresh.choice(Glyphs.CAMERA, "camera", null, listOf(Option("ask"), Option("never")), site.camera) {
                site.camera = it
                paint()
            }
            fresh.choice(Glyphs.MIC, "microphone", null, listOf(Option("ask"), Option("never")), site.microphone) {
                site.microphone = it
                paint()
            }
            fresh.choice(Glyphs.PIN, "location", null, listOf(Option("ask"), Option("never")), site.location) {
                site.location = it
                paint()
            }
            fresh.choice(Glyphs.SHIELD, "fingerprinting", "a fresh pattern every peek", listOf(Option("off"), Option("standard", "std"), Option("strict")), site.fp.level.takeIf { it != "custom" } ?: "standard") {
                site.fp.apply(it)
                paint()
            }
            fresh.switch(Glyphs.HIDDEN, "block screenshots", "and hide it in recents", site.sessionSecure) { site.sessionSecure = it }
            fresh.switch(Glyphs.WARN, "warn before risky sites", null, site.warnRisky ?: true) { site.warnRisky = it }
            for (index in 0 until fresh.view.childCount) {
                val child = fresh.view.getChildAt(0)
                fresh.view.removeView(child)
                list.view.addView(child)
            }
        }
        paint()
        sheet.body.put(list.view)
        val bar = activity.line(8)
        bar.put(activity.quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, activity.dp(48))
        bar.put(activity.spacer(), 0, 1, 1f)
        bar.put(activity.primaryButton("Peek", Glyphs.EYE) {
            sheet.dismiss()
            open(activity, site)
        }, activity.dp(150), activity.dp(48))
        sheet.body.put(bar)
        sheet.show()
    }

    fun cleanUp(context: Context) {
        if (!Storage.separate) return
        val running = context.getSystemService(ActivityManager::class.java)?.appTasks?.mapNotNull { task ->
            try {
                task.taskInfo?.baseIntent?.data?.takeIf { it.host == "temp" }?.lastPathSegment
            } catch (error: IllegalArgumentException) {
                null
            }
        }?.toSet().orEmpty()
        val store = try {
            ProfileStore.getInstance()
        } catch (error: Throwable) {
            return
        }
        val adopted = SiteStore.all(context).map { it.storageName }.toSet()
        for (name in store.allProfileNames) {
            if (!name.startsWith(PREFIX) || name.removePrefix(PREFIX) in running || name in adopted) continue
            try {
                store.deleteProfile(name)
            } catch (error: RuntimeException) {
                continue
            }
        }
    }

    fun forget(name: String?) {
        if (name == null || !name.startsWith(PREFIX)) return
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                ProfileStore.getInstance().deleteProfile(name)
            } catch (error: RuntimeException) {
                return@postDelayed
            }
        }, 800)
    }

    fun adopt(activity: WebAppActivity, name: String, keep: Boolean) {
        val from = activity.site
        val base = SiteStore.get(activity, from.id)
        val json = from.toJson().put("id", UUID.randomUUID().toString())
        val app = Site.fromJson(json) ?: return
        app.name = name
        app.session = ""
        app.sessionId = ""
        app.created = System.currentTimeMillis()
        app.remember = Defaults.get(activity).remember.toMutableSet()
        app.onOpen = "resume"
        app.ownStorage = true
        app.storageName = if (keep) Storage.profileName(from).orEmpty() else ""
        app.power.copyOf = base?.id.orEmpty()
        val icon = base?.let { SiteStore.icon(activity, it.id) } ?: LauncherIcons.make(null, false, app.background, app.label)
        SiteStore.saveIcon(activity, app.id, icon)
        SiteStore.save(activity, app)
        activity.adopted = keep
        activity.toast("${app.label} is now an app" + if (keep) ", still signed in" else "")
        Shortcuts.open(activity, app, activity.pageUrl.takeIf { it.startsWith("http") })
        activity.finishAndRemoveTask()
    }
}
