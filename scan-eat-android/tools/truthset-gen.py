import json
raw=json.load(open('gold_raw.json'))
def ks(s):
    if s is None: return "null"
    return json.dumps(s,ensure_ascii=False).replace("$","\\$")
def kl(l):
    if not l: return "null"
    return "listOf("+", ".join(ks(x) for x in l)+")"

KEEP={"energy-kcal_100g","energy-kj_100g","energy_100g","fat_100g","saturated-fat_100g","carbohydrates_100g","sugars_100g","added-sugars_100g","fiber_100g","proteins_100g","salt_100g","sodium_100g","trans-fat_100g","alcohol_100g","caffeine_100g","calcium_100g","iron_100g","vitamin-c_100g","vitamin-d_100g","vitamin-a_100g","vitamin-e_100g","vitamin-b12_100g","magnesium_100g","potassium_100g","zinc_100g","omega-3-fat_100g"}
def kn(nm):
    items=[]
    for k,v in sorted((nm or {}).items()):
        if k not in KEEP: continue
        try: f=float(v)
        except: continue
        items.append(f'{ks(k)} to {f!r}')
    return "mapOf<String, Any?>("+", ".join(items)+")"
def accept(c,e,p):
    n=(p.get("product_name") or "").lower(); tags=" ".join(p.get("categories_tags") or [])
    # OFF search results that are plainly mis-tagged upstream: not a valid reference either way.
    if c=="fruit-juices" and "cacao" in n: return None
    if c=="wines" and ("confiture" in n or "vinaigre" in n): return None
    if c=="plant-milks" and "coco" in n: return None
    if c=="pizzas" and "pizza-sauces" in tags: return None
    if not n: return None
    if "sans alcool" in n or "0.0" in n or "non-alcoholic-beers" in tags or "non-alcoholic-wines" in tags or "en:sodas" in tags: return ["BEVERAGE_SOFT"]
    if "chocolate-spreads" in tags: return ["SNACK_SWEET","NUTS_SEEDS"]
    if "smoked-salmon" in n.lower() or "salmon" in n.lower(): return None if c=="wines" else [e]
    if "crispbreads" in tags or "rusks" in tags: return ["BREAD"]
    if "lentil-crisps" in tags: return ["SNACK_SALTY","LEGUME"]
    if "ratatouille" in tags or "spinach-with-cream" in tags or "pan-fried" in tags: return ["FRESH_PRODUCE","READY_MEAL"]
    if "kefir" in tags: return ["YOGURT","MILK"]
    if "mixed-nuts-and-dried-fruits" in tags or "shelled-nuts" in tags: return ["DRIED_FRUIT","NUTS_SEEDS"]
    if c=="canned-vegetables" and ("beans" in tags and "green-beans" not in tags): return ["LEGUME"]
    if "curry" in n: return ["LEGUME","READY_MEAL","FRESH_PRODUCE"]
    if "-dishes" in tags or "plant-based-meals" in tags or "microwave-meals" in tags: return [e,"READY_MEAL"]
    if "fromages-blancs" in tags: return ["YOGURT"]
    if "lentilles cuisin" in n or "prepared-lentils" in tags: return ["LEGUME","READY_MEAL"]
    if c=="pizzas": return ["READY_MEAL"]
    return [e]
out=[]
seen=set()
for c,d in raw.items():
    for p in d["products"]:
        code=p.get("code")
        if code in seen: continue
        seen.add(code)
        nova=p.get("nova_group")
        try: nova=int(nova)
        except: nova=None
        eco=p.get("ecoscore_score")
        try: eco=int(eco)
        except: eco=None
        acc=accept(c,d["expected"],p)
        if acc is None: continue
        out.append(f'''    TruthProduct({ks(code)}, {ks(c)}, setOf({", ".join("ProductCategory."+a for a in acc)}), OffProductResponse(
        productName = {ks(p.get("product_name"))}, productNameFr = {ks(p.get("product_name_fr"))}, genericNameFr = {ks(p.get("generic_name_fr"))},
        brands = {ks(p.get("brands"))}, categoriesTags = {kl(p.get("categories_tags"))},
        ingredientsTextFr = {ks(p.get("ingredients_text_fr"))}, ingredientsText = {ks(p.get("ingredients_text"))},
        novaGroup = {nova if nova is not None else "null"}, nutriments = {kn(p.get("nutriments"))},
        labelsTags = {kl(p.get("labels_tags"))}, origins = {ks(p.get("origins"))}, countriesTags = null, quantity = {ks(p.get("quantity"))},
        ecoscoreGrade = {ks(p.get("ecoscore_grade"))}, ecoscoreScore = {eco if eco is not None else "null"}, nutritionGrades = {ks(p.get("nutrition_grades"))},
        allergensTags = {kl(p.get("allergens_tags"))}, tracesTags = {kl(p.get("traces_tags"))}, additivesTags = {kl(p.get("additives_tags"))},
    )),''')
hdr='''package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.engine.nutrition.OffProductResponse
import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// SCORE TRUTH SET (score-engine audit S15) - GENERATED, do not hand-edit.
//
// Real Open Food Facts records (world.openfoodfacts.org API v2, fetched
// 2026-10-13): the 6 most-scanned products sold in France for each of 50 OFF
// categories, with the FULL categories_tags hierarchy exactly as the API
// returns it. [TruthProduct.acceptable] is the set of ProductCategory values a product of
// that OFF category must be scored against - the ground truth the old
// substring mapper got wrong for bread, rice, pasta, ham, coffee, lentils,
// margarine... (Roadmap.md, S1). This is a veracity reference, not a snapshot:
// a product whose expected category is genuinely debatable was left out
// rather than guessed. Regenerate with the script referenced in Roadmap.md.
// ============================================================================

data class TruthProduct(
    val code: String?,
    val offCategory: String,
    /** Every category this product may legitimately be scored against
     *  (a ratatouille is fairly both a vegetable dish and a ready meal). */
    val acceptable: Set<ProductCategory>,
    val off: OffProductResponse,
)

val SCORE_TRUTH_SET: List<TruthProduct> = listOf(
'''
chunks=[out[i:i+15] for i in range(0,len(out),15)]
hdr=hdr.replace("val SCORE_TRUTH_SET: List<TruthProduct> = listOf(\n","val SCORE_TRUTH_SET: List<TruthProduct> by lazy { listOf(" + ", ".join(f"truthChunk{i}()" for i in range(len(chunks))) + ").flatten() }\n\n")
body="".join(f"private fun truthChunk{i}(): List<TruthProduct> = listOf(\n"+"\n".join(ch)+"\n)\n\n" for i,ch in enumerate(chunks))
open('ScoreTruthSet.kt','w').write(hdr+body)
print(len(out))
