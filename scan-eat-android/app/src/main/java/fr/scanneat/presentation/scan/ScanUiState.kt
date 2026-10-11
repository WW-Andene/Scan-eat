package fr.scanneat.presentation.scan

import fr.scanneat.domain.engine.medication.MedicationDbEntry
import fr.scanneat.domain.engine.nonconsumable.NonConsumableDbEntry
import fr.scanneat.domain.model.ScanResult

sealed class ScanUiState {
    data object Idle     : ScanUiState()
    data object Scanning : ScanUiState()
    data class  Success(val result: ScanResult, val persistedId: Long) : ScanUiState()
    data class  Error(val message: String, val needsPhoto: Boolean = false) : ScanUiState()
    /** Barcode matched the medication lookup DB instead of a food product - offer to save it to Traitement rather than running it through food scoring. */
    data class  MedicationFound(val entry: MedicationDbEntry) : ScanUiState()
    /** Barcode matched a household/chemical product - not something to run through food scoring, and never something to imply is safe to consume. */
    data class  NonConsumableFound(val entry: NonConsumableDbEntry) : ScanUiState()
    /** Photo(s) held multiple distinct foods (a plate) - identifyMultiFromPhotos() already scored+persisted every one; the user picks which to view. */
    data class  MultiFoodFound(val items: List<Pair<ScanResult, Long>>) : ScanUiState()
}
