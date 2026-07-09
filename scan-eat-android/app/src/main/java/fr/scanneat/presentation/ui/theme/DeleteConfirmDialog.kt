package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R

/** Shared delete-confirmation dialog — same body used by Weight/Templates/Recipes/Activity. */
@Composable
fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceVariant,
        title   = { Text(stringResource(R.string.common_delete_confirm_title), color = OnBackground) },
        text    = { Text(stringResource(R.string.common_delete_confirm_body), color = OnBackground.copy(0.7f)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_delete), color = FlagRed) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
