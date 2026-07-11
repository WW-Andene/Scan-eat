# ASSUMPTIONS

- "Push commit" = commit to `claude/project-setup-roadmap-2bbp58` and push,
  per this session's standing branch mandate — not open a PR (user hasn't
  asked for one this loop).
- "Repeat" = keep rotating categories and shipping verified fixes without
  re-confirming after each round, consistent with the autonomous-lead-dev
  mandate already established this session. I'll stop the loop and report
  if I hit a [STUCK] (3-retry failure) or run out of clearly scoped findings.
- CI (android-build.yml on GitHub Actions) is the verification gate for
  code/logic changes, per this session's established fix-forward discipline.
- User override (this loop only): batch rounds 4-10 without a CI check
  between each; verify once after round 10, then fix-forward if red.
