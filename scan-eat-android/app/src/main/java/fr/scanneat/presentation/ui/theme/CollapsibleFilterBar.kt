package fr.scanneat.presentation.ui.theme

import compose.icons.tablericons.ChevronDown
import compose.icons.TablerIcons
import compose.icons.tablericons.Filter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A "Filtres : <current>" button that opens its options in a popup menu
 * (Material `DropdownMenu`, floating over the content) rather than an inline
 * expandable list.
 *
 * User-reported (visual review of the running app): the previous shape — a
 * full-width Row with SpaceBetween icon/label/chevron — read as a list row
 * or a collapsible section (something you'd expect to expand in place), not
 * as a button that opens a popup; the mismatch between "looks like a list"
 * and "behaves like a popup" was the actual complaint, not the popup itself.
 * Rebuilt to match Journal's own tab-picker button exactly (DiaryScreen.kt) —
 * a compact, self-contained accent-tinted pill (not full-width), so its shape
 * alone signals "tap to open a menu" the same way Journal's does.
 */
@Composable
fun CollapsibleFilterBar(
    expanded: Boolean,
    onToggle: () -> Unit,
    summaryLabel: String,
    modifier: Modifier = Modifier,
    // User-reported: on Recipes/Templates, this pill sat further right than the
    // search field and "Filtres : Tous" wasn't aligned with the screen's
    // standard left content padding - those two screens wrap their whole list
    // in a Column/LazyColumn that already applies Spacing.L horizontal padding,
    // so this bar's own inset below stacked on top of it. History/Favorites and
    // FoodSearch don't pad their outer container that way (see this composable's
    // own history below), so they still need it. Callers whose parent already
    // carries the inset pass false here instead of getting double-padded.
    applyHorizontalInset: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // User-reported: the pill sat flush against the screen's left edge with no
    // margin (History/Favorites screens) - a regression from the full-width
    // rewrite above, which used to inherit its horizontal inset from the
    // screen's own Column/padding when it spanned the full width. As a
    // self-contained pill it now needs to carry that inset itself, matching
    // the search bar directly above it on every call site that doesn't
    // already provide one (see applyHorizontalInset above for the ones that do).
    Box(if (applyHorizontalInset) modifier.padding(horizontal = Spacing.L) else modifier) {
        var triggerWidth by remember { mutableStateOf(0.dp) }
        Surface(
            onClick = onToggle,
            // User-reported: radius/height didn't match this same screen's other
            // pill button (e.g. FoodSearchScreen's DisplayModeButton, both
            // CardRadius.CONTROL) - was a hardcoded 8.dp, its own one-off value.
            shape = RoundedCornerShape(CardRadius.CONTROL),
            color = ChipBackgroundAccent,
            border = BorderStroke(2.dp, AccentCoral.copy(alpha = CHIP_BORDER_ALPHA)),
            modifier = Modifier.reportWidthTo { triggerWidth = it },
        ) {
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Icon(TablerIcons.Filter, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Compact))
                Text(summaryLabel, style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                Icon(TablerIcons.ChevronDown, null, tint = AccentCoral)
            }
        }
        // User-reported: previously used Material3's own DropdownMenu, whose
        // built-in position provider could flip the popup ABOVE this pill
        // depending on scroll position - see ScanEatDropdownMenu's own doc
        // comment. This is that shared always-below replacement.
        ScanEatDropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle,
            anchorWidth = triggerWidth,
            content = content,
        )
    }
}
