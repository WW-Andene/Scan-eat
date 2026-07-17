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
    val favorite: Boolean = false,
) {
    // Round rather than truncate - .toInt() biased every total down (e.g. a
    // template summing to 0.9g protein displayed "0g"), same fix already
    // applied to GroceryList.kt's aggregateGroceryList for the same reason.
    val totalKcal: Int     get() = items.sumOf { it.kcal }.roundToInt()
    val totalProteinG: Int get() = items.sumOf { it.proteinG }.roundToInt()
    val totalCarbsG: Int   get() = items.sumOf { it.carbsG }.roundToInt()
    val totalFatG: Int     get() = items.sumOf { it.fatG }.roundToInt()
    val totalGrams: Double get() = items.sumOf { it.grams }

    /**
     * Per-100g nutrition, same scaled-from-item-totals shape as Recipe's own
     * nutritionPer100g - unlike Recipe's version (whose RecipeComponent has no
     * satFatG/sugarsG field at all), TemplateItem already tracks both, so
     * they're included here rather than hardcoded to 0.0.
     */
    val nutritionPer100g: NutritionPer100g get() {
        val basis = if (totalGrams > 0) totalGrams else 100.0
        fun scale(v: Double) = v * 100.0 / basis
        return NutritionPer100g(
            energyKcal    = scale(items.sumOf { it.kcal }),
            fatG          = scale(items.sumOf { it.fatG }),
            saturatedFatG = scale(items.sumOf { it.satFatG }),
            carbsG        = scale(items.sumOf { it.carbsG }),
            sugarsG       = scale(items.sumOf { it.sugarsG }),
            fiberG        = scale(items.sumOf { it.fiberG }),
            proteinG      = scale(items.sumOf { it.proteinG }),
            saltG         = scale(items.sumOf { it.saltG }),
        )
    }

    /**
     * Synthetic Product so a saved template can be run through the same
     * checkDiet()/checkUserAllergens() Recipe.toCheckProduct() already uses -
     * a "Saved Meal" template is logged repeatedly, exactly as likely to
     * contain a forbidden ingredient as a Recipe, but previously had no
     * equivalent check anywhere, so a vegan or allergic user got zero warning
     * for the one meal type they'd actually re-log most often.
     *
     * nutrition previously hardcoded NutritionPer100g.EMPTY even though every
     * field it needs was already sitting right there on nutritionPer100g above
     * (itself only added later) - harmless for checkDiet/checkUserAllergens
     * (both only look at ingredients), but it silently starved any nutrition-
     * threshold-based use of this Product (e.g. generateProductHints's
     * fiber/protein/salt/sugar/sat-fat benefit and risk lines) of real data.
     */
    fun toCheckProduct(): Product = Product(
        name        = name,
        category    = ProductCategory.OTHER,
        novaClass   = NovaClass.UNPROCESSED,
        ingredients = items.map { i -> Ingredient(name = i.productName, category = IngredientCategory.FOOD) },
        nutrition   = nutritionPer100g,
    )
}

/**
 * TemplateItem and RecipeComponent carry almost identical fields - the only
 * structural difference is per-item meal/quickAdd (templates) vs dish-level
 * servings/notes/favorite (recipes). satFatG/sugarsG have no equivalent on
 * RecipeComponent, so they're dropped, matching what a manually-added recipe
 * ingredient already has (0 - RecipeComponent never tracked them either).
 */
fun MealTemplate.toRecipeComponents(): List<RecipeComponent> = items.map { i ->
    RecipeComponent(
        productName = i.productName,
        grams       = i.grams,
        kcal        = i.kcal,
        proteinG    = i.proteinG,
        carbsG      = i.carbsG,
        fatG        = i.fatG,
        saltG       = i.saltG,
        fiberG      = i.fiberG,
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
        val existing = id?.let { dao.findById(it) }
        val createdAt = existing?.createdAt ?: System.currentTimeMillis()
        val template = MealTemplate(
            id        = id ?: UUID.randomUUID().toString(),
            name      = name.trim(),
            meal      = meal,
            items     = items,
            createdAt = createdAt,
            // Same reconstruct-from-scratch save() as RecipeRepository - without this,
            // any edit (rename/addItem/removeItem) would silently un-favorite the template.
            favorite  = existing?.favorite ?: false,
        )
        dao.upsert(template.toEntity(profileId))
        return template
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

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
        favorite  = favorite,
    )

    private fun MealTemplateEntity.toDomain(): MealTemplate? = runCatching {
        MealTemplate(
            id        = id,
            name      = name,
            meal      = MealSlot.valueOf(meal.uppercase()),
            items     = itemsAdapter.fromJson(itemsJson) ?: emptyList(),
            createdAt = createdAt,
            favorite  = favorite,
        )
    }.onFailure {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository -
        // a parse failure here previously vanished the template from its list with
        // zero trace, applied consistently across all 6 repos with this pattern.
        android.util.Log.w("MealTemplateRepository", "Failed to parse meal template id=$id", it)
    }.getOrNull()
}


