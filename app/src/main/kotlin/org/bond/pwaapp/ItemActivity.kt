package org.bond.pwaapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

fun Screen.findItem(site: Site): Insertion? = site.insertions.firstOrNull { it.id == intent.getStringExtra(ItemActivity.ITEM) }

fun Screen.diffView(changes: List<Pair<Char, String>>, limit: Int = 60): View {
    val box = column()
    box.background = rounded(T.surface, dpf(12f))
    box.setPadding(dp(4), dp(8), dp(4), dp(8))
    for ((mark, line) in changes.take(limit)) {
        val row = text("$mark  $line", 11f, T.ink, Fonts.mono)
        row.setBackgroundColor(if (mark == '-') T.danger and 0x00FFFFFF or 0x1A000000 else T.good and 0x00FFFFFF or 0x1F000000)
        row.setPadding(dp(6), 0, dp(6), 0)
        box.put(row)
    }
    if (changes.size > limit) box.put(text("… ${changes.size - limit} more changed lines", 11f, T.muted).apply { setPadding(dp(6), dp(4), 0, 0) })
    if (changes.isEmpty()) box.put(text("No changes", 12f, T.muted).apply { setPadding(dp(6), 0, 0, 0) })
    return box
}

class ItemActivity : Screen() {
    private lateinit var site: Site
    private lateinit var item: Insertion

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        val chosen = found?.let { findItem(it) }
        if (found == null || chosen == null) {
            finish()
            return
        }
        site = found
        item = chosen
        guarded = found
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
        item = findItem(site) ?: run {
            finish()
            return
        }
        header(item.name.ifEmpty { "Untitled " + item.kindName }, item.kindName + " · " + site.label, site = site)
        val top = card()
        top.switch(Describe.icon(item), "on", if (site.power.insertionsOn) null else "insertions are off for ${site.label}", item.on) {
            item.on = it
            save()
        }
        top.link(Glyphs.TAG, "name", item.name.ifEmpty { "untitled" }) {
            promptText("Name", item.name, "Name") {
                item.name = it.trim()
                save()
            }
        }
        if (item.kind == "auto") {
            top.link(Glyphs.HAND, "auto target", item.auto?.action ?: "click", item.auto?.targets?.let { targets -> targets.firstOrNull()?.let { Selectors.describe(it) + if (targets.size > 1) " · +${targets.size - 1} backup" else "" } } ?: "no target yet") {
                startActivity(Intent(this, BuilderActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ITEM, item.id))
            }
        } else {
            top.link(Glyphs.CODE, if (item.fromOutside) "read the code" else "edit the code", "${item.code.lines().size} lines") {
                startActivity(Intent(this, CodeActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ITEM, item.id))
            }
        }
        content.put(top.view)

        AddonState.failure(this, site.id, item.id)?.let { failure ->
            content.put(callout(Glyphs.WARN, "Failed ${plural(failure.count, "time")} today", (if (failure.line > 0) "Line ${failure.line}: " else "") + failure.message, T.danger))
            content.put(secondaryButton("Clear the error", Glyphs.CHECK) {
                AddonState.clearFailure(this, site.id, item.id)
                redraw()
            }, MATCH_PARENT, dp(44))
        }

        content.put(sectionTitle("STARTS AND RUNS", Glyphs.PLAY))
        val runs = card()
        runs.link(Glyphs.PLAY, "starts when", item.triggers.size.toString(), Describe.triggers(item, site)) { openRuns() }
        val conditions = mutableListOf<String>()
        if (item.path.isNotEmpty()) conditions += item.path
        if (item.exists.isNotEmpty()) conditions += "if ${item.exists} exists"
        if (item.network.isNotEmpty()) conditions += Net.describe(item.network)
        if (item.timeFrom.isNotEmpty()) conditions += "${item.timeFrom}–${item.timeTo}"
        site.insertions.firstOrNull { it.id == item.after }?.let { conditions += "after ${it.name.ifEmpty { "untitled" }}" }
        runs.link(Glyphs.EYE, "only if", conditions.size.toString(), conditions.joinToString(" · ").ifEmpty { "always" }) { openRuns() }
        runs.link(Glyphs.SETTINGS, "runs", null, RunsActivity.summary(item)) { openRuns() }
        val used = Describe.usedBy(site, item)
        if (used.isNotEmpty()) runs.value(Glyphs.LINK, "used by", used.size.toString(), used.joinToString(", "))
        content.put(runs.view)

        val source = item.source
        if (source != null) {
            content.put(sectionTitle(if (source.detached) "YOUR COPY OF" else "FROM", Glyphs.DOWNLOAD))
            val from = card()
            from.value(Glyphs.LINK, if (source.type == "git") source.repo else source.url, if (item.version.isEmpty()) "" else "v" + item.version,
                if (source.type == "git") "${source.path} · ${source.ref}" + (if (source.commit.isNotEmpty()) " · " + source.commit.take(7) else "") else null)
            if (source.detached) {
                val changes = Diff.changes(source.upstream, item.code)
                from.value(Glyphs.PEN, "your edits", plural(changes.size, "line"), "updates stopped when you edited it")
                from.action(Glyphs.HISTORY, "go back to theirs", "drops your edits and turns updates on again", "go back") {
                    ask("Go back to theirs?", "Your edits are dropped.", "Go back") {
                        item.code = source.upstream
                        source.detached = false
                        save()
                    }
                }
                content.put(from.view)
                if (changes.isNotEmpty()) {
                    content.put(sectionTitle("CHANGES FROM THEIRS", Glyphs.HISTORY, plural(changes.size, "line")))
                    content.put(diffView(changes))
                }
            } else {
                if (source.type == "git") {
                    from.choice(Glyphs.HISTORY, "follow", null, listOf(Option("commit", "this commit"), Option("branch", "branch"), Option("tags", "new tags")), source.follow) {
                        source.follow = it
                        save()
                    }
                }
                from.choice(Glyphs.EYE, "when it changes", if (source.updates == "update") "new grants still ask first" else null, listOf(Option("ask"), Option("update"), Option("never")), source.updates) {
                    source.updates = it
                    save()
                }
                from.choice(Glyphs.CLOCK, "check", if (source.lastCheck > 0) "last checked " + ago(source.lastCheck) else null, listOf(Option("open", "on open"), Option("daily"), Option("weekly")), source.every) {
                    source.every = it
                    save()
                }
                from.action(Glyphs.RELOAD, "check now", null, "check") { checkNow() }
                content.put(from.view)
                if (source.pending.isNotEmpty()) {
                    val changes = Diff.changes(item.code, source.pending)
                    content.put(sectionTitle("UPDATE READY", Glyphs.DOWNLOAD, plural(changes.size, "changed line")))
                    content.put(diffView(changes))
                    val newHeader = Headers.read(source.pending)
                    val added = newHeader?.grants.orEmpty().filter { it !in item.grants }
                    if (added.isNotEmpty()) content.put(callout(Glyphs.WARN, "It asks for more", "New: " + added.joinToString(", "), T.danger))
                    val bar = line(8)
                    bar.put(secondaryButton("Skip it", Glyphs.CLOSE) {
                        source.pending = ""
                        save()
                    }, 0, dp(46), 1f)
                    bar.put(primaryButton("Update", Glyphs.CHECK) {
                        Updater.accept(item)
                        save()
                    }, 0, dp(46), 1f)
                    content.put(bar)
                }
            }
            val will = Headers.disclosures(item)
            if (will.isNotEmpty()) {
                content.put(sectionTitle("IT WILL", Glyphs.WARN, "from its header"))
                val list = card()
                for ((name, detail, kind) in will) list.value(Glyphs.WARN, name, kind, detail.ifEmpty { null })
                content.put(list.view)
            }
        }

        val kept = AddonState.valuesSize(this, site.id, item.id)
        if (kept > 0) {
            content.put(sectionTitle("KEPT BY IT", Glyphs.BOX))
            val data = card()
            data.action(Glyphs.BOX, "its own data", bytes(kept.toLong()) + " · " + plural(AddonState.keys(this, site.id, item.id).size, "value"), "clear") {
                AddonState.clearValues(this, site.id, item.id)
                redraw()
            }
            content.put(data.view)
        }

        val bar = line(8)
        bar.put(secondaryButton("Test it", Glyphs.PLAY) {
            Shortcuts.test(this, site, item.id)
        }, 0, dp(46), 1f)
        bar.put(secondaryButton("Delete", Glyphs.TRASH, T.danger) {
            ask("Delete “${item.name.ifEmpty { "untitled" }}”?", "Buttons and keys that run it stop doing anything.", "Delete", danger = true) {
                site.insertions.remove(item)
                site.insertions.forEach { if (it.after == item.id) it.after = "" }
                AddonState.clearValues(this, site.id, item.id)
                AddonState.clearFailure(this, site.id, item.id)
                SiteStore.save(this, site)
                finish()
            }
        }, 0, dp(46), 1f)
        content.put(bar)
    }

    private fun openRuns() {
        startActivity(Intent(this, RunsActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ITEM, item.id))
    }

    private fun checkNow() {
        toast("Checking…")
        val id = item.id
        Thread {
            val message = try {
                val changed = Updater.check(this, site.id, id, force = true)
                if (changed) "There’s an update" else "It’s up to date"
            } catch (error: Exception) {
                "Couldn’t check: ${error.message}"
            }
            runOnUiThread {
                toast(message)
                if (!isDestroyed) redraw()
            }
        }.start()
    }

    companion object {
        const val ITEM = "item"
    }
}

class RunsActivity : Screen() {
    private lateinit var site: Site
    private lateinit var item: Insertion

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        val chosen = found?.let { findItem(it) }
        if (found == null || chosen == null) {
            finish()
            return
        }
        site = found
        item = chosen
        guarded = found
    }

    private fun save() {
        SiteStore.save(this, site)
        redraw()
    }

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: return
        item = findItem(site) ?: return
        header("Starts and runs", item.name.ifEmpty { "untitled" } + " · " + site.label, site = site)

        content.put(sectionTitle("STARTS WHEN", Glyphs.PLAY, "drag to order · top first"))
        val box = column()
        box.background = surface()
        box.setPadding(dp(14), dp(2), dp(14), dp(4))
        val list = DragList(this) { from, to ->
            item.triggers.add(to, item.triggers.removeAt(from))
            save()
        }
        if (item.triggers.isEmpty()) box.put(text("Nothing starts it on its own. Add a trigger, or run it from a button, key or swipe.", 12f, T.muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        item.triggers.forEachIndexed { index, trigger ->
            val row = line(10)
            row.minimumHeight = dp(50)
            row.setPadding(0, dp(6), 0, dp(6))
            val grip = gripIcon()
            row.put(grip, dp(24), dp(40))
            list.grip(row, grip)
            row.put(mono((index + 1).toString(), 11f, T.faint, 0f), dp(14))
            val (name, detail) = trigger.describe(site)
            val words = column(2)
            words.put(text(name, 13f))
            if (detail.isNotEmpty()) words.put(text(detail, 11f, T.faint))
            row.put(words, 0, WRAP_CONTENT, 1f)
            row.background = ripple(null, 6f)
            row.setOnClickListener { triggerSheet(index) }
            val tone = when (trigger.group) {
                "page" -> T.info
                "you" -> T.good
                else -> 0xFF8A6A00.toInt()
            }
            row.put(tagView(trigger.group, tone, tone and 0x00FFFFFF or 0x1F000000), WRAP_CONTENT)
            row.put(squareKey(Glyphs.CLOSE, "Remove", 32) {
                item.triggers.remove(trigger)
                save()
            }, dp(32), dp(32))
            list.put(row)
        }
        box.put(list)
        content.put(box)
        content.put(note("When two fire together, the higher one runs and the others wait their turn, so it never runs twice at once. Nothing runs while ${site.label} is closed."))
        content.put(secondaryButton("Add a trigger", Glyphs.PLUS) { addTrigger() }, MATCH_PARENT, dp(46))

        content.put(sectionTitle("ONLY IF", Glyphs.EYE, "all of these"))
        val only = card()
        only.link(Glyphs.GLOBE, "page matches", item.path.ifEmpty { "any page" }) {
            promptText("Pages it runs on", item.path, "/watch/*  (empty: every page)", true) {
                item.path = it.trim()
                save()
            }
        }
        only.link(Glyphs.HAND, "an element exists", item.exists.ifEmpty { "no need" }, "otherwise it quietly does nothing") {
            editSelector("Only if this exists", item.exists, true) {
                item.exists = it
                save()
            }
        }
        only.link(Glyphs.WIFI, "network", Net.describe(item.network)) { networkSheet() }
        only.link(Glyphs.CLOCK, "time of day", if (item.timeFrom.isEmpty()) "any time" else "${item.timeFrom}–${item.timeTo}") {
            promptText("Between (like 08:00-18:00)", if (item.timeFrom.isEmpty()) "" else "${item.timeFrom}-${item.timeTo}", "08:00-18:00", true) { typed ->
                val parts = typed.split('-').map { it.trim() }
                if (parts.size == 2 && parts.all { Regex("^\\d{1,2}:\\d{2}$").matches(it) }) {
                    item.timeFrom = parts[0]
                    item.timeTo = parts[1]
                } else {
                    item.timeFrom = ""
                    item.timeTo = ""
                }
                save()
            }
        }
        val after = site.insertions.firstOrNull { it.id == item.after }
        only.link(Glyphs.SCRIPT, "after another insertion", after?.name?.ifEmpty { "untitled" } ?: "none", "waits until it has run") { afterSheet() }
        content.put(only.view)

        content.put(sectionTitle("RUNS", Glyphs.SETTINGS))
        val runs = card()
        runs.choice(Glyphs.CLOCK, "wait for it up to", "for the element, before giving up", listOf(Option("0"), Option("2000", "2 s"), Option("5000", "5 s"), Option("10000", "10 s"), Option("-1", "no limit")), item.waitMs.toString()) {
            item.waitMs = it.toInt()
            save()
        }
        runs.choice(Glyphs.CLOCK, "pause before acting", null, listOf(Option("0"), Option("250", "0.25 s"), Option("1000", "1 s")), item.pauseMs.toString()) {
            item.pauseMs = it.toInt()
            save()
        }
        runs.choice(Glyphs.HISTORY, "how often", null, listOf(Option("every", "every time"), Option("page", "once a page"), Option("visit", "once a visit"), Option("day", "once a day")), item.often) {
            item.often = it
            save()
        }
        runs.choice(Glyphs.HISTORY, "at most", "per page, so a loop can’t run away", listOf(Option("1"), Option("3"), Option("10"), Option("0", "∞")), item.atMost.toString()) {
            item.atMost = it.toInt()
            save()
        }
        runs.switch(Glyphs.CODE, "in frames too", "also inside embedded pages from ${site.label}", item.frames) {
            item.frames = it
            save()
        }
        runs.choice(Glyphs.WARN, "if it fails", null, listOf(Option("ignore"), Option("note", "show a note"), Option("off", "turn it off")), item.onFail) {
            item.onFail = it
            save()
        }
        content.put(runs.view)
    }

    private fun addTrigger(replace: Int = -1) {
        val sheet = Sheet(this)
        sheet.body.put(text(if (replace >= 0) "Change it to" else "Add a trigger", 18f, T.ink, Fonts.sansBold))
        val groups = listOf(
            "THE PAGE" to (if (item.kind == "css") listOf("before", "ready", "loaded", "address") else listOf("before", "ready", "loaded", "appears", "address", "every")),
            "YOU" to listOf("button", "key", "swipe", "quick"),
            "THE APP" to listOf("open", "unlock", "back", "network"),
        )
        for ((title, kinds) in groups) {
            sheet.body.put(sectionTitle(title))
            val list = card()
            for (kind in kinds) {
                val (name, detail) = Trigger(kind).describe(site)
                list.link(null, name, null, detail.ifEmpty { null }) {
                    sheet.dismiss()
                    val trigger = Trigger(kind)
                    when (kind) {
                        "appears" -> trigger.value = item.auto?.targets?.firstOrNull() ?: item.exists
                        "every" -> trigger.value = "30"
                        "key" -> trigger.value = "down"
                        "swipe" -> trigger.value = "left"
                    }
                    if (replace >= 0 && replace < item.triggers.size) item.triggers[replace] = trigger else item.triggers += trigger
                    SiteStore.save(this, site)
                    if (kind in setOf("appears", "every", "button", "key", "swipe")) editTrigger(trigger) else redraw()
                }
            }
            sheet.body.put(list.view)
        }
        sheet.show()
    }

    private fun triggerSheet(index: Int) {
        val trigger = item.triggers.getOrNull(index) ?: return
        val (name, detail) = trigger.describe(site)
        val sheet = Sheet(this)
        sheet.body.put(text(name, 18f, T.ink, Fonts.sansBold))
        if (detail.isNotEmpty()) sheet.body.put(text(detail, 13f, T.muted))
        val list = card()
        val setting = when (trigger.kind) {
            "appears" -> "the element"
            "every" -> "how often"
            "key" -> "which key"
            "swipe" -> "which swipe"
            "button" -> "which button"
            else -> null
        }
        if (setting != null) {
            list.link(Glyphs.PEN, setting, when (trigger.kind) {
                "appears" -> Selectors.describe(trigger.value).ifEmpty { "not set" }
                "every" -> trigger.value.ifEmpty { "30" } + " s"
                "button" -> site.power.buttons.firstOrNull { it.id == trigger.value }?.name ?: "not set"
                "swipe" -> Controls.swipeName(trigger.value)
                else -> trigger.value
            }) {
                sheet.dismiss()
                editTrigger(trigger)
            }
        }
        list.link(Glyphs.RELOAD, "change it to another trigger", null, "keeps its place in the order") {
            sheet.dismiss()
            addTrigger(index)
        }
        if (index > 0) list.link(Glyphs.CHEVRON_UP, "move up", null) {
            sheet.dismiss()
            item.triggers.add(index - 1, item.triggers.removeAt(index))
            save()
        }
        if (index < item.triggers.size - 1) list.link(Glyphs.CHEVRON_DOWN, "move down", null) {
            sheet.dismiss()
            item.triggers.add(index + 1, item.triggers.removeAt(index))
            save()
        }
        list.link(Glyphs.TRASH, "remove", color = T.danger) {
            sheet.dismiss()
            item.triggers.removeAt(index)
            save()
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun editTrigger(trigger: Trigger) {
        when (trigger.kind) {
            "appears" -> editSelector("Element to watch for", trigger.value, true) {
                trigger.value = it
                save()
            }
            "every" -> promptText("Every how many seconds", trigger.value, "30", true) {
                trigger.value = it.trim().toIntOrNull()?.coerceAtLeast(1)?.toString() ?: "30"
                save()
            }
            "key" -> pick("Which volume key", listOf("up", "down")) {
                trigger.value = it
                save()
            }
            "swipe" -> pick("Which swipe", listOf("left", "right", "down")) {
                trigger.value = it
                save()
            }
            "button" -> {
                if (site.power.buttons.isEmpty()) {
                    toast("Make a button first, in ${site.label}’s settings › controls")
                    return
                }
                pick("Which button", site.power.buttons.map { it.name.ifEmpty { "untitled" } }) { name ->
                    trigger.value = site.power.buttons.firstOrNull { it.name.ifEmpty { "untitled" } == name }?.id.orEmpty()
                    save()
                }
            }
        }
    }

    private fun pick(title: String, options: List<String>, onPick: (String) -> Unit) {
        val sheet = Sheet(this)
        sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
        val list = card()
        for (option in options) list.link(null, if (title.startsWith("Which swipe")) Controls.swipeName(option) else option) {
            sheet.dismiss()
            onPick(option)
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun networkSheet() {
        val options = mutableListOf("" to "any network", "tailscale" to "when Tailscale is on", "other" to "other networks")
        for (entry in site.power.networks) if (entry.kind == "wifi" && entry.ssid.isNotEmpty()) options.add(1, "wifi:${entry.ssid}" to "on Wi-Fi “${entry.ssid}”")
        val sheet = Sheet(this)
        sheet.body.put(text("Network", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text("Wi-Fi names come from ${site.label}’s addresses by network.", 13f, T.muted))
        val list = card()
        for ((key, name) in options) list.link(Glyphs.WIFI, name, if (key == item.network) "chosen" else null) {
            sheet.dismiss()
            item.network = key
            save()
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun afterSheet() {
        val sheet = Sheet(this)
        sheet.body.put(text("Run after", 18f, T.ink, Fonts.sansBold))
        val list = card()
        list.link(null, "nothing", null) {
            sheet.dismiss()
            item.after = ""
            save()
        }
        for (other in site.insertions) {
            if (other.id == item.id) continue
            list.link(Describe.icon(other), other.name.ifEmpty { "untitled" }, null, other.kindName) {
                sheet.dismiss()
                item.after = other.id
                save()
            }
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    companion object {
        fun summary(item: Insertion): String {
            val wait = when {
                item.waitMs < 0 -> "waits as long as it takes"
                item.waitMs == 0 -> "doesn’t wait"
                else -> "waits up to ${item.waitMs / 1000} s"
            }
            val often = when (item.often) {
                "page" -> "once a page"
                "visit" -> "once a visit"
                "day" -> "once a day"
                else -> "every time"
            }
            val most = if (item.atMost > 0) "at most ${item.atMost} a page" else "no limit"
            val fail = when (item.onFail) {
                "off" -> "turns off if it fails"
                "ignore" -> "ignores failures"
                else -> "notes failures"
            }
            return "$wait · $often · $most · $fail"
        }
    }
}

