/**
 * Restaurant menu scan — picker dialog.
 *
 * Different semantics from identify-multi: nothing is logged
 * automatically. Callers pass the dish list from identifyMenu() into
 * openMenuScan(), which paints one row per dish with an "Ajouter au
 * journal" button. Each button calls logQuickAdd + re-renders the
 * dashboard.
 *
 * ADR-0004 feature-folder pattern.
 *
 * Deps shape:
 *   { t, defaultMealForHour, logQuickAdd, renderDashboard }
 */

let deps = null;
function $(id) { return document.getElementById(id); }

export async function openMenuScan(dishes, opts = {}) {
  const { t, defaultMealForHour, logQuickAdd, renderDashboard } = deps;
  const dialog = $('menu-scan-dialog');
  const list = $('menu-scan-list');
  const status = $('menu-scan-status');
  if (!dialog || !list) return;
  // F-F-03: mode="plate" reuses this dialog as the safety net for the
  // multi-item photo flow. Behaviour difference: plate items were
  // going to be auto-logged; we now require explicit confirmation,
  // but add a "Log all" bulk action so the common case stays one tap.
  const mode = opts.mode === 'plate' ? 'plate' : 'menu';
  dialog.dataset.mode = mode;
  list.textContent = '';
  if (status) status.textContent = dishes.length ? '' : t('menuScanEmpty');
  const meal = $('qa-meal')?.value || defaultMealForHour(new Date().getHours());
  const remaining = new Set(dishes.map((_, i) => i));
  const rowButtons = [];
  const logOne = async (d, btn) => {
    await logQuickAdd({
      name: d.name, meal,
      kcal: Math.round(d.kcal) || 0,
      protein_g: Math.round(d.protein_g) || 0,
      carbs_g: Math.round(d.carbs_g) || 0,
      fat_g: Math.round(d.fat_g) || 0,
      sat_fat_g: 0, sugars_g: 0, salt_g: 0,
    });
    btn.disabled = true;
    btn.textContent = t('menuScanLogged');
    await renderDashboard();
  };
  for (const [idx, d] of dishes.entries()) {
    const li = document.createElement('li');
    li.className = 'menu-scan-item';
    const label = document.createElement('span');
    label.className = 'menu-scan-dish';
    label.textContent = d.name;
    const meta = document.createElement('span');
    meta.className = 'menu-scan-meta';
    meta.textContent = t('menuScanMeta', {
      kcal: Math.round(d.kcal),
      prot: Math.round(d.protein_g),
      carb: Math.round(d.carbs_g),
      fat: Math.round(d.fat_g),
    });
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'chip-btn accent compact';
    btn.textContent = t('menuScanLog');
    btn.addEventListener('click', async () => {
      await logOne(d, btn);
      remaining.delete(idx);
      updateBulk();
    });
    rowButtons.push({ btn, d, idx });
    li.appendChild(label);
    li.appendChild(meta);
    li.appendChild(btn);
    list.appendChild(li);
  }
  // F-F-03 bulk affordance — only rendered in plate mode. Sibling of
  // the list; keeps menu-mode UX unchanged (one-at-a-time logging).
  let bulkBtn = $('menu-scan-bulk');
  if (mode === 'plate') {
    if (!bulkBtn) {
      bulkBtn = document.createElement('button');
      bulkBtn.id = 'menu-scan-bulk';
      bulkBtn.type = 'button';
      bulkBtn.className = 'chip-btn accent';
      list.insertAdjacentElement('afterend', bulkBtn);
    }
    bulkBtn.hidden = false;
    bulkBtn.textContent = t('plateLogAll', { n: remaining.size });
    bulkBtn.onclick = async () => {
      for (const { btn, d, idx } of rowButtons) {
        if (remaining.has(idx)) {
          await logOne(d, btn);
          remaining.delete(idx);
        }
      }
      updateBulk();
    };
  } else if (bulkBtn) {
    bulkBtn.hidden = true;
  }
  function updateBulk() {
    if (mode !== 'plate' || !bulkBtn) return;
    if (remaining.size === 0) {
      bulkBtn.textContent = t('plateAllLogged');
      bulkBtn.disabled = true;
    } else {
      bulkBtn.textContent = t('plateLogAll', { n: remaining.size });
    }
  }
  dialog.showModal();
}

export function initMenuScan(injected) {
  deps = injected;
  $('menu-scan-close')?.addEventListener('click', (e) => {
    e.preventDefault();
    $('menu-scan-dialog')?.close();
  });
}
