package fr.scanneat.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonClass
import fr.scanneat.data.local.db.CustomFoodDao
import fr.scanneat.data.local.db.CustomFoodEntity
import fr.scanneat.domain.engine.FoodEntry
import fr.scanneat.domain.engine.searchFoodDB
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
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
class CustomFoodRepository @Inject constructor(private val dao: CustomFoodDao) {

    fun observeAll(profileId: String = "default"): Flow<List<FoodEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toFoodEntry() } }

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
     * Search across both built-in FOOD_DB and user's custom foods.
     * Custom foods win ties. Port of searchFoodDB() with extraFoods.
     */
    suspend fun search(query: String, limit: Int = 6, profileId: String = "default"): List<FoodEntry> {
        val customs = dao.observeAll(profileId).map { it.map { e -> e.toFoodEntry() } }
            .let { flow -> var r = emptyList<FoodEntry>(); flow.collect { r = it }; r }
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
    private data class CustomFoodJson(
        val kcal: Double = 0.0,
        val proteinG: Double = 0.0,
        val carbsG: Double = 0.0,
        val fatG: Double = 0.0,
        val fiberG: Double = 0.0,
        val saltG: Double = 0.0,
        val aliases: List<String> = emptyList(),
    )

    private val jsonAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(CustomFoodJson::class.java)

    private fun CustomFoodEntity.toFoodEntry(): FoodEntry {
        val j = runCatching { jsonAdapter.fromJson(nutritionJson) }.getOrNull()
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
