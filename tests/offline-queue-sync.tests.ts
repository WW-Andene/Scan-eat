/** Offline queue retry policy — protects pending scans from 429/data-loss regressions. */
import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

(globalThis as { localStorage?: Storage }).localStorage = {
  getItem() { return null; }, setItem() {}, removeItem() {}, clear() {}, key() { return null; }, get length() { return 0; },
} as unknown as Storage;
(globalThis as { document?: unknown }).document = { getElementById() { return null; } };
(globalThis as { window?: unknown }).window = { addEventListener() {} };

const { shouldRemovePendingAfterResponse, nextRetryPatch } = await import('../public/features/offline-queue-sync.js');

describe('offline queue retry policy', () => {
  it('removes a pending scan only after a successful response', () => {
    assert.equal(shouldRemovePendingAfterResponse({ ok: true, status: 200 }), true);
    assert.equal(shouldRemovePendingAfterResponse({ ok: false, status: 500 }), false);
  });

  it('keeps pending scans on 429 rate limits for a later retry', () => {
    assert.equal(shouldRemovePendingAfterResponse({ ok: false, status: 429 }), false);
  });

  it('records exponential backoff metadata instead of deleting rate-limited scans', () => {
    const before = Date.now();
    const patch = nextRetryPatch({ id: 'queued-1', retryCount: 2 }, '429 Rate-limited; retry pending');
    assert.equal(patch.retryCount, 3);
    assert.equal(patch.lastError, '429 Rate-limited; retry pending');
    assert.ok(patch.nextRetryTime >= before + 3900);
  });
});
