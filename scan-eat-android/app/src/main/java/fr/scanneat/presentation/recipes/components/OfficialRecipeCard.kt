package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.CardRadius

@Composable
internal fun OfficialRecipeCard(recipe: OfficialRecipe, isFrench: Boolean, warning: String?, pairings: List<String>, onLog: () -> Unit, onClone: () -> Unit) {
    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(CardRadius.CONTROL))) {
        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
                        IconButton(onClick = onLog) { Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentCoral) }
                        IconButton(onClick = onClone) { Icon(Icons.Default.ContentCopy, stringResource(R.string.recipes_official_clone_cd), tint = OnSurface.copy(0.5f)) }
                    }
                }
                recipe.ingredients.take(4).forEach { ing ->
                    Text("${ing.foodName} · ${ing.grams.toInt()} g", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                }
                warning?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(16.dp))
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
    }
}
