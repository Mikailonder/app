# CochePDF — appli Android

Importe un PDF, détecte les cases à cocher (champs de formulaire + cases dessinées),
coche d'un tap, ajoute des coches à la main si besoin, puis enregistre un nouveau PDF.
Tout se passe sur le téléphone, rien n'est envoyé en ligne.

## Obtenir l'APK (sans rien installer, via GitHub)
1. Crée un dépôt privé sur github.com.
2. Envoie-y le contenu de ce dossier (bouton « Add file » > « Upload files », glisse tout le dossier).
3. Onglet **Actions** > « Construire l'APK » : la compilation se lance seule (~4 min).
4. Ouvre l'exécution terminée > section **Artifacts** > télécharge `CochePDF-apk` (zip contenant `app-debug.apk`).
5. Sur le téléphone : ouvre l'APK et autorise « Installer des applis inconnues » pour ton navigateur/Fichiers.

## Ou avec Android Studio
Ouvre le dossier, puis Build > Build APK(s).

## Fonctionnement
- `app/src/main/assets/index.html` : l'appli (pdf.js + pdf-lib, hors ligne).
- `MainActivity.java` : sélecteur de fichier, « Ouvrir avec / Partager vers CochePDF », enregistrement du PDF.
