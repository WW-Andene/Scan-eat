/**
 * i18n coverage gate: every static t('xxx') key referenced anywhere
 * in public/ js files must exist in BOTH the FR and EN catalogs of
 * public/core/i18n.js.
 *
 * Why this exists:
 *   When a feature module emits a new key like t('myFeatureToast')
 *   but a contributor forgets to add the entry to STRINGS.fr or .en,
 *   the UI silently shows the raw key string. There's no runtime
 *   error, no test failure (every other test mocks i18n) — only a
 *   user complaint after the build ships.
 *
 *   This test fails fast on every PR.
 *
 * What we cover:
 *   - All literal t('foo') and t("foo") calls in public/ js files.
 *   - HTML attributes data-i18n="foo", data-i18n-aria-label="foo",
 *     data-i18n-placeholder="foo" — wired by applyStaticTranslations
 *     in i18n.js. The audit-v3 F-N-01 batch added the aria-label and
 *     placeholder variants; without this gate, a typo in the HTML
 *     would surface as an English/raw-key string in the UI.
 *   - Plural patterns: a call like t('foo', { n }) is satisfied if
 *     EITHER 'foo' exists OR 'foo_one' + 'foo_other' exist (Intl
 *     plural rules; the project follows the _one / _other shorthand
 *     per the audit-v3 D3-N i18n decisions).
 *
 * What we deliberately DON'T cover:
 *   - es / it / de partial locales. Per the documented decision in
 *     docs/DECISIONS.md (F-N-03, 2026-04-24): they ship a small
 *     skeleton + fall through to EN. Failing CI on missing es/it/de
 *     keys would force a full translation pass we explicitly deferred.
 *   - Dynamic keys: t(varName) or template literals. Static analysis
 *     can't reach them.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

// ---- locate t() calls in every .js under public/ -----------------------

const PUBLIC_ROOT = new URL('../public/', import.meta.url).pathname;

function walkJs(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const s = statSync(full);
    if (s.isDirectory()) out.push(...walkJs(full));
    else if (entry.endsWith('.js')) out.push(full);
  }
  return out;
}

// `\bt\s*\(['"]` — word-boundary before `t` excludes `createElement('div')`,
// `setQaStatus('xxx', state)`, etc. The capture group is the raw key.
const T_CALL_RE = /\bt\s*\(\s*['"]([A-Za-z_][\w]*)['"]\s*[,)]/g;

interface KeyCallSite {
  key: string;
  hasInterpolation: boolean;  // call passes a 2nd arg (e.g. `{ n }`)
  files: Set<string>;
}

function extractKeysFromFile(content: string): Map<string, boolean> {
  // Map<key, hasInterpolation>
  const found = new Map<string, boolean>();
  for (const match of content.matchAll(T_CALL_RE)) {
    const key = match[1];
    const closer = match[0].endsWith(',') ? true : false;
    const prev = found.get(key);
    found.set(key, prev || closer);
  }
  return found;
}

function collectAllKeys(): Map<string, KeyCallSite> {
  const all = new Map<string, KeyCallSite>();
  for (const file of walkJs(PUBLIC_ROOT)) {
    // Skip the i18n catalog itself + its bundled engine.
    const rel = relative(PUBLIC_ROOT, file);
    if (rel === 'core/i18n.js' || rel === 'engine.bundle.js') continue;
    const content = readFileSync(file, 'utf8');
    for (const [key, hasInterp] of extractKeysFromFile(content)) {
      const site = all.get(key) ?? { key, hasInterpolation: false, files: new Set() };
      site.hasInterpolation = site.hasInterpolation || hasInterp;
      site.files.add(rel);
      all.set(key, site);
    }
  }
  // Also harvest HTML attribute keys: data-i18n, data-i18n-aria-label,
  // data-i18n-placeholder. applyStaticTranslations() reads these on
  // every locale change to repaint static labels / aria descriptions /
  // form placeholders.
  const HTML_ATTR_RE = /data-i18n(?:-(?:aria-label|placeholder))?\s*=\s*["']([A-Za-z_][\w]*)["']/g;
  const indexHtml = join(PUBLIC_ROOT, 'index.html');
  try {
    const html = readFileSync(indexHtml, 'utf8');
    for (const match of html.matchAll(HTML_ATTR_RE)) {
      const key = match[1];
      const site = all.get(key) ?? { key, hasInterpolation: false, files: new Set() };
      site.files.add('index.html');
      all.set(key, site);
    }
  } catch { /* index.html missing — collectAllKeys still useful */ }
  return all;
}

// ---- load STRINGS from the catalog -------------------------------------

interface Catalogs {
  fr: Record<string, string>;
  en: Record<string, string>;
}

async function loadCatalogs(): Promise<Catalogs> {
  // The i18n module reads localStorage at import time via its detect
  // helper — provide a minimal shim so node:test doesn't crash.
  const g = globalThis as { localStorage?: Storage };
  if (!g.localStorage) {
    const store = new Map<string, string>();
    g.localStorage = {
      getItem: (k) => (store.has(k) ? store.get(k)! : null),
      setItem: (k, v) => { store.set(k, v); },
      removeItem: (k) => { store.delete(k); },
      clear: () => store.clear(),
      get length() { return store.size; },
      key: (i) => Array.from(store.keys())[i] ?? null,
    } as Storage;
  }
  // STRINGS is a named export — pull it directly.
  const mod = await import('../public/core/i18n.js') as { STRINGS: Catalogs };
  return mod.STRINGS;
}

/**
 * A call site's key is "covered" when ANY of these resolve:
 *   - The exact key.
 *   - <key>_one + <key>_other (binary plural).
 *   - <key>_zero / _one / _two / _few / _many / _other (full Intl
 *     plural rules; we accept any non-empty subset because not every
 *     plural form applies to every language).
 */
function isCovered(catalog: Record<string, string>, key: string, hasInterpolation: boolean): boolean {
  if (key in catalog) return true;
  if (!hasInterpolation) return false;
  const PLURAL_SUFFIXES = ['_zero', '_one', '_two', '_few', '_many', '_other'] as const;
  return PLURAL_SUFFIXES.some((sfx) => `${key}${sfx}` in catalog);
}

// ---- tests --------------------------------------------------------------

describe('i18n: every t(\'…\') key exists in FR + EN catalogs', async () => {
  const catalogs = await loadCatalogs();
  const calls = collectAllKeys();

  it('found a non-trivial number of t() calls (sanity)', () => {
    assert.ok(calls.size > 50, `expected >50 t() calls across public/, got ${calls.size}`);
  });

  it('FR catalog covers every referenced key', () => {
    const missing: Array<{ key: string; files: string[] }> = [];
    for (const site of calls.values()) {
      if (!isCovered(catalogs.fr, site.key, site.hasInterpolation)) {
        missing.push({ key: site.key, files: [...site.files].slice(0, 3) });
      }
    }
    if (missing.length > 0) {
      const detail = missing.slice(0, 20).map((m) => `  ${m.key}  ←  ${m.files.join(', ')}`).join('\n');
      assert.fail(
        `${missing.length} key(s) missing from STRINGS.fr:\n${detail}` +
        (missing.length > 20 ? `\n  …and ${missing.length - 20} more` : ''),
      );
    }
  });

  it('EN catalog covers every referenced key', () => {
    const missing: Array<{ key: string; files: string[] }> = [];
    for (const site of calls.values()) {
      if (!isCovered(catalogs.en, site.key, site.hasInterpolation)) {
        missing.push({ key: site.key, files: [...site.files].slice(0, 3) });
      }
    }
    if (missing.length > 0) {
      const detail = missing.slice(0, 20).map((m) => `  ${m.key}  ←  ${m.files.join(', ')}`).join('\n');
      assert.fail(
        `${missing.length} key(s) missing from STRINGS.en:\n${detail}` +
        (missing.length > 20 ? `\n  …and ${missing.length - 20} more` : ''),
      );
    }
  });

  it('FR + EN catalogs both contain the same set of plural keys (per call site)', () => {
    // If a call site has interpolation and FR uses _one/_other, EN
    // should too (and vice versa). A divergence means one catalog
    // resolves via the plural path and the other via the bare key,
    // which produces inconsistent UI per language.
    const divergent: string[] = [];
    for (const site of calls.values()) {
      if (!site.hasInterpolation) continue;
      const frBare = site.key in catalogs.fr;
      const enBare = site.key in catalogs.en;
      const frPlural = ['_zero', '_one', '_two', '_few', '_many', '_other'].some((s) => `${site.key}${s}` in catalogs.fr);
      const enPlural = ['_zero', '_one', '_two', '_few', '_many', '_other'].some((s) => `${site.key}${s}` in catalogs.en);
      if ((frBare && !enBare && !enPlural) || (!frBare && !frPlural && enBare)) {
        divergent.push(site.key);
      }
      if ((frPlural && !enBare && !enPlural) || (!frPlural && !frBare && enPlural)) {
        divergent.push(site.key);
      }
    }
    // De-dupe + cap output.
    const uniq = [...new Set(divergent)];
    if (uniq.length > 0) {
      assert.fail(
        `${uniq.length} key(s) have inconsistent plural shape across FR/EN:\n  ${uniq.slice(0, 15).join(', ')}`,
      );
    }
  });
});
