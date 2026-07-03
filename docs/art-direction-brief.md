# Art Direction Brief

Canonical one-page brief, produced per `art-direction-engine-SKILL.md §BRIEF`.
Every subsequent design decision in Scan'eat flows from this document.
Complementary to `docs/design-system.md` (the implementation) and
`docs/audit-v3/SUMMARY.md` (the 2026-04 audit that reviewed it end to
end).

---

```
━━━ ART DIRECTION BRIEF ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SUBJECT:          Scan'eat — food-scan + nutrition-tracking PWA.
                  Photograph a label → 0-100 score + A+..F grade.
                  Also: meal logging, recipes, hydration, fasting,
                  activity, weight, progress charts.

AUDIENCE:         Health-curious consumers, 20–50, mobile-first,
                  moderate-frequency daily use (1–5 scans/day).
                  Not clinicians, not athletes, not tech-first
                  early-adopters. Emotional context: wants to
                  improve without feeling scolded.

EMOTIONAL TARGET: "The focused calm of a scientist's notebook at
                   a farmer's market" — precise but warm, data-
                   forward but never cold, grades a product
                   without shaming the person who ate it.

VALUES:           • Respects your intelligence (tabular numerics,
                    exact values, no rounding to lies)
                  • Warm in grading, not punitive
                  • Eye to color-blind and low-vision users from
                    day 1 (Atkinson Hyperlegible, pattern overlays)
                  • Local-first, private (IndexedDB; nothing
                    leaves your device unless you scan through
                    the server)

VISUAL CONCEPT:   Warm cream paper under coral ambient light,
                  hand-ruled nutrient tables with penciled emoji
                  margin glyphs, coral-ink smudges where the
                  writer paused. Paper has visible tooth.

─── PALETTE ──────────────────────────────────────────────────
  Background:     oklch(67% 0.20 16) — calm coral gradient
                  (--bg #E84A5F → --bg-deep #D94458 dark theme;
                   --bg #F6D0D6 → --bg-deep #F0B7BF light theme)
  Surface 1:      oklch(20% 0.005 280) dark / oklch(99% 0.005 90) light
                  (--panel #1B1B1F / #FFFDF7)
  Surface 2:      oklch(25% 0.008 280) dark / oklch(95% 0.01 80) light
                  (--panel-2)
  Text primary:   oklch(95% 0.015 80) dark / oklch(20% 0.005 280) light
                  (--text #F5F0E8 / #1B1B1F — never pure white/black)
  Text secondary: oklch(65% 0.01 80) (--muted #9A948B / #5B564A)
                  Chromatic brown-gray, not neutral gray.
  Accent:         oklch(65% 0.20 40) — warm orange
                  (--accent #FF6B45 dark / #B0431F light)
                  Role: primary CTAs + active states.
  Accent-dim:     derived — 8% chroma drop, 6% lightness drop
                  (--accent-dim, used for ledger underline + rule)
  Error:          oklch(60% 0.20 20) — coral-red, NOT generic red
                  (--danger #E54B5E, harmonically related to --bg)
  Success:        oklch(80% 0.22 140) — lime-green
                  (--success #6BE584, chromatic not flat)
  Warning:        oklch(72% 0.17 60) — amber
                  (--warning #F5A64B)
  Grade palette:  6-stop diverging green → red
                  (--grade-aplus..f, with pattern overlays)
  Temperature:    Warm throughout. No cool grays anywhere.

─── TYPOGRAPHY ───────────────────────────────────────────────
  Display font:   Atkinson Hyperlegible — designed for low-vision +
                  dyslexia readers, high letter distinction, gentle
                  curves. Optional Lexend opt-in for dyslexia users.
  Body font:      Atkinson Hyperlegible (same family — no pairing
                  drama; one typeface carries every role).
  Scale ratio:    1.200 Minor Third (tightens toward body,
                  expands to display). Tokens --text-xs..3xl =
                  11.5 / 13.9 / 16 / 19.2 / 23 / 27.7 / 33.2 px.
  Weight strategy:
                  400 body → 500 labels → 600 interactive → 700
                  display/grade. Never 300 (reads thin on coral);
                  never 800+ (reads aggressive).
  Tracking:       -0.02em on --text-xl / --text-2xl display.
                  0.08em on uppercase section labels.
                  Default elsewhere.
  Numerals:       tabular-nums + lining-nums on body (inherited
                  by every value site — no per-site override
                  needed).

─── SHAPE ────────────────────────────────────────────────────
  Radius scale:   buttons:  14–18px (--r-btn 14 / --r-md 18)
                  inputs:   10px (--r-input — tighter, data-entry feel)
                  cards:    18px tiles / 32px heroes (--r-md / --r-xl)
                  modals:   36px (--r-modal-lg — one step softer than
                            cards, per §DCO3)
                  badges:   999px (--r-pill)
  Geometry:       Rounded, consumer-warm. No sharp corners on
                  interactive chrome. Pill for chips.
  Border strategy:
                  Low-contrast hairlines via --border (dim)
                  and --border-strong (visible on dim bg).
                  1px–1.5px hairlines; 2px only on focus
                  rings and outline-style secondary buttons.

─── DEPTH & SURFACE ──────────────────────────────────────────
  Elevation:      3 steps — --elev-1 (cards + primary buttons),
                  --elev-2 (hover / popovers / toasts),
                  --elev-3 (modals). Optional --elev-1-tonal
                  uses coral hue at low alpha for character-
                  positive shadow.
  Shadow color:   rgba(0,0,0,0.06..0.32) neutral + rgba(232,74,95,
                  0.08..0.14) tonal option. Dark mode adds
                  inset 0 1px 0 rgba(255,255,255,0.06) rim
                  highlight (light physics — dark surfaces
                  catch light on the top edge).
  Material:       Paper dominant — cream panels, 2% SVG grain
                  overlay, shadow cast from a fixed overhead light.
                  Light tertiary — grade-palette glow.
                  Glass demoted — dialog backdrop uses tinted
                  coral paper, not frosted iOS sheet.
  Texture:        2% paper grain (body::before, 0.025 rest →
                  0.05 dialog-open via :has(dialog[open])).
                  Bumped to 0.035 at ≥1024px so desktop reads
                  as paper sheet, not empty void.
  Light source:   Top, diffuse, warm. Every shadow drops
                  straight down. Dark mode reverses to
                  "looking up at a diffuse ceiling light."
  Backdrop:       Coral gradient `--bg → --bg-deep` with paper
                  grain overlay + dialog::backdrop uses
                  --scrim-top / --scrim-bottom tokens + 12px
                  glassmorphism blur via --blur-glass.

─── MOTION ───────────────────────────────────────────────────
  Character:      Snappy + expressive (intensified on signature
                  moments, silent elsewhere).
  Micro:          --motion-fast 100ms ease-out — button/toggle
                  scale-press (scale(0.96)).
  Entrance:       --motion-enter 220ms ease-out — dialog open
                  via @starting-style, skeleton shimmer, wbar
                  growth.
  Exit:           --motion-exit 180ms ease-in (faster than
                  enter, per motion physics).
  Page/nav:       --motion-nav 260ms ease-in-out for page-level
                  transitions (future use).
  Loop:           --motion-loop 1000ms — voice-listening,
                  scanning-dots, pulse indicators.
  Easing:         --ease-ui cubic-bezier(0.2, 0.8, 0.2, 1) for
                  every transition. One easing everywhere.
  Signature:      Grade-chip reveal — the A+/F badge scales
                  from 0.94 to 1.0 over 220ms when a new scan
                  lands. Opt-in via .grade-chip-reveal so re-
                  opening history is silent.
  Reduced motion: One @media block kills every animation +
                  transition if the user requests it; also
                  respected via body.reduce-motion toggle.

─── ICONS ────────────────────────────────────────────────────
  Library:        Unicode emoji (OS-native stack: Apple Color,
                  Segoe UI, Noto, Twemoji fallback). Zero
                  icon fonts, zero SVG sprite sheets.
  Style:          Colour-filled. Platform-dependent rendering
                  is accepted as intentional (we ride the OS
                  stack).
  Personality:    "Calibrated Signature" position — the house
                  rule "one recognisable emoji per feature"
                  (📅 planning, 🥕 pantry, 🛒 grocery, 🎯 gap-
                  closer, 💡 recipe ideas, 📜 menu scan, 📸
                  photo, 📱 install) IS the signature element.
  Calibration:    .icon-glyph helper + [data-icon] auto-apply
                  normalise size (1.08em) + baseline (-0.08em)
                  + line-height (1) + font-family.

─── COMPONENTS ───────────────────────────────────────────────
  Buttons:        Primary = --accent filled, 56px min-height,
                  --elev-1 + --elev-2 hover, scale(0.98) active.
                  Secondary = transparent outline, --border-strong.
                  Destructive = .btn-destructive (never red at
                  rest, red on hover/focus only).
                  Loading = .btn-loading (holds width, pulses a
                  dot).
  Cards:          --panel bg, --r-md/--r-xl radius, --sp-5
                  padding, --elev-1. Hero .score-card also
                  carries the notebook-margin rule +
                  grade-hued focal glow on #grade-el.
  Inputs:         Global baseline — --input-bg, 1.5px --border,
                  --r-input 10px, 44px min-height. ::placeholder
                  bound to --muted.
  Navigation:     No traditional nav bar — the dashboard's
                  .dashboard-actions chip-btn row is the de-
                  facto nav. Feature-level drill-down happens
                  in modals. Forward-compat [aria-current]
                  styling ready.
  Empty states:   Dashed paper-tile treatment (.dash-entry-empty,
                  .tpl-empty, .add-to-recipe-empty) + CTA slot
                  for an inviting action + voiced copy
                  ("Pas encore de recette — crée-en une, scanne
                  une photo ou importe depuis un lien.").
  Loading:        Skeleton with coral-tinted shimmer + 2%
                  paper-grain overlay (not generic grey-on-grey).
  Errors:         Three severity variants — .error-panel,
                  .warn-panel, .info-panel — with color-mix
                  tinted backgrounds + left-rail accents. Never
                  iOS-alert harsh. Toast variant uses a 4px
                  inset stripe (ok/warn/error) on --panel bg.

─── IDENTITY ─────────────────────────────────────────────────
  Signature element:
                  The .score-card notebook-margin rule — a 2px
                  --accent-dim vertical band on the inner-left
                  edge of the hero scan card — combined with
                  the grade-letter pattern overlay on every
                  grade badge. Together they plant the flag on
                  "scientist's notebook at a farmer's market."
  Competitive position:
                  Warm × Data-dense. Unoccupied by Yuka
                  (warm-curated), MyFitnessPal (neutral-dense),
                  Cronometer (clinical-dense), Noom (psych-curated).
                  Scan'eat owns this quadrant.

─── PROPORTIONS ──────────────────────────────────────────────
  Layout model:   Mobile-first vertical stack. `main`
                  max-width: 600px, margin: auto. On desktop,
                  centers a mobile-width column on paper
                  backdrop. No multi-column layouts.
  Spacing base:   4px (--sp-1). Doubling scale to 48px (--sp-8).
                  Section rhythm = --sp-5 20px. Nested content
                  = --sp-3 12px or --sp-4 16px.
  First impression (50ms):
                  "Coral paper with a cream card. The card is
                  precise. The numbers line up. The grade is
                  alive — patterned, coloured, signed with a
                  little ledger underline."
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Source of truth

When this brief and `docs/design-system.md` conflict, **the brief
wins** and the design system is amended. Both together define the
product's art direction — neither alone is the spec.

## Change protocol

1. New character-affecting decisions get written into this brief
   first, then reflected in `design-system.md` tokens / components.
2. The brief is versioned via git; any PR that changes character
   should touch this file.
3. The 2026-04 audit in `docs/audit-v3/SUMMARY.md` reviewed the
   shipped implementation end-to-end and drove the tokens / a11y /
   i18n sweep that reconciled the brief with the code.
