# Design Audit — Étape 7 : Typographie

**Sources** : design-aesthetic-audit §DT1-4 (Typography) · app-audit §E4 (Typography Craft) · art-direction-engine Part II (classification, pairing, échelle, tracking).
**Statut** : audit/synthèse, **aucune modification de code**.

---

## 1. §DT1 — Classification de la police actuelle

D'après l'Étape 1 : aucune police de marque n'est déclarée dans `Type.kt` — les 15 slots `Typography()` n'ont pas de `fontFamily` explicite, donc l'app utilise la police système par défaut (généralement Roboto sur Android stock, ou la police du fabricant sur les surcouches OEM).

Selon la classification art-direction-engine §CLASSIFY, la police système par défaut d'Android relève de la famille **Grotesque** ("neutre, professionnel, invisible" — exactement la catégorie qu'art-direction-engine désigne comme un choix "par défaut" à remplacer consciemment, pas un choix actif).

**Conséquence directe** : la typographie de Scan'eat est, par définition, indiscernable de n'importe quelle app Android qui n'a pas non plus déclaré de police — ce qui objective une partie du Finding F7 (différenciation à point de défaillance unique).

---

## 2. §DT2 — Échelle et hiérarchie de poids

L'échelle (rappel de l'Étape 1) est complète et cohérente :
- 15 slots Material3 tous redéfinis (tailles 11sp→45sp, line-heights, tracking).
- Progression de tracking cohérente : négatif sur les gros titres (jusqu'à −1.0sp sur `displayLarge`), positif sur les labels/petits textes (+0.44 à +0.48sp) — c'est exactement la bonne pratique recommandée par art-direction-engine ("tight on display, open on caps/labels"). **Point fort réel, à ne pas toucher.**
- Poids utilisés : Bold (titres), SemiBold (title*), Medium (label*, titleSmall), Normal (body*). Couverture large de la gamme de poids — évite l'anti-pattern "seulement 400+600" cité dans le blacklist art-direction-engine.

**`HeroNumberStyle`** (Black + figures tabulaires) reste le seul geste typographique vraiment distinctif de toute l'app — cohérent avec le constat de l'Étape 3 (Design DNA) : c'est un gène à préserver et, potentiellement, à étendre.

---

## 3. §DT3 — Rendu et particularités

- Le mode dyslexique (`OpenDyslexicFontFamily`) est un exemple rare et positif de typographie d'accessibilité pilotée par une vraie police alternative (pas juste un espacement de lettres augmenté) — `withDyslexicSpacing()` ajuste aussi line-height (×1.35) et plafonne le tracking (max 0.6sp), ce qui montre une compréhension réelle des besoins DYS (pas un simple interrupteur cosmétique).
- Absence de `fontFeatureSettings` autre que `"tnum"` (figures tabulaires) sur `HeroNumberStyle` — pas d'autres réglages OpenType (ligatures, small caps, etc.), ce qui est cohérent avec une app utilitaire, pas un défaut en soi.

---

## 4. §DT4 — Polices variables

Aucune police variable (`Variable Font`) détectée — non applicable ici puisqu'aucune police custom n'est chargée du tout (seul OpenDyslexic, un fichier `.otf` statique, pas variable). Section à ignorer conformément au guide de skip de design-aesthetic-audit ("§DT4 : skip si pas de police variable détectée").

---

## 5. Findings — Étape 7

| # | Constat | Sévérité | Notes |
|---|---|---|---|
| **F19** | Aucune police de marque déclarée — la typographie de Scan'eat est, par construction, celle de n'importe quelle app Android par défaut. Rejoint et objective F7/F9. | **Haute** | Impact direct sur la mémorabilité — candidat naturel pour un correctif concret (choix + intégration d'une police, ex. via `res/font/`, suivant la méthode déjà en place pour OpenDyslexic) |
| **F20** | `HeroNumberStyle` (Black + tabular nums) est le seul geste typographique distinctif — bien exécuté mais isolé, utilisé seulement pour les nombres héros. Une police de marque pourrait s'appuyer sur ce geste déjà réussi plutôt que de repartir de zéro. | Info | Lien direct vers un futur correctif |

---

## 6. Ce qui n'est PAS remis en cause

- L'échelle de tracking (négatif sur les titres, positif sur les labels) est déjà conforme aux bonnes pratiques — à conserver telle quelle même après l'ajout éventuel d'une police de marque.
- Le mode dyslexique est un exemple d'accessibilité typographique à préserver et à valoriser (cf. F10, opportunité de positionnement Étape 4).
- `HeroNumberStyle` (poids Black + figures tabulaires) est un bon socle à étendre, pas à remplacer.

---

**Prochaine étape** : Étape 8 — Composants & forme (design-aesthetic-audit §DCO1-6, app-audit §E5, art-direction-engine §SHAPE/§COMPOSITION/Components).
