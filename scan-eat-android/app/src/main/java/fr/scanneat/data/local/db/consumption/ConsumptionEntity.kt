package fr.scanneat.data.local.db.consumption

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consumption_log",
    indices = [Index("profileId", "date")],
)
data class ConsumptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                // ISO date yyyy-MM-dd
    val mealSlot: String,
    val loggedAt: Long,              // epoch millis
    val productName: String,
    val barcode: String?,
    val portionG: Double,
    val nutritionJson: String,       // serialised NutritionPer100g
    val source: String,              // ScanSource key
    val profileId: String = "default",
)
