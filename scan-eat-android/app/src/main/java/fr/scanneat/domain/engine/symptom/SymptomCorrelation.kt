package fr.scanneat.domain.engine.symptom

import fr.scanneat.domain.model.DiaryEntry
import java.time.LocalDate
import kotlin.math.roundToInt

// ============================================================================
// symptomFoodCorrelations — user-requested: correlate a symptom journal
// against what was actually logged in the diary. Deliberately a crude,
// transparent heuristic (day-level co-occurrence rate, filtered since C17 by
// a one-sided Fisher exact test - still no causal claim) since a handful of logged days can
// never support anything stronger, and overstating confidence here would be
// actively misleading for what's ultimately a personal-health signal. Meant
// to surface "worth paying attention to", not "this food causes X".
// ============================================================================

data class FoodCorrelation(
    val productName: String,
    // Percentage points: (share of symptom days this food appears on) minus
    // (share of non-symptom days it appears on). Always > 0 in the returned
    // list - foods that appear LESS often on symptom days aren't a signal
    // this feature is trying to surface.
    val differencePct: Int,
    val symptomDaysWithFood: Int,
    val totalSymptomDays: Int,
    /** One-sided Fisher exact test p-value (C17). */
    val pValue: Double = 1.0,
)

// Roadmap C17: was 2 - two days were enough to suggest a link between a
// symptom and a treatment. Now at least 5 co-occurrences, at least 14 observed
// days, and a one-sided Fisher exact test below [MAX_P_VALUE].
internal const val MIN_SYMPTOM_DAY_OCCURRENCES = 5
internal const val MIN_OBSERVED_DAYS = 14
internal const val MAX_P_VALUE = 0.05

/** STRONG below p = 0.01, otherwise MODERATE (only p < 0.05 is ever returned). */
enum class CorrelationRobustness { MODERATE, STRONG }

fun robustnessOf(pValue: Double): CorrelationRobustness =
    if (pValue < 0.01) CorrelationRobustness.STRONG else CorrelationRobustness.MODERATE

/**
 * One-sided Fisher exact test on the 2x2 table
 * [a = symptom & exposed, b = symptom & not exposed, c = no symptom & exposed,
 * d = no symptom & not exposed]: probability of seeing a or more exposed
 * symptom days by chance, given the margins (hypergeometric upper tail).
 */
fun fisherExactGreater(a: Int, b: Int, c: Int, d: Int): Double {
    val n = a + b + c + d
    val row1 = a + b
    val col1 = a + c
    fun logC(nn: Int, k: Int): Double = logFact(nn) - logFact(k) - logFact(nn - k)
    val denom = logC(n, row1)
    var p = 0.0
    for (x in a..minOf(row1, col1)) {
        if (row1 - x > n - col1) continue
        p += kotlin.math.exp(logC(col1, x) + logC(n - col1, row1 - x) - denom)
    }
    return p.coerceIn(0.0, 1.0)
}

private fun logFact(n: Int): Double { var s = 0.0; for (i in 2..n) s += kotlin.math.ln(i.toDouble()); return s }

// Below this point-difference, day-to-day meal variation alone plausibly
// explains it - not worth surfacing as "possibly related".
private const val MIN_DIFFERENCE_PCT = 20

fun symptomFoodCorrelations(
    symptomDates: Set<LocalDate>,
    diaryEntries: List<DiaryEntry>,
): List<FoodCorrelation> {
    if (symptomDates.isEmpty()) return emptyList()
    val allDates = diaryEntries.mapTo(mutableSetOf()) { it.date }
    val nonSymptomDates = allDates - symptomDates
    if (nonSymptomDates.isEmpty()) return emptyList() // no contrast group to compare against
    if (allDates.size < MIN_OBSERVED_DAYS) return emptyList()

    val byFood = diaryEntries.groupBy { it.productName.trim().lowercase() }
    return byFood.mapNotNull { (_, entries) ->
        val displayName = entries.first().productName
        val datesWithFood = entries.mapTo(mutableSetOf()) { it.date }
        val symptomDaysWithFood = (datesWithFood intersect symptomDates).size
        if (symptomDaysWithFood < MIN_SYMPTOM_DAY_OCCURRENCES) return@mapNotNull null
        val nonSymptomDaysWithFood = (datesWithFood intersect nonSymptomDates).size
        // Rounded, not truncated - plain integer division here previously let
        // symptomPct/nonSymptomPct each drift up to ~1 point toward zero
        // independently (different denominators), which could shift their
        // difference across MIN_DIFFERENCE_PCT and silently hide a real
        // correlation this function exists to surface.
        val symptomPct = (symptomDaysWithFood * 100.0 / symptomDates.size).roundToInt()
        val nonSymptomPct = (nonSymptomDaysWithFood * 100.0 / nonSymptomDates.size).roundToInt()
        val diff = symptomPct - nonSymptomPct
        if (diff < MIN_DIFFERENCE_PCT) return@mapNotNull null
        val p = fisherExactGreater(symptomDaysWithFood, symptomDates.size - symptomDaysWithFood,
            nonSymptomDaysWithFood, nonSymptomDates.size - nonSymptomDaysWithFood)
        if (p >= MAX_P_VALUE) return@mapNotNull null
        FoodCorrelation(displayName, diff, symptomDaysWithFood, symptomDates.size, p)
    }.sortedByDescending { it.differencePct }
}

// ============================================================================
// symptomMedicationCorrelations — app-audit §X: Medication has repeatedly been
// cross-referenced against other trackers (MedicationViewModel already
// compares it to Weight and Hydration, ActivityMedicationRisk.kt already
// compares it to Activity) but never against Symptom, despite symptom
// journaling (nausea, fatigue, headache, skin) being one of the most common
// real-world reasons someone would check "is this from a new medication?".
// Same day-level co-occurrence heuristic as symptomFoodCorrelations above -
// purely descriptive, not a causal claim, same framing.
// ============================================================================

data class MedicationCorrelation(
    val medicationName: String,
    val differencePct: Int,
    val symptomDaysWithMedication: Int,
    val totalSymptomDays: Int,
    val pValue: Double = 1.0,
)

fun symptomMedicationCorrelations(
    symptomDates: Set<LocalDate>,
    medicationDoseDates: List<Pair<LocalDate, String>>, // (date, medicationName)
): List<MedicationCorrelation> {
    if (symptomDates.isEmpty()) return emptyList()
    val allDates = medicationDoseDates.mapTo(mutableSetOf()) { it.first }
    val nonSymptomDates = allDates - symptomDates
    if (nonSymptomDates.isEmpty()) return emptyList()
    if (allDates.size < MIN_OBSERVED_DAYS) return emptyList()

    val byMedication = medicationDoseDates.groupBy { it.second.trim().lowercase() }
    return byMedication.mapNotNull { (_, doses) ->
        val displayName = doses.first().second
        val datesWithMedication = doses.mapTo(mutableSetOf()) { it.first }
        val symptomDaysWithMedication = (datesWithMedication intersect symptomDates).size
        if (symptomDaysWithMedication < MIN_SYMPTOM_DAY_OCCURRENCES) return@mapNotNull null
        val nonSymptomDaysWithMedication = (datesWithMedication intersect nonSymptomDates).size
        val symptomPct = (symptomDaysWithMedication * 100.0 / symptomDates.size).roundToInt()
        val nonSymptomPct = (nonSymptomDaysWithMedication * 100.0 / nonSymptomDates.size).roundToInt()
        val diff = symptomPct - nonSymptomPct
        if (diff < MIN_DIFFERENCE_PCT) return@mapNotNull null
        val p = fisherExactGreater(symptomDaysWithMedication, symptomDates.size - symptomDaysWithMedication,
            nonSymptomDaysWithMedication, nonSymptomDates.size - nonSymptomDaysWithMedication)
        if (p >= MAX_P_VALUE) return@mapNotNull null
        MedicationCorrelation(displayName, diff, symptomDaysWithMedication, symptomDates.size, p)
    }.sortedByDescending { it.differencePct }
}
