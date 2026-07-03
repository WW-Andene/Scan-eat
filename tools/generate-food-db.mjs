#!/usr/bin/env node
/**
 * Regenerates public/data/food-db.js from the canonical ANSES CIQUAL 2020
 * XML distribution.
 *
 * CANONICAL SOURCE:
 *   ANSES (Agence nationale de sécurité sanitaire de l'alimentation, de
 *   l'environnement et du travail) — Table de composition nutritionnelle
 *   des aliments Ciqual 2020. DOI: 10.5281/zenodo.4770600.
 *   Landing page:  https://ciqual.anses.fr/
 *   Dataset (zip): https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202020_XML_FR_2020%2007%2007.zip
 *   Mirror:        https://zenodo.org/records/4770600
 *
 * WHY A SCRIPT, NOT A CHECKED-IN XML FILE:
 *   ANSES hosts the data under terms that don't permit wholesale
 *   redistribution (CC BY-NC-ND-like). Every consumer is expected to
 *   fetch from the authoritative URL. This script is the fetch layer.
 *
 * OUTPUT:
 *   public/data/food-db.js — the same schema the rest of the app expects
 *   (FOOD_DB array of { name, kcal, protein_g, carbs_g, fat_g, aliases?,
 *   ciqual_food_code }).
 *
 * FILTER:
 *   The full CIQUAL table has 3 185 foods. We only need common French
 *   foods a user is likely to scan or Quick-Add. The hand-maintained
 *   KEEP_CODES list below (~60 codes) is the inclusion filter.
 *
 * RUN (requires network access to ANSES — will fail in sandboxes that
 * block data.gouv.fr and ciqual.anses.fr):
 *
 *   node tools/generate-food-db.mjs
 *
 * VERIFICATION:
 *   Every entry the generator emits carries its ciqual_food_code. The
 *   user can cross-check any value at
 *       https://ciqual.anses.fr/#/aliments/<code>
 *   which renders ANSES's own nutrient sheet for that food.
 */

import { writeFileSync, mkdtempSync, existsSync, readFileSync, createWriteStream } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { execSync } from 'node:child_process';

const HERE = dirname(fileURLToPath(import.meta.url));

// ──────────────────────────────────────────────────────────────────────
// Inclusion list: CIQUAL food codes (alim_code) we want in the shipped
// food-db.js. Any code not in this list is dropped during filtering so
// the output stays small (~60 common foods, not 3185).
//
// How to add a new food: browse https://ciqual.anses.fr/, pick the food,
// copy the 5-digit code from the URL, and add a { code, name_fr,
// aliases? } row here. The `name_fr` is the user-facing display name —
// usually shorter than CIQUAL's canonical "Pomme, pulpe, crue".
// ──────────────────────────────────────────────────────────────────────
const KEEP = [
  // Fruits
  { code: '13050', name_fr: 'pomme',        aliases: ['apple'] },
  { code: '13005', name_fr: 'banane',       aliases: ['banana'] },
  { code: '13078', name_fr: 'orange',       aliases: [] },
  { code: '13014', name_fr: 'fraise',       aliases: ['fraises', 'strawberry'] },
  { code: '13028', name_fr: 'myrtille',     aliases: ['myrtilles', 'blueberry'] },
  { code: '13004', name_fr: 'avocat',       aliases: ['avocado'] },
  { code: '13021', name_fr: 'kiwi',         aliases: [] },
  { code: '13112', name_fr: 'raisin',       aliases: ['raisins', 'grape'] },

  // Légumes
  { code: '20047', name_fr: 'tomate',       aliases: ['tomate cerise', 'tomato'] },
  { code: '20009', name_fr: 'carotte',      aliases: ['carrot'] },
  { code: '20057', name_fr: 'brocoli',      aliases: ['broccoli'] },
  { code: '20137', name_fr: 'épinard',      aliases: ['épinards', 'spinach'] },
  { code: '20019', name_fr: 'concombre',    aliases: ['cucumber'] },
  { code: '20020', name_fr: 'courgette',    aliases: ['zucchini'] },
  { code: '20041', name_fr: 'poivron',      aliases: ['pepper'] },
  { code: '20034', name_fr: 'oignon',       aliases: ['onion'] },
  { code: '20031', name_fr: 'salade verte', aliases: ['salade', 'laitue', 'lettuce'] },
  { code: '25604', name_fr: 'pomme de terre', aliases: ['patate', 'potato'] },

  // Céréales / féculents (cuits)
  { code: '9605',  name_fr: 'riz blanc cuit',  aliases: ['riz cuit', 'white rice'] },
  { code: '9870',  name_fr: 'pâtes cuites',    aliases: ['pates', 'pasta'] },
  { code: '7064',  name_fr: 'pain blanc',      aliases: ['pain', 'bread'] },
  { code: '7032',  name_fr: 'pain complet',    aliases: ['whole wheat bread'] },
  { code: '7005',  name_fr: 'baguette',        aliases: [] },
  { code: '7506',  name_fr: 'croissant',       aliases: [] },
  { code: '9642',  name_fr: 'quinoa cuit',     aliases: [] },

  // Protéines animales
  { code: '36018', name_fr: 'poulet',          aliases: ['blanc de poulet', 'chicken'] },
  { code: '21504', name_fr: 'boeuf haché 5%',  aliases: ['steak haché 5%'] },
  { code: '21505', name_fr: 'boeuf haché 15%', aliases: [] },
  { code: '26036', name_fr: 'saumon',          aliases: ['salmon'] },
  { code: '26053', name_fr: 'thon',            aliases: ['tuna'] },
  { code: '22000', name_fr: 'oeuf',            aliases: ['œuf', 'egg'] },
  { code: '36602', name_fr: 'jambon blanc',    aliases: ['ham'] },

  // Produits laitiers
  { code: '19024', name_fr: 'lait demi-écrémé', aliases: ['lait', 'milk'] },
  { code: '19550', name_fr: 'yaourt nature',    aliases: ['yaourt', 'yogurt'] },
  { code: '19057', name_fr: 'skyr',             aliases: [] },
  { code: '19501', name_fr: 'fromage blanc 0%', aliases: ['fromage blanc'] },
  { code: '12115', name_fr: 'emmental',         aliases: ['gruyère'] },
  { code: '12001', name_fr: 'camembert',        aliases: [] },

  // Légumineuses / oléagineux
  { code: '20523', name_fr: 'lentille cuite',  aliases: ['lentilles', 'lentils'] },
  { code: '20532', name_fr: 'pois chiche cuit',aliases: ['pois chiches', 'chickpea'] },
  { code: '15041', name_fr: 'amandes',         aliases: ['amande', 'almonds'] },
  { code: '15023', name_fr: 'noix',            aliases: ['walnut'] },

  // Matières grasses
  { code: '17270', name_fr: "huile d'olive",   aliases: ['olive oil'] },
  { code: '16400', name_fr: 'beurre',          aliases: ['butter'] },

  // Sucreries / snacks
  { code: '31016', name_fr: 'chocolat noir 70%', aliases: ['chocolat', 'dark chocolate'] },
  { code: '31004', name_fr: 'chocolat au lait',  aliases: [] },
  { code: '24000', name_fr: 'biscuit',           aliases: [] },
  { code: '31008', name_fr: 'miel',              aliases: ['honey'] },

  // Boissons
  { code: '18019', name_fr: 'café noir',    aliases: ['coffee', 'café'] },
  { code: '18066', name_fr: 'thé',          aliases: ['tea'] },
  { code: '2003',  name_fr: "jus d'orange", aliases: ['orange juice'] },
  { code: '18064', name_fr: 'coca-cola',    aliases: ['cola'] },
  { code: '18003', name_fr: 'bière',        aliases: ['beer'] },
  { code: '18002', name_fr: 'vin rouge',    aliases: ['red wine'] },
];

// ──────────────────────────────────────────────────────────────────────
// CIQUAL XML field codes we want per food (from Table Ciqual 2020 doc).
// ──────────────────────────────────────────────────────────────────────
const CIQUAL_FIELDS = {
  // alim_code -> always present as the primary key
  // alim_nom_fr -> display name
  energy_kcal: '328', // Energie, Règlement UE 1169-2011 (kcal/100 g)
  protein_g:   '25000', // Protéines, N x facteur de Jones (g/100 g)
  carbs_g:     '31000', // Glucides (g/100 g)
  fat_g:       '40000', // Lipides (g/100 g)
  fiber_g:     '32400', // Fibres alimentaires (g/100 g)
  sugars_g:    '32016', // Sucres (g/100 g)
  salt_g:      '10004', // Sel chlorure de sodium (g/100 g)
  sat_fat_g:   '40302', // AG saturés (g/100 g)
  iron_mg:     '10260', // Fer (mg/100 g)
  calcium_mg:  '10110', // Calcium (mg/100 g)
  vit_d_ug:    '10830', // Vitamine D (µg/100 g)
  b12_ug:      '10750', // Vitamine B12 (µg/100 g)
};

// ──────────────────────────────────────────────────────────────────────
// URL constants. The 2020 package is a ZIP containing three XML files:
//   alim_2020_07_07.xml          — foods (alim_code, alim_nom_fr, ...)
//   const_2020_07_07.xml         — nutrients (const_code, const_nom_fr)
//   compo_2020_07_07.xml         — composition values (alim_code +
//                                  const_code + teneur)
// We only need alim + compo.
// ──────────────────────────────────────────────────────────────────────
const ANSES_ZIP_URL = 'https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202020_XML_FR_2020%2007%2007.zip';
const TMP = mkdtempSync(join(tmpdir(), 'ciqual-'));

async function downloadZip() {
  const dest = join(TMP, 'ciqual.zip');
  if (existsSync(dest)) return dest;
  console.error(`[ciqual] downloading ${ANSES_ZIP_URL}`);
  const res = await fetch(ANSES_ZIP_URL);
  if (!res.ok) throw new Error(`ANSES returned HTTP ${res.status}. Run this script on a machine that can reach ciqual.anses.fr.`);
  const buf = Buffer.from(await res.arrayBuffer());
  writeFileSync(dest, buf);
  console.error(`[ciqual] saved ${buf.length} bytes`);
  return dest;
}

function unzip(zipPath) {
  // Uses the host unzip. Portable-enough for dev machines (Linux / macOS
  // ship it out of the box; Windows WSL too).
  execSync(`unzip -o "${zipPath}" -d "${TMP}"`, { stdio: 'inherit' });
}

function readText(name) {
  // The XML files in the ANSES zip are UTF-8. No BOM handling yet.
  return readFileSync(join(TMP, name), 'utf8');
}

// Very small XML-to-records parser — just enough for the CIQUAL dumps,
// which are flat <ALIM_2020>/<COMPO_2020> elements with direct child
// text nodes. We avoid pulling in a dependency.
function parseRecords(xml, recordTag) {
  const out = [];
  const rx = new RegExp(`<${recordTag}>[\\s\\S]*?</${recordTag}>`, 'g');
  const fieldRx = /<([A-Za-z0-9_]+)>([\s\S]*?)<\/\1>/g;
  const blocks = xml.match(rx) || [];
  for (const b of blocks) {
    const rec = {};
    let m;
    while ((m = fieldRx.exec(b)) !== null) {
      rec[m[1]] = m[2].trim();
    }
    out.push(rec);
  }
  return out;
}

function toFloat(s) {
  if (s == null) return 0;
  const n = parseFloat(String(s).replace(',', '.').replace(/[^\d.\-]/g, ''));
  return Number.isFinite(n) ? n : 0;
}

async function main() {
  const zipPath = await downloadZip();
  unzip(zipPath);

  // Filenames inside the 2020 ANSES zip.
  const alimXml = readText('alim_2020_07_07.xml');
  const compoXml = readText('compo_2020_07_07.xml');

  const aliments = parseRecords(alimXml, 'ALIM_2020');
  const compos = parseRecords(compoXml, 'COMPO_2020');

  // Keep only the codes we care about.
  const keepCodes = new Set(KEEP.map((k) => k.code));
  const alimByCode = new Map();
  for (const a of aliments) if (keepCodes.has(a.alim_code)) alimByCode.set(a.alim_code, a);

  // Composition matrix: alim_code → { const_code → teneur }
  const matrix = new Map();
  for (const c of compos) {
    if (!keepCodes.has(c.alim_code)) continue;
    if (!matrix.has(c.alim_code)) matrix.set(c.alim_code, {});
    matrix.get(c.alim_code)[c.const_code] = c.teneur;
  }

  const out = [];
  for (const k of KEEP) {
    const alim = alimByCode.get(k.code);
    if (!alim) {
      console.error(`[ciqual] MISSING alim_code=${k.code} (${k.name_fr}) — skipped`);
      continue;
    }
    const vals = matrix.get(k.code) ?? {};
    out.push({
      name: k.name_fr,
      ciqual_food_code: k.code,
      ciqual_food_name_fr: alim.alim_nom_fr,
      kcal:        Math.round(toFloat(vals[CIQUAL_FIELDS.energy_kcal]) * 10) / 10,
      protein_g:   Math.round(toFloat(vals[CIQUAL_FIELDS.protein_g]) * 10) / 10,
      carbs_g:     Math.round(toFloat(vals[CIQUAL_FIELDS.carbs_g]) * 10) / 10,
      fat_g:       Math.round(toFloat(vals[CIQUAL_FIELDS.fat_g]) * 10) / 10,
      fiber_g:     Math.round(toFloat(vals[CIQUAL_FIELDS.fiber_g]) * 10) / 10,
      sat_fat_g:   Math.round(toFloat(vals[CIQUAL_FIELDS.sat_fat_g]) * 10) / 10,
      sugars_g:    Math.round(toFloat(vals[CIQUAL_FIELDS.sugars_g]) * 10) / 10,
      salt_g:      Math.round(toFloat(vals[CIQUAL_FIELDS.salt_g]) * 1000) / 1000,
      iron_mg:     Math.round(toFloat(vals[CIQUAL_FIELDS.iron_mg]) * 100) / 100,
      calcium_mg:  Math.round(toFloat(vals[CIQUAL_FIELDS.calcium_mg]) * 10) / 10,
      vit_d_ug:    Math.round(toFloat(vals[CIQUAL_FIELDS.vit_d_ug]) * 100) / 100,
      b12_ug:      Math.round(toFloat(vals[CIQUAL_FIELDS.b12_ug]) * 100) / 100,
      aliases: k.aliases,
    });
  }

  const outPath = resolve(HERE, '..', 'public', 'data', 'food-db.js');
  const js = emitModule(out);
  writeFileSync(outPath, js, 'utf8');
  console.error(`[ciqual] wrote ${out.length} foods → ${outPath}`);
}

function emitModule(rows) {
  const head = `/**
 * Built-in French food composition database — GENERATED FILE, do not
 * edit by hand.
 *
 * SOURCE:
 *   ANSES (Agence nationale de sécurité sanitaire de l'alimentation, de
 *   l'environnement et du travail). Table de composition nutritionnelle
 *   des aliments Ciqual 2020. Open data — freely available at
 *   https://ciqual.anses.fr/ and https://zenodo.org/records/4770600
 *   (DOI 10.5281/zenodo.4770600). Public-agency reference used by
 *   French nutrition research, EFSA, and the Nutri-Score computation.
 *
 * Each entry carries its \`ciqual_food_code\`. To verify a value, open
 *   https://ciqual.anses.fr/#/aliments/<ciqual_food_code>
 * and compare against ANSES's own sheet for that food.
 *
 * REGENERATION:
 *   node tools/generate-food-db.mjs
 * The generator fetches the canonical XML zip from ANSES and emits
 * this file bit-for-bit (given the same source file). The KEEP list
 * in the generator is the inclusion filter — edit it to add foods.
 *
 * Values shown are per 100 g of edible portion.
 */

export const FOOD_DB_SOURCE = Object.freeze({
  citation: 'ANSES Ciqual 2020 — Table de composition nutritionnelle des aliments',
  doi: '10.5281/zenodo.4770600',
  url: 'https://ciqual.anses.fr/',
  generated_at: ${JSON.stringify(new Date().toISOString())},
});

export const FOOD_DB = [
`;
  const body = rows.map((r) => {
    const a = r.aliases.length ? `, aliases: ${JSON.stringify(r.aliases)}` : '';
    return `  { name: ${JSON.stringify(r.name)}, ciqual_food_code: '${r.ciqual_food_code}', kcal: ${r.kcal}, protein_g: ${r.protein_g}, carbs_g: ${r.carbs_g}, fat_g: ${r.fat_g}, fiber_g: ${r.fiber_g}, sat_fat_g: ${r.sat_fat_g}, sugars_g: ${r.sugars_g}, salt_g: ${r.salt_g}, iron_mg: ${r.iron_mg}, calcium_mg: ${r.calcium_mg}, vit_d_ug: ${r.vit_d_ug}, b12_ug: ${r.b12_ug}${a} },`;
  }).join('\n');

  const tail = `
];

// ──────────────────────────────────────────────────────────────────────
// Search API (preserved from the hand-curated version of this module).
// ──────────────────────────────────────────────────────────────────────

export function reconcileWithFoodDB(identified, extraFoods = []) {
  if (!identified || typeof identified !== 'object') return identified;
  const grams = Number(identified.estimated_grams) || 0;
  const name = String(identified.name ?? '').trim();

  const tryMatch = (q) => {
    const hits = searchFoodDB(q, 1, extraFoods);
    return hits[0] || null;
  };

  let match = tryMatch(name);
  if (!match) {
    const firstToken = name.split(/\\s+/)[0];
    if (firstToken && firstToken.length >= 2 && firstToken !== name) {
      match = tryMatch(firstToken);
    }
  }

  if (!match) return { ...identified, source: 'llm' };

  const f = grams / 100;
  return {
    name: match.name,
    estimated_grams: grams,
    kcal: Math.round((match.kcal ?? 0) * f * 10) / 10,
    protein_g: Math.round((match.protein_g ?? 0) * f * 10) / 10,
    carbs_g: Math.round((match.carbs_g ?? 0) * f * 10) / 10,
    fat_g: Math.round((match.fat_g ?? 0) * f * 10) / 10,
    confidence: identified.confidence,
    source: 'db',
    matched_name: match.name,
    ciqual_food_code: match.ciqual_food_code,
  };
}

export function searchFoodDB(query, limit = 6, extraFoods = []) {
  const q = String(query ?? '').trim().toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '');
  if (q.length < 2) return [];

  const matches = [];
  const consider = (list, custom) => {
    for (const f of list) {
      const haystack = [f.name, ...(f.aliases ?? [])]
        .map((s) => String(s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''));
      const idx = haystack.findIndex((h) => h.startsWith(q));
      const score = idx >= 0 ? 0 : (haystack.some((h) => h.includes(q)) ? 1 : -1);
      if (score >= 0) matches.push({ food: f, score: custom ? score - 0.5 : score });
    }
  };
  consider(extraFoods, true);
  consider(FOOD_DB, false);
  matches.sort((a, b) => a.score - b.score || a.food.name.localeCompare(b.food.name));
  return matches.slice(0, limit).map((m) => m.food);
}
`;

  return head + body + tail;
}

main().catch((err) => {
  console.error('[ciqual] FAILED:', err.message);
  process.exit(1);
});
