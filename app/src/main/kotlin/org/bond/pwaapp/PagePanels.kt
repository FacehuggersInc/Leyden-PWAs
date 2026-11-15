package org.bond.pwaapp

import android.annotation.SuppressLint
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout

@SuppressLint("ClickableViewAccessibility")
fun View.dragsWith(target: View) {
    var startX = 0f
    var startY = 0f
    var baseX = 0f
    var baseY = 0f
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                baseX = target.translationX
                baseY = target.translationY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) < context.dp(6)) return@setOnTouchListener true
                val parent = target.parent as? View
                var x = baseX + dx
                var y = baseY + dy
                if (parent != null) {
                    x = x.coerceIn(-target.left.toFloat(), (parent.width - target.right).toFloat())
                    y = y.coerceIn(-target.top.toFloat(), (parent.height - target.bottom).toFloat())
                }
                target.translationX = x
                target.translationY = y
                true
            }
            else -> true
        }
    }
}

class FindBar private constructor(private val activity: WebAppActivity) {
    private val bar = activity.line(6)
    private val count = activity.mono("", 11f, T.muted, 0f)

    @SuppressLint("ClickableViewAccessibility")
    private fun open(query: String) {
        bar.background = activity.surface(16f)
        bar.elevation = activity.dpf(6f)
        bar.setPadding(activity.dp(8), activity.dp(6), activity.dp(6), activity.dp(6))
        val grip = activity.gripIcon()
        bar.put(grip, activity.dp(20), activity.dp(36))
        grip.dragsWith(bar)
        val field = activity.field(query, "Find on this page", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        field.setPadding(activity.dp(10), activity.dp(6), activity.dp(10), activity.dp(6))
        field.setOnEditorActionListener { _, _, _ ->
            activity.webView.findNext(true)
            true
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                activity.webView.findAllAsync(s?.toString().orEmpty())
            }
        })
        bar.put(field, 0, activity.dp(40), 1f)
        bar.put(count, WRAP_CONTENT)
        bar.put(activity.squareKey(Glyphs.CHEVRON_UP, "Previous match", 36) { activity.webView.findNext(false) }, activity.dp(36), activity.dp(36))
        bar.put(activity.squareKey(Glyphs.CHEVRON_DOWN, "Next match", 36) { activity.webView.findNext(true) }, activity.dp(36), activity.dp(36))
        bar.put(activity.squareKey(Glyphs.CLOSE, "Close find", 36) { close() }, activity.dp(36), activity.dp(36))
        activity.webView.setFindListener { active, total, done ->
            if (done) count.text = if (total == 0) "none" else "${active + 1}/$total"
        }
        activity.stage.addView(bar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(activity.dp(10), activity.dp(10) + activity.topInset(), activity.dp(10), 0)
        })
        activity.webView.findAllAsync(query)
    }

    fun close() {
        activity.webView.clearMatches()
        activity.webView.setFindListener(null)
        activity.stage.removeView(bar)
        if (current === this) current = null
    }

    companion object {
        private var current: FindBar? = null

        fun show(activity: WebAppActivity, query: String) {
            current?.close()
            val made = FindBar(activity)
            current = made
            made.open(query)
        }
    }
}

class TestPanel(private val activity: WebAppActivity, private val itemId: String) {
    private val panel = activity.column(8)
    private val log = activity.column(4)
    private val lines = mutableListOf<Triple<String, String, Long>>()
    private var small = false
    private val started = System.currentTimeMillis()

    private val item: Insertion?
        get() = activity.site.insertions.firstOrNull { it.id == itemId }

    fun show() {
        panel.background = activity.surface(18f)
        panel.elevation = activity.dpf(10f)
        panel.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(12))
        panel.isClickable = true
        build()
        activity.stage.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(activity.dp(10), activity.dp(10) + activity.topInset(), activity.dp(10), 0)
        })
    }

    fun close() {
        activity.stage.removeView(panel)
        if (activity.testPanel === this) activity.testPanel = null
    }

    fun matches(id: String): Boolean = id == itemId

    fun restart() {
        lines.clear()
        paintLog()
    }

    fun update(id: String, state: String, text: String) {
        if (id != itemId) return
        lines += Triple(state, text, System.currentTimeMillis())
        while (lines.size > 12) lines.removeAt(0)
        if (small) build() else paintLog()
    }

    private fun tone(state: String): Int = when (state) {
        "done" -> T.good
        "failed", "gave up", "skipped" -> T.danger
        "waiting", "busy" -> 0xFF8A6A00.toInt()
        else -> T.info
    }

    private fun build() {
        panel.removeAllViews()
        val found = item
        val head = activity.line(8)
        val grip = activity.gripIcon()
        head.put(grip, activity.dp(20), activity.dp(32))
        grip.dragsWith(panel)
        head.put(activity.glyph(found?.let { Describe.icon(it) } ?: Glyphs.SCRIPT, T.accent, 16, 2f), activity.dp(16), activity.dp(16))
        val words = activity.column(1)
        words.put(activity.text(found?.name?.ifEmpty { "untitled" } ?: "removed", 13f, T.ink, Fonts.sansBold).oneLine())
        val last = lines.lastOrNull()
        words.put(activity.text(if (small && last != null) last.second else "testing · ${found?.kindName ?: ""}", 11f, if (small && last != null) tone(last.first) else T.muted).oneLine())
        words.dragsWith(panel)
        head.put(words, 0, WRAP_CONTENT, 1f)
        head.put(activity.squareKey(if (small) Glyphs.CHEVRON_DOWN else Glyphs.CHEVRON_UP, if (small) "Show more" else "Show less", 32) {
            small = !small
            build()
        }, activity.dp(32), activity.dp(32))
        head.put(activity.squareKey(Glyphs.CLOSE, "Close the test", 32) { close() }, activity.dp(32), activity.dp(32))
        panel.put(head)
        if (small || found == null) return

        panel.put(activity.mono("STARTS WHEN", 9f))
        panel.put(activity.text(Describe.triggers(found, activity.site), 12f, T.body))
        val conditions = mutableListOf<String>()
        if (found.path.isNotEmpty()) conditions += "page matches ${found.path}"
        if (found.exists.isNotEmpty()) conditions += "${Selectors.describe(found.exists)} exists"
        if (found.network.isNotEmpty()) conditions += Net.describe(found.network)
        if (found.timeFrom.isNotEmpty()) conditions += "${found.timeFrom}–${found.timeTo}"
        activity.site.insertions.firstOrNull { it.id == found.after }?.let { conditions += "after ${it.name.ifEmpty { "untitled" }}" }
        found.auto?.targets?.takeIf { it.isNotEmpty() }?.let { targets -> conditions += "targets: " + targets.joinToString(" or ") { Selectors.describe(it) } }
        if (conditions.isNotEmpty()) {
            panel.put(activity.mono("WATCHING FOR", 9f))
            panel.put(activity.text(conditions.joinToString("\n"), 12f, T.body, Fonts.mono))
        }
        panel.put(activity.mono("WHAT HAPPENED · THIS TEST IGNORES HOW-OFTEN LIMITS", 9f))
        panel.put(log)
        paintLog()

        val bar = activity.line(6)
        bar.put(activity.secondaryButton("again", Glyphs.RELOAD) { activity.testInsertion(itemId) }, 0, activity.dp(40), 1f)
        bar.put(activity.secondaryButton("edit", Glyphs.PEN) { edit(found) }, 0, activity.dp(40), 1f)
        bar.put(activity.secondaryButton("starts", Glyphs.PLAY) {
            activity.startActivity(Intent(activity, RunsActivity::class.java).putExtra(AppSettingsActivity.SITE, activity.site.id).putExtra(ItemActivity.ITEM, itemId))
        }, 0, activity.dp(40), 1f)
        panel.put(bar)
    }

    private fun edit(found: Insertion) {
        val target = when {
            found.kind == "auto" -> BuilderActivity::class.java
            found.fromOutside -> ItemActivity::class.java
            else -> CodeActivity::class.java
        }
        activity.startActivity(Intent(activity, target).putExtra(AppSettingsActivity.SITE, activity.site.id).putExtra(ItemActivity.ITEM, itemId))
    }

    private fun paintLog() {
        log.removeAllViews()
        if (lines.isEmpty()) {
            log.put(activity.text("Waiting for it to start…", 12f, T.faint))
            return
        }
        for ((state, text, at) in lines) {
            val row = activity.line(8)
            row.gravity = Gravity.TOP
            row.put(activity.mono(String.format(java.util.Locale.US, "%.1fs", (at - started) / 1000.0), 10f, T.faint, 0f), activity.dp(34))
            row.put(activity.tagView(state, tone(state), tone(state) and 0x00FFFFFF or 0x1F000000), WRAP_CONTENT)
            row.put(activity.text(text, 12f, T.ink), 0, WRAP_CONTENT, 1f)
            log.put(row)
        }
    }
}

fun WebAppActivity.topInset(): Int {
    val spot = IntArray(2)
    stage.getLocationOnScreen(spot)
    val inset = window.decorView.rootWindowInsets?.let {
        if (android.os.Build.VERSION.SDK_INT >= 30) it.getInsets(android.view.WindowInsets.Type.systemBars()).top else {
            @Suppress("DEPRECATION")
            it.systemWindowInsetTop
        }
    } ?: 0
    return (inset - spot[1]).coerceAtLeast(0)
}

