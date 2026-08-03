# Politique de confidentialité — Scan'eat

_Dernière mise à jour : [À COMPLÉTER — date de publication]_

Cette politique de confidentialité décrit comment l'application Scan'eat ("l'application", "nous") traite les données lorsque vous l'utilisez.

## 1. Résumé

Scan'eat est conçue pour garder vos données **sur votre appareil** dans la mesure du possible. Il n'existe pas de compte utilisateur, pas de serveur central Scan'eat, et aucune donnée personnelle n'est vendue ni partagée à des fins publicitaires.

Trois cas font sortir des données de votre appareil, chacun décrit en détail ci-dessous :
1. Le scan d'un code-barres (interrogation d'Open Food Facts).
2. Le scan d'une étiquette par photo/IA (envoi de l'image à un fournisseur d'IA tiers, uniquement si vous activez cette fonctionnalité).
3. La recherche en ligne dans "Recherche" (interrogation d'Open Food Facts par mot-clé).

## 2. Données collectées et stockées localement

Scan'eat stocke localement, sur votre appareil uniquement (base de données Room + DataStore chiffré) :

- **Profil de santé** : allergènes déclarés, conditions de santé (ex. diabète, grossesse, maladie rénale, SCI/Crohn, etc.), régime alimentaire, sexe, âge, poids, taille, objectif de poids, niveau d'activité. Les allergènes et conditions de santé sont chiffrés au repos via le Keystore Android.
- **Historique alimentaire** : produits scannés, repas du journal, recettes, aliments personnalisés, modèles de repas.
- **Suivi complémentaire** : poids, hydratation, activité physique, jeûne, traitements/médicaments (aide-mémoire manuel, non médical), dépenses alimentaires.
- **Clés API personnelles** : si vous renseignez une clé Groq ou Cerebras (pour le scan par photo), elle est chiffrée au repos et n'est utilisée que pour vos propres appels à ce fournisseur.
- **Préférences d'application** : langue, thème, réglages d'accessibilité.

Aucune de ces données n'est envoyée à un serveur Scan'eat, car il n'en existe pas — l'application ne dispose d'aucun backend propriétaire collectant vos données.

## 3. Données envoyées à des tiers

| Fonctionnalité | Donnée envoyée | Destinataire | Quand |
|---|---|---|---|
| Scan d'un code-barres | Le code-barres scanné | Open Food Facts (base de données ouverte et publique) | À chaque scan par code-barres — fait partie intégrante de la fonctionnalité |
| Recherche en ligne | Le texte tapé dans la barre de recherche | Open Food Facts | Uniquement si vous appuyez sur "Rechercher en ligne" |
| Scan par photo (optionnel) | La ou les photos prises de l'étiquette/produit | Le fournisseur d'IA que vous avez choisi et configuré (Groq et/ou Cerebras) | Uniquement si vous avez renseigné une clé API et utilisez cette fonctionnalité |
| Health Connect (optionnel) | Poids, exercice, hydratation, calories, nutrition | Health Connect (Android, local à l'appareil) | Uniquement si vous activez la synchronisation Health Connect |

Scan'eat n'a aucun contrôle sur la façon dont Open Food Facts, Groq ou Cerebras traitent les données une fois reçues — consultez leurs propres politiques de confidentialité respectives.

## 4. Ce que nous ne faisons jamais

- Nous ne vendons aucune donnée.
- Nous n'utilisons aucune donnée à des fins publicitaires ou de profilage marketing.
- Nous n'avons pas de serveur d'analytics/tracking tiers intégré.
- Vos données de santé (allergènes, conditions, traitements) ne quittent jamais votre appareil, sauf via les mécanismes explicites décrits en section 3 que vous déclenchez vous-même.

## 5. Suppression de vos données

Désinstaller l'application supprime l'intégralité des données stockées localement. Un export/sauvegarde manuel est disponible dans Réglages ; supprimer ce fichier de sauvegarde relève de votre propre gestion de fichiers.

## 6. Avertissement médical

Scan'eat ne fournit pas d'avis médical. Les scores, estimations et recommandations sont informatifs et ne remplacent pas l'avis d'un professionnel de santé. Voir aussi les mentions légales complètes dans Réglages > Mentions légales.

## 7. Enfants

Scan'eat ne cible pas spécifiquement les enfants et ne collecte pas sciemment de données concernant des enfants de moins de [À COMPLÉTER — âge selon votre marché, ex. 13 ou 16 ans].

## 8. Contact

Pour toute question concernant cette politique de confidentialité : [À COMPLÉTER — adresse e-mail de contact]

## 9. Modifications

Cette politique peut être mise à jour ; la date en haut de ce document reflète la dernière révision.

---

# Privacy Policy — Scan'eat (English)

_Last updated: [TO FILL IN — publication date]_

This is the English equivalent of the French policy above; keep both in sync when either changes. Summary: Scan'eat stores your data locally on-device by default. Barcode scans query Open Food Facts (a public, open database) as an integral part of that feature. Optional photo/AI-based scoring sends the photo only to the AI provider you personally configure (Groq/Cerebras) with your own API key, only when you use that feature. Optional online search similarly queries Open Food Facts by keyword when you tap "Search online". Optional Health Connect sync shares weight/exercise/hydration/nutrition locally with Android's Health Connect if you enable it. No Scan'eat backend server exists, no data is sold, and no advertising/tracking SDK is integrated. See the French version above for full section-by-section detail — translate/adapt as needed for your English store listing.
