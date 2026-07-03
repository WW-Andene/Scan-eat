/**
 * CSV import for MyFitnessPal and Cronometer exports.
 *
 * Detects the source format from the header row and maps each data row
 * into a ConsumptionEntry-shaped object that the existing
 * /data/consumption.js putEntry() can persist verbatim. No IDB or DOM
 * here — the parser is pure for tests; the UI is in app.js.
 *
 * Both apps export a "one row per logged item" CSV; what differs is
 * column naming, units, and the meal label vocabulary.
 *
 * MFP   — https://www.myfitnesspal.com/account/my_data
 *         Headers: Date, Meal, Item, Calories, Fat (g), Carbohydrates (g),
 *                  Protein (g), [Sodium (mg)], [Fiber], [Sugar], …
 *
 * Cron. — https://cronometer.com/help/exporting-data
 *         Headers: Day, Time, Group, Food Name, Amount,
 *                  Energy (kcal), Protein (g), Carbs (g), Fat (g),
 *                  Fiber (g), [Sodium (mg)], …
 *
 * The parser is permissive: extra columns are ignored, missing optional
 * columns default to 0, and dates / meals get normalised before being
 * emitted.
 */

const MEAL_MAP = {
  // FR + EN + Cronometer "Group" values
  breakfast: 'breakfast', petit_dejeuner: 'breakfast', 'petit déjeuner': 'breakfast', 'petit-déjeuner': 'breakfast',
  lunch: 'lunch', déjeuner: 'lunch', dejeuner: 'lunch',
  dinner: 'dinner', supper: 'dinner', diner: 'dinner', dîner: 'dinner',
  snack: 'snack', snacks: 'snack', goûter: 'snack', gouter: 'snack', collation: 'snack',
};

function normMeal(label) {
  const k = String(label ?? '').trim().toLowerCase();
  return MEAL_MAP[k] || 'snack';
}

function isoDate(input) {
  if (!input) return null;
  const s = String(input).trim();
  // YYYY-MM-DD already
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
  // N/N/YYYY — US MFP is M/D/YYYY by default. R34.I4: previously
  // the function stamped position-1 as month unconditionally, so
  // a French export like "31/01/2025" became "2025-31-01" — an
  // invalid date that downstream code silently dropped. Now: if
  // the first field is > 12 it must be D/M/YYYY; if the second
  // field is > 12 it must be M/D/YYYY; otherwise fall back to
  // US-style (the dominant MFP export).
  const m = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (m) {
    const p1 = Number(m[1]);
    const p2 = Number(m[2]);
    let mo; let da;
    if (p1 > 12 && p2 <= 12) { da = m[1]; mo = m[2]; }
    else if (p2 > 12 && p1 <= 12) { mo = m[1]; da = m[2]; }
    else { mo = m[1]; da = m[2]; } // ambiguous → US-style
    if (Number(mo) < 1 || Number(mo) > 12 || Number(da) < 1 || Number(da) > 31) return null;
    return `${m[3]}-${mo.padStart(2, '0')}-${da.padStart(2, '0')}`;
  }
  return null;
}

function num(v) {
  if (v == null) return 0;
  const n = parseFloat(String(v).replace(',', '.').replace(/[^\d.\-]/g, ''));
  return Number.isFinite(n) ? n : 0;
}

/**
 * RFC-4180-compliant-enough CSV row splitter. Handles quoted fields
 * containing commas, escaped quotes ("" → ").
 */
export function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let i = 0;
  let inQuote = false;
  while (i < line.length) {
    const c = line[i];
    if (inQuote) {
      if (c === '"' && line[i + 1] === '"') { cur += '"'; i += 2; continue; }
      if (c === '"') { inQuote = false; i += 1; continue; }
      cur += c; i += 1; continue;
    }
    if (c === '"') { inQuote = true; i += 1; continue; }
    if (c === ',') { out.push(cur); cur = ''; i += 1; continue; }
    cur += c; i += 1;
  }
  out.push(cur);
  return out;
}

/** Returns 'mfp' | 'cronometer' | 'unknown'. */
export function detectFormat(header) {
  const cols = header.map((c) => String(c ?? '').trim().toLowerCase());
  const has = (name) => cols.some((c) => c === name);
  // MFP signature: "Meal" + "Item" + "Calories"
  if (has('meal') && has('item') && (has('calories') || has('calories_kcal'))) return 'mfp';
  // Cronometer signature: "Day" + "Food Name" + "Energy (kcal)"
  if (has('day') && has('food name') && cols.some((c) => c.startsWith('energy'))) return 'cronometer';
  return 'unknown';
}

/** Find a column index by case-insensitive header match (first hit wins). */
function colIdx(header, ...candidates) {
  for (const cand of candidates) {
    const ix = header.findIndex((h) => String(h ?? '').trim().toLowerCase() === cand.toLowerCase());
    if (ix >= 0) return ix;
  }
  return -1;
}

function mapMfpRow(row, header, now) {
  const dateIx = colIdx(header, 'Date');
  const mealIx = colIdx(header, 'Meal');
  const itemIx = colIdx(header, 'Item', 'Food', 'Food Name');
  const kcalIx = colIdx(header, 'Calories', 'Calories (kcal)', 'Energy (kcal)');
  const carbIx = colIdx(header, 'Carbohydrates (g)', 'Carbohydrates', 'Carbs');
  const protIx = colIdx(header, 'Protein (g)', 'Protein');
  const fatIx  = colIdx(header, 'Fat (g)', 'Fat');
  const fibIx  = colIdx(header, 'Fiber (g)', 'Fiber');
  const sugIx  = colIdx(header, 'Sugar (g)', 'Sugar');
  const sodIx  = colIdx(header, 'Sodium (mg)', 'Sodium');
  const date = isoDate(row[dateIx]);
  const name = String(row[itemIx] ?? '').trim();
  const kcal = num(row[kcalIx]);
  if (!date || !name || kcal <= 0) return null;
  // MFP sodium is in mg; salt_g ≈ sodium_mg × 2.5 / 1000 (NaCl conversion)
  const sodiumMg = num(row[sodIx]);
  const saltG = sodiumMg > 0 ? Math.round(sodiumMg * 2.5) / 1000 : 0;
  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `i${now}${Math.random().toString(36).slice(2)}`),
    date,
    timestamp: new Date(`${date}T12:00:00`).getTime(),
    product_name: name,
    category: 'other',
    grams: 0,
    meal: normMeal(row[mealIx]),
    kcal: Math.round(kcal * 10) / 10,
    carbs_g:  Math.round(num(row[carbIx]) * 100) / 100,
    fat_g:    Math.round(num(row[fatIx])  * 100) / 100,
    sat_fat_g: 0,
    sugars_g: Math.round(num(row[sugIx])  * 100) / 100,
    salt_g:   saltG,
    protein_g: Math.round(num(row[protIx]) * 100) / 100,
    fiber_g:  Math.round(num(row[fibIx]) * 100) / 100,
    iron_mg: 0, calcium_mg: 0, vit_d_ug: 0, b12_ug: 0,
    quickAdd: true,
    imported_from: 'mfp',
  };
}

function mapCronometerRow(row, header, now) {
  const dateIx = colIdx(header, 'Day');
  const groupIx = colIdx(header, 'Group');
  const nameIx = colIdx(header, 'Food Name');
  const kcalIx = colIdx(header, 'Energy (kcal)');
  const carbIx = colIdx(header, 'Carbs (g)', 'Carbohydrate (g)');
  const protIx = colIdx(header, 'Protein (g)');
  const fatIx  = colIdx(header, 'Fat (g)');
  const fibIx  = colIdx(header, 'Fiber (g)');
  const sugIx  = colIdx(header, 'Sugars (g)', 'Sugar (g)');
  const sodIx  = colIdx(header, 'Sodium (mg)');
  const ironIx = colIdx(header, 'Iron, Fe (mg)', 'Iron (mg)');
  const calIx  = colIdx(header, 'Calcium (mg)');
  const vitDIx = colIdx(header, 'Vitamin D (IU)', 'Vitamin D (µg)');
  const b12Ix  = colIdx(header, 'B12, Cobalamin (µg)', 'B12 (µg)');

  const date = isoDate(row[dateIx]);
  const name = String(row[nameIx] ?? '').trim();
  const kcal = num(row[kcalIx]);
  if (!date || !name || kcal <= 0) return null;

  const sodiumMg = num(row[sodIx]);
  const saltG = sodiumMg > 0 ? Math.round(sodiumMg * 2.5) / 1000 : 0;

  // Vitamin D: convert IU → µg (1 µg = 40 IU) when the header says IU.
  const vitDIsIU = vitDIx >= 0 && /\(IU\)/i.test(String(header[vitDIx] ?? ''));
  const vitDValue = num(row[vitDIx]);
  const vitDUg = vitDIsIU ? Math.round((vitDValue / 40) * 100) / 100 : Math.round(vitDValue * 100) / 100;

  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `i${now}${Math.random().toString(36).slice(2)}`),
    date,
    timestamp: new Date(`${date}T12:00:00`).getTime(),
    product_name: name,
    category: 'other',
    grams: 0,
    meal: normMeal(row[groupIx]),
    kcal: Math.round(kcal * 10) / 10,
    carbs_g:  Math.round(num(row[carbIx]) * 100) / 100,
    fat_g:    Math.round(num(row[fatIx])  * 100) / 100,
    sat_fat_g: 0,
    sugars_g: Math.round(num(row[sugIx])  * 100) / 100,
    salt_g:   saltG,
    protein_g: Math.round(num(row[protIx]) * 100) / 100,
    fiber_g:  Math.round(num(row[fibIx]) * 100) / 100,
    iron_mg: Math.round(num(row[ironIx]) * 100) / 100,
    calcium_mg: Math.round(num(row[calIx]) * 10) / 10,
    vit_d_ug: vitDUg,
    b12_ug: Math.round(num(row[b12Ix]) * 100) / 100,
    quickAdd: true,
    imported_from: 'cronometer',
  };
}

/**
 * Parse a full CSV string. Returns { format, entries[], errors[] }.
 * Pure — exported for tests + the UI dry-run.
 *
 * If `format` is 'unknown' the file is rejected without parsing rows.
 */
export function parseCsvImport(csvText, opts = {}) {
  const now = opts.now ?? Date.now();
  const lines = String(csvText ?? '').split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length === 0) return { format: 'unknown', entries: [], errors: ['empty file'] };

  const header = parseCsvLine(lines[0]);
  const format = detectFormat(header);
  if (format === 'unknown') {
    return {
      format,
      entries: [],
      errors: ['unrecognised header — supported: MyFitnessPal exports + Cronometer Servings.csv'],
    };
  }

  const mapper = format === 'mfp' ? mapMfpRow : mapCronometerRow;
  const entries = [];
  const errors = [];
  for (let i = 1; i < lines.length; i += 1) {
    const row = parseCsvLine(lines[i]);
    const entry = mapper(row, header, now);
    if (entry) entries.push(entry);
    // F-F-05: per-row skip reason with 1-based row number so users can
    // open the CSV in their spreadsheet and find exactly which line.
    // Messaging stays English in this pure module (keeps it i18n-free
    // for tests); the UI renders them inside a translated <details>.
    else errors.push(`row ${i + 1} skipped (invalid date / name / kcal)`);
  }
  return { format, entries, errors };
}
