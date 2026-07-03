# Contributing to Scan'eat

Small, focused pull requests welcome. See `README.md §Contributing` for the
provenance rules and the basic test-file convention. This doc focuses on
**design character preservation** — the aesthetic guardrails a new PR must
not drift across.

---

## Design character — the warm × data-dense quadrant

Scan'eat occupies a specific design quadrant: **warm × data-dense**. Rivals
cluster elsewhere:

| Quadrant | Example apps |
|---|---|
| Clinical × dense | Cronometer |
| Warm × curated (low data) | Yuka |
| Neutral | MyFitnessPal |
| **Warm × data-dense** (Scan'eat) | *no direct competitor* |

The visual vocabulary that keeps the app in this quadrant:
- **Coral paper** `--bg` + **cream card** `--panel` two-tone.
- **Tabular numerics** on every data surface (`--num-feat`).
- **Diverging-scale grade badges** A+→F with color-blind pattern overlays.
- **2% paper-grain overlay** (`body::before`).
- **Emoji-per-feature signature** (one recognisable glyph per feature).

---

## Reject PRs that push toward a different quadrant

- ✗ Stripped color / grayscale dashboard → pushes clinical.
- ✗ Muted numeric precision (hiding decimals, rounding to whole numbers) →
  pushes marketing-warm.
- ✗ Frosted-glass cards, `backdrop-filter: blur` on primary surfaces →
  pushes premium-iOS-glass.
- ✗ Serif editorial headlines, thick uppercase tracking on body, neon
  accents → all belong to a different product.

Character-brief decision filters live in `docs/design-system.md §Character
tests`. When in doubt, apply them.

---

## Non-negotiables per PR

1. **Tokens.** New styles use `var(--*)` tokens. Raw `font-size`, raw hex,
   raw `NNNms` transition durations, raw box-shadows are forbidden unless
   the rule is a one-off display (grade badge font-size, etc.) — flag with
   a comment.
2. **Features preserved.** Every PR includes a one-line acceptance check
   against the 9 primary flows documented in `docs/flows.md`. If a PR
   touches a flow, include a manual-smoke line in the PR description.
3. **Tests green.** `npm run build:web && npm test` must pass before
   merge. `presenter-tests.ts` is crowded — new features get their own
   `*-tests.ts` file.
4. **Provenance.** Any new data file cites its source (see `README.md`).
5. **Privacy.** Anything that sends user data off-device must be added
   to `PRIVACY.md §What leaves the device` in the same commit.

---

## Button families (per audit F-DCO-05)

Five button classes exist; the cleanest mental model is three families:

| Family | Class(es) | When to use |
|---|---|---|
| **Icon-only** | `.settings-btn` | Gear / profile / dismiss / torch — tiny glyph-only controls. Always give an explicit `aria-label`. |
| **Pill chip** | `.chip-btn` | The workhorse. `+ .accent` = primary action; `+ .compact` = dense list row. Default for almost every CTA. |
| **Hero display** | `.capture-btn`, `.log-btn` | Large rectangular action buttons on the scan + log flow (Scanner, Logger). Not for secondary actions. |
| *modifier* | `.secondary` | Applied on top of any of the above to de-emphasize. |

If a proposed button doesn't fit these, open an issue — don't invent a
sixth family. The design-system lists three primitives (Card/Chip/Row);
buttons should stay in line with that vocabulary.

---

## Dialog shell naming (`.modal-dialog`)

Historically every modal used `class="settings-dialog"` whether or not
it was actually a settings panel (Quick-Add, Weight, Templates, and
more reuse the styling). Per audit F-CS-05 the canonical class going
forward is **`.modal-dialog`**.

For **new** dialogs:
- Markup: `<dialog class="modal-dialog" aria-labelledby="…">…</dialog>`
- Nothing else needed — `features/appearance.js initAppearance()` adds
  the legacy `settings-dialog` class at init so CSS picks up.

For **existing** dialogs: leave the class as `settings-dialog`. They'll
continue to work. A future sweep will duplicate every
`dialog.settings-dialog` selector to also match `.modal-dialog` and
drop the shim.

---

## Protected elements (do not simplify away)

Flagged by the 2026-04-24 audit (`docs/audit-v3/`) as load-bearing signature
craft. A refactor that silently removes any of these should be blocked at
review:

| Element | Location | Why it's protected |
|---|---|---|
| Paper-grain overlay | `body::before` in `styles.css` | 2% SVG noise, mix-blend-mode: overlay; honors `prefers-reduced-motion` + `prefers-reduced-transparency` |
| Grade pattern overlays | `.recent-grade[data-grade=…]` | Dots / stripes / cross-hatch distinguish grades for color-blind users (~8% of men) |
| Skeleton shimmer | `@keyframes skeleton-shimmer` + `[aria-busy="true"]` | Prevents CLS on cold dashboard load |
| Grade-reveal signature motion | `@keyframes scanneat-grade-land` | The app's decision moment — protect across refactors |
| Success-burst moments | `@keyframes scanneat-success-burst`, `scanneat-milestone-pulse` | Triggered on A+ scans + streak milestones + first-in-week 16h fast |
| Desktop two-pane (≥1100px) | `@media (min-width: 1100px)` on `main` | Capture-left / dashboard-right; mobile-first fallback |
| `<html lang>` sync on locale change | `i18n.js applyStaticTranslations` line 1585 | Screen readers + spell-checkers pick up the right language; pinned by `i18n-tests.ts` |
| reduce-motion kill-switch | 20 `@media (prefers-reduced-motion: reduce)` blocks + `body.reduce-motion` | Triple-orthogonal coverage (OS pref + app pref + body class) |

---

## Development commands

```bash
npm install
GROQ_API_KEY=gsk_… npm run dev     # serves public/ on :3000 + /api/*
npm test                            # full suite, ~2s
npm run build:web                   # regenerates engine bundle + SW cache key
```

See `README.md` for more.
