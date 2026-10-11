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

/**
 * User-requested: a quick self-assessed body-composition estimate ("très peu
 * musclé/gras" → "très musclé/gras") for anyone who doesn't want to pull out
 * a tape measure for the Navy BF% method (BiolismProfile's waistCm/hipCm/
 * neckCm - see MetabolicsCalculator's own doc comment on that, precise but
 * opt-in). Deliberately a 5-point subjective scale, not a claimed percentage
 * - same "rough estimate, not a lab measurement" honesty ActivityLevel/Goal
 * already model as enums rather than continuous inputs. Reused for both the
 * fat-level and muscle-level axes since they're independent but share the
 * same low↔high shape.
 */
enum class BodyCompositionLevel { VERY_LOW, LOW, MODERATE, HIGH, VERY_HIGH }

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
    // "ibs", "crohn_ibd", "chronic_diarrhea", "cancer", "depression",
    // "chronic_migraine", "epilepsy") - same pattern as allergens, consumed by
    // PersonalScoreEngine/HydrationRepository. The old catch-all
    // "digestive_disorders" key was split into "ibs"/"crohn_ibd"/
    // "chronic_diarrhea" (see DietAndConditionAdjustments.checkHealthConditions)
    // since each has its own distinct, sourced dietary guidance, unlike the
    // single bucket it replaced which was too heterogeneous to score against.
    val healthConditions: Set<String> = emptySet(),
    val isMenstruating: Boolean = false,
    // User-requested: trimester-adapted nutrition targets instead of the same
    // static "pregnancy" veto/caution the whole pregnancy - only meaningful
    // (and only ever surfaced in the UI) when "pregnancy" is also in
    // [healthConditions]; see dailyTargets()'s own trimester logic and
    // ProfileScreen's conditional date field.
    val pregnancyStartDate: java.time.LocalDate? = null,
    // See BodyCompositionLevel's own doc comment. Null = not set (neither
    // axis is required - most downstream math already works from
    // weightKg/heightCm alone).
    val fatLevel: BodyCompositionLevel? = null,
    val muscleLevel: BodyCompositionLevel? = null,
)
// NOTE: DailyTargets is defined in domain/engine/scoring/PersonalScoreEngine.kt (canonical location).
// Do NOT add a second DailyTargets here.
