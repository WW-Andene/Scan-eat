# Design Audit — Étape 1 : Profil d'identité & classification de style

**Portée** : intégralité de l'app Scan'eat (Android/Kotlin/Compose, Material3).
**Sources** : app-audit §I.4 (Five-Axis Aesthetic Profile) · design-aesthetic-audit §0, §DP0 (Character Extraction), §DS1-DS2 (Style Classification).
**Statut** : audit factuel, **aucune modification de code effectuée**. Document destiné à la review avant application de correctifs.

---

## 0. Méthode

Deux agents d'exploration read-only ont inventorié :
1. Le système de tokens (`presentation/ui/theme/` — 23 fichiers), les 5 color schemes Material3, la typographie, l'espacement, les rayons, les durées de motion, les icônes de lanceur.
2. L'ensemble des écrans (25+ features, 112 fichiers de composants), la bibliothèque de composants partagés, la famille d'icônes dominante, la fuite éventuelle de couleurs codées en dur, l'onboarding/splash/empty-states, et la forme de navigation.

Tout ce qui suit est **descriptif** (ce qui existe), pas encore prescriptif (ce qui devrait changer) — les recommandations viendront aux étapes suivantes du plan en 12 étapes.

---

## 1. Five-Axis Aesthetic Profile (app-audit §I.4)

| Axe | Constat factuel | Position estimée |
|---|---|---|
| **A1 — Fidélité vs. Vitesse** | 5 color schemes complets (OLED/Dark/Light/High Contrast/Low Contrast), typo à 15 slots entièrement redéfinis, échelle de motion partagée, glassmorphism avec vrai flou de fond (Haze/RenderEffect) — investissement visuel réel, pas un habillage minimal. | Fidélité **haute** |
| **A2 — Amplification esthétique de la valeur** | Le glassmorphism (`glassSheen`, `ambientGloom`) et le "chrome" flottant (top bar + bottom nav en pilules détachées avec flou réel) sont la signature visuelle la plus marquante de l'app — au-delà du minimum fonctionnel. | Esthétique **amplifie** la valeur perçue |
| **A3 — Densité d'information vs. respiration** | Espacement en 7 paliers documentés en tête de `Colors.kt` sur base φ (4/6/10/16/26/42/68) — **mais `Spacing.kt` implémente en réalité une échelle différente et non-φ (4/8/10/12/16/24/32)**. Décalage entre l'intention documentée et l'implémentation réelle. | À clarifier — voir Finding F1 |
| **A4 — Expertise de l'audience** | 25+ écrans, sous-domaines poussés (Biolism avec sous-modules bioProfile/data/evolution/tracker, jeûne, médication, dépenses) — app orientée utilisateur engagé/quotidien, pas un outil grand public superficiel. | Audience **engagée / experte du domaine santé** |
| **A5 — Permanence de la marque vs. tendance** | Aucune fonte de marque (seule police custom = OpenDyslexic, un outil d'accessibilité, pas une police de marque) ; iconographie 100% Material Rounded standard ; aucune illustration custom. La signature vient uniquement de la couleur/matière (glass), pas de la typo/icônes/illustration. | Identité **portée par un seul canal** (couleur+matière), signature fragile ailleurs — voir Finding F2 |

---

## 2. Character Extraction (§DP0) — preuves brutes, avant interprétation

### 2.1 Palette — deux systèmes d'accent coexistants
- **Scan'eat (nutrition/scan)** : `AccentCoral #D97C56` + sémantique `FlagRed`/`FlagGreen`/`AmberWarning`.
- **Biolism (métabolisme, fonctionnalité premium)** : un système entier dérivé du nombre d'or — Gold `#C9A84C`, Teal `#38C8C8`, Violet `#9275E0`, avec 6 paliers d'alpha calculés en puissances de φ (1.0 / 0.618 / 0.236 / 0.162 / 0.10 / 0.062).
- Ces deux systèmes ne partagent pas de hue de base — Biolism a sa propre identité chromatique complète, distincte de l'identité "Scan'eat" du reste de l'app.
- 3 variantes de la palette grade A+→F existent (normale, Protan/Deutéranopie Okabe-Ito, Tritanopie) — accessibilité couleur traitée avec un soin rare.
- 5 color schemes Material3 complets (OLED vrai noir, Dark, Light, High Contrast, Low Contrast) — profondeur d'accessibilité inhabituelle pour ce niveau de détail (3 gold hex distincts documentés comme *devant rester distincts* pour des raisons WCAG).

### 2.2 Matière & profondeur — le glassmorphism comme langage visuel central
- `glassSheen()` : liseré lumineux sur le bord supérieur uniquement (imite un reflet de verre).
- `ambientGloom()` : fond animé à blobs radiaux + anneaux d'ondulation procéduraux (seed fixe `Random(20260729)`), activable/désactivable.
- `FloatingBars.kt` : top bar et bottom nav flottants, coins totalement arrondis, **vrai flou de fond** via la librairie Haze (RenderEffect), détachés des bords de l'écran — pas un edge-to-edge Material standard.
- Élévation exprimée uniquement via `shadowElevation` (jamais `tonalElevation`) — cohérent avec un système "verre + ombre", pas "surface tonale Material3 standard".

### 2.3 Forme — 3 paliers de rayon, cohérents mais resserrés
- `CardRadius` : CONTROL 12dp / CARD 16dp / PROMINENT 20dp. Le commentaire du fichier lui-même documente qu'un audit antérieur avait trouvé 12 valeurs ad hoc en circulation (10/12/14dp) avant cette consolidation — donc la consolidation a déjà eu lieu, c'est un point positif acquis.
- Même logique de nettoyage documentée pour `IconSize` (13 valeurs ad hoc avant consolidation en 3 paliers : Inline 20dp / Nav 24dp / EmptyState 40dp).

### 2.4 Mouvement — vocabulaire restreint et globalement cohérent
- Une seule easing nommée, réutilisée : `ScoreRevealEasing` (cubic-bezier `0.16, 0.84, 0.28, 1`), sur 6+ sites d'appel (Motion.kt, AppNavGraph.kt, ScoreDisplay.kt).
- 6 durées distinctes en usage : 100ms (press), 200ms (fade tab-switch, ×2), 300ms (transitions nav slide+fade, ×4), 420ms (hero entrance, site unique), 700ms (score reveal, site unique), 26 000ms (dérive du fond ambiant, boucle infinie).
- Fallback `snap()` pour `prefers-reduced-motion` sur 3 sites — respect réel de l'accessibilité motion, pas juste une case cochée.
- Aucun usage de `spring(` dans toute la présentation — le mouvement est entièrement piloté par courbes/durées fixes, jamais par physique à ressort.

### 2.5 Typographie — aucune police de marque
- 15 slots Material3 Typography entièrement redéfinis (poids, taille, line-height, tracking) — effort réel sur l'échelle, mais **police système par défaut**, aucune police custom de marque.
- Seule police custom : OpenDyslexic (accessibilité, pas identité visuelle) — utilisée uniquement si l'utilisateur active le mode dyslexique.
- `HeroNumberStyle` : `FontWeight.Black` + figures tabulaires (`fontFeatureSettings = "tnum"`) pour les nombres héros (TDEE, score, minuteur de jeûne) — seul traitement typographique distinctif dans toute l'app.

### 2.6 Iconographie — 100% Material standard
- `Icons.Rounded` domine massivement (250 usages / 81 fichiers). `Icons.Filled` réservé presque exclusivement aux 5 icônes de la bottom nav.
- Aucun set d'icônes custom, aucune illustration détectée nulle part dans `res/drawable/` (3 fichiers seulement : 2 pour l'icône de lanceur adaptative, 1 pour l'icône de notification).
- Les empty states utilisent une icône Material teintée à l'accent (pas de gris — un anti-pattern explicitement évité selon le commentaire du composant) mais restent icône-only, sans illustration.

### 2.7 Discipline du système de tokens — point fort net
- **Zéro couleur codée en dur détectée en dehors de `ui/theme/`** — tout le code applicatif passe par la couche de tokens `Colors.kt`. C'est une discipline rare et positive à documenter/préserver dans les étapes suivantes.

---

## 3. Style Classification (§DS1-DS2)

**Style primaire : Glassmorphism fonctionnel à accent chaud**, avec une sous-identité distincte pour la fonctionnalité premium (Biolism, esthétique "précision dorée" dérivée du nombre d'or).

Caractéristiques qui ancrent cette classification :
- Matière translucide + flou réel (Haze) comme signature de navigation.
- Chrome flottant détaché plutôt que barres edge-to-edge standard Material.
- Accent chaud corail sur fond neutre sombre par défaut (OLED/Dark en schémas principaux).
- Rayons de coin généreux mais mesurés (12–20dp), pas de style anguleux/technique.
- Mouvement discret, une seule courbe d'easing signature, pas de rebond/spring — caractère "posé", pas "ludique".

**Score de cohérence** : élevé sur les fondations (couleur, tokens, rayons, mouvement) — modéré sur l'identité globale, car deux systèmes de couleur non liés (Scan'eat coral vs. Biolism gold/teal/violet) coexistent sans hue partagée, et parce que la signature repose sur un seul canal (matière/couleur) sans renfort typographique ou iconographique.

---

## 4. Findings à investiguer aux étapes suivantes (pas de fix ici)

| # | Constat | Sévérité | Étape du plan concernée |
|---|---|---|---|
| **F1** | Décalage entre l'échelle d'espacement φ documentée dans l'en-tête de `Colors.kt` (4/6/10/16/26/42/68) et l'échelle réellement implémentée dans `Spacing.kt` (4/8/10/12/16/24/32, non-φ) — soit la doc est obsolète, soit l'implémentation a dérivé. | Moyenne | Étape 5 (Tokens) |
| **F2** | L'identité de marque repose sur un seul canal (glassmorphism + couleur) ; aucune typographie de marque, aucun set d'icônes ou illustration distinctifs. Un screenshot recadré sans le glass/couleur ne serait pas reconnaissable comme Scan'eat. | Moyenne-Haute | Étape 3 (Brand Identity) |
| **F3** | Deux systèmes de couleur d'accent sans relation de teinte partagée (Coral vs. Gold/Teal/Violet) — à trancher : est-ce voulu (Biolism = "univers premium" distinct) ou une dérive à unifier ? | À clarifier avec l'utilisateur | Étape 6 (Couleur & atmosphère) |
| **F4** | 420ms (hero entrance) et 700ms (score reveal) sont des valeurs à site d'appel unique, pas des tokens partagés — risque de dérive future si un 3ᵉ site similaire est ajouté avec une durée différente. | Basse | Étape 9 (Interaction & motion) |
| **F5** | Aucune illustration ni set d'icônes custom nulle part dans l'app — écarts probables avec app-audit §E9 (Visual Identity) et design-aesthetic-audit §DIL (Illustration), à approfondir. | Moyenne | Étapes 3, 11 |
| **F6** | Pas de `values-night/` — le mode sombre est entièrement piloté par un état Compose applicatif, indépendant du qualificatif système day/night. Comportemental, pas nécessairement un défaut, mais à vérifier vs. attentes Android (§E11 Mobile-Specific). | Basse | Étape 12 (Synthèse, Mobile) |

---

## 5. Points forts déjà acquis (à ne pas régresser)

- Discipline de tokens quasi parfaite (zéro couleur codée en dur hors théorie).
- Consolidation déjà faite sur les rayons (12 valeurs → 3) et les tailles d'icônes (13 valeurs → 3), avec traçabilité en commentaire.
- Accessibilité couleur (Okabe-Ito daltonisme) et motion (reduced-motion + snap fallback) traitées à un niveau rarement vu dans ce type d'app.
- Vocabulaire de mouvement resserré (une seule easing signature, 6 durées au total).

---

**Prochaine étape** : Étape 2 — Personnage de design (§DP1 Dimensions, §DP2 Character Brief, §DP3 Deepening), qui s'appuiera sur les constats ci-dessus pour formuler un Brief de personnage complet, toujours sans modification de code.
