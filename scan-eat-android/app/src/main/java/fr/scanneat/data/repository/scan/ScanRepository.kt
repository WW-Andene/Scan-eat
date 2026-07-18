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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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

private fun serverUrlMissingMessage(lang: String) =
    if (lang == "en") "Server URL not configured — set it up in Settings"
    else "URL du serveur non configurée — configurez-la dans Réglages"

@Singleton
class ScanRepository @Inject constructor(
    private val offApi: OpenFoodFactsApi,
    private val dao: ScanHistoryDao,
    private val scoreHistoryDao: ScanScoreHistoryDao,
    private val prefs: UserPreferences,
    private val ocrParser: OcrParser,
    private val okHttpClient: OkHttpClient,   // injected directly — no unsafe cast
    private val moshi: Moshi,                  // singleton from AppModule
    private val customFoodRepo: CustomFoodRepository,
) {
    private val productAdapter = moshi.adapter(Product::class.java)
    private val auditAdapter   = moshi.adapter(ScoreAudit::class.java)
    private val warningsAdapter = moshi.adapter<List<String>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
    )

    @Volatile private var _serverApi: ServerScanApi? = null
    @Volatile private var _serverUrl: String = ""

    private fun serverApi(url: String): ServerScanApi {
        val normUrl = if (url.endsWith("/")) url else "$url/"
        if (_serverApi == null || _serverUrl != normUrl) {
            _serverApi = Retrofit.Builder()
                .baseUrl(normUrl)
                .client(okHttpClient)          // safe: directly injected OkHttpClient
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ServerScanApi::class.java)
            _serverUrl = normUrl
        }
        return _serverApi!!
    }

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

    fun observeTodayScanCount(profileId: String = "default"): Flow<Int> {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return dao.observeCountSince(startOfDay, profileId)
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
    ): Result<Pair<ScanResult, Long>> = runCatching {
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
        getCachedByBarcode(barcode)?.let { cached ->
            val fresh = if (cached.audit.engineVersion != ENGINE_VERSION) {
                cached.copy(audit = scoreProduct(cached.product, lang))
            } else cached
            return@runCatching Pair(fresh, persist(fresh))
        }
        if (!online) error(offlineMessage(lang))

        val result = try {
            when (apiMode) {
                ApiMode.SERVER -> scoreViaServer(serverUrl, apiKey, images, barcode, lang, DEFAULT_MODEL)
                ApiMode.DIRECT -> scoreDirectBarcode(barcode, images, apiKey, cerebrasKey, lang)
            }
        } catch (e: Exception) {
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
    ): Result<Pair<ScanResult, Long>> = runCatching {
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
    ): Result<ScanResult> = runCatching {
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
                    ocrParser.identifyFood(images, apiKey, cerebrasKey)
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
        var lastErr: Throwable? = null
        repeat(SERVER_MAX_ATTEMPTS) { attempt ->
            try {
                val resp = serverApi(serverUrl).score(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
                return resp.toDomain(lang)
            } catch (e: HttpException) {
                if (e.code() != 429 && e.code() !in 500..599) throw e
                lastErr = e
            } catch (e: IOException) {
                lastErr = e
            }
            if (attempt < SERVER_MAX_ATTEMPTS - 1) delay(400L * (attempt + 1))
        }
        throw lastErr!!
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
        var lastErr: Throwable? = null
        repeat(SERVER_MAX_ATTEMPTS) { attempt ->
            try {
                val resp = serverApi(serverUrl).identify(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
                return resp.toDomain(lang)
            } catch (e: HttpException) {
                if (e.code() != 429 && e.code() !in 500..599) throw e
                lastErr = e
            } catch (e: IOException) {
                lastErr = e
            }
            if (attempt < SERVER_MAX_ATTEMPTS - 1) delay(400L * (attempt + 1))
        }
        throw lastErr!!
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
                if (attempt < OFF_MAX_ATTEMPTS - 1) delay(400L * (attempt + 1))
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

    /** Every plausible GTIN encoding for [barcode], most-likely-first, deduplicated. */
    private fun barcodeCandidates(barcode: String): List<String> {
        val candidates = LinkedHashSet<String>()
        candidates += barcode

        upcEToUpcA(barcode)?.let { upcA ->
            candidates += upcA
            candidates += "0$upcA" // EAN-13 form of the expanded UPC-A
        }

        gtin14ToEan13(barcode)?.let { ean13 ->
            candidates += ean13
            if (ean13.startsWith("0")) candidates += ean13.substring(1) // UPC-A form
        }

        when {
            barcode.length == 12 -> candidates += "0$barcode"
            barcode.length == 13 && barcode.startsWith("0") -> candidates += barcode.substring(1)
        }

        return candidates.toList()
    }

    /**
     * Strips a GTIN-14 case/pallet code's leading packaging-indicator digit
     * (0–8) down to the consumer-unit EAN-13, recomputing the check digit
     * over the remaining payload — case codes on bulk/wholesale packaging
     * aren't indexed in OFF under their 14-digit form, only under the
     * underlying retail GTIN.
     */
    private fun gtin14ToEan13(code: String): String? {
        if (code.length != 14 || !code.all { it.isDigit() }) return null
        val payload12 = code.substring(1, 13)
        return "0$payload12" + ean13CheckDigit(payload12)
    }

    /** Standard mod-10 EAN-13 check digit for a 12-digit payload. */
    private fun ean13CheckDigit(payload12: String): Int {
        val sum = payload12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - (sum % 10)) % 10
    }

    /**
     * Expands a compressed UPC-E code (6 core digits, optionally with a
     * leading number-system digit and/or trailing check digit — 6, 7, or 8
     * chars total) to its 12-digit UPC-A form, per the standard GS1
     * expansion rules keyed on the last core digit. Only called as a
     * fallback after the code as printed already 404'd, so a false-positive
     * guess (e.g. an EAN-8 code that happens to also parse as UPC-E) just
     * fails its own lookup too — it can't return the wrong product.
     */
    private fun upcEToUpcA(code: String): String? {
        if (!code.all { it.isDigit() }) return null
        val (numberSystem, core) = when (code.length) {
            6 -> '0' to code
            7 -> '0' to code.take(6)
            8 -> code[0] to code.substring(1, 7)
            else -> return null
        }
        if (numberSystem != '0' && numberSystem != '1') return null

        val d = core.map { it - '0' }
        val (manufacturer, product) = when (d[5]) {
            0, 1, 2 -> "${d[0]}${d[1]}${d[5]}00" to "00${d[2]}${d[3]}${d[4]}"
            3       -> "${d[0]}${d[1]}${d[2]}00" to "000${d[3]}${d[4]}"
            4       -> "${d[0]}${d[1]}${d[2]}${d[3]}0" to "0000${d[4]}"
            else    -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}" to "0000${d[5]}"
        }
        val upcA11 = "$numberSystem$manufacturer$product"
        return upcA11 + upcCheckDigit(upcA11)
    }

    /** Standard mod-10 UPC/EAN check digit for an 11-digit UPC-A payload. */
    private fun upcCheckDigit(payload11: String): Int {
        val sum = payload11.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - (sum % 10)) % 10
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
                    parsed.warnings + conflicts.map { "Conflict: ${it.field} OFF=${it.offValue} LLM=${it.llmValue}" })
            }
            offProduct != null -> Triple(offProduct, ScanSource.OPEN_FOOD_FACTS, emptyList())
            images.isNotEmpty() && hasAnyKey -> {
                val parsed = ocrParser.parseLabel(images, apiKey, cerebrasApiKey, lang = lang)
                Triple(parsed.product, ScanSource.LLM, parsed.warnings)
            }
            else -> throw ProductNotFoundException("Produit introuvable dans Open Food Facts — ajoutez une photo pour continuer")
        }

        val audit = scoreProduct(finalProduct, lang)
        return ScanResult(product = finalProduct, audit = audit, warnings = warnings, source = source, barcode = barcode)
    }

    // ---- Server response → domain ----

    private fun ServerNutritionDto.toDomain() = NutritionPer100g(
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
        vitCMg        = vitCMg,
        vitDUg        = vitDUg,
        vitAUg        = vitAUg,
        vitEMg        = vitEMg,
        vitKUg        = vitKUg,
        b12Ug         = b12Ug,
        omega3G       = omega3G,
    )

    private fun List<ServerIngredientDto>.toDomainIngredients() = map { i ->
        Ingredient(name = i.name, percentage = i.percentage, eNumber = i.eNumber,
            category = when (i.category?.lowercase()) {
                "additive"       -> IngredientCategory.ADDITIVE
                "processing_aid" -> IngredientCategory.PROCESSING_AID
                else             -> IngredientCategory.FOOD
            })
    }

    private fun ServerScoreResponse.toDomain(lang: String = "fr"): ScanResult {
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

    /** Same recompute-locally approach as ServerScoreResponse.toDomain() - see identifyViaServer(). */
    private fun ServerIdentifyResponse.toDomain(lang: String = "fr"): ScanResult {
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
