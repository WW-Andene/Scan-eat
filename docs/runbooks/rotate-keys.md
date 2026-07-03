# Rotate keys

The only secret in the runtime is `GROQ_API_KEY` (server-mode only).
Rotation is needed if the key leaked, is suspected compromised, or as a
scheduled hygiene step.

## Preconditions

- You're the Vercel project owner / have "Environment Variables" access.
- You have a Groq account login and can generate a new key at
  `https://console.groq.com/keys`.

## Steps

1. **Generate the new key** in the Groq console. Label it with the date,
   e.g. `scann-eat-prod-2026-04-22`.

2. **Update Vercel env var**:
   - Vercel dashboard → Project → Settings → Environment Variables
   - Find `GROQ_API_KEY`
   - "Edit" → paste new key → Save
   - Select "Production" environment (and Preview if you use it)

3. **Trigger a redeploy** so the new env var is picked up. The change
   doesn't auto-redeploy:
   - Dashboard → Deployments → latest production → `⋯` → "Redeploy"
   - OR push an empty commit: `git commit --allow-empty -m 'rotate GROQ_API_KEY' && git push`

4. **Verify** the new key is in use:
   ```
   # Smoke-test any vision endpoint; a 500 "Groq API 401" in the logs
   # would indicate the old key is still active.
   curl -X POST https://<your-domain>/api/identify \
     -H 'Content-Type: application/json' \
     -d '{"images":[{"base64":"iVBORw0...tiny-pixel...","mime":"image/png"}]}' \
     -w 'HTTP %{http_code}\n'
   # Expected: 500 "Identification failed" for an invalid image,
   # but NOT a 500 "Groq API 401: Unauthorized".
   ```
   Check Vercel function logs for the actual error.

5. **Revoke the old key** in the Groq console. Delete the row; don't just
   rename it. Do this AFTER verification — revoking first causes a brief
   outage if the new key didn't deploy.

6. **Log the rotation** in `docs/DECISIONS.md` with tier 1:
   ```
   ### [YYYY-MM-DD] GROQ_API_KEY rotated
   Tier: 1
   Context: Scheduled rotation / suspected leak.
   Decision: Generated new key, updated Vercel env, revoked old.
   Rationale: Hygiene / containment.
   ```

## If the key actually leaked publicly (e.g. committed to a repo)

1. **Revoke first, rotate second**. The window during which the key is
   both live and public is the damage window; minimise it.
2. **Check Groq's billing dashboard** for anomalous usage in the hours
   before you noticed. Any burst of vision calls from unknown sources
   should be reported to Groq support.
3. **Audit the commit that leaked it** — if it's in the git history, the
   key is public forever even after you force-remove the commit. Always
   revoke.
4. **If it was committed to this repo**: after revocation, `git
   filter-repo` or BFG can remove the commit, but treat the key as
   burned either way.

## What "rotation verified" means

- A scan on a real device against the production URL succeeds.
- Vercel function logs show no `401` from Groq in the hour after
  rotation.
- The old key is listed as "Revoked" in the Groq console.
- `docs/DECISIONS.md` has a new entry dated today.
