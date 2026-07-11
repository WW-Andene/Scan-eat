# LOG

Decision log for the build-and-audit skill's KILLER-mode improvement loop.
Full task history lives in the session's task tracker (tasks #1-79 at time of
writing); this file only logs decisions made *during* the audit loop itself.

### 2026-07-11 KILLER mode engaged
context:   User: "run killer mode improve the app... pick something to improve
           (UI, UX, Code, Data, Old tools/feature, New tool/feature)... review
           the finding to be sure, fix it, push commit, repeat. take a
           different category every time."
options:   Run one big audit vs. iterate one small, verified fix per category.
decision:  Iterate — one category per round, each round: find → verify →
           fix → commit → push → CI-check before starting the next round.
why:       Matches the explicit instruction and keeps each push
           independently bisectable/revertable if CI catches a regression.
reversal:  n/a (process choice, not a code change)

### 2026-07-11 Round 1 — UX/accessibility
context:   RemindersCard.kt's 3 test-notification IconButtons used
           contentDescription = null despite being functional controls.
options:   Add per-row description vs. a generic shared one.
decision:  Parameterized "Test reminder: %1$s" string, passed each row's
           own label (breakfast/lunch/dinner/hydration/weight).
why:       TalkBack announces the actual control instead of nothing.
reversal:  trivial (string + 3 call-site edits)
verify:    CI green — commit 668624b, run 29138064738.

### 2026-07-11 Round 2 — UI
context:   ScanHistoryScreen.kt, ScanHistoryCard.kt (dashboard), and
           DiaryScreen.kt's DiaryEntryCard all render product/entry names
           with maxLines=1 and no TextOverflow — Compose's default Clip
           hard-cuts long names mid-glyph instead of ending in an ellipsis.
           Common in practice: OFF product names are often long.
options:   Fix one occurrence vs. all three (same root cause, confirmed by grep).
decision:  Fixed all three — added overflow = TextOverflow.Ellipsis + the
           missing import in each file.
why:       Same bug, same fix, all three files display the same kind of data.
reversal:  trivial (one param + one import per file)


### 2026-07-11 Round 3 — Old feature
context:   reminders_channel_name (Android notification-channel display
           name, shown in system Settings) was "Rappels Métabolisme" /
           "Metabolism Reminders" even though it covers meal/water/weight
           reminders that live in Journal, not the Métabolisme module.
           Pre-existing mislabel (was "Rappels Biolism"), preserved by the
           earlier blanket Biolism->Métabolisme rename instead of fixed.
options:   Rename to "Rappels Journal" vs. drop the module prefix entirely.
decision:  Drop it — just "Rappels" / "Reminders". A notification channel
           doesn't need a module scope, and Journal itself isn't the
           reminders' identity either (they're just app-wide reminders).
why:       Fixes the mislabel without inventing a new wrong association.
reversal:  trivial (string value only, channel ID unchanged)

### 2026-07-11 Round 4 — Data
context:   scoreBarcode() serves a cached scan_history hit straight back
           with no check against ScoreAudit.engineVersion — every scoring-
           engine fix (this session shipped several: additive DB, Server
           DTO, UPC-E) never reaches anything already in a user's history.
options:   Force full rescan on version mismatch vs. recompute locally.
decision:  Recompute: scoreProduct(cached.product) is a pure function, no
           network needed — rescore in place when engineVersion differs.
why:       Zero-cost fix, works offline, keeps existing history current
           with every future scoring change automatically.
reversal:  trivial (one branch in scoreBarcode's cache-hit path)

### 2026-07-11 Round 5 — New feature (Favorites)
context:   Task #71 (long pending). "Favorite" a scanned product from
           ResultScreen, find it again via a filter in Scan History.
options:   Dedupe favorites by barcode (product-level) vs. per scan-history
           row (event-level) — existing data model is per-scan-event
           (delete/findById already keyed by row id, not barcode).
decision:  Per-row favorite: `favorite` column on scan_history (migration
           4→5), toggle star in ResultScreen's TopAppBar and per-row in
           Scan History, "Favorites only" FilterChip there as the
           dedicated place to find them.
why:       Matches the existing per-event history model instead of
           introducing a second, product-level dedup concept.
reversal:  schema migration is additive (ALTER TABLE ADD COLUMN), safe to
           leave even if the feature were later reworked.
note:      getById() in ResultViewModel is a one-shot suspend read, not a
           Flow, so toggling doesn't auto-refresh there — added a local
           optimistic override (favoriteOverride) so the star flips
           instantly. Scan History's list *is* Flow-backed (Room
           invalidates on table writes) so no override needed there.

### 2026-07-11 Round 6 — Code
context:   DietChecker.kt's b() and AllergenDetector.kt's a() word-boundary
           regex helpers exclude letters from the boundary but not digits.
           Reused for numeric E-number patterns (e.g. vegan-forbidden
           "E120" for carmine), so E120 matches as a substring inside a
           longer, real, unrelated E-number like E1200 (polydextrose) or
           E1201/E1202 (PVP) — a false vegan-violation flag.
options:   Fix only DietChecker's E120 pattern vs. fix the shared boundary
           helper in both files (same structural flaw, same fix).
decision:  Fixed the helper itself in both files: boundary now excludes
           digits too, not just letters.
why:       Root-cause fix, not a patch for one pattern; same flaw exists
           in AllergenDetector's E22[0-8] (sulfites) pattern too.
reversal:  trivial (character class in one regex per file)

### 2026-07-11 Round 7 — UI
context:   NavigationBarItem's label Text (MainShell.kt bottom nav) has no
           maxLines/overflow inside a hard-fixed 64.dp NavigationBar — an
           earlier round (task #74) renamed "Biolism" (7 chars) to
           "Métabolisme" (11 chars, no space to wrap favorably), making
           2-line wrap/clip far more likely on typical phone widths where
           5 tabs share the screen.
options:   Shrink font size vs. cap to one line with ellipsis.
decision:  maxLines = 1 + TextOverflow.Ellipsis — consistent with the
           round-2 fix for the same underlying class of bug.
why:       Guarantees the fixed-height bar never clips/overlaps regardless
           of label length in any future rename/locale.
reversal:  trivial (two Text params)

### 2026-07-11 Round 8 — UX
context:   Round 5 added a "Favorites only" filter to Scan History but the
           empty-state message only branched on the search query, not on
           the favorites filter — toggling it with zero favorites (the
           default first-use state) shows "No scans recorded", which
           reads as "you haven't scanned anything," not "you haven't
           favorited anything yet."
options:   Generic empty state vs. a dedicated favorites-empty message.
decision:  Added history_empty_favorites, checked before the generic
           history_empty fallback.
why:       Tells the user what to actually do (tap the star) instead of
           implying a broken/empty history.
reversal:  trivial (one string + one branch)

### 2026-07-11 Round 9 — Old feature (task #73)
context:   Journal's Fasting tab (FastingRepository, DataStore "fasting")
           and Biolism's Tracker "Jeûne" checkbox (TrackerViewModel,
           DataStore "biolism_prefs") are two fully independent systems
           sharing the exact same generic label — the user's original
           complaint verbatim ("why is jeune in Tableau but also an
           option in Biolism tab?").
options:   Full merge into one shared fasting-state system vs. relabel to
           make the distinction intentional.
decision:  Relabel only. "Mode jeûne (estimations)" + a disabled-state
           hint explicitly says it feeds hormone/ketosis estimates and is
           separate from Journal's timer.
why:       A real merge means picking one canonical timer/history model
           and migrating the other's DataStore — a bigger, riskier change
           than fits one KILLER round. Relabeling honestly resolves the
           *confusion* (the user's stated complaint) without the merge risk.
reversal:  trivial (strings only)

### 2026-07-11 Round 10 — Data
context:   WeightEntity's unique index is on `date` alone, not
           `(date, profileId)`, even though every DAO/repository in this
           codebase already threads profileId through. Not reachable
           today (no UI passes a non-"default" profileId yet), but the
           day a second profile logs weight on a date the first already
           has, OnConflictStrategy.REPLACE's unique-column conflict would
           silently delete the other profile's row.
options:   Fix now (free, no real dual-profile data to migrate) vs. wait
           until multi-profile actually ships.
decision:  Fix now — composite unique index (date, profileId), migration
           5→6 (drop old index, create new one).
why:       Cost of fixing before real data exists is ~0; cost of fixing
           after is a data-recovery problem.
reversal:  additive migration, safe to leave regardless of future
           multi-profile plans

## Batch of 10 rounds complete (per user: batch CI, don't check every round)
Rounds 1-10: UX(a11y) / UI(ellipsis) / Old-feature(channel name) /
Data(engine-version rescoring) / New-feature(Favorites) / Code(E-number
boundary) / UI(nav label overflow) / UX(favorites empty state) /
Old-feature(fasting disambiguation) / Data(weight_log profile index).
Commits: 668624b 3480840 0d04cb3 7722015 3a94402 8420cb4 be581b6 b06a610
175d5fb <round-10-pending>. CI check due now.

### 2026-07-11 CI fix — round 9 broke the build
context:   values-en/strings.xml:biolism_fasting_status_disabled had an
           unescaped apostrophe in "Journal's" — aapt2 failed with
           "Invalid unicode escape sequence in string" / "does not
           contain a valid string resource". Broke rounds 9 and 10 CI
           (10 inherited 9's break, no new issue of its own).
fix:       Escaped the apostrophe (Journal\'s), matching every other
           apostrophe in both strings.xml files.

### 2026-07-11 App-audit §A1 — hydration goal footer described the wrong formula
context:   HydrationScreen.kt:145 shows the water goal computed by
           HydrationRepository.goalMl() (EFSA flat-rate: 2.5L male/2.0L
           female + 0.5L activity bonus, via BiolismEngine — weight-
           independent), but the footer text next to it said "30 mL ×
           poids corporel" (body-weight formula) — stale copy left over
           from before task #30 switched the actual calculation.
decision:  Updated fr+en footer strings to describe the real formula.
reversal:  trivial (strings only)

### App-audit §B4 — backup restore could clobber unrelated local data
context:   scan_history/consumption_log use autoGenerate Long ids;
           importFromJson() inserted backup rows with their original ids
           via REPLACE. Restoring on a different device (explicitly
           promised in settings_backup_hint) or after new local rows
           exist would silently overwrite whatever local row shares that
           numeric id - real data loss, not the safe merge the old
           comment claimed.
decision:  Reset id=0 on scanHistory/consumption entities before insert
           so Room always assigns fresh ids for these two tables. Every
           other table already uses a stable UUID/slug, unaffected.
tradeoff:  re-importing the same backup twice on the same device now
           duplicates those two tables' rows instead of deduping -
           acceptable; that guarantee was never stated in the UI, unlike
           cross-device restore.

### App-audit §C7 — plaintext Groq API key exposed via Android auto-backup
context:   allowBackup="true" + groqApiKey stored in plaintext DataStore
           means Auto Backup (Google Drive) or adb backup on a debuggable
           device includes the credential with no extra protection. The
           app already has its own in-app JSON export/import (task #67),
           so OS-level backup isn't needed for data portability.
decision:  allowBackup="false".

### App-audit §D4/D5 — full-res capture bitmaps held as thumbnails
context:   ScanViewModel.Bitmap.toPayload() stored the full 1600x1200
           capture as `thumbnail`, rendered in a 64.dp box. ~7.7MB per
           queued photo (ARGB_8888); multi-photo sessions risk real
           memory pressure on lower-end devices.
decision:  Scale down aspect-preserving to max 160px for the thumbnail;
           base64 (OCR upload) still uses the full-res bytes, compressed
           before scaling. Recycle the original once both are derived.

### App-audit §E1/E4 — hardcoded fontSize literals bypassing the type scale
context:   ~15 FilterChip/TextField labels across 8 files hardcoded
           fontSize = 11.sp/12.sp instead of MaterialTheme.typography
           .labelSmall(11sp)/labelMedium(12sp) - exact-value matches to
           existing tokens, clearly meant to be those tokens. Any future
           type-scale change (accessibility, rebrand) silently misses
           these ~15 spots.
decision:  Replaced with the matching typography token in each case;
           left the one 13.sp (ProfileScreen field label) and the
           16sp/26sp/56sp cases (onboarding buttons, score/grade hero
           text) alone - no exact token match / deliberate custom sizing.
           Removed the now-unused `sp` import in 7 files.

### App-audit §F4 — raw English error string leaking into French-first UI
context:   ScanRepository's "Groq API key not configured" (a very common
           first-use error: user hasn't set up their key yet) was
           hardcoded English-only and shown verbatim in the scan error
           banner, even though scoreBarcode/scoreFromImages already
           thread a `lang` param through for exactly this purpose. The
           sibling "Pas de connexion internet" message was hardcoded
           French-only - neither respected `lang`, just in opposite
           languages, confirming this wasn't an intentional choice.
decision:  Added lang-aware offlineMessage()/missingApiKeyMessage()
           helpers, used at all 3 error() call sites in scoreBarcode/
           scoreFromImages. Left "Server URL not configured" (line ~159,
           scoreViaServer) for a follow-up - it doesn't have `lang` in
           scope without a broader signature change, and is a rarer
           Server-mode misconfiguration path.

### App-audit §G1/G2 — reminder Switches announce with no label
context:   RemindersCard's 3 Switch composables (hydration, weight,
           ReminderRow's per-meal switch) sit next to a Text label in a
           Row but carry no contentDescription of their own - TalkBack
           announces just "On/Off, Switch" with no indication of which
           setting. Merging the whole Row's semantics wasn't safe here
           (each row also holds an independent test-notification
           IconButton fixed in an earlier round - merging would have
           swallowed that control's own focus stop).
decision:  Set contentDescription = <the adjacent label> directly on
           each Switch via Modifier.semantics{}, leaving the IconButton
           untouched and independently focusable.

### App-audit §H3 — undersized touch target (self-introduced, round 5)
context:   My own Favorites star IconButton (ScanHistoryScreen) was
           28.dp - below this app's own consistent 32-36dp floor used by
           14 other icon buttons across the app (delete/dismiss/log
           buttons in dense list rows), and below the 48dp Android
           accessibility guideline entirely.
decision:  Bumped to 32dp - the app's own existing floor for this exact
           pattern (compact icon button in a dense list row).
queued:    The broader observation - all 15 spots (32-36dp) sit below
           the 48dp guideline - is a bigger, consistent, deliberate
           design-system choice across the whole app. Not blanket-fixing
           it here: 15 spots in dense list rows without visual
           verification is a real layout-regression risk, and it reads
           as an intentional density tradeoff, not an oversight. Logged
           to QUEUE.md as a design-system-level recommendation, not an
           immediate fix.

### App-audit §I4 — duplicated OutlinedTextField color scheme (17 spots, 7 files)
context:   The exact AccentGreen outlined-field color block was repeated
           verbatim 17 times across 7 screen files. Pure duplication -
           any future color tweak would need 17 synchronized edits.
decision:  Extracted scanEatTextFieldColors() into Colors.kt (the
           existing theme-token file); replaced all 17 call sites via
           exact-string sed (safe: single unambiguous literal). All 7
           files already wildcard-import the theme package, no new
           imports needed.

### App-audit §J1 — French-locale users blocked from entering imperial decimals
context:   BiolismProfileScreen's BioInputUnit (metric<->imperial toggle
           for height/weight/waist/etc.) parsed raw keyboard input via
           input.toDoubleOrNull(), which only accepts a period. On a
           French-locale device (this app's default), the decimal
           keyboard produces a comma - every keystroke with a decimal
           silently reset the field to the previous value. The format
           side ("%.1f".format(...), default-locale) compounded it by
           displaying a comma the parser couldn't read back.
decision:  Normalize comma->period before parsing (input.replace(',',
           '.')); force Locale.US when formatting back so the stored/
           displayed value is always period-based and re-parses on the
           next edit. Left the many read-only display-only "%.Nf".format
           calls elsewhere (BMI, TDEE, burn rate, dispWeight/dispCirc/
           dispHeight overview recap) untouched - those are pure display
           text, a French comma there is correct localization, not a bug.

### App-audit §K5/C4 — network logging interceptor active in release builds
context:   NetworkModule's shared OkHttpClient unconditionally added
           HttpLoggingInterceptor(Level.BASIC), logging every request/
           response line (URLs, status, timing) to Logcat in production
           builds too. Level.BASIC doesn't include headers/body, so the
           Groq API key itself was never exposed by this - but shipping
           network logging unconditionally in release is an unnecessary,
           standard anti-pattern to close.
decision:  Gated the interceptor behind BuildConfig.DEBUG. Debug builds
           keep identical logging; release builds attach no logging
           interceptor at all.

### App-audit §L2 — one LazyColumn items() call missing a key (standardization)
context:   MealPlanScreen's week-day list was the sole items() call in
           the entire app without a `key` param - every other LazyColumn/
           LazyRow list already follows this pattern consistently.
decision:  Added key = { it.toEpochDay() } (LocalDate has a natural,
           stable, unique key). Low individual impact (7 items/week) but
           closes the one inconsistency.

### App-audit §M1 — About screen version strings hardcoded, drift from real constants
context:   settings_about_version hardcoded "v0.1.0" and "v2.2.0" as
           literal text, duplicating BuildConfig.VERSION_NAME (build.
           gradle.kts) and ENGINE_VERSION (ScoringEngine.kt) - both
           already single-source-of-truth constants used elsewhere in
           this codebase. Currently they match by coincidence; the next
           versionName bump silently goes stale here.
decision:  Parameterized the string (%1$s/%2$s), passed BuildConfig.
           VERSION_NAME and ENGINE_VERSION from SettingsScreen.

### App-audit §N1 — 4 hardcoded French strings in WeightScreen bypassing i18n
context:   "Objectif", "vers" (inline in a template string), "Poids (lb)"
           (right next to a properly-localized "Poids (kg)" sibling),
           and "Notes (optionnel)" were all hardcoded French literals in
           WeightScreen.kt - would show French even on an English-locale
           device, breaking the app's own EN localization for this one
           screen's goal/notes/lb-unit UI.
decision:  Added weight_goal_label, weight_goal_delta, weight_field_lb,
           weight_field_notes to both strings.xml files; wired them in.

### App-audit §O4 — Health Connect client pinned to an alpha release
context:   health-connect-client sits on 1.1.0-alpha07 - no API
           stability guarantee, and this session already hit a real
           breaking-change symptom of that exact version
           (Metadata.manualEntry() didn't exist, found via CI failure
           during the original integration).
decision:  Did NOT bump the version blindly - no live CI feedback loop
           this session to verify a newer alpha/beta doesn't reintroduce
           a different break, and Rule 8's "2am apply" test says this
           needs care, not a blind version bump. Added a clear comment
           flagging the risk and what to re-verify before ever bumping.

### App-audit §A1/L2 — ingredient-integrity score divides by a fixed 3, not actual count
context:   scoreIngredientIntegrity's "first 3 whole foods" sub-score
           divided first3Whole by a hardcoded 3.0, not first3.size. A
           single-ingredient whole food (e.g. "Pommes", 1/1 whole)
           scored 1/3*5≈2 instead of the full 5 - simple whole foods
           got penalized purely for having a short ingredient list,
           backwards for a metric meant to reward whole-food simplicity.
decision:  Divide by first3.size (guarded against empty) instead of a
           fixed 3.

### App-audit §B4/L2 — DayNotesRepository's doc comment contradicted the real backup scope
context:   DayNotesRepository.kt claimed "Notes stay on-device; included
           in backup export" - false. BackupModels.kt's own scope
           comment correctly and honestly lists day notes as
           deliberately out of scope ("fold in later"), so the actual
           backup behavior is fine and intentional; DayNotesRepository's
           comment was just stale/wrong, contradicting the real source
           of truth.
decision:  Fixed the wrong comment rather than expanding the backup
           bundle's scope - BackupModels.kt already made a deliberate,
           documented scoping call here; unilaterally adding day notes
           to the bundle (new field + format-version bump) is a bigger
           change than "fix a wrong comment."

### App-audit §C7/L2 — permanently-denied camera permission was a dead end
context:   The "Autoriser" button in ScanScreen's permission-denied UI
           always called permLauncher.launch(...) unconditionally. Once
           Android permanently denies (2nd straight denial on API 30+,
           or "don't ask again"), RequestPermission() silently returns
           false without showing the system dialog again - the button
           would look broken forever with no path to the app's core
           scanning feature.
decision:  Track whether a request has already happened once; if a
           denial comes back with shouldShowRequestPermissionRationale
           == false after that, treat it as permanent and swap the
           button to "Open Settings" (ACTION_APPLICATION_DETAILS_SETTINGS).

### App-audit §D1/L2 — Dashboard recomputed 30-day rollup on every new scan
context:   scanRepo.observeHistory(20) was combined into the same
           flatMapLatest that recomputes weekly rollups, gap-closer
           suggestions, weight forecast, and Biolism TDEE - every single
           new scan re-triggered all of that expensive, logically-
           unrelated work, even though only the recentScans field
           actually needs the scan-history stream.
decision:  Split into heavyState (the 5-flow combine, unchanged
           computation) + a separate lightweight combine(heavyState,
           scanHistory) that merges recentScans via .copy(). A new scan
           now only triggers a cheap copy, not the full recomputation.

### App-audit §E4/L2 — 4 hero-number displays leaned on an undefined displaySmall slot
context:   ScanEatTypography (Type.kt) explicitly tunes every style slot
           it defines but never defined displaySmall - it silently fell
           back to Material3's baseline default. HeroCard, DailyEnergyCard,
           KetosisProcessCard, CalorieBalanceCard all build their hero-
           number style from displaySmall.copy(fontSize=X, fontWeight=Y),
           inheriting an un-tuned lineHeight/letterSpacing instead of
           this app's own deliberate ratio.
decision:  Defined displaySmall (36sp/42sp, ~1.167x ratio consistent with
           the app's other slots) so these 4 hero elements inherit a
           tuned baseline before their per-usage size/weight overrides.

### App-audit §F4/L2 — rejected API key (401/403) surfaced as a raw HTTP status
context:   ScanViewModel's onFailure handler only special-cased
           ProductNotFoundException; a Groq-rejected key (invalid/
           revoked - distinct from the "missing key" case already
           handled in app-audit §F) fell through to e.message, showing
           the user a bare "HTTP 401 " with no indication of what to do.
decision:  Added an HttpException 401/403 branch with a friendly,
           lang-aware message pointing to Settings.

### App-audit §G1/L2 — RadioButton rows unlabeled + nested double-actionable controls
context:   5 RadioButton sites across 4 files (ProfileScreen's
           ActivitySelector, BiolismProfileScreen's activity+ethnicity
           rows, BiolismOnboardingScreen's activity row, OnboardingScreen's
           ModeCard) had the same gap as the earlier Switch fix - no
           accessible label - but here (unlike the Switch+IconButton
           case) there was no independent sibling control, so the
           correct fix is different: 3 sites also had a REDUNDANT nested
           clickable (row/Surface already handled the tap AND the
           RadioButton had its own separate onClick) - two actionable
           elements claiming the same tap.
decision:  4 sites: converted the containing Row to
           Modifier.selectable(role=Role.RadioButton) (merges semantics,
           makes the whole row tappable) + RadioButton onClick=null.
           1 site (OnboardingScreen's Surface-based ModeCard): Surface
           already provides click handling, so just removed the
           redundant nested onClick.

### App-audit §I4/L2 — findAdditive() recomputed 3x per ingredient per scoreProduct()
context:   findAdditive(eNumber, name, category) (AdditivesDb.kt) is called
           independently by ProcessingPillar.kt (2 sites), AdditiveRiskPillar.kt
           (2 sites, incl. countTier1Additives), and IngredientIntegrityPillar.kt
           (1 site) - each pillar re-runs its own O(n) linear + synonym-
           substring scan over the ~95-entry ADDITIVES_DB for the same
           ingredient during a single scoreProduct() call. Real duplicated
           work (§I4), grows with the additive DB this session already
           expanded twice.
decision:  Queued rather than fixed here - the correct fix (memoize once per
           product in ScoringEngine.kt, thread through all 3 pillar
           functions) touches 4 files at once, and there's no CI feedback
           loop active mid-batch to catch a threading mistake blind. Logged
           to QUEUE.md for a dedicated pass with CI available (same
           reasoning as §H3 and §O4 this session).
reversal:  n/a (no code changed, doc/queue only)

### App-audit §O1/L2 — Room schema-export gap projects forward to unvalidated migrations
context:   AppDatabase.kt has exportSchema = true and room.schemaLocation
           configured (build.gradle.kts) since the project's start, but no
           app/schemas/ directory has ever been generated/committed - this
           session's own MIGRATION_4_5 and MIGRATION_5_6 (added earlier
           this session) were never validated against a real prior schema
           via androidx.room:room-testing's MigrationTestHelper, only by
           manual reading. Projecting forward: the next migration added
           without this fixed has the exact same blind-spot, compounding
           each time (Rule 9).
decision:  Documented the gap with a comment at the migrations' actual
           usage site (AppDatabase.kt) plus a QUEUE.md entry. Did NOT
           attempt to generate app/schemas/ myself - confirmed this
           sandbox has neither a reachable Gradle distribution nor
           ANDROID_HOME (gradlew --offline failed on both counts), so
           there's no way to produce a real, correct schema export here
           to commit blind.
reversal:  n/a (comment + queue note only, no code/schema change)

### App-audit §N1/L2 — WeightScreen's sparkline caption still hardcoded French
context:   §N1 (layer 1) fixed 4 hardcoded French strings in WeightScreen
           but missed a 5th in the same file: the sparkline caption
           "Tendance — ${last8.size} dernières pesées" (line 128) - shows
           French on an English-locale device exactly like the 4 already
           fixed, just not caught in that pass since it wasn't in the
           dialog/field area that round focused on.
decision:  Added weight_trend_caption (%1$d placeholder for the count) to
           both strings.xml files, wired via stringResource(...,
           last8.size).
reversal:  trivial (one string + one call-site swap)

### App-audit §M1/L2 — R8 keep rules missed 3 Moshi-reflection classes under data.repository
context:   proguard-rules.pro keeps fr.scanneat.domain.model.**,
           data.remote.api.**, and data.local.db.** for Moshi's kotlin-
           reflect adapter (KotlinJsonAdapterFactory, used app-wide per
           di/NetworkModule.kt's shared Moshi instance - not codegen).
           3 real Moshi-(de)serialized data classes live outside all of
           those keeps: RecipeComponent (RecipeRepository.kt, package
           data.repository.planning), TemplateItem
           (MealTemplateRepository.kt, same package), and OcrParser.kt's
           LlmProductDto/LlmIngredientDto/LlmNutritionDto (package
           data.repository.scan). Debug builds (unminified) never exhibit
           the gap; a release build's R8 pass can rename/strip their
           fields, breaking Recipes persistence, Meal Templates
           persistence, and Groq OCR label-parsing simultaneously - and
           CI here only ever builds/tests the debug variant, so this
           would ship silently broken.
decision:  Added -keep class fr.scanneat.data.repository.** { *; },
           mirroring the existing whole-package keep pattern used for the
           3 other Moshi-adjacent packages - same shape, same risk
           profile as what's already there.
reversal:  trivial (proguard rule addition only, strictly widens keeps -
           cannot break anything that worked before)

### App-audit §L2/L2 — 3 more scanEatTextFieldColors() duplicates missed by the L1 sed pass
context:   §I4 (layer 1) extracted scanEatTextFieldColors() and replaced 17
           exact-string-match occurrences of the AccentGreen outlined-field
           color block via sed. 3 more occurrences existed in
           CustomFoodScreen.kt (search field), ScanHistoryScreen.kt (search
           field), and ProfileScreen.kt (text field row) with identical
           values but different line-wrapping/spacing, so they didn't match
           the single-line literal used by that sed pass and were missed.
decision:  Replaced all 3 with scanEatTextFieldColors(). Left
           CustomFoodScreen.kt's other OutlinedTextFieldDefaults.colors()
           call (0.18f unfocused alpha, not 0.2f) alone - genuinely
           different value, not a duplicate.
reversal:  trivial (one call-site swap per file)

### App-audit §K5/L2 — identifyFood() prompt biased the LLM toward zeroed nutrition
context:   OcrParser.kt's identifyFood() prompt (used for fresh foods/plated
           dishes with no label to OCR) showed the model a JSON schema
           example where energy_kcal was "<estimate>" but every other
           nutrient (fat_g, saturated_fat_g, carbs_g, sugars_g, fiber_g,
           protein_g, salt_g) was a literal 0 - a known LLM failure mode is
           echoing back literal schema-example values instead of treating
           them as format placeholders, especially when the instruction
           ("Return a JSON object with the same schema") doesn't clarify
           they're placeholders. This is exactly the identifyFood() path
           whose whole value proposition is nutrition estimation from
           general knowledge (buildWarnings even labels the result
           "Nutrition estimated by AI") - a model that just echoes the 0s
           silently defeats that purpose for a fresh apple, a plated meal,
           anything without a printed label.
decision:  Made all nutrient fields <estimate> placeholders (consistent
           with buildLabelPrompt's own convention) and added an explicit
           instruction line telling the model to estimate every value from
           typical composition and never emit a literal 0 unless the food
           genuinely lacks that nutrient.
reversal:  trivial (prompt string only, no schema/DTO change)

### App-audit §J1/L2 — French-decimal-keyboard bug still open in 6 more screens
context:   §J1 (layer 1) fixed BiolismProfileScreen's comma/period parsing,
           but the same toDoubleOrNull()-only bug was still live in
           WeightScreen (kgText), ProfileScreen (heightCm/weightKg/
           goalWeightKg), RecipesScreen (newIngGrams/newIngKcal/
           fractionText), LogSheet (portionG), CustomFoodScreen (kcal/
           prot/carb/fat/fib/salt), and BiolismOnboardingScreen (height/
           weight/waist/hip/neck) - every decimal numeric-entry field in
           the app except the one already fixed. On a French-locale
           device the decimal keyboard types a comma, so all of these
           silently reject any non-integer input.
decision:  Same mechanical fix everywhere: input.replace(',', '.') before
           .toDoubleOrNull(). Purely additive/mechanical - no logic
           restructuring, same proven pattern as §J1, safe to apply
           across all 6 files in one pass.
why:       High real-world impact (core data entry: weight logging,
           profile setup, custom foods, recipes, portion logging) for a
           French-first app; low risk since it's the identical, already-
           verified transformation repeated at each call site.
reversal:  trivial (one .replace() per call site, 14 sites total)

### App-audit §H3/L2 — ScanScreen's "edge to edge" comment overstated reality
context:   MainShell's own Scaffold (contentWindowInsets =
           WindowInsets.systemBars) already consumes status/nav-bar
           insets before AppNavGraph renders, so ScanScreen's
           Box.fillMaxSize() only fills the already-inset-adjusted area
           - it never actually extends behind the system bars. The
           comment claimed "edge to edge," which isn't literally true.
decision:  Corrected the comment to describe reality (full-bleed within
           the safe content area, not behind system bars). Did NOT
           restructure the actual inset handling to make it truly
           edge-to-edge - that's a real layout change across MainShell
           + ScanScreen I can't visually verify without a device/
           emulator (Rule 8: reduce scope when blind-apply risk is real).

## Layer 2 complete — 15 categories (A-O), 1 fresh finding each, batch CI due now
Commits b684ab9..<O/L2 pending>: A(ingredient-integrity div-by-actual-count),
B(DayNotesRepository comment fix), C(permanently-denied camera permission),
D(Dashboard scan-history decoupling), E(displaySmall type-scale slot),
F(401/403 API-key error message), G(RadioButton rows unlabeled/double-
actionable), H(ScanScreen edge-to-edge comment), I(findAdditive() triple-
computation - queued), J(French-decimal bug in 6 more screens), K(identifyFood
prompt zero-bias), L(3 more scanEatTextFieldColors dupes), M(R8 keep-rule gap
for data.repository Moshi classes), N(WeightScreen sparkline caption still
French), O(Room schema-export never committed - queued). Combined with layer
1's 15, that's the full 30-finding two-layer pass. CI check due now per user
instruction ("only checking ci after all categories and two layer mode done").

## Layers 1-2 CI-verified green (all 30 commits, b684ab9..5ca162d)
User: "do another 15 categories x 2 layers... you can do deep fix or
significant change like restructuration needed or new implementation/tool/
solution." Proceeding to layers 3-4 with license for bigger, riskier fixes
where the finding genuinely warrants it.

### App-audit §A1/L3 — Dairy-free/Paleo diet checks false-flag coconut/peanut butter
context:   VEGAN's dairy pattern correctly guards beurre/crème with negative
           lookahead (beurre(?! de cacahu[eè]te| d'arachide| de coco),
           cr[eè]me(?! v[eé]g[eé]tale)) so peanut butter and coconut butter
           don't get misread as dairy. DAIRY_FREE and PALEO's dairy patterns
           used bare "beurre"/"crème" with no such guard - a product listing
           "beurre de cacahuète" (peanut butter) or "crème de coco" (coconut
           cream) as an ingredient would be falsely flagged as violating a
           dairy-free or paleo diet, exactly the false-positive class VEGAN
           was already fixed against in the same file.
decision:  Applied the same negative-lookahead exclusions
           (lait/beurre/crème guards) to DAIRY_FREE and PALEO's forbidden
           patterns, matching VEGAN's existing convention.
why:       Real, user-visible false positive - a coconut-cream dessert or
           a jar of peanut butter is core "safe" content for exactly these
           two diets, and getting flagged wrong on core foods undermines
           trust in the whole diet-checking feature.
reversal:  trivial (regex lookahead additions only, no schema/logic change)

### App-audit §B1/L3 — Health Connect weight sync spammed a new record on every log()
context:   WeightRepository.log() upserts locally (one row per day, REPLACE
           by (date, profileId)) but unconditionally called
           healthConnect.writeWeight() on every single call - Health
           Connect's insertRecords() has no update-in-place without a
           clientRecordId, so re-saving/correcting the same day's weight
           left the local store correct while Health Connect accumulated
           a fresh duplicate WeightRecord for that date on every save,
           corrupting the shared platform store's "one weight reading"
           expectation for any other app reading from it.
decision:  Gated the Health Connect mirror to only fire when the rounded
           value actually differs from the existing local entry -
           correctly stops the common no-op-resave case. Did NOT
           implement the full clientRecordId-based upsert (the real,
           complete fix) - researched it (Metadata.manualEntry(clientRecordId,
           clientRecordVersion, device)) but this exact pinned alpha07
           health-connect-client already broke once this session on
           Metadata's factory-method shape (see O/L1), and I have neither
           an active CI loop nor local Android SDK to verify the
           alpha07-specific constructor before committing it blind.
           Queued the full fix for when either becomes available.
why:       Meaningfully reduces real duplicate-record accumulation today
           at zero risk (pure Kotlin comparison, no Health Connect API
           surface touched) while being honest that a genuine correction-
           after-correction case still isn't fully solved without the
           riskier change (Rule 8: reduce scope when blind-apply risk is
           real, same call as O/L1's Health Connect version-bump decline).
reversal:  trivial (one added condition, no API surface change)

### App-audit §C1/L3 — OnboardingScreen showed the Groq API key in cleartext
context:   SettingsScreen's API key field correctly masks input
           (PasswordVisualTransformation + a visibility-toggle IconButton,
           KeyboardType.Password), but OnboardingScreen's own API key
           field - the very first place a user ever types this secret,
           often in a semi-public first-run setting - used a plain
           OutlinedTextField with no masking, no toggle, default keyboard
           type. Real shoulder-surfing/screen-recording exposure risk,
           and inconsistent with the app's own already-correct pattern
           for the identical field elsewhere.
decision:  Mirrored SettingsScreen's exact pattern: masked by default,
           visibility toggle IconButton reusing the existing
           settings_toggle_key_visibility string and Visibility/
           VisibilityOff icons (already used elsewhere in the app, no new
           resources needed).
reversal:  trivial (visualTransformation + toggle state + icon button)

### App-audit §D1/L3 — barcode-candidate OFF lookups ran one network round-trip at a time
context:   fetchOffProduct() loops over barcodeCandidates() (every plausible
           GTIN encoding of a scanned barcode - UPC-E expansion, GTIN-14
           case-code stripping, EAN-13/UPC-A padding variants) and calls
           offApi.getProduct() sequentially, only trying the next candidate
           after the previous one 404s. A UPC-E can (the exact case behind
           this session's earlier Coke-can investigation) can need 3-4
           candidate expansions before the real match - each one a full
           network round-trip stacked serially, adding real latency to
           exactly the scans this session already worked to make succeed
           at all.
decision:  Fire all candidate lookups concurrently via async, but still
           await and return them in original priority order (not
           first-to-complete) - preserves the existing "most-likely-first"
           correctness guarantee (a lower-priority candidate that happens
           to respond faster must never win over an earlier, more-likely
           one) while collapsing N sequential round-trips into 1 round-trip
           worth of wall-clock latency.
why:       These are independent, side-effect-free reads against a public
           API with no per-call cost to the user - concurrency has no
           downside here (unlike the earlier LLM retry loop, which is
           deliberately sequential since it burns real Groq API quota per
           attempt).
reversal:  moderate (structural change to fetchOffProduct's control flow,
           but the priority-order-preserving await loop keeps the exact
           same observable behavior/return value as before - just faster)

### App-audit §E1/L3 — back-arrow icon didn't mirror for RTL despite RTL being enabled
context:   15 files used Icons.Default.ArrowBack (the legacy, non-mirrored
           icon) for every top-bar back button across the app, even though
           P-3 (task #23) explicitly enabled RTL support - a non-mirrored
           left-pointing arrow in an RTL locale points the wrong direction
           (should point right, toward where "back" is on that layout).
decision:  Swapped all 15 to Icons.AutoMirrored.Filled.ArrowBack (auto-
           flips per LayoutDirection) and updated each file's icon import
           accordingly (13 wildcard-import files gained an explicit
           automirrored.filled.ArrowBack import; 2 files with explicit
           per-icon imports had their ArrowBack import line repointed to
           the automirrored package).
reversal:  trivial (icon symbol + import swap only, no behavior change
           in LTR locales)

### App-audit §F1/L3 — GroceryScreen's "copy" feedback was dead state, never rendered
context:   Tapping the copy-to-clipboard action set a `snack` boolean with
           a LaunchedEffect that waited 2s and reset it - the naming and
           timing make clear a confirmation Snackbar was intended, but no
           SnackbarHost/Snackbar ever existed in the composable to read
           that flag. The clipboard copy itself worked; the user got zero
           visual confirmation it happened.
decision:  Replaced the dead flag/effect with a real
           SnackbarHostState + Scaffold's snackbarHost, showing a new
           grocery_copied string on tap via scope.launch { showSnackbar() }.
why:       This is exactly the class of bug this session already noted
           app-wide (no Snackbar/Toast feedback exists anywhere) - fixing
           the one place code already half-attempted it, rather than
           adding a new app-wide feedback system from scratch.
reversal:  trivial (localized to GroceryScreen; removes unused state,
           adds standard Compose Scaffold snackbar wiring)

### App-audit §G1/L3 — veto badge conveyed a critical warning via an unlabeled glyph only
context:   DualScoreRing's personal-score slot shows a bare "✗" Text (no
           semantic label) when a product is vetoed - the single most
           important "this is unsafe for you" signal in the whole result
           screen, conveyed only via an unlabeled Unicode glyph + color
           (FlagRed). TalkBack would read "✗" as some generic symbol name
           at best, giving zero indication of what it means; a
           colorblind user without the red cue gets nothing at all beyond
           an ambiguous cross mark.
decision:  Added Modifier.clearAndSetSemantics { contentDescription = ... }
           on the veto column so TalkBack announces the actual meaning
           ("Veto: incompatible with your profile regardless of score")
           instead of the raw glyph or nothing. Visual "✗" for sighted
           users unchanged.
why:       This is the highest-stakes information on the entire result
           screen (a real safety veto, e.g. trans fats/allergen-adjacent
           risk) - it must not depend on color or glyph-recognition alone.
reversal:  trivial (semantics modifier + 2 new strings, no visual change)

### App-audit §H1/L3 — GTIN-14 case-code handling was unreachable dead code
context:   ScanRepository.gtin14ToEan13() (added earlier this session to
           widen barcode matching to wholesale/case-pack codes) can only
           ever run on a 14-digit input, but ScanScreen's own ML Kit
           analyzer callback filtered out both Barcode.FORMAT_ITF (the
           format ITF-14/GTIN-14 codes actually decode as) and any 14-
           digit result (digits.length in listOf(8, 12, 13) excluded 14) -
           so a real camera scan of a GTIN-14 case code could never reach
           that logic at all, regardless of how correct it is.
decision:  Added Barcode.FORMAT_ITF to the accepted format list and 14 to
           the accepted digit-length list, so the already-built
           GTIN-14-to-EAN-13 conversion path is actually reachable from a
           live scan.
why:       Closes the gap between "logic exists and is tested" and
           "logic can ever run" - the exact kind of platform-compatibility
           issue this category is meant to catch.
reversal:  trivial (2 list-literal additions, no new logic)

### App-audit §I1/L3 — findAdditive() triple-computation, resolved (was queued at I/L2)
context:   Queued at I/L2 as too risky to fix blind (the plan then was
           memoizing once in ScoringEngine.kt and threading the result
           through all 3 pillar functions - a 4-file signature change).
           With deep fixes now explicitly in scope, reconsidered the
           actual smallest safe fix: a memoization cache local to
           findAdditive() itself in AdditivesDb.kt needs zero call-site or
           signature changes in any of the 3 pillar files - the function
           is already a pure function of its 3 params, so caching by
           those exact params is transparent to every caller.
decision:  Added a ConcurrentHashMap<Triple<eNumber,name,category>,
           AdditiveInfo> cache (thread-safe, since scoreProduct() can run
           on any coroutine dispatcher) with a NOT_FOUND sentinel to
           represent cached misses (ConcurrentHashMap rejects null
           values). Renamed the original body to computeFindAdditive(),
           kept as the cache-miss path.
why:       Smaller and safer than the originally-queued plan (Rule 7)
           while fully solving the redundant-computation problem (Rule 9)
           - the exact "cleaner alternative" flagged as worth reconsidering
           when this was queued.
reversal:  trivial (cache is purely additive; deleting it reverts to the
           original uncached behavior with no other code changes needed)

### App-audit §J1/L3 — day/month names followed device locale, not the in-app language
context:   WeightScreen, MealPlanScreen, and DiaryScreen each declared a
           top-level DateTimeFormatter.ofPattern("...") (day/month
           abbreviations) with no explicit Locale - per the JDK,
           ofPattern(pattern) alone resolves Locale.getDefault(), i.e. the
           DEVICE's locale, not this app's own in-app language setting
           (Settings has an explicit language toggle independent of
           device locale, used pervasively via `lang`/prefs.language
           throughout scoring and error messages this whole session). A
           user on an English-locale device who picked French in Settings
           would see French everywhere except these 3 screens' date
           labels, which would render in English.
decision:  Threaded prefs.language into WeightViewModel, MealPlanViewModel
           (newly injected UserPreferences), and DiaryViewModel as a
           `language: StateFlow<String>`; each screen now builds its
           formatter via `remember(language.value) { ofPattern(pattern,
           Locale(language.value)) }` instead of a static top-level val.
why:       Same class of "app-language vs device-locale" bug this session
           already fixed for the scoring/error-message layer - date labels
           were the one presentation surface still silently following the
           wrong source of truth.
reversal:  moderate (adds a constructor param + DI wiring to 2 ViewModels
           that didn't have UserPreferences before; WeightViewModel
           already had it). Fully additive - no existing behavior changes
           for a device whose locale already matches the chosen language.

### App-audit §K1/L3 — OcrParser's last-chance retry used a vision-incompatible fallback model
context:   callWithRetry() unconditionally switched to FALLBACK_MODEL
           (llama-3.3-70b-versatile, text-only) on the final retry
           attempt - but both current callers (parseLabel, identifyFood)
           always send image content parts. A vision request sent to a
           text-only model isn't a genuine extra chance, it's a
           near-guaranteed failure - wasting what should be the last real
           retry opportunity.
decision:  Only substitute FALLBACK_MODEL on the last attempt when the
           request has no image content (hasImages check) - preserves the
           fallback for any future text-only caller while fixing it for
           both current image-bearing callers, which now get 3 genuine
           attempts on the vision model instead of 2 real + 1 doomed one.
why:       A real, verifiable logic bug (not a guess about an unverifiable
           external API) - the fallback model's incompatibility with image
           content is a static fact of the two model names/current
           callers, not something needing a live API call to confirm.
reversal:  trivial (one boolean condition added)

note:      Also considered adding response_format: json_object (Groq JSON
           mode) to eliminate the "unparseable JSON" failure class
           entirely, but declined - can't verify from this sandbox whether
           the pinned vision model supports it, and getting it wrong risks
           a hard 400 on every single scan. Queued in QUEUE.md pending a
           live-API check.

### App-audit §L1/L3 — 3 screens duplicated the same empty-state layout
context:   CustomFoodScreen, TemplatesScreen, and RecipesScreen each
           independently wrote the identical centered icon+message empty-
           state block (40dp Box padding, 40dp icon, 8dp Column spacing,
           OnBackground 0.5f) inside their LazyColumn, varying only the
           icon and message.
decision:  Extracted EmptyListState(icon, message) into a new shared file
           (presentation/ui/theme/EmptyListState.kt), matching the
           existing pattern of small shared composables living there
           (DeleteConfirmDialog.kt). All 3 wildcard-import that package
           already, so no new imports needed at any call site.
reversal:  trivial (pure extraction, identical rendered output)

### App-audit §M1/L3 — CI never exercised the release/R8 build path
context:   android-build.yml only ever ran testDebugUnitTest and
           assembleDebug - the release build type (minifyEnabled=true,
           shrinkResources=true) has never once been built in CI. This is
           the exact gap that let the real R8 keep-rule hole (app-audit
           §M1/L2 - data.repository Moshi classes unprotected) go
           unnoticed; any future R8/shrinker issue would ship silently the
           same way.
decision:  Added an assembleRelease step. No signingConfigs block exists
           for the release build type, so this produces a valid unsigned
           APK - no keystore/secrets needed in CI, purely a build-time
           R8/shrinker correctness check.
why:       Closes the gap directly rather than just documenting it -
           unlike the Health Connect/JSON-mode cases, this needed no
           external API verification, just recognizing the existing
           workflow file's own scope gap.
reversal:  trivial (one workflow step; doesn't touch app source)

### App-audit §N1/L3 — entire scoring-engine flag vocabulary is English-only (discovered, queued)
context:   Investigated whether AdditiveRiskPillar's additive `concern`
           citations (English scientific text) leak into visible red
           flags - they don't (Deduction.evidence is set but never read
           anywhere in the presentation layer, confirmed by an exhaustive
           grep - dead field). But the actual `reason` fields on all 44
           Deduction(...) call sites across the 6 scoring-engine files ARE
           what buildFlags() surfaces as visible red/green flags, and
           every single one is hardcoded English, with zero lang-
           awareness - a much bigger, systemic version of the N/L1/N/L2
           findings (a few missed UI strings), affecting the actual
           safety-relevant flag text on every scan result.
decision:  Did not attempt the fix - correctly localizing this needs
           threading `lang` through scoreProduct() and all 5 pillar
           functions (a signature change to the core, most-tested pure
           function in the app) plus ~44 bilingual string pairs, with no
           local build/test loop in this sandbox to verify a change of
           that size and centrality. Documented precisely in QUEUE.md
           (exact file list + call-site count) for a dedicated pass with
           real CI/test feedback at each step.
why:       Rule 7/Rule 8 - a change this large and this central, done
           blind, risks breaking the score computation itself (the app's
           core value proposition) for the sake of a localization fix;
           the smaller, already-fixed UI-string gaps didn't carry that
           risk profile.

### App-audit §O1/L3 — no user-facing story for a deprecated Groq model
context:   DEFAULT_MODEL/FALLBACK_MODEL are compile-time string literals;
           Groq periodically deprecates/retires model names on its
           free/preview tier (a realistic near-future event, not
           hypothetical - this exact class of model churn is why
           task #26 added a Settings model override in the first place).
           When a pinned model gets retired, Groq returns 400/404, not the
           401/403/429/5xx this app already has friendly messages for -
           it would surface as a raw HTTP error with zero indication that
           the fix is simply picking a current model in Settings (a
           feature that already exists and would already solve it).
decision:  Added an HttpException 400/404 branch (mirroring the existing
           401/403 pattern from app-audit §F/L2) with a lang-aware message
           pointing at Settings' model field.
why:       Projects forward to a realistic, foreseeable failure mode of
           this app's core AI dependency and closes it with the cheapest
           possible fix - the actual remedy (Settings override) already
           exists, this just makes sure users are told about it instead
           of seeing a dead-end raw error.
reversal:  trivial (one string helper + one branch, same shape as an
           already-shipped, already-tested pattern)

## Layer 3 complete (A-O) — deep-fix license used for: DietChecker false
positives (A), Health Connect duplicate-record mitigation (B), Onboarding
API key masking (C), parallel OFF barcode lookups (D), AutoMirrored back
arrows (E), Grocery Snackbar (F), veto-badge semantics (G), GTIN-14
scanner reachability (H), findAdditive() memoization (I), date-formatter
locale threading (J), OcrParser fallback-model fix (K), EmptyListState
extraction (L), CI release-build check (M), scoring-engine i18n gap
documented/queued (N), deprecated-model error message (O).

### App-audit §A1/L4 — omega-3 bonus double-counted across two scoring locations
context:   NutritionalDensityPillar.scoreNutritionalDensity() awards +3
           for omega-3 content (nutrition value OR the same ingredient-
           name regex). ScoringEngine.computeGlobalBonuses() independently
           awarded +2 for the exact same ingredient regex match
           ("Omega-3 source: X") as a "global_bonus". Any product listing
           flaxseed/chia/walnut/salmon/sardine/mackerel/herring/anchovy as
           an ingredient got both bonuses simultaneously - +5 total for
           one signal, unfairly inflating the score relative to products
           whose omega-3 content is only reflected once.
decision:  Removed the duplicate global_bonus branch - the pillar-level
           bonus already covers both detection paths (nutrition value and
           ingredient name), so nothing is lost, only the double-count.
why:       Real, mechanically verified duplication (identical regex
           literal in two files) rather than a judgment call - a
           straightforward correctness bug in the scoring engine itself.
reversal:  trivial (deletion of 4 lines + a comment, no other logic
           touched; bonusTotal's minOf(10.0, ...) cap was never reached by
           the old max of 9 either, so no behavior change beyond removing
           the double-count)

### App-audit §B1/L4 — diary-entry parse failures silently vanish from calorie totals
context:   ConsumptionEntity.toDomain() wraps its mapping in
           runCatching {}.getOrNull() - any failure (e.g. a future
           MealSlot enum rename/removal leaving old stored rows with a
           name string that no longer resolves, or a NutritionPer100g
           JSON schema change) makes mapNotNull silently drop that row
           from every diary/dashboard view. The row isn't deleted, but
           the user's calorie/macro totals would be silently wrong with
           no error, no warning, nothing - for a nutrition-tracking app,
           that's a real correctness risk that's currently completely
           invisible. The same runCatching{}.getOrNull() pattern exists
           in 5 more repository files (queued).
decision:  Added Log.w() on the failure path so a parse failure is at
           least discoverable in Logcat during development/QA, instead of
           a fully silent no-op. Did not build a user-facing warning UI -
           that's a real feature (needs a "some entries couldn't load"
           banner + design) beyond a single audit finding's scope.
why:       Smallest safe step that turns a completely invisible failure
           mode into a discoverable one, without guessing at a bigger UI
           change blind.
reversal:  trivial (one .onFailure{} log call, no behavior change on the
           success path)

### App-audit §C1/L4 — backup import read an unbounded file fully into memory
context:   The import file picker uses ActivityResultContracts.OpenDocument(),
           which lets the user pick ANY file on the device, not just one
           this app exported. openInputStream(uri).readText() loads the
           entire file into a String with no size cap before Moshi even
           gets a chance to reject it as malformed - an arbitrarily large
           or mis-picked file (a video, a large unrelated JSON, etc.)
           risks an OOM crash purely from the read, independent of
           whether the content is even valid.
decision:  Query the file's size via
           contentResolver.openFileDescriptor(uri, "r")?.statSize before
           reading; reject (reusing the existing generic IO error path)
           if it exceeds a generous 50MB cap - real backups (thousands of
           scan/diary rows) are a few MB at most.
why:       Untrusted-input handling: the SAF picker hands this app a URI
           to arbitrary user-selected content, not just its own prior
           exports, so the read path needs to defend against that itself.
reversal:  trivial (one size check before the existing read; legitimate
           backups are far under the cap and see no behavior change)

### CI fix — M/L3's assembleRelease step found a real pre-existing lint error
context:   The M/L3 release-build CI step (commit ef875e8) failed on
           lintVitalRelease: "Remove androidx.work.WorkManagerInitializer
           from your AndroidManifest.xml when using on-demand
           initialization." R8/minifyReleaseWithR8 itself succeeded - this
           is a pre-existing manifest gap unrelated to any change this
           session, exactly the kind of thing debug-only CI could never
           have caught (confirming M/L3's whole premise). ScanEatApp
           implements Configuration.Provider (Hilt WorkManagerFactory,
           enqueueUniquePeriodicWork in onCreate()) but the manifest never
           disabled the default androidx.startup WorkManagerInitializer,
           which races the app's own on-demand init.
fix:       Added the standard tools:node="remove" provider-merge block
           (the exact fix Android's own lint message names) to disable
           the default initializer, plus the xmlns:tools declaration it
           needs on the manifest root.
verify:    CI check pending on this commit.

### App-audit §D1/L4 — activity_log was the one table not indexed with profileId
context:   consumption_log and scan_history both index on (profileId, ...)
           for their date/timestamp queries; weight_log was fixed to the
           same shape at app-audit §D/L1 (a real unique-index data-loss
           bug there). activity_log was the one remaining table still
           indexed on `date` alone - not a uniqueness/data-loss issue
           (no unique constraint here, multiple activities per day are
           expected), just a query-shape inconsistency that would matter
           once multi-profile queries are actually reachable.
decision:  Migration 6->7: replaced the date-only index with
           (date, profileId), matching the other 3 tables' convention.
why:       Consistency with an established, deliberate schema pattern
           already applied everywhere else - low-value today (single
           profile in practice) but free to align now.
reversal:  additive migration (index swap only), safe regardless of
           future multi-profile plans - same reasoning as §D/L1's weight_log fix.

### App-audit §E1/L4 — more deprecated non-mirrored icons, caught via the CI build log itself
context:   The M/L3 release-build CI run's compile warnings named 5 more
           non-mirrored deprecated icons the §E1/L3 ArrowBack sweep didn't
           search for: Icons.Default.ListAlt (DashboardScreen,
           MealPlanScreen, TemplatesScreen - 3 sites), TrendingUp/
           TrendingDown (WeekDeltaCard), MenuBook (TopTab, the Journal
           bottom-nav icon), ArrowForward (ScoreDisplay's dual-score
           arrow). Same RTL-correctness gap as §E1/L3, just different
           icons the earlier grep for "ArrowBack" specifically missed.
decision:  Swapped all 6 sites to their Icons.AutoMirrored.Filled
           equivalents with matching import updates, verified via
           Kotlin's own compiler deprecation warnings (not just an ad hoc
           search) - a nice confirmation loop from the new release-build
           CI check already paying for itself twice in one pass.
reversal:  trivial (icon symbol + import swap only, identical LTR
           rendering)

### App-audit §F1/L4 — delete confirmation never named the item being deleted
context:   DeleteConfirmDialog (shared by Weight/Templates/Recipes/
           Activity) always showed a fully generic "Delete? This action
           cannot be undone." with zero indication of which item - a real
           risk in dense lists where a misclick could confirm deleting the
           wrong row with no specific confirmation to catch it.
decision:  Added an optional itemName param; when the caller has one in
           scope (recipe/template name, weight entry's date, activity
           type label - all already available from the same in-memory
           list the row came from, no new state needed) the dialog shows
           "'X' will be permanently deleted..." instead of the generic
           text. Falls back to the original generic body when null, so
           any future caller not passing a name still works.
why:       Same-shape mechanical improvement across 4 already-identical
           call sites, using data already in scope - no new state, no
           risk to existing behavior when a name isn't available.
reversal:  trivial (one new optional param + a lookup at each call site;
           omitting itemName reproduces the exact old behavior)

### App-audit §G1/L4 — weight sparkline had zero semantic content
context:   WeightScreen's 8-bar sparkline (each Box's height/color encoding
           one weigh-in) carried no semantics at all - the caption above
           it only says "Trend - last 8 weigh-ins" with no values, and
           the 8 unlabeled Boxes give TalkBack nothing to announce. A
           screen-reader user gets zero information about the actual
           trend (values, direction) from a chart that's entirely visual.
decision:  Added Modifier.clearAndSetSemantics { contentDescription = ... }
           on the bars' Row, describing the first-to-last value range
           (e.g. "Trend chart: from 74.1 kg to 72.3 kg over the last 8
           weigh-ins") via dispWeight() (already unit/locale-aware) - one
           coherent announcement instead of 8 silent boxes.
why:       A data visualization with literally zero text alternative is
           a hard accessibility gap, not a nice-to-have - the caption
           alone doesn't convey the actual trend data.
reversal:  trivial (semantics modifier + 2 new strings, no visual change)

### App-audit §H1/L4 — ScanScreen imported LocalLifecycleOwner from its deprecated location
context:   The CI build log flagged 'val LocalLifecycleOwner:
           ProvidableCompositionLocal<LifecycleOwner>' (from
           androidx.compose.ui.platform) as deprecated, moved to
           lifecycle-runtime-compose's androidx.lifecycle.compose package
           - the same artifact this file already depends on for
           collectAsStateWithLifecycle. A future AndroidX release could
           remove the old symbol entirely, breaking the build on a routine
           dependency bump for no functional reason.
decision:  Repointed the import to androidx.lifecycle.compose.LocalLifecycleOwner -
           same API, no behavior change, just the current location.
reversal:  trivial (one import line)

### App-audit §I1/L4 — DashboardViewModel's 5-flow combine used unchecked Array casts
context:   The CI build log flagged "Unchecked cast of 'kotlin.Any' to
           'kotlin.collections.List<DiaryEntry>'" - heavyState's combine()
           of exactly 5 flows used the untyped Array<*>-indexed lambda
           form (args[0] as DailySummary, etc., under
           @Suppress("UNCHECKED_CAST")) even though kotlinx.coroutines
           provides a type-safe 5-flow combine() overload with typed
           lambda parameters for exactly this arity - the array form is
           only needed at 6+ flows.
decision:  Switched to the typed 5-parameter lambda (today, allEntries,
           profile, _, bioProfile) - removes every unchecked cast and the
           suppression annotation; the 4th param (weightRepo.observeAll(),
           a trigger-only input never threaded into Quad) is now an
           explicit `_` instead of a silently-dropped array index.
           Removed 2 now-unused imports (DiaryEntry - already covered by
           the existing domain.model.* wildcard; BiolismProfile - no
           longer referenced by name anywhere in the file).
why:       Same information conveyed with zero unchecked casts - the
           compiler itself now verifies every flow's value type instead
           of trusting a manually-indexed array + suppressed warning.
reversal:  trivial (mechanical rewrite of one combine() call + 2 import
           removals, identical runtime behavior)

### App-audit §J1/L4 — grocery list's markdown-checklist export was unreachable
context:   formatGroceryList(items, markdown: Boolean = false) - ported
           from the original grocery-list.js - branches on `markdown` to
           produce "- [ ] item" checkbox-style lines instead of plain
           "- item" lines (useful for pasting into Notion/Obsidian/any
           markdown note app), but no UI call site ever passed
           markdown = true. A real feature dropped in translation, not a
           deliberate cut.
decision:  Turned the single copy IconButton into a small DropdownMenu
           with 2 text-only entries ("Copy (plain text)" / "Copy
           (Markdown checklist)") - avoided guessing at a new, unverified
           icon name (no local build to compile-check against) by using
           only Text-based DropdownMenuItems and the icon already proven
           to compile.
why:       Restores a real, working, previously-inaccessible data-export
           format at essentially zero risk - the underlying function was
           already correct and tested by nothing changing except which
           UI action reaches it.
reversal:  trivial (UI-only change; formatGroceryList itself untouched)
