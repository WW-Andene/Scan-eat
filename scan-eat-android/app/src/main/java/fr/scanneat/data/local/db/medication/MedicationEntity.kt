package fr.scanneat.data.local.db.medication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "medications", indices = [Index("profileId"), Index("barcode", "profileId")])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String = "",
    val scheduleNote: String = "",
    val barcode: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val profileId: String = "default",
    val reminderOn: Boolean = false,
    val reminderTime: String = "08:00",
    // Set the moment `active` flips to false, cleared when it flips back to
    // true - see MedicationViewModel.adherenceStreak/weeklyAdherence's own doc
    // comment on why: filtering historical-day adherence denominators by the
    // CURRENT `active` flag retroactively erased a deactivated medication's
    // real past misses/successes from every prior day's stats, not just future
    // ones. This timestamp lets those computations ask "was this medication
    // active on THIS historical date" instead of "is it active right now."
    val deactivatedAt: Long? = null,
)
