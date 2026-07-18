package fr.scanneat.data.repository.nutrition

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.scoring.inferCategoryFromName
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        dao.observeAll(profileId).map { list -> list.mapNotNull { it.toFoodEntry() } }

    /** Same as [observeAll] but keeps each entry's stable Room id — needed for rename(), since FoodEntry itself carries no id. */
    fun observeAllWithId(profileId: String = "default"): Flow<List<Pair<String, FoodEntry>>> =
        dao.observeAll(profileId).map { list -> list.mapNotNull { e -> e.toFoodEntry()?.let { e.id to it } } }

    /**
     * Looks up a custom food by its real, unambiguous barcode identity — the
     * same one CustomFoodDao.upsertFood already prefers on save. Used by
     * ScanRepository.scoreBarcode() as a last-resort fallback when neither OFF
     * nor the vision LLM can identify a barcode: a user who already manually
     * taught the app an obscure/local/homemade product (see save()'s barcode
     * param) previously hit the exact same "product not found" wall on every
     * single rescan, with no way for the app to recall what it already knows.
     */
    suspend fun findByBarcode(barcode: String, profileId: String = "default"): FoodEntry? =
        dao.findByBarcode(barcode, profileId)?.toFoodEntry()

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
        // Set when this save originates from a scanned barcoded product (see
        // ResultViewModel.saveToDestinations) - a real, unambiguous identity
        // that CustomFoodDao.upsertFood prefers over the name-based match, so
        // two differently-barcoded products sharing a generic name don't
        // silently overwrite each other. Null for a manually-typed custom food,
        // which falls back to the existing name-only dedup.
        barcode: String? = null,
        // Set from the scanned product's real category when this save originates
        // from a scan (see ResultViewModel.saveToDestinations); null for a
        // manually-typed custom food, which falls back to the same name-based
        // inference OffMapper/ScoringEngine already use elsewhere. Previously
        // always hardcoded to "other" regardless of what was actually scanned -
        // harmless today (custom foods are never run through scoreProduct()),
        // but a real gap if that ever changes, and the entity column existed
        // and was persisted/backed-up while never holding a meaningful value.
        category: ProductCategory? = null,
    ): FoodEntry {
        // A blank/whitespace-only name previously passed through untouched: name.trim()
        // still persisted an empty string, silently adding an unnamed row to the custom
        // foods list (and to every Quick Add / search-autocomplete surface built on
        // observeAll()) with no way to identify it afterward in the UI. Reject it up
        // front instead, same guard style as WeightRepository.log()'s require().
        require(name.isNotBlank()) { "Custom food name must not be blank" }
        // Only rejected negatives before - a typo or fat-finger (e.g. "1000" meant
        // as kcal in the protein field, or a missing decimal point turning 45.0g
        // into 4500g) flowed straight into Dashboard's MicronutrientCard bars,
        // dailyTargets percentage math, and chronic-gap detection unclamped. 900
        // is generous for kcal/100g (pure fat/oil tops out around 884); 100 is the
        // physical ceiling for any single macro per 100g of product.
        val entry = FoodEntry(
            name      = name.trim(),
            kcal      = kcal.coerceIn(0.0, 900.0),
            proteinG  = proteinG.coerceIn(0.0, 100.0),
            carbsG    = carbsG.coerceIn(0.0, 100.0),
            fatG      = fatG.coerceIn(0.0, 100.0),
            fiberG    = fiberG.coerceIn(0.0, 100.0),
            saltG     = saltG.coerceIn(0.0, 100.0),
            aliases   = aliases.filter { it.isNotBlank() },
        )
        // Two custom foods sharing a name break Compose's LazyColumn key uniqueness in
        // CustomFoodScreen and make deleteByName() delete every row with that name
        // instead of just one. When no explicit id is given (a fresh "Add", not a
        // rename/update of a known row), reuse an existing matching row's id instead
        // of creating a silent duplicate — "Add" on an existing product updates it in
        // place (matched by barcode first when given, else by name; see upsertFood).
        // The match-resolution read and the insert both run inside upsertFood's own
        // @Transaction, so two concurrent save() calls for the same product can't
        // both read "no existing row" and both insert a duplicate.
        val resolvedCategory = category ?: inferCategoryFromName(entry.name)
        dao.upsertFood(entry.name, barcode, profileId, id) { resolvedId ->
            CustomFoodEntity(
                id            = resolvedId,
                name          = entry.name,
                category      = resolvedCategory.key,
                nutritionJson = jsonAdapter.toJson(CustomFoodJson(
                    kcal = entry.kcal, proteinG = entry.proteinG, carbsG = entry.carbsG,
                    fatG = entry.fatG, fiberG = entry.fiberG, saltG = entry.saltG,
                    aliases = entry.aliases,
                )),
                createdAt     = System.currentTimeMillis(),
                profileId     = profileId,
                barcode       = barcode,
            )
        }
        return entry
    }

    suspend fun delete(id: String) = dao.delete(id)

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
        // Renaming onto another custom food's name would leave two rows sharing a
        // name (the same crash/over-deletion hazard save() guards against) — the
        // collision check and the insert both run inside renameIfNoCollision's own
        // @Transaction, so two concurrent renames can't both pass the check and
        // both write, and skips silently (rather than create the duplicate) if a
        // real collision exists.
        dao.renameIfNoCollision(id, newName.trim())
    }

    /**
     * Search across both built-in FOOD_DB and user's custom foods.
     * Custom foods win ties. Port of searchFoodDB() with extraFoods.
     */
    suspend fun search(query: String, limit: Int = 6, profileId: String = "default"): List<FoodEntry> {
        val customs = dao.observeAll(profileId).first().mapNotNull { it.toFoodEntry() }
        return searchFoodDB(query, limit, customs)
    }

    /**
     * Convert a custom food to a scorable Product.
     * Port of buildCustomFoodProductInput() from custom-food-db.js.
     */
    fun toProduct(entry: FoodEntry): Product = Product(
        name        = entry.name,
        // FoodEntry (shared with built-in FOOD_DB search results) carries no
        // category of its own to read back here — infer from name the same way
        // OffMapper/ScoringEngine already do for exactly this situation, rather
        // than the previous hardcoded OTHER regardless of what the food is.
        category    = inferCategoryFromName(entry.name),
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
            // Previously omitted here even though FoodEntry carries them (FOOD_DB
            // entries used as gap-closer suggestions genuinely have these values -
            // épinard/saumon/oeuf etc.) and the sibling FoodEntry.toProduct(portionG)
            // extension already copies them correctly. This is the converter Diary's
            // Quick Add and ScanRepository.customFoodByBarcode actually use, so every
            // manually-logged custom food silently reported zero iron/calcium/vitD/
            // B12 contribution regardless of what the food really contains -
            // corrupting MicronutrientCard's bars and chronic-gap detection for
            // exactly the foods most likely to close those gaps.
            ironMg        = entry.ironMg,
            calciumMg     = entry.calciumMg,
            vitDUg        = entry.vitDUg,
            b12Ug         = entry.b12Ug,
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

    private fun CustomFoodEntity.toFoodEntry(): FoodEntry? {
        // Was falling back to an all-zero CustomFoodJson() on parse failure, so a
        // corrupted row showed up in Quick Add/search as a real 0-kcal food a user
        // could log — silently corrupting their totals instead of just being
        // absent. Now dropped, matching ConsumptionRepository's drop-on-fail.
        val j = runCatching { jsonAdapter.fromJson(nutritionJson) }
            .onFailure { android.util.Log.w("CustomFoodRepository", "Failed to parse nutrition JSON for '$name'", it) }
            .getOrNull() ?: return null
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
