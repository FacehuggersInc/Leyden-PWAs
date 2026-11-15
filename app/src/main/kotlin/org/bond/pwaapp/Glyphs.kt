package org.bond.pwaapp

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Glyphs {
    const val MAIL = "M3 6h18v12H3zM3 7l9 6 9-6"
    const val COLOR = "M12 3a9 9 0 1 0 0 18c1 0 1.5-.8 1.5-1.5 0-1.2-1-1.5-1-2.5S13.3 15 14.5 15H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8zM7.5 11h.01M10 7.5h.01M15 7.5h.01"
    const val SPLASH = "M3 5h18v14H3zM21 16l-5-5-9 8M8.5 10.5a1.5 1.5 0 1 0 0-.01"
    const val MOON = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"
    const val TEXT = "M4 7V5h10v2M9 5v14M7 19h4M14 12v-1h7v1M17.5 11v8M16 19h3"
    const val ZOOM = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-3.5-3.5M11 8v6M8 11h6"
    const val FULL = "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
    const val HAND = "M8 13V5a1.5 1.5 0 0 1 3 0v6M11 11V4a1.5 1.5 0 0 1 3 0v7M14 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1-6 6h-1a5 5 0 0 1-4-2l-3-4a1.5 1.5 0 0 1 2.3-2L8 15"
    const val NAV = "M3 4h18v16H3zM3 16h18M8 18h.01M12 18h.01M16 18h.01"
    const val LINK = "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"
    const val GLOBE = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9z"
    const val POPUP = "M4 4h12v12H4zM8 20h12V8"
    const val PULL = "M12 3v12M7 10l5 5 5-5M5 21h14"
    const val BACK_PAGE = "M15 18l-6-6 6-6M21 12H9"
    const val SUN = "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM12 1v2M12 21v2M1 12h2M21 12h2"
    const val INFO = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5M12 8h.01"
    const val CLOCK = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2"
    const val PHONE = "M7 2h10v20H7zM11 18h2"
    const val DESKTOP = "M3 4h18v12H3zM8 20h8M12 16v4"
    const val BOX = "M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6"
    const val EYE = "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
    const val FINGER = "M12 10v4M8 11a4 4 0 0 1 8 0v2M5 12a7 7 0 0 1 14 0M16 15v1a4 4 0 0 1-1 3"
    const val HIDDEN = "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM4 4l16 16"
    const val TRASH = "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"
    const val CODE = "M8 6l-6 6 6 6M16 6l6 6-6 6"
    const val SCRIPT = "M5 4h11l3 3v13H5zM9 10l-2 2 2 2M15 10l2 2-2 2"
    const val SHIELD = "M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5z"
    const val COOKIE = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8 9h.01M15 10h.01M10 15h.01"
    const val LIST = "M4 6h16M4 12h16M4 18h10"
    const val WIFI = "M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"
    const val PEN = "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"
    const val BAR = "M3 4h18v16H3zM3 16h18"
    const val WARN = "M12 3 2 20h20zM12 10v4M12 17h.01"
    const val LOCK = "M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4"
    const val UNLOCK = "M6 11h12v10H6zM8 11V7a4 4 0 0 1 7.5-2"
    const val SEARCH = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-3.5-3.5"
    const val GRID = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
    const val BELL = "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0"
    const val ARROW_LEFT = "M19 12H5M12 19l-7-7 7-7"
    const val ARROW_RIGHT = "M5 12h14M12 5l7 7-7 7"
    const val CHEVRON = "m9 6 6 6-6 6"
    const val CHEVRON_LEFT = "m15 6-6 6 6 6"
    const val CHEVRON_UP = "m6 15 6-6 6 6"
    const val CHEVRON_DOWN = "m6 9 6 6 6-6"
    const val CLOSE = "M6 6l12 12M18 6 6 18"
    const val CHECK = "M20 6 9 17l-5-5"
    const val PLUS = "M12 5v14M5 12h14"
    const val MINUS = "M5 12h14"
    const val RELOAD = "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"
    const val SHARE = "M12 3v13M7 8l5-5 5 5M5 14v7h14v-7"
    const val BROWSER = "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6"
    const val COPY = "M8 8h12v12H8zM16 8V4H4v12h4"
    const val DOWNLOAD = "M12 3v12M7 10l5 5 5-5M5 21h14"
    const val UPLOAD = "M12 21V9M7 14l5-5 5 5M5 3h14"
    const val CAMERA = "M3 7h4l2-3h6l2 3h4v13H3zM12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z"
    const val MIC = "M9 3h6v11H9zM5 11a7 7 0 0 0 14 0M12 18v3"
    const val PIN = "M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12zM12 6.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"
    const val MORE = "M12 5h.01M12 12h.01M12 19h.01"
    const val HOME = "M3 11l9-8 9 8M5 9v12h14V9"
    const val SETTINGS = "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0M14 4v4M8 10v4M16 16v4"
    const val TAG = "M3 3h8l10 10-8 8L3 11zM7.5 7.5h.01"
    const val HISTORY = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5M12 7v5l3 2"
    const val CHART = "M3 3v18h18M7 15l4-4 3 3 5-6"
    const val SORT = "M4 6h16M4 12h10M4 18h6"
    const val PICKER = "M3 3l7 18 2.5-7.5L20 11zM13 13l7 7"
    const val SPARK = "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"
    const val STOP = "M5 5h14v14H5z"
    const val PLAY = "M7 4l13 8-13 8z"
    const val FOLDER = "M3 6h6l2 2h10v11H3z"
    const val MUSIC = "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    const val VOLUME = "M4 9h4l5-4v14l-5-4H4zM16 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12"
    const val SWIPE = "M4 12h16M8 8l-4 4 4 4M16 8l4 4-4 4"
    const val ROUTE = "M6 3v12a4 4 0 0 0 4 4h8M14 15l4 4-4 4"
    const val PAUSE = "M7 4h3v16H7zM14 4h3v16h-3z"
    const val NEXT = "M5 4l10 8-10 8zM17 4v16"
    const val PREV = "M19 4 9 12l10 8zM7 4v16"
    const val BUBBLE = "M4 5h16v11H9l-5 4z"

    private val cache = HashMap<String, Path>()

    fun path(data: String): Path = cache.getOrPut(data) { SvgPath.parse(data) }
}

class Glyph(private val data: String, color: Int, private val stroke: Float = 2f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val drawn = Path()
    private val matrix = Matrix()

    var color: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val box = bounds
        val scale = minOf(box.width(), box.height()) / 24f
        matrix.setScale(scale, scale)
        matrix.postTranslate(box.left + (box.width() - 24f * scale) / 2f, box.top + (box.height() - 24f * scale) / 2f)
        drawn.reset()
        Glyphs.path(data).transform(matrix, drawn)
        paint.strokeWidth = stroke * scale
        canvas.drawPath(drawn, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object SvgPath {
    fun parse(data: String): Path {
        val path = Path()
        val tokens = Tokens(data)
        var command = 'M'
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var controlX = 0f
        var controlY = 0f
        var last = ' '
        while (tokens.more()) {
            if (tokens.atCommand()) command = tokens.command()
            val relative = command.isLowerCase()
            val dx = if (relative) x else 0f
            val dy = if (relative) y else 0f
            when (command.uppercaseChar()) {
                'M' -> {
                    x = tokens.number() + dx
                    y = tokens.number() + dy
                    path.moveTo(x, y)
                    startX = x
                    startY = y
                    command = if (relative) 'l' else 'L'
                    last = 'M'
                    continue
                }
                'L' -> {
                    x = tokens.number() + dx
                    y = tokens.number() + dy
                    path.lineTo(x, y)
                }
                'H' -> {
                    x = tokens.number() + dx
                    path.lineTo(x, y)
                }
                'V' -> {
                    y = tokens.number() + dy
                    path.lineTo(x, y)
                }
                'C' -> {
                    val x1 = tokens.number() + dx
                    val y1 = tokens.number() + dy
                    controlX = tokens.number() + dx
                    controlY = tokens.number() + dy
                    x = tokens.number() + dx
                    y = tokens.number() + dy
                    path.cubicTo(x1, y1, controlX, controlY, x, y)
                    last = 'C'
                    continue
                }
                'S' -> {
                    val x1 = if (last == 'C') 2 * x - controlX else x
                    val y1 = if (last == 'C') 2 * y - controlY else y
                    controlX = tokens.number() + dx
                    controlY = tokens.number() + dy
                    x = tokens.number() + dx
                    y = tokens.number() + dy
                    path.cubicTo(x1, y1, controlX, controlY, x, y)
                    last = 'C'
                    continue
                }
                'Q' -> {
                    controlX = tokens.number() + dx
                    controlY = tokens.number() + dy
                    x = tokens.number() + dx
                    y = tokens.number() + dy
                    path.quadTo(controlX, controlY, x, y)
                    last = 'Q'
                    continue
                }
                'T' -> {
                    controlX = if (last == 'Q') 2 * x - controlX else x
                    controlY = if (last == 'Q') 2 * y - controlY else y
                    x = tokens.number() + dx
                    y = tokens.number() + dy
                    path.quadTo(controlX, controlY, x, y)
                    last = 'Q'
                    continue
                }
                'A' -> {
                    val radiusX = tokens.number()
                    val radiusY = tokens.number()
                    val rotation = tokens.number()
                    val large = tokens.flag()
                    val sweep = tokens.flag()
                    val endX = tokens.number() + dx
                    val endY = tokens.number() + dy
                    arc(path, x, y, endX, endY, radiusX, radiusY, rotation, large, sweep)
                    x = endX
                    y = endY
                }
                'Z' -> {
                    path.close()
                    x = startX
                    y = startY
                    last = 'Z'
                    if (tokens.more() && !tokens.atCommand()) command = 'L'
                    continue
                }
                else -> return path
            }
            last = command.uppercaseChar()
        }
        return path
    }

    private fun arc(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, rx0: Float, ry0: Float, degrees: Float, large: Boolean, sweep: Boolean) {
        if (x0 == x1 && y0 == y1) return
        var rx = abs(rx0.toDouble())
        var ry = abs(ry0.toDouble())
        if (rx == 0.0 || ry == 0.0) {
            path.lineTo(x1, y1)
            return
        }
        val angle = degrees * PI / 180
        val cosA = cos(angle)
        val sinA = sin(angle)
        val halfX = (x0 - x1) / 2.0
        val halfY = (y0 - y1) / 2.0
        val px = cosA * halfX + sinA * halfY
        val py = -sinA * halfX + cosA * halfY
        val lambda = (px * px) / (rx * rx) + (py * py) / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        val top = rx * rx * ry * ry - rx * rx * py * py - ry * ry * px * px
        val bottom = rx * rx * py * py + ry * ry * px * px
        var factor = sqrt(maxOf(0.0, top / bottom))
        if (large == sweep) factor = -factor
        val cxPrime = factor * rx * py / ry
        val cyPrime = -factor * ry * px / rx
        val cx = cosA * cxPrime - sinA * cyPrime + (x0 + x1) / 2.0
        val cy = sinA * cxPrime + cosA * cyPrime + (y0 + y1) / 2.0
        val start = atan2((py - cyPrime) / ry, (px - cxPrime) / rx)
        var delta = atan2((-py - cyPrime) / ry, (-px - cxPrime) / rx) - start
        if (sweep && delta < 0) delta += 2 * PI
        if (!sweep && delta > 0) delta -= 2 * PI
        val pieces = ceil(abs(delta) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = delta / pieces
        val handle = 4.0 / 3.0 * tan(step / 4)
        var theta = start
        repeat(pieces) {
            val cos1 = cos(theta)
            val sin1 = sin(theta)
            val cos2 = cos(theta + step)
            val sin2 = sin(theta + step)
            val e1x = -rx * sin1 * handle
            val e1y = ry * cos1 * handle
            val e2x = rx * sin2 * handle
            val e2y = -ry * cos2 * handle
            val ax = rx * cos1 + e1x
            val ay = ry * sin1 + e1y
            val bx = rx * cos2 + e2x
            val by = ry * sin2 + e2y
            val ex = rx * cos2
            val ey = ry * sin2
            path.cubicTo(
                (cx + cosA * ax - sinA * ay).toFloat(),
                (cy + sinA * ax + cosA * ay).toFloat(),
                (cx + cosA * bx - sinA * by).toFloat(),
                (cy + sinA * bx + cosA * by).toFloat(),
                (cx + cosA * ex - sinA * ey).toFloat(),
                (cy + sinA * ex + cosA * ey).toFloat()
            )
            theta += step
        }
    }

    private class Tokens(private val text: String) {
        private var at = 0

        private fun skip() {
            while (at < text.length && (text[at] == ' ' || text[at] == ',' || text[at] == '\n' || text[at] == '\t')) at++
        }

        fun more(): Boolean {
            skip()
            return at < text.length
        }

        fun atCommand(): Boolean {
            skip()
            return at < text.length && text[at].isLetter() && text[at] != 'e' && text[at] != 'E'
        }

        fun command(): Char = text[at++]

        fun flag(): Boolean {
            skip()
            return text[at++] == '1'
        }

        fun number(): Float {
            skip()
            val start = at
            if (at < text.length && (text[at] == '-' || text[at] == '+')) at++
            var dot = false
            while (at < text.length) {
                val char = text[at]
                if (char.isDigit()) {
                    at++
                } else if (char == '.' && !dot) {
                    dot = true
                    at++
                } else if ((char == 'e' || char == 'E') && at + 1 < text.length) {
                    at++
                    if (text[at] == '-' || text[at] == '+') at++
                } else {
                    break
                }
            }
            return text.substring(start, at).toFloatOrNull() ?: 0f
        }
    }
}
