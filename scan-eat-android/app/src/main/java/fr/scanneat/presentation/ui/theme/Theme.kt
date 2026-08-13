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
    outline          = OledOutlineRaw,
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

// User-supplied reference ("utilise cette base pour créé un nouveau
// thème"): a light, airy low-poly/faceted gradient - blush pink into sky
// blue into cream into soft gold, overlapping translucent triangles. A
// LIGHT scheme with its own considered palette (rose/blue/gold standing in
// for primary/secondary/tertiary) rather than a hue applied on top of a
// neutral base - see Glass.kt's ambientGloom() isPrism branch for the
// faceted-triangle background that carries the rest of this theme's identity.
val PrismBackground     = Color(0xFFFBF3F0)
val PrismOnBackground   = Color(0xFF2C2430)
val PrismRose           = Color(0xFFC24A6B)
val PrismBlue           = Color(0xFF3E6FA0)
val PrismGold           = Color(0xFFCB9A3D)
val PrismMint           = Color(0xFF8FB89A)
private val PrismColors = lightColorScheme(
    primary          = PrismRose,
    onPrimary        = Color.White,
    secondary        = PrismBlue,
    onSecondary      = Color.White,
    tertiary         = PrismGold,
    background       = PrismBackground,
    onBackground     = PrismOnBackground,
    surface          = Color(0xFFFFFFFF),
    onSurface        = PrismOnBackground,
    surfaceVariant   = Color(0xFFF3E4E8),
    onSurfaceVariant = PrismOnBackground.copy(alpha = 0.75f),
    error            = Color(0xFFD32F2F),
    onError          = Color.White,
    errorContainer   = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF9B1C1C),
    outline          = Color(0xFFDCC3CB),
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
// below) are two independent axes, not one combined choice - a first pass
// made each accent its own full darkColorScheme (own background too),
// forcing OLED's true-black background and an accent to be mutually
// exclusive. A second pass then made an accent hue-only
// (primary/secondary/tertiary), fixing that exclusivity but reported as "not
// enough color" - the richer background/surface/surfaceVariant from the
// first pass read better and is restored here for every base theme except
// OLED specifically: OLED's [OledBackgroundRaw] is literally 0x000000 (every
// pixel off, the entire point of that theme), so tinting it at all isn't a
// stylistic quibble, it defeats OLED's actual purpose. [ScanEatTheme] below
// keeps OLED's background pure black even with an accent selected, but still
// applies the accent's own richer surface/surfaceVariant (cards, chrome) -
// every other base theme gets the accent's background too.
// [outline] added alongside the original 6 fields — every accent previously
// left `outline` (card/divider/outlined-field borders) on the base theme's
// own neutral warm-gray value (Dark's 0xFF4E4A56, OLED's 0xFF2E2A30), the
// one visible surface this system never re-tinted. Selecting Matcha/Lavande/
// Sunflower/Lazulite colored the fills but every border in the app stayed
// the same flat gray regardless of accent - the one seam that gave the
// whole treatment away as "background + buttons recolored" rather than a
// genuinely cohesive palette. Each value below is a mid-tone step between
// that accent's own surface and surfaceVariant, hue-matched to primary
// rather than desaturated, at roughly the same luminance as Dark's own
// outline so contrast against text/borders doesn't regress.
private data class ColorAccent(
    val primary: Color, val secondary: Color, val tertiary: Color,
    val background: Color, val surface: Color, val surfaceVariant: Color,
    val outline: Color,
)

/** Re-saturates/re-brightens a color to full HSV saturation+value (same hue) — see High Contrast's colorAccent branch in [ScanEatTheme] for why. */
private fun Color.boostedForHighContrast(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[1] = 1f
    hsv[2] = 1f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
// User-requested: "pareil pour les autre thème de couleur" - same
// one-by-one review pass as Rose/Arlequin/Cyberpunk above, applied to the
// original four.
//
// Matcha: was yellow-green + a mustard-leaning pale tan + dark green -
// recognizable but the secondary read more "mustard" than "matcha latte."
// Vivid whisked-matcha green, a cream/latte foam tan, and a deep tea-leaf
// green reads more specifically as the drink/powder, not just "green
// theme."
private val MatchaAccent = ColorAccent(
    primary = Color(0xFF7CB518), secondary = Color(0xFFE8DCB5), tertiary = Color(0xFF3A5311),
    background = Color(0xFF0F130B), surface = Color(0xFF1B2114), surfaceVariant = Color(0xFF2E3A20),
    outline = Color(0xFF485C34),
)
// Lavande: previous values were Material's own pastel lavender defaults -
// correct hue family but soft enough to read as generic "light purple"
// rather than lavender specifically. A clearer light->medium->deep bloom
// gradient (actual lavender-flower purple as the primary, not the
// palest tone) reads more like a lavender field.
private val LavandeAccent = ColorAccent(
    primary = Color(0xFF9575CD), secondary = Color(0xFFB39DDB), tertiary = Color(0xFF5C4B99),
    background = Color(0xFF120F16), surface = Color(0xFF201B26), surfaceVariant = Color(0xFF362E40),
    outline = Color(0xFF4E4560),
)
// Sunflower: petal yellow + orange were already right; tertiary warmed
// from a fairly neutral gold toward a huskier burnt-amber, closer to the
// actual seed-head center's tone instead of just "darker yellow."
private val SunflowerAccent = ColorAccent(
    primary = Color(0xFFFFC940), secondary = Color(0xFFFF9E40), tertiary = Color(0xFFC9820A),
    background = Color(0xFF141008), surface = Color(0xFF231C10), surfaceVariant = Color(0xFF423420),
    outline = Color(0xFF5C4A2E),
)
// Lazulite (lapis lazuli, the mineral): gold tertiary for the stone's
// characteristic pyrite flecks was already right; primary deepened from a
// medium sky-blue to the mineral's actual deep ultramarine, promoting the
// old primary to secondary - lapis is a DEEP blue stone, not a light one.
private val LazuliteAccent = ColorAccent(
    primary = Color(0xFF1F4E8C), secondary = Color(0xFF4C82E0), tertiary = Color(0xFFC9A84C),
    background = Color(0xFF0A0F16), surface = Color(0xFF161F2B), surfaceVariant = Color(0xFF283246),
    outline = Color(0xFF3C4A60),
)
// User-requested: Rose, Arlequin, Cyberpunk - three new colorAccent presets
// alongside the four above, same shape (a background/surface/surfaceVariant/
// outline neighborhood plus a 3-hue primary/secondary/tertiary). User-
// corrected round 2: "le thème Arlequin n'est pas Arlequin, cyberpunk non
// plus" - both reworked below with a clearer identity per name.
//
// Rose: a coherent single-hue family (ruby -> blush -> deep rose), the
// same "gradient of one hue" structure Matcha/Lavande/Sunflower use above.
private val RoseAccent = ColorAccent(
    primary = Color(0xFFE0115F), secondary = Color(0xFFFF8FAB), tertiary = Color(0xFFC9184A),
    background = Color(0xFF16070E), surface = Color(0xFF25121B), surfaceVariant = Color(0xFF3D1D2C),
    outline = Color(0xFF5C2E42),
)
// Arlequin: user-specified round 5 palette - "Carmin-Lemon-Roi" (carmine
// red / lemon yellow / bleu roi royal blue - the three clashing primaries),
// "Olive accent" (surfaceVariant) and "Charcoal tint" (background) - a full
// specific spec, not a web-sourced approximation this time.
private val ArlequinAccent = ColorAccent(
    primary = Color(0xFFB01E3C), secondary = Color(0xFFFCE100), tertiary = Color(0xFF14209E),
    background = Color(0xFF17171A), surface = Color(0xFF202024), surfaceVariant = Color(0xFF4B4D24),
    outline = Color(0xFF5C5E30),
)
// Cyberpunk: user-specified round 5 palette - "Paint yellow" (primary),
// "Neon sky" (secondary) and "Ink tint" (background) - tertiary keeps the
// prior sourced magenta/pink (Cyberpunk 2077's own HUD accent) for the
// full 3-hue triad, since the spec only named two hues plus the background.
private val CyberpunkAccent = ColorAccent(
    primary = Color(0xFFF5D000), secondary = Color(0xFF29D3FF), tertiary = Color(0xFFED1E79),
    background = Color(0xFF05060C), surface = Color(0xFF12121F), surfaceVariant = Color(0xFF1E1E30),
    outline = Color(0xFF2F4C8C),
)
// Elite: user-specified round 6 palette, this time with explicit functional
// roles (not just hue names): "Primary=Satin black" -> this app's background
// (the base tone), "Secondary=Ebony (outline, Shadow, Blur)" -> this app's
// outline/surfaceVariant (the dark structural tone), "Tertiary=Hot gold
// (Button, Logo, text)" -> this app's [primary] field specifically, since
// that's the one ColorAccent field that actually drives MaterialTheme.
// colorScheme.primary (buttons/active states/text accents app-wide - see
// this file's own accent-application block below). Hot gold is a punchier,
// more saturated amber-gold than the prior "melted gold" - secondary/
// tertiary are a lighter highlight and a deeper shadow step of that same
// hue, so the accent still has real light/shadow range from one sourced hue.
private val EliteAccent = ColorAccent(
    primary = Color(0xFFF5A623), secondary = Color(0xFFFFD180), tertiary = Color(0xFFB8860B),
    background = Color(0xFF0A0A0A), surface = Color(0xFF141310), surfaceVariant = Color(0xFF241C15),
    outline = Color(0xFF4A3728),
)
// Noble: user-specified round 5 palette - "Marble" (background), "Chalk
// accent" (surfaceVariant) and "Frozen Gold tint" (primary) - a cooler,
// icier gold than Elite's warm melted gold, and a cooler chalky-gray
// undertone (vs. Elite's warm ebony-brown) so the two stay clearly distinct.
private val NobleAccent = ColorAccent(
    primary = Color(0xFFC8C095), secondary = Color(0xFFEFEFEA), tertiary = Color(0xFF9C9470),
    background = Color(0xFF131316), surface = Color(0xFF1F2023), surfaceVariant = Color(0xFF35363A),
    outline = Color(0xFF55565C),
)

// ── Colorblind-safe decorative/brand accent override ──────────────────────────
// User-reported: colorblind mode adjusted every meaning-bearing signal
// (semanticGreen/Red/Amber/Blue, gradeColor, MaterialTheme.colorScheme.error)
// but left the decorative brand hue itself untouched - the base theme's
// primary/secondary/tertiary (Gold/AccentCoral/Teal) and every colorAccent
// preset (Matcha/Lavande/Sunflower/Lazulite) kept their own literal hues
// regardless of the setting, so a colorblind user picking e.g. Matcha still
// saw its green-leaning primary exactly as before.
//
// Doesn't invent new per-accent hues (there are only 7 non-black Okabe-Ito
// colors and the 4 semantic accessors already claim up to 8 of them between
// their protan/deutan and tritanopia branches - see semanticGreen/Red/Amber/
// Blue's own doc comments) - instead every base theme AND every colorAccent
// collapses to ONE shared, mode-appropriate hue (3 shades of it, for
// primary/secondary/tertiary) that's deliberately picked to NOT match any
// currently-active semantic hue for that same mode, so the decorative accent
// can never be misread as a status signal (a real risk the other direction:
// reusing a semantic hue for decoration would make THAT hue meaningless the
// next time it legitimately signals success/danger/warning).
private data class ColorblindAccent(val primary: Color, val secondary: Color, val tertiary: Color)

// User-reported: with the old #0072B2 blue accent, everything read as "just
// blue" under protanopia/deuteranopia mode - semanticGreen's own substitute
// hue in this mode is sky blue (#56B4E9, Colors.kt), and semanticBlue stays
// on its normal unshifted blue too (see its own doc comment), so a blue
// decorative accent sat on top of two other already-blue signals instead of
// giving the eye a third distinct hue. Okabe-Ito "reddish purple" (#CC79A7)
// is the one hue left unclaimed by semanticGreen/Red/Amber/Blue under this
// mode (semanticGreen=sky blue, semanticRed=vermilion, semanticAmber=yellow,
// semanticBlue=unshifted blue) - genuinely free to use here.
private val ColorblindAccentProtanDeutan = ColorblindAccent(
    primary = Color(0xFFCC79A7), secondary = Color(0xFFE0A8C8), tertiary = Color(0xFF95507A),
)
// Okabe-Ito "vermilion" (#D55E00) - unclaimed under tritanopia (semanticRed's
// tritanopia branch is the reddish-purple #CC79A7 instead, semanticAmber's is
// orange #E69F00, distinct enough from vermilion to not collide).
private val ColorblindAccentTritanopia = ColorblindAccent(
    primary = Color(0xFFD55E00), secondary = Color(0xFFE8894D), tertiary = Color(0xFFA84400),
)

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

/**
 * The resolved theme string ("system" already collapsed to "dark"/"light" -
 * see [ScanEatTheme]'s own doc comment), exposed so a component several
 * layers deep can react to which theme is active (e.g. Glass.kt's Prism
 * facet background) without every call site threading a `theme: String`
 * parameter through, the same reasoning [LocalAnimatedGloom] above already
 * applies to the animated-background toggle.
 */
val LocalThemeName = staticCompositionLocalOf { "dark" }

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
 * ("oled" | "dark" | "light" | "high_contrast" | "low_contrast" | "prism" |
 * "system") -
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
    theme: String = "dark",
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
        "prism"          -> PrismColors
        else             -> OledColors
    }
    // User-reported: High Contrast is supposed to take colorAccent (Matcha
    // etc.) into account and previously didn't - it now does, on the same
    // background-preserving path OLED uses just below (background stays
    // pure black, since HighContrastBackgroundRaw IS 0x000000 for the same
    // max-contrast reason OLED's is - only primary/secondary/tertiary/
    // surface/surfaceVariant/outline take the accent's hue).
    // Prism excluded: its rose/blue/gold palette IS the point of the theme -
    // a Matcha/Lavande/etc. hue swap on top would fight that identity.
    val accent = if (resolvedTheme != "prism") when (colorAccent) {
        "matcha"    -> MatchaAccent
        "lavande"   -> LavandeAccent
        "sunflower" -> SunflowerAccent
        "lazulite"  -> LazuliteAccent
        "rose"      -> RoseAccent
        "arlequin"  -> ArlequinAccent
        "cyberpunk" -> CyberpunkAccent
        "elite"     -> EliteAccent
        "noble"     -> NobleAccent
        else        -> null
    } else null
    val colorScheme = if (accent != null) {
        // OLED's background stays pure black regardless of accent (see
        // ColorAccent's own doc comment above on why) - every other base
        // theme takes the accent's own background too, for the fuller color
        // this was reported as missing.
        //
        // User-reported: Light and Low Contrast broke the same way OLED would
        // have without its own carve-out above - ColorAccent's background/
        // surface/surfaceVariant are all hand-tuned near-black values (e.g.
        // Matcha's #10130E), so picking any accent silently turned "Light
        // mode" dark, and replaced Low Contrast's deliberately narrow,
        // close-together gray palette with a full-saturation dark panel -
        // defeating each theme's own reason to exist exactly like an
        // untinted OLED background would. Both now take only the accent's
        // hue (primary/secondary/tertiary), the same restriction OLED's own
        // branch already applies to background alone.
        if (resolvedTheme == "high_contrast") {
            // User-reported: "quand je change couleur en étant sur
            // contrast élevé, ça ce désactive" - not an actual state bug
            // (theme stays "high_contrast" the whole time), but picking any
            // accent replaced HighContrastColors' own hand-picked maximal-
            // contrast neon yellow/cyan/green with that accent's literal
            // hue - each accent's own colors are tuned for a dark/OLED
            // panel, at meaningfully lower saturation/brightness, so the
            // result visually read as high contrast having turned off.
            // boostedForHighContrast() re-saturates/re-brightens each
            // accent hue to full HSV S/V (same hue, so it's still
            // recognizably "that" accent) before use here, so an accent
            // can be combined with High Contrast without undercutting the
            // one thing that theme exists for.
            baseColorScheme.copy(
                primary = accent.primary.boostedForHighContrast(),
                secondary = accent.secondary.boostedForHighContrast(),
                tertiary = accent.tertiary.boostedForHighContrast(),
                surface = accent.surface, surfaceVariant = accent.surfaceVariant, outline = accent.outline,
            )
        } else if (resolvedTheme == "oled") {
            baseColorScheme.copy(
                primary = accent.primary, secondary = accent.secondary, tertiary = accent.tertiary,
                surface = accent.surface, surfaceVariant = accent.surfaceVariant, outline = accent.outline,
            )
        } else if (resolvedTheme == "light" || resolvedTheme == "low_contrast") {
            baseColorScheme.copy(
                primary = accent.primary, secondary = accent.secondary, tertiary = accent.tertiary,
            )
        } else {
            baseColorScheme.copy(
                primary = accent.primary, secondary = accent.secondary, tertiary = accent.tertiary,
                background = accent.background, surface = accent.surface, surfaceVariant = accent.surfaceVariant,
                outline = accent.outline,
            )
        }
    } else baseColorScheme
    // High Contrast is excluded the same way the `error` override below
    // excludes it - its own primary/secondary/tertiary are deliberately
    // hand-picked maximal-contrast values for that theme's own accessibility
    // purpose (see HighContrastColors' own doc comment), which a colorblind
    // hue swap would undercut rather than complement.
    val colorblindAccent = if (resolvedTheme != "high_contrast") when (colorblindMode) {
        "protanopia", "deuteranopia" -> ColorblindAccentProtanDeutan
        "tritanopia"                 -> ColorblindAccentTritanopia
        else                         -> null
    } else null
    val finalColorScheme = colorblindAccent?.let {
        colorScheme.copy(primary = it.primary, secondary = it.secondary, tertiary = it.tertiary)
    } ?: colorScheme
    val goldAccent = when {
        colorblindAccent != null  -> colorblindAccent.primary
        resolvedTheme == "light" || resolvedTheme == "prism" -> LightGoldAccent
        else                      -> Gold
    }
    val typography = if (dyslexicFont) ScanEatTypography.withDyslexicSpacing() else ScanEatTypography
    CompositionLocalProvider(
        LocalGoldAccent provides goldAccent,
        LocalColorblindMode provides colorblindMode,
        LocalAnimatedGloom provides animatedBackground,
        LocalAccentCoralOverride provides (if (resolvedTheme == "high_contrast") AccentCoralRaw else null),
        LocalThemeName provides resolvedTheme,
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
        val effectiveColorScheme = if (colorblindMode == "none" || theme == "high_contrast") finalColorScheme
            else finalColorScheme.copy(error = semanticRed(), onErrorContainer = semanticRed())
        MaterialTheme(
            colorScheme = effectiveColorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
