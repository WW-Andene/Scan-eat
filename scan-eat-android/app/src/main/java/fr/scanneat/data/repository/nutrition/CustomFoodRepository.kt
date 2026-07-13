package fr.scanneat.data.repository.nutrition

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// CUSTOM FOOD REPOSITORY — port of public/data/custom-food-db.js
//
// User-persisted foods that extend FOOD_DB for Quick Add autocomplete
// and LLM-identify reconciliation.
// Schema per entry: name, kcal, protein_g, carbs_g, fat_g per 100 g.
// Custom foods win ties in searchFoodDB (score -0.5 vs 0 for built-ins).
// ============================================================================

@Singleton
class CustomFoodRepository @Inject constructor(
    private val dao: CustomFoodDao,
    private val moshi: Moshi,
) {

    fun observeAll(profileId: String = "default"): Flow<List<FoodEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toFoodEntry() } }

    /** Same as [observeAll] but keeps each entry's stable Room id — needed for rename(), since FoodEntry itself carries no id. */
    fun observeAllWithId(profileId: String = "default"): Flow<List<Pair<String, FoodEntry>>> =
        dao.observeAll(profileId).map { list -> list.map { it.id to it.toFoodEntry() } }

    suspend fun save(
        name: String,
        kcal: Double,
        proteinG: Double = 0.0,
        carbsG: Double  = 0.0,
        fatG: Double    = 0.0,
        fiberG: Double  = 0.0,
        saltG: Double   = 0.0,
        aliases: List<String> = emptyList(),
        id: String? = null,
        profileId: String = "default",
    ): FoodEntry {
        // A blank/whitespace-only name previously passed through untouched: name.trim()
        // still persisted an empty string, silently adding an unnamed row to the custom
        // foods list (and to every Quick Add / search-autocomplete surface built on
        // observeAll()) with no way to identify or remove it afterward, since
        // deleteByName() keys on the very name that's blank. Reject it up front instead,
        // same guard style as WeightRepository.log()'s require().
        require(name.isNotBlank()) { "Custom food name must not be blank" }
        val entry = FoodEntry(
            name      = name.trim(),
            kcal      = kcal.coerceAtLeast(0.0),
            proteinG  = proteinG.coerceAtLeast(0.0),
            carbsG    = carbsG.coerceAtLeast(0.0),
            fatG      = fatG.coerceAtLeast(0.0),
            fiberG    = fiberG.coerceAtLeast(0.0),
            saltG     = saltG.coerceAtLeast(0.0),
            aliases   = aliases.filter { it.isNotBlank() },
        )
        dao.insert(CustomFoodEntity(
            id            = id ?: UUID.randomUUID().toString(),
            name          = entry.name,
            category      = "other",
            nutritionJson = jsonAdapter.toJson(CustomFoodJson(
                kcal = entry.kcal, proteinG = entry.proteinG, carbsG = entry.carbsG,
                fatG = entry.fatG, fiberG = entry.fiberG, saltG = entry.saltG,
                aliases = entry.aliases,
            )),
            createdAt     = System.currentTimeMillis(),
            profileId     = profileId,
        ))
        return entry
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteByName(name: String, profileId: String = "default") =
        dao.deleteByName(name, profileId)

    /**
     * CustomFoodScreen previously had delete as its only entry point — a typo
     * in a custom food's name could never be fixed without deleting and
     * re-creating it from scratch (losing its id, and any DiaryEntry/Recipe/
     * MealTemplate that already logged it keeps its own nutrition values
     * snapshotted independently at creation time, so they're unaffected by
     * this — only the searchable/displayed name changes going forward).
     */
    suspend fun rename(id: String, newName: String) {
        require(newName.isNotBlank()) { "Custom food name must not be blank" }
        val existing = dao.findById(id) ?: return
        dao.insert(existing.copy(name = newName.trim()))
    }

    /**
     * Search across both built-in FOOD_DB and user's custom foods.
     * Custom foods win ties. Port of searchFoodDB() with extraFoods.
     */
    suspend fun search(query: String, limit: Int = 6, profileId: String = "default"): List<FoodEntry> {
        val customs = dao.observeAll(profileId).first().map { it.toFoodEntry() }
        return searchFoodDB(query, limit, customs)
    }

    /**
     * Convert a custom food to a scorable Product.
     * Port of buildCustomFoodProductInput() from custom-food-db.js.
     */
    fun toProduct(entry: FoodEntry): Product = Product(
        name        = entry.name,
        category    = ProductCategory.OTHER,
        novaClass   = NovaClass.UNPROCESSED,
        ingredients = listOf(Ingredient(name = entry.name, percentage = 100.0,
            category = IngredientCategory.FOOD, isWholeFood = true)),
        nutrition   = NutritionPer100g(
            energyKcal    = entry.kcal,
            fatG          = entry.fatG,
            saturatedFatG = 0.0,
            carbsG        = entry.carbsG,
            sugarsG       = 0.0,
            fiberG        = entry.fiberG,
            proteinG      = entry.proteinG,
            saltG         = entry.saltG,
        ),
        weightG = 100.0,
    )

    @JsonClass(generateAdapter = true)
    internal data class CustomFoodJson(
        val kcal: Double = 0.0,
        val proteinG: Double = 0.0,
        val carbsG: Double = 0.0,
        val fatG: Double = 0.0,
        val fiberG: Double = 0.0,
        val saltG: Double = 0.0,
        val aliases: List<String> = emptyList(),
    )

    private val jsonAdapter = moshi.adapter(CustomFoodJson::class.java)

    private fun CustomFoodEntity.toFoodEntry(): FoodEntry {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository -
        // here a parse failure doesn't drop the row, it silently falls back to all-zero
        // nutrition instead, which is arguably worse (wrong data shown, not just missing).
        val j = runCatching { jsonAdapter.fromJson(nutritionJson) }
            .onFailure { android.util.Log.w("CustomFoodRepository", "Failed to parse nutrition JSON for '$name'", it) }
            .getOrNull()
            ?: CustomFoodJson()
        return FoodEntry(
            name     = name,
            kcal     = j.kcal,
            proteinG = j.proteinG,
            carbsG   = j.carbsG,
            fatG     = j.fatG,
            fiberG   = j.fiberG,
            saltG    = j.saltG,
            aliases  = j.aliases,
        )
    }
}
