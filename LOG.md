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
