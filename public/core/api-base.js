/**
 * api-base.js
 *
 * The /api/* routes (score.ts, identify.ts, identify-recipe.ts, ...) are
 * Vercel serverless functions. They only exist on a deployed Vercel
 * origin — there is no backend bundled inside this APK.
 *
 * When the app runs as a normal web page on the Vercel deployment,
 * relative '/api/...' calls work fine (same origin). When it runs
 * inside the packaged APK (file:// or a builder's local asset scheme),
 * there is no '/api' to hit on that origin, so calls must be pointed
 * at the real deployment explicitly.
 *
 * Fill this in with your deployed origin, e.g.:
 *   'https://scann-eat.vercel.app'
 * Leave empty ('') to keep using same-origin relative calls (web build).
 */
export const API_BASE = '';

export function apiUrl(path) {
  // path expected like '/api/score' or '/version.json'
  if (!API_BASE) return path;
  return API_BASE.replace(/\/$/, '') + path;
}
