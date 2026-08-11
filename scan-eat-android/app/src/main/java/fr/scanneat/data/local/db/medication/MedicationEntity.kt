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
    // User-requested: medication tracking had no structured dosing schedule at
    // all - just this single daily reminderTime and a free-text scheduleNote
    // ("2x/jour", "lundi/mercredi/vendredi") that never actually drove the
    // reminder. 0 = every day (matches every pre-existing row's implicit
    // behavior); otherwise a 7-bit mask, bit (DayOfWeek.value - 1) set = active
    // that day (bit 0 = Monday ... bit 6 = Sunday). See Medication.isScheduledOn().
    val scheduleDaysMask: Int = 0,
    // Comma-separated "HH:mm" times beyond the first (reminderTime itself is
    // always slot 0) - lets a medication taken multiple times/day (e.g.
    // "matin et soir") get reminded at each real time instead of only once.
    // See Medication.reminderTimes().
    val extraReminderTimes: String = "",
)
