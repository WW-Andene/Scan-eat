package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/** Which "Reset data" destructive action is currently mid-confirmation. */
internal enum class ResetTarget { SCANS, FASTING, ALL }

/** Generous cap for a legitimate backup (thousands of scan/diary rows is still a few MB) — rejects an arbitrarily large/mis-picked file before it's fully loaded into memory. */
internal const val MAX_BACKUP_IMPORT_BYTES = 50L * 1024 * 1024

@Composable
internal fun DataResetSection(onShowResetDialog: () -> Unit) {
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
internal fun ResetConfirmDialog(
    pendingReset: ResetTarget?,
    onSetPendingReset: (ResetTarget?) -> Unit,
    onConfirmClearScans: () -> Unit,
    onConfirmClearFasting: () -> Unit,
    onConfirmClearAll: () -> Unit,
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
                    ResetTarget.ALL -> {
                        Text(stringResource(R.string.settings_reset_confirm_body_all), style = MaterialTheme.typography.bodySmall, color = semanticRed(), fontWeight = FontWeight.Bold)
                        TextButton(onClick = onConfirmClearAll) {
                            Icon(Icons.Default.DeleteForever, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
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
                        HorizontalDivider(color = OnBackground.copy(0.1f))
                        TextButton(onClick = { onSetPendingReset(ResetTarget.ALL) }) {
                            Icon(Icons.Default.DeleteForever, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_clear_all), color = semanticRed(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
