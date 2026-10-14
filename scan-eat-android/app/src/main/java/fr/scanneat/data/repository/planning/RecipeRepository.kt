package fr.scanneat.data.repository.planning

import fr.scanneat.data.local.db.recipe.RecipeDao
import com.squareup.moshi.Moshi
import fr.scanneat.data.repository.scan.OnDeviceLabelReader
import fr.scanneat.domain.engine.planning.parseRecipeText
import fr.scanneat.domain.engine.planning.parseSchemaOrgRecipe
import fr.scanneat.domain.engine.planning.suggestOfficialRecipes
import fr.scanneat.util.ioCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// RECIPE REPOSITORY — originally ported from the former web prototype's public/data/recipes.js
//
// Recipes differ from meal templates:
//   templates → each component becomes a separate diary entry
//   recipes   → all components collapse into ONE diary entry (the dish)
//
// Room entity + DAO defined inline (small schema, no shared DAO file impact).
//
// Domain types (Recipe/RecipeComponent/FetchedRecipeResult and their extension
// functions) live in RecipeModels.kt; Recipe<->RecipeEntity mapping lives in
// RecipeEntityMapping.kt. Import (web link, photo) and recipe ideas run on the
// device - see the bottom of this class.
// ============================================================================

@Singleton
class RecipeRepository @Inject constructor(private val dao: RecipeDao,
    moshi: Moshi,
    private val okHttp: OkHttpClient,
    private val labelReader: OnDeviceLabelReader,
) {
    // Internal (not private) so RecipeEntityMapping.kt's toEntity()/toDomain()
    // extension functions can reach it - same reasoning as HealthConnectRepository's
    // widened `context`.
    internal val componentsAdapter = moshi.adapter<List<RecipeComponent>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, RecipeComponent::class.java)
    )

    fun observeAll(profileId: String = "default"): Flow<List<Recipe>> =
        dao.observeAll(profileId).map { it.mapNotNull { e -> e.toDomain(componentsAdapter) } }

    suspend fun findById(id: String): Recipe? = dao.findById(id)?.toDomain(componentsAdapter)

    /** Case-insensitive name lookup - see RecipeDao.findByName's own doc comment. */
    suspend fun findByName(name: String, profileId: String = "default"): Recipe? =
        dao.findByName(name, profileId)?.toDomain(componentsAdapter)

    suspend fun save(
        name: String,
        components: List<RecipeComponent>,
        servings: Int = 1,
        id: String? = null,
        profileId: String = "default",
        notes: String = "",
        // §A5-audit finding: RecipesOperationsExt.undoDelete() re-saves a just-
        // deleted row with its original `id` to restore it - but dao.delete()
        // actually removes the row, so by the time undoDelete()'s save() runs,
        // `existing` below is always null (nothing left to find), silently
        // resetting createdAt to now() and favorite to false regardless of the
        // deleted entry's real values. A restored recipe jumped to the top of
        // the createdAt-sorted list and lost its favorite star, with nothing
        // in this function's signature able to tell it "restore exactly this."
        // These two optional overrides let a caller supply the exact prior
        // values in that one restore case, while every other call site (which
        // never passes them) keeps the existing existing?.field-or-default
        // behavior unchanged.
        createdAt: Long? = null,
        favorite: Boolean? = null,
    ): Recipe {
        // Editing an existing recipe (rename/re-portion, called with its own id)
        // previously re-stamped createdAt to now on every save - same bug already
        // fixed in MedicationRepository/CustomFoodRepository - preserve the
        // original row's createdAt when one exists.
        val existing = id?.let { dao.findById(it) }
        val resolvedCreatedAt = createdAt ?: existing?.createdAt ?: System.currentTimeMillis()
        val recipe = Recipe(
            id         = id ?: UUID.randomUUID().toString(),
            name       = name.trim(),
            servings   = servings.coerceAtLeast(1),
            components = components,
            createdAt  = resolvedCreatedAt,
            notes      = notes,
            // Same reconstruct-from-scratch shape as createdAt above - without
            // this, editing/renaming a favorited recipe would silently drop
            // favorite back to its false default on every save.
            favorite   = favorite ?: existing?.favorite ?: false,
        )
        dao.upsert(recipe.toEntity(profileId, componentsAdapter))
        // Same retention cap/pattern as ScanHistoryDao.trimNonFavorites - this
        // table had no cap at all until now despite carrying a componentsJson
        // blob per row; favorites are exempt, same as scan_history's favorites.
        dao.trimNonFavorites(MAX_ROWS, profileId)
        return recipe
    }

    private companion object {
        const val MAX_ROWS = 5000
        /** Recipe pages are far smaller; caps a pathological download. */
        const val MAX_PAGE_BYTES = 3L * 1024 * 1024
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    /**
     * Collapse a recipe into a single DiaryEntry for a given date + portion.
     * portionFraction = 1.0 means one full serving; 0.5 = half.
     */
    fun collapse(recipe: Recipe, date: LocalDate, mealSlot: MealSlot, portionFraction: Double = 1.0): DiaryEntry {
        val grams = recipe.totalGrams * portionFraction
        return DiaryEntry(
            date        = date,
            mealSlot    = mealSlot,
            productName = recipe.name,
            portionG    = grams.coerceAtLeast(1.0),
            nutrition   = recipe.nutritionPer100g,
            source      = ScanSource.MANUAL, // user-built recipe, not an AI photo estimate
            // Previously omitted, defaulting to emptyList() - DiaryViewModel.diaryWarnings
            // runs entry.toCheckProduct() against this list to surface allergen/diet
            // warnings on logged entries, so a recipe logged straight from Recipes never
            // got a warning even when the same recipe's own recipeWarnings flagged it.
            ingredients = recipe.components.map { c -> Ingredient(name = c.productName, category = IngredientCategory.FOOD) },
        )
    }

    // ---- Import & suggestions — all on the device, no server or AI ----

    /** Downloads the page the user asked to import and reads its schema.org Recipe data. */
    suspend fun fetchRecipeFromUrl(url: String, lang: String): Result<FetchedRecipeResult> = ioCatching {
        val en = lang == "en"
        val parsed = url.trim().toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?: error(if (en) "This isn't a valid web link" else "Ce n'est pas un lien web valide")
        // Cleartext traffic is disabled app-wide (manifest + network security config), so a pasted
        // http:// link could only fail with an opaque "CLEARTEXT communication not permitted".
        // Recipe sites serve https, so upgrade the scheme (and the implicit http port) up front.
        // Hosts network_security_config.xml deliberately allows over cleartext keep their http scheme.
        val cleartextAllowedHost = parsed.host.let { h ->
            h == "localhost" || h == "127.0.0.1" || h == "10.0.2.2" || h == "local" || h.endsWith(".local")
        }
        val httpUrl = if (parsed.scheme == "http" && !cleartextAllowedHost) {
            parsed.newBuilder().scheme("https").apply { if (parsed.port == 80) port(443) }.build()
        } else parsed
        val html = withContext(Dispatchers.IO) {
            okHttp.newCall(Request.Builder().url(httpUrl).header("Accept", "text/html").build()).execute().use { resp ->
                if (!resp.isSuccessful) error(if (en) "The page couldn't be loaded (${resp.code})" else "La page n'a pas pu être chargée (${resp.code})")
                // Recipe pages are well under this; guards against a huge download.
                resp.body?.source()?.let { src -> src.request(MAX_PAGE_BYTES); src.buffer.clone().readUtf8() }.orEmpty()
            }
        }
        parseSchemaOrgRecipe(html, httpUrl.toString())
            ?: error(if (en) "No recipe found on this page — the site doesn't publish its recipe data" else "Aucune recette trouvée sur cette page — le site ne publie pas ses données de recette")
    }

    /** Reads a photographed recipe card / cookbook page with on-device text recognition. */
    suspend fun identifyRecipeFromPhotos(images: List<ImagePayload>, lang: String): Result<FetchedRecipeResult> = ioCatching {
        parseRecipeText(labelReader.readLines(images))
            ?: error(if (lang == "en") "No recipe could be read — photograph the ingredients and steps, sharp and well lit"
                     else "Aucune recette lisible — photographiez les ingrédients et les étapes, nets et bien éclairés")
    }

    /** Built-in recipes that use [ingredient]. */
    suspend fun suggestRecipes(ingredient: String, lang: String): Result<List<FetchedRecipeResult>> = ioCatching {
        suggestOfficialRecipes(listOf(ingredient), english = lang == "en")
    }

    /** Built-in recipes that make the most of what's in the pantry. */
    suspend fun suggestFromPantry(items: List<String>, lang: String): Result<List<FetchedRecipeResult>> = ioCatching {
        suggestOfficialRecipes(items, english = lang == "en")
    }
}
