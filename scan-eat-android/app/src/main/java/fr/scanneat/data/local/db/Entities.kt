package fr.scanneat.data.local.db

import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime

// ============================================================================
// ROOM ENTITIES — replaces IndexedDB stores from public/data/*.js
// ============================================================================

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val productName: String,
    val score: Int,
    val grade: String,
    val category: String,
    val sourceJson: String,          // serialised ScanSource enum
    val productJson: String,         // serialised Product
    val auditJson: String,           // serialised ScoreAudit
    val scannedAt: Long,             // epoch millis
    val profileId: String = "default",
)

@Entity(tableName = "consumption_log")
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

@Entity(tableName = "weight_log", indices = [Index("date", unique = true)])
data class WeightEntity(
    @PrimaryKey val id: String,
    val date: String,              // ISO yyyy-MM-dd — unique per day, upsert wins
    val weightKg: Double,
    val notes: String = "",
    val loggedAt: Long,            // epoch millis
    val profileId: String = "default",
)

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

@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val meal: String,              // default MealSlot key
    val itemsJson: String,         // serialised List<TemplateItem>
    val createdAt: Long,
    val profileId: String = "default",
)

@Entity(tableName = "custom_foods")
data class CustomFoodEntity(
    @PrimaryKey val id: String,      // user-defined slug
    val name: String,
    val category: String,
    val nutritionJson: String,
    val createdAt: Long,
    val profileId: String = "default",
)

// ============================================================================
// TYPE CONVERTERS
// ============================================================================

class Converters {
    @TypeConverter fun fromLocalDate(d: LocalDate): String = d.toString()
    @TypeConverter fun toLocalDate(s: String): LocalDate = LocalDate.parse(s)
    @TypeConverter fun fromLocalDateTime(dt: LocalDateTime): Long = dt.toEpochSecond(java.time.ZoneOffset.UTC) * 1000
    @TypeConverter fun toLocalDateTime(ms: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(ms / 1000, 0, java.time.ZoneOffset.UTC)
}

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val servings: Int = 1,
    val componentsJson: String,
    val createdAt: Long,
    val profileId: String = "default",
)
