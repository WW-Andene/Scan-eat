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
    val triggers: List<String>,      // ingredient names that matched (or the precautionary-labeling note for a trace-only hit)
    // false = confirmed present (ingredient text and/or OFF's allergens_tags).
    // true = ONLY found via OFF's traces_tags precautionary "may contain"
    // labeling, with no confirmed presence elsewhere - a real but lower-
    // certainty cross-contamination signal, distinct from a confirmed
    // allergen. See detectAllergens' traces handling below.
    val isTraceOnly: Boolean = false,
)

// Same b() helper as DietChecker — Unicode-aware word boundary for FR text.
// Digits are excluded from the boundary too, not just letters — otherwise a
// numeric token like "E22" range entries can match as a substring inside a
// longer, unrelated E-number.
private fun a(inner: String): Regex =
    Regex("(?<![a-zà-ÿ0-9])(?:$inner)(?![a-zà-ÿ0-9])", RegexOption.IGNORE_CASE)

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

/** Matches an ingredient explicitly declaring gluten-free oats. */
private val GLUTEN_FREE_OATS_QUALIFIER = Regex("avoine[^,;.]*(sans gluten|gluten[- ]free)|(sans gluten|gluten[- ]free)[^,;.]*avoine", RegexOption.IGNORE_CASE)

/** Any OTHER gluten cereal term (no gluten-free variant exists for these) - if present, don't suppress. */
private val GLUTEN_CEREAL_EXCL_OATS = a("gluten|bl[eé]|froment|seigle|orge|[eé]peautre|kamut|triticale|couscous|boulgour|bulgur|chapelure|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge")

private val RULES: List<AllergenRule> = listOf(
    AllergenRule("gluten", "Gluten", "Gluten",
        a("gluten|bl[eé]|froment|seigle|orge|avoine|[eé]peautre|kamut|triticale|couscous|boulgour|bulgur|chapelure|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge")),

    AllergenRule("lactose", "Lactose / Lait", "Lactose / Milk",
        a("lait|lactose|lactos[eé]rum|petit[- ]lait|cr[eè]me|beurre|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]ine|caseine|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait")),

    // "albumine" alone added - egg-white protein is commonly labeled just
    // "albumine" on French ingredient lists (charcuterie binders, some baked
    // goods), not always "ovalbumine"/"blanc d'oeuf". Missing it was a real
    // false-negative risk on a mandatory Annex II allergen (item 3).
    AllergenRule("eggs", "Œufs", "Eggs",
        a("oeufs?|œufs?|jaune d'?oeuf|jaune d'?œuf|blanc d'?oeuf|blanc d'?œuf|ovalbumine|albumine|lysozyme")),

    AllergenRule("nuts", "Fruits à coque", "Tree nuts",
        a("noix(?! de coco| de muscade)|amandes?|noisettes?|pistaches?|cajou|p[eé]can|pecan|noix du br[eé]sil|macadamia")),

    AllergenRule("peanuts", "Arachides", "Peanuts",
        a("arachide|cacahu[eè]te|peanut|beurre de cacahu[eè]te")),

    AllergenRule("soy", "Soja", "Soy",
        a("soja|tofu|tempeh|edamame|l[eé]cithine de soja")),

    // Species list broadened - truite/colin/lieu/dorade/sole/flétan/eglefin/
    // lotte/tilapia previously absent, so a product listing only e.g. "truite
    // fumée" with no other fish word matched nothing at all, a false negative
    // on a mandatory Annex II allergen (item 4) with real anaphylaxis risk.
    AllergenRule("fish", "Poisson", "Fish",
        a("poisson|saumon|thon|cabillaud|merlu|sardine|maquereau|hareng|anchois|morue|filet de bar|darne de bar|truite|colin|lieu|dorade|sole|fl[eé]tan|[eé]glefin|lotte|tilapia|trout|pollock|haddock|halibut")),

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

    // E22[0-8] required no separator between the letter and digits, missing
    // the common space-separated label form "E 220"/"E 223" (frequent on
    // French labels and in OCR/OFF text). Sulfite sensitivity can trigger
    // asthma/anaphylactoid reactions, so a missed spaced E-number is a real
    // detection gap on a mandatory Annex II allergen (item 12).
    AllergenRule("sulfites", "Sulfites", "Sulfites",
        a("sulfites?|dioxyde de soufre|anhydride sulfureux|E[- ]?22[0-8]|m[eé]tabisulfite|bisulfite")),

    AllergenRule("lupin", "Lupin", "Lupin",
        a("lupin|farine de lupin")),
)

// OFF's own curated allergens_tags → our ANNEX_II keys. OFF verifies these
// against the manufacturer's declaration, so a tag hit is authoritative -
// merged into the regex-based detection below rather than replacing it,
// since OFF's tags are sometimes missing even when the ingredient text
// clearly names the allergen (manufacturer didn't bother tagging it).
private val OFF_ALLERGEN_TAG_MAP: Map<String, String> = mapOf(
    "en:gluten" to "gluten",
    "en:crustaceans" to "crustaceans",
    "en:eggs" to "eggs",
    "en:fish" to "fish",
    "en:peanuts" to "peanuts",
    "en:soybeans" to "soy",
    "en:milk" to "lactose",
    "en:nuts" to "nuts",
    "en:celery" to "celery",
    "en:mustard" to "mustard",
    "en:sesame-seeds" to "sesame",
    "en:sulphur-dioxide-and-sulphites" to "sulfites",
    "en:lupin" to "lupin",
    "en:molluscs" to "molluscs",
)

/**
 * Reverse of [OFF_ALLERGEN_TAG_MAP] — ANNEX_II short key → OFF-style tag.
 * Lets a non-OFF source (the vision LLM's own label reading — see OcrParser.
 * buildLabelPrompt's allergen_declarations field) populate
 * Product.declaredAllergenTags in the same "en:xxx" vocabulary OFF itself
 * uses, so [detectAllergens] needs no separate code path per source.
 */
val ANNEX_II_KEY_TO_OFF_TAG: Map<String, String> = OFF_ALLERGEN_TAG_MAP.entries.associate { (tag, key) -> key to tag }

/**
 * Detect EU-mandatory allergens from product ingredient names, augmented with
 * OFF's own curated allergens_tags when the product came from that source.
 * Returns one entry per allergen found, with the triggering ingredient names.
 * Also appends trace-only hits (isTraceOnly=true) from OFF's traces_tags for
 * any allergen not already confirmed present some other way.
 *
 * Port of detectAllergens() from allergens.js.
 */
fun detectAllergens(product: Product, lang: String = "fr"): List<AllergenHit> {
    // key → triggers set
    val hits = mutableMapOf<String, MutableSet<String>>()
    val ruleMap = RULES.associateBy { it.key }

    for (ingredient in product.ingredients) {
        val name = ingredient.name
        // Match against a lowercased copy, not [name] itself - RegexOption.IGNORE_CASE
        // maps to Pattern.CASE_INSENSITIVE alone (no UNICODE_CASE), which only
        // case-folds ASCII a-z/A-Z, not accented letters. An all-caps ingredient
        // like OFF/OCR commonly produces (e.g. "FARINE DE BLÉ") never matched the
        // lowercase-only [eé]-style character classes below, silently missing a
        // mandatory EU allergen. [name] itself (original casing) is still what
        // gets stored as the trigger, so the UI display is unaffected.
        val matchable = name.lowercase()
        for (rule in RULES) {
            if (!rule.re.containsMatchIn(matchable)) continue
            // EU Annex II lists oats among gluten cereals, but "avoine" alone
            // (unlike blé/froment/seigle/orge, which are gluten-containing with
            // no gluten-free variant) is commonly sold as certified gluten-free
            // oats - flagging "avoine sans gluten"/"gluten-free oats" as a
            // gluten hit is a false positive the manufacturer's own label
            // already contradicts, and crying wolf on a coeliac-relevant
            // allergen erodes trust in the whole allergen panel.
            if (rule.key == "gluten" && GLUTEN_FREE_OATS_QUALIFIER.containsMatchIn(matchable) &&
                !GLUTEN_CEREAL_EXCL_OATS.containsMatchIn(matchable)
            ) continue
            hits.getOrPut(rule.key) { mutableSetOf() }.add(name)
        }
    }

    val offLabel = if (lang == "en") "Declared by Open Food Facts" else "Déclaré par Open Food Facts"
    for (tag in product.declaredAllergenTags) {
        val key = OFF_ALLERGEN_TAG_MAP[tag] ?: continue
        hits.getOrPut(key) { mutableSetOf() }.add(offLabel)
    }

    // OFF's traces_tags - manufacturer precautionary "may contain traces of
    // X" cross-contamination labeling, a distinct field from allergens_tags
    // above. Previously never read at all: a product whose label says "peut
    // contenir des traces de fruits à coque" but names no allergen in the
    // ingredient text (and OFF's own allergens_tags is empty, which happens
    // even for well-populated records) produced zero warning - a real
    // cross-contamination exposure route silently missed for a user with a
    // declared allergy. Kept in a SEPARATE set so a trace-only signal never
    // gets silently merged into (and diluted by) a confirmed-presence hit.
    val traceOnlyKeys = mutableSetOf<String>()
    val traceLabel = if (lang == "en") "May contain traces (Open Food Facts)" else "Peut contenir des traces (Open Food Facts)"
    for (tag in product.declaredTracesTags) {
        val key = OFF_ALLERGEN_TAG_MAP[tag] ?: continue
        if (key !in hits) traceOnlyKeys += key
    }

    // Return in ANNEX_II order so the UI always shows allergens in EU-canonical order
    val confirmed = ANNEX_II_KEYS.mapNotNull { key ->
        val triggers = hits[key] ?: return@mapNotNull null
        val rule = ruleMap[key] ?: return@mapNotNull null
        AllergenHit(
            key      = key,
            labelFr  = rule.labelFr,
            labelEn  = rule.labelEn,
            triggers = triggers.toList(),
        )
    }
    val traceOnly = ANNEX_II_KEYS.mapNotNull { key ->
        if (key !in traceOnlyKeys) return@mapNotNull null
        val rule = ruleMap[key] ?: return@mapNotNull null
        AllergenHit(
            key         = key,
            labelFr     = rule.labelFr,
            labelEn     = rule.labelEn,
            triggers    = listOf(traceLabel),
            isTraceOnly = true,
        )
    }
    return confirmed + traceOnly
}

/**
 * Check if any of the user's declared allergens are present in the product.
 * Returns the list of matching AllergenHits.
 */
fun checkUserAllergens(product: Product, userAllergens: Set<String>, lang: String = "fr"): List<AllergenHit> =
    detectAllergens(product, lang).filter { it.key in userAllergens }
