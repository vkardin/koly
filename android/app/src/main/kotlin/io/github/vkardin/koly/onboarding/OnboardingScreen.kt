package io.github.vkardin.koly.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.core.model.HomeView
import io.github.vkardin.koly.core.model.RegionIndexEntry
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.Wordmark
import io.github.vkardin.koly.feature.places.GroupChips
import io.github.vkardin.koly.feature.places.HowToFindGroup
import io.github.vkardin.koly.feature.places.RegionList
import io.github.vkardin.koly.feature.settings.AppLanguage
import io.github.vkardin.koly.feature.settings.HomeViewPicker
import io.github.vkardin.koly.feature.settings.LanguagePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val regions: List<RegionIndexEntry> = emptyList(),
    val loading: Boolean = false,
    val sourceUrls: Map<String, String> = emptyMap(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val schedules: ScheduleRepository,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val sources = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<OnboardingState> = combine(schedules.snapshot, loading, sources) { snap, busy, urls ->
        OnboardingState(snap.index?.regions.orEmpty(), busy, urls)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingState())

    init {
        loadRegions()
    }

    fun loadRegions() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            try {
                if (schedules.current().index == null) sync.syncNow()
            } finally {
                loading.value = false
            }
        }
    }

    fun loadSource(regionId: String) {
        if (sources.value.containsKey(regionId)) return
        viewModelScope.launch {
            schedules.fetchRegion(regionId)?.sourceUrl?.let { sources.value = sources.value + (regionId to it) }
        }
    }

    fun finish(label: String, regionId: String, group: String, view: HomeView) {
        viewModelScope.launch {
            if (settings.current().places.isEmpty()) settings.addPlace(label, regionId, group)
            settings.setHomeView(view)
            settings.setOnboardingDone()
            sync.syncNow()
        }
    }
}

private const val STEPS = 6

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = LocalKolyColors.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf(AppLanguage.current()) }
    var regionId by rememberSaveable { mutableStateOf<String?>(null) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf(HomeView.CARD) }
    var notifyAsked by rememberSaveable { mutableStateOf(false) }
    var notifyGranted by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifyGranted = granted
        notifyAsked = true
    }
    val region = state.regions.firstOrNull { it.id == regionId }
    LaunchedEffect(regionId) { regionId?.let(viewModel::loadSource) }
    val defaultLabel = stringResource(R.string.default_place_label)

    val canGoNext = when (step) {
        1 -> region != null
        2 -> group != null && region?.groups?.contains(group) == true
        else -> true
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            StepIndicator(step)
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "onboarding-step",
            ) { current ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (current) {
                        0 -> {
                            Wordmark()
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.onb_welcome), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                            Spacer(Modifier.height(24.dp))
                            Title(R.string.onb_language_title)
                            Text(stringResource(R.string.onb_change_language), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            Spacer(Modifier.height(8.dp))
                            LanguagePicker(language, onSelect = {
                                language = it
                                AppLanguage.set(it)
                            })
                        }
                        1 -> {
                            Title(R.string.onb_region_title)
                            when {
                                state.regions.isNotEmpty() -> RegionList(state.regions, regionId, onSelect = {
                                    if (it.id != regionId) group = null
                                    regionId = it.id
                                })
                                state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                                else -> {
                                    Text(stringResource(R.string.load_failed), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = viewModel::loadRegions) { Text(stringResource(R.string.retry)) }
                                }
                            }
                        }
                        2 -> {
                            Title(R.string.onb_group_title)
                            if (region != null) {
                                Text(region.name.forLanguage(language), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                                Spacer(Modifier.height(12.dp))
                                GroupChips(region.groups, group, onSelect = { group = it })
                                Spacer(Modifier.height(8.dp))
                                HowToFindGroup(state.sourceUrls[region.id])
                            }
                        }
                        3 -> {
                            Title(R.string.onb_view_title)
                            HomeViewPicker(view, onSelect = { view = it })
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.change_anytime), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        4 -> {
                            Title(R.string.onb_notify_title)
                            Text(stringResource(R.string.onb_notify_body), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            if (notifyGranted) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.onb_notify_granted), style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Icon(painterResource(R.drawable.ic_bell), contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.onb_notify_allow))
                                }
                                if (notifyAsked) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.onb_notify_denied), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                            }
                        }
                        else -> {
                            Title(R.string.onb_disclaimer_title)
                            KolyCard {
                                Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyMedium)
                                val source = region?.let { state.sourceUrls[it.id] }
                                if (source != null) {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = {
                                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(source))) }
                                    }) {
                                        Text(stringResource(R.string.data_source) + " · " + (android.net.Uri.parse(source).host ?: source))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (step < STEPS - 1) {
                    Button(onClick = { step++ }, enabled = canGoNext) { Text(stringResource(R.string.next)) }
                } else {
                    Button(onClick = { viewModel.finish(defaultLabel, regionId!!, group!!, view) }, enabled = region != null && group != null) {
                        Text(stringResource(R.string.onb_accept))
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun StepIndicator(step: Int) {
    val c = LocalKolyColors.current
    val description = stringResource(R.string.onb_step, step + 1, STEPS)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else c.track),
            )
        }
    }
    Spacer(Modifier.size(4.dp))
    Text(description, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
}
