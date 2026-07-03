/**
 * Reminder scheduler — local, in-page only.
 *
 * Covers meal reminders (breakfast/lunch/dinner at set times),
 * hydration reminders (every N hours during waking hours), and
 * weight-log reminders (nudge after K days without a log).
 *
 * Follows the feature-folder pattern (ADR-0004). Reminders fire via
 * setTimeout while the tab is open; on fire, they prefer the browser
 * Notification API and fall back to an in-app toast if permission is
 * denied.
 *
 * Exports:
 *   initReminders(deps) — wires up scheduleReminders() + exposes it.
 *   scheduleReminders() — re-reads localStorage prefs + queues next fire.
 *
 * Deps shape:
 *   { t, toast, nextOccurrenceMs, listWeight?, getProfile? }
 *   listWeight + getProfile are optional — if provided, weight-log
 *   reminders fire. (Gap fix #8.)
 */

let deps = null;
const timers = [];

function notify(tag, body) {
  const { toast } = deps;
  if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
    try {
      new Notification('Scan\'eat', { body, icon: '/icon.svg', tag });
    } catch { toast(body); }
  } else {
    toast(body);
  }
}

function fireMealReminder(meal) {
  const { t } = deps;
  const body = t('reminderBody', { meal: t(
    meal === 'breakfast' ? 'mealBreakfast'
    : meal === 'lunch' ? 'mealLunch'
    : 'mealDinner',
  ).toLowerCase() });
  notify(`scanneat-reminder-${meal}`, body);
}

// Gap fix #8 — hydration reminder: nudges the user to drink water
// every N hours between wake and sleep times (or a sensible default
// 08:00-22:00 window).
function fireHydrationReminder() {
  const { t } = deps;
  notify('scanneat-reminder-hydration', t('reminderHydration'));
}

// Gap fix #8 — weight reminder: fires once a day if the user hasn't
// logged a weight in WEIGHT_REMINDER_MIN_DAYS. Piggybacks on the
// existing daily scheduler window.
const WEIGHT_REMINDER_MIN_DAYS = 4;
async function maybeFireWeightReminder() {
  const { t, listWeight } = deps;
  if (!listWeight) return;
  try {
    const all = await listWeight();
    const last = all && all.length ? all[all.length - 1] : null;
    if (!last?.timestamp) {
      // No prior weight — single reminder is useful but noisy;
      // skip unless explicitly on. The settings opt-in covers this.
      notify('scanneat-reminder-weight', t('reminderWeightFirst'));
      return;
    }
    const daysSince = (Date.now() - Number(last.timestamp)) / 86_400_000;
    if (daysSince >= WEIGHT_REMINDER_MIN_DAYS) {
      notify('scanneat-reminder-weight', t('reminderWeightGap', {
        n: Math.floor(daysSince),
      }));
    }
  } catch { /* best-effort */ }
}

export function scheduleReminders() {
  if (!deps) return;
  const { nextOccurrenceMs } = deps;
  // Clear any pending timers first — called on boot + after settings save.
  for (const id of timers) clearTimeout(id);
  timers.length = 0;

  // --- Meal reminders -------------------------------------------------------
  for (const meal of ['breakfast', 'lunch', 'dinner']) {
    const on = localStorage.getItem(`scanneat.reminder.${meal}.on`) === '1';
    if (!on) continue;
    const time = localStorage.getItem(`scanneat.reminder.${meal}.time`);
    if (!time) continue;
    const nextMs = nextOccurrenceMs(time, Date.now());
    if (nextMs == null) continue;
    const delay = Math.max(0, nextMs - Date.now());
    const id = setTimeout(() => {
      fireMealReminder(meal);
      scheduleReminders();
    }, delay);
    timers.push(id);
  }

  // --- Hydration reminder ---------------------------------------------------
  // Opt-in. Default cadence: 2 h. Honours a waking-hours window from
  // settings (default 08:00–22:00) so we don't buzz the user at 3am.
  if (localStorage.getItem('scanneat.reminder.hydration.on') === '1') {
    const everyHours = Math.max(1, Number(localStorage.getItem('scanneat.reminder.hydration.every_h')) || 2);
    const startTime = localStorage.getItem('scanneat.reminder.hydration.start') || '08:00';
    const endTime = localStorage.getItem('scanneat.reminder.hydration.end') || '22:00';
    const now = new Date();
    const [sh, sm] = startTime.split(':').map(Number);
    const [eh, em] = endTime.split(':').map(Number);
    const startMs = new Date(now.getFullYear(), now.getMonth(), now.getDate(), sh || 0, sm || 0).getTime();
    const endMs = new Date(now.getFullYear(), now.getMonth(), now.getDate(), eh || 0, em || 0).getTime();
    const nowMs = Date.now();
    let nextHydMs;
    if (nowMs < startMs) {
      nextHydMs = startMs;
    } else if (nowMs >= endMs) {
      // Past end of window — schedule tomorrow's start.
      nextHydMs = startMs + 24 * 3_600_000;
    } else {
      nextHydMs = nowMs + everyHours * 3_600_000;
      if (nextHydMs > endMs) nextHydMs = startMs + 24 * 3_600_000;
    }
    const id = setTimeout(() => {
      fireHydrationReminder();
      scheduleReminders();
    }, Math.max(0, nextHydMs - nowMs));
    timers.push(id);
  }

  // --- Weight-log reminder --------------------------------------------------
  // Opt-in. Fires at the configured time (default 08:00) and then only
  // notifies if the gap since last weight log exceeds the threshold.
  if (localStorage.getItem('scanneat.reminder.weight.on') === '1') {
    const time = localStorage.getItem('scanneat.reminder.weight.time') || '08:00';
    const nextMs = nextOccurrenceMs(time, Date.now());
    if (nextMs != null) {
      const id = setTimeout(() => {
        maybeFireWeightReminder();
        scheduleReminders();
      }, Math.max(0, nextMs - Date.now()));
      timers.push(id);
    }
  }
}

export function initReminders(injected) {
  deps = injected;
  scheduleReminders();
}
