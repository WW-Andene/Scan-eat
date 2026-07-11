# Scan'eat — §X R&D & Improvement Pass

Existing-feature health evaluation, competitive research, and a prioritized
roadmap. Written after the A-O × 4-layer app-audit (60 findings, all CI-green)
closed out the correctness/polish backlog — this pass looks forward instead:
what should the product become next.

## 1. Existing-feature health evaluation

What Scan'eat already has, as of this pass:

- **Scan pipeline**: barcode (ML Kit, multi-format incl. ITF/GTIN-14) → Open
  Food Facts lookup with parallel candidate-code racing → vision-LLM (Groq)
  fallback for OCR label parsing and food identification when OFF has no
  data. Retry/fallback-model logic, prompt-injection hardening added this
  session.
- **Scoring**: a from-scratch nutritional scoring engine (density pillar,
  additives penalty, NOVA-processing penalty, global bonuses) plus a
  *personal* score layer that re-weights against the user's own health
  profile/conditions — this personalization is not something any competitor
  found in this pass's research does.
- **Diet compliance**: DietChecker (vegan, halal w/ certification override,
  keto net-carb math, paleo, dairy-free) and a separate AllergenDetector,
  both regex-driven over ingredient text, now with dedicated unit tests
  (§O1/L4) after this session found and fixed a real false-positive bug.
- **Metabolic modeling ("Biolism"/Métabolisme)**: TDEE, substrate flux,
  ketosis process modeling, thermoregulation, hormone modifiers, an
  equations-transparency panel. This is materially deeper than any
  competitor surfaced in the research below — none of Yuka, Open Food
  Facts, Fooducate, Cronometer, or Nutrola model metabolism, only intake.
- **Tracking ecosystem**: Diary, Weight (+ Health Connect sync), Hydration,
  Fasting, Recipes, Meal Plan, Grocery list (with markdown-checklist
  export), Custom foods, Templates, Favorites, Reminders (localized,
  Doze-resilient), local backup export/import.
- **Accessibility/i18n**: RTL support, colorblind palettes, dyslexia font,
  full FR/EN localization, TalkBack semantics added incrementally through
  the audit (veto reasons, weight-trend sparkline, etc).

Overall: the intake-scoring-and-tracking loop is competitive with the
category leaders on breadth, and the metabolic-modeling depth is a genuine
differentiator nothing else in the category has. The weakest link is
**micronutrient coverage relative to depth-focused competitors** and the
**complete absence of a "why is this scored low, what should I buy instead"
recommendation loop**, detailed below.

## 2. Competitive research (web search, July 2026)

| App | Core mechanic | Notable strength | Notable gap vs Scan'eat |
|---|---|---|---|
| **Yuka** (85M users) | Barcode scan → 0-100 score: 60% nutrition, 30% additives, 10% organic | Huge user base, "find a better product nearby" suggestion loop | No personalization, no diet-specific compliance, no metabolic modeling |
| **Open Food Facts** | Open-source DB, NOVA classification | Data openness, community-sourced | No scoring personalization, no diet checker, no LLM-vision fallback for unlisted products |
| **Fooducate** | Letter grade (A-D) from trans fat/sugar/fiber/artificial-ingredient heuristics | Simple, fast grade | Coarser than Scan'eat's pillar-based engine; no diet/allergen layer |
| **Cronometer** | Deep quantitative micronutrient tracking (80-100+ nutrients), free barcode scan | Best-in-class micronutrient depth | No qualitative score, no diet checker, no metabolic modeling |
| **Nutrola / Nutika / Olive** | AI-assessed holistic score; some read ingredient labels directly (OCR, no barcode needed) | Same OCR-first approach Scan'eat already has via its Groq vision fallback | Narrower tracking ecosystem than Scan'eat (no fasting/hydration/recipes/meal-plan/grocery) |

Sources: TMS Outsource "Must-Try Apps Like Yuka", Trash Panda "Best Food
Scanner Apps Compared (2026)", Nutrisense "11 Best Food Tracker Apps
(2026)", Nutola "Best Food Scanner App for Nutrition Information (2026)",
Fortune "Best Nutrition Apps (2026)", Fitia "Top 12 Nutrition Tracking Apps
(2026)".

**Takeaway**: no single competitor combines qualitative scoring +
quantitative micronutrient depth + diet/allergen compliance + metabolic
modeling + a full tracking ecosystem. Scan'eat's actual competitive
weakness isn't feature breadth, it's that a few specific, high-value loops
other apps treat as core are entirely absent here.

## 3. Gap analysis (prioritized)

Scored on user impact × how directly it plays to Scan'eat's existing
architecture (i.e. cheap to build well vs. requiring a new subsystem).

1. **No "healthier alternative" suggestion** (High impact, moderate cost —
   implemented this pass). Yuka's single most-cited feature is "same
   category, better score, nearby" — Scan'eat computed a score but never
   surfaced what a better choice would have been. Confirmed absent by
   code search (no alternative/substitut/suggestion logic anywhere in
   scoring or result presentation). Scan'eat has no live nearby-product
   database to search, so this was scoped honestly to what the app
   actually has: `ScanHistoryDao.findBetterInCategory()` now finds the
   best-scoring product the user has *themselves already scanned* in the
   same category, surfaced as a new `AlternativeCard` on the result
   screen whenever the current product grades C or worse. It's a real,
   verifiable suggestion sourced from the user's own history — not a
   fabricated "near you" claim this sandbox has no way to back.

2. **Diet definitions carry a real adequacy note (`DietDef.noteFr/noteEn`)
   that never reached the UI, and vegan's note said nothing about B12**
   (High impact for the exact users DietChecker already targets, low
   cost). Correcting an assumption from an earlier pass of this doc:
   `NutritionPer100g` already tracks the full B-complex (B1/B2/B3/B6/B9/
   B12), not just A/C/D/E/K — so this was never a data-model gap. The
   actual gap, confirmed by reading `ProfileScreen.kt`'s `DietSelector`:
   every diet's descriptive note already existed in `DietDef` but was
   only ever consumed internally (as part of `checkDiet()`'s violation
   `reason` string for halal/kosher), never shown next to the diet-picker
   chips themselves — so picking "Vegan" or "Keto" showed a label and
   nothing else. Fixed directly (not just queued): added a public
   `dietNote()` accessor, wired it into `DietSelector` as a caption under
   the selected chip, and added a B12-supplementation line to vegan's
   note text, since B12 deficiency is the headline nutritional risk of
   that diet and the diet checker had no way to say so.

3. **Multi-provider API key support** (task #72, still pending). Currently
   single-provider (Groq) for the vision LLM. Diversifying reduces a
   single point of failure for the OCR fallback path and was already
   queued by the user before this audit began — re-surfacing it here
   because it's now the oldest open item in the whole backlog.

4. **Community/crowdsourced correction loop** (Medium impact, high cost —
   requires backend infrastructure Scan'eat doesn't have. Explicitly
   deprioritized: OFF already provides the crowdsourced data layer Scan'eat
   depends on; building a second one would duplicate it for no clear gain.)

5. **Response-format JSON mode for the Groq OCR calls** (already in
   QUEUE.md from the audit — small reliability win, blocked only on a live
   API check this sandbox can't safely perform blind).

Items 4 and 5 are intentionally not scheduled below — 4 is a scope
rejection, 5 is already correctly parked in QUEUE.md pending live
verification tooling.

## 4. Roadmap

- **Now**: item 1 (healthier-alternative suggestion) and item 2 (diet-note
  surfacing + vegan B12 caveat, already implemented this pass) — both fit
  the existing scoring/diet-checker architecture with no new subsystem,
  and both were confirmed as real, currently-missing capabilities via
  code search, not guessed.
- **Next**: item 3 (multi-provider API key support, task #72) — unblocks
  provider resilience for the OCR fallback path.
- **Later / explicitly parked**: item 4 (crowdsourced corrections — scope
  rejection, OFF already fills this role), item 5 (response_format JSON
  mode — parked in QUEUE.md pending live API verification).
