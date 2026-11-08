package io.github.vkardin.koly.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.vkardin.koly.core.model.HomeView
import io.github.vkardin.koly.core.ui.LocalKolyColors
import io.github.vkardin.koly.core.ui.R
import io.github.vkardin.koly.core.ui.Tag

/** Per-app language. Uses the system setting on Android 13+ and stored locales below. */
object AppLanguage {
    val ALL = listOf("uk", "en", "ru")

    fun current(): String {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language
        return tag?.takeIf { it in ALL } ?: "uk"
    }

    fun isSet(): Boolean = !AppCompatDelegate.getApplicationLocales().isEmpty

    fun set(lang: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }
}

@Composable
fun languageName(lang: String): String = stringResource(
    when (lang) {
        "en" -> R.string.lang_en
        "ru" -> R.string.lang_ru
        else -> R.string.lang_uk
    },
)

@Composable
fun LanguagePicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (lang in AppLanguage.ALL) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = lang == selected, role = Role.RadioButton) { onSelect(lang) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = lang == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(languageName(lang), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun homeViewName(view: HomeView): String = stringResource(
    when (view) {
        HomeView.CARD -> R.string.view_a
        HomeView.RING -> R.string.view_b
        HomeView.LIST -> R.string.view_c
    },
)

/** Three cards with mini icons; A is marked as recommended. */
@Composable
fun HomeViewPicker(selected: HomeView, onSelect: (HomeView) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKolyColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (view in HomeView.entries) {
            val isSelected = view == selected
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.card,
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else c.track),
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(view) },
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MiniIcon(view)
                    Spacer(Modifier.height(8.dp))
                    Text(homeViewName(view), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    if (view == HomeView.CARD) {
                        Spacer(Modifier.height(4.dp))
                        Tag(stringResource(R.string.recommended), color = MaterialTheme.colorScheme.primaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniIcon(view: HomeView) {
    val c = LocalKolyColors.current
    Canvas(Modifier.size(width = 56.dp, height = 64.dp)) {
        val w = size.width
        val r = CornerRadius(3.dp.toPx())
        when (view) {
            HomeView.CARD -> {
                drawRoundRect(c.track, Offset(0f, 0f), Size(w, size.height * 0.42f), r)
                drawCircle(c.on, 3.dp.toPx(), Offset(8.dp.toPx(), 8.dp.toPx()))
                drawRoundRect(c.text.copy(alpha = 0.7f), Offset(6.dp.toPx(), 14.dp.toPx()), Size(w * 0.5f, 7.dp.toPx()), r)
                val y1 = size.height * 0.55f
                drawRoundRect(c.on, Offset(0f, y1), Size(w * 0.6f, 6.dp.toPx()), r)
                drawRoundRect(c.off, Offset(w * 0.6f, y1), Size(w * 0.4f, 6.dp.toPx()), r)
                val y2 = size.height * 0.75f
                drawRoundRect(c.off, Offset(0f, y2), Size(w * 0.3f, 6.dp.toPx()), r)
                drawRoundRect(c.on, Offset(w * 0.3f, y2), Size(w * 0.7f, 6.dp.toPx()), r)
            }
            HomeView.RING -> {
                val d = w * 0.8f
                val tl = Offset((w - d) / 2, 2.dp.toPx())
                val sw = 6.dp.toPx()
                drawArc(c.on, -90f, 250f, false, tl, Size(d, d), style = Stroke(sw))
                drawArc(c.off, 160f, 110f, false, tl, Size(d, d), style = Stroke(sw))
                drawRoundRect(c.text.copy(alpha = 0.7f), Offset(w * 0.35f, tl.y + d / 2 - 3.dp.toPx()), Size(w * 0.3f, 6.dp.toPx()), r)
            }
            HomeView.LIST -> {
                for (i in 0 until 3) {
                    val y = i * size.height / 3.2f
                    drawRoundRect(c.track, Offset(0f, y), Size(w, size.height / 3.8f), r)
                    drawCircle(if (i == 1) c.off else c.on, 2.5.dp.toPx(), Offset(6.dp.toPx(), y + 6.dp.toPx()))
                    drawRoundRect(c.on, Offset(4.dp.toPx(), y + 11.dp.toPx()), Size(w * 0.55f, 3.dp.toPx()), r)
                    drawRoundRect(c.off, Offset(4.dp.toPx() + w * 0.55f, y + 11.dp.toPx()), Size(w * 0.3f, 3.dp.toPx()), r)
                }
            }
        }
    }
}
