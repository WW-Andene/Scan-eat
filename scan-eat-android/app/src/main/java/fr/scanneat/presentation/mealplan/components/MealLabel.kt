package fr.scanneat.presentation.mealplan.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R

@Composable
internal fun mealLabel(key: String): String = when (key) {
    "breakfast" -> stringResource(R.string.meal_breakfast)
    "lunch"     -> stringResource(R.string.meal_lunch)
    "dinner"    -> stringResource(R.string.meal_dinner)
    "snack"     -> stringResource(R.string.meal_snack)
    else        -> key
}
