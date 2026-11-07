package io.github.vkardin.koly.feature.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.core.model.Place
import io.github.vkardin.koly.core.model.RegionIndexEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlacesState(
    val loaded: Boolean = false,
    val places: List<Place> = emptyList(),
    val mainPlaceId: String? = null,
    val regions: List<RegionIndexEntry> = emptyList(),
    val loadingRegions: Boolean = false,
    /** Source links of regions, for "Як дізнатися свою чергу?". */
    val sourceUrls: Map<String, String> = emptyMap(),
)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val schedules: ScheduleRepository,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val sources = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<PlacesState> = combine(settings.settings, schedules.snapshot, loading, sources) { s, snap, busy, urls ->
        PlacesState(
            loaded = true,
            places = s.places,
            mainPlaceId = s.mainPlace?.id,
            regions = snap.index?.regions.orEmpty(),
            loadingRegions = busy,
            sourceUrls = urls + snap.regions.mapNotNull { (id, r) -> r.sourceUrl?.let { id to it } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesState())

    /** Makes sure the list of regions is available. */
    fun ensureRegions() {
        if (state.value.regions.isNotEmpty() || loading.value) return
        viewModelScope.launch {
            loading.value = true
            try {
                sync.syncNow()
            } finally {
                loading.value = false
            }
        }
    }

    /** Looks up the provider link of a region that is not saved yet. */
    fun loadSource(regionId: String) {
        if (state.value.sourceUrls.containsKey(regionId)) return
        viewModelScope.launch {
            schedules.fetchRegion(regionId)?.sourceUrl?.let { url -> sources.value = sources.value + (regionId to url) }
        }
    }

    fun save(existing: Place?, label: String, regionId: String, group: String) {
        viewModelScope.launch {
            if (existing == null) {
                settings.addPlace(label, regionId, group)
            } else {
                settings.updatePlace(existing.copy(label = label, regionId = regionId, group = group))
            }
            sync.syncNow()
        }
    }

    fun remove(place: Place) {
        viewModelScope.launch {
            settings.removePlace(place.id)
            sync.refreshLocal()
        }
    }

    fun move(place: Place, delta: Int) {
        viewModelScope.launch { settings.movePlace(place.id, delta) }
    }

    fun makeMain(place: Place) {
        viewModelScope.launch {
            settings.setMainPlace(place.id)
            sync.refreshLocal()
        }
    }
}
