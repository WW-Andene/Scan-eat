package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.Product
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalTime
import kotlin.math.roundToInt

// ============================================================================
// LOG SHEET — portion selector bottom sheet
// Port of public/features/portion-panel.js
//
// Lets the user specify:
//   - Portion size in grams (defaults to product weight_g or 100 g)
//   - Meal slot (auto-selected from time of day)
// Then calls onConfirm(portionG, mealSlot).
// ============================================================================

/** Select the meal slot based on hour of day. Matches defaultMealForHour() in portion-panel.js. */
fun defaultMealForHour(hour: Int): MealSlot = when (hour) {
    in 5..9   -> MealSlot.BREAKFAST
    in 10..13 -> MealSlot.LUNCH
    in 14..17 -> MealSlot.SNACK
    in 18..22 -> MealSlot.DINNER
    else      -> MealSlot.SNACK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    product: Product,
    sheetState: SheetState,
    isLoading: Boolean = false,
    onConfirm: (portionG: Double, mealSlot: MealSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = LocalTime.now()
    var portionText by remember {
        val default = product.weightG?.takeIf { it in 1.0..2000.0 }?.toInt() ?: 100
        mutableStateOf(default.toString())
    }
    var selectedSlot by remember { mutableStateOf(defaultMealForHour(now.hour)) }

    val portionG = portionText.replace(',', '.').toDoubleOrNull()?.coerceIn(1.0, 2000.0)
    val kcalPreview = portionG?.let {
        (product.nutrition.energyKcal * it / 100.0).roundToInt()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = SurfaceVariant,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = Spacing.XL).padding(bottom = Spacing.XXL),
            verticalArrangement   = Arrangement.spacedBy(Spacing.L),
        ) {
            // Title
            Text(
                stringResource(R.string.logsheet_title),
                style      = MaterialTheme.typography.titleLarge,
                color      = OnSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                product.name,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(alpha = 0.6f),
            )

            // Portion input
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(
                    value         = portionText,
                    // Comma must survive the filter, not be stripped - line 54's
                    // .replace(',', '.') never gets a chance to run otherwise, so a
                    // French-locale "150,5" became the digits "1505" (10x the portion).
                    onValueChange = { portionText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label         = { Text(stringResource(R.string.logsheet_quantity_label)) },
                    suffix        = { Text("g", color = OnSurface.copy(0.5f)) },
                    singleLine    = true,
                    // Decimal, not Number - the field explicitly accepts a decimal portion
                    // (onValueChange above filters for '.'/',' too, and kcalPreview divides
                    // by 100.0), but KeyboardType.Number requests a plain digit-only numeric
                    // keyboard on many IMEs with no decimal-point key at all, making a
                    // fractional gram value (e.g. a small "12,5 g" garnish) unenterable.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(CardRadius.CONTROL),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentCoral,
                        unfocusedBorderColor = OnSurface.copy(0.2f),
                        focusedTextColor     = OnSurface,
                        unfocusedTextColor   = OnSurface,
                    ),
                )
                kcalPreview?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$it", style = MaterialTheme.typography.titleMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                        Text("kcal", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    }
                }
            }

            // Quick-pick presets
            val presetGrams = stringResource(R.string.logsheet_preset_grams, 100)
            val presetPackage = product.weightG?.takeIf { it in 10.0..2000.0 }?.let { w ->
                stringResource(R.string.logsheet_preset_package, w.toInt())
            }
            val preset200 = stringResource(R.string.logsheet_preset_grams, 200)
            val preset50  = stringResource(R.string.logsheet_preset_grams, 50)
            // 100g/package/200g/50g are the 4 core presets (package is the only
            // conditional one, so this is never more than 4). The half-package preset
            // that used to sit between package and 200g pushed this to 5 whenever a
            // package weight was known, and presets.take(4) below always kept the
            // first 4 in insertion order - silently dropping 50g, the one preset every
            // product has, in favor of the half-package convenience preset.
            val presets = buildList {
                add(Pair(presetGrams, 100.0))
                product.weightG?.takeIf { it in 10.0..2000.0 }?.let { w ->
                    presetPackage?.let { add(Pair(it, w)) }
                }
                add(Pair(preset200, 200.0))
                add(Pair(preset50, 50.0))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                presets.take(4).forEach { (label, g) ->
                    FilterChip(
                        selected  = portionG == g,
                        onClick   = { portionText = g.toInt().toString() },
                        label     = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCoral.copy(0.2f),
                            selectedLabelColor     = AccentCoral,
                            labelColor             = OnSurface.copy(0.7f),
                        ),
                    )
                }
            }

            // Meal slot selector
            Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                MealSlot.entries.forEach { slot ->
                    FilterChip(
                        selected  = selectedSlot == slot,
                        onClick   = { selectedSlot = slot },
                        label     = { Text(slot.label(), style = MaterialTheme.typography.labelSmall) },
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCoral.copy(0.2f),
                            selectedLabelColor     = AccentCoral,
                            labelColor             = OnSurface.copy(0.7f),
                        ),
                    )
                }
            }

            // Confirm button — ResultViewModel.log() sets LogState.Loading while writing,
            // but neither this button nor the sheet ever reflected that: the button stayed
            // enabled with no spinner, so a slow write left the user tapping what looked
            // like an unresponsive button (the VM's own re-entrancy guard prevented a
            // double-write, but gave no visible feedback that the tap had registered).
            ScanEatPrimaryButton(
                onClick  = {
                    val g = portionG ?: return@ScanEatPrimaryButton
                    onConfirm(g, selectedSlot)
                },
                enabled  = portionG != null && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(IconSize.Inline))
                } else {
                    Text(kcalPreview?.let { stringResource(R.string.logsheet_confirm_with_kcal, it) } ?: stringResource(R.string.logsheet_confirm_plain))
                }
            }
        }
    }
}
