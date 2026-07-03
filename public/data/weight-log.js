/**
 * Weight tracking store + trend math.
 *
 * One entry per day (newest write wins for same-date entries). Trend computed
 * by simple linear regression over the last N days.
 */

import { localDateISO } from '../core/dateutil.js';

const DB_NAME = 'scanneat';
const DB_VERSION = 6;
const STORE = 'weight';

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => ensureStores(req.result);
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => { try { db.close(); } catch { /* ignore */ } };
      resolve(db);
    };
    req.onerror = () => reject(req.error);
    req.onblocked = () => { /* older tab is holding — eventual close() clears it */ };
  });
}

export function ensureStores(db) {
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
}

const todayISO = localDateISO;

export async function logWeight(kg, notes = '', date = todayISO()) {
  const weight = Number(kg);
  if (!Number.isFinite(weight) || weight <= 0 || weight > 400) {
    throw new Error('Invalid weight');
  }
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    const store = tx.objectStore(STORE);
    // Upsert-by-date: if an entry for this date exists, replace.
    const idx = store.index('date');
    const getReq = idx.get(date);
    getReq.onsuccess = () => {
      const existing = getReq.result;
      const entry = {
        id: existing?.id ?? (globalThis.crypto?.randomUUID?.() ?? `w${Date.now()}${Math.random().toString(36).slice(2)}`),
        date,
        timestamp: Date.now(),
        weight_kg: Math.round(weight * 10) / 10,
        notes: String(notes || ''),
      };
      store.put(entry);
      tx.oncomplete = () => { db.close(); resolve(entry); };
      tx.onerror = () => { db.close(); reject(tx.error); };
    };
    getReq.onerror = () => { db.close(); reject(getReq.error); };
  });
}

export async function listWeight() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => {
      db.close();
      const all = (req.result || []).sort((a, b) => a.date.localeCompare(b.date));
      resolve(all);
    };
    req.onerror = () => { db.close(); reject(req.error); };
  });
}

export async function deleteWeight(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}

/**
 * Simple linear-regression slope over the last N entries, expressed as
 * kg/week. Pure function — exported for unit tests.
 */
export function weeklyTrend(entries) {
  if (!entries || entries.length < 2) return 0;
  const xs = entries.map((e) => new Date(e.date).getTime() / (1000 * 60 * 60 * 24));
  const ys = entries.map((e) => e.weight_kg);
  const x0 = xs[0];
  const xn = xs.map((x) => x - x0);
  const meanX = xn.reduce((a, b) => a + b, 0) / xn.length;
  const meanY = ys.reduce((a, b) => a + b, 0) / ys.length;
  let num = 0, den = 0;
  for (let i = 0; i < xn.length; i++) {
    num += (xn[i] - meanX) * (ys[i] - meanY);
    den += (xn[i] - meanX) ** 2;
  }
  if (den === 0) return 0;
  const slopePerDay = num / den;
  return Math.round(slopePerDay * 7 * 10) / 10;
}

/** Pure helper: summary of entries (current, min, max, delta over N days). */
export function summarize(entries, days = 30) {
  if (!entries || entries.length === 0) return null;
  const sorted = entries.slice().sort((a, b) => a.date.localeCompare(b.date));
  const latest = sorted[sorted.length - 1];
  // Fix #29: local-day cutoff, not UTC. Entries are stamped with
  // localDateISO (R25.1) so the cutoff comparison must be on the
  // same scale — previously drifted by up to a day at tz edges.
  const cutoff = localDateISO(Date.now() - days * 86400000);
  const recent = sorted.filter((e) => e.date >= cutoff);
  const first = recent[0] || sorted[0];
  const delta = Math.round((latest.weight_kg - first.weight_kg) * 10) / 10;
  const min = recent.reduce((m, e) => Math.min(m, e.weight_kg), Infinity);
  const max = recent.reduce((m, e) => Math.max(m, e.weight_kg), -Infinity);
  return {
    latest_kg: latest.weight_kg,
    latest_date: latest.date,
    delta_kg: delta,
    min_kg: min === Infinity ? latest.weight_kg : min,
    max_kg: max === -Infinity ? latest.weight_kg : max,
    count: sorted.length,
    recent_count: recent.length,
    days_window: days,
  };
}
