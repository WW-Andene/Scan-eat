package fr.scanneat.presentation.ui.theme

import java.util.Locale

// Both conversion factors were previously inlined independently at every call
// site (WeightScreen.kt, ProfileScreen.kt, BiolismProfileScreen.kt) with no
// single source of truth - one copy (BiolismProfileScreen's dispWeight) had
// also silently dropped Locale.US, so it rendered "154,3 lb" (comma decimal
// separator) instead of "154.3 lb" on a French-locale device while every
// other weight display in the app used a dot.

/** 1 kilogram in pounds (avoirdupois). */
const val KG_TO_LB = 2.20462

/** 1 centimeter in inches. */
const val CM_TO_IN = 2.54

/** "154.3 lb" / "70.0 kg" — always Locale.US so the decimal separator doesn't vary by device locale. */
fun dispWeight(kg: Double, useImperial: Boolean): String =
    if (useImperial) "%.1f lb".format(Locale.US, kg * KG_TO_LB) else "%.1f kg".format(Locale.US, kg)

/** 1 US fluid ounce in milliliters. */
const val ML_TO_FLOZ = 0.033814

/** "24 fl oz" / "710 mL" — hydration volumes previously rendered "$ml mL" unconditionally,
 *  the only display in the app that ignored useImperial despite Weight/Profile/Biolism
 *  all respecting it. */
fun dispVolume(ml: Int, useImperial: Boolean): String =
    if (useImperial) "%.0f fl oz".format(Locale.US, ml * ML_TO_FLOZ) else "$ml mL"

/** "4.50 €" / "4.50 $" — the user's configured currency symbol (Settings > Devise),
 *  previously hardcoded "€" at every Expenses display site regardless of preference. */
fun dispCurrency(amount: Double, symbol: String, decimals: Int = 2): String =
    "%.${decimals}f %s".format(Locale.US, amount, symbol)
