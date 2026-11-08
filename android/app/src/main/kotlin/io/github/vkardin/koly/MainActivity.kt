package io.github.vkardin.koly

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.ui.KolyTheme
import io.github.vkardin.koly.feature.home.HomeScreen
import io.github.vkardin.koly.feature.places.PlacesScreen
import io.github.vkardin.koly.feature.settings.SettingsScreen
import io.github.vkardin.koly.feature.stats.StatsScreen
import io.github.vkardin.koly.notify.Notifier
import io.github.vkardin.koly.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.github.vkardin.koly.core.ui.R as UiR

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
    val notifier: Notifier,
) : ViewModel() {
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
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: Int, val icon: Int) {
    HOME("home", UiR.string.nav_home, UiR.drawable.ic_home),
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
