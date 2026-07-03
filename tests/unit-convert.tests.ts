/**
 * Unit-conversion tests — cups / tbsp / oz / ml → grams.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS
import { toGrams, parseUnitInput } from '../public/core/unit-convert.js';

describe('toGrams — mass units', () => {
  it('grams pass through', () => {
    assert.equal(toGrams(150, 'g'), 150);
  });
  it('kg → g', () => {
    assert.equal(toGrams(1, 'kg'), 1000);
    assert.equal(toGrams(1.5, 'kg'), 1500);
  });
  it('mg → g with 1-decimal rounding', () => {
    assert.equal(toGrams(500, 'mg'), 0.5);
  });
  it('oz (avoirdupois) → ~28.3g', () => {
    const g = toGrams(1, 'oz') ?? 0;
    assert.ok(Math.abs(g - 28.3) < 0.1, `got ${g}`);
  });
  it('lb → ~453.6g', () => {
    const g = toGrams(1, 'lb') ?? 0;
    assert.ok(Math.abs(g - 453.6) < 0.1, `got ${g}`);
  });
});

describe('toGrams — volume units + density lookup', () => {
  it('cup flour ≈ 125g (US cup × 0.53 g/ml)', () => {
    const g = toGrams(1, 'cup', 'flour') ?? 0;
    assert.ok(Math.abs(g - 125) < 3, `got ${g}`);
  });
  it('cup granulated sugar ≈ 201g', () => {
    const g = toGrams(1, 'cup', 'sugar') ?? 0;
    assert.ok(Math.abs(g - 201) < 3, `got ${g}`);
  });
  it('cup brown sugar ≈ 220g (matches "brown sugar" before "sugar")', () => {
    const g = toGrams(1, 'cup', 'brown sugar') ?? 0;
    assert.ok(Math.abs(g - 220) < 3, `got ${g}`);
  });
  it('tbsp honey ≈ 21g', () => {
    const g = toGrams(1, 'tbsp', 'honey') ?? 0;
    assert.ok(Math.abs(g - 21) < 1, `got ${g}`);
  });
  it('tbsp olive oil ≈ 13.5g', () => {
    const g = toGrams(1, 'tbsp', 'olive oil') ?? 0;
    assert.ok(Math.abs(g - 13.5) < 0.5, `got ${g}`);
  });
  it('fallback to water density when no match (ml for soup)', () => {
    const g = toGrams(250, 'ml', 'mystery liquid') ?? 0;
    assert.equal(g, 250);
  });
  it('accent-folded French names match (farine)', () => {
    const g = toGrams(1, 'cup', 'farine de blé') ?? 0;
    assert.ok(Math.abs(g - 125) < 3, `got ${g}`);
  });
  it('metric cup differs from US cup', () => {
    const us = toGrams(1, 'c-us', 'milk') ?? 0;
    const mt = toGrams(1, 'c-metric', 'milk') ?? 0;
    assert.ok(mt > us, `metric (${mt}) should be bigger than US (${us})`);
  });
});

describe('toGrams — edge cases', () => {
  it('returns null for zero / negative / non-numeric amount', () => {
    assert.equal(toGrams(0, 'cup', 'flour'), null);
    assert.equal(toGrams(-1, 'cup', 'flour'), null);
    assert.equal(toGrams('abc' as unknown as number, 'cup', 'flour'), null);
  });
  it('returns null for unknown unit', () => {
    assert.equal(toGrams(1, 'furlong', 'flour'), null);
  });
  it('serving / piece return null (caller must resolve)', () => {
    assert.equal(toGrams(1, 'serving', 'x'), null);
    assert.equal(toGrams(1, 'piece', 'x'), null);
  });
});

describe('parseUnitInput', () => {
  it('plain number + unit + name', () => {
    assert.deepEqual(parseUnitInput('2 tbsp honey'), { amount: 2, unit: 'tbsp', name: 'honey' });
  });
  it('decimal + grams', () => {
    assert.deepEqual(parseUnitInput('150 g rice'), { amount: 150, unit: 'g', name: 'rice' });
  });
  it('ASCII fraction', () => {
    const out = parseUnitInput('1/2 cup flour');
    assert.equal(out?.amount, 0.5);
    assert.equal(out?.unit, 'cup');
  });
  it('mixed number (whole + ASCII fraction)', () => {
    const out = parseUnitInput('1 1/2 cup sugar');
    assert.equal(out?.amount, 1.5);
  });
  it('vulgar unicode fraction ½', () => {
    const out = parseUnitInput('½ cup butter');
    assert.equal(out?.amount, 0.5);
  });
  it('comma decimal (FR locale)', () => {
    const out = parseUnitInput('1,5 kg farine');
    assert.equal(out?.amount, 1.5);
    assert.equal(out?.unit, 'kg');
  });
  it('French abbrev c.à.s', () => {
    const out = parseUnitInput('2 c.à.s miel');
    assert.equal(out?.unit, 'tbsp');
  });
  it('returns null for garbage', () => {
    assert.equal(parseUnitInput(''), null);
    assert.equal(parseUnitInput('just text'), null);
    assert.equal(parseUnitInput('2 widgets of x'), null);
  });
  it('unit-only (no food name) still parses', () => {
    const out = parseUnitInput('250 ml');
    assert.deepEqual(out, { amount: 250, unit: 'ml', name: '' });
  });
});

describe('parseUnitInput + toGrams round-trip', () => {
  it('"1/2 cup flour" → ~62g', () => {
    const parsed = parseUnitInput('1/2 cup flour')!;
    const g = toGrams(parsed.amount, parsed.unit, parsed.name) ?? 0;
    assert.ok(Math.abs(g - 62.5) < 3, `got ${g}`);
  });
  it('"2 tbsp olive oil" → ~27g', () => {
    const parsed = parseUnitInput('2 tbsp olive oil')!;
    const g = toGrams(parsed.amount, parsed.unit, parsed.name) ?? 0;
    assert.ok(Math.abs(g - 27) < 1, `got ${g}`);
  });
});
