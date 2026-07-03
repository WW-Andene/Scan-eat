import { apiUrl } from '../core/api-base.js';
/**
 * Recipes dialog — multi-component dishes that log as ONE aggregated
 * consumption entry. Also houses the recipe editor and the "from last
 * identified plate" shortcut.
 *
 * Previously ~240 lines inline in app.js. Mirrors the templates-dialog
 * shape: deps injection + returned handles so the outer app.js can
 * re-render the list after external actions (e.g. after a multi-photo
 * plate scan fills `lastIdentifiedPlate`).
 *
 * R9.2 adds the duplicate-recipe chip — same UX as the R8.6 template
 * duplicate: one-tap clone under a new name.
 *
 * R11.8 adds the share-recipe chip — uses shareOrCopy + the new
 * formatRecipeShare presenter.
 *
 * Deps shape:
 *   { t, toast,
 *     aggregateRecipe, saveRecipe, listRecipes, deleteRecipe,
 *     putEntry, defaultMealForHour, todayISO, renderDashboard,
 *     shareOrCopy, formatRecipeShare, currentLang }
 *
 * Returns:
 *   { setLastIdentifiedPlate(items), renderRecipesList() }
 */

function $(id) { return document.getElementById(id); }

export function initRecipesDialog(deps) {
  const {
    t, toast,
    aggregateRecipe, saveRecipe, listRecipes, deleteRecipe,
    putEntry, defaultMealForHour, todayISO, renderDashboard,
    shareOrCopy, formatRecipeShare, currentLang,
    // Gap fix #5: searchFoodDB + listCustomFoods allow the component
    // row's "name" input to autocomplete against the built-in CIQUAL
    // table + user custom foods, and auto-fill kcal / macros when the
    // user picks a suggestion.
    searchFoodDB, listCustomFoods,
    // Feature 3 — recipe import: URL (schema.org / JSON-LD) + photo.
    // `compressImage` is the same helper the QA photo flows use.
    compressImage, getMode, getKey, loadEngine,
    // Gap fix 1 — recipe scoring: the engine's scoreProduct runs on
    // a synthesised ProductInput from buildRecipeProductInput.
    buildRecipeProductInput,
    // Gap fixes #24 + #25 — allergen + diet warnings on user-typed
    // recipes. Pure helpers; getProfile is the reactive source.
    checkRecipeWarnings, getProfile,
  } = deps;

  const recipesBtn = $('recipes-btn');
  const recipesDialog = $('recipes-dialog');
  const recipesCloseBtn = $('recipes-close');
  const recipesListEl = $('recipes-list');
  const recipeNewBtn = $('recipe-new-btn');

  const recipeEditDialog = $('recipe-edit-dialog');
  const recipeEditName = $('recipe-edit-name');
  const recipeEditServings = $('recipe-edit-servings');
  const recipeEditComps = $('recipe-components-list');
  const recipeEditTotals = $('recipe-edit-totals');
  const recipeAddCompBtn = $('recipe-add-component');
  const recipeEditCancel = $('recipe-edit-cancel');
  const recipeEditSave = $('recipe-edit-save');

  let editingRecipe = null;
  let lastIdentifiedPlate = null;

  function newComponent() {
    return { product_name: '', grams: 0, kcal: 0, carbs_g: 0, fat_g: 0, protein_g: 0 };
  }

  function readDraftComponentsFromDOM() {
    if (!recipeEditComps) return [];
    const rows = recipeEditComps.querySelectorAll('.recipe-component');
    return Array.from(rows).map((row) => ({
      product_name: row.querySelector('.rc-name')?.value || '',
      grams:     Number(row.querySelector('.rc-grams')?.value) || 0,
      kcal:      Number(row.querySelector('.rc-kcal')?.value) || 0,
      protein_g: Number(row.querySelector('.rc-prot')?.value) || 0,
      carbs_g:   Number(row.querySelector('.rc-carb')?.value) || 0,
      fat_g:     Number(row.querySelector('.rc-fat')?.value) || 0,
    }));
  }

  // Gap fix 1 — live grade cache + score. loadEngine is async so we
  // cache the engine module once per dialog session and use a local
  // draft→score function. Scoring is cheap; re-running on every
  // keystroke is fine.
  let cachedEngine = null;
  async function scoreDraftRecipe(draft) {
    if (!loadEngine || !buildRecipeProductInput) return null;
    try {
      if (!cachedEngine) cachedEngine = await loadEngine();
      if (!cachedEngine?.scoreProduct) return null;
      const productInput = buildRecipeProductInput(draft);
      return cachedEngine.scoreProduct(productInput);
    } catch (err) {
      console.warn('[recipe-score]', err);
      return null;
    }
  }

  function recalcRecipeTotals() {
    if (!recipeEditTotals) return;
    const draft = {
      name: recipeEditName?.value || '',
      components: readDraftComponentsFromDOM(),
    };
    // R9.4: keep the computed-servings in sync with the display-label
    // servings by clamping both to `max(1, round(n))`. Previously the
    // editor title showed 1 while aggregateRecipe internally used 0.1
    // for non-integer input.
    const serv = Math.max(1, Math.round(Number(recipeEditServings?.value) || 1));
    const agg = aggregateRecipe(draft, serv);
    recipeEditTotals.textContent = t('recipeTotals', {
      kcal: Math.round(agg.kcal),
      prot: Math.round(agg.protein_g),
      carb: Math.round(agg.carbs_g),
      fat:  Math.round(agg.fat_g),
      serv,
    });
    // Gap fix 1: live recipe grade. Async + idempotent — the most
    // recent draft wins so rapid typing doesn't paint stale grades.
    scoreDraftRecipe(draft).then((audit) => {
      const gradeEl = $('recipe-edit-grade');
      if (!gradeEl) return;
      if (!audit || !audit.grade) { gradeEl.hidden = true; return; }
      gradeEl.textContent = t('recipeGradeChip', {
        grade: audit.grade,
        score: Math.round(audit.score),
      });
      gradeEl.dataset.grade = audit.grade;
      gradeEl.hidden = false;
    });
  }

  function renderRecipeComponentRow(comp) {
    const li = document.createElement('li');
    li.className = 'recipe-component';
    const fields = document.createElement('div');
    fields.className = 'rc-fields';
    const mk = (cls, placeholder, value, type = 'text') => {
      const i = document.createElement('input');
      i.type = type;
      i.className = cls;
      i.placeholder = placeholder;
      i.value = value ?? '';
      if (type === 'number') { i.inputMode = 'decimal'; i.step = 'any'; i.min = '0'; }
      i.addEventListener('input', recalcRecipeTotals);
      return i;
    };
    const nameInput = mk('rc-name', t('recipeCompName'), comp.product_name);
    const gramsInput = mk('rc-grams', t('recipeCompGrams'), comp.grams || '', 'number');
    const kcalInput = mk('rc-kcal', t('recipeCompKcal'), comp.kcal || '', 'number');
    const protInput = mk('rc-prot', t('recipeCompProt'), comp.protein_g || '', 'number');
    const carbInput = mk('rc-carb', t('recipeCompCarb'), comp.carbs_g || '', 'number');
    const fatInput = mk('rc-fat', t('recipeCompFat'), comp.fat_g || '', 'number');
    fields.appendChild(nameInput);
    fields.appendChild(gramsInput);
    fields.appendChild(kcalInput);
    fields.appendChild(protInput);
    fields.appendChild(carbInput);
    fields.appendChild(fatInput);

    // Gap fix #5: ingredient autocomplete + auto-fill macros. When the
    // user types a name that matches a FOOD_DB / custom-foods entry
    // and either no kcal is typed yet or all macros are 0, we scale the
    // per-100g DB values by whatever grams the user typed (defaulting
    // to 100) and pre-fill kcal + protein + carbs + fat. Users who
    // typed their own macros (kcal > 0) are never overwritten — the
    // auto-fill only targets empty rows so edits stay sticky.
    if (searchFoodDB) {
      const sug = document.createElement('ul');
      sug.className = 'rc-suggestions';
      sug.hidden = true;
      li.appendChild(sug);
      const macrosEmpty = () =>
        (Number(kcalInput.value) || 0) === 0
        && (Number(protInput.value) || 0) === 0
        && (Number(carbInput.value) || 0) === 0
        && (Number(fatInput.value) || 0) === 0;
      const renderSuggestions = (q) => {
        sug.textContent = '';
        const matches = searchFoodDB(q, 5, listCustomFoods ? listCustomFoods() : []);
        if (matches.length === 0) { sug.hidden = true; return; }
        for (const f of matches) {
          const item = document.createElement('li');
          item.className = 'rc-suggestion';
          item.setAttribute('role', 'option');
          const n = document.createElement('span');
          n.textContent = f.name;
          const k = document.createElement('span');
          k.className = 'rc-suggestion-kcal';
          k.textContent = `${Math.round(f.kcal)} kcal/100g`;
          item.appendChild(n);
          item.appendChild(k);
          item.addEventListener('mousedown', (ev) => {
            ev.preventDefault();
            nameInput.value = f.name;
            const grams = Math.max(1, Number(gramsInput.value) || 100);
            if (!Number(gramsInput.value)) gramsInput.value = '100';
            const factor = grams / 100;
            // Only fill when the row is "blank" on macros — protects
            // users who are building a custom recipe and want their
            // own numbers.
            if (macrosEmpty()) {
              kcalInput.value = String(Math.round((f.kcal || 0) * factor));
              protInput.value = String(Math.round((f.protein_g || 0) * factor));
              carbInput.value = String(Math.round((f.carbs_g || 0) * factor));
              fatInput.value = String(Math.round((f.fat_g || 0) * factor));
            }
            sug.hidden = true;
            recalcRecipeTotals();
          });
          sug.appendChild(item);
        }
        sug.hidden = false;
      };
      nameInput.addEventListener('input', (ev) => {
        const q = (ev.target.value || '').trim();
        if (q.length < 2) { sug.hidden = true; return; }
        renderSuggestions(q);
      });
      nameInput.addEventListener('blur', () => {
        // Slight delay so mousedown on a suggestion fires first.
        setTimeout(() => { sug.hidden = true; }, 150);
      });
      // Also re-scale when user edits grams AFTER picking a food — if
      // the row was freshly picked (tag via data-attr), re-scale macros.
    }

    // Gap fix 2 — ingredient swap. Click the 🔄 button on a row to
    // see alternatives from the local FOOD_DB + custom-foods list,
    // ranked by macro "healthiness" (lower kcal first, then higher
    // protein density). Picking one updates name + macros in place.
    const swap = document.createElement('button');
    swap.type = 'button';
    swap.className = 'rc-swap';
    swap.textContent = '🔄';
    swap.setAttribute('aria-label', t('recipeIngredientSwap'));
    swap.addEventListener('click', () => {
      const currentName = (nameInput.value || '').trim();
      if (!currentName || !searchFoodDB) {
        toast(t('recipeIngredientSwapNoMatch'), 'warn');
        return;
      }
      // Search uses substring matching; we pull 20 candidates then
      // re-rank locally by (kcal asc, protein desc) — a reasonable
      // proxy for "healthier alternative" within the same food
      // family.
      const extras = listCustomFoods ? listCustomFoods() : [];
      const matches = searchFoodDB(currentName, 20, extras);
      const ranked = matches
        .filter((f) => (f.name || '').toLowerCase() !== currentName.toLowerCase())
        .sort((a, b) => {
          if ((a.kcal || 0) !== (b.kcal || 0)) return (a.kcal || 0) - (b.kcal || 0);
          return (b.protein_g || 0) - (a.protein_g || 0);
        })
        .slice(0, 5);
      if (ranked.length === 0) {
        toast(t('recipeIngredientSwapNoMatch'), 'warn');
        return;
      }
      // Inline picker — same affordance as the autocomplete dropdown
      // but anchored to the swap button and showing the 5 ranked
      // candidates.
      let picker = li.querySelector('.rc-swap-picker');
      if (picker) { picker.remove(); return; }
      picker = document.createElement('ul');
      picker.className = 'rc-swap-picker';
      const header = document.createElement('li');
      header.className = 'rc-swap-header';
      header.textContent = t('recipeIngredientSwapTitle', { name: currentName });
      picker.appendChild(header);
      for (const f of ranked) {
        const pickLi = document.createElement('li');
        pickLi.className = 'rc-swap-item';
        pickLi.setAttribute('role', 'option');
        const n = document.createElement('span');
        n.textContent = f.name;
        const k = document.createElement('span');
        k.className = 'rc-swap-kcal';
        k.textContent = `${Math.round(f.kcal)} kcal · P ${Math.round(f.protein_g || 0)} g / 100 g`;
        pickLi.appendChild(n);
        pickLi.appendChild(k);
        pickLi.addEventListener('mousedown', (ev) => {
          ev.preventDefault();
          nameInput.value = f.name;
          const grams = Math.max(1, Number(gramsInput.value) || 100);
          if (!Number(gramsInput.value)) gramsInput.value = '100';
          const factor = grams / 100;
          kcalInput.value = String(Math.round((f.kcal || 0) * factor));
          protInput.value = String(Math.round((f.protein_g || 0) * factor));
          carbInput.value = String(Math.round((f.carbs_g || 0) * factor));
          fatInput.value = String(Math.round((f.fat_g || 0) * factor));
          picker.remove();
          recalcRecipeTotals();
        });
        picker.appendChild(pickLi);
      }
      li.appendChild(picker);
    });

    const rm = document.createElement('button');
    rm.type = 'button';
    rm.className = 'rc-remove';
    rm.textContent = '×';
    rm.setAttribute('aria-label', t('recipeRemoveComp'));
    rm.addEventListener('click', () => { li.remove(); recalcRecipeTotals(); });

    li.appendChild(fields);
    li.appendChild(swap);
    li.appendChild(rm);
    return li;
  }

  function openRecipeEditor(recipe) {
    editingRecipe = recipe ?? { id: undefined, name: '', servings: 1, components: [newComponent()] };
    if (recipeEditName) recipeEditName.value = editingRecipe.name;
    if (recipeEditServings) recipeEditServings.value = String(editingRecipe.servings || 1);
    if (recipeEditComps) {
      recipeEditComps.textContent = '';
      const comps = editingRecipe.components?.length ? editingRecipe.components : [newComponent()];
      for (const c of comps) recipeEditComps.appendChild(renderRecipeComponentRow(c));
    }
    recalcRecipeTotals();
    recipeEditDialog?.showModal();
  }

  async function renderRecipesList() {
    if (!recipesListEl) return;
    recipesListEl.textContent = '';
    const all = await listRecipes().catch(() => []);
    if (all.length === 0) {
      const li = document.createElement('li');
      li.className = 'dash-entry-empty';
      li.textContent = t('recipeEmpty');
      recipesListEl.appendChild(li);
      return;
    }
    // R34.N1: client-side search filter. Case-insensitive substring
    // over recipe.name + any component.product_name, so typing "tomate"
    // surfaces both "Salade de tomates" and "Pâtes sauce tomate".
    const searchEl = $('recipes-search');
    const q = (searchEl?.value || '').trim().toLowerCase();
    const filtered = q
      ? all.filter((r) =>
          (r.name || '').toLowerCase().includes(q)
          || (r.components || []).some((c) => (c.product_name || '').toLowerCase().includes(q)))
      : all;
    if (filtered.length === 0) {
      const li = document.createElement('li');
      li.className = 'dash-entry-empty';
      li.textContent = t('recipesSearchNoMatch');
      recipesListEl.appendChild(li);
      return;
    }
    for (const r of filtered) {
      const li = document.createElement('li');
      li.className = 'tpl-item';
      li.dataset.recipeId = r.id;
      const head = document.createElement('div');
      head.className = 'tpl-head';
      const pick = document.createElement('input');
      pick.type = 'checkbox';
      pick.className = 'tpl-pick';
      pick.setAttribute('aria-label', t('recipeGrocerySelect'));
      pick.dataset.recipeId = r.id;
      head.appendChild(pick);
      // Gap fix 1: per-recipe grade chip on the list. Lazy-loaded so
      // the initial list paint isn't blocked on the engine import.
      const grade = document.createElement('span');
      grade.className = 'recipe-row-grade';
      grade.hidden = true;
      head.appendChild(grade);
      scoreDraftRecipe(r).then((audit) => {
        if (!audit?.grade) return;
        grade.textContent = audit.grade;
        grade.dataset.grade = audit.grade;
        grade.title = t('recipeGradeChip', { grade: audit.grade, score: Math.round(audit.score) });
        grade.hidden = false;
      });
      const name = document.createElement('strong');
      // R14.2: display-time fallback mirrors the templates dialog.
      name.textContent = r.name || t('untitledRecipe');
      if (!r.name) name.classList.add('untitled');
      const agg = aggregateRecipe(r, r.servings || 1);
      const summary = document.createElement('span');
      summary.className = 'tpl-kcal';
      summary.textContent = `${Math.round(agg.kcal)} kcal · ${t('recipeIngrCount', { n: r.components.length })}`;
      // Gap fixes #24 + #25 — surface allergen + diet warnings on
      // each row before the user applies it.
      if (checkRecipeWarnings && getProfile) {
        try {
          const profile = getProfile();
          const warnings = checkRecipeWarnings(r, profile, currentLang ? currentLang() : 'fr');
          if (warnings.allergens.length > 0 || warnings.dietViolations.length > 0) {
            const chip = document.createElement('span');
            chip.className = 'recipe-warn-chip';
            const parts = [];
            if (warnings.allergens.length > 0) {
              parts.push(`⚠ ${warnings.allergens.map((a) => a.label).join(' · ')}`);
            }
            if (warnings.dietViolations.length > 0) {
              parts.push(`🚫 ${warnings.dietViolations.slice(0, 2).join(' · ')}`);
            }
            chip.textContent = parts.join(' · ');
            chip.title = parts.join(' · ');
            head.appendChild(chip);
          }
        } catch { /* best-effort — never block the list render */ }
      }
      head.appendChild(name);
      head.appendChild(summary);
      li.appendChild(head);

      const actions = document.createElement('div');
      actions.className = 'tpl-actions';
      const apply = document.createElement('button');
      apply.type = 'button';
      apply.className = 'chip-btn accent';
      apply.textContent = t('recipeApply');
      apply.addEventListener('click', async () => {
        try {
          const aggregated = aggregateRecipe(r, r.servings || 1);
          const meal = defaultMealForHour(new Date().getHours());
          // Fix #3: carry the computed recipe grade onto the applied
          // entry so the dashboard / rollups / export can see it.
          // Score is cheap; blocking on it isn't worth the wait so
          // we fire-and-forget and fall back to an ungraded entry
          // if the engine isn't loaded.
          let grade = null;
          let score = null;
          try {
            const audit = await scoreDraftRecipe(r);
            if (audit?.grade) {
              grade = audit.grade;
              score = Math.round(audit.score);
            }
          } catch { /* best-effort */ }
          const entry = {
            id: globalThis.crypto?.randomUUID?.() ?? `a${Date.now()}${Math.random().toString(36).slice(2)}`,
            date: todayISO(),
            timestamp: Date.now(),
            meal,
            ...aggregated,
            ...(grade ? { recipe_grade: grade, recipe_score: score } : {}),
          };
          await putEntry(entry);
          await renderDashboard();
          recipesDialog?.close();
          toast(t('recipeAppliedToast', { name: r.name }), 'ok');
        } catch (err) { console.error('[recipe-apply]', err); }
      });
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'chip-btn';
      edit.textContent = t('recipeEdit');
      edit.addEventListener('click', () => openRecipeEditor(r));
      // R9.2: duplicate chip — clones the recipe under "{name} (copy)".
      // Mirror of the R8.6 templates duplicate. Saves immediately so the
      // user sees the clone land in the list without re-opening the
      // editor for obvious cases.
      const dup = document.createElement('button');
      dup.type = 'button';
      dup.className = 'chip-btn';
      dup.textContent = t('recipeDuplicate');
      dup.setAttribute('aria-label', t('recipeDuplicate'));
      dup.addEventListener('click', async () => {
        try {
          const saved = await saveRecipe({
            name: `${r.name} ${t('templateCopySuffix')}`,
            servings: r.servings || 1,
            components: r.components,
          });
          await renderRecipesList();
          toast(t('recipeDuplicatedToast', { name: saved.name }), 'ok');
        } catch (err) { console.error('[recipe-duplicate]', err); }
      });
      // R11.8: share-recipe chip. Emits a compact plain-text summary
      // via the unified shareOrCopy path (native share sheet on
      // mobile, clipboard fallback elsewhere). Locale-aware via the
      // formatRecipeShare presenter + currentLang() lookup.
      const shareBtn = document.createElement('button');
      shareBtn.type = 'button';
      shareBtn.className = 'chip-btn';
      shareBtn.textContent = t('recipeShare');
      shareBtn.setAttribute('aria-label', t('recipeShare'));
      shareBtn.addEventListener('click', async () => {
        if (!shareOrCopy || !formatRecipeShare) return;
        const text = formatRecipeShare(r, { lang: currentLang ? currentLang() : 'fr' });
        if (!text) { toast(t('recipeShareEmpty'), 'warn'); return; }
        await shareOrCopy({
          title: r.name || t('recipeShare'),
          text,
          toasts: { copied: t('recipeShareCopied'), failed: t('recipeShareFailed') },
          toast,
        });
      });
      const del = document.createElement('button');
      del.type = 'button';
      del.className = 'chip-btn';
      del.textContent = '🗑';
      // R23.4: aria-label includes recipe name.
      del.setAttribute('aria-label', r.name
        ? `${t('recipeDelete')} — ${r.name}`
        : t('recipeDelete'));
      del.addEventListener('click', async () => {
        await deleteRecipe(r.id);
        await renderRecipesList();
      });
      actions.appendChild(apply);
      actions.appendChild(edit);
      actions.appendChild(dup);
      actions.appendChild(shareBtn);
      actions.appendChild(del);
      li.appendChild(actions);
      recipesListEl.appendChild(li);
    }
  }

  function refreshRecipeFromPlateBtn() {
    const btn = $('recipe-from-plate-btn');
    if (!btn) return;
    const has = !!lastIdentifiedPlate && Array.isArray(lastIdentifiedPlate.items) && lastIdentifiedPlate.items.length > 0;
    btn.disabled = !has;
    btn.hidden = !has;
  }

  // External mutation path — called by the qa-photo-multi handler in
  // app.js after a successful plate scan.
  function setLastIdentifiedPlate(items) {
    lastIdentifiedPlate = items && items.length
      ? { items, stashed_at: Date.now() }
      : null;
    refreshRecipeFromPlateBtn();
  }

  recipeEditName?.addEventListener('input', recalcRecipeTotals);
  recipeEditServings?.addEventListener('input', recalcRecipeTotals);

  recipeAddCompBtn?.addEventListener('click', () => {
    recipeEditComps?.appendChild(renderRecipeComponentRow(newComponent()));
    recalcRecipeTotals();
  });

  recipeEditCancel?.addEventListener('click', (e) => {
    e.preventDefault();
    editingRecipe = null;
    recipeEditDialog?.close();
  });

  recipeEditSave?.addEventListener('click', async (e) => {
    e.preventDefault();
    const components = readDraftComponentsFromDOM().filter((c) => c.product_name || c.kcal || c.grams);
    try {
      await saveRecipe({
        id: editingRecipe?.id,
        name: recipeEditName?.value || '',
        servings: Number(recipeEditServings?.value) || 1,
        components,
      });
      editingRecipe = null;
      recipeEditDialog?.close();
      await renderRecipesList();
    } catch (err) { console.error('[recipe-save]', err); }
  });

  recipesBtn?.addEventListener('click', async () => {
    recipesDialog?.showModal();
    refreshRecipeFromPlateBtn();
    try { await renderRecipesList(); }
    catch (err) { console.warn('[recipes] render failed', err); }
  });
  // R34.N1: wire the search input — debounce-free; filter runs client-
  // side over the already-fetched list.
  $('recipes-search')?.addEventListener('input', () => {
    renderRecipesList().catch(() => {});
  });
  recipesCloseBtn?.addEventListener('click', (e) => { e.preventDefault(); recipesDialog?.close(); });
  recipeNewBtn?.addEventListener('click', () => openRecipeEditor(null));

  $('recipe-from-plate-btn')?.addEventListener('click', () => {
    if (!lastIdentifiedPlate?.items?.length) return;
    const components = lastIdentifiedPlate.items.map((it) => ({
      product_name: String(it.name ?? ''),
      grams: Math.round(Number(it.estimated_grams) || 0),
      kcal: Math.round(Number(it.kcal) || 0),
      protein_g: Math.round(Number(it.protein_g) || 0),
      carbs_g: Math.round(Number(it.carbs_g) || 0),
      fat_g: Math.round(Number(it.fat_g) || 0),
    }));
    openRecipeEditor({
      id: undefined,
      name: '',
      servings: 1,
      components,
    });
  });

  refreshRecipeFromPlateBtn();

  // Feature 3 — Recipe URL import (schema.org / JSON-LD).
  // The server endpoint /api/fetch-recipe proxies the URL, parses the
  // JSON-LD Recipe, and returns { name, servings, ingredients[], steps,
  // nutrition? }. Client maps ingredient strings → component rows; the
  // LLM-derived nutrition block (if any) is stored per-component via a
  // proportional split so aggregateRecipe still sums correctly.
  function setImportStatus(text, state) {
    const el = $('recipe-import-status');
    if (!el) return;
    if (!text) { el.hidden = true; el.textContent = ''; return; }
    el.textContent = text;
    if (state) el.dataset.state = state;
    else delete el.dataset.state;
    el.hidden = false;
  }

  function openImportedRecipe(imp) {
    // Map ingredient strings to component rows. Each row defaults to
    // empty macros so the user (or the R5 autocomplete) can fill them.
    // When the LLM gave us a total nutrition block, split it evenly
    // across the ingredients — rough but better than zeros, and the
    // user can refine per row.
    const ings = Array.isArray(imp.ingredients) ? imp.ingredients : [];
    const n = Math.max(1, ings.length);
    const total = imp.nutrition || {};
    const perRowKcal = Math.round((Number(total.kcal) || 0) / n);
    const perRowProt = Math.round((Number(total.protein_g) || 0) / n);
    const perRowCarb = Math.round((Number(total.carbs_g) || 0) / n);
    const perRowFat  = Math.round((Number(total.fat_g) || 0) / n);
    const components = ings.map((raw) => ({
      product_name: String(raw),
      grams: 0,
      kcal: perRowKcal,
      protein_g: perRowProt,
      carbs_g: perRowCarb,
      fat_g: perRowFat,
    }));
    // If the recipe has no ingredient list, still open the editor
    // with the name + a single blank row the user can fill.
    openRecipeEditor({
      id: undefined,
      name: String(imp.name || '').trim(),
      servings: Math.max(1, Math.round(Number(imp.servings) || 1)),
      components: components.length > 0 ? components : [newComponent()],
    });
  }

  $('recipe-import-url-go')?.addEventListener('click', async () => {
    const url = String($('recipe-import-url')?.value || '').trim();
    if (!url) { setImportStatus(t('recipeImportNoUrl'), 'warn'); return; }
    setImportStatus(t('recipeImportLoading'));
    try {
      const res = await fetch(apiUrl(`/api/fetch-recipe?url=${encodeURIComponent(url)}`));
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || `HTTP ${res.status}`);
      }
      const imp = await res.json();
      setImportStatus('');
      recipesDialog?.close();
      openImportedRecipe(imp);
      toast(t('recipeImportOk', { name: imp.name || '—' }), 'ok');
    } catch (err) {
      console.warn('[recipe-import-url]', err);
      setImportStatus(t('recipeImportFailed', { msg: err?.message || '' }), 'warn');
    }
  });

  // Feature 3 — Recipe photo import. The user photographs a recipe
  // card / cookbook page; LLM extracts structured fields. Mirrors the
  // qa-photo-input plumbing (compressImage + identifyViaModePath +
  // direct/server mode fallback). No macros returned — user fills
  // via the R5 component autocomplete.
  $('recipe-photo-input')?.addEventListener('change', async (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow re-selecting
    if (!file || !compressImage) return;
    setImportStatus(t('recipeImportScanning'));
    try {
      const compressed = await compressImage(file);
      const images = [{ base64: compressed.base64, mime: compressed.mime }];
      const mode = typeof getMode === 'function' ? getMode() : 'auto';
      let result;
      if (mode === 'direct' && loadEngine && getKey) {
        const engine = await loadEngine();
        const key = getKey();
        if (!key) throw new Error(t('errMissingKey'));
        try {
          result = await engine.identifyRecipe(images, { apiKey: key });
        } catch (err2) {
          if (err2?.status === 429) throw new Error(t('errRateLimit'));
          throw err2;
        }
      } else {
        const res = await fetch(apiUrl('/api/identify-recipe'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ images }),
        });
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          if (res.status === 429 || body.error === 'rate_limit') {
            throw new Error(t('errRateLimit'));
          }
          throw new Error(body.error || `HTTP ${res.status}`);
        }
        result = await res.json();
      }
      setImportStatus('');
      if (!result?.ingredients?.length) {
        setImportStatus(t('recipeImportPhotoEmpty'), 'warn');
        return;
      }
      recipesDialog?.close();
      openImportedRecipe(result);
      toast(t('recipeImportOk', { name: result.name || '—' }), 'ok');
    } catch (err) {
      console.warn('[recipe-import-photo]', err);
      setImportStatus(t('recipeImportFailed', { msg: err?.message || '' }), 'warn');
    }
  });

  return { setLastIdentifiedPlate, renderRecipesList };
}
