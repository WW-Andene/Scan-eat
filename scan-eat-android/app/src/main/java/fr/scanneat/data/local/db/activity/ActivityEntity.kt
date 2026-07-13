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
    // Health Connect's own record id, set only for a workout imported from an
    // external source (a fitness tracker's own app writing straight into
    // Health Connect) via ActivityRepository.syncFromHealthConnect() - the
    // dedup key that makes re-running that sync safe (without it, the same
    // external session would be re-imported as a fresh duplicate on every
    // sync, since Health Connect itself has no "already imported" concept).
    // Null for every activity logged directly in-app.
    val externalSourceId: String? = null,
)
