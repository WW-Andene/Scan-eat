/**
 * ============================================================================
 * FOOD SCORING ENGINE v2.0.0
 * ============================================================================
 *
 * Evidence-anchored scoring engine for supermarket food products.
 * Single-file consolidated build. Zero runtime dependencies.
 *
 * Main entry point: scoreProduct(product: ProductInput): ScoreAudit
 *
 * AUTHORITATIVE vs EDITORIAL boundary — be honest about both:
 *
 *   AUTHORITATIVE (traceable to a named source):
 *     - Nutrient thresholds (FSA traffic-light cutoffs per 100 g for solids).
 *     - Daily intake references (WHO: sat fat <10 %E, free sugars <10 %E /
 *       ideally <5 %E, salt <5 g/day, trans fat elimination target).
 *     - Additive tier assignments are backed per-entry by ADDITIVES_DB.source
 *       (EFSA opinions, IARC Monographs, EU Regulations, cited studies).
 *     - IARC Monograph Vol 114 (2015) — processed meat Group 1.
 *     - NOVA classification framework (Monteiro et al., Public Health Nutrition
 *       22:936–941, 2019).
 *
 *   EDITORIAL (Scan\'eat's own judgment, not a medical authority):
 *     - Pillar weights 20/25/25/15/15 and the A+ → F grade thresholds.
 *     - Category-specific adjustments to sat-fat / sugar thresholds.
 *     - NOVA auto-inference heuristic when input NOVA looks unreliable.
 *     - Additive tier CAP per pillar (−10/−6/−3) and +1/+2/+3 bonus values.
 *     - Veto caps (40 / 45) for the heaviest composition red flags.
 *     - First-ingredient penalty (-3 when sucre / huile dominates).
 *     - Healthy-fat + omega-3 source bonuses.
 *
 * Each function below is annotated with an AUTHORITATIVE-BASIS or EDITORIAL
 * block so the provenance of every number is explicit.
 *
 * Scoring breakdown (100 pts total):
 *   - Processing Level (20)       — EDITORIAL weight; NOVA-based
 *   - Nutritional Density (25)    — EDITORIAL weight; FSA-aligned cutoffs
 *   - Negative Nutrients (25)     — EDITORIAL weight; FSA / WHO cutoffs
 *   - Additive Risk (15)          — EDITORIAL weight; per-entry sourcing
 *   - Ingredient Integrity (15)   — EDITORIAL weight
 *   + Global bonuses (capped +10) — EDITORIAL
 *   - Global penalties            — EDITORIAL
 *   - Veto conditions (cap)       — EDITORIAL
 *
 * Grades: A+ (≥85) / A (≥70) / B (≥55) / C (≥40) / D (≥25) / F (<25)
 *         — EDITORIAL breakpoints. For a published, reproducible grade
 *         anchored to EU law, see the French Nutri-Score algorithm
 *         (not reimplemented here; Scan\'eat is a complementary opinion).
 * ============================================================================
 */

// ============================================================================
// SECTION 1: TYPES
// ============================================================================

export type NovaClass = 1 | 2 | 3 | 4;

export type ProductCategory =
  | 'sandwich'
  | 'ready_meal'
  | 'bread'
  | 'breakfast_cereal'
  | 'yogurt'
  | 'cheese'
  | 'processed_meat'
  | 'fresh_meat'
  | 'fish'
  | 'snack_sweet'
  | 'snack_salty'
  | 'beverage_soft'
  | 'beverage_juice'
  | 'beverage_water'
  | 'condiment'
  | 'oil_fat'
  | 'other';

/**
 * Per-100g nutrition values. All in grams except energy.
 * Use null for unknown/not-declared rather than 0.
 */
export interface NutritionPer100g {
  energy_kcal: number;
  fat_g: number;
  saturated_fat_g: number;
  carbs_g: number;
  sugars_g: number;
  added_sugars_g?: number | null;
  fiber_g: number;
  protein_g: number;
  salt_g: number;
  trans_fat_g?: number | null;
  // Minerals — optional because most OFF products don't report them.
  iron_mg?: number;
  calcium_mg?: number;
  magnesium_mg?: number;
  potassium_mg?: number;
  zinc_mg?: number;
  sodium_mg?: number;
  // Vitamins (µg for fat-soluble + B12, mg for water-soluble except
  // where convention prefers µg).
  vit_a_ug?: number;    // retinol equivalents
  vit_c_mg?: number;
  vit_d_ug?: number;
  vit_e_mg?: number;
  vit_k_ug?: number;
  b1_mg?: number;       // thiamin
  b2_mg?: number;       // riboflavin
  b3_mg?: number;       // niacin
  b6_mg?: number;
  b9_ug?: number;       // folate
  b12_ug?: number;
  // Macro subdivisions — useful for the advanced user + feeding the
  // personal-score engine in future iterations.
  polyunsaturated_fat_g?: number;
  monounsaturated_fat_g?: number;
  omega_3_g?: number;
  omega_6_g?: number;
  cholesterol_mg?: number;
}

/**
 * A single ingredient as parsed from the label.
 */
export interface Ingredient {
  name: string;               // Raw name as on label
  percentage?: number | null; // If declared (e.g., "jambon 17.4%")
  is_whole_food?: boolean;    // Whether it's a recognizable whole food
  e_number?: string | null;   // E250, E451, etc. if it's an additive
  category?: 'food' | 'additive' | 'processing_aid';
}

/**
 * Full product input schema.
 */
export interface ProductInput {
  name: string;
  category: ProductCategory;
  nova_class: NovaClass;
  ingredients: Ingredient[];
  nutrition: NutritionPer100g;

  // Optional metadata
  weight_g?: number;
  origin?: string | null;
  organic?: boolean;
  whole_grain_primary?: boolean;
  fermented?: boolean;
  has_health_claims?: boolean;
  has_misleading_marketing?: boolean;
  named_oils?: boolean; // true if oils are named (e.g., "colza" not "vegetable oil")
  origin_transparent?: boolean;
  /** Vitamin / mineral names declared on-pack or in OFF nutriments. */
  declared_micronutrients?: string[];
  /** Open Food Facts ecoscore, when available. Not part of scoring —
   *  surfaced in the UI alongside the nutritional grade. */
  ecoscore_grade?: 'a' | 'b' | 'c' | 'd' | 'e' | null;
  ecoscore_value?: number | null;
  /** Nutri-Score grade from Open Food Facts. Not a scoring pillar —
   *  used for weight calibration correlation analysis only. */
  nutriscore_grade?: 'a' | 'b' | 'c' | 'd' | 'e' | null;
}

export type Grade = 'A+' | 'A' | 'B' | 'C' | 'D' | 'F';

export type Severity = 'info' | 'minor' | 'moderate' | 'major' | 'critical';

export interface Deduction {
  pillar: string;
  reason: string;
  points: number; // Negative for deduction, positive for bonus
  severity: Severity;
  evidence?: string;
}

export interface PillarScore {
  name: string;
  max: number;
  score: number;
  deductions: Deduction[];
  bonuses: Deduction[];
}

export interface VetoCondition {
  triggered: boolean;
  reason: string;
  cap: number;
}

export interface ScoreAudit {
  product_name: string;
  category: ProductCategory;

  // Final outputs
  score: number;
  grade: Grade;
  verdict: string;

  // Pillar breakdown
  pillars: {
    processing: PillarScore;
    nutritional_density: PillarScore;
    negative_nutrients: PillarScore;
    additive_risk: PillarScore;
    ingredient_integrity: PillarScore;
  };

  // Bonuses & vetoes
  global_bonuses: Deduction[];
  global_penalties: Deduction[];
  veto: VetoCondition;

  // Summary flags for UI
  red_flags: string[];
  green_flags: string[];

  // Fix #2 — side-channel carrying ecoscore context so buildFlags
  // can compose the red/green bullets without coupling to
  // ProductInput. Not a scoring pillar; the numeric score ignores it.
  eco?: { grade?: string; value?: number | null };

  /** Nutri-Score grade passed through from ProductInput for
   *  weight-calibration correlation analysis. Not used in scoring. */
  nutriscore_grade?: 'a' | 'b' | 'c' | 'd' | 'e' | null;

  // Meta
  engine_version: string;
  warnings: string[];
}

// ============================================================================
// SECTION 2: ADDITIVES DATABASE
// ============================================================================
// Based on EFSA reviews, IARC classifications, and current microbiome/CV research.
//
// Tier 1: Serious concern (major evidence of harm or strong mechanistic concern)
// Tier 2: Moderate concern (emerging evidence, precautionary penalty)
// Tier 3: Minor/contextual (mild concern or context-dependent)
// ============================================================================

export type AdditiveTier = 1 | 2 | 3;

export interface AdditiveInfo {
  e_number: string;
  names: string[];
  tier: AdditiveTier;
  category: string;
  /** Plain-language concern text. */
  concern: string;
  /**
   * Authoritative source(s) backing the tier assignment. One of:
   *   "EFSA Scientific Opinion <year>"
   *   "IARC Group X (<year>)"
   *   "EU Regulation 1333/2008 Annex"
   *   "Santé publique France / ANSES <year>"
   *   or a specific primary study (author, journal, year).
   * Entries marked "editorial" are Scan\'eat's judgment without a direct
   * authoritative ruling on that specific additive.
   */
  source: string;
}

export const ADDITIVES_DB: AdditiveInfo[] = [
  // ===== TIER 1: Serious concern (strong authoritative basis) =====
  {
    e_number: 'E249',
    names: ['nitrite de potassium', 'potassium nitrite'],
    tier: 1,
    category: 'preservative',
    concern: 'Curing agent. Processed meat containing nitrites is classified IARC Group 1 (carcinogenic to humans) via N-nitroso compound formation during digestion. Editorial: tier reflects the food-carcinogenicity classification, not the isolated additive.',
    source: 'IARC Monograph Vol 114 (2015) — processed meat Group 1; IARC Monograph Vol 94 (2010) — ingested nitrate/nitrite under conditions that result in endogenous nitrosation Group 2A; EFSA Re-evaluation 2017.',
  },
  {
    e_number: 'E250',
    names: ['nitrite de sodium', 'sodium nitrite'],
    tier: 1,
    category: 'preservative',
    concern: 'Curing agent. Processed meat containing nitrites is classified IARC Group 1 (carcinogenic to humans) via N-nitroso compound formation. Editorial: tier reflects the finished-food classification.',
    source: 'IARC Monograph Vol 114 (2015); IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017.',
  },
  {
    e_number: 'E251',
    names: ['nitrate de sodium', 'sodium nitrate'],
    tier: 1,
    category: 'preservative',
    concern: 'Converts to nitrite in the gut. Same N-nitroso pathway linked to processed-meat carcinogenicity.',
    source: 'IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017.',
  },
  {
    e_number: 'E252',
    names: ['nitrate de potassium', 'potassium nitrate'],
    tier: 1,
    category: 'preservative',
    concern: 'Converts to nitrite in the gut. Same N-nitroso pathway linked to processed-meat carcinogenicity.',
    source: 'IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017.',
  },
  {
    e_number: 'E433',
    names: ['polysorbate 80', 'polysorbate-80'],
    tier: 1,
    category: 'emulsifier',
    concern: 'Detergent-class emulsifier. Mouse studies show microbiome shifts, mucus-layer erosion, and low-grade inflammation at dietary doses. Small human data consistent with the signal; large-scale human evidence still limited.',
    source: 'Chassaing et al., Nature 2015 (mice); Chassaing et al., Gastroenterology 2022 (FRESH crossover human study, n=16, CMC).',
  },
  {
    e_number: 'E466',
    names: ['carboxymethylcellulose', 'cmc', 'cellulose gum'],
    tier: 1,
    category: 'emulsifier',
    concern: 'Detergent-class emulsifier. Mouse microbiome disruption replicated in a controlled human feeding trial (reduced microbial diversity, altered metabolome).',
    source: 'Chassaing et al., Nature 2015; Chassaing et al., Gastroenterology 2022 (FRESH trial, CMC-specific).',
  },

  // ===== TIER 2: Moderate concern =====
  {
    e_number: 'E338',
    names: ['acide phosphorique', 'phosphoric acid'],
    tier: 2,
    category: 'acidulant',
    concern: 'Phosphate additive. Epidemiologic associations between phosphorus-rich diets and cardiovascular / renal outcomes at high chronic intakes.',
    source: 'EFSA Scientific Opinion on phosphates as food additives, EFSA Journal 2019;17(6):5674 (group ADI 40 mg/kg bw/day as phosphorus).',
  },
  {
    e_number: 'E450',
    names: ['diphosphate', 'pyrophosphate'],
    tier: 2,
    category: 'stabilizer',
    concern: 'Phosphate additive (same regulatory group as E338).',
    source: 'EFSA Scientific Opinion 2019;17(6):5674.',
  },
  {
    e_number: 'E451',
    names: ['triphosphate', 'tripolyphosphate'],
    tier: 2,
    category: 'stabilizer',
    concern: 'Phosphate additive (same regulatory group as E338).',
    source: 'EFSA Scientific Opinion 2019;17(6):5674.',
  },
  {
    e_number: 'E452',
    names: ['polyphosphate'],
    tier: 2,
    category: 'stabilizer',
    concern: 'Phosphate additive (same regulatory group as E338).',
    source: 'EFSA Scientific Opinion 2019;17(6):5674.',
  },
  {
    e_number: 'E102',
    names: ['tartrazine', 'jaune de tartrazine'],
    tier: 2,
    category: 'colorant',
    concern: 'Azo dye. Associations with hyperactivity and attention effects in a subset of children. EU foods containing it must carry a warning label.',
    source: 'McCann et al., The Lancet 370:1560–1567 (2007, "Southampton study"); EU Regulation 1333/2008 Annex V — mandatory "may have an adverse effect on activity and attention in children" label.',
  },
  {
    e_number: 'E110',
    names: ['jaune orangé s', 'sunset yellow'],
    tier: 2,
    category: 'colorant',
    concern: 'Azo dye. Same Southampton-study association; EU warning label required.',
    source: 'McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V.',
  },
  {
    e_number: 'E122',
    names: ['azorubine', 'carmoisine'],
    tier: 2,
    category: 'colorant',
    concern: 'Azo dye. Same Southampton-study association; EU warning label required.',
    source: 'McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V.',
  },
  {
    e_number: 'E124',
    names: ['ponceau 4r', 'rouge cochenille a'],
    tier: 2,
    category: 'colorant',
    concern: 'Azo dye. Same Southampton-study association; EU warning label required.',
    source: 'McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V.',
  },
  {
    e_number: 'E129',
    names: ['rouge allura', 'allura red'],
    tier: 2,
    category: 'colorant',
    concern: 'Azo dye. Same Southampton-study association; EU warning label required.',
    source: 'McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V.',
  },
  {
    e_number: 'E320',
    names: ['bha', 'butylhydroxyanisol'],
    tier: 2,
    category: 'antioxidant',
    concern: 'Synthetic antioxidant. Classified IARC Group 2B (possibly carcinogenic) based on rodent forestomach tumours; human relevance debated.',
    source: 'IARC Monograph Vol 40 (1986) — Group 2B; EFSA ADI 1 mg/kg bw/day reaffirmed 2011.',
  },
  {
    e_number: 'E321',
    names: ['bht', 'butylhydroxytoluène'],
    tier: 2,
    category: 'antioxidant',
    concern: 'Synthetic antioxidant used alongside BHA. EFSA set a low ADI on developmental / reproductive grounds.',
    source: 'EFSA Scientific Opinion 2012;10(3):2588 (ADI 0.25 mg/kg bw/day).',
  },
  {
    e_number: 'E951',
    names: ['aspartame'],
    tier: 2,
    category: 'sweetener',
    concern: 'Non-nutritive sweetener. IARC classified as "possibly carcinogenic to humans" (Group 2B) in July 2023 based on limited human evidence for hepatocellular carcinoma. Contraindicated in phenylketonuria.',
    source: 'IARC Monograph Vol 134 (2023) — Group 2B; JECFA 2023 reaffirmed ADI 40 mg/kg bw/day; EFSA ADI 40 mg/kg bw/day (2013).',
  },
  {
    e_number: 'E150c',
    names: ['e150c', 'caramel ammoniacal', 'ammonia caramel'],
    tier: 2,
    category: 'colorant',
    concern: 'Ammonia caramel. Production yields 4-methylimidazole (4-MEI), classified IARC Group 2B (possibly carcinogenic to humans). Found in colas + dark sauces.',
    source: 'IARC Monograph Vol 101 (2013) — 4-MEI Group 2B; EFSA Scientific Opinion 2011;9(3):2004 (ADI 100 mg/kg bw/day for E150c, expressed as caramel, with a sub-ADI of 200 µg/kg bw/day for 4-MEI).',
  },
  {
    e_number: 'E150d',
    names: ['e150d', 'caramel sulfite-ammoniacal', 'sulfite-ammonia caramel'],
    tier: 2,
    category: 'colorant',
    concern: 'Sulfite-ammonia caramel. Same 4-MEI carcinogenicity concern as E150c; the most common caramel sub-class in dark sodas.',
    source: 'IARC Monograph Vol 101 (2013) — 4-MEI Group 2B; EFSA Scientific Opinion 2011;9(3):2004 (ADI 200 mg/kg bw/day for E150d).',
  },
  {
    // Conservative fallback when the label doesn't disambiguate the
    // sub-class. Tagged Tier 2 because EU labelling allows just "E150"
    // and most commercial caramel is c/d in practice.
    e_number: 'E150',
    names: ['caramel', 'colorant caramel'],
    tier: 2,
    category: 'colorant',
    concern: 'Caramel colour, sub-class unspecified. EU labelling allows just "E150"; most commercial caramel in dark sodas + sauces is E150c or E150d, both flagged for 4-MEI (IARC Group 2B). Conservative tier when sub-class is ambiguous.',
    source: 'IARC Monograph Vol 101 (2013); EFSA Scientific Opinion 2011;9(3):2004.',
  },

  // ===== TIER 3: Minor / contextual (authorised, no direct authoritative concern) =====
  {
    e_number: 'E407',
    names: ['carraghénane', 'carrageenan'],
    tier: 3,
    category: 'thickener',
    concern: 'Thickener. EFSA reaffirmed safety at current use levels; animal-study inflammation signals have not been replicated at dietary doses in humans.',
    source: 'EFSA Scientific Opinion 2018;16(4):5238 (ADI 75 mg/kg bw/day).',
  },
  {
    e_number: 'E471',
    names: ['mono- et diglycérides d\'acides gras'],
    tier: 3,
    category: 'emulsifier',
    concern: 'Ubiquitous emulsifier. No specific authoritative concern; tier reflects the class-level emulsifier / UPF marker literature, not a ruling on E471 itself.',
    source: 'EFSA Scientific Opinion 2017;15(11):5045 (no numerical ADI needed).',
  },
  {
    e_number: 'E621',
    names: ['glutamate monosodique', 'monosodium glutamate', 'msg'],
    tier: 3,
    category: 'flavor_enhancer',
    concern: 'Flavour enhancer. EFSA derived a group ADI in 2017 out of caution; JECFA maintains that MSG is safe.',
    source: 'EFSA Scientific Opinion 2017;15(7):4910 (group ADI 30 mg/kg bw/day); JECFA (1988) — no ADI specified.',
  },
  {
    e_number: 'E316',
    names: ['érythorbate de sodium', 'sodium erythorbate'],
    tier: 3,
    category: 'antioxidant',
    concern: 'Vitamin C isomer used in cured meats. Authorised without numerical ADI; its presence often signals a curing system with nitrites.',
    source: 'EFSA Scientific Opinion 2015 (ascorbic acid family).',
  },
  {
    e_number: 'E100',
    names: ['curcumine', 'curcuma (colorant)'],
    tier: 3,
    category: 'colorant',
    concern: 'Natural colorant. Low health concern at food-use levels; presence indicates cosmetic processing.',
    source: 'EFSA Scientific Opinion 2010;8(9):1679 (ADI 3 mg/kg bw/day).',
  },
  {
    e_number: 'E160c',
    names: ['paprika (colorant)', 'extrait de paprika', 'oléorésine de paprika'],
    tier: 3,
    category: 'colorant',
    concern: 'Natural colorant (capsanthin/capsorubin). Low health concern; presence indicates cosmetic processing.',
    source: 'EFSA Scientific Opinion 2015;13(12):4320 (ADI 2 mg/kg bw/day capsanthin).',
  },

  // ===== TIER 1 additions =====
  {
    e_number: 'E171',
    names: ['dioxyde de titane', 'titanium dioxide'],
    tier: 1,
    category: 'colorant',
    concern: 'Banned as a food additive in the EU since August 2022 after EFSA could not establish a safe level on genotoxicity grounds for the nanoparticulate fraction.',
    source: 'EFSA Scientific Opinion 2021;19(5):6585; Commission Regulation (EU) 2022/63 (ban effective August 2022).',
  },
  {
    e_number: 'E220',
    names: ['anhydride sulfureux', 'dioxyde de soufre', 'sulfur dioxide'],
    tier: 1,
    category: 'preservative',
    concern: 'Sulfite — mandatory EU allergen. Established triggers for asthma and sulfite sensitivity at low doses.',
    source: 'EU Regulation 1169/2011 Annex II (mandatory allergen declaration ≥10 mg/kg); EFSA Re-evaluation 2016;14(4):4438.',
  },
  {
    e_number: 'E221',
    names: ['sulfite de sodium', 'sodium sulfite'],
    tier: 1,
    category: 'preservative',
    concern: 'Sulfite — same regulatory allergen classification as E220.',
    source: 'EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016.',
  },
  {
    e_number: 'E223',
    names: ['métabisulfite de sodium', 'sodium metabisulfite'],
    tier: 1,
    category: 'preservative',
    concern: 'Sulfite — same regulatory allergen classification as E220.',
    source: 'EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016.',
  },
  {
    e_number: 'E224',
    names: ['métabisulfite de potassium', 'potassium metabisulfite'],
    tier: 1,
    category: 'preservative',
    concern: 'Sulfite — same regulatory allergen classification as E220.',
    source: 'EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016.',
  },
  {
    e_number: 'E385',
    names: ['edta', 'calcium disodium edta'],
    tier: 1,
    category: 'sequestrant',
    concern: 'Metal chelator. EFSA set a conservative ADI; high chronic intake can affect mineral bioavailability.',
    source: 'EFSA Scientific Opinion 2018;16(11):5007 (ADI 1.9 mg/kg bw/day).',
  },

  // ===== TIER 2 additions =====
  {
    e_number: 'E211',
    names: ['benzoate de sodium', 'sodium benzoate'],
    tier: 2,
    category: 'preservative',
    concern: 'Preservative. Can react with ascorbic acid (vitamin C) to form trace benzene, a known human carcinogen; industry has reformulated many drinks but the risk persists in acidic soft drinks.',
    source: 'Gardner & Lawrence, J Food Prot 2007 (benzene formation mechanism); EFSA Re-evaluation 2016;14(3):4433 (ADI 5 mg/kg bw/day).',
  },
  {
    e_number: 'E212',
    names: ['benzoate de potassium', 'potassium benzoate'],
    tier: 2,
    category: 'preservative',
    concern: 'Same benzene-formation pathway as E211 when combined with vitamin C.',
    source: 'EFSA Re-evaluation 2016;14(3):4433.',
  },
  {
    e_number: 'E950',
    names: ['acésulfame-k', 'acesulfame de potassium', 'acesulfame potassium'],
    tier: 2,
    category: 'sweetener',
    concern: 'Non-nutritive sweetener. Authorised within EFSA ADI; evidence on long-term metabolic outcomes is mixed.',
    source: 'EFSA Re-evaluation 2000 (ADI 9 mg/kg bw/day, reaffirmed in 2011 addendum).',
  },
  {
    e_number: 'E955',
    names: ['sucralose'],
    tier: 2,
    category: 'sweetener',
    concern: 'Non-nutritive sweetener. Decomposes at high temperatures into chlorinated compounds; some studies report microbiome and glucose-response changes that are individually variable.',
    source: 'EFSA Scientific Opinion 2000 (ADI 15 mg/kg bw/day); Schiffman et al., J Toxicol Environ Health B 2013 (heat decomposition); Suez et al., Cell 2022 (microbiome individuality).',
  },
  {
    e_number: 'E954',
    names: ['saccharine', 'saccharin'],
    tier: 2,
    category: 'sweetener',
    concern: 'Non-nutritive sweetener. Older rat bladder-tumour data; IARC downgraded to Group 3 (1999) after human evidence found no clear risk. Tier reflects the lineage of regulatory caution.',
    source: 'IARC Monograph Vol 73 (1999) — Group 3; EFSA Re-evaluation 2018 (ADI 5 mg/kg bw/day).',
  },
  {
    e_number: 'E952',
    names: ['cyclamate', 'cyclamate de sodium'],
    tier: 2,
    category: 'sweetener',
    concern: 'Non-nutritive sweetener. Banned in the US since 1969 on legacy bladder-tumour data; authorised in the EU within an ADI.',
    source: 'EFSA Re-evaluation 2000 (ADI 7 mg/kg bw/day); FDA ban 21 CFR 189.135.',
  },
  {
    e_number: 'E104',
    names: ['jaune de quinoléine', 'quinoline yellow'],
    tier: 2,
    category: 'colorant',
    concern: 'Quinophthalone dye. Behaviour effects in the Southampton study; EU warning label required.',
    source: 'McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V.',
  },
  {
    e_number: 'E127',
    names: ['érythrosine', 'erythrosine'],
    tier: 2,
    category: 'colorant',
    concern: 'Iodine-containing xanthene dye. Low EFSA ADI; thyroid-function concerns for high chronic intake.',
    source: 'EFSA Re-evaluation 2011;9(1):1854 (ADI 0.1 mg/kg bw/day).',
  },
  {
    e_number: 'E173',
    names: ['aluminium'],
    tier: 2,
    category: 'colorant',
    concern: 'Aluminium metallic dye. EFSA set a tolerable weekly intake based on developmental and neurotoxicity endpoints.',
    source: 'EFSA Scientific Opinion 2008;754 (TWI 1 mg/kg bw/week).',
  },
  {
    e_number: 'E339',
    names: ['phosphate de sodium', 'sodium phosphate'],
    tier: 2,
    category: 'stabilizer',
    concern: 'Phosphate additive (same regulatory group as E338).',
    source: 'EFSA Scientific Opinion 2019;17(6):5674.',
  },
  {
    e_number: 'E340',
    names: ['phosphate de potassium', 'potassium phosphate'],
    tier: 2,
    category: 'stabilizer',
    concern: 'Phosphate additive (same regulatory group as E338).',
    source: 'EFSA Scientific Opinion 2019;17(6):5674.',
  },
  {
    e_number: 'E341',
    names: ['phosphate de calcium', 'calcium phosphate'],
    tier: 2,
    category: 'stabilizer',
    concern: 'Phosphate additive (same regulatory group as E338).',
    source: 'EFSA Scientific Opinion 2019;17(6):5674.',
  },
  {
    e_number: 'E1520',
    names: ['propylène glycol', 'propylene glycol'],
    tier: 2,
    category: 'solvent',
    concern: 'Humectant / solvent. EFSA set a low ADI; use in foods is narrowly restricted.',
    source: 'EFSA Scientific Opinion 2018;16(4):5235 (ADI 25 mg/kg bw/day).',
  },

  // ===== TIER 3 additions =====
  {
    e_number: 'E330',
    names: ['acide citrique', 'citric acid'],
    tier: 3,
    category: 'acidulant',
    concern: 'Ubiquitous acidulant, also endogenous in human metabolism. Commercially produced by Aspergillus niger fermentation. No authoritative concern at food-use levels.',
    source: 'EU authorisation without ADI (acceptable intake not specified); natural citrate cycle metabolite.',
  },
  {
    e_number: 'E300',
    names: ['acide ascorbique', 'ascorbic acid', 'vitamine c'],
    tier: 3,
    category: 'antioxidant',
    concern: 'Vitamin C. Used as an antioxidant; no safety concern.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E322',
    names: ['lécithine', 'lecithines', 'lecithin', 'lécithine de soja', 'lécithine de tournesol'],
    tier: 3,
    category: 'emulsifier',
    concern: 'Phospholipid emulsifier from soy or sunflower. No numerical ADI needed. Tier reflects its role as a marker of formulation, not direct concern.',
    source: 'EFSA Scientific Opinion 2017;15(4):4742.',
  },
  {
    e_number: 'E415',
    names: ['gomme xanthane', 'xanthan gum'],
    tier: 3,
    category: 'thickener',
    concern: 'Microbial polysaccharide. EFSA confirmed no safety concern at use levels; some individuals report IBS/IBD flare.',
    source: 'EFSA Scientific Opinion 2017;15(2):4712 (no ADI needed).',
  },
  {
    e_number: 'E412',
    names: ['gomme guar', 'guar gum'],
    tier: 3,
    category: 'thickener',
    concern: 'Legume-derived soluble fibre. No safety concern at use levels.',
    source: 'EFSA Scientific Opinion 2017;15(2):4669 (no ADI needed).',
  },
  {
    e_number: 'E440',
    names: ['pectine', 'pectines', 'pectin'],
    tier: 3,
    category: 'thickener',
    concern: 'Natural plant fibre. No safety concern; acceptable daily intake not specified.',
    source: 'EFSA Scientific Opinion 2017;15(7):4874 (no ADI needed).',
  },
  {
    e_number: 'E202',
    names: ['sorbate de potassium', 'potassium sorbate'],
    tier: 3,
    category: 'preservative',
    concern: 'Mould/yeast preservative. Low concern at authorised levels.',
    source: 'EFSA Re-evaluation 2019;17(3):5626 (group ADI 11 mg/kg bw/day for sorbic acid and its salts).',
  },
  {
    e_number: 'E270',
    names: ['acide lactique', 'lactic acid'],
    tier: 3,
    category: 'acidulant',
    concern: 'Naturally occurring metabolite; no safety concern.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E296',
    names: ['acide malique', 'malic acid'],
    tier: 3,
    category: 'acidulant',
    concern: 'Naturally occurring fruit acid; no safety concern.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E500',
    names: ['bicarbonate de sodium', 'sodium bicarbonate', 'carbonate de sodium'],
    tier: 3,
    category: 'acidity_regulator',
    concern: 'Leavening agent / pH buffer. No safety concern at food-use levels; contributes to sodium intake.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E551',
    names: ['dioxyde de silicium', 'silicon dioxide', 'silice'],
    tier: 3,
    category: 'anticaking',
    concern: 'Anti-caking agent. EFSA 2018 did not conclude a safety concern at use levels but requested further data on nanoparticulate forms.',
    source: 'EFSA Scientific Opinion 2018;16(1):5088.',
  },
  {
    e_number: 'E422',
    names: ['glycérol', 'glycerol', 'glycérine'],
    tier: 3,
    category: 'humectant',
    concern: 'Humectant and sweetener. Metabolised like a carbohydrate; no ADI specified.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E960',
    names: ['glycosides de stéviol', 'steviol glycosides', 'stévia', 'stevia'],
    tier: 3,
    category: 'sweetener',
    concern: 'Plant-derived non-nutritive sweetener. Lower regulatory concern than artificial sweeteners.',
    source: 'EFSA Scientific Opinion 2010;8(4):1537 (ADI 4 mg/kg bw/day, expressed as steviol).',
  },
  {
    e_number: 'E472',
    names: ['esters de mono- et diglycérides', 'esters of monoglycerides'],
    tier: 3,
    category: 'emulsifier',
    concern: 'Family of emulsifiers used in baking and dairy. No numerical ADI; tier reflects UPF class-marker role, not direct concern.',
    source: 'EFSA Scientific Opinion 2017;15(11):5045 (group evaluation with E471).',
  },
  {
    e_number: 'E1422',
    names: ['amidon acétylé', 'acetylated distarch adipate', 'amidon modifié'],
    tier: 3,
    category: 'thickener',
    concern: 'Chemically modified starch. Authorised without numerical ADI.',
    source: 'EU Regulation 1333/2008 — modified starches group without numerical ADI.',
  },
  {
    e_number: 'E1442',
    names: ['phosphate de distarch hydroxypropyle', 'hydroxypropyl distarch phosphate'],
    tier: 3,
    category: 'thickener',
    concern: 'Chemically modified starch. Authorised without numerical ADI.',
    source: 'EU Regulation 1333/2008 — modified starches group.',
  },
  {
    e_number: 'E210',
    names: ['acide benzoïque', 'benzoic acid'],
    tier: 2,
    category: 'preservative',
    concern: 'Parent of E211/E212. Same benzene-formation pathway when combined with vitamin C in acidic foods.',
    source: 'Gardner & Lawrence, J Food Prot 2007; EFSA Re-evaluation 2016;14(3):4433 (group ADI 5 mg/kg bw/day).',
  },
  {
    e_number: 'E475',
    names: ['esters polyglycériques d\'acides gras', 'polyglycerol esters of fatty acids'],
    tier: 2,
    category: 'emulsifier',
    concern: 'Synthetic emulsifier. Authorised within an EFSA ADI. Class-level microbiome concerns (E433, E466) are not directly established for E475; tier reflects extrapolated caution.',
    source: 'EFSA Scientific Opinion 2017;15(12):5089 (ADI 25 mg/kg bw/day).',
  },
  {
    e_number: 'E968',
    names: ['érythritol', 'erythritol'],
    tier: 2,
    category: 'sweetener',
    concern: 'Sugar alcohol. Large prospective cohort + mechanistic study linked higher plasma erythritol to major adverse cardiovascular events; causality still debated.',
    source: 'Witkowski et al., Nature Medicine 29, 710–718 (2023); EFSA Scientific Opinion 2023;21(12):8430 (revised exposure assessment).',
  },
  {
    e_number: 'E905',
    names: ['cire microcristalline', 'microcrystalline wax', 'petroleum wax'],
    tier: 2,
    category: 'glazing',
    concern: 'Petroleum-derived wax. Concerns about migration of mineral-oil-saturated / -aromatic hydrocarbons (MOSH/MOAH) into food.',
    source: 'EFSA Scientific Opinion 2012;10(6):2704 (MOSH/MOAH); EFSA update 2023;21(9):8215.',
  },
  {
    e_number: 'E307',
    names: ['alpha-tocophérol', 'alpha tocopherol', 'tocopherols', 'vitamine e (additif)'],
    tier: 3,
    category: 'antioxidant',
    concern: 'Vitamin E used as an antioxidant. No safety concern.',
    source: 'EFSA Scientific Opinion 2015;13(9):4247 (group evaluation).',
  },
  {
    e_number: 'E331',
    names: ['citrate de sodium', 'sodium citrate', 'citrates de sodium'],
    tier: 3,
    category: 'acidity_regulator',
    concern: 'Citrate buffer. No safety concern.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E333',
    names: ['citrate de calcium', 'calcium citrate'],
    tier: 3,
    category: 'acidity_regulator',
    concern: 'Citrate buffer. No safety concern.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E965',
    names: ['maltitol', 'sirop de maltitol'],
    tier: 3,
    category: 'sweetener',
    concern: 'Sugar alcohol. Moderate glycemic impact; laxative effect above individual tolerance (usually ~20 g).',
    source: 'EU authorisation without ADI; EFSA 2011 opinion on polyol laxative threshold.',
  },
  {
    e_number: 'E967',
    names: ['xylitol'],
    tier: 3,
    category: 'sweetener',
    concern: 'Sugar alcohol. Generally safe; laxative effect above ~50 g. Acutely toxic to dogs (irrelevant to human safety).',
    source: 'EU authorisation without ADI; EFSA 2011 opinion on polyol laxative threshold.',
  },

  // ===== TIER 3: 2026-05-01 batch — common label hits we previously didn't index =====
  // Splitting E150 into a/b/c/d (above) leaves the safer plain + caustic-
  // sulfite sub-classes here as Tier 3 cosmetic-processing signals. The
  // remaining entries fill gaps the audit kept hitting on real OFF data:
  // colorants, acidulants, lactates, flavour enhancers (E627/631/635
  // family), and a few common bulkers.
  {
    e_number: 'E150a',
    names: ['e150a', 'caramel ordinaire', 'plain caramel'],
    tier: 3,
    category: 'colorant',
    concern: 'Plain (no-additive) caramel colour. No 4-MEI concern, but presence still signals cosmetic processing.',
    source: 'EFSA Scientific Opinion 2011;9(3):2004 (no ADI specified for E150a; EU SCF group ADI applies).',
  },
  {
    e_number: 'E150b',
    names: ['e150b', 'caramel caustique', 'caustic-sulfite caramel'],
    tier: 3,
    category: 'colorant',
    concern: 'Caustic-sulfite caramel colour. No 4-MEI concern; sulfite content can be relevant for sulfite-sensitive individuals.',
    source: 'EFSA Scientific Opinion 2011;9(3):2004 (ADI 160 mg/kg bw/day, expressed as caramel solids).',
  },
  {
    e_number: 'E101',
    names: ['riboflavine', 'riboflavin', 'vitamine b2'],
    tier: 3,
    category: 'colorant',
    concern: 'Riboflavin (vitamin B2) used as a yellow colorant. Nutritionally identical to dietary B2; tier reflects cosmetic-processing signal, not safety.',
    source: 'EFSA Scientific Opinion 2013;11(10):3357 (no numerical ADI needed; "not of safety concern at expected use").',
  },
  {
    e_number: 'E160a',
    names: ['caroténoïdes', 'beta-carotène', 'beta carotene', 'carotenes'],
    tier: 3,
    category: 'colorant',
    concern: 'Carotenoid colorants (vitamin A precursor). EFSA flags caution at very high supplemental intakes for smokers, but food-additive use is well below those levels.',
    source: 'EFSA Scientific Opinion 2012;10(3):2593 (β-carotene); EU labelling permitted as colour without an ADI for natural extracts.',
  },
  {
    e_number: 'E163',
    names: ['anthocyanes', 'anthocyanins', 'extrait de peau de raisin'],
    tier: 3,
    category: 'colorant',
    concern: 'Anthocyanin colorants (red/purple, from grape skins, blackcurrant, etc.). Generally regarded as safe; presence signals cosmetic processing.',
    source: 'EFSA Scientific Opinion 2013;11(4):3145 (no numerical ADI considered necessary).',
  },
  {
    e_number: 'E170',
    names: ['carbonate de calcium', 'calcium carbonate'],
    tier: 3,
    category: 'colorant',
    concern: 'Calcium carbonate, used as a white colorant + acidity regulator + calcium-fortification carrier. Identical to dietary calcium chemistry.',
    source: 'EFSA Scientific Opinion 2011;9(7):2318 (ADI not specified, "not of safety concern at use levels").',
  },
  {
    e_number: 'E260',
    names: ['acide acétique', 'acetic acid'],
    tier: 3,
    category: 'acidulant',
    concern: 'Acetic acid (vinegar). Long history of safe use; tier reflects "additive present" signal, not toxicity.',
    source: 'EU authorisation without numerical ADI (quantum satis).',
  },
  {
    e_number: 'E325',
    names: ['lactate de sodium', 'sodium lactate'],
    tier: 3,
    category: 'preservative',
    concern: 'Sodium lactate. Common in deli meats as a moisture / shelf-life agent. Authorised without numerical ADI.',
    source: 'EU Regulation 1333/2008 Annex II (group lactates).',
  },
  {
    e_number: 'E327',
    names: ['lactate de calcium', 'calcium lactate'],
    tier: 3,
    category: 'preservative',
    concern: 'Calcium lactate. Acidity regulator + calcium fortifier; widely used.',
    source: 'EU Regulation 1333/2008 Annex II (group lactates).',
  },
  {
    e_number: 'E332',
    names: ['citrate de potassium', 'potassium citrate'],
    tier: 3,
    category: 'acidulant',
    concern: 'Citrate buffer. No safety concern.',
    source: 'EU authorisation without ADI.',
  },
  {
    e_number: 'E460',
    names: ['cellulose', 'cellulose microcristalline', 'microcrystalline cellulose'],
    tier: 3,
    category: 'thickener',
    concern: 'Cellulose, a plant-fibre derivative. EFSA reaffirmed safety; presence signals processed-food formulation rather than a toxicity concern.',
    source: 'EFSA Scientific Opinion 2018;16(1):5047 (group cellulose ADI not specified).',
  },
  {
    e_number: 'E509',
    names: ['chlorure de calcium', 'calcium chloride'],
    tier: 3,
    category: 'stabilizer',
    concern: 'Calcium chloride. Firming agent for canned vegetables + tofu. No safety concern at use levels.',
    source: 'EU Regulation 1333/2008 Annex II (quantum satis).',
  },
  {
    e_number: 'E575',
    names: ['glucono-delta-lactone', 'glucono delta lactone', 'gdl'],
    tier: 3,
    category: 'acidulant',
    concern: 'Slow-release acidulant used in baked goods + tofu coagulation. Hydrolyses to gluconic acid in the gut.',
    source: 'EU authorisation without numerical ADI.',
  },
  {
    e_number: 'E627',
    names: ['guanylate disodique', 'disodium guanylate'],
    tier: 3,
    category: 'flavor_enhancer',
    concern: 'Nucleotide flavour enhancer, almost always paired with E621 (MSG) for synergistic umami. Authorised without specific ADI; presence signals UPF flavour engineering.',
    source: 'EFSA Scientific Opinion 2017;15(7):4910 (group ribonucleotides + glutamates).',
  },
  {
    e_number: 'E631',
    names: ['inosinate disodique', 'disodium inosinate'],
    tier: 3,
    category: 'flavor_enhancer',
    concern: 'Nucleotide flavour enhancer, paired with MSG for umami synergy. Often derived from animal sources (fish, meat) — relevant for vegetarian / vegan diets.',
    source: 'EFSA Scientific Opinion 2017;15(7):4910.',
  },
  {
    e_number: 'E635',
    names: ['ribonucléotides disodiques', 'disodium ribonucleotides'],
    tier: 3,
    category: 'flavor_enhancer',
    concern: 'Disodium ribonucleotides (inosinate + guanylate mix). Same umami-synergy + UPF-marker context as E631 / E627.',
    source: 'EFSA Scientific Opinion 2017;15(7):4910.',
  },
  {
    e_number: 'E901',
    names: ['cire d\'abeille', 'beeswax'],
    tier: 3,
    category: 'glazing_agent',
    concern: 'Glazing agent (apple shine, confectionery coatings). Authorised; not vegan.',
    source: 'EFSA Scientific Opinion 2007 (re-evaluated; no ADI specified).',
  },
  {
    e_number: 'E920',
    names: ['l-cystéine', 'l cysteine', 'cystéine', 'cysteine'],
    tier: 3,
    category: 'flour_treatment',
    concern: 'Dough conditioner used in industrial bakery. Often derived from animal hair / feathers — relevant for vegetarian / vegan diets even though synthetic + microbial sources exist.',
    source: 'EU Regulation 1333/2008 Annex II (industrial bakery quantum satis).',
  },
];

/**
 * Cosmetic additive categories (trigger processing penalty).
 */
export const COSMETIC_ADDITIVE_CATEGORIES = new Set([
  'colorant',
  'flavor_enhancer',
  'artificial_sweetener',
]);

/**
 * Normalize a string for matching (lowercase, strip accents, collapse spaces).
 */
export function normalize(s: string): string {
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Find additive info from an ingredient name or E-number.
 */
export function findAdditive(ingredient: Ingredient): AdditiveInfo | null {
  const { e_number, name, category } = ingredient;

  // Direct E-number match
  if (e_number) {
    const norm = e_number.toUpperCase().replace(/\s/g, '');
    const match = ADDITIVES_DB.find((a) => a.e_number === norm);
    if (match) return match;
  }

  // Name-based match
  const normName = normalize(name);
  for (const additive of ADDITIVES_DB) {
    for (const synonym of additive.names) {
      if (normName.includes(normalize(synonym))) {
        return additive;
      }
    }
  }

  // Context-aware match: if explicitly flagged as additive, check natural colorant keywords
  if (category === 'additive') {
    const naturalColorants: Record<string, string> = {
      'curcuma': 'E100',
      'curcumin': 'E100',
      'paprika': 'E160c',
      'betterave': 'E162',
      'carmin': 'E120',
      'cochenille': 'E120',
      'caramel': 'E150',
    };
    for (const [keyword, eNum] of Object.entries(naturalColorants)) {
      if (normName.includes(keyword)) {
        const match = ADDITIVES_DB.find((a) => a.e_number === eNum);
        if (match) return match;
      }
    }
  }

  return null;
}

// ============================================================================
// SECTION 3: CATEGORY THRESHOLDS
// ============================================================================
// Per-100g scoring thresholds. These define what "good" looks like for each
// food category. Judging a cheese against sandwich fiber thresholds would be
// unfair.
// ============================================================================

export interface CategoryThresholds {
  protein_g: [number, number, number];       // low / medium / high breakpoints
  fiber_g: [number, number, number];
  expected_kcal_range: [number, number];
  expect_micronutrients: boolean;
  /**
   * Per-category saturated-fat thresholds [moderate, major, critical].
   * Cheese, oil, dark chocolate etc. are fat-rich by nature — judging them
   * against the generic [5, 10, 15] scale unfairly tanks their score.
   */
  sat_fat_thresholds?: [number, number, number];
  /**
   * Per-category sugar thresholds [minor, moderate, major, critical] per 100g.
   * Default applies the WHO-derived generic scale; condiments and drinks
   * benefit from a relaxed scale because a typical serving is tiny (~10g for
   * ketchup) or liquid-based (different glycemic context).
   */
  sugar_thresholds?: [number, number, number, number];
}

const DEFAULT_THRESHOLDS: CategoryThresholds = {
  protein_g: [3, 6, 12],
  fiber_g: [1.5, 3, 6],
  expected_kcal_range: [50, 400],
  expect_micronutrients: false,
};

const DEFAULT_SAT_FAT: [number, number, number] = [5, 10, 15];
const DEFAULT_SUGAR: [number, number, number, number] = [5, 10, 15, 22.5];

export const CATEGORY_THRESHOLDS: Record<ProductCategory, CategoryThresholds> = {
  sandwich:         { protein_g: [5, 8, 12],   fiber_g: [2, 4, 6],  expected_kcal_range: [180, 320], expect_micronutrients: true  },
  ready_meal:       { protein_g: [4, 7, 10],   fiber_g: [2, 4, 6],  expected_kcal_range: [80, 200],  expect_micronutrients: true  },
  bread:            { protein_g: [6, 9, 12],   fiber_g: [3, 6, 9],  expected_kcal_range: [220, 300], expect_micronutrients: false },
  breakfast_cereal: { protein_g: [6, 10, 14],  fiber_g: [5, 8, 12], expected_kcal_range: [320, 420], expect_micronutrients: true  },
  yogurt:           { protein_g: [3, 5, 9],    fiber_g: [0, 1, 2],  expected_kcal_range: [40, 120],  expect_micronutrients: true  },
  cheese:           { protein_g: [15, 20, 25], fiber_g: [0, 0, 0],  expected_kcal_range: [200, 450], expect_micronutrients: true,  sat_fat_thresholds: [12, 20, 30] },
  processed_meat:   { protein_g: [10, 15, 22], fiber_g: [0, 0, 1],  expected_kcal_range: [100, 400], expect_micronutrients: false },
  fresh_meat:       { protein_g: [15, 20, 25], fiber_g: [0, 0, 0],  expected_kcal_range: [100, 300], expect_micronutrients: true  },
  fish:             { protein_g: [15, 20, 25], fiber_g: [0, 0, 0],  expected_kcal_range: [80, 250],  expect_micronutrients: true  },
  snack_sweet:      { protein_g: [4, 7, 10],   fiber_g: [2, 4, 6],  expected_kcal_range: [350, 550], expect_micronutrients: false },
  snack_salty:      { protein_g: [6, 9, 14],   fiber_g: [3, 5, 8],  expected_kcal_range: [400, 550], expect_micronutrients: false },
  beverage_soft:    { protein_g: [0, 0, 0],    fiber_g: [0, 0, 0],  expected_kcal_range: [0, 50],    expect_micronutrients: false },
  beverage_juice:   { protein_g: [0, 0, 0],    fiber_g: [0, 1, 2],  expected_kcal_range: [20, 60],   expect_micronutrients: true  },
  beverage_water:   { protein_g: [0, 0, 0],    fiber_g: [0, 0, 0],  expected_kcal_range: [0, 5],     expect_micronutrients: false },
  condiment:        { protein_g: [0, 3, 7],    fiber_g: [0, 1, 3],  expected_kcal_range: [20, 400],  expect_micronutrients: false, sugar_thresholds: [10, 20, 30, 45] },
  oil_fat:          { protein_g: [0, 0, 0],    fiber_g: [0, 0, 0],  expected_kcal_range: [700, 900], expect_micronutrients: false, sat_fat_thresholds: [20, 35, 50] },
  other:            DEFAULT_THRESHOLDS,
};

export function getThresholds(cat: ProductCategory): CategoryThresholds {
  return CATEGORY_THRESHOLDS[cat] ?? DEFAULT_THRESHOLDS;
}

// ============================================================================
// SECTION 3b: NAME-BASED CATEGORY INFERENCE
// ============================================================================
//
// When OFF returns categories_tags=[] and the LLM is unsure (or not
// invoked), `category` falls through to 'other', which means the
// pillar-2 (nutritional density) and pillar-3 (negative nutrients)
// thresholds use the generic DEFAULT_* scales. That's intentionally
// conservative but punishes products whose name alone makes their
// category obvious — "Yaourt grec nature" reading 0/5 fiber against
// the bread scale, or "Eau Évian" being judged against the snack-sweet
// kcal range.
//
// inferCategoryFromName fires only as a final fallback (called from
// scoreProduct when product.category === 'other'). Patterns are ordered
// most-specific first; a hit wins immediately. The list is editorial
// (not a published taxonomy) but covers the most common French + EN
// supermarket product types.
// ============================================================================

// Order: most-specific (composed dishes) first, then specific
// fish/meat terms, then general categories. Two false-match cases the
// audit caught:
//   1. "Filet de saumon" — `saumon` is fish, but a bare `\bfilet\b`
//      in fresh_meat would have absorbed it. Fish moved above meat.
//   2. "Sandwich poulet curry" — sandwich must win over the embedded
//      `\bpoulet\b`. Sandwich moved above meat.
//   3. "Pâte à tartiner Nutella" — bare `\bp[aâ]t[eé]\b` for pâté/
//      charcuterie also matched the `pâte` (dough) homograph. Removed
//      from processed_meat; the more specific snack_sweet pattern
//      now catches "pâte à tartiner".
const NAME_CATEGORY_PATTERNS: ReadonlyArray<readonly [RegExp, ProductCategory]> = [
  // ---- Beverages (clearest signals; check first) ----
  [/\beau\b|\bwater\b|spring water|eau de source|eau min[eé]rale|eau gaz[eé]use/i, 'beverage_water'],
  [/\bjus\b|\bjuice\b|\bnectar\b|smoothie|fruit drink/i, 'beverage_juice'],
  [/\bsoda\b|\bcola\b|boisson gaz[eé]use|soft drink|\btonic\b|limonade|ic[eé][ -]?tea|th[eé] glac[eé]|energy drink|red bull|monster/i, 'beverage_soft'],
  // ---- Dairy (yogurt before cheese — fromage blanc is yogurt-class) ----
  [/\byaourts?\b|yoghurt|yogurt|\bskyr\b|fromage[ -]?blanc|faisselle|\bquark\b|petit[- ]suisse/i, 'yogurt'],
  [/\bfromages?\b|\bcheese\b|\bbrie\b|camembert|cheddar|gruy[eè]re|\bgouda\b|mozzarella|parmesan|\bfeta\b|roquefort|emmental|comt[eé]|reblochon|munster|\bch[eè]vre\b|ricotta|mascarpone|halloumi/i, 'cheese'],
  // ---- Composed dishes that contain meat names; check before meat ----
  [/\bsandwich\b|\bburger\b|\bwrap\b|panini|\bkebab\b|\bcroque\b/i, 'sandwich'],
  // Sweet snacks before meat so "pâte à tartiner" (Nutella class) wins
  // over any bare meat token a marketing name might carry.
  [/chocolats?\b|\bchocolate\b|\bbonbon|\bcandy\b|biscuits?\b|cookies?\b|g[aâ]teaux?\b|\bcakes?\b|\btartes?\b|\btarts?\b|brownie|\bdonut\b|beignet|barre chocolat[eé]e|kinder|nutella|m&m|haribo|m[aâ]rs|snickers|twix|bounty|pringles?[ -]?sweet|gauffres?\b|cr[eê]pes?\b|p[aâ]te [aà] tartiner/i, 'snack_sweet'],
  // ---- Fish (before fresh_meat: "Filet de saumon" must hit fish) ----
  [/\bsaumon\b|\bthon\b|sardine|maquereau|\bhareng\b|cabillaud|\bmerlu\b|\bcolin\b|\btruite\b|crevette|\bcrabe\b|\bmoules\b|hu[iî]tres|\bbar\b|\bdorade\b/i, 'fish'],
  // ---- Processed meat (before fresh_meat: saucisson > generic meat) ----
  [/\bjambon\b|saucisson|chorizo|\bbacon\b|\blardon|\bsalami\b|pancetta|prosciutto|merguez|\brillettes\b|\bp[aâ]t[eé]\b(?! [aà] tartiner)/i, 'processed_meat'],
  // ---- Fresh meat ----
  [/\bpoulet\b|\bb[oœ]uf\b|\bporc\b|\bagneau\b|\bdinde\b|\bcanard\b|viande hach[eé]e|\bsteaks?\b|escalope|magret/i, 'fresh_meat'],
  // ---- Bakery ----
  [/\bpain\b|\bbread\b|baguette|brioche|focaccia|ciabatta|\btoasts?\b|\bpita\b|tortilla|\bcracotte/i, 'bread'],
  [/c[eé]r[eé]ales?\b|\bcereal\b|\bmuesli\b|\bgranola\b|porridge|flocons d['']avoine|\boats\b|cornflakes|chocapic|special k|fitness/i, 'breakfast_cereal'],
  // ---- Ready meals ----
  [/plat pr[eé]par[eé]|plat cuisin[eé]|ready meal|micro[- ]?ondes|[aà] r[eé]chauffer|lasagne|gratin|paella|risotto|\bcurry\b|chili con carne|hachis parmentier|tartiflette|moussaka/i, 'ready_meal'],
  // ---- Condiments (before salty: tapenade contains "olive"-like words) ----
  [/\bsauces?\b|mayonnaise|\bketchup\b|moutarde|mustard|vinaigrette|\bpesto\b|tahin[ei]|harissa|sambal|sriracha|wasabi|chutney|aioli|\btapenade\b/i, 'condiment'],
  // ---- Oils + butter (before salty: "Huile d'olive" matches `olives?` too) ----
  [/huile d['']olive|huile de colza|huile de tournesol|huile v[eé]g[eé]tale|\bolive oil\b|sunflower oil|canola oil|margarine|\bbeurre\b|\bbutter\b|saindoux/i, 'oil_fat'],
  // ---- Salty snacks ----
  [/\bchips\b|\bcrisps?\b|crackers?\b|biscuits? sal[eé]s?|\bpopcorn\b|\bpretzels?\b|cacahu[eè]tes?\b|noix de cajou|amande grill[eé]e|pistaches?\b|olives?\b/i, 'snack_salty'],
];

/**
 * Best-effort category inference from a product name. Returns 'other'
 * when no pattern matches, so callers can detect "no decision made".
 *
 * Editorial: the patterns are FR + EN supermarket vernacular. Adding a
 * new entry is the right move when a real product comes back from OFF
 * with category='other' but a name a child could classify. Most-
 * specific patterns must come first (e.g. "yaourt" before "fromage"
 * because "fromage blanc" is a yogurt-class product).
 */
export function inferCategoryFromName(name: string): ProductCategory {
  if (!name || typeof name !== 'string') return 'other';
  for (const [re, cat] of NAME_CATEGORY_PATTERNS) {
    if (re.test(name)) return cat;
  }
  return 'other';
}


// ============================================================================
// SECTION 4: SHARED KEYWORD CONSTANTS
// ============================================================================

const WHOLE_FOOD_KEYWORDS = [
  // Produce
  'tomate', 'salade', 'carotte', 'épinard', 'epinard', 'poivron', 'oignon',
  'ail', 'courgette', 'aubergine', 'concombre', 'brocoli', 'chou',
  'betterave', 'poireau', 'potiron', 'courge',
  // Fruits
  'fruit', 'pomme', 'poire', 'orange', 'citron', 'pamplemousse', 'mandarine',
  'abricot', 'pêche', 'peche', 'fraise', 'framboise', 'myrtille', 'cassis',
  'cerise', 'prune', 'mirabelle', 'raisin', 'figue', 'datte', 'mangue',
  'ananas', 'banane', 'kiwi', 'melon', 'pastèque', 'grenade', 'coco', 'noix de coco',
  // Legumes & nuts & seeds
  'lentille', 'haricot', 'pois', 'fève', 'feve', 'noix', 'amande', 'noisette',
  'pistache', 'cajou', 'graine', 'sésame', 'sesame', 'lin', 'chia', 'tournesol',
  // Grains
  'riz', 'quinoa', 'avoine', 'blé', 'ble', 'seigle', 'orge', 'sarrasin',
  // Animal
  'oeuf', 'œuf', 'poisson', 'saumon', 'thon', 'sardine', 'maquereau',
  'poulet', 'boeuf', 'porc', 'viande', 'dinde', 'canard', 'agneau',
  'jambon',
  // Dairy / other
  'fromage', 'lait', 'yaourt', 'skyr', 'eau', 'miel',
  'légume', 'legume',
];

const HIDDEN_SUGAR_NAMES = [
  'dextrose', 'maltodextrine', 'maltodextrin', 'sirop de glucose',
  'sirop de maïs', 'sirop de mais', 'glucose syrup', 'fructose',
  'saccharose', 'sucrose', 'sirop de fructose', 'sirop de glucose-fructose',
  'sucre inverti', 'lactose', 'mélasse', 'melasse', "sirop d'agave",
  'isoglucose', 'concentré de jus', "concentré de pomme", "concentré de poire",
  "sirop d'érable", "sirop d'erable", 'sirop de riz', 'sirop de datte',
  'sucre de canne', 'sucre roux', 'cassonade', 'rapadura', 'panela',
  'sucre inverti', 'jus de fruits concentré', 'jus concentré de pomme',
  'sirop de blé', 'sirop de ble', 'caramel de sucre',
];

const GENERIC_OIL_TERMS = [
  'huile végétale', 'huile vegetale', 'vegetable oil',
  'matière grasse végétale', 'matiere grasse vegetale',
  'graisse végétale', 'graisse vegetale',
];

// ============================================================================
// SECTION 5: PILLAR 1 — PROCESSING LEVEL (max 20)
// ============================================================================
//
// AUTHORITATIVE basis:
//   - NOVA classification: Monteiro et al., Public Health Nutrition
//     22(5):936–941 (2019); FAO Ultra-processed foods, diet quality, and
//     health using the NOVA classification system (2019).
//   - Ultra-processed food / cardiovascular risk association: Srour et al.,
//     BMJ 365:l1451 (2019, NutriNet-Santé cohort, n≈105k).
//
// EDITORIAL:
//   - Base score per NOVA class (20 / 17 / 13 / 6) is Scan\'eat's mapping
//     from the NOVA categorical framework to a 20-point scale. It is not
//     a published calibration.
//   - Auto-NOVA inference rules (see inferNovaClass) are heuristic: a clean
//     ingredient list with no cosmetic additives nudges input-NOVA-4 down
//     to 3. This is a guard against over-cautious OFF / LLM defaults; it is
//     NOT the official Monteiro algorithm.
//   - −2 modifiers (>10 ingredients, cosmetic additives) and the first-
//     ingredient penalty (-3 for sucre / huile leading the list) are
//     editorial proxies for UPF formulation intensity.
// ============================================================================

const FIRST_INGREDIENT_PENALTY_PATTERNS: Array<{ re: RegExp; label: string }> = [
  { re: /^(sucre|sirop|dextrose|fructose|glucose|maltodextrin)/i, label: 'sugar/syrup' },
  { re: /^(huile|graisse|matière grasse|margarine)/i, label: 'oil/fat' },
  { re: /^(amidon modifié|amidon de maïs modifié)/i, label: 'modified starch' },
];

/**
 * UPF markers per NOVA framework (Monteiro et al., Public Health Nutrition
 * 2019;22(5)): industrial ingredients characteristic of ultra-processed foods,
 * regardless of whether they carry an E-number. Flavorings are explicitly
 * listed in NOVA's UPF-indicator set even though EU Regulation 1334/2008
 * classifies them separately from additives.
 */
const UPF_MARKER_PATTERNS: Array<{ re: RegExp; label: string }> = [
  { re: /\bar[oô]mes?\b/i,                                                   label: 'flavorings (arômes)' },
  { re: /\bconcentr[eé] des? min[eé]raux|mineral concentrate/i,               label: 'mineral concentrate' },
  { re: /\bisolat de |\bprot[eé]ine isol[eé]e|protein isolate/i,              label: 'protein isolate' },
  { re: /\bhydrolysat|prot[eé]ines? hydrolys[eé]es?|hydrolyzed protein/i,     label: 'protein hydrolysate' },
  { re: /\bamidon modifi|modified starch|maltodextrin/i,                      label: 'modified starch' },
];

function detectUPFMarkers(ings: Ingredient[]): string[] {
  const hits: string[] = [];
  for (const marker of UPF_MARKER_PATTERNS) {
    if (ings.some((i) => marker.re.test(i.name))) hits.push(marker.label);
  }
  return hits;
}

/**
 * Infer NOVA class from the ingredient list. Expanded: recognizes generic
 * UPF markers (flavorings, isolates, hydrolysates) as NOVA-4 indicators even
 * when no E-number appears. Clean products with a longer but honest ingredient
 * list can now qualify for NOVA 3 (count ≤10 instead of ≤8).
 */
// Minimal fresh-produce lexicon (FR + EN) used as an escape hatch when
// OFF returns a barcode-only product with no ingredients list. Raw apples,
// bananas, etc. previously fell through to "NOVA 4" which punished the
// score — Monteiro's NOVA framework explicitly classes these as NOVA 1.
// Trailing 's' optional so "pommes" / "bananes" / "courgettes" match the
// same way "pomme" / "banane" / "courgette" do. Without this, "pommes"
// silently fell through to NOVA 4 on barcode scans with no ingredients.
const FRESH_PRODUCE_NAME = /^(banane|banana|pomme|apple|poire|pear|tomate|tomato|oignon|onion|avocat|avocado|carotte|carrot|concombre|cucumber|courgette|zucchini|kiwi|orange|citron|lemon|lime|fraise|strawberr|framboise|raspberr|myrtille|blueberr|cassis|blackcurrant|ananas|pineapple|raisin|grape|cerise|cherry|prune|plum|peche|pêche|peach|mangue|mango|papaye|papaya|poireau|leek|chou|cabbage|brocoli|broccoli|salade|lettuce|epinard|épinard|spinach|radis|radish|navet|turnip|betterave|beet|aubergine|eggplant|poivron|bell pepper|piment|chili pepper|champignon|mushroom|asperge|asparagus|artichaut|artichoke|haricot vert|green bean|haricot|bean|lentille|lentil|petit[- ]pois|pea|patate douce|sweet potato|pomme de terre|potato|courge|squash|citrouille|pumpkin|ail|garlic|gingembre|ginger|fenouil|fennel|celeri|céleri|celery|persil|parsley|basilic|basil|menthe|mint|coriandre|cilantro|ciboulette|chive|roquette|arugula|mache|mâche|cresson|watercress|endive|chicory|pastèque|watermelon|melon|nectarine|abricot|apricot|figue|fig|datte|date|grenade|pomegranate|noix|nut|amande|almond|noisette|hazelnut)s?\b/i;

/**
 * NOVA inference confidence levels.
 *
 * Fix #5 — previously inferNovaClass returned a bare NovaClass with no
 * indication of how reliable the inference was. This made "shipped a black box"
 * (audit doc issue #5) impossible to address at the UI level: a 1-ingredient
 * banana and a 20-ingredient product with novel preservatives the ADDITIVES_DB
 * doesn't know both returned a silent NovaClass integer.
 *
 * Confidence tiers:
 *   'high'   — the rules fired on clean positive evidence (fresh produce by
 *               name, single ingredient, only culinary ingredients).
 *   'medium' — the rules fired on absence-of-evidence (no cosmetic additives
 *               found, no UPF markers detected). Accurate on well-populated
 *               ingredient lists but degrades when the OCR/LLM missed additives.
 *   'low'    — fell through to the NOVA-4 conservative fallback. Most likely
 *               correct for complex products but may over-penalise products
 *               with unknown additives not yet in ADDITIVES_DB, or products
 *               with an empty ingredients list that are not fresh produce.
 *
 * KNOWN FAILURE MODES (no labelled dataset baseline — see audit doc issue #5):
 *   - Fermented products (cheese, vinegar, wine): additive-free but multi-
 *     ingredient; infer NOVA 3 when they are arguably NOVA 1–2. Mitigated
 *     by per-category sat_fat_thresholds in scoring but not in NOVA directly.
 *   - Products with non-standard additive names (brand-specific, truncated
 *     OCR) that are in ADDITIVES_DB under a canonical name will be missed
 *     by findAdditive() and may infer NOVA 3 instead of NOVA 4.
 *   - Long but clean ingredient lists (>10 items, all whole foods) are
 *     currently capped at NOVA 3 by the ings.length <= 10 guard. A salad
 *     with 12 vegetables would infer NOVA 4. Acceptable conservatism.
 */
export type NovaInferenceConfidence = 'high' | 'medium' | 'low';
export type NovaInferenceResult = { nova: NovaClass; confidence: NovaInferenceConfidence };

export function inferNovaClass(product: ProductInput): NovaClass {
  return inferNovaClassWithConfidence(product).nova;
}

export function inferNovaClassWithConfidence(product: ProductInput): NovaInferenceResult {
  const ings = product.ingredients;
  if (ings.length === 0) {
    if (FRESH_PRODUCE_NAME.test(String(product.name ?? '').trim())) {
      return { nova: 1, confidence: 'high' };
    }
    // Empty ingredients + unknown name: conservative fallback.
    return { nova: 4, confidence: 'low' };
  }
  const additives = ings.filter((i) => i.category === 'additive' || !!i.e_number);
  const cosmetics = additives
    .map((i) => findAdditive(i))
    .filter((a): a is AdditiveInfo => a !== null && COSMETIC_ADDITIVE_CATEGORIES.has(a.category));
  const upfMarkers = detectUPFMarkers(ings);

  if (ings.length === 1 && additives.length === 0 && upfMarkers.length === 0) {
    return { nova: 1, confidence: 'high' };
  }
  if (ings.length <= 3 && additives.length === 0 && upfMarkers.length === 0) {
    const onlyCulinary = ings.every((i) =>
      /^(sucre|sel|huile|beurre|graisse|miel|vinaigre|eau)/i.test(i.name.trim()),
    );
    if (onlyCulinary) return { nova: 2, confidence: 'high' };
  }
  if (
    cosmetics.length === 0 &&
    upfMarkers.length === 0 &&
    additives.length <= 2 &&
    ings.length <= 10
  ) {
    // 'medium': fired on absence-of-evidence — only as reliable as the
    // completeness of ADDITIVES_DB and the OCR fidelity of the ingredient list.
    return { nova: 3, confidence: 'medium' };
  }
  // Conservative fallback. If there are cosmetic additives or UPF markers we
  // know about, confidence is medium (positive evidence). Otherwise low.
  const hasPositiveEvidence = cosmetics.length > 0 || upfMarkers.length > 0;
  return { nova: 4, confidence: hasPositiveEvidence ? 'medium' : 'low' };
}

export function scoreProcessing(product: ProductInput): PillarScore {
  const MAX = 20;
  const deductions: Deduction[] = [];
  const bonuses: Deduction[] = [];

  // Fix #5: use the confidence-aware variant so low-confidence inferences
  // are surfaced as warnings in the pillar detail dialog rather than silently
  // treated as authoritative. 'high' = positive evidence; 'medium' = absence
  // of evidence (only as reliable as OCR + ADDITIVES_DB completeness); 'low' =
  // conservative fallback (empty ingredients or unknown product type).
  const inferredResult = inferNovaClassWithConfidence(product);
  const inferred = inferredResult.nova;
  const effectiveNova: NovaClass = (() => {
    if (!product.nova_class) return inferred;
    // Only downgrade 4 → lower if the ingredient list genuinely supports it.
    if (product.nova_class === 4 && inferred < 4) return inferred;
    return product.nova_class;
  })();

  if (effectiveNova !== product.nova_class) {
    // Recorded as a zero-point *deduction* (informational) so it shows up in
    // the pillar detail dialog alongside the base-score line. buildFlags filters
    // bonuses by `points >= 2` and would have hidden this otherwise.
    deductions.push({
      pillar: 'processing',
      reason: `NOVA auto-adjusted ${product.nova_class}→${effectiveNova} based on ingredients`,
      points: 0,
      severity: 'info',
    });
  }

  // Fix #5: surface NOVA inference confidence so users know when to be sceptical.
  // 'low'    = empty ingredient list or unknown product — NOVA is a guess.
  // 'medium' = no bad additives found, but absence-of-evidence only as reliable
  //            as OCR quality and ADDITIVES_DB completeness.
  // 'high'   = positive evidence (fresh produce, culinary-only, known additives).
  // Only append when we actually ran the heuristic (no_nova_class supplied or
  // auto-adjusted), and only for low/medium — high is self-explanatory.
  const novaWasInferred = !product.nova_class || effectiveNova !== product.nova_class;
  if (novaWasInferred && inferredResult.confidence !== 'high') {
    const confidenceNote = inferredResult.confidence === 'low'
      ? 'NOVA heuristic confidence: LOW — ingredient list missing or too short to classify reliably; score may be over-penalised'
      : 'NOVA heuristic confidence: MEDIUM — inferred from absence of known additives; only as reliable as OCR completeness';
    deductions.push({
      pillar: 'processing',
      reason: confidenceNote,
      points: 0,
      severity: 'info',
    });
  }

  let base: number;
  switch (effectiveNova) {
    case 1: base = 20; break;
    case 2: base = 17; break;
    case 3: base = 13; break;
    case 4: base = 6; break;
  }

  deductions.push({
    pillar: 'processing',
    reason: `NOVA class ${effectiveNova} base score`,
    points: base - MAX,
    severity: effectiveNova === 4 ? 'major' : effectiveNova === 3 ? 'moderate' : 'info',
  });

  let score = base;

  if (product.ingredients.length > 10) {
    score -= 2;
    deductions.push({
      pillar: 'processing',
      reason: `${product.ingredients.length} ingredients (>10 threshold)`,
      points: -2,
      severity: 'minor',
    });
  }

  const cosmeticAdditives = product.ingredients
    .map((ing) => findAdditive(ing))
    .filter((a): a is AdditiveInfo => a !== null && COSMETIC_ADDITIVE_CATEGORIES.has(a.category));

  // UPF markers per NOVA framework (flavorings, isolates, hydrolysates,
  // modified starch, mineral concentrates). −2 per distinct marker, cap −4.
  // Uses the same list as inferNovaClass to keep the two in lockstep.
  const upfMarkers = detectUPFMarkers(product.ingredients);
  if (upfMarkers.length > 0) {
    const penalty = Math.min(4, upfMarkers.length * 2);
    score -= penalty;
    deductions.push({
      pillar: 'processing',
      reason: `${upfMarkers.length} UPF marker(s) (NOVA framework): ${upfMarkers.join(', ')}`,
      points: -penalty,
      severity: 'minor',
    });
  }

  if (cosmeticAdditives.length > 0) {
    score -= 2;
    deductions.push({
      pillar: 'processing',
      reason: 'Contains cosmetic additives (colorants, flavor enhancers, etc.)',
      points: -2,
      severity: 'minor',
      evidence: cosmeticAdditives.map((a) => `${a.e_number} (${a.category})`).join(', '),
    });
  }

  // First-ingredient penalty — the first listed ingredient is the dominant
  // one by weight. Sugar or oil at position #1 is a strong composition
  // signal that deserves its own deduction.
  const first = product.ingredients[0];
  if (first) {
    const match = FIRST_INGREDIENT_PENALTY_PATTERNS.find((p) => p.re.test(first.name.trim()));
    if (match) {
      score -= 3;
      deductions.push({
        pillar: 'processing',
        reason: `Primary ingredient is ${match.label}: "${first.name}"`,
        points: -3,
        severity: 'moderate',
      });
    }
  }

  return {
    name: 'Processing Level',
    max: MAX,
    score: Math.max(0, score),
    deductions,
    bonuses,
  };
}

// ============================================================================
// SECTION 6: PILLAR 2 — NUTRITIONAL DENSITY (max 25)
// ============================================================================
//
// AUTHORITATIVE basis (ranges, not specific tier points):
//   - Dietary fibre target: EFSA 25 g/day for adults (EFSA Journal
//     2010;8(3):1462, "Scientific Opinion on Dietary Reference Values for
//     carbohydrates and dietary fibre").
//   - Protein requirement: EFSA PRI 0.83 g/kg bw/day (EFSA Journal
//     2012;10(2):2557).
//   - Omega-3 intake recommendations: EFSA ADI 250 mg EPA+DHA/day adults
//     (EFSA Journal 2010;8(3):1461).
//
// EDITORIAL:
//   - The five sub-scores (protein 0-6, fiber 0-5, micro 0-5, fats 0-5,
//     satiety 0-4) and their weighting are Scan\'eat's design.
//   - Category-specific protein/fiber thresholds (CATEGORY_THRESHOLDS) are
//     estimates based on typical food composition, not a published standard.
//   - Satiety index = P + 2·F − S is a reasonable proxy but not a
//     validated index.
//   - +1 "healthy fat source" bump (olive / canola / fish / nuts) is
//     editorial.
//   - +1 "declared micronutrients ≥3" bump is editorial.
// ============================================================================

export function scoreNutritionalDensity(product: ProductInput): PillarScore {
  const MAX = 25;
  const deductions: Deduction[] = [];
  const bonuses: Deduction[] = [];
  const { nutrition, category, ingredients } = product;
  const thresholds = getThresholds(category);

  // ---- 1. Protein (0–6) ----
  const [, pMed, pHigh] = thresholds.protein_g;
  const pLow = thresholds.protein_g[0];
  let proteinScore = 0;
  if (pHigh === 0) {
    proteinScore = 3; // Category doesn't expect protein — neutral credit
  } else if (nutrition.protein_g >= pHigh) proteinScore = 6;
  else if (nutrition.protein_g >= pMed) proteinScore = 4;
  else if (nutrition.protein_g >= pLow) proteinScore = 2;

  if (proteinScore < 6) {
    deductions.push({
      pillar: 'nutritional_density',
      reason: `Protein ${nutrition.protein_g}g/100g (${proteinScore}/6 for category ${category})`,
      points: proteinScore - 6,
      severity: proteinScore === 0 ? 'moderate' : 'minor',
    });
  } else {
    bonuses.push({
      pillar: 'nutritional_density',
      reason: `High protein content: ${nutrition.protein_g}g/100g`,
      points: 6,
      severity: 'info',
    });
  }

  // ---- 2. Fiber (0–5) ----
  const [fLow, fMed, fHigh] = thresholds.fiber_g;
  let fiberScore = 0;
  if (fHigh === 0) {
    fiberScore = 3; // Category doesn't expect fiber — neutral credit
  } else if (nutrition.fiber_g >= fHigh) fiberScore = 5;
  else if (nutrition.fiber_g >= fMed) fiberScore = 3;
  else if (nutrition.fiber_g >= fLow) fiberScore = 1;

  if (fiberScore < 5 && fHigh > 0) {
    deductions.push({
      pillar: 'nutritional_density',
      reason: `Fiber ${nutrition.fiber_g}g/100g (${fiberScore}/5 for category ${category})`,
      points: fiberScore - 5,
      severity: fiberScore === 0 ? 'moderate' : 'minor',
    });
  }

  // ---- 3. Micronutrient contribution (0–5) ----
  // Heuristic: weighted by percentage of whole foods present.
  const wholeFoods = ingredients.filter((ing) => {
    const n = ing.name.toLowerCase();
    return ing.is_whole_food || WHOLE_FOOD_KEYWORDS.some((kw) => n.includes(kw));
  });

  let microScore: number;
  const declaredPcts = wholeFoods
    .map((w) => w.percentage)
    .filter((p): p is number => typeof p === 'number' && p > 0);

  if (declaredPcts.length > 0) {
    const totalPct = declaredPcts.reduce((a, b) => a + b, 0);
    if (totalPct >= 30) microScore = 5;
    else if (totalPct >= 20) microScore = 4;
    else if (totalPct >= 12) microScore = 3;
    else if (totalPct >= 5) microScore = 2;
    else microScore = 1;
  } else {
    microScore = Math.min(5, wholeFoods.length);
  }

  // If category doesn't expect micronutrients, cap at 3 (so beverages can't score >3).
  if (!thresholds.expect_micronutrients) microScore = Math.min(microScore, 3);

  // Declared-vitamin bonus: if the product genuinely provides 3+ vitamins or
  // minerals (from OFF nutriments), bump the micronutrient score by 1 and
  // record it as a bonus rather than a deduction.
  const declared = product.declared_micronutrients ?? [];
  if (declared.length >= 3 && microScore < 5) {
    const before = microScore;
    microScore = Math.min(5, microScore + 1);
    if (microScore > before) {
      bonuses.push({
        pillar: 'nutritional_density',
        reason: `Declares ${declared.length} vitamins/minerals: ${declared.slice(0, 4).join(', ')}${declared.length > 4 ? '…' : ''}`,
        points: 1,
        severity: 'info',
      });
    }
  }

  // Quantitative nutrient-density bonus. Counts how many of the
  // expanded micronutrient fields hit the EU regulation 1924/2006
  // "source of" threshold (≥15% of NRV per 100 g). If 3+ nutrients
  // qualify, bump +1; if 6+ qualify (nutrient-rich food), bump +2
  // total. Overrides the "declared" heuristic because declared-count
  // can overcount (OFF lists any declared nutrient even if the amount
  // is trivial).
  //
  // The keys are typed against NutritionPer100g via `keyof` so the
  // table stays in lockstep with the interface — adding a new
  // micronutrient field to the type without updating NRV_15_PCT is now
  // a compile error instead of a silent miss. The previous version
  // cast `nutrition as Record<string, number | undefined>` which lost
  // exactly that protection.
  const NRV_15_PCT: Partial<Record<keyof NutritionPer100g, number>> = {
    vit_a_ug: 120, vit_c_mg: 12, vit_d_ug: 0.75, vit_e_mg: 1.8, vit_k_ug: 11.25,
    b1_mg: 0.165, b2_mg: 0.21, b3_mg: 2.4, b6_mg: 0.21, b9_ug: 30, b12_ug: 0.375,
    potassium_mg: 300, calcium_mg: 120, magnesium_mg: 56, iron_mg: 2.1, zinc_mg: 1.5,
  };
  let densityHits = 0;
  const hitList: string[] = [];
  for (const [key, threshold] of Object.entries(NRV_15_PCT) as Array<[keyof NutritionPer100g, number]>) {
    const v = Number(nutrition[key] ?? 0);
    if (v >= threshold) {
      densityHits += 1;
      hitList.push(String(key).replace(/_[mu]g$/, '').replace('_', ' '));
    }
  }
  if (densityHits >= 6 && microScore < 5) {
    const before = microScore;
    microScore = Math.min(5, microScore + 2);
    bonuses.push({
      pillar: 'nutritional_density',
      reason: `Nutrient-rich: 6+ vitamins/minerals at ≥15% NRV per 100g (${hitList.slice(0, 6).join(', ')}…)`,
      points: microScore - before,
      severity: 'info',
    });
  } else if (densityHits >= 3 && microScore < 5) {
    const before = microScore;
    microScore = Math.min(5, microScore + 1);
    if (microScore > before) {
      bonuses.push({
        pillar: 'nutritional_density',
        reason: `Source of ${densityHits} nutrients (≥15% NRV): ${hitList.slice(0, 4).join(', ')}`,
        points: 1,
        severity: 'info',
      });
    }
  }

  if (microScore < 5) {
    const reason = declaredPcts.length > 0
      ? `Whole foods = ${declaredPcts.reduce((a, b) => a + b, 0).toFixed(1)}% of product (${microScore}/5)`
      : `${wholeFoods.length} recognizable whole-food ingredients (${microScore}/5)`;
    deductions.push({
      pillar: 'nutritional_density',
      reason,
      points: microScore - 5,
      severity: 'minor',
    });
  }

  // ---- 4. Healthy fats ratio + quality bump (0–5) ----
  //
  // Base: sat/total ratio alone (unchanged). Quality bump: explicit presence
  // of olive, canola, fish or nuts raises a borderline ratio because these
  // fats carry monounsaturates and omega-3 that the raw ratio misses.
  let fatScore = 5;
  if ((nutrition.trans_fat_g ?? 0) > 0.1) {
    fatScore = 0;
  } else if (nutrition.fat_g === 0) {
    fatScore = 3;
  } else {
    const satRatio = nutrition.saturated_fat_g / nutrition.fat_g;
    if (satRatio > 0.5) fatScore = 1;
    else if (satRatio > 0.33) fatScore = 3;
    else if (satRatio > 0.2) fatScore = 4;
    else fatScore = 5;

    const hasHealthyFatSource = ingredients.some((ing) => {
      const n = ing.name.toLowerCase();
      return (
        /huile d'olive|huile de colza|huile de lin|huile de noix/.test(n) ||
        /saumon|sardine|maquereau|thon|hareng/.test(n) ||
        /noix|amande|noisette|pistache|cajou|graine de lin|graine de chia/.test(n)
      );
    });
    // Subtract bad fat source (palm, coprah): should cancel the bump.
    // Mirrors computeGlobalPenalties' palm-detection regex so the
    // two stay in lockstep. The trailing `coconut oil palm` token in
    // the previous version was a stale OR'd fragment that matched
    // nothing real; replaced with the FR/EN palm variants the global
    // penalty already covers.
    const hasBadFatSource = ingredients.some((ing) => {
      const n = ing.name.toLowerCase();
      return /huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah/.test(n);
    });
    if (hasHealthyFatSource && !hasBadFatSource && fatScore < 5) {
      const before = fatScore;
      fatScore = Math.min(5, fatScore + 1);
      if (fatScore > before) {
        bonuses.push({
          pillar: 'nutritional_density',
          reason: 'Healthy fat source in ingredients (olive/canola/fish/nuts)',
          points: 1,
          severity: 'info',
        });
      }
    }
  }

  if (fatScore < 5) {
    deductions.push({
      pillar: 'nutritional_density',
      reason: `Sat fat ratio unfavorable (${nutrition.saturated_fat_g}g sat / ${nutrition.fat_g}g total)`,
      points: fatScore - 5,
      severity: fatScore === 0 ? 'critical' : 'minor',
    });
  }

  // ---- 5. Satiety / glycemic quality (0–4) ----
  const satietySignal = nutrition.protein_g + nutrition.fiber_g * 2 - nutrition.sugars_g;
  let satietyScore: number;
  if (satietySignal >= 10) satietyScore = 4;
  else if (satietySignal >= 5) satietyScore = 3;
  else if (satietySignal >= 0) satietyScore = 2;
  else if (satietySignal >= -5) satietyScore = 1;
  else satietyScore = 0;

  if (satietyScore < 4) {
    deductions.push({
      pillar: 'nutritional_density',
      reason: `Satiety index: P${nutrition.protein_g} + 2×F${nutrition.fiber_g} − S${nutrition.sugars_g} = ${satietySignal.toFixed(1)} (${satietyScore}/4)`,
      points: satietyScore - 4,
      severity: 'minor',
    });
  }

  const totalScore = proteinScore + fiberScore + microScore + fatScore + satietyScore;

  return {
    name: 'Nutritional Density',
    max: MAX,
    score: Math.max(0, Math.min(MAX, totalScore)),
    deductions,
    bonuses,
  };
}

// ============================================================================
// SECTION 7: PILLAR 3 — NEGATIVE NUTRIENTS (max 25, penalty-based)
// ============================================================================
//
// AUTHORITATIVE basis for each cutoff:
//
//   Saturated fat thresholds default [5, 10, 15] g/100 g (solids):
//     - 5 g/100 g is the UK Food Standards Agency / DH "red" threshold above
//       which a product is considered High in saturated fat (FSA "Front of
//       pack nutrition labelling: guidance", 2016).
//     - WHO: saturated fat should supply <10 % of total daily energy
//       (WHO TRS 916, 2003; reaffirmed in WHO Saturated Fatty Acids
//       Guideline, 2023).
//   Sugar thresholds default [5, 10, 15, 22.5] g/100 g (solids):
//     - 22.5 g/100 g is the FSA "red" (High sugar) threshold.
//     - 5 g/100 g is the FSA Amber/Green boundary.
//     - WHO: free sugars <10 % of total energy, ideally <5 % (WHO 2015,
//       "Guideline: Sugars intake for adults and children").
//   Salt thresholds [0.75, 1.25, 1.5] g/100 g:
//     - 1.5 g/100 g is the FSA "red" (High salt) threshold.
//     - WHO: <5 g salt/day per adult (WHO Salt guideline, 2012).
//   Trans fat:
//     - WHO recommends complete elimination of industrially-produced trans
//       fatty acids from the food supply (WHO REPLACE programme, 2018).
//
// EDITORIAL: the granularity of the tiers (minor / moderate / major /
// critical) and the points per tier (-3/-6/-9/-12) are Scan\'eat's choice.
// The anchor values match the FSA "red" thresholds; tiers above and below
// are proportional gradations.
// ============================================================================

export function scoreNegativeNutrients(product: ProductInput): PillarScore {
  const MAX = 25;
  const deductions: Deduction[] = [];
  const bonuses: Deduction[] = [];
  const { nutrition, category } = product;
  const thresholds = getThresholds(category);

  let score = MAX;

  // ---- Saturated fat (category-aware) ----
  const sat = nutrition.saturated_fat_g;
  const [satMod, satMaj, satCrit] = thresholds.sat_fat_thresholds ?? DEFAULT_SAT_FAT;
  if (sat > satCrit) {
    score -= 9;
    deductions.push({
      pillar: 'negative_nutrients',
      reason: `Saturated fat ${sat}g/100g (>${satCrit}g critical for ${category})`,
      points: -9,
      severity: 'critical',
    });
  } else if (sat > satMaj) {
    score -= 6;
    deductions.push({
      pillar: 'negative_nutrients',
      reason: `Saturated fat ${sat}g/100g (>${satMaj}g major for ${category})`,
      points: -6,
      severity: 'major',
    });
  } else if (sat > satMod) {
    score -= 3;
    deductions.push({
      pillar: 'negative_nutrients',
      reason: `Saturated fat ${sat}g/100g (>${satMod}g moderate for ${category})`,
      points: -3,
      severity: 'moderate',
    });
  }

  // ---- Added sugars (or total sugars as fallback), category-aware ----
  const sugars = nutrition.added_sugars_g ?? nutrition.sugars_g;
  const sugarLabel = nutrition.added_sugars_g != null ? 'Added sugars' : 'Total sugars (added not declared)';
  const [sMinor, sMod, sMaj, sCrit] = thresholds.sugar_thresholds ?? DEFAULT_SUGAR;
  if (sugars > sCrit) {
    score -= 12;
    deductions.push({ pillar: 'negative_nutrients', reason: `${sugarLabel} ${sugars}g/100g (>${sCrit}g critical for ${category})`, points: -12, severity: 'critical' });
  } else if (sugars > sMaj) {
    score -= 9;
    deductions.push({ pillar: 'negative_nutrients', reason: `${sugarLabel} ${sugars}g/100g (>${sMaj}g major for ${category})`, points: -9, severity: 'major' });
  } else if (sugars > sMod) {
    score -= 6;
    deductions.push({ pillar: 'negative_nutrients', reason: `${sugarLabel} ${sugars}g/100g (>${sMod}g moderate for ${category})`, points: -6, severity: 'moderate' });
  } else if (sugars > sMinor) {
    score -= 3;
    deductions.push({ pillar: 'negative_nutrients', reason: `${sugarLabel} ${sugars}g/100g (>${sMinor}g minor for ${category})`, points: -3, severity: 'minor' });
  }

  // ---- Salt ----
  const salt = nutrition.salt_g;
  if (salt > 1.5) {
    score -= 6;
    deductions.push({ pillar: 'negative_nutrients', reason: `Salt ${salt}g/100g (>1.5g critical)`, points: -6, severity: 'major' });
  } else if (salt > 1.25) {
    score -= 4;
    deductions.push({ pillar: 'negative_nutrients', reason: `Salt ${salt}g/100g (>1.25g moderate)`, points: -4, severity: 'moderate' });
  } else if (salt > 0.75) {
    score -= 2;
    deductions.push({ pillar: 'negative_nutrients', reason: `Salt ${salt}g/100g (>0.75g minor)`, points: -2, severity: 'minor' });
  }

  // ---- Trans fats ----
  const trans = nutrition.trans_fat_g ?? 0;
  if (trans > 0.1) {
    score -= 10;
    deductions.push({
      pillar: 'negative_nutrients',
      reason: `Trans fat present: ${trans}g/100g (no safe level)`,
      points: -10,
      severity: 'critical',
    });
  }

  // ---- Calorie density anomaly ----
  const [kcalLow, kcalHigh] = thresholds.expected_kcal_range;
  if (nutrition.energy_kcal > kcalHigh * 1.25 || nutrition.energy_kcal < kcalLow * 0.5) {
    score -= 2;
    deductions.push({
      pillar: 'negative_nutrients',
      reason: `Energy ${nutrition.energy_kcal} kcal/100g outside category norm (${kcalLow}–${kcalHigh})`,
      points: -2,
      severity: 'minor',
    });
  }

  return {
    name: 'Negative Nutrients',
    max: MAX,
    score: Math.max(0, score),
    deductions,
    bonuses,
  };
}

// ============================================================================
// SECTION 8: PILLAR 4 — ADDITIVE RISK (max 15, penalty-based)
// ============================================================================
//
// AUTHORITATIVE basis:
//   - Each entry in ADDITIVES_DB has a `source` field citing either an
//     EFSA Scientific Opinion (with journal / year), an IARC Monograph
//     volume and classification, an EU Regulation, or a primary study.
//   - Tier assignment respects these rulings: Tier 1 requires an EU ban
//     (E171), IARC Group classification (nitrites via processed meat),
//     or a controlled human trial demonstrating harm (E466 FRESH trial).
//
// EDITORIAL:
//   - The three-tier grouping (serious / moderate / minor) is Scan\'eat's
//     synthesis; authorities generally speak in terms of specific ADI
//     values or classifications, not in tiers.
//   - Point values (-5 / -2 / -1) and caps (-10 / -6 / -3) are editorial.
// ============================================================================

export function scoreAdditiveRisk(product: ProductInput): PillarScore {
  const MAX = 15;
  const deductions: Deduction[] = [];
  const bonuses: Deduction[] = [];

  const tier1Hits: Array<{ ingredient: string; additive: string; concern: string }> = [];
  const tier2Hits: Array<{ ingredient: string; additive: string; concern: string }> = [];
  const tier3Hits: Array<{ ingredient: string; additive: string; concern: string }> = [];

  for (const ing of product.ingredients) {
    const additive = findAdditive(ing);
    if (!additive) continue;

    const hit = {
      ingredient: ing.name,
      additive: additive.e_number,
      concern: additive.concern,
    };

    if (additive.tier === 1) tier1Hits.push(hit);
    else if (additive.tier === 2) tier2Hits.push(hit);
    else tier3Hits.push(hit);
  }

  let score = MAX;

  if (tier1Hits.length > 0) {
    const penalty = Math.min(10, tier1Hits.length * 5);
    score -= penalty;
    deductions.push({
      pillar: 'additive_risk',
      reason: `${tier1Hits.length} Tier-1 additive${tier1Hits.length > 1 ? 's' : ''} (serious concern)`,
      points: -penalty,
      severity: 'critical',
      evidence: tier1Hits.map((h) => `${h.additive} (${h.ingredient}): ${h.concern}`).join(' | '),
    });
  }

  if (tier2Hits.length > 0) {
    const penalty = Math.min(6, tier2Hits.length * 2);
    score -= penalty;
    deductions.push({
      pillar: 'additive_risk',
      reason: `${tier2Hits.length} Tier-2 additive${tier2Hits.length > 1 ? 's' : ''} (moderate concern)`,
      points: -penalty,
      severity: 'moderate',
      evidence: tier2Hits.map((h) => `${h.additive} (${h.ingredient}): ${h.concern}`).join(' | '),
    });
  }

  if (tier3Hits.length > 0) {
    const penalty = Math.min(3, tier3Hits.length * 1);
    score -= penalty;
    deductions.push({
      pillar: 'additive_risk',
      reason: `${tier3Hits.length} Tier-3 additive${tier3Hits.length > 1 ? 's' : ''} (minor concern)`,
      points: -penalty,
      severity: 'minor',
      evidence: tier3Hits.map((h) => `${h.additive} (${h.ingredient}): ${h.concern}`).join(' | '),
    });
  }

  return {
    name: 'Additive Risk',
    max: MAX,
    score: Math.max(0, score),
    deductions,
    bonuses,
  };
}

export function countTier1Additives(product: ProductInput): number {
  return product.ingredients
    .map((ing) => findAdditive(ing))
    .filter((a) => a !== null && a.tier === 1).length;
}

// ============================================================================
// SECTION 9: PILLAR 5 — INGREDIENT INTEGRITY (max 15)
// ============================================================================
//
// EDITORIAL: this entire pillar is Scan\'eat's opinion layer.
//
// No authoritative body publishes "ingredient integrity" scores; the signals
// we use (first-three-whole-food, recognizability, origin transparency,
// hidden-sugar multiplicity, named oils) are proxies for formulation
// quality that correlate with NOVA 4 in surveys but are not themselves a
// regulatory standard.
//
// The sub-scores and their weightings (+5 / +3 / +2 / +2 / +3) are our
// editorial choices, chosen so that a transparently-labelled whole-food
// product can reach 15/15 and a reformulated UPF lands near 0.
// ============================================================================

function isWholeFood(name: string, isFlag: boolean | undefined): boolean {
  if (isFlag === true) return true;
  const lower = name.toLowerCase();
  if (/sirop|huile|farine raffinée|amidon modifié|isolat|concentré/i.test(lower)) return false;
  return WHOLE_FOOD_KEYWORDS.some((kw) => lower.includes(kw));
}

export function scoreIngredientIntegrity(product: ProductInput): PillarScore {
  const MAX = 15;
  const deductions: Deduction[] = [];
  const bonuses: Deduction[] = [];

  let score = 0;

  // ---- 1. First 3 ingredients whole foods (+5) ----
  const first3 = product.ingredients.slice(0, 3);
  const first3Whole = first3.filter((ing) => isWholeFood(ing.name, ing.is_whole_food)).length;
  const first3Score = Math.round((first3Whole / 3) * 5);
  score += first3Score;
  if (first3Score < 5) {
    deductions.push({
      pillar: 'ingredient_integrity',
      reason: `Only ${first3Whole}/3 of first ingredients are whole foods (${first3Score}/5)`,
      points: first3Score - 5,
      severity: 'moderate',
    });
  } else {
    bonuses.push({
      pillar: 'ingredient_integrity',
      reason: 'First 3 ingredients are all whole foods',
      points: 5,
      severity: 'info',
    });
  }

  // ---- 2. Overall recognizability (+3) ----
  const nonAdditive = product.ingredients.filter((ing) => !findAdditive(ing));
  const recognizable = nonAdditive.filter((ing) => {
    const n = ing.name.toLowerCase();
    return n.length < 40 && !/isolat|hydrolysat|concentré|modifié|extrait sec/i.test(n);
  }).length;
  const recogRatio = nonAdditive.length > 0 ? recognizable / nonAdditive.length : 1;
  const recogScore = recogRatio >= 0.8 ? 3 : recogRatio >= 0.6 ? 2 : recogRatio >= 0.4 ? 1 : 0;
  score += recogScore;
  if (recogScore < 3) {
    deductions.push({
      pillar: 'ingredient_integrity',
      reason: `${(recogRatio * 100).toFixed(0)}% of ingredients are recognizable (${recogScore}/3)`,
      points: recogScore - 3,
      severity: 'minor',
    });
  }

  // ---- 3. Origin transparency (+2) ----
  if (product.origin_transparent || product.origin) {
    score += 2;
    bonuses.push({
      pillar: 'ingredient_integrity',
      reason: `Origin declared: ${product.origin ?? 'transparent'}`,
      points: 2,
      severity: 'info',
    });
  } else {
    deductions.push({
      pillar: 'ingredient_integrity',
      reason: 'No origin information',
      points: -2,
      severity: 'minor',
    });
  }

  // ---- 4. Hidden sugars (+2) ----
  const sugarAliases = new Set<string>();
  for (const ing of product.ingredients) {
    const n = ing.name.toLowerCase();
    for (const alias of HIDDEN_SUGAR_NAMES) {
      if (n.includes(alias)) sugarAliases.add(alias);
    }
    if (/^sucre/i.test(ing.name.trim())) sugarAliases.add('sucre');
  }

  if (sugarAliases.size >= 2) {
    deductions.push({
      pillar: 'ingredient_integrity',
      reason: `${sugarAliases.size} distinct sugar sources detected: ${Array.from(sugarAliases).join(', ')}`,
      points: -2,
      severity: 'moderate',
    });
  } else {
    score += 2;
    bonuses.push({
      pillar: 'ingredient_integrity',
      reason: sugarAliases.size === 1 ? 'Single transparent sugar source' : 'No hidden sugars',
      points: 2,
      severity: 'info',
    });
  }

  // ---- 5. Named oils (+3) ----
  const hasGenericOil = product.ingredients.some((ing) => {
    const n = ing.name.toLowerCase();
    return GENERIC_OIL_TERMS.some((g) => n.includes(g));
  });
  if (product.named_oils !== false && !hasGenericOil) {
    score += 3;
    bonuses.push({
      pillar: 'ingredient_integrity',
      reason: 'Oils are specifically named (not generic "vegetable oil")',
      points: 3,
      severity: 'info',
    });
  } else {
    deductions.push({
      pillar: 'ingredient_integrity',
      reason: 'Generic "vegetable oil" used instead of specific named oil',
      points: -3,
      severity: 'minor',
    });
  }

  return {
    name: 'Ingredient Integrity',
    max: MAX,
    score: Math.max(0, Math.min(MAX, score)),
    deductions,
    bonuses,
  };
}

// ============================================================================
// SECTION 10: GLOBAL MODIFIERS, VETOES & MAIN ORCHESTRATOR
// ============================================================================
//
// EDITORIAL throughout this section:
//
//   - Global bonuses: +2 organic, +3 whole-grain, +2 fermented, +2 omega-3
//     ingredient. Capped at +10.
//   - Global penalties: -2 misleading marketing, -3 health claims,
//     -3 palm oil / derivative present.
//   - Veto caps (30 / 40 / 45) for combinations the engine considers
//     disqualifying regardless of other pillar scores.
//   - Grade breakpoints A+ (≥85) / A (≥70) / B (≥55) / C (≥40) / D (≥25) /
//     F (<25) are Scan\'eat's mapping, not a published standard.
//
// AUTHORITATIVE anchor for the processed-meat veto:
//   - Processed meat = IARC Group 1 (Monograph Vol 114, 2015). The veto
//     cap at 40 reflects this "carcinogenic to humans" classification.
// ============================================================================

export const ENGINE_VERSION = '2.2.0';

function scoreToGrade(score: number): Grade {
  if (score >= 85) return 'A+';
  if (score >= 70) return 'A';
  if (score >= 55) return 'B';
  if (score >= 40) return 'C';
  if (score >= 25) return 'D';
  return 'F';
}

function gradeVerdict(grade: Grade): string {
  switch (grade) {
    case 'A+': return 'Excellent — daily staple potential';
    case 'A':  return 'Good — regular consumption fine';
    case 'B':  return 'Acceptable — moderate frequency';
    case 'C':  return 'Mediocre — occasional only';
    case 'D':  return 'Poor — avoid regular use';
    case 'F':  return 'Very poor — avoid';
  }
}

function computeGlobalBonuses(product: ProductInput): Deduction[] {
  const bonuses: Deduction[] = [];
  if (product.organic) {
    bonuses.push({ pillar: 'global_bonus', reason: 'Organic certification', points: 2, severity: 'info' });
  }
  if (product.whole_grain_primary) {
    bonuses.push({ pillar: 'global_bonus', reason: 'Whole grain as primary grain', points: 3, severity: 'info' });
  }
  if (product.fermented) {
    bonuses.push({ pillar: 'global_bonus', reason: 'Contains fermented / probiotic content', points: 2, severity: 'info' });
  }
  // Omega-3 sources: flax / chia / walnut / fatty fish signal EPA/DHA/ALA.
  const omega3 = product.ingredients.find((ing) => {
    const n = ing.name.toLowerCase();
    return /(graine de )?lin\b|\bchia\b|\bnoix\b|saumon|sardine|maquereau|hareng|anchois/.test(n);
  });
  if (omega3) {
    bonuses.push({
      pillar: 'global_bonus',
      reason: `Omega-3 source: ${omega3.name}`,
      points: 2,
      severity: 'info',
    });
  }
  return bonuses;
}

function computeGlobalPenalties(product: ProductInput): Deduction[] {
  const penalties: Deduction[] = [];
  if (product.has_misleading_marketing) {
    penalties.push({
      pillar: 'global_penalty',
      reason: 'Misleading marketing claims (e.g., "natural" / "light" unjustified)',
      points: -2,
      severity: 'moderate',
    });
  }
  if (product.has_health_claims) {
    penalties.push({
      pillar: 'global_penalty',
      reason: 'Health claims present — verify vs composition',
      points: -3,
      severity: 'moderate',
    });
  }
  // Palm-oil / palm kernel detection (all variants).
  const palm = product.ingredients.find((ing) => {
    const n = ing.name.toLowerCase();
    return /huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah/.test(n);
  });
  if (palm) {
    penalties.push({
      pillar: 'global_penalty',
      reason: `Palm oil or derivative: ${palm.name}`,
      points: -3,
      severity: 'moderate',
    });
  }
  return penalties;
}

function checkVeto(product: ProductInput): VetoCondition {
  const { nutrition, category, ingredients } = product;

  // Trans fats — no safe level. INTENTIONALLY cumulative with the
  // -10 deduction in scoreNegativeNutrients: both penalties draw on
  // independently authoritative bases (FSA "no safe level" guidance
  // and the WHO REPLACE elimination target IARC-aligned). The pillar
  // deduction expresses the within-pillar judgment; the veto cap
  // expresses the engine-level "this product cannot be A/A+ regardless
  // of whatever else it does well". Removing either would weaken a
  // citation. See engine-trans-fat-tests.ts for the pinned behaviour.
  if ((nutrition.trans_fat_g ?? 0) > 0.1) {
    return { triggered: true, reason: 'Contains industrial trans fats — no safe level', cap: 40 };
  }

  // >3 Tier-1 additives
  const tier1Count = countTier1Additives(product);
  if (tier1Count > 3) {
    return { triggered: true, reason: `${tier1Count} Tier-1 additives — cumulative risk too high`, cap: 40 };
  }

  // Processed meat combo: nitrites + high salt + refined starch
  // R-fix: this only substring-matched ing.name for "nitrite"/"e249"/
  // "e250". When ingredients come from structured OFF/LLM parsing,
  // the e_number is frequently carried in its own field (ing.e_number
  // = 'E250') with a name like "Conservateur" or "Sel nitrité" that
  // never spells out the E-number in text — those entries silently
  // failed to trigger nitrite detection at all, undercounting risk on
  // exactly the processed-meat combo this veto exists to catch. Check
  // the structured field first; the text fallback still covers labels
  // that spell out "nitrite de sodium (E250)" without a separate
  // e_number being populated.
  const hasNitrites = ingredients.some((ing) => {
    const eNum = (ing.e_number || '').toUpperCase().replace(/\s/g, '');
    if (eNum === 'E249' || eNum === 'E250') return true;
    const n = ing.name.toLowerCase();
    return n.includes('nitrite') || n.includes('e249') || n.includes('e250');
  });
  const highSalt = nutrition.salt_g > 1.5;
  const refined = ingredients.some((ing) => /farine de blé|farine raffinée|amidon|dextrose/i.test(ing.name.toLowerCase()));

  if (hasNitrites && highSalt && refined && category === 'processed_meat') {
    return { triggered: true, reason: 'Processed meat with nitrites + high salt + refined starch combination', cap: 40 };
  }

  // Extreme added sugar outside confectionery
  const isConfectionery = category === 'snack_sweet';
  const sugars = nutrition.added_sugars_g ?? nutrition.sugars_g;
  if (!isConfectionery && sugars > 30) {
    return { triggered: true, reason: 'Added sugar >30g/100g in non-confectionery category', cap: 40 };
  }

  // Sugary beverages with no nutritional contribution
  if (category === 'beverage_soft' && sugars > 5 && nutrition.protein_g < 1 && nutrition.fiber_g < 1) {
    return { triggered: true, reason: 'Sugar-sweetened beverage with no nutritional contribution', cap: 30 };
  }

  // Ultra-processed meat reconstructions
  const hasMechanicallySeparated = ingredients.some((ing) =>
    /séparée mécaniquement|mechanically separated|msm/i.test(ing.name)
  );
  if (hasMechanicallySeparated && product.nova_class === 4) {
    return { triggered: true, reason: 'Mechanically separated meat in NOVA 4 product — low meat quality', cap: 45 };
  }

  return { triggered: false, reason: '', cap: 100 };
}

function buildFlags(audit: Omit<ScoreAudit, 'red_flags' | 'green_flags'>): {
  red: string[];
  green: string[];
} {
  const red: string[] = [];
  const green: string[] = [];

  const allDeductions = [
    ...audit.pillars.processing.deductions,
    ...audit.pillars.nutritional_density.deductions,
    ...audit.pillars.negative_nutrients.deductions,
    ...audit.pillars.additive_risk.deductions,
    ...audit.pillars.ingredient_integrity.deductions,
    ...audit.global_penalties,
  ];
  const allBonuses = [
    ...audit.pillars.processing.bonuses,
    ...audit.pillars.nutritional_density.bonuses,
    ...audit.pillars.negative_nutrients.bonuses,
    ...audit.pillars.additive_risk.bonuses,
    ...audit.pillars.ingredient_integrity.bonuses,
    ...audit.global_bonuses,
  ];

  for (const d of allDeductions) {
    if (d.severity === 'critical' || d.severity === 'major') red.push(d.reason);
  }
  for (const b of allBonuses) {
    if (b.points >= 2) green.push(b.reason);
  }
  // Surface ecoscore as a flag. Doesn't change the numeric score
  // (preserves the 5-pillar 100-point contract), but folds
  // environmental impact into the flag list so the user-facing
  // audit reflects it. Thresholds follow OFF letter grades: A/B
  // green, D/E red, C neutral. The `eco` field is on ScoreAudit
  // proper now (was a side-channel cast through a local EcoSide
  // type before; cleaned up alongside Phase-1 bug fixes).
  const eco = audit.eco;
  if (eco?.grade) {
    const g = String(eco.grade).toLowerCase();
    if (g === 'a' || g === 'b') {
      green.push(`Eco-score ${g.toUpperCase()} (${eco.value ?? '—'}/100) — low environmental impact`);
    } else if (g === 'd' || g === 'e') {
      red.push(`Eco-score ${g.toUpperCase()} (${eco.value ?? '—'}/100) — high environmental impact`);
    }
  }

  if (audit.veto.triggered) {
    red.unshift(`VETO: ${audit.veto.reason}`);
  }

  return { red, green };
}

function collectWarnings(product: ProductInput): string[] {
  const warnings: string[] = [];
  if (product.nutrition.trans_fat_g == null) {
    warnings.push('trans_fat_g not declared — assumed 0');
  }
  if (product.nutrition.added_sugars_g == null) {
    warnings.push('added_sugars_g not declared — using total sugars as proxy');
  }
  return warnings;
}

/**
 * Apply name-based category inference when the input falls through to
 * 'other'. Returns the (possibly category-overridden) product plus a
 * flag telling the caller whether to surface a warning.
 *
 * Pure: produces a shallow clone when it changes anything, returns the
 * original reference otherwise.
 */
function applyCategoryInference(product: ProductInput): {
  product: ProductInput;
  inferred: ProductCategory | null;
} {
  if (product.category !== 'other') return { product, inferred: null };
  const inferred = inferCategoryFromName(product.name);
  if (inferred === 'other') return { product, inferred: null };
  return { product: { ...product, category: inferred }, inferred };
}

/**
 * ============================================================================
 * MAIN SCORING FUNCTION
 * ============================================================================
 * Takes a structured ProductInput, returns full ScoreAudit.
 *
 * Pure synchronous function — no I/O, no side effects.
 * ============================================================================
 */
export function scoreProduct(input: ProductInput): ScoreAudit {
  // Final-fallback category inference: if both OFF and the LLM landed on
  // 'other', try to recognise the name. A product hand-typed by a user
  // ("Yaourt grec nature") otherwise scored against the generic scale.
  const { product, inferred } = applyCategoryInference(input);

  const processing = scoreProcessing(product);
  const nutritional_density = scoreNutritionalDensity(product);
  const negative_nutrients = scoreNegativeNutrients(product);
  const additive_risk = scoreAdditiveRisk(product);
  const ingredient_integrity = scoreIngredientIntegrity(product);

  // Base sum
  let score =
    processing.score +
    nutritional_density.score +
    negative_nutrients.score +
    additive_risk.score +
    ingredient_integrity.score;

  // Global bonuses (capped at +10)
  const global_bonuses = computeGlobalBonuses(product);
  const bonusTotal = Math.min(10, global_bonuses.reduce((s, b) => s + b.points, 0));

  // Global penalties
  const global_penalties = computeGlobalPenalties(product);
  const penaltyTotal = global_penalties.reduce((s, p) => s + p.points, 0);

  score = score + bonusTotal + penaltyTotal;

  // Clamp 0–100
  score = Math.max(0, Math.min(100, score));

  // Apply veto cap
  const veto = checkVeto(product);
  if (veto.triggered && score > veto.cap) {
    score = veto.cap;
  }

  score = Math.round(score);
  const grade = scoreToGrade(score);

  const preAudit: Omit<ScoreAudit, 'red_flags' | 'green_flags'> = {
    product_name: product.name,
    category: product.category,
    score,
    grade,
    verdict: gradeVerdict(grade),
    pillars: {
      processing,
      nutritional_density,
      negative_nutrients,
      additive_risk,
      ingredient_integrity,
    },
    global_bonuses,
    global_penalties,
    veto,
    engine_version: ENGINE_VERSION,
    warnings: [
      ...collectWarnings(product),
      ...(inferred ? [`Category inferred from name as "${inferred}" (input had category="other")`] : []),
    ],
    // Fix #2 — eco-score side-channel. buildFlags reads this to
    // surface environmental impact as a red/green flag without
    // disrupting the 5-pillar numeric score.
    eco: product.ecoscore_grade
      ? { grade: product.ecoscore_grade, value: product.ecoscore_value }
      : undefined,
    nutriscore_grade: product.nutriscore_grade ?? null,
  };

  const { red, green } = buildFlags(preAudit);

  return {
    ...preAudit,
    red_flags: red,
    green_flags: green,
  };
}
