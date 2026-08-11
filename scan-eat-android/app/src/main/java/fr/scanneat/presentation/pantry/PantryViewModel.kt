package fr.scanneat.presentation.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.pantry.PantryItem
import fr.scanneat.data.repository.pantry.PantryRepository
import fr.scanneat.data.repository.pantry.PantryUnit
import fr.scanneat.domain.model.ProductCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val prefs: UserPreferences,
) : ViewModel() {

    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    val items: StateFlow<List<PantryItem>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Expired or expiring within [SOON_DAYS] - surfaced as its own banner/count
     *  above the plain list, same "don't make the user scan every row" pattern
     *  Medication's interaction banner and Activity's overtraining warning use. */
    val expiringItems: StateFlow<List<PantryItem>> = items
        .map { list -> list.filter { it.expiryUrgency() in setOf(PantryExpiryUrgency.SOON, PantryExpiryUrgency.EXPIRED) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _actionFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun add(name: String, barcode: String?, category: ProductCategory, quantity: Double, unit: PantryUnit, expiryDate: LocalDate?) {
        viewModelScope.launch {
            runCatching { repo.addOrUpdate(name.trim(), barcode, category, quantity, unit, expiryDate, activeProfileId.value) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun updateQuantity(id: String, quantity: Double) {
        viewModelScope.launch {
            runCatching { repo.updateQuantity(id, quantity) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    private var lastDeleted: PantryItem? = null

    fun delete(item: PantryItem) {
        viewModelScope.launch {
            runCatching { repo.delete(item.id) }
                .onSuccess { lastDeleted = item }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Re-creates the last deleted row (used by the "Undo" snackbar action) with its original stats. */
    fun undoDelete() {
        val item = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            runCatching {
                repo.add(item.name, item.barcode, item.category, item.quantity, item.unit, item.expiryDate, activeProfileId.value)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
