package fr.scanneat.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.backup.BackupImportError
import fr.scanneat.data.backup.BackupRepository
import fr.scanneat.data.backup.BackupSummary
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.HealthConnectAvailability
import fr.scanneat.data.repository.health.HealthConnectRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Working : BackupUiState()
    /** JSON generated and ready — the screen still needs to write it to a user-picked URI. */
    data class ExportReady(val json: String) : BackupUiState()
    /** CSV diary export ready — written via Storage Access Framework like JSON. */
    data class CsvExportReady(val csv: String) : BackupUiState()
    data class ImportSuccess(val summary: BackupSummary) : BackupUiState()
    data class Error(val messageKey: BackupErrorKey) : BackupUiState()
}

/** Maps to a stringResource in the screen — keeps user-facing copy out of the ViewModel. */
enum class BackupErrorKey { UNSUPPORTED_VERSION, MALFORMED, IO }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val backupRepository: BackupRepository,
    private val healthConnect: HealthConnectRepository,
) : ViewModel() {
    val apiKey    = prefs.groqApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val cerebrasApiKey = prefs.cerebrasApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
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
    fun saveCerebrasApiKey(key: String) = viewModelScope.launch {
        prefs.setCerebrasApiKey(key.trim()); _savedField.value = "cerebrasApiKey"
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

    // ─────────────────────────────────────────────────────────────────────────
    // Backup export/import
    // ─────────────────────────────────────────────────────────────────────────
    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** Generates the JSON; the screen writes it to a user-picked URI once state becomes ExportReady. */
    fun prepareExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val json = backupRepository.exportToJson()
            _backupState.value = BackupUiState.ExportReady(json)
        }
    }

    fun importFromJson(json: String) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupRepository.importFromJson(json).fold(
                onSuccess = { _backupState.value = BackupUiState.ImportSuccess(it) },
                onFailure = { e ->
                    _backupState.value = BackupUiState.Error(
                        when (e) {
                            is BackupImportError.UnsupportedVersion -> BackupErrorKey.UNSUPPORTED_VERSION
                            is BackupImportError.Malformed          -> BackupErrorKey.MALFORMED
                            else                                    -> BackupErrorKey.IO
                        },
                    )
                },
            )
        }
    }

    fun prepareCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val csv = backupRepository.exportDiaryCsv()
            _backupState.value = BackupUiState.CsvExportReady(csv)
        }
    }

    fun clearScanHistory() = viewModelScope.launch { backupRepository.clearScanHistory() }

    fun reportExportWriteFailed() { _backupState.value = BackupUiState.Error(BackupErrorKey.IO) }
    fun clearBackupState() { _backupState.value = BackupUiState.Idle }

    /** Total scan history + diary entry counts — shown in the backup section so users
     *  know what's stored before they export or reset. Updates reactively after each new entry. */
    val dataStats: StateFlow<Pair<Int, Int>> = backupRepository.observeDataStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    // ─────────────────────────────────────────────────────────────────────────
    // Health Connect
    // ─────────────────────────────────────────────────────────────────────────
    val healthConnectPermissions: Set<String> get() = HealthConnectRepository.PERMISSIONS

    private val _healthConnectAvailability = MutableStateFlow(HealthConnectAvailability.UNSUPPORTED)
    val healthConnectAvailability: StateFlow<HealthConnectAvailability> = _healthConnectAvailability.asStateFlow()

    private val _healthConnectConnected = MutableStateFlow(false)
    val healthConnectConnected: StateFlow<Boolean> = _healthConnectConnected.asStateFlow()

    /** Call on screen entry and after returning from the permission dialog — Health Connect state isn't observable as a Flow. */
    fun refreshHealthConnectStatus() {
        _healthConnectAvailability.value = healthConnect.availability()
        viewModelScope.launch { _healthConnectConnected.value = healthConnect.hasPermissions() }
    }
}
