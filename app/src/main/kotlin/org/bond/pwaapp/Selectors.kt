package org.bond.pwaapp

import android.app.Activity
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

class SelPart(var kind: String, var name: String, var op: String = "", var value: String = "", var flag: String = "", var on: Boolean = true) {
    fun css(): String = when (kind) {
        "id" -> "#$name"
        "class" -> ".$name"
        "attr" -> "[" + name + (if (op.isEmpty()) "" else op + Selectors.quote(value) + if (flag.isEmpty()) "" else " $flag") + "]"
        else -> name
    }
}

class SelStep(var joiner: String, val parts: MutableList<SelPart>)

class TextSel(var mode: String, var tag: String, var value: String) {
    fun build(): String = "text$mode:$tag|$value"
}

object Selectors {
    val ATTR_OPS = listOf("=" to "is exactly", "^=" to "starts with", "*=" to "contains", "$=" to "ends with", "" to "has any value")
    private val TEXT = Regex("^text([*^]?):([a-zA-Z0-9*-]+)\\|(.*)$", RegexOption.DOT_MATCHES_ALL)

    fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun text(selector: String): TextSel? = TEXT.find(selector)?.let { TextSel(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }

    fun describe(selector: String): String {
        val found = text(selector) ?: return selector
        val how = when (found.mode) {
            "^" -> "starts with"
            "*" -> "contains"
            else -> "says"
        }
        return (if (found.tag == "*") "anything" else found.tag) + " that $how “${found.value}”"
    }

    fun stability(selector: String): String {
        val trimmed = selector.trim()
        return when {
            trimmed.contains(":nth-of-type") || trimmed.contains(":nth-child") || trimmed.count { it == '>' } > 1 -> "fragile"
            trimmed.startsWith("text") && text(trimmed) != null -> "stable"
            trimmed.startsWith("#") || listOf("[aria-label", "[data-testid", "[data-test", "[data-qa", "[name", "[placeholder", "[title", "[role").any { trimmed.contains(it) } -> "stable"
            else -> "may change"
        }
    }

    fun parse(selector: String): List<SelStep>? {
        val source = selector.trim()
        if (source.isEmpty()) return emptyList()
        val steps = mutableListOf<SelStep>()
        var parts = mutableListOf<SelPart>()
        var joiner = ""
        var index = 0
        fun ident(): String {
            val start = index
            while (index < source.length) {
                val c = source[index]
                if (c == '\\' && index + 1 < source.length) {
                    index += 2
                    continue
                }
                if (c.isLetterOrDigit() || c == '-' || c == '_' || c.code > 127) index++ else break
            }
            return source.substring(start, index)
        }
        fun close() {
            if (parts.isEmpty()) return
            steps += SelStep(joiner, parts)
            parts = mutableListOf()
            joiner = ""
        }
        while (index < source.length) {
            val c = source[index]
            when {
                c.isWhitespace() || c == '>' || c == '+' || c == '~' -> {
                    var found = " "
                    while (index < source.length && (source[index].isWhitespace() || source[index] in ">+~")) {
                        if (source[index] in ">+~") found = source[index].toString()
                        index++
                    }
                    if (parts.isEmpty()) {
                        if (steps.isEmpty()) return null
                    } else {
                        close()
                    }
                    joiner = found
                }
                c == ',' -> return null
                c == '#' -> {
                    index++
                    val name = ident()
                    if (name.isEmpty()) return null
                    parts += SelPart("id", name)
                }
                c == '.' -> {
                    index++
                    val name = ident()
                    if (name.isEmpty()) return null
                    parts += SelPart("class", name)
                }
                c == '[' -> {
                    index++
                    while (index < source.length && source[index].isWhitespace()) index++
                    val name = ident()
                    if (name.isEmpty()) return null
                    while (index < source.length && source[index].isWhitespace()) index++
                    var op = ""
                    var value = ""
                    var flag = ""
                    if (index < source.length && source[index] != ']') {
                        val opStart = index
                        if (source[index] in "^*$~|") index++
                        if (index >= source.length || source[index] != '=') return null
                        index++
                        op = source.substring(opStart, index)
                        while (index < source.length && source[index].isWhitespace()) index++
                        if (index >= source.length) return null
                        val quoteChar = source[index]
                        if (quoteChar == '"' || quoteChar == '\'') {
                            index++
                            val out = StringBuilder()
                            while (index < source.length && source[index] != quoteChar) {
                                if (source[index] == '\\' && index + 1 < source.length) index++
                                out.append(source[index])
                                index++
                            }
                            if (index >= source.length) return null
                            index++
                            value = out.toString()
                        } else {
                            value = ident()
                        }
                        while (index < source.length && source[index].isWhitespace()) index++
                        if (index < source.length && (source[index] == 'i' || source[index] == 's')) {
                            flag = source[index].toString()
                            index++
                        }
                        while (index < source.length && source[index].isWhitespace()) index++
                    }
                    if (index >= source.length || source[index] != ']') return null
                    index++
                    parts += SelPart("attr", name, op, value, flag)
                }
                c == ':' -> {
                    val start = index
                    index++
                    if (index < source.length && source[index] == ':') index++
                    if (ident().isEmpty()) return null
                    if (index < source.length && source[index] == '(') {
                        var depth = 0
                        while (index < source.length) {
                            if (source[index] == '(') depth++
                            if (source[index] == ')') depth--
                            index++
                            if (depth == 0) break
                        }
                        if (depth != 0) return null
                    }
                    parts += SelPart("pseudo", source.substring(start, index))
                }
                c == '*' -> {
                    index++
                    parts += SelPart("tag", "*")
                }
                else -> {
                    val name = ident()
                    if (name.isEmpty()) return null
                    parts += SelPart("tag", name)
                }
            }
        }
        close()
        return steps
    }

    fun build(steps: List<SelStep>): String {
        val out = StringBuilder()
        steps.forEachIndexed { index, step ->
            val shown = step.parts.filter { it.on }
            val ordered = shown.filter { it.kind == "tag" } + shown.filter { it.kind != "tag" }
            val body = ordered.joinToString("") { it.css() }.ifEmpty { "*" }
            if (index > 0) out.append(if (step.joiner == " ") " " else " ${step.joiner} ")
            out.append(body)
        }
        return out.toString()
    }

    fun prefixes(value: String): List<String> {
        val out = mutableListOf<String>()
        val marks = Regex("[\\s,;:|/·–—]+").findAll(value)
        for (mark in marks) {
            val cut = value.substring(0, mark.range.first).trimEnd()
            if (cut.isNotEmpty() && cut != value && cut !in out) out += cut
        }
        return out.takeLast(6)
    }

    fun joinerName(joiner: String): String = when (joiner) {
        ">" -> "directly inside"
        "+" -> "right after"
        "~" -> "after"
        else -> "inside"
    }
}

fun Activity.editSelector(
    title: String,
    start: String,
    allowText: Boolean,
    counter: ((String, (Int) -> Unit) -> Unit)? = null,
    onDone: (String) -> Unit,
) {
    val sheet = Sheet(this)
    sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
    sheet.body.put(text("Tap a part to leave it out, or tap a value to trim it, like keeping only the start of a label. You can also edit it as text.", 12f, T.muted))
    val input = field(start, if (allowText) "button.skip-intro, or text:button|Skip intro" else "div.promo-bar", true,
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    input.minLines = 2
    input.gravity = Gravity.TOP
    sheet.body.put(input)
    val status = text("", 11f, T.muted)
    sheet.body.put(status)
    val parts = column(8)
    sheet.body.put(parts)
    var current = start
    var quiet = false
    val main = Handler(Looper.getMainLooper())
    var pendingCount: Runnable? = null

    lateinit var paint: () -> Unit
    fun set(value: String) {
        current = value
        quiet = true
        input.setText(value)
        input.setSelection(value.length)
        quiet = false
        paint()
    }

    fun showCount() {
        val ask = counter
        val found = Selectors.text(current)
        when {
            current.isBlank() -> {
                status.text = "Empty"
                status.setTextColor(T.faint)
            }
            found != null && !allowText -> {
                status.text = "Matching by text only works for targets. Hiding needs a selector."
                status.setTextColor(T.danger)
            }
            found == null && Selectors.parse(current) == null -> {
                status.text = "This doesn’t read as a selector. It’s kept exactly as typed."
                status.setTextColor(0xFF8A6A00.toInt())
            }
            ask == null -> {
                status.text = Selectors.stability(current) + " · use Test now or Select on the page to see what it finds"
                status.setTextColor(T.muted)
            }
            else -> {
                status.text = "counting…"
                status.setTextColor(T.faint)
                pendingCount?.let { main.removeCallbacks(it) }
                val asked = current
                val run = Runnable {
                    ask(asked) { count ->
                        if (asked != current) return@ask
                        status.text = when (count) {
                            0 -> "matches nothing on this page"
                            1 -> "matches 1 on this page · " + Selectors.stability(asked)
                            else -> "matches $count on this page · " + Selectors.stability(asked)
                        }
                        status.setTextColor(if (count == 0) T.danger else T.good)
                    }
                }
                pendingCount = run
                main.postDelayed(run, 300)
            }
        }
    }

    fun chip(label: String, on: Boolean, editable: Boolean, onClick: () -> Unit): TextView {
        val view = text(label, 12f, if (on) T.ink else T.faint, Fonts.mono)
        view.setPadding(dp(8), dp(5), dp(8), dp(6))
        view.background = if (on) rounded(T.bg, dpf(7f), dp(1), if (editable) T.accent else T.border) else rounded(0, dpf(7f), dp(1), T.border)
        if (!on) view.paintFlags = view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        view.isClickable = true
        view.setOnClickListener { onClick() }
        view.contentDescription = label + if (on) "" else ", left out"
        return view
    }

    fun textParts(found: TextSel) {
        parts.put(mono("MATCHED BY ITS WORDS", 9f))
        val words = field(found.value, "Skip intro")
        parts.put(words)
        words.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (quiet) return
                found.value = s?.toString().orEmpty()
                current = found.build()
                quiet = true
                input.setText(current)
                quiet = false
                showCount()
            }
        })
        parts.put(chips(listOf(Option("", "says exactly"), Option("^", "starts with"), Option("*", "contains")), found.mode) {
            found.mode = it
            set(found.build())
        }, WRAP_CONTENT)
        val cuts = Selectors.prefixes(found.value)
        if (cuts.isNotEmpty()) {
            parts.put(mono("KEEP ONLY THE START", 9f))
            val flow = Flow(this)
            for (cut in cuts) flow.addView(chip("$cut…", true, true) {
                found.value = cut
                found.mode = "^"
                set(found.build())
            })
            parts.put(flow)
        }
        parts.put(chips(listOf(Option(if (found.tag == "*") "button" else found.tag, if (found.tag == "*") "button" else found.tag), Option("*", "anything")), found.tag) {
            found.tag = it
            set(found.build())
        }, WRAP_CONTENT)
    }

    fun attrSheet(steps: List<SelStep>, part: SelPart) {
        val inner = Sheet(this)
        inner.body.put(text(part.name, 18f, T.ink, Fonts.sansBold))
        var op = if (part.op in Selectors.ATTR_OPS.map { it.first }) part.op else "="
        val value = field(part.value, "value", true)
        var caseless = part.flag == "i"
        var use = part.on
        val box = column(10)
        inner.body.put(box)
        lateinit var draw: () -> Unit
        draw = {
            box.removeAllViews()
            val flow = Flow(this)
            for ((key, name) in Selectors.ATTR_OPS) {
                flow.addView(chip(name, key == op, key == op) {
                    op = key
                    draw()
                })
            }
            box.put(flow)
            if (op.isNotEmpty()) {
                box.put(value)
                val cuts = Selectors.prefixes(value.text.toString())
                if (cuts.isNotEmpty()) {
                    box.put(mono("KEEP ONLY THE START", 9f))
                    val starts = Flow(this)
                    for (cut in cuts) starts.addView(chip("$cut…", true, true) {
                        value.setText(cut)
                        op = "^="
                        draw()
                    })
                    box.put(starts)
                }
            }
            val options = card()
            if (op.isNotEmpty()) options.switch(null, "ignore upper and lower case", null, caseless) { caseless = it }
            options.switch(null, "use this part", "off: it’s left out of the selector", use) { use = it }
            box.put(options.view)
        }
        draw()
        val bar = line(8)
        bar.put(quietButton("Cancel", Glyphs.CLOSE) { inner.dismiss() }, WRAP_CONTENT, dp(48))
        bar.put(spacer(), 0, 1, 1f)
        bar.put(primaryButton("Done", Glyphs.CHECK) {
            inner.dismiss()
            part.op = op
            part.value = if (op.isEmpty()) "" else value.text.toString()
            part.flag = if (op.isNotEmpty() && caseless) "i" else ""
            part.on = use
            set(Selectors.build(steps))
        }, dp(150), dp(48))
        inner.body.put(bar)
        inner.show()
    }

    paint = {
        parts.removeAllViews()
        val found = Selectors.text(current)
        val steps = if (found == null) Selectors.parse(current) else null
        when {
            found != null -> textParts(found)
            steps != null && steps.isNotEmpty() -> {
                steps.forEachIndexed { index, step ->
                    val head = line(8)
                    val last = index == steps.size - 1
                    head.put(mono(if (last) "THE ELEMENT" else "WITHIN · STEP ${index + 1}", 9f), 0, WRAP_CONTENT, 1f)
                    if (index > 0 && (step.joiner == " " || step.joiner == ">")) {
                        head.put(chips(listOf(Option(" ", "anywhere inside"), Option(">", "directly inside")), step.joiner) {
                            step.joiner = it
                            set(Selectors.build(steps))
                        }, WRAP_CONTENT)
                    } else if (index > 0) {
                        head.put(mono(Selectors.joinerName(step.joiner), 9f), WRAP_CONTENT)
                    }
                    if (!last) {
                        head.put(squareKey(Glyphs.CLOSE, "Drop this step", 28) {
                            val rest = steps.toMutableList()
                            rest.removeAt(index)
                            if (rest.isNotEmpty()) rest[0].joiner = ""
                            if (index < rest.size && index > 0) rest[index].joiner = " "
                            set(Selectors.build(rest))
                        }, dp(28), dp(28))
                    }
                    parts.put(head)
                    val flow = Flow(this)
                    for (part in step.parts) {
                        val label = when (part.kind) {
                            "attr" -> if (part.op.isEmpty()) "[${part.name}]" else part.name + " " + (Selectors.ATTR_OPS.firstOrNull { it.first == part.op }?.second ?: part.op) + " “" + part.value + "”"
                            else -> part.css()
                        }
                        flow.addView(chip(label, part.on, part.kind == "attr") {
                            if (part.kind == "attr") {
                                attrSheet(steps, part)
                            } else {
                                part.on = !part.on
                                set(Selectors.build(steps))
                            }
                        })
                    }
                    parts.put(flow)
                }
            }
        }
        showCount()
    }

    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (quiet) return
            current = s?.toString().orEmpty().replace('\n', ' ')
            paint()
        }
    })
    paint()

    val bar = line(8)
    bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
    bar.put(spacer(), 0, 1, 1f)
    bar.put(primaryButton("Use it", Glyphs.CHECK) {
        val chosen = current.trim()
        if (!allowText && Selectors.text(chosen) != null) {
            toast("Hiding needs a selector, not a text match")
            return@primaryButton
        }
        sheet.dismiss()
        onDone(chosen)
    }, dp(150), dp(48))
    sheet.body.put(bar)
    sheet.setOnDismissListener { pendingCount?.let { main.removeCallbacks(it) } }
    sheet.show()
}

fun android.content.Context.selectorRow(selector: String, first: Boolean, onEdit: () -> Unit, onRemove: () -> Unit): LinearLayout {
    val row = line(10)
    row.minimumHeight = dp(52)
    row.setPadding(0, dp(6), 0, dp(6))
    val words = column(2)
    words.put(text(Selectors.describe(selector), 12f, T.ink, Fonts.mono).apply { maxLines = 3 })
    val stability = Selectors.stability(selector)
    val tone = when (stability) {
        "stable" -> T.good
        "fragile" -> T.danger
        else -> 0xFF8A6A00.toInt()
    }
    val tags = line(6)
    tags.put(tagView(if (first) "first choice" else "backup"), WRAP_CONTENT)
    tags.put(tagView(stability, tone, tone and 0x00FFFFFF or 0x1F000000), WRAP_CONTENT)
    words.put(tags)
    row.put(words, 0, WRAP_CONTENT, 1f)
    row.put(squareKey(Glyphs.PEN, "Edit", 32) { onEdit() }, dp(32), dp(32))
    row.put(squareKey(Glyphs.CLOSE, "Remove", 32) { onRemove() }, dp(32), dp(32))
    words.isClickable = true
    words.setOnClickListener { onEdit() }
    return row
}

