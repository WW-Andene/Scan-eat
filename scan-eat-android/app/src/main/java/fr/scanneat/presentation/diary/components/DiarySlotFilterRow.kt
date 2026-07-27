package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun DiarySlotFilterRow(slotFilter: MealSlot?, onFilterChange: (MealSlot?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        item {
            FilterChip(
                selected = slotFilter == null,
                onClick = { onFilterChange(null) },
                label = { Text(stringResource(R.string.diary_filter_all), style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
            )
        }
        items(MealSlot.values()) { slot ->
            FilterChip(
                selected = slotFilter == slot,
                onClick = { onFilterChange(if (slotFilter == slot) null else slot) },
                label = { Text(slot.diaryLabel(), style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
            )
        }
    }
}
