/**
 * Meal-templates dialog — list, save-today, apply, duplicate, share, delete.
 *
 * Previously 120 lines inline in app.js. The extraction is a pure
 * pattern re-use: dialog open/close, a promise-based name prompt, and
 * a list renderer. Keeps the same three IDB helpers (saveTemplate /
 * listTemplates / deleteTemplate) the caller already depends on.
 *
 * R8.6 adds the duplicate chip: clones an existing template under a
 * new name ("{name} (copy)" by default — user can edit in the same
 * name prompt). Useful for creating variants (Monday snack vs Tuesday
 * snack) without retyping every macro.
 *
 * R12.2 adds the share-template chip: routes through shareOrCopy +
 * formatTemplateShare for the same native-share/clipboard symmetry as
 * the recipe / weekly / daily / monthly share chips.
 *
 * Deps shape:
 *   { t, toast, listByDate, saveTemplate, listTemplates, deleteTemplate,
 *     expandTemplate, templateKcal, putEntry, todayISO, renderDashboard,
 *     shareOrCopy, formatTemplateShare, currentLang }
 */

function $(id) { return document.getElementById(id); }

export function initTemplatesDialog(deps) {
  const {
    t, toast,
    listByDate, saveTemplate, listTemplates, deleteTemplate,
    expandTemplate, templateKcal, putEntry, todayISO, renderDashboard,
    shareOrCopy, formatTemplateShare, currentLang,
    // Gap fixes #24 + #25 — warn on allergens / diet violations.
    checkTemplateWarnings, getProfile,
  } = deps;

  const templatesBtn = $('templates-btn');
  const templatesDialog = $('templates-dialog');
  const tplClose = $('tpl-close');
  const tplSaveToday = $('tpl-save-today');
  const tplNameDialog = $('tpl-name-dialog');
  const tplNameInput = $('tpl-name-input');
  const tplNameConfirm = $('tpl-name-confirm');
  const tplNameCancel = $('tpl-name-cancel');

  /** Promise-based name prompt that opens a styled dialog instead of
   *  the blocking native prompt(). Resolves to the typed name or null
   *  on cancel. Accepts an optional initial value so "duplicate" can
   *  pre-fill "{name} (copy)" for the user to accept or edit. */
  function askTemplateName(initial = '') {
    if (!tplNameDialog || !tplNameInput) return Promise.resolve(null);
    tplNameInput.value = initial;
    tplNameInput.placeholder = t('templateNamePlaceholder');
    tplNameDialog.showModal();
    tplNameInput.focus();
    if (initial) tplNameInput.select();
    return new Promise((resolve) => {
      const cleanup = () => {
        tplNameConfirm?.removeEventListener('click', onConfirm);
        tplNameCancel?.removeEventListener('click', onCancel);
        tplNameDialog.removeEventListener('close', onClose);
      };
      const onConfirm = (e) => {
        e.preventDefault();
        const name = tplNameInput.value.trim();
        // Cleanup BEFORE close — dialog.close() fires the 'close' event
        // synchronously, so onClose would otherwise run first and resolve
        // the promise with null before we get to resolve with the name.
        cleanup();
        tplNameDialog.close();
        resolve(name || null);
      };
      const onCancel = (e) => {
        e.preventDefault();
        cleanup();
        tplNameDialog.close();
        resolve(null);
      };
      const onClose = () => { cleanup(); resolve(null); };
      tplNameConfirm?.addEventListener('click', onConfirm);
      tplNameCancel?.addEventListener('click', onCancel);
      tplNameDialog.addEventListener('close', onClose);
    });
  }

  async function renderTemplatesList() {
    const ul = $('tpl-list');
    if (!ul) return;
    const all = await listTemplates().catch(() => []);
    ul.textContent = '';
    if (all.length === 0) {
      const li = document.createElement('li');
      li.className = 'dash-entry-empty';
      li.textContent = t('templateEmpty');
      ul.appendChild(li);
      return;
    }
    // R34.N2: client-side search filter — symmetrical with the
    // recipes search. Case-insensitive substring over name + any
    // item's product_name.
    const searchEl = $('tpl-search');
    const q = (searchEl?.value || '').trim().toLowerCase();
    const filtered = q
      ? all.filter((tpl) =>
          (tpl.name || '').toLowerCase().includes(q)
          || (tpl.items || []).some((it) => (it.product_name || '').toLowerCase().includes(q)))
      : all;
    if (filtered.length === 0) {
      const li = document.createElement('li');
      li.className = 'dash-entry-empty';
      li.textContent = t('templatesSearchNoMatch');
      ul.appendChild(li);
      return;
    }
    for (const tpl of filtered) {
      const li = document.createElement('li');
      li.className = 'tpl-item';
      const head = document.createElement('div');
      head.className = 'tpl-head';
      const name = document.createElement('strong');
      // R14.2: display-time fallback — data layer stores empty when
      // the user submitted no name; we resolve to the locale string
      // here instead of baking "Sans nom" into IDB.
      name.textContent = tpl.name || t('untitledTemplate');
      if (!tpl.name) name.classList.add('untitled');
      const kcal = document.createElement('span');
      kcal.className = 'tpl-kcal';
      // R8.7: "items" was hard-coded English — now goes through i18n so
      // FR users see "éléments" instead of an English word in the
      // otherwise-translated dialog.
      kcal.textContent = `${templateKcal(tpl)} kcal · ${t('tplItemsCount', { n: tpl.items.length })}`;
      head.appendChild(name);
      head.appendChild(kcal);
      // Gap fixes #24 + #25 — allergen + diet warning chip per row.
      if (checkTemplateWarnings && getProfile) {
        try {
          const profile = getProfile();
          const warnings = checkTemplateWarnings(tpl, profile, currentLang ? currentLang() : 'fr');
          if (warnings.allergens.length > 0 || warnings.dietViolations.length > 0) {
            const chip = document.createElement('span');
            chip.className = 'recipe-warn-chip';
            const parts = [];
            if (warnings.allergens.length > 0) parts.push(`⚠ ${warnings.allergens.map((a) => a.label).join(' · ')}`);
            if (warnings.dietViolations.length > 0) parts.push(`🚫 ${warnings.dietViolations.slice(0, 2).join(' · ')}`);
            chip.textContent = parts.join(' · ');
            chip.title = parts.join(' · ');
            head.appendChild(chip);
          }
        } catch { /* best-effort */ }
      }
      li.appendChild(head);
      const actions = document.createElement('div');
      actions.className = 'tpl-actions';
      const apply = document.createElement('button');
      apply.type = 'button';
      apply.className = 'chip-btn accent';
      apply.textContent = t('templateApplyToday');
      apply.addEventListener('click', async () => {
        const entries = expandTemplate(tpl, todayISO());
        for (const e of entries) await putEntry(e);
        await renderDashboard();
        toast(t('templateApplyToast', { n: entries.length, plural: entries.length > 1 ? 'ies' : 'y' }), 'ok');
      });
      // R8.6: duplicate chip — spin up a near-copy under a new name.
      // The name prompt defaults to "{original} (copy)" so one confirm
      // is enough for the common "clone-and-tweak" flow.
      const dup = document.createElement('button');
      dup.type = 'button';
      dup.className = 'chip-btn';
      dup.textContent = t('templateDuplicate');
      dup.setAttribute('aria-label', t('templateDuplicate'));
      dup.addEventListener('click', async () => {
        const suggested = `${tpl.name} ${t('templateCopySuffix')}`;
        const newName = await askTemplateName(suggested);
        if (!newName) return;
        // R9.7: forward the source template's meal so the clone inherits
        // breakfast/lunch/dinner/snack instead of silently defaulting
        // to 'snack' (saveTemplate's parameter default).
        const saved = await saveTemplate({ name: newName, meal: tpl.meal, items: tpl.items });
        await renderTemplatesList();
        toast(t('templateSavedToast', { name: saved.name }), 'ok');
      });
      // R12.2: share-template chip — mirrors the R11.8 recipe chip.
      const shareBtn = document.createElement('button');
      shareBtn.type = 'button';
      shareBtn.className = 'chip-btn';
      shareBtn.textContent = t('templateShare');
      shareBtn.setAttribute('aria-label', t('templateShare'));
      shareBtn.addEventListener('click', async () => {
        if (!shareOrCopy || !formatTemplateShare) return;
        const text = formatTemplateShare(tpl, { lang: currentLang ? currentLang() : 'fr' });
        if (!text) { toast(t('templateShareEmpty'), 'warn'); return; }
        await shareOrCopy({
          title: tpl.name || t('templateShare'),
          text,
          toasts: { copied: t('templateShareCopied'), failed: t('templateShareFailed') },
          toast,
        });
      });
      const del = document.createElement('button');
      del.type = 'button';
      del.className = 'chip-btn';
      del.textContent = '🗑';
      // R23.3: aria-label includes template name.
      del.setAttribute('aria-label', tpl.name
        ? `${t('deleteTemplate')} — ${tpl.name}`
        : t('deleteTemplate'));
      del.addEventListener('click', async () => {
        await deleteTemplate(tpl.id);
        await renderTemplatesList();
      });
      actions.appendChild(apply);
      actions.appendChild(dup);
      actions.appendChild(shareBtn);
      actions.appendChild(del);
      li.appendChild(actions);
      ul.appendChild(li);
    }
  }

  templatesBtn?.addEventListener('click', async () => {
    // Open the dialog first, then render. If the IDB read fails, the
    // user still sees the dialog with an empty list instead of a dead
    // button.
    templatesDialog?.showModal();
    try { await renderTemplatesList(); }
    catch (err) { console.warn('[templates] render failed', err); }
  });
  // R34.N2: wire the search input — debounce-free client-side filter.
  $('tpl-search')?.addEventListener('input', () => {
    renderTemplatesList().catch(() => {});
  });
  tplClose?.addEventListener('click', (e) => { e.preventDefault(); templatesDialog?.close(); });

  tplSaveToday?.addEventListener('click', async () => {
    const entries = await listByDate().catch(() => []);
    if (entries.length === 0) {
      // R16.3: this is a "can't proceed" feedback, not a success.
      // Mark it warn so the toast stripe colour matches the meaning.
      toast(t('nothingLoggedToSave'), 'warn');
      return;
    }
    const name = await askTemplateName();
    if (!name) return;
    const saved = await saveTemplate({ name, items: entries });
    await renderTemplatesList();
    toast(t('templateSavedToast', { name: saved.name }), 'ok');
  });

  return { renderTemplatesList };
}
