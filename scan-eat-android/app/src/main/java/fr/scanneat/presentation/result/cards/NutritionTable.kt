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
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SeparatorLight
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.util.formatDecimal

@Composable
internal fun NutritionTable(nutrition: NutritionPer100g) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.result_nutrition_title), style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.S))
        NRow(stringResource(R.string.result_nutri_energy), "${nutrition.energyKcal.toInt()} kcal")
        NRow(stringResource(R.string.result_nutri_fat), "${fmt1(nutrition.fatG)} g")
        NRow(stringResource(R.string.result_nutri_saturated), "${fmt1(nutrition.saturatedFatG)} g")
        NRow(stringResource(R.string.result_nutri_carbs), "${fmt1(nutrition.carbsG)} g")
        NRow(stringResource(R.string.result_nutri_sugars), "${fmt1(nutrition.sugarsG)} g")
        NRow(stringResource(R.string.result_nutri_fiber), "${fmt1(nutrition.fiberG)} g")
        NRow(stringResource(R.string.result_nutri_protein), "${fmt1(nutrition.proteinG)} g")
        NRow(stringResource(R.string.result_nutri_salt), "${fmt1(nutrition.saltG)} g")
        if (expanded) {
            nutrition.transFatG?.let { NRow(stringResource(R.string.result_nutri_transfat), "${fmt1(it)} g") }
            // Fully parsed/merged from OFF (OffMapper.kt) but previously never displayed
            // anywhere - the always-visible row above only ever showed saltG.
            nutrition.sodiumMg?.let { NRow(stringResource(R.string.result_nutri_sodium), "${fmt1(it)} mg") }
            nutrition.ironMg?.let { NRow(stringResource(R.string.result_nutri_iron), "${fmt1(it)} mg") }
            nutrition.calciumMg?.let { NRow(stringResource(R.string.result_nutri_calcium), "${fmt1(it)} mg") }
            nutrition.vitDUg?.let { NRow(stringResource(R.string.result_nutri_vitd), "${fmt1(it)} µg") }
            nutrition.b12Ug?.let { NRow(stringResource(R.string.result_nutri_vitb12), "${fmt1(it)} µg") }
            nutrition.vitCMg?.let { NRow(stringResource(R.string.result_nutri_vitc), "${fmt1(it)} mg") }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) stringResource(R.string.result_show_less) else stringResource(R.string.result_show_more), style = MaterialTheme.typography.labelMedium, color = AccentCoral)
        }
    }
}

// OFF-sourced doubles (e.g. sodium x1000 -> mg, cl/dl conversions) can carry
// float-imprecision tails (12.339999999999999); round to 1 decimal so every
// row displays with consistent precision, matching how energyKcal is rounded.
private fun fmt1(value: Double): String = value.formatDecimal()

@Composable
private fun NRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OnBackground, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(thickness = 0.5.dp, color = SeparatorLight)
}
