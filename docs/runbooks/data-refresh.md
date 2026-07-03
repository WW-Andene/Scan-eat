# Data refresh

Regenerating `public/data/food-db.js` from ANSES CIQUAL and
`public/data/pairings.js` from the Ahn 2011 supplementary. Run on a host
with unrestricted network access (ANSES + Zenodo + GitHub raw must be
reachable).

## Food DB (CIQUAL)

**Preconditions**: `ciqual.anses.fr` is reachable from your shell. Unzip
available (`apt install unzip` / `brew install unzip` / already present on
most dev machines).

```bash
# The generator fetches, unzips, parses, and writes.
node tools/generate-food-db.mjs
```

The script:
1. Downloads `Table Ciqual 2020_XML_FR_2020 07 07.zip` from ANSES to a
   temp dir.
2. Unzips into `alim_2020_07_07.xml` + `compo_2020_07_07.xml`.
3. Filters to the `KEEP` list of ~54 common food codes (hard-coded in the
   generator; edit to add foods).
4. Emits `public/data/food-db.js` with each entry carrying its
   `ciqual_food_code`. Header includes `FOOD_DB_SOURCE` metadata.

**Verification**:
```bash
git diff public/data/food-db.js | head -40
# Numbers should differ modestly from the hand-transcribed approximation
# (±10 % at most per-value). Schema must match: { name, ciqual_food_code,
# kcal, protein_g, carbs_g, fat_g, fiber_g, sat_fat_g, sugars_g, salt_g,
# iron_mg, calcium_mg, vit_d_ug, b12_ug, aliases? }

npm test
# All 449+ tests must still pass. The tests reference specific named
# foods (pomme, tomate, saumon, …) so they catch if a rename snuck in.

npm run build:web
# Service worker cache key updates. Commit + push.
```

**If ANSES is unreachable**: don't fall back to a mirror silently. The
hand-transcribed file has the honest provenance notice; leaving it in
place until the real data can be fetched is better than a silent swap.

**After a successful run**: commit the generated file + the script with
a message like:
```
Regenerate food-db.js from ANSES CIQUAL 2020 (official XML)

tools/generate-food-db.mjs now runnable in <environment-name>.
```

## Pairings (Ahn 2011)

**Preconditions**: `/tmp/flavor-network/s3.csv` present. If not, fetch it
once from the GitHub mirror:

```bash
mkdir -p /tmp/flavor-network
curl -sL -o /tmp/flavor-network/s3.csv \
  "https://raw.githubusercontent.com/lingcheng99/Flavor-Network/master/data/srep00196-s3.csv"
# File should be ~5.6 MB, 56,502 lines.
```

Then:
```bash
node tools/generate-pairings.mjs
```

The script:
1. Computes SHA-256 of the input file.
2. Refuses to run if the SHA doesn't match `EXPECTED_SHA256` in the
   generator. If it mismatches legitimately (upstream republished the
   file), update `EXPECTED_SHA256` **only after** verifying the new file
   matches the peer-reviewed release at
   `https://www.nature.com/articles/srep00196`.
3. Parses the 56k recipes, builds co-occurrence counts, scores pairs via
   PPMI × √count, filters to ingredients in the FR↔EN map, emits
   `public/data/pairings.js`.

**Verification**:
```bash
git diff public/data/pairings.js | head -20
# Expected diff: generated_at timestamp changes. No structural changes
# unless you edited FR/SYNONYMS in the generator.

npm test
# pairings-tests.ts has structural tests that pin every key and every
# synonym target. They'll catch drift.
```

## CIQUAL code index (OFF-derived)

`tools/_ciqual-codes.json` is committed. It's built from OpenFoodFacts'
public taxonomy. To refresh:

```bash
curl -sL -o /tmp/off-ingredients.txt \
  "https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/food/ingredients.txt"
node tools/extract-ciqual-codes.mjs
```

Writes `tools/_ciqual-codes.json` with ~1,950 FR aliases → 724 unique
CIQUAL codes. The annotator (`tools/annotate-food-db.mjs`) uses this to
dry-run CIQUAL-code attribution for the current `food-db.js`.

## Cadence

No scheduled refresh. Refresh when:
- A user reports a food where our CIQUAL numbers are visibly off.
- ANSES publishes a CIQUAL 2025 (or later). Update
  `tools/generate-food-db.mjs` to point at the new zip URL + the new
  inclusion list in ONE commit; review the diff.
- Someone reports a pairings mismatch that suggests the input corpus has
  changed.
