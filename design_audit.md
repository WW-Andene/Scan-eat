# Scan'eat — Full Deep Design Audit

Running log of the design-aesthetic-audit-SKILL.md "full deep" pass (Phase 1:
core aesthetic, per §EXEC). Findings use the skill's standard format:
`[SEVERITY] — Title / Dimension / Finding / Why it matters / Recommendation / Effort`.
Severity scale: CRITICAL / HIGH / MEDIUM / LOW / POLISH.

Evidence was gathered by three parallel code-reading passes (color/theme,
typography/motion, components/icons/states) over the actual Kotlin/Compose
source — not assumed. Sections not yet reached are listed at the bottom.

---

## §0. AESTHETIC CONTEXT

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
  Personality:       Extracted below in §DP0 (not previously declared).
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

---

## I. STYLE CLASSIFICATION

### §DS1. Design Language Identification

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

### §DS2. Style Coherence Assessment

- **Consistent style vocabulary**: Broken in two measurable ways — (1) shape
  has no scale: card corner radius is 12dp at ~44% of sites but 8/10/14/16/
  18/20dp are all also live for what reads as the same "card" role, with no
  documented rule for which applies where; (2) `glassSheen()` — the one
  genuinely distinctive surface treatment — is applied to dashboard/result/
  diary/history/recipes cards but *not* to Biolism tracker cards, onboarding,
  settings, or `RemindersCard`.
- **Style inflection points**: `ScanScreen.kt`'s primary permission `Button`
  and `FastingScreen.kt`'s primary `Button` both omit the
  `shape = RoundedCornerShape(12.dp)` param that ~8 other primary buttons use.
- **Intentional tension vs. accidental mixing**: Accidental — inline copy-
  paste drift, not deliberate neo-brutalist tension.
- **Theme attribute vs. hardcoded value audit**: Clean — zero hardcoded hex
  colors found in any Kotlin composable outside the theme directory.
- **Elevation vs. tonal surface mixing**: OLED scheme's `surface` and
  `surfaceVariant` are identical (`0xFF1C1820`) — no elevation-tier
  separation in the default theme; the Dark (non-OLED) scheme does
  differentiate them.

---

## XI. DESIGN CHARACTER SYSTEM

### §DP0. Character Extraction

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

### §DP1. Character Dimensions Analysis

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
  cashes that check. [UNVERIFIED — to confirm in §DSA]: whether Glow/Haze/
  Trace tokens are rendered anywhere as literal radial light pools, or exist
  underused.

Primary coherence fix candidate: Realize the ambient-glow concept the
  tokens already name — soft radial light-pool backgrounds behind key
  metric cards using the existing Haze/Trace values — since the character
  is already declared in code, just not expressed.
```

### §DP2. Design Character Brief — CONFIRMED BY USER

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

**Status: user confirmed this brief — proceed.**

---

## IX. BRAND IDENTITY ENGINEERING

### §DBI1. Brand Archetype Alignment

Closest fit: **The Craftsman** (warm neutrals, considered engineering,
editorial restraint) with **Companion** undertones (warmth, health-support).
Currently only the color layer sends Craftsman signals (CVD-safe grade
system, hue-consistent text tokens); icon/motion/shape send generic-utility
signals instead, creating an archetype conflict within the same product.

### §DBI3. Anti-Genericness Audit

**Already avoided (confirmed, not asserted):** no default Tailwind-blue
equivalent; text-color stack (`TextSecondary`/`TextMuted`/`TextLabel`)
already carries the background's hue instead of neutral gray; dark
background is genuinely chromatic, not `#111827`/`#0f1117`.

```
[MEDIUM] — Generic Material iconography with zero character calibration
Dimension: §DBI3 signal #6
Finding: 100% Icons.Default/AutoMirrored.Filled, 47 distinct icons, 85 call
  sites, 28 files — zero custom modification, ad hoc sizing (12-64dp, no
  shared token).
Why it matters: Largest identity-strength gap (§DP1 Dim5) — palette is
  Owned, icons are Borrowed from the single most common possible source.
Recommendation: Keep Material Icons but apply consistent warm-gold tinting
  for active/selected state instead of default onSurface, and standardize
  sizing into a token (20dp inline / 24dp nav / 40dp empty-state).
Effort: LOW

[MEDIUM] — Zero custom easing anywhere in the motion system
Dimension: §DBI3 signal #9
Finding: Every tween() call (AppNavGraph.kt, MainShell.kt) passes only a
  duration — no custom Easing object defined anywhere; all motion uses
  Compose's default FastOutSlowInEasing.
Why it matters: Motion currently contradicts the warmth color establishes
  (§DP1 Dim4). Default easing is the single most common motion signature in
  the Compose ecosystem — zero brand identity.
Recommendation: Define one custom Easing curve for the score-reveal/grade-
  ring animation — the app's "one unavoidable moment."
Effort: LOW

[LOW] — Light theme re-derives its own primary/secondary/tertiary hex
  instead of a documented transform from shared brand tokens
Dimension: §DBI3 signal #8
Finding: LightColors.primary=0xFFA07828, secondary=0xFFB05A38, tertiary=
  0xFF1A9090 — independently authored, not derived from Gold/AccentGreen/
  Teal. A fourth gold value, LightGoldAccent=0xFF8B6914, exists separately
  for Biolism-light-mode.
Why it matters: A rebrand or accent-color change touches 3+ gold values
  instead of 1 (§DTA2 find-and-replace test failure).
Recommendation: Derive LightColors' primary/secondary/tertiary from Gold/
  AccentGreen/Teal via one documented OKLCH lightness-shift rule.
Effort: MEDIUM (needs visual re-check of light-mode contrast)

[LOW] — Single separator/border treatment, no heavy/light/accent taxonomy
Dimension: §DBI3 signal #11
Finding: glassSheen()'s hairline edge is the only border-like treatment,
  applied uniformly wherever used, with no weight distinction by context.
Recommendation: Define 2-3 explicit separator opacities (20%/8%/accent-30%)
  as named tokens next to the existing Colors.kt system.
Effort: LOW
```

---

## II. COLOR SCIENCE DEEP DIVE

```
[MEDIUM] — Accent color has no dedicated pressed/hover-equivalent state token
Dimension: §DC2
Finding: Gold/AccentGreen/Teal are flat values with no "-pressed" sibling;
  buttons rely entirely on Material3's default ripple (zero
  RippleTheme/LocalRippleConfiguration customization anywhere).
Recommendation: Add one AccentGreenPressed/GoldPressed token (~8% darker,
  same hue) and apply via custom ripple color on the primary-button sites.
Effort: LOW

[MEDIUM] — OLED theme's surface and surfaceVariant are identical (no
  elevation tier), while the Dark theme correctly differentiates them
Dimension: §DC3
Finding: OledColors.surface = OledColors.surfaceVariant = 0xFF1C1820.
  DarkColors differentiates surface (0xFF221E27) from surfaceVariant
  (0xFF322C38) — the correct pattern already exists elsewhere in the file.
Recommendation: Port the Dark theme's surface/surfaceVariant step into
  OledColors (the default, most-used theme).
Effort: LOW

[LOW] — "AccentGreen" token name doesn't match its actual color (coral),
  and Gold exists as three unreconciled values
Dimension: §DC2 / §DBI3 signal #8
Finding: AccentGreen = 0xFFD97C56 (coral/terracotta). Gold exists as Gold
  (0xFFC9A84C), LightGoldAccent (0xFF8B6914), LightColors.primary
  (0xFFA07828) — three hex values for one brand hue.
Recommendation: Rename AccentGreen → AccentCoral (~28 files, mechanical);
  consolidate the three golds per the §DBI3 fix above.
Effort: LOW (rename) / MEDIUM (gold consolidation)

[POLISH] — Score/success moments don't intensify color beyond the standard
  grade palette
Dimension: §DC5
Finding: gradeColor() applies identically for routine results and genuine
  milestones — no color/glow intensification for success moments.
Recommendation: Addressed properly under §DST4/§DP3 (states + motion), not
  a standalone color fix.
Effort: LOW
```

**Protect:** OLED background's cool-violet undertone, cream (not pure-white)
primary text, zero hardcoded hex colors leaking outside the theme layer.

---

## III. TYPOGRAPHY AS VISUAL EXPRESSION

```
[MEDIUM] — Base typeface is unmodified system Roboto, in tension with the
  "warm" character established by color
Dimension: §DT1
Finding: No fontFamily set anywhere in ScanEatTypography (Type.kt); every
  slot defaults to platform Roboto (neutral Grotesque/Geometric hybrid).
Recommendation: A single warm-humanist swap (Plus Jakarta Sans or Manrope,
  both bundlable) for body+heading.
Effort: MEDIUM (font bundling + visual re-check across 15 type slots)

[HIGH] — Zero tabular-figure treatment anywhere numbers are displayed
Dimension: §DT3
Finding: Confirmed zero "tabular"/FontFeatureSettings/font-variant-numeric
  usage anywhere. Scores, nutrition values, weight entries, calorie totals
  all render with Roboto's default proportional figures.
Recommendation: Apply `TextStyle(fontFeatureSettings = "tnum")` to every
  numeric display — NutritionTable.kt, weight-history rows, dashboard macro
  cards, ScoreDisplay.
Effort: LOW (one modifier, ~6-8 call sites)

[MEDIUM] — "Hero number" role has no shared token — 4 weight spellings, 5+
  concrete sizes
Dimension: §DT2
Finding: KetosisProcessCard (24sp/Medium), CalorieBalanceCard (32sp/Black),
  DailyEnergyCard (34sp/W500), HeroCard (42sp/W500), ScoreDisplay (untethered
  raw 56sp, no base at all).
Recommendation: Add one ScanEatTypography.heroNumber style (canonical
  FontWeight.Black) and let screens vary only fontSize from a defined
  32/42/56sp scale.
Effort: LOW (one shared style + ~6 call-site swaps)

[LOW] — 3 of 15 M3 type slots (displayLarge, displayMedium, headlineSmall)
  were never hand-tuned
Dimension: §DT2
Finding: Type.kt only overrides 11/15 slots; the untouched 3 silently use
  M3's un-tuned defaults.
Recommendation: Hand-tune the remaining 3 to match the rest, or confirm
  they're unused and drop them from the "available slots" mental model.
Effort: LOW

[POLISH] — No letter-spacing/tracking adjustment on any of the 15 type slots
Dimension: §DT2
Finding: Every TextStyle override leaves letterSpacing at default (0.sp),
  including labelSmall (11sp), which should carry +0.03-0.06em at that size.
Recommendation: +0.02em on titleLarge/headlineMedium; +0.04em on
  labelSmall/labelMedium.
Effort: LOW
```

**Protect:** the hero-number scale-contrast instinct (56sp vs 12sp body) is
correct compositionally — it needs a token, not a redesign. OpenDyslexic
accessibility swap is real, considered craft.

---

## XV. COMPONENT DESIGN CHARACTER

```
[HIGH] — No shared Button component; the primary-button recipe is hand-
  copied inline across ~8+ files, with visible drift
Dimension: §DCO1
Finding: `ButtonDefaults.buttonColors(containerColor = AccentGreen)` + black
  label + `RoundedCornerShape(12.dp)` repeated verbatim across Onboarding/
  Settings/Scan/LogSheet/Fasting screens — but ScanScreen's permission button
  and FastingScreen's primary button omit `shape`, silently reverting to
  M3's default pill shape.
Why it matters: Most-seen interactive element in the app; a coherence bug,
  not a taste question. The §DBI3 "find and replace" test currently fails
  here — changing the primary button means editing 8+ files by hand.
Recommendation: Extract a `ScanEatPrimaryButton` composable once, in
  presentation/ui/theme/, alongside EmptyListState.kt and
  scanEatTextFieldColors() (both already prove this pattern works).
Effort: MEDIUM (extraction + swap ~10 call sites)

[MEDIUM] — Every "card" is a hand-rolled Surface; radius/color/glassSheen
  presence all vary per screen with no shared component
Dimension: §DCO3
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
Dimension: §DCO4
Finding: MainShell.kt's NavigationBar show/hide uses bare fadeIn()/
  fadeOut(). [UNVERIFIED]: whether the bottom nav's selected-tab treatment
  uses Gold tinting or M3 defaults — needs a direct read of MainShell.kt/
  TopTab.kt's NavigationBarItem call before treating as confirmed.
Recommendation: Verify selectedIconColor/selectedTextColor args; set to
  Gold if defaulted.
Effort: LOW (verify first)

[UNVERIFIED, LOOKS FINE] — Input fields (§DCO2) and modals/sheets (§DCO5)
Finding: scanEatTextFieldColors() confirmed shared across ~7 screens with
  AccentGreen focus color. LogSheet.kt's bottom sheet uses a distinct 20dp
  top-corner radius — a defensible modal-tier distinction, not an
  inconsistency.
Recommendation: No fix needed at this pass.
```

---

## V. HIERARCHY & GESTALT PRINCIPLES

```
[PROTECT, NO FIX NEEDED] — Score ring correctly claims primary visual weight
  on the Result screen
Dimension: §DH1
Finding: ScoreRing/DualScoreRing (178dp/110dp, largest element, highest-
  saturation color via gradeColor(), centered, isolated) is unambiguously
  the correct focal point.
Recommendation: Use as the reference pattern for "what a focal moment looks
  like" when extending atmosphere elsewhere (§DSA5).

[MEDIUM] — Chroma contrast (one saturated element in a desaturated field) is
  under-used outside the score ring
Dimension: §DH4
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

[SCOPE GAP] — Reading-flow (§DH2) and full Gestalt proximity/similarity
  (§DH3) require direct layout inspection not yet done
Recommendation: Defer to a follow-up pass reading DashboardScreen.kt/
  DiaryScreen.kt's actual Column/Row structure directly.
```

---

## VI. SURFACE & ATMOSPHERE DESIGN

```
[CRITICAL] — The "ambient glow" color tokens (Glow/Haze/Trace families) are
  defined but not confirmed to be rendered as actual light — the app's
  material language is Flat while its color tokens promise Deep
Dimension: §DSA1, §DSA4, §DSA5 (the concrete form of the §DP1 Dim3 CRITICAL
  gap)
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
  realizing these tokens closes Dimension 3 and Dimension 5 simultaneously,
  using values that already exist (zero new design decisions needed).
Recommendation: Verify usage first, then apply a soft
  `Brush.radialGradient()` using the Haze token (lowest intensity) as a
  background-atmosphere layer behind 1-2 "focal" surfaces per screen (Result
  screen's score ring, Dashboard's primary metric card), reserving the
  brighter Glow token for the single most important element only —
  explicitly not uniform across every card (the most common atmosphere-
  hierarchy violation per §DSA5).
Effort: MEDIUM (verify-then-implement; values already exist)

[MEDIUM] — No established light source direction; the app's one shadow
  (badge, 6dp) and glassSheen()'s "top-light" sheen aren't confirmed
  consistent with each other
Dimension: §DSA4
Finding: glassSheen()'s doc comment describes a top-light gradient; the one
  shadowElevation usage (streak badge) uses Compose's default (also
  top-down), but the two haven't been verified against each other in the
  same file.
Recommendation: Declare "top-center, soft, warm" as the app's one documented
  light source, noted once near the Colors.kt/Glass.kt token definitions.
Effort: LOW

[LOW] — No grain/noise texture layer exists anywhere
Dimension: §DSA3
Finding: No noise/grain overlay found in any surface treatment.
Recommendation: Low priority relative to the Glow/Haze finding; revisit
  only after the atmosphere-hierarchy fix lands, ~3% opacity on the OLED
  background only (not cards).
Effort: LOW, but LOW priority
```

## IV. MOTION ARCHITECTURE

```
[HIGH] — The app's "one unavoidable moment" (the score reveal) uses no
  distinctive motion — driven by the same generic default as everything else
Dimension: §DM5, §DP3 technique 5
Finding: No animateFloatAsState/spring/Crossfade/updateTransition exists
  anywhere in the codebase (confirmed, full-codebase grep). The score ring
  (ScoreDisplay.kt) — the single most important value-delivery moment —
  appears to render its final state without any distinct reveal animation.
Why it matters: Per §DP3 technique 5, every product has one moment
  deserving maximum character investment; this app's gets zero motion
  investment currently.
Recommendation: Add a signature reveal — the score ring's progress arc
  animates in via animateFloatAsState with a custom (non-default) easing
  over ~600-800ms, paired with the grade-color glow (from the §DSA fix)
  intensifying as the arc completes.
Effort: MEDIUM (isolated to one composable)

[MEDIUM] — Zero reduced-motion accessibility handling anywhere
Dimension: §DM3
Finding: Confirmed zero matches for ANIMATOR_DURATION_SCALE or any
  accessibility-driven motion-disable path anywhere in the codebase or
  Settings UI.
Why it matters: A real accessibility gap (also flagged under §G4 in Phase
  2), not just a character question.
Recommendation: Read Settings.Global.ANIMATOR_DURATION_SCALE once and gate
  the new score-reveal animation (and existing nav transitions) behind it.
Effort: LOW-MEDIUM

[LOW, MOSTLY PROTECT] — Screen-transition timing is serviceable but generic
  easing; the tab-switch/peer-navigation duration split itself is fine
Dimension: §DM1, §DM2
Finding: AppNavGraph.kt's tab-switch (200ms fade) vs peer-navigation (300ms
  slide+fade) split is a reasonable, intentional-feeling structure — only
  the easing curve is generic (already covered under §DBI3).
Recommendation: No new fix beyond the custom-easing recommendation already
  given; don't touch the 200/300ms split itself.
Effort: N/A
```

---

## Not yet covered (continuing)

§DI1-4 (Icons, deeper spec), §DST1-4 (States), §DCVW1-3 (Copy × Visual),
§DTA1-2 (Tokens), §DRC1-3 (Responsive), §DDT1-2 (Trends), §DP3 (Deepening
protocol), §DBI2 (Design DNA spec), §DCP1-3 (Competitive positioning —
needs web research).

Phase 2 (expanded UI audit from app-audit-SKILL.md): §E1-10, §F1-6, §G1-4,
§H3, §L3-5, §D5 — still to come after Phase 1 completes.
