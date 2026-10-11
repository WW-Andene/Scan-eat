package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

/** Diet compliance and (where applicable) a hard veto + reason for it. */
internal data class ConditionalAdjustments(
    val adjustments: List<PersonalAdjustment>,
    val veto: Boolean = false,
    val dietReason: String? = null,
)

// ===== DIET COMPLIANCE (HARD) =====
internal fun checkDietCompliance(product: Product, profile: Profile, lang: String): ConditionalAdjustments {
    if (profile.diet == DietKey.NONE) return ConditionalAdjustments(emptyList())
    val adjustments = mutableListOf<PersonalAdjustment>()
    var veto = false
    var dietReason: String? = null
    val r = checkDiet(product, profile.diet, lang)
    if (!r.compliant) {
        veto = true
        dietReason = r.reason
        adjustments += PersonalAdjustment(0.0, r.reason ?: "", AdjustmentCategory.DIET, veto = true)
    } else if (r.certified) {
        val label = if (lang == "en") profile.diet.labelEn else profile.diet.labelFr
        adjustments += PersonalAdjustment(
            points   = 5.0,
            reason   = if (lang == "en") "$label certification detected: ${r.preferredHits.take(2).joinToString()}"
                       else "Certification $label détectée : ${r.preferredHits.take(2).joinToString()}",
            category = AdjustmentCategory.DIET,
        )
    } else if (r.preferredHits.isNotEmpty()) {
        val label = if (lang == "en") profile.diet.labelEn else profile.diet.labelFr
        adjustments += PersonalAdjustment(
            points   = 3.0,
            reason   = if (lang == "en") "$label-friendly ingredients: ${r.preferredHits.take(2).joinToString()}"
                       else "Conforme $label : ${r.preferredHits.take(2).joinToString()}",
            category = AdjustmentCategory.DIET,
        )
    }
    return ConditionalAdjustments(adjustments, veto, dietReason)
}
