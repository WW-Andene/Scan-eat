package fr.scanneat.presentation.grocery.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun GroceryQuickAddRow(quickAddText: String, onQuickAddTextChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        OutlinedTextField(
            value = quickAddText,
            onValueChange = onQuickAddTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.grocery_quick_add_placeholder), color = OnBackground.copy(0.4f)) },
            singleLine = true,
            shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
            // Previously no imeAction/KeyboardActions at all - onAdd could only be
            // triggered by tapping the separate IconButton, not via the keyboard's
            // Done/Go action, on the app's primary "type and submit" entry row.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (quickAddText.isNotBlank()) onAdd() }),
        )
        IconButton(
            onClick = onAdd,
            enabled = quickAddText.isNotBlank(),
            modifier = Modifier.minTouchTarget(), // was a fixed 40dp, below the 48dp WCAG/Material minimum
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.grocery_quick_add_cd), tint = if (quickAddText.isNotBlank()) AccentCoral else OnBackground.copy(0.3f))
        }
    }
}
