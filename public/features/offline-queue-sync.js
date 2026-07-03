/**
 * Offline queue sync — pending-scan banner refresh and retry-on-reconnect.
 * Keeps rate-limited scans instead of deleting them and records exponential
 * backoff metadata directly on each queued record.
 */
import { t } from '../core/i18n.js';
import { show, hide } from '../core/dom-helpers.js';
import { listPending, remove as removePending, update as updatePending, countPending } from '../data/queue-store.js';
import { apiUrl } from '../core/api-base.js';

const $ = (id) => document.getElementById(id);
const pendingBanner = $('pending-banner');
const pendingText = $('pending-text');
const MAX_RATE_LIMIT_RETRIES = 5;

async function updatePendingBanner() {
  const n = await countPending().catch(() => 0);
  if (n === 0) { hide(pendingBanner); return; }
  pendingText.textContent = t('pendingScans', { n });
  show(pendingBanner);
}

function nextRetryPatch(item, reason = 'Retry pending') {
  const retryCount = Number(item.retryCount || 0);
  const capped = retryCount >= MAX_RATE_LIMIT_RETRIES;
  const backoffMs = capped ? 60_000 : Math.min(1000 * Math.pow(2, retryCount), 60_000);
  return {
    retryCount: retryCount + 1,
    lastError: reason,
    nextRetryTime: Date.now() + backoffMs,
  };
}

function shouldRemovePendingAfterResponse(res) {
  return Boolean(res?.ok);
}

async function deferPending(item, reason) {
  const patch = nextRetryPatch(item, reason);
  if (patch.retryCount > MAX_RATE_LIMIT_RETRIES) {
    console.warn(`[Queue] ${item.id} still pending after ${MAX_RATE_LIMIT_RETRIES} retries: ${reason}`);
  }
  await updatePending(item.id, patch);
}

async function retryPending() {
  try {
    const now = Date.now();
    const items = await listPending();
    for (const item of items) {
      if (item.nextRetryTime && item.nextRetryTime > now) continue;
      try {
        const res = await fetch(apiUrl('/api/score'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ images: item.images, barcode: item.barcode }),
        });
        if (shouldRemovePendingAfterResponse(res)) {
          await removePending(item.id);
          continue;
        }
        await deferPending(item, res.status === 429 ? '429 Rate-limited; retry pending' : `HTTP ${res.status}; retry pending`);
        if (res.status === 429) break;
      } catch (err) {
        await deferPending(item, err?.message || 'Network error; retry pending');
        break;
      }
    }
  } catch (err) {
    console.warn('[retryPending] aborted', err);
  }
  await updatePendingBanner().catch(() => { /* banner is non-critical */ });
}

window.addEventListener('online', retryPending);

export { updatePendingBanner, retryPending, shouldRemovePendingAfterResponse, nextRetryPatch };
