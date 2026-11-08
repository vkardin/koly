package io.github.vkardin.koly.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.ThemeMode

/** Brand colors that stay the same with or without Material You. */
@Immutable
data class KolyColors(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val on: Color,
    val onText: Color,
    val off: Color,
    val offText: Color,
    val maybeStripe: Color,
    val maybeBase: Color,
    val maybeText: Color,
    val track: Color,
    val marker: Color,
    val emergency: Color,
    val mark: Color,
) {
    fun stateColor(state: PowerState): Color = when (state) {
        PowerState.ON -> on
        PowerState.OFF -> off
        PowerState.MAYBE -> maybeStripe
    }

    fun stateText(state: PowerState): Color = when (state) {
        PowerState.ON -> onText
        PowerState.OFF -> offText
        PowerState.MAYBE -> maybeText
    }
}

val DarkKolyColors = KolyColors(
    dark = true,
    background = Color(0xFF0F1422),
    card = Color(0xFF171E30),
    text = Color(0xFFF4F6FB),
    textSecondary = Color(0xFFAEB6CA),
    on = Color(0xFFFFC24A),
    onText = Color(0xFFFFC24A),
    off = Color(0xFF2A3350),
    offText = Color(0xFFC9D0E4),
    maybeStripe = Color(0xFF8A6A2B),
    maybeBase = Color(0xFF2A3350),
    maybeText = Color(0xFFE0B860),
    track = Color(0xFF222A40),
    marker = Color(0xFFF4F6FB),
    emergency = Color(0xFFFF8A65),
    mark = Color(0xFF5B6B96),
)

val LightKolyColors = KolyColors(
    dark = false,
    background = Color(0xFFF4F5F9),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF141A2B),
    textSecondary = Color(0xFF545D75),
    on = Color(0xFFFFC24A),
    onText = Color(0xFF8A5A00),
    off = Color(0xFF3A4463),
    offText = Color(0xFF3A4463),
    maybeStripe = Color(0xFFD9A33A),
    maybeBase = Color(0xFF3A4463),
    maybeText = Color(0xFF7A5200),
    track = Color(0xFFE3E6EF),
    marker = Color(0xFF141A2B),
    emergency = Color(0xFFB3261E),
    mark = Color(0xFF5B6B96),
)

val LocalKolyColors = staticCompositionLocalOf { DarkKolyColors }

val Sora = FontFamily(
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val base = Typography()

val KolyTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)

/** Big countdown digits. */
val CountdownStyle = TextStyle(
    fontFamily = Sora,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
    lineHeight = 68.sp,
    letterSpacing = (-1).sp,
)

private fun brandScheme(c: KolyColors): ColorScheme {
    val scheme = if (c.dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = if (c.dark) c.on else Color(0xFF8A5A00),
        onPrimary = if (c.dark) Color(0xFF241A00) else Color.White,
        primaryContainer = if (c.dark) Color(0xFF3A2E10) else Color(0xFFFFE3A3),
        onPrimaryContainer = if (c.dark) Color(0xFFFFE3A3) else Color(0xFF2B1D00),
        secondary = c.mark,
        secondaryContainer = if (c.dark) Color(0xFF2A3350) else Color(0xFFDDE2F2),
        onSecondaryContainer = c.text,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        surfaceVariant = c.card,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.card,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        surfaceContainerHighest = if (c.dark) Color(0xFF1F2740) else Color(0xFFECEEF5),
        outline = if (c.dark) Color(0xFF4A5470) else Color(0xFF8D95AA),
        outlineVariant = c.track,
    )
}

@Composable
fun KolyTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val brand = if (dark) DarkKolyColors else LightKolyColors
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        brandScheme(brand)
    }
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        brand.copy(
            background = scheme.background,
            card = scheme.surfaceContainer,
            text = scheme.onSurface,
            textSecondary = scheme.onSurfaceVariant,
            track = scheme.surfaceContainerHighest,
        )
    } else {
        brand
    }
    CompositionLocalProvider(LocalKolyColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = KolyTypography, content = content)
    }
}
