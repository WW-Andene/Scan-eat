package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// Personal score output
// ============================================================================

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
