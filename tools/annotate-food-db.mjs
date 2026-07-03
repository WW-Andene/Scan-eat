#!/usr/bin/env node
/**
 * One-shot helper: for each entry in public/data/food-db.js, look up
 * the corresponding ANSES CIQUAL food code via the index built from
 * OpenFoodFacts' ingredients taxonomy
 * (public/data/_ciqual-codes.json — run tools/extract-ciqual-codes.mjs
 * to rebuild).
 *
 * Outputs a JSON array mapping each food-db entry's canonical name to
 * its matched CIQUAL code + CIQUAL food name. Rows with no confident
 * match are flagged so the reviewer can resolve them manually before
 * committing the annotated food-db.
 *
 * Run:  node tools/annotate-food-db.mjs > /tmp/food-db-ciqual.json
 */

import { readFileSync } from 'node:fs';

// @ts-expect-error — plain JS module
import { FOOD_DB } from '../public/data/food-db.js';

import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const idx = JSON.parse(readFileSync(resolve(HERE, '_ciqual-codes.json'), 'utf8'));

const normalize = (s) => String(s ?? '').trim().toLowerCase()
  .normalize('NFD').replace(/[̀-ͯ]/g, '');

function lookup(name, aliases = []) {
  const candidates = [name, ...aliases, ...name.split(/\s+/)].map(normalize);
  // Exact match in index first.
  for (const c of candidates) {
    if (idx[c]) return { match: idx[c], how: `exact:${c}` };
  }
  // Substring match — first index key that contains the query.
  for (const c of candidates) {
    if (c.length < 3) continue;
    for (const k of Object.keys(idx)) {
      if (k.includes(c) || c.includes(k)) {
        return { match: idx[k], how: `fuzzy:${k}` };
      }
    }
  }
  return null;
}

const rows = [];
for (const f of FOOD_DB) {
  const hit = lookup(f.name, f.aliases);
  rows.push({
    name: f.name,
    aliases: f.aliases ?? [],
    kcal: f.kcal,
    ciqual_food_code: hit?.match.ciqual_food_code ?? null,
    ciqual_food_name_fr: hit?.match.ciqual_food_name_fr ?? null,
    match_how: hit?.how ?? 'NONE',
  });
}

const matched = rows.filter((r) => r.ciqual_food_code).length;
console.error(`[annotate] ${matched}/${rows.length} matched to a CIQUAL code`);
console.log(JSON.stringify(rows, null, 2));
