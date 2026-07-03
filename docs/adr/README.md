# Architecture Decision Records

Per `app_development_SKILL.md §I.4` — one file per Tier-2 (expensive to
reverse) decision + Tier-1 decisions whose rationale is non-obvious.

## Index

| # | Title | Status |
|---|-------|--------|
| [0001](0001-persistence-layer-indexeddb.md) | Persistence layer is IndexedDB, not SQLite or a remote DB | Accepted |
| [0002](0002-llm-provider-groq-dual-mode.md) | LLM provider is Groq (Llama 4 Scout), two-mode | Accepted |
| [0003](0003-data-provenance-pipeline-over-checked-in-data.md) | Data-generation pipelines shipped as infrastructure | Accepted |
| [0004](0004-features-folder-pattern.md) | Feature-folder pattern under `public/features/` | Accepted |
| [0005](0005-test-stack-node-test-experimental-strip-types.md) | Test stack is `node --test --experimental-strip-types` | Accepted |
| [0006](0006-engine-version-independence.md) | Scoring engine version is independent of `package.json` version | Accepted |

## Writing a new ADR

- File name: `<NNNN>-<kebab-case-title>.md`. NNNN is zero-padded, monotonic.
- Status: `Proposed` → `Accepted` → (optionally) `Superseded by #NNNN`.
- Structure: Context · Options considered · Decision · Consequences ·
  Reversal cost · Revisit trigger.
- Keep it short. An ADR that can't fit in a single screen isn't an ADR, it's
  a design document.

## Relationship to `docs/DECISIONS.md`

`DECISIONS.md` is the **running log** of every Tier-1 + Tier-2 decision in
ADR-lite format (one short block per entry). ADRs here are promoted from
the log when the decision is foundational enough that a new contributor
should read it on day one.

Rule of thumb: if the decision drives the shape of the whole repo (where
data lives, which LLM, how features are structured, how tests work), it
gets its own ADR. If it's a one-feature call ("Pantry search has its own
endpoint" or "Close-the-gap closes half the deficit, not all"), it stays
in `DECISIONS.md`.
