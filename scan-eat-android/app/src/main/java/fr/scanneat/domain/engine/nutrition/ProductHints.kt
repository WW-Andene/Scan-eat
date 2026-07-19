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
    /** General, population-level cautions - apply to anyone regardless of profile
     *  (high sat fat/sugar/salt, trans fat, NOVA 4, additive concerns...). */
    val risks: List<String>,
    /** Risks specific to *this* user's own profile (declared allergens, chosen
     *  diet, and Profile.healthConditions) - previously merged into [risks], so
     *  a generic "high sugar" caution that applies to everyone and a "caution
     *  advised for diabetes" line that only applies because of this one user's
     *  own condition were visually indistinguishable, even though they mean
     *  very different things to a reader without a diabetes diagnosis. */
    val conditionRisks: List<String>,
    val facts: List<String>,
    /** What complements this product nutritionally or gastronomically — flavor
     *  pairings (Ahn et al. flavor-network co-occurrence, same PairingsDb the
     *  standalone PairingsCard already uses) plus absorption-enhancer pairings
     *  (e.g. vitamin C alongside an iron source). */
    val pairWell: List<String> = emptyList(),
    /** What to avoid pairing this product with — nutrient-absorption inhibitor
     *  interactions (e.g. tea/coffee tannins alongside an iron source), not a
     *  flavor judgment. */
    val avoidPairing: List<String> = emptyList(),
) {
    companion object {
        /** Fallback for a combine-into-map StateFlow lookup miss (e.g. the one-frame
         *  gap right after a new recipe/template is added, before its hints entry
         *  lands) — same role as NutritionPer100g.EMPTY elsewhere in the codebase. */
        val EMPTY = ProductHints(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

fun generateProductHints(product: Product, profile: Profile, lang: String): ProductHints {
    val en = lang == "en"
    val n = product.nutrition
    val benefits = mutableListOf<String>()
    val risks = mutableListOf<String>()
    val conditionRisks = mutableListOf<String>()
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

    // ---- Personalized (Profile.healthConditions) ----
    // Closes the gap with PersonalScoreEngine, which already personalizes the
    // *score* for diabetes/hypertension/kidney_disease/pregnancy — the hint
    // panel is a separate surface and previously ignored the profile entirely.
    // Thresholds/wording deliberately mirror PersonalScoreEngine's own
    // condition checks exactly (same 15.0/5.0 sugar, 1.2/0.3 salt, 15.0
    // protein cutoffs) so the two surfaces never disagree with each other.
    // Allergen/diet violations were computed by PersonalScoreEngine for the
    // score itself (checkUserAllergens/checkDiet) but never surfaced here —
    // a user with a declared allergy got a score penalty with no
    // corresponding line in the hint panel explaining why.
    val allergenHits = fr.scanneat.domain.engine.scoring.checkUserAllergens(product, profile.allergens, lang)
    allergenHits.forEach { hit ->
        val label = if (en) hit.labelEn else hit.labelFr
        conditionRisks += if (en) "Contains $label — you've flagged this as an allergen" else "Contient $label — vous avez signalé cet allergène"
    }
    if (profile.diet != fr.scanneat.domain.engine.scoring.DietKey.NONE) {
        val dietResult = fr.scanneat.domain.engine.scoring.checkDiet(product, profile.diet, lang)
        if (!dietResult.compliant && dietResult.violations.isNotEmpty()) {
            val list = dietResult.violations.joinToString(", ")
            conditionRisks += if (en) "Not compliant with your diet — contains: $list" else "Non compatible avec votre régime — contient : $list"
        }
    }

    // Category-relative, matching PersonalScoreEngine.computePersonalScore's own
    // diabetes/hypertension/kidney_disease checks exactly (getThresholds() +
    // the same maxOf-against-the-original-flat-value pattern, and the same SSB
    // definition) - this section's own doc comment above claims these mirror
    // PersonalScoreEngine "exactly" so the two surfaces "never disagree," but
    // before this fix they used flat cutoffs while PersonalScoreEngine had
    // already been made category-aware in a prior round: a condiment at 20g
    // sugar (below its own 30g major tier - no diabetes penalty in the actual
    // score) still showed "high sugar, caution for diabetes" here, and every
    // typical soy sauce/cured meat/fresh meat or fish showed a salt/protein
    // caution the score itself no longer applied - the hint panel and the
    // score visibly disagreeing, exactly what this comment promises can't happen.
    val catThresholds = fr.scanneat.domain.engine.scoring.getThresholds(product.category)
    val isSugarSweetenedBeverage = product.category == fr.scanneat.domain.model.ProductCategory.BEVERAGE_SOFT &&
        n.sugarsG > 5.0 && n.proteinG < 1.0 && n.fiberG < 1.0
    val conditions = profile.healthConditions
    if ("diabetes" in conditions) {
        if (isSugarSweetenedBeverage) {
            conditionRisks += if (en) "Sugar-sweetened beverage — caution advised for diabetes (liquid sugar causes a faster glycemic spike)"
                     else "Boisson sucrée — prudence recommandée en cas de diabète (le sucre liquide provoque un pic glycémique plus rapide)"
        } else if (n.sugarsG >= catThresholds.sugarThresholds.third) conditionRisks += if (en) "High sugar (${n.sugarsG} g/100 g) — caution advised for diabetes"
                                        else "Sucres élevés (${n.sugarsG} g/100 g) — prudence recommandée en cas de diabète"
        else if (n.sugarsG <= catThresholds.sugarThresholds.first) benefits += if (en) "Low sugar — diabetes-friendly" else "Faible en sucres — adapté au diabète"
    }
    if ("hypertension" in conditions) {
        val hypertensionSaltBar = maxOf(1.2, catThresholds.saltThresholds.first)
        if (n.saltG >= hypertensionSaltBar) conditionRisks += if (en) "High salt (${n.saltG} g/100 g) — caution advised for hypertension"
                                     else "Sel élevé (${n.saltG} g/100 g) — prudence recommandée en cas d'hypertension"
        else if (n.saltG <= 0.3) benefits += if (en) "Low salt — hypertension-friendly" else "Faible en sel — adapté à l'hypertension"
    }
    val kidneyProteinBar = maxOf(15.0, catThresholds.proteinG.third)
    if ("kidney_disease" in conditions && n.proteinG >= kidneyProteinBar) {
        conditionRisks += if (en) "High protein (${n.proteinG} g/100 g) — caution advised for kidney disease"
                 else "Protéines élevées (${n.proteinG} g/100 g) — prudence recommandée en cas de maladie rénale"
    }
    conditionRisks += findHealthConditionGuidance(product.ingredients, profile.healthConditions, lang)
    // \b-bounded, not `.contains()` on the normalized name - a plain substring
    // check let bare "mate" match inside "tomate" (tomato) and bare "tea"
    // match inside "steak", firing a false caffeine hint on completely
    // unrelated, extremely common ingredients. Bare "cafe" similarly matched
    // inside "décaféiné"/"decafeine" (decaf).
    val containsCaffeineSource = product.ingredients.any {
        val norm = fr.scanneat.domain.engine.scoring.normalizeForMatching(it.name)
        Regex("""\b(?:cafeine|guarana|yerba mate|mate|the vert|the noir|coffee|tea|cocoa|cacao|cafe)\b""")
            .containsMatchIn(norm)
    }
    if ("pregnancy" in conditions && containsCaffeineSource) {
        // ANSES: pregnant women should keep total caffeine intake under 200 mg/day —
        // a stricter, condition-specific limit than the generic EFSA "75 mg = alertness"
        // claim threshold already reported above for the general population. Flagged
        // on any caffeine-source ingredient, not just declared caffeine content,
        // since coffee/tea/cocoa rarely declare an exact mg figure on-label.
        conditionRisks += if (en) "May contain caffeine — ANSES recommends pregnant women keep total daily caffeine intake under 200 mg"
                 else "Peut contenir de la caféine — l'ANSES recommande de limiter l'apport total en caféine à 200 mg/jour pendant la grossesse"
    }
    // Same alcohol-content check PersonalScoreEngine already runs for its own
    // cancer/depression adjustments — kept in sync so the hint panel never
    // disagrees with the score adjustments section.
    // \b-bounded, not `.contains()` - a plain substring check on "vin " (space-
    // suffixed to avoid matching "vinaigre") missed the case where "vin" is the
    // ingredient's exact/final name with nothing following it.
    val containsAlcohol = product.ingredients.any {
        Regex("""\b(?:alcool|alcohol|vin|wine|bi[eè]re|beer)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.name)
    }
    if ("cancer" in conditions && containsAlcohol) {
        conditionRisks += if (en) "Contains alcohol — WCRF cancer prevention guidance recommends limiting alcohol intake"
                 else "Contient de l'alcool — les recommandations WCRF de prévention du cancer conseillent d'en limiter la consommation"
    }
    if ("depression" in conditions && containsAlcohol) {
        conditionRisks += if (en) "Contains alcohol — can worsen depressive symptoms and interacts with most antidepressants"
                 else "Contient de l'alcool — peut aggraver les symptômes dépressifs et interagit avec la plupart des antidépresseurs"
    }
    // Mirrors PersonalScoreEngine's own sugar/NOVA depression adjustments exactly
    // (same 15.0 g threshold, same two cited cohort studies) so the two surfaces
    // never disagree.
    if ("depression" in conditions && n.sugarsG >= 15.0) {
        conditionRisks += if (en) "High sugar (${n.sugarsG} g/100 g) — prospectively associated with depression risk (Knüppel et al., Whitehall II cohort, Sci Rep 2017)"
                 else "Sucres élevés (${n.sugarsG} g/100 g) — associé de façon prospective au risque de dépression (Knüppel et al., cohorte Whitehall II, Sci Rep 2017)"
    }
    if ("depression" in conditions && product.novaClass == NovaClass.ULTRA_PROCESSED) {
        conditionRisks += if (en) "Ultra-processed (NOVA 4) — prospectively associated with incident depressive symptoms (Adjibade et al., NutriNet-Santé cohort, BMC Medicine 2019)"
                 else "Ultra-transformé (NOVA 4) — associé de façon prospective à l'apparition de symptômes dépressifs (Adjibade et al., cohorte NutriNet-Santé, BMC Medicine 2019)"
    }

    // ---- Facts ----
    facts += if (en) "NOVA processing class: ${product.novaClass.value}/4" else "Classe de transformation NOVA : ${product.novaClass.value}/4"
    facts += if (en) "Energy: ${n.energyKcal} kcal/100 g" else "Énergie : ${n.energyKcal} kcal/100 g"
    if (product.origin != null) facts += if (en) "Origin: ${product.origin}" else "Origine : ${product.origin}"
    n.cholesterolMg?.let { facts += if (en) "Cholesterol: ${it} mg/100 g" else "Cholestérol : ${it} mg/100 g" }
    // Trivia matched against the actual ingredient list — see IngredientFactsDb
    // for why this is a hand-curated substring match rather than LLM-generated.
    facts += findIngredientFacts(product.ingredients, lang)

    // ---- Pair well with / Avoid pairing with ----
    // Two different senses of "pairing" folded into one panel, per their own
    // citation: flavor-network co-occurrence (Ahn et al., the same PairingsDb
    // ResultScreen's separate always-visible PairingsCard already draws on -
    // repeated here so the hint panel alone is a complete answer instead of
    // sending the user hunting for a second card) and nutrient-absorption
    // enhancer/inhibitor interactions, which nothing in the app surfaced at
    // all before this - well-established dietetics, not product-specific
    // medical advice (e.g. non-heme iron absorption enhanced ~3x by vitamin C,
    // inhibited by tannins/calcium — a mechanism, not a claim about this
    // exact product's effect on any one person).
    val pairWell = mutableListOf<String>()
    val avoidPairing = mutableListOf<String>()

    val flavorPairs = fr.scanneat.domain.engine.planning.findPairings(product.name, limit = 4)
    if (flavorPairs.isNotEmpty()) {
        pairWell += if (en) "Goes well with: ${flavorPairs.joinToString(", ")} (flavor-pairing data)"
                    else "Se marie bien avec : ${flavorPairs.joinToString(", ")} (données d'accords culinaires)"
    }

    // NRV threshold for "iron source" = 15% of 14 mg (EU Reg 1169/2011 Annex XIII),
    // same percentage convention as the "high in" benefit checks above, just at
    // the lower "source of" tier since even a moderate iron contribution is
    // worth pairing correctly.
    val isIronSource = (n.ironMg ?: 0.0) >= 2.1
    val isCalciumSource = (n.calciumMg ?: 0.0) >= 120.0
    if (isIronSource) {
        pairWell += if (en) "Pair with a vitamin C source (citrus, peppers, kiwi) in the same meal — vitamin C can enhance non-heme iron absorption up to 3-fold"
                    else "Associez à une source de vitamine C (agrumes, poivron, kiwi) dans le même repas — la vitamine C peut multiplier jusqu'à 3 fois l'absorption du fer non héminique"
        avoidPairing += if (en) "Avoid pairing with tea, coffee, or a high-calcium dairy product in the same meal — tannins and calcium both significantly reduce iron absorption"
                        else "Évitez d'associer thé, café ou un produit laitier riche en calcium dans le même repas — tanins et calcium réduisent tous deux nettement l'absorption du fer"
    } else if (isCalciumSource) {
        // Only fires when the product isn't already the iron source itself,
        // so a food that's rich in both doesn't warn about pairing with itself.
        avoidPairing += if (en) "Avoid taking at the same time as an iron-rich food or supplement — calcium competes with iron for intestinal absorption (space by about 2 hours if both matter to you)"
                        else "Évitez de le prendre en même temps qu'un aliment ou complément riche en fer — le calcium entre en compétition avec le fer pour l'absorption intestinale (espacez d'environ 2 heures si les deux vous concernent)"
    }
    if (containsCaffeineSource && !isIronSource) {
        avoidPairing += if (en) "Avoid pairing with iron-rich meals — the tannins in coffee/tea/cocoa can cut iron absorption by up to 60%"
                        else "Évitez de l'associer à un repas riche en fer — les tanins du café/thé/cacao peuvent réduire l'absorption du fer jusqu'à 60 %"
    }
    n.vitDUg?.let { if (it >= 0.5) pairWell += if (en) "Best absorbed with a source of dietary fat in the same meal — vitamin D is fat-soluble"
                                                else "Mieux absorbée avec une source de matière grasse dans le même repas — la vitamine D est liposoluble" }
    n.zincMg?.let { if (it >= 1.5) pairWell += if (en) "Pairs well with animal protein in the same meal — zinc from animal sources is absorbed more efficiently than from plant sources alone"
                                                else "S'associe bien avec une protéine animale dans le même repas — le zinc d'origine animale est mieux absorbé que celui des seules sources végétales" }
    if (n.fiberG >= 6.0) {
        avoidPairing += if (en) "Very high-fiber foods can reduce the absorption of some minerals and oral medications if eaten at the exact same time — space by 1-2 hours from a supplement or medication dose"
                        else "Les aliments très riches en fibres peuvent réduire l'absorption de certains minéraux et médicaments oraux en cas de prise simultanée — espacez de 1 à 2 heures la prise d'un complément ou d'un médicament"
    }

    return ProductHints(benefits, risks, conditionRisks, facts, pairWell, avoidPairing)
}
