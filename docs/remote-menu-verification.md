# Vérification du menu holographique et de la télécommande SR-1

Vérifié le 15 octobre 2026 avec Java 8, Forge 1.12.2-14.23.5.2864 et un client Minecraft réel à 1280 × 720. Le serveur utilisait uniquement le monde jetable `run-server/stellar-smoke-world`, sur localhost.

## Résultats

- **Build et packaging : réussis.** `build` passe, avec la séparation client/serveur (`verifySides`). Les cinq sons du menu sont valides (`generate-remote-sfx.py --check`). `verify-artifact.py` accepte le JAR.
- **135 tests JUnit réussis**, aucun échec ni test ignoré. Les nouveaux tests couvrent le pouce : chaque touche est enfoncée à fond par les deux mains avec des phalanges rigides, le survol se pose au-dessus de chaque touche, un appui n’enfonce que sa touche et se lit en moins de 0,3 s. Côté menu, pour une prise main droite comme main gauche : la remote se place du côté de la main, chaque touche est cliquable à toutes les échelles, les cartes, le briefing et la croix restent cliquables, la matrice OpenGL correspond à la projection CPU, et ni la remote ni le doigt holographique n’atteignent les cartes. Le taux de couverture n’a pas été mesuré.
- **Interactions en jeu : réussies.** Les quatre sélections reçoivent l’accusé serveur (identifiants 1, 2, 5 et 6). Briefing, navigation clavier, H, Échap et fermeture vérifiés. Une flèche du clavier fait appuyer le pouce sur la touche correspondante de la remote du menu.
- **Première personne : réussie.** K, N et la touche FIRE font appuyer le pouce sur la bonne touche ; la sélection N est confirmée par le serveur.
- **Audio et rendu : réussis.** 38 événements audio, aucun son de menu actif après fermeture. Matrices restaurées sur 571 images sans erreur OpenGL. Caméra et réglages inchangés pendant le menu ; le monde continue de tourner.
- **23 captures réelles**, relues visuellement : menu en prise main droite (remote à droite des cartes, panneau à gauche ; ouverture, survol, briefing, appui sur la croix), échelles 1 et 3, retour au monde, première personne au repos et pendant trois appuis, troisième personne de face, de dos et de profil, icône d’inventaire.

Les preuves sont dans `build/smoke-remote/20261015-212558-24076/` : `report.json`, journaux client/serveur, dossier `screenshots/` et `remote-contact-sheet.jpg`.

## Relancer les vérifications

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot'
.\gradlew.bat --no-daemon --console=plain build exportDevLaunches -Psmoke -x downloadAssets
python scripts/generate-remote-sfx.py --check
python scripts/verify-artifact.py
python scripts/smoke-remote-menu.py --java-home "$env:JAVA_HOME"
```

`downloadAssets` n’est exclu que si les ressources Minecraft ont déjà été téléchargées. Le pilote refuse un serveur qui ne cible pas le monde jetable et les ports localhost attendus. Aucun des scripts ne doit être pointé vers une sauvegarde de jeu.

## Corrections pendant la vérification

- Le test de placement échouait quand le pouce, au-dessus des touches de droite, dépassait l’emplacement des cartes. La remote a été décalée et réduite (échelle 8,4, à 312 unités du centre). Le test distingue désormais le corps de la remote, qui doit rester au-delà des connecteurs, et le pouce, qui ne doit pas atteindre les cartes.
- La remote était à gauche alors que la main qui la tient est à droite. Le menu suit maintenant la main qui porte la remote : en main droite, panneau à gauche, cartes au centre, remote à droite, connecteurs sur le bord droit des cartes ; en main gauche, la disposition d’origine. L’inclinaison du plan et la pose de la remote sont mises en miroir ; la croix reste en haut à droite. Les pilotes de smoke visent désormais la colonne des cartes réelle au lieu d’une coordonnée fixe.
- La main en skin dessinée dans le menu a été retirée après relecture : l’avant-bras traversait l’interface. Elle est remplacée par un doigt holographique qui suit le même mouvement que le pouce, sans changer la logique d’appui.
- Sur les captures de survol, le curseur est encore légèrement sous la touche visée. H vient d’appuyer sur la touche du bas, et le curseur remonte encore au moment de la capture. Sur la capture d’appui, le curseur est bien centré sur la touche.

## Limites

Rendu vérifié avec le moteur Forge standard, sans OptiFine, shader pack ni police de resource pack. Seul le skin Alex par défaut (bras fin) a été vu en jeu ; le bras large et les manches d’autres skins sont pris en charge par le code, mais n’ont pas été capturés. Le joueur tenant la remote de la main gauche n’a pas été capturé en jeu, ni en première personne ni dans le menu ; seuls les tests unitaires couvrent le miroir. Les cinématiques, les dégâts et les paquets réseau n’ont pas été modifiés.

JAR vérifié : `build/libs/beyond-the-space-1.1.0.jar`, 10 448 757 octets.

SHA-256 : `3de552f739b1842ce8473471edf0add13a5059fd96d72eb00031094abc16f42a`.
