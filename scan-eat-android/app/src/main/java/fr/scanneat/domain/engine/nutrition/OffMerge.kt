package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// OFF MERGE — split out of OffMapper.kt: merges an OFF-sourced Product with an
// LLM-extracted one, and flags cross-source disagreements.
// ============================================================================

/**
 * Merge OFF record with LLM extraction.
 * OFF is the trusted baseline; LLM fills empty / zero fields.
 * Port of mergeOFFWithLLM from off.ts.
 */
fun mergeOffWithLlm(off: Product, llm: Product): Product {
    fun <T> prefer(offVal: T, llmVal: T, isEmpty: (T) -> Boolean): T =
        if (isEmpty(offVal)) llmVal else offVal

    val emptyStr: (String) -> Boolean = { it.isBlank() }
    val emptyNum: (Double) -> Boolean = { it == 0.0 }

    fun mergeNutrition(o: NutritionPer100g, l: NutritionPer100g) = NutritionPer100g(
        energyKcal    = prefer(o.energyKcal,    l.energyKcal,    emptyNum),
        fatG          = prefer(o.fatG,          l.fatG,          emptyNum),
        saturatedFatG = prefer(o.saturatedFatG, l.saturatedFatG, emptyNum),
        carbsG        = prefer(o.carbsG,        l.carbsG,        emptyNum),
        sugarsG       = prefer(o.sugarsG,       l.sugarsG,       emptyNum),
        addedSugarsG  = o.addedSugarsG  ?: l.addedSugarsG,
        fiberG        = prefer(o.fiberG,        l.fiberG,        emptyNum),
        proteinG      = prefer(o.proteinG,      l.proteinG,      emptyNum),
        saltG         = prefer(o.saltG,         l.saltG,         emptyNum),
        transFatG     = o.transFatG     ?: l.transFatG,
        ironMg        = o.ironMg        ?: l.ironMg,
        calciumMg     = o.calciumMg     ?: l.calciumMg,
        magnesiumMg   = o.magnesiumMg   ?: l.magnesiumMg,
        potassiumMg   = o.potassiumMg   ?: l.potassiumMg,
        zincMg        = o.zincMg        ?: l.zincMg,
        sodiumMg      = o.sodiumMg      ?: l.sodiumMg,
        vitAUg        = o.vitAUg        ?: l.vitAUg,
        vitCMg        = o.vitCMg        ?: l.vitCMg,
        vitDUg        = o.vitDUg        ?: l.vitDUg,
        vitEMg        = o.vitEMg        ?: l.vitEMg,
        vitKUg        = o.vitKUg        ?: l.vitKUg,
        b12Ug         = o.b12Ug         ?: l.b12Ug,
        // b1Mg/b2Mg/b3Mg were missing here even though NutritionPer100g carries all
        // three and OffMapper.kt already maps them from OFF - any product that also
        // went through LLM merge (isOffSparse true for some unrelated field, e.g.
        // missing category) silently dropped OFF's own B1/B2/B3 values back to null,
        // losing the benefit/risk hints ProductHintsBenefitsRisks.kt derives from them.
        b1Mg          = o.b1Mg          ?: l.b1Mg,
        b2Mg          = o.b2Mg          ?: l.b2Mg,
        b3Mg          = o.b3Mg          ?: l.b3Mg,
        b6Mg          = o.b6Mg          ?: l.b6Mg,
        b9Ug          = o.b9Ug          ?: l.b9Ug,
        omega3G       = o.omega3G       ?: l.omega3G,
        omega6G       = o.omega6G       ?: l.omega6G,
        cholesterolMg = o.cholesterolMg ?: l.cholesterolMg,
        caffeineMg    = o.caffeineMg    ?: l.caffeineMg,
        polyunsaturatedFatG = o.polyunsaturatedFatG ?: l.polyunsaturatedFatG,
        monounsaturatedFatG = o.monounsaturatedFatG ?: l.monounsaturatedFatG,
    )

    return Product(
        name        = prefer(off.name, llm.name, emptyStr),
        category    = if (off.category != ProductCategory.OTHER) off.category else llm.category,
        novaClass   = if (off.novaClass.value > 0) off.novaClass else llm.novaClass,
        // Threshold matches isOffSparse's own documented rule above: a genuine
        // 1-2 ingredient product (water, salt, single-origin oil) is real,
        // correct OFF data, not a gap to paper over with an LLM guess. This
        // used to require >= 3 ingredients to count as "present", so merge
        // (triggered by isOffSparse being true for some unrelated field, e.g.
        // missing category) silently threw away a short-but-correct OFF
        // ingredient list and substituted the LLM's guess instead.
        ingredients = prefer(off.ingredients, llm.ingredients) { it.isEmpty() },
        nutrition   = mergeNutrition(off.nutrition, llm.nutrition),
        weightG             = off.weightG     ?: llm.weightG,
        origin              = off.origin      ?: llm.origin,
        organic             = off.organic     || llm.organic,
        hasHealthClaims     = off.hasHealthClaims || llm.hasHealthClaims,
        hasMisleadingMarketing = off.hasMisleadingMarketing || llm.hasMisleadingMarketing,
        namedOils           = off.namedOils   ?: llm.namedOils,
        originTransparent   = off.originTransparent || llm.originTransparent,
        wholeGrainPrimary   = off.wholeGrainPrimary || llm.wholeGrainPrimary,
        fermented           = off.fermented || llm.fermented,
        declaredMicronutrients = (off.declaredMicronutrients + llm.declaredMicronutrients).distinct(),
        ecoscoreGrade   = off.ecoscoreGrade,
        ecoscoreValue   = off.ecoscoreValue,
        nutriscoreGrade = off.nutriscoreGrade,
        // Previously kept only off.declaredAllergenTags, discarding the LLM's own -
        // LlmLabelParser.mapToProduct() reads the packaging's printed allergen box
        // (allergen_declarations) into declaredAllergenTags just like OFF's allergens_tags,
        // but OFF frequently has none even for a well-populated record, silently losing
        // a real, LLM-read allergen declaration on merge. Mirrors declaredMicronutrients'
        // union just above.
        declaredAllergenTags = (off.declaredAllergenTags + llm.declaredAllergenTags).distinct(),
    )
}

data class SourceConflict(val field: String, val offValue: String, val llmValue: String)

fun detectSourceConflicts(off: Product, llm: Product): List<SourceConflict> {
    val conflicts = mutableListOf<SourceConflict>()
    fun check(field: String, o: Double, l: Double) {
        if (o > 0 && l > 0 && kotlin.math.abs(o - l) / maxOf(o, l) > 0.3)
            conflicts += SourceConflict(field, "${o}g", "${l}g")
    }
    check("protein_g", off.nutrition.proteinG, llm.nutrition.proteinG)
    check("fat_g",     off.nutrition.fatG,     llm.nutrition.fatG)
    check("carbs_g",   off.nutrition.carbsG,   llm.nutrition.carbsG)
    check("sugars_g",  off.nutrition.sugarsG,  llm.nutrition.sugarsG)
    return conflicts
}
