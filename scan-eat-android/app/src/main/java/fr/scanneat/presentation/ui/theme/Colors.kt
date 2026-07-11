package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.scanneat.domain.model.Grade

// ============================================================================
// SCAN'EAT + BIOLISM — Unified Design Token System
//
// Two accent systems coexist:
//   Scan'eat: AccentGreen — scan scoring, diary, nutrition feedback
//   Biolism:  Gold/Teal/Violet — metabolic science, timers, analytics
//
// Spacing follows φ = 1.618 (matches Biolism CSS variables exactly):
//   sp1=4, sp2=6, sp3=10, sp4=16, sp5=26, sp6=42, sp7=68
// ============================================================================

// ── Shared neutrals ──────────────────────────────────────────────────────────
// Nectar: a near-black ground with a faint warm undertone (not flat OLED grey),
// paired with one sober warm-coral brand accent used app-wide for actions and
// active states. Semantic colors (Flag*, grade colors) stay a separate scale on
// purpose — accent means "you can act here," semantic colors mean "this is good
// or bad," and the two must never be the same hue or they stop being readable.
// Raw OLED literals — consumed only by Theme.kt to build the OLED color scheme
// (can't reference MaterialTheme.colorScheme while constructing it).
internal val OledBackgroundRaw     = Color(0xFF0F0D12)  // near-black, faint warm-plum undertone
internal val OledOnBackgroundRaw   = Color(0xFFEFEAE6)
internal val OledSurfaceRaw        = Color(0xFF1C1820)
internal val OledSurfaceVariantRaw = Color(0xFF2C2631)  // one step lighter than surface — the elevation tier Dark already has
internal val OledOnSurfaceRaw      = Color(0xFFCFC7CC)

// Theme-reactive roles. Every screen reads these instead of a fixed literal, so
// switching OLED/Sombre/Clair in Settings actually repaints the app — previously
// these were hardcoded OLED-black constants and the theme picker changed nothing.
val Background:     Color @Composable get() = MaterialTheme.colorScheme.background
val OnBackground:   Color @Composable get() = MaterialTheme.colorScheme.onBackground
val SurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val OnSurface:      Color @Composable get() = MaterialTheme.colorScheme.onSurface

// ── Scan'eat accent ───────────────────────────────────────────────────────────
val AccentGreen     = Color(0xFFD97C56)  // sober warm coral — the app's one brand accent
val FlagRed         = Color(0xFFEF5350)
val FlagGreen       = Color(0xFF66BB6A)
val AmberWarning    = Color(0xFFFFA726)

// ── Biolism accent system (φ-derived) ─────────────────────────────────────────
// Gold — primary action, live timer, BMR hero
val Gold            = Color(0xFFC9A84C)
val GoldDim         = Color(0x9DC9A84C)  // 0.618 α
val GoldGlow        = Color(0x3CC9A84C)  // 0.236 α
val GoldBorder      = Color(0x29C9A84C)  // 0.162 α
val GoldHaze        = Color(0x1AC9A84C)  // 0.10  α
val GoldTrace       = Color(0x0FC9A84C)  // 0.062 α

// Teal — secondary: substrate, ketosis, VO2
val Teal            = Color(0xFF38C8C8)
val TealGlow        = Color(0x3C38C8C8)
val TealBorder      = Color(0x2938C8C8)
val TealHaze        = Color(0x1A38C8C8)
val TealTrace       = Color(0x0F38C8C8)

// Violet — tertiary: fasting, hormones, protein
val Violet          = Color(0xFF9275E0)
val VioletGlow      = Color(0x3C9275E0)
val VioletBorder    = Color(0x299275E0)
val VioletHaze      = Color(0x1A9275E0)
val VioletTrace     = Color(0x0F9275E0)

// Warm orange — fat substrate (non-keto)
val Warm            = Color(0xFFE8A87C)
val WarmGlow        = Color(0x3CE8A87C)
val WarmHaze        = Color(0x1AE8A87C)

// Danger/severe
val Danger          = Color(0xFFC0392B)
val DangerGlow      = Color(0x3CC0392B)
val Severe          = Color(0xFFE06C5A)

// Fibre green
val MetaGreen       = Color(0xFFA0C878)
val MetaGreenHaze   = Color(0x1AA0C878)

// Icon inactive
val IconInactive    = Color(0xFF4E5468)

// ── Label & secondary text ────────────────────────────────────────────────────
val TextSecondary   = Color(0xFF7E859E)
val TextMuted       = Color(0xFF454A60)
val TextLabel       = Color(0xFF5C6278)

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

/** The AccentGreen outlined-field color scheme repeated verbatim across ~7 screens. */
@Composable
fun scanEatTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f),
    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
)

@Composable
fun gradeColor(grade: Grade): Color {
    val palette = when (LocalColorblindMode.current) {
        "protanopia", "deuteranopia" -> ProtanDeuteranSafeGradeColors
        "tritanopia"                 -> TritanopiaSafeGradeColors
        else                         -> NormalGradeColors
    }
    return palette.getValue(grade)
}
