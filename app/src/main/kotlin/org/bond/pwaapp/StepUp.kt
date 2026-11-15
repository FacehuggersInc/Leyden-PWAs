package org.bond.pwaapp

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class Step(val key: String, val name: String, val detail: String, val isOn: (Site) -> Boolean, val set: (Site, Boolean) -> Unit)

object StepUp {
    val STEPS = listOf(
        Step("model", "hide your phone model", "rarely breaks anything", { it.fp.model }, { site, on -> site.fp.model = on }),
        Step("header", "hide that it’s Leyden", "rarely breaks anything", { it.fp.requestedWith }, { site, on -> site.fp.requestedWith = on }),
        Step("hardware", "round hardware details", "rarely breaks anything", { it.fp.hardware }, { site, on -> site.fp.hardware = on }),
        Step("webrtc", "hide your local network address", "video calls keep working", { it.fp.webrtc != "allow" }, { site, on -> site.fp.webrtc = if (on) "local" else "allow" }),
        Step("standard", "block ads and trackers", "standard lists; can hide a needed widget now and then", { it.blocking != "off" }, { site, on ->
            site.blocking = if (on) "standard" else "off"
            if (on) site.lastLevel = "standard"
        }),
        Step("strict", "strict blocking", "also hides what ads leave behind; breaks a few more layouts", { it.blocking == "strict" }, { site, on ->
            site.blocking = if (on) "strict" else if (site.blocking == "strict") "standard" else site.blocking
            if (on) site.lastLevel = "strict"
        }),
        Step("canvas", "blur canvas", "can upset image editors and captchas", { it.fp.canvas }, { site, on -> site.fp.canvas = on }),
        Step("webgl", "blur WebGL", "can upset games and maps", { it.fp.webgl }, { site, on -> site.fp.webgl = on }),
        Step("audio", "blur audio", "may affect audio tools", { it.fp.audio }, { site, on -> site.fp.audio = on }),
        Step("screen", "common screen size", "rarely breaks anything", { it.fp.screen }, { site, on -> site.fp.screen = on }),
        Step("devices", "generic media devices and battery", "picking a specific camera or mic won’t work", { it.fp.devices }, { site, on ->
            site.fp.devices = on
            site.fp.battery = on
        }),
    )

    fun lowest(site: Site) {
        site.blocking = "off"
        site.fp.apply("off")
        site.stepUp = true
    }

    fun isLowest(site: Site): Boolean = site.blocking == "off" && !site.fp.active
}

class StepUpSheet(private val activity: WebAppActivity) {
    private val sheet = Sheet(activity)
    private val history = mutableListOf<Pair<Step, Boolean>>()

    fun show() {
        paint()
        sheet.show()
    }

    private fun apply(step: Step, on: Boolean, record: Boolean = true) {
        val site = activity.site
        if (record) history += step to step.isOn(site)
        step.set(site, on)
        site.fp.level = when {
            !site.fp.active -> "off"
            else -> "custom"
        }
        activity.saveSite()
        activity.applySettings()
        activity.installScripts()
        activity.webView.reload()
        paint()
    }

    private fun paint() {
        val site = activity.site
        sheet.clear()
        sheet.body.put(activity.text("Turn protections on, one at a time", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text("${site.label} is running with ad blocking and fingerprinting protection off. Turn them on one by one: each reloads the page, so you can see what still works. If something breaks, undo it.", 13f, T.muted))
        val list = activity.card()
        for (step in StepUp.STEPS) {
            list.switch(null, step.name, step.detail, step.isOn(site)) { on -> apply(step, on) }
        }
        sheet.body.put(list.view)
        val next = StepUp.STEPS.firstOrNull { !it.isOn(site) }
        val last = history.lastOrNull()
        val bar = activity.line(8)
        if (last != null) {
            bar.put(activity.secondaryButton("That broke it", Glyphs.HISTORY, T.danger) {
                history.removeAt(history.size - 1)
                apply(last.first, last.second, false)
            }, WRAP_CONTENT, activity.dp(48))
        }
        bar.put(activity.spacer(), 0, 1, 1f)
        if (next != null) {
            bar.put(activity.primaryButton("Next: " + next.name, Glyphs.ARROW_RIGHT) { apply(next, true) }, 0, activity.dp(48), 2f)
        }
        sheet.body.put(bar)
        if (last != null) sheet.body.put(activity.text("Last change: ${last.first.name}, ${if (last.second) "turned off" else "turned on"}.", 12f, T.faint))
        sheet.body.put(activity.quietButton("Done, keep these", Glyphs.CHECK, T.ink) {
            site.stepUp = false
            activity.saveSite()
            sheet.dismiss()
        }, MATCH_PARENT, activity.dp(44))
    }
}

object Relax {
    val KNOWN = listOf(
        "challenges.cloudflare.com", "*.hcaptcha.com", "hcaptcha.com", "www.google.com/recaptcha/*", "www.recaptcha.net/*",
        "*.arkoselabs.com", "*.funcaptcha.com", "geo.captcha-delivery.com", "*.captcha-delivery.com", "*.perimeterx.net", "*.px-cloud.net",
    )

    fun regex(pattern: String): Regex {
        val full = if (pattern.contains('/')) pattern else "$pattern/*"
        val escaped = full.split('*').joinToString(".*") { part -> part.replace(Regex("[.+?^\\$()|\\[\\]{}\\\\/]")) { "\\" + it.value } }
        return Regex("^$escaped$", RegexOption.IGNORE_CASE)
    }

    private fun target(url: String?): String {
        val host = hostOf(url)
        if (host.isEmpty()) return ""
        return host + pathOf(url.orEmpty()).ifEmpty { "/" }
    }

    fun glob(pattern: String, url: String?): Boolean {
        val goal = target(url)
        return goal.isNotEmpty() && regex(pattern.trim()).matches(goal)
    }

    fun matches(site: Site, url: String?): Boolean = site.relaxPages.any { glob(it, url) }

    fun known(url: String?): Boolean = KNOWN.any { glob(it, url) }

    fun skips(site: Site): List<String> = site.relaxPages + if (site.fp.relaxChecks) KNOWN else emptyList()

    fun pageRule(url: String): String = hostOf(url) + pathOf(url).ifEmpty { "/" } + "*"

    fun siteRule(url: String): String = hostOf(url) + "/*"
}

fun android.app.Activity.relaxPagesSheet(site: Site, current: String?, save: () -> Unit) {
    val sheet = Sheet(this)
    sheet.body.put(text("Pages with lower protections", 18f, T.ink, Fonts.sansBold))
    sheet.body.put(text("For security checks that refuse protected browsers. On these pages ad blocking and fingerprinting protection are off, and your real phone model is sent. Everywhere else in ${site.label} stays protected.", 13f, T.muted))
    val list = card()
    if (site.relaxPages.isEmpty()) list.custom(text("None yet.", 13f, T.muted).apply { setPadding(0, dp(14), 0, dp(14)) })
    for (rule in site.relaxPages.toList()) {
        list.action(Glyphs.SHIELD, rule, null, "remove") {
            site.relaxPages.remove(rule)
            save()
            sheet.dismiss()
            relaxPagesSheet(site, current, save)
        }
    }
    sheet.body.put(list.view)
    if (current != null && current.startsWith("http")) {
        val here = card()
        for ((label, rule) in listOf("this page" to Relax.pageRule(current), "all of ${hostOf(current)}" to Relax.siteRule(current))) {
            if (rule in site.relaxPages) continue
            here.link(Glyphs.PLUS, "add $label", null, rule) {
                site.relaxPages += rule
                save()
                sheet.dismiss()
                relaxPagesSheet(site, current, save)
            }
        }
        if (here.view.childCount > 0) sheet.body.put(here.view)
    }
    sheet.body.put(secondaryButton("Add by pattern", Glyphs.PEN) {
        sheet.dismiss()
        promptText("Page pattern", "", "login.example.com/verify*  or  *.example.com", true) { typed ->
            val rule = typed.trim().removePrefix("https://").removePrefix("http://")
            if (rule.isNotEmpty() && rule !in site.relaxPages) {
                site.relaxPages += rule
                save()
            }
            relaxPagesSheet(site, current, save)
        }
    }, android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
    sheet.show()
}
