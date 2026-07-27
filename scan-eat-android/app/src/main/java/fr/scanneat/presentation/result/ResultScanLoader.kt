package fr.scanneat.presentation.result

import fr.scanneat.data.repository.scan.ComparisonRepository
import fr.scanneat.data.repository.scan.ComparisonResult
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

// Only worth suggesting an alternative below this grade — A/B/A+ are already a
// good choice, and surfacing one for every single scan would just be noise.
private val ALTERNATIVE_ELIGIBLE_GRADES = setOf(Grade.C, Grade.D, Grade.F)

// Branded/manufactured categories whose name is a flavor or product label, not
// a raw ingredient - "pairs well with" suggestions don't make sense for them.
private val NON_PAIRABLE_CATEGORIES = setOf(
    ProductCategory.BEVERAGE_SOFT, ProductCategory.BEVERAGE_JUICE, ProductCategory.BEVERAGE_WATER,
    ProductCategory.SNACK_SWEET, ProductCategory.SNACK_SALTY, ProductCategory.CONDIMENT,
)

// Internal sealed type — avoids Pair<Triple<...>> type complexity and null-unsafety
internal sealed class ScanLoad {
    data object Empty : ScanLoad()
    data class  Loaded(
        val scan: ScanResult,
        val personal: PersonalScoreResult?,
        val comparison: ComparisonResult?,
        val pairings: List<String>,
        val alternative: ScanResult?,
        val scoreDelta: Int?,
        val scoreHistory: List<Int>,
    ) : ScanLoad()
}

/**
 * Builds the single-scan [ScanLoad] flow for [ResultViewModel] — extracted verbatim
 * out of the ViewModel (same package) since it owns its own re-entrancy state
 * (comparisonResolved/cachedComparison) that must survive across recompositions of
 * the flow but must only run the arm()/compare() side effect once per scan.
 * ResultViewModel holds one instance of this per scanId/isFreshScan pair and calls
 * [build] from inside its flatMapLatest.
 */
internal class ResultScanLoader(
    private val scanId: Long,
    private val isFreshScan: Boolean,
    private val scanRepo: ScanRepository,
    private val comparisonRepo: ComparisonRepository,
) {
    // arm()/compare() disarm shared comparison state as a side effect, so they must run
    // at most once per scan — WhileSubscribed(5000) can cancel and restart this flow
    // (e.g. backgrounding the screen briefly) which would otherwise re-run the side
    // effect and silently re-arm the current scan as its own comparison baseline.
    private var comparisonResolved = false
    private var cachedComparison: ComparisonResult? = null

    fun build(profile: Profile, lang: String, bioProfile: fr.scanneat.domain.engine.biolism.BiolismProfile) = flow {
        // observeHistoryChecked, not observeHistory - every real navigation call
        // site passes an explicit id (see AppNavGraph.kt), so this fallback only
        // fires for a malformed/argument-less entry into this screen. Same guard
        // as CustomFoodViewModel.latestScan: an id-less "just show me whatever's
        // most recent" fallback shouldn't be able to land on a legacy
        // pre-classifyNonFood() row and run full personalized scoring against it.
        // getById(scanId) - the normal, explicit-id path (tapping a specific row
        // in History, say) - deliberately isn't filtered the same way: the user
        // asked to see that exact row's stored data, which is a different
        // guarantee than "silently pick something recent for me."
        val scan = if (scanId > 0L) scanRepo.getById(scanId)
                   else scanRepo.observeHistoryChecked(limit = 1).first().firstOrNull()

        if (scan == null) { emit(ScanLoad.Empty); return@flow }

        // Was previously called with the default lang="fr", so an
        // English-language user still got all personal-score adjustment
        // reasons (diet/allergen/health-condition call-outs) in French.
        // bioTdeeKcal: same "prefer Biolism's richer TDEE when a valid
        // Biolism profile exists" pattern Dashboard/Diary/Widget already
        // use, so this screen's "100 g uses X% of your daily budget"
        // adjustment agrees with what those screens show for the same day.
        val bioTdeeKcal = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
        val personal   = computePersonalScore(scan.audit, scan.product, profile, lang, bioTdeeKcal)
        if (!comparisonResolved && isFreshScan) {
            comparisonResolved = true
            cachedComparison = if (comparisonRepo.isArmed.first()) comparisonRepo.compare(scan)
                               else { comparisonRepo.arm(scan); null }
        }
        // Pairing suggestions are meant for a whole/raw ingredient ("tomate",
        // "boeuf") - a branded beverage or sweet snack's name often carries a
        // flavor descriptor ("Vanille", "Fraise") that happens to key an
        // ingredient in the pairings database, producing baking-ingredient
        // suggestions for a soda that has nothing to do with them.
        val pairs      = if (scan.product.category in NON_PAIRABLE_CATEGORIES) emptyList()
                          else findPairings(scan.product.name, limit = 5)
        val alternative = if (scan.audit.grade in ALTERNATIVE_ELIGIBLE_GRADES)
            scanRepo.findBetterAlternative(scan, allergens = profile.allergens, dietKey = profile.diet, lang = lang) else null

        // Prior scans of the same product (matched by barcode when present, else
        // case-insensitive name) — used for the score delta badge and history
        // mini-sparkline. Previously filtered the last 200 (all-product) scan_
        // history rows client-side, which was both wasteful (deserializing 200
        // full product/audit JSON blobs on every reload) and, for any barcoded
        // product, entirely broken: scan_history upserts by barcode (a rescan
        // REPLACEs the existing row in place, see ScanRepository.persist), so
        // there is never more than one row per barcode to find a "prior" one
        // among - the barcode branch of that filter could never match anything.
        // scan_score_history is a separate append-only log written on every
        // persist() specifically so this feature has real data to query.
        val priorScores  = scanRepo.priorScores(scan.barcode, scan.product.name, beforeMillis = scan.scannedAt)
        val scoreDelta   = priorScores.firstOrNull()?.let { scan.audit.score - it }
        val scoreHistory = priorScores.take(5).reversed()  // oldest → newest for the timeline

        emit(ScanLoad.Loaded(scan, personal, cachedComparison, pairs, alternative, scoreDelta, scoreHistory))
    }
}
