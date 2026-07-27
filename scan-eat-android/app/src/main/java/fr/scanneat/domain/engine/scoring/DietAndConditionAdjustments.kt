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
// alcohol veto, WCRF/NHS alcohol caution for cancer/depression). "thyroid_disorder"
// and "digestive_disorders" are still selectable in ProfileSelectors.kt
// (Profile.healthConditions is free-form) but have no dedicated nutrition-
// threshold rule reliable enough to code here yet, and are NOT surfaced
// anywhere else either (see HealthConditionGuidanceDb.kt's own header for why
// digestive_disorders specifically is too heterogeneous a bucket to map to
// ingredients) - a user selecting either still gets no product-specific
// guidance, identically to not having selected it. "food_allergies" and
// "intolerances" used to have the same no-op problem but were removed
// entirely from ProfileSelectors.kt instead of left as dead options - both
// concepts are already fully covered by the dedicated allergens selector,
// which checkUserAllergens() genuinely checks against every product.
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

    return ConditionalAdjustments(adjustments, veto, dietReason)
}
