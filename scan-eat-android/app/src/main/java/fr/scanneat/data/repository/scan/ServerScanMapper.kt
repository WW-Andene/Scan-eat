package fr.scanneat.data.repository.scan

import fr.scanneat.data.remote.api.ServerIdentifyResponse
import fr.scanneat.data.remote.api.ServerIngredientDto
import fr.scanneat.data.remote.api.ServerNutritionDto
import fr.scanneat.data.remote.api.ServerScoreResponse
import fr.scanneat.domain.engine.scoring.scoreProduct
import fr.scanneat.domain.model.*

// Server (ApiMode.SERVER) response -> domain mapping, extracted from ScanRepository:
// the actual scan-orchestration responsibility (barcode/image lookup, retry, persistence)
// lives there, while this file is purely wire-DTO-to-domain translation - same split
// already used server-side between OffService.kt and ServerOffMapper.kt.

fun ServerNutritionDto.toDomain() = NutritionPer100g(
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
    vitCMg        = vitCMg,
    vitDUg        = vitDUg,
    vitAUg        = vitAUg,
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

fun List<ServerIngredientDto>.toDomainIngredients() = map { i ->
    Ingredient(name = i.name, percentage = i.percentage, eNumber = i.eNumber,
        category = when (i.category?.lowercase()) {
            "additive"       -> IngredientCategory.ADDITIVE
            "processing_aid" -> IngredientCategory.PROCESSING_AID
            else             -> IngredientCategory.FOOD
        })
}

fun ServerScoreResponse.toDomain(lang: String = "fr"): ScanResult {
    val p = product
    val product = Product(
        name          = p.name,
        category      = ProductCategory.fromKey(p.category),
        novaClass     = NovaClass.fromInt(p.novaClass),
        ingredients   = p.ingredients.toDomainIngredients(),
        nutrition     = p.nutrition.toDomain(),
        organic       = p.organic, wholeGrainPrimary = p.wholeGrainPrimary,
        fermented     = p.fermented, hasHealthClaims = p.hasHealthClaims,
        hasMisleadingMarketing = p.hasMisleadingMarketing,
        namedOils     = p.namedOils, origin = p.origin, weightG = p.weightG,
        ecoscoreGrade = p.ecoscoreGrade, ecoscoreValue = p.ecoscoreValue,
        nutriscoreGrade = p.nutriscoreGrade,
        // Previously dropped here even after the server started sending them -
        // this reconstruction is the only place a server-mode Product is ever
        // built, so omitting them silently disabled AllergenDetector's OFF-tag
        // augmentation and the iron-declared SEX bonus for every server-mode scan.
        declaredMicronutrients = p.declaredMicronutrients,
        declaredAllergenTags = p.declaredAllergenTags,
    )
    // Trust the locally recomputed audit wholesale — flags are derived from
    // the same pillars the UI renders, so overlaying the server's flags here
    // risked showing red/green flags that don't match the deductions next to
    // them if the two engines ever disagree.
    val fullAudit = scoreProduct(product, lang)
    return ScanResult(product = product, audit = fullAudit, warnings = warnings,
        source = when (source) {
            "openfoodfacts" -> ScanSource.OPEN_FOOD_FACTS
            "merged"        -> ScanSource.MERGED
            else            -> ScanSource.LLM
        }, barcode = barcode)
}

/** Same recompute-locally approach as ServerScoreResponse.toDomain() - see ScanRepository.identifyViaServer(). */
fun ServerIdentifyResponse.toDomain(lang: String = "fr"): ScanResult {
    val product = Product(
        name        = name,
        category    = ProductCategory.fromKey(category),
        novaClass   = NovaClass.fromInt(novaClass),
        ingredients = ingredients.toDomainIngredients(),
        nutrition   = nutrition.toDomain(),
        organic       = organic, wholeGrainPrimary = wholeGrainPrimary,
        fermented     = fermented, hasHealthClaims = hasHealthClaims,
        hasMisleadingMarketing = hasMisleadingMarketing,
        namedOils     = namedOils, origin = origin, weightG = weightG,
        ecoscoreGrade = ecoscoreGrade, ecoscoreValue = ecoscoreValue,
        nutriscoreGrade = nutriscoreGrade,
        declaredMicronutrients = declaredMicronutrients,
        declaredAllergenTags = declaredAllergenTags,
    )
    return ScanResult(product = product, audit = scoreProduct(product, lang), warnings = warnings, source = ScanSource.LLM)
}
