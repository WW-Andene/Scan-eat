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

fun annualSpendProjection(monthTotalEuros: Double, dayOfMonth: Int, daysInMonth: Int, budgetMonthlyEuros: Double?): AnnualSpendProjection {
    // Early in the month, a straight multiply of the (tiny) month-to-date total
    // wildly understates the real pace - scale up to a full-month-equivalent
    // rate first, same "don't extrapolate from too little data" discipline
    // the rest of the app's forecasts already apply.
    val monthRate = if (dayOfMonth > 0) monthTotalEuros / dayOfMonth * daysInMonth else monthTotalEuros
    val projectedAnnual = monthRate * 12
    val budgetAnnual = budgetMonthlyEuros?.let { it * 12 }
    return AnnualSpendProjection(
        projectedAnnualEuros = projectedAnnual,
        budgetAnnualEuros = budgetAnnual,
        overBudget = budgetAnnual != null && projectedAnnual > budgetAnnual,
    )
}
