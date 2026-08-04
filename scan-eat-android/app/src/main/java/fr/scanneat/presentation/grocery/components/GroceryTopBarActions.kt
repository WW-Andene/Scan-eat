package fr.scanneat.presentation.grocery.components

import compose.icons.tablericons.Share
import compose.icons.TablerIcons
import compose.icons.tablericons.Copy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun GroceryTopBarActions(
    onNavigateToPlanning: (PlanningDestination) -> Unit,
    hasCheckedItems: Boolean,
    onShowClearConfirm: () -> Unit,
    hasItems: Boolean,
    sortAlpha: Boolean,
    onToggleSortAlpha: () -> Unit,
    groupByAisle: Boolean,
    onToggleGroupByAisle: () -> Unit,
    onShare: () -> Unit,
    copyMenuExpanded: Boolean,
    onCopyMenuExpandedChange: (Boolean) -> Unit,
    onCopyPlain: () -> Unit,
    onCopyChecklist: () -> Unit,
) {
    PlanningSwitcherMenu(current = PlanningDestination.GROCERY, onNavigate = onNavigateToPlanning)
    if (hasCheckedItems) {
        IconButton(onClick = onShowClearConfirm) {
            Icon(Icons.Rounded.RemoveDone, stringResource(R.string.grocery_clear_checked), tint = OnBackground.copy(0.7f))
        }
    }
    if (hasItems) {
        IconButton(onClick = onToggleSortAlpha) {
            Icon(
                Icons.Rounded.SortByAlpha,
                stringResource(R.string.grocery_sort_alpha),
                tint = if (sortAlpha) AccentCoral else OnBackground.copy(0.6f),
            )
        }
        // Previously a flat unsorted/alphabetical-only list with no
        // produce/dairy/pantry sectioning at all.
        IconButton(onClick = onToggleGroupByAisle) {
            Icon(
                Icons.Rounded.Category,
                stringResource(R.string.grocery_group_by_aisle),
                tint = if (groupByAisle) AccentCoral else OnBackground.copy(0.6f),
            )
        }
        // Previously the only way out of the app was clipboard copy-then-paste -
        // mirrors ResultScreen's existing ACTION_SEND share pattern.
        IconButton(onClick = onShare) {
            Icon(TablerIcons.Share, stringResource(R.string.grocery_cd_share), tint = OnBackground.copy(0.7f))
        }
        Box {
            IconButton(onClick = { onCopyMenuExpandedChange(true) }) {
                Icon(TablerIcons.Copy, stringResource(R.string.common_copy), tint = AccentCoral)
            }
            // DROPDOWN_MENU_GAP - app-wide standard gap between a DropdownMenu and its trigger (see its own doc comment).
            DropdownMenu(expanded = copyMenuExpanded, onDismissRequest = { onCopyMenuExpandedChange(false) }, shape = RoundedCornerShape(CardRadius.CONTROL), containerColor = SurfaceVariant.copy(alpha = 0.94f), shadowElevation = 0.dp, modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL)), offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = DROPDOWN_MENU_GAP)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.grocery_copy_plain)) },
                    onClick = { onCopyMenuExpandedChange(false); onCopyPlain() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.grocery_copy_checklist)) },
                    onClick = { onCopyMenuExpandedChange(false); onCopyChecklist() },
                )
            }
        }
    }
}
