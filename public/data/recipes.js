/**
 * Recipes — named multi-component dishes that collapse to ONE log entry.
 *
 * Differs from meal-templates by intent:
 *   templates → "I ate these 3 things today, save them as 3 separate
 *                entries I can re-apply".
 *   recipes   → "Here's how I build this dish (tomato + mozzarella +
 *                oil). Log it as a SINGLE entry showing the dish name +
 *                summed macros."
 *
 * The data shape is similar to templates (an array of components) but the
 * apply path sums the components into one consumption entry instead of
 * materializing each as a separate entry.
 */

const DB_NAME = 'scanneat';
const DB_VERSION = 6; // v6 adds the 'activity' store (exercise log)
const STORE = 'recipes';

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => ensureStores(req.result);
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => { try { db.close(); } catch { /* ignore */ } };
      resolve(db);
    };
    req.onerror = () => reject(req.error);
    req.onblocked = () => { /* older tab is holding — eventual close() clears it */ };
  });
}

/** Defensive store creator — every module's upgrade handler should call
 *  this (or its equivalent) so adding a new store doesn't require every
 *  module to be loaded first. */
export function ensureStores(db) {
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
}

/**
 * Sum a recipe's components into a single ConsumptionEntry-shaped object.
 * Pure function — the apply path is responsible for adding id/date/meal
 * before persisting.
 *
 * `servings` divides the total, so the same recipe can be reused as
 * "1 serving" or "half a serving" at apply time.
 */
// Single source of truth — shared with consumption aggregation.
import { MICRO_KEYS as RECIPE_MICRO_FIELDS } from '../core/nutrition-fields.js';

export function aggregateRecipe(recipe, servings = 1) {
  const s = Math.max(0.1, Number(servings) || 1);
  const items = recipe?.components ?? [];
  const sum = (key) => items.reduce((acc, it) => acc + (Number(it?.[key]) || 0), 0) / s;
  const round1 = (v) => Math.round(v * 10) / 10;
  const round2 = (v) => Math.round(v * 100) / 100;
  const round3 = (v) => Math.round(v * 1000) / 1000;
  const out = {
    // R14.1: leave empty for UI-layer fallback; data stays locale-
    // neutral. Callers in the apply path already render via t().
    product_name: recipe?.name || '',
    category: 'other',
    grams:     Math.round(sum('grams')),
    kcal:      round1(sum('kcal')),
    carbs_g:   round2(sum('carbs_g')),
    fat_g:     round2(sum('fat_g')),
    sat_fat_g: round2(sum('sat_fat_g')),
    sugars_g:  round2(sum('sugars_g')),
    protein_g: round2(sum('protein_g')),
    salt_g:    round3(sum('salt_g')),
    fromRecipe: recipe?.id,
  };
  // Micronutrients + fat breakdown — sum across components then
  // divide by servings. Stored as 0 when no component declares the
  // value; the dashboard already renders conditionally on > 0.
  for (const field of RECIPE_MICRO_FIELDS) {
    out[field] = round2(sum(field));
  }
  return out;
}

/**
 * Gap fix 1 — buildRecipeProductInput: synthesise a ProductInput
 * that the scoring engine (scoreProduct) can grade. Pure, no engine
 * dependency — the caller imports scoreProduct and runs this through
 * it to get { grade, score, pillars }.
 *
 * Method:
 *   - Nutrition is per-100g of the TOTAL recipe weight (sum(grams)).
 *     If the user didn't enter grams for components, we fall back to
 *     a nominal 100 g so the scoring engine has something to work
 *     with instead of division-by-zero.
 *   - Ingredients list is synthesised from component names (no
 *     additive detection; home recipes don't have E-numbers). Each
 *     component's grams relative to the total become its percentage
 *     — matches how scoring-engine expects percentages to be
 *     declared.
 *   - NOVA defaults to unclassified (NaN) so inferNovaClass picks
 *     it up from whole-food flags; components are tagged as whole
 *     foods since recipe-building assumes real ingredients.
 */
export function buildRecipeProductInput(recipe) {
  const items = (recipe?.components ?? []).filter((c) => c && (c.product_name || c.kcal));
  const totalGrams = items.reduce((acc, it) => acc + (Number(it?.grams) || 0), 0);
  // Fall back to 100 g nominal total so scoreProduct has a scale.
  const basis = totalGrams > 0 ? totalGrams : 100;
  const per100 = (key) => {
    const total = items.reduce((acc, it) => acc + (Number(it?.[key]) || 0), 0);
    return (total * 100) / basis;
  };
  // Fix #18: don't blanket-flag every component as a whole food.
  // The scoring engine's isWholeFood() already does keyword-matching
  // on ingredient names (pomme, tomate, poulet, riz, …). Letting it
  // infer naturally means a recipe of "bacon + processed cheese +
  // white flour" correctly scores LOW on ingredient-integrity
  // instead of getting max points from a blanket `true`.
  const ingredients = items.map((c) => {
    const grams = Number(c.grams) || 0;
    const pct = totalGrams > 0 ? Math.round((grams / totalGrams) * 1000) / 10 : null;
    return {
      name: String(c.product_name || '').trim() || '—',
      percentage: pct,
      category: 'food',
      // Let scoring-engine.isWholeFood() decide via the keyword list.
      // Only supply `is_whole_food` explicitly if the caller already
      // knows (e.g. the component came from FOOD_DB via the R5
      // autocomplete — the UI could stash that on the component row
      // in a future pass).
    };
  });
  // Fix #19 — infer a rough category from the ingredient list so
  // the scoring engine can pick the right threshold set (beverages
  // vs snacks vs prepared dishes). Pure heuristic; falls back to
  // 'prepared' which is the closest generic fit for home cooking.
  const nameBlob = items.map((c) => (c.product_name || '').toLowerCase()).join(' ');
  let inferredCategory = 'prepared';
  if (/\b(lait|milk|boisson|drink|soda|jus|juice|smoothie|infusion|tisane)\b/.test(nameBlob)
      && items.length <= 4) {
    inferredCategory = 'beverage';
  } else if (/\b(biscuit|cookie|chocolat|chocolate|bonbon|candy|chips)\b/.test(nameBlob)) {
    inferredCategory = 'snack';
  } else if (/\b(pain|bread|baguette|brioche)\b/.test(nameBlob) && items.length <= 5) {
    inferredCategory = 'bread';
  }
  return {
    name: recipe?.name || '',
    category: inferredCategory,
    weight_g: totalGrams || null,
    ingredients,
    nutrition: {
      energy_kcal: per100('kcal'),
      fat_g: per100('fat_g'),
      saturated_fat_g: per100('sat_fat_g'),
      carbs_g: per100('carbs_g'),
      sugars_g: per100('sugars_g'),
      fiber_g: per100('fiber_g'),
      protein_g: per100('protein_g'),
      salt_g: per100('salt_g'),
      iron_mg: per100('iron_mg'),
      calcium_mg: per100('calcium_mg'),
      magnesium_mg: per100('magnesium_mg'),
      potassium_mg: per100('potassium_mg'),
      zinc_mg: per100('zinc_mg'),
      sodium_mg: per100('sodium_mg'),
      vit_a_ug: per100('vit_a_ug'),
      vit_c_mg: per100('vit_c_mg'),
      vit_d_ug: per100('vit_d_ug'),
      vit_e_mg: per100('vit_e_mg'),
      vit_k_ug: per100('vit_k_ug'),
      b1_mg: per100('b1_mg'),
      b2_mg: per100('b2_mg'),
      b3_mg: per100('b3_mg'),
      b6_mg: per100('b6_mg'),
      b9_ug: per100('b9_ug'),
      b12_ug: per100('b12_ug'),
      polyunsaturated_fat_g: per100('polyunsaturated_fat_g'),
      monounsaturated_fat_g: per100('monounsaturated_fat_g'),
      omega_3_g: per100('omega_3_g'),
      omega_6_g: per100('omega_6_g'),
      cholesterol_mg: per100('cholesterol_mg'),
    },
  };
}

/**
 * Save a recipe. `components` is the list of ingredient lines; each line
 * holds its own kcal + macros + grams (the UI derives these from the
 * scanned product or a Quick Add).
 */
export async function saveRecipe({ id, name, components, servings = 1 }) {
  const recipe = {
    id: id ?? (globalThis.crypto?.randomUUID?.() ?? `r${Date.now()}${Math.random().toString(36).slice(2)}`),
    // R14.1: verbatim name; UI layer handles the untitled fallback.
    name: String(name || '').trim(),
    servings: Math.max(1, Math.round(Number(servings) || 1)),
    components: (components || []).map((c) => {
      // Base macros — the ones the UI form captures.
      const row = {
        product_name: c.product_name,
        grams: Number(c.grams) || 0,
        kcal: Number(c.kcal) || 0,
        carbs_g: Number(c.carbs_g) || 0,
        fat_g: Number(c.fat_g) || 0,
        sat_fat_g: Number(c.sat_fat_g) || 0,
        sugars_g: Number(c.sugars_g) || 0,
        protein_g: Number(c.protein_g) || 0,
        salt_g: Number(c.salt_g) || 0,
      };
      // Gap fix 1: also pass through every micro the caller supplied
      // (e.g. from buildEntry when the recipe is built from scanned
      // components). Missing fields default to 0 so aggregateRecipe's
      // sum stays numerical.
      for (const f of RECIPE_MICRO_FIELDS) row[f] = Number(c[f]) || 0;
      return row;
    }),
    created_at: Date.now(),
    updated_at: Date.now(),
  };
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(recipe);
    tx.oncomplete = () => { db.close(); resolve(recipe); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function listRecipes() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => {
      db.close();
      resolve((req.result || []).sort((a, b) => (b.updated_at || 0) - (a.updated_at || 0)));
    };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

export async function deleteRecipe(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}
