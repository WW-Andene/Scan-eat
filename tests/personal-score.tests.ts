/**
 * Personal-score wiring tests. Covers the audit gaps raised during review:
 *   - every profile field affects the score (directly or via BMR/TDEE chain)
 *   - halal detects pork variants AND recognizes halal certification text
 *   - vegan detects carmine / gelatin / isinglass and accepts V-Label override
 *   - certification-override behaviour for halal / kosher / vegan / gluten-free
 *
 * The client modules are plain ESM, so we import them directly via the
 * --experimental-strip-types TS loader. We stub localStorage since the diet
 * + profile modules only touch it for storage (not during pure computations).
 */

import { strict as assert } from 'node:assert';
import { describe, it, before } from 'node:test';

// Minimal localStorage stub so the profile module doesn't throw when imported.
(globalThis as { localStorage?: Storage }).localStorage = {
  _d: {} as Record<string, string>,
  getItem(k: string) { return (this as unknown as { _d: Record<string, string> })._d[k] ?? null; },
  setItem(k: string, v: string) { (this as unknown as { _d: Record<string, string> })._d[k] = v; },
  removeItem(k: string) { delete (this as unknown as { _d: Record<string, string> })._d[k]; },
  clear() { (this as unknown as { _d: Record<string, string> })._d = {}; },
  key() { return null; },
  get length() { return 0; },
} as unknown as Storage;

const { checkDiet } = await import('../public/core/diets.js');
const { computePersonalScore } = await import('../public/core/personal-score.js');
const { bmrMifflinStJeor, tdeeKcal, bmi, dailyTargets, proteinPRI_g } = await import('../public/data/profile.js');

// ----- Authoritative formula sanity -----

describe('Profile physiological formulas', () => {
  it('Mifflin-St Jeor BMR matches the 1990 paper (male example 70 kg / 175 cm / 30 y)', () => {
    const p = { sex: 'male', age_years: 30, height_cm: 175, weight_kg: 70, activity: 'moderate' };
    // 10*70 + 6.25*175 - 5*30 + 5 = 700 + 1093.75 - 150 + 5 = 1648.75 → round 1649
    assert.equal(bmrMifflinStJeor(p), 1649);
  });

  it('Mifflin-St Jeor BMR for female 60 kg / 165 cm / 30 y', () => {
    const p = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    // 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25 → 1320
    assert.equal(bmrMifflinStJeor(p), 1320);
  });

  it('TDEE uses the FAO/WHO/UNU PAL ladder', () => {
    const p = { sex: 'male', age_years: 30, height_cm: 175, weight_kg: 70, activity: 'moderate' };
    assert.equal(tdeeKcal(p), Math.round(1649 * 1.75));
  });

  it('BMI matches kg / m²', () => {
    const p = { height_cm: 175, weight_kg: 70 };
    assert.equal(bmi(p), 22.9);
  });

  it('Protein PRI adapts per age (0.83 g/kg adult vs 1.0 g/kg ≥65y)', () => {
    assert.equal(proteinPRI_g({ weight_kg: 70, age_years: 30 }), Math.round(70 * 0.83));
    assert.equal(proteinPRI_g({ weight_kg: 70, age_years: 70 }), 70);
  });

  it('Daily targets scale with TDEE (WHO %E conversion)', () => {
    const p = { sex: 'male', age_years: 30, height_cm: 175, weight_kg: 70, activity: 'moderate' };
    const tdee = tdeeKcal(p)!;
    const tg = dailyTargets(p)!;
    assert.equal(tg.kcal, tdee);
    assert.equal(tg.sat_fat_g_max, Math.round((0.10 * tdee) / 9));
    assert.equal(tg.free_sugars_g_max, Math.round((0.10 * tdee) / 4));
    assert.equal(tg.free_sugars_g_ideal, Math.round((0.05 * tdee) / 4));
    assert.equal(tg.salt_g_max, 5);
    assert.equal(tg.life_stage, null);
  });

  it('life_stage=pregnancy adds +300 kcal, +15 g protein, iron=27 mg (EFSA)', () => {
    const base = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg0 = dailyTargets(base)!;
    const tg1 = dailyTargets({ ...base, life_stage: 'pregnancy' })!;
    assert.equal(tg1.kcal - tg0.kcal, 300);
    // Protein target must be at least PRI + 15 g.
    const pri = Math.round(60 * 0.83);
    assert.ok(tg1.protein_g_target >= pri + 15);
    assert.equal(tg1.iron_mg_target, 27);
    assert.equal(tg1.calcium_mg_target, 1000);
    assert.equal(tg1.vit_d_ug_target, 15);
    assert.equal(tg1.b12_ug_target, 4.5);
    assert.equal(tg1.life_stage, 'pregnancy');
  });

  it('life_stage=lactation adds +500 kcal, +19 g protein, iron=10 mg (EFSA)', () => {
    const base = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg0 = dailyTargets(base)!;
    const tg1 = dailyTargets({ ...base, life_stage: 'lactation' })!;
    assert.equal(tg1.kcal - tg0.kcal, 500);
    assert.equal(tg1.iron_mg_target, 10); // non-menstruating + lactation
    assert.equal(tg1.calcium_mg_target, 1000);
    assert.equal(tg1.b12_ug_target, 5);
    assert.equal(tg1.life_stage, 'lactation');
  });

  it('invalid life_stage value is ignored (no deltas applied)', () => {
    const base = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg0 = dailyTargets(base)!;
    const tg1 = dailyTargets({ ...base, life_stage: 'menopause' as 'pregnancy' })!;
    assert.equal(tg1.kcal, tg0.kcal);
    assert.equal(tg1.life_stage, null);
  });

  it('menstruating female (age 11–50) iron baseline = 16 mg (EFSA PRI)', () => {
    const p = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg = dailyTargets(p)!;
    assert.equal(tg.iron_mg_target, 16);
  });

  it('post-menopausal female (age ≥51) iron baseline = 11 mg (EFSA PRI)', () => {
    const p = { sex: 'female', age_years: 55, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg = dailyTargets(p)!;
    assert.equal(tg.iron_mg_target, 11);
  });

  it('male iron baseline = 11 mg (EFSA PRI)', () => {
    const p = { sex: 'male', age_years: 30, height_cm: 175, weight_kg: 70, activity: 'moderate' };
    const tg = dailyTargets(p)!;
    assert.equal(tg.iron_mg_target, 11);
  });

  it('lactation override wins over the menstruating baseline (10 mg, not 16)', () => {
    // Postpartum women are not menstruating; EFSA PRI drops to 10 mg.
    const p = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate', life_stage: 'lactation' };
    const tg = dailyTargets(p)!;
    assert.equal(tg.iron_mg_target, 10);
  });

  it('kcal targets: life-stage delta composes with TDEE, not replaces it', () => {
    const p = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const baseKcal = tdeeKcal(p);
    const tgPreg = dailyTargets({ ...p, life_stage: 'pregnancy' })!;
    const tgLact = dailyTargets({ ...p, life_stage: 'lactation' })!;
    // Exact additive relationship — no replacement.
    assert.equal(tgPreg.kcal, baseKcal + 300);
    assert.equal(tgLact.kcal, baseKcal + 500);
  });

  it('carbs / fat targets recompute from the stage-adjusted TDEE', () => {
    // If the stage delta weren't feeding macros, pregnancy carbs would be
    // identical to baseline — but AMDR-derived targets must scale with kcal.
    const base = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg0 = dailyTargets(base)!;
    const tg1 = dailyTargets({ ...base, life_stage: 'pregnancy' })!;
    assert.ok(tg1.carbs_g_target > tg0.carbs_g_target, 'carbs target must scale up in pregnancy');
    assert.ok(tg1.fat_g_target > tg0.fat_g_target, 'fat target must scale up in pregnancy');
  });

  it('life_stage is echoed back on dailyTargets so UI can surface it', () => {
    // The dashboard life-stage chip (R4.5) reads targets.life_stage to
    // decide whether to show the +300 / +500 kcal uplift chip. Pin that
    // the field flows through so the UI can't be fooled into hiding it.
    const base = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate' };
    const tg = dailyTargets({ ...base, life_stage: 'pregnancy' })!;
    assert.equal(tg.life_stage, 'pregnancy');
    const tg2 = dailyTargets({ ...base, life_stage: 'lactation' })!;
    assert.equal(tg2.life_stage, 'lactation');
    const tg3 = dailyTargets(base)!;
    assert.equal(tg3.life_stage, null);
  });
});

// ----- Halal rule coverage -----

describe('Halal diet', () => {
  it('detects classic pork variants', () => {
    for (const name of ['porc', 'jambon blanc', 'lardon fumé', 'saucisson sec', 'bacon', 'couenne de porc', 'saindoux', 'mortadelle', 'chorizo']) {
      const r = checkDiet({ name: 'x', ingredients: [{ name }] }, 'halal', null, 'fr');
      assert.equal(r.compliant, false, `"${name}" should violate halal`);
    }
  });

  it('detects alcohol variants (wine, spirits, liqueurs)', () => {
    for (const name of ['vin rouge', 'bière blonde', 'rhum', 'whisky', 'cognac', 'kirsch', 'liqueur de cassis']) {
      const r = checkDiet({ name: 'x', ingredients: [{ name }] }, 'halal', null, 'fr');
      assert.equal(r.compliant, false, `"${name}" should violate halal`);
    }
  });

  it('allows "gélatine halal" and "gélatine végétale"', () => {
    for (const name of ['gélatine halal', 'gélatine végétale', 'gélatine de poisson']) {
      const r = checkDiet({ name: 'x', ingredients: [{ name }] }, 'halal', null, 'fr');
      assert.equal(r.compliant, true, `"${name}" should NOT violate halal`);
    }
  });

  it('recognizes halal certification in product name and overrides forbidden hit', () => {
    // Product name carries certification AND ingredient looks like gelatine
    const r = checkDiet(
      { name: 'Bonbons certifiés halal AVS', ingredients: [{ name: 'gélatine' }] },
      'halal', null, 'fr',
    );
    assert.equal(r.certified, true);
    assert.equal(r.compliant, true, 'AVS-certified product overrides the gélatine hit');
  });

  it('AFCAI / HMC certifications are detected', () => {
    const r = checkDiet(
      { name: 'Saucisse de volaille certifiée AFCAI', ingredients: [{ name: 'viande de poulet' }] },
      'halal', null, 'fr',
    );
    assert.equal(r.certified, true);
  });
});

// ----- Vegan rule coverage + V-Label override -----

describe('Vegan diet', () => {
  it('detects carmine (E120), isinglass, bone phosphate (E542)', () => {
    for (const name of ['cochenille (E120)', 'colle de poisson', 'phosphate osseux', 'E542', 'E901', 'E904']) {
      const r = checkDiet({ name: 'x', ingredients: [{ name }] }, 'vegan', null, 'fr');
      assert.equal(r.compliant, false, `"${name}" should violate vegan`);
    }
  });

  it('allows "lait de coco" and "beurre de cacahuète" (not animal dairy)', () => {
    const r1 = checkDiet({ name: 'x', ingredients: [{ name: 'lait de coco' }] }, 'vegan', null, 'fr');
    assert.equal(r1.compliant, true, '"lait de coco" should NOT violate vegan');
    const r2 = checkDiet({ name: 'x', ingredients: [{ name: 'beurre de cacahuète' }] }, 'vegan', null, 'fr');
    assert.equal(r2.compliant, true, '"beurre de cacahuète" should NOT violate vegan');
  });

  it('V-Label on product name overrides a plausible false-positive', () => {
    const r = checkDiet(
      { name: 'Steak V-Label', ingredients: [{ name: 'protéines de blé' }] },
      'vegan', null, 'fr',
    );
    assert.equal(r.certified, true);
  });
});

// ----- Kosher certification detection -----

describe('Kosher diet', () => {
  it('detects OU/OK/Star-K marks as certified', () => {
    for (const productName of ['Cheesecake OU', 'Matzo OK Pareve', 'Granola Star-K']) {
      const r = checkDiet({ name: productName, ingredients: [{ name: 'farine' }] }, 'kosher', null, 'fr');
      assert.equal(r.certified, true, `"${productName}" should be recognized as kosher-certified`);
    }
  });

  it('blocks pork + shellfish', () => {
    const r1 = checkDiet({ name: 'x', ingredients: [{ name: 'porc' }] }, 'kosher', null, 'fr');
    assert.equal(r1.compliant, false);
    const r2 = checkDiet({ name: 'x', ingredients: [{ name: 'crevette rose' }] }, 'kosher', null, 'fr');
    assert.equal(r2.compliant, false);
  });
});

// ----- End-to-end: profile drives personal score -----

describe('Personal score uses every profile field', () => {
  const makeAudit = (score: number) => ({
    product_name: 'X',
    category: 'snack_sweet' as const,
    score, grade: 'B',
    verdict: '',
    pillars: {} as never,
    global_bonuses: [], global_penalties: [],
    veto: { triggered: false, reason: '', cap: 100 },
    red_flags: [], green_flags: [],
    engine_version: 'test', warnings: [],
  });
  const makeProduct = (extras: Partial<{ protein_g: number; saturated_fat_g: number; sugars_g: number; salt_g: number }>, ingredients: Array<{ name: string }> = [], name = 'Test') => ({
    name,
    category: 'snack_sweet',
    nova_class: 4,
    ingredients,
    nutrition: {
      energy_kcal: 300, fat_g: 10, saturated_fat_g: 5, carbs_g: 40,
      sugars_g: 10, fiber_g: 2, protein_g: 5, salt_g: 0.5,
      ...extras,
    },
  });

  it('diet violation is a HARD VETO: personal score caps at 0 regardless of classic', () => {
    const profile = {
      sex: null, age_years: null, height_cm: null, weight_kg: null, activity: null,
      diet: 'vegan', custom_diet: null,
    };
    const r = computePersonalScore(makeAudit(70), makeProduct({}, [{ name: 'lait entier' }]), profile, 'fr');
    assert.equal(r.veto, true, 'veto flag must be set');
    assert.equal(r.personal_score, 0, 'hard cap at 0 — a product you cannot eat is 0, not 41');
    // The violation still appears in adjustments list for transparency.
    assert.ok(r.adjustments.some((a) => a.veto === true && a.category === 'diet'));
  });

  it('Dairy-free + Skyr-like product: score is 0, not 41', () => {
    const profile = {
      sex: null, age_years: null, height_cm: null, weight_kg: null, activity: null,
      diet: 'dairy_free', custom_diet: null,
    };
    const skyrLike = {
      name: 'Skyr',
      category: 'yogurt',
      nova_class: 4,
      ingredients: [
        { name: 'yaourt maigre (lait)' },
        { name: 'poire 7%' },
      ],
      nutrition: {
        energy_kcal: 60, fat_g: 0.2, saturated_fat_g: 0.1, carbs_g: 8,
        sugars_g: 8, fiber_g: 0.3, protein_g: 10, salt_g: 0.12,
      },
    };
    const r = computePersonalScore(makeAudit(71), skyrLike, profile, 'fr');
    assert.equal(r.veto, true);
    assert.equal(r.personal_score, 0);
  });

  it('halal certification still gives +5 bonus when no violation', () => {
    const profile = { sex: null, age_years: null, height_cm: null, weight_kg: null, activity: null, diet: 'halal', custom_diet: null };
    const r = computePersonalScore(
      makeAudit(70),
      makeProduct({}, [{ name: 'gélatine' }], 'Bonbon certifié halal AVS'),
      profile, 'fr',
    );
    assert.equal(r.veto, false);
    assert.ok(r.adjustments.some((a) => a.points === +5 && a.category === 'diet'));
    assert.equal(r.personal_score, 75);
  });

  it('Modifier "lowSugar" applies a -3 soft penalty on high-sugar product', () => {
    const profile = {
      sex: null, age_years: null, height_cm: null, weight_kg: null, activity: null,
      diet: 'none', custom_diet: null,
      modifiers: { lowSugar: true, lowSalt: false, highProtein: false, organic: false },
    };
    const r = computePersonalScore(makeAudit(70), makeProduct({ sugars_g: 12 }), profile, 'fr');
    assert.ok(r.adjustments.some((a) => a.category === 'modifier' && a.points === -3));
    assert.equal(r.personal_score, 67);
  });

  it('Modifier "organic" penalizes non-organic, rewards organic', () => {
    const profile = {
      sex: null, age_years: null, height_cm: null, weight_kg: null, activity: null,
      diet: 'none', custom_diet: null,
      modifiers: { organic: true, lowSugar: false, lowSalt: false, highProtein: false },
    };
    const notOrganic = { ...makeProduct({}), organic: false };
    const organic = { ...makeProduct({}), organic: true };
    const r1 = computePersonalScore(makeAudit(70), notOrganic, profile, 'fr');
    assert.ok(r1.adjustments.some((a) => a.category === 'modifier' && a.points === -3));
    const r2 = computePersonalScore(makeAudit(70), organic, profile, 'fr');
    assert.ok(r2.adjustments.some((a) => a.category === 'modifier' && a.points === +2));
  });

  it('height + weight + activity + age feed the sat-fat budget check', () => {
    // TDEE≈2400 → sat-fat max≈27 g. Product with 20 g/100 g sat fat = 74 % of budget → -4 fires.
    const profile = { sex: 'male', age_years: 30, height_cm: 180, weight_kg: 75, activity: 'moderate', diet: 'none', custom_diet: null };
    const r = computePersonalScore(makeAudit(60), makeProduct({ saturated_fat_g: 20 }), profile, 'en');
    const hit = r.adjustments.find((a) => /sat-fat budget/i.test(a.reason));
    assert.ok(hit, `sat-fat daily-budget adjustment should fire; got: ${JSON.stringify(r.adjustments)}`);
    assert.equal(hit!.points, -4);
  });

  it('protein PRI target drives a +2 when 100g covers ≥20% of daily need', () => {
    const profile = { sex: 'female', age_years: 30, height_cm: 165, weight_kg: 60, activity: 'moderate', diet: 'none', custom_diet: null };
    // PRI = 60*0.83 ≈ 50 g. 12 g/100 g = 24 % → fires.
    const r = computePersonalScore(makeAudit(60), makeProduct({ protein_g: 12 }), profile, 'en');
    assert.ok(r.adjustments.some((a) => /daily protein target/i.test(a.reason) && a.points === +2),
      `protein-PRI adjustment missing; got: ${JSON.stringify(r.adjustments)}`);
  });

  it('light + moderate activity get a softer protein bonus (+1)', () => {
    const profile = { sex: 'male', age_years: 40, height_cm: 175, weight_kg: 70, activity: 'light', diet: 'none', custom_diet: null };
    const r = computePersonalScore(makeAudit(60), makeProduct({ protein_g: 18 }), profile, 'fr');
    const activity = r.adjustments.find((a) => a.category === 'activity' && a.points === +1);
    assert.ok(activity, 'light activity should get a +1 protein bonus');
  });
});
