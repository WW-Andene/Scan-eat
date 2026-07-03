/**
 * Single source of truth for the app's nutrition field lists.
 *
 * Before this module, two nearly-identical arrays lived in
 * /data/consumption.js and /data/recipes.js and drifted at every
 * extension. Now both import from here, so adding a new field is
 * a one-line change.
 *
 * Three tiers:
 *   MACRO_FIELDS  — always rendered (kcal + core macros + salt + count).
 *   MICRO_FIELDS  — rendered conditionally (only when daily total > 0).
 *                   Covers minerals, vitamins, fat subdivisions,
 *                   cholesterol. Fiber lives here too so rollups pick
 *                   it up consistently.
 *   ALL_FIELDS    — MACRO_FIELDS + MICRO_FIELDS, for serializers
 *                   (CSV export, share formatters, edit forms).
 *
 * Unit per field is attached so renderers / formatters don't re-
 * invent the mapping.
 */

export const MACRO_FIELDS = [
  { key: 'kcal',       unit: 'kcal' },
  { key: 'carbs_g',    unit: 'g' },
  { key: 'protein_g',  unit: 'g' },
  { key: 'fat_g',      unit: 'g' },
  { key: 'sat_fat_g',  unit: 'g' },
  { key: 'sugars_g',   unit: 'g' },
  { key: 'salt_g',     unit: 'g' },
];

export const MICRO_FIELDS = [
  // Dietary fibre — lives here so every aggregator sums it once.
  { key: 'fiber_g',    unit: 'g',  labelKey: 'dashFiber' },
  // Minerals
  { key: 'iron_mg',    unit: 'mg', labelKey: 'dashIron' },
  { key: 'calcium_mg', unit: 'mg', labelKey: 'dashCalcium' },
  { key: 'magnesium_mg', unit: 'mg', labelKey: 'dashMagnesium' },
  { key: 'potassium_mg', unit: 'mg', labelKey: 'dashPotassium' },
  { key: 'zinc_mg',    unit: 'mg', labelKey: 'dashZinc' },
  { key: 'sodium_mg',  unit: 'mg', labelKey: 'dashSodium' },
  // Vitamins
  { key: 'vit_a_ug',   unit: 'µg', labelKey: 'dashVitA' },
  { key: 'vit_c_mg',   unit: 'mg', labelKey: 'dashVitC' },
  { key: 'vit_d_ug',   unit: 'µg', labelKey: 'dashVitD' },
  { key: 'vit_e_mg',   unit: 'mg', labelKey: 'dashVitE' },
  { key: 'vit_k_ug',   unit: 'µg', labelKey: 'dashVitK' },
  { key: 'b1_mg',      unit: 'mg', labelKey: 'dashB1' },
  { key: 'b2_mg',      unit: 'mg', labelKey: 'dashB2' },
  { key: 'b3_mg',      unit: 'mg', labelKey: 'dashB3' },
  { key: 'b6_mg',      unit: 'mg', labelKey: 'dashB6' },
  { key: 'b9_ug',      unit: 'µg', labelKey: 'dashB9' },
  { key: 'b12_ug',     unit: 'µg', labelKey: 'dashB12' },
  // Fat subdivisions
  { key: 'polyunsaturated_fat_g', unit: 'g', labelKey: 'dashPufa' },
  { key: 'monounsaturated_fat_g', unit: 'g', labelKey: 'dashMufa' },
  { key: 'omega_3_g',  unit: 'g',  labelKey: 'dashOmega3' },
  { key: 'omega_6_g',  unit: 'g',  labelKey: 'dashOmega6' },
  { key: 'cholesterol_mg', unit: 'mg', labelKey: 'dashCholesterol' },
];

export const ALL_FIELDS = [...MACRO_FIELDS, ...MICRO_FIELDS];

// Flat name list for legacy aggregators that just need key strings.
export const MICRO_KEYS = MICRO_FIELDS.map((f) => f.key);
export const ALL_KEYS = ALL_FIELDS.map((f) => f.key);
