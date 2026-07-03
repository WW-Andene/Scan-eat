/**
 * Fasting timer — intermittent-fasting countdown.
 *
 * State persists in localStorage so the clock survives reloads + app
 * restarts. Ticks once a minute (skipped under reduce-motion).
 *
 * ADR-0004 feature-folder pattern. Owns its render loop + button
 * handlers; app.js triggers the initial render via renderFasting().
 *
 * Deps shape:
 *   { t, currentLang, show, hide,
 *     fastingStatus,
 *     buildFastCompletion, saveFastCompletion,
 *     listFastHistory, computeFastStreak, clearFastHistory }
 */

import { dateFormatter, localeFor } from '../core/date-format.js';

const LS_START = 'scanneat.fasting.start';
const LS_TARGET = 'scanneat.fasting.target';

let deps = null;
let tick = null;

function $(id) { return document.getElementById(id); }

function getState() {
  const startRaw = localStorage.getItem(LS_START);
  const targetRaw = localStorage.getItem(LS_TARGET);
  const start = Number(startRaw);
  const target = Number(targetRaw);
  if (!Number.isFinite(start) || start <= 0) return null;
  return {
    start_ms: start,
    target_hours: Number.isFinite(target) && target > 0 ? target : 16,
  };
}

/**
 * Is a fast currently in progress (within its target window)? Past the
 * target hour the fast is in its "completion window" and this returns
 * false — the UI should not nag at logging time once the fast is done.
 *
 * Exported so app.js + other features don't have to read fasting.js's
 * private localStorage keys directly. Previously app.js duplicated this
 * inline, which silently broke whenever the storage keys changed (R27).
 */
export function isFastingActive(now = Date.now()) {
  const s = getState();
  if (!s) return false;
  const elapsedH = (now - s.start_ms) / 3_600_000;
  return elapsedH >= 0 && elapsedH < s.target_hours;
}

function start(targetHours) {
  localStorage.setItem(LS_START, String(Date.now()));
  localStorage.setItem(LS_TARGET, String(targetHours));
}

function stop() {
  const { buildFastCompletion, saveFastCompletion } = deps;
  // Snapshot the completed fast into history before clearing the timer
  // state so the streak chip reflects what just happened.
  const s = getState();
  if (s) {
    const rec = buildFastCompletion({
      start_ms: s.start_ms,
      end_ms: Date.now(),
      target_hours: s.target_hours,
    });
    if (rec) saveFastCompletion(rec);
  }
  localStorage.removeItem(LS_START);
  localStorage.removeItem(LS_TARGET);
}

function renderStreak() {
  const { t, currentLang, show, hide, listFastHistory, computeFastStreak } = deps;
  const streakEl = $('fasting-streak');
  const wrap = $('fasting-history-wrap');
  const list = $('fasting-history-list');
  const hist = listFastHistory();
  if (streakEl) {
    const n = computeFastStreak(hist);
    if (n >= 1) {
      streakEl.textContent = t('fastingStreak', { n });
      show(streakEl);
    } else {
      hide(streakEl);
    }
  }
  if (wrap && list) {
    if (hist.length === 0) {
      hide(wrap);
    } else {
      list.textContent = '';
      for (const r of hist.slice().reverse()) { // newest first
        const li = document.createElement('li');
        const hours = Math.floor(r.duration_ms / 3_600_000);
        const mins = Math.floor((r.duration_ms % 3_600_000) / 60_000);
        const date = dateFormatter(localeFor(currentLang()), {
          day: '2-digit', month: 'short',
        }).format(new Date(r.end_ms));
        li.textContent = t(r.complete ? 'fastingHistoryLineOk' : 'fastingHistoryLineKo', {
          date, h: hours, m: String(mins).padStart(2, '0'),
          target: r.target_hours ?? '—',
        });
        list.appendChild(li);
      }
      show(wrap);
    }
  }
}

export function renderFasting() {
  if (!deps) return;
  const { t, show, hide, fastingStatus } = deps;
  const tile = $('fasting-tile');
  const startRow = $('fasting-start-row');
  const amt = $('fasting-amount');
  const fill = $('fasting-fill');
  const stateEl = $('fasting-state');
  if (!tile || !startRow) return;

  renderStreak();

  const s = getState();
  if (!s) {
    hide(tile);
    show(startRow);
    if (tick) { clearInterval(tick); tick = null; }
    return;
  }

  const st = fastingStatus(s.start_ms, Date.now(), s.target_hours);
  if (amt) amt.textContent = st.label;
  if (fill) {
    fill.style.width = `${st.pct}%`;
    if (st.complete) fill.dataset.state = 'done';
    else delete fill.dataset.state;
  }
  if (stateEl) {
    if (st.complete) {
      const overH = Math.floor(st.overrun_ms / 3_600_000);
      const overM = Math.floor((st.overrun_ms % 3_600_000) / 60_000);
      stateEl.textContent = overH > 0 || overM > 0
        ? `${t('fastingComplete')} · ${t('fastingOverrun', { h: overH, m: String(overM).padStart(2, '0') })}`
        : t('fastingComplete');
    } else {
      stateEl.textContent = t('fastingInProgress');
    }
  }
  show(tile);
  hide(startRow);
  // Tick once a minute — fine for a countdown measured in hours. Skipped
  // under reduce-motion for users who prefer no animated counters.
  if (!tick && !document.body.classList.contains('reduce-motion')) {
    tick = setInterval(renderFasting, 60_000);
  }
}

export function initFasting(injected) {
  deps = injected;
  // Battery-friendly: pause the per-minute tick when the tab is hidden.
  // A 16-hour fast doesn't need wall-clock updates while the user has
  // another tab focused; we re-render on visibilitychange back to
  // visible so the chip catches up immediately.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      if (tick) { clearInterval(tick); tick = null; }
    } else {
      renderFasting();
    }
  });
  const { clearFastHistory } = deps;
  $('fasting-start')?.addEventListener('click', () => {
    const target = Number($('fasting-target')?.value) || 16;
    start(target);
    renderFasting();
  });
  $('fasting-stop')?.addEventListener('click', () => {
    // R19.1: stopping a fast that hasn't completed marks it as a
    // failed attempt in history. A stray tap after 12 h of a 16 h
    // fast used to silently break the streak — confirm now gates
    // any incomplete stop.
    const { t: t2 } = deps;
    const s = getState();
    if (s) {
      const hours = (Date.now() - s.start_ms) / 3_600_000;
      if (hours < s.target_hours) {
        if (!window.confirm(t2('fastingStopConfirm', {
          h: Math.floor(hours),
          m: String(Math.floor((hours - Math.floor(hours)) * 60)).padStart(2, '0'),
          target: s.target_hours,
        }))) return;
      }
    }
    stop();
    renderFasting();
  });
  $('fasting-history-clear')?.addEventListener('click', () => {
    // Tier-2 destructive: history is used by the streak chip. Confirm
    // so the user can't accidentally wipe weeks of data.
    const { t, listFastHistory } = deps;
    const n = listFastHistory().length;
    if (n === 0) return;
    if (!window.confirm(t('fastingHistoryClearConfirm', { n }))) return;
    clearFastHistory();
    renderStreak();
  });
}
