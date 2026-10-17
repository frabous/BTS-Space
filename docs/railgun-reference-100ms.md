# Railgun — réanalyse de référence à 0,1 seconde

**Dernière mise à jour : 9 octobre 2026 — montée spatiale corrigée en dézoom continu.** La table d’écarts et l’auto-évaluation de l’analyse plus bas sont conservées comme état **avant application**, et non comme liste de corrections encore absentes.

## Dézoom continu — 9 octobre 2026

Les repères `CLOUDS`, `EARTH`, `SATURN`, `SOLAR_SYSTEM` et `GALAXY` décrivent désormais des phases de visibilité, **pas des changements de scène ou des coupes caméra**. De 58 à 194 ticks (2,9–9,7 s), `RailgunPullback` fait reculer une seule caméra dans un espace imbriqué : la Terre garde sa position et son rayon, Saturne appartient au même système solaire, et celui-ci se trouve sur un bras de la galaxie. Le faisceau reste attaché à la même Terre.

La distance est interpolée logarithmiquement avec une vitesse continue aux anciens repères ; cadrage, roulis et inclinaison évoluent progressivement. Une mise à l’échelle commune de la caméra et de la géométrie conserve la perspective jusqu’à l’échelle galactique. Le rendu du monde continue derrière le raccord nuageux de 58 à 68 ticks ; le voile est piloté par l’âge absolu et ne repart plus à zéro au changement de phase.

Validation : **78 tests JUnit réussis**, dont contrôles de continuité de position/direction/vitesse, recul monotone, perspective et raccord nuageux ; build Java 8 et `verifySides` réussis. Captures Minecraft dans `build/smoke-railgun-pullback-skip/screenshots/` : Terre à 78/88/100/108, système et Saturne à 118/124/128/136, recul galactique à 144/156/178/192. Smoke avec saut local à 420 ticks réussi, HUD restauré et nettoyage serveur confirmé ; `glError=0` pour les **59 captures Railgun et le menu**.

La passe sans saut a enregistré le dézoom et l’impact, mais son assertion de fin naturelle a échoué : fermeture reçue à l’âge client 510 puis 511 ticks pour une fin nominale à 514. Journaux dans `build/smoke-railgun-pullback/`. Ce contrôle n’est donc pas déclaré réussi. Les images fixes et les tests de trajectoire ne démontrent pas un débit continu de 60 FPS. Voir le [bilan](verification.md).

## État appliqué — 8 octobre 2026

**Ancien montage à 19 plans**, défini par les 20 bornes de `RailgunCameraPath.CUTS` à **20 TPS** (514 est la fin, pas un vingtième plan). Depuis le 9 octobre, les repères 58/68/120/128/158 de la montée ne sont plus des coupes :

```text
0, 10, 44, 58, 68, 120, 128, 158, 194, 210,
226, 236, 244, 254, 256, 274, 282, 286, 386, 514
```

| Repère client | État appliqué |
| --- | --- |
| 0,6 s | Marquage visible |
| 11,9–12,5 s ; 12,6 s | Charge rouge progressive, branches électriques anguleuses et colliers lumineux ; décharges atténuées avant le flash |
| 12,7 s ; 12,8 s | Flash blanc, puis transit avec faisceau diagonal et long flare horizontal |
| 13,2–13,3 s ; 13,4–13,7 s | Terre petite puis grande, tunnel bordeaux asymétrique, puis ciel du monde réel |
| 14,1 s ; 14,3 s | Arrivée avec grandes enveloppes à bords blancs courbés et trame pointillée (halftone), puis impact |
| 18,7–19,3 s ; 19,2 s | Contraction du corps jusqu’à extinction de l’axe ; flash terminal |
| Jusqu’à 25,0–25,7 s | Surface sombre et liseré de danger de rayon fixe, puis fondu final ; caméra terminée à 25,7 s |

Repères audio alignés sur ces phases. Sources principales : `src/main/java/fr/frabous/stellar/client/RailgunVisualTiming.java`, `RailgunCameraPath.java`, `RailgunCinema.java`, `WorldEffects.java`, `CinematicCamera.java`, `OrbitalRailgunModel.java` et `CinematicSoundtrack.java` dans le même dossier client. **Gameplay partagé inchangé : 100 dégâts bruts/s de 14,3 à 22,7 s, rayon 200, terrain intact, aucune génération ni aucun chargement forcé de chunks.** Alpha visuel et dégâts serveur sont indépendants : l’extinction à 19,3 s ne termine pas les dégâts.

Les trajectoires caméra sont verrouillées dans les secteurs chargés, avec un repli aérien plus bas au-dessus du terrain réellement disponible à courte distance de vue. La couche au sol respecte les cellules compilées effectivement rendues, mises en cache une fois par frame. Brouillard en mode EXP à densité 0, état restauré ; pas de rechargement des chunks compilés à la coupe finale.

**Preuves et limites :** build final JDK 8 hors ligne et `verifySides` réussis, 30 tests JUnit sans échec/ignoré, archive contrôlée (62 classes Java 8, 11 sons, mesh de 26 904 sommets). Smoke avec saut local à 18:10 et fin naturelle à 18:12 réussis ; le dernier journal compte 43 captures Railgun plus le menu, avec `glError=0`. Fermeture naturelle contrôlée à 515 ticks (nominal 514, tolérance jusqu’à 518), HUD/caméra/sons restaurés. Voir [le bilan et l’archive actuels](verification.md).

Les PNG inspectés montrent notamment bandes rouges à 240, fourches à 244/248, flash blanc à 254, transit à 256, Terre à 264/266, tunnel à 270, arrivée tramée à 284, contraction à 374/380, portion de sol sombre texturée à 454, herbe sous le fondu à 504 et HUD à 516. Ces nombres sont des **âges demandés** : l’âge GUI `renderedAge` et l’âge du passage monde diffèrent parfois (sondes 420/454/504). La capture finale demandée à 513 est arrivée après fermeture ; **elle ne prouve pas l’ouverture à 513**. Une passe précédente à 17:54 l’avait contrôlée. Aucune correspondance exacte à une frame source ni fluidité continue à 60 FPS n’est démontrée. À distance serveur 4, le liseré entier de rayon 200 est hors terrain disponible ; ni cercle complet ni cratère identique n’est prouvé. Instance de production, shader packs et performances à plus grande distance de vue restent à tester.

## Analyse historique — 8 octobre 2026, avant application

Analyse du 8 octobre 2026, réalisée avec des sous-agents pour la chronologie visuelle, le code, les captures Minecraft et la revue de l'extraction.

**Constat principal : la référence éteint son faisceau entre 19,2 et 19,3 s. À 22,7 s, elle change de vue sur les parois du cratère. L'ancienne interprétation « impact jusqu'à 22,7 s, puis extinction » ne décrit pas la vidéo.**

## Preuves consultables

- [Galerie des 258 images et 13 planches](../build/reference-analysis/100ms/index.html).
- [Manifest avec temps demandés, PTS décodés et validation](../build/reference-analysis/100ms/manifest.json).
- Images complètes : `build/reference-analysis/100ms/frames/`.
- Planches chronologiques : `build/reference-analysis/100ms/sheets/`.
- Extracteur reproductible : `scripts/analyze-railgun-reference.py`.

Source : `C:\Users\Frabous\Downloads\Minecraft The Shooting Star Mod Is INSANE 🌠#shorts - Sigma Villager (1080p).mp4`.

Le conteneur dure **25,704490 s**, mais la piste vidéo finit à **25,633333 s** ; le dernier début de frame décodable est **25,616667 s**. Les **1 538 PTS** confirment une cadence constante de **60 FPS**. Les instants demandés sont 0,0 à 25,7 s inclus : **257 correspondances exactes**, puis le dernier instant est ramené à la dernière vraie frame, avec un décalage de −0,083333 s explicitement enregistré. Aucun temps de frame n'est inventé.

Toutes les **258 images** ont été examinées à travers les 13 planches : 100 pour le lancement/espace, 60 pour le canon/tir/arrivée, 98 pour l'impact et la suite. Des images pleine résolution complètent les transitions et formes importantes. Les JPEG complets restent en 1080 × 1920 ; seul le storyboard recadre les bandes noires communes. Les titres et marques appartiennent au montage.

## Chronologie observée

Les temps ci-dessous sont des **échantillons**, pas des mesures au frame près des coupes. Une coupe entre 10,4 et 10,5 s reste bornée par ces deux observations. `#122` désigne `frames/frame_0122_012.2s.jpg` dans la galerie.

| Temps de référence | Observation | Preuve |
| --- | --- | --- |
| 0,0–0,4 s | Personnage vu de face/oblique au-dessus de la côte, télécommande en main ; pas de faisceau visible. | #000–004 |
| 0,5 s | Coupe vers l'arrière du personnage, village et péninsule en contrebas. | #005 |
| 0,6–2,0 s | Apparition d'un marquage vertical rose-blanc fin et d'une lueur au sol. | #006–020 |
| 2,1 s | Vue brève gênée par le bois et le terrain, avec géométrie souterraine visible. Pas de preuve suffisante d'une explosion. | #021 |
| 2,2–2,8 s | Ciel bleu, faisceau blanc et halo rose ; suivi ascendant de sa pointe lumineuse. | #022–028 |
| 2,9–3,3 s | Recentrage de la pointe, traversée de brume puis de nuages aux contours irréguliers ; teinte rose accrue. | #029–033 |
| 3,4–5,3 s | Faisceau diagonal, surface terrestre puis globe entier et recul rapide ; atmosphère bleue, côté sombre, nuages. | #034–053 |
| 5,4–6,3 s | Réorientation dans les étoiles ; Saturne est grande à 6,0–6,1 s et minuscule dès 6,2 s. | #054–063 |
| 6,4–7,2 s | Orbites cyan d'abord presque sur la tranche, puis ellipses ouvertes ; recul et disparition à petite échelle. | #064–072 |
| 7,3–9,6 s | Bande galactique, grand disque à partir de 7,9 s, bras spiraux et noyau ; faisceau attaché hors centre, vers la droite. Dissolution granuleuse à 9,6 s. | #073–096 |
| 9,7–10,4 s | Révélation large du canon sombre devant les étoiles et un bord galactique. | #097–104 |
| 10,5–11,2 s | Coupe vers le travelling latéral : modules, nervures, tiges et treillis. | #105–112 |
| 11,3–11,7 s | Coupe vers les panneaux arrière, puis composition presque de dos. Les reflets forts ne sont pas le flash de tir. | #113–117 |
| 11,8–12,1 s | Nouvelle vue longitudinale ; rouge visible dès 11,9 s, progressant le long du corps à 12,0–12,1 s. | #118–121 |
| 12,2–12,5 s | Chambre ouverte vue de côté : rails séparés, arcs anguleux et irréguliers, nœuds brillants, collier blanc/rose. | #122–125 |
| 12,6 s | Forte décharge anguleuse absente de cet échantillon ; rails et collier restent éclairés. | #126 |
| 12,7 s | Scène blanche masquant la transition. | #127 |
| 12,8–13,1 s | Transit spatial déjà engagé : faisceau rouge diagonal, long flare horizontal et traînées radiales. | #128–131 |
| 13,2–13,3 s | Terre petite puis presque plein cadre, toujours entourée des traînées de vitesse. | #132–133 |
| 13,4–13,6 s | Traitement de tunnel blanc/rose, ouverture centrale bordeaux sombre très irrégulière et étirement radial. | #134–136 |
| 13,7–14,0 s | Point incandescent et flare en croix dans le ciel ; terrain et eau entrent dans le cadre. | #137–140 |
| 14,1 s | Traitement rouge/blanc tramé très contrasté ; bande horizontale claire et petit éclat autour d'une silhouette sombre. | #141 |
| 14,2–14,3 s | Grandes enveloppes rouges verticales, bords blancs courbés et axe blanc ; contours bien plus grands que de petits anneaux isolés. | #142–143 |
| 14,4–16,7 s | Très large corps rouge saturé, cœur blanc étroit, bandes verticales souples et silhouettes sombres au premier plan. | #144–167 |
| 16,8–18,3 s | Changement de vue puis regard vers le haut : axe diagonal à l'écran et contours convergents par perspective ; rares points/sprites roses. | #168–183 |
| 18,4–18,6 s | Nouvelle vue distante de la partie inférieure arrondie. | #184–186 |
| 18,7–19,1 s | Rétrécissement progressif du corps rouge et de l'axe blanc. | #187–191 |
| 19,2 s | Flash terminal blanc/rose ; seul un trait très fin reste au-dessus de la silhouette résiduelle. | #192 |
| 19,3–19,7 s | Axe éteint ; récupération de l'exposition et silhouette rouge sombre persistante. | #193–197 |
| 19,8–22,6 s | Vue extérieure d'une immense zone circulaire sombre, bord rouge et liseré chaud ; approche puis personnage au premier plan. | #198–226 |
| 22,7–25,1 s | Coupe vers l'intérieur : parois à strates de blocs rouges/orange, poches grises et profondeur sombre. | #227–251 |
| 25,2–25,616667 s | Nouvelle vue rapprochée des parois, eau/lave et nombreuses petites particules carrées claires. | #252–257 |

Le flash à 12,7 s est absent à 12,6 et 12,8 s : sa durée est **positive et inférieure à 0,2 s**, pas nécessairement 0,1 s. Le traitement de tunnel visible à 13,4–13,6 et absent à 13,3/13,7 s dure **au moins 0,2 et moins de 0,4 s**, en supposant un seul intervalle continu.

## Écarts historiques et priorités avant application — 8 octobre 2026

**État antérieur conservé :** « actuel » dans les constats ci-dessous désigne le rendu audité avant application. Les numéros de ligne sont ceux de cet audit ; ils ne sont pas des ancres du code actuel. Pour l’état livré, lire la section appliquée ci-dessus.

Chemins Java ci-dessous relatifs à `src/main/java/fr/frabous/stellar/client/`. Le code et les captures du bloom du 8 octobre ont été audités ; les images Minecraft existantes sont peu nombreuses et leurs noms ne donnent pas des âges exacts.

| Priorité | Écart constaté | Amélioration ciblée et critère visuel | Fichiers concernés |
| --- | --- | --- | --- |
| P1 | Le rendu maintient l'impact jusqu'à 22,7 s et n'amorce sa réduction que vers 22,1 s. La vidéo rétrécit à 18,7–19,1 s et éteint l'axe à 19,3 s. | Séparer maintien, rétrécissement, flash terminal et résidu sombre. Le repère 22,7 s doit rester une coupe, pas déclencher une extinction. | `WorldEffects.java:56–75`, `RailgunCinema.java:253–255`, `CinematicCamera.java:88–104` |
| P1 | La charge passe de 0 à 0,4 au début de `CHAMBER` à 12,1 s ; la progression rouge visible dès 11,9 s manque. | Propager l'éclairage le long du corps avant la vue de chambre ; éviter l'apparition déjà partiellement chargée. | `RailgunCinema.java:127–138`, `OrbitalRailgunModel.java:96–104` |
| P1 | Le retour vers la Terre à 13,2–13,3 s n'est pas rendu dans la phase de transit. | Réutiliser la Terre avec une approche très rapide sous les traînées et garder le faisceau/flaring lisible. | `RailgunCinema.java:143,185–195`, `CelestialModels.java` |
| P1 | Les arcs existent mais suivent des courbes régulières autour du canon. | Connexions brisées irrégulières, nœuds lumineux, variations entre décharges et interruption visible avant le flash ; conserver le collier comme couche distincte. | `OrbitalRailgunModel.java:132–153` |
| P1 | La chambre actuelle est davantage vue depuis l'intérieur ; les beats caméra ne correspondent pas tous aux échantillons. | Vue latérale extérieure permettant de lire rails, collier et arcs ; ajuster les coupes 11,3 / 11,8 / 12,2 / transit visible à 12,8 s. | `RailgunCameraPath.java:8–38`, `RailgunCinema.java` |
| P2 | Le ciel de retour est un décor spatial avec voile bleu ; le flare est centré à l'écran. | Faire entrer terrain/eau dès 13,8–14,0 s, déplacer le point incandescent dans le cadre et maintenir sa croix horizontale/verticale. | `RailgunCinema.java:145–153,249`, `CinematicCamera.java` |
| P2 | L'arrivée utilise surtout cinq anneaux ; la vidéo montre des enveloppes rouges énormes à bord blanc courbé et une trame transitoire rouge/blanc. | Comparer les silhouettes à #141–143 ; distinguer géométrie des enveloppes, flash et traitement tramé. Ne pas confondre ce post-traitement avec des particules supplémentaires. | `RailgunCinema.java:149–152,251`, `WorldEffects.java`, `EffectMesh.java` |
| P2 | Plusieurs plans célestes révèlent leurs objets trop tôt ; la galaxie reçoit son faisceau au centre. | Rapprocher les fenêtres de visibilité de Saturne 6,0–6,1, orbites 6,4, galaxie 7,9, canon 9,7 s ; renforcer la rotation apparente des orbites et décentrer l'attache galactique. | `RailgunCameraPath.java:8,31`, `RailgunCinema.java:106–125` |
| P2 | Le résidu actuel consiste surtout en deux anneaux qui réapparaissent à pleine opacité à 22,7 s puis s'effacent avant la fin. | Assurer un raccord continu d'opacité ; représenter le résidu sombre/rouge et un liseré chaud dès l'extinction de l'axe, avec quelques points clairs. | `WorldEffects.java:56–75` |
| P3 | Le canon a une lecture mécanique détaillée mais les captures montrent des surfaces assez uniformes. | Améliorer les différences de valeurs et les reflets locaux pour garder des panneaux lisibles sous le rouge, après correction du cadrage. | `OrbitalRailgunModel.java`, `RailgunCinema.java` |

### Ce qui existait déjà avant application

Bloom sélectif, cœur blanc et halo rose/rouge, arcs, anneaux lumineux, ouverture de rails, étoiles/traînées, tunnel radial et globe terrestre texturé sont présents. Ajouter davantage de glow partout ne corrigerait ni le timing, ni les contours, ni les changements de vue. Le tunnel actuel a déjà un contraste rouge sombre/blanc fort dans `build/smoke/screenshots/railgun-270ticks.png` : son amélioration porte sur la forme, la dynamique et la liaison avec l'approche terrestre.

Les captures `railgun-430ticks.png` et `railgun-460ticks.png` suivent un saut local de la cinématique. Elles vérifient le retour du HUD et du terrain, **pas l'extinction naturelle**. La suite du rendu reste à capturer intégralement pour une comparaison fiable.

### Fluidité à viser — recommandations de l’analyse historique

- Garder les coupes franches réellement observées ; rendre continus les mouvements et enveloppes à l'intérieur de chaque plan.
- Utiliser le temps fractionnaire de rendu, déjà présent, pour positions, largeur du faisceau, charge et alpha. Un storyboard à 10 images/s ne doit pas devenir la cadence d'animation.
- Revoir les activations binaires des arcs, la largeur instantanée au tir, la sortie du tunnel à pleine intensité et la remise à 1 de l'alpha résiduel.
- Contrôler les boucles de traînées : un retour mathématique modulo ne doit pas produire de saut visible au premier plan.
- Pour conclure sur la fluidité, comparer une capture Minecraft continue à 60 FPS à la source, avec les coupes synchronisées. Les images fixes seules ne mesurent pas les saccades.

### Limite de la reproduction du terrain

Les dernières images montrent des **parois excavées, de l'eau et de la lave**, pas seulement un effet de fumée. Le projet actuel conserve volontairement le terrain et applique 100 dégâts/s pendant 8,4 s. Modifier les constantes partagées de la chronologie changerait aussi le gameplay ; une correction purement visuelle doit être distinguée de ce contrat avant son intégration.

Une apparence résiduelle sombre/rouge et des particules peuvent être recréées visuellement, mais elles ne rendent pas un terrain intact identique aux parois de la vidéo. Aucun panache de fumée, champignon d'explosion ou système identifiable de gros débris volants n'est démontré par cette analyse. Les petits carrés finaux sont visibles, leur nature précise reste inconnue.

## Vérification historique de l’extraction — 8 octobre 2026

```powershell
python scripts/analyze-railgun-reference.py --check
python -O scripts/analyze-railgun-reference.py --check
```

**Résultats :** 258 JPEG complets et 13 planches décodés, séquence demandée continue, choix du PTS le plus proche revérifié par ffprobe, dernier échantillon borné explicitement. Les deux commandes passent. La revue a conduit à remplacer les assertions désactivables et à revérifier indices, PTS, offsets et bornage ; six corruptions en mémoire sont rejetées dans les deux modes. Les 544 liens de la galerie ont été vérifiés lors de la génération.

À la clôture de cette passe d’analyse, la réanalyse, les images, l’outil et les corrections à intégrer étaient livrés, sans nouvelle validation Minecraft ni application des recommandations. **Ce statut est historique :** l’application et les captures ultérieures sont décrites en tête de document.

## Auto-évaluation historique de l’analyse — 8 octobre 2026

| Axe | Note / 5 | Évidence et amélioration |
| --- | --- | --- |
| Exactitude | 4 | PTS et 258 échantillons vérifiés ; coupes et flashes restent bornés à 0,1 s. Une mesure au frame près préciserait ces transitions. |
| Complétude | 4 | Toute la vidéo couverte et confrontée au code/captures ; la fluidité actuelle manque d'une capture Minecraft continue. |
| Clarté | 4 | Galerie, chronologie et priorités reliées ; le rapport détaillé est plus long que le résumé utilisateur. |
| Utilisabilité | 4 | Outil reproductible et fichiers cibles identifiés ; les changements de rendu restent à intégrer et comparer. |
| Concision | 4 | Résumé court et preuves séparées ; la table chronologique est volontairement exhaustive. |

**Moyenne : 4,0 / 5.** Priorités suivantes : corriger la chronologie visuelle sans modifier implicitement les dégâts, revoir charge/arcs/retour Terre, puis capturer l'ensemble à 60 FPS pour juger les raccords. L'analyse complète est livrée ; une égalité visuelle n'est pas démontrée.
