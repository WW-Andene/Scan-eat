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
    "dinde","canard","agneau","jambon","fromage","lait","yaourt","skyr","eau","miel",
    "légume","legume",
)

internal val GENERIC_OIL_TERMS = listOf(
    "huile végétale","huile vegetale","vegetable oil",
    "matière grasse végétale","matiere grasse vegetale",
    "graisse végétale","graisse vegetale",
)

internal val HIDDEN_SUGAR_NAMES = listOf(
    "sirop de glucose","sirop de fructose","sirop de maïs","dextrose",
    "maltodextrine","saccharose","fructose","galactose","glucose",
    "caramel","jus de canne","concentré de jus","purée de fruits","sirop",
)

internal val UPF_MARKER_PATTERNS = listOf(
    Regex("""\bar[oô]mes?\b""", RegexOption.IGNORE_CASE) to "flavorings (arômes)",
    Regex("""\bconcentr[eé] des? min[eé]raux|mineral concentrate""", RegexOption.IGNORE_CASE) to "mineral concentrate",
    Regex("""\bisolat de |\bprot[eé]ine isol[eé]e|protein isolate""", RegexOption.IGNORE_CASE) to "protein isolate",
    Regex("""\bhydrolysat|prot[eé]ines? hydrolys[eé]es?|hydrolyzed protein""", RegexOption.IGNORE_CASE) to "protein hydrolysate",
    Regex("""\bamidon modifi|modified starch|maltodextrin""", RegexOption.IGNORE_CASE) to "modified starch",
)

internal val FIRST_INGREDIENT_PENALTY_PATTERNS = listOf(
    Regex("""^(sucre|sirop|dextrose|fructose|glucose|maltodextrin)""", RegexOption.IGNORE_CASE) to "sugar/syrup",
    Regex("""^(huile|graisse|matière grasse|margarine)""", RegexOption.IGNORE_CASE) to "oil/fat",
    Regex("""^(amidon modifié|amidon de maïs modifié)""", RegexOption.IGNORE_CASE) to "modified starch",
)

internal val FRESH_PRODUCE_NAME = Regex(
    """^(banane|banana|pomme|apple|poire|pear|tomate|tomato|oignon|onion|avocat|avocado|carotte|carrot|concombre|cucumber|courgette|zucchini|kiwi|orange|citron|lemon|lime|fraise|strawberr|framboise|raspberr|myrtille|blueberr|cassis|blackcurrant|ananas|pineapple|raisin|grape|cerise|cherry|prune|plum|peche|pêche|peach|mangue|mango|papaye|papaya|poireau|leek|chou|cabbage|brocoli|broccoli|salade|lettuce|epinard|épinard|spinach|radis|radish|navet|turnip|betterave|beet|aubergine|eggplant|poivron|bell pepper|piment|chili pepper|champignon|mushroom|asperge|asparagus|artichaut|artichoke|haricot vert|green bean|haricot|bean|lentille|lentil|petit[-\s]pois|pea|patate douce|sweet potato|pomme de terre|potato|courge|squash|citrouille|pumpkin|ail|garlic|gingembre|ginger|fenouil|fennel|celeri|céleri|celery|persil|parsley|basilic|basil|menthe|mint|coriandre|cilantro|ciboulette|chive|roquette|arugula|mache|mâche|cresson|watercress|endive|chicory|pastèque|watermelon|melon|nectarine|abricot|apricot|figue|fig|datte|date|grenade|pomegranate|noix|nut|amande|almond|noisette|hazelnut)s?\b""",
    RegexOption.IGNORE_CASE
)
