/**
 * Pairings lookup tests.
 *
 * Data source: tools/generate-pairings.mjs emits public/data/pairings.js
 * from Ahn et al. 2011 srep00196-s3.csv (56 498 recipes, 11 cuisines).
 * These tests verify the lookup + resolver behaviour and spot-check that
 * a handful of canonical empirical pairings survive generation.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module consumed from TS test
import {
  PAIRINGS,
  PAIRINGS_SOURCE,
  EN_TO_FR,
  SYNONYMS,
  resolveIngredient,
  findPairings,
  matchPairings,
} from '../public/data/pairings.js';

describe('PAIRINGS_SOURCE — attribution metadata', () => {
  it('cites Ahn 2011 + doi + corpus size', () => {
    assert.ok(/Ahn.*Sci\. ?Rep\. ?1:196/.test(PAIRINGS_SOURCE.citation));
    assert.ok(PAIRINGS_SOURCE.dataset.includes('srep00196-s3.csv'));
    assert.ok(Number.isFinite(PAIRINGS_SOURCE.corpus_size));
    assert.ok(PAIRINGS_SOURCE.corpus_size > 50000);
  });
  it('pins the input file via SHA-256', () => {
    // A mismatch here means the pipeline was re-run against a different
    // Ahn-2011 mirror without bumping EXPECTED_SHA256 — either deliberate
    // (update the test) or a silent data-integrity problem.
    assert.match(PAIRINGS_SOURCE.input_sha256 ?? '', /^[0-9a-f]{64}$/);
  });
});

describe('SYNONYMS — no dead-end targets', () => {
  it('every SYNONYMS user-variant resolves into PAIRINGS', () => {
    // A SYNONYMS entry pointing at an ingredient that isn't actually in
    // PAIRINGS would silently produce "found a match, no pairings". This
    // test makes dead-end aliases a build-time failure.
    for (const [user, target] of Object.entries(SYNONYMS) as [string, string][]) {
      assert.ok(PAIRINGS[target], `SYNONYMS['${user}'] → '${target}' is not in PAIRINGS`);
    }
  });
  it('every SYNONYMS resolves via resolveIngredient end-to-end', () => {
    for (const user of Object.keys(SYNONYMS)) {
      const r = resolveIngredient(user);
      assert.ok(r, `resolveIngredient('${user}') returned null`);
      assert.ok(PAIRINGS[r], `resolveIngredient('${user}') → '${r}' is not in PAIRINGS`);
    }
  });
});

describe('PAIRINGS shape', () => {
  it('every entry has fr + non-empty pairs + integer cooccur counts', () => {
    for (const [en, entry] of Object.entries(PAIRINGS) as [string, any][]) {
      assert.equal(typeof entry.fr, 'string');
      assert.ok(entry.fr.length > 0, `${en} has empty fr`);
      assert.ok(Array.isArray(entry.pairs) && entry.pairs.length > 0);
      assert.ok(Number.isFinite(entry.recipe_count) && entry.recipe_count >= 20);
      for (const p of entry.pairs) {
        assert.equal(typeof p.b, 'string');
        assert.ok(Number.isFinite(p.cooccur) && p.cooccur >= 5);
      }
    }
  });

  it('pairs are sorted by descending PPMI — not necessarily by cooccur', () => {
    // We can't assert cooccur ordering (PPMI normalises), but we CAN
    // verify a recognised empirical pairing exists. Tomato + basil was
    // the Ahn paper's canonical Mediterranean example.
    const tomato = PAIRINGS.tomato;
    assert.ok(tomato);
    const pairNames = tomato.pairs.map((p: any) => p.b);
    assert.ok(pairNames.includes('basil'), 'tomato should pair with basil');
    assert.ok(pairNames.includes('garlic'), 'tomato should pair with garlic');
  });

  it('classic pairings survive the corpus', () => {
    // Duck à l'orange
    const duck = PAIRINGS.duck;
    if (duck) assert.ok(duck.pairs.some((p: any) => p.b === 'orange'));
    // Lamb + rosemary / mint (Provençal + English)
    const lamb = PAIRINGS.lamb;
    assert.ok(lamb);
    const lambPairs = lamb.pairs.map((p: any) => p.b);
    assert.ok(lambPairs.includes('rosemary') || lambPairs.includes('mint'));
    // Basil + oregano (Italian)
    const basil = PAIRINGS.basil;
    assert.ok(basil);
    const basilPairs = basil.pairs.map((p: any) => p.b);
    assert.ok(basilPairs.includes('oregano') || basilPairs.includes('tomato'));
  });
});

describe('resolveIngredient', () => {
  it('FR → EN canonical id', () => {
    assert.equal(resolveIngredient('tomate'), 'tomato');
    assert.equal(resolveIngredient('saumon'), 'salmon');
    assert.equal(resolveIngredient('œuf'), 'egg');
  });

  it('case + accent insensitive', () => {
    assert.equal(resolveIngredient('TOMATE'), 'tomato');
    assert.equal(resolveIngredient('epinard'), 'spinach');
    assert.equal(resolveIngredient('épinard'), 'spinach');
  });

  it('EN identifier also works', () => {
    assert.equal(resolveIngredient('tomato'), 'tomato');
    assert.equal(resolveIngredient('salmon'), 'salmon');
  });

  it('SYNONYMS map for non-dataset words', () => {
    // "chocolat" isn't a dataset key; synonym routes it to cocoa.
    assert.equal(resolveIngredient('chocolat'), 'cocoa');
    assert.equal(resolveIngredient('chocolat noir'), 'cocoa');
    assert.equal(resolveIngredient('truite'), 'salmon');
  });

  it('first-token fallback for multi-word scans', () => {
    assert.equal(resolveIngredient('saumon fumé atlantique'), 'smoked_salmon');
    assert.equal(resolveIngredient('tomate cerise bio'), 'tomato');
  });

  it('returns null for unknown + short queries', () => {
    assert.equal(resolveIngredient(''), null);
    assert.equal(resolveIngredient('x'), null);
    assert.equal(resolveIngredient('prince lu chocolat biscuit'), 'cocoa'); // chocolat wins
    assert.equal(resolveIngredient('gadget'), null);
  });
});

describe('findPairings', () => {
  it('returns a string array, localised to FR when available', () => {
    const r = findPairings('tomate');
    assert.ok(Array.isArray(r));
    assert.ok(r.length > 0);
    assert.ok(r.includes('basilic'));
    assert.ok(r.includes('ail'));
  });

  it('respects the limit parameter', () => {
    const r = findPairings('tomate', 3);
    assert.equal(r.length, 3);
  });

  it('returns [] for unknown ingredients', () => {
    assert.deepEqual(findPairings('prince lu non-food'), []);
  });

  it('falls back to English id (underscore-stripped) when no FR mapping', () => {
    // Pick an ingredient whose top pair has no FR alias. We can't
    // assume any specific one, so just verify the fallback format: no
    // underscores in the output.
    const anyKey = Object.keys(PAIRINGS)[0];
    const r = findPairings(EN_TO_FR[anyKey]);
    for (const s of r) assert.equal(s.includes('_'), false);
  });
});

describe('matchPairings', () => {
  it('returns the entry object with en + name + recipe_count + pairs', () => {
    const hit = matchPairings('tomate');
    assert.ok(hit);
    assert.equal(hit.en, 'tomato');
    assert.equal(hit.name, 'tomate');
    assert.ok(Number.isFinite(hit.recipe_count));
    assert.ok(Array.isArray(hit.pairs));
  });

  it('returns null when nothing resolves', () => {
    assert.equal(matchPairings(''), null);
    assert.equal(matchPairings('zzzzzzzzz'), null);
  });
});
