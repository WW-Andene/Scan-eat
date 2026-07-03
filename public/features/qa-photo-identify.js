/**
 * Quick Add AI photo-identify status line + the shared direct/server
 * mode-dispatch helper + Quick Add form reader. ADR-0004 feature-folder
 * pattern, extracted per the app.js decomposition plan (Phase 12).
 *
 * Phase 0 finding (H5, resolved): no overlap with
 * /features/qa-autocomplete.js. That module is the Quick Add *name
 * typeahead* (suggests existing foods as the user types). This module
 * is the *photo identification* status/dispatch plumbing for the three
 * qa-photo-* flows (single item, multi-item plate, menu). Different
 * "QA" — Quick Add form, not Quality Assurance — and different
 * concern. No merge; kept as a separate module per the plan's §4
 * mapping ("qa-autocomplete.js ← merge IF no duplication" — none
 * found, so qa-autocomplete.js is left untouched and this is a new
 * sibling file).
 *
 * Only setQaStatus, setQaLoadingPhases, identifyViaModePath, and
 * readQaForm move — matching the precedent set in Phase 9
 * (profiles-ui.js) and Phase 10 (custom-foods-day-notes.js): the
 * surrounding event handlers (qa-photo-input / qa-photo-multi-input /
 * qa-photo-menu-input 'change' listeners, qa-save 'click') stay in
 * app.js and call back into this module's exports — no
 * event-listener migration bundled in.
 *
 * Functions still owned by app.js at extraction time (getMode, getKey,
 * loadEngine — the Bootstrap/engine cluster, explicitly out of scope
 * for this restructuring per the plan's §2 table) are supplied via the
 * `initQaPhotoId(deps)` factory, matching the deps-object convention
 * used by scan-pipeline.js for the same three functions.
 *
 * deps shape: { t, getMode, getKey, loadEngine }
 *
 * DOM elements (#qa-ai-status) are looked up by id directly, matching
 * the existing convention in this cluster — no id renaming in this
 * restructuring (H6). The rotating-phase timer (_qaPhaseTimer) is
 * module-local state shared only between setQaStatus and
 * setQaLoadingPhases, exactly as it was in app.js — no new sharing
 * surface introduced.
 */
import { show, hide } from '../core/dom-helpers.js';

const $ = (id) => document.getElementById(id);

let _deps = null;

export function initQaPhotoId(deps) {
  _deps = deps;
}

let _qaPhaseTimer = null;

export function setQaStatus(text, state) {
  const qaAiStatus = $('qa-ai-status');
  if (!qaAiStatus) return;
  // Stop any rotating-phase loop when the caller transitions to a
  // final text (success/warn/error/empty).
  if (_qaPhaseTimer) { clearInterval(_qaPhaseTimer); _qaPhaseTimer = null; }
  if (!text) { hide(qaAiStatus); return; }
  qaAiStatus.textContent = text;
  if (state) qaAiStatus.dataset.state = state;
  else delete qaAiStatus.dataset.state;
  show(qaAiStatus);
}

// F-DST-05 — rotating-phase loading indicator for async LLM ops.
// Replaces the single static "Analyse en cours…" line while the LLM
// round-trips (often 3–10 s). phases[] rotates every 1.4 s; caller
// calls setQaStatus(finalText, state) to stop.
export function setQaLoadingPhases(phases) {
  const qaAiStatus = $('qa-ai-status');
  if (!qaAiStatus || !Array.isArray(phases) || phases.length === 0) return;
  if (_qaPhaseTimer) clearInterval(_qaPhaseTimer);
  let i = 0;
  qaAiStatus.textContent = phases[0];
  qaAiStatus.dataset.state = 'loading';
  show(qaAiStatus);
  _qaPhaseTimer = setInterval(() => {
    i = (i + 1) % phases.length;
    qaAiStatus.textContent = phases[i];
  }, 1400);
}

// Shared helper for the three qa-photo-* flows. Takes the image payload
// and runs it through either the direct-mode engine export or the
// server-mode endpoint — per the user's current /settings-mode choice.
// Centralises the 429 translation so callers don't each re-implement it.
export async function identifyViaModePath({ images, directFn, serverUrl }) {
  const { t, getMode, getKey, loadEngine } = _deps;
  const mode = getMode();
  if (mode === 'direct') {
    const engine = await loadEngine();
    const key = getKey();
    if (!key) throw new Error(t('errMissingKey'));
    try {
      return await directFn(engine, images, { apiKey: key });
    } catch (err) {
      if (err?.status === 429) throw new Error(t('errRateLimit'));
      throw err;
    }
  }
  const res = await fetch(serverUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ images }),
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

export function readQaForm() {
  return {
    name: $('qa-name')?.value || '',
    meal: $('qa-meal')?.value || 'snack',
    kcal: Number($('qa-kcal')?.value) || 0,
    carbs_g: Number($('qa-carbs')?.value) || 0,
    protein_g: Number($('qa-protein')?.value) || 0,
    fat_g: Number($('qa-fat')?.value) || 0,
    sat_fat_g: Number($('qa-satfat')?.value) || 0,
    sugars_g: Number($('qa-sugars')?.value) || 0,
    salt_g: Number($('qa-salt')?.value) || 0,
    fiber_g: Number($('qa-fiber')?.value) || 0,
  };
}
