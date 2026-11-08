package io.github.vkardin.koly.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.data.buildPlaceView
import io.github.vkardin.koly.core.model.HomeView
import io.github.vkardin.koly.core.model.MAX_PLACES
import io.github.vkardin.koly.core.model.formatCountdown
import io.github.vkardin.koly.core.ui.KolyText
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.Wordmark
import io.github.vkardin.koly.core.ui.appLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    onAddPlace: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val failedText = stringResource(R.string.refresh_failed)
    LifecycleResumeEffect(Unit) {
        viewModel.onShown()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(failedText) }
    }
    if (!state.loaded) return
    val lang = ctx.appLanguage()
    val views = state.settings.places.map { buildPlaceView(it, state.snapshot, state.now, lang) }
    val selected = views.firstOrNull { it.place.id == state.selectedPlaceId } ?: views.firstOrNull()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh))
                }
            }
            if (views.isEmpty() || selected == null) {
                EmptyPlaces(onAddPlace)
                return@Column
            }
            val emergencyViews = if (state.settings.homeView == HomeView.LIST) views else listOf(selected)
            if (emergencyViews.any { it.emergency }) {
                EmergencyBanner(emergencyViews.first { it.emergency })
                Spacer(Modifier.height(12.dp))
            }
            AnimatedContent(
                targetState = state.settings.homeView,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "home-view",
            ) { view ->
                Column {
                    when (view) {
                        HomeView.CARD -> CardAndBarView(views, selected, state, onSelect = viewModel::select, onAddPlace = onAddPlace)
                        HomeView.RING -> DayRingView(views, selected, state, onSelect = viewModel::select)
                        HomeView.LIST -> AllPlacesView(views, state)
                    }
                }
            }
            FreshnessLine(state, selected)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun PlaceChips(
    views: List<PlaceView>,
    selected: PlaceView,
    onSelect: (String) -> Unit,
    onAddPlace: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (v in views) {
            FilterChip(
                selected = v.place.id == selected.place.id,
                onClick = { onSelect(v.place.id) },
                label = { Text(v.place.label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
        if (onAddPlace != null && views.size < MAX_PLACES) {
            FilterChip(
                selected = false,
                onClick = onAddPlace,
                label = { Text(stringResource(R.string.add)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun EmptyPlaces(onAddPlace: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.no_places), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddPlace) { Text(stringResource(R.string.add_place)) }
    }
}

@Composable
internal fun EmergencyBanner(view: PlaceView) {
    val c = LocalKolyColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.emergency.copy(alpha = 0.16f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = c.emergency)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(R.string.emergency_title) + " · " + view.regionName,
                style = MaterialTheme.typography.titleSmall,
                color = c.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                view.region?.emergency?.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.emergency_body),
                style = MaterialTheme.typography.bodySmall,
                color = c.text,
            )
        }
    }
}

@Composable
private fun FreshnessLine(state: HomeState, selected: PlaceView) {
    val c = LocalKolyColors.current
    val ctx = LocalContext.current
    val ago = KolyText.ago(ctx, state.snapshot.checkedAt, state.now.instant)
    val parts = buildList {
        add(stringResource(R.string.updated_ago, ago))
        if (state.snapshot.offline) add(stringResource(R.string.offline))
        if (selected.stale) add(stringResource(R.string.data_stale))
        add(stringResource(R.string.unofficial_app))
    }
    Spacer(Modifier.height(12.dp))
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.snapshot.offline || selected.stale) c.maybeText else c.textSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Countdown text and its spoken form for a place, or null when no change is known. */
internal data class Countdown(val text: String, val description: String)

@Composable
internal fun countdownOf(view: PlaceView, state: HomeState): Countdown? {
    val ctx = LocalContext.current
    val d = view.status.untilChange(state.now.instant) ?: return null
    if (!view.hasData) return null
    return Countdown(formatCountdown(d), KolyText.countdownDescription(ctx, d))
}

internal fun Modifier.countdownSemantics(c: Countdown?): Modifier =
    if (c == null) this else semantics { contentDescription = c.description }

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = LocalKolyColors.current.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}
