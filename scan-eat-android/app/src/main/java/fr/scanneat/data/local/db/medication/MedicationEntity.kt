package fr.scanneat.data.local.db.medication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "medications", indices = [Index("profileId")])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String = "",
    val scheduleNote: String = "",
    val barcode: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val profileId: String = "default",
)
