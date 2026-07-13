package fr.scanneat.domain.model

// ============================================================================
// USER PROFILE — port of public/data/profile.js
// ============================================================================

enum class Sex { MALE, FEMALE, NOT_SPECIFIED }

/**
 * Physical Activity Level values from FAO/WHO/UNU 2004 Table 5.1.
 * These match the ACTIVITY_PAL map in PersonalScoreEngine exactly.
 * The enum has no value field — use ACTIVITY_PAL[activityLevel] to get the multiplier.
 */
enum class ActivityLevel {
    SEDENTARY,          // PAL 1.40
    LIGHTLY_ACTIVE,     // PAL 1.55
    MODERATELY_ACTIVE,  // PAL 1.75
    VERY_ACTIVE,        // PAL 1.90
    EXTRA_ACTIVE,       // PAL 2.20
}

enum class Goal { LOSE, MAINTAIN, GAIN }

data class Profile(
    val id: String = "default",
    val name: String = "",
    val sex: Sex = Sex.NOT_SPECIFIED,
    val ageYears: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val goalWeightKg: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE,
    val goal: Goal = Goal.MAINTAIN,
    // DietKey lives in domain/engine/scoring/DietChecker.kt
    val diet: fr.scanneat.domain.engine.scoring.DietKey = fr.scanneat.domain.engine.scoring.DietKey.NONE,
    val allergens: Set<String> = emptySet(),
    // Free-form keys ("diabetes", "hypertension", "pregnancy", "kidney_disease",
    // "thyroid_disorder", "food_allergies", "intolerances", "digestive_disorders") -
    // same pattern as allergens, consumed by PersonalScoreEngine/HydrationRepository.
    val healthConditions: Set<String> = emptySet(),
    val isMenstruating: Boolean = false,
)
// NOTE: DailyTargets is defined in domain/engine/scoring/PersonalScoreEngine.kt (canonical location).
// Do NOT add a second DailyTargets here.
