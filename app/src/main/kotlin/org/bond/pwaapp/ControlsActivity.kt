package org.bond.pwaapp

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class ControlsActivity : Screen() {
    private lateinit var site: Site

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        if (found == null) {
            finish()
            return
        }
        site = found
        guarded = found
    }

    private fun save() {
        SiteStore.save(this, site)
        redraw()
    }

    override fun render() {
        if (!this::site.isInitialized) return
        site = SiteStore.get(this, site.id) ?: return
        val power = site.power
        header("Controls", "${site.label} · ${site.host}", site = site)

        content.put(sectionTitle("VOLUME KEYS", Glyphs.VOLUME, "only while ${site.label} is open"))
        val keys = card()
        keys.switch(Glyphs.VOLUME, "use volume keys here", "hold a key for normal volume", power.keysOn) {
            power.keysOn = it
            save()
        }
        keys.link(Glyphs.VOLUME, "volume up", power.volumeUp.describe(site)) { pickAction("Volume up", power.volumeUp) }
        keys.link(Glyphs.VOLUME, "volume down", power.volumeDown.describe(site)) { pickAction("Volume down", power.volumeDown) }
        content.put(keys.view)

        content.put(sectionTitle("SWIPES ACROSS THE PAGE", Glyphs.SWIPE, "not from the edges"))
        val swipes = card()
        swipes.link(Glyphs.SWIPE, "swipe left", power.swipeLeft.describe(site)) { pickAction("Swipe left", power.swipeLeft) }
        swipes.link(Glyphs.SWIPE, "swipe right", power.swipeRight.describe(site)) { pickAction("Swipe right", power.swipeRight) }
        swipes.link(Glyphs.SWIPE, "two-finger swipe down", power.swipeDown.describe(site)) { pickAction("Two-finger swipe down", power.swipeDown) }
        content.put(swipes.view)

        content.put(sectionTitle("YOUR BUTTONS", Glyphs.HAND, "floating, toolbar, or only on keys"))
        val buttons = card()
        if (power.buttons.isEmpty()) buttons.custom(text("None yet. A button can open a page, click something you select, or run one of your scripts.", 12f, T.muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        for (button in power.buttons) {
            val does = when (button.does) {
                "open" -> "opens ${button.value.ifEmpty { "a page" }}"
                "script" -> "runs " + (site.insertions.firstOrNull { it.id == button.value }?.name?.ifEmpty { "a script" } ?: "a script")
                else -> "clicks " + button.value.ifEmpty { "something" }
            }
            buttons.action(Glyphs.HAND, button.name.ifEmpty { "untitled" }, "$does · ${button.show}", "edit") { editButton(button, false) }
        }
        content.put(buttons.view)
        content.put(secondaryButton("New button", Glyphs.PLUS) { editButton(Btn(), true) }, MATCH_PARENT, dp(46))
        content.put(note("Buttons, keys and swipes only act on ${site.label}’s own pages. A script runs only when you press it, unless its own triggers say otherwise."))
    }

    private fun pickAction(title: String, action: Action) {
        val sheet = Sheet(this)
        sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
        sheet.body.put(sectionTitle("BUILT-IN", Glyphs.GRID))
        val built = card()
        for (key in Controls.BUILTINS) {
            built.link(null, Controls.builtinName(key), if (action.kind == "builtin" && action.value == key) "chosen" else null) {
                action.kind = "builtin"
                action.value = key
                sheet.dismiss()
                save()
            }
        }
        sheet.body.put(built.view)
        if (site.power.buttons.isNotEmpty()) {
            sheet.body.put(sectionTitle("YOUR BUTTONS", Glyphs.HAND))
            val list = card()
            for (button in site.power.buttons) list.link(Glyphs.HAND, button.name.ifEmpty { "untitled" }) {
                action.kind = "button"
                action.value = button.id
                sheet.dismiss()
                save()
            }
            sheet.body.put(list.view)
        }
        val runnable = site.insertions.filter { it.kind != "css" }
        if (runnable.isNotEmpty()) {
            sheet.body.put(sectionTitle("YOUR SCRIPTS AND AUTO TARGETS", Glyphs.SCRIPT))
            val list = card()
            for (item in runnable) list.link(Describe.icon(item), item.name.ifEmpty { "untitled" }, null, item.kindName) {
                action.kind = "insertion"
                action.value = item.id
                sheet.dismiss()
                save()
            }
            sheet.body.put(list.view)
        }
        sheet.show()
    }

    private fun editButton(button: Btn, isNew: Boolean) {
        val sheet = Sheet(this)
        sheet.body.put(text(if (isNew) "New button" else "Edit “${button.name}”", 18f, T.ink, Fonts.sansBold))
        val name = field(button.name, "Name, like “Skip intro”")
        sheet.body.put(name)
        var does = button.does
        var show = button.show
        val value = field(button.value, "", true)
        fun hint() {
            value.hint = when (does) {
                "open" -> "/continue or a full address"
                "script" -> "pick a script below"
                else -> "button.skip-intro, or text:button|Skip intro"
            }
        }
        hint()
        val options = card()
        lateinit var paint: () -> Unit
        paint = {
            options.view.removeAllViews()
            val fresh = card()
            fresh.choice(Glyphs.HAND, "does", null, listOf(Option("open", "open page"), Option("click"), Option("script")), does) {
                does = it
                hint()
                paint()
            }
            fresh.choice(Glyphs.GRID, "shows as", null, listOf(Option("floating"), Option("toolbar"), Option("hidden", "only on keys")), show) {
                show = it
                paint()
            }
            if (does == "click") {
                val typed = value.text.toString().trim()
                fresh.link(Glyphs.PICKER, "what it clicks", if (typed.isEmpty()) "not set" else Selectors.describe(typed), "trim it, or match it by its words") {
                    editSelector("What it clicks", value.text.toString().trim(), true) {
                        value.setText(it)
                        paint()
                    }
                }
            }
            if (does == "script") {
                val runnable = site.insertions.filter { it.kind != "css" }
                for (item in runnable) fresh.link(Describe.icon(item), item.name.ifEmpty { "untitled" }, if (value.text.toString() == item.id) "chosen" else null) {
                    value.setText(item.id)
                    paint()
                }
                if (runnable.isEmpty()) fresh.custom(text("No scripts yet. Make one in insertions.", 12f, T.muted).apply { setPadding(0, dp(10), 0, dp(10)) })
            }
            options.view.addView(fresh.view)
        }
        paint()
        sheet.body.put(value)
        sheet.body.put(options.view)
        if (does == "click") sheet.body.put(text("Tip: in ${site.label}, quick settings › select › target gives you a stable way to find it.", 11f, T.faint))
        val bar = line(8)
        if (!isNew) {
            bar.put(quietButton("Delete", Glyphs.TRASH, T.danger) {
                site.power.buttons.remove(button)
                sheet.dismiss()
                save()
            }, WRAP_CONTENT, dp(48))
        } else {
            bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
        }
        bar.put(spacer(), 0, 1, 1f)
        bar.put(primaryButton("Save", Glyphs.CHECK) {
            button.name = name.text.toString().trim().ifEmpty { "Button" }
            button.does = does
            button.show = show
            button.value = value.text.toString().trim()
            if (isNew) site.power.buttons += button
            sheet.dismiss()
            save()
        }, dp(140), dp(48))
        sheet.body.put(bar)
        sheet.show()
    }
}
