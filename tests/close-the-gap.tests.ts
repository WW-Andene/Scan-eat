/**
 * closeTheGap() tests.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module
import { closeTheGap } from '../public/core/presenters.js';

const DB = [
  { name: 'lentille cuite', kcal: 115, protein_g: 9,  fiber_g: 4,  iron_mg: 3.3 },
  { name: 'pois chiche cuit', kcal: 165, protein_g: 9, fiber_g: 7.6, iron_mg: 1.9 },
  { name: 'amandes',        kcal: 620, protein_g: 21, fiber_g: 12, calcium_mg: 250 },
  { name: 'broccoli',       kcal: 30,  protein_g: 2.8, fiber_g: 2.6, calcium_mg: 47 },
  { name: 'huile d\'olive', kcal: 900, protein_g: 0,  fiber_g: 0 },
];

describe('closeTheGap', () => {
  it('returns [] when totals already meet all targets', () => {
    const out = closeTheGap(
      { protein_g: 100, fiber_g: 30, iron_mg: 20 },
      { protein_g_target: 80, fiber_g_target: 25, iron_mg_target: 11 },
      DB,
    );
    assert.deepEqual(out, []);
  });

  it('finds protein gap-closers ranked by contribution', () => {
    const out = closeTheGap(
      { protein_g: 40, fiber_g: 25, iron_mg: 15 },
      { protein_g_target: 80, fiber_g_target: 25, iron_mg_target: 11 },
      DB,
    );
    const protein = out.find((g) => g.nutrient === 'protein');
    assert.ok(protein);
    assert.equal(protein.deficit, 40);
    assert.ok(protein.suggestions.length > 0);
    // Amandes (21 g/100 g) should outrank lentille (9 g/100 g).
    assert.equal(protein.suggestions[0].name, 'amandes');
  });

  it('skips foods that do not carry the deficit nutrient', () => {
    const out = closeTheGap(
      { protein_g: 0, fiber_g: 0 },
      { protein_g_target: 50, fiber_g_target: 25 },
      DB,
    );
    const fiber = out.find((g) => g.nutrient === 'fiber');
    assert.ok(fiber);
    // huile d'olive has fiber_g=0; never appears in suggestions.
    assert.ok(!fiber.suggestions.find((s) => s.name === "huile d'olive"));
  });

  it('caps suggestions whose grams would exceed the realistic limit', () => {
    // Make iron deficit huge so naive math would suggest >300 g of broccoli.
    const out = closeTheGap(
      { iron_mg: 0 },
      { iron_mg_target: 100 },
      DB,
    );
    const iron = out.find((g) => g.nutrient === 'iron');
    if (iron) {
      for (const s of iron.suggestions) {
        assert.ok(s.grams <= 200, `iron suggestion ${s.name} has unreasonable grams=${s.grams}`);
      }
    }
  });

  it('skips nutrients with no target set', () => {
    const out = closeTheGap(
      { protein_g: 0, fiber_g: 0 },
      { protein_g_target: 50 }, // no fiber target
      DB,
    );
    assert.ok(out.find((g) => g.nutrient === 'protein'));
    assert.ok(!out.find((g) => g.nutrient === 'fiber'));
  });

  it('returns [] for malformed input', () => {
    assert.deepEqual(closeTheGap(null as never, {}, DB), []);
    assert.deepEqual(closeTheGap({}, null as never, DB), []);
    assert.deepEqual(closeTheGap({}, {}, [] as never), []);
  });
});
