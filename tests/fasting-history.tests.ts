/**
 * Fasting history module — builder + streak tests.
 * localStorage polyfilled.
 */

import { strict as assert } from 'node:assert';
import { describe, it, beforeEach } from 'node:test';

const store = new Map<string, string>();
(globalThis as { localStorage?: Storage }).localStorage = {
  getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
  setItem: (k: string, v: string) => { store.set(k, v); },
  removeItem: (k: string) => { store.delete(k); },
  clear: () => store.clear(),
  get length() { return store.size; },
  key: (i: number) => Array.from(store.keys())[i] ?? null,
} as Storage;

// @ts-expect-error — plain JS module
import { buildFastCompletion, listFastHistory, saveFastCompletion, clearFastHistory, computeFastStreak } from '../public/features/fasting-history.js';

const HR = 3_600_000;

describe('buildFastCompletion', () => {
  it('flags complete when duration >= target_hours', () => {
    const r = buildFastCompletion({ start_ms: 0, end_ms: 16 * HR, target_hours: 16 });
    assert.ok(r);
    assert.equal(r.duration_ms, 16 * HR);
    assert.equal(r.complete, true);
  });
  it('flags incomplete when stopped early', () => {
    const r = buildFastCompletion({ start_ms: 0, end_ms: 10 * HR, target_hours: 16 });
    assert.equal(r.complete, false);
  });
  it('returns null on invalid input (end <= start)', () => {
    assert.equal(buildFastCompletion({ start_ms: 100, end_ms: 50, target_hours: 16 }), null);
    assert.equal(buildFastCompletion({ start_ms: NaN as unknown as number, end_ms: 100, target_hours: 16 }), null);
  });
  it('marks complete false when target is missing', () => {
    const r = buildFastCompletion({ start_ms: 0, end_ms: 18 * HR, target_hours: undefined as unknown as number });
    assert.equal(r.complete, false);
    assert.equal(r.target_hours, null);
  });
});

describe('history persistence', () => {
  beforeEach(() => { store.clear(); });

  it('listFastHistory returns [] when empty', () => {
    assert.deepEqual(listFastHistory(), []);
  });

  it('saveFastCompletion appends entries + listFastHistory returns them in order', () => {
    const a = buildFastCompletion({ start_ms: 0, end_ms: 16 * HR, target_hours: 16 });
    const b = buildFastCompletion({ start_ms: 24 * HR, end_ms: 40 * HR, target_hours: 16 });
    saveFastCompletion(a);
    saveFastCompletion(b);
    const all = listFastHistory();
    assert.equal(all.length, 2);
    assert.equal(all[0].id, a.id);
    assert.equal(all[1].id, b.id);
  });

  it('caps at 100 entries via FIFO eviction', () => {
    for (let i = 0; i < 105; i += 1) {
      saveFastCompletion(buildFastCompletion({
        start_ms: i * 24 * HR,
        end_ms: i * 24 * HR + 16 * HR,
        target_hours: 16,
      }));
    }
    const all = listFastHistory();
    assert.equal(all.length, 100);
    // Oldest 5 were evicted — the earliest surviving entry's start_ms = 5 * 24 h.
    assert.equal(all[0].start_ms, 5 * 24 * HR);
  });

  it('clearFastHistory wipes the store', () => {
    saveFastCompletion(buildFastCompletion({ start_ms: 0, end_ms: 16 * HR, target_hours: 16 }));
    clearFastHistory();
    assert.deepEqual(listFastHistory(), []);
  });

  it('ignores a null / undefined record without crashing', () => {
    saveFastCompletion(null as never);
    saveFastCompletion(undefined as never);
    assert.deepEqual(listFastHistory(), []);
  });

  it('tolerates a corrupt localStorage value', () => {
    store.set('scanneat.fasting.history', '{not json');
    assert.deepEqual(listFastHistory(), []);
  });
});

describe('computeFastStreak', () => {
  const complete = { complete: true };
  const incomplete = { complete: false };
  it('returns 0 for empty history', () => {
    assert.equal(computeFastStreak([]), 0);
    assert.equal(computeFastStreak(null as unknown as []), 0);
  });
  it('counts consecutive complete fasts from the most recent', () => {
    assert.equal(computeFastStreak([complete, complete, complete]), 3);
  });
  it('resets on an incomplete fast', () => {
    assert.equal(computeFastStreak([complete, incomplete, complete, complete]), 2);
  });
  it('returns 0 if the most recent fast was incomplete', () => {
    assert.equal(computeFastStreak([complete, complete, incomplete]), 0);
  });
});
