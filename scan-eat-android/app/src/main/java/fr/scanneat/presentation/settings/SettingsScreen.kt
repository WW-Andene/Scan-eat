package fr.scanneat.presentation.settings

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.BuildConfig
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.repository.health.HealthConnectAvailability
import fr.scanneat.domain.engine.scoring.ENGINE_VERSION
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.settings.components.SaveButtonRow
import fr.scanneat.presentation.settings.components.SettingsSection
import fr.scanneat.presentation.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun DataStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = OnBackground.copy(0.06f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            Icon(icon, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
        }
    }
}

/** Which "Reset data" destructive action is currently mid-confirmation. */
private enum class ResetTarget { SCANS, FASTING }

/** Generous cap for a legitimate backup (thousands of scan/diary rows is still a few MB) — rejects an arbitrarily large/mis-picked file before it's fully loaded into memory. */
private const val MAX_BACKUP_IMPORT_BYTES = 50L * 1024 * 1024

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
    val backgroundTheme = viewModel.backgroundTheme.collectAsStateWithLifecycle()
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

            // Decorative background pattern — separate from ThemeSection above
            // (light/dark/contrast color scheme, unaffected by this).
            item { BackgroundThemeSection(backgroundTheme.value, onThemeChange = viewModel::setBackgroundTheme) }

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
                    onExport = { viewModel.prepareExport() },
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                    onClearBackupState = { viewModel.clearBackupState() },
                    onConfirmImport = { json -> viewModel.confirmImport(json) },
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
            item { AboutSection() }

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
            onDismiss = { showResetDialog = false; pendingReset = null },
        )
    }
}

@Composable
private fun ProfileSection(onOpenProfile: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_profile)) {
        Text(stringResource(R.string.settings_profile_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        ScanEatOutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Person, null, tint = OnBackground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_profile_button), color = OnBackground)
        }
    }
}

@Composable
private fun RemindersSection(onOpenReminders: () -> Unit) {
    SettingsSection(stringResource(R.string.reminders_title)) {
        Text(stringResource(R.string.settings_reminders_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        ScanEatOutlinedButton(
            onClick = onOpenReminders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Notifications, null, tint = OnBackground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_reminders_button), color = OnBackground)
        }
    }
}

@Composable
private fun ApiModeSection(mode: ApiMode, onModeChange: (ApiMode) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_api_mode)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ApiMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick  = { onModeChange(m) },
                    label    = { Text(if (m == ApiMode.DIRECT) stringResource(R.string.settings_mode_direct) else stringResource(R.string.settings_mode_server)) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
        Text(
            if (mode == ApiMode.DIRECT) stringResource(R.string.settings_mode_direct_desc)
            else stringResource(R.string.settings_mode_server_desc),
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
        )
    }
}

@Composable
private fun GroqKeySection(
    localKey: String, onLocalKeyChange: (String) -> Unit,
    keyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_groq_key)) {
        OutlinedTextField(
            value = localKey, onValueChange = onLocalKeyChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_groq_key_placeholder)) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
        )
        Text(stringResource(R.string.onboarding_api_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}

@Composable
private fun CerebrasKeySection(
    localCerebrasKey: String, onLocalCerebrasKeyChange: (String) -> Unit,
    cerebrasKeyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_cerebras_key)) {
        Text(stringResource(R.string.settings_cerebras_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        OutlinedTextField(
            value = localCerebrasKey, onValueChange = onLocalCerebrasKeyChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_cerebras_key_placeholder)) },
            visualTransformation = if (cerebrasKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(if (cerebrasKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
        )
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}

@Composable
private fun ServerUrlSection(localUrl: String, onLocalUrlChange: (String) -> Unit, saved: Boolean, onSave: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_server_url)) {
        OutlinedTextField(
            value = localUrl, onValueChange = onLocalUrlChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_server_url_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true, shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
        )
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}

@Composable
private fun LanguageSection(language: String, onLanguageChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_language)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf("fr" to stringResource(R.string.settings_lang_fr), "en" to stringResource(R.string.settings_lang_en)).forEach { (code, label) ->
                FilterChip(
                    selected = language == code,
                    onClick  = { onLanguageChange(code) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSection(theme: String, onThemeChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_theme)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "oled" to stringResource(R.string.settings_theme_oled),
                "dark" to stringResource(R.string.settings_theme_dark),
                "light" to stringResource(R.string.settings_theme_light),
                "high_contrast" to stringResource(R.string.settings_theme_high_contrast),
                "low_contrast" to stringResource(R.string.settings_theme_low_contrast),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = theme == key,
                    onClick  = { onThemeChange(key) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}

/**
 * Decorative full-bleed background pattern shown behind the app's own flat
 * color (see MainShell's root Box) — distinct from ThemeSection above, which
 * only controls the light/dark/contrast color scheme. "ocean_foam" is the
 * first pattern: a procedural bottom-view wave-foam simulation, see
 * OceanFoamBackground.kt for the full algorithm. Defaults to "default"
 * (today's plain background, unchanged) so nothing looks different unless a
 * user explicitly opts in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundThemeSection(theme: String, onThemeChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_background_theme)) {
        Text(stringResource(R.string.settings_background_theme_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "default" to stringResource(R.string.settings_background_theme_default),
                "ocean_foam" to stringResource(R.string.settings_background_theme_ocean_foam),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = theme == key,
                    onClick  = { onThemeChange(key) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
        // Live preview of the algorithm itself (not a static screenshot) so
        // this setting shows what it actually does before committing to it.
        if (theme == "ocean_foam") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(CardRadius.CONTROL))
                    .oceanFoamBackground(),
            )
        }
    }
}

@Composable
private fun UnitsSection(useImperialWeight: Boolean, onChange: (Boolean) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_units)) {
        Text(stringResource(R.string.settings_units_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(false to stringResource(R.string.bioprofile_unit_metric), true to stringResource(R.string.bioprofile_unit_imperial)).forEach { (imperial, label) ->
                FilterChip(
                    selected = useImperialWeight == imperial,
                    onClick  = { onChange(imperial) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessibilitySection(
    dyslexicFont: Boolean, onDyslexicFontChange: (Boolean) -> Unit,
    colorblindMode: String, onColorblindModeChange: (String) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_accessibility)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_dyslexic_font), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Text(stringResource(R.string.settings_dyslexic_font_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            Switch(
                checked = dyslexicFont,
                onCheckedChange = onDyslexicFontChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            )
        }
        Spacer(Modifier.height(Spacing.XS))
        Text(stringResource(R.string.settings_colorblind_mode), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "none" to stringResource(R.string.settings_colorblind_none),
                "deuteranopia" to stringResource(R.string.settings_colorblind_deuteranopia),
                "protanopia" to stringResource(R.string.settings_colorblind_protanopia),
                "tritanopia" to stringResource(R.string.settings_colorblind_tritanopia),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = colorblindMode == key,
                    onClick  = { onColorblindModeChange(key) },
                    label    = { Text(label, maxLines = 1) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Live preview so the effect of the chosen mode is visible right here,
        // not just later on a scan result.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Grade.entries.forEach { grade ->
                val c = gradeColor(grade)
                Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.2f)) {
                    Text(
                        grade.label,
                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupSection(
    backupState: BackupUiState,
    dataStats: Pair<Int, Int>,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearBackupState: () -> Unit,
    onConfirmImport: (String) -> Unit,
    onPrepareCsvExport: () -> Unit,
    onPrepareBiolismCsvExport: () -> Unit,
    onPrepareWeightCsvExport: () -> Unit,
    onPrepareActivityCsvExport: () -> Unit,
    onPrepareHydrationCsvExport: () -> Unit,
    onPrepareMedicationCsvExport: () -> Unit,
    onPrepareFastingCsvExport: () -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_backup)) {
        Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        val working = backupState is BackupUiState.Working
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ScanEatPrimaryButton(
                onClick = onExport,
                enabled = !working,
            ) {
                // No explicit tint - defaults to LocalContentColor, which
                // ScanEatPrimaryButton now correctly dims when disabled (was
                // previously hardcoded black regardless of enabled state,
                // compounding the same bug fixed in ScanEatButton.kt).
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_backup_export_button))
            }
            ScanEatOutlinedButton(
                onClick = onImport,
                enabled = !working,
            ) {
                Icon(Icons.Default.Download, null, tint = OnBackground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_backup_import_button), color = OnBackground)
            }
        }
        when (val s = backupState) {
            is BackupUiState.Working -> Text(stringResource(R.string.settings_backup_working), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            is BackupUiState.ImportSuccess -> Text(stringResource(R.string.settings_backup_import_success, s.summary.total), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
            is BackupUiState.Error -> ErrorBanner(
                message   = stringResource(
                    when (s.messageKey) {
                        BackupErrorKey.UNSUPPORTED_VERSION -> R.string.settings_backup_error_unsupported_version
                        BackupErrorKey.MALFORMED           -> R.string.settings_backup_error_malformed
                        BackupErrorKey.IO                  -> R.string.settings_backup_error_io
                    },
                ),
                onDismiss = onClearBackupState,
            )
            is BackupUiState.ImportPreview -> {
                val dateFmt = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
                val exportedDate = Instant.ofEpochMilli(s.metadata.exportedAtMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
                AlertDialog(
                    onDismissRequest = onClearBackupState,
                    title = { Text(stringResource(R.string.settings_backup_import_confirm_title), color = OnBackground) },
                    text = {
                        Text(
                            stringResource(R.string.settings_backup_import_confirm_body, exportedDate, s.metadata.appVersionName, s.metadata.summary.total),
                            color = OnBackground.copy(0.8f),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { onConfirmImport(s.json) }) {
                            Text(stringResource(R.string.settings_backup_import_confirm_button), color = AccentCoral)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onClearBackupState) {
                            Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
                        }
                    },
                    containerColor = SurfaceVariant,
                )
            }
            else -> {}
        }
        // CSV diary export — spreadsheet-friendly complement to the JSON backup
        HorizontalDivider(color = OnBackground.copy(0.08f))
        ScanEatOutlinedButton(
            onClick = onPrepareCsvExport,
            enabled = backupState !is BackupUiState.Working,
        ) {
            Icon(Icons.Default.TableChart, null, tint = OnBackground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_csv_export_button), color = OnBackground)
        }
        // CSV Biolism export — same spreadsheet-friendly complement, for workout
        // sessions, which previously only ever left the app via the full JSON backup.
        ScanEatOutlinedButton(
            onClick = onPrepareBiolismCsvExport,
            enabled = backupState !is BackupUiState.Working,
        ) {
            Icon(Icons.Default.TableChart, null, tint = OnBackground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_biolism_csv_export_button), color = OnBackground)
        }
        // Weight/Activity/Hydration/Medication/Fasting previously had no CSV export at
        // all (only Diary and Biolism did) - grouped behind one overflow menu rather
        // than 5 more stacked full-width buttons, same MoreVert/DropdownMenu pattern
        // already used to consolidate a long action list elsewhere (RecipeCard etc.).
        var moreCsvExpanded by remember { mutableStateOf(false) }
        Box {
            ScanEatOutlinedButton(
                onClick = { moreCsvExpanded = true },
                enabled = backupState !is BackupUiState.Working,
            ) {
                Icon(Icons.Default.TableChart, null, tint = OnBackground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_more_csv_export_button), color = OnBackground)
            }
            DropdownMenu(expanded = moreCsvExpanded, onDismissRequest = { moreCsvExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_weight_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareWeightCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_activity_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareActivityCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_hydration_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareHydrationCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_medication_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareMedicationCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_fasting_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareFastingCsvExport() })
            }
        }
        // Data stats — show what's stored so the user knows what they'd export or reset
        val (scanCount, diaryCount) = dataStats
        if (scanCount > 0 || diaryCount > 0) {
            HorizontalDivider(color = OnBackground.copy(0.08f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                DataStatChip(
                    icon = Icons.Default.QrCodeScanner,
                    label = stringResource(R.string.settings_data_stats_scans, scanCount),
                    modifier = Modifier.weight(1f),
                )
                DataStatChip(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = stringResource(R.string.settings_data_stats_diary, diaryCount),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DataResetSection(onShowResetDialog: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_reset)) {
        Text(stringResource(R.string.settings_reset_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        ScanEatOutlinedButton(onClick = onShowResetDialog) {
            Icon(Icons.Default.DeleteForever, null, tint = semanticRed(), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_reset_button), color = semanticRed())
        }
    }
}

@Composable
private fun HealthConnectSection(availability: HealthConnectAvailability, connected: Boolean, onConnect: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_health_connect)) {
        Text(stringResource(R.string.settings_healthconnect_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        when (availability) {
            HealthConnectAvailability.AVAILABLE -> {
                if (connected) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_healthconnect_connected), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
                    }
                } else {
                    ScanEatOutlinedButton(onClick = onConnect) {
                        Icon(Icons.Default.MonitorHeart, null, tint = OnBackground, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_healthconnect_connect_button), color = OnBackground)
                    }
                }
            }
            HealthConnectAvailability.NOT_INSTALLED -> Text(stringResource(R.string.settings_healthconnect_not_installed), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            HealthConnectAvailability.UNSUPPORTED   -> Text(stringResource(R.string.settings_healthconnect_unsupported), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        }
    }
}

@Composable
private fun AboutSection() {
    SettingsSection(stringResource(R.string.settings_section_about)) {
        Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, ENGINE_VERSION), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Text(stringResource(R.string.settings_about_sdk), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
    }
}

@Composable
private fun LegalSection() {
    SettingsSection(stringResource(R.string.settings_section_legal)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.settings_legal_medical_disclaimer), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_data_accuracy), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_nutrition_recs), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_medication), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_liability), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_privacy), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        }
    }
}

@Composable
private fun ResetConfirmDialog(
    pendingReset: ResetTarget?,
    onSetPendingReset: (ResetTarget?) -> Unit,
    onConfirmClearScans: () -> Unit,
    onConfirmClearFasting: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.settings_reset_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                when (pendingReset) {
                    // Second step - resetConfirmed previously existed as a plain Boolean
                    // that was never actually read anywhere, so the first tap on "Clear
                    // scan history" already ran the irreversible delete with no real
                    // second-confirmation step at all.
                    ResetTarget.SCANS -> {
                        Text(stringResource(R.string.settings_reset_confirm_body), style = MaterialTheme.typography.bodySmall, color = semanticRed())
                        TextButton(onClick = onConfirmClearScans) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_confirm_button), color = semanticRed(), fontWeight = FontWeight.Bold)
                        }
                    }
                    ResetTarget.FASTING -> {
                        Text(stringResource(R.string.settings_reset_confirm_body_fasting), style = MaterialTheme.typography.bodySmall, color = semanticRed())
                        TextButton(onClick = onConfirmClearFasting) {
                            Icon(Icons.Default.Timer, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_confirm_button), color = semanticRed(), fontWeight = FontWeight.Bold)
                        }
                    }
                    null -> {
                        Text(stringResource(R.string.settings_reset_dialog_body), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                        TextButton(onClick = { onSetPendingReset(ResetTarget.SCANS) }) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_clear_scans), color = semanticRed())
                        }
                        TextButton(onClick = { onSetPendingReset(ResetTarget.FASTING) }) {
                            Icon(Icons.Default.Timer, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_clear_fasting), color = semanticRed())
                        }
                        Text(stringResource(R.string.settings_reset_clear_all_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.45f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
