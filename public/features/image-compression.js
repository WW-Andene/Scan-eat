/**
 * Image loading + client-side JPEG compression for queued scan photos.
 * Consumed externally by recipes-dialog.js via the app.js deps object
 * (deps.compressImage). ADR-0004 feature-folder pattern, extracted per the
 * app.js decomposition plan (Phase 4).
 */
import { laplacianVariance, sharpnessVerdict } from '../core/presenters.js';

const MAX_SHORT_SIDE = 1024;
const JPEG_QUALITY = 0.85;

function loadImageFromFile(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => resolve({ img, url });
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Image unreadable')); };
    img.src = url;
  });
}
function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
async function compressImage(file) {
  const { img, url } = await loadImageFromFile(file);
  try {
    // Fix #10: scale so that the SHORT side ≤ MAX_SHORT_SIDE.
    // Previously MAX_DIM capped the long side at 1600, which still allowed
    // large payloads for wide-angle shots. Capping the short side at 1024
    // keeps both orientations symmetrical and the base64 body small.
    const shortSide = Math.min(img.naturalWidth, img.naturalHeight);
    const scale = Math.min(1, MAX_SHORT_SIDE / shortSide);
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const canvas = document.createElement('canvas');
    canvas.width = w; canvas.height = h;
    canvas.getContext('2d').drawImage(img, 0, 0, w, h);
    const blob = await new Promise((resolve, reject) =>
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('Compression failed'))),
        'image/jpeg', JPEG_QUALITY,
      ),
    );
    const dataUrl = await blobToDataUrl(blob);
    const comma = dataUrl.indexOf(',');

    // Sharpness probe on a 64×64 luma thumbnail. Lets the caller warn the
    // user before burning an LLM call on a blurry frame.
    let sharpness = null;
    try {
      const S = 64;
      const probe = document.createElement('canvas');
      probe.width = S; probe.height = S;
      const pctx = probe.getContext('2d');
      pctx.drawImage(img, 0, 0, S, S);
      const { data } = pctx.getImageData(0, 0, S, S);
      const luma = new Array(S * S);
      for (let i = 0, j = 0; i < data.length; i += 4, j++) {
        // Rec. 601 luma
        luma[j] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
      }
      const v = laplacianVariance(luma, S);
      sharpness = { variance: v, verdict: sharpnessVerdict(v) };
    } catch { /* best-effort — never block compression on the probe */ }

    return { dataUrl, base64: dataUrl.slice(comma + 1), mime: 'image/jpeg', sharpness };
  } finally {
    URL.revokeObjectURL(url);
  }
}

export { loadImageFromFile, blobToDataUrl, compressImage };
