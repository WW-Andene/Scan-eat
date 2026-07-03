/**
 * Vercel Function: POST /api/identify-menu
 * Body: { images: [{ base64, mime? }, ...] }
 * Response: IdentifiedMenu  (see ocr-parser.ts)
 *
 * Extracts a list of dishes from a restaurant-menu photo with rough
 * per-portion macros. Complements /api/identify (single food) and
 * /api/identify-multi (multi-item plate).
 *
 * GROQ_API_KEY must be set in Vercel project env vars.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { identifyMenu } from '../src/ocr-parser.ts';
import {
  mapErrorToPublicMessage,
  normalizeImages,
  readJsonBody,
  requireGroqKey,
  requirePost,
  sendJSON,
} from './_lib.ts';

export const config = { runtime: 'nodejs', maxDuration: 30 };

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  if (!requirePost(req, res)) return;
  if (!requireGroqKey(res)) return;
  try {
    const body = await readJsonBody<{
      images?: Array<{ base64: string; mime?: string }>;
    }>(req);
    const images = normalizeImages(body);
    if (images.length === 0) return sendJSON(res, 400, { error: 'Missing images' });

    const result = await identifyMenu(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Menu scan failed');
    console.error('[/api/identify-menu]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
