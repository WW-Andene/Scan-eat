/**
 * Meal Plan UI — 7-day grid, slot picker per cell.
 *
 * ADR-0004 feature-folder pattern. renderMealPlan() and openMealPlan()
 * are exported so app.js can call them from the PWA shortcut intent
 * handler and the meal-plan button without owning any of the DOM or
 * data logic.
 *
 * Deps shape (passed to initMealPlanUI):
 *   { t, toast, show, hide, currentLang,
 *     listRecipes, listTemplates,
 *     aggregateRecipe, buildRecipeProductInput,
 *     expandTemplate, putEntry,
 *     aggregateGroceryList, formatGroceryList,
 *     openPantryIdeas,
 *     dateFormatter, localeFor }
 *
 * DOM elements looked up by id:
 *   #meal-plan-dialog, #meal-plan-grid,
 *   #meal-plan-btn, #meal-plan-close, #meal-plan-clear, #meal-plan-grocery,
 *   #pantry-input, #pantry-status, #pantry-submit,
 *   #grocery-list, #grocery-text, #grocery-source,
 *   #grocery-markdown, #grocery-share, #grocery-close, #grocery-copy,
 *   #recipes-dialog
 *
 * Data dependencies (imported directly — pure modules with no side effects):
 *   /features/meal-plan.js  (weekDates, getDayPlan, setSlot, clearDay,
 *                            clearAll, planRecipes, MEAL_PLAN_MEALS, isoToday)
 */

import {
  weekDates, getDayPlan, setSlot, clearDay,
  clearAll as clearMealPlan, planRecipes,
  MEAL_PLAN_MEALS, isoToday,
} from './meal-plan.js';

let deps = null;
let mealPlanDialog = null;
let groceryDialog = null;
let pantryDialog = null;

function $(id) { return document.getElementById(id); }

// ── Meal plan grid ─────────────────────────────────────────────────────────

export async function renderMealPlan() {
  const { t, toast, listRecipes, listTemplates, expandTemplate,
          aggregateRecipe, putEntry, dateFormatter, localeFor, currentLang } = deps;
  const grid = $('meal-plan-grid');
  if (!grid) return;
  grid.textContent = '';
  const dates = weekDates();
  const recipes = await listRecipes().catch(() => []);
  const templates = await listTemplates().catch(() => []);
  const locale = localeFor(currentLang());
  const mealLabels = {
    breakfast: t('mealBreakfast'),
    lunch: t('mealLunch'),
    dinner: t('mealDinner'),
    snack: t('mealSnack'),
  };

  for (const date of dates) {
    const day = getDayPlan(date);
    const card = document.createElement('article');
    card.className = 'meal-plan-day';
    const head = document.createElement('header');
    head.className = 'meal-plan-day-head';
    const dt = new Date(`${date}T12:00:00`);
    head.textContent = dateFormatter(locale, { weekday: 'short', day: 'numeric', month: 'short' }).format(dt);
    card.appendChild(head);

    for (const meal of MEAL_PLAN_MEALS) {
      const row = document.createElement('div');
      row.className = 'meal-plan-row';
      const label = document.createElement('span');
      label.className = 'meal-plan-meal';
      label.textContent = mealLabels[meal];
      row.appendChild(label);

      const select = document.createElement('select');
      select.className = 'meal-plan-pick';
      select.setAttribute('aria-label', `${mealLabels[meal]} ${date}`);
      const noneOpt = document.createElement('option');
      noneOpt.value = '';
      noneOpt.textContent = '—';
      select.appendChild(noneOpt);
      const recipeGroup = document.createElement('optgroup');
      recipeGroup.label = t('mealPlanGroupRecipes');
      for (const r of recipes) {
        const o = document.createElement('option');
        o.value = `recipe:${r.id}`;
        o.textContent = r.name;
        recipeGroup.appendChild(o);
      }
      if (recipes.length > 0) select.appendChild(recipeGroup);
      const tplGroup = document.createElement('optgroup');
      tplGroup.label = t('mealPlanGroupTemplates');
      for (const tpl of templates) {
        const o = document.createElement('option');
        o.value = `template:${tpl.id}`;
        o.textContent = tpl.name;
        tplGroup.appendChild(o);
      }
      if (templates.length > 0) select.appendChild(tplGroup);

      const noteOpt = document.createElement('option');
      noteOpt.value = 'note:new';
      noteOpt.textContent = t('mealPlanNoteOption');
      select.appendChild(noteOpt);

      const slot = day[meal];
      if (slot?.kind === 'recipe') select.value = `recipe:${slot.id}`;
      else if (slot?.kind === 'template') select.value = `template:${slot.id}`;
      else if (slot?.kind === 'note') {
        const o = document.createElement('option');
        o.value = `note:current`;
        o.textContent = `📝 ${slot.text.slice(0, 40)}`;
        if (slot.text.length > 40) o.title = slot.text;
        select.appendChild(o);
        select.value = 'note:current';
      }

      select.addEventListener('change', () => {
        const v = select.value;
        if (!v) { setSlot(date, meal, null); renderMealPlan(); return; }
        if (v.startsWith('recipe:')) {
          const id = v.slice('recipe:'.length);
          const r = recipes.find((x) => x.id === id);
          if (r) setSlot(date, meal, { kind: 'recipe', id, name: r.name });
        } else if (v.startsWith('template:')) {
          const id = v.slice('template:'.length);
          const tpl = templates.find((x) => x.id === id);
          if (tpl) setSlot(date, meal, { kind: 'template', id, name: tpl.name });
        } else if (v === 'note:new') {
          const text = window.prompt(t('mealPlanNotePrompt'));
          if (text && text.trim()) setSlot(date, meal, { kind: 'note', text: text.trim() });
        } else if (v === 'note:current') {
          const text = window.prompt(t('mealPlanNotePrompt'), slot?.text ?? '');
          if (text === null) { renderMealPlan(); return; }
          if (!text.trim()) setSlot(date, meal, null);
          else setSlot(date, meal, { kind: 'note', text: text.trim() });
        }
        renderMealPlan();
      });

      row.appendChild(select);
      card.appendChild(row);
    }

    const actions = document.createElement('div');
    actions.className = 'meal-plan-day-actions';

    const today = isoToday();
    const isTodayOrFuture = date >= today;
    const hasSlots = Object.keys(day).length > 0;

    const applyBtn = document.createElement('button');
    applyBtn.type = 'button';
    applyBtn.className = 'chip-btn accent compact';
    applyBtn.textContent = t('mealPlanApplyDay');
    applyBtn.disabled = !isTodayOrFuture || !hasSlots;
    applyBtn.addEventListener('click', async () => {
      await applyPlanDayToLog(date, day, recipes, templates, { expandTemplate, aggregateRecipe, putEntry });
      deps.renderDashboard?.();
      const prettyDate = dateFormatter(locale, {
        weekday: 'short', day: 'numeric', month: 'short',
      }).format(new Date(`${date}T12:00:00`));
      toast(t('mealPlanApplyToast', { count: Object.keys(day).length, date: prettyDate }), 'ok');
    });
    actions.appendChild(applyBtn);

    const clearBtn = document.createElement('button');
    clearBtn.type = 'button';
    clearBtn.className = 'secondary compact meal-plan-clear-day';
    clearBtn.textContent = t('mealPlanClearDay');
    clearBtn.addEventListener('click', () => {
      if (hasSlots && !window.confirm(t('mealPlanClearDayConfirm'))) return;
      clearDay(date);
      renderMealPlan();
    });
    actions.appendChild(clearBtn);

    card.appendChild(actions);
    grid.appendChild(card);
  }
}

export async function openMealPlan() {
  await renderMealPlan();
  mealPlanDialog?.showModal();
}

// ── Apply plan day to log ──────────────────────────────────────────────────

async function applyPlanDayToLog(date, day, recipes, templates, { expandTemplate, aggregateRecipe, putEntry }) {
  const timestamp = new Date(`${date}T12:00:00`).getTime();
  for (const meal of MEAL_PLAN_MEALS) {
    const slot = day[meal];
    if (!slot) continue;
    if (slot.kind === 'recipe') {
      const r = recipes.find((x) => x.id === slot.id);
      if (!r) continue;
      const agg = aggregateRecipe(r, r.servings || 1);
      try {
        await putEntry({
          id: globalThis.crypto?.randomUUID?.() ?? `p${timestamp}${Math.random().toString(36).slice(2)}`,
          date,
          timestamp,
          meal,
          ...agg,
        });
      } catch { /* skip bad */ }
    } else if (slot.kind === 'template') {
      const tpl = templates.find((x) => x.id === slot.id);
      if (!tpl) continue;
      const entries = expandTemplate(tpl, { date, meal, timestamp });
      for (const entry of entries) {
        try { await putEntry(entry); } catch { /* skip */ }
      }
    }
    // note slots are intentionally skipped
  }
}

// ── Grocery dialog (from meal-plan path) ──────────────────────────────────

async function openGroceryFromPlan() {
  const { t, toast, listRecipes, aggregateGroceryList, formatGroceryList, show, hide } = deps;
  const dates = weekDates();
  const recipes = await listRecipes().catch(() => []);
  const planned = planRecipes(dates, recipes);
  if (planned.length === 0) { toast(t('mealPlanNoRecipes'), 'warn'); return; }
  const items = aggregateGroceryList(planned);
  const list = $('grocery-list');
  const text = $('grocery-text');
  const source = $('grocery-source');
  if (source) source.textContent = t('grocerySource', { n: planned.length });
  if (list) {
    list.textContent = '';
    for (const it of items) {
      const li = document.createElement('li');
      li.className = 'tpl-item';
      const name = document.createElement('strong');
      name.textContent = it.name;
      const grams = document.createElement('span');
      grams.className = 'tpl-kcal';
      grams.textContent = it.grams > 0 ? `${it.grams} g` : '';
      li.appendChild(name);
      li.appendChild(grams);
      if (Array.isArray(it.sources) && it.sources.length > 1) {
        const from = document.createElement('span');
        from.className = 'grocery-from';
        from.textContent = t('groceryFrom', { sources: it.sources.join(' + ') });
        li.appendChild(from);
      }
      list.appendChild(li);
    }
  }
  if (text) text.value = formatGroceryList(items);
  const shareBtn = $('grocery-share');
  if (shareBtn) show(shareBtn);
  mealPlanDialog?.close();
  groceryDialog?.showModal();
}

// ── Pantry submit (recipe ideas from pantry) ───────────────────────────────

function bindPantrySubmit() {
  const { t, openPantryIdeas } = deps;
  $('pantry-submit')?.addEventListener('click', async () => {
    const raw = ($('pantry-input')?.value || '').trim();
    if (!raw) { $('pantry-status').textContent = t('pantryEmpty'); return; }
    const seen = new Set();
    const pantry = [];
    for (const s of raw.split(/[\n,]+/)) {
      const v = s.trim();
      if (v.length < 2) continue;
      const k = v.toLowerCase();
      if (seen.has(k)) continue;
      seen.add(k);
      pantry.push(v);
      if (pantry.length >= 20) break;
    }
    if (pantry.length === 0) { $('pantry-status').textContent = t('pantryEmpty'); return; }
    $('pantry-status').textContent = '';
    pantryDialog?.close();
    document.getElementById('recipes-dialog')?.close();
    await openPantryIdeas(pantry);
  });
}

// ── Init ───────────────────────────────────────────────────────────────────

export function initMealPlanUI(injected) {
  deps = injected;
  mealPlanDialog = $('meal-plan-dialog');
  groceryDialog = $('grocery-dialog');
  pantryDialog = $('pantry-dialog');

  $('meal-plan-btn')?.addEventListener('click', async () => {
    await renderMealPlan();
    mealPlanDialog?.showModal();
  });

  $('meal-plan-close')?.addEventListener('click', (e) => {
    e.preventDefault();
    mealPlanDialog?.close();
  });

  $('meal-plan-clear')?.addEventListener('click', async (e) => {
    e.preventDefault();
    const { t } = deps;
    if (!window.confirm(t('mealPlanClearConfirm'))) return;
    clearMealPlan();
    await renderMealPlan();
  });

  $('meal-plan-grocery')?.addEventListener('click', async (e) => {
    e.preventDefault();
    await openGroceryFromPlan();
  });

  bindPantrySubmit();
}
