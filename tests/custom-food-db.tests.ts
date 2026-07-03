/**
 * User-custom food DB — persistence + builder tests.
 * localStorage is polyfilled since node:test has none.
 */

import { strict as assert } from 'node:assert';
import { describe, it, beforeEach } from 'node:test';

const store = new Map<string, string>();
(globalThis as { localStorage?: Storage }).localStorage = {
  getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
  setItem: (k: string, v: string) => { store.set(k, v); },
  removeItem: (k: string) => { store.delete(k); },
  clear: () => store.clear(),
  get length() { return store.size; },
  key: (i: number) => Array.from(store.keys())[i] ?? null,
} as Storage;

// @ts-expect-error — plain JS module
import { buildCustomFood, listCustomFoods, saveCustomFood, deleteCustomFood, clearCustomFoods } from '../public/data/custom-food-db.js';

describe('buildCustomFood', () => {
  it('trims the name + clamps negatives to 0 + stamps created_at', () => {
    const f = buildCustomFood({ name: '  Mon pain  ', kcal: -50, protein_g: 7 }, 1700000000000);
    assert.equal(f.name, 'Mon pain');
    assert.equal(f.kcal, 0);
    assert.equal(f.protein_g, 7);
    assert.equal(f.created_at, new Date(1700000000000).toISOString());
    assert.equal(f.custom, true);
    assert.ok(f.id);
  });
  it('drops non-string aliases', () => {
    const f = buildCustomFood({ name: 'x', aliases: ['y', 42 as unknown as string, '', null as unknown as string] });
    assert.deepEqual(f.aliases, ['y']);
  });
});

describe('custom food persistence', () => {
  beforeEach(() => { store.clear(); });

  it('listCustomFoods returns [] when nothing saved', () => {
    assert.deepEqual(listCustomFoods(), []);
  });

  it('saveCustomFood persists and listCustomFoods returns the entry', () => {
    saveCustomFood({ name: 'gâteau maison', kcal: 340, protein_g: 5, carbs_g: 40, fat_g: 18 });
    const all = listCustomFoods();
    assert.equal(all.length, 1);
    assert.equal(all[0].name, 'gâteau maison');
    assert.equal(all[0].kcal, 340);
  });

  it('saveCustomFood updates in place when id matches', () => {
    const entry = saveCustomFood({ name: 'a', kcal: 100 });
    entry.kcal = 150;
    saveCustomFood(entry);
    const all = listCustomFoods();
    assert.equal(all.length, 1);
    assert.equal(all[0].kcal, 150);
  });

  it('deleteCustomFood removes by id', () => {
    const a = saveCustomFood({ name: 'a', kcal: 100 });
    saveCustomFood({ name: 'b', kcal: 200 });
    deleteCustomFood(a.id);
    const all = listCustomFoods();
    assert.equal(all.length, 1);
    assert.equal(all[0].name, 'b');
  });

  it('clearCustomFoods wipes the store', () => {
    saveCustomFood({ name: 'a', kcal: 100 });
    saveCustomFood({ name: 'b', kcal: 200 });
    clearCustomFoods();
    assert.deepEqual(listCustomFoods(), []);
  });

  it('saveCustomFood refuses empty-name entries', () => {
    const r = saveCustomFood({ name: '   ', kcal: 100 });
    assert.equal(r, null);
    assert.deepEqual(listCustomFoods(), []);
  });

  it('tolerates a corrupt localStorage value (returns [])', () => {
    store.set('scanneat.customFoods', '{not valid json');
    assert.deepEqual(listCustomFoods(), []);
  });
});
