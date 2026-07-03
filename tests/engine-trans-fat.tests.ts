/**
 * Pin the trans-fat penalty stack. Per Phase-1 audit decision
 * (2026-05-01), the engine intentionally applies BOTH:
 *
 *   1. -10 in scoreNegativeNutrients (FSA "no safe level" framing)
 *   2. veto cap at 40 in checkVeto (WHO REPLACE elimination target)
 *
 * Each draws on an independently authoritative source. Removing
 * either would weaken a citation. This test fails if a future
 * refactor accidentally collapses the two — the comment in
 * checkVeto + this test together pin the contract.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { scoreProduct } from '../src/scoring-engine.ts';
import type { ProductInput } from '../src/scoring-engine.ts';

function baseProduct(overrides: Partial<ProductInput> = {}): ProductInput {
  return {
    name: 'Test Bread',
    category: 'bread',
    nova_class: 1,
    ingredients: [
      { name: 'farine de blé complète', is_whole_food: true },
      { name: 'eau' },
      { name: 'levain' },
      { name: 'sel' },
    ],
    nutrition: {
      energy_kcal: 250,
      fat_g: 3,
      saturated_fat_g: 0.5,
      carbs_g: 48,
      sugars_g: 2,
      fiber_g: 7,
      protein_g: 9,
      salt_g: 1.0,
      trans_fat_g: 0,
    },
    organic: false,
    has_health_claims: false,
    has_misleading_marketing: false,
    named_oils: true,
    origin_transparent: true,
    ...overrides,
  };
}

describe('engine: trans-fat penalty stack', () => {
  it('without trans fat, no veto + no -10 deduction', () => {
    const audit = scoreProduct(baseProduct({ nutrition: { ...baseProduct().nutrition, trans_fat_g: 0 } }));
    assert.equal(audit.veto.triggered, false);
    const transDeductions = audit.pillars.negative_nutrients.deductions
      .filter((d) => /trans fat/i.test(d.reason));
    assert.equal(transDeductions.length, 0);
  });

  it('with trans fat present, BOTH the -10 pillar deduction AND the veto fire', () => {
    const audit = scoreProduct(baseProduct({
      nutrition: { ...baseProduct().nutrition, trans_fat_g: 0.5 },
    }));
    // Veto fires, capping at 40.
    assert.equal(audit.veto.triggered, true);
    assert.equal(audit.veto.cap, 40);
    assert.match(audit.veto.reason, /trans fat/i);
    // Pillar -10 also fires (independent of veto).
    const transDeductions = audit.pillars.negative_nutrients.deductions
      .filter((d) => /trans fat/i.test(d.reason));
    assert.equal(transDeductions.length, 1);
    assert.equal(transDeductions[0].points, -10);
    assert.equal(transDeductions[0].severity, 'critical');
  });

  it('a high-quality product with trans fat is still capped at 40', () => {
    // An otherwise A+ product with trans fat must not slip through.
    const audit = scoreProduct(baseProduct({
      nutrition: {
        energy_kcal: 250, fat_g: 10, saturated_fat_g: 1,
        carbs_g: 30, sugars_g: 1, fiber_g: 12,
        protein_g: 14, salt_g: 0.3, trans_fat_g: 0.2,
      },
    }));
    assert.ok(audit.score <= 40, `expected score ≤ 40, got ${audit.score}`);
    assert.notEqual(audit.grade, 'A');
    assert.notEqual(audit.grade, 'A+');
  });
});
