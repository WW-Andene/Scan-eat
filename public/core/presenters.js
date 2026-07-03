/**
 * Pure presentation helpers — no DOM, no i18n lookup, no side effects.
 *
 * Keeping these separate from app.js makes them directly testable under
 * node:test without a jsdom shim, and lets future UI refactors share the
 * same logic instead of re-inventing it per feature.
 */

import { dateFormatter } from './date-format.js';

/**
 * Confidence level for a scan result. Used to tint the "source" chip so the
 * user can quickly gauge how much to trust the extracted data.
 *
 *   high    — OpenFoodFacts match, or a clean LLM extraction with ≥4 macros
 *   medium  — acceptable LLM extraction
 *   low     — ≥2 warnings or ≤2 macros extracted
 */
export function computeConfidence(data) {
  if (!data || !data.product) return 'low';
  if (data.source === 'openfoodfacts') return 'high';
  const warns = data.warnings?.length || 0;
  const n = data.product.nutrition;
  if (!n) return 'low';
  const filled = [n.energy_kcal, n.fat_g, n.carbs_g, n.sugars_g, n.protein_g, n.salt_g]
    .filter((v) => typeof v === 'number' && v > 0).length;
  if (warns === 0 && filled >= 4) return 'high';
  if (warns >= 2 || filled <= 2) return 'low';
  return 'medium';
}

/**
 * Compact snapshot used by the "compare next scan" feature. Strips the audit
 * + nutrition down to just the fields needed to render the diff, so we don't
 * blow up localStorage with a full product object.
 */
export function snapshotFromData(data) {
  return {
    name: data.audit.product_name || data.product.name,
    grade: data.audit.grade,
    score: data.audit.score,
    ingredients: (data.product.ingredients ?? []).map((i) => i.name),
  };
}

/**
 * Bucket an age in milliseconds into a time-ago token for i18n lookup.
 * Split from the display string so the rounding logic can be unit-tested
 * without mocking the i18n layer.
 *
 *   ms < 60 000        → { kind: 'justNow' }
 *   ms < 3 600 000     → { kind: 'minutes', n: round(ms / 60 000) }
 *   ms < 86 400 000    → { kind: 'hours',   n: round(ms / 3 600 000) }
 *   otherwise          → { kind: 'days',    n: round(ms / 86 400 000) }
 */
export function timeAgoBucket(msAgo) {
  const min = Math.round(msAgo / 60000);
  if (min < 1) return { kind: 'justNow' };
  if (min < 60) return { kind: 'minutes', n: min };
  const h = Math.round(min / 60);
  if (h < 24) return { kind: 'hours', n: h };
  const d = Math.round(h / 24);
  return { kind: 'days', n: d };
}

/**
 * Default meal bucket for a given hour-of-day. Pure function (takes hour,
 * returns a MEALS key) so it can be used anywhere a Meal picker needs a
 * reasonable default without a fresh Date() call.
 *
 *   5-10 → breakfast  (early morning)
 *  10-14 → lunch      (midday)
 *  14-18 → snack      (afternoon)
 *  18-24, 0-5 → dinner (evening + late night)
 */
export function defaultMealForHour(hour) {
  if (hour >= 5  && hour < 10) return 'breakfast';
  if (hour >= 10 && hour < 14) return 'lunch';
  if (hour >= 14 && hour < 18) return 'snack';
  return 'dinner';
}

/**
 * Current logging streak in days, counting backwards from `todayIso`.
 * A day counts as "logged" if at least one entry exists with that date.
 *
 * Returns 0 when:
 *   - no entries exist
 *   - today has no entries AND yesterday has no entries (streak broken)
 * Returns n when the last n consecutive days (including today or yesterday)
 * all have entries. "Yesterday counts" is intentional: if the user opens
 * the app in the morning before logging breakfast, we don't want the
 * streak to show 0 just because today is still empty — grace the last
 * 24h so the user can see their current standing and keep it going.
 *
 * All date math uses ISO YYYY-MM-DD strings, so timezone jumps at midnight
 * don't break the count.
 */
/**
 * Parse a voice-dictated phrase into Quick-Add field candidates. Pure
 * function — no SpeechRecognition or DOM involvement. Recognizes:
 *
 *   kcal      → "120 calories", "120 kcal", "120 cal"
 *   grams     → "250 g", "250 grammes", "250 grams"
 *   ml        → "250 ml" (treated same as g for a rough portion size)
 *   protein   → "15 g de protéines", "15 g protein"
 *   carbs     → "30 g de glucides", "30 g carbs"
 *   fat       → "10 g de lipides", "10 g fat"
 *
 * Numbers accept both French comma decimals ("8,5") and point decimals.
 * Anything left over (no recognised number-unit pair) becomes the name.
 *
 * Returns an object with only the keys it could extract — the caller
 * spreads into the form so empty fields stay empty.
 */
export function parseVoiceQuickAdd(transcript) {
  if (!transcript) return {};
  const text = String(transcript).toLowerCase();
  const out = {};

  // Match "NUMBER (unit|label)". First match wins for each nutrient. French
  // comma decimals and optional "de/of" before the nutrient label.
  const num = '([0-9]+(?:[.,][0-9]+)?)';
  const n = (s) => parseFloat(String(s).replace(',', '.'));
  const grab = (re) => {
    const m = text.match(re);
    return m ? n(m[1]) : undefined;
  };

  const kcal = grab(new RegExp(`${num}\\s*(?:kcal|calories?|cal)\\b`, 'i'));
  // Optional connector word — "de" (FR), "of" (EN), or nothing.
  const conn = '(?:(?:de|of)\\s+)?';
  const protein = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:prot[eé]ines?|protein)\\b`, 'i'));
  const carbs = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:glucides?|carbs?|carbohydrates?)\\b`, 'i'));
  const fat = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:lipides?|fat|mati[eè]res? grasses?)\\b`, 'i'));
  // Fix #11 — expanded: sat-fat / sugars / salt / fiber. Uses the
  // same grammar. Matches "3 g de graisses saturées", "12 g sucres",
  // "0.5 g sel", "4 g fibres", and the English equivalents.
  const satFat = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:graisses? satur[eé]es?|sat(?:urated)?[- ]?fat|saturées)\\b`, 'i'));
  const sugars = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:sucres?|sugars?)\\b`, 'i'));
  const salt   = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:sel|salt)\\b`, 'i'));
  const fiber  = grab(new RegExp(`${num}\\s*g(?:rammes?)?\\s+${conn}(?:fibres?|fiber|fibre)\\b`, 'i'));

  // Portion (grams OR ml). Only take if no macro claimed the same number —
  // tested last and excluded when the nutrient labels are adjacent.
  // The 'g' flag is REQUIRED on this regex so .exec() advances lastIndex
  // between iterations. Without it, a "continue" inside the while loop
  // re-matches the same position forever — that's the bug that hung
  // presenter-tests.ts for ~6 weeks before B6.1.
  const portionRe = new RegExp(`${num}\\s*(g(?:rammes?)?|ml|millilitres?)(?![a-zà-ÿ])`, 'gi');
  let grams;
  let m;
  while ((m = portionRe.exec(text)) !== null) {
    // Reject if this number was already consumed by a macro label.
    const next20 = text.slice(m.index + m[0].length, m.index + m[0].length + 30);
    if (/^\s*(?:(?:de|of)\s+)?(?:prot|gluc|carb|lipid|fat|mati[eè]re)/.test(next20)) continue;
    grams = n(m[1]);
    break;
  }

  if (Number.isFinite(kcal))    out.kcal = kcal;
  if (Number.isFinite(protein)) out.protein_g = protein;
  if (Number.isFinite(carbs))   out.carbs_g = carbs;
  if (Number.isFinite(fat))     out.fat_g = fat;
  if (Number.isFinite(satFat))  out.sat_fat_g = satFat;
  if (Number.isFinite(sugars))  out.sugars_g = sugars;
  if (Number.isFinite(salt))    out.salt_g = salt;
  if (Number.isFinite(fiber))   out.fiber_g = fiber;
  if (Number.isFinite(grams))   out.grams = grams;

  // Meal slot — users commonly say "petit-déjeuner bananes" or
  // "dinner chicken". Snap to the canonical meal key when the transcript
  // starts with one of the known labels. Callers decide whether to
  // override qa-meal.
  if (/\b(petit[- ]d[eé]jeuner|breakfast|brunch)\b/i.test(text)) out.meal = 'breakfast';
  else if (/\b(d[eé]jeuner|lunch|midi)\b/i.test(text))           out.meal = 'lunch';
  else if (/\b(d[iî]ner|dinner|soir)\b/i.test(text))             out.meal = 'dinner';
  else if (/\b(snack|en[- ]cas|go[uû]ter|collation)\b/i.test(text)) out.meal = 'snack';

  // Name: strip out the unit-number blocks we consumed. Whatever's left is
  // the food name. Trim punctuation + whitespace.
  let name = text
    .replace(new RegExp(`${num}\\s*(?:kcal|calories?|cal|kilocalories?)\\b`, 'gi'), ' ')
    .replace(new RegExp(`${num}\\s*g(?:rammes?)?\\s+(?:de\\s+)?(?:prot[eé]ines?|protein|glucides?|carbs?|carbohydrates?|lipides?|fat|mati[eè]res? grasses?|graisses? satur[eé]es?|sat(?:urated)?[- ]?fat|saturées|sucres?|sugars?|sel|salt|fibres?|fiber|fibre)\\b`, 'gi'), ' ')
    .replace(new RegExp(`${num}\\s*(?:g(?:rammes?)?|ml|millilitres?)(?![a-zà-ÿ])`, 'gi'), ' ')
    // Meal labels handled separately into out.meal; strip from name so
    // the food-name field doesn't contain the meal word.
    .replace(/\b(petit[- ]d[eé]jeuner|breakfast|brunch|d[eé]jeuner|lunch|midi|d[iî]ner|dinner|soir|snack|en[- ]cas|go[uû]ter|collation)\b/gi, ' ')
    .replace(/[,;.]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (name.length > 0) out.name = name;

  return out;
}

/**
 * Recommended daily water intake in millilitres.
 *
 * Baseline: EFSA 2010 adequate intake for water (NDA panel) — 2 000 ml/d
 * for women, 2 500 ml/d for men, from ALL sources (food + drinks). We use
 * the drink-only ~75 % portion of that: 1500 / 2000 ml/day as a soft goal
 * the app can nudge toward. Scaled by body weight when available.
 *
 * Returns a millilitre integer (rounded to nearest 100 for readability).
 *
 * Edge cases:
 *   - No profile         → 2000 ml (unisex default)
 *   - Weight but no sex  → 33 ml/kg (common clinical shortcut, Holliday-
 *                          Segar adult derivation; same anchor as EFSA)
 *   - High activity      → +500 ml (WHO training heuristic)
 */
export function waterGoalMl(profile) {
  // User-set override wins. Clamp to a sane range (500 ml – 6 L) so a
  // typo like 20000 doesn't make the progress bar uselessly tiny.
  const override = Number(profile?.water_goal_ml);
  if (Number.isFinite(override) && override >= 500 && override <= 6000) {
    return Math.round(override / 100) * 100;
  }

  const weight = Number(profile?.weight_kg);
  const sex = profile?.sex;
  const activity = profile?.activity;

  let base;
  if (Number.isFinite(weight) && weight > 0) {
    base = weight * 33;
  } else if (sex === 'female') {
    base = 1500;
  } else if (sex === 'male' || sex === 'other') {
    base = 2000;
  } else {
    base = 2000;
  }

  if (activity === 'active' || activity === 'very_active') base += 500;

  return Math.round(base / 100) * 100;
}

/**
 * Group consumption entries into a rolling 7-day window ending on `endIso`
 * (inclusive). Pure — no IDB, no Date fancy-footwork past ISO strings.
 *
 * Returns:
 *   days:  array of 7 { date, kcal, carbs_g, protein_g, fat_g, sat_fat_g,
 *          sugars_g, salt_g, count } — oldest first, always length 7 (empty
 *          days are zero-filled so the chart has stable columns)
 *   total: summed totals across the 7 days
 *   avg:   day averages rounded to 1 decimal
 *   days_logged: count of days with at least one entry (for "6/7 days")
 */
export function weeklyRollup(entries, endIso) {
  const end = new Date(endIso + 'T12:00:00Z');
  const days = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(end);
    d.setUTCDate(d.getUTCDate() - i);
    const date = d.toISOString().slice(0, 10);
    days.push({
      date, kcal: 0, carbs_g: 0, protein_g: 0, fat_g: 0,
      sat_fat_g: 0, sugars_g: 0, salt_g: 0, count: 0,
    });
  }
  const byDate = new Map(days.map((d) => [d.date, d]));
  for (const e of entries ?? []) {
    const bucket = byDate.get(e.date);
    if (!bucket) continue;
    bucket.kcal      += Number(e.kcal) || 0;
    bucket.carbs_g   += Number(e.carbs_g) || 0;
    bucket.protein_g += Number(e.protein_g) || 0;
    bucket.fat_g     += Number(e.fat_g) || 0;
    bucket.sat_fat_g += Number(e.sat_fat_g) || 0;
    bucket.sugars_g  += Number(e.sugars_g) || 0;
    bucket.salt_g    += Number(e.salt_g) || 0;
    bucket.count += 1;
  }
  const total = days.reduce(
    (acc, d) => ({
      kcal:      acc.kcal + d.kcal,
      carbs_g:   acc.carbs_g + d.carbs_g,
      protein_g: acc.protein_g + d.protein_g,
      fat_g:     acc.fat_g + d.fat_g,
      sat_fat_g: acc.sat_fat_g + d.sat_fat_g,
      sugars_g:  acc.sugars_g + d.sugars_g,
      salt_g:    acc.salt_g + d.salt_g,
    }),
    { kcal: 0, carbs_g: 0, protein_g: 0, fat_g: 0, sat_fat_g: 0, sugars_g: 0, salt_g: 0 },
  );
  const round1 = (v) => Math.round(v * 10) / 10;
  const round3 = (v) => Math.round(v * 1000) / 1000;
  const daysLogged = days.filter((d) => d.count > 0).length;
  // Average over days WITH entries — if the user hasn't logged some days,
  // averaging over 7 understates their typical intake.
  const denom = Math.max(1, daysLogged);
  const avg = {
    kcal:      round1(total.kcal / denom),
    carbs_g:   round1(total.carbs_g / denom),
    protein_g: round1(total.protein_g / denom),
    fat_g:     round1(total.fat_g / denom),
    sat_fat_g: round1(total.sat_fat_g / denom),
    sugars_g:  round1(total.sugars_g / denom),
    salt_g:    round3(total.salt_g / denom),
  };
  return { days, total, avg, days_logged: daysLogged };
}

/**
 * monthlyRollup — same shape as weeklyRollup but over a 30-day trailing
 * window ending at endIso. Used by the upcoming month view in the
 * dashboard.
 *
 * Pure. avg/day is averaged over days-with-entries (same convention as
 * weekly), not over 30, so a partially-logged month reports a realistic
 * "typical day" figure rather than diluting by empty days.
 */
export function monthlyRollup(entries, endIso) {
  const end = new Date(endIso + 'T12:00:00Z');
  const days = [];
  for (let i = 29; i >= 0; i--) {
    const d = new Date(end);
    d.setUTCDate(d.getUTCDate() - i);
    const date = d.toISOString().slice(0, 10);
    days.push({
      date, kcal: 0, carbs_g: 0, protein_g: 0, fat_g: 0,
      sat_fat_g: 0, sugars_g: 0, salt_g: 0, count: 0,
    });
  }
  const byDate = new Map(days.map((d) => [d.date, d]));
  for (const e of entries ?? []) {
    const bucket = byDate.get(e.date);
    if (!bucket) continue;
    bucket.kcal      += Number(e.kcal) || 0;
    bucket.carbs_g   += Number(e.carbs_g) || 0;
    bucket.protein_g += Number(e.protein_g) || 0;
    bucket.fat_g     += Number(e.fat_g) || 0;
    bucket.sat_fat_g += Number(e.sat_fat_g) || 0;
    bucket.sugars_g  += Number(e.sugars_g) || 0;
    bucket.salt_g    += Number(e.salt_g) || 0;
    bucket.count += 1;
  }
  const total = days.reduce(
    (acc, d) => ({
      kcal:      acc.kcal + d.kcal,
      carbs_g:   acc.carbs_g + d.carbs_g,
      protein_g: acc.protein_g + d.protein_g,
      fat_g:     acc.fat_g + d.fat_g,
      sat_fat_g: acc.sat_fat_g + d.sat_fat_g,
      sugars_g:  acc.sugars_g + d.sugars_g,
      salt_g:    acc.salt_g + d.salt_g,
    }),
    { kcal: 0, carbs_g: 0, protein_g: 0, fat_g: 0, sat_fat_g: 0, sugars_g: 0, salt_g: 0 },
  );
  const r1 = (v) => Math.round(v * 10) / 10;
  const r3 = (v) => Math.round(v * 1000) / 1000;
  const daysLogged = days.filter((d) => d.count > 0).length;
  const denom = Math.max(1, daysLogged);
  const avg = {
    kcal:      r1(total.kcal / denom),
    carbs_g:   r1(total.carbs_g / denom),
    protein_g: r1(total.protein_g / denom),
    fat_g:     r1(total.fat_g / denom),
    sat_fat_g: r1(total.sat_fat_g / denom),
    sugars_g:  r1(total.sugars_g / denom),
    salt_g:    r3(total.salt_g / denom),
  };
  return { days, total, avg, days_logged: daysLogged };
}

/**
 * Human-readable one-screen summary of a weekly rollup — for
 * navigator.share or copy-paste into a notes app. Pure.
 *
 * Example output:
 *   Semaine du 15 au 21 avril 2026 — 6 jours enregistrés
 *   Moy/jour : 1870 kcal · 55 g prot · 220 g gluc · 75 g lip
 *   Total : 11220 kcal
 *
 *   lun. 15  1900 kcal  56 prot · 220 gluc · 80 lip
 *   mar. 16  …
 */
export function formatWeeklyShare(rollup, opts = {}) {
  if (!rollup || !Array.isArray(rollup.days) || rollup.days.length === 0) return '';
  const { lang = 'fr' } = opts;
  const isFr = lang !== 'en';
  const locale = isFr ? 'fr-FR' : 'en-GB';

  const firstDay = new Date(rollup.days[0].date + 'T12:00:00');
  const lastDay = new Date(rollup.days[rollup.days.length - 1].date + 'T12:00:00');
  const sameMonth = firstDay.getUTCMonth() === lastDay.getUTCMonth();
  const firstStr = dateFormatter(locale, sameMonth ? { day: 'numeric' } : { day: 'numeric', month: 'short' }).format(firstDay);
  const lastStr = dateFormatter(locale, { day: 'numeric', month: 'long', year: 'numeric' }).format(lastDay);
  const range = isFr ? `Semaine du ${firstStr} au ${lastStr}` : `Week of ${firstStr} to ${lastStr}`;

  const r = (n) => Math.round(n);
  const avg = rollup.avg || {};
  const total = rollup.total || {};
  const daysLogged = rollup.days_logged ?? 0;

  const lines = [];
  lines.push(isFr
    ? `${range} — ${daysLogged} jour(s) enregistré(s)`
    : `${range} — ${daysLogged} day(s) logged`);
  lines.push(isFr
    ? `Moy/jour : ${r(avg.kcal)} kcal · ${r(avg.protein_g)} g prot · ${r(avg.carbs_g)} g gluc · ${r(avg.fat_g)} g lip`
    : `Avg/day: ${r(avg.kcal)} kcal · ${r(avg.protein_g)} g prot · ${r(avg.carbs_g)} g carbs · ${r(avg.fat_g)} g fat`);
  lines.push(isFr ? `Total : ${r(total.kcal)} kcal` : `Total: ${r(total.kcal)} kcal`);
  lines.push('');
  const dayFmt = dateFormatter(locale, { weekday: 'short', day: 'numeric' });
  for (const d of rollup.days) {
    const date = new Date(d.date + 'T12:00:00');
    const dayStr = dayFmt.format(date);
    if (d.count === 0) {
      lines.push(isFr ? `${dayStr}  —` : `${dayStr}  —`);
    } else {
      lines.push(`${dayStr}  ${r(d.kcal)} kcal  ${r(d.protein_g)} prot · ${r(d.carbs_g)} gluc · ${r(d.fat_g)} lip`);
    }
  }
  return lines.join('\n');
}

/**
 * formatMonthlyShare — serialises a monthlyRollup into a compact share
 * block. Unlike formatWeeklyShare, we skip the per-day lines (30 rows
 * is too much for a share target) and instead surface avg / total /
 * days-logged with a count of "on-goal" days.
 *
 * Returns '' when the rollup has no logged days.
 */
export function formatMonthlyShare(rollup, opts = {}) {
  if (!rollup || !Array.isArray(rollup.days) || rollup.days_logged === 0) return '';
  const { lang = 'fr', kcalTarget = 0 } = opts;
  const isFr = lang !== 'en';
  const r = (n) => Math.round(Number(n) || 0);
  const firstDate = rollup.days[0]?.date;
  const lastDate = rollup.days[rollup.days.length - 1]?.date;
  const range = firstDate && lastDate
    ? (isFr ? `du ${firstDate} au ${lastDate}` : `${firstDate} → ${lastDate}`)
    : '';
  const onGoal = kcalTarget > 0
    ? rollup.days.filter((d) => d.count > 0 && Math.abs(d.kcal - kcalTarget) <= kcalTarget * 0.1).length
    : 0;
  const lines = [];
  lines.push(isFr
    ? `📅 30 jours ${range} — ${rollup.days_logged} jour(s) enregistré(s)`
    : `📅 30 days ${range} — ${rollup.days_logged} day(s) logged`);
  lines.push(isFr
    ? `Moy/jour : ${r(rollup.avg.kcal)} kcal · ${r(rollup.avg.protein_g)} g prot · ${r(rollup.avg.carbs_g)} g gluc · ${r(rollup.avg.fat_g)} g lip`
    : `Avg/day: ${r(rollup.avg.kcal)} kcal · ${r(rollup.avg.protein_g)} g prot · ${r(rollup.avg.carbs_g)} g carbs · ${r(rollup.avg.fat_g)} g fat`);
  lines.push(isFr
    ? `Total : ${r(rollup.total.kcal)} kcal`
    : `Total: ${r(rollup.total.kcal)} kcal`);
  if (kcalTarget > 0) {
    lines.push(isFr
      ? `Jours dans ±10% de la cible : ${onGoal}/${rollup.days_logged}`
      : `Days within ±10% of target: ${onGoal}/${rollup.days_logged}`);
  }
  lines.push('— Scan\'eat');
  return lines.join('\n');
}

/**
 * formatDailySummary — today's log as a shareable text block.
 * Pure; takes pre-aggregated totals + targets + burned-kcal snapshot.
 * Returns '' if nothing was logged. Used by the daily-share button;
 * mirrored in spirit by formatWeeklyShare above.
 *
 *   totals: { kcal, protein_g, carbs_g, fat_g, sat_fat_g, sugars_g, salt_g, count }
 *   targets: dailyTargets shape (may be null)
 *   burned: { kcal } (may be null)
 *   opts: { lang, dateISO }
 */
export function formatDailySummary(totals, targets, burned, opts = {}) {
  if (!totals || !(totals.count > 0)) return '';
  // Fix #14 + #15 — opts extended with optional hydration + dayNote.
  // Backward-compatible: existing callers passing only { lang, dateISO }
  // get the same output.
  const { lang = 'fr', dateISO, hydrationMl = null, hydrationGoalMl = null, dayNote = null } = opts;
  const isFr = lang !== 'en';
  const r = (n) => Math.round(Number(n) || 0);
  const r1 = (n) => Math.round((Number(n) || 0) * 10) / 10;
  const date = dateISO
    ? dateFormatter(isFr ? 'fr-FR' : 'en-GB', {
        weekday: 'long', day: 'numeric', month: 'long',
      }).format(new Date(`${dateISO}T12:00:00Z`))
    : null;

  const lines = [];
  const entriesWord = isFr
    ? (totals.count === 1 ? 'entrée' : 'entrées')
    : (totals.count === 1 ? 'entry' : 'entries');
  lines.push(isFr
    ? `🥗 ${date ?? 'Aujourd\'hui'} — ${totals.count} ${entriesWord}`
    : `🥗 ${date ?? 'Today'} — ${totals.count} ${entriesWord}`);
  const netKcal = r(totals.kcal) - r(burned?.kcal || 0);
  lines.push(isFr
    ? `${r(totals.kcal)} kcal consommées${burned?.kcal ? ` · ${r(burned.kcal)} brûlées · net ${netKcal}` : ''}`
    : `${r(totals.kcal)} kcal in${burned?.kcal ? ` · ${r(burned.kcal)} burned · net ${netKcal}` : ''}`);
  lines.push(isFr
    ? `Macros : ${r(totals.protein_g)} g protéines · ${r(totals.carbs_g)} g glucides · ${r(totals.fat_g)} g lipides`
    : `Macros: ${r(totals.protein_g)} g protein · ${r(totals.carbs_g)} g carbs · ${r(totals.fat_g)} g fat`);
  if (r1(totals.fiber_g)) {
    lines.push(isFr ? `Fibres : ${r1(totals.fiber_g)} g` : `Fiber: ${r1(totals.fiber_g)} g`);
  }
  // Fix #9 — surface micronutrients when present. Only emits rows
  // where the day's total exceeds the EU "source of" threshold
  // (keeps the share compact on a day of mostly-packaged scans).
  // Matches the NRV_15_PCT list from scoring-engine so what's
  // called a "source" in the score is a "source" in the share.
  const SOURCES = [
    ['iron_mg',       'Fer',        'Iron',        2.1,  'mg'],
    ['calcium_mg',    'Calcium',    'Calcium',     120,  'mg'],
    ['magnesium_mg',  'Magnésium',  'Magnesium',   56,   'mg'],
    ['potassium_mg',  'Potassium',  'Potassium',   300,  'mg'],
    ['zinc_mg',       'Zinc',       'Zinc',        1.5,  'mg'],
    ['vit_a_ug',      'Vit A',      'Vit A',       120,  'µg'],
    ['vit_c_mg',      'Vit C',      'Vit C',       12,   'mg'],
    ['vit_d_ug',      'Vit D',      'Vit D',       0.75, 'µg'],
    ['vit_e_mg',      'Vit E',      'Vit E',       1.8,  'mg'],
    ['b9_ug',         'Folates',    'Folate',      30,   'µg'],
    ['b12_ug',        'Vit B12',    'Vit B12',     0.375,'µg'],
    ['omega_3_g',     'Oméga-3',    'Omega-3',     0.3,  'g'],
  ];
  const hits = SOURCES
    .filter(([key, _fr, _en, threshold]) => (Number(totals[key]) || 0) >= threshold)
    .map(([key, frLab, enLab, _t, unit]) => `${isFr ? frLab : enLab} ${r1(totals[key])} ${unit}`);
  if (hits.length > 0) {
    lines.push(isFr ? `Micros : ${hits.join(' · ')}` : `Micros: ${hits.join(' · ')}`);
  }
  if (targets) {
    const pct = targets.kcal > 0 ? Math.round((totals.kcal / targets.kcal) * 100) : 0;
    lines.push(isFr
      ? `${pct}% de l'objectif (${r(targets.kcal)} kcal cible)`
      : `${pct}% of daily goal (${r(targets.kcal)} kcal target)`);
  }
  // Fix #14 — hydration line in the daily share.
  if (typeof hydrationMl === 'number' && hydrationMl > 0) {
    const suffix = typeof hydrationGoalMl === 'number' && hydrationGoalMl > 0
      ? ` / ${hydrationGoalMl} ml`
      : '';
    lines.push(isFr
      ? `💧 Hydratation : ${hydrationMl} ml${suffix}`
      : `💧 Water: ${hydrationMl} ml${suffix}`);
  }
  // Fix #15 — day note carries forward in the share when present.
  if (typeof dayNote === 'string' && dayNote.trim()) {
    const truncated = dayNote.length > 200 ? dayNote.slice(0, 200) + '…' : dayNote;
    lines.push(isFr ? `📝 Note : ${truncated}` : `📝 Note: ${truncated}`);
  }
  lines.push('— Scan\'eat');
  return lines.join('\n');
}

/**
 * R11.1 — pctClass: maps a 0..∞ "% of target" number to a traffic-light
 * bucket used for the progress-bar CSS state. Pure so the dashboard
 * row builder can live in presenters.js (same move pattern as the
 * share formatters).
 *
 *   < 80  → 'ok'   (green fill)
 *   80..99 → 'near' (amber, approaching goal)
 *   ≥ 100 → 'over'  (filled / exceeded)
 */
export function pctClass(pct) {
  if (pct >= 100) return 'over';
  if (pct >= 80) return 'near';
  return 'ok';
}

/**
 * R11.2 — dashboardRowsFrom: pure builder for the dashboard's progress-
 * row array. Keeps the DOM rendering loop in app.js cohesive and gives
 * the row layout a single source of truth (tests can assert ordering
 * / units / conditional micros without a jsdom shim).
 *
 * The caller is responsible for turning each row into a <li>.
 *
 * Row shape:
 *   { key: i18nKey, value: number, target: number | undefined, unit: string }
 *
 * Micros (iron / calcium / vit D / B12) are conditionally appended only
 * when the user has logged a non-zero value today — so unknown-micro
 * OFF products don't clutter the dashboard with four zero rows.
 */
export function dashboardRowsFrom(totals, targets) {
  const t = totals || {};
  const g = targets || null;
  const rows = [
    { key: 'dashKcal',    value: t.kcal       ?? 0, target: g?.kcal,              unit: 'kcal' },
    { key: 'dashCarbs',   value: t.carbs_g    ?? 0, target: g?.carbs_g_target,    unit: 'g' },
    { key: 'dashFiber',   value: t.fiber_g    ?? 0, target: g?.fiber_g_target,    unit: 'g' },
    { key: 'dashProtein', value: t.protein_g  ?? 0, target: g?.protein_g_target,  unit: 'g' },
    { key: 'dashFat',     value: t.fat_g      ?? 0, target: g?.fat_g_target,      unit: 'g' },
    { key: 'dashSatFat',  value: t.sat_fat_g  ?? 0, target: g?.sat_fat_g_max,     unit: 'g' },
    { key: 'dashSugars',  value: t.sugars_g   ?? 0, target: g?.free_sugars_g_max, unit: 'g' },
    { key: 'dashSalt',    value: t.salt_g     ?? 0, target: g?.salt_g_max,        unit: 'g' },
  ];
  // Feature 2 — Full micronutrient panel. Every field is optional:
  // rendered only when the day's total exceeds zero, so products that
  // don't report a given micro don't fill the dashboard with empty
  // rows. Target fields are looked up from dailyTargets() but default
  // to undefined (= "no target" state) when not set.
  const micro = (key, field, targetKey, unit) => {
    if ((t[field] ?? 0) > 0) {
      rows.push({ key, value: t[field], target: g?.[targetKey], unit });
    }
  };
  // Minerals
  micro('dashIron',       'iron_mg',       'iron_mg_target',       'mg');
  micro('dashCalcium',    'calcium_mg',    'calcium_mg_target',    'mg');
  micro('dashMagnesium',  'magnesium_mg',  'magnesium_mg_target',  'mg');
  micro('dashPotassium',  'potassium_mg',  'potassium_mg_target',  'mg');
  micro('dashZinc',       'zinc_mg',       'zinc_mg_target',       'mg');
  micro('dashSodium',     'sodium_mg',     'sodium_mg_max',        'mg');
  // Vitamins
  micro('dashVitA',       'vit_a_ug',      'vit_a_ug_target',      'µg');
  micro('dashVitC',       'vit_c_mg',      'vit_c_mg_target',      'mg');
  micro('dashVitD',       'vit_d_ug',      'vit_d_ug_target',      'µg');
  micro('dashVitE',       'vit_e_mg',      'vit_e_mg_target',      'mg');
  micro('dashVitK',       'vit_k_ug',      'vit_k_ug_target',      'µg');
  micro('dashB1',         'b1_mg',         'b1_mg_target',         'mg');
  micro('dashB2',         'b2_mg',         'b2_mg_target',         'mg');
  micro('dashB3',         'b3_mg',         'b3_mg_target',         'mg');
  micro('dashB6',         'b6_mg',         'b6_mg_target',         'mg');
  micro('dashB9',         'b9_ug',         'b9_ug_target',         'µg');
  micro('dashB12',        'b12_ug',        'b12_ug_target',        'µg');
  // Fat subdivisions + cholesterol
  micro('dashPufa',       'polyunsaturated_fat_g', 'pufa_g_target', 'g');
  micro('dashMufa',       'monounsaturated_fat_g', 'mufa_g_target', 'g');
  micro('dashOmega3',     'omega_3_g',     'omega_3_g_target',     'g');
  micro('dashOmega6',     'omega_6_g',     'omega_6_g_target',     'g');
  micro('dashCholesterol','cholesterol_mg','cholesterol_mg_max',   'mg');
  return rows;
}

/**
 * R11.7 — formatRecipeShare: a recipe's saved components rendered as a
 * compact share block. Same spirit as formatWeeklyShare /
 * formatDailySummary: pure, locale-aware, empty-safe.
 *
 *   recipe: { name, servings, components: [{ product_name, grams, kcal, protein_g, carbs_g, fat_g }] }
 *   opts:   { lang }
 *
 * Returns '' when the recipe has no components.
 */
export function formatRecipeShare(recipe, opts = {}) {
  if (!recipe || !Array.isArray(recipe.components) || recipe.components.length === 0) return '';
  const { lang = 'fr' } = opts;
  const isFr = lang !== 'en';
  const r = (n) => Math.round(Number(n) || 0);
  const servings = Math.max(1, Math.round(Number(recipe.servings) || 1));
  const sum = (key) => recipe.components.reduce((acc, c) => acc + (Number(c?.[key]) || 0), 0);
  const kcal = r(sum('kcal') / servings);
  const prot = r(sum('protein_g') / servings);
  const carb = r(sum('carbs_g') / servings);
  const fat  = r(sum('fat_g') / servings);
  const lines = [];
  // R14.3: legacy 'Sans nom' from pre-R14.1 recipes still round-trips
  // through here. Treat both empty AND the legacy sentinel as
  // "untitled" so EN users' historical data renders correctly.
  const name = recipe.name && recipe.name !== 'Sans nom'
    ? recipe.name
    : (isFr ? 'Sans nom' : 'Untitled');
  lines.push(isFr ? `🍽 Recette : ${name}` : `🍽 Recipe: ${name}`);
  lines.push(isFr
    ? `${kcal} kcal · P ${prot} g · G ${carb} g · L ${fat} g · pour ${servings} part(s)`
    : `${kcal} kcal · P ${prot} g · C ${carb} g · F ${fat} g · per ${servings} serving(s)`);
  lines.push('');
  lines.push(isFr ? 'Ingrédients :' : 'Ingredients:');
  for (const c of recipe.components) {
    const label = c.product_name || '—';
    const grams = r(c.grams);
    const ck = r(c.kcal);
    lines.push(grams > 0
      ? `• ${label} — ${grams} g · ${ck} kcal`
      : `• ${label} — ${ck} kcal`);
  }
  lines.push('— Scan\'eat');
  return lines.join('\n');
}

/**
 * R12.1 — formatTemplateShare: a saved meal template rendered as a
 * compact share block. Pairs with formatRecipeShare; templates differ
 * in that their items already carry per-item kcal + macros (recipes
 * carry per-component grams + macros that get summed per-serving).
 *
 *   template: { name, meal?, items: [{ product_name, grams, kcal,
 *               protein_g, carbs_g, fat_g }] }
 *   opts:     { lang }
 *
 * Returns '' when items is empty.
 */
export function formatTemplateShare(template, opts = {}) {
  if (!template || !Array.isArray(template.items) || template.items.length === 0) return '';
  const { lang = 'fr' } = opts;
  const isFr = lang !== 'en';
  const r = (n) => Math.round(Number(n) || 0);
  const sum = (key) => template.items.reduce((acc, it) => acc + (Number(it?.[key]) || 0), 0);
  const kcal = r(sum('kcal'));
  const prot = r(sum('protein_g'));
  const carb = r(sum('carbs_g'));
  const fat  = r(sum('fat_g'));
  const lines = [];
  // R14.3: same legacy-sentinel handling as formatRecipeShare.
  const tname = template.name && template.name !== 'Sans nom'
    ? template.name
    : (isFr ? 'Sans nom' : 'Untitled');
  lines.push(isFr
    ? `📋 Repas : ${tname}`
    : `📋 Meal: ${tname}`);
  lines.push(isFr
    ? `${kcal} kcal · P ${prot} g · G ${carb} g · L ${fat} g · ${template.items.length} élément(s)`
    : `${kcal} kcal · P ${prot} g · C ${carb} g · F ${fat} g · ${template.items.length} item(s)`);
  lines.push('');
  lines.push(isFr ? 'Composition :' : 'Items:');
  for (const it of template.items) {
    const label = it.product_name || '—';
    const grams = r(it.grams);
    const ck = r(it.kcal);
    lines.push(grams > 0
      ? `• ${label} — ${grams} g · ${ck} kcal`
      : `• ${label} — ${ck} kcal`);
  }
  lines.push('— Scan\'eat');
  return lines.join('\n');
}

/**
 * Gap fix #11 — weekOverWeekDelta: compare this week's rollup to the
 * prior week's rollup and emit a delta summary for the dashboard
 * header chip. Pure; takes two rollup outputs from weeklyRollup.
 *
 *   current: weeklyRollup output for week ending today
 *   prior:   weeklyRollup output for week ending 7 days before today
 *   returns: { kcal: {cur, prev, pct}, protein: {...}, carbs: {...},
 *              fat: {...}, days_logged: {cur, prev} }
 *
 * `pct` is the percent change from prior → current, rounded. null when
 * prior was zero (can't divide).
 */
export function weekOverWeekDelta(current, prior) {
  const safe = (v) => (v && typeof v === 'object') ? v : { avg: {}, days_logged: 0 };
  const a = safe(current);
  const b = safe(prior);
  const pct = (cur, prev) => {
    if (!prev || prev === 0) return null;
    return Math.round(((cur - prev) / prev) * 100);
  };
  const field = (key) => ({
    cur: Math.round(a.avg?.[key] || 0),
    prev: Math.round(b.avg?.[key] || 0),
    pct: pct(a.avg?.[key] || 0, b.avg?.[key] || 0),
  });
  return {
    kcal:     field('kcal'),
    protein:  field('protein_g'),
    carbs:    field('carbs_g'),
    fat:      field('fat_g'),
    days_logged: { cur: a.days_logged || 0, prev: b.days_logged || 0 },
  };
}

/**
 * Gap fix #7 + #12 — topFoods: most-logged product names from the
 * consumption entries. Pure. Used by Quick Add autocomplete to
 * surface the user's personal favourites on an empty query, and by
 * the progress dialog to answer "what do I eat most?".
 *
 *   entries:  [{ product_name, kcal, ... }]
 *   opts.limit: top N (default 5)
 *   opts.sinceDays: only consider entries from the last N days (default
 *     null = all time).
 *
 * Returns [{ name, count, avg_kcal, last_logged_ms }] sorted by count
 * desc then last_logged_ms desc (recency tiebreaker).
 */
export function topFoods(entries, opts = {}) {
  const list = Array.isArray(entries) ? entries : [];
  const { limit = 5, sinceDays = null, now = Date.now() } = opts;
  const cutoff = sinceDays != null ? now - sinceDays * 86_400_000 : 0;
  const byName = new Map();
  for (const e of list) {
    if (!e?.product_name) continue;
    const name = String(e.product_name).trim();
    if (!name || name === '—') continue;
    const ts = Number(e.timestamp) || 0;
    if (cutoff && ts < cutoff) continue;
    const key = name.toLowerCase();
    const row = byName.get(key) ?? { name, count: 0, sum_kcal: 0, last_logged_ms: 0 };
    row.count += 1;
    row.sum_kcal += Number(e.kcal) || 0;
    if (ts > row.last_logged_ms) row.last_logged_ms = ts;
    byName.set(key, row);
  }
  const out = [...byName.values()]
    .map((r) => ({
      name: r.name,
      count: r.count,
      avg_kcal: Math.round(r.sum_kcal / r.count),
      last_logged_ms: r.last_logged_ms,
    }))
    .sort((a, b) => b.count - a.count || b.last_logged_ms - a.last_logged_ms);
  return out.slice(0, Math.max(0, limit));
}

/**
 * R13.1 — filterScanHistory: pure filter for the scan-history list.
 * Same shape the renderRecentScans loop applies inline; testable
 * without a DOM shim.
 *
 *   items: [{ id, name, grade, ... }]
 *   opts:  { query?: string (case-insensitive substring on name),
 *            gradeFilter?: 'A'|'B'|'C'|'D'|'E' }
 */
export function filterScanHistory(items, opts = {}) {
  const list = Array.isArray(items) ? items : [];
  const q = String(opts.query || '').trim().toLowerCase();
  const g = String(opts.gradeFilter || '');
  return list.filter((i) => {
    if (q && !(i?.name || '').toLowerCase().includes(q)) return false;
    if (g && i?.grade !== g) return false;
    return true;
  });
}

/**
 * R13.6 — summarizeScanHistory: counts items by Scan\'eat grade.
 * Powers the recent-scans summary chip ("12 scans · 4A · 3B · 2C
 * · 2D · 1F") so the user sees their distribution at a glance
 * without scrolling the list.
 *
 *   items: [{ grade: 'A+'|'A'|'B'|'C'|'D'|'F' | other }]
 *   returns: { total: number, byGrade: { 'A+', A, B, C, D, F } }
 *
 * Items with unknown / missing grade count toward `total` but not
 * any letter bucket. Grade keys mirror the <select> options in
 * index.html (#history-grade) — A+ / A / B / C / D / F.
 */
export function summarizeScanHistory(items) {
  const list = Array.isArray(items) ? items : [];
  const byGrade = { 'A+': 0, A: 0, B: 0, C: 0, D: 0, F: 0 };
  for (const it of list) {
    const g = it?.grade;
    if (g && Object.prototype.hasOwnProperty.call(byGrade, g)) byGrade[g] += 1;
  }
  return { total: list.length, byGrade };
}

/**
 * formatPairingsShare — turns a matchPairings() hit into a shareable
 * plain-text "recipe card". Used by the pairings section's Share button
 * and by navigator.share on mobile (WhatsApp, Messages, Mail). Pure
 * presenter — takes raw data, no DOM.
 *
 * Shape matches matchPairings() output: { name, pairs: [{ b, fr, cooccur }] }.
 * lang defaults to 'fr'. Empty/invalid input → ''.
 */
export function formatPairingsShare(hit, opts = {}) {
  if (!hit || !hit.name || !Array.isArray(hit.pairs) || hit.pairs.length === 0) return '';
  const { lang = 'fr' } = opts;
  const isFr = lang !== 'en';
  const header = isFr
    ? `🍳 Accords pour "${hit.name}"`
    : `🍳 Pairings for "${hit.name}"`;
  const sub = isFr ? 'Associations les plus fréquentes :' : 'Most common matches:';
  const lines = [header, '', sub];
  for (const p of hit.pairs.slice(0, 6)) {
    const label = p.fr ?? p.b.replace(/_/g, ' ');
    const tail = Number.isFinite(p.cooccur)
      ? (isFr ? ` (partagé dans ${p.cooccur} recettes)` : ` (shared in ${p.cooccur} recipes)`)
      : '';
    lines.push(`• ${label}${tail}`);
  }
  lines.push('');
  lines.push(isFr
    ? `Idée : commence avec ${hit.name}, ajoute 2–3 éléments ci-dessus, assaisonne.`
    : `Idea: start with ${hit.name}, add 2–3 items above, season to taste.`);
  lines.push(isFr
    ? '— Scan\'eat (corpus Ahn et al. 2011)'
    : '— Scan\'eat (Ahn et al. 2011 recipe corpus)');
  return lines.join('\n');
}

/**
 * State of an intermittent-fasting window given its start time, the current
 * time, and a target duration in hours (default 16 for a classic 16:8).
 *
 * Pure — takes milliseconds instead of Date objects so tests don't have to
 * mock the clock.
 *
 * Returns:
 *   elapsed_ms   — how long since start
 *   remaining_ms — target - elapsed, floored at 0
 *   pct          — elapsed / target * 100, clamped to 0–100
 *   complete     — true when pct ≥ 100 (user can eat)
 *   overrun_ms   — if complete, how long past target (for a "fasted 16h30m" message)
 *   label        — "6:30 / 16:00" style elapsed/target string
 */
export function fastingStatus(startMs, nowMs, targetHours = 16) {
  const targetMs = Math.max(1, targetHours * 3_600_000);
  const elapsed = Math.max(0, nowMs - startMs);
  const remaining = Math.max(0, targetMs - elapsed);
  const pct = Math.max(0, Math.min(100, (elapsed / targetMs) * 100));
  const complete = elapsed >= targetMs;
  const overrun = complete ? elapsed - targetMs : 0;

  const fmt = (ms) => {
    const totalMin = Math.floor(ms / 60_000);
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    return `${h}:${String(m).padStart(2, '0')}`;
  };

  return {
    elapsed_ms: elapsed,
    remaining_ms: remaining,
    pct,
    complete,
    overrun_ms: overrun,
    label: `${fmt(elapsed)} / ${targetHours}:00`,
  };
}

/**
 * Build the SVG path `d` attribute for a line chart of the given values,
 * plus the Y-axis min/max used for scaling. Pure — no DOM touched.
 *
 * Coordinates are normalized to a `[0, width] × [0, height]` viewBox so the
 * caller can size the chart with CSS.
 *
 *   values:  array of numbers (may include null/undefined for gaps)
 *   width:   logical chart width (default 300)
 *   height:  logical chart height (default 100)
 *   padding: inner padding so points don't clip the edges
 *
 * Returns:
 *   path_d:  M/L commands building a polyline; `M` restarts after null gaps
 *   min/max: the y-range used, handy for rendering axis labels
 *   points:  array of {x, y, value} for plotted points (null values skipped)
 */
export function buildLineChartPath(values, opts = {}) {
  const width = opts.width ?? 300;
  const height = opts.height ?? 100;
  const padding = opts.padding ?? 6;
  const cleaned = (values ?? []).map((v) =>
    typeof v === 'number' && Number.isFinite(v) ? v : null,
  );
  const numeric = cleaned.filter((v) => v !== null);
  if (numeric.length === 0) {
    return { path_d: '', min: 0, max: 0, points: [] };
  }
  const min = Math.min(...numeric);
  const max = Math.max(...numeric);
  const range = max - min || 1; // avoid div-by-zero when all values equal
  const innerW = width - padding * 2;
  const innerH = height - padding * 2;
  const stepX = cleaned.length > 1 ? innerW / (cleaned.length - 1) : 0;

  const points = [];
  let d = '';
  let started = false;
  for (let i = 0; i < cleaned.length; i++) {
    const v = cleaned[i];
    if (v === null) { started = false; continue; }
    const x = padding + stepX * i;
    // SVG y grows downward; invert so higher values sit higher.
    const y = padding + innerH - ((v - min) / range) * innerH;
    points.push({ x, y, value: v });
    d += (started ? ' L ' : ' M ') + x.toFixed(1) + ' ' + y.toFixed(1);
    started = true;
  }
  return { path_d: d.trim(), min, max, points };
}

/**
 * Quick sharpness heuristic. Given a flat luminance array (0–255) and its
 * width, compute an approximation of the variance of a Laplacian filter —
 * a classic "is this image sharp?" metric. High variance = sharp (edges
 * present). Low variance = blurry / low-contrast (wall, finger on lens,
 * out-of-focus).
 *
 * Pure — takes a pre-extracted luminance buffer rather than a Canvas /
 * ImageData, so it runs in Node tests without a DOM.
 *
 * The caller will usually downsample the photo to ~64×64 before calling
 * this. Heuristic thresholds we've observed empirically:
 *
 *   variance < 40   → very likely blurry / blank
 *   40 ≤ v < 120    → borderline; warn but allow
 *   v ≥ 120         → sharp enough for OCR
 */
export function laplacianVariance(luma, width) {
  if (!luma || luma.length === 0 || !width) return 0;
  const height = Math.floor(luma.length / width);
  if (height < 3) return 0;

  let sum = 0;
  let sumSq = 0;
  let count = 0;
  // 3×3 discrete Laplacian: center * 4 − (top + bottom + left + right)
  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const i = y * width + x;
      const lap =
        4 * luma[i] -
        luma[i - width] - luma[i + width] - luma[i - 1] - luma[i + 1];
      sum += lap;
      sumSq += lap * lap;
      count++;
    }
  }
  if (count === 0) return 0;
  const mean = sum / count;
  const variance = sumSq / count - mean * mean;
  return Math.max(0, variance);
}

export function sharpnessVerdict(variance) {
  if (variance < 40) return 'blurry';
  if (variance < 120) return 'borderline';
  return 'sharp';
}

/**
 * Build a CSV string of daily totals from a list of consumption entries.
 * Pure — no file I/O. Caller downloads the returned string as a blob.
 *
 * One row per (date, meal) cell? No — one row per DATE with summed macros
 * and entry count. Excel-friendly: UTF-8 BOM prefix, quoted fields,
 * CRLF line terminators, comma separator. The BOM ensures Excel picks
 * UTF-8 instead of guessing CP1252 and butchering accented text.
 *
 * Column order is fixed + documented in the header row, so a user can
 * pivot / chart without further massaging.
 */
export function entriesToDailyCSV(entries) {
  const byDate = new Map();
  // Fix #8 — full micronutrient panel in the CSV. The column set
  // matches what consumption entries actually carry after R34's
  // expansion; new fields are appended so existing downstream
  // importers that parse by header name keep working.
  const EXTRA_KEYS = [
    'fiber_g', 'iron_mg', 'calcium_mg', 'magnesium_mg', 'potassium_mg',
    'zinc_mg', 'sodium_mg', 'vit_a_ug', 'vit_c_mg', 'vit_d_ug',
    'vit_e_mg', 'vit_k_ug', 'b1_mg', 'b2_mg', 'b3_mg', 'b6_mg',
    'b9_ug', 'b12_ug', 'polyunsaturated_fat_g', 'monounsaturated_fat_g',
    'omega_3_g', 'omega_6_g', 'cholesterol_mg',
  ];
  for (const e of entries ?? []) {
    if (!e?.date) continue;
    const key = e.date;
    const row = byDate.get(key) ?? {
      date: key, entries: 0, kcal: 0, carbs_g: 0, protein_g: 0,
      fat_g: 0, sat_fat_g: 0, sugars_g: 0, salt_g: 0,
    };
    for (const k of EXTRA_KEYS) if (row[k] == null) row[k] = 0;
    row.entries += 1;
    row.kcal      += Number(e.kcal) || 0;
    row.carbs_g   += Number(e.carbs_g) || 0;
    row.protein_g += Number(e.protein_g) || 0;
    row.fat_g     += Number(e.fat_g) || 0;
    row.sat_fat_g += Number(e.sat_fat_g) || 0;
    row.sugars_g  += Number(e.sugars_g) || 0;
    row.salt_g    += Number(e.salt_g) || 0;
    for (const k of EXTRA_KEYS) row[k] += Number(e[k]) || 0;
    byDate.set(key, row);
  }
  const rows = Array.from(byDate.values()).sort((a, b) => a.date.localeCompare(b.date));
  const header = [
    'date', 'entries', 'kcal', 'carbs_g', 'protein_g',
    'fat_g', 'sat_fat_g', 'sugars_g', 'salt_g',
    ...EXTRA_KEYS,
  ];
  const round1 = (v) => Math.round(v * 10) / 10;
  const round3 = (v) => Math.round(v * 1000) / 1000;
  const fmt = (r) => [
    r.date, r.entries, round1(r.kcal), round1(r.carbs_g), round1(r.protein_g),
    round1(r.fat_g), round1(r.sat_fat_g), round1(r.sugars_g), round3(r.salt_g),
    ...EXTRA_KEYS.map((k) => round1(r[k])),
  ];
  // CSV quoting: fields don't contain commas or quotes given our data, but
  // wrap in quotes anyway for Excel safety.
  const q = (v) => `"${String(v).replace(/"/g, '""')}"`;
  const body = rows.map((r) => fmt(r).map(q).join(','));
  const bom = '﻿'; // UTF-8 BOM for Excel
  return bom + [header.map(q).join(','), ...body].join('\r\n') + '\r\n';
}

/**
 * Compute the next future-Date timestamp for a given HH:MM local string,
 * relative to `now`. If today's time already passed, jumps to tomorrow.
 * Pure — takes numeric `now` so tests don't fight Date.now().
 *
 *   nextOccurrenceMs('07:30', 0)           → 07:30 of the epoch day
 *   nextOccurrenceMs('07:30', hour(10))    → 07:30 of the next day
 *
 * Returns a ms timestamp or null if the input is malformed.
 */
export function nextOccurrenceMs(hhmm, nowMs) {
  if (typeof hhmm !== 'string') return null;
  // Strict on hour (0-23): a "25:00" input is almost certainly user error
  // and we don't want to silently clamp it to 23:00 — that's surprising.
  // Loose on minutes: "23:75" parses + clamps to 23:59 since the typo
  // intent is clearer (you can guess they meant 23:59).
  const m = hhmm.match(/^([01]?\d|2[0-3]):(\d{2})$/);
  if (!m) return null;
  const hh = Number(m[1]);
  const mm = Math.max(0, Math.min(59, Number(m[2])));
  const now = new Date(nowMs);
  const target = new Date(now);
  target.setHours(hh, mm, 0, 0);
  if (target.getTime() <= now.getTime()) {
    target.setDate(target.getDate() + 1);
  }
  return target.getTime();
}

/**
 * Export consumption entries in a health-app-friendly JSON shape. One
 * record per MEAL (not per day, because Apple Health + Google Fit both
 * expect per-intake timestamps). Field names align with Apple Health's
 * Dietary Energy + macro type identifiers.
 *
 * Consumable directly by:
 *   - iOS Shortcuts with the "Log Health Sample" action
 *   - "Health Auto Export" and similar bridge apps on iOS / Android
 *
 * Can NOT be imported directly by Apple Health / Google Fit without a
 * middleware app — a PWA has no access to those native stores.
 */
export function entriesToHealthJSON(entries) {
  const out = (entries ?? []).map((e) => ({
    timestamp: e.timestamp
      ? new Date(Number(e.timestamp)).toISOString()
      : `${e.date}T12:00:00.000Z`,
    name: e.product_name || '(—)',
    meal: e.meal || 'snack',
    grams: Number(e.grams) || 0,
    // Apple Health identifiers
    dietaryEnergy_kcal: Number(e.kcal) || 0,
    dietaryCarbohydrates_g: Number(e.carbs_g) || 0,
    dietaryProtein_g: Number(e.protein_g) || 0,
    dietaryFatTotal_g: Number(e.fat_g) || 0,
    dietaryFatSaturated_g: Number(e.sat_fat_g) || 0,
    dietarySugar_g: Number(e.sugars_g) || 0,
    dietarySodium_mg: Math.round((Number(e.salt_g) || 0) * 400), // salt → sodium ≈ × 0.4
  }));
  return {
    source: 'scann-eat',
    exported_at: new Date().toISOString(),
    schema: 'apple-health-dietary-v1',
    entries: out,
  };
}

export function logStreakDays(entries, todayIso) {
  if (!entries || entries.length === 0) return 0;
  const days = new Set(entries.map((e) => e.date));
  const ymd = (d) => d.toISOString().slice(0, 10);
  const start = new Date(todayIso + 'T12:00:00Z'); // noon UTC, avoids DST edge
  let cursor = new Date(start);
  let streak = 0;
  // Allow a 1-day grace: if today is empty but yesterday is logged, still
  // start counting from yesterday.
  if (!days.has(ymd(cursor))) {
    cursor.setUTCDate(cursor.getUTCDate() - 1);
    if (!days.has(ymd(cursor))) return 0;
  }
  while (days.has(ymd(cursor))) {
    streak++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return streak;
}

/**
 * Linear extrapolation from the current weight + weekly slope to a target
 * weight. Pure function — exported for unit tests.
 *
 * Returns one of:
 *   { status: 'insufficient-data' }    — <2 entries or no goal set
 *   { status: 'flat'           }       — slope is 0 kg/week (no trend)
 *   { status: 'wrong-direction' }      — slope takes user AWAY from goal
 *   { status: 'already-reached' }      — current_kg already at / past goal
 *   { status: 'ok', weeks, days, targetISO, kgPerWeek }
 *
 * Kept intentionally simple — it's a display aid, not a medical
 * prediction. Doesn't try to project fat vs lean mass splits or account
 * for metabolic adaptation as weight changes.
 */
export function weightForecast(currentKg, goalKg, weeklySlopeKg, nowMs = Date.now()) {
  const c = Number(currentKg);
  const g = Number(goalKg);
  const s = Number(weeklySlopeKg);
  if (!Number.isFinite(c) || c <= 0) return { status: 'insufficient-data' };
  if (!Number.isFinite(g) || g <= 0) return { status: 'insufficient-data' };
  if (!Number.isFinite(s)) return { status: 'insufficient-data' };
  const delta = g - c; // positive → need to gain; negative → need to lose
  if (Math.abs(delta) < 0.05) return { status: 'already-reached' };
  if (s === 0) return { status: 'flat' };
  // Signs must agree: gaining (delta > 0) requires positive slope,
  // losing (delta < 0) requires negative slope.
  if (Math.sign(delta) !== Math.sign(s)) return { status: 'wrong-direction' };
  const weeks = Math.abs(delta / s);
  const days = Math.round(weeks * 7);
  const target = new Date(nowMs + days * 86_400_000);
  return {
    status: 'ok',
    weeks: Math.round(weeks * 10) / 10,
    days,
    targetISO: target.toISOString().slice(0, 10),
    kgPerWeek: s,
  };
}

// ─────────────────────────────────────────────────────────────────────────
// Close-the-gap nutrient suggestions
// ─────────────────────────────────────────────────────────────────────────
//
// Given today's totals + the user's targets + the FOOD_DB, return up to
// 3 suggestions per under-met "more is better" nutrient. Each suggestion
// is { food_name, grams, contribution } where `grams` is the amount of
// that food needed to close roughly half the day's gap.
//
// Why "half" not "full"? Two reasons:
//   1. The user usually eats more before bed than what closes the gap
//      exactly — leaving headroom is friendlier than implying you must
//      eat exactly 280 g of lentils to hit fiber.
//   2. The estimate becomes useless if it asks for 600 g of broccoli to
//      close a 1000 mg calcium gap — capping by share keeps suggestions
//      realistic.
//
// Only operates on nutrients where MORE is better (protein, fiber,
// micros). Skips negative-nutrient targets (sugar/salt/sat-fat caps).
// Pure — exported for tests; no DOM, no IDB.
//
const GAP_NUTRIENTS = [
  // [totals key, target key, label key, target-share to close, max grams cap]
  // Core (original set)
  ['protein_g', 'protein_g_target', 'protein', 0.5, 300],
  ['fiber_g',   'fiber_g_target',   'fiber',   0.5, 200],
  ['iron_mg',   'iron_mg_target',   'iron',    0.5, 200],
  ['calcium_mg','calcium_mg_target','calcium', 0.5, 300],
  ['vit_d_ug',  'vit_d_ug_target',  'vit_d',   0.5, 200],
  ['b12_ug',    'b12_ug_target',    'b12',     0.5, 200],
  // Fix #10 — full micronutrient coverage. Added in this pass so a
  // user short on magnesium / potassium / zinc / vitamin C also
  // sees dense-food suggestions. Each row only fires when the
  // user's profile actually has a target for the nutrient (set by
  // dailyTargets at profile.js line 249+).
  ['magnesium_mg','magnesium_mg_target','magnesium', 0.5, 200],
  ['potassium_mg','potassium_mg_target','potassium', 0.5, 400],
  ['zinc_mg',     'zinc_mg_target',     'zinc',      0.5, 200],
  ['vit_a_ug',    'vit_a_ug_target',    'vit_a',     0.5, 200],
  ['vit_c_mg',    'vit_c_mg_target',    'vit_c',     0.5, 400],
  ['vit_e_mg',    'vit_e_mg_target',    'vit_e',     0.5, 200],
  ['vit_k_ug',    'vit_k_ug_target',    'vit_k',     0.5, 300],
  ['b1_mg',       'b1_mg_target',       'b1',        0.5, 200],
  ['b2_mg',       'b2_mg_target',       'b2',        0.5, 200],
  ['b3_mg',       'b3_mg_target',       'b3',        0.5, 200],
  ['b6_mg',       'b6_mg_target',       'b6',        0.5, 200],
  ['b9_ug',       'b9_ug_target',       'b9',        0.5, 300],
  ['omega_3_g',   'omega_3_g_target',   'omega_3',   0.5, 200],
];

export function closeTheGap(totals, targets, foodDB) {
  const out = [];
  if (!totals || !targets || !Array.isArray(foodDB) || foodDB.length === 0) return out;
  for (const [valKey, tgtKey, label, share, gramsCap] of GAP_NUTRIENTS) {
    const got = Number(totals[valKey]) || 0;
    const tgt = Number(targets[tgtKey]) || 0;
    if (tgt <= 0) continue;
    const deficit = tgt - got;
    if (deficit <= 0) continue;
    const need = deficit * share; // close roughly half the gap

    // Score by per-100 g density of this nutrient. Skip foods that
    // don't carry the nutrient at all so we don't suggest meaningless
    // items. Sort by density desc — most-concentrated food first, so
    // a 20 g handful of almonds outranks 220 g of lentils for the same
    // contribution. The user wants the smallest reasonable portion.
    const ranked = [];
    for (const f of foodDB) {
      const density = Number(f[valKey]) || 0;
      if (density <= 0) continue;
      const grams = Math.round((need / density) * 100);
      if (grams <= 0 || grams > gramsCap) continue;
      const contribution = Math.round(density * (grams / 100) * 10) / 10;
      ranked.push({ food: f, grams, contribution, density });
    }
    ranked.sort((a, b) => b.density - a.density);

    if (ranked.length === 0) continue;
    out.push({
      nutrient: label,
      key: valKey,
      target_key: tgtKey,
      deficit: Math.round(deficit * 10) / 10,
      suggestions: ranked.slice(0, 3).map((r) => ({
        name: r.food.name,
        grams: r.grams,
        contribution: r.contribution,
      })),
    });
  }
  return out;
}
