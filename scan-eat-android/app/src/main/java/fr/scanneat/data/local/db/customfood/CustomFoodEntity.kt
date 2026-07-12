package fr.scanneat.data.local.db.customfood

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "custom_foods", indices = [Index("profileId")])
data class CustomFoodEntity(
    @PrimaryKey val id: String,      // user-defined slug
    val name: String,
    val category: String,
    val nutritionJson: String,
    val createdAt: Long,
    val profileId: String = "default",
)
