package io.github.vkardin.koly.core.data

import io.github.vkardin.koly.core.model.KyivNow
import io.github.vkardin.koly.core.model.Place
import io.github.vkardin.koly.core.model.PlaceStatus
import io.github.vkardin.koly.core.model.RegionFile
import io.github.vkardin.koly.core.model.RegionIndexEntry
import io.github.vkardin.koly.core.model.RegionStatus
import io.github.vkardin.koly.core.model.placeStatus
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** Everything a screen, widget or notification needs to show one place. */
data class PlaceView(
    val place: Place,
    val regionName: String,
    val region: RegionFile?,
    val status: PlaceStatus,
    val regionStatus: RegionStatus,
    /** The source was not reachable for a while, or the region is marked stale. */
    val stale: Boolean,
) {
    val noSchedules: Boolean get() = regionStatus == RegionStatus.NO_SCHEDULES
    val hasData: Boolean get() = region != null && status.hasToday && !noSchedules
    val emergency: Boolean get() = region?.emergency?.active == true
}

private val STALE_AFTER: Duration = Duration.ofHours(3)

fun regionName(entry: RegionIndexEntry?, fallbackId: String, lang: String): String =
    entry?.name?.forLanguage(lang)?.takeIf { it.isNotBlank() } ?: fallbackId

fun buildPlaceView(place: Place, snapshot: DataSnapshot, now: KyivNow, lang: String): PlaceView {
    val region = snapshot.regions[place.regionId]
    val entry = snapshot.index?.regions?.firstOrNull { it.id == place.regionId }
    val status = region?.status ?: entry?.status ?: RegionStatus.ERROR
    val fetchedAt = region?.fetchedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    val stale = status == RegionStatus.STALE ||
        (fetchedAt != null && Duration.between(fetchedAt, now.instant) > STALE_AFTER) ||
        (snapshot.checkedAt != null && Duration.between(snapshot.checkedAt, now.instant) > STALE_AFTER)
    return PlaceView(
        place = place,
        regionName = regionName(entry, place.regionId, lang),
        region = region,
        status = placeStatus(region, place.group, now),
        regionStatus = status,
        stale = stale,
    )
}

/** Moment to refresh widgets again: the nearest state change across places. */
fun nextChange(views: List<PlaceView>): Instant? = views.mapNotNull { it.status.changeAt }.minOrNull()

/** Sorted events of the rest of today for the "Далі сьогодні" feed. */
data class DayEvent(val minutes: Int, val view: PlaceView, val state: io.github.vkardin.koly.core.model.PowerState)

fun upcomingEvents(views: List<PlaceView>, nowMinutes: Double): List<DayEvent> =
    views.filter { it.hasData }.flatMap { v ->
        v.status.today.drop(1).filter { it.start > nowMinutes }.map { DayEvent(it.start, v, it.state) }
    }.sortedBy { it.minutes }

