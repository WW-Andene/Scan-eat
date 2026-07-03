/**
 * weightForecast() — linear extrapolation from current → goal weight.
 * Kept in its own test file so presenter-tests.ts (which currently hangs
 * under node:test for unrelated reasons) doesn't block the feature.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module
import { weightForecast } from '../public/core/presenters.js';

const DAY = 86_400_000;
const NOW = 1_700_000_000_000; // fixed reference for determinism

describe('weightForecast', () => {
  it('insufficient-data when any input is missing/invalid', () => {
    assert.equal(weightForecast(0, 70, -0.5).status, 'insufficient-data');
    assert.equal(weightForecast(80, 0, -0.5).status, 'insufficient-data');
    assert.equal(weightForecast(80, 70, NaN).status, 'insufficient-data');
    assert.equal(weightForecast(null as unknown as number, 70, -0.5).status, 'insufficient-data');
  });

  it('already-reached within 0.05 kg of goal', () => {
    assert.equal(weightForecast(70.03, 70, -0.5).status, 'already-reached');
    assert.equal(weightForecast(70, 70, 0.3).status, 'already-reached');
  });

  it('flat when slope is exactly 0 kg/week', () => {
    assert.equal(weightForecast(80, 70, 0).status, 'flat');
  });

  it('wrong-direction when slope points away from goal', () => {
    // Want to lose (delta = -10) but trending up (+0.3 kg/week)
    assert.equal(weightForecast(80, 70, 0.3).status, 'wrong-direction');
    // Want to gain (delta = +5) but trending down
    assert.equal(weightForecast(60, 65, -0.2).status, 'wrong-direction');
  });

  it('projects the target date when the slope points the right way', () => {
    // 80 → 70 at -0.5 kg/week = 20 weeks = 140 days
    const r = weightForecast(80, 70, -0.5, NOW);
    assert.equal(r.status, 'ok');
    assert.equal(r.weeks, 20);
    assert.equal(r.days, 140);
    assert.equal(r.targetISO, new Date(NOW + 140 * DAY).toISOString().slice(0, 10));
    assert.equal(r.kgPerWeek, -0.5);
  });

  it('symmetric for gain case', () => {
    const r = weightForecast(60, 65, 0.25, NOW);
    assert.equal(r.status, 'ok');
    assert.equal(r.weeks, 20);
    assert.equal(r.days, 140);
  });
});
