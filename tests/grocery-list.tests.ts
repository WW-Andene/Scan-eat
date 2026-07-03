/**
 * Grocery-list aggregator tests.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module
import { aggregateGroceryList, formatGroceryList } from '../public/features/grocery-list.js';

describe('aggregateGroceryList', () => {
  it('sums grams for the same ingredient across recipes (case + accent insensitive)', () => {
    const out = aggregateGroceryList([
      { name: 'A', components: [
        { product_name: 'tomate', grams: 200 },
        { product_name: 'parmesan', grams: 30 },
      ]},
      { name: 'B', components: [
        { product_name: 'TOMATE', grams: 100 },
        { product_name: 'parmesan', grams: 20 },
      ]},
    ]);
    const tomate = out.find((x) => x.name === 'tomate');
    const parmesan = out.find((x) => x.name === 'parmesan');
    assert.equal(tomate?.grams, 300);
    assert.equal(parmesan?.grams, 50);
    assert.deepEqual(tomate?.sources, ['A', 'B']);
  });

  it('keeps the first-seen casing as the canonical display name', () => {
    const out = aggregateGroceryList([
      { name: 'A', components: [{ product_name: 'Tomate Cerise', grams: 50 }] },
      { name: 'B', components: [{ product_name: 'tomate cerise', grams: 50 }] },
    ]);
    assert.equal(out[0].name, 'Tomate Cerise');
    assert.equal(out[0].grams, 100);
  });

  it('alphabetises output, accent-folded', () => {
    const out = aggregateGroceryList([
      { name: 'X', components: [
        { product_name: 'épinard', grams: 50 },
        { product_name: 'avocat', grams: 50 },
        { product_name: 'banane', grams: 50 },
      ]},
    ]);
    assert.deepEqual(out.map((o) => o.name), ['avocat', 'banane', 'épinard']);
  });

  it('skips empty / missing names', () => {
    const out = aggregateGroceryList([
      { name: 'A', components: [
        { product_name: '', grams: 100 },
        { product_name: '   ', grams: 100 },
        { product_name: 'sel', grams: 5 },
      ]},
    ]);
    assert.equal(out.length, 1);
    assert.equal(out[0].name, 'sel');
  });

  it('handles missing or non-numeric grams as 0', () => {
    const out = aggregateGroceryList([
      { name: 'A', components: [
        { product_name: 'sel' }, // no grams
        { product_name: 'poivre', grams: 'abc' },
      ]},
    ]);
    assert.equal(out.find((o) => o.name === 'sel')?.grams, 0);
    assert.equal(out.find((o) => o.name === 'poivre')?.grams, 0);
  });

  it('returns [] for missing / malformed input', () => {
    assert.deepEqual(aggregateGroceryList(undefined as unknown as []), []);
    assert.deepEqual(aggregateGroceryList([]), []);
    assert.deepEqual(aggregateGroceryList([{ name: 'X' }] as never), []);
  });
});

describe('formatGroceryList', () => {
  it('renders a clipboard-friendly block with grams', () => {
    const text = formatGroceryList([
      { name: 'tomate', grams: 250, sources: [] },
      { name: 'sel', grams: 0, sources: [] },
    ]);
    assert.equal(text, '- tomate · 250 g\n- sel');
  });
});
