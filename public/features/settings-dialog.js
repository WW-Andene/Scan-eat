/**
 * Settings dialog — API key, mode, language, theme, font, motion,
 * meal reminders, telemetry opt-in, profiles list.
 *
 * Writes flow through the central app-settings shim so every value is
 * validated against its typed schema. On save, triggers a re-paint of
 * theme + reading prefs, re-schedules reminders, switches the active
 * locale, and re-runs applyStaticTranslations() so all data-i18n nodes
 * update immediately.
 *
 * ADR-0004 feature-folder pattern. Owns the button wiring, not the
 * state — state lives in localStorage / app-settings.js.
 *
 * Deps shape:
 *   { t, setLang, applyStaticTranslations,
 *     isCapacitor, currentLang,
 *     applyTheme, applyReadingPrefs,
 *     setSetting, scheduleReminders,
 *     renderProfilesUI, telemetryEnabled, setTelemetryEnabled,
 *     onLangChange() }
 *
 * onLangChange is invoked once after setLang + applyStaticTranslations so
 * the caller can re-run any dynamically built renderers (e.g. the
 * dashboard's life-stage chip, per-meal "% of day" labels) whose copy
 * doesn't live on data-i18n nodes.
 */

const LS_MODE = 'scanneat.mode';
const LS_THEME = 'scanneat.theme';
const LS_FONT_SIZE = 'scanneat.font_size';
const LS_FONT_FAMILY = 'scanneat.font_family';
const LS_MOTION = 'scanneat.motion';

function $(id) { return document.getElementById(id); }

export function initSettingsDialog(deps) {
  const {
    setLang, applyStaticTranslations,
    isCapacitor, currentLang,
    applyTheme, applyReadingPrefs,
    setSetting, scheduleReminders,
    renderProfilesUI, telemetryEnabled, setTelemetryEnabled,
    getKey, onLangChange,
    listScans,
  } = deps;

  const settingsBtn = $('settings-btn');
  const settingsDialog = $('settings-dialog');
  const keyInput = $('settings-key');
  const modeSelect = $('settings-mode');
  const langSelect = $('settings-language');
  const themeSelect = $('settings-theme');
  const settingsSave = $('settings-save');
  const settingsCancel = $('settings-cancel');

  settingsBtn?.addEventListener('click', () => {
    keyInput.value = getKey();
    modeSelect.value = localStorage.getItem(LS_MODE) || (isCapacitor ? 'direct' : 'auto');
    // The APK has no '/api/*' origin to call — 'auto' and 'server' modes
    // can never succeed there and just produce a confusing network error.
    // Hide them so 'direct' (Groq + OFF, no backend) is the only option.
    if (isCapacitor) {
      for (const opt of modeSelect.options) {
        if (opt.value !== 'direct') opt.hidden = true;
      }
      if (modeSelect.value !== 'direct') {
        modeSelect.value = 'direct';
        localStorage.setItem(LS_MODE, 'direct');
      }
    }
    langSelect.value = currentLang();
    themeSelect.value = localStorage.getItem(LS_THEME) || 'oled';
    const fontSizeSel = $('settings-font-size');
    const fontFamSel = $('settings-font-family');
    const motionSel = $('settings-motion');
    if (fontSizeSel) fontSizeSel.value = localStorage.getItem(LS_FONT_SIZE) || 'normal';
    if (fontFamSel)  fontFamSel.value  = localStorage.getItem(LS_FONT_FAMILY) || 'atkinson';
    if (motionSel)   motionSel.value   = localStorage.getItem(LS_MOTION) || 'normal';
    for (const meal of ['breakfast', 'lunch', 'dinner']) {
      const cb = $(`reminder-${meal}`);
      const tm = $(`reminder-${meal}-time`);
      if (cb) cb.checked = localStorage.getItem(`scanneat.reminder.${meal}.on`) === '1';
      if (tm) {
        const stored = localStorage.getItem(`scanneat.reminder.${meal}.time`);
        if (stored) tm.value = stored;
      }
    }
    // Gap fix #8 — hydration + weight reminder prefs.
    const hydCb = $('reminder-hydration');
    const hydEvery = $('reminder-hydration-every');
    if (hydCb) hydCb.checked = localStorage.getItem('scanneat.reminder.hydration.on') === '1';
    if (hydEvery) {
      const stored = localStorage.getItem('scanneat.reminder.hydration.every_h');
      if (stored) hydEvery.value = stored;
    }
    const wCb = $('reminder-weight');
    const wTm = $('reminder-weight-time');
    if (wCb) wCb.checked = localStorage.getItem('scanneat.reminder.weight.on') === '1';
    if (wTm) {
      const stored = localStorage.getItem('scanneat.reminder.weight.time');
      if (stored) wTm.value = stored;
    }
    renderProfilesUI?.();
    const telCb = $('telemetry-enabled');
    if (telCb) telCb.checked = telemetryEnabled();
    settingsDialog.showModal();
  });

  settingsSave?.addEventListener('click', (e) => {
    e.preventDefault();
    setSetting('scanneat.key', keyInput.value.trim());
    setSetting('scanneat.mode', modeSelect.value);
    setSetting('scanneat.theme', themeSelect.value);
    const fontSizeSel = $('settings-font-size');
    const fontFamSel = $('settings-font-family');
    const motionSel = $('settings-motion');
    if (fontSizeSel) setSetting('scanneat.fontSize', fontSizeSel.value);
    if (fontFamSel)  setSetting('scanneat.fontFamily', fontFamSel.value);
    if (motionSel)   setSetting('scanneat.motion', motionSel.value);
    // Reminder prefs — writing raw ('on'/time) because these aren't in
    // the setSetting schema yet (they're meal-scoped).
    let anyRemindersOn = false;
    for (const meal of ['breakfast', 'lunch', 'dinner']) {
      const cb = $(`reminder-${meal}`);
      const tm = $(`reminder-${meal}-time`);
      const on = !!cb?.checked;
      if (on) anyRemindersOn = true;
      localStorage.setItem(`scanneat.reminder.${meal}.on`, on ? '1' : '0');
      if (tm?.value) localStorage.setItem(`scanneat.reminder.${meal}.time`, tm.value);
    }
    // Gap fix #8 — persist hydration + weight reminder prefs.
    const hydCb = $('reminder-hydration');
    const hydEvery = $('reminder-hydration-every');
    const hydOn = !!hydCb?.checked;
    if (hydOn) anyRemindersOn = true;
    localStorage.setItem('scanneat.reminder.hydration.on', hydOn ? '1' : '0');
    if (hydEvery?.value) {
      const n = Math.max(1, Math.min(6, Number(hydEvery.value) || 2));
      localStorage.setItem('scanneat.reminder.hydration.every_h', String(n));
    }
    const wCb = $('reminder-weight');
    const wTm = $('reminder-weight-time');
    const wOn = !!wCb?.checked;
    if (wOn) anyRemindersOn = true;
    localStorage.setItem('scanneat.reminder.weight.on', wOn ? '1' : '0');
    if (wTm?.value) localStorage.setItem('scanneat.reminder.weight.time', wTm.value);
    // If at least one reminder is newly on, request Notification permission
    // (noop if already granted). Fire-and-forget.
    if (anyRemindersOn && typeof Notification !== 'undefined' && Notification.permission === 'default') {
      try { Notification.requestPermission(); } catch { /* noop */ }
    }
    scheduleReminders();
    const prevLang = currentLang();
    setLang(langSelect.value);
    applyTheme();
    applyReadingPrefs();
    // Telemetry opt-in checkbox, when present.
    const telCb = $('telemetry-enabled');
    if (telCb && setTelemetryEnabled) setTelemetryEnabled(telCb.checked);
    settingsDialog.close();
    applyStaticTranslations();
    // Dynamic renderers (dashboard life-stage chip, per-meal "% of day",
    // weekly view, etc.) live outside data-i18n — nudge the caller to
    // re-render them when the locale actually changed.
    if (onLangChange && currentLang() !== prevLang) onLangChange();
  });

  // ── Weight calibration ────────────────────────────────────────────────────
  const calibrationOutput = $('calibration-output');
  const calibrationPlaceholder = $('calibration-placeholder');
  const calibrationRunBtn = $('calibration-run');

  async function renderCalibration() {
    if (!calibrationOutput) return;
    calibrationPlaceholder && (calibrationPlaceholder.textContent = 'Calcul en cours…');
    try {
      const [{ calibrateWeights, CURRENT_WEIGHTS }, history] = await Promise.all([
        import('../core/weight-calibration.js'),
        listScans ? listScans() : Promise.resolve([]),
      ]);
      const result = calibrateWeights(history);
      if (result.insufficient) {
        calibrationOutput.innerHTML =
          `<p class="hint" id="calibration-placeholder">${result.note}</p>`;
        return;
      }
      // Build table
      const rows = result.pillars.map((p) => {
        const rhoStr = p.rho !== null ? p.rho.toFixed(2) : '—';
        const arrow = p.suggested_weight > p.current_weight ? '▲' : p.suggested_weight < p.current_weight ? '▼' : '=';
        return `<tr>
          <td>${pillarLabel(p.pillar)}</td>
          <td style="text-align:right">${p.current_weight}</td>
          <td style="text-align:right">${rhoStr}</td>
          <td style="text-align:right">${p.suggested_weight} <span aria-hidden="true">${arrow}</span></td>
        </tr>`;
      }).join('');
      calibrationOutput.innerHTML = `
        <p class="hint">${result.note}</p>
        <table style="width:100%;border-collapse:collapse;font-size:0.82em;margin:0.5rem 0">
          <thead>
            <tr style="text-align:left;border-bottom:1px solid var(--border,#444)">
              <th>Pilier</th>
              <th style="text-align:right">Poids actuel</th>
              <th style="text-align:right">ρ Nutri-Score</th>
              <th style="text-align:right">Poids suggéré</th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
        <p class="hint" style="margin-top:0.4rem">ρ : corrélation de Spearman (−1 à 1). Les poids suggérés sont proportionnels à |ρ|, plancher 5. Nutri-Score ne modélise pas les additifs ni NOVA — un ρ faible sur ces piliers est attendu.</p>`;
    } catch (err) {
      if (calibrationOutput) calibrationOutput.innerHTML =
        `<p class="hint">Erreur de calibration : ${err.message}</p>`;
    }
  }

  function pillarLabel(key) {
    return {
      processing: 'Niveau de transformation',
      nutritional_density: 'Densité nutritionnelle',
      negative_nutrients: 'Nutriments négatifs',
      additive_risk: 'Risque additifs',
      ingredient_integrity: 'Intégrité ingrédients',
    }[key] ?? key;
  }

  calibrationRunBtn?.addEventListener('click', renderCalibration);

  // Previously this ran on every Settings open (200ms after click,
  // racing the dialog animation) regardless of whether the user cared
  // about pillar calibration — an IndexedDB read + Spearman correlation
  // pass for a debug/power-user table that's now tucked into a
  // collapsed <details> most people will never open. Removed: it now
  // only computes when the section is actually expanded.
  $('calibration-fieldset')?.addEventListener('toggle', (e) => {
    if (e.target.open && calibrationPlaceholder && !calibrationPlaceholder.dataset.computed) {
      calibrationPlaceholder.dataset.computed = '1';
      renderCalibration();
    }
  });

  settingsCancel?.addEventListener('click', (e) => {
    e.preventDefault();
    settingsDialog.close();
  });
}
