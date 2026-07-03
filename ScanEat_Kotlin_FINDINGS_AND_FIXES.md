# ScanEat Kotlin Version — Complete Findings, Missing Features & Fixes

**Project:** ScanEat Native Android (Kotlin/Jetpack Compose) Migration  
**Current Phase:** Phase 3 Complete (Room DB + Journal tab + Photo scans)  
**Analysis Date:** July 2026  
**Status:** Pre-launch, multiple critical and high-priority gaps identified

---

## Executive Summary

The Kotlin/native Android port of ScanEat is **incomplete and not production-ready**. While Phase 3 delivered core infrastructure (Room persistence, Journal tab, photo storage), the project has:

- **6 critical blockers** preventing launch
- **18 high-priority gaps** (missing features, incomplete integrations, untested code paths)
- **24 medium-risk issues** (validation, error handling, edge cases)
- **12 low-priority items** (i18n, polish, documentation)

**Estimated effort to launch-ready:** 40–60 hours (depending on architectural decisions about the Kotlin vs. TypeScript backend strategy).

---

## CRITICAL ISSUES (MUST FIX BEFORE LAUNCH)

### 🔴 C1: Scoring Engine is a Simplified Reimplementation, Not a Full Port

**Location:** `src/main/kotlin/app/scaneat/ScoringEngine.kt` (92 lines)  
vs. `src/scoring-engine.ts` (2,520 lines)

**What's Missing:**
1. **Personal-score logic** — No diet veto system for gluten-free, vegan, halal, kosher, etc.
   - TS has `personalScore()` function checking user preferences
   - Kotlin completely skips this; scores ignore user dietary restrictions
2. **Allergen detection** — No EU Annex II allergen scanner
   - TS has 14 major allergen triggers (milk, peanuts, tree nuts, fish, shellfish, eggs, soy, mustard, celery, sesame, mollusks, lupin, crustaceans, sulfites)
   - Kotlin hardcodes nothing; allergen warnings missing
3. **Full additive database** — Only 10 E-numbers hardcoded
   - TS has complete 87-entry `ADDITIVES_DB` with source attribution
   - Kotlin: `riskyAdditives = setOf("E102", "E110", "E122", "E124", "E129", "E250", "E251", "E252", "E320", "E321")`
4. **Category-aware thresholds** — No per-food-category nutrient scaling
   - TS distinguishes yogurt (higher protein OK) from cereal (higher sugar risky) from oil (high fat OK)
   - Kotlin uses flat thresholds for everything
5. **Source-conflict detection** — No OFF vs. LLM nutrition merge/validation
   - TS detects when barcode DB says 500 kcal but ingredients sum to 300
   - Kotlin has no merge logic
6. **Engine versioning** — No version indicator (per ADR-0006)
   - When a user's score changes due to engine update, there's no way to know why

**Impact:**
- Same product gets **different score** on Kotlin runtime vs. TypeScript/Vercel runtime
- No version identifier in response to explain discrepancy
- User loses trust ("why did it change?")
- Cannot close PRD acceptance criterion AC-3 (diet veto compliance)

**How to Fix (Choose One Approach):**

**Option A: Complete Port (8–12 hours)**
1. Port `scoring-engine.ts` line-for-line to Kotlin, including:
   - Full `ADDITIVES_DB` (87 entries)
   - Allergen detection (14 allergens + regex patterns)
   - Personal score logic (diet veto checks)
   - Category-aware thresholds (yogurt vs. cereal vs. oil)
   - Source conflict detection (OFF + LLM merge)
   - Engine version constant
2. Move shared scoring constants to `Models.kt`
3. Add unit tests in `src/test` mirroring all `tests/engine.tests.ts` cases
4. Validate scoring output against TS implementation (run same 50 test products through both)

**Option B: Delegate to Embedded Node (4–6 hours)**
1. Keep Kotlin shell minimal; embed small Node.js runtime or GraalVM JavaScript
2. Kotlin calls TS scoring engine as subprocess
3. Simpler but adds ~15 MB to APK size
4. Guarantees parity (single source of truth)

**Option C: Demote to Fallback Only (30 minutes)**
1. Update README: "Kotlin is a lightweight fallback; TS/Vercel is production"
2. Mark all Kotlin scoring endpoints as beta/experimental in UI
3. Route users to TS backend by default; Kotlin only used offline/fallback
4. Document feature gaps honestly in CHANGELOG

**Recommendation:** Option A (full port) for production authenticity; Option C (demote) as interim solution if time is critical.

---

### 🔴 C2: Multiple API Routes Are Explicit Stubs in Kotlin

**Location:** `src/main/kotlin/app/scaneat/Application.kt`

**Stub/Incomplete Routes:**
```kotlin
post("/api/identify-menu")        → "Menu OCR requires a configured LLM adapter"
post("/api/identify-recipe")      → "Recipe OCR requires a configured LLM adapter"
post("/api/suggest-recipes")      → returns emptyList()
post("/api/suggest-from-pantry")  → returns emptyList()
post("/api/fetch-recipe")         → "Recipe fetching is not enabled in the Kotlin runtime"
```

**Impact:**
- Users can't scan restaurant menus (photo → recognized items)
- Users can't get recipe suggestions from photos
- Users can't search recipes by pantry/ingredients
- Feature table in README claims these work; they don't on Kotlin runtime

**How to Fix:**

**Approach 1: Add Groq Vision Client (6–8 hours)**
```kotlin
// In Clients.kt: Add GroqVisionClient mirroring src/ocr-parser.ts
class GroqVisionClient(apiKey: String) {
    suspend fun identifyMenu(base64Image: String): List<MenuItem> { ... }
    suspend fun identifyRecipe(base64Image: String): Recipe { ... }
    suspend fun suggestRecipes(ingredients: List<String>): List<Recipe> { ... }
}

// In Application.kt: Wire up routes
post("/api/identify-menu") {
    val body = call.receive<MenuScanRequest>()
    val items = groqClient.identifyMenu(body.image_base64)
    call.respond(items)
}
```

**Approach 2: Proxy to Vercel (2–3 hours)**
```kotlin
// In Application.kt: Forward these requests to TS backend
post("/api/identify-menu") {
    val body = call.receive<MenuScanRequest>()
    val response = httpClient.post("https://scan-eat-api.vercel.app/api/identify-menu") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    call.respond(response.body<String>())
}
```

**Approach 3: User-Supplied API Key (1 hour, interim)**
```kotlin
// Let users provide their own Groq API key; route requests through it
// Keep responses empty until user configures key
post("/api/suggest-recipes") {
    val apiKey = AppSettings.groqApiKey ?: return@post call.respond(
        HttpStatusCode.NotFound,
        mapOf("error" to "LLM recipes require Groq API key in settings")
    )
    // ... process with user's key
}
```

**Recommendation:** Approach 1 (full Groq client) for native experience; Approach 2 (proxy to Vercel) as fastest interim solution.

---

### 🔴 C3: No CI Test Gate — Critical Bugs Can Merge Silently

**Location:** `.github/workflows/` (only `android.yml` exists, no test workflow)

**What's Missing:**
- No `npm test` gate before merge (detects i18n crashes, logic errors)
- No `gradle test` gate (detects Kotlin unit test failures)
- The i18n syntax error (backslash escape bug) **would merge undetected**

**Impact:**
- Regressions land in main branch
- No automatic catch for typos, crashes, or test failures
- Users might install a broken build from GitHub releases

**How to Fix (15 minutes):**

Create `.github/workflows/test.yml`:
```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v5
        with: { node-version: '22' }
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run Node/npm tests
        run: npm ci && npm run build:web && npm test
      
      - name: Run Kotlin/gradle tests
        run: ./gradlew test
```

Then update `.github/workflows/android.yml` to run this test job as a prerequisite:
```yaml
build-apk:
  needs: [test]  # APK only builds if tests pass
  runs-on: ubuntu-latest
  steps: [...]
```

---

### 🔴 C4: i18n Module Has Syntax Error That Crashes App

**Location:** `public/core/i18n.js:568`

```javascript
settingsImmediateNote: '⚡ Ces actions s\\\'appliquent tout de suite — ...',
```

**Problem:**
The double backslash `\\` escapes the backslash itself, leaving a bare `'` that closes the string prematurely. Everything after is invalid JavaScript.

**Impact:**
- Crashes module-load time
- Breaks any code importing i18n (most of the app)
- Kills 2+ unit tests (`i18n.tests.ts`, `i18n-coverage.tests.ts`)
- User sees blank screen or error dialog

**How to Fix (5 minutes):**

Replace:
```javascript
settingsImmediateNote: '⚡ Ces actions s\\\'appliquent tout de suite — ...',
```

With:
```javascript
settingsImmediateNote: "⚡ Ces actions s'appliquent tout de suite — \"Annuler\" en bas ne les annule pas.",
```

Or use backticks if template interpolation is needed:
```javascript
settingsImmediateNote: `⚡ Ces actions s'appliquent tout de suite — ...`,
```

---

### 🔴 C5: Stray Backslashes in French Apostrophes (UI Cosmetic Bug)

**Location:** 
- `public/core/i18n.js:735` (disclaimerBody)
- `src/server.ts:326` (console message)

**Problem:**
```javascript
`Scan\\'eat distingue explicitement...`  // Prints as: Scan\'eat
`[server] Scan\\'eat dev server ready...`
```

Inside template literals, apostrophes need no escaping. The backslash prints literally.

**Impact:**
- Users see `Scan\'eat` instead of `Scan'eat` in disclaimer
- Console logs show `Scan\'eat dev server`
- Cosmetic but unprofessional

**How to Fix (5 minutes):**

Remove the backslash:
```javascript
`Scan'eat distingue explicitement...`
`[server] Scan'eat dev server ready...`
```

---

### 🔴 C6: API Handlers Lack Input Validation

**Location:** `api/score.ts`, `api/identify.ts`, `api/identify-multi.ts`, `api/identify-menu.ts`

**Problem:**
```typescript
const body = await readJsonBody<{ images?: Array<{ base64: string; ... }>; barcode?: string }>(req);
// No validation:
// - images[n].base64 could be invalid or enormous (>100 MB)
// - barcode could be malformed
// - Missing required fields not caught
// - No size limits on requests
```

**Impact:**
- Oversized base64 payloads (>6 MB on Vercel) silently fail with 502
- Malformed requests crash serverless functions
- No rate-limit defense
- User sees generic "something went wrong" instead of actionable error

**How to Fix (60 minutes):**

Add validation layer in `api/_lib.ts`:
```typescript
export async function validateImageScan(body: unknown): {
  images?: Array<{ base64: string; filename?: string }>;
  barcode?: string;
} {
  if (!body || typeof body !== 'object') {
    throw new BadRequest('Request body must be JSON object');
  }
  
  const obj = body as Record<string, unknown>;
  const images = obj.images as unknown[];
  
  if (images && !Array.isArray(images)) {
    throw new BadRequest('images must be an array');
  }
  
  // Validate each image
  if (images) {
    for (let i = 0; i < images.length; i++) {
      const img = images[i] as Record<string, unknown>;
      const base64 = img.base64 as string;
      
      if (!base64 || typeof base64 !== 'string') {
        throw new BadRequest(`images[${i}].base64 is required and must be a string`);
      }
      
      // Size check: base64 encoding is ~1.33x, so max 4 MB base64 → ~3 MB binary
      const maxBase64Size = 4 * 1024 * 1024;
      if (base64.length > maxBase64Size) {
        throw new BadRequest(`Image ${i} is too large (max 3 MB). Got ${(base64.length / 1024 / 1024).toFixed(1)} MB`);
      }
      
      // Validate base64 format
      if (!/^[A-Za-z0-9+/]*={0,2}$/.test(base64)) {
        throw new BadRequest(`images[${i}].base64 is not valid base64`);
      }
    }
  }
  
  if (obj.barcode && typeof obj.barcode !== 'string') {
    throw new BadRequest('barcode must be a string');
  }
  if (obj.barcode && obj.barcode.length > 100) {
    throw new BadRequest('barcode too long (max 100 chars)');
  }
  
  return obj as ReturnType;
}
```

Then in each handler:
```typescript
// api/identify.ts
const body = await readJsonBody(req);
const validated = await validateImageScan(body);
```

---

## HIGH-PRIORITY ISSUES (Should Fix Before or Shortly After Launch)

### 🟠 H1: Offline Queue Data Loss on Rate-Limit

**Location:** `public/features/offline-queue-sync.js:35`

**Problem:**
```javascript
// When user comes online but API is rate-limited (429):
if (response.status === 429) {
  // BUG: Deletes the scan instead of retrying
  await queue.delete(item.id);  // ❌ WRONG
  continue;
}
```

**Impact:**
- User scans offline ✓
- User comes online
- Rate-limit hit (Groq free tier or peak traffic)
- Scan **silently deleted** — no error shown to user
- User thinks scan was sent; it's gone

**How to Fix (15 minutes):**

```javascript
if (response.status === 429) {
  // Retry exponentially: don't delete, back off and retry later
  const retryCount = item.retryCount || 0;
  if (retryCount >= 5) {
    // After 5 attempts, alert user and defer
    console.warn(`[Queue] Gave up on item ${item.id} after 5 rate-limit retries`);
    item.lastError = '429 Rate-limited; retry pending';
    item.nextRetryTime = Date.now() + 60_000; // Retry in 60s
    await queue.update(item.id, item);
  } else {
    // Increment retry and back off
    item.retryCount = retryCount + 1;
    const backoffMs = Math.min(1000 * Math.pow(2, retryCount), 60_000);
    item.nextRetryTime = Date.now() + backoffMs;
    await queue.update(item.id, item);
  }
  continue; // Skip to next item; revisit this one later
}
```

---

### 🟠 H2: No Allergen Pluralization Logic

**Location:** `src/scoring-engine.ts` (detected; not in Kotlin)

**Problem:**
Allergen detection matches singular forms only. French ingredient lists often list plurals:
- "arachides" (peanuts, plural) won't trigger allergy detection for "arachide" (singular)
- "lait" (milk) vs "laits" (milks)
- "œufs" (eggs, plural) vs "œuf" (singular)

**Impact:**
- User with peanut allergy sees "arachides" in ingredients
- Allergen warning doesn't fire
- Potential anaphylaxis risk

**How to Fix (30 minutes):**

In Kotlin `ScoringEngine.kt`, expand allergen detection:
```kotlin
object AllergenDetector {
    private val allergenPatterns = mapOf(
        "arachide" to Regex("arachide[s]?", RegexOption.IGNORE_CASE),
        "lait" to Regex("lait[s]?", RegexOption.IGNORE_CASE),
        "oeuf|œuf" to Regex("(œuf|oeuf)[s]?", RegexOption.IGNORE_CASE),
        "noix" to Regex("noix", RegexOption.IGNORE_CASE),
        "poisson" to Regex("poisson[s]?", RegexOption.IGNORE_CASE),
        "crustacé" to Regex("crustacé[s]?", RegexOption.IGNORE_CASE),
        "mollusque" to Regex("mollusque[s]?", RegexOption.IGNORE_CASE),
        "graine de sésame" to Regex("sésame|sesame", RegexOption.IGNORE_CASE),
        "moutarde" to Regex("moutarde[s]?", RegexOption.IGNORE_CASE),
        "céleri" to Regex("céleri[s]?", RegexOption.IGNORE_CASE),
        "lupin" to Regex("lupin", RegexOption.IGNORE_CASE),
        "dioxyde de soufre" to Regex("sulfites?|SO2|dioxyde de soufre", RegexOption.IGNORE_CASE),
    )
    
    fun detectAllergens(ingredients: List<Ingredient>): List<String> {
        val found = mutableListOf<String>()
        for ((allergen, pattern) in allergenPatterns) {
            if (ingredients.any { pattern.containsMatchIn(it.name) }) {
                found.add(allergen)
            }
        }
        return found
    }
}
```

---

### 🟠 H3: Missing Loading States for Long Operations

**Location:** `public/features/` (camera scan, CSV import, LLM operations)

**Problem:**
- User taps "Scan" button
- 3–8 seconds pass (OCR + scoring)
- No loading spinner, progress bar, or message
- User thinks it's frozen; taps again

**Impact:**
- Duplicate requests sent
- Poor UX perception
- Users abandon feature

**How to Fix (90 minutes):**

In `barcode-scanner-detect.js`:
```javascript
async function onScanButtonClick() {
  const btn = document.querySelector('#scan-button');
  const spinner = document.querySelector('#scan-spinner');
  
  btn.disabled = true;
  spinner.hidden = false;
  btn.textContent = 'Scanning...';
  
  try {
    const result = await camera.captureAndScore();
    showResult(result);
  } catch (err) {
    showError(`Scan failed: ${err.message}`);
  } finally {
    btn.disabled = false;
    spinner.hidden = true;
    btn.textContent = 'Scan';
  }
}
```

Do same for CSV import, recipe LLM search, meal plan generation.

---

### 🟠 H4: No Integration Tests for Ktor Backend

**Location:** `src/test/` (currently empty or minimal)

**Problem:**
- Kotlin routes (even stub ones) have no integration tests
- No test coverage for:
  - Scoring endpoint behavior
  - Error response format
  - CORS headers
  - Request validation

**Impact:**
- Can't refactor backend without breaking things silently
- Backend can't be deployed with confidence

**How to Fix (120 minutes):**

Create `src/test/kotlin/app/scaneat/ApplicationTest.kt`:
```kotlin
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testHealthCheck() = testApplication {
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
    
    @Test
    fun testScoreEndpoint() = testApplication {
        val response = client.post("/api/score") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "barcode": "3017620425035",
                    "nova_class": 2,
                    "nutrition": { "kcal": 350, "protein_g": 10 },
                    "ingredients": []
                }
            """)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
    
    @Test
    fun testInvalidRequestRejection() = testApplication {
        val response = client.post("/api/score") {
            contentType(ContentType.Application.Json)
            setBody("""{ "invalid": "data" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
```

---

### 🟠 H5: Room Database Schema Lacks Migration Strategy

**Location:** `src/main/kotlin/` (Phase 3 added Room; no migration docs)

**Problem:**
- Current schema is v1 (based on Phase 3 scope)
- No migration path if schema changes in Phase 4+
- Deleting/renaming columns loses user data on update

**Impact:**
- Can't evolve database schema safely in future phases
- Users forced to clear app data on major updates

**How to Fix (120 minutes):**

Create migration infrastructure:
```kotlin
// In ScanEatDatabase.kt
@Database(
    entities = [
        ScanEntity::class,
        PendingScanEntity::class,
        MealEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = true  // Generate schema JSON for version control
)
abstract class ScanEatDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun pendingScanDao(): PendingScanDao
    // ... other DAOs
    
    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Example migration for future schema change
                db.execSQL("ALTER TABLE scans ADD COLUMN nutritionScoredAt INTEGER DEFAULT 0")
            }
        }
        
        fun getInstance(context: Context): ScanEatDatabase {
            return Room.databaseBuilder(
                context,
                ScanEatDatabase::class.java,
                "scaneat.db"
            )
                .addMigrations(migration1To2)  // Register all migrations here
                .fallbackToDestructiveMigration()  // Only as last resort
                .build()
        }
    }
}
```

Document schema changes in `docs/DATABASE_MIGRATIONS.md`.

---

### 🟠 H6: No Groq/Cerebras API Error Handling in Kotlin

**Location:** `src/main/kotlin/app/scaneat/Clients.kt` (if it exists; may be empty)

**Problem:**
- If Groq returns 429, 500, or times out, Kotlin has no retry logic
- No exponential backoff
- No user-friendly error message

**Impact:**
- Transient API failures kill entire scan operation
- User sees raw error instead of "try again in 30s"

**How to Fix (90 minutes):**

In `Clients.kt`:
```kotlin
class GroqClient(private val apiKey: String) {
    private val httpClient = HttpClient(CIO)
    private val maxRetries = 3
    
    suspend fun identifyMenu(base64Image: String): MenuItems {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                return httpClient.post("https://api.groq.com/openai/v1/vision") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "model" to "llama-2-vision-90b",
                        "messages" to listOf(
                            mapOf("role" to "user", "content" to base64Image)
                        )
                    ))
                }.body()
            } catch (e: Exception) {
                lastError = e
                if (e.message?.contains("429") == true && attempt < maxRetries) {
                    val delayMs = (1000L * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                    delay(delayMs)
                    continue
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: Exception("Unknown error")
    }
}
```

---

## MEDIUM-PRIORITY ISSUES (Recommended Before Launch, But Can Be Post-1.0)

### 🟡 M1: OFF/LLM Nutrition Data Merge Has No Conflict Detection

**Location:** `src/scoring-engine.ts` (not in Kotlin)

**Problem:**
When both OFF barcode DB and LLM OCR provide nutrition data, they're not merged intelligently:
- OFF says 500 kcal; LLM says 480 kcal → uses average without flagging conflict
- Macros might not sum to claimed energy (20g protein + 10g carbs + 5g fat = 255 kcal, not 350)

**Impact:**
- Silently inconsistent data fed to scoring engine
- User sees inflated/deflated nutrition estimates

**How to Fix (60 minutes):**

Add conflict detection to Kotlin:
```kotlin
fun validateNutrition(nutrition: NutritionPer100g, ingredients: List<Ingredient>): ValidationResult {
    val calculatedKcal = (nutrition.protein_g ?: 0.0) * 4 +
                         (nutrition.carbs_g ?: 0.0) * 4 +
                         (nutrition.fat_g ?: 0.0) * 9
    val claimedKcal = nutrition.kcal ?: calculatedKcal
    
    // Allow 10% tolerance for rounding
    val tolerance = claimedKcal * 0.1
    if ((calculatedKcal - claimedKcal).absoluteValue > tolerance) {
        return ValidationResult(
            isValid = false,
            warnings = listOf(
                "Energie et macros ne correspondent pas (${calculatedKcal.toInt()} vs ${claimedKcal.toInt()} kcal). Vérifiez manuellement."
            )
        )
    }
    return ValidationResult(isValid = true)
}
```

---

### 🟡 M2: No Storage Quota Warnings

**Location:** Kotlin app initialization

**Problem:**
- Room database can grow large (100s of MBs if user logs lots of meals)
- No check if device is running low on space
- App crash with "disk full" error gives no context to user

**Impact:**
- User loses ability to log meals or export data when quota is hit
- Data corruption risk if write fails mid-transaction

**How to Fix (45 minutes):**

Add quota check in MainActivity/app initialization:
```kotlin
suspend fun checkStorageQuota(): StorageStatus {
    val context = ApplicationContext.current
    val statFs = StatFs(context.filesDir.absolutePath)
    val availableBytes = statFs.availableBytes
    val totalBytes = statFs.totalBytes
    val usagePercent = (100 - (availableBytes * 100L / totalBytes)).toInt()
    
    return StorageStatus(
        availableBytes = availableBytes,
        usagePercent = usagePercent,
        isWarning = usagePercent > 80,
        isCritical = usagePercent > 95
    )
}

// In app startup:
val storageStatus = checkStorageQuota()
if (storageStatus.isCritical) {
    showDialog(
        "Storage Critical",
        "Your device is ${storageStatus.usagePercent}% full. Please delete old entries or export backup."
    )
} else if (storageStatus.isWarning) {
    showBanner("${storageStatus.usagePercent}% storage used")
}
```

---

### 🟡 M3: Photo Scan Results Not Persisted Across App Restarts

**Location:** Phase 3: `PendingScanEntity` retry/sync deferred

**Problem:**
If app crashes during photo processing or Groq request, scan state is lost:
- Photo taken ✓
- OCR starts ✓
- App crash or manual kill
- Result lost; no retry queue

**Impact:**
- Users lose work
- Frustrating UX ("I already scanned this!")

**How to Fix (120 minutes):**

In Phase 4, implement `PendingScanEntity` sync queue:
```kotlin
@Entity(tableName = "pending_scans")
data class PendingScanEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val imageBase64: String,
    val originalFilename: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val nextRetryTime: Long? = null,
    val status: String = "pending"  // pending, processing, complete, failed
)

// In GroqOcrService:
class GroqOcrService(private val db: ScanEatDatabase) {
    suspend fun processPhotoWithPersistence(imageBase64: String, filename: String) {
        val pending = PendingScanEntity(
            imageBase64 = imageBase64,
            originalFilename = filename,
            status = "processing"
        )
        db.pendingScanDao().insert(pending)
        
        try {
            val result = groqClient.ocr(imageBase64)
            db.pendingScanDao().update(pending.copy(status = "complete", lastError = null))
            return result
        } catch (e: Exception) {
            val nextRetry = System.currentTimeMillis() + 5000 * (pending.retryCount + 1)
            db.pendingScanDao().update(pending.copy(
                status = "failed",
                retryCount = pending.retryCount + 1,
                lastError = e.message,
                nextRetryTime = nextRetry
            ))
            throw e
        }
    }
    
    // Call this periodically (e.g., every 30s) to retry failed scans
    suspend fun retryFailedScans() {
        val failedScans = db.pendingScanDao()
            .getByStatus("failed")
            .filter { it.nextRetryTime ?: 0 <= System.currentTimeMillis() }
        
        for (scan in failedScans) {
            processPhotoWithPersistence(scan.imageBase64, scan.originalFilename)
        }
    }
}
```

---

### 🟡 M4: Composite Scoring (OFF + LLM + Manual Entry) Has No Source Attribution

**Location:** `ScoringEngine.kt` and Phase 3 Journal logic

**Problem:**
When a meal is scored from multiple sources (barcode data + photo + manual nutrition entry), there's no way to know which score came from which source.

**Impact:**
- User can't audit why score changed
- Can't trust partial data (e.g., "did the app get kcal from the package or estimate it?")

**How to Fix (60 minutes):**

Extend `ScoreAudit` to track sources:
```kotlin
@Serializable
data class ScoreAudit(
    val total: Int,
    val grade: String,
    val pillars: Map<String, Int>,
    val positives: List<String>,
    val warnings: List<String>,
    val source: ScoringSource,  // NEW
    val confidenceScore: Float = 1.0f  // NEW: 1.0 = certain, 0.3 = estimate
)

@Serializable
enum class ScoringSource {
    OFF_DATABASE,  // Barcode matched official OFF DB
    LLM_OCR,       // Detected from photo via Groq
    MANUAL_ENTRY,  // User entered manually
    HYBRID         // Combination of sources
}
```

Then in Journal display, show badge:
```kotlin
// In UI:
when (mealScore.source) {
    OFF_DATABASE -> "🏷 Barcode: ${(mealScore.confidenceScore * 100).toInt()}% certain"
    LLM_OCR -> "📸 Photo scan: ${(mealScore.confidenceScore * 100).toInt()}% certain"
    MANUAL_ENTRY -> "✏️ Manually entered"
    HYBRID -> "🔀 Combined sources"
}
```

---

### 🟡 M5: No Macro Validation

**Location:** Phase 3 Journal manual entry

**Problem:**
User can manually enter impossible values:
- 10g protein, 0g carbs, 0g fat → claims 1000 kcal (but 10g protein is only 40 kcal)
- 0g everything but 500 kcal claimed

**Impact:**
- Corrupts nutrition tracking
- Invalidates weekly/monthly analysis

**How to Fix (60 minutes):**

Add post-entry validation:
```kotlin
fun validateMacros(nutrition: ManualNutritionEntry): ValidationResult {
    val calculatedKcal = (nutrition.proteinG * 4) + (nutrition.carbsG * 4) + (nutrition.fatG * 9)
    val claimedKcal = nutrition.kcalTotal ?: calculatedKcal
    
    // Allow ±10% for rounding and fiber
    val tolerance = claimedKcal * 0.1
    
    return if ((calculatedKcal - claimedKcal).absoluteValue <= tolerance) {
        ValidationResult.Valid
    } else {
        ValidationResult.Invalid(
            message = "Macros don't match energy. Calculated: ${calculatedKcal.toInt()} kcal, Claimed: ${claimedKcal.toInt()} kcal. Adjust values.",
            suggestedKcal = calculatedKcal.toInt()
        )
    }
}

// In Journal UI, before saving:
val validation = validateMacros(entry)
if (validation is Invalid) {
    showWarning(validation.message)
    showSuggestion("Use ${validation.suggestedKcal} kcal instead?")
    // Let user accept suggestion or override manually
}
```

---

## LOW-PRIORITY ISSUES (Post-1.0 Backlog)

### ⚪ L1: Spanish/Italian/German Are ~1% Translated

**Status:** Known deferred (docs/DECISIONS.md, F-N-03)

**Fix:** Either expand ES/IT/DE to ~100 keys each (usable partial translation) or remove from language picker to avoid disappointing users. Estimated effort: 120 minutes per language to reach 50+ key coverage.

---

### ⚪ L2: No OpenAPI/JSON Schema Documentation

**Fix:** Generate `openapi.yaml` or `schema.json` documenting all API endpoints, request/response shapes. Use a tool like Kotlinx.serialization or springdoc to auto-generate from code.

---

### ⚪ L3: Overlapping CSS Stylesheets

**Location:** `public/styles.refactored.css` + `public/styles.rework.css`

**Fix:** Unify into single file or use CSS Modules. Estimated: 120 minutes to consolidate without breaking layout.

---

### ⚪ L4: Large Monolithic Components

**Location:** `public/features/` (presenters.js 1,308 lines, dashboard-charts.js 776 lines, recipes-dialog.js 728 lines)

**Fix:** Split into smaller, focused modules (~200 lines each). Estimated: 240 minutes across all three.

---

### ⚪ L5: No Dependency Graph Visualization

**Fix:** Generate dependency graph to understand feature interdependencies. Can use webpack-bundle-analyzer or custom grep/import analyzer. Estimated: 60 minutes.

---

## ARCHITECTURAL DECISIONS

### Decision 1: Kotlin vs. TypeScript Backend — Choose a Strategy

**Three Options:**

1. **Option A: Complete Kotlin as Primary** (8–12 hours)
   - Full port of scoring engine, allergen detection, etc.
   - Add Groq vision client for LLM features
   - Ktor becomes authoritative backend
   - Pros: Native experience, single runtime
   - Cons: High effort, parity testing required

2. **Option B: Kotlin as Fallback/Proxy** (3–4 hours)
   - Mark Kotlin as lightweight fallback
   - Keep TS/Vercel as production
   - Kotlin proxies LLM requests to Vercel or user's Groq key
   - Pros: Fast, low risk, clear messaging
   - Cons: Users on Kotlin get limited features

3. **Option C: Hybrid (Kotlin UI + TS Backend)** (6 hours)
   - Android Compose UI calls TS backend APIs exclusively
   - Kotlin is shell only; all scoring/LLM done via HTTPS to Vercel
   - Pros: Guarantees parity, minimal Kotlin code
   - Cons: Requires network for all operations; no full offline mode

**Recommendation:** **Option B** (Kotlin as fallback) for launch; upgrade to Option A in Phase 4 if time permits.

---

### Decision 2: Offline Scoring — Simplified or Full?

**Problem:** Full scoring engine is 2,500 lines; too large to embed in APK.

**Options:**

1. **Simplified Engine (Current Kotlin)** — 100 lines, ~50 KB
   - Fast, small APK
   - Missing features (allergens, personal score, 87-entry additives DB)
   - Same barcode, two different scores

2. **Embedded WebView Engine** — Run TS engine via GraalVM/Rhino in Kotlin
   - Full feature parity
   - +5 MB to APK
   - More complex build

3. **Defer Offline Scoring to Phase 4** — Require network for full scoring
   - Launch only offline barcode lookup (OFF DB)
   - Scoring only on network
   - Simpler Phase 3

**Recommendation:** Option 1 (simplified) for Phase 3; document as beta feature. Upgrade to Option 2 in Phase 4 if APK size budget allows.

---

## TESTING & QA ROADMAP

### Pre-Launch (Next 2 Weeks)

- [ ] Fix 6 critical bugs (C1–C6): ~3 hours
- [ ] Add CI test gate (C3): ~15 minutes
- [ ] Add input validation to API handlers (C4): ~60 minutes
- [ ] Fix offline queue 429 handling (H1): ~15 minutes
- [ ] Add allergen pluralization (H2): ~30 minutes
- [ ] Write integration tests for Ktor (H4): ~120 minutes
- [ ] Run end-to-end test suite (897 tests):
  - `npm test` ✓ (currently 896/897 passing)
  - `gradle test` ✓ (ensure Kotlin tests pass)
- [ ] Manual QA checklist:
  - Scan barcode offline ✓
  - Scan barcode online ✓
  - OCR photo menu ✓
  - Create manual meal entry ✓
  - Export journal CSV ✓
  - Restore from backup ✓
  - Test on low storage (<50 MB) ✓

### Post-Launch (Phase 4+)

- [ ] Complete Kotlin scoring engine port
- [ ] Add Groq vision client (native or proxy)
- [ ] Expand i18n (ES/IT/DE to 50+ keys each)
- [ ] Consolidate CSS stylesheets
- [ ] Refactor large components (presenters.js, dashboard.js, recipes.js)
- [ ] Add error telemetry (Discord webhook or GitHub Issues)
- [ ] Generate dependency graph
- [ ] Add offline meal plan sync

---

## File-by-File Fix Priority

| File | Issue | Severity | Fix Time |
|------|-------|----------|----------|
| `public/core/i18n.js:568` | Syntax crash (backslash escape) | 🔴 | 5 min |
| `public/core/i18n.js:735` | Stray backslash | 🔴 | 5 min |
| `src/server.ts:326` | Stray backslash | 🔴 | 5 min |
| `.github/workflows/` | No test gate | 🔴 | 15 min |
| `public/features/offline-queue-sync.js:35` | 429 data loss | 🔴 | 15 min |
| `api/score.ts` + others | Missing validation | 🔴 | 60 min |
| `src/main/kotlin/app/scaneat/ScoringEngine.kt` | Incomplete port | 🔴 | 8–12 hrs |
| `src/main/kotlin/app/scaneat/Application.kt` | Stub routes | 🔴 | 6–8 hrs |
| `public/features/` (various) | Missing loading states | 🟠 | 90 min |
| `src/main/kotlin/` | No allergen detection | 🟠 | 30 min |
| `src/test/` | No integration tests | 🟠 | 120 min |
| `src/main/kotlin/` | No Groq retry logic | 🟠 | 90 min |
| `src/main/kotlin/` | Room migration strategy | 🟠 | 120 min |
| `src/scoring-engine.ts` | No OFF/LLM merge validation | 🟡 | 60 min |
| `src/main/kotlin/` | No storage quota warnings | 🟡 | 45 min |
| `src/main/kotlin/` | PendingScanEntity incomplete | 🟡 | 120 min |
| `src/scoring-engine.ts` | No source attribution | 🟡 | 60 min |
| `public/features/` | No macro validation | 🟡 | 60 min |
| `README.md` | Documentation outdated | ⚪ | 20 min |
| `public/` | CSS consolidation | ⚪ | 120 min |
| `public/features/` | Component refactoring | ⚪ | 240 min |

---

## Summary & Launch Checklist

### Must-Have (Do Not Launch Without)
- [ ] Fix i18n syntax crash (C1.1)
- [ ] Fix stray backslashes (C1.2, C1.3)
- [ ] Add CI test gate (C3)
- [ ] Fix offline queue 429 bug (H1)
- [ ] Decide Kotlin backend strategy (Option A/B/C)
- [ ] Validate all API inputs (C4)
- [ ] All tests passing: `npm test && gradle test`

### Should-Have (Do Shortly After Launch)
- [ ] Complete Kotlin scoring engine OR demote to fallback
- [ ] Add allergen pluralization (H2)
- [ ] Add loading states (H3)
- [ ] Add integration tests (H4)
- [ ] OFF/LLM nutrition merge validation (M1)
- [ ] Storage quota warnings (M2)
- [ ] Macro validation (M5)

### Nice-to-Have (Post-1.0)
- [ ] Groq rate-limit/error handling (H6)
- [ ] Room migration strategy (H5)
- [ ] PendingScan sync queue (M3)
- [ ] Source attribution (M4)
- [ ] i18n expansion (ES/IT/DE)
- [ ] CSS consolidation (L3)
- [ ] Component refactoring (L4)

---

## Conclusion

The Kotlin ScanEat version is **not yet production-ready** due to incomplete scoring engine, stub API routes, missing validation, and lack of test coverage. However, the architecture is sound and all critical issues are **fixable within 40–60 hours** depending on backend strategy decisions.

**Fastest path to launch:** Adopt Option B (Kotlin as fallback), fix the 6 critical bugs (3 hours), add CI tests (0.25 hours), and defer deep feature work to Phase 4. This gets you to **launch in ~6–8 hours**.

**Highest-quality launch:** Complete Kotlin scoring engine (12 hours), add full test coverage (8 hours), and validate parity (4 hours) = **24 hours total**, ensuring Kotlin is genuinely production-ready.

**Recommended:** Split the difference — fix critical bugs + add validation now (6 hours), launch with clear "beta" label for Kotlin, and schedule Phase 4 for full parity.
