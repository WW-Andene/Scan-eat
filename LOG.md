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
