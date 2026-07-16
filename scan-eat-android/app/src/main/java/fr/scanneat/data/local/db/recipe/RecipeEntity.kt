package fr.scanneat.data.local.db.recipe

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes", indices = [Index("profileId")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val servings: Int = 1,
    val componentsJson: String,
    val createdAt: Long,
    val profileId: String = "default",
    val notes: String = "",
    // Recipe had no equivalent to ScanResult's favorite field at all - unlike
    // scan history, there was no way to pin a recipe to the top of the list.
    val favorite: Boolean = false,
)
