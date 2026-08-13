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

// User-requested "Notebook" theme: cream paper background, navy "ink" text,
// and a post-it/sticky-note palette (coral, sunflower, sky) standing in for
// primary/secondary/tertiary - unlike every other scheme above this one is a
// LIGHT scheme (paper is bright), so it's built on lightColorScheme, not
// darkColorScheme. See ScanEatCard.kt's own notebook post-it rendering path
// and Glass.kt's notebookPaperBackground()/notebookSpiralBinding() for the
// rest of this theme's visual identity - this file only owns the palette.
val NotebookPaper       = Color(0xFFFBF6E9)
val NotebookInk         = Color(0xFF2E2A22)
val NotebookLine        = Color(0xFFCFC6A8)
val NotebookRing        = Color(0xFF8A8478)
val NotebookPostItPink  = Color(0xFFE85D75)
val NotebookPostItGold  = Color(0xFFE8B23D)
val NotebookPostItSky   = Color(0xFF4FA3C4)
val NotebookPostItGreen = Color(0xFF7FB069)
private val NotebookColors = lightColorScheme(
    primary          = Color(0xFFC4425A),
    onPrimary        = Color.White,
    secondary        = Color(0xFFB8811F),
    onSecondary      = Color.White,
    tertiary         = Color(0xFF2E7D96),
    background       = NotebookPaper,
    onBackground     = NotebookInk,
    surface          = Color(0xFFFFFDF5),
    onSurface        = NotebookInk,
    surfaceVariant   = Color(0xFFF3ECD4),
    onSurfaceVariant = NotebookInk.copy(alpha = 0.85f),
    error            = Color(0xFFC0392B),
    onError          = Color.White,
    errorContainer   = Color(0xFFFFD9D2),
    onErrorContainer = Color(0xFF7A1F14),
    outline          = NotebookLine,
)

// User-supplied reference ("utilise cette base pour créé un nouveau
// thème"): a light, airy low-poly/faceted gradient - blush pink into sky
// blue into cream into soft gold, overlapping translucent triangles. Like
// Notebook above, this is a LIGHT scheme with its own considered palette
// (rose/blue/gold standing in for primary/secondary/tertiary) rather than
// a hue applied on top of a neutral base - see Glass.kt's ambientGloom()
// isPrism branch for the faceted-triangle background that carries the
// rest of this theme's identity.
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
private val MatchaAccent = ColorAccent(
    primary = Color(0xFF9BC53D), secondary = Color(0xFFD8CB7A), tertiary = Color(0xFF4E7A51),
    background = Color(0xFF10130E), surface = Color(0xFF1C2117), surfaceVariant = Color(0xFF313A2A),
    outline = Color(0xFF4C5A3E),
)
private val LavandeAccent = ColorAccent(
    primary = Color(0xFFB39DDB), secondary = Color(0xFFCE93D8), tertiary = Color(0xFF7986CB),
    background = Color(0xFF120F16), surface = Color(0xFF201B26), surfaceVariant = Color(0xFF362E40),
    outline = Color(0xFF4E4560),
)
private val SunflowerAccent = ColorAccent(
    primary = Color(0xFFFFC940), secondary = Color(0xFFFF9E40), tertiary = Color(0xFFE0A800),
    background = Color(0xFF141008), surface = Color(0xFF231C10), surfaceVariant = Color(0xFF423420),
    outline = Color(0xFF5C4A2E),
)
private val LazuliteAccent = ColorAccent(
    primary = Color(0xFF4C82E0), secondary = Color(0xFF6FA8DC), tertiary = Color(0xFFC9A84C),
    background = Color(0xFF0A0F16), surface = Color(0xFF161F2B), surfaceVariant = Color(0xFF283246),
    outline = Color(0xFF3C4A60),
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
 * layers deep (ScanEatCard's notebook post-it rendering, Glass.kt's
 * notebook paper background/spiral binding) can react to "notebook" being
 * active without every call site threading a `theme: String` parameter
 * through, the same reasoning [LocalAnimatedGloom] above already applies to
 * the animated-background toggle.
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

// Caveat (Google Fonts, SIL OFL 1.1) — the Notebook theme's handwritten
// accent typeface. Variable font (single weight axis, no separate bold
// instance shipped upstream), used as-is at its default weight.
//
// Applied to display/headline/title roles ONLY, not body/label - a script
// typeface at small sizes is measurably harder to read - originally kept off
// body/label text for that reason. User-reported: "tout les texte n'ont pas
// été mis à la police correctement" - explicit instruction to cover every
// text role, overriding that earlier restraint. Now applied everywhere,
// with a smaller size bump on body/label (1.05x vs display/headline/title's
// 1.1-1.15x) as a partial legibility compensation rather than skipping
// those roles entirely.
private val CaveatFontFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal))
// Both confirmed free for personal AND commercial use in writing by their
// author (Khurasan) at download time - see AboutSection.kt's OSS_LIBRARIES
// entries for these two. Several other candidate handwriting fonts the user
// supplied were deliberately excluded (no confirmed commercial license),
// same reasoning "Rainy Calm" was excluded for earlier in this theme's
// history.
private val MayoniceFontFamily = FontFamily(Font(R.font.mayonice, FontWeight.Normal))
private val FoxliteFontFamily  = FontFamily(Font(R.font.foxlite_script, FontWeight.Normal))
// I eat crayons (FontPanda) - added per explicit user instruction after the
// commercial-license status was flagged as unconfirmed (dafont shows a €
// badge next to it, not their "100% Free" tag, and dafont.com/fontget.com
// aren't reachable from this environment's network egress proxy to check
// the exact license text directly) - the user chose to proceed anyway.
private val IEatCrayonsFontFamily = FontFamily(Font(R.font.i_eat_crayons, FontWeight.Normal))
// User-requested: "augmente un peu la taille général des texte de 4dp" - a
// flat +4sp added on top of the existing per-role multiplier (not instead
// of it), on every role including body/label.
// User-requested a further +2dp on top of the original +4dp bump ("augmente
// taille général police de 2dp"), cumulative: 4f -> 6f.
private const val NOTEBOOK_SIZE_BUMP_SP = 6f
// TextUnit has no `+` operator between two TextUnits (CI-breaking build
// error the first version of this function hit: "Unresolved reference
// 'plus'") - resolved to a raw Float via .value, added, then rewrapped as
// .sp, instead of trying to add TextUnits directly.
private fun bumpedSp(base: androidx.compose.ui.unit.TextUnit, multiplier: Float): androidx.compose.ui.unit.TextUnit =
    (base.value * multiplier + NOTEBOOK_SIZE_BUMP_SP).sp
private fun Typography.withNotebookDisplayFont(fontChoice: String): Typography {
    val family = when (fontChoice) {
        "mayonice"      -> MayoniceFontFamily
        "foxlite"       -> FoxliteFontFamily
        "i_eat_crayons" -> IEatCrayonsFontFamily
        else            -> CaveatFontFamily
    }
    return copy(
        displayLarge   = displayLarge.copy(fontFamily = family, fontSize = bumpedSp(displayLarge.fontSize, 1.15f)),
        displayMedium  = displayMedium.copy(fontFamily = family, fontSize = bumpedSp(displayMedium.fontSize, 1.15f)),
        displaySmall   = displaySmall.copy(fontFamily = family, fontSize = bumpedSp(displaySmall.fontSize, 1.15f)),
        headlineLarge  = headlineLarge.copy(fontFamily = family, fontSize = bumpedSp(headlineLarge.fontSize, 1.15f)),
        headlineMedium = headlineMedium.copy(fontFamily = family, fontSize = bumpedSp(headlineMedium.fontSize, 1.15f)),
        headlineSmall  = headlineSmall.copy(fontFamily = family, fontSize = bumpedSp(headlineSmall.fontSize, 1.15f)),
        titleLarge     = titleLarge.copy(fontFamily = family, fontSize = bumpedSp(titleLarge.fontSize, 1.1f)),
        titleMedium    = titleMedium.copy(fontFamily = family, fontSize = bumpedSp(titleMedium.fontSize, 1.1f)),
        titleSmall     = titleSmall.copy(fontFamily = family, fontSize = bumpedSp(titleSmall.fontSize, 1.1f)),
        bodyLarge      = bodyLarge.copy(fontFamily = family, fontSize = bumpedSp(bodyLarge.fontSize, 1.05f)),
        bodyMedium     = bodyMedium.copy(fontFamily = family, fontSize = bumpedSp(bodyMedium.fontSize, 1.05f)),
        bodySmall      = bodySmall.copy(fontFamily = family, fontSize = bumpedSp(bodySmall.fontSize, 1.05f)),
        labelLarge     = labelLarge.copy(fontFamily = family, fontSize = bumpedSp(labelLarge.fontSize, 1.05f)),
        labelMedium    = labelMedium.copy(fontFamily = family, fontSize = bumpedSp(labelMedium.fontSize, 1.05f)),
        labelSmall     = labelSmall.copy(fontFamily = family, fontSize = bumpedSp(labelSmall.fontSize, 1.05f)),
    )
}

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
 * ("oled" | "dark" | "light" | "high_contrast" | "low_contrast" | "notebook" |
 * "prism" | "system") -
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
    // "caveat" | "mayonice" | "foxlite" - only read when theme == "notebook";
    // ignored otherwise. See NotebookFontSection.kt's own doc comment for why
    // the option list is this short.
    notebookFont: String = "caveat",
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
        "notebook"       -> NotebookColors
        "prism"          -> PrismColors
        else             -> OledColors
    }
    // High Contrast's own primary/secondary/tertiary are deliberately
    // maximal-contrast hand-picked values (see HighContrastColors above) for
    // that theme's own accessibility purpose - an accent's hue would fight
    // that same purpose, so High Contrast never takes one regardless of what
    // colorAccent Settings currently has stored. Notebook excluded the same
    // way: its post-it palette (NotebookColors' primary/secondary/tertiary)
    // IS the theme's own considered accent - a Matcha/Lavande/etc. hue swap
    // on top would fight the paper/ink/sticky-note identity, not complement it.
    // Prism excluded for the same reason: its rose/blue/gold palette IS the
    // point of the theme (matches the faceted background it's drawn from).
    val accent = if (resolvedTheme != "high_contrast" && resolvedTheme != "notebook" && resolvedTheme != "prism") when (colorAccent) {
        "matcha"    -> MatchaAccent
        "lavande"   -> LavandeAccent
        "sunflower" -> SunflowerAccent
        "lazulite"  -> LazuliteAccent
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
        if (resolvedTheme == "oled") {
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
    // Notebook's handwritten display font takes priority when both a
    // dyslexic-font accessibility need AND the Notebook theme are active at
    // once - OpenDyslexic's own accommodation (real dyslexia-tested
    // letterforms, applied to every text role including body) matters more
    // than a decorative theme font, same reasoning Theme.kt already applies
    // to High Contrast/colorblind mode overriding decorative choices.
    val typography = when {
        dyslexicFont             -> ScanEatTypography.withDyslexicSpacing()
        resolvedTheme == "notebook" -> ScanEatTypography.withNotebookDisplayFont(notebookFont)
        else                      -> ScanEatTypography
    }
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
