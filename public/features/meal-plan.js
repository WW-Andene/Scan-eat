/**
 * Forward meal plan — what you intend to eat over the next 7 days,
 * not what you did eat. Different IDB / localStorage layer than
 * consumption.js (which is the historical log).
 *
 * Storage: localStorage 'scanneat.mealPlan' as a single JSON object
 *   { 'YYYY-MM-DD': { breakfast: SLOT, lunch: SLOT, dinner: SLOT, snack: SLOT } }
 * SLOT shape:
 *   { kind: 'recipe', id: <recipe.id>, name: <display> }
 *   { kind: 'template', id: <template.id>, name: <display> }
 *   { kind: 'note',   text: '<free text>' }
 *
 * Old days are auto-pruned on every write so the structure stays tiny.
 *
 * Pure helpers (buildSlot, weekDates, isoToday, pruneOld) are exported
 * for tests. The IDB layer here is localStorage-only — no schema
 * migration to coordinate with the rest of the app.
 */

import { localDateISO } from '../core/dateutil.js';

const KEY = 'scanneat.mealPlan';
const KEEP_DAYS_PAST = 7;
const MEALS = ['breakfast', 'lunch', 'dinner', 'snack'];

function readAll() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function writeAll(obj) {
  try { localStorage.setItem(KEY, JSON.stringify(obj)); } catch { /* quota */ }
}

// R27.2: `isoToday` was only re-exported (no local binding), so the
// `weekDates()` default branch on line below would have thrown a
// ReferenceError at runtime whenever a caller omitted `startDate`.
// Caught in tests only because every test passes a startDate.
export { localDateISO as isoToday };

/**
 * Yields the 7 ISO dates starting at `startDate` (inclusive), or today
 * by default. Pure — used by the UI to render the grid.
 */
export function weekDates(startDate) {
  const start = startDate ? new Date(`${startDate}T00:00:00Z`) : new Date(`${localDateISO()}T00:00:00Z`);
  const out = [];
  for (let i = 0; i < 7; i += 1) {
    const d = new Date(start);
    d.setUTCDate(start.getUTCDate() + i);
    out.push(d.toISOString().slice(0, 10));
  }
  return out;
}

/** Build a slot object — defensive: drops empties, normalises. Pure. */
export function buildSlot(input) {
  if (!input || typeof input !== 'object') return null;
  if (input.kind === 'recipe' && input.id) {
    return { kind: 'recipe', id: String(input.id), name: String(input.name ?? '').trim() || '—' };
  }
  if (input.kind === 'template' && input.id) {
    return { kind: 'template', id: String(input.id), name: String(input.name ?? '').trim() || '—' };
  }
  if (input.kind === 'note') {
    const text = String(input.text ?? '').trim();
    return text ? { kind: 'note', text } : null;
  }
  return null;
}

/** Strip dates older than KEEP_DAYS_PAST. Pure on its input copy. */
export function pruneOld(plan, now = Date.now()) {
  // R27.1: local-day cutoff (was UTC via toISOString). A user in
  // UTC-8 retaining yesterday's plan saw it pruned up to 8 hours
  // early depending on when the write hit during the evening.
  const cutoff = localDateISO(now - KEEP_DAYS_PAST * 86_400_000);
  const out = {};
  for (const [date, slots] of Object.entries(plan ?? {})) {
    if (date >= cutoff) out[date] = slots;
  }
  return out;
}

// `now` is threaded through the storage layer so tests can pin a fixed
// "today" without mocking Date.now globally. Production callers omit it
// and pruning happens against the real wall clock.
export function getMealPlan(now = Date.now()) {
  return pruneOld(readAll(), now);
}

export function getDayPlan(date, now = Date.now()) {
  const all = getMealPlan(now);
  return all[date] || {};
}

export function setSlot(date, meal, slot, now = Date.now()) {
  if (!date || !MEALS.includes(meal)) return;
  const all = pruneOld(readAll(), now);
  const built = buildSlot(slot);
  if (!all[date]) all[date] = {};
  if (built) all[date][meal] = built;
  else delete all[date][meal];
  if (Object.keys(all[date]).length === 0) delete all[date];
  writeAll(all);
}

export function clearDay(date, now = Date.now()) {
  if (!date) return;
  const all = pruneOld(readAll(), now);
  delete all[date];
  writeAll(all);
}

export function clearAll() {
  writeAll({});
}

/**
 * Resolve every recipe-slot in `dateRange` against the user's saved
 * recipes and return them as a flat list. Used by the grocery-list
 * aggregator to compile a weekly shopping list from the plan.
 *
 * `recipeStore` = result of listRecipes().
 */
export function planRecipes(dateRange, recipeStore, now = Date.now()) {
  const plan = getMealPlan(now);
  const recipesById = new Map((recipeStore ?? []).map((r) => [r.id, r]));
  const out = [];
  for (const date of dateRange) {
    const day = plan[date];
    if (!day) continue;
    for (const meal of MEALS) {
      const slot = day[meal];
      if (slot?.kind === 'recipe' && recipesById.has(slot.id)) {
        out.push(recipesById.get(slot.id));
      }
    }
  }
  return out;
}

export { MEALS as MEAL_PLAN_MEALS };
