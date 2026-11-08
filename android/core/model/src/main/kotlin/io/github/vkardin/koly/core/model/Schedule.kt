package io.github.vkardin.koly.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** All schedule times are local wall-clock times in Kyiv. */
val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")

const val DAY_MINUTES = 1440

enum class PowerState { ON, OFF, MAYBE }

/** A part of a day, in minutes from local midnight: [start, end). */
data class Segment(val start: Int, val end: Int, val state: PowerState) {
    val length: Int get() = end - start
}

data class KyivNow(
    val instant: Instant,
    val date: LocalDate,
    /** Wall-clock minutes since local midnight, with seconds as a fraction. */
    val minutes: Double,
) {
    val tomorrow: LocalDate get() = date.plusDays(1)

    companion object {
        fun at(instant: Instant): KyivNow {
            val z = instant.atZone(KYIV)
            val minutes = z.hour * 60 + z.minute + z.second / 60.0
            return KyivNow(instant, z.toLocalDate(), minutes)
        }

        fun now(): KyivNow = at(Instant.now())
    }
}

fun parseHm(value: String): Int {
    val parts = value.trim().split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return (h * 60 + m).coerceIn(0, DAY_MINUTES)
}

/** "08:00"; 1440 becomes "24:00" only when [allowMidnightEnd] is set. */
fun formatHm(minutes: Int, allowMidnightEnd: Boolean = false): String {
    if (allowMidnightEnd && minutes == DAY_MINUTES) return "24:00"
    val total = ((minutes % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES
    return "%02d:%02d".format(total / 60, total % 60)
}

/** Countdown like "2:14" (hours:minutes), rounded up to the next minute. */
fun formatCountdown(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    val total = ((seconds + 59) / 60).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/** Builds a full-day timeline; "off" wins over "maybe" where they overlap. */
fun buildSegments(intervals: List<Interval>?): List<Segment> {
    val states = Array(DAY_MINUTES) { PowerState.ON }
    for (it in intervals.orEmpty()) {
        val from = parseHm(it.from)
        val to = parseHm(it.to)
        val type = if (it.type == IntervalType.OFF) PowerState.OFF else PowerState.MAYBE
        for (i in from until to) {
            if (type == PowerState.OFF || states[i] == PowerState.ON) states[i] = type
        }
    }
    val out = ArrayList<Segment>()
    var start = 0
    for (i in 1..DAY_MINUTES) {
        if (i == DAY_MINUTES || states[i] != states[start]) {
            out += Segment(start, i, states[start])
            start = i
        }
    }
    return out
}

/** Real wall-clock moment of [minutes] after the midnight of [date]; handles 24:00 and DST. */
fun kyivInstant(date: LocalDate, minutes: Int): Instant {
    val days = minutes / DAY_MINUTES
    val rest = minutes % DAY_MINUTES
    val local = date.plusDays(days.toLong()).atTime(LocalTime.of(rest / 60, rest % 60))
    return ZonedDateTime.ofLocal(local, KYIV, null).toInstant()
}

data class PlaceStatus(
    val hasToday: Boolean,
    val hasTomorrow: Boolean,
    val today: List<Segment>,
    val tomorrow: List<Segment>?,
    val state: PowerState,
    /** Moment of the next state change, or null if none is known. */
    val changeAt: Instant?,
    /** Minutes of [changeAt] from today's midnight (>= 1440 means tomorrow). */
    val changeAtMinutes: Int?,
    val nextState: PowerState?,
    /** End of the next state, used for "до 22:00 без світла". */
    val nextStateEnd: Int?,
    val offMinutesToday: Int,
    val maybeMinutesToday: Int,
    val outagesToday: Int,
    val outagesTomorrow: Int,
) {
    fun untilChange(now: Instant): Duration? = changeAt?.let { Duration.between(now, it) }

    /** The next or current outage window of today, as minutes. */
    fun nextOutage(nowMinutes: Double): Segment? =
        today.firstOrNull { it.state != PowerState.ON && it.end > nowMinutes }
}

fun placeStatus(region: RegionFile?, group: String, now: KyivNow): PlaceStatus {
    val todayDay = region?.day(now.date.toString())
    val tomorrowDay = region?.day(now.tomorrow.toString())
    val today = buildSegments(todayDay?.groups?.get(group))
    val tomorrow = tomorrowDay?.let { buildSegments(it.groups[group]) }

    // One timeline over today and tomorrow (if published), in minutes from today's midnight.
    val timeline = ArrayList<Segment>(today)
    if (tomorrow != null) {
        for (s in tomorrow) {
            val last = timeline.last()
            if (s.start == 0 && last.end == DAY_MINUTES && last.state == s.state) {
                timeline[timeline.size - 1] = last.copy(end = DAY_MINUTES + s.end)
            } else {
                timeline += Segment(DAY_MINUTES + s.start, DAY_MINUTES + s.end, s.state)
            }
        }
    }
    val horizon = if (tomorrow != null) DAY_MINUTES * 2 else DAY_MINUTES
    val idx = timeline.indexOfFirst { now.minutes >= it.start && now.minutes < it.end }.coerceAtLeast(0)
    val cur = timeline[idx]

    var changeAtMinutes: Int? = null
    var nextState: PowerState? = null
    var nextStateEnd: Int? = null
    if (todayDay != null && cur.end < horizon) {
        changeAtMinutes = cur.end
        timeline.getOrNull(idx + 1)?.let { next ->
            nextState = next.state
            nextStateEnd = if (next.end < horizon) next.end else null
        }
    }

    return PlaceStatus(
        hasToday = todayDay != null,
        hasTomorrow = tomorrowDay != null,
        today = today,
        tomorrow = tomorrow,
        state = if (todayDay != null) cur.state else PowerState.ON,
        changeAt = changeAtMinutes?.let { kyivInstant(now.date, it) },
        changeAtMinutes = changeAtMinutes,
        nextState = nextState,
        nextStateEnd = nextStateEnd,
        offMinutesToday = today.filter { it.state == PowerState.OFF }.sumOf { it.length },
        maybeMinutesToday = today.filter { it.state == PowerState.MAYBE }.sumOf { it.length },
        outagesToday = today.count { it.state != PowerState.ON },
        outagesTomorrow = tomorrow?.count { it.state != PowerState.ON } ?: 0,
    )
}

/** A label under a day bar: one change moment, or several close ones merged. */
data class ChangeLabel(val first: Int, val last: Int, val merged: Boolean) {
    val center: Int get() = (first + last) / 2
    val text: String get() = if (merged) "${formatHm(first)}–${formatHm(last)}" else formatHm(first)
}

/** Real moments of change during the day; points closer than [mergeMinutes] are merged. */
fun changeLabels(segments: List<Segment>, mergeMinutes: Int = 150): List<ChangeLabel> {
    val points = segments.drop(1).map { it.start }
    val groups = ArrayList<MutableList<Int>>()
    for (p in points) {
        val g = groups.lastOrNull()
        if (g != null && p - g.last() < mergeMinutes) g += p else groups += mutableListOf(p)
    }
    return groups.map { ChangeLabel(it.first(), it.last(), it.size > 1) }
}

/** Stable signature of a group's schedule for change detection. */
fun scheduleSignature(region: RegionFile?, group: String, date: LocalDate): String {
    val day = region?.day(date.toString()) ?: return ""
    return day.groups[group].orEmpty().joinToString(";") { "${it.from}-${it.to}-${it.type}" }
}

/** Minutes without power per day for the group, from the days present in the file. */
fun dailyOffMinutes(region: RegionFile, group: String): Map<String, Pair<Int, Int>> =
    region.days.associate { day ->
        val segs = buildSegments(day.groups[group])
        day.date to Pair(
            segs.filter { it.state == PowerState.OFF }.sumOf { it.length },
            segs.filter { it.state == PowerState.MAYBE }.sumOf { it.length },
        )
    }

/** Today and tomorrow (if published) as one timeline, in minutes from today's midnight. */
fun PlaceStatus.timeline(): List<Segment> {
    val out = ArrayList<Segment>(today)
    val next = tomorrow ?: return out
    for (s in next) {
        val last = out.last()
        if (s.start == 0 && last.end == DAY_MINUTES && last.state == s.state) {
            out[out.size - 1] = last.copy(end = DAY_MINUTES + s.end)
        } else {
            out += Segment(DAY_MINUTES + s.start, DAY_MINUTES + s.end, s.state)
        }
    }
    return out
}
