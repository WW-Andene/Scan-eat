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
 * it as food, same priority order as the barcode path in score() -
 * but via a single vision-LLM call (identifyOrScoreFromImages), not a
 * separate identifyProductName call followed by a second, near-identical
 * identifyFood call for the same photos whenever neither DB matched (the
 * common case: fresh produce, plated dishes never match either lookup).
 */
internal fun ScanViewModel.identifyFromPhotos() {
    val imgs = _images.value
    if (imgs.isEmpty()) return
    if (!scoreMutex.tryLock()) return
    viewModelScope.launch {
        try {
            _state.value = ScanUiState.Scanning
            val lang   = prefs.language.first()
            val online = isOnline()
            if (!online) {
                _state.value = ScanUiState.Error(offlineMessage(lang))
                return@launch
            }
            val identified = scanRepo.identifyOrScoreFromImages(imgs, lang, online, identifyMode = true)
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
                        else -> runCatching { scanRepo.persist(scanResult, activeProfileId.value) }
                            .fold(
                                onSuccess = { id -> _state.value = ScanUiState.Success(scanResult, id); announceScoreIfEnabled(scanResult) },
                                onFailure = { e -> _state.value = ScanUiState.Error(httpFriendlyMessage(e, lang)) },
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

/**
 * Server-only counterpart to identifyFromPhotos() for a plate holding several
 * distinct foods - ScanRepository.identifyMultiFromImages() (wired to the
 * server's POST /api/identify-multi, previously unreachable from the app)
 * returns one ScanResult per detected item instead of collapsing the whole
 * plate into a single one. Every returned result is persisted immediately,
 * same as the single-item path's success branch, so MultiFoodFoundDialog can
 * navigate straight to the existing Result screen for whichever item the
 * user taps without a second network round-trip. Each item is still
 * cross-checked against the medication/non-consumable name DBs first,
 * same as identifyFromPhotos - "a plate is never going to contain a pill
 * box" doesn't rule out the vision model misidentifying something else in
 * frame as one of the plate's "foods," and unlike the single-item path,
 * nothing here would otherwise stop that misidentification from being
 * persisted straight into scan_history with a nutrition-based score.
 */
internal fun ScanViewModel.identifyMultiFromPhotos() {
    if (!isPremium.value) return
    val imgs = _images.value
    if (imgs.isEmpty()) return
    if (!scoreMutex.tryLock()) return
    viewModelScope.launch {
        try {
            _state.value = ScanUiState.Scanning
            val lang   = prefs.language.first()
            val online = isOnline()
            if (!online) {
                _state.value = ScanUiState.Error(offlineMessage(lang))
                return@launch
            }
            val identified = scanRepo.identifyMultiFromImages(imgs, lang, online)
            identified.fold(
                onSuccess = { results ->
                    val nonEdibleNames = withContext(Dispatchers.IO) {
                        results.filter { r ->
                            findMedicationByName(appContext, r.product.name) != null ||
                                findNonConsumableByName(appContext, r.product.name) != null
                        }.map { it.product.name }.toSet()
                    }
                    val edibleResults = results.filterNot { it.product.name in nonEdibleNames }
                    // app-audit §N/§I3: same unguarded scanRepo.persist() gap as
                    // identifyFromPhotos() above - a Room write failure on any one
                    // item previously propagated out of this coroutine uncaught.
                    _state.value = if (edibleResults.isEmpty()) {
                        ScanUiState.Error(noFoodsDetectedMessage(lang))
                    } else {
                        runCatching { edibleResults.map { it to scanRepo.persist(it, activeProfileId.value) } }
                            .fold(
                                onSuccess = { items -> ScanUiState.MultiFoodFound(items = items) },
                                onFailure = { e -> ScanUiState.Error(httpFriendlyMessage(e, lang)) },
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
