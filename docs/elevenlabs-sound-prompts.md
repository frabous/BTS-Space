# Railgun — prompts ElevenLabs Sound Effects

> **Statut (17 octobre 2026) :** les onze sons ne viennent plus d'ElevenLabs. Ils sont synthétisés par `scripts/generate-cinematic-sfx.py`, avec les mêmes noms, durées et rôles. Ce document reste comme cahier des charges ; les sections sur les MP3 intégrés sont historiques.

Prompts prêts à copier, en anglais, pour les sons réellement utilisés par `CinematicSoundtrack.java`. Direction commune : **arme orbitale colossale, énergie électromagnétique rouge/blanc, science-fiction cinématique**, avec une séparation nette entre charge, décharge et faisceau continu.

## Fichiers ElevenLabs intégrés

Les onze MP3 fournis ont été convertis et intégrés dans `src/main/resources/assets/stellar/sounds/`, puis embarqués dans le JAR. Format : **OGG Vorbis mono, 44 100 Hz, qualité Vorbis 5**. Les durées courtes ont été complétées par quelques millisecondes de silence ; les transitoires ont une fermeture de 10 ms pour éviter les clics. Les deux boucles de 2,25 s ont été ramenées à 2 s avec un raccord croisé de 0,25 s. Les crêtes ont été ajustées à la marge de niveau des anciens assets.

| MP3 fourni | OGG intégré | Durée finale |
| --- | --- | --- |
| `A_colossal_sci-fi_ta_#4-1791478660995.mp3` | `ascent.ogg` | 2,70 s |
| `A_single_cinematic_h_#4-1791478689885.mp3` | `warp.ogg` | 1,90 s |
| `A_close_cinematic_fl_#4-1791478723184.mp3` | `flyby.ogg` | 2,20 s |
| `A_seamless_steady-st_#2-1791478825228.mp3` | `charge_loop.ogg` | 2,00 s |
| `A_single_sharp_high-_#2-1791478977879.mp3` | `electric_snap.ogg` | 0,32 s |
| `A_single_colossal_or_#2-1791483597152.mp3` | `cannon_shot.ogg` | 2,00 s |
| `A_one-second_rush_th_#4-1791483624731.mp3` | `tunnel.ogg` | 1,00 s |
| `A_concentrated_orbit_#4-1791483651741.mp3` | `descent.ogg` | 1,10 s |
| `A_gigantic_orbital_e_#4-1791483681017.mp3` | `impact_hit.ogg` | 2,00 s |
| `A_seamless_steady_lo_#3-1791483735305.mp3` | `beam_loop.ogg` | 2,00 s |
| `The_aftermath_of_a_c_#4-1791483806567.mp3` | `rumble_tail.ogg` | 2,60 s |

`python scripts/generate-cinematic-sfx.py --check` valide les onze fichiers : codec, canaux, fréquence, durées, niveau, décalage continu et raccord des boucles. Les crêtes décodées vont de 0,632 à 0,726, sans clipping ; les écarts de raccord sont de 0,0511 pour la charge et 0,0012 pour le faisceau, sous la limite du validateur de 0,08. Il s’agit de contrôles techniques, pas d’une évaluation à l’écoute.

## Réglages et export

- Utiliser **Sound Effects**, avec une génération distincte pour chaque fichier. Pas de voix ni de musique.
- Régler la durée dans l’interface. Les durées ci-dessous correspondent aux fichiers actuels et aux limites de lecture du code : une queue plus longue risque d’être coupée.
- Activer **Looping** uniquement pour `charge_loop` et `beam_loop`. Générer un son stable : le jeu applique déjà les montées de volume/hauteur, puis l’extinction.
- La durée minimale documentée par ElevenLabs est **0,5 s**. Pour `electric_snap`, générer 0,5 s, puis couper/exporter **0,32 s** avec une très courte fermeture sans clic.
- Garder les attaques au début du fichier, sans silence initial. Les explosions ne doivent pas avoir de précharge intégrée.
- Export final : **OGG Vorbis mono, 44 100 Hz**, sans clipping. Garder une marge de crête : environ **−3 à −6 dBFS**, puis équilibrer les sons à l’écoute en jeu.
- Les onze fichiers remplacent ceux de `src/main/resources/assets/stellar/sounds/`, avec **exactement les mêmes noms**. `sounds.json` peut rester tel quel. Recompiler ensuite le JAR.
- Ne pas relancer `generate-cinematic-sfx.py` après remplacement : ce générateur réécrirait les fichiers avec les sons synthétiques.

Documentation consultée : [Sound Effects](https://elevenlabs.io/docs/eleven-creative/playground/sound-effects) et [durée/boucles](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert). Une boucle générée doit encore être écoutée sur plusieurs répétitions pour vérifier sa jonction.

## Chronologie des onze fichiers

Les temps partent du lancement, à **20 TPS** ; le déclenchement réel est arrondi au tick client suivant. La hauteur de certains sons est aussi modifiée par le jeu.

| Fichier | Durée du fichier | Boucle | Déclenchement / usage |
| --- | --- | --- | --- |
| `ascent.ogg` | 2,70 s | Non | 0,00 s : départ et ascension |
| `warp.ogg` | 1,90 s | Non | ≈ 2,91 et 5,89 s : transitions spatiales, deux hauteurs différentes |
| `flyby.ogg` | 2,20 s | Non | 9,30 s : révélation et travelling du canon |
| `charge_loop.ogg` | 2,00 s | Oui | ≈ 12,02–12,70 s : charge, accélérée par le code |
| `electric_snap.ogg` | 0,32 s | Non | ≈ 12,12 et 12,50 s : arcs électriques |
| `cannon_shot.ogg` | 2,00 s | Non | 12,70 s : décharge du canon |
| `tunnel.ogg` | 1,00 s | Non | ≈ 12,92 s : traversée rapide |
| `descent.ogg` | 1,10 s | Non | ≈ 14,20 s : arrivée atmosphérique, juste avant l’impact |
| `impact_hit.ogg` | 2,00 s | Non | 14,30 s : contact du faisceau avec la zone |
| `beam_loop.ogg` | 2,00 s | Oui | 12,70–22,70 s : tir puis impact soutenu |
| `rumble_tail.ogg` | 2,60 s | Non | 22,70 s : extinction et survol final |

### 1. Ascension — `ascent.ogg`

**Intention :** sentir une montée verticale rapide, de l’air terrestre vers l’espace. L’énergie accélère, sans son de fusée conventionnelle ni explosion.

```text
A colossal sci-fi targeting pulse accelerating vertically from the ground into orbit. Begin immediately with a tight low electromagnetic hum and a restrained pressure thump, then build into a rising turbine-like whine wrapped in rushing air. Increase speed and brightness continuously, ending with a thin airy release into space. Powerful, clean, precise, ominous; layered bass and detailed high-frequency air, no harsh clipping. One continuous ascending whoosh lasting 2.7 seconds. No speech, no music, no rocket combustion, no final explosion.
```

### 2. Transition spatiale — `warp.ogg`

**Intention :** changement brutal d’échelle, planète vers espace lointain. Ce même fichier joue deux fois ; ne pas produire deux effets dans un seul fichier.

```text
A single cinematic hyperspace transition for an orbital weapon camera pulling rapidly away from a planet. Start with a brief vacuum-like suction, sweep into a dense accelerating electromagnetic whoosh, crest with a sharp bright tearing-air texture, then rapidly dissolve into a distant low-frequency resonance. Convey enormous speed and expanding cosmic scale without a gunshot or explosion. Smooth continuous pitch movement, controlled sub-bass, clean airy details, mono-compatible. One self-contained transition lasting 1.9 seconds. No speech, no music, no separate repeated whooshes.
```

### 3. Travelling du canon — `flyby.ogg`

**Intention :** passer très près d’une immense structure métallique en orbite ; lourd et mécanique, sans déjà annoncer le tir.

```text
A close cinematic flyby along the armored rails of an enormous orbital cannon. A heavy electromagnetic machinery hum approaches from a distance, swells into a powerful metallic airflow pass, then drops in pitch and recedes with a clear Doppler character. Add fine vibrating metal resonances and a restrained deep engine-like body, suggesting immense mass and precision engineering. The loudest pass occurs near the middle, followed by a short clean decay. One flyby lasting 2.2 seconds. No speech, no music, no weapon firing, no explosion, no rhythmic mechanical clanking.
```

### 4. Charge électromagnétique — `charge_loop.ogg`

**Intention :** lit électrique stable. La montée dramatique est pilotée par le code ; ne pas la figer dans le fichier.

```text
A seamless steady-state loop of a gigantic electromagnetic railgun capacitor under extreme electrical tension. Layer a deep energized transformer hum, a focused metallic coil resonance, fine sizzling plasma, and subtle rapid amplitude flutter. Dense, precise, dangerous stored energy, with enough midrange detail to remain audible above bass. Maintain constant overall loudness and pitch throughout; the game will apply the charging rise. Exactly 2 seconds, seamless end-to-start continuity. No attack, no fade-out, no climax, no discharge, no speech, no music, no isolated loud sparks.
```

### 5. Arc électrique — `electric_snap.ogg`

**Intention :** très court et sec, distinct de la charge. Générer **0,5 s**, conserver les **0,32 premières secondes**.

```text
A single sharp high-voltage arc snapping between the metal conductors of a colossal sci-fi railgun. Immediate dry electrical crack at the very beginning, followed by a tiny metallic zing and a rapidly vanishing sizzling tail. Bright and aggressive but tightly controlled, with very little low-end and almost no room reverb. The entire meaningful sound must finish within the first 0.3 seconds of this 0.5-second clip. No leading silence, no second loud strike, no thunder, no explosion, no speech, no music.
```

### 6. Tir orbital — `cannon_shot.ogg`

**Intention :** événement le plus puissant côté canon : décharge instantanée, masse gigantesque, fin contrôlée pour ne pas masquer l’impact terrestre.

```text
A single colossal orbital railgun discharge, firing a concentrated electromagnetic beam. Immediate razor-sharp electrical-metallic crack layered with a massive controlled sub-bass pressure punch, then a tearing plasma roar and two subtle short metallic reflections. Convey impossible energy and the recoil of a gigantic engineered cannon, rather than a conventional artillery shell. The transient hits instantly, the body lasts briefly, and the resonance decays fully within 2 seconds. Powerful cinematic game sound, clean headroom, no pre-charge, no repeated shots, no rubble, no speech, no music.
```

### 7. Tunnel de vitesse — `tunnel.ogg`

**Intention :** vitesse fulgurante, plus aiguë et plus serrée que le warp ; une seule traversée.

```text
A one-second rush through a narrow hyperspeed plasma tunnel immediately after an orbital railgun fires. Dense forward-moving air shear, a rapidly climbing electronic whistle, and fine crackling streaks whipping past, with a taut low pressure undertone. Fast, focused, bright, and tightly compressed in time; peak near the middle and exit quickly with a clean short tail. Suggest extreme velocity through red-white energy rather than an engine or a portal opening. One continuous motion. No speech, no music, no explosion, no repeated rhythmic pulses.
```

### 8. Descente atmosphérique — `descent.ogg`

**Intention :** l’arrivée commence à peine **0,1 s avant le contact**. La sensation de chute doit donc être immédiate, avec une queue discrète.

```text
A concentrated orbital energy beam slicing downward through the atmosphere at extreme speed. Start immediately with a piercing descending plasma whistle and compressed air tearing past; make the pitch plunge and the strongest downward rush happen within the first 0.08 seconds. Follow with a thin rapidly fading ionized-air hiss for the remainder of the 1.1-second clip. Urgent, precise, dangerous, no heavy low-end tail that could mask the following impact. No impact boom, no ground debris, no speech, no music, no slow buildup.
```

### 9. Impact énergétique — `impact_hit.ogg`

**Intention :** énorme pression à l’arrivée du faisceau, mais sans bruits de terrain qui se détruit.

```text
A gigantic orbital energy beam making sudden contact with a target zone while the surrounding ground remains intact. Instant deep pressure slam, a crisp ionized electrical crack, and a short expanding atmospheric shockwave, followed by a dense low resonance that decays within 2 seconds. Differentiate this from the cannon discharge: heavier, lower, broader, and grounded, with restrained upper-frequency detail. One powerful impact, clean cinematic headroom. No collapsing buildings, no breaking stone, no flying rubble, no secondary explosions, no speech, no music.
```

### 10. Faisceau continu — `beam_loop.ogg`

**Intention :** corps soutenu du rayon, sans impact répétitif et sans crescendo préfabriqué. Doit rester confortable sur environ dix secondes.

```text
A seamless steady loop of a colossal sustained orbital energy beam. Layer a deep electromagnetic power roar, dense broadband plasma hiss, a focused metallic resonance, and slow irregular pressure flutter. Convey a continuous lethal energy column with enormous force, not a sequence of explosions. Powerful low-mid body with controlled sub-bass and smooth non-piercing high frequencies, suitable for listening over many repetitions. Exactly 2 seconds, constant overall intensity, seamless end-to-start continuity. No startup transient, no crescendo, no fade-out, no debris, no speech, no music.
```

### 11. Extinction — `rumble_tail.ogg`

**Intention :** l’énergie s’éteint, pas une nouvelle explosion. Laisser le survol respirer.

```text
The aftermath of a colossal orbital energy beam shutting down. Begin with an already-present deep electromagnetic rumble, then let the bass resonance slowly sink in pitch and lose power while a faint ionized-air hiss and delicate metal-like ringing evaporate above it. A restrained final pressure release fades naturally into silence within 2.6 seconds. Spacious, ominous, exhausted energy; smooth decay with no fresh impact at the beginning. No crumbling terrain, no rubble, no new explosion, no speech, no music, no repeating pulses.
```

## Trois couches sonores du monde

Ces sons sont également joués par `LaunchController.java`, mais utilisent actuellement **des événements Minecraft**, sans fichier OGG propre à Stellar. Les prompts suivants couvrent donc toute la palette sonore existante. Leur intégration future demanderait des événements personnalisés et un changement des appels serveur ; remplacer les onze fichiers précédents suffit uniquement à refaire la cinématique.

### 12. Marquage de cible — actuellement `block.end_portal.spawn`

**Moment :** 0 s, au bloc ciblé. **Durée conseillée pour une future version personnalisée :** 1,2 s, sans boucle.

```text
An ominous sci-fi orbital targeting lock forming at a ground position. A compact low electromagnetic pulse blooms into a narrow resonant energy tone, with a subtle glassy electronic shimmer and a faint airy outward pressure release. Clearly signal that a dangerous target has been acquired without sounding like the weapon has fired. Immediate onset, strong readable midrange, restrained bass, smooth decay within 1.2 seconds. One activation sound. No interface melody, no spoken warning, no music, no explosion, no destruction, no repeating alarm.
```

### 13. Onde locale — actuellement `entity.generic.explode`

**Moment :** 14,3 s. **Durée conseillée :** 1,5 s, sans boucle. Une couche courte à combiner avec le grondement, plutôt qu’un deuxième tir complet.

```text
A single heavy atmospheric pressure wave from an orbital energy beam striking nearby. Immediate compact low-frequency thump with a dry compressed-air crack, spreading into a short turbulent air wash and quickly fading bass resonance. Make it physical and readable from a distance while leaving space for a separate thunder layer. An energy shockwave, not a conventional bomb. Fully decayed within 1.5 seconds. No fireball crackle, no shattering stone, no collapsing structures, no rubble, no secondary detonations, no speech, no music.
```

### 14. Grondement local — actuellement `entity.lightning.thunder`

**Moment :** 14,3 s, superposé à l’onde locale. **Durée conseillée :** 2,5 s, sans boucle.

```text
A vast rolling atmospheric thunder resonance caused by a colossal orbital energy beam. Start with a broad low-mid pressure swell rather than a sharp lightning crack, then unfold into irregular deep rumbling waves and soft distant air turbulence. Enormous outdoor scale, dark resonant body, controlled sub-bass and a natural decay to silence within 2.5 seconds. Designed to sit beneath a separate short impact transient. No rain, no windstorm ambience, no repeated lightning strikes, no terrain collapse, no speech, no music.
```

## Contrôle après génération

Écouter ensemble `cannon_shot`, `beam_loop` et `impact_hit` : le tir doit rester identifiable, le faisceau soutenu ne doit pas saturer le mix et l’impact doit se détacher à 14,3 s. Vérifier les deux jonctions de boucle, l’attaque et la queue finale. Les onze sons ont été fournis et intégrés ; leur équilibre artistique reste à évaluer à l’écoute en jeu.
