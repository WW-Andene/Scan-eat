# Runbooks

Operational procedures for running, deploying, rotating, and recovering
Scan'eat. Per `app_development_SKILL.md §XIII`.

## Index

| Runbook | When |
|---------|------|
| [deploy.md](deploy.md) | Shipping a new version (web shell + Android APK) |
| [rollback.md](rollback.md) | Something's broken on prod and we need the prior version back |
| [rotate-keys.md](rotate-keys.md) | The GROQ_API_KEY leaked or is suspected compromised |
| [incident.md](incident.md) | Users report a crash / wrong data / outage |
| [data-refresh.md](data-refresh.md) | Regenerating CIQUAL + pairings from upstream sources |

## Philosophy

- **Every destructive command has a rollback next to it.** If a runbook
  tells you to run `git push --force`, it also tells you exactly how to
  undo it.
- **Commands are copy-paste.** No "roughly do X". The exact shell command
  or the exact click sequence.
- **Verification steps are falsifiable.** "Check that the site loads"
  isn't good enough; "curl -I https://scann-eat.vercel.app returns 200
  and the `etag` header differs from the previous deploy" is.
- **If a runbook is ambiguous when you're using it under stress, open a
  PR to fix the runbook — the next person under stress thanks you.**
