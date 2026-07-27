package fr.scanneat.data.repository.scan

// ============================================================================
// LLM raw-JSON DTOs (match the JSON schema built in OcrPrompts.kt)
//
// Split out of OcrParser.kt (pure structural move, no behavior change).
// ============================================================================

data class LlmProductDto(
    val name: String? = null,
    val category: String? = null,
    val nova_class: Int? = null,
    val ingredients: List<LlmIngredientDto>? = null,
    val nutrition: LlmNutritionDto? = null,
    val organic: Boolean? = null,
    val whole_grain_primary: Boolean? = null,
    val fermented: Boolean? = null,
    val has_health_claims: Boolean? = null,
    val has_misleading_marketing: Boolean? = null,
    val named_oils: Boolean? = null,
    val origin: String? = null,
    val weight_g: Double? = null,
    val barcode: String? = null,
    val allergen_declarations: List<String>? = null,
)

data class LlmIngredientDto(
    val name: String? = null,
    val percentage: Double? = null,
    val e_number: String? = null,
    val category: String? = null,
    val is_whole_food: Boolean? = null,
)

data class LlmNutritionDto(
    val energy_kcal: Double? = null,
    val fat_g: Double? = null,
    val saturated_fat_g: Double? = null,
    val carbs_g: Double? = null,
    val sugars_g: Double? = null,
    val added_sugars_g: Double? = null,
    val fiber_g: Double? = null,
    val protein_g: Double? = null,
    val salt_g: Double? = null,
    val trans_fat_g: Double? = null,
    val iron_mg: Double? = null,
    val calcium_mg: Double? = null,
    val magnesium_mg: Double? = null,
    val potassium_mg: Double? = null,
    val zinc_mg: Double? = null,
    val vit_a_ug: Double? = null,
    val vit_c_mg: Double? = null,
    val vit_d_ug: Double? = null,
    val vit_e_mg: Double? = null,
    val vit_k_ug: Double? = null,
    val b12_ug: Double? = null,
    val caffeine_mg: Double? = null,
)
