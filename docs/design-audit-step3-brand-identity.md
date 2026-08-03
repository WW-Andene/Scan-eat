# Design Audit — Étape 3 : Identité de marque & différenciation

**Sources** : design-aesthetic-audit §DBI1 (Archétype) · §DBI2 (Design DNA) · §DBI3 (Anti-Genericness) · app-audit §E9 (Visual Identity & Recognizability).
**Statut** : audit/synthèse, **aucune modification de code**. S'appuie sur les Étapes 1-2.

---

## 1. §DBI1 — Archétype de marque

À partir des preuves factuelles (glassmorphism comme signature unique, chiffres cliniques + chaleur corail, discipline d'accessibilité poussée, absence d'illustration/mascotte) :

| Archétype candidat | Correspondance | Rejeté / retenu |
|---|---|---|
| **Le Sage** (expertise, clarté, confiance par la compétence) | Figures tabulaires, 5 color schemes d'accessibilité, discipline de tokens | Partiellement — trop froid seul |
| **Le Soignant** (bienveillance, accompagnement, chaleur) | Accent corail, glass "doux", ton des empty states (accent-tinted, pas gris) | Partiellement — trop chaud seul |
| **Hybride retenu : "Le Sage bienveillant"** | Combine précision (Sage) et chaleur portée par la matière/couleur (Soignant) — cohérent avec la tension centrale identifiée à l'Étape 2 | **Retenu comme hypothèse de travail**, à valider avec l'utilisateur |

Ce n'est **pas** un archétype "Créateur" (pas d'expressivité/originalité formelle — iconographie et typo 100% standard) ni "Explorateur" (pas de dimension ludique/ludique-découverte dans le motion).

---

## 2. §DBI2 — Design DNA (les gènes qui devraient survivre à toute évolution visuelle)

Si l'app était totalement redessinée demain, voici les éléments qui, d'après les preuves, constituent son ADN et devraient être préservés :

1. **Verre + lumière** — la matière translucide avec liseré supérieur lumineux (`glassSheen`) et flou réel (Haze) est l'élément le plus singulier et le plus mûr techniquement (RenderEffect réel, pas une simulation CSS-like approximative).
2. **Chrome flottant détaché** — navigation en pilules suspendues plutôt qu'edge-to-edge. C'est une décision de composition, pas seulement de couleur — donc plus difficile à copier superficiellement qu'une simple palette.
3. **Rigueur du système de tokens** — 3 paliers de rayon, 3 paliers d'icône, discipline "zéro couleur en dur" : un gène invisible pour l'utilisateur final mais qui garantit la cohérence perçue.
4. **Chiffres héros en figures tabulaires + poids Black** — le seul traitement typographique qui distingue Scan'eat d'un Material par défaut.
5. **Accessibilité comme valeur, pas comme contrainte** — 5 color schemes, palettes daltonisme (Okabe-Ito), reduced-motion réel. C'est un gène culturel/produit autant que visuel.

**Gènes absents qu'il faudrait envisager d'ajouter** (pas un fix ici, une observation) : aucune police de marque, aucun motif graphique récurrent au-delà du glass, aucune mascotte/illustration. Le "Design DNA" actuel est donc riche en profondeur technique mais pauvre en surface reconnaissable — cohérent avec le Finding F2 de l'Étape 1.

---

## 3. §DBI3 — Anti-Genericness (le test qui compte le plus)

**Test de la capture recadrée** : si on prend une capture d'écran de Scan'eat et qu'on masque à la fois le glass et l'accent corail (par exemple en niveaux de gris, sans flou), reste-t-il reconnaissable ?

**Réponse honnête, basée sur les preuves** : **Non, probablement pas.** Sans le glass et sans la couleur :
- La typographie est Material par défaut (police système, pas de police de marque).
- L'iconographie est 100% Material Rounded standard, indiscernable d'innombrables autres apps Android.
- La composition des cartes (`ScanEatCard`, 16dp de rayon, cadre plat) est un pattern "carte Material3" générique.
- La navigation bottom-tab à 5 items est un pattern extrêmement répandu.

**Conclusion §DBI3** : Scan'eat n'est PAS générique aujourd'hui grâce à un seul levier (glass+couleur), mais **serait générique sans lui**. C'est une différenciation à un seul point de défaillance — si Google/Material pousse une nouvelle tendance dominante (ce qui arrive régulièrement), ou si un concurrent adopte aussi le glassmorphism (déjà une tendance 2025-2026 largement répandue sur Android/iOS), Scan'eat perd sa seule signature du jour au lendemain.

Comparaison avec app-audit §E9 (Visual Identity & Recognizability) — les 3 questions de ce critère :
| Question §E9 | Réponse |
|---|---|
| L'app est-elle identifiable à partir d'un seul composant ? | Oui, mais seulement si ce composant montre le chrome flottant en verre — pas vrai pour un simple bouton, une carte, ou un champ de recherche pris isolément |
| Existe-t-il une signature qui survivrait à un rebranding concurrentiel du même style visuel ? | Non — voir ci-dessus |
| Le produit revendique-t-il un territoire visuel que la concurrence n'occupe pas ? | Partiellement — le dosage précis "chiffres cliniques + verre chaud + accessibilité poussée" est spécifique, mais chaque ingrédient pris seul est répandu |

---

## 4. Findings — Étape 3

| # | Constat | Sévérité | Étapes concernées |
|---|---|---|---|
| **F7** | La différenciation visuelle repose sur un point de défaillance unique (glass+couleur) ; sans lui, l'app retombe dans un pattern Material générique. | **Haute** | Étapes 7 (Typo), 8 (Composants), 11 (Icônes/Illustration) |
| **F8** | Aucun archétype de marque n'a jamais été formulé explicitement dans le code/design — "Le Sage bienveillant" est une hypothèse de travail déduite, pas une décision produit confirmée. | Moyenne | À valider avec l'utilisateur avant l'Étape 5 (application) |
| **F9** | Le Design DNA est fort en profondeur technique (tokens, accessibilité, matière) mais absent en surface mémorable (pas de motif graphique récurrent, pas de police, pas de mascotte). | Moyenne-Haute | Étapes 7, 11 |

---

## 5. Ce qui n'est PAS remis en cause

- Le choix du glassmorphism en soi n'est pas critiqué — c'est un langage matériel cohérent et bien exécuté techniquement (vrai flou RenderEffect, pas une approximation).
- La rigueur du système de tokens reste un atout à préserver intégralement.

---

**Prochaine étape** : Étape 4 — Positionnement concurrentiel (design-aesthetic-audit §DCP1-3), qui viendra objectiver le Finding F7 face à des concurrents réels du marché nutrition-tracking.
