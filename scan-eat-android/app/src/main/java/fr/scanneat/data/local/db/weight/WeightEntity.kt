package fr.scanneat.data.local.db.weight

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weight_log", indices = [Index("date", unique = true)])
data class WeightEntity(
    @PrimaryKey val id: String,
    val date: String,              // ISO yyyy-MM-dd — unique per day, upsert wins
    val weightKg: Double,
    val notes: String = "",
    val loggedAt: Long,            // epoch millis
    val profileId: String = "default",
)
