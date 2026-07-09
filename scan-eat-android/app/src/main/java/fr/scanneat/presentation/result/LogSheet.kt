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
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.Product
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalTime

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
    onConfirm: (portionG: Double, mealSlot: MealSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = LocalTime.now()
    var portionText by remember {
        val default = product.weightG?.takeIf { it in 1.0..2000.0 }?.toInt() ?: 100
        mutableStateOf(default.toString())
    }
    var selectedSlot by remember { mutableStateOf(defaultMealForHour(now.hour)) }

    val portionG = portionText.toDoubleOrNull()?.coerceAtLeast(1.0)
    val kcalPreview = portionG?.let {
        (product.nutrition.energyKcal * it / 100.0).toInt()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = SurfaceVariant,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement   = Arrangement.spacedBy(16.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = portionText,
                    onValueChange = { portionText = it.filter { c -> c.isDigit() || c == '.' } },
                    label         = { Text(stringResource(R.string.logsheet_quantity_label)) },
                    suffix        = { Text("g", color = OnSurface.copy(0.5f)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentGreen,
                        unfocusedBorderColor = OnSurface.copy(0.2f),
                        focusedTextColor     = OnSurface,
                        unfocusedTextColor   = OnSurface,
                    ),
                )
                kcalPreview?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$it", style = MaterialTheme.typography.titleMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
                        Text("kcal", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    }
                }
            }

            // Quick-pick presets
            val presetGrams = stringResource(R.string.logsheet_preset_grams, 100)
            val presetPackage = product.weightG?.takeIf { it in 10.0..2000.0 }?.let { w ->
                stringResource(R.string.logsheet_preset_package, w.toInt())
            }
            val presetHalf = product.weightG?.takeIf { it in 40.0..2000.0 }?.let { w ->
                stringResource(R.string.logsheet_preset_half, (w / 2).toInt())
            }
            val preset200 = stringResource(R.string.logsheet_preset_grams, 200)
            val preset50  = stringResource(R.string.logsheet_preset_grams, 50)
            val presets = buildList {
                add(Pair(presetGrams, 100.0))
                product.weightG?.takeIf { it in 10.0..2000.0 }?.let { w ->
                    presetPackage?.let { add(Pair(it, w)) }
                    if (w >= 40) presetHalf?.let { add(Pair(it, w / 2)) }
                }
                add(Pair(preset200, 200.0))
                add(Pair(preset50, 50.0))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.take(4).forEach { (label, g) ->
                    FilterChip(
                        selected  = portionG == g,
                        onClick   = { portionText = g.toInt().toString() },
                        label     = { Text(label, fontSize = 11.sp) },
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen.copy(0.2f),
                            selectedLabelColor     = AccentGreen,
                            labelColor             = OnSurface.copy(0.7f),
                        ),
                    )
                }
            }

            // Meal slot selector
            Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { slot ->
                    FilterChip(
                        selected  = selectedSlot == slot,
                        onClick   = { selectedSlot = slot },
                        label     = { Text(slot.label(), fontSize = 11.sp) },
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen.copy(0.2f),
                            selectedLabelColor     = AccentGreen,
                            labelColor             = OnSurface.copy(0.7f),
                        ),
                    )
                }
            }

            // Confirm button
            Button(
                onClick  = {
                    val g = portionG ?: return@Button
                    onConfirm(g, selectedSlot)
                },
                enabled  = portionG != null,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text(
                    kcalPreview?.let { stringResource(R.string.logsheet_confirm_with_kcal, it) } ?: stringResource(R.string.logsheet_confirm_plain),
                    color      = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
