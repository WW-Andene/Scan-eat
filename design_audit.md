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

## VII. ICONOGRAPHY SYSTEM

```
[MEDIUM] — Icon expressiveness sits at "Utilitarian," below what the
  confirmed character (Craftsman/warm-glow) calls for
Dimension: §DI3 (Expressiveness Spectrum)
Finding: 100% default Material Icons, zero weight/style customization —
  squarely "Utilitarian" tier. Given A5=Amplifies value and the confirmed
  Craftsman-leaning character, "Calibrated" is the appropriate target
  (library base + weight/tint matched to character) — not a full custom
  icon system, which would be disproportionate effort for a non-revenue,
  domain-conventional app.
Recommendation: See Icon Character Brief below.
Effort: LOW-MEDIUM
```

```
ICON CHARACTER BRIEF
  Product character: Warm light glowing in a dark room — quiet, confident,
    health-serious, restrained (from confirmed §DP2)
  Target expressiveness: Calibrated (not Signature/Illustrative — the
    domain-conventional, non-revenue context doesn't justify a full custom
    icon system; matching the existing Material Icons base with deliberate
    tinting/sizing does)

  Visual specification:
    Grid:             Keep Material's native 24×24dp grid — no change needed
    Stroke/fill:       Keep filled style (already 100% consistent, a real
                       strength) — do not introduce Outlined/Rounded mixing
    Corner treatment:  No change — inherits from Material's filled glyphs
    Tint strategy:     Default/inactive icons stay OnBackground.copy(0.5-0.7)
                       (current pattern, keep); active/selected icons (nav,
                       toggled states) switch to Gold (0xFFC9A84C) instead of
                       whatever each screen currently defaults to — this is
                       the one calibration that actually connects icons to
                       the brand palette
    Size token:        Standardize the currently-ad-hoc 12-64dp range into
                       3 named sizes: 20dp (inline/label-adjacent), 24dp
                       (nav bar, matches Material's native grid), 40dp
                       (empty states, matches EmptyListState.kt's existing
                       value — don't change that one)
    Unique motif:      None needed at Calibrated tier

  What icons must express: Restraint and warmth through color, not through
    novel shapes — the "glow" character comes from tint, not form
  What icons must avoid: Any move toward a second icon library, outlined/
    filled mixing, or expressive multi-color icon treatments (would violate
    the confirmed "Quiet, not Playful" register)
```

---

## XIII. STATE DESIGN SYSTEM

```
[HIGH] — Error states use three unreconciled color/component systems across
  the app, none matching the confirmed "warm glow" character
Dimension: §DST3
Finding: (1) ScanScreen's real-error case uses Material's colorScheme.error/
  errorContainer tokens (the only place in the app using them at all); (2)
  ResultBanners' diet-veto and allergen cards use custom FlagRed/
  AmberWarning at 15% alpha; (3) SettingsScreen's backup error is a bare
  colored Text with no container, icon, or structure at all — the most
  minimal treatment found anywhere in the app.
Why it matters: Per §DST3's character-specific guidance, "the error must
  feel like this product's error" — right now it feels like three different
  products' errors depending on which screen you're on. Given A2=High-
  stakes/Emotional, allergen/diet-safety errors specifically deserve the
  most consistent, trustworthy treatment in the whole app, not the most
  fragmented one.
Recommendation: Standardize on the custom FlagRed/AmberWarning system
  (option 2) as the house error language — it's already palette-integrated
  and nearly matches the "warm, restrained" character (unlike Material's
  generic colorScheme.error). Build one shared ErrorBanner composable with
  icon+text+optional-dismiss, and migrate ScanScreen's real-error case and
  SettingsScreen's bare-text error onto it.
Effort: MEDIUM (one new shared composable + 2-3 call-site migrations)

[MEDIUM] — Empty states are correctly shared (EmptyListState.kt) but
  minimal — icon + text only, no CTA, and not yet stress-tested against the
  confirmed character
Dimension: §DST1
Finding: EmptyListState.kt (40dp icon at 50% opacity + message text) is used
  consistently across Recipes/Templates/CustomFood — a genuine, deliberate
  shared component (its own doc comment confirms this was extracted from
  3 duplicated inline versions).
Why it matters: Per §DST1, an empty state should contain a character-
  positive visual element + explanation + primary action — this one has
  only the first two. It's consistent (good), but at the floor of what the
  skill considers complete.
Recommendation: Add a primary action slot (optional param, only used where
  a clear next step exists — e.g. "Add a recipe" CTA button on
  RecipesScreen's empty state) rather than redesigning the whole component;
  keep the icon/text pattern, which is already appropriately restrained for
  a "Quiet" character (no illustration library needed).
Effort: LOW (additive param, no visual regression risk)

[MEDIUM] — Loading states have no shared skeleton pattern; scores/data-viz
  bars reuse plain CircularProgressIndicator/LinearProgressIndicator with no
  character-specific styling
Dimension: §DST2
Finding: The same two Material progress primitives serve both true
  async-loading (ScanScreen's in-FAB spinner, ResultScreen's whole-screen
  spinner) and static data-viz rings/bars (ScoreDisplay's grade ring,
  CalorieBalanceCard's macro bar) — styled ad hoc per file (different
  strokeWidth/color each time), with zero skeleton-style "shape of what's
  coming" treatment anywhere.
Why it matters: Lower priority than the error-state fragmentation (this one
  is more about polish than trust), but the confirmed character calls for
  a specific loading feel, and right now it's just "whatever
  CircularProgressIndicator defaults to, tinted per-screen."
Recommendation: For the specific case of ResultScreen's whole-screen
  loading spinner (the wait immediately before the score reveal) —
  standardize its color/stroke to match the new score-reveal motion
  signature from §DM5, so the loading state visually sets up the reveal
  rather than being generic.
Effort: LOW (one call site, follows from the §DM5 fix already recommended)

[POLISH] — No milestone/success-state intensification exists anywhere
Dimension: §DST4
Finding: Confirmed no distinct "milestone success" visual treatment (per
  §DC5) — grade colors and layout are identical whether it's a routine scan
  or a genuine first-A+/streak-milestone moment.
Why it matters: Per §DST4, this is the single highest-emotional-receptivity
  moment available and it's currently unexploited — lowest-urgency finding
  in this batch since the app functions correctly without it.
Recommendation: Defer to §DP3 (Deepening) — this is the same "one
  unavoidable moment" finding as §DM5, viewed from the states lens rather
  than the motion lens. Don't build two separate fixes for one moment.
Effort: N/A (folds into §DM5's recommendation)
```

---

## XVI. COPY × VISUAL ALIGNMENT

```
[LOW] — Copy voice is competent but "invisible," slightly under the warmth
  the confirmed character calls for
Dimension: §DCVW1
Finding: Sampled copy this session (e.g. "Non végan : ...", "URL du serveur
  non configurée — configurez-la dans Réglages", "Server URL not
  configured — set it up in Settings") sits at Formal-to-neutral, Terse,
  and Impersonal-to-neutral on the voice dimensions — functionally correct
  and clear, but doesn't lean toward "Voiced"/"Personal" the way the
  confirmed "warm, quiet-confident" character would suggest (2-position gap
  on Dimension 3, Personality presence).
Why it matters: A genuinely low-cost, low-risk finding — copy tone is one
  of the cheapest character levers available, and the app's current copy
  isn't wrong, just under-warm relative to everything else in the
  Character Brief.
Recommendation: Not a wholesale rewrite — targeted warmth at the highest-
  visibility moments only (per §DP3's "primary character carriers" idea):
  the score-reveal verdict text and milestone messages are worth a warmer
  pass; routine error/validation copy can stay terse-and-clear as-is (that's
  appropriate for a health-safety context — over-warming an allergen warning
  would be a mistake, not an improvement).
Effort: LOW (copy-only changes at ~2-3 highest-visibility strings)

[UNVERIFIED] — Full microcopy audit (§DCVW2) — button labels, field labels,
  tooltip text — not systematically sampled in this pass
Recommendation: Defer to a follow-up read of button/label strings.xml
  entries specifically, rather than assert findings from the small sample
  gathered incidentally this session.
```

---

## VIII. TREND CALIBRATION

```
[PROTECT — no fix needed] — The app's trend posture is Timeless-leaning,
  which is correct for its axis profile
Dimension: §DDT1, §DDT2
Finding: Checked against the full trend inventory — no bento-grid layout, no
  AI gradient mesh, no dot-grid background, no 3D elements, no variable-font
  weight animation, no brutalist typography anywhere in the codebase. Dark-
  mode-as-default is present, but that's baseline-expected in 2024+, not a
  trend chase. The "glass" attempt (glassSheen()) is notably NOT the
  mainstream backdrop-blur trend — it's a custom sheen-only approach,
  meaning the app isn't even copying the current glass trend, it built its
  own adjacent thing.
Why it matters: Per §DDT2, "Timeless" posture is correct for institutional/
  long-lifecycle/trust-dependent products — a health-scoring app chasing
  "AI startup" visual signals (gradient mesh especially) would actively
  undermine the credibility A2=High-stakes/Emotional demands. This is a
  strength worth stating explicitly, not a gap to fix.
Recommendation: None. Don't introduce trend-chasing elements to "modernize"
  — the current restraint is on-character.
Effort: N/A

[POLISH] — Colors are authored as raw hex rather than a perceptually-uniform
  space, which is a documentation/precision gap more than a visible one
Dimension: §DDT1 (OKLCH adoption signal)
Finding: All color tokens are `Color(0xFF...)` hex literals; no OKLCH
  authoring or even OKLCH-equivalent documentation exists for how the
  palette's lightness/chroma steps were chosen.
Why it matters: Lowest-priority item in this batch — Android has no native
  OKLCH color type, so this doesn't block anything functionally. It matters
  only for future palette work (e.g. deriving the light-mode golds
  correctly per the §DBI3/§DC2 finding above) — doing that derivation in
  OKLCH terms (even if the final value ships as hex) would prevent the next
  "three unreconciled golds" problem.
Recommendation: When executing the light-mode gold consolidation fix
  already recommended, do the derivation math in OKLCH and only convert to
  hex at the end — document the OKLCH source values in a code comment next
  to the resulting hex.
Effort: LOW (process change for the next color-derivation task, not a
  standalone fix)
```

---

## XIX. DESIGN TOKEN ARCHITECTURE

```
[MEDIUM] — Token architecture reaches Layer 2 (semantic) solidly but almost
  never Layer 3 (component); the "find-and-replace" test fails concretely
  for the accent color
Dimension: §DTA1, §DTA2
Finding: Layer 1 (primitives — Gold/AccentGreen/Teal/etc. in Colors.kt) and
  Layer 2 (semantic — MaterialTheme.colorScheme mapping in Theme.kt) are
  both genuinely present and reasonably disciplined. Layer 3 (component
  tokens) barely exists: `scanEatTextFieldColors()` is the one real example;
  buttons and cards have no equivalent, so they consume Layer 1 primitives
  directly (`AccentGreen` imported and referenced at ~28 files) rather than
  through a component-scoped token.
Why it matters: This is the architectural root cause behind three separate
  findings already raised (§DCO1 button drift, §DCO3 card drift, §DC2's
  "AccentGreen" naming risk) — they're all symptoms of the same missing
  Layer 3. The find-and-replace test concretely fails: changing the primary
  accent today means touching ~28 files correctly, not editing 1 token
  definition.
Recommendation: This confirms (doesn't duplicate) the priority already
  established: building `ScanEatPrimaryButton`/`ScanEatCard` (§DCO1/§DCO3
  recommendations) IS the Layer 3 fix — once those exist, the accent color
  only needs to change in 2 places (the two new components) instead of ~28.
  No separate action needed beyond executing those two recommendations.
Effort: N/A (already scoped under §DCO1/§DCO3 above)

CHARACTER TOKEN AUDIT
  ☑ Background surface lightness step — tokenized in Dark scheme; NOT
    tokenized/absent in OLED scheme (§DC3 finding)
  ☒ Primary accent hex — tokenized (Layer 1/2), but not OKLCH-verified for
    perceptual consistency across the palette (§DDT1 note above)
  ☒ Component border-radius — hardcoded per call site, no shared scale
    (§DBI3/§DS2 finding — 9 distinct values in circulation)
  ☑ Typography scale — tokenized via Type.kt (11/15 slots hand-tuned)
  ☒ Transition durations — hardcoded per animation call, no named constants
    (200/300ms repeated as literals, not DesignTokens.durationFast, etc.)
  ☒ Shadow definitions — barely exist (one hardcoded 6dp value), not
    tokenized
  ☑ Focus ring style — tokenized for text fields via
    scanEatTextFieldColors(); NOT tokenized for buttons (no distinct focus
    state customization found)
  ☐ Spacing base unit — unverified, deferred to §DH2 follow-up
```

---

## XIV. RESPONSIVE DESIGN CHARACTER

```
[SKIPPED — per skill's own skip rule]
Finding: Scan'eat is a phone-primary Compose app. No WindowSizeClass usage,
  no layout-sw600dp/layout-w840dp resource qualifiers, and no foldable-
  aware layout logic were found or evidenced this session — the app appears
  to be a single fixed-form-factor (phone portrait) layout with no
  responsive/tablet/foldable adaptation built yet.
Why skipped: §DRC's own execution rule: "Skip this section entirely if the
  app is not responsive or is a single fixed-width layout." That's the
  case here.
Note (not a §DRC finding, flagged for completeness): tablet/foldable support
  is a real absence, but it belongs to app-audit's §O (Development Scenario
  Projection) or a future-feature backlog item, not this design-character
  audit — building responsive layouts is a scope decision, not a character
  fix.
```

---

## XI. DESIGN CHARACTER SYSTEM (continued)

### §DP3. Character Deepening Protocol

```
1. CHARACTER TOKEN EXTRACTION
   EXPRESSES the "warm glow" character correctly (protect these):
     - Background 0xFF0F0D12 (cool-violet near-black, not neutral gray)
     - Gold 0xFFC9A84C at its current moderate (not maxed) chroma
     - The Glow/Haze/Trace alpha-family token structure itself (even if
       under-rendered today — the naming convention is correct)
     - glassSheen()'s top-light sheen mechanism
   UNDERMINES the character (replace/fix):
     - System Roboto with zero fontFamily customization (cold-neutral,
       fights the "warm" signal)
     - Default FastOutSlowInEasing everywhere (mechanical, fights
       "considered/organic")
     - 100% unmodified Material Icons (generic, fights "owned identity")
     - Flat Surface fills with no radial-glow atmosphere (literally the
       opposite of what "glow" tokens promise)

2. CHARACTER STRESS TESTING
   Already covered by dedicated sections above — restating as a scorecard:
     Error states:   FAILS — 3 unreconciled systems (§DST3)
     Empty states:   PASSES (minimally) — consistent, restrained, correct
                      tier for the character (§DST1)
     Loading states: FAILS — generic Material spinners, no character
                      treatment (§DST2)
     Edge-case/admin: SettingsScreen's bare-text error is the single
                      weakest character moment found in the entire audit
     Mobile breakpoint: N/A — phone-only app, no breakpoint to stress-test

3. SENSORY VOCABULARY
   Sensory reference: "An ember glowing quietly in a dark hearth."
   Design implications not yet expressed:
     - Light should look genuinely emanating (radial glow), not a flat fill
       — ties directly to the §DSA1/§DSA5 finding
     - The glow stays modest and warm — never blazing/neon (matches the
       confirmed REJECT rule against "loud/Playful" treatments)
     - Elements away from the "ember" (secondary chrome, dividers,
       disabled states) should recede toward near-darkness, not compete —
       ties to the §DH4 chroma-contrast finding on Dashboard cards
     - Material should read as warm despite being dark — avoid cold-clinical
       cues (harsh drop-shadows, neutral-gray dividers, pure-white light
       theme surface) — ties to the §DSA2/§DC3/§DBI3 findings on OLED
       elevation and light-mode's pure-white surface

4. CHARACTER HIERARCHY
   PRIMARY CARRIERS (max investment):
     - The score/grade reveal (Result screen) — the app's "one unavoidable
       moment," currently under-invested (§DM5/§DST4)
     - Dashboard's primary metric card
   SECONDARY CARRIERS (present, not dominant):
     - Navigation bar, buttons, cards generally, diary/history rows
   BACKGROUND ELEMENTS (consistent but quiet):
     - Dividers, timestamps, disabled states, settings toggles — currently
       mostly Material-default (character-neutral, see below)

5. THE ONE UNAVOIDABLE MOMENT
   Identified: the score/grade reveal on the Result screen — the moment a
   user gets what they opened the app for.
   Current state: renders instantly, same generic default motion as
   everything else, no color/glow intensification beyond the standard
   grade palette (already flagged individually at §DM5 and §DST4 — this is
   the single point where those two findings converge).
   This is the highest-leverage fix in the entire audit precisely because
   it's one moment, already identified, already has the necessary color
   tokens sitting unused (§DSA1's Glow/Haze finding) — implementing it
   closes four separate findings at once (§DM5, §DST4, §DC5, part of §DSA1).

6. CHARACTER-NEUTRAL AUDIT
   Character-neutral elements found (don't violate, don't express):
     - Dividers/separators — currently just glassSheen()'s hairline, no
       heavy/light/accent distinction (already flagged, §DBI3 signal #11)
     - Disabled button states — 100% Material default dimming, no warm-
       toned disabled treatment
     - [UNVERIFIED] Settings toggle switches — not confirmed whether
       Switch colors are customized to Gold or left at M3 default; worth a
       quick check before assuming either way
   For each: the minimal fix is the same pattern already established
   elsewhere in the app (tint toward Gold/coral at low intensity) rather
   than a new visual language.

7. CHARACTER FUTURE-PROOFING

   Character Rules (apply to all new features):
     1. Every new accent-colored element emits light onto the dark surface
        around it via the existing Haze/Trace tokens — never a flat
        saturated fill.
     2. Every new numeric display uses tabular figures
        (fontFeatureSettings="tnum") from day one.
     3. Every new card/surface goes through the shared card component (once
        built per §DCO3) — never a fresh hand-rolled Surface().
     4. New motion stays within the established 200ms(tab)/300ms(peer-nav)
        split, EXCEPT the one signature moment (score reveal), which alone
        may use a slower, custom-eased animation.

   Character Risks (watch as the product scales):
     - Settings/admin-style screens are the classic place character
       reverts to defaults — SettingsScreen's bare-text error (§DST3) is
       already the weakest character moment found in this entire audit;
       it will get worse, not better, as more settings ship, unless it's
       explicitly held to the same standard as user-facing screens.
     - Any future external surface (Play Store listing, marketing site)
       needs to inherit this exact palette — right now the identity lives
       only inside the app itself.
```

### §DBI2. Design DNA Specification

```
DESIGN DNA SPECIFICATION
  Color DNA:     Cool-violet-tinted OLED near-black (0xFF0F0D12) lit by warm
                 gold (0xFFC9A84C) primary + coral (0xFFD97C56) secondary +
                 sparing teal (0xFF38C8C8) tertiary — deliberate warm-on-cool
                 temperature contrast, moderate (not maxed) chroma throughout
  Type DNA:      Currently: system Roboto, 400-900 weight range, hero
                 numbers in Black weight (untokenized). Target: a warm-
                 humanist swap (§DT1) at body/heading, hero-number role
                 tokenized at 32/42/56sp
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
  SIGNATURE ELEMENT (target, once §DM5 lands): The score-reveal glow
    animation — the ring's arc completing while its grade-color glow
    intensifies via the Haze/Trace tokens. This would be a genuinely
    unmistakable, hard-to-copy moment, built entirely from values and
    concepts that already exist in the codebase.
```

---

## XVII. ILLUSTRATION & GRAPHIC LANGUAGE

```
[SKIPPED — per skill's own skip rule]
Finding: No custom illustrations, spot graphics, or illustrated empty-state
  compositions exist anywhere in the app (confirmed — EmptyListState.kt
  uses a Material icon, not an illustration; no illustration-related assets
  found in res/drawable/ beyond launcher/notification icons).
Why skipped: §DIL's own execution rule: "Skip when the app has no
  illustrations, custom graphics, or spot illustrations." That's the case
  here — this section has nothing to audit.
```

## XVIII. DATA VISUALIZATION CHARACTER

```
[PARTIALLY COVERED ELSEWHERE, not a standalone gap]
Finding: The app does use progress-ring/progress-bar data visualization
  (ScoreDisplay's grade rings, dashboard macro rings/bars, Biolism's
  LinearProgressIndicator usages) — these aren't absent, but they're built
  from Material's CircularProgressIndicator/LinearProgressIndicator
  primitives rather than a dedicated charting library, and their character
  treatment was already audited under §DST2 (Loading State Design, since
  the same primitives serve both loading and data-viz roles) and §DH1
  (the score ring's correct focal-weight treatment).
Why not a new section: There's no additional data-viz-specific finding here
  beyond what's already been raised — treating this as its own section
  would duplicate §DST2/§DH1 rather than add new evidence.
```

---

## X. COMPETITIVE VISUAL POSITIONING

Web search returned confirmed detail for Yuka (its light-background, color-
coded 0-100 score identity is well-documented); Cronometer and Open Food
Facts didn't return fetchable visual specifics this pass, so those two are
marked `[UNVERIFIED beyond general knowledge]` rather than asserted as
confirmed — per the skill's own accuracy rule, that's the honest way to
handle incomplete research rather than filling gaps from memory.

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
  Typography:          [UNVERIFIED beyond general knowledge — likely a
                        standard mobile-default sans, not independently
                        confirmed this pass]
  Spatial density:     Simple, low-density, single-score-forward — optimized
                        for instant legibility at a glance in-store, not for
                        sustained data review.
  Signature element:   The 0-100 score number itself, at large scale, is
                        the whole identity — there's little else to
                        remember visually.
  Genericness score:   2/5 — the traffic-light convention itself is now a
                        category standard (many apps use it), but Yuka
                        owns the "big number + light background" execution
                        of it at mass-market scale.
  Relation to this app: Yuka does NOT occupy the dark/atmospheric/premium
                        territory Scan'eat is reaching for — this is real,
                        confirmed whitespace (see below), not a guess.

Product B: Cronometer [UNVERIFIED beyond general knowledge — not
  independently re-confirmed via screenshot this pass]
  Visual character:    Widely known as a light-mode-default, data-table-
                        dense tracker prioritizing full nutrient panels over
                        a single score — utility-first, not atmosphere-first.
  Accent / palette:    Green-accented, generally light background.
  Relation to this app: If accurate, occupies a "quantitative depth, low
                        visual investment" position distinct from both Yuka
                        and Scan'eat.

Product C: Open Food Facts (official app) [UNVERIFIED beyond general
  knowledge]
  Visual character:    Open-source, basic Material Design conventions,
                        green branding, minimal custom visual investment —
                        functional over designed.
  Relation to this app: Likely the least visually invested of the three
                        benchmarks; low competitive threat on aesthetics
                        specifically, strong threat on data breadth (which
                        is outside this audit's scope).

WHITESPACE OPPORTUNITY
  Available position: "Dark, warm, atmospheric premium" in a category where
    every major visible competitor (at minimum, the confirmed case of
    Yuka) defaults to light-background, primary-color-forward, mass-market
    clarity.
  Why it's available: Nutrition-scoring apps optimize for instant in-store
    legibility (bright light, quick glance) — light backgrounds serve that
    use case well, so competitors have little reason to invest in a dark/
    atmospheric identity. Scan'eat's broader scope (Diary, Biolism/
    metabolism modeling, weight tracking, meal planning) means it's used
    in more contexts than "standing in a store aisle," which is exactly
    where a considered dark identity has room to differentiate without
    fighting the category's core use case.
  Fit for this app: Strong — this is precisely the confirmed Character
    Brief's territory, and it's currently unclaimed by the one competitor
    with confirmed visual evidence.
  Risk: Dark UI can read as harder to use in bright daylight (the exact
    context barcode-scanning happens in) — worth verifying the scan
    camera screen itself remains legible in direct sunlight regardless of
    the app's dark theme choice (a real UX risk, not just an aesthetic one).
  Claim strategy: (1) Ship the score-reveal glow moment (§DM5/§DP3) as the
    single most memorable differentiator — nothing in the confirmed
    evidence suggests any benchmark has an equivalent; (2) lean into the
    metabolic/Biolism depth as a visual extension of "considered" (data
    density done with craft, not just density); (3) keep the OLED-gold
    identity consistent across every touchpoint, since it's currently the
    strongest asset the app already owns.

§DCP2 Positioning Matrix (axes: Clinical/data-dense ←→ Warm/atmospheric, ×
  Mass-market simplicity ←→ Considered depth):
    Yuka:              Mass-market simple, closer to Clinical-neutral
                        (light, score-forward, not warm)
    Cronometer:         Considered depth, Clinical (data-table-dense)
    Open Food Facts:    Mass-market simple, Clinical (utilitarian)
    Scan'eat (current): Considered depth (Biolism, personal scoring), but
                        pulled toward Clinical by the generic-icon/flat-
                        surface execution gaps already identified
    Scan'eat (target):  Considered depth AND Warm/atmospheric — the one
                        quadrant none of the three benchmarks credibly
                        occupy

§DCP3 Visual Differentiation Opportunities

Differentiator: Dark, warm, "glowing" visual identity vs. the category's
  light/score-forward convention
Current state: Palette exists and is genuinely distinctive; execution
  (icons, motion, elevation) doesn't yet deliver on it
Target state: The confirmed Character Brief, fully realized per §DP3
Effort: Medium (mostly already-scoped fixes from earlier sections)
Competitive value: Differentiates from Yuka specifically (confirmed light-
  background identity) and, if the general-knowledge read on Cronometer/OFF
  holds, from the entire category's light-mode default

Differentiator: A genuine "moment of delivery" (the score reveal) with its
  own motion signature
Current state: Absent — renders like every other state change in the app
Target state: §DM5's glow-reveal recommendation
Effort: Medium
Competitive value: [UNVERIFIED — no confirmed evidence either way for any
  benchmark's reveal-moment treatment] but a signature reveal moment is, by
  the skill's own logic, inherently hard to copy regardless of what
  competitors do, since it only works if it's unique to one product
```

---

## PHASE 1 COMPLETE

All 21 steps of the design-aesthetic-audit-SKILL.md "full deep" Phase 1 path
are covered above: §0 → §DS1-2 → §DP0-2 (confirmed by user) → §DBI1+3 →
§DC1-5 → §DT1-4 → §DCO1-6 → §DH1-4 → §DSA1-5 → §DM1-5 → §DI1-4 → §DST1-4 →
§DCVW1-3 → §DIL1-3 (skipped, no illustrations) → §DDV1-3 (folded into
§DST2/§DH1, no separate finding) → §DTA1-2 → §DRC1-3 (skipped, phone-only
fixed layout) → §DDT1-2 → §DP3 → §DBI2 → §DCP1-3.

Phase 2 (expanded UI audit from app-audit-SKILL.md: §E1-10, §F1-6, §G1-4,
§H3, §L3-5, §D5) — user has now authorized starting this phase.

---

# PHASE 2 — EXPANDED UI AUDIT (from app-audit-SKILL.md)

Per the design-aesthetic-audit-SKILL.md "full deep" scope convention, Phase 2
pulls in every UI-adjacent section from app-audit-SKILL.md. Where a Phase-2
item substantially overlaps a Phase-1 finding, this cross-references rather
than duplicates it (matching the two skills' own §COMPANION section-mapping
table). New evidence and genuinely new findings are called out explicitly.

## CATEGORY E — VISUAL DESIGN QUALITY & POLISH

```
[CROSS-REFERENCE — no new finding needed]
§E1 Design Token System        → see Phase 1 §DTA1-2, §DC2 (token layers,
                                  find-and-replace test)
§E2 Visual Rhythm & Spatial     → see Phase 1 §DH1-4 (focal point, chroma
                                  contrast); spacing-scale specifics remain
                                  an acknowledged scope gap in both passes
§E3 Color Craft & Contrast      → see Phase 1 §DC1-5, §DBI3 (perceptual
                                  architecture, dark-mode quality, WCAG
                                  contrast not independently re-verified
                                  numerically in either pass — flagged below
                                  as the one genuinely open item)
§E4 Typography Craft            → see Phase 1 §DT1-4 (type personality,
                                  scale, tabular figures, tracking)
§E6 Interaction Design Quality  → see Phase 1 §DM1-5, §DP3 (motion
                                  vocabulary, signature moment)
§E7 Overall Professionalism     → see Phase 1 §DP0-2, §DBI1-3 (character
                                  extraction, anti-genericness)
§E9 Visual Identity             → see Phase 1 §DBI2-3, §DCP1-3 (design DNA,
                                  competitive whitespace)
§E10 Data Storytelling          → see Phase 1 §DH1 (score ring focal
                                  weight) — no dedicated chart library in
                                  use, so most §E10 chart-specific items
                                  (axis labeling, chart-type fit) don't apply
```

```
[HIGH] — WCAG numeric contrast ratios have not been independently verified
  for any color pair in the app
Dimension: §E3 (WCAG contrast compliance), §G1 (text contrast)
Finding: Phase 1's color evidence gives exact hex values for every text/
  background pairing (e.g. cream 0xFFEFEAE6 on OLED 0xFF0F0D12; TextSecondary
  0xFF7E859E on the same background; FlagRed/AmberWarning banner text at 15%
  alpha fills), but no pass in this audit has computed actual contrast
  ratios against the WCAG 4.5:1 (normal text) / 3:1 (large text, UI
  components) thresholds.
Why it matters: This is the single most consequential unverified claim in
  the entire audit so far — a beautiful, well-considered palette can still
  fail basic legibility if a specific pairing (most likely candidate:
  TextMuted 0xFF454A60 on OLED background, or banner text over 15%-alpha
  colored fills) falls under 4.5:1. Given A2=High-stakes/Emotional (health/
  allergen data), a real contrast failure here would be a correctness bug,
  not a taste question.
Recommendation: Compute actual contrast ratios for the ~6 most-used text/
  background pairs (primary text/OLED bg, secondary text/OLED bg, muted
  text/OLED bg, banner text/each 15%-alpha fill) before shipping any of the
  Phase 1 recommendations that touch these colors. This is a verification
  task, not a design task — flagging it rather than asserting pass/fail
  without having actually computed it.
Effort: LOW (a contrast-ratio calculator pass, no design change unless a
  failure is found)

[MEDIUM] — Per-component state completeness (§E5) not fully audited beyond
  buttons/cards (already covered in Phase 1 §DCO1-6)
Dimension: §E5
Finding: Phase 1 covered buttons, cards, inputs, navigation, modals in
  detail. Not directly evidenced this session: checkbox/radio/switch/slider/
  dropdown component usage and state-completeness (the app may not use all
  of these — e.g. no evidence of a slider anywhere; toggle switches exist
  in Settings and Reminders but weren't independently re-verified for their
  5-state completeness beyond the §DP3 "character-neutral" flag on switches).
Recommendation: Not a new finding — restating the §DP3 unverified item
  (settings toggle Switch coloring) as the one open component-state question,
  rather than asserting new problems without evidence.
Effort: N/A (already scoped)

[MEDIUM] — Material You / Dynamic Color not used; static palette confirmed
  high-quality, so this is a deliberate, defensible choice — not a gap
Dimension: §E11 (Material You / Dynamic Color)
Finding: Confirmed in Phase 1 (§DC/§DS): no DynamicColors API usage
  anywhere; all three theme schemes (OLED/Dark/Light) are hand-authored
  static palettes.
Why it matters: Per §E11's own guidance — "if not supported, verify the
  static palette is high quality" — Phase 1 already established the static
  palette IS high quality (deliberate, non-default, CVD-safe). Given the
  confirmed Character Brief explicitly wants a specific warm-gold identity
  (not a user-wallpaper-derived one), opting out of Dynamic Color is
  correct, not a gap — Dynamic Color would directly undermine the "owned"
  identity Phase 1 spent most of its effort trying to strengthen.
Recommendation: None — protect this decision, don't add Dynamic Color.
Effort: N/A

[LOW] — Splash screen theme hardcoded black regardless of in-app theme
  choice (same root cause as the §DS2 system-chrome finding)
Dimension: §E11 (Splash screen quality)
Finding: `Theme.ScanEat.Starting` (themes.xml) is hardcoded black, same as
  the main `Theme.ScanEat`'s native Android theme layer — consistent with
  the already-identified §DS2 finding that system chrome doesn't track the
  in-app light/dark/OLED picker (it's Compose-only theming).
Why it matters: Minor, same root cause already documented — not a new
  problem, just its most visible symptom (a user who selects Light theme
  still sees a black splash screen on every cold start).
Recommendation: Fold into the same future fix as the §DS2 finding (make
  system-level theme, including splash, follow the in-app picker) rather
  than fixing splash alone.
Effort: LOW-MEDIUM (requires reading the in-app theme preference before
  Activity Compose content is set, which has real Android lifecycle
  constraints — worth scoping carefully, not a one-line fix)

[UNVERIFIED] — RTL layout quality (§E11) not re-audited this session
Finding: RTL support was added earlier in this project's history (task
  #23, P-3). Not independently re-verified as part of this design audit
  pass — no regression assumed, but also not re-confirmed.
Recommendation: None at this time — noting the gap rather than asserting
  either a pass or a regression without re-testing.
```

Section E complete — appending to design_audit.md.
