package fr.scanneat.domain.model

/**
 * Shared millisecond time-unit constants. The literal `3_600_000L` (ms per hour) and
 * `24L * 60 * 60 * 1000` (ms per day) were independently reimplemented across
 * RemindersRepository, ComparisonRepository, TrackerViewModel, and FastingScreen —
 * one named constant instead of four copies of the same arithmetic.
 */
const val MS_PER_MINUTE: Long = 60_000L
const val MS_PER_HOUR: Long = 3_600_000L
const val MS_PER_DAY: Long = 24L * MS_PER_HOUR
