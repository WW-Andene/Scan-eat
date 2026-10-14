package fr.scanneat.presentation.recalls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.repository.recall.RecallEntry
import fr.scanneat.data.repository.recall.RecallRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One recalled product; [scanId] is its latest scan (null when only in the pantry). */
data class RecalledProduct(val barcode: String, val entry: RecallEntry, val scanId: Long?)

/** Roadmap D1: every confirmed RappelConso recall of a scanned or stocked product. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecallsViewModel @Inject constructor(
    recallRepo: RecallRepository,
    private val scanHistoryDao: ScanHistoryDao,
) : ViewModel() {
    val items: StateFlow<List<RecalledProduct>?> = recallRepo.observeRecalledProducts()
        .mapLatest { list -> list.map { (barcode, entry) -> RecalledProduct(barcode, entry, scanHistoryDao.latestIdForBarcode(barcode)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
