package fr.scanneat.data.repository.planning

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.template.MealTemplateDao
import fr.scanneat.data.local.db.template.MealTemplateEntity
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

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
    val fiberG: Double = 0.0,
    val quickAdd: Boolean = false,
)

data class MealTemplate(
    val id: String,
    val name: String,
    val meal: MealSlot,
    val items: List<TemplateItem>,
    val createdAt: Long,
) {
    // Round rather than truncate - .toInt() biased every total down (e.g. a
    // template summing to 0.9g protein displayed "0g"), same fix already
    // applied to GroceryList.kt's aggregateGroceryList for the same reason.
    val totalKcal: Int     get() = items.sumOf { it.kcal }.roundToInt()
    val totalProteinG: Int get() = items.sumOf { it.proteinG }.roundToInt()
    val totalCarbsG: Int   get() = items.sumOf { it.carbsG }.roundToInt()
    val totalFatG: Int     get() = items.sumOf { it.fatG }.roundToInt()

    /**
     * Synthetic Product so a saved template can be run through the same
     * checkDiet()/checkUserAllergens() Recipe.toCheckProduct() already uses -
     * a "Saved Meal" template is logged repeatedly, exactly as likely to
     * contain a forbidden ingredient as a Recipe, but previously had no
     * equivalent check anywhere, so a vegan or allergic user got zero warning
     * for the one meal type they'd actually re-log most often.
     */
    fun toCheckProduct(): Product = Product(
        name        = name,
        category    = ProductCategory.OTHER,
        novaClass   = NovaClass.UNPROCESSED,
        ingredients = items.map { i -> Ingredient(name = i.productName, category = IngredientCategory.FOOD) },
        nutrition   = NutritionPer100g.EMPTY,
    )
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
        // Editing an existing template (rename/addItem/removeItem, called with its own
        // id) previously re-stamped createdAt to now on every save - same bug already
        // fixed in RecipeRepository/MedicationRepository/CustomFoodRepository - preserve
        // the original row's createdAt when one exists, since observeAll() orders by it.
        val createdAt = id?.let { dao.findById(it)?.createdAt } ?: System.currentTimeMillis()
        val template = MealTemplate(
            id        = id ?: UUID.randomUUID().toString(),
            name      = name.trim(),
            meal      = meal,
            items     = items,
            createdAt = createdAt,
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
        // Local wall-clock `now`, like every other DiaryEntry construction site
        // (the DiaryEntry default, RecipeRepository.collapse) - ConsumptionRepository
        // labels loggedAt as UTC when encoding to millis, so building this one
        // from a true-UTC epoch instead would offset it from every sibling entry
        // by the device's UTC offset, scrambling same-day chronological order.
        // +idx nanos (not seconds) so items don't collapse to one instant yet
        // still doesn't survive a millis round-trip past 1ms - fine, only their
        // relative order among each other matters.
        val now = java.time.LocalDateTime.now()
        return template.items.mapIndexed { idx, item ->
            DiaryEntry(
                date        = date,
                mealSlot    = mealOverride ?: MealSlot.valueOf(item.meal.uppercase()),
                loggedAt    = now.plusNanos(idx * 1_000_000L),
                productName = item.productName,
                portionG    = item.grams,
                nutrition   = NutritionPer100g(
                    energyKcal    = if (item.grams > 0) item.kcal * 100 / item.grams else 0.0,
                    fatG          = if (item.grams > 0) item.fatG * 100 / item.grams else 0.0,
                    saturatedFatG = if (item.grams > 0) item.satFatG * 100 / item.grams else 0.0,
                    carbsG        = if (item.grams > 0) item.carbsG * 100 / item.grams else 0.0,
                    sugarsG       = if (item.grams > 0) item.sugarsG * 100 / item.grams else 0.0,
                    // TemplateItem carried no fiberG field at all until now - every
                    // template-logged entry silently reported zero fiber regardless
                    // of what was actually added, unlike Recipe's identical shape.
                    fiberG        = if (item.grams > 0) item.fiberG * 100 / item.grams else 0.0,
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
    }.onFailure {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository -
        // a parse failure here previously vanished the template from its list with
        // zero trace, applied consistently across all 6 repos with this pattern.
        android.util.Log.w("MealTemplateRepository", "Failed to parse meal template id=$id", it)
    }.getOrNull()
}


