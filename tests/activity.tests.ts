/**
 * Activity / exercise log — pure-function tests.
 *
 * Covers MET estimation, entry building, and sumBurned. IDB glue is not
 * exercised here; see manual testing for persistence.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import { MET_TABLE, ACTIVITY_TYPES, estimateKcalBurned, buildActivityEntry, sumBurned } from '../public/data/activity.js';

describe('MET table', () => {
  it('contains the expected activity types', () => {
    for (const key of ['walking_brisk', 'running', 'cycling', 'swimming', 'strength', 'yoga', 'hiit', 'other']) {
      assert.ok(Number.isFinite(MET_TABLE[key]), `${key} missing`);
      assert.ok(MET_TABLE[key] > 0);
    }
  });
  it('ACTIVITY_TYPES mirrors MET_TABLE keys', () => {
    assert.deepEqual(ACTIVITY_TYPES.sort(), Object.keys(MET_TABLE).sort());
  });
});

describe('estimateKcalBurned', () => {
  it('applies MET × kg × hours', () => {
    // running 30 min at 70 kg → 9.8 × 70 × 0.5 = 343
    assert.equal(estimateKcalBurned('running', 30, 70), 343);
  });
  it('walking brisk 60 min @ 70 kg ~ 301', () => {
    // 4.3 × 70 × 1 = 301
    assert.equal(estimateKcalBurned('walking_brisk', 60, 70), 301);
  });
  it('falls back to "other" MET for unknown type', () => {
    // other = 4.0; 30 min @ 80 kg → 4 × 80 × 0.5 = 160
    assert.equal(estimateKcalBurned('skateboarding', 30, 80), 160);
  });
  it('returns 0 for non-positive minutes', () => {
    assert.equal(estimateKcalBurned('running', 0, 70), 0);
    assert.equal(estimateKcalBurned('running', -10, 70), 0);
  });
  it('returns 0 for missing weight', () => {
    assert.equal(estimateKcalBurned('running', 30, 0), 0);
    assert.equal(estimateKcalBurned('running', 30, NaN), 0);
  });
});

describe('buildActivityEntry', () => {
  it('builds a complete entry with MET-derived kcal', () => {
    const e = buildActivityEntry({ type: 'cycling', minutes: 45, weightKg: 70 }, 1700000000000);
    assert.equal(e.type, 'cycling');
    assert.equal(e.minutes, 45);
    // 7.0 × 70 × 0.75 = 367.5 → 368
    assert.equal(e.kcal_burned, 368);
    assert.equal(e.date, new Date(1700000000000).toISOString().slice(0, 10));
    assert.ok(typeof e.id === 'string' && e.id.length > 0);
  });
  it('kcalOverride wins when > 0', () => {
    const e = buildActivityEntry({ type: 'running', minutes: 30, weightKg: 70, kcalOverride: 500 });
    assert.equal(e.kcal_burned, 500);
  });
  it('falls back to MET estimate when override <= 0', () => {
    const e = buildActivityEntry({ type: 'running', minutes: 30, weightKg: 70, kcalOverride: 0 });
    assert.equal(e.kcal_burned, 343);
  });
  it('coerces unknown type to "other"', () => {
    const e = buildActivityEntry({ type: 'bogus', minutes: 10, weightKg: 70 });
    assert.equal(e.type, 'other');
  });
  it('clamps negative minutes to 0', () => {
    const e = buildActivityEntry({ type: 'running', minutes: -5, weightKg: 70 });
    assert.equal(e.minutes, 0);
    assert.equal(e.kcal_burned, 0);
  });
  it('trims + drops empty notes', () => {
    const a = buildActivityEntry({ type: 'yoga', minutes: 30, weightKg: 70, note: '   ' });
    assert.equal(a.note, undefined);
    const b = buildActivityEntry({ type: 'yoga', minutes: 30, weightKg: 70, note: '  morning  ' });
    assert.equal(b.note, 'morning');
  });
});

describe('sumBurned', () => {
  it('sums kcal + minutes across entries', () => {
    const out = sumBurned([
      { kcal_burned: 300, minutes: 45 },
      { kcal_burned: 150, minutes: 20 },
      { kcal_burned: 50,  minutes: 10 },
    ]);
    assert.equal(out.kcal, 500);
    assert.equal(out.minutes, 75);
    assert.equal(out.count, 3);
  });
  it('handles an empty list', () => {
    const out = sumBurned([]);
    assert.equal(out.kcal, 0);
    assert.equal(out.minutes, 0);
    assert.equal(out.count, 0);
  });
  it('tolerates missing fields', () => {
    const out = sumBurned([{ kcal_burned: 200 }, { minutes: 10 }, {}]);
    assert.equal(out.kcal, 200);
    assert.equal(out.minutes, 10);
    assert.equal(out.count, 3);
  });
});
