/**
 * shareOrCopy — fallback chain: navigator.share → clipboard → toast.
 *
 * Pins that R5.9's unified share helper behaves the same way every
 * caller expects: AbortError (user cancelled) is silent; generic share
 * failure → clipboard path; clipboard failure → `failed` toast;
 * missing navigator.share → straight to clipboard.
 */

import { strict as assert } from 'node:assert';
import { describe, it, afterEach } from 'node:test';

// @ts-expect-error — plain JS
import { shareOrCopy } from '../public/core/share.js';

type Navigator = {
  share?: (d: { title?: string; text?: string }) => Promise<void>;
  clipboard?: { writeText?: (s: string) => Promise<void> };
};

// Stub globals before each test. Node 22 has navigator as a read-only
// property, so we patch via Object.defineProperty.
const origNavigator = (globalThis as { navigator?: Navigator }).navigator;
function setNavigator(n: Navigator | undefined) {
  Object.defineProperty(globalThis, 'navigator', {
    value: n, configurable: true, writable: true,
  });
}

afterEach(() => setNavigator(origNavigator));

describe('shareOrCopy', () => {
  it('prefers navigator.share when available', async () => {
    const calls: Array<{ where: string; arg?: unknown }> = [];
    setNavigator({
      share: async (d) => { calls.push({ where: 'share', arg: d }); },
      clipboard: { writeText: async () => { calls.push({ where: 'clip' }); } },
    });
    const toasts: string[] = [];
    await shareOrCopy({
      title: 't', text: 'body',
      toasts: { copied: 'C', failed: 'F' },
      toast: (s: string) => toasts.push(s),
    });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].where, 'share');
    assert.equal(toasts.length, 0);
  });

  it('silently absorbs AbortError (user cancelled native sheet)', async () => {
    setNavigator({
      share: async () => { const e = new Error('aborted'); e.name = 'AbortError'; throw e; },
      clipboard: { writeText: async () => { throw new Error('must not be called'); } },
    });
    const toasts: string[] = [];
    await shareOrCopy({
      title: 't', text: 'body',
      toasts: { copied: 'C', failed: 'F' },
      toast: (s: string) => toasts.push(s),
    });
    assert.equal(toasts.length, 0);
  });

  it('falls back to clipboard when navigator.share throws non-AbortError', async () => {
    const writes: string[] = [];
    setNavigator({
      share: async () => { throw new Error('browser refused'); },
      clipboard: { writeText: async (s: string) => { writes.push(s); } },
    });
    const toasts: Array<{ msg: string; variant?: string }> = [];
    await shareOrCopy({
      title: 't', text: 'body',
      toasts: { copied: 'C', failed: 'F' },
      toast: (m: string, v?: string) => toasts.push({ msg: m, variant: v }),
    });
    assert.deepEqual(writes, ['body']);
    // R8.2: clipboard fallback now emits the 'ok' variant so the toast
    // stripes green — visually distinct from info / warn / error.
    assert.deepEqual(toasts, [{ msg: 'C', variant: 'ok' }]);
  });

  it('falls back to clipboard when navigator.share is missing', async () => {
    const writes: string[] = [];
    setNavigator({
      clipboard: { writeText: async (s: string) => { writes.push(s); } },
    });
    const toasts: string[] = [];
    await shareOrCopy({
      title: 't', text: 'body',
      toasts: { copied: 'C', failed: 'F' },
      toast: (m: string) => toasts.push(m),
    });
    assert.deepEqual(writes, ['body']);
    assert.deepEqual(toasts, ['C']);
  });

  it('passes the ok variant alongside the copied toast', async () => {
    setNavigator({
      clipboard: { writeText: async () => { /* swallow */ } },
    });
    const variants: Array<string | undefined> = [];
    await shareOrCopy({
      title: 't', text: 'body',
      toasts: { copied: 'C', failed: 'F' },
      toast: (_m: string, v?: string) => variants.push(v),
    });
    assert.deepEqual(variants, ['ok']);
  });

  it('fires `failed` toast when both share and clipboard fail', async () => {
    setNavigator({
      clipboard: { writeText: async () => { throw new Error('perm denied'); } },
    });
    const toasts: Array<{ msg: string; variant?: string }> = [];
    await shareOrCopy({
      title: 't', text: 'body',
      toasts: { copied: 'C', failed: 'F' },
      toast: (m: string, v?: string) => toasts.push({ msg: m, variant: v }),
    });
    assert.deepEqual(toasts, [{ msg: 'F', variant: 'error' }]);
  });

  it('is a no-op when text is empty', async () => {
    let called = false;
    setNavigator({
      share: async () => { called = true; },
      clipboard: { writeText: async () => { called = true; } },
    });
    await shareOrCopy({
      title: 't', text: '',
      toasts: { copied: 'C', failed: 'F' },
      toast: () => { called = true; },
    });
    assert.equal(called, false);
  });
});
