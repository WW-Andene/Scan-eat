package fr.scanneat.presentation.templates.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.result.HintPanel
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun TemplateCard(
    template: MealTemplate,
    hints: ProductHints,
    warning: String?,
    onToggleFavorite: () -> Unit,
    onManageItems: () -> Unit,
    onLog: () -> Unit,
    onSaveAsRecipe: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val hintsRiskCount = hints.risks.size + hints.conditionRisks.size
    val hintsHaveRisks = hintsRiskCount > 0
    var showHints by remember { mutableStateOf(false) }
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.templates_summary, template.totalKcal, template.items.size, template.meal.name.lowercase()),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
            }
            Row {
                // Templates had no equivalent to Recipes'/ScanHistory's favorite
                // pin at all - same Star/StarBorder pattern as RecipeCard.
                // Left at IconButton's default 48dp touch target (Material/WCAG
                // minimum) - a UI/UX audit found 6 icon-sized controls competing
                // for width in this row, each forced below the 48dp minimum.
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (template.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        stringResource(if (template.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                        tint = if (template.favorite) Gold else OnSurface.copy(0.3f),
                    )
                }
                // Previously the only way a template could get items was
                // never - create() always built one with an empty list and
                // there was no UI anywhere to add to it afterward.
                IconButton(onClick = onManageItems) {
                    Icon(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.templates_manage_items_cd), tint = Teal)
                }
                IconButton(onClick = onLog, enabled = template.items.isNotEmpty()) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.common_log), tint = if (template.items.isNotEmpty()) AccentCoral else OnSurface.copy(0.25f))
                }
                // Save-as-Recipe/Rename/Delete moved into an overflow menu -
                // Favorite/Manage-items/Log are the frequent actions and stay
                // directly visible at full size.
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.recipes_cd_more_actions), tint = OnSurface.copy(0.5f))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, shape = RoundedCornerShape(CardRadius.CONTROL)) {
                    // The "💡 Bon à savoir" hint panel was previously reachable
                    // only from a scanned product's Result screen - a template's
                    // items already carry real per-100g nutrition (see
                    // MealTemplateRepository.nutritionPer100g), so the same
                    // benefits/risks/facts apply. Placed in the overflow menu
                    // rather than as a 5th always-visible icon, since this row
                    // already sits at the app's established 4-icon max density
                    // (Favorite/Manage-items/Log/More).
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hint_panel_title)) },
                        leadingIcon = { Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = if (hintsHaveRisks) semanticRed() else semanticAmber()) },
                        trailingIcon = { if (hintsHaveRisks) Badge(containerColor = semanticRed()) { Text("$hintsRiskCount") } },
                        onClick = { menuExpanded = false; showHints = true },
                    )
                    HorizontalDivider(color = OnBackground.copy(0.08f))
                    // Templates/Recipes had no way to convert between the two -
                    // this saves a copy into the user's Recipes library.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.templates_cd_save_as_recipe)) },
                        leadingIcon = { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null) },
                        enabled = template.items.isNotEmpty(),
                        onClick = { menuExpanded = false; onSaveAsRecipe() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    // Fork into an independent copy (e.g. "Breakfast" -> "Breakfast
                    // (weekend)") - same duplicate action RecipeCard already has.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipes_cd_duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { menuExpanded = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
        if (showHints) {
            HintPanel(hints = hints, onDismiss = { showHints = false })
        }
        template.items.take(3).forEach { item ->
            Text(stringResource(R.string.templates_item_summary, item.productName, item.grams.toInt(), item.kcal.toInt()),
                style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
        }
        if (template.items.size > 3) {
            Text(stringResource(R.string.templates_more_items, template.items.size - 3), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.4f))
        }
        // Diet/allergen check previously only ever ran on Recipes/Grocery -
        // a template built from ingredients the user's own profile forbids
        // had no warning anywhere in this screen, despite being logged
        // repeatedly.
        warning?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(16.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = semanticAmber())
            }
        }
        // Macro summary — protein/carbs/fat totals were only available
        // in the log dialog; here on the card a user had no quick read
        // on whether the template fits their current macro targets.
        if (template.items.isNotEmpty()) {
            HorizontalDivider(color = OnSurface.copy(0.07f))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                @Composable fun M(label: String, v: Int, color: androidx.compose.ui.graphics.Color) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.45f))
                        Text("${v}g", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
                M(stringResource(R.string.macro_protein_abbr), template.totalProteinG, semanticGreen())
                M(stringResource(R.string.macro_carbs_abbr), template.totalCarbsG, AccentCoral)
                M(stringResource(R.string.macro_fat_abbr), template.totalFatG, Gold)
            }
        }
    }
}
