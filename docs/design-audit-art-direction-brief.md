# Art Direction Brief — Scan'eat "Seconde Peau"

**Source** : art-direction-engine §BRIEF, rédigé en réponse à la direction donnée par l'utilisateur après les 12 étapes d'audit (`docs/design-audit-step1-*.md` à `step12-synthesis.md`).
**Statut** : document de proposition — **aucune modification de code**. À valider avant toute implémentation.

> **Garde-fou explicite posé par l'utilisateur** : cette direction s'inspire du *sentiment* d'une interface bio-analytique intégrée (type HUD de film futuriste), **pas de son esthétique littérale**. Pas de cyberpunk, pas de néon, pas de scanlines, pas de grille "matrix", pas de typographie hacker/mono agressive. L'app s'adresse à tout le monde — un utilisateur de 16 ans comme un utilisateur de 70 ans doit s'y sentir chez lui dès la première seconde. Chaque choix ci-dessous a été délibérément tempéré dans ce sens.

---

## SUBJECT

Une app de suivi nutritionnel et métabolique qui se comporte comme une extension sensible du corps de l'utilisateur — pas un tableau de bord qu'on consulte, mais une couche qui semble *faire partie de soi*. Chaque chiffre, chaque courbe, chaque alerte doit se lire comme une sensation qu'on reconnaît plutôt qu'une donnée qu'on interprète.

## AUDIENCE

Grand public, tous âges, engagement quotidien (rappel Étape 1 : audience "engagée / experte du domaine santé", 25+ écrans). Pas un public de niche tech-savvy — la sophistication doit être *ressentie*, jamais *démontrée*. Aucun jargon visuel qui exclurait un utilisateur non technophile.

## EMOTIONAL TARGET

Pas "la précision froide d'un scanner médical". Pas non plus "la mignonnerie d'un coach bienveillant qui vous parle comme à un enfant". Plutôt : **la confiance tranquille de sentir son propre corps clairement, pour la première fois** — comme retirer des lunettes floues, pas comme se brancher à une machine.

## VALUES

- "Je vous montre ce qui se passe réellement en vous, avec douceur."
- "Cette précision est à VOUS, pas un jugement clinique extérieur."
- "Je m'adapte à votre corps, pas l'inverse." (rejoint l'accessibilité déjà forte de l'app — 5 thèmes, daltonisme, reduced-motion : ces choix deviennent narrativement cohérents avec "seconde peau" au lieu d'être seulement des cases techniques cochées)

## VISUAL CONCEPT

**"Peau vivante, pas écran de contrôle."** Le glassmorphism existant (`glassSheen`, `ambientGloom`) n'est pas jeté — il est réinterprété : le verre n'est plus une vitre décorative posée sur l'interface, il devient une **membrane** qui réagit doucement à l'état réel de l'utilisateur (score du jour, streak, TDEE) au lieu d'onduler de façon purement ambiante et indépendante des données.

---

## ─── PALETTE ──────────────────────────────────────────────────

Conserver l'accent corail existant (déjà chaud, déjà humain — ne pas le remplacer par un cyan/vert futuriste qui virerait cyberpunk). Le faire évoluer en OKLCH pour garantir une luminosité perçue cohérente à travers les 5 thèmes, et corriger le fond noir pur (F15/F17).

| Rôle | Valeur proposée (OKLCH) | Description |
|---|---|---|
| Background (dark/défaut) | `oklch(14% 0.02 30)` | Un noir légèrement réchauffé (hue 30° = brun-rouge très discret) — remplace le `#000000` pur de l'OLED ; se lit encore comme "sombre et premium" mais n'est plus un noir "écran éteint" |
| Surface | `oklch(18% 0.018 25)` | +4% L, léger hue-shift vers le corail (−5°) — crée la profondeur perçue manquante (F17) |
| Surface élevée | `oklch(22% 0.016 20)` | +4% L, même logique de décalage |
| Texte primaire | `oklch(94% 0.012 30)` | Chromatique, jamais blanc pur |
| Texte secondaire | `oklch(70% 0.015 30)` | |
| Accent (Coral, conservé, reformulé en OKLCH) | `oklch(68% 0.15 35)` | Même identité qu'aujourd'hui (`#D97C56`), juste reformulée pour un contrôle de luminosité fiable |
| Accent "pouls" (nouveau, discret) | `oklch(72% 0.10 35)` à opacité variable | Utilisé UNIQUEMENT pour la respiration/pouls ambiant décrit en §MOTION — jamais comme couleur d'action |
| Biolism (Gold/Teal/Violet) | **conservés tels quels, mais recadrés narrativement** | Ils deviennent "la couche la plus profonde de la peau" (le métabolisme, invisible à l'œil nu) plutôt qu'un monde séparé — résout F3/F14 sans changer un seul hex |
| Erreur/Succès | conservés (déjà calibrés, déjà daltonisme-safe) | Aucun changement — ces choix étaient déjà bons |
| Température | Chaude, mais sombre — jamais froide. Aucun bleu-cyan dominant, aucun vert "matrix" |

**Garde-fou explicite** : pas de vert néon, pas de cyan électrique, pas de rouge sang-cyberpunk. La chaleur du corail reste l'unique signature chromatique forte.

---

## ─── TYPOGRAPHIE ───────────────────────────────────────────────

**Ne pas** choisir une police "hacker" (mono agressif type Terminal/Matrix). Le geste juste : une police géométrique humaniste, plus arrondie qu'une pure grotesque technique, qui garde la précision (bons chiffres tabulaires) sans jamais paraître froide.

- **Police d'affichage/héros** : dans l'esprit d'**Outfit** ou **Manrope** (géométrique mais avec des terminaisons douces, pas anguleuses) — remplace la police système par défaut, corrige F19. Utilisée pour `HeroNumberStyle` et les titres.
- **Police de corps** : une humaniste sans-serif proche de **Plus Jakarta Sans** — lisible, chaleureuse, jamais clinique — pour tout le texte courant.
- Conserver l'échelle de tracking déjà bonne (négatif sur les gros titres, positif sur les labels — Étape 7, à ne pas toucher).
- Conserver `HeroNumberStyle` (Black + tabular nums) tel quel — c'est déjà le bon geste, il a juste besoin d'une vraie police de marque en dessous.
- **Garde-fou** : pas de police "display futuriste" à empattements agressifs façon générique sci-fi (type Orbitron) — resterait dans le "délire cyberpunk" explicitement écarté.

---

## ─── SHAPE ────────────────────────────────────────────────────

Conserver le principe de 3 paliers de rayon (12/16/20dp) — cohérent avec "peau", pas "machine" (une machine aurait des angles vifs). **Ne pas** introduire de clip-paths anguleux ou de hexagones façon HUD sci-fi — ce serait précisément le "délire" à éviter.

Seul ajout : une forme organique douce et unique comme geste signature — pas un octogone/héxagone technique, mais une **goutte/blob asymétrique très subtil** (un seul coin plus arrondi que les autres, à la manière d'une cellule) réservée à UN SEUL élément clé (voir §COMPOSITION) pour éviter de la disperser partout et perdre son impact.

---

## ─── DEPTH & SURFACE ──────────────────────────────────────────

- **Matériau** : "peau" plutôt que "verre froid" — conserver `glassSheen`/`ambientGloom` mais réchauffer leur teinte (déjà couvert en §PALETTE) et introduire un **pouls subtil synchronisé aux données réelles** de l'utilisateur (voir §MOTION) plutôt qu'une dérive purement décorative indépendante.
- **Ombres** : dérivées de la palette (corriger F16) — jamais de `rgba(0,0,0,...)` neutre.
- **Lumière** : source déclarée explicitement en haut-centre (corrige F18), cohérente avec le liseré actuel de `glassSheen`.
- **Garde-fou** : pas de "glow" façon implants cybernétiques (bords lumineux durs, saturés) — le glow doit rester doux, jamais agressif ou "tech".

---

## ─── MOTION ───────────────────────────────────────────────────

C'est ici que le concept "seconde peau" prend le plus de sens concret, et où l'audit avait justement identifié un vide (F24, F25 : pas de signature animation, le "loading" sans personnage).

- **Signature de mouvement proposée** : un **pouls respiratoire lent** (inspiré du rythme cardiaque au repos, ~60-70bpm soit un cycle d'environ 900ms-1000ms, PAS une pulsation rapide façon alerte) appliqué discrètement à l'anneau de score et au chrome flottant en état de repos — remplace le vide du "loading" (F24) par un geste qui EST la signature de marque (F25), d'un coup.
- Conserver l'easing existant `ScoreRevealEasing` ("quiet, confident") — déjà exactement le bon ton, à réutiliser comme base du pouls plutôt qu'à remplacer.
- **Garde-fou explicite** : pas de glitch, pas de scan-line qui traverse l'écran, pas de "boot sequence" façon interface de vaisseau spatial. Le mouvement reste organique et lent, jamais mécanique/saccadé.

---

## ─── ICÔNES ────────────────────────────────────────────────────

Remplacer Material Rounded générique (F32) par une bibliothèque à trait fin et légèrement plus géométrique — dans l'esprit de **Phosphor** (style "light" ou "regular", pas "bold" ni "fill") plutôt que "Duotone" (trop décoratif/futuriste pour cette audience). Cohérent avec une lecture "instrument précis mais doux", jamais "interface de combat".

**Garde-fou** : pas d'iconographie façon "viseur", "radar", "biométrie militaire" — rester sur des pictogrammes simples et chaleureux, seulement plus raffinés que le Material par défaut.

---

## ─── COMPOSANTS ───────────────────────────────────────────────

- **Boutons/cartes** : conserver les primitifs actuels (`ScanEatButton`, `ScanEatCard` à 3 niveaux d'emphase) — déjà bons, juste habillés de la nouvelle palette/matière.
- **Le geste "goutte" organique** (voir §SHAPE) réservé exclusivement à l'anneau de score (`ScoreRing`) — c'est LE candidat naturel pour porter à la fois la forme organique unique et le pouls de mouvement : devient le véritable "geste visuel central" comparable à Yuka (résout F11/F33).
- **Empty states / Loading** : le "loading" (seul état sans personnage, F24) hérite du pouls respiratoire au lieu d'un spinner Material nu — cohérence immédiate avec le concept.
- **Widget** (F35) : à réaligner sur la palette Colors.kt réchauffée plutôt que sur `GlanceTheme` dynamique — pour que la "seconde peau" reste reconnaissable même sur l'écran d'accueil.

---

## ─── IDENTITÉ ─────────────────────────────────────────────────

**Élément signature** : l'anneau de score qui respire doucement au repos et se resserre avec confiance à la révélation du score (`ScoreRevealEasing`, déjà existant) — un seul geste qui porte à la fois la forme (goutte organique), la couleur (pouls corail), et le mouvement (signature).

**Position concurrentielle** (rappel Étape 4) : aucun concurrent (Yuka, MyFitnessPal, Cronometer, Lifesum, Noom) ne combine matière vivante + précision clinique assumée + accessibilité comme valeur de marque — ce brief occupe cet espace blanc sans jamais sombrer dans l'esthétique "gadget tech" qui exclurait une partie du public.

---

## ─── PROPORTIONS ──────────────────────────────────────────────

Aucun changement structurel proposé ici — conserver la composition actuelle (chrome flottant, colonne simple). La "seconde peau" est un concept de matière/couleur/mouvement, pas de mise en page. Le vide responsive (F29) reste un chantier séparé, indépendant de cette direction artistique.

---

## Auto-vérification contre le garde-fou de l'utilisateur

| Piège à éviter | Est-ce présent dans ce brief ? |
|---|---|
| Néon / cyan électrique / vert matrix | Non — palette reste chaude, dérivée du corail existant |
| Scanlines, glitch, "boot sequence" | Non — mouvement organique lent (pouls), pas mécanique |
| Typographie hacker/mono agressive | Non — humaniste géométrique douce (Outfit/Manrope/Jakarta) |
| Formes anguleuses/hexagones militaires | Non — un seul geste organique doux (goutte), reste minoritaire |
| Iconographie "viseur/radar" | Non — trait fin classique, pas tactique |
| Jargon visuel excluant un public non tech | Non — chaque choix reste lisible/chaleureux en premier lieu |

---

**Prochaine étape suggérée** : si ce brief est validé, il devient la référence pour ré-ouvrir concrètement les findings F3/F14/F15/F16/F17/F18/F19/F24/F25/F32/F35 (déjà cartographiés dans `step12-synthesis.md`) sous la forme de tickets d'implémentation — toujours en attente de ton feu vert avant tout code.
