/**
 * Live barcode scanner — camera + BarcodeDetector loop (web) or MLKit (native).
 *
 * ADR-0004 feature-folder pattern. openCameraScanner()/closeCameraScanner()
 * are exported so app.js can wire them to buttons and dialog-close events
 * without owning the lifecycle or camera stream.
 *
 * Deps shape:
 *   { t, errorEl, show, toast,
 *     getBarcodeDetector, scanImage, addBarcodeOnly,
 *     nativeScanBarcode }          ← added; null-safe on web
 *
 * DOM elements (#camera-dialog, #camera-video, #camera-status,
 * #camera-torch) are looked up by id at open time.
 *
 * Native path (isCapacitor):
 *   - Calls nativeScanBarcode() from native-bridge.js which opens the
 *     full-screen MLKit scanner UI directly. No WebView camera stream,
 *     no BarcodeDetector polyfill, no 4 Hz polling loop.
 *   - closeCameraScanner() is a no-op on the native path (MLKit owns
 *     its own UI lifecycle).
 *   - The #camera-dialog / torch DOM is only used on the web path.
 */

import { nativeScanBarcode } from '../native-bridge.js';

let deps = null;
let cameraStream = null;
let cameraLoopHandle = null;

function $(id) { return document.getElementById(id); }
function hide(el) { if (el) el.setAttribute('hidden', ''); }

export async function openCameraScanner() {
  const { t, errorEl, show, getBarcodeDetector, scanImage, addBarcodeOnly } = deps;

  // ── Native path: delegate entirely to MLKit ──────────────────────────────
  if (deps.isNative) {
    const barcode = await nativeScanBarcode();
    if (!barcode) return; // user cancelled or error — nothing to do
    if (!document.body.classList.contains('reduce-motion')) {
      try { navigator.vibrate?.(50); } catch { /* not supported */ }
    }
    addBarcodeOnly(barcode);
    await scanImage();
    return;
  }

  // ── Web path: BarcodeDetector + getUserMedia loop ─────────────────────────
  const cameraDialog = $('camera-dialog');
  const cameraVideo = $('camera-video');
  const cameraStatus = $('camera-status');
  if (!getBarcodeDetector()) {
    errorEl.textContent = t('cameraUnsupported'); show(errorEl); return;
  }
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } }, audio: false,
    });
  } catch {
    errorEl.textContent = t('cameraDenied'); show(errorEl); return;
  }
  cameraVideo.srcObject = cameraStream;
  cameraDialog.showModal();
  cameraStatus.textContent = t('cameraReady');

  // Torch button appears only when the active video track reports
  // capabilities.torch — Chrome Android exposes it on most phones, iOS
  // Safari never does. Hidden = feature not available.
  const torchBtn = $('camera-torch');
  const track = cameraStream.getVideoTracks()[0];
  const caps = track?.getCapabilities?.() || {};
  if (torchBtn && caps.torch) {
    torchBtn.dataset.on = '0';
    torchBtn.setAttribute('aria-pressed', 'false');
    show(torchBtn);
  } else if (torchBtn) {
    hide(torchBtn);
  }

  const detector = getBarcodeDetector();
  const scan = async () => {
    if (!cameraDialog.open) return;
    // R21.1: pause scan loop when the document is hidden — the
    // camera stream suspends anyway, but the detect() call still
    // burns CPU at 4 Hz without any visible benefit. Visibilitychange
    // listener below re-triggers the loop when the tab returns.
    if (document.hidden) {
      cameraLoopHandle = null;
      return;
    }
    try {
      const codes = await detector.detect(cameraVideo);
      for (const c of codes) {
        const d = (c.rawValue || '').replace(/\D/g, '');
        if (d.length === 8 || d.length === 12 || d.length === 13) {
          // R21.2: haptic blip gated by reduce-motion preference —
          // users who opt out of animated UI typically don't want
          // 50 ms vibrations either.
          if (!document.body.classList.contains('reduce-motion')) {
            try { navigator.vibrate?.(50); } catch { /* not supported */ }
          }
          closeCameraScanner();
          addBarcodeOnly(d);
          await scanImage();
          return;
        }
      }
    } catch { /* ignore detection errors */ }
    cameraLoopHandle = setTimeout(scan, 250);
  };
  // Kick the loop again when the user returns to the tab.
  const onVis = () => {
    if (!document.hidden && cameraDialog.open && !cameraLoopHandle) scan();
  };
  document.addEventListener('visibilitychange', onVis);
  // Clean up the visibility listener when the dialog closes.
  cameraDialog.addEventListener('close', () => {
    document.removeEventListener('visibilitychange', onVis);
  }, { once: true });
  scan();
}

export function closeCameraScanner() {
  // Native path: MLKit owns its own UI — nothing to tear down here.
  if (deps?.isNative) return;

  const cameraDialog = $('camera-dialog');
  const cameraVideo = $('camera-video');
  if (cameraLoopHandle) clearTimeout(cameraLoopHandle);
  cameraLoopHandle = null;
  if (cameraStream) {
    // Make sure torch is off before releasing the track, so the LED doesn't
    // linger when the user closes the scanner without capturing.
    try {
      const track = cameraStream.getVideoTracks()[0];
      track?.applyConstraints?.({ advanced: [{ torch: false }] });
    } catch { /* ignore */ }
    cameraStream.getTracks().forEach((t) => t.stop()); cameraStream = null;
  }
  const torchBtn = $('camera-torch');
  if (torchBtn) { torchBtn.dataset.on = '0'; torchBtn.setAttribute('aria-pressed', 'false'); }
  if (cameraVideo) cameraVideo.srcObject = null;
  if (cameraDialog?.open) cameraDialog.close();
}

export function initScanner(injected) {
  deps = injected;
  // Torch toggle is only relevant on the web path (MLKit handles torch itself).
  if (deps.isNative) return;
  const torchBtn = $('camera-torch');
  torchBtn?.addEventListener('click', async () => {
    if (!cameraStream) return;
    const track = cameraStream.getVideoTracks()[0];
    if (!track?.applyConstraints) return;
    const on = torchBtn.dataset.on !== '1';
    try {
      await track.applyConstraints({ advanced: [{ torch: on }] });
      torchBtn.dataset.on = on ? '1' : '0';
      torchBtn.setAttribute('aria-pressed', String(on));
    } catch { /* torch failed; leave state unchanged */ }
  });
}
