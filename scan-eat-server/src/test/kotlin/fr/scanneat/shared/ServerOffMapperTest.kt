package fr.scanneat.shared

import fr.scanneat.service.OffProductRaw
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

// ============================================================================
// SERVER OFF MAPPER TESTS
//
// Covers mapOffProduct()'s unit-conversion math: OFF reports several fields in
// units other than what the domain model stores (kJ vs kcal energy, grams vs
// milligrams/micrograms for minerals/vitamins, sodium->salt, and the quantity
// string's mixed cl/dl/l/kg units), each a distinct place a silent factor-of-N
// error could slip in undetected.
// ============================================================================

class ServerOffMapperTest {

    private fun nutriments(vararg pairs: Pair<String, Double>) = buildJsonObject {
        pairs.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
    }

    @Test
    fun `energy falls back from kJ to kcal when energy-kcal_100g is absent`() {
        // 418.4 kJ / 4.184 == 100 kcal
        val raw = OffProductRaw(productName = "Test", nutriments = nutriments("energy_100g" to 418.4))
        val product = mapOffProduct(raw)
        assertNotNull(product)
        assertEquals(100.0, product.nutrition.energyKcal, 0.01)
    }

    @Test
    fun `energy-kcal_100g is used directly when present, ignoring energy_100g`() {
        val raw = OffProductRaw(
            productName = "Test",
            nutriments = nutriments("energy-kcal_100g" to 250.0, "energy_100g" to 9999.0),
        )
        val product = mapOffProduct(raw)
        assertEquals(250.0, product?.nutrition?.energyKcal)
    }

    @Test
    fun `mineral values in grams are converted to milligrams`() {
        val raw = OffProductRaw(
            productName = "Test",
            nutriments = nutriments(
                "iron_100g"      to 0.002, // -> 2 mg
                "calcium_100g"   to 0.12,  // -> 120 mg
                "potassium_100g" to 0.3,   // -> 300 mg
            ),
        )
        val product = mapOffProduct(raw)
        assertNotNull(product)
        assertEquals(2.0, product.nutrition.ironMg)
        assertEquals(120.0, product.nutrition.calciumMg)
        assertEquals(300.0, product.nutrition.potassiumMg)
    }

    @Test
    fun `vitamin A in grams converts to micrograms (x1,000,000), unlike vitamin C which converts to milligrams`() {
        val raw = OffProductRaw(
            productName = "Test",
            nutriments = nutriments(
                "vitamin-a_100g" to 0.0000008, // 0.0000008 g -> 0.8 ug
                "vitamin-c_100g" to 0.00006,   // 0.00006 g -> 0.06 mg (not ug - different unit than vitamin A)
            ),
        )
        val product = mapOffProduct(raw)
        assertNotNull(product)
        assertEquals(0.8, product.nutrition.vitAUg!!, 1e-9)
        assertEquals(0.06, product.nutrition.vitCMg!!, 1e-9)
    }

    @Test
    fun `salt falls back from sodium using the NaCl conversion factor when salt_100g is absent`() {
        // 0.4 g sodium * 2.5 == 1.0 g salt
        val raw = OffProductRaw(productName = "Test", nutriments = nutriments("sodium_100g" to 0.4))
        val product = mapOffProduct(raw)
        assertEquals(1.0, product?.nutrition?.saltG)
    }

    @Test
    fun `salt_100g is used directly when present, ignoring the sodium fallback`() {
        val raw = OffProductRaw(
            productName = "Test",
            nutriments = nutriments("salt_100g" to 1.5, "sodium_100g" to 9.0),
        )
        val product = mapOffProduct(raw)
        assertEquals(1.5, product?.nutrition?.saltG)
    }

    @Test
    fun `a product with no usable name is rejected as not found`() {
        val raw = OffProductRaw(productName = null, productNameFr = null, genericNameFr = null)
        assertNull(mapOffProduct(raw))
    }

    // ---- quantity string -> weightG (parseWeightG) ------------------------

    @Test
    fun `quantity in centiliters converts to grams via the cl-to-ml equivalence`() {
        val raw = OffProductRaw(productName = "Soda", quantity = "33 cl")
        val product = mapOffProduct(raw)
        assertEquals(330.0, product?.weightG)
    }

    @Test
    fun `quantity in deciliters converts to grams`() {
        val raw = OffProductRaw(productName = "Milk", quantity = "2 dl")
        val product = mapOffProduct(raw)
        assertEquals(200.0, product?.weightG)
    }

    @Test
    fun `quantity in kilograms converts to grams`() {
        val raw = OffProductRaw(productName = "Rice", quantity = "1.5 kg")
        val product = mapOffProduct(raw)
        assertEquals(1500.0, product?.weightG)
    }

    @Test
    fun `quantity in liters converts to grams using the same factor as kilograms`() {
        val raw = OffProductRaw(productName = "Water", quantity = "1 l")
        val product = mapOffProduct(raw)
        assertEquals(1000.0, product?.weightG)
    }

    // ---- classifyNonFood() ---------------------------------------------------
    // A lubricant/cosmetic/tobacco/etc. barcode running through the same
    // nutrition-based food scoring as a real "other" food product produces a
    // meaningless grade - this is the live (per-scan) counterpart to
    // NonConsumableLookupDb's curated, point-in-time OPF CSV snapshot.

    @Test
    fun `personal lubricant tags classify as PERSONAL_CARE`() {
        assertEquals("PERSONAL_CARE", classifyNonFood(listOf("en:sex-toys", "en:personal-lubricants")))
    }

    @Test
    fun `cosmetic and hygiene tags classify as PERSONAL_CARE`() {
        assertEquals("PERSONAL_CARE", classifyNonFood(listOf("en:cosmetics", "en:beauty")))
        assertEquals("PERSONAL_CARE", classifyNonFood(listOf("en:hygiene-products")))
    }

    @Test
    fun `feminine and baby hygiene tags classify as HYGIENE_PRODUCT, not PERSONAL_CARE`() {
        assertEquals("HYGIENE_PRODUCT", classifyNonFood(listOf("en:feminine-hygiene-products")))
        assertEquals("HYGIENE_PRODUCT", classifyNonFood(listOf("en:diapers")))
    }

    @Test
    fun `tobacco, pet-food, battery and cleaning tags classify to their own categories`() {
        assertEquals("TOBACCO", classifyNonFood(listOf("en:cigarettes")))
        assertEquals("PET_SUPPLY", classifyNonFood(listOf("en:dog-food")))
        assertEquals("BATTERY", classifyNonFood(listOf("en:batteries")))
        assertEquals("BLEACH", classifyNonFood(listOf("en:bleaches")))
        assertEquals("LAUNDRY", classifyNonFood(listOf("en:laundry-detergents")))
        assertEquals("CLEANING_PRODUCT", classifyNonFood(listOf("en:cleaning-products")))
    }

    @Test
    fun `real food and beverage tags never classify as non-food`() {
        assertNull(classifyNonFood(listOf("en:beverages", "en:sodas")))
        assertNull(classifyNonFood(listOf("en:dairy", "en:yogurts")))
        assertNull(classifyNonFood(listOf("en:dietary-supplements")))
    }

    @Test
    fun `a tag that also indicates real food wins over a non-food match`() {
        // A magnesium supplement filed under OPF's Health & Beauty tree is still
        // meant to be swallowed - see NonConsumableLookupDb's own header for why
        // this exact conservative rule exists on the curated-CSV side too.
        assertNull(classifyNonFood(listOf("en:beauty", "en:dietary-supplements")))
    }

    @Test
    fun `null or empty tags never classify as non-food`() {
        assertNull(classifyNonFood(null))
        assertNull(classifyNonFood(emptyList()))
    }

    @Test
    fun `name-brand fallback catches a lubricant OFF tagged only in a non-English taxonomy`() {
        // Reproduces a live miss: "Durex Perfect Gliss Glijmiddel" scored as a
        // regular NOVA-4 food product because OFF's own categories_tags for it
        // didn't contain any of the English substrings above (e.g. localized as
        // "nl:glijmiddelen"/"fr:lubrifiants" instead of "en:lubricants").
        assertEquals(
            "PERSONAL_CARE",
            classifyNonFood(emptyList(), productName = "Perfect Gliss Glijmiddel", brand = "Durex"),
        )
        assertEquals(
            "PERSONAL_CARE",
            classifyNonFood(null, productName = "Gel lubrifiant intime", brand = null),
        )
    }

    @Test
    fun `name-brand fallback only runs when the tag pass found nothing`() {
        // A tag-based non-food match still wins even if productName/brand were
        // also supplied - the fallback is last-resort, not a second vote.
        assertEquals(
            "TOBACCO",
            classifyNonFood(listOf("en:cigarettes"), productName = "Some Cigarettes", brand = "Whatever"),
        )
    }

    @Test
    fun `a name that merely contains an unrelated word never false-positives`() {
        assertNull(classifyNonFood(listOf("en:beverages"), productName = "Orange Juice", brand = "Tropicana"))
    }
}
