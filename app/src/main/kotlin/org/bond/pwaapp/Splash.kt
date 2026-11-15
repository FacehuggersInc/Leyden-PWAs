package org.bond.pwaapp

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun blend(color: Int, toward: Int, amount: Float): Int {
    fun mix(a: Int, b: Int): Int = (a + (b - a) * amount).toInt().coerceIn(0, 255)
    return Color.rgb(
        mix(Color.red(color), Color.red(toward)),
        mix(Color.green(color), Color.green(toward)),
        mix(Color.blue(color), Color.blue(toward)),
    )
}

class Motion(activity: Activity, private val base: Int, private val moving: Boolean) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }
    private val blobs = listOf(
        Triple(blend(base, Color.WHITE, 0.22f), 0.25f to 0.3f, 0.75f),
        Triple(blend(base, Color.BLACK, 0.38f), 0.8f to 0.55f, 0.8f),
        Triple(blend(base, Color.WHITE, 0.35f), 0.35f to 0.9f, 0.7f),
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (moving) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(base)
        val size = maxOf(width, height).toFloat()
        blobs.forEachIndexed { index, (color, spot, scale) ->
            val angle = (phase + index / 3f) * 2 * PI
            val x = spot.first * width + (cos(angle) * width * 0.12f).toFloat()
            val y = spot.second * height + (sin(angle * (index + 1)) * height * 0.06f).toFloat()
            val radius = size * 0.45f * scale
            paint.shader = RadialGradient(x, y, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, paint)
        }
    }
}

class SplashView(private val activity: Activity, private val site: Site) : FrameLayout(activity) {
    val ink = if (isLight(site.background)) 0xFF1C1C1C.toInt() else Color.WHITE
    private val icon: View
    private val status: LinearLayout
    private val bottom: FrameLayout
    private var pulse: ValueAnimator? = null

    init {
        isClickable = true
        addView(Motion(activity, site.background, site.splashMotion == "drift"), LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val column = activity.column()
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(activity.dp(24), 0, activity.dp(24), activity.dp(34))
        column.put(activity.spacer(), MATCH_PARENT, 0, 1f)
        icon = activity.appIcon(site, 96, 28f)
        icon.elevation = activity.dpf(10f)
        column.put(icon, activity.dp(96), activity.dp(96)).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL }
        val name = activity.text(site.label, 22f, ink, Fonts.sansBold)
        name.gravity = Gravity.CENTER
        column.put(name, WRAP_CONTENT).margins(0, activity.dp(18), 0, activity.dp(4))
        status = activity.line(6)
        status.gravity = Gravity.CENTER
        column.put(status, WRAP_CONTENT, activity.dp(18))
        column.put(activity.spacer(), MATCH_PARENT, 0, 1f)
        bottom = FrameLayout(activity)
        column.put(bottom, MATCH_PARENT, WRAP_CONTENT)
        addView(column, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (site.splashMotion == "pulse") {
            pulse = ValueAnimator.ofFloat(1f, 1.05f).apply {
                duration = 1200
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val scale = it.animatedValue as Float
                    icon.scaleX = scale
                    icon.scaleY = scale
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        super.onDetachedFromWindow()
    }

    fun showLock(panel: View) {
        status.removeAllViews()
        status.put(activity.glyph(Glyphs.LOCK, ink, 14, 2f), activity.dp(14), activity.dp(14))
        status.put(activity.text("Locked", 13f, ink), WRAP_CONTENT)
        status.alpha = 0.85f
        bottom.removeAllViews()
        val holder = activity.column(14)
        holder.gravity = Gravity.CENTER_HORIZONTAL
        val note = activity.mono("NOTHING LOADS UNTIL YOU UNLOCK", 10f, ink, 0.1f)
        note.alpha = 0.7f
        note.gravity = Gravity.CENTER
        holder.put(note, WRAP_CONTENT)
        holder.put(panel)
        bottom.addView(holder, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun showLoading(host: String) {
        status.removeAllViews()
        bottom.removeAllViews()
        val holder = activity.column(12)
        holder.gravity = Gravity.CENTER_HORIZONTAL
        val track = FrameLayout(activity)
        track.background = rounded(ink and 0x00FFFFFF or 0x40000000, activity.dpf(2f))
        track.clipToOutline = true
        val bar = View(activity)
        bar.background = rounded(ink, activity.dpf(2f))
        track.addView(bar, LayoutParams(activity.dp(56), MATCH_PARENT))
        holder.put(track, activity.dp(140), activity.dp(3))
        val sweep = ValueAnimator.ofFloat(-1f, 2.5f).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { bar.translationX = (it.animatedValue as Float) * activity.dp(56) }
            start()
        }
        track.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) {
                sweep.cancel()
            }
        })
        val words = activity.mono(host, 11f, ink, 0f)
        words.alpha = 0.75f
        holder.put(words, WRAP_CONTENT)
        bottom.addView(holder, LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = activity.dp(14) })
    }
}
