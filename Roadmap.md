# Roadmap — Audit approfondi Scan'eat (2026-10-13, passe 2)

Périmètre : `scan-eat-android/app` (~630 fichiers Kotlin, ~80 k lignes, Room v40,
Compose + Hilt + Glance + WorkManager + Health Connect, versionName 1.1.0, minSdk 26 / target 35).

Cette passe **vérifie dans le code** chacune des affirmations de la passe 1 (rédigée à
partir de la structure du dépôt). Plusieurs étaient fausses : la section 0 les corrige, pour
que ce document ne contienne aucune information erronée (philosophie du README).

---

## 0. Corrections de la passe 1 (ce qui existe déjà)

| Affirmation passe 1 | Réalité vérifiée |
|---|---|
| « Créer un mode multi-profils » | **Existe** : `prefs.activeProfileId`, `profileId` dans ~980 usages, tous les repos sont filtrés par profil. |
| « Health Connect : passer en bidirectionnel » | **Existe** : écriture poids, hydratation, exercice, calories, nutrition (`HealthConnect*Ext.kt`, manifeste). |
| « Widget : passer à Glance » | **Existe** : `ScanWidget` + `TodayWidget` sont des `GlanceAppWidget`. |
| « Rapport médecin PDF » | **Existe** : `PdfReportRepository`. |
| « Sauvegarde chiffrée » | **Existe** : `BackupEncryption`, `BackupPassphraseCipher` (+ test). |
| « Alternatives meilleures » | **Partiel** : `findBetterAlternative` cherche uniquement dans l'**historique de l'utilisateur** (`ScanHistoryQueries`), pas dans Open Food Facts. |
| « Comparateur de produits » | **Existe** : `CompareScansDialog` (historique) + `ComparisonCard` sur le résultat. |
| « Rappels produits (Recall) » | **Partiel** : vérification RappelConso au scan et cache dans le garde-manger ; **aucune vérification périodique** (cf. C3). |
| « Pas de source dans les bases » | **Nuancé** : sources présentes en **commentaires** (ex. en-tête de `FoodDb.kt` : CIQUAL 2020, DOI), mais aucune donnée structurée par entrée, et rien n'est visible pour l'utilisateur. |

---

# Audit approfondi du moteur de score (passe 6)

Périmètre : `domain/engine/scoring/` (35 fichiers, ~5 200 lignes), la base d'additifs, `OffMapper` /
`OffCategoryMapping` / `OffIngredientParsing` (données d'entrée du score), `DailyTargets` /
`BmrCalculations` et les ajustements personnels.

**Méthode.** J'ai lu les piliers ligne à ligne. Les faits ont été comparés aux textes de référence (règlements UE,
EFSA, CIRC, OMS). Surtout, les fonctions de catégorisation ont été **portées à l'identique en
Python et exécutées sur de vrais produits Open Food Facts** : les 10 produits les plus scannés en
France pour 39 catégories, et 300 produits pour la couverture des additifs. Les scripts sont dans le
scratchpad de la session, et les chiffres ci-dessous en sont la sortie brute.

Légende : ✅ vérifié par exécution sur données réelles · 📖 vérifié par lecture du code et des sources ·
⚠️ effet latent (le chemin existe dans le code, mais les données qui le déclenchent sont rares en France).

## Architecture rappelée

Score de 0 à 100 = Transformation (20) + Densité nutritionnelle (25) + Nutriments négatifs (25) + Additifs (15)
+ Intégrité des ingrédients (15), auquel s'ajoutent les bonus (plafonnés à 10) et les pénalités globales, puis un **veto** qui plafonne la note.
Les seuils dépendent de la **catégorie** du produit (`CategoryThresholds`). La catégorie est donc la clé de voûte :
une catégorie fausse rend faux tous les piliers à la fois.

## Ce qui est juste (vérifié)

- Valeurs nutritionnelles de référence (VNR) de l'annexe XIII du règlement 1169/2011 (fer 14, calcium 800, vitamine D 5 µg, vitamine C 80, vitamine A 800, vitamine E 12, B12 2,5,
  magnésium 375, potassium 2000, zinc 10), et seuil « quantité significative » de 15 % 📖.
- Références EFSA dans `DailyTargets` : fer 11/16 mg, zinc 9,4/7,5, vitamine C 110/95, vitamine A 750/650, calcium 950,
  vitamine D 15 µg, B12 4 µg, magnésium 350/300, potassium 3500, folates 330 ; suppléments de grossesse +70/+260/+500 kcal
  et +1/+9/+28 g de protéines 📖.
- Sucres libres 10 % / 5 % (OMS), graisses saturées 10 %, facteur sodium → sel de 2,5, alcool classé CIRC groupe 1 📖.
- Statuts réglementaires : E171 interdit dans l'UE (règlement 2022/63), E127 interdit par la FDA (janvier 2025), E924, E216 et E217 non autorisés dans l'UE 📖.
- Les 14 allergènes de l'annexe II, avec la gestion des traces ; régime végan (E120, E542, E901, E904, lanoline…) 📖.

---

## Findings, par ordre de travail

### S1. La catégorisation Open Food Facts est massivement fausse 🔴 ✅
`mapCategory` concatène **toute la hiérarchie** `categories_tags` (parents compris) et cherche des
**sous-chaînes** dans un ordre fixe. Or les tags parents d'Open Food Facts sont génériques :
`en:plant-based-foods-and-beverages` (presque tous les produits végétaux, et qui contient « beverage »),
`en:cereals-and-potatoes` (pain, riz, pâtes, farines, chips : contient « cereal »), `en:prepared-meats`
(jambons : pas « processed-meat », mais « meat »), `en:breaded-fish…` (contient « bread »).
Il manque aussi des catégories entières : `GRAIN`, `FRESH_PRODUCE`, `EGG` et `ICE_CREAM` ne sont **jamais** produites à
partir des tags, et le lait, la crème et le sucre retombent en `OTHER`.

Catégorie finale (après le rattrapage par le nom de `resolveCategory`), pour les 10 produits les plus scannés en France :

| Catégorie OFF | Catégorie attribuée par l'app |
|---|---|
| Pains | **BREAKFAST_CEREAL 10/10** |
| Riz | **BREAKFAST_CEREAL 10/10** |
| Quinoa | **BREAKFAST_CEREAL 10/10** |
| Pâtes | BREAKFAST_CEREAL 8, CHEESE 1, GRAIN 1 |
| Chips | **BREAKFAST_CEREAL 8**, SNACK_SWEET 2 |
| Farines | BREAKFAST_CEREAL 8, BEVERAGE_SOFT 1, NUTS_SEEDS 1 |
| Lentilles / légumineuses | **NUTS_SEEDS 9/10** |
| Pois chiches | **NUTS_SEEDS 10/10** |
| Jambons | **FRESH_MEAT 10/10** |
| Poissons panés | **BREAD 10/10** |
| Lasagnes | FRESH_MEAT 5, CHEESE 3, FISH 2 |
| Cafés | **BEVERAGE_SOFT 10/10** |
| Thés | BEVERAGE_SOFT 9 |
| Margarines | **BEVERAGE_SOFT 8/10** |
| Huiles d'olive | OIL_FAT 5, **SNACK_SALTY 3**, BEVERAGE_SOFT 2 |
| Légumes en conserve | BEVERAGE_SOFT 5, FRESH_PRODUCE 3, NUTS_SEEDS 2 |
| Légumes surgelés | BEVERAGE_SOFT 6, FRESH_PRODUCE 4 |
| Fruits secs | NUTS_SEEDS 6, BEVERAGE_SOFT 2, BEVERAGE_WATER 1, SNACK_SALTY 1 |
| Laits végétaux | NUTS_SEEDS 5, BREAKFAST_CEREAL 4, BEVERAGE_SOFT 1 |
| Laits | OTHER 9, BEVERAGE_SOFT 1 |
| Aliments pour bébé | SNACK_SWEET 3, BREAKFAST_CEREAL 2 |
| Correctes : yaourts, beurres, miels, chocolats noirs, viandes fraîches, thon, saumon fumé, tofu, bières, eaux minérales, flocons d'avoine, œufs (9/10), compotes (9/10) |

**Conséquences concrètes** (seuils de `CategoryThresholds`) :
- Margarine ou café notés comme une **boisson** : fourchette attendue 0-50 kcal, donc pénalité « hors norme ». Les points de protéines et de fibres
  sont jugés impossibles à obtenir.
- Lentilles préparées notées comme des **noix** : protéines attendues ≥ 12/18/25 g et 480-650 kcal, donc 0/7 en protéines et
  « énergie hors norme ».
- Jambons en `FRESH_MEAT` : le veto combiné « nitrites + sel + amidon » (réservé à `PROCESSED_MEAT`) ne se déclenche
  **jamais** sur un jambon, et les seuils de sel sont ceux d'une viande crue.
- Huile d'olive en `SNACK_SALTY` : -8 pour la densité énergétique (seule `OIL_FAT` en est exemptée) et graisses saturées jugées
  sur les seuils par défaut.

Les tests existants (`OffCategoryMappingTest`) passent parce qu'ils n'utilisent que des **tags feuilles isolés**
(`cat("en:cheeses")`), jamais la hiérarchie réelle renvoyée par l'API.
→ **Correction** : choisir la catégorie à partir du tag **le plus spécifique** (les tags d'Open Food Facts vont du général au
particulier) avec une table exacte `tag → catégorie` (`en:breads`, `en:rices`, `en:pastas`, `en:legumes`,
`en:hams`, `en:prepared-meats`, `en:milks`, `en:plant-based-milks`, `en:coffees`, `en:margarines`,
`en:fresh-vegetables`…) ; ignorer les tags « parapluie » (`plant-based-foods-and-beverages`,
`cereals-and-potatoes`, `snacks`) ; ajouter les catégories manquantes (légumineuses, lait, fruits secs,
café/thé, alimentation infantile). Valider avec un jeu de vérité de vrais produits (cf. S15).

### S2. NOVA : « inconnu » devient « ultra-transformé », et le vrai NOVA 4 d'Open Food Facts est écrasé 🔴 📖
- `OffMapper.kt:147` : `novaClass = NovaClass.fromInt(off.novaGroup ?: 4)`. Faute de NOVA chez Open Food Facts, le
  produit est **présumé NOVA 4**. Sans liste d'ingrédients et avec un nom hors liste « produit frais », il reste
  en NOVA 4 : base de 6/20 au lieu de 20, déduction MAJOR et drapeau rouge « classe NOVA 4 » présentés comme un fait.
- Comme la valeur 4 « par défaut » est indiscernable d'un NOVA 4 **réellement calculé** par Open Food Facts,
  `scoreProcessing` rétrograde aussi ce dernier dès que sa propre heuristique est moins sévère. Or cette heuristique est bien
  plus étroite que la définition NOVA (Monteiro) : `COSMETIC_ADDITIVE_CATEGORIES` ne contient que
  colorants, exhausteurs et édulcorants, **pas** les émulsifiants, épaississants, gélifiants ou agents d'enrobage. Et
  `UPF_MARKER_PATTERNS` (5 motifs) ignore le sirop de glucose(-fructose), le sucre inverti, les huiles hydrogénées,
  le lactosérum, la caséine, le lactose et le gluten ajouté. Une brioche avec E471 et sirop de glucose, classée NOVA 4 par Open Food Facts,
  devient NOVA 3 dans l'app.
→ Rendre `novaClass` nullable (inconnu ≠ 4). Ne **jamais** rétrograder un NOVA fourni par Open Food Facts. N'inférer que
  quand il manque, avec la liste de marqueurs NOVA complète, et afficher « NOVA estimé ».

### S3. L'absence de données produit des affirmations positives 🔴 📖
Un produit **sans liste d'ingrédients** (fréquent : fiches Open Food Facts incomplètes, photo de face seulement) reçoit :
- `IngredientIntegrityPillar` : **+2 « Aucun sucre caché »**, et 3/3 en « ingrédients reconnaissables »
  (`recogRatio = 1.0` quand la liste est vide) ;
- `HealthConditionGiAdjustments` : **+2 « Aucun déclencheur riche en FODMAP détecté — adapté au SII »** ;
- `checkDiet` : considéré **conforme** au régime (végan, halal, sans gluten…), sans aucun avertissement. Seuls les allergènes
  ont un bandeau « non vérifié » ;
- à l'inverse, la transformation est présumée NOVA 4 (S2).
Le moteur conclut donc dans les deux sens à partir de rien.
→ Introduire un état « données insuffisantes » propagé à chaque règle (pas de bonus ni de malus fondés sur
  l'absence de données), et un **indice de confiance** affiché sur la note (lié à la complétude, ancien C6 de la passe 1).

### S4. Messages « sourcés » inexacts 🟠 📖
| Où | Affiché | Réalité |
|---|---|---|
| `AdditivesTier1` E249/E250 | « IARC Group 1 » | Le **CIRC classe la viande transformée en groupe 1**. Le nitrite ingéré dans des conditions de nitrosation endogène est en **groupe 2A** (monographie vol. 94). |
| `checkVeto` nitrites | « conservateurs nitrités (E249/E250) » | Le veto ne détecte que E249/E250 : un produit traité **aux nitrates** (E251/E252, pourtant en niveau 1 dans la base) échappe au veto. |
| `PALM_OIL_PATTERN` | « Huile de palme ou dérivé : huile de coprah » | Le **coprah est la noix de coco**, pas le palmier. |
| `computeGlobalBonuses` | « Contient des éléments fermentés / probiotiques » | Fermenté ≠ probiotique (pain, produits pasteurisés) ; « probiotique » n'est pas une allégation autorisée dans l'UE. |
| Veto + pilier trans | « graisses trans **industrielles** — aucun seuil sûr » | Déclenché par toute valeur `trans-fat_100g` > 0,1 g, y compris les trans **naturelles** des ruminants (beurre, fromage, agneau). Le règlement UE 2019/649 ne vise que les trans industrielles. ⚠️ Rarement déclarées en France (0/40 beurres), mais présentes sur les étiquettes importées ou lues par OCR. Et **double peine** : -10 dans le pilier, plus un veto plafonnant à 39. |
| Caféine > 300 mg/100 g | « une portion normale dépasserait les ~200 mg de l'EFSA » | Calcul par 100 g appliqué aux **poudres** : café soluble (4 g/100 g relevé sur un produit réel), thé en feuilles. Une tasse de soluble (environ 2 g) contient environ 80 mg. ⚠️ Rarement déclaré, mais faux quand ça l'est. |
→ Corriger chaque texte, et faire relire toute phrase qui cite un organisme (EFSA, CIRC, OMS) par rapport à la source exacte (cf. A1).

### S5. Produits secs et sucres intrinsèques jugés comme des aliments transformés 🟠 📖
- `scoreNegativeNutrients` pénalise la **densité énergétique** de -2 à -8 dès 200 kcal/100 g, sur la valeur
  **telle que vendue**. Pâtes et riz secs (environ 350 kcal), légumineuses sèches, flocons d'avoine, cacao, lait en
  poudre et soupes déshydratées sont donc pénalisés pour une énergie qu'ils n'ont pas une fois cuits ou reconstitués (×2,5 à ×3 d'eau).
  Open Food Facts fournit des valeurs `*_prepared_100g` qui ne sont pas utilisées.
- Les sucres : sans « sucres ajoutés », ce sont les **sucres totaux** qui sont jugés. Les fruits frais (`FRESH_PRODUCE`,
  seuils 8/14/20/30) perdent des points pour leurs sucres **intrinsèques** (raisin, banane à 12-16 g), que la définition
  OMS des sucres libres exclut explicitement. ⚠️ Atténué quand Open Food Facts estime `added-sugars_100g` (présent sur 36 à 40 fiches sur 40 pour les fruits secs testés).
→ Utiliser les valeurs « préparé » quand elles existent ; à défaut, exempter de la densité énergétique les catégories
  « à cuire ou reconstituer ». Exempter les sucres intrinsèques des fruits et légumes non transformés et des produits laitiers nature.

### S6. Additifs : sous-indices non reconnus et double comptage 🟠 ✅
Mesure sur les 300 produits les plus scannés en France : **72,7 % des occurrences d'additifs seulement sont reconnues**
par numéro E. Sur 84 additifs distincts, 37 sont inconnus. Les plus fréquents parmi les inconnus sont E322i (48 occurrences), E500ii (10), E267, E392, E461,
E503ii, E336, E14xx, E160ai, E476, E160b, E450i, E472e, E960a. La cause principale : `additiveTagsToIngredients`
produit `E322I` / `E500I` (regex `\d{3,4}[a-z]?`), alors que la base ne connaît que `E322` / `E500`.
Effet secondaire : la lécithine est déjà présente dans le texte (« lécithine de soja ») **et** ajoutée une seconde fois
via le tag `E322I`. Cela gonfle le **nombre d'additifs** (seuil NOVA « ≤ 2 additifs ») et le **nombre d'ingrédients**
(pénalité « > 10 ingrédients »).
→ Normaliser les numéros (retirer les sous-indices romains i/ii/iii et les lettres de variante en repli : E472e → E472),
  dédoublonner par additif résolu et non par chaîne, et compléter la base (E476, E461, E392, E160b, E336…).

### S7. Classement des additifs incohérent 🟠 📖
- Niveau 3 « préoccupation mineure » (-1 chacun) pour la **vitamine C (E300)**, la **riboflavine/vitamine B2 (E101)**, la pectine,
  la lécithine, le bêta-carotène, le carbonate de calcium, les tocophérols (vitamine E) et les acides citrique et lactique. Aucun avis EFSA ne
  soutient une « préoccupation » pour ces substances.
- Sulfites (E220-E228) en **niveau 1 « préoccupation sérieuse »** pour tout le monde : c'est un **allergène**, déjà traité
  par le profil (double traitement), et non une toxicité générale.
- Le CMC (E466) est en niveau 1 alors que les carraghénanes (E407), sur lesquels pèsent des préoccupations comparables (réévaluation EFSA 2018, données
  manquantes), sont en niveau 3.
→ Ajouter un niveau « sans préoccupation » à 0 point ; ne pénaliser les sulfites que via le profil ; documenter les critères de
  chaque niveau avec l'avis EFSA correspondant.

### S8. Bonus et malus sans fondement sourcé 🟠 📖
- **Bio +2** dans un score **nutritionnel** : l'EFSA et l'ANSES ne relèvent pas de différence nutritionnelle significative.
- **Allégations de santé -3** : pénalise aussi les allégations **autorisées** par le règlement 1924/2006.
- **Origine +2** pour toute origine déclarée, y compris « UE et non UE ».
- **Huiles nommées +3** : les catégories sans huile perdent structurellement 3 points (le rééchelonnement ne couvre que 7 catégories).
- **Enrichissement récompensé** : le bonus micronutriments (jusqu'à +8) ne distingue pas l'intrinsèque de l'ajouté. Les céréales
  sucrées et chocolats en poudre enrichis gagnent donc des points.
- Seuil de 15 % des VNR appliqué aux **boissons**, alors que l'annexe XIII prévoit **7,5 %** par 100 ml.
→ Retirer ou sourcer chaque bonus ; ignorer les micronutriments ajoutés (listés dans les ingrédients) ; appliquer 7,5 % aux boissons.

### S9. Profil : formules hors domaine de validité 🟠 📖
✅ **Partiellement fait** : folates de grossesse sourcés EFSA 2014 (600 µg), sel en cas d'hypertension aligné sur ESH 2023 (< 5 g), cible de fibres EFSA (25 g) pour tous les régimes. Restent : équations pédiatriques, état poussée/rémission MICI.

- **Mifflin-St Jeor appliqué aux mineurs** (`clampedAge` de 1 à 110) : formule validée chez l'adulte. L'EFSA utilise des
  équations spécifiques intégrant la croissance pour les 1-17 ans. Le calcium de 950 mg vaut pour les adultes, alors que l'EFSA recommande 1150 mg chez les 11-17 ans.
- **Folates de grossesse non relevés** : la cible reste à 330 µg, alors que l'EFSA recommande 600 µg pendant la grossesse (et une supplémentation de 400 µg d'acide
  folique en période périconceptionnelle).
- **Sel 3 g/jour en cas d'hypertension** : non sourcé. Les recommandations européennes (ESH 2023) et la HAS visent moins de 5 à 6 g.
- **Régime carnivore → cible de fibres 0 g** : l'application valide une cible contraire à l'EFSA (25 g).
- **Crohn/MICI** : les fibres sont pénalisées en permanence, alors que la recommandation citée ne vaut **qu'en poussée**.
→ Bornes d'âge explicites (pas de calcul adulte avant 18 ans) ; ajouter les folates de grossesse ; sourcer ou aligner le sel ;
  afficher la cible EFSA de fibres même pour un régime restrictif ; ajouter un état « poussée / rémission » pour les MICI.

### S10. Incohérences internes du calcul 🟡 📖
✅ **Partiellement fait** : bonus « Peu transformé » compté ; bonus oméga-3 limité à la noix (plus coco, cajou, muscade, pécan, macadamia, Brésil). Restent : pénalités en double (pilier + veto).

- `scoreProcessing` : le bonus « +2 Peu transformé » est **affiché** mais jamais ajouté au score.
- `scoreNutritionalDensity` : la déduction « Aucun apport… » vaut 0 point mais est classée MAJOR. Elle compte donc dans
  `severeFlagCount`, déclenche une pénalité globale et apparaît en drapeau rouge.
- Bonus oméga-3 : `\bnoix\b` détecte la noix de coco, de cajou ou de muscade, qui ne sont pas des sources d'oméga-3.
- Pénalités en double : trans (pilier + veto), alcool (pilier + veto), caféine (pilier + veto), additifs de niveau 1 (pilier
  + veto + pénalité globale « signalements majeurs »).
→ Une règle = un effet ; tests de cohérence « bonus affiché = points comptés ».

### S11. Grades non calibrés 🟡 📖
Sept grades (A+ à F) à seuils linéaires (85/70/55/40/25/10), sans calibration publiée contre une référence (Nutri-Score,
panel de diététiciens) ni distribution observée sur un panel réel. Le même produit peut afficher un Nutri-Score A et un
« C » Scan'eat sans explication.
→ Calibrer sur le jeu de vérité (S15), publier la méthode, et expliquer les divergences avec le Nutri-Score sur l'écran de résultat.

### S12. Sources non européennes là où un équivalent existe 🟡 📖
SII : Monash (Australie). MICI : Crohn's & Colitis Foundation (États-Unis) et NHS (Royaume-Uni). Les équivalents européens existent :
**ECCO** pour les MICI, **SNFGE / HAS** pour le SII. C'est contraire à la règle du README (« sources européennes préférées »).

### S13. Commentaires de parité obsolètes 🟡 📖
« Mirrors scoreProduct() from scoring-engine.ts exactly », « same as JS Fix #7 », « the server mirrors this
exact function » : il n'y a **aucun** fichier TypeScript ni serveur dans le dépôt (le README dit « ni serveur »). D'après la règle du projet, toute
divergence entre un commentaire et le comportement réel compte comme un bug.

### S14. Version du moteur non liée aux scores enregistrés 🟡 📖
`ENGINE_VERSION = "2.4.0"` est enregistrée dans l'audit, mais rien ne recalcule ni ne signale un ancien score après un changement de
règles. Après les corrections S1 à S10, l'historique affichera des notes produites par un moteur faux.
→ Recalculer à la volée (les données produit sont stockées) ou marquer « note calculée avec une version antérieure ».

### S15. Jeu de vérité pour la véracité du score 🔴 (prérequis transverse)
Il ne s'agit pas d'un test de régression, mais d'un **outil de véracité** : environ 200 vrais produits Open Food Facts (hiérarchie de tags complète,
nutriments, ingrédients), avec pour chacun la catégorie attendue, le NOVA d'Open Food Facts et un ordre de grandeur de note validé
par une personne compétente. C'est le seul moyen de savoir si le score dit vrai, et il aurait détecté S1 immédiatement.
La simulation Python de cette passe (fonctions portées, 39 catégories) en est une première ébauche, réutilisable.

## Ordre de travail recommandé pour le moteur

1. **S15** (jeu de vérité) → 2. **S1** (catégories) → 3. **S2** (NOVA) → 4. **S3** (absence de données) →
5. **S6** (additifs : normalisation) → 6. **S4** (messages inexacts) → 7. **S5** (secs et sucres intrinsèques) →
8. **S7, S8** (barème) → 9. **S9** (profil) → 10. **S10** (cohérence) → 11. **S11** (calibration) → 12. **S14**
(version) → 13. **S12, S13** (sources, commentaires).

S1 à S3 changent la note de la majorité des produits. Ils passent **avant** les corrections d'affichage de la phase 2
(C1, C2…), qui présenteraient sinon plus clairement une note fausse. C10 (veto allergène) reste prioritaire : il est indépendant
et vital.

---

# Plan de travail (ordre logique)

Les findings sont rangés dans l'ordre où il est logique de les traiter : les dépendances techniques d'abord, puis la sécurité, puis la valeur. Passe 3 (approfondissement) : ajout de C10, C11, C12, N6, D6 et A8, tous vérifiés dans le code. Passe 4 (étendue) : ajout de C13 à C17 (jeûne, objectif de poids, rétention, relecture OCR, corrélations). Passe 5 (évolutions) : ajout de A9 à A11, D7 à D10 et N7 à N11, puis contre-vérification : A9, D2, D7 et N11 sont partiellement existants (corrigés), A10 est confirmé comme bug. Les identifiants (C = corriger, A = améliorer, D = développer, N = créer) sont conservés. 🔴 = sécurité, 🟠 = bloquant publication, 🟡 = dette.

## Phase 1 — Fondations techniques

Débloquent tout le reste : sécuriser les migrations **avant** de toucher au schéma ou aux valeurs persistées, et rendre le code lisible avant les chantiers d'UI.

### 1. C5. Migrations Room non vérifiables 🟠
✅ **Vérifié (passe 7)** : `tools/check-migrations.py` rejoue les 25 migrations 15→40 dans SQLite et compare au schéma exporté : 0 différence structurelle.

`exportSchema = true`, mais `app/schemas/` ne contient que les versions **15-18, 20-24 et 40**.
Les snapshots **19 et 25 à 39** manquent : `MigrationTestHelper` ne peut pas valider les
15 dernières migrations (40 `Migration(` déclarées dans `AppDatabase.kt`). Les mises à
jour depuis la v1.0 publiée sont le chemin de perte de données le plus probable.
→ Régénérer les schémas manquants en remontant l'historique git (checkout de chaque
version → build KSP), puis écrire un test de migration 15→40.

> *Pourquoi ici :* Prérequis de C6 et de toute évolution de schéma (A1, D1, D2).

### 2. C7. Séparateur NUL brut dans le code source 🟡
✅ **Déjà corrigé** : plus aucun octet NUL dans le code source.

`data/repository/planning/ManualGroceryRepository.kt` contient 226 octets `0x00`. Le
fichier est vu comme **binaire** par git/grep, car il utilise un caractère NUL littéral comme séparateur
(`split('<NUL>')`) dans DataStore. Les revues, diffs et recherches ignorent silencieusement ce fichier.
→ Écrire `'\u0000'` en échappement, ou mieux, sérialiser en JSON.

> *Pourquoi ici :* Rapide ; rend le fichier visible pour les revues des phases suivantes.

### 3. A5 – Taille des fichiers
`ScanViewModel` (719 l.), `AppDatabase` (687 l. : extraire
les 40 migrations dans `Migrations.kt`), `Theme.kt` (627 l.), `ScoringEngine` (604 l.).

> *Pourquoi ici :* Extraire les migrations d'`AppDatabase` (aide C5) et découper `ScanViewModel`/écran résultat avant C1-C2.

### 4. C6. Vestiges « LLM » trompeurs 🟡
✅ **Fait** : `ScanSource.LLM` → `ON_DEVICE_OCR` (l'ancienne valeur stockée « LLM » est relue par `ScanSource.fromStored`), `mergeOffWithLlm` → `mergeOffWithPhoto`, commentaires corrigés (test `ScanSourceTest`).

106 mentions de `LLM` dans le code, dont l'énum `ScanSource.LLM` utilisée pour les
**photos lues sur l'appareil par ML Kit**, des commentaires « OFF-vs-LLM conflict » et le
commentaire de `FoodDb.kt` (« falling back to the LLM's own rough macro guess »). Le README
affirme qu'aucune IA n'est utilisée. D'après la philosophie du projet, toute divergence
entre un commentaire et le comportement réel compte comme un bug.
→ Renommer `ScanSource.LLM` en `ON_DEVICE_OCR` (en gardant la valeur persistée, avec une
migration ou un `@SerializedName`) et purger les commentaires obsolètes.

> *Pourquoi ici :* Renomme une valeur persistée : nécessite le filet de tests de C5.

---

## Phase 2 — Sécurité de l'utilisateur

Le cœur de la promesse ; changements localisés et à fort impact.

### 5. C10. Un allergène confirmé n'a aucun effet sur la note personnelle 🔴
✅ **Fait** : un allergène confirmé déclenche le veto (« Contient : … »), les traces restent informatives (test `PersonalAllergenVetoTest`).

`domain/engine/scoring/PersonalScoreEngine.kt` (l. 63-66) calcule `allergenHits`, mais ce
résultat ne participe **ni au `veto` ni aux `adjustments`** : seuls `dietResult` et
`conditionsResult` peuvent mettre la note à 0. Un produit contenant de l'arachide, scanné par un profil
allergique à l'arachide, garde donc sa note personnelle (par ex. **A, anneau vert**
dans `DualScoreRing`), et seule une carte placée plus bas le signale (cf. C1). Le même
score « sain » sert ensuite au tri de l'historique, aux alternatives et aux widgets.
→ Un allergène **confirmé** (`!isTraceOnly`) déclenche `veto = true` avec la raison
« Contient : arachide », affichée dans le bandeau de veto. Les traces sont traitées
selon N6.

> *Pourquoi ici :* Le défaut de sécurité le plus grave de l'audit : à corriger avant tout le reste, y compris avant C1, puisque C1 ne fait que déplacer la carte.

### 6. C1. Alertes de sécurité mal ordonnées sur l'écran résultat 🔴
✅ **Fait** : veto, allergènes, ingrédients non vérifiables et interactions médicamenteuses sont affichés juste sous le bandeau de rappel.

`presentation/result/ResultContent.kt` affiche, dans cet ordre : bandeau de rappel, badges,
anneau de score, verdict, **ComparisonCard, AlternativeCard, PairingsCard**, puis
seulement ensuite **DietVetoBanner** et **AllergenWarningsCard** (l. ~200-216).
Un allergène détecté pour l'utilisateur apparaît donc **sous** des suggestions d'accords
mets/aliments. Pour quelqu'un d'allergique, c'est l'information n°1.
→ Remonter `AllergenWarningsCard`, `DietVetoBanner` et `AllergenUnverifiedBanner` juste
sous `RecallBanner`, avant l'anneau de score.

> *Pourquoi ici :* Réordonnancement des composables du résultat.

### 7. N6. Sévérité par allergène (allergie sévère / allergie / intolérance) 🔴
✅ **Fait** : `Profile.allergenSeverities` (DataStore chiffré, sauvegarde incluse, aucune migration Room nécessaire). SÉVÈRE : traces = veto ; ALLERGIE (défaut) : ingrédient = veto, traces = prudence ; INTOLÉRANCE : −15 points, traces sans effet. Choix par allergène dans le profil ; la carte Famille suit les mêmes règles.

`Profile.allergens` est un simple `Set<String>`. Les mentions « peut contenir des traces » sont
détectées (`isTraceOnly`), mais elles sont traitées de la même façon pour tout le monde. Or, pour une
allergie anaphylactique, une trace est un motif de refus, alors que pour une intolérance au lactose elle est sans importance.
→ `Map<String, AllergenSeverity>` dans le profil (migration de base de données). Avec SÉVÈRE, une trace entraîne un
veto ; avec ALLERGIE, un malus et un bandeau ; avec INTOLÉRANCE, une simple information.

> *Pourquoi ici :* Prolonge directement C10, dans le même moteur ; dépend de C5 pour la migration du profil.

### 8. C2. Interactions aliment-médicament cachées derrière un bouton 🔴
✅ **Fait** : `MedicationRiskBanner` affiché directement dans l'écran résultat.

Les risques médicamenteux du produit scanné (`hints.medicationRisks`, issus de
`checkFoodDrugInteractions`) ne s'affichent que dans `HintPanel`, une boîte de dialogue
fermée par défaut (`showHints = false`). Un utilisateur sous AVK qui scanne des épinards ne
voit rien sans ouvrir le panneau.
→ Bandeau rouge inline « Interaction possible avec <médicament> » (même niveau que C1)
lorsque `medicationRisks` n'est pas vide ; le panneau reste pour le détail.

> *Pourquoi ici :* Même écran que C1 : à faire dans la foulée.

### 9. C3. Rappels produits jamais revérifiés après le scan 🔴
✅ **Fait** : `RecallSyncWorker` quotidien (réseau requis) : rappels publiés depuis la dernière synchro × codes-barres de l'historique (180 j) et du garde-manger ; cache positif (bandeau résultat, garde-manger) et notification « Produit rappelé » une seule fois par produit, canal dédié prioritaire (test `RecallSyncMatchTest`). Toucher la notification ouvre la fiche du produit.

`RecallRepository` interroge RappelConso **au scan** ; le cache négatif expire en 7 jours
mais n'est réinterrogé qu'**au prochain scan** du même code-barres. `ReminderWorker` ne
contient aucune logique de rappel produit. Un produit acheté, scanné puis rappelé la
semaine suivante ne déclenche **aucune notification**.
→ `RecallSyncWorker` quotidien : requête RappelConso sur les rappels publiés depuis la
dernière synchro, croisés avec les codes-barres de l'historique et du garde-manger ;
notification « Produit rappelé : X — ne pas consommer ». C'est probablement la
fonctionnalité à plus forte valeur sécurité par ligne de code de toute l'app.

> *Pourquoi ici :* Diagnostic ; s'implémente avec D1.

### 10. D1 – Synchro RappelConso en arrière-plan
✅ **Fait** : synchro quotidienne (C3) et écran « Produits rappelés » (bouton dans l'historique) : motif, risques, conduite à tenir, lien vers la fiche officielle, ouverture du produit.

(cf. C3), avec écran « Mes produits
rappelés ».

> *Pourquoi ici :* Worker RappelConso + écran : la correction de C3.

### 11. C9. Précision des rappels de médicaments 🟡
✅ **Fait** : alarme exacte (`setExactAndAllowWhileIdle`, repli inexact sans permission) sur la prochaine prise ; elle lance `ReminderWorker` aussitôt (expédié), qui réarme la suivante ; réarmement au démarrage et au redémarrage du téléphone. Le worker de 15 min reste en filet de sécurité (test `MedicationAlarmsTest`).

Les doses sont rappelées par un `PeriodicWorkRequest` de **15 min** (choix documenté dans
`ScanEatApp.kt`), sans `AlarmManager`. Retard possible de 15 min, et bien plus en Doze.
→ Alarmes exactes (`setExactAndAllowWhileIdle`, permission `SCHEDULE_EXACT_ALARM` /
`USE_EXACT_ALARM`, légitime pour une app de rappel médical) pour les doses ; garder le
worker pour les rappels de repas et d'hydratation.

---

> *Pourquoi ici :* Fiabilise les rappels de doses ; indépendant mais même famille (WorkManager/alarmes).

### 12. C11. Noms de médicaments visibles sur l'écran verrouillé 🟠
✅ **Fait** : notifications du canal MEDICATION en `VISIBILITY_PRIVATE` avec une version publique « Rappel de traitement » (et canal en visibilité privée pour les nouvelles installations). Reste : l'option « masquer le nom » même téléphone déverrouillé.

`ReminderWorker` (l. 141-168) place `med.name` dans le corps de la notification, et
`NotificationHelper.show` ne définit ni `setVisibility(VISIBILITY_PRIVATE)` ni
`setPublicVersion(...)`. Sur un téléphone réglé pour afficher le contenu des notifications (c'est le réglage par défaut),
« Prendre : Lévothyrox 75 µg » s'affiche donc sur l'écran verrouillé. C'est une donnée de santé
(catégorie particulière au sens du RGPD, art. 9).
→ Canal MEDICATION en `VISIBILITY_PRIVATE`, avec une version publique générique « Rappel de
traitement ». Ajouter une option « masquer le nom du médicament ».

> *Pourquoi ici :* Même code que C9 (notifications de doses) : à traiter dans le même passage.

### 13. C13. Jeûne intermittent sans aucune contre-indication 🔴
✅ **Fait** : `fastingSafety(profile)` bloque le démarrage (grossesse, mineur), avertit (diabète, IMC < 18,5), masque la série pour ces profils et cite la FFAB (test `FastingSafetyTest`).

`presentation/fasting/` : `FastingViewModel` ne lit que `activeProfileId` ; aucun champ du
profil n'est consulté. `StartFastForm` propose 12, 16, 18, 20 et **24 h** et un compteur de
**série** (streak) quel que soit le profil. Or le profil connaît déjà la grossesse
(`pregnancyStartDate`), l'âge, l'IMC et les conditions (diabète).
Le jeûne est déconseillé, voire contre-indiqué, pendant la grossesse et l'allaitement, chez les mineurs, en cas
d'insuffisance pondérale et pour un diabète traité par insuline ou sulfamides (risque
d'hypoglycémie). Une série récompensant la restriction est aussi un facteur de risque de troubles du comportement alimentaire.
→ Avant de démarrer, vérifier le profil : bloquer le jeûne pour une grossesse ou un mineur ; afficher un avertissement
avec renvoi au médecin pour un diabète traité ou un IMC < 18,5. Pas de série pour ces profils. Sources à
citer : ANSES / HAS. Mentionner une ressource d'aide sur les troubles du comportement alimentaire (ex. FFAB) dans l'écran.

> *Pourquoi ici :* Même logique que C10 (le profil connaît le risque mais le module l'ignore) ; correction locale à un écran.

### 14. C14. Objectif « Perdre du poids » accepté pour un profil à risque 🟠
✅ **Fait** : `Profile.safeGoal` remplace LOSE par MAINTAIN (grossesse, < 18 ans, IMC < 18,5) dans tous les calculs (cibles, protéines, ajustements de score), avec explication dans le profil ; un poids cible sous IMC 18,5 affiche une alerte au lieu d'une date (test `SafeGoalTest`).

`ProfileSelectors` propose `Goal.LOSE` sans condition. Le plancher de 1200 kcal
(`DailyTargets.kt`, l. 109-122) existe, mais rien n'empêche de choisir la perte avec un IMC
< 18,5, pour un mineur ou pendant une grossesse. Les ajustements de score favorisent alors les produits pauvres en énergie,
ce qui va à l'encontre de `BmiAdjustments` (qui, pour l'IMC bas, favorise les produits denses).
→ Désactiver LOSE, ou le convertir en MAINTAIN avec explication, pour ces profils. Pour un objectif de poids
cible sous IMC 18,5, afficher une alerte au lieu d'une date de « réussite » (`DashboardWeightForecast`).

> *Pourquoi ici :* Même famille que C13 ; touche le profil, donc à faire juste après.

### 15. C16. Résultats OCR et photo notés sans relecture par l'utilisateur 🟠
✅ **Partiellement fait** : une étiquette lue par OCR ouvre un écran de relecture (nom, 8 valeurs pour 100 g, ingrédients, virgule acceptée) avant la note ; le résultat porte « Valeurs lues sur la photo (OCR), vérifiées par vous » ; « Annuler » supprime le scan (test `OcrReviewTest`). Restent : surlignage sur la photo et 3 meilleures correspondances pour la reconnaissance d'image.

`ScanRepository.identifyOrScoreFromImages` : le produit lu par ML Kit (`parseLabel`) ou
reconnu par l'image (`foodIdentifier`) est **noté immédiatement**. Seul un avertissement
textuel signale l'origine. `LabelTextParser.sameRowNumber` prend « le premier nombre à droite du
libellé ». Une colonne mal alignée, ou une virgule lue comme un point (« 1,5 » lu « 15 »), produit une
note fausse présentée avec la même assurance qu'une fiche Open Food Facts.
→ Écran de relecture éditable (valeurs pour 100 g et ingrédients surlignés sur la photo) avant
de calculer la note, avec un badge « valeurs lues par OCR, vérifiées par vous ». Même principe pour la reconnaissance
d'image : proposer les 3 meilleures correspondances au lieu d'en choisir une.

> *Pourquoi ici :* Fiabilité de la note à la source : à faire avant d'exposer les sources (phase 3), sinon on afficherait des sources sur une donnée mal lue.

---

## Phase 3 — Modèle de messages et de sources

C8 d'abord : une fois que le domaine renvoie des clés structurées, on y attache la source (A1) au même endroit au lieu de le faire deux fois.

### 16. C8. Internationalisation dans le moteur 🟡
⏸️ **Reporté (passe 7)** : refonte de ~230 textes du moteur en clés, à faire dans une passe dédiée (risque de régression élevé) ; les nouveaux textes de cette passe sont dans `strings.xml`.

233 occurrences de `if (en) "…" else "…"` dans le code (moteur de scoring compris, ex.
`ScoringEngine.kt:578`). Le domaine produit des textes localisés : impossible d'ajouter une
3ᵉ langue, et les traductions échappent aux outils.
→ Le domaine renvoie des codes ou des clés (`WarningKey.NOVA_INFERRED(confidence)`),
et la présentation les résout via `strings.xml`.

> *Pourquoi ici :* Domaine → clés ; présentation → `strings.xml`.

### 17. A1 – Transparence des sources dans l'UI
✅ **Fait (niveau table)** : catalogue `DATA_SOURCES` (nom, usage, version/date, lien), chaque entrée vérifiée contre un commentaire de provenance du code. Reste : la source en pied de chaque alerte (dépend de C8).

Les sources existent en commentaires ;
les remonter en données (`source`, `version`, `verifiedOn` par table ou par entrée) et
les afficher dans un écran « Sources et versions des données », ainsi qu'en pied de chaque
alerte (« Source : ANSM, 2024 »). C'est le différenciateur central de la philosophie
« confiance absolue ».

> *Pourquoi ici :* Métadonnées `source`/`version`/`verifiedOn` sur ces mêmes clés.

### 18. N2 – Écran « Sources & méthodologie »
✅ **Fait** : écran « Sources et méthodologie » (calcul de la note, note personnelle, traitement sur l'appareil, version du moteur, 8 sources) ouvert depuis les réglages et depuis chaque note (« Comment est calculée cette note ? »).

public (cf. A1), lien depuis chaque note.

> *Pourquoi ici :* Écran « Sources & méthodologie » alimenté par A1.

### 19. A2 – « Pourquoi cette note ? »
⏸️ **Partiel (existant)** : la décomposition par pilier et les ajustements personnels sont déjà affichés ; le lien règle → source par ajustement dépend de C8.

`AdjustmentsSection` liste déjà les ajustements
personnels (points ±). Il manque le lien vers la règle et la source de chaque ajustement,
ainsi que la décomposition de la note classique (`PillarsSection`) en langage simple.

> *Pourquoi ici :* « Pourquoi cette note ? » : réutilise les clés de C8 et les sources de A1.

### 20. D9. Page « Ma sécurité » (évolution de `DashboardSafetyCenter`)
✅ **Fait** : page « Ma sécurité » (icône bouclier du tableau de bord) : allergènes et sévérités, conditions et contre-indications (jeûne, perte de poids), traitements actifs et interactions, produits rappelés, lien vers les sources.

`buildDashboardSafetyWarnings` agrège déjà les alertes dans une carte du tableau de bord. En faire
une page à part entière : allergènes et sévérités (N6), interactions actives entre les traitements et le journal
du jour, produits rappelés (D1), contre-indications du profil (C13, C14), chacun avec sa source (A1).
C'est la vitrine de la promesse de confiance, accessible en un geste depuis la barre de navigation.

> *Pourquoi ici :* Assemble les briques de sécurité (phase 2) et de sources (A1) : à faire dès qu'elles existent.

---

## Phase 4 — Contenu santé

S'appuie sur le format sourcé de la phase 3 : chaque nouvelle entrée naît avec sa source.

### 21. A7 – Couverture de la base médicamenteuse
✅ **Partiellement fait** : + levure de riz rouge × statines (ANSES 2014, EFSA 2018), calcium/fer × fluoroquinolones/cyclines (RCP), alcool × métronidazole (RCP Flagyl, hors vinaigres), tyramine × linézolide (RCP Zyvoxid) (test `FoodDrugA7Test`). Restent : millepertuis et réglisse (concernent surtout des compléments ou une condition, pas une classe de médicaments).

`FoodDrugInteractionDb` couvre 5 classes
(AVK, IMAO, statines/inhibiteurs calciques, IEC/sartans, lévothyroxine). Candidats sourcés
ANSM/EMA : millepertuis (inducteur CYP3A4), réglisse (antihypertenseurs, hypokaliémie),
tyramine sous linézolide, alcool sous métronidazole, calcium/fer avec les
fluoroquinolones/tétracyclines, levure de riz rouge et statines.

> *Pourquoi ici :* Nouvelles interactions aliment-médicament (ANSM/EMA).

### 22. D6. Actions « Pris / Reporter » et suivi du stock de médicaments
✅ **Partiellement fait** : boutons « Pris » (enregistre la prise datée, comme dans l'app) et « Reporter 15 min » sur chaque rappel de dose. Restent : stock de comprimés et rappel de renouvellement d'ordonnance (migration Room).

Les notifications n'ont aucune action (pas d'`addAction` dans `NotificationHelper`) :
pour marquer une prise, il faut ouvrir l'app. Il n'existe pas non plus de suivi du nombre de
comprimés restants ni de la date de renouvellement de l'ordonnance.
→ Ajouter des boutons d'action (`BroadcastReceiver` qui écrit la prise datée), une alerte « plus que 5 jours
de traitement » et un rappel de renouvellement d'ordonnance. L'observance entre ensuite dans D5.

> *Pourquoi ici :* Après C9/C11 (même infrastructure de notifications) ; alimente le rapport médecin D5.

### 23. C17. Corrélations symptômes ↔ traitements sur 2 occurrences 🟡
✅ **Fait** : ≥ 5 co-occurrences, ≥ 14 jours observés, test exact de Fisher unilatéral (p < 0,05), indicateur « lien net / modéré » et mention rouge « N'arrêtez jamais un traitement sans avis médical » (test `SymptomCorrelationTest`). Reste : export vers le rapport médecin (D5).

`SymptomCorrelation.kt` : `MIN_SYMPTOM_DAY_OCCURRENCES = 2` et `MIN_DIFFERENCE_PCT = 20`,
sans test statistique, appliqués aussi aux **médicaments** (« Corrélations possibles avec un
traitement »). La mention « pas une preuve médicale » existe, mais deux jours suffisent pour
suggérer un lien entre un symptôme et un traitement, ce qui risque de pousser à un arrêt de traitement non supervisé.
→ Relever le seuil (au moins 5 occurrences et 14 jours d'observation), ajouter un test simple (exact de Fisher) et un
indicateur de robustesse. Pour les traitements, ajouter explicitement « N'arrêtez pas un traitement sans avis médical »
et proposer de l'inclure dans le rapport médecin (D5) plutôt que de conclure.

> *Pourquoi ici :* Contenu santé : se range avec A7/D6 et alimente D5.

### 24. N10. Carnet de réactions allergiques
✅ **Fait** : table `allergic_reactions` (migration 40→41, schéma 41 exporté, rejeu des migrations sans différence), écran « Carnet de réactions » depuis « Ma sécurité » (produit choisi parmi les scans récents, ingrédients figés, symptômes, gravité, adrénaline), points communs à discuter avec un allergologue (allergènes de l'annexe II puis ingrédients présents dans ≥ 2 réactions), rappel d'urgence (15/112), sauvegarde et rapport médecin (test `ReactionAnalysisTest`).

Il n'existe aucun endroit pour noter « réaction le 12/10 après X ». Saisie : date, produit (pris dans
l'historique de scans ou le journal), symptômes, gravité, prise d'adrénaline. Le moteur croise ensuite
les ingrédients communs aux produits incriminés pour suggérer un **allergène caché à discuter avec un
allergologue** (sans conclure, cf. C17). Le carnet est exporté dans le rapport médecin (D5).

> *Pourquoi ici :* Contenu santé ; réutilise l'historique de scans et `AllergenDetector`, et alimente D5.

### 25. D5 – Rapport médecin
✅ **Fait** : page « Santé et traitements (30j) » du PDF : allergies et sévérités, conditions, traitements actifs avec observance (prises notées / prévues), nombre d'interactions entre traitements, symptômes par type avec sévérité moyenne, corrélations traitement ↔ symptôme retenues par le test de C17 (test `MedicationAdherenceTest`).

ajouter interactions détectées, observance des médicaments
et symptômes sur la période choisie dans `PdfReportRepository`.

> *Pourquoi ici :* Le rapport médecin intègre interactions, observance et symptômes.

---

## Phase 5 — Prêt pour publication

Honnêteté de l'offre et qualité perçue avant de pousser la diffusion.

### 26. C4. « Premium » sans paiement 🟠
`PremiumSection` propose un bouton qui bascule `isPremium` sur `true`/`false` (`onSetPremium(!isPremium)`).
Aucune dépendance Billing. Toute personne peut s'activer Premium gratuitement depuis les
réglages. En l'état, publier avec un écran « Premium verrouillé » serait trompeur.
→ Soit intégrer Google Play Billing (abonnement / achat unique), soit retirer la notion
de Premium avant publication. `PremiumGate` mentionne aussi « AI photo scanning » alors
que le README affirme « ni serveur ni IA » (cf. C6).

> *Pourquoi ici :* Décision : Billing ou retrait du Premium.

### 27. C12. Risque de perte totale des données au changement de téléphone 🟠
`AndroidManifest.xml` : `android:allowBackup="false"`. L'app est 100 % locale et la sauvegarde
chiffrée (`BackupRepository`) est **uniquement manuelle** : on ne trouve ni sauvegarde
périodique, ni rappel « dernière sauvegarde il y a X jours ». Un téléphone perdu ou réinitialisé fait perdre
des mois d'historique, de traitements, de pesées, etc.
→ Sauvegarde automatique chiffrée hebdomadaire vers un dossier choisi par l'utilisateur
(SAF, `OpenDocumentTree`, persistable URI) via WorkManager, plus un rappel dans les réglages. Autre option :
une sauvegarde Android chiffrée côté client, avec des règles `data_extraction_rules` qui excluent les clés du Keystore.

> *Pourquoi ici :* Bloquant publication au même titre que C4/C5 : la promesse « tout est local » implique d'assumer la sauvegarde.

### 28. C15. Historique de santé supprimé silencieusement 🟠
Plafonds de rétention par `trim` : humeur et sommeil **730 lignes** (`MoodRepository`,
`SleepRepository`), non-alimentaire 500, et poids, prises de médicaments, journal et scans 5000.
Pour un utilisateur assidu (5 entrées par jour), le journal alimentaire perd ses données les plus anciennes au bout d'environ 2,7 ans,
et l'humeur et le sommeil au bout de 2 ans, **sans avertissement ni proposition d'export**. Ce sont précisément les
données longues qu'un médecin voudrait voir (D5), et qu'aucune sauvegarde automatique ne protège (C12).
→ Remplacer la suppression par un archivage (agrégats mensuels conservés, détail exportable), ou relever les plafonds en
les rendant réglables ; dans tous les cas, prévenir avant la première suppression.

> *Pourquoi ici :* Même sujet que C12 (préservation des données) ; à traiter ensemble avant publication.

### 29. N4 – Monétisation réelle
(cf. C4) : Play Billing ; Premium centré sur Biolism et les
rapports, sans rien retirer à la sécurité (allergènes, rappels et interactions restent
gratuits).

> *Pourquoi ici :* Implémentation de la décision de C4.

### 30. A6 – Accessibilité
Environ 40 `contentDescription = null` : vérifier qu'aucune ne
porte sur une icône de statut ou d'alerte. Valider le contraste AA des couleurs de grade
en mode sombre.

> *Pourquoi ici :* Accessibilité et contraste.

### 31. A4 – Navigation
34 modules dans `presentation/` (fasting, loyalty, seasonal,
calendar, sleep, symptom, expenses…). Proposer à l'onboarding de choisir les modules
suivis, et masquer les autres dans la navigation.

> *Pourquoi ici :* Onboarding « modules suivis » et navigation allégée.

### 32. A11. Tableau de bord personnalisable
`presentation/dashboard/cards/` contient **21 cartes**. Le réordonnancement n'existe que pour les onglets
du Journal (`UserPreferences`, l. 113). Permettre de masquer et réordonner les cartes, avec des préréglages
selon les modules choisis à l'onboarding (A4).

> *Pourquoi ici :* Va de pair avec A4 (navigation allégée).

### 33. N11. Lecture vocale des alertes, pas seulement de la note
✅ **Fait** : la lecture vocale annonce d'abord les alertes (rappel officiel, veto personnel avec son motif, traces d'allergènes, interaction médicamenteuse), puis la note ; libellé du réglage mis à jour. Reste : proposer l'option dans l'onboarding.

**Partiellement existant (corrigé après vérification)** : `util/ScoreSpeechAnnouncer` lit déjà la
note et le grade à voix haute après un scan (option désactivée par défaut). À faire : annoncer aussi les
**alertes** (allergène, interaction, rappel, veto), qui sont l'information vitale pour une personne
malvoyante, et proposer l'option dans l'onboarding accessibilité.

> *Pourquoi ici :* Accessibilité : avec A6.

---

## Phase 6 — Valeur produit

Fonctionnalités qui reposent sur des modules existants.

### 34. A8. Recherche Open Food Facts sur l'ancienne API
⚠️ **Vérifié (passe 7)** : la recherche en ligne a déjà un debounce de 700 ms, un minimum de 2 caractères et un cache ; reste la migration vers Search-a-licious.

`OpenFoodFactsApi` utilise `cgi/search.pl`, l'ancien point d'accès de recherche, lent et limité
à environ 10 requêtes par minute par Open Food Facts. Avec un debounce de 150 ms (`FoodSearchViewModel`), la frappe
d'un utilisateur peut atteindre cette limite.
→ Migrer vers l'API Search-a-licious d'Open Food Facts, ou passer à l'API v2 avec filtres, et porter le debounce
de la recherche en ligne à environ 400 ms, en gardant la recherche locale instantanée.

> *Pourquoi ici :* Prérequis de A3 : les alternatives Open Food Facts reposent sur cette recherche.

### 35. A10. Green-Score : les notes A+ et F sont jetées 🟡
✅ **Fait** : `environmental_score_*` demandés en priorité (repli sur `ecoscore_*`), A+ et F conservés, badge renommé « Green-Score » (test `GreenScoreGradeTest`).

**Vérifié.** `OffMapper.kt:153` : `ecoscoreGrade … takeIf { it.matches(Regex("[a-e]")) }`. Fin 2024,
Open Food Facts a renommé l'Eco-Score en **Green-Score** et ajouté deux notes : **A+** et **F**. Les produits les
meilleurs et les pires sur le plan environnemental perdent donc silencieusement leur information environnementale dans
l'app : exactement ceux où elle est la plus parlante.
→ Accepter `a-plus` et `f` (vérifier la valeur exacte renvoyée par l'API), renommer l'affichage en
« Green-Score », et envisager les champs `environmental_score_*` de l'API v3 (documentés à côté de
`ecoscore_*`, qui reste présent dans le schéma produit).

> *Pourquoi ici :* Même chantier que A8 (API Open Food Facts).

### 36. A3 – Alternatives venant d'Open Food Facts
Aujourd'hui limitées à l'historique :
un nouvel utilisateur n'a donc jamais d'alternative. Interroger OFF par catégorie
(`categories_tags`) et trier par le score local du profil.

> *Pourquoi ici :* Alternatives Open Food Facts (le score local est stabilisé après les phases 2-3).

### 37. D2 – Garde-manger ↔ dates de péremption
**Partiellement existant** : `PantryItem.expiryUrgency`, une notification de péremption dans `ReminderWorker`,
et des suggestions de recettes à partir du garde-manger (`SuggestRecipesDialog`, mode PANTRY). Reste à faire : prioriser
dans ces suggestions les produits **bientôt périmés** et les placer dans le plan de repas (lien avec D8).

> *Pourquoi ici :* Péremption du garde-manger (même infra de worker que D1).

### 38. D3 – Plan de repas → courses → dépenses
les trois modules existent ; il manque le
bouclage « budget prévu vs ticket scanné » (`ReceiptParser` déjà présent).

> *Pourquoi ici :* Plan → courses → dépenses.

### 39. D4 – Signaux croisés symptômes ↔ aliments
`SymptomCorrelation` existe ; ajouter un
journal d'élimination guidé (FODMAP, lactose), à présenter clairement comme une
observation personnelle et non un diagnostic.

> *Pourquoi ici :* Journal d'élimination symptômes ↔ aliments.

### 40. D10. Évaluer un produit pour toute la famille en un scan
✅ **Fait** : carte « Famille » sur le résultat (dès 2 profils) : ✕ veto, ⚠ traces, ? ingrédients illisibles, ✓ rien détecté, via `familyVerdicts` (test `FamilyCheckTest`).

Le multi-profils existe, mais le résultat n'est calculé que pour le profil **actif**
(`prefs.activeProfileId`). Ajouter sur le résultat une ligne « Famille » : une pastille par profil
(✓ / ⚠ / ✕) en relançant `PersonalScoreEngine` pour chacun. C'est le cas d'usage du parent qui fait les
courses pour un enfant allergique.

> *Pourquoi ici :* S'appuie sur C10/N6 (veto allergène fiable) : sans eux, les pastilles seraient fausses.

### 41. D7. Ticket de caisse → garde-manger
**Partiellement existant (corrigé après vérification)** : `ReceiptScanViewModel` enregistre déjà
les prix (`priceRepo.log`) et interagit avec la liste de courses (`ManualGroceryRepository`,
`GroceryCheckedRepository`). Il n'injecte en revanche **pas** `PantryRepository`. À faire : « Ajouter ces
articles au garde-manger », avec des dates de péremption estimées par catégorie.

> *Pourquoi ici :* Complète D3 (boucle plan → courses → ticket).

### 42. D8. Générateur de plan de repas
`MealPlanViewModel` ne propose que des actions manuelles (`setRecipe`, `duplicateDay`, `duplicateWeek`).
Toutes les briques d'un générateur existent pourtant : cibles journalières (`DailyTargets`),
recettes et modèles, garde-manger avec urgence de péremption, fruits et légumes de saison (`seasonal/`),
allergies et régime. Proposer « Générer ma semaine » (contraintes : budget, temps, priorité aux produits
qui expirent bientôt), puis la liste de courses de la différence avec le garde-manger.

> *Pourquoi ici :* Après D2/D3/D7 : exploite toute la chaîne garde-manger → courses.

### 43. A9. Partager un résultat sous forme d'image
**Partiellement existant (corrigé après vérification)** : `ResultScreen.kt:138` partage déjà un **texte**
(nom, note, grade). À faire : une carte image (note, verdict personnel, principales alertes, source),
plus parlante dans une messagerie et plus virale.

> *Pourquoi ici :* Croissance ; indépendant, mais à faire après la stabilisation des notes (phases 2-3).

---

## Phase 7 — Nouveaux axes

### 44. N1 – Contribution Open Food Facts
aucune écriture vers OFF aujourd'hui. Quand un
produit est absent ou incomplet, proposer d'envoyer photos et champs lus par OCR (API
d'écriture OFF, compte utilisateur ou anonyme).

> *Pourquoi ici :* Contribution Open Food Facts.

### 45. N3 – Scan de carte de restaurant
OCR ML Kit, puis détection allergènes/régime par plat,
en réutilisant `AllergenDetector` et `DietChecker`.

> *Pourquoi ici :* Scan de carte de restaurant (réutilise allergènes/régime durcis en phase 2).

### 46. N5 – Wear OS / tuile
prise de médicament, verre d'eau, chrono de jeûne.

> *Pourquoi ici :* Wear OS / tuile (réutilise les alarmes de C9).

### 47. N7. Mode « En magasin »
Combiner la liste de courses (triée par rayon via `GroceryCategories`) et le scan : scanner un produit
en rayon coche l'article correspondant, vérifie la compatibilité de toute la famille (D10) et compare le
prix au dernier prix payé (D7). Écran unique, pensé pour une seule main.

> *Pourquoi ici :* Assemble D7, D10 et la liste de courses existante.

### 48. N8. Partage d'un profil avec un aidant
Permettre à un parent de transmettre le profil d'un enfant allergique (allergènes, sévérités, régime) à un
autre appareil (grands-parents, nourrice, cantine) via un QR code chiffré, sans compte ni serveur. C'est cohérent
avec la philosophie « tout est local » ; le profil reçu est en lecture seule, avec une date de mise à jour.

> *Pourquoi ici :* Après N6 (sévérités) et D10 (multi-profils au scan).

### 49. N9. Base de produits hors-ligne
En magasin, le réseau est souvent mauvais. Proposer le téléchargement d'un instantané Open Food Facts des
produits les plus scannés en France (export officiel, filtré sur les champs utiles : environ 50 000 produits,
quelques dizaines de Mo) dans une table Room, mis à jour chaque mois. Le scan fonctionne alors sans réseau,
avec la date de l'instantané affichée.

> *Pourquoi ici :* Gros chantier de données ; après A8 (API) et C5 (migrations).

---

## Récapitulatif de l'ordre

- **Phase 1 bis** (Véracité du score — voir l'audit du moteur) : C10 → S15 → S1 → S2 → S3 → S6 → S4 → S5 → S7 → S8 → S9 → S10 → S11 → S14 → S12 → S13
- **Phase 1** (Fondations techniques) : C5 → C7 → A5 → C6
- **Phase 2** (Sécurité de l'utilisateur) : C10 → C1 → N6 → C2 → C3 → D1 → C9 → C11 → C13 → C14 → C16
- **Phase 3** (Modèle de messages et de sources) : C8 → A1 → N2 → A2 → D9
- **Phase 4** (Contenu santé) : A7 → D6 → C17 → N10 → D5
- **Phase 5** (Prêt pour publication) : C4 → C12 → C15 → N4 → A6 → A4 → A11 → N11
- **Phase 6** (Valeur produit) : A8 → A10 → A3 → D2 → D3 → D4 → D10 → D7 → D8 → A9
- **Phase 7** (Nouveaux axes) : N1 → N3 → N5 → N7 → N8 → N9
