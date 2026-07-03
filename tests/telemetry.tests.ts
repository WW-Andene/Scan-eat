/**
 * Local telemetry — opt-in + FIFO cap + format contract.
 *
 * The module writes to localStorage, so we polyfill a Map-backed store
 * before the import runs. Verifies:
 *   - isEnabled() defaults false; setEnabled() flips it
 *   - logEvent() is a no-op when disabled
 *   - logEvent() caps at MAX_EVENTS (50)  FIFO eviction
 *   - formatEvents() reverses chronology and includes context
 *   - clearEvents() drops only events, not the enabled flag
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
const tele = await import('../public/core/telemetry.js');
const { isEnabled, setEnabled, logEvent, listEvents, clearEvents, formatEvents } = tele;

describe('telemetry opt-in gate', () => {
  beforeEach(() => { store.clear(); });

  it('isEnabled() is false by default', () => {
    assert.equal(isEnabled(), false);
  });

  it('setEnabled(true) flips; setEnabled(false) clears', () => {
    setEnabled(true);
    assert.equal(isEnabled(), true);
    setEnabled(false);
    assert.equal(isEnabled(), false);
  });

  it('logEvent() is a no-op when disabled', () => {
    assert.equal(isEnabled(), false);
    logEvent('info', 'ignored', 'ctx');
    assert.deepEqual(listEvents(), []);
  });

  it('logEvent() records { ts, type, message, context } when enabled', () => {
    setEnabled(true);
    logEvent('warn', 'hello', 'x=1');
    const events = listEvents();
    assert.equal(events.length, 1);
    assert.equal(events[0].type, 'warn');
    assert.equal(events[0].message, 'hello');
    assert.equal(events[0].context, 'x=1');
    assert.match(events[0].ts, /^\d{4}-\d{2}-\d{2}T/);
  });
});

describe('telemetry FIFO cap', () => {
  beforeEach(() => { store.clear(); setEnabled(true); });

  it('caps at 50 — evicts the oldest on overflow', () => {
    for (let i = 0; i < 60; i++) logEvent('info', `m${i}`);
    const events = listEvents();
    assert.equal(events.length, 50);
    // FIFO means the first ten (m0..m9) are gone; m10 is oldest.
    assert.equal(events[0].message, 'm10');
    assert.equal(events[events.length - 1].message, 'm59');
  });
});

describe('telemetry formatEvents()', () => {
  beforeEach(() => { store.clear(); setEnabled(true); });

  it('returns "(no events)" sentinel when empty', () => {
    assert.match(formatEvents(), /no events/i);
  });

  it('orders newest-first and includes context', () => {
    logEvent('info', 'first', 'ctx1');
    logEvent('error', 'second', 'ctx2');
    const out = formatEvents();
    // "second" appears before "first" — reverse chronological.
    const iFirst = out.indexOf('first');
    const iSecond = out.indexOf('second');
    assert.ok(iSecond < iFirst, 'newest-first ordering');
    assert.ok(out.includes('(ctx1)'));
    assert.ok(out.includes('(ctx2)'));
    assert.ok(out.includes('ERROR'));
  });
});

describe('telemetry clearEvents()', () => {
  it('clearEvents() drops events but keeps the enabled flag', () => {
    store.clear();
    setEnabled(true);
    logEvent('info', 'before-clear');
    assert.equal(listEvents().length, 1);
    clearEvents();
    assert.equal(listEvents().length, 0);
    assert.equal(isEnabled(), true);
  });
});

describe('telemetry formatEvents() contract edges', () => {
  it('survives a corrupted localStorage payload (returns []) — no throw', () => {
    store.clear();
    store.set('scanneat.telemetry.events', '{{ not valid json');
    // listEvents / formatEvents must defend against hand-edited LS.
    assert.doesNotThrow(() => formatEvents());
    assert.deepEqual(listEvents(), []);
  });

  it('caps message + context to the stored size limit', () => {
    store.clear();
    setEnabled(true);
    const longMsg = 'x'.repeat(1000);
    const longCtx = 'y'.repeat(1000);
    logEvent('info', longMsg, longCtx);
    const [e] = listEvents();
    // Contract: message ≤ 500 chars, context ≤ 300 chars.
    assert.ok(e.message.length <= 500);
    assert.ok(e.context.length <= 300);
  });

  it('never serialises personal-sounding payloads verbatim (caller-enforced)', () => {
    store.clear();
    setEnabled(true);
    // Non-string types get coerced via String(); objects become
    // "[object Object]". This is the contract: the telemetry log is a
    // string log, not a structured dump.
    logEvent('info', { secret: 'do not log' } as unknown as string);
    const [e] = listEvents();
    assert.equal(e.message, '[object Object]');
  });
});
