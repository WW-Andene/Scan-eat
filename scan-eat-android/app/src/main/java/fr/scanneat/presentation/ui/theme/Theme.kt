package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.scanneat.R

// ─────────────────────────────────────────────────────────────────────────────
// Color schemes
// ─────────────────────────────────────────────────────────────────────────────

private val OledColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = Color.Black,
    secondary        = AccentCoral,
    onSecondary      = Color.Black,
    tertiary         = Teal,
    background       = OledBackgroundRaw,
    onBackground     = OledOnBackgroundRaw,
    surface          = OledSurfaceRaw,
    onSurface        = OledOnSurfaceRaw,
    surfaceVariant   = OledSurfaceVariantRaw,
    onSurfaceVariant = OledOnSurfaceRaw,
    error            = FlagRed,
    onError          = Color.White,
    errorContainer   = Color(0x26EF5350),
    onErrorContainer = FlagRed,
    outline          = Color(0xFF4E4A56),
)

private val DarkColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = Color.Black,
    secondary        = AccentCoral,
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

// OpenDyslexic (SIL OFL 1.1, https://opendyslexic.org) — the actual dyslexia
// typeface, not just a spacing tweak on the default font. Weighted-bottom
// letterforms are the whole point: switching it on must look like a different
// font, not just bolder text in the same one.
private val OpenDyslexicFontFamily = FontFamily(
    Font(R.font.open_dyslexic_regular, FontWeight.Normal),
    Font(R.font.open_dyslexic_bold, FontWeight.Bold),
)

/**
 * Real typeface swap plus wider letter/word spacing and taller lines — all
 * measurable dyslexia accommodations. Deliberately strong enough to be obvious
 * the instant it's toggled, not a barely-there tweak.
 */
private fun Typography.withDyslexicSpacing(): Typography = copy(
    displayLarge   = displayLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.2.sp, lineHeight = displayLarge.lineHeight * 1.35f),
    displayMedium  = displayMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.2.sp, lineHeight = displayMedium.lineHeight * 1.35f),
    displaySmall   = displaySmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.2.sp, lineHeight = displaySmall.lineHeight * 1.35f),
    headlineLarge  = headlineLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.0.sp, lineHeight = headlineLarge.lineHeight * 1.35f),
    headlineMedium = headlineMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.0.sp, lineHeight = headlineMedium.lineHeight * 1.35f, fontWeight = FontWeight.Black),
    headlineSmall  = headlineSmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.0.sp, lineHeight = headlineSmall.lineHeight * 1.35f),
    titleLarge     = titleLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.8.sp, lineHeight = titleLarge.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    titleMedium    = titleMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.8.sp, lineHeight = titleMedium.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    titleSmall     = titleSmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.8.sp, lineHeight = titleSmall.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    bodyLarge      = bodyLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.0.sp, lineHeight = bodyLarge.lineHeight * 1.45f, fontSize = bodyLarge.fontSize * 1.08f),
    bodyMedium     = bodyMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 1.0.sp, lineHeight = bodyMedium.lineHeight * 1.45f, fontSize = bodyMedium.fontSize * 1.08f),
    bodySmall      = bodySmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.9.sp, lineHeight = bodySmall.lineHeight * 1.45f, fontSize = bodySmall.fontSize * 1.08f),
    labelLarge     = labelLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.8.sp, lineHeight = labelLarge.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    labelMedium    = labelMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.8.sp, lineHeight = labelMedium.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    labelSmall     = labelSmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.7.sp, lineHeight = labelSmall.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
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
