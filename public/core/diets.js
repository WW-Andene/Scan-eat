/**
 * Diet definitions — rule-based compliance checks against a product.
 *
 * Each diet has:
 *   - forbidden: regex patterns on ingredient names that disqualify the product
 *   - preferred (optional): regex patterns that earn a small bonus when present
 *   - macro: optional macronutrient constraint (e.g. ketogenic net carbs)
 *   - note: plain-language rationale shown in the personal-score breakdown
 *
 * AUTHORITATIVE / definitional sources:
 *   - Vegan/Vegetarian: Vegan Society UK (vegan) / Vegetarian Society UK
 *     (lacto-ovo-vegetarian) definitions.
 *   - Ketogenic: net carbs <10 %E, Volek & Phinney clinical ketosis
 *     literature; typical threshold ≤10 g net carbs / 100 g solid food.
 *   - Halal: Qur'anic prohibition of pork and intoxicants (haram 5:3, 5:90).
 *     Verification of "halal-certified slaughter" cannot be done from an
 *     ingredient list alone.
 *   - Kosher: Torah prohibition of pork and shellfish (Leviticus 11),
 *     separation of meat and dairy. Certification required for full
 *     compliance, not detectable from ingredient list alone.
 *   - Gluten-free: EU Commission Regulation (EC) 41/2009 — "gluten-free"
 *     requires ≤20 mg/kg gluten. Detection here is on grain ingredients,
 *     not on trace-contamination claims.
 *   - Low-FODMAP: Monash University Low FODMAP Diet App reference lists.
 *
 * ============================================================================
 * Word-boundary note (see public/allergens.js for the full explanation):
 *
 * JavaScript's \b only recognizes ASCII word chars, so patterns wrapped in
 * \b…\b silently failed whenever the match contained a leading or trailing
 * accented letter (écrevisse, crustacé, pâté, etc.). For diet compliance
 * that drives the hard-veto system, this was a shipped bug that could let
 * non-vegetarian products through the vegetarian veto.
 *
 * Fix: the `b(...)` helper below wraps each alternation group with explicit
 * negative lookbehind/lookahead that treat Latin-1 accented letters as
 * word-like, preserving the "whole word" intent in a Unicode-aware way.
 * ============================================================================
 */

// Word-like character class: ASCII + Latin-1 accented letters. The pattern
// means "not preceded / not followed by a letter" — it simulates \b in a way
// that's safe for accented characters.
const W = '[a-zà-ÿ]';
const b = (inner) => new RegExp(`(?<!${W})(?:${inner})(?!${W})`, 'i');

export const DIET_DEFS = {
  none: {
    label_fr: 'Aucun (score classique)',
    label_en: 'None (classic only)',
  },

  vegetarian: {
    label_fr: 'Végétarien',
    label_en: 'Vegetarian',
    forbidden: [
      b(`viande|porc|b[oœ]euf|poulet|dinde|canard|agneau|veau|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|confit|rillette|pat[eé]|foie gras|cro[uû]te de viande|g[eé]lati?ne(?! halal)|pr[eé]sure animale|collag[eè]ne|pepsine`),
      b(`poisson|saumon|thon|cabillaud|sardine|maquereau|anchois|hareng|crustac[eé]|crevette|homard|crabe|hu[iî]tre|moule|calmar|poulpe`),
    ],
    note_fr: 'Exclut viande, poisson, crustacés, mollusques, gélatine et présure animale.',
    note_en: 'Excludes meat, fish, shellfish, molluscs, animal gelatin and rennet.',
  },

  vegan: {
    label_fr: 'Végan',
    label_en: 'Vegan',
    forbidden: [
      // Meat / fish / shellfish
      b(`viande|porc|b[oœ]euf|poulet|dinde|canard|agneau|veau|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|confit|rillette|pat[eé]|foie gras|g[eé]lati?ne(?! v[eé]g[eé]tale)|pr[eé]sure animale|collag[eè]ne|pepsine|isinglass|colle de poisson`),
      b(`poisson|saumon|thon|cabillaud|sardine|maquereau|anchois|hareng|crustac[eé]|crevette|homard|crabe|hu[iî]tre|moule|calmar|poulpe`),
      // Dairy
      b(`lait(?! de (coco|soja|amande|avoine|riz))|lactos[eé]rum|petit[- ]lait|cr[eè]me(?! v[eé]g[eé]tale)|beurre(?! de cacahu[eè]te| d'arachide| de coco)|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]ine|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|beurre clarifi[eé]|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental`),
      // Eggs
      b(`oeufs?|œufs?|jaune d'?oeuf|blanc d'?oeuf|ovalbumine|lysozyme|ovomuco[iï]de`),
      // Bee / insect products
      b(`miel|propolis|gel[eé]e royale|cire d'abeille|beeswax`),
      b(`E90[134]`),     // E901 beeswax, E904 shellac, E903 carnauba (actually plant) — E901/E904 are animal/insect
      b(`E120`),         // carmine
      b(`E542`),         // bone phosphate
      b(`E631|E635`),    // disodium 5'-ribonucleotides — often fish/animal-derived
      b(`cochenille|carmin|phosphate osseux|lanoline`),
    ],
    preferred: [
      b(`v[eé]gan|v-label|vegan|plant-based|100% v[eé]g[eé]tal`),
    ],
    note_fr: 'Exclut tout produit animal : viande, poisson, œufs, lait, miel, colle de poisson (isinglass), phosphate osseux E542, ribonucléotides E631/E635, carmin E120, gélatine, cire d\'abeille E901, shellac E904. Certification V-Label reconnue → bonus.',
    note_en: 'Excludes any animal product: meat, fish, eggs, dairy, honey, isinglass, bone phosphate E542, ribonucleotides E631/E635, carmine E120, gelatin, beeswax E901, shellac E904. V-Label certification → bonus.',
  },

  pescatarian: {
    label_fr: 'Pescétarien',
    label_en: 'Pescatarian',
    forbidden: [
      b(`viande|porc|b[oœ]euf|poulet|dinde|canard|agneau|veau|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|confit|rillette|pat[eé]|foie gras`),
    ],
    note_fr: 'Végétarien autorisant poisson et fruits de mer.',
    note_en: 'Vegetarian that still allows fish and seafood.',
  },

  keto: {
    label_fr: 'Cétogène',
    label_en: 'Ketogenic',
    // No per-ingredient forbidden list; compliance is macronutrient-based.
    macro: {
      max_net_carbs_g_per_100: 10, // common clinical-keto threshold
      min_fat_fraction_of_kcal: 0.60,
    },
    note_fr: 'Très faible en glucides (<10 g nets/100 g), riche en lipides (>60 %E).',
    note_en: 'Very low carb (<10 g net carbs/100 g), high fat (>60 %E).',
  },

  halal: {
    label_fr: 'Halal',
    label_en: 'Halal',
    forbidden: [
      // Pork + derivatives
      b(`porc|cochon|lard|lardon|jambon|saucisson|bacon|salami|chorizo|pepperoni|couenne|boudin|saindoux|suif|andouille|pancetta|pi[eé]tement de porc|pied de porc|oreille de porc|groin|boyau de porc|foie de porc|jambonneau|rosette|coppa|speck|guanciale|mortadelle|hot[- ]dog|nugget`),
      // Ambiguous animal fat (must specify halal source)
      b(`graisse animale(?! halal)|mati[eè]re grasse animale(?! halal)`),
      // Alcohol & derivatives (wine/spirit/beer lineage)
      b(`alcool|[eé]thanol|ethyl alcohol|vin(?! blanc de cuisson sans alcool)|bi[eè]re|biere|liqueur|rhum|whisky|whiskey|gin|vodka|spiritueux|kirsch|marc|cognac|armagnac|calvados|porto|champagne|x[eé]r[eé]s|amaretto|eau[- ]de[- ]vie|saki?|sake|grappa|tequila|mezcal|absinthe|chartreuse|b[eé]n[eé]dictine`),
      // Gelatin / rennet / pepsin — allowed only when explicitly halal or vegetal
      b(`g[eé]lati?ne(?!\\s+(halal|v[eé]g[eé]tale|v[eé]g|de poisson))`),
      b(`pr[eé]sure animale(?! halal)|pepsine(?! halal)`),
      // Animal-derived emulsifiers / mono-diglycerides when source not declared
      // (E471 / E472 can be animal or vegetal — require explicit plant source)
      b(`mono[- ]et diglyc[eé]rides(?! d['']origine v[eé]g[eé]tale)`),
      // Insects / shellac / carmine (debated; conservatively excluded by many halal scholars)
      b(`cochenille|carmin|shellac|gomme[- ]laque`),
      b(`E1(20|200|201|202)`), // E120 carmine, E901-904 waxes handled separately
    ],
    // Any of these in the product name / ingredients counts as certified → preferred bonus
    preferred: [
      b(`halal|certifi[eé] halal|AVS|AFCAI|ARGML|HMC|halal[- ]certified`),
    ],
    note_fr: 'Pas de porc ni dérivés, pas d\'alcool ni dérivés, gélatine/présure doivent être halal ou végétales, émulsifiants (E471/E472) doivent déclarer une origine végétale. Certification reconnue (AVS / AFCAI / HMC) donne un bonus ; elle ne peut pas être vérifiée depuis l\'étiquette seule (méthode d\'abattage).',
    note_en: 'No pork or derivatives, no alcohol or derivatives; gelatin/rennet must be halal or vegetal; emulsifiers (E471/E472) must declare a plant source. Recognized certification (AVS / AFCAI / HMC / HMC-UK) earns a bonus; cannot be fully verified from the label alone (slaughter method).',
  },

  kosher: {
    label_fr: 'Casher',
    label_en: 'Kosher',
    forbidden: [
      b(`porc|cochon|lard|lardon|jambon|saucisson|bacon|salami|chorizo|pepperoni|saindoux|suif|pancetta|couenne`),
      b(`crustac[eé]|crevette|homard|crabe|langouste|langoustine|[eé]crevisse|hu[iî]tre|moule|calmar|encornet|poulpe|p[eé]toncle|palourde|bigorneau|bulot`),
      b(`lapin|li[eè]vre|cheval|sanglier|chameau|autruche`), // non-kosher land animals
      b(`anguille|requin|esturgeon`),                        // fish without scales = treif
    ],
    preferred: [
      b(`casher|kasher|kosher|OU|OK|Star-K|KOF-K|COR|SCS[- ]?Loubavitch`),
    ],
    note_fr: 'Pas de porc, mollusques/crustacés, poissons sans écailles (requin, esturgeon, anguille), viande non ruminante (lapin, cheval). Certification (OU, OK, Star-K, KOF-K) donne un bonus ; la séparation viande-lait et shechita ne peuvent pas être vérifiées depuis l\'étiquette.',
    note_en: 'No pork, no shellfish/molluscs, no scaleless fish (shark, sturgeon, eel), no non-ruminant meat (rabbit, horse). Certification (OU, OK, Star-K, KOF-K) earns a bonus; meat-dairy separation and shechita cannot be verified from the label.',
  },

  gluten_free: {
    label_fr: 'Sans gluten',
    label_en: 'Gluten-free',
    forbidden: [
      b(`bl[eé]|froment|seigle|orge|avoine(?! sans gluten)|[eé]peautre|kamut|triticale|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge`),
    ],
    note_fr: 'Pas de blé, seigle, orge, avoine (sauf certifiée), épeautre, kamut, triticale. Règlement (CE) 41/2009 : ≤20 mg/kg gluten.',
    note_en: 'No wheat, rye, barley, oats (unless certified), spelt, kamut, triticale. EC Regulation 41/2009: ≤20 mg/kg gluten.',
  },

  dairy_free: {
    label_fr: 'Sans lactose / produits laitiers',
    label_en: 'Dairy-free',
    forbidden: [
      b(`lait|lactos[eé]rum|petit[- ]lait|cr[eè]me|beurre|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]ine|lactose|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|beurre clarifi[eé]|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental`),
    ],
    note_fr: 'Exclut tous produits laitiers (lait, crème, beurre, fromage, yaourts, caséine, lactose).',
    note_en: 'Excludes all dairy (milk, cream, butter, cheese, yogurt, casein, lactose).',
  },

  paleo: {
    label_fr: 'Paléo',
    label_en: 'Paleo',
    forbidden: [
      b(`bl[eé]|froment|seigle|orge|avoine|[eé]peautre|kamut|triticale|ma[iï]s|riz|quinoa(?! paleo)|farine de bl[eé]|farine de seigle|malt`),
      b(`lait|lactos[eé]rum|cr[eè]me|beurre|fromage|yaourt|cas[eé]ine|lactose|whey`),
      b(`haricot|lentille|pois chiche|soja|arachide|cacahu[eè]te|f[eè]ve`),
      b(`sucre raffin[eé]|sirop de glucose|sirop de ma[iï]s|maltodextrin`),
    ],
    note_fr: 'Pas de céréales, légumineuses, produits laitiers, ni sucres raffinés. Approximatif depuis une étiquette — Paléo est avant tout un régime "aliment brut".',
    note_en: 'No grains, legumes, dairy, or refined sugars. Label-based detection is approximate — Paleo is primarily a whole-foods diet.',
  },

  low_fodmap: {
    label_fr: 'Pauvre en FODMAP',
    label_en: 'Low-FODMAP',
    forbidden: [
      b(`bl[eé]|froment|seigle|orge|oignon|ail|pomme|poire|mangue|past[eè]que|miel|sirop d'agave|sirop de ma[iï]s|fructose|inuline|chicor[eé]e|artichaut|lait(?! sans lactose)|lactose|fromage frais|yaourt|l[eé]gume sec|haricot|lentille|pois chiche|sorbitol|mannitol|xylitol|maltitol`),
    ],
    note_fr: 'Référence Monash. Exclut oligosaccharides, disaccharides, monosaccharides et polyols mal absorbés. Détection indicative depuis l\'étiquette seulement.',
    note_en: 'Monash University reference. Excludes poorly absorbed oligos, disaccharides, monosaccharides and polyols. Label-based detection is indicative only.',
  },

  mediterranean: {
    label_fr: 'Méditerranéen',
    label_en: 'Mediterranean',
    preferred: [
      b(`huile d'olive|olive|poisson|saumon|sardine|maquereau|thon|noix|amande|noisette|pistache|l[eé]gume|tomate|poivron|aubergine|courgette|l[eé]gumineuse|lentille|pois chiche|haricot|c[eé]r[eé]ale compl[eè]te|bl[eé] complet|avoine|feta|yaourt grec`),
    ],
    note_fr: 'Priorise olive, poisson, légumes, légumineuses, céréales complètes. Pas d\'interdits stricts ; les produits conformes reçoivent un bonus.',
    note_en: 'Prioritises olive, fish, vegetables, legumes, whole grains. No hard bans; compliant products get a bonus.',
  },

  carnivore: {
    label_fr: 'Carnivore',
    label_en: 'Carnivore',
    forbidden: [
      // Everything plant-based
      b(`bl[eé]|farine|sucre|fruit|l[eé]gume|c[eé]r[eé]ale|riz|quinoa|ma[iï]s|haricot|lentille|pois|soja|arachide|ma[iï]s|huile v[eé]g[eé]tale|huile d'olive`),
    ],
    note_fr: 'Exclut tout ingrédient végétal. Détection depuis l\'étiquette seulement.',
    note_en: 'Excludes any plant ingredient. Label-based detection only.',
  },

  custom: {
    label_fr: 'Personnalisé',
    label_en: 'Custom',
    note_fr: 'Définis tes propres motifs interdits ou préférés.',
    note_en: 'Define your own forbidden or preferred patterns.',
  },
};

export const DIET_KEYS = Object.keys(DIET_DEFS);

/**
 * Check a product against a diet.
 * @returns {{ compliant: boolean, violations: string[], preferredHits: string[], reason: string|null }}
 */
export function checkDiet(product, dietKey, customDiet, lang = 'fr') {
  const def = DIET_DEFS[dietKey];
  if (!def || dietKey === 'none') return { compliant: true, violations: [], preferredHits: [], reason: null };

  const violations = [];
  const preferredHits = [];
  const productName = product.name || '';
  // Haystack = product name + every ingredient name joined — certification text
  // ("Certifié halal", "V-Label", "OU") usually appears in the product name,
  // not as an ingredient.
  const haystacks = [productName, ...product.ingredients.map((i) => i.name)];

  const testAny = (re) => haystacks.find((h) => re.test(h));

  // Custom diet: use user-provided patterns.
  if (dietKey === 'custom' && customDiet) {
    for (const pattern of customDiet.forbidden || []) {
      try {
        const re = new RegExp(pattern, 'i');
        const hit = testAny(re);
        if (hit) violations.push(hit);
      } catch { /* bad regex, skip */ }
    }
    for (const pattern of customDiet.preferred || []) {
      try {
        const re = new RegExp(pattern, 'i');
        const hit = testAny(re);
        if (hit) preferredHits.push(hit);
      } catch { /* skip */ }
    }
  } else {
    for (const re of def.forbidden || []) {
      const hit = testAny(re);
      if (hit) violations.push(hit);
    }
    for (const re of def.preferred || []) {
      const hit = testAny(re);
      if (hit) preferredHits.push(hit);
    }
  }

  // Ketogenic: macro-based check.
  if (dietKey === 'keto' && product.nutrition) {
    const netCarbs = (product.nutrition.carbs_g ?? 0) - (product.nutrition.fiber_g ?? 0);
    if (netCarbs > (def.macro?.max_net_carbs_g_per_100 ?? 10)) {
      violations.push(`${netCarbs.toFixed(1)} g net carbs/100 g`);
    }
    const kcal = product.nutrition.energy_kcal || 0;
    const fatKcal = (product.nutrition.fat_g || 0) * 9;
    const fatFrac = kcal > 0 ? fatKcal / kcal : 0;
    if (kcal > 50 && fatFrac < (def.macro?.min_fat_fraction_of_kcal ?? 0.60)) {
      violations.push(`only ${Math.round(fatFrac * 100)} %E from fat`);
    }
  }

  // Certification override — for halal, kosher, vegan, gluten_free, the
  // presence of an explicit certification mark on the product name / ingredient
  // list is evidence that the overall product IS compliant, even when an
  // individual ingredient pattern might have matched (e.g. "gélatine" labeled
  // gélatine halal is often written in a way the regex missed).
  const hasCertification = preferredHits.length > 0;
  const certificationOverride =
    hasCertification && ['halal', 'kosher', 'vegan', 'gluten_free'].includes(dietKey);

  const compliant = certificationOverride || violations.length === 0;
  return {
    compliant,
    violations: certificationOverride ? [] : violations,
    preferredHits,
    certified: hasCertification,
    reason: compliant
      ? null
      : (lang === 'en'
          ? `Not ${def.label_en}: ${violations.slice(0, 3).join(', ')}`
          : `Non ${def.label_fr} : ${violations.slice(0, 3).join(', ')}`),
  };
}
