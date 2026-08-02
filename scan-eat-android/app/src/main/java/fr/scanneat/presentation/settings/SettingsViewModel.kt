package fr.scanneat.presentation.settings

import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.backup.BackupImportError
import fr.scanneat.data.backup.BackupMetadata
import fr.scanneat.data.backup.BackupSummary
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.backup.BackupRepository
import fr.scanneat.data.repository.backup.CsvExportRepository
import fr.scanneat.data.repository.backup.PdfReportRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HealthConnectAvailability
import fr.scanneat.data.repository.health.HealthConnectRepository
import fr.scanneat.presentation.common.ActionFailureViewModel
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
     *  before confirmImport() actually overwrites local data with this file's contents.
     *  [passphrase] carries through from NeedsPassphrase so confirmImport() can decrypt
     *  [json] again for the real import - it's still the encrypted envelope here, not
     *  the decrypted plaintext, since peekMetadata() only ever returns parsed metadata. */
    data class ImportPreview(val json: String, val metadata: BackupMetadata, val passphrase: String? = null) : BackupUiState()
    /** The picked file is passphrase-encrypted (BackupPassphraseCipher) - the screen
     *  prompts for one before peekMetadata()/importFromJson() can even parse it.
     *  [wrongPassphrase] is true after a submitted passphrase failed to decrypt it. */
    data class NeedsPassphrase(val json: String, val wrongPassphrase: Boolean = false) : BackupUiState()
    data class ImportSuccess(val summary: BackupSummary) : BackupUiState()
    data class Error(val messageKey: BackupErrorKey) : BackupUiState()
    /** PDF report generated and ready — the screen writes it to a user-picked URI via PdfDocument.writeTo(), then closes it. */
    data class PdfExportReady(val document: android.graphics.pdf.PdfDocument) : BackupUiState()
}

/** Maps to a stringResource in the screen — keeps user-facing copy out of the ViewModel. */
enum class BackupErrorKey { UNSUPPORTED_VERSION, MALFORMED, IO }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val backupRepository: BackupRepository,
    private val csvExportRepository: CsvExportRepository,
    private val pdfReportRepository: PdfReportRepository,
    private val healthConnect: HealthConnectRepository,
    private val fastingRepo: FastingRepository,
    @ApplicationContext private val context: Context,
) : ActionFailureViewModel() {
    val apiKey    = prefs.groqApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val cerebrasApiKey = prefs.cerebrasApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mode      = prefs.apiMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiMode.DIRECT)
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val language  = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val theme     = prefs.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "oled")
    val dyslexicFont   = prefs.dyslexicFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val colorblindMode = prefs.colorblindMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")
    // Was only reachable from Profile despite being an app-wide preference also
    // consumed by the Weight tab and Biolism's body-measurement fields — a user
    // expecting a units setting under Réglages (where every other display
    // preference lives) wouldn't find it there.
    val useImperialWeight = prefs.useImperialWeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val biolismAdvancedView = prefs.biolismAdvancedView.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val animatedBackground = prefs.animatedBackground.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _savedField = MutableStateFlow<String?>(null)
    /** Which field was just saved — SettingsScreen shows a brief confirmation, then clears it. */
    val savedField: StateFlow<String?> = _savedField.asStateFlow()

    // saveApiKey/saveCerebrasApiKey/saveServerUrl below wrote to DataStore
    // completely unguarded - the same "one code path missing a check its
    // sibling has" pattern already fixed elsewhere this loop (DataViewModel.
    // saveManualHR/deleteSession, ProfileViewModel.save). A DataStore I/O
    // failure (disk full, corrupt prefs file) here would crash this
    // ViewModel's coroutine and _savedField would simply never flip, leaving
    // the user staring at an unsaved field with zero feedback that Save did
    // nothing, instead of surfacing a recoverable error.
    // actionFailed/clearActionFailed()/guardedSuspend now come from
    // ActionFailureViewModel (see that file's doc comment) instead of being
    // redefined here.
    private fun saveField(fieldKey: String, write: suspend () -> Unit) = viewModelScope.launch {
        if (guardedSuspend { write() }) _savedField.value = fieldKey
    }

    fun saveApiKey(key: String) = saveField("apiKey") { prefs.setGroqApiKey(key.trim()) }
    fun saveCerebrasApiKey(key: String) = saveField("cerebrasApiKey") { prefs.setCerebrasApiKey(key.trim()) }
    fun setMode(m: ApiMode)        = viewModelScope.launch { prefs.setApiMode(m) }
    fun saveServerUrl(url: String) = saveField("serverUrl") { prefs.setServerUrl(url.trim()) }
    fun clearSavedField() { _savedField.value = null }
    fun setLanguage(lang: String) {
        // Drives both the OCR prompt language (persisted) and the actual app UI locale.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        viewModelScope.launch { prefs.setLanguage(lang) }
    }
    fun setTheme(t: String)        = viewModelScope.launch { prefs.setTheme(t) }
    fun setDyslexicFont(v: Boolean)     = viewModelScope.launch { prefs.setDyslexicFont(v) }
    fun setColorblindMode(mode: String) = viewModelScope.launch { prefs.setColorblindMode(mode) }
    fun setUseImperialWeight(v: Boolean) = viewModelScope.launch { prefs.setUseImperialWeight(v) }
    fun setBiolismAdvancedView(v: Boolean) = viewModelScope.launch { prefs.setBiolismAdvancedView(v) }
    fun setAnimatedBackground(v: Boolean) = viewModelScope.launch { prefs.setAnimatedBackground(v) }

    // ─────────────────────────────────────────────────────────────────────────
    // Backup export/import
    // ─────────────────────────────────────────────────────────────────────────
    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /**
     * Generates the JSON; the screen writes it to a user-picked URI once state becomes
     * ExportReady. [passphrase], when non-blank, encrypts the file (opt-in - see
     * BackupPassphraseCipher's own doc comment).
     */
    fun prepareExport(passphrase: String? = null) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val json = backupRepository.exportToJson(passphrase?.takeIf { it.isNotBlank() })
            _backupState.value = BackupUiState.ExportReady(json)
        }
    }

    /**
     * Reads just the file's header/summary via peekMetadata() — the screen shows a confirm
     * dialog before [confirmImport] actually overwrites local data. Previously importFromJson
     * applied the file immediately with no way to see what's in it or back out first.
     * An encrypted file (BackupPassphraseCipher) can't even be parsed this far without a
     * passphrase - checked up front via isEncryptedBackup() rather than always trying blind
     * and reading a generic "malformed" error for what's actually "needs a passphrase."
     */
    fun previewImport(json: String) {
        if (backupRepository.isEncryptedBackup(json)) {
            _backupState.value = BackupUiState.NeedsPassphrase(json)
            return
        }
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupRepository.peekMetadata(json).fold(
                onSuccess = { _backupState.value = BackupUiState.ImportPreview(json, it) },
                onFailure = { e -> _backupState.value = BackupUiState.Error(e.toBackupErrorKey()) },
            )
        }
    }

    /** Submits a passphrase for a NeedsPassphrase file - re-runs the same preview step now
     *  that it can actually be decrypted, or re-shows NeedsPassphrase(wrongPassphrase=true). */
    fun previewImportWithPassphrase(json: String, passphrase: String) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupRepository.peekMetadata(json, passphrase).fold(
                onSuccess = { _backupState.value = BackupUiState.ImportPreview(json, it, passphrase) },
                onFailure = { e ->
                    _backupState.value = if (e is BackupImportError.WrongPassphrase)
                        BackupUiState.NeedsPassphrase(json, wrongPassphrase = true)
                    else BackupUiState.Error(e.toBackupErrorKey())
                },
            )
        }
    }

    fun confirmImport(json: String, passphrase: String? = null) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            backupRepository.importFromJson(json, passphrase).fold(
                onSuccess = { _backupState.value = BackupUiState.ImportSuccess(it) },
                onFailure = { e -> _backupState.value = BackupUiState.Error(e.toBackupErrorKey()) },
            )
        }
    }

    private fun Throwable.toBackupErrorKey() = when (this) {
        is BackupImportError.UnsupportedVersion -> BackupErrorKey.UNSUPPORTED_VERSION
        is BackupImportError.Malformed          -> BackupErrorKey.MALFORMED
        is BackupImportError.PassphraseRequired,
        is BackupImportError.WrongPassphrase    -> BackupErrorKey.MALFORMED
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
    fun preparePricesCsvExport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch { _backupState.value = BackupUiState.CsvExportReady(csvExportRepository.exportPricesCsv(), filenamePrefix = "depenses") }
    }

    /** Settings > "Rapport PDF" — builds the multi-page evolution report (see PdfReportRepository's own doc comment) and hands it to the screen to write via SAF, same flow as the JSON/CSV exports above. */
    fun preparePdfReport() {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            runCatching { pdfReportRepository.generate(language.value) }.fold(
                onSuccess = { _backupState.value = BackupUiState.PdfExportReady(it) },
                onFailure = { _backupState.value = BackupUiState.Error(BackupErrorKey.IO) },
            )
        }
    }

    // Both previously called their repo's Room/DataStore write completely unguarded -
    // the same unguarded-write pattern already fixed for saveApiKey/saveServerUrl above
    // (see this file's comment near saveField) and for every sibling tracker ViewModel
    // this loop has swept. A write failure here (disk full, corrupt row) crashed the
    // app instead of surfacing as the shared actionFailed snackbar ActionFailureViewModel
    // already provides to this class.
    fun clearScanHistory() = guardedLaunch { backupRepository.clearScanHistory() }

    /** clearHistory() was fully implemented with zero callers — FastingScreen shows the full
     *  history/streak but had no way to reset it. Mirrors clearScanHistory()'s reset entry point. */
    fun clearFastingHistory() = guardedLaunch { fastingRepo.clearHistory() }

    /**
     * Full wipe — the exact same OS-level operation as Settings > App > Clear
     * Data, exposed in-app instead of sending users out to system settings
     * (which is all the reset dialog previously offered for a full erase).
     * One call handles every Room table, every DataStore key, and any cached
     * file at once; hand-enumerating each repository's own clear function
     * the way clearScanHistory/clearFastingHistory do would silently miss
     * whatever gets added next without a matching update here. Available
     * since API 19, well under this app's minSdk 26, so no version gate is
     * needed. Kills and restarts the process — there's nothing to do after
     * calling this, the app relaunches into onboarding on its own.
     */
    fun eraseAllData() {
        context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData()
    }

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
