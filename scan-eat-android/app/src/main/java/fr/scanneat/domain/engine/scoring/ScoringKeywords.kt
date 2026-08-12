package fr.scanneat.domain.engine.scoring

// ============================================================================
// SECTION 4: SHARED KEYWORD CONSTANTS
// ============================================================================

internal val WHOLE_FOOD_KEYWORDS = listOf(
    "tomate","salade","carotte","épinard","epinard","poivron","oignon","ail",
    "courgette","aubergine","concombre","brocoli","chou","betterave","poireau",
    "potiron","courge","fruit","pomme","poire","orange","citron","pamplemousse",
    "abricot","pêche","peche","fraise","framboise","myrtille","cassis","cerise",
    "prune","raisin","figue","datte","mangue","ananas","banane","kiwi","melon",
    "pastèque","grenade","coco","lentille","haricot","pois","fève","feve",
    "noix","amande","noisette","pistache","cajou","graine","sésame","sesame",
    "lin","chia","tournesol","riz","quinoa","avoine","blé","ble","seigle","orge",
    "sarrasin","farine complète","farine complete","oeuf","œuf","poisson",
    "saumon","thon","sardine","maquereau","poulet","boeuf","porc","viande",
    // "jambon" (ham) deliberately excluded, unlike "porc"/"viande" above -
    // porc/viande can name a raw whole cut, but ham is cured/processed by
    // definition (salt, nitrites, sometimes phosphates), never a raw whole
    // food. Including it let a cured deli product (e.g. "Jambon-beurre"
    // listing "Jambon" as ingredient #1) earn the "first 3 ingredients are
    // whole foods" bonus and a factually wrong "aliments bruts" badge for
    // the same product IngredientIntegrityPillar/AdditiveRiskPillar
    // elsewhere score as processed for its nitrite content.
    "dinde","canard","agneau","fromage","lait","yaourt","skyr","eau","miel",
    "légume","legume",
    // Added — already recognized by FRESH_PRODUCE_NAME below (or, for the
    // seafood/legume/soy entries, matching the same whole-food tier as the
    // saumon/thon/sardine/haricot/lentille entries already above) but missing
    // here, so an ingredient list naming them as a primary ingredient (e.g.
    // "crevette" in a shrimp product) never earned the first-3-ingredients
    // whole-food bonus despite genuinely being one.
    "mûre","mure","clémentine","clementine","asperge","champignon","radis",
    "céleri","celeri","artichaut","patate","maïs","mais","crevette","moules",
    "cabillaud","tofu","edamame","cacahuète","cacahuete",
    // Confirmed via OpenFoodFacts (barcode 3254380008430, Cristaline Eau De
    // Source Pétillante): carbonation gas is the product's literal 2nd
    // ingredient, and "eau" was already whitelisted above but this wasn't -
    // every sparkling water lost the "first 3 ingredients are whole foods"
    // bonus for naming the natural gas that makes it sparkling, treated the
    // same as an unrecognized industrial additive despite being about as
    // minimally processed as an ingredient can be.
    "dioxyde de carbone","gaz carbonique","carbon dioxide",
)

internal val GENERIC_OIL_TERMS = listOf(
    "huile végétale","huile vegetale","vegetable oil",
    "matière grasse végétale","matiere grasse vegetale",
    "graisse végétale","graisse vegetale",
)

internal val NAMED_OIL_TERMS = listOf(
    "huile de tournesol","huile d'olive","huile de colza","huile de canola",
    "huile de palme","huile de coco","huile de noix de coco","huile de noix","huile d'arachide",
    "huile de sésame","huile de sesame","huile de pépins de raisin","huile de pepins de raisin",
    "huile de lin","huile de germe de blé","huile de germe de ble",
    "sunflower oil","olive oil","rapeseed oil","canola oil","palm oil","coconut oil",
    "walnut oil","peanut oil","sesame oil","grapeseed oil","flaxseed oil","wheat germ oil",
)

internal val HIDDEN_SUGAR_NAMES = listOf(
    "sirop de glucose","sirop de fructose","sirop de maïs","dextrose",
    "maltodextrine","saccharose","fructose","galactose","glucose",
    "caramel","jus de canne","concentré de jus","purée de fruits","sirop",
    // Added — same "sugar wearing a different name" euphemism class already
    // targeted above (e.g. "jus de canne" for cane-juice-as-sugar), but these
    // were conspicuously absent. Bare "sirop"/"sucre" above already catches
    // their French forms via substring/prefix match, so these mainly close
    // the gap for English-labeled products with no "sirop"/"sucre" prefix.
    "agave","sirop d'agave","sirop d'érable","maple syrup","sirop de riz",
    "rice syrup","sirop de malt","malt syrup","sucre inverti","invert sugar",
)

internal val UPF_MARKER_PATTERNS = listOf(
    Regex("""\bar[oô]mes?\b""", RegexOption.IGNORE_CASE) to "flavorings (arômes)",
    Regex("""\bconcentr[eé] des? min[eé]raux|mineral concentrate""", RegexOption.IGNORE_CASE) to "mineral concentrate",
    Regex("""\bisolat de |\bprot[eé]ine isol[eé]e|protein isolate""", RegexOption.IGNORE_CASE) to "protein isolate",
    Regex("""\bhydrolysat|prot[eé]ines? hydrolys[eé]es?|hydrolyzed protein""", RegexOption.IGNORE_CASE) to "protein hydrolysate",
    Regex("""\bamidon modifi|modified starch|maltodextrin""", RegexOption.IGNORE_CASE) to "modified starch",
)

// Accented and unaccented spellings both matched directly - unlike every
// other ingredient-text pattern in this engine (POLYOL_PATTERN, additive
// synonym lookup, etc.), this list was matched against raw first.name with
// no normalizeForMatching() pass (ProcessingPillar.kt's containsMatchIn
// call), so "matiere grasse vegetale"/"amidon modifie" (both real forms in
// this app's own OFF/OCR-derived data, same as GENERIC_OIL_TERMS' own
// accented+unaccented pairs above) silently skipped this deduction entirely.
// UPF_MARKER_PATTERNS already handles this exact class of bug one list up
// by truncating before the accented character; character classes here do
// the same without truncating past a real word boundary.
internal val FIRST_INGREDIENT_PENALTY_PATTERNS = listOf(
    Regex("""^(sucre|sirop|dextrose|fructose|glucose|maltodextrin)""", RegexOption.IGNORE_CASE) to "sugar/syrup",
    Regex("""^(huile|graisse|mati[eè]re grasse|margarine)""", RegexOption.IGNORE_CASE) to "oil/fat",
    Regex("""^(amidon modifi[eé]|amidon de ma[iï]s modifi[eé])""", RegexOption.IGNORE_CASE) to "modified starch",
)

internal val FRESH_PRODUCE_NAME = Regex(
    """^(banane|banana|pomme|apple|poire|pear|tomate|tomato|oignon|onion|avocat|avocado|carotte|carrot|concombre|cucumber|courgette|zucchini|kiwi|orange|citron|lemon|lime|fraise|strawberr|framboise|raspberr|myrtille|blueberr|cassis|blackcurrant|ananas|pineapple|raisin|grape|cerise|cherry|prune|plum|peche|pêche|peach|mangue|mango|papaye|papaya|poireau|leek|chou|cabbage|brocoli|broccoli|salade|lettuce|epinard|épinard|spinach|radis|radish|navet|turnip|betterave|beet|aubergine|eggplant|poivron|bell pepper|piment|chili pepper|champignon|mushroom|asperge|asparagus|artichaut|artichoke|ma[iï]s|corn|haricot vert|green bean|haricot|bean|lentille|lentil|petit[-\s]pois|pea|patate douce|sweet potato|pomme de terre|potato|courge|squash|citrouille|pumpkin|ail|garlic|gingembre|ginger|fenouil|fennel|celeri|céleri|celery|persil|parsley|basilic|basil|menthe|mint|coriandre|cilantro|ciboulette|chive|roquette|arugula|mache|mâche|cresson|watercress|endive|chicory|pastèque|watermelon|melon|nectarine|abricot|apricot|figue|fig|datte|date|grenade|pomegranate|noix|nut|amande|almond|noisette|hazelnut)s?\b""",
    RegexOption.IGNORE_CASE
)
