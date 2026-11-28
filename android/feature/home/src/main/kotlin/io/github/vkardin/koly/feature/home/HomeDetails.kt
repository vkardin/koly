package io.github.vkardin.koly.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.model.CheckResult
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.Segment
import io.github.vkardin.koly.core.model.formatHm
import io.github.vkardin.koly.core.ui.DayBar
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.KolyText
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.StatusDot

private enum class RowTime { PAST, NOW, NEXT, LATER }

/** One outage of the day with its place in time: over, going on, the next one, or still ahead. */
private data class OutageRow(val segment: Segment, val time: RowTime)

private fun outageRows(segments: List<Segment>, nowMinutes: Double?): List<OutageRow> {
    var nextMarked = false
    val rows = ArrayList<OutageRow>()
    for (s in segments) {
        if (s.state == PowerState.ON) continue
        var time = RowTime.LATER
        if (nowMinutes != null) {
            if (s.end <= nowMinutes) {
                time = RowTime.PAST
            } else if (s.start <= nowMinutes) {
                time = RowTime.NOW
            } else if (!nextMarked) {
                time = RowTime.NEXT
                nextMarked = true
            }
        }
        rows.add(OutageRow(s, time))
    }
    return rows
}

/** View D "Докладно": the status card, then every outage of today and tomorrow with its length, totals and sources. */
@Composable
internal fun DetailsView(
    views: List<PlaceView>,
    selected: PlaceView,
    state: HomeState,
    onSelect: (String) -> Unit,
    onAddPlace: () -> Unit,
) {
    PlaceChips(views, selected, onSelect, onAddPlace)
    // The same status card as the first layout: what is happening now and when it changes.
    StatusCard(selected, state)
    if (!selected.hasData) return
    Spacer(Modifier.height(12.dp))
    DaySection(stringResource(R.string.today), selected.status.today, state.now.minutes)
    Spacer(Modifier.height(12.dp))
    val tomorrow = selected.status.tomorrow
    if (tomorrow != null) {
        DaySection(stringResource(R.string.tomorrow), tomorrow, null)
    } else {
        KolyCard {
            SectionTitle(stringResource(R.string.tomorrow))
            Text(
                stringResource(R.string.tomorrow_not_published),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalKolyColors.current.textSecondary,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    SourcesCard(selected)
}

@Composable
private fun DaySection(title: String, segments: List<Segment>, nowMinutes: Double?) {
    val c = LocalKolyColors.current
    val rows = outageRows(segments, nowMinutes)
    fun total(state: PowerState) = segments.filter { it.state == state }.sumOf { it.length }
    val off = total(PowerState.OFF)
    val maybe = total(PowerState.MAYBE)
    val on = total(PowerState.ON)
    val longestOn = if (rows.isEmpty()) null else segments.filter { it.state == PowerState.ON }.maxByOrNull { it.length }
    KolyCard {
        SectionTitle(title)
        DayBar(segments, nowMinutes = nowMinutes)
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) {
            Text(stringResource(R.string.d_none), style = MaterialTheme.typography.bodyLarge, color = c.onText)
        }
        for (row in rows) OutageLine(row)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (off > 0) Total(stringResource(R.string.d_off_total, hoursUnit(off)), c.offText)
            if (maybe > 0) Total(stringResource(R.string.d_maybe_total, hoursUnit(maybe)), c.maybeText)
        }
        Total(stringResource(R.string.d_on_total, hoursUnit(on)), c.onText)
        if (longestOn != null) {
            Text(
                stringResource(R.string.d_longest_on, formatHm(longestOn.start), formatHm(longestOn.end, allowMidnightEnd = true)),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun hoursUnit(minutes: Int): String = stringResource(R.string.d_hours_unit, KolyText.hours(minutes))

@Composable
private fun Total(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun OutageLine(row: OutageRow) {
    val c = LocalKolyColors.current
    val s = row.segment
    val range = formatHm(s.start) + "–" + formatHm(s.end, allowMidnightEnd = true)
    val tag = when (row.time) {
        RowTime.PAST -> stringResource(R.string.d_past)
        RowTime.NOW -> stringResource(R.string.d_now)
        RowTime.NEXT -> stringResource(R.string.d_next)
        RowTime.LATER -> stringResource(R.string.d_later)
    }
    val dimmed = row.time == RowTime.PAST
    val color = when {
        dimmed -> c.textSecondary
        s.state == PowerState.OFF -> c.offText
        else -> c.maybeText
    }
    val length = stringResource(R.string.d_hours_unit, KolyText.hours(s.length))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = "$range, $length, $tag" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(s.state, size = 10.dp)
        Spacer(Modifier.width(10.dp))
        Text(range, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        Text(length, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            tag,
            style = MaterialTheme.typography.labelMedium,
            color = if (row.time == RowTime.NOW) color else c.textSecondary,
            modifier = Modifier.width(56.dp),
        )
    }
}

/** Where the schedule comes from and how the other sources compare with it. */
@Composable
private fun SourcesCard(view: PlaceView) {
    val region = view.region ?: return
    val c = LocalKolyColors.current
    val host = runCatching { java.net.URI(region.sourceUrl ?: "").host.removePrefix("www.") }.getOrNull().orEmpty()
    val time = region.updatedAt?.let {
        runCatching {
            java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.of("Europe/Kyiv"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrNull()
    }
    if (host.isEmpty() && region.checks.isEmpty()) return
    KolyCard {
        SectionTitle(stringResource(R.string.d_sources))
        if (host.isNotEmpty()) {
            Text(
                if (time != null) stringResource(R.string.d_main_source_time, host, time) else stringResource(R.string.d_main_source, host),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
            )
        }
        if (region.checks.isNotEmpty()) {
            val agree = region.checks.count { it.result == CheckResult.SAME }
            Text(
                stringResource(R.string.d_agree, agree, region.checks.size),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            for (check in region.checks) {
                val label = stringResource(
                    when (check.result) {
                        CheckResult.SAME -> R.string.d_same
                        CheckResult.DIFFERENT -> R.string.d_different
                        CheckResult.OLDER -> R.string.d_older
                        CheckResult.FAILED -> R.string.d_failed
                    },
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(check.name, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (check.result == CheckResult.SAME) c.onText else c.maybeText,
                    )
                }
            }
        }
    }
}
