/**
 * Direct unit coverage for scoring-engine.ts helpers that were
 * previously only exercised through scoreProduct() fixture tests.
 *
 * Findings from the Phase-1 audit:
 *   findAdditive — 215 entries in ADDITIVES_DB; the name-match path
 *     is the only thing standing between an ingredient string and
 *     its tier penalty. Worth its own tests.
 *   getThresholds — pure mapping; trivial but the absence of a test
 *     means a future category rename / threshold tweak silently
 *     skips coverage.
 *   countTier1Additives — used by checkVeto's >3-tier-1 cap; we
 *     need to pin its 0/1/3/4 boundary behaviour.
 *   inferNovaClass — heuristic NOVA-1..4 inference. The fresh-
 *     produce escape hatch (added per the FRESH_PRODUCE_NAME regex)
 *     is the part most likely to silently regress.
 *   normalize — accent stripping + lowercase + alpha-only collapse.
 *     Underpins findAdditive's synonym matching.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import {
  countTier1Additives,
  findAdditive,
  getThresholds,
  inferNovaClass,
  normalize,
} from '../src/scoring-engine.ts';
import type { Ingredient, ProductInput } from '../src/scoring-engine.ts';

function ing(name: string, extras: Partial<Ingredient> = {}): Ingredient {
  return { name, ...extras };
}

function p(overrides: Partial<ProductInput> = {}): ProductInput {
  return {
    name: 'X',
    category: 'other',
    nova_class: 4,
    ingredients: [],
    nutrition: {
      energy_kcal: 0, fat_g: 0, saturated_fat_g: 0, carbs_g: 0,
      sugars_g: 0, fiber_g: 0, protein_g: 0, salt_g: 0,
    },
    ...overrides,
  };
}

// ---------------------------------------------------------------------

describe('engine helpers: normalize', () => {
  it('lowercases', () => {
    assert.equal(normalize('SUCRE'), 'sucre');
  });
  it('strips accents', () => {
    assert.equal(normalize('épinard'), 'epinard');
    assert.equal(normalize('orangé'), 'orange');
  });
  it('collapses non-alphanumeric to single space + trims', () => {
    assert.equal(normalize('  jambon  ,  17.4 % '), 'jambon 17 4');
  });
  it('preserves digits + hyphens', () => {
    assert.equal(normalize('E-250'), 'e-250');
  });
});

// ---------------------------------------------------------------------

describe('engine helpers: findAdditive', () => {
  it('matches by exact E-number', () => {
    const m = findAdditive(ing('whatever', { e_number: 'E250' }));
    assert.ok(m, 'expected a match');
    assert.equal(m!.tier, 1);
    assert.equal(m!.category, 'preservative');
  });

  it('upper-cases and strips spaces from the e-number key', () => {
    const m = findAdditive(ing('x', { e_number: 'e 250' }));
    assert.ok(m);
    assert.equal(m!.e_number, 'E250');
  });

  it('matches by name synonym', () => {
    const m = findAdditive(ing('Nitrite de sodium'));
    assert.ok(m);
    assert.equal(m!.e_number, 'E250');
  });

  it('matches a synonym embedded in a longer string', () => {
    const m = findAdditive(ing('Conservateur: nitrite de sodium et eau'));
    assert.ok(m);
    assert.equal(m!.e_number, 'E250');
  });

  it('returns null for plain food names with no match', () => {
    assert.equal(findAdditive(ing('tomate')), null);
    assert.equal(findAdditive(ing('farine de blé')), null);
  });

  it('uses natural-colorant fallback only when category="additive"', () => {
    // Marked food → no additive (paprika is a regular ingredient)
    assert.equal(findAdditive(ing('paprika')), null);
    // Marked additive → recognised as E160c
    const m = findAdditive(ing('paprika', { category: 'additive' }));
    assert.ok(m);
    assert.equal(m!.e_number, 'E160c');
  });
});

// ---------------------------------------------------------------------

describe('engine helpers: getThresholds', () => {
  it('returns category-specific thresholds for cheese', () => {
    const t = getThresholds('cheese');
    // Cheese has its own sat-fat scale (the default would unfairly tank it)
    assert.deepEqual(t.sat_fat_thresholds, [12, 20, 30]);
    assert.equal(t.expect_micronutrients, true);
  });

  it('returns DEFAULT_THRESHOLDS for "other"', () => {
    const t = getThresholds('other');
    assert.deepEqual(t.protein_g, [3, 6, 12]);
    assert.deepEqual(t.fiber_g, [1.5, 3, 6]);
  });

  it('beverages do not expect micronutrients', () => {
    assert.equal(getThresholds('beverage_soft').expect_micronutrients, false);
  });

  it('condiment carries a relaxed sugar scale', () => {
    const t = getThresholds('condiment');
    assert.deepEqual(t.sugar_thresholds, [10, 20, 30, 45]);
  });
});

// ---------------------------------------------------------------------

describe('engine helpers: countTier1Additives', () => {
  it('returns 0 on a clean ingredient list', () => {
    assert.equal(countTier1Additives(p({ ingredients: [ing('tomate'), ing('basilic')] })), 0);
  });

  it('counts each Tier-1 hit exactly once', () => {
    const product = p({
      ingredients: [
        ing('jambon'),
        ing('nitrite de sodium'),                       // E250 — Tier 1
        ing('emulsifiant', { e_number: 'E466' }),       // CMC — Tier 1
        ing('sel'),
        ing('aspartame'),                                // Tier 2 (not counted here)
      ],
    });
    assert.equal(countTier1Additives(product), 2);
  });

  it('counts E251/E252 (Tier 1) but not E300 (vitamin C, not Tier 1)', () => {
    const product = p({
      ingredients: [
        ing('e', { e_number: 'E251' }),
        ing('e', { e_number: 'E252' }),
        ing('e', { e_number: 'E300' }),
      ],
    });
    assert.equal(countTier1Additives(product), 2);
  });
});

// ---------------------------------------------------------------------

describe('engine helpers: inferNovaClass', () => {
  it('NOVA 1: empty ingredient list + recognised fresh-produce name', () => {
    // Barcode-only OFF lookup with no ingredients — without the
    // FRESH_PRODUCE_NAME escape hatch, "banane" silently fell through
    // to NOVA 4. The regex is the bug fix; the test pins it.
    assert.equal(inferNovaClass(p({ name: 'Banane', ingredients: [] })), 1);
    assert.equal(inferNovaClass(p({ name: 'Pommes', ingredients: [] })), 1);
    assert.equal(inferNovaClass(p({ name: 'Apple', ingredients: [] })), 1);
  });

  it('NOVA 4: empty ingredient list + unrecognised name (conservative fallback)', () => {
    assert.equal(inferNovaClass(p({ name: 'Mystery Goo', ingredients: [] })), 4);
  });

  it('NOVA 1: single whole-food ingredient, no additives', () => {
    assert.equal(inferNovaClass(p({ ingredients: [ing('lentilles')] })), 1);
  });

  it('NOVA 2: ≤3 culinary-only ingredients (sucre/sel/huile/etc.)', () => {
    assert.equal(inferNovaClass(p({ ingredients: [ing('sucre'), ing('eau')] })), 2);
  });

  it('NOVA 3: clean composition with ≤2 additives + no UPF markers', () => {
    const got = inferNovaClass(p({
      ingredients: [
        ing('lait'), ing('crème'), ing('sucre'), ing('cacao'),
        ing('lécithine de soja', { category: 'additive', e_number: 'E322' }),
      ],
    }));
    assert.equal(got, 3);
  });

  it('NOVA 4: cosmetic colorant present', () => {
    const got = inferNovaClass(p({
      ingredients: [
        ing('eau'),
        ing('tartrazine', { category: 'additive', e_number: 'E102' }), // colorant
      ],
    }));
    assert.equal(got, 4);
  });

  it('NOVA 4: UPF marker (arômes) present even without an E-number', () => {
    const got = inferNovaClass(p({
      ingredients: [ing('lait'), ing('sucre'), ing('arômes')],
    }));
    assert.equal(got, 4);
  });
});
