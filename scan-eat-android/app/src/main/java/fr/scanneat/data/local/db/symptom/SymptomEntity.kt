package fr.scanneat.data.local.db.symptom

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-requested: a free-form symptom journal (bloating, energy, sleep,
 * headaches...) correlated against what was actually logged in the diary
 * that same day - previously nothing in the app connected how a user felt
 * to what they ate, despite both being tracked (food) or askable (symptoms)
 * separately. Deliberately a flat log, not a per-symptom-type table - see
 * SymptomType's own doc comment for why the type set stays open-ended.
 */
@Entity(
    tableName = "symptoms",
    indices = [Index(value = ["profileId", "date"])],
)
data class SymptomEntity(
    @PrimaryKey val id: String,
    val date: String,          // ISO yyyy-MM-dd
    val type: String,          // SymptomType.key, or a free-typed custom label
    val severity: Int,         // 1-5
    val notes: String,
    val loggedAt: Long,        // epoch millis
    val profileId: String = "default",
)
