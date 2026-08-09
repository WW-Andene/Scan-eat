package fr.scanneat.presentation.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * User-requested: long-press a bottom-nav tab and drag it onto another one to
 * swap their positions. MainShell previously had no ViewModel of its own —
 * added just to back this persisted custom order, the same
 * UserPreferences-backed pattern every other cross-restart setting uses.
 */
@HiltViewModel
class MainShellViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {
    val navTabOrder: StateFlow<String> = prefs.navTabOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setNavTabOrder(csv: String) {
        viewModelScope.launch { runCatching { prefs.setNavTabOrder(csv) } }
    }
}
