# Scan'eat — Checklist de publication (Google Play)

État au [voir date du dernier commit sur cette liste]. Basé sur un audit complet du repo (`app/build.gradle.kts`, `AndroidManifest.xml`, CI, secrets, données personnelles).

Légende : ✅ déjà fait dans le code · 🛠️ préparé dans ce commit, à finaliser par vous · 🔴 action qui ne peut être faite que par vous (compte, secrets, comptes tiers)

## 1. Build & signature

- ✅ `applicationId = fr.scanneat.app`, `minSdk 26`, `targetSdk 35`, `compileSdk 35` — conformes aux exigences actuelles du Play Store (targetSdk 35 est au-dessus du minimum exigé).
- ✅ `versionCode = 2`, `versionName = "1.1.0"` — pensez à incrémenter `versionCode` à chaque nouvel envoi sur le Play Store (jamais deux fois le même).
- ✅ `isMinifyEnabled = true` + `isShrinkResources = true` + ProGuard en release — build de production optimisé.
- ✅ Le `signingConfig` release est déjà conditionnel (lit `keystore.properties` ou des variables d'environnement `RELEASE_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`) — aucune clé n'est committée en clair.
- 🔴 **Générer un keystore de release** (si vous n'en avez pas déjà un) :
  ```
  keytool -genkeypair -v -keystore release.keystore -alias scaneat -keyalg RSA -keysize 2048 -validity 10000
  ```
  Conservez ce fichier et son mot de passe **en lieu sûr, hors du repo** — le perdre signifie ne plus jamais pouvoir mettre à jour l'app sous le même applicationId sans passer par Play App Signing dès le départ.
- 🔴 **Recommandé** : activer **Play App Signing** lors de la première publication (Google gère la clé d'upload à votre place, réduit le risque de perte).
- 🔴 Ajouter les secrets CI si vous voulez que GitHub Actions produise un `.aab` signé (voir section 5).

## 2. Permissions & conformité technique

- ✅ Toutes les permissions Android 12+ (`android:exported`) sont explicitement déclarées — pas de blocage à la compilation/publication.
- ✅ `usesCleartextTraffic="false"` — tout le trafic réseau passe en HTTPS.
- ✅ `allowBackup="false"` — cohérent avec des données de santé sensibles.
- ✅ Permissions Health Connect déclarées individuellement (lecture/écriture poids, exercice, hydratation, calories, nutrition) plutôt qu'une permission globale.
- 🔴 Si votre app utilise Health Connect en production, vous devrez remplir le **formulaire d'accès Health Connect** de Google (déclaration d'usage) séparément du Play Console classique — prévoyez un délai de revue.

## 3. Politique de confidentialité

- 🛠️ `PRIVACY_POLICY.md` créé à la racine du repo (FR + EN) — décrit précisément : données stockées localement, ce qui part vers Open Food Facts, ce qui part vers Groq/Cerebras (uniquement si activé), Health Connect.
- 🔴 **Cette politique doit être hébergée à une URL publique** pour être acceptée par Play Console (le texte in-app dans Réglages ne suffit pas). Options simples :
  - **GitHub Pages** : activez Pages sur ce repo (Settings > Pages), servez `PRIVACY_POLICY.md` (ou copiez-le en `docs/index.html`/`index.md` selon la config Pages) → URL du type `https://ww-andene.github.io/Scan-eat/`.
  - Ou un simple gist/page statique sur un domaine que vous contrôlez.
- 🔴 Complétez les deux champs `[À COMPLÉTER]` du fichier (âge minimum selon votre marché, adresse e-mail de contact) avant publication.

## 4. Data Safety (formulaire Play Console)

- 🛠️ `docs/PLAY_DATA_SAFETY.md` créé — mappe chaque catégorie de donnée (santé, activité, photos, etc.) sur les cases exactes du formulaire Play Console "Sécurité des données", avec ce qui est collecté/partagé et pourquoi.
- 🔴 Ce document est une aide de saisie, **pas un remplacement** — vous devez cocher les cases correspondantes vous-même dans Play Console (Contenu de l'app > Sécurité des données).
- 🔴 Comme l'app traite des **données de santé**, vérifiez la section "Politiques relatives aux applications de santé" de Play Console (déclaration supplémentaire possible selon les catégories cochées : allergènes/conditions médicales).

## 5. CI/CD

- ✅ CI actuelle (`android-build.yml`) : tests + lint + `assembleDebug` + `assembleRelease` (non signé, juste pour vérifier que R8/minification ne cassent rien).
- 🔴 **Aucune automatisation de publication n'existe** (pas de Fastlane, pas de plugin Gradle Play Publisher). Pour publier :
  - **Option manuelle (recommandée pour commencer)** : générez un `.aab` signé localement (`./gradlew bundleRelease` avec `keystore.properties` rempli) et téléversez-le à la main dans Play Console.
  - **Option automatisée (plus tard)** : ajoutez le plugin [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) + un compte de service Google Cloud, puis un secret GitHub Actions `PLAY_SERVICE_ACCOUNT_JSON` + les 4 secrets de signature déjà attendus par `build.gradle.kts` (`RELEASE_STORE_FILE` en base64, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Je peux préparer ce workflow si vous voulez automatiser dès maintenant.

## 6. Fiche Play Store (store listing)

- 🛠️ `fastlane/metadata/android/{fr-FR,en-US}/title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/2.txt` créés — textes prêts à copier-coller dans Play Console (ou à utiliser directement si vous adoptez Fastlane plus tard).
- 🔴 **Icône d'app** : déjà présente dans le code (`res/mipmap-anydpi-v26/ic_launcher.xml`, adaptive icon) — Play Console demande en plus un PNG 512×512 séparé à l'upload, à exporter depuis Android Studio (Image Asset Studio) ou à me fournir si vous voulez que je génère un PNG depuis les ressources vectorielles existantes.
- 🔴 **Graphique vedette (feature graphic)** : 1024×500 px, **obligatoire**, n'existe pas encore. Je peux en concevoir un si vous le souhaitez (à faire dans un outil de design, hors de ce repo de code).
- 🔴 **Captures d'écran** : minimum 2 par format d'appareil (téléphone obligatoire ; tablette recommandée si l'app est optimisée tablette). Nécessite de lancer l'app sur un émulateur/appareil réel — je ne peux pas les générer sans un environnement Android exécutable disponible dans cette session.
- 🔴 **Catégorie** : suggestion "Santé et remise en forme" ou "Alimentation et boissons" selon le positionnement souhaité.
- 🔴 **Classification du contenu** : questionnaire IARC à remplir dans Play Console (probablement "Tout public", mais confirmez selon le contenu de mentions médicales).
- 🔴 **Coordonnées de contact** développeur (e-mail obligatoire, site web/téléphone optionnels) à renseigner dans Play Console.

## 7. Compte développeur

- 🔴 Un compte Google Play Console (frais unique de 25 $) est requis si vous n'en avez pas déjà un.
- 🔴 Vérification d'identité développeur (peut prendre plusieurs jours selon votre pays).

## 8. Ce que je peux faire ensuite si vous le souhaitez

- Générer le PNG 512×512 de l'icône à partir des ressources vectorielles existantes.
- Concevoir un brouillon de graphique vedette (texte + palette de l'app).
- Écrire le workflow GitHub Actions de build signé + upload automatique (Gradle Play Publisher) une fois que vous avez un compte de service Google Cloud.
- Vérifier/adapter les mentions légales existantes (`settings_legal_*`) si Play exige des libellés spécifiques après revue du questionnaire santé.

Le reste (compte développeur, keystore, captures d'écran réelles, hébergement de la politique de confidentialité) nécessite une action de votre part car cela touche des comptes externes, des secrets, ou l'exécution réelle de l'app sur un appareil.
