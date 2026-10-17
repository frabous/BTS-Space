# Vérification de Stellar 1.0.0 — Railgun seul

**Dernière mise à jour : 9 octobre 2026.** Le dézoom spatial continu remplace le montage de montée du 8 octobre ; les passes précédentes sont conservées comme historiques.

## Dézoom Railgun — 9 octobre 2026

Une seule scène imbriquée et une trajectoire de recul continue de 58 à 194 ticks remplacent les vues Terre/Saturne/système/galaxie indépendantes. Le monde se raccorde progressivement dans les nuages. Voir la [chronologie et les fichiers](railgun-reference-100ms.md#dézoom-continu--9-octobre-2026).

| Contrôle | Résultat |
| --- | --- |
| Build Java 8 et séparation client/serveur | `gradlew.bat --no-daemon --console=plain --offline '-Dnet.minecraftforge.gradle.test_certs=false' build exportDevLaunches -Psmoke -x downloadAssets` réussi |
| Tests | **78 JUnit, aucun échec ni ignoré** ; continuité du dézoom, vitesse aux anciens repères, recul monotone et raccord nuageux contrôlés |
| Archive | `scripts/verify-artifact.py` réussi : **88 classes Java 8**, ressources valides et mesh de 26 904 sommets |
| Client/serveur réel, saut local | Passe finale à **13:04** réussie sur les ports isolés 25595/25596 : 59 captures Railgun et menu, `glError=0`, saut à 420, HUD restauré, lancement serveur nettoyé, arrêt propre des deux JVM |
| Fin naturelle | **Non validée** : deux passes ont fermé à l’âge client 510/511 pour un nominal de 514 ; échec de l’assertion de synchronisation, journal conservé dans `build/smoke-railgun-pullback/client.log` |

Les captures consultées montrent la Terre diminuant à position stable, Saturne et les orbites dans le même cadre, puis le bras galactique qui recule. Preuves : `build/smoke-railgun-pullback-skip/screenshots/` et `client.log`. La trajectoire est continue mathématiquement ; aucune capture vidéo à 60 FPS ni validation dans l’instance de production n’est revendiquée.

Le pilote de smoke ne réclame plus une nouvelle passe de bloom après la fin de la frappe : les captures ajoutées à 513/516 vérifient aussi l’absence d’erreur OpenGL après fermeture. Le blocage initial de build venait de dépendances absentes du cache hors ligne ; leur récupération en ligne a permis le build final hors ligne. Les ports du smoke standard étaient occupés par une autre session ; la vérification Railgun a utilisé ses propres dossiers et ports.

Archive contrôlée : `build/libs/beyond-the-space-1.0.0.jar`, **4 914 057 octets**, SHA-256 `fef2aad4613ea9ca66bd78d91679c3321dd9dc978430656b9b7c182495fb7ced`.

### Auto-évaluation du dézoom

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | Trajectoire et perspective testées, captures Minecraft inspectées ; pas de mesure vidéo continue du mouvement. |
| Complétude | 4 | Les échelles spatiales partagent une scène ; le contrôle strict de fin naturelle reste en échec. |
| Clarté | 4 | Phases de visibilité distinguées des anciennes coupes ; historique technique encore long. |
| Utilisabilité | 4 | JAR compilé et smoke avec saut réussi ; essayer le mouvement dans l’instance cible. |
| Concision | 4 | Logique ciblée sur la montée ; une interpolation dédiée est nécessaire pour éviter les resets de vitesse. |

**Moyenne : 4,0 / 5.** Priorités : contrôler la fluidité dans l’instance cible, puis diagnostiquer l’écart d’horloge à la fin naturelle. Auto-contrôle : la modification demandée est visible et testée, avec une limite de vérification explicitement consignée.

## État appliqué — 8 octobre 2026, smoke final à 18:12

Réanalyse intégrée **côté client uniquement** : 19 plans, charge dès 11,9 s, arcs ramifiés anguleux et colliers, flash à 12,7 s, transit à 12,8 s, Terre à 13,2–13,3 s, tunnel puis ciel réel, arrivée à enveloppes blanches et trame pointillée, impact à 14,3 s. Contraction à 18,7–19,3 s, flash terminal à 19,2 s, puis surface sombre et liseré de danger de rayon fixe jusqu’au fondu 25,0–25,7 s. Caméra terminée à 25,7 s ; audio aligné. Voir la [chronologie appliquée](railgun-reference-100ms.md).

**Contrat serveur inchangé :** 100 dégâts bruts/s de 14,3 à 22,7 s, rayon 200 blocs, terrain intact, aucune génération ni aucun chargement forcé de chunks. L’alpha visuel ne pilote pas les dégâts : l’axe éteint à 19,3 s ne signifie pas que la zone est devenue sûre.

| Contrôle | Résultat et preuve disponible |
| --- | --- |
| Build final JDK 8 hors ligne | `gradlew.bat --no-daemon --console=plain --offline '-Dnet.minecraftforge.gradle.test_certs=false' build exportDevLaunches -Psmoke -x downloadAssets` : succès de la passe appliquée ; non relancé pour cette mise à jour documentaire |
| Tests et séparation physique | **30 tests JUnit, 0 échec, 0 erreur, 0 ignoré** dans `build/test-results/test/` ; `verifySides` réussi |
| Archive | `scripts/verify-artifact.py` réussi : **62 classes Java 8**, **11 sons**, mesh de **26 904 sommets** |
| Smoke avec saut local | Passe finale par défaut à **18:10** réussie ; fermeture locale sans annuler la frappe serveur |
| Smoke jusqu’à la fin naturelle | Passe finale à **18:12** réussie : **43 captures Railgun + 1 menu** ; 43 entrées `STELLAR_SMOKE_FRAME`, puis `STELLAR_SMOKE_PASS` dans [le journal client](../build/smoke/client.log) |
| Fin naturelle et restauration | `STELLAR_SMOKE_NATURAL_END actualLaunchAge=515.0`, `hudRestored=true cameraRestored=true soundtrackStopped=true` ; fin nominale 514 ticks, tolérance de synchronisation jusqu’à 518 ticks |
| OpenGL / bloom | `glError=0` à toutes les captures, menu compris ; bloom prêt et passes croissantes pendant le film (menu pré-lancement : `ready=false`, attendu) |

### Rendu et terrain effectivement observés

Les PNG du framebuffer Minecraft se trouvent dans [le dossier de captures](../build/smoke/screenshots/). Images inspectées lors de la passe appliquée :

| Capture demandée (ticks) | Observation visuelle |
| --- | --- |
| 240 ; 244 / 248 | Bandes rouges sur le corps ; fourches électriques anguleuses |
| 254 ; 256 | Écran entièrement blanc ; faisceau diagonal avec long flare horizontal |
| 264 / 266 ; 270 | Terre petite puis grande ; tunnel bordeaux asymétrique |
| 284 ; 374 / 380 | Arrivée à contours blancs courbés et trame pointillée ; contraction du corps |
| 454 ; 504 ; 516 | Sol sombre avec textures sur la portion chargée ; retour de l’herbe sous le résidu en fondu ; HUD restauré |

Les noms indiquent **l’âge demandé**, pas une correspondance exacte à la source. `STELLAR_SMOKE_FRAME.renderedAge` décrit le rendu GUI ; les effets du passage monde peuvent avoir un autre âge, consigné par les sondes aux captures 420, 454 et 504. Exemples du run final : 454 demandés → `worldAge=455.46`, `renderedAge=461.46` ; 504 demandés → `worldAge=505.62`, `renderedAge=507.56`. Ces PNG ne prouvent donc pas un alignement exact avec une frame vidéo.

**Capture 513 du run final : tardive, film déjà fermé.** Le journal indique `actualLaunchAge=516.78 retainedWhenClosed=true hudVisible=true screen=world`. Elle ne prouve pas que la caméra était encore ouverte à 513 ticks. La passe précédente de **17:54** avait vérifié l’ouverture à 513 ; ce constat historique ne remplace pas la limite du run final. La fermeture à 515 est, elle, explicitement contrôlée.

### Correctifs appliqués et limites

- Caméras verrouillées dans des secteurs chargés, avec repli aérien plus bas sur terrain réellement chargé pour les courtes distances de vue.
- Couche au sol gardée uniquement sur les cellules compilées effectivement rendues ; ensemble mis en cache une fois par frame.
- Brouillard cinématique corrigé en mode **EXP, densité 0**, puis état entrant restauré. La coupe finale ne recharge plus le pool de chunks compilés ; cela remplace le contournement décrit dans la passe glow historique ci-dessous.
- Le smoke final utilise une **distance de vue serveur de 4 chunks** : le liseré complet de rayon 200 dépasse le terrain disponible. Les images prouvent une portion texturée et le repli caméra, **pas un cercle complet ni des parois de cratère conformes à la vidéo**.
- Pas de capture Minecraft continue à 60 FPS : les images fixes ne prouvent ni fluidité ni raccords frame par frame. Le JAR de production n’a pas été lancé dans l’instance cible ; shader packs, autres GPU et performances à plus grande distance de vue ne sont pas validés. L’alignement des repères audio ne vaut pas validation artistique du mix.

### Archive actuelle — 8 octobre 2026, passe appliquée

```text
build/libs/stellar-1.0.0.jar
size_bytes=3803168
sha256=89f13c598e60a1a3f072c5d9bd7220e3be6c479f49c8516575cdb83384217dca
```

### Auto-évaluation de la passe appliquée

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | 30 tests, archive et fermeture à 515 contrôlés ; les âges monde/GUI diffèrent, sans preuve d’alignement exact à la source. |
| Complétude | 4 | 43 captures couvrent les phases et la restauration ; le liseré complet ne tient pas dans la distance serveur 4. |
| Clarté | 4 | Chronologies client/serveur et passes historiques séparées ; la lecture frame par frame reste dans les journaux. |
| Utilisabilité | 4 | JAR identifié par taille/hash et preuves accessibles ; installation et rendu dans l’instance cible restent à vérifier. |
| Concision | 4 | Bilan actuel compact et historique conservé ; les avertissements de capture nécessitent quelques détails techniques. |

**Moyenne : 4,0 / 5.** Priorités : essayer le JAR dans l’instance cible (shader pack éventuel), vérifier le liseré avec une distance de vue suffisante, puis capturer une séquence continue à 60 FPS et écouter le mix. Auto-contrôle : le lecteur peut distinguer ce qui est appliqué, observé et non prouvé ; aucune égalité visuelle ou validation de production n’est promise.

## Glow historique — 8 octobre 2026, avant la chronologie appliquée

Cette section conserve les résultats de cette passe antérieure (16 tests, 56 classes, 18 captures). Ses âges estimés, son archive et son contournement de rechargement des chunks ne décrivent plus l’état actuel. Les chemins sous `build/` sont réutilisés entre les runs : un lien existant n’est pas une archive immuable de la passe historique.

Bloom sélectif intégré, sans shader pack externe : cœur blanc incandescent, épaules roses et halo rouge doux sur le canon, le faisceau et les anneaux. Principaux fichiers modifiés : `EffectMesh`, `RailgunCinema`, `WorldEffects`, `OrbitalRailgunModel`, `EmissionBloom` et `CinematicCamera` dans `src/main/java/fr/frabous/stellar/client/`, ainsi que le pilote de smoke de développement `src/smoke/java/fr/frabous/stellar/smoke/ClientSmokeMod.java`. **Aucun changement de gameplay ni de dégâts.**

| Contrôle | Résultat et preuve |
| --- | --- |
| Build complet Java 8 hors ligne | `gradlew.bat --no-daemon --console=plain --offline '-Dnet.minecraftforge.gradle.test_certs=false' build exportDevLaunches -Psmoke -x downloadAssets` : succès |
| Tests et séparation physique | **16 tests JUnit réussis** ; `verifySides` réussi |
| Archive | `verify-artifact.py` réussi : **56 classes Java 8** |
| Client/serveur réels | Exécution finale de `scripts/smoke-game.py` réussie ; **18 captures de lancement** aux âges estimés suivants : 60, 140, 220, 245, 250, 253, 256, 260, 270, 278, 285, 288, 300, 330, 365, 410, 430 et 460 ticks |
| Bloom en jeu | À chaque capture de lancement : `ready=true`, compteur de passes de bloom croissant et `glError=0` ; journaux dans `build/smoke/` |

L’âge estimé de la frappe est **l’horloge de sélection moins 10 ticks**, avec une incertitude réseau ; les noms des captures ne sont donc pas des âges exacts. Captures Minecraft consultées dans `build/smoke/screenshots/` : à **260**, bloom blanc-rose à la bouche du canon ; à **365**, rideau rouge lisse et halo des anneaux ; à **430/460**, après le saut local, terrain herbeux et HUD présents.

Correctifs vérifiés pendant cette passe :
- Le premier smoke échouait parce que le client se mettait en pause à la perte du focus ; correction limitée au test.
- Le voile blanc près de la caméra a été corrigé par l’atténuation de `worldBeam` et l’arrêt du faisceau de marquage à l’impact.
- Le recyclage des chunks par la vue cinématique pouvait laisser le terrain vide au retour ; `mc.renderGlobal.loadRenderers()` à la fermeture corrige ce cas, avec de l’herbe visible dans les captures finales.
- Deux défauts relevés en revue ont été corrigés : réduction par moyenne de surface de toute la source pour conserver les détails fins, et respect du masque RGB entrant lors de la composition finale.

Le bloom est prouvé dans ce client de développement, pas encore dans l’instance de production avec le JAR obfusqué. D’autres GPU, l’anaglyphe, le redimensionnement et le repli sur géométrie sans bloom en environnement non pris en charge n’ont pas été testés. Ni fidélité exacte à la référence ni compatibilité générale avec les shader packs ne sont affirmées.

### Archive historique du glow — 8 octobre 2026 (remplacée)

```text
build/libs/stellar-1.0.0.jar
size_bytes=3781247
sha256=85f57a7e0bcceb87c00a175300557f61d70ae264b4a149c00d5db6480db3cbea
```

### Auto-évaluation historique du glow — 8 octobre 2026

| Axe | Note / 5 | Évidence et limite |
| --- | --- | --- |
| Exactitude | 4 | Smoke réel réussi ; instance de production non testée. |
| Complétude | 4 | Glow visible dans les captures réelles ; pas de reproduction exacte de la référence. |
| Clarté | 4 | Preuves et limites distinguées ; validation artistique encore à faire par l’utilisateur. |
| Utilisabilité | 4 | JAR reconstruit prêt ; pas encore installé dans l’instance cible. |
| Concision | 4 | Helper de rendu ciblé, intégration dans le rendu principal et documentation limitée ; gestion d’état GL nécessaire. |

**Moyenne : 4,0 / 5.** Priorités : tester dans l’instance cible, vérifier son shader pack éventuel et recueillir l’avis artistique de l’utilisateur.

## Résultats historiques — 7 octobre 2026

| Contrôle | Résultat et preuve |
| --- | --- |
| Compilation et obfuscation | `gradlew.bat --no-daemon --console=plain build` : succès ; JAR dans `build/libs/stellar-1.0.0.jar` |
| Tests unitaires | **16 tests**, aucun échec ni test ignoré : chronologie, limites exactes de la colonne, codecs réseau, refus des anciens identifiants et caméra Railgun |
| Séparation physique | `verifySides` : aucune dépendance client/LWJGL dans les classes communes et serveur contrôlées |
| Serveur dédié réel | Chargement de Forge et Stellar, lancement via télécommande/visée serveur, commandes `status` et `cancel`, sauvegarde et arrêt propre |
| Client/serveur réels | Connexion Forge, sélection Railgun confirmée par le serveur, film, menu à une seule attaque, fin de frappe |
| Saut local | Film Railgun fermé ; le serveur indique encore une frappe active ; elle se termine normalement |
| Débit réel des dégâts | Entités sans armure à 1 000 PV : 900 PV après 20 ticks d’impact, 800 après 40, 160 après les 168 ticks ; les délais d’invulnérabilité ne réduisent pas ce débit |
| Frontière et entrée/sortie | Une entité à exactement 200 blocs est touchée ; à 200,1 blocs elle reste intacte ; entrer dans la colonne déclenche les dégâts, en sortir les arrête |
| Durée des dégâts | Aucun dégât avant impact ; santé inchangée après extinction à 454 ticks |
| Terrain réel | Bloc cible en pierre, bloc support en or et bedrock restent intacts pendant toute la frappe |
| Protections | Annulation de `LivingAttackEvent` respectée : l’entité protégée conserve ses 1 000 PV |
| Anciennes télécommandes | NBT sélectionnant Seven Stars résolu automatiquement vers Railgun |
| Archive distribuée | **55 classes Java 8**, méthode de l’item obfusquée, JSON valides, onze sons résolus et mesh Railgun de 26 904 sommets ; aucun pilote de test ni classe Forge embarqué |

Les captures ont été produites par le framebuffer Minecraft. `build/smoke/screenshots/skill-menu.png` et `railgun-365ticks.png` ont été consultées pour confirmer le choix unique et le faisceau. Les rapports unitaires sont dans `build/reports/tests/test/`, les journaux client/serveur dans `build/smoke/` ; les assertions de dégâts sont dans `build/smoke-server.log`.

Les essais client/serveur utilisent les classes de développement Forge. Le JAR obfusqué a été contrôlé séparément, sans lancement supplémentaire dans l’instance de production. Les journaux contiennent les avertissements préexistants de l’environnement ForgeGradle 1.12 sur les JAR ASM et les signatures ; les tests terminent normalement. Les immunités créatif/spectateur ont été conservées par lecture du code, sans test de combat dédié avec ces joueurs. Les 100 dégâts/s sont des dégâts bruts avant armure/résistance, à 20 TPS. Aucun seuil de performance, rendu avec shaders ni compatibilité avec tous les boss d’autres mods n’est affirmé.

## Archive historique — 7 octobre 2026 (remplacée par celle du 8 octobre)

```text
build/libs/stellar-1.0.0.jar
size_bytes=3771373
sha256=8a7b67765d0c2b2b93541b3ef4fefe99a1134578f7400f68f8155f7b275200fb
```

## Prompts sonores — bilan historique du 7 octobre 2026

`docs/elevenlabs-sound-prompts.md` contient onze prompts pour les OGG existants, leur chronologie, leurs durées et leurs contraintes de boucle ; trois prompts supplémentaires couvrent les sons Minecraft de marquage, d’onde et de grondement.

Les **onze MP3 ElevenLabs fournis sont maintenant intégrés** dans les ressources et le JAR. Conversion en Vorbis mono 44,1 kHz, arc électrique ramené à 0,32 s et raccords croisés pour les deux boucles de 2 s. Le contrôle `generate-cinematic-sfx.py --check` passe pour les onze fichiers, avec crêtes décodées de 0,632 à 0,726 et sans clipping. Le build et les 16 tests passent ; `verify-artifact.py` confirme les onze sons dans l’archive. La reconstruction a utilisé le cache Gradle avec `--offline -Dnet.minecraftforge.gradle.test_certs=false`, le contrôle préliminaire du certificat Forge empêchant le lancement ordinaire. Les essais client/serveur ci-dessus précèdent le remplacement audio ; les nouveaux sons n’ont pas été évalués à l’écoute en jeu.

## Auto-évaluation historique du livrable — 7 octobre 2026

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | Build, 16 tests, archive et débit réel vérifiés. Un essai du JAR obfusqué dans l’instance de production compléterait la preuve. |
| Complétude | 4 | Railgun seul, terrain intact, dégâts répétés, 14 prompts et onze sons fournis intégrés. Leur équilibre artistique reste à écouter en jeu. |
| Clarté | 4 | Débit brut, durée et rayon sont explicites ; les trois couches Minecraft demandent une intégration distincte, indiquée dans le document sonore. |
| Utilisabilité | 4 | JAR prêt avec les onze OGG aux noms et durées attendus. Leur équilibre dépend encore du volume et du mix en jeu. |
| Concision | 4 | Résultat court côté utilisateur et détails dans des documents dédiés ; la liste détaillée des sons reste volontairement longue. |

**Moyenne : 4,0 / 5.** Priorités : essayer le JAR dans l’instance cible, puis écouter et équilibrer les fichiers ElevenLabs intégrés, notamment les deux boucles. Auto-contrôle : l’utilisateur devrait retrouver le Railgun seul, le débit demandé et ses sons convertis ; aucune qualité artistique du mix n’est présentée comme déjà vérifiée.
