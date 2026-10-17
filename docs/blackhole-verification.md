# Blackhole — vérification du rework

## Spaghettification et distorsion de l'espace-temps — 15 octobre 2026

Points relevés avant ce passage :

- Aucune vraie lentille : l'anneau photonique était dessiné, mais les étoiles, la Terre, le terrain et les entités derrière le trou noir n'étaient jamais déviés.
- Étirement des modèles affine et symétrique, ancré aux pieds, sans conservation du volume (×2,7), sans gradient de marée ni alignement vers le trou noir.
- Trou noir inoffensif.
- Champ d'étoiles calculé sur la position non interpolée du joueur : tremblement des traînées en vol rapide.
- Allocation d'un `double[16]` par entité et par frame dans l'étirement.

Changements :

- `api/BlackholeTides` (partagé client/serveur) : rayon d'horizon, létalité selon la distance, fenêtre létale de 400 à 740 ticks, dégâts par impulsion de 10 ticks.
- `BlackholeField` : source de dégâts `stellar.spaghettification` qui ignore l'armure, appliquée seulement dans un chunk chargé. Réglages `blackholeLethal` et `blackholeLethalPlayers` dans `stellar.cfg`. Message de mort en anglais et en français.
- `SpacetimeLens` : passe plein écran, équation de lentille ponctuelle, test de profondeur (seul l'arrière-plan est dévié), entraînement du disque, onde gravitationnelle et frange chromatique.
- `TidalShader` : shader de sommets GLSL 120 pour les êtres vivants. Étirement plus fort côté trou noir, largeur réduite pour conserver le volume, alignement, courbure orbitale, fil à la mort. Éclairage fixe émulé ; repli affine si GLSL est indisponible ou si un autre programme est actif.
- Filaments de plasma tirés des corps vers l'horizon ; étoiles interpolées ; matrice réutilisée.
- Les deux shaders sont compilés dès le lancement, joueur encore au sol. Une compilation au premier usage tombait en pleine montée (capture de 364 ms au tick 100 du passage `20261015-222310`).

Vérifications : `gradlew build exportDevLaunches -Psmoke` réussi, séparation client/serveur vérifiée, **147 tests JUnit réussis** (dont `BlackholeTidesTest` 4, `BlackholeVisualMathTest` 23, `BlackholeFieldTest` 8). Les corps partis de 12, 90, 400 et 1000 blocs meurent tous entre 400 et 740 ticks, et aucun dégât n'est infligé avant 400 ticks.

Test connecté `build/smoke-blackhole/20261015-223136/` : PASS côté client, 36 captures.

- Shaders prêts à 30 ticks, Y=4.
- Passes de lentille : 722 puis 1133 ; modèles déformés par le shader : 700 puis 1650 ; modèles de mobs mourants : 145 puis 76.
- Aucun mob gardé jusqu'à la fin n'a survécu. Quatre messages `BhVictim was spaghettified by a black hole` reçus par le propriétaire des loups témoins.

`report.json` reste `passed: false` : un avertissement `moved too quickly` pendant la montée. Signature identique (`dy=12.553808246508027`) aux passages `20261009-135141` et `20261012-180217`, antérieurs à ce travail. Problème de montée préexistant, non traité ici.

Limites :

- Un mob dont le chunk se décharge (orbite loin du seul joueur) est reposé au sol et échappe aux dégâts. Observé dans 2 passages sur 6 (un cochon, un mouton à pleine vie).
- La lentille ne travaille que sur l'image affichée : rien hors écran ne peut être ramené dans le champ. Avec un shader pack, la passe est ignorée et l'étirement revient au repli affine.
- En survie, le lanceur meurt aussi par défaut (« létal pour tous »). `blackholeLethalPlayers=false` épargne les joueurs ; le smoke l'utilise pour observer le champ complet.

## Revue VFX, caméra et audio — 12 octobre 2026

- Transition vers l'espace continue entre Y=300 et Y=340 : suppression du saut initial de 20 % de luminosité, de brouillard et d'intensité audio.
- Doppler calculé dans le repère du disque incliné à 15°, avec le signe de sa rotation. Disque et lentille éclairent maintenant le même côté approchant. La normalisation de l'observateur est calculée une fois par passe au lieu de chaque sommet du disque.
- Arcs de lentille alignés sur le disque projeté et atténués dans la vue polaire ; l'anneau photon devient symétrique au pôle. Direction visuelle inspirée de la [description NASA du disque, de la lentille et du Doppler](https://science.nasa.gov/universe/black-holes/anatomy/). Le rendu reste une approximation géométrique.
- Yaw et pitch du joueur interpolés ; yaw du travelling et du joueur déroulés séparément pour éviter un changement de tour à ±180°. La position orbitale et la restitution de caméra entre les frames sont conservées.
- Hauteur des trois boucles audio modulée doucement avec l'approche et l'entrée. Une interface ou une sortie de la zone suspend les sources sans réarmer les transitoires.
- Sources audio terminées retirées selon l'état du moteur. Une source muette ou échouée ne reste pas possédée indéfiniment : attente de démarrage bornée à 100 ticks. Cela corrige notamment le dernier rapport du 9 octobre, qui échouait avec `Ending left blackhole audio owned`.

Vérifications de cette revue : compilation Java 8 des 57 sources principales, des 12 sources de test et du smoke ; **88 tests JUnit réussis** ; séparation client/serveur des 41 classes partagées ; contrôle des sept OGG Blackhole et des onze OGG Railgun.

Test connecté final réussi : `build/smoke-blackhole/20261012-181506/report.json`, `passed: true`. Les six empreintes de classes du rapport correspondent aux classes compilées actuelles.

- Deux séquences en survie, interruption locale par J puis annulation, et fin naturelle : retour au sol, capacités et caméra restaurés.
- **36 captures réelles**, toutes écrites avant PASS ; captures dans `screenshots/`, vue d'ensemble dans `contact-sheet.jpg`.
- Séquence naturelle : 537 ticks consécutifs au-dessus de Y=300 ; 87 frames de travelling et sept frames de rotation manuelle traversant l'orientation opposée ; modèles du joueur et des mobs étirés et restitution de caméra vérifiés.
- Sept événements audio observés en lecture ; recul d'horloge de trois ticks sans répétition ; interface ouverte à 440 ticks et fermée à 445, sans sources conservées pendant la pause ni transitoires rejoués à la reprise ; aucune source possédée après la fin.
- Aucun avertissement de déplacement rapide, erreur OpenGL ou déconnexion anti-vol. Les passages `20261012-180928` et `20261012-181210` ont aussi réussi avant les derniers ajustements visuels.

Le banc de test conserve la capture OpenGL sur le thread client, puis encode les PNG en arrière-plan avec une attente et une propagation des erreurs avant PASS. Les mesures du passage `20261012-180928` donnent 25,47 ms maximum pour la capture et 181,21 ms pour l'encodage. Les captures synchrones provoquaient des rafales de paquets et de véritables avertissements de mouvement. Les commandes du serveur de test passent aussi par sa console, pour éviter les mutations du monde depuis le thread RCON. Aucun seuil de contrôle réseau du jeu n'a été modifié.

Le build Gradle via le plugin MCP échoue avant la configuration du projet : son script d'initialisation utilise `BuildOperationProgressEventEmitter`, absent de Gradle 5.6.4. La compilation de cette revue utilise `javac` avec les dépendances des manifestes de développement existants. Le JAR de distribution n'a pas été régénéré ; les preuves de build et de JAR ci-dessous concernent les versions historiques.

Le test Railgun avec fin naturelle échoue sur sa fenêtre de synchronisation (`Film closed outside the natural-ending window: actualLaunchAge=509.0`, fenêtre attendue à partir de 513 ticks). Ce passage ne valide donc pas sa fin naturelle ; les logs sont dans `build/smoke/`.

Limites audio : pas d'écoute subjective du mix final. Une boucle rejetée lorsque le volume est nul, ou perdue lors d'un rechargement des ressources, n'est pas recréée automatiquement sans suspension/reprise ou nouveau lancement. Les tests vérifient son nettoyage, pas cette reprise particulière.

## Livrable

- JAR Forge 1.12.2 : `build/libs/beyond-the-space-1.0.0.jar` (nom affiché : Beyond The Space).
- Direction visuelle et sept prompts audio : [blackhole-elevenlabs-prompts.md](blackhole-elevenlabs-prompts.md).
- Rendu Java/OpenGL avec les ressources planétaires existantes, sans particules Blockbuster.

## Vérifications réalisées

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot'
.\gradlew.bat --no-daemon --console=plain build exportDevLaunches -Psmoke -x downloadAssets
python scripts/smoke-blackhole.py --java-home "$env:JAVA_HOME"
```

- Compilation, obfuscation et contrôle des dépendances physiques client/serveur : réussis.
- JUnit : **50 tests réussis**, zéro échec, zéro erreur ; dont 12 tests de mathématiques visuelles du blackhole.
- Test visuel du rework : `build/smoke-blackhole/20261008-204948/report.json`, `passed: true`.
- Deux séquences en survie, monde plat jetable, serveur et client Forge réels.
- **36 captures du framebuffer Minecraft**, dans `build/smoke-blackhole/20261008-204948/screenshots/` ; vue d'ensemble dans `contact-sheet.jpg`.
- Caméra orbitale observée sur 62 frames de la séquence naturelle ; restitution du render-view entity vérifiée à chaque fin de frame.
- Séquence d'interruption : J à 230 ticks, pendant le travelling ; caméra/FOV supprimés, effets du monde conservés, puis annulation serveur et atterrissage.
- Séquence naturelle : 548 ticks consécutifs au-dessus de Y=300, fin de lancement à 801 ticks observés côté client, capacités de survie et position au sol restaurées.
- Aucun avertissement de déplacement rapide, erreur OpenGL ou déconnexion anti-vol dans le dernier test.

## Correction mise en évidence par les tests

Les deux premiers passages ont terminé les séquences mais échoué au contrôle final des journaux : après annulation, un paquet d'atterrissage était comparé à l'ancienne position spatiale. Le handshake de téléportation existant ne réinitialisait pas les deux références de mouvement avant un acquittement reçu dans le même tick réseau.

Le retour du blackhole appelle maintenant `NetHandlerPlayServer.captureCurrentPosition` juste après `setPlayerLocation`, via le nom MCP/SRG vérifié dans les mappings 1.12.2. Les deux passages connectés suivants ont réussi ; le dernier ajoute l'interruption pendant la nouvelle caméra et le contrôle de restitution entre frames.

## Lecture des captures

- `sequence-1-220ticks.png` et `sequence-1-260ticks.png` : présentation extérieure, étoiles, Terre, ombre noire, disque ambré et arcs blanc chaud.
- Les prises en troisième personne et les prises tardives montrent les modèles étirés et l'orbite intérieure.
- Les captures `restored` montrent le retour à la vue normale au sol.

## Portée de la preuve

Les captures proviennent de **Minecraft réel**, et non de Snowstorm. La vidéo de référence a servi à la direction visuelle ; le rendu n'est pas une reproduction exacte ni une simulation relativiste. Le bloom utilise le mécanisme existant du mod et dépend des capacités OpenGL disponibles. Aucun benchmark de fluidité, validation de shader pack externe ou test d'autre dimension n'a été réalisé.

Les sept sons ElevenLabs fournis dans `Black_Hole_Sound_Effects.zip` sont maintenant intégrés : voir [les fichiers, leur conversion et leur synchronisation](blackhole-elevenlabs-prompts.md). L'import valide codec, fréquence, durée, niveaux et raccord des boucles. Les onze assets Railgun passent également leur contrôle après extension du manifeste audio.

## Vérification de l'intégration audio

- Nouveau test connecté réussi : `build/smoke-blackhole/20261008-211210/report.json`, `passed: true`.
- Les sept événements ont chacun créé une source audio réelle et été observés en lecture par le SoundHandler Minecraft : **7 sources / 7 sons en lecture**, sans déclenchement doublé.
- Ascension à 60 ticks ; révélation et trois boucles à 191 ticks dans la séquence naturelle ; franchissement à 301 ticks ; retour à 740 ticks. Ces horaires spatiaux dépendent du trajet réel, pas d'une durée de montage fixe.
- J à 230 ticks dans la première séquence : arrêt des cinq sources déjà déclenchées, liste des sons possédés vide et aucune reprise pendant le lancement.
- Correction d'horloge simulée de trois ticks à 320 ticks : aucun son rejoué.
- Fin naturelle : boucles arrêtées, son de retour terminé et liste des sons possédés vide.
- Contrôle de régression Railgun : `python scripts/smoke-game.py --java-home "$env:JAVA_HOME" --natural-end` réussi, avec caméra/HUD restaurés et bande-son arrêtée. Journaux : `build/smoke/client.log` et `server.log`.
- Build, 50 tests unitaires, séparation client/serveur et validation des 18 OGG réussis.

Le premier passage audio a détecté des références persistantes aux transitoires terminés et une répétition des cues après correction d'horloge. Le nettoyage consulte désormais l'état réel du SoundHandler pour les sons non bouclés ; le blackhole conserve les cues déjà déclenchés lorsque l'horloge recule. Le test connecté vérifie ces deux régressions.

La preuve concerne les assets, leur empaquetage, les sources en lecture et leur cycle de vie. Aucun enregistrement du mixage final ni jugement subjectif à l'écoute n'est fourni.

## Orbites d’accrétion et renommage — 8 octobre 2026

- Orbites elliptiques individualisées, vitesse variable inspirée de Kepler, précession, inclinaison et contraction dissipative. Le temps d’accrétion est propre à chaque capture et commence dans l’espace, à moins de 180 blocs du centre.
- Trois tests supplémentaires : passages rapprochés plus rapides, orbites distinctes qui se contractent et s’aplatissent, accélération bornée lors d’un changement de direction. Le test d’arrivée depuis 1000 blocs intègre maintenant aussi l’inertie réelle du contrôleur.
- Build, obfuscation, séparation client/serveur et contrôle du JAR : réussis ; **53 tests JUnit**, zéro échec et zéro test ignoré.
- Test connecté réussi : `build/smoke-blackhole/20261008-222334/report.json`, `passed: true`, **36 captures** dans `screenshots/`, vue d’ensemble dans `contact-sheet.jpg`.
- Travelling recadré sur le côté proche de l’orbite : la séquence naturelle a observé **68 frames de caméra**, **57 rendus du joueur** et **217 rendus de mobs** pendant ce plan. La caméra est restaurée entre les frames.
- Interruption par J à 240 ticks puis annulation serveur ; fin naturelle à 800 ticks, **537 ticks consécutifs au-dessus de Y=300**, retour en survie au sol. Sept événements audio observés en lecture ; correction d’horloge après le franchissement réel sans répétition de son.
- Les anciens tests supposaient un travelling déjà suffisamment rendu à 230 ticks et un franchissement déjà réalisé à 320 ticks. Ils attendent désormais les frames et le cue réels, avec les mêmes exigences de rendu, d’arrêt et de non-répétition.
- Aucun avertissement de déplacement rapide, erreur OpenGL ou déconnexion anti-vol dans le test réussi. Les chemins techniques, commandes et clés de sauvegarde `stellar` sont conservés.
