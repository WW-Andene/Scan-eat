/**
 * Vercel Function: POST /api/score
 * Body: { images?: [{ base64, mime? }, ...], imageBase64?: string, mime?: string, barcode?: string }
 * Response: { product, audit, warnings, source, barcode }
 *
 * Hybrid scoring path:
 *   1. If a barcode is provided, hit Open Food Facts first.
 *   2. If OFF returns a record but it's sparse (missing core fields)
 *      AND the user provided photos, run the LLM augmentation pass and
 *      merge — surfacing source conflicts as warnings.
 *   3. If OFF returns nothing, fall back to LLM-only.
 *
 * GROQ_API_KEY is required for LLM paths but NOT for OFF-only scoring,
 * so we don't gate the whole handler on it — only the LLM-using branches
 * fail with a 503 if the key is absent.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

import { parseLabel } from '../src/ocr-parser.ts';
import { fetchFromOFF, isOFFSparse, mergeOFFWithLLM, detectSourceConflicts } from '../src/off.ts';
import { scoreProduct } from '../src/scoring-engine.ts';
import {
  mapErrorToPublicMessage,
  normalizeImages,
  readJsonBody,
  requirePost,
  sendJSON,
} from './_lib.ts';

export const config = {
  runtime: 'nodejs',
  maxDuration: 30, // Pro plan only; Hobby silently caps at 10s
};

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  if (!requirePost(req, res)) return;

  try {
    const body = await readJsonBody<{
      images?: Array<{ base64: string; mime?: string }>;
      imageBase64?: string;
      mime?: string;
      barcode?: string;
    }>(req);

    const images = normalizeImages(body);

    // OFF + optional LLM hybrid path.
    if (body.barcode) {
      const off = await fetchFromOFF(body.barcode);
      if (off) {
        if (isOFFSparse(off) && images.length > 0 && process.env?.GROQ_API_KEY) {
          try {
            const parsed = await parseLabel(images);
            const merged = mergeOFFWithLLM(off, parsed.product);
            const conflicts = detectSourceConflicts(off, parsed.product);
            const audit = scoreProduct(merged);
            return sendJSON(res, 200, {
              product: merged,
              audit,
              warnings: [...parsed.warnings, ...conflicts],
              source: 'merged',
              barcode: body.barcode,
            });
          } catch (llmErr) {
            // LLM failed — fall back to OFF alone. Log first so the
            // operator can see Groq is failing; user-facing path is
            // unaffected because OFF-only scoring still works.
            const m = llmErr instanceof Error ? llmErr.message : String(llmErr);
            console.error('[/api/score] LLM augmentation failed, falling back to OFF:', m);
          }
        }
        const audit = scoreProduct(off);
        return sendJSON(res, 200, {
          product: off,
          audit,
          warnings: [],
          source: 'openfoodfacts',
          barcode: body.barcode,
        });
      }
    }

    if (images.length === 0) {
      return sendJSON(res, 400, { error: 'Missing images' });
    }
    if (!process.env?.GROQ_API_KEY) {
      return sendJSON(res, 503, { error: 'service_unavailable', detail: 'GROQ_API_KEY not configured' });
    }

    const parsed = await parseLabel(images);
    const audit = scoreProduct(parsed.product);

    return sendJSON(res, 200, {
      product: parsed.product,
      audit,
      warnings: parsed.warnings,
      source: 'llm',
      barcode: parsed.barcode ?? null,
    });
  } catch (err) {
    const { status, publicMessage, internalMessage } = mapErrorToPublicMessage(err, 'Scoring failed');
    console.error('[/api/score]', internalMessage);
    return sendJSON(res, status, { error: publicMessage });
  }
}
