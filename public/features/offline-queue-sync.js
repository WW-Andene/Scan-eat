/**
 * Offline queue sync — pending-scan banner refresh and retry-on-reconnect.
 * Consumed externally by update-checker.js via the app.js deps object
 * (deps.updatePendingBanner — H3). Also called from scan-pipeline.js's
 * enqueueCurrent() via the deps it receives from app.js. ADR-0004
 * feature-folder pattern, extracted per the app.js decomposition plan
 * (Phase 7).
 *
 * DOM elements (#pending-banner, #pending-text) are looked up by id
 * directly, matching the existing convention in this cluster — no id
 * renaming in this restructuring (H6).
 */
import { t } from '../core/i18n.js';
import { show, hide } from '../core/dom-helpers.js';
import { listPending, remove as removePending, countPending } from '../data/queue-store.js';
import { apiUrl } from '../core/api-base.js';

const $ = (id) => document.getElementById(id);
const pendingBanner = $('pending-banner');
const pendingText = $('pending-text');

async function updatePendingBanner() {
  const n = await countPending().catch(() => 0);
  if (n === 0) { hide(pendingBanner); return; }
  // Plural handled by t() via Intl.PluralRules — one key, two variants.
  pendingText.textContent = t('pendingScans', { n });
  show(pendingBanner);
}

async function retryPending() {
  try {
    const items = await listPending();
    for (const item of items) {
      try {
        const res = await fetch(apiUrl('/api/score'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ images: item.images, barcode: item.barcode }),
        });
        if (res.ok) await removePending(item.id);
        else break; // stop the loop on first failure
      } catch { break; }
    }
  } catch (err) {
    // IDB read failed (quota / versionchange / browser shutdown). The banner
    // will repaint empty next tick and the user can still add new scans.
    console.warn('[retryPending] aborted', err);
  }
  await updatePendingBanner().catch(() => { /* banner is non-critical */ });
}

window.addEventListener('online', retryPending);

export { updatePendingBanner, retryPending };
