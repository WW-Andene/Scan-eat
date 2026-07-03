# Scan'eat

A PWA for French-speaking users that scans food products + food photos, scores
them against the user's diet / allergen / health profile, and tracks the day's
intake against EFSA / WHO targets.

> **Status**: pre-1.0. Data is stored locally only. No account system. No
> cloud sync. See [docs/00-brief.md](docs/00-brief.md) for the full product
> brief and [docs/DECISIONS.md](docs/DECISIONS.md) for the architecture
> decisions history.

---

## What it does

| Surface | Features |
|---------|----------|
| Scan | Live barcode (BarcodeDetector + torch) · Label OCR · Food photo (single + multi-item plate) · Restaurant menu scan |
| Score | Classic score · Personal score (diet, allergens, age, sex, activity, BMI, modifiers) · Alternatives · Ingredient pairings (Ahn 2011 corpus) · Chef recipe ideas (LLM) · Pantry-first recipe search |
| Log | 7 macros + fiber + iron/Ca/vit-D/B12 · Hydration · Fasting timer + streak + history · Exercise (MET-based) · Weight + forecast · Per-day notes · Recipes · Meal templates · 7-day meal plan |
| Analysis | Close-the-gap suggestions · Grocery list aggregator · Weekly rollup · Progress charts |
| Data ops | Backup / restore (JSON) · CSV + Health JSON export · MFP / Cronometer CSV import · Multi-profile snapshots |
| Shell | Install prompt · Dark/Light/Auto theme · FR/EN + ES/IT/DE (beta) · Local meal reminders · Opt-in telemetry (stays on device) |

---

## Architecture

- **Runtime**: Kotlin/JVM + Ktor now owns the native `/api/*` route surface for scoring, barcode fallback, menu/recipe helpers, pantry suggestions, and recipe import. The existing browser/PWA and TypeScript files are retained as legacy/reference source while native parity work continues; they were not deleted wholesale.
- **Shell**: PWA under `public/`. Service Worker: stale-while-revalidate + `share_target`.
- **Persistence**: IndexedDB (v6, 7 stores) + `localStorage` for settings, snapshots, and
  small feature state (meal plan, fasting, hydration, per-day notes, custom foods).
- **External APIs**: Legacy TypeScript routes still document/use Open Food Facts and Groq. The Kotlin native route path does not proxy those LLM routes and uses deterministic native helpers plus an embedded barcode catalog instead.
- **Native wrap**: Capacitor 6 (Android only) — see `.github/workflows/android.yml`
  for the APK build path.
- **Tests**: `node --test --experimental-strip-types` on ~20 `*-tests.ts` files.
  920 tests at the time of writing.

Folder layout:

```
public/                  Static shell (deployed as-is)
  app.js                 Orchestration layer
  core/                  i18n, presenters, app-settings, telemetry, scoring rules
  data/                  IDB + localStorage storage modules
  features/              Self-contained UI + state modules
  styles.refactored.css  Base responsive stylesheet
  styles.rework.css      Design-token/component override layer
  service-worker.js      PWA shell + share_target
  index.html             Single-page entry

src/main/kotlin/         Kotlin Ktor application, native API routes, embedded catalog, native data repository, scoring engine
src/main/resources/      Native JSON resources translated from legacy data modules for JVM/Android reuse
public/                  Legacy static shell retained until native feature parity replacement lands
api/, src/*.ts           Production TypeScript API/scoring path used by Vercel

tools/                   Data-generation scripts (commit-as-infrastructure)
docs/                    Brief, decisions, assumptions, flows, ADRs

*-tests.ts               Per-feature test files (see package.json scripts.test)
```

---

## Getting started

### Local dev

```bash
# requires JDK 21+
gradle run                 # serves public/ on :5173 + /api/* routes
gradle test                # Kotlin test suite

# optional legacy web asset checks still use Node 22+
npm install
npm run build:web
```

### Deploying the web shell

Vercel deployment picks up `api/*.ts` as serverless functions and serves
`public/` statically. `GROQ_API_KEY` must be set as a project env var for
server mode to work; otherwise the app remains functional in direct mode
(user pastes their Groq key in Settings).

### Android APK

`.github/workflows/android.yml` runs `cap sync android` + Gradle build on
push. Artifacts are uploaded to the workflow run. See the workflow file for
signing details.

---

## Data + provenance

Every data file in the repo cites its source. See [docs/00-brief.md](docs/00-brief.md#data-sources--provenance)
for the full table. Highlights:

| File | Source |
|------|--------|
| `public/data/food-db.js` | ANSES CIQUAL 2020 (hand-transcribed; `tools/generate-food-db.mjs` for bit-for-bit regen) |
| `public/data/pairings.js` | Ahn et al., *Scientific Reports* 1:196 (2011), DOI 10.1038/srep00196 |
| `public/data/activity.js` (MET) | Ainsworth 2011 Compendium of Physical Activities |
| `public/data/profile.js` (targets) | EFSA PRI + WHO guidelines |
| `public/core/allergens.js` | EU Regulation 1169/2011 Annex II (list is regulatory; detection regexes are editorial) |
| Label / food ID | Groq Llama 4 Scout via `api/identify*.ts` |

---

## Versioning

SemVer. Currently **v0.2.0** (pre-1.0). Backwards-incompatible changes bump
the minor until 1.0 ships. Each feature-complete batch lands with a commit
tagged `B<n>.<m>` in the message.

Cache invalidation for the PWA shell is **automatic** — `build.mjs` sets the
service-worker cache key to the current commit SHA, so users on an old
shell get a clean update on next load.

---

## License

**Licensing decision pending** — see `LICENSE.TODO` for the open question.
Until resolved, redistribution / derivative works / commercial use are not
authorised by default. Open an issue or contact the owner.

---

## Privacy

See [PRIVACY.md](PRIVACY.md). One-line summary: all user data stays on the
device unless the deployer explicitly configures server-mode Groq, in which
case label photos and food photos are sent to Groq for identification. No
other third party receives user content.

---

## Contributing

Small, focused pull requests welcome. Each feature should land with tests in
a dedicated `*-tests.ts` file following the existing naming convention. Do
not extend `presenter-tests.ts` — it already covers the bulk of
`public/core/presenters.js` and new features should live in their own file.

Provenance rules:

- Every piece of data shipped in the repo must cite its source.
- Hand-transcribed approximations are allowed if the header clearly flags
  them as such + points to a generator that produces the authoritative form.
- Regex rules (allergens, diets) are editorial; document that in-file.

Run the full suite before opening a PR:

```bash
npm run build:web && npm test
```
