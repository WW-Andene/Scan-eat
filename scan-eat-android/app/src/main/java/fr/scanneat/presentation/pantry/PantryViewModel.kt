package fr.scanneat.presentation.pantry

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.pantry.PantryItem
import fr.scanneat.data.repository.pantry.PantryRepository
import fr.scanneat.data.repository.pantry.PantryUnit
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.scoring.computePersonalScore
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** How urgently a pantry row's expiry needs attention - drives badge color/sort emphasis. */
enum class PantryExpiryUrgency { NONE, OK, SOON, EXPIRED }

private const val SOON_DAYS = 3L

fun PantryItem.expiryUrgency(today: LocalDate = LocalDate.now()): PantryExpiryUrgency {
    val date = expiryDate ?: return PantryExpiryUrgency.NONE
    return when {
        date.isBefore(today) -> PantryExpiryUrgency.EXPIRED
        !date.isAfter(today.plusDays(SOON_DAYS)) -> PantryExpiryUrgency.SOON
        else -> PantryExpiryUrgency.OK
    }
}

/**
 * User-requested: a real persisted pantry inventory (see PantryEntity's own
 * doc comment) - "Mon garde-manège" previously existed only as a free-text
 * input mode in Recipes' suggestion dialog, nothing was ever saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PantryViewModel @Inject constructor(
    private val repo: PantryRepository,
    private val recallRepo: fr.scanneat.data.repository.recall.RecallRepository,
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {

    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    private val allItems: StateFlow<List<PantryItem>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // No way to filter a growing pantry list by name - fine for a handful of
    // staples, not once it holds dozens of items. Same in-memory contains()
    // filter as RecipesViewModel's own name search (the list is already fully
    // loaded from observeAll(), no DAO query needed for this small a dataset).
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val items: StateFlow<List<PantryItem>> = combine(allItems, _query) { list, q ->
        if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Expired or expiring within [SOON_DAYS] - surfaced as its own banner/count
     *  above the plain list, same "don't make the user scan every row" pattern
     *  Medication's interaction banner and Activity's overtraining warning use. */
    val expiringItems: StateFlow<List<PantryItem>> = allItems
        .map { list -> list.filter { it.expiryUrgency() in setOf(PantryExpiryUrgency.SOON, PantryExpiryUrgency.EXPIRED) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User-requested: flag a stocked item that's since turned out to be
    // recalled - see RecallRepository.observeRecalledBarcodes' own doc
    // comment on why this is cache-only (never a fresh network check per row).
    val recalledBarcodes: StateFlow<Set<String>> = recallRepo.observeRecalledBarcodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // User-requested: "alerte proactive si un produit du Frigo entre en
    // conflit avec une condition de santé" - a pantry item is only ever
    // checked against health conditions once, at scan time (the audit baked
    // into scan_history); adding a condition afterward (Profile screen) never
    // retroactively re-checked anything already sitting in the pantry. Same
    // "flag barcodes" pattern as recalledBarcodes above - recomputed fresh
    // from the current profile on every emission (not the stored audit), via
    // the same computePersonalScore() the Result screen itself uses, so a
    // condition added yesterday is reflected the next time this recomposes,
    // not just on the item's next rescan. veto (a hard contraindication, e.g.
    // pregnancy+alcohol) or a declared-allergen hit both count as a real
    // conflict; a merely lower personal score does not - that's just "less
    // ideal", not something worth an alert.
    val healthConflictBarcodes: StateFlow<Set<String>> = combine(allItems, prefs.profile, language) { list, profile, lang ->
        Triple(list, profile, lang)
    }.flatMapLatest { (list, profile, lang) ->
        flow {
            val barcodes = list.mapNotNull { it.barcode }.distinct()
            val conflicts = barcodes.mapNotNull { barcode ->
                val cached = scanRepo.getCachedByBarcode(barcode, profile.id, lang) ?: return@mapNotNull null
                val personal = computePersonalScore(cached.audit, cached.product, profile, lang)
                if (personal.veto || personal.allergenHits.isNotEmpty()) barcode else null
            }
            emit(conflicts.toSet())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun add(name: String, barcode: String?, category: ProductCategory, quantity: Double, unit: PantryUnit, expiryDate: LocalDate?) {
        guardedLaunch { repo.addOrUpdate(name.trim(), barcode, category, quantity, unit, expiryDate, activeProfileId.value) }
    }

    fun updateQuantity(id: String, quantity: Double) {
        guardedLaunch { repo.updateQuantity(id, quantity) }
    }

    fun updateDetails(id: String, quantity: Double, unit: PantryUnit, expiryDate: LocalDate?, category: ProductCategory) {
        guardedLaunch { repo.updateDetails(id, quantity, unit, expiryDate, category) }
    }

    private var lastDeleted: PantryItem? = null

    fun delete(item: PantryItem) {
        guardedLaunch {
            repo.delete(item.id)
            lastDeleted = item
        }
    }

    /** Re-creates the last deleted row (used by the "Undo" snackbar action) with its original stats. */
    fun undoDelete() {
        val item = lastDeleted ?: return
        lastDeleted = null
        guardedLaunch {
            repo.add(item.name, item.barcode, item.category, item.quantity, item.unit, item.expiryDate, activeProfileId.value)
        }
    }
}
