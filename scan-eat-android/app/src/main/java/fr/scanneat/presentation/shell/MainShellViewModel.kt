package fr.scanneat.presentation.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes the app-wide decorative background theme (see
 * SettingsScreen.kt's BackgroundThemeSection / OceanFoamBackground.kt) to
 * MainShell's root Box, the single place every screen's background is
 * drawn behind — this is the one integration point a background theme
 * needs to actually apply app-wide without touching every individual
 * screen's own ambientGloom() call site.
 */
@HiltViewModel
class MainShellViewModel @Inject constructor(
    prefs: UserPreferences,
) : ViewModel() {
    val backgroundTheme = prefs.backgroundTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")
}
