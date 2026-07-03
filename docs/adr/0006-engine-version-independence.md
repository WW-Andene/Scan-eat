# ADR-0006 — Scoring engine is independently versioned from the package

**Status:** Accepted · **Tier:** 1 · **Date:** 2026-05-01

## Context

`scoring-engine.ts` exports `ENGINE_VERSION = '2.0.0'` while
`package.json` carries `"version": "0.2.0"`. The two have drifted
intentionally during development but the rationale was never written
down, so newcomers have repeatedly asked whether the mismatch is a
bug.

The two versions track different concerns:

- **`package.json#version`** tracks the app shell (PWA + Capacitor
  wrapper + UI). It bumps on user-visible release cuts, not on every
  scoring tweak.
- **`ENGINE_VERSION`** is stamped into every `ScoreAudit` object that
  gets persisted to IDB scan-history. Users compare scans across time
  ("did this Snickers really score 3 points lower last week?"). The
  engine version is the only durable signal of "the math changed",
  independent of when the app shell happened to release.

## Decision

Keep them independent. Bump rules:

- `ENGINE_VERSION` MAJOR — pillar weights change, a pillar is added
  or removed, grade breakpoints move. Old audits in IDB are
  considered un-comparable to new ones; UI may surface a "scoring
  changed" badge when displaying historical audits with an older
  major.
- `ENGINE_VERSION` MINOR — a new bonus / penalty rule lands, a
  threshold is tuned, a new category or additive enters
  `ADDITIVES_DB`. Audits are still comparable; rounding may differ
  ±1.
- `ENGINE_VERSION` PATCH — bug fix that does not change the scoring
  contract for any product (e.g., a regex typo, a cast cleanup).

`package.json#version` follows normal semver against the app surface
(routes, IDB schema, manifest fields, exported feature modules).

The `ENGINE_VERSION` constant is asserted by a test
(`tests/engine-version.tests.ts`, planted in Phase 0 of the
2026-05-01 cleanup). When you intend to bump it, change the test in
the same commit so the reason for the bump is in the diff.

## Reversal cost

Trivial. Either could be brought back into lockstep at any time;
the cost is permanently losing the ability to disambiguate "engine
math changed" from "we shipped a new icon". Nothing depends on them
being equal.

## Revisit trigger

If we decide IDB-stored audits should always be re-scored on read
(invalidating the durable-version reason), this ADR becomes moot
and the engine can fold into the package version.
