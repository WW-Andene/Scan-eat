# Roadmap — Audit Scan'eat (2026-10-13)

Audit rapide (~5 min) de `scan-eat-android/app` : ~630 fichiers Kotlin, ~80 k lignes,
Room v40, Compose + Hilt, 100 % local (Open Food Facts + ML Kit). Debug, compilation et
tests ont été volontairement mis de côté ; l'accent porte sur la valeur produit et la
fiabilité des données (philosophie « confiance absolue » du README).

## Constat général

- **Points forts** : moteur de scoring riche et contextuel (nutrition, non-consommables,
  interactions aliment-médicament, Biolism), nombreux trackers (poids, hydratation, jeûne,
  humeur, dépenses, repas, médicaments), design system base-2, widget, Health Connect,
  sauvegarde, rappels produits (Recall).
- **Faiblesse structurelle** : l'app est devenue une « super-app » de ~20 écrans. La
  promesse centrale (scanner → note fiable et personnalisée) se dilue ; la dette porte
  surtout sur la **traçabilité des sources**, la **découvrabilité** et la **cohérence**
  entre modules, plus que sur le code lui-même.

---

## 1. Ce qui peut être corrigé (priorité haute)

| # | Sujet | Constat | Action |
|---|-------|---------|--------|
| C1 | Sources des bases embarquées | `FoodDb*`, `DrugFactsDb`, `FoodDrugInteractionDb`, `CosingRegulatoryDb`, `OfficialRecipeDb` sont du code Kotlin codé en dur. Pas de métadonnée uniforme (source, version, date de vérification) par entrée. | Ajouter un champ `source`/`verifiedOn` obligatoire à chaque entrée + un test d'invariant qui échoue si une entrée n'en a pas. Afficher la source dans l'UI (cf. D1). |
| C2 | Fraîcheur des données | Les tables nutritionnelles/médicamenteuses évoluent (CIQUAL, ANSM). Aucun mécanisme de version visible. | Exposer « version des données : CIQUAL 2020 / ANSM jj/mm/aaaa » dans *À propos* ; planifier une revue semestrielle. |
| C3 | Migrations Room | `fallbackToDestructiveMigration()` en DEBUG uniquement — bien, mais 40 versions de schéma : risque élevé sur les vieux installs. | Vérifier que `exportSchema` + `MigrationTestHelper` couvrent toutes les chaînes 1→40 ; documenter la politique. |
| C4 | Accessibilité | ~40 `contentDescription = null`, dont certaines sur des icônes porteuses de sens (statut, alerte). | Passer en revue : `null` seulement pour le décoratif ; libellés pour badges de risque, scores, boutons icône. |
| C5 | Chaînes en dur | ~100 littéraux français dans `presentation/` hors `strings.xml`, alors qu'un `values-en` existe. | Extraire vers `strings.xml` ; l'anglais est actuellement partiel → incohérent pour l'utilisateur anglophone. |
| C6 | Données OFF incomplètes | Une note calculée sur un produit OFF partiel peut paraître aussi fiable qu'une note complète. | Indicateur de **complétude/confiance** systématique sur chaque résultat (« note basée sur 6/9 champs »). |

## 2. Ce qui peut être amélioré

- **A1 – Hiérarchie de navigation** : regrouper les ~20 modules en 3 piliers (*Scanner*,
  *Mon suivi*, *Ma santé*) ; masquer les modules non activés par défaut (onboarding « que
  voulez-vous suivre ? »).
- **A2 – Écran résultat** : présenter d'abord le verdict personnalisé (« OK pour vous /
  attention : interaction avec X »), puis le détail. Les alertes médicales doivent toujours
  être au-dessus de la ligne de flottaison.
- **A3 – Mode hors-ligne** : un cache OFF existe (`OnlineSearchCacheDao`) ; le rendre
  explicite (badge « hors-ligne, données du jj/mm ») et pré-cacher les produits favoris.
- **A4 – Fichiers volumineux** : `ScanViewModel` (719 l.), `AppDatabase` (687 l.),
  `Theme.kt` (627 l.), `ScoringEngine` (604 l.) → découper par responsabilité
  (chargement, scoring, état UI) pour faciliter les audits « par sections » prônés par le README.
- **A5 – Performance perçue** : skeletons pendant le lookup OFF, scan continu sans retour
  d'écran, retour haptique à la détection.
- **A6 – Dark mode / contraste** des couleurs de score (Nutri-Score-like) à valider WCAG AA.

## 3. Ce qui peut être développé (fonctionnalités existantes à pousser)

- **D1 – « Pourquoi cette note ? »** : panneau explicatif traçant chaque malus/bonus vers
  sa règle et sa source (EFSA, ANSES…). C'est la concrétisation directe de la philosophie
  de confiance et le différenciateur n°1 face à Yuka.
- **D2 – Alternatives meilleures** : depuis un produit mal noté, proposer des produits OFF
  de la même catégorie mieux notés *pour ce profil*.
- **D3 – Interactions médicaments** : étendre aux compléments alimentaires (millepertuis,
  pamplemousse déjà ?, vitamine K, levure de riz rouge) avec sources ANSM/EMA.
- **D4 – Health Connect** : bidirectionnel (export nutrition/hydratation, pas seulement import
  poids/activité).
- **D5 – Rappels produits (Recall)** : notifier si un produit **déjà scanné/historique** fait
  l'objet d'un rappel RappelConso — très forte valeur sécurité.
- **D6 – Planification repas ↔ liste de courses ↔ dépenses** : relier les trois modules
  existants (plan → liste → ticket scanné → budget réel vs prévu).
- **D7 – Widget** : passer à Glance avec score du jour, hydratation, bouton scan direct.
- **D8 – Rapport médecin** (`presentation/report`) : export PDF propre, période choisie,
  pour consultation diététicien/médecin.

## 4. Ce qui peut être créé (nouveau)

- **N1 – Contribution Open Food Facts** : quand un produit manque ou est incomplet, envoyer
  photos + OCR ML Kit à OFF (API write). Améliore les données pour tous et la note locale.
- **N2 – Mode famille / multi-profils** : plusieurs profils santé sur un appareil (enfant
  allergique, parent sous traitement) ; scan évalué pour chaque membre.
- **N3 – Scan de menu / restaurant** : OCR d'une carte → plats signalés selon allergies/régime.
- **N4 – Comparateur** : scanner 2–3 produits en rayon et les comparer côte à côte.
- **N5 – Journal des sources** : écran public listant toutes les bases, versions et
  références scientifiques utilisées (transparence, argument Play Store).
- **N6 – Synchronisation chiffrée optionnelle** (backup E2E vers Drive/fichier) pour changer
  de téléphone sans perte — l'app est 100 % locale, c'est le principal risque utilisateur.
- **N7 – Wear OS** : hydratation, jeûne, rappels médicaments au poignet.

---

## Priorisation proposée

1. **Confiance** : C1, C6, D1, N5 (traçabilité des sources de bout en bout).
2. **Sécurité utilisateur** : D5, D3, C4.
3. **Clarté produit** : A1, A2, C5.
4. **Croissance** : N1, D2, N2, N4.
5. **Écosystème** : D4, D7, N6, N7.
