/**
 * Per-day notes — localStorage-backed CRUD tests.
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
import { getDayNote, setDayNote, listDayNoteDates, DAY_NOTE_MAX_CHARS } from '../public/features/day-notes.js';

describe('day-notes', () => {
  beforeEach(() => { store.clear(); });

  it('getDayNote returns "" when absent', () => {
    assert.equal(getDayNote('2026-04-22'), '');
  });

  it('set + get round-trip', () => {
    setDayNote('2026-04-22', 'felt good, did 5k');
    assert.equal(getDayNote('2026-04-22'), 'felt good, did 5k');
  });

  it('setDayNote with "" removes the entry', () => {
    setDayNote('2026-04-22', 'some note');
    setDayNote('2026-04-22', '');
    assert.equal(getDayNote('2026-04-22'), '');
    assert.equal(store.has('scanneat.note.2026-04-22'), false);
  });

  it('truncates to DAY_NOTE_MAX_CHARS', () => {
    const long = 'x'.repeat(DAY_NOTE_MAX_CHARS + 50);
    setDayNote('2026-04-22', long);
    assert.equal(getDayNote('2026-04-22').length, DAY_NOTE_MAX_CHARS);
  });

  it('listDayNoteDates returns all YYYY-MM-DD keys sorted', () => {
    setDayNote('2026-04-20', 'a');
    setDayNote('2026-04-22', 'b');
    setDayNote('2026-04-21', 'c');
    assert.deepEqual(listDayNoteDates(), ['2026-04-20', '2026-04-21', '2026-04-22']);
  });

  it('no-ops on missing date', () => {
    setDayNote('', 'x');
    assert.equal(getDayNote(''), '');
  });
});
