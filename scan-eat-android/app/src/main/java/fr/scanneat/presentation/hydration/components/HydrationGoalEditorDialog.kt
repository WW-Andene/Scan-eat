package fr.scanneat.presentation.hydration.components

import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HydrationGoalEditorDialog(
    initialGoalText: String,
    hasCustomGoal: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var goalText by rememberSaveable { mutableStateOf(initialGoalText) }
    // Was a silent no-op: an empty/zero/out-of-range value just closed the dialog
    // with the previous goal quietly left in place, no isError/message telling the
    // user why nothing happened - same fix AddExpenseDialog's price/weight fields
    // got this round for the identical "typed something invalid" case.
    val goalValid = goalText.toIntOrNull()?.let { it in 1..10000 } == true
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hydration_edit_goal_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.hydration_goal_ml_hint)) },
                    isError = goalText.isNotBlank() && !goalValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    // app-audit §E6: had no colors at all - fell back fully to
                    // Material's default Gold-tinted field theming.
                    colors = scanEatTextFieldColors(),
                )
                if (hasCustomGoal) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.hydration_reset_goal), color = AccentCoral)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(goalText) }, enabled = goalValid) {
                Text(stringResource(R.string.common_save), color = if (goalValid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
