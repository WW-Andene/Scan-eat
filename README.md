# Scan'eat

Scan'eat est une application Android qui note la qualité nutritionnelle des produits alimentaires (et, pour certains produits non-consommables, leur composition) à partir d'un scan de code-barres ou d'une photo d'étiquette, en tenant compte du profil santé de l'utilisateur (allergies, conditions médicales, médicaments, régime, objectifs).

## Philosophie de l'application

Cette philosophie a été posée explicitement par le porteur du projet et gouverne toutes les décisions de conception, de scoring et de correction de bugs de ce dépôt — elle n'est pas une note ponctuelle, c'est un principe permanent :

> Le plus important et prioritaire est toujours la confiance des utilisateurs : ils ne peuvent pas vérifier ou savoir, et dépendent donc aveuglément des outils proposés. L'application doit donc être **irréprochable en tout point, c'est un absolu**.
>
> Mais également : la précision des données et la véracité scientifique, une information sourcée, une vérification systématique (même en l'absence de source par défaut, toujours essayer d'en trouver une autre), et l'adaptabilité/conscience du contexte applicable.
>
> Sources préférées lorsqu'un équivalent existe : les sources européennes (EFSA, ANSES/CIQUAL, SCCS/CosIng, ANSM/EMA, HAS) plutôt que non-européennes (USDA, FDA, OMS générique).

Concrètement, cela signifie :

- **Aucune affirmation nutritionnelle, cosmétique ou médicale n'est ajoutée au code sans source vérifiable.** Quand aucune source par défaut n'existe, une source alternative est activement recherchée avant d'écrire quoi que ce soit — jamais laissée non sourcée par facilité.
- **Le contexte prime sur la règle générale.** Un même nutriment, un même mot-clé ("eau", "protéines", "gras saturé"...) n'a pas le même sens selon la catégorie de produit : de l'eau brute n'est pas un plat préparé contenant de l'eau ; un poisson ordinaire n'est pas une barre protéinée ; une restriction protéique rénale est un budget quotidien absolu, pas un jugement relatif à la catégorie du produit scanné.
- **Toute divergence entre un commentaire de code et le comportement réel du code est traitée comme un bug à haute confiance**, pas comme un détail — un commentaire qui affirme une parité avec une autre vérification doit être vérifié ligne à ligne.
- **Aucune information "en double" ou trompeuse** ne doit laisser croire à l'utilisateur qu'un même risque est signalé deux fois de façon indépendante quand ce n'est qu'un seul et même fait.
- **Les audits de logique/contexte se font par sections, en plusieurs passes**, jamais en un seul passage superficiel — chaque passe cherche activement de nouvelles instances des classes de bugs déjà trouvées, pas seulement les redites des trouvailles précédentes.

## Structure du dépôt

```
Scan-eat/
├── scan-eat-android/   Application Android (Kotlin, Jetpack Compose, Hilt, Room)
├── docs/               Audits de design, notes techniques
└── fastlane/           Configuration de publication Play Store
```

Tout le fonctionnement est dans l'application : il n'y a ni serveur ni service d'IA. Le scan
par code-barres interroge Open Food Facts ; les photos (étiquettes, aliments, recettes) sont
lues sur l'appareil avec ML Kit (reconnaissance de texte et d'images intégrée), puis notées
par le moteur de scoring local.

## Démarrer

**Application Android** — voir `scan-eat-android/` (Gradle standard, `./gradlew build`).

## Confidentialité

Voir `PRIVACY_POLICY.md` — aucune donnée de santé ne quitte l'appareil sauf action explicite de l'utilisateur (scan code-barres, recherche en ligne, import d'une recette depuis un lien, synchronisation Health Connect).
