# ADR-0004 — Feature-folder pattern under `public/features/`

**Status:** Accepted · **Tier:** 1 · **Date:** 2026-04-22

## Context

`public/app.js` was a single monolithic entry point approaching 3,500 lines
before the feature-folder restructure. It handled everything: scanning,
scoring, dashboard rendering, dialog wiring, i18n application, feature
state — no separation. Adding a new feature meant editing app.js in 4-6
places and hoping you caught them all.

The `public/data/` and `public/core/` folders were already established:
storage modules and shared pure utilities respectively. What was missing
was a home for **UI + state modules that own their own DOM surface**.

## Options considered

### Option A — Component framework (React / Vue / Svelte)

**Pros**: standard pattern for UI decomposition.

**Cons**: a framework is a major Tier-2 decision in itself; the app had
already shipped value with vanilla ES modules; migration cost is high.

### Option B — "feature folder" convention with dependency injection

**Pros**: incremental; can extract one feature at a time; no framework
added; modules stay testable without a DOM shim if their business logic
is split cleanly from the UI wiring.

**Cons**: not enforced by the toolchain — drift is possible.

### Option C — Leave app.js as a monolith

**Pros**: zero migration work.

**Cons**: onboarding friction keeps growing; the 3500-line file is where
most incidents originate during maintenance.

## Decision

**Option B — feature folder.** Each feature lives in
`public/features/<feature>.js` and exposes two things:

- `init<Feature>(deps)` — called once at boot with a dependency object:
  `{ t, toast, renderDashboard, getProfile, … }`. The module stashes
  these in module-scope and wires up its DOM event listeners.
- `render<Feature>()` — called by the dashboard render loop to paint the
  feature's DOM surface. Returns feature-specific data the dashboard may
  want (e.g. `renderActivity()` returns today's burned kcal so the
  dashboard can compute Net = consumed − burned).

Tests live in a per-feature `*-tests.ts` file at the repo root
(`activity-tests.ts`, `meal-plan-tests.ts`, …). Pure-function helpers are
the test surface; IDB / DOM glue is tested manually.

## Shipped features under this pattern

`hydration` · `fasting-history` · `day-notes` · `grocery-list` ·
`meal-plan` · `activity` · `csv-import` (no UI — pure parser, invoked
from app.js).

Remaining extraction candidates (currently still inline in app.js): weight
summary, camera-scanner, quick-add, pairings UI, recipe-ideas dialog,
profiles, reminders. Each is ~50-200 lines of inline code.

## Consequences

- Adding a feature is a three-file change: `public/features/<feature>.js`,
  a `<feature>-tests.ts` file, and a single-line addition to `app.js` to
  call `init<Feature>()` at boot.
- The service worker's SHELL list has to include each feature file — build
  regeneration handles the cache key; the SHELL list itself is
  hand-maintained.
- Tests are genuinely per-feature — a regression in activity doesn't
  cascade to fasting-history tests.
- Drift risk: a feature module that reaches for `app.js` internals via
  `document.getElementById` instead of `deps` starts to re-couple. The
  hydration / fasting-history / day-notes / grocery-list / meal-plan /
  activity modules all respect the discipline today. Keeping them as the
  reference pattern is the only enforcement mechanism we have.

## Reversal cost

Low per-feature. A module can be re-inlined into app.js by copy-pasting
the exported functions and deleting the init call. Not recommended in
practice.

## Revisit trigger

- The feature count exceeds 15 and app.js starts growing linearly again
  because of orchestration boilerplate.
- A consumer of the code asks for a framework migration (React) in which
  case the feature-folder modules port more cleanly than the monolith
  would.

## Status update — 2026-06-30 (drift correction complete)

This ADR's own risk callout ("not enforced by toolchain — drift is
possible") materialized: app.js grew back to 3,718 lines, and two
incidents (`#status` visibility bug, verdict i18n leak) traced directly
to clusters that had never been extracted, contrary to this ADR's
intent. A 13-phase decomposition (see `restructuring-plan.md`) has now
extracted every cluster identified in that plan's responsibility map:

- `core/dom-helpers.js` and `state/scan-queue-state.js` (new — `queue`
  is now a single shared module export instead of an implicit
  module-level variable forked across three call sites).
- `features/barcode-scanner-detect.js`, `features/image-compression.js`,
  `features/scan-queue-ui.js`, `features/scan-pipeline.js`,
  `features/offline-queue-sync.js`, `features/scan-result-render.js`,
  `features/profiles-ui.js`, `features/custom-foods-day-notes.js`,
  `features/dashboard-charts.js`, `features/qa-photo-identify.js`.

All extractions kept the existing `deps`-object convention this ADR
specifies — no new wiring pattern was introduced. The final sweep
(Phase 13) removed dead imports left behind by the extractions
(`removePending`, `findScanByBarcode`, `getSetting`, `buildCustomFood`,
`findPairings`, `ACTIVITY_TYPES`, `laplacianVariance`,
`loadImageFromFile`, `countPending`, `sharpnessVerdict`,
`blobToDataUrl`) and confirmed no duplicate function definitions or
stale call sites remained.

**LOC target not fully met, and that's a planning-estimate gap, not a
missed extraction.** `app.js` is ~1,860 lines today, not the 400–500
originally targeted. The original responsibility map (§2 of
`restructuring-plan.md`) only catalogued *named, function-level*
clusters; it undercounted the volume of inline `addEventListener`
wiring (dialog open/close handlers, form submits, share/copy buttons,
quick-add, custom-foods, grocery list, etc.) that was always legitimate
bootstrap glue rather than an unextracted cluster. That code has no
single owning feature and is small per-handler, so it was correctly
left in place per this restructuring's own "no behavior changes, no
speculative restructuring" rule (R10) — moving it now would mean
inventing new feature boundaries that don't yet exist in the product,
which is out of scope for a drift-correction pass.

**Re-drift prevention**: `tools/check-app-size.mjs`, wired as `pretest`,
fails the build if `public/app.js` exceeds a line-count cap — implemented
2026-06-30 alongside this status note. Periodic re-runs of the
`app-audit` skill's "Feature Preservation Ledger" check are still
recommended to catch clusters that grow past ~150 LOC before they're
extracted.

## Phase 14 — further reduction (2026-06-30, same day)

After this ADR's first status update shipped, a follow-up pass moved
five more self-contained UI clusters out of `app.js` into their natural
feature-module homes, all via the same `deps`-object convention:

| Cluster | New home |
|---|---|
| Grocery-list dialog (open/render/copy/share/close) | `features/grocery-list.js` (`initGroceryListDialog`) |
| Telemetry panel (enable/view/copy/export/clear) | `features/telemetry-ui.js` (new file, `initTelemetryUi`) |
| "Add to recipe" picker | `features/add-to-recipe.js` (new file, `initAddToRecipe`) |
| Custom-foods dialog open/close/save | `features/custom-foods-day-notes.js` (`initCustomFoodsDialog`) |
| Profiles save/switch/delete listeners | `features/profiles-ui.js` (`initProfilesDialog`) |

`app.js` dropped from ~1,866 lines to 1,604 — about 14% smaller, and
under the 2,000-line cap with real margin instead of sitting right at
the post-Phase-13 baseline. Eleven additional dead imports left over
from the Phase 13 sweep (`removePending`, `findScanByBarcode`, etc.)
were also removed as part of this pass.

The remaining ~1,600 lines are genuinely bootstrap + per-feature deps
wiring (boot-time `init*()` calls, DOM element lookups, a handful of
small handlers with no natural feature-module home) — the same
conclusion as the original status update, just at a smaller, now
actually-trimmed baseline. Further reduction is possible (e.g. the
remaining `qa-photo-*-input` listeners, CSV import wiring) but each
additional extraction has diminishing returns relative to the risk of
touching more call sites; revisit opportunistically rather than as a
forced next phase.
