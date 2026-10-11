package fr.scanneat.presentation.recipes

import androidx.lifecycle.viewModelScope
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.data.repository.planning.scaledComponents
import fr.scanneat.data.repository.planning.toTemplateItems
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

// Recipe CRUD + logging operations for [RecipesViewModel] - split out of the
// main file to keep it from growing unbounded, same purely-structural split
// ScanRepository already went through for ScanOffLookup/ScanServerClient.
// Every function here touches the same RecipesViewModel private-turned-internal
// state (`repo`, `templateRepo`, `consumptionRepo`, `flagActionFailed()`,
// `_cloneUnmatchedCount`, `_allRecipes`, `lastDeleted`, `lastPreScale`) the main
// file itself used before the split - no behavior change.

fun RecipesViewModel.toggleFavorite(recipe: Recipe) = viewModelScope.launch {
    runCatching { repo.setFavorite(recipe.id, !recipe.favorite) }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
}

fun RecipesViewModel.save(name: String, components: List<RecipeComponent>, servings: Int = 1, notes: String = "") {
    viewModelScope.launch { runCatching { repo.save(name, components, servings, profileId = activeProfileId.value, notes = notes) }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() } }
}

// Same undo-delete pattern as Diary/Weight/ScanHistory/Medication/Grocery -
// snapshots the row right before deleting it so a snackbar "Undo" can bring
// it back. repo.save(id = ...) re-upserts the exact same row (same id), same
// mechanism rename()/updateNotes()/scale() below already use for in-place edits.
fun RecipesViewModel.delete(id: String) {
    val entry = _allRecipes.value.firstOrNull { it.id == id }
    viewModelScope.launch {
        runCatching { repo.delete(id) }
            .onSuccess { lastDeleted = entry }
            .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

fun RecipesViewModel.undoDelete() {
    val entry = lastDeleted ?: return
    lastDeleted = null
    viewModelScope.launch {
        // §A5-audit finding: dao.delete() actually removes the row, so without
        // passing these explicitly, repo.save()'s own existing-row lookup finds
        // nothing and silently resets createdAt to now() and favorite to false -
        // a restored recipe jumped to the top of the createdAt-sorted list and
        // lost its favorite star. See RecipeRepository.save()'s own doc comment.
        runCatching { repo.save(entry.name, entry.components, entry.servings, id = entry.id, profileId = activeProfileId.value, notes = entry.notes, createdAt = entry.createdAt, favorite = entry.favorite) }
            .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

/** Forks a user's own recipe into a new, independent copy - e.g. to try a
 *  variant ("Curry — spicy version") without altering the original.
 *  cloneOfficial() already proved this save-a-copy shape for official
 *  recipes; this extends it to the user's own saved list. Suffix follows
 *  the in-app language (not hardcoded French) - the same [RecipesViewModel.language]
 *  StateFlow every other language-aware ViewModel decision already uses. */
fun RecipesViewModel.duplicate(recipe: Recipe) {
    val suffix = if (language.value == "en") " (copy)" else " (copie)"
    save("${recipe.name}$suffix", recipe.components, recipe.servings, notes = recipe.notes)
}

/** Inverse of TemplatesViewModel.saveAsRecipe() - a Recipe has no meal of its own,
 *  so the screen must ask which slot before this can be saved as a Saved Meal. */
fun RecipesViewModel.saveAsTemplate(recipe: Recipe, meal: MealSlot) = viewModelScope.launch {
    runCatching { templateRepo.save(recipe.name, meal, recipe.toTemplateItems(meal), profileId = activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
}

/** Replaces a saved recipe's name/servings/ingredient list in place - previously
 *  the only way to change a saved recipe's ingredients was delete-and-recreate
 *  from scratch, or duplicate (which just copies the same list unchanged). */
fun RecipesViewModel.updateRecipe(recipe: Recipe, name: String, components: List<RecipeComponent>, servings: Int, notes: String) {
    if (name.isBlank() || components.isEmpty()) return
    viewModelScope.launch {
        runCatching { repo.save(name, components, servings.coerceAtLeast(1), id = recipe.id, profileId = activeProfileId.value, notes = notes) }
            .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

fun RecipesViewModel.rename(recipe: Recipe, newName: String) {
    if (newName.isBlank()) return
    // notes = recipe.notes - without this, renaming (a distinct UI action from
    // editing notes) would silently wipe any notes already saved on the recipe,
    // since save() otherwise defaults an unpassed notes to "".
    viewModelScope.launch { runCatching { repo.save(newName, recipe.components, recipe.servings, id = recipe.id, profileId = activeProfileId.value, notes = recipe.notes) }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() } }
}

/** Edits a recipe's prep notes/instructions independently of rename. */
fun RecipesViewModel.updateNotes(recipe: Recipe, notes: String) {
    viewModelScope.launch { runCatching { repo.save(recipe.name, recipe.components, recipe.servings, id = recipe.id, profileId = activeProfileId.value, notes = notes) }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() } }
}

/** Permanently rescales every component's stored quantity (grams/kcal/macros) for a new serving count. */
// Same undo pattern as delete above - scale() permanently overwrites every
// component's stored grams/kcal/macros with no confirmation step, so a
// fat-fingered "40 servings" instead of "4" previously had no way back.
fun RecipesViewModel.scale(recipe: Recipe, newServings: Int) {
    if (newServings <= 0) return
    viewModelScope.launch {
        runCatching {
            repo.save(recipe.name, recipe.scaledComponents(newServings), newServings, id = recipe.id, profileId = activeProfileId.value, notes = recipe.notes)
        }.onSuccess { lastPreScale = recipe }
            .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

fun RecipesViewModel.undoScale() {
    val entry = lastPreScale ?: return
    lastPreScale = null
    viewModelScope.launch {
        runCatching { repo.save(entry.name, entry.components, entry.servings, id = entry.id, profileId = activeProfileId.value, notes = entry.notes) }
            .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

fun RecipesViewModel.log(recipe: Recipe, mealSlot: MealSlot, portionFraction: Double = 1.0) {
    viewModelScope.launch {
        runCatching {
            consumptionRepo.log(repo.collapse(recipe, LocalDate.now(), mealSlot, portionFraction).copy(profileId = activeProfileId.value))
            // User-requested "connect everything": logging a recipe only ever
            // deducted stock for the recipe's own collapsed name (rarely a
            // pantry match) - a recipe is really several ingredients, each
            // individually stocked, so deduct each one by its own name/grams
            // instead. Best-effort per component: pantryRepo.deductStock()
            // already no-ops silently on no match / a UNITS-counted row (see
            // its own doc comment), so an ingredient not tracked in the
            // pantry simply doesn't affect anything, same as today.
            recipe.components.forEach { c ->
                pantryRepo.deductStock(barcode = null, name = c.productName, portionG = c.grams * portionFraction, profileId = activeProfileId.value)
            }
        }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

/** Logs an official recipe straight to the diary, no cloning required first. */
// portionFraction lets a user who only ate part of an official recipe log that
// fraction directly - previously always logged the full totalGrams, so eating
// half a "Poulet basquaise" meant cloning it into an editable recipe just to
// reach LogRecipeDialog's serving-fraction slider.
fun RecipesViewModel.logOfficial(recipe: OfficialRecipe, mealSlot: MealSlot, portionFraction: Double = 1.0) {
    viewModelScope.launch {
        val basis = recipe.totalGrams.takeIf { it > 0 } ?: 100.0
        fun per100(v: Double) = v * 100.0 / basis
        runCatching {
            consumptionRepo.log(
                DiaryEntry(
                    date        = LocalDate.now(),
                    mealSlot    = mealSlot,
                    productName = recipe.nameFr,
                    barcode     = null,
                    portionG    = basis * portionFraction,
                    // saturatedFatG/sugarsG/saltG previously hardcoded to 0.0
                    // despite OfficialRecipe exposing correctly-summed
                    // totalSaturatedFatG/totalSugarsG/totalSaltG right on the
                    // same object (used correctly by cloneOfficial()'s
                    // equivalent path) - a salt-cured/pastry/fried official
                    // recipe (quiche, moules-frites, ...) logged straight to
                    // the diary via this one-tap path recorded as containing
                    // zero salt/sugar/sat-fat, silently defeating the
                    // hypertension/diabetes daily-budget features for
                    // exactly the users who need them.
                    nutrition   = NutritionPer100g(
                        energyKcal    = per100(recipe.totalKcal),
                        fatG          = per100(recipe.totalFatG),
                        saturatedFatG = per100(recipe.totalSaturatedFatG),
                        carbsG        = per100(recipe.totalCarbsG),
                        sugarsG       = per100(recipe.totalSugarsG),
                        fiberG        = per100(recipe.totalFiberG),
                        proteinG      = per100(recipe.totalProteinG),
                        saltG         = per100(recipe.totalSaltG),
                    ),
                    source = ScanSource.MANUAL,
                    // Previously omitted, defaulting to emptyList() - DiaryViewModel.diaryWarnings
                    // runs entry.toCheckProduct() against this list to surface allergen/diet
                    // warnings, so logging an official recipe never got a warning even when
                    // this same screen's officialRecipeWarnings already flagged it pre-log.
                    ingredients = recipe.ingredients.map { i -> Ingredient(name = i.foodName, category = IngredientCategory.FOOD) },
                    profileId = activeProfileId.value,
                )
            )
        }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
}

/** Clones an official recipe into the user's own editable recipe list. */
fun RecipesViewModel.cloneOfficial(recipe: OfficialRecipe) {
    var unmatched = 0
    val components = recipe.ingredients.map { ing ->
        val food = FOOD_DB.firstOrNull { it.name.equals(ing.foodName, ignoreCase = true) }
        if (food == null) unmatched++
        RecipeComponent(
            productName = ing.foodName,
            grams       = ing.grams,
            kcal        = (food?.kcal ?: 0.0) * ing.grams / 100.0,
            proteinG    = (food?.proteinG ?: 0.0) * ing.grams / 100.0,
            carbsG      = (food?.carbsG ?: 0.0) * ing.grams / 100.0,
            fatG        = (food?.fatG ?: 0.0) * ing.grams / 100.0,
            fiberG      = (food?.fiberG ?: 0.0) * ing.grams / 100.0,
            saltG       = (food?.saltG ?: 0.0) * ing.grams / 100.0,
            saturatedFatG = (food?.saturatedFatG ?: 0.0) * ing.grams / 100.0,
            sugarsG     = (food?.sugarsG ?: 0.0) * ing.grams / 100.0,
        )
    }
    save(recipe.nameFr, components)
    if (unmatched > 0) _cloneUnmatchedCount.value = unmatched
}
