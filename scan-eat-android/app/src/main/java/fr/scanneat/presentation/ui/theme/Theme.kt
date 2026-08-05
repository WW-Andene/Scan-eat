package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    secondary        = AccentCoralRaw,
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

// Warmed off a cool violet cast (was background 0xFF17141B / surface
// 0xFF221E27 / surfaceVariant 0xFF322C38) toward the accent's own hue, per
// docs/design-audit-art-direction-brief.md — unlike OledColors above, this
// theme's background isn't constrained to pure black, so it carries the
// warmth directly instead of only its surfaces.
private val DarkColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = Color.Black,
    secondary        = AccentCoralRaw,
    onSecondary      = Color.Black,
    tertiary         = Teal,
    // User-reported: darkened alongside SurfaceVariant's own lightening below,
    // widening the gap from both ends instead of only pushing the card side.
    // Safe direction for contrast - onBackground is light text, so a darker
    // background only raises that ratio further, never lowers it. Same ~35%
    // scale-down (not just dimmed) to keep the identical warm hue.
    background       = Color(0xFF120F0B),
    onBackground     = Color(0xFFEFEAE6),
    surface          = Color(0xFF261F17),
    onSurface        = Color(0xFFCFC7CC),
    // User-reported: cards read as "almost inseparable" from Background even
    // after ScanEatCard's fill alpha was raised (0.24 -> 0.4, see its own doc
    // comment) - the real cause is this token itself: at #362C1F it was only
    // ~27 RGB units from Background's #1B1611, so no amount of alpha-blending
    // between two colors that close can create real separation without going
    // nearly opaque (which would kill the translucent-glass look entirely).
    // Lightened ~35% (scaled, not just brightened, to keep the same warm hue)
    // so a translucent card actually has headroom to separate from the
    // background instead of the two tokens themselves being the bottleneck.
    // User-reported: the terracotta lean (#5E3726) made every card visibly
    // colored/opaque instead of translucent glass - separation from
    // Background is now carried by the card's border/shadow treatment and
    // ambientGloom's own color variety instead of the fill token itself, so
    // this goes back to a quiet, neutral warm-gray rather than a strong hue.
    // User-reported (round 2): lightened again so the card reads a bit
    // lighter than Background at a glance, not just barely distinguishable.
    surfaceVariant   = Color(0xFF423B32),
    onSurfaceVariant = Color(0xFFCFC7CC),
    error            = FlagRed,
    onError          = Color.White,
    errorContainer   = Color(0x26EF5350),
    onErrorContainer = FlagRed,
    outline          = Color(0xFF4E4A56),
)

// primary/secondary/tertiary are each a darkened variant of Gold/AccentCoral/
// Teal, tuned for two different WCAG contrast roles rather than one shared
// value: as a white-text button fill (onPrimary/onSecondary=White) they clear
// 4.0-4.8:1; as text directly on the F6F1EC background they sit at 3.4-4.3:1
// (large/bold-text tier). Darkening further to clear 4.5:1 as body text would
// make the button fills unnecessarily dark — the two roles pull in opposite
// directions, which is why these are hand-picked per role instead of one
// formula.
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

private val HighContrastColors = darkColorScheme(
    primary          = Color(0xFFFFD700),
    onPrimary        = Color.Black,
    secondary        = Color(0xFF00FFFF),
    onSecondary      = Color.Black,
    tertiary         = Color(0xFF00FF00),
    background       = HighContrastBackgroundRaw,
    onBackground     = HighContrastOnBackgroundRaw,
    surface          = HighContrastSurfaceRaw,
    onSurface        = HighContrastOnSurfaceRaw,
    surfaceVariant   = HighContrastSurfaceRaw,
    onSurfaceVariant = HighContrastOnSurfaceRaw,
    error            = Color(0xFFFF5555),
    onError          = Color.Black,
    errorContainer   = Color(0xFF400000),
    onErrorContainer = Color(0xFFFF5555),
    outline          = HighContrastOutlineRaw,
)

private val LowContrastColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = Color.Black,
    secondary        = AccentCoralRaw,
    onSecondary      = Color.Black,
    tertiary         = Teal,
    background       = LowContrastBackgroundRaw,
    onBackground     = LowContrastOnBackgroundRaw,
    surface          = LowContrastSurfaceRaw,
    onSurface        = LowContrastOnSurfaceRaw,
    surfaceVariant   = LowContrastSurfaceRaw,
    onSurfaceVariant = LowContrastOnSurfaceRaw,
    error            = FlagRed,
    onError          = Color.White,
    errorContainer   = Color(0x26EF5350),
    onErrorContainer = FlagRed,
    outline          = LowContrastOutlineRaw,
)

// User-requested: OLED/Dark/Light/Contrast (brightness/contrast, [theme]
// below) and a color accent (Matcha/Lavande/Sunflower/Lazulite, [colorAccent]
// below) are two independent axes, not one combined choice - a first pass at
// this made each accent its own full darkColorScheme (its own background too),
// which meant OLED's true-black background and an accent were mutually
// exclusive, exactly the "should be able to be both" complaint. An accent now
// only carries hue (primary/secondary/tertiary) and is layered on top of
// whichever base [theme] scheme already resolved, via colorScheme.copy(...)
// in [ScanEatTheme] below - every other role (background/surface/onSurface/
// error/outline, already contrast-tested per base theme) is untouched.
private data class ColorAccent(val primary: Color, val secondary: Color, val tertiary: Color)
private val MatchaAccent    = ColorAccent(primary = Color(0xFF9BC53D), secondary = Color(0xFFD8CB7A), tertiary = Color(0xFF4E7A51))
private val LavandeAccent   = ColorAccent(primary = Color(0xFFB39DDB), secondary = Color(0xFFCE93D8), tertiary = Color(0xFF7986CB))
private val SunflowerAccent = ColorAccent(primary = Color(0xFFFFC940), secondary = Color(0xFFFF9E40), tertiary = Color(0xFFE0A800))
private val LazuliteAccent  = ColorAccent(primary = Color(0xFF4C82E0), secondary = Color(0xFF6FA8DC), tertiary = Color(0xFFC9A84C))

// ── Gold accent override ──────────────────────────────────────────────────────
// Biolism screens need a darker gold in light theme for legible contrast on a
// light background; every other theme uses the raw Gold token as-is.
//
// NOT A DUPLICATE TO CONSOLIDATE: Gold (0xFFC9A84C, Colors.kt), LightGoldAccent
// (0xFF8B6914, below) and LightColors.primary (0xFFA07828, above) are three
// deliberately different hex values for the same brand hue, each hand-tuned
// for a different WCAG contrast role on the light background (F6F1EC):
//   - Gold            0xFFC9A84C — dark/OLED theme accent; not measured against
//                                  the light background, only dark surfaces.
//   - LightGoldAccent  0xFF8B6914 — Biolism accent text/icon directly on the
//                                  light background; darkened further than
//                                  LightColors.primary to clear body-text
//                                  contrast (~4.5:1) at small sizes.
//   - LightColors.primary 0xFFA07828 — button-fill role (paired with
//                                  onPrimary = White); only needs to clear the
//                                  large/bold-text tier (~3.4-4.3:1) since the
//                                  white text on top carries its own contrast.
// A future rebrand must re-measure each role's contrast independently rather
// than merging these into one value.
val LocalGoldAccent = staticCompositionLocalOf { Gold }
private val LightGoldAccent = Color(0xFF8B6914)

/** "none" | "deuteranopia" | "protanopia" | "tritanopia" — read by gradeColor() and friends. */
val LocalColorblindMode = staticCompositionLocalOf { "none" }

/**
 * Settings > Appearance > "Animated background" - read internally by
 * ambientGloom() (Glass.kt) so every existing call site across the app
 * (every screen using ambientGloom for its own background wash) picks up
 * the setting automatically with no change to any of those call sites.
 */
val LocalAnimatedGloom = staticCompositionLocalOf { false }

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
 * measurable dyslexia accommodations. The letter-spacing values were originally
 * up to 1.2sp with an extra +8% font-size bump on body text; on top of a user's
 * own system font scale that combination reliably overflowed fixed-height rows
 * and chips throughout the app. Spacing is now capped at 0.6sp (half the
 * previous max) and the font-size multiplier is dropped entirely — the
 * typeface swap, spacing and taller line-height already make the accommodation
 * obvious without also growing point size.
 */
private fun Typography.withDyslexicSpacing(): Typography = copy(
    displayLarge   = displayLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.6.sp, lineHeight = displayLarge.lineHeight * 1.35f),
    displayMedium  = displayMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.6.sp, lineHeight = displayMedium.lineHeight * 1.35f),
    displaySmall   = displaySmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.6.sp, lineHeight = displaySmall.lineHeight * 1.35f),
    headlineLarge  = headlineLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.5.sp, lineHeight = headlineLarge.lineHeight * 1.35f),
    headlineMedium = headlineMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.5.sp, lineHeight = headlineMedium.lineHeight * 1.35f, fontWeight = FontWeight.Black),
    headlineSmall  = headlineSmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.5.sp, lineHeight = headlineSmall.lineHeight * 1.35f),
    titleLarge     = titleLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.4.sp, lineHeight = titleLarge.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    titleMedium    = titleMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.4.sp, lineHeight = titleMedium.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    titleSmall     = titleSmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.4.sp, lineHeight = titleSmall.lineHeight * 1.35f, fontWeight = FontWeight.Bold),
    bodyLarge      = bodyLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.5.sp, lineHeight = bodyLarge.lineHeight * 1.35f),
    bodyMedium     = bodyMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.5.sp, lineHeight = bodyMedium.lineHeight * 1.35f),
    bodySmall      = bodySmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.45.sp, lineHeight = bodySmall.lineHeight * 1.35f),
    labelLarge     = labelLarge.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.4.sp, lineHeight = labelLarge.lineHeight * 1.25f, fontWeight = FontWeight.Bold),
    labelMedium    = labelMedium.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.4.sp, lineHeight = labelMedium.lineHeight * 1.25f, fontWeight = FontWeight.Bold),
    labelSmall     = labelSmall.copy(fontFamily = OpenDyslexicFontFamily, letterSpacing = 0.35.sp, lineHeight = labelSmall.lineHeight * 1.25f, fontWeight = FontWeight.Bold),
)

/**
 * Root theme. Pass [theme] from UserPreferences
 * ("oled" | "dark" | "light" | "high_contrast" | "low_contrast" | "system") -
 * brightness/contrast only. [colorAccent] ("none" | "matcha" | "lavande" |
 * "sunflower" | "lazulite") is the independent color-accent axis - see
 * [ColorAccent]'s own doc comment on why these are separate params rather
 * than colorAccent being folded into [theme]'s own value set. All screens in
 * the app use this — both Scan'eat and Biolism sections.
 *
 * "system" follows the phone's own OS-level dark/light setting instead of a
 * theme fixed in Settings — resolved once here via [isSystemInDarkTheme] into
 * "dark" or "light" so the rest of this function (and every screen downstream)
 * never needs to know "system" exists as a concept. Deliberately maps the
 * system's dark state to the warmed "dark" scheme rather than "oled": true
 * pure-black OLED is a battery-saving choice a user opts into explicitly, not
 * something the OS's day/night switch should silently turn on.
 */
@Composable
fun ScanEatTheme(
    theme: String = "oled",
    colorAccent: String = "none",
    dyslexicFont: Boolean = false,
    colorblindMode: String = "none",
    animatedBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolvedTheme = if (theme == "system") {
        if (isSystemInDarkTheme()) "dark" else "light"
    } else theme
    val baseColorScheme = when (resolvedTheme) {
        "dark"           -> DarkColors
        "light"          -> LightColors
        "high_contrast"  -> HighContrastColors
        "low_contrast"   -> LowContrastColors
        else             -> OledColors
    }
    // High Contrast's own primary/secondary/tertiary are deliberately
    // maximal-contrast hand-picked values (see HighContrastColors above) for
    // that theme's own accessibility purpose - an accent's hue would fight
    // that same purpose, so High Contrast never takes one regardless of what
    // colorAccent Settings currently has stored.
    val accent = if (resolvedTheme != "high_contrast") when (colorAccent) {
        "matcha"    -> MatchaAccent
        "lavande"   -> LavandeAccent
        "sunflower" -> SunflowerAccent
        "lazulite"  -> LazuliteAccent
        else        -> null
    } else null
    val colorScheme = if (accent != null) {
        baseColorScheme.copy(primary = accent.primary, secondary = accent.secondary, tertiary = accent.tertiary)
    } else baseColorScheme
    val goldAccent = if (resolvedTheme == "light") LightGoldAccent else Gold
    val typography = if (dyslexicFont) ScanEatTypography.withDyslexicSpacing() else ScanEatTypography
    CompositionLocalProvider(
        LocalGoldAccent provides goldAccent,
        LocalColorblindMode provides colorblindMode,
        LocalAnimatedGloom provides animatedBackground,
    ) {
        // The 5 schemes above bake `error` in as a plain val at construction
        // time, so it can't itself read LocalColorblindMode - every isError
        // form field in the app (OutlinedTextField etc.) rendered via
        // MaterialTheme.colorScheme.error bypassed colorblind mode entirely.
        // Overridden here, once, using the same mapping semanticRed() uses
        // everywhere else, now that LocalColorblindMode is actually provided.
        // High Contrast's own error = Color(0xFFFF5555) is a deliberately hand-picked
        // maximal-contrast value against HighContrastBackgroundRaw/HighContrastSurfaceRaw
        // specifically - not derived from semanticRed()'s hue-safety tuning, which was
        // picked against the OLED/Dark palette instead. Applying the colorblind override
        // here too silently replaced High Contrast's own considered choice whenever both
        // accessibility features were enabled together, undercutting whichever one the
        // user actually needed more.
        val effectiveColorScheme = if (colorblindMode == "none" || theme == "high_contrast") colorScheme
            else colorScheme.copy(error = semanticRed(), onErrorContainer = semanticRed())
        MaterialTheme(
            colorScheme = effectiveColorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
