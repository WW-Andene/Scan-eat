# Design Audit — Étape 6 : Couleur & atmosphère (fusionné)

**Sources** : design-aesthetic-audit §DC1-5 (Color Science) + §DSA1-5 (Surface & Atmosphere) · app-audit §E3 (Color Craft & Contrast) · art-direction-engine §COLOR + §ATMOSPHERE + §DEPTH + §LIGHT + §TEXTURE.
**Statut** : audit/synthèse, **aucune modification de code**.

---

## 1. §DC1-2 — Science de la couleur : format et relations de teinte

**Format** : toutes les couleurs sont en hex ARGB (`Color(0xFF...)`), **pas en OKLCH**. art-direction-engine recommande OKLCH pour sa perception uniforme (une même valeur de luminosité "L" paraît réellement égale à travers les teintes, contrairement à HSL). Ce n'est pas un défaut en soi — l'app fonctionne — mais cela rend difficile de garantir, par exemple, que le Gold Biolism et le Teal Biolism ont réellement la même luminosité perçue à alpha égal ; actuellement c'est du réglage à l'œil, pas garanti mathématiquement.

**Relations de teinte** (cf. Étape 2/3) :
- AccentCoral (~20° hue, orange-rouge chaud) vs. Biolism Gold (~45° hue, jaune-doré), Teal (~180° hue, cyan), Violet (~260° hue) — **aucune relation harmonique déclarée** (ni analogue, ni complémentaire, ni split-complémentaire) entre le système Scan'eat et le système Biolism. Ils coexistent sans grammaire de couleur commune.
- À l'intérieur du système Biolism lui-même, Gold/Teal/Violet ne sont pas non plus en relation harmonique classique (45°/180°/260° ne forment ni un triadique régulier ni un analogue) — ils semblent choisis pour se distinguer entre eux (rôles fonctionnels différents : métabolisme/hydratation/autre), pas pour une cohérence chromatique globale.

---

## 2. §DC3 — Qualité du mode sombre (le mode par défaut de l'app)

- OLED (noir pur `#000000`) et Dark (`#17141B`, légèrement teinté) sont deux variantes distinctes du mode sombre — bon signe de nuance.
- Cependant, `#000000` en OLED contredit directement la règle art-direction-engine §COLOR Layer 1 ("jamais de noir pur — toujours une teinte"). C'est un choix défendable pour l'économie de batterie sur écrans OLED réels (cas d'usage légitime), mais cela signifie que le mode le plus "premium" en théorie (vrai noir OLED) est aussi celui qui contredit le plus la garde-fou anti-générique.
- Les 3 niveaux de surface (`background`/`surface`/`surfaceVariant`) varient en luminosité dans les 5 schemes, mais sans confirmation qu'un décalage de teinte (hue shift) accompagne chaque palier — l'Étape 1 n'a relevé aucune preuve de hue-shift systématique entre paliers de surface, contrairement à la recommandation §DEPTH ("un decalage de ~5° par palier crée une profondeur perçue, un simple decalage de luminosité paraît plat").

---

## 3. §DC4-5 — Contraste et couleur comme narration

- Effort de contraste réel et documenté : les commentaires du code mentionnent explicitement des corrections WCAG (`TextSecondary`/`TextMuted` dérivés avec des paliers d'alpha calculés, `LightSafeGreen`/`LightSafeRed`/etc. comme overrides de contraste en thème clair, 3 gold hex distincts "qui ne doivent jamais être fusionnés" pour des raisons de contraste par rôle).
- Couleur comme narration : le système de grade A+→F (6 couleurs, 3 variantes daltonisme) est le seul endroit où la couleur "raconte" activement une information de santé — c'est un bon exemple de couleur porteuse de sens plutôt que décorative.

---

## 4. §DSA1-5 — Surface & atmosphère

- **Matière déclarée** : verre/glass (glassmorphism) — cohérent, un seul matériau clairement assumé, pas un mélange de métaphores contradictoires.
- **Source de lumière** : `glassSheen()` place son liseré lumineux uniquement sur le bord *supérieur* — cohérent avec une lumière zénithale implicite (haut). Mais aucune déclaration explicite de direction de lumière n'existe dans le code (pas de commentaire "light source: top"), donc c'est une cohérence accidentelle plutôt que pilotée par une règle documentée — un risque si un futur composant place son highlight ailleurs sans le savoir.
- **Élévation** : exprimée uniquement via `shadowElevation` (jamais `tonalElevation`) — cohérent avec l'esprit "objet physique en verre qui projette une ombre" plutôt que "surface Material3 qui change de teinte selon son niveau". C'est un choix cohérent avec le matériau déclaré.
- **Ombres** : aucune preuve trouvée que la couleur des ombres soit dérivée de la palette (recommandation art-direction-engine : "jamais de `rgba(0,0,0,...)` générique, toujours une teinte issue de la palette"). Les `shadowElevation` de `ErrorBanner.kt`/`FloatingBars.kt` utilisent le comportement d'ombre par défaut du système Material (gris/noir neutre), pas une ombre teintée custom.
- **Texture** : `ambientGloom()` (blobs radiaux + anneaux d'ondulation) est la seule texture procédurale de l'app — bien exécutée, avec seed fixe pour la reproductibilité, activable/désactivable par préférence utilisateur (bon respect du contrôle utilisateur).
- **Fond atmosphérique par défaut** : noir pur (OLED) ou quasi-noir teinté violet-froid (`#17141B`, Dark) — l'atmosphère par défaut est donc froide/nocturne, alors que l'accent (corail) et la valeur de marque hypothétique ("Sage bienveillant", Étape 3) sont plutôt chauds. Tension similaire à celle déjà identifiée en Étape 2 sur le plan chaud/froid, mais ici visible concrètement dans le choix du fond.

---

## 5. Findings — Étape 6

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F14** | Aucune relation de teinte harmonique déclarée entre AccentCoral (Scan'eat) et Gold/Teal/Violet (Biolism) — rejoint et objective F3. | Moyenne-Haute | Décision produit à trancher avec l'utilisateur |
| **F15** | Fond par défaut froid/noir (OLED `#000000`, Dark `#17141B` à dominante violette) contredit la chaleur de l'accent et de la valeur de marque hypothétique — tension chaud/froid visible concrètement ici, pas seulement en théorie. | Moyenne | Rejoint la tension centrale de l'Étape 2 |
| **F16** | Ombres non teintées (comportement par défaut du système, pas dérivées de la palette) — détail de matière incohérent avec le soin apporté ailleurs (glass, tokens). | Basse | Fix mineur si appliqué |
| **F17** | Aucune preuve de décalage de teinte (hue-shift) entre les paliers de surface (background→surface→surfaceVariant) — la profondeur perçue vient uniquement de la luminosité, pas de la teinte, ce qui est décrit comme "plus plat" par art-direction-engine. | Basse-Moyenne | À vérifier visuellement avant de conclure définitivement |
| **F18** | Pas de direction de lumière déclarée explicitement (juste une cohérence accidentelle via `glassSheen` toujours en haut) — risque de dérive si un futur composant place un highlight dans une autre direction. | Basse | Documentation, pas de refonte |

---

## 6. Ce qui n'est PAS remis en cause

- Le choix du noir OLED pur pour l'économie de batterie est un compromis technique légitime, pas une erreur — à documenter comme exception consciente plutôt qu'à "corriger" mécaniquement.
- Le système de grade A+→F avec 3 variantes daltonisme reste un exemple fort de couleur porteuse de sens, à préserver intégralement.
- `ambientGloom()` et `glassSheen()` restent la signature matérielle la plus mûre de l'app.

---

**Prochaine étape** : Étape 7 — Typographie (design-aesthetic-audit §DT1-4, app-audit §E4, art-direction-engine Part II).
