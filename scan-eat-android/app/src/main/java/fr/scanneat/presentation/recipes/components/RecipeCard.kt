package fr.scanneat.presentation.recipes.components

import compose.icons.tablericons.Plus
import compose.icons.tablericons.Share
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Copy
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Edit
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.formatShareText
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.result.HintIconButton
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.CardRadius

@Composable
internal fun RecipeCard(recipe: Recipe, warning: String?, pairings: List<String>, hints: ProductHints, onLog: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onEditNotes: () -> Unit, onToggleFavorite: () -> Unit, onScale: () -> Unit, onSaveAsTemplate: () -> Unit, onDuplicate: () -> Unit, onEditIngredients: () -> Unit) {
    val context = LocalContext.current
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.recipes_summary, recipe.totalKcal.toInt(), recipe.components.size, recipe.totalGrams.toInt()),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
            }
            // Recipe had no equivalent to ScanResult's favorite field at all -
            // mirrors ScanHistoryScreen's identical star toggle placement.
            // Left at IconButton's default 48dp touch target (Material/WCAG
            // minimum) rather than the 36dp this row used to force on every
            // action - a UI/UX audit found 8 icon-sized controls competing for
            // width in this one row, each below the 48dp minimum tappable size.
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (recipe.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(if (recipe.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                    tint = if (recipe.favorite) Gold else OnSurface.copy(0.3f),
                )
            }
            // The "💡" hint panel was previously reachable only from a scanned
            // product's Result screen - a saved Recipe carries the exact same
            // toCheckProduct() a scan does, so the same benefits/risks/facts
            // apply and were simply never surfaced here.
            HintIconButton(hints = hints)
            Row {
                // Log stays the one always-visible action - everything else
                // (Share/Notes/Scale/Save-as-Template/Rename/Delete) moves into
                // an overflow menu so each remaining control can sit at a
                // compliant 48dp instead of being squeezed to fit seven-wide.
                IconButton(onClick = onLog) { Icon(TablerIcons.Plus, stringResource(R.string.common_log), tint = AccentCoral) }
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(TablerIcons.DotsVertical, stringResource(R.string.recipes_cd_more_actions), tint = OnSurface.copy(0.5f))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, shape = RoundedCornerShape(CardRadius.CONTROL)) {
                    // Previously a recipe could only leave the app via the whole-database
                    // backup - no way to send just this one recipe to someone else.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_share)) },
                        leadingIcon = { Icon(TablerIcons.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, recipe.formatShareText())
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_notes)) },
                        leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditNotes() },
                    )
                    // Previously the only way to change a saved recipe's ingredient list
                    // was delete-and-recreate from scratch, or duplicate (which just
                    // copies the same list) - AddRecipeDialog already supports arbitrary
                    // add/remove of ingredients, this just reopens it pre-filled.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_edit_ingredients)) },
                        leadingIcon = { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditIngredients() },
                    )
                    // Previously servings only ever affected a one-off logged portion
                    // (LogRecipeDialog) - no way to permanently rescale the recipe itself.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_scale)) },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        onClick = { menuExpanded = false; onScale() },
                    )
                    // Templates/Recipes had no way to convert between the two -
                    // this saves a copy into the user's Saved Meal templates.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_save_as_template)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
                        onClick = { menuExpanded = false; onSaveAsTemplate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_rename)) },
                        leadingIcon = { Icon(TablerIcons.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    // Fork a variant (e.g. "Curry — spicy version") without altering the
                    // original - cloneOfficial() already proved this save-a-copy shape
                    // for official/starter recipes, previously never extended to a
                    // user's own saved list.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_duplicate)) },
                        leadingIcon = { Icon(TablerIcons.Copy, contentDescription = null) },
                        onClick = { menuExpanded = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(TablerIcons.X, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
        recipe.components.take(3).forEach { c ->
            Text(stringResource(R.string.templates_item_summary, c.productName, c.grams.toInt(), c.kcal.toInt()),
                style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
        }
        if (recipe.components.size > 3) Text(stringResource(R.string.templates_more_items, recipe.components.size - 3), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.4f))
        // Macro strip — protein/carbs/fat totals per serving were previously
        // invisible on the card; a user judging whether a recipe fits their
        // macros had to tap Log just to see the numbers.
        HorizontalDivider(color = OnSurface.copy(0.08f))
        // The header above shows whole-recipe totals (all servings) while this
        // strip divides by servings - previously nothing on the card indicated
        // that difference, so a multi-serving recipe read as internally
        // inconsistent (e.g. "1200 kcal" next to "15g protein" that's actually
        // 1/4 of the dish). Only shown when there's more than one serving to
        // avoid a redundant label on the common single-serving case.
        if (recipe.servings > 1) {
            Text(stringResource(R.string.recipes_macro_per_serving_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            val servings = recipe.servings.coerceAtLeast(1)
            @Composable fun MacroChip(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(value / servings).toInt()}g", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.45f))
                }
            }
            MacroChip(stringResource(R.string.macro_protein_abbr), recipe.totalProteinG, semanticGreen())
            MacroChip(stringResource(R.string.macro_carbs_abbr), recipe.totalCarbsG, AccentCoral)
            MacroChip(stringResource(R.string.macro_fat_abbr), recipe.totalFatG, Gold)
        }
        // Diet/allergen check previously only ever ran on scanned products -
        // a recipe built from ingredients the user's own profile forbids
        // (allergen or diet violation) had no warning anywhere in this screen.
        warning?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(16.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = semanticAmber())
            }
        }
        // findPairings()/PairingsDb.kt (Ahn et al. flavor-network data) was
        // already used for scanned products but never reached Recipes - the
        // exact same "what goes well with this" question applies here too.
        if (pairings.isNotEmpty()) {
            Text(
                stringResource(R.string.recipes_pairs_well_with, pairings.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
            )
        }
        // Prep notes/instructions - the recipe model previously had nowhere to
        // record how to actually make the dish, only its ingredient list.
        if (recipe.notes.isNotBlank()) {
            HorizontalDivider(color = OnSurface.copy(0.08f))
            Text(recipe.notes, style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
        }
    }
}
