package fr.scanneat.service

import fr.scanneat.model.ImageDto
import fr.scanneat.model.ScoreResponse
import fr.scanneat.shared.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ScoreService")

/**
 * Outcome of the barcode-scoring path. OffMiss means "no OFF match, caller
 * should fall through to the image-only path" — kept separate from Success
 * rather than nullable so the route can't accidentally treat a miss as an
 * error. Ktor-agnostic on purpose: no ApplicationCall / HttpStatusCode here,
 * so this orchestration can be exercised without a route/call.
 */
sealed class BarcodeScoreOutcome {
    data class Success(val response: ScoreResponse) : BarcodeScoreOutcome()
    object OffMiss : BarcodeScoreOutcome()
}

/**
 * The hybrid-scoring workflow previously inlined in ScoreRoute.kt's post("/score")
 * handler: barcode -> OFF -> optional LLM augment (if sparse) -> score, or a
 * straight image-only LLM path. Extracted so the route is just request-parse +
 * response-write (+ the two call.resolveGroqKey/requireGroqKey side effects,
 * which stay in the route since they respond directly on the ApplicationCall),
 * matching the thinner routes elsewhere (FetchRecipeRoute, IdentifyRoute).
 */
class ScoreService(
    private val offService: OffService,
    private val groqService: GroqService,
) {
    /**
     * [resolveGroqKey] is only used for the *optional* sparse-OFF-record LLM
     * augmentation — a missing key there just skips augmentation, it never
     * needs to fail the request, so it's a plain nullable-returning lambda
     * rather than something that can itself respond an error.
     */
    suspend fun scoreBarcode(
        barcode: String,
        images: List<ImageDto>,
        resolveGroqKey: suspend () -> String?,
        lang: String,
        model: String,
    ): BarcodeScoreOutcome {
        val offRaw = offService.fetchProduct(barcode)
        val offProduct = offRaw?.let { mapOffProduct(it) } ?: return BarcodeScoreOutcome.OffMiss

        // Sparse + have images -> augment with LLM. A key that's absent (no
        // augmentation attempted) and a key that's present but rejected by Groq
        // both used to fall back to OFF-only identically - a user who
        // deliberately supplied their own key had no way to learn it didn't
        // work, unlike the image-only path below which surfaces auth failures.
        var augmentWarning: String? = null
        if (isOffSparse(offProduct) && images.isNotEmpty()) {
            val key = resolveGroqKey()
            if (key != null) {
                val augmented = runCatching {
                    val parsed = groqService.parseLabel(images, key, lang, model)
                    val merged = mergeOffWithLlm(offProduct, parsed.product)
                    val conflicts = detectSourceConflicts(offProduct, parsed.product)
                    val audit = scoreProduct(merged, lang)
                    BarcodeScoreOutcome.Success(
                        ScoreResponse(
                            product  = merged.toDto(),
                            audit    = audit.toDto(),
                            warnings = parsed.warnings + conflicts.map { "Conflict: ${it.field} OFF=${it.offValue} LLM=${it.llmValue}" },
                            source   = "merged",
                            barcode  = barcode,
                        ),
                    )
                }.onFailure { e ->
                    log.warn("LLM augmentation failed, falling back to OFF-only: ${e.message}")
                    augmentWarning = "AI augmentation failed (invalid API key or model) - showing Open Food Facts data only"
                }.getOrNull()
                if (augmented != null) return augmented
            }
        }

        // OFF-only
        val audit = scoreProduct(offProduct, lang)
        return BarcodeScoreOutcome.Success(
            ScoreResponse(
                product  = offProduct.toDto(),
                audit    = audit.toDto(),
                warnings = listOfNotNull(augmentWarning),
                source   = "openfoodfacts",
                barcode  = barcode,
            ),
        )
    }

    /** Image-only path — caller must already have a required (non-null) Groq key. */
    suspend fun scoreFromImages(images: List<ImageDto>, groqKey: String, lang: String, model: String): ScoreResponse {
        val parsed = groqService.parseLabel(images, groqKey, lang, model)
        val audit = scoreProduct(parsed.product, lang)
        return ScoreResponse(
            product  = parsed.product.toDto(),
            audit    = audit.toDto(),
            warnings = parsed.warnings,
            source   = "llm",
            barcode  = parsed.barcode,
        )
    }
}
