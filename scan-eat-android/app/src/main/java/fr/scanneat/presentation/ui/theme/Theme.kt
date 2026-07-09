package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Color schemes
// ─────────────────────────────────────────────────────────────────────────────

private val OledColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = Color.Black,
    secondary        = AccentGreen,
    onSecondary      = Color.Black,
    tertiary         = Teal,
    background       = Background,
    onBackground     = OnBackground,
    surface          = SurfaceVariant,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = OnSurface,
    error            = FlagRed,
    onError          = Color.White,
    errorContainer   = Color(0x26EF5350),
    onErrorContainer = FlagRed,
    outline          = Color(0xFF4E4A56),
)

private val DarkColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = Color.Black,
    secondary        = AccentGreen,
    onSecondary      = Color.Black,
    tertiary         = Teal,
    background       = Color(0xFF17141B),
    onBackground     = Color(0xFFEFEAE6),
    surface          = Color(0xFF221E27),
    onSurface        = Color(0xFFCFC7CC),
    surfaceVariant   = Color(0xFF322C38),
    onSurfaceVariant = Color(0xFFCFC7CC),
    error            = FlagRed,
    onError          = Color.White,
    errorContainer   = Color(0x26EF5350),
    onErrorContainer = FlagRed,
    outline          = Color(0xFF4E4A56),
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFFA07828),
    onPrimary        = Color.White,
    secondary        = Color(0xFFB05A38),
    onSecondary      = Color.White,
    tertiary         = Color(0xFF1A9090),
    background       = Color(0xFFF6F1EC),
    onBackground     = Color(0xFF241C1F),
    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF241C1F),
    surfaceVariant   = Color(0xFFF0E7E0),
    onSurfaceVariant = Color(0xFF3A3033),
    error            = Color(0xFFD32F2F),
    onError          = Color.White,
    errorContainer   = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF9B1C1C),
    outline          = Color(0xFFCCBFB8),
)

// ── Gold accent override ──────────────────────────────────────────────────────
// Biolism screens need a darker gold in light theme for legible contrast on a
// light background; every other theme uses the raw Gold token as-is.
val LocalGoldAccent = staticCompositionLocalOf { Gold }
private val LightGoldAccent = Color(0xFF8B6914)

/** "none" | "deuteranopia" | "protanopia" | "tritanopia" — read by gradeColor() and friends. */
val LocalColorblindMode = staticCompositionLocalOf { "none" }

/** Wider letter/line spacing measurably helps dyslexic readers even without a specialty typeface. */
private fun Typography.withDyslexicSpacing(): Typography = copy(
    displayLarge   = displayLarge.copy(letterSpacing = 0.5.sp, lineHeight = displayLarge.lineHeight * 1.15f),
    displayMedium  = displayMedium.copy(letterSpacing = 0.5.sp, lineHeight = displayMedium.lineHeight * 1.15f),
    displaySmall   = displaySmall.copy(letterSpacing = 0.5.sp, lineHeight = displaySmall.lineHeight * 1.15f),
    headlineLarge  = headlineLarge.copy(letterSpacing = 0.4.sp, lineHeight = headlineLarge.lineHeight * 1.15f),
    headlineMedium = headlineMedium.copy(letterSpacing = 0.4.sp, lineHeight = headlineMedium.lineHeight * 1.15f),
    headlineSmall  = headlineSmall.copy(letterSpacing = 0.4.sp, lineHeight = headlineSmall.lineHeight * 1.15f),
    titleLarge     = titleLarge.copy(letterSpacing = 0.3.sp, lineHeight = titleLarge.lineHeight * 1.15f),
    titleMedium    = titleMedium.copy(letterSpacing = 0.3.sp, lineHeight = titleMedium.lineHeight * 1.15f),
    titleSmall     = titleSmall.copy(letterSpacing = 0.3.sp, lineHeight = titleSmall.lineHeight * 1.15f),
    bodyLarge      = bodyLarge.copy(letterSpacing = 0.4.sp, lineHeight = bodyLarge.lineHeight * 1.2f),
    bodyMedium     = bodyMedium.copy(letterSpacing = 0.4.sp, lineHeight = bodyMedium.lineHeight * 1.2f),
    bodySmall      = bodySmall.copy(letterSpacing = 0.4.sp, lineHeight = bodySmall.lineHeight * 1.2f),
    labelLarge     = labelLarge.copy(letterSpacing = 0.3.sp, lineHeight = labelLarge.lineHeight * 1.2f),
    labelMedium    = labelMedium.copy(letterSpacing = 0.3.sp, lineHeight = labelMedium.lineHeight * 1.2f),
    labelSmall     = labelSmall.copy(letterSpacing = 0.3.sp, lineHeight = labelSmall.lineHeight * 1.2f),
)

/**
 * Root theme. Pass [theme] from UserPreferences ("oled" | "dark" | "light").
 * All screens in the app use this — both Scan'eat and Biolism sections.
 */
@Composable
fun ScanEatTheme(
    theme: String = "oled",
    dyslexicFont: Boolean = false,
    colorblindMode: String = "none",
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        "dark"  -> DarkColors
        "light" -> LightColors
        else    -> OledColors
    }
    val goldAccent = if (theme == "light") LightGoldAccent else Gold
    val typography = if (dyslexicFont) ScanEatTypography.withDyslexicSpacing() else ScanEatTypography
    CompositionLocalProvider(LocalGoldAccent provides goldAccent, LocalColorblindMode provides colorblindMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
