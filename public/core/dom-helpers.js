/**
 * Generic DOM/UI helpers used across the app (5+ features).
 *
 * ADR-0004 feature-folder pattern, extracted per the app.js decomposition
 * plan (Phase 1). No deps-object needed — these are pure DOM utilities with
 * a single external dependency (`t` for i18n), imported directly like the
 * rest of core/*.
 */
import { t } from './i18n.js';

function show(el) { if (el) el.hidden = false; }
function hide(el) { if (el) el.hidden = true; }

/**
 * Lightweight non-blocking toast. Announces to screen readers via role=status
 * and auto-removes after a short delay. Prefer this over alert() inside the
 * PWA — native alert() on Android steals focus, breaks the read-aloud flow,
 * and looks foreign compared to the rest of the UI.
 */
let toastEl = null;
let toastTimer = null;
/**
 * Fire a transient toast. Second arg can be EITHER a variant string
 * ('ok' | 'warn' | 'error') or a numeric ms duration. Most callers pass
 * a variant; the legacy ms form is still honoured for the few tests /
 * sites that rely on it. Variant drives a `data-variant` attribute the
 * stylesheet can hook into (e.g. warning/error accent stripe).
 *
 * Prior to R7.9, the signature was `toast(text, ms=2600)` but ~10 sites
 * called `toast(text, 'error')`, passing a string where a number was
 * expected. setTimeout silently coerced it to NaN → inconsistent auto-
 * hide behavior across browsers. Unified signature fixes the silent
 * bug while staying backward-compatible.
 */
function toast(text, variantOrMs) {
  if (!toastEl) {
    toastEl = document.createElement('div');
    toastEl.className = 'app-toast';
    toastEl.setAttribute('role', 'status');
    toastEl.setAttribute('aria-live', 'polite');
    document.body.appendChild(toastEl);
  }
  let variant = '';
  let ms = 2600;
  if (typeof variantOrMs === 'number') ms = variantOrMs;
  else if (typeof variantOrMs === 'string') variant = variantOrMs;
  toastEl.textContent = String(text);
  if (variant) toastEl.dataset.variant = variant;
  else delete toastEl.dataset.variant;
  // Audit F-DCO-04: error variants need role="alert" + aria-live
  // "assertive" so screen readers interrupt to announce them. Everything
  // else stays polite.
  if (variant === 'error') {
    toastEl.setAttribute('role', 'alert');
    toastEl.setAttribute('aria-live', 'assertive');
  } else {
    toastEl.setAttribute('role', 'status');
    toastEl.setAttribute('aria-live', 'polite');
  }
  toastEl.dataset.visible = 'true';
  // Ensure any lingering action slot from a previous toastWithUndo is
  // cleared so plain toast() calls don't inherit a stale button.
  toastEl.dataset.hasAction = 'false';
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toastEl.dataset.visible = 'false';
  }, ms);
}

// R35.I2: toast with an Undo button. Used by destructive actions
// (delete entry, clear-today, delete weight, etc.) so the user has a
// 6-second grace window to reverse. The caller passes an async
// callback that reinstates the data; tapping Undo invokes it and
// dismisses the toast early.
let toastActionEl = null;
function toastWithUndo(text, onUndo, ms = 6000) {
  if (!toastEl) {
    toastEl = document.createElement('div');
    toastEl.className = 'app-toast';
    toastEl.setAttribute('role', 'status');
    toastEl.setAttribute('aria-live', 'polite');
    document.body.appendChild(toastEl);
  }
  if (!toastActionEl) {
    toastActionEl = document.createElement('button');
    toastActionEl.type = 'button';
    toastActionEl.className = 'app-toast-action';
    toastEl.appendChild(toastActionEl);
  } else if (toastActionEl.parentElement !== toastEl) {
    toastEl.appendChild(toastActionEl);
  }
  // Clear existing text nodes (the button stays); rebuild.
  for (const n of Array.from(toastEl.childNodes)) {
    if (n !== toastActionEl) toastEl.removeChild(n);
  }
  toastEl.insertBefore(document.createTextNode(String(text)), toastActionEl);
  toastActionEl.textContent = t('undo');
  toastEl.dataset.variant = 'warn';
  toastEl.dataset.visible = 'true';
  toastEl.dataset.hasAction = 'true';
  if (toastTimer) clearTimeout(toastTimer);
  const dismiss = () => {
    toastEl.dataset.visible = 'false';
    toastEl.dataset.hasAction = 'false';
    toastActionEl.onclick = null;
  };
  toastActionEl.onclick = async () => {
    dismiss();
    try { await onUndo(); } catch (err) { console.error('[undo]', err); }
  };
  toastTimer = setTimeout(dismiss, ms);
}

export { show, hide, toast, toastWithUndo };
