# Design Audit — Étape 8 : Composants & forme

**Sources** : design-aesthetic-audit §DCO1-6 (Components) · app-audit §E5 (Component Visual Quality) · art-direction-engine §SHAPE + §COMPOSITION + section Components.
**Statut** : audit/synthèse, **aucune modification de code**.

---

## 1. §DCO1-2 — Boutons & cartes

- **Boutons** (`ScanEatButton.kt`) : primaire (fill corail, texte noir semibold, 12dp) et outlined. Interaction custom via `pressScale()` (échelle 1↔0.97, 100ms) plutôt que le ripple Material par défaut seul — bon détail de matière cohérent avec le personnage "posé" (Étape 2).
- **Cartes** (`ScanEatCard.kt`) : un seul primitif avec 3 niveaux d'emphase (`HeroGlassSpec`/`PrimaryGlassSpec`/`SecondaryGlassSpec`), chacun avec son propre triplet glow/edge/élévation. C'est une hiérarchie de carte bien pensée — la conformité au principe "un focal point par écran" (art-direction-engine §CHECK) dépend de la discipline d'usage (une seule carte Hero par écran), non vérifiée ici sans revue écran-par-écran.

## 2. §DCO3-4 — Champs, inputs, navigation

- **Recherche** (`ScanEatSearchField.kt`) — composant dédié, pas un `TextField` Material nu.
- **Navigation** (rappel Étape 1) : chrome flottant en verre (top bar + bottom nav détachés) — signature la plus forte de l'app, déjà traitée aux Étapes 2-3.
- **Filtres** : convertis en menus popup (`CollapsibleFilterBar` + `DropdownMenu`) lors d'un travail récent dans cette même session — cohérent, un seul pattern réutilisé sur FoodSearch/History/Recipes/Templates.

## 3. §DCO5 — États des composants (recoupement avec l'agent d'exploration état)

Rappel des preuves collectées :
- **Erreur** : `ErrorBanner.kt` est un composant "banni de l'anti-pattern" au sens art-direction-engine (pas de boîte rouge générique) — utilise `semanticRed()` à 15% alpha, icône, `shadowElevation`, live-region TalkBack assertive. **Mais seulement 3 sites d'appel identifiés** malgré un commentaire de code qui en implique un usage plus large (vétos alimentaires, échecs réseau). Écran-par-écran, plusieurs flux d'erreur potentiels (échecs de chargement `FoodSearchScreen`, listes) n'ont pas de preuve d'utiliser ce composant.
- **Succès** : `ScanEatSnackbarHost.kt` custom (couleurs de marque) + un tier dédié `CelebrationSnackbarVisuals` (accent Gold + icône trophée) pour les jalons — un vrai geste de célébration distinctif, à mentionner positivement (recoupe potentiellement avec F6/l'engagement).
- **Chargement** : très majoritairement des indicateurs Material par défaut simplement teintés — pas de traitement "personnage" spécifique. La seule exception notable est l'anneau de score (`ScoreDisplay.kt`/`ResultScreen.kt`), qui détourne `CircularProgressIndicator` en jauge de score animée — un bon geste, mais ce n'est pas conçu comme un "loading state" en soi, c'est un composant de data-viz qui emprunte la primitive de chargement.
- **Empty states** : centralisés (`EmptyListState.kt`), icône teintée à l'accent + message + CTA optionnel — évite explicitement l'anti-pattern "texte gris" (documenté dans le composant lui-même).

## 4. §DCO6 — Duplication de composants (constat de code, pertinent pour la qualité visuelle)

L'agent d'exploration a révélé une duplication directe : `EvolutionComponents.kt` (Biolism) et `WeightHistorySection.kt` (Weight) implémentent chacun, indépendamment, un graphique en ligne Canvas quasi identique (aire remplie + trait + marqueurs de points) — même technique, même structure, deux fichiers séparés. Ce n'est pas seulement un problème de code (duplication) mais un risque visuel : si l'un des deux est retouché (couleur, épaisseur de trait, style de marqueur) sans retoucher l'autre, une incohérence visuelle apparaîtra entre le graphique Biolism et le graphique Poids — deux endroits qui devraient visuellement se répondre.

De même, les 3 spinners inline dans les dialogues Recipes (`ImportRecipeUrlDialog.kt`, `RecipesImportStateDialogs.kt`, `SuggestRecipesDialog.kt`) répètent le même pattern `IconSize.Inline` copié-collé plutôt que d'être un seul composant partagé — pas une divergence visuelle actuelle, mais un risque de dérive future identique au F1/F1-bis de l'Étape 1 (12 valeurs de rayon avant consolidation).

## 5. §SHAPE — Langage de forme (art-direction-engine)

Rappel Étape 1 : 3 paliers (12/16/20dp), tous arrondis, aucun élément anguleux/clip-path, aucune forme organique SVG. C'est une échelle cohérente et consolidée (bon point), mais entièrement dans le registre "rounded corners" — aucun élément non-rectangulaire n'existe dans toute l'app (recommandation art-direction-engine §SHAPE : "introduire au moins un élément non-rectangulaire" pour la distinction — actuellement absent). L'anneau de score (`ScoreRing`, cercle) est la seule forme non-rectangulaire trouvée dans tout l'audit.

## 6. §COMPOSITION — Modèle spatial

Navigation en overlay flottant (top+bottom), contenu en colonne simple scrollable par écran — pas de modèle "asymétrique" ou "split doré" (60/40, golden split) au niveau de la mise en page générale ; chaque écran suit un modèle centré/pleine-largeur standard. Cohérent avec l'absence totale de responsive/adaptive (cf. agent d'exploration : zéro `WindowSizeClass`, layout fixe portrait) — un modèle de composition à la fois sûr et sans audace, ce qui rejoint le constat "discret dans la structure" de l'Étape 2.

---

## 7. Findings — Étape 8

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F21** | `ErrorBanner.kt` (composant custom bien conçu) n'a que 3 sites d'appel confirmés malgré une intention de code plus large — écart d'adoption probable sur d'autres écrans avec états d'erreur (FoodSearch, listes). | Moyenne | À vérifier écran par écran avant fix |
| **F22** | Duplication de code ET de risque visuel entre `EvolutionComponents.kt` (Biolism) et `WeightHistorySection.kt` (Weight) — deux graphiques en ligne quasi identiques implémentés indépendamment. | Moyenne | Candidat à une consolidation en composant partagé |
| **F23** | Aucun élément de forme non-rectangulaire dans toute l'app en dehors de l'anneau de score — langage de forme cohérent mais entièrement "safe", sans geste distinctif de forme. | Basse | Lié à F9 (Design DNA pauvre en surface mémorable) |
| **F24** | Les indicateurs de chargement restent 100% Material par défaut (simplement teintés), contrairement aux états d'erreur et de succès qui sont, eux, entièrement custom — incohérence de traitement entre les 3 états. | Basse-Moyenne | Le "loading" est le seul état des 3 qui n'a pas reçu de traitement "personnage" |

---

## 8. Ce qui n'est PAS remis en cause

- Les 3 niveaux d'emphase de carte (`GlassSpec` Hero/Primary/Secondary) sont un bon système de hiérarchie visuelle — à conserver.
- `pressScale()` sur les boutons est un détail d'interaction cohérent avec le personnage "posé" — à préserver.
- Le `CelebrationSnackbarVisuals` (jalons, accent Gold + trophée) est un vrai bon geste de délice/reconnaissance — à valoriser, pas à toucher.

---

**Prochaine étape** : Étape 9 — Interaction, motion & états (design-aesthetic-audit §DM1-5 + §DST1-4, app-audit §E6, art-direction-engine Interaction + §BUILD États).
