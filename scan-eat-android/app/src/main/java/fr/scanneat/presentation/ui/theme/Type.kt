package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import fr.scanneat.R

// F19 (docs/design-audit-step7-typography.md): the app had no brand typeface
// at all — every screen fell back to the system default (Roboto or an OEM
// substitute), meaning its typography was, by construction, indistinguishable
// from any other Android app that also never set a fontFamily. Manrope is a
// humanist-geometric sans (per docs/design-audit-art-direction-brief.md's
// "warm, chaleureux, jamais froide" direction) — deliberately NOT a hacker/
// mono/sci-fi display face, per the user's explicit "this app is for
// everyone, not cyberpunk" constraint. Resolved via Android's Downloadable
// Fonts API (Google Play Services) rather than a bundled binary — falls back
// gracefully to the system font on devices without Play Services, so this is
// additive, never a hard requirement.
private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val ManropeGoogleFont = GoogleFont("Manrope")

val ManropeFontFamily = FontFamily(
    Font(googleFont = ManropeGoogleFont, fontProvider = GoogleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = ManropeGoogleFont, fontProvider = GoogleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = ManropeGoogleFont, fontProvider = GoogleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = ManropeGoogleFont, fontProvider = GoogleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = ManropeGoogleFont, fontProvider = GoogleFontProvider, weight = FontWeight.ExtraBold),
)

val ScanEatTypography = Typography(
    // Hero numbers (TDEE, ketosis timer, calorie balance) build their style from
    // this slot via .copy(fontSize=..., fontWeight=...) — every call site overrides
    // size/weight but still inherits lineHeight/letterSpacing, which would otherwise
    // silently fall back to Material3's un-tuned baseline default instead of this
    // app's own ratio (every other slot here is deliberately tuned).
    //
    // Category E audit (§E4/§DT2 Typography Craft): displayLarge/Medium/Small and
    // headlineLarge/Small had no letterSpacing at all — the four largest, heaviest
    // sizes in the whole scale were the only ones with no tracking decision made,
    // while headlineMedium/titleLarge/labelMedium/labelSmall each got a deliberate,
    // specific value. Untightened large bold text reads loose/amateur (standard
    // typographic convention, and this app's own §TRACKING-equivalent logic already
    // in use elsewhere); added negative tracking scaled to size, leaving the four
    // already-tuned slots' distinctive open/positive tracking untouched.
    displayLarge    = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold,      fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-1.0).sp),
    displayMedium   = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold,      fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.9).sp),
    displaySmall    = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium,   fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.7).sp),
    headlineLarge   = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium  = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.56.sp),
    headlineSmall   = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold,     fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge      = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.4.sp),
    titleMedium     = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall      = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge       = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium      = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall       = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge      = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium     = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.48.sp),
    labelSmall      = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal,   fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.44.sp),
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
    fontFamily          = ManropeFontFamily,
    fontWeight          = FontWeight.Black,
    fontFeatureSettings = "tnum",
)
