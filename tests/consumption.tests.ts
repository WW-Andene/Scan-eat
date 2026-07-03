/**
 * Consumption arithmetic tests — no IDB needed.
 *
 * Verifies the per-100 g → per-portion conversion and the daily aggregation,
 * which are pure functions exported from consumption.js.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

(globalThis as { localStorage?: Storage }).localStorage = {
  _d: {} as Record<string, string>,
  getItem() { return null; },
  setItem() {},
  removeItem() {},
  clear() {},
  key() { return null; },
  get length() { return 0; },
} as unknown as Storage;

// Stub crypto.randomUUID for deterministic tests.
if (!('crypto' in globalThis) || !('randomUUID' in (globalThis as { crypto?: Crypto }).crypto!)) {
  (globalThis as { crypto?: Crypto }).crypto = {
    ...((globalThis as { crypto?: Crypto }).crypto ?? {}),
    randomUUID: () => 'test-uuid' as `${string}-${string}-${string}-${string}-${string}`,
  } as Crypto;
}

const { buildEntry, buildQuickAdd, sumTotals, groupByMeal, todayISO, MEALS } = await import('../public/data/consumption.js');

type P = {
  name: string; category: string; nutrition: Record<string, number>; weight_g?: number;
};

function product(over: Partial<P['nutrition']> = {}): P {
  return {
    name: 'Test Yogurt',
    category: 'yogurt',
    nutrition: {
      energy_kcal: 60,
      fat_g: 0.2,
      saturated_fat_g: 0.1,
      carbs_g: 8,
      sugars_g: 8,
      added_sugars_g: 6,
      fiber_g: 0,
      protein_g: 10,
      salt_g: 0.12,
      ...over,
    },
  };
}

describe('buildEntry — per-100 g → per-portion math', () => {
  it('scales linearly by grams/100 for each macro', () => {
    const e = buildEntry(product(), 200);
    assert.equal(e.grams, 200);
    assert.equal(e.kcal, 120); // 60 * 2
    assert.equal(e.sat_fat_g, 0.2);
    assert.equal(e.sugars_g, 12); // added_sugars preferred: 6 * 2
    assert.equal(e.salt_g, 0.24);
    assert.equal(e.protein_g, 20);
    assert.equal(e.carbs_g, 16); // 8 * 2
    assert.equal(e.fat_g, 0.4);  // 0.2 * 2
  });

  it('meal defaults to "snack" and validates against MEALS list', () => {
    const e1 = buildEntry(product(), 100);
    assert.equal(e1.meal, 'snack');
    const e2 = buildEntry(product(), 100, { meal: 'breakfast' });
    assert.equal(e2.meal, 'breakfast');
    const e3 = buildEntry(product(), 100, { meal: 'invalid' });
    assert.equal(e3.meal, 'snack', 'unknown meal falls back to snack');
  });

  it('half portion', () => {
    const e = buildEntry(product(), 50);
    assert.equal(e.kcal, 30);
    assert.equal(e.protein_g, 5);
  });

  it('falls back to total sugars when added_sugars_g is missing', () => {
    const p = product();
    delete (p.nutrition as Record<string, unknown>).added_sugars_g;
    const e = buildEntry(p, 100);
    assert.equal(e.sugars_g, 8);
  });

  it('treats missing nutrition values as zero (no NaN)', () => {
    const e = buildEntry({ name: 'X', category: 'other', nutrition: { energy_kcal: 100 } as never }, 100);
    assert.equal(e.kcal, 100);
    assert.equal(e.sat_fat_g, 0);
    assert.equal(e.protein_g, 0);
  });

  it('zero grams gives zero everything', () => {
    const e = buildEntry(product(), 0);
    assert.equal(e.kcal, 0);
    assert.equal(e.protein_g, 0);
  });

  it('date is ISO YYYY-MM-DD', () => {
    const e = buildEntry(product(), 100);
    assert.match(e.date, /^\d{4}-\d{2}-\d{2}$/);
  });

  it('negative grams coerces to 0', () => {
    const e = buildEntry(product(), -50);
    assert.equal(e.grams, 0);
    assert.equal(e.kcal, 0);
  });

  it('non-numeric grams coerces to 0', () => {
    const e = buildEntry(product(), 'abc' as unknown as number);
    assert.equal(e.grams, 0);
  });
});

describe('sumTotals — daily aggregation', () => {
  it('sums every macro across entries', () => {
    const e1 = buildEntry(product(), 100);
    const e2 = buildEntry(product({ energy_kcal: 300, saturated_fat_g: 5 }), 50);
    const total = sumTotals([e1, e2]);
    assert.equal(total.count, 2);
    assert.equal(total.kcal, 60 + 150); // 210
    assert.equal(total.sat_fat_g, 0.1 + 2.5); // 2.6
  });

  it('empty list → zero totals for every macro + micro field', () => {
    const out = sumTotals([]);
    assert.equal(out.kcal, 0);
    assert.equal(out.protein_g, 0);
    assert.equal(out.iron_mg, 0);
    assert.equal(out.calcium_mg, 0);
    // Full micronutrient panel (feature 2): every declared field
    // zeroes when there are no entries.
    assert.equal(out.magnesium_mg, 0);
    assert.equal(out.potassium_mg, 0);
    assert.equal(out.zinc_mg, 0);
    assert.equal(out.sodium_mg, 0);
    assert.equal(out.vit_a_ug, 0);
    assert.equal(out.vit_c_mg, 0);
    assert.equal(out.vit_d_ug, 0);
    assert.equal(out.vit_e_mg, 0);
    assert.equal(out.vit_k_ug, 0);
    assert.equal(out.b1_mg, 0);
    assert.equal(out.b2_mg, 0);
    assert.equal(out.b3_mg, 0);
    assert.equal(out.b6_mg, 0);
    assert.equal(out.b9_ug, 0);
    assert.equal(out.b12_ug, 0);
    assert.equal(out.polyunsaturated_fat_g, 0);
    assert.equal(out.monounsaturated_fat_g, 0);
    assert.equal(out.omega_3_g, 0);
    assert.equal(out.omega_6_g, 0);
    assert.equal(out.cholesterol_mg, 0);
    assert.equal(out.count, 0);
  });

  it('micros (iron, calcium, vit D, B12) scale + sum', () => {
    const e1 = buildEntry(product({ iron_mg: 3, calcium_mg: 200, vit_d_ug: 1, b12_ug: 0.5 }), 100);
    const e2 = buildEntry(product({ iron_mg: 2, calcium_mg: 150, vit_d_ug: 2, b12_ug: 1 }), 50);
    assert.equal(e1.iron_mg, 3);     // × 1
    assert.equal(e2.calcium_mg, 75); // 150 × 0.5
    const total = sumTotals([e1, e2]);
    assert.equal(total.iron_mg, 4);        // 3 + 1
    assert.equal(total.calcium_mg, 275);   // 200 + 75
    assert.equal(total.vit_d_ug, 2);       // 1 + 1
    assert.equal(total.b12_ug, 1);         // 0.5 + 0.5
  });

  it('buildQuickAdd accepts iron_mg + calcium_mg + vit_d_ug + b12_ug', () => {
    const q = buildQuickAdd({ name: 'skyr fortifié', kcal: 60, iron_mg: 2.4, calcium_mg: 120, vit_d_ug: 1.5, b12_ug: 0.3 });
    assert.equal(q.iron_mg, 2.4);
    assert.equal(q.calcium_mg, 120);
    assert.equal(q.vit_d_ug, 1.5);
    assert.equal(q.b12_ug, 0.3);
  });

  it('rounds the final sum to the consumption module precision', () => {
    // Each entry is rounded to 3 decimals on build, then summed and re-rounded.
    // 0.1234 → round3 → 0.123 per entry; 3 entries → 0.369.
    const e = buildEntry(product({ salt_g: 0.1234 }), 100);
    const total = sumTotals([e, e, e]);
    assert.equal(total.salt_g, 0.369);
  });

  it('fiber_g scales linearly and sums like the other macros', () => {
    const e1 = buildEntry(product({ fiber_g: 3 }), 100);   // 3 g
    const e2 = buildEntry(product({ fiber_g: 2 }), 150);   // 3 g
    assert.equal(e1.fiber_g, 3);
    assert.equal(e2.fiber_g, 3);
    const total = sumTotals([e1, e2]);
    assert.equal(total.fiber_g, 6);
  });

  it('buildQuickAdd accepts and rounds fiber_g', () => {
    const q = buildQuickAdd({ name: 'lentilles', kcal: 100, fiber_g: 8.256 });
    assert.equal(q.fiber_g, 8.26);
  });
});

describe('buildQuickAdd', () => {
  it('stores user-entered totals directly (no per-100 g scaling)', () => {
    const e = buildQuickAdd({ name: 'Restaurant meal', meal: 'dinner', kcal: 820, carbs_g: 95, protein_g: 35, fat_g: 30, salt_g: 3.2 });
    assert.equal(e.kcal, 820);
    assert.equal(e.carbs_g, 95);
    assert.equal(e.fat_g, 30);
    assert.equal(e.salt_g, 3.2);
    assert.equal(e.meal, 'dinner');
    assert.equal(e.quickAdd, true);
    assert.equal(e.grams, 0);
  });

  it('defaults missing macros to 0', () => {
    const e = buildQuickAdd({ kcal: 200 });
    assert.equal(e.carbs_g, 0);
    assert.equal(e.protein_g, 0);
    assert.equal(e.fat_g, 0);
    assert.equal(e.meal, 'snack');
  });

  it('uses fallback name when none provided', () => {
    const e = buildQuickAdd({ kcal: 100 });
    assert.equal(e.product_name, 'Quick Add');
  });
});

describe('groupByMeal', () => {
  it('splits entries across the 4 meal buckets', () => {
    const entries = [
      buildEntry(product(), 100, { meal: 'breakfast' }),
      buildEntry(product(), 100, { meal: 'lunch' }),
      buildEntry(product(), 100, { meal: 'dinner' }),
      buildEntry(product(), 50, { meal: 'snack' }),
      buildEntry(product(), 100, { meal: 'snack' }),
    ];
    const g = groupByMeal(entries);
    assert.equal(g.breakfast.entries.length, 1);
    assert.equal(g.lunch.entries.length, 1);
    assert.equal(g.dinner.entries.length, 1);
    assert.equal(g.snack.entries.length, 2);
  });

  it('per-meal totals sum only that meal\'s entries', () => {
    const entries = [
      buildEntry(product(), 100, { meal: 'breakfast' }), // 60 kcal
      buildEntry(product(), 200, { meal: 'breakfast' }), // 120 kcal
      buildEntry(product(), 50, { meal: 'lunch' }),     // 30 kcal
    ];
    const g = groupByMeal(entries);
    assert.equal(g.breakfast.totals.kcal, 180);
    assert.equal(g.lunch.totals.kcal, 30);
    assert.equal(g.dinner.totals.kcal, 0);
  });

  it('MEALS list exports the 4 canonical meal keys in order', () => {
    assert.deepEqual(MEALS, ['breakfast', 'lunch', 'dinner', 'snack']);
  });
});

describe('todayISO', () => {
  it('returns a valid YYYY-MM-DD date string', () => {
    assert.match(todayISO(), /^\d{4}-\d{2}-\d{2}$/);
  });

  it('accepts a `now` epoch-ms argument for deterministic bucketing', () => {
    // 2026-04-22 at 12:00 UTC — noon UTC is the same local date in every
    // timezone offset between UTC-12 and UTC+12, so the assertion is
    // TZ-agnostic.
    const noon = Date.UTC(2026, 3, 22, 12, 0, 0);
    assert.equal(todayISO(noon), '2026-04-22');
  });

  it('returns the LOCAL calendar day (not UTC)', () => {
    // This test documents the bugfix: prior to R2.10, todayISO() used
    // toISOString() which returns the UTC day. The new impl uses
    // Intl.DateTimeFormat('en-CA') which reports the runtime-local day.
    // We verify that at noon UTC (a time every timezone between UTC-12
    // and UTC+12 reports as the same calendar day), the output matches
    // the locally-observed calendar day.
    const noon = new Date(Date.UTC(2026, 3, 22, 12, 0, 0));
    const localDay = noon.toLocaleDateString('en-CA'); // YYYY-MM-DD in local TZ
    assert.equal(todayISO(noon.getTime()), localDay);
  });
});
