package org.bond.pwaapp

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class Tokens(val dark: Boolean, val base: Int = Accents.ORANGE) {
    val bg = if (dark) 0xFF161615.toInt() else 0xFFEDEDEA.toInt()
    val surface = if (dark) 0xFF222220.toInt() else 0xFFFFFFFF.toInt()
    val lift = if (dark) 0xFF0A0A09.toInt() else 0xFFD8D8D3.toInt()
    val ink = if (dark) 0xFFEDEDEA.toInt() else 0xFF1C1C1C.toInt()
    val body = if (dark) 0xFFC9C9C3.toInt() else 0xFF3A3A37.toInt()
    val muted = if (dark) 0xFFA3A39C.toInt() else 0xFF6B6B66.toInt()
    val faint = 0xFF8A8A84.toInt()
    val key = if (dark) 0xFF2C2C29.toInt() else 0xFFDADAD6.toInt()
    val keyOn = if (dark) 0xFF3A3A36.toInt() else 0xFFFFFFFF.toInt()
    val accent = if (dark) blend(base, 0xFFFFFFFF.toInt(), 0.18f) else base
    val accentEdge = blend(base, 0xFF000000.toInt(), 0.22f)
    val accentSoft = (accent and 0x00FFFFFF) or (if (dark) 0x38000000 else 0x2E000000)
    val onAccent = if (isLight(accent)) 0xFF1C1C1C.toInt() else 0xFFFFFFFF.toInt()
    val rule = if (dark) 0xFF2D2D2A.toInt() else 0xFFECECE8.toInt()
    val border = if (dark) 0xFF3A3A36.toInt() else 0xFFDCDCD7.toInt()
    val band = if (dark) 0xFF1A1A18.toInt() else 0xFFECECE8.toInt()
    val switchOff = if (dark) 0xFF4A4A45.toInt() else 0xFFCFCFCA.toInt()
    val chosenLift = if (dark) 0xFF0A0A09.toInt() else 0xFFBDBDB8.toInt()
    val danger = if (dark) 0xFFF97066.toInt() else 0xFFB42318.toInt()
    val good = if (dark) 0xFF5BC27F.toInt() else 0xFF1F7A3E.toInt()
    val goodLine = if (dark) 0xFF2F5A3D.toInt() else 0xFFBFDCC9.toInt()
    val info = if (dark) 0xFF8EA6E0.toInt() else 0xFF4F6BA8.toInt()
    val press = if (dark) 0x22FFFFFF else 0x14000000
}

object Fonts {
    lateinit var sans: Typeface
    lateinit var sansMedium: Typeface
    lateinit var sansBold: Typeface
    lateinit var mono: Typeface
    lateinit var monoMedium: Typeface
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val assets = context.applicationContext.assets
        fun font(name: String, fallback: Typeface): Typeface = try {
            Typeface.createFromAsset(assets, "fonts/$name.ttf")
        } catch (error: RuntimeException) {
            fallback
        }
        sans = font("IBMPlexSans-Regular", Typeface.DEFAULT)
        sansMedium = font("IBMPlexSans-Medium", Typeface.DEFAULT)
        sansBold = font("IBMPlexSans-SemiBold", Typeface.DEFAULT_BOLD)
        mono = font("IBMPlexMono-Regular", Typeface.MONOSPACE)
        monoMedium = font("IBMPlexMono-Medium", Typeface.MONOSPACE)
        loaded = true
    }
}

object Look {
    var t = Tokens(false)

    fun refresh(context: Context) {
        Fonts.load(context)
        val theme = Defaults.get(context).theme
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (theme) {
            "dark" -> true
            "light" -> false
            else -> night
        }
        val base = Accents.resolve(context, Defaults.get(context).accent, dark)
        if (dark != t.dark || base != t.base) t = Tokens(dark, base)
    }
}

object Accents {
    const val ORANGE = 0xFFE8590C.toInt()
    val PRESETS = listOf(
        "orange" to ORANGE,
        "amber" to 0xFFD99A06.toInt(),
        "red" to 0xFFD9363E.toInt(),
        "pink" to 0xFFD63A85.toInt(),
        "violet" to 0xFF7A4DD8.toInt(),
        "blue" to 0xFF2F6FEB.toInt(),
        "teal" to 0xFF0E8C8C.toInt(),
        "green" to 0xFF1F8A4C.toInt(),
        "graphite" to 0xFF4A4A46.toInt(),
    )

    val phoneWorks: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= 31

    fun resolve(context: Context, choice: String, dark: Boolean): Int {
        if (choice == "phone" && phoneWorks) {
            return context.resources.getColor(if (dark) android.R.color.system_accent1_300 else android.R.color.system_accent1_600, null)
        }
        PRESETS.firstOrNull { it.first == choice }?.let { return it.second }
        return parseCssColor(choice)?.let { it or 0xFF000000.toInt() } ?: ORANGE
    }

    fun name(choice: String): String = when {
        choice == "phone" -> "from your phone"
        PRESETS.any { it.first == choice } -> choice
        else -> choice.uppercase()
    }

    fun rgb(color: Int): String = "${(color shr 16) and 0xFF},${(color shr 8) and 0xFF},${color and 0xFF}"
}

val T: Tokens
    get() = Look.t

fun Context.dp(value: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

fun Context.dp(value: Int): Int = dp(value.toFloat())

fun Context.dpf(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

fun Context.surface(radius: Float = 18f, color: Int = T.surface, lift: Int = T.lift): Drawable {
    val r = dpf(radius)
    val layers = LayerDrawable(arrayOf(rounded(lift, r), rounded(color, r)))
    layers.setLayerInset(1, 0, 0, 0, dp(1))
    return layers
}

fun Context.ripple(content: Drawable?, radius: Float = 14f): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(T.press), content, rounded(Color.WHITE, dpf(radius)))

fun Context.primaryBack(radius: Float = 14f): Drawable {
    val r = dpf(radius)
    val layers = LayerDrawable(arrayOf(rounded(T.accentEdge, r), rounded(0x47FFFFFF, r), rounded(T.accent, r)))
    layers.setLayerInset(1, 0, 0, 0, dp(2))
    layers.setLayerInset(2, 0, dp(1), 0, dp(2))
    return ripple(layers, radius)
}

fun Context.secondaryBack(radius: Float = 14f, fill: Int = T.surface): Drawable {
    val r = dpf(radius)
    val layers = LayerDrawable(arrayOf(rounded(T.border, r), rounded(T.band, r), rounded(fill, r)))
    layers.setLayerInset(1, dp(1), dp(1), dp(1), dp(1))
    layers.setLayerInset(2, dp(1), dp(1), dp(1), dp(3))
    return ripple(layers, radius)
}

fun Context.text(value: CharSequence, size: Float = 13f, color: Int = T.ink, font: Typeface = Fonts.sans): TextView =
    TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        typeface = font
        includeFontPadding = false
        setLineSpacing(0f, 1.15f)
    }

fun Context.mono(value: CharSequence, size: Float = 10f, color: Int = T.muted, spacing: Float = 0.1f): TextView =
    text(value, size, color, Fonts.mono).apply { letterSpacing = spacing }

fun TextView.oneLine(): TextView {
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
    return this
}

fun Context.glyph(data: String, color: Int = T.body, size: Int = 17, stroke: Float = 1.9f): ImageView =
    ImageView(this).apply {
        setImageDrawable(Glyph(data, color, stroke))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

fun Context.column(gap: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (gap > 0) {
        dividerDrawable = GradientDrawable().apply { setSize(0, dp(gap)) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }
}

fun Context.line(gap: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    if (gap > 0) {
        dividerDrawable = GradientDrawable().apply { setSize(dp(gap), 0) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }
}

fun <V : View> ViewGroup.put(view: V, width: Int = MATCH_PARENT, height: Int = WRAP_CONTENT, weight: Float = 0f): V {
    val params = if (this is LinearLayout) LinearLayout.LayoutParams(width, height, weight) else ViewGroup.MarginLayoutParams(width, height)
    if (this is FrameLayout) {
        addView(view, FrameLayout.LayoutParams(width, height))
    } else {
        addView(view, params)
    }
    return view
}

fun View.margins(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0): View {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(left, top, right, bottom)
    return this
}

fun Context.spacer(): View = View(this)

fun Context.sectionTitle(title: String, icon: String? = null, note: String? = null): View {
    val row = line(6)
    row.setPadding(dp(4), dp(4), dp(4), 0)
    if (icon != null) row.put(glyph(icon, T.muted, 12, 2f), dp(12), dp(12))
    row.put(mono(title), WRAP_CONTENT)
    if (note != null) {
        row.put(spacer(), 0, 1, 1f)
        row.put(text(note, 11f, T.faint), WRAP_CONTENT)
    }
    return row
}

class Switch(context: Context) : View(context) {
    var checked = false
        set(value) {
            field = value
            contentDescription = if (value) "on" else "off"
            invalidate()
        }
    var onChange: ((Boolean) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            checked = !checked
            onChange?.invoke(checked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(34), context.dp(20))
    }

    override fun onDraw(canvas: Canvas) {
        val radius = context.dpf(4f)
        paint.color = if (checked) T.accent else T.switchOff
        paint.alpha = if (isEnabled) 255 else 110
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(box, radius, radius, paint)
        val knob = context.dpf(16f)
        val inset = context.dpf(2f)
        val left = if (checked) width - knob - inset else inset
        paint.color = Color.WHITE
        paint.alpha = if (isEnabled) 255 else 170
        box.set(left, inset, left + knob, inset + knob)
        canvas.drawRoundRect(box, context.dpf(3f), context.dpf(3f), paint)
    }
}

class Option(val key: String, val name: String = key)

fun Context.chips(options: List<Option>, chosen: String, enabled: Boolean = true, onPick: (String) -> Unit): LinearLayout {
    val group = line(2)
    group.background = rounded(T.bg, dpf(8f))
    group.setPadding(dp(2), dp(2), dp(2), dp(2))
    for (option in options) {
        val picked = option.key == chosen
        val chip = text(option.name, 11f, if (enabled) T.ink else T.faint, if (picked) Fonts.sansBold else Fonts.sans)
        chip.gravity = Gravity.CENTER
        chip.setPadding(dp(8), 0, dp(8), 0)
        chip.minWidth = dp(34)
        if (picked) {
            val layers = LayerDrawable(arrayOf(rounded(T.chosenLift, dpf(6f)), rounded(T.keyOn, dpf(6f))))
            layers.setLayerInset(1, 0, 0, 0, dp(1))
            chip.background = layers
        }
        chip.contentDescription = option.name + if (picked) ", chosen" else ""
        chip.isEnabled = enabled
        chip.setOnClickListener { if (!picked) onPick(option.key) }
        group.put(chip, WRAP_CONTENT, dp(26))
    }
    return group
}

fun Context.primaryButton(label: String, icon: String? = null, onClick: () -> Unit): View {
    val button = line()
    button.background = primaryBack()
    button.isClickable = true
    button.contentDescription = label
    button.setOnClickListener { onClick() }
    val words = text(label, 14f, T.onAccent, Fonts.sansBold)
    words.gravity = Gravity.CENTER
    button.put(words, 0, MATCH_PARENT, 1f)
    if (icon != null) {
        val block = FrameLayout(this)
        val shape = GradientDrawable().apply {
            setColor(0x24000000)
            val r = dpf(14f)
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        block.background = shape
        val mark = glyph(icon, T.onAccent, 18, 2.2f)
        block.addView(mark, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        button.put(block, dp(46), MATCH_PARENT).margins(0, dp(1), 0, dp(2))
    }
    button.setPadding(0, 0, 0, 0)
    return button
}

fun Context.secondaryButton(label: String, icon: String? = null, color: Int = T.ink, onClick: () -> Unit): View {
    val button = line(6)
    button.gravity = Gravity.CENTER
    button.background = secondaryBack()
    button.setPadding(dp(12), 0, dp(12), dp(2))
    button.isClickable = true
    button.contentDescription = label
    button.setOnClickListener { onClick() }
    if (icon != null) button.put(glyph(icon, color, 15, 2f), dp(15), dp(15))
    button.put(text(label, 13f, color, Fonts.sansMedium), WRAP_CONTENT)
    return button
}

fun Context.quietButton(label: String, icon: String? = null, color: Int = T.muted, onClick: () -> Unit): View {
    val button = line(6)
    button.gravity = Gravity.CENTER
    button.setPadding(dp(12), 0, dp(12), 0)
    button.background = ripple(null)
    button.isClickable = true
    button.contentDescription = label
    button.setOnClickListener { onClick() }
    if (icon != null) button.put(glyph(icon, color, 15, 2f), dp(15), dp(15))
    button.put(text(label, 13f, color, Fonts.sansMedium), WRAP_CONTENT)
    return button
}

fun Context.squareKey(icon: String, label: String, size: Int = 44, onClick: () -> Unit): View {
    val key = FrameLayout(this)
    key.background = secondaryBack()
    key.contentDescription = label
    key.isClickable = true
    key.setOnClickListener { onClick() }
    key.addView(glyph(icon, T.ink, 20, 2f), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER).apply { bottomMargin = dp(1) })
    key.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    return key
}

fun Context.toggleKey(icon: String, label: String, on: Boolean, onClick: () -> Unit): View {
    val key = column(6)
    key.gravity = Gravity.CENTER
    if (on) {
        val r = dpf(14f)
        val layers = LayerDrawable(arrayOf(rounded(T.accentEdge, r), rounded(0x47FFFFFF, r), rounded(T.accent, r)))
        layers.setLayerInset(1, 0, 0, 0, dp(2))
        layers.setLayerInset(2, 0, dp(1), 0, dp(2))
        key.background = ripple(layers)
    } else {
        key.background = secondaryBack()
    }
    key.isClickable = true
    key.contentDescription = "$label, " + if (on) "on" else "off"
    key.setOnClickListener { onClick() }
    val ink = if (on) T.onAccent else T.ink
    val mark = glyph(icon, ink, 20, 2f)
    key.put(mark, dp(20), dp(20)).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL }
    val words = text(label, 11f, ink, Fonts.sansMedium)
    words.gravity = Gravity.CENTER
    key.put(words)
    key.setPadding(dp(4), dp(10), dp(4), dp(12))
    return key
}

fun Context.field(value: String, hint: String, monoFont: Boolean = false, kind: Int = InputType.TYPE_CLASS_TEXT): EditText =
    EditText(this).apply {
        setText(value)
        this.hint = hint
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(T.ink)
        setHintTextColor(T.faint)
        typeface = if (monoFont) Fonts.mono else Fonts.sans
        inputType = kind
        background = rounded(T.surface, dpf(12f), dp(1), T.border)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isSingleLine = kind and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0
    }

fun Context.badge(value: String, color: Int): TextView {
    val view = mono(value, 10f, Color.WHITE, 0f)
    view.gravity = Gravity.CENTER
    view.background = rounded(color, dpf(5f))
    view.setPadding(dp(5), dp(2), dp(5), dp(2))
    view.minWidth = dp(18)
    return view
}

fun Context.appIcon(site: Site?, size: Int = 40, radius: Float = 12f): View {
    val frame = FrameLayout(this)
    val color = site?.color ?: T.key
    frame.background = rounded(color, dpf(radius))
    frame.clipToOutline = true
    val bitmap = site?.let { SiteStore.face(this, it) }
    if (bitmap != null) {
        val image = ImageView(this)
        image.setImageBitmap(bitmap)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(image, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    } else {
        val letter = text(site?.name?.trim()?.firstOrNull()?.uppercase() ?: "+", size * 0.42f, if (isLight(color)) 0xFF1A1A1A.toInt() else Color.WHITE, Fonts.sansBold)
        letter.gravity = Gravity.CENTER
        frame.addView(letter, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    frame.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    frame.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    return frame
}

fun Context.readouts(cells: List<Triple<String, String, String?>>, accentFirst: Boolean = false): View {
    val strip = line()
    strip.background = surface()
    strip.setPadding(0, dp(12), 0, dp(13))
    cells.forEachIndexed { index, (label, value, icon) ->
        val cell = column(4)
        cell.setPadding(dp(14), 0, dp(10), 0)
        val top = line(5)
        if (icon != null) top.put(glyph(icon, T.muted, 11, 2f), dp(11), dp(11))
        top.put(mono(label, 9f), WRAP_CONTENT)
        cell.put(top)
        val shown = text(value, 17f, if (accentFirst && index == 0) T.accent else T.ink, Fonts.monoMedium).oneLine()
        shown.tag = label
        cell.put(shown)
        strip.put(cell, 0, WRAP_CONTENT, 1f)
        if (index < cells.size - 1) {
            val rule = View(this)
            rule.setBackgroundColor(T.rule)
            strip.put(rule, dp(1), MATCH_PARENT)
        }
    }
    return strip
}

class Card(val context: Context) {
    val view: LinearLayout = context.column().apply {
        background = context.surface()
        setPadding(context.dp(14), context.dp(2), context.dp(14), context.dp(3))
    }
    private var count = 0

    private fun divide() {
        if (count++ == 0) return
        val rule = View(context)
        rule.setBackgroundColor(T.rule)
        view.put(rule, MATCH_PARENT, context.dp(1))
    }

    fun custom(child: View): View {
        divide()
        view.put(child)
        return child
    }

    private fun base(icon: String?, name: String, detail: String?, enabled: Boolean = true): Pair<LinearLayout, LinearLayout> {
        divide()
        val row = context.line(10)
        row.minimumHeight = context.dp(50)
        row.setPadding(0, context.dp(8), 0, context.dp(8))
        if (icon != null) row.put(context.glyph(icon, if (enabled) T.body else T.faint), context.dp(17), context.dp(17))
        val words = context.column(2)
        words.put(context.text(name, 13f, if (enabled) T.ink else T.faint))
        if (!detail.isNullOrEmpty()) words.put(context.text(detail, 11f, T.faint))
        row.put(words, 0, WRAP_CONTENT, 1f)
        view.put(row)
        return row to words
    }

    fun switch(icon: String?, name: String, detail: String? = null, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): View {
        val (row, _) = base(icon, name, detail, enabled)
        val toggle = Switch(context)
        toggle.checked = on
        toggle.isEnabled = enabled
        toggle.onChange = onChange
        toggle.contentDescription = name
        row.put(toggle, WRAP_CONTENT)
        if (enabled) {
            row.setOnClickListener { toggle.performClick() }
            row.background = context.ripple(null, 6f)
        }
        return row
    }

    fun choice(icon: String?, name: String, detail: String? = null, options: List<Option>, chosen: String, enabled: Boolean = true, onPick: (String) -> Unit): View {
        val (row, words) = base(icon, name, detail, enabled)
        val group = context.chips(options, chosen, enabled, onPick)
        val long = options.sumOf { it.name.length } > 16
        if (long) {
            words.put(group, WRAP_CONTENT).margins(0, context.dp(6), 0, 0)
        } else {
            row.put(group, WRAP_CONTENT)
        }
        return row
    }

    fun link(icon: String?, name: String, value: String? = null, detail: String? = null, color: Int = T.ink, onClick: () -> Unit): View {
        val (row, words) = base(icon, name, detail)
        (words.getChildAt(0) as TextView).setTextColor(color)
        if (value != null && value.length > 14) {
            val shown = context.mono(value, 11f, T.muted, 0f)
            shown.maxLines = 2
            shown.ellipsize = TextUtils.TruncateAt.END
            words.put(shown)
        } else if (value != null) {
            row.put(context.mono(value, 11f, T.muted, 0f).oneLine(), WRAP_CONTENT)
        }
        row.put(context.glyph(Glyphs.CHEVRON, T.faint, 15, 2f), context.dp(15), context.dp(15))
        row.background = context.ripple(null, 6f)
        row.setOnClickListener { onClick() }
        return row
    }

    fun value(icon: String?, name: String, value: String, detail: String? = null): View {
        val (row, words) = base(icon, name, detail)
        if (value.length > 14) {
            val shown = context.mono(value, 11f, T.muted, 0f)
            shown.maxLines = 3
            shown.ellipsize = TextUtils.TruncateAt.END
            words.put(shown)
        } else if (value.isNotEmpty()) {
            row.put(context.mono(value, 11f, T.muted, 0f).oneLine(), WRAP_CONTENT)
        }
        return row
    }

    fun stepper(icon: String?, name: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit): View {
        val (row, _) = base(icon, name, null)
        val group = context.line(2)
        group.background = rounded(T.bg, context.dpf(8f))
        group.setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
        val minus = context.text("−", 15f, T.ink, Fonts.sansMedium)
        minus.gravity = Gravity.CENTER
        minus.contentDescription = "smaller"
        minus.setOnClickListener { onMinus() }
        val shown = context.mono(value, 11f, T.ink, 0f)
        shown.gravity = Gravity.CENTER
        val plus = context.text("+", 15f, T.ink, Fonts.sansMedium)
        plus.gravity = Gravity.CENTER
        plus.contentDescription = "bigger"
        plus.setOnClickListener { onPlus() }
        group.put(minus, context.dp(30), context.dp(26))
        group.put(shown, context.dp(44), context.dp(26))
        group.put(plus, context.dp(30), context.dp(26))
        row.put(group, WRAP_CONTENT)
        return row
    }

    fun switchWith(icon: String?, name: String, detail: String?, on: Boolean, label: String, enabled: Boolean = true, onAction: () -> Unit, onChange: (Boolean) -> Unit): View {
        val (row, _) = base(icon, name, detail)
        val button = context.text(label, 12f, T.ink, Fonts.sansMedium)
        button.gravity = Gravity.CENTER
        button.setPadding(context.dp(10), 0, context.dp(10), context.dp(1))
        button.background = context.secondaryBack(8f)
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
        button.setOnClickListener { onAction() }
        row.put(button, WRAP_CONTENT, context.dp(30))
        val toggle = Switch(context)
        toggle.checked = on
        toggle.onChange = onChange
        toggle.contentDescription = name
        row.put(toggle, WRAP_CONTENT)
        return row
    }

    fun action(icon: String?, name: String, detail: String? = null, label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit): View {
        val (row, _) = base(icon, name, detail)
        val button = context.text(label, 12f, if (primary) T.onAccent else T.ink, Fonts.sansMedium)
        button.gravity = Gravity.CENTER
        button.setPadding(context.dp(10), 0, context.dp(10), context.dp(1))
        button.background = if (primary) context.primaryBack(8f) else context.secondaryBack(8f)
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
        button.setOnClickListener { onClick() }
        row.put(button, WRAP_CONTENT, context.dp(30))
        return row
    }
}

fun Context.card(): Card = Card(this)

class LimitedScroll(context: Context) : ScrollView(context) {
    var limit = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (limit > 0) MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, spec)
    }
}

open class Sheet(val activity: Activity) : Dialog(activity) {
    val body: LinearLayout
    private val scroll: LimitedScroll
    private val panel: LinearLayout
    var onExpand: (() -> Unit)? = null
    val compact: Boolean
        get() = scroll.limit > 0

    fun startCompact(fraction: Float) {
        scroll.limit = (activity.resources.displayMetrics.heightPixels * fraction).toInt()
        scroll.requestLayout()
    }

    fun expand() {
        if (scroll.limit == 0) return
        scroll.limit = 0
        scroll.requestLayout()
        onExpand?.invoke()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun dragHandle(): View {
        val handle = FrameLayout(activity)
        handle.contentDescription = "drag down to close, up for more"
        val grip = View(activity)
        grip.background = rounded(T.switchOff, activity.dpf(2f))
        handle.addView(grip, FrameLayout.LayoutParams(activity.dp(40), activity.dp(5), Gravity.CENTER))
        var startY = 0f
        var startAt = 0L
        var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startAt = System.currentTimeMillis()
                    moved = false
                    grip.background = rounded(T.accent, activity.dpf(2f))
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (kotlin.math.abs(dy) > activity.dp(6)) moved = true
                    if (dy > 0) {
                        panel.translationY = dy
                    } else if (compact && -dy > activity.dp(48)) {
                        expand()
                        startY = event.rawY
                    } else {
                        panel.translationY = 0f
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    grip.background = rounded(T.switchOff, activity.dpf(2f))
                    val dy = event.rawY - startY
                    val quick = System.currentTimeMillis() - startAt < 250 && dy > activity.dp(40)
                    if (dy > panel.height * 0.28f || quick) {
                        panel.animate().translationY(panel.height.toFloat()).setDuration(160).withEndAction { dismiss() }.start()
                    } else {
                        panel.animate().translationY(0f).setDuration(160).start()
                        if (!moved && compact && event.actionMasked == MotionEvent.ACTION_UP) expand()
                    }
                }
            }
            true
        }
        return handle
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val holder = FrameLayout(activity)
        holder.setOnClickListener { dismiss() }
        panel = activity.column()
        val r = activity.dpf(24f)
        panel.background = GradientDrawable().apply {
            setColor(T.bg)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        panel.isClickable = true
        panel.put(dragHandle(), MATCH_PARENT, activity.dp(26))
        scroll = LimitedScroll(activity)
        scroll.isFillViewport = false
        body = activity.column(12)
        body.setPadding(activity.dp(16), activity.dp(4), activity.dp(16), activity.dp(16))
        scroll.addView(body, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.put(scroll, MATCH_PARENT, WRAP_CONTENT)
        holder.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        holder.setOnApplyWindowInsetsListener { view, insets ->
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            val top = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).top else 0
            panel.setPadding(0, 0, 0, bottom)
            view.setPadding(0, top + activity.dp(40), 0, 0)
            insets
        }
        setContentView(holder)
        window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(MATCH_PARENT, MATCH_PARENT)
            window.setDimAmount(0.32f)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
            window.navigationBarColor = Color.TRANSPARENT
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun show() {
        panel.translationY = 0f
        super.show()
    }

    fun clear() {
        body.removeAllViews()
    }
}

fun Activity.ask(title: String, message: String, yes: String, danger: Boolean = false, no: String = "Cancel", onYes: () -> Unit) {
    val sheet = Sheet(this)
    sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
    if (message.isNotEmpty()) sheet.body.put(text(message, 13f, T.muted))
    val bar = line(8)
    bar.put(quietButton(no, Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
    bar.put(spacer(), 0, 1, 1f)
    if (danger) {
        bar.put(secondaryButton(yes, Glyphs.TRASH, T.danger) {
            sheet.dismiss()
            onYes()
        }, WRAP_CONTENT, dp(48))
    } else {
        bar.put(primaryButton(yes, Glyphs.ARROW_RIGHT) {
            sheet.dismiss()
            onYes()
        }, dp(170), dp(48))
    }
    sheet.body.put(bar).margins(0, dp(4), 0, 0)
    sheet.show()
}

fun Activity.promptText(title: String, value: String, hint: String, monoFont: Boolean = false, multiLine: Boolean = false, onDone: (String) -> Unit) {
    val sheet = Sheet(this)
    sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
    val kind = if (multiLine) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    val input = field(value, hint, monoFont, kind)
    if (multiLine) {
        input.minLines = 6
        input.gravity = Gravity.TOP
    }
    sheet.body.put(input)
    val bar = line(8)
    bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
    bar.put(spacer(), 0, 1, 1f)
    bar.put(primaryButton("Save", Glyphs.CHECK) {
        sheet.dismiss()
        onDone(input.text.toString())
    }, dp(150), dp(48))
    sheet.body.put(bar)
    sheet.show()
    input.requestFocus()
    sheet.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

fun Activity.pickMany(title: String, names: List<String>, onDone: (List<Int>) -> Unit) {
    val sheet = Sheet(this)
    sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
    val picked = BooleanArray(names.size)
    val list = card()
    names.forEachIndexed { index, name ->
        list.switch(null, name, null, false) { picked[index] = it }
    }
    sheet.body.put(list.view)
    val bar = line(8)
    bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
    bar.put(spacer(), 0, 1, 1f)
    bar.put(primaryButton("Done", Glyphs.CHECK) {
        sheet.dismiss()
        onDone(picked.indices.filter { picked[it] })
    }, dp(150), dp(48))
    sheet.body.put(bar)
    sheet.show()
}

class DragList(context: Context, private val onMoved: (from: Int, to: Int) -> Unit) : LinearLayout(context) {
    private var dragging: View? = null
    private var startY = 0f
    private var startIndex = -1

    init {
        orientation = VERTICAL
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun grip(row: View, handle: View) {
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dragging = row
                    handle.isPressed = true
                    handle.invalidate()
                    startY = event.rawY
                    startIndex = indexOfChild(row)
                    row.elevation = context.dpf(8f)
                    row.setBackgroundColor(T.surface)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val moving = dragging ?: return@setOnTouchListener true
                    moving.translationY = event.rawY - startY
                    val index = indexOfChild(moving)
                    val middle = moving.top + moving.translationY + moving.height / 2f
                    val next = getChildAt(index + 1)
                    val previous = getChildAt(index - 1)
                    if (next != null && middle > next.top + next.height / 2f && next.tag != "fixed") {
                        val shift = next.height.toFloat()
                        removeView(next)
                        addView(next, index)
                        startY += shift
                        moving.translationY = event.rawY - startY
                    } else if (previous != null && middle < previous.top + previous.height / 2f && previous.tag != "fixed") {
                        val shift = previous.height.toFloat()
                        removeView(previous)
                        addView(previous, index)
                        startY -= shift
                        moving.translationY = event.rawY - startY
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handle.isPressed = false
                    handle.invalidate()
                    val moving = dragging ?: return@setOnTouchListener true
                    moving.translationY = 0f
                    moving.elevation = 0f
                    moving.background = null
                    dragging = null
                    val end = indexOfChild(moving)
                    if (end != startIndex) onMoved(startIndex, end)
                }
            }
            true
        }
    }
}

class GripView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        paint.color = if (isPressed) T.accent else T.muted
        val radius = context.dpf(1.8f)
        val step = context.dpf(5.5f)
        val left = width / 2f - step / 2f
        val top = height / 2f - step
        for (column in 0..1) for (row in 0..2) canvas.drawCircle(left + column * step, top + row * step, radius, paint)
    }
}

fun Context.gripIcon(): View {
    val grip = GripView(this)
    grip.contentDescription = "drag to reorder"
    grip.isClickable = true
    return grip
}

fun Context.tagView(label: String, color: Int = T.body, back: Int = T.bg): TextView {
    val view = mono(label, 10f, color, 0f)
    view.background = rounded(back, dpf(5f))
    view.setPadding(dp(6), dp(2), dp(6), dp(2))
    return view
}
