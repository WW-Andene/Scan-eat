# Rollback

Something is broken on production and you need the prior version back. Pick
the fastest safe path for the failure mode.

## Triage (30-second assessment)

| Symptom | Path |
|---------|------|
| Homepage won't load / JS errors in console for all users | §Fast (Vercel promote previous) |
| One API route returning 500s / timeouts | §Fast (Vercel promote previous) |
| A feature shows wrong data but the app otherwise works | §Safe (revert commit + redeploy) |
| User data corrupted (IDB schema bug) | §User-data (§below, more involved) |
| Groq 429s at user-visible volume | Don't roll back. See `incident.md` §Rate limits. |

## §Fast — Promote previous deploy (Vercel dashboard)

1. Open `https://vercel.com/<team>/scann-eat/deployments`.
2. Find the last known-good deploy (green check next to a commit SHA you
   trust).
3. Click `⋯` → "Promote to production".
4. Wait ~10 s for propagation.

**Verification**:
```
curl -s https://<your-domain>/service-worker.js | head -1
# CACHE = 'scann-eat-shell-<sha>' must match the promoted deploy's SHA.
```

## §Safe — Revert + redeploy

When the bad commit is identifiable and you want git history to reflect
the rollback:

```
git checkout main
git pull
git log -5 --oneline
# Identify the bad commit SHA, e.g. abc1234
git revert abc1234
git push origin main
```

Vercel auto-deploys the revert. Takes ~30 s.

**Don't force-push**. If the bad commit is already on main, revert — never
rewrite. Force-push on a branch tracking multiple deploys desynchronises
history in a way that's hard to reason about and can land you with
mismatched SW caches on clients.

## §User-data — IDB schema bug

If a shipped change corrupts user data in IndexedDB (e.g. a `DB_VERSION`
bump with a broken `onupgradeneeded`):

1. **Stop the bleeding**: §Fast promote a prior deploy so new installs get
   the working code.

2. **Advisory**: post to the release notes / status page:
   > "If you installed Scan'eat between YYYY-MM-DD HH:MM and HH:MM, your
   > diary may be affected. Export your data via Settings → Backup →
   > Export before uninstalling. Reinstall clears the affected database."

3. **Cleanup migration** (optional, preferred): bump `DB_VERSION` again
   with a repair migration in each `public/data/*.js` `onupgradeneeded`.
   Test locally with an artificially damaged IDB dump. Only ship after
   `npm test` passes + manual verification on a real profile.

4. **Never auto-delete user data without consent**. A migration that
   clears a store is Tier-3 per `app_development_SKILL §I.1`. If the only
   recovery is dropping the store, route it through a prompt that asks
   the user before deleting anything.

## What "rollback verified" means

- The promoted deploy's SW cache key matches the known-good SHA.
- An end-to-end scan completes on a real device against the production URL.
- Console has no repeated errors from the reverted code path (check
  Vercel function logs if the issue was server-side).
- If any user data was affected, the follow-up migration is drafted or a
  user-facing advisory is posted.
