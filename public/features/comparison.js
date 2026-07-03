/**
 * "Compare next scan" — A/B diff between the previous scan + the next.
 *
 * Flow:
 *   1. User scans Product A, hits "Compare next" → snapshot A is stashed
 *      in localStorage with an "armed" flag and a 24 h TTL.
 *   2. User scans Product B → maybeRenderComparison() reads the snapshot,
 *      paints the diff (grade, score delta, added / lost ingredients),
 *      and disarms.
 *
 * Storage keys (canonical, owned by this module):
 *   scanneat.compare_armed     '1' when a comparison is pending
 *   scanneat.compare_armed_at  ms timestamp; checked against TTL on read
 *   scanneat.compare_prev      JSON snapshot of Product A
 *
 * The 24 h TTL was added in R34.I1 — without it, a user who armed the
 * compare, got distracted, then scanned a totally unrelated product
 * days later saw a bogus diff. Arming is now session-scoped intent.
 */

// 24 h: long enough to span a shopping trip + the drive home + dinner;
// short enough that arming → next-day-other-product doesn't surprise.
const COMPARE_ARM_TTL_MS = 24 * 60 * 60 * 1000;
const LS_COMPARE_ARMED = 'scanneat.compare_armed';
const LS_COMPARE_ARMED_AT = 'scanneat.compare_armed_at';
const LS_COMPARE_PREV = 'scanneat.compare_prev';

/**
 * Returns true if a comparison is armed AND still within the TTL.
 * Side-effect: clears the keys when expired so the next read is clean.
 */
export function compareArmed() {
  if (localStorage.getItem(LS_COMPARE_ARMED) !== '1') return false;
  const armedAt = Number(localStorage.getItem(LS_COMPARE_ARMED_AT) || 0);
  if (!Number.isFinite(armedAt) || Date.now() - armedAt > COMPARE_ARM_TTL_MS) {
    localStorage.removeItem(LS_COMPARE_ARMED);
    localStorage.removeItem(LS_COMPARE_ARMED_AT);
    localStorage.removeItem(LS_COMPARE_PREV);
    return false;
  }
  return true;
}

function previousSnapshot() {
  const raw = localStorage.getItem(LS_COMPARE_PREV);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

/**
 * Paint the comparison panel for the just-completed scan, or hide it
 * when no compare is armed. Reads + clears the armed snapshot.
 *
 * deps: { $, t, show, hide, snapshotFromData, comparisonEl }
 */
export function maybeRenderComparison(data, deps) {
  const { $, t, show, hide, snapshotFromData, comparisonEl } = deps;
  const prev = previousSnapshot();
  if (!compareArmed() || !prev) { hide(comparisonEl); return; }

  const current = snapshotFromData(data);
  $('a-grade').textContent = prev.grade; $('a-grade').dataset.grade = prev.grade;
  $('a-name').textContent = prev.name; $('a-score').textContent = String(prev.score);
  $('b-grade').textContent = current.grade; $('b-grade').dataset.grade = current.grade;
  $('b-name').textContent = current.name; $('b-score').textContent = String(current.score);
  const delta = current.score - prev.score;
  const sign = delta > 0 ? '+' : '';
  const direction = delta > 0 ? ` ${t('betterCurrent')}` : delta < 0 ? ` ${t('betterPrev')}` : '';
  const prevIng = new Set(prev.ingredients.map((s) => s.toLowerCase()));
  const curIng = new Set(current.ingredients.map((s) => s.toLowerCase()));
  const added = [...curIng].filter((i) => !prevIng.has(i));
  const lost = [...prevIng].filter((i) => !curIng.has(i));

  const deltaEl = $('compare-delta');
  deltaEl.textContent = ''; // clear safely (no innerHTML)
  const frag = document.createDocumentFragment();
  frag.append(`${t('deltaScore')}: `);
  const s = document.createElement('strong');
  s.textContent = `${sign}${delta}`;
  frag.appendChild(s);
  frag.append(direction);
  deltaEl.appendChild(frag);
  if (added.length) {
    deltaEl.append(' • ', `${t('newIngredients')}: ${added.slice(0, 4).join(', ')}${added.length > 4 ? '…' : ''}`);
  }
  if (lost.length) {
    deltaEl.append(' • ', `${t('lostIngredients')}: ${lost.slice(0, 4).join(', ')}${lost.length > 4 ? '…' : ''}`);
  }
  show(comparisonEl);
  localStorage.removeItem(LS_COMPARE_ARMED);
  localStorage.removeItem(LS_COMPARE_PREV);
}

/**
 * Arm a comparison: stash the current scan as Snapshot A, flip the
 * armed flag + the timestamp. Updates the button label so the user
 * sees the state.
 *
 * deps: { t, snapshotFromData, compareNextBtn }
 */
export function armComparison(data, deps) {
  const { t, snapshotFromData, compareNextBtn } = deps;
  localStorage.setItem(LS_COMPARE_ARMED, '1');
  localStorage.setItem(LS_COMPARE_ARMED_AT, String(Date.now()));
  localStorage.setItem(LS_COMPARE_PREV, JSON.stringify(snapshotFromData(data)));
  compareNextBtn.textContent = t('compareWaiting');
  compareNextBtn.disabled = true;
}

/** Clear an armed compare (user explicitly cancels). */
export function clearComparison(deps) {
  const { hide, comparisonEl } = deps;
  hide(comparisonEl);
  localStorage.removeItem(LS_COMPARE_ARMED);
  localStorage.removeItem(LS_COMPARE_PREV);
}

/**
 * Wire the buttons. Call once during boot.
 *
 * deps: { compareNextBtn, compareClear, hide, show, t,
 *         comparisonEl, snapshotFromData, $, getLastData }
 *   getLastData — () => last scan's data object, or null.
 */
export function initComparison(deps) {
  const { compareNextBtn, compareClear, getLastData, t } = deps;
  compareNextBtn?.addEventListener('click', () => {
    const lastData = getLastData();
    if (lastData) armComparison(lastData, deps);
  });
  compareClear?.addEventListener('click', () => clearComparison(deps));
  // Restore the "waiting" UI if we boot with an already-armed compare.
  if (compareArmed()) {
    compareNextBtn?.setAttribute('disabled', 'true');
    if (compareNextBtn) compareNextBtn.textContent = t('compareWaiting');
  }
}
