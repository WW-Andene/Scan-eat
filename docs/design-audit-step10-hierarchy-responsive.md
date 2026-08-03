# Design Audit — Étape 10 : Hiérarchie & responsive

**Sources** : design-aesthetic-audit §DH1-4 (Hierarchy & Gestalt) + §DRC1-3 (Responsive Character) · app-audit §E2 (Visual Rhythm & Spatial Composition).
**Statut** : audit/synthèse, **aucune modification de code**.

---

## 1. §DH1-2 — Hiérarchie visuelle & poids

- Système de 3 niveaux de carte (Hero/Primary/Secondary via `GlassSpec`) fournit une base objective de hiérarchie — chaque écran devrait avoir au plus une carte Hero. Non vérifié écran-par-écran dans cet audit (nécessiterait une revue visuelle par capture, hors scope read-only-code) — **à confirmer visuellement avant l'implémentation de tout fix**.
- Hiérarchie typographique (15 slots, poids Bold→Normal, tracking différencié) est cohérente et fournit un socle solide pour la lecture — confirmé à l'Étape 7.
- `HeroNumberStyle` (Black, tabular) crée un point focal net pour les chiffres héros — bon exemple de hiérarchie par le poids plutôt que seulement par la taille.

## 2. §DH3 — Rythme spatial

Rappel Étape 1 (Finding F1) : l'échelle d'espacement réellement implémentée (`Spacing.kt` : 4/8/10/12/16/24/32) ne suit pas de progression géométrique stricte — le saut 4→8 (×2) puis 8→10 (×1.25) puis 10→12 (×1.2) puis 12→16 (×1.33) puis 16→24 (×1.5) puis 24→32 (×1.33) est irrégulier. Une échelle rythmique cohérente (géométrique, comme le φ documenté en intention, ou une échelle modulaire classique) crée un rythme visuel plus prévisible qu'une suite de valeurs arrondies "au jugé".

**Règle de contraste spatial** (art-direction-engine §COMPOSITION) : "l'écart ENTRE sections doit être 3-5× l'écart DANS un composant". Non vérifiable précisément sans revue visuelle écran-par-écran, mais avec seulement 7 paliers dont le plus grand (32dp = XXL) n'est que 8× le plus petit (4dp = XS), la marge disponible pour un contraste net entre "espacement interne" et "respiration entre sections" est structurellement limitée comparée à des échelles qui vont jusqu'à des paliers "section" bien plus larges (48/64/96dp).

## 3. §DH4 — Un point focal par écran

Non vérifiable de façon fiable en lecture de code seule (dépend du rendu réel) — **marqué explicitement comme nécessitant une vérification visuelle** (captures d'écran ou exécution de l'app) avant toute conclusion définitive ou fix.

## 4. §DRC1-3 — Caractère responsive

Rappel de l'agent d'exploration : **zéro système responsive/adaptatif** dans toute l'app.
- Aucun `WindowSizeClass`, aucun `BoxWithConstraints` utilisé pour de la mise en page adaptative, aucune logique tablette/foldable.
- Les deux seules utilisations de `LocalConfiguration` (`ScanShelfOverlay.kt`, `ScanHeaderOverlay.kt`) servent uniquement à clamper la position d'un chip dans la vue caméra — pas de l'adaptation de mise en page.
- **Conséquence directe pour §DRC** : la question "le personnage visuel reste-t-il cohérent à travers les tailles d'écran" est **sans objet aujourd'hui** — il n'y a qu'une seule taille d'écran prise en charge (portrait téléphone). Sur tablette ou en mode fenêtré/multi-fenêtre Android (de plus en plus courant, notamment sur Samsung DeX, Chromebooks, pliables), l'app s'affichera très probablement en layout téléphone étiré, ce qui dégraderait potentiellement le rythme spatial et la lisibilité du chrome flottant (top/bottom bars conçues pour une largeur téléphone).

Ceci recoupe app-audit §H3 (Mobile & Touch) et §E11 (Mobile-Specific), qui seront synthétisés à l'Étape 12.

---

## 5. Findings — Étape 10

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F28** | L'échelle d'espacement réelle n'est pas géométriquement régulière (progressions irrégulières entre 4dp et 32dp), limitant la marge de contraste rythmique "section vs. composant" recommandée par art-direction-engine. | Basse-Moyenne | Rejoint F1/F13 (couche primitives faible) |
| **F29** | Absence totale de support responsive/adaptatif (`WindowSizeClass`, tablette, multi-fenêtre) — l'app est un layout fixe téléphone-portrait uniquement. Sur tablette/pliable/DeX, dégradation probable du chrome flottant et du rythme spatial. | **Moyenne-Haute** | Dépend du % réel d'utilisateurs tablette/pliable — à quantifier avant de prioriser un fix |
| **F30** | La vérification du "un point focal par écran" (§DH4) et de la discipline Hero/Primary/Secondary en usage réel nécessite une revue visuelle (captures ou exécution de l'app), non réalisable en lecture de code seule. | Info | Action de suivi : revue visuelle avant tout fix de hiérarchie |

---

## 6. Ce qui n'est PAS remis en cause

- La hiérarchie typographique (poids, tracking) reste solide et n'a pas besoin d'être retouchée.
- Le système de 3 niveaux de carte est un bon cadre — le problème potentiel (si confirmé visuellement) serait dans la discipline d'usage, pas dans le système lui-même.

---

**Prochaine étape** : Étape 11 — Iconographie, illustration, data viz & copy×visuel (design-aesthetic-audit §DI1-4 + §DIL1-3 + §DDV1-3 + §DCVW1-3, app-audit §E10).
