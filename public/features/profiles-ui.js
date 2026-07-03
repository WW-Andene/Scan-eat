/**
 * Multi-profile status line + active/switch-select rendering.
 * ADR-0004 feature-folder pattern, extracted per the app.js
 * decomposition plan (Phase 9).
 *
 * Only setProfilesStatus and renderProfilesUI move here — the
 * #profiles-save / #profiles-switch / #profiles-delete click handlers
 * (and the telemetry block interleaved between them in app.js) stay in
 * app.js, calling back into this module's exports. This matches the
 * Target Architecture in the plan (§4): "features/profiles-ui.js ←
 * setProfilesStatus, renderProfilesUI" — no event-listener migration.
 *
 * Confirmed external consumer (not previously in the plan's H1–H8
 * list, found while extracting): settings-dialog.js receives
 * renderProfilesUI via deps.renderProfilesUI in the initSettingsDialog
 * call in app.js and calls it after a profile-related action inside
 * the settings dialog. No signature change — app.js re-exports the
 * same function reference it gets back from initProfilesUi(), so that
 * wiring needs no edit.
 *
 * deps shape: none — this module is self-contained (t, show, hide,
 * listProfiles, activeProfile are pure imports, no app.js state).
 *
 * DOM elements (#profiles-status, #profiles-active,
 * #profiles-switch-select) are looked up by id directly, matching the
 * existing convention in this cluster — no id renaming in this
 * restructuring (H6).
 */
import { show, hide } from '../core/dom-helpers.js';
import { listProfiles, activeProfile } from '../profiles.js';

const $ = (id) => document.getElementById(id);

function setProfilesStatus(text, state) {
  const el = $('profiles-status');
  if (!el) return;
  if (!text) { hide(el); return; }
  el.textContent = text;
  if (state) el.dataset.state = state;
  else delete el.dataset.state;
  show(el);
}

function renderProfilesUI() {
  const active = activeProfile();
  const list = listProfiles();
  const activeEl = $('profiles-active');
  if (activeEl) activeEl.textContent = active || '—';
  const sel = $('profiles-switch-select');
  if (sel) {
    sel.textContent = '';
    if (list.length === 0) {
      const opt = document.createElement('option');
      opt.value = '';
      opt.textContent = '—';
      sel.appendChild(opt);
    }
    for (const name of list) {
      const opt = document.createElement('option');
      opt.value = name;
      opt.textContent = name;
      if (name === active) opt.selected = true;
      sel.appendChild(opt);
    }
  }
}

export { setProfilesStatus, renderProfilesUI };

/**
 * Wires the #profiles-save / #profiles-switch / #profiles-delete
 * click handlers. Extracted from app.js (Phase 14) — these were left
 * behind by the Phase 9 extraction per this file's original header
 * note. Needs a deps object (unlike the rest of this module) because
 * switching profiles has to re-render several app.js-owned surfaces
 * that depend on the now-active profile.
 */
export function initProfilesDialog({
  t, getProfile,
  saveProfile, switchProfile, deleteProfile,
  renderRecentScans, renderDashboard, renderWeightSummary,
  applyTheme, applyReadingPrefs,
}) {
  $('profiles-save')?.addEventListener('click', async () => {
    const name = $('profiles-name')?.value.trim();
    if (!name) { setProfilesStatus(t('profilesNoName'), 'error'); return; }
    try {
      await saveProfile(name);
      renderProfilesUI();
      setProfilesStatus(t('profilesSaved', { name }));
    } catch (err) {
      console.error('[profiles save]', err);
      setProfilesStatus(err.message || String(err), 'error');
    }
  });

  $('profiles-switch')?.addEventListener('click', async () => {
    const name = $('profiles-switch-select')?.value;
    if (!name) return;
    try {
      await switchProfile(name);
      renderProfilesUI();
      setProfilesStatus(t('profilesSwitched', { name }));
      // Re-render everything that depends on profile / stored data.
      await renderRecentScans();
      await renderDashboard();
      await renderWeightSummary(getProfile());
      applyTheme();
      applyReadingPrefs();
    } catch (err) {
      console.error('[profiles switch]', err);
      setProfilesStatus(err.message || String(err), 'error');
    }
  });

  $('profiles-delete')?.addEventListener('click', () => {
    const name = $('profiles-switch-select')?.value;
    if (!name) return;
    // R-fix: every other destructive action in the app (custom food,
    // history, fasting log, meal plan, telemetry) gates on
    // window.confirm() first. Deleting a whole profile — wiping its
    // history/weight/recipes/settings snapshot in one irreversible
    // localStorage.removeItem() — had no such gate. One misclick on the
    // wrong <select> value silently destroyed the data.
    if (!window.confirm(t('profilesDeleteConfirm', { name }))) return;
    deleteProfile(name);
    renderProfilesUI();
    setProfilesStatus(t('profilesDeleted', { name }));
  });
}
