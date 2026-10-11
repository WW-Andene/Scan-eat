package fr.scanneat.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot

/** Localized display label for a meal slot — shared by Diary, LogSheet, and MealPlan. */
@Composable
fun MealSlot.label(): String = when (this) {
    MealSlot.BREAKFAST -> stringResource(R.string.meal_breakfast)
    MealSlot.LUNCH     -> stringResource(R.string.meal_lunch)
    MealSlot.SNACK     -> stringResource(R.string.meal_snack)
    MealSlot.DINNER    -> stringResource(R.string.meal_dinner)
}
