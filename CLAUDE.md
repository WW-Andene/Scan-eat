# Scan-eat

## Design tokens — échelle base-2

Toutes les dimensions dp (spacing, tailles d'icônes, rayons de coin, etc.)
de l'app Android (`scan-eat-android/app`) doivent suivre une échelle unique
en base 2 : `{2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128}` dp.

Règles :

- Préférer toujours un token nommé du design system
  (`Spacing.*`, `IconSize.*`, `CardRadius.*` dans
  `scan-eat-android/app/src/main/java/fr/scanneat/presentation/ui/theme/`)
  plutôt qu'un littéral `.dp` ad hoc.
- Un littéral `.dp` doit être une valeur unique de l'échelle ci-dessus, ou
  une somme de tokens de l'échelle **distincts, chacun utilisé une seule
  fois** (ex. `16.dp + 8.dp`). Interdit : répéter deux fois le même token
  dans une somme (`16.dp + 16.dp`) ou multiplier une valeur pour atteindre
  une autre taille.
- Exceptions documentées : `0.dp` (valeur nulle, hors échelle par nature)
  et les bordures hairline `1.dp` / `0.5.dp` (traits fins d'1px).

Voir l'en-tête de `Spacing.kt` pour le détail de la règle.
