import json,urllib.request,time,sys
EXP={"breads":"BREAD","rices":"GRAIN","pastas":"GRAIN","quinoa":"GRAIN","flours":"GRAIN","crisps":"SNACK_SALTY",
"lentils":"LEGUME","chickpeas":"LEGUME","hams":"PROCESSED_MEAT","sausages":"PROCESSED_MEAT","breaded-fishes":"FISH",
"lasagna":"READY_MEAL","pizzas":"READY_MEAL","coffees":"COFFEE_TEA","teas":"COFFEE_TEA","margarines":"OIL_FAT",
"olive-oils":"OIL_FAT","butters":"OIL_FAT","canned-vegetables":"FRESH_PRODUCE","frozen-vegetables":"FRESH_PRODUCE",
"dried-fruits":"DRIED_FRUIT","plant-milks":"MILK","milks":"MILK","yogurts":"YOGURT","cheeses":"CHEESE",
"honeys":"SPREAD_SWEET","jams":"SPREAD_SWEET","dark-chocolates":"SNACK_SWEET","biscuits":"SNACK_SWEET",
"fresh-meats":"FRESH_MEAT","canned-tunas":"FISH","smoked-salmons":"FISH","tofu":"PLANT_BASED_ALTERNATIVE",
"beers":"ALCOHOLIC_BEVERAGE","wines":"ALCOHOLIC_BEVERAGE","mineral-waters":"BEVERAGE_WATER","sodas":"BEVERAGE_SOFT",
"fruit-juices":"BEVERAGE_JUICE","eggs":"EGG","ice-creams":"ICE_CREAM","breakfast-cereals":"BREAKFAST_CEREAL",
"oat-flakes":"BREAKFAST_CEREAL","compotes":"FRESH_PRODUCE","bananas":"FRESH_PRODUCE","soups":"SOUP",
"mustards":"CONDIMENT","ketchup":"CONDIMENT","nuts":"NUTS_SEEDS","peanut-butters":"NUTS_SEEDS","energy-drinks":"BEVERAGE_SOFT"}
F="product_name,product_name_fr,generic_name_fr,brands,categories_tags,ingredients_text_fr,ingredients_text,nova_group,nutriments,labels_tags,origins,countries_tags,quantity,ecoscore_grade,ecoscore_score,nutrition_grades,allergens_tags,traces_tags,additives_tags,code"
try: out=json.load(open('gold_raw.json'))
except: out={}
for c,e in EXP.items():
    if c in out and out[c]: continue
    d=None
    for a in range(4):
        try:
            u=f"https://world.openfoodfacts.org/api/v2/search?categories_tags_en={c}&countries_tags_en=france&fields={F}&page_size=6&sort_by=unique_scans_n"
            d=json.load(urllib.request.urlopen(u,timeout=40));break
        except Exception as ex: time.sleep(6)
    if d: out[c]={"expected":e,"products":d["products"]}; print(c,len(d["products"]),flush=True)
    else: print(c,"ERR",flush=True)
    json.dump(out,open('gold_raw.json','w'),ensure_ascii=False)
    time.sleep(2)
