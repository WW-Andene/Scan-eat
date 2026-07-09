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

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun setMode(mode: ApiMode) = viewModelScope.launch { prefs.setApiMode(mode) }
    fun setApiKey(key: String) = viewModelScope.launch { prefs.setGroqApiKey(key) }
    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url) }
    fun skipApiSetup() { /* no key/server set — barcode-only OFF lookups still work */ }
    fun finish() {
        viewModelScope.launch { prefs.setOnboardingComplete(true) }
        _done.value = true
    }
}
