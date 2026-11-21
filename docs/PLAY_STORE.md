# Publier sur le Play Store

1. Créez un compte développeur Google Play (frais d'inscription unique de 25 $ — c'est Google qui le facture, pas l'app).
   Les comptes personnels récents doivent faire un test fermé (12 testeurs pendant 14 jours) avant la production.
2. Créez l'application : nom « Zap Remote », gratuite, catégorie *Outils*, « Ne contient pas de publicité ».
3. Activez *Play App Signing* et envoyez `app/build/outputs/bundle/release/app-release.aab`
   (signé avec la clé `signing/zap-remote-release.jks`, qui devient votre clé d'importation — gardez-la précieusement).
4. Section *Sécurité des données* : « Aucune donnée collectée », « Aucune donnée partagée ».
5. Politique de confidentialité : publiez `docs/PRIVACY.md` (par ex. via GitHub Pages) et collez l'URL.
6. Fiche : icône 512×512, image de présentation 1024×500, au moins 2 captures (`docs/screenshots/`).
7. Classification du contenu : questionnaire → « Tout public ».
8. Pour chaque mise à jour : augmentez `versionCode` et `versionName` dans `app/build.gradle.kts`.

Description courte suggérée : *Télécommande TV gratuite, sans pub : Google TV, Samsung, LG, Roku et infrarouge.*
