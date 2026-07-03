/**
 * Personal-score adjustments layered on top of the classic score.
 *
 * Design: the diet is a HARD constraint; everything else is soft. When the
 * product violates the user's diet, the personal score is capped at 0 (grade F)
 * regardless of other merits — a product you cannot eat is a 0, not a 41.
 * Compliance path runs the full adjustment chain (age / sex / activity /
 * protein PRI / daily budgets / BMI / modifiers) and sums to a delta applied
 * on top of the classic score, clamped 0–100.
 *
 * AUTHORITATIVE anchors for the adjustments:
 *   - Protein PRI by age: EFSA Scientific Opinion 2012;10(2):2557
 *     (0.83 g/kg for adults; ≥65y 1.0 g/kg for sarcopenia prevention).
 *   - Iron RNI by sex: EFSA Scientific Opinion 2015;13(10):4254 (adult
 *     men 11 mg/day; menstruating women 16 mg/day).
 *   - Sodium sensitivity / BMI: WHO Global Database on BMI (2000);
 *     WHO Salt Guideline (2012).
 *   - Sat-fat, free-sugar daily budgets from TDEE × WHO %E conversions
 *     (WHO Guideline on Saturated Fatty Acids 2023; WHO Sugars 2015).
 *   - Athlete protein / carb context: IOC Consensus on Sports Nutrition
 *     (Br J Sports Med 2018).
 *
 * EDITORIAL: the point magnitudes (±2, ±3, ±4, ±5) and the flat -30 fall-
 * back when a diet violation is detected WITHOUT enough physiological data
 * are Scan\'eat's choice, not validated calibrations. The hard "0 for diet
 * violation" cap is the defensible behaviour from a user-decision standpoint.
 */

import { checkDiet, DIET_DEFS } from './diets.js';
import {
  bmi, bmiCategory, hasMinimalProfile,
  dailyTargets, proteinPRI_g,
} from '../data/profile.js';

/**
 * @returns {{
 *   personal_score: number,
 *   delta: number,
 *   adjustments: Array<{points: number, reason: string, category: 'diet' | 'age' | 'sex' | 'activity' | 'bmi' | 'modifier', veto?: boolean}>,
 *   applicable: boolean,
 *   diet_reason: string | null,
 *   veto: boolean
 * }}
 */
export function computePersonalScore(audit, product, profile, lang = 'fr') {
  const applicable =
    (profile?.diet && profile.diet !== 'none') ||
    hasMinimalProfile(profile) ||
    anyModifierOn(profile?.modifiers);
  if (!applicable) {
    return {
      personal_score: audit.score,
      delta: 0,
      adjustments: [],
      applicable: false,
      diet_reason: null,
      veto: false,
    };
  }

  const adjustments = [];
  let dietReason = null;
  let veto = false;

  // ===== DIET COMPLIANCE (HARD) =====
  if (profile.diet && profile.diet !== 'none') {
    const r = checkDiet(product, profile.diet, profile.custom_diet, lang);
    if (!r.compliant) {
      // Hard veto — the product is not eligible for this user. Everything
      // else is informational (still computed below) but the score caps at 0.
      veto = true;
      dietReason = r.reason;

      // Fix #8: halal and kosher compliance cannot be fully verified from an
      // ingredient list alone (slaughter method, meat-dairy separation, etc.).
      // The hard veto fires anyway to catch obvious violations, but we must
      // surface a disclaimer so the user doesn't treat the veto as authoritative.
      const UNVERIFIABLE_DIETS = ['halal', 'kosher'];
      const disclaimer = UNVERIFIABLE_DIETS.includes(profile.diet)
        ? (lang === 'en'
            ? ` — Note: ${DIET_DEFS[profile.diet]?.note_en ?? 'full compliance cannot be verified from the label alone'}`
            : ` — Note : ${DIET_DEFS[profile.diet]?.note_fr ?? 'la conformité complète ne peut pas être vérifiée depuis l\'étiquette seule'}`)
        : '';

      adjustments.push({
        points: 0,
        reason: r.reason + disclaimer,
        category: 'diet',
        veto: true,
      });
    } else if (r.certified) {
      adjustments.push({
        points: +5,
        reason: lang === 'en'
          ? `${DIET_DEFS[profile.diet].label_en} certification detected: ${r.preferredHits.slice(0, 2).join(', ')}`
          : `Certification ${DIET_DEFS[profile.diet].label_fr} détectée : ${r.preferredHits.slice(0, 2).join(', ')}`,
        category: 'diet',
      });
    } else if (r.preferredHits.length > 0) {
      adjustments.push({
        points: +3,
        reason: lang === 'en'
          ? `${DIET_DEFS[profile.diet].label_en}-friendly ingredients: ${r.preferredHits.slice(0, 2).join(', ')}`
          : `Conforme ${DIET_DEFS[profile.diet].label_fr} : ${r.preferredHits.slice(0, 2).join(', ')}`,
        category: 'diet',
      });
    }
  }

  // ===== MODIFIERS (SOFT preferences from profile.modifiers) =====
  const mods = profile.modifiers || {};
  if (mods.lowSugar) {
    const sugars = product.nutrition.added_sugars_g ?? product.nutrition.sugars_g;
    if (sugars > 5) {
      adjustments.push({
        points: -3,
        reason: lang === 'en'
          ? `Low-sugar preference: ${sugars} g/100 g exceeds 5 g/100 g`
          : `Préférence faible en sucre : ${sugars} g/100 g > 5 g/100 g`,
        category: 'modifier',
      });
    }
  }
  if (mods.lowSalt) {
    if (product.nutrition.salt_g > 0.75) {
      adjustments.push({
        points: -3,
        reason: lang === 'en'
          ? `Low-salt preference: ${product.nutrition.salt_g} g/100 g exceeds 0.75 g/100 g`
          : `Préférence faible en sel : ${product.nutrition.salt_g} g/100 g > 0,75 g/100 g`,
        category: 'modifier',
      });
    }
  }
  if (mods.highProtein) {
    if (product.nutrition.protein_g >= 10) {
      adjustments.push({
        points: +3,
        reason: lang === 'en'
          ? `High-protein preference matched (${product.nutrition.protein_g} g/100 g)`
          : `Préférence riche en protéines satisfaite (${product.nutrition.protein_g} g/100 g)`,
        category: 'modifier',
      });
    }
  }
  if (mods.organic) {
    if (product.organic) {
      adjustments.push({
        points: +2,
        reason: lang === 'en'
          ? `Organic-only preference: product is organic (EU Reg. 2018/848)`
          : `Préférence bio : produit certifié bio (Règl. UE 2018/848)`,
        category: 'modifier',
      });
    } else {
      adjustments.push({
        points: -3,
        reason: lang === 'en'
          ? `Organic-only preference: product is not certified organic`
          : `Préférence bio : produit non-bio`,
        category: 'modifier',
      });
    }
  }

  // ===== AGE-BASED =====
  if (typeof profile.age_years === 'number' && profile.age_years > 0) {
    if (profile.age_years >= 65 && product.nutrition.protein_g >= 12) {
      adjustments.push({
        points: +3,
        reason: lang === 'en'
          ? `High protein (${product.nutrition.protein_g} g/100 g) — helps prevent sarcopenia (EFSA PRI 1.0 g/kg/day for ≥65)`
          : `Protéines élevées (${product.nutrition.protein_g} g/100 g) — prévention de la sarcopénie (PRI EFSA 1,0 g/kg/j après 65 ans)`,
        category: 'age',
      });
    }
    if (profile.age_years >= 50 && product.nutrition.salt_g > 1.5) {
      adjustments.push({
        points: -3,
        reason: lang === 'en'
          ? `Salt penalty amplified after 50y (higher hypertension risk, WHO salt guideline 2012)`
          : `Pénalité sel amplifiée après 50 ans (risque d'hypertension accru, OMS 2012)`,
        category: 'age',
      });
    }
    if (profile.age_years < 18) {
      if (product.nutrition.sugars_g > 15) {
        adjustments.push({
          points: -4,
          reason: lang === 'en'
            ? `Sugar penalty amplified for under-18 (WHO added-sugar target stricter in children)`
            : `Pénalité sucres amplifiée chez les moins de 18 ans (recommandations OMS plus strictes)`,
          category: 'age',
        });
      }
      const hasColorant = product.ingredients.some((i) =>
        /\bE(102|104|110|122|124|129)\b/i.test(i.name) || !!i.e_number?.match(/^E(102|104|110|122|124|129)$/i),
      );
      if (hasColorant) {
        adjustments.push({
          points: -3,
          reason: lang === 'en'
            ? `Azo colorant under EU hyperactivity-warning label (Reg. 1333/2008) in a product for a child`
            : `Colorant azoïque avec avertissement UE hyperactivité (Règl. 1333/2008) — consommateur mineur`,
          category: 'age',
        });
      }
    }
  }

  // ===== SEX =====
  if (profile.sex === 'female' && profile.age_years != null && profile.age_years >= 13 && profile.age_years <= 50) {
    const declaresIron = product.declared_micronutrients?.some((m) => /iron|fer/i.test(m));
    if (declaresIron) {
      adjustments.push({
        points: +2,
        reason: lang === 'en'
          ? `Iron-declared product — menstruating women have higher RNI (EFSA 2015: 16 mg/day)`
          : `Fer déclaré — les femmes en âge menstruel ont un besoin plus élevé (EFSA 2015 : 16 mg/j)`,
        category: 'sex',
      });
    }
  }

  // ===== ACTIVITY =====
  if (profile.activity === 'active' || profile.activity === 'very_active') {
    if (product.nutrition.protein_g >= 15) {
      adjustments.push({
        points: +2,
        reason: lang === 'en'
          ? `High-protein product — supports athlete recovery (IOC 2018: 1.2–2.0 g/kg/day)`
          : `Protéines élevées — adaptées à la récupération sportive (CIO 2018 : 1,2–2,0 g/kg/j)`,
        category: 'activity',
      });
    }
    if (product.nutrition.sugars_g > 5 && product.nutrition.sugars_g <= 15) {
      adjustments.push({
        points: +2,
        reason: lang === 'en'
          ? `Moderate-sugar product — active lifestyle uses carbs`
          : `Sucres modérés — ton activité justifie un apport glucidique`,
        category: 'activity',
      });
    }
  }
  if (profile.activity === 'light' || profile.activity === 'moderate') {
    if (product.nutrition.protein_g >= 15) {
      adjustments.push({
        points: +1,
        reason: lang === 'en'
          ? `High protein — useful for moderate-activity adults (EFSA PRI 0.83 g/kg/day)`
          : `Protéines élevées — utiles pour un niveau d'activité modéré (PRI EFSA 0,83 g/kg/j)`,
        category: 'activity',
      });
    }
  }
  if (profile.activity === 'sedentary') {
    if (product.nutrition.sugars_g > 10) {
      adjustments.push({
        points: -3,
        reason: lang === 'en'
          ? `Sedentary lifestyle — sugar penalty amplified (higher insulin-resistance risk)`
          : `Mode de vie sédentaire — pénalité sucres amplifiée (risque accru de résistance insulinique)`,
        category: 'activity',
      });
    }
  }

  // ===== PROTEIN PRI =====
  const priTarget = proteinPRI_g(profile);
  if (priTarget != null) {
    const pctOfPRI = (product.nutrition.protein_g / priTarget) * 100;
    if (pctOfPRI >= 20) {
      adjustments.push({
        points: +2,
        reason: lang === 'en'
          ? `100 g covers ${pctOfPRI.toFixed(0)} % of your daily protein target (${priTarget} g, EFSA PRI adjusted for your weight/age)`
          : `100 g couvre ${pctOfPRI.toFixed(0)} % de ton besoin protéique journalier (${priTarget} g, PRI EFSA ajusté à ton poids/âge)`,
        category: 'bmi',
      });
    }
  }

  // ===== DAILY TARGET CONTEXT =====
  const targets = dailyTargets(profile);
  if (targets) {
    const satFatPct = (product.nutrition.saturated_fat_g / Math.max(1, targets.sat_fat_g_max)) * 100;
    if (satFatPct >= 50) {
      adjustments.push({
        points: -4,
        reason: lang === 'en'
          ? `100 g uses ${satFatPct.toFixed(0)} % of your daily sat-fat budget (${targets.sat_fat_g_max} g from TDEE×10 %E, WHO 2023)`
          : `100 g consomme ${satFatPct.toFixed(0)} % de ton budget AGS journalier (${targets.sat_fat_g_max} g selon TDEE×10 %E, OMS 2023)`,
        category: 'bmi',
      });
    }
    const sugars = product.nutrition.added_sugars_g ?? product.nutrition.sugars_g;
    const sugarPct = (sugars / Math.max(1, targets.free_sugars_g_max)) * 100;
    if (sugarPct >= 50) {
      adjustments.push({
        points: -4,
        reason: lang === 'en'
          ? `100 g uses ${sugarPct.toFixed(0)} % of your daily free-sugar budget (${targets.free_sugars_g_max} g from TDEE×10 %E, WHO 2015; ideal ${targets.free_sugars_g_ideal} g at 5 %E)`
          : `100 g consomme ${sugarPct.toFixed(0)} % de ton budget sucres libres (${targets.free_sugars_g_max} g selon TDEE×10 %E, OMS 2015 ; idéal ${targets.free_sugars_g_ideal} g à 5 %E)`,
        category: 'bmi',
      });
    }
    const saltPct = (product.nutrition.salt_g / targets.salt_g_max) * 100;
    if (saltPct >= 30) {
      adjustments.push({
        points: -3,
        reason: lang === 'en'
          ? `100 g uses ${saltPct.toFixed(0)} % of the WHO 5 g/day salt ceiling`
          : `100 g consomme ${saltPct.toFixed(0)} % du plafond OMS de 5 g/j de sel`,
        category: 'bmi',
      });
    }
  }

  // ===== BMI =====
  const bmiValue = bmi(profile);
  const bmiCat = bmiCategory(bmiValue);
  if (bmiCat === 'overweight' || bmiCat?.startsWith('obese')) {
    if (product.nutrition.saturated_fat_g > 5 || product.nutrition.sugars_g > 15) {
      adjustments.push({
        points: -4,
        reason: lang === 'en'
          ? `BMI ${bmiValue} (${bmiCat}) — high sat fat / sugar penalty amplified (WHO 2000 BMI + cardiometabolic guidance)`
          : `IMC ${bmiValue} (${bmiCat}) — pénalité accrue sur graisses saturées / sucres élevés (OMS 2000 IMC)`,
        category: 'bmi',
      });
    }
  }
  if (bmiCat === 'underweight') {
    if (product.nutrition.energy_kcal > 300 && product.nutrition.protein_g >= 8) {
      adjustments.push({
        points: +2,
        reason: lang === 'en'
          ? `BMI ${bmiValue} (underweight) — energy- and protein-dense product is supportive`
          : `IMC ${bmiValue} (insuffisance pondérale) — produit dense en énergie et protéines, bénéfique`,
        category: 'bmi',
      });
    }
  }

  const delta = adjustments.reduce((s, a) => s + a.points, 0);
  // Hard veto: if diet violated, personal score is flat 0 regardless of sum.
  let personal_score = veto ? 0 : Math.max(0, Math.min(100, audit.score + delta));

  // Fix #7 — re-apply the engine's composition veto cap AFTER the personal
  // delta. Without this, a classic score of 44 (already capped by the engine
  // at 45) plus a +3 personal delta would produce 47, silently exceeding the
  // cap. The cap must be the last operation in the scoring chain.
  if (!veto && audit.veto?.triggered && personal_score > audit.veto.cap) {
    personal_score = audit.veto.cap;
  }

  return {
    personal_score: Math.round(personal_score),
    delta: veto ? -audit.score : delta,
    adjustments,
    applicable: true,
    diet_reason: dietReason,
    veto,
  };
}

function anyModifierOn(m) {
  if (!m || typeof m !== 'object') return false;
  return !!(m.lowSugar || m.lowSalt || m.highProtein || m.organic);
}

/** Map the 0-100 personal score to the same A+/A/B/C/D/F grade scale. */
export function personalGrade(score) {
  if (score >= 85) return 'A+';
  if (score >= 70) return 'A';
  if (score >= 55) return 'B';
  if (score >= 40) return 'C';
  if (score >= 25) return 'D';
  return 'F';
}
