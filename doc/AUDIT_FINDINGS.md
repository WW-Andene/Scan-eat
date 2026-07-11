# Scan'eat — End-to-End Audit Findings

Audit of `scan-eat-android/` and `scan-eat-server/` on branch
`claude/project-setup-roadmap-2bbp58`, July 2026. The app side had already
been through 60+ audit passes this project; the genuinely under-audited
surface was the server and the app↔server boundary, which is where almost
everything below was found.

Severity scale: **HIGH** (wrong results / security), **MEDIUM** (real but
bounded impact), **LOW** (edge case or polish), **INFO** (worth knowing).
Each finding lists the dimension it belongs to (Bug / Incoherence /
Inconsistency / Structure / Design) and the concrete fix.

---

## Part 1 — Already fixed and pushed this session

These were fixed directly (before the write-findings-only instruction),
each in its own commit, server side verified with `gradle test` locally:

| Commit | Fix |
|---|---|
| `35e5d90` | Server `AdditivesDb` synced with app: 12 missing entries (E120, E162, E140, E153, E200, E262, E290, E401, E406, E410, E414, E420), NFD accent normalization, memoized lookup |
| `fc31be7` | Server engine parity: omega-3 double-count removed, sugary-beverage veto (cap 30) ordered before generic sugar veto (cap 40), `roundToInt()` instead of truncation |
| `f37e360` | Server OFF mapper synced: whole-tag-hierarchy category matching, missing-nutriments tolerance (the "Coca-Cola not found" bug), kJ→kcal conversion on the `energy_100g` fallback, vitamin A/E/K + B6 + sodium + omega-3 mapping, relaxed `isOffSparse`, fuller LLM merge, sugars conflict check |
| `ad8540e` | Server OFF lookups now try all GTIN candidate encodings (UPC-E cans, GTIN-14 case codes, UPC-A/EAN-13 padding) like Direct mode |
| `2ba8d21` | `omega_3_g` added to the server score-response DTO (client already parsed it; server never sent it) |
| `1ef9a0c` | Groq client: `expectSuccess = true` so 429/5xx surface as real status codes; fail-fast on non-retryable 4xx; 401/403 mapped to `invalid_api_key` |
| `fd31aa5` | SSRF guard on `/api/fetch-recipe`: host must not resolve to loopback/private/link-local/metadata/ULA addresses; redirects followed manually with per-hop re-validation |
| `b6e3ffe` | `ENGINE_VERSION` 2.2.0 → 2.3.0 on both sides, so `ScanRepository`'s rescore-cached-history-on-version-change mechanism actually fires after the behavior changes above |
| `4938265` | Server CI workflow (`server-build.yml`: gradle test + shadowJar) + Gradle wrapper — the server previously had **zero** CI, which is how all of the above drifted in unnoticed |

## Part 1B — F2-F30 fixed on request ("apply the fixes")

Every finding below except F1, F15, F18, and F22 (see "Still open" below for
why) was subsequently fixed, one concern per commit. Server-side commits
verified with `gradle test` locally; app-side commits were read carefully but
**not compiled** — no Android SDK is available in this sandbox, so CI is the
real verification gate for these (see the coverage note at the end of this
file).

| Commit | Finding | Fix |
|---|---|---|
| `a99abb3` | F24 | Weight-delta operator-precedence bug — `(latest−first)×10`, not `latest−first×10` |
| `dec6ecf` | F29, F30 | Gluten regex missing the literal word "gluten"; `pâté` accent bug; `noix` over-matching coconut/nutmeg — plus new `AllergenDetectorTest.kt` |
| `f7884dc` | F2 | `rejectIfTooLarge()` 413 guard on every server POST route |
| `b221e0a` | F3 | `lang`/`model` threaded through `ScoreRequest`/`ServerScoreRequest` and `ScanRepository.scoreViaServer()` |
| `18ee360` | F4 | `ServerScoreResponse.toDomain()` no longer overlays server flags onto the locally recomputed audit |
| `6a069aa` | F5 | `yieldToServings()` returns `Int?` instead of fabricating `1` |
| `72fa1a1` | F6 | `numFrom()` extracts the leading numeric token from values like `"350 calories"` |
| `1d0c944` | F7 | `parseSchemaOrgRecipe` made `internal`; real `FetchRecipeRouteTest.kt` replacing the no-op test |
| `9da54a2` | F8 | CORS driven by `ALLOWED_ORIGINS` env var; `anyHost()` only in dev mode |
| `d6d3d3c` | F11 | `/health`'s version generated from the Gradle project version at build time |
| `e456626` | F16 | Prompt-injection hardening, `hasImages` fallback-model guard, and retry backoff ported to the server's LLM parser |
| `d07f767` | F9 | Backup import for `scan_history`/`consumption_log` deduped by natural key |
| `e94c843` | F14 | Result screen's "Log" button now uses the same tab-switch nav options as `MainShell` |
| `cde3a24` | F17 | `OcrParser` takes the shared Moshi singleton instead of building its own |
| `030dfed` | F13 | Meal reminders bounded to a 3h staleness window; enabling a past-due reminder marks it fired-today |
| `16890ad` | F26 | Fasting streak given the same 1-day grace as the journal streak |
| `6380975` | F28 | Meal-plan notes sanitize embedded newlines; dead `component5` extension removed |
| `91591ff` | F27 | Hydration DataStore keys older than 90 days pruned on write |
| `c0e23a7` | F21 | Weight forecast treats near-zero slope (< 20g/week) and >104-week horizons as `Flat` |
| `ab2709a` | F23 | Notification channel renamed off `biolism_reminders`; legacy channel deleted |
| `25f1a20` | F19, F20 | `FoodEntry` gained real iron/calcium/vitD/B12 values; `closeTheGap()`'s density lambdas wired to them; rollup fiber/micro fields populated |
| `12fcf69` | F10 | Stale `scanneat-complete.zip` removed; `*.zip` gitignored |
| `10b8491` | F12 | Groq API key encrypted at rest (`ApiKeyCipher`, AES-256/GCM via AndroidKeyStore), with transparent legacy-plaintext migration |
| `0017b13` | F25 | Profile, settings (minus the API key), reminder settings, fasting, hydration, day notes, and meal plan added to backup export/import (format v2) |

### Still open

- **F1** (drift guard) — a structural/process fix (CI diff check, parity
  tests, or a shared Gradle module), not a single code change; left as
  documented options above.
- **F15** (dead multi-profile plumbing) — requires a product decision
  (implement real profiles vs. delete the plumbing), not something to guess.
- **F18** (Health Connect `clientRecordId` dedup) — the pinned
  `androidx.health.connect:connect-client:1.1.0-alpha07`'s `Metadata`
  constructor shape has "already broken once across this pinned alpha's
  versions" per `WeightRepository.kt`'s own comment, and no cached artifact
  or Android SDK was available in this sandbox to verify the current shape.
  Guessing it blind risked shipping a broken build for a LOW-severity fix —
  left open rather than guessed.
- **F22** (committed Room migration schemas) — needs a real Android SDK
  build to generate `app/schemas/`, unavailable in this sandbox.

---

## Part 2 — Full findings detail (fix status per finding, see Part 1B)

### F1 · HIGH · Structure — the shared-code copy has no drift guard

**Status: OPEN** — see "Still open" in Part 1B for why.

**Where:** `scan-eat-server/src/main/kotlin/fr/scanneat/shared/*` vs
`scan-eat-android/.../domain/engine/scoring/*`, `.../domain/model/*`,
`.../domain/engine/nutrition/OffMapper.kt`

**Finding:** The server's scoring engine, additive DB, domain models and
OFF mapper are hand-maintained copies of the app's domain code
("kept in sync manually", per the file headers). This session found them
15+ fixes behind — every Part 1 item above is a symptom of this one
structural problem. Nothing prevents the next app-side fix from silently
missing the server again.

**How to fix (in order of increasing effort):**
1. *Cheap guard:* add a CI job that normalizes both copies (strip package
   lines and the `fr.scanneat.domain.model.` qualifier) and `diff`s them,
   failing the build on divergence. ~20 lines of shell in a workflow.
2. *Parity tests:* a shared JSON fixture set (products in, expected
   score/grade/flags out) executed by both the Android unit tests and the
   server tests, so semantic drift fails even if the text of the files
   diverges intentionally.
3. *Real fix:* extract the pure-Kotlin domain code (models, engine,
   additives, mappers — it is already I/O-free) into a plain Kotlin JVM
   module in a Gradle composite build consumed by both `scan-eat-android`
   and `scan-eat-server`. Removes the copy entirely. This is a repo
   restructure; do it when both builds are green and quiet.

---

### F2 · MEDIUM · Bug — the 12 MB request-body limit is claimed but never enforced

**Status: FIXED — commit `f7884dc`.**

**Where:** `scan-eat-server/.../routing/RouteHelpers.kt:15`
(`MAX_BODY_BYTES`), referenced in `ScoreRoute.kt`'s doc comment
("Body: ScoreRequest JSON (max 12 MB)")

**Finding:** `MAX_BODY_BYTES` is declared and documented but no code reads
it. `call.receive<ScoreRequest>()` will buffer a body of any size, so a
client (or attacker) can post a multi-hundred-MB payload and OOM the
server.

**How to fix:** Add a helper in `RouteHelpers.kt` and call it first in
every POST route:

```kotlin
suspend fun ApplicationCall.rejectIfTooLarge(): Boolean {
    val len = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (len != null && len > MAX_BODY_BYTES) {
        respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Request body too large"))
        return true
    }
    return false
}
```

(Chunked uploads bypass Content-Length; for a complete fix, upgrade to
Ktor 3.x and `install(RequestBodyLimit) { bodyLimit { MAX_BODY_BYTES } }`.)

---

### F3 · MEDIUM · Incoherence — Server mode ignores the user's language and model settings

**Status: FIXED — commit `b221e0a`.**

**Where:** `scan-eat-server/.../model/ApiModels.kt` (`ScoreRequest`),
`ScoreRoute.kt:47` (`groqService.parseLabel(images, key)`),
app side `ServerScanApi.kt` (`ServerScoreRequest`),
`ScanRepository.scoreViaServer()`

**Finding:** In Direct mode the app threads `lang` (fr/en) and the
user-selected Groq model through every LLM call. `ScoreRequest` carries
neither field, so Server mode always parses labels in French with the
default model — the Settings choices silently stop applying the moment the
user switches API mode.

**How to fix:** Add `val lang: String? = null` and `val model: String? =
null` to `ScoreRequest` (server) and `ServerScoreRequest` (app);
in `ScoreRoute` pass them to `parseLabel(images, key, lang ?: "fr")` and
`complete(..., model = model ?: DEFAULT_GROQ_MODEL)`; in
`ScanRepository.scoreViaServer()` fill them from `prefs`. Backward
compatible in both directions since both fields default to null.

---

### F4 · LOW · Incoherence — server-mode results mix locally recomputed scores with server flags

**Status: FIXED — commit `18ee360`.**

**Where:** `scan-eat-android/.../data/repository/scan/ScanRepository.kt:404`

**Finding:** `ServerScoreResponse.toDomain()` recomputes the audit locally
(`scoreProduct(product)`) but then overwrites the freshly computed
red/green flags with the server's: `.copy(redFlags = audit.redFlags,
greenFlags = audit.greenFlags)`. If the two engines ever disagree again,
the flags shown won't match the pillar deductions shown next to them.

**How to fix:** Trust one source. Simplest: drop the `.copy(...)` and use
the locally computed audit wholesale (flags are derived from the same
pillars the UI renders). The server's flags are only needed if local
rescoring is ever removed.

---

### F5 · LOW · Bug — fetch-recipe fabricates "1 serving" when the page declares none

**Status: FIXED — commit `6a069aa`.**

**Where:** `scan-eat-server/.../routing/FetchRecipeRoute.kt`
(`yieldToServings`, used as `yieldToServings(...).toString()`)

**Finding:** When `recipeYield` is absent or unparsable the function
returns 1, so the response claims `servings: "1"` as if the page said so.
`FetchedRecipeResponse.servings` is already nullable.

**How to fix:** Change `yieldToServings` to return `Int?` (null when the
element is missing/unparsable) and pass
`yieldToServings(recipe["recipeYield"])?.toString()` through.

---

### F6 · LOW · Bug — fetch-recipe drops nutrition when calories carry units

**Status: FIXED — commit `72fa1a1`.**

**Where:** `scan-eat-server/.../routing/FetchRecipeRoute.kt` (`numFrom`)

**Finding:** schema.org sites commonly write `"calories": "350 calories"`.
`"350 calories".toDoubleOrNull()` is null → kcal parses as 0.0 →
`extractNutrition` returns null and the whole nutrition block is discarded.

**How to fix:** In `numFrom`, extract the first numeric token before
parsing:

```kotlin
Regex("""(\d+(?:[.,]\d+)?)""").find(content)?.groupValues?.get(1)
    ?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
```

---

### F7 · LOW · Test gap — the server's fetch-recipe test asserts nothing

**Status: FIXED — commit `1d0c944`.**

**Where:** `scan-eat-server/src/test/kotlin/fr/scanneat/ScoringEngineTest.kt`
(`fetch recipe schema org parser finds recipe name`)

**Finding:** The test builds an HTML fixture and then asserts the fixture
contains a string it itself wrote — it exercises none of the parser and can
never fail. The comment admits it ("test the JSON-LD extraction logic
inline via a minimal check").

**How to fix:** Make `parseSchemaOrgRecipe` `internal` (it is `private`
today) and assert on real behavior: name extracted, ingredients list
length, HowToStep text unwrapped, `PT1H30M` → 90 min, and the F5/F6 cases
above once fixed.

---

### F8 · LOW · Security hardening — CORS is `anyHost()`

**Status: FIXED — commit `9da54a2`.**

**Where:** `scan-eat-server/.../Application.kt:59`
(`anyHost() // tighten in production`)

**Finding:** The server answers CORS preflight for any origin. The only
client is the Android app (which doesn't use CORS at all), so the
permissive config buys nothing and would matter only if a browser client
appears.

**How to fix:** Either delete the CORS plugin entirely (native apps ignore
it), or drive it from an env var:
`System.getenv("ALLOWED_ORIGINS")?.split(',')?.forEach { allowHost(it) }`
and only `anyHost()` when `io.ktor.development` is true.

---

### F9 · LOW · Data — restoring the same backup twice duplicates history rows

**Status: FIXED — commit `d07f767`.**

**Where:** `scan-eat-android/.../data/backup/BackupRepository.kt:77-78`

**Finding:** `scan_history` and `consumption_log` ids are deliberately
reset to 0 on import (correct — prevents REPLACE-by-autoincrement
destroying unrelated local rows), but the trade-off is that importing the
same file twice inserts every row twice. The duplicates are user-visible
in Journal and history.

**How to fix:** Before insert, dedup against existing rows on a natural
key: `barcode + scannedAt` for scans, `date + slot + name(+grams)` for
consumption entries. A `SELECT` of existing keys inside the same
transaction, then `filterNot` on the bundle, keeps it O(n) and atomic.

---

### F10 · LOW · Structure — a 290 KB snapshot zip is committed at the repo root

**Status: FIXED — commit `12fcf69`.**

**Where:** `scanneat-complete.zip` (git-tracked)

**Finding:** A stale full-project archive lives in version control. It
bloats every clone, will only get staler, and duplicates what git itself
is for.

**How to fix:** `git rm scanneat-complete.zip`, add `*.zip` to
`.gitignore`. (Not done here per the findings-only instruction and
because deleting user files warrants an explicit OK.)

---

### F11 · INFO · Incoherence — `/health` hardcodes its own version string

**Status: FIXED — commit `d6d3d3c`.**

**Where:** `scan-eat-server/.../Application.kt:75` vs
`build.gradle.kts` (`version = "0.1.0"`)

**Finding:** The health endpoint's `"version" to "0.1.0"` is a literal,
independent of the Gradle project version; they will diverge on the first
version bump.

**How to fix:** Generate it at build time — e.g. the Gradle
`processResources` task expands a `version.properties` placeholder from
`project.version`, and `Application.kt` reads it once at startup.

---

### F12 · MEDIUM · Security — the Groq API key is stored in plaintext

**Status: FIXED — commit `10b8491`.**

**Where:** `scan-eat-android/.../data/local/prefs/UserPreferences.kt:32,58,70`
(`KEY_API_KEY` in the plain `scanneat_prefs` DataStore)

**Finding:** The user's Groq API key sits unencrypted in the app's
preferences file. App-private storage protects it from other apps on a
healthy device, but it is exposed verbatim in any device backup, on rooted
devices, and via `adb backup`/debugging on debuggable builds. No
`security-crypto`/Keystore usage exists anywhere in the app.

**How to fix:** Wrap the key (only this one value — the rest of the prefs
are not sensitive) with a Keystore-backed AES cipher: generate a key in
`AndroidKeyStore`, store `Base64(iv + ciphertext)` under `KEY_API_KEY`,
and decrypt in the `groqApiKey` flow. On first read after the update,
detect a legacy plaintext value (decryption fails), encrypt it in place.
Alternative with less code: `androidx.security:security-crypto`'s
`EncryptedSharedPreferences` for just this value (note the library is
deprecated-but-functional; the manual Keystore route is future-proof).

---

### F13 · LOW · Bug — meal reminders can fire hours stale

**Status: FIXED — commit `030dfed`.**

**Where:** `scan-eat-android/.../notifications/ReminderWorker.kt:72-87`
(`checkMeal`)

**Finding:** `checkMeal` deliberately fires on the first worker run
*at-or-after* the target time (correct for Doze-delayed workers), but has
no upper bound. Two consequences: (1) enabling a breakfast reminder at
22:00 fires the breakfast notification within the next worker period,
14 hours after breakfast time; (2) a device left idle all evening fires
the 19:00 dinner reminder at 23:50 when the user picks it up. The
hydration branch already has an hour-band guard (`now.hour in 8..21`);
the meal branches have nothing.

**How to fix:** Add a staleness window: fire only when
`now in target..target.plusHours(3)` (still generous for Doze delays),
and when the user *enables* a reminder whose time already passed today,
call `markFiredToday` for it so the first eligible fire is tomorrow.

---

### F14 · LOW · Incoherence — "Log" on the Result screen builds a different back stack than the tab bar

**Status: FIXED — commit `e94c843`.**

**Where:** `scan-eat-android/.../presentation/shell/AppNavGraph.kt:98`
(`onLog = { navController.navigate(TopTab.Diary.route) { launchSingleTop = true } }`)
vs `MainShell.kt:43-46`

**Finding:** The bottom bar switches tabs with
`popUpTo(startDestination) { saveState = true }; restoreState = true`,
but the Result screen's log action pushes the Diary tab with a bare
`navigate { launchSingleTop }`. The result: Diary lands *on top of*
Scan→Result, system back from Diary returns to the Result screen (not tab
behavior), and this Diary instance doesn't share saved state with the one
the tab bar restores.

**How to fix:** Use the exact same options as `MainShell`'s tab click:

```kotlin
navController.navigate(TopTab.Diary.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

---

### F15 · INFO · Structure — multi-profile plumbing exists but only one profile can ever exist

**Status: OPEN** — see "Still open" in Part 1B for why.

**Where:** `UserPreferences.kt` (flat `KEY_PROFILE_*` keys +
`KEY_ACTIVE_PROFILE`), `ConsumptionRepository`/`ConsumptionDao`
(`profileId` parameter, always `"default"`), `DiaryEntry.profileId`

**Finding:** `profileId` is threaded through the consumption schema, DAO
queries and domain model as if multiple profiles were supported, but the
profile itself lives in flat single-value preference keys — there is no
way to create a second profile, so every row is `"default"` forever. Dead
generality that each new feature has to keep threading through.

**How to fix:** Decide the product direction: either (a) implement real
profiles — move `Profile` into a Room table keyed by id, keep
`KEY_ACTIVE_PROFILE` as the switcher, and the existing `profileId`
plumbing starts paying for itself; or (b) drop the `profileId`
parameters/columns in the next schema migration and reintroduce them if
profiles ever become a feature. (a) is the direction the plumbing implies;
(b) is one small migration.

---

### F16 · MEDIUM · Inconsistency — the server's LLM parser missed the app's OcrParser fixes

**Status: FIXED — commit `e456626`.**

**Where:** `scan-eat-server/.../service/LlmLabelParser.kt` (prompts),
`scan-eat-server/.../service/GroqService.kt:117-131` (`complete` retry loop)
vs `scan-eat-android/.../data/repository/scan/OcrParser.kt`

**Finding:** Same manual-copy drift as the scoring engine (F1), on the LLM
side. Three concrete divergences, each one an app-side fix the server
never received:

1. *No prompt-injection hardening.* The app's label and identify prompts
   both instruct the model to treat all text in the image strictly as
   printed content, never as instructions. The server's `labelPrompt` /
   `identifyFoodPrompt` (and the menu/recipe prompts) have none of this —
   Server mode is the more exposed path and the less hardened one.
2. *Text-only fallback for vision requests.* `GroqService.complete()`
   switches to `FALLBACK_GROQ_MODEL` (llama-3.3-70b, text-only) on the
   last attempt unconditionally. The app's `callWithRetry` explicitly
   guards this with `hasImages` because sending images to a text-only
   model guarantee-fails the final retry instead of giving it a real one.
3. *No backoff.* The app delays `500ms × attempt` between retries; the
   server hammers immediately, which is exactly wrong for the 429 case.
   (Also, the server's identify prompt says "estimate conservatively"
   where the app's says "never output a literal 0 unless the food
   genuinely contains none" — the app version was tuned to stop the model
   from returning all-zero nutrition.)

**How to fix:** Port the app's versions: copy the two hardened prompts
into `LlmLabelParser.kt` verbatim (they are plain strings), add
`val hasImages = images.isNotEmpty()` to `complete()` and only use the
fallback model when `!hasImages`, and add `delay(500L * (attempt + 1))`
between attempts. Fold this into whatever drift guard F1 lands on.

---

### F17 · LOW · Inconsistency — OcrParser builds its own private Moshi

**Status: FIXED — commit `cde3a24`.**

**Where:** `scan-eat-android/.../data/repository/scan/OcrParser.kt:233`

**Finding:** An earlier pass (Q-3) routed all repositories through the
singleton Moshi provided by DI, but `OcrParser` still constructs its own
`Moshi.Builder().add(KotlinJsonAdapterFactory()).build()` — a second
reflection-based instance with its own adapter cache, and one more place
adapter configuration can silently diverge from the app-wide instance.

**How to fix:** Inject `Moshi` through the constructor
(`class OcrParser(private val groqApi: GroqApi, moshi: Moshi)`) and update
its provider in `DomainModule`/`NetworkModule` to pass the singleton.

---

### F18 · LOW · Data — re-logging weight writes duplicate Health Connect records

**Status: OPEN** — see "Still open" in Part 1B for why.

**Where:** `scan-eat-android/.../data/repository/health/HealthConnectRepository.kt:59-68`

**Finding:** `writeWeight()` inserts a new `WeightRecord` on every call.
Logging weight twice on the same day (e.g. correcting a typo) leaves both
records in Health Connect — other apps reading the store see two weigh-ins,
and the stale value can't be corrected from Scan'eat.

**How to fix:** Set `metadata = Metadata(clientRecordId = "scanneat-weight-$date", clientRecordVersion = System.currentTimeMillis())`
on the record — Health Connect upserts by `clientRecordId` (higher
`clientRecordVersion` replaces), so one record per day per app, updated in
place on re-log.

---

### F19 · MEDIUM · Bug — "Close the gap" silently works for only 2 of its 6 nutrients

**Status: FIXED — commit `25f1a20`.**

**Where:** `scan-eat-android/.../domain/engine/dashboard/DashboardAggregator.kt:224-231`
(`nutrientMap` density lambdas) and `.../domain/engine/nutrition/FoodDb.kt:22-31`
(`FoodEntry`)

**Finding:** `GAP_NUTRIENTS` advertises protein, fiber, iron, calcium,
vitamin D and B12, but the food-density lambdas for iron/calcium/vitD/B12
are hardcoded `{ 0.0 }` because `FoodEntry` has no micronutrient fields.
`density <= 0` skips every food, so those four `GapEntry`s are never
emitted — the feature silently degrades to protein + fiber suggestions
only, while the deficit for the other four (which `DailyTargets` computes)
is never surfaced.

**How to fix:** Extend `FoodEntry` with `ironMg`, `calciumMg`, `vitDUg`,
`b12Ug` (per 100 g, defaulting 0.0), fill them for the curated `FOOD_DB`
entries that are genuinely good sources (lentils/spinach → iron, dairy →
calcium+B12, oily fish → vitD+B12 …), and replace the `{ 0.0 }` lambdas
with the real field reads. Foods without data keep 0.0 and are skipped
naturally.

---

### F20 · INFO · Incoherence — rollup totals carry micro-nutrient fields that are never populated

**Status: FIXED — commit `25f1a20`.**

**Where:** `DashboardAggregator.kt:33-47` (`NutrientTotals.ironMg/
calciumMg/vitDUg/b12Ug/fiberG`) vs `rollup()` (:105-124), which never sets
them

**Finding:** `NutrientTotals` declares fiber + four micronutrients, but
`DayBucket` doesn't track them and `rollup()` never sums them, so
`total`/`avg` always report 0.0 for those fields. No current caller reads
them — but the first one that does will trust a silent zero.

**How to fix:** Either populate them (add the fields to `DayBucket` and the
`sumOf` chains — `DiaryEntry.consumed` already has them), or delete the
five dead fields until a real consumer exists. Populating is ~10 lines and
makes F19's deficits displayable per week; deleting is honest minimalism.
Pick one, don't leave the trap.

---

### F21 · LOW · Bug — weight forecast treats a 1-gram-per-week trend as a real trajectory

**Status: FIXED — commit `c0e23a7`.**

**Where:** `DashboardAggregator.kt:279-293` (`weightForecast`)

**Finding:** `Flat` is returned only when `weeklySlopeKg == 0.0` exactly.
A regression slope over noisy scale data is never exactly zero, so a
near-flat trend of e.g. 0.005 kg/week yields `Ok(weeks = 1400,
targetDate = 2053)` instead of "no meaningful trend". The absurd date
reaches the dashboard card.

**How to fix:** Treat `abs(weeklySlopeKg) < 0.02` (20 g/week — below any
scale's noise floor) as `Flat`, and additionally return `Flat` when the
computed horizon exceeds a sanity cap (e.g. `weeks > 104`).

---

### F22 · LOW · Structure — Room migrations have never been verified against real schemas

**Status: OPEN** — see "Still open" in Part 1B for why.

**Where:** `scan-eat-android/.../data/local/db/AppDatabase.kt:52-61`
(comment), `app/build.gradle.kts` (`room.schemaLocation`), missing
`app/schemas/` directory

**Finding:** `exportSchema = true` is configured but no `app/schemas/`
snapshots have ever been committed, so `MIGRATION_1_2` … `MIGRATION_6_7`
have only ever been validated by eyeball — `room-testing`'s
`MigrationTestHelper` can't run without the historical schema JSONs. The
explanatory comment also still says "see QUEUE.md", a file that no longer
exists.

**How to fix:** CI already builds the app: add a step that uploads (or a
one-time PR that commits) the generated `app/schemas/` directory, then add
an instrumented `MigrationTestHelper` test walking 1→7. Update the stale
comment to point at this findings doc instead of QUEUE.md.

---

### F23 · INFO · Incoherence — the notification channel is still named after Biolism

**Status: FIXED — commit `ab2709a`.**

**Where:** `scan-eat-android/.../notifications/NotificationHelper.kt:15`
(`CHANNEL_ID = "biolism_reminders"`)

**Finding:** The channel carries *all* app reminders (meals, hydration,
weight) and the Biolism feature itself was renamed "Métabolisme" — but the
system-settings channel users see is still the Biolism one. Channel ids
are persistent per install, so this is also the only place the old name
survives user-visibly.

**How to fix:** Create a new channel `"reminders"` (localized display
name), post to it, and call
`notificationManager.deleteNotificationChannel("biolism_reminders")` once
at startup. Android migrates nothing automatically, but the user's
importance/sound overrides on the old channel are the only thing lost —
acceptable for a rename this early.

---

### F24 · HIGH · Bug — the weight delta shown to the user is computed with a precedence error

**Status: FIXED — commit `a99abb3`.**

**Where:** `scan-eat-android/.../data/repository/health/WeightRepository.kt:92`

**Finding:**

```kotlin
val delta = (latest.weightKg - first.weightKg * 10.0).roundToInt() / 10.0
```

`*` binds before `-`, so this computes `latest − (first × 10)` instead of
`(latest − first) × 10`. For latest = 79 kg, first = 80 kg the intended
delta is −1.0 kg; this yields `(79 − 800).roundToInt() / 10 = −72.1 kg`.
The value renders on both the Weight screen (`WeightScreen.kt:94-96`) and
the Dashboard weight card (`WeightSummaryCard.kt:28-33`), always as a huge
negative number colored green ("losing weight").

**How to fix:** Parenthesize the subtraction:

```kotlin
val delta = ((latest.weightKg - first.weightKg) * 10.0).roundToInt() / 10.0
```

and add a unit test pinning `summarize()`'s delta for a two-entry fixture
(this is exactly the class of bug a one-line test catches forever).

---

### F25 · MEDIUM · Data — backup exports none of the DataStore-backed data

**Status: FIXED — commit `0017b13`.**

**Where:** `scan-eat-android/.../data/backup/BackupRepository.kt:43-56`
(`exportToJson`), vs the DataStore repositories (UserPreferences,
HydrationRepository, FastingRepository, MealPlanRepository,
DayNotesRepository, RemindersRepository, ComparisonRepository)

**Finding:** The backup bundle covers the seven Room tables only. Its own
doc comment promises restore "on this device or a new one after
reinstalling," but a restore on a new device silently loses: the entire
profile (age/weight/height/diet/allergens — which drives personal scoring),
app settings (API mode, server URL, language, theme, accessibility modes),
hydration history, fasting history and streak, the meal plan, day notes,
and reminder settings. The user discovers this only after the old device
is gone.

**How to fix:** Add the DataStore payloads to `BackupBundle`: profile +
settings as typed fields read from `UserPreferences`, and the
feature stores either as typed lists (fasting history, meal plan) or as a
raw `Map<String, String>` snapshot per store. On import, write them back
before the Room transaction. Bump `BACKUP_FORMAT_VERSION`; old files
simply leave the new fields null (the version guard already tolerates
older files). Deliberately exclude the Groq API key from the export —
a backup JSON shared for debugging must not leak a credential (ties into
F12).

---

### F26 · LOW · Inconsistency — the fasting streak has no grace day but the journal streak does

**Status: FIXED — commit `16890ad`.**

**Where:** `scan-eat-android/.../data/repository/health/FastingRepository.kt:122-134`
vs `DashboardAggregator.logStreakDays` (:159-173)

**Finding:** `logStreakDays` deliberately tolerates "today not yet logged"
by starting the walk from yesterday. The fasting streak requires a
completed fast dated *today* or reports the streak broken — so a user who
fasts every day sees their fasting streak flip to 0 every morning until
that day's fast completes, while the journal streak next to it stays
intact. Two streaks, two rules.

**How to fix:** Apply the same 1-day grace in `FastingRepository.streak`:
if today's date isn't in `doneDates`, start `expected` at yesterday
instead of returning 0 through the loop mismatch.

---

### F27 · LOW · Data — hydration DataStore grows by one key per day forever

**Status: FIXED — commit `91591ff`.**

**Where:** `scan-eat-android/.../data/repository/health/HydrationRepository.kt:47`
(`key(date)` = `hyd_<date>`, no pruning anywhere)

**Finding:** Every day of use adds a permanent `hyd_2026-07-11`-style key.
DataStore loads the whole preferences file into memory on first access, so
years of use mean thousands of stale keys parsed on every app start.
`MealPlanRepository` in the same codebase already prunes (KEEP_DAYS_PAST);
hydration never does.

**How to fix:** On repository init (or first `add()` per session), edit
once and drop keys older than the longest window any UI reads (90 days
covers the history views). If hydration history ever needs to be
long-term (or backed up per F25), move it to a small Room table like
weight/activity instead.

---

### F28 · LOW · Bug — a meal-plan note containing a newline corrupts the stored plan

**Status: FIXED — commit `6380975`.**

**Where:** `scan-eat-android/.../data/repository/planning/MealPlanRepository.kt:118-154`
(newline-per-entry, pipe-delimited serialization)

**Finding:** Entries are joined with `\n` and notes are stored as the last
pipe field. A multi-line note (easy to type in any text field) splits into
several lines on deserialize: the first parses as a truncated note, the
rest fail the `parts.size < 5` check and are dropped — along with nothing
else visibly wrong, so the user's plan quietly loses data. (Also: the
`component5` operator extension at the bottom of the file is dead code.)

**How to fix:** Sanitize on serialize — `slot.text.replace('\n', ' ')`
(and same for names) — or switch the store to Moshi like the comparison
repository, which removes the hand-rolled format entirely. Delete the
unused `component5` extension while there.

---

### F29 · HIGH · Bug — the gluten allergen rule doesn't match the word "gluten"

**Status: FIXED — commit `dec6ecf`.**

**Where:** `scan-eat-android/.../domain/engine/scoring/AllergenDetector.kt:59-60`

**Finding:** The gluten rule matches cereal names (blé, seigle, orge,
avoine, épeautre, kamut, triticale, farines, malt) but not the word
**"gluten"** itself — which appears verbatim on many French ingredient
lists ("gluten", "gluten de blé" is caught only via "blé", plain "gluten"
is not caught at all). Common gluten-carrying ingredients "couscous",
"boulgour" and "chapelure" are also absent. For a coeliac user who
declared the gluten allergen, the warning banner simply doesn't appear on
such products. This is the safety-critical direction of error (false
negative).

**How to fix:** Extend the pattern:

```kotlin
a("gluten|bl[eé]|froment|seigle|orge|avoine|[eé]peautre|kamut|triticale|couscous|boulgour|bulgur|chapelure|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge")
```

and add unit tests asserting `detectAllergens` fires for ingredient names
"gluten", "gluten de blé", "couscous". (The DietChecker GLUTEN_FREE rule
at `DietChecker.kt:153-155` has the identical gap — fix both.)

---

### F30 · MEDIUM · Bug — "pâté" is spelled unaccented in the vegetarian/vegan bans, and "noix" over-matches

**Status: FIXED — commit `dec6ecf`.**

**Where:** `scan-eat-android/.../domain/engine/scoring/DietChecker.kt:77,86`
(`pat[eé]` in VEGETARIAN and VEGAN forbidden lists),
`AllergenDetector.kt:68-69` (nuts rule)

**Finding:** Two pattern-quality issues in the same rule family:

1. The meat ban lists `pat[eé]`, which matches "pate"/"paté" but **not
   "pâté"** — the spelling actually printed on French labels (the `â` is
   not matched by `a`). A vegetarian/vegan user scanning a product
   containing "pâté de foie" gets no diet violation from this term (only
   from other matched words, when present). False negative in the
   direction users rely on.
2. The tree-nut allergen rule matches bare `noix`, which false-positives
   on "noix de coco" (coconut — explicitly *excluded* from EU Annex II
   tree nuts) and "noix de muscade" (nutmeg, a seed). Over-warning is the
   safe direction but erodes trust in the allergen banner.

**How to fix:** For 1, use `p[aâ]t[eé](?![a-zà-ÿ0-9])` — the accented
`é` is already required by the character class, so plain "pâte" (dough)
and "pâtes" (pasta) stay unmatched; add a test for "pâté de campagne".
For 2, add a negative lookahead: `noix(?!\s+de\s+(coco|muscade))`, with
tests for "noix de coco" (no hit) and "noix de cajou" (hit).

---

## Coverage note

Areas read end-to-end this audit: all of `scan-eat-server/` (routes,
services, shared engine copies, build/deploy files, tests); on the app
side, the scan pipeline (ScanRepository, OcrParser, ScanViewModel,
ResultViewModel), scoring engines (pillars, AdditivesDb, AllergenDetector,
DietChecker), dashboard math (DashboardAggregator, WeightRepository),
DataStore repositories (UserPreferences, Hydration, Fasting, Comparison,
MealPlan, Reminders), backup, Health Connect, notifications
(ReminderWorker, NotificationHelper), navigation shell (AppNavGraph,
MainShell), and the Room database/migrations. Not re-audited here (covered
by the prior A-O × 4-level audit passes and the design audit this
session): the Biolism engine family (has dedicated unit tests), individual
Compose screens/cards, and `design_audit.md`'s visual/UX scope.

---
