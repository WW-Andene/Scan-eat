package fr.scanneat.presentation.menu

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.domain.engine.menu.MenuDish
import fr.scanneat.domain.engine.menu.analyzeMenuText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class MenuScanState {
    data object Idle : MenuScanState()
    data object Processing : MenuScanState()
    data class Result(val dishes: List<MenuDish>, val profileHasConstraints: Boolean) : MenuScanState()
    data object Error : MenuScanState()
}

/** Roadmap N3: restaurant menu read on the device (ML Kit), checked against the profile. */
@HiltViewModel
class MenuScanViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {
    private val _state = MutableStateFlow<MenuScanState>(MenuScanState.Idle)
    val state: StateFlow<MenuScanState> = _state.asStateFlow()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun processImage(base64: String) {
        _state.value = MenuScanState.Processing
        viewModelScope.launch {
            runCatching {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("undecodable image")
                val text = suspendCancellableCoroutine<String> { cont ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { cont.resume(it.text) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                val profile = prefs.profile.first()
                MenuScanState.Result(analyzeMenuText(text, profile, prefs.language.first()),
                    profile.allergens.isNotEmpty() || profile.diet != fr.scanneat.domain.engine.scoring.DietKey.NONE)
            }.onSuccess { _state.value = it }
                .onFailure { e -> if (e is CancellationException) throw e; _state.value = MenuScanState.Error }
        }
    }

    fun retry() { _state.value = MenuScanState.Idle }

    override fun onCleared() { recognizer.close() }
}
