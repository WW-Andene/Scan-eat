package fr.scanneat.data.local.db.mood

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-requested: a Mood/Stress tab at the same level of polish as
 * Sommeil/Poids/Hydratation/Jeûne - a daily 1-5 mood rating and a daily 1-5
 * stress rating logged together (not two separate tables), since the user
 * asked for one "Humeur/Stress" tab, not two. [date] is unique per
 * (date, profileId), same upsert-wins-by-day convention SleepEntity/
 * WeightEntity already use - one entry per day, re-saving corrects it.
 */
@Entity(tableName = "mood_log", indices = [Index(value = ["date", "profileId"], unique = true)])
data class MoodEntity(
    @PrimaryKey val id: String,
    val date: String,          // ISO yyyy-MM-dd - unique per day per profile
    val mood: Int,             // 1-5 (1 = very low, 5 = very good)
    val stress: Int,           // 1-5 (1 = very low, 5 = very high)
    val notes: String = "",
    val loggedAt: Long,        // epoch millis
    val profileId: String = "default",
)
