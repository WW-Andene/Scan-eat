package fr.scanneat.presentation.scan

import androidx.lifecycle.viewModelScope
import fr.scanneat.domain.engine.medication.findMedicationByName
import fr.scanneat.domain.engine.nonconsumable.findNonConsumableByName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * No-barcode identify flows for [ScanViewModel] (single-item and Premium
 * multi-food), split out of the main file to keep it from growing unbounded.
 * Both extension functions here touch the same [ScanViewModel]
 * private-turned-internal state (`scanRepo`, `prefs`, `appContext`,
 * `scoreMutex`, `_images`, `_state`, `isOnline()`) the main file itself uses —
 * no behavior change from when these lived inline.
 */

/**
 * Identifies whatever is in photo(s) with no barcode/DataMatrix/QR to
 * scan — a medication or household product without any machine-readable
 * code, or fresh produce / a plated dish. Previously this always assumed
 * food (scoreFromImages' identifyMode), which would score a medication box
 * as if it were something to eat. Checks the identified product's name
 * against the medication/non-consumable name-lookup DBs before treating
 * it as food, same priority order as the barcode path in score(), using a
 * single on-device recognition pass (identifyOrScoreFromImages).
 */
internal fun ScanViewModel.identifyFromPhotos() {
    val imgs = _images.value
    if (imgs.isEmpty()) return
    if (!scoreMutex.tryLock()) return
    viewModelScope.launch {
        try {
            _state.value = ScanUiState.Scanning
            val lang   = prefs.language.first()
            // On-device recognition - works offline.
            val identified = scanRepo.identifyOrScoreFromImages(imgs, lang, identifyMode = true)
            identified.fold(
                onSuccess = { scanResult ->
                    val name = scanResult.product.name
                    val medication = withContext(Dispatchers.IO) { findMedicationByName(appContext, name) }
                    val nonConsumable = if (medication == null) {
                        withContext(Dispatchers.IO) { findNonConsumableByName(appContext, name) }?.let { staticMatch ->
                            // Same static-CSV-has-no-ingredients gap fixed in
                            // ScanViewModel.score()'s barcode path 13/08/2026 - see
                            // that fix's own comment. [online] is already true here
                            // (checked above at this function's start), and this
                            // OCR-name match's own barcode is enough to try the
                            // same live-OPF enrichment.
                            if (staticMatch.ingredientsText.isNullOrBlank()) {
                                val liveIngredients = withContext(Dispatchers.IO) { scanRepo.findNonConsumableViaOpf(staticMatch.barcode) }?.ingredientsText
                                if (liveIngredients.isNullOrBlank()) staticMatch else staticMatch.copy(ingredientsText = liveIngredients)
                            } else staticMatch
                        }
                    } else null
                    when {
                        medication != null -> _state.value = ScanUiState.MedicationFound(medication)
                        nonConsumable != null -> { _state.value = ScanUiState.NonConsumableFound(nonConsumable); logNonConsumableScan(nonConsumable) }
                        // app-audit §N/§I3: scanRepo.persist() (a Room write) was
                        // previously unguarded here, unlike the identical
                        // identify->persist sequence in identifyShelfBox() (which
                        // wraps it in mapCatching) - a Room write failure would
                        // propagate out of this coroutine uncaught instead of
                        // surfacing as an Error state.
                        // code-audit §D8: onFailure must rethrow CancellationException
                        // first - without it, backing out of Scan mid-persist() gets
                        // the cancellation caught here and routed to an Error state
                        // instead of the coroutine actually stopping, which could
                        // leave a stale error banner behind after navigating away.
                        else -> runCatching { scanRepo.persist(scanResult, activeProfileId.value) }
                            .fold(
                                onSuccess = { id ->
                                    if (scanResult.needsOcrReview) _state.value = ScanUiState.ReviewOcr(scanResult, id)
                                    else { _state.value = ScanUiState.Success(scanResult, id); announceScoreIfEnabled(scanResult) }
                                },
                                onFailure = { e ->
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    _state.value = ScanUiState.Error(httpFriendlyMessage(e, lang))
                                },
                            )
                    }
                },
                onFailure = { e -> _state.value = ScanUiState.Error(httpFriendlyMessage(e, lang)) },
            )
        } finally {
            scoreMutex.unlock()
        }
    }
}
