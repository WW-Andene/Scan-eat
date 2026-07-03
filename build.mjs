/**
 * Bundles the TS engine (src/scoring-engine.ts + src/ocr-parser.ts + src/off.ts)
 * into a single browser-friendly ESM file at public/engine.bundle.js.
 *
 * Also writes public/version.json with the current git commit so the running
 * app (web or APK) can self-compare against the latest GitHub Release and
 * offer an in-place update.
 */

import { build } from 'esbuild';
import { execSync } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';

let commit = process.env.APP_COMMIT;
if (!commit) {
  try {
    commit = execSync('git rev-parse --short HEAD', { encoding: 'utf8' }).trim();
  } catch {
    commit = 'dev';
  }
}
const builtAt = new Date().toISOString();

await writeFile(
  'public/version.json',
  JSON.stringify({ commit, built_at: builtAt }, null, 2) + '\n',
);

// Auto-bump the service-worker cache key to the current commit so browsers
// pick up fresh shell assets without us remembering to edit the SW manually.
// Any placeholder-style `const CACHE = '...';` on the first line works.
try {
  const swPath = 'public/service-worker.js';
  const src = await readFile(swPath, 'utf8');
  const next = src.replace(
    /const CACHE = '[^']+';/,
    `const CACHE = 'scann-eat-shell-${commit}';`,
  );
  if (next !== src) await writeFile(swPath, next);
} catch (err) {
  console.warn('[build] could not bump SW cache:', err?.message || err);
}

await build({
  stdin: {
    contents: `
      export { parseLabel, parseIngredientsText, identifyFood, identifyMultiFood, identifyMenu, identifyRecipe, suggestRecipes, suggestRecipesFromPantry } from './src/ocr-parser.ts';
      export { scoreProduct, ADDITIVES_DB, ENGINE_VERSION, inferCategoryFromName, findAdditive, countTier1Additives, getThresholds, normalize } from './src/scoring-engine.ts';
      export { fetchFromOFF, searchOFFByCategory, rankAlternatives, suggestionTagFor, isOFFSparse, mergeOFFWithLLM, detectSourceConflicts } from './src/off.ts';
    `,
    resolveDir: '.',
    loader: 'ts',
  },
  bundle: true,
  format: 'esm',
  outfile: 'public/engine.bundle.js',
  target: 'es2022',
  platform: 'browser',
  sourcemap: false,
  minify: false,
  logLevel: 'info',
});

console.log(`[build] commit=${commit}`);

