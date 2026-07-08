package fr.scanneat.data.local.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromLocalDate(d: LocalDate): String = d.toString()
    @TypeConverter fun toLocalDate(s: String): LocalDate = LocalDate.parse(s)
    @TypeConverter fun fromLocalDateTime(dt: LocalDateTime): Long = dt.toEpochSecond(java.time.ZoneOffset.UTC) * 1000
    @TypeConverter fun toLocalDateTime(ms: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(ms / 1000, 0, java.time.ZoneOffset.UTC)
}
