package fr.scanneat.presentation.recipes.components

import compose.icons.tablericons.Plus
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Copy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.result.HintIconButton
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize

@Composable
internal fun OfficialRecipeCard(recipe: OfficialRecipe, isFrench: Boolean, warning: String?, pairings: List<String>, hints: ProductHints, onLog: () -> Unit, onClone: () -> Unit) {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isFrench) recipe.nameFr else recipe.nameEn, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.recipes_summary, recipe.totalKcal.toInt(), recipe.ingredients.size, recipe.totalGrams.toInt()),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f),
                )
            }
            Row {
                // Left at IconButton's default 48dp touch target (Material/WCAG
                // minimum) - a UI/UX audit found this row forcing 36dp.
                HintIconButton(hints = hints)
                IconButton(onClick = onLog) { Icon(TablerIcons.Plus, stringResource(R.string.common_log), tint = AccentCoral) }
                IconButton(onClick = onClone) { Icon(TablerIcons.Copy, stringResource(R.string.recipes_official_clone_cd), tint = OnSurface.copy(0.5f)) }
            }
        }
        recipe.ingredients.take(4).forEach { ing ->
            Text("${ing.foodName} · ${ing.grams.toInt()} g", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
        }
        warning?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(IconSize.Small))
                Text(it, style = MaterialTheme.typography.bodySmall, color = semanticAmber())
            }
        }
        if (pairings.isNotEmpty()) {
            Text(
                stringResource(R.string.recipes_pairs_well_with, pairings.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
            )
        }
    }
}
