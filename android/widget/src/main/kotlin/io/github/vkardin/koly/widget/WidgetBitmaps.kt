package io.github.vkardin.koly.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.createBitmap
import io.github.vkardin.koly.core.model.DAY_MINUTES
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.Segment
import io.github.vkardin.koly.core.model.changeLabels
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colors for widget drawings, as ARGB ints. */
data class WidgetPalette(
    val on: Int,
    val off: Int,
    val maybeBase: Int,
    val maybeStripe: Int,
    val track: Int,
    val marker: Int,
) {
    companion object {
        /** Dark substrate of the "Картки" style. */
        val CARDS = WidgetPalette(
            on = 0xFFFFC24A.toInt(),
            off = 0xFF2A3350.toInt(),
            maybeBase = 0xFF2A3350.toInt(),
            maybeStripe = 0xFF8A6A2B.toInt(),
            track = 0xFF222A40.toInt(),
            marker = 0xFFF4F6FB.toInt(),
        )

        /** "Мінімал": wallpaper colors on Android 12+, the amber of "light on" stays. */
        fun minimal(context: Context, dark: Boolean): WidgetPalette {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return if (dark) CARDS else CARDS.copy(off = 0xFF3A4463.toInt(), maybeBase = 0xFF3A4463.toInt(), track = 0x33141A2B, marker = 0xFF141A2B.toInt())
            }
            fun c(id: Int) = context.getColor(id)
            val off = if (dark) c(android.R.color.system_accent2_700) else c(android.R.color.system_accent2_600)
            return WidgetPalette(
                on = 0xFFFFC24A.toInt(),
                off = off,
                maybeBase = off,
                maybeStripe = if (dark) c(android.R.color.system_accent1_300) else c(android.R.color.system_accent1_400),
                track = if (dark) c(android.R.color.system_neutral1_800) else c(android.R.color.system_neutral1_100),
                marker = if (dark) c(android.R.color.system_neutral1_10) else c(android.R.color.system_neutral1_900),
            )
        }
    }
}

object WidgetBitmaps {
    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun hatch(canvas: Canvas, rect: RectF, p: WidgetPalette, step: Float) {
        canvas.drawRect(rect, paint(p.maybeBase))
        canvas.save()
        canvas.clipRect(rect)
        val stripe = paint(p.maybeStripe)
        var x = rect.left - rect.height()
        while (x < rect.right) {
            val path = Path().apply {
                moveTo(x, rect.bottom)
                lineTo(x + step / 2, rect.bottom)
                lineTo(x + step / 2 + rect.height(), rect.top)
                lineTo(x + rect.height(), rect.top)
                close()
            }
            canvas.drawPath(path, stripe)
            x += step
        }
        canvas.restore()
    }

    /** Horizontal day strip with an optional "now" marker. */
    fun bar(segments: List<Segment>, nowMinutes: Double?, width: Int, height: Int, p: WidgetPalette, density: Float): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        val markerPad = if (nowMinutes != null) 3 * density else 0f
        val barRect = RectF(0f, markerPad, w.toFloat(), h - markerPad)
        val radius = min(barRect.height() / 2, 6 * density)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(barRect, radius, radius, Path.Direction.CW) })
        for (s in segments) {
            val r = RectF(w * s.start / DAY_MINUTES.toFloat(), barRect.top, w * s.end / DAY_MINUTES.toFloat(), barRect.bottom)
            when (s.state) {
                PowerState.ON -> canvas.drawRect(r, paint(p.on))
                PowerState.OFF -> canvas.drawRect(r, paint(p.off))
                PowerState.MAYBE -> hatch(canvas, r, p, 5 * density)
            }
        }
        canvas.restore()
        if (nowMinutes != null) {
            val x = (w * nowMinutes / DAY_MINUTES).toFloat()
            val m = 1.5f * density
            canvas.drawRoundRect(RectF(x - m, 0f, x + m, h.toFloat()), m, m, paint(p.marker))
        }
        return bmp
    }

    /** Day strip with the real moments of change written below it. */
    fun barWithLabels(
        segments: List<Segment>,
        nowMinutes: Double?,
        width: Int,
        height: Int,
        p: WidgetPalette,
        density: Float,
        labelColor: Int,
    ): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val textSize = 10 * density
        val barHeight = (h - textSize - 4 * density).toInt().coerceAtLeast(4)
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(bar(segments, nowMinutes, w, barHeight, p, density), 0f, 0f, null)
        val text = paint(labelColor).apply { this.textSize = textSize }
        val baseline = h - 2 * density
        var lastRight = -1f
        val labels = listOf(Pair(0, "00:00")) + changeLabels(segments).map { it.center to it.text }
        labels.forEachIndexed { i, (minutes, label) ->
            val tw = text.measureText(label)
            val x = if (i == 0) 0f else (w * minutes / DAY_MINUTES.toFloat() - tw / 2).coerceIn(0f, w - tw)
            if (x >= lastRight + 4 * density) {
                text.alpha = if (i == 0) 150 else 255
                canvas.drawText(label, x, baseline, text)
                lastRight = x + tw
            }
        }
        return bmp
    }

    /** 24-hour ring, midnight at the top, with a "now" dot. */
    fun ring(segments: List<Segment>, nowMinutes: Double?, size: Int, p: WidgetPalette, density: Float, stroke: Float = 10f): Bitmap {
        val s = size.coerceAtLeast(1)
        val bmp = createBitmap(s, s)
        val canvas = Canvas(bmp)
        val sw = stroke * density
        val inset = sw / 2 + 4 * density
        val oval = RectF(inset, inset, s - inset, s - inset)
        val strokePaint = { color: Int -> paint(color).apply { style = Paint.Style.STROKE; strokeWidth = sw } }
        canvas.drawArc(oval, 0f, 360f, false, strokePaint(p.track))
        for (seg in segments) {
            val start = seg.start * 360f / DAY_MINUTES - 90f
            val sweep = seg.length * 360f / DAY_MINUTES
            when (seg.state) {
                PowerState.ON -> canvas.drawArc(oval, start, sweep, false, strokePaint(p.on))
                PowerState.OFF -> canvas.drawArc(oval, start, sweep, false, strokePaint(p.off))
                PowerState.MAYBE -> {
                    canvas.drawArc(oval, start, sweep, false, strokePaint(p.maybeBase))
                    var a = start
                    while (a < start + sweep) {
                        canvas.drawArc(oval, a, min(2.5f, start + sweep - a), false, strokePaint(p.maybeStripe))
                        a += 5f
                    }
                }
            }
        }
        if (nowMinutes != null) {
            val ang = Math.toRadians(nowMinutes * 360.0 / DAY_MINUTES - 90.0)
            val r = oval.width() / 2
            val cx = s / 2f + r * cos(ang).toFloat()
            val cy = s / 2f + r * sin(ang).toFloat()
            canvas.drawCircle(cx, cy, sw / 2 + 1.5f * density, paint(p.track))
            canvas.drawCircle(cx, cy, sw / 2 - 1f * density, paint(p.marker))
        }
        return bmp
    }
}
