package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.ProductCategory

/**
 * Most barcode products on Open Food Facts (and plenty of vision-LLM label
 * reads) simply never declare iron/calcium/magnesium/potassium/zinc/vitC/vitD/
 * B12 at all — OFF's own nutriment coverage for these fields sits well under
 * 50% even for well-populated categories. Before this, a null field collapsed
 * straight to 0.0 the moment it reached DiaryEntry.consumed (see that file),
 * so a user who scanned and logged real beef (fresh_meat, a genuine iron
 * source) saw their daily iron intake stay at exactly 0mg regardless of how
 * many such products they logged — MicronutrientCard/GapCloserCard then acted
 * on that silent zero as if it were measured fact.
 *
 * This fills each still-null field with a representative per-100g estimate for
 * the product's own [ProductCategory], grounded in typical USDA FoodData
 * Central / CIQUAL category averages (blended across common items in that
 * category, not one specific food) — a realistic ballpark, not a fabricated
 * precise value, which is why [NutritionPer100g.micronutrientsEstimated] gets
 * set whenever any field here is actually used, so the UI can flag it as an
 * estimate rather than present it as declared fact. A field the source DID
 * declare (including an explicit 0.0, which is different from null - "not
 * declared") is never touched.
 */
private data class MicronutrientDefaults(
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val zincMg: Double = 0.0,
    val vitCMg: Double = 0.0,
    val vitDUg: Double = 0.0,
    val b12Ug: Double = 0.0,
)

private val CATEGORY_DEFAULTS: Map<ProductCategory, MicronutrientDefaults> = mapOf(
    // Mixed bread + filling (meat/cheese/veg) - blended toward the bread half.
    ProductCategory.SANDWICH to MicronutrientDefaults(
        ironMg = 1.5, calciumMg = 80.0, magnesiumMg = 20.0, potassiumMg = 200.0,
        zincMg = 1.2, vitCMg = 2.0, vitDUg = 0.1, b12Ug = 0.4,
    ),
    // Frozen/chilled composite meals - grain+protein+veg blend.
    ProductCategory.READY_MEAL to MicronutrientDefaults(
        ironMg = 1.2, calciumMg = 60.0, magnesiumMg = 18.0, potassiumMg = 250.0,
        zincMg = 1.0, vitCMg = 5.0, vitDUg = 0.2, b12Ug = 0.5,
    ),
    // Mostly water + vegetables/stock - low across the board.
    ProductCategory.SOUP to MicronutrientDefaults(
        ironMg = 0.5, calciumMg = 20.0, magnesiumMg = 10.0, potassiumMg = 180.0,
        zincMg = 0.3, vitCMg = 3.0,
    ),
    // Wheat flour is iron/B-vitamin fortified in most markets this app targets.
    ProductCategory.BREAD to MicronutrientDefaults(
        ironMg = 2.5, calciumMg = 40.0, magnesiumMg = 25.0, potassiumMg = 120.0, zincMg = 1.0,
    ),
    // Commercial breakfast cereals are the single most heavily fortified
    // category in most markets (often 25-100% RDA per serving stamped on-pack).
    ProductCategory.BREAKFAST_CEREAL to MicronutrientDefaults(
        ironMg = 8.0, calciumMg = 100.0, magnesiumMg = 40.0, potassiumMg = 200.0,
        zincMg = 3.0, vitCMg = 10.0, vitDUg = 2.0, b12Ug = 1.0,
    ),
    ProductCategory.YOGURT to MicronutrientDefaults(
        ironMg = 0.1, calciumMg = 150.0, magnesiumMg = 12.0, potassiumMg = 155.0,
        zincMg = 0.6, vitDUg = 0.05, b12Ug = 0.4,
    ),
    // Hard/semi-hard cheese average (calcium is the category's whole point).
    ProductCategory.CHEESE to MicronutrientDefaults(
        ironMg = 0.3, calciumMg = 600.0, magnesiumMg = 25.0, potassiumMg = 100.0,
        zincMg = 3.0, vitDUg = 0.3, b12Ug = 1.0,
    ),
    // Charcuterie/cured meats - less iron-dense than fresh meat once cured/diluted.
    ProductCategory.PROCESSED_MEAT to MicronutrientDefaults(
        ironMg = 1.0, calciumMg = 15.0, magnesiumMg = 15.0, potassiumMg = 250.0,
        zincMg = 2.0, vitDUg = 0.3, b12Ug = 1.5,
    ),
    // Blended red/white/poultry average - the exact case from the bug report
    // (a plain beef scan previously logging exactly 0mg iron all day).
    ProductCategory.FRESH_MEAT to MicronutrientDefaults(
        ironMg = 2.0, calciumMg = 10.0, magnesiumMg = 22.0, potassiumMg = 350.0,
        zincMg = 4.0, vitDUg = 0.2, b12Ug = 2.5,
    ),
    // Deliberately conservative vitD (varies hugely lean-white vs oily fish) -
    // under-estimating a nutrient is the safer failure mode than over-crediting it.
    ProductCategory.FISH to MicronutrientDefaults(
        ironMg = 0.5, calciumMg = 15.0, magnesiumMg = 28.0, potassiumMg = 380.0,
        zincMg = 0.5, vitDUg = 8.0, b12Ug = 3.0,
    ),
    ProductCategory.SNACK_SWEET to MicronutrientDefaults(
        ironMg = 1.0, calciumMg = 40.0, magnesiumMg = 20.0, potassiumMg = 150.0, zincMg = 0.5,
    ),
    // Potato-based salty snacks carry real potassium from the potato itself.
    ProductCategory.SNACK_SALTY to MicronutrientDefaults(
        ironMg = 1.0, calciumMg = 20.0, magnesiumMg = 30.0, potassiumMg = 300.0,
        zincMg = 0.5, vitCMg = 5.0,
    ),
    // Carbonated soft drinks: genuinely near-zero, not an estimation gap - left
    // at MicronutrientDefaults() (all-zero) deliberately, not omitted from this
    // map, so a soft drink doesn't fall through to OTHER's non-zero fallback.
    ProductCategory.BEVERAGE_SOFT to MicronutrientDefaults(),
    ProductCategory.BEVERAGE_JUICE to MicronutrientDefaults(
        ironMg = 0.2, calciumMg = 10.0, magnesiumMg = 8.0, potassiumMg = 150.0,
        zincMg = 0.1, vitCMg = 25.0,
    ),
    // Plain/mineral water: real product-to-product mineral variation exists,
    // but with no way to distinguish "genuinely zero" from "not analysed" at
    // this category's granularity, zero is the honest default rather than a
    // guessed non-zero mineral count.
    ProductCategory.BEVERAGE_WATER to MicronutrientDefaults(),
    // Small typical portion size (5-15g) already limits real-world impact.
    ProductCategory.CONDIMENT to MicronutrientDefaults(
        ironMg = 0.5, calciumMg = 20.0, magnesiumMg = 10.0, potassiumMg = 100.0,
        zincMg = 0.2, vitCMg = 2.0,
    ),
    // Oils/fats/butter - vitD only where dairy-fat-based; otherwise negligible.
    ProductCategory.OIL_FAT to MicronutrientDefaults(vitDUg = 0.3),
    // Generic "mixed diet" fallback for anything OffCategoryMapping couldn't
    // classify - still meaningfully better than a hard 0 for every field, at
    // the cost of being the least category-specific estimate in this table.
    ProductCategory.OTHER to MicronutrientDefaults(
        ironMg = 1.0, calciumMg = 40.0, magnesiumMg = 15.0, potassiumMg = 150.0,
        zincMg = 0.8, vitCMg = 3.0, vitDUg = 0.1, b12Ug = 0.3,
    ),
)

/**
 * Fills only the fields the source left null (never declared), leaving an
 * explicit declared 0.0 alone, and sets [NutritionPer100g.micronutrientsEstimated]
 * only when at least one field actually needed filling.
 */
fun NutritionPer100g.withEstimatedMicronutrients(category: ProductCategory): NutritionPer100g {
    val d = CATEGORY_DEFAULTS[category] ?: return this
    val anyMissing = ironMg == null || calciumMg == null || magnesiumMg == null ||
        potassiumMg == null || zincMg == null || vitCMg == null || vitDUg == null || b12Ug == null
    if (!anyMissing) return this
    return copy(
        ironMg      = ironMg      ?: d.ironMg,
        calciumMg   = calciumMg   ?: d.calciumMg,
        magnesiumMg = magnesiumMg ?: d.magnesiumMg,
        potassiumMg = potassiumMg ?: d.potassiumMg,
        zincMg      = zincMg      ?: d.zincMg,
        vitCMg      = vitCMg      ?: d.vitCMg,
        vitDUg      = vitDUg      ?: d.vitDUg,
        b12Ug       = b12Ug       ?: d.b12Ug,
        micronutrientsEstimated = true,
    )
}
