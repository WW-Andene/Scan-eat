package fr.scanneat.presentation.settings.components

import compose.icons.tablericons.Barcode
import compose.icons.TablerIcons
import compose.icons.tablericons.Trash
import androidx.compose.foundation.shape.RoundedCornerShape
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
    SettingsSection(stringResource(R.string.settings_section_reset), icon = TablerIcons.Trash) {
        Text(stringResource(R.string.settings_reset_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        ScanEatOutlinedButton(onClick = onShowResetDialog) {
            Icon(TablerIcons.Trash, null, tint = semanticRed(), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.S))
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
        shape = RoundedCornerShape(CardRadius.PROMINENT),
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
                            Icon(TablerIcons.Barcode, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(Spacing.XS))
                            // Was the same "Yes, clear everything" label shared by all three
                            // tiers - a user confirming "clear scan history" read a button
                            // that literally said "everything," either causing hesitation or
                            // false confidence the action was narrower than it visibly claimed.
                            Text(stringResource(R.string.settings_reset_confirm_button_scans), color = semanticRed(), fontWeight = FontWeight.Bold)
                        }
                    }
                    ResetTarget.FASTING -> {
                        Text(stringResource(R.string.settings_reset_confirm_body_fasting), style = MaterialTheme.typography.bodySmall, color = semanticRed())
                        TextButton(onClick = onConfirmClearFasting) {
                            Icon(Icons.Default.Timer, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_confirm_button_fasting), color = semanticRed(), fontWeight = FontWeight.Bold)
                        }
                    }
                    ResetTarget.ALL -> {
                        Text(stringResource(R.string.settings_reset_confirm_body_all), style = MaterialTheme.typography.bodySmall, color = semanticRed(), fontWeight = FontWeight.Bold)
                        TextButton(onClick = onConfirmClearAll) {
                            Icon(TablerIcons.Trash, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_confirm_button), color = semanticRed(), fontWeight = FontWeight.Bold)
                        }
                    }
                    null -> {
                        Text(stringResource(R.string.settings_reset_dialog_body), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                        TextButton(onClick = { onSetPendingReset(ResetTarget.SCANS) }) {
                            Icon(TablerIcons.Barcode, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_clear_scans), color = semanticRed())
                        }
                        TextButton(onClick = { onSetPendingReset(ResetTarget.FASTING) }) {
                            Icon(Icons.Default.Timer, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(Spacing.XS))
                            Text(stringResource(R.string.settings_reset_clear_fasting), color = semanticRed())
                        }
                        HorizontalDivider(color = OnBackground.copy(0.1f))
                        TextButton(onClick = { onSetPendingReset(ResetTarget.ALL) }) {
                            Icon(TablerIcons.Trash, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Small))
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
