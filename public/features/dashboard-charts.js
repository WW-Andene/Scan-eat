/**
 * Daily/weekly/monthly dashboard rendering, the gap-closer suggestion
 * panel, the 30-day SVG line-chart primitive, and the progress-charts
 * dialog (weight/kcal/water trends). ADR-0004 feature-folder pattern,
 * extracted per the app.js decomposition plan (Phase 11).
 *
 * This is the largest extracted cluster by dependent count: renderDashboard
 * is consumed via deps.renderDashboard by SEVEN other already-extracted
 * modules (activity.js, weight.js, menu-scan.js, recipes-dialog.js,
 * templates-dialog.js, backup-io.js, meal-plan-ui.js) plus app.js's own
 * onLangChange callback and boot sequence. None of those call sites
 * change — app.js re-exports the same function reference it gets back
 * from this module, same property name (`renderDashboard`) in every
 * deps object that already expected it (H1-style — no signature change).
 * Likewise `round1` continues to flow into initWeight's deps unchanged.
 *
 * renderWeeklyView / renderMonthlyView / renderDashboard are also the
 * three functions custom-foods-day-notes.js's applyViewToggle calls via
 * its own deps object (wired by initCustomFoodsDayNotes() in app.js,
 * Phase 10). That wiring needs no change either — app.js now sources
 * those three names from this module's import instead of a local
 * function declaration, but passes them into initCustomFoodsDayNotes()
 * exactly as before.
 *
 * State ownership note (found while extracting, not in the original
 * H1–H8 list): renderDashboard's per-entry edit button writes to
 * `editingEntry`, a module-level variable in app.js that the Quick-Add
 * save handler (staying in app.js) also reads/writes. Same shape as H4
 * (queue) — rather than fork that state, this module receives a
 * `setEditingEntry` setter via initDashboardCharts(deps) and never reads
 * editingEntry itself (write-only from this side, matching the
 * scan-history-ui.js `setLastData` precedent).
 *
 * deps shape: { setEditingEntry }  ← (entry) => void
 *
 * DOM elements (#daily-dashboard, #dashboard-rows, #dashboard-entries,
 * #dashboard-log, #dashboard-date, #dashboard-remaining, #gap-closer,
 * #gap-closer-list, #progress-*-chart, #progress-*-summary, #qa-*,
 * #quick-add-dialog, #quick-add-dialog-title) are looked up by id
 * directly, matching the existing convention in this cluster — no id
 * renaming in this restructuring (H6). The aria-busy skeleton markers
 * set once at boot (app.js lines near the original dashboardRows const)
 * stay in app.js untouched — they're boot sequencing, not part of any
 * moved function.
 */
import { show, hide, toast, toastWithUndo } from '../core/dom-helpers.js';
import { t, currentLang } from '../core/i18n.js';
import { dateFormatter, localeFor } from '../core/date-format.js';
import { localDateISO } from '../core/dateutil.js';
import { getProfile, dailyTargets } from '../data/profile.js';
import {
  listByDate, listAllEntries, dailyTotals, deleteEntry, putEntry,
  groupByMeal, MEALS, todayISO,
} from '../data/consumption.js';
import { listWeight } from '../data/weight-log.js';
import { listCustomFoods } from '../data/custom-food-db.js';
import { FOOD_DB } from '../data/food-db.js';
import {
  weeklyRollup, monthlyRollup, weekOverWeekDelta, logStreakDays,
  dashboardRowsFrom, pctClass, closeTheGap, buildLineChartPath,
} from '../core/presenters.js';
import { renderHydration } from './hydration.js';
import { renderActivity } from './activity.js';
import { renderWeightSummary } from './weight.js';
import { renderFasting } from './fasting.js';
import { renderDayNote } from './custom-foods-day-notes.js';

const $ = (id) => document.getElementById(id);

let _deps = null;

export function initDashboardCharts(deps) {
  _deps = deps;
}

export async function renderWeeklyView() {
  const root = $('weekly-view');
  const bars = $('weekly-bars');
  const summary = $('weekly-summary');
  if (!root || !bars || !summary) return;

  const all = await listAllEntries().catch(() => []);
  const roll = weeklyRollup(all, todayISO());
  const profile = getProfile();
  const targets = dailyTargets(profile);
  const kcalTarget = targets?.kcal ?? 0;
  const peak = Math.max(kcalTarget, ...roll.days.map((d) => d.kcal), 1);

  // Summary line — avg / total / days logged
  summary.textContent = '';
  const mkChip = (labelKey, value) => {
    const d = document.createElement('div');
    d.className = 'ws-item';
    const l = document.createElement('span');
    l.className = 'ws-label';
    l.textContent = t(labelKey);
    const v = document.createElement('span');
    v.className = 'ws-value';
    v.textContent = value;
    d.appendChild(l); d.appendChild(v);
    return d;
  };
  summary.appendChild(mkChip('weeklyAvgKcal', `${Math.round(roll.avg.kcal)} kcal`));
  summary.appendChild(mkChip('weeklyTotalKcal', `${Math.round(roll.total.kcal)} kcal`));
  summary.appendChild(mkChip('weeklyDaysLogged', t('weeklyDaysLogged', { n: roll.days_logged })));

  // Gap fix #11: week-over-week delta chip. Compare this week's avg
  // kcal / avg protein to last week's; surface up to two deltas so
  // the user sees drift without scrolling to monthly view.
  const priorEnd = localDateISO(Date.now() - 7 * 86_400_000);
  const prior = weeklyRollup(all, priorEnd);
  if (prior.days_logged > 0) {
    const delta = weekOverWeekDelta(roll, prior);
    const fmtPct = (p) => (p === null ? '—' : (p > 0 ? `+${p}%` : `${p}%`));
    // Show kcal delta (primary) + protein delta (secondary, since
    // macro balance is typically the user's goal axis).
    const kcalChip = mkChip('weeklyVsPriorKcal', fmtPct(delta.kcal.pct));
    if (delta.kcal.pct !== null) kcalChip.dataset.direction = delta.kcal.pct > 0 ? 'up' : delta.kcal.pct < 0 ? 'down' : 'flat';
    summary.appendChild(kcalChip);
    const protChip = mkChip('weeklyVsPriorProtein', fmtPct(delta.protein.pct));
    if (delta.protein.pct !== null) protChip.dataset.direction = delta.protein.pct > 0 ? 'up' : delta.protein.pct < 0 ? 'down' : 'flat';
    summary.appendChild(protChip);
  }

  // Streak chip: consecutive days logged ending at today. Use the same
  // logStreakDays presenter the dashboard uses, so the two agree.
  const streak = logStreakDays(all, todayISO());
  if (streak > 0) {
    summary.appendChild(mkChip('weeklyStreakLabel', t('streakDays', { n: streak })));
  }

  // Bar chart — one column per day
  bars.textContent = '';
  const dayFmt = dateFormatter(localeFor(currentLang), { weekday: 'narrow' });
  for (const d of roll.days) {
    const wrap = document.createElement('div');
    wrap.className = 'wbar';
    const isEmpty = d.count === 0;
    const isOver = kcalTarget > 0 && d.kcal > kcalTarget;
    if (isEmpty) wrap.dataset.empty = 'true';
    if (isOver) wrap.dataset.over = 'true';

    // Tooltip + aria-label: the actual per-day macro breakdown. Native
    // `title` works for mouse hover + touch long-press on most mobile
    // browsers; aria-label covers screen readers regardless.
    const date = new Date(d.date + 'T12:00:00Z');
    const dateFull = dateFormatter(localeFor(currentLang), {
      weekday: 'long', day: 'numeric', month: 'long',
    }).format(date);
    const tooltip = isEmpty
      ? `${dateFull} — ${t('weekViewTooltipEmpty')}`
      : t('weekViewTooltip', {
          date: dateFull,
          kcal: Math.round(d.kcal),
          prot: Math.round(d.protein_g),
          carb: Math.round(d.carbs_g),
          fat: Math.round(d.fat_g),
        });
    wrap.title = tooltip;
    wrap.setAttribute('aria-label', tooltip);
    wrap.tabIndex = 0; // focusable for keyboard users → screen reader reads aria-label

    const col = document.createElement('span');
    col.className = 'wbar-col';
    const heightPct = Math.max(2, (d.kcal / peak) * 100);
    col.style.height = `${heightPct}%`;
    const dayLabel = document.createElement('span');
    dayLabel.className = 'wbar-label';
    dayLabel.textContent = dayFmt.format(date);
    const valLabel = document.createElement('span');
    valLabel.className = 'wbar-val';
    valLabel.textContent = isEmpty ? '—' : Math.round(d.kcal);
    wrap.appendChild(col);
    wrap.appendChild(dayLabel);
    wrap.appendChild(valLabel);
    bars.appendChild(wrap);
  }
}

// 30-day view — same look as the 7-day bars, sourced from monthlyRollup.
// Day labels omitted (too crowded at 30 columns); fall back to a dot
// under each bar and the full date lives in title / aria-label.
export async function renderMonthlyView() {
  const root = $('monthly-view');
  const bars = $('monthly-bars');
  const summary = $('monthly-summary');
  if (!root || !bars || !summary) return;

  const all = await listAllEntries().catch(() => []);
  const roll = monthlyRollup(all, todayISO());
  const profile = getProfile();
  const targets = dailyTargets(profile);
  const kcalTarget = targets?.kcal ?? 0;
  const peak = Math.max(kcalTarget, ...roll.days.map((d) => d.kcal), 1);

  summary.textContent = '';
  const mkChip = (labelKey, value) => {
    const d = document.createElement('div');
    d.className = 'ws-item';
    const l = document.createElement('span');
    l.className = 'ws-label';
    l.textContent = t(labelKey);
    const v = document.createElement('span');
    v.className = 'ws-value';
    v.textContent = value;
    d.appendChild(l); d.appendChild(v);
    return d;
  };
  summary.appendChild(mkChip('weeklyAvgKcal', `${Math.round(roll.avg.kcal)} kcal`));
  summary.appendChild(mkChip('weeklyTotalKcal', `${Math.round(roll.total.kcal)} kcal`));
  summary.appendChild(mkChip('monthlyDaysLogged', t('monthlyDaysLogged', { n: roll.days_logged })));
  // On-goal chip — same ±10% math the share presenter uses (R6.4) so
  // the view and the share block always agree.
  if (kcalTarget > 0 && roll.days_logged > 0) {
    const onGoal = roll.days.filter(
      (d) => d.count > 0 && Math.abs(d.kcal - kcalTarget) <= kcalTarget * 0.1,
    ).length;
    summary.appendChild(mkChip(
      'monthlyOnGoal',
      `${onGoal}/${roll.days_logged}`,
    ));
  }

  bars.textContent = '';
  bars.classList.add('wbars-30');
  for (const d of roll.days) {
    const wrap = document.createElement('div');
    wrap.className = 'wbar wbar-thin';
    const isEmpty = d.count === 0;
    const isOver = kcalTarget > 0 && d.kcal > kcalTarget;
    if (isEmpty) wrap.dataset.empty = 'true';
    if (isOver) wrap.dataset.over = 'true';
    const date = new Date(d.date + 'T12:00:00Z');
    const dateFull = dateFormatter(localeFor(currentLang), {
      weekday: 'long', day: 'numeric', month: 'long',
    }).format(date);
    const tooltip = isEmpty
      ? `${dateFull} — ${t('weekViewTooltipEmpty')}`
      : t('weekViewTooltip', {
          date: dateFull, kcal: Math.round(d.kcal),
          prot: Math.round(d.protein_g), carb: Math.round(d.carbs_g), fat: Math.round(d.fat_g),
        });
    wrap.title = tooltip;
    wrap.setAttribute('aria-label', tooltip);
    wrap.tabIndex = 0;
    const col = document.createElement('span');
    col.className = 'wbar-col';
    col.style.height = `${Math.max(2, (d.kcal / peak) * 100)}%`;
    wrap.appendChild(col);
    bars.appendChild(wrap);
  }
}

export async function renderDashboard() {
  const dashboardEl = $('daily-dashboard');
  const dashboardRows = $('dashboard-rows');
  const dashboardEntries = $('dashboard-entries');
  const dashboardLog = $('dashboard-log');
  const dashboardDateEl = $('dashboard-date');
  const dashboardRemainingEl = $('dashboard-remaining');
  const quickAddDialog = $('quick-add-dialog');

  const profile = getProfile();
  const targets = dailyTargets(profile);
  const entries = await listByDate().catch(() => []);
  const totals = await dailyTotals().catch(() => null);
  // Gap fix #10: clear skeletons once we have the IDB read back; both
  // success and empty-dashboard branches below drop the busy marker.
  dashboardRows?.removeAttribute('aria-busy');
  dashboardEntries?.removeAttribute('aria-busy');
  if (!totals) { hide(dashboardEl); return; }

  if (totals.count === 0 && !targets) { hide(dashboardEl); return; }
  renderHydration();
  renderFasting();
  renderDayNote();
  const burned = await renderActivity();

  dashboardDateEl.textContent = dateFormatter(localeFor(currentLang), {
    weekday: 'short', day: 'numeric', month: 'short',
  }).format(new Date());

  // Streak: small positive-reinforcement line. Only shown at 2+ consecutive
  // days so a single-day user isn't greeted with "1 day streak" nag.
  try {
    const streakEl = $('dashboard-streak');
    if (streakEl) {
      const allEntries = await listAllEntries().catch(() => []);
      const streak = logStreakDays(allEntries, todayISO());
      if (streak >= 2) {
        streakEl.textContent = t('streakDays', { n: streak });
        show(streakEl);
        // Milestone celebration — 7/30/100/365 day streaks get a
        // brief success-burst pulse on the streak element + accent
        // flash. Remembers which milestones fired today so a
        // single-day dashboard refresh doesn't re-celebrate.
        const MILESTONES = [7, 30, 100, 365];
        const fired = (localStorage.getItem('scanneat.streakFired') || '').split(',');
        if (MILESTONES.includes(streak) && !fired.includes(String(streak) + ':' + todayISO())) {
          streakEl.classList.remove('milestone-pulse');
          void streakEl.offsetWidth;
          streakEl.classList.add('milestone-pulse');
          setTimeout(() => streakEl.classList.remove('milestone-pulse'), 1400);
          fired.push(String(streak) + ':' + todayISO());
          // Cap the list so it doesn't grow forever; keep only today.
          localStorage.setItem(
            'scanneat.streakFired',
            fired.filter(f => f.endsWith(':' + todayISO())).join(',')
          );
        }
      } else {
        hide(streakEl);
      }
    }
  } catch { /* streak is decorative; never fail the dashboard render */ }

  // "Remaining" line (MFP-style Remaining = Goal − Food + Exercise).
  if (targets) {
    const rem = [];
    const remKcal = targets.kcal - totals.kcal + (burned?.kcal || 0);
    rem.push(`${Math.round(remKcal)} kcal`);
    if (targets.free_sugars_g_max > 0) rem.push(`${round1(targets.free_sugars_g_max - totals.sugars_g)} g ${t('dashSugars').toLowerCase()}`);
    if (targets.salt_g_max > 0) rem.push(`${round3(targets.salt_g_max - totals.salt_g)} g ${t('dashSalt').toLowerCase()}`);
    dashboardRemainingEl.textContent = `${t('dashRemaining')} : ${rem.join(' · ')}`;
    show(dashboardRemainingEl);
  } else {
    hide(dashboardRemainingEl);
  }

  await renderWeightSummary(profile);

  // R11.2: row shape + conditional micros now live in
  // dashboardRowsFrom (pure presenter). The DOM loop below is
  // unchanged; the builder is testable under node:test without a
  // jsdom shim.
  const rows = dashboardRowsFrom(totals, targets);

  dashboardRows.textContent = '';
  for (const row of rows) {
    const li = document.createElement('li');
    li.className = 'dash-row';
    const label = document.createElement('span');
    label.className = 'dash-label';
    label.textContent = t(row.key);
    const bar = document.createElement('span');
    bar.className = 'dash-bar';
    const fill = document.createElement('span');
    fill.className = 'dash-fill';
    let pct = 0;
    if (row.target && row.target > 0) {
      pct = Math.min(200, (row.value / row.target) * 100);
      fill.style.width = `${Math.min(100, pct)}%`;
      fill.dataset.state = pctClass(pct);
    } else {
      fill.style.width = '0%';
    }
    bar.appendChild(fill);
    const val = document.createElement('strong');
    val.className = 'dash-value';
    if (row.target) {
      val.textContent = `${row.value} / ${row.target} ${row.unit} (${Math.round(pct)} %)`;
    } else {
      val.textContent = `${row.value} ${row.unit}`;
    }
    li.appendChild(label);
    li.appendChild(bar);
    li.appendChild(val);
    dashboardRows.appendChild(li);
    // Net-kcal line directly under the kcal row when exercise was logged.
    if (row.key === 'dashKcal' && (burned?.kcal || 0) > 0) {
      const net = document.createElement('li');
      net.className = 'dash-net';
      net.textContent = t('netKcalLine', { net: Math.round(totals.kcal - burned.kcal) });
      dashboardRows.appendChild(net);
    }
    // Life-stage kcal chip directly under kcal row — reminds the user
    // that today's target includes the EFSA pregnancy / lactation
    // uplift, not just their baseline TDEE. Only shown when life_stage
    // is set (dailyTargets surfaces it).
    if (row.key === 'dashKcal' && targets?.life_stage) {
      const chip = document.createElement('li');
      chip.className = 'dash-lifestage-chip';
      const delta = targets.life_stage === 'pregnancy' ? 300 : 500;
      chip.textContent = targets.life_stage === 'pregnancy'
        ? t('lifeStageChipPregnancy', { delta })
        : t('lifeStageChipLactation', { delta });
      dashboardRows.appendChild(chip);
    }
  }

  // Per-meal entry list
  dashboardEntries.textContent = '';
  if (entries.length === 0) {
    const p = document.createElement('p');
    p.className = 'dash-entry-empty';
    p.textContent = t('dashEmpty');
    dashboardEntries.appendChild(p);
    hide(dashboardLog);
  } else {
    const grouped = groupByMeal(entries);
    const mealLabels = {
      breakfast: t('mealBreakfast'),
      lunch: t('mealLunch'),
      dinner: t('mealDinner'),
      snack: t('mealSnack'),
    };
    for (const m of MEALS) {
      const bucket = grouped[m];
      if (bucket.entries.length === 0) continue;
      const section = document.createElement('section');
      section.className = 'meal-section';
      // data-meal lets CSS give each meal its own time-of-day
      // visual cue (morning warmth / daylight / evening dim / snack).
      section.dataset.meal = m;
      const header = document.createElement('div');
      header.className = 'meal-header';
      const name = document.createElement('strong');
      name.textContent = mealLabels[m];
      const kcal = document.createElement('span');
      kcal.className = 'meal-kcal';
      // Append "(X% of day)" when the user has a kcal target set, so the
      // meal's share of the day is visible without mental arithmetic.
      // Uses the targets already fetched for the dashboard summary above.
      const dailyKcal = targets?.kcal || 0;
      const pct = dailyKcal > 0 ? Math.round((bucket.totals.kcal / dailyKcal) * 100) : 0;
      kcal.textContent = pct > 0
        ? `${Math.round(bucket.totals.kcal)} kcal · ${pct}% ${t('mealOfDayShort')}`
        : `${Math.round(bucket.totals.kcal)} kcal`;
      header.appendChild(name);
      header.appendChild(kcal);
      section.appendChild(header);
      if (bucket.totals.protein_g + bucket.totals.carbs_g + bucket.totals.fat_g > 0) {
        const macroLine = document.createElement('p');
        macroLine.className = 'meal-macros';
        macroLine.textContent = t('mealMacros', {
          prot: Math.round(bucket.totals.protein_g),
          carb: Math.round(bucket.totals.carbs_g),
          fat: Math.round(bucket.totals.fat_g),
        });
        section.appendChild(macroLine);
      }
      const ul = document.createElement('ul');
      ul.className = 'meal-entries';
      for (const e of bucket.entries.slice().sort((a, b) => b.timestamp - a.timestamp)) {
        const li = document.createElement('li');
        li.className = 'dash-entry';
        const info = document.createElement('div');
        info.className = 'dash-entry-info';
        const nm = document.createElement('span');
        nm.className = 'dash-entry-name';
        nm.textContent = e.quickAdd
          ? `${e.product_name} · ${t('quickAdd')}`
          : `${e.product_name} · ${e.grams} g`;
        info.appendChild(nm);
        // Fix #3: entry's recipe grade (when the entry came from
        // "Apply recipe") — same color-blind-safe pattern as the
        // recipes-list grade chip.
        if (e.recipe_grade) {
          const g = document.createElement('span');
          g.className = 'recipe-row-grade';
          g.dataset.grade = e.recipe_grade;
          g.textContent = e.recipe_grade;
          g.title = `${e.recipe_grade} · ${e.recipe_score ?? ''} / 100`;
          info.appendChild(g);
        }
        if ((e.protein_g || 0) + (e.carbs_g || 0) + (e.fat_g || 0) > 0) {
          const macros = document.createElement('span');
          macros.className = 'dash-entry-macros';
          macros.textContent = t('entryMacros', {
            prot: Math.round(e.protein_g || 0),
            carb: Math.round(e.carbs_g || 0),
            fat: Math.round(e.fat_g || 0),
          });
          info.appendChild(macros);
        }
        const k = document.createElement('strong');
        k.textContent = `${Math.round(e.kcal)} kcal`;
        // R35.I1+I9: edit button opens Quick Add pre-filled so the
        // user can tweak grams / macros / meal without delete+re-log.
        // Reuses the existing Quick Add form (it already has every
        // field the entry carries) instead of building a second
        // dialog.
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'dash-entry-edit';
        edit.setAttribute('aria-label', e.product_name
          ? `${t('editEntry')} — ${e.product_name}`
          : t('editEntry'));
        edit.textContent = '✎';
        edit.addEventListener('click', () => {
          _deps.setEditingEntry(e);
          $('qa-name').value = e.product_name || '';
          $('qa-kcal').value = String(Math.round(e.kcal || 0));
          $('qa-carbs').value = String(Math.round(e.carbs_g || 0));
          $('qa-protein').value = String(Math.round(e.protein_g || 0));
          $('qa-fat').value = String(Math.round(e.fat_g || 0));
          $('qa-satfat').value = String(Math.round(e.sat_fat_g || 0));
          $('qa-sugars').value = String(Math.round(e.sugars_g || 0));
          $('qa-salt').value = String((e.salt_g || 0).toFixed(2));
          $('qa-fiber').value = String(Math.round(e.fiber_g || 0));
          if ($('qa-meal')) $('qa-meal').value = e.meal || 'snack';
          const title = $('quick-add-dialog-title');
          if (title) title.textContent = t('editEntryTitle');
          // Fix #7 — read-only micros line when the entry carries
          // any. Users see that editing won't erase micros; the
          // save path still preserves them on the upserted record.
          const microLine = $('qa-micros-readonly');
          if (microLine) {
            const MICRO_SUMMARY = [
              ['iron_mg', 'Fe', 'mg'], ['calcium_mg', 'Ca', 'mg'],
              ['magnesium_mg', 'Mg', 'mg'], ['potassium_mg', 'K', 'mg'],
              ['zinc_mg', 'Zn', 'mg'], ['vit_c_mg', 'C', 'mg'],
              ['vit_d_ug', 'D', 'µg'], ['b12_ug', 'B12', 'µg'],
              ['omega_3_g', 'ω3', 'g'],
            ];
            const parts = MICRO_SUMMARY
              .filter(([k]) => (Number(e[k]) || 0) > 0)
              .map(([k, label, unit]) => `${label} ${Math.round(e[k] * 10) / 10} ${unit}`);
            if (parts.length > 0) {
              microLine.textContent = t('qaMicrosReadOnly', { items: parts.join(' · ') });
              microLine.hidden = false;
            } else {
              microLine.hidden = true;
            }
          }
          quickAddDialog.showModal();
        });
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'dash-entry-del';
        del.setAttribute('aria-label', e.product_name
          ? `${t('deleteEntry')} — ${e.product_name}`
          : t('deleteEntry'));
        del.textContent = '×';
        del.addEventListener('click', async () => {
          // R35.I2: undo-toast. Snapshot the entry before deletion
          // and let the user restore it in a 6 s window. putEntry
          // is upsert-by-id so the restore reinstates the row with
          // its original id / timestamp / date — completely intact.
          const snapshot = { ...e };
          await deleteEntry(e.id);
          await renderDashboard();
          toastWithUndo(
            t('entryDeleted', { name: e.product_name || '—' }),
            async () => {
              await putEntry(snapshot);
              await renderDashboard();
              toast(t('entryRestored'), 'ok');
            },
          );
        });
        li.appendChild(info);
        li.appendChild(k);
        li.appendChild(edit);
        li.appendChild(del);
        ul.appendChild(li);
      }
      section.appendChild(ul);
      dashboardEntries.appendChild(section);
    }
    // Diary Complete chip: something logged in each of the 3 main meals.
    const main3 = ['breakfast', 'lunch', 'dinner']
      .every((k) => grouped[k].entries.length > 0);
    if (main3) {
      const chip = document.createElement('p');
      chip.className = 'diary-complete';
      chip.textContent = t('diaryComplete');
      dashboardEntries.appendChild(chip);
    }
    show(dashboardLog);
  }

  renderGapCloser(totals, targets);

  show(dashboardEl);
}

export function renderGapCloser(totals, targets) {
  const section = $('gap-closer');
  const list = $('gap-closer-list');
  if (!section || !list) return;
  const all = [...FOOD_DB, ...listCustomFoods()];
  const gaps = closeTheGap(totals, targets, all);
  list.textContent = '';
  const loggedToday = (totals?.count ?? 0) > 0;
  if (!loggedToday) {
    hide(section);
    return;
  }
  // F-F-06: when every "more is better" nutrient target is met, show a
  // celebration tile instead of hiding the section. Rewards the user
  // at their signature moment and keeps dashboard rhythm consistent.
  if (gaps.length === 0) {
    const item = document.createElement('li');
    item.className = 'gap-closer-item gap-closer-item-success';
    const head = document.createElement('p');
    head.className = 'gap-closer-head gap-closer-head-success';
    head.textContent = t('gapCloserAllMet');
    item.appendChild(head);
    list.appendChild(item);
    show(section);
    return;
  }
  for (const g of gaps) {
    const item = document.createElement('li');
    item.className = 'gap-closer-item';
    const head = document.createElement('p');
    head.className = 'gap-closer-head';
    head.textContent = t(`gapCloser_${g.nutrient}`, { deficit: g.deficit });
    item.appendChild(head);
    const chips = document.createElement('ul');
    chips.className = 'gap-closer-chips';
    // Units per nutrient for the hover tooltip. Keeps the chip label
    // tight ("amandes · 38 g") while the `title` attr spells out exactly
    // why this food was picked + how much it delivers.
    const unitFor = { protein: 'g', fiber: 'g', iron: 'mg', calcium: 'mg', vit_d: 'µg', b12: 'µg' }[g.nutrient] || '';
    for (const s of g.suggestions) {
      const li = document.createElement('li');
      li.className = 'gap-closer-chip';
      li.textContent = `${s.name} · ${s.grams} g`;
      // Tooltip sequence: "[nutrient delivered] — [density per 100 g]"
      li.title = t('gapCloserTooltip', {
        value: s.contribution, unit: unitFor,
        deficit: g.deficit, nutrient: t(`gapCloserNutrient_${g.nutrient}`),
      });
      li.setAttribute('aria-label', li.title);
      chips.appendChild(li);
    }
    item.appendChild(chips);
    list.appendChild(item);
  }
  show(section);
}

export function round1(x) { return Math.round(x * 10) / 10; }
export function round3(x) { return Math.round(x * 1000) / 1000; }

// ============================================================================
// Progress charts — 30-day trend for weight, kcal, hydration.
// ============================================================================

const SVG_NS = 'http://www.w3.org/2000/svg';

export function renderLineChart(container, values, opts = {}) {
  if (!container) return;
  container.textContent = '';
  const numeric = values.filter((v) => typeof v === 'number' && Number.isFinite(v));
  if (numeric.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'pc-empty';
    empty.textContent = t('progressNoData');
    container.appendChild(empty);
    return { min: null, max: null };
  }
  const width = 300;
  const height = 120;
  const { path_d, min, max, points } = buildLineChartPath(values, {
    width, height, padding: 10,
  });
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.setAttribute('role', 'img');

  // F-DDV-01 — chart a11y. Give the SVG a real name via <title>,
  // describe the trend in <desc>, and cross-reference both with
  // aria-labelledby/-describedby. Never fall back to the literal
  // string "chart" — if callers don't provide ariaLabel, we at
  // least say "Chart: N points" so screen readers get a number.
  const ariaName = opts.ariaLabel || `Chart (${numeric.length} points)`;
  const delta = numeric[numeric.length - 1] - numeric[0];
  const direction = delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat';
  const descText = `${numeric.length} points. Min ${round1(min)}, max ${round1(max)}. Trend ${direction}${delta !== 0 ? ' by ' + Math.abs(round1(delta)) : ''}${opts.unit ? ' ' + opts.unit : ''}.`;
  const uid = 'pc-' + Math.random().toString(36).slice(2, 8);
  const titleEl = document.createElementNS(SVG_NS, 'title');
  titleEl.setAttribute('id', uid + '-t');
  titleEl.textContent = ariaName;
  const descEl = document.createElementNS(SVG_NS, 'desc');
  descEl.setAttribute('id', uid + '-d');
  descEl.textContent = descText;
  svg.appendChild(titleEl);
  svg.appendChild(descEl);
  svg.setAttribute('aria-labelledby', uid + '-t');
  svg.setAttribute('aria-describedby', uid + '-d');

  if (path_d) {
    const line = document.createElementNS(SVG_NS, 'path');
    line.setAttribute('class', 'pc-line');
    line.setAttribute('d', path_d);
    svg.appendChild(line);
    // Dot only on the last point — visual emphasis on "where you are now".
    const last = points[points.length - 1];
    if (last) {
      const dot = document.createElementNS(SVG_NS, 'circle');
      dot.setAttribute('class', 'pc-dot');
      dot.setAttribute('cx', last.x.toFixed(1));
      dot.setAttribute('cy', last.y.toFixed(1));
      dot.setAttribute('r', '3.5');
      // Keyboard-focusable "latest value" marker with a per-point
      // <title> so screen-reader users can get the exact number.
      dot.setAttribute('tabindex', '0');
      dot.setAttribute('role', 'img');
      const dotTitle = document.createElementNS(SVG_NS, 'title');
      dotTitle.textContent = `Latest: ${round1(numeric[numeric.length - 1])}${opts.unit ? ' ' + opts.unit : ''}`;
      dot.appendChild(dotTitle);
      svg.appendChild(dot);
    }
    // Axis labels: min at bottom-left, max at top-left.
    const minLabel = document.createElementNS(SVG_NS, 'text');
    minLabel.setAttribute('class', 'pc-axis');
    minLabel.setAttribute('x', '2');
    minLabel.setAttribute('y', (height - 2).toString());
    minLabel.textContent = String(Math.round(min));
    const maxLabel = document.createElementNS(SVG_NS, 'text');
    maxLabel.setAttribute('class', 'pc-axis');
    maxLabel.setAttribute('x', '2');
    maxLabel.setAttribute('y', '12');
    maxLabel.textContent = String(Math.round(max));
    svg.appendChild(minLabel);
    svg.appendChild(maxLabel);
  }
  container.appendChild(svg);
  return { min, max };
}

export async function renderProgressCharts() {
  // Build an ISO-dated series of the last 30 days. Null = no data that day.
  // R26.1: use local-day ISO (matches how consumption + weight entries
  // are stamped after R25.1). Previously mixed local Date arithmetic
  // with UTC-based toISOString().slice(0,10), which could offset the
  // series by one day at tz edges and silently misalign weights with
  // kcal columns.
  const days = [];
  const nowMs = Date.now();
  for (let i = 29; i >= 0; i--) {
    days.push(localDateISO(nowMs - i * 86_400_000));
  }

  // Weight series — pick the most recent entry per date if multiple.
  const weights = await listWeight().catch(() => []);
  const weightByDate = new Map();
  for (const w of weights) {
    if (!weightByDate.has(w.date) || (w.timestamp ?? 0) > (weightByDate.get(w.date).timestamp ?? 0)) {
      weightByDate.set(w.date, w);
    }
  }
  const weightSeries = days.map((d) => weightByDate.get(d)?.weight_kg ?? null);

  // Kcal series — sum per date from all consumption entries.
  const allEntries = await listAllEntries().catch(() => []);
  const kcalByDate = new Map();
  for (const e of allEntries) {
    kcalByDate.set(e.date, (kcalByDate.get(e.date) ?? 0) + (Number(e.kcal) || 0));
  }
  const kcalSeries = days.map((d) => (kcalByDate.has(d) ? kcalByDate.get(d) : null));

  // Water series — localStorage-backed per date.
  const waterSeries = days.map((d) => {
    const raw = localStorage.getItem(`scanneat.hydration.${d}`);
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? n : null;
  });

  const w = renderLineChart($('progress-weight-chart'), weightSeries, { ariaLabel: t('progressWeight'), unit: 'kg' });
  const k = renderLineChart($('progress-kcal-chart'), kcalSeries, { ariaLabel: t('progressKcal'), unit: 'kcal' });
  const wt = renderLineChart($('progress-water-chart'), waterSeries, { ariaLabel: t('progressWater'), unit: 'ml' });

  const fmtSummary = (el, res) => {
    if (!el) return;
    if (!res || res.min == null) { el.textContent = ''; return; }
    el.textContent = t('progressMinMax', { min: Math.round(res.min), max: Math.round(res.max) });
  };
  fmtSummary($('progress-weight-summary'), w);
  fmtSummary($('progress-kcal-summary'), k);
  fmtSummary($('progress-water-summary'), wt);
}
