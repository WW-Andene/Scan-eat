package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// FOOD SCORING ENGINE v2.2.0 — Kotlin port of scoring-engine.ts
//
// Authoritative vs Editorial boundary preserved from original.
// See scoring-engine.ts header for full provenance notes.
//
// Main entry: scoreProduct(product: Product): ScoreAudit
// Pure function — no I/O, no side effects.
// ============================================================================

const val ENGINE_VERSION = "2.2.0"

// ============================================================================
// SECTION 3: CATEGORY THRESHOLDS
// ============================================================================

data class CategoryThresholds(
    val proteinG: Triple<Double, Double, Double>,
    val fiberG: Triple<Double, Double, Double>,
    val expectedKcalRange: Pair<Double, Double>,
    val expectMicronutrients: Boolean,
    val satFatThresholds: Triple<Double, Double, Double> = Triple(5.0, 10.0, 15.0),
    val sugarThresholds: Quadruple<Double, Double, Double, Double> = Quadruple(5.0, 10.0, 15.0, 22.5),
)

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private val DEFAULT_THRESHOLDS = CategoryThresholds(
    proteinG = Triple(3.0, 6.0, 12.0),
    fiberG = Triple(1.5, 3.0, 6.0),
    expectedKcalRange = Pair(50.0, 400.0),
    expectMicronutrients = false,
)

val CATEGORY_THRESHOLDS: Map<ProductCategory, CategoryThresholds> = mapOf(
    ProductCategory.SANDWICH         to CategoryThresholds(Triple(5.0,6.0,12.0),  Triple(2.0,4.0,6.0),  Pair(180.0,320.0), true),
    ProductCategory.READY_MEAL       to CategoryThresholds(Triple(4.0,7.0,10.0),  Triple(2.0,4.0,6.0),  Pair(80.0,200.0),  true),
    ProductCategory.BREAD            to CategoryThresholds(Triple(6.0,9.0,12.0),  Triple(3.0,6.0,9.0),  Pair(220.0,300.0), false),
    ProductCategory.BREAKFAST_CEREAL to CategoryThresholds(Triple(6.0,10.0,14.0), Triple(5.0,8.0,12.0), Pair(320.0,420.0), true),
    ProductCategory.YOGURT           to CategoryThresholds(Triple(3.0,5.0,9.0),   Triple(0.0,1.0,2.0),  Pair(40.0,120.0),  true),
    ProductCategory.CHEESE           to CategoryThresholds(Triple(15.0,20.0,25.0),Triple(0.0,0.0,0.0),  Pair(200.0,450.0), true,  satFatThresholds = Triple(12.0,20.0,30.0)),
    ProductCategory.PROCESSED_MEAT   to CategoryThresholds(Triple(10.0,15.0,22.0),Triple(0.0,0.0,1.0),  Pair(100.0,400.0), false),
    ProductCategory.FRESH_MEAT       to CategoryThresholds(Triple(15.0,20.0,25.0),Triple(0.0,0.0,0.0),  Pair(100.0,300.0), true),
    ProductCategory.FISH             to CategoryThresholds(Triple(15.0,20.0,25.0),Triple(0.0,0.0,0.0),  Pair(80.0,250.0),  true),
    ProductCategory.SNACK_SWEET      to CategoryThresholds(Triple(4.0,7.0,10.0),  Triple(2.0,4.0,6.0),  Pair(350.0,550.0), false),
    ProductCategory.SNACK_SALTY      to CategoryThresholds(Triple(6.0,9.0,14.0),  Triple(3.0,5.0,8.0),  Pair(400.0,550.0), false),
    ProductCategory.BEVERAGE_SOFT    to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(0.0,50.0),    false),
    ProductCategory.BEVERAGE_JUICE   to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,1.0,2.0),  Pair(20.0,60.0),   true),
    ProductCategory.BEVERAGE_WATER   to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(0.0,5.0),     false),
    ProductCategory.CONDIMENT        to CategoryThresholds(Triple(0.0,3.0,7.0),   Triple(0.0,1.0,3.0),  Pair(20.0,400.0),  false,
        sugarThresholds = Quadruple(10.0,20.0,30.0,45.0)),
    ProductCategory.OIL_FAT          to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(700.0,900.0), false,
        satFatThresholds = Triple(20.0,35.0,50.0)),
    ProductCategory.OTHER            to DEFAULT_THRESHOLDS,
)

fun getThresholds(cat: ProductCategory): CategoryThresholds =
    CATEGORY_THRESHOLDS[cat] ?: DEFAULT_THRESHOLDS

// ============================================================================
// SECTION 3b: NAME-BASED CATEGORY INFERENCE
// ============================================================================

private val NAME_CATEGORY_PATTERNS: List<Pair<Regex, ProductCategory>> = listOf(
    Regex("""eau\b|water\b|spring water|eau de source|eau min[eé]rale|eau gaz[eé]use""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_WATER,
    Regex("""\bjus\b|\bjuice\b|\bnectar\b|smoothie|fruit drink""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_JUICE,
    Regex("""\bsoda\b|\bcola\b|boisson gaz[eé]use|soft drink|\btonic\b|limonade|ice[-\s]?tea|th[eé] glac[eé]|energy drink|red bull|monster""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_SOFT,
    Regex("""\byaourts?\b|yoghurt|yogurt|\bskyr\b|fromage[-\s]?blanc|faisselle|\bquark\b|petit[-\s]suisse""", RegexOption.IGNORE_CASE) to ProductCategory.YOGURT,
    Regex("""\bfromages?\b|\bcheese\b|\bbrie\b|camembert|cheddar|gruy[eè]re|\bgouda\b|mozzarella|parmesan|\bfeta\b|roquefort|emmental|comt[eé]|reblochon|munster|\bch[eè]vre\b|ricotta|mascarpone|halloumi""", RegexOption.IGNORE_CASE) to ProductCategory.CHEESE,
    Regex("""\bsandwich\b|\bburger\b|\bwrap\b|panini|\bkebab\b|\bcroque\b""", RegexOption.IGNORE_CASE) to ProductCategory.SANDWICH,
    Regex("""chocolats?\b|\bchocolate\b|\bbonbon|\bcandy\b|biscuits?\b|cookies?\b|g[aâ]teaux?\b|\bcakes?\b|\btartes?\b|\btarts?\b|brownie|\bdonut\b|beignet|barre chocolat[eé]e|kinder|nutella|m&m|haribo|m[aâ]rs|snickers|twix|bounty|gauffres?\b|cr[eê]pes?\b|p[aâ]te [aà] tartiner""", RegexOption.IGNORE_CASE) to ProductCategory.SNACK_SWEET,
    Regex("""\bsaumon\b|\bthon\b|sardine|maquereau|\bhareng\b|cabillaud|\bmerlu\b|\bcolin\b|\btruite\b|crevette|\bcrabe\b|\bmoules\b|hu[iî]tres|\bbar\b|\bdorade\b""", RegexOption.IGNORE_CASE) to ProductCategory.FISH,
    Regex("""\bjambon\b|saucisson|chorizo|\bbacon\b|\blardon|\bsalami\b|pancetta|prosciutto|merguez|\brillettes\b|\bp[aâ]t[eé]\b""", RegexOption.IGNORE_CASE) to ProductCategory.PROCESSED_MEAT,
    Regex("""\bpoulet\b|\bb[oœ]uf\b|\bporc\b|\bagneau\b|\bdinde\b|\bcanard\b|viande hach[eé]e|\bsteaks?\b|escalope|magret""", RegexOption.IGNORE_CASE) to ProductCategory.FRESH_MEAT,
    Regex("""\bpain\b|\bbread\b|baguette|brioche|focaccia|ciabatta|\btoasts?\b|\bpita\b|tortilla|\bcracotte""", RegexOption.IGNORE_CASE) to ProductCategory.BREAD,
    Regex("""c[eé]r[eé]ales?\b|\bcereal\b|\bmuesli\b|\bgranola\b|porridge|flocons d['']avoine|\boats\b|cornflakes|chocapic|special k|fitness""", RegexOption.IGNORE_CASE) to ProductCategory.BREAKFAST_CEREAL,
    Regex("""plat pr[eé]par[eé]|plat cuisin[eé]|ready meal|micro[-\s]?ondes|[aà] r[eé]chauffer|lasagne|gratin|paella|risotto|\bcurry\b|chili con carne|hachis parmentier|tartiflette|moussaka""", RegexOption.IGNORE_CASE) to ProductCategory.READY_MEAL,
    Regex("""\bsauces?\b|mayonnaise|\bketchup\b|moutarde|mustard|vinaigrette|\bpesto\b|tahin[ei]|harissa|sambal|sriracha|wasabi|chutney|aioli|\btapenade\b""", RegexOption.IGNORE_CASE) to ProductCategory.CONDIMENT,
    Regex("""huile d['']olive|huile de colza|huile de tournesol|huile v[eé]g[eé]tale|\bolive oil\b|sunflower oil|canola oil|margarine|\bbeurre\b|\bbutter\b|saindoux""", RegexOption.IGNORE_CASE) to ProductCategory.OIL_FAT,
    Regex("""\bchips\b|\bcrisps?\b|crackers?\b|biscuits? sal[eé]s?|\bpopcorn\b|\bpretzels?\b|cacahu[eè]tes?\b|noix de cajou|amande grill[eé]e|pistaches?\b|olives?\b""", RegexOption.IGNORE_CASE) to ProductCategory.SNACK_SALTY,
)

fun inferCategoryFromName(name: String): ProductCategory {
    if (name.isBlank()) return ProductCategory.OTHER
    for ((regex, category) in NAME_CATEGORY_PATTERNS) {
        if (regex.containsMatchIn(name)) return category
    }
    return ProductCategory.OTHER
}

// ============================================================================
// SECTION 4: SHARED KEYWORD CONSTANTS
// ============================================================================

private val WHOLE_FOOD_KEYWORDS = listOf(
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

private val GENERIC_OIL_TERMS = listOf(
    "huile végétale","huile vegetale","vegetable oil",
    "matière grasse végétale","matiere grasse vegetale",
    "graisse végétale","graisse vegetale",
)

private val HIDDEN_SUGAR_NAMES = listOf(
    "sirop de glucose","sirop de fructose","sirop de maïs","dextrose",
    "maltodextrine","saccharose","fructose","galactose","glucose",
    "caramel","jus de canne","concentré de jus","purée de fruits","sirop",
)

private val UPF_MARKER_PATTERNS = listOf(
    Regex("""\bar[oô]mes?\b""", RegexOption.IGNORE_CASE) to "flavorings (arômes)",
    Regex("""\bconcentr[eé] des? min[eé]raux|mineral concentrate""", RegexOption.IGNORE_CASE) to "mineral concentrate",
    Regex("""\bisolat de |\bprot[eé]ine isol[eé]e|protein isolate""", RegexOption.IGNORE_CASE) to "protein isolate",
    Regex("""\bhydrolysat|prot[eé]ines? hydrolys[eé]es?|hydrolyzed protein""", RegexOption.IGNORE_CASE) to "protein hydrolysate",
    Regex("""\bamidon modifi|modified starch|maltodextrin""", RegexOption.IGNORE_CASE) to "modified starch",
)

private val FIRST_INGREDIENT_PENALTY_PATTERNS = listOf(
    Regex("""^(sucre|sirop|dextrose|fructose|glucose|maltodextrin)""", RegexOption.IGNORE_CASE) to "sugar/syrup",
    Regex("""^(huile|graisse|matière grasse|margarine)""", RegexOption.IGNORE_CASE) to "oil/fat",
    Regex("""^(amidon modifié|amidon de maïs modifié)""", RegexOption.IGNORE_CASE) to "modified starch",
)

private val FRESH_PRODUCE_NAME = Regex(
    """^(banane|banana|pomme|apple|poire|pear|tomate|tomato|oignon|onion|avocat|avocado|carotte|carrot|concombre|cucumber|courgette|zucchini|kiwi|orange|citron|lemon|lime|fraise|strawberr|framboise|raspberr|myrtille|blueberr|cassis|blackcurrant|ananas|pineapple|raisin|grape|cerise|cherry|prune|plum|peche|pêche|peach|mangue|mango|papaye|papaya|poireau|leek|chou|cabbage|brocoli|broccoli|salade|lettuce|epinard|épinard|spinach|radis|radish|navet|turnip|betterave|beet|aubergine|eggplant|poivron|bell pepper|piment|chili pepper|champignon|mushroom|asperge|asparagus|artichaut|artichoke|haricot vert|green bean|haricot|bean|lentille|lentil|petit[-\s]pois|pea|patate douce|sweet potato|pomme de terre|potato|courge|squash|citrouille|pumpkin|ail|garlic|gingembre|ginger|fenouil|fennel|celeri|céleri|celery|persil|parsley|basilic|basil|menthe|mint|coriandre|cilantro|ciboulette|chive|roquette|arugula|mache|mâche|cresson|watercress|endive|chicory|pastèque|watermelon|melon|nectarine|abricot|apricot|figue|fig|datte|date|grenade|pomegranate|noix|nut|amande|almond|noisette|hazelnut)s?\b""",
    RegexOption.IGNORE_CASE
)

// ============================================================================
// SECTION 5: PILLAR 1 — PROCESSING LEVEL (max 20)
// ============================================================================

enum class NovaConfidence { HIGH, MEDIUM, LOW }
data class NovaInference(val nova: NovaClass, val confidence: NovaConfidence)

private fun detectUPFMarkers(ingredients: List<Ingredient>): List<String> =
    UPF_MARKER_PATTERNS.mapNotNull { (regex, label) ->
        if (ingredients.any { regex.containsMatchIn(it.name) }) label else null
    }

fun inferNovaClassWithConfidence(product: Product): NovaInference {
    val ings = product.ingredients
    if (ings.isEmpty()) {
        return if (FRESH_PRODUCE_NAME.containsMatchIn(product.name.trim()))
            NovaInference(NovaClass.UNPROCESSED, NovaConfidence.HIGH)
        else
            NovaInference(NovaClass.ULTRA_PROCESSED, NovaConfidence.LOW)
    }

    val additives = ings.filter {
        it.category == IngredientCategory.ADDITIVE || it.eNumber != null
    }
    val cosmetics = additives.mapNotNull { ing ->
        findAdditive(ing.eNumber, ing.name, ing.category)
    }.filter { it.category in COSMETIC_ADDITIVE_CATEGORIES }
    val upfMarkers = detectUPFMarkers(ings)

    if (ings.size == 1 && additives.isEmpty() && upfMarkers.isEmpty())
        return NovaInference(NovaClass.UNPROCESSED, NovaConfidence.HIGH)

    if (ings.size <= 3 && additives.isEmpty() && upfMarkers.isEmpty()) {
        val onlyCulinary = ings.all { Regex("""^(sucre|sel|huile|beurre|graisse|miel|vinaigre|eau)""", RegexOption.IGNORE_CASE).containsMatchIn(it.name.trim()) }
        if (onlyCulinary) return NovaInference(NovaClass.CULINARY, NovaConfidence.HIGH)
    }

    if (cosmetics.isEmpty() && upfMarkers.isEmpty() && additives.size <= 2 && ings.size <= 10)
        return NovaInference(NovaClass.PROCESSED, NovaConfidence.MEDIUM)

    val hasPositiveEvidence = cosmetics.isNotEmpty() || upfMarkers.isNotEmpty()
    return NovaInference(NovaClass.ULTRA_PROCESSED, if (hasPositiveEvidence) NovaConfidence.MEDIUM else NovaConfidence.LOW)
}

fun scoreProcessing(product: Product): PillarScore {
    val MAX = 20
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()

    val inferredResult = inferNovaClassWithConfidence(product)
    val effectiveNova = when {
        product.novaClass == NovaClass.ULTRA_PROCESSED && inferredResult.nova.value < 4 -> inferredResult.nova
        else -> product.novaClass
    }

    if (effectiveNova != product.novaClass) {
        deductions += Deduction("processing", "NOVA auto-adjusted ${product.novaClass.value}→${effectiveNova.value} based on ingredients", 0.0, Severity.INFO)
    }

    val novaWasInferred = effectiveNova != product.novaClass
    if (novaWasInferred && inferredResult.confidence != NovaConfidence.HIGH) {
        val note = if (inferredResult.confidence == NovaConfidence.LOW)
            "NOVA heuristic confidence: LOW — ingredient list missing or too short"
        else
            "NOVA heuristic confidence: MEDIUM — inferred from absence of known additives"
        deductions += Deduction("processing", note, 0.0, Severity.INFO)
    }

    val base = when (effectiveNova) {
        NovaClass.UNPROCESSED   -> 20.0
        NovaClass.CULINARY      -> 17.0
        NovaClass.PROCESSED     -> 13.0
        NovaClass.ULTRA_PROCESSED -> 6.0
    }

    deductions += Deduction("processing", "NOVA class ${effectiveNova.value} base score", base - MAX, when (effectiveNova) {
        NovaClass.ULTRA_PROCESSED -> Severity.MAJOR
        NovaClass.PROCESSED -> Severity.MODERATE
        else -> Severity.INFO
    })

    var score = base

    if (product.ingredients.size > 10) {
        score -= 2
        deductions += Deduction("processing", "${product.ingredients.size} ingredients (>10 threshold)", -2.0, Severity.MINOR)
    }

    val cosmeticAdditives = product.ingredients
        .mapNotNull { findAdditive(it.eNumber, it.name, it.category) }
        .filter { it.category in COSMETIC_ADDITIVE_CATEGORIES }

    val upfMarkers = detectUPFMarkers(product.ingredients)
    if (upfMarkers.isNotEmpty()) {
        val penalty = minOf(4.0, upfMarkers.size * 2.0)
        score -= penalty
        deductions += Deduction("processing", "${upfMarkers.size} UPF marker(s): ${upfMarkers.joinToString()}", -penalty, Severity.MINOR)
    }

    if (cosmeticAdditives.isNotEmpty()) {
        score -= 2
        deductions += Deduction("processing", "Contains cosmetic additives", -2.0, Severity.MINOR,
            cosmeticAdditives.joinToString { "${it.eNumber} (${it.category.key})" })
    }

    val first = product.ingredients.firstOrNull()
    if (first != null) {
        val match = FIRST_INGREDIENT_PENALTY_PATTERNS.find { (re, _) -> re.containsMatchIn(first.name.trim()) }
        if (match != null) {
            score -= 3
            deductions += Deduction("processing", "Primary ingredient is ${match.second}: \"${first.name}\"", -3.0, Severity.MODERATE)
        }
    }

    return PillarScore("Processing Level", MAX, maxOf(0.0, score), deductions, bonuses)
}

// ============================================================================
// SECTION 6: PILLAR 2 — NUTRITIONAL DENSITY (max 25)
// ============================================================================

private val NRV_TARGETS = mapOf(
    "protein"   to Pair("proteinG",  50.0),
    "fiber"     to Pair("fiberG",    25.0),
    "iron"      to Pair("ironMg",    14.0),
    "calcium"   to Pair("calciumMg", 800.0),
    "vitD"      to Pair("vitDUg",    5.0),
    "vitC"      to Pair("vitCMg",    80.0),
    "vitA"      to Pair("vitAUg",    800.0),
    "vitE"      to Pair("vitEMg",    12.0),
    "b12"       to Pair("b12Ug",     2.5),
    "magnesium" to Pair("magnesiumMg", 375.0),
    "potassium" to Pair("potassiumMg", 2000.0),
    "zinc"      to Pair("zincMg",    10.0),
)

private fun getNutrientValue(n: NutritionPer100g, key: String): Double? = when (key) {
    "proteinG"    -> n.proteinG
    "fiberG"      -> n.fiberG
    "ironMg"      -> n.ironMg
    "calciumMg"   -> n.calciumMg
    "vitDUg"      -> n.vitDUg
    "vitCMg"      -> n.vitCMg
    "vitAUg"      -> n.vitAUg
    "vitEMg"      -> n.vitEMg
    "b12Ug"       -> n.b12Ug
    "magnesiumMg" -> n.magnesiumMg
    "potassiumMg" -> n.potassiumMg
    "zincMg"      -> n.zincMg
    else          -> null
}

fun scoreNutritionalDensity(product: Product): PillarScore {
    val MAX = 25
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    val n = product.nutrition
    val thresholds = getThresholds(product.category)
    var score = 0.0

    // Protein (0–7)
    val (pLow, pMed, pHigh) = thresholds.proteinG
    val protScore = when {
        n.proteinG >= pHigh -> 7.0
        n.proteinG >= pMed  -> 5.0
        n.proteinG >= pLow  -> 3.0
        else                -> 0.0
    }
    score += protScore
    if (protScore < 7) deductions += Deduction("nutritional_density", "Protein ${n.proteinG}g/100g (${protScore.toInt()}/7)", protScore - 7, Severity.MINOR)
    else bonuses += Deduction("nutritional_density", "High protein ${n.proteinG}g/100g", 7.0, Severity.INFO)

    // Fiber (0–7)
    val (fLow, fMed, fHigh) = thresholds.fiberG
    val fiberScore = when {
        n.fiberG >= fHigh -> 7.0
        n.fiberG >= fMed  -> 5.0
        n.fiberG >= fLow  -> 3.0
        else              -> 0.0
    }
    score += fiberScore
    if (fiberScore >= 5) bonuses += Deduction("nutritional_density", "Good fiber ${n.fiberG}g/100g", fiberScore, Severity.INFO)
    else deductions += Deduction("nutritional_density", "Low fiber ${n.fiberG}g/100g (${fiberScore.toInt()}/7)", fiberScore - 7, Severity.MINOR)

    // Micronutrients NRV-15% bonus (0–8): +1 per micro declared at ≥15% NRV per 100g, cap 8
    var microBonus = 0.0
    val declaredMicros = mutableListOf<String>()
    for ((name, pair) in NRV_TARGETS) {
        val (fieldKey, nrv) = pair
        val value = getNutrientValue(n, fieldKey) ?: continue
        if (value >= nrv * 0.15) {
            microBonus += 1.0
            declaredMicros += name
        }
    }
    microBonus = minOf(8.0, microBonus)
    score += microBonus
    if (microBonus > 0) bonuses += Deduction("nutritional_density", "Micronutrient richness: ${declaredMicros.joinToString()}", microBonus, Severity.INFO)

    // Healthy-fat bonus (+3): omega-3 or declared omega-3 nutrition
    val hasOmega3 = (n.omega3G ?: 0.0) > 0.5 || product.ingredients.any { ing ->
        Regex("""(graine de )?lin\b|\bchia\b|\bnoix\b|saumon|sardine|maquereau|hareng|anchois""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (hasOmega3) {
        score += 3
        bonuses += Deduction("nutritional_density", "Omega-3 source present", 3.0, Severity.INFO)
    }

    return PillarScore("Nutritional Density", MAX, minOf(MAX.toDouble(), maxOf(0.0, score)), deductions, bonuses)
}

// ============================================================================
// SECTION 7: PILLAR 3 — NEGATIVE NUTRIENTS (max 25)
// ============================================================================

fun scoreNegativeNutrients(product: Product): PillarScore {
    val MAX = 25
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    val n = product.nutrition
    val thresholds = getThresholds(product.category)
    var score = MAX.toDouble()

    // Saturated fat
    val sat = n.saturatedFatG
    val (satMod, satMaj, satCrit) = thresholds.satFatThresholds
    when {
        sat > satCrit -> { score -= 9; deductions += Deduction("negative_nutrients", "Saturated fat ${sat}g/100g (>$satCrit critical)", -9.0, Severity.CRITICAL) }
        sat > satMaj  -> { score -= 6; deductions += Deduction("negative_nutrients", "Saturated fat ${sat}g/100g (>$satMaj major)", -6.0, Severity.MAJOR) }
        sat > satMod  -> { score -= 3; deductions += Deduction("negative_nutrients", "Saturated fat ${sat}g/100g (>$satMod moderate)", -3.0, Severity.MODERATE) }
    }

    // Sugars
    val sugars = n.addedSugarsG ?: n.sugarsG
    val sugarLabel = if (n.addedSugarsG != null) "Added sugars" else "Total sugars (added not declared)"
    val (sMinor, sMod, sMaj, sCrit) = thresholds.sugarThresholds
    when {
        sugars > sCrit -> { score -= 12; deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sCrit critical)", -12.0, Severity.CRITICAL) }
        sugars > sMaj  -> { score -= 9;  deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMaj major)", -9.0, Severity.MAJOR) }
        sugars > sMod  -> { score -= 6;  deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMod moderate)", -6.0, Severity.MODERATE) }
        sugars > sMinor -> { score -= 3; deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMinor minor)", -3.0, Severity.MINOR) }
    }

    // Salt
    val salt = n.saltG
    when {
        salt > 1.5  -> { score -= 6; deductions += Deduction("negative_nutrients", "Salt ${salt}g/100g (>1.5g critical)", -6.0, Severity.MAJOR) }
        salt > 1.25 -> { score -= 4; deductions += Deduction("negative_nutrients", "Salt ${salt}g/100g (>1.25g moderate)", -4.0, Severity.MODERATE) }
        salt > 0.75 -> { score -= 2; deductions += Deduction("negative_nutrients", "Salt ${salt}g/100g (>0.75g minor)", -2.0, Severity.MINOR) }
    }

    // Trans fat
    val trans = n.transFatG ?: 0.0
    if (trans > 0.1) {
        score -= 10
        deductions += Deduction("negative_nutrients", "Trans fat present: ${trans}g/100g (no safe level)", -10.0, Severity.CRITICAL)
    }

    // Calorie density anomaly
    val (kcalLow, kcalHigh) = thresholds.expectedKcalRange
    if (n.energyKcal > kcalHigh * 1.25 || n.energyKcal < kcalLow * 0.5) {
        score -= 2
        deductions += Deduction("negative_nutrients", "Energy ${n.energyKcal}kcal/100g outside category norm ($kcalLow–$kcalHigh)", -2.0, Severity.MINOR)
    }

    return PillarScore("Negative Nutrients", MAX, maxOf(0.0, score), deductions, bonuses)
}

// ============================================================================
// SECTION 8: PILLAR 4 — ADDITIVE RISK (max 15)
// ============================================================================

fun scoreAdditiveRisk(product: Product): PillarScore {
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()

    data class Hit(val ingredient: String, val additive: String, val concern: String)

    val tier1 = mutableListOf<Hit>()
    val tier2 = mutableListOf<Hit>()
    val tier3 = mutableListOf<Hit>()

    for (ing in product.ingredients) {
        val additive = findAdditive(ing.eNumber, ing.name, ing.category) ?: continue
        val hit = Hit(ing.name, additive.eNumber, additive.concern)
        when (additive.tier) {
            AdditiveTier.ONE   -> tier1 += hit
            AdditiveTier.TWO   -> tier2 += hit
            AdditiveTier.THREE -> tier3 += hit
        }
    }

    var score = MAX.toDouble()

    if (tier1.isNotEmpty()) {
        val penalty = minOf(10.0, tier1.size * 5.0)
        score -= penalty
        deductions += Deduction("additive_risk", "${tier1.size} Tier-1 additive(s) (serious concern)", -penalty, Severity.CRITICAL,
            tier1.joinToString(" | ") { "${it.additive} (${it.ingredient}): ${it.concern}" })
    }
    if (tier2.isNotEmpty()) {
        val penalty = minOf(6.0, tier2.size * 2.0)
        score -= penalty
        deductions += Deduction("additive_risk", "${tier2.size} Tier-2 additive(s) (moderate concern)", -penalty, Severity.MODERATE,
            tier2.joinToString(" | ") { "${it.additive} (${it.ingredient}): ${it.concern}" })
    }
    if (tier3.isNotEmpty()) {
        val penalty = minOf(3.0, tier3.size * 1.0)
        score -= penalty
        deductions += Deduction("additive_risk", "${tier3.size} Tier-3 additive(s) (minor concern)", -penalty, Severity.MINOR,
            tier3.joinToString(" | ") { "${it.additive} (${it.ingredient})" })
    }

    return PillarScore("Additive Risk", MAX, maxOf(0.0, score), deductions, bonuses)
}

fun countTier1Additives(product: Product): Int =
    product.ingredients.mapNotNull { findAdditive(it.eNumber, it.name, it.category) }.count { it.tier == AdditiveTier.ONE }

// ============================================================================
// SECTION 9: PILLAR 5 — INGREDIENT INTEGRITY (max 15)
// ============================================================================

private fun isWholeFood(ingredient: Ingredient): Boolean {
    if (ingredient.isWholeFood == true) return true
    val lower = ingredient.name.lowercase()
    if (Regex("""sirop|huile|farine raffinée|amidon modifié|isolat|concentré""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) return false
    return WHOLE_FOOD_KEYWORDS.any { lower.contains(it) }
}

fun scoreIngredientIntegrity(product: Product): PillarScore {
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    var score = 0.0

    // 1. First 3 whole foods (+5)
    val first3 = product.ingredients.take(3)
    val first3Whole = first3.count { isWholeFood(it) }
    val first3Score = ((first3Whole.toDouble() / 3.0) * 5.0).let { kotlin.math.round(it).toDouble() }
    score += first3Score
    if (first3Score < 5) deductions += Deduction("ingredient_integrity", "Only $first3Whole/3 first ingredients are whole foods (${first3Score.toInt()}/5)", first3Score - 5, Severity.MODERATE)
    else bonuses += Deduction("ingredient_integrity", "First 3 ingredients are all whole foods", 5.0, Severity.INFO)

    // 2. Overall recognizability (+3)
    val nonAdditive = product.ingredients.filter { findAdditive(it.eNumber, it.name, it.category) == null }
    val recognizable = nonAdditive.count { ing ->
        val n = ing.name.lowercase()
        n.length < 40 && !Regex("""isolat|hydrolysat|concentré|modifié|extrait sec""", RegexOption.IGNORE_CASE).containsMatchIn(n)
    }
    val recogRatio = if (nonAdditive.isNotEmpty()) recognizable.toDouble() / nonAdditive.size else 1.0
    val recogScore = when {
        recogRatio >= 0.8 -> 3.0
        recogRatio >= 0.6 -> 2.0
        recogRatio >= 0.4 -> 1.0
        else              -> 0.0
    }
    score += recogScore
    if (recogScore < 3) deductions += Deduction("ingredient_integrity", "${(recogRatio * 100).toInt()}% recognizable ingredients (${recogScore.toInt()}/3)", recogScore - 3, Severity.MINOR)

    // 3. Origin transparency (+2)
    if (product.originTransparent || product.origin != null) {
        score += 2
        bonuses += Deduction("ingredient_integrity", "Origin declared: ${product.origin ?: "transparent"}", 2.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", "No origin information", -2.0, Severity.MINOR)
    }

    // 4. Hidden sugars (+2)
    val sugarAliases = mutableSetOf<String>()
    for (ing in product.ingredients) {
        val n = ing.name.lowercase()
        for (alias in HIDDEN_SUGAR_NAMES) { if (n.contains(alias)) sugarAliases += alias }
        if (Regex("""^sucre""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name.trim())) sugarAliases += "sucre"
    }
    if (sugarAliases.size >= 2) {
        deductions += Deduction("ingredient_integrity", "${sugarAliases.size} distinct sugar sources: ${sugarAliases.joinToString()}", -2.0, Severity.MODERATE)
    } else {
        score += 2
        bonuses += Deduction("ingredient_integrity", if (sugarAliases.size == 1) "Single transparent sugar source" else "No hidden sugars", 2.0, Severity.INFO)
    }

    // 5. Named oils (+3)
    val hasGenericOil = product.ingredients.any { ing ->
        val n = ing.name.lowercase()
        GENERIC_OIL_TERMS.any { n.contains(it) }
    }
    if (product.namedOils != false && !hasGenericOil) {
        score += 3
        bonuses += Deduction("ingredient_integrity", "Oils specifically named", 3.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", "Generic \"vegetable oil\" instead of specific named oil", -3.0, Severity.MINOR)
    }

    return PillarScore("Ingredient Integrity", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}

// ============================================================================
// SECTION 10: GLOBAL MODIFIERS, VETOES & ORCHESTRATOR
// ============================================================================

private fun scoreToGrade(score: Int): Grade = when {
    score >= 85 -> Grade.A_PLUS
    score >= 70 -> Grade.A
    score >= 55 -> Grade.B
    score >= 40 -> Grade.C
    score >= 25 -> Grade.D
    else        -> Grade.F
}

private fun gradeVerdict(grade: Grade): String = when (grade) {
    Grade.A_PLUS -> "Excellent — daily staple potential"
    Grade.A      -> "Good — regular consumption fine"
    Grade.B      -> "Acceptable — moderate frequency"
    Grade.C      -> "Mediocre — occasional only"
    Grade.D      -> "Poor — avoid regular use"
    Grade.F      -> "Very poor — avoid"
}

private fun computeGlobalBonuses(product: Product): List<Deduction> {
    val bonuses = mutableListOf<Deduction>()
    if (product.organic) bonuses += Deduction("global_bonus", "Organic certification", 2.0, Severity.INFO)
    if (product.wholeGrainPrimary) bonuses += Deduction("global_bonus", "Whole grain as primary grain", 3.0, Severity.INFO)
    if (product.fermented) bonuses += Deduction("global_bonus", "Contains fermented / probiotic content", 2.0, Severity.INFO)
    val omega3Ing = product.ingredients.find { ing ->
        Regex("""(graine de )?lin\b|\bchia\b|\bnoix\b|saumon|sardine|maquereau|hareng|anchois""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (omega3Ing != null) bonuses += Deduction("global_bonus", "Omega-3 source: ${omega3Ing.name}", 2.0, Severity.INFO)
    return bonuses
}

private fun computeGlobalPenalties(product: Product): List<Deduction> {
    val penalties = mutableListOf<Deduction>()
    if (product.hasMisleadingMarketing) penalties += Deduction("global_penalty", "Misleading marketing claims", -2.0, Severity.MODERATE)
    if (product.hasHealthClaims) penalties += Deduction("global_penalty", "Health claims present — verify vs composition", -3.0, Severity.MODERATE)
    val palm = product.ingredients.find { ing ->
        Regex("""huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (palm != null) penalties += Deduction("global_penalty", "Palm oil or derivative: ${palm.name}", -3.0, Severity.MODERATE)
    return penalties
}

private fun checkVeto(product: Product): VetoCondition {
    val n = product.nutrition

    if ((n.transFatG ?: 0.0) > 0.1)
        return VetoCondition(true, "Contains industrial trans fats — no safe level", 40)

    if (countTier1Additives(product) > 3)
        return VetoCondition(true, "${countTier1Additives(product)} Tier-1 additives — cumulative risk too high", 40)

    val hasNitrites = product.ingredients.any { ing ->
        val eNum = (ing.eNumber ?: "").uppercase().replace("\\s".toRegex(), "")
        eNum == "E249" || eNum == "E250" || ing.name.lowercase().let { it.contains("nitrite") || it.contains("e249") || it.contains("e250") }
    }
    val highSalt = n.saltG > 1.5
    val refined = product.ingredients.any { Regex("""farine de blé|farine raffinée|amidon|dextrose""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasNitrites && highSalt && refined && product.category == ProductCategory.PROCESSED_MEAT)
        return VetoCondition(true, "Processed meat with nitrites + high salt + refined starch", 40)

    val sugars = n.addedSugarsG ?: n.sugarsG
    if (product.category != ProductCategory.SNACK_SWEET && sugars > 30)
        return VetoCondition(true, "Added sugar >30g/100g in non-confectionery", 40)

    if (product.category == ProductCategory.BEVERAGE_SOFT && sugars > 5 && n.proteinG < 1 && n.fiberG < 1)
        return VetoCondition(true, "Sugar-sweetened beverage with no nutritional contribution", 30)

    val hasMSM = product.ingredients.any { Regex("""séparée mécaniquement|mechanically separated|msm""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasMSM && product.novaClass == NovaClass.ULTRA_PROCESSED)
        return VetoCondition(true, "Mechanically separated meat in NOVA 4 product", 45)

    return VetoCondition(false, "", 100)
}

private fun buildFlags(audit: ScoreAudit): Pair<List<String>, List<String>> {
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

    val eco = audit.eco
    if (eco?.grade != null) {
        when (eco.grade.lowercase()) {
            "a", "b" -> green += "Eco-score ${eco.grade.uppercase()} — low environmental impact"
            "d", "e" -> red += "Eco-score ${eco.grade.uppercase()} — high environmental impact"
        }
    }

    if (audit.veto.triggered) red.add(0, "VETO: ${audit.veto.reason}")
    return Pair(red, green)
}

private fun collectWarnings(product: Product): List<String> {
    val warnings = mutableListOf<String>()
    if (product.nutrition.transFatG == null) warnings += "trans_fat_g not declared — assumed 0"
    if (product.nutrition.addedSugarsG == null) warnings += "added_sugars_g not declared — using total sugars as proxy"
    return warnings
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================

/**
 * Score a product. Pure synchronous function.
 * Mirrors scoreProduct() from scoring-engine.ts exactly.
 */
fun scoreProduct(input: Product): ScoreAudit {
    // Final fallback: if both OFF and LLM landed on 'other', infer from name
    val product = if (input.category == ProductCategory.OTHER) {
        val inferred = inferCategoryFromName(input.name)
        if (inferred != ProductCategory.OTHER) input.copy(category = inferred) else input
    } else input

    val processing          = scoreProcessing(product)
    val nutritionalDensity  = scoreNutritionalDensity(product)
    val negativeNutrients   = scoreNegativeNutrients(product)
    val additiveRisk        = scoreAdditiveRisk(product)
    val ingredientIntegrity = scoreIngredientIntegrity(product)

    val baseScore = processing.score + nutritionalDensity.score + negativeNutrients.score +
                    additiveRisk.score + ingredientIntegrity.score

    val globalBonuses   = computeGlobalBonuses(product)
    val globalPenalties = computeGlobalPenalties(product)
    val bonusTotal      = minOf(10.0, globalBonuses.sumOf { it.points })
    val penaltyTotal    = globalPenalties.sumOf { it.points }

    var score = (baseScore + bonusTotal + penaltyTotal).coerceIn(0.0, 100.0)
    val veto = checkVeto(product)
    if (veto.triggered && score > veto.cap) score = veto.cap.toDouble()
    val finalScore = score.toInt()
    val grade = scoreToGrade(finalScore)

    val pillars = ScoreAudit.Pillars(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)

    val warnings = collectWarnings(product) +
        if (product.category != input.category) listOf("Category inferred from name as \"${product.category.key}\"") else emptyList()

    val preAudit = ScoreAudit(
        productName     = product.name,
        category        = product.category,
        score           = finalScore,
        grade           = grade,
        verdict         = gradeVerdict(grade),
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

    val (red, green) = buildFlags(preAudit)
    return preAudit.copy(redFlags = red, greenFlags = green)
}
