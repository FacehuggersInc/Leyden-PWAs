package org.bond.pwaapp

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

object Describe {
    fun triggers(item: Insertion, site: Site): String =
        item.triggers.joinToString(", ") { it.describe(site).first }.ifEmpty { "only when something runs it" }

    fun where(item: Insertion): String = if (item.path.isEmpty()) "every page" else item.path

    fun usedBy(site: Site, item: Insertion): List<String> {
        val out = mutableListOf<String>()
        for (button in site.power.buttons) if (button.does == "script" && button.value == item.id) out += "${button.name} button"
        val power = site.power
        listOf("volume up" to power.volumeUp, "volume down" to power.volumeDown, "swipe left" to power.swipeLeft, "swipe right" to power.swipeRight, "two-finger swipe" to power.swipeDown)
            .forEach { (name, action) -> if (action.kind == "insertion" && action.value == item.id) out += name }
        for (trigger in item.triggers) {
            when (trigger.kind) {
                "button" -> site.power.buttons.firstOrNull { it.id == trigger.value }?.let { out += "${it.name} button" }
                "key" -> out += "volume ${trigger.value}"
                "swipe" -> out += "swipe ${Controls.swipeName(trigger.value)}"
                "quick" -> out += "quick settings"
            }
        }
        return out.distinct()
    }

    fun phaseName(phase: String): String = when (phase) {
        "before" -> "BEFORE THE PAGE LOADS"
        "ready" -> "WHEN THE PAGE IS READY"
        "loaded" -> "WHEN IT’S FULLY LOADED"
        else -> "ONLY WHEN SOMETHING RUNS IT"
    }

    fun icon(item: Insertion): String = when {
        item.kind == "auto" -> Glyphs.HAND
        item.source != null && !item.source!!.detached -> Glyphs.DOWNLOAD
        item.kind == "css" -> Glyphs.CODE
        else -> Glyphs.SCRIPT
    }
}

class InsertionsActivity : Screen() {
    private lateinit var site: Site
    private var view = "order"

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        if (found == null) {
            finish()
            return
        }
        site = found
        guarded = found
        if (intent.getStringExtra(TAB) == "hidden") window.decorView.post { hiddenSheet() }
    }

    private fun save() {
        SiteStore.save(this, site)
        redraw()
    }

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: run {
            finish()
            return
        }
        val power = site.power
        header("Insertions", "${site.label} · ${site.host}", site = site)
        val master = card()
        master.switch(Glyphs.CODE, "insertions for ${site.label}", "all of them, built-in and yours. Off: the site runs untouched.", power.insertionsOn) {
            power.insertionsOn = it
            save()
        }
        content.put(master.view)
        val failing = site.insertions.count { AddonState.failure(this, site.id, it.id) != null }
        content.put(callout(Glyphs.WARN, if (failing > 0) "${plural(failing, "insertion")} failed today" else "Something on a page stopped working?",
            "Turn insertions off here, or open the page once without them from quick settings. Ones that fail are marked below with the error.", if (failing > 0) T.danger else T.info))

        content.put(sectionTitle("BUILT INTO PWA APP", Glyphs.GRID, "you can switch these too"))
        val built = card()
        built.switch(Glyphs.SHIELD, "filter list styles", "strict blocking hides leftovers with these", power.builtinLists, power.insertionsOn) {
            power.builtinLists = it
            save()
        }
        built.switchWith(Glyphs.HIDDEN, "hidden elements", "${site.hidden.size} you picked", power.builtinHidden, "list", power.insertionsOn, onAction = { hiddenSheet() }) {
            power.builtinHidden = it
            save()
        }
        built.switch(Glyphs.EYE, "theme color reader", "reads the bar color from the page", power.builtinTheme, power.insertionsOn) {
            power.builtinTheme = it
            save()
        }
        built.switch(Glyphs.LOCK, "password field helper", "notices when a login field is focused, on any site, to offer your password manager. It reads no text.", power.builtinLogin, power.insertionsOn) {
            power.builtinLogin = it
            save()
        }
        built.switch(Glyphs.POPUP, "video pop-out", "lets a site’s own pop-out or picture-in-picture button work, on any site, by filling the window with that video", power.builtinPip, power.insertionsOn) {
            power.builtinPip = it
            save()
        }
        built.link(Glyphs.SHIELD, "fingerprint protection", site.fp.summary(), "runs on every site in ${site.label}, before the page’s own scripts, to hide or blur device details. Your phone model is hidden even with insertions off.") {
            startActivity(Intent(this, FingerprintActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id))
        }
        built.value(Glyphs.PICKER, "select", "on demand", "for hiding and targeting, only while you use it")
        content.put(built.view)

        val head = line(8)
        head.setPadding(dp(4), dp(4), dp(4), 0)
        head.put(glyph(Glyphs.LIST, T.muted, 12, 2f), dp(12), dp(12))
        head.put(mono("YOURS · LOAD ORDER"), 0, WRAP_CONTENT, 1f)
        head.put(chips(listOf(Option("order"), Option("kind", "by kind")), view) {
            view = it
            redraw()
        }, WRAP_CONTENT)
        content.put(head)

        if (site.insertions.isEmpty()) {
            content.put(empty("Nothing of yours yet. Add a style or script, an auto target that clicks or fills something for you, or one from the web or a repository."))
        } else if (view == "kind") {
            byKind()
        } else {
            ordered()
        }
        content.put(note("Order matters inside each step: a style that styles what a script adds belongs below it. Something that needs another to run first waits for it, and is marked."))
        val adds = line(8)
        adds.put(secondaryButton("auto target", Glyphs.HAND) { startActivity(Intent(this, BuilderActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id)) }, 0, dp(46), 1f)
        adds.put(secondaryButton("new", Glyphs.PLUS) { newSheet() }, 0, dp(46), 1f)
        adds.put(secondaryButton("from…", Glyphs.DOWNLOAD) { fromSheet() }, 0, dp(46), 1f)
        content.put(adds)
    }

    private fun ordered() {
        val box = column()
        box.background = surface()
        box.setPadding(dp(14), dp(4), dp(14), dp(8))
        var number = 1
        for (phase in listOf("before", "ready", "loaded", "manual")) {
            val group = site.insertions.filter { it.phase == phase }
            if (group.isEmpty()) continue
            val label = line(8)
            label.setPadding(0, dp(10), 0, dp(4))
            label.put(mono(Describe.phaseName(phase), 9f), WRAP_CONTENT)
            val rule = View(this)
            rule.setBackgroundColor(T.rule)
            label.put(rule, 0, dp(1), 1f)
            box.put(label)
            val list = DragList(this) { from, to ->
                val moved = group.toMutableList()
                moved.add(to, moved.removeAt(from))
                reorder(phase, moved)
            }
            for (item in group) {
                val row = row(item, number++, list)
                list.put(row)
            }
            box.put(list)
        }
        content.put(box)
    }

    private fun reorder(phase: String, moved: List<Insertion>) {
        val out = mutableListOf<Insertion>()
        for (each in listOf("before", "ready", "loaded", "manual")) {
            out += if (each == phase) moved else site.insertions.filter { it.phase == each }
        }
        site.insertions = out
        save()
    }

    private fun byKind() {
        for ((title, kinds) in listOf("STYLES" to setOf("css"), "SCRIPTS" to setOf("js"), "AUTO TARGETS" to setOf("auto"))) {
            val group = site.insertions.filter { it.kind in kinds }
            if (group.isEmpty()) continue
            content.put(sectionTitle(title))
            val box = column()
            box.background = surface()
            box.setPadding(dp(14), dp(4), dp(14), dp(8))
            for (item in group) box.put(row(item, site.insertions.indexOf(item) + 1, null))
            content.put(box)
        }
    }

    private fun row(item: Insertion, number: Int, list: DragList?): View {
        val row = line(10)
        row.minimumHeight = dp(52)
        row.setPadding(0, dp(8), 0, dp(8))
        val grip = gripIcon()
        if (list != null) {
            row.put(grip, dp(24), dp(40))
            list.grip(row, grip)
        }
        row.put(mono(number.toString(), 11f, T.faint, 0f).apply { gravity = Gravity.END }, dp(20))
        row.put(glyph(Describe.icon(item), T.body), dp(17), dp(17))
        val words = column(2)
        words.put(text(item.name.ifEmpty { "Untitled " + item.kindName }, 13f, if (item.on) T.ink else T.faint))
        val detail = item.kindName + (item.source?.takeIf { !it.detached }?.let { " · " + it.label } ?: "") + " · " + Describe.where(item)
        words.put(text(detail, 11f, T.faint).oneLine())
        val tags = Flow(this)
        val needs = site.insertions.firstOrNull { it.id == item.after }
        if (needs != null) {
            val below = site.insertions.indexOf(needs) > site.insertions.indexOf(item)
            tags.addView(tagView("needs: " + needs.name.ifEmpty { "untitled" } + if (below) " (below it; it waits)" else "", 0xFF8A6A00.toInt(), 0x29C9A24A))
        }
        val used = Describe.usedBy(site, item)
        if (used.isNotEmpty()) tags.addView(tagView("used by: " + used.joinToString(", "), T.good, T.good and 0x00FFFFFF or 0x1A000000))
        if (!item.runsOnItsOwn) tags.addView(tagView("only when run"))
        if (item.source?.pending?.isNotEmpty() == true) tags.addView(tagView("update ready", T.info, T.info and 0x00FFFFFF or 0x1F000000))
        if (tags.childCount > 0) words.put(tags).margins(0, dp(4), 0, 0)
        AddonState.failure(this, site.id, item.id)?.let { failure ->
            words.put(text("failed ${plural(failure.count, "time")} today" + (if (failure.line > 0) ": line ${failure.line}, " else ": ") + "“${failure.message}”", 11f, T.danger))
        }
        row.put(words, 0, WRAP_CONTENT, 1f)
        words.isClickable = true
        words.setOnClickListener { open(item) }
        val toggle = Switch(this)
        toggle.checked = item.on
        toggle.isEnabled = site.power.insertionsOn
        toggle.contentDescription = item.name
        toggle.onChange = {
            item.on = it
            save()
        }
        row.put(toggle, WRAP_CONTENT)
        return row
    }

    private fun open(item: Insertion) {
        startActivity(Intent(this, ItemActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ItemActivity.ITEM, item.id))
    }

    private fun newSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("New of your own", 18f, T.ink, Fonts.sansBold))
        val list = card()
        list.link(Glyphs.CODE, "a style", null, "CSS that changes how ${site.label} looks") {
            sheet.dismiss()
            create("css")
        }
        list.link(Glyphs.SCRIPT, "a script", null, "JavaScript that runs on ${site.label}’s pages") {
            sheet.dismiss()
            create("js")
        }
        list.link(Glyphs.HAND, "an auto target", null, "clicks, focuses or fills something for you") {
            sheet.dismiss()
            startActivity(Intent(this, BuilderActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id))
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun create(kind: String) {
        val item = Insertion(kind = kind)
        item.triggers = mutableListOf(Trigger(if (kind == "css") "before" else "ready"))
        site.insertions += item
        SiteStore.save(this, site)
        startActivity(Intent(this, CodeActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ItemActivity.ITEM, item.id).putExtra(CodeActivity.MODE, "new"))
    }

    private fun fromSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("Add from…", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("You’ll see the whole file and what it can do before anything is added.", 13f, T.muted))
        val list = card()
        list.link(Glyphs.LINK, "an address", null, "a .user.js, .user.css, .js or .css file on the web") {
            sheet.dismiss()
            startActivity(Intent(this, SourceActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(SourceActivity.MODE, "web"))
        }
        list.link(Glyphs.FOLDER, "a repository", null, "GitHub, GitLab, Gitea or Forgejo, including your own server") {
            sheet.dismiss()
            startActivity(Intent(this, SourceActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(SourceActivity.MODE, "git"))
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun hiddenSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("Hidden elements", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("Pick more in ${site.label}: quick settings › select.", 13f, T.muted))
        val list = card()
        if (site.hidden.isEmpty()) list.custom(text("Nothing hidden yet.", 13f, T.muted).apply { setPadding(0, dp(14), 0, dp(14)) })
        for (item in site.hidden.toList()) {
            list.link(Glyphs.HIDDEN, item.selector, null, if (item.path.isEmpty()) "every page" else "only on ${item.path}") {
                sheet.dismiss()
                hiddenItem(item)
            }
        }
        sheet.body.put(list.view)
        sheet.body.put(secondaryButton("Add by selector", Glyphs.PLUS) {
            sheet.dismiss()
            editSelector("Hide elements matching", "", false) { typed ->
                if (typed.isNotBlank()) {
                    site.hidden += Hidden(typed)
                    save()
                }
                hiddenSheet()
            }
        }, MATCH_PARENT, dp(46))
        sheet.show()
    }

    private fun hiddenItem(item: Hidden) {
        val sheet = Sheet(this)
        sheet.body.put(text("Hidden element", 18f, T.ink, Fonts.sansBold))
        val list = card()
        list.link(Glyphs.PICKER, "what it hides", item.selector) {
            sheet.dismiss()
            editSelector("What to hide", item.selector, false) { edited ->
                if (edited.isNotBlank()) item.selector = edited
                save()
                hiddenItem(item)
            }
        }
        list.link(Glyphs.GLOBE, "pages", item.path.ifEmpty { "every page" }) {
            sheet.dismiss()
            promptText("Pages it hides on", item.path, "/watch/*  (empty: every page)", true) {
                item.path = it.trim()
                save()
                hiddenItem(item)
            }
        }
        list.link(Glyphs.TRASH, "stop hiding it", color = T.danger) {
            sheet.dismiss()
            site.hidden.remove(item)
            save()
            hiddenSheet()
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    companion object {
        const val TAB = "tab"
    }
}

fun android.content.Context.callout(icon: String, title: String, detail: String, tone: Int): View {
    val box = line(12)
    box.gravity = Gravity.TOP
    box.background = rounded(tone and 0x00FFFFFF or 0x1A000000, dpf(16f), dp(1), tone and 0x00FFFFFF or 0x33000000)
    box.setPadding(dp(14), dp(12), dp(14), dp(12))
    box.put(glyph(icon, tone, 18, 2f), dp(18), dp(18))
    val words = column(3)
    words.put(text(title, 13f, T.ink, Fonts.sansBold))
    words.put(text(detail, 12f, T.body))
    box.put(words, 0, WRAP_CONTENT, 1f)
    return box
}

