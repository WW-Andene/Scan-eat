/**
 * Vercel Function: POST /api/suggest-from-pantry
 * Body: { pantry: string[] }
 * Response: SuggestedRecipes  (see ocr-parser.ts)
 *
 * Pantry-first recipe search — given the ingredients the user already
 * has, propose recipes that use most of them + standard pantry staples.
 *
 * GROQ_API_KEY must be set in Vercel project env vars.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { suggestRecipesFromPantry } from '../src/ocr-parser.ts';
import {
  mapErrorToPublicMessage,
  readJsonBody,
  requireGroqKey,
  requirePost,
  sendJSON,
} from './_lib.ts';

export const config = { runtime: 'nodejs', maxDuration: 30 };

const MAX_BODY_BYTES = 64 * 1024;

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  if (!requirePost(req, res)) return;
  if (!requireGroqKey(res)) return;
  try {
    const body = await readJsonBody<{ pantry?: string[] }>(req, MAX_BODY_BYTES);
    const pantry = (Array.isArray(body.pantry) ? body.pantry : [])
      .filter((s) => typeof s === 'string' && s.trim().length > 0)
      .slice(0, 20);
    if (pantry.length === 0) return sendJSON(res, 400, { error: 'Empty pantry' });

    const result = await suggestRecipesFromPantry(pantry);
    return sendJSON(res, 200, result);
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Pantry suggestion failed');
    console.error('[/api/suggest-from-pantry]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
