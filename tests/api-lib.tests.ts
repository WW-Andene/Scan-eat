/**
 * Smoke tests for api/_lib.ts — the shared boilerplate that every
 * /api/*.ts handler relies on. Hardening here prevents regressions
 * across all 8 endpoints simultaneously.
 *
 * What we cover:
 *   - readBody: streams the body, enforces the cap, rejects overflow.
 *   - sendJSON: writes correct headers + Content-Length.
 *   - mapErrorToPublicMessage: 429 / 413 / 400 / generic mapping.
 *   - normalizeImages: handles modern + legacy shapes; drops junk.
 *   - requirePost / requireGroqKey: status + body shape on rejection.
 *
 * No real Vercel runtime here — we fabricate IncomingMessage /
 * ServerResponse mocks because Node's stream + http machinery is
 * heavy to instantiate just to assert behaviour.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { Readable } from 'node:stream';
import type { IncomingMessage, ServerResponse } from 'node:http';

import {
  MAX_BODY_BYTES,
  mapErrorToPublicMessage,
  normalizeImages,
  readBody,
  readJsonBody,
  requireGroqKey,
  requirePost,
  sendJSON,
} from '../api/_lib.ts';

// --- mock helpers ------------------------------------------------------

/** Build an IncomingMessage-shaped Readable carrying the given chunks. */
function mockReq(chunks: (string | Buffer)[], method = 'POST'): IncomingMessage {
  const buffers = chunks.map((c) => (typeof c === 'string' ? Buffer.from(c, 'utf8') : c));
  const stream = Readable.from(buffers);
  (stream as unknown as { method: string }).method = method;
  // readBody uses .on('data'/'end'/'error') — Readable already emits those.
  // .destroy() short-circuits stream consumption on overflow; Readable also
  // already supports it. Cast through unknown for the IncomingMessage type.
  return stream as unknown as IncomingMessage;
}

interface CapturedResponse {
  status: number | null;
  headers: Record<string, string | number>;
  body: string;
  res: ServerResponse;
}

/** Build a ServerResponse-shaped sink that records writeHead/end calls. */
function mockRes(): CapturedResponse {
  const captured: CapturedResponse = {
    status: null,
    headers: {},
    body: '',
    // assigned below
    res: undefined as unknown as ServerResponse,
  };
  const fake = {
    writeHead(status: number, headers: Record<string, string | number>) {
      captured.status = status;
      captured.headers = headers;
      return this;
    },
    end(payload: string) {
      captured.body = payload;
    },
  };
  captured.res = fake as unknown as ServerResponse;
  return captured;
}

// --- tests -------------------------------------------------------------

describe('api/_lib: readBody', () => {
  it('reads a normal-sized body into a Buffer', async () => {
    const req = mockReq(['{"hello":', '"world"}']);
    const buf = await readBody(req);
    assert.equal(buf.toString('utf8'), '{"hello":"world"}');
  });

  it('rejects with a tagged error when body exceeds the cap', async () => {
    // 100-byte cap, body ≈ 200 bytes
    const big = 'x'.repeat(200);
    const req = mockReq([big]);
    await assert.rejects(
      () => readBody(req, 100),
      /Body too large/i,
    );
  });

  it('default cap = MAX_BODY_BYTES = 12 MB', () => {
    assert.equal(MAX_BODY_BYTES, 12 * 1024 * 1024);
  });
});

describe('api/_lib: readJsonBody', () => {
  it('parses a JSON body', async () => {
    const req = mockReq([JSON.stringify({ a: 1, b: 'two' })]);
    const body = await readJsonBody<{ a: number; b: string }>(req);
    assert.deepEqual(body, { a: 1, b: 'two' });
  });

  it('throws on invalid JSON (caller catches via mapErrorToPublicMessage)', async () => {
    const req = mockReq(['{not json']);
    await assert.rejects(() => readJsonBody(req), /JSON/i);
  });
});

describe('api/_lib: sendJSON', () => {
  it('writes status + content-type + content-length + body', () => {
    const r = mockRes();
    sendJSON(r.res, 201, { ok: true, n: 7 });
    assert.equal(r.status, 201);
    assert.equal(r.headers['Content-Type'], 'application/json; charset=utf-8');
    assert.equal(r.body, '{"ok":true,"n":7}');
    assert.equal(r.headers['Content-Length'], Buffer.byteLength(r.body));
  });
});

describe('api/_lib: mapErrorToPublicMessage', () => {
  it('429 (Groq rate limit) → status 429 + "rate_limit"', () => {
    const err = new Error('Groq API 429: too many requests');
    (err as Error & { status?: number }).status = 429;
    const m = mapErrorToPublicMessage(err, 'fallback');
    assert.equal(m.status, 429);
    assert.equal(m.publicMessage, 'rate_limit');
  });

  it('"Body too large …" → 413 + sanitized message', () => {
    const err = new Error('Body too large (>12345 bytes)');
    const m = mapErrorToPublicMessage(err, 'fallback');
    assert.equal(m.status, 413);
    assert.equal(m.publicMessage, 'Request body too large');
  });

  it('SyntaxError from JSON.parse → 400 + "Invalid JSON body"', () => {
    let caught: unknown;
    try { JSON.parse('{not json'); } catch (e) { caught = e; }
    const m = mapErrorToPublicMessage(caught, 'fallback');
    assert.equal(m.status, 400);
    assert.equal(m.publicMessage, 'Invalid JSON body');
  });

  it('unknown error → 500 + the caller-supplied fallback', () => {
    const err = new Error('Groq vision: model unavailable');
    const m = mapErrorToPublicMessage(err, 'Identification failed');
    assert.equal(m.status, 500);
    assert.equal(m.publicMessage, 'Identification failed');
  });

  it('non-Error thrown values still produce a sane public response', () => {
    const m = mapErrorToPublicMessage('something weird', 'Scoring failed');
    assert.equal(m.status, 500);
    assert.equal(m.publicMessage, 'Scoring failed');
  });
});

describe('api/_lib: normalizeImages', () => {
  it('returns canonical {base64, mime} from the modern images[] shape', () => {
    const out = normalizeImages({
      images: [
        { base64: 'AAAA', mime: 'image/png' },
        { base64: 'BBBB' }, // mime defaulted
      ],
    });
    assert.deepEqual(out, [
      { base64: 'AAAA', mime: 'image/png' },
      { base64: 'BBBB', mime: 'image/jpeg' },
    ]);
  });

  it('falls back to the legacy imageBase64 shape', () => {
    const out = normalizeImages({ imageBase64: 'AAAA', mime: 'image/heic' });
    assert.deepEqual(out, [{ base64: 'AAAA', mime: 'image/heic' }]);
  });

  it('drops empty / non-string entries silently', () => {
    const out = normalizeImages({
      images: [
        { base64: '' },
        { base64: 'OK' },
        { mime: 'image/png' },         // missing base64
        { base64: 123 as unknown as string }, // wrong type
      ],
    });
    assert.deepEqual(out, [{ base64: 'OK', mime: 'image/jpeg' }]);
  });

  it('returns [] when both shapes are absent', () => {
    assert.deepEqual(normalizeImages({}), []);
  });
});

describe('api/_lib: requirePost', () => {
  it('passes through on POST and returns true', () => {
    const r = mockRes();
    const req = mockReq([], 'POST');
    assert.equal(requirePost(req, r.res), true);
    assert.equal(r.status, null);
  });

  it('rejects non-POST with 405 + "Method not allowed"', () => {
    const r = mockRes();
    const req = mockReq([], 'GET');
    assert.equal(requirePost(req, r.res), false);
    assert.equal(r.status, 405);
    assert.match(r.body, /Method not allowed/);
  });
});

describe('api/_lib: requireGroqKey', () => {
  it('passes through when GROQ_API_KEY is set', () => {
    const prev = process.env.GROQ_API_KEY;
    process.env.GROQ_API_KEY = 'gsk_test';
    const r = mockRes();
    try {
      assert.equal(requireGroqKey(r.res), true);
      assert.equal(r.status, null);
    } finally {
      if (prev === undefined) delete process.env.GROQ_API_KEY;
      else process.env.GROQ_API_KEY = prev;
    }
  });

  it('rejects with 503 service_unavailable when key is absent', () => {
    const prev = process.env.GROQ_API_KEY;
    delete process.env.GROQ_API_KEY;
    const r = mockRes();
    try {
      assert.equal(requireGroqKey(r.res), false);
      assert.equal(r.status, 503);
      assert.match(r.body, /service_unavailable/);
    } finally {
      if (prev !== undefined) process.env.GROQ_API_KEY = prev;
    }
  });
});
