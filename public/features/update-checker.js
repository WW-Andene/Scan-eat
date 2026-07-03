import { apiUrl } from '../core/api-base.js';
/**
 * Auto-update checker (APK / Capacitor builds only).
 *
 * Compares the running build's git commit (public/version.json) against
 * the latest GitHub release that ships an .apk asset. If they differ
 * AND the user hasn't already dismissed that release tag, surface the
 * update banner with a one-tap install link.
 *
 * Web PWA builds skip the check entirely — service-worker swap handles
 * those updates without a banner.
 *
 * Storage:
 *   localStorage 'scanneat.dismissed_update' = the tag the user said
 *   "no" to. Cleared per-tag (so a newer release re-prompts).
 *
 * Lifecycle:
 *   initUpdateChecker(deps) starts an immediate check + a 12 h interval +
 *   a visibilitychange re-check. Returns a teardown function that clears
 *   the interval (called on pagehide so HMR / multi-tab cycles don't
 *   stack timers).
 */

const GITHUB_REPO = 'DarkLichtAeterna/Scan-eat';
const UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000;
const LS_DISMISSED_VERSION = 'scanneat.dismissed_update';

async function currentCommit() {
  try {
    const r = await fetch(apiUrl('/version.json'), { cache: 'no-cache' });
    if (!r.ok) return null;
    return (await r.json()).commit || null;
  } catch { return null; }
}

async function latestRelease() {
  try {
    const r = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`, {
      headers: { Accept: 'application/vnd.github+json' }, cache: 'no-cache',
    });
    if (!r.ok) return null;
    const rel = await r.json();
    const apk = (rel.assets || []).find((a) => /\.apk$/i.test(a.name));
    if (!apk) return null;
    return {
      tag: rel.tag_name,
      commit: (rel.tag_name || '').replace(/^build-/, ''),
      apkUrl: apk.browser_download_url,
    };
  } catch { return null; }
}

/**
 * Run a single update check. Pure of side effects beyond DOM mutation
 * (banner + version text). Safe to call from setInterval without await
 * because all paths swallow network errors.
 *
 * deps: { isCapacitor, updateBanner, updateVersionEl, updateInstallBtn,
 *         show }
 */
async function checkForUpdate(deps) {
  const { isCapacitor, updateBanner, updateVersionEl, updateInstallBtn, show } = deps;
  if (!isCapacitor) return;
  try {
    const [cur, latest] = await Promise.all([currentCommit(), latestRelease()]);
    if (!cur || !latest || latest.commit === cur) return;
    if (localStorage.getItem(LS_DISMISSED_VERSION) === latest.tag) return;
    updateVersionEl.textContent = latest.tag;
    updateInstallBtn.setAttribute('href', latest.apkUrl);
    show(updateBanner);
  } catch {
    // Network unavailable / GitHub rate limit / CDN issue — silently skip.
    // Called from setInterval + visibilitychange without await, so a reject
    // here would otherwise surface as an unhandled promise rejection.
  }
}

/**
 * Start the update-check loop. Returns a teardown closure.
 *
 * deps additionally accepts:
 *   updateDismissBtn — element; click marks the current tag dismissed
 *   updatePendingBanner — () => void; refresh offline-queue banner on visibility
 *   hide — el => void; hide helper
 */
export function initUpdateChecker(deps) {
  const {
    updateDismissBtn, updateVersionEl, updateBanner,
    updatePendingBanner, hide,
  } = deps;

  // Wire the "dismiss" button: persist the tag and hide the banner.
  updateDismissBtn?.addEventListener('click', () => {
    const tag = updateVersionEl?.textContent || '';
    if (tag) localStorage.setItem(LS_DISMISSED_VERSION, tag);
    hide(updateBanner);
  });

  // Initial fire + interval + visibility re-check.
  checkForUpdate(deps);
  let intervalId = setInterval(() => checkForUpdate(deps), UPDATE_CHECK_INTERVAL_MS);

  const onVisibility = () => {
    if (document.visibilityState === 'visible') {
      checkForUpdate(deps);
      updatePendingBanner?.();
    }
  };
  document.addEventListener('visibilitychange', onVisibility);

  // Hold the interval id so pagehide can clear it. Unbounded setInterval
  // stacking shows up under HMR / multiple-tab restores; a single clear
  // path on pagehide keeps us at one timer per tab lifetime.
  const teardown = () => {
    if (intervalId) { clearInterval(intervalId); intervalId = null; }
    document.removeEventListener('visibilitychange', onVisibility);
  };
  window.addEventListener('pagehide', teardown);
  return teardown;
}
