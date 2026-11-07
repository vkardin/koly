package io.github.vkardin.koly.feature.places

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.vkardin.koly.core.model.RegionIndexEntry
import io.github.vkardin.koly.core.model.RegionStatus
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.Tag
import io.github.vkardin.koly.core.ui.appLanguage
import java.text.Collator
import java.util.Locale

/** Regions sorted alphabetically in the app language. */
fun sortedRegions(regions: List<RegionIndexEntry>, lang: String): List<RegionIndexEntry> {
    val collator = Collator.getInstance(Locale.forLanguageTag(lang))
    return regions.sortedWith { a, b -> collator.compare(a.name.forLanguage(lang), b.name.forLanguage(lang)) }
}

/** Radio list of regions; meant to be placed inside a scrolling container. */
@Composable
fun RegionList(
    regions: List<RegionIndexEntry>,
    selectedId: String?,
    onSelect: (RegionIndexEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalContext.current.appLanguage()
    val sorted = remember(regions, lang) { sortedRegions(regions, lang) }
    Column(modifier) {
        for (r in sorted) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = r.id == selectedId, role = Role.RadioButton) { onSelect(r) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = r.id == selectedId, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(r.name.forLanguage(lang), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (r.status == RegionStatus.NO_SCHEDULES) Tag(stringResource(R.string.region_no_schedules_badge))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupChips(
    groups: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (g in groups) {
            FilterChip(
                selected = g == selected,
                onClick = { onSelect(g) },
                label = { Text(g) },
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
    }
}

/** "Як дізнатися свою чергу?" link with an explanation and the provider's page. */
@Composable
fun HowToFindGroup(sourceUrl: String?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button) { open = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.how_to_find_group), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.how_to_find_group)) },
            text = { Text(stringResource(R.string.how_to_find_group_body), color = LocalKolyColors.current.text) },
            confirmButton = {
                if (sourceUrl != null) {
                    TextButton(onClick = {
                        open = false
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
                    }) { Text(stringResource(R.string.open_provider_site)) }
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RegionDropdown(
    regions: List<RegionIndexEntry>,
    selectedId: String?,
    onSelect: (RegionIndexEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalContext.current.appLanguage()
    val sorted = remember(regions, lang) { sortedRegions(regions, lang) }
    var expanded by remember { mutableStateOf(false) }
    val selected = sorted.firstOrNull { it.id == selectedId }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        androidx.compose.material3.OutlinedTextField(
            value = selected?.name?.forLanguage(lang).orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.region)) },
            placeholder = { Text(stringResource(R.string.choose_region)) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (r in sorted) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(r.name.forLanguage(lang)) },
                    onClick = {
                        onSelect(r)
                        expanded = false
                    },
                )
            }
        }
    }
}
