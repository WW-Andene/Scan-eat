package fr.scanneat.domain.engine.menu

import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.detectAllergens
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Profile

/** One menu line and what its own words reveal for the profile (Roadmap N3). */
data class MenuDish(
    val text: String,
    /** Declared allergens of the profile named in the dish text (labels). */
    val allergens: List<String>,
    /** Diet conflict reason, if the dish text names a non-compliant ingredient. */
    val dietConflict: String?,
)

private val ZERO = NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0)
private val PRICE_ONLY = Regex("""^[\s\d.,€$£xX/-]*$""")

/**
 * Splits OCR'd menu text into dish lines and checks each one's words with
 * the same allergen detector and diet checker as a scanned product. Only
 * what is WRITTEN can be detected - a dish without a match is "nothing
 * found in the text", never "safe" (the screen says so).
 */
fun analyzeMenuText(text: String, profile: Profile, lang: String = "fr"): List<MenuDish> =
    text.lines().map { it.trim() }
        .filter { it.count(Char::isLetter) >= 4 && !PRICE_ONLY.matches(it) }
        .distinct()
        .map { line ->
            val parts = line.split(Regex("""[,;:•·/()]|\s+(?:et|and|with|avec|au|aux|à la|à l'|sauce)\s+""", RegexOption.IGNORE_CASE))
                .map { it.trim() }.filter { it.isNotEmpty() }
            val product = Product(name = line, category = ProductCategory.OTHER, novaClass = NovaClass.PROCESSED,
                ingredients = (parts + line).map { Ingredient(name = it) }, nutrition = ZERO)
            val allergens = if (profile.allergens.isEmpty()) emptyList() else
                detectAllergens(product, lang).filter { !it.isTraceOnly && it.key in profile.allergens }
                    .map { if (lang == "en") it.labelEn else it.labelFr }
            // Only explicitly named forbidden ingredients count: a dish can never
            // carry a certification label, so "not certified" is not a conflict.
            val violations = if (profile.diet == DietKey.NONE) emptyList() else checkDiet(product, profile.diet, lang).violations.distinct()
            MenuDish(line, allergens, violations.takeIf { it.isNotEmpty() }?.joinToString(", "))
        }
