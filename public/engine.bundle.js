// src/scoring-engine.ts
var ADDITIVES_DB = [
  // ===== TIER 1: Serious concern (strong authoritative basis) =====
  {
    e_number: "E249",
    names: ["nitrite de potassium", "potassium nitrite"],
    tier: 1,
    category: "preservative",
    concern: "Curing agent. Processed meat containing nitrites is classified IARC Group 1 (carcinogenic to humans) via N-nitroso compound formation during digestion. Editorial: tier reflects the food-carcinogenicity classification, not the isolated additive.",
    source: "IARC Monograph Vol 114 (2015) \u2014 processed meat Group 1; IARC Monograph Vol 94 (2010) \u2014 ingested nitrate/nitrite under conditions that result in endogenous nitrosation Group 2A; EFSA Re-evaluation 2017."
  },
  {
    e_number: "E250",
    names: ["nitrite de sodium", "sodium nitrite"],
    tier: 1,
    category: "preservative",
    concern: "Curing agent. Processed meat containing nitrites is classified IARC Group 1 (carcinogenic to humans) via N-nitroso compound formation. Editorial: tier reflects the finished-food classification.",
    source: "IARC Monograph Vol 114 (2015); IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017."
  },
  {
    e_number: "E251",
    names: ["nitrate de sodium", "sodium nitrate"],
    tier: 1,
    category: "preservative",
    concern: "Converts to nitrite in the gut. Same N-nitroso pathway linked to processed-meat carcinogenicity.",
    source: "IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017."
  },
  {
    e_number: "E252",
    names: ["nitrate de potassium", "potassium nitrate"],
    tier: 1,
    category: "preservative",
    concern: "Converts to nitrite in the gut. Same N-nitroso pathway linked to processed-meat carcinogenicity.",
    source: "IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017."
  },
  {
    e_number: "E433",
    names: ["polysorbate 80", "polysorbate-80"],
    tier: 1,
    category: "emulsifier",
    concern: "Detergent-class emulsifier. Mouse studies show microbiome shifts, mucus-layer erosion, and low-grade inflammation at dietary doses. Small human data consistent with the signal; large-scale human evidence still limited.",
    source: "Chassaing et al., Nature 2015 (mice); Chassaing et al., Gastroenterology 2022 (FRESH crossover human study, n=16, CMC)."
  },
  {
    e_number: "E466",
    names: ["carboxymethylcellulose", "cmc", "cellulose gum"],
    tier: 1,
    category: "emulsifier",
    concern: "Detergent-class emulsifier. Mouse microbiome disruption replicated in a controlled human feeding trial (reduced microbial diversity, altered metabolome).",
    source: "Chassaing et al., Nature 2015; Chassaing et al., Gastroenterology 2022 (FRESH trial, CMC-specific)."
  },
  // ===== TIER 2: Moderate concern =====
  {
    e_number: "E338",
    names: ["acide phosphorique", "phosphoric acid"],
    tier: 2,
    category: "acidulant",
    concern: "Phosphate additive. Epidemiologic associations between phosphorus-rich diets and cardiovascular / renal outcomes at high chronic intakes.",
    source: "EFSA Scientific Opinion on phosphates as food additives, EFSA Journal 2019;17(6):5674 (group ADI 40 mg/kg bw/day as phosphorus)."
  },
  {
    e_number: "E450",
    names: ["diphosphate", "pyrophosphate"],
    tier: 2,
    category: "stabilizer",
    concern: "Phosphate additive (same regulatory group as E338).",
    source: "EFSA Scientific Opinion 2019;17(6):5674."
  },
  {
    e_number: "E451",
    names: ["triphosphate", "tripolyphosphate"],
    tier: 2,
    category: "stabilizer",
    concern: "Phosphate additive (same regulatory group as E338).",
    source: "EFSA Scientific Opinion 2019;17(6):5674."
  },
  {
    e_number: "E452",
    names: ["polyphosphate"],
    tier: 2,
    category: "stabilizer",
    concern: "Phosphate additive (same regulatory group as E338).",
    source: "EFSA Scientific Opinion 2019;17(6):5674."
  },
  {
    e_number: "E102",
    names: ["tartrazine", "jaune de tartrazine"],
    tier: 2,
    category: "colorant",
    concern: "Azo dye. Associations with hyperactivity and attention effects in a subset of children. EU foods containing it must carry a warning label.",
    source: 'McCann et al., The Lancet 370:1560\u20131567 (2007, "Southampton study"); EU Regulation 1333/2008 Annex V \u2014 mandatory "may have an adverse effect on activity and attention in children" label.'
  },
  {
    e_number: "E110",
    names: ["jaune orang\xE9 s", "sunset yellow"],
    tier: 2,
    category: "colorant",
    concern: "Azo dye. Same Southampton-study association; EU warning label required.",
    source: "McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V."
  },
  {
    e_number: "E122",
    names: ["azorubine", "carmoisine"],
    tier: 2,
    category: "colorant",
    concern: "Azo dye. Same Southampton-study association; EU warning label required.",
    source: "McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V."
  },
  {
    e_number: "E124",
    names: ["ponceau 4r", "rouge cochenille a"],
    tier: 2,
    category: "colorant",
    concern: "Azo dye. Same Southampton-study association; EU warning label required.",
    source: "McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V."
  },
  {
    e_number: "E129",
    names: ["rouge allura", "allura red"],
    tier: 2,
    category: "colorant",
    concern: "Azo dye. Same Southampton-study association; EU warning label required.",
    source: "McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V."
  },
  {
    e_number: "E320",
    names: ["bha", "butylhydroxyanisol"],
    tier: 2,
    category: "antioxidant",
    concern: "Synthetic antioxidant. Classified IARC Group 2B (possibly carcinogenic) based on rodent forestomach tumours; human relevance debated.",
    source: "IARC Monograph Vol 40 (1986) \u2014 Group 2B; EFSA ADI 1 mg/kg bw/day reaffirmed 2011."
  },
  {
    e_number: "E321",
    names: ["bht", "butylhydroxytolu\xE8ne"],
    tier: 2,
    category: "antioxidant",
    concern: "Synthetic antioxidant used alongside BHA. EFSA set a low ADI on developmental / reproductive grounds.",
    source: "EFSA Scientific Opinion 2012;10(3):2588 (ADI 0.25 mg/kg bw/day)."
  },
  {
    e_number: "E951",
    names: ["aspartame"],
    tier: 2,
    category: "sweetener",
    concern: 'Non-nutritive sweetener. IARC classified as "possibly carcinogenic to humans" (Group 2B) in July 2023 based on limited human evidence for hepatocellular carcinoma. Contraindicated in phenylketonuria.',
    source: "IARC Monograph Vol 134 (2023) \u2014 Group 2B; JECFA 2023 reaffirmed ADI 40 mg/kg bw/day; EFSA ADI 40 mg/kg bw/day (2013)."
  },
  {
    e_number: "E150c",
    names: ["e150c", "caramel ammoniacal", "ammonia caramel"],
    tier: 2,
    category: "colorant",
    concern: "Ammonia caramel. Production yields 4-methylimidazole (4-MEI), classified IARC Group 2B (possibly carcinogenic to humans). Found in colas + dark sauces.",
    source: "IARC Monograph Vol 101 (2013) \u2014 4-MEI Group 2B; EFSA Scientific Opinion 2011;9(3):2004 (ADI 100 mg/kg bw/day for E150c, expressed as caramel, with a sub-ADI of 200 \xB5g/kg bw/day for 4-MEI)."
  },
  {
    e_number: "E150d",
    names: ["e150d", "caramel sulfite-ammoniacal", "sulfite-ammonia caramel"],
    tier: 2,
    category: "colorant",
    concern: "Sulfite-ammonia caramel. Same 4-MEI carcinogenicity concern as E150c; the most common caramel sub-class in dark sodas.",
    source: "IARC Monograph Vol 101 (2013) \u2014 4-MEI Group 2B; EFSA Scientific Opinion 2011;9(3):2004 (ADI 200 mg/kg bw/day for E150d)."
  },
  {
    // Conservative fallback when the label doesn't disambiguate the
    // sub-class. Tagged Tier 2 because EU labelling allows just "E150"
    // and most commercial caramel is c/d in practice.
    e_number: "E150",
    names: ["caramel", "colorant caramel"],
    tier: 2,
    category: "colorant",
    concern: 'Caramel colour, sub-class unspecified. EU labelling allows just "E150"; most commercial caramel in dark sodas + sauces is E150c or E150d, both flagged for 4-MEI (IARC Group 2B). Conservative tier when sub-class is ambiguous.',
    source: "IARC Monograph Vol 101 (2013); EFSA Scientific Opinion 2011;9(3):2004."
  },
  // ===== TIER 3: Minor / contextual (authorised, no direct authoritative concern) =====
  {
    e_number: "E407",
    names: ["carragh\xE9nane", "carrageenan"],
    tier: 3,
    category: "thickener",
    concern: "Thickener. EFSA reaffirmed safety at current use levels; animal-study inflammation signals have not been replicated at dietary doses in humans.",
    source: "EFSA Scientific Opinion 2018;16(4):5238 (ADI 75 mg/kg bw/day)."
  },
  {
    e_number: "E471",
    names: ["mono- et diglyc\xE9rides d'acides gras"],
    tier: 3,
    category: "emulsifier",
    concern: "Ubiquitous emulsifier. No specific authoritative concern; tier reflects the class-level emulsifier / UPF marker literature, not a ruling on E471 itself.",
    source: "EFSA Scientific Opinion 2017;15(11):5045 (no numerical ADI needed)."
  },
  {
    e_number: "E621",
    names: ["glutamate monosodique", "monosodium glutamate", "msg"],
    tier: 3,
    category: "flavor_enhancer",
    concern: "Flavour enhancer. EFSA derived a group ADI in 2017 out of caution; JECFA maintains that MSG is safe.",
    source: "EFSA Scientific Opinion 2017;15(7):4910 (group ADI 30 mg/kg bw/day); JECFA (1988) \u2014 no ADI specified."
  },
  {
    e_number: "E316",
    names: ["\xE9rythorbate de sodium", "sodium erythorbate"],
    tier: 3,
    category: "antioxidant",
    concern: "Vitamin C isomer used in cured meats. Authorised without numerical ADI; its presence often signals a curing system with nitrites.",
    source: "EFSA Scientific Opinion 2015 (ascorbic acid family)."
  },
  {
    e_number: "E100",
    names: ["curcumine", "curcuma (colorant)"],
    tier: 3,
    category: "colorant",
    concern: "Natural colorant. Low health concern at food-use levels; presence indicates cosmetic processing.",
    source: "EFSA Scientific Opinion 2010;8(9):1679 (ADI 3 mg/kg bw/day)."
  },
  {
    e_number: "E160c",
    names: ["paprika (colorant)", "extrait de paprika", "ol\xE9or\xE9sine de paprika"],
    tier: 3,
    category: "colorant",
    concern: "Natural colorant (capsanthin/capsorubin). Low health concern; presence indicates cosmetic processing.",
    source: "EFSA Scientific Opinion 2015;13(12):4320 (ADI 2 mg/kg bw/day capsanthin)."
  },
  // ===== TIER 1 additions =====
  {
    e_number: "E171",
    names: ["dioxyde de titane", "titanium dioxide"],
    tier: 1,
    category: "colorant",
    concern: "Banned as a food additive in the EU since August 2022 after EFSA could not establish a safe level on genotoxicity grounds for the nanoparticulate fraction.",
    source: "EFSA Scientific Opinion 2021;19(5):6585; Commission Regulation (EU) 2022/63 (ban effective August 2022)."
  },
  {
    e_number: "E220",
    names: ["anhydride sulfureux", "dioxyde de soufre", "sulfur dioxide"],
    tier: 1,
    category: "preservative",
    concern: "Sulfite \u2014 mandatory EU allergen. Established triggers for asthma and sulfite sensitivity at low doses.",
    source: "EU Regulation 1169/2011 Annex II (mandatory allergen declaration \u226510 mg/kg); EFSA Re-evaluation 2016;14(4):4438."
  },
  {
    e_number: "E221",
    names: ["sulfite de sodium", "sodium sulfite"],
    tier: 1,
    category: "preservative",
    concern: "Sulfite \u2014 same regulatory allergen classification as E220.",
    source: "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."
  },
  {
    e_number: "E223",
    names: ["m\xE9tabisulfite de sodium", "sodium metabisulfite"],
    tier: 1,
    category: "preservative",
    concern: "Sulfite \u2014 same regulatory allergen classification as E220.",
    source: "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."
  },
  {
    e_number: "E224",
    names: ["m\xE9tabisulfite de potassium", "potassium metabisulfite"],
    tier: 1,
    category: "preservative",
    concern: "Sulfite \u2014 same regulatory allergen classification as E220.",
    source: "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."
  },
  {
    e_number: "E385",
    names: ["edta", "calcium disodium edta"],
    tier: 1,
    category: "sequestrant",
    concern: "Metal chelator. EFSA set a conservative ADI; high chronic intake can affect mineral bioavailability.",
    source: "EFSA Scientific Opinion 2018;16(11):5007 (ADI 1.9 mg/kg bw/day)."
  },
  // ===== TIER 2 additions =====
  {
    e_number: "E211",
    names: ["benzoate de sodium", "sodium benzoate"],
    tier: 2,
    category: "preservative",
    concern: "Preservative. Can react with ascorbic acid (vitamin C) to form trace benzene, a known human carcinogen; industry has reformulated many drinks but the risk persists in acidic soft drinks.",
    source: "Gardner & Lawrence, J Food Prot 2007 (benzene formation mechanism); EFSA Re-evaluation 2016;14(3):4433 (ADI 5 mg/kg bw/day)."
  },
  {
    e_number: "E212",
    names: ["benzoate de potassium", "potassium benzoate"],
    tier: 2,
    category: "preservative",
    concern: "Same benzene-formation pathway as E211 when combined with vitamin C.",
    source: "EFSA Re-evaluation 2016;14(3):4433."
  },
  {
    e_number: "E950",
    names: ["ac\xE9sulfame-k", "acesulfame de potassium", "acesulfame potassium"],
    tier: 2,
    category: "sweetener",
    concern: "Non-nutritive sweetener. Authorised within EFSA ADI; evidence on long-term metabolic outcomes is mixed.",
    source: "EFSA Re-evaluation 2000 (ADI 9 mg/kg bw/day, reaffirmed in 2011 addendum)."
  },
  {
    e_number: "E955",
    names: ["sucralose"],
    tier: 2,
    category: "sweetener",
    concern: "Non-nutritive sweetener. Decomposes at high temperatures into chlorinated compounds; some studies report microbiome and glucose-response changes that are individually variable.",
    source: "EFSA Scientific Opinion 2000 (ADI 15 mg/kg bw/day); Schiffman et al., J Toxicol Environ Health B 2013 (heat decomposition); Suez et al., Cell 2022 (microbiome individuality)."
  },
  {
    e_number: "E954",
    names: ["saccharine", "saccharin"],
    tier: 2,
    category: "sweetener",
    concern: "Non-nutritive sweetener. Older rat bladder-tumour data; IARC downgraded to Group 3 (1999) after human evidence found no clear risk. Tier reflects the lineage of regulatory caution.",
    source: "IARC Monograph Vol 73 (1999) \u2014 Group 3; EFSA Re-evaluation 2018 (ADI 5 mg/kg bw/day)."
  },
  {
    e_number: "E952",
    names: ["cyclamate", "cyclamate de sodium"],
    tier: 2,
    category: "sweetener",
    concern: "Non-nutritive sweetener. Banned in the US since 1969 on legacy bladder-tumour data; authorised in the EU within an ADI.",
    source: "EFSA Re-evaluation 2000 (ADI 7 mg/kg bw/day); FDA ban 21 CFR 189.135."
  },
  {
    e_number: "E104",
    names: ["jaune de quinol\xE9ine", "quinoline yellow"],
    tier: 2,
    category: "colorant",
    concern: "Quinophthalone dye. Behaviour effects in the Southampton study; EU warning label required.",
    source: "McCann et al., The Lancet 2007; EU Regulation 1333/2008 Annex V."
  },
  {
    e_number: "E127",
    names: ["\xE9rythrosine", "erythrosine"],
    tier: 2,
    category: "colorant",
    concern: "Iodine-containing xanthene dye. Low EFSA ADI; thyroid-function concerns for high chronic intake.",
    source: "EFSA Re-evaluation 2011;9(1):1854 (ADI 0.1 mg/kg bw/day)."
  },
  {
    e_number: "E173",
    names: ["aluminium"],
    tier: 2,
    category: "colorant",
    concern: "Aluminium metallic dye. EFSA set a tolerable weekly intake based on developmental and neurotoxicity endpoints.",
    source: "EFSA Scientific Opinion 2008;754 (TWI 1 mg/kg bw/week)."
  },
  {
    e_number: "E339",
    names: ["phosphate de sodium", "sodium phosphate"],
    tier: 2,
    category: "stabilizer",
    concern: "Phosphate additive (same regulatory group as E338).",
    source: "EFSA Scientific Opinion 2019;17(6):5674."
  },
  {
    e_number: "E340",
    names: ["phosphate de potassium", "potassium phosphate"],
    tier: 2,
    category: "stabilizer",
    concern: "Phosphate additive (same regulatory group as E338).",
    source: "EFSA Scientific Opinion 2019;17(6):5674."
  },
  {
    e_number: "E341",
    names: ["phosphate de calcium", "calcium phosphate"],
    tier: 2,
    category: "stabilizer",
    concern: "Phosphate additive (same regulatory group as E338).",
    source: "EFSA Scientific Opinion 2019;17(6):5674."
  },
  {
    e_number: "E1520",
    names: ["propyl\xE8ne glycol", "propylene glycol"],
    tier: 2,
    category: "solvent",
    concern: "Humectant / solvent. EFSA set a low ADI; use in foods is narrowly restricted.",
    source: "EFSA Scientific Opinion 2018;16(4):5235 (ADI 25 mg/kg bw/day)."
  },
  // ===== TIER 3 additions =====
  {
    e_number: "E330",
    names: ["acide citrique", "citric acid"],
    tier: 3,
    category: "acidulant",
    concern: "Ubiquitous acidulant, also endogenous in human metabolism. Commercially produced by Aspergillus niger fermentation. No authoritative concern at food-use levels.",
    source: "EU authorisation without ADI (acceptable intake not specified); natural citrate cycle metabolite."
  },
  {
    e_number: "E300",
    names: ["acide ascorbique", "ascorbic acid", "vitamine c"],
    tier: 3,
    category: "antioxidant",
    concern: "Vitamin C. Used as an antioxidant; no safety concern.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E322",
    names: ["l\xE9cithine", "lecithines", "lecithin", "l\xE9cithine de soja", "l\xE9cithine de tournesol"],
    tier: 3,
    category: "emulsifier",
    concern: "Phospholipid emulsifier from soy or sunflower. No numerical ADI needed. Tier reflects its role as a marker of formulation, not direct concern.",
    source: "EFSA Scientific Opinion 2017;15(4):4742."
  },
  {
    e_number: "E415",
    names: ["gomme xanthane", "xanthan gum"],
    tier: 3,
    category: "thickener",
    concern: "Microbial polysaccharide. EFSA confirmed no safety concern at use levels; some individuals report IBS/IBD flare.",
    source: "EFSA Scientific Opinion 2017;15(2):4712 (no ADI needed)."
  },
  {
    e_number: "E412",
    names: ["gomme guar", "guar gum"],
    tier: 3,
    category: "thickener",
    concern: "Legume-derived soluble fibre. No safety concern at use levels.",
    source: "EFSA Scientific Opinion 2017;15(2):4669 (no ADI needed)."
  },
  {
    e_number: "E440",
    names: ["pectine", "pectines", "pectin"],
    tier: 3,
    category: "thickener",
    concern: "Natural plant fibre. No safety concern; acceptable daily intake not specified.",
    source: "EFSA Scientific Opinion 2017;15(7):4874 (no ADI needed)."
  },
  {
    e_number: "E202",
    names: ["sorbate de potassium", "potassium sorbate"],
    tier: 3,
    category: "preservative",
    concern: "Mould/yeast preservative. Low concern at authorised levels.",
    source: "EFSA Re-evaluation 2019;17(3):5626 (group ADI 11 mg/kg bw/day for sorbic acid and its salts)."
  },
  {
    e_number: "E270",
    names: ["acide lactique", "lactic acid"],
    tier: 3,
    category: "acidulant",
    concern: "Naturally occurring metabolite; no safety concern.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E296",
    names: ["acide malique", "malic acid"],
    tier: 3,
    category: "acidulant",
    concern: "Naturally occurring fruit acid; no safety concern.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E500",
    names: ["bicarbonate de sodium", "sodium bicarbonate", "carbonate de sodium"],
    tier: 3,
    category: "acidity_regulator",
    concern: "Leavening agent / pH buffer. No safety concern at food-use levels; contributes to sodium intake.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E551",
    names: ["dioxyde de silicium", "silicon dioxide", "silice"],
    tier: 3,
    category: "anticaking",
    concern: "Anti-caking agent. EFSA 2018 did not conclude a safety concern at use levels but requested further data on nanoparticulate forms.",
    source: "EFSA Scientific Opinion 2018;16(1):5088."
  },
  {
    e_number: "E422",
    names: ["glyc\xE9rol", "glycerol", "glyc\xE9rine"],
    tier: 3,
    category: "humectant",
    concern: "Humectant and sweetener. Metabolised like a carbohydrate; no ADI specified.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E960",
    names: ["glycosides de st\xE9viol", "steviol glycosides", "st\xE9via", "stevia"],
    tier: 3,
    category: "sweetener",
    concern: "Plant-derived non-nutritive sweetener. Lower regulatory concern than artificial sweeteners.",
    source: "EFSA Scientific Opinion 2010;8(4):1537 (ADI 4 mg/kg bw/day, expressed as steviol)."
  },
  {
    e_number: "E472",
    names: ["esters de mono- et diglyc\xE9rides", "esters of monoglycerides"],
    tier: 3,
    category: "emulsifier",
    concern: "Family of emulsifiers used in baking and dairy. No numerical ADI; tier reflects UPF class-marker role, not direct concern.",
    source: "EFSA Scientific Opinion 2017;15(11):5045 (group evaluation with E471)."
  },
  {
    e_number: "E1422",
    names: ["amidon ac\xE9tyl\xE9", "acetylated distarch adipate", "amidon modifi\xE9"],
    tier: 3,
    category: "thickener",
    concern: "Chemically modified starch. Authorised without numerical ADI.",
    source: "EU Regulation 1333/2008 \u2014 modified starches group without numerical ADI."
  },
  {
    e_number: "E1442",
    names: ["phosphate de distarch hydroxypropyle", "hydroxypropyl distarch phosphate"],
    tier: 3,
    category: "thickener",
    concern: "Chemically modified starch. Authorised without numerical ADI.",
    source: "EU Regulation 1333/2008 \u2014 modified starches group."
  },
  {
    e_number: "E210",
    names: ["acide benzo\xEFque", "benzoic acid"],
    tier: 2,
    category: "preservative",
    concern: "Parent of E211/E212. Same benzene-formation pathway when combined with vitamin C in acidic foods.",
    source: "Gardner & Lawrence, J Food Prot 2007; EFSA Re-evaluation 2016;14(3):4433 (group ADI 5 mg/kg bw/day)."
  },
  {
    e_number: "E475",
    names: ["esters polyglyc\xE9riques d'acides gras", "polyglycerol esters of fatty acids"],
    tier: 2,
    category: "emulsifier",
    concern: "Synthetic emulsifier. Authorised within an EFSA ADI. Class-level microbiome concerns (E433, E466) are not directly established for E475; tier reflects extrapolated caution.",
    source: "EFSA Scientific Opinion 2017;15(12):5089 (ADI 25 mg/kg bw/day)."
  },
  {
    e_number: "E968",
    names: ["\xE9rythritol", "erythritol"],
    tier: 2,
    category: "sweetener",
    concern: "Sugar alcohol. Large prospective cohort + mechanistic study linked higher plasma erythritol to major adverse cardiovascular events; causality still debated.",
    source: "Witkowski et al., Nature Medicine 29, 710\u2013718 (2023); EFSA Scientific Opinion 2023;21(12):8430 (revised exposure assessment)."
  },
  {
    e_number: "E905",
    names: ["cire microcristalline", "microcrystalline wax", "petroleum wax"],
    tier: 2,
    category: "glazing",
    concern: "Petroleum-derived wax. Concerns about migration of mineral-oil-saturated / -aromatic hydrocarbons (MOSH/MOAH) into food.",
    source: "EFSA Scientific Opinion 2012;10(6):2704 (MOSH/MOAH); EFSA update 2023;21(9):8215."
  },
  {
    e_number: "E307",
    names: ["alpha-tocoph\xE9rol", "alpha tocopherol", "tocopherols", "vitamine e (additif)"],
    tier: 3,
    category: "antioxidant",
    concern: "Vitamin E used as an antioxidant. No safety concern.",
    source: "EFSA Scientific Opinion 2015;13(9):4247 (group evaluation)."
  },
  {
    e_number: "E331",
    names: ["citrate de sodium", "sodium citrate", "citrates de sodium"],
    tier: 3,
    category: "acidity_regulator",
    concern: "Citrate buffer. No safety concern.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E333",
    names: ["citrate de calcium", "calcium citrate"],
    tier: 3,
    category: "acidity_regulator",
    concern: "Citrate buffer. No safety concern.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E965",
    names: ["maltitol", "sirop de maltitol"],
    tier: 3,
    category: "sweetener",
    concern: "Sugar alcohol. Moderate glycemic impact; laxative effect above individual tolerance (usually ~20 g).",
    source: "EU authorisation without ADI; EFSA 2011 opinion on polyol laxative threshold."
  },
  {
    e_number: "E967",
    names: ["xylitol"],
    tier: 3,
    category: "sweetener",
    concern: "Sugar alcohol. Generally safe; laxative effect above ~50 g. Acutely toxic to dogs (irrelevant to human safety).",
    source: "EU authorisation without ADI; EFSA 2011 opinion on polyol laxative threshold."
  },
  // ===== TIER 3: 2026-05-01 batch — common label hits we previously didn't index =====
  // Splitting E150 into a/b/c/d (above) leaves the safer plain + caustic-
  // sulfite sub-classes here as Tier 3 cosmetic-processing signals. The
  // remaining entries fill gaps the audit kept hitting on real OFF data:
  // colorants, acidulants, lactates, flavour enhancers (E627/631/635
  // family), and a few common bulkers.
  {
    e_number: "E150a",
    names: ["e150a", "caramel ordinaire", "plain caramel"],
    tier: 3,
    category: "colorant",
    concern: "Plain (no-additive) caramel colour. No 4-MEI concern, but presence still signals cosmetic processing.",
    source: "EFSA Scientific Opinion 2011;9(3):2004 (no ADI specified for E150a; EU SCF group ADI applies)."
  },
  {
    e_number: "E150b",
    names: ["e150b", "caramel caustique", "caustic-sulfite caramel"],
    tier: 3,
    category: "colorant",
    concern: "Caustic-sulfite caramel colour. No 4-MEI concern; sulfite content can be relevant for sulfite-sensitive individuals.",
    source: "EFSA Scientific Opinion 2011;9(3):2004 (ADI 160 mg/kg bw/day, expressed as caramel solids)."
  },
  {
    e_number: "E101",
    names: ["riboflavine", "riboflavin", "vitamine b2"],
    tier: 3,
    category: "colorant",
    concern: "Riboflavin (vitamin B2) used as a yellow colorant. Nutritionally identical to dietary B2; tier reflects cosmetic-processing signal, not safety.",
    source: 'EFSA Scientific Opinion 2013;11(10):3357 (no numerical ADI needed; "not of safety concern at expected use").'
  },
  {
    e_number: "E160a",
    names: ["carot\xE9no\xEFdes", "beta-carot\xE8ne", "beta carotene", "carotenes"],
    tier: 3,
    category: "colorant",
    concern: "Carotenoid colorants (vitamin A precursor). EFSA flags caution at very high supplemental intakes for smokers, but food-additive use is well below those levels.",
    source: "EFSA Scientific Opinion 2012;10(3):2593 (\u03B2-carotene); EU labelling permitted as colour without an ADI for natural extracts."
  },
  {
    e_number: "E163",
    names: ["anthocyanes", "anthocyanins", "extrait de peau de raisin"],
    tier: 3,
    category: "colorant",
    concern: "Anthocyanin colorants (red/purple, from grape skins, blackcurrant, etc.). Generally regarded as safe; presence signals cosmetic processing.",
    source: "EFSA Scientific Opinion 2013;11(4):3145 (no numerical ADI considered necessary)."
  },
  {
    e_number: "E170",
    names: ["carbonate de calcium", "calcium carbonate"],
    tier: 3,
    category: "colorant",
    concern: "Calcium carbonate, used as a white colorant + acidity regulator + calcium-fortification carrier. Identical to dietary calcium chemistry.",
    source: 'EFSA Scientific Opinion 2011;9(7):2318 (ADI not specified, "not of safety concern at use levels").'
  },
  {
    e_number: "E260",
    names: ["acide ac\xE9tique", "acetic acid"],
    tier: 3,
    category: "acidulant",
    concern: 'Acetic acid (vinegar). Long history of safe use; tier reflects "additive present" signal, not toxicity.',
    source: "EU authorisation without numerical ADI (quantum satis)."
  },
  {
    e_number: "E325",
    names: ["lactate de sodium", "sodium lactate"],
    tier: 3,
    category: "preservative",
    concern: "Sodium lactate. Common in deli meats as a moisture / shelf-life agent. Authorised without numerical ADI.",
    source: "EU Regulation 1333/2008 Annex II (group lactates)."
  },
  {
    e_number: "E327",
    names: ["lactate de calcium", "calcium lactate"],
    tier: 3,
    category: "preservative",
    concern: "Calcium lactate. Acidity regulator + calcium fortifier; widely used.",
    source: "EU Regulation 1333/2008 Annex II (group lactates)."
  },
  {
    e_number: "E332",
    names: ["citrate de potassium", "potassium citrate"],
    tier: 3,
    category: "acidulant",
    concern: "Citrate buffer. No safety concern.",
    source: "EU authorisation without ADI."
  },
  {
    e_number: "E460",
    names: ["cellulose", "cellulose microcristalline", "microcrystalline cellulose"],
    tier: 3,
    category: "thickener",
    concern: "Cellulose, a plant-fibre derivative. EFSA reaffirmed safety; presence signals processed-food formulation rather than a toxicity concern.",
    source: "EFSA Scientific Opinion 2018;16(1):5047 (group cellulose ADI not specified)."
  },
  {
    e_number: "E509",
    names: ["chlorure de calcium", "calcium chloride"],
    tier: 3,
    category: "stabilizer",
    concern: "Calcium chloride. Firming agent for canned vegetables + tofu. No safety concern at use levels.",
    source: "EU Regulation 1333/2008 Annex II (quantum satis)."
  },
  {
    e_number: "E575",
    names: ["glucono-delta-lactone", "glucono delta lactone", "gdl"],
    tier: 3,
    category: "acidulant",
    concern: "Slow-release acidulant used in baked goods + tofu coagulation. Hydrolyses to gluconic acid in the gut.",
    source: "EU authorisation without numerical ADI."
  },
  {
    e_number: "E627",
    names: ["guanylate disodique", "disodium guanylate"],
    tier: 3,
    category: "flavor_enhancer",
    concern: "Nucleotide flavour enhancer, almost always paired with E621 (MSG) for synergistic umami. Authorised without specific ADI; presence signals UPF flavour engineering.",
    source: "EFSA Scientific Opinion 2017;15(7):4910 (group ribonucleotides + glutamates)."
  },
  {
    e_number: "E631",
    names: ["inosinate disodique", "disodium inosinate"],
    tier: 3,
    category: "flavor_enhancer",
    concern: "Nucleotide flavour enhancer, paired with MSG for umami synergy. Often derived from animal sources (fish, meat) \u2014 relevant for vegetarian / vegan diets.",
    source: "EFSA Scientific Opinion 2017;15(7):4910."
  },
  {
    e_number: "E635",
    names: ["ribonucl\xE9otides disodiques", "disodium ribonucleotides"],
    tier: 3,
    category: "flavor_enhancer",
    concern: "Disodium ribonucleotides (inosinate + guanylate mix). Same umami-synergy + UPF-marker context as E631 / E627.",
    source: "EFSA Scientific Opinion 2017;15(7):4910."
  },
  {
    e_number: "E901",
    names: ["cire d'abeille", "beeswax"],
    tier: 3,
    category: "glazing_agent",
    concern: "Glazing agent (apple shine, confectionery coatings). Authorised; not vegan.",
    source: "EFSA Scientific Opinion 2007 (re-evaluated; no ADI specified)."
  },
  {
    e_number: "E920",
    names: ["l-cyst\xE9ine", "l cysteine", "cyst\xE9ine", "cysteine"],
    tier: 3,
    category: "flour_treatment",
    concern: "Dough conditioner used in industrial bakery. Often derived from animal hair / feathers \u2014 relevant for vegetarian / vegan diets even though synthetic + microbial sources exist.",
    source: "EU Regulation 1333/2008 Annex II (industrial bakery quantum satis)."
  }
];
var COSMETIC_ADDITIVE_CATEGORIES = /* @__PURE__ */ new Set([
  "colorant",
  "flavor_enhancer",
  "artificial_sweetener"
]);
function normalize(s) {
  return s.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9\s-]/g, " ").replace(/\s+/g, " ").trim();
}
function findAdditive(ingredient) {
  const { e_number, name, category } = ingredient;
  if (e_number) {
    const norm = e_number.toUpperCase().replace(/\s/g, "");
    const match = ADDITIVES_DB.find((a) => a.e_number === norm);
    if (match) return match;
  }
  const normName = normalize(name);
  for (const additive of ADDITIVES_DB) {
    for (const synonym of additive.names) {
      if (normName.includes(normalize(synonym))) {
        return additive;
      }
    }
  }
  if (category === "additive") {
    const naturalColorants = {
      "curcuma": "E100",
      "curcumin": "E100",
      "paprika": "E160c",
      "betterave": "E162",
      "carmin": "E120",
      "cochenille": "E120",
      "caramel": "E150"
    };
    for (const [keyword, eNum] of Object.entries(naturalColorants)) {
      if (normName.includes(keyword)) {
        const match = ADDITIVES_DB.find((a) => a.e_number === eNum);
        if (match) return match;
      }
    }
  }
  return null;
}
var DEFAULT_THRESHOLDS = {
  protein_g: [3, 6, 12],
  fiber_g: [1.5, 3, 6],
  expected_kcal_range: [50, 400],
  expect_micronutrients: false
};
var DEFAULT_SAT_FAT = [5, 10, 15];
var DEFAULT_SUGAR = [5, 10, 15, 22.5];
var CATEGORY_THRESHOLDS = {
  sandwich: { protein_g: [5, 8, 12], fiber_g: [2, 4, 6], expected_kcal_range: [180, 320], expect_micronutrients: true },
  ready_meal: { protein_g: [4, 7, 10], fiber_g: [2, 4, 6], expected_kcal_range: [80, 200], expect_micronutrients: true },
  bread: { protein_g: [6, 9, 12], fiber_g: [3, 6, 9], expected_kcal_range: [220, 300], expect_micronutrients: false },
  breakfast_cereal: { protein_g: [6, 10, 14], fiber_g: [5, 8, 12], expected_kcal_range: [320, 420], expect_micronutrients: true },
  yogurt: { protein_g: [3, 5, 9], fiber_g: [0, 1, 2], expected_kcal_range: [40, 120], expect_micronutrients: true },
  cheese: { protein_g: [15, 20, 25], fiber_g: [0, 0, 0], expected_kcal_range: [200, 450], expect_micronutrients: true, sat_fat_thresholds: [12, 20, 30] },
  processed_meat: { protein_g: [10, 15, 22], fiber_g: [0, 0, 1], expected_kcal_range: [100, 400], expect_micronutrients: false },
  fresh_meat: { protein_g: [15, 20, 25], fiber_g: [0, 0, 0], expected_kcal_range: [100, 300], expect_micronutrients: true },
  fish: { protein_g: [15, 20, 25], fiber_g: [0, 0, 0], expected_kcal_range: [80, 250], expect_micronutrients: true },
  snack_sweet: { protein_g: [4, 7, 10], fiber_g: [2, 4, 6], expected_kcal_range: [350, 550], expect_micronutrients: false },
  snack_salty: { protein_g: [6, 9, 14], fiber_g: [3, 5, 8], expected_kcal_range: [400, 550], expect_micronutrients: false },
  beverage_soft: { protein_g: [0, 0, 0], fiber_g: [0, 0, 0], expected_kcal_range: [0, 50], expect_micronutrients: false },
  beverage_juice: { protein_g: [0, 0, 0], fiber_g: [0, 1, 2], expected_kcal_range: [20, 60], expect_micronutrients: true },
  beverage_water: { protein_g: [0, 0, 0], fiber_g: [0, 0, 0], expected_kcal_range: [0, 5], expect_micronutrients: false },
  condiment: { protein_g: [0, 3, 7], fiber_g: [0, 1, 3], expected_kcal_range: [20, 400], expect_micronutrients: false, sugar_thresholds: [10, 20, 30, 45] },
  oil_fat: { protein_g: [0, 0, 0], fiber_g: [0, 0, 0], expected_kcal_range: [700, 900], expect_micronutrients: false, sat_fat_thresholds: [20, 35, 50] },
  other: DEFAULT_THRESHOLDS
};
function getThresholds(cat) {
  return CATEGORY_THRESHOLDS[cat] ?? DEFAULT_THRESHOLDS;
}
var NAME_CATEGORY_PATTERNS = [
  // ---- Beverages (clearest signals; check first) ----
  [/\beau\b|\bwater\b|spring water|eau de source|eau min[eé]rale|eau gaz[eé]use/i, "beverage_water"],
  [/\bjus\b|\bjuice\b|\bnectar\b|smoothie|fruit drink/i, "beverage_juice"],
  [/\bsoda\b|\bcola\b|boisson gaz[eé]use|soft drink|\btonic\b|limonade|ic[eé][ -]?tea|th[eé] glac[eé]|energy drink|red bull|monster/i, "beverage_soft"],
  // ---- Dairy (yogurt before cheese — fromage blanc is yogurt-class) ----
  [/\byaourts?\b|yoghurt|yogurt|\bskyr\b|fromage[ -]?blanc|faisselle|\bquark\b|petit[- ]suisse/i, "yogurt"],
  [/\bfromages?\b|\bcheese\b|\bbrie\b|camembert|cheddar|gruy[eè]re|\bgouda\b|mozzarella|parmesan|\bfeta\b|roquefort|emmental|comt[eé]|reblochon|munster|\bch[eè]vre\b|ricotta|mascarpone|halloumi/i, "cheese"],
  // ---- Composed dishes that contain meat names; check before meat ----
  [/\bsandwich\b|\bburger\b|\bwrap\b|panini|\bkebab\b|\bcroque\b/i, "sandwich"],
  // Sweet snacks before meat so "pâte à tartiner" (Nutella class) wins
  // over any bare meat token a marketing name might carry.
  [/chocolats?\b|\bchocolate\b|\bbonbon|\bcandy\b|biscuits?\b|cookies?\b|g[aâ]teaux?\b|\bcakes?\b|\btartes?\b|\btarts?\b|brownie|\bdonut\b|beignet|barre chocolat[eé]e|kinder|nutella|m&m|haribo|m[aâ]rs|snickers|twix|bounty|pringles?[ -]?sweet|gauffres?\b|cr[eê]pes?\b|p[aâ]te [aà] tartiner/i, "snack_sweet"],
  // ---- Fish (before fresh_meat: "Filet de saumon" must hit fish) ----
  [/\bsaumon\b|\bthon\b|sardine|maquereau|\bhareng\b|cabillaud|\bmerlu\b|\bcolin\b|\btruite\b|crevette|\bcrabe\b|\bmoules\b|hu[iî]tres|\bbar\b|\bdorade\b/i, "fish"],
  // ---- Processed meat (before fresh_meat: saucisson > generic meat) ----
  [/\bjambon\b|saucisson|chorizo|\bbacon\b|\blardon|\bsalami\b|pancetta|prosciutto|merguez|\brillettes\b|\bp[aâ]t[eé]\b(?! [aà] tartiner)/i, "processed_meat"],
  // ---- Fresh meat ----
  [/\bpoulet\b|\bb[oœ]uf\b|\bporc\b|\bagneau\b|\bdinde\b|\bcanard\b|viande hach[eé]e|\bsteaks?\b|escalope|magret/i, "fresh_meat"],
  // ---- Bakery ----
  [/\bpain\b|\bbread\b|baguette|brioche|focaccia|ciabatta|\btoasts?\b|\bpita\b|tortilla|\bcracotte/i, "bread"],
  [/c[eé]r[eé]ales?\b|\bcereal\b|\bmuesli\b|\bgranola\b|porridge|flocons d['']avoine|\boats\b|cornflakes|chocapic|special k|fitness/i, "breakfast_cereal"],
  // ---- Ready meals ----
  [/plat pr[eé]par[eé]|plat cuisin[eé]|ready meal|micro[- ]?ondes|[aà] r[eé]chauffer|lasagne|gratin|paella|risotto|\bcurry\b|chili con carne|hachis parmentier|tartiflette|moussaka/i, "ready_meal"],
  // ---- Condiments (before salty: tapenade contains "olive"-like words) ----
  [/\bsauces?\b|mayonnaise|\bketchup\b|moutarde|mustard|vinaigrette|\bpesto\b|tahin[ei]|harissa|sambal|sriracha|wasabi|chutney|aioli|\btapenade\b/i, "condiment"],
  // ---- Oils + butter (before salty: "Huile d'olive" matches `olives?` too) ----
  [/huile d['']olive|huile de colza|huile de tournesol|huile v[eé]g[eé]tale|\bolive oil\b|sunflower oil|canola oil|margarine|\bbeurre\b|\bbutter\b|saindoux/i, "oil_fat"],
  // ---- Salty snacks ----
  [/\bchips\b|\bcrisps?\b|crackers?\b|biscuits? sal[eé]s?|\bpopcorn\b|\bpretzels?\b|cacahu[eè]tes?\b|noix de cajou|amande grill[eé]e|pistaches?\b|olives?\b/i, "snack_salty"]
];
function inferCategoryFromName(name) {
  if (!name || typeof name !== "string") return "other";
  for (const [re, cat] of NAME_CATEGORY_PATTERNS) {
    if (re.test(name)) return cat;
  }
  return "other";
}
var WHOLE_FOOD_KEYWORDS = [
  // Produce
  "tomate",
  "salade",
  "carotte",
  "\xE9pinard",
  "epinard",
  "poivron",
  "oignon",
  "ail",
  "courgette",
  "aubergine",
  "concombre",
  "brocoli",
  "chou",
  "betterave",
  "poireau",
  "potiron",
  "courge",
  // Fruits
  "fruit",
  "pomme",
  "poire",
  "orange",
  "citron",
  "pamplemousse",
  "mandarine",
  "abricot",
  "p\xEAche",
  "peche",
  "fraise",
  "framboise",
  "myrtille",
  "cassis",
  "cerise",
  "prune",
  "mirabelle",
  "raisin",
  "figue",
  "datte",
  "mangue",
  "ananas",
  "banane",
  "kiwi",
  "melon",
  "past\xE8que",
  "grenade",
  "coco",
  "noix de coco",
  // Legumes & nuts & seeds
  "lentille",
  "haricot",
  "pois",
  "f\xE8ve",
  "feve",
  "noix",
  "amande",
  "noisette",
  "pistache",
  "cajou",
  "graine",
  "s\xE9same",
  "sesame",
  "lin",
  "chia",
  "tournesol",
  // Grains
  "riz",
  "quinoa",
  "avoine",
  "bl\xE9",
  "ble",
  "seigle",
  "orge",
  "sarrasin",
  // Animal
  "oeuf",
  "\u0153uf",
  "poisson",
  "saumon",
  "thon",
  "sardine",
  "maquereau",
  "poulet",
  "boeuf",
  "porc",
  "viande",
  "dinde",
  "canard",
  "agneau",
  "jambon",
  // Dairy / other
  "fromage",
  "lait",
  "yaourt",
  "skyr",
  "eau",
  "miel",
  "l\xE9gume",
  "legume"
];
var HIDDEN_SUGAR_NAMES = [
  "dextrose",
  "maltodextrine",
  "maltodextrin",
  "sirop de glucose",
  "sirop de ma\xEFs",
  "sirop de mais",
  "glucose syrup",
  "fructose",
  "saccharose",
  "sucrose",
  "sirop de fructose",
  "sirop de glucose-fructose",
  "sucre inverti",
  "lactose",
  "m\xE9lasse",
  "melasse",
  "sirop d'agave",
  "isoglucose",
  "concentr\xE9 de jus",
  "concentr\xE9 de pomme",
  "concentr\xE9 de poire",
  "sirop d'\xE9rable",
  "sirop d'erable",
  "sirop de riz",
  "sirop de datte",
  "sucre de canne",
  "sucre roux",
  "cassonade",
  "rapadura",
  "panela",
  "sucre inverti",
  "jus de fruits concentr\xE9",
  "jus concentr\xE9 de pomme",
  "sirop de bl\xE9",
  "sirop de ble",
  "caramel de sucre"
];
var GENERIC_OIL_TERMS = [
  "huile v\xE9g\xE9tale",
  "huile vegetale",
  "vegetable oil",
  "mati\xE8re grasse v\xE9g\xE9tale",
  "matiere grasse vegetale",
  "graisse v\xE9g\xE9tale",
  "graisse vegetale"
];
var FIRST_INGREDIENT_PENALTY_PATTERNS = [
  { re: /^(sucre|sirop|dextrose|fructose|glucose|maltodextrin)/i, label: "sugar/syrup" },
  { re: /^(huile|graisse|matière grasse|margarine)/i, label: "oil/fat" },
  { re: /^(amidon modifié|amidon de maïs modifié)/i, label: "modified starch" }
];
var UPF_MARKER_PATTERNS = [
  { re: /\bar[oô]mes?\b/i, label: "flavorings (ar\xF4mes)" },
  { re: /\bconcentr[eé] des? min[eé]raux|mineral concentrate/i, label: "mineral concentrate" },
  { re: /\bisolat de |\bprot[eé]ine isol[eé]e|protein isolate/i, label: "protein isolate" },
  { re: /\bhydrolysat|prot[eé]ines? hydrolys[eé]es?|hydrolyzed protein/i, label: "protein hydrolysate" },
  { re: /\bamidon modifi|modified starch|maltodextrin/i, label: "modified starch" }
];
function detectUPFMarkers(ings) {
  const hits = [];
  for (const marker of UPF_MARKER_PATTERNS) {
    if (ings.some((i) => marker.re.test(i.name))) hits.push(marker.label);
  }
  return hits;
}
var FRESH_PRODUCE_NAME = /^(banane|banana|pomme|apple|poire|pear|tomate|tomato|oignon|onion|avocat|avocado|carotte|carrot|concombre|cucumber|courgette|zucchini|kiwi|orange|citron|lemon|lime|fraise|strawberr|framboise|raspberr|myrtille|blueberr|cassis|blackcurrant|ananas|pineapple|raisin|grape|cerise|cherry|prune|plum|peche|pêche|peach|mangue|mango|papaye|papaya|poireau|leek|chou|cabbage|brocoli|broccoli|salade|lettuce|epinard|épinard|spinach|radis|radish|navet|turnip|betterave|beet|aubergine|eggplant|poivron|bell pepper|piment|chili pepper|champignon|mushroom|asperge|asparagus|artichaut|artichoke|haricot vert|green bean|haricot|bean|lentille|lentil|petit[- ]pois|pea|patate douce|sweet potato|pomme de terre|potato|courge|squash|citrouille|pumpkin|ail|garlic|gingembre|ginger|fenouil|fennel|celeri|céleri|celery|persil|parsley|basilic|basil|menthe|mint|coriandre|cilantro|ciboulette|chive|roquette|arugula|mache|mâche|cresson|watercress|endive|chicory|pastèque|watermelon|melon|nectarine|abricot|apricot|figue|fig|datte|date|grenade|pomegranate|noix|nut|amande|almond|noisette|hazelnut)s?\b/i;
function inferNovaClassWithConfidence(product) {
  const ings = product.ingredients;
  if (ings.length === 0) {
    if (FRESH_PRODUCE_NAME.test(String(product.name ?? "").trim())) {
      return { nova: 1, confidence: "high" };
    }
    return { nova: 4, confidence: "low" };
  }
  const additives = ings.filter((i) => i.category === "additive" || !!i.e_number);
  const cosmetics = additives.map((i) => findAdditive(i)).filter((a) => a !== null && COSMETIC_ADDITIVE_CATEGORIES.has(a.category));
  const upfMarkers = detectUPFMarkers(ings);
  if (ings.length === 1 && additives.length === 0 && upfMarkers.length === 0) {
    return { nova: 1, confidence: "high" };
  }
  if (ings.length <= 3 && additives.length === 0 && upfMarkers.length === 0) {
    const onlyCulinary = ings.every(
      (i) => /^(sucre|sel|huile|beurre|graisse|miel|vinaigre|eau)/i.test(i.name.trim())
    );
    if (onlyCulinary) return { nova: 2, confidence: "high" };
  }
  if (cosmetics.length === 0 && upfMarkers.length === 0 && additives.length <= 2 && ings.length <= 10) {
    return { nova: 3, confidence: "medium" };
  }
  const hasPositiveEvidence = cosmetics.length > 0 || upfMarkers.length > 0;
  return { nova: 4, confidence: hasPositiveEvidence ? "medium" : "low" };
}
function scoreProcessing(product) {
  const MAX = 20;
  const deductions = [];
  const bonuses = [];
  const inferredResult = inferNovaClassWithConfidence(product);
  const inferred = inferredResult.nova;
  const effectiveNova = (() => {
    if (!product.nova_class) return inferred;
    if (product.nova_class === 4 && inferred < 4) return inferred;
    return product.nova_class;
  })();
  if (effectiveNova !== product.nova_class) {
    deductions.push({
      pillar: "processing",
      reason: `NOVA auto-adjusted ${product.nova_class}\u2192${effectiveNova} based on ingredients`,
      points: 0,
      severity: "info"
    });
  }
  const novaWasInferred = !product.nova_class || effectiveNova !== product.nova_class;
  if (novaWasInferred && inferredResult.confidence !== "high") {
    const confidenceNote = inferredResult.confidence === "low" ? "NOVA heuristic confidence: LOW \u2014 ingredient list missing or too short to classify reliably; score may be over-penalised" : "NOVA heuristic confidence: MEDIUM \u2014 inferred from absence of known additives; only as reliable as OCR completeness";
    deductions.push({
      pillar: "processing",
      reason: confidenceNote,
      points: 0,
      severity: "info"
    });
  }
  let base;
  switch (effectiveNova) {
    case 1:
      base = 20;
      break;
    case 2:
      base = 17;
      break;
    case 3:
      base = 13;
      break;
    case 4:
      base = 6;
      break;
  }
  deductions.push({
    pillar: "processing",
    reason: `NOVA class ${effectiveNova} base score`,
    points: base - MAX,
    severity: effectiveNova === 4 ? "major" : effectiveNova === 3 ? "moderate" : "info"
  });
  let score = base;
  if (product.ingredients.length > 10) {
    score -= 2;
    deductions.push({
      pillar: "processing",
      reason: `${product.ingredients.length} ingredients (>10 threshold)`,
      points: -2,
      severity: "minor"
    });
  }
  const cosmeticAdditives = product.ingredients.map((ing) => findAdditive(ing)).filter((a) => a !== null && COSMETIC_ADDITIVE_CATEGORIES.has(a.category));
  const upfMarkers = detectUPFMarkers(product.ingredients);
  if (upfMarkers.length > 0) {
    const penalty = Math.min(4, upfMarkers.length * 2);
    score -= penalty;
    deductions.push({
      pillar: "processing",
      reason: `${upfMarkers.length} UPF marker(s) (NOVA framework): ${upfMarkers.join(", ")}`,
      points: -penalty,
      severity: "minor"
    });
  }
  if (cosmeticAdditives.length > 0) {
    score -= 2;
    deductions.push({
      pillar: "processing",
      reason: "Contains cosmetic additives (colorants, flavor enhancers, etc.)",
      points: -2,
      severity: "minor",
      evidence: cosmeticAdditives.map((a) => `${a.e_number} (${a.category})`).join(", ")
    });
  }
  const first = product.ingredients[0];
  if (first) {
    const match = FIRST_INGREDIENT_PENALTY_PATTERNS.find((p) => p.re.test(first.name.trim()));
    if (match) {
      score -= 3;
      deductions.push({
        pillar: "processing",
        reason: `Primary ingredient is ${match.label}: "${first.name}"`,
        points: -3,
        severity: "moderate"
      });
    }
  }
  return {
    name: "Processing Level",
    max: MAX,
    score: Math.max(0, score),
    deductions,
    bonuses
  };
}
function scoreNutritionalDensity(product) {
  const MAX = 25;
  const deductions = [];
  const bonuses = [];
  const { nutrition, category, ingredients } = product;
  const thresholds = getThresholds(category);
  const [, pMed, pHigh] = thresholds.protein_g;
  const pLow = thresholds.protein_g[0];
  let proteinScore = 0;
  if (pHigh === 0) {
    proteinScore = 3;
  } else if (nutrition.protein_g >= pHigh) proteinScore = 6;
  else if (nutrition.protein_g >= pMed) proteinScore = 4;
  else if (nutrition.protein_g >= pLow) proteinScore = 2;
  if (proteinScore < 6) {
    deductions.push({
      pillar: "nutritional_density",
      reason: `Protein ${nutrition.protein_g}g/100g (${proteinScore}/6 for category ${category})`,
      points: proteinScore - 6,
      severity: proteinScore === 0 ? "moderate" : "minor"
    });
  } else {
    bonuses.push({
      pillar: "nutritional_density",
      reason: `High protein content: ${nutrition.protein_g}g/100g`,
      points: 6,
      severity: "info"
    });
  }
  const [fLow, fMed, fHigh] = thresholds.fiber_g;
  let fiberScore = 0;
  if (fHigh === 0) {
    fiberScore = 3;
  } else if (nutrition.fiber_g >= fHigh) fiberScore = 5;
  else if (nutrition.fiber_g >= fMed) fiberScore = 3;
  else if (nutrition.fiber_g >= fLow) fiberScore = 1;
  if (fiberScore < 5 && fHigh > 0) {
    deductions.push({
      pillar: "nutritional_density",
      reason: `Fiber ${nutrition.fiber_g}g/100g (${fiberScore}/5 for category ${category})`,
      points: fiberScore - 5,
      severity: fiberScore === 0 ? "moderate" : "minor"
    });
  }
  const wholeFoods = ingredients.filter((ing) => {
    const n = ing.name.toLowerCase();
    return ing.is_whole_food || WHOLE_FOOD_KEYWORDS.some((kw) => n.includes(kw));
  });
  let microScore;
  const declaredPcts = wholeFoods.map((w) => w.percentage).filter((p) => typeof p === "number" && p > 0);
  if (declaredPcts.length > 0) {
    const totalPct = declaredPcts.reduce((a, b) => a + b, 0);
    if (totalPct >= 30) microScore = 5;
    else if (totalPct >= 20) microScore = 4;
    else if (totalPct >= 12) microScore = 3;
    else if (totalPct >= 5) microScore = 2;
    else microScore = 1;
  } else {
    microScore = Math.min(5, wholeFoods.length);
  }
  if (!thresholds.expect_micronutrients) microScore = Math.min(microScore, 3);
  const declared = product.declared_micronutrients ?? [];
  if (declared.length >= 3 && microScore < 5) {
    const before = microScore;
    microScore = Math.min(5, microScore + 1);
    if (microScore > before) {
      bonuses.push({
        pillar: "nutritional_density",
        reason: `Declares ${declared.length} vitamins/minerals: ${declared.slice(0, 4).join(", ")}${declared.length > 4 ? "\u2026" : ""}`,
        points: 1,
        severity: "info"
      });
    }
  }
  const NRV_15_PCT = {
    vit_a_ug: 120,
    vit_c_mg: 12,
    vit_d_ug: 0.75,
    vit_e_mg: 1.8,
    vit_k_ug: 11.25,
    b1_mg: 0.165,
    b2_mg: 0.21,
    b3_mg: 2.4,
    b6_mg: 0.21,
    b9_ug: 30,
    b12_ug: 0.375,
    potassium_mg: 300,
    calcium_mg: 120,
    magnesium_mg: 56,
    iron_mg: 2.1,
    zinc_mg: 1.5
  };
  let densityHits = 0;
  const hitList = [];
  for (const [key, threshold] of Object.entries(NRV_15_PCT)) {
    const v = Number(nutrition[key] ?? 0);
    if (v >= threshold) {
      densityHits += 1;
      hitList.push(String(key).replace(/_[mu]g$/, "").replace("_", " "));
    }
  }
  if (densityHits >= 6 && microScore < 5) {
    const before = microScore;
    microScore = Math.min(5, microScore + 2);
    bonuses.push({
      pillar: "nutritional_density",
      reason: `Nutrient-rich: 6+ vitamins/minerals at \u226515% NRV per 100g (${hitList.slice(0, 6).join(", ")}\u2026)`,
      points: microScore - before,
      severity: "info"
    });
  } else if (densityHits >= 3 && microScore < 5) {
    const before = microScore;
    microScore = Math.min(5, microScore + 1);
    if (microScore > before) {
      bonuses.push({
        pillar: "nutritional_density",
        reason: `Source of ${densityHits} nutrients (\u226515% NRV): ${hitList.slice(0, 4).join(", ")}`,
        points: 1,
        severity: "info"
      });
    }
  }
  if (microScore < 5) {
    const reason = declaredPcts.length > 0 ? `Whole foods = ${declaredPcts.reduce((a, b) => a + b, 0).toFixed(1)}% of product (${microScore}/5)` : `${wholeFoods.length} recognizable whole-food ingredients (${microScore}/5)`;
    deductions.push({
      pillar: "nutritional_density",
      reason,
      points: microScore - 5,
      severity: "minor"
    });
  }
  let fatScore = 5;
  if ((nutrition.trans_fat_g ?? 0) > 0.1) {
    fatScore = 0;
  } else if (nutrition.fat_g === 0) {
    fatScore = 3;
  } else {
    const satRatio = nutrition.saturated_fat_g / nutrition.fat_g;
    if (satRatio > 0.5) fatScore = 1;
    else if (satRatio > 0.33) fatScore = 3;
    else if (satRatio > 0.2) fatScore = 4;
    else fatScore = 5;
    const hasHealthyFatSource = ingredients.some((ing) => {
      const n = ing.name.toLowerCase();
      return /huile d'olive|huile de colza|huile de lin|huile de noix/.test(n) || /saumon|sardine|maquereau|thon|hareng/.test(n) || /noix|amande|noisette|pistache|cajou|graine de lin|graine de chia/.test(n);
    });
    const hasBadFatSource = ingredients.some((ing) => {
      const n = ing.name.toLowerCase();
      return /huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah/.test(n);
    });
    if (hasHealthyFatSource && !hasBadFatSource && fatScore < 5) {
      const before = fatScore;
      fatScore = Math.min(5, fatScore + 1);
      if (fatScore > before) {
        bonuses.push({
          pillar: "nutritional_density",
          reason: "Healthy fat source in ingredients (olive/canola/fish/nuts)",
          points: 1,
          severity: "info"
        });
      }
    }
  }
  if (fatScore < 5) {
    deductions.push({
      pillar: "nutritional_density",
      reason: `Sat fat ratio unfavorable (${nutrition.saturated_fat_g}g sat / ${nutrition.fat_g}g total)`,
      points: fatScore - 5,
      severity: fatScore === 0 ? "critical" : "minor"
    });
  }
  const satietySignal = nutrition.protein_g + nutrition.fiber_g * 2 - nutrition.sugars_g;
  let satietyScore;
  if (satietySignal >= 10) satietyScore = 4;
  else if (satietySignal >= 5) satietyScore = 3;
  else if (satietySignal >= 0) satietyScore = 2;
  else if (satietySignal >= -5) satietyScore = 1;
  else satietyScore = 0;
  if (satietyScore < 4) {
    deductions.push({
      pillar: "nutritional_density",
      reason: `Satiety index: P${nutrition.protein_g} + 2\xD7F${nutrition.fiber_g} \u2212 S${nutrition.sugars_g} = ${satietySignal.toFixed(1)} (${satietyScore}/4)`,
      points: satietyScore - 4,
      severity: "minor"
    });
  }
  const totalScore = proteinScore + fiberScore + microScore + fatScore + satietyScore;
  return {
    name: "Nutritional Density",
    max: MAX,
    score: Math.max(0, Math.min(MAX, totalScore)),
    deductions,
    bonuses
  };
}
function scoreNegativeNutrients(product) {
  const MAX = 25;
  const deductions = [];
  const bonuses = [];
  const { nutrition, category } = product;
  const thresholds = getThresholds(category);
  let score = MAX;
  const sat = nutrition.saturated_fat_g;
  const [satMod, satMaj, satCrit] = thresholds.sat_fat_thresholds ?? DEFAULT_SAT_FAT;
  if (sat > satCrit) {
    score -= 9;
    deductions.push({
      pillar: "negative_nutrients",
      reason: `Saturated fat ${sat}g/100g (>${satCrit}g critical for ${category})`,
      points: -9,
      severity: "critical"
    });
  } else if (sat > satMaj) {
    score -= 6;
    deductions.push({
      pillar: "negative_nutrients",
      reason: `Saturated fat ${sat}g/100g (>${satMaj}g major for ${category})`,
      points: -6,
      severity: "major"
    });
  } else if (sat > satMod) {
    score -= 3;
    deductions.push({
      pillar: "negative_nutrients",
      reason: `Saturated fat ${sat}g/100g (>${satMod}g moderate for ${category})`,
      points: -3,
      severity: "moderate"
    });
  }
  const sugars = nutrition.added_sugars_g ?? nutrition.sugars_g;
  const sugarLabel = nutrition.added_sugars_g != null ? "Added sugars" : "Total sugars (added not declared)";
  const [sMinor, sMod, sMaj, sCrit] = thresholds.sugar_thresholds ?? DEFAULT_SUGAR;
  if (sugars > sCrit) {
    score -= 12;
    deductions.push({ pillar: "negative_nutrients", reason: `${sugarLabel} ${sugars}g/100g (>${sCrit}g critical for ${category})`, points: -12, severity: "critical" });
  } else if (sugars > sMaj) {
    score -= 9;
    deductions.push({ pillar: "negative_nutrients", reason: `${sugarLabel} ${sugars}g/100g (>${sMaj}g major for ${category})`, points: -9, severity: "major" });
  } else if (sugars > sMod) {
    score -= 6;
    deductions.push({ pillar: "negative_nutrients", reason: `${sugarLabel} ${sugars}g/100g (>${sMod}g moderate for ${category})`, points: -6, severity: "moderate" });
  } else if (sugars > sMinor) {
    score -= 3;
    deductions.push({ pillar: "negative_nutrients", reason: `${sugarLabel} ${sugars}g/100g (>${sMinor}g minor for ${category})`, points: -3, severity: "minor" });
  }
  const salt = nutrition.salt_g;
  if (salt > 1.5) {
    score -= 6;
    deductions.push({ pillar: "negative_nutrients", reason: `Salt ${salt}g/100g (>1.5g critical)`, points: -6, severity: "major" });
  } else if (salt > 1.25) {
    score -= 4;
    deductions.push({ pillar: "negative_nutrients", reason: `Salt ${salt}g/100g (>1.25g moderate)`, points: -4, severity: "moderate" });
  } else if (salt > 0.75) {
    score -= 2;
    deductions.push({ pillar: "negative_nutrients", reason: `Salt ${salt}g/100g (>0.75g minor)`, points: -2, severity: "minor" });
  }
  const trans = nutrition.trans_fat_g ?? 0;
  if (trans > 0.1) {
    score -= 10;
    deductions.push({
      pillar: "negative_nutrients",
      reason: `Trans fat present: ${trans}g/100g (no safe level)`,
      points: -10,
      severity: "critical"
    });
  }
  const [kcalLow, kcalHigh] = thresholds.expected_kcal_range;
  if (nutrition.energy_kcal > kcalHigh * 1.25 || nutrition.energy_kcal < kcalLow * 0.5) {
    score -= 2;
    deductions.push({
      pillar: "negative_nutrients",
      reason: `Energy ${nutrition.energy_kcal} kcal/100g outside category norm (${kcalLow}\u2013${kcalHigh})`,
      points: -2,
      severity: "minor"
    });
  }
  return {
    name: "Negative Nutrients",
    max: MAX,
    score: Math.max(0, score),
    deductions,
    bonuses
  };
}
function scoreAdditiveRisk(product) {
  const MAX = 15;
  const deductions = [];
  const bonuses = [];
  const tier1Hits = [];
  const tier2Hits = [];
  const tier3Hits = [];
  for (const ing of product.ingredients) {
    const additive = findAdditive(ing);
    if (!additive) continue;
    const hit = {
      ingredient: ing.name,
      additive: additive.e_number,
      concern: additive.concern
    };
    if (additive.tier === 1) tier1Hits.push(hit);
    else if (additive.tier === 2) tier2Hits.push(hit);
    else tier3Hits.push(hit);
  }
  let score = MAX;
  if (tier1Hits.length > 0) {
    const penalty = Math.min(10, tier1Hits.length * 5);
    score -= penalty;
    deductions.push({
      pillar: "additive_risk",
      reason: `${tier1Hits.length} Tier-1 additive${tier1Hits.length > 1 ? "s" : ""} (serious concern)`,
      points: -penalty,
      severity: "critical",
      evidence: tier1Hits.map((h) => `${h.additive} (${h.ingredient}): ${h.concern}`).join(" | ")
    });
  }
  if (tier2Hits.length > 0) {
    const penalty = Math.min(6, tier2Hits.length * 2);
    score -= penalty;
    deductions.push({
      pillar: "additive_risk",
      reason: `${tier2Hits.length} Tier-2 additive${tier2Hits.length > 1 ? "s" : ""} (moderate concern)`,
      points: -penalty,
      severity: "moderate",
      evidence: tier2Hits.map((h) => `${h.additive} (${h.ingredient}): ${h.concern}`).join(" | ")
    });
  }
  if (tier3Hits.length > 0) {
    const penalty = Math.min(3, tier3Hits.length * 1);
    score -= penalty;
    deductions.push({
      pillar: "additive_risk",
      reason: `${tier3Hits.length} Tier-3 additive${tier3Hits.length > 1 ? "s" : ""} (minor concern)`,
      points: -penalty,
      severity: "minor",
      evidence: tier3Hits.map((h) => `${h.additive} (${h.ingredient}): ${h.concern}`).join(" | ")
    });
  }
  return {
    name: "Additive Risk",
    max: MAX,
    score: Math.max(0, score),
    deductions,
    bonuses
  };
}
function countTier1Additives(product) {
  return product.ingredients.map((ing) => findAdditive(ing)).filter((a) => a !== null && a.tier === 1).length;
}
function isWholeFood(name, isFlag) {
  if (isFlag === true) return true;
  const lower = name.toLowerCase();
  if (/sirop|huile|farine raffinée|amidon modifié|isolat|concentré/i.test(lower)) return false;
  return WHOLE_FOOD_KEYWORDS.some((kw) => lower.includes(kw));
}
function scoreIngredientIntegrity(product) {
  const MAX = 15;
  const deductions = [];
  const bonuses = [];
  let score = 0;
  const first3 = product.ingredients.slice(0, 3);
  const first3Whole = first3.filter((ing) => isWholeFood(ing.name, ing.is_whole_food)).length;
  const first3Score = Math.round(first3Whole / 3 * 5);
  score += first3Score;
  if (first3Score < 5) {
    deductions.push({
      pillar: "ingredient_integrity",
      reason: `Only ${first3Whole}/3 of first ingredients are whole foods (${first3Score}/5)`,
      points: first3Score - 5,
      severity: "moderate"
    });
  } else {
    bonuses.push({
      pillar: "ingredient_integrity",
      reason: "First 3 ingredients are all whole foods",
      points: 5,
      severity: "info"
    });
  }
  const nonAdditive = product.ingredients.filter((ing) => !findAdditive(ing));
  const recognizable = nonAdditive.filter((ing) => {
    const n = ing.name.toLowerCase();
    return n.length < 40 && !/isolat|hydrolysat|concentré|modifié|extrait sec/i.test(n);
  }).length;
  const recogRatio = nonAdditive.length > 0 ? recognizable / nonAdditive.length : 1;
  const recogScore = recogRatio >= 0.8 ? 3 : recogRatio >= 0.6 ? 2 : recogRatio >= 0.4 ? 1 : 0;
  score += recogScore;
  if (recogScore < 3) {
    deductions.push({
      pillar: "ingredient_integrity",
      reason: `${(recogRatio * 100).toFixed(0)}% of ingredients are recognizable (${recogScore}/3)`,
      points: recogScore - 3,
      severity: "minor"
    });
  }
  if (product.origin_transparent || product.origin) {
    score += 2;
    bonuses.push({
      pillar: "ingredient_integrity",
      reason: `Origin declared: ${product.origin ?? "transparent"}`,
      points: 2,
      severity: "info"
    });
  } else {
    deductions.push({
      pillar: "ingredient_integrity",
      reason: "No origin information",
      points: -2,
      severity: "minor"
    });
  }
  const sugarAliases = /* @__PURE__ */ new Set();
  for (const ing of product.ingredients) {
    const n = ing.name.toLowerCase();
    for (const alias of HIDDEN_SUGAR_NAMES) {
      if (n.includes(alias)) sugarAliases.add(alias);
    }
    if (/^sucre/i.test(ing.name.trim())) sugarAliases.add("sucre");
  }
  if (sugarAliases.size >= 2) {
    deductions.push({
      pillar: "ingredient_integrity",
      reason: `${sugarAliases.size} distinct sugar sources detected: ${Array.from(sugarAliases).join(", ")}`,
      points: -2,
      severity: "moderate"
    });
  } else {
    score += 2;
    bonuses.push({
      pillar: "ingredient_integrity",
      reason: sugarAliases.size === 1 ? "Single transparent sugar source" : "No hidden sugars",
      points: 2,
      severity: "info"
    });
  }
  const hasGenericOil = product.ingredients.some((ing) => {
    const n = ing.name.toLowerCase();
    return GENERIC_OIL_TERMS.some((g) => n.includes(g));
  });
  if (product.named_oils !== false && !hasGenericOil) {
    score += 3;
    bonuses.push({
      pillar: "ingredient_integrity",
      reason: 'Oils are specifically named (not generic "vegetable oil")',
      points: 3,
      severity: "info"
    });
  } else {
    deductions.push({
      pillar: "ingredient_integrity",
      reason: 'Generic "vegetable oil" used instead of specific named oil',
      points: -3,
      severity: "minor"
    });
  }
  return {
    name: "Ingredient Integrity",
    max: MAX,
    score: Math.max(0, Math.min(MAX, score)),
    deductions,
    bonuses
  };
}
var ENGINE_VERSION = "2.2.0";
function scoreToGrade(score) {
  if (score >= 85) return "A+";
  if (score >= 70) return "A";
  if (score >= 55) return "B";
  if (score >= 40) return "C";
  if (score >= 25) return "D";
  return "F";
}
function gradeVerdict(grade) {
  switch (grade) {
    case "A+":
      return "Excellent \u2014 daily staple potential";
    case "A":
      return "Good \u2014 regular consumption fine";
    case "B":
      return "Acceptable \u2014 moderate frequency";
    case "C":
      return "Mediocre \u2014 occasional only";
    case "D":
      return "Poor \u2014 avoid regular use";
    case "F":
      return "Very poor \u2014 avoid";
  }
}
function computeGlobalBonuses(product) {
  const bonuses = [];
  if (product.organic) {
    bonuses.push({ pillar: "global_bonus", reason: "Organic certification", points: 2, severity: "info" });
  }
  if (product.whole_grain_primary) {
    bonuses.push({ pillar: "global_bonus", reason: "Whole grain as primary grain", points: 3, severity: "info" });
  }
  if (product.fermented) {
    bonuses.push({ pillar: "global_bonus", reason: "Contains fermented / probiotic content", points: 2, severity: "info" });
  }
  const omega3 = product.ingredients.find((ing) => {
    const n = ing.name.toLowerCase();
    return /(graine de )?lin\b|\bchia\b|\bnoix\b|saumon|sardine|maquereau|hareng|anchois/.test(n);
  });
  if (omega3) {
    bonuses.push({
      pillar: "global_bonus",
      reason: `Omega-3 source: ${omega3.name}`,
      points: 2,
      severity: "info"
    });
  }
  return bonuses;
}
function computeGlobalPenalties(product) {
  const penalties = [];
  if (product.has_misleading_marketing) {
    penalties.push({
      pillar: "global_penalty",
      reason: 'Misleading marketing claims (e.g., "natural" / "light" unjustified)',
      points: -2,
      severity: "moderate"
    });
  }
  if (product.has_health_claims) {
    penalties.push({
      pillar: "global_penalty",
      reason: "Health claims present \u2014 verify vs composition",
      points: -3,
      severity: "moderate"
    });
  }
  const palm = product.ingredients.find((ing) => {
    const n = ing.name.toLowerCase();
    return /huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah/.test(n);
  });
  if (palm) {
    penalties.push({
      pillar: "global_penalty",
      reason: `Palm oil or derivative: ${palm.name}`,
      points: -3,
      severity: "moderate"
    });
  }
  return penalties;
}
function checkVeto(product) {
  const { nutrition, category, ingredients } = product;
  if ((nutrition.trans_fat_g ?? 0) > 0.1) {
    return { triggered: true, reason: "Contains industrial trans fats \u2014 no safe level", cap: 40 };
  }
  const tier1Count = countTier1Additives(product);
  if (tier1Count > 3) {
    return { triggered: true, reason: `${tier1Count} Tier-1 additives \u2014 cumulative risk too high`, cap: 40 };
  }
  const hasNitrites = ingredients.some((ing) => {
    const eNum = (ing.e_number || "").toUpperCase().replace(/\s/g, "");
    if (eNum === "E249" || eNum === "E250") return true;
    const n = ing.name.toLowerCase();
    return n.includes("nitrite") || n.includes("e249") || n.includes("e250");
  });
  const highSalt = nutrition.salt_g > 1.5;
  const refined = ingredients.some((ing) => /farine de blé|farine raffinée|amidon|dextrose/i.test(ing.name.toLowerCase()));
  if (hasNitrites && highSalt && refined && category === "processed_meat") {
    return { triggered: true, reason: "Processed meat with nitrites + high salt + refined starch combination", cap: 40 };
  }
  const isConfectionery = category === "snack_sweet";
  const sugars = nutrition.added_sugars_g ?? nutrition.sugars_g;
  if (!isConfectionery && sugars > 30) {
    return { triggered: true, reason: "Added sugar >30g/100g in non-confectionery category", cap: 40 };
  }
  if (category === "beverage_soft" && sugars > 5 && nutrition.protein_g < 1 && nutrition.fiber_g < 1) {
    return { triggered: true, reason: "Sugar-sweetened beverage with no nutritional contribution", cap: 30 };
  }
  const hasMechanicallySeparated = ingredients.some(
    (ing) => /séparée mécaniquement|mechanically separated|msm/i.test(ing.name)
  );
  if (hasMechanicallySeparated && product.nova_class === 4) {
    return { triggered: true, reason: "Mechanically separated meat in NOVA 4 product \u2014 low meat quality", cap: 45 };
  }
  return { triggered: false, reason: "", cap: 100 };
}
function buildFlags(audit) {
  const red = [];
  const green = [];
  const allDeductions = [
    ...audit.pillars.processing.deductions,
    ...audit.pillars.nutritional_density.deductions,
    ...audit.pillars.negative_nutrients.deductions,
    ...audit.pillars.additive_risk.deductions,
    ...audit.pillars.ingredient_integrity.deductions,
    ...audit.global_penalties
  ];
  const allBonuses = [
    ...audit.pillars.processing.bonuses,
    ...audit.pillars.nutritional_density.bonuses,
    ...audit.pillars.negative_nutrients.bonuses,
    ...audit.pillars.additive_risk.bonuses,
    ...audit.pillars.ingredient_integrity.bonuses,
    ...audit.global_bonuses
  ];
  for (const d of allDeductions) {
    if (d.severity === "critical" || d.severity === "major") red.push(d.reason);
  }
  for (const b of allBonuses) {
    if (b.points >= 2) green.push(b.reason);
  }
  const eco = audit.eco;
  if (eco?.grade) {
    const g = String(eco.grade).toLowerCase();
    if (g === "a" || g === "b") {
      green.push(`Eco-score ${g.toUpperCase()} (${eco.value ?? "\u2014"}/100) \u2014 low environmental impact`);
    } else if (g === "d" || g === "e") {
      red.push(`Eco-score ${g.toUpperCase()} (${eco.value ?? "\u2014"}/100) \u2014 high environmental impact`);
    }
  }
  if (audit.veto.triggered) {
    red.unshift(`VETO: ${audit.veto.reason}`);
  }
  return { red, green };
}
function collectWarnings(product) {
  const warnings = [];
  if (product.nutrition.trans_fat_g == null) {
    warnings.push("trans_fat_g not declared \u2014 assumed 0");
  }
  if (product.nutrition.added_sugars_g == null) {
    warnings.push("added_sugars_g not declared \u2014 using total sugars as proxy");
  }
  return warnings;
}
function applyCategoryInference(product) {
  if (product.category !== "other") return { product, inferred: null };
  const inferred = inferCategoryFromName(product.name);
  if (inferred === "other") return { product, inferred: null };
  return { product: { ...product, category: inferred }, inferred };
}
function scoreProduct(input) {
  const { product, inferred } = applyCategoryInference(input);
  const processing = scoreProcessing(product);
  const nutritional_density = scoreNutritionalDensity(product);
  const negative_nutrients = scoreNegativeNutrients(product);
  const additive_risk = scoreAdditiveRisk(product);
  const ingredient_integrity = scoreIngredientIntegrity(product);
  let score = processing.score + nutritional_density.score + negative_nutrients.score + additive_risk.score + ingredient_integrity.score;
  const global_bonuses = computeGlobalBonuses(product);
  const bonusTotal = Math.min(10, global_bonuses.reduce((s, b) => s + b.points, 0));
  const global_penalties = computeGlobalPenalties(product);
  const penaltyTotal = global_penalties.reduce((s, p) => s + p.points, 0);
  score = score + bonusTotal + penaltyTotal;
  score = Math.max(0, Math.min(100, score));
  const veto = checkVeto(product);
  if (veto.triggered && score > veto.cap) {
    score = veto.cap;
  }
  score = Math.round(score);
  const grade = scoreToGrade(score);
  const preAudit = {
    product_name: product.name,
    category: product.category,
    score,
    grade,
    verdict: gradeVerdict(grade),
    pillars: {
      processing,
      nutritional_density,
      negative_nutrients,
      additive_risk,
      ingredient_integrity
    },
    global_bonuses,
    global_penalties,
    veto,
    engine_version: ENGINE_VERSION,
    warnings: [
      ...collectWarnings(product),
      ...inferred ? [`Category inferred from name as "${inferred}" (input had category="other")`] : []
    ],
    // Fix #2 — eco-score side-channel. buildFlags reads this to
    // surface environmental impact as a red/green flag without
    // disrupting the 5-pillar numeric score.
    eco: product.ecoscore_grade ? { grade: product.ecoscore_grade, value: product.ecoscore_value } : void 0,
    nutriscore_grade: product.nutriscore_grade ?? null
  };
  const { red, green } = buildFlags(preAudit);
  return {
    ...preAudit,
    red_flags: red,
    green_flags: green
  };
}

// src/ocr-parser.ts
var WHOLE_FOOD_KEYWORDS2 = [
  // Produce
  "tomate",
  "salade",
  "carotte",
  "\xE9pinard",
  "epinard",
  "poivron",
  "oignon",
  "ail",
  "courgette",
  "aubergine",
  "concombre",
  "brocoli",
  "chou",
  "betterave",
  "poireau",
  "potiron",
  "courge",
  // Fruits
  "fruit",
  "pomme",
  "poire",
  "orange",
  "citron",
  "pamplemousse",
  "mandarine",
  "abricot",
  "p\xEAche",
  "peche",
  "fraise",
  "framboise",
  "myrtille",
  "cassis",
  "cerise",
  "prune",
  "mirabelle",
  "raisin",
  "figue",
  "datte",
  "mangue",
  "ananas",
  "banane",
  "kiwi",
  "melon",
  "past\xE8que",
  "grenade",
  "coco",
  "noix de coco",
  // Legumes & nuts & seeds
  "lentille",
  "haricot",
  "pois",
  "f\xE8ve",
  "feve",
  "noix",
  "amande",
  "noisette",
  "pistache",
  "cajou",
  "graine",
  "s\xE9same",
  "sesame",
  "lin",
  "chia",
  "tournesol",
  // Grains
  "riz",
  "quinoa",
  "avoine",
  "bl\xE9",
  "ble",
  "seigle",
  "orge",
  "sarrasin",
  "farine compl\xE8te",
  "farine complete",
  // Animal
  "oeuf",
  "\u0153uf",
  "poisson",
  "saumon",
  "thon",
  "sardine",
  "maquereau",
  "poulet",
  "boeuf",
  "porc",
  "viande",
  "dinde",
  "canard",
  "agneau",
  "jambon",
  // Dairy / other
  "fromage",
  "lait",
  "yaourt",
  "skyr",
  "eau",
  "miel",
  "l\xE9gume",
  "legume"
];
var NON_WHOLE_FOOD_MARKERS = /sirop|isolat|hydrolysat|concentré|concentre|modifié|modifie|arôme|arome|extrait sec|poudre de|amidon/i;
var GENERIC_OIL_TERMS2 = [
  "huile v\xE9g\xE9tale",
  "huile vegetale",
  "vegetable oil",
  "mati\xE8re grasse v\xE9g\xE9tale",
  "matiere grasse vegetale",
  "graisse v\xE9g\xE9tale",
  "graisse vegetale"
];
var CATEGORY_VALUES = [
  "sandwich",
  "ready_meal",
  "bread",
  "breakfast_cereal",
  "yogurt",
  "cheese",
  "processed_meat",
  "fresh_meat",
  "fish",
  "snack_sweet",
  "snack_salty",
  "beverage_soft",
  "beverage_juice",
  "beverage_water",
  "condiment",
  "oil_fat",
  "other"
];
var SYSTEM_PROMPT = `Tu es un expert en \xE9tiquetage alimentaire fran\xE7ais. On te fournit des photos d'emballages de produits de supermarch\xE9 (face + dos, panneau ingr\xE9dients, tableau nutritionnel). Tu dois extraire une fiche produit structur\xE9e EN JSON STRICT.

R\xE8gles non-n\xE9gociables:
- R\xE9ponds UNIQUEMENT avec du JSON valide, sans texte autour, sans balises markdown.
- Les valeurs nutritionnelles sont pour 100 g (ou 100 ml pour les liquides). Utilise null si une valeur n'est pas d\xE9clar\xE9e \u2014 jamais 0 par d\xE9faut.
- D\xE9cimales avec point (8.5, pas 8,5).

Liste d'ingr\xE9dients \u2014 CRUCIAL:
- Pr\xE9serve l'ordre tel qu'imprim\xE9 sur le paquet.
- Accepte N'IMPORTE QUEL format visuel: paragraphe continu s\xE9par\xE9 par virgules, liste \xE0 puces verticale, liste avec sauts de ligne, colonnes multiples, \xE9tiquette s\xE9par\xE9e. Lis-les tous et reconstruis la m\xEAme structure plate.
- Chaque ingr\xE9dient est un objet s\xE9par\xE9 dans le tableau \`ingredients\`.
- Si un ingr\xE9dient a un pourcentage d\xE9clar\xE9 (ex. "jambon 17,4%"), mets-le dans \`percentage\` sans le signe %.
- Si c'est un additif (conservateur, colorant, \xE9mulsifiant, \xE9paississant, acidifiant, antioxydant, exhausteur de go\xFBt, \xE9dulcorant\u2026), mets le num\xE9ro E dans \`e_number\` et category="additive".
- Ne fusionne jamais deux ingr\xE9dients en un seul, m\xEAme si le paquet les affiche sur la m\xEAme ligne.

Autres champs:
- nova_class: estime 1 (brut), 2 (ingr\xE9dient culinaire), 3 (transform\xE9) ou 4 (ultra-transform\xE9, >5 ingr\xE9dients industriels, additifs cosm\xE9tiques, additifs E).
- Si un code-barres EAN/UPC est visible, mets-le dans le champ "barcode" (13 ou 8 chiffres) sans espaces.
- En cas de doute sur un chiffre, renvoie null plut\xF4t que d'inventer.
- Laisse ingredients.name en fran\xE7ais tel qu'imprim\xE9 (avec accents).`;
var JSON_SCHEMA_HINT = `{
  "name": "string",
  "category": "sandwich|ready_meal|bread|breakfast_cereal|yogurt|cheese|processed_meat|fresh_meat|fish|snack_sweet|snack_salty|beverage_soft|beverage_juice|beverage_water|condiment|oil_fat|other",
  "nova_class": 1|2|3|4,
  "weight_g": number|null,
  "origin": "string|null",
  "organic": boolean,
  "has_health_claims": boolean,
  "has_misleading_marketing": boolean,
  "ingredients": [
    {
      "name": "string (fran\xE7ais, tel qu'imprim\xE9)",
      "percentage": number|null,
      "e_number": "E250|null",
      "category": "food|additive|processing_aid"
    }
  ],
  "nutrition": {
    "energy_kcal": number,
    "fat_g": number,
    "saturated_fat_g": number,
    "carbs_g": number,
    "sugars_g": number,
    "added_sugars_g": number|null,
    "fiber_g": number,
    "protein_g": number,
    "salt_g": number,
    "trans_fat_g": number|null
  }
}`;
var DEFAULT_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
var DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
var FALLBACK_MODEL = "llama-3.3-70b-versatile";
var MAX_RETRIES = 3;
var BASE_DELAY_MS = 500;
async function withRetry(fn, primaryModel, signal) {
  let lastErr = new Error("withRetry: no attempts made");
  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    const isFinalAttempt = attempt === MAX_RETRIES - 1;
    const model = isFinalAttempt ? FALLBACK_MODEL : primaryModel;
    try {
      return await fn(model);
    } catch (err) {
      const status = err.status;
      if (status !== void 0 && status >= 400 && status < 500 && status !== 429) throw err;
      if (signal?.aborted) throw err;
      lastErr = err;
      if (!isFinalAttempt) {
        const delay = BASE_DELAY_MS * 2 ** attempt * (0.8 + Math.random() * 0.4);
        await new Promise((res) => setTimeout(res, delay));
      }
    }
  }
  throw lastErr;
}
function toImageUrl(img) {
  if ("dataUrl" in img) return img.dataUrl;
  if ("url" in img) return img.url;
  const mime = img.mime ?? "image/jpeg";
  return `data:${mime};base64,${img.base64}`;
}
async function callGroqVision(systemPrompt, userText, images, opts) {
  const apiKey = opts.apiKey ?? (typeof process !== "undefined" ? process.env?.GROQ_API_KEY : void 0);
  if (!apiKey) throw new Error("GROQ_API_KEY missing \u2014 pass opts.apiKey or set env var.");
  const userContent = [
    { type: "text", text: userText },
    ...images.map((img) => ({
      type: "image_url",
      image_url: { url: toImageUrl(img) }
    }))
  ];
  const primaryModel = opts.model ?? DEFAULT_MODEL;
  const content = await withRetry(async (model) => {
    const body = {
      model,
      temperature: opts.temperature ?? 0.1,
      max_tokens: opts.maxTokens ?? 2048,
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userContent }
      ]
    };
    const res = await fetch(opts.endpoint ?? DEFAULT_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`
      },
      body: JSON.stringify(body),
      signal: opts.signal
    });
    if (!res.ok) {
      const text2 = await res.text().catch(() => "");
      const err = new Error(`Groq API ${res.status}: ${text2.slice(0, 500)}`);
      err.status = res.status;
      throw err;
    }
    const json = await res.json();
    const text = json.choices?.[0]?.message?.content;
    if (!text) throw new Error("Groq API returned no content.");
    return text;
  }, primaryModel, opts.signal);
  return content;
}
async function callVisionLLM(images, opts) {
  return callGroqVision(
    SYSTEM_PROMPT,
    `Extrais la fiche produit. Renvoie uniquement ce JSON:
${JSON_SCHEMA_HINT}`,
    images,
    opts
  );
}
var IDENTIFY_FOOD_PROMPT = `Tu es un expert en nutrition qui identifie des aliments pr\xEAts \xE0 manger depuis une photo : plats pr\xE9par\xE9s, fruits / l\xE9gumes frais, p\xE2tisseries, plats de restaurant. Il n'y a PAS d'\xE9tiquette ni de code-barres \xE0 lire.

Ta t\xE2che :
1. Identifie l'aliment principal visible.
2. Estime la quantit\xE9 totale visible en grammes (ou ml si liquide). Utilise des rep\xE8res visuels : assiette standard (~26 cm), fourchette, main, bol. Si plusieurs items, estime le total.
3. Estime les macronutriments pour la portion VISIBLE (pas pour 100 g).

R\xE8gles :
- R\xE9ponds UNIQUEMENT en JSON valide, sans markdown.
- D\xE9cimales avec point.
- Si tu n'es pas s\xFBr \xE0 au moins 60 %, renvoie confidence: "low".
- Nom en fran\xE7ais, tel qu'on le dirait normalement.`;
var IDENTIFY_FOOD_SCHEMA = `{
  "name": "string (fran\xE7ais, ex: 'salade c\xE9sar au poulet')",
  "estimated_grams": number,
  "kcal": number,
  "protein_g": number,
  "carbs_g": number,
  "fat_g": number,
  "confidence": "low" | "medium" | "high"
}`;
async function identifyFood(images, opts = {}) {
  const imgs = Array.isArray(images) ? images : [images];
  if (imgs.length === 0) throw new Error("identifyFood: no images provided.");
  if (imgs.length > 4) throw new Error("identifyFood: max 4 images per call.");
  const raw = await callGroqVision(
    IDENTIFY_FOOD_PROMPT,
    `Identifie l'aliment. Renvoie uniquement ce JSON :
${IDENTIFY_FOOD_SCHEMA}`,
    imgs,
    opts
  );
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[identifyFood] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("identifyFood: LLM returned malformed JSON.");
  }
  const name = typeof parsed.name === "string" && parsed.name.trim() ? parsed.name.trim() : "(aliment inconnu)";
  const confidence = parsed.confidence === "high" || parsed.confidence === "low" ? parsed.confidence : "medium";
  return {
    name,
    estimated_grams: Math.max(0, coerceNumber(parsed.estimated_grams)),
    kcal: Math.max(0, coerceNumber(parsed.kcal)),
    protein_g: Math.max(0, coerceNumber(parsed.protein_g)),
    carbs_g: Math.max(0, coerceNumber(parsed.carbs_g)),
    fat_g: Math.max(0, coerceNumber(parsed.fat_g)),
    confidence
  };
}
var IDENTIFY_MULTI_PROMPT = `Tu identifies plusieurs aliments distincts visibles sur une m\xEAme photo (ex. une assiette avec plusieurs composants, un plateau-repas, un buffet). PAS d'\xE9tiquette ni de code-barres.

Ta t\xE2che pour chaque aliment :
1. Nomme l'aliment principal.
2. Estime sa quantit\xE9 visible en grammes (ou ml).
3. Estime ses macros pour la portion visible (pas pour 100 g).

R\xE8gles :
- R\xE9ponds UNIQUEMENT en JSON valide.
- D\xE9cimales avec point.
- Jusqu'\xE0 8 aliments maximum. Ignore les condiments / accompagnements n\xE9gligeables.
- Si un aliment n'est pas clairement identifiable, laisse-le de c\xF4t\xE9.
- Noms en fran\xE7ais.`;
var IDENTIFY_MULTI_SCHEMA = `{
  "items": [
    {
      "name": "string",
      "estimated_grams": number,
      "kcal": number,
      "protein_g": number,
      "carbs_g": number,
      "fat_g": number
    }
  ],
  "confidence": "low" | "medium" | "high"
}`;
async function identifyMultiFood(images, opts = {}) {
  const imgs = Array.isArray(images) ? images : [images];
  if (imgs.length === 0) throw new Error("identifyMultiFood: no images provided.");
  if (imgs.length > 4) throw new Error("identifyMultiFood: max 4 images per call.");
  const raw = await callGroqVision(
    IDENTIFY_MULTI_PROMPT,
    `Identifie tous les aliments distincts. Renvoie uniquement ce JSON :
${IDENTIFY_MULTI_SCHEMA}`,
    imgs,
    opts
  );
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[identifyMultiFood] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("identifyMultiFood: LLM returned malformed JSON.");
  }
  const rawItems = Array.isArray(parsed.items) ? parsed.items : [];
  const items = rawItems.slice(0, 8).filter((it) => !!it && typeof it === "object").map((it) => ({
    name: typeof it.name === "string" && it.name.trim() ? it.name.trim() : "(inconnu)",
    estimated_grams: Math.max(0, coerceNumber(it.estimated_grams)),
    kcal: Math.max(0, coerceNumber(it.kcal)),
    protein_g: Math.max(0, coerceNumber(it.protein_g)),
    carbs_g: Math.max(0, coerceNumber(it.carbs_g)),
    fat_g: Math.max(0, coerceNumber(it.fat_g))
  })).filter((it) => it.name !== "(inconnu)" && (it.kcal > 0 || it.estimated_grams > 0));
  const confidence = parsed.confidence === "high" || parsed.confidence === "low" ? parsed.confidence : "medium";
  return { items, confidence };
}
var IDENTIFY_MENU_PROMPT = `Tu analyses la photo du MENU d'un restaurant (carte, ardoise, menu du jour). Tu lis les plats propos\xE9s et tu les transcris proprement.

Ta t\xE2che :
1. Rep\xE8re chaque plat list\xE9. Ignore cat\xE9gories ("Entr\xE9es"), prix, descriptions marketing.
2. Pour chaque plat, estime des macros plausibles pour UNE PORTION restaurant typique (\u2248 350-600 kcal).
3. Donne une estimation \u2014 c'est une aide \xE0 la d\xE9cision avant commande, pas un tracker pr\xE9cis.

R\xE8gles :
- R\xE9ponds UNIQUEMENT en JSON valide, sans markdown.
- D\xE9cimales avec point.
- Maximum 12 plats. Si ambigu, laisse de c\xF4t\xE9.
- Noms en fran\xE7ais, tels qu'affich\xE9s sur le menu.
- Si la photo ne contient pas de menu lisible, renvoie { "dishes": [] }.`;
var IDENTIFY_MENU_SCHEMA = `{
  "dishes": [
    {
      "name": "string (ex: 'Risotto aux champignons')",
      "kcal": number,
      "protein_g": number,
      "carbs_g": number,
      "fat_g": number
    }
  ]
}`;
async function identifyMenu(images, opts = {}) {
  const imgs = Array.isArray(images) ? images : [images];
  if (imgs.length === 0) throw new Error("identifyMenu: no images provided.");
  if (imgs.length > 4) throw new Error("identifyMenu: max 4 images per call.");
  const raw = await callGroqVision(
    IDENTIFY_MENU_PROMPT,
    `Lis le menu et renvoie uniquement ce JSON :
${IDENTIFY_MENU_SCHEMA}`,
    imgs,
    opts
  );
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[identifyMenu] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("identifyMenu: LLM returned malformed JSON.");
  }
  const rawDishes = Array.isArray(parsed.dishes) ? parsed.dishes : [];
  const dishes = rawDishes.slice(0, 12).filter((d) => !!d && typeof d === "object").map((d) => ({
    name: typeof d.name === "string" && d.name.trim() ? d.name.trim() : "(inconnu)",
    kcal: Math.max(0, coerceNumber(d.kcal)),
    protein_g: Math.max(0, coerceNumber(d.protein_g)),
    carbs_g: Math.max(0, coerceNumber(d.carbs_g)),
    fat_g: Math.max(0, coerceNumber(d.fat_g))
  })).filter((d) => d.name !== "(inconnu)" && d.kcal > 0);
  return { dishes };
}
var IDENTIFY_RECIPE_PROMPT = `Tu analyses la photo d'une carte de recette, d'une page de livre de cuisine, ou d'une recette manuscrite. Tu dois extraire une recette structur\xE9e.

R\xE8gles strictes :
1. Transcris fid\xE8lement le nom du plat, le nombre de personnes (si indiqu\xE9), les ingr\xE9dients (avec leurs quantit\xE9s telles qu'\xE9crites), et les \xE9tapes.
2. Garde les quantit\xE9s dans leur format original ("1 tasse", "200 g", "2 c. \xE0 soupe"). Ne convertis PAS.
3. Si la recette indique ou permet d'inf\xE9rer kcal + macros pour l'ensemble ou par portion, remplis "nutrition". Sinon laisse \xE0 0 \u2014 n'invente pas de valeurs.
4. Si une info n'est PAS visible, renvoie une cha\xEEne vide ou 0 \u2014 n'invente rien.
5. Si c'est pas une recette (carte de restaurant, \xE9tiquette, autre), renvoie des champs vides.
6. R\xE9ponds UNIQUEMENT en JSON valide, pas de markdown.`;
var IDENTIFY_RECIPE_SCHEMA = `{
  "name": "string (ex: 'Salade ni\xE7oise')",
  "servings": number (ex: 4, ou 0 si inconnu),
  "ingredients": ["string", ...],
  "steps": ["string", ...],
  "cook_time_min": number (ex: 30, ou 0 si inconnu),
  "nutrition": {
    "kcal": number,
    "protein_g": number,
    "carbs_g": number,
    "fat_g": number,
    "per_serving": boolean
  }
}`;
async function identifyRecipe(images, opts = {}) {
  const imgs = Array.isArray(images) ? images : [images];
  if (imgs.length === 0) throw new Error("identifyRecipe: no images provided.");
  if (imgs.length > 4) throw new Error("identifyRecipe: max 4 images per call.");
  const raw = await callGroqVision(
    IDENTIFY_RECIPE_PROMPT,
    `Lis la recette et renvoie uniquement ce JSON :
${IDENTIFY_RECIPE_SCHEMA}`,
    imgs,
    opts
  );
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[identifyRecipe] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("identifyRecipe: LLM returned malformed JSON.");
  }
  const ingredients = Array.isArray(parsed.ingredients) ? parsed.ingredients.filter((s) => typeof s === "string" && s.trim().length > 0) : [];
  const steps = Array.isArray(parsed.steps) ? parsed.steps.filter((s) => typeof s === "string" && s.trim().length > 0) : [];
  let nutrition;
  if (parsed.nutrition && typeof parsed.nutrition === "object") {
    const nb = parsed.nutrition;
    const kcal = Math.max(0, coerceNumber(nb.kcal));
    const protein_g = Math.max(0, coerceNumber(nb.protein_g));
    const carbs_g = Math.max(0, coerceNumber(nb.carbs_g));
    const fat_g = Math.max(0, coerceNumber(nb.fat_g));
    if (kcal > 0 || protein_g > 0 || carbs_g > 0 || fat_g > 0) {
      nutrition = {
        kcal,
        protein_g,
        carbs_g,
        fat_g,
        per_serving: nb.per_serving === true
      };
    }
  }
  return {
    name: typeof parsed.name === "string" && parsed.name.trim() ? parsed.name.trim() : "",
    servings: Math.max(0, Math.round(coerceNumber(parsed.servings))),
    ingredients,
    steps,
    cook_time_min: Math.max(0, Math.round(coerceNumber(parsed.cook_time_min))),
    nutrition
  };
}
var _recipeCache = /* @__PURE__ */ new Map();
var SUGGEST_RECIPES_PROMPT = `Tu es un chef fran\xE7ais qui propose des recettes simples et r\xE9alistes \xE0 la maison.

Ta t\xE2che :
1. Propose jusqu'\xE0 3 recettes diff\xE9rentes, toutes r\xE9alisables en 30 minutes ou moins.
2. Consid\xE8re qu'on a les placards classiques (sel, poivre, huile, ail, oignon, beurre, farine, \u0153ufs, herbes courantes).
3. Privil\xE9gie des combinaisons classiques qui respectent le go\xFBt, pas des exp\xE9rimentations.

R\xE8gles :
- R\xE9ponds UNIQUEMENT en JSON valide, sans markdown.
- Noms et \xE9tapes en fran\xE7ais clair et court.
- Maximum 8 ingr\xE9dients par recette, maximum 5 \xE9tapes.
- Si l'ingr\xE9dient est ambigu ou pas comestible, renvoie { "recipes": [] }.`;
var SUGGEST_RECIPES_SCHEMA = `{
  "recipes": [
    {
      "name": "string (ex: 'Salade grecque express')",
      "time_min": number,
      "kcal_estimate": number,
      "ingredients": ["string", ...],
      "steps": ["string", ...]
    }
  ]
}`;
async function suggestRecipes(ingredient, opts = {}) {
  const name = String(ingredient ?? "").trim();
  if (!name) throw new Error("suggestRecipes: ingredient is required.");
  const cacheKey = `recipes:${name.toLowerCase()}`;
  const cached = _recipeCache.get(cacheKey);
  if (cached) return cached;
  const userText = `Ingr\xE9dient principal : ${name}

Propose jusqu'\xE0 3 recettes au format JSON :
${SUGGEST_RECIPES_SCHEMA}`;
  const raw = await callGroqVision(SUGGEST_RECIPES_PROMPT, userText, [], {
    ...opts,
    // Slightly higher temperature than identify to allow some variety in
    // recipe choice — still conservative enough to keep the classics.
    temperature: opts.temperature ?? 0.6
  });
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[suggestRecipes] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("suggestRecipes: LLM returned malformed JSON.");
  }
  const rawList = Array.isArray(parsed.recipes) ? parsed.recipes : [];
  const recipes = rawList.slice(0, 3).filter((r) => !!r && typeof r === "object").map((r) => ({
    name: typeof r.name === "string" && r.name.trim() ? r.name.trim() : "(sans nom)",
    time_min: Math.max(0, coerceNumber(r.time_min)),
    kcal_estimate: Math.max(0, coerceNumber(r.kcal_estimate)),
    ingredients: Array.isArray(r.ingredients) ? r.ingredients.filter((s) => typeof s === "string" && s.length > 0).slice(0, 8) : [],
    steps: Array.isArray(r.steps) ? r.steps.filter((s) => typeof s === "string" && s.length > 0).slice(0, 5) : []
  })).filter((r) => r.name !== "(sans nom)" && r.ingredients.length > 0 && r.steps.length > 0);
  const result = { recipes };
  _recipeCache.set(cacheKey, result);
  return result;
}
var PANTRY_RECIPES_PROMPT = `Tu es un chef fran\xE7ais qui propose des recettes bas\xE9es sur ce que l'utilisateur a d\xE9j\xE0.

Ta t\xE2che :
1. \xC0 partir de la LISTE D'INGR\xC9DIENTS DISPONIBLES, propose jusqu'\xE0 3 recettes simples.
2. Utilise UNIQUEMENT les ingr\xE9dients list\xE9s + les staples classiques de placard (sel, poivre, huile, vinaigre, ail, oignon, beurre, farine, \u0153ufs, herbes courantes).
3. Pr\xE9f\xE8re les recettes qui utilisent PLUSIEURS ingr\xE9dients de la liste \u2014 pas une seule.
4. Maximum 30 minutes par recette.

R\xE8gles :
- R\xE9ponds UNIQUEMENT en JSON valide, sans markdown.
- D\xE9cimales avec point.
- Maximum 8 ingr\xE9dients et 5 \xE9tapes par recette.
- Le champ "ingredients" doit citer chaque item utilis\xE9 (de la liste OU des staples).
- Si la liste est vide ou ne permet pas de cuisiner, renvoie { "recipes": [] }.`;
async function suggestRecipesFromPantry(pantry, opts = {}) {
  const items = (Array.isArray(pantry) ? pantry : []).map((s) => String(s ?? "").trim()).filter(Boolean);
  if (items.length === 0) throw new Error("suggestRecipesFromPantry: pantry is empty.");
  const cacheKey = `pantry:${[...items].sort().join("|").toLowerCase()}`;
  const cached = _recipeCache.get(cacheKey);
  if (cached) return cached;
  const userText = `Ingr\xE9dients disponibles dans le placard / frigo :
- ${items.join("\n- ")}

Propose jusqu'\xE0 3 recettes au format JSON :
${SUGGEST_RECIPES_SCHEMA}`;
  const raw = await callGroqVision(PANTRY_RECIPES_PROMPT, userText, [], {
    ...opts,
    temperature: opts.temperature ?? 0.6
  });
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[suggestRecipesFromPantry] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("suggestRecipesFromPantry: LLM returned malformed JSON.");
  }
  const rawList = Array.isArray(parsed.recipes) ? parsed.recipes : [];
  const recipes = rawList.slice(0, 3).filter((r) => !!r && typeof r === "object").map((r) => ({
    name: typeof r.name === "string" && r.name.trim() ? r.name.trim() : "(sans nom)",
    time_min: Math.max(0, coerceNumber(r.time_min)),
    kcal_estimate: Math.max(0, coerceNumber(r.kcal_estimate)),
    ingredients: Array.isArray(r.ingredients) ? r.ingredients.filter((s) => typeof s === "string" && s.length > 0).slice(0, 8) : [],
    steps: Array.isArray(r.steps) ? r.steps.filter((s) => typeof s === "string" && s.length > 0).slice(0, 5) : []
  })).filter((r) => r.name !== "(sans nom)" && r.ingredients.length > 0 && r.steps.length > 0);
  const result = { recipes };
  _recipeCache.set(cacheKey, result);
  return result;
}
var E_NUMBER_RE = /\bE\s?-?\s?([0-9]{3}[a-z]?)\b/i;
var PERCENTAGE_RE = /([0-9]+(?:[.,][0-9]+)?)\s*%/;
function stripBullet(line) {
  return line.replace(/^[\s]*(?:[-–—•▪·∙*]+|\d+[.)])\s*/, "").trim();
}
function splitIngredients(raw) {
  const parts = [];
  let depth = 0;
  let buf = "";
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (ch === "(" || ch === "[") depth++;
    else if (ch === ")" || ch === "]") depth = Math.max(0, depth - 1);
    if ((ch === "\n" || ch === "\r") && depth === 0) {
      const piece = stripBullet(buf);
      if (piece) parts.push(piece);
      buf = "";
      continue;
    }
    const isDelimiter = (ch === "," || ch === ";") && depth === 0;
    if (isDelimiter) {
      const prev = raw[i - 1];
      const next = raw[i + 1];
      if (ch === "," && prev && /\d/.test(prev) && next && /\d/.test(next)) {
        buf += ch;
        continue;
      }
      const piece = stripBullet(buf);
      if (piece) parts.push(piece);
      buf = "";
    } else {
      buf += ch;
    }
  }
  const last = stripBullet(buf);
  if (last) parts.push(last);
  return parts;
}
function extractPercentage(text) {
  const m = text.match(PERCENTAGE_RE);
  if (!m) return null;
  const n = parseFloat(m[1].replace(",", "."));
  return Number.isFinite(n) && n > 0 && n <= 100 ? n : null;
}
function extractENumber(text) {
  const m = text.match(E_NUMBER_RE);
  if (!m) return null;
  return `E${m[1].toUpperCase()}`;
}
function looksLikeAdditive(text) {
  return !!(extractENumber(text) || additiveByName(text));
}
function additiveByName(text) {
  const n = normalize(text);
  for (const a of ADDITIVES_DB) {
    for (const syn of a.names) {
      if (n.includes(normalize(syn))) return a;
    }
  }
  return null;
}
function isWholeFood2(text) {
  const lower = text.toLowerCase();
  if (NON_WHOLE_FOOD_MARKERS.test(lower)) return false;
  return WHOLE_FOOD_KEYWORDS2.some((kw) => lower.includes(kw));
}
function enrichIngredient(draft) {
  const name = draft.name.trim();
  const pctFromText = extractPercentage(name);
  const percentage = typeof draft.percentage === "number" && draft.percentage > 0 ? draft.percentage : pctFromText;
  let eNumber = draft.e_number?.toUpperCase().replace(/\s/g, "") ?? null;
  const eFromText = extractENumber(name);
  if (eFromText) eNumber = eFromText;
  if (eNumber && !/^E\d{3}[A-Z]?$/.test(eNumber)) eNumber = null;
  let byName = null;
  if (!eNumber) {
    byName = additiveByName(name);
    if (byName) eNumber = byName.e_number;
  }
  let category = draft.category;
  if (!category) {
    if (eNumber || byName || looksLikeAdditive(name)) category = "additive";
    else category = "food";
  }
  const is_whole_food = typeof draft.is_whole_food === "boolean" ? draft.is_whole_food : category === "food" && isWholeFood2(name);
  return {
    name,
    percentage: percentage ?? null,
    e_number: eNumber,
    category,
    is_whole_food
  };
}
var ROLE_PREFIX_RE = /^(conservateurs?|émulsifiants?|emulsifiants?|épaississants?|epaississants?|stabilisants?|acidifiants?|antioxydants?|antioxidants?|colorants?|exhausteurs? de go[uû]t|édulcorants?|edulcorants?|correcteurs? d'acidité|gélifiants?|gelifiants?|humectants?|affermissants?|agents? de traitement|séquestrants?|sequestrants?|anti[- ]?agglom[eé]rants?|arômes?|aromes?)\s*[:：]\s*/i;
function stripRolePrefix(text) {
  return text.replace(ROLE_PREFIX_RE, "").trim();
}
var LESS_THAN_RE = /(?:contient|dont)?\s*(?:moins de|<)\s*\d+\s*%\s*de\s*[:：]?/gi;
function parseIngredientsText(raw) {
  const cleaned = raw.replace(/^\s*ingr[éeè]dients?\s*[:：]\s*/i, "").replace(/\*/g, "").replace(LESS_THAN_RE, " ").trim();
  return splitIngredients(cleaned).map((part) => enrichIngredient({ name: stripRolePrefix(part) }));
}
function coerceNumber(v) {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string") {
    const n = parseFloat(v.replace(",", "."));
    if (Number.isFinite(n)) return n;
  }
  return 0;
}
function coerceNullableNumber(v) {
  if (v == null) return null;
  const n = coerceNumber(v);
  return Number.isFinite(n) ? n : null;
}
function coerceCategory(v) {
  if (typeof v === "string" && CATEGORY_VALUES.includes(v)) {
    return v;
  }
  return "other";
}
function coerceNova(v) {
  const n = typeof v === "number" ? v : parseInt(String(v), 10);
  if (n === 1 || n === 2 || n === 3 || n === 4) return n;
  return 4;
}
function coerceNutrition(raw) {
  const r = raw ?? {};
  return {
    energy_kcal: coerceNumber(r.energy_kcal),
    fat_g: coerceNumber(r.fat_g),
    saturated_fat_g: coerceNumber(r.saturated_fat_g),
    carbs_g: coerceNumber(r.carbs_g),
    sugars_g: coerceNumber(r.sugars_g),
    added_sugars_g: coerceNullableNumber(r.added_sugars_g),
    fiber_g: coerceNumber(r.fiber_g),
    protein_g: coerceNumber(r.protein_g),
    salt_g: coerceNumber(r.salt_g),
    trans_fat_g: coerceNullableNumber(r.trans_fat_g)
  };
}
function coerceIngredients(raw) {
  if (!Array.isArray(raw)) return [];
  return raw.filter((x) => !!x && typeof x === "object").map(
    (x) => enrichIngredient({
      name: String(x.name ?? "").trim(),
      percentage: coerceNullableNumber(x.percentage) ?? void 0,
      e_number: typeof x.e_number === "string" ? x.e_number : null,
      category: x.category === "food" || x.category === "additive" || x.category === "processing_aid" ? x.category : void 0,
      is_whole_food: typeof x.is_whole_food === "boolean" ? x.is_whole_food : void 0
    })
  ).filter((ing) => ing.name.length > 0);
}
function inferNamedOils(ingredients) {
  const hasGeneric = ingredients.some((ing) => {
    const n = ing.name.toLowerCase();
    return GENERIC_OIL_TERMS2.some((g) => n.includes(g));
  });
  return !hasGeneric;
}
function extractJSON(raw) {
  const trimmed = raw.trim();
  const fenceMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenceMatch) return fenceMatch[1].trim();
  const firstBrace = trimmed.indexOf("{");
  const lastBrace = trimmed.lastIndexOf("}");
  if (firstBrace !== -1 && lastBrace > firstBrace) {
    return trimmed.slice(firstBrace, lastBrace + 1);
  }
  return trimmed;
}
function coerceBarcode(v) {
  if (typeof v !== "string") return null;
  const digits = v.replace(/\D/g, "");
  if (digits.length === 8 || digits.length === 12 || digits.length === 13) return digits;
  return null;
}
async function parseLabel(images, opts = {}) {
  const imgs = Array.isArray(images) ? images : [images];
  if (imgs.length === 0) throw new Error("parseLabel: no images provided.");
  if (imgs.length > 4) throw new Error("parseLabel: max 4 images per call.");
  const raw = await callVisionLLM(imgs, opts);
  let parsed;
  try {
    parsed = JSON.parse(extractJSON(raw));
  } catch {
    console.warn("[parseLabel] malformed JSON, first 300 chars:", raw.slice(0, 300));
    throw new Error("parseLabel: LLM returned malformed JSON.");
  }
  const warnings = [];
  const name = typeof parsed.name === "string" && parsed.name.trim() ? parsed.name.trim() : "(unknown product)";
  if (name === "(unknown product)") warnings.push("Product name could not be read.");
  const ingredients = coerceIngredients(parsed.ingredients);
  if (ingredients.length === 0) warnings.push("No ingredients extracted \u2014 scoring will be unreliable.");
  const nutrition = coerceNutrition(parsed.nutrition);
  if (nutrition.energy_kcal === 0 && nutrition.carbs_g === 0 && nutrition.protein_g === 0) {
    warnings.push("Nutrition panel appears blank \u2014 re-shoot the back of pack.");
  }
  const product = {
    name,
    category: coerceCategory(parsed.category),
    nova_class: coerceNova(parsed.nova_class),
    ingredients,
    nutrition,
    weight_g: coerceNullableNumber(parsed.weight_g) ?? void 0,
    origin: typeof parsed.origin === "string" ? parsed.origin : null,
    organic: parsed.organic === true,
    has_health_claims: parsed.has_health_claims === true,
    has_misleading_marketing: parsed.has_misleading_marketing === true,
    named_oils: inferNamedOils(ingredients),
    origin_transparent: typeof parsed.origin === "string" && parsed.origin.trim().length > 0
  };
  if (product.category !== "other" && nutrition.energy_kcal > 0) {
    const ct = CATEGORY_THRESHOLDS[product.category];
    if (ct) {
      const [kcalLow, kcalHigh] = ct.expected_kcal_range;
      if (nutrition.energy_kcal < kcalLow * 0.4 || nutrition.energy_kcal > kcalHigh * 2.2) {
        warnings.push(
          `Category '${product.category}' expects ${kcalLow}\u2013${kcalHigh} kcal/100 g but label reads ${nutrition.energy_kcal} kcal \u2014 category may be wrong, which affects FSA thresholds. Check the product type.`
        );
      }
    }
  }
  const barcode = coerceBarcode(parsed.barcode);
  return { product, raw_llm_output: raw, warnings, barcode };
}

// src/off.ts
var OFF_ENDPOINT = "https://world.openfoodfacts.org/api/v2/product";
var USER_AGENT = "scaneat/0.1 (https://github.com/WW-Andene/Scann-eat)";
var OFF_CACHE_DB = "scanneat";
var OFF_CACHE_VER = 7;
var OFF_CACHE_STORE = "off_cache";
var OFF_CACHE_TTL = 7 * 24 * 60 * 60 * 1e3;
var _isBrowser = typeof indexedDB !== "undefined";
async function offCacheOpen() {
  if (!_isBrowser) return null;
  return new Promise((resolve) => {
    const req = indexedDB.open(OFF_CACHE_DB, OFF_CACHE_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      for (const store of [
        "pending_scans",
        "history",
        "consumption",
        "weight",
        "meal_templates",
        "recipes",
        "activity"
      ]) {
        if (!db.objectStoreNames.contains(store)) {
          if (store === "history") {
            const s = db.createObjectStore(store, { keyPath: "id" });
            s.createIndex("created", "createdAt");
          } else if (store === "consumption" || store === "activity") {
            const s = db.createObjectStore(store, { keyPath: "id" });
            s.createIndex("date", "date");
          } else if (store === "weight") {
            const s = db.createObjectStore(store, { keyPath: "id" });
            s.createIndex("date", "date", { unique: true });
          } else {
            db.createObjectStore(store, { keyPath: "id" });
          }
        }
      }
      if (!db.objectStoreNames.contains(OFF_CACHE_STORE)) {
        const s = db.createObjectStore(OFF_CACHE_STORE, { keyPath: "barcode" });
        s.createIndex("cachedAt", "cachedAt");
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => resolve(null);
  });
}
async function offCacheGet(barcode) {
  const db = await offCacheOpen();
  if (!db) return null;
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(OFF_CACHE_STORE, "readonly");
      const req = tx.objectStore(OFF_CACHE_STORE).get(barcode);
      req.onsuccess = () => {
        db.close();
        const rec = req.result;
        if (!rec) return resolve(null);
        if (Date.now() - rec.cachedAt > OFF_CACHE_TTL) return resolve(null);
        resolve(rec.product);
      };
      req.onerror = () => {
        db.close();
        resolve(null);
      };
    } catch {
      db.close();
      resolve(null);
    }
  });
}
async function offCachePut(barcode, product) {
  const db = await offCacheOpen();
  if (!db) return;
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(OFF_CACHE_STORE, "readwrite");
      tx.objectStore(OFF_CACHE_STORE).put({ barcode, cachedAt: Date.now(), product });
      tx.oncomplete = () => {
        db.close();
        resolve();
      };
      tx.onerror = () => {
        db.close();
        resolve();
      };
    } catch {
      db.close();
      resolve();
    }
  });
}
async function fetchFromOFF(barcode, opts = {}) {
  const digits = barcode.replace(/\D/g, "");
  if (digits.length < 8 || digits.length > 14) return null;
  const cached = await offCacheGet(digits);
  if (cached) return cached;
  const url = `${OFF_ENDPOINT}/${encodeURIComponent(digits)}.json?fields=${FIELDS.join(",")}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), opts.timeoutMs ?? 4e3);
  const signal = opts.signal ? anySignal([opts.signal, controller.signal]) : controller.signal;
  try {
    const res = await fetch(url, {
      headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
      signal
    });
    if (!res.ok) return null;
    const json = await res.json();
    if (json.status !== 1 || !json.product) return null;
    const product = mapOFFProduct(json.product);
    if (product) offCachePut(digits, product).catch(() => {
    });
    return product;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}
var FIELDS = [
  "product_name",
  "product_name_fr",
  "generic_name_fr",
  "brands",
  "categories_tags",
  "ingredients_text_fr",
  "ingredients_text",
  "nova_group",
  "nutriments",
  "labels_tags",
  "origins",
  "countries_tags",
  "quantity",
  "ecoscore_grade",
  "ecoscore_score",
  "nutrition_grades"
];
function num(v) {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string") {
    const n = parseFloat(v.replace(",", "."));
    if (Number.isFinite(n)) return n;
  }
  return 0;
}
function numNullable(v) {
  if (v == null || v === "") return null;
  const n = num(v);
  return Number.isFinite(n) ? n : null;
}
function nutritionFromOFF(n) {
  const nut = n ?? {};
  const gToMg = (v) => num(v) * 1e3;
  const gToUg = (v) => num(v) * 1e6;
  const saltRaw = numNullable(nut["salt_100g"]);
  const sodiumRaw = numNullable(nut["sodium_100g"]);
  const salt_g = saltRaw ?? (sodiumRaw != null ? sodiumRaw * 2.5 : 0);
  const sodium_mg = saltRaw != null && sodiumRaw == null ? saltRaw / 2.5 * 1e3 : gToMg(nut["sodium_100g"]);
  return {
    energy_kcal: num(nut["energy-kcal_100g"] ?? nut["energy_100g"]),
    fat_g: num(nut["fat_100g"]),
    saturated_fat_g: num(nut["saturated-fat_100g"]),
    carbs_g: num(nut["carbohydrates_100g"]),
    sugars_g: num(nut["sugars_100g"]),
    added_sugars_g: numNullable(nut["added-sugars_100g"]),
    fiber_g: num(nut["fiber_100g"]),
    protein_g: num(nut["proteins_100g"]),
    salt_g,
    trans_fat_g: numNullable(nut["trans-fat_100g"]),
    // Minerals
    iron_mg: gToMg(nut["iron_100g"]),
    calcium_mg: gToMg(nut["calcium_100g"]),
    magnesium_mg: gToMg(nut["magnesium_100g"]),
    potassium_mg: gToMg(nut["potassium_100g"]),
    zinc_mg: gToMg(nut["zinc_100g"]),
    sodium_mg,
    // Vitamins
    vit_a_ug: gToUg(nut["vitamin-a_100g"]),
    vit_c_mg: gToMg(nut["vitamin-c_100g"]),
    vit_d_ug: gToUg(nut["vitamin-d_100g"]),
    vit_e_mg: gToMg(nut["vitamin-e_100g"]),
    vit_k_ug: gToUg(nut["vitamin-k_100g"]),
    b1_mg: gToMg(nut["vitamin-b1_100g"]),
    b2_mg: gToMg(nut["vitamin-b2_100g"]),
    b3_mg: gToMg(nut["vitamin-pp_100g"] ?? nut["vitamin-b3_100g"]),
    b6_mg: gToMg(nut["vitamin-b6_100g"]),
    b9_ug: gToUg(nut["vitamin-b9_100g"] ?? nut["folates_100g"]),
    b12_ug: gToUg(nut["vitamin-b12_100g"]),
    // Fat subdivisions + cholesterol
    polyunsaturated_fat_g: num(nut["polyunsaturated-fat_100g"]),
    monounsaturated_fat_g: num(nut["monounsaturated-fat_100g"]),
    omega_3_g: num(nut["omega-3-fat_100g"]),
    omega_6_g: num(nut["omega-6-fat_100g"]),
    cholesterol_mg: gToMg(nut["cholesterol_100g"])
  };
}
var VITAMIN_KEY_PATTERNS = [
  [/^vitamin-a_/, "Vitamin A"],
  [/^vitamin-c_/, "Vitamin C"],
  [/^vitamin-d_/, "Vitamin D"],
  [/^vitamin-e_/, "Vitamin E"],
  [/^vitamin-b\d+_/, "Vitamin B"],
  [/^calcium_/, "Calcium"],
  [/^iron_/, "Iron"],
  [/^magnesium_/, "Magnesium"],
  [/^potassium_/, "Potassium"],
  [/^zinc_/, "Zinc"],
  [/^iodine_/, "Iodine"],
  [/^selenium_/, "Selenium"]
];
function declaredMicronutrients(n) {
  if (!n) return [];
  const seen = /* @__PURE__ */ new Set();
  for (const key of Object.keys(n)) {
    if (!key.endsWith("_100g")) continue;
    for (const [re, label] of VITAMIN_KEY_PATTERNS) {
      if (re.test(key) && num(n[key]) > 0) {
        seen.add(label);
      }
    }
  }
  return Array.from(seen);
}
var CATEGORY_MAP = [
  [/sandwich/i, "sandwich"],
  [/ready[-_]?meal|plat[-_]?cuisin/i, "ready_meal"],
  [/breakfast[-_]?cereal|cereale-petit/i, "breakfast_cereal"],
  [/bread|pain|baguette/i, "bread"],
  [/yogurt|yoghurt|yaourt|skyr|fromage[-_]?blanc|faisselle|quark|petit[-_]?suisse|fermented[-_]?dair/i, "yogurt"],
  [/cheese|fromage/i, "cheese"],
  [/processed[-_]?meat|charcuterie|saucisson|jambon-sec/i, "processed_meat"],
  [/fresh[-_]?meat|viande-fraiche|boucherie/i, "fresh_meat"],
  [/fish|poisson|seafood|crustace/i, "fish"],
  [/confection|candy|chocolate|bonbon|biscuit/i, "snack_sweet"],
  [/chip|crisp|crouton|apero-salee/i, "snack_salty"],
  [/soda|soft[-_]?drink|cola|boisson-gazeuse/i, "beverage_soft"],
  [/juice|jus|nectar/i, "beverage_juice"],
  [/water|eau-minerale|eau-de-source/i, "beverage_water"],
  [/condiment|sauce|mayonnaise|ketchup|mustard|moutarde/i, "condiment"],
  [/oil|huile|matiere-grasse|margarine/i, "oil_fat"]
];
function categoryFromOFF(tags) {
  if (!Array.isArray(tags)) return "other";
  const joined = tags.filter((t) => typeof t === "string").join(" ");
  for (const [re, cat] of CATEGORY_MAP) {
    if (re.test(joined)) return cat;
  }
  return "other";
}
var CATEGORY_TO_OFF_TAG = {
  sandwich: "en:sandwiches",
  ready_meal: "en:prepared-meals",
  breakfast_cereal: "en:breakfast-cereals",
  bread: "en:breads",
  yogurt: "en:yogurts",
  cheese: "en:cheeses",
  processed_meat: "en:processed-meats",
  fresh_meat: "en:meats",
  fish: "en:fishes",
  snack_sweet: "en:sweet-snacks",
  snack_salty: "en:salty-snacks",
  beverage_soft: "en:sodas",
  beverage_juice: "en:fruit-juices",
  beverage_water: "en:waters",
  condiment: "en:condiments",
  oil_fat: "en:fats"
};
function suggestionTagFor(category) {
  return CATEGORY_TO_OFF_TAG[category] ?? null;
}
function novaFromOFF(v) {
  const n = typeof v === "number" ? v : parseInt(String(v ?? ""), 10);
  if (n === 1 || n === 2 || n === 3 || n === 4) return n;
  return 4;
}
function organicFromOFF(labels) {
  if (!Array.isArray(labels)) return false;
  return labels.some(
    (t) => typeof t === "string" && /^en:(organic|bio|eu-organic|ab-agriculture-biologique)$/i.test(t)
  );
}
function mapOFFProduct(p) {
  const name = typeof p.product_name_fr === "string" && p.product_name_fr.trim() || typeof p.product_name === "string" && p.product_name.trim() || "(produit sans nom)";
  const ingredientsText = typeof p.ingredients_text_fr === "string" && p.ingredients_text_fr.trim() || typeof p.ingredients_text === "string" && p.ingredients_text.trim() || "";
  const ingredients = ingredientsText ? parseIngredientsText(ingredientsText) : [];
  const nutrients = p.nutriments;
  const nutrition = nutritionFromOFF(nutrients);
  const declared_micronutrients = declaredMicronutrients(nutrients);
  if (ingredients.length === 0 && nutrition.energy_kcal === 0 && nutrition.protein_g === 0) {
    return null;
  }
  const origin = typeof p.origins === "string" && p.origins.trim() ? p.origins.trim() : null;
  const ecoGrade = typeof p.ecoscore_grade === "string" && /^[a-e]$/i.test(p.ecoscore_grade) ? p.ecoscore_grade.toLowerCase() : null;
  const ecoValue = typeof p.ecoscore_score === "number" && Number.isFinite(p.ecoscore_score) ? p.ecoscore_score : null;
  const nutriscoreGrade = typeof p.nutrition_grades === "string" && /^[a-e]$/i.test(p.nutrition_grades) ? p.nutrition_grades.toLowerCase() : null;
  return {
    name,
    category: categoryFromOFF(p.categories_tags),
    nova_class: novaFromOFF(p.nova_group),
    ingredients,
    nutrition,
    origin,
    organic: organicFromOFF(p.labels_tags),
    has_health_claims: false,
    has_misleading_marketing: false,
    named_oils: !ingredients.some(
      (i) => /huile v[eé]g[eé]tale|vegetable oil/i.test(i.name)
    ),
    origin_transparent: !!origin,
    declared_micronutrients,
    ecoscore_grade: ecoGrade,
    ecoscore_value: ecoValue,
    nutriscore_grade: nutriscoreGrade
  };
}
function anySignal(signals) {
  if ("any" in AbortSignal && typeof AbortSignal.any === "function") {
    return AbortSignal.any(signals);
  }
  const controller = new AbortController();
  for (const s of signals) {
    if (s.aborted) {
      controller.abort(s.reason);
      break;
    }
    s.addEventListener("abort", () => controller.abort(s.reason), { once: true });
  }
  return controller.signal;
}
function isOFFSparse(p) {
  const n = p.nutrition;
  const hasNutrition = n.energy_kcal > 0 || n.protein_g > 0 || n.carbs_g > 0;
  const hasIngredients = p.ingredients.length >= 3;
  const hasCategory = p.category !== "other";
  const MICRO_FIELDS = [
    "iron_mg",
    "calcium_mg",
    "magnesium_mg",
    "potassium_mg",
    "zinc_mg",
    "vit_a_ug",
    "vit_c_mg",
    "vit_d_ug",
    "vit_e_mg",
    "vit_k_ug",
    "b1_mg",
    "b2_mg",
    "b3_mg",
    "b6_mg",
    "b9_ug",
    "b12_ug"
  ];
  const hasMicronutrients = MICRO_FIELDS.some(
    (f) => (n[f] ?? 0) > 0
  );
  return !hasNutrition || !hasIngredients || !hasCategory || !hasMicronutrients;
}
function mergeOFFWithLLM(off, llm) {
  const prefer = (offVal, llmVal, isEmpty) => isEmpty(offVal) ? llmVal : offVal;
  const emptyStr = (s) => !s || typeof s === "string" && s.trim() === "" || s === "(produit sans nom)";
  const emptyNum = (n) => n == null || n === 0;
  const emptyArr = (a) => !Array.isArray(a) || a.length === 0;
  return {
    name: prefer(off.name, llm.name, emptyStr),
    category: off.category !== "other" ? off.category : llm.category,
    nova_class: off.nova_class || llm.nova_class,
    ingredients: emptyArr(off.ingredients) || off.ingredients.length < 3 ? llm.ingredients : off.ingredients,
    // Nutrition merge: required macros use prefer() (OFF over LLM unless
    // OFF is 0/missing); optional micronutrients use ??-cascade (the
    // first non-null/non-undefined wins, so a 0 declared by OFF is
    // preserved over an LLM hallucination). Previously only iron / Ca /
    // vit_D / B12 were merged — every other micronutrient declared on a
    // product was silently dropped on the merge path, including the
    // vitamins (A, C, E, K, B-complex), the minerals (Mg, K, Zn, Na),
    // and the macro subdivisions (poly/mono/ω-3/ω-6/cholesterol). Those
    // fields are read by scoreNutritionalDensity's NRV-15% bonus loop,
    // so dropping them silently lowered scores on multi-vitamin products
    // that fell through the OFF-sparse → LLM merge path.
    nutrition: {
      energy_kcal: prefer(off.nutrition.energy_kcal, llm.nutrition.energy_kcal, emptyNum),
      fat_g: prefer(off.nutrition.fat_g, llm.nutrition.fat_g, emptyNum),
      saturated_fat_g: prefer(off.nutrition.saturated_fat_g, llm.nutrition.saturated_fat_g, emptyNum),
      carbs_g: prefer(off.nutrition.carbs_g, llm.nutrition.carbs_g, emptyNum),
      sugars_g: prefer(off.nutrition.sugars_g, llm.nutrition.sugars_g, emptyNum),
      added_sugars_g: off.nutrition.added_sugars_g ?? llm.nutrition.added_sugars_g ?? null,
      fiber_g: prefer(off.nutrition.fiber_g, llm.nutrition.fiber_g, emptyNum),
      protein_g: prefer(off.nutrition.protein_g, llm.nutrition.protein_g, emptyNum),
      salt_g: prefer(off.nutrition.salt_g, llm.nutrition.salt_g, emptyNum),
      trans_fat_g: off.nutrition.trans_fat_g ?? llm.nutrition.trans_fat_g ?? null,
      // Minerals
      iron_mg: off.nutrition.iron_mg ?? llm.nutrition.iron_mg,
      calcium_mg: off.nutrition.calcium_mg ?? llm.nutrition.calcium_mg,
      magnesium_mg: off.nutrition.magnesium_mg ?? llm.nutrition.magnesium_mg,
      potassium_mg: off.nutrition.potassium_mg ?? llm.nutrition.potassium_mg,
      zinc_mg: off.nutrition.zinc_mg ?? llm.nutrition.zinc_mg,
      sodium_mg: off.nutrition.sodium_mg ?? llm.nutrition.sodium_mg,
      // Vitamins
      vit_a_ug: off.nutrition.vit_a_ug ?? llm.nutrition.vit_a_ug,
      vit_c_mg: off.nutrition.vit_c_mg ?? llm.nutrition.vit_c_mg,
      vit_d_ug: off.nutrition.vit_d_ug ?? llm.nutrition.vit_d_ug,
      vit_e_mg: off.nutrition.vit_e_mg ?? llm.nutrition.vit_e_mg,
      vit_k_ug: off.nutrition.vit_k_ug ?? llm.nutrition.vit_k_ug,
      b1_mg: off.nutrition.b1_mg ?? llm.nutrition.b1_mg,
      b2_mg: off.nutrition.b2_mg ?? llm.nutrition.b2_mg,
      b3_mg: off.nutrition.b3_mg ?? llm.nutrition.b3_mg,
      b6_mg: off.nutrition.b6_mg ?? llm.nutrition.b6_mg,
      b9_ug: off.nutrition.b9_ug ?? llm.nutrition.b9_ug,
      b12_ug: off.nutrition.b12_ug ?? llm.nutrition.b12_ug,
      // Macro subdivisions
      polyunsaturated_fat_g: off.nutrition.polyunsaturated_fat_g ?? llm.nutrition.polyunsaturated_fat_g,
      monounsaturated_fat_g: off.nutrition.monounsaturated_fat_g ?? llm.nutrition.monounsaturated_fat_g,
      omega_3_g: off.nutrition.omega_3_g ?? llm.nutrition.omega_3_g,
      omega_6_g: off.nutrition.omega_6_g ?? llm.nutrition.omega_6_g,
      cholesterol_mg: off.nutrition.cholesterol_mg ?? llm.nutrition.cholesterol_mg
    },
    weight_g: off.weight_g ?? llm.weight_g,
    origin: off.origin ?? llm.origin ?? null,
    organic: off.organic || llm.organic,
    has_health_claims: off.has_health_claims || llm.has_health_claims,
    has_misleading_marketing: off.has_misleading_marketing || llm.has_misleading_marketing,
    named_oils: off.named_oils ?? llm.named_oils,
    origin_transparent: off.origin_transparent || llm.origin_transparent,
    // Union of declared micronutrients across both sources, deduped.
    // Previously OFF-only — products where the LLM read additional
    // declared nutrients off the panel had those dropped on merge.
    declared_micronutrients: Array.from(/* @__PURE__ */ new Set([
      ...off.declared_micronutrients ?? [],
      ...llm.declared_micronutrients ?? []
    ])),
    // Eco + nutriscore fields are only ever known from OFF; LLM doesn't see them.
    ecoscore_grade: off.ecoscore_grade,
    ecoscore_value: off.ecoscore_value,
    nutriscore_grade: off.nutriscore_grade
  };
}
var OFF_SEARCH_ENDPOINT = "https://world.openfoodfacts.org/cgi/search.pl";
async function searchOFFByCategory(tags, opts = {}) {
  if (!Array.isArray(tags) || tags.length === 0) return [];
  const primaryTag = tags[0];
  const params = new URLSearchParams({
    action: "process",
    tagtype_0: "categories",
    tag_contains_0: "contains",
    tag_0: primaryTag,
    sort_by: "popularity_key",
    // most common first
    page_size: String(opts.pageSize ?? 20),
    fields: FIELDS.join(","),
    json: "1"
  });
  const url = `${OFF_SEARCH_ENDPOINT}?${params.toString()}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), opts.timeoutMs ?? 5e3);
  const signal = opts.signal ? anySignal([opts.signal, controller.signal]) : controller.signal;
  try {
    const res = await fetch(url, {
      headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
      signal
    });
    if (!res.ok) return [];
    const json = await res.json();
    const products = Array.isArray(json.products) ? json.products : [];
    const mapped = [];
    for (const raw of products) {
      const p = mapOFFProduct(raw);
      if (p) mapped.push(p);
    }
    return mapped;
  } catch {
    return [];
  } finally {
    clearTimeout(timeout);
  }
}
function rankAlternatives(reference, candidates, opts = {}) {
  const max = opts.max ?? 3;
  const filter = opts.dietFilter ?? (() => true);
  const refAudit = scoreProduct(reference);
  const ranked = candidates.filter((c) => c.name && c.name !== reference.name).filter(filter).map((product) => ({ product, audit: scoreProduct(product) })).filter((a) => a.audit.score > refAudit.score).sort((a, b) => b.audit.score - a.audit.score);
  return ranked.slice(0, max);
}
var CONFLICT_THRESHOLDS = {
  sugars_g: { label: "Sugars", relativeThreshold: 0.2, absoluteFloor: 2, unit: "g", severity: "medium" },
  saturated_fat_g: { label: "Sat fat", relativeThreshold: 0.15, absoluteFloor: 1, unit: "g", severity: "high" },
  salt_g: { label: "Salt", relativeThreshold: 0.15, absoluteFloor: 0.3, unit: "g", severity: "high" },
  protein_g: { label: "Protein", relativeThreshold: 0.25, absoluteFloor: 3, unit: "g", severity: "medium" },
  trans_fat_g: { label: "Trans fat", relativeThreshold: 0.1, absoluteFloor: 0.05, unit: "g", severity: "high" },
  energy_kcal: { label: "Energy", relativeThreshold: 0.2, absoluteFloor: 30, unit: "kcal", severity: "high" }
};
function detectSourceConflicts(off, llm) {
  const conflicts = [];
  for (const [field, cfg] of Object.entries(CONFLICT_THRESHOLDS)) {
    const a = Number(off.nutrition[field] ?? 0);
    const b = Number(llm.nutrition[field] ?? 0);
    const peak = Math.max(a, b);
    if (a <= 0 || b <= 0 || peak < cfg.absoluteFloor) continue;
    const diff = Math.abs(a - b) / peak;
    if (diff > cfg.relativeThreshold) {
      conflicts.push({
        message: `${cfg.label}: OFF ${a}${cfg.unit} vs photo ${b}${cfg.unit} (${Math.round(diff * 100)}% difference, threshold ${Math.round(cfg.relativeThreshold * 100)}% \u2014 possible reformulation)`,
        severity: cfg.severity
      });
    }
  }
  return conflicts;
}
export {
  ADDITIVES_DB,
  ENGINE_VERSION,
  countTier1Additives,
  detectSourceConflicts,
  fetchFromOFF,
  findAdditive,
  getThresholds,
  identifyFood,
  identifyMenu,
  identifyMultiFood,
  identifyRecipe,
  inferCategoryFromName,
  isOFFSparse,
  mergeOFFWithLLM,
  normalize,
  parseIngredientsText,
  parseLabel,
  rankAlternatives,
  scoreProduct,
  searchOFFByCategory,
  suggestRecipes,
  suggestRecipesFromPantry,
  suggestionTagFor
};
