/**
 * Recipes — aggregator unit tests.
 * Pure function; no IDB involvement.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import { aggregateRecipe, buildRecipeProductInput } from '../public/data/recipes.js';

const recipe = {
  id: 'r1',
  name: 'Salade composée',
  servings: 1,
  components: [
    { product_name: 'tomate',     grams: 150, kcal: 30, carbs_g: 6, fat_g: 0.3, protein_g: 1.2, salt_g: 0.01, sat_fat_g: 0.05, sugars_g: 4 },
    { product_name: 'mozzarella', grams: 100, kcal: 280, carbs_g: 3, fat_g: 22, protein_g: 18, salt_g: 0.65, sat_fat_g: 15, sugars_g: 1 },
    { product_name: 'huile olive', grams: 15, kcal: 135, carbs_g: 0, fat_g: 15, protein_g: 0, salt_g: 0, sat_fat_g: 2.1, sugars_g: 0 },
  ],
};

describe('aggregateRecipe', () => {
  it('sums kcal + macros across components (1 serving)', () => {
    const out = aggregateRecipe(recipe, 1);
    assert.equal(out.kcal, 445);         // 30+280+135
    assert.equal(out.fat_g, 37.3);       // 0.3+22+15
    assert.equal(out.protein_g, 19.2);   // 1.2+18+0
    assert.equal(out.carbs_g, 9);        // 6+3+0
    assert.equal(out.grams, 265);        // 150+100+15
  });

  it('divides totals when servings > 1', () => {
    const out = aggregateRecipe(recipe, 2);
    assert.equal(out.kcal, 222.5);       // 445 / 2
    assert.equal(out.grams, 133);        // round(265/2)
  });

  it('clamps servings to a minimum of 0.1 (no div-by-zero)', () => {
    const out = aggregateRecipe(recipe, 0);
    // servings=0 gets clamped to 0.1 → totals *10
    assert.equal(Number.isFinite(out.kcal), true);
    assert.ok(out.kcal > 0);
  });

  it('handles a recipe with no components', () => {
    const out = aggregateRecipe({ id: 'r2', name: 'Vide', components: [] }, 1);
    assert.equal(out.kcal, 0);
    assert.equal(out.grams, 0);
    assert.equal(out.product_name, 'Vide');
  });

  it('tags the output with fromRecipe for dashboard attribution', () => {
    const out = aggregateRecipe(recipe, 1);
    assert.equal(out.fromRecipe, 'r1');
  });

  it('returns empty product_name when recipe has none (R14.1)', () => {
    // Data layer now stays locale-neutral; the UI substitutes the
    // localised "Untitled" / "Sans nom" via t() at render time.
    const out = aggregateRecipe({ components: [] }, 1);
    assert.equal(out.product_name, '');
  });

  it('ignores non-numeric component values', () => {
    const weird = {
      id: 'x',
      name: 'Weird',
      components: [
        { product_name: 'a', grams: '50', kcal: null, carbs_g: 'abc', fat_g: undefined, protein_g: 3 },
      ],
    };
    const out = aggregateRecipe(weird, 1);
    // only protein and grams parse; everything else → 0
    assert.equal(out.kcal, 0);
    assert.equal(out.carbs_g, 0);
    assert.equal(out.protein_g, 3);
    assert.equal(out.grams, 50);
  });

  // R9.2: the duplicate-recipe chip persists a clone via saveRecipe;
  // we can't exercise the IDB round-trip from node:test without a shim,
  // but the aggregator must stay stable across name changes so the
  // duplicate's totals are bit-for-bit identical to the source.
  it('aggregates identically regardless of recipe name (duplicate contract)', () => {
    const original = aggregateRecipe(recipe, 1);
    const clone = aggregateRecipe({ ...recipe, id: 'r1-copy', name: 'Salade (copie)' }, 1);
    // Name and fromRecipe differ by design; the macro totals must match.
    assert.equal(clone.kcal, original.kcal);
    assert.equal(clone.fat_g, original.fat_g);
    assert.equal(clone.protein_g, original.protein_g);
    assert.equal(clone.carbs_g, original.carbs_g);
    assert.equal(clone.grams, original.grams);
  });
});

// ============================================================================
// Gap fix 1 — buildRecipeProductInput for recipe scoring
// ============================================================================

describe('buildRecipeProductInput', () => {
  it('synthesises a ProductInput with per-100g nutrition', () => {
    const r = {
      id: 'r1', name: 'Pasta', servings: 1,
      components: [
        // 200 g pasta at 700 kcal total + 150 g sauce at 200 kcal total
        // = 900 kcal / 350 g × 100 = 257 kcal per 100 g
        { product_name: 'pasta', grams: 200, kcal: 700, protein_g: 25, carbs_g: 140, fat_g: 3 },
        { product_name: 'sauce', grams: 150, kcal: 200, protein_g: 4, carbs_g: 20, fat_g: 10 },
      ],
    };
    const input = buildRecipeProductInput(r);
    assert.equal(input.name, 'Pasta');
    assert.equal(input.weight_g, 350);
    assert.ok(Math.abs(input.nutrition.energy_kcal - 257.14) < 0.5, `got ${input.nutrition.energy_kcal}`);
    assert.ok(Math.abs(input.nutrition.protein_g - 8.29) < 0.5, `got ${input.nutrition.protein_g}`);
  });

  it('ingredients list carries percentages based on grams', () => {
    const r = {
      id: 'r1', name: 'Mix', servings: 1,
      components: [
        { product_name: 'a', grams: 100, kcal: 100 },
        { product_name: 'b', grams: 300, kcal: 100 },
      ],
    };
    const input = buildRecipeProductInput(r);
    assert.equal(input.ingredients.length, 2);
    assert.equal(input.ingredients[0].name, 'a');
    assert.equal(input.ingredients[0].percentage, 25);
    assert.equal(input.ingredients[1].percentage, 75);
    // #18: no blanket is_whole_food — scoring-engine's keyword
    // matching decides per-name.
    assert.equal(input.ingredients[0].is_whole_food, undefined);
  });

  it('falls back to 100g basis when no grams are declared', () => {
    const r = {
      id: 'r1', name: 'NoGrams', servings: 1,
      components: [
        { product_name: 'x', grams: 0, kcal: 50 },
        { product_name: 'y', grams: 0, kcal: 50 },
      ],
    };
    const input = buildRecipeProductInput(r);
    // basis = 100; per-100g = 100 total / 100 × 100 = 100
    assert.equal(input.nutrition.energy_kcal, 100);
    // percentages are null when grams are missing
    assert.equal(input.ingredients[0].percentage, null);
  });

  it('handles empty recipe', () => {
    const input = buildRecipeProductInput({ id: 'x', name: 'Empty', components: [] });
    assert.equal(input.ingredients.length, 0);
    assert.equal(input.nutrition.energy_kcal, 0);
  });
});
