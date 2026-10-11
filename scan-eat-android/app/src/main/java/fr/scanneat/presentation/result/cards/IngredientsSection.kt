package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SeparatorLight
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.util.formatDecimal

private const val COLLAPSED_COUNT = 8

/**
 * The parsed ingredient list, in label order. Every scan parses it (scoring
 * reads it for the processing/additive/integrity pillars) but the result
 * screen only ever showed the pillar scores, never the list itself.
 */
@Composable
internal fun IngredientsSection(ingredients: List<Ingredient>) {
    if (ingredients.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.result_ingredients_title), style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.S))
        val shown = if (expanded) ingredients else ingredients.take(COLLAPSED_COUNT)
        shown.forEach { ing ->
            val detail = listOfNotNull(
                ing.percentage?.let { "${it.formatDecimal()} %" },
                ing.eNumber?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XS),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ing.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall,
                    color = OnBackground.copy(0.8f), modifier = Modifier.weight(1f))
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = if (ing.category == IngredientCategory.ADDITIVE) AccentCoral else OnBackground,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = Spacing.S))
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = SeparatorLight)
        }
        if (ingredients.size > COLLAPSED_COUNT) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) stringResource(R.string.result_show_less) else stringResource(R.string.result_show_more),
                    style = MaterialTheme.typography.labelMedium, color = AccentCoral)
            }
        }
    }
}
