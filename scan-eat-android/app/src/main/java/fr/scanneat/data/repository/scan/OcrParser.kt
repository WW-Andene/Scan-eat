package fr.scanneat.data.repository.scan

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.model.*

// ============================================================================
// OCR PARSER — port of src/ocr-parser.ts
//
// Vision LLM (Groq Llama 4 Scout) reads packaging image → draft Product
// → deterministic post-processor re-validates percentages, E-numbers,
// whole-food flags, generic oils, hidden sugars.
//
// Lives in data/ (not domain/) — it performs network I/O against GroqApi,
// which is data-layer behavior; the domain layer must stay pure.
// ============================================================================

const val DEFAULT_MODEL   = "meta-llama/llama-4-scout-17b-16e-instruct"
const val FALLBACK_MODEL  = "llama-3.3-70b-versatile"

data class ParseLabelResult(
    val product: Product,
    val warnings: List<String>,
    val barcode: String? = null,
)

// ============================================================================
// Label parsing prompt
// ============================================================================

private fun buildLabelPrompt(lang: String = "fr"): String = """
You are a food label OCR and structuring assistant.

Read the food packaging image and extract the following as a raw JSON object (no markdown, no preamble):

{
  "name": "<product name>",
  "category": "<one of: sandwich|ready_meal|bread|breakfast_cereal|yogurt|cheese|processed_meat|fresh_meat|fish|snack_sweet|snack_salty|beverage_soft|beverage_juice|beverage_water|condiment|oil_fat|other>",
  "nova_class": <1|2|3|4>,
  "ingredients": [
    { "name": "<ingredient name>", "percentage": <number or null>, "e_number": "<Exxx or null>", "category": "<food|additive|processing_aid or null>", "is_whole_food": <true|false|null> }
  ],
  "nutrition": {
    "energy_kcal": <number>,
    "fat_g": <number>,
    "saturated_fat_g": <number>,
    "carbs_g": <number>,
    "sugars_g": <number>,
    "added_sugars_g": <number or null>,
    "fiber_g": <number>,
    "protein_g": <number>,
    "salt_g": <number>,
    "trans_fat_g": <number or null>,
    "iron_mg": <number or null>,
    "calcium_mg": <number or null>,
    "magnesium_mg": <number or null>,
    "potassium_mg": <number or null>,
    "zinc_mg": <number or null>,
    "vit_a_ug": <number or null>,
    "vit_c_mg": <number or null>,
    "vit_d_ug": <number or null>,
    "vit_e_mg": <number or null>,
    "vit_k_ug": <number or null>,
    "b12_ug": <number or null>
  },
  "organic": <true|false>,
  "whole_grain_primary": <true|false>,
  "fermented": <true|false>,
  "has_health_claims": <true|false>,
  "has_misleading_marketing": <true|false>,
  "named_oils": <true|false|null>,
  "origin": "<country of origin or null>",
  "weight_g": <number or null>,
  "barcode": "<EAN/UPC string if visible or null>"
}

Rules:
- All nutrition values are per 100g.
- For beverages, values are per 100ml.
- Use null for any value you cannot read — never guess nutrient values.
- For nova_class: 1=unprocessed, 2=culinary, 3=processed, 4=ultra-processed.
- Preserve E-numbers exactly as printed (E250, E471, etc.).
- Language of the label: ${lang}.
- Output ONLY the JSON object. No explanation, no markdown, no backticks.
""".trimIndent()

// ============================================================================
// LLM raw-JSON DTO (matches prompt schema above)
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
)

// ============================================================================
// Mapping LLM DTO → domain Product
// ============================================================================

private fun coerceDouble(v: Any?): Double = when (v) {
    is Number -> v.toDouble().coerceAtLeast(0.0)
    is String -> v.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    else -> 0.0
}

private fun mapLlmToProduct(dto: LlmProductDto): Product {
    val n = dto.nutrition
    return Product(
        name      = dto.name?.trim() ?: "(produit sans nom)",
        category  = ProductCategory.fromKey(dto.category ?: "other"),
        novaClass = NovaClass.fromInt(dto.nova_class ?: 4),
        ingredients = dto.ingredients?.mapNotNull { ing ->
            val name = ing.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Ingredient(
                name        = name,
                percentage  = ing.percentage,
                eNumber     = ing.e_number?.uppercase()?.takeIf { Regex("^E\\d{3}").containsMatchIn(it) },
                category    = when (ing.category?.lowercase()) {
                    "additive"      -> IngredientCategory.ADDITIVE
                    "processing_aid" -> IngredientCategory.PROCESSING_AID
                    else            -> IngredientCategory.FOOD
                },
                isWholeFood = ing.is_whole_food,
            )
        } ?: emptyList(),
        nutrition = NutritionPer100g(
            energyKcal    = coerceDouble(n?.energy_kcal),
            fatG          = coerceDouble(n?.fat_g),
            saturatedFatG = coerceDouble(n?.saturated_fat_g),
            carbsG        = coerceDouble(n?.carbs_g),
            sugarsG       = coerceDouble(n?.sugars_g),
            addedSugarsG  = n?.added_sugars_g,
            fiberG        = coerceDouble(n?.fiber_g),
            proteinG      = coerceDouble(n?.protein_g),
            saltG         = coerceDouble(n?.salt_g),
            transFatG     = n?.trans_fat_g,
            ironMg        = n?.iron_mg,
            calciumMg     = n?.calcium_mg,
            magnesiumMg   = n?.magnesium_mg,
            potassiumMg   = n?.potassium_mg,
            zincMg        = n?.zinc_mg,
            vitAUg        = n?.vit_a_ug,
            vitCMg        = n?.vit_c_mg,
            vitDUg        = n?.vit_d_ug,
            vitEMg        = n?.vit_e_mg,
            vitKUg        = n?.vit_k_ug,
            b12Ug         = n?.b12_ug,
        ),
        organic               = dto.organic ?: false,
        wholeGrainPrimary     = dto.whole_grain_primary ?: false,
        fermented             = dto.fermented ?: false,
        hasHealthClaims       = dto.has_health_claims ?: false,
        hasMisleadingMarketing = dto.has_misleading_marketing ?: false,
        namedOils             = dto.named_oils,
        origin                = dto.origin?.takeIf { it.isNotBlank() },
        weightG               = dto.weight_g,
    )
}

// ============================================================================
// JSON extraction helper (strips markdown fences if the model ignores rules)
// ============================================================================

private fun extractJson(raw: String): String {
    val stripped = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = stripped.indexOf('{')
    val end   = stripped.lastIndexOf('}')
    return if (start >= 0 && end > start) stripped.substring(start, end + 1) else stripped
}

// ============================================================================
// OcrParser class — injected into use-cases
// ============================================================================

class OcrParser(private val groqApi: GroqApi) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dtoAdapter = moshi.adapter(LlmProductDto::class.java)

    /**
     * Parse one or more images of a food label.
     * Calls Groq with vision model; falls back to text-only model on 429/5xx.
     */
    suspend fun parseLabel(
        images: List<ImagePayload>,
        apiKey: String,
        model: String = DEFAULT_MODEL,
        lang: String = "fr",
    ): ParseLabelResult {
        val content = buildContentParts(images, buildLabelPrompt(lang))
        val raw = callWithRetry(apiKey, model, content)
        val json = extractJson(raw)
        val dto = runCatching { dtoAdapter.fromJson(json) }.getOrNull()
            ?: return ParseLabelResult(
                product  = Product(name = "(parse error)", category = ProductCategory.OTHER,
                    novaClass = NovaClass.ULTRA_PROCESSED, ingredients = emptyList(),
                    nutrition = NutritionPer100g.EMPTY),
                warnings = listOf("LLM returned unparseable JSON"),
            )

        val product  = mapLlmToProduct(dto)
        val warnings = buildWarnings(product, dto)
        return ParseLabelResult(product = product, warnings = warnings, barcode = dto.barcode)
    }

    /**
     * Identify a food from a photo (no label) — used for fresh foods / plated dishes.
     */
    suspend fun identifyFood(
        images: List<ImagePayload>,
        apiKey: String,
        model: String = DEFAULT_MODEL,
    ): ParseLabelResult {
        val prompt = """
Identify the food in this image. Return a JSON object with the same schema as a food label:
{
  "name": "<food name in French>",
  "category": "<category key>",
  "nova_class": <1-4>,
  "ingredients": [],
  "nutrition": { "energy_kcal": <estimate>, "fat_g": 0, "saturated_fat_g": 0, "carbs_g": 0, "sugars_g": 0, "fiber_g": 0, "protein_g": 0, "salt_g": 0 },
  "organic": false, "whole_grain_primary": false, "fermented": false,
  "has_health_claims": false, "has_misleading_marketing": false
}
Output ONLY the JSON. No explanation.
        """.trimIndent()
        val content = buildContentParts(images, prompt)
        val raw     = callWithRetry(apiKey, model, content)
        val json    = extractJson(raw)
        val dto     = runCatching { dtoAdapter.fromJson(json) }.getOrNull() ?: return ParseLabelResult(
            product  = Product("(identification failed)", ProductCategory.OTHER,
                NovaClass.ULTRA_PROCESSED, emptyList(), NutritionPer100g.EMPTY),
            warnings = listOf("LLM identification returned unparseable JSON"),
        )
        return ParseLabelResult(product = mapLlmToProduct(dto), warnings = listOf("Nutrition estimated by AI — not from label"))
    }

    // ----

    private fun buildContentParts(images: List<ImagePayload>, text: String): List<ContentPart> =
        images.map { img ->
            ContentPart(type = "image_url", imageUrl = ImageUrl("data:${img.mime};base64,${img.base64}"))
        } + ContentPart(type = "text", text = text)

    private suspend fun callWithRetry(
        apiKey: String,
        primaryModel: String,
        content: List<ContentPart>,
        maxRetries: Int = 3,
    ): String {
        var lastErr: Throwable? = null
        repeat(maxRetries) { attempt ->
            val model = if (attempt == maxRetries - 1) FALLBACK_MODEL else primaryModel
            runCatching {
                val resp = groqApi.chatCompletions(
                    auth    = "Bearer $apiKey",
                    request = ChatRequest(
                        model    = model,
                        messages = listOf(ChatMessage(role = "user", content = content)),
                    ),
                )
                return resp.choices.firstOrNull()?.message?.content ?: ""
            }.onFailure { lastErr = it }
        }
        throw lastErr ?: RuntimeException("All LLM retries exhausted")
    }

    private fun buildWarnings(product: Product, dto: LlmProductDto): List<String> {
        val w = mutableListOf<String>()
        if (product.nutrition.energyKcal == 0.0 && product.nutrition.proteinG == 0.0)
            w += "Nutrition values could not be read from image"
        if (product.ingredients.isEmpty())
            w += "Ingredients list could not be parsed"
        if (dto.barcode != null && !dto.barcode.matches(Regex("\\d{8,14}")))
            w += "Barcode '${dto.barcode}' found on label may be incorrect"
        return w
    }
}
