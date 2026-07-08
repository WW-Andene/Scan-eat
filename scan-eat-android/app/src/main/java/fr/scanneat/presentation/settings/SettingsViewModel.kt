package fr.scanneat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {
    val apiKey    = prefs.groqApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mode      = prefs.apiMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiMode.DIRECT)
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val language  = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val theme     = prefs.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "oled")

    fun saveApiKey(key: String)    = viewModelScope.launch { prefs.setGroqApiKey(key.trim()) }
    fun setMode(m: ApiMode)        = viewModelScope.launch { prefs.setApiMode(m) }
    fun saveServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url.trim()) }
    fun setLanguage(lang: String)  = viewModelScope.launch { prefs.setLanguage(lang) }
    fun setTheme(t: String)        = viewModelScope.launch { prefs.setTheme(t) }
}
