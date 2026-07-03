/**
 * Vercel Function: POST /api/suggest-recipes
 * Body: { ingredient: string }
 * Response: SuggestedRecipes  (see ocr-parser.ts)
 *
 * Chef-style recipe ideas for a single ingredient. Text-only call (no
 * vision) — companion to /api/identify which extracts the ingredient in
 * the first place.
 *
 * GROQ_API_KEY must be set in Vercel project env vars.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { suggestRecipes } from '../src/ocr-parser.ts';
import {
  mapErrorToPublicMessage,
  readJsonBody,
  requireGroqKey,
  requirePost,
  sendJSON,
} from './_lib.ts';

export const config = { runtime: 'nodejs', maxDuration: 30 };

const MAX_BODY_BYTES = 16 * 1024; // text only — 16 kB is plenty

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  if (!requirePost(req, res)) return;
  if (!requireGroqKey(res)) return;
  try {
    const body = await readJsonBody<{ ingredient?: string }>(req, MAX_BODY_BYTES);
    const ingredient = typeof body.ingredient === 'string' ? body.ingredient.trim() : '';
    if (!ingredient) return sendJSON(res, 400, { error: 'Missing ingredient' });

    const result = await suggestRecipes(ingredient);
    return sendJSON(res, 200, result);
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Recipe suggestion failed');
    console.error('[/api/suggest-recipes]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
