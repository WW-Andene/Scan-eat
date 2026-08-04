package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

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
private val ALCOHOL_INGREDIENT_PATTERN = Regex(
    """\b(?:alcool|alcohol|vin|wine|bi[eè]re|beer)\b""",
    RegexOption.IGNORE_CASE,
)

// \b-bounded, not `.contains()` on the normalized name - a plain substring
// check let bare "mate" match inside "tomate" (tomato) and bare "tea" match
// inside "steak", firing a false pregnancy caffeine warning on completely
// unrelated, extremely common ingredients. Bare "cafe" similarly matched
// inside "décaféiné"/"decafeine" (decaf), warning about caffeine in a product
// that explicitly doesn't have it.
private val CAFFEINE_SOURCE_PATTERN = Regex(
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
private val POLYOL_PATTERN = Regex(
    """\b(?:sorbitol|mannitol|xylitol|maltitol|erythritol|isomalt|lactitol|polyols?|e420|e421|e953|e965|e966|e967)\b""",
)

// High-FODMAP fructan/GOS sources per Monash University's Low FODMAP Diet
// research (the protocol NICE/British Dietetic Association guidelines
// recommend for IBS) - matched on normalized ingredient names, same word-
// boundary discipline as CAFFEINE_SOURCE_PATTERN above.
private val HIGH_FODMAP_PATTERN = Regex(
    """\b(?:oignons?|onions?|ail|garlic|inulines?|inulin|chicoree|chicory|ble|wheat|seigle|rye|orge|barley|pois chiches?|chickpeas?|lentilles?|lentils?)\b""",
)

// Tyramine is the most consistently cited migraine trigger across aged/
// fermented foods (National Headache Foundation's "low-tyramine diet";
// American Migraine Foundation trigger-food guidance) - aged cheese and
// cured/fermented meats/condiments are its most common dietary sources.
private val TYRAMINE_SOURCE_PATTERN = Regex(
    """\b(?:fromage affine|aged cheese|parmesan|roquefort|bleu|blue cheese|cheddar affine|gruyere|comte|salami|pepperoni|jambon cru|prosciutto|saucisson|choucroute|sauerkraut|kimchi|sauce soja|soy sauce|miso)\b""",
)

// EU additive codes for cured/processed-meat nitrite/nitrate preservatives,
// plus the plain words - "hot dog headache" is a long-documented migraine
// trigger (American Migraine Foundation, National Headache Foundation).
private val NITRATE_PRESERVATIVE_PATTERN = Regex(
    """\b(?:nitrite|nitrites?|nitrate|nitrates?|e249|e250|e251|e252)\b""",
)

// MSG (E621) and aspartame (E951) are both listed by the American Migraine
// Foundation as commonly patient-reported migraine triggers - phrased in the
// guidance text as "reported by some patients", not a settled causal claim,
// since the underlying research is more mixed than for alcohol/tyramine/
// nitrates above.
private val MSG_PATTERN = Regex("""\b(?:glutamate monosodique|monosodium glutamate|glutamate|msg|e621)\b""")
private val ASPARTAME_PATTERN = Regex("""\b(?:aspartame|e951)\b""")

// ============================================================================
// Personal score sub-computations — one rule category per function, each a
// direct extraction of one `===== SECTION =====` block from the original
// monolithic computePersonalScore(). Behavior is unchanged; only the grouping
// into named functions is new.
// ============================================================================

/** Diet compliance and (where applicable) a hard veto + reason for it. */
internal data class ConditionalAdjustments(
    val adjustments: List<PersonalAdjustment>,
    val veto: Boolean = false,
    val dietReason: String? = null,
)

// ===== DIET COMPLIANCE (HARD) =====
internal fun checkDietCompliance(product: Product, profile: Profile, lang: String): ConditionalAdjustments {
    if (profile.diet == DietKey.NONE) return ConditionalAdjustments(emptyList())
    val adjustments = mutableListOf<PersonalAdjustment>()
    var veto = false
    var dietReason: String? = null
    val r = checkDiet(product, profile.diet, lang)
    if (!r.compliant) {
        veto = true
        dietReason = r.reason
        adjustments += PersonalAdjustment(0.0, r.reason ?: "", AdjustmentCategory.DIET, veto = true)
    } else if (r.certified) {
        val label = if (lang == "en") profile.diet.labelEn else profile.diet.labelFr
        adjustments += PersonalAdjustment(
            points   = 5.0,
            reason   = if (lang == "en") "$label certification detected: ${r.preferredHits.take(2).joinToString()}"
                       else "Certification $label détectée : ${r.preferredHits.take(2).joinToString()}",
            category = AdjustmentCategory.DIET,
        )
    } else if (r.preferredHits.isNotEmpty()) {
        val label = if (lang == "en") profile.diet.labelEn else profile.diet.labelFr
        adjustments += PersonalAdjustment(
            points   = 3.0,
            reason   = if (lang == "en") "$label-friendly ingredients: ${r.preferredHits.take(2).joinToString()}"
                       else "Conforme $label : ${r.preferredHits.take(2).joinToString()}",
            category = AdjustmentCategory.DIET,
        )
    }
    return ConditionalAdjustments(adjustments, veto, dietReason)
}

// ===== HEALTH CONDITIONS =====
// Only the conditions with an established, simple nutrition-level rule get a
// scoring effect here (WHO sugar/salt guidance, kidney protein load, pregnancy
// alcohol veto, WCRF/NHS alcohol caution for cancer/depression, American
// Migraine Foundation/National Headache Foundation trigger-food guidance for
// chronic_migraine, and - below - Monash low-FODMAP/EU polyol-labeling/
// Crohn's & Colitis Foundation guidance for ibs/crohn_ibd/chronic_diarrhea).
// "thyroid_disorder", "food_allergies" and "intolerances" used to have the
// same no-op problem (selectable in ProfileSelectors.kt, zero downstream
// effect here) but were removed entirely instead of left as dead options -
// thyroid_disorder because hypo-/hyperthyroidism have opposite iodine-intake
// implications and this one flag can't distinguish which the user has (see
// ProfileSelectors.kt's own doc comment); food_allergies/intolerances
// because both concepts are already fully covered by the dedicated
// allergens selector, which checkUserAllergens() genuinely checks against
// every product.
internal fun checkHealthConditions(
    product: Product,
    profile: Profile,
    lang: String,
    // The base engine's own NegativeNutrientsPillar judges sat-fat/sugar against
    // category-relative thresholds (cheese tolerates far more sat fat than a
    // sandwich; a condiment far more sugar than a beverage - see
    // CategoryThresholds.kt) - but every condition/BMI check below this point
    // used to compare against one flat number regardless of category. That mix
    // produced exactly the "raw mozzarella flagged, boxed pasta meal not"
    // inconsistency a user reported: cheese is *always and unavoidably* higher
    // in saturated fat than the flat 5g/100g bar, so an overweight/obese
    // profile got that same amplified penalty on every single cheese scanned
    // regardless of whether it was unusually fatty for a cheese - while nothing
    // here ever looked at refined-carbohydrate/energy-density signals at all,
    // so a NOVA-4 boxed pasta/rice meal (low fat, low sugar, but refined starch
    // and calorie-dense even for a ready meal) sailed through untouched. Reusing
    // the same category-relative thresholds here (computed once, since diabetes/
    // age/BMI all need them) fixes the false positive; the new refined-carb/
    // energy-density check below fixes the false negative.
    catThresholds: CategoryThresholds,
    // Sugar-sweetened beverages (SSB) need their own, much lower bar - not the
    // ~15g/100g that would flag a solid snack. Matches checkVeto's own already-
    // established SSB definition exactly (BEVERAGE_SOFT, sugar>5g, protein<1g,
    // fiber<1g), so the veto and every condition/BMI check below agree on what
    // counts as one, instead of the veto silently using a different (and much
    // stricter) bar than diabetes/age/depression/BMI ever did.
    isSugarSweetenedBeverage: Boolean,
): ConditionalAdjustments {
    val conditions = profile.healthConditions
    val adjustments = mutableListOf<PersonalAdjustment>()
    var veto = false
    var dietReason: String? = null

    if ("diabetes" in conditions) {
        val sugars = product.nutrition.sugarsG
        if (isSugarSweetenedBeverage) {
            adjustments += PersonalAdjustment(
                points = -5.0,
                reason = if (lang == "en") "Sugar-sweetened beverage — liquid sugar causes a faster glycemic spike than the same amount in solid food, a particular concern for diabetes (WHO Sugars Intake Guideline 2015)"
                         else "Boisson sucrée — le sucre liquide provoque un pic glycémique plus rapide que la même quantité dans un aliment solide, une préoccupation particulière en cas de diabète (recommandation OMS sur les sucres, 2015)",
                category = AdjustmentCategory.CONDITION,
            )
        // .third/.first = the "major"/"minor" tiers of Quadruple(minor, moderate,
        // major, critical) - for the DEFAULT category these are exactly 15.0/5.0,
        // so this is behavior-identical to the old flat cutoff for any product
        // whose category has no override, and only loosens for categories (e.g.
        // condiments) whose own base-pillar thresholds are already higher.
        } else if (sugars >= catThresholds.sugarThresholds.third) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "High sugar (${sugars} g/100 g) — caution advised for diabetes"
                         else "Sucres élevés (${sugars} g/100 g) — prudence recommandée en cas de diabète",
                category = AdjustmentCategory.CONDITION,
            )
        } else if (sugars <= catThresholds.sugarThresholds.first) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Low sugar — diabetes-friendly"
                         else "Faible en sucres — adapté au diabète",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    if ("hypertension" in conditions) {
        val salt = product.nutrition.saltG
        // maxOf against the original flat 1.2g - never lowers the bar for any
        // category (so a random product still gets flagged exactly as before),
        // only raises it for CONDIMENT/PROCESSED_MEAT, whose own category norm
        // (CategoryThresholds.kt's saltThresholds) is structurally higher -
        // same "mozzarella flagged for sat fat, soda wasn't" category-blindness
        // this fixes, just for salt/hypertension instead of sat-fat/BMI. Without
        // this, every soy sauce and every cured ham triggered this exact same
        // caution regardless of being entirely typical for its category.
        val hypertensionSaltBar = maxOf(1.2, catThresholds.saltThresholds.first)
        if (salt >= hypertensionSaltBar) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "High salt (${salt} g/100 g) — caution advised for hypertension"
                         else "Sel élevé (${salt} g/100 g) — prudence recommandée en cas d'hypertension",
                category = AdjustmentCategory.CONDITION,
            )
        } else if (salt <= 0.3) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Low salt — hypertension-friendly"
                         else "Faible en sel — adapté à l'hypertension",
                category = AdjustmentCategory.CONDITION,
            )
        }
        // Previously no caffeine check existed for hypertension at all — a
        // caffeinated zero-sugar soda/energy drink (e.g. Monster Zero, Coca-Cola
        // Zero) passed every hypertension rule cleanly and read as fully "safe"
        // purely because caffeine was never modeled as data, not because it
        // genuinely poses no risk. Caffeine causes an acute BP rise (Mesas et al.,
        // meta-analysis, Am J Clin Nutr 2011); 20 mg/100g/mL is roughly typical
        // cola/energy-drink level and well below what a null (undeclared) value
        // would ever wrongly match, so this only fires on a real declared amount.
        product.nutrition.caffeineMg?.let { caffeine ->
            if (caffeine >= 20.0) {
                adjustments += PersonalAdjustment(
                    points = -2.0,
                    reason = if (lang == "en") "Contains caffeine (${caffeine.toInt()} mg/100 g) — can raise blood pressure acutely, caution advised for hypertension"
                             else "Contient de la caféine (${caffeine.toInt()} mg/100 g) — peut élever la tension artérielle de façon aiguë, prudence recommandée en cas d'hypertension",
                    category = AdjustmentCategory.CONDITION,
                )
            }
        }
    }
    // maxOf against the original flat 15.0g - never lowers the bar, only raises
    // it for FRESH_MEAT/FISH/CHEESE/PROCESSED_MEAT etc. whose own category norm
    // (CategoryThresholds.kt's proteinG "high" tier) is structurally higher.
    // Without this, every single fresh chicken breast or cod fillet (completely
    // ordinary protein for meat/fish, ~18-23g/100g) triggered the identical
    // kidney-disease caution as a genuine outlier like a protein bar spiked
    // to 3-4x its own category's norm - the warning couldn't distinguish
    // "ordinary meat" from "unusually protein-dense for what it is," exactly
    // the same category-blindness class as the mozzarella/sat-fat case.
    val kidneyProteinBar = maxOf(15.0, catThresholds.proteinG.third)
    if ("kidney_disease" in conditions && product.nutrition.proteinG >= kidneyProteinBar) {
        adjustments += PersonalAdjustment(
            points = -3.0,
            reason = if (lang == "en") "High protein (${product.nutrition.proteinG} g/100 g) — caution advised for kidney disease"
                     else "Protéines élevées (${product.nutrition.proteinG} g/100 g) — prudence recommandée en cas de maladie rénale",
            category = AdjustmentCategory.CONDITION,
        )
    }
    val alcoholHit = product.ingredients.any { ing -> ALCOHOL_INGREDIENT_PATTERN.containsMatchIn(ing.name) }
    if ("pregnancy" in conditions) {
        if (alcoholHit) {
            veto = true
            val reason = if (lang == "en") "Contains alcohol — avoid during pregnancy"
                         else "Contient de l'alcool — à éviter pendant la grossesse"
            dietReason = dietReason ?: reason
            adjustments += PersonalAdjustment(0.0, reason, AdjustmentCategory.CONDITION, veto = true)
        }
        // ANSES's 200mg/day pregnancy caffeine cap was already cited in the hint
        // panel (ProductHints.kt's containsCaffeineSource) but never actually
        // affected the *score* for a pregnant profile - a caffeinated soda or
        // coffee-flavored product scored identically to a decaf one here. Same
        // ingredient-name heuristic as the hint panel (kept duplicated rather
        // than shared, same rationale as this file's other small helpers).
        val containsCaffeineSource = product.ingredients.any { ing ->
            CAFFEINE_SOURCE_PATTERN.containsMatchIn(normalizeForMatching(ing.name))
        }
        if (containsCaffeineSource) {
            adjustments += PersonalAdjustment(
                points = -3.0,
                reason = if (lang == "en") "May contain caffeine — ANSES recommends pregnant women keep total daily caffeine intake under 200 mg"
                         else "Peut contenir de la caféine — l'ANSES recommande de limiter l'apport total en caféine à 200 mg/jour pendant la grossesse",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    // WCRF/AICR (World Cancer Research Fund) Cancer Prevention Recommendations:
    // alcohol intake is a well-established risk factor for several cancer types —
    // a caution, not a veto, since abstinence isn't universally medically required
    // the way it is in pregnancy.
    if ("cancer" in conditions && alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains alcohol — WCRF cancer prevention guidance recommends limiting alcohol intake"
                     else "Contient de l'alcool — les recommandations WCRF de prévention du cancer conseillent d'en limiter la consommation",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // NHS/CDC guidance: alcohol is a depressant that can worsen depressive symptoms
    // and interacts with most antidepressant classes (notably MAOIs and, to a
    // lesser extent, SSRIs) — a caution, not a veto.
    if ("depression" in conditions && alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains alcohol — can worsen depressive symptoms and interacts with most antidepressants"
                     else "Contient de l'alcool — peut aggraver les symptômes dépressifs et interagit avec la plupart des antidépresseurs",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // Knüppel et al., Scientific Reports 2017 (Whitehall II cohort): higher sweet
    // food/*beverage* sugar intake prospectively associated with incident common
    // mental disorder and depression in men over ~5 years follow-up - sweet
    // beverages are literally half of what that cohort measured, so the SSB
    // bar applies here too, not just the flat 15g solid-food bar.
    if ("depression" in conditions && (isSugarSweetenedBeverage || product.nutrition.sugarsG >= 15.0)) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "High sugar (${product.nutrition.sugarsG} g/100 g) — prospectively associated with depression risk (Knüppel et al., Whitehall II cohort, Sci Rep 2017)"
                     else "Sucres élevés (${product.nutrition.sugarsG} g/100 g) — associé de façon prospective au risque de dépression (Knüppel et al., cohorte Whitehall II, Sci Rep 2017)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // Adjibade et al., BMC Medicine 2019 (French NutriNet-Santé cohort): higher
    // ultra-processed food consumption prospectively associated with incident
    // depressive symptoms.
    if ("depression" in conditions && product.novaClass == NovaClass.ULTRA_PROCESSED) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Ultra-processed (NOVA 4) — prospectively associated with incident depressive symptoms (Adjibade et al., NutriNet-Santé cohort, BMC Medicine 2019)"
                     else "Ultra-transformé (NOVA 4) — associé de façon prospective à l'apparition de symptômes dépressifs (Adjibade et al., cohorte NutriNet-Santé, BMC Medicine 2019)",
            category = AdjustmentCategory.CONDITION,
        )
    }

    // Epilepsy Foundation guidance: alcohol lowers the seizure threshold and
    // interacts with most anti-epileptic drugs (reduced efficacy, increased
    // sedation) - a caution, not a veto, same framing as cancer/depression's
    // alcohol checks above.
    if ("epilepsy" in conditions && alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains alcohol — can lower seizure threshold and interacts with most anti-epileptic medications (Epilepsy Foundation)"
                     else "Contient de l'alcool — peut abaisser le seuil épileptogène et interagit avec la plupart des traitements antiépileptiques (Epilepsy Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }

    // Chronic migraine: alcohol (especially red wine), tyramine (aged cheese,
    // cured/fermented foods), nitrite/nitrate preservatives (cured/processed
    // meat) are the most consistently documented dietary migraine triggers
    // (American Migraine Foundation; National Headache Foundation's
    // low-tyramine diet). MSG and aspartame are commonly patient-reported
    // triggers per the same sources, weighted lower here since the underlying
    // evidence is more mixed than for the first three. Migraine triggers are
    // individualized - not everyone reacts to every item - so this is framed
    // as caution, never a veto.
    if ("chronic_migraine" in conditions) {
        val tyramineHit = product.ingredients.any { ing -> TYRAMINE_SOURCE_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
        val nitrateHit = product.ingredients.any { ing -> NITRATE_PRESERVATIVE_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
        val msgHit = product.ingredients.any { ing -> MSG_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
        val aspartameHit = product.ingredients.any { ing -> ASPARTAME_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
        if (alcoholHit) {
            adjustments += PersonalAdjustment(
                points = -3.0,
                reason = if (lang == "en") "Contains alcohol — one of the most consistently reported migraine triggers, especially red wine (American Migraine Foundation)"
                         else "Contient de l'alcool — l'un des déclencheurs de migraine les plus régulièrement rapportés, en particulier le vin rouge (American Migraine Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (tyramineHit) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "Contains an aged/fermented ingredient (cheese, cured meat...) — a tyramine source, a well-documented migraine trigger (National Headache Foundation low-tyramine diet)"
                         else "Contient un ingrédient affiné/fermenté (fromage, charcuterie...) — une source de tyramine, déclencheur de migraine bien documenté (régime pauvre en tyramine, National Headache Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (nitrateHit) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "Contains a nitrite/nitrate preservative — a documented migraine trigger in cured/processed meat (American Migraine Foundation)"
                         else "Contient un conservateur nitrite/nitrate — un déclencheur de migraine documenté dans la charcuterie/viande transformée (American Migraine Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (msgHit) {
            adjustments += PersonalAdjustment(
                points = -1.0,
                reason = if (lang == "en") "Contains MSG — reported as a migraine trigger by some patients (American Migraine Foundation)"
                         else "Contient du glutamate monosodique — rapporté comme déclencheur de migraine par certains patients (American Migraine Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (aspartameHit) {
            adjustments += PersonalAdjustment(
                points = -1.0,
                reason = if (lang == "en") "Contains aspartame — reported as a migraine trigger by some patients (American Migraine Foundation)"
                         else "Contient de l'aspartame — rapporté comme déclencheur de migraine par certains patients (American Migraine Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!alcoholHit && !tyramineHit && !nitrateHit && !msgHit && !aspartameHit) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "No common migraine trigger detected"
                         else "Aucun déclencheur de migraine habituel détecté",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }

    // IBS, Crohn's/IBD and chronic diarrhea all share a real, sourced trigger:
    // high-fat meals slow gastric emptying and provoke the gastrocolic reflex
    // (Monash University IBS patient guidance; Crohn's & Colitis Foundation;
    // NHS/Mayo Clinic diarrhea-diet advice). 17.5g/100g is the UK Food
    // Standards Agency's own public "high fat" traffic-light threshold, not a
    // guess - reused here across all three conditions rather than inventing a
    // condition-specific number with no comparable public sourcing.
    val highFat = product.nutrition.fatG >= 17.5
    val polyolHit = product.ingredients.any { ing -> POLYOL_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    if ("ibs" in conditions) {
        val fodmapHit = product.ingredients.any { ing -> HIGH_FODMAP_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
        if (fodmapHit) {
            adjustments += PersonalAdjustment(
                points = -3.0,
                reason = if (lang == "en") "Contains a high-FODMAP ingredient (onion/garlic/wheat/legumes) — a common IBS symptom trigger per Monash University's Low FODMAP research"
                         else "Contient un ingrédient riche en FODMAP (oignon/ail/blé/légumineuses) — un déclencheur fréquent de symptômes du SII selon les travaux de l'université Monash sur le régime pauvre en FODMAP",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (polyolHit) {
            adjustments += PersonalAdjustment(
                points = -3.0,
                reason = if (lang == "en") "Contains a sugar alcohol (sorbitol/mannitol/xylitol...) — poorly absorbed and osmotically active, a known IBS trigger (Monash Low FODMAP)"
                         else "Contient un polyol (sorbitol/mannitol/xylitol...) — mal absorbé et osmotiquement actif, un déclencheur connu du SII (régime pauvre en FODMAP, université Monash)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (highFat) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fat (${product.nutrition.fatG} g/100 g) — fatty meals can trigger IBS symptoms via the gastrocolic reflex"
                         else "Riche en matières grasses (${product.nutrition.fatG} g/100 g) — les repas gras peuvent déclencher des symptômes du SII via le réflexe gastro-colique",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!fodmapHit && !polyolHit && !highFat) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "No common high-FODMAP trigger detected — IBS-friendly"
                         else "Aucun déclencheur riche en FODMAP détecté — adapté au SII",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    // EU "high fibre" nutrition-claim threshold (Regulation (EC) 1924/2006:
    // ≥6g/100g) reused here, not invented - Crohn's & Colitis Foundation/NHS
    // both recommend a low-residue (low-fiber) diet during a flare, since
    // insoluble fiber is mechanically harder to pass through an inflamed gut.
    if ("crohn_ibd" in conditions) {
        val highFiber = product.nutrition.fiberG >= 6.0
        if (highFiber) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fiber (${product.nutrition.fiberG} g/100 g) — a low-residue diet is commonly advised during a Crohn's/IBD flare (Crohn's & Colitis Foundation, NHS)"
                         else "Riche en fibres (${product.nutrition.fiberG} g/100 g) — un régime pauvre en résidus est généralement conseillé lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation, NHS)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (highFat) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fat (${product.nutrition.fatG} g/100 g) — can worsen symptoms during a Crohn's/IBD flare (Crohn's & Colitis Foundation)"
                         else "Riche en matières grasses (${product.nutrition.fatG} g/100 g) — peut aggraver les symptômes lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (alcoholHit) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "Contains alcohol — commonly advised against during a Crohn's/IBD flare (Crohn's & Colitis Foundation)"
                         else "Contient de l'alcool — généralement déconseillé lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!highFiber && !highFat && !alcoholHit) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Low fiber and low fat — compatible with a low-residue diet"
                         else "Pauvre en fibres et en matières grasses — compatible avec un régime pauvre en résidus",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    // NHS/Mayo Clinic chronic-diarrhea dietary advice: limit sugar alcohols
    // (osmotic effect, the same EU-labeled mechanism as the IBS polyol check
    // above), caffeine and alcohol (both gut stimulants/motility accelerants),
    // and fatty/fried food.
    if ("chronic_diarrhea" in conditions) {
        if (polyolHit) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "Contains a sugar alcohol (sorbitol/mannitol/xylitol...) — osmotically draws water into the bowel and can worsen diarrhea (EU-mandated laxative-effect labeling above 10 g/100 g; NHS/Mayo Clinic diarrhea-diet guidance)"
                         else "Contient un polyol (sorbitol/mannitol/xylitol...) — attire l'eau dans l'intestin par effet osmotique et peut aggraver la diarrhée (étiquetage \"effet laxatif\" obligatoire en UE au-delà de 10 g/100 g ; recommandations NHS/Mayo Clinic)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        product.nutrition.caffeineMg?.let { caffeine ->
            if (caffeine >= 20.0) {
                adjustments += PersonalAdjustment(
                    points = -2.0,
                    reason = if (lang == "en") "Contains caffeine (${caffeine.toInt()} mg/100 g) — a gut stimulant that can worsen diarrhea (NHS diarrhea-diet guidance)"
                             else "Contient de la caféine (${caffeine.toInt()} mg/100 g) — un stimulant intestinal pouvant aggraver la diarrhée (recommandations NHS)",
                    category = AdjustmentCategory.CONDITION,
                )
            }
        }
        if (alcoholHit) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "Contains alcohol — can worsen diarrhea (NHS diarrhea-diet guidance)"
                         else "Contient de l'alcool — peut aggraver la diarrhée (recommandations NHS)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (highFat) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fat (${product.nutrition.fatG} g/100 g) — fatty/fried food can worsen diarrhea (NHS/Mayo Clinic diarrhea-diet guidance)"
                         else "Riche en matières grasses (${product.nutrition.fatG} g/100 g) — les aliments gras/frits peuvent aggraver la diarrhée (recommandations NHS/Mayo Clinic)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!polyolHit && !highFat && (product.nutrition.caffeineMg ?: 0.0) < 20.0 && !alcoholHit) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "No common diarrhea trigger detected"
                         else "Aucun déclencheur habituel de diarrhée détecté",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }

    return ConditionalAdjustments(adjustments, veto, dietReason)
}

// ============================================================================
// PUBLIC SURFACE — reusable "why this isn't ideal for you" badge for anywhere
// outside PersonalScoreEngine's own pipeline (Diary/Recipes/Grocery/Templates
// entry cards, the scan-result "better alternative" filter). Wraps
// checkHealthConditions() with the same category-relative thresholds and
// sugar-sweetened-beverage definition the real score uses, so a Diary entry's
// "not recommended" reason is never a second, drifted copy of the scoring
// logic - just the negative-points subset of the exact same computation,
// surfaced as short reasons instead of numeric adjustments.
// ============================================================================

/** Same sugar-sweetened-beverage definition checkVeto/checkHealthConditions use internally. */
private fun isSugarSweetenedBeverage(product: Product): Boolean =
    product.category == ProductCategory.BEVERAGE_SOFT &&
        product.nutrition.sugarsG > 5.0 && product.nutrition.proteinG < 1.0 && product.nutrition.fiberG < 1.0

/**
 * Short caution reasons from the user's declared health conditions for
 * [product] - empty if the user has none selected or none apply. Diet-key
 * compliance and allergens are intentionally NOT included here (Diary/Recipes/
 * Grocery/Templates already surface those separately via checkDiet()/
 * checkUserAllergens() - see e.g. DiaryViewModel.diaryWarnings) - this is
 * purely the healthConditions-driven half.
 */
fun healthConditionCautions(product: Product, healthConditions: Set<String>, lang: String = "fr"): List<String> {
    if (healthConditions.isEmpty()) return emptyList()
    val result = checkHealthConditions(
        product           = product,
        profile           = Profile(healthConditions = healthConditions),
        lang              = lang,
        catThresholds     = getThresholds(product.category),
        isSugarSweetenedBeverage = isSugarSweetenedBeverage(product),
    )
    return result.adjustments.filter { it.points < 0.0 }.map { it.reason }
}
