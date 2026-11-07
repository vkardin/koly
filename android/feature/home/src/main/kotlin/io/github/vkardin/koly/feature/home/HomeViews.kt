package io.github.vkardin.koly.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.data.upcomingEvents
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.formatHm
import io.github.vkardin.koly.core.ui.CountdownStyle
import io.github.vkardin.koly.core.ui.DayBar
import io.github.vkardin.koly.core.ui.DayRing
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.KolyText
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.StatusDot
import io.github.vkardin.koly.core.ui.StatusLabel

/** View A "Картка і смуга". */
@Composable
internal fun CardAndBarView(
    views: List<PlaceView>,
    selected: PlaceView,
    state: HomeState,
    onSelect: (String) -> Unit,
    onAddPlace: () -> Unit,
) {
    PlaceChips(views, selected, onSelect, onAddPlace)
    StatusCard(selected, state)
    Spacer(Modifier.height(12.dp))
    TodayAndTomorrow(selected, state)
}

@Composable
private fun StatusCard(view: PlaceView, state: HomeState) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    KolyCard {
        Text(
            "${view.regionName} · ${stringResource(R.string.group_value, view.place.group)}",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        when {
            view.noSchedules -> {
                StatusLabel(PowerState.ON)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.no_schedules), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.no_schedules_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            view.region == null -> {
                Text(stringResource(R.string.region_unavailable), style = MaterialTheme.typography.headlineSmall)
            }
            !view.status.hasToday -> {
                Text(stringResource(R.string.no_data_today), style = MaterialTheme.typography.headlineSmall)
            }
            else -> {
                StatusLabel(view.status.state)
                val countdown = countdownOf(view, state)
                if (countdown != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        KolyText.countdownCaption(ctx, view.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                    Text(
                        countdown.text,
                        style = CountdownStyle,
                        color = c.text,
                        modifier = Modifier.countdownSemantics(countdown),
                    )
                }
                Text(
                    KolyText.changeLine(ctx, view.status),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.text,
                )
                if (view.stale) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.region_stale), style = MaterialTheme.typography.bodySmall, color = c.maybeText)
                }
            }
        }
    }
}

@Composable
private fun TodayAndTomorrow(view: PlaceView, state: HomeState) {
    if (!view.hasData) return
    val c = LocalKolyColors.current
    KolyCard {
        SectionTitle(stringResource(R.string.today))
        DayBar(view.status.today, nowMinutes = state.now.minutes)
    }
    Spacer(Modifier.height(12.dp))
    KolyCard {
        SectionTitle(stringResource(R.string.tomorrow))
        val tomorrow = view.status.tomorrow
        if (tomorrow != null) {
            DayBar(tomorrow)
        } else {
            Text(stringResource(R.string.tomorrow_not_published), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

/** View B "Кільце доби". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayRingView(
    views: List<PlaceView>,
    selected: PlaceView,
    state: HomeState,
    onSelect: (String) -> Unit,
) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.place.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.choose_place)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (v in views) {
                DropdownMenuItem(
                    text = { Text(v.place.label) },
                    onClick = {
                        onSelect(v.place.id)
                        expanded = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (!selected.hasData) {
        KolyCard {
            Text(
                stringResource(
                    when {
                        selected.noSchedules -> R.string.no_schedules
                        selected.region == null -> R.string.region_unavailable
                        else -> R.string.no_data_today
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        return
    }
    val countdown = countdownOf(selected, state)
    Centered {
    DayRing(
        segments = selected.status.today,
        nowMinutes = state.now.minutes,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.countdownSemantics(countdown)) {
            StatusDot(selected.status.state)
            Spacer(Modifier.height(4.dp))
            Text(
                KolyText.stateLabel(ctx, selected.status.state),
                style = MaterialTheme.typography.titleSmall,
                color = c.stateText(selected.status.state),
            )
            if (countdown != null) {
                Text(countdown.text, style = CountdownStyle.copy(fontSize = 52.sp, lineHeight = 56.sp), color = c.text)
                Text(
                    KolyText.changeLine(ctx, selected.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            } else {
                Text(stringResource(R.string.no_changes_today), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val outage = selected.status.nextOutage(state.now.minutes)
        Tile(
            modifier = Modifier.weight(1f),
            text = if (outage != null) {
                stringResource(
                    if (outage.state == PowerState.OFF) R.string.off_window else R.string.maybe_window,
                    formatHm(outage.start),
                    formatHm(outage.end, allowMidnightEnd = true),
                )
            } else {
                stringResource(R.string.no_more_outages)
            },
        )
        Tile(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.today_total, KolyText.hours(selected.status.offMinutesToday)),
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        when {
            !selected.status.hasTomorrow -> stringResource(R.string.tomorrow_not_published)
            selected.status.outagesTomorrow == 0 -> stringResource(R.string.tomorrow_no_outages)
            else -> pluralStringResource(R.plurals.tomorrow_outages, selected.status.outagesTomorrow, selected.status.outagesTomorrow)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = c.text,
    )
}

@Composable
private fun Tile(text: String, modifier: Modifier = Modifier) {
    KolyCard(modifier) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = LocalKolyColors.current.text)
    }
}

/** View C "Усі місця списком". */
@Composable
internal fun AllPlacesView(views: List<PlaceView>, state: HomeState) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    Spacer(Modifier.height(8.dp))
    for (v in views) {
        KolyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (v.hasData) v.status.state else PowerState.ON)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.place.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${v.regionName} · ${stringResource(R.string.group_value, v.place.group)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val countdown = countdownOf(v, state)
                Text(
                    when {
                        v.noSchedules -> stringResource(R.string.no_schedules)
                        !v.hasData -> stringResource(R.string.status_region_off)
                        countdown != null -> KolyText.stillText(ctx, v.status.state, countdown.text)
                        else -> KolyText.stateLabel(ctx, v.status.state)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (v.hasData) c.stateText(v.status.state) else c.textSecondary,
                    modifier = Modifier.countdownSemantics(countdown),
                )
            }
            if (v.hasData) {
                Spacer(Modifier.height(10.dp))
                DayBar(v.status.today, nowMinutes = state.now.minutes, showLabels = false, showDurations = false, barHeight = 14.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(8.dp))
    SectionTitle(stringResource(R.string.next_today))
    val events = upcomingEvents(views, state.now.minutes)
    if (events.isEmpty()) {
        Text(stringResource(R.string.nothing_more_today), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
    for (e in events.take(12)) {
        val label = stringResource(
            when (e.state) {
                PowerState.OFF -> R.string.event_off
                PowerState.ON -> R.string.event_on
                PowerState.MAYBE -> R.string.event_maybe
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics {
                contentDescription = "${formatHm(e.minutes)}, ${e.view.place.label}, $label"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatHm(e.minutes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
            StatusDot(e.state, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Text(e.view.place.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}
