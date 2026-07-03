# public/features/

Self-contained feature modules extracted from `app.js`. Each module:

- Owns its DOM surface (renders into known element IDs)
- Owns its persistence layer (localStorage / IDB keys)
- Wires its own event handlers
- Takes runtime dependencies via an `init()` function, not imports — so
  the module doesn't circularly import from `app.js` and stays independently
  testable.

## Pattern

```js
// public/features/my-feature.js
let deps = null;

export function renderMyFeature() { /* reads deps, updates DOM */ }

export function initMyFeature(injected) {
  deps = injected; // { t, getProfile, ... }
  document.getElementById('my-button')?.addEventListener('click', () => {
    // ...
    renderMyFeature();
  });
}
```

```js
// public/app.js
import { initMyFeature, renderMyFeature } from '/features/my-feature.js';
initMyFeature({ t, getProfile, waterGoalMl, todayISO });
// Later: renderMyFeature() from the main render loop
```

## Migrated so far

- `hydration.js` — the daily-glass counter tile on the dashboard.

## Candidates for future migration

- Fasting timer (same shape as hydration)
- Weekly rollup view
- Meal templates dialog
- Recipes builder
- Profile dialog
- Quick Add dialog
- Camera live-scan

Each migration should be done in an isolated commit so the blast radius
stays small. Don't move more than one feature per change.
