package fr.scanneat.presentation.scan

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.ScanRepository
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import javax.inject.Inject

sealed class ScanUiState {
    data object Idle     : ScanUiState()
    data object Scanning : ScanUiState()
    data class  Success(val result: ScanResult, val persistedId: Long) : ScanUiState()
    data class  Error(val message: String) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,       // Fix 15/21: read language from preferences
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
                val result = if (barcode != null) {
                    scanRepo.scoreBarcode(barcode, imgs, lang)
                } else {
                    scanRepo.scoreFromImages(imgs, lang)
                }
                result.fold(
                    onSuccess = { (scanResult, id) -> _state.value = ScanUiState.Success(scanResult, id) },
                    onFailure = { _state.value = ScanUiState.Error(it.message ?: "Erreur inconnue") },
                )
            } finally {
                scoreMutex.unlock()
            }
        }
    }

    fun dismissError() { _state.value = ScanUiState.Idle }

    private fun Bitmap.toPayload(): ImagePayload {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, out)
        return ImagePayload(
            base64    = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            thumbnail = this,
        )
    }
}
