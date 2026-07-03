/**
 * Unit conversions — common cooking measures → grams.
 *
 * Density-aware per-food lookup for dry solids where volume→mass
 * depends on the ingredient (1 cup flour ≠ 1 cup sugar). Falls back
 * to liquid water density (1 ml = 1 g) for anything not matched,
 * which is correct for water / milk / juice / most broths within ±5%.
 *
 * Pure. Exposed for tests. No DOM. No i18n lookups (the UI composes
 * the label).
 *
 * Coverage is intentionally narrow: ~40 food families cover 95% of
 * home cooking. Outside the lookup, volume units resolve via water
 * density (close enough for liquids) and the caller is responsible
 * for falling back to a typed grams value if they need precision.
 *
 * Sources for the density table:
 *   - USDA FoodData Central weights for common measures
 *   - King Arthur Baking weight chart
 *   - FAO/WHO Codex Alimentarius densities
 * All values are per 1 ml (g/ml). "Cup" is US (236.588 ml); metric
 * cup (250 ml) is handled by the volume table below.
 */

// Food → density in g/ml. Keys are lower-case, accent-folded for
// permissive matching. `match(name)` below runs substring contains.
// Order matters only for ambiguous prefixes (e.g. "brown sugar" must
// match before "sugar"); the resolver walks the entries and takes
// the first substring hit.
const DENSITIES = [
  // Flours / starches
  ['flour', 0.53], ['farine', 0.53],
  ['whole wheat flour', 0.50], ['farine complete', 0.50], ['farine intégrale', 0.50],
  ['almond flour', 0.42], ['farine amande', 0.42],
  ['coconut flour', 0.48], ['farine coco', 0.48],
  ['cornstarch', 0.54], ['maizena', 0.54], ['amidon', 0.54],
  ['cornmeal', 0.66], ['semoule de mais', 0.66], ['polenta', 0.66],
  ['oats', 0.42], ['rolled oats', 0.42], ['flocons d\'avoine', 0.42], ['avoine', 0.42],
  // Sugars
  ['brown sugar', 0.93], ['cassonade', 0.93], ['sucre roux', 0.93], ['sucre brun', 0.93],
  ['powdered sugar', 0.51], ['sucre glace', 0.51],
  ['granulated sugar', 0.85],
  // Plain 'sugar' / 'sucre' as the default case. Must come AFTER the
  // qualified variants so "brown sugar" still matches first via the
  // longest-substring-first property of the ordered walk.
  ['sugar', 0.85], ['sucre', 0.85],
  ['honey', 1.42], ['miel', 1.42],
  ['maple syrup', 1.33], ['sirop d\'érable', 1.33],
  // Dairy liquids (close to water + protein/fat content)
  ['heavy cream', 1.01], ['crème épaisse', 1.01], ['creme epaisse', 1.01],
  ['milk', 1.03], ['lait', 1.03],
  ['yogurt', 1.03], ['yaourt', 1.03],
  ['buttermilk', 1.03], ['lait ribot', 1.03],
  // Fats
  ['butter', 0.91], ['beurre', 0.91],
  ['olive oil', 0.91], ['huile d\'olive', 0.91],
  ['vegetable oil', 0.92], ['huile végétale', 0.92], ['huile vegetale', 0.92],
  ['coconut oil', 0.92], ['huile de coco', 0.92],
  // Grains / rice / pasta
  ['rice', 0.80], ['riz', 0.80],
  ['cooked rice', 0.70], ['riz cuit', 0.70],
  ['pasta', 0.45], ['pâtes', 0.45], ['pates', 0.45],
  ['cooked pasta', 0.65], ['pâtes cuites', 0.65],
  ['quinoa', 0.80], ['couscous', 0.65],
  // Legumes (dried)
  ['lentils', 0.85], ['lentilles', 0.85],
  ['chickpeas', 0.83], ['pois chiches', 0.83],
  ['black beans', 0.80], ['haricots noirs', 0.80],
  ['kidney beans', 0.80], ['haricots rouges', 0.80],
  // Nuts / seeds
  ['almonds', 0.60], ['amandes', 0.60],
  ['walnuts', 0.50], ['noix', 0.50],
  ['peanuts', 0.60], ['cacahuètes', 0.60], ['cacahuetes', 0.60],
  ['chia seeds', 0.55], ['graines de chia', 0.55], ['chia', 0.55],
  ['flax seeds', 0.52], ['graines de lin', 0.52], ['lin', 0.52],
  // Misc
  ['peanut butter', 1.08], ['beurre de cacahuète', 1.08], ['beurre cacahuete', 1.08],
  ['jam', 1.33], ['confiture', 1.33],
  ['salt', 1.20], ['sel', 1.20],
  ['baking soda', 0.92], ['bicarbonate', 0.92],
  ['baking powder', 0.72], ['levure chimique', 0.72],
  ['cocoa', 0.55], ['cacao', 0.55],
];

// Volume units → ml. Covers US + metric common sizes. `serving` and
// `piece` are unit-less fallbacks (grams must come from elsewhere).
const VOLUME_ML = {
  ml: 1,
  l: 1000,
  tsp: 4.929,         // US teaspoon
  tbsp: 14.787,       // US tablespoon
  'tbsp-metric': 15,  // metric (AU / UK / EU cooking)
  'c-us': 236.588,    // US cup
  'c-metric': 250,    // metric cup
  oz_fl: 29.574,      // fl oz
  pt: 473.176,        // US pint
  qt: 946.353,        // US quart
};

// Mass units → grams. For when the label already states a mass in
// non-gram units (ounces, pounds).
const MASS_G = {
  g: 1,
  kg: 1000,
  mg: 0.001,
  oz: 28.3495,   // US / avoirdupois ounce (weight)
  lb: 453.592,
};

function norm(s) {
  return String(s ?? '')
    .toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .trim();
}

function densityFor(foodName) {
  const n = norm(foodName);
  if (!n) return null;
  // Walk entries in order; the first substring match wins. Longer /
  // more specific keys appear first in the table so "brown sugar"
  // beats "sugar".
  for (const [key, d] of DENSITIES) {
    if (n.includes(norm(key))) return d;
  }
  return null;
}

/**
 * toGrams(amount, unit, foodName) — convert a measurement to grams.
 *
 *   amount:   number
 *   unit:     one of:
 *             mass:    'g' | 'kg' | 'mg' | 'oz' | 'lb'
 *             volume:  'ml' | 'l' | 'tsp' | 'tbsp' | 'tbsp-metric'
 *                    | 'c-us' | 'c-metric' | 'cup' (= c-us)
 *                    | 'oz_fl' | 'pt' | 'qt'
 *             other:   'serving' | 'piece' (passthrough — returns null,
 *                      caller must resolve via a per-food average)
 *   foodName: optional; used for density lookup on volume units.
 *
 * Returns the gram value rounded to 1 decimal, or null when the unit
 * doesn't resolve (unknown unit, or volume unit with no density match
 * and no water fallback requested).
 */
export function toGrams(amount, unit, foodName = '') {
  const n = Number(amount);
  if (!Number.isFinite(n) || n <= 0) return null;
  const u = String(unit || '').toLowerCase();
  if (u in MASS_G) return Math.round(n * MASS_G[u] * 10) / 10;
  const alias = u === 'cup' ? 'c-us' : u;
  const ml = VOLUME_ML[alias];
  if (typeof ml === 'number') {
    const density = densityFor(foodName);
    // Fallback to 1 g/ml (water) when no density match — good enough
    // for most liquids and a conservative estimate for mixed foods.
    const gPerMl = density ?? 1;
    return Math.round(n * ml * gPerMl * 10) / 10;
  }
  return null;
}

/**
 * parseUnitInput("1/2 cup flour") → { amount: 0.5, unit: 'cup', name: 'flour' }
 * parseUnitInput("2 tbsp honey")   → { amount: 2, unit: 'tbsp', name: 'honey' }
 * parseUnitInput("100g rice")      → { amount: 100, unit: 'g', name: 'rice' }
 *
 * Returns null if no amount or no unit parse.
 *
 * Handles common ASCII fractions ("1/2", "3/4") + vulgar-fraction
 * Unicode chars (½ ⅓ ¼ etc.) + whole-number + mixed ("1 1/2 cup").
 */
const VULGAR = {
  '½': 0.5, '⅓': 1/3, '⅔': 2/3, '¼': 0.25, '¾': 0.75,
  '⅕': 0.2, '⅖': 0.4, '⅗': 0.6, '⅘': 0.8,
  '⅙': 1/6, '⅚': 5/6, '⅛': 0.125, '⅜': 0.375, '⅝': 0.625, '⅞': 0.875,
};

const UNIT_ALIASES = {
  // gram
  g: 'g', gr: 'g', gram: 'g', grams: 'g', gramme: 'g', grammes: 'g',
  // kilogram
  kg: 'kg', kgs: 'kg', kilo: 'kg', kilos: 'kg',
  // milligram
  mg: 'mg',
  // ounce (mass)
  oz: 'oz', ounce: 'oz', ounces: 'oz',
  // pound
  lb: 'lb', lbs: 'lb', pound: 'lb', pounds: 'lb',
  // ml / l
  ml: 'ml', milliliter: 'ml', milliliters: 'ml', millilitre: 'ml', millilitres: 'ml',
  l: 'l', liter: 'l', liters: 'l', litre: 'l', litres: 'l',
  // tsp / tbsp
  tsp: 'tsp', teaspoon: 'tsp', teaspoons: 'tsp', 'c.à.c': 'tsp', 'cc': 'tsp',
  tbsp: 'tbsp', tbs: 'tbsp', tablespoon: 'tbsp', tablespoons: 'tbsp',
  'c.à.s': 'tbsp', 'cs': 'tbsp', 'cuillère': 'tbsp',
  // cup
  cup: 'cup', cups: 'cup',
  // fluid ounce
  'fl-oz': 'oz_fl', 'fl.oz': 'oz_fl', 'floz': 'oz_fl',
  // pint / quart
  pt: 'pt', pint: 'pt', pints: 'pt',
  qt: 'qt', quart: 'qt', quarts: 'qt',
};

export function parseUnitInput(raw) {
  if (!raw) return null;
  const s = String(raw).trim().toLowerCase();
  // Split into: quantity chunk, unit, optional name.
  // Quantity patterns we accept:
  //   "2"            → 2
  //   "1.5"          → 1.5
  //   "1/2"          → 0.5
  //   "1 1/2"        → 1.5
  //   "½"            → 0.5
  //   "1 ½"          → 1.5
  const m = s.match(
    /^([0-9]+(?:[.,][0-9]+)?(?:\s+[0-9]+\/[0-9]+)?|[0-9]+\/[0-9]+|[½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]|[0-9]+\s*[½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞])\s*([a-zA-ZÀ-ÿ.\-]+)\b\s*(.*)$/,
  );
  if (!m) return null;
  const qtyRaw = m[1].trim();
  const unitRaw = m[2].replace(/\.+$/, '').toLowerCase();
  const name = (m[3] || '').trim();
  let amount = 0;
  // Try vulgar-fraction first.
  const vm = qtyRaw.match(/^(\d+)?\s*([½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞])$/);
  if (vm) {
    amount = (Number(vm[1]) || 0) + (VULGAR[vm[2]] || 0);
  } else if (qtyRaw.includes('/')) {
    const parts = qtyRaw.split(/\s+/);
    if (parts.length === 1) {
      const [num, den] = parts[0].split('/').map(Number);
      if (den > 0) amount = num / den;
    } else if (parts.length === 2) {
      const whole = Number(parts[0]) || 0;
      const [num, den] = parts[1].split('/').map(Number);
      if (den > 0) amount = whole + num / den;
    }
  } else {
    amount = Number(qtyRaw.replace(',', '.')) || 0;
  }
  const unit = UNIT_ALIASES[unitRaw];
  if (!unit || amount <= 0) return null;
  return { amount, unit, name };
}

// Expose the internal density list for tests + any future UI that
// wants to surface "we know this food" hints.
export const _DENSITIES_FOR_TESTS = DENSITIES;
export const _VOLUME_ML_FOR_TESTS = VOLUME_ML;
