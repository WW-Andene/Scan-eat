# Design Audit — Étape 11 : Iconographie, illustration, data viz & copy×visuel

**Sources** : design-aesthetic-audit §DI1-4 (Icons) + §DIL1-3 (Illustration) + §DDV1-3 (Data Viz) + §DCVW1-3 (Copy × Visual) · app-audit §E10 (Data Storytelling).
**Statut** : audit/synthèse, **aucune modification de code**.

---

## 1. §DI1-4 — Iconographie

Rappel (Étape 1) : `Icons.Rounded` domine massivement (250 usages/81 fichiers), `Icons.Filled` réservé à la bottom nav (5 icônes), aucun set custom.

- **§DI1 (cohérence)** : forte — un seul style (Rounded) domine, pas de mélange incohérent de familles d'icônes à travers l'app (bon point, souvent une source de dérive dans les apps qui grandissent organiquement).
- **§DI2 (expressivité)** : faible — Material Rounded standard est, par définition, générique. Rejoint directement F7/F9/F19 (différenciation à un seul point de défaillance).
- **§DI3 (personnalisation)** : aucune. Pas un seul icône custom (vectoriel ou autre) trouvé nulle part dans `res/drawable/` en dehors de l'icône de lanceur/notification.
- **§DI4 (taille/poids)** : cohérent — 3 paliers déjà consolidés (Inline 20dp/Nav 24dp/EmptyState 40dp, cf. Étape 1).

**Recommandation art-direction-engine directement applicable** : "Library: [Phosphor / Tabler / Remix / custom — NOT default Lucide/Heroicons]" — l'équivalent Android de cette règle s'applique ici : Material Rounded est l'équivalent Android d'Heroicons/Lucide en usage web, c'est-à-dire le choix "par défaut" que la règle recommande justement de remplacer consciemment.

---

## 2. §DIL1-3 — Illustration

Confirmé sans ambiguïté par l'agent d'exploration : **zéro illustration custom, zéro Lottie/Rive, zéro asset `res/raw/`**. Les 3 seuls fichiers de `res/drawable/` sont l'icône de lanceur adaptative et l'icône de notification — rien d'autre.

Conséquence directe sur les empty states et l'onboarding (4 pages : Welcome/ValueProposition/ApiMode/ProfileCapture) : ils reposent entièrement sur texte + icône Material, sans aucun renfort illustratif. C'est le point d'entrée de l'app (premier contact utilisateur) qui est structurellement le plus dépourvu de signature visuelle propre — un contraste net avec le soin apporté ailleurs (glass, accessibilité, tokens).

---

## 3. §DDV1-3 — Visualisation de données

Rappel de l'agent d'exploration — tous les graphiques sont dessinés à la main en Canvas Compose, aucune librairie de charting :

| Composable | Technique | Qualité constatée |
|---|---|---|
| `WeeklyBarsCard.kt` | Canvas, lignes de référence sur barres | Custom, fonctionnel |
| `EvolutionComponents.kt` (Biolism) | Canvas complet : aire remplie + trait + marqueurs | Bien exécuté, mais dupliqué (cf. F22) |
| `WeightHistorySection.kt` | Même pattern que ci-dessus, dupliqué indépendamment | Idem — incohérence potentielle si l'un dérive sans l'autre |
| `ScoreDisplay.kt` / `ResultScreen.kt` (anneau de score) | `CircularProgressIndicator` détourné en jauge + glow radial | Le graphique le plus "signature" de l'app (lié au moment clé du scan) |
| Barres de budget/micronutriments | `LinearProgressIndicator` simple | Fonctionnel, pas un vrai "chart" |

**§DDV2 (précision/lisibilité)** : non vérifiable en profondeur sans revue visuelle (axes, légendes, échelles) — mais l'absence de librairie dédiée signifie que chaque graphique gère lui-même son échelle, ses axes implicites, etc., ce qui est exactement le terrain où des incohérences d'échelle entre graphiques similaires (Biolism vs. Weight) peuvent apparaître silencieusement.

**§DDV3 (accessibilité des graphiques)** : les couleurs sémantiques (grade A+→F) ont 3 variantes daltonisme documentées (Étape 1) — bon point. Non vérifié si les graphiques de tendance (Evolution/Weight) utilisent ces mêmes garanties de contraste/daltonisme ou des couleurs de trait ad hoc.

**Lien avec F11 (Étape 4)** : Yuka a un "geste visuel central" unique et mémorable (le score circulaire coloré). Scan'eat a un anneau de score comparable (`ScoreRing`) — **la donnée existe, le geste visuel existe déjà**, mais rien dans l'audit ne suggère qu'il soit mis en scène avec la même intensité de signature que chez Yuka (pas de traitement hero/plein-écran confirmé, pas de couleur dédiée unique par tranche de score au même niveau de dramatisation). À vérifier visuellement.

---

## 4. §DCVW1-3 — Copy × Visuel

Rappel des échantillons de l'agent d'exploration :
- Instructif/direct : "Photographiez une étiquette → score 0–100", "Créez des recettes pour générer la liste de courses automatiquement."
- Chaleureux/casual dans les CTA d'empty state : "touchez l'étoile sur un scan pour l'ajouter."
- Clinique/technique dans Biolism : "BMR", "flux de substrats", terminologie scientifique (Deurenberg/Hodgdon-Beckett).

**§DCVW1 (cohérence de voix)** : la voix écrite **reproduit exactement** la tension chaud/clinique déjà identifiée visuellement à l'Étape 2 (Character Brief) et l'Étape 6 (couleur/atmosphère) — ce n'est donc pas une observation isolée mais une troisième confirmation indépendante (visuel, couleur, ET copy) de la même tension centrale non résolue. C'est un signal fort que cette tension est bien réelle et mérite une décision explicite, pas un artefact d'un seul système.

**§DCVW2 (alignement voix/visuel)** : la voix imperative/instructive ("Créez…", "Ajoutez…", "Photographiez…") correspond bien au personnage "posé/sérieux" du motion — cohérence positive à noter.

---

## 5. Findings — Étape 11

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F31** | Zéro illustration/animation (Lottie/Rive) nulle part dans l'app — l'onboarding et les empty states, les points de contact les plus importants pour la première impression, sont les moins pourvus en signature visuelle. | **Haute** | Candidat concret et à fort impact perçu pour un futur correctif |
| **F32** | Iconographie 100% Material Rounded standard — confirme et objective F7/F9/F19 sous un troisième angle (après typo et illustration). | Moyenne-Haute | Répétition du même problème racine sous 3 angles distincts (typo/icônes/illustration) — signal fort de priorité |
| **F33** | Le "geste visuel central" comparable à Yuka existe déjà en fonction (`ScoreRing`) mais rien ne confirme qu'il soit mis en scène avec la même intensité de signature — à vérifier visuellement avant de conclure. | Moyenne | Rejoint F11 |
| **F34** | La tension chaud/clinique identifiée en Étape 2 (personnage) et Étape 6 (couleur) est confirmée une troisième fois indépendamment dans la voix écrite (Biolism clinique vs. reste de l'app plus casual) — ce n'est plus une hypothèse isolée, c'est un pattern systémique confirmé sur 3 dimensions indépendantes. | **Haute (confirmation, pas nouveau)** | Doit être tranché explicitement avant tout fix de personnage (Étapes 2/3/6) |

---

## 6. Ce qui n'est PAS remis en cause

- La cohérence interne de la famille d'icônes (Rounded uniquement) est un acquis — le problème n'est pas l'incohérence, c'est le manque de personnalisation.
- La voix écrite est globalement claire et actionnable (impératif direct) — le problème n'est pas la qualité de l'écriture, c'est le dosage clinique/chaleureux non tranché.

---

**Prochaine étape** : Étape 12 — Synthèse finale & auto-audit (app-audit §E7-E8, §E11, art-direction-engine §CHECK, design-aesthetic-audit §DDT1-2, tableau de bord priorisé).
