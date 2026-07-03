/**
 * Smoke tests for the 8 Vercel function handlers in api/*.ts.
 *
 * Why these exist:
 *   Before this batch, only /api/score was exercised indirectly via
 *   parseLabel() in parser-tests.ts. The other 7 endpoints
 *   (identify, identify-multi, identify-menu, identify-recipe,
 *    suggest-recipes, suggest-from-pantry, fetch-recipe) had ZERO
 *   coverage — a copy-paste typo in error mapping or a 405-rejection
 *   regression would have shipped silently.
 *
 * What we cover:
 *   - 405 on the wrong HTTP method.
 *   - 400 on missing required input (images / ingredient / pantry / url).
 *   - 503 service_unavailable when GROQ_API_KEY is absent (LLM endpoints).
 *   - We do NOT exercise the happy path — that hits Groq's real API,
 *     which is paid + flaky. The "happy path returns whatever the LLM
 *     emits" contract is owned by parser-tests.ts on the underlying
 *     functions.
 *
 * Mock strategy:
 *   - mockReq(chunks, method, url?) — a Readable wearing the
 *     IncomingMessage shape.
 *   - mockRes() — a writeHead/end sink, returns the captured status +
 *     body for assertion.
 */

import { strict as assert } from 'node:assert';
import { describe, it, before, after } from 'node:test';
import { Readable } from 'node:stream';
import type { IncomingMessage, ServerResponse } from 'node:http';

import scoreHandler from '../api/score.ts';
import identifyHandler from '../api/identify.ts';
import identifyMultiHandler from '../api/identify-multi.ts';
import identifyMenuHandler from '../api/identify-menu.ts';
import identifyRecipeHandler from '../api/identify-recipe.ts';
import suggestRecipesHandler from '../api/suggest-recipes.ts';
import suggestPantryHandler from '../api/suggest-from-pantry.ts';
import fetchRecipeHandler from '../api/fetch-recipe.ts';

// --- mock plumbing -----------------------------------------------------

function mockReq(chunks: (string | Buffer)[], method = 'POST', url = '/'): IncomingMessage {
  const buffers = chunks.map((c) => (typeof c === 'string' ? Buffer.from(c, 'utf8') : c));
  const stream = Readable.from(buffers);
  (stream as unknown as { method: string; url: string }).method = method;
  (stream as unknown as { method: string; url: string }).url = url;
  return stream as unknown as IncomingMessage;
}

interface CapturedResponse {
  status: number | null;
  body: string;
  res: ServerResponse;
}

function mockRes(): CapturedResponse {
  const captured: CapturedResponse = { status: null, body: '', res: undefined as unknown as ServerResponse };
  const fake = {
    writeHead(status: number) { captured.status = status; return this; },
    end(payload: string) { captured.body = payload; },
  };
  captured.res = fake as unknown as ServerResponse;
  return captured;
}

// --- env management ----------------------------------------------------
//
// We toggle GROQ_API_KEY around tests. Save the original and restore it
// in `after` so other test files aren't affected.

let _savedKey: string | undefined;

before(() => { _savedKey = process.env.GROQ_API_KEY; });
after(() => {
  if (_savedKey === undefined) delete process.env.GROQ_API_KEY;
  else process.env.GROQ_API_KEY = _savedKey;
});

// --- test matrix -------------------------------------------------------
//
// All POST-only handlers share the same 405 + 400 + 503 contract.
// We loop the assertion suite once per handler.

interface PostHandler {
  label: string;
  fn: (req: IncomingMessage, res: ServerResponse) => Promise<unknown>;
  validBody: string;
  /** Body that should reach the GROQ-key guard (i.e., has all required input). */
  fullValidBody: string;
}

const POST_HANDLERS: PostHandler[] = [
  {
    label: '/api/score',
    fn: scoreHandler as PostHandler['fn'],
    validBody: '{"images":[]}',
    fullValidBody: '{"images":[{"base64":"AAAA"}]}',
  },
  {
    label: '/api/identify',
    fn: identifyHandler as PostHandler['fn'],
    validBody: '{"images":[]}',
    fullValidBody: '{"images":[{"base64":"AAAA"}]}',
  },
  {
    label: '/api/identify-multi',
    fn: identifyMultiHandler as PostHandler['fn'],
    validBody: '{"images":[]}',
    fullValidBody: '{"images":[{"base64":"AAAA"}]}',
  },
  {
    label: '/api/identify-menu',
    fn: identifyMenuHandler as PostHandler['fn'],
    validBody: '{"images":[]}',
    fullValidBody: '{"images":[{"base64":"AAAA"}]}',
  },
  {
    label: '/api/identify-recipe',
    fn: identifyRecipeHandler as PostHandler['fn'],
    validBody: '{"images":[]}',
    fullValidBody: '{"images":[{"base64":"AAAA"}]}',
  },
  {
    label: '/api/suggest-recipes',
    fn: suggestRecipesHandler as PostHandler['fn'],
    validBody: '{"ingredient":""}',
    fullValidBody: '{"ingredient":"poulet"}',
  },
  {
    label: '/api/suggest-from-pantry',
    fn: suggestPantryHandler as PostHandler['fn'],
    validBody: '{"pantry":[]}',
    fullValidBody: '{"pantry":["lentilles","oignon","tomate"]}',
  },
];

for (const h of POST_HANDLERS) {
  describe(`api: ${h.label}`, () => {
    it('returns 405 on a non-POST request', async () => {
      const req = mockReq([], 'GET');
      const res = mockRes();
      await h.fn(req, res.res);
      assert.equal(res.status, 405);
      assert.match(res.body, /Method not allowed/);
    });

    it('returns 400 on missing required input', async () => {
      // /api/score's special case: no barcode + no images = 400 only when
      // we go past the GROQ-key check. With the key absent, score returns
      // 503 instead. Set the key for this test so we hit the 400 branch.
      const prev = process.env.GROQ_API_KEY;
      process.env.GROQ_API_KEY = 'gsk_test_400';
      try {
        const req = mockReq([h.validBody], 'POST');
        const res = mockRes();
        await h.fn(req, res.res);
        assert.equal(res.status, 400);
        assert.match(res.body, /(Missing|Empty|ingredient|pantry|images)/i);
      } finally {
        if (prev === undefined) delete process.env.GROQ_API_KEY;
        else process.env.GROQ_API_KEY = prev;
      }
    });

    it('returns 400 on invalid JSON body', async () => {
      // Most handlers gate on GROQ_API_KEY before reading the body, so
      // the JSON-parse 400 only fires once that gate is past.
      const prev = process.env.GROQ_API_KEY;
      process.env.GROQ_API_KEY = 'gsk_test_invalid_json';
      try {
        const req = mockReq(['{not json'], 'POST');
        const res = mockRes();
        await h.fn(req, res.res);
        assert.equal(res.status, 400);
        assert.match(res.body, /Invalid JSON/i);
      } finally {
        if (prev === undefined) delete process.env.GROQ_API_KEY;
        else process.env.GROQ_API_KEY = prev;
      }
    });
  });
}

// --- LLM endpoints' GROQ_API_KEY guard --------------------------------
//
// /api/score has a special path: it does OFF lookup first, so a barcode-
// only call doesn't need the key. The other 6 LLM endpoints reject
// immediately with 503 when the key is missing.

const LLM_REQUIRED_HANDLERS = POST_HANDLERS.filter((h) => h.label !== '/api/score');

for (const h of LLM_REQUIRED_HANDLERS) {
  describe(`api: ${h.label} GROQ_API_KEY guard`, () => {
    it('returns 503 service_unavailable when GROQ_API_KEY is unset', async () => {
      const prev = process.env.GROQ_API_KEY;
      delete process.env.GROQ_API_KEY;
      try {
        const req = mockReq([h.fullValidBody], 'POST');
        const res = mockRes();
        await h.fn(req, res.res);
        assert.equal(res.status, 503);
        assert.match(res.body, /service_unavailable/);
      } finally {
        if (prev !== undefined) process.env.GROQ_API_KEY = prev;
      }
    });
  });
}

// --- /api/fetch-recipe (GET-only, distinct contract) ------------------

describe('api: /api/fetch-recipe', () => {
  it('returns 405 on POST', async () => {
    const req = mockReq([], 'POST', '/api/fetch-recipe');
    const res = mockRes();
    await fetchRecipeHandler(req, res.res);
    assert.equal(res.status, 405);
  });

  it('returns 400 on missing url query param', async () => {
    const req = mockReq([], 'GET', '/api/fetch-recipe');
    const res = mockRes();
    await fetchRecipeHandler(req, res.res);
    assert.equal(res.status, 400);
    assert.match(res.body, /Invalid URL/);
  });

  it('rejects non-http(s) URL schemes', async () => {
    const req = mockReq([], 'GET', '/api/fetch-recipe?url=' + encodeURIComponent('file:///etc/passwd'));
    const res = mockRes();
    await fetchRecipeHandler(req, res.res);
    assert.equal(res.status, 400);
    assert.match(res.body, /http\(s\)/);
  });
});
