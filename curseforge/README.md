# Publication CurseForge

## Contenu

| Fichier | Usage sur CurseForge |
| --- | --- |
| `art/logo-400.png` | Logo du projet (avatar carré) |
| `art/banner.png` | Première image de la description et image de galerie |
| `art/header-0x-*.png` | Titres de section de la description |
| `art/*.svg` | Versions vectorielles du pixel art, sans police à installer |
| `art/native/*.png` | Dessins à leur résolution d'origine, pour retouche pixel par pixel |
| `description.md` | Description (anglais + résumé français) |
| `changelog-1.1.0.md` | Changelog du fichier 1.1.0 |

`art/mod-logo.png` est copié dans `src/main/resources/assets/stellar/logo.png` : c'est le logo affiché dans la liste des mods Forge (`logoFile` de `mcmod.info`).

Régénérer toutes les images : `python scripts/generate-curseforge-art.py` (Python + Pillow ; `--svg-only` écrit seulement les SVG). Aucun navigateur requis.

## Style pixel art

Palette commune de 16 couleurs : bleu nuit, turquoise désaturé et ambre. Les lettres et les illustrations sont dessinées sur une grille, sans flou ni dégradé lissé. Les SVG contiennent des rectangles vectoriels, pas des images intégrées.

- Logo : grille de 64 × 64, agrandie ×8 pour le PNG de 512 × 512. La version CurseForge utilise un agrandissement ×6 avec une marge de 8 pixels pour atteindre 400 × 400 sans déformer les pixels.
- Bannière et titres de section : grilles de 375 × 125 et 375 × 35, agrandies ×4.
- Logo du mod : fond transparent, grille de 300 × 75 agrandie ×2.

Pour retoucher les PNG natifs, garder la palette et utiliser un agrandissement en « plus proche voisin », sans lissage. Le script reste la source de régénération : une nouvelle exécution remplace les exports et les PNG natifs.

## Licence

Licence retenue : **MIT**, copyright (c) 2026 Frabous. Le texte complet est dans [`../LICENSE`](../LICENSE) et la compilation l'inclut dans le JAR sous `META-INF/LICENSE`. Sélectionner **MIT License** dans les paramètres du projet CurseForge.

MIT autorise la réutilisation, la modification et la redistribution des contributions originales du projet, y compris commerciales, en conservant les mentions de copyright et de licence. Les dépendances et contenus tiers conservent leurs propres conditions ; `FORGE-MDK-LICENSE.txt` n'est pas remplacé.

**Sons :** tous générés par les scripts `scripts/generate-*-sfx.py` du dépôt. Les 38 anciennes prises ElevenLabs (offre gratuite, usage non commercial) ont été refaites et ne sont plus dans le JAR : les restrictions de cette offre ne s'appliquent plus au mod.

## Créer le projet

1. **Create Project** → Minecraft → Mods. Nom : `Beyond The Space`. Résumé (max. 150 caractères) :
   `Unofficial 1.12.2 remake inspired by The Shooting Star: orbital railgun, black hole, carrier ops, supernova and gravity inversion.`
2. Catégories : **Armor, Tools, and Weapons** (principale) et **Adventure and RPG**.
3. Logo : `art/logo-400.png`.
4. Licence : **MIT License** (texte dans `../LICENSE`).
5. Galerie : ajouter `art/banner.png`, puis des captures en jeu de chaque frappe.

## Description

1. Coller `description.md` dans l'éditeur en mode Markdown.
2. Envoyer `banner.png` et les six `header-*.png` dans l'éditeur ou la galerie, puis remplacer `IMG_BANNER` et `IMG_HEADER_01` à `IMG_HEADER_06` par leurs URL.

## Envoyer le fichier

1. Compiler : `.\gradlew.bat --no-daemon --console=plain build` (JDK 8).
2. Fichier : `build/libs/beyond-the-space-1.1.0.jar`.
3. Release type : **Release** ; Game version : **1.12.2** ; Mod loader : **Forge** ; Java : **Java 8**.
4. Environnement : **Client** et **Server** cochés ; aucune dépendance.
5. Changelog : `changelog-1.1.0.md` en Markdown.
