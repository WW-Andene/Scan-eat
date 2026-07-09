package fr.scanneat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    prefs: UserPreferences,
) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Read once at cold start — only used to pick the NavHost start destination. */
    var needsOnboarding: Boolean = false
        private set

    /** Reactive — the in-app theme preference can change any time via Settings. */
    val theme: StateFlow<String> = prefs.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "oled")
    val dyslexicFont: StateFlow<Boolean> = prefs.dyslexicFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val colorblindMode: StateFlow<String> = prefs.colorblindMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")

    init {
        viewModelScope.launch {
            needsOnboarding = !prefs.onboardingComplete.first()
            _ready.value = true
        }
    }
}
