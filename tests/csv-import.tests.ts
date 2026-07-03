/**
 * CSV import — parser tests for MFP + Cronometer schemas.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS module
import { parseCsvLine, detectFormat, parseCsvImport } from '../public/features/csv-import.js';

describe('parseCsvLine', () => {
  it('splits a plain row', () => {
    assert.deepEqual(parseCsvLine('a,b,c'), ['a', 'b', 'c']);
  });
  it('honours quoted fields with commas', () => {
    assert.deepEqual(parseCsvLine('a,"b, with comma",c'), ['a', 'b, with comma', 'c']);
  });
  it('handles escaped quotes inside quoted fields', () => {
    assert.deepEqual(parseCsvLine('a,"she said ""hi""",c'), ['a', 'she said "hi"', 'c']);
  });
  it('preserves empty trailing field', () => {
    assert.deepEqual(parseCsvLine('a,b,'), ['a', 'b', '']);
  });
});

describe('detectFormat', () => {
  it('identifies MFP', () => {
    assert.equal(detectFormat(['Date', 'Meal', 'Item', 'Calories', 'Fat (g)']), 'mfp');
  });
  it('identifies Cronometer', () => {
    assert.equal(detectFormat(['Day', 'Time', 'Group', 'Food Name', 'Energy (kcal)']), 'cronometer');
  });
  it('unknown header falls through', () => {
    assert.equal(detectFormat(['x', 'y', 'z']), 'unknown');
  });
});

describe('parseCsvImport — MFP', () => {
  const MFP = [
    'Date,Meal,Item,Calories,Fat (g),Carbohydrates (g),Protein (g),Sodium (mg),Fiber (g),Sugar (g)',
    '2026-04-22,Breakfast,Oatmeal,150,2.5,27,5,5,4,1',
    '04/22/2026,Lunch,"Salad, mixed",250,12,15,8,300,5,4',
    '2026-04-22,Snack,,80,0,20,1,0,1,15',  // no name → skipped
  ].join('\n');

  it('parses MFP rows + maps meal labels', () => {
    const { format, entries, errors } = parseCsvImport(MFP);
    assert.equal(format, 'mfp');
    assert.equal(entries.length, 2);
    assert.equal(entries[0].meal, 'breakfast');
    assert.equal(entries[0].kcal, 150);
    assert.equal(entries[0].fiber_g, 4);
    assert.equal(entries[1].meal, 'lunch');
    assert.equal(entries[1].product_name, 'Salad, mixed');
    // 300 mg sodium → ~0.75 g salt (300 * 2.5 / 1000)
    assert.equal(entries[1].salt_g, 0.75);
    assert.equal(errors.length, 1); // empty-name row
  });

  it('parses M/D/YYYY date format', () => {
    const { entries } = parseCsvImport(MFP);
    assert.equal(entries[1].date, '2026-04-22');
  });
});

describe('parseCsvImport — Cronometer', () => {
  // Cronometer's real export quotes column names that contain commas
  // ("Iron, Fe (mg)", "B12, Cobalamin (µg)"). The parser handles both
  // quoted forms and the simpler "Iron (mg)" alias we accept.
  const CRON = [
    'Day,Time,Group,Food Name,Energy (kcal),Protein (g),Carbs (g),Fat (g),Fiber (g),"Iron, Fe (mg)",Calcium (mg),Vitamin D (IU),"B12, Cobalamin (µg)"',
    '2026-04-22,08:30,Breakfast,Oatmeal,150,5,27,2.5,4,1.2,80,40,0',
    '2026-04-22,12:30,Lunch,Salmon,300,25,0,20,0,0.5,12,400,5',
  ].join('\n');

  it('parses Cronometer rows with micros', () => {
    const { format, entries } = parseCsvImport(CRON);
    assert.equal(format, 'cronometer');
    assert.equal(entries.length, 2);
    assert.equal(entries[1].meal, 'lunch');
    assert.equal(entries[1].product_name, 'Salmon');
    assert.equal(entries[1].kcal, 300);
    assert.equal(entries[1].iron_mg, 0.5);
    assert.equal(entries[1].calcium_mg, 12);
  });

  it('converts vitamin D IU → µg (1 µg = 40 IU)', () => {
    const { entries } = parseCsvImport(CRON);
    // 400 IU / 40 = 10 µg
    assert.equal(entries[1].vit_d_ug, 10);
  });
});

describe('parseCsvImport — error paths', () => {
  it('returns format=unknown for an unrecognised header', () => {
    const { format, entries, errors } = parseCsvImport('foo,bar\n1,2');
    assert.equal(format, 'unknown');
    assert.equal(entries.length, 0);
    assert.equal(errors.length, 1);
  });

  it('returns empty result for empty input', () => {
    const { format, entries } = parseCsvImport('');
    assert.equal(format, 'unknown');
    assert.equal(entries.length, 0);
  });

  it('skips rows with kcal 0', () => {
    const csv = [
      'Date,Meal,Item,Calories',
      '2026-04-22,Breakfast,Water,0',
      '2026-04-22,Breakfast,Toast,200',
    ].join('\n');
    const { entries } = parseCsvImport(csv);
    assert.equal(entries.length, 1);
    assert.equal(entries[0].product_name, 'Toast');
  });

  it('handles CRLF line endings (Windows exports)', () => {
    const csv = 'Date,Meal,Item,Calories\r\n2026-04-22,Breakfast,Toast,200\r\n';
    const { format, entries } = parseCsvImport(csv);
    assert.equal(format, 'mfp');
    assert.equal(entries.length, 1);
    assert.equal(entries[0].kcal, 200);
  });

  it('handles trailing blank line + BOM', () => {
    const csv = '﻿Date,Meal,Item,Calories\n2026-04-22,Breakfast,Toast,200\n\n';
    const { format, entries } = parseCsvImport(csv);
    assert.equal(format, 'mfp');
    assert.equal(entries.length, 1);
  });

  it('quoted field with embedded comma + quote does not split mid-field', () => {
    const csv = [
      'Date,Meal,Item,Calories',
      '2026-04-22,Breakfast,"Nutella, 15g, ""spoon""",80',
    ].join('\n');
    const { entries } = parseCsvImport(csv);
    assert.equal(entries.length, 1);
    assert.ok(entries[0].product_name.includes('Nutella'));
    assert.equal(entries[0].kcal, 80);
  });
});
