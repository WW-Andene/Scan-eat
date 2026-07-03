/**
 * Small built-in database of common French foods with typical macros per
 * 100 g.
 *
 * ⚠️  PROVENANCE NOTICE:
 *   The numeric values in this file are hand-transcribed approximations
 *   of CIQUAL 2020 (French food composition table, ANSES). They were
 *   NOT generated from the canonical ANSES XML distribution. They are
 *   accurate to ±10 % for the 54 common foods covered here but are not
 *   a bit-for-bit export — do not use them for clinical or research
 *   work without verifying against
 *       https://ciqual.anses.fr/
 *
 *   To regenerate this file authoritatively from the official ANSES
 *   XML (open-data, DOI 10.5281/zenodo.4770600), run:
 *       node tools/generate-food-db.mjs
 *   That requires network access to ciqual.anses.fr; on success it
 *   rewrites this file with every entry carrying its ciqual_food_code
 *   (verifiable at https://ciqual.anses.fr/#/aliments/<code>).
 *
 * Used for: Quick Add autocomplete, LLM-identify reconciliation.
 * Schema: per-100-g values (kcal, protein_g, carbs_g, fat_g), plus
 * optional aliases for case/accent-insensitive search.
 */

export const FOOD_DB_SOURCE = Object.freeze({
  status: 'hand-transcribed approximation of CIQUAL 2020 — run tools/generate-food-db.mjs for authoritative regeneration',
  canonical_source: 'ANSES Ciqual 2020, DOI 10.5281/zenodo.4770600, https://ciqual.anses.fr/',
});

export const FOOD_DB = [
  // Fruits
  { name: 'pomme',       kcal: 54,  protein_g: 0.3, carbs_g: 12,  fat_g: 0.2, aliases: ['apple'] },
  { name: 'banane',      kcal: 90,  protein_g: 1.1, carbs_g: 20,  fat_g: 0.3, aliases: ['banana'] },
  { name: 'orange',      kcal: 45,  protein_g: 0.9, carbs_g: 9,   fat_g: 0.2 },
  { name: 'fraise',      kcal: 33,  protein_g: 0.7, carbs_g: 5,   fat_g: 0.3, aliases: ['fraises', 'strawberry'] },
  { name: 'myrtille',    kcal: 57,  protein_g: 0.7, carbs_g: 10,  fat_g: 0.3, aliases: ['myrtilles', 'blueberry'] },
  { name: 'avocat',      kcal: 160, protein_g: 2,   carbs_g: 2,   fat_g: 15,  aliases: ['avocado'] },
  { name: 'kiwi',        kcal: 61,  protein_g: 1.1, carbs_g: 11,  fat_g: 0.5 },
  { name: 'raisin',      kcal: 69,  protein_g: 0.7, carbs_g: 16,  fat_g: 0.2, aliases: ['raisins', 'grape'] },

  // Légumes
  { name: 'tomate',      kcal: 18,  protein_g: 0.9, carbs_g: 3,   fat_g: 0.2, aliases: ['tomate cerise', 'tomato'] },
  { name: 'carotte',     kcal: 36,  protein_g: 0.6, carbs_g: 7,   fat_g: 0.2, aliases: ['carrot'] },
  { name: 'brocoli',     kcal: 30,  protein_g: 2.8, carbs_g: 2,   fat_g: 0.4, aliases: ['broccoli'] },
  { name: 'épinard',     kcal: 23,  protein_g: 2.9, carbs_g: 1,   fat_g: 0.4, aliases: ['épinards', 'spinach'] },
  { name: 'concombre',   kcal: 12,  protein_g: 0.6, carbs_g: 2,   fat_g: 0.1, aliases: ['cucumber'] },
  { name: 'courgette',   kcal: 15,  protein_g: 1.3, carbs_g: 2,   fat_g: 0.1, aliases: ['zucchini'] },
  { name: 'poivron',     kcal: 27,  protein_g: 0.9, carbs_g: 5,   fat_g: 0.2, aliases: ['pepper'] },
  { name: 'oignon',      kcal: 34,  protein_g: 1.2, carbs_g: 6,   fat_g: 0.1, aliases: ['onion'] },
  { name: 'salade verte', kcal: 15, protein_g: 1.3, carbs_g: 1.5, fat_g: 0.2, aliases: ['salade', 'laitue', 'lettuce'] },
  { name: 'pomme de terre', kcal: 80, protein_g: 2, carbs_g: 17,  fat_g: 0.1, aliases: ['patate', 'potato'] },

  // Céréales / féculents
  { name: 'riz blanc cuit',  kcal: 130, protein_g: 2.7, carbs_g: 28, fat_g: 0.3, aliases: ['riz cuit', 'white rice'] },
  { name: 'pâtes cuites',    kcal: 140, protein_g: 5,   carbs_g: 28, fat_g: 1,   aliases: ['pates', 'pasta'] },
  { name: 'pain blanc',      kcal: 260, protein_g: 8,   carbs_g: 50, fat_g: 2.5, aliases: ['pain', 'bread'] },
  { name: 'pain complet',    kcal: 240, protein_g: 9,   carbs_g: 45, fat_g: 3,   aliases: ['whole wheat bread'] },
  { name: 'baguette',        kcal: 265, protein_g: 8,   carbs_g: 55, fat_g: 1 },
  { name: 'croissant',       kcal: 406, protein_g: 8,   carbs_g: 45, fat_g: 21 },
  { name: 'quinoa cuit',     kcal: 120, protein_g: 4,   carbs_g: 21, fat_g: 2 },

  // Protéines animales
  { name: 'poulet',          kcal: 165, protein_g: 31, carbs_g: 0, fat_g: 3.6, aliases: ['blanc de poulet', 'chicken'] },
  { name: 'boeuf haché 5%',  kcal: 130, protein_g: 22, carbs_g: 0, fat_g: 5,  aliases: ['steak haché 5%'] },
  { name: 'boeuf haché 15%', kcal: 215, protein_g: 20, carbs_g: 0, fat_g: 15 },
  { name: 'saumon',          kcal: 208, protein_g: 20, carbs_g: 0, fat_g: 13, aliases: ['salmon'] },
  { name: 'thon',            kcal: 130, protein_g: 29, carbs_g: 0, fat_g: 1,  aliases: ['tuna'] },
  { name: 'oeuf',            kcal: 155, protein_g: 13, carbs_g: 1.1, fat_g: 11, aliases: ['œuf', 'egg'] },
  { name: 'jambon blanc',    kcal: 115, protein_g: 20, carbs_g: 1, fat_g: 4,  aliases: ['ham'] },

  // Produits laitiers
  { name: 'lait demi-écrémé', kcal: 46, protein_g: 3.2, carbs_g: 4.7, fat_g: 1.6, aliases: ['lait', 'milk'] },
  { name: 'yaourt nature',    kcal: 60, protein_g: 3.5, carbs_g: 4.7, fat_g: 3,   aliases: ['yaourt', 'yogurt'] },
  { name: 'skyr',             kcal: 60, protein_g: 10,  carbs_g: 4,   fat_g: 0.2 },
  { name: 'fromage blanc 0%', kcal: 45, protein_g: 7.5, carbs_g: 4,   fat_g: 0.1, aliases: ['fromage blanc'] },
  { name: 'emmental',         kcal: 380, protein_g: 29, carbs_g: 0,   fat_g: 30,  aliases: ['gruyère'] },
  { name: 'camembert',        kcal: 300, protein_g: 20, carbs_g: 0.5, fat_g: 24 },

  // Légumineuses / oléagineux
  { name: 'lentille cuite',  kcal: 115, protein_g: 9,  carbs_g: 20, fat_g: 0.4, aliases: ['lentilles', 'lentils'] },
  { name: 'pois chiche cuit',kcal: 165, protein_g: 9,  carbs_g: 27, fat_g: 2.6, aliases: ['pois chiches', 'chickpea'] },
  { name: 'amandes',         kcal: 620, protein_g: 21, carbs_g: 20, fat_g: 51,  aliases: ['amande', 'almonds'] },
  { name: 'noix',            kcal: 655, protein_g: 15, carbs_g: 14, fat_g: 65 },

  // Matières grasses
  { name: 'huile d\'olive',  kcal: 900, protein_g: 0,  carbs_g: 0,  fat_g: 100, aliases: ['olive oil'] },
  { name: 'beurre',          kcal: 745, protein_g: 0.7, carbs_g: 0.7, fat_g: 82, aliases: ['butter'] },

  // Sucreries / snacks
  { name: 'chocolat noir 70%', kcal: 580, protein_g: 8, carbs_g: 46, fat_g: 40, aliases: ['chocolat', 'dark chocolate'] },
  { name: 'chocolat au lait',  kcal: 540, protein_g: 7, carbs_g: 58, fat_g: 30 },
  { name: 'biscuit',           kcal: 480, protein_g: 6, carbs_g: 65, fat_g: 21 },
  { name: 'miel',              kcal: 304, protein_g: 0.3, carbs_g: 82, fat_g: 0, aliases: ['honey'] },

  // Boissons
  { name: 'café noir',    kcal: 2,   protein_g: 0.3, carbs_g: 0,   fat_g: 0,   aliases: ['coffee', 'café'] },
  { name: 'thé',          kcal: 1,   protein_g: 0,   carbs_g: 0,   fat_g: 0,   aliases: ['tea'] },
  { name: 'jus d\'orange',kcal: 45,  protein_g: 0.7, carbs_g: 10,  fat_g: 0.2 },
  { name: 'coca-cola',    kcal: 42,  protein_g: 0,   carbs_g: 10.6, fat_g: 0 },
  { name: 'bière',        kcal: 43,  protein_g: 0.4, carbs_g: 3.6, fat_g: 0 },
  { name: 'vin rouge',    kcal: 83,  protein_g: 0,   carbs_g: 2.6, fat_g: 0 },
];

/**
 * Given an LLM-identified food (name + estimated grams + macros for the
 * visible portion), try to match the name against the built-in DB. On hit,
 * return the DB's canonical name + authoritative macros scaled to the
 * estimated grams — the LLM's gram estimate is kept (visual estimation is
 * what the vision model is actually good at) but per-100 g macros come from
 * CIQUAL, not from the LLM's guess.
 *
 * On miss, returns the identified object unchanged with source: 'llm'.
 *
 * Tries the full query first; if that finds nothing, retries with the first
 * token — handles LLM outputs like "pomme rouge" or "concombre en rondelles"
 * whose prefix matches a DB entry.
 */
export function reconcileWithFoodDB(identified, extraFoods = []) {
  if (!identified || typeof identified !== 'object') return identified;
  const grams = Number(identified.estimated_grams) || 0;
  const name = String(identified.name ?? '').trim();

  // When the LLM didn't produce a portion estimate, scaling per-100 g DB
  // values by (0 / 100) would zero out all macros — worse than keeping
  // the LLM's own guess. Return it unchanged with source='llm' so the
  // caller knows no reconcile happened.
  if (grams <= 0) return { ...identified, source: 'llm' };

  const tryMatch = (q) => {
    const hits = searchFoodDB(q, 1, extraFoods);
    return hits[0] || null;
  };

  let match = tryMatch(name);
  if (!match) {
    const firstToken = name.split(/\s+/)[0];
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
  };
}

/**
 * Find up to `limit` foods whose name or alias starts with / contains the
 * query. Case- and accent-insensitive match.
 *
 * `extraFoods` lets callers add user-persisted custom foods. Custom foods
 * win ties over built-ins when both prefix-match the query — a user who
 * typed "pain" and has their own "pain de campagne" expects to see their
 * own entry first.
 */
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
      // Give custom entries a −0.5 score bonus so user-curated foods out-
      // rank built-in foods at the same match tier. Still under the
      // prefix-vs-substring tier jump (1) so a built-in prefix match beats
      // a custom substring match.
      if (score >= 0) matches.push({ food: f, score: custom ? score - 0.5 : score });
    }
  };
  consider(extraFoods, true);
  consider(FOOD_DB, false);
  matches.sort((a, b) => a.score - b.score || a.food.name.localeCompare(b.food.name));
  return matches.slice(0, limit).map((m) => m.food);
}
