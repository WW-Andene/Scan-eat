package fr.scanneat.shared

import fr.scanneat.model.*

// ============================================================================
// DOMAIN → DTO MAPPING
// One-way: domain types (pure Kotlin) → serializable API response types
// ============================================================================

fun NutritionPer100g.toDto() = NutritionDto(
    energyKcal    = energyKcal,
    fatG          = fatG,
    saturatedFatG = saturatedFatG,
    carbsG        = carbsG,
    sugarsG       = sugarsG,
    addedSugarsG  = addedSugarsG,
    fiberG        = fiberG,
    proteinG      = proteinG,
    saltG         = saltG,
    transFatG     = transFatG,
    ironMg        = ironMg,
    calciumMg     = calciumMg,
    magnesiumMg   = magnesiumMg,
    potassiumMg   = potassiumMg,
    zincMg        = zincMg,
    sodiumMg      = sodiumMg,
    vitAUg        = vitAUg,
    vitCMg        = vitCMg,
    vitDUg        = vitDUg,
    vitEMg        = vitEMg,
    vitKUg        = vitKUg,
    b1Mg          = b1Mg,
    b2Mg          = b2Mg,
    b3Mg          = b3Mg,
    b6Mg          = b6Mg,
    b9Ug          = b9Ug,
    b12Ug         = b12Ug,
    omega3G       = omega3G,
    omega6G       = omega6G,
    cholesterolMg = cholesterolMg,
    polyunsaturatedFatG = polyunsaturatedFatG,
    monounsaturatedFatG = monounsaturatedFatG,
)

fun Ingredient.toDto() = IngredientDto(
    name        = name,
    percentage  = percentage,
    eNumber     = eNumber,
    category    = category?.name?.lowercase(),
    isWholeFood = isWholeFood,
)

/**
 * The ~15 fields ProductDto and IdentifiedFoodResponse both carry, computed once
 * here instead of separately in toDto() and toIdentifiedDto() below - a new
 * Product field previously had to be added to both mapping functions in lockstep,
 * with nothing catching it if one was missed.
 */
private class CommonProductFields(p: Product) {
    val name          = p.name
    val category      = p.category.key
    val novaClass     = p.novaClass.value
    val ingredients   = p.ingredients.map { it.toDto() }
    val nutrition     = p.nutrition.toDto()
    val organic       = p.organic
    val wholeGrainPrimary = p.wholeGrainPrimary
    val fermented     = p.fermented
    val hasHealthClaims = p.hasHealthClaims
    val hasMisleadingMarketing = p.hasMisleadingMarketing
    val namedOils     = p.namedOils
    val origin        = p.origin
    val weightG       = p.weightG
    val ecoscoreGrade = p.ecoscoreGrade
    val ecoscoreValue = p.ecoscoreValue
    val nutriscoreGrade = p.nutriscoreGrade
    val declaredMicronutrients = p.declaredMicronutrients
    val declaredAllergenTags = p.declaredAllergenTags
}

fun Product.toDto(): ProductDto = CommonProductFields(this).let { f ->
    ProductDto(
        name          = f.name,
        category      = f.category,
        novaClass     = f.novaClass,
        ingredients   = f.ingredients,
        nutrition     = f.nutrition,
        organic       = f.organic,
        wholeGrainPrimary = f.wholeGrainPrimary,
        fermented     = f.fermented,
        hasHealthClaims = f.hasHealthClaims,
        hasMisleadingMarketing = f.hasMisleadingMarketing,
        namedOils     = f.namedOils,
        origin        = f.origin,
        weightG       = f.weightG,
        ecoscoreGrade = f.ecoscoreGrade,
        ecoscoreValue = f.ecoscoreValue,
        nutriscoreGrade = f.nutriscoreGrade,
        declaredMicronutrients = f.declaredMicronutrients,
        declaredAllergenTags = f.declaredAllergenTags,
    )
}

/** Same field set as Product.toDto(), plus the identify-only warnings list - see IdentifyRoute.kt. */
fun Product.toIdentifiedDto(warnings: List<String> = emptyList()): IdentifiedFoodResponse = CommonProductFields(this).let { f ->
    IdentifiedFoodResponse(
        name          = f.name,
        category      = f.category,
        novaClass     = f.novaClass,
        ingredients   = f.ingredients,
        nutrition     = f.nutrition,
        organic       = f.organic,
        wholeGrainPrimary = f.wholeGrainPrimary,
        fermented     = f.fermented,
        hasHealthClaims = f.hasHealthClaims,
        hasMisleadingMarketing = f.hasMisleadingMarketing,
        namedOils     = f.namedOils,
        origin        = f.origin,
        weightG       = f.weightG,
        ecoscoreGrade = f.ecoscoreGrade,
        ecoscoreValue = f.ecoscoreValue,
        nutriscoreGrade = f.nutriscoreGrade,
        declaredMicronutrients = f.declaredMicronutrients,
        declaredAllergenTags = f.declaredAllergenTags,
        warnings      = warnings,
    )
}

fun Deduction.toDto() = DeductionDto(
    pillar   = pillar,
    reason   = reason,
    points   = points,
    severity = severity.name.lowercase(),
    evidence = evidence,
)

fun PillarScore.toDto() = PillarDto(
    name       = name,
    max        = max,
    score      = score,
    deductions = deductions.map { it.toDto() },
    bonuses    = bonuses.map { it.toDto() },
)

fun VetoCondition.toDto() = VetoDto(triggered = triggered, reason = reason, cap = cap)

fun ScoreAudit.toDto() = AuditDto(
    productName   = productName,
    category      = category.key,
    score         = score,
    grade         = grade.label,
    verdict       = verdict,
    pillars       = PillarsDto(
        processing         = pillars.processing.toDto(),
        nutritionalDensity = pillars.nutritionalDensity.toDto(),
        negativeNutrients  = pillars.negativeNutrients.toDto(),
        additiveRisk       = pillars.additiveRisk.toDto(),
        ingredientIntegrity = pillars.ingredientIntegrity.toDto(),
    ),
    globalBonuses   = globalBonuses.map { it.toDto() },
    globalPenalties = globalPenalties.map { it.toDto() },
    veto            = veto.toDto(),
    redFlags        = redFlags,
    greenFlags      = greenFlags,
    engineVersion   = engineVersion,
    warnings        = warnings,
    nutriscoreGrade = nutriscoreGrade,
)
