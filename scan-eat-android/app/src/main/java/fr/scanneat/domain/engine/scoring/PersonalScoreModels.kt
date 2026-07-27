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

enum class AdjustmentCategory { DIET, AGE, SEX, ACTIVITY, BMI, GOAL, MODIFIER, CONDITION, PROTEIN_BUDGET }

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
