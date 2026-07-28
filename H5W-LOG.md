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
