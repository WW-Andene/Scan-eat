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
)
