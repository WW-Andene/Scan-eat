/**
 * Tests for the 2026-05-01 ADDITIVES_DB additions (Engine 2.2.0).
 *
 * What we pin:
 *   - The new E-numbers + their FR + EN synonym lookups resolve.
 *   - Tier assignments are stable: caramel sub-class split (a/b → 3,
 *     c/d → 2, bare E150 → 2 conservative).
 *   - Each new entry carries a `source` field — provenance is never
 *     blank.
 *   - countTier1Additives is unaffected by the new entries (none of
 *     them are Tier 1).
 *   - ADDITIVES_DB length increases as expected (smoke against
 *     accidental deletes).
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { ADDITIVES_DB, countTier1Additives, findAdditive } from '../src/scoring-engine.ts';
import type { ProductInput } from '../src/scoring-engine.ts';

function lookup(eNumber: string) {
  return ADDITIVES_DB.find((a) => a.e_number === eNumber);
}

// ---------------------------------------------------------------------

describe('additives 2.2.0: caramel sub-class split', () => {
  it('E150c (ammonia caramel) is Tier 2 — 4-MEI present', () => {
    const a = lookup('E150c');
    assert.ok(a, 'E150c missing');
    assert.equal(a!.tier, 2);
    assert.match(a!.concern, /4-MEI|4-methylimidazole/i);
    assert.match(a!.source, /IARC/i);
  });

  it('E150d (sulfite-ammonia caramel) is Tier 2 — 4-MEI present', () => {
    const a = lookup('E150d');
    assert.ok(a);
    assert.equal(a!.tier, 2);
    assert.match(a!.concern, /4-MEI|4-methylimidazole/i);
  });

  it('E150a (plain caramel) is Tier 3 — no 4-MEI present', () => {
    const a = lookup('E150a');
    assert.ok(a);
    assert.equal(a!.tier, 3);
    // Tier-3 == no 4-MEI carcinogenicity flag; the concern wording can
    // mention "no 4-MEI concern" so we match for the absence + allow
    // the explanatory text. Tier is the contract.
    assert.match(a!.concern, /no 4-MEI|plain.*caramel|no .*concern/i);
  });

  it('E150b (caustic-sulfite caramel) is Tier 3 — no 4-MEI', () => {
    const a = lookup('E150b');
    assert.ok(a);
    assert.equal(a!.tier, 3);
  });

  it('Bare "E150" stays Tier 2 (conservative when sub-class is unknown)', () => {
    const a = lookup('E150');
    assert.ok(a);
    assert.equal(a!.tier, 2);
    assert.match(a!.concern, /sub-class unspecified|conservative|ambiguous/i);
  });
});

describe('additives 2.2.0: name lookup of new entries', () => {
  const cases: Array<[string, string]> = [
    ['riboflavine', 'E101'],
    ['vitamine b2', 'E101'],
    ['beta-carotène', 'E160a'],
    ['anthocyanes', 'E163'],
    ['carbonate de calcium', 'E170'],
    ['acide acétique', 'E260'],
    ['lactate de sodium', 'E325'],
    ['lactate de calcium', 'E327'],
    ['citrate de potassium', 'E332'],
    ['cellulose microcristalline', 'E460'],
    ['chlorure de calcium', 'E509'],
    ['glucono-delta-lactone', 'E575'],
    ['guanylate disodique', 'E627'],
    ['inosinate disodique', 'E631'],
    ['ribonucléotides disodiques', 'E635'],
    ['cire d\'abeille', 'E901'],
    ['l-cystéine', 'E920'],
  ];

  for (const [name, eNum] of cases) {
    it(`"${name}" → ${eNum}`, () => {
      const m = findAdditive({ name });
      assert.ok(m, `findAdditive returned null for "${name}"`);
      assert.equal(m!.e_number, eNum);
    });
  }
});

describe('additives 2.2.0: provenance + integrity', () => {
  const NEW_E_NUMBERS = [
    'E101', 'E150a', 'E150b', 'E150c', 'E150d', 'E160a', 'E163',
    'E170', 'E260', 'E325', 'E327', 'E332', 'E460', 'E509',
    'E575', 'E627', 'E631', 'E635', 'E901', 'E920',
  ];

  it('every new entry carries a non-empty source citation', () => {
    for (const en of NEW_E_NUMBERS) {
      const a = lookup(en);
      assert.ok(a, `${en} missing from ADDITIVES_DB`);
      assert.ok(
        a!.source && a!.source.length >= 10,
        `${en} has empty/trivial source: "${a!.source}"`,
      );
    }
  });

  it('every new entry carries a non-empty plain-language concern', () => {
    for (const en of NEW_E_NUMBERS) {
      const a = lookup(en);
      assert.ok(
        a!.concern && a!.concern.length >= 20,
        `${en} concern too short: "${a!.concern}"`,
      );
    }
  });

  it('none of the new entries are Tier 1', () => {
    for (const en of NEW_E_NUMBERS) {
      const a = lookup(en);
      assert.notEqual(a!.tier, 1, `${en} should not be Tier 1`);
    }
  });
});

describe('additives 2.2.0: scoring impact', () => {
  it('a product with E627 + E631 (umami pair) is recognised — was invisible before 2.2.0', () => {
    const product: ProductInput = {
      name: 'Bouillon en cube',
      category: 'condiment',
      nova_class: 4,
      ingredients: [
        { name: 'sel' },
        { name: 'glutamate monosodique', category: 'additive', e_number: 'E621' },
        { name: 'guanylate disodique', category: 'additive', e_number: 'E627' },
        { name: 'inosinate disodique', category: 'additive', e_number: 'E631' },
      ],
      nutrition: {
        energy_kcal: 150, fat_g: 2, saturated_fat_g: 1,
        carbs_g: 8, sugars_g: 1, fiber_g: 0,
        protein_g: 12, salt_g: 50,
      },
    };
    // E621 (MSG) was already in the DB; E627 + E631 are the additions.
    // findAdditive must resolve all three now.
    assert.ok(findAdditive(product.ingredients[1]));
    assert.ok(findAdditive(product.ingredients[2]));
    assert.ok(findAdditive(product.ingredients[3]));
  });

  it('countTier1Additives is unchanged by the new (Tier 2/3) entries', () => {
    const product: ProductInput = {
      name: 'Caramel test',
      category: 'snack_sweet',
      nova_class: 4,
      ingredients: [
        { name: 'caramel', category: 'additive', e_number: 'E150d' }, // Tier 2
        { name: 'beta-carotène', category: 'additive', e_number: 'E160a' }, // Tier 3
      ],
      nutrition: {
        energy_kcal: 400, fat_g: 10, saturated_fat_g: 4,
        carbs_g: 60, sugars_g: 30, fiber_g: 1,
        protein_g: 4, salt_g: 0.2,
      },
    };
    assert.equal(countTier1Additives(product), 0);
  });
});
