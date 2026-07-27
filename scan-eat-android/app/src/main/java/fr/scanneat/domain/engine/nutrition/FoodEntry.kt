package fr.scanneat.domain.engine.nutrition

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
    // Per-100g, approximate CIQUAL-style values like the macros above — only
    // set for foods that are a genuine, well-known source of that nutrient
    // (the "Close the gap" suggestion engine needs real density values to
    // suggest anything for iron/calcium/vitD/B12; 0.0 elsewhere means "not
    // a notable source", same convention as fiberG/saltG defaulting to 0.0).
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double = 0.0,
    val b12Ug: Double = 0.0,
    val aliases: List<String> = emptyList(),
)
