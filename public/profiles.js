/**
 * Named user-profile snapshots — built on top of backup.js.
 *
 * One device can host several people (self + partner + child). Each profile
 * is a full state snapshot: all IDB stores + the scanneat.* localStorage
 * slice. Switching profiles wipes the current live data and restores the
 * selected snapshot's data. The NOT-active profile(s) stay serialised in
 * localStorage.
 *
 * We piggy-back on buildBackup / restoreBackup to avoid reinventing the
 * serialisation. The snapshot shape is the backup shape.
 */

import { buildBackup, restoreBackup } from './backup.js';

const LS_PROFILES_LIST = 'scanneat.profiles.list';
const LS_PROFILES_ACTIVE = 'scanneat.profiles.active';
const LS_PROFILE_PREFIX = 'scanneat.profiles.snap.';

function readList() {
  try { return JSON.parse(localStorage.getItem(LS_PROFILES_LIST) || '[]'); }
  catch { return []; }
}
function writeList(names) {
  localStorage.setItem(LS_PROFILES_LIST, JSON.stringify(names));
}
function snapKey(name) { return LS_PROFILE_PREFIX + name; }

export function listProfiles() { return readList(); }
export function activeProfile() { return localStorage.getItem(LS_PROFILES_ACTIVE) || ''; }

/**
 * Capture the current state under the given profile name. If the profile
 * exists, overwrites it. Returns the saved snapshot (useful for tests).
 */
export async function saveProfile(name) {
  const trimmed = String(name || '').trim();
  if (!trimmed) throw new Error('Profile name required');
  const snap = await buildBackup();
  localStorage.setItem(snapKey(trimmed), JSON.stringify(snap));
  const list = readList();
  if (!list.includes(trimmed)) { list.push(trimmed); writeList(list); }
  localStorage.setItem(LS_PROFILES_ACTIVE, trimmed);
  return snap;
}

/**
 * Switch to the named profile: capture the current state back to the active
 * profile's slot first (so nothing is lost), then wipe-restore the target.
 */
export async function switchProfile(name) {
  const trimmed = String(name || '').trim();
  if (!trimmed) throw new Error('Profile name required');
  const raw = localStorage.getItem(snapKey(trimmed));
  if (!raw) throw new Error(`No saved profile: ${trimmed}`);

  // Save current state back to the outgoing profile if one is active.
  const outgoing = activeProfile();
  if (outgoing && outgoing !== trimmed) {
    try {
      const current = await buildBackup();
      localStorage.setItem(snapKey(outgoing), JSON.stringify(current));
    } catch { /* best-effort */ }
  }

  const target = JSON.parse(raw);
  await restoreBackup(target, { wipe: true });
  localStorage.setItem(LS_PROFILES_ACTIVE, trimmed);
}

export function deleteProfile(name) {
  const trimmed = String(name || '').trim();
  if (!trimmed) return;
  localStorage.removeItem(snapKey(trimmed));
  const list = readList().filter((n) => n !== trimmed);
  writeList(list);
  if (activeProfile() === trimmed) localStorage.removeItem(LS_PROFILES_ACTIVE);
}
