/**
 * Meal templates — named snapshots of consumption entries that can be
 * re-applied to any date/meal with one tap.
 *
 * Scope: no calendar-based planning yet; this is the "saved meals" layer,
 * which is the pattern MFP calls "Create a meal" / "Apply meal".
 */

const DB_NAME = 'scanneat';
const DB_VERSION = 6;
const STORE = 'meal_templates';

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('pending_scans')) db.createObjectStore('pending_scans', { keyPath: 'id' });
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
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'id' });
      if (!db.objectStoreNames.contains('recipes')) db.createObjectStore('recipes', { keyPath: 'id' });
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

/**
 * Save a template. `items` is an array of ConsumptionEntry-like objects
 * without date/timestamp/id — those get re-generated on apply.
 */
export async function saveTemplate({ id, name, meal = 'snack', items }) {
  // R14.1: store the user's name verbatim (empty string allowed).
  // Previous 'Sans nom' fallback baked a French literal into the
  // user's IDB, visible in the list for English-locale users as a
  // French word amidst otherwise-translated UI. Display-time
  // fallback now lives in the UI layer (see t('untitledTemplate')).
  //
  // R20.1: preserve the caller-supplied id (restore path + future
  // update flow). Previously saveTemplate always regenerated the id,
  // which meant backup/restore broke meal-plan slot references
  // (slots store `template.id` and can't find them after restore).
  const template = {
    id: id ?? (globalThis.crypto?.randomUUID?.() ?? `t${Date.now()}${Math.random().toString(36).slice(2)}`),
    name: String(name || '').trim(),
    meal,
    items: (items || []).map((i) => ({
      product_name: i.product_name,
      grams: i.grams || 0,
      meal: i.meal || meal,
      kcal: i.kcal || 0,
      carbs_g: i.carbs_g || 0,
      fat_g: i.fat_g || 0,
      sat_fat_g: i.sat_fat_g || 0,
      sugars_g: i.sugars_g || 0,
      salt_g: i.salt_g || 0,
      protein_g: i.protein_g || 0,
      quickAdd: !!i.quickAdd,
    })),
    created_at: Date.now(),
  };
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(template);
    tx.oncomplete = () => { db.close(); resolve(template); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function listTemplates() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => {
      db.close();
      resolve((req.result || []).sort((a, b) => (b.created_at || 0) - (a.created_at || 0)));
    };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

export async function deleteTemplate(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

/** Total kcal of a template (pure helper). */
export function templateKcal(t) {
  if (!t?.items) return 0;
  return Math.round(t.items.reduce((s, i) => s + (i.kcal || 0), 0));
}

/**
 * Fix #4 — buildTemplateProductInput: synthesise a ProductInput so
 * scoreProduct can grade saved meal templates. Templates store items
 * already as totals (kcal + macros per item, grams optional). The
 * recipe helper wants per-100 g, so we rebase on sum(grams) or a
 * 100 g nominal basis when grams aren't declared.
 */
export function buildTemplateProductInput(template) {
  const items = (template?.items || []).filter((i) => i && (i.product_name || i.kcal));
  const totalGrams = items.reduce((acc, it) => acc + (Number(it?.grams) || 0), 0);
  const basis = totalGrams > 0 ? totalGrams : 100;
  const per100 = (key) => {
    const total = items.reduce((acc, it) => acc + (Number(it?.[key]) || 0), 0);
    return (total * 100) / basis;
  };
  const ingredients = items.map((it) => ({
    name: String(it.product_name || '').trim() || '—',
    percentage: totalGrams > 0 ? Math.round((Number(it.grams || 0) / totalGrams) * 1000) / 10 : null,
    category: 'food',
  }));
  return {
    name: template?.name || '',
    category: 'other',
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
    },
  };
}

/**
 * Build the concrete consumption entries a template would produce for a
 * given date + meal override. Returned entries still need to be persisted
 * via the consumption module's put path (caller responsibility; keeps
 * cross-module coupling low).
 */
export function expandTemplate(template, date, mealOverride = null, now = Date.now()) {
  return template.items.map((i, idx) => ({
    id: globalThis.crypto?.randomUUID?.() ?? `a${now}${idx}${Math.random().toString(36).slice(2)}`,
    date,
    timestamp: now + idx,
    product_name: i.product_name,
    category: 'other',
    grams: i.grams || 0,
    meal: mealOverride || i.meal || template.meal || 'snack',
    kcal: i.kcal || 0,
    carbs_g: i.carbs_g || 0,
    fat_g: i.fat_g || 0,
    sat_fat_g: i.sat_fat_g || 0,
    sugars_g: i.sugars_g || 0,
    salt_g: i.salt_g || 0,
    protein_g: i.protein_g || 0,
    quickAdd: i.quickAdd,
    fromTemplate: template.id,
  }));
}
