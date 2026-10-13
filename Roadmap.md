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

## 1. À corriger — priorité haute (vérifié)

### C1. Alertes de sécurité mal ordonnées sur l'écran résultat 🔴
`presentation/result/ResultContent.kt` affiche, dans cet ordre : bandeau de rappel, badges,
anneau de score, verdict, **ComparisonCard, AlternativeCard, PairingsCard**, puis
seulement ensuite **DietVetoBanner** et **AllergenWarningsCard** (l. ~200-216).
Un allergène détecté pour l'utilisateur apparaît donc **sous** des suggestions d'accords
mets/aliments. Pour quelqu'un d'allergique, c'est l'information n°1.
→ Remonter `AllergenWarningsCard`, `DietVetoBanner` et `AllergenUnverifiedBanner` juste
sous `RecallBanner`, avant l'anneau de score.

### C2. Interactions aliment-médicament cachées derrière un bouton 🔴
Les risques médicamenteux du produit scanné (`hints.medicationRisks`, issus de
`checkFoodDrugInteractions`) ne s'affichent que dans `HintPanel`, une boîte de dialogue
fermée par défaut (`showHints = false`). Un utilisateur sous AVK qui scanne des épinards ne
voit rien sans ouvrir le panneau.
→ Bandeau rouge inline « Interaction possible avec <médicament> » (même niveau que C1)
lorsque `medicationRisks` n'est pas vide ; le panneau reste pour le détail.

### C3. Rappels produits jamais revérifiés après le scan 🔴
`RecallRepository` interroge RappelConso **au scan** ; le cache négatif expire en 7 jours
mais n'est réinterrogé qu'**au prochain scan** du même code-barres. `ReminderWorker` ne
contient aucune logique de rappel produit. Un produit acheté, scanné puis rappelé la
semaine suivante ne déclenche **aucune notification**.
→ `RecallSyncWorker` quotidien : requête RappelConso sur les rappels publiés depuis la
dernière synchro, croisés avec les codes-barres de l'historique et du garde-manger ;
notification « Produit rappelé : X — ne pas consommer ». C'est probablement la
fonctionnalité à plus forte valeur sécurité par ligne de code de toute l'app.

### C4. « Premium » sans paiement 🟠
`PremiumSection` propose un bouton qui bascule `isPremium` sur `true`/`false` (`onSetPremium(!isPremium)`).
Aucune dépendance Billing. Toute personne peut s'activer Premium gratuitement depuis les
réglages. En l'état, publier avec un écran « Premium verrouillé » serait trompeur.
→ Soit intégrer Google Play Billing (abonnement / achat unique), soit retirer la notion
de Premium avant publication. `PremiumGate` mentionne aussi « AI photo scanning » alors
que le README affirme « ni serveur ni IA » (cf. C6).

### C5. Migrations Room non vérifiables 🟠
`exportSchema = true`, mais `app/schemas/` ne contient que les versions **15-18, 20-24 et 40**.
Les snapshots **19 et 25 à 39** manquent : `MigrationTestHelper` ne peut pas valider les
15 dernières migrations (40 `Migration(` déclarées dans `AppDatabase.kt`). Les mises à
jour depuis la v1.0 publiée sont le chemin de perte de données le plus probable.
→ Régénérer les schémas manquants en remontant l'historique git (checkout de chaque
version → build KSP), puis écrire un test de migration 15→40.

### C6. Vestiges « LLM » trompeurs 🟡
106 mentions de `LLM` dans le code, dont l'énum `ScanSource.LLM` utilisée pour les
**photos lues sur l'appareil par ML Kit**, des commentaires « OFF-vs-LLM conflict » et le
commentaire de `FoodDb.kt` (« falling back to the LLM's own rough macro guess »). Le README
affirme qu'aucune IA n'est utilisée. D'après la philosophie du projet, toute divergence
entre un commentaire et le comportement réel compte comme un bug.
→ Renommer `ScanSource.LLM` en `ON_DEVICE_OCR` (en gardant la valeur persistée, avec une
migration ou un `@SerializedName`) et purger les commentaires obsolètes.

### C7. Séparateur NUL brut dans le code source 🟡
`data/repository/planning/ManualGroceryRepository.kt` contient 226 octets `0x00`. Le
fichier est vu comme **binaire** par git/grep, car il utilise un caractère NUL littéral comme séparateur
(`split('<NUL>')`) dans DataStore. Les revues, diffs et recherches ignorent silencieusement ce fichier.
→ Écrire `'\u0000'` en échappement, ou mieux, sérialiser en JSON.

### C8. Internationalisation dans le moteur 🟡
233 occurrences de `if (en) "…" else "…"` dans le code (moteur de scoring compris, ex.
`ScoringEngine.kt:578`). Le domaine produit des textes localisés : impossible d'ajouter une
3ᵉ langue, et les traductions échappent aux outils.
→ Le domaine renvoie des codes ou des clés (`WarningKey.NOVA_INFERRED(confidence)`),
et la présentation les résout via `strings.xml`.

### C9. Précision des rappels de médicaments 🟡
Les doses sont rappelées par un `PeriodicWorkRequest` de **15 min** (choix documenté dans
`ScanEatApp.kt`), sans `AlarmManager`. Retard possible de 15 min, et bien plus en Doze.
→ Alarmes exactes (`setExactAndAllowWhileIdle`, permission `SCHEDULE_EXACT_ALARM` /
`USE_EXACT_ALARM`, légitime pour une app de rappel médical) pour les doses ; garder le
worker pour les rappels de repas et d'hydratation.

---

## 2. À améliorer

- **A1 – Transparence des sources dans l'UI.** Les sources existent en commentaires ;
  les remonter en données (`source`, `version`, `verifiedOn` par table ou par entrée) et
  les afficher dans un écran « Sources et versions des données », ainsi qu'en pied de chaque
  alerte (« Source : ANSM, 2024 »). C'est le différenciateur central de la philosophie
  « confiance absolue ».
- **A2 – « Pourquoi cette note ? ».** `AdjustmentsSection` liste déjà les ajustements
  personnels (points ±). Il manque le lien vers la règle et la source de chaque ajustement,
  ainsi que la décomposition de la note classique (`PillarsSection`) en langage simple.
- **A3 – Alternatives venant d'Open Food Facts.** Aujourd'hui limitées à l'historique :
  un nouvel utilisateur n'a donc jamais d'alternative. Interroger OFF par catégorie
  (`categories_tags`) et trier par le score local du profil.
- **A4 – Navigation.** 34 modules dans `presentation/` (fasting, loyalty, seasonal,
  calendar, sleep, symptom, expenses…). Proposer à l'onboarding de choisir les modules
  suivis, et masquer les autres dans la navigation.
- **A5 – Taille des fichiers.** `ScanViewModel` (719 l.), `AppDatabase` (687 l. : extraire
  les 40 migrations dans `Migrations.kt`), `Theme.kt` (627 l.), `ScoringEngine` (604 l.).
- **A6 – Accessibilité.** Environ 40 `contentDescription = null` : vérifier qu'aucune ne
  porte sur une icône de statut ou d'alerte. Valider le contraste AA des couleurs de grade
  en mode sombre.
- **A7 – Couverture de la base médicamenteuse.** `FoodDrugInteractionDb` couvre 5 classes
  (AVK, IMAO, statines/inhibiteurs calciques, IEC/sartans, lévothyroxine). Candidats sourcés
  ANSM/EMA : millepertuis (inducteur CYP3A4), réglisse (antihypertenseurs, hypokaliémie),
  tyramine sous linézolide, alcool sous métronidazole, calcium/fer avec les
  fluoroquinolones/tétracyclines, levure de riz rouge et statines.

## 3. À développer

- **D1 – Synchro RappelConso en arrière-plan** (cf. C3), avec écran « Mes produits
  rappelés ».
- **D2 – Garde-manger ↔ dates de péremption** : notification J-2, liée au plan de repas
  (« cuisiner d'abord ce qui expire »).
- **D3 – Plan de repas → courses → dépenses** : les trois modules existent ; il manque le
  bouclage « budget prévu vs ticket scanné » (`ReceiptParser` déjà présent).
- **D4 – Signaux croisés symptômes ↔ aliments** : `SymptomCorrelation` existe ; ajouter un
  journal d'élimination guidé (FODMAP, lactose), à présenter clairement comme une
  observation personnelle et non un diagnostic.
- **D5 – Rapport médecin** : ajouter interactions détectées, observance des médicaments
  et symptômes sur la période choisie dans `PdfReportRepository`.

## 4. À créer

- **N1 – Contribution Open Food Facts** : aucune écriture vers OFF aujourd'hui. Quand un
  produit est absent ou incomplet, proposer d'envoyer photos et champs lus par OCR (API
  d'écriture OFF, compte utilisateur ou anonyme).
- **N2 – Écran « Sources & méthodologie »** public (cf. A1), lien depuis chaque note.
- **N3 – Scan de carte de restaurant** : OCR ML Kit, puis détection allergènes/régime par plat,
  en réutilisant `AllergenDetector` et `DietChecker`.
- **N4 – Monétisation réelle** (cf. C4) : Play Billing ; Premium centré sur Biolism et les
  rapports, sans rien retirer à la sécurité (allergènes, rappels et interactions restent
  gratuits).
- **N5 – Wear OS / tuile** : prise de médicament, verre d'eau, chrono de jeûne.

---

## Priorisation

| Ordre | Items | Pourquoi |
|---|---|---|
| 1 | **C1, C2, C3** | Sécurité directe de l'utilisateur ; modifications localisées (ordre de composables, un bandeau, un worker). |
| 2 | **C4, C5** | Bloquants avant toute publication ou mise à jour (honnêteté de l'offre, perte de données). |
| 3 | A1, A2, N2 | Concrétiser la promesse « information sourcée ». |
| 4 | C6, C7, C8, C9 | Dette qui contredit la doc ou fragilise l'outillage. |
| 5 | A3, A7, D1-D5 | Valeur produit. |
| 6 | N1, N3, N4, N5 | Nouveaux axes. |
