package org.bond.pwaapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView

object CodeView {
    fun marked(code: String): CharSequence {
        val text = SpannableString(code)
        var start = 0
        for (line in code.split('\n')) {
            val end = start + line.length
            if (Headers.risky(line)) text.setSpan(BackgroundColorSpan(T.accentSoft), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end + 1
        }
        return text
    }

    fun readOnly(context: Context, code: String, wrap: Boolean = true): View {
        val box = context.line()
        box.gravity = Gravity.TOP
        box.background = context.surface(16f)
        box.setPadding(context.dp(4), context.dp(10), context.dp(10), context.dp(10))
        val lines = code.split('\n')
        val gutter = context.text((1..lines.size).joinToString("\n") { number -> if (Headers.risky(lines[number - 1])) "● $number" else "$number" }, 11f, T.faint, Fonts.mono)
        gutter.gravity = Gravity.END
        gutter.setLineSpacing(0f, 1.25f)
        gutter.setPadding(0, 0, context.dp(8), 0)
        val body = context.text(marked(code), 11f, T.ink, Fonts.mono)
        body.setLineSpacing(0f, 1.25f)
        body.setTextIsSelectable(true)
        box.put(gutter, WRAP_CONTENT)
        if (wrap && code.lines().all { it.length < 60 }) {
            box.put(body, 0, WRAP_CONTENT, 1f)
        } else {
            val scroller = HorizontalScrollView(context)
            scroller.addView(body)
            box.put(scroller, 0, WRAP_CONTENT, 1f)
        }
        return box
    }

    fun summary(context: Context, code: String, kind: String): View {
        val flow = Flow(context)
        flow.addView(context.tagView(bytes(code.toByteArray().size.toLong())))
        flow.addView(context.tagView(plural(code.lines().size, "line")))
        flow.addView(context.tagView(kind, T.good, T.good and 0x00FFFFFF or 0x1A000000))
        val lower = code.lowercase()
        val network = code.lines().count { val l = it.lowercase(); "xmlhttprequest" in l || "fetch(" in l || "sendbeacon" in l || "websocket" in l }
        if (network > 0) flow.addView(context.tagView(plural(network, "network line"), T.danger, T.danger and 0x00FFFFFF or 0x1A000000))
        val clip = code.lines().count { "clipboard" in it.lowercase() }
        if (clip > 0) flow.addView(context.tagView(plural(clip, "clipboard line"), T.danger, T.danger and 0x00FFFFFF or 0x1A000000))
        if ("document.cookie" in lower) flow.addView(context.tagView("reads cookies", T.danger, T.danger and 0x00FFFFFF or 0x1A000000))
        return flow
    }

    fun check(context: Context, code: String, done: (String?) -> Unit) {
        val web = WebView(context)
        web.settings.javaScriptEnabled = true
        web.evaluateJavascript("(function(){try{new Function(${org.json.JSONObject.quote(code)});return ''}catch(e){return String(e.message)}})()") { value ->
            web.destroy()
            val message = value?.trim('"').orEmpty()
            done(message.ifEmpty { null })
        }
    }
}

class CodeActivity : Screen() {
    private lateinit var site: Site
    private lateinit var item: Insertion
    private var editing = false
    private var wrap = true
    private var editor: EditText? = null
    private var gutter: TextView? = null
    private val history = mutableListOf<String>()
    private var place = -1
    private var restoring = false
    private var lastRecord = 0L
    private var started = ""

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
        editing = !item.fromOutside
        started = item.code
    }

    override fun onBack(): Boolean {
        val typed = editor?.text?.toString()
        if (editing && typed != null && typed != item.code) {
            ask("Leave without saving?", "Your changes to this code are dropped.", "Leave") {
                dropIfNew()
                finish()
            }
            return true
        }
        dropIfNew()
        return false
    }

    private fun dropIfNew() {
        if (intent.getStringExtra(MODE) == "new" && item.code.isEmpty()) {
            site.insertions.removeAll { it.id == item.id }
            SiteStore.save(this, site)
        }
    }

    override fun render() {
        if (!this::site.isInitialized) return
        header(item.name.ifEmpty { if (item.kind == "css") "New style" else "New script" }, (if (editing) "editing · " else "read only · ") + item.kindName, site = site)
        if (!editing) {
            content.put(CodeView.summary(this, item.code, item.kindName))
            content.put(CodeView.readOnly(this, item.code, wrap))
            content.put(note("Lines marked ● reach outside the page: other servers, your clipboard, cookies or storage."))
            bottom.put(quietButton("Back", Glyphs.ARROW_LEFT) { finish() }, WRAP_CONTENT, dp(50))
            bottom.put(spacer(), 0, 1, 1f)
            bottom.put(primaryButton("Edit a copy", Glyphs.PEN) { detach() }, dp(190), dp(50))
            showBottom()
            return
        }
        if (item.source != null) {
            content.put(callout(Glyphs.WARN, "This is your copy", "Edits stop updates from ${item.source?.label}. Its source is kept, so you can compare or go back to theirs any time.", T.accent))
        }
        if (item.name.isEmpty()) {
            val name = field("", if (item.kind == "css") "Name, like “Wider reading column”" else "Name, like “Bigger kanji”")
            name.addTextChangedListener(simpleWatcher { item.name = it })
            content.put(name)
        }
        val tools = line(6)
        tools.put(small("undo") { step(-1) }, WRAP_CONTENT, dp(32))
        tools.put(small("redo") { step(1) }, WRAP_CONTENT, dp(32))
        tools.put(small("find") { find() }, WRAP_CONTENT, dp(32))
        tools.put(small("go to line") { goTo() }, WRAP_CONTENT, dp(32))
        tools.put(spacer(), 0, 1, 1f)
        tools.put(small(if (wrap) "wrap: on" else "wrap: off") {
            editor?.let { item.code = it.text.toString() }
            wrap = !wrap
            redraw()
        }, WRAP_CONTENT, dp(32))
        content.put(tools)
        content.put(editorView())
        if (item.source != null) {
            val changes = Diff.changes(item.source?.upstream.orEmpty(), editor?.text?.toString().orEmpty())
            content.put(sectionTitle("CHANGES FROM THEIRS", Glyphs.HISTORY, plural(changes.size, "line")))
            content.put(diffView(changes))
        }
        bottom.put(quietButton("Cancel", Glyphs.CLOSE) { if (!onBack()) finish() }, WRAP_CONTENT, dp(50))
        bottom.put(secondaryButton("Check", Glyphs.CHECK) { check() }, WRAP_CONTENT, dp(50))
        bottom.put(secondaryButton("Test", Glyphs.PLAY) { save(true) }, WRAP_CONTENT, dp(50))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Save", Glyphs.CHECK) { save() }, dp(120), dp(50))
        showBottom()
    }

    private fun small(label: String, onClick: () -> Unit): View {
        val view = text(label, 12f, T.ink, Fonts.sansMedium)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(10), 0, dp(10), dp(1))
        view.background = secondaryBack(8f)
        view.setOnClickListener { onClick() }
        return view
    }

    private fun simpleWatcher(onChange: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            onChange(s?.toString().orEmpty())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun editorView(): View {
        val box = line()
        box.gravity = Gravity.TOP
        box.background = surface(16f)
        box.setPadding(dp(4), dp(10), dp(8), dp(10))
        val numbers = text("1", 11f, T.faint, Fonts.mono)
        numbers.gravity = Gravity.END
        numbers.setPadding(0, 0, dp(8), 0)
        gutter = numbers
        box.put(numbers, WRAP_CONTENT)
        val edit = EditText(this)
        edit.setText(item.code)
        edit.typeface = Fonts.mono
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        edit.setTextColor(T.ink)
        edit.background = null
        edit.setPadding(0, 0, 0, 0)
        edit.gravity = Gravity.TOP or Gravity.START
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        edit.minLines = 14
        edit.setHorizontallyScrolling(!wrap)
        edit.includeFontPadding = false
        numbers.includeFontPadding = false
        editor = edit
        if (history.isEmpty()) {
            history += item.code
            place = 0
        }
        edit.addTextChangedListener(simpleWatcher { value ->
            numbers.post { paintGutter() }
            if (restoring) return@simpleWatcher
            val now = System.currentTimeMillis()
            if (now - lastRecord > 700 || history.isEmpty()) {
                while (history.size > place + 1) history.removeAt(history.size - 1)
                history += value
                if (history.size > 60) history.removeAt(0)
                place = history.size - 1
            } else if (place >= 0) {
                history[place] = value
            }
            lastRecord = now
        })
        edit.viewTreeObserver.addOnGlobalLayoutListener { paintGutter() }
        box.put(edit, 0, WRAP_CONTENT, 1f)
        return box
    }

    private fun paintGutter() {
        val edit = editor ?: return
        val numbers = gutter ?: return
        val layout = edit.layout ?: return
        val text = edit.text
        val out = StringBuilder()
        var number = 1
        for (visual in 0 until layout.lineCount) {
            val start = layout.getLineStart(visual)
            if (visual == 0 || (start > 0 && text[start - 1] == '\n')) {
                out.append(number++)
            }
            if (visual < layout.lineCount - 1) out.append('\n')
        }
        if (numbers.text.toString() != out.toString()) {
            numbers.text = out
            numbers.minHeight = edit.height
        }
    }

    private fun step(delta: Int) {
        val edit = editor ?: return
        val next = place + delta
        if (next !in history.indices) return
        place = next
        restoring = true
        edit.setText(history[place])
        edit.setSelection(edit.text.length)
        restoring = false
    }

    private fun find() {
        promptText("Find", "", "text to find", true) { wanted ->
            val edit = editor ?: return@promptText
            if (wanted.isEmpty()) return@promptText
            val text = edit.text.toString()
            var at = text.indexOf(wanted, edit.selectionEnd)
            if (at < 0) at = text.indexOf(wanted)
            if (at < 0) {
                toast("Not found")
                return@promptText
            }
            edit.requestFocus()
            edit.setSelection(at, at + wanted.length)
            edit.post { scrollTo(edit, at) }
        }
    }

    private fun goTo() {
        promptText("Go to line", "", "12", true) { typed ->
            val edit = editor ?: return@promptText
            val number = typed.trim().toIntOrNull() ?: return@promptText
            val text = edit.text.toString()
            var offset = 0
            repeat((number - 1).coerceAtLeast(0)) {
                val next = text.indexOf('\n', offset)
                if (next >= 0) offset = next + 1
            }
            edit.requestFocus()
            edit.setSelection(offset.coerceAtMost(text.length))
            edit.post { scrollTo(edit, offset) }
        }
    }

    private fun scrollTo(edit: EditText, offset: Int) {
        val layout = edit.layout ?: return
        val y = layout.getLineTop(layout.getLineForOffset(offset))
        val spot = IntArray(2)
        edit.getLocationInWindow(spot)
        val content = IntArray(2)
        this.content.getLocationInWindow(content)
        scroll.smoothScrollTo(0, (spot[1] - content[1] + y - dp(120)).coerceAtLeast(0))
    }

    private fun check() {
        val code = editor?.text?.toString().orEmpty()
        if (item.kind == "css") {
            val open = code.count { it == '{' }
            val close = code.count { it == '}' }
            toast(if (open == close) "Looks fine: braces match" else "Braces don’t match: $open { and $close }")
            return
        }
        CodeView.check(this, code) { problem -> toast(problem?.let { "Mistake: $it" } ?: "No mistakes found") }
    }

    private fun detach() {
        ask("Edit your own copy?", "Edits stop updates from ${item.source?.label}. The original stays so you can compare or go back to it.", "Edit a copy") {
            editing = true
            redrawTop()
        }
    }

    private fun save(test: Boolean = false) {
        val code = editor?.text?.toString() ?: return
        val source = item.source
        if (source != null && code != source.upstream) source.detached = true
        item.code = code
        if (item.name.isEmpty()) item.name = if (item.kind == "css") "Untitled style" else "Untitled script"
        AddonState.clearFailure(this, site.id, item.id)
        SiteStore.save(this, site)
        if (test) {
            Shortcuts.test(this, site, item.id)
            return
        }
        toast("Saved. It applies the next time a page loads.")
        finish()
    }

    companion object {
        const val MODE = "mode"
    }
}

