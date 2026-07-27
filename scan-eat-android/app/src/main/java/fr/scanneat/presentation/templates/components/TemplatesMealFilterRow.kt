package fr.scanneat.presentation.templates.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun TemplatesMealFilterRow(selected: MealSlot?, onSelect: (MealSlot?) -> Unit) {
    val mealOptions = listOf<MealSlot?>(null) + MealSlot.values().toList()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        items(mealOptions) { slot ->
            FilterChip(
                selected = selected == slot,
                onClick = { onSelect(slot) },
                label = { Text(slot?.label() ?: stringResource(R.string.recipes_filter_all)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
            )
        }
    }
}
