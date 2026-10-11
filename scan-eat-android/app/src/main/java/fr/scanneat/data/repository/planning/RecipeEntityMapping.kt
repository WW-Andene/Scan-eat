package fr.scanneat.data.repository.planning

import com.squareup.moshi.JsonAdapter
import fr.scanneat.data.local.db.recipe.RecipeEntity

// ============================================================================
// RECIPE <-> ROOM ENTITY MAPPING — extracted verbatim out of RecipeRepository.kt,
// the cohesive "translate between the domain Recipe and its Room row" concern.
// Extension functions on Recipe/RecipeEntity, same package as RecipeRepository
// (same pattern as HealthConnectRepository's Ext files), taking the
// components JSON adapter explicitly rather than widening it - avoids
// threading the whole repository through, and both call sites (save()/
// observeAll()/findById()) just pass their own componentsAdapter, same as
// before. No caller outside RecipeRepository touches these.
// ============================================================================

internal fun Recipe.toEntity(profileId: String, componentsAdapter: JsonAdapter<List<RecipeComponent>>) = RecipeEntity(
    id             = id,
    name           = name,
    servings       = servings,
    componentsJson = componentsAdapter.toJson(components),
    createdAt      = createdAt,
    profileId      = profileId,
    notes          = notes,
    favorite       = favorite,
)

internal fun RecipeEntity.toDomain(componentsAdapter: JsonAdapter<List<RecipeComponent>>): Recipe? = runCatching {
    Recipe(
        id         = id,
        name       = name,
        servings   = servings,
        components = componentsAdapter.fromJson(componentsJson) ?: emptyList(),
        createdAt  = createdAt,
        notes      = notes,
        favorite   = favorite,
    )
}.onFailure {
    // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository.
    android.util.Log.w("RecipeRepository", "Failed to parse recipe id=$id", it)
}.getOrNull()
