/**
 * ============================================================================
 * Scan\'eat local dev server  —  photo in, score out
 * ============================================================================
 *
 * Zero-dep HTTP server (Node 22+ native TS) that mirrors the Vercel setup
 * for local development without needing `vercel dev`:
 *   - Serves the static PWA shell from /public
 *   - Exposes POST /api/score: { imageBase64, mime? } → full ScoreAudit
 *
 * In production, /api/score is handled by api/score.ts as a Vercel Function
 * and the static files are served directly from /public by Vercel's CDN.
 *
 * Run:   GROQ_API_KEY=... node --experimental-strip-types server.ts
 * ============================================================================
 */

import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { parseLabel, identifyFood, identifyMultiFood, identifyMenu, suggestRecipes, suggestRecipesFromPantry } from './ocr-parser.ts';
import { fetchFromOFF, isOFFSparse, mergeOFFWithLLM, detectSourceConflicts } from './off.ts';
import { scoreProduct } from './scoring-engine.ts';

const PORT = Number(process.env.PORT ?? 5173);
const ROOT = resolve(fileURLToPath(new URL('.', import.meta.url)));
const WEB_DIR = join(ROOT, 'public');
const MAX_BODY_BYTES = 12 * 1024 * 1024;

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function sendJSON(res: ServerResponse, status: number, body: unknown) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

// Map a Groq/LLM error to an HTTP response. Returns true if the error was
// rate-limit (429) and a response was sent; callers should early-return.
function sendIfRateLimit(err: unknown, res: ServerResponse): boolean {
  const status = (err as { status?: number } | null)?.status;
  if (status === 429) {
    sendJSON(res, 429, { error: 'rate_limit' });
    return true;
  }
  return false;
}

async function readBody(req: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let total = 0;
    req.on('data', (chunk: Buffer) => {
      total += chunk.length;
      if (total > MAX_BODY_BYTES) {
        reject(new Error(`Body too large (>${MAX_BODY_BYTES} bytes)`));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

async function handleScore(req: IncomingMessage, res: ServerResponse) {
  try {
    const raw = await readBody(req);
    const body = JSON.parse(raw.toString('utf8')) as {
      images?: Array<{ base64: string; mime?: string }>;
      imageBase64?: string;
      mime?: string;
      barcode?: string;
    };

    const images =
      body.images && body.images.length > 0
        ? body.images.map((img) => ({ base64: img.base64, mime: img.mime ?? 'image/jpeg' }))
        : body.imageBase64
          ? [{ base64: body.imageBase64, mime: body.mime ?? 'image/jpeg' }]
          : [];

    // OFF + optional LLM hybrid path.
    if (body.barcode) {
      const off = await fetchFromOFF(body.barcode);
      if (off) {
        if (isOFFSparse(off) && images.length > 0) {
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
          } catch {
            /* LLM failed — fall back to OFF alone */
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
    const message = err instanceof Error ? err.message : String(err);
    console.error('[/api/score]', message);
    if (sendIfRateLimit(err, res)) return;
    const publicMessage =
      /body too large/i.test(message) ? 'Request body too large'
      : /JSON/i.test(message) ? 'Invalid JSON body'
      : 'Scoring failed';
    return sendJSON(res, 500, { error: publicMessage });
  }
}

async function handleStatic(req: IncomingMessage, res: ServerResponse) {
  const urlPath = new URL(req.url ?? '/', 'http://x').pathname;
  const rel = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  const full = normalize(join(WEB_DIR, rel));
  if (!full.startsWith(WEB_DIR)) {
    res.writeHead(403).end('Forbidden');
    return;
  }
  try {
    const info = await stat(full);
    if (!info.isFile()) throw new Error('not a file');
    const mime = MIME[extname(full)] ?? 'application/octet-stream';
    if (req.method === 'HEAD') {
      res.writeHead(200, { 'Content-Type': mime, 'Content-Length': info.size });
      res.end();
      return;
    }
    const data = await readFile(full);
    res.writeHead(200, { 'Content-Type': mime, 'Content-Length': data.length });
    res.end(data);
  } catch {
    res.writeHead(404).end('Not found');
  }
}

async function handleIdentifyMulti(req: IncomingMessage, res: ServerResponse) {
  try {
    const raw = await readBody(req);
    const body = JSON.parse(raw.toString('utf8')) as {
      images?: Array<{ base64: string; mime?: string }>;
    };
    const images = (body.images ?? [])
      .filter((i) => typeof i?.base64 === 'string' && i.base64.length > 0)
      .map((i) => ({ base64: i.base64, mime: i.mime ?? 'image/jpeg' }));
    if (images.length === 0) return sendJSON(res, 400, { error: 'Missing images' });
    const result = await identifyMultiFood(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error('[/api/identify-multi]', message);
    if (sendIfRateLimit(err, res)) return;
    const publicMessage =
      /body too large/i.test(message) ? 'Request body too large'
      : /JSON/i.test(message) ? 'Invalid JSON body'
      : 'Identification failed';
    return sendJSON(res, 500, { error: publicMessage });
  }
}

async function handleIdentifyMenu(req: IncomingMessage, res: ServerResponse) {
  try {
    const raw = await readBody(req);
    const body = JSON.parse(raw.toString('utf8')) as {
      images?: Array<{ base64: string; mime?: string }>;
    };
    const images = (body.images ?? [])
      .filter((i) => typeof i?.base64 === 'string' && i.base64.length > 0)
      .map((i) => ({ base64: i.base64, mime: i.mime ?? 'image/jpeg' }));
    if (images.length === 0) return sendJSON(res, 400, { error: 'Missing images' });
    const result = await identifyMenu(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error('[/api/identify-menu]', message);
    if (sendIfRateLimit(err, res)) return;
    const publicMessage =
      /body too large/i.test(message) ? 'Request body too large'
      : /JSON/i.test(message) ? 'Invalid JSON body'
      : 'Menu scan failed';
    return sendJSON(res, 500, { error: publicMessage });
  }
}

async function handleSuggestFromPantry(req: IncomingMessage, res: ServerResponse) {
  try {
    const raw = await readBody(req);
    const body = JSON.parse(raw.toString('utf8')) as { pantry?: string[] };
    const pantry = (Array.isArray(body.pantry) ? body.pantry : [])
      .filter((s) => typeof s === 'string' && s.trim().length > 0)
      .slice(0, 20);
    if (pantry.length === 0) return sendJSON(res, 400, { error: 'Empty pantry' });
    const result = await suggestRecipesFromPantry(pantry);
    return sendJSON(res, 200, result);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error('[/api/suggest-from-pantry]', message);
    if (sendIfRateLimit(err, res)) return;
    const publicMessage =
      /body too large/i.test(message) ? 'Request body too large'
      : /JSON/i.test(message) ? 'Invalid JSON body'
      : 'Pantry suggestion failed';
    return sendJSON(res, 500, { error: publicMessage });
  }
}

async function handleSuggestRecipes(req: IncomingMessage, res: ServerResponse) {
  try {
    const raw = await readBody(req);
    const body = JSON.parse(raw.toString('utf8')) as { ingredient?: string };
    const ingredient = typeof body.ingredient === 'string' ? body.ingredient.trim() : '';
    if (!ingredient) return sendJSON(res, 400, { error: 'Missing ingredient' });
    const result = await suggestRecipes(ingredient);
    return sendJSON(res, 200, result);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error('[/api/suggest-recipes]', message);
    if (sendIfRateLimit(err, res)) return;
    const publicMessage =
      /body too large/i.test(message) ? 'Request body too large'
      : /JSON/i.test(message) ? 'Invalid JSON body'
      : 'Recipe suggestion failed';
    return sendJSON(res, 500, { error: publicMessage });
  }
}

async function handleIdentify(req: IncomingMessage, res: ServerResponse) {
  try {
    const raw = await readBody(req);
    const body = JSON.parse(raw.toString('utf8')) as {
      images?: Array<{ base64: string; mime?: string }>;
    };
    const images = (body.images ?? [])
      .filter((i) => typeof i?.base64 === 'string' && i.base64.length > 0)
      .map((i) => ({ base64: i.base64, mime: i.mime ?? 'image/jpeg' }));
    if (images.length === 0) return sendJSON(res, 400, { error: 'Missing images' });
    const result = await identifyFood(images);
    return sendJSON(res, 200, result);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error('[/api/identify]', message);
    if (sendIfRateLimit(err, res)) return;
    const publicMessage =
      /body too large/i.test(message) ? 'Request body too large'
      : /JSON/i.test(message) ? 'Invalid JSON body'
      : 'Identification failed';
    return sendJSON(res, 500, { error: publicMessage });
  }
}

const server = createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/api/score') {
    return handleScore(req, res);
  }
  if (req.method === 'POST' && req.url === '/api/identify') {
    return handleIdentify(req, res);
  }
  if (req.method === 'POST' && req.url === '/api/identify-multi') {
    return handleIdentifyMulti(req, res);
  }
  if (req.method === 'POST' && req.url === '/api/identify-menu') {
    return handleIdentifyMenu(req, res);
  }
  if (req.method === 'POST' && req.url === '/api/suggest-recipes') {
    return handleSuggestRecipes(req, res);
  }
  if (req.method === 'POST' && req.url === '/api/suggest-from-pantry') {
    return handleSuggestFromPantry(req, res);
  }
  // GET + HEAD both map to static. HEAD lets service workers and curl probe
  // file availability without downloading the full payload.
  if (req.method === 'GET' || req.method === 'HEAD') {
    return handleStatic(req, res);
  }
  res.writeHead(405).end('Method not allowed');
});

server.listen(PORT, () => {
  if (!process.env.GROQ_API_KEY) {
    console.warn('[server] GROQ_API_KEY is not set — /api/score will fail.');
  }
  console.log(`[server] Scan\'eat dev server ready at http://localhost:${PORT}`);
});
