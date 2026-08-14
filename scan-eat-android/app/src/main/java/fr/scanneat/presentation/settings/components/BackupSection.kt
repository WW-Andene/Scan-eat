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

@Composable
internal fun DataStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(CardRadius.CONTROL))
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
    onPrepareSymptomCsvExport: () -> Unit,
    onPreparePantryCsvExport: () -> Unit,
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
                BackupPassphraseDialog(state = s, onDismiss = onClearBackupState, onSubmit = onSubmitPassphrase)
            }
            is BackupUiState.ImportPreview -> {
                BackupImportPreviewDialog(state = s, language = language, onDismiss = onClearBackupState, onConfirm = onConfirmImport)
            }
            else -> {}
        }
        if (showExportDialog) {
            BackupExportDialog(
                onDismiss = { showExportDialog = false },
                onExport = { passphrase -> onExport(passphrase); showExportDialog = false },
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
        BackupCsvOverflowMenu(
            enabled = backupState !is BackupUiState.Working,
            onPrepareWeightCsvExport = onPrepareWeightCsvExport,
            onPrepareActivityCsvExport = onPrepareActivityCsvExport,
            onPrepareHydrationCsvExport = onPrepareHydrationCsvExport,
            onPrepareMedicationCsvExport = onPrepareMedicationCsvExport,
            onPrepareFastingCsvExport = onPrepareFastingCsvExport,
            onPreparePricesCsvExport = onPreparePricesCsvExport,
            onPrepareCustomFoodsCsvExport = onPrepareCustomFoodsCsvExport,
            onPrepareMealTemplatesCsvExport = onPrepareMealTemplatesCsvExport,
            onPrepareRecipesCsvExport = onPrepareRecipesCsvExport,
            onPrepareScanHistoryCsvExport = onPrepareScanHistoryCsvExport,
            onPrepareMedicationsCsvExport = onPrepareMedicationsCsvExport,
            onPrepareSymptomCsvExport = onPrepareSymptomCsvExport,
            onPreparePantryCsvExport = onPreparePantryCsvExport,
        )
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
