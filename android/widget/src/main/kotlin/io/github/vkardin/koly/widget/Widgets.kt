package io.github.vkardin.koly.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
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
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.formatHm
import io.github.vkardin.koly.core.ui.KolyText
import java.time.Instant
import io.github.vkardin.koly.core.ui.R as UiR

/** Refreshes every widget of the app. */
object KolyWidgets {
    suspend fun updateAll(context: Context) {
        for (w in listOf(CardsSmallWidget(), CardsMediumWidget(), CardsLargeWidget(), RingWidget(), PillWidget(), TextWidget())) {
            runCatching { w.updateAll(context) }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

private object Cards {
    val text = Color(0xFFF4F6FB)
    val secondary = Color(0xFFAEB6CA)
    val on = Color(0xFFFFC24A)
    val off = Color(0xFFC9D0E4)
    val maybe = Color(0xFFE0B860)
    val warn = Color(0xFFFF8A65)

    fun state(s: PowerState) = when (s) {
        PowerState.ON -> on
        PowerState.OFF -> off
        PowerState.MAYBE -> maybe
    }
}

private fun dotColor(s: PowerState) = when (s) {
    PowerState.ON -> Color(0xFFFFC24A)
    PowerState.OFF -> Color(0xFF6B7697)
    PowerState.MAYBE -> Color(0xFFB98E3A)
}

private fun Color.argb(): Int =
    android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun isNight(context: Context) =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private fun GlanceModifier.openOnClick(context: Context): GlanceModifier =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { clickable(actionStartActivity(it)) } ?: this

private fun style(color: ColorProvider, size: TextUnit, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun style(color: Color, size: TextUnit, bold: Boolean = false) = style(ColorProvider(color), size, bold)

@Composable
private fun Dot(state: PowerState, size: Int = 10) {
    Image(
        ImageProvider(R.drawable.widget_dot),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ColorProvider(dotColor(state))),
        modifier = GlanceModifier.size(size.dp),
    )
}

/** Live countdown using a system Chronometer, so it ticks without waking the app. */
@Composable
private fun LiveCountdown(view: PlaceView, big: Boolean, color: Color) {
    val ctx = LocalContext.current
    val until = view.status.untilChange(Instant.now()) ?: return
    val rv = RemoteViews(ctx.packageName, if (big) R.layout.widget_countdown_big else R.layout.widget_countdown_small)
    rv.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + until.toMillis(), null, true)
    rv.setChronometerCountDown(R.id.countdown, true)
    rv.setTextColor(R.id.countdown, color.argb())
    Box(GlanceModifier.semantics { contentDescription = KolyText.countdownDescription(ctx, until) }) {
        AndroidRemoteViews(rv)
    }
}

private fun stateText(context: Context, v: PlaceView): String = when {
    v.noSchedules -> context.getString(UiR.string.no_schedules)
    v.region == null -> context.getString(UiR.string.region_unavailable)
    !v.status.hasToday -> context.getString(UiR.string.no_data_today)
    else -> KolyText.stateLabel(context, v.status.state)
}

private fun untilText(context: Context, v: PlaceView): String? =
    v.status.changeAtMinutes?.takeIf { v.hasData }?.let { context.getString(UiR.string.until_time, formatHm(it)) }

private fun subtitle(context: Context, v: PlaceView): String =
    v.place.label + " · " + context.getString(UiR.string.group_value, v.place.group)

/** "Без світла 18:00–22:00" for the next outage today. */
private fun nextOutageText(context: Context, v: PlaceView, nowMinutes: Double): String {
    val o = v.status.nextOutage(nowMinutes) ?: return context.getString(UiR.string.no_more_outages)
    return context.getString(
        if (o.state == PowerState.OFF) UiR.string.off_window else UiR.string.maybe_window,
        formatHm(o.start),
        formatHm(o.end, allowMidnightEnd = true),
    )
}

private fun tomorrowText(context: Context, v: PlaceView): String = when {
    !v.status.hasTomorrow -> context.getString(UiR.string.tomorrow_not_published)
    v.status.outagesTomorrow == 0 -> context.getString(UiR.string.tomorrow_no_outages)
    else -> context.resources.getQuantityString(UiR.plurals.tomorrow_outages, v.status.outagesTomorrow, v.status.outagesTomorrow)
}

private fun footerText(context: Context, model: WidgetModel, v: PlaceView?): String {
    val parts = mutableListOf(context.getString(UiR.string.updated_ago, KolyText.ago(context, model.checkedAt, model.now.instant)))
    if (model.offline) parts += context.getString(UiR.string.offline)
    if (v?.stale == true) parts += context.getString(UiR.string.data_stale)
    return parts.joinToString(" · ")
}

@Composable
private fun DayImage(segments: List<io.github.vkardin.koly.core.model.Segment>, now: Double?, widthDp: Float, heightDp: Float, labels: Boolean, palette: WidgetPalette, labelColor: Color) {
    val ctx = LocalContext.current
    val d = ctx.resources.displayMetrics.density
    val w = (widthDp * d).toInt()
    val h = (heightDp * d).toInt()
    val bmp = if (labels) {
        WidgetBitmaps.barWithLabels(segments, now, w, h, palette, d, labelColor.argb())
    } else {
        WidgetBitmaps.bar(segments, now, w, h, palette, d)
    }
    Image(
        ImageProvider(bmp),
        contentDescription = KolyText.describeSegments(ctx, segments),
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.width(widthDp.dp).height(heightDp.dp),
    )
}

@Composable
private fun NoPlace(dark: Boolean = true) {
    val ctx = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().background(ImageProvider(if (dark) R.drawable.widget_bg_cards else R.drawable.widget_bg_pill)).padding(12.dp).openOnClick(ctx),
        contentAlignment = Alignment.Center,
    ) {
        Text(ctx.getString(UiR.string.widget_no_places), style = style(Cards.text, 13.sp))
    }
}

/** Size buckets: Glance renders the largest one that fits, so resizing changes the layout. */
private val SIZES = setOf(
    DpSize(110.dp, 40.dp), DpSize(180.dp, 40.dp), DpSize(260.dp, 40.dp),
    DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp), DpSize(260.dp, 110.dp), DpSize(340.dp, 110.dp),
    DpSize(110.dp, 180.dp), DpSize(180.dp, 180.dp), DpSize(260.dp, 180.dp), DpSize(340.dp, 180.dp),
    DpSize(180.dp, 260.dp), DpSize(260.dp, 260.dp), DpSize(340.dp, 260.dp), DpSize(340.dp, 340.dp),
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

/** Adaptive card for one place; small and medium only differ in their default size. */
@Composable
private fun PlaceCard(model: WidgetModel) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val v = model.chosen ?: return NoPlace()
    val w = size.width.value
    val h = size.height.value
    val inner = w - 28
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_cards)).padding(14.dp).openOnClick(ctx),
    ) {
        // Header: place and group, freshness on wide widgets.
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(subtitle(ctx, v), style = style(Cards.secondary, 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (v.emergency) Text("!", style = style(Cards.warn, 13.sp, bold = true))
        }
        if (h < 70) {
            // One line: state and countdown.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (v.hasData) Dot(v.status.state)
                Spacer(GlanceModifier.width(6.dp))
                Text(stateText(ctx, v), style = style(if (v.hasData) Cards.state(v.status.state) else Cards.text, 13.sp, true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                if (v.hasData) LiveCountdown(v, big = false, color = Cards.text)
            }
            return@Column
        }
        Spacer(GlanceModifier.height(4.dp))
        val wide = w >= 250
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Text(
                        stateText(ctx, v),
                        style = style(if (v.hasData) Cards.state(v.status.state) else Cards.text, 14.sp, true),
                        maxLines = 2,
                    )
                }
                if (v.hasData && !wide) {
                    LiveCountdown(v, big = true, color = Cards.text)
                    untilText(ctx, v)?.let { Text(it, style = style(Cards.secondary, 12.sp), maxLines = 1) }
                }
                if (v.hasData && wide) {
                    Text(KolyText.changeLine(ctx, v.status), style = style(Cards.secondary, 12.sp), maxLines = 2)
                }
            }
            if (v.hasData && wide) LiveCountdown(v, big = true, color = Cards.text)
        }
        if (!v.hasData) {
            if (h >= 150) {
                Spacer(GlanceModifier.height(6.dp))
                Text(footerText(ctx, model, v), style = style(Cards.secondary, 11.sp), maxLines = 2)
            }
            return@Column
        }
        if (h >= 140 || (wide && h >= 105)) {
            Spacer(GlanceModifier.height(8.dp))
            val labels = w >= 180
            DayImage(v.status.today, model.now.minutes, inner, if (labels) 32f else 12f, labels, WidgetPalette.CARDS, Cards.secondary)
        }
        if (h >= 180) {
            Spacer(GlanceModifier.height(6.dp))
            Text(nextOutageText(ctx, v, model.now.minutes), style = style(Cards.text, 12.sp, true), maxLines = 1)
            Text(
                ctx.getString(UiR.string.today_total, KolyText.hours(v.status.offMinutesToday)),
                style = style(Cards.secondary, 12.sp),
                maxLines = 1,
            )
        }
        if (h >= 240) {
            Spacer(GlanceModifier.height(8.dp))
            Text(ctx.getString(UiR.string.tomorrow), style = style(Cards.secondary, 12.sp), maxLines = 1)
            val tomorrow = v.status.tomorrow
            if (tomorrow != null) {
                Spacer(GlanceModifier.height(3.dp))
                DayImage(tomorrow, null, inner, if (w >= 180) 30f else 10f, w >= 180, WidgetPalette.CARDS, Cards.secondary)
            }
            Text(tomorrowText(ctx, v), style = style(Cards.secondary, 11.sp), maxLines = 1)
        }
        if (h >= 300) {
            Spacer(GlanceModifier.defaultWeight())
            Text(footerText(ctx, model, v), style = style(Cards.secondary, 11.sp), maxLines = 1)
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
        if (model.views.isEmpty()) return NoPlace()
        val w = size.width.value
        val h = size.height.value
        val inner = w - 28
        val withBars = h >= 150
        val rowHeight = if (withBars) 44 else 24
        val footer = h >= 200
        val rows = ((h - 50 - (if (footer) 40 else 0)) / rowHeight).toInt().coerceIn(1, model.views.size.coerceAtMost(6))
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_cards)).padding(14.dp).openOnClick(ctx)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.getString(UiR.string.app_name), style = style(Cards.on, 14.sp, true), modifier = GlanceModifier.defaultWeight())
                if (w >= 250) Text(footerText(ctx, model, null), style = style(Cards.secondary, 11.sp), maxLines = 1)
            }
            for (v in model.views.take(rows)) {
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, 8)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Text(v.place.label, style = style(Cards.text, 13.sp, true), maxLines = 1)
                        if (w >= 250) {
                            Text(
                                if (v.hasData) KolyText.changeLine(ctx, v.status) else stateText(ctx, v),
                                style = style(Cards.secondary, 11.sp),
                                maxLines = 1,
                            )
                        }
                    }
                    if (v.hasData && v.status.changeAt != null) {
                        LiveCountdown(v, big = false, color = Cards.state(v.status.state))
                    } else if (w < 250) {
                        Text(stateText(ctx, v), style = style(Cards.secondary, 11.sp), maxLines = 1)
                    }
                }
                if (v.hasData && withBars) {
                    Spacer(GlanceModifier.height(3.dp))
                    DayImage(v.status.today, model.now.minutes, inner, 10f, false, WidgetPalette.CARDS, Cards.secondary)
                }
            }
            val main = model.chosen
            if (footer && main != null && main.hasData) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    ctx.getString(UiR.string.tomorrow) + " · " + main.place.label + " · " + tomorrowText(ctx, main),
                    style = style(Cards.secondary, 11.sp),
                    maxLines = 1,
                )
                main.status.tomorrow?.let {
                    Spacer(GlanceModifier.height(3.dp))
                    DayImage(it, null, inner, 10f, false, WidgetPalette.CARDS, Cards.secondary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- «Мінімал»

@Composable
private fun themedColor(p: ColorProvider): Color {
    val ctx = LocalContext.current
    return p.getColor(ctx)
}

/** Round ring; on a wide widget the ring sits on the left and details on the right. */
class RingWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace(dark = isNight(ctx))
            val w = size.width.value
            val h = size.height.value
            val wide = w >= h * 1.6f && w >= 220
            val side = if (wide) h else minOf(w, h)
            val density = ctx.resources.displayMetrics.density
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
                                WidgetBitmaps.ring(v.status.today, model.now.minutes, (side * density).toInt(), WidgetPalette.minimal(ctx, isNight(ctx)), density, stroke = side / 13f),
                            ),
                            contentDescription = KolyText.describeSegments(ctx, v.status.today),
                            modifier = GlanceModifier.size(side.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!wide) Text(v.place.label, style = style(variant, 11.sp), maxLines = 1)
                        if (v.hasData && v.status.changeAt != null) {
                            LiveCountdown(v, big = side >= 140, color = themedColor(onSurface))
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
                        Text(subtitle(ctx, v), style = style(variant, 12.sp), maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (v.hasData) {
                                Dot(v.status.state)
                                Spacer(GlanceModifier.width(6.dp))
                            }
                            Text(stateText(ctx, v), style = style(onSurface, 15.sp, true), maxLines = 1)
                        }
                        if (v.hasData) {
                            Text(KolyText.changeLine(ctx, v.status), style = style(onSurface, 12.sp), maxLines = 2)
                            if (h >= 130) Text(nextOutageText(ctx, v, model.now.minutes), style = style(variant, 12.sp), maxLines = 1)
                            if (h >= 160) Text(tomorrowText(ctx, v), style = style(variant, 12.sp), maxLines = 1)
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
            val v = model.chosen ?: return@GlanceTheme NoPlace(dark = isNight(ctx))
            val w = size.width.value
            val h = size.height.value
            val onSurface = GlanceTheme.colors.onSurface
            val variant = GlanceTheme.colors.onSurfaceVariant
            Column(
                GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(if (h < 70) (h / 2).dp else 24.dp)
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
                    if (v.hasData && v.status.changeAt != null) LiveCountdown(v, big = false, color = themedColor(onSurface))
                }
                if (h >= 70 && v.hasData) {
                    Text(KolyText.changeLine(ctx, v.status), style = style(variant, 12.sp), maxLines = 1)
                }
                if (h >= 100 && v.hasData) {
                    Spacer(GlanceModifier.height(6.dp))
                    DayImage(v.status.today, model.now.minutes, w - 32, 10f, false, WidgetPalette.minimal(ctx, isNight(ctx)), Color.Gray)
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
            val v = model.chosen ?: return@GlanceTheme NoPlace(dark = true)
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
                        stateText(ctx, v) + (untilText(ctx, v)?.let { " · $it" } ?: ""),
                        style = style(white, if (h >= 90) 22.sp else 18.sp, true),
                        maxLines = 1,
                    )
                }
                Text(subtitle(ctx, v), style = style(soft, 12.sp), maxLines = 1)
                if (h >= 90 && v.hasData) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(KolyText.changeLine(ctx, v.status), style = style(white, 13.sp), maxLines = 1)
                    Text(nextOutageText(ctx, v, model.now.minutes), style = style(soft, 12.sp), maxLines = 1)
                }
                if (h >= 140 && v.hasData) {
                    Text(tomorrowText(ctx, v), style = style(soft, 12.sp), maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- receivers

abstract class KolyWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetPlaces.remove(context, appWidgetIds)
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
