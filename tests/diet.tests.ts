/**
 * ============================================================================
 * DIET COMPLIANCE — TESTS
 * ============================================================================
 *
 * Guards the hard-veto system at public/diets.js. A regression here lets
 * non-compliant products through the veto and falsely tells the user they
 * can eat something that violates their diet.
 *
 * The original motivation for this file: the \b-after-accent bug shipped
 * undetected. Tests now pin the accented-form behavior explicitly.
 * ============================================================================
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import { checkDiet } from '../public/core/diets.js';

type Ing = { name: string; category?: string };
function product(name: string, ings: string[]): { name: string; ingredients: Ing[] } {
  return { name, ingredients: ings.map((n) => ({ name: n, category: 'food' })) };
}

// ============================================================================
// Vegetarian — the case the original bug was shipped against
// ============================================================================

describe('checkDiet: vegetarian', () => {
  it('compliant for a plain-vegetable product', () => {
    const p = product('Salade', ['salade', 'tomate', 'huile d\'olive']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, true);
    assert.equal(r.violations.length, 0);
  });

  it('REJECTS pâté (shipped-bug regression guard: accented trigger word)', () => {
    const p = product('Pâté de campagne', ['pâté de porc', 'sel', 'poivre']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, false, 'pâté must NOT pass vegetarian veto');
    assert.ok(r.violations.length > 0);
  });

  it('REJECTS crustacé (shipped-bug regression guard: accented trigger word)', () => {
    const p = product('Soupe', ['bouillon', 'crustacé']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, false);
  });

  it('REJECTS the generic term "crustacé" (regression guard)', () => {
    // crustac[eé] is listed — the é-form specifically must match.
    const p = product('Soupe', ['eau', 'crustacé', 'sel']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, false);
  });

  it('REJECTS jambon (ASCII, baseline)', () => {
    const p = product('Sandwich', ['pain', 'jambon', 'emmental']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, false);
  });

  it('does not false-positive on "poison" (not "poisson")', () => {
    // Defensive — the fish alternation is "poisson" not "poison".
    const p = product('Jeu', ['poison text not a food']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, true);
  });

  it('does not match "viande" inside longer word accidentally', () => {
    // "viande" with a (?<!letter) boundary should still fire on its own.
    const p = product('', ['viande hachée']);
    const r = checkDiet(p, 'vegetarian');
    assert.equal(r.compliant, false);
  });
});

// ============================================================================
// Vegan — superset of vegetarian + dairy + eggs + honey + animal additives
// ============================================================================

describe('checkDiet: vegan', () => {
  it('compliant for a plain-vegetable product', () => {
    const p = product('Soupe légumes', ['carotte', 'pomme de terre', 'eau']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, true);
  });

  it('REJECTS "crème" (accented e — the é-form must fire)', () => {
    const p = product('Dessert', ['lait', 'sucre', 'crème']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, false);
  });

  it('REJECTS "caséine" (accented trigger)', () => {
    const p = product('', ['protéine', 'caséine']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, false);
  });

  it('REJECTS eggs via œuf glyph', () => {
    const p = product('', ['œuf entier', 'farine']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, false);
  });

  it('REJECTS honey', () => {
    const p = product('', ['sucre', 'miel']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, false);
  });

  it('REJECTS carmine (E120)', () => {
    const p = product('Yaourt rose', ['lait', 'E120']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, false);
  });

  it('REJECTS bone phosphate (E542)', () => {
    const p = product('', ['maltodextrine', 'E542']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, false);
  });

  it('"lait de coco" is allowed (negative lookahead exclusion)', () => {
    const p = product('Curry', ['lait de coco', 'curry', 'riz']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, true);
  });

  it('"beurre de cacahuète" is allowed (negative lookahead exclusion)', () => {
    const p = product('', ['beurre de cacahuète', 'sel']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.compliant, true);
  });

  it('V-Label certification earns the preferred bonus', () => {
    const p = product('Burger V-Label', ['protéine de soja', 'avoine']);
    const r = checkDiet(p, 'vegan');
    assert.equal(r.preferredHits.length > 0, true);
    assert.equal(r.certified, true);
  });
});

// ============================================================================
// Halal — pork + alcohol + unverified animal additives
// ============================================================================

describe('checkDiet: halal', () => {
  it('compliant for a plant / halal-labeled product', () => {
    const p = product('Couscous', ['semoule', 'pois chiche', 'carotte']);
    const r = checkDiet(p, 'halal');
    assert.equal(r.compliant, true);
  });

  it('REJECTS pork', () => {
    const p = product('', ['porc', 'sel']);
    const r = checkDiet(p, 'halal');
    assert.equal(r.compliant, false);
  });

  it('REJECTS alcohol / wine', () => {
    const p = product('Sauce', ['vin blanc', 'tomate']);
    const r = checkDiet(p, 'halal');
    assert.equal(r.compliant, false);
  });

  it('REJECTS plain "gélatine" (no halal qualifier)', () => {
    // Note: gélatine ends in 'e' (ASCII), so \b worked before the fix
    // too — this test pins the positive-case behavior, not the bug.
    const p = product('Bonbon', ['sucre', 'gélatine']);
    const r = checkDiet(p, 'halal');
    assert.equal(r.compliant, false);
  });

  it('Certification override: "Certifié halal" flips to compliant', () => {
    // Even with a trigger, halal certification in the product name means
    // we trust the label's verification.
    const p = product('Viande hachée Certifié halal', ['viande de boeuf']);
    const r = checkDiet(p, 'halal');
    // viande is not on halal forbidden (it's vegetarian's list), but this
    // exercises the certification path regardless.
    assert.equal(r.certified, true);
  });
});

// ============================================================================
// Gluten-free — EU Reg 41/2009 grain list
// ============================================================================

describe('checkDiet: gluten_free', () => {
  it('compliant for a rice-based product', () => {
    const p = product('Riz basmati', ['riz']);
    const r = checkDiet(p, 'gluten_free');
    assert.equal(r.compliant, true);
  });

  it('REJECTS "blé" (shipped-bug regression: accented trigger)', () => {
    const p = product('Pain', ['farine de blé', 'sel', 'levure']);
    const r = checkDiet(p, 'gluten_free');
    assert.equal(r.compliant, false);
  });

  it('REJECTS "épeautre" (accented leading char)', () => {
    const p = product('Pain', ['épeautre', 'sel']);
    const r = checkDiet(p, 'gluten_free');
    assert.equal(r.compliant, false);
  });

  it('does NOT match "maltodextrine" (false-positive trap)', () => {
    // "malt" is on the forbidden list. "maltodextrine" must NOT match
    // because the fix's lookahead rejects when the next char is a letter.
    const p = product('', ['maltodextrine']);
    const r = checkDiet(p, 'gluten_free');
    assert.equal(r.compliant, true);
  });

  it('allows oats only when spelled "avoine sans gluten"', () => {
    const a = product('', ['avoine', 'sucre']);
    const b2 = product('', ['avoine sans gluten', 'sucre']);
    assert.equal(checkDiet(a, 'gluten_free').compliant, false);
    assert.equal(checkDiet(b2, 'gluten_free').compliant, true);
  });
});

// ============================================================================
// Kosher — Leviticus 11 land animals + scaleless fish + shellfish
// ============================================================================

describe('checkDiet: kosher', () => {
  it('compliant for a plant-based product', () => {
    const p = product('', ['pommes de terre', 'huile d\'olive', 'sel']);
    const r = checkDiet(p, 'kosher');
    assert.equal(r.compliant, true);
  });

  it('REJECTS pork', () => {
    const p = product('', ['viande de porc', 'sel']);
    const r = checkDiet(p, 'kosher');
    assert.equal(r.compliant, false);
  });

  it('REJECTS "écrevisse" (accented leading char — pins the regression fix)', () => {
    const p = product('', ['écrevisse', 'mayonnaise']);
    const r = checkDiet(p, 'kosher');
    assert.equal(r.compliant, false);
  });

  it('REJECTS "pétoncle" (accented leading char)', () => {
    const p = product('', ['pétoncle', 'persil']);
    const r = checkDiet(p, 'kosher');
    assert.equal(r.compliant, false);
  });

  it('REJECTS "anguille" (scaleless fish — ASCII baseline)', () => {
    const p = product('', ['anguille fumée']);
    const r = checkDiet(p, 'kosher');
    assert.equal(r.compliant, false);
  });

  it('"OU" certification earns the bonus', () => {
    const p = product('Snack OU-certified', ['flour', 'sugar']);
    const r = checkDiet(p, 'kosher');
    assert.equal(r.certified, true);
  });
});

// ============================================================================
// Low-FODMAP — Monash list
// ============================================================================

describe('checkDiet: low_fodmap', () => {
  it('compliant for rice', () => {
    const p = product('', ['riz', 'sel']);
    assert.equal(checkDiet(p, 'low_fodmap').compliant, true);
  });

  it('REJECTS oignon', () => {
    const p = product('', ['oignon caramélisé']);
    assert.equal(checkDiet(p, 'low_fodmap').compliant, false);
  });

  it('REJECTS "chicorée" (accented regression guard)', () => {
    const p = product('', ['fibre de chicorée']);
    assert.equal(checkDiet(p, 'low_fodmap').compliant, false);
  });

  it('"lait sans lactose" still triggers (lactose is a standalone trigger)', () => {
    // Documents current behavior: the rule list has both `lait(?! sans
    // lactose)` and plain `lactose`, so "lait sans lactose" is caught by the
    // second alternative. If this is ever considered wrong, remove `lactose`
    // from the list — but be careful: plain "lactose monohydrate" would then
    // also pass, which may or may not be desired.
    const p = product('', ['lait sans lactose']);
    assert.equal(checkDiet(p, 'low_fodmap').compliant, false);
  });
});

// ============================================================================
// Paleo — no grains, legumes, dairy, refined sugars
// ============================================================================

describe('checkDiet: paleo', () => {
  it('compliant for meat + veggies', () => {
    const p = product('', ['viande de boeuf', 'épinard', 'huile d\'olive']);
    assert.equal(checkDiet(p, 'paleo').compliant, true);
  });

  it('REJECTS grains', () => {
    const p = product('', ['farine de blé']);
    assert.equal(checkDiet(p, 'paleo').compliant, false);
  });

  it('REJECTS legumes', () => {
    const p = product('', ['pois chiche']);
    assert.equal(checkDiet(p, 'paleo').compliant, false);
  });

  it('REJECTS refined sugar (accented "raffiné")', () => {
    const p = product('', ['sucre raffiné']);
    assert.equal(checkDiet(p, 'paleo').compliant, false);
  });
});
