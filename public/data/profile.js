/**
 * User profile storage + authoritative physiological calculations.
 *
 * AUTHORITATIVE sources:
 *   - BMR (Mifflin-St Jeor): Mifflin MD, St Jeor ST, Hill LA, Scott BJ,
 *     Daugherty SA, Koh YO. "A new predictive equation for resting energy
 *     expenditure in healthy individuals." Am J Clin Nutr 1990;51:241-247.
 *   - Activity factors (PAL): FAO/WHO/UNU "Human Energy Requirements",
 *     Report of a Joint FAO/WHO/UNU Expert Consultation (Rome, 2001;
 *     published 2004).
 *   - BMI cutoffs: WHO Global Database on BMI (2000; reaffirmed 2023).
 *   - Protein Population Reference Intake: EFSA Scientific Opinion on
 *     Dietary Reference Values for protein, EFSA Journal 2012;10(2):2557.
 */

const LS_PROFILE = 'scanneat.profile';

/**
 * Macro split presets.
 *
 * All percentages are of total daily energy (%E). Defensible reference points:
 *   - balanced: EFSA AMDR midpoints (Journal 2010;8(3):1461, carbs 45-65 %E,
 *     fat 20-35 %E; 50/20/30 is roughly centred).
 *   - mediterranean: higher fat share reflecting traditional olive-oil-heavy
 *     patterns (Trichopoulou et al., NEJM 2003).
 *   - high_protein: 30-35 %E protein for strength / satiety contexts (IOC
 *     Consensus, Br J Sports Med 2018).
 *   - low_carb: 25 %E carbs is roughly the threshold for "low-carb" in the
 *     literature (Hite AH et al., Nutrition 2011).
 *   - keto: 5-10 %E carbs, 70-80 %E fat is clinical ketosis (Volek & Phinney,
 *     The Art and Science of Low Carbohydrate Living).
 * `custom` stores arbitrary user-entered percentages that must sum to 100.
 */
export const MACRO_PRESETS = {
  balanced:      { carbs: 50, protein: 20, fat: 30, label_fr: 'Équilibré (OMS/EFSA)',   label_en: 'Balanced (WHO/EFSA)' },
  mediterranean: { carbs: 45, protein: 15, fat: 40, label_fr: 'Méditerranéen',          label_en: 'Mediterranean' },
  high_protein:  { carbs: 30, protein: 35, fat: 35, label_fr: 'Riche en protéines',     label_en: 'High-protein' },
  low_carb:      { carbs: 25, protein: 30, fat: 45, label_fr: 'Faible en glucides',     label_en: 'Low-carb' },
  keto:          { carbs:  5, protein: 20, fat: 75, label_fr: 'Cétogène',               label_en: 'Ketogenic' },
  custom:        {                                   label_fr: 'Personnalisé',          label_en: 'Custom' },
};

export function resolveMacroSplit(p) {
  const key = p?.macro_split || 'balanced';
  if (key === 'custom' && p?.macro_split_custom) {
    const c = p.macro_split_custom;
    // sanity: must sum to 100 ±3, otherwise fall back to balanced
    const sum = (c.carbs || 0) + (c.protein || 0) + (c.fat || 0);
    if (Math.abs(sum - 100) <= 3 && c.carbs >= 0 && c.protein >= 0 && c.fat >= 0) {
      return c;
    }
    return MACRO_PRESETS.balanced;
  }
  return MACRO_PRESETS[key] || MACRO_PRESETS.balanced;
}

export const DEFAULT_MODIFIERS = {
  lowSugar: false,
  lowSalt: false,
  highProtein: false,
  organic: false,
};

export const DEFAULT_PROFILE = {
  sex: null,            // 'male' | 'female' | 'other'
  age_years: null,
  height_cm: null,
  weight_kg: null,
  goal_weight_kg: null, // optional goal for the weight tracker
  water_goal_ml: null,  // optional user override; see waterGoalMl()
  activity: null,       // sedentary | light | moderate | active | very_active
  diet: 'none',         // HARD constraint — violation caps personal score at 0
  custom_diet: null,    // { forbidden: string[], preferred: string[] } when diet==='custom'
  modifiers: { ...DEFAULT_MODIFIERS }, // SOFT preferences — fine-tune within compatible products
  macro_split: 'balanced',                      // key into MACRO_PRESETS
  macro_split_custom: { carbs: 50, protein: 20, fat: 30 }, // used when macro_split==='custom'
};

const LEGACY_PREFS_KEY = 'scanneat.prefs';

export function getProfile() {
  try {
    const raw = localStorage.getItem(LS_PROFILE);
    let p = raw ? { ...DEFAULT_PROFILE, ...JSON.parse(raw) } : { ...DEFAULT_PROFILE };

    // One-time migration: the legacy LS_PREFS key held the dietary preference
    // checkboxes from the Settings dialog. Consolidate them into the profile.
    if (!p.modifiers || typeof p.modifiers !== 'object') {
      p.modifiers = { ...DEFAULT_MODIFIERS };
    } else {
      p.modifiers = { ...DEFAULT_MODIFIERS, ...p.modifiers };
    }
    const legacy = localStorage.getItem(LEGACY_PREFS_KEY);
    if (legacy) {
      try {
        const old = JSON.parse(legacy);
        if (old && typeof old === 'object') {
          p.modifiers = {
            lowSugar: p.modifiers.lowSugar || !!old.lowSugar,
            lowSalt: p.modifiers.lowSalt || !!old.lowSalt,
            highProtein: p.modifiers.highProtein || !!old.highProtein,
            organic: p.modifiers.organic || !!old.organic,
          };
          // Map legacy "vegetarian" checkbox into the diet dropdown if unset.
          if (old.vegetarian && (!p.diet || p.diet === 'none')) {
            p.diet = 'vegetarian';
          }
          setProfile(p);
          localStorage.removeItem(LEGACY_PREFS_KEY);
        }
      } catch { /* ignore corrupt legacy blob */ }
    }
    return p;
  } catch { return { ...DEFAULT_PROFILE }; }
}

export function setProfile(p) {
  localStorage.setItem(LS_PROFILE, JSON.stringify(p));
}

export function hasMinimalProfile(p) {
  // Enough to compute BMR + TDEE + apply diet rules.
  return (
    p.sex != null &&
    typeof p.age_years === 'number' && p.age_years > 0 &&
    typeof p.height_cm === 'number' && p.height_cm > 0 &&
    typeof p.weight_kg === 'number' && p.weight_kg > 0 &&
    p.activity != null
  );
}

/** Mifflin-St Jeor BMR in kcal/day. */
export function bmrMifflinStJeor(p) {
  if (!hasMinimalProfile(p)) return null;
  const sexOffset = p.sex === 'female' ? -161 : 5; // "other" treated as male per study population
  return Math.round(10 * p.weight_kg + 6.25 * p.height_cm - 5 * p.age_years + sexOffset);
}

/** Physical Activity Level multipliers — FAO/WHO/UNU 2004 Table 5.1. */
export const ACTIVITY_PAL = {
  sedentary:   1.40, // bedrest or very light activity (FAO/WHO/UNU lower bound 1.40)
  light:       1.55, // light lifestyle
  moderate:    1.75, // moderately active lifestyle
  active:      1.90, // vigorous / vigorously active lifestyle
  very_active: 2.20, // heavy occupational + training (upper end)
};

export function tdeeKcal(p) {
  const bmr = bmrMifflinStJeor(p);
  const factor = ACTIVITY_PAL[p.activity];
  if (bmr == null || factor == null) return null;
  return Math.round(bmr * factor);
}

/** BMI = kg / m². WHO cutoffs. */
export function bmi(p) {
  if (!p.height_cm || !p.weight_kg) return null;
  const m = p.height_cm / 100;
  return +(p.weight_kg / (m * m)).toFixed(1);
}

export function bmiCategory(value) {
  if (value == null) return null;
  if (value < 18.5) return 'underweight';
  if (value < 25) return 'normal';
  if (value < 30) return 'overweight';
  if (value < 35) return 'obese_1';
  if (value < 40) return 'obese_2';
  return 'obese_3';
}

/**
 * Protein Population Reference Intake (PRI).
 * EFSA 2012: 0.83 g/kg bw/day for adults. ≥65y: 1.0 g/kg/day (sarcopenia prevention).
 * Pregnancy / lactation deltas applied via life_stage in dailyTargets().
 */
export function proteinPRI_g(p) {
  if (!p.weight_kg || !p.age_years) return null;
  const per_kg = p.age_years >= 65 ? 1.0 : 0.83;
  return Math.round(p.weight_kg * per_kg);
}

/**
 * Life-stage adjustments applied on top of the baseline adult targets.
 * EFSA DRV Summary 2017 (EFSA Journal; Scientific Opinion on DRVs).
 *   Pregnancy: kcal +300 (avg across trimesters; EFSA 2013),
 *              protein +15 g/day (EFSA 2012 PRI delta),
 *              iron 27 mg/day (IOM; EFSA 16 mg + supplementation alternative),
 *              calcium 1000 mg/day (EFSA AI 2015),
 *              vit D 15 µg/day (unchanged), B12 4.5 µg/day.
 *   Lactation: kcal +500 (EFSA 2013), protein +19 g (EFSA 2012 first 6 mo),
 *              iron 10 mg (non-menstruating), calcium 1000 mg,
 *              vit D 15 µg, B12 5 µg.
 *
 * Returned deltas are APPLIED, not replacements — caller composes them on
 * top of the baseline.
 */
export function lifeStageAdjust(stage) {
  if (stage === 'pregnancy') {
    return { kcal: 300, protein_g: 15, iron_mg: 27, calcium_mg: 1000, vit_d_ug: 15, b12_ug: 4.5 };
  }
  if (stage === 'lactation') {
    return { kcal: 500, protein_g: 19, iron_mg: 10, calcium_mg: 1000, vit_d_ug: 15, b12_ug: 5 };
  }
  return null;
}

/**
 * Daily targets derived from TDEE + WHO/EFSA macronutrient distribution
 * ranges. Fat/carb targets use midpoints of the EFSA Acceptable Macro-
 * nutrient Distribution Ranges (AMDR, EFSA Journal 2010;8(3):1461) because
 * no single "target" is authoritative — 45-65 %E carbs / 20-35 %E fat.
 *
 * Sat fat: <10 %E → grams = 0.10 × kcal / 9 (WHO SFA Guideline 2023).
 * Free sugars: <10 %E ideal <5 %E (WHO Sugars Guideline 2015).
 * Salt: <5 g/day flat (WHO Salt Guideline 2012).
 * Protein: EFSA PRI 0.83 g/kg/d (1.0 g/kg/d ≥65y).
 * Fiber: EFSA DRV 25 g/day adults (adequate intake).
 * Iron: EFSA PRI 11 mg/d men, 16 mg/d menstruating women.
 * Calcium: EFSA PRI 950 mg/d adults (1150 mg 18–24 y).
 * Vitamin D: EFSA AI 15 µg/d (600 IU) all adults.
 * Vitamin B12: EFSA AI 4 µg/d.
 */
export function dailyTargets(p) {
  const baseTdee = tdeeKcal(p);
  if (baseTdee == null) return null;
  const stage = p?.life_stage === 'pregnancy' || p?.life_stage === 'lactation'
    ? p.life_stage : null;
  const delta = lifeStageAdjust(stage) || { kcal: 0, protein_g: 0 };
  const tdee = baseTdee + (delta.kcal || 0);
  const split = resolveMacroSplit(p);
  // protein target = max(EFSA PRI + stage delta, macro-split percentage) —
  // prevents sub-PRI targets when the user picks a low-protein macro split.
  const pri = (proteinPRI_g(p) ?? 0) + (delta.protein_g || 0);
  const pctProtein = Math.round(((split.protein / 100) * tdee) / 4);
  const isMenstruating = p?.sex === 'female' && (p?.age_years ?? 0) >= 11 && (p?.age_years ?? 0) < 51;
  // Micronutrient iron baseline; stage override takes precedence.
  const iron_baseline = isMenstruating ? 16 : 11;
  return {
    kcal: tdee,
    carbs_g_target: Math.round(((split.carbs / 100) * tdee) / 4),
    protein_g_target: Math.max(pri, pctProtein),
    fat_g_target: Math.round(((split.fat / 100) * tdee) / 9),
    sat_fat_g_max: Math.round((0.10 * tdee) / 9),          // WHO 2023 fixed
    free_sugars_g_max: Math.round((0.10 * tdee) / 4),      // WHO 2015
    free_sugars_g_ideal: Math.round((0.05 * tdee) / 4),
    salt_g_max: 5,                                          // WHO 2012 fixed
    fiber_g_target: 25,                                     // EFSA DRV 2010
    iron_mg_target: delta.iron_mg ?? iron_baseline,         // EFSA PRI 2015 + stage
    calcium_mg_target: delta.calcium_mg ?? 950,             // EFSA PRI 2015 + stage
    vit_d_ug_target: delta.vit_d_ug ?? 15,                  // EFSA AI 2016
    b12_ug_target: delta.b12_ug ?? 4,                       // EFSA AI 2015
    // Fix #6 — full EFSA PRI/AI micronutrient panel. Values are
    // adult PRIs (pregnancy/lactation adjustments handled by the
    // life-stage delta system if/when EFSA tables are added there).
    // Sources: EFSA DRV Summary Report 2017, NNR 2023 where EFSA is
    // silent. Stage-dependent fields fall through to `delta` first.
    magnesium_mg_target: delta.magnesium_mg ?? (p?.sex === 'female' ? 300 : 350),
    potassium_mg_target: delta.potassium_mg ?? 3500,
    zinc_mg_target:      delta.zinc_mg ?? (p?.sex === 'female' ? 8 : 11),
    sodium_mg_max:       delta.sodium_mg_max ?? 2000,       // WHO recommendation
    vit_a_ug_target:     delta.vit_a_ug ?? (p?.sex === 'female' ? 650 : 750),
    vit_c_mg_target:     delta.vit_c_mg ?? (p?.sex === 'female' ? 95 : 110),
    vit_e_mg_target:     delta.vit_e_mg ?? (p?.sex === 'female' ? 11 : 13),
    vit_k_ug_target:     delta.vit_k_ug ?? 70,
    b1_mg_target:        delta.b1_mg ?? 0.1 * (tdee / 1000), // EFSA AI 0.1 mg/MJ
    b2_mg_target:        delta.b2_mg ?? 1.6,                // EFSA PRI
    b3_mg_target:        delta.b3_mg ?? (p?.sex === 'female' ? 14 : 16),
    b6_mg_target:        delta.b6_mg ?? 1.7,
    b9_ug_target:        delta.b9_ug ?? 330,                // folate equivalents
    // Fat subdivisions have AI references but no PRI for most.
    pufa_g_target:       delta.pufa_g ?? Math.round((0.04 * tdee) / 9),
    mufa_g_target:       delta.mufa_g ?? Math.round((0.15 * tdee) / 9),
    omega_3_g_target:    delta.omega_3_g ?? 2.5,            // EFSA 250 mg EPA+DHA + 2 g ALA
    cholesterol_mg_max:  delta.cholesterol_mg_max ?? 300,   // DGA 2020
    macro_split_key: p?.macro_split || 'balanced',
    life_stage: stage,
  };
}
