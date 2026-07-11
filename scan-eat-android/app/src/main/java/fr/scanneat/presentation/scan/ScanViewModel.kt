package fr.scanneat.presentation.scan

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.scan.ProductNotFoundException
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Reaches the user verbatim in the scan error banner — needs to actually say what to do. */
private fun invalidApiKeyMessage(lang: String) =
    if (lang == "en") "Groq rejected this API key — check it in Settings"
    else "Clé API Groq refusée — vérifiez-la dans Réglages"

/**
 * Groq model names get deprecated/retired periodically (the pinned
 * DEFAULT_MODEL/FALLBACK_MODEL are compile-time literals) - when that
 * happens Groq returns a 400/404 for the model, not the more common
 * 401/403/429/5xx this app already has friendly messages for, and it'd
 * otherwise surface as a bare HTTP error with no indication that the fix
 * is just picking a current model in Settings.
 */
private fun invalidModelMessage(lang: String) =
    if (lang == "en") "This AI model is no longer available — pick a current one in Settings"
    else "Ce modèle IA n'est plus disponible — choisissez-en un à jour dans Réglages"

sealed class ScanUiState {
    data object Idle     : ScanUiState()
    data object Scanning : ScanUiState()
    data class  Success(val result: ScanResult, val persistedId: Long) : ScanUiState()
    data class  Error(val message: String, val needsPhoto: Boolean = false) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,       // Fix 15/21: read language from preferences
    private val connectivityManager: ConnectivityManager,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _images = MutableStateFlow<List<ImagePayload>>(emptyList())
    val images: StateFlow<List<ImagePayload>> = _images.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    private val scoreMutex = Mutex()

    fun onBarcodeDetected(barcode: String) {
        if (_state.value is ScanUiState.Scanning) return
        if (_scannedBarcode.value == barcode) return
        _scannedBarcode.value = barcode
    }

    fun addPhoto(bitmap: Bitmap) {
        _images.value = _images.value + bitmap.toPayload()
    }

    fun removePhoto(index: Int) {
        _images.value = _images.value.toMutableList().also { it.removeAt(index) }
    }

    fun clearQueue() {
        _images.value = emptyList()
        _scannedBarcode.value = null
        _state.value = ScanUiState.Idle
    }

    fun score() {
        val barcode = _scannedBarcode.value
        val imgs    = _images.value
        if (barcode == null && imgs.isEmpty()) {
            _state.value = ScanUiState.Error("Scannez un code-barres ou prenez une photo")
            return
        }
        if (!scoreMutex.tryLock()) return   // already scoring — ignore double-tap
        viewModelScope.launch {
            try {
                _state.value = ScanUiState.Scanning
                val lang   = prefs.language.first()    // Fix 15/21: thread language into scan
                val online = isOnline()
                // A barcode already scanned before is served from the local cache
                // and needs no connection — only a real lookup requires one, so
                // the connectivity check happens inside the repo, after the cache
                // read, instead of blocking every scan up front.
                val result = if (barcode != null) {
                    scanRepo.scoreBarcode(barcode, imgs, lang, online)
                } else {
                    scanRepo.scoreFromImages(imgs, lang, online)
                }
                result.fold(
                    onSuccess = { (scanResult, id) -> _state.value = ScanUiState.Success(scanResult, id) },
                    onFailure = { e ->
                        _state.value = when {
                            e is ProductNotFoundException ->
                                ScanUiState.Error(e.message ?: "Produit introuvable", needsPhoto = true)
                            // A rejected API key (invalid/revoked, not just missing — that
                            // case is already caught earlier as a friendly message) would
                            // otherwise surface as a bare "HTTP 401 " to the user with no
                            // indication of what to actually do about it.
                            e is HttpException && (e.code() == 401 || e.code() == 403) ->
                                ScanUiState.Error(invalidApiKeyMessage(lang))
                            e is HttpException && (e.code() == 400 || e.code() == 404) ->
                                ScanUiState.Error(invalidModelMessage(lang))
                            else -> ScanUiState.Error(e.message ?: "Erreur inconnue")
                        }
                    },
                )
            } finally {
                scoreMutex.unlock()
            }
        }
    }

    fun dismissError() { _state.value = ScanUiState.Idle }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Photo-queue thumbnails render in a 64.dp box, but the capture itself is
     * 1600×1200 — holding that full bitmap per queued photo (~7.7MB each,
     * ARGB_8888) risks real memory pressure with more than one or two photos
     * queued. Scale down (aspect-preserving) for display; the full-res bytes
     * still go into [base64] for the OCR upload, which needs the resolution.
     */
    private fun Bitmap.toPayload(): ImagePayload {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, out)
        val scale = THUMBNAIL_MAX_PX.toFloat() / maxOf(width, height)
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        } else this
        if (thumb !== this) recycle()
        return ImagePayload(
            base64    = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            thumbnail = thumb,
        )
    }

    private companion object {
        const val THUMBNAIL_MAX_PX = 160
    }
}
