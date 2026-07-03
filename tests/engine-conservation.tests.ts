/**
 * Property tests for scoreProduct(): pillar-sum + bound conservation.
 *
 * Why: pillar weights drift silently. Without a conservation test, a
 * future refactor that changed `scoreNutritionalDensity`'s 0–25 range
 * to 0–30 would only fail when a fixture happened to exercise the new
 * ceiling — typically months later, after a real product had moved.
 *
 * What we pin:
 *   1. Each pillar's reported `score` is in [0, max].
 *   2. Sum of pillar scores ≤ 100.
 *   3. Final audit.score is in [0, 100] for every input.
 *   4. A "synthetic-floor" product (all zeros, no name, NOVA 4) lands
 *      below grade C.
 *   5. A "synthetic-ceiling" product (whole-food list, ideal macros,
 *      organic, omega-3, named oils, NOVA 1) lands grade A or A+.
 *   6. ENGINE_VERSION is propagated into the audit.
 *   7. Veto cap is honored: trans fat > 0.1 → score ≤ 40 regardless of
 *      what the rest of the math says.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { ENGINE_VERSION, scoreProduct } from '../src/scoring-engine.ts';
import type { ProductInput, NutritionPer100g } from '../src/scoring-engine.ts';

// ---------------------------------------------------------------------

const PILLAR_MAXES = {
  processing: 20,
  nutritional_density: 25,
  negative_nutrients: 25,
  additive_risk: 15,
  ingredient_integrity: 15,
} as const;

function emptyNutrition(): NutritionPer100g {
  return {
    energy_kcal: 0, fat_g: 0, saturated_fat_g: 0, carbs_g: 0,
    sugars_g: 0, fiber_g: 0, protein_g: 0, salt_g: 0,
  };
}

function floorProduct(): ProductInput {
  // Worst plausible product: NOVA 4, no whole foods, no transparency,
  // generic vegetable oil, hidden sugar variants, palm oil, health claims.
  // The scoring math should put this firmly below grade C.
  return {
    name: 'Generic Snack Cake',
    category: 'snack_sweet',
    nova_class: 4,
    ingredients: [
      { name: 'sucre', is_whole_food: false },
      { name: 'huile végétale' },
      { name: 'farine raffinée' },
      { name: 'sirop de glucose' },
      { name: 'huile de palme' },
      { name: 'arômes' },
      { name: 'tartrazine', e_number: 'E102', category: 'additive' },
      { name: 'aspartame', e_number: 'E951', category: 'additive' },
    ],
    nutrition: {
      energy_kcal: 500, fat_g: 25, saturated_fat_g: 14,
      carbs_g: 60, sugars_g: 35, added_sugars_g: 30,
      fiber_g: 1, protein_g: 4, salt_g: 1.6, trans_fat_g: 0,
    },
    organic: false,
    has_health_claims: true,
    has_misleading_marketing: true,
    named_oils: false,
    origin_transparent: false,
  };
}

function ceilingProduct(): ProductInput {
  // Best plausible product: whole-food list, NOVA 1, ideal macros,
  // organic, omega-3 source, transparent origin, named oils.
  return {
    name: 'Plain Whole Lentils',
    category: 'fresh_meat', // category-irrelevant for our purposes; using
                            // a high-protein-expecting category so the
                            // protein pillar can reach max.
    nova_class: 1,
    ingredients: [
      { name: 'lentilles', is_whole_food: true },
      { name: 'graine de lin', is_whole_food: true }, // omega-3 trigger
    ],
    nutrition: {
      energy_kcal: 200, fat_g: 8, saturated_fat_g: 1,
      carbs_g: 22, sugars_g: 1, added_sugars_g: 0,
      fiber_g: 12, protein_g: 25, salt_g: 0.1, trans_fat_g: 0,
      // declared micronutrients hitting NRV-15
      iron_mg: 5, magnesium_mg: 80, potassium_mg: 600,
      b9_ug: 180,
    },
    organic: true,
    has_health_claims: false,
    has_misleading_marketing: false,
    named_oils: true,
    origin_transparent: true,
    fermented: false,
    declared_micronutrients: ['iron', 'magnesium', 'potassium', 'b9'],
  };
}

// Some category × nova × nutrition combinations to exercise corners.
// Not exhaustive — the goal is to fan out enough that any pillar-sum
// drift would be caught.
function fanOutProducts(): ProductInput[] {
  const cats: ProductInput['category'][] = [
    'sandwich', 'ready_meal', 'bread', 'breakfast_cereal', 'yogurt',
    'cheese', 'processed_meat', 'fresh_meat', 'fish',
    'snack_sweet', 'snack_salty', 'beverage_soft', 'beverage_juice',
    'beverage_water', 'condiment', 'oil_fat', 'other',
  ];
  return cats.flatMap<ProductInput>((category) => ([
    {
      name: `${category} (empty)`,
      category, nova_class: 4, ingredients: [],
      nutrition: emptyNutrition(),
    },
    {
      name: `${category} (medium)`,
      category, nova_class: 3,
      ingredients: [
        { name: 'eau' }, { name: 'sucre' }, { name: 'sel' },
      ],
      nutrition: {
        energy_kcal: 250, fat_g: 5, saturated_fat_g: 2,
        carbs_g: 35, sugars_g: 8, fiber_g: 3, protein_g: 6, salt_g: 0.8,
      },
    },
  ]));
}

// ---------------------------------------------------------------------

describe('engine: pillar conservation', () => {
  for (const product of [floorProduct(), ceilingProduct(), ...fanOutProducts()]) {
    it(`${product.name}: every pillar in [0, max]`, () => {
      const audit = scoreProduct(product);
      for (const [name, max] of Object.entries(PILLAR_MAXES)) {
        const pillar = (audit.pillars as unknown as Record<string, { score: number }>)[name];
        assert.ok(
          pillar.score >= 0 && pillar.score <= max,
          `${name} score ${pillar.score} out of [0, ${max}] for ${product.name}`,
        );
      }
    });

    it(`${product.name}: total pillar sum ≤ 100`, () => {
      const audit = scoreProduct(product);
      const sum = Object.values(audit.pillars).reduce((s, p) => s + p.score, 0);
      assert.ok(sum <= 100, `pillar sum ${sum} exceeds 100`);
    });

    it(`${product.name}: final audit.score in [0, 100]`, () => {
      const audit = scoreProduct(product);
      assert.ok(
        audit.score >= 0 && audit.score <= 100,
        `final score ${audit.score} out of [0, 100]`,
      );
    });

    it(`${product.name}: audit carries ENGINE_VERSION`, () => {
      const audit = scoreProduct(product);
      assert.equal(audit.engine_version, ENGINE_VERSION);
    });
  }
});

describe('engine: extremes', () => {
  it('synthetic floor product lands at grade D or worse', () => {
    const audit = scoreProduct(floorProduct());
    assert.ok(
      ['D', 'F'].includes(audit.grade),
      `expected D/F floor grade, got ${audit.grade} (score ${audit.score})`,
    );
  });

  it('synthetic ceiling product lands at grade A or A+', () => {
    const audit = scoreProduct(ceilingProduct());
    assert.ok(
      ['A', 'A+'].includes(audit.grade),
      `expected A/A+ ceiling grade, got ${audit.grade} (score ${audit.score})`,
    );
  });

  it('trans-fat veto caps the score at 40 regardless of pillar sums', () => {
    const product = ceilingProduct();
    product.nutrition.trans_fat_g = 0.5;
    const audit = scoreProduct(product);
    assert.ok(
      audit.score <= 40,
      `trans-fat veto failed: score=${audit.score} for an otherwise-A product`,
    );
    assert.equal(audit.veto.triggered, true);
  });

  it('all-zero nutrition + empty ingredients does not crash and stays in bounds', () => {
    // Edge case the engine has to survive even from a malformed payload.
    const audit = scoreProduct({
      name: '',
      category: 'other',
      nova_class: 4,
      ingredients: [],
      nutrition: emptyNutrition(),
    });
    assert.ok(audit.score >= 0 && audit.score <= 100);
    assert.ok(audit.grade);
  });
});
