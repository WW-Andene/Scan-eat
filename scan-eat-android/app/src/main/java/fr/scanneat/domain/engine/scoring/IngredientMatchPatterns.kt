package fr.scanneat.domain.engine.scoring

// ============================================================================
// Ingredient-name matching helpers — word-boundary safe, not plain substring
// ============================================================================

// \b-bounded, not `.contains()` - a plain substring check on "vin " (space-
// suffixed to avoid matching "vinaigre"/"vinaigrette") missed the case where
// "vin" is the ingredient's exact/final name with nothing following it (e.g.
// an OFF ingredient list ending in ",vin" parses to a lone Ingredient(name=
// "vin"), no trailing space to match against) - silently skipping the hard
// pregnancy alcohol veto for a real wine ingredient. \b correctly bounds "vin"
// as a whole word regardless of what follows or doesn't follow it, while
// still not matching "vinaigre"/"vinaigrette".
internal val ALCOHOL_INGREDIENT_PATTERN = Regex(
    """\b(?:alcool|alcohol|vin|wine|bi[eè]re|beer)\b""",
    RegexOption.IGNORE_CASE,
)

// \b-bounded, not `.contains()` on the normalized name - a plain substring
// check let bare "mate" match inside "tomate" (tomato) and bare "tea" match
// inside "steak", firing a false pregnancy caffeine warning on completely
// unrelated, extremely common ingredients. Bare "cafe" similarly matched
// inside "décaféiné"/"decafeine" (decaf), warning about caffeine in a product
// that explicitly doesn't have it.
internal val CAFFEINE_SOURCE_PATTERN = Regex(
    """\b(?:cafeine|guarana|yerba mate|mate|the vert|the noir|coffee|tea|cocoa|cacao|cafe)\b""",
)

// Sugar alcohols (polyols) - matched against normalizeForMatching()'d ingredient
// names, so accented/E-number spellings ("sorbitol (E420)") still hit. EU
// Regulation (EC) 1169/2011 Annex III mandates the "excessive consumption may
// induce laxative effects" warning label above 10g polyols/100g - the clearest,
// most concrete regulatory signal available for any digestive condition, and
// the basis of Monash University's low-FODMAP guidance (IBS) and NHS/Mayo
// Clinic diarrhea-diet advice alike, since polyols are poorly absorbed and
// osmotically draw water into the bowel regardless of which condition is doing
// the drawing attention.
internal val POLYOL_PATTERN = Regex(
    """\b(?:sorbitol|mannitol|xylitol|maltitol|erythritol|isomalt|lactitol|polyols?|e420|e421|e953|e965|e966|e967)\b""",
)

// High-FODMAP fructan/GOS sources per Monash University's Low FODMAP Diet
// research (the protocol NICE/British Dietetic Association guidelines
// recommend for IBS) - matched on normalized ingredient names, same word-
// boundary discipline as CAFFEINE_SOURCE_PATTERN above.
internal val HIGH_FODMAP_PATTERN = Regex(
    """\b(?:oignons?|onions?|ail|garlic|inulines?|inulin|chicoree|chicory|ble|wheat|seigle|rye|orge|barley|pois chiches?|chickpeas?|lentilles?|lentils?)\b""",
)

// Tyramine is the most consistently cited migraine trigger across aged/
// fermented foods (National Headache Foundation's "low-tyramine diet";
// American Migraine Foundation trigger-food guidance) - aged cheese and
// cured/fermented meats/condiments are its most common dietary sources.
internal val TYRAMINE_SOURCE_PATTERN = Regex(
    """\b(?:fromage affine|aged cheese|parmesan|roquefort|bleu|blue cheese|cheddar affine|gruyere|comte|salami|pepperoni|jambon cru|prosciutto|saucisson|choucroute|sauerkraut|kimchi|sauce soja|soy sauce|miso)\b""",
)

// EU additive codes for cured/processed-meat nitrite/nitrate preservatives,
// plus the plain words - "hot dog headache" is a long-documented migraine
// trigger (American Migraine Foundation, National Headache Foundation).
internal val NITRATE_PRESERVATIVE_PATTERN = Regex(
    """\b(?:nitrite|nitrites?|nitrate|nitrates?|e249|e250|e251|e252)\b""",
)

// MSG (E621) and aspartame (E951) are both listed by the American Migraine
// Foundation as commonly patient-reported migraine triggers - phrased in the
// guidance text as "reported by some patients", not a settled causal claim,
// since the underlying research is more mixed than for alcohol/tyramine/
// nitrates above.
internal val MSG_PATTERN = Regex("""\b(?:glutamate monosodique|monosodium glutamate|glutamate|msg|e621)\b""")
internal val ASPARTAME_PATTERN = Regex("""\b(?:aspartame|e951)\b""")
