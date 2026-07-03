/**
 * Consumption log + daily aggregates.
 *
 * Each logged scan becomes a ConsumptionEntry keyed by date. Totals for
 * today (or any date) are computed by summing the entries. Per-100 g
 * nutrition values are scaled to the portion size the user entered.
 *
 * Math is pure per-100 g → per-portion conversion:
 *   nutrient_consumed = nutrient_per_100g × (grams / 100)
 *
 * No medical claims here — this is just arithmetic scoring against the
 * daily targets already derived in profile.js from Mifflin-St Jeor BMR +
 * FAO/WHO/UNU PAL + WHO energy-percent conversions.
 */

import { localDateISO } from '../core/dateutil.js';
import { MICRO_KEYS } from '../core/nutrition-fields.js';
// Single-source micro list. Fiber is included; sumTotals handles it
// through the loop, no explicit per-field addition.
const MICRO_FIELDS = MICRO_KEYS;

const DB_NAME = 'scanneat';
const DB_VERSION = 6;
const STORE = 'consumption';

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('pending_scans')) {
        db.createObjectStore('pending_scans', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('history')) {
        const s = db.createObjectStore('history', { keyPath: 'id' });
        s.createIndex('created', 'createdAt');
      }
      if (!db.objectStoreNames.contains('consumption')) {
        const s = db.createObjectStore('consumption', { keyPath: 'id' });
        s.createIndex('date', 'date');
      }
      if (!db.objectStoreNames.contains('weight')) {
        const s = db.createObjectStore('weight', { keyPath: 'id' });
        s.createIndex('date', 'date', { unique: true });
      }
      if (!db.objectStoreNames.contains('meal_templates')) {
        db.createObjectStore('meal_templates', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('recipes')) {
        db.createObjectStore('recipes', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('activity')) {
        const s = db.createObjectStore('activity', { keyPath: 'id' });
        s.createIndex('date', 'date');
      }
    };
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => { try { db.close(); } catch { /* ignore */ } };
      resolve(db);
    };
    req.onerror = () => reject(req.error);
    req.onblocked = () => { /* older tab is holding — eventual close() clears it */ };
  });
}

/** Persist a pre-built entry object (used by meal-templates expand path). */
export async function putEntry(entry) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(entry);
    tx.oncomplete = () => { db.close(); resolve(entry); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

// Re-exported via /core/dateutil.js — kept here as the canonical name
// the rest of the app imports from. See that file for why it's local
// (not UTC) day.
export { localDateISO as todayISO } from '../core/dateutil.js';

export const MEALS = ['breakfast', 'lunch', 'dinner', 'snack'];

/**
 * Build a ConsumptionEntry from a product + portion size (grams).
 * Exposed separately so it can be unit-tested without IDB.
 */
export function buildEntry(product, grams, opts = {}) {
  const { meal = 'snack', now = Date.now() } = opts;
  const g = Math.max(0, Number(grams) || 0);
  const f = g / 100;
  const n = product?.nutrition ?? {};
  const sugars = n.added_sugars_g ?? n.sugars_g ?? 0;
  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `c${now}${Math.random().toString(36).slice(2)}`),
    // R25.1: local-day ISO via Intl — previous toISOString().slice(0,10)
    // returned the UTC day, so a user in UTC-5 logging a meal at 9 pm
    // local saw it filed under tomorrow's date. Real data-integrity
    // bug that corrupted the "today" dashboard count.
    date: localDateISO(now),
    timestamp: now,
    product_name: product?.name || '—',
    category: product?.category || 'other',
    grams: g,
    meal: MEALS.includes(meal) ? meal : 'snack',
    kcal: round1((n.energy_kcal ?? 0) * f),
    carbs_g: round2((n.carbs_g ?? 0) * f),
    fat_g: round2((n.fat_g ?? 0) * f),
    sat_fat_g: round2((n.saturated_fat_g ?? 0) * f),
    sugars_g: round2(sugars * f),
    salt_g: round3((n.salt_g ?? 0) * f),
    protein_g: round2((n.protein_g ?? 0) * f),
    fiber_g: round2((n.fiber_g ?? 0) * f),
    // Key micros (mg / µg). Stored as 0 when OFF has no value for the
    // product; the dashboard only renders a row when the daily total > 0.
    iron_mg: round2((n.iron_mg ?? 0) * f),
    calcium_mg: round2((n.calcium_mg ?? 0) * f),
    magnesium_mg: round2((n.magnesium_mg ?? 0) * f),
    potassium_mg: round2((n.potassium_mg ?? 0) * f),
    zinc_mg: round2((n.zinc_mg ?? 0) * f),
    sodium_mg: round2((n.sodium_mg ?? 0) * f),
    vit_a_ug: round2((n.vit_a_ug ?? 0) * f),
    vit_c_mg: round2((n.vit_c_mg ?? 0) * f),
    vit_d_ug: round2((n.vit_d_ug ?? 0) * f),
    vit_e_mg: round2((n.vit_e_mg ?? 0) * f),
    vit_k_ug: round2((n.vit_k_ug ?? 0) * f),
    b1_mg: round2((n.b1_mg ?? 0) * f),
    b2_mg: round2((n.b2_mg ?? 0) * f),
    b3_mg: round2((n.b3_mg ?? 0) * f),
    b6_mg: round2((n.b6_mg ?? 0) * f),
    b9_ug: round2((n.b9_ug ?? 0) * f),
    b12_ug: round2((n.b12_ug ?? 0) * f),
    polyunsaturated_fat_g: round2((n.polyunsaturated_fat_g ?? 0) * f),
    monounsaturated_fat_g: round2((n.monounsaturated_fat_g ?? 0) * f),
    omega_3_g: round2((n.omega_3_g ?? 0) * f),
    omega_6_g: round2((n.omega_6_g ?? 0) * f),
    cholesterol_mg: round2((n.cholesterol_mg ?? 0) * f),
  };
}

/**
 * Build a manual "Quick Add" entry — user-entered totals, not derived from
 * a product. Skips the per-100 g conversion because the user types totals.
 */
export function buildQuickAdd({
  name, meal,
  kcal = 0, carbs_g = 0, protein_g = 0, fat_g = 0,
  sat_fat_g = 0, sugars_g = 0, salt_g = 0, fiber_g = 0,
  iron_mg = 0, calcium_mg = 0, vit_d_ug = 0, b12_ug = 0,
}, now = Date.now()) {
  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `q${now}${Math.random().toString(36).slice(2)}`),
    // R25.1: same local-ISO fix as buildEntry — Quick Add was the
    // other path with the UTC-date bug.
    date: localDateISO(now),
    timestamp: now,
    product_name: name?.trim() || 'Quick Add',
    category: 'other',
    grams: 0,
    meal: MEALS.includes(meal) ? meal : 'snack',
    kcal: round1(Number(kcal) || 0),
    carbs_g: round2(Number(carbs_g) || 0),
    fat_g: round2(Number(fat_g) || 0),
    sat_fat_g: round2(Number(sat_fat_g) || 0),
    sugars_g: round2(Number(sugars_g) || 0),
    salt_g: round3(Number(salt_g) || 0),
    protein_g: round2(Number(protein_g) || 0),
    fiber_g: round2(Number(fiber_g) || 0),
    iron_mg: round2(Number(iron_mg) || 0),
    calcium_mg: round2(Number(calcium_mg) || 0),
    vit_d_ug: round2(Number(vit_d_ug) || 0),
    b12_ug: round2(Number(b12_ug) || 0),
    quickAdd: true,
  };
}

const round1 = (x) => Math.round(x * 10) / 10;
const round2 = (x) => Math.round(x * 100) / 100;
const round3 = (x) => Math.round(x * 1000) / 1000;

export async function logEntry(product, grams, meal = 'snack') {
  const entry = buildEntry(product, grams, { meal });
  return persistEntry(entry);
}

export async function logQuickAdd(fields) {
  const entry = buildQuickAdd(fields);
  return persistEntry(entry);
}

async function persistEntry(entry) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(entry);
    tx.oncomplete = () => { db.close(); resolve(entry); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function listByDate(date = todayISO()) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const idx = tx.objectStore(STORE).index('date');
    const req = idx.getAll(IDBKeyRange.only(date));
    req.onsuccess = () => { db.close(); resolve(req.result || []); };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

/** Every consumption entry in the store. Used for streak + weekly rollup
 *  computations that span more than a single date. */
export async function listAllEntries() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => { db.close(); resolve(req.result || []); };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

export async function deleteEntry(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function clearDate(date = todayISO()) {
  const entries = await listByDate(date);
  for (const e of entries) await deleteEntry(e.id);
}

/** Pure aggregation — exported for tests. */
export function sumTotals(entries) {
  // MACRO keys are summed explicitly. MICRO keys (includes fiber_g)
  // go through the loop below — single source of truth; no
  // double-counting because fiber is NOT in the explicit list.
  const t = {
    kcal: 0, carbs_g: 0, fat_g: 0, sat_fat_g: 0, sugars_g: 0, salt_g: 0, protein_g: 0,
    count: entries.length,
  };
  for (const f of MICRO_FIELDS) t[f] = 0;
  for (const e of entries) {
    t.kcal += e.kcal || 0;
    t.carbs_g += e.carbs_g || 0;
    t.fat_g += e.fat_g || 0;
    t.sat_fat_g += e.sat_fat_g || 0;
    t.sugars_g += e.sugars_g || 0;
    t.salt_g += e.salt_g || 0;
    t.protein_g += e.protein_g || 0;
    for (const f of MICRO_FIELDS) t[f] += e[f] || 0;
  }
  const out = {
    kcal: round1(t.kcal),
    carbs_g: round2(t.carbs_g),
    fat_g: round2(t.fat_g),
    sat_fat_g: round2(t.sat_fat_g),
    sugars_g: round2(t.sugars_g),
    salt_g: round3(t.salt_g),
    protein_g: round2(t.protein_g),
    count: t.count,
  };
  // Rounded micro outputs (fiber_g included via the shared list).
  for (const f of MICRO_FIELDS) out[f] = round2(t[f]);
  return out;
}

/**
 * Group entries by meal and return per-meal totals + the raw list per meal.
 * Unknown or missing meal values collapse into 'snack'.
 */
export function groupByMeal(entries) {
  const buckets = { breakfast: [], lunch: [], dinner: [], snack: [] };
  for (const e of entries) {
    const m = MEALS.includes(e?.meal) ? e.meal : 'snack';
    buckets[m].push(e);
  }
  return {
    breakfast: { entries: buckets.breakfast, totals: sumTotals(buckets.breakfast) },
    lunch:     { entries: buckets.lunch,     totals: sumTotals(buckets.lunch) },
    dinner:    { entries: buckets.dinner,    totals: sumTotals(buckets.dinner) },
    snack:     { entries: buckets.snack,     totals: sumTotals(buckets.snack) },
  };
}

export async function dailyTotals(date = todayISO()) {
  const entries = await listByDate(date);
  return sumTotals(entries);
}
