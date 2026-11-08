package io.github.vkardin.koly.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.db.DayHistoryEntity
import io.github.vkardin.koly.core.model.KYIV
import io.github.vkardin.koly.core.model.Place
import io.github.vkardin.koly.core.ui.AdSlot
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.KolyText
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class DayStat(val date: LocalDate, val offMinutes: Int?, val maybeMinutes: Int?)

data class StatsState(
    val loaded: Boolean = false,
    val places: List<Place> = emptyList(),
    val selected: Place? = null,
    val days: List<DayStat> = emptyList(),
) {
    val hasData: Boolean get() = days.any { it.offMinutes != null }
    val totalOff: Int get() = days.sumOf { it.offMinutes ?: 0 }
    val totalMaybe: Int get() = days.sumOf { it.maybeMinutes ?: 0 }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    settings: SettingsRepository,
    schedules: ScheduleRepository,
) : ViewModel() {
    private val chosen = MutableStateFlow<String?>(null)

    val state: StateFlow<StatsState> = combine(settings.settings, chosen) { s, id ->
        s.places to (s.places.firstOrNull { it.id == id } ?: s.mainPlace)
    }.flatMapLatest { (places, place) ->
        if (place == null) return@flatMapLatest flowOf(StatsState(loaded = true, places = places))
        val today = LocalDate.now(KYIV)
        val from = today.minusDays(6)
        schedules.history(place.regionId, place.group, from, today).map { rows ->
            StatsState(loaded = true, places = places, selected = place, days = buildDays(from, rows))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun select(id: String) {
        chosen.value = id
    }

    private fun buildDays(from: LocalDate, rows: List<DayHistoryEntity>): List<DayStat> {
        val byDate = rows.associateBy { it.date }
        return (0L..6L).map { i ->
            val d = from.plusDays(i)
            val row = byDate[d.toString()]
            DayStat(d, row?.offMinutes, row?.maybeMinutes)
        }
    }
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalKolyColors.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.stats_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        if (state.places.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (p in state.places) {
                    FilterChip(selected = p.id == state.selected?.id, onClick = { viewModel.select(p.id) }, label = { Text(p.label) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        KolyCard {
            if (!state.loaded) return@KolyCard
            if (!state.hasData) {
                Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                return@KolyCard
            }
            Text(
                stringResource(R.string.stats_total, KolyText.hours(state.totalOff)),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.stats_by_schedule), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (state.totalMaybe > 0) {
                Text(
                    stringResource(R.string.stats_maybe, KolyText.hours(state.totalMaybe)),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.maybeText,
                )
            }
            Spacer(Modifier.height(16.dp))
            WeekChart(state.days)
        }
        Spacer(Modifier.height(16.dp))
        AdSlot()
    }
}

@Composable
private fun WeekChart(days: List<DayStat>) {
    val c = LocalKolyColors.current
    val maxMinutes = (days.maxOfOrNull { it.offMinutes ?: 0 } ?: 0).coerceAtLeast(60)
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in days) {
            val dayName = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            val hoursText = d.offMinutes?.let { KolyText.hours(it) } ?: "—"
            val description = stringResource(R.string.stats_day_description, "$dayName ${d.date.dayOfMonth}", hoursText)
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(hoursText, style = MaterialTheme.typography.labelSmall, color = c.text)
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 6.dp)) {
                    val off = d.offMinutes ?: 0
                    val h = size.height * off / maxMinutes
                    drawRoundRect(c.track, Offset.Zero, size, CornerRadius(6.dp.toPx()))
                    if (h > 0) {
                        drawRoundRect(c.mark, Offset(0f, size.height - h), Size(size.width, h), CornerRadius(6.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(dayName, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text(d.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}
