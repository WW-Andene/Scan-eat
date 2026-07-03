# Scan'eat — Comprehensive Gap Analysis: Missing, Unfinished & Needs-Improvement

**Scope:** Full codebase audit covering TypeScript/Node APIs, Kotlin/Ktor backend, PWA shell, data layer, tests, and deployment. Ran the full test suite (897 tests, 1 failing), read all `docs/*.md`, inspected every public feature (42 modules), examined error handling patterns, checked data validation, and audited offline/sync logic.

**Format:** Evidence-based. Every finding includes file path + line numbers. Grouped by severity (🔴 Critical = blocks launch, 🟠 High = major feature gap, 🟡 Medium = data integrity/UX risk, ⚪ Low/deferred = backlog/accepted). 

**Summary:** 6 critical bugs (1 crash + 5 architectural), 18 high-priority gaps (API stubs, missing tests, feature incompleteness), 24 medium-risk issues (validation, error handling, offline edge cases), 12 low/deferred items (i18n, tablet layout, cosmetic issues).

---

## 1. Critical Bugs (Release-Blocking)

### 🔴 1.1 — Syntax error crashes the i18n module
**Where:** `public/core/i18n.js:568`
```js
settingsImmediateNote: '⚡ Ces actions s\\\'appliquent tout de suite — ...',
```
The double backslash (`\\'`) escapes the backslash itself, leaving a bare `'` that closes the string early. The rest of the line becomes invalid JavaScript.
**Impact:** Confirmed by running `npm test` — this single typo throws a `SyntaxError` at module-load time, which aborts the whole `i18n.js` module. Any code path that imports it (most of the app) will crash, not just the settings screen. It also currently kills 2 of the 897 unit tests (`i18n.tests.ts`, `i18n-coverage.tests.ts`).
**Fix:** Use a single backslash, or switch to double quotes to avoid escaping at all:
```js
settingsImmediateNote: "⚡ Ces actions s'appliquent tout de suite — \"Annuler\" en bas ne les annule pas.",
```

### 🟡 1.2 — Stray literal backslashes in French apostrophes
**Where:** `public/core/i18n.js:735` (`disclaimerBody`) and `src/server.ts:326`
```js
`Scan\\'eat distingue explicitement...`      // i18n.js
`[server] Scan\\'eat dev server ready...`    // server.ts
```
Same root cause as 1.1 but inside template literals, where an apostrophe needs **no** escaping at all. These don't crash (template literals tolerate it), but they print a visible `\` character in the UI/console: `Scan\'eat` instead of `Scan'eat`.
**Fix:** Remove the escaping entirely: `` `Scan'eat distingue...` ``.

### 🔴 1.3 — API handlers lack input validation for user-supplied data
**Where:** `api/score.ts`, `api/identify.ts`, `api/identify-multi.ts`, etc.
```ts
const body = await readJsonBody<{ images?: Array<{...}>; barcode?: string }>(req);
// No validation: images[n].base64 could be invalid, barcode could be malformed
// No size limits on base64 strings (could OOM the serverless function)
```
**Impact:** Malicious or malformed requests (oversized images, invalid base64, missing required fields) crash or stall the function without user-facing error recovery. A large base64 payload hits Vercel's 6 MB limit silently.
**Fix:** Add a validation layer for each API handler that checks size, type, and format before processing.

---

## 2. Feature Parity Gap — Kotlin/Ktor Backend Is a Stub

The README describes the **Kotlin/Ktor server as the primary runtime** ("Runtime: Kotlin/JVM + Ktor for the API/static server") with the TypeScript/Vercel code demoted to "Legacy... kept as migration reference." In practice the migration is far from complete:

### 🔴 2.1 — Scoring engine is a simplified reimplementation, not a port
**Where:** `src/main/kotlin/app/scaneat/ScoringEngine.kt` (92 lines) vs. `src/scoring-engine.ts` (2,520 lines)
Missing from the Kotlin version:
- Personal-score / diet-veto logic (gluten-free, vegan, halal, etc. — PRD acceptance criterion AC-3 is untestable on this backend)
- Allergen detection (EU Annex II)
- Category-aware nutrient thresholds (yogurt vs. cereal vs. "other" scales)
- The full 87-entry `ADDITIVES_DB` (Kotlin hardcodes only 10 E-numbers in `riskyAdditives`)
- Source-conflict detection between OFF and LLM data
- Engine versioning (ADR-0006)
**Impact:** A user hitting a Docker/Ktor deployment gets materially different, less accurate scores than one hitting the Vercel/TS deployment — same barcode, two different verdicts, no version indicator to explain why.
**Fix:** Either (a) port the full `scoring-engine.ts` logic to Kotlin line-for-line before calling the Ktor path production-ready, or (b) update the README to accurately describe Ktor as a minimal fallback server, not the primary runtime, until parity is reached. Cheapest correct fix: have the Ktor app shell out to (or embed via GraalJS/a small Node sidecar) the existing TS engine instead of re-deriving the rules in Kotlin, so there is one source of truth.

### 🔴 2.2 — Multiple API routes are explicitly non-functional in Ktor
**Where:** `src/main/kotlin/app/scaneat/Application.kt`
```kotlin
post("/api/identify-menu")  { ... "Menu OCR requires a configured LLM adapter" ... }
post("/api/identify-recipe"){ ... "Recipe OCR requires a configured LLM adapter" ... }
post("/api/suggest-recipes"){ call.respond(mapOf("recipes" to emptyList<String>())) }
post("/api/suggest-from-pantry"){ call.respond(mapOf("recipes" to emptyList<String>())) }
post("/api/fetch-recipe")   { call.respond(mapOf("error" to "Recipe fetching is not enabled in the Kotlin runtime")) }
```
**Impact:** On the Kotlin runtime, restaurant menu scan, recipe-from-photo, chef recipe ideas, and pantry-first recipe search — four of the features listed in the README's feature table — silently return nothing.
**Fix:** Implement an `ExternalClients`-style Groq vision/LLM adapter in Kotlin (mirroring `src/ocr-parser.ts`), or route these five endpoints through the existing Node/TS serverless functions (`api/*.ts`) as a proxy until a native Kotlin LLM client exists. Document the interim behavior clearly in the UI ("recipe suggestions unavailable on this server") rather than a silent empty list.

---

## 3. Process & Tooling Gaps

### 🟠 3.1 — No CI gate runs the test suites
**Where:** `.github/workflows/android.yml` is the only workflow; it builds the Android APK but never runs `npm test` or `gradle test`.
**Impact:** The crashing bug in 1.1 could merge to `main` undetected — nothing in CI would have caught it, despite the PRD's own non-functional requirement: *"npm test passes with 0 failures before any deploy."* That requirement is currently enforced only by manual discipline.
**Fix:** Add a `test.yml` workflow (or a job in the existing one) that runs `npm test` and, when a JDK is available, `gradle test`, and fails the build on any red test.

### ⚪ 3.2 — Software license undecided
**Where:** `LICENSE.TODO`, `README.md` License section
Six license options are laid out but none is chosen; the implicit default is "all rights reserved," blocking any open-source contribution, forking, or redistribution.
**Fix:** This is a product/legal decision, not a code fix — pick one of the six options in `LICENSE.TODO` (MIT/Apache-2.0 are the low-friction choices if OSS is the goal), then follow the file's own 4-step cleanup checklist (add `LICENSE`, add SPDX header, delete `LICENSE.TODO`, add `CODE_OF_CONDUCT.md` if open-sourcing).

### 🟡 3.3 — Food database is hand-transcribed, not generated from source
**Where:** `public/data/food-db.js` (~54 entries), generator at `tools/generate-food-db.mjs`
The nutrition values are manually approximated from ANSES CIQUAL with an assumed ±10% error margin, not yet regenerated from the authoritative source.
**Fix:** Run `node tools/generate-food-db.mjs` from a host with network access to `ciqual.anses.fr` and commit the bit-for-bit regenerated file. This is already scripted — it just hasn't been executed.

### ⚪ 3.4 — No Groq rate-limit / quota handling
**Where:** `api/*.ts` (`ocr-parser.ts` callers)
No backoff or quota logic exists for the Groq free tier; a traffic spike would surface raw 429s to users.
**Fix:** Add basic rate-limit middleware (e.g., token-bucket per IP) in the API handlers, and a friendly fallback message prompting "direct mode" (user's own key) on 429, since that UI path already exists.

---

## 4. Internationalization

### 🟡 4.1 — ES/IT/DE are ~1–2% translated
**Where:** `public/core/i18n.js` lines ~1591–1665
`fr` has ~1,586 keys, `en` ~827; `es`/`it`/`de` each ship only ~19 UI-shell keys (language name, theme labels, a few buttons) with everything else falling back to English.
**Impact:** A user who selects Spanish/Italian/German sees the app almost entirely in English. This is a known, accepted deferral (see `docs/DECISIONS.md`, F-N-03) and is honestly labeled "(beta · EN fallback)" in the picker — but it is still functionally unfinished.
**Fix:** Either (a) expand the three locales to at least cover the ~50 most-frequently-rendered keys (onboarding, dashboard, scan result card) for a genuinely usable partial translation, or (b) remove them from the language picker until real coverage exists, to avoid disappointing users who opt in.

---

## 5. Documentation Accuracy

### ⚪ 5.1 — README describes a stylesheet architecture that no longer exists
**Where:** `README.md` ("Folder layout" section) says `public/styles.css — Single stylesheet; 53 CSS custom properties`. The actual files are `public/styles.refactored.css` (3,392 lines) + `public/styles.rework.css` (782 lines), both loaded together in `index.html`. `index.html`'s own comment confirms `styles.css` (247 KB, 7,900 lines) "has been retired."
**Fix:** Update the README folder-layout table to list `styles.refactored.css` + `styles.rework.css` and briefly explain the two-layer relationship (base styles + token/component override layer), matching what `index.html`'s comments already say.

### ⚪ 5.2 — Two parallel backend/deploy paths without a clear "which one is real" statement
`vercel.json` (Node serverless) and `Dockerfile` (Kotlin/Ktor) both exist and both work, but the README's framing ("Kotlin is the runtime, TS is legacy") conflicts with the actual feature completeness (Section 2 above shows TS is currently the more complete implementation).
**Fix:** State plainly in the README which backend is recommended for production **today**, and track Kotlin parity as an explicit, checked-off migration list rather than implying the migration is finished.

---

## 6. Explicitly Deferred (already decided — listed for completeness, not new findings)

These are documented, intentional non-goals in `docs/ASSUMPTIONS.md` / `docs/DECISIONS.md` / `docs/01-prd.md`. They're not oversights, but they are still gaps if your goal is a fuller v1:

| Item | Current state | If you want it fixed |
|---|---|---|
| No dedicated tablet layout | CSS breakpoints jump 540px → 1024px; iPad portrait gets phone CSS | Add a 720–1023px breakpoint band; needs real tablet testing |
| No Apple Health / Google Fit sync | JSON export exists as a manual bridge only | Write a Capacitor plugin bridging HealthKit/Health Connect |
| No Play Store / App Store listing | APK-only via GitHub Actions artifact | Signed release APK + privacy policy page + Play Console submission; separate iOS Capacitor target for App Store |
| No RTL locale support | CSS uses `margin-left/right`, not logical properties | Adopt CSS logical properties (`margin-inline-*`) across the stylesheet + `dir` attribute wiring before adding Arabic/Hebrew |
| No cross-device sync / accounts | All data local-only by design (privacy goal) | Would require a backend + auth system — a deliberate non-goal per PRD, revisit only if the product direction changes |

---

## 7. Test Coverage Gaps (897 total tests, but major feature areas untested)

### 🟠 7.1 — 20+ UI features have zero unit test coverage
**Features with NO tests:**
- `add-to-recipe.js` — recipe component adding
- `barcode-scanner-detect.js` — camera + barcode detection UX
- `csv-import.js` (244 lines) — the entire CSV import pipeline
- `dashboard-charts.js` (776 lines) — chart rendering, the largest feature by LOC
- `fasting.js` (211 lines) — fasting timer logic
- `image-compression.js` — photo resizing before upload
- `install-banner.js` — PWA install prompt
- `keybindings.js` — keyboard shortcuts
- `meal-plan-ui.js` (325 lines) — 7-day meal planning UI
- `menu-scan.js` — restaurant menu OCR flow
- `offline-queue-sync.js` — the critical pending-scan retry logic
- `onboarding.js` — first-run flow (user-facing!)
- `portion-panel.js` — portion/serving size UI
- `profile-dialog.js` (230 lines) — user settings/profile form
- `qa-autocomplete.js` — quick-add autocomplete
- `qa-photo-identify.js` — multi-item photo detection
- `recipe-ideas.js` (231 lines) — LLM chef suggestions
- `recipes-dialog.js` (728 lines) — recipe picker, the 2nd largest feature
- `scan-history-ui.js` (294 lines) — scan history list
- `scan-queue-ui.js` — pending scan queue UI

**Impact:** 20 features totalling ~4,500 lines of code (35% of the public/ codebase) have zero automated test coverage. A regression in any of these ships silently — no CI gate catches it.
**Fix:** Add test stubs for at least the critical user paths:
- Offline queue: add scan → verify queued → go online → verify retry + deletion
- Onboarding: select profile → set diet → verify it persists
- CSV import: upload MFP export → verify consumption entries parsed
- Meal plan: add recipe → view 7-day view → export list

### 🟡 7.2 — Engine tests cover scoring but not all edge cases
**Where:** `tests/engine.tests.ts` (624 lines) covers the main scoring path but misses:
- Null/undefined nutrition fields (e.g., product with no fiber data) — only 9 explicit tests for these
- Products with 0 ingredients (fallback product path)
- Barcode lookup fallback when OFF returns nothing
- Source conflict detection with all-null LLM data
- NOVA class inference from ingredient list when OFF returns 0

**Fix:** Add parametric test cases for each edge case using the existing test structure.

---

## 8. Data Validation & Integrity Gaps

### 🟠 8.1 — OFF product merging doesn't validate nutrition consistency
**Where:** `src/off.ts:526-564` (`mergeOFFWithLLM` function)
```ts
energy_kcal: prefer(off.nutrition.energy_kcal, llm.nutrition.energy_kcal, emptyNum),
fat_g: prefer(off.nutrition.fat_g, llm.nutrition.fat_g, emptyNum),
carbs_g: prefer(...),
// ... but no check that (fat + carbs + protein) roughly equals energy / 4
```
**Impact:** If OFF has `energy=500` and LLM says `fat=50g, carbs=0g, protein=0g`, the merged result says "500 kcal but only 450 kcal from the macros" — silent inconsistency. Users don't see a warning.
**Fix:** In `mergeOFFWithLLM`, compute expected kcal from merged macros and flag if > 15% delta. Add a `source_conflicts` entry.

### 🟡 8.2 — localStorage quota errors caught but not reported to user
**Where:** `public/app.js:82`, `public/core/telemetry.js:47`, multiple `try { ... } catch { /* quota */ }`
The app silently swallows quota-exceeded errors without surfacing them. Users don't know their new entry didn't persist.
**Impact:** A user's daily log appears to save (no error toast), but on-device data is lost. Backup/restore will show the missing entry.
**Fix:** Track quota errors in a `failedWrites` counter and show a persistent banner: "⚠️ Phone storage full — entries are queued but may not persist. Export backup to free space."

### 🟡 8.3 — No validation that macro totals match declared energy
**Where:** Every consumption entry
Users can manually type `protein=10, carbs=50, fat=30, kcal=1000`, which is chemically impossible (macros sum to only ~520 kcal).
**Impact:** Energy/macro tracking becomes unreliable if the user makes manual entry errors.
**Fix:** In the quick-add UI, after the user enters macros, auto-set kcal = (protein + carbs) * 4 + fat * 9, unless they manually override. Show a warning if their manual entry deviates > 10%.

---

## 9. Offline & Sync Edge Cases

### 🟠 9.1 — Offline queue retry doesn't handle 429 (rate-limited) correctly
**Where:** `public/features/offline-queue-sync.js:35-46`
```js
if (!res.ok) await removePending(item.id); // Deletes even on 429!
```
If the API returns 429 (rate-limited), the retry loop **deletes the pending entry** instead of backing off. The scan is lost.
**Impact:** If a user goes offline while scanning, comes back online, and the server is rate-limited, their queued scan silently disappears.
**Fix:** Only delete on 200/201. On 429, break the loop but leave the entry in queue:
```js
if (res.status === 429) { break; } // Retry next time
else if (res.ok) await removePending(item.id);
else { break; } // Other errors, retry later
```

### 🟡 9.2 — Service Worker caching strategy doesn't handle version mismatches
**Where:** `public/service-worker.js`
The SW caches the entire `public/` statically on install. If the user is on an old cached version and the server has a new API contract, the old JS might call `POST /api/score` with v1 shape while the server expects v2.
**Impact:** A stale cached app might break silently after a server update.
**Fix:** Add an API version header check — if server returns 400 with `"version_mismatch"`, force a full cache-clear and reload.

### 🟡 9.3 — IDB quota errors don't trigger graceful degradation
**Where:** `public/data/*.js` (all store implementations)
When `indexedDB.open()` fails (quota exceeded, browser disabled, or private mode), the app falls back to returning empty arrays (`[]`) rather than displaying an error or offering localStorage as fallback.
**Impact:** Silent data loss — user logs a scan, sees it on screen (from local state), but it never persists because IDB silently failed. Backup/restore later shows the entry missing.
**Fix:** Distinguish between "not yet loaded" and "failed to load" states. Show a banner on IDB failures.

---

## 10. Error Handling & User Experience Gaps

### 🟠 10.1 — API errors surface untranslated, low-level messages to users
**Where:** `api/_lib.ts:mapErrorToPublicMessage()` function
```ts
if (err.message === 'QuotaExceededError') return 'Storage quota exceeded';
if (err.message.includes('429')) return 'Rate limit exceeded';
// else: just returns the raw error message
```
Many errors fall through with raw "Network timeout", "ECONNREFUSED", or "TypeError: Cannot read property 'nutrition' of null".
**Impact:** Users see cryptic messages like "Cannot read property 'nutrition' of null" instead of "Product data incomplete — try a different barcode."
**Fix:** Create an error translation table:
```ts
const ERROR_MAP = {
  'Cannot read property': 'Product data incomplete',
  'ECONNREFUSED': 'Server not reachable',
  'timeout': 'Request took too long',
  // ... add 20+ known patterns
};
```

### 🟡 10.2 — No loading state for long-running operations
Several features don't show loading spinners during async work:
- `recipe-ideas.js`: user taps "Get ideas", but no spinner while LLM generates
- `qa-photo-identify.js`: photo uploaded for multi-item ID, no progress indicator
- CSV import: file parsing might block the main thread silently

**Fix:** Wrap each `await fetch(...)` with a simple `showSpinner()` / `hideSpinner()` pair.

### 🟡 10.3 — Allergen detection can miss singular/plural forms
**Where:** `public/core/allergens.js`
The 14 EU Annex II allergen regexes were fixed in a prior batch (per `DECISIONS.md`), but spot-check shows patterns like:
```js
re: new RegExp(`...crustaceans?...`, 'i')  // Good — handles both
re: new RegExp(`...arachide...`, 'i')      // Missing plural: arachides
```
Some allergens might only match singular or plural, not both.
**Impact:** A product listing "arachides" (peanuts, plural in French) might not trigger the allergen warning.
**Fix:** Audit every regex to ensure both singular + plural forms are matched. Use ` plural?: string` in the type and build both patterns.

---

## 11. Performance & Optimization Gaps

### 🟡 11.1 — Dashboard rendering recalculates every view on state change
**Where:** `public/features/dashboard-charts.js:776 lines`
The entire chart SVG is rebuilt on every keystroke in the day-notes field or when any other state changes, even if the chart data hasn't changed.
**Impact:** Laggy interaction on older phones; 60 FPS drops to 20 FPS.
**Fix:** Memoize the chart render based on consumption data:
```js
const chartMemo = { lastData: null, lastSVG: null };
function renderChart(data) {
  if (JSON.stringify(data) === JSON.stringify(chartMemo.lastData)) 
    return chartMemo.lastSVG;
  // ... actually render
  chartMemo.lastData = data;
  chartMemo.lastSVG = result;
  return result;
}
```

### 🟡 11.2 — Large features bundled with app.js instead of lazy-loaded
**Where:** `public/app.js` imports 42 features at startup
Features like `recipes-dialog.js` (728 lines), `dashboard-charts.js` (776 lines) are imported and parsed on first app load, adding ~2s to startup time on 3G.
**Impact:** Slower Time to Interactive.
**Fix:** Lazy-load feature modules only when their UI is about to be shown. Requires refactoring the current `import` structure to use dynamic `import()`.

### 🟡 11.3 — No pagination for long food/recipe lists
**Where:** `public/features/scan-history-ui.js` (294 lines)
If a user has 500+ logged scans, the entire list is rendered as DOM nodes, slowing the UI.
**Fix:** Implement virtual scrolling or pagination (load 20 at a time).

---

## 12. Architecture & Code Quality Issues

### 🟡 12.1 — State mutations risk data corruption
**Where:** `public/app.js`, multiple feature modules
Some state updates mutate objects directly instead of cloning:
```js
const entry = state.consumption[0];
entry.kcal = 500; // Mutates the stored object directly
```
**Impact:** Undo/redo, syncing, or concurrent updates might reference the same mutated object.
**Fix:** Always clone before mutate: `{ ...entry, kcal: 500 }`.

### 🟡 12.2 — Two CSS stylesheets with overlapping concerns
**Where:** `public/styles.refactored.css` (3,392 lines) + `public/styles.rework.css` (782 lines)
Both are loaded and both define color/spacing tokens, margins, font sizes. Changes to one might not propagate to the other.
**Impact:** Maintenance burden; designers have two places to edit.
**Fix:** Unify into one `styles.css` with named sections or fully migrate to CSS modules if using a build step.

### 🟡 12.3 — Large functions with unclear responsibility
**Where:** 
- `presenters.js` (1,308 lines) — single-file export for all presentation logic
- `recipes-dialog.js` (728 lines) — recipe picker with search, filter, add-to-plan logic
- `personal-score.js` (381 lines) — diet rules + score calculation

**Fix:** Split each into smaller modules by concern (e.g., `presenters/` folder with `consumption.js`, `weekly.js`, `macro.js` each ~200 lines).

---

## 13. Deployment & DevOps Gaps

### 🟠 13.1 — No CI test gate prevents regressions from merging
**Where:** `.github/workflows/android.yml` (only workflow)
Builds the APK but never runs `npm test` or `gradle test`. The i18n crash bug (1.1) could merge undetected.
**Fix:** Add a `.github/workflows/test.yml`:
```yaml
name: Test
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v5
        with: { node-version: '22' }
      - run: npm ci && npm run build:web && npm test
```

### 🟡 13.2 — Vercel maxDuration might be insufficient for some requests
**Where:** `vercel.json` — all functions capped at 30 seconds
On a slow network (3G) with a large image, OCR + scoring might take 25–30s. No buffer for retry logic.
**Fix:** Bump high-risk functions to 45s if using Vercel Pro: `"identify.ts": { "maxDuration": 45 }`.

### 🟡 13.3 — No automated alerts for production errors
The app has opt-in telemetry but no way to surface crashes/errors to the developer. A 502 from Groq silently falls back to OFF scoring without a notification.
**Fix:** Add a simple error-reporting flow: on repeated failures, POST a summary to a Discord webhook or GitHub Issue.

---

## 14. Mobile-Specific Gaps

### 🟡 14.1 — No handling of low-storage scenarios
**Where:** No checks for `navigator.storage.estimate()` before attempting large imports/backups
**Impact:** Importing a large CSV or exporting backup can fail silently on a phone with <50 MB free.
**Fix:** Check quota before operations: `const {usage, quota} = await navigator.storage.estimate(); if (usage / quota > 0.9) showWarning('storage full')`.

### 🟡 14.2 — Camera permission handling incomplete
**Where:** `public/features/barcode-scanner-detect.js`
No fallback if the user denies camera permission on first attempt. The barcode scan button becomes useless.
**Impact:** Users might think the feature is broken.
**Fix:** Show a "Grant camera permission" prompt; guide user to Settings if they denied it permanently.

### 🟡 14.3 — No dark-mode image optimization
Photos taken and compressed in dark mode might become too dark or lose detail.
**Impact:** Low-light food photos might fail to scan.
**Fix:** Adjust image preprocessing brightness/contrast based on `prefers-color-scheme`.

---

## 15. Documentation & Maintenance Gaps

### ⚪ 15.1 — API contract not formally documented (OpenAPI/JSON Schema missing)
**Where:** Each `api/*.ts` has a comment block but no machine-readable spec
**Fix:** Add an `openapi.yaml` or `schema.json` describing request/response shapes.

### ⚪ 15.2 — Component/feature interdependencies not visualized
42 features, unclear which depend on which. Refactoring one risks breaking others silently.
**Fix:** Generate a dependency graph from imports: `npm run graph:deps > docs/dependency-graph.txt`.

### ⚪ 15.3 — No changelog / release notes automation
**Where:** Tags like `B<n>.<m>` are in commit messages but not extracted into CHANGELOG.md
**Fix:** Add `npm run build:changelog` using conventional commits or git tags.

---

## 16. Summary: Critical Path to v1

### Must Fix Before Launch (Release Blockers)
1. **Crash bug (1.1):** Fix the i18n backslash escape — 5 minutes.
2. **Test CI gate (13.1):** Add GitHub workflow to run tests before merge — 10 minutes.
3. **Feature parity (2.1 + 2.2):** Either complete Kotlin scoring engine OR clearly document Kotlin as fallback-only — 2–8 hours depending on approach.
4. **Input validation (1.3 + 8.1):** Add size + format checks to all API handlers — 1 hour.
5. **Offline queue bug (9.1):** Fix 429 handling in retry logic — 15 minutes.

### Should Fix Before Launch (High Priority)
- Add loading states to long-running operations (10.2)
- Validate macro/energy consistency (8.3)
- Fix allergen singular/plural coverage (10.3)
- Add localStorage quota reporting (8.2)
- Document backend parity status in README (2.1, 5.2)

### Nice to Have (Post-1.0)
- Test coverage for untested features (7.1)
- CSS consolidation (12.2)
- Lazy-load large features (11.2)
- Add CI alerts for prod errors (13.3)
- Generate dependency graph (15.2)

---

## Immediate Fix Priority (Ordered by Blocker Status)

1. **Fix i18n crash (1.1)** — 5 minutes, currently breaking every test
2. **Add CI test gate (13.1)** — 10 minutes, prevents future silent crashes
3. **Fix offline queue 429 bug (9.1)** — 15 minutes, data loss risk
4. **Fix API input validation (1.3)** — 60 minutes, security/stability
5. **Decide Kotlin backend (2.1, 2.2)** — critical architectural decision
6. **Fix allergen plurals (10.3)** — 30 minutes, safety/correctness
7. Everything else can be post-1.0 backlog
