# Product Requirements — Scan'eat

Retrofit per `app_development_SKILL.md §III`. The code ships; this doc
describes what it does in the shape the skill expects — user stories with
acceptance criteria + non-functional requirements. See also
`00-brief.md` for vision and `DECISIONS.md` for rationale.

## Goals

1. **Instant verdict**: a scanned product carries a defensible
   personalised score in ≤ 2 s on a typical phone + 3G.
2. **Honest provenance**: every piece of data the user sees is either
   from a cited source (CIQUAL, EFSA, OFF, Ahn 2011) or explicitly
   labelled as editorial / LLM output.
3. **Diary-grade tracking**: per-meal entries with kcal + 7 macros +
   fiber + 4 micros, aggregated to daily targets with deficit nudges.
4. **Zero-server privacy**: all user state on device. External calls
   only for barcode lookup (OFF) and AI vision (Groq) — and Groq is
   optional via direct mode.
5. **Offline-first**: the app works without network for everything
   except the AI vision and OFF lookups.

## Non-goals

- Clinical or prescriptive medical advice. All scores + suggestions are
  editorial.
- Cross-device sync. Backup / restore is manual, user-driven.
- Social features, sharing, leaderboards.
- Voice assistant integration (Alexa / Google / Siri).
- Paid features / subscription.

## Personas

### P1 — The label reader (primary)

French home cook, 30–55, tracks intake loosely, wants faster verdicts
than reading the back of the box. Uses the phone camera at the grocery
store. Values: speed, honesty, not being nagged.

**User stories**:
- US-P1-1: "I scan a pasta sauce and see whether it fits my diet in under
  three seconds."
- US-P1-2: "When a scan matches my diet poorly, I'm shown an alternative
  in the same category with a better score."
- US-P1-3: "When I log a product, the day's kcal + macros update
  instantly on the dashboard."

### P2 — The diet enforcer (secondary)

Has a specific dietary constraint (gluten-free, vegan, halal, low-FODMAP).
Wants hard yes/no on products. Will stop using the app if it misses an
obvious violation.

**User stories**:
- US-P2-1: "A product containing an ingredient forbidden by my diet
  shows a red veto regardless of its classic score."
- US-P2-2: "When I open a product, I see which diet rule triggered the
  veto, with a plain-language rationale."
- US-P2-3: "Halal / Kosher certification tags raise the product's score."

### P3 — The nutrient optimiser (tertiary)

Tracks fiber + micros. May be vegan, vegetarian, or menstruating;
specifically cares about iron / calcium / vit D / B12.

**User stories**:
- US-P3-1: "When I'm under a nutrient target at the end of the day, I see
  2-3 foods that would close the gap, sized realistically."
- US-P3-2: "My daily targets account for sex / age / activity / BMI /
  goal weight."
- US-P3-3: "Menstruating women see the 16 mg/d iron target, not the 11
  mg/d default."

### P4 — The home cook (tertiary)

Uses the app outside the store too — for meal planning, recipe ideas,
pantry-first cooking.

**User stories**:
- US-P4-1: "I photograph a plate I'm about to eat and every item gets
  identified + logged."
- US-P4-2: "I list what's in my fridge and get 3 recipes using most of it."
- US-P4-3: "I plan the week's dinners and export a shopping list."
- US-P4-4: "I scan a restaurant menu and pick the dish to log."

## Acceptance criteria (select — not exhaustive)

### AC-1 Barcode scan end-to-end
**Given** the user has granted camera permission
**When** they point at an EAN-13 of a product present in Open Food Facts
**Then** within 2 s the result card renders with grade + score + name +
verdict + allergen list + pairings section + "Log this" button.

**Verification**: `scoring-engine.ts` + `off.ts` + `app.js` render path.
Manual smoke test per `docs/runbooks/deploy.md`.

### AC-2 Barcode scan offline + queued
**Given** the user is offline at scan time
**When** they scan a barcode
**Then** the scan is queued in IDB `pending_scans` and retried when
online, with a banner communicating pending state.

**Verification**: `public/data/queue-store.js` + `public/app.js` retry
logic.

### AC-3 Personal score veto on diet violation
**Given** the user has set `diet: 'gluten_free'`
**When** they scan a product whose ingredients include wheat / rye /
barley / oats (no certified-GF mark)
**Then** the personal score is capped at 0 (grade F) regardless of
classic score, and the veto reason is shown.

**Verification**: `public/core/personal-score.js` + `public/core/diets.js`
+ `diet-tests.ts` + `personal-score-tests.ts`.

### AC-4 Log entry flows into daily totals
**Given** the user logs a 200 g portion of a product with 54 kcal / 100 g
**When** the dashboard renders
**Then** the kcal row shows +108 vs the prior state, and the Remaining
line decrements accordingly.

**Verification**: `consumption-tests.ts` sumTotals semantics. Manual
verification via dashboard.

### AC-5 Close-the-gap suggestions only appear when relevant
**Given** the user has logged ≥ 1 entry today
**When** at least one "more is better" nutrient is below target
**Then** the #gap-closer panel shows up to 3 suggestions per deficit,
ranked by per-100 g density, capped at a realistic portion.
**And** when every target is met, the panel is hidden.

**Verification**: `close-the-gap-tests.ts` + `public/app.js
renderGapCloser`.

### AC-6 Meal plan → grocery list round-trip
**Given** the user has planned ≥ 1 recipe in the 7-day meal plan
**When** they tap "🛒 Liste de courses du planning"
**Then** aggregated shopping list appears, alphabetised, with total
grams per ingredient, and a "Copier" button that writes the list to
clipboard.

**Verification**: `grocery-list-tests.ts` + `meal-plan-tests.ts`.

### AC-7 CSV import from MFP
**Given** the user exports their MyFitnessPal diary as CSV
**When** they select the file from Settings → Sauvegarde → "Importer MFP / Cronometer"
**Then** each row is parsed and written to IDB `consumption` with the
correct meal slot, macros, and date.
**And** rows with kcal=0 or empty names are skipped with a summary count.

**Verification**: `csv-import-tests.ts`.

### AC-8 Food reconciliation on photo ID
**Given** the user photographs an apple
**When** the LLM returns `{ name: 'pomme', estimated_grams: 150, ... }`
**Then** the reconcile step replaces the LLM's macro estimate with the
CIQUAL values for `pomme` (grade-authoritative) scaled to 150 g, and
the UI chip labels the source as DB-derived.

**Verification**: `food-db-tests.ts reconcileWithFoodDB` block.

### AC-9 Backup / restore lossless round-trip
**Given** the user has data in every IDB store + every `scanneat.*`
localStorage key
**When** they export, wipe the database, then import the export
**Then** every entry is restored byte-equivalent (ignoring new IDs that
might be regenerated on import).

**Verification**: manual; `public/backup.js` shape tests could be added.

### AC-10 Install prompt doesn't nag
**Given** the user has dismissed the install banner
**When** they reload the app within 30 days
**Then** the banner does not reappear.
**And** if the user installs the app, the banner never reappears.

**Verification**: `public/app.js` `shouldShowInstallBanner` +
`snoozeInstallBanner` + `appinstalled` listener.

## Non-functional requirements

| Category | Requirement | Verification |
|----------|-------------|-------------|
| Performance | First interactive < 3 s on 4G on a $200 Android | Lighthouse + real-device test |
| Performance | Barcode scan-to-result < 2 s on a cached product | Manual + console.time in dev |
| Reliability | `npm test` passes with 0 failures before any deploy | CI / `docs/runbooks/deploy.md` preconditions |
| Security | No secrets in repo except `.env.example` | Audit: `grep -rnE "gsk_|sk-|AKIA" --exclude-dir=.git --exclude-dir=node_modules` |
| Privacy | No analytics / crash-reporter / ad tracker | Audit: `grep -rn "sentry\|datadog\|google-analytics\|posthog"` should return 0 matches |
| Accessibility | WCAG 2.1 AA on colour contrast, focus rings, landmarks, live regions | Spot-audited in B7.2; deeper audit deferred |
| i18n | FR + EN fully translated; ES/IT/DE skeleton with EN fallback | `public/core/i18n.js` structure |
| Data integrity | Every shipped data file cites its source | `00-brief.md` provenance table |
| Observability | Local telemetry only; no outbound observability calls | `public/core/telemetry.js` inspection |
| Offline | Dashboard + log + backup export work with no network | Manual: `chrome://devtools` offline mode |

## Out of scope (for this version)

- iOS App Store / Play Store distribution (Android-direct APK only).
- Native HealthKit / Google Fit sync (JSON export exists as a bridge).
- Multi-device sync.
- Professional / clinician mode with audit logging.
- Real-time push notifications (local reminders only).

## Open questions (for the user)

- LICENSE — currently Tier-3 stop-and-ask per `app_development_SKILL §I.1`.
  See `LICENSE.TODO` for the matrix of 6 options. Blocks any open
  redistribution story.
- Food DB regeneration — `tools/generate-food-db.mjs` ready; needs to run
  in a host with network access to ANSES. Current file is hand-
  transcribed approximations with visible provenance notice.
- Cost posture — current `ASSUMPTIONS.md` says traffic stays on Groq's
  free tier. Before any real launch announcement, decide whether to
  pre-emptively upgrade tier or implement client-side backoff in
  `callGroqVision`.
