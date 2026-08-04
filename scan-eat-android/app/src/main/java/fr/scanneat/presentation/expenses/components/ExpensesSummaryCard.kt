package fr.scanneat.presentation.expenses.components

import compose.icons.tablericons.Calendar
import compose.icons.TablerIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

internal enum class ExpensesSummaryMode { DAY, WEEK, MONTH }

/** Rounds a euro amount to whole cents for an exact over/under-budget comparison -
 *  see ExpensesWeekCard's own comment on why a raw Double `>` isn't safe here. */
internal fun centsOf(euros: Double): Long = Math.round(euros * 100)

/** Was falling back to the raw enum key with underscores swapped for spaces
 *  (e.g. "Dairy products"-style English leaking into an otherwise fully French
 *  screen) since no per-category string table existed - this breakdown row is
 *  the only place ProductCategory needs a user-facing label at all. */
@Composable
internal fun ProductCategory.displayLabel(): String = stringResource(
    when (this) {
        ProductCategory.SANDWICH          -> R.string.category_sandwich
        ProductCategory.READY_MEAL        -> R.string.category_ready_meal
        ProductCategory.SOUP              -> R.string.category_soup
        ProductCategory.BREAD             -> R.string.category_bread
        ProductCategory.BREAKFAST_CEREAL  -> R.string.category_breakfast_cereal
        ProductCategory.YOGURT            -> R.string.category_yogurt
        ProductCategory.CHEESE            -> R.string.category_cheese
        ProductCategory.PROCESSED_MEAT    -> R.string.category_processed_meat
        ProductCategory.FRESH_MEAT        -> R.string.category_fresh_meat
        ProductCategory.FISH              -> R.string.category_fish
        ProductCategory.SNACK_SWEET       -> R.string.category_snack_sweet
        ProductCategory.SNACK_SALTY       -> R.string.category_snack_salty
        ProductCategory.BEVERAGE_SOFT     -> R.string.category_beverage_soft
        ProductCategory.BEVERAGE_JUICE    -> R.string.category_beverage_juice
        ProductCategory.BEVERAGE_WATER    -> R.string.category_beverage_water
        ProductCategory.CONDIMENT         -> R.string.category_condiment
        ProductCategory.OIL_FAT           -> R.string.category_oil_fat
        ProductCategory.OTHER             -> R.string.category_other
    },
)

@Composable
internal fun ExpensesWeekCard(
    mode: ExpensesSummaryMode,
    onModeChange: (ExpensesSummaryMode) -> Unit,
    dayTotal: Double,
    weekTotal: Double,
    monthTotal: Double,
    budgetDaily: Double?,
    budgetWeekly: Double?,
    budgetMonthly: Double?,
    avgPerEntry: Double?,
    avgPerEntryMonth: Double?,
    budgetPerMeal: Double?,
    spendByCategoryDay: List<Pair<ProductCategory, Double>>,
    spendByCategory: List<Pair<ProductCategory, Double>>,
    spendByCategoryMonth: List<Pair<ProductCategory, Double>>,
    onEditBudget: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    // Each of the three windows (Jour/Semaine/Mois) now has its own budget
    // target (budgetDaily/budgetWeekly/budgetMonthly, all set via
    // onEditBudget) - previously only the weekly window had one, so Month
    // (and now Day) showed the total with no progress bar at all.
    val total = when (mode) {
        ExpensesSummaryMode.DAY   -> dayTotal
        ExpensesSummaryMode.WEEK  -> weekTotal
        ExpensesSummaryMode.MONTH -> monthTotal
    }
    val budget = when (mode) {
        ExpensesSummaryMode.DAY   -> budgetDaily
        ExpensesSummaryMode.WEEK  -> budgetWeekly
        ExpensesSummaryMode.MONTH -> budgetMonthly
    }
    // Per-meal average is only meaningful over a multi-purchase window - Day
    // mode has no separate "average per purchase today" figure, so this stays
    // null there rather than reusing the weekly average out of context.
    val avg = when (mode) {
        ExpensesSummaryMode.DAY   -> null
        ExpensesSummaryMode.WEEK  -> avgPerEntry
        ExpensesSummaryMode.MONTH -> avgPerEntryMonth
    }
    val byCategory = when (mode) {
        ExpensesSummaryMode.DAY   -> spendByCategoryDay
        ExpensesSummaryMode.WEEK  -> spendByCategory
        ExpensesSummaryMode.MONTH -> spendByCategoryMonth
    }
    val ofBudgetTemplate = when (mode) {
        ExpensesSummaryMode.DAY   -> R.string.expenses_of_budget_day
        ExpensesSummaryMode.WEEK  -> R.string.expenses_of_budget
        ExpensesSummaryMode.MONTH -> R.string.expenses_of_budget_month
    }
    val noBudgetHint = when (mode) {
        ExpensesSummaryMode.DAY   -> R.string.expenses_no_budget_hint_day
        ExpensesSummaryMode.WEEK  -> R.string.expenses_no_budget_hint
        ExpensesSummaryMode.MONTH -> R.string.expenses_no_budget_hint_month
    }

    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Every other embedded Journal tab (Weight/Activity/Hydration/Fasting/
                // Traitement) exposes this same shortcut into the unified Calendar -
                // Dépenses previously accepted onOpenCalendar as a parameter but never
                // actually called it anywhere, the one embedded tab silently missing
                // this affordance.
                // No explicit size override - every sibling embedded tab's own
                // calendar IconButton (Weight/Medication/etc.) relies on the
                // default 48dp minimum touch target rather than shrinking it;
                // an earlier pass here set 32dp, undersizing this one tap target
                // below every other IconButton in the app.
                IconButton(onClick = onOpenCalendar) {
                    Icon(TablerIcons.Calendar, stringResource(R.string.expenses_cd_calendar), tint = OnSurface.copy(0.5f))
                }
                Spacer(Modifier.width(Spacing.XS))
                // Jour/Semaine/Mois toggle - was a static "Cette semaine" label with
                // no way to see a shorter (today-only) or longer (calendar month)
                // total than the trailing 7-day window.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    listOf(
                        ExpensesSummaryMode.DAY to stringResource(R.string.expenses_view_day),
                        ExpensesSummaryMode.WEEK to stringResource(R.string.expenses_view_week),
                        ExpensesSummaryMode.MONTH to stringResource(R.string.expenses_view_month),
                    ).forEach { (m, label) ->
                        val selected = m == mode
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AccentCoral else OnSurface.copy(0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(CardRadius.BADGE))
                                .clickable { onModeChange(m) }
                                .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                        )
                    }
                }
            }
            TextButton(onClick = onEditBudget) {
                Text(stringResource(R.string.expenses_edit_budget), color = AccentCoral, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            "${total.formatDecimal(2)} €",
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        if (budget != null && budget > 0) {
            val pct = (total / budget).toFloat().coerceIn(0f, 1.5f)
            // total is a Double sum of per-entry prices, so a spend that's
            // mathematically exactly at budget can drift a fraction of a cent
            // either way from float summation - comparing to the nearest cent
            // (not a raw > 1f/Double >) avoids a budget met to the cent
            // flip-flopping between "over" and "under" depending on entry order.
            val isOver = centsOf(total) > centsOf(budget)
            LinearProgressIndicator(
                progress = { pct.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(CardRadius.BADGE)),
                color = if (isOver) AccentCoral else semanticGreen(),
                trackColor = OnSurface.copy(0.1f),
            )
            // Was color-only (a thin 8dp bar + small label hue switch) - easy to miss,
            // and not a real "helpful warning vs. scolding" signal since nothing here
            // said "over budget" in words, only in a subtle color shift.
            Text(
                stringResource(ofBudgetTemplate, budget) +
                    if (isOver) " · " + stringResource(R.string.expenses_over_budget_suffix) else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOver) AccentCoral else OnSurface.copy(0.5f),
            )
        } else {
            Text(stringResource(noBudgetHint), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        }
        if (avg != null && budgetPerMeal != null && budgetPerMeal > 0) {
            // Same cent-rounded comparison as isOver above - avg is
            // also a Double average of summed prices, subject to the same drift.
            val over = centsOf(avg) > centsOf(budgetPerMeal)
            Text(
                stringResource(R.string.expenses_avg_per_meal, avg, budgetPerMeal) +
                    if (over) " · " + stringResource(R.string.expenses_over_budget_suffix) else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) AccentCoral else OnSurface.copy(0.5f),
            )
        }
        // A single-category breakdown just repeats the total above with no new
        // information - only worth showing once spend actually spans categories.
        if (byCategory.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                byCategory.take(3).forEach { (category, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category.displayLabel(), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                        Text("${amount.formatDecimal(2)} €", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    }
                }
            }
        }
    }
}
