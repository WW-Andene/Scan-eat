package fr.scanneat.data.repository.scan

import fr.scanneat.data.remote.api.OffResponse
import fr.scanneat.data.remote.api.OpenFoodFactsApi
import fr.scanneat.domain.engine.nutrition.OffProductResponse
import fr.scanneat.domain.engine.nutrition.classifyNonFood
import fr.scanneat.domain.engine.nutrition.detectSourceConflicts
import fr.scanneat.domain.engine.nutrition.isOffSparse
import fr.scanneat.domain.engine.nutrition.mapOffProduct
import fr.scanneat.domain.engine.nutrition.mergeOffWithLlm
import fr.scanneat.domain.engine.nutrition.withEstimatedMicronutrients
import fr.scanneat.domain.engine.scoring.scoreProduct
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.domain.model.ScanSource
import fr.scanneat.util.barcodeCandidates
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/**
 * "Product not found in Open Food Facts — add a photo to continue" - kept next
 * to scoreDirectBarcode since that's this file's only caller now that it's
 * been split out of ScanRepository.
 */
private fun productNotFoundMessage(lang: String) =
    if (lang == "en") "Product not found in Open Food Facts — add a photo to continue"
    else "Produit introuvable dans Open Food Facts — ajoutez une photo pour continuer"

private fun conflictMessage(lang: String, field: String, offValue: Any?, llmValue: Any?) =
    if (lang == "en") "Conflict: $field OFF=$offValue LLM=$llmValue"
    else "Conflit : $field OFF=$offValue IA=$llmValue"

/**
 * DIRECT-mode OFF lookup + label-OCR merge logic, extracted verbatim out of
 * ScanRepository - the cohesive "barcode -> Open Food Facts, optionally
 * augmented by the vision LLM" concern. ScanRepository holds one instance and
 * delegates scoreDirectBarcode() to it; the public ScanRepository API is
 * unchanged.
 */
internal class ScanOffLookup(
    private val offApi: OpenFoodFactsApi,
    private val ocrParser: OcrParser,
) {
    /**
     * Looks up a barcode on OFF, retrying against every plausible alternate
     * encoding of the same GTIN on a 404. Scanners hand back the code as
     * printed — 12-digit UPC-A on many North American cans, 13-digit EAN-13
     * elsewhere, and *compressed* UPC-E (6–8 digits) on small packaging like
     * soda cans and candy — but OFF only indexes the expanded UPC-A/EAN-13
     * form, so a compressed or differently-padded code misses even though
     * the product is in the database. This was the root cause behind cans
     * scanning as "not found" (see the Coke-can investigation).
     */
    private suspend fun fetchOffProduct(barcode: String): OffResponse? = coroutineScope {
        // OFF is a public, sometimes-flaky API - a transient 5xx/429/network blip
        // previously surfaced immediately as a hard failure here, unlike OcrParser's
        // vision-LLM calls which already retry the same class of error with a short
        // backoff (see OcrParser.callWithRetry/isRetryable). 404 still means "not
        // this candidate encoding" and returns null without retrying, same as before.
        suspend fun lookup(code: String): OffResponse? {
            var lastErr: Throwable? = null
            repeat(OFF_MAX_ATTEMPTS) { attempt ->
                try {
                    return offApi.getProduct(code)
                } catch (e: HttpException) {
                    if (e.code() == 404) return null
                    if (e.code() != 429 && e.code() !in 500..599) throw e
                    lastErr = e
                } catch (e: IOException) {
                    lastErr = e
                }
                if (attempt < OFF_MAX_ATTEMPTS - 1) delay(backoffDelayMs(attempt))
            }
            throw lastErr!!
        }

        // Candidates are independent reads, so they're fired off concurrently
        // instead of one-round-trip-at-a-time — a UPC-E can (the exact case
        // from the earlier Coke-can investigation) can need 3-4 candidate
        // expansions before hitting the real match, and doing that serially
        // stacks their full network latency end to end. Still awaited in
        // priority order (not first-to-complete) so a lower-priority
        // candidate that happens to respond faster never wins over an
        // earlier, more-likely one.
        val candidates = barcodeCandidates(barcode)
        val pending = candidates.map { async { lookup(it) } }
        for (deferred in pending) {
            deferred.await()?.product?.let { return@coroutineScope OffResponse(status = 1, product = it) }
        }
        null
    }

    suspend fun scoreDirectBarcode(
        barcode: String,
        images: List<ImagePayload>,
        apiKey: String,
        cerebrasApiKey: String,
        lang: String,
        missingApiKeyMessage: (String) -> String,
    ): ScanResult {
        val offResponse = fetchOffProduct(barcode)
        // Checked before mapOffProduct (which only preserves the coarse
        // ProductCategory food-subcategory enum, not the raw tags) and before any
        // LLM augmentation - running a vision-LLM label parse against something
        // like a lubricant or bleach bottle to "fill in missing nutrition facts"
        // would fabricate nutrition data for a product that was never meant to
        // carry any, not just under-serve a sparse but genuine food record.
        offResponse?.product?.let { dto ->
            val name = dto.productNameFr ?: dto.productName ?: dto.genericNameFr ?: ""
            classifyNonFood(dto.categoriesTags, name, dto.brands)?.let { category ->
                throw NonFoodProductException(
                    productName = name,
                    brand       = dto.brands ?: "",
                    category    = category,
                )
            }
        }
        val offProduct  = offResponse?.product?.let { dto ->
            mapOffProduct(OffProductResponse(
                productName       = dto.productName,
                productNameFr     = dto.productNameFr,
                genericNameFr     = dto.genericNameFr,
                brands            = dto.brands,
                categoriesTags    = dto.categoriesTags,
                ingredientsTextFr = dto.ingredientsTextFr,
                ingredientsText   = dto.ingredientsText,
                novaGroup         = dto.novaGroup,
                nutriments        = dto.nutriments?.mapValues { it.value },
                labelsTags        = dto.labelsTags,
                origins           = dto.origins,
                countriesTags     = dto.countriesTags,
                quantity          = dto.quantity,
                ecoscoreGrade     = dto.ecoscoreGrade,
                ecoscoreScore     = dto.ecoscoreScore,
                nutritionGrades   = dto.nutritionGrades,
                allergensTags     = dto.allergensTags,
                additivesTags     = dto.additivesTags,
            ))
        }

        val hasAnyKey = apiKey.isNotBlank() || cerebrasApiKey.isNotBlank()
        val (finalProduct, source, warnings) = when {
            offProduct != null && isOffSparse(offProduct) && images.isNotEmpty() && hasAnyKey -> {
                val parsed    = ocrParser.parseLabel(images, apiKey, cerebrasApiKey, lang = lang)
                val merged    = mergeOffWithLlm(offProduct, parsed.product)
                val conflicts = detectSourceConflicts(offProduct, parsed.product)
                Triple(merged, ScanSource.MERGED,
                    parsed.warnings + conflicts.map { conflictMessage(lang, it.field, it.offValue, it.llmValue) })
            }
            offProduct != null -> Triple(offProduct, ScanSource.OPEN_FOOD_FACTS, emptyList())
            images.isNotEmpty() && hasAnyKey -> {
                val parsed = ocrParser.parseLabel(images, apiKey, cerebrasApiKey, lang = lang)
                Triple(parsed.product, ScanSource.LLM, parsed.warnings)
            }
            // The user DID take a photo here (images.isNotEmpty() true, hasAnyKey
            // false is why the branch above didn't match) - falling through to
            // productNotFoundMessage's "add a photo to continue" told them to do
            // the one thing they'd already done, with no hint that label-scanning
            // is an opt-in feature gated behind Settings' AI key field. Barcode
            // scanning itself never needed a key (see the offProduct != null
            // branch above); this message only fires for the label-scan path.
            images.isNotEmpty() -> throw ProductNotFoundException(missingApiKeyMessage(lang))
            else -> throw ProductNotFoundException(productNotFoundMessage(lang))
        }

        // Fills iron/calcium/magnesium/potassium/zinc/vitC/vitD/B12 with a
        // category-representative estimate wherever OFF/LLM declared none at all -
        // see MicronutrientEstimator.kt for why: most barcode products simply never
        // list these, which previously meant a real, common food (e.g. beef,
        // fresh_meat) logged with zero iron impact on the day's totals, every time.
        val estimatedProduct = finalProduct.copy(
            nutrition = finalProduct.nutrition.withEstimatedMicronutrients(finalProduct.category),
        )
        val audit = scoreProduct(estimatedProduct, lang)
        return ScanResult(product = estimatedProduct, audit = audit, warnings = warnings, source = source, barcode = barcode)
    }

    private companion object {
        /** Matches OcrParser.callWithRetry's per-candidate retry budget for the same class of transient error. */
        const val OFF_MAX_ATTEMPTS = 3
    }
}
