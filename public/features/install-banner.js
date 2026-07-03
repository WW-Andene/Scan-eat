/**
 * PWA install-prompt banner.
 *
 * We catch `beforeinstallprompt` and reveal a small dismissible banner.
 * Respects a localStorage snooze so users who tapped "plus tard" don't
 * get re-prompted on every page load.
 *
 * Note: the browser only fires the event when engagement heuristics are
 * met (SW installed, manifest valid, ~30 s interaction). Nothing to do
 * if it never fires — the aside stays hidden.
 *
 * ADR-0004 feature-folder pattern. Single init function; no renderX
 * counterpart because the banner's visibility is event-driven.
 *
 * Deps shape: { show, hide }
 */

const SNOOZE_KEY = 'scanneat.installBanner.snoozedUntil';
const SNOOZE_DAYS = 30;

let deferredPrompt = null;

function $(id) { return document.getElementById(id); }

function shouldShow() {
  const until = Number(localStorage.getItem(SNOOZE_KEY) || 0);
  return !Number.isFinite(until) || until < Date.now();
}

function snooze(days = SNOOZE_DAYS) {
  localStorage.setItem(SNOOZE_KEY, String(Date.now() + days * 86_400_000));
}

export function initInstallBanner({ show, hide }) {
  const banner = $('install-banner');
  const accept = $('install-banner-accept');
  const dismiss = $('install-banner-dismiss');

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    if (banner && shouldShow()) show(banner);
  });

  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    if (banner) hide(banner);
    // Hard-snooze so we never prompt again on this device.
    snooze(365 * 10);
  });

  accept?.addEventListener('click', async () => {
    if (!deferredPrompt) { if (banner) hide(banner); return; }
    deferredPrompt.prompt();
    try {
      const { outcome } = await deferredPrompt.userChoice;
      if (outcome === 'dismissed') snooze(7);
    } catch { /* user agent error; fall through */ }
    deferredPrompt = null;
    if (banner) hide(banner);
  });

  dismiss?.addEventListener('click', () => {
    snooze();
    if (banner) hide(banner);
  });
}
