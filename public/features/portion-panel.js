/**
 * Portion-panel + unit-convert UI for the post-scan logging flow.
 *
 * Owns:
 *   - "How much did you eat?" grams input + presets + half-pack chip.
 *   - Time-of-day default meal selection.
 *   - kcal preview that follows the grams input live.
 *   - Free-text unit converter ("1 cup of rice" → grams) feeding the
 *     same input.
 *
 * Pure DOM module — no IDB / network. The owner (app.js) calls
 * setupPortionPanel(product) after each scan and again after edits.
 *
 * Init shape:
 *   initPortionPanel({
 *     $, t, hide, show,
 *     portionInput, portionMealSelect, portionPresetPack, logToast,
 *     defaultMealForHour, parseUnitInput, toGrams,
 *     getLastData,            // () => last scan's data | null
 *   })
 *   returns { setupPortionPanel(product), updateLogPreview(product) }
 */

export function initPortionPanel(deps) {
  const {
    $, t, hide,
    portionInput, portionMealSelect, portionPresetPack, logToast,
    logKcalPreview,
    defaultMealForHour, parseUnitInput, toGrams,
    getLastData,
  } = deps;

  function updateLogPreview(product) {
    const g = Math.max(0, Number(portionInput.value) || 0);
    const per100 = product?.nutrition?.energy_kcal ?? 0;
    const kcal = Math.round((per100 * g) / 100);
    logKcalPreview.textContent = kcal > 0 ? ` (${kcal} kcal)` : '';
  }

  function setupPortionPanel(product) {
    const weight = product?.weight_g;
    const defaultG = weight && weight > 0 && weight < 2000 ? weight : 100;
    portionInput.value = String(defaultG);
    // Default meal matches time-of-day (same logic as Quick Add) so a user
    // scanning a product at 8am sees "breakfast" pre-selected, not "snack".
    if (portionMealSelect) {
      portionMealSelect.value = defaultMealForHour(new Date().getHours());
    }
    // R13.1 — `(paquet)` was hard-coded French; EN users saw a French
    // word in an otherwise-translated UI. Now resolved via t().
    // R13.8 — also surface a half-pack chip when the package is large
    // enough to make halving meaningful (≥40 g, so we don't show
    // "8 g (½ paquet)" for a tiny chocolate square).
    const halfPackEl = $('portion-preset-half-pack');
    if (weight && weight > 0 && weight < 2000) {
      portionPresetPack.textContent = t('portionPack', { g: weight });
      portionPresetPack.dataset.portion = String(weight);
      portionPresetPack.hidden = false;
      if (halfPackEl) {
        if (weight >= 40) {
          const half = Math.round(weight / 2);
          halfPackEl.textContent = t('portionHalfPack', { g: half });
          halfPackEl.dataset.portion = String(half);
          halfPackEl.hidden = false;
        } else {
          halfPackEl.hidden = true;
        }
      }
    } else {
      portionPresetPack.hidden = true;
      if (halfPackEl) halfPackEl.hidden = true;
    }
    updateLogPreview(product);
    hide(logToast);
  }

  // -- Listeners (idempotent; this module is initialised once at boot.) --

  portionInput?.addEventListener('input', () => {
    const lastData = getLastData();
    if (lastData) updateLogPreview(lastData.product);
  });

  $('portion-panel')?.addEventListener('click', (e) => {
    const btn = e.target.closest('.chip-btn[data-portion]');
    if (!btn) return;
    portionInput.value = btn.dataset.portion;
    const lastData = getLastData();
    if (lastData) updateLogPreview(lastData.product);
  });

  // Unit conversion — cup/tbsp/oz → grams, density-aware via the
  // current scan's product name when available.
  function applyUnitConvert() {
    const input = $('unit-convert-input');
    const out = $('unit-convert-result');
    const raw = (input?.value || '').trim();
    if (!raw) return;
    const parsed = parseUnitInput(raw);
    const lastData = getLastData();
    const foodName = parsed?.name || lastData?.product?.name || '';
    const grams = parsed ? toGrams(parsed.amount, parsed.unit, foodName) : null;
    if (!grams || grams <= 0) {
      if (out) {
        out.textContent = t('unitConvertBadInput');
        out.dataset.state = 'warn';
        out.hidden = false;
      }
      return;
    }
    portionInput.value = String(Math.round(grams));
    if (lastData) updateLogPreview(lastData.product);
    if (out) {
      out.textContent = t('unitConvertResult', {
        amount: parsed.amount, unit: parsed.unit,
        name: foodName || t('unitConvertNoName'),
        grams: Math.round(grams),
      });
      delete out.dataset.state;
      out.hidden = false;
    }
  }
  $('unit-convert-apply')?.addEventListener('click', applyUnitConvert);
  $('unit-convert-input')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); applyUnitConvert(); }
  });

  return { setupPortionPanel, updateLogPreview };
}
