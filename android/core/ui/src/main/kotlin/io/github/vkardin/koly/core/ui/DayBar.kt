package io.github.vkardin.koly.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vkardin.koly.core.model.DAY_MINUTES
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.Segment
import io.github.vkardin.koly.core.model.changeLabels
import io.github.vkardin.koly.core.model.formatHm

/** Diagonal hatching used for "possible outage" everywhere. */
fun DrawScope.drawHatch(rect: Rect, base: Color, stripe: Color, step: Float = 6.dp.toPx()) {
    drawRect(base, rect.topLeft, rect.size)
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        val w = step / 2f
        var x = rect.left - rect.height
        while (x < rect.right) {
            val p = Path().apply {
                moveTo(x, rect.bottom)
                lineTo(x + w, rect.bottom)
                lineTo(x + w + rect.height, rect.top)
                lineTo(x + rect.height, rect.top)
                close()
            }
            drawPath(p, stripe)
            x += step
        }
    }
}

/**
 * The 24-hour strip: segments, a "now" flag above the marker and labels at the
 * real moments of change below it. Durations are written inside dark segments when they fit.
 */
@Composable
fun DayBar(
    segments: List<Segment>,
    modifier: Modifier = Modifier,
    nowMinutes: Double? = null,
    showLabels: Boolean = true,
    showDurations: Boolean = true,
    barHeight: Dp = 28.dp,
) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    val description = remember(segments) { KolyText.describeSegments(ctx, segments) }
    val labels = remember(segments) { changeLabels(segments) }
    val labelStyle = MaterialTheme.typography.labelSmall
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        if (nowMinutes != null) {
            PositionedRow(listOf(nowMinutes / DAY_MINUTES)) {
                Text(
                    stringResourceSafe(R.string.now_at, formatHm(nowMinutes.toInt())),
                    style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
            }
        }
        Layout(
            content = {
                Canvas(Modifier) {
                    val r = CornerRadius(8.dp.toPx())
                    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), r)) }
                    clipPath(clip) {
                        for (s in segments) {
                            val left = size.width * s.start / DAY_MINUTES
                            val right = size.width * s.end / DAY_MINUTES
                            val rect = Rect(left, 0f, right, size.height)
                            when (s.state) {
                                PowerState.ON -> drawRect(c.on, rect.topLeft, rect.size)
                                PowerState.OFF -> drawRect(c.off, rect.topLeft, rect.size)
                                PowerState.MAYBE -> drawHatch(rect, c.maybeBase, c.maybeStripe)
                            }
                        }
                    }
                    if (nowMinutes != null) {
                        val x = size.width * (nowMinutes / DAY_MINUTES).toFloat()
                        drawRoundRect(
                            c.marker,
                            topLeft = Offset(x - 1.5.dp.toPx(), -3.dp.toPx()),
                            size = Size(3.dp.toPx(), size.height + 6.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
                if (showDurations) {
                    for (s in segments) {
                        if (s.state == PowerState.OFF) {
                            Text(
                                stringResourceSafe(R.string.hours_short, KolyText.hours(s.length)),
                                style = labelStyle,
                                color = c.offText,
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(barHeight),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = barHeight.roundToPx()
            val canvas = measurables.first().measure(Constraints.fixed(width, height))
            val offSegments = segments.filter { it.state == PowerState.OFF }
            val texts = measurables.drop(1).mapIndexed { i, m ->
                val seg = offSegments[i]
                val segWidth = width * seg.length / DAY_MINUTES
                val p = m.measure(Constraints())
                Triple(p, seg, segWidth)
            }
            layout(width, height) {
                canvas.place(0, 0)
                for ((p, seg, segWidth) in texts) {
                    if (p.width + 8.dp.roundToPx() <= segWidth) {
                        val left = width * seg.start / DAY_MINUTES
                        p.place(left + (segWidth - p.width) / 2, (height - p.height) / 2)
                    }
                }
            }
        }
        if (showLabels) {
            val positions = listOf(0.0) + labels.map { it.center.toDouble() / DAY_MINUTES }
            PositionedRow(positions, firstAtStart = true) {
                Text("00:00", style = labelStyle, color = c.textSecondary.copy(alpha = 0.6f))
                for (l in labels) Text(l.text, style = labelStyle, color = c.textSecondary)
            }
        }
    }
}

/**
 * Places each child centered at a fraction of the width, kept inside the bounds.
 * A child that would overlap the previous one is skipped.
 */
@Composable
private fun PositionedRow(
    fractions: List<Double>,
    firstAtStart: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints()) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val gap = 4.dp.roundToPx()
        layout(width, height) {
            var lastRight = Int.MIN_VALUE
            placeables.forEachIndexed { i, p ->
                val f = fractions.getOrElse(i) { 0.0 }
                val x = if (i == 0 && firstAtStart) 0 else (width * f).toInt() - p.width / 2
                val clamped = x.coerceIn(0, (width - p.width).coerceAtLeast(0))
                if (clamped >= lastRight + gap || lastRight == Int.MIN_VALUE) {
                    p.place(clamped, 0)
                    lastRight = clamped + p.width
                }
            }
        }
    }
}

@Composable
internal fun stringResourceSafe(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)
