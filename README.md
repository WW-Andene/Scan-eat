# Scan'eat Native

Scan'eat is now a Kotlin-only native codebase. The old browser PWA, Vercel serverless functions, Capacitor wrapper, Node build, and TypeScript test/runtime files have been removed so the repository no longer ships a web shell or web deployment path.

## What remains

- `src/main/kotlin/app/scaneat/Application.kt` exposes the native Kotlin API surface for scoring, identification fallbacks, menu/recipe helpers, and health checks.
- `src/main/kotlin/app/scaneat/ScoringEngine.kt` contains the deterministic nutrition, additive, allergen, diet, and personal-score engine.
- `src/main/kotlin/app/scaneat/Clients.kt` contains native, offline helpers and a small embedded barcode catalog. It does not proxy to Vercel, Open Food Facts, browser fetch, or any web API.
- `src/test/kotlin/app/scaneat/` contains the Kotlin test suite.

## Native-only guarantees

- No PWA assets: no service worker, manifest, browser shell, or install banner.
- No Vercel deployment: no `vercel.json` and no `api/*.ts` serverless functions.
- No Node runtime: no `package.json`, package lock, TypeScript entry points, or JavaScript tests.
- No web fetch/proxy behavior: barcode lookup and recipe/menu helpers are deterministic Kotlin logic.

## Requirements

- JDK 21+
- Gradle wrapper included in this repository

## Run

```bash
./gradlew run
```

The service listens on `PORT` or `5173` by default.

## Test

```bash
./gradlew test
```
