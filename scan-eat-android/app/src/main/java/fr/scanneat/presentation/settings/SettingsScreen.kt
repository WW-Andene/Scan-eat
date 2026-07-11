package fr.scanneat.presentation.settings

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import fr.scanneat.data.repository.scan.DEFAULT_MODEL
import fr.scanneat.data.repository.scan.FALLBACK_MODEL
import fr.scanneat.domain.engine.scoring.ENGINE_VERSION
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate

/** Generous cap for a legitimate backup (thousands of scan/diary rows is still a few MB) — rejects an arbitrarily large/mis-picked file before it's fully loaded into memory. */
private const val MAX_BACKUP_IMPORT_BYTES = 50L * 1024 * 1024

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
    onOpenProfile: () -> Unit = {},
) {
    val apiKey    = viewModel.apiKey.collectAsStateWithLifecycle()
    val groqModel = viewModel.groqModel.collectAsStateWithLifecycle()
    val mode      = viewModel.mode.collectAsStateWithLifecycle()
    val serverUrl = viewModel.serverUrl.collectAsStateWithLifecycle()
    val language  = viewModel.language.collectAsStateWithLifecycle()
    val theme     = viewModel.theme.collectAsStateWithLifecycle()
    val dyslexicFont   = viewModel.dyslexicFont.collectAsStateWithLifecycle()
    val colorblindMode = viewModel.colorblindMode.collectAsStateWithLifecycle()
    val savedField = viewModel.savedField.collectAsStateWithLifecycle()
    val backupState = viewModel.backupState.collectAsStateWithLifecycle()
    val healthConnectAvailability = viewModel.healthConnectAvailability.collectAsStateWithLifecycle()
    val healthConnectConnected = viewModel.healthConnectConnected.collectAsStateWithLifecycle()

    var keyVisible  by remember { mutableStateOf(false) }
    var localKey    by remember(apiKey.value)    { mutableStateOf(apiKey.value) }
    var localUrl    by remember(serverUrl.value) { mutableStateOf(serverUrl.value) }
    var localModel  by remember(groqModel.value) { mutableStateOf(groqModel.value) }

    LaunchedEffect(savedField.value) {
        if (savedField.value != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSavedField()
        }
    }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val state = backupState.value
        if (uri != null && state is BackupUiState.ExportReady) {
            val wrote = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(state.json.toByteArray()) }
            }.isSuccess
            if (wrote) viewModel.clearBackupState() else viewModel.reportExportWriteFailed()
        } else {
            viewModel.clearBackupState()
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
                viewModel.reportExportWriteFailed()
            } else {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (json != null) viewModel.importFromJson(json) else viewModel.reportExportWriteFailed()
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
        }
    }

    val healthConnectLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthConnectStatus()
    }
    LaunchedEffect(Unit) { viewModel.refreshHealthConnectStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = OnBackground) },
                navigationIcon = {
                    if (!isTabRoot) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- Profile — first thing in Réglages, not buried at the bottom ----
            SettingsSection(stringResource(R.string.settings_section_profile)) {
                Text(stringResource(R.string.settings_profile_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                OutlinedButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Person, null, tint = OnBackground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_profile_button), color = OnBackground)
                }
            }

            // ---- API Mode ----
            SettingsSection(stringResource(R.string.settings_section_api_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApiMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode.value == m,
                            onClick  = { viewModel.setMode(m) },
                            label    = { Text(if (m == ApiMode.DIRECT) stringResource(R.string.settings_mode_direct) else stringResource(R.string.settings_mode_server)) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                            ),
                        )
                    }
                }
                Text(
                    if (mode.value == ApiMode.DIRECT) stringResource(R.string.settings_mode_direct_desc)
                    else stringResource(R.string.settings_mode_server_desc),
                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
                )
            }

            // ---- Groq API key ----
            if (mode.value == ApiMode.DIRECT) {
                SettingsSection(stringResource(R.string.settings_section_groq_key)) {
                    OutlinedTextField(
                        value = localKey, onValueChange = { localKey = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_groq_key_placeholder)) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = scanEatTextFieldColors(),
                    )
                    Text(stringResource(R.string.onboarding_api_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                    SaveButtonRow(saved = savedField.value == "apiKey") { viewModel.saveApiKey(localKey) }
                }

                // ---- Groq model ----
                SettingsSection(stringResource(R.string.settings_section_groq_model)) {
                    Text(
                        stringResource(R.string.settings_groq_model_hint, DEFAULT_MODEL),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(DEFAULT_MODEL, FALLBACK_MODEL).forEach { m ->
                            FilterChip(
                                selected = localModel == m,
                                onClick  = { localModel = m },
                                label    = { Text(m.substringAfterLast('/'), maxLines = 1) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = localModel, onValueChange = { localModel = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_groq_model_id_placeholder)) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = scanEatTextFieldColors(),
                    )
                    SaveButtonRow(saved = savedField.value == "groqModel") { viewModel.saveGroqModel(localModel) }
                }
            }

            // ---- Server URL ----
            if (mode.value == ApiMode.SERVER) {
                SettingsSection(stringResource(R.string.settings_server_url)) {
                    OutlinedTextField(
                        value = localUrl, onValueChange = { localUrl = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_server_url_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = scanEatTextFieldColors(),
                    )
                    SaveButtonRow(saved = savedField.value == "serverUrl") { viewModel.saveServerUrl(localUrl) }
                }
            }

            // Fix 4: Language toggle
            SettingsSection(stringResource(R.string.settings_section_language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("fr" to stringResource(R.string.settings_lang_fr), "en" to stringResource(R.string.settings_lang_en)).forEach { (code, label) ->
                        FilterChip(
                            selected = language.value == code,
                            onClick  = { viewModel.setLanguage(code) },
                            label    = { Text(label) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                            ),
                        )
                    }
                }
            }

            // Fix 4: Theme toggle
            SettingsSection(stringResource(R.string.settings_section_theme)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("oled" to stringResource(R.string.settings_theme_oled), "dark" to stringResource(R.string.settings_theme_dark), "light" to stringResource(R.string.settings_theme_light)).forEach { (key, label) ->
                        FilterChip(
                            selected = theme.value == key,
                            onClick  = { viewModel.setTheme(key) },
                            label    = { Text(label) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                            ),
                        )
                    }
                }
            }

            // ---- Accessibility ----
            SettingsSection(stringResource(R.string.settings_section_accessibility)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dyslexic_font), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                        Text(stringResource(R.string.settings_dyslexic_font_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                    }
                    Switch(
                        checked = dyslexicFont.value,
                        onCheckedChange = { viewModel.setDyslexicFont(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.settings_colorblind_mode), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "none" to stringResource(R.string.settings_colorblind_none),
                        "deuteranopia" to stringResource(R.string.settings_colorblind_deuteranopia),
                        "protanopia" to stringResource(R.string.settings_colorblind_protanopia),
                        "tritanopia" to stringResource(R.string.settings_colorblind_tritanopia),
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = colorblindMode.value == key,
                            onClick  = { viewModel.setColorblindMode(key) },
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Backup — local export/import, no cloud account required
            SettingsSection(stringResource(R.string.settings_section_backup)) {
                Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                val working = backupState.value is BackupUiState.Working
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanEatPrimaryButton(
                        onClick = { viewModel.prepareExport() },
                        enabled = !working,
                    ) {
                        Icon(Icons.Default.Upload, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_backup_export_button))
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = !working,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Download, null, tint = OnBackground, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_backup_import_button), color = OnBackground)
                    }
                }
                when (val s = backupState.value) {
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
                        onDismiss = { viewModel.clearBackupState() },
                    )
                    else -> {}
                }
            }

            // Health Connect — platform weight sync
            SettingsSection(stringResource(R.string.settings_section_health_connect)) {
                Text(stringResource(R.string.settings_healthconnect_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                when (healthConnectAvailability.value) {
                    HealthConnectAvailability.AVAILABLE -> {
                        if (healthConnectConnected.value) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.settings_healthconnect_connected), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { healthConnectLauncher.launch(viewModel.healthConnectPermissions) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
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

            // About
            SettingsSection(stringResource(R.string.settings_section_about)) {
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, ENGINE_VERSION), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                Text(stringResource(R.string.settings_about_sdk), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            }

            Spacer(Modifier.height(40.dp))
        }
    }

}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SaveButtonRow(saved: Boolean, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ScanEatPrimaryButton(onClick = onSave) {
            Text(stringResource(R.string.common_save))
        }
        androidx.compose.animation.AnimatedVisibility(visible = saved) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.settings_saved_confirmation), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
            }
        }
    }
}
