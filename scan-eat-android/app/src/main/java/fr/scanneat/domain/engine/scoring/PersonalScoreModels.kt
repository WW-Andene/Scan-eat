package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// Personal score output
// ============================================================================

// `category` already carries the signal a future UI needs to visually
// distinguish evidentiary weight - a CONDITION adjustment (see enum below)
// rests on an associative/probabilistic finding (typically a single cohort
// study, e.g. HealthConditionSystemicAdjustments.kt's depression/sugar
// adjustment) applied deterministically to one user's scan, which is a
// different epistemic category from a directly measured nutrient value
// (BMI/DIET/ACTIVITY reflect the user's own declared profile, not a
// third-party research finding about people like them). Both currently reach
// the UI as an identically-typed, identically-confident `points`/`reason`
// pair with nothing marking that difference - the data needed to fix that
// (category == CONDITION) already exists here; it just isn't surfaced
// differently downstream yet. Noted here so that gap isn't invisible to
// whoever builds the next UI pass over this data.
data class PersonalAdjustment(
    val points: Double,
    val reason: String,
    val category: AdjustmentCategory,
    val veto: Boolean = false,
)

// PROTEIN_BUDGET previously also covered sat-fat/sugar/salt daily-budget penalties,
// which have nothing to do with protein — flagged independently by two separate
// audit passes as a real mislabeling (any future UI grouping/filtering by category
// would bucket sat-fat/sugar/salt warnings under "protein"). DAILY_BUDGET now
// covers those three; PROTEIN_BUDGET is reserved for the actual protein-PRI bonus.
// CONDITION specifically is the one category above whose adjustments rest on
// associative epidemiological evidence about a declared health condition
// rather than a directly declared/measured fact about the product or user -
// see this file's PersonalAdjustment doc comment.
enum class AdjustmentCategory { DIET, AGE, SEX, ACTIVITY, BMI, GOAL, MODIFIER, CONDITION, PROTEIN_BUDGET, DAILY_BUDGET }

data class PersonalScoreResult(
    val personalScore: Int,
    val delta: Int,
    val adjustments: List<PersonalAdjustment>,
    val applicable: Boolean,
    val dietReason: String?,
    val veto: Boolean,
    /** Allergens found in this product that match the user's declared allergens. */
    val allergenHits: List<AllergenHit> = emptyList(),
)
