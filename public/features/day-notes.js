/**
 * Per-day free-text notes — mood, training, cycle phase, medication, etc.
 *
 * Storage: localStorage keys 'scanneat.note.YYYY-MM-DD'. Notes are never
 * transmitted anywhere; they stay on-device with the rest of the app's
 * state and flow through backup.js because it sweeps all scanneat.* keys.
 *
 * Cap: 500 chars per day. Longer is truncated on save — we're intentionally
 * not a journaling app, just a single-line context slot.
 */

const PREFIX = 'scanneat.note.';
const MAX_CHARS = 500;

export function getDayNote(date) {
  if (!date) return '';
  return localStorage.getItem(PREFIX + date) || '';
}

export function setDayNote(date, text) {
  if (!date) return;
  const trimmed = String(text ?? '').slice(0, MAX_CHARS);
  if (trimmed.length === 0) {
    localStorage.removeItem(PREFIX + date);
  } else {
    try { localStorage.setItem(PREFIX + date, trimmed); } catch { /* quota */ }
  }
}

export function listDayNoteDates() {
  const out = [];
  for (let i = 0; i < localStorage.length; i += 1) {
    const k = localStorage.key(i);
    if (k && k.startsWith(PREFIX)) out.push(k.slice(PREFIX.length));
  }
  return out.sort();
}

export { MAX_CHARS as DAY_NOTE_MAX_CHARS };
