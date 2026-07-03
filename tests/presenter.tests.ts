/**
 * ============================================================================
 * PRESENTERS — TESTS
 * ============================================================================
 *
 * Pure helpers extracted from app.js so they can run under node:test without
 * a DOM shim. See public/presenters.js for the source.
 * ============================================================================
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import { computeConfidence, snapshotFromData, timeAgoBucket, defaultMealForHour, logStreakDays, parseVoiceQuickAdd, waterGoalMl, weeklyRollup, fastingStatus, buildLineChartPath, laplacianVariance, sharpnessVerdict, entriesToDailyCSV, nextOccurrenceMs, pctClass, dashboardRowsFrom, formatRecipeShare, formatTemplateShare, filterScanHistory, summarizeScanHistory, topFoods, weekOverWeekDelta } from '../public/core/presenters.js';

// ============================================================================
// computeConfidence
// ============================================================================

describe('computeConfidence', () => {
  it('openfoodfacts source is always high confidence', () => {
    const data = {
      source: 'openfoodfacts',
      warnings: ['anything'],
      product: { nutrition: {} },
    };
    assert.equal(computeConfidence(data), 'high');
  });

  it('clean LLM extraction with ≥4 filled macros is high', () => {
    const data = {
      source: 'llm',
      warnings: [],
      product: {
        nutrition: {
          energy_kcal: 150, fat_g: 8, carbs_g: 10, sugars_g: 2,
          protein_g: 5, salt_g: 0.5,
        },
      },
    };
    assert.equal(computeConfidence(data), 'high');
  });

  it('≥2 warnings downgrades to low regardless of macro count', () => {
    const data = {
      source: 'llm',
      warnings: ['w1', 'w2'],
      product: {
        nutrition: {
          energy_kcal: 150, fat_g: 8, carbs_g: 10, sugars_g: 2,
          protein_g: 5, salt_g: 0.5,
        },
      },
    };
    assert.equal(computeConfidence(data), 'low');
  });

  it('≤2 filled macros is low', () => {
    const data = {
      source: 'llm',
      warnings: [],
      product: {
        nutrition: {
          energy_kcal: 150, fat_g: 8, carbs_g: 0, sugars_g: 0,
          protein_g: 0, salt_g: 0,
        },
      },
    };
    assert.equal(computeConfidence(data), 'low');
  });

  it('3 filled macros with zero warnings is medium', () => {
    const data = {
      source: 'llm',
      warnings: [],
      product: {
        nutrition: {
          energy_kcal: 150, fat_g: 8, carbs_g: 10, sugars_g: 0,
          protein_g: 0, salt_g: 0,
        },
      },
    };
    assert.equal(computeConfidence(data), 'medium');
  });

  it('null data returns low (defensive)', () => {
    assert.equal(computeConfidence(null), 'low');
    assert.equal(computeConfidence({}), 'low');
    assert.equal(computeConfidence({ product: {} }), 'low');
  });

  it('non-numeric macro values do not count as filled', () => {
    const data = {
      source: 'llm',
      warnings: [],
      product: {
        nutrition: {
          energy_kcal: '150', fat_g: null, carbs_g: undefined, sugars_g: NaN,
          protein_g: 5, salt_g: 0.5,
        },
      },
    };
    // Only 2 truly-numeric > 0 (protein + salt) → low
    assert.equal(computeConfidence(data), 'low');
  });
});

// ============================================================================
// snapshotFromData
// ============================================================================

describe('snapshotFromData', () => {
  it('prefers audit.product_name over product.name when both exist', () => {
    const data = {
      audit: { product_name: 'Scored name', grade: 'B', score: 72 },
      product: { name: 'Raw name', ingredients: [] },
    };
    assert.equal(snapshotFromData(data).name, 'Scored name');
  });

  it('falls back to product.name when audit name is empty', () => {
    const data = {
      audit: { product_name: '', grade: 'B', score: 72 },
      product: { name: 'Raw name', ingredients: [] },
    };
    assert.equal(snapshotFromData(data).name, 'Raw name');
  });

  it('flattens ingredients to name strings', () => {
    const data = {
      audit: { product_name: 'X', grade: 'A', score: 85 },
      product: {
        name: 'X',
        ingredients: [{ name: 'flour' }, { name: 'water' }, { name: 'salt' }],
      },
    };
    assert.deepEqual(snapshotFromData(data).ingredients, ['flour', 'water', 'salt']);
  });

  it('handles missing ingredients array defensively', () => {
    const data = {
      audit: { product_name: 'X', grade: 'A', score: 85 },
      product: { name: 'X' },
    };
    assert.deepEqual(snapshotFromData(data).ingredients, []);
  });
});

// ============================================================================
// timeAgoBucket
// ============================================================================

describe('timeAgoBucket', () => {
  it('<1 minute → justNow', () => {
    assert.deepEqual(timeAgoBucket(0), { kind: 'justNow' });
    assert.deepEqual(timeAgoBucket(29_000), { kind: 'justNow' });
  });

  it('1-59 minutes → minutes bucket', () => {
    assert.deepEqual(timeAgoBucket(60_000), { kind: 'minutes', n: 1 });
    assert.deepEqual(timeAgoBucket(5 * 60_000), { kind: 'minutes', n: 5 });
    assert.deepEqual(timeAgoBucket(59 * 60_000), { kind: 'minutes', n: 59 });
  });

  it('1-23 hours → hours bucket', () => {
    assert.deepEqual(timeAgoBucket(60 * 60_000), { kind: 'hours', n: 1 });
    assert.deepEqual(timeAgoBucket(3 * 60 * 60_000), { kind: 'hours', n: 3 });
  });

  it('≥24 hours → days bucket', () => {
    assert.deepEqual(timeAgoBucket(24 * 60 * 60_000), { kind: 'days', n: 1 });
    assert.deepEqual(timeAgoBucket(5 * 24 * 60 * 60_000), { kind: 'days', n: 5 });
  });

  it('rounds to the nearest bucket (not floors)', () => {
    // 29.5 seconds rounds to 0 minutes → justNow
    assert.deepEqual(timeAgoBucket(29_500), { kind: 'justNow' });
    // 30 seconds rounds to 1 minute → minutes
    assert.deepEqual(timeAgoBucket(30_000), { kind: 'minutes', n: 1 });
  });
});

// ============================================================================
// defaultMealForHour
// ============================================================================

describe('defaultMealForHour', () => {
  it('early morning (5-9) → breakfast', () => {
    assert.equal(defaultMealForHour(5), 'breakfast');
    assert.equal(defaultMealForHour(8), 'breakfast');
    assert.equal(defaultMealForHour(9), 'breakfast');
  });

  it('midday (10-13) → lunch', () => {
    assert.equal(defaultMealForHour(10), 'lunch');
    assert.equal(defaultMealForHour(12), 'lunch');
    assert.equal(defaultMealForHour(13), 'lunch');
  });

  it('afternoon (14-17) → snack', () => {
    assert.equal(defaultMealForHour(14), 'snack');
    assert.equal(defaultMealForHour(17), 'snack');
  });

  it('evening + late night (18+, 0-4) → dinner', () => {
    assert.equal(defaultMealForHour(18), 'dinner');
    assert.equal(defaultMealForHour(21), 'dinner');
    assert.equal(defaultMealForHour(23), 'dinner');
    assert.equal(defaultMealForHour(0), 'dinner');
    assert.equal(defaultMealForHour(4), 'dinner');
  });
});

// ============================================================================
// logStreakDays
// ============================================================================

describe('logStreakDays', () => {
  const mk = (...dates: string[]) => dates.map((d) => ({ date: d }));

  it('0 when no entries', () => {
    assert.equal(logStreakDays([], '2026-04-22'), 0);
  });

  it('1 when only today is logged', () => {
    const e = mk('2026-04-22');
    assert.equal(logStreakDays(e, '2026-04-22'), 1);
  });

  it('counts consecutive days including today', () => {
    const e = mk('2026-04-20', '2026-04-21', '2026-04-22');
    assert.equal(logStreakDays(e, '2026-04-22'), 3);
  });

  it('breaks on a missing day', () => {
    const e = mk('2026-04-19', '2026-04-21', '2026-04-22');
    // today + yesterday are present, day before is skipped → streak = 2
    assert.equal(logStreakDays(e, '2026-04-22'), 2);
  });

  it('1-day grace: today empty + yesterday logged still counts', () => {
    const e = mk('2026-04-20', '2026-04-21');
    // yesterday is logged → start there, count back
    assert.equal(logStreakDays(e, '2026-04-22'), 2);
  });

  it('2-day gap → 0 (grace is only 1 day)', () => {
    const e = mk('2026-04-18', '2026-04-19', '2026-04-20');
    assert.equal(logStreakDays(e, '2026-04-22'), 0);
  });

  it('deduplicates multiple entries on the same day', () => {
    const e = mk('2026-04-22', '2026-04-22', '2026-04-22', '2026-04-21');
    assert.equal(logStreakDays(e, '2026-04-22'), 2);
  });
});

// ============================================================================
// parseVoiceQuickAdd
// ============================================================================

describe('parseVoiceQuickAdd', () => {
  it('returns empty object for empty / missing input', () => {
    assert.deepEqual(parseVoiceQuickAdd(''), {});
    assert.deepEqual(parseVoiceQuickAdd(null), {});
    assert.deepEqual(parseVoiceQuickAdd(undefined), {});
  });

  it('extracts kcal from "120 calories"', () => {
    const r = parseVoiceQuickAdd('café au lait 120 calories');
    assert.equal(r.kcal, 120);
    assert.equal(r.name, 'café au lait');
  });

  it('extracts kcal from "kcal" and "cal" variants', () => {
    assert.equal(parseVoiceQuickAdd('250 kcal').kcal, 250);
    assert.equal(parseVoiceQuickAdd('90 cal').kcal, 90);
  });

  it('extracts portion (grams + ml)', () => {
    assert.equal(parseVoiceQuickAdd('yaourt 125 g').grams, 125);
    assert.equal(parseVoiceQuickAdd('boisson 250 ml').grams, 250);
  });

  it('handles French comma decimals', () => {
    const r = parseVoiceQuickAdd('salade 8,5 g de protéines');
    assert.equal(r.protein_g, 8.5);
  });

  it('distinguishes grams from grams-of-macro', () => {
    // "15 g de protéines" should populate protein, NOT grams
    const r = parseVoiceQuickAdd('barre 15 g de protéines');
    assert.equal(r.protein_g, 15);
    assert.equal(r.grams, undefined);
  });

  it('extracts all four macros from a full phrase', () => {
    const r = parseVoiceQuickAdd(
      'pizza 350 calories 40 g de glucides 15 g de protéines 12 g de lipides',
    );
    assert.equal(r.kcal, 350);
    assert.equal(r.carbs_g, 40);
    assert.equal(r.protein_g, 15);
    assert.equal(r.fat_g, 12);
  });

  it('leaves the remaining text as the name', () => {
    const r = parseVoiceQuickAdd('banane nature 110 kcal');
    assert.equal(r.name, 'banane nature');
  });

  it('recognizes EN vocabulary too', () => {
    const r = parseVoiceQuickAdd('oatmeal 150 kcal 30 g of carbs 5 g of protein');
    assert.equal(r.kcal, 150);
    assert.equal(r.carbs_g, 30);
    assert.equal(r.protein_g, 5);
  });

  it('only overwrites parsed keys (caller spreads safely)', () => {
    const r = parseVoiceQuickAdd('just a name no numbers');
    assert.equal(r.kcal, undefined);
    assert.equal(r.protein_g, undefined);
    assert.equal(r.name, 'just a name no numbers');
  });

  it('snaps meal slot to the FR canonical key when phrase says petit-déjeuner / déjeuner / dîner', () => {
    assert.equal(parseVoiceQuickAdd('petit-déjeuner banane 110 kcal').meal, 'breakfast');
    assert.equal(parseVoiceQuickAdd('déjeuner salade poulet').meal, 'lunch');
    assert.equal(parseVoiceQuickAdd('dîner steak 500 kcal').meal, 'dinner');
    assert.equal(parseVoiceQuickAdd('goûter pomme').meal, 'snack');
  });

  it('snaps meal slot to EN canonical key', () => {
    assert.equal(parseVoiceQuickAdd('breakfast oatmeal 150 kcal').meal, 'breakfast');
    assert.equal(parseVoiceQuickAdd('lunch sandwich 400 kcal').meal, 'lunch');
    assert.equal(parseVoiceQuickAdd('dinner pasta').meal, 'dinner');
    assert.equal(parseVoiceQuickAdd('snack almonds 150 kcal').meal, 'snack');
  });

  it('strips the meal label from the extracted name', () => {
    assert.equal(parseVoiceQuickAdd('petit-déjeuner banane').name, 'banane');
    assert.equal(parseVoiceQuickAdd('breakfast oatmeal').name, 'oatmeal');
  });

  it('omits meal when no canonical keyword is present', () => {
    assert.equal(parseVoiceQuickAdd('banane 110 kcal').meal, undefined);
  });
});

// ============================================================================
// waterGoalMl — hydration goal derivation
// ============================================================================

describe('waterGoalMl', () => {
  it('defaults to 2000 ml when no profile / no weight / no sex', () => {
    assert.equal(waterGoalMl({}), 2000);
    assert.equal(waterGoalMl(null), 2000);
    assert.equal(waterGoalMl(undefined), 2000);
  });

  it('scales by weight (33 ml/kg) when available', () => {
    // 60 kg → 1980 → rounded to 2000
    assert.equal(waterGoalMl({ weight_kg: 60 }), 2000);
    // 80 kg → 2640 → rounded to 2600
    assert.equal(waterGoalMl({ weight_kg: 80 }), 2600);
    // 100 kg → 3300 → 3300
    assert.equal(waterGoalMl({ weight_kg: 100 }), 3300);
  });

  it('female baseline without weight = 1500', () => {
    assert.equal(waterGoalMl({ sex: 'female' }), 1500);
  });

  it('male baseline without weight = 2000', () => {
    assert.equal(waterGoalMl({ sex: 'male' }), 2000);
  });

  it('active profile earns +500 ml', () => {
    // 80 kg → 2640 → 2600, + 500 = 3100
    assert.equal(waterGoalMl({ weight_kg: 80, activity: 'active' }), 3100);
  });

  it('very_active profile earns +500 ml', () => {
    assert.equal(waterGoalMl({ weight_kg: 80, activity: 'very_active' }), 3100);
  });

  it('sedentary profile earns no bonus', () => {
    assert.equal(waterGoalMl({ weight_kg: 80, activity: 'sedentary' }), 2600);
  });

  it('always returns a multiple of 100 for readability', () => {
    for (const w of [55, 62, 68, 73, 77, 81, 89, 95]) {
      const g = waterGoalMl({ weight_kg: w });
      assert.equal(g % 100, 0, `goal ${g} for ${w}kg should be divisible by 100`);
    }
  });
});

// ============================================================================
// weeklyRollup — 7-day window aggregation
// ============================================================================

describe('weeklyRollup', () => {
  const mk = (date: string, kcal: number, extras: Record<string, number> = {}) => ({
    date, kcal, carbs_g: 0, protein_g: 0, fat_g: 0, sat_fat_g: 0, sugars_g: 0, salt_g: 0, ...extras,
  });

  it('returns exactly 7 days, oldest first', () => {
    const r = weeklyRollup([], '2026-04-22');
    assert.equal(r.days.length, 7);
    assert.equal(r.days[0].date, '2026-04-16');
    assert.equal(r.days[6].date, '2026-04-22');
  });

  it('zero-fills days with no entries', () => {
    const r = weeklyRollup([mk('2026-04-22', 500)], '2026-04-22');
    assert.equal(r.days[0].kcal, 0);
    assert.equal(r.days[6].kcal, 500);
  });

  it('sums kcal across same-day entries', () => {
    const r = weeklyRollup([
      mk('2026-04-22', 500), mk('2026-04-22', 200), mk('2026-04-22', 100),
    ], '2026-04-22');
    assert.equal(r.days[6].kcal, 800);
    assert.equal(r.days[6].count, 3);
  });

  it('ignores entries outside the 7-day window', () => {
    const r = weeklyRollup([mk('2026-04-15', 999), mk('2026-04-23', 999)], '2026-04-22');
    // Both out-of-window → totals stay zero
    assert.equal(r.total.kcal, 0);
  });

  it('averages only over days with entries, not the 7-day denominator', () => {
    // Logged 2 days (500 + 1500). Average should be 1000, not 2000/7.
    const r = weeklyRollup([
      mk('2026-04-21', 500),
      mk('2026-04-22', 1500),
    ], '2026-04-22');
    assert.equal(r.days_logged, 2);
    assert.equal(r.avg.kcal, 1000);
    assert.equal(r.total.kcal, 2000);
  });

  it('days_logged = 0 when no entries → avg denom clamps to 1', () => {
    const r = weeklyRollup([], '2026-04-22');
    assert.equal(r.days_logged, 0);
    assert.equal(r.avg.kcal, 0);
  });

  it('sums every macro field independently', () => {
    const e = mk('2026-04-22', 100, { protein_g: 10, carbs_g: 20, fat_g: 5, salt_g: 0.5 });
    const r = weeklyRollup([e, e], '2026-04-22');
    assert.equal(r.total.protein_g, 20);
    assert.equal(r.total.carbs_g, 40);
    assert.equal(r.total.fat_g, 10);
    assert.equal(r.total.salt_g, 1);
  });
});

// ============================================================================
// fastingStatus — intermittent-fasting countdown
// ============================================================================

describe('fastingStatus', () => {
  const hour = (n: number) => n * 3_600_000;

  it('just started: 0% elapsed, 16h remaining', () => {
    const s = fastingStatus(0, 0, 16);
    assert.equal(s.elapsed_ms, 0);
    assert.equal(s.remaining_ms, hour(16));
    assert.equal(s.pct, 0);
    assert.equal(s.complete, false);
  });

  it('halfway: 8h elapsed at 16h target → 50%', () => {
    const s = fastingStatus(0, hour(8), 16);
    assert.equal(s.pct, 50);
    assert.equal(s.remaining_ms, hour(8));
    assert.equal(s.complete, false);
  });

  it('complete at target: pct 100, remaining 0, overrun 0', () => {
    const s = fastingStatus(0, hour(16), 16);
    assert.equal(s.pct, 100);
    assert.equal(s.remaining_ms, 0);
    assert.equal(s.complete, true);
    assert.equal(s.overrun_ms, 0);
  });

  it('overrun: elapsed past target reports overrun_ms', () => {
    const s = fastingStatus(0, hour(17), 16);
    assert.equal(s.complete, true);
    assert.equal(s.overrun_ms, hour(1));
    assert.equal(s.pct, 100); // clamped
  });

  it('negative elapsed (clock-skew) clamps to 0', () => {
    const s = fastingStatus(1_000_000, 0, 16);
    assert.equal(s.elapsed_ms, 0);
    assert.equal(s.pct, 0);
  });

  it('formats the elapsed/target label as h:mm', () => {
    const s = fastingStatus(0, hour(6) + 30 * 60_000, 16);
    assert.equal(s.label, '6:30 / 16:00');
  });

  it('default target is 16 hours', () => {
    const s = fastingStatus(0, hour(16));
    assert.equal(s.complete, true);
  });

  it('custom target (e.g. 18:6) respected', () => {
    const s = fastingStatus(0, hour(16), 18);
    assert.equal(s.complete, false);
    assert.equal(s.remaining_ms, hour(2));
  });
});

// ============================================================================
// buildLineChartPath — SVG path builder for progress charts
// ============================================================================

describe('buildLineChartPath', () => {
  it('empty input → no path, zero min/max, empty points', () => {
    const r = buildLineChartPath([]);
    assert.equal(r.path_d, '');
    assert.equal(r.min, 0);
    assert.equal(r.max, 0);
    assert.deepEqual(r.points, []);
  });

  it('single value → one M command, min == max', () => {
    const r = buildLineChartPath([80]);
    assert.match(r.path_d, /^M /);
    assert.equal(r.min, 80);
    assert.equal(r.max, 80);
    assert.equal(r.points.length, 1);
  });

  it('ascending series → ascending x, descending y (SVG coord)', () => {
    const r = buildLineChartPath([70, 72, 75, 78], { width: 300, height: 100, padding: 0 });
    // x monotonically increasing
    for (let i = 1; i < r.points.length; i++) {
      assert.ok(r.points[i].x > r.points[i - 1].x);
    }
    // higher data value → lower y coordinate (chart top = low y)
    for (let i = 1; i < r.points.length; i++) {
      assert.ok(r.points[i].y < r.points[i - 1].y, `y should decrease as value grows: ${JSON.stringify(r.points)}`);
    }
  });

  it('null values split the path with a new M command', () => {
    const r = buildLineChartPath([70, null, 75, 78]);
    // Expect two M commands (one before 70, one before 75)
    const mCount = (r.path_d.match(/M /g) || []).length;
    assert.equal(mCount, 2);
    assert.equal(r.points.length, 3);
  });

  it('all-equal values → flat line at mid-height (range=1 guard)', () => {
    const r = buildLineChartPath([72, 72, 72], { width: 300, height: 100, padding: 0 });
    // All y the same, positioned at the top (because (v - min)/range = 0)
    const ys = new Set(r.points.map((p) => p.y));
    assert.equal(ys.size, 1);
  });

  it('respects width / height / padding options', () => {
    const r = buildLineChartPath([1, 2, 3], { width: 100, height: 50, padding: 5 });
    assert.ok(r.points[0].x >= 5 && r.points[0].x <= 95);
    assert.ok(r.points[0].y >= 5 && r.points[0].y <= 45);
    assert.ok(r.points[2].x >= 5 && r.points[2].x <= 95);
  });

  it('non-numeric entries are treated as gaps', () => {
    const r = buildLineChartPath([1, 'bad' as unknown as number, 3, NaN, 5]);
    // 3 numeric points (1, 3, 5)
    assert.equal(r.points.length, 3);
  });
});

// ============================================================================
// laplacianVariance + sharpnessVerdict — image sharpness heuristic
// ============================================================================

describe('laplacianVariance', () => {
  // Tiny test fixtures, width × height flat Uint8-style arrays.
  const flat = (w: number, h: number, v: number) => {
    const a = new Array(w * h).fill(v);
    return { a, w };
  };

  it('returns 0 for a uniform grey image (no edges)', () => {
    const { a, w } = flat(8, 8, 128);
    assert.equal(laplacianVariance(a, w), 0);
  });

  it('returns 0 for empty / missing input', () => {
    assert.equal(laplacianVariance([], 8), 0);
    assert.equal(laplacianVariance(null as unknown as number[], 8), 0);
    assert.equal(laplacianVariance([1, 2, 3], 0), 0);
  });

  it('returns a high value for a checkerboard (lots of edges)', () => {
    const w = 8;
    const a: number[] = [];
    for (let y = 0; y < 8; y++) {
      for (let x = 0; x < 8; x++) {
        a.push(((x + y) & 1) ? 255 : 0);
      }
    }
    const v = laplacianVariance(a, w);
    assert.ok(v > 1000, `checkerboard variance ${v} should be very high`);
  });

  it('returns a moderate value for a smooth gradient', () => {
    const w = 8, h = 8;
    const a: number[] = [];
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) a.push((x + y) * 16);
    const v = laplacianVariance(a, w);
    // Gradient has no abrupt edges → low but >0
    assert.ok(v < 100, `gradient variance ${v} should be low`);
  });

  it('handles height < 3 → 0 (too small for 3×3 filter)', () => {
    assert.equal(laplacianVariance([1, 2, 3, 4], 2), 0);
  });
});

describe('sharpnessVerdict', () => {
  it('< 40 → blurry', () => {
    assert.equal(sharpnessVerdict(0), 'blurry');
    assert.equal(sharpnessVerdict(39.9), 'blurry');
  });
  it('40 ≤ v < 120 → borderline', () => {
    assert.equal(sharpnessVerdict(40), 'borderline');
    assert.equal(sharpnessVerdict(100), 'borderline');
    assert.equal(sharpnessVerdict(119.9), 'borderline');
  });
  it('≥ 120 → sharp', () => {
    assert.equal(sharpnessVerdict(120), 'sharp');
    assert.equal(sharpnessVerdict(5000), 'sharp');
  });
});

// ============================================================================
// entriesToDailyCSV
// ============================================================================

describe('entriesToDailyCSV', () => {
  const mk = (date: string, macros: Record<string, number> = {}) => ({
    date, kcal: 0, carbs_g: 0, protein_g: 0, fat_g: 0,
    sat_fat_g: 0, sugars_g: 0, salt_g: 0, ...macros,
  });

  it('empty → header only', () => {
    const csv = entriesToDailyCSV([]);
    assert.ok(csv.startsWith('﻿"date","entries"'));
    // header line + 0 data rows + trailing CRLF
    assert.equal(csv.split('\r\n').length, 2);
  });

  it('one entry → one data row', () => {
    const csv = entriesToDailyCSV([mk('2026-04-22', { kcal: 500, protein_g: 20 })]);
    const lines = csv.split('\r\n').filter((l) => l);
    assert.equal(lines.length, 2);
    assert.match(lines[1], /"2026-04-22","1","500"/);
  });

  it('same date, multiple entries → one row with summed totals', () => {
    const csv = entriesToDailyCSV([
      mk('2026-04-22', { kcal: 300 }),
      mk('2026-04-22', { kcal: 450 }),
    ]);
    const lines = csv.split('\r\n').filter((l) => l);
    assert.equal(lines.length, 2);
    assert.match(lines[1], /"2026-04-22","2","750"/);
  });

  it('sorts rows by date ascending', () => {
    const csv = entriesToDailyCSV([
      mk('2026-04-23', { kcal: 100 }),
      mk('2026-04-21', { kcal: 200 }),
      mk('2026-04-22', { kcal: 300 }),
    ]);
    const lines = csv.split('\r\n').filter((l) => l);
    assert.match(lines[1], /2026-04-21/);
    assert.match(lines[2], /2026-04-22/);
    assert.match(lines[3], /2026-04-23/);
  });

  it('starts with UTF-8 BOM for Excel', () => {
    const csv = entriesToDailyCSV([mk('2026-04-22')]);
    assert.equal(csv.charCodeAt(0), 0xFEFF);
  });

  it('uses CRLF line terminators', () => {
    const csv = entriesToDailyCSV([mk('2026-04-22')]);
    assert.match(csv, /\r\n/);
  });

  it('ignores entries missing the date field', () => {
    const csv = entriesToDailyCSV([{ kcal: 100 } as unknown as never, mk('2026-04-22')]);
    const lines = csv.split('\r\n').filter((l) => l);
    assert.equal(lines.length, 2); // header + 1
  });
});

// ============================================================================
// nextOccurrenceMs — meal-reminder scheduler helper
// ============================================================================

describe('nextOccurrenceMs', () => {
  // Use a fixed "now" at 2026-04-22 10:00 local time for determinism.
  const now = new Date(2026, 3, 22, 10, 0, 0).getTime();

  it('returns today when target is later today', () => {
    const target = nextOccurrenceMs('19:30', now);
    const d = new Date(target!);
    assert.equal(d.getDate(), 22);
    assert.equal(d.getHours(), 19);
    assert.equal(d.getMinutes(), 30);
  });

  it('rolls to tomorrow when target has already passed', () => {
    const target = nextOccurrenceMs('07:30', now); // 10:00 now, 07:30 is past
    const d = new Date(target!);
    assert.equal(d.getDate(), 23);
    assert.equal(d.getHours(), 7);
    assert.equal(d.getMinutes(), 30);
  });

  it('returns null for malformed input', () => {
    assert.equal(nextOccurrenceMs('bad' as unknown as string, now), null);
    assert.equal(nextOccurrenceMs('25:00', now), null); // regex doesn't match 3 digits
    assert.equal(nextOccurrenceMs('', now), null);
    assert.equal(nextOccurrenceMs(null as unknown as string, now), null);
  });

  it('clamps out-of-range hh/mm silently', () => {
    // '23:75' parses; minutes clamp to 59
    const t = nextOccurrenceMs('23:75', now);
    const d = new Date(t!);
    assert.equal(d.getMinutes(), 59);
  });

  it('target exactly at now rolls to tomorrow', () => {
    const t = nextOccurrenceMs('10:00', now);
    const d = new Date(t!);
    assert.equal(d.getDate(), 23);
  });
});

// ============================================================================
// pctClass (R11.1)
// ============================================================================

describe('pctClass', () => {
  it('returns "ok" below 80%', () => {
    assert.equal(pctClass(0), 'ok');
    assert.equal(pctClass(50), 'ok');
    assert.equal(pctClass(79.9), 'ok');
  });

  it('returns "near" from 80 to 99', () => {
    assert.equal(pctClass(80), 'near');
    assert.equal(pctClass(99), 'near');
  });

  it('returns "over" at 100+', () => {
    assert.equal(pctClass(100), 'over');
    assert.equal(pctClass(200), 'over');
  });
});

// ============================================================================
// dashboardRowsFrom (R11.2)
// ============================================================================

describe('dashboardRowsFrom', () => {
  const totals = {
    kcal: 1800, carbs_g: 180, fiber_g: 20, protein_g: 90, fat_g: 60,
    sat_fat_g: 10, sugars_g: 30, salt_g: 4, count: 5,
  };
  const targets = {
    kcal: 2000, carbs_g_target: 225, fiber_g_target: 25,
    protein_g_target: 100, fat_g_target: 70, sat_fat_g_max: 20,
    free_sugars_g_max: 50, salt_g_max: 5,
  };

  it('always emits the 8 macro rows in stable order', () => {
    const rows = dashboardRowsFrom(totals, targets);
    assert.equal(rows.length, 8);
    assert.deepEqual(rows.map((r: { key: string }) => r.key), [
      'dashKcal', 'dashCarbs', 'dashFiber', 'dashProtein',
      'dashFat', 'dashSatFat', 'dashSugars', 'dashSalt',
    ]);
  });

  it('adds iron row only when totals.iron_mg > 0', () => {
    const withIron = dashboardRowsFrom({ ...totals, iron_mg: 10 }, targets);
    assert.ok(withIron.some((r: { key: string }) => r.key === 'dashIron'));
    const withoutIron = dashboardRowsFrom(totals, targets);
    assert.ok(!withoutIron.some((r: { key: string }) => r.key === 'dashIron'));
  });

  it('safe with null / undefined totals', () => {
    const rows = dashboardRowsFrom(undefined, undefined);
    assert.equal(rows.length, 8);
    for (const r of rows) assert.equal(r.value, 0);
  });
});

// ============================================================================
// formatRecipeShare (R11.7)
// ============================================================================

describe('formatRecipeShare', () => {
  const recipe = {
    id: 'r1',
    name: 'Pâtes pesto',
    servings: 2,
    components: [
      { product_name: 'Pâtes', grams: 200, kcal: 700, protein_g: 25, carbs_g: 140, fat_g: 3 },
      { product_name: 'Pesto', grams: 50, kcal: 260, protein_g: 6, carbs_g: 3, fat_g: 24 },
    ],
  };

  it('returns "" when components is empty', () => {
    assert.equal(formatRecipeShare({ name: 'x', servings: 1, components: [] }), '');
    assert.equal(formatRecipeShare(null), '');
    assert.equal(formatRecipeShare(undefined), '');
  });

  it('divides totals by servings', () => {
    const out = formatRecipeShare(recipe, { lang: 'en' });
    // 960 total / 2 servings = 480 kcal per serving
    assert.ok(out.includes('480 kcal'), out);
    // Per-serving protein: (25+6)/2 = 15.5 → rounds to 16
    assert.ok(out.includes('16 g'), out);
  });

  it('locale-aware header', () => {
    const fr = formatRecipeShare(recipe, { lang: 'fr' });
    const en = formatRecipeShare(recipe, { lang: 'en' });
    assert.ok(fr.startsWith('🍽 Recette'));
    assert.ok(en.startsWith('🍽 Recipe'));
  });

  it('ends with Scan\'eat signature', () => {
    const out = formatRecipeShare(recipe);
    assert.ok(out.endsWith('— Scan\'eat'));
  });

  it('each component becomes a bullet line', () => {
    const out = formatRecipeShare(recipe, { lang: 'fr' });
    assert.ok(out.includes('• Pâtes — 200 g · 700 kcal'));
    assert.ok(out.includes('• Pesto — 50 g · 260 kcal'));
  });

  // R14.3: empty name + legacy 'Sans nom' sentinel both render
  // locale-aware. Ensures pre-R14 IDB rows don't leak French into
  // English share output.
  it('empty name renders as Untitled in EN, Sans nom in FR', () => {
    const r2 = { ...recipe, name: '' };
    assert.ok(formatRecipeShare(r2, { lang: 'en' }).includes('Untitled'));
    assert.ok(formatRecipeShare(r2, { lang: 'fr' }).includes('Sans nom'));
  });

  it('legacy "Sans nom" name in EN locale renders as Untitled', () => {
    const legacy = { ...recipe, name: 'Sans nom' };
    assert.ok(formatRecipeShare(legacy, { lang: 'en' }).includes('Untitled'));
    assert.ok(!formatRecipeShare(legacy, { lang: 'en' }).includes('Sans nom'));
  });
});

// ============================================================================
// formatTemplateShare (R12.1)
// ============================================================================

describe('formatTemplateShare', () => {
  const tpl = {
    id: 't1',
    name: 'Petit-déj rapide',
    meal: 'breakfast',
    items: [
      { product_name: 'Yaourt', grams: 125, kcal: 90, protein_g: 6, carbs_g: 12, fat_g: 0 },
      { product_name: 'Banane', grams: 120, kcal: 105, protein_g: 1, carbs_g: 27, fat_g: 0 },
    ],
  };

  it('returns "" on empty / missing items', () => {
    assert.equal(formatTemplateShare({ name: 'x', items: [] }), '');
    assert.equal(formatTemplateShare(null), '');
    assert.equal(formatTemplateShare(undefined), '');
  });

  it('sums per-item kcal + macros without per-serving division', () => {
    const out = formatTemplateShare(tpl, { lang: 'en' });
    // 90 + 105 = 195 kcal total (templates do not divide by servings)
    assert.ok(out.includes('195 kcal'), out);
    // 6+1 = 7 g protein
    assert.ok(out.includes('P 7 g'), out);
  });

  it('locale-aware header', () => {
    const fr = formatTemplateShare(tpl, { lang: 'fr' });
    const en = formatTemplateShare(tpl, { lang: 'en' });
    assert.ok(fr.startsWith('📋 Repas'));
    assert.ok(en.startsWith('📋 Meal'));
  });

  it('shows item count in the second line', () => {
    const fr = formatTemplateShare(tpl, { lang: 'fr' });
    const en = formatTemplateShare(tpl, { lang: 'en' });
    assert.ok(fr.includes('2 élément(s)'));
    assert.ok(en.includes('2 item(s)'));
  });

  it('ends with Scan\'eat signature', () => {
    assert.ok(formatTemplateShare(tpl).endsWith('— Scan\'eat'));
  });

  // R14.3: empty + legacy-sentinel handling for templates.
  it('empty name renders locale-aware', () => {
    const t2 = { ...tpl, name: '' };
    assert.ok(formatTemplateShare(t2, { lang: 'en' }).includes('Untitled'));
    assert.ok(formatTemplateShare(t2, { lang: 'fr' }).includes('Sans nom'));
  });

  it('legacy "Sans nom" name renders as Untitled in EN', () => {
    const legacy = { ...tpl, name: 'Sans nom' };
    assert.ok(formatTemplateShare(legacy, { lang: 'en' }).includes('Untitled'));
  });
});

// ============================================================================
// filterScanHistory (R13.1)
// ============================================================================

describe('filterScanHistory', () => {
  const items = [
    { id: '1', name: 'Yaourt nature',  grade: 'A' },
    { id: '2', name: 'Coca-Cola',      grade: 'F' },
    { id: '3', name: 'Pain complet',   grade: 'B' },
    { id: '4', name: 'YAOURT FRUITS',  grade: 'C' },
    { id: '5', name: null,             grade: 'A' },
  ];

  it('returns the full list when no filter is set', () => {
    assert.equal(filterScanHistory(items).length, 5);
  });

  it('filters by case-insensitive substring on name', () => {
    const out = filterScanHistory(items, { query: 'yaourt' });
    assert.equal(out.length, 2);
    assert.deepEqual(out.map((i: { id: string }) => i.id), ['1', '4']);
  });

  it('filters by exact grade', () => {
    const out = filterScanHistory(items, { gradeFilter: 'A' });
    assert.equal(out.length, 2);
    assert.deepEqual(out.map((i: { id: string }) => i.id), ['1', '5']);
  });

  it('combines query + grade', () => {
    const out = filterScanHistory(items, { query: 'yaourt', gradeFilter: 'A' });
    assert.equal(out.length, 1);
    assert.equal(out[0].id, '1');
  });

  it('treats missing name as non-matching for queries', () => {
    const out = filterScanHistory(items, { query: 'anything' });
    assert.ok(!out.some((i: { id: string }) => i.id === '5'));
  });

  it('safe with non-array input', () => {
    assert.deepEqual(filterScanHistory(null), []);
    assert.deepEqual(filterScanHistory(undefined), []);
  });
});

// ============================================================================
// summarizeScanHistory (R13.6)
// ============================================================================

describe('summarizeScanHistory', () => {
  it('counts items by grade with all six buckets initialised', () => {
    const out = summarizeScanHistory([
      { grade: 'A' }, { grade: 'A' }, { grade: 'B' },
      { grade: 'A+' }, { grade: 'F' },
    ]);
    assert.equal(out.total, 5);
    assert.deepEqual(out.byGrade, { 'A+': 1, A: 2, B: 1, C: 0, D: 0, F: 1 });
  });

  it('totals everything but ignores unknown grades in the buckets', () => {
    const out = summarizeScanHistory([
      { grade: 'X' }, { grade: 'A' }, { grade: undefined },
    ]);
    assert.equal(out.total, 3);
    assert.equal(out.byGrade.A, 1);
    assert.equal(out.byGrade['A+'], 0);
  });

  it('safe with non-array input', () => {
    const out = summarizeScanHistory(null);
    assert.equal(out.total, 0);
    assert.deepEqual(out.byGrade, { 'A+': 0, A: 0, B: 0, C: 0, D: 0, F: 0 });
  });
});

// ============================================================================
// topFoods (gap fix #7 + #12)
// ============================================================================

describe('topFoods', () => {
  const now = 1_714_000_000_000;
  const e = (name: string, ts: number, kcal: number) => ({
    id: `e${ts}`, product_name: name, timestamp: ts, kcal, date: '2026-04-23',
  });

  it('counts + sorts by frequency, then recency tiebreaker', () => {
    const out = topFoods([
      e('Yaourt',  now - 1000, 100),
      e('Yaourt',  now - 500,  100),
      e('Yaourt',  now,        100),
      e('Banane',  now - 200,  100),
      e('Banane',  now - 100,  100),
      e('Pomme',   now,        80),
    ], { limit: 3 });
    assert.equal(out.length, 3);
    assert.equal(out[0].name, 'Yaourt');
    assert.equal(out[0].count, 3);
    assert.equal(out[1].name, 'Banane');
    assert.equal(out[1].count, 2);
    assert.equal(out[2].name, 'Pomme');
  });

  it('computes avg_kcal across occurrences', () => {
    const out = topFoods([
      e('Yaourt', now, 100),
      e('Yaourt', now, 120),
      e('Yaourt', now, 110),
    ]);
    assert.equal(out[0].avg_kcal, 110);
  });

  it('honours sinceDays cutoff', () => {
    const out = topFoods([
      e('Ancien', now - 90 * 86_400_000, 100),
      e('Récent', now, 100),
    ], { sinceDays: 30, now });
    assert.equal(out.length, 1);
    assert.equal(out[0].name, 'Récent');
  });

  it('ignores empty / "—" names', () => {
    const out = topFoods([
      e('',   now, 100),
      e('—',  now, 100),
      e('ok', now, 100),
    ]);
    assert.equal(out.length, 1);
    assert.equal(out[0].name, 'ok');
  });

  it('safe with non-array input', () => {
    assert.deepEqual(topFoods(null), []);
    assert.deepEqual(topFoods(undefined), []);
  });

  it('limit=0 returns an empty list', () => {
    const out = topFoods([e('a', now, 1)], { limit: 0 });
    assert.deepEqual(out, []);
  });
});

// ============================================================================
// weekOverWeekDelta (gap fix #11)
// ============================================================================

describe('weekOverWeekDelta', () => {
  const mk = (kcal: number, prot: number, carb: number, fat: number, daysLogged: number) => ({
    avg: { kcal, protein_g: prot, carbs_g: carb, fat_g: fat },
    days_logged: daysLogged,
  });

  it('computes percent change per macro', () => {
    const out = weekOverWeekDelta(mk(2200, 120, 200, 80, 7), mk(2000, 100, 200, 80, 7));
    assert.equal(out.kcal.pct, 10);
    assert.equal(out.protein.pct, 20);
    assert.equal(out.carbs.pct, 0);
    assert.equal(out.fat.pct, 0);
  });

  it('returns null pct when prior is zero', () => {
    const out = weekOverWeekDelta(mk(2000, 100, 200, 80, 5), mk(0, 0, 0, 0, 0));
    assert.equal(out.kcal.pct, null);
    assert.equal(out.protein.pct, null);
  });

  it('reports rounded cur/prev alongside pct', () => {
    const out = weekOverWeekDelta(mk(2200.6, 0, 0, 0, 7), mk(2000.4, 0, 0, 0, 7));
    assert.equal(out.kcal.cur, 2201);
    assert.equal(out.kcal.prev, 2000);
  });

  it('is safe with missing / malformed inputs', () => {
    const out = weekOverWeekDelta(null, undefined);
    assert.equal(out.kcal.cur, 0);
    assert.equal(out.kcal.prev, 0);
    assert.equal(out.kcal.pct, null);
    assert.equal(out.days_logged.cur, 0);
  });
});
