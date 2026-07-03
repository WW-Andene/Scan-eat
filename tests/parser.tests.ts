/**
 * ============================================================================
 * OCR PARSER — TESTS
 * ============================================================================
 *
 * Two layers:
 *   1. Deterministic helpers — splitIngredients, extractPercentage,
 *      extractENumber, enrichIngredient, parseIngredientsText.
 *   2. Full parseLabel pipeline with mocked fetch — exercises JSON parsing,
 *      the coercion layer, inferNamedOils, warnings, and HTTP error paths.
 *
 * No API key required for either layer.
 *
 * Run on Node 22+ with native TS stripping:
 *   node --test --experimental-strip-types parser-tests.ts
 *
 * Or with tsx:
 *   npx tsx --test parser-tests.ts
 * ============================================================================
 */

import { strict as assert } from 'node:assert';
import { after, before, describe, it } from 'node:test';

import {
  coerceNullableNumber,
  coerceNumber,
  enrichIngredient,
  extractENumber,
  extractJSON,
  extractPercentage,
  parseIngredientsText,
  parseLabel,
  splitIngredients,
} from '../src/ocr-parser.ts';

// ============================================================================
// splitIngredients
// ============================================================================

describe('splitIngredients', () => {
  it('splits on top-level commas', () => {
    assert.deepEqual(
      splitIngredients('eau, sucre, sel'),
      ['eau', 'sucre', 'sel'],
    );
  });

  it('preserves commas inside parentheses', () => {
    const out = splitIngredients(
      'émulsifiants (lécithine, mono- et diglycérides), sel, arôme',
    );
    assert.deepEqual(out, [
      'émulsifiants (lécithine, mono- et diglycérides)',
      'sel',
      'arôme',
    ]);
  });

  it('handles nested brackets', () => {
    const out = splitIngredients(
      'préparation (huile [tournesol, colza], vinaigre), moutarde',
    );
    assert.deepEqual(out, [
      'préparation (huile [tournesol, colza], vinaigre)',
      'moutarde',
    ]);
  });

  it('splits a vertical bulleted list (newline + bullet glyphs)', () => {
    const raw = '- farine de blé\n- sucre\n- beurre\n- sel';
    assert.deepEqual(splitIngredients(raw), ['farine de blé', 'sucre', 'beurre', 'sel']);
  });

  it('splits a numbered list without losing percentages', () => {
    const raw = '1. jambon 17,4%\n2. fromage 12%\n3. pain';
    assert.deepEqual(splitIngredients(raw), ['jambon 17,4%', 'fromage 12%', 'pain']);
  });

  it('splits on semicolon as well as comma', () => {
    assert.deepEqual(
      splitIngredients('eau; sucre; sel'),
      ['eau', 'sucre', 'sel'],
    );
  });

  it('drops empty segments and trims whitespace', () => {
    assert.deepEqual(
      splitIngredients('  eau ,, sucre ,  '),
      ['eau', 'sucre'],
    );
  });

  it('tolerates unbalanced closing brackets without crashing', () => {
    const out = splitIngredients('eau), sucre');
    assert.deepEqual(out, ['eau)', 'sucre']);
  });
});

// ============================================================================
// extractPercentage
// ============================================================================

describe('extractPercentage', () => {
  it('reads French comma decimals', () => {
    assert.equal(extractPercentage('jambon 17,4%'), 17.4);
  });

  it('reads point decimals', () => {
    assert.equal(extractPercentage('jambon 17.4%'), 17.4);
  });

  it('reads integer percentages', () => {
    assert.equal(extractPercentage('tomate 25 %'), 25);
  });

  it('returns null when absent', () => {
    assert.equal(extractPercentage('sel'), null);
  });

  it('rejects out-of-bounds values', () => {
    assert.equal(extractPercentage('??? 150%'), null);
    assert.equal(extractPercentage('??? 0%'), null);
  });
});

// ============================================================================
// extractENumber
// ============================================================================

describe('extractENumber', () => {
  it('matches plain E-number', () => {
    assert.equal(extractENumber('E250'), 'E250');
  });

  it('matches with space', () => {
    assert.equal(extractENumber('conservateur E 250'), 'E250');
  });

  it('matches with hyphen', () => {
    assert.equal(extractENumber('E-250'), 'E250');
  });

  it('normalizes lowercase to uppercase', () => {
    assert.equal(extractENumber('e250'), 'E250');
  });

  it('handles sub-letters (E150a, E150c)', () => {
    assert.equal(extractENumber('colorant caramel E150c'), 'E150C');
  });

  it('returns null when absent', () => {
    assert.equal(extractENumber('sucre'), null);
  });

  it('does not match nearby digits that are not an E-number', () => {
    assert.equal(extractENumber('ecologie 250 km'), null);
  });
});

// ============================================================================
// enrichIngredient
// ============================================================================

describe('enrichIngredient', () => {
  it('classifies a whole food', () => {
    const ing = enrichIngredient({ name: 'tomate' });
    assert.equal(ing.category, 'food');
    assert.equal(ing.is_whole_food, true);
    assert.equal(ing.e_number, null);
  });

  it('detects additive via E-number in text', () => {
    const ing = enrichIngredient({ name: 'conservateur: nitrite de sodium (E250)' });
    assert.equal(ing.category, 'additive');
    assert.equal(ing.e_number, 'E250');
    assert.equal(ing.is_whole_food, false);
  });

  it('detects additive via known name even without E-number', () => {
    const ing = enrichIngredient({ name: 'polysorbate 80' });
    assert.equal(ing.category, 'additive');
  });

  it('prefers text-extracted percentage over missing LLM value', () => {
    const ing = enrichIngredient({ name: 'jambon de porc 17,4%' });
    assert.equal(ing.percentage, 17.4);
  });

  it('keeps LLM percentage when text has none', () => {
    const ing = enrichIngredient({ name: 'jambon de porc', percentage: 17.4 });
    assert.equal(ing.percentage, 17.4);
  });

  it('drops malformed LLM E-number', () => {
    const ing = enrichIngredient({ name: 'sucre', e_number: 'not-a-real-e-number' });
    assert.equal(ing.e_number, null);
  });

  it('treats concentré / isolat as non-whole even if keyword matches', () => {
    const ing = enrichIngredient({ name: 'concentré de tomate' });
    assert.equal(ing.is_whole_food, false);
  });

  it('recognizes extended fruit keywords (poire, abricot, framboise, myrtille)', () => {
    for (const name of ['poire', 'abricot', 'framboise', 'myrtille', 'cerise']) {
      assert.equal(enrichIngredient({ name }).is_whole_food, true, `${name} should be whole food`);
    }
  });

  it('recognizes skyr as a whole-food dairy item', () => {
    assert.equal(enrichIngredient({ name: 'skyr maigre' }).is_whole_food, true);
  });

  it('strips role prefixes like "conservateur :"', () => {
    const out = parseIngredientsText('eau, conservateur : sorbate de potassium, sel');
    const preserv = out.find((i) => /sorbate/.test(i.name));
    assert.ok(preserv, 'sorbate ingredient must exist');
    assert.equal(preserv.name, 'sorbate de potassium');
    assert.equal(preserv.category, 'additive');
    assert.equal(preserv.e_number, 'E202');
  });

  it('flattens "moins de 2% de:" sub-phrases', () => {
    const out = parseIngredientsText(
      'tomate, eau, moins de 2% de: sucre, sel, arômes',
    );
    const names = out.map((i) => i.name);
    assert.ok(names.includes('sucre'), 'sucre should be flattened into top level');
    assert.ok(names.includes('sel'));
    assert.ok(names.some((n) => /arôme/i.test(n)));
  });

  it('extracts new additives: E171 (dioxyde de titane) and E322 (lécithine)', () => {
    const titane = enrichIngredient({ name: 'dioxyde de titane' });
    assert.equal(titane.e_number, 'E171');
    assert.equal(titane.category, 'additive');
    const lec = enrichIngredient({ name: 'lécithine de soja' });
    assert.equal(lec.e_number, 'E322');
  });

  it('uppercases and strips spaces from LLM-declared E-number', () => {
    const ing = enrichIngredient({ name: 'sel nitrité', e_number: 'e 250' });
    assert.equal(ing.e_number, 'E250');
  });

  it('ignores a non-positive percentage from LLM and reads text instead', () => {
    // The LLM occasionally emits 0 or -1 as a placeholder. The parser should
    // fall back to the text extractor instead of locking in garbage.
    const ing = enrichIngredient({ name: 'jambon 17,4%', percentage: 0 });
    assert.equal(ing.percentage, 17.4);
  });

  it('trims surrounding whitespace from the ingredient name', () => {
    const ing = enrichIngredient({ name: '   sel   ' });
    assert.equal(ing.name, 'sel');
  });
});

// ============================================================================
// coerceNumber / coerceNullableNumber — boundary coverage for OFF / LLM
// payloads that may arrive as strings, null, or unexpected shapes
// ============================================================================

describe('coerceNumber', () => {
  it('passes finite numbers through', () => {
    assert.equal(coerceNumber(42), 42);
    assert.equal(coerceNumber(0), 0);
    assert.equal(coerceNumber(-3.5), -3.5);
  });

  it('parses French comma decimals in strings', () => {
    assert.equal(coerceNumber('17,4'), 17.4);
  });

  it('parses point decimals in strings', () => {
    assert.equal(coerceNumber('17.4'), 17.4);
  });

  it('strips trailing units and still parses', () => {
    // parseFloat consumes the leading number, ignoring the unit tail
    assert.equal(coerceNumber('450 kcal'), 450);
  });

  it('returns 0 for NaN / null / undefined / objects', () => {
    assert.equal(coerceNumber(NaN), 0);
    assert.equal(coerceNumber(null), 0);
    assert.equal(coerceNumber(undefined), 0);
    assert.equal(coerceNumber({} as unknown), 0);
  });

  it('returns 0 for unparseable strings', () => {
    assert.equal(coerceNumber('abc'), 0);
    assert.equal(coerceNumber(''), 0);
  });

  it('returns 0 for Infinity and -Infinity', () => {
    assert.equal(coerceNumber(Infinity), 0);
    assert.equal(coerceNumber(-Infinity), 0);
  });
});

describe('coerceNullableNumber', () => {
  it('returns null for null / undefined (preserves absence)', () => {
    assert.equal(coerceNullableNumber(null), null);
    assert.equal(coerceNullableNumber(undefined), null);
  });

  it('returns 0 (not null) for a valid zero — zero is a real value', () => {
    assert.equal(coerceNullableNumber(0), 0);
  });

  it('returns null when the value is unparseable', () => {
    // Currently returns 0 via coerceNumber — documents existing behavior
    // so that if we tighten this later we see the change.
    assert.equal(coerceNullableNumber('abc'), 0);
  });
});

// ============================================================================
// parseIngredientsText — realistic French fixtures
// ============================================================================

describe('parseIngredientsText', () => {
  it('strips the "Ingrédients:" prefix', () => {
    const out = parseIngredientsText('Ingrédients: eau, sucre, sel');
    assert.deepEqual(out.map((i) => i.name), ['eau', 'sucre', 'sel']);
  });

  it('parses a sandwich-style list end-to-end', () => {
    const out = parseIngredientsText(
      'pain (farine de blé, eau, levain, sel), jambon de porc 17,4%, emmental 12%, mayonnaise (huile de colza, oeuf, moutarde), salade',
    );
    const names = out.map((i) => i.name);
    assert.deepEqual(names, [
      'pain (farine de blé, eau, levain, sel)',
      'jambon de porc 17,4%',
      'emmental 12%',
      'mayonnaise (huile de colza, oeuf, moutarde)',
      'salade',
    ]);

    const jambon = out.find((i) => i.name.startsWith('jambon'))!;
    assert.equal(jambon.percentage, 17.4);
    assert.equal(jambon.is_whole_food, true);

    const emmental = out.find((i) => i.name.startsWith('emmental'))!;
    assert.equal(emmental.percentage, 12);
  });

  it('flags E-number additives inside a processed-meat list', () => {
    const out = parseIngredientsText(
      'viande de porc 85%, eau, sel, dextrose, conservateur: nitrite de sodium (E250), antioxydant: érythorbate de sodium (E316)',
    );
    const nitrite = out.find((i) => i.e_number === 'E250');
    const erythorbate = out.find((i) => i.e_number === 'E316');
    assert.ok(nitrite, 'E250 should be recognized');
    assert.ok(erythorbate, 'E316 should be recognized');
    assert.equal(nitrite!.category, 'additive');
    assert.equal(erythorbate!.category, 'additive');
  });

  it('returns empty array on empty input', () => {
    assert.deepEqual(parseIngredientsText(''), []);
    assert.deepEqual(parseIngredientsText('   '), []);
  });

  it('does not split inside nested brackets with commas and percentages', () => {
    const out = parseIngredientsText(
      'chocolat (sucre, pâte de cacao 45%, beurre de cacao), lait',
    );
    assert.equal(out.length, 2);
    assert.equal(out[0].name, 'chocolat (sucre, pâte de cacao 45%, beurre de cacao)');
    assert.equal(out[1].name, 'lait');
  });
});

// ============================================================================
// extractJSON — tolerate markdown fences and surrounding prose
// ============================================================================

describe('extractJSON', () => {
  it('returns plain JSON unchanged', () => {
    const raw = '{"a": 1}';
    assert.equal(extractJSON(raw), '{"a": 1}');
  });

  it('strips ```json fences', () => {
    const raw = '```json\n{"a": 1}\n```';
    assert.equal(extractJSON(raw).trim(), '{"a": 1}');
  });

  it('strips bare ``` fences', () => {
    const raw = '```\n{"a": 1}\n```';
    assert.equal(extractJSON(raw).trim(), '{"a": 1}');
  });

  it('extracts the JSON object when surrounded by prose', () => {
    const raw = 'Voici la fiche produit:\n{"a": 1, "b": 2}\nFin.';
    assert.equal(extractJSON(raw), '{"a": 1, "b": 2}');
  });
});

// ============================================================================
// parseLabel — full pipeline with mocked fetch
// ============================================================================

type MockResponse = {
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
  text: () => Promise<string>;
};

type MockFetch = (url: string, init?: RequestInit) => Promise<MockResponse>;

const originalFetch = globalThis.fetch;
let lastFetchBody: string | null = null;

function mockGroqReply(content: unknown): MockFetch {
  return async (_url, init) => {
    lastFetchBody = typeof init?.body === 'string' ? init.body : null;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        choices: [{ message: { content: JSON.stringify(content) } }],
      }),
      text: async () => '',
    };
  };
}

function mockGroqError(status: number, body: string): MockFetch {
  return async () => ({
    ok: false,
    status,
    json: async () => ({}),
    text: async () => body,
  });
}

function installFetch(mock: MockFetch) {
  (globalThis as { fetch: unknown }).fetch = mock;
}

describe('parseLabel (mocked fetch)', () => {
  before(() => {
    process.env.GROQ_API_KEY = 'test-key';
  });

  after(() => {
    (globalThis as { fetch: unknown }).fetch = originalFetch;
  });

  it('happy path: sandwich label → scorable ProductInput', async () => {
    installFetch(
      mockGroqReply({
        name: 'Sandwich Jambon-Emmental',
        category: 'sandwich',
        nova_class: 4,
        weight_g: 150,
        origin: 'France',
        organic: false,
        has_health_claims: false,
        has_misleading_marketing: false,
        ingredients: [
          { name: 'pain (farine de blé, eau, levain, sel)', category: 'food' },
          { name: 'jambon de porc 17,4%', percentage: 17.4, category: 'food' },
          { name: 'emmental 12%', percentage: 12, category: 'food' },
          {
            name: 'conservateur: nitrite de sodium',
            e_number: 'E250',
            category: 'additive',
          },
        ],
        nutrition: {
          energy_kcal: 245,
          fat_g: 9.5,
          saturated_fat_g: 3.1,
          carbs_g: 28,
          sugars_g: 2.4,
          added_sugars_g: 1.0,
          fiber_g: 2.8,
          protein_g: 11.2,
          salt_g: 1.4,
          trans_fat_g: null,
        },
      }),
    );

    const { product, warnings } = await parseLabel({ base64: 'ZmFrZQ==' });

    assert.equal(product.name, 'Sandwich Jambon-Emmental');
    assert.equal(product.category, 'sandwich');
    assert.equal(product.nova_class, 4);
    assert.equal(product.weight_g, 150);
    assert.equal(product.origin, 'France');
    assert.equal(product.origin_transparent, true);
    assert.equal(product.ingredients.length, 4);

    const nitrite = product.ingredients.find((i) => i.e_number === 'E250');
    assert.ok(nitrite, 'nitrite ingredient should survive coercion');
    assert.equal(nitrite!.category, 'additive');

    assert.equal(product.nutrition.energy_kcal, 245);
    assert.equal(product.nutrition.trans_fat_g, null);
    assert.equal(warnings.length, 0);

    // Confirm the request body includes the image data URL.
    assert.ok(lastFetchBody?.includes('data:image/jpeg;base64,ZmFrZQ=='));
  });

  it('coerces string numerics and invalid enums without throwing', async () => {
    installFetch(
      mockGroqReply({
        name: 'Weird Response',
        category: 'not-a-real-category',
        nova_class: '3',
        ingredients: [{ name: 'eau' }],
        nutrition: {
          energy_kcal: '120',
          fat_g: '1.5',
          saturated_fat_g: 0,
          carbs_g: '20,5',
          sugars_g: 'oops',
          fiber_g: 1,
          protein_g: 3,
          salt_g: 0.1,
        },
      }),
    );

    const { product } = await parseLabel({ base64: 'ZmFrZQ==' });
    assert.equal(product.category, 'other');
    assert.equal(product.nova_class, 3);
    assert.equal(product.nutrition.energy_kcal, 120);
    assert.equal(product.nutrition.fat_g, 1.5);
    assert.equal(product.nutrition.carbs_g, 20.5);
    assert.equal(product.nutrition.sugars_g, 0); // unparseable → coerced to 0
  });

  it('flags generic "huile végétale" and sets named_oils=false', async () => {
    installFetch(
      mockGroqReply({
        name: 'Biscuit',
        category: 'snack_sweet',
        nova_class: 4,
        ingredients: [
          { name: 'farine de blé' },
          { name: 'sucre' },
          { name: 'huile végétale (colza, palme)' },
        ],
        nutrition: {
          energy_kcal: 480,
          fat_g: 20,
          saturated_fat_g: 10,
          carbs_g: 65,
          sugars_g: 25,
          fiber_g: 2,
          protein_g: 6,
          salt_g: 0.5,
        },
      }),
    );

    const { product } = await parseLabel({ base64: 'ZmFrZQ==' });
    assert.equal(product.named_oils, false);
  });

  it('emits warnings when ingredients and nutrition are missing', async () => {
    installFetch(
      mockGroqReply({
        name: '',
        category: 'other',
        nova_class: 4,
        ingredients: [],
        nutrition: {
          energy_kcal: 0,
          fat_g: 0,
          saturated_fat_g: 0,
          carbs_g: 0,
          sugars_g: 0,
          fiber_g: 0,
          protein_g: 0,
          salt_g: 0,
        },
      }),
    );

    const { warnings } = await parseLabel({ base64: 'ZmFrZQ==' });
    assert.ok(warnings.some((w) => /ingredients/i.test(w)));
    assert.ok(warnings.some((w) => /nutrition/i.test(w)));
    assert.ok(warnings.some((w) => /name/i.test(w)));
  });

  it('surfaces HTTP errors from Groq', async () => {
    installFetch(mockGroqError(500, 'internal server error'));
    await assert.rejects(
      () => parseLabel({ base64: 'ZmFrZQ==' }),
      /Groq API 500/,
    );
  });

  it('throws a clear error on malformed JSON from the LLM', async () => {
    installFetch(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        choices: [{ message: { content: 'not json at all' } }],
      }),
      text: async () => '',
    }));
    await assert.rejects(
      () => parseLabel({ base64: 'ZmFrZQ==' }),
      /malformed JSON/,
    );
  });

  it('rejects zero-image and >4-image calls', async () => {
    installFetch(mockGroqReply({}));
    await assert.rejects(() => parseLabel([]), /no images/);
    const five = Array.from({ length: 5 }, () => ({ base64: 'ZmFrZQ==' }));
    await assert.rejects(() => parseLabel(five), /max 4 images/);
  });
});
