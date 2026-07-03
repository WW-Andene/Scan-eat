/**
 * Quick Add name autocomplete — typeahead against the built-in food DB
 * + any custom foods the user has saved, + the user's own top-logged
 * foods (gap fix #7 favourites) when the input is empty.
 *
 * Previously ~50 lines inline in app.js (the "Quick Add name
 * autocomplete" block). Pure presentation: read query, query the DB,
 * paint a <ul> of suggestion <li>s, and on click pre-fill the six
 * Quick Add fields (name + kcal + protein + carbs + fat).
 *
 * R10.1 extraction. Mirrors the templates/recipes module shape:
 * deps-injection; no cross-module state.
 *
 * Deps shape:
 *   { t, show, hide, searchFoodDB, listCustomFoods,
 *     listAllEntries?, topFoods? }   // optional — enables favourites
 */

function $(id) { return document.getElementById(id); }

export function initQaAutocomplete(deps) {
  const {
    t, show, hide, searchFoodDB, listCustomFoods,
    listAllEntries, topFoods,
  } = deps;

  const qaNameInput = $('qa-name');
  const qaSuggestionsList = $('qa-name-suggestions-list');
  if (!qaNameInput) return;

  function applyFoodToQuickAdd(food) {
    const set = (id, v) => { const el = $(id); if (el) el.value = String(Math.round(v ?? 0)); };
    qaNameInput.value = food.name;
    // food-db values are per 100 g. Default to 100 g portion unless the
    // user has already typed a specific kcal value; we overwrite either
    // way since the user's intent when picking a suggestion is to
    // inherit its macros.
    set('qa-kcal', food.kcal);
    set('qa-protein', food.protein_g);
    set('qa-carbs', food.carbs_g);
    set('qa-fat', food.fat_g);
  }

  // Gap fix #7 — favourites row: applies a user's own logged entry
  // verbatim (already-totaled kcal + macros, not per-100g scaled).
  function applyFavouriteToQuickAdd(fav) {
    const set = (id, v) => { const el = $(id); if (el) el.value = String(Math.round(v ?? 0)); };
    qaNameInput.value = fav.name;
    set('qa-kcal', fav.avg_kcal);
  }

  function renderFoodSuggestions(query) {
    if (!qaSuggestionsList) return;
    const matches = searchFoodDB(query, 6, listCustomFoods());
    qaSuggestionsList.textContent = '';
    if (matches.length === 0) { hide(qaSuggestionsList); return; }
    for (const f of matches) {
      const li = document.createElement('li');
      li.className = 'qa-suggestion';
      li.setAttribute('role', 'option');
      const name = document.createElement('span');
      name.className = 'qs-name';
      name.textContent = f.name;
      const kcal = document.createElement('span');
      kcal.className = 'qs-kcal';
      kcal.textContent = `${Math.round(f.kcal)} kcal / 100 g`;
      li.appendChild(name);
      li.appendChild(kcal);
      li.addEventListener('mousedown', (ev) => {
        ev.preventDefault(); // keep focus on the input
        applyFoodToQuickAdd(f);
        hide(qaSuggestionsList);
      });
      qaSuggestionsList.appendChild(li);
    }
    show(qaSuggestionsList);
  }

  // Gap fix #7 — render the user's top-5 logged foods when the name
  // input is empty and focused. Falls back silently if the favourites
  // deps aren't wired, so existing callers keep the old behaviour.
  async function renderFavourites() {
    if (!qaSuggestionsList || !listAllEntries || !topFoods) { hide(qaSuggestionsList); return; }
    const entries = await listAllEntries().catch(() => []);
    const favs = topFoods(entries, { limit: 5, sinceDays: 60 });
    qaSuggestionsList.textContent = '';
    if (favs.length === 0) { hide(qaSuggestionsList); return; }
    const header = document.createElement('li');
    header.className = 'qa-suggestion-header';
    header.textContent = t ? t('qaFavouritesHeader') : '★ Favourites';
    qaSuggestionsList.appendChild(header);
    for (const f of favs) {
      const li = document.createElement('li');
      li.className = 'qa-suggestion qa-favourite';
      li.setAttribute('role', 'option');
      const name = document.createElement('span');
      name.className = 'qs-name';
      name.textContent = `★ ${f.name}`;
      const meta = document.createElement('span');
      meta.className = 'qs-kcal';
      meta.textContent = `${f.avg_kcal} kcal · ×${f.count}`;
      li.appendChild(name);
      li.appendChild(meta);
      li.addEventListener('mousedown', (ev) => {
        ev.preventDefault();
        applyFavouriteToQuickAdd(f);
        hide(qaSuggestionsList);
      });
      qaSuggestionsList.appendChild(li);
    }
    show(qaSuggestionsList);
  }

  qaNameInput.addEventListener('input', (e) => {
    const v = e.target.value || '';
    if (v.trim().length === 0) renderFavourites();
    else renderFoodSuggestions(v);
  });
  qaNameInput.addEventListener('blur', () => {
    // Slight delay so a mousedown on a suggestion can fire before we
    // hide.
    setTimeout(() => hide(qaSuggestionsList), 150);
  });
  qaNameInput.addEventListener('focus', (e) => {
    const v = e.target.value || '';
    if (v.trim().length === 0) renderFavourites();
    else renderFoodSuggestions(v);
  });
}
