# Blackhole — sons ElevenLabs intégrés et prompts

> **Statut (17 octobre 2026) :** les sept sons ne viennent plus d'ElevenLabs. Ils sont synthétisés par `scripts/generate-blackhole-sfx.py`, avec les mêmes noms, durées, boucles et mixage. Ce document reste comme cahier des charges ; les sections sur l'archive MP3 et `import-blackhole-sfx.py` sont historiques.

## Rendu

Référence locale : `C:\Users\Frabous\Downloads\videoplayback (1).mp4`.
La vidéo dure environ 61 minutes ; les 90 premières secondes analysées montrent le même plan sans coupe détectée : ombre noire, disque ambré incliné, anneaux blanc chaud et image du disque courbée autour de l'ombre.

- Disque mince à rotation différentielle, filaments, température blanc chaud vers ambre et asymétrie de luminosité.
- Anneau de photons et arcs polaires représentant une approximation géométrique de l'image secondaire du disque. Ce n'est pas un calcul de relativité générale ni du ray tracing.
- Bloom sélectif du plasma ; ombre centrale non émissive.
- Panorama de 2 800 étoiles de luminosités différentes, bande galactique discrète et Terre texturée avec nuages et liseré atmosphérique.
- Travelling orbital local de 110 ticks (5,5 secondes à 20 TPS), déclenché à l'arrivée dans l'espace, avec raccord progressif à la vue du joueur. La caméra n'est pas ajoutée au monde et ne déplace pas le joueur sur le serveur.
- Bandes cinématiques, champ de vision plus posé ; déformation et traînées progressives à l'entrée. Le panorama est dégagé pendant le travelling.
- J désactive la présentation et restitue immédiatement la caméra ; les effets du monde restent visibles.

## Génération ElevenLabs

Utiliser **Sound Effects**, une génération par prompt. Régler la durée dans l'interface et activer **Looping** seulement pour les trois boucles. Les prompts sont en anglais et restent sous 450 caractères.

Durée actuellement documentée : 0,5 à 30 secondes. L'option de boucle est disponible avec `eleven_text_to_sound_v2`. Commencer avec l'influence par défaut, puis l'augmenter si le résultat s'éloigne de la description.

Les sept MP3 de `C:\Users\Frabous\Downloads\Black_Hole_Sound_Effects.zip` sont convertis et intégrés dans `src/main/resources/assets/stellar/sounds/`, puis embarqués dans le JAR. Lecture locale sans atténuation spatiale, catégorie **Players**. L'arrivée dans l'espace dépend de la position initiale du joueur et des TPS.

Les boucles utilisent un raccord croisé de 250 ms ; leur durée finale est donc réduite de 250 ms. Les transitoires gardent leur durée demandée, avec une fermeture sans clic. Les sept fichiers sont en **OGG Vorbis mono, 44,1 kHz**, avec crête normalisée avant encodage à 0,68.

| OGG intégré | Durée finale | Boucle | Déclenchement / mixage |
| --- | --- | --- | --- |
| `blackhole_ascent.ogg` | 3 s | Non | Début de l'attraction, à 60 ticks (3 s) |
| `blackhole_space_bed.ogg` | 7,75 s | Oui | Arrivée au-dessus de Y=300, volume maximal 0,12 |
| `blackhole_reveal.ogg` | 4 s | Non | Première arrivée dans l'espace, une seule fois |
| `blackhole_accretion.ogg` | 7,75 s | Oui | Présence du disque, volume maximal 0,28 |
| `blackhole_tidal.ogg` | 3,75 s | Oui | Tension progressive avec la déformation, volume maximal 0,24 |
| `blackhole_horizon.ogg` | 3 s | Non | Entrée visuelle ≥ 0,65, après le travelling, une seule fois |
| `blackhole_return.ogg` | 3,5 s | Non | Libération à 740 ticks, ou annulation serveur après arrivée dans l'espace |

À l'entrée, les trois boucles s'atténuent pour laisser ressortir le franchissement et le calme intérieur. Leur hauteur évolue avec la déformation et l'entrée : ambiance entre 0,94 et 1, disque entre 0,80 et 1, tension entre 0,84 et 1,08. Le lissage du moteur de mixage évite un changement instantané de hauteur. Elles s'éteignent avant la libération.

J arrête immédiatement tous les sons du blackhole et empêche leur reprise pour ce lancement. Décès, changement de monde et désactivation de la présentation arrêtent également le mixage. Une interface ouverte ou une sortie de la zone audible suspend les sources ; la reprise recrée les boucles sans rejouer les transitoires déjà déclenchés. Une fin de lancement pendant cette suspension reste silencieuse. Une correction d'horloge ne rejoue pas les transitoires déjà déclenchés.

### Correspondance avec l'archive

Les fichiers `01_Ascension_Vers_Espace.mp3`, `02_Ambiance_Spatiale_LOOP.mp3`, `03_Revelation_Blackhole.mp3`, `04_Disque_Accretion_LOOP.mp3`, `05_Tension_Gravitationnelle_LOOP.mp3`, `06_Franchissement_Horizon.mp3` et `07_Liberation_Retour_Sol.mp3` correspondent, dans cet ordre, aux sept lignes du tableau.

```powershell
# Réimporter une archive au même format, puis recompiler le mod.
python scripts/import-blackhole-sfx.py "C:\Users\Frabous\Downloads\Black_Hole_Sound_Effects.zip"

# Vérifier les OGG installés sans les modifier.
python scripts/import-blackhole-sfx.py --check
```

Le rapport d'import est `build/blackhole-audio/report.json` (durées, niveaux et SHA-256). Les crêtes décodées sont comprises entre 0,679 et 0,703 ; les écarts de raccord des trois boucles sont inférieurs à 0,0011. Ces mesures valident les assets techniquement ; elles ne constituent pas une évaluation subjective à l'écoute.

Les prompts ci-dessous conservent les durées de **génération** originales.

### 1. Ascension — 3 secondes

```text
A cinematic gravitational lift from the ground into orbit. Begin with a restrained deep pressure pulse, rise into smooth rushing air and a subtle accelerating resonance, then filter the air away into near-silence. Enormous scale, clean bass, detailed soft highs, continuous upward motion. No rocket engine, explosion, speech or music.
```

### 2. Espace — 8 secondes, Looping activé

```text
A seamless quiet cinematic deep-space ambience. An almost silent low-frequency resonance with sparse delicate shimmering overtones and slow, irregular distant pressure fluctuations. Spacious, mysterious and beautiful, with a stable low level and no obvious beginning or ending. Sound design only: no melody, beat, choir, speech, wind or explosions.
```

### 3. Révélation du trou noir — 4 secondes

```text
A single restrained cinematic reveal of an immense black hole. A deep, smooth gravitational resonance slowly blooms beneath a faint glassy shimmer, conveying awe and terrifying mass, then settles into a soft dark tail. Warm detailed midrange, controlled sub-bass, no sudden impact. Sound design only, no melody, choir, speech, music or explosion.
```

### 4. Disque d'accrétion — 8 secondes, Looping activé

```text
A seamless steady-state sound of an enormous black hole accretion disk. Dense low gravitational rumble beneath finely textured swirling plasma, soft turbulent hiss and slow irregular metallic resonances. Massive rotating motion, warm and ominous, with controlled bass and smooth highs. No crescendo, start, ending, rhythmic beat, speech or music.
```

### 5. Tension gravitationnelle — 4 secondes, Looping activé

```text
A seamless steady-state tidal gravitational strain. A deep stretched resonance with slowly bending metallic creaks, subtle fluttering pressure and a thin glassy vibration. Tense, dense and physically oppressive, but clean and controlled. Maintain constant intensity for game-controlled fades. No impact, crescendo, melody, speech, music or explosion.
```

### 6. Franchissement — 3 secondes

```text
A single cinematic crossing of a black hole event horizon. Begin immediately with tight vacuum-like suction, accelerate into a dense twisting pressure rush with stretched glassy resonances, peak in a brief soft low-frequency collapse, then rapidly filter down to near-silence. Terrifying scale, smooth dynamics. No explosion, gunshot, speech or music.
```

### 7. Retour — 3,5 secondes

```text
A single cinematic release from extreme gravity back to solid ground. A compressed dark resonance unwinds into a smooth descending airy whoosh, followed by one restrained low landing thump and a short warm decaying tail. Relief after immense pressure, precise and clean, no crash or debris. No repeated impacts, explosion, speech or music.
```

## Mixage et export

- L'ambiance spatiale reste faible : le calme fait ressortir la présence du trou noir.
- Superposer ambiance et disque ; faire monter la tension à l'approche, puis couper rapidement les hautes fréquences lors du franchissement.
- Éviter un sub-bass excessif et les boucles qui contiennent déjà une montée dramatique : réserver l'évolution du volume au jeu.
- Écouter plusieurs répétitions de chaque boucle pour vérifier les raccords.
- Conserver les originaux ElevenLabs. Pour Minecraft 1.12.2 : OGG Vorbis, 44,1 kHz, marge de crête −3 à −6 dBFS ; mono pour un son positionnel, stéréo possible pour une couche cinématique non positionnelle.

Documentation : [Sound Effects](https://elevenlabs.io/docs/eleven-creative/playground/sound-effects), [durées et boucles](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert).
