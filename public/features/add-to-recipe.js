/**
 * "Add to recipe" picker (Gap fix 3) — lets the user append the
 * currently-scanned product as a component of one of their saved
 * recipes. Surfaces a small inline picker under the scan result;
 * picking a recipe appends a row carrying the product's per-100 g
 * macros (+ all R34 micros when OFF reports them) and persists via
 * saveRecipe (upsert by id).
 *
 * Extracted from app.js (Phase 14) — same deps-object convention as
 * the rest of public/features/*. `getLastData` is a thunk (not a
 * plain value) because the scanned product changes after every scan;
 * reading through the thunk at click time avoids a stale snapshot,
 * matching the pattern already used for scan-result-render.js and
 * comparison.js.
 */
export function initAddToRecipe({ $, t, toast, listRecipes, saveRecipe, getLastData }) {
  $('add-to-recipe-btn')?.addEventListener('click', async () => {
    const picker = $('add-to-recipe-picker');
    if (!picker) return;
    const lastData = getLastData();
    if (!lastData?.product) { toast(t('logNoScan'), 'warn'); return; }
    // Toggle close-on-second-click.
    if (!picker.hidden) { picker.hidden = true; picker.textContent = ''; return; }
    const recipes = await listRecipes().catch(() => []);
    picker.textContent = '';
    if (recipes.length === 0) {
      const hint = document.createElement('li');
      hint.className = 'add-to-recipe-empty';
      hint.textContent = t('addToRecipeNoRecipes');
      picker.appendChild(hint);
      picker.hidden = false;
      return;
    }
    const header = document.createElement('li');
    header.className = 'add-to-recipe-header';
    header.textContent = t('addToRecipePickTitle');
    picker.appendChild(header);
    for (const r of recipes) {
      const li = document.createElement('li');
      li.className = 'add-to-recipe-item';
      li.setAttribute('role', 'option');
      li.textContent = r.name || t('untitledRecipe');
      li.addEventListener('click', async () => {
        try {
          const p = getLastData().product;
          const n = p.nutrition || {};
          // Component shape mirrors saveRecipe's writer (it strips
          // through the RECIPE_MICRO_FIELDS list and zeros missing
          // values). Grams default to 100 so aggregateRecipe's
          // per-100g math stays correct until the user tweaks them.
          const component = {
            product_name: p.name || t('untitledRecipe'),
            grams: 100,
            kcal: Number(n.energy_kcal) || 0,
            carbs_g: Number(n.carbs_g) || 0,
            fat_g: Number(n.fat_g) || 0,
            sat_fat_g: Number(n.saturated_fat_g) || 0,
            sugars_g: Number(n.sugars_g) || 0,
            protein_g: Number(n.protein_g) || 0,
            salt_g: Number(n.salt_g) || 0,
            fiber_g: Number(n.fiber_g) || 0,
            iron_mg: Number(n.iron_mg) || 0,
            calcium_mg: Number(n.calcium_mg) || 0,
            magnesium_mg: Number(n.magnesium_mg) || 0,
            potassium_mg: Number(n.potassium_mg) || 0,
            zinc_mg: Number(n.zinc_mg) || 0,
            sodium_mg: Number(n.sodium_mg) || 0,
            vit_a_ug: Number(n.vit_a_ug) || 0,
            vit_c_mg: Number(n.vit_c_mg) || 0,
            vit_d_ug: Number(n.vit_d_ug) || 0,
            vit_e_mg: Number(n.vit_e_mg) || 0,
            vit_k_ug: Number(n.vit_k_ug) || 0,
            b1_mg: Number(n.b1_mg) || 0,
            b2_mg: Number(n.b2_mg) || 0,
            b3_mg: Number(n.b3_mg) || 0,
            b6_mg: Number(n.b6_mg) || 0,
            b9_ug: Number(n.b9_ug) || 0,
            b12_ug: Number(n.b12_ug) || 0,
            polyunsaturated_fat_g: Number(n.polyunsaturated_fat_g) || 0,
            monounsaturated_fat_g: Number(n.monounsaturated_fat_g) || 0,
            omega_3_g: Number(n.omega_3_g) || 0,
            omega_6_g: Number(n.omega_6_g) || 0,
            cholesterol_mg: Number(n.cholesterol_mg) || 0,
          };
          await saveRecipe({
            id: r.id,
            name: r.name,
            servings: r.servings || 1,
            components: [...(r.components || []), component],
          });
          picker.hidden = true;
          picker.textContent = '';
          toast(t('addToRecipeDone', {
            food: component.product_name,
            recipe: r.name || t('untitledRecipe'),
          }), 'ok');
        } catch (err) { console.error('[add-to-recipe]', err); }
      });
      picker.appendChild(li);
    }
    picker.hidden = false;
  });
}
