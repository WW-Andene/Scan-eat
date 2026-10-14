package fr.scanneat.presentation.settings.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import fr.scanneat.R
import fr.scanneat.data.repository.backup.AutoBackupSettings
import fr.scanneat.presentation.ui.theme.*
import java.text.DateFormat
import java.util.Date

/** Roadmap C12: weekly encrypted backup into a user-chosen folder. */
@Composable
internal fun AutoBackupSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val enabled = remember(refresh) { AutoBackupSettings.isEnabled(context) }
    val lastMs = remember(refresh) { AutoBackupSettings.lastSuccessMs(context) }
    val lastFailed = remember(refresh) { AutoBackupSettings.lastFailed(context) }
    var askPassphrase by remember { mutableStateOf(false) }
    var pendingPassphrase by remember { mutableStateOf<String?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val pass = pendingPassphrase
        if (uri != null && pass != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                AutoBackupSettings.enable(context, uri, pass)
            }
        }
        pendingPassphrase = null
        refresh++
    }

    SettingsSection(stringResource(R.string.auto_backup_title), icon = Icons.Default.Backup) {
        Text(stringResource(R.string.auto_backup_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        val status = when {
            !enabled -> stringResource(R.string.auto_backup_off)
            lastMs == 0L -> stringResource(R.string.auto_backup_on_never)
            else -> stringResource(R.string.auto_backup_on_last, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(lastMs)))
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = if (enabled) OnBackground else semanticAmber())
        if (enabled && lastFailed) Text(stringResource(R.string.auto_backup_last_failed), style = MaterialTheme.typography.bodySmall, color = semanticRed())
        if (enabled) {
            ScanEatOutlinedButton(onClick = { AutoBackupSettings.disable(context); refresh++ }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auto_backup_disable), color = OnBackground)
            }
        } else {
            ScanEatOutlinedButton(onClick = { askPassphrase = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auto_backup_enable), color = OnBackground)
            }
        }
    }

    if (askPassphrase) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        val valid = p1.length >= 8 && p1 == p2
        GlassAlertDialog(
            onDismissRequest = { askPassphrase = false },
            title = { Text(stringResource(R.string.auto_backup_passphrase_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(stringResource(R.string.auto_backup_passphrase_body), style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    OutlinedTextField(p1, { p1 = it }, label = { Text(stringResource(R.string.auto_backup_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(p2, { p2 = it }, label = { Text(stringResource(R.string.auto_backup_passphrase_confirm)) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = p2.isNotEmpty() && p1 != p2,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { pendingPassphrase = p1; askPassphrase = false; pickFolder.launch(null) }) {
                    Text(stringResource(R.string.auto_backup_choose_folder))
                }
            },
            dismissButton = { TextButton(onClick = { askPassphrase = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
