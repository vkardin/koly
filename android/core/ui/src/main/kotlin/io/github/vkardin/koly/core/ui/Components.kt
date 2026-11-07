package io.github.vkardin.koly.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vkardin.koly.core.model.DAY_MINUTES
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.Segment
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Status dot: brightness differs per state, "maybe" is hatched. */
@Composable
fun StatusDot(state: PowerState, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val c = LocalKolyColors.current
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        when (state) {
            PowerState.ON -> {
                drawCircle(c.on.copy(alpha = 0.25f), r)
                drawCircle(c.on, r * 0.72f)
            }
            PowerState.OFF -> {
                drawCircle(c.off, r)
                drawCircle(c.textSecondary, r * 0.72f, style = Stroke(1.5.dp.toPx()))
            }
            PowerState.MAYBE -> {
                val clip = Path().apply { addOval(Rect(Offset.Zero, this@Canvas.size)) }
                clipPath(clip) { drawHatch(Rect(Offset.Zero, this@Canvas.size), c.maybeBase, c.maybeStripe, 4.dp.toPx()) }
            }
        }
    }
}

/** Dot, icon-free label of the state: "Світло є". */
@Composable
fun StatusLabel(state: PowerState, modifier: Modifier = Modifier) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(state)
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(KolyText.stateIcon(state)),
            contentDescription = null,
            tint = c.stateText(state),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            KolyText.stateLabel(ctx, state),
            style = MaterialTheme.typography.titleMedium,
            color = c.stateText(state),
        )
    }
}

/** 24-hour ring with midnight at the top, a "now" marker and free content in the middle. */
@Composable
fun DayRing(
    segments: List<Segment>,
    nowMinutes: Double?,
    modifier: Modifier = Modifier,
    stroke: Dp = 22.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    val description = remember(segments) { KolyText.describeSegments(ctx, segments) }
    val tickStyle = MaterialTheme.typography.labelSmall
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val d = min(size.width, size.height) - sw - 8.dp.toPx()
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            drawArc(c.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            for (s in segments) {
                val start = s.start * 360f / DAY_MINUTES - 90f
                val sweep = s.length * 360f / DAY_MINUTES
                when (s.state) {
                    PowerState.ON -> drawArc(c.on, start, sweep, false, topLeft, arcSize, style = Stroke(sw))
                    PowerState.OFF -> drawArc(c.off, start, sweep, false, topLeft, arcSize, style = Stroke(sw))
                    PowerState.MAYBE -> {
                        drawArc(c.maybeBase, start, sweep, false, topLeft, arcSize, style = Stroke(sw))
                        // Dashes across the ring stand in for the hatching.
                        var a = start
                        val dash = 2.2f
                        while (a < start + sweep) {
                            drawArc(c.maybeStripe, a, min(dash, start + sweep - a), false, topLeft, arcSize, style = Stroke(sw))
                            a += dash * 2
                        }
                    }
                }
            }
            val cx = size.width / 2
            val cy = size.height / 2
            val r = d / 2
            // Hour ticks.
            for (h in 0 until 24) {
                val ang = Math.toRadians(h * 15.0 - 90.0)
                val inner = r - sw / 2 - (if (h % 6 == 0) 8.dp.toPx() else 4.dp.toPx())
                val outer = r - sw / 2 - 2.dp.toPx()
                drawLine(
                    c.textSecondary.copy(alpha = if (h % 6 == 0) 0.8f else 0.35f),
                    Offset(cx + inner * cos(ang).toFloat(), cy + inner * sin(ang).toFloat()),
                    Offset(cx + outer * cos(ang).toFloat(), cy + outer * sin(ang).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (nowMinutes != null) {
                val ang = Math.toRadians(nowMinutes * 360.0 / DAY_MINUTES - 90.0)
                val p = Offset(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat())
                drawCircle(c.background, sw / 2 + 3.dp.toPx(), p)
                drawCircle(c.marker, sw / 2 - 1.dp.toPx(), p)
            }
        }
        center()
    }
}

/** The "Нитка" mark: Є with a glowing amber filament as its middle bar. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val c = LocalKolyColors.current
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val s = this.size.minDimension / 64f
        val arc = Path().apply {
            // Arc of Є: from the top right, around the left, to the bottom right.
            addArc(Rect(Offset(8f * s, 8f * s), Size(48f * s, 48f * s)), -55f, -250f)
        }
        drawPath(arc, c.mark, style = Stroke(7f * s, cap = StrokeCap.Round))
        drawLine(Color(0xFFFFC24A).copy(alpha = 0.35f), Offset(17f * s, 32f * s), Offset(39f * s, 32f * s), 10f * s, StrokeCap.Round)
        drawLine(Color(0xFFFFC24A), Offset(17f * s, 32f * s), Offset(39f * s, 32f * s), 6f * s, StrokeCap.Round)
        drawLine(Color(0xFFFFE3A3), Offset(17f * s, 32f * s), Offset(39f * s, 32f * s), 2f * s, StrokeCap.Round)
    }
}

/** Text logo: "Коли Є" with an amber Є. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val c = LocalKolyColors.current
    val name = stringResource(R.string.app_name)
    val text = buildAnnotatedString {
        val idx = name.lastIndexOf('Є').takeIf { it >= 0 } ?: name.lastIndexOf("Ye").takeIf { it >= 0 }
        if (idx == null) {
            append(name)
        } else {
            append(name.substring(0, idx))
            withStyle(SpanStyle(color = Color(0xFFFFC24A))) { append(name.substring(idx)) }
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LogoMark(size = 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = c.text)
    }
}

@Composable
fun KolyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalKolyColors.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.card) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Small rounded tag. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = LocalKolyColors.current.track) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = LocalKolyColors.current.text,
    )
}

/** Reserved banner space; only for statistics and settings screens, off in v1. */
object AdsConfig {
    const val ENABLED = false
}

@Composable
fun AdSlot(modifier: Modifier = Modifier) {
    if (!AdsConfig.ENABLED) return
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(LocalKolyColors.current.track),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.ad_slot_label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
