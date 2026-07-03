# Scan'eat — Brief

> Retroactive brief. Extracted from code + commit history on
> `claude/improve-ocr-ingredient-parsing-zxgSf` as of commit `574dd27`.
> Populated per `app_development_SKILL.md` §II stage 1. Parts the user
> should verify are marked **[ASSUMED]**; those should either be
> confirmed or flipped via `ASSUMPTIONS.md`.

## Identity

- **Name**: Scan'eat.
- **Domain**: Food + nutrition tracking from label scans (barcode / OCR) and photos.
- **Audience**: French-speaking home users managing their diet. **[ASSUMED]** — no
  evidence of non-FR / non-home-user targeting in the codebase; EN locale exists as
  secondary, ES/IT/DE shipped as beta skeletons (B8.1).
- **Stakes**: **MEDIUM**. Nutrition data is advisory, not medical. Every surfaced
  score + figure is labelled as such. Not a clinical tool; not audited as one.
- **Version**: No `package.json` version bump discipline yet (stays at `0.1.0`).
  Cache invalidation tracked via `public/service-worker.js CACHE` hash set by
  `build.mjs` to the latest commit SHA.

## Problem

Open Food Facts gives raw product data but no decision-making layer. CIQUAL gives
gold-standard nutrition but is a lookup table, not an app. Users want:

- "Is this product OK for my diet?" — personalised score in one tap.
- "What did I eat today vs. my targets?" — diary-style log with macros + micros.
- "How do I close the gaps?" — suggestions tied to what the app already measured.
- "Can I eat this while respecting [halal / vegan / low-FODMAP / gluten-free]?" —
  hard-constraint diet compliance with regulatory allergen cover.

## Metrics

> **[ASSUMED]** — no observability backend is wired yet. Metrics below are what
> the app SHOULD measure once `app_development_SKILL §X Observability` is
> implemented. Today the app tracks these locally via `public/core/telemetry.js`
> (opt-in, never transmitted).

- Time-to-first-scan from cold install.
- % scans that return a useful verdict (OFF hit + personal score + alternatives).
- % scans that fall back to LLM identify + its confidence distribution.
- Retention: days between sessions; diary completeness (main-3 meals logged).
- Cost: Groq tokens per user per day in server-mode deployments.

## Non-goals

- Not a clinical or medical-advice tool. Every score carries an editorial caveat.
- Not a social network; no "friends", feeds, or public profiles.
- Not a grocery-delivery hub; we stop at a plain-text shopping list (B3.3).
- Not a prescribing dietitian replacement. LLM suggestions are explicitly advisory
  (`identifyLowConfidence`, `pairingsSource` provenance strips).
- Not multi-locale yet past FR/EN. ES/IT/DE are marked "(beta)" in the lang
  select and fall back to EN via the i18n chain (B8.1).

## Current stack (auto-extracted)

| Area | Stack |
|------|------|
| Shell | PWA (`public/`) + Capacitor 6 wrapper for Android |
| Framework | Vanilla ES modules — no React / Vue / Svelte |
| Styling | Plain CSS tokens in `public/styles.css` |
| State | IDB (v6, 7 stores) + localStorage (scanneat.*) |
| Persistence | Same as above; backup/restore via `public/backup.js` |
| Workers | Service Worker (stale-while-revalidate shell + share_target) |
| Build | `build.mjs` (esbuild) bundles `engine.bundle.js` only |
| External APIs | Open Food Facts (barcode); Groq (`meta-llama/llama-4-scout-17b-16e-instruct`) |
| LLM modes | Direct (user's own Groq key) or Server (/api routes) |
| Server runtime | Vercel Functions (`api/*.ts`) + a local `server.ts` for dev |
| Tests | `node --test --experimental-strip-types` on 20+ `*-tests.ts` files — 449/449 pass |

## Current feature surface

Extracted from `public/features/`, `public/data/`, `public/core/`:

| Group | Features |
|-------|----------|
| Scan & identify | Barcode (BarcodeDetector live + file fallback, torch toggle) · Label OCR · Food photo identify (single) · Multi-item plate identify · Restaurant menu scan |
| Score & advise | Classic score · Personal score (diet/allergen/age/sex/activity/BMI/modifiers) · Alternatives · Pairings (Ahn 2011 recipe co-occurrence corpus) · Recipe ideas (LLM) · Pantry-first recipe search (LLM) |
| Log & track | Consumption log (7 macros + fiber + iron/Ca/vit-D/B12) · Hydration · Fasting timer + history + streak · Activity/MET · Weight log + forecast · Per-day notes · Recipes · Meal templates · Meal plan (7-day) |
| Targets & gaps | EFSA/WHO daily targets · Close-the-gap food suggestions (uses food-db + custom foods) · Grocery list aggregator |
| Data ops | Backup / restore (JSON) · CSV + Health JSON export · Multi-profile snapshots · CSV import from MFP + Cronometer |
| Shell | PWA install prompt · Dark/Light/Auto theme · FR/EN + ES/IT/DE beta · Reminder scheduler · Telemetry (local-only, opt-in) |

## Data sources + provenance

> Every data source carries its citation in-file. Audit has already been
> performed on the food DB (pending regeneration) and the pairings (regenerated
> from Ahn 2011). Other provenance audits already passed.

| Source | Used For | File |
|--------|----------|------|
| ANSES CIQUAL 2020 (DOI 10.5281/zenodo.4770600) | Built-in food DB per-100 g macros | `public/data/food-db.js` — values hand-transcribed; `tools/generate-food-db.mjs` committed for authoritative regen |
| Open Food Facts | Barcode product lookup + ingredient taxonomy + CIQUAL code index | `off.ts` + `tools/extract-ciqual-codes.mjs` → `tools/_ciqual-codes.json` |
| Ahn et al. 2011, Sci. Rep. 1:196 | Ingredient pairings (recipe co-occurrence subset, s3.csv) | `public/data/pairings.js` — generated by `tools/generate-pairings.mjs` with SHA-256 input pin |
| Ainsworth 2011 Compendium of Physical Activities | MET table | `public/data/activity.js` |
| EFSA PRI / WHO Guidelines | Daily nutrient targets (protein, fiber, iron, Ca, vit D, B12, Na, sugar, sat-fat) | `public/data/profile.js` |
| EU Regulation 1169/2011 Annex II | Allergen list (14 mandatory) | `public/core/allergens.js` — list is regulatory; detection regexes are editorial |
| Burdock 2010, Fenaroli's Handbook of Flavor Ingredients | Indirect, via Ahn 2011 | — |

## Risks (high-level)

| Risk | Status |
|------|--------|
| Hand-transcribed food-db values | Flagged in header as approximate. `tools/generate-food-db.mjs` is committed; running it in an unrestricted environment replaces the values with bit-for-bit ANSES exports. |
| Groq free-tier rate limit if many users in server mode | No quota protection yet. [ASSUMED] traffic is low. |
| LLM hallucinations on unusual ingredients | `reconcileWithFoodDB` swaps LLM macros for DB values when available; else source is labelled `source: 'llm'`. |
| IDB data loss on browser clear | Backup / restore is surfaced in Settings; no cloud sync. |
| Cost of native wearable sync (Apple Health / Google Fit) | Not done. Documented as out of PWA scope; needs Capacitor plugin. |
