package fr.scanneat.presentation.settings.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import fr.scanneat.presentation.settings.BackupUiState
import fr.scanneat.presentation.settings.SettingsViewModel
import java.time.LocalDate

/**
 * Sets up the four SAF (Storage Access Framework) launchers Settings' backup
 * section needs - JSON/CSV/PDF export destination pickers plus the JSON import
 * file picker - and the LaunchedEffect that fires the right export launcher
 * once the ViewModel side has the export payload ready. Split out of
 * SettingsScreen.kt to keep that file's own composable body from growing
 * unbounded; only [rememberBackupImportLauncher] (the import picker) is
 * actually referenced elsewhere (BackupSection's onImport), so that's the
 * only launcher returned - the export/csv/pdf launchers are only ever
 * triggered internally by [backupState] changing, never called directly by
 * the caller.
 */
@Composable
internal fun rememberBackupImportLauncher(
    viewModel: SettingsViewModel,
    backupState: State<BackupUiState>,
): ActivityResultLauncher<Array<String>> {
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
    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val state = backupState.value
        when {
            state !is BackupUiState.PdfExportReady -> if (uri == null) viewModel.clearBackupState() else viewModel.reportBackupIoFailed()
            uri == null -> { state.document.close(); viewModel.clearBackupState() }
            else -> {
                val stream = context.contentResolver.openOutputStream(uri)
                val wrote = stream != null && runCatching { stream.use { state.document.writeTo(it) } }.isSuccess
                state.document.close()
                if (wrote) viewModel.clearBackupState() else viewModel.reportBackupIoFailed()
            }
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
        } else if (state is BackupUiState.PdfExportReady) {
            pdfExportLauncher.launch("scaneat-rapport-${LocalDate.now()}.pdf")
        }
    }
    return importLauncher
}
