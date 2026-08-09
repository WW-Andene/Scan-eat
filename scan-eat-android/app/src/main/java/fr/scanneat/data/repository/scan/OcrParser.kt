package fr.scanneat.data.repository.scan

import com.squareup.moshi.Moshi
import fr.scanneat.data.remote.api.CerebrasApi
import fr.scanneat.data.remote.api.ContentPart
import fr.scanneat.data.remote.api.GroqApi
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.remote.api.ImageUrl
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// OCR PARSER — port of src/ocr-parser.ts
//
// Vision LLM (Groq Llama 4 Scout) reads packaging image → draft Product
// → deterministic post-processor re-validates percentages, E-numbers,
// whole-food flags, generic oils, hidden sugars.
//
// Lives in data/ (not domain/) — it performs network I/O against GroqApi,
// which is data-layer behavior; the domain layer must stay pure.
//
// This file now holds only the main class and its two public entry points
// (parseLabel/identifyFood). The other concerns that used to live in this
// single file have moved to sibling files in this same package: prompt
// building (OcrPrompts.kt), the LLM response DTOs (OcrDto.kt), DTO→domain
// mapping + warnings (OcrMapper.kt), and the multi-provider call/retry/
// fallback logic (OcrRetry.kt). Pure structural split — no behavior changed.
// ============================================================================

const val DEFAULT_MODEL   = "meta-llama/llama-4-scout-17b-16e-instruct"
const val FALLBACK_MODEL  = "llama-3.3-70b-versatile"

data class ParseLabelResult(
    val product: Product,
    val warnings: List<String>,
    val barcode: String? = null,
)

// ============================================================================
// OcrParser class — injected into use-cases
// ============================================================================

class OcrParser(
    internal val groqApi: GroqApi,
    internal val cerebrasApi: CerebrasApi,
    moshi: Moshi,
) {

    private val dtoAdapter = moshi.adapter(LlmProductDto::class.java)

    /**
     * Parse one or more images of a food label.
     *
     * Previously exposed a user-facing model-picker in Settings — but Groq
     * model names get retired/renamed periodically, and asking a non-technical
     * user to pick a working one from a list was just moving an outage onto
     * them. This now tries a fixed, ordered list of models across two
     * providers (Groq, then Cerebras as a free-tier fallback) automatically —
     * a provider whose key is blank is simply skipped, not an error.
     */
    suspend fun parseLabel(
        images: List<ImagePayload>,
        groqApiKey: String,
        cerebrasApiKey: String = "",
        lang: String = "fr",
    ): ParseLabelResult {
        val content = buildContentParts(images, buildLabelPrompt(lang))
        val raw = callWithRetry(groqApiKey, cerebrasApiKey, content)
        val json = extractJson(raw)
        val dto = runCatching { dtoAdapter.fromJson(json) }.getOrNull()
            ?: return ParseLabelResult(
                product  = Product(name = "(parse error)", category = ProductCategory.OTHER,
                    novaClass = NovaClass.ULTRA_PROCESSED, ingredients = emptyList(),
                    nutrition = NutritionPer100g.EMPTY),
                warnings = listOf(unparseableJsonMessage(lang)),
            )

        val product  = mapLlmToProduct(dto)
        val warnings = buildWarnings(product, dto, lang)
        return ParseLabelResult(product = product, warnings = warnings, barcode = dto.barcode)
    }

    /**
     * Identify a food from a photo (no label) — used for fresh foods / plated dishes.
     */
    suspend fun identifyFood(
        images: List<ImagePayload>,
        groqApiKey: String,
        cerebrasApiKey: String = "",
        lang: String = "fr",
    ): ParseLabelResult {
        val prompt = buildIdentifyFoodPrompt(lang)
        val content = buildContentParts(images, prompt)
        val raw     = callWithRetry(groqApiKey, cerebrasApiKey, content)
        val json    = extractJson(raw)
        val dto     = runCatching { dtoAdapter.fromJson(json) }.getOrNull() ?: return ParseLabelResult(
            product  = Product("(identification failed)", ProductCategory.OTHER,
                NovaClass.ULTRA_PROCESSED, emptyList(), NutritionPer100g.EMPTY),
            warnings = listOf(unparseableIdentificationMessage(lang)),
        )
        return ParseLabelResult(product = mapLlmToProduct(dto), warnings = listOf(aiEstimatedMessage(lang)))
    }

    // ----

    private fun buildContentParts(images: List<ImagePayload>, text: String): List<ContentPart> =
        images.map { img ->
            ContentPart(type = "image_url", imageUrl = ImageUrl("data:${img.mime};base64,${img.base64}"))
        } + ContentPart(type = "text", text = text)
}
