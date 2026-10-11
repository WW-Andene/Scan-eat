# Design Audit — Étape 2 : Personnage de design (Character Brief)

**Sources** : design-aesthetic-audit §DP1 (Dimensions) · §DP2 (Character Brief) · §DP3 (Deepening protocol).
**Statut** : audit/synthèse, **aucune modification de code**. S'appuie sur les preuves factuelles de l'Étape 1 (`docs/design-audit-step1-identity-profile.md`).

---

## 1. §DP1 — Dimensions de personnage (positionnement sur des axes bipolaires)

Chaque axe est noté à partir des preuves de l'Étape 1, pas d'une impression.

| Dimension | Pôle A | Pôle B | Position Scan'eat | Preuve |
|---|---|---|---|---|
| **Chaud ↔ Froid** | Chaud (organique, accueillant) | Froid (clinique, précis) | **Chaud, mais tempéré** | AccentCoral (#D97C56) comme accent principal ; fond OLED noir pur en défaut, pas un fond chaud — chaleur portée par l'accent seul, pas par l'atmosphère |
| **Ludique ↔ Sérieux** | Ludique (rebond, surprise) | Sérieux (posé, prévisible) | **Sérieux / posé** | Aucun `spring(`, une seule easing signature réutilisée partout (`ScoreRevealEasing`, "quiet, confident" selon son propre commentaire), pas de micro-surprises visuelles |
| **Minimal ↔ Maximaliste** | Minimal (peu d'éléments) | Maximaliste (dense, riche) | **Modéré, tirant vers dense** | 25+ écrans, 112 fichiers de composants, densité de fonctionnalités élevée (Biolism, jeûne, médication, dépenses) — mais chaque écran individuel reste discipliné (tokens propres, 3 paliers de rayon) |
| **Artisanal ↔ Industriel** | Artisanal (fait main, texturé) | Industriel (systématique, standardisé) | **Industriel avec un vernis artisanal** | Système de tokens rigoureux (spacing/radius/icon size consolidés avec traçabilité) = industriel ; le glassmorphism (`glassSheen`, `ambientGloom`) apporte la seule touche "faite main" |
| **Discret ↔ Expressif** | Discret (s'efface) | Expressif (revendique une présence) | **Discret dans la structure, expressif dans la matière** | Navigation/typo/icônes 100% standard Material = discret ; le chrome flottant en verre flouté = le seul geste expressif de l'app |
| **Confiance clinique ↔ Confiance chaleureuse** | Clinique (données, précision) | Chaleureuse (accompagnement, bienveillance) | **Hybride, tiraillé** | Figures tabulaires + `FontWeight.Black` pour les nombres héros (clinique/précis) coexistent avec l'accent corail chaud et le glass "premium" — l'app n'a pas encore choisi son camp |

---

## 2. §DP2 — Character Brief

```
━━━ CHARACTER BRIEF — SCAN'EAT ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NOM DE PERSONNAGE PROPOSÉ : "Le compagnon posé"
  (à valider/renommer avec l'utilisateur — première formulation de travail)

CE QUE L'APP EST AUJOURD'HUI (extrait, pas prescrit) :
  Une app de suivi nutritionnel qui se comporte comme un instrument de
  précision (chiffres, scores, TDEE, figures tabulaires) habillé d'une
  matière chaude et translucide (verre dépoli, accent corail). Le
  mouvement est sobre, la structure est standard Material, et la seule
  vraie signature vient de la navigation flottante en verre flouté —
  pas de la typo, pas des icônes, pas d'illustration.

TENSION CENTRALE NON RÉSOLUE :
  L'app hésite entre deux promesses : "je suis un outil clinique fiable"
  (chiffres nets, figures tabulaires, sérieux du motion) et "je suis un
  compagnon premium chaleureux" (glass, corail, dorure Biolism). Ces
  deux promesses ne sont pas incompatibles, mais elles ne sont
  actuellement reliées par aucun principe explicite — chaque écran
  choisit implicitement son dosage.

CE QUE LE PERSONNAGE DEVRAIT COMMUNIQUER (hypothèse à valider) :
  "Cette app prend vos données de santé au sérieux, et vous le montre
  sans jamais être froide avec vous." — précision sans clinique,
  chaleur sans infantilisation.

VALEURS PORTÉES (déduites du code, pas du marketing) :
  - Respect de l'accessibilité au-delà du minimum légal (5 color schemes,
    Okabe-Ito, reduced-motion réel) → "cette app inclut, ne trie pas."
  - Discipline de token quasi parfaite → "cette app est du travail sérieux,
    pas du bricolage."
  - Absence totale d'illustration/mascotte/police de marque → risque de
    lire comme "une app parmi d'autres" au premier coup d'œil, malgré
    un soin réel en profondeur.

SIGNATURE ACTUELLE (unique) :
  Le "chrome" flottant en verre flouté (top bar + bottom nav détachés,
  coins arrondis, vrai flou de fond). C'est le seul élément qui, recadré
  seul en capture d'écran, serait reconnaissable comme Scan'eat.

FRAGILITÉ IDENTIFIÉE (rejoint F2 de l'Étape 1) :
  Cette signature repose sur UN SEUL canal (matière + couleur). Si un
  concurrent adopte aussi le glassmorphism (tendance Android/iOS actuelle
  très répandue), Scan'eat perd sa seule différenciation visuelle
  du jour au lendemain.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 3. §DP3 — Protocole d'approfondissement (pistes, non appliquées)

Ces pistes ne sont **pas des correctifs à ce stade** — elles seront évaluées et confirmées avec l'utilisateur aux étapes suivantes (notamment Étape 3 — Brand Identity) avant toute implémentation.

1. **Résoudre la tension centrale explicitement.**
   Décider consciemment du dosage clinique/chaleureux plutôt que le laisser flotter écran par écran. Deux directions possibles :
   - *"Précision chaleureuse"* : garder les figures tabulaires et le sérieux du motion, mais réchauffer l'atmosphère par défaut (fond OLED noir pur → un noir légèrement teinté corail, comme le préconise déjà art-direction-engine §COLOR Layer 1 : "jamais de noir pur").
   - *"Instrument premium"* : assumer pleinement le registre clinique, et déplacer la chaleur uniquement sur les moments de célébration/récompense (streaks, objectifs atteints), pas sur l'interface de base.

2. **Renforcer la signature au-delà d'un seul canal.**
   Le glassmorphism ne doit pas rester le seul porteur d'identité. Pistes à explorer aux étapes Typographie (7) et Icônes/Illustration (11) : un traitement typographique signature pour les nombres héros au-delà du poids/tabular actuel, ou une famille d'icônes/illustrations légèrement personnalisée pour les empty states et l'onboarding.

3. **Clarifier la relation entre les deux systèmes de couleur (Scan'eat coral vs. Biolism gold/teal/violet).**
   À trancher explicitement avec l'utilisateur (rejoint F3) : est-ce que Biolism doit se lire comme "un autre monde" (feature premium avec sa propre identité, choix défendable) ou doit-il partager une parenté de teinte avec le reste de l'app pour ne pas fragmenter l'identité globale ?

4. **Vérifier la cohérence du personnage à travers les 5 color schemes.**
   Le personnage "compagnon posé" doit rester reconnaissable en Light, High Contrast et Low Contrast, pas seulement en Dark/OLED (où le glass et le corail sont visuellement les plus forts). À vérifier concrètement à l'Étape 6.

---

## 4. Ce qui n'est PAS remis en cause

- Le sérieux du motion (pas de spring, easing signature unique) — cohérent avec un personnage "posé", à préserver.
- La discipline d'accessibilité (5 schemes, Okabe-Ito, reduced-motion) — c'est une valeur de marque forte, pas un compromis technique à minimiser.
- La consolidation déjà faite sur radius/icon-size — bon signal de maturité du système.

---

**Prochaine étape** : Étape 3 — Identité de marque & différenciation (design-aesthetic-audit §DBI1-3 + app-audit §E9), qui tranchera concrètement la fragilité de signature identifiée ci-dessus, toujours sans modification de code.
