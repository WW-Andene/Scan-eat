package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import fr.scanneat.R

/**
 * Shared confirm/cancel dialog body — same layout used by every destructive-action prompt in the app.
 *
 * design-aesthetic-audit §DTA2: the app defines a deliberate 3-tier corner-
 * radius scale (CardRadius.CONTROL/CARD/PROMINENT) but this shared dialog -
 * the one every destructive confirm in the app is built on - never set a
 * shape, so it silently used Material's unthemed default (28dp extra-large),
 * off the app's own scale entirely. PROMINENT (20dp) matches the tier the
 * scale already reserves for bottom sheets/modals.
 */
@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, confirmColor: Color = semanticRed(), onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape   = RoundedCornerShape(CardRadius.PROMINENT),
        title   = { Text(title, color = OnBackground) },
        text    = { Text(body, color = OnBackground.copy(0.7f)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = confirmColor) } },
        // §E3 contrast: 0.6f alpha only reached 4.25:1 on Light (below 4.5:1 AA) -
        // TextSecondary clears AA in every theme.
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextSecondary) } },
    )
}

/**
 * Shared delete-confirmation dialog — same body used by Weight/Templates/Recipes/Activity.
 * [itemName], when the caller has one in scope, names the specific item being deleted
 * (e.g. a recipe name, a date) instead of a fully generic "delete this?" - reduces the
 * risk of confirming the wrong row after a misclick in a dense list.
 */
@Composable
fun DeleteConfirmDialog(itemName: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.common_delete_confirm_title),
        body  = if (itemName != null) stringResource(R.string.common_delete_confirm_body_named, itemName)
                else stringResource(R.string.common_delete_confirm_body),
        confirmLabel = stringResource(R.string.common_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
