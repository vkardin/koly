package io.github.vkardin.koly.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

/**
 * Power map: the same module the website uses (web/powermap.js, bundled as assets),
 * shown in a WebView. The page is built from the assets and loads nothing but the schedule files.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val lang = LocalConfiguration.current.locales[0].language.let { if (it in setOf("uk", "en", "ru")) it else "uk" }
    val offline = stringResource(UiR.string.map_offline)
    val html = remember(dark, lang, colors.background, colors.onBackground, offline) {
        page(context, lang, dark, colors.background, colors.onBackground, colors.onSurfaceVariant, offline)
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(it).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
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
): String {
    val light = if (dark) "" else """
        .pm{--pm-card:#ffffff;--pm-card2:#eef0f6;--pm-line:rgba(20,26,43,.12);--pm-sea:#e9ecf3;--pm-nodata:#c3c9d8;--pm-border:#ffffff;--pm-warn:#a8530a;--pm-h-on:#f0a818;--pm-h-maybe:#d07a1e;--pm-h-off:#3a4463;--pm-h-unknown:#d3d8e3;--pm-h-road:#ffffff}
        .pm-label{fill:#141a2b;stroke:#ffffff}.pm-label.on-light{stroke:#ffe7b0}
        .pm-hatch-bg{fill:#d5d9e3}.pm-hatch-line{stroke:#b4bbcb}
        .pm-g[data-state="off"]{background:#3a4463;color:#fff}
        .pm-lg-occ::before{background:repeating-linear-gradient(45deg,#d5d9e3 0 3px,#b4bbcb 3px 5px)}
    """
    return """<!doctype html><html lang="$lang"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
html,body{margin:0;background:${css(background)};color:${css(text)};font-family:system-ui,sans-serif;-webkit-text-size-adjust:100%}
body{padding:12px 16px 24px}
#off{margin:0 0 10px;font-size:13px;color:${css(muted)}}
.pm{--pm-text:${css(text)};--pm-muted:${css(muted)}}
${asset(context, "powermap.css")}
$light
</style></head><body>
<p id="off" hidden>${escapeHtml(offline)}</p><div id="map"></div>
<script type="module">
${asset(context, "powermap.js")}
const geo = ${asset(context, "ua-map.json")};
const map = createPowerMap({ geo, lang: document.documentElement.lang, housesBase: '${DATA_BASE}houses/' });
document.getElementById('map').append(map.root);
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
