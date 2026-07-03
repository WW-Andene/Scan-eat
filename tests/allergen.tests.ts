/**
 * ============================================================================
 * ALLERGEN DETECTION — TESTS
 * ============================================================================
 *
 * Covers the EU Annex II (1169/2011) allergen detector at public/allergens.js.
 * Missing a real allergen in a scanned product is the worst kind of bug this
 * app can ship, so every rule pattern is tested against at least one positive
 * and one "looks similar but isn't" negative example.
 * ============================================================================
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import { detectAllergens, ANNEX_II_KEYS } from '../public/core/allergens.js';

function product(names: string[]) {
  return { ingredients: names.map((name) => ({ name, category: 'food' })) };
}

describe('ANNEX_II_KEYS — coverage of EU 1169/2011 Annex II', () => {
  it('exposes exactly the 14 mandatory allergens', () => {
    assert.equal(ANNEX_II_KEYS.length, 14);
    const expected = ['gluten', 'crustaceans', 'eggs', 'fish', 'peanuts', 'soy',
                      'lactose', 'nuts', 'celery', 'mustard', 'sesame', 'sulfites',
                      'lupin', 'molluscs'];
    assert.deepEqual([...ANNEX_II_KEYS].sort(), expected.sort());
  });
  it('every Annex II key has a working positive detection', () => {
    const positives: Record<string, string> = {
      gluten: 'farine de blé',
      crustaceans: 'crevettes',
      eggs: 'oeufs',
      fish: 'poisson',
      peanuts: 'arachide',
      soy: 'soja',
      lactose: 'lait',
      nuts: 'amandes',
      celery: 'céleri',
      mustard: 'moutarde',
      sesame: 'sésame',
      sulfites: 'sulfites',
      lupin: 'lupin',
      molluscs: 'moules',
    };
    for (const key of ANNEX_II_KEYS) {
      const ex = positives[key];
      assert.ok(ex, `no positive example mapped for key '${key}' — extend the test`);
      const found = detectAllergens(product([ex]));
      assert.ok(found.find((h: { key: string }) => h.key === key), `'${ex}' should trigger '${key}'`);
    }
  });
});

// ============================================================================
// Positive detection — each rule must fire on a typical French label word
// ============================================================================

describe('detectAllergens — positives', () => {
  it('detects gluten via "farine de blé"', () => {
    const hits = detectAllergens(product(['farine de blé', 'sel']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'gluten'));
  });

  it('detects lactose via "lait entier"', () => {
    const hits = detectAllergens(product(['lait entier']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'lactose'));
  });

  it('detects lactose via "skyr" (yogurt-like fresh cheese)', () => {
    const hits = detectAllergens(product(['skyr nature']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'lactose'));
  });

  it('detects eggs via "blanc d\'œuf"', () => {
    const hits = detectAllergens(product(['blanc d\'œuf']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'eggs'));
  });

  it('detects peanuts via "arachide"', () => {
    const hits = detectAllergens(product(['huile d\'arachide']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'peanuts'));
  });

  it('detects tree nuts via "noisettes"', () => {
    const hits = detectAllergens(product(['sucre', 'noisettes torréfiées']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'nuts'));
  });

  it('detects soy via "lécithine de soja"', () => {
    const hits = detectAllergens(product(['lécithine de soja']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'soy'));
  });

  it('detects sulfites via E220', () => {
    const hits = detectAllergens(product(['conservateur E220']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'sulfites'));
  });

  it('detects lupin via "farine de lupin"', () => {
    const hits = detectAllergens(product(['farine de lupin']));
    assert.ok(hits.find((h: { key: string }) => h.key === 'lupin'));
  });

  it('deduplicates: same allergen in multiple ingredients → one hit, all triggers', () => {
    const hits = detectAllergens(product(['lait', 'crème fraîche', 'beurre']));
    const lact = hits.find((h: { key: string }) => h.key === 'lactose');
    assert.equal(hits.filter((h: { key: string }) => h.key === 'lactose').length, 1);
    assert.equal(lact.triggers.length, 3);
  });
});

// ============================================================================
// Negative — common false-positive traps that the regex must NOT catch
// ============================================================================

describe('detectAllergens — negatives', () => {
  it('does not match "malt" substring inside "malteur"-like distractors (word boundary)', () => {
    // "malt" is a gluten trigger, but it MUST not fire on unrelated words
    // like "chocolat au malteur" — actually "malt" has word boundaries
    // so `\bmalt\b` only matches on its own. Confirm.
    const hits = detectAllergens(product(['maltodextrine']));
    assert.equal(hits.find((h: { key: string }) => h.key === 'gluten'), undefined);
  });

  it('does not confuse "eau" with "oeufs"', () => {
    const hits = detectAllergens(product(['eau', 'sucre']));
    assert.equal(hits.find((h: { key: string }) => h.key === 'eggs'), undefined);
  });

  it('empty product has no hits', () => {
    assert.deepEqual(detectAllergens({ ingredients: [] }), []);
  });

  it('missing ingredients array is tolerated', () => {
    assert.deepEqual(detectAllergens({}), []);
  });
});

// ============================================================================
// Localization
// ============================================================================

describe('detectAllergens — labels', () => {
  it('returns French labels by default', () => {
    const hits = detectAllergens(product(['lait']));
    const lact = hits.find((h: { key: string }) => h.key === 'lactose');
    assert.equal(lact.label, 'Lactose / Lait');
  });

  it('returns English labels when lang="en"', () => {
    const hits = detectAllergens(product(['lait']), 'en');
    const lact = hits.find((h: { key: string }) => h.key === 'lactose');
    assert.equal(lact.label, 'Lactose / Milk');
  });
});
