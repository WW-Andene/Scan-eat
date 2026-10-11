# Changelog

All notable user-facing changes to the Scan-eat Android app are documented here,
starting from the point this file was introduced (versionCode 2 / 1.1.0). Changes
before this point are only in the git history — see `git log` for that period.

## [1.1.0] - versionCode 2

First version bump since the app's initial `1.0.0` (versionCode 1), which had been
frozen through 20+ prior fix commits. Highlights from that unversioned period,
kept brief since they were never tied to a release number at the time:

- Fixed several domain-logic/scoring correctness bugs (category classification,
  hormone-estimate discontinuities, value-score wiring).
- Fixed state/data-integrity bugs (frozen "today" calculations, inconsistent
  week definitions, an undersized touch target).
- Threading/process-death fixes in the scan flow (camera capture moved off the
  main thread, photo queue persisted to disk).
- Accessibility fixes (TalkBack chart/bar announcements, keyboard submit
  actions, reduced-motion gating).
- Network resilience fixes (captive-portal detection, clearer unreachable-
  server messaging).
- Error-handling consistency fixes and a drift-check gap closed between the
  Android client and the server's hand-mirrored scoring logic.
- Chart accessibility parity across Dashboard/Activity/Hydration weekly charts,
  and a locale bug in Expenses' date formatting.
- Micronutrient value coercion (a hallucinated/misread AI-parsed label value
  could previously corrupt diary totals with no ceiling) and a float-precision
  fix in budget over/under comparisons.
- Undo-on-delete parity for Diary (previously only Weight/Activity offered it)
  and a memoization fix in the Grocery screen.

Going forward, add a new dated entry here for each release.
