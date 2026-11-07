package io.github.vkardin.koly.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import io.github.vkardin.koly.core.ui.R as UiR

/** Refreshes every widget of the app. */
object KolyWidgets {
    suspend fun updateAll(context: Context) {
        for (w in listOf(CardsSmallWidget(), CardsMediumWidget(), CardsLargeWidget(), RingWidget(), PillWidget(), TextWidget())) {
            runCatching { w.updateAll(context) }
        }
    }
}

private object CardColors {
    val text = Color(0xFFF4F6FB)
    val secondary = Color(0xFFAEB6CA)
    val on = Color(0xFFFFC24A)
    val off = Color(0xFFC9D0E4)
    val maybe = Color(0xFFE0B860)

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

private fun openApp(context: Context) =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { actionStartActivity(it) }

private fun GlanceModifier.openOnClick(context: Context): GlanceModifier =
    openApp(context)?.let { clickable(it) } ?: this

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
    val until = view.status.untilChange(java.time.Instant.now()) ?: return
    val rv = RemoteViews(ctx.packageName, if (big) R.layout.widget_countdown_big else R.layout.widget_countdown_small)
    rv.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + until.toMillis(), null, true)
    rv.setChronometerCountDown(R.id.countdown, true)
    rv.setTextColor(R.id.countdown, android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
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

@Composable
private fun NoPlace() {
    val ctx = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_cards)).padding(12.dp).openOnClick(ctx),
        contentAlignment = Alignment.Center,
    ) {
        Text(ctx.getString(UiR.string.widget_no_places), style = TextStyle(color = ColorProvider(CardColors.text), fontSize = 13.sp))
    }
}

abstract class KolyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val model = loadWidgetModel(context, appWidgetId)
        provideContent { Content(model) }
    }

    @Composable
    abstract fun Content(model: WidgetModel)
}

/** «Картки» 2×2: place, status, big countdown, "до HH:MM". */
class CardsSmallWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        val ctx = LocalContext.current
        val v = model.chosen ?: return NoPlace()
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_cards)).padding(14.dp).openOnClick(ctx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(v.place.label, style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (v.hasData) {
                    Dot(v.status.state)
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    stateText(ctx, v),
                    style = TextStyle(color = ColorProvider(if (v.hasData) CardColors.state(v.status.state) else CardColors.text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 2,
                )
            }
            if (v.hasData) {
                Spacer(GlanceModifier.height(4.dp))
                LiveCountdown(v, big = true, color = CardColors.text)
                untilText(ctx, v)?.let {
                    Text(it, style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 12.sp))
                }
            }
        }
    }
}

/** «Картки» 4×2: countdown plus the day bar with the moments of change. */
class CardsMediumWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val v = model.chosen ?: return NoPlace()
        val density = ctx.resources.displayMetrics.density
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_cards)).padding(14.dp).openOnClick(ctx)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(v.place.label, style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 12.sp), maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (v.hasData) {
                            Dot(v.status.state)
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        Text(
                            stateText(ctx, v),
                            style = TextStyle(color = ColorProvider(if (v.hasData) CardColors.state(v.status.state) else CardColors.text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                    }
                    if (v.hasData) {
                        Text(KolyText.changeLine(ctx, v.status), style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 12.sp), maxLines = 1)
                    }
                }
                if (v.hasData) LiveCountdown(v, big = true, color = CardColors.text)
            }
            if (v.hasData) {
                Spacer(GlanceModifier.height(8.dp))
                val widthPx = ((size.width - 28.dp).value * density).toInt()
                val bmp = WidgetBitmaps.barWithLabels(v.status.today, model.now.minutes, widthPx, (34 * density).toInt(), WidgetPalette.CARDS, density, CardColors.secondary.toArgbInt())
                Image(
                    ImageProvider(bmp),
                    contentDescription = KolyText.describeSegments(ctx, v.status.today),
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** «Картки» 4×3: several places with mini bars, plus tomorrow for the main place. */
class CardsLargeWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val density = ctx.resources.displayMetrics.density
        if (model.views.isEmpty()) return NoPlace()
        val rows = ((size.height.value - 70) / 46).toInt().coerceIn(1, 4)
        val widthPx = ((size.width - 28.dp).value * density).toInt()
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_cards)).padding(14.dp).openOnClick(ctx)) {
            Text(ctx.getString(UiR.string.app_name), style = TextStyle(color = ColorProvider(CardColors.on), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            for (v in model.views.take(rows)) {
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, 8)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Text(v.place.label, style = TextStyle(color = ColorProvider(CardColors.text), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    if (v.hasData && v.status.changeAt != null) {
                        LiveCountdown(v, big = false, color = CardColors.state(v.status.state))
                    } else {
                        Text(stateText(ctx, v), style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 12.sp), maxLines = 1)
                    }
                }
                if (v.hasData) {
                    Spacer(GlanceModifier.height(3.dp))
                    Image(
                        ImageProvider(WidgetBitmaps.bar(v.status.today, model.now.minutes, widthPx, (10 * density).toInt(), WidgetPalette.CARDS, density)),
                        contentDescription = KolyText.describeSegments(ctx, v.status.today),
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                }
            }
            val main = model.chosen
            if (main != null && main.hasData && size.height.value >= 170) {
                Spacer(GlanceModifier.height(8.dp))
                val tomorrow = main.status.tomorrow
                Text(
                    ctx.getString(UiR.string.tomorrow) + " · " + main.place.label,
                    style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 12.sp),
                    maxLines = 1,
                )
                if (tomorrow != null) {
                    Spacer(GlanceModifier.height(3.dp))
                    Image(
                        ImageProvider(WidgetBitmaps.bar(tomorrow, null, widthPx, (10 * density).toInt(), WidgetPalette.CARDS, density)),
                        contentDescription = KolyText.describeSegments(ctx, tomorrow),
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                } else {
                    Text(ctx.getString(UiR.string.tomorrow_not_published), style = TextStyle(color = ColorProvider(CardColors.secondary), fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }
}

private fun isNight(context: Context) =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** «Мінімал»: round, a day ring with the countdown in the middle. */
class RingWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val v = model.chosen ?: return@GlanceTheme NoPlace()
            val density = ctx.resources.displayMetrics.density
            val side = minOf(size.width.value, size.height.value)
            val px = (side * density).toInt()
            Box(
                GlanceModifier.fillMaxSize().openOnClick(ctx),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    GlanceModifier.size(side.dp).background(GlanceTheme.colors.widgetBackground).cornerRadius((side / 2).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (v.hasData) {
                        Image(
                            ImageProvider(WidgetBitmaps.ring(v.status.today, model.now.minutes, px, WidgetPalette.minimal(ctx, isNight(ctx)), density, stroke = side / 14f)),
                            contentDescription = KolyText.describeSegments(ctx, v.status.today),
                            modifier = GlanceModifier.size(side.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v.place.label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                        if (v.hasData && v.status.changeAt != null) {
                            LiveCountdownThemed(v, big = side >= 130)
                        } else {
                            Text(stateText(ctx, v), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 2)
                        }
                        untilText(ctx, v)?.let { Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCountdownThemed(v: PlaceView, big: Boolean) {
    val ctx = LocalContext.current
    val color = GlanceTheme.colors.onSurface.getColor(ctx)
    LiveCountdown(v, big, color)
}

/** «Мінімал»: one-line pill. */
class PillWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val v = model.chosen ?: return@GlanceTheme NoPlace()
            Row(
                GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(999.dp).padding(horizontal = 14.dp).openOnClick(ctx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (v.hasData) {
                    Dot(v.status.state)
                    Spacer(GlanceModifier.width(8.dp))
                }
                Text(
                    v.place.label + " · " + stateText(ctx, v),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (v.hasData && v.status.changeAt != null) LiveCountdownThemed(v, big = false)
            }
        }
    }
}

/** «Мінімал»: text straight on the wallpaper. */
class TextWidget : KolyWidget() {
    @Composable
    override fun Content(model: WidgetModel) {
        GlanceTheme {
            val ctx = LocalContext.current
            val v = model.chosen ?: return@GlanceTheme NoPlace()
            val white = Color(0xFFFFFFFF)
            Column(GlanceModifier.fillMaxSize().padding(4.dp).openOnClick(ctx), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.hasData) {
                        Dot(v.status.state, 12)
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    Text(
                        stateText(ctx, v) + (untilText(ctx, v)?.let { " · $it" } ?: ""),
                        style = TextStyle(color = ColorProvider(white), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                Text(v.place.label, style = TextStyle(color = ColorProvider(white.copy(alpha = 0.85f)), fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}

private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

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
