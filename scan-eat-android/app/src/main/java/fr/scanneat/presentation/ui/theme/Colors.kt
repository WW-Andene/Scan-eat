package fr.scanneat.presentation.ui.theme

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
val Background      = Color(0xFF000000)  // OLED black
val OnBackground    = Color(0xFFEEEEEE)
val SurfaceVariant  = Color(0xFF1A1A1A)
val OnSurface       = Color(0xFFCCCCCC)

// ── Scan'eat accent ───────────────────────────────────────────────────────────
val AccentGreen     = Color(0xFF5BCA8E)
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
private val ColorblindSafeGradeColors = mapOf(
    Grade.A_PLUS to Color(0xFF0072B2),
    Grade.A      to Color(0xFF56B4E9),
    Grade.B      to Color(0xFFE6C619),
    Grade.C      to Color(0xFFE69F00),
    Grade.D      to Color(0xFFD55E00),
    Grade.F      to Color(0xFF8B2E00),
)

@Composable
fun gradeColor(grade: Grade): Color {
    val palette = if (LocalColorblindMode.current == "none") NormalGradeColors else ColorblindSafeGradeColors
    return palette.getValue(grade)
}
