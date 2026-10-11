# Design Audit — Étape 5 : Fondations — architecture des tokens

**Sources** : design-aesthetic-audit §DTA1-2 (Token Architecture) · app-audit §E1 (Design Token System) · art-direction-engine §TOKENS.
**Statut** : audit/synthèse, **aucune modification de code**.

---

## 1. §DTA1 — Inventaire des couches actuelles

art-direction-engine décrit une architecture idéale à 3 couches (Primitives → Sémantique → Composant). Voici où se situe Scan'eat aujourd'hui, fichier par fichier :

| Couche théorique | Ce qui existe chez Scan'eat | Fichier |
|---|---|---|
| **1. Primitives** | Absent en tant que couche distincte — les valeurs brutes (hex, dp) sont écrites directement comme constantes nommées, pas comme variables de base réutilisées par dérivation | `Colors.kt` (hex directs), `Spacing.kt`/`CardRadius.kt`/`IconSize.kt` (dp directs) |
| **2. Sémantique** | Présent et bien fait — `TextSecondary`, `TextMuted`, `SeparatorHeavy` dérivés d'`OnBackground` avec alpha ; `semanticGreen/Red/Amber/Blue()` qui basculent selon le mode daltonisme/luminosité | `Colors.kt` |
| **3. Composant** | Présent et bien fait — `GlassSpec` (Hero/Primary/Secondary), `CardRadius.CARD` utilisé dans `ScanEatCard`, `CardRadius.CONTROL` dans `ScanEatButton` | `ScanEatCard.kt`, `ScanEatButton.kt` |

**Constat** : les couches 2 et 3 sont solides. La couche 1 (primitives) est la plus faible — il n'existe pas de "table de base" (ex : une seule teinte de départ + décalages calculés) dont tout le reste dériverait mathématiquement. Chaque hex est saisi indépendamment plutôt que dérivé d'une primitive commune.

---

## 2. §DTA2 — Cohérence inter-couches et dérive documentée

Deux dérives déjà repérées à l'Étape 1 relèvent directement de cette étape :

- **F1 (rappel)** : l'échelle d'espacement documentée en tête de `Colors.kt` (φ = 4/6/10/16/26/42/68) ne correspond pas à l'échelle réellement implémentée dans `Spacing.kt` (4/8/10/12/16/24/32). C'est une preuve directe d'absence de primitive partagée — la couche sémantique/composant a évolué sans que la documentation de couche 1 suive.
- **Nouveau constat (F12)** : `Colors.kt` mélange couche sémantique et couche de composant dans un même fichier (270 lignes) sans séparation de section claire au niveau du code (uniquement via des commentaires). `Theme.kt` (243 lignes) contient à la fois la définition des 5 `ColorScheme` Material3 (couche sémantique) et des couleurs "hand-tuned" ad hoc comme `LightGoldAccent` (couche composant/exception). Le mélange rend difficile de savoir, pour une nouvelle couleur, à quelle couche elle appartient.

---

## 3. Comparaison avec art-direction-engine §TOKENS (modèle de référence)

Le modèle de référence propose :
```css
--hue-base: 245;  --chroma-bg: 0.015;  --radius-base: 6px;  --space-base: 6px;
```
c'est-à-dire des primitives numériques minimales dont tout dérive par calcul (`calc()`). Scan'eat, en Kotlin/Compose, n'a pas d'équivalent — pas de fonction `deriveSurface(baseHue, step)` par exemple. Chaque `SurfaceVariant`, chaque niveau d'élévation Biolism (Gold/GoldDim/GoldGlow/GoldBorder/GoldHaze/GoldTrace) est une constante hex saisie à la main, même si la relation mathématique (paliers d'alpha en puissances de φ) est bien réelle et documentée en commentaire.

**Ce que cela signifie concrètement** : si l'utilisateur souhaite un jour retoucher la teinte de base de Biolism (par exemple si F3 est tranché en faveur d'un rapprochement avec l'accent Scan'eat), il faudra retoucher à la main chacun des ~6 hex Gold, ~6 hex Teal, ~6 hex Violet (18 valeurs), au lieu de changer une seule primitive de hue et voir les 18 valeurs se recalculer.

---

## 4. Findings — Étape 5

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F12** | Pas de séparation nette (même en commentaire de section) entre couche sémantique et couche composant dans `Colors.kt`/`Theme.kt` — augmente le risque qu'une nouvelle couleur soit ajoutée au mauvais niveau. | Basse-Moyenne | Fix léger : réorganisation de commentaires, pas de logique |
| **F13** | Absence de couche "primitives" calculée : les 18 valeurs Biolism (Gold/Teal/Violet × 6 paliers) sont saisies à la main plutôt que dérivées d'une seule teinte de base + une fonction d'alpha. Fonctionnellement correct aujourd'hui, mais coûteux à maintenir si la teinte de base doit changer. | Moyenne | Rejoint F3 (relation Coral/Biolism) |
| **F1 (rappel de l'Étape 1)** | Écart entre l'échelle φ documentée et l'échelle réellement implémentée dans `Spacing.kt`. | Moyenne | À trancher : la doc était-elle une intention jamais suivie, ou l'implémentation a-t-elle dérivé après coup ? |

---

## 5. Ce qui n'est PAS remis en cause

- La couche sémantique (`TextSecondary`, `semanticGreen()`, etc.) est un exemple solide de bonne pratique — à conserver telle quelle.
- La couche composant (`GlassSpec`, `CardRadius` appliqué dans les primitives de bouton/carte) fonctionne bien et n'a pas besoin d'être réécrite, seulement clarifiée en documentation de structure.

---

**Prochaine étape** : Étape 6 — Couleur & atmosphère (fusionné) : design-aesthetic-audit §DC1-5 + §DSA1-5, app-audit §E3, art-direction-engine §COLOR/§ATMOSPHERE/§DEPTH/§LIGHT/§TEXTURE.
