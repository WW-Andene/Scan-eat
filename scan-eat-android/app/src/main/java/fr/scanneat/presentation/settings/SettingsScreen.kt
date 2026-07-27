package fr.scanneat.presentation.settings

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.presentation.settings.components.AboutSection
import fr.scanneat.presentation.settings.components.AccessibilitySection
import fr.scanneat.presentation.settings.components.ApiModeSection
import fr.scanneat.presentation.settings.components.BackupSection
import fr.scanneat.presentation.settings.components.CerebrasKeySection
import fr.scanneat.presentation.settings.components.DataResetSection
import fr.scanneat.presentation.settings.components.GroqKeySection
import fr.scanneat.presentation.settings.components.HealthConnectSection
import fr.scanneat.presentation.settings.components.LanguageSection
import fr.scanneat.presentation.settings.components.LegalSection
import fr.scanneat.presentation.settings.components.MAX_BACKUP_IMPORT_BYTES
import fr.scanneat.presentation.settings.components.OssLicensesDialog
import fr.scanneat.presentation.settings.components.ProfileSection
import fr.scanneat.presentation.settings.components.RemindersSection
import fr.scanneat.presentation.settings.components.ResetConfirmDialog
import fr.scanneat.presentation.settings.components.ResetTarget
import fr.scanneat.presentation.settings.components.ServerUrlSection
import fr.scanneat.presentation.settings.components.ThemeSection
import fr.scanneat.presentation.settings.components.UnitsSection
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
    onOpenProfile: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
) {
    val apiKey    = viewModel.apiKey.collectAsStateWithLifecycle()
    val cerebrasApiKey = viewModel.cerebrasApiKey.collectAsStateWithLifecycle()
    val mode      = viewModel.mode.collectAsStateWithLifecycle()
    val serverUrl = viewModel.serverUrl.collectAsStateWithLifecycle()
    val language  = viewModel.language.collectAsStateWithLifecycle()
    val theme     = viewModel.theme.collectAsStateWithLifecycle()
    val dyslexicFont   = viewModel.dyslexicFont.collectAsStateWithLifecycle()
    val colorblindMode = viewModel.colorblindMode.collectAsStateWithLifecycle()
    val useImperialWeight = viewModel.useImperialWeight.collectAsStateWithLifecycle()
    val savedField = viewModel.savedField.collectAsStateWithLifecycle()
    val backupState = viewModel.backupState.collectAsStateWithLifecycle()
    val healthConnectAvailability = viewModel.healthConnectAvailability.collectAsStateWithLifecycle()
    val healthConnectConnected = viewModel.healthConnectConnected.collectAsStateWithLifecycle()
    val dataStats = viewModel.dataStats.collectAsStateWithLifecycle()

    var keyVisible  by remember { mutableStateOf(false) }
    // rememberSaveable, not remember - these hold a typed/pasted-but-not-yet-saved API
    // key/URL, exactly the kind of input a user carefully pastes from another app and
    // would be frustrated to retype. A process death before tapping Save (backgrounding
    // to copy the key is the common flow) previously discarded it silently on return.
    var localKey    by rememberSaveable(apiKey.value)    { mutableStateOf(apiKey.value) }
    var localUrl    by rememberSaveable(serverUrl.value) { mutableStateOf(serverUrl.value) }
    var cerebrasKeyVisible by remember { mutableStateOf(false) }
    var localCerebrasKey   by rememberSaveable(cerebrasApiKey.value) { mutableStateOf(cerebrasApiKey.value) }

    LaunchedEffect(savedField.value) {
        if (savedField.value != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSavedField()
        }
    }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val state = backupState.value
        when {
            uri == null -> viewModel.clearBackupState()   // user cancelled the picker - not a failure
            state is BackupUiState.ExportReady -> {
                // openOutputStream() can return null (stale/invalidated SAF document) - the
                // previous `runCatching { stream?.use{...} }.isSuccess` treated a null stream
                // as success (the safe-call just short-circuits to null, no exception thrown),
                // so this silently reported a write that never happened.
                val stream = context.contentResolver.openOutputStream(uri)
                val wrote = stream != null && runCatching { stream.use { it.write(state.json.toByteArray()) } }.isSuccess
                if (wrote) viewModel.clearBackupState() else viewModel.reportBackupIoFailed()
            }
            // A destination was picked but the export JSON isn't there anymore - most
            // commonly the process died while this system picker (a separate task/
            // activity, routinely killed under memory pressure) was open, recreating
            // the ViewModel back to Idle. The SAF picker already materializes an empty
            // file at the chosen URI regardless of what happens next, so silently
            // clearing state here would leave the user believing they have a real
            // backup when nothing was ever written to it.
            else -> viewModel.reportBackupIoFailed()
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val state = backupState.value
        when {
            uri == null -> viewModel.clearBackupState()
            state is BackupUiState.CsvExportReady -> {
                val stream = context.contentResolver.openOutputStream(uri)
                val wrote = stream != null && runCatching { stream.use { it.write(state.csv.toByteArray()) } }.isSuccess
                if (wrote) viewModel.clearBackupState() else viewModel.reportBackupIoFailed()
            }
            else -> viewModel.reportBackupIoFailed()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // OpenDocument() lets the user pick ANY file, not just one this app
            // exported - readText() with no cap would load an arbitrarily large
            // file fully into memory before Moshi even gets a chance to reject it
            // as malformed, risking an OOM on a huge or mis-picked file.
            val size = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull()
            if (size != null && size > MAX_BACKUP_IMPORT_BYTES) {
                viewModel.reportBackupIoFailed()
            } else {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (json != null) viewModel.previewImport(json) else viewModel.reportBackupIoFailed()
            }
        }
    }
    // The JSON is generated in the ViewModel (testable, no Android dependency); once it's
    // ready this launches the system "save file" picker, which needs an Activity context
    // the ViewModel doesn't have.
    LaunchedEffect(backupState.value) {
        val state = backupState.value
        if (state is BackupUiState.ExportReady) {
            exportLauncher.launch("scaneat-backup-${LocalDate.now()}.json")
        } else if (state is BackupUiState.CsvExportReady) {
            csvExportLauncher.launch("scaneat-${state.filenamePrefix}-${LocalDate.now()}.csv")
        }
    }
    var showResetDialog by remember { mutableStateOf(false) }
    // Which destructive action (if any) is on its second-confirmation step - a
    // plain Boolean couldn't distinguish "confirming scans" from "confirming
    // fasting history" once a second reset target was added alongside scans.
    var pendingReset by remember { mutableStateOf<ResetTarget?>(null) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    val healthConnectLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthConnectStatus()
    }
    LaunchedEffect(Unit) { viewModel.refreshHealthConnectStatus() }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.settings_title), color = OnBackground) },
        navigationIcon = {
            if (!isTabRoot) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) }
            }
        },
        showBottomNavClearance = isTabRoot,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Violet)
                .padding(horizontal = 20.dp),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            // ---- Profile — first thing in Réglages, not buried at the bottom ----
            item { ProfileSection(onOpenProfile) }

            // ---- Reminders — MealRemindersCard was only ever embedded inline in
            // Diary and Medication, so there was no way to reach it from Settings
            // even though the underlying reminder system covers meals, hydration,
            // weigh-ins, activity, and fasting targets app-wide. ----
            item { RemindersSection(onOpenReminders) }

            // ---- API Mode ----
            item { ApiModeSection(mode.value, onModeChange = viewModel::setMode) }

            // ---- Groq API key ----
            if (mode.value == ApiMode.DIRECT) {
                item {
                    GroqKeySection(
                        localKey = localKey, onLocalKeyChange = { localKey = it },
                        keyVisible = keyVisible, onToggleVisible = { keyVisible = !keyVisible },
                        saved = savedField.value == "apiKey", onSave = { viewModel.saveApiKey(localKey) },
                    )
                }

                // ---- Cerebras API key — second provider, automatic fallback ----
                // Previously this section let the user pick a specific Groq model by
                // name — but Groq model names get retired/renamed, and there was no
                // way to recover except opening this screen and picking a new one.
                // The app now cycles through models/providers automatically (see
                // OcrParser); the only thing left to configure here is a second
                // provider's key so scanning survives Groq being down entirely.
                item {
                    CerebrasKeySection(
                        localCerebrasKey = localCerebrasKey, onLocalCerebrasKeyChange = { localCerebrasKey = it },
                        cerebrasKeyVisible = cerebrasKeyVisible, onToggleVisible = { cerebrasKeyVisible = !cerebrasKeyVisible },
                        saved = savedField.value == "cerebrasApiKey", onSave = { viewModel.saveCerebrasApiKey(localCerebrasKey) },
                    )
                }
            }

            // ---- Server URL ----
            if (mode.value == ApiMode.SERVER) {
                item {
                    ServerUrlSection(
                        localUrl = localUrl, onLocalUrlChange = { localUrl = it },
                        saved = savedField.value == "serverUrl", onSave = { viewModel.saveServerUrl(localUrl) },
                    )
                }
            }

            // Fix 4: Language toggle
            item { LanguageSection(language.value, onLanguageChange = viewModel::setLanguage) }

            // Fix 4: Theme toggle
            item { ThemeSection(theme.value, onThemeChange = viewModel::setTheme) }

            // ---- Units — was only reachable from Profile despite being an app-wide
            // preference also consumed by Weight/Biolism; users looking for it under
            // Réglages (where every other display preference lives) found nothing. ----
            item { UnitsSection(useImperialWeight.value, onChange = viewModel::setUseImperialWeight) }

            // ---- Accessibility ----
            item {
                AccessibilitySection(
                    dyslexicFont = dyslexicFont.value, onDyslexicFontChange = viewModel::setDyslexicFont,
                    colorblindMode = colorblindMode.value, onColorblindModeChange = viewModel::setColorblindMode,
                )
            }

            // Backup — local export/import, no cloud account required
            item {
                BackupSection(
                    backupState = backupState.value,
                    dataStats = dataStats.value,
                    language = language.value,
                    onExport = { passphrase -> viewModel.prepareExport(passphrase) },
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                    onClearBackupState = { viewModel.clearBackupState() },
                    onConfirmImport = { json, passphrase -> viewModel.confirmImport(json, passphrase) },
                    onSubmitPassphrase = { json, passphrase -> viewModel.previewImportWithPassphrase(json, passphrase) },
                    onPrepareCsvExport = { viewModel.prepareCsvExport() },
                    onPrepareBiolismCsvExport = { viewModel.prepareBiolismCsvExport() },
                    onPrepareWeightCsvExport = { viewModel.prepareWeightCsvExport() },
                    onPrepareActivityCsvExport = { viewModel.prepareActivityCsvExport() },
                    onPrepareHydrationCsvExport = { viewModel.prepareHydrationCsvExport() },
                    onPrepareMedicationCsvExport = { viewModel.prepareMedicationCsvExport() },
                    onPrepareFastingCsvExport = { viewModel.prepareFastingCsvExport() },
                )
            }

            // Data reset section
            item { DataResetSection(onShowResetDialog = { showResetDialog = true }) }

            // Health Connect — platform weight sync
            item {
                HealthConnectSection(
                    availability = healthConnectAvailability.value,
                    connected = healthConnectConnected.value,
                    onConnect = { healthConnectLauncher.launch(viewModel.healthConnectPermissions) },
                )
            }

            // About
            item { AboutSection(onShowLicenses = { showLicensesDialog = true }) }

            // Legal — every claim this app makes (scores, personal adjustments, hints,
            // metabolisme estimates, medication tracking) is a heuristic or a published
            // formula substituted with the user's own numbers, not a medical opinion.
            item { LegalSection() }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showResetDialog) {
        ResetConfirmDialog(
            pendingReset = pendingReset,
            onSetPendingReset = { pendingReset = it },
            onConfirmClearScans = {
                viewModel.clearScanHistory()
                showResetDialog = false
                pendingReset = null
            },
            onConfirmClearFasting = {
                // clearFastingHistory() was fully implemented (FastingRepository.
                // clearHistory()) with zero callers - FastingScreen shows the full
                // history/streak but had no reset control anywhere.
                viewModel.clearFastingHistory()
                showResetDialog = false
                pendingReset = null
            },
            onConfirmClearAll = {
                // No dialog dismissal/state reset here - eraseAllData() kills the
                // process (see its own doc comment), so this composable's own
                // remembered state won't survive to matter anyway.
                viewModel.eraseAllData()
            },
            onDismiss = { showResetDialog = false; pendingReset = null },
        )
    }

    if (showLicensesDialog) {
        OssLicensesDialog(onDismiss = { showLicensesDialog = false })
    }
}
