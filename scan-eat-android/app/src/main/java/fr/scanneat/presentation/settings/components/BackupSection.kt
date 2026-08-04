package fr.scanneat.presentation.settings.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Barcode
import compose.icons.tablericons.Table
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.settings.BackupErrorKey
import fr.scanneat.presentation.settings.BackupUiState
import fr.scanneat.presentation.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DataStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = OnBackground.copy(0.06f),
        // art-direction-engine §CARDS: standalone stat tile directly on the
        // Settings screen background, matching the small-tile elevation tier
        // established elsewhere - had no shadowElevation at all.
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            Icon(icon, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Tiny))
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
        }
    }
}

@Composable
internal fun BackupSection(
    backupState: BackupUiState,
    dataStats: Pair<Int, Int>,
    language: String,
    onExport: (String?) -> Unit,
    onImport: () -> Unit,
    onClearBackupState: () -> Unit,
    onConfirmImport: (String, String?) -> Unit,
    onSubmitPassphrase: (String, String) -> Unit,
    onPrepareCsvExport: () -> Unit,
    onPrepareBiolismCsvExport: () -> Unit,
    onPrepareWeightCsvExport: () -> Unit,
    onPrepareActivityCsvExport: () -> Unit,
    onPrepareHydrationCsvExport: () -> Unit,
    onPrepareMedicationCsvExport: () -> Unit,
    onPrepareFastingCsvExport: () -> Unit,
    onPreparePricesCsvExport: () -> Unit,
    onPrepareCustomFoodsCsvExport: () -> Unit,
    onPrepareMealTemplatesCsvExport: () -> Unit,
    onPrepareRecipesCsvExport: () -> Unit,
    onPrepareScanHistoryCsvExport: () -> Unit,
    onPrepareMedicationsCsvExport: () -> Unit,
    onPrepareReport: () -> Unit,
) {
    var showExportDialog by remember { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_section_backup), icon = Icons.Default.Backup) {
        Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        val working = backupState is BackupUiState.Working
        // User-reported: "Restaurer une sauvegarde" is noticeably longer than
        // "Exporter mes données" - side by side in a Row with neither button
        // given fillMaxWidth/weight, the pair didn't jointly fit and the longer
        // label got compressed/wrapped. Stacked instead, each full width.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ScanEatPrimaryButton(
                onClick = { showExportDialog = true },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // No explicit tint - defaults to LocalContentColor, which
                // ScanEatPrimaryButton now correctly dims when disabled (was
                // previously hardcoded black regardless of enabled state,
                // compounding the same bug fixed in ScanEatButton.kt).
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(IconSize.Compact))
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(R.string.settings_backup_export_button))
            }
            ScanEatOutlinedButton(
                onClick = onImport,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Download, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
                Spacer(Modifier.width(Spacing.S))
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
            is BackupUiState.NeedsPassphrase -> {
                var passphraseInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = onClearBackupState,
                    title = { Text(stringResource(R.string.settings_backup_passphrase_title), color = OnBackground) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Text(stringResource(R.string.settings_backup_passphrase_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                            OutlinedTextField(
                                value = passphraseInput, onValueChange = { passphraseInput = it },
                                singleLine = true,
                                isError = s.wrongPassphrase,
                                label = { Text(stringResource(R.string.settings_backup_passphrase_field)) },
                                colors = scanEatTextFieldColors(),
                            )
                            if (s.wrongPassphrase) {
                                Text(stringResource(R.string.settings_backup_passphrase_wrong), style = MaterialTheme.typography.labelSmall, color = semanticRed())
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { onSubmitPassphrase(s.json, passphraseInput) },
                            enabled = passphraseInput.isNotBlank(),
                        ) { Text(stringResource(R.string.common_ok), color = AccentCoral) }
                    },
                    dismissButton = {
                        TextButton(onClick = onClearBackupState) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
                    },
                    containerColor = SurfaceVariant,
                    shape = RoundedCornerShape(CardRadius.PROMINENT),
                )
            }
            is BackupUiState.ImportPreview -> {
                // Every other date formatter in this screen respects the app's own
                // in-app language toggle (independent of device locale) - this one
                // didn't, so a user running the app in a language different from
                // their device locale saw this one date's month abbreviation in the
                // wrong language.
                val dateFmt = remember(language) { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale(language)) }
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
                        TextButton(onClick = { onConfirmImport(s.json, s.passphrase) }) {
                            Text(stringResource(R.string.settings_backup_import_confirm_button), color = AccentCoral)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onClearBackupState) {
                            Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
                        }
                    },
                    containerColor = SurfaceVariant,
                    shape = RoundedCornerShape(CardRadius.PROMINENT),
                )
            }
            else -> {}
        }
        if (showExportDialog) {
            var exportPassphrase by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text(stringResource(R.string.settings_backup_export_dialog_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.settings_backup_export_dialog_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                        OutlinedTextField(
                            value = exportPassphrase, onValueChange = { exportPassphrase = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.settings_backup_passphrase_field_optional)) },
                            colors = scanEatTextFieldColors(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onExport(exportPassphrase.takeIf { it.isNotBlank() }); showExportDialog = false }) {
                        Text(
                            stringResource(if (exportPassphrase.isNotBlank()) R.string.settings_backup_export_encrypt_button else R.string.settings_backup_export_button),
                            color = AccentCoral,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
                },
                containerColor = SurfaceVariant,
                shape = RoundedCornerShape(CardRadius.PROMINENT),
            )
        }
        // CSV diary export — spreadsheet-friendly complement to the JSON backup
        ScanEatDivider()
        ScanEatOutlinedButton(
            onClick = onPrepareCsvExport,
            enabled = backupState !is BackupUiState.Working,
        ) {
            Icon(TablerIcons.Table, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_csv_export_button), color = OnBackground)
        }
        // CSV Biolism export — same spreadsheet-friendly complement, for workout
        // sessions, which previously only ever left the app via the full JSON backup.
        ScanEatOutlinedButton(
            onClick = onPrepareBiolismCsvExport,
            enabled = backupState !is BackupUiState.Working,
        ) {
            Icon(TablerIcons.Table, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
            Spacer(Modifier.width(Spacing.S))
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
                Icon(TablerIcons.Table, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(R.string.settings_more_csv_export_button), color = OnBackground)
            }
            DropdownMenu(expanded = moreCsvExpanded, onDismissRequest = { moreCsvExpanded = false }, shape = RoundedCornerShape(CardRadius.CONTROL), containerColor = SurfaceVariant.copy(alpha = 0.94f), shadowElevation = 0.dp, modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL))) {
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
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_prices_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPreparePricesCsvExport() })
                // Last batch of domains that had JSON backup but no CSV equivalent -
                // same reasoning/pattern as the five entries above.
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_customfoods_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareCustomFoodsCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_mealtemplates_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareMealTemplatesCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_recipes_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareRecipesCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_scanhistory_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareScanHistoryCsvExport() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_medications_csv_export_button)) },
                    onClick = { moreCsvExpanded = false; onPrepareMedicationsCsvExport() })
            }
        }
        // PDF evolution report — a formatted, printable summary of the user's own
        // logged data (weight/nutrition/activity/hydration/fasting/expenses),
        // distinct from the raw CSV/JSON exports above. See PdfReportRepository's
        // own doc comment on why this is explicitly NOT framed as medical advice.
        ScanEatDivider()
        ScanEatOutlinedButton(
            onClick = onPrepareReport,
            enabled = backupState !is BackupUiState.Working,
        ) {
            Icon(Icons.Default.PictureAsPdf, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_pdf_report_button), color = OnBackground)
        }
        // Data stats — show what's stored so the user knows what they'd export or reset
        val (scanCount, diaryCount) = dataStats
        if (scanCount > 0 || diaryCount > 0) {
            ScanEatDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                DataStatChip(
                    icon = TablerIcons.Barcode,
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
