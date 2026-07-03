/**
 * Activity / exercise log.
 *
 * Burned kcal is estimated with the MET equation (Ainsworth et al.,
 * Compendium of Physical Activities 2011):
 *
 *   kcal = MET × weight_kg × hours
 *
 * The default MET per activity type matches the Compendium for typical
 * moderate intensity. Users can override the kcal value directly; the
 * helper is advisory, not prescriptive.
 *
 * Net intake on the dashboard is `consumed_kcal − burned_kcal`, so a 60-min
 * brisk walk at 70 kg offsets ~305 kcal from the day's target.
 *
 * Data model: ActivityEntry = {
 *   id, date (YYYY-MM-DD), timestamp, type, minutes, kcal_burned, note?
 * }
 *
 * The IDB store is called 'activity' and is indexed by date.
 */

const DB_NAME = 'scanneat';
const DB_VERSION = 6;
const STORE = 'activity';

// MET values — Ainsworth 2011. Round numbers picked from the "general"
// row of each activity category. Intentionally conservative for casual
// pace so the UI doesn't overstate burn.
export const MET_TABLE = {
  walking_brisk: 4.3,
  running: 9.8,
  cycling: 7.0,
  swimming: 7.0,
  strength: 5.0,
  yoga: 2.8,
  hiit: 8.0,
  other: 4.0,
};

export const ACTIVITY_TYPES = Object.keys(MET_TABLE);

/**
 * MET × weight_kg × hours = kcal burned.
 * Returns 0 for missing / invalid inputs rather than throwing — the UI
 * needs something to display even for a partial profile.
 */
export function estimateKcalBurned(type, minutes, weightKg) {
  const m = Number(minutes);
  const w = Number(weightKg);
  const met = MET_TABLE[type] ?? MET_TABLE.other;
  if (!Number.isFinite(m) || m <= 0) return 0;
  if (!Number.isFinite(w) || w <= 0) return 0;
  return Math.round(met * w * (m / 60));
}

export { localDateISO as todayISO } from '../core/dateutil.js';

/**
 * Build an entry object. Split out so tests don't need IDB.
 * `kcalOverride` wins over the MET estimate when provided and > 0.
 */
export function buildActivityEntry({ type, minutes, weightKg, kcalOverride, note }, now = Date.now()) {
  const t = ACTIVITY_TYPES.includes(type) ? type : 'other';
  const min = Math.max(0, Math.round(Number(minutes) || 0));
  const override = Number(kcalOverride);
  const kcal = (Number.isFinite(override) && override > 0)
    ? Math.round(override)
    : estimateKcalBurned(t, min, weightKg);
  return {
    id: (globalThis.crypto?.randomUUID?.() ?? `a${now}${Math.random().toString(36).slice(2)}`),
    date: new Date(now).toISOString().slice(0, 10),
    timestamp: now,
    type: t,
    minutes: min,
    kcal_burned: kcal,
    note: (note ?? '').trim() || undefined,
  };
}

/** Pure sum — exported for tests. */
export function sumBurned(entries) {
  let kcal = 0;
  let minutes = 0;
  for (const e of entries) {
    kcal += e.kcal_burned || 0;
    minutes += e.minutes || 0;
  }
  return { kcal: Math.round(kcal), minutes: Math.round(minutes), count: entries.length };
}

// ---------- IDB glue ----------

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      // Recreate all stores defensively — see the equivalent guard in the
      // sibling data modules. Load order is not guaranteed so every module
      // creates every store it knows about.
      if (!db.objectStoreNames.contains('pending_scans')) {
        db.createObjectStore('pending_scans', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('history')) {
        const s = db.createObjectStore('history', { keyPath: 'id' });
        s.createIndex('created', 'createdAt');
      }
      if (!db.objectStoreNames.contains('consumption')) {
        const s = db.createObjectStore('consumption', { keyPath: 'id' });
        s.createIndex('date', 'date');
      }
      if (!db.objectStoreNames.contains('weight')) {
        const s = db.createObjectStore('weight', { keyPath: 'id' });
        s.createIndex('date', 'date', { unique: true });
      }
      if (!db.objectStoreNames.contains('meal_templates')) {
        db.createObjectStore('meal_templates', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('recipes')) {
        db.createObjectStore('recipes', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('activity')) {
        const s = db.createObjectStore('activity', { keyPath: 'id' });
        s.createIndex('date', 'date');
      }
    };
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => { try { db.close(); } catch { /* ignore */ } };
      resolve(db);
    };
    req.onerror = () => reject(req.error);
    req.onblocked = () => { /* older tab; eventual close() clears it */ };
  });
}

export async function logActivity(entry) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(entry);
    tx.oncomplete = () => { db.close(); resolve(entry); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function listActivityByDate(date = todayISO()) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const idx = tx.objectStore(STORE).index('date');
    const req = idx.getAll(IDBKeyRange.only(date));
    req.onsuccess = () => { db.close(); resolve(req.result || []); };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

export async function listAllActivity() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => { db.close(); resolve(req.result || []); };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

export async function deleteActivity(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function dailyBurned(date = todayISO()) {
  const entries = await listActivityByDate(date);
  return sumBurned(entries);
}
