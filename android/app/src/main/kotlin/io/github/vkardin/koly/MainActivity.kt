package io.github.vkardin.koly

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.AppUpdater
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.UpdateState
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.ui.KolyTheme
import io.github.vkardin.koly.feature.home.HomeScreen
import io.github.vkardin.koly.feature.places.PlacesScreen
import io.github.vkardin.koly.feature.settings.SettingsScreen
import io.github.vkardin.koly.feature.stats.StatsScreen
import io.github.vkardin.koly.map.MapScreen
import io.github.vkardin.koly.notify.Notifier
import io.github.vkardin.koly.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.github.vkardin.koly.core.ui.R as UiR

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
    val notifier: Notifier,
    val updater: AppUpdater,
) : ViewModel() {
    fun install() = viewModelScope.launch { updater.install() }

    /** Null until the stored settings are read. */
    val settings: StateFlow<AppSettings?> = settings.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val root: RootViewModel = hiltViewModel()
            val settings by root.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            KolyTheme(mode = s.theme, dynamicColor = s.dynamicColor) {
                if (!s.onboardingDone) {
                    OnboardingScreen()
                } else {
                    MainNavigation(onTestNotification = root.notifier::showTest)
                    UpdatePrompt(root)
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: Int, val icon: Int) {
    HOME("home", UiR.string.nav_home, UiR.drawable.ic_home),
    MAP("map", UiR.string.nav_map, UiR.drawable.ic_map),
    STATS("stats", UiR.string.nav_stats, UiR.drawable.ic_stats),
    SETTINGS("settings", UiR.string.nav_settings, UiR.drawable.ic_settings),
}

private const val ROUTE_PLACES = "places?add={add}"

@Composable
private fun MainNavigation(onTestNotification: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(snackbar = snackbar, onAddPlace = { nav.navigate("places?add=true") })
            }
            composable(Tab.MAP.route) { MapScreen() }
            composable(Tab.STATS.route) { StatsScreen() }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(onOpenPlaces = { nav.navigate("places?add=false") }, onTestNotification = onTestNotification)
            }
            composable(ROUTE_PLACES) { e ->
                PlacesScreen(
                    onBack = { nav.popBackStack() },
                    startWithEditor = e.arguments?.getString("add") == "true",
                )
            }
        }
    }
}

/** Offers a newer release once on start (at most every few hours), unless the user postponed it. */
@Composable
private fun UpdatePrompt(root: RootViewModel) {
    val updater = root.updater
    if (!updater.supported) return
    LaunchedEffect(Unit) { updater.check() }
    val update by updater.state.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    if (dismissed) return
    val u = update
    if (!accepted) {
        val available = u as? UpdateState.Available ?: return
        if (updater.isPostponed(available.version)) return
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text(stringResource(UiR.string.update_dialog_title)) },
            text = {
                Text(stringResource(UiR.string.update_dialog_text, available.version,
                    android.text.format.Formatter.formatShortFileSize(ctx, available.sizeBytes)))
            },
            confirmButton = {
                TextButton(onClick = { accepted = true; root.install() }) { Text(stringResource(UiR.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = { dismissed = true; updater.postpone(available.version) }) { Text(stringResource(UiR.string.update_later)) }
            },
        )
        return
    }
    // After "Update": show the download, the permission Android asks for, or what went wrong.
    val text = when (u) {
        is UpdateState.Downloading -> stringResource(UiR.string.update_downloading, u.version, (u.progress * 100).toInt())
        is UpdateState.NeedsPermission -> stringResource(UiR.string.update_permission)
        is UpdateState.Installing -> stringResource(UiR.string.update_installing, u.version)
        is UpdateState.Failed -> stringResource(UiR.string.update_failed, u.reason)
        else -> return
    }
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(UiR.string.update_dialog_title)) },
        text = { Text(text) },
        confirmButton = {
            if (u is UpdateState.NeedsPermission) {
                TextButton(onClick = {
                    ctx.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:" + ctx.packageName),
                        ),
                    )
                }) { Text(stringResource(UiR.string.update_permission_button)) }
            }
        },
        dismissButton = {
            if (u is UpdateState.NeedsPermission) {
                TextButton(onClick = { root.install() }) { Text(stringResource(UiR.string.update_install)) }
            } else {
                TextButton(onClick = { dismissed = true }) { Text(stringResource(UiR.string.close)) }
            }
        },
    )
}
