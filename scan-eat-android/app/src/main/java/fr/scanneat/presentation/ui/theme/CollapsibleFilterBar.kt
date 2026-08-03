package fr.scanneat.presentation.ui.theme

import compose.icons.tablericons.ChevronDown
import compose.icons.TablerIcons
import compose.icons.tablericons.Filter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A "Filtres : <current>" button that opens its options in a popup menu
 * (Material `DropdownMenu`, floating over the content) rather than an inline
 * expandable list — the previous shape (this same component, before this
 * rewrite) pushed the list/chips below it down every time it opened, which
 * read as a collapsible section, not a filter picker. A popup overlays
 * instead of displacing anything, and closes itself the moment an option is
 * picked (each caller's item composable calls onToggle() after selecting).
 */
@Composable
fun CollapsibleFilterBar(
    expanded: Boolean,
    onToggle: () -> Unit,
    summaryLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL))
                .clickable(onClick = onToggle).padding(vertical = Spacing.XS),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                Icon(TablerIcons.Filter, null, tint = OnBackground.copy(0.6f), modifier = Modifier.size(18.dp))
                Text(summaryLabel, style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.8f))
            }
            Icon(TablerIcons.ChevronDown, null, tint = OnBackground.copy(0.5f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle, content = content)
    }
}
