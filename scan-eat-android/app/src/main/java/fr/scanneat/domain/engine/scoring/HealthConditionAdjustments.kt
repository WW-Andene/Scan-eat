package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

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
//
// Each condition family below lives in its own sibling file
// (HealthConditionMetabolicAdjustments.kt, HealthConditionSystemicAdjustments.kt,
// HealthConditionMigraineAdjustments.kt, HealthConditionGiAdjustments.kt) -
// this function just computes the couple of shared signals (alcoholHit,
// isSugarSweetenedBeverage's caller-supplied value) and dispatches to them.
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

    checkMetabolicConditions(product, conditions, lang, catThresholds, isSugarSweetenedBeverage, adjustments)

    val alcoholHit = product.ingredients.any { ing -> ALCOHOL_INGREDIENT_PATTERN.containsMatchIn(ing.name) }

    checkPregnancyCondition(product, conditions, lang, alcoholHit, adjustments)?.let { (v, reason) ->
        veto = v
        dietReason = dietReason ?: reason
    }
    checkCancerDepressionEpilepsyConditions(product, conditions, lang, alcoholHit, isSugarSweetenedBeverage, adjustments)
    checkMigraineCondition(product, conditions, lang, alcoholHit, adjustments)
    checkGastrointestinalConditions(product, conditions, lang, alcoholHit, adjustments)

    return ConditionalAdjustments(adjustments, veto, dietReason)
}
