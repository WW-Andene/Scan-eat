package fr.scanneat.util

import java.util.Locale

/**
 * Locale-US decimal formatting for display. The `"%.Nf".format(Locale.US, x)` shape
 * was independently inlined across dashboard/, weight/, biolism/, and result/ screens
 * (plus NutritionTable's private fmt1) - one shared helper instead of two dozen copies
 * of the same format string.
 */
fun Double.formatDecimal(digits: Int = 1): String = "%.${digits}f".format(Locale.US, this)
