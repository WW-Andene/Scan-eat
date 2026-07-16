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

fun LocalDateTime.toEpochMillisUtc(): Long = toEpochSecond(ZoneOffset.UTC) * 1000
fun Long.toLocalDateTimeUtc(): LocalDateTime = LocalDateTime.ofEpochSecond(this / 1000, 0, ZoneOffset.UTC)
