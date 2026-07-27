package fr.scanneat.presentation.result

import fr.scanneat.data.repository.planning.RecipeComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Multi-destination save from the scanned product's "Save to..." popup —
 * extracted verbatim out of ResultViewModel (same package): each destination is
 * independent (a product can go to several at once) and none of them requires
 * the others to be reachable first. Was already a public member; external
 * callers see no change.
 */
internal fun ResultViewModel.saveToDestinations(destinations: Set<SaveDestination>) {
    val scan = state.value.scanResult ?: return
    viewModelScope.launch {
        // Every write below previously ran completely unguarded - a Room/DataStore
        // write failure (disk-full, constraint violation) on any one of these 4
        // independent destinations crashed the app instead of just failing to
        // save that one destination. Reuses the same LogState.Error signal log()
        // already surfaces on failure.
        runCatching {
            // Symmetric with the check: unchecking Favoris and tapping Save must
            // actually unfavorite, not just skip re-favoriting — this popup is
            // the only way to change favorite status for an already-favorited
            // scan (the star icon opens it pre-selected rather than toggling).
            if (scan.dbId > 0) {
                val wantFavorite = SaveDestination.FAVORIS in destinations
                favoriteOverride.value = wantFavorite
                scanRepo.setFavorite(scan.dbId, wantFavorite)
            }
            if (SaveDestination.MES_ALIMENTS in destinations) {
                val n = scan.product.nutrition
                customFoodRepo.save(
                    name     = scan.product.name,
                    kcal     = n.energyKcal,
                    proteinG = n.proteinG,
                    carbsG   = n.carbsG,
                    fatG     = n.fatG,
                    fiberG   = n.fiberG,
                    saltG    = n.saltG,
                    // Previously dropped here too - CustomFoodRepository.save() had
                    // no params for these at all, so every custom food saved from a
                    // scan permanently reported zero iron/calcium/vitD/B12 regardless
                    // of what the source product actually contained.
                    ironMg    = n.ironMg ?: 0.0,
                    calciumMg = n.calciumMg ?: 0.0,
                    vitDUg    = n.vitDUg ?: 0.0,
                    b12Ug     = n.b12Ug ?: 0.0,
                    // Previously dropped here - the only dedup key left was the
                    // display name, which two differently-barcoded products can
                    // share (e.g. two brands both "Yaourt nature"), silently
                    // overwriting one with the other's nutrition values.
                    barcode  = scan.barcode,
                    // Also previously dropped - the scanned product's real,
                    // already-known category was discarded in favour of a
                    // hardcoded "other" on the saved custom food.
                    category = scan.product.category,
                )
            }
            if (SaveDestination.COURSES in destinations) {
                // Previously hardcoded to 100g regardless of the actual product — a
                // 1.5kg bag of rice and a 30g snack both landed on the grocery list
                // as "100 g", actively corrupting the "how much do I need to buy"
                // math aggregateGroceryList() exists to compute. weightG is the
                // label's own stated package weight when the scan captured one.
                // addOrUpdate (not add) - re-saving the same product on a later
                // shopping trip previously created a fresh duplicate line item
                // every time instead of refreshing the existing one's quantity.
                manualGroceryRepo.addOrUpdate(scan.product.name, scan.product.weightG ?: 100.0)
            }
            if (SaveDestination.REPAS in destinations) {
                val n = scan.product.nutrition
                // Previously always called save() with no id, so re-saving the same
                // scanned product created a brand-new one-component "recipe" every
                // time instead of recognizing it was already saved - matched by name
                // here since Recipe (unlike CustomFood) has no barcode field of its own.
                val existingId = recipeRepo.observeAll().first().find { it.name.equals(scan.product.name, ignoreCase = true) }?.id
                recipeRepo.save(
                    name = scan.product.name,
                    components = listOf(
                        RecipeComponent(
                            productName = scan.product.name, grams = 100.0,
                            kcal = n.energyKcal, proteinG = n.proteinG, carbsG = n.carbsG,
                            fatG = n.fatG, saltG = n.saltG, fiberG = n.fiberG,
                        ),
                    ),
                    id = existingId,
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            _logState.value = LogState.Error(e.message ?: if (language.value == "en") "Error" else "Erreur")
        }
    }
}
