package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.settings.BackupUiState
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Passphrase prompt for an encrypted backup import - extracted from BackupSection (§T1 composition-root split). */
@Composable
internal fun BackupPassphraseDialog(state: BackupUiState.NeedsPassphrase, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var passphraseInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_passphrase_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.settings_backup_passphrase_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                OutlinedTextField(
                    value = passphraseInput, onValueChange = { passphraseInput = it },
                    singleLine = true,
                    isError = state.wrongPassphrase,
                    label = { Text(stringResource(R.string.settings_backup_passphrase_field)) },
                    colors = scanEatTextFieldColors(),
                )
                if (state.wrongPassphrase) {
                    Text(stringResource(R.string.settings_backup_passphrase_wrong), style = MaterialTheme.typography.labelSmall, color = semanticRed())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(state.json, passphraseInput) },
                enabled = passphraseInput.isNotBlank(),
            ) { Text(stringResource(R.string.common_ok), color = AccentCoral) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}

/** Confirmation step before overwriting local data with a previewed import - extracted from BackupSection. */
@Composable
internal fun BackupImportPreviewDialog(state: BackupUiState.ImportPreview, language: String, onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    // Every other date formatter in this screen respects the app's own
    // in-app language toggle (independent of device locale) - this one
    // didn't, so a user running the app in a language different from
    // their device locale saw this one date's month abbreviation in the
    // wrong language.
    val dateFmt = remember(language) { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale(language)) }
    val exportedDate = Instant.ofEpochMilli(state.metadata.exportedAtMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_import_confirm_title), color = OnBackground) },
        text = {
            Text(
                stringResource(R.string.settings_backup_import_confirm_body, exportedDate, state.metadata.appVersionName, state.metadata.summary.total),
                color = OnBackground.copy(0.8f),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.json, state.passphrase) }) {
                Text(stringResource(R.string.settings_backup_import_confirm_button), color = AccentCoral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
            }
        },
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}

/** Optional-passphrase prompt before starting a JSON export - extracted from BackupSection. */
@Composable
internal fun BackupExportDialog(onDismiss: () -> Unit, onExport: (String?) -> Unit) {
    var exportPassphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = { onExport(exportPassphrase.takeIf { it.isNotBlank() }) }) {
                Text(
                    stringResource(if (exportPassphrase.isNotBlank()) R.string.settings_backup_export_encrypt_button else R.string.settings_backup_export_button),
                    color = AccentCoral,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
