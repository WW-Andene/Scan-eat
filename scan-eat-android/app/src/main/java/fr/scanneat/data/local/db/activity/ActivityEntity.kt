package fr.scanneat.data.local.db.activity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "activity_log", indices = [Index("date", "profileId")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String,              // MET activity key
    val minutes: Int,
    val kcalBurned: Int,
    val note: String = "",
    val loggedAt: Long,
    val profileId: String = "default",
    // Sub-entry + free-form training metrics - e.g. STRENGTH/"bench_press" with
    // sets=4, reps=8, weightUsedKg=60.0, or RUNNING/"trail" with distanceKm=8.2.
    // All nullable: most activity types (yoga, HIIT) never populate them.
    val subType: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val distanceKm: Double? = null,
    val weightUsedKg: Double? = null,
)
