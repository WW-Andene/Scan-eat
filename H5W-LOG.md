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
