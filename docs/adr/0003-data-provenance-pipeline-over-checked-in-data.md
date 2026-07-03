# ADR-0003 — Data-generation pipelines shipped as infrastructure, not as one-shot scripts

**Status:** Accepted · **Tier:** 1 · **Date:** 2026-04-22

## Context

Two data files shape the app's user experience: `public/data/food-db.js`
(CIQUAL 2020 nutrition) and `public/data/pairings.js` (Ahn 2011 recipe
corpus). Both derive from external, peer-reviewed sources that update on
their own cadence (ANSES publishes new CIQUAL versions every few years;
Ahn 2011 is a 2011 artefact, stable).

Early iterations of both files were hand-transcribed from memory, with
speculative attribution in the header comments. That's the
authority-fabrication failure mode the app is otherwise scrupulous about
(`explanations.js` cites specific EFSA / IARC / WHO documents, for
example).

## Options considered

### Option A — Ship only the data files, regenerate rarely

**Pros**: lowest repo size.

**Cons**: no audit trail for how the current values were produced. Next
maintainer has to guess. Encourages hand-editing when a user reports a
diff.

### Option B — Ship the generator script alongside the data

**Pros**: reproducibility is traceable — anyone can re-run the generator
and diff the output. Future refresh is a one-liner. Anchors the data to
its source.

**Cons**: slightly more to maintain. The generator script must stay in
sync with the data module's schema.

### Option C — Generate at build time, don't commit the data

**Pros**: data is always "fresh".

**Cons**: build becomes network-dependent (ANSES / Zenodo / GitHub must
be reachable). Breaks sandboxed / offline builds. Puts the deployer on
the hook for handling upstream outages at build time.

## Decision

**Option B — ship generators + committed output.** The committed output is
the source of truth for the running app; the generator is the source of
truth for how that output was produced.

Location convention: `tools/generate-<topic>.mjs` produces
`public/data/<topic>.js`. The generated file's header cites the source
+ the generator path. SHA-256 pinning of the input (see
`tools/generate-pairings.mjs`) catches silent mirror swaps.

## Current pipelines

| Pipeline | Input | Output | Notes |
|----------|-------|--------|-------|
| `tools/generate-pairings.mjs` | `/tmp/flavor-network/s3.csv` (Ahn 2011 supplementary, fetched once) | `public/data/pairings.js` | SHA-256 pin on input; refuses to run on mismatch. |
| `tools/generate-food-db.mjs` | ANSES CIQUAL 2020 XML zip (fetched at runtime) | `public/data/food-db.js` | Not yet run in a network-capable environment. Current `food-db.js` is hand-transcribed with a visible `⚠️ PROVENANCE NOTICE` in the header. |
| `tools/extract-ciqual-codes.mjs` | OpenFoodFacts' `taxonomies/food/ingredients.txt` | `tools/_ciqual-codes.json` | Indexes ~1,950 FR aliases → 724 unique CIQUAL codes; supports verifying generated food-db entries against canonical codes. |
| `tools/annotate-food-db.mjs` | existing `food-db.js` + `_ciqual-codes.json` | stdout JSON report | Dry-run tool that reports CIQUAL-code match confidence per entry. |

## Consequences

- Re-running a generator in an unrestricted environment should produce
  bit-for-bit the same output (modulo `generated_at` and timestamped
  metadata). Diffs on the data file during PR review are legible.
- Hand-editing a generated file is a code smell — the next generator run
  will clobber it. The file header says "GENERATED — do not edit by hand".
- The authority-verification test lives in the generator, not in the data
  file itself. For pairings: the SHA-256 pin is the test. For food-db:
  every row carries `ciqual_food_code` so the user can verify at
  `https://ciqual.anses.fr/#/aliments/<code>`.

## Reversal cost

None — this ADR records an existing pattern; reversal would mean stopping
future generators from being committed, which doesn't delete prior work.

## Revisit trigger

- A generator outgrows the "one file per pipeline" shape (e.g. needs a
  multi-stage build).
- Upstream source disappears — at which point the pinned-hash local copy
  becomes the only truth.
