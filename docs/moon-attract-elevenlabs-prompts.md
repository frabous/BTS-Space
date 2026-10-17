# Moon Attract — contrat sonore et prompts ElevenLabs

> **Statut (17 octobre 2026) :** les 14 sons `moon_attract_*` ne viennent plus d'ElevenLabs. Ils sont synthétisés par `scripts/generate-moon-attract-sfx.py`, avec les mêmes noms et durées, et servent aujourd'hui à Asteroid Puller, Gravity Inversion et Supernova. Ce document reste comme cahier des charges ; la chronologie 40 s et l'import sont historiques.

Attaque Forge 1.12.2 : **40 secondes / 800 ticks monde**, à 20 ticks/s nominalement. Direction : canon gravitationnel massif, attraction lunaire puis collision cataclysmique, avec des couches distinctes et une réserve de dynamique pour l'impact. Aucun dialogue, voix, musique ou mélodie.

## Génération et montage

- [Documentation officielle ElevenLabs Sound Effects](https://elevenlabs.io/docs/eleven-creative/playground/sound-effects), consultée via Context7 : **450 caractères maximum par prompt UI**. Tous les blocs `text` ci-dessous sont directement copiables et respectent cette limite.
- [Contrat officiel de durée](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert) : **0,5 à 30 secondes**. Régler explicitement la durée indiquée, plutôt que Auto. Looping ON uniquement pour les deux boucles principales. Influence : commencer au réglage par défaut de 30 %, puis ajuster à l'écoute.
- Les indications temporelles du prompt ne garantissent pas une synchronisation à l'échantillon. Dans un DAW, déplacer les attaques, couper, compléter par du silence et appliquer les fondus pour respecter le contrat. Exporter chaque cue depuis son origine locale 0 s, sans silence de tête involontaire.
- Le temps local de chaque fichier commence au départ absolu indiqué dans la table. La charge est un **one-shot de 5 s**, même si son secours utilise `charge_loop`.
- Les quatre couches d'approche actives et la tension doivent finir à **27,7 s / tick 554**, y compris leurs queues et tout bus de réverbération. Fondre très brièvement avant la coupure. **27,7–28 s / ticks 554–560 : silence de toutes les couches de cette attaque**, puis impact à 28 s / tick 560. Ne pas repousser l'impact à 27,7 s.
- Le pull utilise un fichier de 8 s répété dans la fenêtre **11–27,7 s** ; couper la dernière répétition au tick 554. L'aftermath utilise un fichier de 8 s dans la fenêtre **32–40 s**, avec un fondu de lecture **38–40 s / ticks 760–800**. Ces fins et fondus relèvent de la lecture, pas de `sounds.json`.
- Les ticks monde sont l'horloge de l'attaque. `MoonAttractSoundtrack` déclenche les cues en fin de tick client, synchronise les boucles, coupe les sons à 554 et programme le fondu final. En cas de TPS inférieur à 20, une seconde audio réelle ne correspond plus à 20 ticks monde : les one-shots ne sont pas automatiquement étirés. L'importeur prépare les fichiers ; il ne reprogramme pas les événements Java.

## Les 14 cues : chronologie absolue

Clé de manifeste : `cinematic.<nom>` ; identifiant d'événement complet : `stellar:cinematic.<nom>`. Après import, ressource : `stellar:<nom>` et fichier `assets/stellar/sounds/<nom>.ogg`. Les intervalles sont début inclus, fin exclue.

| Nom exact | Départ s | Tick | Fichier s | Lecture | Fin s / tick | Secours existant |
| --- | ---: | ---: | ---: | --- | --- | --- |
| moon_attract_cannon_summon | 0 | 0 | 3 | one-shot | 3 / 60 | ascent |
| moon_attract_cannon_deployment | 1 | 20 | 4 | one-shot | 5 / 100 | flyby |
| moon_attract_gravity_charge | 5 | 100 | 5 | one-shot | 10 / 200 | charge_loop |
| moon_attract_maximum_charge | 9 | 180 | 1 | one-shot | 10 / 200 | electric_snap |
| moon_attract_gravity_beam_fire | 10 | 200 | 3 | one-shot | 13 / 260 | cannon_shot |
| moon_attract_moon_gravitational_pull | 11 | 220 | 8 | boucle, dernière répétition coupée | 27.7 / 554 | blackhole_tidal |
| moon_attract_moon_approaching | 16 | 320 | 11.7 | one-shot, coupure | 27.7 / 554 | rumble_tail |
| moon_attract_atmospheric_entry | 22 | 440 | 5.7 | one-shot, coupure | 27.7 / 554 | descent |
| moon_attract_moon_fragmentation | 23 | 460 | 4.7 | one-shot, coupure | 27.7 / 554 | impact_hit |
| moon_attract_pre_impact_tension | 26 | 520 | 1.7 | one-shot, fin franche | 27.7 / 554 | blackhole_horizon |
| moon_attract_moon_impact | 28 | 560 | 6 | one-shot | 34 / 680 | impact_hit |
| moon_attract_shockwave_expansion | 28.1 | 562 | 4 | one-shot | 32.1 / 642 | flyby |
| moon_attract_debris_rain | 30 | 600 | 8 | one-shot | 38 / 760 | rumble_tail |
| moon_attract_aftermath_ambience | 32 | 640 | 8 | boucle, fondu 38–40 s | 40 / 800 | blackhole_space_bed |

Les secours sont de vrais OGG déjà présents. Ils permettent la résolution des événements, mais ne reproduisent ni les durées ni le design final ; le lecteur doit aussi contrôler leur fin. Aucun nouvel audio n'est fourni ici.

## Prompts principaux

Enregistrer les résultats sous le nom exact du cue avec une seule extension `.mp3`, `.wav` ou `.ogg`. Copier uniquement le texte du bloc dans ElevenLabs.

### 01 — moon_attract_cannon_summon

Durée : **3 s**, Looping OFF. Absolu : **0–3 s**.

```text
3-second cinematic gravity cannon materialization. Start with a tight vacuum intake, layer a dark sub-pressure bloom, granular metallic particles converging, and a restrained glassy energy shimmer. Resolve into a heavy hollow housing thud, then a short dry tail. Massive engineered machinery, close and tactile, clear mono-compatible layers, ample headroom. No voice, no music.
```

### 02 — moon_attract_cannon_deployment

Durée : **4 s**, Looping OFF. Absolu : **1–5 s**.

```text
4-second deployment of a colossal gravity cannon. Interlocking steel segments unfold with staggered servo whines, weighty ratchet clicks and hydraulic breaths over a quiet structural groan. End with one precise locking clunk and a brief settling rattle. Detailed mechanical midrange, controlled bass, no explosion or charge crescendo, mono-compatible. No voice, no music.
```

### 03 — moon_attract_gravity_charge

Durée : **5 s**, Looping OFF. Absolu : **5–10 s**. Maximum charge se superpose pendant la dernière seconde.

```text
5-second gravity cannon charge, a single rising event. A deep pressure hum tightens beneath accelerating irregular electrical pulses, strained metal resonance and fine ionized crackle. Increase density and tension gradually, not just volume; narrow toward a focused held crest in the last second. End cleanly without firing or an explosive payoff. Controlled lows, mono-compatible. No voice, no music.
```

### 04 — moon_attract_maximum_charge

Durée : **1 s**, Looping OFF. Absolu : **9–10 s**.

```text
1-second maximum-charge accent for a gravity cannon. A sharp magnetic latch and compact electric snap reveal a briefly saturated, trembling energy core, with a tight sub-pressure knock and fine sizzling detail. Hold extreme tension for a moment, then stop cleanly for the next cue. An accent over an existing charge, not a second explosion; no long reverb. No voice, no music.
```

### 05 — moon_attract_gravity_beam_fire

Durée : **3 s**, Looping OFF. Absolu : **10–13 s**.

```text
3-second gravity beam discharge. Immediate hard electromagnetic crack and compact low-frequency recoil, followed by a dense focused tearing-energy jet, fluttering ionized edges and a brief stressed-metal ring. The beam body holds for two seconds, then collapses into a short dry suction tail. Powerful but leave room for a later lunar impact; mono-compatible, no sweeping stereo motion. No voice, no music.
```

### 06 — moon_attract_moon_gravitational_pull

Durée : **8 s**, Looping ON. Absolu : **11–27,7 s**, répétition contrôlée.

```text
8-second seamless loop of an immense gravitational field pulling a distant moon. Stable dark pressure drone, slow irregular tidal groans, granular mineral stress and faint electromagnetic flutter, with subtle internal movement but no overall crescendo. Restrained sub-bass, textured low mids, space for foreground events. No onset, ending, impact, periodic beat or obvious seam. Mono-compatible. No voice, no music.
```

### 07 — moon_attract_moon_approaching

Durée : **11,7 s**, Looping OFF. Absolu : **16–27,7 s**.

```text
11.7-second approach of a colossal rocky moon, one continuous escalation. Begin with distant mineral rumble and slow pressure tremors; grow into strained crust groans, rolling boulder friction and increasingly urgent air displacement. Increase apparent mass and proximity while reserving headroom. No collision or explosive ending; terminate at peak tension with a very short fade. Mono-compatible. No voice, no music.
```

### 08 — moon_attract_atmospheric_entry

Durée : **5,7 s**, Looping OFF. Absolu : **22–27,7 s**.

```text
5.7-second lunar atmospheric entry. Build a dense turbulent air roar with sizzling plasma edges, granular heat crackle and intermittent pressure buffets. Start already moving fast, then intensify friction and brightness without a tonal siren. Keep the low end restrained beneath separate gravity layers. No impact; end with a short clean cutoff at maximum speed. Detailed mono-compatible cinematic texture. No voice, no music.
```

### 09 — moon_attract_moon_fragmentation

Durée : **4,7 s**, Looping OFF. Absolu : **23–27,7 s**.

```text
4.7-second fragmentation of a massive rocky moon in flight. Stagger several deep crust fractures with sharp stone snaps, grinding slabs, brittle mineral splinters and trailing gravel shivers. Uneven events grow closer and denser, never a machine-gun rhythm. Clear transients above a restrained rubble body, no final ground collision or long tail. Finish cleanly under rising tension. Mono-compatible. No voice, no music.
```

### 10 — moon_attract_pre_impact_tension

Durée : **1,7 s**, Looping OFF. Absolu : **26–27,7 s**. Silence séparé ensuite, pas inclus dans ce fichier.

```text
1.7-second pre-impact tension accent. Compress a dark vacuum-pressure surge, strained nonmusical metal resonance and fine suspended grit into a narrowing, breathless crest. Intensify density rather than loudness; no collision transient, boom or payoff. End abruptly but click-free with no reverb spill, ready for a separate 0.3-second silence before impact. Mono-compatible. No voice, no music.
```

### 11 — moon_attract_moon_impact

Durée : **6 s**, Looping OFF. Absolu : **28–34 s**, contact immédiat à l'origine locale.

```text
6-second colossal moon-ground collision. At the very start, one dry brutal stone-and-metal crack over a controlled sub-bass pressure punch. Follow with crushing rock slabs, layered earth rupture, gritty collapsing mass and a dark decaying crater rumble. Strong transient contrast, readable low mids, no clipping or sustained bass wall. No separate sweeping shockwave or extended debris rain. Mono-compatible. No voice, no music.
```

### 12 — moon_attract_shockwave_expansion

Durée : **4 s**, Looping OFF. Absolu : **28,1–32,1 s**, soit 0,1 s après l'impact.

```text
4-second expanding shockwave, starting already in motion just after a giant impact. A compressed air slap opens into a broad rushing pressure front, rolling low-mid turbulence and dusty granular flutter, rapidly moving away and thinning into air. No second collision boom, deep sub hit or rubble crashes; emphasize displacement and receding scale. Mono-compatible, not dependent on stereo panning. No voice, no music.
```

### 13 — moon_attract_debris_rain

Durée : **8 s**, Looping OFF. Absolu : **30–38 s**.

```text
8-second aftermath debris rain. Scattered heavy rock landings, smaller sharp pebble ticks, gritty bounces and dusty slides fall in irregular overlapping clusters. Begin dense, then progressively thin to isolated settling stones. Vary material size and distance, keep each transient readable, restrained low end beneath the main impact tail. No new explosion, looping rhythm or long wash. Mono-compatible. No voice, no music.
```

### 14 — moon_attract_aftermath_ambience

Durée : **8 s**, Looping ON. Absolu : **32–40 s**, fondu externe **38–40 s**.

```text
8-second seamless ruined-crater ambience loop. Quiet dusty wind, diffuse low earth settling, occasional tiny mineral ticks and faint residual electrical fizz. Stable restrained texture with subtle nonperiodic movement, spacious but mono-compatible, well below the collision. No new impacts, crescendo, fade-in, fade-out or obvious seam; the playback system will fade it later. No voice, no music.
```

## Couches isolées optionnelles

Ces dix alternatives servent au montage des cinq composites ci-dessus. **Elles remplacent les bandes correspondantes du composite ; ne pas les additionner aveuglément**, sous peine de doubler le grave, les attaques et la réverbération. Conserver une seule couche dominante par bande, filtrer les recouvrements et ajuster les gains à l'écoute. Les mixer dans le fichier principal final avant import ; elles n'ajoutent aucun événement au manifeste et l'importeur ne les collecte pas.

Tous les blocs restent sous 450 caractères ; Looping OFF. Même origine locale et même durée que le composite, sauf indication contraire. Aligner manuellement les attaques entre couches ; les nombres écrits dans les prompts sont des intentions de montage.

### Charge — corps de pression isolé

Durée **5 s**, absolu **5–10 s**. Remplace le grave/corps de la charge.

```text
5-second isolated low-frequency gravity charge body. From local 0 to 4 seconds, a dark pressure hum grows denser and tighter with irregular sub pulses; hold the focused crest from 4 to 5 seconds. No bright crackle, metal clicks, firing transient or ending boom. Nonmusical, restrained bass with clear headroom; clean boundary for a separate discharge. Mono-compatible. No voice, no music.
```

### Charge — détails électriques isolés

Durée **5 s**, absolu **5–10 s**. Remplace le haut médium/aigu de la charge.

```text
5-second isolated gravity-charge electrical detail. Fine ionized fizz and irregular magnetic ticks accelerate from local 0 to 4 seconds, becoming a taut shimmering crackle for the final second. High-mid texture only, no bass drone, melody, metal deployment or firing crack. Leave gaps between early pulses and finish cleanly at 5 seconds. Mono-compatible. No voice, no music.
```

### Beam — attaque et recul isolés

Durée **0,5 s**, absolu **10–10,5 s**. Remplace uniquement l'attaque du beam de 3 s ; compléter à 3 s dans le composite.

```text
0.5-second isolated gravity-beam launch transient. At local zero, a hard electromagnetic crack with a compact controlled low-pressure recoil and tiny mechanical ring; decay quickly within half a second. No sustained jet, sweeping tail, secondary shot or lunar collision. Dry, punchy, ample headroom, precise mono-compatible onset. No voice, no music.
```

### Beam — jet isolé

Durée **3 s**, absolu **10–13 s**. Remplace le corps et la queue ; attaque du jet alignée à 10 s, sous le transient.

```text
3-second isolated sustained gravity-beam body. Start immediately with a dense focused midrange tearing jet and fluttering ionized edges; sustain through local 2 seconds, then collapse into dry suction by 3 seconds. No launch crack, sub-bass recoil, impact or mechanical lock. Fine moving texture, controlled harshness, no melody or stereo dependency. No voice, no music.
```

### Atmosphere — air isolé

Durée **5,7 s**, absolu **22–27,7 s**. Remplace le souffle large bande.

```text
5.7-second isolated atmospheric air-displacement layer. A fast dense turbulent roar with irregular pressure buffets grows in proximity and urgency, then ends cleanly at local 5.7 seconds. Low-mid and midrange air only; no plasma sizzle, rock fractures, bass impact or tonal siren. Controlled dynamics, short boundary fade, no reverberant spill into the following silence. Mono-compatible. No voice, no music.
```

### Atmosphere — plasma isolé

Durée **5,7 s**, absolu **22–27,7 s**. Remplace les détails thermiques aigus.

```text
5.7-second isolated plasma-friction detail for lunar entry. Fine searing fizz, granular heat crackle and intermittent bright ionized snaps intensify toward local 5.7 seconds. High-frequency texture only, no broadband wind body, sub drone, stone impact or melody. Keep transients fine and unsaturated; short clean cutoff with no tail into the pre-impact silence. Mono-compatible. No voice, no music.
```

### Impact — masse isolée

Durée **6 s**, absolu **28–34 s**. Remplace le grave et la queue de cratère.

```text
6-second isolated low-frequency lunar impact mass. At local zero, one compact sub-pressure punch with a dense low-mid earth rupture; decay through irregular deep crater settling until 6 seconds. No sharp collision crack, bright stone debris, air sweep or second hit. Preserve a clear transient and generous headroom, avoid a constant bass wall. Mono-compatible. No voice, no music.
```

### Impact — contact minéral isolé

Durée **6 s**, absolu **28–34 s**. Remplace le contact et les fractures médium ; contact à 28 s.

```text
6-second isolated mineral contact layer for a moon collision. At local zero, one dry brutal stone crack; follow with overlapping crushing slabs, brittle fragments and gritty structural collapse, decaying to sparse dust by 6 seconds. Midrange and high detail only, no sub boom, sweeping shockwave or extended falling-rock rain. Clear size contrast and unsmeared attack. Mono-compatible. No voice, no music.
```

### Shockwave — pression isolée

Durée **4 s**, absolu **28,1–32,1 s**. Remplace le front de pression médium/grave.

```text
4-second isolated expanding air-pressure front. Start at local zero with a compressed low-mid air slap, then rapidly broaden into rolling turbulent displacement and recede over four seconds. No collision transient, deep sub punch, bright dust hiss or rock crashes. Restrained pressure body, no tonal sweep; mono-compatible motion conveyed by envelope and texture. No voice, no music.
```

### Shockwave — poussière isolée

Durée **4 s**, absolu **28,1–32,1 s**. Remplace le souffle poussiéreux aigu.

```text
4-second isolated shockwave dust-and-air detail. A bright granular rush begins immediately, flutters like suspended grit in an outward pressure front, then thins and recedes to dry air by local 4 seconds. High-mid and high texture only, no bass pulse, impact crack, large rock landings or melody. Fine irregular detail with controlled harshness, mono-compatible. No voice, no music.
```

## Import local des fichiers fournis

Prérequis : Python 3 et `ffmpeg`/`ffprobe` sur PATH, avec encodeur `libvorbis`. Depuis la racine du workspace, fournir **explicitement** le dossier des exports :

```powershell
python scripts/import-moon-attract-sfx.py "C:\Audio\MoonAttract"
python scripts/import-moon-attract-sfx.py --check
```

Le dossier doit contenir exactement une source reconnue par nom principal, par exemple `moon_attract_cannon_summon.wav`. Les extensions acceptées sont MP3/WAV/OGG, indifférentes à la casse ; les noms de base sont exacts. Un fichier manquant ou deux formats pour le même cue bloquent l'import complet avant modification. Les fichiers non concernés, notamment les couches isolées, sont ignorés.

L'importeur suit la convention de `scripts/import-blackhole-sfx.py` : **mono, 44 100 Hz, OGG Vorbis qualité 5**, retrait du DC et normalisation de crête à 0,68 avant de très courts fondus anti-clic (entrée 5 ms, sortie 20 ms ; boucles 5 ms). Il tronque ou complète par du silence jusqu'à la durée contractuelle, sans modifier la vitesse. Contrairement au crossfade raccourcissant les boucles Black Hole, les boucles Moon Attract restent **8 s**. Une source trop courte conserve donc du silence final : vérifier le montage et la boucle à l'écoute avant livraison.

Les 14 conversions sont réalisées et contrôlées dans un dossier temporaire avant installation dans `src/main/resources/assets/stellar/sounds/`. Seules les listes `sounds` des 14 clés `cinematic.moon_attract_*` sont alors remplacées par `stellar:moon_attract_*` ; les autres entrées et les autres propriétés sont conservées. Réimporter remplace les 14 OGG du même nom. Une erreur disque pendant la copie finale n'est pas une transaction atomique multi-fichiers : corriger puis relancer depuis les sources conservées.

`--check` vérifie les 14 liaisons finales, le codec, le mono, la fréquence, la durée (tolérance inférieure à 25 ms), les niveaux, le DC et la discontinuité instantanée des boucles. Il échoue normalement tant que les secours sont actifs. Ce contrôle ne prouve pas qu'une boucle est imperceptible, que le mix est bon ni que la synchronisation en jeu est correcte. La normalisation indépendante n'est pas un mix : régler les gains de lecture pour réserver le sommet dynamique à l'impact, éviter l'empilement des graves et garder les textures de détail en retrait.

L'importeur n'accède jamais à ElevenLabs et ne synthétise aucun son. Aucun MP3/WAV/OGG final Moon Attract n'a été inventé ou installé avec cette documentation. Les coupures au tick 554, les 6 ticks de silence et le fondu final nécessitent une validation du lecteur en jeu à partir de la future intégration.
