/**
 * weight-calibration.js
 *
 * Computes Spearman rank correlation between each pillar's raw score and an
 * external reference (Nutri-Score) across the user's scan history.
 *
 * PURPOSE
 * -------
 * The 5-pillar weights (20/25/25/15/15) are editorial. This module gives the
 * user quantitative grounds to argue for or against each weight: if a pillar
 * correlates strongly with Nutri-Score it is "pulling in the same direction"
 * as the published EU reference; if it correlates weakly or inversely, either
 * the pillar captures something Nutri-Score misses, or its weight is off.
 *
 * ALGORITHM
 * ---------
 * Spearman ρ = 1 − 6∑dᵢ² / (n(n²−1))
 * where dᵢ is the rank difference for observation i.
 * Chosen over Pearson because pillar scores are bounded/skewed.
 *
 * Nutri-Score → numeric: a=5, b=4, c=3, d=2, e=1 (higher = better).
 *
 * Suggested weights use a proportional re-scaling of |ρ| per pillar,
 * normalised so the 5 weights still sum to 100, with a minimum floor
 * of 5 pts per pillar (no pillar goes to zero).
 *
 * LIMITATIONS (surfaced to the user in the UI)
 * -----------------------------------------------
 * - n < 10: correlation is meaningless; function returns {insufficient: true}.
 * - Records without a nutriscore_grade are excluded (LLM-only scans, manual
 *   entries, or scans from before this version). Low OFF coverage = small n.
 * - Nutri-Score does not model additives, NOVA, or ingredient integrity —
 *   weak correlation on those pillars is expected and does not mean they are
 *   wrong.
 * - Suggested weights are a starting point, not a recommendation to blindly
 *   apply. The correlation is against one external reference; other valid
 *   references (SAIN/LIM, FSA, user satisfaction) might yield different results.
 */

/** Nutri-Score grade → numeric reference (higher = better). */
export function nutriscoreToNum(grade) {
  return { a: 5, b: 4, c: 3, d: 2, e: 1 }[grade?.toLowerCase()] ?? null;
}

/**
 * Rank an array of numbers (ascending, average-rank for ties).
 * @param {number[]} xs
 * @returns {number[]}
 */
function ranks(xs) {
  const indexed = xs.map((v, i) => ({ v, i }));
  indexed.sort((a, b) => a.v - b.v);
  const r = new Array(xs.length);
  let j = 0;
  while (j < indexed.length) {
    let k = j;
    while (k + 1 < indexed.length && indexed[k + 1].v === indexed[j].v) k++;
    const avgRank = (j + 1 + k + 1) / 2; // 1-based average
    for (let m = j; m <= k; m++) r[indexed[m].i] = avgRank;
    j = k + 1;
  }
  return r;
}

/**
 * Spearman ρ between two equal-length arrays.
 * Returns NaN if n < 2 or arrays have zero variance.
 */
export function spearman(xs, ys) {
  const n = xs.length;
  if (n < 2 || ys.length !== n) return NaN;
  const rx = ranks(xs);
  const ry = ranks(ys);
  let sumD2 = 0;
  for (let i = 0; i < n; i++) sumD2 += (rx[i] - ry[i]) ** 2;
  return 1 - (6 * sumD2) / (n * (n * n - 1));
}

/**
 * Current editorial pillar weights.
 * @type {Record<string, number>}
 */
export const CURRENT_WEIGHTS = {
  processing:           20,
  nutritional_density:  25,
  negative_nutrients:   25,
  additive_risk:        15,
  ingredient_integrity: 15,
};

const PILLARS = Object.keys(CURRENT_WEIGHTS);
const MIN_WEIGHT = 5;   // floor per pillar
const MIN_N      = 10;  // minimum usable observations

/**
 * Extract pillar scores from a history record's snapshot.
 * Returns null if the snapshot is missing or incomplete.
 *
 * @param {object} record  — a scan-history IDB record
 * @returns {{ processing, nutritional_density, negative_nutrients,
 *             additive_risk, ingredient_integrity, nutriscoreNum } | null}
 */
function extractObservation(record) {
  const pillars = record?.snapshot?.audit?.pillars;
  if (!pillars) return null;
  const nutriscoreNum = nutriscoreToNum(record.nutriscore_grade);
  if (nutriscoreNum === null) return null; // no Nutri-Score → exclude
  const obs = {};
  for (const p of PILLARS) {
    const score = pillars[p]?.score;
    if (typeof score !== 'number' || !Number.isFinite(score)) return null;
    obs[p] = score;
  }
  obs.nutriscoreNum = nutriscoreNum;
  return obs;
}

/**
 * @typedef {object} PillarCalibration
 * @property {string}  pillar
 * @property {number}  current_weight
 * @property {number}  rho            Spearman ρ vs Nutri-Score
 * @property {number}  suggested_weight
 * @property {string}  interpretation  Human-readable reading of ρ
 */

/**
 * @typedef {object} CalibrationResult
 * @property {boolean}             insufficient  True when n < MIN_N
 * @property {number}              n             Observations used
 * @property {number}              n_total       Total history records checked
 * @property {PillarCalibration[]} pillars
 * @property {string}              note
 */

/**
 * Run calibration against the user's scan history.
 *
 * @param {object[]} historyRecords  Array from listScans()
 * @returns {CalibrationResult}
 */
export function calibrateWeights(historyRecords) {
  const obs = historyRecords.map(extractObservation).filter(Boolean);
  const n = obs.length;
  const nTotal = historyRecords.length;

  if (n < MIN_N) {
    return {
      insufficient: true,
      n,
      n_total: nTotal,
      pillars: [],
      note: n === 0
        ? `No scans with Nutri-Score data yet. Nutri-Score is fetched from Open Food Facts for barcode scans. Scan ${MIN_N}+ products with barcodes to enable calibration.`
        : `Only ${n} scan${n === 1 ? '' : 's'} with Nutri-Score data (need ≥ ${MIN_N}). Keep scanning barcoded products.`,
    };
  }

  // Build per-pillar series
  const nutriscoreVec = obs.map((o) => o.nutriscoreNum);
  const rhos = {};
  for (const p of PILLARS) {
    const vec = obs.map((o) => o[p]);
    rhos[p] = spearman(vec, nutriscoreVec);
  }

  // Suggested weights: proportional to |ρ| with floor MIN_WEIGHT,
  // normalised to sum = 100.
  const absRhos = PILLARS.map((p) => Math.max(0, isNaN(rhos[p]) ? 0 : Math.abs(rhos[p])));
  const totalAbsRho = absRhos.reduce((s, v) => s + v, 0);
  const rawSuggested = totalAbsRho === 0
    ? PILLARS.map(() => 20) // flat if all rho=0
    : absRhos.map((r) => Math.max(MIN_WEIGHT, Math.round((r / totalAbsRho) * 100)));

  // Re-normalise to exactly 100
  const suggested = normaliseWeights(rawSuggested);

  const pillarResults = PILLARS.map((p, i) => ({
    pillar: p,
    current_weight: CURRENT_WEIGHTS[p],
    rho: isNaN(rhos[p]) ? null : Math.round(rhos[p] * 100) / 100,
    suggested_weight: suggested[i],
    interpretation: interpretRho(rhos[p]),
  }));

  const hasEnoughNutriscore = (n / nTotal) >= 0.3;

  return {
    insufficient: false,
    n,
    n_total: nTotal,
    pillars: pillarResults,
    note: [
      `Based on ${n} of ${nTotal} history scan${nTotal === 1 ? '' : 's'} with Nutri-Score data.`,
      hasEnoughNutriscore ? '' : ` Low Nutri-Score coverage (${n}/${nTotal}) — correlations may be noisy.`,
      ' Nutri-Score does not model additives or NOVA; weak ρ on those pillars is expected.',
    ].join('').trim(),
  };
}

/** Normalise an array of non-negative numbers to sum to 100 (integers). */
function normaliseWeights(ws) {
  const total = ws.reduce((s, v) => s + v, 0);
  if (total === 0) return ws.map(() => Math.round(100 / ws.length));
  const scaled = ws.map((v) => (v / total) * 100);
  // Round, then fix rounding error on the largest value
  const rounded = scaled.map(Math.round);
  const diff = 100 - rounded.reduce((s, v) => s + v, 0);
  const maxIdx = rounded.indexOf(Math.max(...rounded));
  rounded[maxIdx] += diff;
  return rounded;
}

/** Human-readable interpretation of Spearman ρ. */
function interpretRho(rho) {
  if (rho === null || isNaN(rho)) return 'Not computable';
  const a = Math.abs(rho);
  const dir = rho >= 0 ? 'positive' : 'inverse';
  if (a >= 0.7) return `Strong ${dir} correlation with Nutri-Score`;
  if (a >= 0.4) return `Moderate ${dir} correlation with Nutri-Score`;
  if (a >= 0.2) return `Weak ${dir} correlation with Nutri-Score`;
  return 'No meaningful correlation with Nutri-Score';
}
