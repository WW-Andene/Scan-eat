# Scan'eat Server

Ktor backend for the Scan'eat Android app. Replaces the Vercel/TypeScript `api/*.ts` functions with a single deployable JVM service.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Liveness probe |
| POST | `/api/score` | Barcode + optional images → ScoreAudit |
| POST | `/api/identify` | Image → identified food (fresh/unpackaged) |
| POST | `/api/identify-multi` | Image → multiple foods on one plate |
| POST | `/api/identify-menu` | Menu photo → list of dishes |
| POST | `/api/identify-recipe` | Recipe card photo → structured recipe |
| POST | `/api/suggest-recipes` | Ingredient → recipe ideas |
| POST | `/api/suggest-from-pantry` | Pantry list → recipes |
| GET | `/api/fetch-recipe?url=` | Scrape schema.org Recipe from a blog URL |

## Authentication

Two modes, evaluated in this order per request:

1. **Client key** — client passes `X-Groq-Key: gsk_...` header (Direct mode in the app)
2. **Server key** — `GROQ_API_KEY` env var set at deploy time

Either is sufficient. Both may be present (client key wins).

## Run locally

```bash
# Option 1 — Gradle
GROQ_API_KEY=gsk_... ./gradlew run

# Option 2 — Docker Compose
cp .env.example .env   # fill in GROQ_API_KEY
docker compose up --build
```

Server starts on port 8080 (or `$PORT`).

## Deploy

**Docker (any host)**
```bash
docker build -t scanneat-server .
docker run -p 8080:8080 -e GROQ_API_KEY=gsk_... scanneat-server
```

**Railway / Render / Fly.io** — point at this directory, set `GROQ_API_KEY` as an env secret. The Dockerfile and `PORT` env var handling are already in place.

**Fat JAR**
```bash
./gradlew shadowJar
java -jar build/libs/scan-eat-server.jar
```

## Wire into the Android app

1. Open Settings in the app
2. Set Mode → **Server**
3. Set Server URL → `https://your-host.example.com`
4. Optionally fill in your Groq key (passed as `X-Groq-Key` header if server has no key of its own)

## Architecture

```
scan-eat-server/
├── src/main/kotlin/fr/scanneat/
│   ├── Application.kt          Ktor entry point, plugin wiring, routing
│   ├── model/ApiModels.kt      All @Serializable request/response DTOs
│   ├── service/
│   │   ├── GroqService.kt      HTTP client for Groq (retry + fallback model)
│   │   ├── OffService.kt       HTTP client for Open Food Facts
│   │   └── LlmLabelParser.kt   All LLM prompt logic (label, identify, menus, recipes)
│   ├── shared/
│   │   ├── DomainModels.kt     Domain types (copied from Android, same package)
│   │   ├── AdditivesDb.kt      70+ EFSA/IARC-cited additive entries
│   │   ├── ScoringEngine.kt    5-pillar scoring engine (pure, no I/O — see ENGINE_VERSION)
│   │   ├── ServerOffMapper.kt  OFF API response → domain Product
│   │   └── DomainToDto.kt      domain → @Serializable API response
│   └── routing/
│       ├── RouteHelpers.kt     Key resolution, image normalisation, error mapping
│       ├── ScoreRoute.kt       POST /api/score
│       ├── IdentifyRoute.kt    POST /api/identify + /api/identify-multi
│       ├── IdentifyMenuRoute.kt POST /api/identify-menu + /api/identify-recipe
│       ├── SuggestRoute.kt     POST /api/suggest-recipes + /api/suggest-from-pantry
│       └── FetchRecipeRoute.kt GET /api/fetch-recipe
```

The scoring engine (`ScoringEngine.kt`, `AdditivesDb.kt`) is a verbatim copy of the Android domain module. No multiplatform setup — kept simple for Phase 2. If the engine diverges, copy the updated file and rewrite the package declaration.
