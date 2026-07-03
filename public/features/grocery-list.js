/**
 * Grocery list aggregator.
 *
 * Given a list of recipes (each with `components: [{ product_name,
 * grams, ... }]`), sum the grams per ingredient name and emit a flat
 * shopping list. Names that case-insensitively match the same key
 * collapse into one row.
 *
 * Intentionally tiny: this isn't the place to model categories ("frais"
 * vs "épicerie"), branded products, or supermarket aisles. It's a
 * Saturday-morning utility — paste the list into a notes app.
 *
 * No IDB / persistence — the user opens the dialog, picks recipes,
 * gets the list. Ephemeral by design.
 */

function normalizeKey(name) {
  // R30.1: regex uses \u escape codes instead of literal combining
  // diacritical marks in the source. Behaviour-identical (matches
  // the Unicode Combining Diacritical Marks block U+0300..U+036F)
  // but portable across editors / transpilers that may mishandle
  // raw combining chars.
  return String(name ?? '').trim().toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/**
 * Aggregate a list of recipes into a deduplicated grocery list.
 *
 *   aggregateGroceryList([
 *     { name: 'Pesto pâtes', servings: 2, components: [
 *         { product_name: 'pâtes', grams: 200 },
 *         { product_name: 'parmesan', grams: 30 },
 *     ]},
 *     { name: 'Salade caprese', servings: 1, components: [
 *         { product_name: 'tomate', grams: 250 },
 *         { product_name: 'mozzarella', grams: 125 },
 *     ]},
 *   ])
 *   →
 *   [
 *     { name: 'mozzarella', grams: 125, sources: ['Salade caprese'] },
 *     { name: 'parmesan',   grams: 30,  sources: ['Pesto pâtes'] },
 *     { name: 'pâtes',      grams: 200, sources: ['Pesto pâtes'] },
 *     { name: 'tomate',     grams: 250, sources: ['Salade caprese'] },
 *   ]
 *
 * Sorted alphabetically by ingredient name, accent-folded.
 * `grams` is the sum across recipes; `sources` lists every recipe
 * that needs it (deduped, in insertion order). Components without a
 * grams field contribute 0 — useful for "salt to taste" rows.
 */
export function aggregateGroceryList(recipes) {
  const acc = new Map(); // key → { name, grams, sources[] }
  const recipeArr = Array.isArray(recipes) ? recipes : [];
  for (const r of recipeArr) {
    if (!r || !Array.isArray(r.components)) continue;
    for (const c of r.components) {
      const rawName = String(c?.product_name ?? '').trim();
      if (!rawName) continue;
      const key = normalizeKey(rawName);
      const grams = Math.max(0, Number(c?.grams) || 0);
      let entry = acc.get(key);
      if (!entry) {
        entry = { name: rawName, grams: 0, sources: [] };
        acc.set(key, entry);
      }
      entry.grams += grams;
      const recipeName = String(r.name ?? '').trim() || '—';
      if (!entry.sources.includes(recipeName)) entry.sources.push(recipeName);
    }
  }
  // Round grams; sort by accent-folded name.
  const out = [...acc.values()].map((e) => ({
    name: e.name,
    grams: Math.round(e.grams),
    sources: e.sources,
  }));
  out.sort((a, b) => normalizeKey(a.name).localeCompare(normalizeKey(b.name)));
  return out;
}

/**
 * Format the aggregated list as a single plain-text block ready for
 * copy/paste into a notes app. Lines look like:
 *   - tomate · 250 g
 *   - parmesan · 30 g
 *
 * R34.N4: `markdown: true` emits GitHub-style task-list checkboxes
 * (`- [ ] tomate · 250 g`) so the pasted result renders as checkable
 * items in GitHub Issues, Obsidian, Bear, Notion, and most modern
 * note apps. Default behaviour unchanged for existing callers.
 */
export function formatGroceryList(items, opts = {}) {
  const prefix = opts.markdown ? '- [ ] ' : '- ';
  return items
    .map((it) => `${prefix}${it.name}${it.grams > 0 ? ` · ${it.grams} g` : ''}`)
    .join('\n');
}

/**
 * Grocery-list dialog UI: wires the "📝 Liste de courses" button,
 * builds the aggregated list + share text, and handles markdown
 * toggle / copy / share / close. Extracted from app.js (Phase 14 —
 * same deps-object convention as the rest of public/features/*).
 *
 * Grocery list dialog: aggregates ingredients across the recipes
 * ticked in the recipes-list. If nothing is ticked, takes ALL
 * recipes (the "list all my recipes" weekly-shopping use case).
 */
export function initGroceryListDialog({ $, t, toast, show, shareOrCopy, listRecipes }) {
  const groceryDialog = $('grocery-dialog');

  async function openGroceryList() {
    const all = await listRecipes().catch(() => []);
    const checked = Array.from(document.querySelectorAll('#recipes-list .tpl-pick:checked'));
    const ids = new Set(checked.map((el) => el.dataset.recipeId));
    const picked = ids.size > 0 ? all.filter((r) => ids.has(r.id)) : all;
    if (picked.length === 0) { toast(t('groceryEmpty'), 'warn'); return; }
    const items = aggregateGroceryList(picked);
    const list = $('grocery-list');
    const text = $('grocery-text');
    const source = $('grocery-source');
    if (source) source.textContent = t('grocerySource', { n: picked.length });
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
        // F-F-04: show per-ingredient recipe breakdown when the
        // ingredient is used in ≥ 2 recipes (the "why 400g of oignon?"
        // question). Single-source rows stay clean.
        if (Array.isArray(it.sources) && it.sources.length > 1) {
          const from = document.createElement('span');
          from.className = 'grocery-from';
          from.textContent = t('groceryFrom', { sources: it.sources.join(' + ') });
          li.appendChild(from);
        }
        list.appendChild(li);
      }
    }
    // R34.N4: honour the markdown checkbox for checkbox-style output.
    const useMd = !!$('grocery-markdown')?.checked;
    if (text) text.value = formatGroceryList(items, { markdown: useMd });
    // Share btn falls back to clipboard via shareOrCopy, so it's useful
    // on every platform — just reveal it when the dialog opens.
    const shareBtn = $('grocery-share');
    if (shareBtn) show(shareBtn);
    groceryDialog?.showModal();
  }

  $('recipe-grocery-btn')?.addEventListener('click', openGroceryList);
  // R34.N4: re-render the textarea when the user toggles the markdown
  // checkbox. We already have a reference to `items` only within
  // openGroceryList; cheapest path is to re-run the aggregation.
  $('grocery-markdown')?.addEventListener('change', () => openGroceryList());
  $('grocery-close')?.addEventListener('click', (e) => { e.preventDefault(); groceryDialog?.close(); });
  $('grocery-copy')?.addEventListener('click', async (e) => {
    e.preventDefault();
    const text = $('grocery-text')?.value || '';
    try {
      await navigator.clipboard?.writeText(text);
      // R16.5: success → 'ok' for stripe consistency.
      toast(t('groceryCopied'), 'ok');
    } catch {
      // Fallback: select the textarea so the user can copy manually.
      $('grocery-text')?.select();
    }
  });
  $('grocery-share')?.addEventListener('click', async (e) => {
    e.preventDefault();
    const text = $('grocery-text')?.value || '';
    if (!text) return;
    await shareOrCopy({
      title: t('groceryTitle'),
      text,
      toasts: { copied: t('groceryCopied'), failed: t('groceryShareFailed') },
      toast,
    });
  });

  return { openGroceryList };
}
