package fr.scanneat.data.local.db.template

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "meal_templates", indices = [Index("profileId")])
data class MealTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val meal: String,              // default MealSlot key
    val itemsJson: String,         // serialised List<TemplateItem>
    val createdAt: Long,
    val profileId: String = "default",
    val favorite: Boolean = false,
)
