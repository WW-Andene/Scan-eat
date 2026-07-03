/**
 * Vercel Function: POST /api/identify-recipe
 * Body: { images: [{ base64, mime? }, ...] }
 * Response: IdentifiedRecipe  { name, servings, ingredients[], steps[], cook_time_min }
 *
 * Sibling of /api/identify-menu, but for recipe cards / cookbook pages /
 * handwritten recipes. The user photographs a recipe; the LLM extracts
 * the structured shape our recipe editor can pre-fill.
 *
 * GROQ_API_KEY must be set in Vercel project env vars.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { identifyRecipe } from '../src/ocr-parser.ts';
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

    const result = await identifyRecipe(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Recipe identification failed');
    console.error('[/api/identify-recipe]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
