/**
 * shareOrCopy — one-liner for the Web Share API with clipboard
 * fallback. Keeps the three share buttons (pairings / weekly / daily)
 * from each reinventing the same "if navigator.share try/catch else
 * clipboard else toast" dance.
 *
 * Behaviour:
 *   - Prefers navigator.share (mobile native sheet).
 *   - On AbortError (user dismissed the sheet), stays silent.
 *   - Otherwise copies to clipboard and fires the `copied` toast with the
 *     `'ok'` variant — so the success-accent stripe makes the copy
 *     feedback visually distinct from generic info toasts.
 *   - If clipboard writeText fails (permission / http), fires `failed`
 *     with the `'error'` variant.
 *
 * Contract:
 *   { title, text, toasts: { copied, failed }, toast(msg, variant) }
 */

export async function shareOrCopy({ title, text, toasts, toast }) {
  if (!text) return;
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text });
      return;
    } catch (err) {
      // AbortError = user cancelled the native sheet. Silent by design.
      if (err && err.name === 'AbortError') return;
      // Fall through to clipboard.
    }
  }
  try {
    await navigator.clipboard?.writeText(text);
    toast(toasts.copied, 'ok');
  } catch {
    toast(toasts.failed, 'error');
  }
}
