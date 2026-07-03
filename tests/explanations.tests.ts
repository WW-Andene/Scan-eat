/**
 * explainFlag() — the regex rules map score reasons to cited plain-
 * language explanations. Silent regressions here would make the score
 * explanations useless; these tests pin a handful of canonical reasons
 * to known non-null explanations.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS
import { explainFlag } from '../public/core/explanations.js';

describe('explainFlag', () => {
  it('returns a non-empty French explanation for a classic reason', () => {
    // Real scoring engine emits reasons like "Saturated fat 12.3 g/100g major".
    // The rules use /^Saturated fat .* major/i — need non-empty fill.
    const out = explainFlag('Saturated fat 12.3 g/100g major', 'fr');
    assert.ok(out && out.length > 0);
    assert.match(out, /EFSA|IARC|WHO|FSA|Monteiro|Srour|Éditorial|Editorial/i);
  });

  it('returns English text when lang=en', () => {
    const out = explainFlag('NOVA class 4', 'en');
    assert.ok(out && out.length > 0);
    // The English ultra-processed rule cites Srour 2019 + Monteiro 2019.
    assert.match(out, /NOVA|Monteiro|Srour/);
  });

  it('returns null for reasons not covered by any rule', () => {
    assert.equal(explainFlag('totally unknown reason', 'fr'), null);
    assert.equal(explainFlag('', 'fr'), null);
  });

  it('defaults to French when lang is missing / invalid', () => {
    assert.equal(explainFlag('x', 'zz'), null);
    const fr = explainFlag('Saturated fat 12.3 g major');
    assert.ok(fr);
    assert.match(fr, /FSA|AGS|Éditorial/i);
  });

  it('editorial entries are explicitly labelled', () => {
    // FR "Saturated fat .* major" rule is editorial + cites FSA threshold.
    const out = explainFlag('Saturated fat 12.3 g major', 'fr');
    assert.ok(out);
    if (out && !out.match(/WHO|EFSA|IARC/i)) {
      assert.match(out, /Éditorial|Editorial|FSA/i);
    }
  });
});
