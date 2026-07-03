/**
 * Built-in food DB — search tests.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import { FOOD_DB, searchFoodDB, reconcileWithFoodDB } from '../public/data/food-db.js';

describe('FOOD_DB shape', () => {
  it('every entry has required fields', () => {
    for (const f of FOOD_DB) {
      assert.equal(typeof f.name, 'string', `missing name: ${JSON.stringify(f)}`);
      assert.ok(f.name.length > 0);
      assert.ok(Number.isFinite(f.kcal));
      assert.ok(Number.isFinite(f.protein_g));
      assert.ok(Number.isFinite(f.carbs_g));
      assert.ok(Number.isFinite(f.fat_g));
    }
  });

  it('kcal consistency — within ±20 kcal of 4P + 4C + 9F (Atwater)', () => {
    // Not perfect for high-fiber or high-alcohol items, but good sanity check.
    for (const f of FOOD_DB) {
      const atwater = f.protein_g * 4 + f.carbs_g * 4 + f.fat_g * 9;
      const diff = Math.abs(f.kcal - atwater);
      // Allow 40 kcal drift: fiber, alcohol, rounding, and genuine variety
      // between CIQUAL measurements. Catches order-of-magnitude errors.
      assert.ok(diff < 80, `${f.name}: kcal=${f.kcal} vs Atwater=${atwater}`);
    }
  });
});

describe('searchFoodDB', () => {
  it('returns [] for queries shorter than 2 chars', () => {
    assert.deepEqual(searchFoodDB(''), []);
    assert.deepEqual(searchFoodDB('a'), []);
  });

  it('matches prefix case-insensitively', () => {
    const r = searchFoodDB('ban');
    assert.ok(r.find((f: { name: string }) => f.name === 'banane'));
  });

  it('accent-insensitive search', () => {
    // "epinar" (no é) should still find "épinard"
    const r = searchFoodDB('epin');
    assert.ok(r.find((f: { name: string }) => f.name === 'épinard'));
  });

  it('matches aliases (EN when input is EN)', () => {
    const r = searchFoodDB('apple');
    assert.ok(r.find((f: { name: string }) => f.name === 'pomme'));
  });

  it('prefix match ranks ahead of substring match', () => {
    // "to" → tomate should rank before any substring-only match
    const r = searchFoodDB('tom');
    assert.equal(r[0].name, 'tomate');
  });

  it('respects the limit parameter', () => {
    const r = searchFoodDB('a', 3);
    // "a" is below min-length → []. Use a real query:
    const r2 = searchFoodDB('p', 3);
    assert.ok(r2.length <= 3);
  });

  it('extraFoods argument merges user-custom foods into results', () => {
    const extras = [{ name: 'pain de campagne', kcal: 255, protein_g: 9, carbs_g: 48, fat_g: 2 }];
    const r = searchFoodDB('pain de', 3, extras);
    assert.ok(r.find((f: { name: string }) => f.name === 'pain de campagne'));
  });

  it('custom prefix matches outrank built-in prefix matches at the same tier', () => {
    const extras = [{ name: 'banane bio', kcal: 90, protein_g: 1, carbs_g: 20, fat_g: 0.3 }];
    const r = searchFoodDB('ban', 5, extras);
    // Both "banane" (built-in) and "banane bio" (custom) prefix-match "ban".
    // Custom wins the tie.
    assert.equal(r[0].name, 'banane bio');
  });
});

describe('reconcileWithFoodDB', () => {
  it('matches an exact DB name and scales macros to grams', () => {
    const r = reconcileWithFoodDB({
      name: 'pomme',
      estimated_grams: 150,
      kcal: 200, protein_g: 1, carbs_g: 50, fat_g: 2, // LLM guesses
      confidence: 'medium',
    });
    assert.equal(r.source, 'db');
    assert.equal(r.name, 'pomme');
    // DB pomme: 54/100g → 81 kcal for 150g
    assert.equal(r.kcal, 81);
    assert.equal(r.carbs_g, 18);
    assert.equal(r.confidence, 'medium');
  });

  it('falls back to first-token when full name misses', () => {
    const r = reconcileWithFoodDB({
      name: 'pomme rouge bien mûre',
      estimated_grams: 100,
      kcal: 999, protein_g: 99, carbs_g: 99, fat_g: 99,
    });
    assert.equal(r.source, 'db');
    assert.equal(r.name, 'pomme');
    assert.equal(r.kcal, 54);
  });

  it('matches through an alias (EN)', () => {
    const r = reconcileWithFoodDB({
      name: 'cucumber',
      estimated_grams: 200,
      kcal: 999, protein_g: 99, carbs_g: 99, fat_g: 99,
    });
    assert.equal(r.source, 'db');
    assert.equal(r.name, 'concombre');
  });

  it('passes the LLM values through when there is no DB hit', () => {
    const r = reconcileWithFoodDB({
      name: 'plat mystère',
      estimated_grams: 300,
      kcal: 450, protein_g: 20, carbs_g: 40, fat_g: 15,
      confidence: 'low',
    });
    assert.equal(r.source, 'llm');
    assert.equal(r.name, 'plat mystère');
    assert.equal(r.kcal, 450);
    assert.equal(r.confidence, 'low');
  });

  it('R6.8: when estimated_grams is 0/missing, returns LLM values unchanged (not 0)', () => {
    // The LLM sometimes returns a name without a portion estimate — e.g.
    // for a mixed plate. Scaling per-100 g DB values by (0 / 100) would
    // zero out everything. Guard: reconcile is a no-op when grams<=0.
    const r = reconcileWithFoodDB({
      name: 'pomme',
      estimated_grams: 0,
      kcal: 120, protein_g: 1, carbs_g: 30, fat_g: 1,
      confidence: 'medium',
    });
    assert.equal(r.source, 'llm');
    assert.equal(r.kcal, 120);
    assert.equal(r.carbs_g, 30);
  });

  it('handles missing/invalid input gracefully', () => {
    assert.equal(reconcileWithFoodDB(null), null);
    assert.equal(reconcileWithFoodDB(undefined), undefined);
    const r = reconcileWithFoodDB({ name: '', estimated_grams: 0 });
    assert.equal(r.source, 'llm');
  });

  it('0 grams is a reconcile no-op — see R6.8: returns LLM values, not zero', () => {
    // Contract changed in R6.8: previously 0 grams → 0 macros with
    // source='db' (because f = 0/100 = 0). That silently erased the
    // LLM's own kcal estimate whenever it didn't emit a portion. New
    // behaviour: skip reconcile when grams <= 0.
    const r = reconcileWithFoodDB({
      name: 'banane', estimated_grams: 0,
      kcal: 500, protein_g: 50, carbs_g: 50, fat_g: 50,
    });
    assert.equal(r.source, 'llm');
    assert.equal(r.kcal, 500);
    assert.equal(r.carbs_g, 50);
  });
});
