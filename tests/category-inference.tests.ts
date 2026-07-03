/**
 * Tests for the name-based category inference fallback that fires
 * when product.category === 'other' (ENGINE_VERSION 2.1.0).
 *
 * Pin behaviour:
 *   - Recognised FR + EN supermarket terms map to the right category.
 *   - Most-specific patterns win over more general ones (e.g.
 *     "fromage blanc" → yogurt, not cheese).
 *   - No match → returns 'other' (caller falls through to default
 *     thresholds).
 *   - scoreProduct surfaces a warning when inference fires + uses the
 *     inferred category in the audit + drives pillar thresholds with it.
 *   - Scoring with category='yogurt' explicit equals scoring with
 *     category='other' + a yogurt-naming product.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { inferCategoryFromName, scoreProduct } from '../src/scoring-engine.ts';
import type { ProductInput } from '../src/scoring-engine.ts';

function p(overrides: Partial<ProductInput> = {}): ProductInput {
  return {
    name: 'X',
    category: 'other',
    nova_class: 1,
    ingredients: [{ name: 'lait', is_whole_food: true }],
    nutrition: {
      energy_kcal: 60, fat_g: 3, saturated_fat_g: 1.8,
      carbs_g: 4, sugars_g: 4, fiber_g: 0, protein_g: 4, salt_g: 0.1,
    },
    ...overrides,
  };
}

describe('inferCategoryFromName: beverages', () => {
  it('recognises water in FR + EN', () => {
    assert.equal(inferCategoryFromName('Eau Évian'), 'beverage_water');
    assert.equal(inferCategoryFromName('Spring Water'), 'beverage_water');
    assert.equal(inferCategoryFromName('Eau gazeuse Perrier'), 'beverage_water');
  });
  it('recognises juice + nectar', () => {
    assert.equal(inferCategoryFromName('Jus d\'orange pressé'), 'beverage_juice');
    assert.equal(inferCategoryFromName('Apple juice'), 'beverage_juice');
    assert.equal(inferCategoryFromName('Nectar de poire'), 'beverage_juice');
  });
  it('recognises sodas and energy drinks', () => {
    assert.equal(inferCategoryFromName('Coca-Cola Zero'), 'beverage_soft');
    assert.equal(inferCategoryFromName('Red Bull Original'), 'beverage_soft');
    assert.equal(inferCategoryFromName('Limonade au citron'), 'beverage_soft');
  });
});

describe('inferCategoryFromName: dairy disambiguation', () => {
  it('"yaourt nature" → yogurt', () => {
    assert.equal(inferCategoryFromName('Yaourt nature bio'), 'yogurt');
  });
  it('"fromage blanc" → yogurt (not cheese)', () => {
    // Specific-first ordering pins this — fromage blanc is dairy-y but
    // its score profile aligns with yogurt thresholds.
    assert.equal(inferCategoryFromName('Fromage blanc 0%'), 'yogurt');
  });
  it('"fromage" alone → cheese', () => {
    assert.equal(inferCategoryFromName('Fromage de Comté'), 'cheese');
    assert.equal(inferCategoryFromName('Camembert de Normandie'), 'cheese');
    assert.equal(inferCategoryFromName('Mozzarella di Bufala'), 'cheese');
  });
  it('Skyr → yogurt', () => {
    assert.equal(inferCategoryFromName('Skyr islandais nature'), 'yogurt');
  });
});

describe('inferCategoryFromName: meat / fish', () => {
  it('charcuterie terms → processed_meat', () => {
    assert.equal(inferCategoryFromName('Jambon de Paris'), 'processed_meat');
    assert.equal(inferCategoryFromName('Saucisson sec à l\'ancienne'), 'processed_meat');
    assert.equal(inferCategoryFromName('Chorizo doux'), 'processed_meat');
  });
  it('fresh meat terms → fresh_meat', () => {
    assert.equal(inferCategoryFromName('Escalope de poulet fermier'), 'fresh_meat');
    assert.equal(inferCategoryFromName('Steak haché de bœuf'), 'fresh_meat');
  });
  it('fish + seafood', () => {
    assert.equal(inferCategoryFromName('Filet de saumon Atlantique'), 'fish');
    assert.equal(inferCategoryFromName('Sardines à l\'huile'), 'fish');
    assert.equal(inferCategoryFromName('Crevettes décortiquées'), 'fish');
  });
});

describe('inferCategoryFromName: bakery + cereals + ready meals', () => {
  it('bread terms', () => {
    assert.equal(inferCategoryFromName('Pain de campagne'), 'bread');
    assert.equal(inferCategoryFromName('Baguette traditionnelle'), 'bread');
    assert.equal(inferCategoryFromName('Brioche tressée'), 'bread');
  });
  it('breakfast cereals', () => {
    assert.equal(inferCategoryFromName('Muesli aux fruits rouges'), 'breakfast_cereal');
    assert.equal(inferCategoryFromName('Granola maison'), 'breakfast_cereal');
    assert.equal(inferCategoryFromName('Cornflakes Kellogg\'s'), 'breakfast_cereal');
  });
  it('ready meals + dishes', () => {
    assert.equal(inferCategoryFromName('Lasagne bolognese'), 'ready_meal');
    assert.equal(inferCategoryFromName('Paella royale'), 'ready_meal');
    assert.equal(inferCategoryFromName('Hachis parmentier'), 'ready_meal');
  });
  it('sandwiches', () => {
    assert.equal(inferCategoryFromName('Sandwich poulet curry'), 'sandwich');
    assert.equal(inferCategoryFromName('Wrap végétarien'), 'sandwich');
  });
});

describe('inferCategoryFromName: snacks + condiments + oils', () => {
  it('sweet snacks', () => {
    assert.equal(inferCategoryFromName('Biscuits chocolatés'), 'snack_sweet');
    assert.equal(inferCategoryFromName('Tablette de chocolat noir 70%'), 'snack_sweet');
    assert.equal(inferCategoryFromName('Bonbons fruités'), 'snack_sweet');
    assert.equal(inferCategoryFromName('Pâte à tartiner Nutella'), 'snack_sweet');
  });
  it('salty snacks', () => {
    assert.equal(inferCategoryFromName('Chips salées'), 'snack_salty');
    assert.equal(inferCategoryFromName('Crackers au sésame'), 'snack_salty');
    assert.equal(inferCategoryFromName('Cacahuètes grillées'), 'snack_salty');
  });
  it('condiments', () => {
    assert.equal(inferCategoryFromName('Mayonnaise à l\'ancienne'), 'condiment');
    assert.equal(inferCategoryFromName('Pesto verde'), 'condiment');
    assert.equal(inferCategoryFromName('Sauce barbecue fumée'), 'condiment');
  });
  it('oils + butter', () => {
    assert.equal(inferCategoryFromName('Huile d\'olive extra vierge'), 'oil_fat');
    assert.equal(inferCategoryFromName('Beurre doux'), 'oil_fat');
    assert.equal(inferCategoryFromName('Margarine végétale'), 'oil_fat');
  });
});

describe('inferCategoryFromName: edge cases', () => {
  it('empty + non-string input → "other"', () => {
    assert.equal(inferCategoryFromName(''), 'other');
    assert.equal(inferCategoryFromName(undefined as unknown as string), 'other');
    assert.equal(inferCategoryFromName(null as unknown as string), 'other');
  });
  it('unrecognised name → "other" (no false positives)', () => {
    assert.equal(inferCategoryFromName('Mystery Goo X-99'), 'other');
    assert.equal(inferCategoryFromName('Quelque chose d\'inconnu'), 'other');
  });
  it('case-insensitive', () => {
    assert.equal(inferCategoryFromName('YAOURT'), 'yogurt');
    assert.equal(inferCategoryFromName('yaourt'), 'yogurt');
    assert.equal(inferCategoryFromName('YaOuRt'), 'yogurt');
  });
});

describe('scoreProduct: name-based category inference', () => {
  it('uses inferred category when input was "other" with a recognisable name', () => {
    const audit = scoreProduct(p({
      name: 'Yaourt nature bio',
      category: 'other',
    }));
    assert.equal(audit.category, 'yogurt');
    assert.ok(
      audit.warnings.some((w) => /Category inferred/i.test(w)),
      'expected a "Category inferred" warning',
    );
  });

  it('does not override a category that was already non-"other"', () => {
    const audit = scoreProduct(p({
      name: 'Yaourt nature',
      category: 'cheese',
    }));
    assert.equal(audit.category, 'cheese');
    assert.ok(
      !audit.warnings.some((w) => /Category inferred/i.test(w)),
      'should not warn when no inference happened',
    );
  });

  it('falls through to "other" + no warning when name is unrecognisable', () => {
    const audit = scoreProduct(p({
      name: 'Mystery Goo X-99',
      category: 'other',
    }));
    assert.equal(audit.category, 'other');
    assert.ok(
      !audit.warnings.some((w) => /Category inferred/i.test(w)),
      'no inference happened, no warning expected',
    );
  });

  it('explicit-yogurt scoring matches inferred-yogurt scoring', () => {
    const explicit = scoreProduct(p({ name: 'Yaourt', category: 'yogurt' }));
    const inferred = scoreProduct(p({ name: 'Yaourt', category: 'other' }));
    // Same pillar scores (the warning differs but the math doesn't).
    assert.equal(inferred.score, explicit.score);
    assert.equal(inferred.grade, explicit.grade);
    assert.equal(inferred.pillars.nutritional_density.score, explicit.pillars.nutritional_density.score);
  });

  it('inference does not mutate the caller\'s input object', () => {
    const input = p({ name: 'Yaourt nature', category: 'other' });
    scoreProduct(input);
    // applyCategoryInference must clone, not mutate.
    assert.equal(input.category, 'other');
  });
});
