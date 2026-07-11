# Scan'eat — Full Deep Design Audit

A complete design/aesthetic audit run against the design-aesthetic-audit
skill's "full deep" scope: Phase 1 (core aesthetic, 21 steps), Phase 2
(expanded UI audit — visual design, UX/IA/copy, accessibility, mobile touch,
design-system polish, mobile performance), and Phase 3 (cross-cutting
compound-finding analysis). Findings use the standard template — Severity /
Dimension / Finding / Why it matters / Recommendation / Effort. Severity
scale: CRITICAL / HIGH / MEDIUM / LOW / POLISH.

Evidence comes from direct reads of the actual Kotlin/Compose source (three
parallel research passes covering color/theme, typography/motion, and
components/icons/states, plus targeted greps throughout), not assumption.
Anything not independently verified is marked `[UNVERIFIED]` rather than
asserted. This document is organized for reading, not in the skill's
execution order — the original §-codes are kept on every finding so they
can still be cross-referenced against the source skill files (see the
Appendix).

---

## EXECUTIVE SUMMARY

**What the design already says** (confirmed character, see Part A): Scan'eat
reads as *a warm light glowing in a dark room* — a deliberate, well-engineered
color system (OLED near-black + warm gold/coral/teal, CVD-safe grading) that
promises atmosphere and depth. Everything outside the palette — icons,
motion, elevation, shape — is currently generic Material/Compose defaults
that don't deliver on what the color tokens already name. The single biggest
opportunity in this audit is that the fix for this doesn't require new design
decisions: the color tokens for the "glow" (Haze/Trace/Glow families) already
exist in code, unused.

**Priority order** (derived from severity + Phase 3's compound-chain analysis
— see Part F for the full reasoning):

1. ~~**Verify WCAG contrast on the diet-veto/allergen banners**~~ — DONE.
   Computed contrast ratios confirm all pairings pass WCAG AA (13.8:1 body
   text, 4.7-7.7:1 label/icon color, worst case 4.9:1 on ComparisonCard's
   FlagRed-on-tinted-fill text) across OLED/Dark/Light themes. No color
   change needed — see Part B1 and Part F Chain 1 for the full computation.
2. **Build the score-reveal signature moment** (Part B5, B8, D) — the
   highest-leverage single fix in the audit: closes four separate findings
   (motion, states, color, atmosphere) at once, using tokens that already
   exist. Must ship together with reduced-motion gating (Part F Chain 2),
   not before it.
3. **Realize the Glow/Haze atmosphere tokens as actual rendered light**
   (Part B6) — the concrete fix for the audit's core finding (Dimension 3
   and 5 gaps in Part A).
4. **Build the two missing Layer-3 components** — `ScanEatPrimaryButton`
   and `ScanEatCard` (Part B4, B10, Part F Chain 4) — do this *before* the
   naming/consolidation cleanups it unblocks, not after.
5. **Unify the three error-state systems into one ErrorBanner component**
   (Part B8, Part F Chain 5) — reprioritized from a polish finding to an
   accessibility-compliance finding once cross-referenced against §G1.

**What NOT to change:** the OLED background's cool-violet undertone, Gold's
current moderate chroma, the CVD-safe grade-color system, `glassSheen()`'s
mechanism, the icon set's internal consistency (100% filled Material Icons —
keep, just tint), and the deliberate non-adoption of Material You Dynamic
Color, real glassmorphism blur, and visual trend-chasing. Each is addressed
in its Part with an explicit PROTECT note.

### Master Findings Table

| # | Severity | Area | Finding | Detail |
|---|----------|------|---------|--------|
| 1 | ~~CRITICAL*~~ FIXED | Contrast/Safety | WCAG contrast verified PASS on allergen/diet-veto banners | B1, F1 |
| 2 | CRITICAL | Surface/Atmosphere | Glow/Haze tokens exist but aren't rendered as light | B6 |
| 3 | HIGH | Motion/States | Score reveal has no signature moment | B5, B8, D |
| 4 | HIGH | Typography | Zero tabular figures on any numeric display | B2 |
| 5 | HIGH | Components | No shared Button component — recipe hand-copied, drifting | B4 |
| 6 | HIGH | States | Three unreconciled error-banner systems in one app | B8, F5 |
| 7 | MEDIUM | Tokens | No Layer-3 component tokens — find/replace test fails | B10, F4 |
| 8 | ~~MEDIUM~~ FIXED | Color | OLED theme's surface/surfaceVariant identical (no elevation tier) | B1 |
| 9 | MEDIUM | Color | No pressed/hover accent state token | B1 |
| 10 | MEDIUM | Typography | "Hero number" role untokenized (4 weight spellings, 5+ sizes) | B2 |
| 11 | MEDIUM | Components | Cards are hand-rolled per screen, glassSheen() inconsistent | B4 |
| 12 | MEDIUM | Iconography | Icon expressiveness stuck at "Utilitarian" | B3 |
| 13 | MEDIUM | Motion | Zero reduced-motion accommodation anywhere | B5, F2 |
| 14 | MEDIUM | Hierarchy | Chroma contrast under-used outside the score ring | B7 |
| 15 | MEDIUM | States | Empty states have no CTA slot | B8 |
| 16 | MEDIUM | States | Loading states have no character treatment | B8 |
| 17 | ~~MEDIUM~~ FIXED | Contrast | WCAG verification gap (Phase 2 restated) — now verified PASS | F1 |
| 18 | MEDIUM | Touch | 12+ IconButtons at 32-36dp, below 48dp guideline | F3 |
| 19 | LOW | Color | "AccentGreen" misnamed (it's coral); 3 unreconciled gold values | B1 |
| 20 | LOW | Brand | Light theme re-derives colors independently of shared tokens | A6 |
| 21 | LOW | Tokens | No separator-weight taxonomy | A6, B10 |
| 22 | LOW | Components | Nav tab-fade and bottom-nav tinting unconfirmed | B4 |
| 23 | LOW | Typography | 3/15 type slots never hand-tuned | B2 |
| 24 | LOW | UX | Scan error recovery is dismiss-only, no retry action | F2 |
| 25 | LOW | Copy | Voice is competent but under-warm vs. confirmed character | B9, F2 |
| 26 | POLISH | Color | No color/glow intensification at success moments | B1, B8 |
| 27 | POLISH | Surface | No grain/noise texture | B6 |
| 28 | POLISH | Typography | No letter-spacing/tracking on any type slot | B2 |
| 29 | POLISH | Trends | Colors authored as hex, not OKLCH (documentation-only gap) | B11 |

\* *Severity 1 is CRITICAL conditionally — HIGH until the contrast check is
actually run; escalates to CRITICAL only if it fails. See Part F Chain 1.*

Findings marked PROTECT (deliberate strengths, not gaps) are listed inline
in each Part and are not numbered above — see especially Part A (color
token hygiene, CVD-safe grading), Part B12 (trend restraint, Dynamic Color
non-adoption), and Part E (IA rework history, mobile-performance discipline).

---

# PART A — IDENTITY & CHARACTER

## A0. Aesthetic Context

```yaml
Design Identity:
  Current style:    Dark OLED base, warm-gold primary accent, coral secondary
                     (token misleadingly named "AccentGreen"), teal tertiary —
                     a hue-family "glow" color system (Gold/Teal/Violet/Warm/
                     Danger/MetaGreen, each with Glow/Border/Haze/Trace alpha
                     variants) suggesting an intended ambient-light aesthetic,
                     executed via flat surface fills + a custom glassSheen()
                     top-light overlay (no real blur/elevation).
  Intended style:    Inferable from the color-token naming itself (Glow/Haze/
                     Trace implies emanating light, i.e. atmospheric depth) —
                     more atmospheric/premium than what's currently rendered.
  Protected elements: OLED background, gold/coral/teal brand triad, the
                     CVD-safe grade-color system (genuinely well-engineered).

Five-Axis Quick Profile:
  A1 Commercial intent: Non-revenue currently (no ads/IAP/subscription found)
  A2 Use context:       High-stakes/Emotional — health data, weight, diet
                        compliance, allergen safety sit alongside quick
                        transactional barcode scans
  A3 Audience:          General public, French-first, health-conscious,
                        non-expert
  A4 Subject identity:  Domain conventions — nutrition-scoring apps (Yuka,
                        Nutri-Score) have an established green→red grading
                        convention this app follows and extends
  A5 Aesthetic role:    Amplifies value — visual trust in the score matters,
                        and the gold/dark identity is a real differentiator
                        vs. competitors' generic green-scale branding
```

## A1. Style Classification

```
Primary style: Cyberpunk/Terminal — recalibrated warm (dark OLED base +
  glowing accent color families), but without that style's usual neon/
  monospace/scanline signals
Secondary influences: Glassmorphism (attempted, incomplete — glassSheen()
  gives a top-light sheen + hairline edge, but no backdrop-filter/blur exists
  anywhere in the codebase, so it's "glass-lite" rather than true glass)
Coherence score: MIXED-INTENTIONAL
Style-appropriate execution: The color system itself (Gold 0xFFC9A84C,
  AccentGreen/coral 0xFFD97C56, Teal 0xFF38C8C8, plus full Glow/Border/Haze/
  Trace alpha families per hue) is a deliberate, non-default palette —
  clearly authored, not accidental. But neither reference style's other
  rules are followed: Cyberpunk calls for monospace/high-edge-contrast
  (absent — system Roboto, filled Material icons only); Glassmorphism
  requires actual blur (absent). The palette is ahead of the execution.
```

**Style coherence gaps**: shape has no scale (12dp dominates at ~44% of
sites, but 8/10/14/16/18/20dp are all also live for the same "card" role,
no documented rule); `glassSheen()` — the one genuinely distinctive surface
treatment — is applied to dashboard/result/diary/history/recipes cards but
*not* to Biolism tracker cards, onboarding, settings, or `RemindersCard`;
`ScanScreen.kt`'s primary permission button and `FastingScreen.kt`'s primary
button both omit the `shape = RoundedCornerShape(12.dp)` param ~8 other
primary buttons use (accidental drift, not intentional tension). **Clean**:
zero hardcoded hex colors found in any Kotlin composable outside the theme
directory. **Gap**: OLED scheme's `surface` and `surfaceVariant` are
identical (`0xFF1C1820`) — no elevation-tier separation in the default
theme, while the Dark (non-OLED) scheme does differentiate them correctly.

## A2. Character Extraction

```
Color character:
  Background:  OLED 0xFF0F0D12 (cool violet-tinted near-black, not neutral)
  Surface:     0xFF1C1820 (same cool-violet lean)
  Text:        0xFFEFEAE6 primary (warm cream)
  Accent:      Gold 0xFFC9A84C (primary, muted antique-gold, not oversaturated)
               AccentGreen 0xFFD97C56 (secondary, actually coral — most-used
               interactive color, ~28 files)
               Teal 0xFF38C8C8 (tertiary, sparing, mostly Biolism)
  Overall palette feeling: "Warm-metallic dark ambient" — cool-violet near-
               black stage lit by warm gold/coral accents.

Typography character:
  Typeface:    System default (Roboto), no custom base font; OpenDyslexic
               (2 weights) swapped in for the accessibility toggle.
  Weight range: Normal(400)-Black(900); "hero numbers" use Black but via 4
               different literal spellings (Black/Medium/W500/Bold) with no
               shared token.
  Size range:  11sp-56sp (the 56sp score digit has no named style at all).
  Overall feeling: Clean/utilitarian, moderately hand-tuned for 11/15 M3
               slots, least standardized exactly where it matters most
               (hero numbers).

Component character:
  Border radius: No single scale — 9 distinct values (2/3/4/6/8/10/12/14/16/
               18/20dp) in active use for cards/bars/chips.
  Shadow/elevation: Effectively none — exactly one shadowElevation value
               (6dp, one streak badge) in the entire card inventory.
  Button style: Filled, warm-colored, black label — confident and warm,
               undermined by 2 sampled call sites reverting to default shape.

Motion character:
  Durations:   200ms (tab-switch fade) / 300ms (peer-nav slide+fade), both
               default FastOutSlowInEasing; zero spring/physics anywhere;
               zero reduced-motion accessibility handling.

Icon character:
  100% Material Icons.Default/AutoMirrored.Filled — no custom iconography,
  ad hoc sizing (12-64dp, no shared token).

Copy/voice character: Direct, factual, functional French-first copy —
  competent, no distinct brand voice.

Emergent personality statement:
  "Based on these decisions, this app reads as: a nutrition-scanner reaching
  for a premium warm-gold dark-ambient identity, but only fully realizing
  that ambition in its color tokens — typography, iconography, motion, and
  shape are all left at generic Material/system defaults."

  Strongest signals: (1) OLED-violet-black + gold/coral/teal palette with
  Glow/Haze/Trace alpha families; (2) the 3-scale CVD-safe grade-color
  system (Okabe & Ito-derived); (3) glassSheen()'s custom surface treatment.

  Weakest signals: (1) fully generic Material iconography; (2) an elevation
  system that never materializes despite atmosphere-implying color tokens;
  (3) no shape scale — 9 distinct corner-radius values for one visual role.
```

## A3. Character Dimensions

```
Dim1 Visual Voice:      CURRENT: Warm, Quiet-to-balanced, Terse-to-balanced
                        GAP: small
Dim2 Spatial:           CURRENT: not fully evidenced (deferred)
                        GAP: unassessed
Dim3 Material Quality:  CURRENT: Flat (confirmed near-zero elevation) vs.
                        token names implying Deep/atmospheric
                        GAP: CRITICAL
Dim4 Interaction:       CURRENT: Mechanical-to-neutral (tween-only, no
                        spring, no reduced-motion path)
                        GAP: significant — contradicts color's warmth
Dim5 Identity Strength: CURRENT: Palette=Distinctive/Owned; icons/motion/
                        shape=Anonymous/Borrowed
                        GAP: CRITICAL — the core finding of this audit
Dim6 Emotional Register: CURRENT: Mildly charged, Serious-leaning (correct
                        for domain)
                        GAP: small

Dominant character: "Restrained warm-tech" / "warm ambient precision,
  half-realized"

Conflicting signals: The Gold/Teal/Violet/Warm Glow/Haze/Trace tokens name
  an atmospheric, glowing, deep visual world; the component layer (flat
  Surface fills, zero elevation, default icons, tween-only motion) never
  cashes that check.

Primary coherence fix candidate: Realize the ambient-glow concept the
  tokens already name — soft radial light-pool backgrounds behind key
  metric cards using the existing Haze/Trace values — since the character
  is already declared in code, just not expressed.
```

## A4. Design Character Brief — CONFIRMED BY USER

```
CHARACTER STATEMENT
  "This app's design reads as a warm light glowing in a dark room — quiet,
   confident, health-serious but not clinical. The strongest expression of
   this is the Gold/Teal/Violet glow-family palette. It must always feel
   like the accent color is the only light source in an otherwise dark,
   calm space. It currently loses that feeling the moment you look past the
   color values — at the generic icons, the flat unlit cards, and the
   purely mechanical screen transitions that could belong to any Compose
   app."

CHARACTER TESTS
  ✓ ON CHARACTER: A new accent-colored element should look like it's
    emitting light onto the dark surface around it — not just a flat fill.
  ✗ OFF CHARACTER: Drop-shadow-based Material elevation (dark shadows) —
    depth should come from light, not cast shadow.
  ✓ ON CHARACTER: New icons/custom iconography should carry warm/gold
    weight and restraint.
  ✗ OFF CHARACTER: Bright, high-saturation, "loud" moments (confetti, neon
    flashes) — established character is Quiet, not Playful.

PROTECT: OLED background 0xFF0F0D12, Gold 0xFFC9A84C at its current
  moderate chroma, the CVD-safe grade system, glassSheen()'s mechanism
  (extend coverage, don't replace it).

REJECT: Full Material3 elevation/shadow system; true heavy glassmorphism
  (blur everywhere); a colorful/expressive custom icon library.
```

This brief is the filter every subsequent finding and recommendation in
this document was tested against.

## A5. Brand Archetype

Closest fit: **The Craftsman** (warm neutrals, considered engineering,
editorial restraint) with **Companion** undertones (warmth, health-support).
Currently only the color layer sends Craftsman signals (CVD-safe grade
system, hue-consistent text tokens); icon/motion/shape send generic-utility
signals instead, creating an archetype conflict within the same product.

## A6. Anti-Genericness Audit

**Already avoided (confirmed, not asserted):** no default Tailwind-blue
equivalent; text-color stack (`TextSecondary`/`TextMuted`/`TextLabel`)
already carries the background's hue instead of neutral gray; dark
background is genuinely chromatic, not `#111827`/`#0f1117`.

```
[MEDIUM] — Generic Material iconography with zero character calibration
Finding: 100% Icons.Default/AutoMirrored.Filled, 47 distinct icons, 85 call
  sites, 28 files — zero custom modification, ad hoc sizing (12-64dp, no
  shared token).
Why it matters: Largest identity-strength gap (A3 Dim5) — palette is
  Owned, icons are Borrowed from the single most common possible source.
Recommendation: Keep Material Icons but apply consistent warm-gold tinting
  for active/selected state instead of default onSurface, and standardize
  sizing into a token (20dp inline / 24dp nav / 40dp empty-state). Full
  icon character brief in Part B3.
Effort: LOW

[MEDIUM] — Zero custom easing anywhere in the motion system
Finding: Every tween() call (AppNavGraph.kt, MainShell.kt) passes only a
  duration — no custom Easing object defined anywhere; all motion uses
  Compose's default FastOutSlowInEasing.
Why it matters: Motion currently contradicts the warmth color establishes
  (A3 Dim4). Default easing is the single most common motion signature in
  the Compose ecosystem — zero brand identity.
Recommendation: Define one custom Easing curve for the score-reveal/grade-
  ring animation — the app's "one unavoidable moment" (Part D).
Effort: LOW

[LOW] — Light theme re-derives its own primary/secondary/tertiary hex
  instead of a documented transform from shared brand tokens
Finding: LightColors.primary=0xFFA07828, secondary=0xFFB05A38, tertiary=
  0xFF1A9090 — independently authored, not derived from Gold/AccentGreen/
  Teal. A fourth gold value, LightGoldAccent=0xFF8B6914, exists separately
  for Biolism-light-mode.
Why it matters: A rebrand or accent-color change touches 3+ gold values
  instead of 1 (see Part B10's find-and-replace test).
Recommendation: Derive LightColors' primary/secondary/tertiary from Gold/
  AccentGreen/Teal via one documented OKLCH lightness-shift rule.
Effort: MEDIUM (needs visual re-check of light-mode contrast)

[LOW] — Single separator/border treatment, no heavy/light/accent taxonomy
Finding: glassSheen()'s hairline edge is the only border-like treatment,
  applied uniformly wherever used, with no weight distinction by context.
Recommendation: Define 2-3 explicit separator opacities (20%/8%/accent-30%)
  as named tokens next to the existing Colors.kt system.
Effort: LOW
```

## A7. Design DNA Specification

```
  Color DNA:     Cool-violet-tinted OLED near-black (0xFF0F0D12) lit by warm
                 gold (0xFFC9A84C) primary + coral (0xFFD97C56) secondary +
                 sparing teal (0xFF38C8C8) tertiary — deliberate warm-on-cool
                 temperature contrast, moderate (not maxed) chroma throughout
  Type DNA:      Currently: system Roboto, 400-900 weight range, hero
                 numbers in Black weight (untokenized). Target: a warm-
                 humanist swap at body/heading, hero-number role tokenized
                 at 32/42/56sp
  Shape DNA:     Currently undisciplined (9 radii in circulation). Target
                 house scale: 12dp banners/chips, 16dp cards, 20dp sheets/
                 modals — 3 values, each with a clear rule
  Space DNA:     Not yet verified (deferred — flagged as a scope gap, not
                 asserted)
  Motion DNA:    200ms tab-fade / 300ms peer-nav-slide, default easing today;
                 target: one custom easing reserved exclusively for the
                 score-reveal signature moment
  Material DNA:  Flat Surface fill + glassSheen() sheen/hairline overlay —
                 "glass-lite," no blur, no shadow-elevation; target: add
                 radial Haze/Glow atmosphere behind 1-2 focal surfaces per
                 screen, using tokens that already exist in code

  SIGNATURE ELEMENT (as shipped today): The OLED-violet background paired
    with the gold-led CVD-safe grading ring — this is the one visual a
    screenshot of Scan'eat could be identified by right now.
  SIGNATURE ELEMENT (target, once Part D lands): The score-reveal glow
    animation — the ring's arc completing while its grade-color glow
    intensifies via the Haze/Trace tokens. This would be a genuinely
    unmistakable, hard-to-copy moment, built entirely from values and
    concepts that already exist in the codebase.
```

---

# PART B — VISUAL SYSTEM FINDINGS

## B1. Color Science

```
[MEDIUM] — Accent color has no dedicated pressed/hover-equivalent state token
Finding: Gold/AccentGreen/Teal are flat values with no "-pressed" sibling;
  buttons rely entirely on Material3's default ripple (zero
  RippleTheme/LocalRippleConfiguration customization anywhere).
Recommendation: Add one AccentGreenPressed/GoldPressed token (~8% darker,
  same hue) and apply via custom ripple color on the primary-button sites.
Effort: LOW

[MEDIUM] — OLED theme's surface and surfaceVariant are identical (no
  elevation tier), while the Dark theme correctly differentiates them
Finding: OledColors.surface = OledColors.surfaceVariant = 0xFF1C1820.
  DarkColors differentiates surface (0xFF221E27) from surfaceVariant
  (0xFF322C38) — the correct pattern already exists elsewhere in the file.
Recommendation: Port the Dark theme's surface/surfaceVariant step into
  OledColors (the default, most-used theme).
Effort: LOW
STATUS: FIXED — Colors.kt now defines OledSurfaceRaw (0xFF1C1820, unchanged)
  and a distinct OledSurfaceVariantRaw (0xFF2C2631, one step lighter), mirroring
  the Dark theme's surface→surfaceVariant delta. Theme.kt's OledColors wires
  surface/surfaceVariant to the two separate tokens.

[LOW] — "AccentGreen" token name doesn't match its actual color (coral),
  and Gold exists as three unreconciled values
Finding: AccentGreen = 0xFFD97C56 (coral/terracotta). Gold exists as Gold
  (0xFFC9A84C), LightGoldAccent (0xFF8B6914), LightColors.primary
  (0xFFA07828) — three hex values for one brand hue.
Recommendation: Rename AccentGreen → AccentCoral (~28 files, mechanical);
  consolidate the three golds per Part A6's fix.
Effort: LOW (rename) / MEDIUM (gold consolidation)

[POLISH] — Score/success moments don't intensify color beyond the standard
  grade palette
Finding: gradeColor() applies identically for routine results and genuine
  milestones — no color/glow intensification for success moments.
Recommendation: Addressed properly under Part B8/Part D (states + motion),
  not a standalone color fix.
Effort: LOW

[HIGH, unverified until computed] — WCAG numeric contrast ratios have not
  been independently verified for any color pair in the app
Finding: Exact hex values exist for every text/background pairing (e.g.
  cream 0xFFEFEAE6 on OLED 0xFF0F0D12; TextSecondary 0xFF7E859E on the same
  background; FlagRed/AmberWarning banner text at 15% alpha fills), but no
  pass has computed actual contrast ratios against the WCAG 4.5:1 (normal
  text) / 3:1 (large text, UI components) thresholds.
Why it matters: The single most consequential unverified claim in the whole
  audit — a well-considered palette can still fail basic legibility if a
  specific pairing (most likely candidate: TextMuted 0xFF454A60 on OLED, or
  banner text over 15%-alpha colored fills) falls under 4.5:1. Given
  A2=High-stakes/Emotional (health/allergen data), a real failure here is a
  correctness bug, not a taste question. See Part F Chain 1 for why this
  escalates to CRITICAL specifically on the allergen/diet-veto banners.
Recommendation: Compute actual contrast ratios for the ~6 most-used text/
  background pairs before shipping any recommendation that touches these
  colors. This is a verification task, not a design task.
Effort: LOW (a calculator pass, no design change unless a failure is found)
STATUS: VERIFIED — PASS. Computed WCAG relative-luminance contrast ratios
  (standard sRGB linearization) for the actual rendered pairs, alpha-composited
  over each theme's real background — not the raw token pairs in isolation:
    - DietVetoBanner/AllergenWarningsCard body text (OnBackground on 15%-alpha
      FlagRed/AmberWarning fill, over OLED bg): 13.8:1
    - AllergenWarningsCard title (AmberWarning on its own 15%-alpha fill,
      over OLED bg): 7.7:1
    - DietVetoBanner icon (FlagRed on its own 15%-alpha fill, over OLED bg):
      4.7:1 (icon only, needs 3:1 — comfortable margin)
    - Same allergen/diet-veto pairs recomputed over Light theme's background
      (0xFFF6F1EC): 12.5:1 — also passes
    - Narrowest case found in the same file: ComparisonCard's FlagRed text on
      AccentGreen-tinted 10%-alpha fill, over OLED bg: 4.9:1 (bodySmall/12sp
      normal text, needs 4.5:1 — passes with a real but narrow margin)
  All six pairs clear WCAG AA. No color change needed. Chain 1 (Part F) is
  resolved: the escalation to CRITICAL was conditional on failure, and it
  did not fail.
```

**Protect:** OLED background's cool-violet undertone, cream (not pure-white)
primary text, zero hardcoded hex colors leaking outside the theme layer.

## B2. Typography

```
[MEDIUM] — Base typeface is unmodified system Roboto, in tension with the
  "warm" character established by color
Finding: No fontFamily set anywhere in ScanEatTypography (Type.kt); every
  slot defaults to platform Roboto (neutral Grotesque/Geometric hybrid).
Recommendation: A single warm-humanist swap (Plus Jakarta Sans or Manrope,
  both bundlable) for body+heading.
Effort: MEDIUM (font bundling + visual re-check across 15 type slots)

[HIGH] — Zero tabular-figure treatment anywhere numbers are displayed
Finding: Confirmed zero "tabular"/FontFeatureSettings/font-variant-numeric
  usage anywhere. Scores, nutrition values, weight entries, calorie totals
  all render with Roboto's default proportional figures.
Recommendation: Apply `TextStyle(fontFeatureSettings = "tnum")` to every
  numeric display — NutritionTable.kt, weight-history rows, dashboard macro
  cards, ScoreDisplay.
Effort: LOW (one modifier, ~6-8 call sites)

[MEDIUM] — "Hero number" role has no shared token — 4 weight spellings, 5+
  concrete sizes
Finding: KetosisProcessCard (24sp/Medium), CalorieBalanceCard (32sp/Black),
  DailyEnergyCard (34sp/W500), HeroCard (42sp/W500), ScoreDisplay (untethered
  raw 56sp, no base at all).
Recommendation: Add one ScanEatTypography.heroNumber style (canonical
  FontWeight.Black) and let screens vary only fontSize from a defined
  32/42/56sp scale.
Effort: LOW (one shared style + ~6 call-site swaps)

[LOW] — 3 of 15 M3 type slots (displayLarge, displayMedium, headlineSmall)
  were never hand-tuned
Finding: Type.kt only overrides 11/15 slots; the untouched 3 silently use
  M3's un-tuned defaults.
Recommendation: Hand-tune the remaining 3 to match the rest, or confirm
  they're unused and drop them from the "available slots" mental model.
Effort: LOW

[POLISH] — No letter-spacing/tracking adjustment on any of the 15 type slots
Finding: Every TextStyle override leaves letterSpacing at default (0.sp),
  including labelSmall (11sp), which should carry +0.03-0.06em at that size.
Recommendation: +0.02em on titleLarge/headlineMedium; +0.04em on
  labelSmall/labelMedium.
Effort: LOW
```

**Protect:** the hero-number scale-contrast instinct (56sp vs 12sp body) is
correct compositionally — it needs a token, not a redesign. OpenDyslexic
accessibility swap is real, considered craft.

## B3. Iconography

```
[MEDIUM] — Icon expressiveness sits at "Utilitarian," below what the
  confirmed character (Craftsman/warm-glow) calls for
Finding: 100% default Material Icons, zero weight/style customization —
  squarely "Utilitarian" tier. Given A5=Amplifies value and the confirmed
  Craftsman-leaning character, "Calibrated" is the appropriate target
  (library base + weight/tint matched to character) — not a full custom
  icon system, which would be disproportionate effort for a non-revenue,
  domain-conventional app.
Effort: LOW-MEDIUM
```

```
ICON CHARACTER BRIEF
  Target expressiveness: Calibrated (not Signature/Illustrative)
  Grid:             Keep Material's native 24×24dp grid — no change needed
  Stroke/fill:       Keep filled style (already 100% consistent, a real
                       strength) — do not introduce Outlined/Rounded mixing
  Corner treatment:  No change — inherits from Material's filled glyphs
  Tint strategy:     Default/inactive icons stay OnBackground.copy(0.5-0.7)
                       (current pattern, keep); active/selected icons (nav,
                       toggled states) switch to Gold (0xFFC9A84C) instead —
                       the one calibration that actually connects icons to
                       the brand palette
  Size token:        Standardize the currently-ad-hoc 12-64dp range into
                       3 named sizes: 20dp (inline/label-adjacent), 24dp
                       (nav bar, matches Material's native grid), 40dp
                       (empty states, matches EmptyListState.kt's existing
                       value — don't change that one)
  What icons must avoid: Any move toward a second icon library, outlined/
    filled mixing, or expressive multi-color icon treatments (would violate
    the confirmed "Quiet, not Playful" register)
```

## B4. Components (Buttons, Cards, Navigation, Inputs, Modals)

```
[HIGH] — No shared Button component; the primary-button recipe is hand-
  copied inline across ~8+ files, with visible drift
Finding: `ButtonDefaults.buttonColors(containerColor = AccentGreen)` + black
  label + `RoundedCornerShape(12.dp)` repeated verbatim across Onboarding/
  Settings/Scan/LogSheet/Fasting screens — but ScanScreen's permission button
  and FastingScreen's primary button omit `shape`, silently reverting to
  M3's default pill shape.
Why it matters: Most-seen interactive element in the app; a coherence bug,
  not a taste question. The find-and-replace test (Part B10) currently
  fails here — changing the primary button means editing 8+ files by hand.
Recommendation: Extract a `ScanEatPrimaryButton` composable once, in
  presentation/ui/theme/, alongside EmptyListState.kt and
  scanEatTextFieldColors() (both already prove this pattern works).
Effort: MEDIUM (extraction + swap ~10 call sites)

[MEDIUM] — Every "card" is a hand-rolled Surface; radius/color/glassSheen
  presence all vary per screen with no shared component
Finding: No M3 Card/ElevatedCard/OutlinedCard used anywhere. Biolism shares
  one BioCard() wrapper; Dashboard/Result/Diary/Settings each hand-roll
  Surface(...) independently (12/14/16/18/20dp radius scatter), and
  glassSheen() coverage is inconsistent (present on Dashboard/Result/Diary/
  History/Recipes/Templates/Weight/Grocery/Hydration; absent on Biolism
  tracker/Onboarding/Settings/RemindersCard).
Why it matters: glassSheen() is the app's single most distinctive surface
  treatment — its inconsistent application means the signature finish is a
  coin flip per screen.
Recommendation: Generalize BioCard()'s pattern into one ScanEatCard()
  primitive app-wide, glassSheen() on by default; standardize on 16dp for
  cards, reserve 12dp for banners/chips.
Effort: MEDIUM-HIGH (touches ~15+ files, each change mechanical)

[LOW] — Navigation tab-switch fade (200ms) has no distinctive character;
  bottom-nav active-state tinting unconfirmed
Finding: MainShell.kt's NavigationBar show/hide uses bare fadeIn()/
  fadeOut(). [UNVERIFIED]: whether the bottom nav's selected-tab treatment
  uses Gold tinting or M3 defaults — needs a direct read of MainShell.kt/
  TopTab.kt's NavigationBarItem call before treating as confirmed.
Recommendation: Verify selectedIconColor/selectedTextColor args; set to
  Gold if defaulted.
Effort: LOW (verify first)

[LOOKS FINE] — Input fields and modals/sheets
Finding: scanEatTextFieldColors() confirmed shared across ~7 screens with
  AccentGreen focus color. LogSheet.kt's bottom sheet uses a distinct 20dp
  top-corner radius — a defensible modal-tier distinction, not an
  inconsistency.
Recommendation: No fix needed.
```

## B5. Motion

```
[HIGH] — The app's "one unavoidable moment" (the score reveal) uses no
  distinctive motion — driven by the same generic default as everything else
Finding: No animateFloatAsState/spring/Crossfade/updateTransition exists
  anywhere in the codebase (confirmed, full-codebase grep). The score ring
  (ScoreDisplay.kt) — the single most important value-delivery moment —
  appears to render its final state without any distinct reveal animation.
Why it matters: Every product has one moment deserving maximum character
  investment; this app's gets zero motion investment currently.
Recommendation: Add a signature reveal — the score ring's progress arc
  animates in via animateFloatAsState with a custom (non-default) easing
  over ~600-800ms, paired with the grade-color glow (from Part B6)
  intensifying as the arc completes. Full synthesis in Part D.
Effort: MEDIUM (isolated to one composable)

[MEDIUM] — Zero reduced-motion accessibility handling anywhere
Finding: Confirmed zero matches for ANIMATOR_DURATION_SCALE or any
  accessibility-driven motion-disable path anywhere in the codebase or
  Settings UI.
Why it matters: A real accessibility gap, not just a character question.
  See Part F Chain 2 — this must be fixed together with, not after, the
  score-reveal animation above.
Recommendation: Read Settings.Global.ANIMATOR_DURATION_SCALE once and gate
  the new score-reveal animation (and existing nav transitions) behind it.
Effort: LOW-MEDIUM

[LOW, MOSTLY PROTECT] — Screen-transition timing is serviceable but generic
  easing; the tab-switch/peer-navigation duration split itself is fine
Finding: AppNavGraph.kt's tab-switch (200ms fade) vs peer-navigation (300ms
  slide+fade) split is a reasonable, intentional-feeling structure — only
  the easing curve is generic (already covered under Part A6).
Recommendation: No new fix beyond the custom-easing recommendation already
  given; don't touch the 200/300ms split itself.
Effort: N/A
```

## B6. Surface & Atmosphere

```
[CRITICAL] — The "ambient glow" color tokens (Glow/Haze/Trace families) are
  defined but not confirmed to be rendered as actual light — the app's
  material language is Flat while its color tokens promise Deep
Finding: Colors.kt defines 15+ dedicated alpha-variant tokens (GoldGlow/
  GoldBorder/GoldHaze/GoldTrace, TealGlow/TealBorder/TealHaze/TealTrace,
  VioletGlow/VioletBorder/VioletHaze/VioletTrace, WarmGlow/WarmHaze,
  DangerGlow, MetaGreenHaze) whose names describe emitted/ambient light.
  Evidence gathered so far shows only flat Surface fills + glassSheen()'s
  top-light sheen; no Brush.radialGradient() or equivalent "light pool"
  rendering confirmed. [UNVERIFIED — needs a direct grep for
  "radialGradient|GoldHaze|TealGlow|VioletGlow" usage before treating this
  as "tokens exist but are unused" rather than "not yet found"].
Why it matters: The single highest-leverage fix in the audit — if unused,
  realizing these tokens closes Dimension 3 and Dimension 5 (Part A3)
  simultaneously, using values that already exist (zero new design
  decisions needed).
Recommendation: Verify usage first, then apply a soft
  `Brush.radialGradient()` using the Haze token (lowest intensity) as a
  background-atmosphere layer behind 1-2 "focal" surfaces per screen (Result
  screen's score ring, Dashboard's primary metric card), reserving the
  brighter Glow token for the single most important element only —
  explicitly not uniform across every card (the most common atmosphere-
  hierarchy violation).
Effort: MEDIUM (verify-then-implement; values already exist)

[MEDIUM] — No established light source direction; the app's one shadow
  (badge, 6dp) and glassSheen()'s "top-light" sheen aren't confirmed
  consistent with each other
Finding: glassSheen()'s doc comment describes a top-light gradient; the one
  shadowElevation usage (streak badge) uses Compose's default (also
  top-down), but the two haven't been verified against each other in the
  same file.
Recommendation: Declare "top-center, soft, warm" as the app's one documented
  light source, noted once near the Colors.kt/Glass.kt token definitions.
Effort: LOW

[LOW] — No grain/noise texture layer exists anywhere
Finding: No noise/grain overlay found in any surface treatment.
Recommendation: Low priority relative to the Glow/Haze finding; revisit
  only after the atmosphere-hierarchy fix lands, ~3% opacity on the OLED
  background only (not cards).
Effort: LOW, but LOW priority
```

## B7. Hierarchy & Gestalt

```
[PROTECT, NO FIX NEEDED] — Score ring correctly claims primary visual weight
  on the Result screen
Finding: ScoreRing/DualScoreRing (178dp/110dp, largest element, highest-
  saturation color via gradeColor(), centered, isolated) is unambiguously
  the correct focal point.
Recommendation: Use as the reference pattern for "what a focal moment looks
  like" when extending atmosphere elsewhere (Part B6).

[MEDIUM] — Chroma contrast (one saturated element in a desaturated field) is
  under-used outside the score ring
Finding: Dashboard cards each carry their own colored accent simultaneously
  (AccentGreen streak badge, HydrationBlue, CalorieOrange, MetaGreen) rather
  than one element per screen being the sole chroma-contrast focal point.
Why it matters: When every card is colorful, none reads as more important —
  the isolation principle that makes the Result screen work isn't available
  on data-dense dashboard screens.
Recommendation: Pick one Dashboard metric (today's score or streak) to carry
  full accent saturation; desaturate other card accents toward the existing
  Haze/Trace muted tokens.
Effort: MEDIUM (needs a visual check, not blind)

[SCOPE GAP] — Reading-flow and full Gestalt proximity/similarity require
  direct layout inspection not yet done
Recommendation: Defer to a follow-up pass reading DashboardScreen.kt/
  DiaryScreen.kt's actual Column/Row structure directly.
```

## B8. State Design (Empty, Loading, Error, Success)

```
[HIGH] — Error states use three unreconciled color/component systems across
  the app, none matching the confirmed "warm glow" character
Finding: (1) ScanScreen's real-error case uses Material's colorScheme.error/
  errorContainer tokens (the only place in the app using them at all); (2)
  ResultBanners' diet-veto and allergen cards use custom FlagRed/
  AmberWarning at 15% alpha; (3) SettingsScreen's backup error is a bare
  colored Text with no container, icon, or structure at all — the most
  minimal treatment found anywhere in the app.
Why it matters: "The error must feel like this product's error" — right
  now it feels like three different products' errors depending on which
  screen you're on. Given A2=High-stakes/Emotional, allergen/diet-safety
  errors specifically deserve the most consistent, trustworthy treatment in
  the whole app, not the most fragmented one. See Part F Chain 5 — the
  SettingsScreen case specifically is also a §G1 accessibility finding
  (status conveyed by color alone, no icon), not just a polish gap.
Recommendation: Standardize on the custom FlagRed/AmberWarning system
  (option 2) as the house error language — it's already palette-integrated
  and nearly matches the "warm, restrained" character (unlike Material's
  generic colorScheme.error). Build one shared ErrorBanner composable with
  icon+text+optional-dismiss, and migrate ScanScreen's real-error case and
  SettingsScreen's bare-text error onto it.
Effort: MEDIUM (one new shared composable + 2-3 call-site migrations)

[MEDIUM] — Empty states are correctly shared (EmptyListState.kt) but
  minimal — icon + text only, no CTA
Finding: EmptyListState.kt (40dp icon at 50% opacity + message text) is used
  consistently across Recipes/Templates/CustomFood — a genuine, deliberate
  shared component (its own doc comment confirms this was extracted from
  3 duplicated inline versions).
Why it matters: An empty state should contain a character-positive visual
  element + explanation + primary action — this one has only the first two.
  It's consistent (good), but at the floor of what's considered complete.
Recommendation: Add a primary action slot (optional param, only used where
  a clear next step exists — e.g. "Add a recipe" CTA on RecipesScreen's
  empty state) rather than redesigning the whole component; keep the
  icon/text pattern, appropriately restrained for a "Quiet" character.
Effort: LOW (additive param, no visual regression risk)

[MEDIUM] — Loading states have no shared skeleton pattern; scores/data-viz
  bars reuse plain progress indicators with no character-specific styling
Finding: The same Material progress primitives serve both true async-
  loading (ScanScreen's in-FAB spinner, ResultScreen's whole-screen spinner)
  and static data-viz rings/bars (ScoreDisplay's grade ring,
  CalorieBalanceCard's macro bar) — styled ad hoc per file, with zero
  skeleton-style "shape of what's coming" treatment anywhere.
Recommendation: For ResultScreen's whole-screen loading spinner (the wait
  immediately before the score reveal) — standardize its color/stroke to
  match the new score-reveal motion signature (Part B5/D), so loading
  visually sets up the reveal rather than being generic.
Effort: LOW (one call site, follows from the Part B5 fix)

[POLISH] — No milestone/success-state intensification exists anywhere
Finding: Grade colors and layout are identical whether it's a routine scan
  or a genuine first-A+/streak-milestone moment.
Recommendation: This is the same "one unavoidable moment" finding as Part
  B5, viewed from the states lens rather than the motion lens — see Part D,
  don't build two separate fixes for one moment.
Effort: N/A (folds into the Part D recommendation)
```

## B9. Copy × Visual Alignment

```
[LOW] — Copy voice is competent but "invisible," slightly under the warmth
  the confirmed character calls for
Finding: Sampled copy (e.g. "Non végan : ...", "URL du serveur non
  configurée — configurez-la dans Réglages") sits at Formal-to-neutral,
  Terse, and Impersonal-to-neutral on the voice dimensions — functionally
  correct and clear, but doesn't lean toward "Voiced"/"Personal" the way
  the confirmed "warm, quiet-confident" character would suggest.
Why it matters: A genuinely low-cost, low-risk finding — copy tone is one
  of the cheapest character levers available.
Recommendation: Not a wholesale rewrite — targeted warmth at the highest-
  visibility moments only: the score-reveal verdict text and milestone
  messages are worth a warmer pass; routine error/validation copy can stay
  terse-and-clear as-is (appropriate for a health-safety context —
  over-warming an allergen warning would be a mistake, not an improvement).
Effort: LOW (copy-only changes at ~2-3 highest-visibility strings)

[UNVERIFIED] — Full microcopy audit (button labels, field labels, tooltip
  text) not systematically sampled in this pass
Recommendation: Defer to a follow-up read of button/label strings.xml
  entries specifically, rather than assert findings from an incidental
  sample.
```

## B10. Design Token Architecture

```
[MEDIUM] — Token architecture reaches Layer 2 (semantic) solidly but almost
  never Layer 3 (component); the "find-and-replace" test fails concretely
  for the accent color
Finding: Layer 1 (primitives — Gold/AccentGreen/Teal/etc. in Colors.kt) and
  Layer 2 (semantic — MaterialTheme.colorScheme mapping in Theme.kt) are
  both genuinely present and reasonably disciplined. Layer 3 (component
  tokens) barely exists: `scanEatTextFieldColors()` is the one real example;
  buttons and cards have no equivalent, so they consume Layer 1 primitives
  directly (`AccentGreen` imported and referenced at ~28 files) rather than
  through a component-scoped token.
Why it matters: This is the architectural root cause behind three separate
  findings raised elsewhere (Part B4 button/card drift, Part B1's
  "AccentGreen" naming risk) — see Part F Chain 4 for the full sequencing
  argument (build components first, then rename/consolidate).
Recommendation: Building `ScanEatPrimaryButton`/`ScanEatCard` (Part B4) IS
  the Layer 3 fix — once those exist, the accent color only needs to change
  in 2 places instead of ~28. No separate action needed.
Effort: N/A (already scoped under Part B4)

CHARACTER TOKEN AUDIT
  ☑ Background surface lightness step — tokenized in Dark scheme; NOT
    tokenized/absent in OLED scheme (Part B1)
  ☒ Primary accent hex — tokenized (Layer 1/2), but not OKLCH-verified for
    perceptual consistency across the palette (Part B11)
  ☒ Component border-radius — hardcoded per call site, no shared scale
    (9 distinct values in circulation)
  ☑ Typography scale — tokenized via Type.kt (11/15 slots hand-tuned)
  ☒ Transition durations — hardcoded per animation call, no named constants
  ☒ Shadow definitions — barely exist (one hardcoded 6dp value), not
    tokenized
  ☑ Focus ring style — tokenized for text fields via
    scanEatTextFieldColors(); NOT tokenized for buttons
  ☐ Spacing base unit — unverified, deferred to Part B7 follow-up

[MEDIUM, Phase 2 synthesis] — Accessibility properties (focus ring style,
  minimum touch target, contrast-safe pairings) aren't encoded as tokens
  anywhere — they're either absent or ad hoc per component
Finding: Synthesizing three findings made separately in this audit — no
  focus-ring-style token exists, no minimum-touch-target dimension token
  exists (the 32/36dp IconButton sites in Part E have no shared
  `@dimen/min_touch_target` to violate), and no contrast-safe color-pairing
  documentation exists (Part B1's WCAG gap).
Why it matters: This is the connective root cause behind three separate
  findings — they're not three unrelated gaps, they're one missing layer
  (accessibility-as-tokens) showing up three times.
Recommendation: When building the Layer 3 component tokens (Part B4),
  bake in a `MIN_TOUCH_TARGET = 48.dp` constant and a documented focus-ring
  color (Gold at full opacity, 2dp) at the same time — this turns three
  separate follow-up tasks into one.
Effort: LOW (documentation + constants, enforced alongside the already-
  planned component extraction)

[LOW] — No design-system documentation exists anywhere in the codebase
Finding: Colors.kt/Theme.kt/Type.kt have good inline comments explaining
  individual decisions, but there's no single document describing the
  design system as a whole.
Recommendation: This audit document now serves that purpose — keep it
  updated as fixes land, rather than starting a second design-system doc.
Effort: N/A
```

## B11. Trend Calibration

```
[PROTECT — no fix needed] — The app's trend posture is Timeless-leaning,
  which is correct for its axis profile
Finding: Checked against the full trend inventory — no bento-grid layout,
  no AI gradient mesh, no dot-grid background, no 3D elements, no variable-
  font weight animation, no brutalist typography anywhere. Dark-mode-as-
  default is present, but that's baseline-expected in 2024+, not a trend
  chase. The "glass" attempt (glassSheen()) notably is NOT the mainstream
  backdrop-blur trend — a custom sheen-only approach, meaning the app isn't
  even copying the current glass trend, it built its own adjacent thing.
Why it matters: "Timeless" posture is correct for institutional/long-
  lifecycle/trust-dependent products — a health-scoring app chasing "AI
  startup" visual signals would actively undermine the credibility
  A2=High-stakes/Emotional demands.
Recommendation: None. Don't introduce trend-chasing elements to "modernize"
  — the current restraint is on-character.

[POLISH] — Colors are authored as raw hex rather than a perceptually-uniform
  space, which is a documentation/precision gap more than a visible one
Finding: All color tokens are `Color(0xFF...)` hex literals; no OKLCH
  authoring or equivalent documentation exists for how the palette's
  lightness/chroma steps were chosen.
Why it matters: Lowest priority in this section — Android has no native
  OKLCH color type. Matters only for future palette work (deriving the
  light-mode golds, Part A6) — doing that derivation in OKLCH terms would
  prevent the next "three unreconciled golds" problem.
Recommendation: When executing the light-mode gold consolidation, do the
  derivation math in OKLCH and only convert to hex at the end.
Effort: LOW (process change, not a standalone fix)
```

## B12. Sections Skipped or Folded (Illustration, Data Viz, Responsive)

```
Illustration & Graphic Language — SKIPPED: no custom illustrations, spot
  graphics, or illustrated empty-state compositions exist anywhere in the
  app (EmptyListState.kt uses a Material icon, not an illustration; no
  illustration assets in res/drawable/ beyond launcher/notification icons).

Data Visualization Character — FOLDED, not a standalone gap: the app does
  use progress-ring/bar data visualization (ScoreDisplay's grade rings,
  dashboard macro bars, Biolism's LinearProgressIndicator usages), but
  they're built from Material's progress-indicator primitives, already
  covered under Part B8 (Loading State Design) and Part B7 (the score
  ring's correct focal-weight treatment) — a standalone section here would
  duplicate rather than add evidence.

Responsive Design Character — SKIPPED: Scan'eat is a phone-primary Compose
  app. No WindowSizeClass usage, no layout-sw600dp/layout-w840dp resource
  qualifiers, and no foldable-aware layout logic were found — a single
  fixed-form-factor (phone portrait) layout with no responsive/tablet/
  foldable adaptation built yet. (Tablet/foldable support is a real
  absence, but it's a scope/roadmap decision, not a design-character fix.)
```

---

# PART C — COMPETITIVE VISUAL POSITIONING

Web search confirmed detail for Yuka (its light-background, color-coded
0-100 score identity is well-documented); Cronometer and Open Food Facts
didn't return fetchable visual specifics, so those two are marked
`[UNVERIFIED beyond general knowledge]` rather than asserted as confirmed.

```
Product A: Yuka
  Visual character:    Light, white/pale-background app built around a
                        single 0-100 numeric score that resolves to a
                        green/yellow/red color band — mass-market clarity
                        over atmosphere. [WEB-confirmed: white/light
                        background, green-for-good/red-for-bad Nutri-Score-
                        derived color coding, 76M+ users as of Oct 2025.]
  Accent / palette:    Green (good) / yellow (moderate) / red (poor) — the
                        same traffic-light convention Scan'eat's grade
                        system also uses, but on a light background instead
                        of dark.
  Signature element:   The 0-100 score number itself, at large scale, is
                        the whole identity.
  Genericness score:   2/5 — the traffic-light convention is now a category
                        standard, but Yuka owns the "big number + light
                        background" execution at mass-market scale.
  Relation to this app: Yuka does NOT occupy the dark/atmospheric/premium
                        territory Scan'eat is reaching for — confirmed
                        whitespace, not a guess.

Product B: Cronometer [UNVERIFIED beyond general knowledge]
  Visual character:    Widely known as a light-mode-default, data-table-
                        dense tracker prioritizing full nutrient panels over
                        a single score — utility-first, not atmosphere-first.
  Relation to this app: If accurate, occupies a "quantitative depth, low
                        visual investment" position distinct from both Yuka
                        and Scan'eat.

Product C: Open Food Facts (official app) [UNVERIFIED beyond general
  knowledge]
  Visual character:    Open-source, basic Material Design conventions,
                        green branding, minimal custom visual investment.
  Relation to this app: Likely least visually invested of the three;
                        low competitive threat on aesthetics, strong threat
                        on data breadth (outside this audit's scope).

WHITESPACE OPPORTUNITY
  Available position: "Dark, warm, atmospheric premium" in a category where
    every major visible competitor (at minimum, Yuka) defaults to light-
    background, primary-color-forward, mass-market clarity.
  Why it's available: Nutrition-scoring apps optimize for instant in-store
    legibility — light backgrounds serve that well, so competitors have
    little reason to invest in a dark/atmospheric identity. Scan'eat's
    broader scope (Diary, Biolism, weight tracking, meal planning) means
    it's used in more contexts than "standing in a store aisle."
  Risk: Dark UI can read as harder to use in bright daylight — the exact
    context barcode-scanning happens in. Worth verifying the scan camera
    screen remains legible in direct sunlight regardless of theme.
  Claim strategy: (1) Ship the score-reveal glow moment (Part D) as the
    single most memorable differentiator; (2) lean into the metabolic/
    Biolism depth as a visual extension of "considered"; (3) keep the
    OLED-gold identity consistent across every touchpoint.

Positioning Matrix (axes: Clinical/data-dense ←→ Warm/atmospheric ×
  Mass-market simplicity ←→ Considered depth):
    Yuka:              Mass-market simple, Clinical-neutral
    Cronometer:         Considered depth, Clinical
    Open Food Facts:    Mass-market simple, Clinical
    Scan'eat (current): Considered depth, pulled toward Clinical by the
                        generic-icon/flat-surface execution gaps
    Scan'eat (target):  Considered depth AND Warm/atmospheric — the one
                        quadrant none of the three benchmarks occupy

Visual Differentiation Opportunities:
  1. Dark, warm, "glowing" identity vs. the category's light/score-forward
     convention — palette exists and is distinctive, execution doesn't yet
     deliver (Effort: Medium, mostly already-scoped fixes)
  2. A genuine "moment of delivery" (score reveal) with its own motion
     signature — currently absent, target is Part D's recommendation
     (Effort: Medium)
```

---

# PART D — CHARACTER DEEPENING PROTOCOL

The synthesis pass: given the confirmed character (Part A4), what's the
concrete plan to make it more concentrated, consistent, and unmistakable.

```
1. CHARACTER TOKEN EXTRACTION
   EXPRESSES the "warm glow" character correctly (protect these):
     - Background 0xFF0F0D12 (cool-violet near-black, not neutral gray)
     - Gold 0xFFC9A84C at its current moderate (not maxed) chroma
     - The Glow/Haze/Trace alpha-family token structure itself (even if
       under-rendered today — the naming convention is correct)
     - glassSheen()'s top-light sheen mechanism
   UNDERMINES the character (replace/fix):
     - System Roboto with zero fontFamily customization
     - Default FastOutSlowInEasing everywhere
     - 100% unmodified Material Icons
     - Flat Surface fills with no radial-glow atmosphere

2. CHARACTER STRESS TEST SCORECARD
     Error states:    FAILS — 3 unreconciled systems (Part B8)
     Empty states:    PASSES (minimally) — consistent, restrained, correct
                       tier for the character (Part B8)
     Loading states:  FAILS — generic Material spinners, no character
                       treatment (Part B8)
     Edge-case/admin: SettingsScreen's bare-text error is the single
                       weakest character moment found in the entire audit
     Mobile breakpoint: N/A — phone-only app, no breakpoint to stress-test

3. SENSORY VOCABULARY
   Sensory reference: "An ember glowing quietly in a dark hearth."
   Design implications:
     - Light should look genuinely emanating (radial glow), not a flat
       fill (Part B6)
     - The glow stays modest and warm — never blazing/neon (matches the
       REJECT rule against "loud/Playful" treatments)
     - Elements away from the "ember" (secondary chrome, dividers,
       disabled states) should recede toward near-darkness, not compete
       (Part B7's chroma-contrast finding)
     - Material should read as warm despite being dark — avoid cold-
       clinical cues (harsh drop-shadows, neutral-gray dividers, pure-white
       light-theme surface)

4. CHARACTER HIERARCHY
   PRIMARY CARRIERS (max investment): the score/grade reveal (Result
     screen) — the "one unavoidable moment," currently under-invested;
     Dashboard's primary metric card
   SECONDARY CARRIERS: navigation bar, buttons, cards generally, diary/
     history rows
   BACKGROUND ELEMENTS: dividers, timestamps, disabled states, settings
     toggles — currently mostly Material-default, character-neutral

5. THE ONE UNAVOIDABLE MOMENT
   Identified: the score/grade reveal on the Result screen — the moment a
   user gets what they opened the app for. Currently renders instantly,
   same generic default motion as everything else, no color/glow
   intensification. This is the highest-leverage fix in the entire audit —
   implementing it closes four separate findings at once (Part B5's motion
   gap, Part B8's success-state gap, Part B1's color-intensification gap,
   and part of Part B6's atmosphere gap).

6. CHARACTER-NEUTRAL AUDIT
   Found: dividers/separators (just glassSheen()'s hairline, no weight
   taxonomy — Part A6); disabled button states (100% Material default
   dimming); [UNVERIFIED] settings toggle switches (not confirmed whether
   Switch colors are customized to Gold or left at M3 default). For each:
   the minimal fix is the same pattern already established elsewhere
   (tint toward Gold/coral at low intensity), not a new visual language.

7. CHARACTER FUTURE-PROOFING
   Rules for all new features:
     1. Every new accent-colored element emits light via the existing
        Haze/Trace tokens — never a flat saturated fill.
     2. Every new numeric display uses tabular figures from day one.
     3. Every new card/surface goes through the shared card component
        (Part B4) — never a fresh hand-rolled Surface().
     4. New motion stays within the established 200ms/300ms split, EXCEPT
        the one signature moment (score reveal).
     5. Streak/engagement mechanics never pair with loss-framed or urgent
        copy — a broken streak resets quietly, without punitive messaging
        (see Part E, Category F).
   Risks to watch: Settings/admin-style screens are the classic place
     character reverts to defaults — SettingsScreen's bare-text error is
     already the weakest moment found; any future external surface (Play
     Store listing, marketing site) needs to inherit this exact palette.
```

---

# PART E — PHASE 2: EXPANDED UI AUDIT

Pulls in the UI-adjacent sections from app-audit-SKILL.md. Where an item
substantially overlaps a Part A-D finding, this cross-references rather than
duplicates it.

## Category E — Visual Design Quality & Polish

Design Token System, Visual Rhythm, Color Craft, Typography Craft,
Interaction Design, Overall Professionalism, and Visual Identity are all
covered by Parts A/B/C/D above — see the Master Findings Table for direct
pointers. New findings from this category's structural/code lens:

```
[HIGH] — WCAG numeric contrast ratios have not been independently verified
  (restated from Part B1 under its correct compliance category)
See Part B1 for the full finding — this is the single highest-priority
  open verification item in the whole audit.

[MEDIUM] — Material You / Dynamic Color not used; static palette confirmed
  high-quality, so this is a deliberate, defensible choice — not a gap
Finding: No DynamicColors API usage anywhere; all three theme schemes
  (OLED/Dark/Light) are hand-authored static palettes.
Why it matters: The confirmed Character Brief explicitly wants a specific
  warm-gold identity, not a user-wallpaper-derived one — Dynamic Color
  would directly undermine the "owned" identity this audit is trying to
  strengthen.
Recommendation: None — protect this decision, don't add Dynamic Color.

[LOW] — Splash screen theme hardcoded black regardless of in-app theme
  choice (same root cause as the system-chrome finding, Part A1)
Finding: `Theme.ScanEat.Starting` (themes.xml) is hardcoded black, same as
  the main theme's native Android layer — system chrome doesn't track the
  in-app light/dark/OLED picker (Compose-only theming).
Recommendation: Fold into the same future fix as the system-chrome finding
  rather than fixing splash alone.
Effort: LOW-MEDIUM (real Android lifecycle constraints — scope carefully)

[UNVERIFIED] — RTL layout quality not re-audited this session (added
  earlier in this project's history; no regression assumed, not re-tested)
```

## Category F — UX, Information Architecture & Copy

```
[PROTECT] — Information architecture has already been through deliberate
  rework this project's history (logging consolidated into Journal,
  Reminders relocated, Profile placement reversed per considered decisions)
  — not a gap.

[LOW] — Scan error recovery is dismiss-only, no explicit distinct "retry"
  action
Finding: ScanViewModel.kt's `dismissError()` sets state directly to Idle —
  no separate retry action; the user must manually re-aim/re-tap.
Why it matters: Kept LOW deliberately — a barcode scan is a single-step
  action, unlike a multi-step form where losing progress is costly.
Recommendation: If addressed, offer a "Réessayer" action alongside dismiss
  when the error is retryable (network timeout) vs. dismiss-only when it
  isn't (product not found).
Effort: LOW

[LOW, MOSTLY PROTECT] — One retention mechanic (the Dashboard streak
  badge) exists; tonally appropriate, but worth a standing rule so it
  doesn't drift toward pressure patterns later
Finding: CalorieBalanceCard's streak badge is the one engagement mechanic
  in the app. Reminders are opt-in with gentle, non-urgent copy — no
  FOMO/guilt language found anywhere.
Recommendation: No code change — add to Part D's Future-Proofing rules:
  streak mechanics never pair with loss-framed or urgent copy.
```

## Category G — Accessibility

```
[PROTECT — verified this session] — Icon-only buttons consistently carry
  real contentDescription strings, not null/decorative
Finding: Spot-checked across CustomFoodScreen, DiaryScreen,
  ScanHistoryScreen, MealPlanScreen — every sampled icon-only button passes
  a real stringResource, not null. Reflects this project's own prior
  accessibility pass.
Recommendation: Maintain the pattern as new icon-only buttons are added.

[MEDIUM] — Zero reduced-motion accommodation anywhere (restated from Part
  B5 under its correct accessibility-compliance category)
See Part B5 / Part F Chain 2 for the full finding and sequencing
  requirement with the score-reveal animation.

[PROTECT] — No frosted-glass/blur effects exist, so "reduced transparency"
  doesn't apply — glassSheen() uses alpha gradients, not real blur.

[DEFERRED — requires live-device verification] — Screen Reader Trace
  (TalkBack) and Keyboard/Switch Access require actually running the app
  with assistive tech active; this sandbox has no way to do that. A real
  device/emulator TalkBack trace of the core scan→result flow would be the
  highest-value next accessibility action beyond what this audit can verify.
```

## Category H3 — Mobile & Touch

```
[MEDIUM, re-confirmed] — 12+ IconButtons across dense list rows sit at
  32-36dp, below the 48×48dp Android touch-target guideline
Finding: Re-verified via fresh grep: `Modifier.size(32.dp)` at
  ActivityScreen.kt:108, CustomFoodScreen.kt:186, DiaryScreen.kt:280,
  ScanHistoryScreen.kt:119, MealPlanScreen.kt:109/112/135/140 (×4),
  ScanScreen.kt:237/249 (×2); `Modifier.size(36.dp)` at
  RecipesScreen.kt:87/88 (×2), TemplatesScreen.kt:66 — all delete/dismiss/
  log/edit IconButtons in dense list rows. Matches a finding already queued
  much earlier in this project's history (deliberate density choice
  pending visual verification, not yet fixed).
Why it matters: A real, measurable guideline gap — but correctly deferred
  once already, since a blanket resize touches many list layouts and risks
  breaking row-height rhythm. See Part F Chain 3 for why this does NOT
  escalate to a real risk (the delete confirmation dialog already breaks
  the chain).
Recommendation: No change — the earlier deferral (visual verification
  needed first) still holds.
Effort: MEDIUM (touches ~12 sites, one-line size changes; effort is in the
  visual verification, not the edit)

[UNVERIFIED] — Touch target spacing, thumb-zone ergonomics, orientation
  handling require a running layout inspector, not source reads alone.
```

## Category L3-L5 — Design System / Copy / Interaction Polish

Fully covered by Parts A6/B9/B10/D above — the token consolidation plan,
component variant audit, and interaction-polish checklist are the same
findings already made there. One new synthesis:

```
[MEDIUM] — Accessibility-as-tokens synthesis (restated from Part B10)
See Part B10 — the missing focus-ring/touch-target/contrast tokens are one
  root cause showing up three times, not three separate gaps.

[LOW] — Craft-implementation-checklist baseline is only partially met
Finding: Checked the specific universal baseline items: styled focus rings
  (not customized), tabular-nums (absent, Part B2 HIGH), press-state
  scale/depth feedback (not confirmed beyond default ripple), skeleton
  loaders (absent, Part B8). The CVD-safe grade system and glassSheen()
  both qualify as "at least one detail that took extra effort," so the
  baseline is met, but narrowly.
Recommendation: No new recommendation — every item here already has its
  own finding and fix elsewhere; listed to confirm the checklist was
  actually run, not skipped.
```

## Category D5 — Mobile-Specific Performance

```
[PROTECT — verified this session] — Several mobile-performance disciplines
  are already correctly in place:
  - Coroutine lifecycle: ViewModels consistently use `viewModelScope.launch`
    — no orphaned-coroutine pattern found.
  - LazyColumn list stability: `items(scans.value, key = { it.dbId })` and
    `items(slotEntries, key = { it.id })` both use stable keys (Compose's
    equivalent of DiffUtil stable IDs).
  - Database queries: Room DAOs return Flow<> for observed data and
    suspend fun for one-shot reads/writes — both dispatched off the main
    thread automatically; no runBlocking on main thread found.
  - Image loading: Coil is confirmed fully absent — correct, since the app
    doesn't display remote product images.
  - APK size: R8/minification verified enabled via CI.
  - Process death recovery: SavedStateHandle used in ResultViewModel.
  - Background work: ReminderWorker uses WorkManager, the correct battery-
    efficient API.
Recommendation: None — maintain these patterns.

[UNVERIFIED] — ANR risk and battery impact (wake locks, location update
  frequency) not independently re-profiled this session; would need a
  profiler/systrace run, not source reads alone.
```

---

# PART F — CROSS-CUTTING CONCERN MAP (Compound Finding Chains)

Individually minor findings from different categories can combine into
escalating harm chains. This section documents each real chain found across
Parts A-E, escalates the combined severity where it's genuine, and
explicitly notes where a chain does NOT escalate (because a downstream
safeguard already exists) rather than inventing risk that isn't there.

```
CHAIN 1 — Unverified contrast on a safety-critical, deliberately-restrained
  warning banner
Links: WCAG contrast unverified (Part B1, HIGH) → diet-veto/allergen
  banners at 15% alpha fill, a deliberately quiet treatment matching the
  confirmed "Quiet, not Playful" character (Part B8) → A2=High-stakes/
  Emotional axis (allergen/diet-safety data, not decorative content)
Combined severity: CRITICAL (escalated from HIGH)
Why it escalates: A contrast gap on a decorative element is a polish issue.
  The same gap on the exact element that tells a user "this product may
  violate your allergen or diet restriction" is a safety issue if it fails
  — nothing else in the current design (no motion, no stronger emphasis)
  would compensate. The banner does correctly pair icon + text + color, but
  that pairing still needs the color+text contrast itself to pass.
Recommendation: This is now the single highest-priority verification in the
  entire audit — compute the actual contrast ratio for FlagRed/AmberWarning
  text and icon tint against their own 15%-alpha-fill backgrounds
  specifically, before any other recommendation touching these colors ships.
Effort: LOW (a calculation) — but sequenced first
STATUS: RESOLVED — computed and verified passing across all three themes
  (13.8:1 / 7.7:1 / 4.7:1 body/title/icon over OLED, 12.5:1 over Light). See
  Part B1's STATUS note for the full pairing-by-pairing breakdown. The chain
  does not escalate to a real failure — no design change required.

CHAIN 2 — Building the score-reveal signature moment before fixing
  reduced-motion would make an existing accessibility gap worse, not better
Links: Score-reveal glow animation recommendation (Part B5/D, HIGH) →
  zero reduced-motion accommodation exists anywhere (Part B5/E, MEDIUM)
Combined severity: escalates the reduced-motion finding from MEDIUM to
  effectively HIGH *if the two are implemented out of order* — adding one
  more prominent, un-gated animation on top of an app with zero motion-
  reduction respect increases real-world impact even though each finding
  individually looked moderate.
Why this is a chain, not two items: neither finding changes in isolation —
  what changes is the correct BUILD ORDER.
Recommendation: Implement the ANIMATOR_DURATION_SCALE gating in the same
  commit as the score-reveal animation — never ship the new animation
  without the reduced-motion check already wired to it.
Effort: LOW-MEDIUM (a sequencing constraint on already-planned work)

CHAIN 3 — Missing touch-target size stacks with a destructive action, but
  does NOT escalate to a real risk, because a downstream safeguard exists
Links: 32-36dp delete/dismiss IconButtons below 48dp (Part E, MEDIUM) →
  triggers → DeleteConfirmDialog (already fixed earlier in this project's
  history with itemName-aware confirmation text)
Combined severity: does NOT escalate — stays MEDIUM/LOW (nuisance-level),
  explicitly NOT a data-loss risk
Why it doesn't escalate: An accidental tap on a small delete icon is real
  friction, but it lands on a confirmation dialog that names the specific
  item and requires a second deliberate action. The safeguard breaks the
  chain before it reaches real harm.
Recommendation: No change beyond what's already queued (Part E's own
  recommendation, pending visual verification).

CHAIN 4 — Missing Layer-3 token architecture is the single root cause
  behind three independently-rated LOW/MEDIUM findings
Links: "AccentGreen" misnamed (LOW) + 3 unreconciled gold values (LOW) +
  button/card recipes hand-copied per file (HIGH/MEDIUM) → all trace back
  to → no Layer 3 component-scoped tokens exist (Part B10, MEDIUM)
Combined severity: the underlying architectural gap is more accurately
  rated HIGH once its downstream cost is counted — it's the reason four
  separate findings each cost more to fix than they should (the "find and
  replace" test failing at ~28 files for a single color, not 1-2).
Why this is a chain: fixing any ONE symptom without building the Layer 3
  components first would be wasted effort — renaming AccentGreen today
  still touches ~28 files; building the components first, then renaming,
  touches 2.
Recommendation: Sequence the fixes — build ScanEatPrimaryButton/
  ScanEatCard FIRST, then the naming/consolidation fixes become 1-2-file
  changes instead of ~28-file changes.

CHAIN 5 — A finding first flagged as purely an aesthetic weakness gains
  real accessibility weight when cross-referenced
Links: SettingsScreen's backup-import error flagged as the weakest
  CHARACTER moment in the app (Part D, bare Text, no icon, no container) →
  "verify status/error/success are conveyed by icon + text + color, not
  color alone" (Part E, Category G)
Combined severity: escalates from POLISH/aesthetic-only (as originally
  framed) to a genuine accessibility-compliance concern — this error has
  no icon and conveys status via colored text only, closer to a color-
  independence violation than a taste question.
Why this matters: the aesthetic lens and the accessibility lens looked at
  the exact same code and produced two different severities for what turns
  out to be one finding — the value of running both lenses and then
  cross-referencing, rather than either alone.
Recommendation: The already-recommended fix (Part B8's shared ErrorBanner
  component) resolves both the character gap and the accessibility gap
  simultaneously — priority corrected upward here.
Effort: MEDIUM (already scoped; priority corrected)
```

**Chain summary**: Chain 1 (verify before anything else ships), Chain 2
(sequencing constraint on the score-reveal build), Chain 3 (verified NOT
escalating — confirmation dialog already breaks it), Chain 4 (root-cause
reprioritization — token architecture is HIGH, not MEDIUM), Chain 5
(severity correction — SettingsScreen's error is a §G1 finding, not just
polish).

---

# APPENDIX — Original Section Code Reference

For cross-referencing findings against design-aesthetic-audit-SKILL.md /
app-audit-SKILL.md directly. This document's Parts map to the skills'
sections as follows:

| This document | Original §-codes |
|---|---|
| Part A0-A4 | §0, §DS1-2, §DP0-2 |
| Part A5-A7 | §DBI1, §DBI3, §DBI2 |
| Part B1 | §DC1-5 |
| Part B2 | §DT1-4 |
| Part B3 | §DI1-4 |
| Part B4 | §DCO1-6 |
| Part B5 | §DM1-5 |
| Part B6 | §DSA1-5 |
| Part B7 | §DH1-4 |
| Part B8 | §DST1-4 |
| Part B9 | §DCVW1-3 |
| Part B10 | §DTA1-2 |
| Part B11 | §DDT1-2 |
| Part B12 | §DIL1-3, §DDV1-3, §DRC1-3 |
| Part C | §DCP1-3 |
| Part D | §DP3 |
| Part E — Category E | app-audit §E1-11 |
| Part E — Category F | app-audit §F1-6 |
| Part E — Category G | app-audit §G1-5 |
| Part E — Category H3 | app-audit §H3 |
| Part E — Category L3-5 | app-audit §L3-5 |
| Part E — Category D5 | app-audit §D5 |
| Part F | app-audit §II / §VIII (Compound Finding Chains / Cross-Cutting Concern Map) |

**Audit status**: Phase 1 (21 sections), Phase 2 (6 categories), and Phase 3
(5 compound chains) are all complete. The Executive Summary's priority
order is the recommended sequence for acting on this document.
