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
- findAdditive(eNumber, name, category) in AdditivesDb.kt is called
  independently 3x per ingredient during a single scoreProduct() run —
  ProcessingPillar.kt (2 call sites), AdditiveRiskPillar.kt (2 call sites,
  incl. countTier1Additives), IngredientIntegrityPillar.kt (1 call site) —
  each doing its own O(n) linear + synonym-substring scan over the ~95-entry
  ADDITIVES_DB for the same ingredient. Real wasted computation (§I4 code
  duplication), but the correct fix (memoize once per product in
  ScoringEngine.kt and thread the result through all 3 pillar functions)
  touches 4 files at once with no CI feedback loop active mid-audit-batch —
  too risky to land blind. Queued for a dedicated pass with CI available.

## Stuck
(3-strike failures land here, per skill's FAILURE & RETRY rule)

## Done
(see LOG.md for the decision trail; see git log for the actual diffs)
- reminders_channel_name mislabel fixed (round 3)
