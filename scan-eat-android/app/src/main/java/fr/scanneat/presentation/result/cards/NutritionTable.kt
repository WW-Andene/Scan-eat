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
import fr.scanneat.presentation.ui.theme.AccentGreen
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SeparatorLight

@Composable
internal fun NutritionTable(nutrition: NutritionPer100g) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.result_nutrition_title), style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NRow(stringResource(R.string.result_nutri_energy), "${nutrition.energyKcal.toInt()} kcal")
        NRow(stringResource(R.string.result_nutri_fat), "${nutrition.fatG} g")
        NRow(stringResource(R.string.result_nutri_saturated), "${nutrition.saturatedFatG} g")
        NRow(stringResource(R.string.result_nutri_carbs), "${nutrition.carbsG} g")
        NRow(stringResource(R.string.result_nutri_sugars), "${nutrition.sugarsG} g")
        NRow(stringResource(R.string.result_nutri_fiber), "${nutrition.fiberG} g")
        NRow(stringResource(R.string.result_nutri_protein), "${nutrition.proteinG} g")
        NRow(stringResource(R.string.result_nutri_salt), "${nutrition.saltG} g")
        if (expanded) {
            nutrition.transFatG?.let { NRow(stringResource(R.string.result_nutri_transfat), "${it} g") }
            nutrition.ironMg?.let { NRow(stringResource(R.string.result_nutri_iron), "${it} mg") }
            nutrition.calciumMg?.let { NRow(stringResource(R.string.result_nutri_calcium), "${it} mg") }
            nutrition.vitDUg?.let { NRow(stringResource(R.string.result_nutri_vitd), "${it} µg") }
            nutrition.b12Ug?.let { NRow(stringResource(R.string.result_nutri_vitb12), "${it} µg") }
            nutrition.vitCMg?.let { NRow(stringResource(R.string.result_nutri_vitc), "${it} mg") }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) stringResource(R.string.result_show_less) else stringResource(R.string.result_show_more), style = MaterialTheme.typography.labelMedium, color = AccentGreen)
        }
    }
}

@Composable
private fun NRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OnBackground, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(thickness = 0.5.dp, color = SeparatorLight)
}
