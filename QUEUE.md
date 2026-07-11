# QUEUE

Working queue for the KILLER-mode audit loop. Categories rotate: UI, UX, Code,
Data, Old feature, New feature — never the same category twice in a row.

## In progress
(updated per round)

## Found while auditing something else (queued for a future round)
- Design-system pass: 14 IconButtons across the app (delete/dismiss/log
  in dense list rows) use 32-36dp touch targets, below the 48dp Android
  accessibility guideline. Consistent, deliberate density choice, not
  fixed here — needs visual verification before a blanket resize since
  it touches many list layouts at once. See app-audit §H3.
- scoreViaServer's "Server URL not configured" error (ScanRepository.kt)
  is still hardcoded English-only, unlike the offline/missing-API-key
  messages fixed in app-audit §F — needs `lang` threaded into
  scoreViaServer's signature to fix properly (rarer Server-mode-only path).
- Room schema export (exportSchema = true, room.schemaLocation ->
  app/schemas/) has never actually been generated/committed - no
  app/schemas/ directory exists in the repo. Needed for
  androidx.room:room-testing's MigrationTestHelper to validate
  MIGRATION_4_5/MIGRATION_5_6 against real prior schemas. Requires a
  build with Android SDK access (this sandbox has neither local Gradle
  distribution access nor ANDROID_HOME - confirmed by a failed offline
  gradlew fetch) - generate once via CI or a real dev machine and commit
  app/schemas/.
- HealthConnectRepository.writeWeight() still creates a brand-new
  WeightRecord on every call that reaches it (now gated to only fire when
  the value actually changed - see App-audit §B1/L3 - but a genuine
  correction to an already-corrected day still leaves the prior record
  behind). The correct full fix is Health Connect's clientRecordId/
  clientRecordVersion upsert mechanism (Metadata.manualEntry(...) in
  current docs), but this exact pinned alpha07 dependency already broke
  once this session on Metadata's factory-method shape (see O/L1 queue
  entry + libs.versions.toml comment) - guessing the alpha07-specific
  constructor blind, with no CI feedback loop active mid-batch and no
  local Android SDK to compile-check, risks repeating that break. Revisit
  once either (a) a verified newer health-connect-client version is
  adopted, or (b) a CI-backed session can iterate on the exact API shape.
- OcrParser's ChatRequest never sets response_format: {"type":"json_object"}
  (Groq/OpenAI-compatible JSON mode) despite relying entirely on prompt
  instructions + a manual extractJson() markdown-stripping regex fallback
  to get valid JSON back - JSON mode would eliminate the
  "LLM returned unparseable JSON" failure class at the API level instead
  of hoping the model complies. Not applied: JSON-mode support varies by
  model on Groq, and DEFAULT_MODEL is a vision model (llama-4-scout) -
  adding this param blind risks a hard 400 on every scan if that specific
  model doesn't support it, with no way to verify against the real API
  from this sandbox (no network access to Groq, no test key). Needs a
  live-API check before adding.

## Stuck
(3-strike failures land here, per skill's FAILURE & RETRY rule)

## Done
(see LOG.md for the decision trail; see git log for the actual diffs)
- reminders_channel_name mislabel fixed (round 3)
- findAdditive() triple-computation fixed via a local memoization cache in
  AdditivesDb.kt (app-audit §I1/L3) — no call-site changes needed in any
  pillar after all; smaller and safer than the originally-queued plan.
