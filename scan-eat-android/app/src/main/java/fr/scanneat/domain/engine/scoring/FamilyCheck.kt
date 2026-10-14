package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.ScoreAudit

/** Roadmap D10: one verdict per household profile for the scanned product. */
enum class FamilyStatus { OK, CAUTION, UNVERIFIED, BLOCKED }

data class FamilyMemberVerdict(
    val profileId: String,
    val profileName: String,
    val status: FamilyStatus,
    /** Why the product is blocked / needs caution (veto reason, trace allergens); null for OK. */
    val reason: String?,
    /** Personal score when personalisation applies to this profile, else the classic score. */
    val score: Int,
)

/**
 * Re-runs [computePersonalScore] for every profile, so a parent shopping for a
 * child with an allergy sees each person's compatibility in one scan, not only
 * the active profile's.
 *
 * - BLOCKED: personal veto (confirmed allergen, diet, health condition).
 * - UNVERIFIED: the profile declares allergens or a diet but the product has no
 *   ingredient list - "no problem found" must not read as "safe" (same rule as
 *   AllergenUnverifiedBanner on the active profile).
 * - CAUTION: only precautionary "may contain" traces of a declared allergen.
 * - OK: nothing found for this profile.
 */
fun familyVerdicts(
    audit: ScoreAudit,
    product: Product,
    profiles: List<Profile>,
    lang: String = "fr",
): List<FamilyMemberVerdict> = profiles.map { profile ->
    val personal = computePersonalScore(audit, product, profile, lang)
    val traces = personal.allergenHits.filter { it.isTraceOnly }
    val needsIngredients = profile.allergens.isNotEmpty() || profile.diet != DietKey.NONE
    val (status, reason) = when {
        personal.veto -> FamilyStatus.BLOCKED to personal.dietReason
        needsIngredients && product.ingredients.isEmpty() -> FamilyStatus.UNVERIFIED to null
        traces.isNotEmpty() -> FamilyStatus.CAUTION to traces.joinToString(", ") { if (lang == "fr") it.labelFr else it.labelEn }
        else -> FamilyStatus.OK to null
    }
    FamilyMemberVerdict(
        profileId = profile.id,
        profileName = profile.name,
        status = status,
        reason = reason,
        score = if (personal.applicable) personal.personalScore else audit.score,
    )
}
