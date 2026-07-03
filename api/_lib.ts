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
    : /JSON/i.test(internalMessage) ? 400
    : 500;
  const publicMessage =
    status === 429 ? 'rate_limit'
    : status === 413 ? 'Request body too large'
    : status === 400 ? 'Invalid JSON body'
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
  if (Array.isArray(body.images) && body.images.length > 0) {
    return body.images
      .filter((i) => typeof i?.base64 === 'string' && i.base64.length > 0)
      .map((i) => ({ base64: i.base64 as string, mime: i.mime ?? 'image/jpeg' }));
  }
  if (typeof body.imageBase64 === 'string' && body.imageBase64.length > 0) {
    return [{ base64: body.imageBase64, mime: body.mime ?? 'image/jpeg' }];
  }
  return [];
}
