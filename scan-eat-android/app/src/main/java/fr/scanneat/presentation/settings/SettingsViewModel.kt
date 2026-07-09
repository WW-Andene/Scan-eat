package fr.scanneat.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
    val groqModel = prefs.groqModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mode      = prefs.apiMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiMode.DIRECT)
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val language  = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val theme     = prefs.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "oled")
    val dyslexicFont   = prefs.dyslexicFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val colorblindMode = prefs.colorblindMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")

    private val _savedField = MutableStateFlow<String?>(null)
    /** Which field was just saved — SettingsScreen shows a brief confirmation, then clears it. */
    val savedField: StateFlow<String?> = _savedField.asStateFlow()

    fun saveApiKey(key: String) = viewModelScope.launch {
        prefs.setGroqApiKey(key.trim()); _savedField.value = "apiKey"
    }
    fun saveGroqModel(model: String) = viewModelScope.launch {
        prefs.setGroqModel(model.trim()); _savedField.value = "groqModel"
    }
    fun setMode(m: ApiMode)        = viewModelScope.launch { prefs.setApiMode(m) }
    fun saveServerUrl(url: String) = viewModelScope.launch {
        prefs.setServerUrl(url.trim()); _savedField.value = "serverUrl"
    }
    fun clearSavedField() { _savedField.value = null }
    fun setLanguage(lang: String) {
        // Drives both the OCR prompt language (persisted) and the actual app UI locale.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        viewModelScope.launch { prefs.setLanguage(lang) }
    }
    fun setTheme(t: String)        = viewModelScope.launch { prefs.setTheme(t) }
    fun setDyslexicFont(v: Boolean)     = viewModelScope.launch { prefs.setDyslexicFont(v) }
    fun setColorblindMode(mode: String) = viewModelScope.launch { prefs.setColorblindMode(mode) }
}
