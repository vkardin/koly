package io.github.vkardin.koly.core.ui

import android.content.Context
import io.github.vkardin.koly.core.model.DAY_MINUTES
import io.github.vkardin.koly.core.model.PlaceStatus
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.Segment
import io.github.vkardin.koly.core.model.formatHm
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant

/** Text helpers shared by the app, notifications and widgets. */
object KolyText {
    fun stateLabel(ctx: Context, state: PowerState): String = ctx.getString(
        when (state) {
            PowerState.ON -> R.string.light_on
            PowerState.OFF -> R.string.light_off
            PowerState.MAYBE -> R.string.light_maybe
        },
    )

    /** "о 18:00 · до 22:00 без світла" and similar. */
    fun changeLine(ctx: Context, status: PlaceStatus): String {
        val at = status.changeAtMinutes ?: return ctx.getString(R.string.no_changes_today)
        val atText = formatHm(at)
        val endText = status.nextStateEnd?.let { formatHm(it, allowMidnightEnd = false) }
        return when (status.nextState) {
            PowerState.OFF -> if (endText != null) ctx.getString(R.string.change_off_until, atText, endText) else ctx.getString(R.string.change_off, atText)
            PowerState.MAYBE -> if (endText != null) ctx.getString(R.string.change_maybe_until, atText, endText) else ctx.getString(R.string.change_maybe, atText)
            PowerState.ON, null -> ctx.getString(R.string.change_on, atText)
        }
    }

    /** "вимкнуть через" / "увімкнуть через" before the countdown. */
    fun countdownCaption(ctx: Context, status: PlaceStatus): String = ctx.getString(
        when (status.nextState) {
            PowerState.OFF -> R.string.off_in
            PowerState.MAYBE -> R.string.maybe_in
            else -> R.string.on_in
        },
    )

    /** "є ще 2:14" / "немає ще 0:46". */
    fun stillText(ctx: Context, state: PowerState, countdown: String): String = ctx.getString(
        when (state) {
            PowerState.ON -> R.string.still_on
            PowerState.OFF -> R.string.still_off
            PowerState.MAYBE -> R.string.still_maybe
        },
        countdown,
    )

    fun countdownDescription(ctx: Context, duration: Duration): String {
        val total = ((duration.seconds.coerceAtLeast(0) + 59) / 60).toInt()
        return ctx.getString(R.string.countdown_description, total / 60, total % 60)
    }

    fun ago(ctx: Context, at: Instant?, now: Instant = Instant.now()): String {
        if (at == null) return ctx.getString(R.string.never_updated)
        val min = Duration.between(at, now).toMinutes().coerceAtLeast(0)
        return when {
            min < 1 -> ctx.getString(R.string.just_now)
            min < 60 -> ctx.getString(R.string.min_ago, min.toInt())
            min < 60 * 48 -> ctx.getString(R.string.hours_ago, (min / 60).toInt())
            else -> ctx.getString(R.string.days_ago, (min / 1440).toInt())
        }
    }

    fun hours(minutes: Int): String {
        val f = NumberFormat.getNumberInstance()
        f.maximumFractionDigits = 1
        return f.format(minutes / 60.0)
    }

    /** Text form of a day bar for TalkBack. */
    fun describeSegments(ctx: Context, segments: List<Segment>): String = segments.joinToString(", ") { s ->
        val from = formatHm(s.start)
        val to = if (s.end >= DAY_MINUTES) "24:00" else formatHm(s.end)
        when (s.state) {
            PowerState.ON -> ctx.getString(R.string.bar_on, from, to)
            PowerState.OFF -> ctx.getString(R.string.bar_off, from, to)
            PowerState.MAYBE -> ctx.getString(R.string.bar_maybe, from, to)
        }
    }

    fun stateIcon(state: PowerState): Int = when (state) {
        PowerState.ON -> R.drawable.ic_power_on
        PowerState.OFF -> R.drawable.ic_power_off
        PowerState.MAYBE -> R.drawable.ic_power_maybe
    }
}

/** App language code ("uk", "en" or "ru") of the given context. */
fun Context.appLanguage(): String = when (resources.configuration.locales[0].language) {
    "en" -> "en"
    "ru" -> "ru"
    else -> "uk"
}
