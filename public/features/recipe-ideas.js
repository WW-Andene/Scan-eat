import { apiUrl } from '../core/api-base.js';
/**
 * Recipe ideas dialog — LLM-backed recipe suggestion cards.
 *
 * Used by:
 *   - the pairings panel ("Idées de recettes" button) → openRecipeIdeas(name)
 *   - the pantry dialog ("Suggest from pantry")       → openPantryIdeas([…])
 *
 * Both paths render into the same #recipe-ideas-dialog + #recipe-ideas-list
 * via the shared renderRecipeCards helper.
 *
 * ADR-0004 feature-folder pattern. Deps:
 *   { t, getMode, getKey, loadEngine }
 */

let deps = null;
function $(id) { return document.getElementById(id); }

// Fix #12 — session-level memoisation. Keyed on a stable string derived from
// the request so identical ingredient / pantry lookups skip the LLM entirely.
// Stored in a plain Map (lives until page reload) — no serialisation needed.
const _recipeCache = new Map();

function _recipeKey(type, payload) {
  if (type === 'ingredient') return `recipes:${String(payload).trim().toLowerCase()}`;
  // pantry: sort so order doesn't matter
  return `pantry:${[...payload].map(s => String(s).trim().toLowerCase()).sort().join('|')}`;
}

function renderCards(recipes) {
  const { t, saveRecipe, toast, openMealPlan } = deps;
  const list = $('recipe-ideas-list');
  if (!list) return;
  list.textContent = '';
  for (const r of recipes) {
    const card = document.createElement('article');
    card.className = 'recipe-idea-card';
    const h = document.createElement('h3');
    h.textContent = r.name;
    card.appendChild(h);
    const meta = document.createElement('p');
    meta.className = 'recipe-idea-meta';
    meta.textContent = t('recipeIdeasMeta', {
      time: Math.round(r.time_min || 0),
      kcal: Math.round(r.kcal_estimate || 0),
    });
    card.appendChild(meta);
    if (Array.isArray(r.ingredients) && r.ingredients.length) {
      const label = document.createElement('p');
      label.className = 'recipe-idea-sublabel';
      label.textContent = t('recipeIdeasIngredients');
      card.appendChild(label);
      const ul = document.createElement('ul');
      ul.className = 'recipe-idea-ings';
      for (const ing of r.ingredients) {
        const li = document.createElement('li');
        li.textContent = ing;
        ul.appendChild(li);
      }
      card.appendChild(ul);
    }
    if (Array.isArray(r.steps) && r.steps.length) {
      const label = document.createElement('p');
      label.className = 'recipe-idea-sublabel';
      label.textContent = t('recipeIdeasSteps');
      card.appendChild(label);
      const ol = document.createElement('ol');
      ol.className = 'recipe-idea-steps';
      for (const step of r.steps) {
        const li = document.createElement('li');
        li.textContent = step;
        ol.appendChild(li);
      }
      card.appendChild(ol);
    }
    // F-F-07 — card actions. Save stashes the idea as a recipe the user
    // can edit later (components empty; name + synthetic kcal total
    // preserved). Plan saves AND opens the meal-plan dialog so the
    // user can drop it onto a day/meal immediately.
    if (typeof saveRecipe === 'function') {
      const actions = document.createElement('div');
      actions.className = 'recipe-idea-actions';
      const saveBtn = document.createElement('button');
      saveBtn.type = 'button';
      saveBtn.className = 'chip-btn compact';
      saveBtn.textContent = t('recipeIdeasSave');
      const planBtn = document.createElement('button');
      planBtn.type = 'button';
      planBtn.className = 'chip-btn accent compact';
      planBtn.textContent = t('recipeIdeasPlan');
      const doSave = async () => {
        try {
          const kcal = Math.round(r.kcal_estimate || 0);
          const components = kcal > 0
            ? [{ product_name: r.name, grams: 0, kcal, carbs_g: 0, fat_g: 0, sat_fat_g: 0, sugars_g: 0, protein_g: 0, salt_g: 0 }]
            : [];
          const saved = await saveRecipe({ name: r.name, components, servings: 1 });
          toast?.(t('recipeIdeasSaved'), 'ok');
          return saved;
        } catch (err) {
          console.warn('[recipeIdeas] save failed', err);
          toast?.(t('recipeIdeasSaveFailed'), 'error');
          return null;
        }
      };
      saveBtn.addEventListener('click', doSave);
      planBtn.addEventListener('click', async () => {
        const saved = await doSave();
        if (saved && typeof openMealPlan === 'function') {
          $('recipe-ideas-dialog')?.close();
          openMealPlan();
        }
      });
      actions.appendChild(saveBtn);
      actions.appendChild(planBtn);
      card.appendChild(actions);
    }
    list.appendChild(card);
  }
}

async function fetchRecipes({ direct, server, cacheKey }) {
  // Fix #12 — return cached result without hitting the LLM.
  if (cacheKey) {
    const hit = _recipeCache.get(cacheKey);
    if (hit) return hit;
  }

  const { getMode, getKey, loadEngine, t } = deps;
  const mode = getMode();
  let result;
  if (mode === 'direct') {
    const engine = await loadEngine();
    const key = getKey();
    if (!key) throw new Error(t('errMissingKey'));
    try {
      result = await direct(engine, key);
    } catch (err) {
      // R22.2: 429 rate-limit translation for the direct path —
      // matches the identifyViaModePath pattern in app.js so the
      // user sees the same "try again in a moment" message rather
      // than a raw HTTP error.
      if (err?.status === 429) throw new Error(t('errRateLimit'));
      throw err;
    }
  } else {
    const res = await fetch(server.url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(server.body),
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
  // Fix #12 — store in session cache so the next call with the same
  // ingredient / pantry skips the LLM entirely.
  if (cacheKey && result) _recipeCache.set(cacheKey, result);
  return result;
}

async function runAndRender(intro, fetcher) {
  const { t } = deps;
  const dialog = $('recipe-ideas-dialog');
  if (!dialog) return;
  $('recipe-ideas-intro').textContent = intro;
  $('recipe-ideas-list').textContent = '';
  $('recipe-ideas-status').textContent = t('recipeIdeasLoading');
  dialog.showModal();
  let result;
  try {
    result = await fetcher();
  } catch (err) {
    console.warn('[recipeIdeas]', err);
    // R22.2: surface a translated rate-limit message instead of the
    // generic "failed" text so the user knows to retry, not to
    // abandon the feature.
    const msg = err?.message === t('errRateLimit')
      ? err.message
      : t('recipeIdeasFailed');
    $('recipe-ideas-status').textContent = msg;
    return;
  }
  const recipes = Array.isArray(result?.recipes) ? result.recipes : [];
  if (recipes.length === 0) {
    $('recipe-ideas-status').textContent = t('recipeIdeasEmpty');
    return;
  }
  $('recipe-ideas-status').textContent = '';
  renderCards(recipes);
}

export async function openRecipeIdeas(ingredient) {
  const { t } = deps;
  if (!ingredient) return;
  await runAndRender(
    t('recipeIdeasIntro', { name: ingredient }),
    () => fetchRecipes({
      cacheKey: _recipeKey('ingredient', ingredient),
      direct: (engine, key) => engine.suggestRecipes(ingredient, { apiKey: key }),
      server: { url: apiUrl('/api/suggest-recipes'), body: { ingredient } },
    }),
  );
}

export async function openPantryIdeas(pantry) {
  const { t } = deps;
  if (!Array.isArray(pantry) || pantry.length === 0) return;
  const previewName = pantry.slice(0, 3).join(', ') + (pantry.length > 3 ? '…' : '');
  await runAndRender(
    t('recipeIdeasPantryIntro', { ingredients: previewName }),
    () => fetchRecipes({
      cacheKey: _recipeKey('pantry', pantry),
      direct: (engine, key) => engine.suggestRecipesFromPantry(pantry, { apiKey: key }),
      server: { url: apiUrl('/api/suggest-from-pantry'), body: { pantry } },
    }),
  );
}

export function initRecipeIdeas(injected) {
  deps = injected;
  $('recipe-ideas-close')?.addEventListener('click', (e) => {
    e.preventDefault();
    $('recipe-ideas-dialog')?.close();
  });
}
