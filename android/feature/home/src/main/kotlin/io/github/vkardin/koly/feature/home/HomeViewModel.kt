package io.github.vkardin.koly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.DataSnapshot
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.model.KyivNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class HomeState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val snapshot: DataSnapshot = DataSnapshot(),
    val now: KyivNow = KyivNow.now(),
    val selectedPlaceId: String? = null,
    val refreshing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    scheduleRepository: ScheduleRepository,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<HomeMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<HomeMessage> = _messages

    /** Ticks a little after each new minute so the countdown stays exact. */
    private val ticker = flow {
        while (true) {
            val now = KyivNow.now()
            emit(now)
            val secondsLeft = 60 - (now.instant.epochSecond % 60)
            delay(secondsLeft * 1000L + 200L)
        }
    }

    val state: StateFlow<HomeState> = combine(
        settingsRepository.settings,
        scheduleRepository.snapshot,
        ticker,
        selected,
        refreshing,
    ) { settings, snapshot, now, sel, busy ->
        HomeState(
            loaded = true,
            settings = settings,
            snapshot = snapshot,
            now = now,
            selectedPlaceId = sel?.takeIf { id -> settings.places.any { it.id == id } } ?: settings.mainPlace?.id,
            refreshing = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun select(placeId: String) {
        selected.value = placeId
    }

    fun refresh(userInitiated: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = try {
                sync.syncNow()
            } finally {
                refreshing.value = false
            }
            if (!result.ok && userInitiated) _messages.tryEmit(HomeMessage.RefreshFailed)
        }
    }

    /** Called when the screen appears: refresh if the last check is older than a couple of minutes. */
    fun onShown() {
        val checked = state.value.snapshot.checkedAt
        if (checked == null || Duration.between(checked, Instant.now()) > Duration.ofMinutes(2)) refresh(userInitiated = false)
    }
}

enum class HomeMessage { RefreshFailed }
