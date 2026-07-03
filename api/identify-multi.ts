/**
 * Vercel Function: POST /api/identify-multi
 * Body: { images: [{ base64, mime? }, ...] }
 * Response: IdentifiedMultiFood  (see ocr-parser.ts)
 *
 * Companion to /api/identify. Used when the user photographs a plate with
 * multiple distinct foods (steak + fries + salad, buffet tray) and wants
 * each logged separately.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { identifyMultiFood } from '../src/ocr-parser.ts';
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

    const result = await identifyMultiFood(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Identification failed');
    console.error('[/api/identify-multi]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
