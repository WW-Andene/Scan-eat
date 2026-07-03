/**
 * Bottom tab navigation — replaces "one giant scrolling page" with four
 * real destinations: Scan (capture + result), Journal (today's log +
 * history), Plan (meal plan / recipes / templates / custom foods hub),
 * Progress (weight / trends hub).
 *
 * Implementation: every top-level section already carries a
 * data-tab="scan|journal|plan|progress" attribute (see index.html).
 * Switching tabs just toggles `hidden` on those groups + updates the
 * tab-bar's active state and aria-selected. No DOM is moved, so every
 * existing feature module's getElementById() calls keep working
 * unchanged regardless of which tab is active.
 *
 * Persisted in localStorage so re-opening the PWA lands back where the
 * user left off, except the very first boot (always starts on Scan —
 * the app's core action).
 */

const LS_TAB = 'scanneat.active_tab';
const TABS = ['scan', 'journal', 'plan', 'progress'];

function $(id) { return document.getElementById(id); }

export function setActiveTab(tab) {
  if (!TABS.includes(tab)) tab = 'scan';
  for (const el of document.querySelectorAll('[data-tab]')) {
    el.hidden = el.dataset.tab !== tab;
  }
  for (const btn of document.querySelectorAll('.tab-bar-btn')) {
    const active = btn.dataset.tabTarget === tab;
    btn.classList.toggle('active', active);
    btn.setAttribute('aria-selected', String(active));
  }
  try { localStorage.setItem(LS_TAB, tab); } catch { /* private mode */ }
  // Let other modules (e.g. dashboard render-on-visible) react.
  document.dispatchEvent(new CustomEvent('scaneat:tab-change', { detail: { tab } }));
}

export function initTabNav() {
  const bar = $('tab-bar');
  if (!bar) return;
  bar.addEventListener('click', (e) => {
    const btn = e.target.closest('.tab-bar-btn');
    if (!btn) return;
    setActiveTab(btn.dataset.tabTarget);
  });
  let initial = 'scan';
  try {
    const stored = localStorage.getItem(LS_TAB);
    if (stored && TABS.includes(stored)) initial = stored;
  } catch { /* private mode */ }
  setActiveTab(initial);
}

/** Programmatic jump used by other features (e.g. a scan result should
 * always land on the Scan tab even if the user was on Journal). */
export function goToTab(tab) { setActiveTab(tab); }
