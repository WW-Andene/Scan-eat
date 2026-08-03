package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Ingredient

// ============================================================================
// HEALTH CONDITION GUIDANCE DB — closes the gap between PersonalScoreEngine
// (which already personalizes the numeric score for diabetes/hypertension/
// kidney_disease/pregnancy/cancer/depression — see its own file) and the
// hint panel, which previously ignored Profile.healthConditions entirely.
// Same "trace back to a concrete sourced field" discipline as the rest of
// this package: every entry here matches an actual ingredient-list keyword
// and cites the public-health body that publishes the guidance.
//
// Deliberately narrow: only conditions with well-documented, stable,
// non-controversial public food-safety guidance get a dictionary here —
//  - pregnancy: ANSES's own public pregnancy dietary advice (mirrored by
//    most EU national equivalents).
//  - cancer: the same category of raw/unpasteurized food-safety caution,
//    sourced to oncology-organization "food safety during treatment"
//    guidance (ASCO, Macmillan Cancer Support, CDC) — chemotherapy and
//    some cancers suppress the immune system, so the underlying risk
//    (listeriosis, salmonellosis, toxoplasmosis) is the same category of
//    concern as pregnancy, just sourced to a different body. This is
//    deliberately framed as "if undergoing treatment", not "cancer
//    causes/is caused by this ingredient" — no diet-causes-cancer claim
//    is made anywhere in this file.
//  - ibs: Monash University's Low FODMAP Diet research (the protocol NICE/
//    British Dietetic Association guidelines recommend for IBS).
//  - crohn_ibd: Crohn's & Colitis Foundation / NHS low-residue-during-a-flare
//    guidance.
//  - chronic_diarrhea: NHS / Mayo Clinic diarrhea-diet guidance, plus the
//    EU's own mandatory polyol "may induce laxative effects" label warning
//    (Regulation (EC) 1169/2011 Annex III) above 10g/100g.
// The old single "digestive_disorders" bucket this replaced was too
// heterogeneous to map to specific ingredients without guessing - splitting
// it into these three specific, separately-sourced conditions is what makes
// a real ingredient dictionary possible here (see DietAndConditionAdjustments.kt
// for the numeric scoring side of the same split).
// ============================================================================

private data class ConditionGuidance(
    val names: List<String>,
    val textFr: String,
    val textEn: String,
)

// ANSES (Agence nationale de sécurité sanitaire) pregnancy food-safety guidance:
// raw/undercooked meat & fish (toxoplasmosis/listeria/parasites), unpasteurized
// dairy (listeria), high-mercury fish species, liver/vitamin-A-rich offal
// (teratogenic risk at high intake), and deli/cured meats eaten unheated
// (listeria) — https://www.anses.fr, "Grossesse et alimentation".
private val PREGNANCY_GUIDANCE: List<ConditionGuidance> = listOf(
    ConditionGuidance(listOf("tartare", "carpaccio", "viande crue", "steak tartare", "raw meat", "rare beef"),
        "Viande crue ou peu cuite : risque de toxoplasmose et de listériose — l'ANSES recommande une cuisson à cœur pendant la grossesse.",
        "Raw or undercooked meat: risk of toxoplasmosis and listeriosis — French food safety agency ANSES recommends thorough cooking during pregnancy."),
    ConditionGuidance(listOf("sushi", "sashimi", "saumon fumé", "smoked salmon", "poisson cru", "raw fish", "ceviche"),
        "Poisson cru, fumé à froid ou mariné : risque de listériose et de parasitose — l'ANSES recommande d'éviter ces préparations pendant la grossesse.",
        "Raw, cold-smoked or marinated fish: risk of listeriosis and parasitic infection — ANSES recommends avoiding these preparations during pregnancy."),
    ConditionGuidance(listOf("thon", "espadon", "requin", "lamproie", "tuna", "swordfish", "shark", "marlin"),
        "Poisson prédateur à teneur élevée en mercure : consommation à limiter pendant la grossesse (ANSES / recommandations européennes sur le mercure).",
        "High-mercury predatory fish: intake should be limited during pregnancy (ANSES / EU mercury-exposure guidance)."),
    ConditionGuidance(listOf("lait cru", "fromage au lait cru", "fromage à pâte molle", "raw milk", "unpasteurized", "soft cheese"),
        "Lait ou fromage au lait cru (notamment pâtes molles) : risque de listériose — l'ANSES recommande de privilégier les produits pasteurisés pendant la grossesse.",
        "Raw-milk dairy (especially soft cheeses): risk of listeriosis — ANSES recommends pasteurized products during pregnancy."),
    ConditionGuidance(listOf("foie", "pâté", "liver", "pate", "foie gras"),
        "Foie et produits à base de foie : très riches en vitamine A, dont l'excès est associé à un risque tératogène — l'ANSES recommande d'en limiter la consommation pendant la grossesse.",
        "Liver and liver-based products: very high in vitamin A, whose excess intake carries a documented teratogenic risk — ANSES recommends limiting intake during pregnancy."),
    ConditionGuidance(listOf("jambon cru", "charcuterie", "salami", "chorizo", "cured meat", "deli meat", "prosciutto"),
        "Charcuterie consommée sans cuisson : risque de listériose et de toxoplasmose — l'ANSES recommande de la faire chauffer avant consommation pendant la grossesse.",
        "Cured/deli meat eaten without cooking: risk of listeriosis and toxoplasmosis — ANSES recommends heating it before eating during pregnancy."),
    ConditionGuidance(listOf("germes crus", "pousses crues", "graines germées", "raw sprouts", "alfalfa sprouts"),
        "Graines germées crues : risque de contamination bactérienne (salmonelle, E. coli) — l'ANSES recommande de les cuire pendant la grossesse.",
        "Raw sprouts: risk of bacterial contamination (salmonella, E. coli) — ANSES recommends cooking them during pregnancy."),
)

// Oncology "food safety during treatment" guidance (ASCO, Macmillan Cancer
// Support, CDC) — chemotherapy and some cancers suppress the immune system,
// making the same category of foodborne-illness risk (listeria, salmonella,
// toxoplasmosis) a documented concern; this is neutropenic-diet-style
// caution, not a diet-and-cancer-causation claim.
private val CANCER_GUIDANCE: List<ConditionGuidance> = listOf(
    ConditionGuidance(listOf("tartare", "carpaccio", "viande crue", "steak tartare", "raw meat", "rare beef"),
        "Viande crue ou peu cuite : un système immunitaire affaibli par un traitement oncologique augmente le risque d'infection alimentaire — une cuisson à cœur est généralement recommandée par les équipes soignantes (ASCO, Macmillan Cancer Support).",
        "Raw or undercooked meat: a weakened immune system during cancer treatment raises foodborne-illness risk — oncology care teams (ASCO, Macmillan Cancer Support) typically recommend thorough cooking."),
    ConditionGuidance(listOf("sushi", "sashimi", "saumon fumé", "smoked salmon", "poisson cru", "raw fish", "ceviche"),
        "Poisson cru ou fumé à froid : risque infectieux accru en cas d'immunodépression liée au traitement — à éviter selon les recommandations usuelles en oncologie (ASCO, Macmillan Cancer Support).",
        "Raw or cold-smoked fish: elevated infection risk if immunocompromised by treatment — typically discouraged per standard oncology guidance (ASCO, Macmillan Cancer Support)."),
    ConditionGuidance(listOf("lait cru", "fromage au lait cru", "fromage à pâte molle", "raw milk", "unpasteurized", "soft cheese"),
        "Lait ou fromage au lait cru : risque de listériose accru en cas d'immunodépression — les produits pasteurisés sont généralement recommandés pendant un traitement oncologique.",
        "Raw-milk dairy: elevated listeriosis risk if immunocompromised — pasteurized products are typically recommended during cancer treatment."),
    ConditionGuidance(listOf("jambon cru", "charcuterie", "salami", "chorizo", "cured meat", "deli meat", "prosciutto"),
        "Charcuterie consommée sans cuisson : risque infectieux accru en cas d'immunodépression — à faire chauffer avant consommation selon les recommandations usuelles en oncologie.",
        "Cured/deli meat eaten without cooking: elevated infection risk if immunocompromised — typically recommended to heat before eating per standard oncology guidance."),
    ConditionGuidance(listOf("germes crus", "pousses crues", "graines germées", "raw sprouts", "alfalfa sprouts"),
        "Graines germées crues : risque de contamination bactérienne accru en cas d'immunodépression — à cuire selon les recommandations usuelles en oncologie.",
        "Raw sprouts: elevated bacterial-contamination risk if immunocompromised — typically recommended to cook per standard oncology guidance."),
)

// Monash University Low FODMAP Diet ingredient list (the protocol NICE/
// British Dietetic Association guidelines recommend for IBS) - fructan/GOS
// sources and sugar alcohols, the two ingredient classes most consistently
// linked to IBS symptom flares.
private val IBS_GUIDANCE: List<ConditionGuidance> = listOf(
    ConditionGuidance(listOf("oignon", "onion", "ail", "garlic"),
        "Oignon/ail : source de fructanes, un FODMAP fréquemment associé aux poussées de symptômes du syndrome de l'intestin irritable (recherche de l'université Monash).",
        "Onion/garlic: a fructan (FODMAP) source frequently linked to IBS symptom flares (Monash University research)."),
    ConditionGuidance(listOf("blé", "wheat", "seigle", "rye", "orge", "barley", "inuline", "inulin", "chicorée", "chicory"),
        "Blé/seigle/orge ou inuline : sources de fructanes, un FODMAP fréquemment associé aux poussées de symptômes du SII (recherche de l'université Monash).",
        "Wheat/rye/barley or inulin: fructan (FODMAP) sources frequently linked to IBS symptom flares (Monash University research)."),
    ConditionGuidance(listOf("pois chiches", "chickpeas", "lentilles", "lentils"),
        "Légumineuses (pois chiches, lentilles) : source de galacto-oligosaccharides (GOS), un FODMAP fréquemment associé aux poussées de symptômes du SII (recherche de l'université Monash).",
        "Legumes (chickpeas, lentils): a galacto-oligosaccharide (GOS/FODMAP) source frequently linked to IBS symptom flares (Monash University research)."),
    ConditionGuidance(listOf("sorbitol", "mannitol", "xylitol", "maltitol", "erythritol", "isomalt", "lactitol", "polyols"),
        "Polyol (sorbitol, mannitol, xylitol...) : mal absorbé et osmotiquement actif, un déclencheur connu des symptômes du SII (régime pauvre en FODMAP, université Monash).",
        "Sugar alcohol (sorbitol, mannitol, xylitol...): poorly absorbed and osmotically active, a known IBS symptom trigger (Monash Low FODMAP research)."),
)

// Crohn's & Colitis Foundation / NHS "eating during a flare" guidance: whole
// grains/bran/nuts/seeds/raw skins are the concrete insoluble-fiber sources
// most consistently named as harder to pass through an inflamed gut.
private val CROHN_IBD_GUIDANCE: List<ConditionGuidance> = listOf(
    ConditionGuidance(listOf("son de blé", "son d'avoine", "bran", "graines de lin", "flaxseed", "graines de chia", "chia seeds", "noix", "amandes", "nuts", "almonds", "pop-corn", "popcorn"),
        "Fibres insolubles (son, graines, noix, pop-corn) : un régime pauvre en résidus est généralement conseillé lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation, NHS).",
        "Insoluble fiber (bran, seeds, nuts, popcorn): a low-residue diet is commonly advised during a Crohn's/IBD flare (Crohn's & Colitis Foundation, NHS)."),
    ConditionGuidance(listOf("pain complet", "pain intégral", "whole wheat bread", "wholegrain", "farine complète", "whole grain"),
        "Céréales complètes : riches en fibres insolubles, généralement déconseillées lors d'une poussée de Crohn/MICI au profit d'un régime pauvre en résidus (Crohn's & Colitis Foundation, NHS).",
        "Whole grains: high in insoluble fiber, commonly discouraged during a Crohn's/IBD flare in favor of a low-residue diet (Crohn's & Colitis Foundation, NHS)."),
)

// NHS / Mayo Clinic diarrhea-diet guidance, plus the EU's own mandatory
// polyol "may induce laxative effects" label warning (Regulation (EC)
// 1169/2011 Annex III) above 10g/100g - the same osmotic mechanism IBS's
// polyol entry above cites, just sourced to the general diarrhea-diet
// guidance rather than the IBS-specific Monash protocol.
private val CHRONIC_DIARRHEA_GUIDANCE: List<ConditionGuidance> = listOf(
    ConditionGuidance(listOf("sorbitol", "mannitol", "xylitol", "maltitol", "erythritol", "isomalt", "lactitol", "polyols"),
        "Polyol (sorbitol, mannitol, xylitol...) : attire l'eau dans l'intestin par effet osmotique et peut aggraver la diarrhée — l'UE impose un étiquetage \"effet laxatif\" au-delà de 10 g/100 g (règlement (UE) n°1169/2011).",
        "Sugar alcohol (sorbitol, mannitol, xylitol...): osmotically draws water into the bowel and can worsen diarrhea — the EU mandates a \"may have a laxative effect\" label above 10 g/100 g (Regulation (EU) 1169/2011)."),
    ConditionGuidance(listOf("café", "coffee", "thé", "tea", "cacao", "cocoa", "caféine", "caffeine"),
        "Caféine : stimulant intestinal pouvant aggraver la diarrhée (recommandations NHS sur l'alimentation en cas de diarrhée).",
        "Caffeine: a gut stimulant that can worsen diarrhea (NHS diarrhea-diet guidance)."),
)

private val GUIDANCE_BY_CONDITION: Map<String, List<ConditionGuidance>> = mapOf(
    "pregnancy" to PREGNANCY_GUIDANCE,
    "cancer" to CANCER_GUIDANCE,
    "ibs" to IBS_GUIDANCE,
    "crohn_ibd" to CROHN_IBD_GUIDANCE,
    "chronic_diarrhea" to CHRONIC_DIARRHEA_GUIDANCE,
)

/**
 * Cross-reference the product's ingredient list against Profile.healthConditions.
 * Only conditions present in GUIDANCE_BY_CONDITION have a mapped dictionary —
 * see this file's header for why the other free-form condition keys aren't
 * guessed at.
 */
fun findHealthConditionGuidance(ingredients: List<Ingredient>, healthConditions: Set<String>, lang: String): List<String> {
    val en = lang == "en"
    return healthConditions
        .mapNotNull { GUIDANCE_BY_CONDITION[it] }
        .flatMap { dictionary -> matchIngredientDictionary(ingredients, dictionary, ConditionGuidance::names) }
        .distinct()
        .map { if (en) it.textEn else it.textFr }
}
