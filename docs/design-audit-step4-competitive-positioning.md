# Design Audit — Étape 4 : Positionnement concurrentiel

**Sources** : design-aesthetic-audit §DCP1-3 (Competitive Positioning).
**Statut** : audit/synthèse, **aucune modification de code**.

> **Note de fiabilité des sources** : cette étape s'appuie sur ma connaissance générale (entraînement) de l'identité visuelle publique de ces apps (captures d'écran largement diffusées, stores Android/iOS), **pas sur une recherche web en direct effectuée pour cet audit**. Les détails précis (nuances de couleur exactes, versions actuelles) peuvent avoir évolué depuis ma date de connaissance. À vérifier avec des captures d'écran actuelles avant toute décision engageante si la précision compte.

---

## 1. §DCP1 — Inventaire concurrentiel (nutrition-tracking, catégorie directe)

| App | Signature visuelle connue | Registre |
|---|---|---|
| **Yuka** | Fond blanc/clair, gros score circulaire coloré (vert/jaune/orange/rouge) comme élément central de chaque écran, typographie ronde et amicale, mascotte/ton très accessible grand public | Simplicité radicale, verdict binaire lisible en 1 seconde |
| **MyFitnessPal** | Interface dense, orientée données/listes, bleu comme couleur de marque, beaucoup de chiffres et de tableaux, esthétique "tableur convivial" | Utilitaire, exhaustif, peu d'atmosphère |
| **Cronometer** | Encore plus dense/tableur que MyFitnessPal, orienté utilisateurs experts (micronutriments), vert comme accent, esthétique clairement "outil de précision" sans fioriture | Clinique assumé, pas de chaleur recherchée |
| **Lifesum** | Palette pastel/corail-pêche, illustrations rondes et amicales, courbes douces, ton "lifestyle bien-être" | Chaleureux, lifestyle, peu clinique |
| **Noom** | Ton coaching/psychologie comportementale, illustrations de personnages, palette douce, orienté contenu éditorial autant qu'outil | Accompagnement humain, coaching |

---

## 2. §DCP2 — Matrice de positionnement (deux axes : Clinique↔Chaleureux / Minimal↔Dense)

```
                    CLINIQUE
                        │
         Cronometer  ●  │
                        │  ● MyFitnessPal
                        │
  DENSE ──────────────────────────────── MINIMAL
                        │
                        │  ● Scan'eat (position actuelle,
                        │     hypothèse)
              Noom  ●   │  ● Lifesum
                        │        ● Yuka
                    CHALEUREUX
```

**Où se situe Scan'eat aujourd'hui** (d'après les Étapes 1-3) : dans la zone médiane — chiffres tabulaires + figures cliniques (tire vers Cronometer/MyFitnessPal) MAIS habillé de glass et de corail chaud (tire vers Lifesum). Densité fonctionnelle élevée (25+ écrans, proche MyFitnessPal/Cronometer) mais chaque écran individuel reste discipliné/aéré (proche Yuka/Lifesum dans l'exécution, pas dans la densité globale).

**Espace blanc identifié** : aucun concurrent direct ne combine *matière premium tactile (glass/blur réel)* + *rigueur clinique des chiffres* + *accessibilité poussée comme argument de marque*. C'est potentiellement un territoire visuel inoccupé — mais rejoint le Finding F7 : ce territoire n'est aujourd'hui occupé que par un seul ingrédient différenciant (le glass), pas par l'ensemble de la combinaison rendue explicite et mémorable.

---

## 3. §DCP3 — Menaces & opportunités de positionnement

| Type | Constat |
|---|---|
| **Menace** | Le glassmorphism est une tendance Android/iOS 2025-2026 largement répandue (Material You, Liquid Glass d'Apple). Si Scan'eat n'ajoute pas de couche de différenciation au-delà de la matière (cf. F7/F9), il risque de se fondre visuellement dans la vague plutôt que de s'en distinguer. |
| **Opportunité** | Aucun concurrent direct ne traite l'accessibilité (daltonisme, contraste, reduced-motion, police dyslexique) comme un axe de marque visible. C'est un espace blanc réel et défendable — actuellement invisible car traité uniquement en profondeur technique, jamais mis en avant visuellement/narrativement (cf. Étape 11, Copy). |
| **Opportunité** | Yuka a construit tout son succès sur UN geste visuel simple et mémorable (le score coloré). Scan'eat a des ingrédients épars (glass, figures tabulaires, dual accent Coral/Biolism) mais aucun geste central unique équivalent — une simplification/unification pourrait créer ce geste signature. |

---

## 4. Findings — Étape 4

| # | Constat | Sévérité | Étapes concernées |
|---|---|---|---|
| **F10** | Aucun concurrent direct ne combine glass premium + rigueur clinique + accessibilité comme argument de marque — espace blanc réel, mais non exploité visuellement aujourd'hui. | Info/Opportunité | Étape 12 (Synthèse) |
| **F11** | Contrairement à Yuka (score circulaire = geste signature unique et mémorable), Scan'eat n'a pas de "geste visuel central" équivalent malgré des scores similaires en fonction (PersonalScoreEngine) — à vérifier si le score est visuellement mis en scène de façon aussi marquante côté UI. | Moyenne | Étape 8 (Composants), Étape 11 (Data viz) |

---

**Prochaine étape** : Étape 5 — Fondations : architecture des tokens (design-aesthetic-audit §DTA1-2, app-audit §E1, art-direction-engine §TOKENS).
