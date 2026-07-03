#!/usr/bin/env node
/**
 * Generates public/data/pairings.js from the Ahn 2011 recipe-co-occurrence
 * dataset (srep00196-s3.csv — 56 498 published recipes from 11 regional
 * cuisines, compiled from allrecipes.com, epicurious.com, and menupan.com).
 *
 * Why recipes, not molecular similarity?
 *   The supplementary srep00196-s2.csv ranks ingredient pairs by
 *   shared volatile flavor compounds. That captures a real, peer-
 *   reviewed signal but — as Ahn et al. themselves document (§3) —
 *   the ranking is dominated by alcoholic beverages and does not
 *   match classic French / Mediterranean culinary intuition. The s3
 *   recipe file captures the COOKBOOK signal: "how often do these two
 *   ingredients appear in the same published recipe?"
 *
 *   We score pairings via positive pointwise mutual information
 *       PPMI(a,b) = max(0, log2( P(a,b) / (P(a) * P(b)) ))
 *   weighted by sqrt(count(a,b)) so a 100-recipe evidence base beats
 *   a 3-recipe coincidence. This is a standard information-retrieval
 *   trick for corpus-derived associations; it suppresses ubiquitous
 *   staples (salt, olive_oil, black_pepper) while rewarding genuine
 *   affinities (tomato+basil, duck+orange, potato+rosemary).
 *
 * Input:  /tmp/flavor-network/s3.csv
 * Output: public/data/pairings.js
 *
 * Run: node tools/generate-pairings.mjs
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

// ──────────────────────────────────────────────────────────────────────
// Integrity pin. The Ahn 2011 supplementary CSV is a 2011 artefact —
// it never changes. We verify the on-disk file matches the checksum of
// the version this generator was written against, so a future run can't
// silently pick up a modified mirror.
//
// Source verified: github.com/lingcheng99/Flavor-Network (mirror of the
// Springer / Nature srep00196-s3 supplementary CSV). To re-anchor to
// the official Springer URL, diff the hash below against the original.
// ──────────────────────────────────────────────────────────────────────
const EXPECTED_SHA256 = '34dfbe5c55eb1b227a161eb654d71012ce1c4cada7670903bd9f3fe1620ccba1';
const INPUT_PATH = '/tmp/flavor-network/s3.csv';

const inputBytes = readFileSync(INPUT_PATH);
const actualSha = createHash('sha256').update(inputBytes).digest('hex');
if (actualSha !== EXPECTED_SHA256) {
  console.error(`[pairings] CHECKSUM MISMATCH on ${INPUT_PATH}`);
  console.error(`[pairings]   expected: ${EXPECTED_SHA256}`);
  console.error(`[pairings]   got:      ${actualSha}`);
  console.error('[pairings] If you intend to regenerate from a newer release of the');
  console.error('[pairings] Ahn 2011 supplementary file, update EXPECTED_SHA256 above');
  console.error('[pairings] AFTER confirming the new file matches the peer-reviewed');
  console.error('[pairings] release at https://www.nature.com/articles/srep00196');
  process.exit(2);
}

// ──────────────────────────────────────────────────────────────────────
// FR ↔ EN aliases. Only ingredients in this map appear in the output.
// Keys are the snake_case identifiers used by the Ahn 2011 dataset.
// ──────────────────────────────────────────────────────────────────────
const FR = {
  // Fruits
  apple: 'pomme', pear: 'poire', banana: 'banane',
  strawberry: 'fraise', raspberry: 'framboise', blueberry: 'myrtille',
  blackberry: 'mûre', cherry: 'cerise', peach: 'pêche',
  apricot: 'abricot', fig: 'figue', grape: 'raisin', pineapple: 'ananas',
  mango: 'mangue', avocado: 'avocat', lemon: 'citron', lime: 'citron vert',
  orange: 'orange', mandarin: 'mandarine', grapefruit: 'pamplemousse',
  melon: 'melon', watermelon: 'pastèque', coconut: 'noix de coco',

  // Légumes + aromates
  tomato: 'tomate', carrot: 'carotte', onion: 'oignon', scallion: 'ciboule',
  garlic: 'ail', shallot: 'échalote', leek: 'poireau', potato: 'pomme de terre',
  sweet_potato: 'patate douce', cucumber: 'concombre', zucchini: 'courgette',
  eggplant: 'aubergine', bell_pepper: 'poivron', cabbage: 'chou',
  broccoli: 'brocoli', cauliflower: 'chou-fleur', spinach: 'épinard',
  lettuce: 'salade verte', celery: 'céleri', mushroom: 'champignon',
  asparagus: 'asperge', fennel: 'fenouil', pumpkin: 'potiron',
  beet: 'betterave', artichoke: 'artichaut', pea: 'petit pois',

  // Herbes + épices
  basil: 'basilic', parsley: 'persil', mint: 'menthe', thyme: 'thym',
  rosemary: 'romarin', sage: 'sauge', oregano: 'origan', dill: 'aneth',
  tarragon: 'estragon', chive: 'ciboulette',
  bay_leaf: 'laurier', cilantro: 'coriandre', cinnamon: 'cannelle',
  clove: 'girofle', ginger: 'gingembre', nutmeg: 'muscade',
  pepper: 'poivre', black_pepper: 'poivre noir', white_pepper: 'poivre blanc',
  vanilla: 'vanille', saffron: 'safran', cumin: 'cumin',
  paprika: 'paprika', turmeric: 'curcuma', cardamom: 'cardamome',
  star_anise: 'anis étoilé', anise: 'anis', mustard: 'moutarde',
  cayenne: 'cayenne', chili_pepper: 'piment',

  // Protéines animales
  beef: 'boeuf', pork: 'porc', lamb: 'agneau', chicken: 'poulet',
  duck: 'canard', turkey: 'dinde', egg: 'œuf', salmon: 'saumon',
  smoked_salmon: 'saumon fumé', tuna: 'thon', shrimp: 'crevette',
  crab: 'crabe', lobster: 'homard', scallop: 'coquille Saint-Jacques',
  anchovy: 'anchois',

  // Féculents / céréales
  rice: 'riz', wheat: 'blé', oat: 'avoine', corn: 'maïs',
  barley: 'orge', buckwheat: 'sarrasin', rye: 'seigle',

  // Légumineuses + oléagineux
  lentil: 'lentille', chickpea: 'pois chiche', soybean: 'soja',
  kidney_bean: 'haricot rouge', almond: 'amandes', walnut: 'noix',
  hazelnut: 'noisette', pistachio: 'pistache', pecan: 'noix de pécan',
  cashew: 'noix de cajou', peanut: 'cacahuète', sesame_seed: 'graines de sésame',

  // Laitier / fromages
  milk: 'lait', cream: 'crème', butter: 'beurre', yogurt: 'yaourt',
  cheese: 'fromage', mozzarella_cheese: 'mozzarella',
  parmesan_cheese: 'parmesan', cheddar_cheese: 'cheddar',
  camembert_cheese: 'camembert', gruyere_cheese: 'gruyère',
  emmental_cheese: 'emmental', feta_cheese: 'feta', goat_cheese: 'chèvre',
  blue_cheese: 'fromage bleu', roquefort_cheese: 'roquefort',
  cottage_cheese: 'fromage blanc', cream_cheese: 'cream cheese',
  ricotta_cheese: 'ricotta',

  // Sucres / douceurs
  honey: 'miel', cocoa: 'cacao', caramel: 'caramel',
  maple_syrup: 'sirop d\'érable', cranberry: 'canneberge',

  // Matières grasses / condiments
  olive_oil: 'huile d\'olive', vegetable_oil: 'huile végétale',
  vinegar: 'vinaigre', balsamic_vinegar: 'vinaigre balsamique',
  soy_sauce: 'sauce soja', fish_sauce: 'sauce poisson',

  // Boissons / alcools
  coffee: 'café', black_tea: 'thé noir', green_tea: 'thé vert',
  white_wine: 'vin blanc', red_wine: 'vin rouge', rum: 'rhum',
  beer: 'bière',

  // Divers + souvent cités dans la co-occurrence (jus, beurres composés,
  // bouillons, levures — pas des ingrédients scannables mais on veut
  // les voir en français dans les paires).
  olive: 'olive', caper: 'câpre', seaweed: 'algue', tamarind: 'tamarin',
  lemon_juice: 'jus de citron', lime_juice: 'jus de citron vert',
  orange_juice: 'jus d\'orange', peanut_butter: 'beurre de cacahuète',
  chicken_broth: 'bouillon de poulet', beef_broth: 'bouillon de bœuf',
  vegetable_broth: 'bouillon de légumes',
  macaroni: 'macaronis', yeast: 'levure', lard: 'saindoux',
  sour_cream: 'crème fraîche', buttermilk: 'babeurre',
  bread: 'pain', flour: 'farine', sugar: 'sucre', salt: 'sel',
  raisin: 'raisin sec', date: 'datte', prune: 'pruneau', fish: 'poisson',
  green_bell_pepper: 'poivron vert', red_bell_pepper: 'poivron rouge',
  chinese_cabbage: 'chou chinois', radish: 'radis', kelp: 'varech',
  lemongrass: 'citronnelle', celery_oil: 'huile de céleri',
  roasted_beef: 'boeuf rôti', bacon: 'bacon',
};

// User-facing synonyms that aren't dataset ingredients themselves —
// resolved to their closest dataset counterpart. Without these, users
// searching "chocolat" would miss the cocoa entry. Only synonyms whose
// target yields pairings are shipped (filtered at emission).
const SYNONYMS = {
  chocolat: 'cocoa', chocolate: 'cocoa',
  'chocolat noir': 'cocoa', 'chocolat au lait': 'cocoa',
  'champignon de paris': 'mushroom',
  'saumon fumé': 'smoked_salmon',
  truite: 'salmon',
  cabillaud: 'fish',
};

// ──────────────────────────────────────────────────────────────────────
// Parse the Ahn recipe CSV (lines beginning with '#' are a header stanza).
// Each data line: cuisine,ingredient1,ingredient2,...
// ──────────────────────────────────────────────────────────────────────
const raw = inputBytes.toString('utf8').split(/\r?\n/);
const recipes = [];
for (const line of raw) {
  if (!line || line.startsWith('#')) continue;
  const parts = line.split(',').map((s) => s.trim()).filter(Boolean);
  if (parts.length < 3) continue;
  const [cuisine, ...ings] = parts;
  if (ings.length === 0) continue;
  recipes.push({ cuisine, ings: [...new Set(ings)] }); // dedupe inside one recipe
}
const N = recipes.length;

// ──────────────────────────────────────────────────────────────────────
// Singleton + pair counts.
// ──────────────────────────────────────────────────────────────────────
const count = new Map();
const pair = new Map();
const pairKey = (a, b) => a < b ? `${a}|${b}` : `${b}|${a}`;

for (const r of recipes) {
  for (const a of r.ings) count.set(a, (count.get(a) || 0) + 1);
  const u = r.ings;
  for (let i = 0; i < u.length; i += 1) {
    for (let j = i + 1; j < u.length; j += 1) {
      const k = pairKey(u[i], u[j]);
      pair.set(k, (pair.get(k) || 0) + 1);
    }
  }
}

// ──────────────────────────────────────────────────────────────────────
// Score pairings via sqrt-weighted PPMI.
//   PMI(a,b) = log2( P(a,b) / (P(a)*P(b)) )
//   PPMI    = max(0, PMI)
//   score   = PPMI(a,b) * sqrt(count(a,b))   — prefers well-evidenced pairs
//
// Minimum co-occurrence threshold: 5 recipes (filters data noise).
// Top N per source ingredient.
// ──────────────────────────────────────────────────────────────────────
const TOP_N = 8;
const MIN_COOCCUR = 5;
const results = new Map(); // en → { pairs: [{b, fr, cooccur, score}] }

for (const src of Object.keys(FR)) {
  const srcCount = count.get(src) || 0;
  if (srcCount < 20) continue; // too rare in the corpus to trust

  const candidates = [];
  for (const other of count.keys()) {
    if (other === src) continue;
    const oCount = count.get(other);
    const cooccur = pair.get(pairKey(src, other)) || 0;
    if (cooccur < MIN_COOCCUR) continue;
    const pAB = cooccur / N;
    const pA = srcCount / N;
    const pB = oCount / N;
    const pmi = Math.log2(pAB / (pA * pB));
    const ppmi = Math.max(0, pmi);
    if (ppmi === 0) continue;
    const score = ppmi * Math.sqrt(cooccur);
    candidates.push({ b: other, cooccur, score });
  }
  candidates.sort((x, y) => y.score - x.score);
  const top = candidates.slice(0, TOP_N);
  if (top.length === 0) continue;

  results.set(src, {
    count: srcCount,
    // Keep `cooccur` so the UI can show "co-cited in N recipes" as a
    // tooltip. We don't ship the raw PPMI score — it's only an ordering
    // aid during generation and adds bytes without informing the user.
    pairs: top.map((p) => ({ b: p.b, fr: FR[p.b] ?? null, cooccur: p.cooccur })),
  });
}

// ──────────────────────────────────────────────────────────────────────
// Emit public/data/pairings.js
// ──────────────────────────────────────────────────────────────────────
const entries = [...results.entries()].sort((a, b) => a[0].localeCompare(b[0]));

const lines = [];
lines.push("/**");
lines.push(" * Empirical ingredient-pairing data.");
lines.push(" *");
lines.push(" * SOURCE DATASET:");
lines.push(" *   Ahn Y-Y, Ahnert SE, Bagrow JP, Barabási A-L.");
lines.push(" *   \"Flavor network and the principles of food pairing.\"");
lines.push(" *   Scientific Reports 1:196 (2011).  doi:10.1038/srep00196");
lines.push(" *   Open-access, peer-reviewed. Supplementary file srep00196-s3.csv");
lines.push(" *   contains 56 498 published recipes from allrecipes.com,");
lines.push(" *   epicurious.com and menupan.com, grouped by 11 regional cuisines.");
lines.push(" *");
lines.push(" * WHY THIS SOURCE, NOT THE MOLECULAR BACKBONE:");
lines.push(" *   Ahn's other supplementary file (srep00196-s2) scores pairs by");
lines.push(" *   shared volatile-flavor-compound counts. That captures a real");
lines.push(" *   signal but is dominated by alcoholic beverages and does not");
lines.push(" *   match classic Mediterranean / French culinary intuition");
lines.push(" *   (Ahn §3 documents this limitation). The recipe-corpus file");
lines.push(" *   captures the actual cookbook signal.");
lines.push(" *");
lines.push(" * SCORING:");
lines.push(" *   score(a,b) = PPMI(a,b) * sqrt(count(a,b))");
lines.push(" *   where PPMI = max(0, log2(P(a,b) / (P(a)*P(b)))) — standard IR");
lines.push(" *   normalisation that suppresses ubiquitous staples (salt, oil,");
lines.push(" *   black_pepper) while rewarding genuine affinities. sqrt weight");
lines.push(" *   prefers pairs with solid evidence bases over rare coincidences.");
lines.push(" *   Minimum co-occurrence threshold: 5 recipes.");
lines.push(" *");
lines.push(" * GENERATED FILE — do not edit by hand. See tools/generate-");
lines.push(" * pairings.mjs. Re-run the generator to refresh.");
lines.push(" */");
lines.push("");
lines.push("export const PAIRINGS_SOURCE = Object.freeze({");
lines.push("  citation: 'Ahn, Ahnert, Bagrow, Barabási — Sci. Rep. 1:196 (2011), doi:10.1038/srep00196',");
lines.push("  dataset: 'srep00196-s3.csv — 56 498 published recipes, 11 cuisines',");
lines.push(`  scoring: 'PPMI(a,b) * sqrt(count(a,b)), min co-occurrence 5 recipes',`);
lines.push(`  input_sha256: ${JSON.stringify(EXPECTED_SHA256)},`);
lines.push(`  generated_at: ${JSON.stringify(new Date().toISOString())},`);
lines.push(`  corpus_size: ${N},`);
lines.push("});");
lines.push("");
lines.push("// English → French display-name map for the shipped ingredients.");
lines.push("export const EN_TO_FR = Object.freeze({");
for (const [en, fr] of Object.entries(FR)) {
  lines.push(`  '${en}': ${JSON.stringify(fr)},`);
}
lines.push("});");
lines.push("");
lines.push("// User-facing synonyms. Map non-dataset terms that users are likely");
lines.push("// to type (\"chocolat\", \"chocolat noir\") to the canonical dataset id");
lines.push("// (\"cocoa\"). Only synonyms whose target is actually present in");
lines.push("// PAIRINGS are shipped — dead-end synonyms would just produce misses.");
lines.push("export const SYNONYMS = Object.freeze({");
for (const [user, target] of Object.entries(SYNONYMS)) {
  if (results.has(target)) {
    lines.push(`  ${JSON.stringify(user)}: '${target}',`);
  }
}
lines.push("});");
lines.push("");
lines.push("// Source ingredient → { fr, pairs[] }. Pairs ordered by PPMI score, desc.");
lines.push("// `cooccur` = number of recipes where both ingredients appear.");
lines.push("export const PAIRINGS = Object.freeze({");
for (const [en, v] of entries) {
  lines.push(`  '${en}': Object.freeze({`);
  lines.push(`    fr: ${JSON.stringify(FR[en])},`);
  lines.push(`    recipe_count: ${v.count},`);
  lines.push(`    pairs: Object.freeze([`);
  for (const p of v.pairs) {
    if (p.fr) {
      lines.push(`      { b: '${p.b}', fr: ${JSON.stringify(p.fr)}, cooccur: ${p.cooccur} },`);
    } else {
      lines.push(`      { b: '${p.b}', fr: null, cooccur: ${p.cooccur} },`);
    }
  }
  lines.push(`    ]),`);
  lines.push(`  }),`);
}
lines.push("});");
lines.push("");
lines.push("const normalize = (s) => String(s ?? '').trim().toLowerCase()");
lines.push("  .normalize('NFD').replace(/[̀-ͯ]/g, '');");
lines.push("");
lines.push("// FR (and EN) → canonical English id. Includes SYNONYMS so that");
lines.push("// common user-typed variants still resolve.");
lines.push("const FR_TO_EN = (() => {");
lines.push("  const m = {};");
lines.push("  for (const [en, fr] of Object.entries(EN_TO_FR)) {");
lines.push("    m[normalize(fr)] = en;");
lines.push("    m[normalize(en)] = en;");
lines.push("  }");
lines.push("  for (const [user, target] of Object.entries(SYNONYMS)) {");
lines.push("    m[normalize(user)] = target;");
lines.push("  }");
lines.push("  return Object.freeze(m);");
lines.push("})();");
lines.push("");
lines.push("/**");
lines.push(" * Resolve a FR or EN name (any case/accents) to the dataset's");
lines.push(" * canonical id. Tokenises the query and tries the largest n-gram");
lines.push(" * window first, so \"saumon fumé atlantique\" matches \"saumon fumé\"");
lines.push(" * (smoked_salmon) rather than collapsing to \"saumon\" (salmon).");
lines.push(" */");
lines.push("export function resolveIngredient(name) {");
lines.push("  const q = normalize(name);");
lines.push("  if (q.length < 2) return null;");
lines.push("  if (FR_TO_EN[q]) return FR_TO_EN[q];");
lines.push("  const tokens = q.split(/\\s+/).filter((t) => t.length >= 2);");
lines.push("  for (let size = tokens.length; size >= 1; size -= 1) {");
lines.push("    for (let start = 0; start + size <= tokens.length; start += 1) {");
lines.push("      const candidate = tokens.slice(start, start + size).join(' ');");
lines.push("      if (FR_TO_EN[candidate]) return FR_TO_EN[candidate];");
lines.push("    }");
lines.push("  }");
lines.push("  return null;");
lines.push("}");
lines.push("");
lines.push("/** Display-ready array of pairing names (FR). Empty on miss. */");
lines.push("export function findPairings(name, limit = 6) {");
lines.push("  const en = resolveIngredient(name);");
lines.push("  if (!en) return [];");
lines.push("  const entry = PAIRINGS[en];");
lines.push("  if (!entry) return [];");
lines.push("  return entry.pairs.slice(0, limit).map((p) => p.fr ?? p.b.replace(/_/g, ' '));");
lines.push("}");
lines.push("");
lines.push("/** Full entry (UI wants the score + cooccur weights too). */");
lines.push("export function matchPairings(name) {");
lines.push("  const en = resolveIngredient(name);");
lines.push("  if (!en) return null;");
lines.push("  const entry = PAIRINGS[en];");
lines.push("  if (!entry) return null;");
lines.push("  return {");
lines.push("    en,");
lines.push("    name: entry.fr ?? en.replace(/_/g, ' '),");
lines.push("    recipe_count: entry.recipe_count,");
lines.push("    pairs: entry.pairs,");
lines.push("  };");
lines.push("}");
lines.push("");

const out = lines.join('\n');
const outPath = resolve(HERE, '..', 'public', 'data', 'pairings.js');
writeFileSync(outPath, out, 'utf8');
console.log(`[pairings] wrote ${outPath}`);
console.log(`[pairings] corpus N=${N} recipes`);
console.log(`[pairings] ${entries.length} source ingredients · ${entries.reduce((n, [, v]) => n + v.pairs.length, 0)} edges`);
