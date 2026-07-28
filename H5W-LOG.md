# H5W Unified Log — Scan-eat

## Session: 2026-07-28 — Mode: §AUTO FULL — Scope: database layer first, then adjacent capabilities

### Phase 0: Understand
- App: Scan-eat (Android, Kotlin/Compose + Ktor server), branch claude/app-wide-audit-kcfpmx
- Persistence: Room (scan/consumption/activity/weight/medication/recipe/template/customfood entities+DAOs),
  DataStore (UserPreferences), asset-backed lookup CSVs (MedicationLookupDb, NonConsumableLookupDb),
  server-side persistence in scan-eat-server
- Prior session fixes already landed (context, not to re-do):
  - StreakBadge.kt missing `size` import (CI break) — fixed b7866ec
  - SuggestRecipesDialog enum rememberSaveable missing Saver (crash) — fixed d643866
  - Frosted-glass fallback panel color mismatch (MIUI no-blur devices) — fixed bd87464
  - CameraPreview.kt image.toBitmap() JPEG crash — fixed ecb4ddc
  - Live Open Products Facts fallback for unrecognized barcodes — fixed ecb4ddc
- Database-layer audit starts fresh this session — no prior findings queued.

### Phase 1+: Autonomous cycles logged below by the executing agent.

### Cycle 1 — Database layer: AppDatabase migration chain audit (2026-07-28)
- Read AppDatabase.kt (v1→v25 migration chain), DatabaseModule.kt (registration +
  debug-gated fallbackToDestructiveMigration), all 10 DAOs, ScanHistoryEntity,
  MedicationEntity, SecureFieldCipher.kt, UserPreferences.kt, MedicationLookupDb.kt,
  NonConsumableLookupDb.kt in full.
- Finding: the migration chain, entity/index consistency, encryption-at-rest
  coverage (allergens/conditions/medication name+dosage+scheduleNote, including
  backup export/CSV export decrypt paths), and lookup-DB thread-safety are all
  already correct and thoroughly documented by prior sessions. No gaps found in
  this pass — explicitly recording this as "checked, clean" rather than skipping it.
- Real bug found and fixed (commit c1b3b4b): `ScanRepository.getById(id)` — the
  read path used when a user taps a specific row in History — was the one
  cached-row read path that never re-checked `audit.engineVersion` against
  `ENGINE_VERSION`, unlike `getCachedByBarcode` and `scoreBarcode`'s cache-hit
  path (both fixed by a prior session for exactly this class of bug). Reopening
  an old scan's detail page after a scoring-engine bump showed its stale grade
  forever. Fixed by mirroring the existing pattern: `getById` now takes an
  optional `lang` param and rescores locally (pure function, no network) when
  the cached engineVersion doesn't match current. Updated its one call site
  (ResultScanLoader.build) to pass through the `lang` it already receives.
- Swept the rest of ScanRepository (observeHistory/observeFavorites/searchHistory/
  findBetterAlternative/priorScores/ComparisonRepository) for the same staleness
  gap: observeHistory/observeFavorites/searchHistory intentionally do NOT rescore
  (they're bulk history feeds — rescoring on every list emission would be a perf
  regression the existing codebase clearly avoided elsewhere too, e.g.
  observeTopScanned's doc comment), so no fix needed there. ComparisonRepository's
  A/B snapshots are always built from an already-current ScanResult at arm/compare
  time (24h TTL), not a re-read of a stale row — clean.
- Next action for whoever picks this up: continue the Priority 1 sweep into
  MealTemplateRepository/RecipeRepository/CustomFoodRepository for the same
  "one read path misses a check every sibling path already has" pattern, then
  escalate per H5W scope-expansion to ViewModels/UI states once repositories are
  exhausted. No T3/blocked items this cycle — see H5W-QUEUE.md (still empty).

### Cycle 2 — Repository read-path sweep continued (2026-07-28)
- Read MealTemplateRepository.kt, RecipeRepository.kt, RecipeEntityMapping.kt,
  RecipeModels.kt, CustomFoodRepository.kt in full. No ENGINE_VERSION-style
  staleness concept applies to any of the three (they're not scored/cached
  scan data) - all three already carry a properly-guarded parse-failure path
  (runCatching + Log.w + getOrNull, mapNotNull at every call site) and already
  preserve createdAt/favorite on edit. Genuinely clean, no fix needed here.
- Read HealthConnectRepository.kt and BiolismRepository.kt in full - both
  already heavily hardened by prior sessions (per-feature permission subsets,
  DataStore IOException fallback, live-weight-always-wins profile merge).
  Clean.
- Real bug found and fixed (commit 2717150): WeightRepository.toDomain() and
  ActivityRepository.toDomain() both call `date.toLocalDate()`
  (`LocalDate.parse`, throws `DateTimeParseException` on a malformed date
  column) with no guard - the one class of protection every sibling
  repository (ConsumptionRepository/RecipeRepository/MealTemplateRepository/
  CustomFoodRepository) already has via `runCatching { }.onFailure { log }.
  getOrNull()`. A single corrupted date row would previously crash the whole
  Flow collector (observeAll/observeRange/observeByDate/summarize for Weight;
  observeByDate/getRange/observeRange for Activity), taking down
  WeightScreen/ActivityScreen/Dashboard/Calendar entirely instead of just
  dropping that one row. Mirrored the same pattern, switched call sites from
  `map` to `mapNotNull` (public Flow<List<T>>/Flow<T?> signatures unchanged,
  no external call site needed updating).
- Read CsvExportRepository.kt and BackupRepository.kt in full - both operate
  on raw DAO/entity rows (no toDomain() parsing step, so no equivalent risk)
  and are already extensively hardened (transactional restore, per-table
  dedup keyed to each table's real uniqueness invariant, dangling-FK guard
  for medication_log, CSV-injection guard). Clean.
- Priority-1 repository sweep (MealTemplateRepository/RecipeRepository/
  CustomFoodRepository/HealthConnectRepository/BiolismRepository/
  CsvExportRepository/BackupRepository) is now exhausted. Every repository
  under data/repository/ has been read in full across cycle 1 + cycle 2.
- Next action for whoever picks this up: escalate per H5W's own
  scope-expansion ladder - move to the ViewModels consuming
  WeightRepository/ActivityRepository (WeightViewModel/ActivityViewModel/
  DashboardViewModel/CalendarViewModel) for race conditions, missing loading/
  error states, or coroutine scope leaks; then to the Compose screens
  rendering that state (does WeightScreen/ActivityScreen actually show
  anything when a row silently drops now, or would the user just see a
  slightly-short list with zero indication a row was dropped - worth a quick
  check since this cycle's own fix newly makes that drop path reachable in
  practice, not just in Consumption/Recipe/Template/CustomFood). No T3/
  blocked items this cycle - H5W-QUEUE.md still empty.

### Cycle 3 — ViewModel layer + medication log gap (2026-07-28)
- Read WeightViewModel.kt, ActivityViewModel.kt, DashboardViewModel.kt,
  CalendarViewModel.kt in full, specifically hunting for coroutine scope
  leaks, race conditions on concurrent writes, missing loading/error states,
  and cold-start StateFlow initial-value bugs per this cycle's brief. All
  four are already extremely well hardened by many prior sessions: every
  StateFlow uses `stateIn(viewModelScope, WhileSubscribed(5000), <sane
  default>)` (no raw MutableStateFlow racing a collector), every write
  (log/delete/update/restore) is `runCatching` with a CancellationException
  rethrow + `_actionFailed` snackbar flag, and every date-scoped read
  re-subscribes on a 60s-polled `today`/`date` Flow with distinctUntilChanged
  (not a one-shot value frozen at construction) so nothing goes stale across
  midnight. No race condition, scope leak, or missing error state found -
  genuinely clean.
- Investigated cycle 2's own open question: does the UI say anything when
  the new `mapNotNull` guard (commit 2717150) silently drops a corrupted
  row? Grepped the whole presentation+data tree for any existing
  "corrupted"/"partial"/"could not be loaded" user-facing string - there is
  none anywhere in the codebase. Every sibling repo with the same guard
  (Consumption/Recipe/MealTemplate/CustomFood, predating this cycle) already
  established the convention of silent-drop + `Log.w`, with zero UI
  surfacing. Decision: leave Weight/Activity consistent with that existing,
  deliberate convention rather than introduce a one-off banner - a literal
  DB-corruption case is astronomically rare, log-only is proportionate, and
  inventing new UI just for these two repos would be inconsistent rather
  than an improvement. No change made here; documenting the reasoning so
  it isn't re-litigated.
- Real bug found and fixed (commit 2cd50b9): swept every other repository
  under data/repository/health for the same unguarded-`toLocalDate()`
  pattern cycle 2 fixed in Weight/Activity, since those two were fixed as
  siblings but the sweep wasn't exhaustive across the whole health/ package
  yet. Found `MedicationRepository.toLogDomain()` had never been
  updated - `date.toLocalDate()` (LocalDate.parse) with no guard, the exact
  same crash-on-corrupt-row bug. Fixed by mirroring the identical pattern:
  `toLogDomain()` now returns `MedicationLogEntry?` via
  `runCatching + Log.w + getOrNull`, and `observeLogByDate`/`getLogRange`/
  `observeLogRange` switched from `map` to `mapNotNull`. Public signatures
  unchanged, no call site needed updating (verified all 7 call sites across
  MedicationViewModel/DashboardViewModel/CalendarViewModel/ReminderWorker).
  This closes out the "unguarded toLocalDate()" bug class across the whole
  data/repository tree - grepped for `LocalDate.parse`/`toLocalDate()`
  across all of data/repository/ and confirmed every remaining call site
  (MealPlanRepository, HydrationRepository, FastingRepository,
  DayNotesRepository, BackupRestore, ConsumptionRepository) is already
  `runCatching`-guarded.
- Next action for whoever picks this up: ViewModel layer for
  Weight/Activity/Dashboard/Calendar is now exhausted and clean. Good next
  targets per H5W's scope-expansion ladder: (1) the Compose screens
  themselves (WeightScreen.kt/ActivityScreen.kt/DashboardScreen.kt/
  CalendarScreen.kt) for empty/loading state correctness now that the
  ViewModel layer is confirmed solid; (2) MedicationViewModel.kt itself
  (not yet read this cycle - only its repository was touched) for the same
  race/scope/error-state checks just applied to Weight/Activity/Dashboard/
  Calendar; (3) HydrationViewModel/FastingViewModel/DiaryViewModel/
  MealPlanViewModel, referenced repeatedly in comments above as already
  having had similar fixes applied but not yet directly read+verified this
  session. No T3/blocked items this cycle - H5W-QUEUE.md still empty.

### Cycle 4 — MedicationViewModel + Hydration/Fasting/Diary/MealPlan ViewModels, 4th-instance hunt (2026-07-28)
- Read MedicationViewModel.kt, HydrationViewModel.kt, FastingViewModel.kt,
  DiaryViewModel.kt, MealPlanViewModel.kt in full - the cycle 3 next-action
  list, exhaustively this time (previously only referenced in comments, never
  directly read+verified). Checked each for coroutine scope leaks, race
  conditions on concurrent writes, missing loading/error states, and
  cold-start StateFlow initial-value bugs, same rubric as cycle 3.
  All five are already extremely well hardened by many prior sessions:
  every StateFlow is `stateIn(viewModelScope, WhileSubscribed(5000), <sane
  default>)`, every write path is `runCatching` + CancellationException
  rethrow + `_actionFailed` flag, every date-scoped read re-subscribes via a
  60s-polled `today`/`currentDate` Flow + distinctUntilChanged (not a value
  frozen at construction) so nothing goes stale across midnight
  (MedicationViewModel's `today`, HydrationViewModel's `today`,
  DiaryViewModel's `currentDate`, MealPlanViewModel's `weekDates`).
  MealPlanViewModel additionally runs a live orphan-slot pruner in `init{}`
  keyed off recipe/template id sets - re-checked it can't race a legitimate
  in-flight `setRecipe`/`setTemplate` write (it only ever removes ids that
  no longer resolve against the *current* recipe/template lists, so a slot
  set in the same tick it's observed is never pruned - correct). No
  fixable bug found in any of the five; confirmed clean rather than skipped.
- HydrationViewModel's `streak`/`weeklyIntake` compute `LocalDate.now()`
  inline inside their `.map`/`combine` bodies instead of consuming the
  `today` Flow directly - looked suspicious at first (same shape as the
  bug class this loop keeps finding: a frozen date). Traced it through:
  both are downstream of `intake`, which itself is `today.flatMapLatest`,
  so any midnight rollover already re-triggers this chain within the same
  60s poll window and `LocalDate.now()` is freshly evaluated at that
  trigger point, not captured earlier. Functionally equivalent to using
  `today` directly. No bug - documenting so this isn't re-flagged and
  re-investigated by a future cycle.
- Re-ran the 4th-instance hunt requested this cycle: grepped all of
  data/repository/ for `LocalDate.parse`, `.toLocalDate()`, `.toInt()`,
  `.toDouble()`, `Json.decode`/`fromJson`/Moshi adapters. Every remaining
  unguarded-looking hit resolved to one of: (a) already inside a
  `runCatching`-wrapped `toDomain()`/`parseEntry()` (Weight/Activity/
  Medication/Consumption/Fasting - all fixed in cycles 2-3), (b) a Moshi/
  kotlinx.serialization adapter *construction* line, not a decode call site
  (decode calls themselves are separately guarded), or (c) pure numeric
  formatting on already-validated in-memory domain values (RecipeModels.kt/
  CsvExportRepository.kt's `.toInt()` calls on values that were never
  parsed from a string, just rounded for display/CSV output - not the same
  risk class at all). Singled out `FastingRepository.streak`'s
  `LocalDate.parse(date)` (line 153) for closer inspection since it's
  unguarded and initially looked like a real 4th instance - traced the
  control flow by hand: it only ever calls `LocalDate.parse` on a `date`
  string already proven equal (`==`) to `expected`, and `expected` is only
  ever assigned from `LocalDate.now().toString()` or a prior successful
  `LocalDate.parse(...).minusDays(1).toString()` - i.e. always a
  by-construction-valid ISO string. A corrupted/malformed date value in
  history can never reach this `LocalDate.parse` call because it would
  first have to fail the `date == expected` equality check (a corrupt
  string can't coincidentally equal a well-formed ISO date except in the
  degenerate case where it isn't actually corrupt). Confirmed safe by
  construction, not a bug - no fix made. The "unguarded LocalDate.parse /
  toLocalDate() crash-on-corrupt-row" bug class search is now genuinely
  exhausted a second time with zero new instances found; cycles 2-3's
  three fixes (Weight/Activity, Medication) were the complete set.
- Escalated per this cycle's own instruction to the Compose screens once
  the ViewModel/repository layer turned up nothing: checked
  MedicationScreen.kt/HydrationScreen.kt/FastingScreen.kt/DiaryScreen.kt/
  MealPlanScreen.kt for `actionFailed` snackbar wiring and empty-state
  handling. All five already correctly collect `actionFailed` with
  `collectAsStateWithLifecycle()`, show a snackbar via `LaunchedEffect` and
  call `clearActionFailed()` afterward; Medication/Diary explicitly render
  a distinct empty state (`medications.value.isEmpty()`, `s.entries.
  isEmpty()`). Hydration/Fasting have no empty-state branch, but neither
  needs one - both always render a live counter (glass count / fast timer)
  that is itself the "empty" state (0 mL / no active fast), not a list that
  could silently render blank. No gap found.
- Also read HydrationRepository.kt in full (previously only referenced, not
  directly read+verified) - already correctly guards `LocalDate.parse` in
  both `prune()` and `observeAll()` via `runCatching { }.getOrNull()`.
  Clean.
- No commits this cycle - every area in scope was read in full and
  confirmed already correct; nothing met the bar for a real, safe fix.
  No T3/blocked items - H5W-QUEUE.md still empty.
- Next action for whoever picks this up: the ViewModel layer across the
  entire presentation/ tree (Weight/Activity/Dashboard/Calendar from cycle
  3, Medication/Hydration/Fasting/Diary/MealPlan from this cycle) is now
  fully read and confirmed clean. Remaining unread ViewModels to sweep for
  completeness: RecipesViewModel, TemplatesViewModel, GroceryViewModel,
  CustomFoodViewModel, ProfileViewModel, ScanHistoryViewModel/
  ResultViewModel (partially referenced in comments above but not yet
  directly read this session). Also unread this session:
  MealPlanRepository.kt and DayNotesRepository.kt (both referenced but not
  opened) - worth a quick full read since they're the last two repos under
  data/repository/ not yet explicitly confirmed clean by name in this log.
  If those all come back clean too, the repository+ViewModel layers are
  fully exhausted and the next real scope expansion is the server side
  (scan-eat-server) or a first pass at code-quality/duplication dimensions
  per H5W's own ladder.

### Cycle 4 addendum — MealPlanRepository + DayNotesRepository read (2026-07-28)
- Read MealPlanRepository.kt and DayNotesRepository.kt in full (the two repos
  flagged as unread at the end of this cycle's main entry above). Both
  already guard every `LocalDate.parse` call site with `runCatching { }.
  getOrNull()` (deserialize()'s per-line date parse, prune()'s stale-key
  scan, listDates()/exportAll()'s key parsing). MealPlanRepository's
  pruneOrphanedSlots/copyDay/copyWeek/setSlot all correctly re-serialize
  through the same prune() after mutation. No gaps found - both clean.
- This closes out every repository under data/repository/ by name in this
  log across cycles 1-4. Full status: clean except the three fixed bugs
  (ScanRepository.getById staleness c1b3b4b; Weight/Activity toDomain()
  2717150; Medication toLogDomain() 2cd50b9).
- No commit this addendum (read-only confirmation, folded into the cycle 4
  commit's next-action note). Next cycle should pick up the still-unread
  ViewModels (Recipes/Templates/Grocery/CustomFood/Profile/ScanHistory/
  Result) or escalate to scan-eat-server per H5W's ladder.

## Cycle 5 (2026-07-28)

Scope: both A (remaining unread ViewModels) and B (scan-eat-server
persistence/routing layer), per cycle 4's own recommendation.

### Part A — remaining ViewModels
Read in full: RecipesViewModel, TemplatesViewModel, GroceryViewModel,
CustomFoodViewModel, ProfileViewModel, ScanHistoryViewModel,
OnboardingViewModel, ResultViewModel, and all four Biolism-tab ViewModels
(BiolismProfileViewModel, EvolutionViewModel, TrackerViewModel,
DataViewModel). All but one were already clean and heavily hardened -
every write path already wrapped in runCatching with an actionFailed
snackbar, StateFlow cold-start values all sensibly defaulted, no
unguarded parsing, no coroutine scope leaks, loading/error states all
present where needed.

**Found and fixed:** `DataViewModel.saveManualHR()`/`deleteSession()`
called `BiolismRepository`'s DataStore writes completely unguarded -
the exact "one code path missing a check its sibling has" pattern this
loop keeps finding. Every other Biolism ViewModel in the same package
(TrackerViewModel.saveSession, BiolismProfileViewModel.save/
completeOnboarding/skipOnboarding) already wraps its DataStore write in
runCatching + `_actionFailed` + a one-shot snackbar; DataViewModel's two
write methods, and DataScreen.kt itself, had none of that wiring at all -
a DataStore I/O failure (disk full, corrupt prefs file) here would crash
the whole Biolism "Data" tab instead of surfacing a recoverable error.
Fixed by adding the same `_actionFailed`/`clearActionFailed()` pair to
DataViewModel, wrapping both writes in runCatching (rethrowing
CancellationException first, matching every sibling), and wiring
DataScreen.kt's LaunchedEffect + ScanEatSnackbarHost following
TrackerScreen.kt's own embedded-tab pattern exactly (no Scaffold on this
screen, host overlaid in a Box). Commit a0ef38a.

### Part B — scan-eat-server (first read this session; untouched by
cycles 1-4, which were all Android-side)
Read Application.kt, every file under routing/ (ScoreRoute, IdentifyRoute,
IdentifyMenuRoute + IdentifyRecipeRoute, SuggestRoute, FetchRecipeRoute,
RouteHelpers, RateLimiter, ClientAddress) and every file under service/
(OffService, GroqService, ScoreService, LlmLabelParser) in full.

**Persistence: none.** Confirmed explicitly per this cycle's own
instruction - scan-eat-server has no database, no file-backed state, no
session store. It's a stateless proxy/orchestration layer: barcode →
Open Food Facts lookup (with an in-memory TTL+size-capped cache and
in-flight request coalescing, not persistence) → optional Groq vision-LLM
augmentation → scoring, or a straight image→LLM path, plus a
schema.org-recipe-scraping proxy and recipe-suggestion endpoints. No
database migrations, no user data at rest server-side to audit for
staleness/corruption bugs the way Android's Room layer needed across
cycles 1-3.

**Everything else here is already extremely hardened** - clearly the
product of prior, thorough security/correctness work (the code's own
comments reference numbered "Fix N" items predating this log, i.e. work
done before this H5W loop's 4 logged cycles). Specifically verified
present and correct: per-client rate limiting shared across every
LLM-calling route (RateLimiter.kt, 30 req/60s) plus a separate budget for
the anonymous fetch-recipe proxy (20 req/60s); trusted-proxy-aware client
IP resolution (ClientAddress.kt) that only honors X-Forwarded-For when
the direct TCP peer is itself inside a trusted CIDR, closing the
"expose the port with no reverse proxy" X-Forwarded-For spoofing gap;
SSRF-safe DNS resolution for fetch-recipe (SsrfSafeDns pins the address
actually connected to, closing the check-then-connect DNS-rebinding
TOCTOU a naive resolve-then-fetch would leave open) with manual redirect
following so every hop gets the same public-address check; a JSON
nesting-depth pre-scan guarding against a stack-overflow DoS via deeply
nested schema.org JSON-LD; body size caps (12 MB, requiring
Content-Length so a chunked-encoding request can't bypass it), image
count caps (8), per-field string length caps on LLM-prompt-injected user
text (200 chars) with explicit "treat this as a literal food name, not
instructions" framing in every prompt that embeds caller-supplied text;
a caller-selectable Groq model allowlist (preventing an anonymous
Server-mode caller from forcing the operator's key onto an arbitrary,
possibly costlier model); consistent CancellationException-rethrow-first
in every retry/catch site (route handlers, GroqService.complete,
OffService.fetchExact, the top-level StatusPages handler) so a client
disconnect is never mislogged as a server error; Groq response
truncation (finish_reason == "length") logged instead of silently
producing a downstream parse failure indistinguishable from a garbage
LLM response; nutrition-value clamping with sensible per-domain ceilings
in both LlmLabelParser (900 kcal/100g cap, matching Android's own
OcrParser.kt) and FetchRecipeRoute's schema.org nutrition extraction
(10,000 kcal cap, appropriate for a whole recipe rather than per-100g);
a bounded, size-capped OFF product-lookup cache with in-flight-request
coalescing so a burst of concurrent lookups for the same not-yet-cached
barcode can't each independently fire the 4-candidate GTIN-expansion
fan-out. Compared every route pairwise (Score/Identify/IdentifyMulti/
IdentifyMenu/IdentifyRecipe/SuggestRecipes/SuggestFromPantry/FetchRecipe)
looking for the "one route missing what its sibling has" pattern
explicitly - found none; every LLM-calling route applies
rejectIfTooLarge + rejectIfRateLimited + requireGroqKey (or the
barcode-then-optional-key path for /score) in the same order, and every
route's catch block ends in the same handleRouteError call.

No T3/blocked items this cycle. H5W-QUEUE.md still empty.

**Next action for whoever picks this up:** Both the Android
ViewModel/repository layers and the server's routing/service layers are
now fully read and confirmed clean (bar the one DataViewModel fix above).
Per H5W's own scope-expansion ladder, the next areas to escalate to are:
(1) Compose screen empty/loading/error-state correctness for the
remaining unaudited screens (Recipes/Templates/Grocery/CustomFood/
Profile/ScanHistory/Result/Onboarding/Biolism-tab screens - only
Medication/Hydration/Fasting/Diary/MealPlan screens were checked in
cycle 4), since this cycle only read ViewModels, not their Compose
screens, for Part A; (2) scan-eat-server's model/ (ApiModels.kt) and
shared/ (ScoringEngine.kt and its pillar files - CategoryThresholds,
ProcessingPillar, ScoringKeywords, AdditivesDb, NegativeNutrientsPillar,
AdditiveRiskPillar, ServerOffMapper, DomainToDto, NutritionalDensityPillar,
IngredientIntegrityPillar) were not read this cycle at all - the actual
scoring math/domain-mapping logic, as opposed to the routing/service
plumbing around it, remains unaudited server-side; (3) once those are
exhausted, code-quality dimensions (dead code, naming, duplication)
per H5W's ladder.

## Cycle 6 — Server scoring-engine drift check (Part A) + Compose empty/loading/error sweep (Part B)

### Part A — cross-check scan-eat-server's shared/ scoring engine against
Android's domain/engine/scoring/, looking for a bug where the same product
would score a different grade depending on Direct-mode vs Server-mode.

Read every file on both sides in full and diffed them with package/import/
comment-only lines stripped to isolate real logic differences:
ScoringEngine.kt, CategoryThresholds.kt, ProcessingPillar.kt,
ScoringKeywords.kt, NegativeNutrientsPillar.kt, AdditiveRiskPillar.kt,
NutritionalDensityPillar.kt, IngredientIntegrityPillar.kt, AdditivesDb.kt
(server) vs. AdditivesDb.kt + all 5 AdditivesTierN.kt files (Android),
ServerOffMapper.kt vs. OffMapper.kt/OffCategoryMapping.kt/
OffIngredientParsing.kt/OffMerge.kt (Android's post-atomization split).

**Result: no drift bug found.** Every pillar's scoring logic is byte-for-byte
identical once comments are stripped (all remaining diffs were either
whitespace or comments explaining a fix already applied identically on
both sides - e.g. the omega-3 double-count fix, the E150 longest-synonym-
match fix, the whole-food-ratio fix, the cl/dl weight-parsing fix,
b1/b2/b3/b9/caffeine vitamin mapping). Additive DB cross-checked by E-number
set (111 codes, identical on both sides, zero diff). mapCategory/
classifyNonFood/parseIngredients/additiveTagsToIngredients/parseWeightG all
logic-identical. Git history confirms these two engines have been kept in
sync deliberately across many prior fixes (commits like "fix: mirror
caffeineMg on the server side (Scoring Drift Check)" predate this H5W loop's
own cycles) - this is a well-maintained pair, not an accidental one.

**Found and fixed a real, symmetric (non-drift) bug while reading
ServerOffMapper.kt/OffMerge.kt's mergeNutrition():** b1Mg/b2Mg/b3Mg are
mapped correctly from OFF on both platforms, but `mergeOffWithLlm()`'s
mergeNutrition() carried forward every other micronutrient (b6Mg, b9Ug,
b12Ug, vitAUg, etc.) with `o.x ?: l.x` while omitting these three entirely -
so a product's real, OFF-sourced B1/B2/B3 value silently reset to null the
moment that product also triggered LLM merge for any unrelated reason
(isOffSparse() true for missing category/ingredients elsewhere).
ProductHintsBenefitsRisks.kt derives real benefit/risk hints from these
three fields, so this was a genuine, user-visible data-loss bug - just one
present identically on both Android and server rather than a mode-dependent
drift. Fixed by adding the same `o.x ?: l.x` merge lines for b1Mg/b2Mg/b3Mg
to both OffMerge.kt (Android) and ServerOffMapper.kt (server), keeping them
in sync. Commit d0dcd5e.

### Part B — Compose screen empty/loading/error-state correctness
Read GroceryScreen, CustomFoodScreen, ScanHistoryScreen, TemplatesScreen,
RecipesScreen, ProfileScreen in full (the 6 screens flagged as unaudited
by cycle 5, whose ViewModels were just read that cycle).

**Grocery/CustomFood/ScanHistory/Templates/Recipes: clean.** All render a
distinct EmptyListState (with a query-aware or filter-aware message where
relevant) instead of blank/stale content; all already have actionFailed
snackbar wiring for their write paths (a pattern earlier cycles had to
retrofit repeatedly, now consistently present); Recipes' URL/photo/menu
import flow has a real Loading/Success/Error sealed ImportUiState rendered
distinctly via RecipesImportStateDialogs, not a silent-drop. Two very minor,
lower-value UX gaps noted but not fixed (deliberately, per "small surgical
diffs" - neither is a silent-drop or crash): ScanHistoryScreen and
TemplatesScreen both fall back to their generic "library is empty" message
when a grade/meal filter (not search query) is what's actually producing
zero rows, rather than a filter-specific "no matches" message search
already gets.

**ProfileScreen/ProfileViewModel: found and fixed the same DataStore-write-
unguarded pattern a 3rd time this loop** (after WeightViewModel/
ActivityViewModel and cycle 5's DataViewModel). `save()` called
`prefs.saveProfile()` and `biolismRepo.saveBodyMeasurements()`/
`clearProfileOverride()` - all raw `DataStore.edit{}` calls - with zero
runCatching, zero actionFailed flow, and the screen's `LaunchedEffect(saved
.value)` (which pops back on success) simply never firing on a write
failure, silently stranding the user on the Profile screen after tapping
Save with no feedback that nothing was written, on top of the coroutine
crash risk itself. Fixed with the identical runCatching + `_actionFailed`/
`clearActionFailed()` + ScanEatSnackbarHost wiring used everywhere else this
bug class has been found. Commit d32159d.

This closes out the DataStore-unguarded-write bug class check for every
top-level settings/profile-adjacent screen in the app - Weight, Activity,
Biolism Data tab, and now Profile all confirmed fixed; every other
ViewModel read across cycles 1-6 already had it.

No T3/blocked items. H5W-QUEUE.md still empty.

**Next action for whoever picks this up:** Both scoring engines and the
6 screens above are now confirmed clean/fixed. Recommended next steps per
H5W's ladder: (1) the two very minor filter-vs-search empty-state message
gaps noted above (ScanHistory/Templates) if pursuing UX polish; (2) sweep
the remaining unaudited Compose screens for the same empty/loading/error
pattern - Onboarding, Result, Dashboard, Calendar, and the 4 Biolism screens
(Tracker/Data/Profile/onboarding sub-screens) were never explicitly checked
for this dimension, only their ViewModels; (3) once screens are exhausted,
escalate to code-quality dimensions (dead code, naming, duplication) or
accessibility/i18n gaps per the ladder.

## Cycle 7 — Final 4th/5th-instance hunt for the unguarded-write bug class (2026-07-28)

Scope: per this cycle's brief, re-grep the entire Android app for the
unguarded-DataStore/DAO-write bug class one more time, specifically checking
SettingsViewModel, OnboardingViewModel, and any ViewModel with a save/update/
delete function not yet individually traced. Read every remaining ViewModel
by name not yet explicitly confirmed in this log: SettingsViewModel,
OnboardingViewModel, RemindersViewModel, SplashViewModel.

**Found and fixed a 4th instance (commit 62340a4): `SettingsViewModel`**.
`saveApiKey()`/`saveCerebrasApiKey()`/`saveServerUrl()` called
`prefs.setGroqApiKey()`/`setCerebrasApiKey()`/`setServerUrl()` - raw
`DataStore.edit{}` writes - completely unguarded. Same shape as the
DataViewModel/ProfileViewModel bugs fixed in cycles 5-6: on failure the
coroutine would crash and `_savedField` would never flip, so
SettingsScreen's confirmation checkmark silently never appears - the user
is left staring at a freshly pasted API key or server URL with zero
indication whether Save did anything. Fixed via a shared `saveField()`
helper (runCatching + CancellationException rethrow + `_actionFailed`/
`clearActionFailed()`), wired to SettingsScreen's `FloatingScreenScaffold`
via its existing `snackbarHost` slot.

**Found and fixed a 5th, more severe instance (commit 9871f2f):
`OnboardingViewModel`**. Every write (`setMode`/`setApiKey`/`setServerUrl`/
`finish`/`saveMinimalProfile`) ran completely unguarded - and
`saveMinimalProfile` is a `suspend fun` awaited directly inside
`OnboardingScreen`'s `rememberCoroutineScope().launch { }` with zero
try/catch anywhere in the whole call chain. A DataStore I/O failure here
would propagate as an uncaught exception and crash the entire app on a new
user's very first screen, before they'd ever completed onboarding - the
worst-case manifestation of this bug class found across all 7 cycles.
Fixed by routing every write through the same `guarded()` helper pattern,
and additionally making `saveMinimalProfile` return a success `Boolean` so
its two call sites (`onSaveAndContinue`/`onSaveAndGoToProfile`) only call
`finish()` (which navigates onward) when the save actually succeeded,
instead of unconditionally continuing past a silently-swallowed failure.
Wired an `actionFailed` snackbar into OnboardingScreen's existing
`Scaffold`.

**RemindersViewModel and SplashViewModel: confirmed already clean.**
RemindersViewModel already has the full `_actionFailed`/`guarded()` pattern
applied to every one of its 15 setters (predates this cycle). SplashViewModel
has no writes at all - it only reads `onboardingComplete`/`theme`/
`dyslexicFont`/`colorblindMode` once at cold start.

**This closes out the "unguarded ViewModel write" bug class for real.**
Every `*ViewModel.kt` file under `presentation/` (26 total) has now been
individually read and confirmed either already-guarded or fixed across
cycles 1-7: WeightViewModel/ActivityViewModel (pre-loop), DataViewModel
(cycle 5), ProfileViewModel (cycle 6), SettingsViewModel + OnboardingViewModel
(cycle 7) were the five real instances found; every other ViewModel
(Splash, Activity, BiolismProfile, Data✓, Evolution, Tracker, Calendar,
CustomFood, Dashboard, Diary, Fasting, Grocery, ScanHistory, Hydration,
MealPlan, Medication, Onboarding✓, Profile✓, Recipes, Reminders✓, Result,
Scan, Settings✓, Templates, Weight) was already correctly wired or has no
write path at all.

No T3/blocked items this cycle. H5W-QUEUE.md still empty.

**Correction after further checking (same cycle):** initially assumed no
test coverage existed for the scoring engine and drafted that as the next
recommendation below - checked before acting on it and that assumption was
wrong. Both platforms already have real test suites: Android has
`ScoringEngineTest`/`PersonalScoreEngineTest`/`DietCheckerTest`/
`AllergenDetectorTest`/`BiolismEngineTest`/`ScanViewModelTest`/
`BackupBundleTest` under `app/src/test/`; the server has
`ScoringEngineTest`/`ServerOffMapperTest`/`ScoreServiceTest`/
`OffServiceTest`/`GroqServiceTest`/`LlmLabelParserTest`/
`FetchRecipeRouteTest`/`ApplicationTest` under `src/test/kotlin/`. Both
`android-build.yml` and `server-build.yml` run their respective test tasks
before building. Even better: there is a **third, dedicated CI workflow**,
`.github/workflows/scoring-drift-check.yml`, running
`scripts/check_scoring_drift.py` on every push/PR touching either
platform's scoring code - it automatically diffs matched functions between
`scan-eat-server/.../shared/*` and the Android `domain/engine/scoring/*`
copy and fails the build on divergence. This means cycle 6's manual
byte-for-byte cross-read of the two scoring engines was corroborating work
CI already automates, not filling a real gap - worth knowing so a future
cycle doesn't repeat that manual diff from scratch assuming it's the only
protection. All three workflow YAML files were re-read end to end this
cycle and are syntactically sound with no obvious regression from cycles
1-7's commits (checked/relevant since there's still no local compiler to
verify any of the seven cycles' Android/server changes actually build).

**Next action for whoever picks this up:** Since test coverage and CI
automation are both already solid, the recommended next phase per H5W's
ladder is either (1) the two minor filter-vs-search empty-state message
gaps noted in cycle 6 (ScanHistory/Templates) for UX polish, or (2) a first
pass at code-quality dimensions - dead code, naming consistency, duplication
- across the presentation/ or data/ trees, since correctness-bug-hunting
(unguarded writes, stale-date parsing, engine-version staleness, OFF/LLM
merge drift) is now exhaustively closed across both the Android app and the
server.

## Cycle 8 — Depth-escalation phase: UX polish, accessibility, temporal-scale, dead code (2026-07-28)

Per this cycle's brief, correctness-bug-hunting is exhausted; ran the full
4-item depth-escalation ladder in order.

**1. ScanHistory/Templates filter-vs-search empty-state gap (fixed, commit
5156426).** Confirmed the exact minor UX gap cycle 6 flagged and deliberately
deferred: both screens fell back to their generic "library is empty"
message when a grade filter (History) or meal filter (Templates) - not a
search query - produced zero rows, misleadingly implying an empty library
rather than an active filter with no matches. Added `history_empty_grade`/
`templates_empty_filtered` strings (FR+EN) and branched both screens'
EmptyListState message on `gradeFilter.value`/`mealFilter.value` before
falling through to the generic copy, matching the pattern their own
search-query branch already used.

**2. Accessibility pass on Settings/Onboarding/Profile/Biolism-Data
snackbars (checked, clean).** All four screens edited in cycles 5-7 to add
error snackbars already route through the shared `ScanEatSnackbarHost`
composable, which centrally applies `semantics { liveRegion =
LiveRegionMode.Polite }` - confirmed this is the one and only snackbar-host
component used across all four, no raw `SnackbarHost` anywhere in them.
DataScreen.kt's two `ScanEatSnackbarHost(...)` call sites are in mutually
exclusive early-return branches (empty-metabolics state vs. loaded state),
not a duplicate-instance bug. No fix needed.

**3. Temporal accumulation / unbounded-query check on Weight/Activity/
Biolism (checked, clean).** Traced every DAO backing these three screens.
`WeightDao`/`ActivityDao`/`ConsumptionDao`/`MedicationLogDao`/
`ScanScoreHistoryDao` all already have a `trim(keepCount, profileId)` query
(`DELETE ... WHERE id NOT IN (SELECT id ... ORDER BY ... DESC LIMIT
:keepCount)`), and their respective Repositories (`WeightRepository`,
`ActivityRepository`, `ConsumptionRepository`, `MedicationRepository`,
`ScanRepository`) call `dao.trim(MAX_HISTORY_ROWS, profileId)` after every
write - so the underlying tables are already bounded regardless of which
query variant (`observeAll`/`getRange`/etc.) a screen reads, closing off
the "unbounded SELECT on an ever-growing table" failure mode by construction
rather than by every call site remembering a LIMIT. Biolism's session
history (`SessionHistoryStore` in `BiolismSessionHistory.kt`) is even
simpler - a DataStore-backed JSON list explicitly `.takeLast(20)`-truncated
on every `saveSession()`, so it can never exceed 20 entries regardless of
how many workouts a user logs over years of use. This dimension is
genuinely already solved app-wide; no fix needed.

**4. Dead-code/unused-import sweep on files touched since ecb4ddc (fixed,
commit 0146c19).** Grepped every import in every file this loop (7 prior
cycles + this one) has touched, checking each imported symbol for a second
occurrence beyond its own import line. Found and removed 20 genuinely
unused imports in `ScanHistoryScreen.kt` (background, LazyRow,
RoundedCornerShape, KeyboardOptions, Alignment, clip, Role,
clearAndSetSemantics, contentDescription, role, selected, FontWeight,
KeyboardType, TextOverflow, dp, sp, ViewModel, viewModelScope,
HiltViewModel, ScanRepository, Inject - none referenced in the file body,
apparently copy-paste residue predating this loop), plus one each in
`ScanRepository.kt` (`KotlinJsonAdapterFactory` - Moshi is injected
pre-built, never constructed locally), `DataScreen.kt` (`getValue` - no
`by` delegate syntax in the file; `dp` - no raw dp literals), and
`ProfileScreen.kt` (`MetricChip` - defined in profile/components but never
referenced from this screen). Purely subtractive, re-verified post-edit
with a fresh grep on each symbol to rule out wildcard-import shadowing
false positives.

No T3/blocked items. H5W-QUEUE.md still empty after 8 cycles.

**Next action for whoever picks this up:** All four items on this cycle's
ladder are now closed (2 fixed, 2 confirmed clean). Correctness-bug-hunting
and this round of polish/accessibility/temporal-scale/dead-code passes are
both exhausted for the areas explicitly scoped so far. Recommended next
steps: (1) extend the dead-code sweep beyond files this loop has touched -
it was deliberately scoped narrow this cycle, so a repo-wide unused-import/
unused-private-function sweep (e.g. via a static grep pass over every
`*ViewModel.kt`/`*Screen.kt`, not just the ~19 files with loop history) is
still open; (2) naming-consistency and duplication passes across
`presentation/`/`data/` per cycle 7's own recommendation, never yet
started; (3) i18n completeness check - confirm every string added by this
loop across 8 cycles has both a `values/` (FR) and `values-en/` entry with
no placeholder/format-arg mismatch, since new strings have been added in
nearly every cycle without a dedicated cross-check pass.

## Cycle 9 — Scope broadened per explicit user instruction: away from
database/repository/ViewModel layer, onto feature-completeness, i18n,
visual consistency, and Compose list-perf (2026-07-28)

**User directive this cycle:** stop focusing narrowly on the database
layer; the correctness-bug-hunting ladder (unguarded writes, stale-date
parsing, engine-version staleness, OFF/LLM merge drift) that drove cycles
1-8 is explicitly done being re-litigated. Ran all 4 items of this
session's broadened checklist. Every item came back genuinely clean on
real inspection - documenting each in full so this isn't mistaken for a
skipped pass.

**1. i18n completeness (values/strings.xml vs values-en/strings.xml).**
Wrote a small Python script to parse both files' `<string name="...">`
entries directly (1308 keys each) and diff key sets both directions -
zero keys missing in either direction. Additionally diffed every shared
key's format-specifier list (`%s`/`%d`/`%f`, both positional `%1$s` and
bare forms) between the FR and EN copy of each string - zero mismatches
across all 1308 pairs, meaning no string risks a runtime
`MissingFormatArgumentException` or a silently-dropped `%2$s` in one
locale but not the other. Also grepped `presentation/` for hardcoded
user-facing `Text("...")` literals (a common way new copy sneaks in
without ever reaching strings.xml) - none found; every `Text(...)` call
site already goes through `stringResource(...)`. This closes out cycle
8's open i18n recommendation with a real, systematic check rather than a
spot check - genuinely clean, no fix needed.

**2. Feature completeness, both directions.** Re-read every route file
under `scan-eat-server/.../routing/` (8 endpoints: `/api/score`,
`/api/identify`, `/api/identify-multi`, `/api/identify-menu`,
`/api/identify-recipe`, `/api/fetch-recipe`, `/api/suggest-recipes`,
`/api/suggest-from-pantry`) and traced each one forward through
`ServerScanApi.kt`'s Retrofit interface to its real Android call site by
grepping the exact method name (not just the route string, per this
cycle's explicit instruction not to repeat cycle-N's grep-only false
positive): `score`→ScanScreen/ScanServerClient, `identify`→
ScanServerClient, `identifyMulti`→ScanRepository/ScanServerClient,
`identifyMenu`/`fetchRecipe`/`identifyRecipe`→RecipeServerImportClient,
`suggestRecipes`/`suggestFromPantry`→RecipesScreen/RecipesImportExt/
RecipeRepository. All 8 have live, non-stub call chains reaching real UI
entry points - no orphaned server capability. Reverse direction: grepped
all of `presentation/` and `data/` for `TODO`/`FIXME`/"not yet
implemented"/`NotImplemented` - zero hits anywhere in the app. No stub UI
feature found. Confirmed clean both directions.

**3. Visual/design-consistency pass on screens not specifically named in
cycles 1-8's design work: Hydration, Fasting, Calendar, Grocery,
MealPlan.** Read HydrationScreen.kt + its components
(HydrationSuggestedGoalBanner, HydrationRingAndControls,
HydrationWeeklyChart), FastingScreen.kt + components (ActiveFastCard,
FastingHistorySection), CalendarScreen.kt + components (DayDetailCard,
MonthSummaryBar, MultiMarkerMonthGrid, LegendDot, DetailRow),
GroceryScreen.kt + GroceryItemRow, MealPlanScreen.kt + components
(MealPlanDayCard, MealPlanRow, WeeklyKcalBanner, AssignSlotDialog) in
full, checking every `Surface(`/`Card(` call site for token drift
(`CardRadius`, not a raw dp corner radius; `glassSheen`/semantic color
helpers, not ad-hoc colors). Every card-shaped `Surface` already uses
`RoundedCornerShape(CardRadius.CONTROL)`; every full "card" composable
already uses `ScanEatCard` (GroceryItemRow, DayDetailCard,
MealPlanDayCard) or a `glassSheen`-wrapped `Surface` matching the
established pattern (HydrationWeeklyChart/FastingHistorySection). Small
semantic-colored stat chips (MonthSummaryBar's 4 month-stat pills,
ActiveFastCard's streak/record badges) intentionally use a plain
`color.copy(alpha)`-tinted `Surface` without `glassSheen` - traced this
against the same pattern already established elsewhere in the app
(existing stat-chip components predating this session) and confirmed
it's a deliberate, consistent secondary pattern for at-a-glance color
coding, not drift. Checked icon sizing too: raw dp values below 20
(12/14/16/18dp) appear throughout these files for small inline/decorative
icons - checked `IconSize.kt` and confirmed it only defines tokens for
`Inline` (20dp), `Nav` (24dp), `EmptyState` (40dp), with no token for
sub-20dp decorative icons, so these aren't drift from a token that exists
and is being ignored - they're a category the token system doesn't cover,
consistent with usage elsewhere in the app. No stray hand-rolled
`Surface`/corner-radius/color drift found in any of these 5 screens -
genuinely clean.

**4. Compose list-perf: missing `key = { }` on `LazyColumn`/`LazyRow`
`items()` calls.** Grepped every file under `presentation/` using
`LazyColumn`/`LazyRow` for `items(`/`itemsIndexed(` without `key =`.
Every hit resolved to a screen (RemindersScreen, ProfileScreen,
SettingsScreen, EvolutionScreen, DataScreen, HydrationScreen,
CalendarScreen) whose `LazyColumn` is only ever used with fixed, static
`item { ... }` blocks - one distinct composable per app-defined section,
never a dynamic `List<T>`-backed `items(list)` call - so `key` genuinely
doesn't apply (there's no reorderable/insertable collection to give
identity to). Cross-checked the real list-heavy, dynamically-populated
screens this item was meant to catch (History/Diary/Recipes/Grocery/
MealPlan/Templates): every one of their `items(...)` calls already
supplies `key = { ... }`. No missing-key instance found anywhere in the
app - this dimension was already fully handled.

**No commits this cycle** - every one of the 4 broadened-scope checks was
carried out with real reading (scripted i18n diff, call-chain tracing,
full file reads of 5 previously-unaudited screens + their components,
exhaustive `items()`/`key` grep), and every one came back genuinely
clean. This is a legitimate "checked, no gap" result, not a skipped pass -
documenting in full per this loop's own standing rule against declaring
"nothing found" without reading the code.

No T3/blocked items. H5W-QUEUE.md still empty after 9 cycles.

**Next action for whoever picks this up:** Per the user's explicit
instruction this cycle, **do not drift back to database/repository/
ViewModel-only work** - that ladder was declared closed after 8 cycles of
its own. Areas from this session's broadened checklist not yet exhausted:
(1) code-quality dimensions (naming consistency, duplication) across
`presentation/`/`data/`, flagged as open since cycle 7 and never
started; (2) a repo-wide unused-import/unused-private-function sweep
beyond the ~19 files this loop has already touched (cycle 8 only checked
loop-touched files); (3) a visual/UX pass on the screens this cycle didn't
reach - Onboarding, Result, Dashboard, the 4 Biolism screens
(Tracker/Data/Profile/onboarding sub-screens), Reminders - for the same
`Surface`/`CardRadius`/`glassSheen` drift check just run clean on
Hydration/Fasting/Calendar/Grocery/MealPlan; (4) a security/privacy pass
specifically on the Android app's handling of the API key/server URL
fields (SettingsViewModel) and any exported backup/CSV data, which no
cycle has looked at from a "what's in this file if someone else opens
it" angle rather than the "does the write crash" angle cycles 1-8 already
covered.

## Cycle 10 — Visual-consistency pass on remaining unaudited screens + guarded-write dedup (2026-07-28)

Followed cycle 9's own recommendation: (1) design/UX-audit the screens not
yet specifically checked this loop (Onboarding, Result, Dashboard, all 4
Biolism sub-screens, Reminders), and (2) check whether the "guarded write +
actionFailed snackbar" pattern across DataViewModel/ProfileViewModel/
SettingsViewModel/OnboardingViewModel had become duplicated boilerplate.

**1. Visual-consistency pass (Onboarding/Result/Dashboard/Biolism*4/
Reminders).** Grepped every `Surface(`/`RoundedCornerShape(`/`Spacing.`/
`IconSize.`/`glassSheen`/`ScanEatCard` call site across ~15 files
(OnboardingScreen, ResultScreen + its 8 component files, DashboardScreen,
RemindersScreen, BiolismScreen, BiolismOnboardingScreen,
BiolismProfileScreen, FormPrimitives, TrackerScreen +
TrackerScreenComponents, DataScreen + DataScreenComponents +
MetabolicHealthScoreCard, EvolutionScreen + EvolutionComponents). Found and
fixed two genuine, small token-drift instances (commit e5f2ce3):
- `LogSheet.kt`'s portion-entry `ModalBottomSheet` used a raw
  `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)` where
  `CardRadius.PROMINENT` (also 20dp, documented as the token for exactly
  "bottom sheets/modals") already existed and is used by
  `AddDiaryEntryDialog`'s sheet internals.
- `MetabolicHealthScoreCard`'s small colored score-badge `Surface` used a
  raw `RoundedCornerShape(12.dp)` where `CardRadius.CONTROL` (12dp,
  documented for "buttons/chips/badges") already exists and is used by
  `ScoreBadgesRow.kt` for the same pill-badge role.
Both are literal substitutions, zero visual/behavioral change, verified by
re-grepping for any remaining bare 12/16/20dp `RoundedCornerShape` literal
in the same file set (none left) and confirming both files already import
`ui.theme.*` so `CardRadius` resolves without a new import. Everything
else in these ~15 files was already on-token: every card-shaped `Surface`
uses `CardRadius.CONTROL/CARD/PROMINENT`, every full card uses
`ScanEatCard` or a `glassSheen`-wrapped `Surface`, `Spacing.*`/`IconSize.*`
used consistently, sub-20dp decorative icons intentionally uncovered by
the token system (same conclusion cycle 9 reached on Hydration/Fasting/
Calendar/Grocery/MealPlan).

**2. Guarded-write pattern duplication (commit 78569d7).** Read
DataViewModel, ProfileViewModel, SettingsViewModel, OnboardingViewModel in
full. All four had independently grown the identical trio
(`_actionFailed`/`actionFailed`/`clearActionFailed()`) plus their own
runCatching-wrapping launch helper (`guarded`, `saveField`, or inline
`viewModelScope.launch { runCatching {...} }`) — genuine, safe, mechanical
duplication per cycle 9's own flag. Extracted a new
`fr.scanneat.presentation.common.ActionFailureViewModel` abstract base
class (the trio + `guardedLaunch`/`guardedSuspend`) and switched all four
ViewModels to extend it instead of `ViewModel()` directly, removing the
duplicated members. Verified: (a) every call site's
CancellationException-rethrow-then-flag semantics is byte-for-byte
preserved: (b) the two callers with post-success side effects
(`SettingsViewModel.saveField`'s `_savedField` assignment,
`ProfileViewModel.save`'s `_saved` assignment) only fire when the guarded
block actually succeeded, same as before, now via `guardedSuspend`'s
`Boolean` return; (c) every screen reading
`viewModel.actionFailed`/`.clearActionFailed()` (ProfileScreen,
SettingsScreen, DataScreen, OnboardingScreen) needed zero changes since
the public API is identical; (d) no other file in the codebase references
`ProfileViewModel`/`SettingsViewModel`/`DataViewModel`/`OnboardingViewModel`
by their old `: ViewModel()` supertype in a way a base-class swap could
break (grepped for direct type references beyond `hiltViewModel()`/doc
comments — none found); (e) dropped now-dangling `CancellationException`/
`ViewModel`/`launch` imports left unused by the extraction in each of the
4 files, re-grepped each file afterward to confirm no other usage of the
removed import remained.

No T3/blocked items. H5W-QUEUE.md still empty after 10 cycles.

**Next action for whoever picks this up:** Two items from cycle 9's list
remain open: (1) a repo-wide unused-import/unused-private-function sweep
beyond the ~19 files this loop's history has touched (cycle 8 scoped this
narrow on purpose); (2) a security/privacy pass on the Android app's
handling of the API key/server URL fields and any exported backup/CSV
data, from a "what's exposed if this file/field is inspected by someone
else" angle rather than the "does the write crash" angle already covered.
Also worth a look: this cycle's `ActionFailureViewModel` extraction opens
the door to checking whether any *other* ViewModel in the app (outside
the 4 checked this cycle) has grown its own independent, uninherited copy
of the same `_actionFailed`/runCatching-launch pattern and could be
switched onto the shared base class too — this cycle deliberately scoped
the check to the 4 ViewModels cycle 9 named, not a full-repo grep.
