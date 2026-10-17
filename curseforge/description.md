![Beyond The Space](IMG_BANNER)

> **Unofficial 1.12.2 remake inspired by the popular [The Shooting Star](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo) mod.**
> Beyond The Space is an independent project. It is not affiliated with or endorsed by the authors of The Shooting Star, and it contains none of their code, textures or sounds: every model, effect and sound was rebuilt from scratch for Forge 1.12.2.

Call down orbital strikes from a handheld remote. Most attacks play a full space cinematic, then hit the world for real: railgun columns, a black hole that pulls everything into orbit, a destroyer that warps in, a supernova, and a local gravity inversion. **The terrain is never destroyed.**

*🇫🇷 Version française plus bas.*

![Features](IMG_HEADER_01)

- **5 orbital strikes**, each with its own sounds and server-side damage. Most play a cinematic; Kinetic Rain happens live in the world and Broadside Command puts you at the turrets.
- **SR-1 Remote**: a 3D remote held in first person; your thumb actually presses the button of each strike.
- **Holographic menu (H)**: animated cards, orbital previews and briefings, without pausing the game.
- **Skippable cinematics**: press **J** or **Esc** to leave the film; the strike keeps running on the server.
- **Terrain-safe**: no block destroyed or placed, no forced chunk loading.
- **No shader pack needed**: built-in selective bloom and space-time distortion.

![Attacks](IMG_HEADER_02)

| Key | Strike | What happens |
| --- | --- | --- |
| **K** | **SS-01 Orbital Railgun** | A continuous zoom-out from Earth to the galaxy, then a 200-block-radius beam column: 100 raw damage per second for 8.4 s. |
| **B** | **SS-02 Black Hole** | Pulls every loaded entity within 1000 blocks into tilted Keplerian orbits, with gravitational lensing and lethal spaghettification. |
| **M** | **SS-03 Carrier Ops** | A destroyer warps in and delivers a payload. Press **M** again to cycle: **A** Asteroid Puller, **B** Broadside Command (pilot 5 twin turrets for 30 s), **C** Kinetic Rain (96 tungsten rods in a spiral barrage). |
| **N** | **SS-04 Supernova** | A massive star collapses and explodes; the shockwave reaches the whole dimension. Sounds are muffled under a roof or underwater. |
| **G** | **SS-05 Gravity Inversion** | Gravity flips within 180 blocks: mobs, items and illusory terrain fragments rise, spin, then slam back down. |

![Controls](IMG_HEADER_03)

| Input | Action |
| --- | --- |
| **Right click** (aim at a block, max 256 blocks) | Fire the selected strike |
| **Sneak + right click** | Select the next strike |
| **K / B / M / N / G** | Select a strike directly |
| **H** | Holographic menu (left click: select, right click: briefing) |
| **J** | Skip the current cinematic, or toggle cinematics for the session |

All keys can be changed in **Options → Controls → Beyond The Space**.

![Installation](IMG_HEADER_04)

1. Install **Minecraft Forge 1.12.2** (14.23.5.2864 or newer) with **Java 8**.
2. Put the jar in the `mods` folder of **the server and every client**.
3. As an operator, take the **Beyond The Space Remote** from the creative tab, or run `/stellar give`.

The remote has no recipe. It and `/stellar` are reserved for operators (permission level 2); in singleplayer, enable cheats.

![Configuration](IMG_HEADER_05)

`config/stellar.cfg` is created on first launch.

| Option | Default | Effect |
| --- | --- | --- |
| `gameplay.targetRange` | `256` | Targeting range, checked on the server |
| `gameplay.cooldownTicks` | `600` | Cooldown between strikes (30 s) |
| `gameplay.blackholeLethal` | `true` | Black hole spaghettification damage |
| `gameplay.blackholeLethalPlayers` | `true` | Set to `false` to spare players |
| `performance.maxActive` | `2` | Simultaneous strikes per dimension |

Commands: `/stellar give`, `/stellar status`, `/stellar cancel`.

![FAQ](IMG_HEADER_06)

**Is this the official The Shooting Star for 1.12.2?**
No. It is an unofficial remake inspired by it, made from scratch.

**Will it grief my world?**
No. Strikes only damage living entities. Blocks are never broken or placed.

**Does it work with shader packs?**
It doesn't need one. With a shader pack active, some effects fall back to a simpler version.

**Can I use it in a modpack?**
Yes. The project's original contributions are released under the **MIT License**, copyright (c) 2026 Frabous. Keep the copyright and license notices when redistributing MIT-licensed material. Third-party content and dependencies retain their own terms.

---

## 🇫🇷 Version française

> **Reprise non officielle en 1.12.2, inspirée du populaire mod [The Shooting Star](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo).**
> Beyond The Space est un projet indépendant, sans lien avec les auteurs de The Shooting Star. Il ne contient aucun de leurs codes, textures ou sons : modèles, effets et sons ont été entièrement recréés pour Forge 1.12.2.

Appelez des frappes orbitales depuis une télécommande. La plupart des attaques jouent une cinématique spatiale, puis frappent réellement le monde. **Le terrain n'est jamais détruit.**

- **K – SS-01 Railgun orbital** : dézoom continu de la Terre à la galaxie, puis colonne de 200 blocs de rayon, 100 dégâts bruts par seconde pendant 8,4 s.
- **B – SS-02 Trou noir** : attire les entités chargées dans un rayon de 1000 blocs sur des orbites de Kepler, lentille gravitationnelle et spaghettification.
- **M – SS-03 Carrier Ops** : un destroyer arrive en warp. Appuyer encore sur **M** : **A** Asteroid Puller, **B** Broadside Command (5 tourelles pilotables pendant 30 s), **C** Kinetic Rain (96 tiges de tungstène).
- **N – SS-04 Supernova** : une étoile massive explose ; l'onde de choc touche toute la dimension.
- **G – SS-05 Inversion de gravité** : dans 180 blocs, entités et fragments de terrain s'élèvent, tournent puis s'effondrent.

**Installation** : Forge 1.12.2 (14.23.5.2864+), Java 8, le jar dans `mods` **sur le serveur et sur chaque client**. Télécommande dans l'onglet créatif ou `/stellar give` (opérateurs uniquement). **H** ouvre le menu holographique, **J** ou **Échap** passe la cinématique.

**Licence** : contributions originales sous **MIT**, copyright (c) 2026 Frabous. Utilisation en modpack, modification et redistribution autorisées en conservant les mentions de copyright et de licence. Les contenus tiers et les dépendances gardent leurs propres conditions.
