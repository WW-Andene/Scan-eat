/**
 * Vercel Function: GET /api/fetch-recipe?url=<recipe-page-url>
 * Response: { name, servings, ingredients[], nutrition?, steps[], source_url }
 *
 * Client-side `fetch` to third-party recipe blogs is blocked by CORS.
 * This endpoint proxies the HTML, extracts the schema.org Recipe
 * (JSON-LD microdata that every major recipe site ships), and returns
 * a compact object the Scan\'eat recipe editor can pre-fill.
 *
 * Safety:
 *   - URL must be http(s), public scheme.
 *   - 8 s fetch timeout (recipe blogs are often slow).
 *   - Response body capped at 2 MB — any sane recipe page is far under.
 *   - User-Agent spoof (Scan\'eat) so sites know who's hitting them.
 *
 * No GROQ_API_KEY here — this endpoint is pure HTML fetch + parse,
 * no LLM dependency. The photo-to-recipe LLM flow lives in
 * /api/identify-recipe.
 */

import type { IncomingMessage, ServerResponse } from 'node:http';

export const config = {
  runtime: 'nodejs',
  maxDuration: 15,
};

const FETCH_TIMEOUT_MS = 8_000;
import { sendJSON } from './_lib.ts';

const MAX_BODY_BYTES = 2 * 1024 * 1024;

// --- ISO 8601 duration parser (PT30M, PT1H15M) ---
function parseISODuration(s: unknown): number | null {
  if (typeof s !== 'string') return null;
  const m = s.match(/^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/);
  if (!m) return null;
  const h = Number(m[1]) || 0;
  const min = Number(m[2]) || 0;
  const sec = Number(m[3]) || 0;
  return h * 60 + min + Math.round(sec / 60);
}

// --- Nutrition block parser (NutritionInformation) ---
function numFromMeasure(s: unknown): number {
  if (typeof s === 'number') return Number.isFinite(s) ? s : 0;
  if (typeof s === 'string') {
    // "240 kcal", "15 g", "120 mg" — grab the leading number.
    const n = parseFloat(s.replace(',', '.'));
    return Number.isFinite(n) ? n : 0;
  }
  return 0;
}

function nutritionBlock(n: Record<string, unknown> | undefined) {
  if (!n || typeof n !== 'object') return undefined;
  return {
    kcal:         numFromMeasure(n['calories']),
    protein_g:    numFromMeasure(n['proteinContent']),
    fat_g:        numFromMeasure(n['fatContent']),
    sat_fat_g:    numFromMeasure(n['saturatedFatContent']),
    carbs_g:      numFromMeasure(n['carbohydrateContent']),
    sugars_g:     numFromMeasure(n['sugarContent']),
    fiber_g:      numFromMeasure(n['fiberContent']),
    sodium_mg:    numFromMeasure(n['sodiumContent']),
    cholesterol_mg: numFromMeasure(n['cholesterolContent']),
    serving_size: typeof n['servingSize'] === 'string' ? n['servingSize'] : null,
  };
}

function stringOr(v: unknown, fallback: string): string {
  return typeof v === 'string' && v.trim() ? v.trim() : fallback;
}

function stringArray(v: unknown): string[] {
  if (Array.isArray(v)) {
    return v
      .map((x) => (typeof x === 'string' ? x.trim() : typeof x === 'object' && x && 'text' in x ? String((x as { text: unknown }).text).trim() : ''))
      .filter((s) => s.length > 0);
  }
  if (typeof v === 'string' && v.trim()) return [v.trim()];
  return [];
}

// --- Recipe finder: walks any JSON-LD graph. ---
function findRecipe(json: unknown): Record<string, unknown> | null {
  if (!json) return null;
  if (Array.isArray(json)) {
    for (const item of json) {
      const hit = findRecipe(item);
      if (hit) return hit;
    }
    return null;
  }
  if (typeof json !== 'object') return null;
  const obj = json as Record<string, unknown>;
  const type = obj['@type'];
  const typeMatches = type === 'Recipe'
    || (Array.isArray(type) && type.includes('Recipe'));
  if (typeMatches) return obj;
  // Follow @graph + nested fields.
  if (obj['@graph']) {
    const hit = findRecipe(obj['@graph']);
    if (hit) return hit;
  }
  return null;
}

// --- HTML → JSON-LD scripts ---
function extractJSONLD(html: string): unknown[] {
  const blocks: unknown[] = [];
  // Match every <script type="application/ld+json">…</script>
  const re = /<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html)) !== null) {
    const raw = m[1].trim();
    if (!raw) continue;
    try {
      blocks.push(JSON.parse(raw));
    } catch {
      // Some sites ship with trailing commas or HTML-escaped entities.
      // Best-effort: unescape common entities + retry once.
      try {
        const cleaned = raw
          .replace(/&quot;/g, '"')
          .replace(/&amp;/g, '&')
          .replace(/&#34;/g, '"');
        blocks.push(JSON.parse(cleaned));
      } catch { /* skip */ }
    }
  }
  return blocks;
}

// --- Yield → servings number ---
function yieldToServings(v: unknown): number {
  if (typeof v === 'number' && Number.isFinite(v) && v > 0) return Math.round(v);
  if (typeof v === 'string') {
    const m = v.match(/(\d+)/);
    if (m) return Number(m[1]);
  }
  if (Array.isArray(v) && v.length > 0) return yieldToServings(v[0]);
  return 1;
}

// --- Public shape we return to the client ---
export interface FetchedRecipe {
  name: string;
  servings: number;
  ingredients: string[];
  steps: string[];
  nutrition?: ReturnType<typeof nutritionBlock>;
  cook_time_min?: number;
  source_url: string;
}

export async function parseRecipeFromHtml(html: string, sourceUrl: string): Promise<FetchedRecipe | null> {
  const blocks = extractJSONLD(html);
  let recipe: Record<string, unknown> | null = null;
  for (const b of blocks) {
    recipe = findRecipe(b);
    if (recipe) break;
  }
  if (!recipe) return null;
  return {
    name: stringOr(recipe['name'], ''),
    servings: yieldToServings(recipe['recipeYield']),
    ingredients: stringArray(recipe['recipeIngredient']),
    steps: stringArray(
      Array.isArray(recipe['recipeInstructions'])
        ? (recipe['recipeInstructions'] as unknown[]).map((s) => {
            if (typeof s === 'string') return s;
            if (s && typeof s === 'object' && 'text' in s) return String((s as { text: unknown }).text || '');
            return '';
          })
        : recipe['recipeInstructions'],
    ),
    nutrition: nutritionBlock(recipe['nutrition'] as Record<string, unknown> | undefined),
    cook_time_min: parseISODuration(recipe['totalTime']) ?? parseISODuration(recipe['cookTime']) ?? undefined,
    source_url: sourceUrl,
  };
}

async function fetchWithTimeout(url: string, ms: number): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), ms);
  try {
    return await fetch(url, {
      signal: ctrl.signal,
      redirect: 'follow',
      headers: {
        'User-Agent': 'ScanEat/1.0 (+https://scann-eat.local)',
        'Accept': 'text/html,application/xhtml+xml',
      },
    });
  } finally {
    clearTimeout(timer);
  }
}

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  if (req.method !== 'GET') {
    return sendJSON(res, 405, { error: 'Method not allowed' });
  }
  const url = new URL(req.url ?? '/', 'http://localhost');
  const target = url.searchParams.get('url') || '';
  let parsed: URL;
  try { parsed = new URL(target); }
  catch { return sendJSON(res, 400, { error: 'Invalid URL' }); }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return sendJSON(res, 400, { error: 'Only http(s) URLs are allowed' });
  }
  try {
    const r = await fetchWithTimeout(parsed.toString(), FETCH_TIMEOUT_MS);
    if (!r.ok) return sendJSON(res, 502, { error: `Upstream HTTP ${r.status}` });
    const cl = Number(r.headers.get('content-length') || 0);
    if (cl > MAX_BODY_BYTES) return sendJSON(res, 413, { error: 'Upstream body too large' });
    const html = await r.text();
    if (html.length > MAX_BODY_BYTES) return sendJSON(res, 413, { error: 'Upstream body too large' });
    const recipe = await parseRecipeFromHtml(html, parsed.toString());
    if (!recipe) return sendJSON(res, 404, { error: 'No schema.org Recipe found on this page' });
    return sendJSON(res, 200, recipe);
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    console.error('[/api/fetch-recipe]', msg);
    const publicMsg =
      /aborted/i.test(msg) ? 'Upstream timed out'
      : /fetch failed/i.test(msg) ? 'Upstream fetch failed'
      : 'Recipe import failed';
    return sendJSON(res, 500, { error: publicMsg });
  }
}
