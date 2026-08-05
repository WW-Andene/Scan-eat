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
    val colorAccent: StateFlow<String> = prefs.colorAccent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val dyslexicFont: StateFlow<Boolean> = prefs.dyslexicFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val colorblindMode: StateFlow<String> = prefs.colorblindMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    val animatedBackground: StateFlow<Boolean> = prefs.animatedBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            // If this DataStore read throws (corrupt prefs file, IO error), _ready
            // previously never flipped to true - the whole app hung on the splash
            // screen forever with no fallback, the one unguarded write/read left in
            // the entire ViewModel layer. Defaults to "onboarding not needed" on
            // failure so the app still boots into the main flow rather than
            // getting stuck; a genuinely new user who hits this rare failure just
            // sees onboarding again later from Settings instead of being locked out.
            runCatching { !prefs.onboardingComplete.first() }
                .onSuccess { needsOnboarding = it }
            _ready.value = true
        }
    }
}
