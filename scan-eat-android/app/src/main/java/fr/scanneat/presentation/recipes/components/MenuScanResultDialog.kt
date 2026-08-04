package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.MenuDish
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant

/**
 * Renders IdentifyMenuRoute.kt's dishes — purely informational, unlike every other
 * ImportUiState success above: a menu dish has no barcode/ingredients to score and
 * nothing to save as a recipe or log to the diary (it's someone else's kitchen, not
 * a product the user owns), so this is a plain list with a close button only, styled
 * consistently with SuggestRecipesDialog's idea list (Surface row, name/description/
 * macro line) but with no onClick since there's nothing to pick into.
 */
@Composable
internal fun MenuScanResultDialog(dishes: List<MenuDish>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.recipes_menu_dialog_title), color = OnBackground) },
        text = {
            if (dishes.isEmpty()) {
                Text(stringResource(R.string.recipes_menu_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    items(dishes, key = { it.name }) { dish ->
                        Surface(
                            shape = RoundedCornerShape(CardRadius.CONTROL), color = OnBackground.copy(0.05f),
                            modifier = Modifier.fillMaxWidth()
                                .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
                                .clip(RoundedCornerShape(CardRadius.CONTROL)),
                            // app-audit §E5: matching the dialog list-row
                            // elevation established elsewhere - had none.
                            shadowElevation = 0.dp,
                        ) {
                            Column(modifier = Modifier.padding(Spacing.SM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(dish.name, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                                dish.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), maxLines = 3)
                                }
                                val kcal = dish.estimatedKcal
                                val protein = dish.proteinG
                                if (kcal != null || protein != null) {
                                    val macros = listOfNotNull(
                                        kcal?.let { stringResource(R.string.recipes_menu_kcal_value, it) },
                                        protein?.let { stringResource(R.string.recipes_menu_protein_value, it.toInt()) },
                                    ).joinToString(" · ")
                                    Text(macros, style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
