package fr.scanneat.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {

    /** Where the screen navigates once onboarding finishes - see OnboardingScreen's single LaunchedEffect. */
    enum class Exit { SCAN, PROFILE }

    // Previously two independent signals (a `done` Boolean plus the "Complete
    // profile" button calling onGoToProfile() directly in the same onClick) -
    // both an onDone-observing LaunchedEffect and the direct callback fired off
    // their own navigate() call for the same tap, racing to decide the
    // post-onboarding destination. A single nullable Exit is now the only
    // source of truth: exactly one navigation happens, driven by one observer.
    private val _exit = MutableStateFlow<Exit?>(null)
    val exit: StateFlow<Exit?> = _exit.asStateFlow()

    fun setMode(mode: ApiMode) = viewModelScope.launch { prefs.setApiMode(mode) }
    // Trimmed to match SettingsViewModel.saveApiKey/saveServerUrl — an accidental
    // leading/trailing space (easy to pick up on a copy-paste from elsewhere) was
    // previously stored verbatim here, silently making the Groq key or server URL
    // invalid despite looking correct in the input field.
    fun setApiKey(key: String) = viewModelScope.launch { prefs.setGroqApiKey(key.trim()) }
    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url.trim()) }
    fun skipApiSetup() { /* no key/server set — barcode-only OFF lookups still work */ }
    fun finish(goToProfile: Boolean = false) {
        viewModelScope.launch { prefs.setOnboardingComplete(true) }
        _exit.value = if (goToProfile) Exit.PROFILE else Exit.SCAN
    }
}
