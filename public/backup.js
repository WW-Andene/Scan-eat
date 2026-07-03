/**
 * Full backup + restore. One JSON file that captures every piece of user
 * state the app owns — so a user moving to a new device, clearing their
 * browser data, or just making a precautionary copy loses nothing.
 *
 * Sources of truth covered:
 *   localStorage — settings, profile, preferences, dismissed banners,
 *                  hydration per-date counters, custom foods
 *   IndexedDB    — history, consumption, weight, meal_templates, recipes,
 *                  activity
 *
 * NOT covered:
 *   pending_scans — transient offline queue; not worth saving across devices
 */

import { listScans, saveScan, clearScans } from './data/scan-history.js';
import {
  listAllEntries, putEntry, listByDate, deleteEntry, todayISO,
} from './data/consumption.js';
import { listWeight, logWeight, deleteWeight } from './data/weight-log.js';
import { listTemplates, saveTemplate, deleteTemplate } from './data/meal-templates.js';
import { listRecipes, saveRecipe, deleteRecipe } from './data/recipes.js';
import { listAllActivity, logActivity, deleteActivity } from './data/activity.js';

// v2 adds the `activity` array. v1 readers just ignore it; we still read
// old v1 backups by defaulting to [].
const BACKUP_VERSION = 2;
const LS_PREFIX = 'scanneat.';

function readAllLocalStorage() {
  const out = {};
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k && k.startsWith(LS_PREFIX)) out[k] = localStorage.getItem(k);
  }
  return out;
}

function writeAllLocalStorage(map) {
  for (const [k, v] of Object.entries(map ?? {})) {
    if (!k.startsWith(LS_PREFIX)) continue; // guard against random keys
    if (typeof v === 'string') localStorage.setItem(k, v);
  }
}

export async function buildBackup() {
  const [history, consumption, weight, templates, recipes, activity] = await Promise.all([
    listScans().catch(() => []),
    listAllEntries().catch(() => []),
    listWeight().catch(() => []),
    listTemplates().catch(() => []),
    listRecipes().catch(() => []),
    listAllActivity().catch(() => []),
  ]);
  return {
    app: 'scann-eat',
    backup_version: BACKUP_VERSION,
    exported_at: new Date().toISOString(),
    localStorage: readAllLocalStorage(),
    history,
    consumption,
    weight,
    templates,
    recipes,
    activity,
  };
}

/**
 * Restore a backup. By default, merges into existing data (keyed IDB writes
 * overwrite existing items with the same id; new items are added). Pass
 * { wipe: true } to clear history before writing — the strongest form.
 *
 * localStorage keys are always overwritten (settings should match the saved
 * snapshot, not merge, because a partial merge would leave inconsistent
 * state).
 */
export async function restoreBackup(backup, opts = {}) {
  if (!backup || typeof backup !== 'object' || backup.app !== 'scann-eat') {
    throw new Error('Not a Scan\'eat backup file.');
  }
  if (Number(backup.backup_version) > BACKUP_VERSION) {
    throw new Error('Backup format is newer than this version of the app.');
  }

  writeAllLocalStorage(backup.localStorage);

  if (opts.wipe) {
    // Full-wipe mode (used for profile switching, where the current user's
    // data shouldn't mix with the loaded profile's).
    await clearScans().catch(() => {});
    try {
      const entries = await listAllEntries();
      for (const e of entries) await deleteEntry(e.id).catch(() => {});
    } catch { /* ignore */ }
    try {
      const weights = await listWeight();
      for (const w of weights) await deleteWeight(w.id).catch(() => {});
    } catch { /* ignore */ }
    try {
      const templates = await listTemplates();
      for (const tpl of templates) await deleteTemplate(tpl.id).catch(() => {});
    } catch { /* ignore */ }
    try {
      const recipes = await listRecipes();
      for (const r of recipes) await deleteRecipe(r.id).catch(() => {});
    } catch { /* ignore */ }
    try {
      const activity = await listAllActivity();
      for (const a of activity) await deleteActivity(a.id).catch(() => {});
    } catch { /* ignore */ }
  }

  for (const rec of backup.history ?? []) {
    try { await saveScan(rec); } catch { /* skip corrupt */ }
  }
  for (const entry of backup.consumption ?? []) {
    try { await putEntry(entry); } catch { /* skip */ }
  }
  for (const w of backup.weight ?? []) {
    try { await logWeight(w.weight_kg, w.notes || '', w.date); } catch { /* skip */ }
  }
  for (const t of backup.templates ?? []) {
    try { await saveTemplate(t); } catch { /* skip */ }
  }
  for (const r of backup.recipes ?? []) {
    try { await saveRecipe(r); } catch { /* skip */ }
  }
  for (const a of backup.activity ?? []) {
    try { await logActivity(a); } catch { /* skip */ }
  }
}
