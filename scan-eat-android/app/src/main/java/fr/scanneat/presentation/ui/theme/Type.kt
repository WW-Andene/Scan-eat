package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ScanEatTypography = Typography(
    // Hero numbers (TDEE, ketosis timer, calorie balance) build their style from
    // this slot via .copy(fontSize=..., fontWeight=...) — every call site overrides
    // size/weight but still inherits lineHeight/letterSpacing, which would otherwise
    // silently fall back to Material3's un-tuned baseline default instead of this
    // app's own ratio (every other slot here is deliberately tuned).
    displayLarge    = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 45.sp, lineHeight = 52.sp),
    displayMedium   = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge   = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.56.sp),
    headlineSmall   = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge      = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.4.sp),
    titleMedium     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall      = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge       = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall       = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge      = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.48.sp),
    labelSmall      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.44.sp),
)

/**
 * The "hero number" role — TDEE, ketosis timer, calorie balance, score —
 * was previously copied from displaySmall with 4 different literal weight
 * spellings (Black/Medium/W500/Bold) and no shared base. Canonical weight
 * is Black; tabular figures are baked in so the digits never jitter in
 * width as they change. Screens still vary fontSize per their own
 * hierarchy — consolidating that onto one discrete size scale is a
 * separate, visually-verified follow-up.
 */
val HeroNumberStyle = TextStyle(
    fontWeight          = FontWeight.Black,
    fontFeatureSettings = "tnum",
)
