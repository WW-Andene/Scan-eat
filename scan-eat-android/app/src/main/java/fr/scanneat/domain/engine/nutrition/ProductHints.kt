package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.Profile

// ============================================================================
// PRODUCT HINTS — the "💡" info panel's content. Deliberately rule-based off
// data already on the Product (NOVA class, nutrition thresholds, organic/
// fermented flags) and, where the profile has it, Profile.healthConditions
// — not fabricated medical claims: every hint traces back to a concrete
// field, so there is no line here that isn't backed by either the
// product's own declared data or a cited public-health guidance source
// (see HealthConditionGuidanceDb).
// ============================================================================

data class ProductHints(
    val benefits: List<String>,
    val risks: List<String>,
    val facts: List<String>,
)

fun generateProductHints(product: Product, profile: Profile, lang: String): ProductHints {
    val en = lang == "en"
    val n = product.nutrition
    val benefits = mutableListOf<String>()
    val risks = mutableListOf<String>()
    val facts = mutableListOf<String>()

    // ---- Benefits ----
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
    if (product.declaredMicronutrients.isNotEmpty()) {
        benefits += if (en) "Declared micronutrients: ${product.declaredMicronutrients.joinToString(", ")}"
                    else "Micronutriments déclarés : ${product.declaredMicronutrients.joinToString(", ")}"
    }
    // Named substances (caffeine, creatine, melatonin, ginseng, ...) matched
    // against the actual ingredient list — see NamedSubstanceDb for the
    // EFSA-authorised-claim-or-not distinction this splits on.
    val (substanceBenefits, substanceCautions) = findNamedSubstanceHints(product.ingredients, lang)
    benefits += substanceBenefits

    // ---- Risks ----
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
    risks += substanceCautions

    // ---- Personalized (Profile.healthConditions) ----
    // Closes the gap with PersonalScoreEngine, which already personalizes the
    // *score* for diabetes/hypertension/kidney_disease/pregnancy — the hint
    // panel is a separate surface and previously ignored the profile entirely.
    // Thresholds/wording deliberately mirror PersonalScoreEngine's own
    // condition checks exactly (same 15.0/5.0 sugar, 1.2/0.3 salt, 15.0
    // protein cutoffs) so the two surfaces never disagree with each other.
    val conditions = profile.healthConditions
    if ("diabetes" in conditions) {
        if (n.sugarsG >= 15.0) risks += if (en) "High sugar (${n.sugarsG} g/100 g) — caution advised for diabetes"
                                        else "Sucres élevés (${n.sugarsG} g/100 g) — prudence recommandée en cas de diabète"
        else if (n.sugarsG <= 5.0) benefits += if (en) "Low sugar — diabetes-friendly" else "Faible en sucres — adapté au diabète"
    }
    if ("hypertension" in conditions) {
        if (n.saltG >= 1.2) risks += if (en) "High salt (${n.saltG} g/100 g) — caution advised for hypertension"
                                     else "Sel élevé (${n.saltG} g/100 g) — prudence recommandée en cas d'hypertension"
        else if (n.saltG <= 0.3) benefits += if (en) "Low salt — hypertension-friendly" else "Faible en sel — adapté à l'hypertension"
    }
    if ("kidney_disease" in conditions && n.proteinG >= 15.0) {
        risks += if (en) "High protein (${n.proteinG} g/100 g) — caution advised for kidney disease"
                 else "Protéines élevées (${n.proteinG} g/100 g) — prudence recommandée en cas de maladie rénale"
    }
    risks += findHealthConditionGuidance(product.ingredients, profile.healthConditions, lang)
    val containsCaffeineSource = product.ingredients.any {
        val norm = fr.scanneat.domain.engine.scoring.normalizeForMatching(it.name)
        listOf("cafeine", "guarana", "mate", "yerba mate", "cafe", "the vert", "the noir", "coffee", "tea", "cocoa", "cacao")
            .any { syn -> norm.contains(fr.scanneat.domain.engine.scoring.normalizeForMatching(syn)) }
    }
    if ("pregnancy" in conditions && containsCaffeineSource) {
        // ANSES: pregnant women should keep total caffeine intake under 200 mg/day —
        // a stricter, condition-specific limit than the generic EFSA "75 mg = alertness"
        // claim threshold already reported above for the general population. Flagged
        // on any caffeine-source ingredient, not just declared caffeine content,
        // since coffee/tea/cocoa rarely declare an exact mg figure on-label.
        risks += if (en) "May contain caffeine — ANSES recommends pregnant women keep total daily caffeine intake under 200 mg"
                 else "Peut contenir de la caféine — l'ANSES recommande de limiter l'apport total en caféine à 200 mg/jour pendant la grossesse"
    }
    // Same alcohol-content check PersonalScoreEngine already runs for its own
    // cancer/depression adjustments — kept in sync so the hint panel never
    // disagrees with the score adjustments section.
    val containsAlcohol = product.ingredients.any {
        val norm = it.name.lowercase()
        "alcool" in norm || "alcohol" in norm || "vin " in norm || "wine" in norm || "bière" in norm || "beer" in norm
    }
    if ("cancer" in conditions && containsAlcohol) {
        risks += if (en) "Contains alcohol — WCRF cancer prevention guidance recommends limiting alcohol intake"
                 else "Contient de l'alcool — les recommandations WCRF de prévention du cancer conseillent d'en limiter la consommation"
    }
    if ("depression" in conditions && containsAlcohol) {
        risks += if (en) "Contains alcohol — can worsen depressive symptoms and interacts with most antidepressants"
                 else "Contient de l'alcool — peut aggraver les symptômes dépressifs et interagit avec la plupart des antidépresseurs"
    }

    // ---- Facts ----
    facts += if (en) "NOVA processing class: ${product.novaClass.value}/4" else "Classe de transformation NOVA : ${product.novaClass.value}/4"
    facts += if (en) "Energy: ${n.energyKcal} kcal/100 g" else "Énergie : ${n.energyKcal} kcal/100 g"
    if (product.origin != null) facts += if (en) "Origin: ${product.origin}" else "Origine : ${product.origin}"
    n.cholesterolMg?.let { facts += if (en) "Cholesterol: ${it} mg/100 g" else "Cholestérol : ${it} mg/100 g" }
    // Trivia matched against the actual ingredient list — see IngredientFactsDb
    // for why this is a hand-curated substring match rather than LLM-generated.
    facts += findIngredientFacts(product.ingredients, lang)

    return ProductHints(benefits, risks, facts)
}
