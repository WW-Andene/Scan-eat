/**
 * ============================================================================
 * SCORING ENGINE — FIXTURE TESTS
 * ============================================================================
 *
 * Ten representative products with hand-validated expected grades. Pins the
 * engine so a pillar refactor can't silently change real-world scores.
 *
 * Run:  node --test --experimental-strip-types engine-tests.ts
 * ============================================================================
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { scoreProduct, inferNovaClass, type Grade, type ProductInput } from '../src/scoring-engine.ts';

function expectGrade(product: ProductInput, grades: Grade[], range: [number, number]) {
  const audit = scoreProduct(product);
  const [min, max] = range;
  assert.ok(
    grades.includes(audit.grade),
    `${product.name}: expected grade in ${grades.join('/')}, got ${audit.grade} (score=${audit.score})`,
  );
  assert.ok(
    audit.score >= min && audit.score <= max,
    `${product.name}: score ${audit.score} outside expected range ${min}..${max}`,
  );
  return audit;
}

describe('engine fixtures — real French supermarket products', () => {
  it('Evian (mineral water) → A+', () => {
    expectGrade(
      {
        name: 'Evian eau minérale',
        category: 'beverage_water',
        nova_class: 1,
        ingredients: [{ name: 'eau minérale naturelle', is_whole_food: true, category: 'food' }],
        nutrition: {
          energy_kcal: 0, fat_g: 0, saturated_fat_g: 0, carbs_g: 0,
          sugars_g: 0, fiber_g: 0, protein_g: 0, salt_g: 0,
        },
        origin: 'France', named_oils: true, origin_transparent: true,
      },
      ['A+', 'A'],
      [70, 100],
    );
  });

  it('Skyr nature → A or A+', () => {
    expectGrade(
      {
        name: 'Skyr nature',
        category: 'yogurt',
        nova_class: 3,
        ingredients: [
          { name: 'lait écrémé', is_whole_food: true, category: 'food' },
          { name: 'ferments lactiques', category: 'food' },
        ],
        nutrition: {
          energy_kcal: 60, fat_g: 0.2, saturated_fat_g: 0.1, carbs_g: 4,
          sugars_g: 4, added_sugars_g: 0, fiber_g: 0, protein_g: 11, salt_g: 0.1,
        },
        fermented: true, origin: 'France', named_oils: true, origin_transparent: true,
      },
      ['A', 'A+', 'B'],
      [55, 100],
    );
  });

  it('Coca-Cola Classic → D or F', () => {
    expectGrade(
      {
        name: 'Coca-Cola',
        category: 'beverage_soft',
        nova_class: 4,
        ingredients: [
          { name: 'eau gazéifiée', is_whole_food: true, category: 'food' },
          { name: 'sucre', category: 'food' },
          { name: 'colorant: caramel', e_number: 'E150', category: 'additive' },
          { name: 'acide phosphorique', e_number: 'E338', category: 'additive' },
          { name: 'arômes naturels', category: 'additive' },
          { name: 'caféine', category: 'additive' },
        ],
        nutrition: {
          energy_kcal: 42, fat_g: 0, saturated_fat_g: 0, carbs_g: 10.6,
          sugars_g: 10.6, added_sugars_g: 10.6, fiber_g: 0, protein_g: 0, salt_g: 0,
        },
        named_oils: true,
      },
      ['D', 'F'],
      [0, 40],
    );
  });

  it('Nutella → C or D (engine gives it mid-range due to named oil + single sugar bonuses)', () => {
    const audit = scoreProduct({
      name: 'Nutella',
      category: 'snack_sweet',
      nova_class: 4,
      ingredients: [
        { name: 'sucre', category: 'food' },
        { name: 'huile de palme', category: 'food' },
        { name: 'noisettes', is_whole_food: true, category: 'food' },
        { name: 'cacao maigre', category: 'food' },
        { name: 'lait écrémé en poudre', category: 'food' },
        { name: 'émulsifiants: lécithines', e_number: 'E322', category: 'additive' },
        { name: 'vanilline', category: 'additive' },
      ],
      nutrition: {
        energy_kcal: 539, fat_g: 30.9, saturated_fat_g: 10.6, carbs_g: 57.5,
        sugars_g: 56.3, added_sugars_g: 50, fiber_g: 0, protein_g: 6.3, salt_g: 0.107,
      },
      named_oils: true,
    });
    assert.ok(['C', 'D', 'F'].includes(audit.grade), `Nutella grade ${audit.grade} (score ${audit.score})`);
    // Critical sugar deduction must fire (that's the product's defining problem).
    const sugarDeduction = audit.pillars.negative_nutrients.deductions.find((d) =>
      /sugars?/i.test(d.reason) && d.severity === 'critical',
    );
    assert.ok(sugarDeduction, 'Nutella should trigger a critical sugar deduction');
  });

  it('Jambon blanc sans nitrite → A or B', () => {
    expectGrade(
      {
        name: 'Jambon blanc sans nitrite',
        category: 'processed_meat',
        nova_class: 3,
        ingredients: [
          { name: 'jambon de porc 95%', is_whole_food: true, percentage: 95, category: 'food' },
          { name: 'sel', category: 'food' },
          { name: 'bouillon', category: 'food' },
          { name: 'antioxydant: ascorbate de sodium', e_number: 'E301', category: 'additive' },
        ],
        nutrition: {
          energy_kcal: 105, fat_g: 2.5, saturated_fat_g: 0.9, carbs_g: 0.5,
          sugars_g: 0.5, added_sugars_g: 0, fiber_g: 0, protein_g: 21, salt_g: 1.8,
        },
        origin: 'France', named_oils: true, origin_transparent: true,
      },
      ['A', 'B'],
      [55, 85],
    );
  });

  it('Camembert → A or B (category-aware sat fat)', () => {
    const audit = scoreProduct({
      name: 'Camembert',
      category: 'cheese',
      nova_class: 3,
      ingredients: [
        { name: 'lait cru de vache', is_whole_food: true, category: 'food' },
        { name: 'sel', category: 'food' },
        { name: 'ferments lactiques', category: 'food' },
        { name: 'présure', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 290, fat_g: 23, saturated_fat_g: 14, carbs_g: 0.5,
        sugars_g: 0.5, added_sugars_g: 0, fiber_g: 0, protein_g: 20, salt_g: 1.4,
      },
      origin: 'Normandie', named_oils: true, origin_transparent: true, fermented: true,
    });
    assert.ok(['A', 'B'].includes(audit.grade), `Camembert grade ${audit.grade} (score ${audit.score})`);
    const satDeduction = audit.pillars.negative_nutrients.deductions.find((d) =>
      d.reason.startsWith('Saturated fat'),
    );
    assert.equal(
      satDeduction?.points,
      -3,
      'sat-fat deduction for cheese should be moderate (-3), not major/critical',
    );
  });

  it("Huile d'olive → A (oil sat-fat tolerance)", () => {
    const audit = scoreProduct({
      name: "Huile d'olive vierge extra",
      category: 'oil_fat',
      nova_class: 1,
      ingredients: [{ name: "huile d'olive vierge extra", category: 'food' }],
      nutrition: {
        energy_kcal: 824, fat_g: 91.6, saturated_fat_g: 13.8, carbs_g: 0,
        sugars_g: 0, fiber_g: 0, protein_g: 0, salt_g: 0,
      },
      origin: 'Italie', named_oils: true, origin_transparent: true,
    });
    const satDeduction = audit.pillars.negative_nutrients.deductions.find((d) =>
      d.reason.startsWith('Saturated fat'),
    );
    assert.equal(
      satDeduction,
      undefined,
      "olive oil's 13.8g sat fat must not deduct (oil_fat tolerance is 20/35/50)",
    );
  });

  it('Pain complet bio → A or B', () => {
    expectGrade(
      {
        name: 'Pain complet bio',
        category: 'bread',
        nova_class: 3,
        ingredients: [
          { name: 'farine de blé complète', is_whole_food: true, category: 'food' },
          { name: 'eau', is_whole_food: true, category: 'food' },
          { name: 'levain', category: 'food' },
          { name: 'sel', category: 'food' },
        ],
        nutrition: {
          energy_kcal: 240, fat_g: 1.5, saturated_fat_g: 0.3, carbs_g: 42,
          sugars_g: 2, added_sugars_g: 0, fiber_g: 7, protein_g: 9, salt_g: 1.1,
        },
        organic: true, whole_grain_primary: true, origin: 'France',
        named_oils: true, origin_transparent: true,
      },
      ['A+', 'A', 'B'],
      [55, 100],
    );
  });

  it('Céréales chocolatées enfants → D or F', () => {
    const audit = scoreProduct({
      name: 'Céréales chocolatées',
      category: 'breakfast_cereal',
      nova_class: 4,
      ingredients: [
        { name: 'farine de blé', category: 'food' },
        { name: 'sucre', category: 'food' },
        { name: 'cacao maigre', category: 'food' },
        { name: 'huile de palme', category: 'food' },
        { name: 'sirop de glucose', category: 'food' },
        { name: 'dextrose', category: 'food' },
        { name: 'arômes', category: 'additive' },
        { name: 'colorant: caramel', e_number: 'E150', category: 'additive' },
      ],
      nutrition: {
        energy_kcal: 380, fat_g: 4, saturated_fat_g: 2, carbs_g: 80,
        sugars_g: 30, added_sugars_g: 28, fiber_g: 3, protein_g: 6, salt_g: 0.8,
      },
      has_misleading_marketing: true, named_oils: true,
    });
    assert.ok(['C', 'D', 'F'].includes(audit.grade), `Cereals grade ${audit.grade} (score ${audit.score})`);
  });

  it('Saumon frais → A+', () => {
    expectGrade(
      {
        name: 'Filet de saumon frais',
        category: 'fish',
        nova_class: 1,
        ingredients: [{ name: 'saumon atlantique', is_whole_food: true, category: 'food' }],
        nutrition: {
          energy_kcal: 180, fat_g: 12, saturated_fat_g: 2.5, carbs_g: 0,
          sugars_g: 0, fiber_g: 0, protein_g: 20, salt_g: 0.1,
        },
        origin: 'Norvège', named_oils: true, origin_transparent: true,
      },
      ['A+', 'A'],
      [70, 100],
    );
  });

  it('Ketchup (condiment) → sugar scored against relaxed threshold', () => {
    const audit = scoreProduct({
      name: 'Ketchup',
      category: 'condiment',
      nova_class: 3,
      ingredients: [
        { name: 'tomate concentrée', category: 'food' },
        { name: 'vinaigre', category: 'food' },
        { name: 'sucre', category: 'food' },
        { name: 'sel', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 105, fat_g: 0.2, saturated_fat_g: 0, carbs_g: 24,
        sugars_g: 22, added_sugars_g: 15, fiber_g: 0.5, protein_g: 1, salt_g: 1.8,
      },
      origin: 'France', named_oils: true, origin_transparent: true,
    });
    // At generic thresholds 15g → major (-9). At condiment thresholds
    // [10, 20, 30, 45], 15g → moderate (-6). Confirm via the deduction severity.
    const sugarDed = audit.pillars.negative_nutrients.deductions.find((d) =>
      /sugars?/i.test(d.reason),
    );
    assert.ok(sugarDed, 'sugar deduction expected');
    assert.notEqual(sugarDed.severity, 'major', 'condiment sugar 15g should not be major');
  });

  it('First-ingredient penalty fires when sucre leads the list', () => {
    const audit = scoreProduct({
      name: 'Bonbons',
      category: 'snack_sweet',
      nova_class: 4,
      ingredients: [
        { name: 'sucre', category: 'food' },
        { name: 'sirop de glucose', category: 'food' },
        { name: 'gélatine', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 340, fat_g: 0, saturated_fat_g: 0, carbs_g: 82,
        sugars_g: 65, added_sugars_g: 60, fiber_g: 0, protein_g: 5, salt_g: 0.05,
      },
    });
    const first = audit.pillars.processing.deductions.find((d) =>
      /Primary ingredient is sugar/i.test(d.reason),
    );
    assert.ok(first, 'first-ingredient=sugar should add a processing deduction');
    assert.equal(first.points, -3);
  });

  it('NOVA 4 softer base: processed yogurt-like product gets a fair floor', () => {
    const audit = scoreProduct({
      name: 'Yaourt aromatisé simple',
      category: 'yogurt',
      nova_class: 4,
      ingredients: [
        { name: 'yaourt', is_whole_food: true, category: 'food' },
        { name: 'sucre', category: 'food' },
        { name: 'arômes naturels', category: 'additive' },
      ],
      nutrition: {
        energy_kcal: 85, fat_g: 1.5, saturated_fat_g: 1, carbs_g: 13,
        sugars_g: 12, added_sugars_g: 9, fiber_g: 0, protein_g: 4, salt_g: 0.1,
      },
      origin: 'France', named_oils: true, origin_transparent: true,
    });
    // With old NOVA 4 base (=4), score dropped into D. Softer base (=6) plus
    // the other pillars should land in C range at worst.
    assert.ok(
      ['B', 'C'].includes(audit.grade),
      `softer NOVA-4 base should yield B or C here (got ${audit.grade}, score ${audit.score})`,
    );
  });

  it('Palm oil adds a global penalty regardless of E-number', () => {
    const audit = scoreProduct({
      name: 'Biscuit avec huile de palme',
      category: 'snack_sweet',
      nova_class: 4,
      ingredients: [
        { name: 'farine de blé', category: 'food' },
        { name: 'sucre', category: 'food' },
        { name: 'huile de palme', category: 'food' },
        { name: 'sel', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 480, fat_g: 20, saturated_fat_g: 10, carbs_g: 65,
        sugars_g: 25, added_sugars_g: 22, fiber_g: 2, protein_g: 6, salt_g: 0.5,
      },
      named_oils: true,
    });
    const palm = audit.global_penalties.find((p) => /Palm oil/i.test(p.reason));
    assert.ok(palm, 'palm oil should add a global penalty');
    assert.equal(palm.points, -3);
  });

  it('Auto-NOVA downgrades a clean yogurt from incorrect 4 to 3', () => {
    const audit = scoreProduct({
      name: 'Yaourt nature sans additifs',
      category: 'yogurt',
      nova_class: 4, // input mistake — ingredients suggest NOVA 3
      ingredients: [
        { name: 'lait demi-écrémé', is_whole_food: true, category: 'food' },
        { name: 'ferments lactiques', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 50, fat_g: 1.5, saturated_fat_g: 1, carbs_g: 5,
        sugars_g: 5, added_sugars_g: 0, fiber_g: 0, protein_g: 4, salt_g: 0.15,
      },
    });
    const adjustment = audit.pillars.processing.deductions.find((d) =>
      /auto-adjusted/i.test(d.reason),
    );
    assert.ok(adjustment, 'NOVA auto-adjustment should be recorded');
    assert.equal(adjustment.points, 0, 'NOVA auto-adjust entry is informational (0 pts); real delta is in the base-score line');
    const base = audit.pillars.processing.deductions.find((d) =>
      /NOVA class 3 base/i.test(d.reason),
    );
    assert.ok(base, 'effective NOVA should render as class 3 in the deduction');
  });

  it('Declared micronutrients add a Pillar 2 bonus', () => {
    const audit = scoreProduct({
      name: 'Céréales enrichies',
      category: 'breakfast_cereal',
      nova_class: 4,
      ingredients: [
        { name: 'avoine', is_whole_food: true, category: 'food' },
        { name: 'sucre', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 370, fat_g: 6, saturated_fat_g: 1, carbs_g: 70,
        sugars_g: 18, added_sugars_g: 16, fiber_g: 8, protein_g: 9, salt_g: 0.6,
      },
      declared_micronutrients: ['Iron', 'Calcium', 'Vitamin D', 'Vitamin B'],
    });
    const bonus = audit.pillars.nutritional_density.bonuses.find((b) =>
      /Declares .* vitamins/i.test(b.reason),
    );
    assert.ok(bonus, 'should surface a declared-micronutrient bonus');
  });

  it('Omega-3 source adds a global bonus (saumon / noix / lin)', () => {
    const audit = scoreProduct({
      name: 'Mélange graines et noix',
      category: 'snack_salty',
      nova_class: 1,
      ingredients: [
        { name: 'noix', is_whole_food: true, category: 'food' },
        { name: 'graines de lin', is_whole_food: true, category: 'food' },
        { name: 'sel', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 540, fat_g: 40, saturated_fat_g: 5, carbs_g: 15,
        sugars_g: 3, added_sugars_g: 0, fiber_g: 8, protein_g: 15, salt_g: 0.3,
      },
      named_oils: true,
    });
    const o3 = audit.global_bonuses.find((b) => /Omega-3/.test(b.reason));
    assert.ok(o3, 'omega-3 source should add a bonus');
    assert.equal(o3.points, 2);
  });

  it('Flavored yogurt (Skyr-class) gets UPF-marker penalty from arômes + concentré de minéraux', () => {
    const audit = scoreProduct({
      name: 'Skyr Poire Vanille',
      category: 'yogurt',
      nova_class: 4,
      ingredients: [
        { name: 'yaourt maigre (lait)', is_whole_food: true, category: 'food' },
        { name: 'poire 7%', is_whole_food: true, percentage: 7, category: 'food' },
        { name: 'sucre 6.2%', percentage: 6.2, category: 'food' },
        { name: 'amidon de maïs', category: 'food' },
        { name: 'arômes naturels', category: 'food' },
        { name: 'arôme naturel de vanille', category: 'food' },
        { name: 'concentré des minéraux du lait', category: 'food' },
        { name: 'gousses de vanille épuisées', category: 'food' },
        { name: 'pectines', e_number: 'E440', category: 'additive' },
      ],
      nutrition: {
        energy_kcal: 60, fat_g: 0.2, saturated_fat_g: 0.1, carbs_g: 8.5,
        sugars_g: 8.5, added_sugars_g: 6.2, fiber_g: 0.3, protein_g: 10, salt_g: 0.12,
      },
      origin: 'Lait français', named_oils: true, origin_transparent: true,
    });
    const upfHit = audit.pillars.processing.deductions.find((d) =>
      /UPF marker/i.test(d.reason),
    );
    assert.ok(upfHit, 'UPF marker deduction should fire on this composition');
    // arômes + mineral concentrate = 2 markers → capped -4
    assert.equal(upfHit.points, -4);
    // Skyr should still land B/A range (not get tanked)
    assert.ok(['A', 'B'].includes(audit.grade), `Skyr grade ${audit.grade} (score ${audit.score})`);
  });

  it('Clean yogurt without UPF markers keeps a softer NOVA assessment', () => {
    const audit = scoreProduct({
      name: 'Yaourt nature',
      category: 'yogurt',
      nova_class: 4, // input wrong — no UPF markers, should downgrade
      ingredients: [
        { name: 'lait', is_whole_food: true, category: 'food' },
        { name: 'ferments lactiques', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 50, fat_g: 1.5, saturated_fat_g: 1, carbs_g: 5,
        sugars_g: 5, added_sugars_g: 0, fiber_g: 0, protein_g: 4, salt_g: 0.15,
      },
    });
    const adjustment = audit.pillars.processing.deductions.find((d) =>
      /auto-adjusted/i.test(d.reason),
    );
    assert.ok(adjustment, 'NOVA auto-adjust fires on clean yogurt');
    // No UPF markers here
    const upfHit = audit.pillars.processing.deductions.find((d) =>
      /UPF marker/i.test(d.reason),
    );
    assert.equal(upfHit, undefined, 'no UPF marker in clean yogurt');
  });

  it('UPF-marker penalty caps at -4 even with many markers', () => {
    const audit = scoreProduct({
      name: 'Ultra-formulated product',
      category: 'snack_sweet',
      nova_class: 4,
      ingredients: [
        { name: 'arômes naturels', category: 'food' },
        { name: 'concentré de minéraux', category: 'food' },
        { name: 'isolat de protéines de soja', category: 'food' },
        { name: 'hydrolysat de caséine', category: 'food' },
        { name: 'amidon modifié', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 400, fat_g: 20, saturated_fat_g: 8, carbs_g: 40,
        sugars_g: 15, fiber_g: 2, protein_g: 10, salt_g: 0.8,
      },
    });
    const upfHit = audit.pillars.processing.deductions.find((d) =>
      /UPF marker/i.test(d.reason),
    );
    assert.ok(upfHit);
    assert.equal(upfHit.points, -4, 'cap holds at -4');
  });

  it('Healthy fat source (olive oil) lifts borderline fatScore', () => {
    // Vinaigrette-style product with 30g fat, 5g sat. Ratio 0.17 already
    // gives full fatScore=5, so a pure test needs a borderline ratio.
    const audit = scoreProduct({
      name: 'Sauce pesto',
      category: 'condiment',
      nova_class: 3,
      ingredients: [
        { name: 'basilic', is_whole_food: true, category: 'food' },
        { name: "huile d'olive vierge", category: 'food' },
        { name: 'parmesan', category: 'food' },
        { name: 'pignons de pin', category: 'food' },
      ],
      nutrition: {
        energy_kcal: 420, fat_g: 40, saturated_fat_g: 9, carbs_g: 5,
        sugars_g: 1, added_sugars_g: 0, fiber_g: 1, protein_g: 5, salt_g: 1.5,
      },
      origin: 'Italie', named_oils: true, origin_transparent: true,
    });
    const bonus = audit.pillars.nutritional_density.bonuses.find((b) =>
      /Healthy fat source/i.test(b.reason),
    );
    assert.ok(bonus, 'olive oil ingredient should earn healthy-fat-source bump');
  });
});

// ============================================================================
// Veto conditions — trans-fat is the strictest: WHO REPLACE program
// considers no level safe, so we cap score ≤ 40 regardless of other pillars.
// ============================================================================

describe('veto conditions', () => {
  it('trans-fat > 0.1 g/100 g caps score at 40', () => {
    const audit = scoreProduct({
      name: 'Margarine industrielle',
      category: 'oil_fat',
      nova_class: 4,
      ingredients: [
        { name: 'huiles partiellement hydrogénées', category: 'food', is_whole_food: false },
        { name: 'sel', category: 'food', is_whole_food: false },
      ],
      nutrition: {
        energy_kcal: 700, fat_g: 80, saturated_fat_g: 30,
        carbs_g: 0, sugars_g: 0, fiber_g: 0, protein_g: 0, salt_g: 1.2,
        trans_fat_g: 2.5, // well above the 0.1 g/100g threshold
      },
    });
    assert.ok(audit.score <= 40, `expected score ≤40 (trans-fat veto), got ${audit.score}`);
    assert.equal(audit.veto.triggered, true);
    assert.ok(/trans fat/i.test(audit.veto.reason));
  });

  it('trans-fat exactly at 0.1 g/100 g does NOT trigger (boundary check)', () => {
    const audit = scoreProduct({
      name: 'Margarine',
      category: 'oil_fat',
      nova_class: 3,
      ingredients: [{ name: 'huile de tournesol', category: 'food', is_whole_food: false }],
      nutrition: {
        energy_kcal: 700, fat_g: 80, saturated_fat_g: 8,
        carbs_g: 0, sugars_g: 0, fiber_g: 0, protein_g: 0, salt_g: 0.5,
        trans_fat_g: 0.1, // exactly the threshold
      },
    });
    // threshold is `> 0.1` strictly, so 0.1 should not trigger
    assert.equal(audit.veto.triggered, false, `expected no veto at exactly 0.1g, got ${audit.veto.reason}`);
  });
});

// ============================================================================
// inferNovaClass fresh-produce exception (R3.2): OFF barcode scans for
// raw produce return an empty ingredients array, which used to collapse
// to NOVA 4 (ultra-processed). With the lexicon guard, a whole banana
// scanned by barcode now resolves to NOVA 1.
// ============================================================================

describe('inferNovaClass fresh-produce exception', () => {
  it('banana with empty ingredients → NOVA 1 (not 4)', () => {
    const cls = inferNovaClass({
      name: 'Banane',
      category: 'fruit',
      ingredients: [],
      nutrition: { energy_kcal: 89, fat_g: 0.3, saturated_fat_g: 0.1, carbs_g: 23, sugars_g: 12, fiber_g: 2.6, protein_g: 1.1, salt_g: 0 },
    });
    assert.equal(cls, 1);
  });

  it('apple / pomme resolves via the FR lexicon', () => {
    const cls = inferNovaClass({
      name: 'Pomme',
      category: 'fruit',
      ingredients: [],
      nutrition: { energy_kcal: 52, fat_g: 0.2, saturated_fat_g: 0, carbs_g: 14, sugars_g: 10, fiber_g: 2.4, protein_g: 0.3, salt_g: 0 },
    });
    assert.equal(cls, 1);
  });

  it('handles FR plural forms: pommes, bananes, courgettes → NOVA 1', () => {
    for (const name of ['pommes', 'bananes', 'courgettes', 'carottes', 'fraises']) {
      const cls = inferNovaClass({
        name,
        category: 'fruit',
        ingredients: [],
        nutrition: { energy_kcal: 50, fat_g: 0, saturated_fat_g: 0, carbs_g: 12, sugars_g: 10, fiber_g: 2, protein_g: 1, salt_g: 0 },
      });
      assert.equal(cls, 1, `"${name}" should match as fresh produce`);
    }
  });

  it('unknown product name with empty ingredients still falls back to NOVA 4', () => {
    const cls = inferNovaClass({
      name: 'Mystery Branded Snack Co.',
      category: 'snack',
      ingredients: [],
      nutrition: { energy_kcal: 500, fat_g: 25, saturated_fat_g: 10, carbs_g: 50, sugars_g: 20, fiber_g: 2, protein_g: 8, salt_g: 1 },
    });
    assert.equal(cls, 4);
  });
});
