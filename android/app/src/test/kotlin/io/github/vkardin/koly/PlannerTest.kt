package io.github.vkardin.koly

import io.github.vkardin.koly.core.data.DataSnapshot
import io.github.vkardin.koly.core.data.buildPlaceView
import io.github.vkardin.koly.core.model.DaySchedule
import io.github.vkardin.koly.core.model.Interval
import io.github.vkardin.koly.core.model.IntervalType
import io.github.vkardin.koly.core.model.KYIV
import io.github.vkardin.koly.core.model.KyivNow
import io.github.vkardin.koly.core.model.NotifySettings
import io.github.vkardin.koly.core.model.Place
import io.github.vkardin.koly.core.model.RegionFile
import io.github.vkardin.koly.core.model.RegionStatus
import io.github.vkardin.koly.notify.NoticeKind
import io.github.vkardin.koly.notify.isQuietTime
import io.github.vkardin.koly.notify.planNotices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class PlannerTest {
    private val place = Place("p", "Дім", "r", "1.1")

    private fun now(time: String) = KyivNow.at(ZonedDateTime.of(LocalDate.parse("2026-11-07"), LocalTime.parse(time), KYIV).toInstant())

    private fun snapshot(vararg days: Pair<String, List<Interval>>) = DataSnapshot(
        regions = mapOf(
            "r" to RegionFile(
                id = "r",
                status = RegionStatus.OK,
                days = days.map { (d, list) -> DaySchedule(d, mapOf("1.1" to list)) },
            ),
        ),
    )

    @Test
    fun plansWarningsBeforeOffAndOn() {
        val snap = snapshot("2026-11-07" to listOf(Interval("18:00", "22:00", IntervalType.OFF)))
        val n = now("12:00")
        val notices = planNotices(listOf(buildPlaceView(place, snap, n, "uk")), NotifySettings(leadMinutes = 15), n)
        assertEquals(listOf(NoticeKind.OFF, NoticeKind.ON), notices.map { it.kind })
        assertEquals(1320, notices[0].untilMinutes)
        assertEquals(ZonedDateTime.of(LocalDate.parse("2026-11-07"), LocalTime.of(17, 45), KYIV).toInstant(), notices[0].fireAt)
    }

    @Test
    fun respectsSwitches() {
        val snap = snapshot("2026-11-07" to listOf(Interval("18:00", "22:00", IntervalType.OFF)))
        val n = now("12:00")
        val notices = planNotices(listOf(buildPlaceView(place, snap, n, "uk")), NotifySettings(beforeOff = false), n)
        assertEquals(listOf(NoticeKind.ON), notices.map { it.kind })
    }

    @Test
    fun skipsChangesInThePast() {
        val snap = snapshot("2026-11-07" to listOf(Interval("08:00", "10:00", IntervalType.OFF)))
        val n = now("12:00")
        assertTrue(planNotices(listOf(buildPlaceView(place, snap, n, "uk")), NotifySettings(), n).isEmpty())
    }

    @Test
    fun quietHours() {
        assertTrue(isQuietTime(now("23:30").instant))
        assertTrue(isQuietTime(now("06:59").instant))
        assertFalse(isQuietTime(now("07:00").instant))
    }
}
