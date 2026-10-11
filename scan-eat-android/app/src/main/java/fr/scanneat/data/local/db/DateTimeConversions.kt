package fr.scanneat.data.local.db

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

// ============================================================================
// Shared LocalDate <-> ISO String and LocalDateTime <-> epoch-millis (UTC)
// conversions.
//
// Every entity date/time column stays a plain String/Long by design (no Room
// migration needed - these functions produce exactly the ISO/epoch format
// those columns already store), so this isn't a Room @TypeConverter - each
// repository's own toEntity()/toDomain() mapping calls these directly at the
// domain-model boundary. Centralized here instead of hand-duplicated per
// repository (ConsumptionRepository, WeightRepository, ActivityRepository,
// MedicationRepository all independently reimplemented the identical
// toEpochSecond(UTC)/ofEpochSecond(UTC) pair before this).
// ============================================================================

fun LocalDate.toIsoString(): String = toString()
fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

// nano / 1_000_000 (not dropped) - toEpochSecond() alone truncates to whole SECONDS,
// silently discarding the entire sub-second component. MealTemplateRepository.expand()
// relies on this function actually preserving millisecond-level differences between
// entries logged microseconds apart in the same batch (each item's loggedAt offset by
// +idx ms specifically so a later ORDER BY loggedAt ASC keeps them in their original
// order) - the previous whole-second truncation collapsed every item in one expand()
// call to the exact same stored value, silently defeating that ordering trick despite
// its own doc comment claiming the offset "survives a millis round-trip".
fun LocalDateTime.toEpochMillisUtc(): Long = toEpochSecond(ZoneOffset.UTC) * 1000 + nano / 1_000_000
fun Long.toLocalDateTimeUtc(): LocalDateTime =
    LocalDateTime.ofEpochSecond(this / 1000, ((this % 1000) * 1_000_000).toInt(), ZoneOffset.UTC)
