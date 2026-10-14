package fr.scanneat.presentation.scan.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.parseIngredients
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.presentation.ui.theme.*

/** Parses a user-typed number, accepting "1,5" as well as "1.5". Null when invalid or negative. */
internal fun parseReviewNumber(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }

/** Applies the reviewed fields to [p]; ingredients are re-parsed only when the text changed. */
internal fun applyOcrReview(p: Product, name: String, values: Map<String, Double>, ingredientsText: String, originalIngredientsText: String): Product {
    val n = p.nutrition
    val nutrition: NutritionPer100g = n.copy(
        energyKcal = values["kcal"] ?: n.energyKcal,
        fatG = values["fat"] ?: n.fatG,
        saturatedFatG = values["satfat"] ?: n.saturatedFatG,
        carbsG = values["carbs"] ?: n.carbsG,
        sugarsG = values["sugars"] ?: n.sugarsG,
        fiberG = values["fiber"] ?: n.fiberG,
        proteinG = values["protein"] ?: n.proteinG,
        saltG = values["salt"] ?: n.saltG,
    )
    val ingredients = if (ingredientsText.trim() == originalIngredientsText.trim()) p.ingredients else parseIngredients(ingredientsText)
    return p.copy(name = name.trim().ifBlank { p.name }, nutrition = nutrition, ingredients = ingredients)
}

/**
 * Roadmap C16: a label read by OCR (one column off, "1,5" read as "15") used
 * to be scored with the same confidence as an Open Food Facts record. The
 * user now checks/corrects the per-100 g values and ingredients first.
 */
@Composable
internal fun OcrReviewDialog(product: Product, onConfirm: (Product) -> Unit, onCancel: () -> Unit) {
    val n = product.nutrition
    fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
    val originalIngredients = remember(product) { product.ingredients.joinToString(", ") { it.name } }
    var name by remember(product) { mutableStateOf(product.name) }
    var ingredients by remember(product) { mutableStateOf(originalIngredients) }
    val fields = listOf(
        "kcal" to R.string.ocr_review_kcal, "fat" to R.string.ocr_review_fat, "satfat" to R.string.ocr_review_satfat,
        "carbs" to R.string.ocr_review_carbs, "sugars" to R.string.ocr_review_sugars, "fiber" to R.string.ocr_review_fiber,
        "protein" to R.string.ocr_review_protein, "salt" to R.string.ocr_review_salt,
    )
    val initial = mapOf("kcal" to n.energyKcal, "fat" to n.fatG, "satfat" to n.saturatedFatG, "carbs" to n.carbsG,
        "sugars" to n.sugarsG, "fiber" to n.fiberG, "protein" to n.proteinG, "salt" to n.saltG)
    val texts = remember(product) { mutableStateMapOf<String, String>().apply { initial.forEach { (k, v) -> put(k, fmt(v)) } } }
    val allValid = texts.values.all { parseReviewNumber(it) != null }

    GlassAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.ocr_review_title), color = OnBackground) },
        text = {
            Column(Modifier.heightIn(max = 512.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.ocr_review_body), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.7f))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ocr_review_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                fields.forEach { (key, labelRes) ->
                    val t = texts[key].orEmpty()
                    OutlinedTextField(
                        value = t, onValueChange = { texts[key] = it },
                        label = { Text(stringResource(labelRes)) }, singleLine = true,
                        isError = parseReviewNumber(t) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(value = ingredients, onValueChange = { ingredients = it }, label = { Text(stringResource(R.string.ocr_review_ingredients)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = allValid, onClick = {
                val values = texts.mapNotNull { (k, v) -> parseReviewNumber(v)?.let { k to it } }.toMap()
                onConfirm(applyOcrReview(product, name, values, ingredients, originalIngredients))
            }) { Text(stringResource(R.string.ocr_review_confirm)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.ocr_review_cancel)) } },
    )
}
