/**
 * Custom-foods dialog list rendering + per-day note rendering + the
 * Day/Week/Month dashboard view toggle.
 *
 * ADR-0004 feature-folder pattern, extracted per the app.js decomposition
 * plan (Phase 10). Per the Target Architecture (plan §4): "custom-foods-
 * day-notes.js ← renderCustomFoodsList, renderDayNote, applyViewToggle" —
 * only these three render/toggle functions move. Matching the precedent
 * set in Phase 9 (profiles-ui.js), the surrounding click handlers
 * (#custom-foods-btn, #cf-close, #cf-save, #day-note-input's 'input'
 * listener, the .view-tab click wiring) stay in app.js and call back
 * into this module's exports — no event-listener migration bundled in.
 *
 * State ownership note (not in the original H1–H8 list, found while
 * extracting): `dashboardView` (+ its localStorage persistence key and
 * the day/week/month enum) was a module-level variable in app.js that
 * only applyViewToggle ever wrote, but which app.js's boot sequence also
 * read once (to decide whether to restore Week view on load). Per the
 * same reasoning as H4 (queue state), splitting applyViewToggle out
 * without giving dashboardView a single home would either fork the
 * state or force app.js to keep owning it and pass it back in on every
 * call. We give it a home here and expose getDashboardView() for the
 * one external read site.
 *
 * Wiring convention: renderWeeklyView, renderMonthlyView, and
 * renderDashboard are NOT yet extracted (dashboard-charts.js is Phase
 * 11, not yet run) — they still live in app.js. Per the project's
 * deps-object convention (no importing app.js internals), this module
 * receives them via initCustomFoodsDayNotes(deps) and app.js must call
 * that once at boot, after those three functions are defined.
 *
 * DOM elements are looked up by id directly (#cf-list, #day-note-input,
 * #day-note-counter, .view-tab, #weekly-view, #monthly-view,
 * #dashboard-rows, #dashboard-log) — matching the existing convention
 * in this cluster. No id renaming in this restructuring (H6).
 */
import { show, hide, toast } from '../core/dom-helpers.js';
import { t } from '../core/i18n.js';
import { listCustomFoods, deleteCustomFood, saveCustomFood } from '../data/custom-food-db.js';
import { getDayNote, DAY_NOTE_MAX_CHARS } from './day-notes.js';
import { todayISO } from '../data/consumption.js';

const $ = (id) => document.getElementById(id);

let _deps = null;

export function initCustomFoodsDayNotes(deps) {
  _deps = deps;
}

// ----------------------------------------------------------------------
// Custom foods dialog — list rendering only (add/delete handlers and the
// dialog open/close wiring stay in app.js, calling renderCustomFoodsList()
// after any mutation).
// ----------------------------------------------------------------------

/**
 * Custom-foods dialog open/close/save wiring (Phase 14 — was left in
 * app.js by Phase 10 per this file's original header note; moved here
 * now that the pattern of co-locating a feature's render fn + its
 * dialog wiring in one module is well-established elsewhere, e.g.
 * profiles-ui.js). Call once at boot.
 */
export function initCustomFoodsDialog() {
  const customFoodsDialog = $('custom-foods-dialog');

  $('custom-foods-btn')?.addEventListener('click', () => {
    renderCustomFoodsList();
    // Reset the add form
    for (const id of ['cf-name', 'cf-kcal', 'cf-protein', 'cf-carbs', 'cf-fat']) {
      const el = $(id); if (el) el.value = '';
    }
    customFoodsDialog?.showModal();
    // R17.4: pre-focus the name input so the user can start typing
    // immediately. Defer one tick so showModal() finishes layout first.
    setTimeout(() => $('cf-name')?.focus(), 0);
  });
  $('cf-close')?.addEventListener('click', (e) => { e.preventDefault(); customFoodsDialog?.close(); });
  $('cf-save')?.addEventListener('click', () => {
    const name = ($('cf-name')?.value || '').trim();
    const kcal = Number($('cf-kcal')?.value) || 0;
    if (!name || kcal <= 0) { toast(t('customFoodNeedsNameKcal'), 'warn'); return; }
    // R34.N3: when the user clicked a row to edit, the name field
    // carries a data-editing-id attr. Passing `id` upserts (saveCustomFood
    // finds by id and replaces) instead of creating a duplicate.
    const editingId = $('cf-name')?.dataset.editingId || undefined;
    const saved = saveCustomFood({
      id: editingId,
      name,
      kcal,
      protein_g: Number($('cf-protein')?.value) || 0,
      carbs_g:   Number($('cf-carbs')?.value)   || 0,
      fat_g:     Number($('cf-fat')?.value)     || 0,
    });
    if (saved) {
      toast(t(editingId ? 'customFoodUpdatedToast' : 'customFoodSavedToast', { name: saved.name }), 'ok');
      for (const id of ['cf-name', 'cf-kcal', 'cf-protein', 'cf-carbs', 'cf-fat']) {
        const el = $(id); if (el) el.value = '';
      }
      if ($('cf-name')) delete $('cf-name').dataset.editingId;
      renderCustomFoodsList();
      // R17.5: after-save focus back to name so the user can chain
      // entries without touch-navigation between macros and name.
      $('cf-name')?.focus();
    }
  });
}

export function renderCustomFoodsList() {
  const list = $('cf-list');
  if (!list) return;
  list.textContent = '';
  const all = listCustomFoods().slice().sort((a, b) => a.name.localeCompare(b.name));
  if (all.length === 0) {
    const empty = document.createElement('li');
    empty.className = 'tpl-empty';
    empty.textContent = t('customFoodsEmpty');
    list.appendChild(empty);
    return;
  }
  for (const f of all) {
    const li = document.createElement('li');
    li.className = 'tpl-item';
    const label = document.createElement('button');
    label.type = 'button';
    label.className = 'custom-food-label';
    // R34.N3: click a row's label to pre-fill the add form for
    // in-place editing. Saving with the same name upserts via
    // saveCustomFood's id-lookup branch (buildCustomFood adds an
    // id only if not provided — we preserve it by passing the
    // existing food wholesale when the name is unchanged).
    label.textContent = t('customFoodRow', {
      name: f.name,
      kcal: Math.round(f.kcal),
      prot: Math.round(f.protein_g),
      carb: Math.round(f.carbs_g),
      fat: Math.round(f.fat_g),
    });
    label.addEventListener('click', () => {
      $('cf-name').value = f.name;
      $('cf-kcal').value = String(Math.round(f.kcal));
      $('cf-protein').value = String(Math.round(f.protein_g));
      $('cf-carbs').value = String(Math.round(f.carbs_g));
      $('cf-fat').value = String(Math.round(f.fat_g));
      $('cf-name').focus();
      // Stash the id on the form so the save handler upserts instead
      // of creating a duplicate.
      $('cf-name').dataset.editingId = f.id;
    });
    const del = document.createElement('button');
    del.type = 'button';
    del.className = 'secondary';
    del.textContent = t('customFoodDelete');
    del.addEventListener('click', () => {
      // R17.2: confirm before deleting — previously a single
      // misclick wiped the entry with no undo path.
      if (!window.confirm(t('customFoodDeleteConfirm', { name: f.name }))) return;
      deleteCustomFood(f.id);
      renderCustomFoodsList();
    });
    li.appendChild(label);
    li.appendChild(del);
    list.appendChild(li);
  }
}

// ----------------------------------------------------------------------
// Day note — render only (the #day-note-input 'input' listener that
// writes via setDayNote() stays in app.js).
// ----------------------------------------------------------------------

export function renderDayNote() {
  const input = $('day-note-input');
  const counter = $('day-note-counter');
  if (!input) return;
  const current = getDayNote(todayISO());
  // Avoid stomping the user's caret position on every re-render. Only
  // repopulate when what's on screen differs from storage (initial load
  // or another tab updated it).
  if (input.value !== current) input.value = current;
  if (counter) counter.textContent = `${input.value.length} / ${DAY_NOTE_MAX_CHARS}`;
}

// ----------------------------------------------------------------------
// Day / Week / Month dashboard view toggle.
// ----------------------------------------------------------------------

const LS_DASHBOARD_VIEW = 'scanneat.dashboard.view';
const DASHBOARD_VIEWS = new Set(['day', 'week', 'month']);
const _storedView = localStorage.getItem(LS_DASHBOARD_VIEW);
let dashboardView = DASHBOARD_VIEWS.has(_storedView) ? _storedView : 'day';

export function getDashboardView() {
  return dashboardView;
}

export function applyViewToggle(view) {
  dashboardView = DASHBOARD_VIEWS.has(view) ? view : 'day';
  try { localStorage.setItem(LS_DASHBOARD_VIEW, dashboardView); } catch { /* quota */ }
  for (const btn of document.querySelectorAll('.view-tab')) {
    const isActive = btn.dataset.view === dashboardView;
    btn.classList.toggle('active', isActive);
    btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
  }
  const weeklyEl = $('weekly-view');
  const monthlyEl = $('monthly-view');
  const rowsEl = $('dashboard-rows');
  const logEl = $('dashboard-log');
  if (dashboardView === 'week') {
    hide(rowsEl); hide(logEl); hide(monthlyEl);
    _deps.renderWeeklyView();
    show(weeklyEl);
  } else if (dashboardView === 'month') {
    hide(rowsEl); hide(logEl); hide(weeklyEl);
    _deps.renderMonthlyView();
    show(monthlyEl);
  } else {
    hide(weeklyEl); hide(monthlyEl);
    show(rowsEl);
    // Keep log visibility driven by renderDashboard; re-run it to refresh.
    _deps.renderDashboard();
  }
}
