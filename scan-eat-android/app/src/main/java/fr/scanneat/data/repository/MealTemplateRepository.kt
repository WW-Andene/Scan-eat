package fr.scanneat.data.repository

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.MealTemplateDao
import fr.scanneat.data.local.db.MealTemplateEntity
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// MEAL TEMPLATE REPOSITORY — port of public/data/meal-templates.js
//
// Templates are named snapshots of consumption items that can be re-applied
// to any date/meal with one tap. MFP calls these "Saved Meals".
// ============================================================================

@JsonClass(generateAdapter = true)
data class TemplateItem(
    val productName: String,
    val grams: Double,
    val meal: String,
    val kcal: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val satFatG: Double = 0.0,
    val sugarsG: Double = 0.0,
    val saltG: Double = 0.0,
    val proteinG: Double = 0.0,
    val quickAdd: Boolean = false,
)

data class MealTemplate(
    val id: String,
    val name: String,
    val meal: MealSlot,
    val items: List<TemplateItem>,
    val createdAt: Long,
) {
    val totalKcal: Int get() = items.sumOf { it.kcal }.toInt()
}

@Singleton
class MealTemplateRepository @Inject constructor(private val dao: MealTemplateDao,
    private val moshi: Moshi,
) {
    private val itemsAdapter = moshi.adapter<List<TemplateItem>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, TemplateItem::class.java)
    )

    fun observeAll(profileId: String = "default"): Flow<List<MealTemplate>> =
        dao.observeAll(profileId).map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun save(
        name: String,
        meal: MealSlot,
        items: List<TemplateItem>,
        id: String? = null,
        profileId: String = "default",
    ): MealTemplate {
        val template = MealTemplate(
            id        = id ?: UUID.randomUUID().toString(),
            name      = name.trim(),
            meal      = meal,
            items     = items,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(template.toEntity(profileId))
        return template
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun findById(id: String): MealTemplate? = dao.findById(id)?.toDomain()

    /**
     * Expand a template into concrete DiaryEntries for a given date.
     * Port of expandTemplate() from meal-templates.js.
     */
    fun expand(
        template: MealTemplate,
        date: LocalDate,
        mealOverride: MealSlot? = null,
    ): List<DiaryEntry> {
        val now = System.currentTimeMillis()
        return template.items.mapIndexed { idx, item ->
            DiaryEntry(
                date        = date,
                mealSlot    = mealOverride ?: MealSlot.valueOf(item.meal.uppercase()),
                loggedAt    = java.time.LocalDateTime.ofEpochSecond((now + idx) / 1000, 0, java.time.ZoneOffset.UTC),
                productName = item.productName,
                portionG    = item.grams,
                nutrition   = NutritionPer100g(
                    energyKcal    = if (item.grams > 0) item.kcal * 100 / item.grams else 0.0,
                    fatG          = if (item.grams > 0) item.fatG * 100 / item.grams else 0.0,
                    saturatedFatG = if (item.grams > 0) item.satFatG * 100 / item.grams else 0.0,
                    carbsG        = if (item.grams > 0) item.carbsG * 100 / item.grams else 0.0,
                    sugarsG       = if (item.grams > 0) item.sugarsG * 100 / item.grams else 0.0,
                    fiberG        = 0.0,
                    proteinG      = if (item.grams > 0) item.proteinG * 100 / item.grams else 0.0,
                    saltG         = if (item.grams > 0) item.saltG * 100 / item.grams else 0.0,
                ),
                source = ScanSource.LLM,  // quick-add origin
            )
        }
    }

    // ---- Mapping ----

    private fun MealTemplate.toEntity(profileId: String) = MealTemplateEntity(
        id        = id,
        name      = name,
        meal      = meal.name.lowercase(),
        itemsJson = itemsAdapter.toJson(items),
        createdAt = createdAt,
        profileId = profileId,
    )

    private fun MealTemplateEntity.toDomain(): MealTemplate? = runCatching {
        MealTemplate(
            id        = id,
            name      = name,
            meal      = MealSlot.valueOf(meal.uppercase()),
            items     = itemsAdapter.fromJson(itemsJson) ?: emptyList(),
            createdAt = createdAt,
        )
    }.getOrNull()
}


