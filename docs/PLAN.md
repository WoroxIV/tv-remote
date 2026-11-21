# Plan de développement

## Objectif
Une télécommande Android gratuite, sans publicité ni collecte de données, qui pilote le plus de téléviseurs possible
sans contourner aucune sécurité : uniquement les API officielles de contrôle à distance que les TV exposent
sur le réseau local, et l'infrarouge.

## Architecture
- **Kotlin + Jetpack Compose (Material 3)**, une seule activité, `minSdk 24`, `targetSdk 36` (exigence Play Store).
- `model/` — TV, protocoles, touches, états de connexion.
- `protocol/` — une implémentation de `TvController` par protocole :
  - `androidtv/` : Android TV Remote v2 (protobuf encodé à la main, certificat client X.509 auto-signé généré sur le téléphone, appairage par code).
  - `SamsungController`, `LgController` (WebSocket via OkHttp), `RokuController` (HTTP), `ir/` (ConsumerIrManager + encodeurs NEC, Samsung32, Sony SIRC, RC5, Panasonic).
  - `PinnedTrust` : les TV ont des certificats auto-signés ; leur empreinte est mémorisée à la première connexion et vérifiée ensuite (trust-on-first-use).
- `discovery/` — mDNS `_androidtvremote2._tcp` + SSDP (Roku, Samsung, LG).
- `data/DeviceStore` — TV enregistrées et jetons d'appairage dans le stockage privé de l'app.
- `ui/` — écran liste des TV, écran télécommande, composants (D-pad, boutons, rockers).

## Étapes
1. ✅ Squelette Gradle, thème, icône adaptative.
2. ✅ Protocoles réseau (Android TV, Samsung, LG, Roku) + infrarouge.
3. ✅ Découverte automatique et ajout manuel.
4. ✅ Interface : D-pad, volume/chaînes, médias, pavé numérique, clavier, appairage.
5. ✅ Tests unitaires (protobuf, certificat, secret d'appairage, codes IR, WoL) et captures d'écran Robolectric.
6. ✅ APK/AAB signés, CI GitHub Actions.
7. ⏭ Tests sur de vraies TV de chaque marque et corrections.
8. ⏭ Traduction anglaise, fiche Play Store, captures, publication.

## Pistes ensuite
- Pavé tactile (souris) pour LG/Android TV, lancement d'applis (YouTube, Netflix), widget et tuile de réglages rapides,
  Sony Bravia IRCC et Philips JointSpace pour les modèles non Android, Fire TV (nécessite le débogage ADB).
