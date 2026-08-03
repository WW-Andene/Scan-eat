package fr.scanneat.presentation.templates.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.*

/**
 * Popup menu behind a "Filtres : <current>" button (see CollapsibleFilterBar's
 * own doc comment for why this is a popup, not an inline expandable list).
 */
@Composable
internal fun TemplatesMealFilterRow(
    expanded: Boolean,
    onToggle: () -> Unit,
    selected: MealSlot?,
    onSelect: (MealSlot?) -> Unit,
) {
    val mealOptions = listOf<MealSlot?>(null) + MealSlot.values().toList()
    val allLabel = stringResource(R.string.recipes_filter_all)
    CollapsibleFilterBar(
        expanded = expanded, onToggle = onToggle,
        summaryLabel = stringResource(R.string.foodsearch_filters_label, selected?.label() ?: allLabel),
    ) {
        mealOptions.forEach { slot ->
            val isSelected = selected == slot
            DropdownMenuItem(
                text = { Text(slot?.label() ?: allLabel) },
                trailingIcon = { if (isSelected) Icon(TablerIcons.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp)) },
                onClick = { onSelect(slot); onToggle() },
            )
        }
    }
}
