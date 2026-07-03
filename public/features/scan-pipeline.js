/**
 * Scan pipeline — server-mode and direct-mode product lookup/scoring,
 * orchestration of a full scan (cache check → mode dispatch → render →
 * persist), and the offline-enqueue fallback. ADR-0004 feature-folder
 * pattern, extracted per the app.js decomposition plan (Phase 6).
 *
 * Pulls directly from already-extracted modules (queue state, queue UI,
 * core helpers, data layer). Functions still owned by app.js at extraction
 * time (engine bootstrap, render cluster, offline-banner refresh, the
 * comparison-deps snapshot, and the lastData setter) are supplied via the
 * `initScanPipeline(deps)` factory, matching the deps-object convention
 * used by scanner.js / recipes-dialog.js / update-checker.js.
 *
 * deps shape:
 *   { t, getKey, loadEngine, getMode,
 *     renderAudit, renderIngredients, renderNutrition,
 *     persistToHistory, updatePendingBanner,
 *     comparisonDeps,    ← () => current comparison-deps snapshot
 *     setLastData }       ← (data) => void, mirrors app.js's `lastData`
 *
 * DOM elements (#error, #result, #status, #status-text, .capture) are
 * looked up by id directly, matching the existing convention in this
 * cluster — no id renaming in this restructuring (H6).
 */
import { show, hide } from '../core/dom-helpers.js';
import { queue } from '../state/scan-queue-state.js';
import { queuePayload, firstBarcode } from './scan-queue-ui.js';
import { maybeRenderComparison } from './comparison.js';
import { enqueue } from '../data/queue-store.js';
import { findScanByBarcode } from '../data/scan-history.js';
import { logEvent as telemetryLog } from '../core/telemetry.js';
import { computePersonalScore } from '../core/personal-score.js';
import { getProfile } from '../data/profile.js';
import { currentLang } from '../core/i18n.js';
import { goToTab } from './tab-nav.js';
import { apiUrl } from '../core/api-base.js';

const $ = (id) => document.getElementById(id);
const errorEl = $('error');
const resultEl = $('result');
const statusEl = $('status');
const statusText = $('status-text');
const scanBtn = $('scan-btn');

function initScanPipeline(deps) {
  const { t, getKey, loadEngine, getMode, renderAudit, renderIngredients,
    renderNutrition, persistToHistory, updatePendingBanner, comparisonDeps,
    setLastData } = deps;

  async function scanViaServer() {
    const res = await fetch(apiUrl('/api/score'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ images: queuePayload(), barcode: firstBarcode() }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      if (res.status === 429 || body.error === 'rate_limit') {
        throw new Error(t('errRateLimit'));
      }
      throw new Error(body.error || `HTTP ${res.status}`);
    }
    return res.json();
  }

  async function scanViaDirect() {
    const key = getKey();
    const {
      parseLabel, scoreProduct, fetchFromOFF,
      isOFFSparse, mergeOFFWithLLM, detectSourceConflicts,
    } = await loadEngine();
    const bc = firstBarcode();
    const payload = queuePayload();

    if (bc) {
      const off = await fetchFromOFF(bc);
      if (off) {
        // Mirror the server-mode hybrid path: when OFF returns a thin
        // record AND the user supplied label photos AND a Groq key is
        // configured, run the LLM augmentation pass + surface any
        // OFF/LLM source conflicts. Previously direct-mode skipped this
        // step and returned the OFF-only audit even when the photos
        // could have filled the gaps.
        if (isOFFSparse(off) && payload.length > 0 && key) {
          try {
            const parsed = await parseLabel(payload, { apiKey: key });
            const merged = mergeOFFWithLLM(off, parsed.product);
            const conflicts = detectSourceConflicts(off, parsed.product);
            // Fix #15: conflicts are now {message, severity} objects. Flatten to
            // strings for the warnings array but prefix high-severity ones with
            // a red-flag marker so the renderer can style them differently.
            const conflictStrings = conflicts.map(
              (c) => (c.severity === 'high' ? `🔴 ${c.message}` : `🟡 ${c.message}`),
            );
            return {
              product: merged,
              audit: scoreProduct(merged),
              warnings: [...parsed.warnings, ...conflictStrings],
              source: 'merged',
              barcode: bc,
            };
          } catch (err) {
            // LLM failed (rate limit, network) — fall through to OFF
            // alone so the user still gets a score. 429 is surfaced
            // for any LLM-only path below; here we just downgrade.
            console.warn('[scanViaDirect] LLM augmentation failed, falling back to OFF:', err?.message || err);
          }
        }
        return { product: off, audit: scoreProduct(off), warnings: [], source: 'openfoodfacts', barcode: bc };
      }
    }

    if (payload.length === 0) throw new Error(t('errNoPhotos'));
    if (!key) throw new Error(t('errMissingKey'));
    let parsed;
    try {
      parsed = await parseLabel(payload, { apiKey: key });
    } catch (err) {
      // ocr-parser tags the thrown Error with .status — 429 means the user's
      // own Groq quota is saturated. Surface a translated message instead of
      // the raw "Groq API 429: …" string.
      if (err?.status === 429) throw new Error(t('errRateLimit'));
      throw err;
    }
    return {
      product: parsed.product,
      audit: scoreProduct(parsed.product),
      warnings: parsed.warnings,
      source: 'llm',
      barcode: parsed.barcode ?? null,
    };
  }

  async function scanImage() {
    if (queue.length === 0) return;
    // R-fix: scanBtn was never disabled while a scan was in flight — the
    // aria-busy overlay is purely visual (pointer-events: none), so rapid
    // double-clicks fired concurrent scanViaServer()/scanViaDirect() calls
    // against the same queue (out-of-order responses, duplicate history
    // entries, duplicate API spend). Disable for the duration; restored in
    // both the success and error paths below.
    if (scanBtn) scanBtn.disabled = true;
    hide(errorEl); hide(resultEl); show(statusEl);
    // §DST2 loading — mark the capture panel as aria-busy so CSS can
    // overlay a "scanning in progress" state. Cleared in the
    // finally-like block where status is hidden / result is shown.
    const captureEl = document.querySelector('.capture');
    if (captureEl) captureEl.setAttribute('aria-busy', 'true');
    const bc = firstBarcode();
    statusText.textContent = bc
      ? t('barcodeDetected', { code: bc })
      : queue.length > 1 ? t('analysingN', { n: queue.length })
      : t('analysing');

    // "Progressive" status: real streaming of the LLM response is a bigger
    // refactor. In the meantime, cycle the status line through the phases a
    // real progressive parser would report — ingredients → nutrition →
    // scoring — so the user sees motion instead of one long spinner.
    let phaseTimer = null;
    if (!bc) {
      const phases = [t('phaseIngredients'), t('phaseNutrition'), t('phaseScoring')];
      let idx = 0;
      phaseTimer = setInterval(() => {
        statusText.textContent = phases[idx % phases.length];
        idx++;
      }, 1500);
    }

    const mode = getMode();
    try {
      let data;
      // Barcode cache: if the user has scanned this exact EAN/UPC before, hand
      // back the stored snapshot instead of round-tripping OFF + LLM. Makes
      // re-scans sub-100ms and saves API quota / OFF bandwidth.
      if (bc) {
        try {
          const cached = await findScanByBarcode(bc);
          if (cached?.snapshot) {
            data = { ...cached.snapshot, source: cached.snapshot.source || 'cache' };
          }
        } catch { /* cache is an optimization — never block scan on it */ }
      }
      if (!data) {
        if (mode === 'direct') data = await scanViaDirect();
        else if (mode === 'server') data = await scanViaServer();
        else {
          try { data = await scanViaServer(); }
          catch (err) {
            if (getKey()) { statusText.textContent = t('serverUnavailable'); data = await scanViaDirect(); }
            else throw err;
          }
        }
      }
      if (phaseTimer) { clearInterval(phaseTimer); phaseTimer = null; }
      hide(statusEl);
      if (captureEl) captureEl.removeAttribute('aria-busy');
      if (scanBtn) scanBtn.disabled = queue.length === 0;
      setLastData(data);
      maybeRenderComparison(data, comparisonDeps());
      renderAudit(data);
      renderIngredients(data.product);
      renderNutrition(data.product);
      show(resultEl);
      goToTab('scan');
      // Fix #17: compute personal score here (same call as renderPersonalScore)
      // and attach it to the record so scan-history can show how the user's
      // dietary context changed the evaluation retrospectively.
      const _ps = computePersonalScore(data.audit, data.product, getProfile(), currentLang);
      persistToHistory({ ...data, personal_score: _ps?.applicable ? (_ps.veto ? 0 : _ps.personal_score) : null });
    } catch (err) {
      if (phaseTimer) { clearInterval(phaseTimer); phaseTimer = null; }
      hide(statusEl);
      if (captureEl) captureEl.removeAttribute('aria-busy');
      if (scanBtn) scanBtn.disabled = queue.length === 0;
      console.error('[scan] failed', err);
      telemetryLog('scan-failed', err?.message || String(err), bc ? `barcode=${bc}` : `mode=${mode}`);
      // navigator.onLine is the primary signal. The regex is a secondary probe
      // on the error message: cover EN phrasing (Chrome, Safari) + FR phrasing
      // ("Échec du réseau", "Impossible de charger") so French users also hit
      // the offline-queue fallback instead of a generic error.
      const isNet = !navigator.onLine
        || /network|failed to fetch|load failed|[eé]chec du r[eé]seau|impossible de charger/i.test(err.message);
      if (isNet) {
        await enqueueCurrent();
        errorEl.textContent = `${t('offline')} — ${err.message}`;
      } else {
        errorEl.textContent = `${err.message}`;
      }
      show(errorEl);
    }
  }

  async function enqueueCurrent() {
    if (queue.length === 0) return;
    await enqueue({
      id: crypto.randomUUID(),
      createdAt: Date.now(),
      images: queue.filter((q) => q.base64).map((q) => ({ base64: q.base64, mime: q.mime })),
      barcode: firstBarcode(),
    });
    await updatePendingBanner();
  }

  return { scanViaServer, scanViaDirect, scanImage, enqueueCurrent };
}

export { initScanPipeline };
