package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Ingredient

// ============================================================================
// NAMED SUBSTANCE DB — the same table-plus-substring-match pattern as
// AdditivesDb, generalized past E-numbers and basic macro/micronutrients:
// named compounds that show up in ingredient
// lists (caffeine, creatine, melatonin, ginseng, ...) with a stable,
// citable verdict — either an EFSA-authorised health claim under
// Regulation (EU) No 432/2012 (List of permitted health claims), or the
// documented absence of one. "No authorised claim" is reported neutrally
// as a caution against unsubstantiated marketing, not as a safety
// judgement — the same spirit as Product.hasHealthClaims elsewhere in
// this codebase, just anchored to a specific named substance instead of
// a generic "carries marketing claims" flag.
//
// Deliberately short: only substances with a genuinely verifiable EFSA
// Register status, not every trending supplement ingredient.
// ============================================================================

private enum class ClaimStatus { AUTHORISED, NONE }

private data class NamedSubstance(
    val names: List<String>,
    val status: ClaimStatus,
    val textFr: String,
    val textEn: String,
)

private val SUBSTANCES: List<NamedSubstance> = listOf(
    NamedSubstance(listOf("cafeine", "guarana", "mate", "yerba mate"), ClaimStatus.AUTHORISED,
        "Caféine : allégation EFSA autorisée — contribue à l'amélioration de la concentration et de l'état de vigilance (à partir de 75 mg).",
        "Caffeine: EFSA-authorised claim — contributes to increased alertness and concentration (from 75 mg)."),
    NamedSubstance(listOf("creatine", "créatine"), ClaimStatus.AUTHORISED,
        "Créatine : allégation EFSA autorisée — augmente la performance physique lors d'exercices répétés de haute intensité et de courte durée (à partir de 3 g/jour).",
        "Creatine: EFSA-authorised claim — increases physical performance in successive bursts of short-term, high-intensity exercise (from 3 g/day)."),
    NamedSubstance(listOf("melatonine", "melatonin"), ClaimStatus.AUTHORISED,
        "Mélatonine : allégation EFSA autorisée — contribue à réduire le temps d'endormissement (à partir de 1 mg, prise proche du coucher).",
        "Melatonin: EFSA-authorised claim — contributes to the reduction of time to fall asleep (from 1 mg, taken close to bedtime)."),
    NamedSubstance(listOf("taurine"), ClaimStatus.NONE,
        "Taurine : aucune allégation de santé autorisée par l'EFSA à ce jour ; l'ANSES a signalé un risque accru lié aux boissons énergisantes combinant taurine, caféine et sucres, en particulier chez les adolescents.",
        "Taurine: no EFSA-authorised health claim to date; the French food safety agency (ANSES) has flagged increased risk from energy drinks combining taurine, caffeine and sugar, particularly in adolescents."),
    NamedSubstance(listOf("collagene", "collagen", "collagene marin", "collagene hydrolyse"), ClaimStatus.NONE,
        "Collagène : les demandes d'allégations santé (fermeté/élasticité de la peau) ont été rejetées par l'EFSA faute de lien de cause à effet démontré.",
        "Collagen: health claim applications (skin firmness/elasticity) have been rejected by EFSA for lack of a demonstrated cause-and-effect relationship."),
    NamedSubstance(listOf("ginseng"), ClaimStatus.NONE,
        "Ginseng : aucune allégation de santé n'a été autorisée par l'EFSA pour le ginseng à ce jour.",
        "Ginseng: no health claim has been authorised by EFSA for ginseng to date."),
    NamedSubstance(listOf("spiruline", "spirulina", "chlorelle", "chlorella"), ClaimStatus.NONE,
        "Spiruline/chlorelle : aucune allégation de santé spécifique n'est autorisée par l'EFSA ; ce sont des sources de protéines et de micronutriments, mais les bénéfices mis en avant commercialement dépassent souvent les données validées.",
        "Spirulina/chlorella: no specific health claim is EFSA-authorised; they're a source of protein and micronutrients, but marketed benefits often outpace the validated evidence."),
    NamedSubstance(listOf("l-theanine", "theanine"), ClaimStatus.NONE,
        "L-théanine : aucune allégation de santé autorisée par l'EFSA à ce jour, malgré son association fréquente avec la caféine dans les compléments et boissons.",
        "L-theanine: no EFSA-authorised health claim to date, despite its frequent pairing with caffeine in supplements and drinks."),
    NamedSubstance(listOf("millepertuis", "st john's wort", "st johns wort", "hypericum"), ClaimStatus.NONE,
        "Millepertuis : aucune allégation de santé autorisée par l'EFSA ; interactions majeures documentées par l'EMA avec de nombreux médicaments (dont les antidépresseurs) — signalez-en la prise à votre médecin.",
        "St John's Wort: no EFSA-authorised health claim; the EMA has documented major interactions with numerous medications (including antidepressants) — disclose use to your doctor."),
)

/**
 * Match the product's ingredient names against the named-substance table.
 * Returns (benefits, cautions) — authorised-claim substances go to
 * benefits, no-authorised-claim substances go to cautions — so callers
 * can append each into the matching ProductHints list.
 */
fun findNamedSubstanceHints(ingredients: List<Ingredient>, lang: String): Pair<List<String>, List<String>> {
    val en = lang == "en"
    val matched = matchIngredientDictionary(ingredients, SUBSTANCES, NamedSubstance::names)
    val (authorised, none) = matched.partition { it.status == ClaimStatus.AUTHORISED }
    return authorised.map { if (en) it.textEn else it.textFr } to none.map { if (en) it.textEn else it.textFr }
}
