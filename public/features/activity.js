/**
 * Activity (exercise) feature — self-contained module.
 *
 * Owns the dashboard tile (#activity-tile + entries list) and the
 * "Add exercise" dialog (#activity-dialog). Storage is /data/activity.js
 * (IDB v6 store 'activity'); this module is the UI/wiring layer.
 *
 * Pattern matches /features/hydration.js — initActivity({...}) at boot,
 * renderActivity() exposed for the dashboard render loop. All
 * dependencies are passed in so the module stays decoupled from
 * app.js internals (no circular import).
 *
 * Returned by renderActivity:
 *   { kcal, minutes, count }  — today's totals, or null if the tile
 *   isn't in the DOM. The dashboard subtracts kcal from the day's
 *   target for the MFP-style Net + Remaining lines.
 */

// Map IDB-stored snake_case activity keys to the i18n key suffix.
// Explicit table — never string-munge into i18n keys.
const LABEL_KEY = {
  walking_brisk: 'activityTypeWalking',
  running:       'activityTypeRunning',
  cycling:       'activityTypeCycling',
  swimming:      'activityTypeSwimming',
  strength:      'activityTypeStrength',
  yoga:          'activityTypeYoga',
  hiit:          'activityTypeHiit',
  other:         'activityTypeOther',
};

let deps = null;

function $(id) { return document.getElementById(id); }
function show(el) { if (el) el.removeAttribute('hidden'); }
function hide(el) { if (el) el.setAttribute('hidden', ''); }

function activityTypeLabel(type) {
  const { t } = deps;
  return t(LABEL_KEY[type] || 'activityTypeOther');
}

export async function renderActivity() {
  if (!deps) return null;
  const { t, listActivityByDate, sumBurned, deleteActivity, renderDashboard, activityDelete } = deps;
  const tile = $('activity-tile');
  const summary = $('activity-summary');
  const list = $('activity-entries');
  if (!tile || !summary) return null;
  const entries = await listActivityByDate().catch(() => []);
  const b = sumBurned(entries);
  if (b.count === 0) {
    summary.textContent = t('activitySummaryEmpty');
  } else {
    summary.textContent = t('activitySummary', { min: b.minutes, kcal: b.kcal });
  }
  if (list) {
    list.textContent = '';
    if (entries.length === 0) {
      hide(list);
    } else {
      for (const e of entries.slice().sort((a, b) => b.timestamp - a.timestamp)) {
        const li = document.createElement('li');
        li.className = 'act-entry';
        const span = document.createElement('span');
        span.textContent = t('activityEntryRow', {
          type: activityTypeLabel(e.type),
          min: e.minutes,
          kcal: e.kcal_burned,
        });
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'act-entry-del';
        del.setAttribute('aria-label', t(activityDelete ?? 'activityDelete'));
        del.textContent = '×';
        del.addEventListener('click', async () => {
          await deleteActivity(e.id);
          await renderDashboard();
        });
        li.appendChild(span);
        li.appendChild(del);
        list.appendChild(li);
      }
      show(list);
    }
  }
  return b;
}

function updateEstimate() {
  const { t, getProfile, estimateKcalBurned } = deps;
  const out = $('a-estimate');
  if (!out) return;
  const type = $('a-type')?.value || 'walking_brisk';
  const minutes = Number($('a-minutes')?.value) || 0;
  const override = Number($('a-kcal')?.value) || 0;
  const weightKg = Number(getProfile()?.weight_kg) || 0;
  if (override > 0) { out.textContent = t('activityEstimate', { kcal: Math.round(override) }); return; }
  if (weightKg <= 0) { out.textContent = t('activityEstimateNeedWeight'); return; }
  if (minutes <= 0) { out.textContent = ''; return; }
  const kcal = estimateKcalBurned(type, minutes, weightKg);
  out.textContent = t('activityEstimate', { kcal });
}

export function initActivity(injected) {
  deps = injected;
  const dialog = $('activity-dialog');
  const { t, getProfile, buildActivityEntry, logActivity, renderDashboard, toast } = deps;

  $('activity-add')?.addEventListener('click', () => {
    if (!dialog) return;
    $('a-minutes').value = '';
    $('a-kcal').value = '';
    $('a-note').value = '';
    updateEstimate();
    dialog.showModal();
  });
  $('a-type')?.addEventListener('change', updateEstimate);
  $('a-minutes')?.addEventListener('input', updateEstimate);
  $('a-kcal')?.addEventListener('input', updateEstimate);
  $('a-close')?.addEventListener('click', () => dialog?.close());
  $('a-save')?.addEventListener('click', async (e) => {
    e.preventDefault();
    const type = $('a-type')?.value || 'walking_brisk';
    const minutes = Number($('a-minutes')?.value) || 0;
    if (minutes <= 0) return;
    const kcalOverride = Number($('a-kcal')?.value) || 0;
    const note = $('a-note')?.value || '';
    const entry = buildActivityEntry({
      type, minutes, weightKg: getProfile()?.weight_kg, kcalOverride, note,
    });
    await logActivity(entry);
    dialog?.close();
    // R16.2: success toast adopts the 'ok' variant for the green
    // accent stripe — same pattern as R8.3 normalisation.
    toast(t('activityToast', {
      type: activityTypeLabel(type),
      min: minutes,
      kcal: entry.kcal_burned,
    }), 'ok');
    await renderDashboard();
  });
}
