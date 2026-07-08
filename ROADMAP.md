# Scan'eat — Restructuring & Release Readiness Roadmap

**App:** `fr.scanneat.app` · v0.1.0  
**Codebase:** 84 Kotlin files · 15 172 lines  
**Date:** July 2026

This document covers two things in execution order:

1. **Code restructuring** — pure file moves, package renames, import updates. Zero logic changes.
2. **Release readiness** — everything outside the code: platform, UX, accessibility, i18n, performance, store submission.

Each item includes what is wrong today, what the fix is, and why it matters.

---

## Part 1 — Code Restructuring

### Why restructure?

The project grew across many incremental sessions. The architecture is sound (Hilt, Room, MVVM, Compose Navigation), but the *physical organisation* of files accumulated debt:

- `data/repository/` holds 11 unrelated repositories in a flat list
- `domain/engine/` holds 9 engines with no sub-grouping
- `Daos.kt` and `Entities.kt` are monolithic 130-line files covering 7 unrelated entities each
- `MainShell.kt` does three jobs: navigation model, shell UI, and route registration
- `AppModule.kt` has three `@Module` objects in one file
- `Theme.kt` contains colour constants, three colour schemes, the composable, and an imperative token object

None of this prevents the app from working. It prevents the project from scaling cleanly when new features ship.

---

### Step 1 — Split `Theme.kt` and remove `BiolismTokens`

**What is wrong today:**

`Theme.kt` (155 lines) contains four distinct concerns:
- ~50 raw `val Gold = Color(...)` token declarations
- Three `ColorScheme` instances (OLED / dark / light)
- `ScanEatTheme()` composable
- `BiolismTokens` imperative object (`fun background(theme: String)`, `fun gold(theme: String)`, etc.)

`BiolismTokens` was introduced as a workaround when Biolism screens were wrapped in their own `ScanEatTheme {}` and the user's theme preference wasn't flowing through. That problem is now fixed — `ScanEatTheme` is called exactly once at the root in `MainActivity`. Every composable in the tree already has the correct `MaterialTheme` in scope. `BiolismTokens` is now dead weight that requires passing a raw `String` around instead of using `MaterialTheme.colorScheme`.

**The fix:**

```
presentation/ui/theme/
  Colors.kt     ← all raw Color val tokens only (~60 lines)
  Type.kt       ← ScanEatTypography — already correct, no change
  Theme.kt      ← three ColorSchemes + ScanEatTheme() only (~80 lines, no BiolismTokens)
```

Replace every `BiolismTokens.background(theme)` call with `MaterialTheme.colorScheme.background`.  
Replace every `BiolismTokens.gold(theme)` call with `Gold` (identical value in all themes).  
Remove `theme: String` parameter from `BiolismScreen`, `TrackerScreen`, `DataScreen`, `BiolismProfileScreen`.  
Remove `theme: String` parameter from `MainShell`.  
Remove `theme` argument from `MainActivity`'s `MainShell(theme = theme)` call.

**Why it matters:** Every future screen is written with `MaterialTheme.colorScheme.*` as intended by Material 3. No String-based theme plumbing. Adding a fourth theme (e.g. `"amoled_green"`) only requires a new `ColorScheme` in `Theme.kt` — nothing else changes.

---

### Step 2 — Split `Daos.kt` and `Entities.kt` into per-entity files

**What is wrong today:**

`Daos.kt` (130 lines) contains 7 `@Dao` interfaces for completely unrelated tables.  
`Entities.kt` (130 lines) contains 7 `@Entity` data classes for those same tables.

Adding a new entity (e.g. sleep tracking) requires editing both files and increases merge conflict risk.

**The fix:**

```
data/local/db/
  AppDatabase.kt        ← unchanged; references all entities and DAOs
  Converters.kt         ← unchanged
  scan/
    ScanHistoryEntity.kt
    ScanHistoryDao.kt
  consumption/
    ConsumptionEntity.kt
    ConsumptionDao.kt
  weight/
    WeightEntity.kt
    WeightDao.kt
  activity/
    ActivityEntity.kt
    ActivityDao.kt
  customfood/
    CustomFoodEntity.kt
    CustomFoodDao.kt
  template/
    MealTemplateEntity.kt
    MealTemplateDao.kt
  recipe/
    RecipeEntity.kt
    RecipeDao.kt
```

**Why it matters:** A developer adding a new feature only creates two new files in a new subdirectory, then adds two lines to `AppDatabase.kt`. No shared file is modified. Every entity and its DAO live side-by-side — the schema and query logic for a feature are always in one place.

---

### Step 3 — Regroup `domain/engine/` by concern

**What is wrong today:**

`domain/engine/` contains 9 files:

| File | Responsibility |
|---|---|
| `ScoringEngine.kt` | Core nutrition scoring |
| `PersonalScoreEngine.kt` | User-profile-adjusted scoring |
| `AdditivesDb.kt` | E-number lookup table |
| `AllergenDetector.kt` | Allergen matching |
| `DietChecker.kt` | Diet compatibility |
| `FoodDb.kt` | Built-in food database |
| `OcrParser.kt` | LLM label parsing |
| `OffMapper.kt` | OpenFoodFacts DTO → domain |
| `DashboardAggregator.kt` | Dashboard rollups and forecasts |
| `GroceryList.kt` | Grocery list aggregation |
| `PairingsDb.kt` | Food pairing lookup table |

These are four completely different domains mixed into one flat directory.

**The fix:**

```
domain/engine/
  scoring/
    ScoringEngine.kt
    PersonalScoreEngine.kt
    AdditivesDb.kt
    AllergenDetector.kt
    DietChecker.kt
  nutrition/
    FoodDb.kt
    OcrParser.kt
    OffMapper.kt
  dashboard/
    DashboardAggregator.kt
  planning/
    GroceryList.kt
    PairingsDb.kt
  biolism/              ← already correct, no change
    BiolismEngine.kt
    BiolismModels.kt
    BiolismConstants.kt
```

**Why it matters:** A developer working on the scanner touches only `domain/engine/scoring/` and `domain/engine/nutrition/`. A developer working on the meal planner touches only `domain/engine/planning/`. The `biolism/` subdirectory already demonstrates this pattern working correctly.

---

### Step 4 — Regroup `data/repository/` by feature domain

**What is wrong today:**

`data/repository/` contains 11 repositories in a flat list with no indication of which feature they belong to:

```
ActivityRepository.kt
ComparisonRepository.kt
ConsumptionRepository.kt
CustomFoodRepository.kt
DayNotesRepository.kt
FastingRepository.kt
HydrationRepository.kt
MealPlanRepository.kt
MealTemplateRepository.kt
RecipeRepository.kt
ScanRepository.kt
WeightRepository.kt
biolism/BiolismRepository.kt
```

**The fix:**

```
data/repository/
  scan/
    ScanRepository.kt
    ComparisonRepository.kt
  nutrition/
    ConsumptionRepository.kt
    DayNotesRepository.kt
    CustomFoodRepository.kt
  health/
    WeightRepository.kt
    ActivityRepository.kt
    HydrationRepository.kt
    FastingRepository.kt
  planning/
    RecipeRepository.kt
    MealTemplateRepository.kt
    MealPlanRepository.kt
  biolism/              ← already correct, no change
    BiolismRepository.kt
```

**Why it matters:** "What repositories does the scanner feature use?" → look in `scan/`. "What does health tracking touch?" → look in `health/`. New features add a new subdirectory rather than appending to a flat 15-item list.

---

### Step 5 — Split `MainShell.kt` into four focused files

**What is wrong today:**

`MainShell.kt` (~200 lines) does three things:
1. Defines `TopTab` sealed class (navigation model)
2. Renders the `NavigationBar` scaffold (shell UI)
3. Registers all 18 composable routes in the `NavHost` (route wiring)

Every new screen requires editing `MainShell.kt` for an import, a `composable(...)` registration, and potentially a callback parameter in a tab root composable. It becomes a merge-conflict file.

**The fix:**

```
presentation/shell/
  AppRoutes.kt      ← string constants for all routes ("scan", "result/{scanId}", etc.)
  TopTab.kt         ← TopTab sealed class only
  MainShell.kt      ← NavigationBar + Scaffold only (~60 lines)
  AppNavGraph.kt    ← NavHost + all composable() registrations (~90 lines)
```

`MainShell` calls `AppNavGraph(navController, startDestination)`.  
Adding a new screen only touches `AppRoutes.kt` (add a constant) and `AppNavGraph.kt` (add a `composable(...) { }` block).

**Why it matters:** Clear separation of "what the navigation looks like" (`MainShell`) from "what routes exist" (`AppNavGraph`). A team of two can work on new screens simultaneously without touching the same file.

---

### Step 6 — Split `AppModule.kt` into three focused DI files

**What is wrong today:**

`AppModule.kt` contains three `@Module @InstallIn(SingletonComponent::class) object` blocks in a single file: `DatabaseModule`, `NetworkModule`, and miscellaneous providers. Three unrelated concerns.

**The fix:**

```
di/
  DatabaseModule.kt   ← Room builder + all DAO @Provides
  NetworkModule.kt    ← OkHttp + Retrofit + Groq/OpenFoodFacts/Server APIs + Moshi singleton
  DomainModule.kt     ← OcrParser + any other domain-layer singletons not auto-injected
```

**Why it matters:** Adding a new network API only touches `NetworkModule.kt`. Adding a new database table only touches `DatabaseModule.kt`. The files are small, focused, and independently readable.

---

## Part 2 — Release Readiness

### 🔴 Store submission blockers — must fix before any APK distribution

---

**B-1 — No app icon**

No `mipmap-*` directories exist. `ic_launcher` and `ic_launcher_round` are referenced in the manifest but resolve to nothing. The app will fail to install on a real device.

*Fix:* Create an adaptive icon in `mipmap-anydpi-v26/ic_launcher.xml` (foreground + background layers) and export PNG assets to all five density buckets: `mdpi` (48×48), `hdpi` (72×72), `xhdpi` (96×96), `xxhdpi` (144×144), `xxxhdpi` (192×192). The foreground should be the 🥦 or a clean scan-line mark; the background should match `AccentGreen (#5BCA8E)`.

---

**B-2 — Status and navigation bars stay black in light theme**

`values/themes.xml` hardcodes `android:statusBarColor = black`, `android:navigationBarColor = black`, `android:windowLightStatusBar = false`, and `android:windowLightNavigationBar = false`. Switching to light theme in Settings makes the Compose surface white but leaves the system bars solid black.

*Fix:* Use `WindowCompat.getInsetsController(window, view)` in `MainActivity` and set `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` based on the active theme. Also add a `values/themes.xml` (light parent with transparent bars) and `values-night/themes.xml` (dark parent) to handle the XML-side window background flash before Compose draws.

---

**B-3 — No `FileProvider` declaration**

Any future share-sheet integration (exporting diary CSV, sharing a result card, camera output via URI) requires a `FileProvider`. Without it, Android 7+ throws `FileUriExposedException` at runtime for any file-backed `Intent`.

*Fix:* Add a `<provider>` entry in `AndroidManifest.xml` pointing to `androidx.core.content.FileProvider` with `android:authorities="${applicationId}.fileprovider"`, and create `res/xml/file_paths.xml` defining at minimum a `<cache-path>` for scan output images.

---

### 🟠 UX and platform issues — highly visible to users

---

**U-1 — All 171 UI strings are hardcoded French literals**

The app has `values/strings.xml` (8 entries) and `values-en/strings.xml` (8 entries), but zero `stringResource(...)` calls. The language toggle in Settings saves a preference that changes the LLM prompt language but has no visible effect on any UI label, button, or heading. Every user who switches to English still sees French UI.

*Fix:* Move all visible strings from Compose `Text("...")` literals into `strings.xml` / `strings-en.xml`, and call `stringResource(R.string.*)`. The existing FR/EN `strings.xml` files provide the correct structure — they just need to be populated and wired.

---

**U-2 — No splash screen**

`MainActivity.onCreate()` makes two serial `runBlocking { prefs.X.first() }` DataStore reads before calling `setContent {}`. DataStore deserialises a protobuf from disk on first access — on a cold start this takes 150–400ms during which the user sees a plain black rectangle. Android 12+ expects the Splash Screen API.

*Fix:* Add `implementation(androidx.core:core-splashscreen)` and configure it in `themes.xml` with `postSplashScreenTheme`. Move the DataStore reads into a `SplashViewModel` that drives a `collectAsState` — the splash stays visible until the reads complete, then the nav graph launches.

---

**U-3 — Screen transitions are instant cuts**

All `navController.navigate(...)` calls use the default Compose Navigation transition: a hard cut with no animation. Material 3 specifies:

- **Shared-axis horizontal** (slide): forward push / back pop between peer-level screens (scan → result, dashboard → weight)
- **Fade-through**: tab switches in the bottom nav
- **Container transform** (optional): for detail views (scan card → result)

*Fix:* Add `enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition` parameters to `composable(...)` calls in `AppNavGraph`. Use `slideInHorizontally + fadeIn` / `slideOutHorizontally + fadeOut` for push navigation, and `fadeIn + fadeOut` for tab root switches. The `accompanist-navigation-animation` library is no longer needed — Compose Navigation 2.7+ includes built-in animation support.

---

**U-4 — No predictive back gesture support**

Android 13+ shows a live preview of the previous screen as the user begins a back swipe. Without `android:enableOnBackInvokedCallback="true"` in the manifest, this feature is silently disabled. The system back gesture still functions but feels dated.

*Fix:* Add `android:enableOnBackInvokedCallback="true"` to the `<activity>` element in `AndroidManifest.xml`. Verify no `Activity.onBackPressed()` override conflicts. Add `BackHandler` composables in any screen that needs to intercept the back gesture (e.g. the scanner mid-capture).

---

**U-5 — Edge-to-edge insets not consistently applied**

`enableEdgeToEdge()` is called in `MainActivity`, which draws the app content behind the system bars. But `MainShell`'s `Scaffold` does not declare `contentWindowInsets`. On devices with gesture navigation, the bottom `NavigationBar` may overlap the system gesture indicator. On notched devices, top content may render behind the cutout.

*Fix:* Add `contentWindowInsets = WindowInsets.systemBars` to the root `Scaffold` in `MainShell`. Add `Modifier.navigationBarsPadding()` to any bottom-pinned surface that is not already inside the scaffold's content padding.

---

### 🟡 Code quality and correctness

---

**Q-1 — No tests for `BiolismEngine` or `PersonalScoreEngine`**

`ScoringEngineTest.kt` has 15 well-written test cases covering the scoring engine. `BiolismEngine.kt` (574 lines, 7-phase RQ curve, 3-pool substrate partition, 12 hormones, organ heat distribution, full metabolic result) has zero tests. `PersonalScoreEngine.kt` (438 lines, Mifflin-St Jeor, Katch-McArdle, gap-closer logic, macro targets) has zero tests.

A regression in `computeKetoRQ` or `computeHormones` would be completely invisible. Both files are pure Kotlin functions with no Android dependencies — they can be tested with plain JUnit4.

*Fix:* Add `BiolismEngineTest.kt` covering at minimum: `computeKetoRQ` at each of the 7 phase boundaries, `computeSubstrates` for protein fraction at key keto hours (0h, 24h, 96h, 504h), `computeMetabolics` for a known male/female profile with expected BMR range, and `computeHormones` for expected testosterone and insulin direction under ketosis. Add `PersonalScoreEngineTest.kt` for BMR/TDEE outputs and macro target ranges.

---

**Q-2 — `Coil` is a declared dependency used nowhere**

`implementation(libs.coil.compose)` adds ~400KB to the uncompressed APK. No `AsyncImage`, `rememberAsyncImagePainter`, or `ImageLoader` call exists anywhere. Either the dependency is removed (freeing the binary size), or it is put to use.

Natural candidates for Coil: product thumbnail from OpenFoodFacts (`product_image_front_url` in the OFF response), recipe card images, and user-assigned food photos.

*Fix:* If thumbnails are not planned, remove the dependency. If they are planned, wire the `imageUrl` field (if present in `Product`) to `AsyncImage(model = product.imageUrl)` in `ResultScreen`'s product card.

---

**Q-3 — `Moshi` is instantiated in two repositories that bypass the singleton**

`CustomFoodRepository` constructs `Moshi.Builder().add(KotlinJsonAdapterFactory()).build()` inline. This bypasses the `@Singleton Moshi` provided by `AppModule` and creates a redundant instance with its own adapter registry. If a custom adapter is ever added to the `AppModule` Moshi (e.g. for a custom type), `CustomFoodRepository` will never see it.

*Fix:* Inject `private val moshi: Moshi` into `CustomFoodRepository` via its `@Inject constructor`. Remove the inline `Moshi.Builder()` call.

---

**Q-4 — No network connectivity check before scanning**

`ScanViewModel.score()` launches a scan coroutine with no check for internet access. Offline users get a raw Retrofit exception message like `"failed to connect to api.groq.com (port 443) after 30000ms"`. This message leaks implementation details and is incomprehensible to a non-technical user.

*Fix:* Inject `ConnectivityManager` (or use `NetworkCallback`) and check connectivity before calling `scanRepo.scoreBarcode()`. Emit a clear `ScanUiState.Error("Pas de connexion internet")` immediately rather than waiting 30 seconds for the timeout.

---

**Q-5 — `MealTemplateRepository.expand()` writes multiple diary entries without a Room `@Transaction`**

When logging a meal template (e.g. a breakfast with 4 items), `TemplatesViewModel` loops `consumptionRepo.log(it)` for each entry. If the process is killed after 2 of 4 inserts, partial data is written with no way to detect or roll back. Room does not provide automatic multi-operation atomicity without `@Transaction`.

*Fix:* Add a `suspend fun logAll(entries: List<DiaryEntry>)` method to `ConsumptionRepository` annotated with `@Transaction`, which loops `dao.insert()` inside a single transaction. `TemplatesViewModel` and `RecipesViewModel` call this instead of individual `log()` calls.

---

**Q-6 — `MainActivity` blocks the main thread twice with `runBlocking`**

```kotlin
val apiKey    = runBlocking { prefs.groqApiKey.first() }
val serverUrl = runBlocking { prefs.serverUrl.first() }
```

Two serial blocking DataStore reads before `setContent {}`. On a cold start, DataStore must deserialise the Preferences protobuf from disk — each read can take 50–200ms. Combined: 100–400ms of ANR-risk main-thread blocking before the first frame.

*Fix:* Merge into a single read: `runBlocking { combine(prefs.groqApiKey, prefs.serverUrl) { a, b -> a to b }.first() }`. Better: move entirely into a `SplashViewModel` that reads asynchronously and drives the start destination reactively, eliminating `runBlocking` entirely.

---

### 🔵 Platform compatibility

---

**P-1 — `themes.xml` uses deprecated `android.Theme.Material.NoTitleBar`**

The XML base theme inherits from the legacy Material 1 theme from Android 5. For a Compose-only app on API 26+, the correct parent is `Theme.AppCompat.DayNight.NoActionBar` or `Theme.MaterialComponents.DayNight.NoActionBar`. The legacy parent can cause subtle window inset and system bar animation conflicts with Compose Material 3.

*Fix:* Change `parent="android:Theme.Material.NoTitleBar"` to `parent="Theme.MaterialComponents.DayNight.NoActionBar"` and add the `material` dependency if not already present (it likely is via the Compose BOM).

---

**P-2 — No accessibility semantics on custom composables**

Only 1 `contentDescription` string is set across the entire presentation layer. Custom composables — `FeatureTile`, `BioCard`, `KetosisToggleRow`, `PhaseStrip`, `HormoneRow`, `MetCellGrid`, the substrate bar — have no `Modifier.semantics {}` annotations. TalkBack users cannot navigate the app usefully.

*Fix:* Add `Modifier.semantics { contentDescription = "..." }` to every interactive element that conveys information beyond its label. `Icon` composables that carry meaning (not just decoration) need a real `contentDescription` string, not `null`. `BioCard` collapsible sections need `Modifier.semantics { heading() }` and `stateDescription = if (open) "expanded" else "collapsed"`.

---

**P-3 — `android:supportsRtl="false"` disables right-to-left layout**

Explicitly disabling RTL prevents the OS from mirroring layouts for Arabic, Hebrew, Persian, and Urdu speakers. It also prevents correct mirroring of directional icons (back arrows, forward chevrons, progress bars) even in future locales.

*Fix:* Change to `android:supportsRtl="true"`. Audit all `Modifier.padding(start/end)` usages (should already be correct since Compose uses start/end by convention). Audit any hardcoded `Arrangement.Start` / `Arrangement.End` that may need to become mirrored for RTL.

---

## Execution Order

Treating all three parts together, the recommended order is:

| # | Area | Item | Why first |
|---|---|---|---|
| 1 | Code | Split `Colors.kt` + remove `BiolismTokens` | Unblocks Step 2 theme work; simplest pure-file change |
| 2 | Platform | Fix `themes.xml` parent + light/dark variants | Required for B-2 status bar fix; blocks icon work visually |
| 3 | Store | Create app icon | Needed before any real-device install |
| 4 | Code | Split `Daos.kt` / `Entities.kt` | Self-contained; no downstream import changes outside `db/` |
| 5 | Code | Regroup `domain/engine/` subdirectories | Only import updates; downstream repositories unaffected |
| 6 | Code | Regroup `data/repository/` subdirectories | Import updates in ViewModels; depends on Step 5 package names |
| 7 | Code | Split `MainShell.kt` into 4 files | Depends on Steps 5–6 for correct import paths in AppNavGraph |
| 8 | Code | Split `AppModule.kt` into 3 files | Last — depends on Steps 4–6 import paths |
| 9 | UX | Add screen transitions | After nav restructure (Step 7) is stable |
| 10 | Store | Add `FileProvider` | Needed before share-sheet feature work |
| 11 | UX | Splash screen + async DataStore reads | Requires themes.xml fix (Step 2) first |
| 12 | UX | Edge-to-edge insets | After splash screen resolves first-frame timing |
| 13 | UX | Predictive back gesture | Quick manifest change; after nav structure is stable |
| 14 | Quality | `BiolismEngine` + `PersonalScoreEngine` tests | Pure Kotlin; no dependency on any other step |
| 15 | Quality | Remove `Coil` or wire it | Quick build change; decide before next release |
| 16 | Quality | Fix `Moshi` singleton injection | Simple constructor change; no logic impact |
| 17 | Quality | Network connectivity check | After scanner flow is stable |
| 18 | Quality | `@Transaction` on multi-entry writes | After data/repository restructure (Step 6) |
| 19 | i18n | Wire `stringResource()` calls | After all UI labels are known and stable |
| 20 | Accessibility | Add semantics to custom composables | Final UX pass before public release |
| 21 | Platform | `supportsRtl="true"` audit | Final platform pass; verify Compose start/end usage |

---

*End of document. This file should be updated as items are completed.*
