# Beyond The Space — Minecraft Forge 1.12.2

**Version 1.1.0 — 15 octobre 2026 : rework SS-03A Asteroid Puller, famille Carrier Ops et 17 sons SS-03.**

Railgun orbital et trou noir inspirés de **The Shooting Star [Demo]**, avec les attaques originales **SS-03 Carrier Ops** (**SS-03A Asteroid Puller**, **SS-03B Broadside Command** et **SS-03C Kinetic Rain**), **SS-04 Supernova** et **SS-05 Gravity Inversion**. Mod autonome à installer sur **le serveur et chaque client**, avec Minecraft **1.12.2**, Forge **14.23.5.2864 ou ultérieur** et Java **8**.

## Installation et utilisation

1. Copier `build/libs/beyond-the-space-1.1.0.jar` dans le dossier `mods` de l’instance et du serveur, en supprimant l’ancien JAR (`beyond-the-space-1.0.0.jar` ou Stellar).
2. Prendre la **Beyond The Space Remote** dans l’onglet créatif **Beyond The Space**, ou utiliser `/stellar give` en tant qu’opérateur.
3. Viser un bloc du terrain chargé, à 256 blocs maximum, puis **clic droit**.

| Commande | Action |
| --- | --- |
| **K** | SS-01 Railgun |
| **B** | SS-02 Blackhole |
| **M** | SS-03 Carrier Ops ; appuyer encore passe à la charge suivante de la famille |
| **N** | SS-04 Supernova |
| **G** | SS-05 Gravity Inversion |
| **H** | Menu holographique ; clic gauche pour sélectionner, clic droit pour le briefing |
| **Accroupi + clic droit** | Sélectionner l’attaque suivante |
| **J / Échap pendant un film** | Quitter la présentation ; la frappe continue sur le serveur |
| **J hors film** | Activer/désactiver les prochains films pour la session |

Les raccourcis se changent dans **Options → Contrôles → Beyond The Space**. Les anciennes télécommandes sélectionnent automatiquement Railgun. Il n’y a pas de recette : la télécommande s’obtient en créatif ou par commande.

Le nom affiché et le JAR sont renommés ; l’identifiant technique `stellar`, les commandes `/stellar`, les clés NBT et `config/stellar.cfg` restent compatibles avec les sauvegardes existantes.

## Menu holographique de la remote

Remote en main, **H** ouvre des cartes blanches en perspective sur le monde encore visible, avec des accents cyan et ambre. Les cartes arrivent en cascade, flottent et réagissent au curseur ; chaque attaque possède une projection orbitale animée. Cinq sons courts originaux accompagnent l’ouverture, le survol, la sélection, le briefing et la fermeture. Un survol continu ne rejoue pas le SFX.

**Clic gauche** sélectionne puis referme le menu ; il ne lance pas la frappe. **Clic droit** ou le bouton du panneau affiche le briefing. Les **flèches + Entrée** permettent aussi de choisir ; **H / Échap** ferme le menu, et Échap revient d’abord à la projection si le briefing est ouvert. Les raccourcis K/B/M/N/G restent utilisables. La sélection reste synchronisée avec le serveur ; le tir se fait toujours en visant puis en cliquant droit dans le monde.

Le menu ne met pas le jeu en pause et ne déplace pas la caméra. Son rendu ne nécessite pas de shader pack. Les sons du menu sont arrêtés à la fermeture, sans couper les sons du monde. Les ressources audio se contrôlent avec `python scripts/generate-remote-sfx.py --check`.

## Télécommande SR-1 et pouce animé

La remote est un modèle 3D procédural : coque blanche, façade en verre sombre, écran d’état (SS-0x et nom de la frappe), touche FIRE, cinq touches de frappe aux couleurs du menu, croix directionnelle, rangée H / i / J et émetteur au sommet.

- **Première personne** : la main du joueur, avec son skin et ses manches, tient la remote. Le pouce appuie réellement sur la touche correspondant à **K / B / M / N / G / H / J**, à la frappe (clic droit → FIRE) ou à la sélection suivante (accroupi + clic droit). La touche s’enfonce et s’allume. Le texte d’aide du HUD est masqué en première personne : l’écran de la remote affiche la sélection.
- **Menu H** : la même remote se tient du côté de la main qui la porte (à droite par défaut, à gauche pour une prise main gauche), reliée à chaque carte par un faisceau ; le panneau de projection passe de l’autre côté des cartes. Un doigt holographique survole la touche de la carte pointée, plonge sur la touche des flèches, d’Entrée, de K/B/M/N/G, H, Échap et J, puis une onde lumineuse part de la touche enfoncée. Les touches de la remote sont aussi cliquables. **J** active ou désactive les films directement depuis le menu.
- **Inventaire, sol, cadre et troisième personne** utilisent le même modèle, sans main.

## Railgun et dégâts

- Marquage rouge, Terre, Saturne, système solaire, galaxie spirale et canon extragalactique ; retour du faisceau.
- **100 dégâts bruts par seconde**, distribués en **10 dégâts tous les 2 ticks** à 20 TPS. L’armure, les résistances et les événements Forge peuvent réduire ou annuler les dégâts.
- Colonne circulaire de **rayon 200 blocs**, centrée sur le bloc visé, de **Y=0 à Y=512**. Les entités vivantes souterraines et aériennes sont incluses, sans contrôle de ligne de vue.
- Dégâts serveur uniquement de **14,3 à 22,7 s**, soit **8,4 s** et jusqu’à **840 dégâts bruts** pour une entité présente pendant toute cette phase. Aucun dégât avant ou après cette fenêtre. **L’axe visuel s’éteint à 19,3 s : son alpha est indépendant des dégâts, qui continuent jusqu’à 22,7 s.**
- Les cibles sont recherchées à chaque impulsion : entrer dans la colonne expose aux dégâts ; la quitter les arrête. Le lanceur en survie est également touché. Créatifs et spectateurs restent immunisés.
- **Aucune destruction ni pose de blocs**, aucun déplacement des joueurs, aucun chargement forcé de chunks. Seules les entités dans les chunks déjà chargés sont traitées.

Les effets dans le monde et les scènes spatiales utilisent des géométries et ressources originales. La montée du Railgun est un dézoom continu : Terre, planètes, orbites et galaxie partagent le même espace, avec une caméra qui recule sans coupure entre ces échelles. Le passage depuis le monde se raccorde dans les nuages. Le canon détaillé conserve ses plans rapprochés et ses sons cinématiques ; sa source éditable est `models/orbital_railgun.bbmodel`. Ses plans terrestres déplacent uniquement une caméra locale pendant le rendu ; la vue du joueur est restaurée entre les frames. Les cinématiques ne changent pas sa dimension et ne mettent pas le serveur en pause. Le joueur reste exposé aux événements du monde pendant le film. J / Échap et la fin du film arrêtent les SFX et restaurent la caméra.

Le rendu intègre un **bloom sélectif sans shader pack externe** : canon, faisceau et anneaux associent un cœur blanc incandescent, des épaules roses et un halo rouge doux. Si les capacités de rendu nécessaires ne sont pas disponibles, la géométrie reste affichée sans bloom. Cela ne garantit ni une reproduction exacte de la vidéo ni la compatibilité avec tous les shader packs.

## Blackhole et orbites d’accrétion

- Champ de **rayon 1000 blocs**, centre à **Y=420**, entrée dans l’espace à **Y=300** ; seules les entités déjà chargées sont attirées.
- Chaque entité suit une **ellipse inclinée différente**, inspirée des orbites de Kepler : passage rapide au périastre, ralentissement à l’apoastre et précession du grand axe.
- Après l’arrivée près du trou noir, la dissipation contracte progressivement les orbites et aplatit leur plan dans un disque d’accrétion. Le temps de contraction commence à l’arrivée de chaque entité, y compris pour les arrivées tardives.
- L’accélération est lissée et la vitesse bornée ; les corps restent visibles dans les orbites intérieures avant le retour au sol à la fin du champ. Il s’agit d’une dynamique stylisée, pas d’une simulation relativiste exacte.
- **Spaghettification létale** : à partir de 400 ticks, les forces de marée infligent des dégâts qui ignorent l’armure aux êtres vivants proches de l’horizon (≤ 56 blocs : dose complète, nulle au-delà de 98). Un mob de 10 PV meurt en ~2 s, un boss de 300 PV en ~9 s. Message de mort : « a été spaghettifié par un trou noir ». Réglages `gameplay` de `config/stellar.cfg` : `blackholeLethal` (tout désactiver) et `blackholeLethalPlayers` (épargner les joueurs).
- **Distorsion de l’espace-temps** : une passe plein écran courbe réellement l’image (lentille gravitationnelle avec anneau d’Einstein, ombre, entraînement du disque, onde gravitationnelle et frange chromatique). Elle tient compte de la profondeur : seul ce qui est derrière le trou noir est dévié.
- Les êtres vivants s’étirent dans un shader de sommets : le côté proche est plus étiré, le corps s’aligne vers le trou noir et se courbe sur son orbite. L’étirement augmente avec les dégâts jusqu’à un fil à la mort. Des filaments de plasma s’écoulent ensuite vers l’horizon. Sans GLSL ou avec un shader pack actif, une déformation simplifiée prend le relais.
- Créatifs et spectateurs sont immunisés. Une entité dont le chunk se décharge (loin de tout joueur) est reposée au sol et échappe aux dégâts.

## SS-03 Carrier Ops — Asteroid Puller (SS-03A)

SS-03 est désormais une **famille d’attaques livrées par le destroyer**. Le vaisseau, son warp, sa soute et son départ forment un module réutilisable (`CarrierFlight` pour la chorégraphie, `CarrierRenderer` pour le rendu) ; chaque attaque de la famille n’apporte que sa charge utile. **M** sélectionne la famille, un nouvel appui passe à la charge suivante. Dans le menu **H**, la carte SS-03 affiche les charges (A, B…) et **← / →** les fait défiler dès que la famille en contient plusieurs. Ajouter une charge : une entrée `AttackId` avec `AttackFamily.CARRIER`, un `CarrierFlight.Plan` et une scène. Les identifiants techniques `moon_attract` (wire id 5, NBT, raccourci) restent compatibles.

**M**, viser un bloc chargé, clic droit : film de **43 s / 860 ticks**. [Bilan et captures réelles](docs/asteroid-puller-verification.md).

| Temps | Phase |
| --- | --- |
| 0–1,2 s | Fumée ambrée et hologramme de zone sur le bloc visé, faisceau d’uplink vers l’orbite |
| 1,3–4,1 s | Portail warp en orbite (spirale, arcs, flash, onde de choc) ; le destroyer en sort étiré, freine, le portail se referme |
| 4,1–5,1 s | Soute ventrale ouverte, pinces relâchées : largage de la Graviton Lance |
| 5,1–8,6 s | Chute avec plasma de rentrée, transition par les nuages à 6,4 s, rétrofusées à 7,5 s, contact à 8,6 s |
| 8,6–11,7 s | Ancrage et déploiement ; le vaisseau rallume ses moteurs, accélère et repart en warp |
| 11,8–18,5 s | L’astéroïde apparaît à environ 2,5 km et traverse le ciel ; la Lance le suit, charge et vise son point d’interception |
| 18,5–25 s | Tir du harpon gravitationnel, accrochage à 19,1 s, déviation progressive de plus de 120° |
| 25,3–28,5 s | Entrée atmosphérique accélérée vers la Lance, rupture du lien à 27,8 s, silence à 28,2 s |
| 28,5–43 s | Impact, onde de choc, colonne et champignon, retombées, ambiance puis fondu |

**Graviton Lance AG-07.** Elle remplace le canon AG-03. Le modèle est procédural (`GravitonLanceModel.java`, sans fichier mesh) : châssis octogonal, quatre jambes repliées qui se déplient avant le contact, patins et ancres, tourelle orientable, réacteur de culasse et ailettes radiatrices, canon télescopique en trois étages, bobines contrarotatives et couronne à quatre branches. Déployée, sa bouche dépasse 18 blocs de haut. La chute suit une loi continue : vitesse nulle au largage et au contact, rétro-poussée finale. Lumières, bobines, charge, recul, chaleur et vibrations sont animés au tick près. Après la rupture du lien, la tourelle se fige et s’affaisse. `models/graviton_cannon.bbmodel` (AG-03) reste dans le dépôt comme historique mais n’est plus empaqueté.

**Astéroïde.** Corps irrégulier procédural d’environ 160 blocs : ellipsoïde bosselé, 42 cratères, plans de fracture, régolithe éclairé par sommet, rotation propre amortie après l’accrochage. La trajectoire (`AsteroidTrajectory`) est continue en position et en vitesse : survol rectiligne vers 1 100 blocs d’altitude à 15 blocs/tick, crochet gravitationnel qui ralentit l’astre vers 4 blocs/tick et inverse son cap, puis plongée d’environ 44° qui accélère jusqu’à 26 blocs/tick au contact. La chaleur d’entrée dépend de la densité atmosphérique et de la vitesse. C’est une échelle cinématique Minecraft, pas une simulation orbitale réelle.

**Caméra et HUD.** Plans orbitaux continus jusqu’à la transition nuageuse, poursuite de la Lance en chute, orbite pendant le déploiement avec bascule vers le départ du vaisseau, plan par-dessus l’épaule pendant le suivi, grue au tir, puis plan de plongée pendant l’entrée atmosphérique. Le HUD tactique affiche la cible, la solution de tir, la charge, la déviation et le compte à rebours d’impact. **J / Échap** ferme la présentation ; la frappe serveur continue. Les caméras terrestres se replient sur les chunks déjà reçus.

**Gameplay serveur.** Rayon 200 blocs, 100 dégâts bruts/s, uniquement de **28,5 à 32,5 s** ; créatifs et spectateurs immunisés ; terrain intact.

**Audio.** 17 sons originaux synthétisés (aucun échantillon) couvrent les étapes propres au rework. Les `carrier_*` (portail warp, fermeture, freinage, soute, pinces, réacteur en boucle, départ) sont réutilisables par toute charge Carrier Ops. Les `lance_*`, `tether_*` et `asteroid_*` (rentrée, rétrofusées, atterrissage, ancrage, dépliage, harpon, accroche, longe en boucle, rupture, double bang supersonique) sont propres à Asteroid Puller. Chaque son a un temps fort documenté (ex. éclatement du portail à 0,8 s) ; `AsteroidPullerSoundtrack` le cale sur le tick de l'animation. Régénérer ou vérifier : `python scripts/generate-asteroid-puller-sfx.py` (`--check` pour vérifier sans écrire, `--only nom` pour un seul son ; numpy, scipy et ffmpeg requis). Les 14 sons `moon_attract_*` (charge, rayon, astéroïde entrant, entrée atmosphérique, fragmentation, impact, onde de choc, pluie de débris, ambiance du cratère) sont aussi synthétisés : `python scripts/generate-moon-attract-sfx.py` (mêmes options). Ils gardent leur nom technique pour la compatibilité ; [`docs/moon-attract-elevenlabs-prompts.md`](docs/moon-attract-elevenlabs-prompts.md) ne sert plus que de cahier des charges (noms, durées, intentions). Le mix cinématique est local au lanceur. Les boucles et fins sont pilotées par les ticks monde ; les one-shots sont des fichiers audio en temps réel, donc un serveur sous 20 TPS étire la scène sans étirer ces fichiers.

## SS-03B Broadside Command : canons pilotables

1. Remote en main, appuyer sur **M** pour choisir Carrier Ops, puis à nouveau jusqu'à **SS-03B Broadside Command**. Dans le menu **H**, sélectionner la carte SS-03 et utiliser **← / →** pour passer de A à B.
2. Viser un bloc chargé et faire **clic droit**. Le destroyer arrive par warp, proue tournée vers la zone visée, et s'immobilise avec sa **nacelle de tir** (bulle vitrée sous la quille) à la verticale du joueur. Après **4 secondes**, un faisceau de transfert téléporte réellement le joueur dans la nacelle, à **Y=240**.
3. Pendant **30 secondes**, la croix de visée désigne la cible. Maintenir **Attaquer** (clic gauche par défaut) pour tirer ; maintenir **Utiliser** (clic droit) pour le viseur optique zoomé. **J / Échap** quitte le poste avant la fin. Le faisceau ramène ensuite le joueur au point occupé juste avant l'embarquement.

Ce sont les **cinq tourelles doubles ventrales** du destroyer qui tirent : B1 sous la quille, B2/B3 à l'arrière, B4/B5 à l'avant. Chacune pivote et s'incline à vitesse limitée vers le point désigné, puis tire un obus de coilgun depuis l'un de ses deux canons (recul, flash de bouche, bobines qui se rechargent). Un obus part de la tourelle libre la mieux pointée ; chaque tourelle se recharge en **2 secondes**. Une cible que la coque masque ou que les canons ne peuvent pas atteindre affiche **NO FIRING SOLUTION**. Le HUD liste l'état des cinq batteries (prête, pointage, recharge, hors secteur), le temps de vol et le nombre d'obus en l'air.

Tous les joueurs voient les mêmes tourelles bouger : le serveur diffuse le point visé, puis chaque obus confirmé. Pendant le pointage, des lasers de télémétrie marquent l'impact ; l'obus vole à **24 blocs/tick** avec un traceur, puis l'impact produit flash, boule de feu, étincelles, onde de choc, colonne de poussière et trace de brûlure temporaire (le terrain n'est pas modifié). Les sons sont positionnés et arrivent à la vitesse du son : au sol, on voit l'éclair des canons avant d'entendre la détonation.

Les tourelles et la nacelle sont procédurales (`DestroyerBatteryModel.java`, `FireControlGondolaModel.java`) ; le groupe `07_ventral_defense_batteries` du `.bbmodel` sert de référence de montage et n'est plus exporté dans le maillage statique. La géométrie de tir (positions, arcs, volumes de coque) est partagée par le serveur et les clients dans `BroadsideBatteries.java`. Les 10 sons `broadside_*` (tir proche et lointain, passage d'obus, impact proche et lointain, rechargement, moteurs de tourelle en boucle, ambiance de nacelle, faisceau de transfert, verrouillage) sont synthétisés sans échantillon par `python scripts/generate-broadside-sfx.py` (`--check` pour vérifier sans écrire). Le déplacement est verrouillé au poste, mais la visée reste libre ; le viseur optique masque l'intérieur de la nacelle. Le contrôle fonctionne aussi lorsque les films optionnels sont désactivés.

L'étendue du paysage visible dépend de la distance de vue du serveur. À faible distance, le terrain reçu forme une petite zone sous le vaisseau ; le poste ne demande pas de chunks supplémentaires pour masquer cette limite.

Cadence maximale d'une demande de tir tous les **8 ticks** : **20 dégâts bruts** par obus, appliqués **à l'impact**, explosion de **rayon 8 blocs**, solution de visée jusqu'à **768 blocs**, uniquement dans les chunks déjà chargés. Le serveur valide le propriétaire, la durée, la cadence, les secteurs de tir et les collisions ; un relief entre la tourelle et la cible arrête l'obus. Le pilote, les créatifs et les spectateurs ne prennent pas les dégâts des canons. Les blocs ne sont ni détruits ni posés. Les autres frappes restent soumises à leurs propres dégâts ; un champ de trou noir qui atteint le pilote interrompt le poste avant de prendre le relais.

La fin, la sortie anticipée et `/stellar cancel` restaurent la position, la gravité et les autorisations de vol précédentes. Le client rétablit la vue F5 précédente et cesse de modifier le champ de vision. Une déconnexion sauvegarde le retour ; un marqueur NBT permet aussi de récupérer le joueur à sa prochaine connexion après un arrêt imprévu. Le poste ne peut pas être lancé à dos de monture ni pendant un vol en élytres. La recharge et la limite de frappes restent partagées avec les autres attaques.

Test connecté sur le monde local jetable, sans inclure le pilote de test dans le JAR distribué :

```powershell
.\gradlew.bat --no-daemon --console=plain build exportDevLaunches -Psmoke -x downloadAssets
python scripts/smoke-carrier.py --java-home "$env:JAVA_HOME"
```

Rapport et captures : `build/smoke-carrier/<date-pid>/`. Le test couvre la sélection A/B, l'embarquement en survie avec les films désactivés, le pointage des tourelles vers la cible, le tir maintenu réparti sur les cinq batteries, le zoom, les dégâts et la cadence serveur, la fin naturelle, J, l'annulation et la restauration des états. Les captures servent au contrôle visuel ; les assertions ne jugent pas la qualité graphique.

## SS-03C Kinetic Rain : barrage de tiges de tungstène

1. Remote en main, appuyer sur **M** jusqu'à **SS-03C Kinetic Rain**, ou dans le menu **H** utiliser **← / →** sur la carte SS-03 jusqu'à C.
2. Viser un bloc chargé et faire **clic droit**. Pas de film : tous les joueurs de la dimension voient la scène dans le monde. Durée totale **23 s / 460 ticks**.

Le destroyer arrive par warp et s'immobilise, soute à la verticale du point visé, à **Y = max(300, cible + 200)**. Les portes s'ouvrent et **quatre rails coilgun** descendent aux coins de la soute. De **7 à 13,9 s**, ils tirent **96 tiges** par salves de 4 toutes les **6 ticks** : un **barrage en spirale à quatre bras** qui part de **176 blocs** et se resserre jusqu'à **30 blocs** du centre. Chaque point d'impact s'allume **2,5 s avant** : disque teinté au **vrai rayon de 8 blocs**, anneau qui se referme, balise verticale visible de loin, puis laser de désignation du rail. La tige sort du rail vers le bas, traverse la couche nuageuse (anneau de choc) et plonge presque à la verticale ; l'impact donne éclair, gerbe d'éjecta, onde de choc, boule de feu, étincelles, cratère incandescent, poussière et trace de brûlure temporaire.

Pendant le barrage, le berceau abaisse lentement **le marteau**, une tige géante tenue par les pinces. À **14,5 s** les pinces s'ouvrent ; sa zone de **28 blocs** est marquée **5 s avant** (anneau rouge, anneau pointillé tournant, viseur, balise). Il frappe le centre à **17 s**, puis le vaisseau referme sa soute et repart en warp.

Dégâts, appliqués par le serveur au tick exact de chaque impact :

- **Tige** : **24 dégâts bruts** au centre, rayon **8 blocs**, **-35 %** au bord, avec projection vers l'extérieur et le haut.
- **Marteau** : **80 dégâts bruts** au centre, rayon **28 blocs**, même atténuation, projection plus forte.

Les zones sont des colonnes verticales (Y 0 à 512), comme le Railgun. Le lanceur est touché en survie ; créatifs et spectateurs sont immunisés ; la résistance au recul est respectée. Le terrain n'est jamais modifié et aucun chunk n'est chargé. Positions et horaires sont dérivés de la graine du lancement (`KineticRainRules.java`) : le serveur frappe exactement là où chaque client dessine, sans paquet supplémentaire. Le lancement exige que **190 blocs** autour de la cible tiennent dans la bordure du monde.

Les rails et le marteau sont procéduraux (`KineticRainScene.java`), les effets dans `KineticRainVfx.java`. Les sons du vaisseau réutilisent ceux du destroyer ; les 6 sons `kinetic_*` (salve de rail, sifflement de tige, impact proche et lointain, chute et impact du marteau) sont synthétisés sans échantillon par `python scripts/generate-kinetic-rain-sfx.py` (`--check` pour vérifier sans écrire). Les salves et les impacts arrivent à la vitesse du son ; le sifflement de la tige la plus proche (moins de 110 blocs) monte jusqu'à l'impact, ce qui avertit avant la détonation.

## Supernova

**N**, puis viser un bloc chargé et clic droit. Le bloc fixe seulement l'origine visuelle : **les dégâts concernent toute la dimension, pas une zone de 200 blocs**. Le film dure **32 s / 640 ticks**. Le modèle représente une **étoile massive**, pas notre Soleil, qui n'a pas la masse nécessaire pour finir en supernova.

À **3 s**, la caméra rejoint sa photosphère. La convection utilise **16 images originales de 512 × 256**, réunies dans un atlas **2048 × 1024**, interpolées tous les 6 ticks. Les cellules claires et chenaux sombres évoluent ; la surface se déforme légèrement au lieu de simplement faire tourner une texture. L'assombrissement du bord, la couronne et les boucles magnétiques sont conservés.

Le film garde un **plan fixe de 4,5 à 7,5 s** pour lire la convection, puis stabilise la coupe du cœur avant la rupture. Le recul commence plus près de l'étoile et suit l'expansion au lieu de tourner continuellement. Les éjectas séparent **trois nappes de vitesses différentes** ; leurs lobes se déforment avec le temps et laissent des vides entre les poches de gaz. **144 traînées courbes à épaisseur variable** remplacent les lignes fines, avec autant de nœuds de plasma texturés. Le cœur doré et les bords bleus refroidissent vers l'orange. Trois fronts sphériques déchirés partent à **16,0, 16,2 et 16,4 s** ; le guide de lumière reste discret pour ne pas masquer la matière. Les guides idéalisés et la coupe n'apparaissent que dans le film.

Avant la rupture, le film « inspire » de 15,3 à 16 s : zoom, vignette, bandes noires élargies, lignes de vitesse convergentes et compte à rebours **T-x.xx** qui clignote en rouge. Une brève coupure sombre précède ensuite un **dessin d'impact noir et blanc en deux temps** : une image négative de 1,2 tick, puis l'image positive au cœur irrégulier et à l'anneau fissuré. L'ensemble dure **2,6 ticks, soit 130 ms à 20 TPS**, puis laisse revenir les éjectas avec un trait anamorphique, une gerbe de rayons, des étincelles et un recul de caméra ; il n'est pas répété en stroboscope. Des cartons titrent l'étoile, l'effondrement du cœur et le front lumineux. Cet effet contrasté est réservé au film optionnel, que **J / Échap** permet de quitter. Le flash d'irradiation à 20 s est plus bref, et le choc atmosphérique à 24 s ajoute un recul du champ de vision et du roulis à la caméra du film. Les caméras libres des témoins ne sont pas secouées. Dégâts, chronologie serveur et sons des témoins restent inchangés.

De **9 à 13 s**, seul le **cœur** s'effondre ; l'enveloppe garde son rayon. Le cœur est montré en **coupe schématique explicitement légendée**, pas comme une vue optique réelle à travers l'étoile. Un choc aidé par les neutrinos traverse ensuite l'enveloppe et débouche à **16 s**. La lumière se propage en sphère, devant les éjectas plus lents. Aucun laser, jet dirigé, disque de danger ni boule de feu centrée sur un bloc ne subsiste.

La caméra revient dans le monde à **19,1 s**. L'irradiation commence à **20 s** ; la matière et le choc atmosphérique arrivent à **24 s**. Lumière et matière utilisent la même horloge, avec un rapport de vitesse **30:1** (ordre de grandeur de 300 000 km/s contre 10 000 km/s). Après 20 s, le temps illustré accélère **×29**, annoncé à l'écran. Distances, rayons, convection et durées sont comprimés pour Minecraft ; le front de lumière dans le vide est un **guide schématique légendé**, pas une bulle de matière ou un rayonnement intrinsèquement visible de côté. Ce n'est pas une simulation hydrodynamique ou un calcul d'habitabilité. Le petit reste compact est agrandi pour rester lisible. Référence : [NASA, Star Basics](https://science.nasa.gov/universe/stars/).

Les dégâts sont de **100 dégâts bruts/s pendant 4 s**, de **20 à 24 s**, sur **toutes les entités vivantes déjà chargées de la dimension du lancement**, sans limite de distance ni de hauteur. Cela inclut le lanceur en survie ; créatifs et spectateurs restent immunisés, et les annulations Forge sont respectées. Une annonce avertit les joueurs de la dimension. Les dimensions voisines, joueurs hors ligne et entités déchargées ne sont pas parcourus. **Aucun bloc n'est modifié, aucun chunk n'est chargé de force.** Recharge et limite de frappes simultanées restent communes aux autres attaques.

Les témoins gardent leur caméra libre, leur zoom et leurs vues **F5 arrière / avant**. Une représentation céleste bornée conserve la direction et la taille angulaire de l'étoile, même au-delà du plan lointain, sans charger de terrain supplémentaire ; le point de vue réel inclut le recul F5. Le terrain et les bâtiments continuent de masquer l'objet. L'atmosphère lumineuse et les poussières sont rendues autour de chaque observateur de la dimension, même si l'origine est hors champ. Les sprites atmosphériques sont triés par profondeur, exclus du bloom et s'éteignent à la fin du film. Le bloom sélectif reste natif 1.12.2, sans shader pack externe ; la géométrie existe aussi sans post-traitement. La caméra du film utilise seulement le terrain reçu et restaure exposition, champ de vision et brouillard.

En espace, seul un fond musical non diégétique accompagne le film : aucun bruit physique de canon/explosion dans le vide. Ce score enchaîne nappe spatiale, montée de tension, crépitements du compte à rebours, **un tick de silence** sous la coupure, puis des frappes stylisées à la détonation et un souffle au retour sur le monde ; il s'efface à 19,8 s pour laisser les sons des témoins. **Tous les témoins**, avec ou sans film, reçoivent le choc de pression à **24 s**, puis les basses et le vent poussiéreux. Ces trois sons sont synthétisés : choc de 3,2 s (front d'onde, souffle, tonnerre renvoyé par le relief), grondement et vent en boucles de 2 s sans raccord audible. Chaque variante sous toit est dérivée de la même prise par un filtre à 140 Hz, sans renormalisation indépendante. Un toit atténue et filtre progressivement le mix ; l'eau le réduit davantage, sans changer la hauteur des sons. Le test d'abri consulte uniquement le chunk déjà reçu du joueur. **J / Échap** ferme le film et arrête son fond musical, mais les sons environnementaux continuent tant que l'événement existe. Fin naturelle, annulation, déconnexion et changement de monde libèrent les sources possédées. Aucun doublon de tonnerre serveur n'est ajouté.

Les textures se régénèrent avec `python scripts/generate-supernova-textures.py` ; `--check` vérifie dimensions, coutures, pôles, contraste, continuité cyclique des 16 images et bords transparents du plasma.

`python scripts/generate-supernova-sfx.py` régénère les six OGG (`--check` pour vérifier sans écrire). La vérification mesure les aigus au-dessus de 1 kHz par FFT, puis compare niveau et corrélation de chaque variante au filtrage de sa prise ouverte : un vent filtré naturellement discret n'est pas remonté artificiellement. `python scripts/test-supernova-audio.py` exécute les huit tests DSP. `scripts/import-supernova-sfx.py` reste disponible pour des enregistrements dont les droits de redistribution sont acquis : il écrit un reçu `docs/supernova-audio-sources.json`, et le générateur refuse ensuite d'écraser ces prises tant que ce reçu existe.

Le test connecté vérifie aussi les dégâts réels via le code serveur de production : cible lointaine, haute/basse altitude, entrée/sortie de la liste chargée, autre dimension, immunités, annulation Forge et exclusion d'une entrée périmée dans un chunk déchargé. Les chunks des fixtures sont préparés uniquement dans le monde de test jetable ; aucune impulsion de production ne doit charger de chunk. Les entités temporaires ne sont ni suivies ni sauvegardées. Les captures du framebuffer couvrent la sélection par quatrième ligne et par N, la fin naturelle, le saut local et le nettoyage caméra/audio :

```powershell
.\gradlew.bat --no-daemon --console=plain exportDevLaunches -Psmoke -x downloadAssets
python scripts/smoke-supernova.py --java-home "$env:JAVA_HOME"
```

Le premier passage capture aussi les ticks 319, 320 et 321 autour du dessin d'impact, puis les fronts décalés et l'arrivée du choc atmosphérique. Le contrôle échoue si aucune capture ne tombe pendant les 2,6 ticks du dessin. Le second passage ferme le film puis déplace réellement le joueur de test à plus de 5 000 blocs de l'origine. Neuf captures sans cinématique couvrent l'étoile lointaine, F5 arrière / avant avec rendu du modèle du joueur, l'irradiation et les poussières globales. Un toit temporaire dans le seul monde jetable vérifie le passage au mix filtré puis le retour en plein air ; il est retiré après le contrôle. Les artifacts sont dans `build/smoke-supernova/<date-pid>/`. La planche `supernova-contact-sheet.jpg` sert à contrôler le rendu ; le rapport automatique vérifie les fichiers, les horaires et l'état du jeu, pas la beauté des images.

## SS-05 Gravity Inversion

La gravité s’inverse localement autour du point visé : entités, projectiles et fragments illusoires du terrain s’élèvent, tournent, puis s’effondrent ensemble. Sélection par **G**, la cinquième ligne du menu ou la touche magenta de la remote.

| Acte | Temps | Effet |
| --- | --- | --- |
| Gravity Failure | 0–4 s | Fissures lumineuses en étoile ; étincelles et petits débris flottent |
| World Ascension | 4–10 s | Les ennemis décollent ; 30 fragments de terrain s’arrachent et flottent |
| Axis Rotation | 10–15 s | Les fragments tournent autour d’un axe invisible incliné ; la gravité bascule 4 fois et projette les corps |
| Total Collapse | 15–19 s | Suspension de 0,3 s, rabattement violent, onde de choc géante |

- **Zone** : cylindre de **180 m** de rayon, de 24 blocs sous la cible à 120 au-dessus.
- **Dégâts** : **15/s** (7,5 toutes les 10 ticks, armure ignorée) de 4 s à 15 s, puis **120** à l’effondrement (15,8 s). Le lanceur est pris dans sa propre frappe et subit les dégâts complets. Les créatifs sont soulevés mais, comme partout en jeu, ne prennent pas de dégâts ; les spectateurs ne sont pas touchés. Les dégâts de chute des corps capturés sont annulés.
- **Rotation** : les corps capturés tournent autour de l’axe avec les fragments pendant l’Axis Rotation, et chaque corps culbute sur lui-même avant de se redresser à l’impact. Un joueur capturé voit sa vue s’incliner et pivoter à chaque bascule de gravité ; son corps tourne réellement, les autres joueurs le voient tourner.
- **Corps affectés** : créatures, joueurs, projectiles, boules de feu, objets au sol, orbes d’XP, TNT et blocs qui tombent. Les joueurs reçoivent une lévitation invisible pendant la capture pour éviter l’expulsion « vol non autorisé ».
- **Terrain** : jamais modifié. Les fragments sont dessinés côté client avec les textures des blocs réellement présents sous leur ancrage ; la même graine donne les mêmes fragments et les mêmes bascules de gravité au serveur et à chaque client.

Le lanceur voit un film : plans au ras des fissures, grue sur l’ascension, portrait de son propre corps qui s’élève au milieu des fragments, orbite pendant la rotation avec roulis à chaque bascule et caméra collée à son corps sur les 2ᵉ et 4ᵉ bascules, plan fixe sous le champ suspendu, plongée accélérée sur l’effondrement puis recul aérien secoué. Impact frames à l’encre à chaque bascule, au rabattement et quatre cels alternés positif/négatif à l’effondrement ; flash, lignes de vitesse, cartons titres et jauge de gravité. **J / Échap** quitte le film, la frappe continue. Les témoins sans film voient la scène dans le monde, avec flash, assombrissement pendant la suspension et secousses de caméra.

Test connecté (client réel + serveur réel, monde jetable) :

```powershell
.\gradlew.bat --no-daemon --console=plain exportDevLaunches -Psmoke -x downloadAssets
python scripts/smoke-inversion.py --java-home "$env:JAVA_HOME"
```

Au démarrage du serveur, une sonde fait passer des fixtures dans le vrai champ de production, tick par tick, loin du joueur : 22 impulsions de 7,5 puis 120 au centre et à 179 m, rien à 181 m ni au-dessus du plafond, lanceur touché, créatif soulevé sans dégâts (vol créatif coupé), spectateur et joueurs-machines exemptés, objets et flèches qui flottent dès la Gravity Failure, lévitation invisible posée puis retirée au rabattement, annulation Forge respectée, aucun chargement de chunk. Le client sélectionne ensuite SS-05 par la cinquième ligne du menu puis par **G**, regarde le film complet (26 captures, dont l’impact frame de l’effondrement) et vérifie que les 22 sons du film démarrent. Une vache nommée sur le terrain de test doit s’élever d’au moins 5 blocs, prendre exactement 15/s + 120 et aucun dégât de chute. Le second passage saute le film, vérifie la secousse des témoins, capture cinq vues sans film, puis annule la frappe avant l’effondrement. Le second lancement attend la recharge serveur de 30 s. Artifacts : `build/smoke-inversion/<date-pid>/`, planche `inversion-contact-sheet.jpg`.

## Configuration serveur

Premier lancement : création de `config/stellar.cfg`.

Les dégâts du Railgun sont actifs directement. Les anciennes options `destroyTerrain`, `damageEntities`, `blockBudget`, `loadChunks` et `operatorsOnly` sont ignorées ; `/stellar terrain` a été retirée.

La télécommande et `/stellar` sont réservées aux opérateurs (niveau 2). En solo, il faut les cheats activés.

| Paramètre | Défaut | Fonction |
| --- | --- | --- |
| `gameplay.targetRange` | `256` | Portée de ciblage calculée sur le serveur |
| `gameplay.cooldownTicks` | `600` | Recharge de 30 secondes à 20 TPS |
| `performance.maxActive` | `2` | Nombre de frappes simultanées par dimension |

`/stellar status` affiche les frappes actives, le rayon et le débit des dégâts. `/stellar cancel` arrête les frappes et leurs dégâts dans la dimension courante. Un arrêt/redémarrage du serveur ou déchargement de dimension interrompt également les frappes. Les anciens blocs aiguille/noyau restent enregistrés pour la compatibilité des sauvegardes, mais ne sont plus proposés en créatif ni générés.

## Sons

Tous les sons empaquetés sont synthétisés par les scripts du dépôt, à partir d'oscillateurs et de bruit, sans enregistrement ni échantillon. Les 38 fichiers qui provenaient d'ElevenLabs (offre gratuite, usage non commercial) ont été refaits :

| Script | Sons |
| --- | --- |
| `generate-cinematic-sfx.py` | 11 sons du Railgun (`ascent`, `warp`, `flyby`, `charge_loop`, `electric_snap`, `cannon_shot`, `tunnel`, `descent`, `impact_hit`, `beam_loop`, `rumble_tail`) |
| `generate-blackhole-sfx.py` | 7 sons `blackhole_*` |
| `generate-moon-attract-sfx.py` | 14 sons `moon_attract_*` |
| `generate-supernova-sfx.py` | 3 sons de témoin Supernova et leurs 3 variantes sous toit |

Chaque son garde son nom, sa durée et son rôle. Son niveau est calé sur la sonie momentanée maximale (pondération K, fenêtres de 400 ms) du fichier qu'il remplace, donc les volumes réglés dans les classes `*Soundtrack` restent valables. Les boucles sont construites en cycles entiers, sans fondu. Ces quatre scripts acceptent `--check` ; les trois premiers acceptent aussi `--only nom` et `--verify-reproducible`. Ils demandent numpy, scipy et ffmpeg. Les prompts de [`docs/elevenlabs-sound-prompts.md`](docs/elevenlabs-sound-prompts.md) et de [`docs/blackhole-elevenlabs-prompts.md`](docs/blackhole-elevenlabs-prompts.md) restent comme cahiers des charges. Pour les repères actuels du Railgun, voir la [chronologie appliquée](docs/railgun-reference-100ms.md).

## Compilation

JDK **8**, Gradle **5.6.4**, ForgeGradle **3.0.197**, mappings `snapshot_20171003-1.12`. Le wrapper vérifie le SHA-256 de la distribution Gradle. Aucun changement du Java global n’est nécessaire :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot'
.\gradlew.bat --no-daemon --console=plain build
```

Le résultat obfusqué pour Forge est `build/libs/beyond-the-space-1.1.0.jar`. `build` exécute les tests de chronologie, d’orbites, de rayon et de codecs ainsi qu’un contrôle des dépendances physiques client/serveur. `runClient` et `runServer` lancent les environnements de développement.

### Vérification en jeu

Les pilotes de test sont dans des jeux de sources séparés et ne sont pas inclus dans le JAR distribué. Ils utilisent uniquement le monde plat jetable `run-server/stellar-smoke-world`, les ports locaux `25591` (jeu) et `25592` (RCON). Le dossier `run-server` doit contenir sa configuration de test et une acceptation locale de l’EULA Minecraft avant le démarrage.

```powershell
# Client réel connecté au serveur réel : Railgun, menu et saut local.
.\gradlew.bat --no-daemon --console=plain exportDevLaunches -Psmoke -x downloadAssets
python scripts/smoke-game.py --java-home "$env:JAVA_HOME"

# Remote : quatre sélections, perspective, clavier, échelles d’interface, sons réels et pouce animé.
python scripts/smoke-remote-menu.py --java-home "$env:JAVA_HOME"

# SS-03A Asteroid Puller : film complet, saut local, annulation et 42 captures framebuffer.
python scripts/smoke-moon.py --java-home "$env:JAVA_HOME"

# Serveur réel : 100 dégâts/s, frontières, entrée/sortie, protection et terrain intact.
.\gradlew.bat --no-daemon --console=plain exportDevLaunches -PrailgunSmoke -x downloadAssets
python scripts/smoke-server.py --java-home "$env:JAVA_HOME" --railgun-check
```

Les captures viennent directement du framebuffer Minecraft et se trouvent dans `build/smoke/screenshots`. Les journaux sont dans `build/smoke/` et `build/smoke-server.log`. Pour un nouveau poste de développement, télécharger d’abord les assets avec `downloadAssets` ; l’exclusion ci-dessus réutilise les assets déjà téléchargés. Un correctif limité aux lancements de développement donne priorité à l’énumération `Side` du Forge officiel, car l’ancien MergeTool injecte une variante incompatible. Ce correctif n’est pas embarqué dans le mod.

Le test de la remote utilise un client isolé et le même monde jetable. Ses 23 captures, son rapport et la planche `remote-contact-sheet.jpg` sont dans `build/smoke-remote/<date-pid>/`. Les assertions contrôlent les interactions, les matrices OpenGL, les cinq événements audio, la caméra, l’absence de lancement et la touche enfoncée par le pouce (menu et première personne) ; les images servent à vérifier le rendu visuellement, y compris en troisième personne.

Résultats et limites du test : [vérification du menu holographique](docs/remote-menu-verification.md).

## Référence et différences

Railgun s'inspire de la vidéo locale **« Minecraft The Shooting Star Mod Is INSANE » — Sigma Villager**. La [réanalyse à 0,1 seconde](docs/railgun-reference-100ms.md) a été appliquée **au client uniquement** : les 19 repères sont conservés, mais la montée spatiale à 2,9–9,7 s est désormais **un dézoom continu**, sans coupe entre Terre, Saturne, système solaire et galaxie. Marquage à 0,6 s, charge progressive à 11,9–12,5 s, arcs anguleux et colliers lumineux, décharges atténuées à 12,6 s, flash blanc à 12,7 s, transit à 12,8 s, retour Terre à 13,2–13,3 s et tunnel à 13,4–13,7 s, puis ciel du monde réel. L’arrivée à 14,1 s emploie des enveloppes à bords blancs et une trame pointillée ; l’impact commence à 14,3 s. Le corps se contracte à **18,7–19,3 s**, avec flash terminal à 19,2 s. Surface sombre et liseré de danger de rayon fixe persistent jusqu’au fondu **25,0–25,7 s** ; la caméra finit à **25,7 s**. Les repères audio sont alignés sur cette présentation, sans changement du gameplay partagé.

Les caméras terrestres verrouillent leurs trajectoires dans les secteurs chargés et se replient sur une vue aérienne plus basse au-dessus du terrain réellement disponible si la distance de vue est courte. La couche au sol se limite aux cellules compilées effectivement rendues, mises en cache une fois par frame. Le paysage reste intact : **ni cratère réel, ni génération/chargement forcé de chunks**. À distance serveur 4, le smoke ne peut pas montrer tout le liseré de 200 blocs. Le [bilan actuel](docs/verification.md) distingue captures réelles et limites ; les [seize plans antérieurs](docs/railgun-reference.md) restent un historique. Aucune fidélité exacte ni fluidité continue à 60 FPS n’est démontrée.

Référence : [page officielle CurseForge](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo), consultée le 7 octobre 2026, description de la démo 1.3.1. Les durées, dégâts et visuels sont des choix du projet, pas des valeurs extraites de son code.

Les scènes utilisent des planètes, étoiles et machines recréées ; elles ne reproduisent pas à l’identique les textures spatiales, les post-traitements ni les mouvements de caméra de la démo. Sons originaux pour la cinématique Railgun et sons Minecraft pour le marquage et l’impact. Aucun code, texture ou son du mod propriétaire n’est intégré ou redistribué.

## Licence

Les contributions originales de Beyond The Space sont publiées sous [licence MIT](LICENSE), copyright (c) 2026 Frabous. La réutilisation, la modification et la redistribution, y compris commerciales, sont autorisées en conservant les mentions de copyright et de licence. Le texte complet est également inclus dans le JAR sous `META-INF/LICENSE`.

Cette licence ne remplace pas celles des dépendances et des contenus tiers. La notice du MDK Forge est conservée dans [`FORGE-MDK-LICENSE.txt`](FORGE-MDK-LICENSE.txt). Les sons empaquetés sont produits par les scripts `scripts/generate-*-sfx.py` du dépôt ; le JAR ne contient plus d'enregistrement ElevenLabs ou tiers.
