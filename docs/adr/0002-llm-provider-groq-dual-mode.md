# ADR-0002 — LLM provider is Groq (Llama 4 Scout), two-mode

**Status:** Accepted · **Tier:** 2 · **Date:** 2026-04-22

## Context

Several features need a vision-capable LLM: label OCR, single-food photo
identification, multi-item plate identification, restaurant-menu scanning,
chef-style recipe suggestions, pantry-first recipe search. The provider
choice drives cost, latency, reliability, and — in server mode — the
deployer's liability surface.

Two deployment modes are both useful:

- **Server mode**: deployer holds the API key; users don't configure
  anything. Best UX.
- **Direct mode**: user pastes their own API key; the deployer handles zero
  traffic. Best for cost-control and privacy-strict deployments.

## Options considered

### Option A — Groq (`meta-llama/llama-4-scout-17b-16e-instruct`)

**Pros**: OpenAI-compatible API · vision + text · free tier covers typical
solo deployments · low p50 latency · open-weights model (future flexibility).

**Cons**: newer model than GPT-4o or Claude; long-tail reliability not as
established. No fine-tuning available on the free tier.

### Option B — OpenAI (GPT-4o / GPT-4o-mini)

**Pros**: incumbent, battle-tested.

**Cons**: no free tier — direct-mode users pay per scan from the first
request. Server-mode deployers pay immediately.

### Option C — Anthropic Claude (claude-sonnet-4-6 etc.)

**Pros**: excellent JSON adherence, strong vision.

**Cons**: no free tier; higher per-token cost than Groq; vision throughput
lower than Groq at comparable quality for this task.

### Option D — Self-hosted OSS (Llama via Ollama)

**Pros**: zero ongoing cost · full data control.

**Cons**: requires a GPU host · not a PWA feature anymore; changes the
distribution story; raises the operational bar significantly.

## Decision

**Option A — Groq, dual mode.** Default server-mode uses the deployer's
`GROQ_API_KEY`. Users can switch to direct-mode from Settings and paste
their own key; requests then go from the browser directly to
`api.groq.com/openai/v1/chat/completions` via `ocr-parser.ts`'s
`callGroqVision` helper.

## Consequences

- `api/*.ts` Vercel functions exist for every vision feature
  (`identify.ts`, `identify-multi.ts`, `identify-menu.ts`,
  `suggest-recipes.ts`, `suggest-from-pantry.ts`). They are thin wrappers
  over the TypeScript parser.
- `build.mjs` also exports the same functions from `ocr-parser.ts` into
  `public/engine.bundle.js` for direct-mode calls.
- System prompts are authored in French (the audience default) and instruct
  the model to return JSON only. `extractJSON()` strips accidental markdown
  fencing defensively.
- `response_format: json_object` is intentionally NOT set on vision calls
  because Groq's multimodal endpoint rejects it. Text-only calls like
  `suggestRecipes` could set it but currently don't (consistent behaviour
  across the parser is simpler).
- Rate-limit 429s are not yet handled as a first-class error in
  `api/*.ts`. Flagged in `ASSUMPTIONS.md` ("traffic will stay on free-tier
  limits").

## Reversal cost

Low. The model + endpoint are set in two constants (`DEFAULT_MODEL`,
`DEFAULT_ENDPOINT` in `ocr-parser.ts`). Swapping to another
OpenAI-compatible endpoint is a URL change + a model-name change. Prompts
might need tuning per model but the schemas don't change.

## Revisit trigger

- Groq free-tier terms change materially (quota cut, paid-only vision,
  retention-policy shift).
- Sustained quality regression on food identification — tracked via the
  `confidence` self-report in the response.
- Cost concerns at scale — direct mode already routes around this; a
  server-mode move to a cheaper provider would be the next step.
