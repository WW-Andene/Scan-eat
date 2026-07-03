import { t, setLang, currentLang, applyStaticTranslations } from './core/i18n.js';
import { show, hide, toast, toastWithUndo } from './core/dom-helpers.js';
import { enqueue, listPending } from './data/queue-store.js';
import { saveScan, listScans, deleteScan, clearScans } from './data/scan-history.js';
import { buildBackup, restoreBackup } from './backup.js';
import { saveProfile, switchProfile, deleteProfile } from './profiles.js';
import { isEnabled as telemetryEnabled, setEnabled as telemetrySetEnabled, logEvent as telemetryLog, clearEvents as telemetryClear, formatEvents as telemetryFormat } from './core/telemetry.js';
import { initTelemetryUi } from './features/telemetry-ui.js';
import { setSetting } from './core/app-settings.js';
import { initHydration } from './features/hydration.js';
import { initActivity } from './features/activity.js';
import { initWeight, renderWeightSummary } from './features/weight.js';
import { initReminders, scheduleReminders } from './features/reminders.js';
import { initVoiceDictate } from './features/voice-dictate.js';
import { initScanner, openCameraScanner, closeCameraScanner } from './features/scanner.js';
import { maybeShowOnboarding } from './features/onboarding.js';
import { initInstallBanner } from './features/install-banner.js';
import { initUpdateChecker } from './features/update-checker.js';
import { initComparison, maybeRenderComparison, compareArmed } from './features/comparison.js';
import { initPortionPanel } from './features/portion-panel.js';
import { initScanHistoryUi } from './features/scan-history-ui.js';
import { initBackupIO } from './features/backup-io.js';
import { initFasting, isFastingActive } from './features/fasting.js';
import { initAppearance, applyAppearance, applyTheme, applyReadingPrefs } from './features/appearance.js';
import { initTabNav, goToTab } from './features/tab-nav.js';
import { shareOrCopy } from './core/share.js';
import { dateFormatter, localeFor } from './core/date-format.js';
import { localDateISO } from './core/dateutil.js';
import { toGrams, parseUnitInput } from './core/unit-convert.js';
import { initRecipeIdeas, openRecipeIdeas, openPantryIdeas } from './features/recipe-ideas.js';
import { initSettingsDialog } from './features/settings-dialog.js';
import { initKeybindings } from './features/keybindings.js';
import { initProfileDialog } from './features/profile-dialog.js';
import { initMenuScan, openMenuScan } from './features/menu-scan.js';
import { initMealPlanUI, renderMealPlan, openMealPlan } from './features/meal-plan-ui.js';
import { initTemplatesDialog } from './features/templates-dialog.js';
import { initRecipesDialog } from './features/recipes-dialog.js';
import { initQaAutocomplete } from './features/qa-autocomplete.js';
import { buildFastCompletion, saveFastCompletion, listFastHistory, computeFastStreak, clearFastHistory } from './features/fasting-history.js';
import { getDayNote, setDayNote, DAY_NOTE_MAX_CHARS } from './features/day-notes.js';
import { searchFoodDB, reconcileWithFoodDB } from './data/food-db.js';
import { listCustomFoods } from './data/custom-food-db.js';
// pairings lookup (findPairings) is now only consumed inside
// /features/scan-result-render.js — no longer imported here.
import {
  getProfile, setProfile, hasMinimalProfile,
  bmrMifflinStJeor, tdeeKcal, bmi, bmiCategory, dailyTargets,
} from './data/profile.js';
import { computePersonalScore } from './core/personal-score.js';
import { logEntry, logQuickAdd, listByDate, listAllEntries, deleteEntry, clearDate, dailyTotals, todayISO, putEntry } from './data/consumption.js';
import { logWeight, listWeight, deleteWeight, summarize as summarizeWeight, weeklyTrend } from './data/weight-log.js';
import { saveTemplate, listTemplates, deleteTemplate, expandTemplate, templateKcal } from './data/meal-templates.js';
import { saveRecipe, listRecipes, deleteRecipe, aggregateRecipe, buildRecipeProductInput } from './data/recipes.js';
import { initAddToRecipe } from './features/add-to-recipe.js';
import { aggregateGroceryList, formatGroceryList, initGroceryListDialog } from './features/grocery-list.js';
// /features/meal-plan.js is consumed entirely via /features/meal-plan-ui.js
import { logActivity, listActivityByDate, deleteActivity, buildActivityEntry, estimateKcalBurned, sumBurned } from './data/activity.js';
import { snapshotFromData, timeAgoBucket, defaultMealForHour, parseVoiceQuickAdd, waterGoalMl, weeklyRollup, monthlyRollup, fastingStatus, entriesToDailyCSV, nextOccurrenceMs, entriesToHealthJSON, weightForecast, formatWeeklyShare, formatMonthlyShare, formatPairingsShare, formatDailySummary, formatRecipeShare, formatTemplateShare, pctClass, filterScanHistory, summarizeScanHistory, topFoods } from './core/presenters.js';
import { FOOD_DB } from './data/food-db.js';
import { checkDiet } from './core/diets.js';
import { hasNativeCamera, hasNativeBarcodeScanner, nativeTakePhoto, nativeDetectBarcodeFromBase64 } from './native-bridge.js';
import { getBarcodeDetector, detectBarcodeFromFile } from './features/barcode-scanner-detect.js';
import { compressImage } from './features/image-compression.js';
import { renderQueue, addFiles, addBarcodeOnly, removeFromQueue, firstBarcode, queuePayload } from './features/scan-queue-ui.js';
import { initScanPipeline } from './features/scan-pipeline.js';
import { updatePendingBanner, retryPending } from './features/offline-queue-sync.js';
import { checkRecipeWarnings, checkTemplateWarnings, checkQuickAddWarnings } from './core/user-content-checks.js';
import { initScanResultRender } from './features/scan-result-render.js';
import { initCustomFoodsDayNotes, initCustomFoodsDialog, renderDayNote, applyViewToggle, getDashboardView } from './features/custom-foods-day-notes.js';
import { initDashboardCharts, renderWeeklyView, renderMonthlyView, renderDashboard, renderGapCloser, renderLineChart, renderProgressCharts, round1, round3 } from './features/dashboard-charts.js';
import { initQaPhotoId, setQaStatus, setQaLoadingPhases, identifyViaModePath, readQaForm } from './features/qa-photo-identify.js';
import { setProfilesStatus, renderProfilesUI, initProfilesDialog } from './features/profiles-ui.js';

// Safari private mode + some embedded WebViews disable localStorage writes
// (getItem returns null silently, but setItem/removeItem throw). Shim the
// writers so the whole app degrades gracefully instead of crashing on the
// first preference change. Reads are already safe — they just return null.
try {
  const _set = Storage.prototype.setItem;
  const _rem = Storage.prototype.removeItem;
  Storage.prototype.setItem = function (k, v) {
    try { return _set.call(this, k, v); } catch { /* quota / disabled */ }
  };
  Storage.prototype.removeItem = function (k) {
    try { return _rem.call(this, k); } catch { /* disabled */ }
  };
} catch { /* Storage.prototype missing — nothing to protect */ }

const $ = (id) => document.getElementById(id);

const fileInput = $('file-input');
const queueEl = $('queue');
const scanBtn = $('scan-btn');
const statusEl = $('status');
const statusText = $('status-text');
const errorEl = $('error');
const resultEl = $('result');
const resetBtn = $('reset-btn');
const compareNextBtn = $('compare-next-btn');
const comparisonEl = $('comparison');
const compareClear = $('compare-clear');
const resultSourceEl = $('result-source');
const resultConfidenceEl = $('result-confidence');
const barcodeLiveBtn = $('barcode-live-btn');

const settingsBtn = $('settings-btn');
const settingsDialog = $('settings-dialog');
const keyInput = $('settings-key');
const modeSelect = $('settings-mode');
const langSelect = $('settings-language');
const settingsSave = $('settings-save');
const settingsCancel = $('settings-cancel');

const explainDialog = $('explain-dialog');
const explainTitle = $('explain-title');
const explainBody = $('explain-body');

const cameraDialog = $('camera-dialog');
const cameraVideo = $('camera-video');
const cameraStatus = $('camera-status');
const cameraClose = $('camera-close');

const pendingBanner = $('pending-banner');
const pendingText = $('pending-text');
const pendingRetry = $('pending-retry');

const updateBanner = $('update-banner');
const updateInstallBtn = $('update-install-btn');
const updateDismissBtn = $('update-dismiss-btn');
const updateVersionEl = $('update-version');

const pillarDialog = $('pillar-dialog');
const pillarDialogTitle = $('pillar-dialog-title');
const pillarDialogList = $('pillar-dialog-list');

// Onboarding dialog refs moved into /features/onboarding.js.

const historySearchInput = $('history-search');
const historyGradeSelect = $('history-grade');
const additiveSummaryEl = $('additive-summary');
const shareBtn = $('share-btn');

const LS_KEY = 'scanneat.groq_key';
const LS_MODE = 'scanneat.mode';
const LS_PREFS = 'scanneat.prefs';

const MAX_IMAGES = 4;
// Fix #10: cap the SHORT side (not the long side) at 1024 px.
// Old code capped the LONG side at 1600, so a portrait 4032×3024 became
// 2139×1600 — still a large payload. Capping the short side at 1024 yields
// 1024×1365 (portrait) or 1365×1024 (landscape), keeping all 4 images under
// ~1.5 MB total base64 and well within the 12 MB server body limit.

const isCapacitor = !!globalThis.Capacitor?.isNativePlatform?.();

import { queue } from './state/scan-queue-state.js'; // single source of truth (was: local array)
import { apiUrl } from './core/api-base.js';
let lastData = null;

// Assigned in the boot block once initRecipesDialog runs. Declared here
// so the qa-photo-multi handler (registered early) can close over the
// reference — reads happen at user-click time, always after assignment.
let recipesDialog = null;

// ============================================================================
// Preferences → Profile modifiers (moved into Profile; this block kept for
// the single UI-side responsibility: projecting the veto status onto flags).
// ============================================================================

// ============================================================================
// Helpers
// ============================================================================

let engineMod = null;
async function loadEngine() {
  if (engineMod) return engineMod;
  engineMod = await import('./engine.bundle.js');
  return engineMod;
}

function getMode() {
  const saved = localStorage.getItem(LS_MODE);
  if (saved === 'server' || saved === 'direct') return saved;
  return isCapacitor ? 'direct' : 'auto';
}
function getKey() { return localStorage.getItem(LS_KEY) || ''; }

// ============================================================================
// Barcode detection
// ============================================================================
// (extracted to features/barcode-scanner-detect.js)

// ============================================================================
// Compression
// ============================================================================
// (extracted to features/image-compression.js)

// ============================================================================
// Queue UI
// ============================================================================

// The scoring engine returns an English prose verdict baked into audit.verdict
// (src/scoring-engine.ts gradeVerdict()) — that string is never localized, so
// it leaked raw English into the French UI. We ignore that field for display
// and translate by grade instead; audit.verdict is kept only as the EN
// fallback for read-aloud / non-UI consumers.
// ============================================================================
// Scan result rendering (Phase 8 extraction)
// ============================================================================
// renderAudit, renderAllergens, renderSparseHint, renderPairings,
// renderIngredients, renderNutrition, renderAdditiveSummary,
// renderPersonalScore, buildIngredientRow, openExplanation,
// openPillarDialog, shareCurrentScan, and the read-aloud group all live
// in /features/scan-result-render.js. Called here (rather than near the
// bottom of the file) so it plugs into the deps-object convention the
// same way the rest of the boot sequence does; setupPortionPanel is
// wrapped in an indirection arrow because it isn't assigned until
// initPortionPanel() runs later — it is only ever *called* at scan
// time, well after that assignment exists. maybeRenderAlternatives,
// maybeShowHistoryAlternative, renderList, makeActivatable, and
// ensureAdditivesIndex are plain hoisted function declarations further
// down this file and are safe to reference directly here.
const {
  renderAudit, renderAllergens, renderSparseHint, renderPairings,
  renderIngredients, renderNutrition, renderAdditiveSummary,
  renderPersonalScore, buildIngredientRow, openExplanation,
  openPillarDialog, shareCurrentScan,
  readAloud, stopReading, updateReadAloudButton, composeReadAloudText,
  isSpeechSupported, isSpeaking,
} = initScanResultRender({
  t, toast,
  resultSourceEl, resultConfidenceEl, shareBtn, compareNextBtn,
  explainTitle, explainBody, explainDialog,
  pillarDialogTitle, pillarDialogList, pillarDialog,
  additiveSummaryEl,
  maybeRenderAlternatives, maybeShowHistoryAlternative,
  renderList, makeActivatable, ensureAdditivesIndex,
  setupPortionPanel: (...args) => setupPortionPanel(...args),
  getLastData: () => lastData,
  setPairedHit: (name, hit) => { pairedIngredientName = name; pairedHit = hit; },
});


// ============================================================================
// Scan execution
// ============================================================================

// (renderQueue, addFiles, addBarcodeOnly, removeFromQueue, firstBarcode,
// queuePayload extracted to features/scan-queue-ui.js)

// (scanViaServer, scanViaDirect, scanImage, enqueueCurrent extracted to
// features/scan-pipeline.js — wired below via initScanPipeline(deps))


// (updatePendingBanner, retryPending extracted to
// features/offline-queue-sync.js)

// ============================================================================
// Rendering
// ============================================================================

// renderAudit extracted to /features/scan-result-render.js (Phase 8).

// renderAllergens extracted to /features/scan-result-render.js (Phase 8).

// renderSparseHint extracted to /features/scan-result-render.js (Phase 8).

/**
 * "Similar but better" alternatives. Surfaces compliant + higher-scoring
 * products from the same OFF category when the current scan is either:
 *   - graded C/D/F (mediocre)
 *   - veto'd by the user's diet
 *
 * Silently no-ops on any failure path (no category tag available, network
 * failure, no alternatives found) — the suggestion section is decorative.
 */

/**
 * Classic-pairings chip row: "Ça va bien avec…" under the product card.
 * Offline, zero-LLM. Looks up the scanned product's name (or its first
 * ingredient when the name is a brand) against the curated PAIRINGS
 * table. Hidden when nothing matches.
 */
// Stashes the canonical ingredient name from the last successful
// renderPairings() call so the recipe-ideas button has something to send
// to the LLM without having to re-derive it on click. pairedHit retains
// the full match (name + pairs) for the Share button's recipe-card
// formatter.
let pairedIngredientName = null;
let pairedHit = null;

// renderPairings extracted to /features/scan-result-render.js (Phase 8).
// It reports back via deps.setPairedHit(name, hit), which updates the
// pairedIngredientName/pairedHit state declared above (still owned by
// app.js — consumed by the recipe-ideas/pairings-copy/pairings-share
// button handlers below).

// ============================================================================
// Recipe ideas dialog — LLM-backed, powered by /api/suggest-recipes.
// Opens from the "💡 Idées de recettes" button inside the pairings panel.
// ============================================================================

// Recipe-ideas dialog + renderer extracted to /features/recipe-ideas.js.
// openRecipeIdeas / openPantryIdeas are imported at the top; init happens
// in the boot sequence below.

$('recipe-ideas-btn')?.addEventListener('click', () => {
  if (!pairedIngredientName) return;
  openRecipeIdeas(pairedIngredientName);
});

// Pair-list "Copy" — useful for dropping into a shopping-list or a chat.
// Builds a single line from the currently-rendered chips so the text
// stays in sync with what the user is actually looking at.
$('pairings-copy-btn')?.addEventListener('click', async () => {
  const chips = document.querySelectorAll('#pairings-list .pairing-chip');
  if (chips.length === 0) return;
  const names = Array.from(chips).map((c) => c.textContent?.trim() || '').filter(Boolean);
  const header = pairedIngredientName
    ? `${t('pairingsTitle', { name: pairedIngredientName })}: `
    : '';
  const text = header + names.join(' · ');
  try {
    await navigator.clipboard?.writeText(text);
    // R16.5: success → 'ok' variant for stripe consistency.
    toast(t('pairingsCopied'), 'ok');
  } catch { toast(t('pairingsCopyFailed'), 'error'); }
});

// Pair-list "Share" — richer than Copy: builds a recipe-card-shaped block
// and hands it to navigator.share (Web Share API) on mobile, with a
// clipboard fallback elsewhere. Uses the structured hit, not the chips'
// textContent, so co-occurrence counts survive round-trip.
$('pairings-share-btn')?.addEventListener('click', async () => {
  if (!pairedHit) return;
  await shareOrCopy({
    title: t('pairingsShareTitle', { name: pairedHit.name }),
    text: formatPairingsShare(pairedHit, { lang: currentLang }),
    toasts: { copied: t('pairingsShareCopied'), failed: t('pairingsShareFailed') },
    toast,
  });
});
// Recipe-ideas close handler lives in initRecipeIdeas().

// Pantry dialog: list ingredients → suggestRecipesFromPantry → reuse
// recipe-ideas-dialog for the cards.
const pantryDialog = $('pantry-dialog');
$('recipe-pantry-btn')?.addEventListener('click', () => pantryDialog?.showModal());
$('pantry-close')?.addEventListener('click', (e) => { e.preventDefault(); pantryDialog?.close(); });
// Grocery list dialog (open/render/copy/share/close) extracted to
// /features/grocery-list.js (Phase 14) — initGroceryListDialog() wired
// in the boot block below; openGroceryList is not needed here since
// nothing else in app.js calls it directly.


// ─────────────────────────────────────────────────────────────────────
// Meal plan dialog — initMealPlanUI() wires all event handlers and owns
// renderMealPlan / openMealPlan. See /features/meal-plan-ui.js.
// ─────────────────────────────────────────────────────────────────────


async function maybeRenderAlternatives(data) {
  const section = $('alternatives');
  const list = $('alternatives-list');
  if (!section || !list) return;
  hide(section);
  list.textContent = '';

  const { audit, product } = data;
  const profile = getProfile();
  const isVeto = computePersonalScore(audit, product, profile, currentLang)?.veto;
  const poor = ['C', 'D', 'F'].includes(audit.grade);
  if (!poor && !isVeto) return;

  try {
    const { searchOFFByCategory, rankAlternatives, suggestionTagFor } = await loadEngine();
    const tag = suggestionTagFor(audit.category);
    if (!tag) return;

    const offCandidates = await searchOFFByCategory([tag], { pageSize: 20 });
    // Fix #21: fold user's saved recipes into the candidate pool so
    // the alternatives panel can surface a home-made recipe that
    // out-scores the scanned packaged product. Recipes go through
    // buildRecipeProductInput to produce a comparable ProductInput
    // with per-100 g nutrition + ingredients + computed grade via
    // scoreProduct (inside rankAlternatives itself).
    const userRecipes = await listRecipes().catch(() => []);
    const recipeCandidates = userRecipes
      .filter((r) => (r.components?.length ?? 0) > 0)
      .map((r) => buildRecipeProductInput(r))
      .map((pi) => ({ ...pi, _userRecipe: true }));
    const candidates = [...offCandidates, ...recipeCandidates];
    if (candidates.length === 0) return;

    const dietFilter = profile?.diet && profile.diet !== 'none'
      ? (p) => checkDiet(p, profile.diet, profile.custom_diet, currentLang).compliant
      : undefined;
    const alts = rankAlternatives(product, candidates, { max: 3, dietFilter });
    if (alts.length === 0) return;

    for (const { product: alt, audit: altAudit } of alts) {
      const li = document.createElement('li');
      li.className = 'alt-item';
      if (alt._userRecipe) li.dataset.source = 'recipe';
      const grade = document.createElement('span');
      grade.className = 'alt-grade';
      grade.dataset.grade = altAudit.grade;
      grade.textContent = altAudit.grade;
      const meta = document.createElement('div');
      meta.className = 'alt-meta';
      const name = document.createElement('strong');
      name.className = 'alt-name';
      // Tag user recipes with 🍽 prefix so the source is obvious.
      name.textContent = alt._userRecipe ? `🍽 ${alt.name}` : alt.name;
      const score = document.createElement('small');
      score.className = 'alt-score';
      score.textContent = `${altAudit.score} / 100`;
      meta.appendChild(name);
      meta.appendChild(score);
      li.appendChild(grade);
      li.appendChild(meta);
      list.appendChild(li);
    }
    show(section);
  } catch { /* no-op */ }
}

// Lightweight additives index populated lazily from the bundle.
async function ensureAdditivesIndex() {
  if (window.__additivesIndex) return;
  try {
    const mod = await import('./engine.bundle.js');
    if (mod.ADDITIVES_DB) {
      const idx = {};
      for (const a of mod.ADDITIVES_DB) idx[a.e_number] = a;
      window.__additivesIndex = idx;
    }
  } catch { /* ignore — fall back to name-only rendering */ }
}

// buildIngredientRow extracted to /features/scan-result-render.js (Phase 8).

// renderIngredients / renderNutrition extracted to /features/scan-result-render.js (Phase 8).

/** Make a clickable <li> also keyboard-activatable.
 *  Enter and Space open the same callback the click handler fires. */
function makeActivatable(el, onActivate) {
  el.setAttribute('role', 'button');
  el.setAttribute('tabindex', '0');
  el.addEventListener('click', onActivate);
  el.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onActivate();
    }
  });
}

function renderList(id, items, emptyLabel) {
  const ul = $(id); ul.textContent = '';
  if (!items || items.length === 0) {
    const li = document.createElement('li'); li.className = 'empty';
    li.textContent = emptyLabel; ul.appendChild(li); return;
  }
  for (const item of items) {
    const li = document.createElement('li');
    li.textContent = item;
    li.classList.add('explainable');
    makeActivatable(li, () => openExplanation(item));
    ul.appendChild(li);
  }
}

// openExplanation / openPillarDialog extracted to /features/scan-result-render.js (Phase 8).

// renderAdditiveSummary extracted to /features/scan-result-render.js (Phase 8).

// shareCurrentScan extracted to /features/scan-result-render.js (Phase 8).

// Theme + reading accessibility extracted to /features/appearance.js.
// initAppearance() is called at boot below with no deps; applyTheme()
// and applyReadingPrefs() are imported so Settings-save handlers can
// re-paint without routing through initAppearance again.
initAppearance();
initTabNav();

// Read-aloud (SpeechSynthesis): isSpeechSupported/readAloud/stopReading/
// updateReadAloudButton/composeReadAloudText extracted to
// /features/scan-result-render.js (Phase 8).

// Onboarding extracted to /features/onboarding.js. maybeShowOnboarding()
// is a no-op after first dismissal (flag in localStorage).

// Comparison logic moved to /features/comparison.js. The maybeRender
// + compareArmed entry points are imported at the top; the buttons are
// wired from the boot block via initComparison().
const _comparisonDeps = () => ({
  $, t, show, hide, comparisonEl, snapshotFromData,
  compareNextBtn, compareClear,
  getLastData: () => lastData,
});

// ============================================================================
// Live barcode scanner
// ============================================================================

// Camera scanner extracted to /features/scanner.js — openCameraScanner /
// closeCameraScanner are imported at the top of this file. initScanner()
// below wires the torch button (the camera stream + dialog lifecycle live
// inside the feature).

// ============================================================================
// Personal score
// ============================================================================

// personalSlot/renderPersonalScore extracted to /features/scan-result-render.js (Phase 8).

// Profile dialog extracted to /features/profile-dialog.js — init in
// the boot block below. Element refs, per-field listeners, macro-sum
// validation, life-stage gate, and save pipeline live inside the module.


// ============================================================================
// Scan history
// ============================================================================

const recentScansEl = $('recent-scans');
const recentListEl = $('recent-list');
const clearHistoryBtn = $('clear-history');

// Scan history UI moved to /features/scan-history-ui.js. The init
// returns the entry points used elsewhere in this file.
let _scanHistoryApi = null;
async function renderRecentScans() { return _scanHistoryApi?.renderRecentScans(); }
async function persistToHistory(data) { return _scanHistoryApi?.persistToHistory(data); }
function reopenScan(item) { return _scanHistoryApi?.reopenScan(item); }

// Keyboard shortcuts extracted to /features/keybindings.js.
initKeybindings({
  scanBtn, historySearchInput,
  quickAddBtn: $('quick-add-btn'),
  templatesBtn: $('templates-btn'),
  recipesBtn: $('recipes-btn'),
  weightBtn: $('weight-btn'),
  t, toast,
});

// Auto-update logic moved to /features/update-checker.js. Started in
// the boot block below via initUpdateChecker().

// ============================================================================
// Wiring
// ============================================================================

applyStaticTranslations();

fileInput.addEventListener('change', async (e) => {
  await addFiles(e.target.files); fileInput.value = '';
});

// Native capture button: replaces <input type=file capture=environment> on
// Capacitor builds with a real Camera API call, then feeds the base64 result
// directly into compressImage so the rest of the queue pipeline is unchanged.
if (isCapacitor && hasNativeCamera()) {
  const captureLabel = fileInput.closest('label') ?? fileInput.parentElement;
  if (captureLabel) {
    captureLabel.addEventListener('click', async (e) => {
      e.preventDefault();
      hide(errorEl);
      const photo = await nativeTakePhoto('camera');
      if (!photo) return; // user cancelled
      // Convert base64 → Blob → File so compressImage + detectBarcodeFromFile
      // can process it identically to a web file-picker result.
      const mime = `image/${photo.format ?? 'jpeg'}`;
      const bytes = Uint8Array.from(atob(photo.base64), (c) => c.charCodeAt(0));
      const blob = new Blob([bytes], { type: mime });
      const file = new File([blob], `native-capture.${photo.format ?? 'jpg'}`, { type: mime });
      await addFiles([file]);
    });
  }
}
queueEl.addEventListener('click', (e) => {
  const b = e.target.closest('.queue-remove');
  if (b) removeFromQueue(b.dataset.id);
});
scanBtn.addEventListener('click', () => { scanImage(); });
function resetScanState() {
  queue.length = 0; fileInput.value = '';
  // Clear lingering scan state so compare-next can't re-arm an old product.
  lastData = null;
  renderQueue();
  hide(resultEl);
  hide(errorEl);
  hide(comparisonEl);
  // Gap fix 3: stash the add-to-recipe picker on reset.
  const atrBtn = $('add-to-recipe-btn');
  if (atrBtn) atrBtn.hidden = true;
  const atrPicker = $('add-to-recipe-picker');
  if (atrPicker) { atrPicker.hidden = true; atrPicker.textContent = ''; }
}
resetBtn.addEventListener('click', () => { resetScanState(); });

// "Scanner un autre" — batch-mode shortcut that resets + reopens the
// barcode camera in a single tap. Only shown when BarcodeDetector or
// native MLKit is available (same gate as the main capture-screen barcode button).
const resetCameraBtn = $('reset-camera-btn');
// Gate is async on native (MLKit availability check); show immediately on web.
(async () => {
  const hasBarcodeSupport = isCapacitor
    ? hasNativeBarcodeScanner()
    : !!getBarcodeDetector();
  if (hasBarcodeSupport) {
    show(resetCameraBtn);
    show(barcodeLiveBtn);
  }
})();
resetCameraBtn?.addEventListener('click', () => {
  resetScanState();
  openCameraScanner();
});
initComparison(_comparisonDeps());

barcodeLiveBtn?.addEventListener('click', () => openCameraScanner());
// Gap fix #13 — manual barcode entry. When camera is denied / fails
// or the barcode is unreadable, the user can type 8-13 digits and
// push the queue into the same pipeline as a live scan.
$('manual-barcode-form')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const input = $('manual-barcode-input');
  const raw = (input?.value || '').replace(/\D/g, '');
  if (raw.length !== 8 && raw.length !== 12 && raw.length !== 13) {
    toast(t('manualBarcodeInvalid'), 'warn');
    input?.focus();
    return;
  }
  // Reuse the same path as the live-scanner success handler: enqueue a
  // barcode-only queue item, then kick scanImage() which resolves
  // through OFF (or LLM fallback if the barcode is unknown).
  addBarcodeOnly(raw);
  await scanImage();
  if (input) input.value = '';
  // Collapse the <details> so the input isn't still sitting open.
  const details = input?.closest('details');
  if (details) details.open = false;
});
cameraClose?.addEventListener('click', () => closeCameraScanner());
// Tear down the MediaStream + detection loop regardless of how the dialog
// closes — Escape key, backdrop click, or programmatic close from a
// successful barcode scan all fire the 'close' event.
cameraDialog?.addEventListener('close', () => closeCameraScanner());

// Settings dialog wiring extracted to /features/settings-dialog.js —
// initialised in the boot block below.

// Backup / export / import wiring extracted to /features/backup-io.js —
// initialized below with initBackupIO({ ...deps }).

// ----- MFP / Cronometer CSV import -----
$('csv-import-file')?.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  e.target.value = '';
  const status = $('csv-import-status');
  if (!file) return;
  if (status) {
    status.textContent = t('csvImportLoading');
    // F-F-05: clear any prior skipped-rows details so a fresh import
    // doesn't inherit stale warnings.
    const priorDetails = status.nextElementSibling;
    if (priorDetails && priorDetails.classList?.contains('csv-skipped-details')) {
      priorDetails.remove();
    }
  }
  try {
    const { parseCsvImport } = await import('./features/csv-import.js');
    const text = await file.text();
    const { format, entries, errors } = parseCsvImport(text);
    if (format === 'unknown') {
      if (status) status.textContent = t('csvImportUnknown');
      return;
    }
    let written = 0;
    for (const e of entries) {
      try { await putEntry(e); written += 1; } catch { /* skip */ }
    }
    if (status) {
      status.textContent = t('csvImportDone', {
        n: written, format, skipped: errors.length,
      });
      // F-F-05: when rows were skipped, expose the per-row reasons
      // in a collapsible <details> next to the status line. Users
      // with a 300-row export and "13 skipped" can click to see
      // which ones and why.
      if (errors.length > 0) {
        const details = document.createElement('details');
        details.className = 'csv-skipped-details';
        const summary = document.createElement('summary');
        summary.textContent = t('csvImportSkippedDetails', { n: errors.length });
        details.appendChild(summary);
        const ul = document.createElement('ul');
        ul.className = 'csv-skipped-list';
        for (const err of errors) {
          const li = document.createElement('li');
          li.textContent = err;
          ul.appendChild(li);
        }
        details.appendChild(ul);
        status.insertAdjacentElement('afterend', details);
      }
    }
    await renderDashboard();
  } catch (err) {
    console.error('[csv import]', err);
    if (status) status.textContent = t('csvImportFailed');
  }
});

// ----- Multi-profile -----
// setProfilesStatus / renderProfilesUI extracted to
// /features/profiles-ui.js (Phase 9); the #profiles-save / -switch /
// -delete listeners themselves extracted there too (Phase 14) —
// initProfilesDialog() is wired in the boot block below.

// Telemetry panel wiring extracted to /features/telemetry-ui.js
// (Phase 14) — initTelemetryUi() wired in the boot block below.


// updateDismissBtn click handler is wired by initUpdateChecker().

pendingRetry?.addEventListener('click', () => { retryPending(); });

// Share button is now useful even without Web Share — it falls back to
// clipboard via shareOrCopy(). No need to hide it on unsupported browsers.
shareBtn?.addEventListener('click', shareCurrentScan);

// "Add to recipe" picker (Gap fix 3) extracted to
// /features/add-to-recipe.js (Phase 14) — initAddToRecipe() wired in
// the boot block below.


// Read-aloud wiring
const readAloudBtn = document.getElementById('read-aloud-btn');
if (!isSpeechSupported()) hide(readAloudBtn);
readAloudBtn?.addEventListener('click', () => {
  if (isSpeaking()) { stopReading(); return; }
  if (lastData) readAloud(composeReadAloudText(lastData));
});

// Fix #19: "Better alternative from history" button.
// Queries the IDB scan history for products in the same category with a
// higher classic score than the current scan. One click — no manual
// two-tap compare flow. Reveals itself only when at least one better
// option exists; hidden otherwise so it never clutters the UI for A-grade scans.
const historyAltBtn = document.getElementById('history-alt-btn');
async function maybeShowHistoryAlternative(data) {
  if (!historyAltBtn) return;
  hide(historyAltBtn);
  historyAltBtn.onclick = null;
  if (!data) return;
  try {
    const all = await listScans();
    const currentCategory = data.audit?.category;
    const currentScore = data.audit?.score ?? 0;
    const currentName = (data.audit?.product_name || data.product?.name || '').toLowerCase();
    // Find the best-scoring same-category item that outscores the current scan
    // and is not the same product (guard by name similarity).
    const better = all
      .filter((item) =>
        item.category === currentCategory &&
        item.score > currentScore &&
        (item.name || '').toLowerCase() !== currentName,
      )
      .sort((a, b) => b.score - a.score)[0];
    if (!better) return;
    historyAltBtn.textContent =
      `⬆ ${better.name} (${better.score}/100 · ${better.grade})`;
    historyAltBtn.onclick = () => {
      if (better.snapshot) reopenScan(better);
    };
    show(historyAltBtn);
  } catch { /* non-critical */ }
}

const aboutBtn = $('about-btn');
const aboutDialog = $('about-dialog');
const settingsDialogForAbout = $('settings-dialog');
// UX fix 2026-06: About used to open via showModal() while Settings was
// still open underneath — two native <dialog> elements both in the top
// layer at once. That's not a crash, but it stacks two ::backdrop dims
// (visibly darker than either dialog alone), nests two focus traps, and
// means Escape only ever closes the topmost one, which reads as buggy.
// Now it's a sequential drill-down: Settings closes, About opens: and
// closing About reopens Settings instead of dropping the user back to
// whatever was behind both dialogs, since they almost certainly opened
// About *from* Settings and want to keep editing.
let reopenSettingsAfterAbout = false;
aboutBtn?.addEventListener('click', () => {
  if (settingsDialogForAbout?.open) {
    settingsDialogForAbout.close();
    reopenSettingsAfterAbout = true;
  }
  aboutDialog?.showModal();
});
aboutDialog?.addEventListener('close', () => {
  if (reopenSettingsAfterAbout) {
    reopenSettingsAfterAbout = false;
    settingsDialogForAbout?.showModal();
  }
});

if ('serviceWorker' in navigator && !isCapacitor) {
  navigator.serviceWorker.register('/service-worker.js').catch(() => {});
}

// ---------- PWA share_target receiver ----------
// The manifest declares Scan\'eat as a share target for image/*. When the
// user picks "Share → Scan\'eat" from another app, the SW receives the POST,
// stashes the files, and redirects us here with ?shared=1. Pull the files
// from the SW via a MessageChannel and feed them straight into the capture
// queue — zero-click scan from the gallery.
(async () => {
  if (!new URLSearchParams(location.search).has('shared')) return;
  history.replaceState({}, '', '/'); // clean the URL immediately
  try {
    const reg = await navigator.serviceWorker?.ready;
    if (!reg?.active) return;
    const files = await new Promise((resolve) => {
      const channel = new MessageChannel();
      channel.port1.onmessage = (ev) => resolve(ev.data);
      reg.active.postMessage('shared-files?', [channel.port2]);
      setTimeout(() => resolve(null), 1500); // don't hang forever
    });
    if (files && files.length > 0) await addFiles(files);
  } catch { /* non-critical */ }
})();
// Boot-time arming-state restore is handled inside initComparison().

// ---------- PWA dynamic-shortcut intents ----------
// Manifest declares shortcuts that launch the app with ?intent=... on
// Android's long-press menu. Route each intent to its existing UI action.
(() => {
  const intent = new URLSearchParams(location.search).get('intent');
  if (!intent) return;
  history.replaceState({}, '', '/'); // clean URL so reload doesn't re-fire
  // Defer slightly so the main UI has finished mounting.
  setTimeout(() => {
    if (intent === 'scan') {
      // Native: always use MLKit scanner directly.
      // Web: BarcodeDetector if available, else file picker.
      if (isCapacitor) openCameraScanner();
      else if (getBarcodeDetector()) openCameraScanner();
      else fileInput?.click();
    } else if (intent === 'quick-add') {
      quickAddBtn?.click();
    } else if (intent === 'dashboard') {
      document.body.classList.add('returning-user');
      $('daily-dashboard')?.scrollIntoView({ behavior: 'auto', block: 'start' });
    }
  }, 50);
})();

initUpdateChecker({
  isCapacitor,
  updateBanner, updateVersionEl, updateInstallBtn, updateDismissBtn,
  updatePendingBanner, show, hide,
});

// ============================================================================
// Consumption logging + daily dashboard
// ============================================================================

const portionPanel = $('portion-panel');
const portionInput = $('portion-grams');
const portionMealSelect = $('portion-meal');
const portionPresetPack = $('portion-preset-pack');
const logBtn = $('log-btn');
const logKcalPreview = $('log-kcal-preview');
const logToast = $('log-toast');
const dashboardEl = $('daily-dashboard');
const dashboardRows = $('dashboard-rows');
const dashboardEntries = $('dashboard-entries');
// Gap fix #10: mark the dashboard regions as busy on boot so the CSS
// skeleton placeholders paint before the first real render. Cleared
// on the first successful render.
dashboardRows?.setAttribute('aria-busy', 'true');
dashboardEntries?.setAttribute('aria-busy', 'true');
const dashboardLog = $('dashboard-log');
const dashboardDateEl = $('dashboard-date');
const dashboardRemainingEl = $('dashboard-remaining');
const clearTodayBtn = $('clear-today');
const quickAddBtn = $('quick-add-btn');
const quickAddDialog = $('quick-add-dialog');
const qaCancel = $('qa-cancel');
const qaSave = $('qa-save');

// Portion-panel logic moved to /features/portion-panel.js. The init
// returns the setupPortionPanel + updateLogPreview entry points used
// elsewhere in this file (post-scan hook + edit hook).
const { setupPortionPanel, updateLogPreview } = initPortionPanel({
  $, t, hide, show,
  portionInput, portionMealSelect, portionPresetPack, logToast,
  logKcalPreview,
  defaultMealForHour, parseUnitInput, toGrams,
  getLastData: () => lastData,
});

// `isFastingActive` is now exported from /features/fasting.js, which
// owns the LS keys. Previously this file duplicated the key strings
// inline; that broke silently when fasting.js renamed them (R27).

logBtn?.addEventListener('click', async () => {
  // R10.3: tell the user *why* the log button is a no-op when they
  // click it without a scanned product. Previously silent — looked
  // like a broken button until the user figured out they needed to
  // scan first.
  if (!lastData) { toast(t('logNoScan'), 'warn'); return; }
  const grams = Math.max(0, Number(portionInput.value) || 0);
  if (grams <= 0) { toast(t('logNeedsGrams'), 'warn'); return; }
  const meal = portionMealSelect?.value || 'snack';
  // Shared-meal scaling: if the user says they only ate e.g. 50% of the
  // portion, downscale grams before logging. buildEntry() multiplies per-
  // 100g macros by grams/100, so scaling grams scales the whole entry.
  const sharePct = Math.max(1, Math.min(100, Number($('portion-share-pct')?.value) || 100));
  const effectiveGrams = Math.round(grams * (sharePct / 100));
  try {
    const entry = await logEntry(lastData.product, effectiveGrams, meal);
    logToast.textContent = t('logged', { grams: effectiveGrams, kcal: Math.round(entry.kcal) });
    show(logToast);
    // Fix #26 — warn (non-blocking) when logging during a fast.
    if (isFastingActive()) toast(t('fastingActiveWarn'), 'warn');
    await renderDashboard();
  } catch (err) {
    console.error('[log]', err);
  }
});

// Share-preset chips — write the selected % into the input.
document.querySelectorAll('.share-presets [data-share]').forEach((btn) => {
  btn.addEventListener('click', () => {
    const v = Number(btn.dataset.share);
    const input = $('portion-share-pct');
    if (input) input.value = String(v);
  });
});

// ----- Quick Add -----
quickAddBtn?.addEventListener('click', () => {
  // R35.I1: clicking "+" always resets the edit state — user is
  // adding a new entry, not continuing an edit.
  editingEntry = null;
  // reset fields
  for (const id of ['qa-name', 'qa-kcal', 'qa-carbs', 'qa-protein', 'qa-fat', 'qa-satfat', 'qa-sugars', 'qa-salt', 'qa-fiber']) {
    const el = $(id);
    if (el) el.value = '';
  }
  // pick a default meal by time-of-day
  if ($('qa-meal')) $('qa-meal').value = defaultMealForHour(new Date().getHours());
  // Reset the dialog title back to default from any prior edit.
  const title = $('quick-add-dialog-title');
  if (title) title.textContent = t('quickAddTitle');
  quickAddDialog.showModal();
});
qaCancel?.addEventListener('click', (e) => {
  e.preventDefault();
  editingEntry = null;
  quickAddDialog.close();
});

// Voice-dictate extracted to /features/voice-dictate.js — initialized below
// with initVoiceDictate({ t, currentLang, parseVoiceQuickAdd }).

// ----- Photo-to-food identification for Quick Add -----
// Complements voice dictation with image recognition: the user snaps a
// plate / fresh fruit / bakery item, we hit /api/identify, and the dialog
// pre-fills with the estimated name + macros.
const qaPhotoInput = $('qa-photo-input');

// setQaStatus, setQaLoadingPhases, and identifyViaModePath extracted to
// /features/qa-photo-identify.js (Phase 12) — imported above.

qaPhotoInput?.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  e.target.value = ''; // allow re-selecting the same file
  if (!file) return;
  // F-DST-05: phase-rotating status while the LLM round-trips.
  setQaLoadingPhases([
    t('identifyPhaseCompressing'),
    t('identifyPhaseAnalyzing'),
    t('identifyPhaseReconciling'),
  ]);
  try {
    const compressed = await compressImage(file);
    const images = [{ base64: compressed.base64, mime: compressed.mime }];
    const result = await identifyViaModePath({
      images,
      directFn: (engine, imgs, opts) => engine.identifyFood(imgs, opts),
      serverUrl: apiUrl('/api/identify'),
    });
    // Reconcile with the built-in DB: if the identified name matches a
    // CIQUAL entry, swap the LLM's guessed macros for the DB's authoritative
    // per-100 g values scaled by the LLM's gram estimate.
    const reconciled = reconcileWithFoodDB(result, listCustomFoods());
    const setField = (id, v) => { const el = $(id); if (el && v != null) el.value = String(v); };
    setField('qa-name',    reconciled.name);
    setField('qa-kcal',    Math.round(reconciled.kcal));
    setField('qa-protein', Math.round(reconciled.protein_g));
    setField('qa-carbs',   Math.round(reconciled.carbs_g));
    setField('qa-fat',     Math.round(reconciled.fat_g));
    if (reconciled.source === 'db') {
      setQaStatus(t('identifyMatchedDB', { name: reconciled.name }), 'ok');
    } else if (reconciled.confidence === 'low') {
      setQaStatus(t('identifyLowConfidence'), 'warn');
    } else {
      setQaStatus('');
    }
  } catch (err) {
    console.warn('[identifyFood]', err);
    setQaStatus(t('identifyFailed'), 'error');
  }
});

// Multi-item plate: identify all foods in one shot, write each as a
// separate Quick Add entry. Closes the Quick Add dialog on success.
$('qa-photo-multi-input')?.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  e.target.value = '';
  if (!file) return;
  setQaLoadingPhases([
    t('identifyPhaseCompressing'),
    t('identifyPhasePlateItems'),
    t('identifyPhaseReconciling'),
  ]);
  try {
    const compressed = await compressImage(file);
    const images = [{ base64: compressed.base64, mime: compressed.mime }];
    const result = await identifyViaModePath({
      images,
      directFn: (engine, imgs, opts) => engine.identifyMultiFood(imgs, opts),
      serverUrl: apiUrl('/api/identify-multi'),
    });
    const items = Array.isArray(result?.items) ? result.items : [];
    if (items.length === 0) {
      setQaStatus(t('identifyMultiEmpty'), 'warn');
      return;
    }
    // F-F-03: reconcile for macro accuracy + stash for the Recipes
    // dialog shortcut, but DO NOT auto-log. Show the items in the
    // menu-scan dialog (mode=plate) so the user can uncheck false
    // positives before logging. A "Log all" button keeps the common-
    // case UX to one extra tap.
    let dbHits = 0;
    const reconciled = [];
    for (const it of items) {
      const r = reconcileWithFoodDB(it, listCustomFoods());
      if (r.source === 'db') dbHits += 1;
      reconciled.push(r);
    }
    recipesDialog.setLastIdentifiedPlate(reconciled);
    quickAddDialog?.close();
    setQaStatus(t('identifyMultiPreview', { n: items.length, db: dbHits }), 'ok');
    await openMenuScan(reconciled, { mode: 'plate' });
  } catch (err) {
    console.warn('[identifyMultiFood]', err);
    setQaStatus(t('identifyFailed'), 'error');
  }
});

// Menu-scan dialog + picker extracted to /features/menu-scan.js.
// openMenuScan is imported at the top; initMenuScan wires the close
// button in the boot block below.

$('qa-photo-menu-input')?.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  e.target.value = '';
  if (!file) return;
  setQaLoadingPhases([
    t('identifyPhaseCompressing'),
    t('identifyPhaseMenu'),
  ]);
  try {
    const compressed = await compressImage(file);
    const images = [{ base64: compressed.base64, mime: compressed.mime }];
    const result = await identifyViaModePath({
      images,
      directFn: (engine, imgs, opts) => engine.identifyMenu(imgs, opts),
      serverUrl: apiUrl('/api/identify-menu'),
    });
    const dishes = Array.isArray(result?.dishes) ? result.dishes : [];
    if (dishes.length === 0) {
      setQaStatus(t('menuScanEmpty'), 'warn');
      return;
    }
    setQaStatus('');
    quickAddDialog?.close();
    await openMenuScan(dishes);
  } catch (err) {
    console.warn('[identifyMenu]', err);
    setQaStatus(t('identifyFailed'), 'error');
  }
});

// menu-scan-close handled inside initMenuScan().

// Reset AI status when the dialog opens (via the quick-add button).
quickAddBtn?.addEventListener('click', () => setQaStatus(''));

// Quick Add name autocomplete extracted to /features/qa-autocomplete.js.
// initQaAutocomplete is imported at the top; wired in the boot block.

// Weight UI is extracted to /features/weight.js. renderWeightSummary is
// imported and called from the dashboard render loop; initWeight wires
// up the dialog at boot.

// Meal-templates dialog extracted to /features/templates-dialog.js.
// initTemplatesDialog is imported at the top; the wire-up happens in
// the boot block alongside the other feature inits.

// Recipes dialog + editor extracted to /features/recipes-dialog.js.
// initRecipesDialog is imported at the top; the module owns the state
// (editingRecipe, lastIdentifiedPlate) and exposes setLastIdentifiedPlate
// so the qa-photo-multi handler above can push the latest plate items
// without poking shared module-scoped variables. Assigned in the boot
// block below; the handler only reads it at click time, so the TDZ
// gap between module-top and boot is safe.

// readQaForm extracted to /features/qa-photo-identify.js (Phase 12) —
// imported above.

// R35.I1+I9: editing state for the Quick Add dialog. When set, the
// save handler upserts the existing entry (preserving its id + date +
// timestamp, letting the user change grams/macros/meal) instead of
// creating a new one. Null = "add new" flow.
let editingEntry = null;

qaSave?.addEventListener('click', async (e) => {
  e.preventDefault();
  const f = readQaForm();
  // Fix #22 — reconcile typed Quick Add against the built-in food DB
  // when kcal is empty. A user who typed "pomme" + left macros blank
  // used to see the save rejected; now we try a FOOD_DB match and
  // fill in per-100 g macros (defaulting portion to 100 g). Matches
  // the existing LLM-identify reconciliation path.
  if (f.kcal <= 0 && f.name && f.name.trim().length >= 2) {
    const reconciled = reconcileWithFoodDB(
      { name: f.name, estimated_grams: 100 },
      listCustomFoods(),
    );
    if (reconciled?.source === 'db') {
      f.kcal = Math.round(reconciled.kcal) || 0;
      f.protein_g = Math.round(reconciled.protein_g) || 0;
      f.carbs_g = Math.round(reconciled.carbs_g) || 0;
      f.fat_g = Math.round(reconciled.fat_g) || 0;
      // Reflect in the form so the user sees what got filled.
      $('qa-kcal').value    = String(f.kcal);
      $('qa-protein').value = String(f.protein_g);
      $('qa-carbs').value   = String(f.carbs_g);
      $('qa-fat').value     = String(f.fat_g);
    }
  }
  if (f.kcal <= 0) { $('qa-kcal')?.focus(); return; }
  try {
    if (editingEntry) {
      // R35.I1: upsert the existing entry with the user's edits.
      // Preserves id / date / timestamp / fromRecipe so the row stays
      // in its original meal slot unless the user changed `meal`, and
      // doesn't jump to today's date when edited tomorrow.
      await putEntry({
        ...editingEntry,
        product_name: f.name || editingEntry.product_name,
        meal: f.meal,
        kcal: f.kcal,
        carbs_g: f.carbs_g,
        protein_g: f.protein_g,
        fat_g: f.fat_g,
        sat_fat_g: f.sat_fat_g,
        sugars_g: f.sugars_g,
        salt_g: f.salt_g,
        fiber_g: f.fiber_g,
      });
      toast(t('entryUpdated', { name: f.name || editingEntry.product_name }), 'ok');
      editingEntry = null;
    } else {
      await logQuickAdd(f);
    }
    // Gap fixes #24 + #25: after logging, surface a non-blocking
    // warning if the typed name triggers allergen or diet rules. Does
    // not prevent the save — respects user autonomy, just informs.
    try {
      const warn = checkQuickAddWarnings(f.name, getProfile(), currentLang);
      if (warn.allergens.length > 0) {
        toast(t('qaAllergenWarn', { items: warn.allergens.map((a) => a.label).join(' · ') }), 'warn');
      } else if (warn.dietViolations.length > 0) {
        toast(t('qaDietWarn', { items: warn.dietViolations.slice(0, 2).join(' · ') }), 'warn');
      }
    } catch { /* never block the save */ }
    // Fix #26 — fasting-window warn (non-blocking).
    if (isFastingActive()) toast(t('fastingActiveWarn'), 'warn');
    quickAddDialog.close();
    await renderDashboard();
  } catch (err) {
    console.error('[quickAdd]', err);
  }
});

// Save-as-template: turn whatever the user has typed into a reusable
// template without actually logging it. Useful for recurring snacks /
// dinners the user re-enters weekly — one tap later via the templates
// dialog re-applies the same shape. We reuse the name field for the
// template name, which is the intuitive pick.
$('qa-save-tpl')?.addEventListener('click', async (e) => {
  e.preventDefault();
  const f = readQaForm();
  if (!f.name.trim() || f.kcal <= 0) {
    setQaStatus(t('qaSaveAsTemplateNeedsName'), 'warn');
    return;
  }
  try {
    await saveTemplate({
      name: f.name.trim(),
      meal: f.meal,
      items: [{
        product_name: f.name,
        grams: 0,
        meal: f.meal,
        kcal: f.kcal,
        carbs_g: f.carbs_g,
        protein_g: f.protein_g,
        fat_g: f.fat_g,
        sat_fat_g: f.sat_fat_g,
        sugars_g: f.sugars_g,
        salt_g: f.salt_g,
        quickAdd: true,
      }],
    });
    setQaStatus(t('qaSaveAsTemplateDone', { name: f.name.trim() }), 'ok');
  } catch (err) {
    console.error('[quickAdd save-as-template]', err);
    setQaStatus(t('qaSaveAsTemplateFailed'), 'error');
  }
});

// Gap fix #6: copy yesterday's entries into today. Skips the step
// of re-applying templates one by one for users with a repeating
// routine. Gracefully no-ops when yesterday was empty. Entries get
// fresh ids + today's date + current timestamp so they look like
// freshly-logged rows, but keep their product_name / grams /
// macros / meal so the "shape" of the day comes forward.
$('copy-yesterday')?.addEventListener('click', async () => {
  const today = todayISO();
  const yesterday = localDateISO(Date.now() - 86_400_000);
  const src = await listByDate(yesterday).catch(() => []);
  if (src.length === 0) { toast(t('copyYesterdayEmpty'), 'warn'); return; }
  const existing = await listByDate(today).catch(() => []);
  if (existing.length > 0) {
    if (!window.confirm(t('copyYesterdayConfirm', { n: src.length, existing: existing.length }))) return;
  }
  const now = Date.now();
  const copied = [];
  for (let i = 0; i < src.length; i++) {
    const e = src[i];
    const copy = {
      ...e,
      id: globalThis.crypto?.randomUUID?.() ?? `c${now}${i}${Math.random().toString(36).slice(2)}`,
      date: today,
      timestamp: now + i,
    };
    await putEntry(copy);
    copied.push(copy);
  }
  await renderDashboard();
  // Undo-safe: deleting every freshly-added copy reverses the action.
  toastWithUndo(
    t('copyYesterdayDone', { n: copied.length }),
    async () => {
      for (const e of copied) await deleteEntry(e.id);
      await renderDashboard();
      toast(t('copyYesterdayReverted'), 'ok');
    },
  );
});

clearTodayBtn?.addEventListener('click', async () => {
  // Tier-2 destructive: wipes all entries logged today. Show a count
  // in the confirm so the user knows the magnitude before nuking.
  const today = await listByDate();
  const n = today.length;
  if (n === 0) { toast(t('clearTodayNoneToClear'), 'warn'); return; }
  if (!window.confirm(t('clearTodayConfirm', { n }))) return;
  // R35.I2: undo-safe. Snapshot the entries before clearing; tapping
  // Undo reinserts them all. IDB upsert-by-id means restoration is
  // exact even if the user already re-logged something in between.
  const snapshot = today.map((e) => ({ ...e }));
  await clearDate();
  await renderDashboard();
  toastWithUndo(
    t('clearTodayDone', { n }),
    async () => {
      for (const e of snapshot) await putEntry(e);
      await renderDashboard();
      toast(t('clearTodayRestored', { n }), 'ok');
    },
  );
});

// ============================================================================
// Custom foods dialog — add / list / delete user-curated per-100 g foods.
// These flow into renderFoodSuggestions + reconcileWithFoodDB via
// listCustomFoods() at every call site above.
//
// Dialog open/close/save wiring extracted to
// /features/custom-foods-day-notes.js (Phase 14) —
// initCustomFoodsDialog() wired in the boot block below.
// ============================================================================


// pctClass extracted to /core/presenters.js (R11.1) — pure, testable.

// Hydration feature is now self-contained in public/features/hydration.js.
// Inject its runtime dependencies (i18n lookup, profile getter, goal calc,
// date helper) at boot so the module stays isolated from app.js internals.
initHydration({ t, getProfile, waterGoalMl, todayISO });
initActivity({
  t, getProfile, toast,
  listActivityByDate, deleteActivity, logActivity,
  buildActivityEntry, estimateKcalBurned, sumBurned,
  renderDashboard,
});
initWeight({
  t, toast,
  currentLang: () => currentLang,
  getProfile, setProfile,
  listWeight, logWeight, deleteWeight,
  summarizeWeight, weeklyTrend, weightForecast,
  renderDashboard, todayISO, round1,
});
initReminders({ t, toast, nextOccurrenceMs, listWeight });
initVoiceDictate({
  t,
  currentLang: () => currentLang,
  parseVoiceQuickAdd,
  logEvent: telemetryLog,
});
const { scanViaServer, scanViaDirect, scanImage, enqueueCurrent } = initScanPipeline({
  t, getKey, loadEngine, getMode,
  renderAudit, renderIngredients, renderNutrition,
  persistToHistory, updatePendingBanner,
  comparisonDeps: () => _comparisonDeps(),
  setLastData: (data) => { lastData = data; },
});
initScanner({ t, errorEl, show, getBarcodeDetector, scanImage, addBarcodeOnly, isNative: isCapacitor });
initInstallBanner({ show, hide });
initBackupIO({
  t, show, hide,
  buildBackup, restoreBackup, listAllEntries,
  entriesToHealthJSON, entriesToDailyCSV,
  renderRecentScans, renderDashboard,
});
initRecipeIdeas({
  t, getMode, getKey, loadEngine,
  // F-F-07: card actions need persistence + navigation deps.
  saveRecipe,
  toast,
  openMealPlan,
});
initSettingsDialog({
  t, setLang, applyStaticTranslations,
  isCapacitor,
  currentLang: () => currentLang,
  applyTheme, applyReadingPrefs,
  setSetting, scheduleReminders,
  renderProfilesUI, telemetryEnabled,
  setTelemetryEnabled: telemetrySetEnabled,
  getKey,
  listScans,
  onLangChange: () => {
    // Close any dialog whose contents were rendered dynamically (not
    // via data-i18n) so the user doesn't see stale strings. Explain +
    // pillar + templates + recipes dialogs all render chip text from
    // `t()` snapshots; reopen is one tap away, so the UX cost is zero.
    // R10.5: extended to close templates + recipes so users switching
    // locale mid-dialog don't see a mix of FR + EN chip labels.
    $('explain-dialog')?.close();
    $('pillar-dialog')?.close();
    $('templates-dialog')?.close();
    $('recipes-dialog')?.close();
    $('recipe-edit-dialog')?.close();
    renderDashboard();
  },
});
initMenuScan({ t, defaultMealForHour, logQuickAdd, renderDashboard });
initGroceryListDialog({ $, t, toast, show, shareOrCopy, listRecipes });
initAddToRecipe({ $, t, toast, listRecipes, saveRecipe, getLastData: () => lastData });
initTelemetryUi({
  $, t, toast, show, hide, shareOrCopy, todayISO,
  setEnabled: telemetrySetEnabled, format: telemetryFormat, clear: telemetryClear,
});
initMealPlanUI({
  t, toast, show, hide,
  currentLang: () => currentLang,
  listRecipes, listTemplates,
  aggregateRecipe, buildRecipeProductInput,
  expandTemplate, putEntry,
  aggregateGroceryList, formatGroceryList,
  openPantryIdeas,
  dateFormatter, localeFor,
  renderDashboard,
});
initTemplatesDialog({
  t, toast,
  listByDate, saveTemplate, listTemplates, deleteTemplate,
  expandTemplate, templateKcal, putEntry, todayISO, renderDashboard,
  shareOrCopy, formatTemplateShare,
  currentLang: () => currentLang,
  // Gap fixes #24 + #25: allergen + diet warnings.
  checkTemplateWarnings, getProfile,
});
recipesDialog = initRecipesDialog({
  t, toast,
  aggregateRecipe, saveRecipe, listRecipes, deleteRecipe,
  putEntry, defaultMealForHour, todayISO, renderDashboard,
  shareOrCopy, formatRecipeShare,
  currentLang: () => currentLang,
  // Gap fix #5: ingredient autocomplete + auto-fill in the recipe
  // editor rows.
  searchFoodDB, listCustomFoods,
  // Feature 3 — URL import + photo scan for recipes.
  compressImage, getMode, getKey, loadEngine,
  // Gap fix 1 — recipe scoring: synthesise ProductInput for
  // scoreProduct.
  buildRecipeProductInput,
  // Gap fixes #24 + #25: per-recipe allergen + diet warnings.
  checkRecipeWarnings, getProfile,
});
initQaAutocomplete({
  t, show, hide, searchFoodDB, listCustomFoods,
  // Gap fix #7: user's top-5 logged foods as favourites.
  listAllEntries, topFoods,
});
initProfileDialog({
  t, show, hide,
  getProfile, setProfile, hasMinimalProfile,
  bmrMifflinStJeor, tdeeKcal, bmi, bmiCategory, dailyTargets,
  onAfterSave: () => {
    // Re-render the currently open scan result (personal score may have
    // flipped) + refresh the dashboard so the life-stage chip appears
    // / disappears immediately after a life_stage change.
    if (lastData && !resultEl.hidden) renderAudit(lastData);
    renderDashboard();
  },
});
initFasting({
  t,
  currentLang: () => currentLang,
  show, hide,
  fastingStatus,
  buildFastCompletion, saveFastCompletion,
  listFastHistory, computeFastStreak, clearFastHistory,
});

// ============================================================================
// PWA install-prompt banner extracted to /features/install-banner.js —
// initialised at boot with initInstallBanner({ show, hide }).

// Fasting timer extracted to /features/fasting.js — initFasting()
// wires the Start / Stop / Clear-history buttons, renderFasting() is
// imported for the dashboard tick.

// renderDayNote extracted to /features/custom-foods-day-notes.js
// (Phase 10) — imported above.

$('day-note-input')?.addEventListener('input', (e) => {
  const text = e.target.value || '';
  setDayNote(todayISO(), text);
  const counter = $('day-note-counter');
  if (counter) counter.textContent = `${text.length} / ${DAY_NOTE_MAX_CHARS}`;
});

// Fasting handlers + render loop moved to /features/fasting.js —
// initialised via initFasting() at boot.

// Activity (exercise) UI is extracted to /features/activity.js.
// renderActivity() is imported below; initialisation lives next to
// initHydration / initFasting at boot.

// ============================================================================
// Day / Week view toggle — flips between daily dashboard and weekly rollup.
// Persisted to localStorage so Week-view users don't have to re-click
// on every reload.
// ============================================================================

// dashboardView state + applyViewToggle extracted to
// /features/custom-foods-day-notes.js (Phase 10) — imported above.
// getDashboardView() replaces the former local `dashboardView` read.

// renderWeeklyView and renderMonthlyView extracted to
// /features/dashboard-charts.js (Phase 11) — imported above.
// Share button wiring — hidden when navigator.share is unavailable (desktop
// browsers without Web Share). Falls back to clipboard copy so the action
// isn't a dead end even without native share.
// Daily summary share — the same pattern as weekly, but for today's log.
// Gives users a one-tap way to copy or send today's kcal / macros rundown
// to a partner or a coach, without having to screenshot the dashboard.
$('daily-share')?.addEventListener('click', async () => {
  const totals = await dailyTotals().catch(() => null);
  const profile = getProfile();
  const targets = dailyTargets(profile);
  const burnedForDate = await listActivityByDate(todayISO()).catch(() => []);
  const burned = { kcal: sumBurned(burnedForDate) };
  // Fix #14 + #15 — fold hydration + day note into the share.
  const hydrationMl = Number(localStorage.getItem(`scanneat.hydration.${todayISO()}`)) || 0;
  const hydrationGoalMl = waterGoalMl(getProfile()) || 0;
  const dayNote = getDayNote(todayISO()) || '';
  const text = formatDailySummary(totals, targets, burned, {
    lang: currentLang,
    dateISO: todayISO(),
    hydrationMl, hydrationGoalMl, dayNote,
  });
  if (!text) { toast(t('dailyShareEmpty'), 'warn'); return; }
  await shareOrCopy({
    title: t('dailyShareTitle'),
    text,
    toasts: { copied: t('dailyShareCopied'), failed: t('dailyShareFailed') },
    toast,
  });
});

$('weekly-share')?.addEventListener('click', async () => {
  const entries = await listAllEntries().catch(() => []);
  const roll = weeklyRollup(entries, todayISO());
  const text = formatWeeklyShare(roll, { lang: currentLang });
  if (!text) { toast(t('weeklyShareEmpty'), 'warn'); return; }
  await shareOrCopy({
    title: t('weeklyShareTitle'),
    text,
    toasts: { copied: t('weeklyShareCopied'), failed: t('weeklyShareFailed') },
    toast,
  });
});

$('monthly-share')?.addEventListener('click', async () => {
  const entries = await listAllEntries().catch(() => []);
  const roll = monthlyRollup(entries, todayISO());
  const kcalTarget = dailyTargets(getProfile())?.kcal ?? 0;
  const text = formatMonthlyShare(roll, { lang: currentLang, kcalTarget });
  if (!text) { toast(t('monthlyShareEmpty'), 'warn'); return; }
  await shareOrCopy({
    title: t('monthlyShareTitle'),
    text,
    toasts: { copied: t('monthlyShareCopied'), failed: t('monthlyShareFailed') },
    toast,
  });
});

document.querySelectorAll('.view-tab').forEach((btn) =>
  btn.addEventListener('click', () => applyViewToggle(btn.dataset.view)));

// renderDashboard, renderGapCloser, round1, round3, renderLineChart, and
// renderProgressCharts extracted to /features/dashboard-charts.js
// (Phase 11) — imported above.
$('progress-btn')?.addEventListener('click', async () => {
  const dlg = $('progress-dialog');
  if (!dlg) return;
  dlg.showModal();
  try { await renderProgressCharts(); }
  catch (err) { console.warn('[progress]', err); }
});
$('progress-close')?.addEventListener('click', (e) => {
  e.preventDefault();
  $('progress-dialog')?.close();
});

// renderWeightSummary extracted to /features/weight.js — imported at top.

// Meal reminders extracted to /features/reminders.js — initReminders()
// wires scheduleReminders() + registers it on boot.

// Init scan-history UI now that all renderAudit/renderIngredients/
// renderNutrition + makeActivatable defs above are available.
_scanHistoryApi = initScanHistoryUi({
  $, t, hide, show, toast,
  recentScansEl, recentListEl, historySearchInput, historyGradeSelect,
  clearHistoryBtn, exportHistoryBtn: $('export-history'),
  errorEl, statusEl, resultEl,
  listScans, deleteScan, clearScans, saveScan,
  filterScanHistory, summarizeScanHistory, timeAgoBucket, todayISO,
  getQueue: () => queue,
  setLastData: (d) => { lastData = d; },
  renderAudit, renderIngredients, renderNutrition,
  makeActivatable,
});

initQaPhotoId({ t, getMode, getKey, loadEngine });
initDashboardCharts({ setEditingEntry: (e) => { editingEntry = e; } });
initCustomFoodsDayNotes({ renderWeeklyView, renderMonthlyView, renderDashboard });
initCustomFoodsDialog();
initProfilesDialog({
  t, getProfile,
  saveProfile, switchProfile, deleteProfile,
  renderRecentScans, renderDashboard, renderWeightSummary,
  applyTheme, applyReadingPrefs,
});

renderQueue();
updatePendingBanner();
renderRecentScans();
// If the user's last session was in Week view, apply that now — otherwise
// applyViewToggle('day') is a no-op since 'day' is the default state.
if (getDashboardView() === 'week') applyViewToggle('week');
else renderDashboard();
maybeShowOnboarding({ t });
// scheduleReminders() is called inside initReminders() above.

// ----- Dashboard-first for returning users -----
// If the user logged anything in the last 3 days, they're in "daily use" mode
// and the dashboard (kcal remaining, macros) is more useful above the fold
// than the scan-capture card. CSS handles the reorder via body.returning-user
// so it works even if the user scrolls down and back up.
(async () => {
  try {
    const entries = await listByDate().catch(() => []);
    const logged3d = entries.length > 0; // today specifically; cheap proxy
    if (logged3d) document.body.classList.add('returning-user');
  } catch { /* non-critical */ }
})();
