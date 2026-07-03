#!/usr/bin/env node
/**
 * Parses /tmp/off-ingredients.txt (OpenFoodFacts ingredients taxonomy,
 * fetched from openfoodfacts/openfoodfacts-server main branch) and
 * extracts every entry that has both a French name and a
 * ciqual_food_code. Emits a JSON index we can use to anchor our
 * hand-transcribed food-db entries to their official ANSES codes.
 *
 * Taxonomy block format (blocks separated by blank lines):
 *   fr: apple, apples
 *   en: apple
 *   ciqual_food_code:en: 13000
 *   ciqual_food_name:fr: Pomme, pulpe et peau, crue
 *
 * Run:  node tools/extract-ciqual-codes.mjs
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const raw = readFileSync('/tmp/off-ingredients.txt', 'utf8');

// Split on blank lines (possibly preceded by whitespace). Each chunk is
// one taxonomy entry.
const blocks = raw.split(/\n\s*\n/);
const index = {};

for (const block of blocks) {
  // Skip header / comment-only blocks.
  if (!block.match(/ciqual_food_code/)) continue;

  const lines = block.split('\n').map((l) => l.trim());

  const codeLine = lines.find((l) => /^ciqual_food_code:/.test(l));
  if (!codeLine) continue;
  const code = codeLine.split(':').pop().trim();
  if (!/^\d+$/.test(code)) continue;

  const nameFrLine = lines.find((l) => /^ciqual_food_name:fr:/.test(l));
  const nameEnLine = lines.find((l) => /^ciqual_food_name:en:/.test(l));
  const frAliasLine = lines.find((l) => /^fr:/.test(l));
  const enAliasLine = lines.find((l) => /^en:/.test(l));

  const parseAliases = (line) => {
    if (!line) return [];
    const value = line.replace(/^[a-z]+:\s*/, '').replace(/^ciqual_food_name:[a-z]+:\s*/, '');
    return value.split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);
  };

  const entry = {
    ciqual_food_code: code,
    ciqual_food_name_fr: nameFrLine ? nameFrLine.replace(/^ciqual_food_name:fr:\s*/, '') : null,
    ciqual_food_name_en: nameEnLine ? nameEnLine.replace(/^ciqual_food_name:en:\s*/, '') : null,
    fr_aliases: parseAliases(frAliasLine),
    en_aliases: parseAliases(enAliasLine),
  };

  // Index under every French alias (lowercased). If two blocks collide
  // on the same alias we keep the first — the taxonomy orders common
  // foods first.
  for (const alias of entry.fr_aliases) {
    if (!index[alias]) index[alias] = entry;
  }
  // Also under the exact ciqual fr name (lowercased) if not already.
  if (entry.ciqual_food_name_fr) {
    const canon = entry.ciqual_food_name_fr.toLowerCase();
    if (!index[canon]) index[canon] = entry;
  }
}

const outPath = resolve(HERE, '_ciqual-codes.json');
writeFileSync(outPath, JSON.stringify(index, null, 2), 'utf8');
console.log(`[ciqual-codes] ${Object.keys(index).length} aliases → ${new Set(Object.values(index).map((e) => e.ciqual_food_code)).size} unique codes`);
console.log(`[ciqual-codes] wrote ${outPath}`);
