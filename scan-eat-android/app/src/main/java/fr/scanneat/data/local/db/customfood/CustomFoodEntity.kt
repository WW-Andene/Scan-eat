package fr.scanneat.data.local.db.customfood

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "custom_foods", indices = [Index("profileId"), Index("barcode", "profileId")])
data class CustomFoodEntity(
    @PrimaryKey val id: String,      // user-defined slug
    val name: String,
    val category: String,
    val nutritionJson: String,
    val createdAt: Long,
    val profileId: String = "default",
    // Set when this entry came from "Save to Mes Aliments" on a scanned barcoded
    // product - lets CustomFoodDao.upsertFood prefer this (a real, unambiguous
    // product identity) over the name-based dedup, which previously silently
    // merged two genuinely different products that happen to share a generic
    // display name (e.g. two brands both "Yaourt nature") into one row.
    val barcode: String? = null,
)
