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
    vitAUg        = vitAUg,
    vitCMg        = vitCMg,
    vitDUg        = vitDUg,
    vitEMg        = vitEMg,
    b12Ug         = b12Ug,
    omega3G       = omega3G,
)

fun Ingredient.toDto() = IngredientDto(
    name        = name,
    percentage  = percentage,
    eNumber     = eNumber,
    category    = category?.name?.lowercase(),
    isWholeFood = isWholeFood,
)

fun Product.toDto() = ProductDto(
    name          = name,
    category      = category.key,
    novaClass     = novaClass.value,
    ingredients   = ingredients.map { it.toDto() },
    nutrition     = nutrition.toDto(),
    organic       = organic,
    wholeGrainPrimary = wholeGrainPrimary,
    fermented     = fermented,
    hasHealthClaims = hasHealthClaims,
    hasMisleadingMarketing = hasMisleadingMarketing,
    namedOils     = namedOils,
    origin        = origin,
    weightG       = weightG,
    ecoscoreGrade = ecoscoreGrade,
    ecoscoreValue = ecoscoreValue,
    nutriscoreGrade = nutriscoreGrade,
)

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
