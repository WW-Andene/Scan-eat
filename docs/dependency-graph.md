# Dependency Graph Snapshot

Generated manually from the current module boundaries to make feature ownership
visible until a bundler-based graph is introduced.

```mermaid
graph TD
  app[public/app.js] --> scanPipeline[features/scan-pipeline.js]
  app --> recipeIdeas[features/recipe-ideas.js]
  app --> mealPlan[features/meal-plan-ui.js]
  scanPipeline --> apiBase[core/api-base.js]
  scanPipeline --> queueState[state/scan-queue-state.js]
  scanPipeline --> queueUi[features/scan-queue-ui.js]
  scanPipeline --> scanHistory[data/scan-history.js]
  offlineQueue[features/offline-queue-sync.js] --> queueStore[data/queue-store.js]
  offlineQueue --> apiBase
  apiScore[api/score.ts] --> apiLib[api/_lib.ts]
  apiScore --> ocr[src/ocr-parser.ts]
  apiScore --> off[src/off.ts]
  apiScore --> engine[src/scoring-engine.ts]
  kotlinApp[src/main/kotlin/app/scaneat/Application.kt] --> kotlinClients[src/main/kotlin/app/scaneat/Clients.kt]
  kotlinApp --> kotlinEngine[src/main/kotlin/app/scaneat/ScoringEngine.kt]
```

Use this file as the review checklist when splitting large modules or moving API
validation so callers do not bypass shared hardening.
