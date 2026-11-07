package io.github.vkardin.koly.notify

import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.model.DAY_MINUTES
import io.github.vkardin.koly.core.model.KYIV
import io.github.vkardin.koly.core.model.KyivNow
import io.github.vkardin.koly.core.model.NotifySettings
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.kyivInstant
import io.github.vkardin.koly.core.model.timeline
import java.time.Duration
import java.time.Instant

enum class NoticeKind { OFF, MAYBE, ON }

/** One warning ahead of a state change. */
data class Notice(
    val placeId: String,
    val placeLabel: String,
    val kind: NoticeKind,
    val changeAt: Instant,
    /** Minutes from today's midnight of the change and of the end of the new state. */
    val changeMinutes: Int,
    val untilMinutes: Int?,
    val fireAt: Instant,
) {
    val key: String get() = "$placeId|${changeAt.epochSecond}|$kind"
}

/** Every warning still ahead for the places in scope, today and tomorrow. */
fun planNotices(views: List<PlaceView>, notify: NotifySettings, now: KyivNow): List<Notice> {
    val lead = Duration.ofMinutes(notify.leadMinutes.toLong())
    val out = ArrayList<Notice>()
    for (v in views) {
        if (!v.hasData) continue
        val tl = v.status.timeline()
        val horizon = if (v.status.tomorrow != null) DAY_MINUTES * 2 else DAY_MINUTES
        for (i in 1 until tl.size) {
            val prev = tl[i - 1]
            val cur = tl[i]
            val kind = when {
                cur.state == PowerState.ON -> NoticeKind.ON
                cur.state == PowerState.OFF && prev.state != PowerState.OFF -> NoticeKind.OFF
                cur.state == PowerState.MAYBE && prev.state == PowerState.ON -> NoticeKind.MAYBE
                else -> null
            } ?: continue
            if (kind == NoticeKind.ON && !notify.beforeOn) continue
            if (kind != NoticeKind.ON && !notify.beforeOff) continue
            val changeAt = kyivInstant(now.date, cur.start)
            if (!changeAt.isAfter(now.instant)) continue
            out += Notice(
                placeId = v.place.id,
                placeLabel = v.place.label,
                kind = kind,
                changeAt = changeAt,
                changeMinutes = cur.start,
                untilMinutes = cur.end.takeIf { it < horizon },
                fireAt = changeAt.minus(lead),
            )
        }
    }
    return out.sortedBy { it.fireAt }
}

/** All moments when some place changes state; widgets are refreshed at these. */
fun changeMoments(views: List<PlaceView>, now: KyivNow): List<Instant> =
    views.filter { it.hasData }.flatMap { v ->
        v.status.timeline().drop(1).map { kyivInstant(now.date, it.start) }
    }.filter { it.isAfter(now.instant) }.sorted()

/** Night hours when warnings are not shown, by Kyiv time. */
fun isQuietTime(at: Instant): Boolean {
    val h = at.atZone(KYIV).hour
    return h >= 23 || h < 7
}
