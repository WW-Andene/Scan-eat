package fr.scanneat.presentation.hydration.components

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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.hydration_edit_goal_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.hydration_goal_ml_hint)) },
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
            TextButton(onClick = { onConfirm(goalText) }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
