/**
 * Fasting completion log — persists each finished fast so the tile can
 * show a streak and a history list. The live timer is still driven by the
 * two scanneat.fasting.start / .target localStorage keys in app.js; this
 * module only captures the completion record.
 *
 * Storage: localStorage 'scanneat.fasting.history' as a JSON array, most
 * recent last. Capped at 100 entries (FIFO eviction) so storage doesn't
 * grow unboundedly — a power user fasting 20h/day still has a year of
 * history within that cap.
 *
 * Schema per entry:
 *   { id, start_ms, end_ms, target_hours, duration_ms, complete }
 * complete = duration_ms >= target_hours * 3_600_000
 */

const KEY = 'scanneat.fasting.history';
const MAX_ENTRIES = 100;

function readAll() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function writeAll(arr) {
  try {
    localStorage.setItem(KEY, JSON.stringify(arr));
  } catch {
    // Quota or serialization; best-effort.
  }
}

/** Build a completion record from the live-timer inputs. Pure for tests. */
export function buildFastCompletion({ start_ms, end_ms = Date.now(), target_hours }) {
  const start = Number(start_ms);
  const end = Number(end_ms);
  const target = Number(target_hours);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return null;
  const duration_ms = end - start;
  const complete = Number.isFinite(target) && target > 0
    ? duration_ms >= target * 3_600_000
    : false;
  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `f${end}${Math.random().toString(36).slice(2)}`),
    start_ms: start,
    end_ms: end,
    target_hours: Number.isFinite(target) && target > 0 ? target : null,
    duration_ms,
    complete,
  };
}

export function listFastHistory() {
  return readAll();
}

export function saveFastCompletion(record) {
  if (!record) return;
  const all = readAll();
  all.push(record);
  // FIFO eviction — drop the oldest when we exceed the cap.
  while (all.length > MAX_ENTRIES) all.shift();
  writeAll(all);
  return record;
}

export function clearFastHistory() {
  writeAll([]);
}

/**
 * Streak = number of completed fasts in a row from the most recent entry
 * backwards. A non-complete fast (< target) resets it. Pure for tests.
 */
export function computeFastStreak(history) {
  if (!Array.isArray(history) || history.length === 0) return 0;
  let streak = 0;
  for (let i = history.length - 1; i >= 0; i -= 1) {
    if (history[i]?.complete) streak += 1; else break;
  }
  return streak;
}
