package fr.scanneat.presentation.mealplan.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal val MEALS = listOf("breakfast", "lunch", "dinner", "snack")

@Composable
internal fun MealPlanDayCard(
    date: LocalDate,
    dayPlan: DayPlan,
    dayFmt: DateTimeFormatter,
    kcal: Int,
    suggestion: Recipe?,
    onDuplicateDay: () -> Unit,
    onClearDay: () -> Unit,
    onEditNote: (meal: String, text: String) -> Unit,
    onClearSlot: (meal: String) -> Unit,
    onAssign: (meal: String) -> Unit,
    onLogSlot: (meal: String, slot: MealPlanSlot) -> Unit,
    slotWarnings: Map<String, String>,
) {
    val isToday = date == LocalDate.now()
    // "Clear day" wiped all 4 meal slots on a single tap with no confirmation
    // or undo - every other whole-list-wiping action in the app (Grocery's
    // clear-checked) gates behind a confirm dialog first.
    var showClearConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    if (isToday) stringResource(R.string.mealplan_day_today, date.format(dayFmt)) else date.format(dayFmt),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isToday) AccentCoral else OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                if (kcal > 0) Text(stringResource(R.string.mealplan_day_kcal, kcal), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            }
            // MealPlanRepository.clearDay() previously had no UI entry
            // point - clearing a fully-planned day took one tap per
            // meal slot instead of one action for the whole day.
            if (MEALS.any { dayPlan[it] != null }) {
                Row {
                    // Repeating a day (e.g. "same lunch as today, next
                    // Tuesday") previously meant re-assigning each slot by
                    // hand - this copies the whole day onto the same
                    // weekday next week.
                    IconButton(onClick = onDuplicateDay) {
                        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.mealplan_duplicate_day), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.mealplan_clear_day), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        MEALS.forEach { meal ->
            val slot = dayPlan[meal]
            MealPlanRow(
                meal  = mealLabel(meal),
                slot  = slot,
                onEdit = { text -> onEditNote(meal, text) },
                onClear = { onClearSlot(meal) },
                onAssign = { onAssign(meal) },
                onLog = { s -> onLogSlot(meal, s) },
                warning = slotWarnings["$date|$meal"],
            )
        }
        if (suggestion != null) {
            HorizontalDivider(color = OnSurface.copy(0.07f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Icon(Icons.Rounded.Lightbulb, null, tint = AccentCoral, modifier = Modifier.size(14.dp))
                // Per-serving, not the recipe's whole-batch total - a single
                // planned meal slot only ever represents 1/servings of
                // suggestion.totalKcal/totalProteinG (same division logSlot()
                // and dayCalories both already apply), so a 4-serving recipe
                // previously showed 4x the real per-meal numbers here.
                val servings = suggestion.servings.coerceAtLeast(1)
                Text(
                    stringResource(
                        R.string.mealplan_gap_suggestion, suggestion.name,
                        (suggestion.totalKcal / servings).roundToInt(),
                        (suggestion.totalProteinG / servings).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f), modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.mealplan_clear_day_confirm_title),
            body = stringResource(R.string.mealplan_clear_day_confirm_body),
            confirmLabel = stringResource(R.string.mealplan_clear_day),
            onConfirm = { onClearDay(); showClearConfirm = false },
            onDismiss = { showClearConfirm = false },
        )
    }
}
