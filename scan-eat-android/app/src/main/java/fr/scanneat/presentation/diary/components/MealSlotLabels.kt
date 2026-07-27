package fr.scanneat.presentation.diary.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot

@Composable
internal fun MealSlot.shortLabel(): String = when (this) {
    MealSlot.BREAKFAST -> stringResource(R.string.diary_slot_short_breakfast)
    MealSlot.LUNCH     -> stringResource(R.string.diary_slot_short_lunch)
    MealSlot.SNACK     -> stringResource(R.string.diary_slot_short_snack)
    MealSlot.DINNER    -> stringResource(R.string.diary_slot_short_dinner)
}

/** Diary keeps its own (fuller) wording for breakfast — distinct from the abbreviated shared label. */
@Composable
internal fun MealSlot.diaryLabel(): String = when (this) {
    MealSlot.BREAKFAST -> stringResource(R.string.diary_meal_breakfast)
    MealSlot.LUNCH     -> stringResource(R.string.meal_lunch)
    MealSlot.SNACK     -> stringResource(R.string.meal_snack)
    MealSlot.DINNER    -> stringResource(R.string.meal_dinner)
}
