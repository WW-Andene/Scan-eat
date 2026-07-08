package fr.scanneat.data.local.db.activity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "activity_log", indices = [Index("date")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String,              // MET activity key
    val minutes: Int,
    val kcalBurned: Int,
    val note: String = "",
    val loggedAt: Long,
    val profileId: String = "default",
)
