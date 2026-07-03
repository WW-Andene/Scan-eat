/**
 * Date utilities — local-time ISO conversion.
 *
 * Extracted so `todayISO()` / `isoToday()` share one implementation
 * across consumption / activity / weight-log / meal-plan. Earlier each
 * module re-implemented it inline, and the initial implementation used
 * `new Date().toISOString().slice(0,10)` — which returns the UTC day,
 * not the user's local day. For a food tracker, user-local bucketing is
 * the only sensible contract: users in UTC+3 or greater had late-evening
 * meals bucketed on the next calendar day.
 *
 * Pure. No Intl-date allocation cost concerns for the call rate we have
 * (one call per entry write + a handful per render).
 */

/**
 * Local-day ISO string (YYYY-MM-DD) for `now` (epoch ms, defaults to
 * Date.now()).
 *
 * Uses Intl.DateTimeFormat('en-CA') because its default output is
 * already YYYY-MM-DD — no manual zero-padding needed. Modern browsers +
 * Node 22 all support it.
 */
export function localDateISO(now = Date.now()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(now));
  const y = parts.find((p) => p.type === 'year')?.value ?? '0000';
  const m = parts.find((p) => p.type === 'month')?.value ?? '00';
  const d = parts.find((p) => p.type === 'day')?.value ?? '00';
  return `${y}-${m}-${d}`;
}
