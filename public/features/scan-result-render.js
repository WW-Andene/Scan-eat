/**
 * Scan result rendering — grade/score card, personal-score overlay,
 * allergens, sparse-data hint, classic pairings, ingredient list,
 * nutrition table, additive summary, pillar/explanation dialogs, share,
 * and read-aloud (SpeechSynthesis). ADR-0004 feature-folder pattern,
 * extracted per the app.js decomposition plan (Phase 8).
 *
 * This is the largest extracted cluster (~890 LOC in the original
 * audit) and has 2 confirmed external consumers besides the normal scan
 * flow: scanner.js's flow renders via deps.renderAudit from app.js, and
 * scan-history-ui.js calls renderAudit / renderIngredients /
 * renderNutrition directly when reopening a past scan (H7). Neither
 * consumer's call signature changes — app.js re-exports the same
 * function references it gets back from initScanResultRender().
 *
 * Functions still owned by app.js at extraction time (alternatives
 * lookups, history-alternative lookup, the portion-panel hook, the
 * generic renderList/makeActivatable/ensureAdditivesIndex helpers, and
 * the lastData / pairedHit module state) are supplied via the
 * `initScanResultRender(deps)` factory, matching the deps-object
 * convention used by scanner.js / recipes-dialog.js / scan-pipeline.js.
 *
 * deps shape:
 *   { t, toast,
 *     resultSourceEl, resultConfidenceEl, shareBtn, compareNextBtn,
 *     explainTitle, explainBody, explainDialog,
 *     pillarDialogTitle, pillarDialogList, pillarDialog,
 *     additiveSummaryEl,
 *     maybeRenderAlternatives,    ← (data) => Promise<void>
 *     maybeShowHistoryAlternative,← (data) => Promise<void>
 *     setupPortionPanel,          ← (product) => void (lazy-bound; the
 *                                   real fn is assigned by app.js after
 *                                   initPortionPanel() runs, but is only
 *                                   ever called at scan time, well after
 *                                   that assignment has happened)
 *     renderList,                 ← (id, items, emptyLabel) => void
 *     makeActivatable,            ← (el, onActivate) => void
 *     ensureAdditivesIndex,       ← () => Promise<void>
 *     getLastData,                ← () => current lastData
 *     setPairedHit }              ← (name, hit) => void — mirrors
 *                                   app.js's pairedIngredientName/
 *                                   pairedHit state (consumed by the
 *                                   recipe-ideas/pairings-share buttons
 *                                   that stay in app.js)
 *
 * DOM elements not passed via deps (#grade-el, #ingredient-list, etc.)
 * are looked up by id directly, matching the existing convention in
 * this cluster — no id renaming in this restructuring (H6).
 */
import { show, hide } from '../core/dom-helpers.js';
import { t as i18nT, currentLang } from '../core/i18n.js';
import { detectAllergens } from '../core/allergens.js';
import { explainFlag } from '../core/explanations.js';
import { matchPairings } from '../data/pairings.js';
import { shareOrCopy } from '../core/share.js';
import { computePersonalScore, personalGrade } from '../core/personal-score.js';
import { getProfile } from '../data/profile.js';
import { computeConfidence } from '../core/presenters.js';

const $ = (id) => document.getElementById(id);

// The scoring engine returns an English prose verdict baked into audit.verdict
// (src/scoring-engine.ts gradeVerdict()) — that string is never localized, so
// it leaked raw English into the French UI. We ignore that field for display
// and translate by grade instead; audit.verdict is kept only as the EN
// fallback for read-aloud / non-UI consumers.
const GRADE_VERDICT_KEY = { 'A+': 'verdictAPlus', A: 'verdictA', B: 'verdictB', C: 'verdictC', D: 'verdictD', F: 'verdictF' };

function initScanResultRender(deps) {
  const {
    t = i18nT, toast,
    resultSourceEl, resultConfidenceEl, shareBtn, compareNextBtn,
    explainTitle, explainBody, explainDialog,
    pillarDialogTitle, pillarDialogList, pillarDialog,
    additiveSummaryEl,
    maybeRenderAlternatives, maybeShowHistoryAlternative,
    setupPortionPanel,
    renderList, makeActivatable,
    ensureAdditivesIndex,
    getLastData,
    setPairedHit,
  } = deps;

  function verdictText(grade) {
    return t(GRADE_VERDICT_KEY[grade] || 'verdictB');
  }

  function renderAudit(data) {
    const { audit, warnings } = data;
    $('grade-el').textContent = audit.grade;
    $('grade-el').dataset.grade = audit.grade;
    $('score-el').textContent = String(audit.score);
    $('verdict-el').textContent = verdictText(audit.grade);
    $('product-name').textContent = audit.product_name || '(—)';
    $('product-category').textContent = audit.category.replace(/_/g, ' ');

    // Milestone success moment: A+ grade lands → briefly intensify
    // the .score-card's notebook-margin rule + coral halo pulse
    // (§DST4 + Step 14). Also fires the grade-chip reveal animation.
    // Auto-removes so the next scan that lands A+ triggers fresh.
    const scoreCard = $('score-card');
    const gradeEl = $('grade-el');
    if (scoreCard && gradeEl) {
      gradeEl.classList.remove('grade-chip-reveal');
      // Force reflow so the re-added class re-runs the animation.
      void gradeEl.offsetWidth;
      gradeEl.classList.add('grade-chip-reveal');
      if (audit.grade === 'A+') {
        scoreCard.classList.remove('success-burst');
        void scoreCard.offsetWidth;
        scoreCard.classList.add('success-burst');
        setTimeout(() => scoreCard.classList.remove('success-burst'), 1000);
      }
    }

    renderPersonalScore(audit, data.product);

    // Suggest "similar but better" alternatives for mediocre scans or veto'd
    // products. Fire-and-forget — never block the main render on a network
    // call; the section reveals itself when ready.
    maybeRenderAlternatives(data).catch(() => { /* non-critical */ });

    // Fix #19: reveal the "better from history" button when a higher-scoring
    // same-category product is already in scan history.
    maybeShowHistoryAlternative(data).catch(() => { /* non-critical */ });

    renderPairings(data);

    if (data.source === 'openfoodfacts') {
      resultSourceEl.textContent = t('sourceOFF');
      show(resultSourceEl);
    } else if (data.source === 'merged') {
      resultSourceEl.textContent = t('sourceMerged');
      show(resultSourceEl);
    } else if (data.source === 'llm') {
      resultSourceEl.textContent = t('sourceLLM');
      show(resultSourceEl);
    } else hide(resultSourceEl);

    const conf = computeConfidence(data);
    resultConfidenceEl.dataset.level = conf;
    resultConfidenceEl.textContent = conf === 'high' ? t('confidenceHigh')
      : conf === 'low' ? t('confidenceLow') : t('confidenceMed');
    show(resultConfidenceEl);

    // Eco-score chip — only populated for OFF-sourced products where OFF has
    // computed one. Purely informational; not part of our own scoring.
    // Gap fix #15: when OFF also reports ecoscore_value (the 0-100
    // numeric score behind the letter grade), append it to the chip
    // text + tooltip so users see the quantitative signal, not just
    // the coloured letter. Grade alone is equivalent to the food-grade
    // chip; the value adds the "how far from A" information.
    const ecoEl = $('result-ecoscore');
    const ecoGrade = data.product?.ecoscore_grade;
    const ecoValue = data.product?.ecoscore_value;
    if (ecoEl) {
      if (ecoGrade && /^[a-e]$/.test(ecoGrade)) {
        ecoEl.dataset.eco = ecoGrade;
        const valueSuffix = typeof ecoValue === 'number' && Number.isFinite(ecoValue)
          ? ` · ${Math.round(ecoValue)}/100`
          : '';
        ecoEl.textContent = t('ecoscoreChip', { grade: ecoGrade.toUpperCase() }) + valueSuffix;
        ecoEl.title = typeof ecoValue === 'number'
          ? t('ecoscoreTooltipWithValue', { grade: ecoGrade.toUpperCase(), value: Math.round(ecoValue) })
          : t('ecoscoreTooltip');
        show(ecoEl);
      } else {
        hide(ecoEl);
      }
    }

    renderAllergens(data.product);
    renderSparseHint(data);
    renderAdditiveSummary(data.product);
    setupPortionPanel(data.product);
    show(shareBtn);
    // Gap fix 3 — reveal the add-to-recipe button once a scan is on
    // screen. Hidden by default and when the user dismisses the scan.
    const addBtn = $('add-to-recipe-btn');
    if (addBtn) addBtn.hidden = false;
    const picker = $('add-to-recipe-picker');
    if (picker) { picker.hidden = true; picker.textContent = ''; }

    renderList('red-flags', audit.red_flags, t('noFlag'));
    renderList('green-flags', audit.green_flags, t('noFlag'));

    const pillars = [
      [t('pillarProcessing'), audit.pillars.processing],
      [t('pillarDensity'), audit.pillars.nutritional_density],
      [t('pillarNegatives'), audit.pillars.negative_nutrients],
      [t('pillarAdditives'), audit.pillars.additive_risk],
      [t('pillarIntegrity'), audit.pillars.ingredient_integrity],
    ];
    const pillarList = $('pillar-list'); pillarList.textContent = '';
    for (const [label, pillar] of pillars) {
      const pct = Math.round((pillar.score / pillar.max) * 100);
      const safePct = Number.isFinite(pct) ? Math.max(0, Math.min(100, pct)) : 0;
      const li = document.createElement('li');
      li.className = 'pillar-row pillar-clickable';

      const labelSpan = document.createElement('span');
      labelSpan.className = 'pillar-label';
      labelSpan.textContent = String(label);

      const bar = document.createElement('span');
      bar.className = 'pillar-bar';
      const fill = document.createElement('span');
      fill.className = 'pillar-bar-fill';
      fill.style.width = `${safePct}%`;
      bar.appendChild(fill);

      const value = document.createElement('strong');
      value.className = 'pillar-value';
      value.textContent = `${pillar.score} / ${pillar.max}`;

      li.appendChild(labelSpan);
      li.appendChild(bar);
      li.appendChild(value);
      makeActivatable(li, () => openPillarDialog(label, pillar));
      pillarList.appendChild(li);
    }
    if (warnings?.length) {
      // Fix #15: split conflict warnings by severity (prefixed by 🔴/🟡) so
      // a NOVA-class disagreement vs a 1g nutrition delta are visually distinct.
      const highConflicts = warnings.filter((s) => s.startsWith('🔴'));
      const otherWarnings = warnings.filter((s) => !s.startsWith('🔴'));
      if (highConflicts.length) {
        const w = document.createElement('li');
        w.className = 'warn warn--high';
        w.textContent = highConflicts.join(' • ');
        pillarList.appendChild(w);
      }
      if (otherWarnings.length) {
        const w = document.createElement('li');
        w.className = 'warn warn--medium';
        w.textContent = `⚠ ${otherWarnings.join(' • ')}`;
        pillarList.appendChild(w);
      }
    }
    compareNextBtn.disabled = false;
    compareNextBtn.textContent = t('compareNext');
  }

  function renderAllergens(product) {
    const el = $('allergens');
    const hits = detectAllergens(product, currentLang);
    el.textContent = '';
    if (hits.length === 0) { hide(el); return; }
    const intro = document.createElement('span');
    intro.className = 'allergen-intro';
    intro.textContent = t('allergenIntro') + ' :';
    el.appendChild(intro);
    for (const hit of hits) {
      const chip = document.createElement('span');
      chip.className = 'allergen-chip';
      chip.textContent = hit.label;
      chip.title = hit.triggers.join(', ');
      el.appendChild(chip);
    }
    show(el);
  }

  function renderSparseHint(data) {
    const el = $('sparse-hint');
    // Defensive against older saved snapshots where ingredients / nutrition
    // might be missing from the persisted shape.
    const ings = data.product?.ingredients ?? [];
    const n = data.product?.nutrition ?? {};
    const sparse =
      ings.length === 0 ||
      ((n.energy_kcal ?? 0) === 0 && (n.protein_g ?? 0) === 0);
    if (sparse && data.source !== 'openfoodfacts') {
      el.textContent = t('sparseData');
      show(el);
    } else hide(el);
  }

  function renderPairings(data) {
    const section = $('pairings');
    const title = $('pairings-title');
    const list = $('pairings-list');
    if (!section || !title || !list) return;
    const product = data?.product || {};
    // Try the product name first, then the primary ingredient (OFF products
    // often have brand names that won't match, but their first ingredient
    // often does — e.g. a "Président Mozzarella Bio" resolves via
    // ingredients[0] = 'lait').
    let hit = matchPairings(product.name || '');
    if (!hit && Array.isArray(product.ingredients) && product.ingredients.length > 0) {
      const firstIng = product.ingredients[0]?.name || '';
      hit = matchPairings(firstIng);
    }
    list.textContent = '';
    if (!hit) {
      setPairedHit(null, null);
      hide(section);
      return;
    }
    setPairedHit(hit.name, hit);
    title.textContent = t('pairingsTitle', { name: hit.name });
    for (const p of hit.pairs.slice(0, 6)) {
      const li = document.createElement('li');
      li.className = 'pairing-chip';
      // Ahn 2011 pair shape: { b, fr, cooccur }. Use fr when available,
      // otherwise fall back to the English id with underscores stripped.
      const label = p.fr ?? p.b.replace(/_/g, ' ');
      li.textContent = label;
      // Title attr exposes the empirical strength — "co-cité dans 577
      // recettes" — without cluttering the chip itself.
      if (Number.isFinite(p.cooccur)) {
        li.title = t('pairingsSharedCompounds', { n: p.cooccur });
      }
      list.appendChild(li);
    }
    show(section);
  }

  function buildIngredientRow(ing) {
    const li = document.createElement('li');
    const info = ing.e_number ? window.__additivesIndex?.[ing.e_number] : null;

    const dot = document.createElement('span');
    dot.className = 'ing-dot';
    if (info) dot.dataset.tier = String(info.tier);
    else if (ing.category === 'additive') dot.dataset.tier = '0';
    else if (ing.is_whole_food) dot.dataset.whole = '1';
    li.appendChild(dot);

    const label = document.createElement('span');
    label.className = 'ing-label';
    const e = ing.e_number ? ` [${ing.e_number}]` : '';
    label.textContent = `${ing.name}${e}`;
    li.appendChild(label);

    if (ing.percentage != null) {
      const rawPct = Number(ing.percentage);
      const safePct = Number.isFinite(rawPct) ? Math.max(0, Math.min(100, rawPct)) : 0;
      const pct = document.createElement('span');
      pct.className = 'ing-pct';
      const bar = document.createElement('span');
      bar.className = 'ing-pct-bar';
      const fill = document.createElement('span');
      fill.className = 'ing-pct-fill';
      fill.style.width = `${safePct}%`;
      bar.appendChild(fill);
      const val = document.createElement('span');
      val.className = 'ing-pct-val';
      val.textContent = `${Number.isFinite(rawPct) ? rawPct : '?'}%`;
      pct.appendChild(bar);
      pct.appendChild(val);
      li.appendChild(pct);
    }

    if (ing.category === 'additive') li.classList.add('additive');
    if (info) {
      li.classList.add('explainable');
      makeActivatable(li, () => {
        explainTitle.textContent = `${ing.name}${e}`;
        const parts = [info.concern];
        // R15.1: "Source :" was hard-coded French. The source text
        // itself is scientific reference data (locale-neutral) but the
        // label was leaking French to EN users every time they opened
        // an additive explanation.
        if (info.source) parts.push(`\n\n${t('sourceLabel')} ${info.source}`);
        explainBody.textContent = parts.join('');
        explainDialog.showModal();
      });
    }
    return li;
  }

  function renderIngredients(product) {
    ensureAdditivesIndex();
    const host = $('ingredient-list');
    host.textContent = '';
    const ingredients = Array.isArray(product?.ingredients) ? product.ingredients : [];
    const foods = ingredients.filter((i) => i && i.category !== 'additive');
    const additives = ingredients.filter((i) => i && i.category === 'additive');

    if (foods.length > 0) {
      const headerLi = document.createElement('li');
      headerLi.className = 'ing-group-header';
      headerLi.textContent = `${t('ingFoods')} (${foods.length})`;
      host.appendChild(headerLi);
      for (const ing of foods) host.appendChild(buildIngredientRow(ing));
    }
    if (additives.length > 0) {
      const headerLi = document.createElement('li');
      headerLi.className = 'ing-group-header';
      headerLi.textContent = `${t('ingAdditives')} (${additives.length})`;
      host.appendChild(headerLi);
      for (const ing of additives) host.appendChild(buildIngredientRow(ing));
    }
  }

  function renderNutrition(product) {
    const ul = $('nutrition-list'); ul.textContent = '';
    const n = product?.nutrition;
    if (!n) return; // defensive: stale saved snapshot without nutrition
    const fmt = (v, unit) => (typeof v === 'number' ? `${v} ${unit}` : '—');
    const rows = [
      ['Énergie', fmt(n.energy_kcal, 'kcal')],
      ['Matières grasses', fmt(n.fat_g, 'g')],
      ['↳ dont saturées', fmt(n.saturated_fat_g, 'g')],
      ['Glucides', fmt(n.carbs_g, 'g')],
      ['↳ dont sucres', fmt(n.sugars_g, 'g')],
      ['Fibres', fmt(n.fiber_g, 'g')],
      ['Protéines', fmt(n.protein_g, 'g')],
      ['Sel', fmt(n.salt_g, 'g')],
    ];
    for (const [label, value] of rows) {
      const li = document.createElement('li');
      const lblSpan = document.createElement('span');
      lblSpan.textContent = label; // developer-controlled but safe-by-default
      const valStrong = document.createElement('strong');
      valStrong.textContent = value;
      li.appendChild(lblSpan);
      li.appendChild(valStrong);
      ul.appendChild(li);
    }
  }

  function openExplanation(reason) {
    const bare = reason.replace(/^VETO:\s*/i, '');
    const text = explainFlag(bare, currentLang) || reason;
    explainTitle.textContent = reason;
    explainBody.textContent = text;
    explainDialog.showModal();
  }

  function openPillarDialog(label, pillar) {
    pillarDialogTitle.textContent = `${label} — ${pillar.score} / ${pillar.max}`;
    pillarDialogList.textContent = '';
    const all = [
      ...pillar.bonuses.map((b) => ({ ...b, kind: 'bonus' })),
      ...pillar.deductions.map((d) => ({ ...d, kind: 'deduction' })),
    ];
    if (all.length === 0) {
      const li = document.createElement('li');
      li.textContent = '—';
      pillarDialogList.appendChild(li);
    }
    for (const item of all) {
      const li = document.createElement('li');
      li.className = `pillar-d-row ${item.kind}`;
      const pts = document.createElement('span');
      pts.className = 'pd-points';
      pts.textContent = `${item.points > 0 ? '+' : ''}${item.points}`;
      const reason = document.createElement('span');
      reason.className = 'pd-reason';
      reason.textContent = String(item.reason ?? '');
      li.appendChild(pts);
      li.appendChild(reason);
      if (item.evidence) {
        const ev = document.createElement('small');
        ev.className = 'pd-evidence';
        ev.textContent = String(item.evidence);
        li.appendChild(ev);
      }
      pillarDialogList.appendChild(li);
    }
    pillarDialog.showModal();
  }

  function renderAdditiveSummary(product) {
    const tiers = { 1: 0, 2: 0, 3: 0 };
    for (const ing of product.ingredients) {
      if (!ing.e_number) continue;
      const info = window.__additivesIndex?.[ing.e_number];
      if (info) tiers[info.tier]++;
    }
    const total = tiers[1] + tiers[2] + tiers[3];
    if (total === 0) {
      additiveSummaryEl.textContent = t('additiveNone');
      additiveSummaryEl.dataset.worst = 'none';
    } else {
      additiveSummaryEl.textContent = t('additiveSummary', {
        total, t1: tiers[1], t2: tiers[2], t3: tiers[3],
      });
      additiveSummaryEl.dataset.worst = tiers[1] > 0 ? '1' : tiers[2] > 0 ? '2' : '3';
    }
  }

  async function shareCurrentScan() {
    const lastData = getLastData();
    if (!lastData) return;
    const text = t('shareText', {
      name: lastData.audit.product_name || t('productFallbackName'),
      score: lastData.audit.score,
      grade: lastData.audit.grade,
    });
    await shareOrCopy({
      title: 'Scan\'eat',
      text,
      toasts: { copied: t('shareCopied'), failed: t('shareFailed') },
      toast,
    });
  }

  const personalSlot = $('personal-slot');
  const personalAdjustmentsEl = $('personal-adjustments');
  const personalAdjustmentsList = $('personal-adjustments-list');

  function renderPersonalScore(audit, product) {
    const profile = getProfile();
    const r = computePersonalScore(audit, product, profile, currentLang);
    personalSlot.classList.toggle('veto', !!r.veto);
    if (!r.applicable) {
      hide(personalSlot);
      hide(personalAdjustmentsEl);
      return;
    }
    const g = personalGrade(r.personal_score);
    $('personal-grade-el').textContent = r.veto ? '⛔' : g;
    $('personal-grade-el').dataset.grade = r.veto ? 'F' : g;
    $('personal-score-el').textContent = r.veto ? '0' : String(r.personal_score);
    const deltaEl = $('personal-delta-el');
    if (r.veto) {
      deltaEl.textContent = t('vetoLabel');
      deltaEl.dataset.sign = 'neg';
    } else if (r.delta === 0) {
      deltaEl.textContent = '';
    } else {
      deltaEl.textContent = r.delta > 0 ? `(+${r.delta})` : `(${r.delta})`;
      deltaEl.dataset.sign = r.delta > 0 ? 'pos' : r.delta < 0 ? 'neg' : 'zero';
    }
    $('personal-verdict-el').textContent = r.diet_reason || '';
    show(personalSlot);

    personalAdjustmentsList.textContent = '';
    if (r.adjustments.length === 0) {
      hide(personalAdjustmentsEl);
      return;
    }
    if (r.veto) {
      const note = document.createElement('li');
      note.className = 'pa-row veto';
      note.textContent = t('vetoExplain');
      personalAdjustmentsList.appendChild(note);
    }
    for (const a of r.adjustments) {
      const li = document.createElement('li');
      const posNeg = a.points > 0 ? 'positive' : a.points < 0 ? 'negative' : 'neutral';
      li.className = `pa-row ${a.category} ${posNeg} ${a.veto ? 'veto-row' : ''}`.trim();
      const pts = document.createElement('span');
      pts.className = 'pa-points';
      pts.textContent = a.veto ? '⛔' : `${a.points > 0 ? '+' : ''}${a.points}`;
      const reason = document.createElement('span');
      reason.className = 'pa-reason';
      reason.textContent = String(a.reason ?? '');
      li.appendChild(pts);
      li.appendChild(reason);
      personalAdjustmentsList.appendChild(li);
    }
    show(personalAdjustmentsEl);
  }

  // ---------- Read-aloud (SpeechSynthesis) ----------

  const SPEECH = globalThis.speechSynthesis;
  let speaking = false;

  function isSpeechSupported() {
    return !!SPEECH && typeof globalThis.SpeechSynthesisUtterance === 'function';
  }

  function readAloud(text) {
    if (!isSpeechSupported() || !text) return;
    stopReading();
    const utter = new SpeechSynthesisUtterance(text);
    utter.lang = (currentLang === 'en' ? 'en-US' : 'fr-FR');
    utter.rate = 1.0;
    utter.pitch = 1.0;
    utter.onend = () => { speaking = false; updateReadAloudButton(); };
    utter.onerror = () => { speaking = false; updateReadAloudButton(); };
    speaking = true;
    updateReadAloudButton();
    SPEECH.speak(utter);
  }

  function stopReading() {
    if (!isSpeechSupported()) return;
    SPEECH.cancel();
    speaking = false;
    updateReadAloudButton();
  }

  function updateReadAloudButton() {
    const btn = document.getElementById('read-aloud-btn');
    if (!btn) return;
    btn.textContent = speaking ? t('stopReading') : t('readAloud');
  }

  /** Compose the short phrase read when the user taps Read Aloud. */
  function composeReadAloudText(data) {
    if (!data?.audit) return '';
    const name = data.audit.product_name || data.product?.name || '';
    const score = data.audit.score;
    const grade = data.audit.grade;
    const verdict = verdictText(data.audit.grade);
    if (currentLang === 'en') {
      return `${name}. Score ${score} out of 100. Grade ${grade}. ${verdict}`;
    }
    return `${name}. Score ${score} sur 100. Note ${grade}. ${verdict}`;
  }

  return {
    renderAudit, renderAllergens, renderSparseHint, renderPairings,
    renderIngredients, renderNutrition, renderAdditiveSummary,
    renderPersonalScore, buildIngredientRow, openExplanation,
    openPillarDialog, shareCurrentScan,
    readAloud, stopReading, updateReadAloudButton, composeReadAloudText,
    isSpeechSupported, isSpeaking: () => speaking,
  };
}

export { initScanResultRender };
