# Railgun — caméra, modèle et SFX de référence

> **État au 8 octobre 2026 : réanalyse appliquée au client uniquement.** Les [258 échantillons espacés de 0,1 s et la chronologie appliquée](railgun-reference-100ms.md) remplacent l’ancienne lecture : **19 plans**, charge dès 11,9 s, contraction à 18,7–19,3 s et extinction visuelle à 19,3 s, résidu sombre/liseré jusqu’au fondu 25,0–25,7 s ; caméra et audio alignés. **Dégâts serveur inchangés : 100 bruts/s de 14,3 à 22,7 s, rayon 200, terrain intact, aucun chunk généré ou chargé de force.** La chronologie à seize plans, les résultats et archives ci-dessous sont historiques. Le [bilan actuel](verification.md) consigne 30 tests, 43 captures Railgun plus le menu et la fin naturelle à 515 ticks ; ni fluidité continue à 60 FPS, ni cercle complet à distance serveur 4, ni reproduction du cratère, ni rendu du JAR en production ne sont prouvés.

> Historique de la passe cinématique. Depuis la passe « Railgun seul », le terrain reste intact et l’impact applique 100 dégâts bruts/s pendant 8,4 s ; les passages ci-dessous concernant le terrassement décrivent l’ancienne version. Le JAR et les vérifications actuels sont dans `docs/verification.md`, les prompts SFX dans `docs/elevenlabs-sound-prompts.md`.

## Référence utilisée

Vidéo locale : `Minecraft The Shooting Star Mod Is INSANE 🌠#shorts - Sigma Villager (1080p).mp4`, durée **25,704 s**, **1080 × 1920**, **60 FPS**. Analyse des coupes et de 24 images précises. La séquence observée est **SS-01 Railgun** ; cette passe porte sur cette arme.

Les images du mod sont rendues au format de la fenêtre Minecraft. La comparaison portrait utilise un recadrage central, sans imposer un écran vertical au joueur. Les textes et marques du montage vidéo ne sont pas ajoutés au jeu.

## Livrables

- `RailgunCinema.java` : montage en perspective, recul spatial, travelling, flash, flare, tunnel de vitesse et contraste rouge/noir/blanc.
- `RailgunCameraPath.java` : seize plans et interpolation des positions, cibles, roulis et FOV.
- `CinematicCamera.java` : caméra de rendu terrestre, plan lointain temporaire, restauration à chaque fin de frame et à la fermeture.
- `OrbitalRailgunModel.java` et `assets/stellar/models/orbital_railgun.mesh` : canon à huit rails, panneaux, nervures, trusses, stabilisateurs, chambre et ouverture animée des rails.
- `models/orbital_railgun.bbmodel` : source éditable du canon, **1 121 cuboïdes** et **6 726 quads**.
- `CelestialModels.java` et cinq textures originales : Terre, nuages, Saturne, anneaux et galaxie.
- `CinematicSoundtrack.java`, `assets/stellar/sounds.json` et onze OGG : ascension, warp, flyby, charge, claquements électriques, tir, tunnel, descente, impact, faisceau et grondement final.
- `WorldEffects.java` : rideau rouge, cœur blanc étroit, rayons verticaux, onde et bord lumineux résiduel.

Scripts de génération reproductible : `scripts/generate-orbital-railgun.py`, `scripts/generate-celestial-textures.py`, `scripts/generate-cinematic-sfx.py`. Le modèle, les textures et les sons sont des créations originales.

## Chronologie

Les temps sont ceux du lancement local à **20 ticks/s**, synchronisés au tick serveur reçu. Un ralentissement du serveur ralentit donc également la séquence.

| Temps | Plan |
| --- | --- |
| 0–2,20 s | Lanceur et cible dans le monde, puis ascension |
| 2,20–3,40 s | Traversée des nuages |
| 3,40–5,70 s | Recul depuis la Terre |
| 5,70–6,50 s | Saturne |
| 6,50–7,25 s | Orbites du système solaire |
| 7,25–9,30 s | Galaxie |
| 9,30–10,40 s | Vue large du canon |
| 10,40–11,55 s | Travelling du canon |
| 11,55–12,10 s | Stabilisateur et arrière |
| 12,10–12,70 s | Chambre, ouverture et charge |
| 12,70–13,10 s | Tir et flash blanc |
| 13,10–13,75 s | Warp, puis tunnel rouge/noir/blanc |
| 13,75–14,20 s | Flare dans le ciel |
| 14,20–14,30 s | Descente du faisceau |
| 14,30–22,70 s | Impact dans le monde |
| 22,70–25,70 s | Survol final, extinction et restauration |

## Vérification effectuée

### Build et données

- Compilation Java 8, tests et `reobfJar` : succès.
- **21 tests unitaires**, zéro échec ; contrôle supplémentaire des coupes, de la perspective et de la synchronisation serveur.
- `verifySides` : les classes communes/serveur ne dépendent pas du client ou de LWJGL.
- Parité de la géométrie statique entre `.bbmodel` et mesh vérifiée ; génération déterministe.
- Cinq textures : dimensions, alpha, continuité des coutures et déterminisme vérifiés.
- Onze sons : Vorbis mono 44,1 kHz, crêtes sans clipping, boucles vérifiées et génération reproductible avec le FFmpeg local.

### Client Minecraft réel

Une instance de développement Forge avec un monde plat isolé a capturé **23 images du framebuffer**, couvrant les seize plans. Il s’agit bien du rendu Minecraft, et non d’un aperçu Snowstorm ou d’un rendu logiciel du modèle.

Résultat de la dernière validation :

```text
samples=23
checked_render_frames=650
camera_restored_between_ticks=true
far_field_restored=true
cancellation_restored=true
automatic_close=true
sounds_stopped=true
eventregistration_restored=true
```

- Aucun code d’erreur OpenGL aux captures.
- La vue et le mode troisième personne sont restaurés entre les frames.
- Le plan lointain temporaire survit à la reconstruction de projection après le ciel, puis revient à sa valeur initiale.
- **J / annulation** et expiration à **514 ticks**, après vérification de l’écran encore ouvert à 513, nettoient écran, caméra, sons et écouteurs d’événements.
- Les captures de charge à **245 et 253 ticks** montrent maintenant l’intérieur, les conducteurs rouges et les anneaux blancs sans obstruction dominante des panneaux.
- Le tir part vers la gauche dans le plan de bouche du canon, comme dans la référence.

Preuves locales générées sous `build/client-validation/` :

- `runtime-contact-sheet.jpg`
- `portrait-reference-comparison.jpg`
- `screenshots/railgun-245-CHAMBER.png`, `railgun-253-CHAMBER.png`, `railgun-255-MUZZLE.png`
- `FINALVALIDATION.md` et `client.log`

## Écarts encore présents

La charge est plus serrée et davantage vue depuis l’intérieur que dans le plan latéral externe de la référence. Le tunnel est plus régulier et le flash plus blanc. Le rideau d’impact a été corrigé pour rester rouge saturé après son flash bref. Les assets originaux ne sont pas les textures ou sons exacts de la vidéo. Les transitions sonores ont été vérifiées par le moteur audio, mais pas jugées à l’écoute dans cette passe.

Archive vérifiée : `build/libs/stellar-1.0.0.jar`, **3 817 905 octets**, ZIP intègre, onze OGG, cinq PNG et le mesh présents, sans classes du pilote de test. SHA-256 : `fe394cba8d9ad37d0c383d2272de1afa3d6a0fceba8f6557fc1f1f758936c4c0`.

Le test visuel utilise un lancement synthétique local : il ne terrasse pas le monde. Le cratère final, la continuité du paysage, le coût d’une frappe destructrice complète et l’équivalence du survol dans un monde réel restent à vérifier ensemble. Le terrassement est progressif et les chunks manquants restent absents de la vue. Les essais ne couvrent pas les shader packs.

**Statut : amélioration intégrée et vérifiée ; fidélité strictement 1:1 non acquise.**

## Auto-évaluation

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | Build, données et rendu Minecraft vérifiés ; le JAR obfusqué reste à lancer dans l’instance de production. |
| Complétude | 3 | Caméra, VFX, modèle et SFX intégrés ; la comparaison révèle encore les écarts visuels et le survol de cratère n’est pas validé sur une frappe complète. |
| Clarté | 4 | Coupes, fichiers et preuves explicités ; une comparaison vidéo continue compléterait les captures fixes. |
| Utilisabilité | 4 | JAR et source Blockbench disponibles ; l’évaluation finale du paysage exige un monde représentatif. |
| Concision | 4 | Résumé utilisateur court et preuves séparées ici ; les détails de validation peuvent encore être regroupés. |

**Moyenne : 3,8 / 5.** Priorités : comparer une frappe complète dans le monde cible, ajuster les couleurs et le cadrage de charge, puis évaluer les SFX à l’écoute. Cette note correspond à une reproduction améliorée et testée, pas à une identité visuelle et sonore démontrée.
