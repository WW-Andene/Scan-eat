package fr.scanneat.presentation.report

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
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors

/**
 * User-requested: "système de correction communautaire quand un produit est
 * mal classé" - e.g. a shampoo scored as food (classifyNonFood missed it), or
 * a real food product landing in ScanStateOverlay's non-food dialog. See
 * MisclassificationReportEntity's own doc comment on why this is a local log
 * rather than a real server-submitted community correction - scan-eat-server
 * has no user-data database to submit to. Shared by both entry points
 * (ResultScreen for "this scored as food but shouldn't have",
 * ScanStateOverlay's NonConsumableFound dialog for "this isn't actually
 * <category>") so the reported shape stays consistent regardless of which
 * screen the report started from.
 */
@Composable
fun ReportMisclassificationDialog(
    productName: String,
    currentClassificationLabel: String,
    onSubmit: (correctedClassification: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var corrected by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.report_misclassification_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(
                    stringResource(R.string.report_misclassification_body, productName, currentClassificationLabel),
                    style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f),
                )
                OutlinedTextField(
                    value = corrected, onValueChange = { corrected = it },
                    label = { Text(stringResource(R.string.report_misclassification_corrected_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.report_misclassification_note_label)) },
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(corrected, note) }, enabled = corrected.isNotBlank()) {
                Text(stringResource(R.string.report_misclassification_submit), color = AccentCoral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
