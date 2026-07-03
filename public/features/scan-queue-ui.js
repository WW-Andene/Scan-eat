/**
 * Scan queue UI — render the photo/barcode queue strip, add/remove items,
 * build the payload sent to the scoring engine. Operates on the shared
 * `queue` array from state/scan-queue-state.js (H4 — single source of
 * truth; scan-pipeline.js and offline-queue-sync.js import the same
 * instance). ADR-0004 feature-folder pattern, extracted per the app.js
 * decomposition plan (Phase 5).
 *
 * DOM elements (#queue, #scan-btn, #error, #capture-label) are looked up
 * by id directly, matching the existing convention in this cluster — no id
 * renaming in this restructuring (H6).
 */
import { t } from '../core/i18n.js';
import { show, hide, toast } from '../core/dom-helpers.js';
import { queue } from '../state/scan-queue-state.js';
import { detectBarcodeFromFile } from './barcode-scanner-detect.js';
import { compressImage } from './image-compression.js';

const $ = (id) => document.getElementById(id);
const queueEl = $('queue');
const scanBtn = $('scan-btn');
const errorEl = $('error');
const MAX_IMAGES = 4;

function renderQueue() {
  queueEl.textContent = '';
  if (queue.length === 0) queueEl.hidden = true;
  else {
    queueEl.hidden = false;
    for (const item of queue) {
      const wrap = document.createElement('div');
      wrap.className = 'queue-item';
      if (item.barcode) wrap.classList.add('has-barcode');
      const img = document.createElement('img');
      img.src = item.dataUrl; img.alt = '';
      const remove = document.createElement('button');
      remove.type = 'button'; remove.className = 'queue-remove';
      remove.dataset.id = item.id; remove.textContent = '×';
      remove.setAttribute('aria-label', t('removePhoto'));
      wrap.appendChild(img); wrap.appendChild(remove);
      if (item.barcode) {
        const tag = document.createElement('span');
        tag.className = 'queue-barcode'; tag.textContent = `📦 ${item.barcode}`;
        wrap.appendChild(tag);
      }
      queueEl.appendChild(wrap);
    }
  }
  scanBtn.disabled = queue.length === 0;
  const label = $('capture-label');
  if (label) {
    label.textContent =
      queue.length >= MAX_IMAGES ? t('maxPhotos', { n: MAX_IMAGES })
      : queue.length > 0 ? t('addAnotherPhoto')
      : t('addPhoto');
  }
}

async function addFiles(fileList) {
  if (!fileList || fileList.length === 0) return;
  hide(errorEl);
  const files = Array.from(fileList).slice(0, MAX_IMAGES - queue.length);
  let blurryCount = 0;
  let dupCount = 0;
  for (const file of files) {
    try {
      const barcode = await detectBarcodeFromFile(file);
      // Duplicate-barcode guard: if the user snaps the same product's
      // barcode twice, a second identical hit contributes nothing (the
      // server takes only one barcode anyway) and clutters the queue.
      // We keep the first and warn via a toast.
      if (barcode && queue.some((q) => q.barcode === barcode)) {
        dupCount += 1;
        continue;
      }
      const compressed = await compressImage(file);
      if (compressed.sharpness?.verdict === 'blurry' && !barcode) blurryCount++;
      queue.push({ id: crypto.randomUUID(), ...compressed, barcode });
      renderQueue();
    } catch (err) {
      errorEl.textContent = err.message; show(errorEl);
    }
  }
  // Soft warning — don't block the scan (user might know better), just
  // hint that re-shooting might help. Only fires when none of the added
  // frames have a barcode (barcode scans don't need pixel-sharp ingredient
  // text).
  // R16.4: blurry + duplicate are both "this didn't go quite right"
  // conditions — annotate as 'warn' so the stripe colour matches.
  if (blurryCount > 0) {
    toast(t('blurryPhotoWarning'), 'warn');
  }
  if (dupCount > 0) {
    toast(t('duplicateBarcodeSkipped', { n: dupCount }), 'warn');
  }
}

function addBarcodeOnly(barcode) {
  // Duplicate guard for the live scanner path too — lets the user re-
  // aim the camera if they accidentally pointed at the same barcode
  // twice without blocking the UI.
  if (queue.some((q) => q.barcode === barcode)) {
    toast(t('duplicateBarcodeSkipped', { n: 1 }), 'warn');
    return;
  }
  // Inline SVG placeholder for the queue thumbnail when the user arrived
  // via the barcode scanner (no actual photo to show). Palette matches
  // the coral redesign (--panel + --text), not the retired dark-green.
  const placeholder =
    'data:image/svg+xml;utf8,' +
    encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 60">' +
      '<rect width="60" height="60" fill="#1B1B1F"/>' +
      '<text x="30" y="40" text-anchor="middle" fill="#F5F0E8" font-size="28">📦</text>' +
      '</svg>'
    );
  queue.push({
    id: crypto.randomUUID(),
    dataUrl: placeholder,
    base64: '', mime: 'image/jpeg',
    barcode,
  });
  renderQueue();
}

function removeFromQueue(id) {
  const idx = queue.findIndex((q) => q.id === id);
  if (idx >= 0) queue.splice(idx, 1);
  renderQueue();
}
function firstBarcode() { return queue.find((q) => !!q.barcode)?.barcode || null; }

function queuePayload() {
  return queue.filter((q) => q.base64).map((q) => ({ base64: q.base64, mime: q.mime }));
}

export { renderQueue, addFiles, addBarcodeOnly, removeFromQueue, firstBarcode, queuePayload };
