package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

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
    // Soup is structurally salty the same way soy sauce/cured meat are - a
    // broth base plus the canning/packaging process puts most commercial
    // soups around 0.7-1.1g salt/100g, which always tripped the flat default
    // 0.75/1.25/1.5 bar (the same category-blind-threshold bug this app's
    // already fixed for CONDIMENT/PROCESSED_MEAT, just never swept to soup).
    ProductCategory.SOUP             to CategoryThresholds(Triple(2.0,4.0,8.0),   Triple(1.0,2.0,4.0),  Pair(25.0,120.0),  true,
        saltThresholds = Triple(1.1,1.6,2.2)),
    // Salt is functionally required for gluten development and shelf life,
    // not just seasoning - ordinary commercial bread structurally runs
    // ~1.0-1.4g/100g, above the generic 1.25g "moderate" bar, the same
    // process-makes-it-inherently-salty pattern already fixed for SOUP/
    // CONDIMENT/PROCESSED_MEAT but never swept to bread, one of the most
    // commonly scanned categories. Kcal ceiling raised from 300 to 390 -
    // brioche (explicitly matched into BREAD by its own name pattern below)
    // is egg/butter-enriched and runs ~370-390kcal/100g, well above lean
    // bread's 220-300 range, tripping an energy anomaly for being a normal
    // brioche.
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
    // Salted by design (chips ~1.0-1.6g, pretzels ~1.5-2.2g, salted crackers
    // ~1.2-1.8g/100g) - same category-blindness pattern as bread above, just
    // more pronounced since this category IS defined by intentional salting.
    // Kcal range widened from 400-550 (tuned for fried starch snacks) to
    // 110-630 - the same regex also routes olives (~115-145kcal, brine-
    // packed/mostly water) and nuts (almonds/cashews/pistachios ~550-630kcal)
    // into this category, so the chip-tuned band flagged completely ordinary
    // olives and nuts for "abnormal" energy density.
    ProductCategory.SNACK_SALTY      to CategoryThresholds(Triple(6.0,9.0,14.0),  Triple(3.0,5.0,8.0),  Pair(110.0,630.0), false,
        saltThresholds = Triple(1.3,1.8,2.5)),
    ProductCategory.BEVERAGE_SOFT    to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(0.0,50.0),    false),
    // 100% fruit juice with zero added sugar is naturally high in sugar from
    // the fruit itself (OJ ~8-10g, apple ~10-11g, grape ~15-16g/100ml, all
    // intrinsic fructose) - against the generic 5g/10g minor/moderate bar, an
    // unadulterated glass of juice already reads "moderate" and grape juice
    // reads "major," identical to how an actually-sweetened soda would score.
    // Same natural-vs-added-sugar gap already closed for jam/honey via the
    // CONDIMENT reroute, never applied to juice even though the underlying
    // structural cause (natural sugar concentration) is the same.
    ProductCategory.BEVERAGE_JUICE   to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,1.0,2.0),  Pair(20.0,60.0),   true,
        sugarThresholds = Quadruple(9.0,13.0,17.0,25.0)),
    ProductCategory.BEVERAGE_WATER   to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(0.0,5.0),     false),
    // Beer ~35-45kcal/100ml, wine ~70-90kcal/100ml, spirits ~220-280kcal/100ml -
    // a range wide enough to span all three without tripping the "energy
    // anomaly" flag in NegativeNutrientsPillar.kt purely for being liquor
    // rather than beer. Sugar uses the default thresholds (dry wine/spirits
    // are ~0g, dessert wine/liqueurs are the ones that should trip them).
    ProductCategory.ALCOHOLIC_BEVERAGE to CategoryThresholds(Triple(0.0,0.0,0.0), Triple(0.0,0.0,0.0),  Pair(30.0,280.0),  false),
    // Kcal ceiling raised from 400 to 750 - this category's own name pattern
    // explicitly includes oil-emulsion condiments (mayonnaise, pesto, tahini,
    // aioli) whose kcal is structurally dominated by fat, not the watery
    // sauces the 400 ceiling was tuned for (mayo ~680-720, pesto ~450-550,
    // tahini ~590-600, aioli ~600+kcal/100g) - the same reasoning OIL_FAT's
    // own 700-900 range already uses two lines below.
    ProductCategory.CONDIMENT        to CategoryThresholds(Triple(0.0,3.0,7.0),   Triple(0.0,1.0,3.0),  Pair(20.0,750.0),  false,
        sugarThresholds = Quadruple(10.0,20.0,30.0,45.0), saltThresholds = Triple(2.0,5.0,10.0)),
    // Honey ~76-80g sugar/100g, jam/marmalade ~55-65g/100g - all intrinsic
    // fructose from fruit/nectar, not an added-sugar manufacturing choice,
    // the same reasoning BEVERAGE_JUICE's own sugarThresholds override uses.
    // Previously shared CONDIMENT's 10/20/30/45 band (tuned for oversweetened
    // savory sauces), which put plain honey past the 45g "critical" ceiling -
    // the same category-blind-threshold bug already fixed for juice, applied
    // here to close the gap for real this time (a prior comment claimed this
    // was already fixed via the CONDIMENT reroute, but the reroute only ever
    // shared CONDIMENT's bucket, never gave honey/jam their own thresholds).
    ProductCategory.SPREAD_SWEET     to CategoryThresholds(Triple(0.0,0.0,1.0),   Triple(0.0,1.0,2.0),  Pair(250.0,320.0), false,
        sugarThresholds = Quadruple(40.0,55.0,70.0,85.0), saltThresholds = Triple(0.5,1.0,1.5)),
    ProductCategory.OIL_FAT          to CategoryThresholds(Triple(0.0,0.0,0.0),   Triple(0.0,0.0,0.0),  Pair(700.0,900.0), false,
        satFatThresholds = Triple(20.0,35.0,50.0)),
    // Plant-based meat/fish/cheese substitutes span a wide real-world range
    // (tofu ~8g protein/~76kcal, textured pea protein ~18-50g/100g,
    // tempeh ~190kcal, seitan ~370kcal, coconut-oil-based vegan cheese
    // ~250-320kcal with elevated sat fat) - protein/fiber/kcal bands are
    // wide enough to span the category without inheriting real dairy
    // cheese's much higher protein/sat-fat expectations (12-30g sat fat)
    // or real meat's kcal-from-animal-fat assumptions.
    ProductCategory.PLANT_BASED_ALTERNATIVE to CategoryThresholds(Triple(4.0,8.0,15.0), Triple(1.0,2.0,4.0), Pair(60.0,370.0), false,
        satFatThresholds = Triple(6.0,12.0,20.0)),
    // Dairy ice cream ~200-320kcal/100g (Häagen-Dazs/premium ~280-320,
    // standard tubs ~180-250), sorbet ~100-150kcal (little/no fat, higher
    // sugar) - kcal range wide enough to span both under one category rather
    // than inventing a fruit-vs-dairy split with no real product-name signal
    // to tell them apart reliably. Sugar is inherently high (18-25g/100g
    // dairy ice cream, 25-30g sorbet) - same "high but normal for the
    // category" reasoning already used for SPREAD_SWEET/BEVERAGE_JUICE, not
    // flagged as added-sugar-style "critical" the way the default band would.
    ProductCategory.ICE_CREAM to CategoryThresholds(Triple(1.5,3.0,5.0), Triple(0.0,0.0,1.0), Pair(80.0,340.0), false,
        satFatThresholds = Triple(6.0,12.0,18.0), sugarThresholds = Quadruple(15.0,22.0,28.0,35.0)),
    // Dry/uncooked pasta, rice, couscous, quinoa, semoule, boulgour - OFF
    // packaging near-universally declares nutrition per 100g dry, not
    // cooked, so these thresholds assume dry weight. A product genuinely
    // logged/labeled per cooked weight (~1/3 the kcal density) would read as
    // abnormally low-energy against this band - a real but accepted
    // limitation, no reliable name-based signal distinguishes the two.
    // Protein/fiber bands span white (rice ~7g protein/~2g fiber) through
    // wholegrain/quinoa (~13-14g protein/~6-8g fiber).
    ProductCategory.GRAIN to CategoryThresholds(Triple(6.0,10.0,14.0), Triple(2.0,4.0,8.0), Pair(280.0,400.0), false),
    // Fresh, unprocessed fruit and vegetables - deliberately wide bands, the
    // same category-blindness reasoning already used for SNACK_SALTY (olives
    // vs. nuts) and OIL_FAT: a category this heterogeneous (lettuce ~15kcal
    // vs. avocado ~160kcal, cucumber ~2g carbs vs. banana ~20g) would either
    // need per-item thresholds no name-based classifier can assign, or wide
    // enough bands that ordinary produce never trips an "abnormal nutrition"
    // flag just for being lettuce or being avocado. Sugar band widened for
    // the same intrinsic-fruit-sugar reasoning as BEVERAGE_JUICE/SPREAD_SWEET
    // (banana ~12g, grapes ~16g) - vegetables' near-zero sugar never
    // approaches even the default band, so only the fruit side needed room.
    ProductCategory.FRESH_PRODUCE to CategoryThresholds(Triple(1.0,2.5,5.0), Triple(2.0,3.5,6.0), Pair(10.0,200.0), true,
        sugarThresholds = Quadruple(8.0,14.0,20.0,30.0)),
    // Eggs are tightly consistent nutritionally (raw egg ~155kcal, ~13g
    // protein, ~11g fat/100g, near-zero carbs/fiber/sugar), unlike every
    // other category above which needed a wide band for real heterogeneity.
    ProductCategory.EGG to CategoryThresholds(Triple(8.0,11.0,15.0), Triple(0.0,0.0,0.0), Pair(120.0,180.0), true),
    ProductCategory.OTHER            to DEFAULT_THRESHOLDS,
)

fun getThresholds(cat: ProductCategory): CategoryThresholds =
    CATEGORY_THRESHOLDS[cat] ?: DEFAULT_THRESHOLDS

// ============================================================================
// SECTION 3b: NAME-BASED CATEGORY INFERENCE
// ============================================================================

private val NAME_CATEGORY_PATTERNS: List<Pair<Regex, ProductCategory>> = listOf(
    // §A5-audit finding: plain "eau\b" matched inside "eau de vie" (a fruit
    // brandy, ~40% ABV, ~250kcal/100ml) via first-match-wins, since this
    // pattern runs before ALCOHOLIC_BEVERAGE further down - misclassifying a
    // real spirit as water and scoring it against near-zero-kcal thresholds.
    // Excluded via negative lookahead rather than reordering the whole list,
    // since "eau" alone is otherwise a safe, common water-product signal.
    Regex("""eau\b(?!\s*de\s*vie)|water\b|spring water|eau de source|eau min[eé]rale|eau gaz[eé]use""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_WATER,
    Regex("""\bjus\b|\bjuice\b|\bnectar\b|smoothie|fruit drink""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_JUICE,
    // Checked before BEVERAGE_SOFT below, same reason ALCOHOLIC_BEVERAGE exists
    // at all - a "bière"/"beer"/"vin"/"whisky" name previously fell through to
    // BEVERAGE_SOFT (or OTHER) and inherited soda-shaped sugar/kcal reference
    // ranges instead of alcohol-appropriate ones.
    Regex("""\bbi[eè]res?\b|\bbeers?\b|\bvins?\b|\bwines?\b|(?<!vinaigre de )\bcidres?\b|\bciders?\b|champagne|\bwhisky\b|\bwhiskey\b|\bvodka\b|\bgin\b|\brhum\b|\brum\b|\bcognac\b|\barmagnac\b|\bcalvados\b|\bporto\b|\bliqueurs?\b|spiritueux|\bp[aâ]stis\b|\btequila\b|\bmojito\b|hard seltzer""", RegexOption.IGNORE_CASE) to ProductCategory.ALCOHOLIC_BEVERAGE,
    Regex("""\bsoda\b|\bcola\b|boisson gaz[eé]use|soft drink|\btonic\b|limonade|ice[-\s]?tea|th[eé] glac[eé]|energy drink|red bull|monster""", RegexOption.IGNORE_CASE) to ProductCategory.BEVERAGE_SOFT,
    Regex("""\byaourts?\b|yoghurt|yogurt|\bskyr\b|fromage[-\s]?blanc|faisselle|\bquark\b|petit[-\s]suisse|cr[eè]me dessert|\bflans?\b|li[eé]geois|panna cotta|riz au lait|entremets?\b""", RegexOption.IGNORE_CASE) to ProductCategory.YOGURT,
    // Standalone regional cheese names that don't necessarily contain the word
    // "fromage" itself in the product name (e.g. a plain "Cantal AOP" or
    // "Boursin ail & fines herbes") - "bleu"/"raclette" deliberately left out,
    // too collision-prone ("cordon bleu" is chicken, "kit raclette" mixes
    // cheese with charcuterie/potatoes as one weighed product).
    Regex("""\bfromages?\b|\bcheese\b|\bbrie\b|camembert|cheddar|gruy[eè]re|\bgouda\b|mozzarella|parmesan|\bfeta\b|roquefort|emmental|comt[eé]|reblochon|munster|\bch[eè]vre\b|ricotta|mascarpone|halloumi|\bcantal\b|beaufort|morbier|\btomme\b|boursin|babybel|saint-nectaire""", RegexOption.IGNORE_CASE) to ProductCategory.CHEESE,
    Regex("""\bsandwich\b|\bburger\b|\bwrap\b|panini|\bkebab\b|\bcroque\b""", RegexOption.IGNORE_CASE) to ProductCategory.SANDWICH,
    // biscuits?\b excludes a trailing "salé(s)/apéritif" qualifier - this
    // pattern sits earlier in the list than SNACK_SALTY's own "biscuits
    // salés" pattern below, and inferCategoryFromName is first-match-wins,
    // so "Biscuits salés apéritif" previously matched here first and got
    // scored against sweet-snack thresholds (kcal/protein/fiber tuned for
    // cookies) instead of the salty-cracker thresholds it actually needs.
    // Checked before SNACK_SWEET below, not after - a "barre de céréales au
    // chocolat" or "céréales chocolatées" contains both "céréale" and
    // "chocolat", and SNACK_SWEET's own chocolats?\b pattern used to sit
    // first in the list (first-match-wins), so any name literally containing
    // the token "chocolat" got SNACK_SWEET's thresholds (fiberG 2/4/6,
    // expectMicronutrients=false) instead of BREAKFAST_CEREAL's (fiberG
    // 5/8/12, expectMicronutrients=true) - even though a real chocolate-
    // coated granola/cereal bar runs ~6-8g fiber/100g and is often fortified,
    // structurally closer to a cereal than a candy bar. Same
    // shadowing-keyword class already fixed for "biscuits salés" below, just
    // solved here by reordering instead of a negative lookahead (a plain
    // "Tablette de chocolat noir" has no cereal keyword, so it still
    // correctly falls through to SNACK_SWEET regardless of this reorder).
    Regex("""c[eé]r[eé]ales?\b|\bcereal\b|\bmuesli\b|\bgranola\b|porridge|flocons d['']avoine|\boats\b|cornflakes|chocapic|special k|fitness""", RegexOption.IGNORE_CASE) to ProductCategory.BREAKFAST_CEREAL,
    Regex("""chocolats?\b|\bchocolate\b|\bbonbon|\bcandy\b|biscuits?\b(?!\s*(sal[eé]s?|ap[eé]ritif))|cookies?\b|g[aâ]teaux?\b|\bcakes?\b|\btartes?\b|\btarts?\b|brownie|\bdonut\b|beignet|barre chocolat[eé]e|kinder|nutella|m&m|haribo|m[aâ]rs|snickers|twix|bounty|gauffres?\b|cr[eê]pes?\b|p[aâ]te [aà] tartiner""", RegexOption.IGNORE_CASE) to ProductCategory.SNACK_SWEET,
    // §-audit finding: ice cream/sorbet had no category at all - see
    // CategoryThresholds' own ICE_CREAM entry above for the nutrition
    // reasoning. Checked before FISH below so "esquimau"/"cornet" etc. never
    // collide with anything meat/fish-related (none of the words overlap,
    // this ordering is just defensive).
    Regex("""\bglaces?\b|ice[-\s]?cream|\bsorbets?\b|cornet glac[eé]|esquimau|cr[eè]me glac[eé]e|popsicle""", RegexOption.IGNORE_CASE) to ProductCategory.ICE_CREAM,
    // \bpoisson\b (generic "fish"/"fish fillet") added - a very common frozen
    // product name ("Poisson pané", "Filet de poisson") previously only
    // matched if it named a specific species. Also added a few common species/
    // preparations missing entirely: lieu, haddock/églefin, sole, turbot, surimi,
    // calamars/poulpe.
    Regex("""\bpoissons?\b|\bsaumon\b|\bthon\b|sardine|maquereau|\bhareng\b|cabillaud|\bmerlu\b|\bcolin\b|\btruite\b|\blieu\b|haddock|[eé]glefin|\bsole\b|\bturbot\b|surimi|calamars?|\bpoulpe\b|crevette|\bcrabe\b|\bmoules\b|hu[iî]tres|(?:filet|darne|pav[eé]) de bar\b|\bdorade\b""", RegexOption.IGNORE_CASE) to ProductCategory.FISH,
    // Trailing \b right after an accented letter never matches in Java regex
    // (\b is defined via ASCII \w, so "e"/"é" is a non-word char to it - a
    // name ending exactly at "pâté" with no ASCII letter after the accent
    // hits non-word -> non-word, i.e. no boundary). Using a Unicode-aware
    // negative lookahead instead of \b for these accent-terminated tokens
    // fixes real product names like "Pâté de campagne" while still
    // correctly rejecting "Pâtes" (pasta, an unrelated food).
    // "Terrine de campagne aux piments d'espelette" (a real scanned product) fell
    // through every category regex to Other/uncertain - terrine is a charcuterie
    // spread exactly like pâté/rillettes (often the same recipe, molded instead
    // of potted), just missing from this list. Same audit swept the rest of this
    // list for the same class of gap and added saucisse(s) (generic sausage -
    // only the dry-cured "saucisson" was covered), boudin and foie gras (both
    // already recognized by DietDefinitions.kt's meat keyword lists, but never
    // added here for category inference), and andouille/andouillette.
    Regex("""\bjambon\b|saucissons?|\bsaucisses?\b|chorizo|\bbacon\b|\blardon|\bsalami\b|pancetta|prosciutto|merguez|\brillettes\b|\bterrines?\b|\bboudins?\b|andouillettes?|andouilles?|foie gras|\bp[aâ]t[eé](?![\w\p{L}])""", RegexOption.IGNORE_CASE) to ProductCategory.PROCESSED_MEAT,
    // veau/lapin added alongside the PROCESSED_MEAT sweep above - previously
    // only caught indirectly if the name also happened to contain "escalope"
    // or "steak" (e.g. "Escalope de veau" matched, but "Rôti de veau" or
    // "Blanquette de veau" didn't).
    Regex("""\bpoulet\b|\bb[oœ]uf\b|\bporc\b|\bagneau\b|\bdinde\b|\bcanard\b|\bveau\b|\blapin\b|viande hach[eé]e|\bsteaks?\b|escalope|magret""", RegexOption.IGNORE_CASE) to ProductCategory.FRESH_MEAT,
    Regex("""\bpain\b|\bbread\b|baguette|brioche|focaccia|ciabatta|\btoasts?\b|\bpita\b|tortilla|\bcracotte""", RegexOption.IGNORE_CASE) to ProductCategory.BREAD,
    Regex("""plat pr[eé]par[eé]|plat cuisin[eé]|ready meal|micro[-\s]?ondes|[aà] r[eé]chauffer|lasagne|gratin|paella|risotto|\bcurry\b|chili con carne|hachis parmentier|tartiflette|moussaka""", RegexOption.IGNORE_CASE) to ProductCategory.READY_MEAL,
    // §-audit finding: dry pasta/rice/grains had no category at all - see
    // CategoryThresholds' own GRAIN entry above for the dry-vs-cooked-weight
    // caveat. Checked after READY_MEAL above (first-match-wins), so a real
    // prepared dish name like "Risotto aux champignons" or "Paella" still
    // routes to READY_MEAL first - only a plain "Riz basmati"/"Pâtes
    // penne"-style bag name (no ready-meal keyword) falls through to here.
    Regex("""\bp[aâ]tes\b|spaghettis?\b|macaronis?\b|pennes?\b|fusillis?\b|tagliatelles?\b|coquillettes?\b|nouilles?\b|vermicelles?\b|\briz\b|couscous|semoule|quinoa|boulgour""", RegexOption.IGNORE_CASE) to ProductCategory.GRAIN,
    Regex("""\bœufs?\b|\boeufs?\b|\beggs?\b""", RegexOption.IGNORE_CASE) to ProductCategory.EGG,
    Regex("""\bsoupe?s?\b|velout[eé]s?(?![\w\p{L}])|\bpotages?\b|\bbouillons?\b|\bbroths?\b|consomm[eé]s?(?![\w\p{L}])|minestrone|gaspacho|gazpacho""", RegexOption.IGNORE_CASE) to ProductCategory.SOUP,
    // Own category, checked before CONDIMENT below - honey/jam/marmalade's
    // sugar is intrinsic fruit/nectar fructose (~55-80g/100g), nutritionally
    // unlike the near-zero-sugar savory sauces CONDIMENT's regex otherwise
    // matches. See SPREAD_SWEET's threshold-table entry above for the full
    // rationale.
    Regex("""confiture|marmelade|marmalade|\bmiel\b|\bhoney\b|gel[eé]e de fruits|\bjam\b""", RegexOption.IGNORE_CASE) to ProductCategory.SPREAD_SWEET,
    // vinaigre (plain vinegar) added - a common pantry item previously
    // uncategorized (vinaigrette, the salad-dressing product, was already
    // covered; the vinegar itself wasn't).
    Regex("""\bsauces?\b|mayonnaise|\bketchup\b|moutarde|mustard|vinaigrette|\bvinaigres?\b|\bpesto\b|tahin[ei]|harissa|sambal|sriracha|wasabi|chutney|aioli|\btapenade\b""", RegexOption.IGNORE_CASE) to ProductCategory.CONDIMENT,
    // User-flagged: "beurre" alone matched inside nut butters (beurre de
    // cacahuète/arachide/amande/noisette/noix) before the engine ever
    // reached SNACK_SALTY's cacahuètes/amandes/noisettes keywords further
    // down - peanut butter (~25g protein, ~590-600kcal/100g) was scored
    // against OIL_FAT's zero-protein-expectation, 700-900kcal band instead.
    // Excluded here so it falls through to SNACK_SALTY, a much closer fit
    // (also added "arachides?\b" there so "beurre d'arachide" - the
    // Québécois/technical term for peanut, also used in France - still
    // matches even without the word "cacahuète" itself in the name).
    Regex("""huile d['']olive|huile de colza|huile de tournesol|huile v[eé]g[eé]tale|\bolive oil\b|sunflower oil|canola oil|margarine|\bbeurre\b(?!\s*d[e']\s*(cacahu[eè]te|arachide|amande|noisette|noix))|\bbutter\b|saindoux""", RegexOption.IGNORE_CASE) to ProductCategory.OIL_FAT,
    Regex("""\bchips\b|\bcrisps?\b|crackers?\b|biscuits? sal[eé]s?|\bpopcorn\b|\bpretzels?\b|cacahu[eè]tes?\b|arachides?\b|\bamandes?\b|\bnoix\b(?!\s*de\s*(saint-jacques|veau))|noisettes?\b|noix de cajou|noix de p[eé]can|noix du br[eé]sil|amande grill[eé]e|pistaches?\b|olives?\b""", RegexOption.IGNORE_CASE) to ProductCategory.SNACK_SALTY,
    // §-audit finding: fresh fruit/vegetables had no category at all - see
    // CategoryThresholds' own FRESH_PRODUCE entry above for the wide-band
    // reasoning. Deliberately LAST in this list (first-match-wins) so every
    // more specific category above (soup, snack, juice, jam, ready meal,
    // bread...) gets first claim - a raw "pomme"/"tomate"/"salade" only
    // falls through to here once nothing more specific already matched it.
    Regex("""\bfruits?\b|\bl[eé]gumes?\b|\bpommes?\b|\bpoires?\b|\bbananes?\b|\boranges?\b|\bfraises?\b|\bframboises?\b|\braisins?\b|\bp[eê]ches?\b|\babricots?\b|\bkiwis?\b|\bmangues?\b|\bananas\b|\bcitrons?\b|\bpast[eè]ques?\b|\bmelons?\b|\bavocats?\b|\btomates?\b|\bcarottes?\b|\bcourgettes?\b|\baubergines?\b|\bpoivrons?\b|\boignons?\b|\bail\b|\bsalade\b|\blaitue\b|\b[eé]pinards?\b|\bbrocolis?\b|\bchoux?\b(?!\s*[aà]\s*la\s*cr[eè]me)|\bharicots? verts?\b|\bpetits? pois\b|\bpoireaux?\b|\bconcombres?\b|\bradis\b|\bc[eé]leri\b|\bchampignons?\b|pommes? de terre|\bpatates?\b""", RegexOption.IGNORE_CASE) to ProductCategory.FRESH_PRODUCE,
)

fun inferCategoryFromName(name: String): ProductCategory {
    if (name.isBlank()) return ProductCategory.OTHER
    for ((regex, category) in NAME_CATEGORY_PATTERNS) {
        if (regex.containsMatchIn(name)) return category
    }
    return ProductCategory.OTHER
}
