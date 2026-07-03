/**
 * native-bridge.js
 *
 * Wraps Capacitor Camera and MLKit BarcodeScanning behind a stable async API.
 * Accesses plugins via window.Capacitor.Plugins — the global registry that
 * Capacitor populates at runtime when running inside a native APK. No dynamic
 * imports, no file paths, no npm/cap-sync required on the authoring device.
 *
 * Web builds: all functions return null/false safely (IS_NATIVE is false).
 * Native builds: plugins must be listed in capacitor.config.json and the APK
 * must have been assembled with the plugins present (CI handles this).
 *
 * CameraResultType / CameraSource / BarcodeFormat are plain string/number
 * constants — we inline them here so we never need to import plugin modules.
 */

const IS_NATIVE = !!globalThis.Capacitor?.isNativePlatform?.();

// Inline Capacitor plugin constants (stable across plugin versions).
// Avoids any import that would require cap sync on the authoring device.
const CameraResultType = { Base64: 'base64' };
const CameraSource    = { Camera: 'CAMERA', Photos: 'PHOTOS', Prompt: 'PROMPT' };
// BarcodeFormat string values for @capacitor-mlkit/barcode-scanning v6
const BarcodeFormat = {
  Ean13: 'EAN_13', Ean8: 'EAN_8',
  UpcA: 'UPC_A',  UpcE: 'UPC_E',
  Code128: 'CODE_128',
};
const BARCODE_FORMATS = Object.values(BarcodeFormat);

function getPlugin(name) {
  return globalThis.Capacitor?.Plugins?.[name] ?? null;
}

// ---------------------------------------------------------------------------
// Camera (photo capture)
// ---------------------------------------------------------------------------

/**
 * @param {'camera'|'photos'|'prompt'} source
 * @returns {Promise<{base64: string, format: string}|null>}
 */
export async function nativeTakePhoto(source = 'camera') {
  if (!IS_NATIVE) return null;
  const Camera = getPlugin('Camera');
  if (!Camera) return null;
  try {
    const sourceVal = { camera: CameraSource.Camera, photos: CameraSource.Photos, prompt: CameraSource.Prompt }[source] ?? CameraSource.Camera;
    const photo = await Camera.getPhoto({
      quality: 85,
      allowEditing: false,
      resultType: CameraResultType.Base64,
      source: sourceVal,
      correctOrientation: true,
    });
    return { base64: photo.base64String, format: photo.format ?? 'jpeg' };
  } catch (err) {
    if (err?.message?.toLowerCase().includes('cancel')) return null;
    console.error('[native-bridge] Camera error:', err);
    return null;
  }
}

export function hasNativeCamera() {
  return IS_NATIVE && !!getPlugin('Camera');
}

// ---------------------------------------------------------------------------
// MLKit Barcode Scanning
// ---------------------------------------------------------------------------

export function hasNativeBarcodeScanner() {
  // Synchronous — plugin is registered at app boot, not lazily.
  return IS_NATIVE && !!getPlugin('BarcodeScanner');
}

/**
 * Open the native MLKit full-screen scanner UI.
 * @returns {Promise<string|null>} Barcode value or null (cancelled/error).
 */
export async function nativeScanBarcode() {
  if (!IS_NATIVE) return null;
  const BS = getPlugin('BarcodeScanner');
  if (!BS) return null;
  try {
    // The Google Barcode Scanner module is a separate on-device download
    // (Play Services). On a fresh install, or on a device that hasn't
    // pulled it down yet, BS.scan() can throw/hang instead of prompting.
    // Check first and trigger the install if missing.
    try {
      const avail = await BS.isGoogleBarcodeScannerModuleAvailable();
      if (!avail?.available) {
        await BS.installGoogleBarcodeScannerModule();
      }
    } catch {
      // isGoogleBarcodeScannerModuleAvailable/install are Android-only;
      // ignore failures here (e.g. iOS) and fall through to scan().
    }
    const status = await BS.requestPermissions();
    if (status?.camera !== 'granted' && status?.camera !== 'limited') return null;
    const result = await BS.scan({ formats: BARCODE_FORMATS });
    for (const barcode of result?.barcodes ?? []) {
      const d = (barcode.rawValue || '').replace(/\D/g, '');
      if (d.length === 8 || d.length === 12 || d.length === 13) return d;
    }
    return null;
  } catch (err) {
    if (err?.message?.toLowerCase().includes('cancel')) return null;
    console.error('[native-bridge] MLKit scan error:', err);
    return null;
  }
}

/**
 * Detect a barcode from a base64 image using MLKit (no UI, offline).
 * @param {string} base64 Raw base64, no data-URL prefix.
 * @returns {Promise<string|null>}
 */
export async function nativeDetectBarcodeFromBase64(base64) {
  if (!IS_NATIVE) return null;
  const BS = getPlugin('BarcodeScanner');
  if (!BS) return null;
  try {
    const result = await BS.readBarcodesFromImage({ base64, formats: BARCODE_FORMATS });
    for (const barcode of result?.barcodes ?? []) {
      const d = (barcode.rawValue || '').replace(/\D/g, '');
      if (d.length === 8 || d.length === 12 || d.length === 13) return d;
    }
    return null;
  } catch {
    return null;
  }
}
