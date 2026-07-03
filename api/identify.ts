/**
 * Vercel Function: POST /api/identify
 * Body: { images: [{ base64, mime? }, ...] }
 * Response: IdentifiedFood  (see ocr-parser.ts)
 *
 * Companion to /api/score. Used when the user photographs a plate / fresh
 * food / bakery item that has no barcode or ingredient label — the label-
 * scanning prompt is a poor fit, so we use the dedicated food-identification
 * prompt instead.
 *
 * GROQ_API_KEY must be set in Vercel project env vars.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { identifyFood } from '../src/ocr-parser.ts';
import {
  mapErrorToPublicMessage,
  normalizeImages,
  readJsonBody,
  requireGroqKey,
  requirePost,
  sendJSON,
} from './_lib.ts';

export const config = {
  runtime: 'nodejs',
  maxDuration: 30,
};

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  if (!requirePost(req, res)) return;
  if (!requireGroqKey(res)) return;
  try {
    const body = await readJsonBody<{
      images?: Array<{ base64: string; mime?: string }>;
    }>(req);
    const images = normalizeImages(body);
    if (images.length === 0) return sendJSON(res, 400, { error: 'Missing images' });

    const result = await identifyFood(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Identification failed');
    console.error('[/api/identify]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
