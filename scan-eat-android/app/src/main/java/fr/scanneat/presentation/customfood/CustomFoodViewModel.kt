package fr.scanneat.presentation.customfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomFoodViewModel @Inject constructor(
    private val repo: CustomFoodRepository,
    private val scanRepo: ScanRepository,
    prefs: UserPreferences,
) : ViewModel() {
    // Needed so the hint panel can cross-reference health conditions the same way
    // ResultViewModel's identical pair already does for scanned products — the
    // "💡 Bon à savoir" panel was previously reachable only from Result, despite
    // CustomFoodRepository.toProduct() already making every custom/built-in food
    // here just as eligible.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val profile: StateFlow<Profile> = prefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())

    /** Thin pass-through so the screen can build a Product for [fr.scanneat.domain.engine.nutrition.generateProductHints]
     *  without reaching into the repository directly — toProduct() already works on any FoodEntry, custom or built-in. */
    fun toProduct(entry: FoodEntry): Product = repo.toProduct(entry)

    val foods: StateFlow<List<FoodEntry>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** id-keyed, for the rename action — FoodEntry itself carries no stable id. */
    val foodsWithId: StateFlow<List<Pair<String, FoodEntry>>> = repo.observeAllWithId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Derived from the same live `foods` flow (not a separate repo.search() call), so
    // results can never go stale after a save/delete the way a one-shot search snapshot
    // could — and debounced + recomputed via combine instead of launching an unbounded,
    // uncancelled coroutine per keystroke.
    val searchResults: StateFlow<List<FoodEntry>> =
        combine(_query.debounce(200), foods) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 10, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    // Same pattern as RecipesViewModel/TemplatesViewModel - save/delete/rename/
    // importFromScan previously called repo's Room writes completely unguarded,
    // so an I/O failure (disk full, corrupt row) crashed the app instead of
    // surfacing here as a one-shot snackbar.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun save(
        name: String,
        kcal: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double = 0.0,
        saltG: Double = 0.0,
        // CustomFoodRepository.save() already accepted/persisted this - built-in
        // FOOD_DB entries carry aliases so a bilingual search finds them either
        // way (searchFoodDB is alias-aware), but nothing here ever forwarded a
        // value, so every custom food a user creates was permanently alias-less.
        aliases: List<String> = emptyList(),
        // Also already accepted/persisted by the repository (used for scan-import
        // via importFromScan below) but never collectible on a manually-typed food
        // until AddFoodDialog gained a barcode field - see that dialog's own comment.
        barcode: String? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                repo.save(name = name, kcal = kcal, proteinG = proteinG,
                          carbsG = carbsG, fatG = fatG, fiberG = fiberG, saltG = saltG, aliases = aliases, barcode = barcode)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Deletes by stable row id (not name) so two custom foods sharing a name can't cause one delete to remove both. */
    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun rename(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            // repo.rename() returns false (rather than throwing) on a name collision -
            // surface that the same way as any other failed write, since the dialog
            // otherwise closes as if the rename had succeeded while the food silently
            // keeps its old name.
            runCatching { repo.rename(id, newName) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
                .onSuccess { renamed -> if (!renamed) _actionFailed.value = true }
        }
    }

    // New: expose the most recent scan so the user can import its nutrition directly
    // as a custom food — previously every custom food had to be entered from scratch
    // even when the user had just scanned the exact same product.
    val latestScan: StateFlow<ScanResult?> = scanRepo.observeHistory(limit = 1)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Average kcal/100g across all saved custom foods — shown as a quick library stat. */
    val avgKcal: StateFlow<Int?> = foods.map { list ->
        if (list.isEmpty()) null else (list.sumOf { it.kcal } / list.size).toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun importFromScan(scan: ScanResult) {
        val n = scan.product.nutrition
        viewModelScope.launch {
            runCatching { repo.save(
                name     = scan.product.name,
                kcal     = n.energyKcal,
                proteinG = n.proteinG,
                carbsG   = n.carbsG,
                fatG     = n.fatG,
                fiberG   = n.fiberG,
                saltG    = n.saltG,
                // Previously omitted here (unlike ResultViewModel.saveToDestinations,
                // already fixed to pass both) - without the barcode, upsertFood's
                // fallback match is name-only, so a scan whose name happens to
                // case-insensitively collide with an existing custom food silently
                // overwrote that unrelated row's nutrition values instead of adding
                // a new one, since two differently-barcoded products can share a
                // generic display name (e.g. two brands both "Yaourt nature").
                barcode  = scan.barcode,
                category = scan.product.category,
            ) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
