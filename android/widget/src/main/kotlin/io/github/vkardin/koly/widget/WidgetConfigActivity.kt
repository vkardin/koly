package io.github.vkardin.koly.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.ui.KolyCard
import io.github.vkardin.koly.core.ui.KolyTheme
import io.github.vkardin.koly.core.ui.LocalKolyColors
import kotlinx.coroutines.launch
import io.github.vkardin.koly.core.ui.R as UiR

/** Lets the user pick which place a newly added widget shows. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val ep = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        setContent {
            var settings by remember { mutableStateOf<AppSettings?>(null) }
            LaunchedEffect(Unit) { settings = ep.settings().current() }
            val scope = rememberCoroutineScope()
            val s = settings
            KolyTheme(mode = s?.theme ?: io.github.vkardin.koly.core.model.ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text(stringResource(UiR.string.widget_choose_place), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        if (s != null && s.places.isEmpty()) {
                            Text(stringResource(UiR.string.widget_no_places), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = {
                                packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
                                finish()
                            }) { Text(stringResource(UiR.string.widget_open_app)) }
                        }
                        for (p in s?.places.orEmpty()) {
                            KolyCard(
                                Modifier.heightIn(min = 56.dp).clickable(role = Role.Button) {
                                    WidgetPlaces.set(this@WidgetConfigActivity, appWidgetId, p.id)
                                    scope.launch {
                                        KolyWidgets.updateAll(applicationContext)
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                        finish()
                                    }
                                },
                            ) {
                                Text(p.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(UiR.string.group_value, p.group),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalKolyColors.current.textSecondary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.fillMaxWidth().height(8.dp))
                    }
                }
            }
        }
    }
}
