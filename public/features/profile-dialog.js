/**
 * Profile dialog — sex / age / height / weight / activity / diet /
 * life-stage / macro-split / modifiers / derived BMR/TDEE/BMI preview.
 *
 * Owns the open handler, all per-field listeners, macro-sum validation,
 * the custom-diet wrap toggle, the life-stage-by-sex gate, and the save
 * pipeline. Reads / writes the profile via injected getProfile /
 * setProfile.
 *
 * ADR-0004 feature-folder pattern.
 *
 * Deps shape:
 *   { t, show, hide,
 *     getProfile, setProfile, hasMinimalProfile,
 *     bmrMifflinStJeor, tdeeKcal, bmi, bmiCategory, dailyTargets,
 *     onAfterSave() }
 *
 * onAfterSave is invoked with the saved profile after the dialog closes.
 * Callers use it to re-render the open scan result (so the personal
 * score updates) or to nudge the dashboard.
 */

function $(id) { return document.getElementById(id); }

let deps = null;
let els = null;

function cacheEls() {
  els = {
    btn: $('profile-btn'),
    dialog: $('profile-dialog'),
    sex: $('profile-sex'),
    age: $('profile-age'),
    height: $('profile-height'),
    weight: $('profile-weight'),
    activity: $('profile-activity'),
    diet: $('profile-diet'),
    customForbidden: $('profile-custom-forbidden'),
    customPreferred: $('profile-custom-preferred'),
    customWrap: $('custom-diet-wrap'),
    derivedEl: $('profile-derived'),
    derivedList: $('profile-derived-list'),
    save: $('profile-save'),
    cancel: $('profile-cancel'),
  };
}

function open() {
  const { getProfile } = deps;
  const p = getProfile();
  els.sex.value = p.sex || '';
  els.age.value = p.age_years ?? '';
  els.height.value = p.height_cm ?? '';
  els.weight.value = p.weight_kg ?? '';
  $('profile-goal-weight').value = p.goal_weight_kg ?? '';
  $('profile-water-goal').value = p.water_goal_ml ?? '';
  els.activity.value = p.activity || '';
  const lifeStageEl = $('profile-life-stage');
  if (lifeStageEl) lifeStageEl.value = p.life_stage || '';
  toggleLifeStageVisibility();
  els.diet.value = p.diet || 'none';
  els.customForbidden.value = (p.custom_diet?.forbidden || []).join('\n');
  els.customPreferred.value = (p.custom_diet?.preferred || []).join('\n');
  const m = p.modifiers || {};
  $('mod-lowsugar').checked = !!m.lowSugar;
  $('mod-lowsalt').checked = !!m.lowSalt;
  $('mod-highprotein').checked = !!m.highProtein;
  $('mod-organic').checked = !!m.organic;
  $('profile-macro-split').value = p.macro_split || 'balanced';
  const c = p.macro_split_custom || { carbs: 50, protein: 20, fat: 30 };
  $('macro-custom-carbs').value = c.carbs;
  $('macro-custom-protein').value = c.protein;
  $('macro-custom-fat').value = c.fat;
  toggleCustomDietWrap();
  toggleMacroCustomWrap();
  renderDerived();
  els.dialog.showModal();
}

function toggleMacroCustomWrap() {
  const { show, hide } = deps;
  const wrap = $('macro-custom-wrap');
  const sel = $('profile-macro-split');
  if (!wrap || !sel) return;
  if (sel.value === 'custom') show(wrap); else hide(wrap);
  renderMacroSum();
}

function renderMacroSum() {
  const { t } = deps;
  const el = $('macro-custom-sum');
  if (!el) return;
  const c = Number($('macro-custom-carbs')?.value) || 0;
  const p = Number($('macro-custom-protein')?.value) || 0;
  const f = Number($('macro-custom-fat')?.value) || 0;
  const sum = c + p + f;
  const ok = Math.abs(sum - 100) <= 3;
  if (ok) {
    el.textContent = t('macroSumOk');
    el.dataset.state = 'ok';
  } else {
    const delta = Math.round(100 - sum);
    const hint = delta > 0
      ? t('macroSumAddHint', { n: delta })
      : t('macroSumRemoveHint', { n: -delta });
    el.textContent = t('macroSumOff', { v: sum }) + ' · ' + hint;
    el.dataset.state = 'off';
  }
  const split = $('profile-macro-split')?.value;
  if (els.save) {
    els.save.disabled = split === 'custom' && !ok;
    els.save.title = els.save.disabled ? t('macroSumBlocksSave') : '';
  }
}

function toggleCustomDietWrap() {
  const { show, hide } = deps;
  if (els.diet.value === 'custom') show(els.customWrap);
  else hide(els.customWrap);
}

function toggleLifeStageVisibility() {
  const { show, hide } = deps;
  const wrap = $('profile-life-stage-wrap');
  const sel = $('profile-life-stage');
  if (!wrap) return;
  if (els.sex?.value === 'female') {
    show(wrap);
  } else {
    hide(wrap);
    if (sel) sel.value = '';
  }
}

function readForm() {
  const toNum = (v) => {
    const n = parseFloat(String(v).replace(',', '.'));
    return Number.isFinite(n) && n > 0 ? n : null;
  };
  return {
    sex: els.sex.value || null,
    age_years: toNum(els.age.value),
    height_cm: toNum(els.height.value),
    weight_kg: toNum(els.weight.value),
    activity: els.activity.value || null,
    life_stage: $('profile-life-stage')?.value || null,
    diet: els.diet.value || 'none',
    custom_diet: els.diet.value === 'custom'
      ? {
          forbidden: els.customForbidden.value.split('\n').map((s) => s.trim()).filter(Boolean),
          preferred: els.customPreferred.value.split('\n').map((s) => s.trim()).filter(Boolean),
        }
      : null,
    modifiers: {
      lowSugar: !!$('mod-lowsugar')?.checked,
      lowSalt: !!$('mod-lowsalt')?.checked,
      highProtein: !!$('mod-highprotein')?.checked,
      organic: !!$('mod-organic')?.checked,
    },
    goal_weight_kg: toNum($('profile-goal-weight')?.value),
    water_goal_ml: toNum($('profile-water-goal')?.value),
    macro_split: $('profile-macro-split')?.value || 'balanced',
    macro_split_custom: {
      carbs: Number($('macro-custom-carbs')?.value) || 50,
      protein: Number($('macro-custom-protein')?.value) || 20,
      fat: Number($('macro-custom-fat')?.value) || 30,
    },
  };
}

function renderDerived() {
  const { t, show, hide, hasMinimalProfile, bmi, bmiCategory, bmrMifflinStJeor, tdeeKcal, dailyTargets } = deps;
  const p = readForm();
  if (!hasMinimalProfile(p)) { hide(els.derivedEl); return; }
  const rows = [];
  const bmiVal = bmi(p);
  const bmiCat = bmiCategory(bmiVal);
  const bmiLabel = bmiCat ? t(({
    underweight: 'bmiUnderweight',
    normal: 'bmiNormal',
    overweight: 'bmiOverweight',
    obese_1: 'bmiObese1',
    obese_2: 'bmiObese2',
    obese_3: 'bmiObese3',
  })[bmiCat] || 'bmiNormal') : '';
  rows.push([t('bmi'), `${bmiVal} (${bmiLabel})`]);
  rows.push([t('bmr'), `${bmrMifflinStJeor(p)} kcal/j`]);
  rows.push([t('tdee'), `${tdeeKcal(p)} kcal/j`]);
  const targets = dailyTargets(p);
  if (targets) {
    rows.push([t('proteinTarget'), `${targets.protein_g_target} g`]);
    rows.push([t('satfatMax'), `${targets.sat_fat_g_max} g`]);
    rows.push([t('freeSugarMax'), `${targets.free_sugars_g_max} g (idéal ${targets.free_sugars_g_ideal} g)`]);
  }
  els.derivedList.textContent = '';
  for (const [k, v] of rows) {
    const li = document.createElement('li');
    const span = document.createElement('span');
    span.textContent = String(k);
    const strong = document.createElement('strong');
    strong.textContent = String(v);
    li.appendChild(span); li.appendChild(strong);
    els.derivedList.appendChild(li);
  }
  show(els.derivedEl);
}

export function initProfileDialog(injected) {
  deps = injected;
  cacheEls();
  els.btn?.addEventListener('click', open);
  els.sex?.addEventListener('change', () => { toggleLifeStageVisibility(); renderDerived(); });
  els.diet?.addEventListener('change', () => { toggleCustomDietWrap(); renderDerived(); });
  $('profile-macro-split')?.addEventListener('change', () => { toggleMacroCustomWrap(); renderDerived(); });
  for (const id of ['macro-custom-carbs', 'macro-custom-protein', 'macro-custom-fat']) {
    $(id)?.addEventListener('input', () => { renderMacroSum(); renderDerived(); });
  }
  [els.sex, els.age, els.height, els.weight, els.activity].forEach((el) => {
    el?.addEventListener('input', renderDerived);
    el?.addEventListener('change', renderDerived);
  });
  els.save?.addEventListener('click', (e) => {
    e.preventDefault();
    const saved = readForm();
    deps.setProfile(saved);
    els.dialog.close();
    deps.onAfterSave?.(saved);
  });
  els.cancel?.addEventListener('click', (e) => { e.preventDefault(); els.dialog.close(); });
}
