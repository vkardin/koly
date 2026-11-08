package io.github.vkardin.koly.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.Segment
import io.github.vkardin.koly.core.model.formatCountdown
import io.github.vkardin.koly.core.model.formatHm
import io.github.vkardin.koly.core.ui.KolyText
import java.time.Instant
import io.github.vkardin.koly.core.ui.R as UiR

/** Refreshes every widget of the app and keeps the minute tick going while widgets exist. */
object KolyWidgets {
    suspend fun updateAll(context: Context) {
        for (w in listOf(CardsSmallWidget(), CardsMediumWidget(), CardsLargeWidget(), RingWidget(), PillWidget(), TextWidget())) {
            runCatching { w.updateAll(context) }
        }
        WidgetTicker.schedule(context)
    }
}

// ---------------------------------------------------------------- colors and text

/** Colors of the "Картки" style in its dark and light variants. */
private data class CardStyle(
    val background: Int,
    val badge: Int,
    val row: Int,
    val text: Color,
    val secondary: Color,
    val on: Color,
    val off: Color,
    val maybe: Color,
    val total: Color,
    val palette: WidgetPalette,
) {
    fun state(s: PowerState) = when (s) {
        PowerState.ON -> on
        PowerState.OFF -> off
        PowerState.MAYBE -> maybe
    }

    companion object {
        val DARK = CardStyle(
            background = R.drawable.widget_bg_cards,
            badge = R.drawable.widget_badge_dark,
            row = R.drawable.widget_row_dark,
            text = Color(0xFFF4F6FB),
            secondary = Color(0xFFAEB6CA),
            on = Color(0xFFFFC24A),
            off = Color(0xFFC9D0E4),
            maybe = Color(0xFFE0B860),
            total = Color(0xFFFFC24A),
            palette = WidgetPalette.CARDS,
        )
        val LIGHT = CardStyle(
            background = R.drawable.widget_bg_cards_light,
            badge = R.drawable.widget_badge_light,
            row = R.drawable.widget_row_light,
            text = Color(0xFF141A2B),
            secondary = Color(0xFF545D75),
            on = Color(0xFF8A5A00),
            off = Color(0xFF3A4463),
            maybe = Color(0xFF7A5200),
            total = Color(0xFF8A5A00),
            palette = WidgetPalette.CARDS.copy(off = 0xFF3A4463.toInt(), maybeBase = 0xFF3A4463.toInt(), maybeStripe = 0xFFD9A33A.toInt(), track = 0xFFE3E6EF.toInt(), marker = 0xFF141A2B.toInt()),
        )

        fun of(context: Context, theme: WidgetTheme) = when (theme) {
            WidgetTheme.DARK -> DARK
            WidgetTheme.LIGHT -> LIGHT
            WidgetTheme.SYSTEM -> if (isNight(context)) DARK else LIGHT
        }
    }
}

private fun dotColor(s: PowerState) = when (s) {
    PowerState.ON -> Color(0xFFFFC24A)
    PowerState.OFF -> Color(0xFF6B7697)
    PowerState.MAYBE -> Color(0xFFB98E3A)
}

private fun isNight(context: Context) =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private fun GlanceModifier.openOnClick(context: Context): GlanceModifier =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { clickable(actionStartActivity(it)) } ?: this

private fun style(color: Color, size: TextUnit, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun style(color: ColorProvider, size: TextUnit, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** "3 год", "3 год 30 хв" or "45 хв". */
private fun duration(context: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> context.getString(UiR.string.duration_hm, h, m)
        h > 0 -> context.getString(UiR.string.duration_h, h)
        else -> context.getString(UiR.string.duration_m, m)
    }
}

/** Whole minutes left until the next change, rounded up. */
private fun minutesLeft(v: PlaceView): Int? {
    val d = v.status.untilChange(Instant.now()) ?: return null
    return ((d.seconds.coerceAtLeast(0) + 59) / 60).toInt()
}

/** "2:18", refreshed every minute. */
private fun shortCountdown(v: PlaceView): String? =
    v.status.untilChange(Instant.now())?.takeIf { v.hasData }?.let(::formatCountdown)

/** "до відключення 2 год 18 хв". */
private fun untilChangeText(context: Context, v: PlaceView): String? {
    if (!v.hasData) return null
    val left = minutesLeft(v) ?: return context.getString(UiR.string.no_changes_today)
    val text = duration(context, left)
    return context.getString(
        when (v.status.nextState) {
            PowerState.OFF -> UiR.string.w_until_off
            PowerState.MAYBE -> UiR.string.w_until_maybe
            else -> UiR.string.w_until_on
        },
        text,
    )
}

private fun stateText(context: Context, v: PlaceView): String = when {
    v.noSchedules -> context.getString(UiR.string.no_schedules)
    v.region == null -> context.getString(UiR.string.region_unavailable)
    !v.status.hasToday -> context.getString(UiR.string.no_data_today)
    else -> KolyText.stateLabel(context, v.status.state)
}

private fun untilText(context: Context, v: PlaceView): String? =
    v.status.changeAtMinutes?.takeIf { v.hasData }?.let { context.getString(UiR.string.until_time, formatHm(it)) }

private fun tomorrowText(context: Context, v: PlaceView): String = when {
    !v.status.hasTomorrow -> context.getString(UiR.string.tomorrow_not_published)
    v.status.outagesTomorrow == 0 -> context.getString(UiR.string.tomorrow_no_outages)
    else -> context.resources.getQuantityString(UiR.plurals.tomorrow_outages, v.status.outagesTomorrow, v.status.outagesTomorrow)
}

private fun updatedText(context: Context, model: WidgetModel, v: PlaceView?): String {
    val parts = mutableListOf(context.getString(UiR.string.updated_ago, KolyText.ago(context, model.checkedAt, model.now.instant)))
    if (model.offline) parts += context.getString(UiR.string.offline)
    if (v?.stale == true) parts += context.getString(UiR.string.data_stale)
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Dot(state: PowerState, size: Int = 10) {
    Image(
        ImageProvider(R.drawable.widget_dot),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ColorProvider(dotColor(state))),
        modifier = GlanceModifier.size(size.dp),
    )
}

@Composable
private fun Badge(text: String, s: CardStyle) {
    Box(GlanceModifier.background(ImageProvider(s.badge)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = style(s.text, 11.sp, true), maxLines = 1)
    }
}

@Composable
private fun DayImage(segments: List<Segment>, now: Double?, widthDp: Float, heightDp: Float, palette: WidgetPalette) {
    val ctx = LocalContext.current
    val d = ctx.resources.displayMetrics.density
    Image(
        ImageProvider(WidgetBitmaps.bar(segments, now, (widthDp * d).toInt(), (heightDp * d).toInt(), palette, d)),
        contentDescription = KolyText.describeSegments(ctx, segments),
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.width(widthDp.dp).height(heightDp.dp),
    )
}

/** Header: place on the left, group badge on the right. */
@Composable
private fun Header(v: PlaceView, s: CardStyle, compact: Boolean) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(v.place.label, style = style(s.text, if (compact) 13.sp else 15.sp, true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Badge(if (compact) v.place.group else ctx.getString(UiR.string.group_value, v.place.group), s)
    }
}

/** Status line: dot, "Світло є" and, on the right, the big countdown when there is room. */
@Composable
private fun StatusBlock(v: PlaceView, s: CardStyle, bigCountdown: Boolean) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (v.hasData) {
                    Dot(v.status.state)
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(stateText(ctx, v), style = style(if (v.hasData) s.state(v.status.state) else s.text, 14.sp, true), maxLines = 2)
            }
            untilChangeText(ctx, v)?.let { Text(it, style = style(s.secondary, 12.sp), maxLines = 1) }
        }
        if (bigCountdown) shortCountdown(v)?.let { Text(it, style = style(s.text, 28.sp, true), maxLines = 1) }
    }
}

/** Outage windows of a day with their durations and the total. */
@Composable
private fun IntervalList(
    title: String,
    segments: List<Segment>,
    nowMinutes: Double?,
    s: CardStyle,
    maxRows: Int,
    showTotal: Boolean,
) {
    val ctx = LocalContext.current
    val outages = segments.filter { it.state != PowerState.ON }
    // A widget Column holds at most 10 children, so rows are capped and grouped.
    val rows = maxRows.coerceIn(1, 6)
    Column(GlanceModifier.fillMaxWidth()) {
        Text(title, style = style(s.text, 12.sp, true), maxLines = 1)
        if (outages.isEmpty()) {
            Text(ctx.getString(UiR.string.no_more_outages), style = style(s.secondary, 12.sp), maxLines = 1)
            return@Column
        }
        // When space is short, skip the outages that are already over.
        val visible = if (outages.size > rows && nowMinutes != null) {
            outages.filter { it.end > nowMinutes }.ifEmpty { outages }.take(rows)
        } else {
            outages.take(rows)
        }
        for (o in visible) {
            val past = nowMinutes != null && o.end <= nowMinutes
            val current = nowMinutes != null && nowMinutes >= o.start && nowMinutes < o.end
            val color = if (past) s.secondary else s.text
            Column(GlanceModifier.fillMaxWidth().padding(top = 3.dp)) {
                Row(
                    GlanceModifier.fillMaxWidth().background(ImageProvider(s.row)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(o.state, 7)
                    Spacer(GlanceModifier.width(6.dp))
                    val range = formatHm(o.start) + "–" + formatHm(o.end, allowMidnightEnd = true)
                    val suffix = if (o.state == PowerState.MAYBE) " · " + ctx.getString(UiR.string.maybe_short) else ""
                    Text(range + suffix, style = style(color, 13.sp, current), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(duration(ctx, o.length), style = style(s.secondary, 12.sp), maxLines = 1)
                }
            }
        }
        if (showTotal) {
            val off = segments.filter { it.state == PowerState.OFF }.sumOf { it.length }
            if (off > 0) {
                Text(
                    ctx.getString(UiR.string.total_off, duration(ctx, off)),
                    style = style(s.total, 13.sp, true),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NoPlace(s: CardStyle = CardStyle.DARK) {
    val ctx = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().background(ImageProvider(s.background)).padding(12.dp).openOnClick(ctx),
        contentAlignment = Alignment.Center,
    ) {
        Text(ctx.getString(UiR.string.widget_no_places), style = style(s.text, 13.sp))
    }
}

/** Size buckets: Glance renders the largest one that fits, so resizing changes the layout. */
private val SIZES = setOf(
    DpSize(110.dp, 40.dp), DpSize(250.dp, 40.dp),
    DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp), DpSize(250.dp, 110.dp),
    DpSize(110.dp, 180.dp), DpSize(250.dp, 180.dp),
    DpSize(110.dp, 260.dp), DpSize(250.dp, 260.dp), DpSize(250.dp, 340.dp),
)

abstract class KolyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val model = loadWidgetModel(context, appWidgetId)
        provideContent { Content(model) }
    }

    @Composable
    abstract fun Content(model: WidgetModel)
}

// ---------------------------------------------------------------- «Картки», one place

/** Rows of 24 dp fit into the height left after the fixed parts. */
private fun rowsFor(heightDp: Float): Int = (heightDp / 24f).toInt().coerceAtLeast(0)

@Composable
private fun PlaceCard(model: WidgetModel) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val s = CardStyle.of(ctx, model.theme)
    val v = model.chosen ?: return NoPlace(s)
    val w = size.width.value
    val h = size.height.value
    val inner = w - 24
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(s.background)).padding(12.dp).openOnClick(ctx)) {
        if (h < 80) {
            // One line: place, state and countdown.
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (v.hasData) Dot(v.status.state)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    if (w >= 180) v.place.label + " · " + stateText(ctx, v) else v.place.label,
                    style = style(s.text, 13.sp, true),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                shortCountdown(v)?.let { Text(it, style = style(s.text, 16.sp, true)) }
            }
            return@Column
        }
        Header(v, s, compact = w < 180)
        Spacer(GlanceModifier.height(6.dp))
        if (w < 180) {
            // Narrow: state, big countdown and the time of change stacked.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (v.hasData) {
                    Dot(v.status.state)
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(stateText(ctx, v), style = style(if (v.hasData) s.state(v.status.state) else s.text, 13.sp, true), maxLines = 2)
            }
            shortCountdown(v)?.let { Text(it, style = style(s.text, 30.sp, true), maxLines = 1) }
            untilText(ctx, v)?.let { Text(it, style = style(s.secondary, 12.sp), maxLines = 1) }
            if (h >= 180 && v.hasData) {
                IntervalList(ctx.getString(UiR.string.today), v.status.today, model.now.minutes, s, rowsFor(h - 170), showTotal = h >= 220)
            }
            return@Column
        }
        StatusBlock(v, s, bigCountdown = true)
        if (!v.hasData) return@Column
        // Grouped so that no widget Column gets more than 10 children.
        Column(GlanceModifier.fillMaxWidth()) {
            if (h >= 110) {
                Box(GlanceModifier.padding(top = 6.dp)) { DayImage(v.status.today, model.now.minutes, inner, 8f, s.palette) }
            }
            if (h >= 170) {
                val hasTomorrow = v.status.tomorrow != null && h >= 300
                val rowsToday = rowsFor(h - 150 - (if (hasTomorrow) 90 else 0) - 20)
                Box(GlanceModifier.padding(top = 6.dp)) {
                    IntervalList(ctx.getString(UiR.string.today), v.status.today, model.now.minutes, s, rowsToday, showTotal = true)
                }
                if (hasTomorrow) {
                    Box(GlanceModifier.padding(top = 6.dp)) {
                        IntervalList(ctx.getString(UiR.string.tomorrow), v.status.tomorrow!!, null, s, 2, showTotal = false)
                    }
                } else if (h >= 260) {
                    Text(tomorrowText(ctx, v), style = style(s.secondary, 11.sp), maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp))
                }
            }
        }
        if (h >= 220) {
            Spacer(GlanceModifier.defaultWeight())
            Text(updatedText(ctx, model, v), style = style(s.secondary, 10.sp), maxLines = 1)
        }
    }
}

class CardsSmallWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) = PlaceCard(model)
}

class CardsMediumWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) = PlaceCard(model)
}

// ---------------------------------------------------------------- «Картки», several places

class CardsLargeWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val s = CardStyle.of(ctx, model.theme)
        if (model.views.isEmpty()) return NoPlace(s)
        val w = size.width.value
        val h = size.height.value
        val inner = w - 24
        val perPlace = 50f
        val places = ((h - 40) / perPlace).toInt().coerceIn(1, minOf(4, model.views.size))
        // Height left after the places goes to the outage list of the first place.
        val spare = h - 40 - places * perPlace
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(s.background)).padding(12.dp).openOnClick(ctx)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.getString(UiR.string.app_name), style = style(s.total, 14.sp, true), modifier = GlanceModifier.defaultWeight())
                if (w >= 220) Text(updatedText(ctx, model, null), style = style(s.secondary, 10.sp), maxLines = 1)
            }
            for (v in model.views.take(places)) {
                Column(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (v.hasData) {
                            Dot(v.status.state, 9)
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        Column(GlanceModifier.defaultWeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(v.place.label, style = style(s.text, 13.sp, true), maxLines = 1)
                                Spacer(GlanceModifier.width(6.dp))
                                Badge(v.place.group, s)
                            }
                            Text(untilChangeText(ctx, v) ?: stateText(ctx, v), style = style(s.secondary, 11.sp), maxLines = 1)
                        }
                        shortCountdown(v)?.let { Text(it, style = style(s.state(v.status.state), 18.sp, true)) }
                    }
                    if (v.hasData) {
                        Box(GlanceModifier.padding(top = 3.dp)) { DayImage(v.status.today, model.now.minutes, inner, 8f, s.palette) }
                    }
                }
            }
            val first = model.views.first()
            if (spare >= 70 && first.hasData) {
                IntervalList(
                    ctx.getString(UiR.string.today) + " · " + first.place.label,
                    first.status.today,
                    model.now.minutes,
                    s,
                    rowsFor(spare - 50).coerceAtLeast(1),
                    showTotal = true,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- «Мінімал»

/** Round ring; on a wide widget the ring sits on the left and details on the right. */
class RingWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace(if (isNight(ctx)) CardStyle.DARK else CardStyle.LIGHT)
            val w = size.width.value
            val h = size.height.value
            val wide = w >= h * 1.6f && w >= 220
            val side = if (wide) h else minOf(w, h)
            // Ring bitmaps are capped in size: every size bucket carries its own copy.
            val ringPx = (side * ctx.resources.displayMetrics.density).toInt().coerceAtMost(420)
            val onSurface = GlanceTheme.colors.onSurface
            val variant = GlanceTheme.colors.onSurfaceVariant
            val ring = @Composable {
                Box(
                    GlanceModifier.size(side.dp).background(GlanceTheme.colors.widgetBackground).cornerRadius((side / 2).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (v.hasData) {
                        Image(
                            ImageProvider(
                                WidgetBitmaps.ring(v.status.today, model.now.minutes, ringPx, WidgetPalette.minimal(ctx, isNight(ctx)), ringPx / side, stroke = side / 13f),
                            ),
                            contentDescription = KolyText.describeSegments(ctx, v.status.today),
                            modifier = GlanceModifier.size(side.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!wide) Text(v.place.label, style = style(variant, 11.sp), maxLines = 1)
                        val cd = shortCountdown(v)
                        if (cd != null) {
                            Text(cd, style = style(onSurface, if (side >= 140) 32.sp else 22.sp, true), maxLines = 1)
                        } else {
                            Text(stateText(ctx, v), style = style(onSurface, 12.sp), maxLines = 2)
                        }
                        if (!wide) untilText(ctx, v)?.let { Text(it, style = style(variant, 11.sp)) }
                    }
                }
            }
            if (!wide) {
                Box(GlanceModifier.fillMaxSize().openOnClick(ctx), contentAlignment = Alignment.Center) { ring() }
            } else {
                Row(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius((h / 2).dp).openOnClick(ctx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ring()
                    Column(GlanceModifier.defaultWeight().padding(end = 16.dp)) {
                        Text(v.place.label + " · " + ctx.getString(UiR.string.group_value, v.place.group), style = style(variant, 12.sp), maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (v.hasData) {
                                Dot(v.status.state)
                                Spacer(GlanceModifier.width(6.dp))
                            }
                            Text(stateText(ctx, v), style = style(onSurface, 15.sp, true), maxLines = 1)
                        }
                        untilChangeText(ctx, v)?.let { Text(it, style = style(onSurface, 12.sp), maxLines = 1) }
                        if (v.hasData && h >= 130) {
                            val outages = v.status.today.filter { it.state != PowerState.ON && it.end > model.now.minutes }
                            for (o in outages.take(if (h >= 180) 3 else 1)) {
                                Text(
                                    formatHm(o.start) + "–" + formatHm(o.end, allowMidnightEnd = true) + " · " + duration(ctx, o.length),
                                    style = style(variant, 12.sp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One-line pill; grows to two lines and a day bar when there is room. */
class PillWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace(if (isNight(ctx)) CardStyle.DARK else CardStyle.LIGHT)
            val w = size.width.value
            val h = size.height.value
            val onSurface = GlanceTheme.colors.onSurface
            val variant = GlanceTheme.colors.onSurfaceVariant
            Column(
                GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(if (h < 80) (h / 2).dp else 24.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp).openOnClick(ctx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state)
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    val main = when {
                        w < 160 -> v.place.label
                        w < 250 -> v.place.label + " · " + stateText(ctx, v)
                        else -> v.place.label + " · " + stateText(ctx, v) + (untilText(ctx, v)?.let { " · $it" } ?: "")
                    }
                    Text(main, style = style(onSurface, 13.sp, true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    shortCountdown(v)?.let { Text(it, style = style(onSurface, 16.sp, true)) }
                }
                if (h >= 80 && v.hasData) {
                    untilChangeText(ctx, v)?.let { Text(it, style = style(variant, 12.sp), maxLines = 1) }
                }
                if (h >= 110 && v.hasData) {
                    Spacer(GlanceModifier.height(6.dp))
                    DayImage(v.status.today, model.now.minutes, w - 32, 8f, WidgetPalette.minimal(ctx, isNight(ctx)))
                }
            }
        }
    }
}

/** Text right on the wallpaper; more lines appear as the widget grows. */
class TextWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace()
            val h = size.height.value
            val white = Color(0xFFFFFFFF)
            val soft = Color(0xD9FFFFFF)
            Column(GlanceModifier.fillMaxSize().padding(4.dp).openOnClick(ctx), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, 12)
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    Text(
                        stateText(ctx, v) + (shortCountdown(v)?.let { " · $it" } ?: ""),
                        style = style(white, if (h >= 90) 22.sp else 18.sp, true),
                        maxLines = 1,
                    )
                }
                Text(
                    listOfNotNull(v.place.label + " · " + ctx.getString(UiR.string.group_value, v.place.group), untilText(ctx, v)).joinToString(" · "),
                    style = style(soft, 12.sp),
                    maxLines = 1,
                )
                if (h >= 90 && v.hasData) {
                    val outages = v.status.today.filter { it.state != PowerState.ON && it.end > model.now.minutes }
                    Spacer(GlanceModifier.height(4.dp))
                    for (o in outages.take(((h - 70) / 18).toInt().coerceIn(1, 4))) {
                        Text(
                            formatHm(o.start) + "–" + formatHm(o.end, allowMidnightEnd = true) + " · " + duration(ctx, o.length),
                            style = style(white, 13.sp),
                            maxLines = 1,
                        )
                    }
                    if (outages.isEmpty()) Text(ctx.getString(UiR.string.no_more_outages), style = style(soft, 12.sp), maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- receivers

abstract class KolyWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetTicker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetPlaces.remove(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTicker.schedule(context)
    }
}

class CardsSmallReceiver : KolyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsSmallWidget()
}

class CardsMediumReceiver : KolyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsMediumWidget()
}

class CardsLargeReceiver : KolyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsLargeWidget()
}

class RingReceiver : KolyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}

class PillReceiver : KolyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PillWidget()
}

class TextReceiver : KolyWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TextWidget()
}

