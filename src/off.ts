/**
 * ============================================================================
 * Open Food Facts adapter  —  barcode → ProductInput
 * ============================================================================
 *
 * Hits the public OFF API to avoid an LLM call on products that are already
 * in the database (~70% of French supermarket SKUs). Falls back to null when
 * the product isn't found, the nutrition is empty, or the network fails, so
 * the caller can cleanly retry with the vision pipeline.
 *
 * Zero dependencies. Safe to call from both server (Node fetch) and browser.
 * ============================================================================
 */

import { parseIngredientsText } from './ocr-parser.ts';
import { scoreProduct, type ScoreAudit } from './scoring-engine.ts';
import type {
  Ingredient,
  NovaClass,
  NutritionPer100g,
  ProductCategory,
  ProductInput,
} from './scoring-engine.ts';

const OFF_ENDPOINT = 'https://world.openfoodfacts.org/api/v2/product';
const USER_AGENT = 'scaneat/0.1 (https://github.com/WW-Andene/Scann-eat)';

export interface OFFLookupOptions {
  signal?: AbortSignal;
  timeoutMs?: number;
}

type OFFResponse = {
  status?: number;
  product?: Record<string, unknown>;
};

/**
 * Fetch a product from Open Food Facts by barcode. Returns a fully-built
 * ProductInput ready to score, or null if OFF doesn't have it or the record
 * is too sparse to be useful.
 */
// ============================================================================
// Fix #14: IndexedDB cache for OFF results (7-day TTL)
//
// Previously every barcode scan hit the OFF API regardless of whether the
// product had been looked up moments ago. On a slow mobile connection the
// 4-second timeout caused a silent fallback to the vision-LLM path (slower
// and less accurate). A local cache eliminates repeat-barcode latency and
// makes the app usable offline after the first lookup.
//
// The cache lives in IndexedDB under the same 'scanneat' database that
// scan-history.js, consumption.js, etc. already use — just a new object
// store. DB_VERSION must advance whenever a new store is added; we use a
// defensive upgrade pattern (check objectStoreNames before creating).
// ============================================================================

const OFF_CACHE_DB   = 'scanneat';
const OFF_CACHE_VER  = 7;            // must be > current version (6) in scan-history.js
const OFF_CACHE_STORE = 'off_cache';
const OFF_CACHE_TTL  = 7 * 24 * 60 * 60 * 1000; // 7 days in ms

// Only available in browser environments (not Deno/Node server).
const _isBrowser = typeof indexedDB !== 'undefined';

async function offCacheOpen(): Promise<IDBDatabase | null> {
  if (!_isBrowser) return null;
  return new Promise((resolve) => {
    const req = indexedDB.open(OFF_CACHE_DB, OFF_CACHE_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      // Carry over all existing stores from prior versions (defensive).
      for (const store of [
        'pending_scans', 'history', 'consumption', 'weight',
        'meal_templates', 'recipes', 'activity',
      ] as const) {
        if (!db.objectStoreNames.contains(store)) {
          if (store === 'history') {
            const s = db.createObjectStore(store, { keyPath: 'id' });
            s.createIndex('created', 'createdAt');
          } else if (store === 'consumption' || store === 'activity') {
            const s = db.createObjectStore(store, { keyPath: 'id' });
            s.createIndex('date', 'date');
          } else if (store === 'weight') {
            const s = db.createObjectStore(store, { keyPath: 'id' });
            s.createIndex('date', 'date', { unique: true });
          } else {
            db.createObjectStore(store, { keyPath: 'id' });
          }
        }
      }
      if (!db.objectStoreNames.contains(OFF_CACHE_STORE)) {
        const s = db.createObjectStore(OFF_CACHE_STORE, { keyPath: 'barcode' });
        s.createIndex('cachedAt', 'cachedAt');
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => resolve(null); // cache miss on IDB error — never block
  });
}

async function offCacheGet(barcode: string): Promise<ProductInput | null> {
  const db = await offCacheOpen();
  if (!db) return null;
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(OFF_CACHE_STORE, 'readonly');
      const req = tx.objectStore(OFF_CACHE_STORE).get(barcode);
      req.onsuccess = () => {
        db.close();
        const rec = req.result as { barcode: string; cachedAt: number; product: ProductInput } | undefined;
        if (!rec) return resolve(null);
        if (Date.now() - rec.cachedAt > OFF_CACHE_TTL) return resolve(null); // stale
        resolve(rec.product);
      };
      req.onerror = () => { db.close(); resolve(null); };
    } catch { db.close(); resolve(null); }
  });
}

async function offCachePut(barcode: string, product: ProductInput): Promise<void> {
  const db = await offCacheOpen();
  if (!db) return;
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(OFF_CACHE_STORE, 'readwrite');
      tx.objectStore(OFF_CACHE_STORE).put({ barcode, cachedAt: Date.now(), product });
      tx.oncomplete = () => { db.close(); resolve(); };
      tx.onerror = () => { db.close(); resolve(); }; // best-effort
    } catch { db.close(); resolve(); }
  });
}

export async function fetchFromOFF(
  barcode: string,
  opts: OFFLookupOptions = {},
): Promise<ProductInput | null> {
  const digits = barcode.replace(/\D/g, '');
  if (digits.length < 8 || digits.length > 14) return null;

  // Fix #14: check cache first — avoids network on repeat barcodes and
  // on slow mobile connections where the 4-second timeout would silently
  // fall back to the vision-LLM path.
  const cached = await offCacheGet(digits);
  if (cached) return cached;

  const url = `${OFF_ENDPOINT}/${encodeURIComponent(digits)}.json?fields=${FIELDS.join(',')}`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), opts.timeoutMs ?? 4000);
  const signal = opts.signal
    ? anySignal([opts.signal, controller.signal])
    : controller.signal;

  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      signal,
    });
    if (!res.ok) return null;
    const json = (await res.json()) as OFFResponse;
    if (json.status !== 1 || !json.product) return null;
    const product = mapOFFProduct(json.product);
    // Fix #14: persist to cache so the next lookup for the same barcode is instant.
    if (product) offCachePut(digits, product).catch(() => { /* best-effort */ });
    return product;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

const FIELDS = [
  'product_name',
  'product_name_fr',
  'generic_name_fr',
  'brands',
  'categories_tags',
  'ingredients_text_fr',
  'ingredients_text',
  'nova_group',
  'nutriments',
  'labels_tags',
  'origins',
  'countries_tags',
  'quantity',
  'ecoscore_grade',
  'ecoscore_score',
  'nutrition_grades',
];

// ---------- Mapping ----------

function num(v: unknown): number {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string') {
    const n = parseFloat(v.replace(',', '.'));
    if (Number.isFinite(n)) return n;
  }
  return 0;
}

function numNullable(v: unknown): number | null {
  if (v == null || v === '') return null;
  const n = num(v);
  return Number.isFinite(n) ? n : null;
}

function nutritionFromOFF(n: Record<string, unknown> | undefined): NutritionPer100g {
  const nut = n ?? {};
  // OFF stores micronutrients as grams per 100 g (e.g. 0.012 for 12 mg of
  // iron, 0.000002 for 2 µg of vitamin D). Convert to the app's preferred
  // display units (mg for minerals + water-soluble vitamins typically
  // reported in mg; µg for fat-soluble vitamins, B12, folate).
  const gToMg = (v: unknown) => num(v) * 1000;
  const gToUg = (v: unknown) => num(v) * 1_000_000;

  // Salt vs. sodium: OFF carries both salt_100g and sodium_100g as
  // separate fields, and not every product reports both — manually
  // entered or older imports frequently have only one. Previously
  // salt_g read salt_100g alone via num() (defaults missing to 0),
  // which silently turned "salt not declared" into "0g salt": an
  // artificially perfect negative-nutrients score AND a wrong 0mg
  // reading on the sodium dashboard tracker for anyone monitoring
  // intake. EU/FSA standard conversion is salt(g) = sodium(g) × 2.5;
  // derive whichever side is missing from the other before falling
  // back to 0 when neither is present.
  const saltRaw = numNullable(nut['salt_100g']);
  const sodiumRaw = numNullable(nut['sodium_100g']);
  const salt_g = saltRaw ?? (sodiumRaw != null ? sodiumRaw * 2.5 : 0);
  const sodium_mg = saltRaw != null && sodiumRaw == null
    ? (saltRaw / 2.5) * 1000
    : gToMg(nut['sodium_100g']);

  return {
    energy_kcal: num(nut['energy-kcal_100g'] ?? nut['energy_100g']),
    fat_g: num(nut['fat_100g']),
    saturated_fat_g: num(nut['saturated-fat_100g']),
    carbs_g: num(nut['carbohydrates_100g']),
    sugars_g: num(nut['sugars_100g']),
    added_sugars_g: numNullable(nut['added-sugars_100g']),
    fiber_g: num(nut['fiber_100g']),
    protein_g: num(nut['proteins_100g']),
    salt_g,
    trans_fat_g: numNullable(nut['trans-fat_100g']),
    // Minerals
    iron_mg:       gToMg(nut['iron_100g']),
    calcium_mg:    gToMg(nut['calcium_100g']),
    magnesium_mg:  gToMg(nut['magnesium_100g']),
    potassium_mg:  gToMg(nut['potassium_100g']),
    zinc_mg:       gToMg(nut['zinc_100g']),
    sodium_mg,
    // Vitamins
    vit_a_ug:      gToUg(nut['vitamin-a_100g']),
    vit_c_mg:      gToMg(nut['vitamin-c_100g']),
    vit_d_ug:      gToUg(nut['vitamin-d_100g']),
    vit_e_mg:      gToMg(nut['vitamin-e_100g']),
    vit_k_ug:      gToUg(nut['vitamin-k_100g']),
    b1_mg:         gToMg(nut['vitamin-b1_100g']),
    b2_mg:         gToMg(nut['vitamin-b2_100g']),
    b3_mg:         gToMg(nut['vitamin-pp_100g'] ?? nut['vitamin-b3_100g']),
    b6_mg:         gToMg(nut['vitamin-b6_100g']),
    b9_ug:         gToUg(nut['vitamin-b9_100g'] ?? nut['folates_100g']),
    b12_ug:        gToUg(nut['vitamin-b12_100g']),
    // Fat subdivisions + cholesterol
    polyunsaturated_fat_g: num(nut['polyunsaturated-fat_100g']),
    monounsaturated_fat_g: num(nut['monounsaturated-fat_100g']),
    omega_3_g:             num(nut['omega-3-fat_100g']),
    omega_6_g:             num(nut['omega-6-fat_100g']),
    cholesterol_mg:        gToMg(nut['cholesterol_100g']),
  };
}

const VITAMIN_KEY_PATTERNS: Array<[RegExp, string]> = [
  [/^vitamin-a_/,  'Vitamin A'],
  [/^vitamin-c_/,  'Vitamin C'],
  [/^vitamin-d_/,  'Vitamin D'],
  [/^vitamin-e_/,  'Vitamin E'],
  [/^vitamin-b\d+_/, 'Vitamin B'],
  [/^calcium_/,    'Calcium'],
  [/^iron_/,       'Iron'],
  [/^magnesium_/,  'Magnesium'],
  [/^potassium_/,  'Potassium'],
  [/^zinc_/,       'Zinc'],
  [/^iodine_/,     'Iodine'],
  [/^selenium_/,   'Selenium'],
];

function declaredMicronutrients(n: Record<string, unknown> | undefined): string[] {
  if (!n) return [];
  const seen = new Set<string>();
  for (const key of Object.keys(n)) {
    if (!key.endsWith('_100g')) continue;
    for (const [re, label] of VITAMIN_KEY_PATTERNS) {
      if (re.test(key) && num((n as Record<string, unknown>)[key]) > 0) {
        seen.add(label);
      }
    }
  }
  return Array.from(seen);
}

/**
 * Map OFF's `categories_tags` array (["en:breakfast-cereals", "en:cereal-bars"])
 * to our narrower ProductCategory. Checked most-specific first.
 */
const CATEGORY_MAP: Array<[RegExp, ProductCategory]> = [
  [/sandwich/i,                            'sandwich'],
  [/ready[-_]?meal|plat[-_]?cuisin/i,      'ready_meal'],
  [/breakfast[-_]?cereal|cereale-petit/i,  'breakfast_cereal'],
  [/bread|pain|baguette/i,                 'bread'],
  [/yogurt|yoghurt|yaourt|skyr|fromage[-_]?blanc|faisselle|quark|petit[-_]?suisse|fermented[-_]?dair/i, 'yogurt'],
  [/cheese|fromage/i,                      'cheese'],
  [/processed[-_]?meat|charcuterie|saucisson|jambon-sec/i, 'processed_meat'],
  [/fresh[-_]?meat|viande-fraiche|boucherie/i, 'fresh_meat'],
  [/fish|poisson|seafood|crustace/i,       'fish'],
  [/confection|candy|chocolate|bonbon|biscuit/i, 'snack_sweet'],
  [/chip|crisp|crouton|apero-salee/i,      'snack_salty'],
  [/soda|soft[-_]?drink|cola|boisson-gazeuse/i, 'beverage_soft'],
  [/juice|jus|nectar/i,                    'beverage_juice'],
  [/water|eau-minerale|eau-de-source/i,    'beverage_water'],
  [/condiment|sauce|mayonnaise|ketchup|mustard|moutarde/i, 'condiment'],
  [/oil|huile|matiere-grasse|margarine/i,  'oil_fat'],
];

function categoryFromOFF(tags: unknown): ProductCategory {
  if (!Array.isArray(tags)) return 'other';
  const joined = tags.filter((t) => typeof t === 'string').join(' ');
  for (const [re, cat] of CATEGORY_MAP) {
    if (re.test(joined)) return cat;
  }
  return 'other';
}

/**
 * Inverse of the CATEGORY_MAP: given our narrow ProductCategory enum,
 * return the best OFF `categories_tags` search term. Used by the "similar
 * but better" suggestion feature to query the OFF catalog for alternatives
 * in the same category as the scanned product.
 *
 * Returns null for 'other' — we shouldn't surface random suggestions when
 * we couldn't place the product.
 */
const CATEGORY_TO_OFF_TAG: Record<string, string> = {
  sandwich:          'en:sandwiches',
  ready_meal:        'en:prepared-meals',
  breakfast_cereal:  'en:breakfast-cereals',
  bread:             'en:breads',
  yogurt:            'en:yogurts',
  cheese:            'en:cheeses',
  processed_meat:    'en:processed-meats',
  fresh_meat:        'en:meats',
  fish:              'en:fishes',
  snack_sweet:       'en:sweet-snacks',
  snack_salty:       'en:salty-snacks',
  beverage_soft:     'en:sodas',
  beverage_juice:    'en:fruit-juices',
  beverage_water:    'en:waters',
  condiment:         'en:condiments',
  oil_fat:           'en:fats',
};

export function suggestionTagFor(category: ProductCategory): string | null {
  return CATEGORY_TO_OFF_TAG[category] ?? null;
}

function novaFromOFF(v: unknown): NovaClass {
  const n = typeof v === 'number' ? v : parseInt(String(v ?? ''), 10);
  if (n === 1 || n === 2 || n === 3 || n === 4) return n as NovaClass;
  return 4; // conservative when OFF has no NOVA group
}

function organicFromOFF(labels: unknown): boolean {
  if (!Array.isArray(labels)) return false;
  return labels.some(
    (t) =>
      typeof t === 'string' &&
      /^en:(organic|bio|eu-organic|ab-agriculture-biologique)$/i.test(t),
  );
}

function mapOFFProduct(p: Record<string, unknown>): ProductInput | null {
  const name =
    (typeof p.product_name_fr === 'string' && p.product_name_fr.trim()) ||
    (typeof p.product_name === 'string' && p.product_name.trim()) ||
    '(produit sans nom)';

  const ingredientsText =
    (typeof p.ingredients_text_fr === 'string' && p.ingredients_text_fr.trim()) ||
    (typeof p.ingredients_text === 'string' && p.ingredients_text.trim()) ||
    '';

  const ingredients: Ingredient[] = ingredientsText
    ? parseIngredientsText(ingredientsText)
    : [];

  const nutrients = p.nutriments as Record<string, unknown> | undefined;
  const nutrition = nutritionFromOFF(nutrients);
  const declared_micronutrients = declaredMicronutrients(nutrients);

  // Reject records that are too empty to score meaningfully.
  if (ingredients.length === 0 && nutrition.energy_kcal === 0 && nutrition.protein_g === 0) {
    return null;
  }

  const origin =
    typeof p.origins === 'string' && p.origins.trim()
      ? p.origins.trim()
      : null;

  const ecoGrade =
    typeof p.ecoscore_grade === 'string' && /^[a-e]$/i.test(p.ecoscore_grade)
      ? (p.ecoscore_grade.toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e')
      : null;
  const ecoValue =
    typeof p.ecoscore_score === 'number' && Number.isFinite(p.ecoscore_score)
      ? p.ecoscore_score
      : null;
  const nutriscoreGrade =
    typeof p.nutrition_grades === 'string' && /^[a-e]$/i.test(p.nutrition_grades)
      ? (p.nutrition_grades.toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e')
      : null;

  return {
    name,
    category: categoryFromOFF(p.categories_tags),
    nova_class: novaFromOFF(p.nova_group),
    ingredients,
    nutrition,
    origin,
    organic: organicFromOFF(p.labels_tags),
    has_health_claims: false,
    has_misleading_marketing: false,
    named_oils: !ingredients.some((i) =>
      /huile v[eé]g[eé]tale|vegetable oil/i.test(i.name),
    ),
    origin_transparent: !!origin,
    declared_micronutrients,
    ecoscore_grade: ecoGrade,
    ecoscore_value: ecoValue,
    nutriscore_grade: nutriscoreGrade,
  };
}

/** Polyfill AbortSignal.any for older Node / Safari. */
function anySignal(signals: AbortSignal[]): AbortSignal {
  if ('any' in AbortSignal && typeof (AbortSignal as { any?: unknown }).any === 'function') {
    return (AbortSignal as unknown as { any: (s: AbortSignal[]) => AbortSignal }).any(signals);
  }
  const controller = new AbortController();
  for (const s of signals) {
    if (s.aborted) {
      controller.abort(s.reason);
      break;
    }
    s.addEventListener('abort', () => controller.abort(s.reason), { once: true });
  }
  return controller.signal;
}

// ============================================================================
// Hybrid OFF + LLM merge
// ============================================================================

/**
 * Returns true if the OFF record is too thin to score well on its own. When
 * both an OFF hit and user-provided photos exist, a sparse record triggers an
 * LLM augmentation pass to fill the gaps.
 */
export function isOFFSparse(p: ProductInput): boolean {
  const n = p.nutrition;
  const hasNutrition = n.energy_kcal > 0 || n.protein_g > 0 || n.carbs_g > 0;
  const hasIngredients = p.ingredients.length >= 3;
  const hasCategory = p.category !== 'other';

  // Fix #13: A product can have complete macros but zero micronutrients (very
  // common for French SKUs in OFF). Those products would previously be judged
  // non-sparse, so the LLM was never called to fill vitamin/mineral data that
  // scoreNutritionalDensity's NRV-15% loop uses. We now also flag as sparse
  // when all tracked micronutrients are null/zero — the LLM augmentation pass
  // can then fill at least some of them from the label photo.
  const MICRO_FIELDS = [
    'iron_mg', 'calcium_mg', 'magnesium_mg', 'potassium_mg', 'zinc_mg',
    'vit_a_ug', 'vit_c_mg', 'vit_d_ug', 'vit_e_mg', 'vit_k_ug',
    'b1_mg', 'b2_mg', 'b3_mg', 'b6_mg', 'b9_ug', 'b12_ug',
  ] as const;
  const hasMicronutrients = MICRO_FIELDS.some(
    (f) => (n[f] ?? 0) > 0,
  );

  return !hasNutrition || !hasIngredients || !hasCategory || !hasMicronutrients;
}

/**
 * Merge an OFF record with an LLM extraction. OFF is the trusted baseline;
 * fields the LLM can fill (when OFF has 0 or empty) are pulled in from the
 * LLM side. This mirrors the "authoritative > heuristic" stance of the rest
 * of the engine.
 */
export function mergeOFFWithLLM(off: ProductInput, llm: ProductInput): ProductInput {
  const prefer = <T>(offVal: T, llmVal: T, isEmpty: (v: T) => boolean): T =>
    isEmpty(offVal) ? llmVal : offVal;

  const emptyStr = (s: unknown) => !s || (typeof s === 'string' && s.trim() === '') || s === '(produit sans nom)';
  const emptyNum = (n: unknown) => n == null || n === 0;
  const emptyArr = (a: unknown) => !Array.isArray(a) || a.length === 0;

  return {
    name: prefer(off.name, llm.name, emptyStr),
    category: off.category !== 'other' ? off.category : llm.category,
    nova_class: off.nova_class || llm.nova_class,
    ingredients: emptyArr(off.ingredients) || off.ingredients.length < 3 ? llm.ingredients : off.ingredients,
    // Nutrition merge: required macros use prefer() (OFF over LLM unless
    // OFF is 0/missing); optional micronutrients use ??-cascade (the
    // first non-null/non-undefined wins, so a 0 declared by OFF is
    // preserved over an LLM hallucination). Previously only iron / Ca /
    // vit_D / B12 were merged — every other micronutrient declared on a
    // product was silently dropped on the merge path, including the
    // vitamins (A, C, E, K, B-complex), the minerals (Mg, K, Zn, Na),
    // and the macro subdivisions (poly/mono/ω-3/ω-6/cholesterol). Those
    // fields are read by scoreNutritionalDensity's NRV-15% bonus loop,
    // so dropping them silently lowered scores on multi-vitamin products
    // that fell through the OFF-sparse → LLM merge path.
    nutrition: {
      energy_kcal:     prefer(off.nutrition.energy_kcal,     llm.nutrition.energy_kcal,     emptyNum),
      fat_g:           prefer(off.nutrition.fat_g,           llm.nutrition.fat_g,           emptyNum),
      saturated_fat_g: prefer(off.nutrition.saturated_fat_g, llm.nutrition.saturated_fat_g, emptyNum),
      carbs_g:         prefer(off.nutrition.carbs_g,         llm.nutrition.carbs_g,         emptyNum),
      sugars_g:        prefer(off.nutrition.sugars_g,        llm.nutrition.sugars_g,        emptyNum),
      added_sugars_g:  off.nutrition.added_sugars_g  ?? llm.nutrition.added_sugars_g  ?? null,
      fiber_g:         prefer(off.nutrition.fiber_g,         llm.nutrition.fiber_g,         emptyNum),
      protein_g:       prefer(off.nutrition.protein_g,       llm.nutrition.protein_g,       emptyNum),
      salt_g:          prefer(off.nutrition.salt_g,          llm.nutrition.salt_g,          emptyNum),
      trans_fat_g:     off.nutrition.trans_fat_g     ?? llm.nutrition.trans_fat_g     ?? null,
      // Minerals
      iron_mg:         off.nutrition.iron_mg         ?? llm.nutrition.iron_mg,
      calcium_mg:      off.nutrition.calcium_mg      ?? llm.nutrition.calcium_mg,
      magnesium_mg:    off.nutrition.magnesium_mg    ?? llm.nutrition.magnesium_mg,
      potassium_mg:    off.nutrition.potassium_mg    ?? llm.nutrition.potassium_mg,
      zinc_mg:         off.nutrition.zinc_mg         ?? llm.nutrition.zinc_mg,
      sodium_mg:       off.nutrition.sodium_mg       ?? llm.nutrition.sodium_mg,
      // Vitamins
      vit_a_ug:        off.nutrition.vit_a_ug        ?? llm.nutrition.vit_a_ug,
      vit_c_mg:        off.nutrition.vit_c_mg        ?? llm.nutrition.vit_c_mg,
      vit_d_ug:        off.nutrition.vit_d_ug        ?? llm.nutrition.vit_d_ug,
      vit_e_mg:        off.nutrition.vit_e_mg        ?? llm.nutrition.vit_e_mg,
      vit_k_ug:        off.nutrition.vit_k_ug        ?? llm.nutrition.vit_k_ug,
      b1_mg:           off.nutrition.b1_mg           ?? llm.nutrition.b1_mg,
      b2_mg:           off.nutrition.b2_mg           ?? llm.nutrition.b2_mg,
      b3_mg:           off.nutrition.b3_mg           ?? llm.nutrition.b3_mg,
      b6_mg:           off.nutrition.b6_mg           ?? llm.nutrition.b6_mg,
      b9_ug:           off.nutrition.b9_ug           ?? llm.nutrition.b9_ug,
      b12_ug:          off.nutrition.b12_ug          ?? llm.nutrition.b12_ug,
      // Macro subdivisions
      polyunsaturated_fat_g: off.nutrition.polyunsaturated_fat_g ?? llm.nutrition.polyunsaturated_fat_g,
      monounsaturated_fat_g: off.nutrition.monounsaturated_fat_g ?? llm.nutrition.monounsaturated_fat_g,
      omega_3_g:       off.nutrition.omega_3_g       ?? llm.nutrition.omega_3_g,
      omega_6_g:       off.nutrition.omega_6_g       ?? llm.nutrition.omega_6_g,
      cholesterol_mg:  off.nutrition.cholesterol_mg  ?? llm.nutrition.cholesterol_mg,
    },
    weight_g: off.weight_g ?? llm.weight_g,
    origin: off.origin ?? llm.origin ?? null,
    organic: off.organic || llm.organic,
    has_health_claims: off.has_health_claims || llm.has_health_claims,
    has_misleading_marketing: off.has_misleading_marketing || llm.has_misleading_marketing,
    named_oils: off.named_oils ?? llm.named_oils,
    origin_transparent: off.origin_transparent || llm.origin_transparent,
    // Union of declared micronutrients across both sources, deduped.
    // Previously OFF-only — products where the LLM read additional
    // declared nutrients off the panel had those dropped on merge.
    declared_micronutrients: Array.from(new Set([
      ...(off.declared_micronutrients ?? []),
      ...(llm.declared_micronutrients ?? []),
    ])),
    // Eco + nutriscore fields are only ever known from OFF; LLM doesn't see them.
    ecoscore_grade: off.ecoscore_grade,
    ecoscore_value: off.ecoscore_value,
    nutriscore_grade: off.nutriscore_grade,
  };
}

/**
 * Search OFF for products matching any of the given category tags (e.g.
 * `en:yogurts`, `en:fresh-cheeses`). Returns an array of ProductInput ready
 * to feed scoreProduct + checkDiet — this is the data plumbing behind the
 * "similar but compliant" suggestion feature.
 *
 * Uses the OFF search-v1 endpoint; v2 search exists but its parameter shape
 * is unstable. page_size is capped low (default 20) because we don't need a
 * full catalog — we only surface a handful of alternatives.
 */
const OFF_SEARCH_ENDPOINT = 'https://world.openfoodfacts.org/cgi/search.pl';

export async function searchOFFByCategory(
  tags: string[],
  opts: OFFLookupOptions & { pageSize?: number } = {},
): Promise<ProductInput[]> {
  if (!Array.isArray(tags) || tags.length === 0) return [];

  // Pick the first tag as the primary filter — more tags via tagtype_N is
  // possible but the OFF search API treats them as AND, which is too strict
  // for "similar products" matching.
  const primaryTag = tags[0];
  const params = new URLSearchParams({
    action: 'process',
    tagtype_0: 'categories',
    tag_contains_0: 'contains',
    tag_0: primaryTag,
    sort_by: 'popularity_key', // most common first
    page_size: String(opts.pageSize ?? 20),
    fields: FIELDS.join(','),
    json: '1',
  });
  const url = `${OFF_SEARCH_ENDPOINT}?${params.toString()}`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), opts.timeoutMs ?? 5000);
  const signal = opts.signal ? anySignal([opts.signal, controller.signal]) : controller.signal;

  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      signal,
    });
    if (!res.ok) return [];
    const json = (await res.json()) as { products?: Array<Record<string, unknown>> };
    const products = Array.isArray(json.products) ? json.products : [];
    const mapped: ProductInput[] = [];
    for (const raw of products) {
      const p = mapOFFProduct(raw);
      if (p) mapped.push(p);
    }
    return mapped;
  } catch {
    return [];
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * "Similar but better" — take a list of OFF candidates, score each one,
 * filter to only those strictly beating the reference product, and return
 * the top N by score. Pure: caller passes the candidates so this is
 * trivially testable without an OFF round-trip.
 *
 * `dietFilter(candidate)` is an optional predicate; the caller plugs in
 * checkDiet() bound to the user's active diet. Candidates where the
 * filter returns false are dropped.
 */
export interface Alternative {
  product: ProductInput;
  audit: ScoreAudit;
}

export function rankAlternatives(
  reference: ProductInput,
  candidates: ProductInput[],
  opts: {
    max?: number;
    dietFilter?: (p: ProductInput) => boolean;
  } = {},
): Alternative[] {
  const max = opts.max ?? 3;
  const filter = opts.dietFilter ?? (() => true);
  const refAudit = scoreProduct(reference);

  const ranked = candidates
    .filter((c) => c.name && c.name !== reference.name) // don't suggest the same item
    .filter(filter)
    .map((product) => ({ product, audit: scoreProduct(product) }))
    .filter((a) => a.audit.score > refAudit.score)
    .sort((a, b) => b.audit.score - a.audit.score);

  return ranked.slice(0, max);
}

/**
 * Compare OFF + LLM nutrition extractions and warn when they disagree
 * materially. Per-nutrient thresholds reflect how close to a regulatory
 * cutoff each value sits — a 20 % swing in protein is rarely actionable,
 * but the same 20 % on sat-fat can flip a product across the FSA red
 * line.
 *
 * Two gates per check:
 *   1. Relative threshold (per nutrient).
 *   2. Absolute floor (per nutrient) — guards against firing on
 *      "0.05g vs 0.10g" where the % is meaningless.
 *
 * Returns a list of human-readable warnings (caller writes them to the
 * audit's warnings array).
 */
// Fix #15: conflicts now carry a severity tier so the UI can render them
// differently instead of showing every disagreement in the same generic banner.
// HIGH = safety-relevant (trans fat, saturated fat, salt, energy).
// MEDIUM = informational (sugars, protein).
export type ConflictSeverity = 'high' | 'medium';
export type SourceConflict = { message: string; severity: ConflictSeverity };

const CONFLICT_THRESHOLDS: Record<
  'sugars_g' | 'saturated_fat_g' | 'salt_g' | 'protein_g' | 'trans_fat_g' | 'energy_kcal',
  { label: string; relativeThreshold: number; absoluteFloor: number; unit: string; severity: ConflictSeverity }
> = {
  sugars_g:        { label: 'Sugars',    relativeThreshold: 0.20, absoluteFloor: 2,    unit: 'g',    severity: 'medium' },
  saturated_fat_g: { label: 'Sat fat',   relativeThreshold: 0.15, absoluteFloor: 1,    unit: 'g',    severity: 'high'   },
  salt_g:          { label: 'Salt',      relativeThreshold: 0.15, absoluteFloor: 0.3,  unit: 'g',    severity: 'high'   },
  protein_g:       { label: 'Protein',   relativeThreshold: 0.25, absoluteFloor: 3,    unit: 'g',    severity: 'medium' },
  trans_fat_g:     { label: 'Trans fat', relativeThreshold: 0.10, absoluteFloor: 0.05, unit: 'g',    severity: 'high'   },
  energy_kcal:     { label: 'Energy',    relativeThreshold: 0.20, absoluteFloor: 30,   unit: 'kcal', severity: 'high'   },
};

export function detectSourceConflicts(off: ProductInput, llm: ProductInput): SourceConflict[] {
  const conflicts: SourceConflict[] = [];
  for (const [field, cfg] of Object.entries(CONFLICT_THRESHOLDS) as Array<
    [keyof typeof CONFLICT_THRESHOLDS, typeof CONFLICT_THRESHOLDS[keyof typeof CONFLICT_THRESHOLDS]]
  >) {
    const a = Number(off.nutrition[field] ?? 0);
    const b = Number(llm.nutrition[field] ?? 0);
    const peak = Math.max(a, b);
    if (a <= 0 || b <= 0 || peak < cfg.absoluteFloor) continue;
    const diff = Math.abs(a - b) / peak;
    if (diff > cfg.relativeThreshold) {
      conflicts.push({
        message:
          `${cfg.label}: OFF ${a}${cfg.unit} vs photo ${b}${cfg.unit} ` +
          `(${Math.round(diff * 100)}% difference, threshold ${Math.round(cfg.relativeThreshold * 100)}% — possible reformulation)`,
        severity: cfg.severity,
      });
    }
  }
  return conflicts;
}
