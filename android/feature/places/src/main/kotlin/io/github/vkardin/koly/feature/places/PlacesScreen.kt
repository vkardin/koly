package io.github.vkardin.koly.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vkardin.koly.core.model.MAX_PLACES
import io.github.vkardin.koly.core.model.Place
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.Tag
import io.github.vkardin.koly.core.ui.appLanguage
import androidx.compose.ui.platform.LocalContext
import io.github.vkardin.koly.core.data.regionName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    startWithEditor: Boolean = false,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Place?>(null) }
    var adding by rememberSaveable { mutableStateOf(startWithEditor) }
    var removing by remember { mutableStateOf<Place?>(null) }
    val lang = LocalContext.current.appLanguage()
    val c = LocalKolyColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.places_count, state.places.size, MAX_PLACES),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            state.places.forEachIndexed { i, place ->
                val isMain = place.id == state.mainPlaceId
                KolyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(place.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (isMain) {
                                    Spacer(Modifier.width(8.dp))
                                    Tag(stringResource(R.string.main_badge))
                                }
                            }
                            val entry = state.regions.firstOrNull { it.id == place.regionId }
                            Text(
                                "${regionName(entry, place.regionId, lang)} · ${stringResource(R.string.group_value, place.group)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        IconButton(onClick = { viewModel.move(place, -1) }, enabled = i > 0) {
                            Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { viewModel.move(place, 1) }, enabled = i < state.places.lastIndex) {
                            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.move_down))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isMain) {
                            TextButton(onClick = { viewModel.makeMain(place) }) { Text(stringResource(R.string.make_main)) }
                        }
                        TextButton(onClick = { editing = place }) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = { removing = place }) { Text(stringResource(R.string.remove)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (state.places.size < MAX_PLACES) {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_place))
                }
            } else {
                Text(stringResource(R.string.limit_reached), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding || editing != null) {
        PlaceEditorDialog(
            existing = editing,
            state = state,
            onEnsureRegions = viewModel::ensureRegions,
            onLoadSource = viewModel::loadSource,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { label, region, group ->
                viewModel.save(editing, label, region, group)
                adding = false
                editing = null
            },
        )
    }
    removing?.let { place ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_place_confirm, place.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(place)
                    removing = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceEditorDialog(
    existing: Place?,
    state: PlacesState,
    onEnsureRegions: () -> Unit,
    onLoadSource: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val defaultLabel = stringResource(R.string.default_place_label)
    var label by rememberSaveable { mutableStateOf(existing?.label ?: if (state.places.isEmpty()) defaultLabel else "") }
    var regionId by rememberSaveable { mutableStateOf(existing?.regionId) }
    var group by rememberSaveable { mutableStateOf(existing?.group) }
    LaunchedEffect(Unit) { onEnsureRegions() }
    LaunchedEffect(regionId) { regionId?.let(onLoadSource) }
    val region = state.regions.firstOrNull { it.id == regionId }
    val canSave = label.isNotBlank() && region != null && group != null && group in region.groups

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (existing == null) R.string.add_place else R.string.edit_place)) },
                    actions = {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { onSave(label, regionId!!, group!!) }, enabled = canSave) {
                            Text(stringResource(R.string.save))
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    label = { Text(stringResource(R.string.place_name)) },
                    placeholder = { Text(stringResource(R.string.place_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                when {
                    state.regions.isNotEmpty() -> {
                        RegionDropdown(state.regions, regionId, onSelect = {
                            if (it.id != regionId) group = null
                            regionId = it.id
                        })
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.group), style = MaterialTheme.typography.titleSmall)
                        if (region == null) {
                            Text(stringResource(R.string.choose_region), style = MaterialTheme.typography.bodyMedium, color = LocalKolyColors.current.textSecondary)
                        } else {
                            Spacer(Modifier.height(8.dp))
                            GroupChips(region.groups, group, onSelect = { group = it })
                            HowToFindGroup(state.sourceUrls[region.id])
                        }
                    }
                    state.loadingRegions -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> Column {
                        Text(stringResource(R.string.load_failed), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onEnsureRegions) { Text(stringResource(R.string.retry)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
