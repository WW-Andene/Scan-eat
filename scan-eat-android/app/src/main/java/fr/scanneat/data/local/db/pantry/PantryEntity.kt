package fr.scanneat.data.local.db.pantry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-requested: a real persisted "what do I actually have at home"
 * inventory - previously "Mon garde-manger" (Recipes' pantry-suggestion
 * mode) was only ever a free-text field or the scan history reused as an
 * approximation, with zero persistence (see RecipesViewModel's own comment:
 * "the app has no persisted pantry inventory feature"). Distinct from
 * Courses (`ManualGroceryRepository`/`grocery` list) - Courses tracks what to
 * buy, this tracks what's already bought and on hand, including how much is
 * left and whether it's about to expire.
 */
@Entity(
    tableName = "pantry",
    indices = [
        Index(value = ["profileId", "expiryDate"]),
        Index(value = ["barcode", "profileId"]),
    ],
)
data class PantryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val barcode: String?,
    val category: String,          // ProductCategory.key
    val quantity: Double,
    val unit: String,              // "g" | "mL" | "unit" - see PantryUnit
    val expiryDate: String?,       // ISO yyyy-MM-dd, null = no expiry tracked
    val addedAt: Long,             // epoch millis
    val profileId: String = "default",
)
