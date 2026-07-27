package fr.scanneat.presentation.settings.components

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

@Composable
internal fun BackupSection(
    backupState: BackupUiState,
    dataStats: Pair<Int, Int>,
    language: String,
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
