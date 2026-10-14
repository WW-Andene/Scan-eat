package fr.scanneat.domain.engine.health

import fr.scanneat.domain.engine.scoring.bmi
import fr.scanneat.domain.model.Profile

/** Roadmap C13: why intermittent fasting is not appropriate / needs a doctor for this profile. */
enum class FastingRisk { PREGNANCY, MINOR, DIABETES, UNDERWEIGHT }

data class FastingSafety(
    /** Start is refused (pregnancy, minor): energy needs of pregnancy and growth. */
    val blocked: Boolean,
    val risks: List<FastingRisk>,
) {
    /** A streak rewards restriction - a known eating-disorder risk factor; never shown to an at-risk profile. */
    val showStreak: Boolean get() = risks.isEmpty()
}

/**
 * Checks the profile before a fast. Blocked: pregnancy (increased energy and
 * protein needs every trimester - EFSA DRVs already applied in DailyTargets)
 * and minors (growth). Warning + "talk to your doctor": diabetes (risk of
 * hypoglycaemia when treated with insulin or a sulfonylurea; the app does not
 * know the treatment, so every diabetic profile is warned) and BMI < 18.5
 * (WHO underweight threshold).
 */
fun fastingSafety(profile: Profile): FastingSafety {
    val risks = buildList {
        if ("pregnancy" in profile.healthConditions) add(FastingRisk.PREGNANCY)
        if (profile.ageYears != null && profile.ageYears < 18) add(FastingRisk.MINOR)
        if ("diabetes" in profile.healthConditions) add(FastingRisk.DIABETES)
        if ((profile.ageYears == null || profile.ageYears >= 18) && (bmi(profile) ?: Double.MAX_VALUE) < 18.5) add(FastingRisk.UNDERWEIGHT)
    }
    return FastingSafety(
        blocked = FastingRisk.PREGNANCY in risks || FastingRisk.MINOR in risks,
        risks = risks,
    )
}
