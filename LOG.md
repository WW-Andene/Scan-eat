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
