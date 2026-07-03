/**
 * Tests for weight trend math, meal-template expansion, and macro-split
 * resolution. Pure helpers — no IDB round-trip needed.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

(globalThis as { localStorage?: Storage }).localStorage = {
  _d: {} as Record<string, string>,
  getItem(k: string) { return (this as unknown as { _d: Record<string, string> })._d[k] ?? null; },
  setItem(k: string, v: string) { (this as unknown as { _d: Record<string, string> })._d[k] = v; },
  removeItem(k: string) { delete (this as unknown as { _d: Record<string, string> })._d[k]; },
  clear() { (this as unknown as { _d: Record<string, string> })._d = {}; },
  key() { return null; },
  get length() { return 0; },
} as unknown as Storage;

const { weeklyTrend, summarize } = await import('../public/data/weight-log.js');
const { expandTemplate, templateKcal } = await import('../public/data/meal-templates.js');
const { resolveMacroSplit, MACRO_PRESETS, dailyTargets } = await import('../public/data/profile.js');

describe('weight-log: weeklyTrend', () => {
  it('0 or 1 entries → 0 kg/week', () => {
    assert.equal(weeklyTrend([]), 0);
    assert.equal(weeklyTrend([{ date: '2026-04-01', weight_kg: 70 }]), 0);
  });

  it('linear gain: +7 kg over 7 days → +7 kg/week trend', () => {
    const entries = Array.from({ length: 7 }, (_, i) => ({
      date: new Date(Date.UTC(2026, 3, i + 1)).toISOString().slice(0, 10),
      weight_kg: 70 + i,
    }));
    const slope = weeklyTrend(entries);
    assert.ok(Math.abs(slope - 7) < 0.5, `expected ~7 kg/week, got ${slope}`);
  });

  it('linear loss → negative trend', () => {
    const entries = Array.from({ length: 14 }, (_, i) => ({
      date: new Date(Date.UTC(2026, 3, i + 1)).toISOString().slice(0, 10),
      weight_kg: 80 - 0.3 * i, // -0.3 kg/day ≈ -2.1 kg/week
    }));
    const slope = weeklyTrend(entries);
    assert.ok(slope < -1.5 && slope > -2.5, `expected ~-2.1 kg/week, got ${slope}`);
  });
});

describe('weight-log: summarize', () => {
  it('returns null on empty', () => {
    assert.equal(summarize([]), null);
  });

  it('computes latest / min / max / delta over window', () => {
    const entries = [
      { date: '2026-03-01', weight_kg: 80 },
      { date: '2026-04-01', weight_kg: 78 },
      { date: '2026-04-15', weight_kg: 77.5 },
    ];
    const s = summarize(entries, 30);
    assert.equal(s.latest_kg, 77.5);
    assert.equal(s.count, 3);
    assert.ok(s.min_kg <= 78);
    assert.ok(s.max_kg >= 77.5);
  });
});

describe('meal-templates: expand + kcal', () => {
  const tpl = {
    id: 't1',
    name: 'Petit-déj',
    meal: 'breakfast',
    items: [
      { product_name: 'Oats', grams: 50, meal: 'breakfast', kcal: 190, carbs_g: 34, protein_g: 7, fat_g: 3, sat_fat_g: 0.5, sugars_g: 0.5, salt_g: 0.005 },
      { product_name: 'Banana', grams: 120, meal: 'breakfast', kcal: 107, carbs_g: 27, protein_g: 1.3, fat_g: 0.4, sat_fat_g: 0, sugars_g: 14, salt_g: 0 },
    ],
    created_at: Date.now(),
  };

  it('templateKcal sums item kcal', () => {
    assert.equal(templateKcal(tpl), 297);
  });

  it('expandTemplate emits one entry per item with the target date', () => {
    const entries = expandTemplate(tpl, '2026-04-20');
    assert.equal(entries.length, 2);
    assert.equal(entries[0].date, '2026-04-20');
    assert.equal(entries[1].date, '2026-04-20');
    assert.equal(entries[0].product_name, 'Oats');
    assert.equal(entries[0].kcal, 190);
    assert.equal(entries[0].fromTemplate, 't1');
  });

  it('expandTemplate respects meal override', () => {
    const entries = expandTemplate(tpl, '2026-04-20', 'lunch');
    assert.equal(entries[0].meal, 'lunch');
    assert.equal(entries[1].meal, 'lunch');
  });
});

describe('profile: resolveMacroSplit', () => {
  it('defaults to balanced when undefined', () => {
    assert.deepEqual(resolveMacroSplit({}), MACRO_PRESETS.balanced);
  });

  it('returns the preset for a known key', () => {
    const keto = resolveMacroSplit({ macro_split: 'keto' });
    assert.equal(keto.carbs, 5);
    assert.equal(keto.fat, 75);
  });

  it('custom split with valid sum is honoured', () => {
    const p = { macro_split: 'custom', macro_split_custom: { carbs: 40, protein: 30, fat: 30 } };
    const r = resolveMacroSplit(p);
    assert.deepEqual({ carbs: r.carbs, protein: r.protein, fat: r.fat }, { carbs: 40, protein: 30, fat: 30 });
  });

  it('custom split with invalid sum falls back to balanced', () => {
    const p = { macro_split: 'custom', macro_split_custom: { carbs: 10, protein: 10, fat: 10 } };
    const r = resolveMacroSplit(p);
    assert.equal(r.carbs, MACRO_PRESETS.balanced.carbs);
  });
});

describe('profile: dailyTargets uses the chosen macro split', () => {
  const basePro = {
    sex: 'male', age_years: 30, height_cm: 180, weight_kg: 80, activity: 'moderate',
  };

  it('keto split produces very low carbs / high fat targets', () => {
    const t = dailyTargets({ ...basePro, macro_split: 'keto' });
    assert.ok(t.carbs_g_target < 40, `keto carbs should be <40g, got ${t.carbs_g_target}`);
    assert.ok(t.fat_g_target > 200, `keto fat should be >200g, got ${t.fat_g_target}`);
  });

  it('balanced split is roughly 50/20/30', () => {
    const t = dailyTargets({ ...basePro, macro_split: 'balanced' });
    // carbs_g_target × 4 should be ~half of tdee
    const pct = (t.carbs_g_target * 4) / t.kcal;
    assert.ok(pct > 0.45 && pct < 0.55, `balanced carbs %E expected ~0.50, got ${pct.toFixed(2)}`);
  });

  it('protein target never drops below EFSA PRI', () => {
    const t = dailyTargets({ ...basePro, macro_split: 'keto' });
    // PRI for 80kg adult = 66.4g. Keto's 20% ×E ÷ 4 on a 3000 kcal TDEE ≈ 150g, so max(PRI, 150) = 150.
    // Test with a lighter activity so the split-protein could drop below PRI.
    const lightPro = { ...basePro, activity: 'sedentary' as const, macro_split: 'low_carb' };
    const lt = dailyTargets(lightPro);
    const pri = Math.round(80 * 0.83);
    assert.ok(lt.protein_g_target >= pri, `protein target ${lt.protein_g_target} must respect PRI ${pri}`);
  });
});
