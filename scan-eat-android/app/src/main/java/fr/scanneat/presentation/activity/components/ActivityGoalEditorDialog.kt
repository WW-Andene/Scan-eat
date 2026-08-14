package fr.scanneat.presentation.activity.components

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

/** Same shape as HydrationGoalEditorDialog - configurable weekly active-
 *  minutes goal, previously a flat, non-editable WHO 150min figure. */
@Composable
internal fun ActivityGoalEditorDialog(
    initialGoalText: String,
    hasCustomGoal: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var goalText by rememberSaveable { mutableStateOf(initialGoalText) }
    val goalValid = goalText.toIntOrNull()?.let { it in 1..2000 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.activity_edit_goal_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.activity_goal_minutes_hint)) },
                    isError = goalText.isNotBlank() && !goalValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = scanEatTextFieldColors(),
                )
                if (hasCustomGoal) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.activity_reset_goal), color = AccentCoral)
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
