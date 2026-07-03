/**
 * app-settings shim — validation + defaults tests.
 * Provides a tiny localStorage mock since Node test runner doesn't ship one.
 */

import { strict as assert } from 'node:assert';
import { describe, it, beforeEach } from 'node:test';

// Install a minimal localStorage polyfill for Node.
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
import { getSetting, setSetting, snapshotSettings } from '../public/core/app-settings.js';

describe('app-settings', () => {
  beforeEach(() => { store.clear(); });

  it('returns default when unset', () => {
    assert.equal(getSetting('scanneat.mode'), 'auto');
    assert.equal(getSetting('scanneat.theme'), 'dark');
    assert.equal(getSetting('scanneat.fontSize'), 'normal');
  });

  it('round-trips a valid enum', () => {
    setSetting('scanneat.mode', 'direct');
    assert.equal(getSetting('scanneat.mode'), 'direct');
  });

  it('rejects invalid enum values on write', () => {
    assert.throws(() => setSetting('scanneat.mode', 'bogus'));
  });

  it('falls back to default when LS contains an invalid value (schema drift)', () => {
    // Simulates an old app version writing a value we no longer accept.
    store.set('scanneat.theme', 'techno');
    assert.equal(getSetting('scanneat.theme'), 'dark');
  });

  it('empty-string write removes the key (clean slate)', () => {
    setSetting('scanneat.key', 'gsk_123');
    assert.equal(store.has('scanneat.key'), true);
    setSetting('scanneat.key', '');
    assert.equal(store.has('scanneat.key'), false);
  });

  it('snapshotSettings returns every managed key', () => {
    const snap = snapshotSettings();
    assert.ok('scanneat.mode' in snap);
    assert.ok('scanneat.theme' in snap);
    assert.ok('scanneat.fontFamily' in snap);
  });

  it('unknown key passes through without validation', () => {
    setSetting('scanneat.future', 'anything');
    // Not in schema → getSetting returns the raw value, doesn't throw
    assert.equal(getSetting('scanneat.future'), 'anything');
  });
});
