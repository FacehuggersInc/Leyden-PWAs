package org.bond.pwaapp

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout

object Brand {
    const val NAME = "Leyden PWAs"
    const val SHORT = "Leyden"
    private var splashShown = false

    fun wordmark(context: Context, size: Float = 20f): LinearLayout {
        val row = context.line(8)
        row.put(JarView(context), context.dp(size * 1.5f), context.dp(size * 1.5f))
        val words = context.line(5)
        words.put(context.text(SHORT, size, T.ink, Fonts.sansBold), WRAP_CONTENT)
        words.put(context.text("PWAs", size, T.muted, Fonts.sans), WRAP_CONTENT)
        row.put(words, WRAP_CONTENT)
        row.contentDescription = NAME
        return row
    }

    fun splash(activity: Activity) {
        if (splashShown) return
        splashShown = true
        val root = activity.window.decorView as? ViewGroup ?: return
        val cover = FrameLayout(activity)
        cover.setBackgroundColor(T.bg)
        cover.isClickable = true
        val middle = activity.column(14)
        middle.gravity = Gravity.CENTER_HORIZONTAL
        val jar = JarView(activity)
        middle.put(jar, activity.dp(120), activity.dp(120))
        val name = activity.line(6)
        name.gravity = Gravity.CENTER
        name.put(activity.text(SHORT, 26f, T.ink, Fonts.sansBold), WRAP_CONTENT)
        name.put(activity.text("PWAs", 26f, T.muted, Fonts.sans), WRAP_CONTENT)
        middle.put(name, WRAP_CONTENT)
        cover.addView(middle, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        root.addView(cover, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val charge = ValueAnimator.ofFloat(0f, 1f)
        charge.duration = 650
        charge.startDelay = 120
        charge.addUpdateListener { jar.charge = it.animatedValue as Float }
        charge.start()
        cover.postDelayed({
            cover.animate().alpha(0f).setDuration(220).withEndAction { root.removeView(cover) }.start()
        }, 1000)
    }
}

class JarView(context: Context, private val fixed: Int? = null) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val body = Path()
    private val stopper = Path()
    private val bolt = Path()
    private val streak = Path()
    var charge = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        body.moveTo(28f, 44f)
        body.lineTo(28f, 80f)
        body.arcTo(RectF(28f, 71f, 72f, 89f), 180f, -180f)
        body.lineTo(72f, 44f)
        body.close()
        stopper.moveTo(34f, 38f)
        stopper.lineTo(34f, 43f)
        stopper.arcTo(RectF(34f, 36.5f, 66f, 49.5f), 180f, -180f)
        stopper.lineTo(66f, 38f)
        stopper.close()
        val points = listOf(52f to 55f, 39.5f to 73f, 48.5f to 73f, 44.5f to 88f, 60.5f to 67f, 51.5f to 67f, 59f to 55f)
        points.forEachIndexed { index, (x, y) -> if (index == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y) }
        bolt.close()
        streak.moveTo(32.5f, 56f)
        streak.quadTo(31f, 68f, 32.5f, 82f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val color = fixed ?: T.accent
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(side / 72f, side / 72f)
        canvas.rotate(-10f)
        canvas.translate(-50f, -61f)

        fill.shader = null
        fill.color = Color.BLACK
        fill.alpha = 56
        canvas.drawOval(RectF(25f, 79f, 75f, 93f), fill)

        fill.alpha = 255
        fill.shader = LinearGradient(28f, 0f, 72f, 0f, intArrayOf(blend(color, Color.WHITE, 0.35f), color, blend(color, Color.BLACK, 0.35f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(body, fill)
        fill.shader = null
        val rim = blend(color, Color.WHITE, 0.5f)
        line.color = rim
        line.alpha = 190
        line.strokeWidth = 1.4f
        canvas.drawPath(body, line)

        fill.color = blend(color, Color.WHITE, 0.5f)
        fill.alpha = 230
        canvas.save()
        canvas.translate(0.8f, 1f)
        canvas.drawPath(bolt, fill)
        canvas.restore()
        fill.color = blend(color, Color.BLACK, 0.65f)
        fill.alpha = 255
        canvas.save()
        canvas.translate(-0.7f, -0.8f)
        canvas.drawPath(bolt, fill)
        canvas.restore()
        fill.color = blend(color, Color.BLACK, 0.32f)
        canvas.drawPath(bolt, fill)
        if (charge > 0f) {
            canvas.save()
            canvas.clipRect(30f, 88f - 33f * charge, 70f, 89f)
            fill.color = 0xFFFFF4C2.toInt()
            fill.alpha = (235 * minOf(1f, charge * 1.4f)).toInt()
            canvas.drawPath(bolt, fill)
            canvas.restore()
        }

        fill.color = rim
        fill.alpha = 90
        canvas.drawOval(RectF(28f, 35f, 72f, 53f), fill)
        canvas.drawOval(RectF(28f, 35f, 72f, 53f), line)

        fill.alpha = 255
        fill.shader = LinearGradient(34f, 0f, 66f, 0f, intArrayOf(0xFF5A3A22.toInt(), 0xFF8A5C36.toInt(), 0xFF3E2716.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(stopper, fill)
        fill.shader = null
        fill.color = 0xFF9B6A40.toInt()
        canvas.drawOval(RectF(34f, 31.5f, 66f, 44.5f), fill)

        line.color = Color.WHITE
        line.alpha = 180
        line.strokeWidth = 2.2f
        line.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(streak, line)
        line.strokeCap = Paint.Cap.BUTT
        canvas.restore()
    }
}
