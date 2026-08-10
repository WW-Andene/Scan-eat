package fr.scanneat.shared

// ============================================================================
// SECTION 3: CATEGORY THRESHOLDS
//
// Split out of ScoringEngine.kt (847 lines - a god-file candidate mirroring
// the Android client's PersonalScoreEngine.kt before it was atomized into
// CategoryThresholds/ProcessingPillar/NutritionalDensityPillar/
// NegativeNutrientsPillar/AdditiveRiskPillar/IngredientIntegrityPillar/
// ScoringEngine files). Purely structural move - no behavior change. Same
// package (fr.scanneat.shared) throughout, so no import changes needed
// anywhere that calls these functions.
// ============================================================================

data class CategoryThresholds(
    val proteinG: Triple<Double, Double, Double>,
    val fiberG: Triple<Double, Double, Double>,
    val expectedKcalRange: Pair<Double, Double>,
    val expectMicronutrients: Boolean,
    val satFatThresholds: Triple<Double, Double, Double> = Triple(5.0, 10.0, 15.0),
    val sugarThresholds: Quadruple<Double, Double, Double, Double> = Quadruple(5.0, 10.0, 15.0, 22.5),
    // (minor, moderate, major) g/100g - same shape/semantics as satFatThresholds.
    // Default matches NegativeNutrientsPillar's old flat 0.75/1.25/1.5 cutoffs
    // exactly, so this is behavior-identical for any category without an
    // override below. CONDIMENT and PROCESSED_MEAT get an explicit override:
    // salt is *inherent* to how those categories are made (soy sauce/miso are
    // brine-fermented, ~10-15g salt/100ml; dry-cured meat is salt-cured,
    // ~2.5-6g/100g) the same way saturated fat is inherent to cheese - without
    // this, a completely typical soy sauce or prosciutto always tripped the
    // same flat "major salt" flag every cheese used to trip for sat fat,
    // giving the flag zero power to distinguish "typical for this category"
    // from "unusually salty even for this category."
    val saltThresholds: Triple<Double, Double, Double> = Triple(0.75, 1.25, 1.5),
)

private val DEFAULT_THRESHOLDS = CategoryThresholds(
    proteinG = Triple(3.0, 6.0, 12.0),
    fiberG = Triple(1.5, 3.0, 6.0),
    expectedKcalRange = Pair(50.0, 400.0),
    expectMicronutrients = false,
)

val CATEGORY_THRESHOLDS: Map<ProductCategory, CategoryThresholds> = mapOf(
    ProductCategory.SANDWICH         to CategoryThresholds(Triple(5.0,6.0,12.0),  Triple(2.0,4.0,6.0),  Pair(180.0,320.0), true),
    ProductCategory.READY_MEAL       to CategoryThresholds(Triple(4.0,7.0,10.0),  Triple(2.0,4.0,6.0),  Pair(80.0,200.0),  true),
    // Soup is structurally salty the same way soy sauce/cured meat are - a
    // broth base plus the canning/packaging process puts most commercial
    // soups around 0.7-1.1g salt/100g, which always tripped the flat default
    // 0.75/1.25/1.5 bar (the same category-blind-threshold bug this app's
    // already fixed for CONDIMENT/PROCESSED_MEAT, just never swept to soup).
    ProductCategory.SOUP             to CategoryThresholds(Triple(2.0,4.0,8.0),   Triple(1.0,2.0,4.0),  Pair(25.0,120.0),  true,
        saltThresholds = Triple(1.1,1.6,2.2)),
    // Salt is functionally required for gluten development and shelf life,
    // not just seasoning - ordinary commercial bread structurally runs
    // ~1.0-1.4g/100g, above the generic 1.25g "moderate" bar. Kcal ceiling
    // raised from 300 to 390 - brioche (explicitly matched into BREAD) is
    // egg/butter-enriched and runs ~370-390kcal/100g. Mirrors the identical
    // fix on the Android side (see Scoring Drift Check).
    ProductCategory.BREAD            to CategoryThresholds(Triple(6.0,9.0,12.0),  Triple(3.0,6.0,9.0),  Pair(220.0,390.0), false,
        saltThresholds = Triple(1.3,1.6,2.0)),
    ProductCategory.BREAKFAST_CEREAL to CategoryThresholds(Triple(6.0,10.0,14.0), Triple(5.0,8.0,12.0), Pair(320.0,420.0), true),
    ProductCategory.YOGURT           to CategoryThresholds(Triple(3.0,5.0,9.0),   Triple(0.0,1.0,2.0),  Pair(40.0,120.0),  true),
    ProductCategory.CHEESE           to CategoryThresholds(Triple(15.0,20.0,25.0),Triple(0.0,0.0,0.0),  Pair(200.0,450.0), true,  satFatThresholds = Triple(12.0,20.0,30.0)),
    ProductCategory.PROCESSED_MEAT   to CategoryThresholds(Triple(10.0,15.0,22.0),Triple(0.0,0.0,1.0),  Pair(100.0,400.0), false,
        saltThresholds = Triple(2.5,4.0,6.0)),
    ProductCategory.FRESH_MEAT       to CategoryThresholds(Triple(15.0,20.0,25.0),Triple(0.0,0.0,0.0),  Pair(100.0,300.0), true),
    ProductCategory.FISH             to CategoryThresholds(Triple(15.0,20.0,25.0),Triple(0.0,0.0,0.0),  Pair(80.0,250.0),  true),
    ProductCategory.SNACK_SWEET      to CategoryThresholds(Triple(4.0,7.0,10.0),  Triple(2.0,4.0,6.0),  Pair(350.0,550.0), false),
    // Salted by design (chips ~1.0-1.6g, pretzels ~1.5-2.2g/100g). Kcal range
    // widened from 400-550 to 110-630 - the same regex also routes olives
    // (~115-145kcal) and nuts (~550-630kcal) into this category. Mirrors the
    // identical fix on the Android side (see Scoring Drift Check).
    ProductCategory.SNACK_SALTY      to CategoryThresholds(Triple(6.0,9.0,14.0),  Triple(3.0,5.0,8.0),  Pair(110.0,630.0), false,
        saltThresholds = Triple(1.3,1.8,2.5)),
    ProductCategory.BEVERAGE_SOFT    to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(0.0,50.0),    false),
    // 100% fruit juice with zero added sugar is naturally high in sugar from
    // the fruit itself (OJ ~8-10g, apple ~10-11g, grape ~15-16g/100ml, all
    // intrinsic fructose). Mirrors the identical fix on the Android side (see
    // Scoring Drift Check).
    ProductCategory.BEVERAGE_JUICE   to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,1.0,2.0),  Pair(20.0,60.0),   true,
        sugarThresholds = Quadruple(9.0,13.0,17.0,25.0)),
    ProductCategory.BEVERAGE_WATER   to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(0.0,5.0),     false),
    ProductCategory.ALCOHOLIC_BEVERAGE to CategoryThresholds(Triple(0.0,0.0,0.0), Triple(0.0,0.0,0.0),  Pair(30.0,280.0),  false),
    // Kcal ceiling raised from 400 to 750 - this category includes oil-emulsion
    // condiments (mayonnaise, pesto, tahini, aioli) whose kcal is structurally
    // dominated by fat (mayo ~680-720, pesto ~450-550kcal/100g). Mirrors the
    // identical fix on the Android side (see Scoring Drift Check).
    ProductCategory.CONDIMENT        to CategoryThresholds(Triple(0.0,3.0,7.0),   Triple(0.0,1.0,3.0),  Pair(20.0,750.0),  false,
        sugarThresholds = Quadruple(10.0,20.0,30.0,45.0), saltThresholds = Triple(2.0,5.0,10.0)),
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
    Regex("""\bbi[eè]res?\b|\bbeers?\b|\bvins?\b|\bwines?\b|\bcidres?\b|\bciders?\b|champagne|\bwhisky\b|\bwhiskey\b|\bvodka\b|\bgin\b|\brhum\b|\brum\b|\bcognac\b|\barmagnac\b|\bcalvados\b|\bporto\b|\bliqueurs?\b|spiritueux|\bp[aâ]stis\b|\btequila\b|\bmojito\b|hard seltzer""", RegexOption.IGNORE_CASE) to ProductCategory.ALCOHOLIC_BEVERAGE,
    Regex("""\bsoda\b|\bcola\b|boisson gaz[eé]use|soft drink|\btonic\b|limonade|ice[-\s]?tea|th[eé] glac[eé]|energy drink|red bull|monster""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_SOFT,
    Regex("""\byaourts?\b|yoghurt|yogurt|\bskyr\b|fromage[-\s]?blanc|faisselle|\bquark\b|petit[-\s]suisse""", RegexOption.IGNORE_CASE) to ProductCategory.YOGURT,
    Regex("""\bfromages?\b|\bcheese\b|\bbrie\b|camembert|cheddar|gruy[eè]re|\bgouda\b|mozzarella|parmesan|\bfeta\b|roquefort|emmental|comt[eé]|reblochon|munster|\bch[eè]vre\b|ricotta|mascarpone|halloumi""", RegexOption.IGNORE_CASE) to ProductCategory.CHEESE,
    Regex("""\bsandwich\b|\bburger\b|\bwrap\b|panini|\bkebab\b|\bcroque\b""", RegexOption.IGNORE_CASE) to ProductCategory.SANDWICH,
    // biscuits?\b excludes a trailing "salé(s)/apéritif" qualifier - this
    // pattern sits earlier in the list than SNACK_SALTY's own "biscuits
    // salés" pattern below, and inferCategoryFromName is first-match-wins,
    // so "Biscuits salés apéritif" previously matched here first and got
    // scored against sweet-snack thresholds instead of the salty-cracker
    // thresholds it actually needs. Mirrors the identical fix on the Android
    // side (see Scoring Drift Check).
    Regex("""chocolats?\b|\bchocolate\b|\bbonbon|\bcandy\b|biscuits?\b(?!\s*(sal[eé]s?|ap[eé]ritif))|cookies?\b|g[aâ]teaux?\b|\bcakes?\b|\btartes?\b|\btarts?\b|brownie|\bdonut\b|beignet|barre chocolat[eé]e|kinder|nutella|m&m|haribo|m[aâ]rs|snickers|twix|bounty|gauffres?\b|cr[eê]pes?\b|p[aâ]te [aà] tartiner""", RegexOption.IGNORE_CASE) to ProductCategory.SNACK_SWEET,
    Regex("""\bsaumon\b|\bthon\b|sardine|maquereau|\bhareng\b|cabillaud|\bmerlu\b|\bcolin\b|\btruite\b|crevette|\bcrabe\b|\bmoules\b|hu[iî]tres|(?:filet|darne|pav[eé]) de bar\b|\bdorade\b""", RegexOption.IGNORE_CASE) to ProductCategory.FISH,
    // Trailing \b right after an accented letter never matches in Java regex
    // (\b is defined via ASCII \w, so "e"/"é" is a non-word char to it - a
    // name ending exactly at "pâté" with no ASCII letter after the accent
    // hits non-word -> non-word, i.e. no boundary). Using a Unicode-aware
    // negative lookahead instead of \b for these accent-terminated tokens
    // fixes real product names like "Pâté de campagne" while still
    // correctly rejecting "Pâtes" (pasta, an unrelated food).
    Regex("""\bjambon\b|saucisson|chorizo|\bbacon\b|\blardon|\bsalami\b|pancetta|prosciutto|merguez|\brillettes\b|\bp[aâ]t[eé](?![\w\p{L}])""", RegexOption.IGNORE_CASE) to ProductCategory.PROCESSED_MEAT,
    Regex("""\bpoulet\b|\bb[oœ]uf\b|\bporc\b|\bagneau\b|\bdinde\b|\bcanard\b|viande hach[eé]e|\bsteaks?\b|escalope|magret""", RegexOption.IGNORE_CASE) to ProductCategory.FRESH_MEAT,
    Regex("""\bpain\b|\bbread\b|baguette|brioche|focaccia|ciabatta|\btoasts?\b|\bpita\b|tortilla|\bcracotte""", RegexOption.IGNORE_CASE) to ProductCategory.BREAD,
    Regex("""c[eé]r[eé]ales?\b|\bcereal\b|\bmuesli\b|\bgranola\b|porridge|flocons d['']avoine|\boats\b|cornflakes|chocapic|special k|fitness""", RegexOption.IGNORE_CASE) to ProductCategory.BREAKFAST_CEREAL,
    Regex("""plat pr[eé]par[eé]|plat cuisin[eé]|ready meal|micro[-\s]?ondes|[aà] r[eé]chauffer|lasagne|gratin|paella|risotto|\bcurry\b|chili con carne|hachis parmentier|tartiflette|moussaka""", RegexOption.IGNORE_CASE) to ProductCategory.READY_MEAL,
    Regex("""\bsoupe?s?\b|velout[eé]s?(?![\w\p{L}])|\bpotages?\b|\bbouillons?\b|\bbroths?\b|consomm[eé]s?(?![\w\p{L}])|minestrone|gaspacho|gazpacho""", RegexOption.IGNORE_CASE) to ProductCategory.SOUP,
    // confiture/marmelade/honey added — same "eaten by the tablespoon, not the
    // 100g this scale is normalized to" reasoning checkVeto's own comment uses
    // to exempt CONDIMENT from its flat added-sugar veto, but jam/marmalade/
    // honey previously had no category match at all and fell through to
    // DEFAULT_THRESHOLDS/OTHER, which the veto's exemption list doesn't cover
    // - a jam sat unprotected next to a structurally identical chutney that
    // was explicitly protected. Mirrors the identical fix on the Android side
    // (see Scoring Drift Check).
    Regex("""\bsauces?\b|mayonnaise|\bketchup\b|moutarde|mustard|vinaigrette|\bpesto\b|tahin[ei]|harissa|sambal|sriracha|wasabi|chutney|aioli|\btapenade\b|confiture|marmelade|marmalade|\bmiel\b|\bhoney\b|gel[eé]e de fruits|\bjam\b""", RegexOption.IGNORE_CASE) to ProductCategory.CONDIMENT,
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
