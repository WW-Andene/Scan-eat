/**
 * User-extendable food database.
 *
 * The built-in FOOD_DB ships with ~60 CIQUAL entries. This module lets the
 * user persist their own foods (favourite cereal, a local pastry, etc.)
 * so they show up in autocomplete and get the same CIQUAL-vs-LLM
 * reconciliation as the built-in list.
 *
 * Storage: localStorage under `scanneat.customFoods` as a JSON array. Kept
 * small (< 200 entries in practice) so localStorage's 5 MB cap is not a
 * concern. Not using IDB because querying is linear anyway and the set is
 * small; a simpler store avoids another DB_VERSION migration.
 *
 * Schema per entry:
 *   { id, name, kcal, protein_g, carbs_g, fat_g, aliases?[], created_at }
 * Values are per 100 g — same convention as FOOD_DB.
 */

const KEY = 'scanneat.customFoods';

function readAll() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function writeAll(arr) {
  try {
    localStorage.setItem(KEY, JSON.stringify(arr));
  } catch {
    // Quota or serialization failure — caller treats as best-effort.
  }
}

/** Pure builder exposed for tests. */
export function buildCustomFood({ name, kcal, protein_g = 0, carbs_g = 0, fat_g = 0, aliases = [] }, now = Date.now()) {
  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `cf${now}${Math.random().toString(36).slice(2)}`),
    name: String(name ?? '').trim(),
    kcal: Math.max(0, Number(kcal) || 0),
    protein_g: Math.max(0, Number(protein_g) || 0),
    carbs_g: Math.max(0, Number(carbs_g) || 0),
    fat_g: Math.max(0, Number(fat_g) || 0),
    aliases: Array.isArray(aliases) ? aliases.filter((s) => typeof s === 'string' && s.length > 0) : [],
    created_at: new Date(now).toISOString(),
    custom: true,
  };
}

export function listCustomFoods() {
  return readAll();
}

export function saveCustomFood(food) {
  // R34.N3: always build a normalized entry (which fills defaults for
  // aliases / created_at / custom). If the caller supplies an id, we
  // override the generated one so the existing-by-id branch of the
  // upsert replaces the prior row cleanly.
  const entry = buildCustomFood(food);
  if (food?.id) entry.id = food.id;
  if (!entry.name) return null;
  const all = readAll();
  const ix = all.findIndex((f) => f.id === entry.id);
  if (ix >= 0) all[ix] = entry; else all.push(entry);
  writeAll(all);
  return entry;
}

export function deleteCustomFood(id) {
  const all = readAll().filter((f) => f.id !== id);
  writeAll(all);
}

export function clearCustomFoods() {
  writeAll([]);
}

/**
 * Fix #5 — buildCustomFoodProductInput: synthesise a ProductInput
 * from a custom food so scoreProduct can grade it (and the
 * autocomplete picker can surface a grade chip). Custom foods are
 * per-100 g by contract, so nutrition maps 1:1.
 */
export function buildCustomFoodProductInput(food) {
  return {
    name: food?.name || '',
    category: 'other',
    weight_g: 100,
    // Single-ingredient synthesis: the food IS the ingredient.
    ingredients: [{
      name: food?.name || '',
      percentage: 100,
      category: 'food',
    }],
    nutrition: {
      energy_kcal: Number(food?.kcal) || 0,
      protein_g:   Number(food?.protein_g) || 0,
      carbs_g:     Number(food?.carbs_g) || 0,
      fat_g:       Number(food?.fat_g) || 0,
      // Custom foods currently don't capture these; zero is safer
      // than omitting (scoring engine expects numbers).
      saturated_fat_g: Number(food?.sat_fat_g) || 0,
      sugars_g:    Number(food?.sugars_g) || 0,
      fiber_g:     Number(food?.fiber_g) || 0,
      salt_g:      Number(food?.salt_g) || 0,
    },
  };
}
