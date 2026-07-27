package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product

// ============================================================================
// General, population-level benefits/risks — apply to anyone regardless of
// profile (nutrient thresholds, organic/fermented/whole-grain flags, additive
// tiers, named-substance matches). Split out of generateProductHints so the
// unconditional rule set lives apart from the profile-personalized section in
// ProductHintsPersonalization.kt.
// ============================================================================

/** Appends every population-level "benefit" line to [benefits] — nutrient
 *  claim thresholds (EU Reg 1924/2006 Annex / Reg 1169/2011 Annex XIII NRVs),
 *  organic/fermented/whole-grain flags, declared micronutrients, and named
 *  substances (see NamedSubstanceDb). Also appends any named-substance
 *  cautions to [risks] since findNamedSubstanceHints returns both in one call. */
internal fun appendGeneralBenefits(product: Product, lang: String, benefits: MutableList<String>, risks: MutableList<String>) {
    val en = lang == "en"
    val n = product.nutrition

    // Fibre/protein "high in"/"source of" wording follows EU Regulation (EC) No 1924/2006
    // Annex, nutrition claim thresholds (per 100 g, solids).
    if (n.fiberG >= 6.0) benefits += if (en) "High in fiber (${n.fiberG} g/100 g) — supports digestion" else "Riche en fibres (${n.fiberG} g/100 g) — favorise la digestion"
    if (n.proteinG >= 12.0) benefits += if (en) "Good source of protein (${n.proteinG} g/100 g)" else "Bonne source de protéines (${n.proteinG} g/100 g)"
    if (n.saltG <= 0.3) benefits += if (en) "Low salt (${n.saltG} g/100 g)" else "Faible en sel (${n.saltG} g/100 g)"
    if (n.sugarsG <= 5.0) benefits += if (en) "Low sugar (${n.sugarsG} g/100 g)" else "Faible en sucres (${n.sugarsG} g/100 g)"
    if (product.organic) benefits += if (en) "Certified organic" else "Certifié biologique"
    if (product.fermented) benefits += if (en) "Fermented — may support gut health" else "Fermenté — peut favoriser la santé intestinale"
    if (product.wholeGrainPrimary) {
        benefits += if (en) "Whole grain is the primary ingredient — associated with lower cardiometabolic risk in cohort studies"
                    else "Céréale complète en ingrédient principal — associé à un moindre risque cardiométabolique dans les études de cohorte"
    }
    // Omega-3 (ALA/EPA/DHA combined): 0.3 g/100 g is EFSA's "source of omega-3
    // fatty acids" claim threshold (Reg 1924/2006 Annex, as amended by Reg 116/2010).
    n.omega3G?.let { if (it >= 0.3) benefits += if (en) "Source of omega-3 fatty acids (${it} g/100 g)" else "Source d'oméga-3 (${it} g/100 g)" }
    // NRVs (Reg 1169/2011 Annex XIII): potassium 2000 mg, vitamin C 80 mg, vitamin D 5 µg.
    // "Source of" = ≥15% NRV/100g, "high in" = ≥30% NRV/100g.
    n.potassiumMg?.let { if (it >= 600.0) benefits += if (en) "High in potassium (${it.toInt()} mg/100 g)" else "Riche en potassium (${it.toInt()} mg/100 g)" }
    n.vitCMg?.let { if (it >= 24.0) benefits += if (en) "High in vitamin C (${it} mg/100 g)" else "Riche en vitamine C (${it} mg/100 g)" }
    n.vitDUg?.let { if (it >= 1.5) benefits += if (en) "High in vitamin D (${it} µg/100 g)" else "Riche en vitamine D (${it} µg/100 g)" }
    // Same "high in X" = ≥30% NRV/100g pattern as potassium/vitC/vitD above,
    // just extended to the rest of NutritionPer100g's optional micronutrient
    // fields (NRVs per EU Reg 1169/2011 Annex XIII) — those three were
    // previously the only micronutrients this ever surfaced as a benefit,
    // even though CustomFoodRepository/OffMapper/server mappers already
    // populate most of these fields when the source data has them.
    n.calciumMg?.let { if (it >= 240.0) benefits += if (en) "High in calcium (${it.toInt()} mg/100 g)" else "Riche en calcium (${it.toInt()} mg/100 g)" }
    n.ironMg?.let { if (it >= 4.2) benefits += if (en) "High in iron (${it} mg/100 g)" else "Riche en fer (${it} mg/100 g)" }
    n.magnesiumMg?.let { if (it >= 112.5) benefits += if (en) "High in magnesium (${it.toInt()} mg/100 g)" else "Riche en magnésium (${it.toInt()} mg/100 g)" }
    n.zincMg?.let { if (it >= 3.0) benefits += if (en) "High in zinc (${it} mg/100 g)" else "Riche en zinc (${it} mg/100 g)" }
    n.vitAUg?.let { if (it >= 240.0) benefits += if (en) "High in vitamin A (${it.toInt()} µg/100 g)" else "Riche en vitamine A (${it.toInt()} µg/100 g)" }
    n.vitEMg?.let { if (it >= 3.6) benefits += if (en) "High in vitamin E (${it} mg/100 g)" else "Riche en vitamine E (${it} mg/100 g)" }
    n.vitKUg?.let { if (it >= 22.5) benefits += if (en) "High in vitamin K (${it.toInt()} µg/100 g)" else "Riche en vitamine K (${it.toInt()} µg/100 g)" }
    n.b1Mg?.let { if (it >= 0.33) benefits += if (en) "High in vitamin B1 (${it} mg/100 g)" else "Riche en vitamine B1 (${it} mg/100 g)" }
    n.b2Mg?.let { if (it >= 0.42) benefits += if (en) "High in vitamin B2 (${it} mg/100 g)" else "Riche en vitamine B2 (${it} mg/100 g)" }
    n.b3Mg?.let { if (it >= 4.8) benefits += if (en) "High in vitamin B3 (${it} mg/100 g)" else "Riche en vitamine B3 (${it} mg/100 g)" }
    n.b6Mg?.let { if (it >= 0.42) benefits += if (en) "High in vitamin B6 (${it} mg/100 g)" else "Riche en vitamine B6 (${it} mg/100 g)" }
    n.b9Ug?.let { if (it >= 60.0) benefits += if (en) "High in folate/B9 (${it.toInt()} µg/100 g)" else "Riche en folates/B9 (${it.toInt()} µg/100 g)" }
    n.b12Ug?.let { if (it >= 0.75) benefits += if (en) "High in vitamin B12 (${it} µg/100 g)" else "Riche en vitamine B12 (${it} µg/100 g)" }
    if (product.declaredMicronutrients.isNotEmpty()) {
        benefits += if (en) "Declared micronutrients: ${product.declaredMicronutrients.joinToString(", ")}"
                    else "Micronutriments déclarés : ${product.declaredMicronutrients.joinToString(", ")}"
    }
    // Named substances (caffeine, creatine, melatonin, ginseng, ...) matched
    // against the actual ingredient list — see NamedSubstanceDb for the
    // EFSA-authorised-claim-or-not distinction this splits on.
    val (substanceBenefits, substanceCautions) = findNamedSubstanceHints(product.ingredients, lang)
    benefits += substanceBenefits
    risks += substanceCautions
}

/** Appends every population-level "risk" line to [risks] — nutrient
 *  thresholds, trans fat/added sugar WHO guidance, NOVA 4, marketing health
 *  claims, and Tier-1/Tier-2 additives (see AdditivesDb, same lookup
 *  AdditiveRiskPillar already runs for the score deduction). */
internal fun appendGeneralRisks(product: Product, lang: String, risks: MutableList<String>) {
    val en = lang == "en"
    val n = product.nutrition

    if (n.saturatedFatG >= 5.0) risks += if (en) "High in saturated fat (${n.saturatedFatG} g/100 g)" else "Riche en graisses saturées (${n.saturatedFatG} g/100 g)"
    if (n.sugarsG >= 15.0) risks += if (en) "High in sugar (${n.sugarsG} g/100 g)" else "Riche en sucres (${n.sugarsG} g/100 g)"
    if (n.saltG >= 1.2) risks += if (en) "High in salt (${n.saltG} g/100 g)" else "Riche en sel (${n.saltG} g/100 g)"
    // WHO REPLACE initiative / EU Regulation 2019/649 caps industrial trans fat at 2 g
    // per 100 g of fat; flagged here at any declared presence since WHO's guidance is
    // to eliminate industrial trans fat from the food supply entirely, not just cap it.
    n.transFatG?.let { if (it > 0.0) risks += if (en) "Contains trans fat (${it} g/100 g) — WHO recommends minimizing industrial trans fat intake"
                                              else "Contient des acides gras trans (${it} g/100 g) — l'OMS recommande de minimiser leur consommation" }
    // WHO guideline (2015): free/added sugars should be <10% of total energy intake;
    // flagged distinctly from total sugars since it isolates the manufacturer-added portion.
    n.addedSugarsG?.let { if (it >= 10.0) risks += if (en) "Contains added sugars (${it} g/100 g) — WHO recommends limiting free sugar intake"
                                                   else "Contient des sucres ajoutés (${it} g/100 g) — l'OMS recommande de limiter les sucres libres" }
    if (product.novaClass == NovaClass.ULTRA_PROCESSED) {
        risks += if (en) "Ultra-processed (NOVA 4) — associated with higher long-term health risk in observational studies"
                 else "Ultra-transformé (NOVA 4) — associé à un risque accru sur la santé à long terme dans les études observationnelles"
    }
    if (product.hasHealthClaims) {
        risks += if (en) "Carries marketing health claims — verify against the actual nutrition values above"
                 else "Porte des allégations santé marketing — à vérifier au regard des valeurs nutritionnelles ci-dessus"
    }

    // Tier-1/Tier-2 additives (AdditivesDb — same lookup AdditiveRiskPillar
    // already runs for the score deduction itself) were only ever reflected
    // as a number buried in the score breakdown, never named here — a user
    // could see "-8 points, additive risk" on the score without the hint
    // panel ever saying *which* additive or why. Tier 3 (minor concern) is
    // left out to keep this list high-signal, matching the pillar's own
    // "minor" label for that tier.
    product.ingredients.forEach { ing ->
        val additive = fr.scanneat.domain.engine.scoring.findAdditive(ing.eNumber, ing.name, ing.category) ?: return@forEach
        if (additive.tier == fr.scanneat.domain.engine.scoring.AdditiveTier.ONE || additive.tier == fr.scanneat.domain.engine.scoring.AdditiveTier.TWO) {
            risks += if (en) "Contains ${additive.eNumber} (${ing.name}) — ${additive.concern} (${additive.source})"
                     else "Contient ${additive.eNumber} (${ing.name}) — ${additive.concern} (${additive.source})"
        }
    }
}
