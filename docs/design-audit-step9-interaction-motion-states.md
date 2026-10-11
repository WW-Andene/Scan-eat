# Design Audit — Étape 9 : Interaction, motion & états

**Sources** : design-aesthetic-audit §DM1-5 (Motion) + §DST1-4 (State Design) · app-audit §E6 (Interaction Design Quality) · art-direction-engine section Interaction + §BUILD phase États.
**Statut** : audit/synthèse, **aucune modification de code**. Recoupe et approfondit les Étapes 1 et 8.

---

## 1. §DM1-2 — Vocabulaire de mouvement

Rappel des preuves (Étape 1) : une seule easing signature (`ScoreRevealEasing`, cubic-bezier "quiet, confident"), réutilisée sur 6+ sites ; 6 durées distinctes (100/200/300/420/700/26000ms) ; aucun `spring(` ; `snap()` en fallback reduced-motion sur 3 sites.

**Évaluation §DM1 (caractère du mouvement)** : le vocabulaire est délibérément sobre — cohérent avec le personnage "posé" de l'Étape 2. Ce n'est pas un défaut, c'est une décision de personnage cohérente. Le risque n'est pas "trop de mouvement" mais l'inverse : un excès de sobriété peut se lire comme "sans vie" si aucun moment n'est laissé pour un geste plus expressif (voir §DM5 ci-dessous).

## 2. §DM3-4 — Cohérence et application

- 100ms (press) / 200ms (tab-switch) / 300ms (nav slide) forment un groupe cohérent pour les interactions rapides — bon point.
- 420ms (hero entrance) et 700ms (score reveal) sont chacun utilisés à un seul site d'appel (rappel F4, Étape 1) — pas des tokens partagés. Ce n'est pas un problème aujourd'hui (2 valeurs, pas 10), mais sans en faire des constantes nommées comme `ScoreRevealEasing` l'a été pour l'easing, un 3ᵉ animateur pourrait introduire un 730ms ou un 380ms par accident plutôt que de réutiliser l'un des deux existants.

## 3. §DM5 — Signature de mouvement (l'"unique geste" qui définit le produit)

art-direction-engine demande explicitement un **signature animation** — "le SEUL mouvement qui définit le produit". Candidats actuels :
- Le hero entrance (fade + scale 0.94→1, 420ms) — utilisé largement mais pas unique à un contexte précis.
- Le score reveal (700ms) — plus proche d'un geste signature réel puisqu'il est lié au moment le plus important de l'app (résultat d'un scan) — candidat naturel si l'équipe veut désigner UN mouvement comme "la signature Scan'eat".

**Constat** : aucun de ces mouvements n'est explicitement désigné comme signature dans le code (pas de commentaire "this is THE brand animation"). C'est une opportunité manquée de renforcer l'identité (rejoint F9/F19 — pauvreté de surface mémorable) à un coût d'implémentation quasi nul (il s'agit surtout de décision et de documentation, le mouvement existe déjà).

## 4. §DST1-4 — Design des états (empty / loading / error / success)

Synthèse croisée avec l'agent d'exploration (recoupe et étend le Finding F24 de l'Étape 8) :

| État | Traitement | Personnage exprimé ? |
|---|---|---|
| **Empty** | `EmptyListState.kt` — icône teintée accent + message + CTA optionnel | Oui, cohérent (évite l'anti-pattern gris) |
| **Loading** | `CircularProgressIndicator`/`LinearProgressIndicator` Material par défaut, simplement teintés ; seule exception = l'anneau de score qui détourne la primitive de chargement en jauge | **Non** — le seul état sans design "personnage" propre |
| **Error** | `ErrorBanner.kt` custom (semanticRed 15%, icône, live-region) | Oui, mais adoption incomplète (F21, seulement 3 sites confirmés) |
| **Success** | `ScanEatSnackbarHost.kt` custom + tier `CelebrationSnackbarVisuals` (Gold + trophée) pour les jalons | Oui, et même au-delà du minimum (célébration dédiée) |

**Conclusion §DST** : 3 des 4 états ont reçu un traitement de personnage explicite ; le "loading" reste le parent pauvre. Pour une app dont la promesse de marque hypothétique inclut la patience/l'accompagnement ("Sage bienveillant"), un état de chargement générique est une occasion manquée — c'est précisément le moment où l'utilisateur attend et où un geste rassurant/caractéristique aurait le plus de valeur perçue.

## 5. Interaction — au-delà du motion pur

- `pressScale()` sur les boutons est le seul feedback tactile custom identifié au-delà du ripple Material — cohérent, mais limité aux boutons (pas de preuve de feedback équivalent sur les cartes cliquables, chips, ou lignes de liste).
- Aucune preuve de gestes personnalisés (swipe-to-delete stylé, drag-and-reorder animé) au-delà des interactions Compose standard — cohérent avec le personnage "posé", mais à confirmer que ce n'est pas simplement un oubli plutôt qu'un choix.

---

## 6. Findings — Étape 9

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F25** | Aucun mouvement n'est explicitement désigné comme "signature animation" du produit malgré de bons candidats déjà existants (score reveal 700ms) — opportunité à coût quasi nul de renforcer l'identité. | Moyenne | Décision + documentation, peu de code à changer |
| **F24 (approfondi)** | Le "loading" reste le seul des 4 états sans traitement de personnage propre — incohérent avec l'effort mis sur empty/error/success. Le moment d'attente est précisément celui où un geste caractéristique aurait le plus d'impact perçu. | Moyenne | Candidat concret pour l'Étape 12/implémentation |
| **F26** | 420ms et 700ms restent des valeurs à site d'appel unique, non nommées comme constantes partagées (rappel F4). | Basse | Fix mécanique simple si voulu |
| **F27** | Feedback tactile custom (`pressScale`) limité aux boutons — pas de preuve d'un traitement équivalent sur cartes/chips/lignes de liste cliquables. | Basse | À vérifier avant fix |

---

## 7. Ce qui n'est PAS remis en cause

- La sobriété générale du motion est une décision de personnage cohérente, pas un défaut à corriger en ajoutant du mouvement partout.
- `CelebrationSnackbarVisuals` reste un exemple positif fort, à préserver et potentiellement à citer comme modèle pour combler le vide du "loading".
- Le respect du reduced-motion (snap() sur 3 sites) est un acquis à ne jamais régresser.

---

**Prochaine étape** : Étape 10 — Hiérarchie & responsive (design-aesthetic-audit §DH1-4 + §DRC1-3, app-audit §E2).
