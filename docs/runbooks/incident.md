# Incident response

A user reports a problem, or you notice something broken. This runbook
triages and gives the first-hour actions.

## Triage tree

```
Is user data being lost or corrupted?
├─ YES → SEV-1. Follow §SEV-1 below, STOP all deploys until resolved.
└─ NO  → Is the app unusable for most users (won't load / crashes)?
          ├─ YES → SEV-2. Follow §SEV-2.
          └─ NO  → Is a specific feature broken for many users?
                    ├─ YES → SEV-3. Follow §SEV-3.
                    └─ NO  → single-user issue. File + ack, no runbook.
```

## §SEV-1 — Data loss / corruption

1. **Halt all deploys**. Announce in team channel. Nothing ships until
   resolved.
2. **Execute `rollback.md` §User-data** if the cause is a recent schema
   bug. Promote the previous known-good deploy.
3. **Preserve evidence**: if a user can share their IDB dump, ask them to
   use Settings → Backup → Export and attach the file to the incident
   ticket. Do not mutate their local data until you have the backup.
4. **Patch**: write a targeted fix with a test reproducing the failure.
   Don't ship without the test. Don't roll the patch into a broader release.
5. **Advisory**: users in the affected window get a notice
   (release-notes update, in-app banner if possible) explaining:
   - What happened
   - What data is at risk
   - How they can export / recover
   - Whether a subsequent patch restores anything automatically
6. **Post-mortem** within 48 h. Template:
   - What happened (timeline, UTC timestamps).
   - Root cause (one sentence, with a code pointer).
   - Blast radius (how many users, how much data).
   - What stopped the bleeding.
   - What we'll do so it can't happen again.

## §SEV-2 — App unusable for most users

1. **Check the obvious**:
   - Vercel deploy status: is the last deploy green?
   - API health: `curl -sI https://<domain>/api/identify -X POST -d '{"images":[]}'`
     should return 400, not 500 or timeout.
   - Groq status: `https://groqstatus.com` (if they publish one) or try a
     direct-mode scan with a personal key.
2. **If a recent deploy caused it**: execute `rollback.md` §Fast.
3. **If Groq is down**:
   - Users in server mode are broken; nothing we can do for them.
   - Users in direct mode may see the same failure (it's the same upstream).
   - Post a status note. Don't deploy — you'll just need to undo it.
4. **If the SW is holding a broken version on user devices**: force a
   cache-buster by deploying a trivial commit. The SW cache key (derived
   from commit SHA) changes; users get a clean shell on their next load.
5. **Communicate**: status page / release notes update. Honest about scope.

## §SEV-3 — Feature broken for many users

1. **Reproduce it**. If you can't reproduce from the bug report, ask the
   user for:
   - Browser + OS + version
   - A sample barcode or food photo (if scan-related)
   - Console log export from devtools if available
   - `scanneat.*` localStorage dump (Settings → Backup → Export is the
     safe way — strips PII via the same path as a real backup)
2. **Write a failing test first**. If the test can't express the failure
   (browser-only, DOM-dependent), at least write the pure-function test
   that isolates the culprit.
3. **Fix + merge + deploy**. Standard release path.
4. **Confirm with the reporter** before closing. "Fixed in vN.M.P, please
   refresh" is the minimum; a screenshot of the reporter confirming is
   better.

## Useful commands (copy-paste)

```bash
# Local reproduction against production data
GROQ_API_KEY=… npm run dev
# Then open http://localhost:3000

# Smoke-test every API endpoint
for ep in identify identify-multi identify-menu suggest-recipes suggest-from-pantry; do
  curl -X POST https://<domain>/api/$ep \
    -H 'Content-Type: application/json' \
    -d '{}' -w "$ep → HTTP %{http_code}\n" -s -o /dev/null
done

# Check that the SW on a device matches the live deploy
# (run in browser console on the problem device)
navigator.serviceWorker.getRegistrations().then(rs => rs.forEach(r => console.log(r.active.scriptURL)))
```

## Rate-limit (Groq 429)

Not a rollback situation. Sustained 429s mean we've outgrown the free
tier or the per-minute quota. Actions:

1. Acknowledge publicly — direct-mode is always available as a workaround.
2. Consider a client-side backoff layer in `callGroqVision`.
3. Budget: decide whether to upgrade the Groq tier (paid) or push more
   users toward direct mode. Per `ASSUMPTIONS.md`, this decision is
   Tier-2; document it in `DECISIONS.md` before signing up.
