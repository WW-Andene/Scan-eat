package fr.scanneat.data.local.db.weight

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Unique per (date, profileId), not date alone — every DAO in this app
// threads profileId through for future multi-profile support; a date-only
// unique index would let one profile's REPLACE upsert silently delete
// another profile's same-day entry the moment that ships.
@Entity(tableName = "weight_log", indices = [Index(value = ["date", "profileId"], unique = true)])
data class WeightEntity(
    @PrimaryKey val id: String,
    val date: String,              // ISO yyyy-MM-dd — unique per day per profile, upsert wins
    val weightKg: Double,
    val notes: String = "",
    val loggedAt: Long,            // epoch millis
    val profileId: String = "default",
)
