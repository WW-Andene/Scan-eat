/**
 * Appearance settings — theme + reading accessibility (font size,
 * font family, reduce-motion).
 *
 * Reads localStorage on init, paints the doc root + body classes, and
 * listens to the OS light-mode media query so the auto-theme re-paints
 * when the user flips their system setting.
 *
 * ADR-0004 feature-folder pattern. applyAppearance() is exported so
 * callers can re-paint after a Settings save without knowing internals.
 */

const LS_THEME = 'scanneat.theme';
const LS_FONT_SIZE = 'scanneat.fontSize';     // 'normal' | 'large' | 'xlarge'
const LS_FONT_FAMILY = 'scanneat.fontFamily'; // 'atkinson' | 'lexend' | 'system'
const LS_MOTION = 'scanneat.motion';           // 'normal' | 'reduced'

export function applyTheme() {
  // OLED noir is the default theme (2026-06): true-black surfaces, the
  // coral identity kept as an accent hint rather than a backdrop fill.
  // Anyone wanting the original coral-paper dark theme can still pick
  // "Sombre" explicitly in Settings.
  const pref = localStorage.getItem(LS_THEME) || 'oled';
  const mediaLight = pref === 'auto' && window.matchMedia?.('(prefers-color-scheme: light)').matches;
  const actual = pref === 'light' || mediaLight ? 'light' : (pref === 'dark' ? 'dark' : 'oled');
  document.documentElement.dataset.theme = actual;
}

export function applyReadingPrefs() {
  const body = document.body;
  const size = localStorage.getItem(LS_FONT_SIZE) || 'normal';
  const family = localStorage.getItem(LS_FONT_FAMILY) || 'atkinson';
  const motion = localStorage.getItem(LS_MOTION) || 'normal';

  body.classList.remove('font-size-large', 'font-size-xlarge');
  if (size === 'large')  body.classList.add('font-size-large');
  if (size === 'xlarge') body.classList.add('font-size-xlarge');

  body.classList.remove('font-lexend', 'font-system');
  if (family === 'lexend') body.classList.add('font-lexend');
  if (family === 'system') body.classList.add('font-system');

  body.classList.toggle('reduce-motion', motion === 'reduced');
}

export function applyAppearance() {
  applyTheme();
  applyReadingPrefs();
}

export function initAppearance() {
  applyAppearance();
  // Re-paint if the user flips their OS light/dark mode while we're on
  // the 'auto' setting.
  window.matchMedia?.('(prefers-color-scheme: light)')?.addEventListener('change', applyTheme);
  // F-CS-05: class alias so new dialogs can use class="modal-dialog"
  // (the intent-clearer name per audit) and still pick up the
  // existing .settings-dialog styling. Retire the shim once every
  // dialog.settings-dialog selector has been duplicated to also
  // match .modal-dialog.
  for (const el of document.querySelectorAll('.modal-dialog:not(.settings-dialog)')) {
    el.classList.add('settings-dialog');
  }
}
