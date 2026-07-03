# Deploy

Shipping a new version of Scan'eat covers two artefacts: the **web shell**
(Vercel) and the **Android APK** (GitHub Actions workflow `android.yml`).

## Preconditions

- Branch you're deploying has passed `npm test` locally. 449 tests must
  pass; 0 failures; run <1.5 s.
- You have merged to `main` (or whatever the production branch is — check
  `vercel.json` and the GH Actions workflow for the current name).
- You've bumped `package.json` `version` if the change is user-visible.
  See ADR on versioning (not yet written — SemVer until 1.0).
- `docs/DECISIONS.md` has an entry for any Tier-1 or Tier-2 decisions this
  release contains.

## Web shell

Vercel auto-deploys on push to the production branch. Steps:

1. **Merge / push**:
   ```
   git push origin main
   ```

2. **Watch the Vercel deploy**. The URL is
   `https://vercel.com/<your-team>/scann-eat/deployments`. Typical deploy
   takes ~30 s.

3. **Post-deploy verification**:
   ```
   # Homepage returns 200 with the expected SW cache key
   curl -sI https://<your-domain>/ | head -5
   curl -s https://<your-domain>/service-worker.js | head -2
   # Expected: CACHE = 'scann-eat-shell-<sha>' where <sha> is the new commit
   ```
   The cache key embeds the commit SHA (set by `build.mjs`). A stale key
   here means the build didn't run.

4. **API smoke test** (server mode only):
   ```
   curl -X POST https://<your-domain>/api/identify \
     -H 'Content-Type: application/json' \
     -d '{"images":[]}' | head
   # Expected: 400 { "error": "Missing images" } — confirms the endpoint
   # is wired and the deployer's GROQ_API_KEY is available.
   ```

5. **Verify service-worker update** on a real device. The SW uses
   stale-while-revalidate, so users on an old shell pick up the new one on
   their next load. Force-reload clears the cache immediately.

### Rollback

If the deploy is broken, roll back from the Vercel dashboard
(Deployments → previous → "Promote to production"). Instant. Or revert
the commit + push:

```
git revert HEAD
git push origin main
```

The revert triggers a fresh Vercel deploy with the prior code.

## Android APK

1. **Trigger the build**. Push to any branch fires
   `.github/workflows/android.yml`. Watch it at
   `https://github.com/<owner>/<repo>/actions`.

2. **Download the artifact** from the completed workflow run. File name
   like `scann-eat-debug-<sha>.apk`.

3. **Sideload** for testing on a dev device (ADB):
   ```
   adb install -r scann-eat-debug-<sha>.apk
   ```

4. **Release builds**: the workflow currently produces debug APKs only.
   Signing + release-APK production is left to the deployer. Add a
   signed-release job when a real distribution channel exists (Play
   Store or direct-download releases page).

### Known quirk

The SW cache key is set from `git rev-parse --short HEAD` at build time.
If the workflow checks out a detached HEAD without full history, the
short SHA may collide with a previous build. Always run in a shallow-
clone with at least `fetch-depth: 1` — the checked-in `android.yml`
already does this.

## What "deploy verified" means

- Web shell homepage returns 200 + the new SW cache key.
- `/api/identify` (or any `/api/*`) returns a structured error (not HTML)
  confirming the function is live.
- An end-to-end barcode scan completes on a real device using the
  deployed URL.
- The CHANGELOG entry (if you keep one) matches the commit range
  between the previous tag and the new tag.
