package fr.scanneat.domain.engine.expense

// ============================================================================
// annualSpendProjection — user-requested: "empreinte financière annuelle".
// Extrapolates the current month's real spend rate into a full-year estimate
// and compares it to the user's own declared monthly budget (x12), rather
// than only ever showing week/month totals in isolation with no longer-term
// framing.
// ============================================================================

data class AnnualSpendProjection(
    val projectedAnnualEuros: Double,
    val budgetAnnualEuros: Double?,
    val overBudget: Boolean,
)

// A single day-1 purchase divided by dayOfMonth=1 and multiplied back up to a
// full month wildly OVERSTATES the real pace instead (e.g. one 50€ grocery
// run on the 1st projects as 1550€ that month alone) - dampened by treating
// the first few days as too little data to extrapolate confidently, same
// "don't extrapolate from too little data" discipline weeklyCrossTrackerInsight's
// own minLoggedDays already applies elsewhere in the app.
private const val MIN_DAYS_FOR_FULL_EXTRAPOLATION = 5

fun annualSpendProjection(monthTotalEuros: Double, dayOfMonth: Int, daysInMonth: Int, budgetMonthlyEuros: Double?): AnnualSpendProjection {
    val effectiveDays = dayOfMonth.coerceAtLeast(MIN_DAYS_FOR_FULL_EXTRAPOLATION)
    val monthRate = if (effectiveDays > 0) monthTotalEuros / effectiveDays * daysInMonth else monthTotalEuros
    val projectedAnnual = monthRate * 12
    val budgetAnnual = budgetMonthlyEuros?.let { it * 12 }
    // app-audit §O/§K1: cent-rounded comparison, same fix ExpensesSummaryCard's
    // own centsOf() already applies to its week/day/month over-budget checks -
    // a projection landing exactly on budget could otherwise flip this flag
    // non-deterministically from sub-cent float drift in the *12/ /effectiveDays
    // chain above.
    val projectedAnnualCents = Math.round(projectedAnnual * 100)
    val budgetAnnualCents = budgetAnnual?.let { Math.round(it * 100) }
    return AnnualSpendProjection(
        projectedAnnualEuros = projectedAnnual,
        budgetAnnualEuros = budgetAnnual,
        overBudget = budgetAnnualCents != null && projectedAnnualCents > budgetAnnualCents,
    )
}
