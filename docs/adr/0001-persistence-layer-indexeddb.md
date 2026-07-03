# ADR-0001 — Persistence layer is IndexedDB, not SQLite or a remote DB

**Status:** Accepted · **Tier:** 2 · **Date:** 2026-04-22

## Context

Scan'eat needs to persist: product scan history, per-entry consumption log,
weight log, activity log, recipes, meal templates, and a pending-scan queue
for offline capture. Total data per active user is small (< 10 MB) but
query patterns matter — dashboard renders join "all entries for today" with
"all weights in the last 30 days" several times per interaction.

## Options considered

### Option A — IndexedDB (single database `scanneat`, multiple stores, DB_VERSION bumps on schema changes)

**Pros**:
- Available everywhere a browser runs (including the Capacitor WebView).
- Indexes let the dashboard query `by date` efficiently.
- Transactional across multiple stores — backup/restore can write a weight
  + a consumption entry atomically.
- No dependency added.

**Cons**:
- Migration coordination across sibling modules (every data module's
  `onupgradeneeded` has to create every store it knows about, because load
  order is not guaranteed).
- API is verbose compared to a key-value store.

### Option B — localStorage only

**Pros**: simplest API.

**Cons**: no indexing, 5–10 MB hard cap, synchronous I/O stalls the main
thread, not suitable for an append-heavy log.

### Option C — SQLite via `sql.js` or similar WASM

**Pros**: real query language.

**Cons**: adds ~1 MB to bundle, needs a worker thread for async writes,
exports/imports are file-format-coupled, overkill for this data shape.

### Option D — Remote DB (Firebase / Supabase / etc.)

**Pros**: cross-device sync.

**Cons**: requires accounts, kills the "data stays on device" privacy
posture in `PRIVACY.md`, introduces a monthly cost, blocks offline-first.

## Decision

**Option A (IndexedDB)**. One database `scanneat`, seven stores under IDB
version 6: `pending_scans`, `history`, `consumption`, `weight`,
`meal_templates`, `recipes`, `activity`. Each data module (`public/data/*.js`)
defensively declares every store in its `onupgradeneeded` handler so load
order doesn't matter.

## Consequences

- Every schema change requires a `DB_VERSION` bump coordinated across
  `public/data/*.js` — the audit trail for bumps lives in commit messages
  ("IDB v5 → v6 migration: activity store + backup coverage").
- Cross-device sync is explicitly out of scope and documented in
  `ASSUMPTIONS.md`. If ever added, it would sit *above* IDB — a sync layer
  that replicates stores to a remote backend, not a replacement.
- Backup / restore is a user-initiated JSON round-trip through
  `public/backup.js`, which covers all seven stores + every
  `scanneat.*` localStorage key.

## Reversal cost

Medium. A move off IDB would require: (a) replacing every `public/data/*.js`
module's backing store; (b) a one-time migration script that reads the
live IDB and writes to the new backend; (c) updating `public/backup.js`
to read from both during a transition period.

## Revisit trigger

- Sustained user requests for cross-device sync (≥ 5 independent reports).
- IDB quota becoming a real problem on low-end devices (we'd see
  `QuotaExceededError` in telemetry).
- Browser deprecation signal for IndexedDB (none expected at publication).
