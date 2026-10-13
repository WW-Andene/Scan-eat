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

# Plan de travail (ordre logique)

Les findings sont rangés dans l'ordre où il est logique de les traiter : les dépendances techniques d'abord, puis la sécurité, puis la valeur. Passe 3 (approfondissement) : ajout de C10, C11, C12, N6, D6 et A8, tous vérifiés dans le code. Passe 4 (étendue) : ajout de C13 à C17 (jeûne, objectif de poids, rétention, relecture OCR, corrélations). Les identifiants (C = corriger, A = améliorer, D = développer, N = créer) sont conservés. 🔴 = sécurité, 🟠 = bloquant publication, 🟡 = dette.

## Phase 1 — Fondations techniques

Débloquent tout le reste : sécuriser les migrations **avant** de toucher au schéma ou aux valeurs persistées, et rendre le code lisible avant les chantiers d'UI.

### 1. C5. Migrations Room non vérifiables 🟠
`exportSchema = true`, mais `app/schemas/` ne contient que les versions **15-18, 20-24 et 40**.
Les snapshots **19 et 25 à 39** manquent : `MigrationTestHelper` ne peut pas valider les
15 dernières migrations (40 `Migration(` déclarées dans `AppDatabase.kt`). Les mises à
jour depuis la v1.0 publiée sont le chemin de perte de données le plus probable.
→ Régénérer les schémas manquants en remontant l'historique git (checkout de chaque
version → build KSP), puis écrire un test de migration 15→40.

> *Pourquoi ici :* Prérequis de C6 et de toute évolution de schéma (A1, D1, D2).

### 2. C7. Séparateur NUL brut dans le code source 🟡
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
`presentation/result/ResultContent.kt` affiche, dans cet ordre : bandeau de rappel, badges,
anneau de score, verdict, **ComparisonCard, AlternativeCard, PairingsCard**, puis
seulement ensuite **DietVetoBanner** et **AllergenWarningsCard** (l. ~200-216).
Un allergène détecté pour l'utilisateur apparaît donc **sous** des suggestions d'accords
mets/aliments. Pour quelqu'un d'allergique, c'est l'information n°1.
→ Remonter `AllergenWarningsCard`, `DietVetoBanner` et `AllergenUnverifiedBanner` juste
sous `RecallBanner`, avant l'anneau de score.

> *Pourquoi ici :* Réordonnancement des composables du résultat.

### 7. N6. Sévérité par allergène (allergie sévère / allergie / intolérance) 🔴
`Profile.allergens` est un simple `Set<String>`. Les mentions « peut contenir des traces » sont
détectées (`isTraceOnly`), mais elles sont traitées de la même façon pour tout le monde. Or, pour une
allergie anaphylactique, une trace est un motif de refus, alors que pour une intolérance au lactose elle est sans importance.
→ `Map<String, AllergenSeverity>` dans le profil (migration de base de données). Avec SÉVÈRE, une trace entraîne un
veto ; avec ALLERGIE, un malus et un bandeau ; avec INTOLÉRANCE, une simple information.

> *Pourquoi ici :* Prolonge directement C10, dans le même moteur ; dépend de C5 pour la migration du profil.

### 8. C2. Interactions aliment-médicament cachées derrière un bouton 🔴
Les risques médicamenteux du produit scanné (`hints.medicationRisks`, issus de
`checkFoodDrugInteractions`) ne s'affichent que dans `HintPanel`, une boîte de dialogue
fermée par défaut (`showHints = false`). Un utilisateur sous AVK qui scanne des épinards ne
voit rien sans ouvrir le panneau.
→ Bandeau rouge inline « Interaction possible avec <médicament> » (même niveau que C1)
lorsque `medicationRisks` n'est pas vide ; le panneau reste pour le détail.

> *Pourquoi ici :* Même écran que C1 : à faire dans la foulée.

### 9. C3. Rappels produits jamais revérifiés après le scan 🔴
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
(cf. C3), avec écran « Mes produits
rappelés ».

> *Pourquoi ici :* Worker RappelConso + écran : la correction de C3.

### 11. C9. Précision des rappels de médicaments 🟡
Les doses sont rappelées par un `PeriodicWorkRequest` de **15 min** (choix documenté dans
`ScanEatApp.kt`), sans `AlarmManager`. Retard possible de 15 min, et bien plus en Doze.
→ Alarmes exactes (`setExactAndAllowWhileIdle`, permission `SCHEDULE_EXACT_ALARM` /
`USE_EXACT_ALARM`, légitime pour une app de rappel médical) pour les doses ; garder le
worker pour les rappels de repas et d'hydratation.

---

> *Pourquoi ici :* Fiabilise les rappels de doses ; indépendant mais même famille (WorkManager/alarmes).

### 12. C11. Noms de médicaments visibles sur l'écran verrouillé 🟠
`ReminderWorker` (l. 141-168) place `med.name` dans le corps de la notification, et
`NotificationHelper.show` ne définit ni `setVisibility(VISIBILITY_PRIVATE)` ni
`setPublicVersion(...)`. Sur un téléphone réglé pour afficher le contenu des notifications (c'est le réglage par défaut),
« Prendre : Lévothyrox 75 µg » s'affiche donc sur l'écran verrouillé. C'est une donnée de santé
(catégorie particulière au sens du RGPD, art. 9).
→ Canal MEDICATION en `VISIBILITY_PRIVATE`, avec une version publique générique « Rappel de
traitement ». Ajouter une option « masquer le nom du médicament ».

> *Pourquoi ici :* Même code que C9 (notifications de doses) : à traiter dans le même passage.

### 13. C13. Jeûne intermittent sans aucune contre-indication 🔴
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
`ProfileSelectors` propose `Goal.LOSE` sans condition. Le plancher de 1200 kcal
(`DailyTargets.kt`, l. 109-122) existe, mais rien n'empêche de choisir la perte avec un IMC
< 18,5, pour un mineur ou pendant une grossesse. Les ajustements de score favorisent alors les produits pauvres en énergie,
ce qui va à l'encontre de `BmiAdjustments` (qui, pour l'IMC bas, favorise les produits denses).
→ Désactiver LOSE, ou le convertir en MAINTAIN avec explication, pour ces profils. Pour un objectif de poids
cible sous IMC 18,5, afficher une alerte au lieu d'une date de « réussite » (`DashboardWeightForecast`).

> *Pourquoi ici :* Même famille que C13 ; touche le profil, donc à faire juste après.

### 15. C16. Résultats OCR et photo notés sans relecture par l'utilisateur 🟠
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
233 occurrences de `if (en) "…" else "…"` dans le code (moteur de scoring compris, ex.
`ScoringEngine.kt:578`). Le domaine produit des textes localisés : impossible d'ajouter une
3ᵉ langue, et les traductions échappent aux outils.
→ Le domaine renvoie des codes ou des clés (`WarningKey.NOVA_INFERRED(confidence)`),
et la présentation les résout via `strings.xml`.

> *Pourquoi ici :* Domaine → clés ; présentation → `strings.xml`.

### 17. A1 – Transparence des sources dans l'UI
Les sources existent en commentaires ;
les remonter en données (`source`, `version`, `verifiedOn` par table ou par entrée) et
les afficher dans un écran « Sources et versions des données », ainsi qu'en pied de chaque
alerte (« Source : ANSM, 2024 »). C'est le différenciateur central de la philosophie
« confiance absolue ».

> *Pourquoi ici :* Métadonnées `source`/`version`/`verifiedOn` sur ces mêmes clés.

### 18. N2 – Écran « Sources & méthodologie »
public (cf. A1), lien depuis chaque note.

> *Pourquoi ici :* Écran « Sources & méthodologie » alimenté par A1.

### 19. A2 – « Pourquoi cette note ? »
`AdjustmentsSection` liste déjà les ajustements
personnels (points ±). Il manque le lien vers la règle et la source de chaque ajustement,
ainsi que la décomposition de la note classique (`PillarsSection`) en langage simple.

> *Pourquoi ici :* « Pourquoi cette note ? » : réutilise les clés de C8 et les sources de A1.

---

## Phase 4 — Contenu santé

S'appuie sur le format sourcé de la phase 3 : chaque nouvelle entrée naît avec sa source.

### 20. A7 – Couverture de la base médicamenteuse
`FoodDrugInteractionDb` couvre 5 classes
(AVK, IMAO, statines/inhibiteurs calciques, IEC/sartans, lévothyroxine). Candidats sourcés
ANSM/EMA : millepertuis (inducteur CYP3A4), réglisse (antihypertenseurs, hypokaliémie),
tyramine sous linézolide, alcool sous métronidazole, calcium/fer avec les
fluoroquinolones/tétracyclines, levure de riz rouge et statines.

> *Pourquoi ici :* Nouvelles interactions aliment-médicament (ANSM/EMA).

### 21. D6. Actions « Pris / Reporter » et suivi du stock de médicaments
Les notifications n'ont aucune action (pas d'`addAction` dans `NotificationHelper`) :
pour marquer une prise, il faut ouvrir l'app. Il n'existe pas non plus de suivi du nombre de
comprimés restants ni de la date de renouvellement de l'ordonnance.
→ Ajouter des boutons d'action (`BroadcastReceiver` qui écrit la prise datée), une alerte « plus que 5 jours
de traitement » et un rappel de renouvellement d'ordonnance. L'observance entre ensuite dans D5.

> *Pourquoi ici :* Après C9/C11 (même infrastructure de notifications) ; alimente le rapport médecin D5.

### 22. C17. Corrélations symptômes ↔ traitements sur 2 occurrences 🟡
`SymptomCorrelation.kt` : `MIN_SYMPTOM_DAY_OCCURRENCES = 2` et `MIN_DIFFERENCE_PCT = 20`,
sans test statistique, appliqués aussi aux **médicaments** (« Corrélations possibles avec un
traitement »). La mention « pas une preuve médicale » existe, mais deux jours suffisent pour
suggérer un lien entre un symptôme et un traitement, ce qui risque de pousser à un arrêt de traitement non supervisé.
→ Relever le seuil (au moins 5 occurrences et 14 jours d'observation), ajouter un test simple (exact de Fisher) et un
indicateur de robustesse. Pour les traitements, ajouter explicitement « N'arrêtez pas un traitement sans avis médical »
et proposer de l'inclure dans le rapport médecin (D5) plutôt que de conclure.

> *Pourquoi ici :* Contenu santé : se range avec A7/D6 et alimente D5.

### 23. D5 – Rapport médecin
ajouter interactions détectées, observance des médicaments
et symptômes sur la période choisie dans `PdfReportRepository`.

> *Pourquoi ici :* Le rapport médecin intègre interactions, observance et symptômes.

---

## Phase 5 — Prêt pour publication

Honnêteté de l'offre et qualité perçue avant de pousser la diffusion.

### 24. C4. « Premium » sans paiement 🟠
`PremiumSection` propose un bouton qui bascule `isPremium` sur `true`/`false` (`onSetPremium(!isPremium)`).
Aucune dépendance Billing. Toute personne peut s'activer Premium gratuitement depuis les
réglages. En l'état, publier avec un écran « Premium verrouillé » serait trompeur.
→ Soit intégrer Google Play Billing (abonnement / achat unique), soit retirer la notion
de Premium avant publication. `PremiumGate` mentionne aussi « AI photo scanning » alors
que le README affirme « ni serveur ni IA » (cf. C6).

> *Pourquoi ici :* Décision : Billing ou retrait du Premium.

### 25. C12. Risque de perte totale des données au changement de téléphone 🟠
`AndroidManifest.xml` : `android:allowBackup="false"`. L'app est 100 % locale et la sauvegarde
chiffrée (`BackupRepository`) est **uniquement manuelle** : on ne trouve ni sauvegarde
périodique, ni rappel « dernière sauvegarde il y a X jours ». Un téléphone perdu ou réinitialisé fait perdre
des mois d'historique, de traitements, de pesées, etc.
→ Sauvegarde automatique chiffrée hebdomadaire vers un dossier choisi par l'utilisateur
(SAF, `OpenDocumentTree`, persistable URI) via WorkManager, plus un rappel dans les réglages. Autre option :
une sauvegarde Android chiffrée côté client, avec des règles `data_extraction_rules` qui excluent les clés du Keystore.

> *Pourquoi ici :* Bloquant publication au même titre que C4/C5 : la promesse « tout est local » implique d'assumer la sauvegarde.

### 26. C15. Historique de santé supprimé silencieusement 🟠
Plafonds de rétention par `trim` : humeur et sommeil **730 lignes** (`MoodRepository`,
`SleepRepository`), non-alimentaire 500, et poids, prises de médicaments, journal et scans 5000.
Pour un utilisateur assidu (5 entrées par jour), le journal alimentaire perd ses données les plus anciennes au bout d'environ 2,7 ans,
et l'humeur et le sommeil au bout de 2 ans, **sans avertissement ni proposition d'export**. Ce sont précisément les
données longues qu'un médecin voudrait voir (D5), et qu'aucune sauvegarde automatique ne protège (C12).
→ Remplacer la suppression par un archivage (agrégats mensuels conservés, détail exportable), ou relever les plafonds en
les rendant réglables ; dans tous les cas, prévenir avant la première suppression.

> *Pourquoi ici :* Même sujet que C12 (préservation des données) ; à traiter ensemble avant publication.

### 27. N4 – Monétisation réelle
(cf. C4) : Play Billing ; Premium centré sur Biolism et les
rapports, sans rien retirer à la sécurité (allergènes, rappels et interactions restent
gratuits).

> *Pourquoi ici :* Implémentation de la décision de C4.

### 28. A6 – Accessibilité
Environ 40 `contentDescription = null` : vérifier qu'aucune ne
porte sur une icône de statut ou d'alerte. Valider le contraste AA des couleurs de grade
en mode sombre.

> *Pourquoi ici :* Accessibilité et contraste.

### 29. A4 – Navigation
34 modules dans `presentation/` (fasting, loyalty, seasonal,
calendar, sleep, symptom, expenses…). Proposer à l'onboarding de choisir les modules
suivis, et masquer les autres dans la navigation.

> *Pourquoi ici :* Onboarding « modules suivis » et navigation allégée.

---

## Phase 6 — Valeur produit

Fonctionnalités qui reposent sur des modules existants.

### 30. A8. Recherche Open Food Facts sur l'ancienne API
`OpenFoodFactsApi` utilise `cgi/search.pl`, l'ancien point d'accès de recherche, lent et limité
à environ 10 requêtes par minute par Open Food Facts. Avec un debounce de 150 ms (`FoodSearchViewModel`), la frappe
d'un utilisateur peut atteindre cette limite.
→ Migrer vers l'API Search-a-licious d'Open Food Facts, ou passer à l'API v2 avec filtres, et porter le debounce
de la recherche en ligne à environ 400 ms, en gardant la recherche locale instantanée.

> *Pourquoi ici :* Prérequis de A3 : les alternatives Open Food Facts reposent sur cette recherche.

### 31. A3 – Alternatives venant d'Open Food Facts
Aujourd'hui limitées à l'historique :
un nouvel utilisateur n'a donc jamais d'alternative. Interroger OFF par catégorie
(`categories_tags`) et trier par le score local du profil.

> *Pourquoi ici :* Alternatives Open Food Facts (le score local est stabilisé après les phases 2-3).

### 32. D2 – Garde-manger ↔ dates de péremption
notification J-2, liée au plan de repas
(« cuisiner d'abord ce qui expire »).

> *Pourquoi ici :* Péremption du garde-manger (même infra de worker que D1).

### 33. D3 – Plan de repas → courses → dépenses
les trois modules existent ; il manque le
bouclage « budget prévu vs ticket scanné » (`ReceiptParser` déjà présent).

> *Pourquoi ici :* Plan → courses → dépenses.

### 34. D4 – Signaux croisés symptômes ↔ aliments
`SymptomCorrelation` existe ; ajouter un
journal d'élimination guidé (FODMAP, lactose), à présenter clairement comme une
observation personnelle et non un diagnostic.

> *Pourquoi ici :* Journal d'élimination symptômes ↔ aliments.

---

## Phase 7 — Nouveaux axes

### 35. N1 – Contribution Open Food Facts
aucune écriture vers OFF aujourd'hui. Quand un
produit est absent ou incomplet, proposer d'envoyer photos et champs lus par OCR (API
d'écriture OFF, compte utilisateur ou anonyme).

> *Pourquoi ici :* Contribution Open Food Facts.

### 36. N3 – Scan de carte de restaurant
OCR ML Kit, puis détection allergènes/régime par plat,
en réutilisant `AllergenDetector` et `DietChecker`.

> *Pourquoi ici :* Scan de carte de restaurant (réutilise allergènes/régime durcis en phase 2).

### 37. N5 – Wear OS / tuile
prise de médicament, verre d'eau, chrono de jeûne.

> *Pourquoi ici :* Wear OS / tuile (réutilise les alarmes de C9).

---

## Récapitulatif de l'ordre

- **Phase 1** (Fondations techniques) : C5 → C7 → A5 → C6
- **Phase 2** (Sécurité de l'utilisateur) : C10 → C1 → N6 → C2 → C3 → D1 → C9 → C11 → C13 → C14 → C16
- **Phase 3** (Modèle de messages et de sources) : C8 → A1 → N2 → A2
- **Phase 4** (Contenu santé) : A7 → D6 → C17 → D5
- **Phase 5** (Prêt pour publication) : C4 → C12 → C15 → N4 → A6 → A4
- **Phase 6** (Valeur produit) : A8 → A3 → D2 → D3 → D4
- **Phase 7** (Nouveaux axes) : N1 → N3 → N5
