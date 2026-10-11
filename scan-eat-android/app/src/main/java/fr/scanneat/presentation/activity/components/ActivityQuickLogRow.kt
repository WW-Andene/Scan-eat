package fr.scanneat.presentation.activity.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Warm

/**
 * User-requested: "develop the tool" for Activity - one-tap re-log of a
 * frequently repeated workout instead of re-filling the whole Add dialog
 * every time. See ActivityViewModel.quickLogSuggestions' own doc comment for
 * the "seen at least twice, not already logged today" selection rule -
 * mirrors GroceryFrequentSuggestionsRow's identical chip-row shape (same
 * "seen at least twice" bar), Activity's own accent (Warm) instead of
 * Grocery's neutral chip fill, matching AddActivityDialog's own chip colors.
 */
@Composable
internal fun ActivityQuickLogRow(
    suggestions: List<ActivityEntry>,
    typeLabels: Map<ActivityType, String>,
    subTypeLabels: Map<String, String>,
    onQuickLog: (ActivityEntry) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            stringResource(R.string.activity_quick_log_title),
            style = MaterialTheme.typography.labelMedium,
            color = OnBackground.copy(0.6f),
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            items(suggestions, key = { it.id }) { entry ->
                val typeLabel = typeLabels[entry.type] ?: entry.type.name
                val subLabel = entry.subType?.let { subTypeLabels[it] ?: it }
                val label = if (subLabel != null) "$typeLabel · $subLabel · ${entry.minutes} min" else "$typeLabel · ${entry.minutes} min"
                SuggestionChip(
                    onClick = { onQuickLog(entry) },
                    icon = { Icon(TablerIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Micro)) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(CardRadius.BADGE),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Warm.copy(alpha = 0.12f),
                        labelColor = Warm,
                        iconContentColor = Warm,
                    ),
                    border = null,
                )
            }
        }
    }
}
