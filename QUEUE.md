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

- The entire scoring engine's Deduction/bonus "reason" text - 44 call
  sites across ScoringEngine.kt, ProcessingPillar.kt,
  NegativeNutrientsPillar.kt, AdditiveRiskPillar.kt,
  IngredientIntegrityPillar.kt, NutritionalDensityPillar.kt - is
  hardcoded English-only (e.g. "Saturated fat 12g/100g (>9 critical)",
  "NOVA class 4 base score"), and these ARE the red/green flags actually
  shown on every scan result (buildFlags() in ScoringEngine.kt reads
  d.reason directly). A French-locale user (this app's default/primary
  audience) sees English safety-flag text on every single scan,
  regardless of the in-app language setting used everywhere else. Not a
  couple of missed strings like the earlier N/L1/N/L2 findings - this is
  the entire scoring engine's flag vocabulary. The correct fix threads
  `lang` through scoreProduct() and all 5 pillar functions (breaking the
  "pure function of Product alone" signature used everywhere, incl. every
  existing test and the D/L1 fix's rescoring-in-place logic) and needs
  ~44 bilingual string pairs - too large and too central to the app to
  land correctly without local build/test verification, which this
  sandbox doesn't have. Needs a dedicated pass with CI/test feedback
  available at each step, not a single blind commit.

- Task #72 (multi-provider API key support) remains open. Investigated
  during the §XI pass: GroqApi's chatCompletions() already targets a
  plain OpenAI-compatible `/v1/chat/completions` endpoint, so the safe,
  low-risk version of "multi-provider" is letting the user point Direct
  mode at any OpenAI-compatible base URL (OpenRouter, Together, a
  self-hosted vLLM endpoint, etc.) instead of the hardcoded
  "https://api.groq.com/" in NetworkModule - no new request/response
  shape to guess at, unlike a real multi-vendor integration (Anthropic/
  OpenAI-native APIs use different schemas entirely, which WOULD require
  guessing blind here). Not implemented this pass: it still means
  reworking GroqApi from a Hilt-singleton-bound Retrofit instance to a
  dynamically-built one (same pattern ScanRepository.serverApi() already
  uses for Server mode), touching NetworkModule, DomainModule, OcrParser,
  and Settings UI - correct in scope for a real session with CI feedback
  at each step, risky to land in one blind multi-file pass.

## Stuck
(3-strike failures land here, per skill's FAILURE & RETRY rule)

## Done
(see LOG.md for the decision trail; see git log for the actual diffs)
- reminders_channel_name mislabel fixed (round 3)
- findAdditive() triple-computation fixed via a local memoization cache in
  AdditivesDb.kt (app-audit §I1/L3) — no call-site changes needed in any
  pillar after all; smaller and safer than the originally-queued plan.
- The other 5 repositories sharing ConsumptionRepository's silent-drop
  parse pattern (MealTemplateRepository, RecipeRepository,
  FastingRepository, ScanRepository, CustomFoodRepository) all got the
  same Log.w() addition — app-audit §XI coherence-fracture pass.
- ScanRepository's unused `groqApi: GroqApi` constructor parameter
  removed (dead dependency — all Groq calls actually go through the
  injected OcrParser) — app-audit §XI/2.
- scoreViaServer's "Server URL not configured" error now localized (lang
  threaded through both call sites) — app-audit §XI/3.
