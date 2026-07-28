package fr.scanneat.data.repository.scan

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.AppDatabase
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
import fr.scanneat.domain.engine.nonconsumable.NonConsumableCategory
import fr.scanneat.domain.engine.nonconsumable.NonConsumableDbEntry
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import fr.scanneat.util.ioCatching
import fr.scanneat.util.serverUrlMissingMessage
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a barcode has no Open Food Facts entry and no photos were supplied to fall back on. */
class ProductNotFoundException(message: String) : Exception(message)

/**
 * Thrown when a barcode's own OFF category tags (checked via classifyNonFood)
 * indicate it isn't actually food/beverage/supplement/medicine at all - a
 * lubricant, shampoo, or cigarette pack scored as "food" produces a
 * meaningless nutrition-based grade instead of no grade at all. [category] is
 * a NonConsumableCategory key string (matches the Android-only asset-backed
 * NonConsumableLookupDb's own enum names exactly), kept as a plain string here
 * so this file has no dependency on that package.
 */
class NonFoodProductException(val productName: String, val brand: String, val category: String) : Exception()

// These reach the user verbatim (ScanViewModel shows e.message directly in the
// error banner) — "Groq API key not configured" was leaking straight to a
// French-first UI in English, and neither message respected the [lang]
// parameter these functions already thread through for exactly this purpose.
private fun offlineMessage(lang: String) =
    if (lang == "en") "No internet connection" else "Pas de connexion internet"

private fun missingApiKeyMessage(lang: String) =
    if (lang == "en") "Missing Groq API key — set it up in Settings"
    else "Clé API Groq manquante — configurez-la dans Réglages"

@Singleton
class ScanRepository @Inject constructor(
    private val offApi: OpenFoodFactsApi,
    private val opfApi: OpenProductsFactsApi,
    private val dao: ScanHistoryDao,
    private val scoreHistoryDao: ScanScoreHistoryDao,
    private val prefs: UserPreferences,
    private val ocrParser: OcrParser,
    private val moshi: Moshi,                  // singleton from AppModule
    private val customFoodRepo: CustomFoodRepository,
    private val serverApiProvider: ServerScanApiProvider,
    private val db: AppDatabase,
) {
    private val productAdapter = moshi.adapter(Product::class.java)
    private val auditAdapter   = moshi.adapter(ScoreAudit::class.java)
    private val warningsAdapter = moshi.adapter<List<String>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
    )

    // Cohesive network concerns delegated to their own files (ScanServerClient,
    // ScanOffLookup) verbatim - same retry/backoff, same request/response
    // shapes, same exception signalling as before the split. Purely a
    // structural extraction: ScanRepository's own public API is unchanged.
    private val serverClient = ScanServerClient(serverApiProvider)
    private val offLookup = ScanOffLookup(offApi, ocrParser)

    // ---- History ----

    // Convention (applies repo-wide, not just here): every DAO/repository method
    // that reads or writes profile-scoped data accepts profileId: String =
    // "default" and threads it straight through to the query - this is the
    // app's only multi-profile foundation (single-profile today, but every
    // Room table/index is already scoped by it). A new method that silently
    // hardcodes "default" inline instead of accepting this parameter quietly
    // breaks that foundation the day multi-profile support ships.
    fun observeHistory(limit: Int = 50, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.observeRecent(profileId = profileId, limit = limit).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    /** See ScanHistoryDao.observeRecentChecked's own doc comment. */
    fun observeHistoryChecked(limit: Int = 50, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.observeRecentChecked(profileId = profileId, limit = limit).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    suspend fun getById(id: Long): ScanResult? = dao.findById(id)?.toDomain()

    /**
     * Same staleness check scoreBarcode()'s own cache-hit path already applies:
     * a cached row scored by an older engine version is rescored locally (pure
     * function, no network) rather than returned as-is. Without this, the
     * "already scanned" live preview (ScanViewModel.cachedPreview and the
     * per-barcode AR panel) kept showing a product's pre-engine-bump grade
     * until the user actually re-scanned it, even though scoreBarcode() itself
     * would have shown the corrected score all along.
     */
    suspend fun getCachedByBarcode(barcode: String, profileId: String = "default", lang: String = "en"): ScanResult? =
        dao.findByBarcode(barcode, profileId)?.toDomain()?.let { cached ->
            if (cached.audit.engineVersion != ENGINE_VERSION) cached.copy(audit = scoreProduct(cached.product, lang)) else cached
        }

    /**
     * Live Open Products Facts lookup — a fallback for barcodes the bundled
     * NonConsumableLookupDb CSV (a frozen 2026-07-13 export snapshot) doesn't
     * cover yet, e.g. a household/personal-care product added to OPF after
     * that export or just outside the category slice the export query
     * covered (the reported case: a mouthwash barcode wasn't in the static
     * CSV and OFF has no reason to know about it either, so scoreBarcode()
     * fell straight through to a dead-end "product not found" error even
     * though OPF's own live database has it). classifyNonFood is the same
     * pure tag-classifier scoreDirectBarcode() already uses on OFF's
     * categories_tags - reused here since OPF's category tag shape is
     * identical. Returns null (not an error) on any network failure -
     * this is a best-effort enrichment on top of the existing "not found"
     * path, not something that should itself block or fail a scan.
     */
    suspend fun findNonConsumableViaOpf(barcode: String): NonConsumableDbEntry? = runCatching {
        val product = opfApi.getProduct(barcode).product ?: return@runCatching null
        val name = product.productName ?: ""
        val category = classifyNonFood(product.categoriesTags, name, product.brands) ?: return@runCatching null
        NonConsumableDbEntry(
            barcode  = barcode,
            name     = name,
            brand    = product.brands ?: "",
            category = runCatching { NonConsumableCategory.valueOf(category) }.getOrDefault(NonConsumableCategory.OTHER),
        )
    }.getOrNull()

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

    /**
     * A better-scoring product from the user's own history, same category —
     * or null if none beats [scan] once the user's own allergens/diet are
     * respected. Previously took only the single best-scoring DB row with no
     * regard for the current profile at all — a peanut-allergic user could
     * be shown "here's a better alternative" pointing at a product that
     * itself contains peanuts, since nothing downstream ever re-checked it.
     * findBetterInCategory now returns a small pool (best-score-first) so
     * this can walk past any candidate that fails either check instead of
     * being stuck with the single row the old query returned.
     */
    suspend fun findBetterAlternative(
        scan: ScanResult,
        allergens: Set<String> = emptySet(),
        dietKey: DietKey = DietKey.NONE,
        lang: String = "fr",
        profileId: String = "default",
    ): ScanResult? {
        val candidates = dao.findBetterInCategory(
            category       = scan.product.category.key,
            minScore       = scan.audit.score,
            excludeBarcode = scan.barcode,
            profileId      = profileId,
        ).mapNotNull { it.toDomain() }
        return candidates.firstOrNull { candidate ->
            val allergenHits = if (allergens.isNotEmpty()) checkUserAllergens(candidate.product, allergens, lang) else emptyList()
            allergenHits.isEmpty() && checkDiet(candidate.product, dietKey, lang).compliant
        }
    }

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
    suspend fun persist(result: ScanResult, profileId: String = "default"): Long = db.withTransaction {
        // scan_history and scan_score_history are two separate DAOs, written by
        // two separate suspend calls - without wrapping both in one Room
        // transaction, a process death between them (rare, but real - Android can
        // kill a background app mid-write at any time) left scan_history updated
        // but that scan's score-history row missing, permanently under-counting
        // "Top Scanned" and silently dropping one point from the score-delta/
        // sparkline feature for that product, with no way to detect or repair it
        // after the fact.
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
                // Explicit, not relied-on-as-default - see ScanHistoryEntity's own
                // doc comment on why the raw default must stay false.
                nonFoodChecked = true,
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
        id
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
            dao.findByBarcode(barcode)?.let { entity ->
                // A row written before classifyNonFood() existed (nonFoodChecked=0,
                // backfilled by MIGRATION_24_25) might be exactly the kind of
                // lubricant/cosmetic/tobacco item that check exists to catch - trusting
                // it forever would mean the SAME physical barcode shows the correct
                // "not food" dialog on a first scan post-update but silently reverts to
                // its old bogus food score on every rescan after that, just because a
                // cache row already exists. Falling through to a real lookup re-runs
                // that check once and persist() then writes nonFoodChecked=true, so
                // every scan after this one is fast again regardless of API mode. When
                // offline, still serve the stale cache rather than error out - a legacy
                // row is far more likely to be genuine food than not, and this defers
                // to the very next online scan instead of blocking the user now.
                if (entity.nonFoodChecked || !online) {
                    entity.toDomain()?.let { cached ->
                        val fresh = if (cached.audit.engineVersion != ENGINE_VERSION) {
                            cached.copy(audit = scoreProduct(cached.product, lang))
                        } else cached
                        return@ioCatching Pair(fresh, persist(fresh))
                    }
                }
            }
        }
        if (!online) error(offlineMessage(lang))

        val result = try {
            when (apiMode) {
                ApiMode.SERVER -> serverClient.scoreViaServer(serverUrl, apiKey, images, barcode, lang, DEFAULT_MODEL)
                ApiMode.DIRECT -> offLookup.scoreDirectBarcode(barcode, images, apiKey, cerebrasKey, lang, ::missingApiKeyMessage)
            }
        } catch (e: Exception) {
            // A cancelled scan (user already left the screen) must propagate as
            // cancellation, not be reinterpreted as "lookup failed" - otherwise a
            // stale DB fallback lookup below could fabricate a "success" result
            // for a scan nobody is waiting on anymore.
            if (e is CancellationException) throw e
            // A legacy row from before classifyNonFood() existed (or, rarer, one the
            // static NonConsumableLookupDb CSV didn't cover yet) can end up right here
            // via the cache-hit re-verification above falling through to this real
            // lookup - it never should have been scored as food, so it's purged
            // outright rather than left for findBetterInCategory/observeTopScanned to
            // keep surfacing forever. A harmless no-op when there was no prior row
            // (a genuinely first-time scan of a non-food barcode).
            if (e is NonFoodProductException) purgeNonFoodEntry(barcode)
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

    private suspend fun purgeNonFoodEntry(barcode: String, profileId: String = "default") = db.withTransaction {
        dao.deleteByBarcode(barcode, profileId)
        // matchKeyFor(barcode, ...) always resolves to the barcode itself when one is
        // present (see its own definition) - passed directly here since there's no
        // product name to fall back to for something being purged, not scored.
        scoreHistoryDao.deleteByMatchKey(barcode, profileId)
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
                serverClient.identifyViaServer(serverUrl, apiKey, images, lang)
            } else {
                serverClient.scoreViaServer(serverUrl, apiKey, images, barcode = null, lang = lang, model = DEFAULT_MODEL)
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
    //
    // scoreViaServer/identifyViaServer and their shared retry/backoff now live
    // in ScanServerClient (see serverClient above) - extracted verbatim, same
    // retry policy, same request/response shapes.

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
        serverClient.identifyMulti(serverUrl, apiKey, images, lang)
    }

    // ---- Direct mode ----
    //
    // fetchOffProduct/scoreDirectBarcode now live in ScanOffLookup (see
    // offLookup above) - extracted verbatim, same OFF candidate-encoding
    // retry loop, same OFF/LLM merge logic.

    // ---- Entity → domain ----

    private companion object {
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
