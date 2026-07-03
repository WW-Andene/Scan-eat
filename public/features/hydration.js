/**
 * Hydration feature — self-contained module.
 *
 * Entry point: initHydration(deps). Call once at boot. The module:
 *   - Reads + writes its own localStorage key (scanneat.hydration.YYYY-MM-DD)
 *   - Renders into #hydration-tile / #hydration-amount / #hydration-fill
 *   - Wires the +/- button handlers
 *   - Exports renderHydration() for callers that want to trigger a re-
 *     render after changing the profile (which affects goal calculation)
 *
 * Dependency injection: deps is { t, getProfile, waterGoalMl, todayISO }.
 * Keeps the module testable and avoids circular imports with app.js.
 *
 * This module is the first step of a broader feature-folder migration —
 * see public/features/README.md (future) for the pattern.
 */

const HYD_GLASS_ML = 250;
const hydKey = (date) => `scanneat.hydration.${date}`;

let deps = null;

function getHydrationMl(date) {
  const raw = localStorage.getItem(hydKey(date));
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? n : 0;
}

function setHydrationMl(ml, date) {
  const clamped = Math.max(0, Math.round(ml));
  localStorage.setItem(hydKey(date), String(clamped));
}

export function renderHydration() {
  if (!deps) return;
  const { t, getProfile, waterGoalMl, todayISO } = deps;
  const tile = document.getElementById('hydration-tile');
  const amt = document.getElementById('hydration-amount');
  const fill = document.getElementById('hydration-fill');
  if (!tile || !amt || !fill) return;
  const profile = getProfile();
  const goal = waterGoalMl(profile);
  const ml = getHydrationMl(todayISO());
  amt.textContent = t('hydrationAmount', { ml, goal });
  const pct = goal > 0 ? Math.min(120, (ml / goal) * 100) : 0;
  fill.style.width = `${Math.min(100, pct)}%`;
  if (pct >= 100 && pct < 110) fill.dataset.state = 'done';
  else if (pct >= 110) fill.dataset.state = 'over';
  else delete fill.dataset.state;
}

export function initHydration(injected) {
  deps = injected;
  document.getElementById('hydration-plus')?.addEventListener('click', () => {
    const today = deps.todayISO();
    setHydrationMl(getHydrationMl(today) + HYD_GLASS_ML, today);
    renderHydration();
  });
  document.getElementById('hydration-minus')?.addEventListener('click', () => {
    const today = deps.todayISO();
    setHydrationMl(Math.max(0, getHydrationMl(today) - HYD_GLASS_ML), today);
    renderHydration();
  });
}
