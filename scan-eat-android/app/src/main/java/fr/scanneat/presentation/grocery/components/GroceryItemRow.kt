package fr.scanneat.presentation.grocery.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.presentation.ui.theme.*

/** Extracted so the same row renders identically flat (default) or grouped-by-aisle. */
@Composable
internal fun GroceryItemRow(
    checkableItem: CheckableGroceryItem,
    warning: String?,
    isManual: Boolean,
    onToggleChecked: (Boolean) -> Unit,
    onDeleteManual: () -> Unit,
) {
    val item = checkableItem.item
    val checked = checkableItem.checked
    val reducedMotion = rememberReducedMotion()
    val contentAlpha by animateFloatAsState(
        targetValue   = if (checked) 0.5f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 200),
        label         = "groceryItemAlpha",
    )
    // Previously deleted a manual item on a single tap with no confirmation - every
    // other destructive action in the app (Weight/Activity/Recipes/Templates/Medication/
    // CustomFood/ScanHistory) routes through DeleteConfirmDialog first.
    var showDeleteConfirm by remember { mutableStateOf(false) }
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(horizontal = Spacing.XS, vertical = Spacing.XS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Checkbox(
                checked = checked,
                onCheckedChange = onToggleChecked,
                colors = CheckboxDefaults.colors(checkedColor = AccentCoral),
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface.copy(contentAlpha), fontWeight = FontWeight.Medium,
                    textDecoration = if (checked) TextDecoration.LineThrough else null)
                if (item.sources.isNotEmpty()) {
                    Text(item.sources.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f * contentAlpha))
                }
                // Diet/allergen check previously only ran on scanned products and
                // (as of this same round) Recipes - the grocery list itself, the
                // other place a user relies on the app to protect them, had none.
                warning?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = semanticAmber().copy(contentAlpha))
                }
            }
            if (item.grams > 0) {
                Text(stringResource(R.string.grocery_grams, item.grams), style = MaterialTheme.typography.labelLarge,
                    color = AccentCoral.copy(contentAlpha), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = Spacing.S))
            }
            // Manually-added items (e.g. "Save to grocery" from a scanned
            // product) previously had no way to be removed at all, only
            // checked off — the only way to hide one was to leave it
            // permanently ticked. Only shown when this row actually has a
            // manual contribution, since a recipe-only row has nothing here to delete.
            if (isManual) {
                // Left at IconButton's default 48dp touch target (Material/WCAG
                // minimum) - a UI/UX audit found this forced to 32dp.
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            itemName = item.name,
            onConfirm = { onDeleteManual(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
