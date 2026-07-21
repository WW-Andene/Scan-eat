package fr.scanneat.data.repository.scan

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.scan.ScanScoreHistoryDao
import fr.scanneat.data.local.db.scan.ScanScoreHistoryEntity
import fr.scanneat.data.local.db.scan.TopScannedRow
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.*
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import fr.scanneat.util.barcodeCandidates
import fr.scanneat.util.ioCatching
import fr.scanneat.util.serverUrlMissingMessage
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.random.Random

/** Thrown when a barcode has no Open Food Facts entry and no photos were supplied to fall back on. */
class ProductNotFoundException(message: String) : Exception(message)

// These reach the user verbatim (ScanViewModel shows e.message directly in the
// error banner) — "Groq API key not configured" was leaking straight to a
// French-first UI in English, and neither message respected the [lang]
// parameter these functions already thread through for exactly this purpose.
private fun offlineMessage(lang: String) =
    if (lang == "en") "No internet connection" else "Pas de connexion internet"

private fun missingApiKeyMessage(lang: String) =
    if (lang == "en") "Missing Groq API key — set it up in Settings"
    else "Clé API Groq manquante — configurez-la dans Réglages"

private fun productNotFoundMessage(lang: String) =
    if (lang == "en") "Product not found in Open Food Facts — add a photo to continue"
    else "Produit introuvable dans Open Food Facts — ajoutez une photo pour continuer"

private fun conflictMessage(lang: String, field: String, offValue: Any?, llmValue: Any?) =
    if (lang == "en") "Conflict: $field OFF=$offValue LLM=$llmValue"
    else "Conflit : $field OFF=$offValue IA=$llmValue"

@Singleton
class ScanRepository @Inject constructor(
    private val offApi: OpenFoodFactsApi,
    private val dao: ScanHistoryDao,
    private val scoreHistoryDao: ScanScoreHistoryDao,
    private val prefs: UserPreferences,
    private val ocrParser: OcrParser,
    private val moshi: Moshi,                  // singleton from AppModule
    private val customFoodRepo: CustomFoodRepository,
    private val serverApiProvider: ServerScanApiProvider,
) {
    private val productAdapter = moshi.adapter(Product::class.java)
    private val auditAdapter   = moshi.adapter(ScoreAudit::class.java)
    private val warningsAdapter = moshi.adapter<List<String>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
    )

    /**
     * Exponential backoff with jitter, replacing the old fixed `400L * (attempt + 1)`
     * linear delay used by every retry loop in this file (fetchOffProduct's lookup,
     * scoreViaServer, identifyViaServer) - same rough magnitude (~400ms then ~800ms
     * across this file's 3-attempt budget) but avoids concurrent clients retrying in
     * lockstep against a momentarily-overloaded OFF/server endpoint.
     */
    private fun backoffDelayMs(attempt: Int, baseDelayMs: Long = 400L, jitterMs: Long = 200L): Long =
        (baseDelayMs * 2.0.pow(attempt)).toLong() + Random.nextLong(0, jitterMs)

    // ---- History ----

    fun observeHistory(limit: Int = 50, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.observeRecent(profileId = profileId, limit = limit).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    suspend fun getById(id: Long): ScanResult? = dao.findById(id)?.toDomain()

    suspend fun getCachedByBarcode(barcode: String, profileId: String = "default"): ScanResult? =
        dao.findByBarcode(barcode, profileId)?.toDomain()

    fun observeFavorites(profileId: String = "default"): Flow<List<ScanResult>> =
        dao.observeFavorites(profileId).map { entities -> entities.mapNotNull { it.toDomain() } }

    /**
     * Searches the full history by product name/barcode, not just whatever
     * window the caller happens to have loaded - see ScanHistoryDao.searchByName.
     */
    fun searchHistory(query: String, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.searchByName(query, profileId).map { entities -> entities.mapNotNull { it.toDomain() } }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    // ScanViewModel.todayScanCount collects this once at property-init time and keeps
    // it for the ViewModel's whole lifetime - startOfDay computed once here (the
    // previous shape) stayed pinned to whichever day the ViewModel happened to be
    // constructed on, so a session left open across midnight kept accumulating into
    // "today"'s count forever instead of resetting for the new day (observeCountSince
    // is an open-ended >= bound, not a same-day window). Polling + flatMapLatest
    // re-derives startOfDay and re-subscribes exactly when the day rolls over, same
    // fix already applied to DiaryViewModel/CalendarViewModel/DashboardViewModel.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayScanCount(profileId: String = "default"): Flow<Int> =
        flow {
            while (true) {
                emit(java.time.LocalDate.now())
                delay(60_000)
            }
        }.distinctUntilChanged().flatMapLatest { today ->
            val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            dao.observeCountSince(startOfDay, profileId)
        }

    suspend fun delete(id: Long) = dao.delete(id)

    /** A better-scoring product from the user's own history, same category — or null if none beats [scan]. */
    suspend fun findBetterAlternative(scan: ScanResult, profileId: String = "default"): ScanResult? =
        dao.findBetterInCategory(
            category       = scan.product.category.key,
            minScore       = scan.audit.score,
            excludeBarcode = scan.barcode,
            profileId      = profileId,
        )?.toDomain()

    /**
     * Upserts by barcode instead of always inserting — rescanning the same product
     * (very common: same item scanned 50-100+ times over weeks) previously created a
     * brand-new scan_history row every single time, since @Insert(REPLACE) only
     * dedupes on the primary key, which was always 0/new. That bloated the table
     * unbounded and was the direct cause of the "same product => tons of entries"
     * report. Reusing the existing row's id (and its favorite flag) makes a rescan
     * simply refresh the existing entry's score/timestamp instead of cloning it.
     *
     * The find-then-insert itself runs inside ScanHistoryDao.upsertByBarcode's
     * @Transaction so two concurrent scans of the same barcode can't both read
     * "no existing row" and both insert a duplicate.
     */
    suspend fun persist(result: ScanResult, profileId: String = "default"): Long {
        val now = System.currentTimeMillis()
        val id = dao.upsertByBarcode(result.barcode, profileId) { existingId, existingFavorite ->
            ScanHistoryEntity(
                id          = existingId,
                barcode     = result.barcode,
                productName = result.product.name,
                score       = result.audit.score,
                grade       = result.audit.grade.label,
                category    = result.product.category.key,
                sourceJson  = result.source.name,
                productJson = productAdapter.toJson(result.product),
                auditJson   = auditAdapter.toJson(result.audit),
                scannedAt   = now,
                profileId   = profileId,
                favorite    = existingFavorite,
                warningsJson = warningsAdapter.toJson(result.warnings),
            )
        }
        // Opportunistic retention trim - scan_history otherwise grows unbounded
        // forever for distinct products (repeat scans of the *same* product
        // already upsert in place above, so this only ever matters for a heavy
        // user who scans thousands of distinct items). A no-op most of the time:
        // the DELETE's NOT IN subquery returns every row once the table is under
        // MAX_HISTORY_ROWS, so nothing matches and nothing is deleted. Favorites
        // are never trimmed - see ScanHistoryDao.trimNonFavorites.
        dao.trimNonFavorites(MAX_HISTORY_ROWS, profileId)

        // Written on every persist(), including a rescan that upserts scan_history's
        // row in place - see ScanScoreHistoryEntity's doc comment for why that upsert
        // would otherwise silently destroy the very history the score-delta/sparkline
        // feature needs.
        scoreHistoryDao.insert(ScanScoreHistoryEntity(
            matchKey  = matchKeyFor(result.barcode, result.product.name),
            score     = result.audit.score,
            scannedAt = now,
            profileId = profileId,
        ))
        scoreHistoryDao.trim(MAX_HISTORY_ROWS, profileId)
        return id
    }

    /**
     * Prior scores for the same product (matched by barcode when present, else
     * case-insensitive name), most-recent-first, strictly before [beforeMillis] -
     * used for ResultViewModel's score-delta badge and history sparkline.
     */
    suspend fun priorScores(barcode: String?, productName: String, beforeMillis: Long, profileId: String = "default", limit: Int = 6): List<Int> =
        scoreHistoryDao.recentScoresBefore(matchKeyFor(barcode, productName), beforeMillis, limit, profileId)

    /** Top-N most-frequently-scanned products, counted from the append-only score log - see ScanHistoryDao.observeTopScanned's own doc comment. */
    fun observeTopScanned(profileId: String = "default", limit: Int = 3): Flow<List<TopScannedRow>> =
        dao.observeTopScanned(profileId, limit)

    private fun matchKeyFor(barcode: String?, productName: String): String = barcode ?: productName.lowercase()

    // ---- Score from barcode ----

    suspend fun scoreBarcode(
        barcode: String,
        images: List<ImagePayload> = emptyList(),
        lang: String = "fr",
        online: Boolean = true,
    ): Result<Pair<ScanResult, Long>> = ioCatching {
        val apiMode   = prefs.apiMode.first()
        val apiKey    = prefs.groqApiKey.first()
        val cerebrasKey = prefs.cerebrasApiKey.first()
        val serverUrl = prefs.serverUrl.first()

        // A barcode already scanned before is served straight from the local
        // cache — only a genuinely new lookup needs a connection, so this check
        // happens after the cache read instead of gating every scan up front.
        // If the scoring engine shipped since this was cached, rescore the
        // already-stored product locally (pure function, no network) instead
        // of serving a permanently stale score — without this, engine fixes
        // never reach anything already in a user's history.
        //
        // Only takes this shortcut when no new photos are queued. [images] used
        // to be ignored here entirely: any cache hit returned immediately
        // regardless of [images], silently discarding photos the user had just
        // taken to enrich/correct a sparse cached entry - directly contradicting
        // ScanViewModel.onBarcodeDetected's own documented design ("a barcode
        // detected first can be augmented with follow-up photos when OFF's
        // entry for it is sparse"). Falling through to the normal lookup below
        // when images are present lets scoreDirectBarcode/scoreViaServer's
        // existing OFF+LLM merge logic run instead, and persist() still upserts
        // the same barcode's existing row rather than creating a duplicate.
        if (images.isEmpty()) {
            getCachedByBarcode(barcode)?.let { cached ->
                val fresh = if (cached.audit.engineVersion != ENGINE_VERSION) {
                    cached.copy(audit = scoreProduct(cached.product, lang))
                } else cached
                return@ioCatching Pair(fresh, persist(fresh))
            }
        }
        if (!online) error(offlineMessage(lang))

        val result = try {
            when (apiMode) {
                ApiMode.SERVER -> scoreViaServer(serverUrl, apiKey, images, barcode, lang, DEFAULT_MODEL)
                ApiMode.DIRECT -> scoreDirectBarcode(barcode, images, apiKey, cerebrasKey, lang)
            }
        } catch (e: Exception) {
            // A cancelled scan (user already left the screen) must propagate as
            // cancellation, not be reinterpreted as "lookup failed" - otherwise a
            // stale DB fallback lookup below could fabricate a "success" result
            // for a scan nobody is waiting on anymore.
            if (e is CancellationException) throw e
            // Last-resort fallback: neither OFF nor the vision LLM could identify
            // this barcode (or the lookup itself failed after exhausting its own
            // retries) — but the user may have already manually taught the app
            // this exact product (CustomFoodRepository.save()'s barcode param,
            // wired from ResultViewModel.saveToDestinations). Without this, an
            // obscure/local/homemade item hit the identical "not found" wall on
            // every single rescan even after the user already resolved it once.
            customFoodByBarcode(barcode, lang) ?: throw e
        }
        Pair(result, persist(result))
    }

    private suspend fun customFoodByBarcode(barcode: String, lang: String): ScanResult? {
        val entry = customFoodRepo.findByBarcode(barcode) ?: return null
        val product = customFoodRepo.toProduct(entry)
        val fallbackNote = if (lang == "en") "Local match from Mes Aliments — live lookup failed or found nothing"
            else "Correspondance locale (Mes Aliments) — recherche en ligne indisponible ou infructueuse"
        return ScanResult(product = product, audit = scoreProduct(product, lang),
            warnings = listOf(fallbackNote), source = ScanSource.MANUAL, barcode = barcode)
    }

    /**
     * [identifyMode] routes to OcrParser.identifyFood instead of parseLabel — for
     * fresh produce, plated dishes, or anything else with no printed nutrition
     * label to OCR. identifyFood existed since the OcrParser port but had no
     * caller anywhere in the app; this was the missing wiring (see ScanViewModel.
     * identifyFromPhotos / ScanScreen's "identify without label" action).
     */
    suspend fun scoreFromImages(
        images: List<ImagePayload>,
        lang: String = "fr",
        online: Boolean = true,
        identifyMode: Boolean = false,
    ): Result<Pair<ScanResult, Long>> = ioCatching {
        val result = identifyOrScoreFromImages(images, lang, online, identifyMode).getOrThrow()
        Pair(result, persist(result))
    }

    /**
     * Same identify/score logic scoreFromImages uses, without persisting - lets a
     * caller inspect the result (e.g. check its product name against the
     * medication/non-consumable lookup DBs) before deciding whether it's worth a
     * scan_history row at all. Previously ScanViewModel.identifyFromPhotos() made
     * a *separate* identifyProductName vision-LLM call just to get a name to check
     * first, then this same identification work ran a second time via
     * scoreFromImages(identifyMode = true) whenever the name didn't match either
     * DB (the common case: fresh produce, plated dishes) - a second full image
     * upload + model call for the exact same photos. Exposing the un-persisted
     * result here lets the caller reuse one call for both purposes.
     */
    suspend fun identifyOrScoreFromImages(
        images: List<ImagePayload>,
        lang: String = "fr",
        online: Boolean = true,
        identifyMode: Boolean = false,
    ): Result<ScanResult> = ioCatching {
        if (!online) error(offlineMessage(lang))
        val apiMode   = prefs.apiMode.first()
        val apiKey    = prefs.groqApiKey.first()
        val cerebrasKey = prefs.cerebrasApiKey.first()
        val serverUrl = prefs.serverUrl.first()

        when (apiMode) {
            ApiMode.SERVER -> if (identifyMode) {
                identifyViaServer(serverUrl, apiKey, images, lang)
            } else {
                scoreViaServer(serverUrl, apiKey, images, barcode = null, lang = lang, model = DEFAULT_MODEL)
            }
            ApiMode.DIRECT -> {
                if (apiKey.isBlank() && cerebrasKey.isBlank()) error(missingApiKeyMessage(lang))
                val parsed = if (identifyMode) {
                    ocrParser.identifyFood(images, apiKey, cerebrasKey, lang = lang)
                } else {
                    ocrParser.parseLabel(images, apiKey, cerebrasKey, lang = lang)
                }
                ScanResult(
                    product  = parsed.product,
                    audit    = scoreProduct(parsed.product, lang),
                    warnings = parsed.warnings,
                    source   = ScanSource.LLM,
                    barcode  = parsed.barcode,
                )
            }
        }
    }

    // ---- Server mode ----

    /**
     * Retries transient failures (429/5xx/IO) the same way fetchOffProduct/
     * OcrParser.callWithRetry already do for their own network calls - previously
     * a single unguarded call, so the server's own rate limit (30 req/min/IP,
     * see scan-eat-server's RateLimiter) or a momentary 5xx failed the whole
     * scan for SERVER-mode users with no retry, unlike every other network path
     * in this repository.
     */
    /**
     * Shared retry/backoff for scoreViaServer/identifyViaServer - both retried
     * the identical class of transient error (429/5xx/IO) with the identical
     * 3-attempt backoff loop, verbatim, differing only in which server call
     * they make. Extracted here so that logic (and any future tuning of it)
     * exists in exactly one place.
     */
    private suspend fun <T> retryServerCall(block: suspend () -> T): T {
        var lastErr: Throwable? = null
        repeat(SERVER_MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() != 429 && e.code() !in 500..599) throw e
                lastErr = e
            } catch (e: IOException) {
                lastErr = e
            }
            if (attempt < SERVER_MAX_ATTEMPTS - 1) delay(backoffDelayMs(attempt))
        }
        throw lastErr!!
    }

    private suspend fun scoreViaServer(
        serverUrl: String,
        apiKey: String,
        images: List<ImagePayload>,
        barcode: String?,
        lang: String,
        model: String,
    ): ScanResult {
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerScoreRequest(
            images  = images.map { ServerImageDto(it.base64, it.mime) },
            barcode = barcode,
            lang    = lang,
            model   = model,
        )
        return retryServerCall {
            serverApiProvider.get(serverUrl).score(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request).toDomain(lang)
        }
    }

    /**
     * SERVER-mode counterpart to DIRECT mode's ocrParser.identifyFood() - calls
     * the server's own POST /api/identify (see scan-eat-server's IdentifyRoute),
     * which already existed but was never called from here: identifyOrScoreFromImages
     * previously ignored identifyMode entirely for SERVER mode and always fell
     * through to scoreViaServer's label-OCR path, so a SERVER-mode user tapping
     * "Identifier sans étiquette" on fresh produce or a plated dish silently got
     * whatever that path returned for a non-label photo. Same retry/backoff
     * policy as scoreViaServer.
     */
    private suspend fun identifyViaServer(
        serverUrl: String,
        apiKey: String,
        images: List<ImagePayload>,
        lang: String,
    ): ScanResult {
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        return retryServerCall {
            serverApiProvider.get(serverUrl).identify(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request).toDomain(lang)
        }
    }

    /**
     * SERVER-mode counterpart to identifyViaServer(), for a plate holding several
     * distinct foods - calls the server's own POST /api/identify-multi (see
     * scan-eat-server's IdentifyRoute), which already existed but was never
     * called from here: a user photographing a plate with several different
     * foods could previously only ever identify one item at a time via
     * identifyOrScoreFromImages/identify. DIRECT mode's ocrParser.identifyFood()
     * has no multi-item equivalent (it always returns a single Product), so this
     * skips the ApiMode branch entirely and goes straight to the server - same
     * "Server-mode only import" shape as RecipeRepository's fetchRecipeFromUrl/
     * identifyRecipeFromPhotos, which likewise have no DIRECT-mode counterpart
     * and never check prefs.apiMode either. Same retry/backoff policy as
     * scoreViaServer/identifyViaServer. Each returned item is rescored locally
     * via ServerIdentifyResponse.toDomain(), same as the single-item path.
     */
    suspend fun identifyMultiFromImages(
        images: List<ImagePayload>,
        lang: String = "fr",
        online: Boolean = true,
    ): Result<List<ScanResult>> = ioCatching {
        if (!online) error(offlineMessage(lang))
        val serverUrl = prefs.serverUrl.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val apiKey = prefs.groqApiKey.first()
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        val response = retryServerCall {
            serverApiProvider.get(serverUrl).identifyMulti(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
        }
        response.items.map { it.toDomain(lang) }
    }

    // ---- Direct mode ----

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

    private suspend fun scoreDirectBarcode(
        barcode: String,
        images: List<ImagePayload>,
        apiKey: String,
        cerebrasApiKey: String,
        lang: String,
    ): ScanResult {
        val offResponse = fetchOffProduct(barcode)
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
            else -> throw ProductNotFoundException(productNotFoundMessage(lang))
        }

        val audit = scoreProduct(finalProduct, lang)
        return ScanResult(product = finalProduct, audit = audit, warnings = warnings, source = source, barcode = barcode)
    }

    // ---- Entity → domain ----

    private companion object {
        /** Matches OcrParser.callWithRetry's per-candidate retry budget for the same class of transient error. */
        const val OFF_MAX_ATTEMPTS = 3
        /** Same retry budget as OFF_MAX_ATTEMPTS, for scoreViaServer's own transient-error retry. */
        const val SERVER_MAX_ATTEMPTS = 3
        /** Generous cap on non-favorite scan_history rows per profile - see persist()/ScanHistoryDao.trimNonFavorites. */
        const val MAX_HISTORY_ROWS = 5000
    }

    private fun ScanHistoryEntity.toDomain(): ScanResult? = runCatching {
        ScanResult(
            product  = productAdapter.fromJson(productJson)!!,
            audit    = auditAdapter.fromJson(auditJson)!!,
            warnings = warningsAdapter.fromJson(warningsJson) ?: emptyList(),
            source   = ScanSource.valueOf(sourceJson),
            barcode   = barcode,
            dbId      = id,
            favorite  = favorite,
            scannedAt = scannedAt,
        )
    }.onFailure {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository -
        // a parse failure here previously vanished the scan from history/favorites
        // with zero trace.
        android.util.Log.w("ScanRepository", "Failed to parse scan history row id=$id barcode=$barcode", it)
    }.getOrNull()
}
