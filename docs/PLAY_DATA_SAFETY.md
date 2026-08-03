# Google Play Console — Data Safety form mapping

Fill this in verbatim when completing Play Console's "Data safety" section (App content > Data safety). Source: `PRIVACY_POLICY.md` + code audit (`UserPreferences.kt`, `AndroidManifest.xml`, domain models).

## Does your app collect or share any of the required user data types?

**Yes.**

## Data types to declare

| Play category | Sub-type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|---|
| Health and fitness | Health info | Yes | No¹ | App functionality | Allergens, health conditions, diet, weight/height, activity level — encrypted at rest, stored locally only |
| Health and fitness | Fitness info | Yes | No¹ | App functionality | Weight, exercise, hydration, calories, nutrition entries |
| Personal info | Name | No | — | — | App has no user account / no name field |
| Personal info | Age | Yes | No | App functionality | Used for TDEE/nutrition calculations only, stored locally |
| Photos or videos | Photos | Yes (conditional) | Yes, conditional | App functionality | Only when the user enables photo/AI label scanning; sent to the user's own configured AI provider (Groq and/or Cerebras) |
| App activity | App interactions | Yes | No | App functionality | Diary/meal history, recipes, custom foods, templates — local only |
| App activity | Other user-generated content | Yes | No | App functionality | Custom recipes, custom foods, expense entries |
| Financial info | Purchase history | No | — | — | No IAP/payments; expense tracking is user-entered budgeting only, not linked to real purchase/payment data |
| Device or other IDs | — | No | — | — | No advertising ID, no device fingerprinting |
| Location | — | No | — | — | App does not request location permission |

¹ "Shared" in Play's sense means transmitted to a third party outside your app. Health/fitness data itself is **not** sent anywhere by default. Exception: barcodes (not personal health data) are sent to Open Food Facts to identify a scanned product — this is closer to "App info and performance" / product-lookup functionality than personal data sharing, but disclose it under **Data shared with third parties** as: "Barcode of scanned product → Open Food Facts, for product lookup" if Play's form has a slot for it (it typically falls under "App activity" or a footnote in the listing description if no exact category fits).

## Is all of the collected data encrypted in transit?

Yes — OFF/OFF-like calls and any AI-provider calls occur over HTTPS. `usesCleartextTraffic="false"` is already set in the manifest.

## Can users request that data be deleted?

Yes, describe as: "Uninstalling the app deletes all locally stored data. A manual backup/export feature exists in Settings; users manage deletion of any exported file themselves." There is no server-side account to delete data from, since no Scan'eat backend exists.

## Is data collection required or optional?

- Health/fitness profile fields: optional (features degrade gracefully with an empty profile — no allergen/diet/condition warnings shown, no personalized score adjustments).
- Photo/AI scanning: fully optional, gated behind the user entering their own API key.
- Health Connect sync: fully optional, gated behind explicit user permission grants.

## Third parties data may be shared with

1. **Open Food Facts** (world.openfoodfacts.org) — barcode/product lookups and keyword search. Public, non-profit, open database. Link to their privacy policy: https://world.openfoodfacts.org/privacy
2. **Groq** and/or **Cerebras** — only if the user supplies their own API key for photo-based label scanning; the photo is sent directly from the user's device to that provider's API using the user's own credentials. Link to their respective privacy policies in the store listing if required by your jurisdiction.
3. **Android Health Connect** — on-device only (not a network third party), governed by the user's own Health Connect permission grants.

## Independent security review

Not applicable (small team / no formal third-party pentest) unless your organization has commissioned one — if so, list it here.

---

**Action for the developer**: transcribe the table above into Play Console's Data Safety questionnaire UI directly — Google does not accept this file as a submission, it's a working reference so nothing gets missed while clicking through the form.
