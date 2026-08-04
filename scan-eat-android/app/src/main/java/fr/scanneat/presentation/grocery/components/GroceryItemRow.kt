package fr.scanneat.presentation.grocery.components

import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.presentation.grocery.CheckableGroceryItem
import fr.scanneat.presentation.ui.theme.*

/** Extracted so the same row renders identically flat (default) or grouped-by-aisle. */
@Composable
internal fun GroceryItemRow(
    checkableItem: CheckableGroceryItem,
    warning: String?,
    isManual: Boolean,
    onToggleChecked: (Boolean) -> Unit,
    onDeleteManual: () -> Unit,
    onEditQuantity: (Double) -> Unit,
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
    // Only meaningful for a manual contribution's own grams - a recipe-derived
    // row's quantity comes from its recipe's ingredient list, not something
    // this screen should let a user silently drift away from that source.
    var showEditQuantity by remember { mutableStateOf(false) }
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(horizontal = Spacing.XS, vertical = Spacing.XS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            // User-reported: no accessibility labeling anywhere in this screen -
            // a bare Checkbox announces only "checkbox, checked/not checked" to
            // TalkBack with no indication of which item it belongs to.
            Checkbox(
                checked = checked,
                onCheckedChange = onToggleChecked,
                colors = CheckboxDefaults.colors(checkedColor = AccentCoral),
                modifier = Modifier.semantics { contentDescription = item.name },
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface.copy(contentAlpha), fontWeight = FontWeight.Medium,
                        textDecoration = if (checked) TextDecoration.LineThrough else null)
                    // Connects SeasonalProduceScreen (previously an isolated
                    // reference screen - R&D audit finding) to the grocery list -
                    // a quiet in-season nudge, not a warning, so no color/weight
                    // competing with the item name itself.
                    if (!checked && fr.scanneat.domain.engine.nutrition.isInSeasonNow(item.name)) {
                        Surface(shape = RoundedCornerShape(CardRadius.BADGE), color = semanticGreen().copy(alpha = 0.12f)) {
                            Text(
                                stringResource(R.string.grocery_in_season_badge),
                                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2),
                                style = MaterialTheme.typography.labelSmall,
                                color = semanticGreen(),
                            )
                        }
                    }
                }
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
                Text(
                    stringResource(R.string.grocery_grams, item.grams), style = MaterialTheme.typography.labelLarge,
                    color = AccentCoral.copy(contentAlpha), fontWeight = FontWeight.SemiBold,
                    // Manual quantities previously could only be changed via
                    // delete-and-re-add - tapping the amount now opens a quick
                    // edit dialog. Recipe-derived amounts stay non-interactive
                    // (see showEditQuantity's own doc comment).
                    modifier = Modifier.padding(end = Spacing.S)
                        .let { if (isManual) it.clickable { showEditQuantity = true } else it },
                )
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
                    Icon(TablerIcons.X, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Small))
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
    if (showEditQuantity) {
        EditGroceryQuantityDialog(
            itemName = item.name,
            initialGrams = item.grams.toDouble(),
            onConfirm = { grams -> onEditQuantity(grams); showEditQuantity = false },
            onDismiss = { showEditQuantity = false },
        )
    }
}

@Composable
private fun EditGroceryQuantityDialog(itemName: String, initialGrams: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (initialGrams > 0) initialGrams.toInt().toString() else "") }
    val grams = text.toDoubleOrNull()?.takeIf { it in 0.0..50000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(itemName, color = OnBackground) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(R.string.grocery_edit_quantity_label)) },
                singleLine = true,
                isError = text.isNotBlank() && grams == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { grams?.let(onConfirm) }, enabled = grams != null) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
