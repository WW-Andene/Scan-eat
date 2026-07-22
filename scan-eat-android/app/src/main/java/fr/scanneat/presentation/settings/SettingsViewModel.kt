package fr.scanneat.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.backup.BackupImportError
import fr.scanneat.data.backup.BackupMetadata
import fr.scanneat.data.backup.BackupSummary
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.backup.BackupRepository
import fr.scanneat.data.repository.backup.CsvExportRepository
import fr.scanneat.data.repository.health.FastingRepository
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
    /** CSV export ready (diary or Biolism sessions) — written via Storage Access Framework like JSON. filenamePrefix picks the suggested filename. */
    data class CsvExportReady(val csv: String, val filenamePrefix: String = "journal") : BackupUiState()
    /** peekMetadata() succeeded — the screen shows a confirm dialog ("taken on X, N items")
     *  before confirmImport() actually overwrites local data with this file's contents. */
    data class ImportPreview(val json: String, val metadata: BackupMetadata) : BackupUiState()
    data class ImportSuccess(val summary: BackupSummary) : BackupUiState()
    data class Error(val messageKey: BackupErrorKey) : BackupUiState()
}

/** Maps to a stringResource in the screen — keeps user-facing copy out of the ViewModel. */
enum class BackupErrorKey { UNSUPPORTED_VERSION, MALFORMED, IO }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val backupRepository: BackupRepository,
    private val csvExportRepository: CsvExportRepository,
    private val healthConnect: HealthConnectRepository,
    private val fastingRepo: FastingRepository,
) : ViewModel() {
    val apiKey    = prefs.groqApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val cerebrasApiKey = prefs.cerebrasApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mode      = prefs.apiMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiMode.DIRECT)
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val language  = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val theme     = prefs.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "oled")
    val backgroundTheme = prefs.backgroundTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")
    val dyslexicFont   = prefs.dyslexicFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val colorblindMode = prefs.colorblindMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    // Was only reachable from Profile despite being an app-wide preference also
    // consumed by the Weight tab and Biolism's body-measurement fields — a user
    // expecting a units setting under Réglages (where every other display
    // preference lives) wouldn't find it there.
    val useImperialWeight = prefs.useImperialWeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
    fun setBackgroundTheme(t: String) = viewModelScope.launch { prefs.setBackgroundTheme(t) }
    fun setDyslexicFont(v: Boolean)     = viewModelScope.launch { prefs.setDyslexicFont(v) }
    fun setColorblindMode(mode: String) = viewModelScope.launch { prefs.setColorblindMode(mode) }
    fun setUseImperialWeight(v: Boolean) = viewModelScope.launch { prefs.setUseImperialWeight(v) }

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

    /** Reads just the file's header/summary via peekMetadata() — the screen shows a confirm
     *  dialog before [confirmImport] actually overwrites local data. Previously importFromJson
     *  applied the file immediately with no way to see what's in it or back out first. */
    fun previewImport(json: String) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupRepository.peekMetadata(json).fold(
                onSuccess = { _backupState.value = BackupUiState.ImportPreview(json, it) },
                onFailure = { e -> _backupState.value = BackupUiState.Error(e.toBackupErrorKey()) },
            )
        }
    }

    fun confirmImport(json: String) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupRepository.importFromJson(json).fold(
                onSuccess = { _backupState.value = BackupUiState.ImportSuccess(it) },
                onFailure = { e -> _backupState.value = BackupUiState.Error(e.toBackupErrorKey()) },
            )
        }
    }

    private fun Throwable.toBackupErrorKey() = when (this) {
        is BackupImportError.UnsupportedVersion -> BackupErrorKey.UNSUPPORTED_VERSION
        is BackupImportError.Malformed          -> BackupErrorKey.MALFORMED
        else                                    -> BackupErrorKey.IO
    }

    fun prepareCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val csv = csvExportRepository.exportDiaryCsv()
            _backupState.value = BackupUiState.CsvExportReady(csv)
        }
    }

    /** Same CSV export pattern as [prepareCsvExport], for Biolism workout sessions. */
    fun prepareBiolismCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val csv = csvExportRepository.exportBiolismSessionsCsv()
            _backupState.value = BackupUiState.CsvExportReady(csv, filenamePrefix = "biolism")
        }
    }

    // Diary/Biolism previously were the only two trackers with a CSV export -
    // Weight/Activity/Hydration/Medication/Fasting each already expose an
    // equivalent JSON-backup dataset with no lightweight spreadsheet path.
    fun prepareWeightCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch { _backupState.value = BackupUiState.CsvExportReady(csvExportRepository.exportWeightCsv(), filenamePrefix = "poids") }
    }
    fun prepareActivityCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch { _backupState.value = BackupUiState.CsvExportReady(csvExportRepository.exportActivityCsv(), filenamePrefix = "activite") }
    }
    fun prepareHydrationCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch { _backupState.value = BackupUiState.CsvExportReady(csvExportRepository.exportHydrationCsv(), filenamePrefix = "hydratation") }
    }
    fun prepareMedicationCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch { _backupState.value = BackupUiState.CsvExportReady(csvExportRepository.exportMedicationCsv(), filenamePrefix = "traitement") }
    }
    fun prepareFastingCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch { _backupState.value = BackupUiState.CsvExportReady(csvExportRepository.exportFastingCsv(), filenamePrefix = "jeune") }
    }

    fun clearScanHistory() = viewModelScope.launch { backupRepository.clearScanHistory() }

    /** clearHistory() was fully implemented with zero callers — FastingScreen shows the full
     *  history/streak but had no way to reset it. Mirrors clearScanHistory()'s reset entry point. */
    fun clearFastingHistory() = viewModelScope.launch { fastingRepo.clearHistory() }

    /** Also used for import read/size failures, not just export writes — name says "IO" for that reason. */
    fun reportBackupIoFailed() { _backupState.value = BackupUiState.Error(BackupErrorKey.IO) }
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
