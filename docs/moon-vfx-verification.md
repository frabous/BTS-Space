# Moon Attract — rework visuel du 9 octobre 2026

**Mise à jour suivante :** le nom visible devient **Asteroide Attract**. Le nouveau
canon compact, la fumée au point visé, le warp et le largage sont documentés dans
[le bilan actuel](asteroide-attract-verification.md), avec 80 tests réussis et un
smoke final complet à 1280 × 720. Le contenu ci-dessous conserve les passes antérieures.

**État actuel :** le destroyer orbital est reconstruit via le MCP Blockbench d’après les quatre images du SES Legislator of Supremacy : double proue, pont bas, ailerons arrière et marquages jaunes. Le vaisseau et le canon au sol sont indépendants. Le [bilan du modèle actuel](../models/orbital_destroyer.md) documente les 2 605 éléments, la parité du maillage, le build et les 79 tests réussis, ainsi que la capture Minecraft inspectée. Le smoke du 9 octobre à 17:58 reste en échec au contrôle des dimensions des captures (1920 × 1111 au lieu de 1280 × 720), après la réussite du pilote client. Les sections suivantes conservent l’historique des modèles et passes précédentes.

## Refonte des modèles : destroyer et canon lourd

- **Vaisseau :** coque effilée à surfaces biseautées, blindages segmentés, passerelle vitrée, antennes, quatre moteurs arrière, batteries dorsales et hangar ventral caréné. Les deux portes coulissent séparément.
- **Réutilisation :** [`models/orbital_destroyer.bbmodel`](../models/orbital_destroyer.bbmodel), palette intégrée et huit groupes nommés ; `OrbitalDestroyerModel.render(hatchOpen, emissionOnly)` ne dépend d'aucun sort. Le [contrat de modèle](../models/orbital_destroyer.md) indique les axes, l'ancre cargo et les commandes de génération.
- **Canon :** socle élargi, blindages et grilles de refroidissement, quatre appuis hydrauliques renforcés, couronne de rotation, berceau suivant l'azimut du tube et pivots latéraux. Quatre rails segmentés télescopiques entourent une bouche creuse plus large ; éclairages cyan et violet.
- **Blockbench réel :** ouverture réussie en format Generic Model ; **237 éléments**, dont **198 cubes et 39 meshes**, huit groupes et une palette **32 × 8**, sans erreur de texture.
- **Parité :** positions et couleurs identiques entre Blockbench et les **5 688 sommets / 1 422 quads** du mesh ; écart flottant maximal **0,00000336**. Dégagement du cargo replié et ouverture complète des portes vérifiés.
- **Build :** Java 8, obfuscation et séparation client/serveur réussies ; **79 tests JUnit, 0 échec, 0 ignoré**. Le vérificateur d'archive confirme la présence du renderer et du mesh destroyer, identique à la source.
- **Minecraft :** essai final client/serveur réussi, **47 captures 1280 × 720**, fin naturelle à 800 ticks et saut local avec annulation. Bloom actif et **aucune erreur OpenGL** ; largage, pose, déploiement et tir inspectés dans les captures.

### Aperçus et livrable

- [Vaisseau, quatre vues géométriques](../build/orbital-destroyer-preview.png).
- [Vaisseau en orbite](../build/smoke-moon/20261009-160832-34356/screenshots/sequence-0-moon-16ticks.png), [largage](../build/smoke-moon/20261009-160832-34356/screenshots/sequence-0-moon-28ticks.png), [canon déployé](../build/smoke-moon/20261009-160832-34356/screenshots/sequence-0-moon-96ticks.png), [charge](../build/smoke-moon/20261009-160832-34356/screenshots/sequence-0-moon-140ticks.png) et [tir](../build/smoke-moon/20261009-160832-34356/screenshots/sequence-0-moon-202ticks.png).
- [Planche complète](../build/smoke-moon/20261009-160832-34356/moon-contact-sheet.jpg) et [rapport](../build/smoke-moon/20261009-160832-34356/moon-report.json).
- JAR : `build/libs/beyond-the-space-1.0.0.jar`, **5 626 706 octets**, SHA-256 `fd82b5e5a8b0c1004b3406fcbed953ea704d0d985893ad8b28f438f90cd2df8b`.

### Retour de vérification : ordre géométrique et hiérarchie éditable

Le contrôle de parité a détecté une couleur différente sur un mur de hangar après l'ajout des groupes des portes. Les sommets étaient sérialisés dans l'ordre de création, alors que le vérificateur parcourait l'ordre des groupes de l'outliner : revenir au groupe du hangar après avoir créé une porte produisait deux ordres différents. La génération regroupe désormais les sommets par UUID d'élément puis sérialise explicitement l'outliner. Lors d'un prochain export à groupes entrelacés, garder les UUID comme correspondance stable et définir l'ordre de sortie avant d'aplatir les sommets. Le signal à reconnaître est une parité correcte jusqu'à la première pièce créée après un changement de groupe. Le contrôle géométrique a également fait reculer les attaches hors de l'enveloppe de dégagement du cargo avant l'essai final.

### Auto-évaluation

| Axe | Note / 5 | Évidence et amélioration possible |
| --- | --- | --- |
| Exactitude | 4 | Import Blockbench, parité, build, 79 tests et Minecraft réels ; compléter par l'instance de jeu finale. |
| Complétude | 4 | Vaisseau réutilisable et canon intégré livrés ; l'appréciation de la ressemblance à Helldivers reste artistique. |
| Clarté | 4 | Contrat autonome et captures ciblées ; le bilan global conserve un historique long. |
| Utilisabilité | 4 | JAR prêt, source Blockbench ouverte et régénération documentée ; les edits manuels Blockbench demandent encore une reprise dans le générateur pour le runtime. |
| Concision | 4 | Renderer réutilisable de taille limitée et données mises en cache ; génération et contrôle conservent chacun leur description des faces pour une vérification indépendante. |

**Moyenne : 4,0 / 5.** Priorités : apprécier les silhouettes dans l'instance finale, puis utiliser le contrat autonome lors de la prochaine réutilisation. Auto-contrôle : les captures montrent les deux modèles demandés et le vaisseau est réellement éditable dans Blockbench.

## Passe space-opera — 9 octobre 2026

- **Modèles 3D :** cellules et cadres sur les panneaux du transporteur, antennes, éclairages de soute, détails de largage ; bearing du canon épaissi, colliers de bobines et patins d'appui renforcés. Le maillage lunaire passe à **160 × 80** et tourne lentement pendant l'attraction.
- **Lune :** mers régionales, albédo froid et cratères moins uniformément cerclés, générés de façon déterministe dans `moon_regolith.png`.
- **Gravité et trajectoires :** impulsion visuelle au largage, pulsations parcourant le faisceau, deux anneaux gravitationnels inclinés, wakes d'entrée et fragments irréguliers déterminés par la graine du lancement.
- **Impact :** feu plus chaud et moins blanc, fumées assombries et front de choc hiérarchisé pour conserver les formes sous bloom.
- **Présentation :** voile bleu nuit progressif, barre de progression et libellé de phase. Le timing de gameplay et la durée du film restent inchangés : **800 ticks / 40 secondes**.
- **Cadrage orbital :** la caméra et le cargo suivent le point de livraison tandis que la Lune reste dans le repère de la cible ; les translations ne déplacent plus la Lune par rapport à sa trajectoire.

### Validation de cette passe

- Build Forge/Java 8 et vérification des côtés : réussis.
- JUnit : **79 tests, 0 échec, 0 ignoré**.
- Textures : contrôles de raccords, pôles et bords alpha réussis.
- Smoke Minecraft client/serveur : film naturel complet et séquence skip/annulation réussis ; **25 captures** naturelles et **15 captures** avant skip, 1280 × 720, `glError=0`, bloom actif ; caméra, HUD et sons restaurés à la fermeture.
- Captures et rapport : [`build/smoke-moon/20261009-154335-8076/`](../build/smoke-moon/20261009-154335-8076/), [planche](../build/smoke-moon/20261009-154335-8076/moon-contact-sheet.jpg).
- Archive vérifiée : `build/libs/beyond-the-space-1.0.0.jar`, **5 609 750 octets**, SHA-256 `958f3157788ba7e8fa0104e7449207205ea6dcad557187102edf5456ccdbf090`.

Le smoke valide le client de développement et un monde plat à courte distance de vue. Il ne valide ni un shader pack, ni les performances sur d'autres configurations ou le rendu dans l'instance cible.

## Rendu livré

- **Canon :** bouche creuse et alésage sombre, colliers cylindriques, couronne de rotation, vérins sur les appuis, panneaux et petites pièces métalliques. Rails cyan à cœur lumineux et chambre violette ; halo de charge et flash de tir localisés à la bouche.
- **Lune :** relief de 96 cratères, maillage mis en cache de 160 × 80 segments, texture originale de régolithe 1024 × 512 avec petits cratères supplémentaires et mers sombres. Éclairage directionnel, ombres des reliefs et échauffement de la face avant.
- **Entrée atmosphérique :** traînées de plasma effilées à extrémités transparentes, sillage de fumée texturé et 24 fragments rocheux en rotation avec traînées chaudes.
- **Impact :** flash bref, fronts de pression lumineux, poches de feu qui refroidissent, colonne ascendante et couronne de fumée, poussière basse et jusqu’à 72 fragments balistiques.
- **Composition :** trois atlas VFX originaux, quatre tuiles chacun, bords transparents ; fumées triées par profondeur, dessinées hors bloom. Le ciel est assombri par le voile cinématique, sans grande sphère susceptible de créer une silhouette polygonale dans le fond.

Le rendu reste une direction cinématique adaptée à Minecraft 1.12.2 : matériaux éclairés par les couleurs des sommets et particules orientées caméra, sans matériau PBR ni simulation volumétrique physique.

## Fichiers principaux

- `src/main/java/fr/frabous/stellar/client/MoonAttractModel.java`
- `src/main/java/fr/frabous/stellar/client/MoonAttractScene.java`
- `src/main/java/fr/frabous/stellar/client/MoonAttractVfx.java`
- `src/main/java/fr/frabous/stellar/client/MoonAttractCinema.java`
- `src/main/resources/assets/stellar/textures/cinema/moon_{regolith,glow,smoke,fire}.png`
- `scripts/generate-moon-textures.py` : génération déterministe avec Pillow ; `--check` contrôle raccords et alpha.
- `src/smoke/java/fr/frabous/stellar/smoke/MoonClientSmokeMod.java` et `scripts/smoke-moon.py` : 25 repères pour le film complet et 15 avant skip, compteurs de bloom, annulation locale et nettoyage.
- `scripts/verify-artifact.py` : dimensions et présence des quatre textures dans le JAR, identiques aux ressources sources.

## Vérifications finales

| Contrôle | Résultat |
| --- | --- |
| Build et obfuscation Java 8 | Réussis ; `verifySides` réussi |
| JUnit | **70 tests, 0 échec, 0 ignoré** |
| Textures | Raccord de longitude et pôles uniformes ; chaque tuile VFX a un centre visible et une bordure entièrement transparente |
| Archive | **86 classes Java 8**, aucun pilote smoke embarqué, JSON/sons valides, quatre textures Moon vérifiées |
| Minecraft client/serveur | Smoke final réussi ; **26 captures réelles 1280 × 720** |
| Glow | `bloomReady=true`, compteur croissant aux 19 captures du film ; `glError=0` |
| Cycle de présentation | Fin naturelle à **800 ticks** ; saut local à 280 ticks conservant la frappe serveur, puis annulation reçue |
| Restauration | Caméra et HUD restaurés, sons arrêtés, effets nettoyés ; JVM client et serveur arrêtées proprement |
| Terrain | Contrôle du bloc d’herbe cible réussi avant et après chaque séquence |

Les preuves finales se trouvent dans [`build/smoke-moon/20261009-112816-29604/`](../build/smoke-moon/20261009-112816-29604/), notamment [la planche de captures](../build/smoke-moon/20261009-112816-29604/moon-contact-sheet.jpg) et `moon-report.json`. Les repères 140, 530, 574, 590, 622 et 660 ont été inspectés visuellement, ainsi que la planche complète. Les âges demandés sont consignés avec les âges réellement rendus dans le journal client.

Le smoke utilise le monde plat jetable et une courte distance de vue serveur : la petite surface visible sous l’impact correspond au terrain réellement chargé. Ces captures valident le client de développement, pas une autre instance avec shader pack ni un seuil de performances ou une vidéo continue.

### Commandes utilisées

La reconstruction réutilise le script de récupération de cache local préexistant, nécessaire après le déplacement du dépôt :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot'
.\gradlew.bat --no-daemon --offline --console=plain '-Dnet.minecraftforge.gradle.test_certs=false' --init-script 'C:\Users\Frabous\AppData\Local\Temp\opencode\stellar-cache-recovery.init.gradle' build exportDevLaunches -Psmoke -x downloadAssets
python scripts/generate-moon-textures.py --check
python scripts/smoke-moon.py --java-home "$env:JAVA_HOME"
python scripts/verify-artifact.py
```

## Livrable

```text
build/libs/beyond-the-space-1.0.0.jar
size_bytes=4903371
sha256=cf533482002079e9eef75ebf6cc99b2684eede37848d48ec4d9124ed6ee3dca1
```

## Auto-évaluation

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | Build, archive, 70 tests et smoke réel réussis ; compléter par le JAR dans l’instance cible. |
| Complétude | 4 | Canon, lune, glow, entrée atmosphérique et explosion retravaillés ; la direction artistique reste à apprécier par le joueur. |
| Clarté | 4 | Fichiers, captures et commandes liés ; les noms des PNG utilisent les repères demandés et les âges réels sont dans le journal. |
| Utilisabilité | 4 | JAR prêt et textures régénérables ; le build de cette machine dépend du script local de récupération du cache. |
| Concision | 4 | Un helper de sprites partagé par les couches Moon ; les modèles et l’ordonnancement des passes nécessitent plusieurs méthodes de rendu. |

**Moyenne : 4,0 / 5.** Priorités : apprécier le rework dans l’instance cible avec sa distance de vue et ses shaders éventuels, puis mesurer une séquence continue si un objectif FPS est fixé. L’utilisateur peut distinguer le rendu livré, les contrôles effectués et les validations restantes.

## Mise à jour : satellite transporteur et livraison au joueur

### Modèle et VFX

- Satellite transporteur distinct : panneaux solaires segmentés, soute éclairée, portes coulissantes. Il reste à 720 unités au-dessus du point de livraison.
- Canon embarqué à 672 unités, appuis repliés. Largage à 20 ticks, descente continue avec freinage, arrivée au sol à 80 ticks et verrouillage des quatre appuis à 100 ticks. Le canon charge puis tire depuis le sol à 200 ticks.
- Traînées thermiques en vol, rétropropulseurs bleus, guidage fin depuis la soute, anneau d’arrivée et poussière au sol. L’anneau de tir suit l’axe réel de la bouche et le faisceau rejoint la surface lunaire.
- Le serveur fixe la livraison dans une colonne de terrain chargé située à 12 blocs sur le côté du lanceur. Si cette colonne est indisponible, il utilise celle du joueur. Le point est conservé dans le snapshot, ses changements de phase et les messages de présentation/rejeu. La cible de dégâts et la trajectoire lunaire restent liées au bloc visé.
- La bouche et le suivi de la Lune tiennent compte des deux ancrages, même lorsque le lanceur et la cible sont éloignés ou à des altitudes différentes. Les plans orbitaux et terrestres utilisent la même géométrie de livraison.

### Caméra

| Repère | Composition |
| --- | --- |
| 0–1 s | Révélation du satellite et ouverture de la soute devant la Terre |
| 1–2,2 s | Canon sortant de la soute, caméra suivant le cargo |
| 2,0–2,4 s | Bref fondu ; coupe vers le monde à 2,2 s, pendant la descente |
| 2,4–4 s | Suivi de la chute et du freinage jusqu’au terrain près du joueur |
| 4–5 s | Atterrissage, poussière, déploiement des quatre appuis et des rails |
| 5–10 s | Canon cadré au sol pendant la charge |
| 10–11 s | Départ du tir au sol, puis orientation progressive de la caméra vers la Lune |
| 11–40 s | Attraction, entrée atmosphérique, impact et suite du film |

La scène orbitale utilise une projection privée. La caméra réelle suit ensuite la livraison dans les chunks reçus avant de reprendre les vues de la zone d’impact. La Terre cinématique a un rayon de 1 200 unités et sa surface rejoint l’ancre de livraison : c’est une mise en scène stylisée à l’échelle du film.

### Vérifications et preuves

- **Build, obfuscation et séparation client/serveur : réussis.**
- **78 tests JUnit : 0 échec, 0 ignoré.** Les contrôles de livraison vérifient le largage unique, la descente sans saut, la pose avant charge, les appuis verrouillés avant tir, l’alignement de la bouche depuis différents points de livraison et le cadrage du cargo. Les codecs contrôlent la conservation d’un point de livraison distinct et le rejet des paquets tronqués ou invalides.
- **Smoke Minecraft client/serveur réussi : 47 captures réelles 1280 × 720**, dont 25 repères du film complet et 15 de la séquence interrompue. Bloom prêt et croissant, aucune erreur OpenGL.
- Deux livraisons serveur observées à `(264, 3, 268)`, à **12 blocs du lanceur** `(264.5, 256.5)` en XZ ; cible d’impact `(256, 3, 256)` distincte.
- Fin naturelle observée à 799 ticks client, dans la tolérance de synchronisation du film de 800 ticks ; saut local à 280 ticks conservant la frappe, annulation reçue, caméra/HUD/sons restaurés et JVM arrêtées proprement.
- Archive : **88 classes Java 8**, pilotes de développement exclus, JSON, sons et textures valides.

Preuves : [`build/smoke-moon/20261009-123311-4504/`](../build/smoke-moon/20261009-123311-4504/), [planche complète](../build/smoke-moon/20261009-123311-4504/moon-contact-sheet.jpg), [soute avec canon](../build/smoke-moon/20261009-123311-4504/screenshots/sequence-0-moon-16ticks.png), [canon largué](../build/smoke-moon/20261009-123311-4504/screenshots/sequence-0-moon-28ticks.png), [freinage près du sol](../build/smoke-moon/20261009-123311-4504/screenshots/sequence-0-moon-70ticks.png), [canon posé](../build/smoke-moon/20261009-123311-4504/screenshots/sequence-0-moon-96ticks.png) et [tir au sol](../build/smoke-moon/20261009-123311-4504/screenshots/sequence-0-moon-202ticks.png). La planche et ces cinq images pleine résolution ont été inspectées ; `moon-report.json` contient les âges réellement rendus et les événements de contrôle.

Les commandes de build et de smoke restent celles du premier bilan ci-dessus. Le résultat concerne le client de développement testé ; aucun objectif FPS ni compatibilité avec un shader pack particulier n’a été mesuré.

```text
build/libs/beyond-the-space-1.0.0.jar
size_bytes=4914057
sha256=34a7704ff90ba373dcbedd217773664d25549e04e579fc18e5c5c5e19830785a
```

### Incident de vérification et récupération

Le premier essai (`20261009-122819-9032`) a échoué sur la précision temporelle du premier PNG : 16 ticks demandés, 27,92 réellement rendus. L’ancrage serveur était déjà correct ; un chargement initial des ressources graphiques était l’hypothèse retenue. La récupération a consisté à précharger les textures Terre/Lune/VFX à la sélection, avec restauration de l’état de texture. Le nouvel essai conserve le seuil de retard de six ticks : première capture à 16,34 ticks, puis l’ensemble des 47 captures et contrôles validé. L’effet du préchargement est vérifié sur cet essai ; aucun seuil de FPS général n’en est déduit.

### Auto-évaluation de la livraison

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | Livraison serveur distincte de l’impact, alignement testé et captures réelles ; une lecture vidéo continue compléterait les repères fixes. |
| Complétude | 4 | Satellite, largage, descente, pose, tir au sol et caméra livrés ; l’écart de 12 blocs réserve la place du canon et reste à apprécier par le joueur. |
| Clarté | 4 | Chronologie et captures liées ; le bilan historique rend le document plus long, d’où le lien vers l’état actuel en tête. |
| Utilisabilité | 4 | JAR construit et contrôlé ; vérifier son rendu dans l’instance cible avec ses réglages propres. |
| Concision | 4 | Géométrie partagée entre plans orbitaux et terrestres ; les projections conservent deux chemins de rendu nécessaires aux décors distincts. |

**Moyenne : 4,0 / 5.** Améliorations prioritaires : apprécier le cadrage de livraison et le placement près du joueur, puis contrôler le montage en lecture continue dans l’instance cible. Auto-contrôle : la correction demandée est concrètement visible dans les captures, du satellite jusqu’au canon qui tire au sol.
