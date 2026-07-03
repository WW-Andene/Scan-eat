/**
 * Recipe-URL schema.org parser tests. Exercises the pure
 * parseRecipeFromHtml function against a handful of real-world
 * JSON-LD shapes recipe blogs use.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { parseRecipeFromHtml } from '../api/fetch-recipe.ts';

function html(ld: unknown): string {
  return `<!doctype html><html><head><script type="application/ld+json">${JSON.stringify(ld)}</script></head><body></body></html>`;
}

describe('parseRecipeFromHtml', () => {
  it('extracts a basic Recipe with ingredients and steps', async () => {
    const ld = {
      '@context': 'https://schema.org',
      '@type': 'Recipe',
      name: 'Pâtes pesto',
      recipeYield: '4 servings',
      recipeIngredient: ['200g pasta', '30g parmesan', '2 tbsp olive oil'],
      recipeInstructions: [
        { '@type': 'HowToStep', text: 'Boil water.' },
        { '@type': 'HowToStep', text: 'Cook pasta 10 min.' },
      ],
      totalTime: 'PT15M',
      nutrition: {
        '@type': 'NutritionInformation',
        calories: '320 kcal',
        proteinContent: '12 g',
        fatContent: '14 g',
        carbohydrateContent: '38 g',
      },
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://example.com/recipe');
    assert.ok(out);
    assert.equal(out!.name, 'Pâtes pesto');
    assert.equal(out!.servings, 4);
    assert.equal(out!.ingredients.length, 3);
    assert.equal(out!.ingredients[0], '200g pasta');
    assert.equal(out!.steps.length, 2);
    assert.equal(out!.steps[0], 'Boil water.');
    assert.equal(out!.cook_time_min, 15);
    assert.equal(out!.nutrition?.kcal, 320);
    assert.equal(out!.nutrition?.protein_g, 12);
  });

  it('follows @graph nesting (common in WordPress recipe plugins)', async () => {
    const ld = {
      '@context': 'https://schema.org',
      '@graph': [
        { '@type': 'WebSite', name: 'Blog' },
        {
          '@type': 'Recipe',
          name: 'Salade de tomates',
          recipeIngredient: ['4 tomates', '1 mozzarella'],
        },
      ],
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.ok(out);
    assert.equal(out!.name, 'Salade de tomates');
    assert.equal(out!.ingredients.length, 2);
  });

  it('handles @type as an array (["Recipe","Thing"])', async () => {
    const ld = {
      '@context': 'https://schema.org',
      '@type': ['Recipe', 'Thing'],
      name: 'Omelette',
      recipeIngredient: ['3 eggs'],
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.ok(out);
    assert.equal(out!.name, 'Omelette');
  });

  it('parses ISO duration PT1H30M correctly', async () => {
    const ld = {
      '@type': 'Recipe',
      name: 'Slow cook',
      recipeIngredient: ['x'],
      totalTime: 'PT1H30M',
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.equal(out!.cook_time_min, 90);
  });

  it('gracefully returns null when no Recipe is present', async () => {
    const ld = {
      '@type': 'BlogPosting',
      name: 'My thoughts on soup',
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.equal(out, null);
  });

  it('recipeInstructions as a single string still parses', async () => {
    const ld = {
      '@type': 'Recipe',
      name: 'Simple',
      recipeIngredient: ['x'],
      recipeInstructions: 'Mix everything. Bake 30 min.',
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.ok(out);
    assert.equal(out!.steps.length, 1);
    assert.ok(out!.steps[0].startsWith('Mix'));
  });

  it('recipeYield can be numeric', async () => {
    const ld = {
      '@type': 'Recipe',
      name: 'x',
      recipeIngredient: ['y'],
      recipeYield: 6,
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.equal(out!.servings, 6);
  });

  it('defaults servings to 1 when yield is missing', async () => {
    const ld = {
      '@type': 'Recipe',
      name: 'x',
      recipeIngredient: ['y'],
    };
    const out = await parseRecipeFromHtml(html(ld), 'https://x/y');
    assert.equal(out!.servings, 1);
  });

  it('handles HTML-escaped JSON-LD (some WP plugins double-encode)', async () => {
    const raw = `<script type="application/ld+json">{&quot;@type&quot;:&quot;Recipe&quot;,&quot;name&quot;:&quot;Escaped&quot;,&quot;recipeIngredient&quot;:[&quot;one&quot;]}</script>`;
    const out = await parseRecipeFromHtml(`<html><head>${raw}</head></html>`, 'https://x/y');
    assert.ok(out);
    assert.equal(out!.name, 'Escaped');
  });

  it('picks the first Recipe when multiple blocks are present', async () => {
    const ld1 = { '@type': 'WebSite', name: 'first' };
    const ld2 = { '@type': 'Recipe', name: 'Pancakes', recipeIngredient: ['flour'] };
    const twoBlocks = `<html><head>
      <script type="application/ld+json">${JSON.stringify(ld1)}</script>
      <script type="application/ld+json">${JSON.stringify(ld2)}</script>
    </head></html>`;
    const out = await parseRecipeFromHtml(twoBlocks, 'https://x/y');
    assert.ok(out);
    assert.equal(out!.name, 'Pancakes');
  });
});
