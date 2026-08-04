package fr.scanneat.data.repository.scan

import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.scan.ScanScoreHistoryDao
import fr.scanneat.data.local.db.scan.TopScannedRow
import fr.scanneat.data.remote.api.OpenProductsFactsApi
import fr.scanneat.domain.engine.nonconsumable.NonConsumableCategory
import fr.scanneat.domain.engine.nonconsumable.NonConsumableDbEntry
import fr.scanneat.domain.engine.nutrition.classifyNonFood
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.engine.scoring.ENGINE_VERSION
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.engine.scoring.healthConditionCautions
import fr.scanneat.domain.engine.scoring.scoreProduct
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * History/browse read paths for [ScanRepository], split out of the main file
 * to keep it from growing unbounded. [ScanRepository] holds one instance of
 * this as a private field and exposes each method here as a same-named,
 * same-signature one-line delegate on itself - so ScanRepository's public
 * member-function surface (and anything, e.g. MockK-based tests, that mocks
 * calls against it) is completely unchanged by this split; only the
 * implementation moved.
 *
 * Convention (applies repo-wide, not just here): every DAO/repository method
 * that reads or writes profile-scoped data accepts profileId: String =
 * "default" and threads it straight through to the query - this is the
 * app's only multi-profile foundation (single-profile today, but every
 * Room table/index is already scoped by it). A new method that silently
 * hardcodes "default" inline instead of accepting this parameter quietly
 * breaks that foundation the day multi-profile support ships.
 */
internal class ScanHistoryQueries(
    private val dao: ScanHistoryDao,
    private val scoreHistoryDao: ScanScoreHistoryDao,
    private val opfApi: OpenProductsFactsApi,
    private val offLookup: ScanOffLookup,
    /** Delegates back to ScanRepository's own productAdapter/auditAdapter/warningsAdapter - see mapScanHistoryEntity(). */
    private val toDomain: (ScanHistoryEntity) -> ScanResult?,
) {
    fun observeHistory(limit: Int = 50, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.observeRecent(profileId = profileId, limit = limit).map { entities ->
            entities.mapNotNull { toDomain(it) }
        }

    /** See ScanHistoryDao.observeRecentChecked's own doc comment. */
    fun observeHistoryChecked(limit: Int = 50, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.observeRecentChecked(profileId = profileId, limit = limit).map { entities ->
            entities.mapNotNull { toDomain(it) }
        }

    /**
     * Same engine-version staleness check getCachedByBarcode()/scoreBarcode()'s
     * cache-hit path already apply - without it, opening a specific History row
     * (see ResultScanLoader.build, the scanId > 0 path) showed that scan's
     * pre-engine-bump grade forever, even though every other read path into a
     * cached row (barcode lookup, live preview) had already been fixed to
     * rescore locally instead. Rescoring is a pure function over the stored
     * product, so this never touches the network or the stored row itself -
     * only the ScanResult handed back to the caller reflects the current engine.
     */
    suspend fun getById(id: Long, lang: String = "en"): ScanResult? =
        dao.findById(id)?.let { toDomain(it) }?.let { cached ->
            if (cached.audit.engineVersion != ENGINE_VERSION) cached.copy(audit = scoreProduct(cached.product, lang)) else cached
        }

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
        dao.findByBarcode(barcode, profileId)?.let { toDomain(it) }?.let { cached ->
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
        dao.observeFavorites(profileId).map { entities -> entities.mapNotNull { toDomain(it) } }

    /**
     * Searches the full history by product name/barcode, not just whatever
     * window the caller happens to have loaded - see ScanHistoryDao.searchByName.
     */
    fun searchHistory(query: String, profileId: String = "default"): Flow<List<ScanResult>> =
        dao.searchByName(query, profileId).map { entities -> entities.mapNotNull { toDomain(it) } }

    /**
     * Free-text search against the whole Open Food Facts catalog (name, brand,
     * ingredient, additive - whatever OFF's own search matches), unlike
     * [searchHistory] above which only covers products this user has personally
     * scanned before. Delegates to [offLookup] - see its own doc comment.
     */
    suspend fun searchOffProducts(query: String, lang: String): List<ScanResult> =
        offLookup.searchOffProducts(query, lang)

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
        healthConditions: Set<String> = emptySet(),
        lang: String = "fr",
        profileId: String = "default",
    ): ScanResult? {
        val candidates = dao.findBetterInCategory(
            category       = scan.product.category.key,
            minScore       = scan.audit.score,
            excludeBarcode = scan.barcode,
            profileId      = profileId,
        ).mapNotNull { toDomain(it) }
        return candidates.firstOrNull { candidate ->
            val allergenHits = if (allergens.isNotEmpty()) checkUserAllergens(candidate.product, allergens, lang) else emptyList()
            // Previously only allergens/diet were re-checked - a diabetic or IBS
            // user could be shown a "better score" alternative that itself
            // tripped their own health condition (e.g. a high-sugar candidate for
            // a diabetic profile), since nothing downstream ever re-checked it,
            // the exact same class of bug this file's allergen check already
            // fixed once for allergens specifically.
            allergenHits.isEmpty() &&
                checkDiet(candidate.product, dietKey, lang).compliant &&
                healthConditionCautions(candidate.product, healthConditions, lang).isEmpty()
        }
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
}
