package fr.scanneat.presentation.history.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.history.HistorySort
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HistorySortMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, currentSort: HistorySort, onSortChange: (HistorySort) -> Unit) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Rounded.Sort, stringResource(R.string.history_sort), tint = OnBackground.copy(0.7f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, shape = RoundedCornerShape(CardRadius.CONTROL), containerColor = SurfaceVariant.copy(alpha = 0.94f), shadowElevation = 0.dp, modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL))) {
            val options = listOf(
                HistorySort.RECENT to stringResource(R.string.history_sort_recent),
                HistorySort.OLDEST to stringResource(R.string.history_sort_oldest),
                HistorySort.NAME_AZ to stringResource(R.string.history_sort_name),
                HistorySort.SCORE_DESC to stringResource(R.string.history_sort_score),
            )
            options.forEach { (value, label) ->
                val isSelected = currentSort == value
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        if (isSelected) Icon(TablerIcons.Check, contentDescription = null, tint = AccentCoral)
                    },
                    modifier = Modifier.semantics { selected = isSelected; role = Role.RadioButton },
                    onClick = { onSortChange(value); onExpandedChange(false) },
                )
            }
        }
    }
}
