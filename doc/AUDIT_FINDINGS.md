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

---

## Part 2 — Open findings (documented, not fixed)

### F1 · HIGH · Structure — the shared-code copy has no drift guard

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

**Where:** `scanneat-complete.zip` (git-tracked)

**Finding:** A stale full-project archive lives in version control. It
bloats every clone, will only get staler, and duplicates what git itself
is for.

**How to fix:** `git rm scanneat-complete.zip`, add `*.zip` to
`.gitignore`. (Not done here per the findings-only instruction and
because deleting user files warrants an explicit OK.)

---

### F11 · INFO · Incoherence — `/health` hardcodes its own version string

**Where:** `scan-eat-server/.../Application.kt:75` vs
`build.gradle.kts` (`version = "0.1.0"`)

**Finding:** The health endpoint's `"version" to "0.1.0"` is a literal,
independent of the Gradle project version; they will diverge on the first
version bump.

**How to fix:** Generate it at build time — e.g. the Gradle
`processResources` task expands a `version.properties` placeholder from
`project.version`, and `Application.kt` reads it once at startup.
