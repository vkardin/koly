package io.github.vkardin.koly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class ScheduleTest {
    private fun region(vararg days: Pair<String, List<Interval>>) = RegionFile(
        id = "test",
        status = RegionStatus.OK,
        days = days.map { (date, list) -> DaySchedule(date, mapOf("1.1" to list)) },
    )

    private fun at(date: String, time: String): KyivNow =
        KyivNow.at(ZonedDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time), KYIV).toInstant())

    @Test
    fun segmentsCoverTheWholeDay() {
        val segs = buildSegments(listOf(Interval("08:00", "11:30", IntervalType.OFF)))
        assertEquals(3, segs.size)
        assertEquals(Segment(0, 480, PowerState.ON), segs[0])
        assertEquals(Segment(480, 690, PowerState.OFF), segs[1])
        assertEquals(Segment(690, DAY_MINUTES, PowerState.ON), segs[2])
    }

    @Test
    fun offWinsOverMaybe() {
        val segs = buildSegments(
            listOf(
                Interval("10:00", "14:00", IntervalType.MAYBE),
                Interval("12:00", "13:00", IntervalType.OFF),
            ),
        )
        assertEquals(
            listOf(PowerState.ON, PowerState.MAYBE, PowerState.OFF, PowerState.MAYBE, PowerState.ON),
            segs.map { it.state },
        )
    }

    @Test
    fun endAtMidnightIsAccepted() {
        val segs = buildSegments(listOf(Interval("22:00", "24:00", IntervalType.OFF)))
        assertEquals(Segment(1320, DAY_MINUTES, PowerState.OFF), segs.last())
    }

    @Test
    fun statusCountsDownToNextChange() {
        val r = region("2026-11-07" to listOf(Interval("18:00", "22:00", IntervalType.OFF)))
        val now = at("2026-11-07", "15:46")
        val s = placeStatus(r, "1.1", now)
        assertEquals(PowerState.ON, s.state)
        assertEquals(1080, s.changeAtMinutes)
        assertEquals(PowerState.OFF, s.nextState)
        assertEquals(1320, s.nextStateEnd)
        assertEquals("2:14", formatCountdown(s.untilChange(now.instant)!!))
        assertEquals(240, s.offMinutesToday)
    }

    @Test
    fun outageRunningPastMidnightJoinsTomorrow() {
        val r = region(
            "2026-11-07" to listOf(Interval("22:00", "24:00", IntervalType.OFF)),
            "2026-11-08" to listOf(Interval("00:00", "02:00", IntervalType.OFF)),
        )
        val s = placeStatus(r, "1.1", at("2026-11-07", "23:00"))
        assertEquals(PowerState.OFF, s.state)
        assertEquals(DAY_MINUTES + 120, s.changeAtMinutes)
        assertEquals(PowerState.ON, s.nextState)
        assertTrue(s.hasTomorrow)
    }

    @Test
    fun noChangeWithoutTomorrow() {
        val r = region("2026-11-07" to listOf(Interval("08:00", "10:00", IntervalType.OFF)))
        val s = placeStatus(r, "1.1", at("2026-11-07", "20:00"))
        assertEquals(PowerState.ON, s.state)
        assertNull(s.changeAt)
        assertFalse(s.hasTomorrow)
    }

    @Test
    fun missingTodayMeansNoData() {
        val r = region("2026-11-06" to listOf(Interval("08:00", "10:00", IntervalType.OFF)))
        val s = placeStatus(r, "1.1", at("2026-11-07", "09:00"))
        assertFalse(s.hasToday)
        assertNull(s.changeAt)
    }

    @Test
    fun countdownHonoursDaylightSavingTime() {
        // 2026-10-25: clocks go back at 04:00 in Kyiv, the day lasts 25 hours.
        val r = region("2026-10-25" to listOf(Interval("06:00", "08:00", IntervalType.OFF)))
        val now = at("2026-10-25", "01:00")
        val s = placeStatus(r, "1.1", now)
        assertEquals(Duration.ofHours(6), s.untilChange(now.instant))
    }

    @Test
    fun closeChangePointsAreMerged() {
        val segs = buildSegments(
            listOf(
                Interval("12:00", "13:00", IntervalType.MAYBE),
                Interval("13:00", "14:00", IntervalType.OFF),
                Interval("18:00", "22:00", IntervalType.OFF),
            ),
        )
        val labels = changeLabels(segs)
        assertEquals(listOf("12:00–14:00", "18:00", "22:00"), labels.map { it.text })
    }

    @Test
    fun parsesSchemaAndIgnoresUnknownValues() {
        val json = """
            {"id":"x","status":"weird","extra":1,"emergency":{"active":true,"note":"n"},
             "days":[{"date":"2026-11-07","groups":{"1.1":[{"from":"08:00","to":"09:00","type":"off"}]}}]}
        """.trimIndent()
        val r = KolyJson.decodeFromString(RegionFile.serializer(), json)
        assertEquals(RegionStatus.ERROR, r.status)
        assertTrue(r.emergency.active)
        assertEquals(IntervalType.OFF, r.days[0].groups["1.1"]!![0].type)
    }
}
