package fr.scanneat.data.local.db.medication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One "I took this" event - MedicationEntity itself is only the active
 * list + reminder schedule, with no dated record of whether a dose was
 * actually taken on a given day. Without this, Traitement had no per-day
 * event of its own: not on its own tab, and not on the unified Calendar
 * (which combines every *other* tracker's dated log).
 */
@Entity(tableName = "medication_log", indices = [Index("date", "profileId"), Index("medicationId")])
data class MedicationLogEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val medicationName: String, // denormalized snapshot - survives the medication itself being renamed/deleted later
    val date: String,
    val takenAt: Long,
    val profileId: String = "default",
)
