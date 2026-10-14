package fr.scanneat.presentation.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.planning.GroceryItem
import fr.scanneat.domain.engine.planning.normalizeKey
import fr.scanneat.domain.engine.scoring.FamilyMemberVerdict
import fr.scanneat.domain.engine.scoring.familyVerdicts
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What store mode shows for the last product scanned in the aisle. */
data class StoreScan(
    val result: ScanResult,
    val family: List<FamilyMemberVerdict>,
    val lastPrice: Double?,
)

/**
 * Roadmap N7: the shelf scan of "En magasin" mode. Scores the barcode (same
 * path as a normal scan, so it lands in history), runs the family check (D10)
 * and finds the last price paid for that product.
 */
@HiltViewModel
class StoreModeViewModel @Inject constructor(
    private val scanRepo: ScanRepository,
    private val priceRepo: PriceRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    private val _last = MutableStateFlow<StoreScan?>(null)
    val last: StateFlow<StoreScan?> = _last.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private var lastBarcode: String? = null
    private var lastAtMs = 0L

    fun onBarcode(barcode: String, onScored: (ScanResult) -> Unit) {
        val now = System.currentTimeMillis()
        if (_busy.value || (barcode == lastBarcode && now - lastAtMs < 5_000)) return
        lastBarcode = barcode; lastAtMs = now
        _busy.value = true
        viewModelScope.launch {
            try {
                val lang = prefs.language.first()
                val profileId = prefs.activeProfileId.first()
                val result = scanRepo.scoreBarcode(barcode, lang = lang, profileId = profileId).getOrNull()?.first ?: return@launch
                val profiles = prefs.profileList.first()
                val prices = priceRepo.observeAll(profileId).first()
                val lastPrice = prices.filter { it.barcode == barcode || it.productName.equals(result.product.name, ignoreCase = true) }
                    .maxByOrNull { it.date }?.priceEuros
                _last.value = StoreScan(result, if (profiles.size >= 2) familyVerdicts(result.audit, result.product, profiles, lang) else emptyList(), lastPrice)
                onScored(result)
            } finally {
                _busy.value = false
            }
        }
    }
}

/**
 * Grocery item the scanned product fulfils: every word of the item (singular
 * form) appears as a whole word in the product name ("tomates" <- "Tomate
 * cerise"; "riz" never matches inside "chorizo").
 */
fun matchGroceryItem(productName: String, items: List<GroceryItem>): GroceryItem? {
    fun words(s: String) = normalizeKey(s).split(Regex("""\W+""")).filter { it.length >= 2 }
        .map { w -> if (w.length > 3 && (w.endsWith("s") || w.endsWith("x"))) w.dropLast(1) else w }.toSet()
    val product = words(productName)
    return items.firstOrNull { val w = words(it.name); w.isNotEmpty() && product.containsAll(w) }
}
