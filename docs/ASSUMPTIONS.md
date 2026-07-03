# Assumption Ledger

> Per `app_development_SKILL.md §I.5`. Unverified beliefs the work is resting
> on. Each entry has a falsification cost and an invalidation check. Flip any
> assumption with one message — the skill re-routes from there.

## [2026-04-22] Primary audience is French-speaking home users

**Assumption:** Target persona is a French-speaking home user managing their
own diet. Commercial / B2B / clinical users are not in scope.
**Basis:** Every string defaults to French; every authoritative source
(CIQUAL, EFSA) is European / francophone. No evidence of pricing / contracts /
compliance hooks in the repo. No clinical disclaimers beyond the "indicative
values" notes.
**Falsification cost:** Medium. A B2B / clinical pivot would invalidate the
"MEDIUM stakes" call in the brief and would demand: (1) a legal disclaimer
framework, (2) data retention policies, (3) audit logging for professional
users.
**Invalidation check:** User requests any of: HL7/FHIR export · patient-
management features · clinician login · HIPAA/GDPR-processor contracts ·
medical-device regulatory posture.
**Status:** `[STILL ASSUMED]`

## [2026-04-22] Traffic will stay on free-tier limits

**Assumption:** Server-mode deployments will not exceed Groq's free-tier
rate limits. No quota logic is implemented in `api/*.ts`.
**Basis:** No launch / marketing signal in the repo. The app has been
author-bootstrapped.
**Falsification cost:** Low-to-medium. If 429s start hitting users, we'd
need: rate-limit middleware in the API handlers, a user-facing "direct
mode" prompt on failure (already exists in principle), and possibly a
cached-answer path.
**Invalidation check:** First `Groq API 429` in server logs; any marketing
announcement; sustained traffic from GitHub stars / social shares.
**Status:** `[STILL ASSUMED]`

## [2026-04-22] The hand-transcribed food-db values are accurate ±10 %

**Assumption:** The 54 hand-entered CIQUAL approximations in
`public/data/food-db.js` are close enough to the official ANSES numbers
that users won't systematically mis-track.
**Basis:** Spot-check of the most common entries against the CIQUAL web
viewer during development. Error bars documented in the file header.
**Falsification cost:** Low. `tools/generate-food-db.mjs` is committed and
runnable on any host with network access to ciqual.anses.fr; output is
bit-for-bit regenerable.
**Invalidation check:** User reports a food where our macros diverge > 15 %
from the ANSES sheet.
**Status:** `[STILL ASSUMED — pending generator run]`

## [2026-04-22] Ahn 2011 corpus bias is acceptable

**Assumption:** Culinary pairings from a US/Italian-biased recipe corpus
(allrecipes + epicurious + menupan) generalise to French home cooking well
enough that users don't notice.
**Basis:** Sanity-checked output for tomate / saumon / agneau / chocolat —
classic Mediterranean + French desserts. The corpus does include French
cuisine.
**Falsification cost:** Low. Could be mitigated by a cuisine-filter at
generation time (only rows labelled `French` / `MediterraneanSouthern`).
**Invalidation check:** User reports systematically "weird American" pairings
for a French ingredient (e.g., peanut butter with apple).
**Status:** `[STILL ASSUMED]`

## [2026-04-22] Node test runner is the right test stack

**Assumption:** `node --test --experimental-strip-types` stays the test
runner for the foreseeable future — no switch to Vitest / Playwright.
**Basis:** Zero-dependency; strips TS out of the box; parallel-isolated;
works on the repo today.
**Falsification cost:** Low. A move to Vitest would mean adding a dev
dependency + updating 20+ test-file entrypoints.
**Invalidation check:** Node 22 deprecation of `--experimental-strip-types`,
or a need for browser-DOM tests (Playwright / jsdom).
**Status:** `[STILL ASSUMED]`

## [2026-04-22] PWA-only distribution is acceptable for v1

**Assumption:** Shipping as a PWA + an optional Capacitor-wrapped Android
APK is sufficient. No App Store / Play Store submission required.
**Basis:** The manifest, service worker, install prompt, and APK build
workflow all exist and work; no iOS / Play Store artefacts.
**Falsification cost:** Medium. iOS Safari has known PWA quirks (no
BarcodeDetector, no navigator.share in all contexts, no install prompt).
Play Store submission would need signed APKs, privacy policy page,
screenshots, etc.
**Invalidation check:** User asks for Play Store / App Store listing.
**Status:** `[STILL ASSUMED]`

## [2026-04-22] Native wearable sync is out of PWA scope

**Assumption:** Apple Health / Google Fit sync is intentionally deferred —
documented as "needs Capacitor plugin code".
**Basis:** Explicit note in several prior summary messages.
**Falsification cost:** Low. Adding a Capacitor plugin is a separate, well-
scoped piece of work.
**Invalidation check:** User asks for the bridge.
**Status:** `[STILL ASSUMED]`

## [2026-04-22] ES/IT/DE users accept English fallback for untranslated strings

**Assumption:** Non-Romance / non-French users who pick ES/IT/DE in the
lang select are OK seeing 80% of the app in English.
**Basis:** "(beta)" marker in the language picker sets the expectation.
The fallback chain (`lang → EN → FR → key`) was designed around this.
**Falsification cost:** Low. Filling in more locale strings is mechanical
work; doesn't invalidate any infrastructure.
**Invalidation check:** User chooses a beta locale and says "it's not
translated".
**Status:** `[STILL ASSUMED]`

## [2026-04-24] LTR-only layout (no RTL locales)

**Assumption:** All shipped and planned locales (FR, EN, ES, IT, DE) are
LTR. The CSS uses `margin-left` / `margin-right` (not logical properties
like `margin-inline-*`), `<html dir="ltr">` is implicit. No RTL mirroring
rules.
**Basis:** Target audience per docs/00-brief.md is French-speaking users;
all current and beta locales are LTR European languages.
**Falsification cost:** Medium. Adding Arabic / Hebrew / Persian would
require: (1) adopting CSS logical properties across the 7360-line
stylesheet, (2) setting `<html dir>` from currentLang, (3) icon flip
rules for chevrons / arrows / progress fills.
**Invalidation check:** Product owner asks for an RTL locale.
**Status:** `[STILL ASSUMED]` (audit F-N-06 flagged this)
