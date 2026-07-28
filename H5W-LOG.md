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
