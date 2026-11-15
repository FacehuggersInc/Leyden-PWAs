package org.bond.pwaapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

object LauncherIcons {
    private const val SIZE = 432
    private const val ART = 0.56f

    fun make(source: Bitmap?, maskable: Boolean, background: Int, name: String): Bitmap {
        val made = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(made)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (source != null && maskable) {
            canvas.drawBitmap(source, null, Rect(0, 0, SIZE, SIZE), paint)
            return made
        }
        canvas.drawColor(background)
        if (source != null) {
            canvas.drawBitmap(source, null, fitted(source, SIZE * ART), paint)
            return made
        }
        paint.color = if (isLight(background)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        paint.textSize = SIZE * 0.4f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
        val baseline = SIZE / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, SIZE / 2f, baseline, paint)
        return made
    }

    private fun fitted(source: Bitmap, room: Float): RectF {
        val scale = room / maxOf(source.width, source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (SIZE - width) / 2f
        val top = (SIZE - height) / 2f
        return RectF(left, top, left + width, top + height)
    }
}
