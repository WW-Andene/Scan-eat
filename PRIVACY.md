# Privacy

Scan'eat is local-first by design. This document describes what happens to
user data, where it's stored, what leaves the device, and when.

> **Jurisdiction**: this document targets EU / EEA users under GDPR + the
> French Loi Informatique et Libertés framework. Not a legal review. See the
> LICENSE placeholder and open an issue if you are deploying in a regulated
> jurisdiction requiring additional disclosures.

---

## What's on the device

Stored in the browser (IndexedDB + `localStorage`) under origin keys prefixed
`scanneat.*` and the IDB database `scanneat`:

- **Consumption log** — every entry the user has recorded (barcode scans,
  photo scans, quick adds, imported CSV rows). Fields: product name, grams,
  macros, fiber, micros, meal slot, date, timestamp, category.
- **Scan history** — up to 30 most recent product scans.
- **Weight log** — user-entered weight measurements.
- **Activity log** — user-logged exercise sessions.
- **Hydration** — glasses-of-water counter keyed by date.
- **Fasting state + history** — start/end timestamps of intermittent fasts.
- **Recipes, meal templates, custom foods** — user-curated catalogues.
- **Profile** — age, sex, height, weight, goal weight, activity level, diet,
  diet modifiers, hydration goal, macro split.
- **Settings** — language, theme, font size, scoring mode, reminders.
- **Day notes** — free-text per-day notes.
- **Meal plan** — 7 days × 4 meals × slot contents.
- **Telemetry buffer (if opt-in)** — a capped local log of recent scoring
  events. Never transmitted off-device.

All of the above is cleared when the user clears browser site data, or when
they use the "Clear today" / "Switch profile" / per-item delete controls.

---

## What leaves the device

| Destination | When | Payload |
|-------------|------|---------|
| **Open Food Facts** (world.openfoodfacts.org) | On every barcode lookup | The 8/12/13-digit barcode only. No user ID. No session cookie. No photo. |
| **Groq** (api.groq.com) in **server mode** | When the user takes a label photo / food photo / menu photo, or asks for recipe ideas / pantry suggestions | The photo (as base64 JPEG, compressed and downscaled to ≤ 1280 px in `compressImage`) OR the ingredient text. No user ID, no location, no device fingerprint. |
| **Groq** in **direct mode** | Same as above, but the request goes from the user's browser directly to Groq using the user's own API key | Same payload. The deployer does not see any of it. |
| **Nothing** | Everything else (weight, hydration, diary totals, profile, settings, recipes, custom foods, meal plans, fasting state, telemetry) | — |

### About the Groq calls

- **Models**: `meta-llama/llama-4-scout-17b-16e-instruct` (default). The model
  name is set in `ocr-parser.ts` and can be overridden per call.
- **System prompts**: all prompts are French-language and instruct the model
  to return structured JSON only. See `ocr-parser.ts` §3 for the complete
  prompt text. No user PII is sent in prompts.
- **Storage**: Groq's data-retention policy governs what happens to the
  payload once received. Per their documented policy at publication time,
  free-tier inputs may be used for abuse monitoring; paid tier provides
  stronger retention controls. Users on direct mode are subject to the
  terms attached to their own Groq key.
- **Images sent**: downscaled JPEG, typically < 200 KB. No EXIF stripping
  is performed explicitly — the `compressImage` path re-encodes via canvas,
  which drops most EXIF in practice, but users with sensitive EXIF should
  strip before scanning.

### What Scan'eat *does not* transmit

- No analytics (no Google Analytics, Plausible, Matomo, PostHog).
- No crash reporting (no Sentry, Datadog, Rollbar).
- No ads / tracking pixels.
- No account system — there is no user identifier to attach to anything.
- No background sync — nothing leaves the device unless the user deliberately
  takes an action (scan, photograph, ask for ideas).

---

## Backup, restore, and export

- **Backup** produces a single JSON file containing every `scanneat.*` key
  from `localStorage` + every IDB store. Downloaded locally; never uploaded.
- **CSV export** produces a daily-totals CSV. Downloaded locally.
- **Health JSON** produces a `data.json` compatible with generic health
  importers. Downloaded locally.
- **CSV import** accepts MyFitnessPal + Cronometer exports. Parsed entirely
  client-side; no server round trip.

---

## Third-party keys

In server-mode deployments, the deployer sets `GROQ_API_KEY` as a Vercel
project environment variable. That key is never exposed to the client.

In direct mode, the user pastes their own Groq key into Settings. The key is
stored in `localStorage` under `scanneat.groqKey` and is used only to
construct the `Authorization` header for requests to Groq. It is never
transmitted to any other host.

---

## Telemetry

Telemetry is **opt-in** and **local-only**. When enabled:

- A capped log of recent events (scan verdicts, identify confidences, errors)
  is stored in `localStorage` under `scanneat.telemetry.buffer`.
- The user can view and clear the log from Settings → Telemetry.
- The buffer is never sent anywhere.

The module's source is in `public/core/telemetry.js`. It has no network
calls; verify with `grep -n 'fetch\|XMLHttpRequest' public/core/telemetry.js`
(expected: no matches).

---

## Data subject rights (GDPR / LIL)

Because all data is on-device and there is no account system, the standard
GDPR rights resolve to browser actions:

- **Right of access / portability**: use Settings → Backup → Export.
- **Right to erasure**: use per-item delete, Clear Today, browser "Clear site
  data", or — in the extreme — uninstall the PWA / wipe the browser profile.
- **Right to rectification**: edit entries directly in the dashboard diary.

If the deployer has enabled server-mode Groq, the deployer is a data
controller for the payloads transiting their server. They are responsible
for the relevant disclosures in their deployment's privacy policy.

---

## Changes

This file is versioned in-repo. Any material change to what leaves the device
must be reflected here in the same commit that changes the behaviour.

**Last reviewed:** 2026-04-22.
