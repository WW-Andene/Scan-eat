/**
 * Scan history store — keeps the last 30 scans in IndexedDB so the user can
 * re-open a product they looked at recently. Each record is lightweight:
 * score / grade / name / thumbnail (first queued image) / audit snapshot.
 */

const DB_NAME = 'scanneat';
// Must stay in lockstep with queue-store.js / consumption.js / weight-log.js /
// meal-templates.js / recipes.js / activity.js. Every upgrade handler
// declares ALL stores defensively.
const DB_VERSION = 6;
const STORE = 'history';
const MAX_ITEMS = 30;

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
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
      // If another tab wants to upgrade, release our connection so it can.
      db.onversionchange = () => { try { db.close(); } catch { /* ignore */ } };
      resolve(db);
    };
    req.onerror = () => reject(req.error);
    req.onblocked = () => { /* older tab is holding — eventual close() clears it */ };
  });
}

function isQuotaError(err) {
  if (!err) return false;
  const name = err.name || '';
  return name === 'QuotaExceededError' || name === 'NS_ERROR_DOM_QUOTA_REACHED';
}

async function putRecord(record) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    const req = tx.objectStore(STORE).put(record);
    req.onerror = () => { db.close(); reject(req.error); };
    tx.oncomplete = () => { db.close(); resolve(record); };
    tx.onerror = () => { db.close(); reject(tx.error); };
    tx.onabort = () => { db.close(); reject(tx.error || new Error('transaction aborted')); };
  });
}

export async function saveScan(record) {
  try {
    await putRecord(record);
  } catch (err) {
    if (!isQuotaError(err)) throw err;
    // Quota hit — drop the thumbnail (biggest payload) and purge the oldest
    // half of the history, then retry once. If it still fails, surface the
    // error so the caller can warn the user instead of silently losing data.
    try {
      const items = await listScans();
      const dropCount = Math.max(1, Math.ceil(items.length / 2));
      const toDrop = items.slice(-dropCount);
      for (const item of toDrop) await deleteScan(item.id);
    } catch { /* best-effort cleanup */ }
    const lean = { ...record, thumbnail: '' };
    await putRecord(lean);
  }

  // Trim to MAX_ITEMS (post-save housekeeping, separate from quota recovery)
  try {
    const items = await listScans();
    if (items.length > MAX_ITEMS) {
      const excess = items.slice(MAX_ITEMS);
      for (const e of excess) await deleteScan(e.id);
    }
  } catch { /* non-fatal */ }
  return record;
}

export async function listScans() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => {
      db.close();
      const all = req.result || [];
      all.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
      resolve(all);
    };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

/**
 * Find the most recent history entry whose snapshot barcode matches.
 * Used by the "barcode cache" path to avoid re-hitting Open Food Facts
 * when the user scans a product they've seen before.
 *
 * Returns the stored record (with the snapshot) or null.
 *
 * Implementation note: with the default MAX_ITEMS of 30, a linear scan
 * is faster than maintaining a barcode index + key path change — no
 * migration cost.
 */
export async function findScanByBarcode(barcode) {
  if (!barcode || typeof barcode !== 'string') return null;
  const normalised = barcode.replace(/\D/g, '');
  if (!normalised) return null;
  const all = await listScans();
  for (const rec of all) {
    const b = rec?.snapshot?.barcode || rec?.snapshot?.product?.barcode;
    if (b && String(b).replace(/\D/g, '') === normalised) return rec;
  }
  return null;
}

export async function deleteScan(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

export async function clearScans() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).clear();
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}
