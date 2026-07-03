# ADR-0005 — Test stack is `node --test --experimental-strip-types`, per-feature test files

**Status:** Accepted · **Tier:** 1 · **Date:** 2026-04-22

## Context

Needs for the test harness:

1. Strip TypeScript without a compile step. Contributors can write a
   `*-tests.ts` file, import from `public/...` JS modules, and run.
2. Zero additional dev dependencies if possible — the repo already has
   `esbuild` for the engine bundle; adding Vitest / Jest would increase
   install size + onboarding friction.
3. Parallel test-file isolation to avoid cross-file state pollution (e.g.
   the localStorage polyfill shared across tests).
4. Fast — `npm test` should run in under 2 seconds on a laptop.

## Options considered

### Option A — `node --test --experimental-strip-types`

**Pros**: native. No dep. Handles TS. Parallel isolation by default.

**Cons**: the `--experimental-strip-types` flag is still flagged
experimental as of Node 22 — could be renamed or changed in minor
versions. **Minimum runtime: Node 22.6** (the version that landed
the flag; Node 20 does not support it). Pinned via the
`"engines": {"node": ">=22.6"}` field in `package.json` and the
matrix in `.github/workflows/test.yml`.

### Option B — Vitest

**Pros**: rich assertion API, watch mode, snapshot testing, vi.mock.

**Cons**: dev dep. Slightly slower cold start.

### Option C — Jest + ts-jest

**Pros**: industry standard.

**Cons**: meaningful dev-dep footprint. ESM handling still rough. Slower
than Vitest.

### Option D — esbuild-based harness (bundle tests then run)

**Pros**: uses existing esbuild.

**Cons**: custom code to maintain for no real benefit over Option A.

## Decision

**Option A.** `npm test` = `node --test --experimental-strip-types
<list of *-tests.ts>`. All test files live at the repo root (not in a
`tests/` subfolder) for discoverability next to the module they cover.

Convention: tests per feature are in their own file
(`<feature>-tests.ts`), not lumped into a mega-file. `presenter-tests.ts`
is the only consolidated file because it covers many small helpers in
`public/core/presenters.js`.

localStorage polyfill is duplicated at the top of each test file that
needs it (custom-food-db, day-notes, fasting-history, meal-plan,
app-settings). ~9 lines, simpler than extracting to a shared module.

## Consequences

- Adding a test file is a one-line change in `package.json` `scripts.test`.
- No watch mode — contributors re-run `npm test` manually. The full suite
  is fast enough (~1s) that this is fine.
- A Node minor-version change that renames or removes
  `--experimental-strip-types` forces a migration. This is the biggest
  lurking risk and is flagged in `ASSUMPTIONS.md`.
- Tests import real modules from `public/core/*.js` and
  `public/features/*.js` — refactors that break those imports show up
  immediately.
- Features with meaningful browser-only dependencies (IndexedDB, fetch,
  DOM) are tested only at the pure-function layer. Integration tests
  would require Playwright or jsdom; deferred.

## Reversal cost

Low. A move to Vitest would be: `npm install -D vitest`; rename
`describe/it` (already compatible); update `scripts.test`. Individual
test files barely change.

## Revisit trigger

- Node deprecates `--experimental-strip-types`.
- Real browser-integration tests needed for a critical flow (e.g. SW
  behaviour, IDB migrations).
- Watch mode / snapshot testing becomes necessary for contributors'
  workflow.
