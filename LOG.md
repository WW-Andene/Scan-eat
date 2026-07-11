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
