package fr.scanneat.data.local.db.sleep

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-requested: a sleep tracker at the same level of polish as Poids/
 * Hydratation/Jeûne - bedtime/wake time (a night can span midnight, so both
 * are stored as epoch millis rather than a same-day local time, same shape
 * FastingRepository's startMs/endMs already use for the same reason) plus a
 * subjective 1-5 quality rating. [date] is the WAKE date ("the night of Aug
 * 11→12" is keyed by Aug 12, matching how a user thinks about "last night's
 * sleep" when checking the app in the morning) - unique per (date,
 * profileId), same upsert-wins-by-day convention WeightEntity already uses.
 */
@Entity(tableName = "sleep_log", indices = [Index(value = ["date", "profileId"], unique = true)])
data class SleepEntity(
    @PrimaryKey val id: String,
    val date: String,          // ISO yyyy-MM-dd, wake date - unique per day per profile
    val bedtimeMs: Long,
    val wakeMs: Long,
    val quality: Int,          // 1-5
    val notes: String = "",
    val loggedAt: Long,        // epoch millis
    val profileId: String = "default",
)
