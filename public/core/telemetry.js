/**
 * Local-only, opt-in telemetry. NEVER sent to any server. Useful for:
 *   - User sharing a failure log with the developer voluntarily
 *   - Debugging a sticky parse issue without re-running the scan
 *   - Personal insight ("how often does X fail?")
 *
 * Capped at 50 events (FIFO eviction) so it never dominates the LS quota.
 *
 * NO network. The feature name is "telemetry" only in the industry sense —
 * here it's a local log with zero external exfiltration.
 */

const LS_ENABLED = 'scanneat.telemetry.enabled';
const LS_EVENTS = 'scanneat.telemetry.events';
const MAX_EVENTS = 50;

export function isEnabled() {
  return localStorage.getItem(LS_ENABLED) === '1';
}

export function setEnabled(flag) {
  if (flag) localStorage.setItem(LS_ENABLED, '1');
  else localStorage.removeItem(LS_ENABLED);
}

export function listEvents() {
  try {
    const raw = localStorage.getItem(LS_EVENTS);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}

/**
 * Append an event if telemetry is enabled. No-op otherwise.
 * Records only strings (no images, no product data, no personal info).
 */
export function logEvent(type, message, context) {
  if (!isEnabled()) return;
  const events = listEvents();
  events.push({
    ts: new Date().toISOString(),
    type: String(type || 'info'),
    message: String(message || '').slice(0, 500),
    context: context == null ? null : String(context).slice(0, 300),
  });
  while (events.length > MAX_EVENTS) events.shift();
  try { localStorage.setItem(LS_EVENTS, JSON.stringify(events)); } catch { /* quota */ }
}

export function clearEvents() {
  localStorage.removeItem(LS_EVENTS);
}

/** Serialise for clipboard / file export. Plain-text report, one line per
 *  event, sorted newest-first. */
export function formatEvents() {
  const events = listEvents().slice().reverse();
  if (events.length === 0) return '(no events)\n';
  return events.map((e) =>
    `[${e.ts}] ${e.type.toUpperCase()} — ${e.message}` +
    (e.context ? ` (${e.context})` : ''),
  ).join('\n') + '\n';
}
