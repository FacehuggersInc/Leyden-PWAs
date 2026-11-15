package org.bond.pwaapp

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class BuilderActivity : Screen() {
    private lateinit var site: Site
    private lateinit var item: Insertion
    private var fresh = false
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        if (found == null) {
            finish()
            return
        }
        site = found
        guarded = found
        val existing = findItem(found)
        if (existing != null) {
            item = existing
            intent.getStringArrayListExtra(TARGETS)?.let { picked ->
                item.auto = (item.auto ?: AutoSpec()).apply { targets = picked.toMutableList() }
                SiteStore.save(this, site)
            }
            return
        }
        fresh = true
        item = Insertion(kind = "auto")
        val targets = intent.getStringArrayListExtra(TARGETS).orEmpty()
        item.auto = AutoSpec(targets.toMutableList(), "click", "")
        item.triggers = mutableListOf(Trigger("appears", targets.firstOrNull().orEmpty()))
        item.waitMs = 10000
        item.pauseMs = 250
        item.atMost = 3
        item.path = intent.getStringExtra(PATH).orEmpty()
        item.name = intent.getStringExtra(NAME).orEmpty()
        site.insertions += item
        SiteStore.save(this, site)
    }

    override fun onBack(): Boolean {
        if (fresh && !saved) {
            site.insertions.removeAll { it.id == item.id }
            SiteStore.save(this, site)
        }
        return false
    }

    private fun save() {
        SiteStore.save(this, site)
        redraw()
    }

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: return
        item = findItem(site) ?: site.insertions.firstOrNull { it.id == item.id } ?: return
        val spec = item.auto ?: AutoSpec().also { item.auto = it }
        header("Auto target", "${site.label} · " + if (fresh) "new" else item.name, site = site)

        content.put(sectionTitle("NAME", Glyphs.TAG))
        val name = card()
        name.link(Glyphs.TAG, "name", item.name.ifEmpty { "untitled" }) {
            promptText("Name", item.name, "Skip intro") {
                item.name = it.trim()
                save()
            }
        }
        content.put(name.view)

        content.put(sectionTitle("TARGET", Glyphs.HAND, "tried top to bottom · drag to order"))
        val box = column()
        box.background = surface()
        box.setPadding(dp(14), dp(2), dp(14), dp(4))
        val list = DragList(this) { from, to ->
            spec.targets.add(to, spec.targets.removeAt(from))
            syncAppears()
            save()
        }
        if (spec.targets.isEmpty()) box.put(text("No target yet. Select one on the page, or type a selector.", 12f, T.muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        spec.targets.forEachIndexed { index, target ->
            val row = selectorRow(target, index == 0, onEdit = {
                editSelector("Target", target, true) { edited ->
                    if (edited.isEmpty()) spec.targets.removeAt(index) else spec.targets[index] = edited
                    syncAppears()
                    save()
                }
            }, onRemove = {
                spec.targets.removeAt(index)
                syncAppears()
                save()
            })
            val grip = gripIcon()
            row.addView(grip, 0, android.widget.LinearLayout.LayoutParams(dp(24), dp(40)))
            list.grip(row, grip)
            list.put(row)
        }
        box.put(list)
        content.put(box)
        content.put(note("If the first one finds nothing, the next is tried. Tap one to trim it, like matching a label by how it starts."))
        val pickers = line(8)
        pickers.put(secondaryButton("Select on the page", Glyphs.PICKER) {
            Shortcuts.select(this, site, item.id)
            saved = true
            finish()
        }, 0, dp(46), 1f)
        pickers.put(secondaryButton("Type one", Glyphs.PEN) {
            editSelector("New target", "", true) {
                if (it.isNotBlank()) {
                    spec.targets += it
                    syncAppears()
                    save()
                }
            }
        }, 0, dp(46), 1f)
        content.put(pickers)

        content.put(sectionTitle("DO", Glyphs.ARROW_RIGHT))
        val does = card()
        does.choice(Glyphs.ARROW_RIGHT, "action", null, listOf(Option("click"), Option("focus"), Option("type"), Option("check"), Option("scroll", "scroll to")), spec.action) {
            spec.action = it
            save()
        }
        if (spec.action == "type") {
            does.link(Glyphs.PEN, "text to type", spec.text.ifEmpty { "nothing yet" }) {
                promptText("Text to type", spec.text, "text") {
                    spec.text = it
                    save()
                }
            }
        }
        content.put(does.view)

        content.put(sectionTitle("STARTS AND RUNS", Glyphs.PLAY))
        val runs = card()
        runs.link(Glyphs.PLAY, "starts when", item.triggers.size.toString(), Describe.triggers(item, site)) { openRuns() }
        runs.link(Glyphs.EYE, "only if", null, (if (item.path.isEmpty()) "every page" else item.path) + if (item.exists.isNotEmpty()) " · if ${item.exists} exists" else "") { openRuns() }
        runs.link(Glyphs.SETTINGS, "runs", null, RunsActivity.summary(item)) { openRuns() }
        content.put(runs.view)

        content.put(sectionTitle("THE SCRIPT IT MAKES", Glyphs.CODE, "read only"))
        content.put(CodeView.readOnly(this, Runtime.autoCode(item), false))
        content.put(note("Triggers, conditions and limits live outside the script, so they work the same for every kind of insertion."))
        val tools = line(8)
        tools.put(secondaryButton("Edit as a script", Glyphs.CODE) {
            ask("Turn it into a script?", "It becomes a normal script of yours, which this builder can’t change anymore.", "Turn it into a script") {
                item.code = Runtime.autoCode(item)
                item.kind = "js"
                item.auto = null
                saved = true
                SiteStore.save(this, site)
                startActivity(Intent(this, CodeActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ItemActivity.ITEM, item.id))
                finish()
            }
        }, 0, dp(46), 1f)
        tools.put(secondaryButton("Test now", Glyphs.PLAY) {
            if (spec.targets.isEmpty()) {
                toast("Pick a target first")
                return@secondaryButton
            }
            saved = true
            if (item.name.isEmpty()) item.name = "Auto target"
            SiteStore.save(this, site)
            Shortcuts.test(this, site, item.id)
        }, 0, dp(46), 1f)
        content.put(tools)

        bottom.put(quietButton("Cancel", Glyphs.CLOSE) { if (!onBack()) finish() }, WRAP_CONTENT, dp(50))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Save", Glyphs.CHECK) {
            if (spec.targets.isEmpty()) {
                toast("Pick a target first")
                return@primaryButton
            }
            if (item.name.isEmpty()) item.name = "Auto target"
            saved = true
            SiteStore.save(this, site)
            finish()
        }, dp(140), dp(50))
        showBottom()
    }

    private fun syncAppears() {
        val first = item.auto?.targets?.firstOrNull().orEmpty()
        item.triggers.filter { it.kind == "appears" }.forEach { it.value = first }
    }

    private fun openRuns() {
        startActivity(Intent(this, RunsActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(ItemActivity.ITEM, item.id))
    }

    companion object {
        const val TARGETS = "targets"
        const val PATH = "path"
        const val NAME = "name"
    }
}

