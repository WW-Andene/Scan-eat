package fr.scanneat.shared

/**
 * Generic 4-tuple, matching stdlib's Pair/Triple naming — Kotlin has no
 * built-in Quadruple. Kept in its own file rather than inline in
 * ScoringEngine.kt (where it previously lived, sandwiched between two
 * domain-specific types) since it's a general-purpose utility, not
 * scoring-specific.
 */
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** Mirrors util/DecimalFormat.kt on the Android project — pins Locale.US so
 *  reason strings never render a device/server-locale-dependent decimal
 *  separator glued onto an English/French unit suffix. */
fun Double.formatDecimal(digits: Int = 1): String = "%.${digits}f".format(java.util.Locale.US, this)
