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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    content: @Composable ColumnScope.() -> Unit,
) {
    // User-reported: the pill sat flush against the screen's left edge with no
    // margin (History/Favorites screens) - a regression from the full-width
    // rewrite above, which used to inherit its horizontal inset from the
    // screen's own Column/padding when it spanned the full width. As a
    // self-contained pill it now needs to carry that inset itself, matching
    // the search bar directly above it on every call site.
    Box(modifier.padding(horizontal = Spacing.L)) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(8.dp),
            color = AccentCoral.copy(0.15f),
            border = BorderStroke(1.dp, AccentCoral.copy(0.4f)),
        ) {
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(TablerIcons.Filter, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                Text(summaryLabel, style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                Icon(TablerIcons.ChevronDown, null, tint = AccentCoral)
            }
        }
        // User-reported: the popup's own container used Material3's default
        // (cool gray) surfaceContainer color, standing out against the app's
        // warm palette — pinned to SurfaceVariant like every other themed
        // surface in the app. Also carries the same glass treatment
        // (tinted shadow + hairline sheen) as the app's cards — see
        // glassPopupSurface()'s doc comment for why it stops short of real
        // backdrop blur (DropdownMenu renders in its own Popup window).
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle,
            shape = RoundedCornerShape(CardRadius.CONTROL),
            containerColor = SurfaceVariant.copy(alpha = 0.94f),
            shadowElevation = 0.dp,
            modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL)),
            content = content,
        )
    }
}
