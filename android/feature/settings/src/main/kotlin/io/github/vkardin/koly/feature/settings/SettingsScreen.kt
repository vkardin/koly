package io.github.vkardin.koly.feature.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.AppInfo
import io.github.vkardin.koly.core.data.DataSnapshot
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.model.HomeView
import io.github.vkardin.koly.core.model.LEAD_MINUTES_OPTIONS
import io.github.vkardin.koly.core.model.NotifyScope
import io.github.vkardin.koly.core.model.NotifySettings
import io.github.vkardin.koly.core.model.ThemeMode
import io.github.vkardin.koly.core.ui.AdSlot
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.KolyText
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.LogoMark
import io.github.vkardin.koly.core.ui.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val SITE_URL = "https://vkardin.github.io"
const val PRIVACY_URL = "https://vkardin.github.io/koly/privacy/"
const val REPORT_URL = "https://github.com/vkardin/koly/issues/new?template=schedule.yml"

data class SettingsState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val snapshot: DataSnapshot = DataSnapshot(),
    val refreshing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val schedules: ScheduleRepository,
    private val sync: SyncTrigger,
    val appInfo: AppInfo,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<SettingsState> = combine(settings.settings, schedules.snapshot, refreshing) { s, snap, busy ->
        SettingsState(true, s, snap, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setHomeView(v: HomeView) = viewModelScope.launch { settings.setHomeView(v) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { settings.setTheme(t) }
    fun setDynamic(on: Boolean) = viewModelScope.launch { settings.setDynamicColor(on) }

    fun updateNotify(transform: (NotifySettings) -> NotifySettings) = viewModelScope.launch {
        settings.updateNotify(transform)
        sync.refreshLocal()
    }

    fun refreshNow() = viewModelScope.launch {
        refreshing.value = true
        try {
            sync.syncNow()
        } finally {
            refreshing.value = false
        }
    }

    fun reset() = viewModelScope.launch {
        settings.reset()
        schedules.clear()
        sync.refreshLocal()
    }
}

fun canScheduleExact(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun SettingsScreen(
    onOpenPlaces: () -> Unit,
    onTestNotification: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = LocalKolyColors.current
    var exactAllowed by remember { mutableStateOf(canScheduleExact(ctx)) }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var confirmReset by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(AppLanguage.current()) }
    LifecycleResumeEffect(Unit) {
        exactAllowed = canScheduleExact(ctx)
        notificationsOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    if (!state.loaded) return
    val s = state.settings
    val n = s.notify

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))

        Section(R.string.sec_appearance) {
            Label(R.string.home_view)
            HomeViewPicker(s.homeView, viewModel::setHomeView)
            Spacer(Modifier.height(16.dp))
            Label(R.string.theme)
            ChipRow(
                options = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark),
                selected = s.theme,
                onSelect = viewModel::setTheme,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(R.string.dynamic_color, s.dynamicColor, viewModel::setDynamic, hint = R.string.dynamic_color_hint)
            }
        }

        Section(R.string.sec_places) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenPlaces),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_pin), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.places_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.places_count, s.places.size, 10), color = c.textSecondary)
            }
        }

        Section(R.string.sec_notifications) {
            if (!notificationsOn) {
                Text(stringResource(R.string.notifications_blocked), color = c.emergency, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    runCatching { ctx.startActivity(intent) }
                }) { Text(stringResource(R.string.open_system_settings)) }
            }
            SwitchRow(R.string.before_off, n.beforeOff, { v -> viewModel.updateNotify { it.copy(beforeOff = v) } })
            SwitchRow(R.string.before_on, n.beforeOn, { v -> viewModel.updateNotify { it.copy(beforeOn = v) } })
            SwitchRow(R.string.schedule_changed, n.scheduleChanged, { v -> viewModel.updateNotify { it.copy(scheduleChanged = v) } })
            SwitchRow(R.string.tomorrow_published, n.tomorrowPublished, { v -> viewModel.updateNotify { it.copy(tomorrowPublished = v) } })
            Spacer(Modifier.height(8.dp))
            Label(R.string.lead_time)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in LEAD_MINUTES_OPTIONS) {
                    FilterChip(
                        selected = n.leadMinutes == m,
                        onClick = { viewModel.updateNotify { it.copy(leadMinutes = m) } },
                        label = { Text(stringResource(R.string.minutes_short, m)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Label(R.string.for_places)
            ChipRow(
                options = listOf(NotifyScope.MAIN to R.string.for_main, NotifyScope.ALL to R.string.for_all),
                selected = n.scope,
                onSelect = { v -> viewModel.updateNotify { it.copy(scope = v) } },
            )
            SwitchRow(R.string.quiet_night, n.quietNight, { v -> viewModel.updateNotify { it.copy(quietNight = v) } })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.exact_alarms), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.exact_alarms_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    if (exactAllowed) {
                        Text(stringResource(R.string.exact_alarms_on), color = c.textSecondary)
                    } else {
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                            runCatching { ctx.startActivity(intent) }
                        }) { Text(stringResource(R.string.allow)) }
                    }
                }
            }
            OutlinedButton(onClick = onTestNotification) { Text(stringResource(R.string.test_notification)) }
        }

        Section(R.string.sec_general) {
            Label(R.string.language)
            LanguagePicker(language, onSelect = {
                language = it
                AppLanguage.set(it)
            })
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.data_updated, KolyText.ago(ctx, state.snapshot.checkedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            OutlinedButton(onClick = { viewModel.refreshNow() }, enabled = !state.refreshing) {
                Text(stringResource(R.string.refresh_now))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_all), color = c.emergency) }
        }

        Section(R.string.sec_about) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.version, viewModel.appInfo.versionName), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Text(stringResource(R.string.author), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            val sources = s.places.mapNotNull { p -> state.snapshot.regions[p.regionId]?.sourceUrl }.distinct()
            for (url in sources) {
                LinkRow(stringResource(R.string.data_source) + " · " + Uri.parse(url).host.orEmpty()) { openUrl(ctx, url) }
            }
            LinkRow(stringResource(R.string.website)) { openUrl(ctx, SITE_URL) }
            LinkRow(stringResource(R.string.privacy)) { openUrl(ctx, PRIVACY_URL) }
            LinkRow(stringResource(R.string.report_error)) { openUrl(ctx, REPORT_URL) }
        }
        AdSlot()
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(R.string.reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.reset()
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp).semantics { heading() },
    )
    KolyCard { content() }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SwitchRow(title: Int, checked: Boolean, onChange: (Boolean) -> Unit, hint: Int? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = LocalKolyColors.current.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_open), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = LocalKolyColors.current.track)
}
