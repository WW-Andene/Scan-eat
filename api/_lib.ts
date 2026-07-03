/**
 * Shared boilerplate for all `/api/*.ts` handlers.
 *
 * Why this file exists:
 *   readBody, sendJSON, the body-size cap, the JSON-parse error path,
 *   and the "log internally / return generic to client" sanitization
 *   were copy-pasted into all 8 endpoints, with measurable drift.
 *   When `/api/score` got a fix (e.g., adding the rate-limit branch
 *   on the public-message regex), the others were forgotten. Pulling
 *   them here makes the per-endpoint handlers tiny and ensures any
 *   future hardening lands in one place.
 *
 * Vercel runtime hint: this file lives next to the handlers in `api/`
 * but exports nothing as a serverless function — the absence of a
 * `default export` keeps Vercel from registering it as a route. The
 * leading underscore in the filename is the project convention for
 * "internal helper, not a route".
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

/** Body-size cap shared across handlers. 12 MB is enough for 4 photos
 *  at the engine's MAX_DIM=1600 + JPEG_QUALITY=0.85 worst case. */
export const MAX_BODY_BYTES = 12 * 1024 * 1024;
export const MAX_IMAGES = 4;
export const MAX_IMAGE_BASE64_CHARS = 3_500_000;
const ALLOWED_IMAGE_MIMES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']);

/**
 * Read the request body up to MAX_BODY_BYTES, then return it as a Buffer.
 * Rejects with a tagged error on overflow so the caller's catch can map
 * the public message correctly.
 */
export async function readBody(req: IncomingMessage, maxBytes = MAX_BODY_BYTES): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let total = 0;
    req.on('data', (chunk: Buffer) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new Error(`Body too large (>${maxBytes} bytes)`));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

/** Send a JSON response with proper headers + Content-Length. */
export function sendJSON(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

/**
 * Read + JSON.parse the request body, returning the parsed value.
 * Throws on body overflow or invalid JSON — the caller's catch maps
 * to the right public message via mapErrorToPublicMessage.
 */
export async function readJsonBody<T>(req: IncomingMessage, maxBytes?: number): Promise<T> {
  const raw = await readBody(req, maxBytes);
  return JSON.parse(raw.toString('utf8')) as T;
}

/**
 * Map an internal error to a non-revealing public message. The internal
 * message is logged server-side; the returned string is what the client
 * sees. Keeps stack traces, auth tokens, and Groq-specific error text
 * out of user-facing responses.
 */
export function mapErrorToPublicMessage(err: unknown, fallback: string): {
  status: number;
  publicMessage: string;
  internalMessage: string;
} {
  const internalMessage = err instanceof Error ? err.message : String(err);
  const status =
    err instanceof Error && (err as Error & { status?: number }).status === 429 ? 429
    : /body too large/i.test(internalMessage) ? 413
    : err instanceof Error && (err as Error & { status?: number }).status === 400 ? 400
    : /JSON/i.test(internalMessage) ? 400
    : 500;
  const publicMessage =
    status === 429 ? 'rate_limit'
    : status === 413 ? 'Request body too large'
    : status === 400 ? (err instanceof Error && (err as Error & { publicMessage?: string }).publicMessage) || 'Invalid JSON body'
    : fallback;
  return { status, publicMessage, internalMessage };
}

/**
 * Boilerplate guard for every POST-only endpoint. Rejects non-POST
 * with 405 and returns false so the caller can early-return. Wrapped
 * here so the convention is identical across every handler.
 */
export function requirePost(req: IncomingMessage, res: ServerResponse): boolean {
  if (req.method !== 'POST') {
    sendJSON(res, 405, { error: 'Method not allowed' });
    return false;
  }
  return true;
}

/**
 * Validate that GROQ_API_KEY is configured. Surfaces a typed 503 instead
 * of a generic 500 when the env var is missing, so the UI can distinguish
 * "service unavailable" from "scoring failed". Call at the top of every
 * handler that hits the LLM.
 */
export function requireGroqKey(res: ServerResponse): boolean {
  if (!process.env?.GROQ_API_KEY) {
    sendJSON(res, 503, { error: 'service_unavailable', detail: 'GROQ_API_KEY not configured' });
    return false;
  }
  return true;
}

/**
 * Normalize an `images` payload from a body that may have one of:
 *   - `images: [{ base64, mime? }, ...]`  (current shape)
 *   - `imageBase64: string, mime?: string` (legacy single-image shape,
 *      kept for backward compat with callers that haven't updated)
 * Returns a clean array of `{ base64, mime }` with empty/invalid entries
 * dropped.
 */
export function normalizeImages(body: {
  images?: Array<{ base64?: string; mime?: string }>;
  imageBase64?: string;
  mime?: string;
}): Array<{ base64: string; mime: string }> {
  const raw = Array.isArray(body.images) && body.images.length > 0
    ? body.images
    : typeof body.imageBase64 === 'string' && body.imageBase64.length > 0
      ? [{ base64: body.imageBase64, mime: body.mime }]
      : [];

  return raw
    .filter((i) => typeof i?.base64 === 'string' && i.base64.trim().length > 0)
    .map((i) => ({ base64: (i.base64 as string).trim(), mime: i.mime ?? 'image/jpeg' }));
}

function publicValidationError(message: string): Error & { status: number; publicMessage: string } {
  const err = new Error(message) as Error & { status: number; publicMessage: string };
  err.status = 400;
  err.publicMessage = message;
  return err;
}

export function validateImageScan(body: unknown): {
  images: Array<{ base64: string; mime: string }>;
  barcode?: string;
} {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw publicValidationError('Request body must be a JSON object');
  }
  const obj = body as {
    images?: unknown;
    imageBase64?: unknown;
    mime?: unknown;
    barcode?: unknown;
  };
  if (obj.images != null && !Array.isArray(obj.images)) {
    throw publicValidationError('images must be an array');
  }
  if (obj.imageBase64 != null && typeof obj.imageBase64 !== 'string') {
    throw publicValidationError('imageBase64 must be a string');
  }
  if (obj.mime != null && typeof obj.mime !== 'string') {
    throw publicValidationError('mime must be a string');
  }
  if (Array.isArray(obj.images)) {
    obj.images.forEach((img, idx) => {
      if (!img || typeof img !== 'object' || Array.isArray(img)) {
        throw publicValidationError(`images[${idx}] must be an object`);
      }
      const rec = img as { base64?: unknown; mime?: unknown };
      if (typeof rec.base64 !== 'string' || rec.base64.trim().length === 0) {
        throw publicValidationError(`images[${idx}].base64 is required and must be a string`);
      }
      if (rec.mime != null && typeof rec.mime !== 'string') {
        throw publicValidationError(`images[${idx}].mime must be a string`);
      }
    });
  }
  return {
    images: validateImages(normalizeImages(obj as { images?: Array<{ base64?: string; mime?: string }>; imageBase64?: string; mime?: string })),
    barcode: validateBarcode(obj.barcode),
  };
}

export function validateBarcode(barcode: unknown): string | undefined {
  if (barcode == null || barcode === '') return undefined;
  if (typeof barcode !== 'string') throw publicValidationError('Invalid barcode');
  const clean = barcode.trim();
  if (!/^(?:\d{8}|\d{12,14})$/.test(clean)) throw publicValidationError('Invalid barcode');
  return clean;
}

export function validateImages(images: Array<{ base64: string; mime: string }>): Array<{ base64: string; mime: string }> {
  if (images.length > MAX_IMAGES) throw publicValidationError(`Too many images (max ${MAX_IMAGES})`);
  return images.map((img, idx) => {
    if (!ALLOWED_IMAGE_MIMES.has(img.mime)) throw publicValidationError(`Unsupported image type at index ${idx}`);
    if (img.base64.length > MAX_IMAGE_BASE64_CHARS) throw publicValidationError(`Image too large at index ${idx}`);
    if (!/^[A-Za-z0-9+/]+={0,2}$/.test(img.base64) || img.base64.length % 4 !== 0) {
      throw publicValidationError(`Invalid base64 image at index ${idx}`);
    }
    return img;
  });
}
