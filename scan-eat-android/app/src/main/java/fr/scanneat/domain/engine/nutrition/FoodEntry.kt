package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// FOOD DATABASE — model
// Split out of FoodDb.kt: the FoodEntry shape shared by every FOOD_DB
// category file and by the search/reconciliation helpers.
// ============================================================================

data class FoodEntry(
    val name: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double = 0.0,
    val saltG: Double = 0.0,
    // User-reported: FoodEntry had no saturated-fat/sugars fields at all, so
    // both toProduct() converters (this file's own extension fun and
    // CustomFoodRepository.toProduct()) hardcoded 0.0 for these two macros
    // regardless of the real food - a FOOD_DB/custom-food entry for butter or
    // honey reported 0g saturated fat / 0g sugar even though those are exactly
    // the foods where these two macros matter most. Same "field simply didn't
    // exist" class of gap as vitCMg's own doc comment below, just for
    // NutritionPer100g's two other non-optional macro fields instead of a
    // micronutrient.
    val saturatedFatG: Double = 0.0,
    val sugarsG: Double = 0.0,
    // Per-100g, approximate CIQUAL-style values like the macros above — only
    // set for foods that are a genuine, well-known source of that nutrient
    // (the "Close the gap" suggestion engine needs real density values to
    // suggest anything for iron/calcium/vitD/B12; 0.0 elsewhere means "not
    // a notable source", same convention as fiberG/saltG defaulting to 0.0).
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double = 0.0,
    val b12Ug: Double = 0.0,
    // User-reported: logging a well-known vitamin C source (orange) from FOOD_DB
    // contributed nothing to the dashboard's vitamin C total - this field simply
    // didn't exist here, unlike iron/calcium/vitD/B12 above, so there was no way
    // for even the most obvious source (citrus) to carry a value through
    // CustomFoodRepository.toProduct() into NutritionPer100g.vitCMg.
    val vitCMg: Double = 0.0,
    // Same gap as vitCMg above, for the rest of MicronutrientCard's rows
    // (dashboard/cards/MicronutrientCard.kt) that FoodEntry still had no field
    // for at all: magnesium/potassium/zinc/vitaminA/folate are all displayed
    // there with real NRV targets, so a FOOD_DB food that's a genuine source of
    // one of these previously had no way to contribute to it either.
    val magnesiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val zincMg: Double = 0.0,
    val vitAUg: Double = 0.0,
    val b9Ug: Double = 0.0,
    // Not shown on MicronutrientCard itself, but still real NutritionPer100g
    // fields consumed elsewhere (ProductHintsBenefitsRisks' benefit lines,
    // NutritionalDensityPillar) - covered for the same reason: a FOOD_DB food
    // that's a genuine source had no way to declare it.
    val vitEMg: Double = 0.0,
    val vitKUg: Double = 0.0,
    val b6Mg: Double = 0.0,
    // User-requested: let the portion picker (LogSheet.kt) offer a "≈180g"
    // one-tap preset for a whole/typical serving (one apple, one egg, a
    // handful of almonds) the same way it already does for a scanned
    // product's package weight (Product.weightG) - FOOD_DB/custom foods had
    // no equivalent, so every quick-add food had to be weighed and typed in
    // manually even for foods with an obvious typical portion. Null (not 0.0,
    // unlike every other field here) for foods with no single natural
    // portion (rice, oil, flour) - LogSheet only shows the chip when this is
    // in the same 10-2000g sane range Product.weightG's own chip already
    // requires.
    val typicalPortionG: Double? = null,
    val aliases: List<String> = emptyList(),
    // User-requested: "combler les écarts" suggestions should avoid repeating
    // a food category already logged today (see DashboardGapAnalysis.kt's
    // eatenCategories filter). ProductCategory is tailored to packaged/OFF
    // products, so some raw staples genuinely have no clean bucket (milk,
    // coffee/tea, legumes) - those stay null, treated as "no category" by
    // the filter (never excluded, never excludes another suggestion), rather
    // than forcing an inaccurate classification.
    val category: ProductCategory? = null,
)
