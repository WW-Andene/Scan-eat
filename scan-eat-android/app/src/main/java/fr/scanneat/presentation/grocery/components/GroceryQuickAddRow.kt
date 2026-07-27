package fr.scanneat.presentation.grocery.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        )
        IconButton(
            onClick = onAdd,
            enabled = quickAddText.isNotBlank(),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.grocery_quick_add_cd), tint = if (quickAddText.isNotBlank()) AccentCoral else OnBackground.copy(0.3f))
        }
    }
}
