package fr.scanneat.presentation.hydration.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.ML_TO_FLOZ
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.dispVolume
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticBlue
import kotlin.math.roundToInt

/** Common container sizes, in mL - a "small cup" through a "large bottle". */
private val CONTAINER_PRESETS_ML = listOf(330, 500, 1000)

/**
 * User-requested: "develop the tool" for Hydration - one-tap logging for a
 * real container (bottle/cup) instead of only the ring's fixed 250 mL glass
 * steps. See HydrationViewModel.addAmount's own doc comment. Labels use
 * dispVolume() so an imperial user sees "17 fl oz" etc, same unit the rest
 * of this screen already displays in.
 */
@Composable
internal fun HydrationQuickAddRow(useImperial: Boolean, onAdd: (Int) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        items(CONTAINER_PRESETS_ML, key = { it }) { ml ->
            SuggestionChip(
                onClick = { onAdd(ml) },
                icon = { Icon(TablerIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Micro)) },
                label = { Text(dispVolume(ml, useImperial), style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(CardRadius.BADGE),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = semanticBlue().copy(alpha = 0.12f),
                    labelColor = semanticBlue(),
                    iconContentColor = semanticBlue(),
                ),
                border = null,
            )
        }
        item {
            SuggestionChip(
                onClick = { showCustom = true },
                label = { Text(stringResource(R.string.hydration_quick_add_custom), style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(CardRadius.BADGE),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = SurfaceVariant.copy(alpha = 0.6f),
                    labelColor = OnBackground.copy(0.7f),
                ),
                border = null,
            )
        }
    }
    if (showCustom) {
        HydrationCustomAmountDialog(
            useImperial = useImperial,
            onConfirm = { ml -> onAdd(ml); showCustom = false },
            onDismiss = { showCustom = false },
        )
    }
}

/** Same native-unit-edit/convert-on-confirm shape as HydrationHistoryEditDialog. */
@Composable
private fun HydrationCustomAmountDialog(useImperial: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val maxNative = if (useImperial) (5000 * ML_TO_FLOZ).roundToInt() else 5000
    val typed = text.toIntOrNull()?.takeIf { it in 1..maxNative }
    val ml = typed?.let { if (useImperial) (it / ML_TO_FLOZ).roundToInt() else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.hydration_quick_add_custom_title), color = OnBackground) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(if (useImperial) R.string.hydration_history_edit_label_imperial else R.string.hydration_history_edit_label)) },
                singleLine = true,
                isError = text.isNotBlank() && typed == null,
                // UX friction pass: no imeAction meant the keyboard's Done key
                // did nothing, forcing a reach back to "Ajouter" after typing.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { ml?.let(onConfirm) }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { ml?.let(onConfirm) }, enabled = ml != null) {
                Text(stringResource(R.string.common_add), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
