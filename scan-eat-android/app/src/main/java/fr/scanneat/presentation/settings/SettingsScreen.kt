package fr.scanneat.presentation.settings

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
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
import fr.scanneat.presentation.settings.components.BiolismDisplaySection
import fr.scanneat.presentation.settings.components.CerebrasKeySection
import fr.scanneat.presentation.settings.components.ColorSection
import fr.scanneat.presentation.settings.components.CurrencySection
import fr.scanneat.presentation.settings.components.DataResetSection
import fr.scanneat.presentation.settings.components.GroqKeySection
import fr.scanneat.presentation.settings.components.HealthConnectSection
import fr.scanneat.presentation.settings.components.LanguageSection
import fr.scanneat.presentation.settings.components.LegalSection
import fr.scanneat.presentation.settings.components.OssLicensesDialog
import fr.scanneat.presentation.settings.components.PremiumSection
import fr.scanneat.presentation.settings.components.ProfileSection
import fr.scanneat.presentation.settings.components.RemindersSection
import fr.scanneat.presentation.settings.components.ResetConfirmDialog
import fr.scanneat.presentation.settings.components.ResetTarget
import fr.scanneat.presentation.settings.components.ServerUrlSection
import fr.scanneat.presentation.settings.components.SettingsSection
import fr.scanneat.presentation.settings.components.ThemeSection
import fr.scanneat.presentation.settings.components.UnitsSection
import fr.scanneat.presentation.settings.components.rememberBackupImportLauncher
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

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
    val colorAccent = viewModel.colorAccent.collectAsStateWithLifecycle()
    val dyslexicFont   = viewModel.dyslexicFont.collectAsStateWithLifecycle()
    val colorblindMode = viewModel.colorblindMode.collectAsStateWithLifecycle()
    val useImperialWeight = viewModel.useImperialWeight.collectAsStateWithLifecycle()
    val currencySymbol = viewModel.currencySymbol.collectAsStateWithLifecycle()
    val biolismAdvancedView = viewModel.biolismAdvancedView.collectAsStateWithLifecycle()
    val animatedBackground = viewModel.animatedBackground.collectAsStateWithLifecycle()
    val isPremium = viewModel.isPremium.collectAsStateWithLifecycle()
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

    // saveApiKey/saveCerebrasApiKey/saveServerUrl previously wrote to DataStore
    // completely unguarded - see SettingsViewModel.actionFailed's own comment.
    // A failed write now surfaces here as a one-shot snackbar instead of
    // silently leaving the field unsaved with no feedback.
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val actionFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            viewModel.clearActionFailed()
        }
    }
    // AboutSection's "no crash log to share" case - previously a bare Toast, no
    // reliable TalkBack announcement, unlike everything else on this screen.
    val noCrashLogMessage = stringResource(R.string.settings_about_no_crash_log)

    // Sets up the JSON/CSV/PDF export launchers + the LaunchedEffect that fires
    // the right one when backupState is ready, returning only the JSON import
    // picker (the one BackupSection's onImport actually calls) - see
    // BackupLaunchers.kt's own doc comment.
    val importLauncher = rememberBackupImportLauncher(viewModel, backupState)
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
                IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) }
            }
        },
        hasNavigationIcon = !isTabRoot,
        showBottomNavClearance = isTabRoot,
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
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

            // ---- Premium — everything else is free; this gates Biolism (metabolism)
            // and AI photo scanning only. Placed above the AI key sections below since
            // it's the reason those are locked out for a non-Premium user. ----
            item { PremiumSection(isPremium.value, onSetPremium = viewModel::setIsPremium) }

            // ---- API Mode ----
            item { ApiModeSection(mode.value, onModeChange = viewModel::setMode) }

            // ---- Groq/Cerebras API keys — AI photo scanning is Premium-gated. Without
            // a configured key, OcrParser's callers already fall back to "add a photo to
            // continue" / barcode-only scoring (see ScanOffLookup.scoreDirectBarcode) -
            // gating key *entry* here is enough to gate the whole AI path, no change
            // needed to the scan flow itself. ----
            if (mode.value == ApiMode.DIRECT) {
                if (isPremium.value) {
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
                } else {
                    item {
                        SettingsSection(stringResource(R.string.settings_section_groq_key), icon = null) {
                            Text(
                                stringResource(R.string.settings_premium_required_ai_scan),
                                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f),
                            )
                        }
                    }
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
            item {
                ThemeSection(
                    theme.value, onThemeChange = viewModel::setTheme,
                    animatedBackground.value, onAnimatedBackgroundChange = viewModel::setAnimatedBackground,
                )
            }

            // User-requested: color accent is independent from the theme's own
            // brightness/contrast (see ThemeSection's own doc comment) - its own
            // section rather than folded into the row above.
            item { ColorSection(colorAccent.value, onColorAccentChange = viewModel::setColorAccent) }

            // ---- Units — was only reachable from Profile despite being an app-wide
            // preference also consumed by Weight/Biolism; users looking for it under
            // Réglages (where every other display preference lives) found nothing. ----
            item { UnitsSection(useImperialWeight.value, onChange = viewModel::setUseImperialWeight) }
            item { CurrencySection(currencySymbol.value, onChange = viewModel::setCurrencySymbol) }

            // ---- Biolism display depth (R&D §X.0) ----
            item { BiolismDisplaySection(biolismAdvancedView.value, onChange = viewModel::setBiolismAdvancedView) }

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
                    onPreparePricesCsvExport = { viewModel.preparePricesCsvExport() },
                    onPrepareCustomFoodsCsvExport = { viewModel.prepareCustomFoodsCsvExport() },
                    onPrepareMealTemplatesCsvExport = { viewModel.prepareMealTemplatesCsvExport() },
                    onPrepareRecipesCsvExport = { viewModel.prepareRecipesCsvExport() },
                    onPrepareScanHistoryCsvExport = { viewModel.prepareScanHistoryCsvExport() },
                    onPrepareMedicationsCsvExport = { viewModel.prepareMedicationsCsvExport() },
                    onPrepareReport = { viewModel.preparePdfReport() },
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
            item {
                AboutSection(
                    onShowLicenses = { showLicensesDialog = true },
                    onNoCrashLog = { coroutineScope.launch { snackbarHostState.showSnackbar(noCrashLogMessage) } },
                )
            }

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
