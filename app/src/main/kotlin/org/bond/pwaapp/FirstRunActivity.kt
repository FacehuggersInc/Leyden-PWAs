package org.bond.pwaapp

import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class FirstRunActivity : Screen() {
    private var step = 1
    private val defaults by lazy { Defaults.get(this) }

    override fun onBack(): Boolean {
        if (step == 2) {
            step = 1
            redrawTop()
            return true
        }
        return false
    }

    private fun steps(now: Int) {
        val row = line(6)
        for ((index, name) in listOf("what apps remember", "blocking").withIndex()) {
            val part = column(6)
            val bar = View(this)
            bar.background = rounded(if (index + 1 <= now) T.accent else T.switchOff, dpf(2f))
            part.put(bar, MATCH_PARENT, dp(4))
            part.put(mono("${index + 1} · ${name.uppercase()}", 9f, if (index + 1 == now) T.ink else T.faint))
            row.put(part, 0, WRAP_CONTENT, 1f)
        }
        content.put(row)
    }

    private fun choiceCard(title: String, detail: String, icon: String, picked: Boolean, onPick: () -> Unit): View {
        val box = line(12)
        val r = dpf(18f)
        val layers = LayerDrawable(arrayOf(rounded(if (picked) T.accent else T.lift, r), rounded(T.surface, r)))
        if (picked) layers.setLayerInset(1, dp(2), dp(2), dp(2), dp(2)) else layers.setLayerInset(1, 0, 0, 0, dp(1))
        box.background = ripple(layers, 18f)
        box.setPadding(dp(14), dp(14), dp(14), dp(14))
        box.isClickable = true
        box.contentDescription = title + if (picked) ", chosen" else ""
        box.setOnClickListener { onPick() }
        val mark = android.widget.FrameLayout(this)
        mark.background = rounded(if (picked) T.accent else T.key, dpf(10f))
        mark.addView(glyph(icon, if (picked) T.onAccent else T.ink, 18, 2f), android.widget.FrameLayout.LayoutParams(dp(18), dp(18), android.view.Gravity.CENTER))
        box.put(mark, dp(36), dp(36))
        val words = column(2)
        words.put(text(title, 14f, T.ink, Fonts.sansBold))
        words.put(text(detail, 12f, T.muted))
        box.put(words, 0, WRAP_CONTENT, 1f)
        return box
    }

    override fun render() {
        if (step == 1) one() else two()
    }

    private fun one() {
        steps(1)
        content.put(Brand.wordmark(this, 18f), WRAP_CONTENT)
        content.put(text("What should your apps remember?", 22f, T.ink, Fonts.sansBold))
        content.put(note("This is what Leyden itself keeps about each app, to make your list more helpful. Sites keep their own logins and data either way. You can change this for any app later."))
        content.put(choiceCard("nothing", "Apps open fresh-feeling: no history, counts or previews.", Glyphs.MINUS, defaults.rememberMode == "nothing") {
            defaults.rememberMode = "nothing"
            defaults.remember.clear()
            redraw()
        })
        content.put(choiceCard("let me pick", "Choose each kind below. Off means not kept at all.", Glyphs.LIST, defaults.rememberMode == "pick") {
            defaults.rememberMode = "pick"
            if (defaults.remember.isEmpty()) defaults.remember = Kinds.HELPFUL.toMutableSet()
            redraw()
        })
        content.put(choiceCard("everything listed", "All of the below, for the most helpful list.", Glyphs.CHECK, defaults.rememberMode == "everything") {
            defaults.rememberMode = "everything"
            defaults.remember = Kinds.ALL.toMutableSet()
            redraw()
        })
        val list = card()
        for (kind in Kinds.ALL) {
            list.switch(Kinds.icon(kind), Kinds.name(kind), Kinds.detail(kind), kind in defaults.remember) { on ->
                if (on) defaults.remember += kind else defaults.remember -= kind
                defaults.rememberMode = when (defaults.remember.size) {
                    0 -> "nothing"
                    Kinds.ALL.size -> "everything"
                    else -> "pick"
                }
                redraw()
            }
        }
        content.put(list.view)
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Next · blocking", Glyphs.ARROW_RIGHT) {
            step = 2
            redrawTop()
        }, dp(220), dp(52))
        showBottom()
    }

    private fun two() {
        steps(2)
        content.put(text("Block ads and trackers?", 22f, T.ink, Fonts.sansBold))
        content.put(note("Blocking uses public filter lists. It stops requests to ad and tracking servers, and strict also hides the empty spaces they leave. Each app can have its own level."))
        val levels = listOf(
            Triple("off", "nothing blocked", Glyphs.MINUS),
            Triple("standard", "ads and trackers", Glyphs.SHIELD),
            Triple("strict", "ads, trackers, and hides leftovers", Glyphs.SPARK),
        )
        for ((key, detail, icon) in levels) {
            content.put(choiceCard(key, detail, icon, defaults.blocking == key) {
                defaults.blocking = key
                redraw()
            })
        }
        content.put(sectionTitle("LISTS", Glyphs.LIST))
        val list = card()
        for (filter in Blocker.BUILT_IN) {
            list.switch(Glyphs.LIST, filter.name, filter.detail, filter.key in defaults.lists) { on ->
                if (on) defaults.lists += filter.key else defaults.lists -= filter.key
            }
        }
        list.switch(Glyphs.WIFI, "update lists on Wi-Fi only", "they’re a few megabytes each", defaults.wifiOnly) { defaults.wifiOnly = it }
        content.put(list.view)
        bottom.put(quietButton("Back", Glyphs.ARROW_LEFT) {
            step = 1
            redrawTop()
        }, WRAP_CONTENT, dp(52))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Done", Glyphs.CHECK) { finishSetup() }, dp(180), dp(52))
        showBottom()
    }

    private fun finishSetup() {
        defaults.firstRunDone = true
        Defaults.save(this, defaults)
        if (defaults.blocking != "off" && defaults.lists.isNotEmpty()) {
            Blocker.update(this, false) { problem -> if (problem != null) applicationContext.toast(problem) }
        }
        finish()
    }
}
