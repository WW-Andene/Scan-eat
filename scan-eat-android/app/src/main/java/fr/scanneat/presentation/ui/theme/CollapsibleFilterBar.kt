package fr.scanneat.presentation.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A filter-chip row hidden behind a "Filters: <current>" toggle instead of
 * always occupying screen space - previously each screen with filter chips
 * (History's grade chips, Recipes' goal chips, Templates' meal chips) showed
 * them in a permanently-visible LazyRow, which either got cut off/needed a
 * horizontal scroll on narrower devices or just ate vertical space above the
 * list even when the user wasn't actively filtering. Recherche/FoodSearch
 * already solved this with its own hand-rolled collapsible section; this
 * extracts that same pattern so every filterable list screen can share it
 * instead of a screen-specific reimplementation.
 */
@Composable
fun CollapsibleFilterBar(
    expanded: Boolean,
    onToggle: () -> Unit,
    summaryLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onToggle).padding(vertical = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                Icon(Icons.Rounded.FilterList, null, tint = OnBackground.copy(0.6f), modifier = Modifier.size(18.dp))
                Text(summaryLabel, style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.8f))
            }
            IconButton(onClick = onToggle) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = OnBackground.copy(0.5f))
            }
        }
        if (expanded) content()
    }
}
