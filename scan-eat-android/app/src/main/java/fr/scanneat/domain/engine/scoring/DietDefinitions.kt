package fr.scanneat.domain.engine.scoring

// ============================================================================
// DIET DEFINITIONS — split out of DietChecker.kt: the per-diet regex rules
// (forbidden/preferred patterns, macro constraints, display notes) checkDiet()
// evaluates against a product.
//
// AUTHORITATIVE sources (preserved from original):
//   Vegan/Vegetarian: Vegan Society UK / Vegetarian Society UK definitions
//   Ketogenic: Volek & Phinney clinical ketosis; net carbs ≤10 g/100 g
//   Halal: Qur'anic prohibition (haram 5:3, 5:90)
//   Kosher: Torah (Leviticus 11); certification required for full compliance
//   Gluten-free: EU Commission Regulation (EC) 41/2009 ≤20 mg/kg gluten
//   Low-FODMAP: Monash University Low FODMAP Diet App reference lists
// ============================================================================

// ============================================================================
// Word-boundary helper matching the JS b() helper — negative lookbehind/
// lookahead for Latin-1 accented letters so patterns work on accented FR text.
// ============================================================================

// Digits are excluded from the boundary too, not just letters — otherwise a
// numeric token like "E120" (carmine) matches as a substring inside a longer,
// unrelated E-number such as E1200 (polydextrose) or E1201 (PVP).
internal fun b(inner: String): Regex =
    // Optional trailing s/x: label text is usually plural ("crevettes",
    // "fromages", "maquereaux") and the strict trailing boundary otherwise
    // rejected every plural a pattern didn't spell out explicitly.
    Regex("(?<![a-zà-ÿ0-9])(?:$inner)[sx]?(?![a-zà-ÿ0-9])", RegexOption.IGNORE_CASE)

// ============================================================================
// English term sets. Every forbidden list below was written for French label
// text; OFF's ingredients_text (the fallback when no _fr text exists) and
// photo-read labels are frequently English for imported products, and a vegan
// check that doesn't know "milk"/"egg"/"gelatin" reports "Skimmed milk powder,
// whey, egg" as compliant - the dangerous direction of error for a diet filter.
// Each set is added alongside (never instead of) the French pattern(s).
// ============================================================================

// A qualifier right before the word ("vegan cheese", "dairy-free milk", "meat-free
// burger") means the product is the plant-based substitute, not the animal food -
// the product name is part of the haystack, so without this a vegan user scanning a
// vegan cheese would be told it contains cheese.
private const val SUBSTITUTE_GUARD =
    "(?<!vegan )(?<!vegan-)(?<!vegetarian )(?<!vegetable )(?<!plant-based )(?<!plant based )(?<!dairy-free )(?<!dairy free )" +
        "(?<!non-dairy )(?<!meat-free )(?<!meatless )(?<!imitation )(?<!veggie )(?<!egg-free )(?<!egg free )"
private const val FREE_FROM_SUFFIX = "(?![- ]free)"

internal val EN_MEAT = b(SUBSTITUTE_GUARD + "(?:meats?|beef|pork|chicken|turkey|duck|lamb|mutton|veal|rabbit|venison|ham|sausages?|salami|pepperoni|prosciutto|chorizo|bacon|lard|tallow|suet|pâté|foie gras|gelatin(?:e)?(?!\\s+(?:halal|vegetable|vegetarian|plant))|animal rennet|collagen|pepsin|bone broth|meat extract|meat stock)" + FREE_FROM_SUFFIX)
internal val EN_FISH = b(SUBSTITUTE_GUARD + "(?:fish|salmon|tuna|cod|sardines?|mackerel|anchov(?:y|ies)|herring|trout|pollock|haddock|halibut|shellfish|crustaceans?|shrimps?|prawns?|lobsters?|crabs?|oysters?|mussels?|squids?|calamari|octopus|clams?|scallops?|molluscs?|isinglass)" + FREE_FROM_SUFFIX)
internal val EN_DAIRY = b(SUBSTITUTE_GUARD +
    "(?:(?<!coconut )(?<!almond )(?<!oat )(?<!soy )(?<!soya )(?<!rice )(?<!cashew )(?<!hazelnut )(?<!nut )milk|buttermilk|" +
    "(?<!peanut )(?<!cocoa )(?<!shea )(?<!nut )(?<!almond )(?<!apple )(?<!cashew )(?<!hazelnut )(?<!coconut )butter(?! beans?)|" +
    "(?<!coconut )cream(?! of tartar)|cheeses?|yog(?:h)?urts?|whey|casein(?:ate)?s?|lactose|lactoserum|ghee|kefir|skyr|lactalbumin|lactoglobulin|mascarpone|ricotta|mozzarella|parmesan|cheddar)" +
    FREE_FROM_SUFFIX)
internal val EN_EGG = b(SUBSTITUTE_GUARD + "(?:eggs?|egg yolks?|egg whites?|albumen|ovalbumin|ovomucoid|lysozyme|mayonnaise)" + FREE_FROM_SUFFIX)
internal val EN_ANIMAL_OTHER = b("honey|propolis|royal jelly|beeswax|carmine|cochineal|shellac|lanolin|bone phosphate")
internal val EN_PORK = b("pork|bacon|ham|lard|salami|pepperoni|prosciutto|pancetta|chorizo|sausages?|swine|gelatin(?:e)?(?!\\s+(?:halal|vegetable|vegetarian|plant|fish))")
internal val EN_SHELLFISH = b("shellfish|crustaceans?|shrimps?|prawns?|lobsters?|crabs?|oysters?|mussels?|squids?|calamari|octopus|clams?|scallops?|molluscs?|eel|shark|sturgeon|rabbit|hare|horse|camel")
// "gluten" itself must not match inside "gluten-free"/"sans gluten"/"free from gluten".
internal val EN_GLUTEN = b("(?<!free from )(?<!sans )gluten(?![- ]free)|wheat(?![- ]free)|barley|rye|spelt|oat(?!s?(?:[- ]free| gluten[- ]free))|semolina|durum|bulgur|couscous|seitan|farro|kamut|triticale|malt|breadcrumbs?")

// A product NAME that declares itself a plant-based / free-from substitute ("Saucisses
// végétales", "Alternative végétale au fromage", "Vegan cheese", "Meat-free burger")
// necessarily contains the animal word it replaces. checkDiet() therefore skips the
// product name (ingredients are still checked) for the animal-product diets when this
// matches - otherwise the exact products those diets exist for are flagged as violations.
internal val SUBSTITUTE_NAME_PATTERN = Regex(
    "v[eé]g[eé]tal|v[eé]g[eé]tarien|v[eé]gan|vegan|plant[- ]based|veggie|meat[- ]?free|meatless|dairy[- ]free|non[- ]dairy|egg[- ]free|sans (?:viande|lait|lactose|[oœ]ufs?)",
    RegexOption.IGNORE_CASE,
)
internal val SUBSTITUTE_NAME_DIETS = setOf(DietKey.VEGETARIAN, DietKey.VEGAN, DietKey.PESCATARIAN, DietKey.DAIRY_FREE)

internal data class DietDef(
    val forbidden: List<Regex> = emptyList(),
    val preferred: List<Regex> = emptyList(),
    val noteFr: String = "",
    val noteEn: String = "",
    // Keto macro constraints
    val maxNetCarbsG: Double? = null,
    val minFatFractionOfKcal: Double? = null,
)

internal val DIET_DEFS: Map<DietKey, DietDef> = mapOf(

    DietKey.VEGETARIAN to DietDef(
        forbidden = listOf(
            EN_MEAT, EN_FISH,
            // (?! v[eé]g[eé]tale), not (?! halal) - halal certification says nothing about
            // whether gelatin is animal-derived (it's orthogonal to vegetarianism), so the
            // earlier (?! halal) lookahead let "gélatine halal" - still animal, just
            // halal-slaughtered - silently pass as vegetarian-compliant. Same exception
            // VEGAN uses two definitions below, for the same reason.
            b("viande|porc|b(?:œ|oe)uf|poulet|dinde|canard|agneau|veau|lapin|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|andouillettes?|andouilles?|confit|rillette|terrines?|pat[eé](?!s)|pâté|foie gras|cro[uû]te de viande|g[eé]lati?ne(?! v[eé]g[eé]tale)|pr[eé]sure(?! v[eé]g[eé]tale| microbienne)|saindoux|suif|graisse (?:animale|de (?:porc|b(?:œ|oe)uf|canard|volaille|oie))|collag[eè]ne|pepsine"),
            b("poisson|saumon|thon|cabillaud|sardine|maquereau|anchois|hareng|crustac[eé]|crevette|homard|crabe|hu[iî]tre|moule|cal(?:a)?mar|poulpe"),
        ),
        noteFr = "Exclut viande, poisson, crustacés, mollusques, gélatine et présure animale.",
        noteEn = "Excludes meat, fish, shellfish, molluscs, animal gelatin and rennet.",
    ),

    DietKey.VEGAN to DietDef(
        forbidden = listOf(
            EN_MEAT, EN_FISH, EN_DAIRY, EN_EGG, EN_ANIMAL_OTHER,
            b("viande|porc|b(?:œ|oe)uf|poulet|dinde|canard|agneau|veau|lapin|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|andouillettes?|andouilles?|confit|rillette|terrines?|pat[eé](?!s)|pâté|foie gras|g[eé]lati?ne(?! v[eé]g[eé]tale)|pr[eé]sure(?! v[eé]g[eé]tale| microbienne)|saindoux|suif|graisse (?:animale|de (?:porc|b(?:œ|oe)uf|canard|volaille|oie))|collag[eè]ne|pepsine|isinglass|colle de poisson"),
            b("poisson|saumon|thon|cabillaud|sardine|maquereau|anchois|hareng|crustac[eé]|crevette|homard|crabe|hu[iî]tre|moule|cal(?:a)?mar|poulpe"),
            b("lait(?! (?:de |d'|d’)(?:coco|soja|amandes?|avoine|riz|noisettes?|cajou|noix|ch[aâ]taignes?|chanvre|[eé]peautre|quinoa|sarrasin|pois|lupin|macadamia))|lactos[eé]rum|petit[- ]lait|cr[eè]me(?! v[eé]g[eé]tale| de tartre| de coco)|beurre(?! de (?:cacao|karit[eé]|cacahu[eè]tes?|coco|amandes?|noisettes?|cajou)| d'arachides?)|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]in(?:e|ates?)|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental"),
            b("oeufs?|œufs?|jaune d'?oeuf|blanc d'?oeuf|ovalbumine|lysozyme|ovomuco[iï]de"),
            b("miel|propolis|gel[eé]e royale|cire d'abeille|beeswax"),
            b("E90[14]"),   // E901 beeswax, E904 shellac
            b("E120"),      // carmine
            b("E542"),      // bone phosphate
            b("E631|E635"), // ribonucleotides — often animal-derived
            b("cochenille|carmin|phosphate osseux|lanoline"),
            // Vitamin D2 (ergocalciferol, yeast/fungal-derived) is always vegan and
            // never flagged. Vitamin D3 (cholecalciferol) is overwhelmingly
            // lanolin-derived (sheep wool grease) in fortified food absent a
            // specific vegan/lichen-sourced claim - flagging it by default and
            // exempting only when the product text itself says "vegan"/"lichen"/
            // "végétal(e)" right after it (a genuinely vegan D3 source exists,
            // extracted from lichen, and is the only non-animal D3 source).
            b("vitamine d3(?! v[eé]gane?| de lichen| v[eé]g[eé]tale?)|cholecalciferol(?! v[eé]gane?| de lichen)|chol[eé]calcif[eé]rol(?! v[eé]gane?| de lichen)"),
        ),
        // Restricted to V-Label (European Vegetarian Union's actual
        // certification mark), not bare marketing words - "vegan"/"plant-
        // based"/"100% végétal" are self-declared claims a manufacturer can
        // print without third-party verification, unlike HALAL/KOSHER/
        // GLUTEN_FREE's own preferred lists below, which only accept named
        // certifying bodies or an explicit certified-mark phrase. Since
        // CERTIFICATION_OVERRIDE_DIETS treats any preferred-pattern hit as
        // trumping detected violations (wiping effectiveViolations to
        // empty), the bare word "plant-based" appearing anywhere in the
        // product name or ingredient text - including a mislabeled/
        // incomplete OFF record that still lists "lait"/dairy as an
        // ingredient - previously erased a real, correctly-detected VEGAN
        // violation and reported the product compliant.
        preferred = listOf(
            b("v-label|v[eé]gan certifi[eé]|certified vegan"),
        ),
        // B12 exists naturally only in animal foods (or fortified plant products the
        // scanner already treats as compliant) - a vegan diet has no other source in
        // scope, so this is a lifestyle-level adequacy note, not a per-product finding.
        noteFr = "Exclut tout produit animal : viande, poisson, œufs, lait, miel, gélatine, cire d'abeille E901, shellac E904, carmin E120, phosphate osseux E542, vitamine D3 non végane (lanoline, sauf source lichen déclarée — la D2 reste toujours végane). Pensez à une supplémentation en vitamine B12, absente du régime végan.",
        noteEn = "Excludes any animal product: meat, fish, eggs, dairy, honey, gelatin, beeswax E901, shellac E904, carmine E120, bone phosphate E542, non-vegan vitamin D3 (lanolin, unless a lichen-sourced claim is declared — D2 is always vegan). Consider B12 supplementation — a vegan diet has no other reliable source.",
    ),

    DietKey.PESCATARIAN to DietDef(
        forbidden = listOf(
            EN_MEAT,
            // Standard pescatarian diets exclude land-animal derivatives the same as
            // vegetarian ones - gelatin/rennet/collagen/pepsin are typically bovine or
            // porcine, so without this a product whose only violation is bovine gelatin
            // previously passed as pescatarian-compliant.
            b("viande|porc|b(?:œ|oe)uf|poulet|dinde|canard|agneau|veau|lapin|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|andouillettes?|andouilles?|confit|rillette|terrines?|pat[eé](?!s)|pâté|foie gras|g[eé]lati?ne(?! v[eé]g[eé]tale)|pr[eé]sure(?! v[eé]g[eé]tale| microbienne)|saindoux|suif|graisse (?:animale|de (?:porc|b(?:œ|oe)uf|canard|volaille|oie))|collag[eè]ne|pepsine"),
        ),
        noteFr = "Végétarien autorisant poisson et fruits de mer.",
        noteEn = "Vegetarian that still allows fish and seafood.",
    ),

    DietKey.KETO to DietDef(
        maxNetCarbsG          = 10.0,
        minFatFractionOfKcal  = 0.60,
        noteFr = "Très faible en glucides (<10 g nets/100 g), riche en lipides (>60 %E).",
        noteEn = "Very low carb (<10 g net carbs/100 g), high fat (>60 %E).",
    ),

    DietKey.HALAL to DietDef(
        forbidden = listOf(
            EN_PORK, ALCOHOL_INGREDIENT_PATTERN,
            b("porc|cochon|lard|lardon|jambon|saucisson|bacon|salami|chorizo|pepperoni|couenne|boudin|saindoux|suif|andouille|pancetta|mortadelle|guanciale"),
            b("graisse animale(?! halal)|mati[eè]re grasse animale(?! halal)"),
            b("alcool|[eé]thanol|vin(?! blanc de cuisson sans alcool)|bi[eè]re|biere|liqueur|rhum|whisky|whiskey|gin|vodka|spiritueux|kirsch|cognac|armagnac|calvados|porto|champagne|eau[- ]de[- ]vie"),
            b("g[eé]lati?ne(?!\\s+(halal|v[eé]g[eé]tale|v[eé]g|de poisson))"),
            b("pr[eé]sure animale(?! halal)|pepsine(?! halal)"),
            b("mono[- ]et diglyc[eé]rides(?! d[''\\s]origine v[eé]g[eé]tale)"),
            b("cochenille|carmin|shellac|gomme[- ]laque"),
        ),
        preferred = listOf(
            b("halal|certifi[eé] halal|AVS|AFCAI|ARGML|HMC|halal[- ]certified"),
        ),
        noteFr = "Pas de porc ni dérivés, pas d'alcool, gélatine/présure doivent être halal ou végétales. Certification AVS/AFCAI/HMC donne un bonus.",
        noteEn = "No pork or derivatives, no alcohol; gelatin/rennet must be halal or vegetal. Recognized certification earns a bonus.",
    ),

    DietKey.KOSHER to DietDef(
        forbidden = listOf(
            EN_PORK, EN_SHELLFISH,
            b("porc|cochon|lard|lardon|jambon|saucisson|bacon|salami|chorizo|pepperoni|saindoux|suif|pancetta|couenne"),
            b("crustac[eé]|crevette|homard|crabe|langouste|langoustine|[eé]crevisse|hu[iî]tre|moule|calmar|encornet|poulpe|p[eé]toncle|palourde|bigorneau|bulot"),
            b("lapin|li[eè]vre|cheval|sanglier|chameau|autruche"),
            b("anguille|requin|esturgeon"),
        ),
        preferred = listOf(
            b("casher|kasher|kosher|\\bOU\\b|\\bOK\\b|Star-K|KOF-K|COR"),
        ),
        noteFr = "Pas de porc, crustacés, mollusques, poissons sans écailles, viande non ruminante. Certification OU/OK/Star-K donne un bonus.",
        noteEn = "No pork, shellfish, molluscs, scaleless fish, non-ruminant meat. Certification OU/OK/Star-K earns a bonus.",
    ),

    DietKey.GLUTEN_FREE to DietDef(
        forbidden = listOf(
            EN_GLUTEN,
            b("(?<!sans )(?<!sans-)(?<!sans tout )gluten(?![- ]free)|bl[eé]|froment|seigle|orge|avoine(?! sans gluten)|[eé]peautre|kamut|triticale|couscous|boulgour|bulgur|chapelure|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge"),
        ),
        // GLUTEN_FREE sits in CERTIFICATION_OVERRIDE_DIETS below (same safety-net
        // as HALAL/KOSHER/VEGAN: a real certification trumps a regex false
        // positive), but had no `preferred` patterns at all - preferredHits could
        // never be non-empty, so `certified` was unconditionally false and the
        // override could never actually trigger despite the diet being listed as
        // if it supported one. "épi barré" is AFDIAG's official French gluten-free
        // certification mark name (the crossed-grain-ear symbol).
        preferred = listOf(
            b("sans gluten certifi[eé]|certifi[eé] sans gluten|AFDIAG|[eé]pi barr[eé]|gluten[- ]free certified|certified gluten[- ]free"),
        ),
        noteFr = "Pas de blé, seigle, orge, avoine (sauf certifiée), épeautre, kamut, triticale. Règl. (CE) 41/2009 ≤20 mg/kg.",
        noteEn = "No wheat, rye, barley, oats (unless certified), spelt, kamut, triticale. EC Regulation 41/2009 ≤20 mg/kg gluten.",
    ),

    DietKey.DAIRY_FREE to DietDef(
        forbidden = listOf(
            EN_DAIRY,
            b("lait(?! (?:de |d'|d’)(?:coco|soja|amandes?|avoine|riz|noisettes?|cajou|noix|ch[aâ]taignes?|chanvre|[eé]peautre|quinoa|sarrasin|pois|lupin|macadamia))|lactos[eé]rum|petit[- ]lait|cr[eè]me(?! de coco| v[eé]g[eé]tale| de tartre)|beurre(?! de (?:cacao|karit[eé]|cacahu[eè]tes?|coco|amandes?|noisettes?|cajou)| d'arachides?)|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]in(?:e|ates?)|lactose|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental"),
        ),
        noteFr = "Exclut tous produits laitiers.",
        noteEn = "Excludes all dairy.",
    ),

    DietKey.PALEO to DietDef(
        forbidden = listOf(
            b("wheat|barley|rye|spelt|oats?|corn|rice|malt|beans?|lentils?|chickpeas?|peanuts?|soy|soya|refined sugar|glucose syrup|corn syrup|maltodextrin"), EN_DAIRY,
            b("bl[eé]|froment|seigle|orge|avoine|[eé]peautre|kamut|triticale|ma[iï]s|riz|farine de bl[eé]|farine de seigle|malt"),
            b("lait(?! (?:de |d'|d’)(?:coco|soja|amandes?|avoine|riz|noisettes?|cajou|noix|ch[aâ]taignes?|chanvre|[eé]peautre|quinoa|sarrasin|pois|lupin|macadamia))|lactos[eé]rum|cr[eè]me(?! de coco| v[eé]g[eé]tale| de tartre)|beurre(?! de (?:cacao|karit[eé]|cacahu[eè]tes?|coco|amandes?|noisettes?|cajou)| d'arachides?)|fromage|yaourt|cas[eé]in(?:e|ates?)|lactose|whey"),
            b("haricot|lentille|pois chiche|soja|arachide|cacahu[eè]te|f[eè]ve"),
            b("sucre raffin[eé]|sirop de glucose|sirop de ma[iï]s|maltodextrin"),
        ),
        noteFr = "Pas de céréales, légumineuses, produits laitiers, ni sucres raffinés.",
        noteEn = "No grains, legumes, dairy, or refined sugars.",
    ),

    DietKey.LOW_FODMAP to DietDef(
        forbidden = listOf(
            b("wheat|rye|barley|onions?|garlic|apples?|pears?|mango|watermelon|honey|agave|(?:high[- ])?fructose|inulin|chicory|artichoke|milk(?! lactose[- ]free)|lactose|beans|lentils?|chickpeas?|sorbitol|mannitol|xylitol|maltitol"),
            // pomme(?! de terre) - "pomme" alone (apple) is high-FODMAP (fructose) and
            // correctly forbidden, but the bare word "pomme" also matches inside "pomme
            // de terre" (potato) - itself low-FODMAP - via this list's own word-boundary
            // regex (b(), see its doc comment), which only guards against adjacent
            // letters/digits, not the space before "de terre". Any potato-containing
            // product was silently misclassified as FODMAP-violating.
            b("bl[eé]|froment|seigle|orge|oignon|ail|pomme(?! de terre)|poire|mangue|past[eè]que|miel|sirop d'agave|sirop de ma[iï]s|fructose|inuline|chicor[eé]e|artichaut|lait(?! sans lactose)|lactose|fromage frais|yaourt|l[eé]gume sec|haricot|lentille|pois chiche|sorbitol|mannitol|xylitol|maltitol"),
        ),
        noteFr = "Teneurs : base de l'université Monash ; régime recommandé dans le SII par les recommandations européennes UEG/ESNM 2021. Exclut oligosaccharides, disaccharides, monosaccharides et polyols mal absorbés.",
        noteEn = "Contents: Monash University database; diet recommended for IBS by the UEG/ESNM 2021 European guideline. Excludes poorly absorbed oligos, disaccharides, monosaccharides and polyols.",
    ),

    DietKey.MEDITERRANEAN to DietDef(
        preferred = listOf(
            b("huile d'olive|olive|poisson|saumon|sardine|maquereau|thon|noix|amande|noisette|pistache|l[eé]gume|tomate|poivron|aubergine|courgette|l[eé]gumineuse|lentille|pois chiche|haricot|c[eé]r[eé]ale compl[eè]te|bl[eé] complet|avoine|feta|yaourt grec"),
        ),
        noteFr = "Priorise olive, poisson, légumes, légumineuses, céréales complètes. Pas d'interdits stricts.",
        noteEn = "Prioritises olive, fish, vegetables, legumes, whole grains. No hard bans.",
    ),

    // WHO 2015 guideline "Sugars intake for adults and children": free sugars =
    // mono- and disaccharides added by the manufacturer, cook or consumer, plus
    // sugars naturally present in honey, syrups, fruit juices and fruit juice
    // concentrates. Sugars inside whole fruit, vegetables and milk are not free
    // sugars and stay allowed; sweeteners aren't sugars and aren't flagged.
    DietKey.NO_FREE_SUGARS to DietDef(
        forbidden = listOf(
            b("(?<!sans )sucres?(?! (?:ajout|naturellement|de lait))|sucre de canne|cassonade|vergeoise|rapadura|sucre inverti|saccharose|dextrose|glucose|fructose|sirop(?: de [a-zà-ÿ'-]+)*|m[eé]lasse|miel|maltose|jus de fruits?|jus concentr[eé]|concentr[eé] de jus|pur jus|jus (?:de |d')(?!citron|lime)[a-zà-ÿ]+"),
            b("(?<!no )(?<!no added )sugars?|cane sugar|brown sugar|invert sugar|sucrose|dextrose|glucose|fructose|(?:[a-z]+ )?syrup|molasses|honey|maltose|fruit juice|juice concentrate"),
        ),
        noteFr = "OMS 2015 : exclut les sucres libres (sucres ajoutés, miel, sirops, jus de fruits). Les sucres des fruits entiers, légumes et du lait restent permis ; les édulcorants ne sont pas comptés.",
        noteEn = "WHO 2015: excludes free sugars (added sugars, honey, syrups, fruit juices). Sugars in whole fruit, vegetables and milk stay allowed; sweeteners aren't counted.",
    ),

    DietKey.CARNIVORE to DietDef(
        forbidden = listOf(
            b("wheat|flour|sugar|fruits?|vegetables?|cereals?|rice|quinoa|corn|beans?|lentils?|peas?|soy|soya|peanuts?|vegetable oil|olive oil"),
            b("bl[eé]|farine|sucre|fruit|l[eé]gume|c[eé]r[eé]ale|riz|quinoa|ma[iï]s|haricot|lentille|pois|soja|arachide|huile v[eé]g[eé]tale|huile d'olive"),
        ),
        noteFr = "Exclut tout ingrédient végétal.",
        noteEn = "Excludes any plant ingredient.",
    ),
)

// Kosher law's meat+dairy prohibition (basar b'chalav, Exodus 23:19 et al.) is
// structurally different from KOSHER's forbidden[] list above: it isn't about
// any single ingredient being forbidden (beef and cheese are each
// individually kosher), it's about the two FAMILIES never appearing TOGETHER
// in one product. checkDiet()'s per-forbidden-pattern loop can never express
// that - it only asks "does this one pattern match," never "do these two
// patterns both match." A "beef ragù with cream sauce" or a "cheeseburger-
// flavoured" snack containing both a meat ingredient and a dairy ingredient
// (neither individually forbidden) previously passed as kosher-compliant,
// the same class of real-harm false-compliance already fixed for vegan.
// Ruminant/poultry meat only, not KOSHER's own pork-specific forbidden list
// (pork is already caught there) - and no fish/egg here, since kosher fish/
// eggs are pareve (neutral) and may be eaten with either meat or dairy.
internal val KOSHER_MEAT_PATTERN = b("meats?|beef|veal|chicken|turkey|duck|lamb|mutton|bacon|sausages?|corned beef|viande|b(?:œ|oe)uf|veau|poulet|dinde|canard|agneau|mouton|lard|lardon|saucisse|saucisson|chorizo|merguez|pastrami|corned[- ]beef")
internal val KOSHER_DAIRY_PATTERN = b("(?<!coconut )(?<!almond )(?<!oat )(?<!soy )(?<!rice )milk|cheeses?|yog(?:h)?urts?|cream(?! of tartar)|(?<!peanut )(?<!cocoa )(?<!shea )(?<!nut )butter|casein|lait(?! (?:de |d'|d’)(?:coco|soja|amandes?|avoine|riz|noisettes?|cajou|noix|ch[aâ]taignes?|chanvre|[eé]peautre|quinoa|sarrasin|pois|lupin|macadamia))|lactos[eé]rum|petit[- ]lait|cr[eè]me(?! v[eé]g[eé]tale| de tartre| de coco)|beurre(?! de (?:cacao|karit[eé]|cacahu[eè]tes?|coco|amandes?|noisettes?|cajou)| d'arachides?)|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]in(?:e|ates?)|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental")

// Diets where a certification mark overrides detected violations
internal val CERTIFICATION_OVERRIDE_DIETS = setOf(DietKey.HALAL, DietKey.KOSHER, DietKey.VEGAN, DietKey.GLUTEN_FREE)
internal val UNVERIFIABLE_DIETS = setOf(DietKey.HALAL, DietKey.KOSHER)
