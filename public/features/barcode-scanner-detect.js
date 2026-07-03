/**
 * Barcode detection from a still image/file — web BarcodeDetector API or
 * native MLKit path. Consumed externally by scanner.js via the app.js deps
 * object (deps.getBarcodeDetector). ADR-0004 feature-folder pattern,
 * extracted per the app.js decomposition plan (Phase 3).
 */
import { nativeDetectBarcodeFromBase64 } from '../native-bridge.js';

const isCapacitor = !!globalThis.Capacitor?.isNativePlatform?.();

let barcodeDetector = null;
function getBarcodeDetector() {
  if (barcodeDetector !== null) return barcodeDetector;
  if (!('BarcodeDetector' in window)) { barcodeDetector = false; return false; }
  try {
    barcodeDetector = new BarcodeDetector({
      formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128'],
    });
  } catch { barcodeDetector = false; }
  return barcodeDetector;
}

async function detectBarcodeFromFile(file) {
  // Native path: use MLKit for higher accuracy barcode detection from image.
  if (isCapacitor) {
    const b64 = await new Promise((res) => {
      const r = new FileReader();
      r.onload = () => res(r.result.split(',')[1] ?? null);
      r.onerror = () => res(null);
      r.readAsDataURL(file);
    });
    if (b64) {
      const code = await nativeDetectBarcodeFromBase64(b64);
      if (code) return code;
    }
    // Fall through to web detector if MLKit unavailable.
  }
  const detector = getBarcodeDetector();
  if (!detector) return null;
  try {
    const bitmap = await createImageBitmap(file);
    const codes = await detector.detect(bitmap);
    bitmap.close?.();
    for (const c of codes) {
      const d = (c.rawValue || '').replace(/\D/g, '');
      if (d.length === 8 || d.length === 12 || d.length === 13) return d;
    }
  } catch { /* ignore */ }
  return null;
}

export { getBarcodeDetector, detectBarcodeFromFile };
