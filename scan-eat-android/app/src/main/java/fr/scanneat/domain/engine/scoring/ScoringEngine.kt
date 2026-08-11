package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import fr.scanneat.util.formatDecimal
import kotlin.math.roundToInt

// ============================================================================
// FOOD SCORING ENGINE v2.3.0 — Kotlin port of scoring-engine.ts
//
// Authoritative vs Editorial boundary preserved from original.
// See scoring-engine.ts header for full provenance notes.
//
// Main entry: scoreProduct(product: Product): ScoreAudit
// Pure function — no I/O, no side effects.
//
// Orchestrator only — each pillar's scoring logic lives in its own file
// (ProcessingPillar.kt, NutritionalDensityPillar.kt, NegativeNutrientsPillar.kt,
// AdditiveRiskPillar.kt, IngredientIntegrityPillar.kt), category thresholds in
// CategoryThresholds.kt, shared keyword constants in ScoringKeywords.kt. Was
// previously a single 724-line file with all of the above inline.
// ============================================================================

const val ENGINE_VERSION = "2.3.1"

// ============================================================================
// SECTION 10: GLOBAL MODIFIERS, VETOES & ORCHESTRATOR
// ============================================================================

// internal (not private) so callers that need an aggregate/derived grade from a raw
// score - e.g. DashboardViewModel's weekly average - reuse the real breakpoints
// instead of hand-maintaining a second set that can drift out of sync with them,
// same class of bug ScanHistoryViewModel.gradeFilter's own doc comment documents
// (a previous hand-maintained 80/60/40 range disagreeing with these real values).
// 7 bands, not 6 - E inserted between D and F (splitting the old <25 "F"
// range into E [10,25) and F [0,10)) so this app's own worst tiers have a
// letter-for-letter match with Nutri-Score's native A-E scale. A+/A/B/C/D's
// existing boundaries (85/70/55/40/25) are untouched, so every veto cap tuned
// against those floors (VetoCap object below) still lands on the same grade
// it always did.
internal fun scoreToGrade(score: Int): Grade = when {
    score >= 85 -> Grade.A_PLUS
    score >= 70 -> Grade.A
    score >= 55 -> Grade.B
    score >= 40 -> Grade.C
    score >= 25 -> Grade.D
    score >= 10 -> Grade.E
    else        -> Grade.F
}

// Direct, not softened: a prior pass reworded D/F to "best kept occasional"
// phrasing on food-anxiety grounds, but that traded scientific bluntness for
// comfort rather than correcting a factual error - unlike the caffeine/
// nitrite fixes elsewhere in this file, which corrected an actual
// misattribution (wrong unit, wrong classification target). The grade itself
// already IS the calibrated, evidence-based verdict; euphemizing the verdict
// text undermines that without adding accuracy.
private fun gradeVerdict(grade: Grade, lang: String = "en"): String = if (lang == "en") when (grade) {
    Grade.A_PLUS -> "Excellent — daily staple potential"
    Grade.A      -> "Good — regular consumption fine"
    Grade.B      -> "Acceptable — moderate frequency"
    Grade.C      -> "Mediocre — occasional only"
    Grade.D      -> "Poor — avoid regular use"
    Grade.E      -> "Very poor — avoid"
    Grade.F      -> "Extremely poor — avoid"
} else when (grade) {
    Grade.A_PLUS -> "Excellent — potentiel de consommation quotidienne"
    Grade.A      -> "Bon — consommation régulière adaptée"
    Grade.B      -> "Acceptable — fréquence modérée"
    Grade.C      -> "Médiocre — occasionnel uniquement"
    Grade.D      -> "Mauvais — à éviter en usage régulier"
    Grade.E      -> "Très mauvais — à éviter"
    Grade.F      -> "Extrêmement mauvais — à éviter"
}

private fun computeGlobalBonuses(product: Product, lang: String = "en"): List<Deduction> {
    val en = lang == "en"
    val bonuses = mutableListOf<Deduction>()
    if (product.organic) bonuses += Deduction("global_bonus", if (en) "Organic certification" else "Certification biologique", 2.0, Severity.INFO)
    if (product.wholeGrainPrimary) bonuses += Deduction("global_bonus", if (en) "Whole grain as primary grain" else "Céréale complète en ingrédient principal", 3.0, Severity.INFO)
    if (product.fermented) bonuses += Deduction("global_bonus", if (en) "Contains fermented / probiotic content" else "Contient des éléments fermentés / probiotiques", 2.0, Severity.INFO)
    // Omega-3 bonus lives solely in NutritionalDensityPillar.scoreNutritionalDensity()
    // (+3, using the same ingredient regex plus a nutrition-value check) - it was
    // duplicated here too, double-counting the same signal for +5 total on any
    // product containing e.g. flaxseed or salmon.
    return bonuses
}

private fun computeGlobalPenalties(product: Product, severeFlagCount: Int, lang: String = "en"): List<Deduction> {
    val en = lang == "en"
    val penalties = mutableListOf<Deduction>()
    if (product.hasMisleadingMarketing) penalties += Deduction("global_penalty", if (en) "Misleading marketing claims" else "Allégations marketing trompeuses", -2.0, Severity.MODERATE)
    if (product.hasHealthClaims) penalties += Deduction("global_penalty", if (en) "Health claims present — verify vs composition" else "Allégations de santé présentes — à vérifier vs composition", -3.0, Severity.MODERATE)
    val palm = product.ingredients.find { ing ->
        Regex("""huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (palm != null) penalties += Deduction("global_penalty", (if (en) "Palm oil or derivative: " else "Huile de palme ou dérivé : ") + palm.name, -3.0, Severity.MODERATE)
    // Cumulative vigilance malus: each pillar's own score floors at 0 (never
    // negative), so a product that maxes out its deductions in several
    // *independent* pillars at once (e.g. critical sugar AND critical sat fat
    // AND high additive risk) never loses more than any single one of those
    // pillars' own max points — the overflow past each floor is silently
    // discarded instead of compounding. This adds a small, capped penalty back
    // in proportion to how many MAJOR/CRITICAL flags fired across all 5
    // pillars combined, so being bad in many independent ways scores worse
    // than being bad in just one.
    when {
        severeFlagCount >= 8 -> penalties += Deduction("global_penalty", if (en) "$severeFlagCount major/critical flags — very high cumulative risk" else "$severeFlagCount signalements majeurs/critiques — risque cumulé très élevé", -6.0, Severity.MAJOR)
        severeFlagCount >= 6 -> penalties += Deduction("global_penalty", if (en) "$severeFlagCount major/critical flags — high cumulative risk" else "$severeFlagCount signalements majeurs/critiques — risque cumulé élevé", -4.0, Severity.MODERATE)
        severeFlagCount >= 4 -> penalties += Deduction("global_penalty", if (en) "$severeFlagCount major/critical flags — elevated cumulative risk" else "$severeFlagCount signalements majeurs/critiques — risque cumulé modéré", -2.0, Severity.MINOR)
    }
    return penalties
}

// A named, documented ladder instead of a magic number chosen per veto to
// "feel right" in isolation - this is the actual generalization the alcohol
// audit called for. Alcohol wasn't a one-off bug: it was this engine's first
// instance of a hazard needing a tiered, no-safe-level veto, and every veto
// added since (nitrites, banned additives, caffeine) picked its own cap by
// local reasoning rather than checking it against the others. That already
// produced one live inconsistency this ladder fixes: high-proof spirits
// (the single most concentrated Group-1-carcinogen exposure this engine
// scores) capped at 40 - a full 5 points LOOSER than trans fat's 39, and even
// looser than the banned-additive veto's 35, despite being at least as severe
// as either. Every veto below must be assigned one of these named tiers, and
// any new veto added in the future should be justified against this scale
// rather than picking a fresh number.
private object VetoCap {
    // Reserved for a hazard severe enough to force grade F outright - not
    // currently used by any veto, kept named so a future one has somewhere
    // principled to land instead of inventing a number. 9, not 20 - since E
    // was inserted as its own grade band ([10,25)), a cap of 20 would now
    // only force E, not F; the F floor is <10, so this must sit below that.
    const val EXTREME = 9
    // Worst tier actually in use: concentrated exposure to a WHO/IARC Group 1
    // carcinogen (high-proof spirits), or a sugar-sweetened beverage with
    // zero redeeming nutritional contribution. WHO's SSB guidance (limit
    // free sugars to <10% energy) is a population-level dietary-pattern
    // recommendation, not a per-100g product classification - same
    // population-vs-product distinction the nitrite and caffeine vetoes
    // below already draw explicitly; this cap is this engine's own severity
    // call informed by that guidance, not a claim WHO scores individual
    // products this way.
    const val SEVERE = 30
    // A single severe, specific risk factor with strong evidence: one
    // EU-banned or IARC-flagged additive.
    const val MAJOR = 35
    // A risk factor clearly over its category's own "critical" threshold -
    // forces grade D (scoreToGrade's C floor is 40, so 39 guarantees D, not a
    // coincidental landing exactly on the C floor).
    const val MODERATE = 39
    // A real but more moderate single-hazard signal: wine-strength alcohol,
    // standalone nitrite/nitrate presence without the corroborating high-
    // salt/refined-starch combo, high caffeine, mechanically-separated meat.
    const val MILD = 45
    // The weakest tier of "no safe level" hazard still worth a hard cap:
    // trace/low-dose presence (beer-strength alcohol). Forces grade C, not B
    // (scoreToGrade's B floor is 55, so 54 guarantees C).
    const val MINOR = 54
}

private fun checkVeto(product: Product, lang: String = "en"): VetoCondition {
    val en = lang == "en"
    val n = product.nutrition

    // Collect every triggered condition and return the most restrictive (lowest
    // cap) one, instead of the first one that happens to match. These checks
    // aren't mutually exclusive - e.g. a sugar-sweetened beverage (VetoCap.SEVERE)
    // can also contain >3 Tier-1 additives (VetoCap.MODERATE) - and a
    // first-match-wins scan silently applied whichever check happened to run
    // first regardless of which cap was actually stricter. That used to
    // require a one-off manual reordering of just the beverage-vs-generic-sugar
    // pair to work around one specific collision; it didn't generalize to any
    // other pair. See the VetoCap object above for the shared cap ladder every
    // veto below is assigned from.
    val candidates = mutableListOf<VetoCondition>()

    if ((n.transFatG ?: 0.0) > 0.1)
        candidates += VetoCondition(true, if (en) "Contains industrial trans fats — no safe level" else "Contient des graisses trans industrielles — aucun seuil sûr", VetoCap.MODERATE)

    if (countTier1Additives(product) > 3)
        candidates += VetoCondition(true, (if (en) "${countTier1Additives(product)} Tier-1 additives — cumulative risk too high" else "${countTier1Additives(product)} additifs de niveau 1 — risque cumulé trop élevé"), VetoCap.MODERATE)

    // Severity-aware, not just a count threshold: countTier1Additives(product)
    // > 3 above only fires on volume, so a product with exactly one EU-banned
    // or IARC-classified additive (e.g. a single trace of titanium dioxide,
    // E171, banned outright in the EU since 2022) never triggered any veto on
    // its own - only the flat, easily-absorbed Tier-1 pillar deduction. These
    // five E-numbers are explicitly documented as banned/IARC-flagged in
    // AdditivesTier1.kt (E249/E250/E251/E252 nitrites/nitrates are already
    // covered by the standalone nitrite veto below, so excluded here to avoid
    // a redundant, weaker-worded duplicate).
    val bannedTier1ENumbers = setOf("E171", "E127", "E924", "E216", "E217")
    val hasBannedTier1 = product.ingredients.any { ing ->
        val eNum = (ing.eNumber ?: "").uppercase().replace("\\s".toRegex(), "")
        eNum in bannedTier1ENumbers
    }
    // MAJOR, stricter than the volume-based >3-Tier1-additives veto's MODERATE
    // just above - this veto exists precisely because raw additive COUNT
    // misses severity (one banned/IARC-flagged additive is worse than four
    // unspecified Tier-1 ones), so it would be incoherent for its own cap to
    // be looser than the check it was built to complement.
    if (hasBannedTier1)
        candidates += VetoCondition(true, if (en) "Contains an additive banned or restricted for carcinogenicity/endocrine concerns in the EU" else "Contient un additif interdit ou restreint pour cancérogénicité/perturbation endocrinienne dans l'UE", VetoCap.MAJOR)

    val hasNitrites = product.ingredients.any { ing ->
        val eNum = (ing.eNumber ?: "").uppercase().replace("\\s".toRegex(), "")
        eNum == "E249" || eNum == "E250" || ing.name.lowercase().let { it.contains("nitrite") || it.contains("e249") || it.contains("e250") }
    }
    // Category-relative, not a flat bar: PROCESSED_MEAT's own thresholds (dry-cured
    // meat is structurally ~2.5-6g/100g salt) put "moderate" at 4.0g, not the
    // generic-food 1.5g this veto used to hard-code - a flat 1.5 was stricter than
    // even that category's own "minor" tier (2.5g), so nearly every typical
    // nitrite-cured product (ham ~1.6-2g, bacon ~2.5g, salami ~3-4g) combined with
    // any refined-starch filler tripped this veto even when its own category-aware
    // salt scoring would call the same salt level merely "typical."
    val highSalt = n.saltG > getThresholds(ProductCategory.PROCESSED_MEAT).saltThresholds.second
    val refined = product.ingredients.any { Regex("""farine de blé|farine raffinée|amidon|dextrose""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasNitrites && highSalt && refined && product.category == ProductCategory.PROCESSED_MEAT)
        candidates += VetoCondition(true, if (en) "Processed meat with nitrites + high salt + refined starch" else "Viande transformée avec nitrites + sel élevé + amidon raffiné", VetoCap.MODERATE)

    // User-reported (via the alcohol scoring audit): the combo veto above only
    // fires for PROCESSED_MEAT with high salt AND a refined-starch ingredient -
    // AdditivesTier1.kt labels E249/E250 "IARC Group 1 (processed meat,
    // carcinogenic to humans)", the same carcinogen classification that
    // justifies the alcohol veto elsewhere in this function, but nitrite/
    // nitrate in a fish, ready-meal, or sandwich product (any category other
    // than PROCESSED_MEAT), or a cured meat that happens to sit under the
    // salt/starch bar, previously never triggered any veto at all - only the
    // flat, easily-absorbed Tier-1 additive deduction in AdditiveRiskPillar.kt.
    // A softer tier (MILD) than the full combo's MODERATE since presence alone
    // (without the corroborating high-salt/refined-starch signal) is a real
    // but less compounded risk - candidates.minByOrNull{cap} below still picks
    // the stricter combo veto automatically whenever both conditions hold.
    // Wording is deliberately about the additive class, not a causal claim
    // about this specific product - IARC's Group 1 classification is for
    // PROCESSED MEAT CONSUMPTION as a dietary pattern (population-level
    // epidemiological association over years), not a standalone verdict on
    // one ingredient-list entry. Stating it as "IARC Group 1 carcinogen"
    // flat-out, as if labeling the compound itself, overstates what the
    // classification actually established.
    // Guarded by !(combo condition) - whenever the combo veto above fires,
    // hasNitrites is always true too (it's a strict subset of the combo's
    // conditions), so both candidates used to always co-occur. checkVeto's
    // combinedReason below joins every candidate's reason text, which meant
    // a cured/salty/starch-filled product's flags read as two separate
    // sentences both about the same nitrite content - one fact double-stated
    // as if it were two corroborating risks, overstating the evidence to
    // whoever reads the flags. The standalone MILD veto exists to catch
    // nitrites WITHOUT the corroborating salt/starch/category signal (see
    // comment above); once that signal is present, the stricter MODERATE
    // combo already covers it and doesn't need restating.
    if (hasNitrites && !(highSalt && refined && product.category == ProductCategory.PROCESSED_MEAT))
        candidates += VetoCondition(true, if (en) "Contains nitrite/nitrate preservatives (E249/E250), linked by IARC to processed-meat consumption at a population level" else "Contient des conservateurs nitrités (E249/E250), associés par le CIRC à la consommation de viande transformée au niveau populationnel", VetoCap.MILD)

    val sugars = n.addedSugarsG ?: n.sugarsG
    if (product.category == ProductCategory.BEVERAGE_SOFT && sugars > 5 && n.proteinG < 1 && n.fiberG < 1)
        candidates += VetoCondition(true, if (en) "Sugar-sweetened beverage with no nutritional contribution" else "Boisson sucrée sans apport nutritionnel", VetoCap.SEVERE)

    // CONDIMENT excluded too, alongside SNACK_SWEET — CategoryThresholds.kt
    // deliberately gives it a wider sugar band (30g is only its own "major"
    // tier, critical starts at 45g: chutneys/glazes/hoisin-style sauces are
    // eaten by the tablespoon, not the 100g this scale is normalized to). This
    // flat 30g line ignored that category-specific tolerance and hard-capped
    // such a condiment to grade C even though the pillar itself only scored it
    // MAJOR, not CRITICAL - the same category-blindness class already fixed
    // for BMI/diabetes thresholds elsewhere in this engine.
    // SPREAD_SWEET (honey/jam) exempted for the same eaten-by-the-tablespoon
    // reasoning, now with its own thresholds tuned to intrinsic fruit sugar
    // (40/55/70/85) instead of sharing CONDIMENT's savory-sauce band.
    if (product.category != ProductCategory.SNACK_SWEET && product.category != ProductCategory.CONDIMENT &&
        product.category != ProductCategory.SPREAD_SWEET && sugars > 30)
        candidates += VetoCondition(true, if (en) "Added sugar >30g/100g in non-confectionery" else "Sucre ajouté >30g/100g dans un produit non-confiserie", VetoCap.MODERATE)

    val hasMSM = product.ingredients.any { Regex("""séparée mécaniquement|mechanically separated|msm""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasMSM && product.novaClass == NovaClass.ULTRA_PROCESSED)
        candidates += VetoCondition(true, if (en) "Mechanically separated meat in NOVA 4 product" else "Viande séparée mécaniquement dans un produit NOVA 4", VetoCap.MILD)

    // High-proof spirits (~40%+ vol) have low/no sugar and no additive risk, so
    // the tiered per-mille deduction in NegativeNutrientsPillar.kt (max -12) is
    // not enough on its own to keep a clean-profile spirit out of grade A/B.
    // WHO/IARC classify ethanol as a Group 1 carcinogen with no established
    // safe consumption level, and at 40%+ vol this is the single most
    // concentrated instance of that exposure this engine scores - VetoCap.SEVERE,
    // its strictest tier actually in use, reflects that (previously capped at
    // a coincidental 40, looser than trans fat's own tier despite being at
    // least as severe - see the VetoCap object's own comment for why that was
    // incoherent).
    //
    // This veto previously only fired above HIGH_ABV_THRESHOLD (15%), so an
    // otherwise clean-profile beer (~4-6%) or wine (~11-14%) never triggered
    // it - only the tiered -6/-9 deduction above applied, which a beer/wine
    // with unremarkable sugar/salt/additives could absorb and still land at
    // grade B. WHO/IARC classify ethanol as equally "no safe level" regardless
    // of concentration, the same way trans fat's veto fires at any presence
    // above the detection floor, not just at a high dose - so it's tiered by
    // the same %vol bands NegativeNutrientsPillar.kt uses (rather than one
    // flat cap for all alcohol), so a spirit is still capped more severely
    // than a beer.
    val abv = n.alcoholPercentVol ?: 0.0
    when {
        abv > HIGH_ABV_THRESHOLD ->
            candidates += VetoCondition(true, if (en) "High-proof alcohol (${abv.formatDecimal(1)}% vol) — no safe consumption level" else "Alcool fort (${abv.formatDecimal(1)}% vol) — aucun seuil de consommation sûr", VetoCap.SEVERE)
        abv > 5.0 ->
            candidates += VetoCondition(true, if (en) "Wine-strength alcohol (${abv.formatDecimal(1)}% vol) — no safe consumption level" else "Alcool titrant comme un vin (${abv.formatDecimal(1)}% vol) — aucun seuil de consommation sûr", VetoCap.MILD)
        abv > 1.2 ->
            candidates += VetoCondition(true, if (en) "Alcohol (${abv.formatDecimal(1)}% vol) — no safe consumption level" else "Alcool (${abv.formatDecimal(1)}% vol) — aucun seuil de consommation sûr", VetoCap.MINOR)
    }

    // Caffeine — NegativeNutrientsPillar.kt already deducts up to -8/25 for
    // caffeine > 300mg/100g ("well above EFSA single-dose caution level"), but
    // a pillar deduction alone lets a concentrated energy-drink/shot with an
    // otherwise clean sugar/additive profile still land in grade B, the same
    // gap the alcohol veto above was created to close. EFSA sets ~200mg as its
    // single-dose caution level; 300mg/100g is comfortably past that even for
    // a modest single serving, so it gets the same hard-veto treatment as the
    // other "no safe level at this concentration" conditions above.
    // Wording says "typical serving," not a flat "above EFSA's threshold" -
    // EFSA's ~200mg guidance is a per-DOSE figure, not a per-100g one, so
    // stating it as if 300mg/100g were itself the EFSA number misattributes
    // the unit (a concentrated product like instant coffee crystals can
    // exceed 300mg/100g while a realistic single serving, a teaspoon, stays
    // well under 200mg).
    val caffeine = n.caffeineMg ?: 0.0
    if (caffeine > 300.0)
        candidates += VetoCondition(true, if (en) "Caffeine ${caffeine.formatDecimal(1)}mg/100g — concentrated enough that a typical single serving would exceed EFSA's ~200mg single-dose guidance" else "Caféine ${caffeine.formatDecimal(1)}mg/100g — concentration telle qu'une portion normale dépasserait le repère de prudence EFSA d'environ 200mg par prise", VetoCap.MILD)

    // Winning (strictest-cap) reason stays first and still drives the cap,
    // but a product that trips MULTIPLE independent vetoes (e.g. an SSB that
    // also has >3 Tier-1 additives) previously had every reason but the
    // winner's silently discarded - an auditor (or a curious user) trying to
    // reconstruct why a score landed where it did only ever saw one of
    // possibly several real triggers. Same " · " join pattern
    // PersonalScoreEngine.kt's dietReason already uses for the identical
    // "don't let a second real reason silently vanish" problem.
    val winner = candidates.minByOrNull { it.cap } ?: return VetoCondition(false, "", 100)
    val combinedReason = (listOf(winner) + candidates.filter { it !== winner }).joinToString(" · ") { it.reason }
    return VetoCondition(true, combinedReason, winner.cap)
}

private fun buildFlags(audit: ScoreAudit, lang: String = "en"): Pair<List<String>, List<String>> {
    val en = lang == "en"
    val red = mutableListOf<String>()
    val green = mutableListOf<String>()

    val allDeductions = with(audit.pillars) {
        processing.deductions + nutritionalDensity.deductions + negativeNutrients.deductions +
        additiveRisk.deductions + ingredientIntegrity.deductions
    } + audit.globalPenalties

    val allBonuses = with(audit.pillars) {
        processing.bonuses + nutritionalDensity.bonuses + negativeNutrients.bonuses +
        additiveRisk.bonuses + ingredientIntegrity.bonuses
    } + audit.globalBonuses

    for (d in allDeductions) {
        if (d.severity == Severity.CRITICAL || d.severity == Severity.MAJOR) red += d.reason
    }
    for (b in allBonuses) {
        if (b.points >= 2) green += b.reason
    }

    // Eco-score deliberately NOT injected into red/green here (removed a
    // prior version that did) - Eco-Score is an environmental-impact scale
    // (packaging/transport/land use), unrelated to and never actually
    // contributing to this pillar-based health/nutrition Grade's score
    // (audit.eco's value is display-only, never added into `score`
    // anywhere in this file). Mixing its text into the SAME red/green list
    // used to explain "why did the health Grade land here" silently implied
    // environmental impact was one of the reasons, when the app already
    // surfaces Eco-Score correctly and distinctly as its own badge chip
    // (ScoreBadgesRow.kt's ecoscore LetterGradeChip) right next to NutriScore.

    // "Critical:", not "VETO:" - the veto still hard-caps the score exactly
    // as before (no softening of the judgment), this only stops leaking this
    // engine's internal mechanism name ("veto") into end-user copy, which is
    // jargon rather than a claim about severity.
    if (audit.veto.triggered) red.add(0, (if (en) "Critical: " else "Critique : ") + audit.veto.reason)
    return Pair(red, green)
}

private fun collectWarnings(product: Product, lang: String = "en"): List<String> {
    val en = lang == "en"
    val warnings = mutableListOf<String>()
    if (product.nutrition.transFatG == null) warnings += (if (en) "trans_fat_g not declared — assumed 0" else "trans_fat_g non déclaré — supposé 0")
    if (product.nutrition.addedSugarsG == null) warnings += (if (en) "added_sugars_g not declared — using total sugars as proxy" else "added_sugars_g non déclaré — sucres totaux utilisés en approximation")
    // caffeineMg follows the same "nullable, silently treated as 0" shape as
    // transFatG above - many OFF energy-drink entries genuinely lack this
    // field, and without this warning those scans silently assumed
    // caffeine-free with no disclosed uncertainty.
    if (product.nutrition.caffeineMg == null) warnings += (if (en) "caffeine_mg not declared — assumed 0" else "caffeine_mg non déclaré — supposé 0")
    // alcoholPercentVol is architecturally identical to the three fields
    // above - nullable, defaults to 0 via `?: 0.0` in both
    // NegativeNutrientsPillar.kt and checkVeto, and its own doc comment
    // (Product.kt) already says "null means not declared, not alcohol-free" -
    // but this warning was never added, so a cider/cocktail whose OFF entry
    // never scraped alcohol_100g silently scored as 0% ABV with no
    // disclosed uncertainty, unlike caffeine's identical case.
    if (product.nutrition.alcoholPercentVol == null) warnings += (if (en) "alcohol_percent_vol not declared — assumed 0" else "alcohol_percent_vol non déclaré — supposé 0")
    return warnings
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================

/**
 * Score a product. Pure synchronous function.
 * Mirrors scoreProduct() from scoring-engine.ts exactly.
 */
fun scoreProduct(input: Product, lang: String = "en"): ScoreAudit {
    val en = lang == "en"
    // Final fallback: if both OFF and LLM landed on 'other', infer from name
    val product = if (input.category == ProductCategory.OTHER) {
        val inferred = inferCategoryFromName(input.name)
        if (inferred != ProductCategory.OTHER) input.copy(category = inferred) else input
    } else input

    // The 20/25/25/15/15 split across these 5 pillars had no documented
    // rationale anywhere in this file before now - each pillar's own MAX was
    // just a bare `val MAX = N` chosen independently, the exact same
    // "invented in isolation" pattern the VetoCap ladder above was built to
    // close for vetoes, just one level up at the pillar-composition layer.
    // The actual, consistent principle behind the split, made explicit here:
    //   NutritionalDensity (25) and NegativeNutrients (25) — the two largest
    //     shares — are driven by DECLARED NUMERIC VALUES (protein/fiber/
    //     micros; sat fat/sugar/salt/trans fat/alcohol/caffeine) checked
    //     against category-relative, regulatorily-grounded thresholds
    //     (CategoryThresholds.kt, EFSA/WHO caution levels cited throughout
    //     NegativeNutrientsPillar.kt) - the highest-certainty signals this
    //     engine has access to, and NegativeNutrients is also the primary
    //     vehicle for checkVeto's hard caps.
    //   Processing (20) sits in between: NOVA class is sometimes a directly
    //     declared OFF value and sometimes this engine's own heuristic
    //     inference (inferNovaClassWithConfidence's HIGH/MEDIUM/LOW
    //     confidence tiers) - a real, evidence-linked signal, but one step
    //     more removed from a directly measured number than the two 25s.
    //   AdditiveRisk (15) and IngredientIntegrity (15) are the smallest
    //     shares because both are driven by INGREDIENT-LIST PATTERN
    //     MATCHING (E-number/name lookups, regex heuristics for whole-food/
    //     hidden-sugar/named-oil signals) rather than a directly declared
    //     quantitative value - real signals, but lower-certainty ones, which
    //     is why they're weighted below the numerically-grounded pillars
    //     rather than tied with them.
    val processing          = scoreProcessing(product, lang)
    val nutritionalDensity  = scoreNutritionalDensity(product, lang)
    val negativeNutrients   = scoreNegativeNutrients(product, lang)
    val additiveRisk        = scoreAdditiveRisk(product, lang)
    val ingredientIntegrity = scoreIngredientIntegrity(product, lang)

    val baseScore = processing.score + nutritionalDensity.score + negativeNutrients.score +
                    additiveRisk.score + ingredientIntegrity.score

    val severeFlagCount = listOf(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)
        .sumOf { pillar -> pillar.deductions.count { it.severity == Severity.CRITICAL || it.severity == Severity.MAJOR } }

    val globalBonuses   = computeGlobalBonuses(product, lang)
    val globalPenalties = computeGlobalPenalties(product, severeFlagCount, lang)
    // Deliberately asymmetric, not an oversight: bonusTotal is capped at +10
    // so no combination of positive signals (organic, transparent origin,
    // eco-score, etc.) can outweigh a product's actual pillar-level nutrition
    // math, while penaltyTotal is uncapped because risk factors compound
    // (severeFlagCount already counts CRITICAL/MAJOR deductions across every
    // pillar, so a product with multiple independent severe problems should
    // be able to accumulate a correspondingly larger penalty, not be capped
    // at the same +10 ceiling "goodness" is held to).
    val bonusTotal      = minOf(10.0, globalBonuses.sumOf { it.points })
    val penaltyTotal    = globalPenalties.sumOf { it.points }

    var score = (baseScore + bonusTotal + penaltyTotal).coerceIn(0.0, 100.0)
    val veto = checkVeto(product, lang)
    if (veto.triggered && score > veto.cap) score = veto.cap.toDouble()
    val finalScore = score.roundToInt()
    val grade = scoreToGrade(finalScore)

    val pillars = ScoreAudit.Pillars(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)

    // NOVA-confidence gets the same audit-level promotion category-inference
    // already has just below - both are "this input to the grade was
    // inferred, not declared" signals, but only the category one used to
    // reach `warnings`; the NOVA one was previously visible only as a
    // low-severity INFO line buried inside the Processing pillar's own
    // deduction list, several UI layers away from the headline grade a user
    // actually reads.
    val novaInference = inferNovaClassWithConfidence(product)
    val novaConfidenceWarning = if (product.novaClass == NovaClass.ULTRA_PROCESSED && novaInference.nova.value < 4 && novaInference.confidence != NovaConfidence.HIGH)
        listOf(if (en) "Processing level (NOVA) inferred from ingredients with ${novaInference.confidence.name.lowercase()} confidence, not declared" else "Niveau de transformation (NOVA) déduit des ingrédients avec une confiance ${if (novaInference.confidence == NovaConfidence.LOW) "faible" else "moyenne"}, non déclaré")
    else emptyList()

    val warnings = collectWarnings(product, lang) + novaConfidenceWarning +
        if (product.category != input.category) listOf(if (en) "Category inferred from name as \"${product.category.key}\"" else "Catégorie déduite du nom : \"${product.category.key}\"") else emptyList()

    val preAudit = ScoreAudit(
        productName     = product.name,
        category        = product.category,
        score           = finalScore,
        grade           = grade,
        verdict         = gradeVerdict(grade, lang),
        pillars         = pillars,
        globalBonuses   = globalBonuses,
        globalPenalties = globalPenalties,
        veto            = veto,
        redFlags        = emptyList(),
        greenFlags      = emptyList(),
        eco             = product.ecoscoreGrade?.let { ScoreAudit.EcoInfo(it, product.ecoscoreValue) },
        nutriscoreGrade = product.nutriscoreGrade,
        engineVersion   = ENGINE_VERSION,
        warnings        = warnings,
    )

    val (red, green) = buildFlags(preAudit, lang)
    return preAudit.copy(redFlags = red, greenFlags = green)
}
