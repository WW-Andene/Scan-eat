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
