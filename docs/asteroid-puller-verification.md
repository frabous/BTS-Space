# SS-03A Asteroid Puller — rework cinématique et famille Carrier Ops

Remplace l'ancien « Asteroide Attract » ([historique](asteroide-attract-verification.md)).
Sauvegarde complète des fichiers d'avant rework :
`backups/asteroid-puller-pre-rework-2026-10-15/`.

## Famille SS-03 Carrier Ops

Le destroyer devient une **famille d'attaques** (`api/AttackFamily.CARRIER`).
Asteroid Puller est sa charge **A** (`AttackId.ASTEROID_PULLER`, code `SS-03A`).
La touche **M** sélectionne la famille ; chaque nouvel appui passe à la charge
suivante. Dans le menu, la carte Carrier affiche des puces A, B… et
**Gauche/Droite** change de charge quand la famille en contient plusieurs.

Ajouter une charge :

1. Déclarer une entrée `AttackId` avec la famille `CARRIER` et un nouvel identifiant réseau.
2. Lui donner sa chronologie et sa scène.
3. Réutiliser `CarrierFlight.Plan` (arrivée warp, station, soute, largage, départ, saut)
   et `CarrierRenderer` (coque, soute animée, portails, réacteurs).

Compatibilité conservée : identifiant réseau 5, clé `moon_attract` (NBT, touche,
ressources, sons).

## Chronologie (20 TPS, 860 ticks = 43 s)

| Temps | Ticks | Phase |
| --- | --- | --- |
| 0–1,2 s | 0–24 | Balise ambrée sur le bloc visé, hologramme de zone, faisceau de liaison montante. |
| 1,2–4,1 s | 24–82 | Coupe vers l'orbite. Portail warp (spirale, arcs, onde de choc) ; le destroyer sort étiré puis freine. Fermeture à 3,4 s. |
| 4,1–6,4 s | 82–128 | Soute ventrale ouverte, pinces, largage du Graviton Lance à 5,1 s. |
| 6,4–8,8 s | 128–176 | Retour au sol par voile de nuages. Chute continue, plasma de rentrée, rétrofusées à 7,5 s, contact à 8,6 s, ancrage. |
| 9,3–11,7 s | 186–234 | Le canon se déplie (télescopes, ailettes, couronne). Le vaisseau relance ses réacteurs, part et saute en warp. |
| 11,8–18,5 s | 236–370 | L'astéroïde traverse le ciel à ~15 blocs/tick ; plan téléobjectif 14–16 s ; charge gravitationnelle à 16,5 s. |
| 18,5–19,1 s | 370–382 | Tir du harpon sur le point d'interception calculé ; accroche à 19,1 s. |
| 19,1–28,5 s | 382–570 | Longe gravitationnelle : virage de plus de 120°, entrée atmosphérique à 25,3 s, rupture de la longe à 27,8 s, silence à 28,2 s. |
| 28,5–43 s | 570–860 | Impact, dôme de choc, colonne et champignon, 110 débris balistiques, retombées, fondu final. |

Le serveur applique les dégâts sur la fenêtre **28,5–32,5 s** (rayon 200, 100 dégâts/s).

## Physique vérifiée par tests

- **Trajectoire** (`AsteroidTrajectory`) : passage rectiligne à plus de 1000 blocs
  d'altitude ; apparition à 2000–2900 blocs ; raccords C1 (sans cassure de vitesse) ;
  vitesse 15 → ~4 au sommet du virage → 26 blocs/tick au contact ; plongée de 35–60° ;
  descente monotone après l'accroche ; échauffement monotone.
- **Canon** (`AsteroidPullerLance`) : continuité au largage, atterrissage doux,
  pieds au sol à 0,06 bloc près, visée sur le point d'interception à moins de 0,05°
  au tir, longe alignée à moins de 0,8°, tourelle sans saut supérieur à 1,5°.
  Après la rupture, la tourelle se fige et le canon s'affaisse.
- **Caméra** (`AsteroidPullerCamera`) : aucune téléportation hors coupes voulues,
  raccord orbite/monde identique à 6,4 s, jamais dans l'astéroïde, sujets cadrés
  (vaisseau, canon en chute, astéroïde avant tir et avant impact).

## Sons SS-03 (synthétisés)

Générateur : `scripts/generate-asteroid-puller-sfx.py` (mono Vorbis 44,1 kHz).
Le tick de départ est choisi pour que le temps fort tombe sur l'animation.

| Son | Départ | Temps fort |
| --- | --- | --- |
| `carrier_warp_open` | 24 / 218 | éclatement à +0,8 s → sortie (40) / saut (234) |
| `carrier_brake` | 46 | poussée de freinage |
| `carrier_warp_collapse` | 54 / 237 | pincement à +0,55 s → fermeture du portail |
| `carrier_bay_open` | 82 | verrou, butée des portes (+0,5 s), verrou du berceau (+1,06 s) |
| `carrier_clamp_release` | 102 | ouverture des pinces |
| `carrier_drive_loop` | 40–238 | boucle, niveau et hauteur montent au départ |
| `carrier_spool` | 186 | allumage à +0,7 s → départ (200) |
| `lance_reentry` | 106 | plasma de rentrée |
| `lance_retro` | 150 | coupure à +1,1 s → contact (172) |
| `lance_touchdown` | 172 | impact, rebond à +0,62 s |
| `lance_anchor` | 176 | 4 ancres, verrou hydraulique |
| `lance_unfold` | 196 | verrous à 212, 222, 226, 234 |
| `lance_harpoon` | 370 | tir, échos du terrain |
| `tether_lock` | 382 | accroche gravitationnelle |
| `tether_strain_loop` | 382–556 | boucle, plus grave quand la charge augmente |
| `asteroid_sonic_boom` | 526 | double bang (+0,02 s, +0,36 s) |
| `tether_snap` | 556 | rupture de la longe |

## Vérification finale

- Build Java 8, réobfuscation et contrôle client/serveur (`verifySides`) : **PASS**.
- **125 tests JUnit, zéro échec**.
- `python scripts/verify-artifact.py` : **PASS** (classes Carrier Ops présentes,
  ancien `graviton_cannon.mesh` absent du JAR).
- Smoke Minecraft connecté : **PASS**, 42 captures en 1280 × 720 du film complet
  plus 22 pour la séquence saut/annulation. Fin naturelle à 43 s, saut local à
  14,5 s qui conserve l'attaque serveur, annulation, caméra/HUD restaurés,
  sons arrêtés, `glError` nul partout. Les 17 sons SS-03 démarrent pendant le
  film, sans erreur de codec ni d'événement inconnu.

Preuves : [`build/smoke-moon/20261015-190805-33544/`](../build/smoke-moon/20261015-190805-33544/).

- [Portail warp et Terre](../build/smoke-moon/20261015-190805-33544/screenshots/sequence-0-moon-48ticks.png)
- [Sortie du destroyer](../build/smoke-moon/20261015-190805-33544/screenshots/sequence-0-moon-56ticks.png)
- [Astéroïde au téléobjectif](../build/smoke-moon/20261015-190805-33544/screenshots/sequence-0-moon-320ticks.png)
- [Longe gravitationnelle](../build/smoke-moon/20261015-190805-33544/screenshots/sequence-0-moon-420ticks.png)
- [Entrée atmosphérique](../build/smoke-moon/20261015-190805-33544/screenshots/sequence-0-moon-558ticks.png)
- [Impact](../build/smoke-moon/20261015-190805-33544/screenshots/sequence-0-moon-596ticks.png)
- [Planche complète](../build/smoke-moon/20261015-190805-33544/moon-contact-sheet.jpg)

JAR officiel 1.1.0 : `build/libs/beyond-the-space-1.1.0.jar`, **10 415 715 octets** (avec les 17 sons SS-03).
SHA-256 : `c897c6d3fce7268c8e6de89640b40446c7d462c91134d576140d27b793bd90b8`.

### Corrections après la première revue visuelle

Le premier passage montrait un astéroïde presque noir (éclairé par le dessus, vu
d'en dessous), trop petit à l'écran, et un portail vu de profil sans la Terre.
Corrections : lumière clé basse d'après-midi partagée par l'astéroïde et son
ombre, rebond chaud et ciel bleuté sur la face cachée ; plans téléobjectif
(14–16 s, puis après l'accroche) ; plan orbital de trois quarts avec la planète
dans le tiers inférieur.

## Limites connues

- Monde de test plat avec peu de chunks chargés : au-delà, le sol est remplacé par le
  brouillard et l'ombre de l'astéroïde peut flotter. Terrain accidenté non testé.
- Les 17 sons SS-03 sont synthétisés et vérifiés par mesures (durée, niveaux,
  raccord des boucles, spectrogrammes alignés sur les temps forts) et par leur
  démarrage en jeu ; leur rendu à l'oreille reste à juger. Les horaires de
  `moon-attract-elevenlabs-prompts.md` datent de la version 40 s.
- Le Graviton Lance AG-07 est procédural (`GravitonLanceModel`) ; l'ancien
  `graviton_cannon.bbmodel` reste en historique et n'est plus empaqueté.
- La fluidité continue à 60 FPS et la compatibilité shaders ne sont pas démontrées.
