package fr.scanneat.shared

/**
 * Generic 4-tuple, matching stdlib's Pair/Triple naming — Kotlin has no
 * built-in Quadruple. Kept in its own file rather than inline in
 * ScoringEngine.kt (where it previously lived, sandwiched between two
 * domain-specific types) since it's a general-purpose utility, not
 * scoring-specific.
 */
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
