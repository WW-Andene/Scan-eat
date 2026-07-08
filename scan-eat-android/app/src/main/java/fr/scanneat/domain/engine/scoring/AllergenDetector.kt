package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product

// ============================================================================
// ALLERGEN DETECTOR — port of public/core/allergens.js
//
// AUTHORITATIVE:
//   EU Regulation No 1169/2011, Annex II — 14 mandatory allergens.
//   https://eur-lex.europa.eu/eli/reg/2011/1169/oj
//
// EDITORIAL:
//   Detection patterns match common French + English ingredient spellings.
//   They are heuristic — not a substitute for the manufacturer's own
//   declaration. Accented-letter word-boundary bug from the original TS
//   is fixed here the same way: explicit negative lookbehind/lookahead.
// ============================================================================

data class AllergenHit(
    val key: String,
    val labelFr: String,
    val labelEn: String,
    val triggers: List<String>,      // ingredient names that matched
)

// Same b() helper as DietChecker — Unicode-aware word boundary for FR text
private fun a(inner: String): Regex =
    Regex("(?<![a-zà-ÿ])(?:$inner)(?![a-zà-ÿ])", RegexOption.IGNORE_CASE)

private data class AllergenRule(
    val key: String,
    val labelFr: String,
    val labelEn: String,
    val re: Regex,
)

/** EU Annex II allergen keys in regulatory order (1–14). */
val ANNEX_II_KEYS: List<String> = listOf(
    "gluten",       //  1 — Cereals containing gluten
    "crustaceans",  //  2
    "eggs",         //  3
    "fish",         //  4
    "peanuts",      //  5
    "soy",          //  6
    "lactose",      //  7 — Milk / Lactose
    "nuts",         //  8 — Tree nuts
    "celery",       //  9
    "mustard",      // 10
    "sesame",       // 11
    "sulfites",     // 12
    "lupin",        // 13
    "molluscs",     // 14
)

private val RULES: List<AllergenRule> = listOf(
    AllergenRule("gluten", "Gluten", "Gluten",
        a("bl[eé]|froment|seigle|orge|avoine|[eé]peautre|kamut|triticale|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge")),

    AllergenRule("lactose", "Lactose / Lait", "Lactose / Milk",
        a("lait|lactose|lactos[eé]rum|petit[- ]lait|cr[eè]me|beurre|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]ine|caseine|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait")),

    AllergenRule("eggs", "Œufs", "Eggs",
        a("oeufs?|œufs?|jaune d'?oeuf|jaune d'?œuf|blanc d'?oeuf|blanc d'?œuf|ovalbumine|lysozyme")),

    AllergenRule("nuts", "Fruits à coque", "Tree nuts",
        a("noix|amandes?|noisettes?|pistaches?|cajou|p[eé]can|pecan|noix du br[eé]sil|macadamia")),

    AllergenRule("peanuts", "Arachides", "Peanuts",
        a("arachide|cacahu[eè]te|peanut|beurre de cacahu[eè]te")),

    AllergenRule("soy", "Soja", "Soy",
        a("soja|tofu|tempeh|edamame|l[eé]cithine de soja")),

    AllergenRule("fish", "Poisson", "Fish",
        a("poisson|saumon|thon|cabillaud|merlu|sardine|maquereau|hareng|anchois|morue|\\bbar\\b")),

    AllergenRule("crustaceans", "Crustacés", "Crustaceans",
        a("crevettes?|crabes?|homards?|langoustes?|langoustines?|[eé]crevisses?|crustac[eé]s?|shrimps?|prawns?|lobsters?|crayfish")),

    AllergenRule("molluscs", "Mollusques", "Molluscs",
        a("hu[iî]tres?|moules?|coquilles?|calmars?|encornets?|poulpes?|p[eé]toncles?|palourdes?|bigorneaux?|bulots?|mollusques?|oysters?|mussels?|squids?|clams?|scallops?")),

    AllergenRule("sesame", "Sésame", "Sesame",
        a("s[eé]same|tahini|tahin")),

    AllergenRule("celery", "Céleri", "Celery",
        a("c[eé]leri|celery")),

    AllergenRule("mustard", "Moutarde", "Mustard",
        a("moutarde|graines? de moutarde|mustard")),

    AllergenRule("sulfites", "Sulfites", "Sulfites",
        a("sulfites?|dioxyde de soufre|anhydride sulfureux|E22[0-8]|m[eé]tabisulfite|bisulfite")),

    AllergenRule("lupin", "Lupin", "Lupin",
        a("lupin|farine de lupin")),
)

/**
 * Detect EU-mandatory allergens from product ingredient names.
 * Returns one entry per allergen found, with the triggering ingredient names.
 *
 * Port of detectAllergens() from allergens.js.
 */
fun detectAllergens(product: Product, lang: String = "fr"): List<AllergenHit> {
    // key → triggers set
    val hits = mutableMapOf<String, MutableSet<String>>()
    val ruleMap = RULES.associateBy { it.key }

    for (ingredient in product.ingredients) {
        val name = ingredient.name
        for (rule in RULES) {
            if (rule.re.containsMatchIn(name)) {
                hits.getOrPut(rule.key) { mutableSetOf() }.add(name)
            }
        }
    }

    // Return in ANNEX_II order so the UI always shows allergens in EU-canonical order
    return ANNEX_II_KEYS.mapNotNull { key ->
        val triggers = hits[key] ?: return@mapNotNull null
        val rule = ruleMap[key] ?: return@mapNotNull null
        AllergenHit(
            key      = key,
            labelFr  = rule.labelFr,
            labelEn  = rule.labelEn,
            triggers = triggers.toList(),
        )
    }
}

/**
 * Check if any of the user's declared allergens are present in the product.
 * Returns the list of matching AllergenHits.
 */
fun checkUserAllergens(product: Product, userAllergens: Set<String>, lang: String = "fr"): List<AllergenHit> =
    detectAllergens(product, lang).filter { it.key in userAllergens }
