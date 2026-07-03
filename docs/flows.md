# UX flows + sitemap

Scan'eat is a single-page PWA. Everything lives under one URL; the UI is
decomposed into persistent surfaces + modal dialogs. This doc maps the
user-visible surfaces and the primary flows across them.

## Sitemap

```
/  (index.html)
├── <main>
│   ├── <aside#install-banner>                  Install prompt (beforeinstallprompt)
│   ├── .capture                                Barcode live + file input
│   │   ├── #barcode-live-btn                   BarcodeDetector camera
│   │   └── #file-input                         File picker (label + food photos)
│   │
│   ├── #daily-dashboard                        Today's summary
│   │   ├── .dashboard-actions                  Chip buttons → dialogs below
│   │   ├── #dashboard-streak                   "N-day streak" if ≥ 2
│   │   ├── #dashboard-remaining                "Remaining N kcal / g sugar / g salt"
│   │   ├── #weight-summary                     Current + trend + forecast
│   │   ├── #fasting-tile                       Timer + streak
│   │   ├── #hydration-tile                     Glasses counter
│   │   ├── #activity-tile                      Today's exercise
│   │   ├── #dashboard-rows                     Per-nutrient progress bars
│   │   ├── #weekly-view                        (toggle) 7-day bar chart
│   │   ├── #dashboard-log (<details>)          Per-meal entries list
│   │   ├── #gap-closer                         Close-the-gap suggestions
│   │   └── #day-note-wrap (<details>)          Free-text day note
│   │
│   ├── <section#result>                        Post-scan product view
│   │   ├── Grade badge + score + verdict
│   │   ├── Personal score panel
│   │   ├── #alternatives                       Better-scoring same-category
│   │   ├── .product (name, category, chips)
│   │   ├── #allergens                          Annex II hits
│   │   ├── #pairings                           Ahn 2011 chip row + source line
│   │   └── .portion-panel                      "Log this" (grams + meal)
│   │
│   └── #recent-scans                           Last 30 scanned products
│
├── Dialogs (native <dialog>, opened from buttons):
│   ├── #settings-dialog                        Language, theme, API key, advanced
│   ├── #profile-dialog                         Age, sex, height, weight, activity, diet, modifiers, macros
│   ├── #onboarding-dialog                      First-run tour
│   ├── #about-dialog                           Credits, sources, license
│   ├── #explain-dialog                         Per-flag explanation popups
│   ├── #pillar-dialog                          Score-pillar breakdown
│   ├── #camera-dialog                          Live barcode scanner overlay
│   ├── #quick-add-dialog                       Quick Add (voice / photo / menu / manual)
│   ├── #activity-dialog                        Log exercise session
│   ├── #weight-dialog                          Log weight, see history
│   ├── #progress-dialog                        30-day progress charts
│   ├── #templates-dialog                       Meal templates (snapshot + apply)
│   ├── #tpl-name-dialog                        Rename a template
│   ├── #recipes-dialog                         Recipes list + pantry + grocery buttons
│   ├── #recipe-edit-dialog                     Component editor for a recipe
│   ├── #meal-plan-dialog                       7-day meal plan grid
│   ├── #pantry-dialog                          Pantry-first ingredient entry
│   ├── #recipe-ideas-dialog                    LLM recipe cards (from ingredient OR pantry)
│   ├── #menu-scan-dialog                       Picker for dishes extracted from menu photo
│   ├── #custom-foods-dialog                    User's per-100 g food library
│   └── #grocery-dialog                         Aggregated shopping list + copy/share
```

## Primary flows

### 1. First run (onboarding)

```
Open app
  → #onboarding-dialog auto-opens
  → User enters name + language + theme + (optional) Groq API key
  → Dialog closes
  → Dashboard is empty; "Scanner un code-barres" and "Prendre / choisir une
    photo" are the two big capture buttons at the top
```

### 2. Barcode scan (happy path)

```
User taps #barcode-live-btn
  → #camera-dialog opens, BarcodeDetector reads the live video stream
  → On successful detect: haptic blip, dialog closes, app enters scan pipeline
  → /api/score (or direct-mode Groq) fires → OFF lookup → merge → score
  → <section#result> reveals:
     - Grade + score + verdict
     - Personal score panel
     - #alternatives (fires separately, appears when ready)
     - Ingredients + allergens
     - #pairings (instant, offline)
     - #recipe-ideas-btn inside pairings
  → User taps "Log this" → portion-panel appears with grams + meal select
  → "Logger" writes consumption entry → dashboard refreshes
```

### 3. Food photo (no barcode)

```
User taps #qa-photo-input (Quick Add → 📸 Photo de mon repas)
  → Select / capture photo
  → compressImage → /api/identify (or direct-mode)
  → Groq returns { name, estimated_grams, kcal, protein_g, carbs_g, fat_g }
  → reconcileWithFoodDB(result) swaps macros for CIQUAL values if name matches
  → Quick Add form pre-fills; status chip shows provenance
  → User confirms / edits → Logger
```

### 4. Multi-item plate → recipe

```
User taps #qa-photo-multi-input (🍽️ Plateau multi-aliments)
  → /api/identify-multi returns up to 8 items with per-item macros
  → reconcileWithFoodDB per item
  → Each item is logged as its own entry (auto)
  → Stash reconciled items in module-scope lastIdentifiedPlate
  → toast "✓ N items logged (M from DB)"
  → User opens #recipes-dialog later → "📸 From last plate scan" button
  → openRecipeEditor pre-filled with components
```

### 5. Restaurant menu photo

```
User taps #qa-photo-menu-input (📜 Menu restaurant)
  → /api/identify-menu returns up to 12 dishes with per-dish macros
  → #menu-scan-dialog opens (distinct from plate — semantics differ)
  → Each dish is a row with a "Ajouter" button
  → User picks dishes → individual logs
```

### 6. Pantry-first recipe search

```
User taps #recipes-btn → #recipes-dialog → "🥕 Avec ce que j'ai"
  → #pantry-dialog opens
  → User lists ingredients (newline or comma separated, up to 20)
  → "Trouver des idées" → /api/suggest-from-pantry
  → Recipe ideas dialog opens with 3 cards
  → Each card: name + time_min + kcal + ingredients + steps
```

### 7. 7-day meal plan → grocery list

```
User taps #meal-plan-btn
  → #meal-plan-dialog opens with a 7-day grid
  → Each cell's <select> lets the user pick: recipe | template | 📝 note
  → "🛒 Liste de courses du planning" button
  → planRecipes(dates, recipes) resolves recipe-slots
  → aggregateGroceryList() sums ingredients
  → #grocery-dialog opens with bullet list + textarea + Copy + Share
```

### 8. CSV import (migration from MFP / Cronometer)

```
User opens #settings-dialog → Sauvegarde
  → "📥 Importer MFP / Cronometer…" chip
  → File picker → CSV
  → parseCsvImport(text) detects format + maps rows
  → putEntry per valid row
  → status line: "✓ N imported (format, M skipped)"
  → Dashboard re-renders with historical data
```

### 9. Gap-closer feedback loop

```
User logs breakfast → dashboard renders
  → renderGapCloser(totals, targets) computes deficits
  → For each under-target nutrient, findPairings through FOOD_DB
  → #gap-closer panel shows "Manque 12 g de fibres : amandes · 38 g · …"
  → User scans another product, logs it, panel updates
  → When all targets hit, panel hides
```

## Dead-end + recovery

| Dead end | Recovery |
|----------|----------|
| Barcode not in OFF | LLM fallback → user can edit before logging |
| Camera permission denied | Fallback to file picker remains available |
| LLM returns `confidence: low` on food photo | Warning chip + user can edit fields |
| LLM returns empty dishes on menu scan | `menuScanEmpty` message, user re-takes photo |
| Pantry search returns 0 recipes | `recipeIdeasEmpty` shown in the recipe-ideas dialog |
| Reconcile match on wrong food (false positive) | User edits name in Quick Add before logging |
| IDB quota | No graceful path today — flagged as a known gap in `ASSUMPTIONS.md` |

## Keyboard paths (a11y)

- Skip link (`Tab` from top → "Aller au contenu") jumps focus into `<main>`.
- Every capture button is a real `<button>`; `Enter` / `Space` activates.
- Dialogs trap focus (native `<dialog>` behaviour); `Esc` closes.
- Lang select, theme select, macro split select: all keyboard-native.
- Quick Add form: `Tab` through name → meal → kcal → macros → save.

## State regions

| State | Store |
|-------|-------|
| Consumption log | IDB `consumption` |
| Scan history | IDB `history` |
| Weight | IDB `weight` |
| Activity | IDB `activity` |
| Recipes | IDB `recipes` |
| Meal templates | IDB `meal_templates` |
| Pending scan queue (offline) | IDB `pending_scans` |
| Profile | `localStorage` `scanneat.profile` |
| Settings (theme, lang, fonts, modes) | `localStorage` via `core/app-settings.js` |
| Custom foods | `localStorage` `scanneat.customFoods` |
| Day notes | `localStorage` `scanneat.note.YYYY-MM-DD` |
| Meal plan | `localStorage` `scanneat.mealPlan` |
| Fasting (live + history) | `localStorage` `scanneat.fasting.*` |
| Hydration (per date) | `localStorage` `scanneat.hydration.YYYY-MM-DD` |
| Install banner snooze | `localStorage` `scanneat.installBanner.snoozedUntil` |
| Telemetry buffer (opt-in) | `localStorage` `scanneat.telemetry.buffer` |
| Groq API key (direct mode) | `localStorage` `scanneat.groqKey` |
