# Scan'eat — Open Findings

Consolidated from `doc/ROADMAP.md`, `doc/AUDIT_FINDINGS.md`, `doc/RND_ROADMAP.md`,
and `design_audit.md` (all four superseded and removed by this doc). Those
files covered ~60+ audit passes; the overwhelming majority of their findings
are fixed and verified against the current codebase. This file keeps only
what's still actually open, so the backlog doesn't have to be re-derived by
reading four largely-resolved documents.

Severity scale: **HIGH** (wrong results / security / safety), **MEDIUM** (real
but bounded impact), **LOW** (edge case or polish), **INFO** (needs a product
decision, not a code fix).

---

## HIGH

### Server/app scoring-logic drift has no guard
**Where:** `scan-eat-server/src/main/kotlin/fr/scanneat/shared/*` vs
`scan-eat-android/.../domain/engine/scoring/*`, `.../domain/model/*`,
`.../domain/engine/nutrition/OffMapper.kt`

The server's scoring engine, additive DB, domain models, and OFF mapper are
hand-maintained copies of the app's domain code. A prior audit found them 15+
fixes behind. Re-verified: sampled functions (e.g. `checkVeto()`) are
currently byte-identical logic between the two copies — they are *not*
drifted right now — but no CI guard exists anywhere (`android-build.yml` and
`server-build.yml` are the only workflows; neither diffs the two copies), so
nothing prevents the next app-side fix from silently missing the server
again.

**Fix options, increasing effort:**
1. CI job that normalizes both copies and diffs them, failing the build on divergence.
2. Shared JSON fixture set (product in → expected score/grade/flags out) run by both Android and server test suites.
3. Extract the pure-Kotlin domain code into a shared Gradle module consumed by both apps — removes the copy entirely.

### Scoring engine has no localization — ~100 hardcoded English strings
**Where:** `domain/engine/` — `ScoringEngine.kt`, every scoring `Pillar`,
`AdditivesDb.kt`, `AllergenDetector.kt`, `DietChecker.kt`,
`PersonalScoreEngine.kt`, plus Biolism's `KetoPhaseCalculator`/
`BiolismConstants`.

Re-verified, doc corrected: `DietChecker.kt`, `PersonalScoreEngine.kt`, and
`AllergenDetector.kt` already have a working `lang: String = "fr"` parameter
and branch on it — the language plumbing is not entirely absent. The real
gap is narrower: `ScoringEngine.kt`, the Pillar files, and `AdditivesDb.kt`
have no `lang` parameter and hardcode English (deduction reasons,
`gradeVerdict()`, additive concern/source descriptions). Raw literal counts:
`AdditivesDb.kt` ~276 (mostly reference-source citations, may not need
translating), `NutritionalDensityPillar.kt` ~31, `IngredientIntegrityPillar.kt`
~11, `ProcessingPillar.kt` ~9, Biolism's `BiolismConstants.kt`/
`KetoPhaseCalculator.kt` ~22/~11. Real user-facing untranslated string count
is closer to **60–80**, not ~100. Fixing this means extending the existing
`lang` pattern from the three files that already have it into
`ScoringEngine.kt`/Pillars/`AdditivesDb.kt`, not building the mechanism from
scratch.

---

## MEDIUM

### ~30 UI strings still hardcoded instead of using `stringResource`
62 files already use `stringResource(...)`, but ~30 `Text("...")` literal
calls remain in the presentation layer (confirmed exact count: 30). Many of
these are unit/symbol literals ("g", "kcal", "•", "−") rather than real
sentences — lower priority than the count alone suggests. Low effort,
mechanical cleanup.

### Light-mode gold values not consolidated
`Gold` (0xFFC9A84C), `LightGoldAccent` (0xFF8B6914), and `LightColors.primary`
(0xFFA07828) are three different hex values for one brand hue. A prior pass
computed real WCAG contrast for each and confirmed they're each correctly
tuned for a *different* contrast role (text-on-background vs. button-fill) —
so a blind merge would make one role's contrast worse. Consolidating
correctly requires deriving all light-mode variants from one documented
OKLCH lightness-shift rule, then visually re-verifying — not yet done.

### `ScanEatCard` migration incomplete
Re-verified, count corrected upward: only 4 call sites are migrated onto the
shared `ScanEatCard` primitive (`ResultBanners.kt` ×3, `WeightScreen.kt` ×1).
**29 hand-rolled `Surface(...)` cards across 15 files** remain unmigrated —
Biolism (`BiolismScreen`, `BiolismOnboardingScreen`, `BiolismProfileScreen`,
`LiveWeightCard`, `FastingSection`, `HeroCard`, `KetosisSection`,
`TrackerScreen`, `DataScreenComponents`, `MacroTargetsCard`,
`BodyCompositionCard`, `SessionHistoryCard`), `OnboardingScreen`,
`SettingsScreen`, `RemindersCard`. Not "~12" as previously estimated — the
Biolism feature area alone accounts for most of the remaining count.

### Status/navigation bars — pre-Compose flash only (downgraded from black-bars claim)
Re-verified: `MainActivity.kt:54-57` already calls
`WindowCompat.getInsetsController(...).isAppearanceLightStatusBars`/
`isAppearanceLightNavigationBars` at runtime based on the active theme — the
bars **do** follow the theme once Compose is running. Only
`values/themes.xml`'s static `android:statusBarColor`/`navigationBarColor`
(black, used for the pre-Compose window-background flash) remain hardcoded.
This is now a brief-flash cosmetic issue, not a persistent black-bars bug —
downgraded from MEDIUM to LOW.

---

## LOW

- **Room migration schemas never committed** (`app/schemas/` doesn't exist;
  `AppDatabase.kt` has 8 migrations, `MIGRATION_1_2` through `MIGRATION_8_9`,
  all wired in `DatabaseModule.kt`) — needs a real Android SDK build to
  generate, add an instrumented `MigrationTestHelper` test walking the full
  1→9 chain.
- **Health Connect duplicate weigh-in records** — `writeWeight()` inserts a
  new record on every call instead of upserting by `clientRecordId`. Blocked:
  the pinned `androidx.health.connect:connect-client:1.1.0-alpha07`'s
  `Metadata` constructor shape needs verifying against a real build before
  changing this.
- **Icon size sweep incomplete** — a 3-size token (`IconSize`) exists, but
  ~80 call sites still use ad hoc values (12–64dp); needs a visual pass to
  resize safely.
- **12+ IconButtons in dense list rows sit at 32–36dp**, below the 48dp touch
  target guideline. Deliberately deferred — these are all delete/dismiss
  actions that land on a confirmation dialog, so it's a friction issue, not a
  data-loss risk.
- **Ripple pressed-state tokens exist but aren't wired** — `GoldPressed`/
  `AccentCoralPressed` tokens exist in `Colors.kt` but custom ripple wiring
  requires the experimental `LocalRippleConfiguration` API, unverified without
  a working build.
- **Live TalkBack/Switch Access trace not run** — requires a real
  device/emulator with assistive tech active; not something a source-only
  audit can verify.
- **Grain/noise texture** on the OLED background — cosmetic, low priority.

---

## INFO — needs a product decision, not a code fix

- **Multi-profile plumbing** — `profileId` is threaded through ~15+ Room
  entities/DAOs (Consumption, Weight, Activity, Scan, CustomFood, Recipe,
  MealTemplate) always populated with the literal `"default"`, and
  `domain/model/Profile.kt` **already exists as a complete data class**
  (id, name, sex, age, diet, allergens, activity, goal, ...) — not just a
  stray column. `UserPreferences.kt` only ever stores one profile via flat
  DataStore keys; there is no `ProfileEntity`/DAO, no profile table, and no
  switcher UI. Given the domain model already exists, finishing real
  multi-profile support (a `ProfileEntity`/DAO, a switcher UI, and reworking
  `UserPreferences` off single-profile keys) is a **medium lift that
  completes work already started**, not a from-scratch feature — this
  should be the default direction over deleting the `profileId` plumbing,
  absent an explicit product decision to drop multi-profile support
  entirely.
- **Multi-provider LLM API key support** (oldest open roadmap item) —
  currently single-provider (Groq) for the vision LLM; diversifying reduces
  a single point of failure for the OCR fallback path.
- **Groq `response_format` JSON mode** — parked pending a live API check
  this can't safely be verified without hitting the real endpoint.

---

*Explicitly rejected, not tracked further:* a crowdsourced/community
correction loop — Open Food Facts already provides that data layer; building
a second one would duplicate it for no clear gain.
