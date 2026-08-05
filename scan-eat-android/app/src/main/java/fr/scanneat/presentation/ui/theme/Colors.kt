package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fr.scanneat.domain.model.Grade

// ============================================================================
// SCAN'EAT + BIOLISM — Unified Design Token System
//
// Two accent systems coexist:
//   Scan'eat: AccentCoral — scan scoring, diary, nutrition feedback
//   Biolism:  Gold/Teal/Violet — metabolic science, timers, analytics
//
// This file previously documented a φ = 1.618 spacing scale here (sp1=4,
// sp2=6, sp3=10, sp4=16, sp5=26, sp6=42, sp7=68) as if Spacing.kt implemented
// it — it never did; Spacing.kt's actual scale is 4/8/10/12/16/24/32, a
// separate, rounder progression. Audit F1 (docs/design-audit-step1-identity-
// profile.md, docs/design-audit-step5-tokens.md) flagged the two as
// inconsistent with no record of which was authoritative. Removing the
// unimplemented φ comment here rather than rewriting Spacing.kt's real
// values, which are already in production use at ~35+ call sites — see
// Spacing.kt itself for the scale that is actually in effect.
// ============================================================================

// Audit F12 (docs/design-audit-step5-tokens.md): this file mixed the
// semantic layer (theme-reactive roles like Background/OnBackground below)
// and the raw/primitive layer (fixed hex constants like AccentCoral, Gold,
// the OLED/HighContrast/LowContrast *Raw values) with no marker distinguishing
// them, risking a future color being added at the wrong layer. Three banners
// below mark where each layer starts; nothing was moved, only labeled.

// ═══ LAYER 1 — PRIMITIVES (raw hex, never theme-reactive) ════════════════════
// ── Shared neutrals ──────────────────────────────────────────────────────────
// Nectar: a near-black ground with a faint warm undertone (not flat OLED grey),
// paired with one sober warm-coral brand accent used app-wide for actions and
// active states. Semantic colors (Flag*, grade colors) stay a separate scale on
// purpose — accent means "you can act here," semantic colors mean "this is good
// or bad," and the two must never be the same hue or they stop being readable.
// Raw OLED literals — consumed only by Theme.kt to build the OLED color scheme
// (can't reference MaterialTheme.colorScheme while constructing it).
// Background stays true pure black — the whole point of the OLED theme is
// every pixel physically off for battery, so this is the one background this
// audit's "never a pure black/white" rule (docs/design-audit-step6-color-
// atmosphere.md, F15) deliberately does NOT apply to. Surface/SurfaceVariant
// below carry the "second skin" warmth instead, since cards/chrome sit on top
// of that true black rather than being it.
internal val OledBackgroundRaw     = Color(0xFF000000)  // true pure black — every OLED pixel off, not a darker Dark
internal val OledOnBackgroundRaw   = Color(0xFFEFEAE6)
// Warmed off cool violet-black (was 0xFF141118) toward the same coral-adjacent
// hue as the accent, per docs/design-audit-art-direction-brief.md — a card
// floating over true-black should feel like skin catching light, not a
// separate cool-toned panel. SurfaceVariant shifts hue again (not just
// lightness) one step further, for the perceived-depth cue F17 flagged as
// missing between elevation tiers.
internal val OledSurfaceRaw        = Color(0xFF181310)
internal val OledSurfaceVariantRaw = Color(0xFF2A2018)  // one step lighter than surface — the elevation tier Dark already has
internal val OledOnSurfaceRaw      = Color(0xFFCFC7CC)

// High/Low Contrast — WCAG-maximal variants, not brand-color re-tunes.
internal val HighContrastBackgroundRaw = Color(0xFF000000)
internal val HighContrastOnBackgroundRaw = Color(0xFFFFFFFF)
internal val HighContrastSurfaceRaw = Color(0xFF000000)
internal val HighContrastOnSurfaceRaw = Color(0xFFFFFFFF)
internal val HighContrastOutlineRaw = Color(0xFFFFFFFF)

internal val LowContrastBackgroundRaw = Color(0xFF3A3A3A)
internal val LowContrastOnBackgroundRaw = Color(0xFFB8B8B8)
internal val LowContrastSurfaceRaw = Color(0xFF454545)
internal val LowContrastOnSurfaceRaw = Color(0xFFAFAFAF)
internal val LowContrastOutlineRaw = Color(0xFF6A6A6A)

// Audit F16 (docs/design-audit-step6-color-atmosphere.md): shadows app-wide
// rendered via Compose's default `Modifier.shadow`/Surface `shadowElevation`
// color, an unthemed near-black — inconsistent with the warmed "second skin"
// palette everything else in this file now carries (docs/design-audit-art-
// direction-brief.md). A warm near-black instead of a neutral one, applied
// via explicit `Modifier.shadow(ambientColor = ShadowTint, spotColor =
// ShadowTint)` at call sites that opt in (ScanEatCard first).
val ShadowTint: Color = Color(0xFF120D0A)

// ═══ LAYER 2 — SEMANTIC (meaning-bearing roles; theme-reactive where noted) ══
// Theme-reactive roles. Every screen reads these instead of a fixed literal, so
// switching OLED/Sombre/Clair in Settings actually repaints the app — previously
// these were hardcoded OLED-black constants and the theme picker changed nothing.
val Background:     Color @Composable get() = MaterialTheme.colorScheme.background
val OnBackground:   Color @Composable get() = MaterialTheme.colorScheme.onBackground
val SurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val OnSurface:      Color @Composable get() = MaterialTheme.colorScheme.onSurface

// ── Scan'eat accent ───────────────────────────────────────────────────────────
// User-reported: selecting a color theme (Matcha/Lavande/Sunflower/Lazulite)
// left some elements stuck on the base coral instead of picking up the new
// theme's own accent - because this was a plain, non-theme-reactive Color
// literal, unlike Background/OnBackground/SurfaceVariant/OnSurface above.
// Now reads MaterialTheme.colorScheme.secondary, same reactive pattern as
// those - every existing `AccentCoral` call site (dozens across the app)
// picks this up automatically with no change of its own, since a property
// with a custom getter is referenced identically to a plain val. The raw
// literal survives as AccentCoralRaw (below), which OLED/Dark/Low-Contrast's
// own colorScheme definitions (Theme.kt) still use as their literal
// `secondary` value - Light/High-Contrast/Matcha/Lavande/Sunflower/Lazulite
// each set a different `secondary` there instead, which is exactly what now
// makes this reactive.
internal val AccentCoralRaw = Color(0xFFD97C56)  // sober warm coral — OLED/Dark/Low-Contrast's own secondary
val AccentCoral: Color @Composable get() = MaterialTheme.colorScheme.secondary
val FlagRed         = Color(0xFFEF5350)
val FlagGreen       = Color(0xFF66BB6A)
val AmberWarning    = Color(0xFFFFA726)

// The five φ = 1.618 alpha steps (1.0 / 0.618 / 0.236 / 0.162 / 0.10 / 0.062)
// as named Float constants — the actual primitive layer F13 (docs/design-
// audit-step5-tokens.md) found missing. Previously each of Gold/Teal/Violet's
// five alpha tiers was a separately hand-packed ARGB hex literal (0x9DC9A84C,
// 0x3CC9A84C, ...) that happened to compute to these ratios rather than being
// derived from them — correct today, but any future accent color's tiers
// would need re-deriving each alpha by hand again instead of reusing this
// scale. `.copy(alpha = ...)` below reads only from these five constants.
const val PHI_DIM_ALPHA: Float = 0.618f
const val PHI_GLOW_ALPHA: Float = 0.236f
const val GLOW_BORDER_ALPHA: Float = 0.162f
const val GLOW_HAZE_ALPHA: Float = 0.10f
const val PHI_TRACE_ALPHA: Float = 0.062f

// Border genre audit: a distinct, unnamed role - the border on a *clickable*
// haze-filled chip/pill (CollapsibleFilterBar's filter pill, DiaryScreen's
// tab-menu pill, FastingSection's log-meal/import-fast badges) - had
// converged on the same 0.4f alpha at all 4 sites, independently of hue
// (AccentCoral, Violet). Deliberately distinct from GLOW_BORDER_ALPHA
// (0.162f), which is for passive/decorative badges, not tappable ones - an
// interactive element earning a more visible border is a real semantic
// difference, not drift to fold into the existing golden-ratio tier.
const val CHIP_BORDER_ALPHA: Float = 0.4f

// Border genre audit (round 2): a second distinct role - the border on a
// *static* status/metric Surface (BMI badge, Biolism metric-card accent
// strip, data-screen tint badges) - converged on 0.3f at 4 sites across
// 4 different hues (bmiColor, Teal, per-nutrient color x2), independently
// of CHIP_BORDER_ALPHA's clickable-pill role above.
const val STATUS_BORDER_ALPHA: Float = 0.3f

// ── Biolism accent system (φ-derived) ─────────────────────────────────────────
// Gold — primary action, live timer, BMR hero
val Gold            = Color(0xFFC9A84C)
val GoldDim         = Gold.copy(alpha = PHI_DIM_ALPHA)
val GoldGlow        = Gold.copy(alpha = PHI_GLOW_ALPHA)
val GoldBorder      = Gold.copy(alpha = GLOW_BORDER_ALPHA)
val GoldHaze        = Gold.copy(alpha = GLOW_HAZE_ALPHA)
val GoldTrace       = Gold.copy(alpha = PHI_TRACE_ALPHA)

// Teal — secondary: substrate, ketosis, VO2
val Teal            = Color(0xFF38C8C8)
val TealGlow        = Teal.copy(alpha = PHI_GLOW_ALPHA)
val TealBorder      = Teal.copy(alpha = GLOW_BORDER_ALPHA)
val TealHaze        = Teal.copy(alpha = GLOW_HAZE_ALPHA)
val TealTrace       = Teal.copy(alpha = PHI_TRACE_ALPHA)

// Violet — tertiary: fasting, hormones, protein
val Violet          = Color(0xFF9275E0)
val VioletGlow      = Violet.copy(alpha = PHI_GLOW_ALPHA)
val VioletBorder    = Violet.copy(alpha = GLOW_BORDER_ALPHA)
val VioletHaze      = Violet.copy(alpha = GLOW_HAZE_ALPHA)
val VioletTrace     = Violet.copy(alpha = PHI_TRACE_ALPHA)

// Warm orange — fat substrate (non-keto)
val Warm            = Color(0xFFE8A87C)
val WarmGlow        = Warm.copy(alpha = PHI_GLOW_ALPHA)
val WarmHaze        = Warm.copy(alpha = GLOW_HAZE_ALPHA)

// Danger/severe
val Danger          = Color(0xFFC0392B)
val DangerGlow      = Danger.copy(alpha = PHI_GLOW_ALPHA)
val Severe          = Color(0xFFE06C5A)

// Fibre green
val MetaGreen       = Color(0xFFA0C878)
val MetaGreenHaze   = MetaGreen.copy(alpha = GLOW_HAZE_ALPHA)

// Icon inactive
val IconInactive    = Color(0xFF4E5468)

// ── Pressed/hover accent states ───────────────────────────────────────────────
// ~8% darker than the base hue, same hue — for ripple/pressed-state customization
// (Material3 buttons otherwise fall back to a generic default ripple).
val GoldPressed        = Color(0xFFB89843)
val AccentCoralPressed = Color(0xFFC5714E)

// ── Separator weight taxonomy ─────────────────────────────────────────────────
// Three explicit weights instead of ad hoc alpha literals scattered per call
// site — glassSheen()'s hairline was the only named separator treatment before
// this. Theme-reactive like Background/OnBackground above.
val SeparatorHeavy:  Color @Composable get() = OnBackground.copy(alpha = 0.20f)
val SeparatorLight:  Color @Composable get() = OnBackground.copy(alpha = 0.08f)
val SeparatorAccent: Color @Composable get() = AccentCoral.copy(alpha = 0.30f)

// genre audit (divider alpha): 0.06f turned out to be the second most common
// ad hoc divider alpha (5 call sites) this taxonomy's own doc comment below
// already named as drift alongside 0.08f/0.1f, but never gave its own tier.
val SeparatorExtraLight: Color @Composable get() = OnBackground.copy(alpha = 0.06f)

// genre audit (chip background alpha): AccentCoral.copy(0.15f) is the single
// most common tinted-chip fill in the app (7 sites: CollapsibleFilterBar's
// pill, DiaryScreen's tab pill, WeightHistorySection/FoodEntryRow/dashboard
// gap-card highlight chips) - unlike Separator*, there was no name for this
// role at all. Not a @Composable getter since AccentCoral itself isn't
// theme-reactive.
val ChipBackgroundAccent: Color = AccentCoral.copy(alpha = 0.15f)

// LAYER 3 (component-specific, not a general role) — see the fuller LAYER 3
// banner further below before scanEatTextFieldColors(); this one composable
// sits early only because it's a small divider tied to the separators above.
/**
 * The hairline `HorizontalDivider(color = OnBackground.copy(0.06f/0.08f/0.1f))`
 * pattern was hand-copied across many screens with the alpha literal drifting
 * per site instead of using the Separator* taxonomy above. First adoption at
 * RemindersCard/DiaryScreen; other call sites migrate incrementally.
 */
@Composable
fun ScanEatDivider(color: Color = SeparatorLight) = androidx.compose.material3.HorizontalDivider(color = color)

// ── Label & secondary text (back to LAYER 2 — semantic) ──────────────────────
// Category E audit (§E3 Color Craft & Contrast): these were flat literals
// tuned by eye against the dark themes only — Color(0xFF7E859E) against the
// OLED/Dark backgrounds clears ~5:1 (barely, on Dark) to ~5.75:1 (OLED), but
// the exact same hardcoded value on the Light theme's near-white background
// (0xFFF6F1EC) computes to ~3.2:1 — a real WCAG AA failure (4.5:1 required
// for this size class), since these three never varied with `theme` the way
// every other role in this file (Background/OnBackground/Separator*) already
// does. Deriving from the theme's own OnBackground — already contrast-tuned
// per theme, including Light's separately-calibrated near-black — fixes this
// for every theme at once instead of hand-picking five more literals.
val TextSecondary: Color @Composable get() = OnBackground.copy(alpha = 0.65f)

// design-aesthetic-audit §E3 (WCAG contrast pass): a flat alpha across every
// theme assumes contrast only depends on picking the right onBackground per
// theme (this file's own comment above), but composited-alpha contrast also
// depends on the specific background luminance behind it - Light's near-white
// background (0xF6F1EC) needs meaningfully more opacity than Dark/OLED's dark
// ones to land at the same real ratio. Verified by relative-luminance
// calculation: the old flat 0.38 TextMuted computed to 2.97:1 on OLED and
// 2.3:1 on Light - both below even the 3:1 floor for large/bold text, not
// just the 4.5:1 body-text tier. TextLabel's flat 0.55 similarly only reached
// 3.69:1 on Light (below 4.5:1 AA). Split per isLightBackground() so the same
// "muted"/"label" role reads at the same real contrast in every theme instead
// of silently failing accessibility only in Light.
val TextMuted: Color @Composable get() = OnBackground.copy(alpha = if (isLightBackground()) 0.50f else 0.40f)
val TextLabel: Color @Composable get() = OnBackground.copy(alpha = if (isLightBackground()) 0.68f else 0.55f)

// ── Feature accents outside the Scan'eat/Biolism systems ──────────────────────
val HydrationBlue   = Color(0xFF29B6F6)
val CalorieOrange   = Color(0xFFFF6B35)

// ── Grade → color (single source of truth — was duplicated per screen) ────────
// The red↔green axis is the one most CVD types confuse, so the colorblind-safe
// palette swaps to a blue→orange/brown gradient (Okabe & Ito 2008) where "worse"
// grades also get darker/lower-luminance, keeping the scale readable even when
// hue can't be trusted. Grade letters are always shown alongside the color too.
private val NormalGradeColors = mapOf(
    Grade.A_PLUS to Color(0xFF4CAF50),
    Grade.A      to Color(0xFF8BC34A),
    Grade.B      to Color(0xFFCDDC39),
    Grade.C      to Color(0xFFFF9800),
    Grade.D      to Color(0xFFFF5722),
    Grade.F      to Color(0xFFF44336),
)
// Safe for protanopia/deuteranopia (red-green confusion): diverges on the
// blue↔orange/brown axis instead, which that pair of deficiencies still sees fine.
private val ProtanDeuteranSafeGradeColors = mapOf(
    Grade.A_PLUS to Color(0xFF0072B2),
    Grade.A      to Color(0xFF56B4E9),
    Grade.B      to Color(0xFFE6C619),
    Grade.C      to Color(0xFFE69F00),
    Grade.D      to Color(0xFFD55E00),
    Grade.F      to Color(0xFF8B2E00),
)

// Safe for tritanopia (blue-yellow confusion) — the blue/orange scale above is
// one of the worst choices here since it sits right on the confused axis. This
// scale stays on teal↔red instead, which tritanopia leaves largely intact.
private val TritanopiaSafeGradeColors = mapOf(
    Grade.A_PLUS to Color(0xFF0B7A75),
    Grade.A      to Color(0xFF4FB3AC),
    Grade.B      to Color(0xFFB5B5B5),
    Grade.C      to Color(0xFFE8998D),
    Grade.D      to Color(0xFFD45D5D),
    Grade.F      to Color(0xFFA62B2B),
)

// ═══ LAYER 3 — COMPONENT (specific to one composable, not a general role) ════
/** The AccentCoral outlined-field color scheme repeated verbatim across ~7 screens. */
@Composable
// art-direction-engine §INPUTS: cursorColor/focusedLabelColor weren't set, so
// both silently fell back to Material's default `primary` role - Gold in this
// theme - while the border was explicitly AccentCoral. A focused field showed
// a coral border with a gold blinking cursor and gold label inside it: two
// different accents clashing in the same component instead of one coherent
// focus treatment.
fun scanEatTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentCoral, unfocusedBorderColor = OnBackground.copy(0.2f),
    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
    cursorColor = AccentCoral,
    focusedLabelColor = AccentCoral,
)

// Back to LAYER 2 — semantic (a meaning-bearing accessor used all over the
// app, not tied to one composable) — scanEatTextFieldColors() above was the
// exception, not the start of a new section.
@Composable
fun gradeColor(grade: Grade): Color {
    val palette = when (LocalColorblindMode.current) {
        "protanopia", "deuteranopia" -> ProtanDeuteranSafeGradeColors
        "tritanopia"                 -> TritanopiaSafeGradeColors
        else                         -> NormalGradeColors
    }
    return palette.getValue(grade)
}

// ── Colorblind-safe semantic color accessors (Okabe-Ito 2008 palette) — LAYER 2 ─
// All UI code should read these instead of the raw constants so that colorblind
// mode actually affects every element, not just grade chips.

// design-aesthetic-audit §DC3: FlagGreen/FlagRed/AmberWarning/HydrationBlue are
// flat literals tuned by eye against the dark themes only — same defect as
// TextSecondary/TextMuted/TextLabel had (see Colors.kt's own fix above), just
// undiscovered until this pass because these are semantic (colorblind-mode
// branched) rather than plain text roles. Each computes to roughly 2-2.1:1
// contrast against the Light theme's near-white background (0xFFF6F1EC) —
// well below the 4.5:1 WCAG AA floor — when used directly as text/icon tint,
// which every semantic* accessor's "none"/default branch does. Checking the
// actual rendered background's luminance (not the theme name, so this stays
// correct if a theme's exact background value ever changes) and swapping to a
// darkened variant on light grounds fixes all four at once.
private val LightSafeGreen  = Color(0xFF2E7D32)
private val LightSafeRed    = Color(0xFFC62828)
private val LightSafeAmber  = Color(0xFF8F4B00)
private val LightSafeBlue   = Color(0xFF01579B)

@Composable
internal fun isLightBackground(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

/** Good / positive / success signal. */
@Composable
fun semanticGreen(): Color = when (LocalColorblindMode.current) {
    "protanopia", "deuteranopia" -> Color(0xFF56B4E9) // sky blue — unambiguous for red-green CVD
    "tritanopia"                 -> Color(0xFF009E73) // bluish green — safe on blue-yellow axis
    else                         -> if (isLightBackground()) LightSafeGreen else FlagGreen
}

/** Bad / danger / rejection signal. */
@Composable
fun semanticRed(): Color = when (LocalColorblindMode.current) {
    "protanopia", "deuteranopia" -> Color(0xFFD55E00) // vermilion — distinct from sky blue above
    "tritanopia"                 -> Color(0xFFCC79A7) // reddish purple — safe on blue-yellow axis
    else                         -> if (isLightBackground()) LightSafeRed else FlagRed
}

/** Warning / caution signal. */
@Composable
fun semanticAmber(): Color = when (LocalColorblindMode.current) {
    "protanopia", "deuteranopia" -> Color(0xFFF0E442) // yellow — strongly distinct from vermilion
    "tritanopia"                 -> Color(0xFFE69F00) // orange — safe on blue-yellow axis
    else                         -> if (isLightBackground()) LightSafeAmber else AmberWarning
}

/** Hydration / water indicator. */
@Composable
fun semanticBlue(): Color = when (LocalColorblindMode.current) {
    // Was the same 0x009E73 as semanticGreen()'s tritanopia value - a hard hex
    // collision between two distinct meanings (success vs. hydration). Okabe-Ito's
    // "blue" isn't claimed by any other semantic accessor under this mode.
    "tritanopia" -> Color(0xFF0072B2)
    else         -> if (isLightBackground()) LightSafeBlue else HydrationBlue     // blue is fine for protan/deutan and normal
}
