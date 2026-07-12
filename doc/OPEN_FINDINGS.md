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
fixes behind. Nothing currently prevents the next app-side fix from silently
missing the server again.

**Fix options, increasing effort:**
1. CI job that normalizes both copies and diffs them, failing the build on divergence.
2. Shared JSON fixture set (product in → expected score/grade/flags out) run by both Android and server test suites.
3. Extract the pure-Kotlin domain code into a shared Gradle module consumed by both apps — removes the copy entirely.

### Scoring engine has no localization — ~100 hardcoded English strings
**Where:** `domain/engine/` — `ScoringEngine.kt`, every scoring `Pillar`,
`AdditivesDb.kt`, `AllergenDetector.kt`, `DietChecker.kt`,
`PersonalScoreEngine.kt`, plus Biolism's `KetoPhaseCalculator`/
`BiolismConstants`.

These are pure domain functions with no language parameter at all — the
app's FR/EN toggle has no effect on this layer (`gradeVerdict()` and similar
outputs are English literals regardless of the user's language setting).
Fixing this means threading a language parameter through the whole scoring
call chain and adding ~100 string resources — a real, standalone piece of
work, not a copy-tone edit.

---

## MEDIUM

### Status/navigation bars stay black in light theme
**Where:** `scan-eat-android/app/src/main/res/values/themes.xml`

`android:statusBarColor`/`navigationBarColor` are hardcoded black and
`windowLightStatusBar`/`windowLightNavigationBar` are false, with no
`values-night/themes.xml` override. Switching to light theme in Settings
makes the Compose surface white but leaves the system bars solid black.

**Fix:** Use `WindowCompat.getInsetsController` in `MainActivity` to set
`isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` based on the
active theme, and add a light-parent `values/themes.xml` +
`values-night/themes.xml` pair for the window-background flash before Compose draws.

### ~30 UI strings still hardcoded instead of using `stringResource`
62 files already use `stringResource(...)`, but ~30 `Text("...")` literal
calls remain in the presentation layer. Low effort, mechanical cleanup.

### Light-mode gold values not consolidated
`Gold` (0xFFC9A84C), `LightGoldAccent` (0xFF8B6914), and `LightColors.primary`
(0xFFA07828) are three different hex values for one brand hue. A prior pass
computed real WCAG contrast for each and confirmed they're each correctly
tuned for a *different* contrast role (text-on-background vs. button-fill) —
so a blind merge would make one role's contrast worse. Consolidating
correctly requires deriving all light-mode variants from one documented
OKLCH lightness-shift rule, then visually re-verifying — not yet done.

### `ScanEatCard` migration incomplete
~12 hand-rolled `Surface(...)` cards across the app (Biolism tracker,
Onboarding, Settings, `RemindersCard`, etc.) haven't been migrated onto the
shared `ScanEatCard` primitive yet (3 call sites in `ResultBanners.kt` are
migrated as proof of the pattern).

---

## LOW

- **Room migration schemas never committed** (`app/schemas/` doesn't exist,
  `exportSchema = true` is configured but nothing has snapshotted the
  historical schema JSONs) — needs a real Android SDK build to generate, add
  an instrumented `MigrationTestHelper` test walking the full migration chain.
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

- **Dead multi-profile plumbing** — `profileId` is threaded through the
  consumption schema/DAO/domain model as if multiple profiles were
  supported, but only one profile (`"default"`) can ever exist. Decide:
  implement real profiles, or drop the plumbing.
- **Multi-provider LLM API key support** (oldest open roadmap item) —
  currently single-provider (Groq) for the vision LLM; diversifying reduces
  a single point of failure for the OCR fallback path.
- **Groq `response_format` JSON mode** — parked pending a live API check
  this can't safely be verified without hitting the real endpoint.

---

*Explicitly rejected, not tracked further:* a crowdsourced/community
correction loop — Open Food Facts already provides that data layer; building
a second one would duplicate it for no clear gain.
