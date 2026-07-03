/**
 * Gap fixes #24 + #25 — allergen + diet checks for user-typed
 * content (Quick Add entries, recipes, templates, menu-scan
 * dishes).
 *
 * Both upstream helpers (`detectAllergens`, `checkDiet`) expect a
 * "product" shape { name, ingredients: [{ name }] }. Pure user-typed
 * content carries loose strings; this module synthesises the right
 * shape from each carrier type then delegates.
 *
 * Keeps call sites tiny: one `checkRecipeWarnings(recipe, profile,
 * lang)` returns `{ allergens, dietViolations }` ready to render.
 */

import { detectAllergens } from './allergens.js';
import { checkDiet } from './diets.js';

function toProduct(name, ingredientStrings) {
  return {
    name: String(name || ''),
    ingredients: (ingredientStrings || []).map((s) => ({
      name: String(s || ''),
    })),
  };
}

/**
 * Recipe warnings — runs allergen + diet detection on the component
 * names + recipe name. Returns:
 *   { allergens: [{ key, label, triggers }], dietViolations: [...] }
 * Pass a fresh `profile` each call; the diet check is skipped when
 * profile.diet is 'none' or missing.
 */
export function checkRecipeWarnings(recipe, profile, lang = 'fr') {
  const names = (recipe?.components || []).map((c) => c?.product_name || '');
  const product = toProduct(recipe?.name, names);
  const allergens = detectAllergens(product, lang);
  const diet = profile?.diet && profile.diet !== 'none'
    ? checkDiet(product, profile.diet, profile.custom_diet, lang)
    : { compliant: true, violations: [] };
  return {
    allergens,
    dietViolations: diet.compliant ? [] : diet.violations,
  };
}

/**
 * Template warnings — same contract, but a template stores
 * items[] with product_name fields.
 */
export function checkTemplateWarnings(template, profile, lang = 'fr') {
  const names = (template?.items || []).map((i) => i?.product_name || '');
  const product = toProduct(template?.name, names);
  const allergens = detectAllergens(product, lang);
  const diet = profile?.diet && profile.diet !== 'none'
    ? checkDiet(product, profile.diet, profile.custom_diet, lang)
    : { compliant: true, violations: [] };
  return {
    allergens,
    dietViolations: diet.compliant ? [] : diet.violations,
  };
}

/**
 * Quick Add name check — single-string input. Useful for the qa-save
 * path to warn the user before they log something that violates
 * their declared diet or contains a declared allergen.
 */
export function checkQuickAddWarnings(name, profile, lang = 'fr') {
  const trimmed = String(name || '').trim();
  if (!trimmed) return { allergens: [], dietViolations: [] };
  // Treat the typed name as both the product name AND the sole
  // ingredient — allergen regex + diet haystack both read it.
  const product = toProduct(trimmed, [trimmed]);
  const allergens = detectAllergens(product, lang);
  const diet = profile?.diet && profile.diet !== 'none'
    ? checkDiet(product, profile.diet, profile.custom_diet, lang)
    : { compliant: true, violations: [] };
  return {
    allergens,
    dietViolations: diet.compliant ? [] : diet.violations,
  };
}
