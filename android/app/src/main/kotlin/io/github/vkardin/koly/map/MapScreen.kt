package io.github.vkardin.koly.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.SyncTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.github.vkardin.koly.core.ui.R as UiR

/** The published data; the page gets this origin so its requests for the schedule files are same-origin. */
private const val DATA_BASE = "https://vkardin.github.io/koly/v1/"
private const val PAGE_BASE = "https://vkardin.github.io/koly/app-map/"

@HiltViewModel
class MapViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sync: SyncTrigger,
) : ViewModel() {
    /** Region of the main place: where "Where am I" goes when the location is not available. */
    val home: StateFlow<String?> = settings.settings.map { it.mainPlace?.regionId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A building picked on the map becomes a place; false when the list is full. */
    fun addPlace(label: String, regionId: String, group: String): Boolean {
        val added = runBlocking { settings.addPlace(label, regionId, group) } != null
        if (added) viewModelScope.launch { sync.syncNow() }
        return added
    }
}

/** What the map page may ask of the app. Only our own page is ever loaded in this WebView. */
private class MapBridge(private val vm: MapViewModel) {
    @JavascriptInterface
    fun addPlace(regionId: String, group: String, label: String): Boolean =
        regionId.length in 1..40 && group.length in 1..10 && vm.addPlace(label.take(60), regionId, group)
}

/**
 * Power map: the same module the website uses (web/powermap.js, bundled as assets),
 * shown in a WebView. The page is built from the assets and loads nothing but the schedule files.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(vm: MapViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val home by vm.home.collectAsStateWithLifecycle()
    // "Where am I": the page asks for the location, Android asks the user once.
    val pending = remember { arrayOfNulls<Pair<String, GeolocationPermissions.Callback>>(1) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.let { (origin, callback) -> callback.invoke(origin, granted, false) }
        pending[0] = null
    }
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val lang = LocalConfiguration.current.locales[0].language.let { if (it in setOf("uk", "en", "ru")) it else "uk" }
    val offline = stringResource(UiR.string.map_offline)
    val html = remember(dark, lang, colors.background, colors.onBackground, offline, home) {
        page(context, lang, dark, colors.background, colors.onBackground, colors.onSurfaceVariant, offline, home)
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(it).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setGeolocationEnabled(true)
                addJavascriptInterface(MapBridge(vm), "KolyApp")
                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                        if (has) {
                            callback.invoke(origin, true, false)
                        } else {
                            pending[0] = origin to callback
                            askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                }
                webViewClient = object : WebViewClient() {
                    // The page has no links to follow; never navigate away from it.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                }
                tag = html
                loadDataWithBaseURL(PAGE_BASE, html, "text/html", "utf-8", null)
            }
        },
        update = {
            if (it.tag != html) {
                it.tag = html
                it.loadDataWithBaseURL(PAGE_BASE, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun asset(context: Context, name: String): String =
    context.assets.open("map/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

private fun css(color: Color): String = "#%06x".format(color.toArgb() and 0xFFFFFF)

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun page(
    context: Context, lang: String, dark: Boolean, background: Color, text: Color, muted: Color, offline: String,
    home: String?,
): String {
    val homeJs = home?.takeIf { it.matches(Regex("[a-z-]{2,40}")) }?.let { "'$it'" } ?: "null"
    // Panels follow the app theme; the map itself has a dark and a light basemap.
    val light = if (dark) "" else """
        .pm{--pm-card:#ffffff;--pm-card2:#eef0f6;--pm-line:rgba(20,26,43,.12);--pm-sea:#e9ecf3;--pm-nodata:#c3c9d8;--pm-border:#ffffff;--pm-warn:#a8530a;--pm-h-on:#f0a818;--pm-h-maybe:#e0701a;--pm-h-off:#3a4ac0;--pm-h-unknown:#9aa3b5}
        .pm-g[data-state="off"]{background:#3a4463;color:#fff}
        .pm-lg-occ::before{background:repeating-linear-gradient(45deg,#d5d9e3 0 3px,#b4bbcb 3px 5px)}
    """
    val theme = if (dark) "dark" else "light"
    return """<!doctype html><html lang="$lang"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
${asset(context, "maplibre-gl-5.24.1.css")}
html,body{margin:0;background:${css(background)};color:${css(text)};font-family:system-ui,sans-serif;-webkit-text-size-adjust:100%}
body{padding:12px 16px 24px}
#off{margin:0 0 10px;font-size:13px;color:${css(muted)}}
.pm{--pm-text:${css(text)};--pm-muted:${css(muted)}}
${asset(context, "powermap.css")}
.pm-stage{aspect-ratio:auto;height:62vh}
$light
</style></head><body>
<p id="off" hidden>${escapeHtml(offline)}</p><div id="map"></div>
<script>${asset(context, "maplibre-gl-5.24.1.js")}</script>
<script type="module">
${asset(context, "powermap.js")}
const regions = ${asset(context, "ua-regions.json")};
const map = createPowerMap({
  regions, lang: document.documentElement.lang, theme: '$theme',
  housesBase: '${DATA_BASE}houses/', home: $homeJs,
  onAddPlace: (p) => window.KolyApp ? KolyApp.addPlace(p.regionId, p.group, p.label) : false,
});
document.getElementById('map').append(map.root);
map.resize();
const off = document.getElementById('off');
async function refresh() {
  try { const { index, files } = await loadAll('$DATA_BASE'); map.update(index, files); off.hidden = true; }
  catch (e) { off.hidden = false; }
}
refresh();
setInterval(refresh, 5 * 60000);
window.addEventListener('online', refresh);
</script></body></html>"""
}
