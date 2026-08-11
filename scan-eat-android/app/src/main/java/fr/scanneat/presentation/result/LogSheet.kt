package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.Product
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalTime
import kotlin.math.roundToInt

// ============================================================================
// LOG SHEET — portion selector popup
// Port of public/features/portion-panel.js
//
// Lets the user specify:
//   - Portion size in grams (defaults to product weight_g or 100 g)
//   - Meal slot (auto-selected from time of day)
// Then calls onConfirm(portionG, mealSlot).
//
// User-reported: as a ModalBottomSheet, tapping "Logger" showed only the
// scrim (ambient dimming) with no visible sheet panel, on every screen that
// opens it (Dashboard, Scan's Result screen) - and this was the app's only
// ModalBottomSheet call site, so there was no sibling instance already
// proving that primitive actually renders in this Compose BOM version. An
// explicit sheetState.show() (a first attempt at fixing this) didn't resolve
// it either. First rebuilt on GlassAlertDialog (ScanEatCard as the dialog
// body), then user-reported again as "not standard, too transparent" -
// ScanEatCard's fill is deliberately translucent for the app's main
// scrolling surfaces (see its own doc comment), which reads as too see-
// through for a modal popup at this size. Now a plain Material3
// AlertDialog with a near-opaque containerColor, the same "standard dialog"
// pattern PriceInputDialog (PriceEntryCard.kt) already uses.
// ============================================================================

/** Select the meal slot based on hour of day. Matches defaultMealForHour() in portion-panel.js. */
fun defaultMealForHour(hour: Int): MealSlot = when (hour) {
    in 5..9   -> MealSlot.BREAKFAST
    in 10..13 -> MealSlot.LUNCH
    in 14..17 -> MealSlot.SNACK
    in 18..22 -> MealSlot.DINNER
    else      -> MealSlot.SNACK
}

/**
 * User-reported: "Logger" always counted a scanned product as eaten today
 * (consumptionRepo.log()), with no way to just record a price or stock the
 * pantry from a product not actually consumed yet (e.g. right after a
 * shopping trip). REPAS is this dialog's original, sole behavior; DEPENSES/
 * GARDE_MANGER let the same confirm action also (or only) log a price
 * (PriceEntryCard's own fields, previously reachable only via its separate
 * "+" button) and/or stock the pantry (previously reachable only via the
 * bookmark/"Save to..." popup's own Garde-manger checkbox) without implying
 * the product was eaten.
 */
enum class LogDestination { REPAS, DEPENSES, GARDE_MANGER }

@Composable
fun LogSheet(
    product: Product,
    isLoading: Boolean = false,
    onConfirm: (portionG: Double, mealSlot: MealSlot) -> Unit,
    onDismiss: () -> Unit,
    // Optional multi-destination extension - only ResultScreen (the one
    // screen where price-logging and pantry-stocking already exist as
    // separate actions worth decoupling from "eaten today") opts in. Every
    // other LogSheet call site (FoodSearch/Diary/Dashboard) is unaffected -
    // showDestinationPicker defaults false, so onConfirm(portionG, mealSlot)
    // above still fires exactly as before, always implicitly "log to Repas".
    showDestinationPicker: Boolean = false,
    onConfirmWithDestinations: ((
        portionG: Double, mealSlot: MealSlot, destinations: Set<LogDestination>,
        priceEuros: Double?, weightG: Double?,
    ) -> Unit)? = null,
) {
    val now = LocalTime.now()
    var portionText by remember {
        val default = product.weightG?.takeIf { it in 1.0..2000.0 }?.toInt() ?: 100
        mutableStateOf(default.toString())
    }
    var selectedSlot by remember { mutableStateOf(defaultMealForHour(now.hour)) }
    var destinations by remember { mutableStateOf(setOf(LogDestination.REPAS)) }
    var priceText by remember { mutableStateOf("") }
    var weightText by remember {
        val default = product.weightG?.takeIf { it in 1.0..2000.0 }
        mutableStateOf(default?.toInt()?.toString() ?: "")
    }

    val portionG = portionText.replace(',', '.').toDoubleOrNull()?.coerceIn(1.0, 2000.0)
    val kcalPreview = portionG?.let {
        (product.nutrition.energyKcal * it / 100.0).roundToInt()
    }
    val priceEuros = priceText.replace(',', '.').toDoubleOrNull()
    val weightG = weightText.replace(',', '.').toDoubleOrNull()
    // At least one destination checked, and each checked destination's own
    // required field(s) filled in - REPAS needs nothing beyond the portion
    // already required below; DEPENSES needs a valid price.
    val destinationsValid = destinations.isNotEmpty() &&
        (LogDestination.DEPENSES !in destinations || (priceEuros != null && priceEuros > 0))

    val shape = RoundedCornerShape(CardRadius.PROMINENT)
    AlertDialog(
        onDismissRequest = onDismiss,
        // User-requested: one standard glass config app-wide - see
        // StandardCardAlpha's own doc comment (ScanEatCard.kt).
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(shape),
        shape = shape,
        title = {
            Column {
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
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.L)) {
                // Portion input
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    OutlinedTextField(
                        value         = portionText,
                        // Comma must survive the filter, not be stripped - line 54's
                        // .replace(',', '.') never gets a chance to run otherwise, so a
                        // French-locale "150,5" became the digits "1505" (10x the portion).
                        onValueChange = { portionText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label         = { Text(stringResource(R.string.logsheet_quantity_label)) },
                        suffix        = { Text(stringResource(R.string.common_unit_grams), color = OnSurface.copy(0.5f)) },
                        singleLine    = true,
                        // Decimal, not Number - the field explicitly accepts a decimal portion
                        // (onValueChange above filters for '.'/',' too, and kcalPreview divides
                        // by 100.0), but KeyboardType.Number requests a plain digit-only numeric
                        // keyboard on many IMEs with no decimal-point key at all, making a
                        // fractional gram value (e.g. a small "12,5 g" garnish) unenterable.
                        //
                        // UX friction pass: this is the single most-repeated dialog in the
                        // app (every food log passes through it - see this file's own header
                        // comment) yet had no imeAction/keyboardActions at all, so pressing
                        // the keyboard's Done/Enter key did nothing - the user always had to
                        // reach down and tap "Logger" by hand even after finishing typing.
                        // Same fix already applied to GroceryQuickAddRow's own entry field.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            portionG?.let { g ->
                                if (showDestinationPicker && onConfirmWithDestinations != null) {
                                    if (destinationsValid) onConfirmWithDestinations(g, selectedSlot, destinations, priceEuros, weightG)
                                } else onConfirm(g, selectedSlot)
                            }
                        }),
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(CardRadius.CONTROL),
                        // app-audit §E6: focusedBorderColor was AccentCoral but cursorColor/
                        // focusedLabelColor weren't set, so both fell back to Material's
                        // default Gold primary - the same coral/gold clash fixed on
                        // scanEatTextFieldColors() itself, on the single most-used input
                        // in the app (every food log passes through this portion field).
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AccentCoral,
                            unfocusedBorderColor = OnSurface.copy(0.2f),
                            focusedTextColor     = OnSurface,
                            unfocusedTextColor   = OnSurface,
                            cursorColor          = AccentCoral,
                            focusedLabelColor    = AccentCoral,
                        ),
                    )
                    kcalPreview?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$it", style = MaterialTheme.typography.titleMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.common_unit_kcal), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
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

                // User-requested: decouple "eaten today" from "log a price" /
                // "stock the pantry" - see LogDestination's own doc comment.
                if (showDestinationPicker) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text(stringResource(R.string.logsheet_destinations_label), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.7f))
                        DestinationCheckboxRow(LogDestination.REPAS, stringResource(R.string.logsheet_destination_repas), destinations) { destinations = it }
                        DestinationCheckboxRow(LogDestination.DEPENSES, stringResource(R.string.logsheet_destination_depenses), destinations) { destinations = it }
                        DestinationCheckboxRow(LogDestination.GARDE_MANGER, stringResource(R.string.logsheet_destination_garde_manger), destinations) { destinations = it }
                    }
                    if (LogDestination.DEPENSES in destinations) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            OutlinedTextField(
                                value = priceText, onValueChange = { priceText = it },
                                label = { Text(stringResource(R.string.result_price_field_euros)) }, singleLine = true,
                                isError = priceText.isNotBlank() && (priceEuros == null || priceEuros <= 0),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(CardRadius.CONTROL),
                                colors = scanEatTextFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = weightText, onValueChange = { weightText = it },
                                label = { Text(stringResource(R.string.result_price_field_weight)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(CardRadius.CONTROL),
                                colors = scanEatTextFieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Meal slot selector - meaningless (and hidden) when Repas isn't
                // even one of the checked destinations.
                if (!showDestinationPicker || LogDestination.REPAS in destinations) {
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
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.common_cancel), color = TextSecondary)
            }
        },
        confirmButton = {
            // ResultViewModel.log() sets LogState.Loading while writing, but neither
            // this button nor the dialog ever reflected that - the button stayed
            // enabled with no spinner, so a slow write left the user tapping what
            // looked like an unresponsive button (the VM's own re-entrancy guard
            // prevented a double-write, but gave no visible feedback that the tap
            // had registered).
            TextButton(
                onClick  = {
                    portionG?.let { g ->
                        if (showDestinationPicker && onConfirmWithDestinations != null) {
                            if (destinationsValid) onConfirmWithDestinations(g, selectedSlot, destinations, priceEuros, weightG)
                        } else onConfirm(g, selectedSlot)
                    }
                },
                enabled  = portionG != null && !isLoading && (!showDestinationPicker || destinationsValid),
            ) {
                if (isLoading) {
                    ScanEatLoadingIndicator(color = LocalContentColor.current)
                } else {
                    Text(
                        kcalPreview?.let { stringResource(R.string.logsheet_confirm_with_kcal, it) } ?: stringResource(R.string.logsheet_confirm_plain),
                        color = AccentCoral,
                    )
                }
            }
        },
    )
}

@Composable
private fun DestinationCheckboxRow(
    destination: LogDestination,
    label: String,
    selected: Set<LogDestination>,
    onChange: (Set<LogDestination>) -> Unit,
) {
    val checked = destination in selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked, role = Role.Checkbox,
                onValueChange = { onChange(if (checked) selected - destination else selected + destination) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = AccentCoral))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
    }
}
