package io.github.vkardin.koly.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
    val warn: Color,
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
            warn = Color(0xFFFF8A65),
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
            warn = Color(0xFFB3261E),
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

/** Shown on every widget when the data may be out of date: "офлайн · оновлено 2 год тому". */
private fun staleNote(context: Context, model: WidgetModel, v: PlaceView?): String? =
    if (model.offline || v?.stale == true || (v == null && model.views.any { it.stale })) updatedText(context, model, v) else null

private fun updatedText(context: Context, model: WidgetModel, v: PlaceView?): String {
    val parts = mutableListOf(context.getString(UiR.string.updated_ago, KolyText.ago(context, model.checkedAt, model.now.instant)))
    if (model.offline) parts += context.getString(UiR.string.offline)
    if (v?.stale == true) parts += context.getString(UiR.string.data_stale)
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------- building blocks

/**
 * Every widget keeps one layout at every size; only its scale changes.
 * [k] multiplies text, spacing and drawings relative to the design size.
 */
private data class Scale(val k: Float) {
    fun sp(v: Float): TextUnit = (v * k).sp
    fun dp(v: Float) = (v * k).dp
    fun px(v: Float) = v * k
}

private fun scaleFor(w: Float, h: Float, baseW: Float, baseH: Float, min: Float = 0.72f, max: Float = 1.35f) =
    Scale(minOf(w / baseW, h / baseH).coerceIn(min, max))

@Composable
private fun Dot(state: PowerState, size: Dp = 10.dp) {
    Image(
        ImageProvider(R.drawable.widget_dot),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ColorProvider(dotColor(state))),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun Badge(text: String, s: CardStyle, sc: Scale) {
    Box(GlanceModifier.background(ImageProvider(s.badge)).padding(horizontal = sc.dp(7f), vertical = sc.dp(2f))) {
        Text(text, style = style(s.text, sc.sp(11f), true), maxLines = 1)
    }
}

@Composable
private fun DayImage(segments: List<Segment>, now: Double?, widthDp: Float, heightDp: Float, palette: WidgetPalette) {
    val ctx = LocalContext.current
    val d = ctx.resources.displayMetrics.density
    Image(
        ImageProvider(WidgetBitmaps.bar(segments, now, (widthDp * d).toInt().coerceAtLeast(1), (heightDp * d).toInt().coerceAtLeast(1), palette, d)),
        contentDescription = KolyText.describeSegments(ctx, segments),
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.width(widthDp.dp).height(heightDp.dp),
    )
}

/** One outage row: "10:30–17:30 · 7 год". */
@Composable
private fun OutageRow(o: Segment, nowMinutes: Double?, s: CardStyle, sc: Scale) {
    val ctx = LocalContext.current
    val past = nowMinutes != null && o.end <= nowMinutes
    val current = nowMinutes != null && nowMinutes >= o.start && nowMinutes < o.end
    Column(GlanceModifier.fillMaxWidth().padding(top = sc.dp(3f))) {
        Row(
            GlanceModifier.fillMaxWidth().background(ImageProvider(s.row)).padding(horizontal = sc.dp(8f), vertical = sc.dp(3f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(o.state, sc.dp(7f))
            Spacer(GlanceModifier.width(sc.dp(6f)))
            val range = formatHm(o.start) + "–" + formatHm(o.end, allowMidnightEnd = true)
            val suffix = if (o.state == PowerState.MAYBE) " · " + ctx.getString(UiR.string.maybe_short) else ""
            Text(range + suffix, style = style(if (past) s.secondary else s.text, sc.sp(13f), current), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text(duration(ctx, o.length), style = style(s.secondary, sc.sp(12f)), maxLines = 1)
        }
    }
}

/** Outages of a day, as many rows as fit, then "+N" and the total. */
@Composable
private fun OutageList(title: String, segments: List<Segment>, nowMinutes: Double?, s: CardStyle, sc: Scale, rows: Int, showTotal: Boolean) {
    val ctx = LocalContext.current
    val outages = segments.filter { it.state != PowerState.ON }
    Column(GlanceModifier.fillMaxWidth()) {
        Text(title, style = style(s.text, sc.sp(12f), true), maxLines = 1)
        if (outages.isEmpty()) {
            Text(ctx.getString(UiR.string.no_more_outages), style = style(s.secondary, sc.sp(12f)), maxLines = 1)
            return@Column
        }
        val limit = rows.coerceIn(1, 6)
        // When space is short, the outages that are already over go first.
        val visible = if (outages.size > limit && nowMinutes != null) {
            outages.filter { it.end > nowMinutes }.ifEmpty { outages }.take(limit)
        } else {
            outages.take(limit)
        }
        for (o in visible) OutageRow(o, nowMinutes, s, sc)
        val hidden = outages.size - visible.size
        val off = segments.filter { it.state == PowerState.OFF }.sumOf { it.length }
        val footer = listOfNotNull(
            if (hidden > 0) "+$hidden" else null,
            if (showTotal && off > 0) ctx.getString(UiR.string.total_off, duration(ctx, off)) else null,
        ).joinToString(" · ")
        if (footer.isNotEmpty()) {
            Text(footer, style = style(s.total, sc.sp(12f), true), maxLines = 1, modifier = GlanceModifier.padding(top = sc.dp(4f)))
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

abstract class KolyWidget : GlanceAppWidget() {
    /** Exact size, so the one layout can be scaled to the real widget size. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val initial = loadWidgetModel(context, appWidgetId)
        val updates = widgetModelFlow(context, appWidgetId)
        provideContent {
            val model by updates.collectAsState(initial)
            Content(model)
        }
    }

    @Composable
    abstract fun Content(model: WidgetModel)
}

// ---------------------------------------------------------------- «Картки», one place

/**
 * One layout for every size: header with the group, status with the countdown,
 * day bar, today's outages with the total, and tomorrow when there is room.
 * Designed for 180×180 dp and scaled from there.
 */
@Composable
private fun PlaceCard(model: WidgetModel) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val s = CardStyle.of(ctx, model.theme)
    val v = model.chosen ?: return NoPlace(s)
    val w = size.width.value
    val h = size.height.value
    val sc = scaleFor(w, h, 180f, 180f)
    val pad = sc.px(12f)
    val inner = w - 2 * pad
    // Height of the fixed parts, to know how many outage rows fit.
    val fixed = sc.px(22f + 44f + 16f + 18f + 20f) + 2 * pad
    val rowH = sc.px(25f)
    val free = h - fixed
    val tomorrow = v.status.tomorrow
    val todayCount = v.status.today.count { it.state != PowerState.ON }
    val rowsToday = (free / rowH).toInt()
    val showTomorrow = tomorrow != null && free - todayCount * rowH > sc.px(18f) + 2 * rowH
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(s.background)).padding(pad.dp).openOnClick(ctx)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(v.place.label, style = style(s.text, sc.sp(15f), true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Badge(if (w >= 200) ctx.getString(UiR.string.group_value, v.place.group) else v.place.group, s, sc)
        }
        staleNote(ctx, model, v)?.let { Text(it, style = style(s.warn, sc.sp(10f), true), maxLines = 1) }
        Row(GlanceModifier.fillMaxWidth().padding(top = sc.dp(6f)), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, sc.dp(10f))
                        Spacer(GlanceModifier.width(sc.dp(6f)))
                    }
                    Text(stateText(ctx, v), style = style(if (v.hasData) s.state(v.status.state) else s.text, sc.sp(14f), true), maxLines = 1)
                }
                (untilChangeText(ctx, v) ?: untilText(ctx, v))?.let {
                    Text(it, style = style(s.secondary, sc.sp(11f)), maxLines = 2)
                }
            }
            shortCountdown(v)?.let { Text(it, style = style(s.text, sc.sp(28f), true), maxLines = 1) }
        }
        if (v.hasData) {
            Column(GlanceModifier.fillMaxWidth()) {
                Box(GlanceModifier.padding(top = sc.dp(6f))) { DayImage(v.status.today, model.now.minutes, inner, sc.px(8f), s.palette) }
                Box(GlanceModifier.padding(top = sc.dp(6f))) {
                    OutageList(ctx.getString(UiR.string.today), v.status.today, model.now.minutes, s, sc, rowsToday, showTotal = true)
                }
                if (showTomorrow) {
                    Box(GlanceModifier.padding(top = sc.dp(6f))) {
                        OutageList(ctx.getString(UiR.string.tomorrow), tomorrow!!, null, s, sc, ((free - todayCount * rowH - sc.px(40f)) / rowH).toInt(), showTotal = false)
                    }
                } else if (free - todayCount * rowH > sc.px(30f)) {
                    Text(tomorrowText(ctx, v), style = style(s.secondary, sc.sp(11f)), maxLines = 1, modifier = GlanceModifier.padding(top = sc.dp(4f)))
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (staleNote(ctx, model, v) == null && h - fixed - todayCount * rowH > sc.px(60f)) {
            Text(updatedText(ctx, model, v), style = style(s.secondary, sc.sp(10f)), maxLines = 1)
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

/** Every place gets the same row (name, group, countdown, mini bar); designed for 250×180 dp. */
class CardsLargeWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val s = CardStyle.of(ctx, model.theme)
        if (model.views.isEmpty()) return NoPlace(s)
        val w = size.width.value
        val h = size.height.value
        val sc = scaleFor(w, h, 250f, 180f, min = 0.68f)
        val pad = sc.px(12f)
        val inner = w - 2 * pad
        val perPlace = sc.px(48f)
        val places = ((h - 2 * pad - sc.px(22f)) / perPlace).toInt().coerceIn(1, minOf(4, model.views.size))
        val spare = h - 2 * pad - sc.px(22f) - places * perPlace
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(s.background)).padding(pad.dp).openOnClick(ctx)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.getString(UiR.string.app_name), style = style(s.total, sc.sp(14f), true), modifier = GlanceModifier.defaultWeight())
                val note = staleNote(ctx, model, null)
                Text(note ?: updatedText(ctx, model, null), style = style(if (note != null) s.warn else s.secondary, sc.sp(10f), note != null), maxLines = 1)
            }
            for (v in model.views.take(places)) {
                Column(GlanceModifier.fillMaxWidth().padding(top = sc.dp(6f))) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (v.hasData) {
                            Dot(v.status.state, sc.dp(9f))
                            Spacer(GlanceModifier.width(sc.dp(6f)))
                        }
                        Column(GlanceModifier.defaultWeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(v.place.label, style = style(s.text, sc.sp(13f), true), maxLines = 1)
                                Spacer(GlanceModifier.width(sc.dp(6f)))
                                Badge(v.place.group, s, sc)
                            }
                            Text(untilChangeText(ctx, v) ?: stateText(ctx, v), style = style(s.secondary, sc.sp(11f)), maxLines = 1)
                        }
                        shortCountdown(v)?.let { Text(it, style = style(s.state(v.status.state), sc.sp(18f), true)) }
                    }
                    if (v.hasData) {
                        Box(GlanceModifier.padding(top = sc.dp(3f))) { DayImage(v.status.today, model.now.minutes, inner, sc.px(7f), s.palette) }
                    }
                }
            }
            val first = model.views.first()
            if (spare >= sc.px(60f) && first.hasData) {
                Box(GlanceModifier.padding(top = sc.dp(8f))) {
                    OutageList(
                        ctx.getString(UiR.string.today) + " · " + first.place.label,
                        first.status.today,
                        model.now.minutes,
                        s,
                        sc,
                        ((spare - sc.px(40f)) / sc.px(25f)).toInt(),
                        showTotal = true,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- «Мінімал»

@Composable
private fun themedColor(p: ColorProvider): Color = p.getColor(LocalContext.current)

/** «Мінімал» colors: wallpaper colors when following the system, otherwise the chosen dark or light set. */
private data class MinimalColors(val background: ColorProvider, val text: ColorProvider, val secondary: ColorProvider, val palette: WidgetPalette)

@Composable
private fun minimalColors(model: WidgetModel): MinimalColors {
    val ctx = LocalContext.current
    if (model.theme == WidgetTheme.SYSTEM) {
        return MinimalColors(
            GlanceTheme.colors.widgetBackground,
            GlanceTheme.colors.onSurface,
            GlanceTheme.colors.onSurfaceVariant,
            WidgetPalette.minimal(ctx, isNight(ctx)),
        )
    }
    val s = CardStyle.of(ctx, model.theme)
    val bg = if (s == CardStyle.DARK) Color(0xFF1B2236) else Color(0xFFF7F8FB)
    return MinimalColors(ColorProvider(bg), ColorProvider(s.text), ColorProvider(s.secondary), s.palette)
}

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
            val mc = minimalColors(model)
            val onSurface = mc.text
            val variant = mc.secondary
            val ring = @Composable {
                Box(
                    GlanceModifier.size(side.dp).background(mc.background).cornerRadius((side / 2).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (v.hasData) {
                        Image(
                            ImageProvider(
                                WidgetBitmaps.ring(v.status.today, model.now.minutes, ringPx, mc.palette, ringPx / side, stroke = side / 13f),
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
                        if (!wide) {
                            val stale = staleNote(ctx, model, v) != null
                            val line = if (stale) ctx.getString(if (model.offline) UiR.string.offline else UiR.string.data_stale) else untilText(ctx, v)
                            line?.let { Text(it, style = if (stale) style(Color(0xFFFF8A65), 11.sp, true) else style(variant, 11.sp), maxLines = 1) }
                        }
                    }
                }
            }
            if (!wide) {
                Box(GlanceModifier.fillMaxSize().openOnClick(ctx), contentAlignment = Alignment.Center) { ring() }
            } else {
                Row(
                    GlanceModifier.fillMaxSize().background(mc.background).cornerRadius((h / 2).dp).openOnClick(ctx),
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
                        staleNote(ctx, model, v)?.let { Text(it, style = style(Color(0xFFFF8A65), 11.sp, true), maxLines = 1) }
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


/** The pill: always the same two lines and bar, scaled to the widget height; designed for 250×110 dp. */
class PillWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace(if (isNight(ctx)) CardStyle.DARK else CardStyle.LIGHT)
            val w = size.width.value
            val h = size.height.value
            // A one-row pill only has room for the first line.
            val oneLine = h < 70
            val sc = if (oneLine) scaleFor(w, h, 250f, 48f, min = 0.7f, max = 1.2f) else scaleFor(w, h, 250f, 100f)
            val mc = minimalColors(model)
            val onSurface = mc.text
            val variant = mc.secondary
            Column(
                GlanceModifier.fillMaxSize().background(mc.background)
                    .cornerRadius(if (oneLine) (h / 2).dp else 28.dp)
                    .padding(horizontal = sc.dp(16f), vertical = sc.dp(6f)).openOnClick(ctx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, sc.dp(10f))
                        Spacer(GlanceModifier.width(sc.dp(8f)))
                    }
                    Text(v.place.label + " · " + stateText(ctx, v), style = style(onSurface, sc.sp(14f), true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    shortCountdown(v)?.let { Text(it, style = style(onSurface, sc.sp(18f), true)) }
                }
                if (!oneLine && v.hasData) {
                    val note = staleNote(ctx, model, v)
                    (note ?: untilChangeText(ctx, v))?.let { Text(it, style = style(if (note != null) ColorProvider(Color(0xFFFF8A65)) else variant, sc.sp(12f)), maxLines = 1) }
                    Box(GlanceModifier.padding(top = sc.dp(6f))) {
                        DayImage(v.status.today, model.now.minutes, w - 2 * sc.px(16f), sc.px(8f), mc.palette)
                    }
                }
            }
        }
    }
}

/** Text right on the wallpaper: status, place, then the coming outages; scaled to the widget. */
class TextWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace()
            val w = size.width.value
            val h = size.height.value
            val sc = scaleFor(w, h, 220f, 110f, min = 0.75f, max = 1.4f)
            val white = Color(0xFFFFFFFF)
            val soft = Color(0xD9FFFFFF)
            val outages = v.status.today.filter { it.state != PowerState.ON && it.end > model.now.minutes }
            val rows = ((h - sc.px(56f)) / sc.px(18f)).toInt().coerceIn(0, 4)
            Column(GlanceModifier.fillMaxSize().padding(4.dp).openOnClick(ctx), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, sc.dp(12f))
                        Spacer(GlanceModifier.width(sc.dp(8f)))
                    }
                    Text(stateText(ctx, v) + (shortCountdown(v)?.let { " · $it" } ?: ""), style = style(white, sc.sp(20f), true), maxLines = 1)
                }
                Text(
                    listOfNotNull(v.place.label + " · " + ctx.getString(UiR.string.group_value, v.place.group), untilText(ctx, v)).joinToString(" · "),
                    style = style(soft, sc.sp(12f)),
                    maxLines = 1,
                )
                staleNote(ctx, model, v)?.let { Text(it, style = style(Color(0xFFFFB199), sc.sp(11f), true), maxLines = 1) }
                if (v.hasData && rows > 0) {
                    for (o in outages.take(rows)) {
                        Text(
                            formatHm(o.start) + "–" + formatHm(o.end, allowMidnightEnd = true) + " · " + duration(ctx, o.length),
                            style = style(white, sc.sp(13f)),
                            maxLines = 1,
                        )
                    }
                    if (outages.isEmpty()) Text(ctx.getString(UiR.string.no_more_outages), style = style(soft, sc.sp(12f)), maxLines = 1)
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

