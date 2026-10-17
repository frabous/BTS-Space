# SES Legislator of Supremacy — reference destroyer

Reconstructed from the four user-provided images, using native cubes, meshes,
groups and an embedded palette in the live Blockbench MCP editor.

**Current integration:** Asteroide Attract now adds a runtime warp entrance and
animated ventral deployment cradle. The compact cannon is released onto the aimed
smoke beacon. See [the current verification](../docs/asteroide-attract-verification.md).
The source hull remains reusable; the checks/artifact below describe the preceding
ship-only reconstruction, before this delivery-sequence update.

## Editable asset

- `orbital_destroyer.bbmodel`: authoritative Blockbench Generic Model, format 5.0.
- **2,605 elements**: 730 cubes and 1,875 meshes, arranged in ten named groups.
- **15,630 quads / 62,520 runtime vertices**, below the renderer's 100,000-vertex limit.
- Bounds: **[-311, -75.373, -111.15]** to **[395, 55, 111.15]**.
- Dimensions: **706 × 130.373 × 222.3 units**; 44.125 × 8.148 × 13.894 blocks at 16 units/block.
- Axes: **+X bow, +Y dorsal, +Z starboard**. Cinematic transforms set apparent size.

The long parallel bow girders have sloping broadside armor, an inset center channel,
open frame ends, inner bracing, pipes, deck rails and small sensor probes. The stern
has four swept fins, four recessed drives, eight circular side-thruster assemblies,
stepped shoulder armor and a low command deck. The underside has a deep armored
keel and five twin-barrel defense batteries. Yellow registry lettering, recognition
stripes, grilles, fasteners and wear marks are editable geometry.

The reconstruction estimates dimensions and hidden surfaces from perspective images.
It does **not** establish an exact 1:1 match. Broad armor panels remain cleaner and
less weathered than the reference; the renderer uses baked vertex colors rather
than the reference game's detailed materials and lighting.

## Authoring and export

`scripts/build-reference-destroyer.js` builds the geometry **inside Blockbench**, in
an empty Generic Model project, using one native Undo transaction. Run it with the
Blockbench MCP JavaScript tool, then export the project to `models/orbital_destroyer.bbmodel`.
The script refuses to replace a populated project. It uses installed Arial for the
geometry lettering, so rebuilding on a different font environment can change it.

The Python command now exports the saved Blockbench source; it does not regenerate
or overwrite that source. Native mesh vertex keys and Blockbench 5 group metadata
are preserved. Palette swatches are flat-color face materials, not a painted atlas.

```powershell
python scripts/generate-orbital-destroyer.py
python scripts/preview-orbital-destroyer.py --output build/orbital-destroyer-reference-preview.png
```

The exporter rejects unbaked rotations, mesh origins, inflation, nonuniform face
colors and unsupported materials instead of silently exporting a different shape.
Keep transforms baked and retain `emit__` / `glass__` prefixes for runtime materials.

## Runtime contract

```java
OrbitalDestroyerModel.prepare();
OrbitalDestroyerModel.render(emissionOnly);
```

SOD1 stores a big-endian magic and vertex count, followed by XYZ floats, RGB integer
and material byte per vertex: 0 hardware, 1 emissive, 2 translucent glass. Glass uses
80/255 alpha. Positions and colors are verified independently against the saved
Blockbench project. The static hull renderer remains reusable; its cinematic caller
adds the animated cradle and synchronizes the separate cannon's release.

## Verification — 9 October 2026

- Native Blockbench views inspected: dorsal three-quarter, ventral, stern and bow.
- Source/runtime parity passed; maximum float error **0.000015245 units**.
- Geometry checks: finite/nondegenerate faces, UUID uniqueness, material tags,
  two bow girders, four fins, lettering and runtime vertex budget passed.
- Java 8 build, reobfuscation, physical-side check and **79 JUnit tests** passed.
- Packaged mesh equals the exported source mesh; artifact verification passed.
- Minecraft rendered the new ship with bloom and `glError=0`; the natural-end and
  skip/cancel driver completed. The full Python smoke report remains **failed**:
  its contact-sheet checker expected 1280 × 720 but received 1920 × 1111. This is a
  capture-size failure, not a successful end-to-end smoke result. No check was relaxed.

Evidence:
- [Four-view mesh preview](../build/orbital-destroyer-reference-preview.png)
- [Hero preview](../build/orbital-destroyer-reference-preview-hero.jpg)
- [Actual Minecraft frame](../build/smoke-moon/20261009-175856-16520/screenshots/sequence-0-moon-16ticks.png)
- [Smoke report and logs](../build/smoke-moon/20261009-175856-16520/moon-report.json)

Artifact: `build/libs/beyond-the-space-1.0.0.jar`, 5,776,320 bytes.
SHA-256: `f81795e26e98f722c7a91e4651147774cf505f2790abdb1946d33fd71c4f8a46`.

### Self-evaluation

| Axis | /5 | Evidence / remaining improvement |
| --- | --- | --- |
| Accuracy | 4 | Native MCP construction, independent parity and actual game frame; exact dimensions are inferred. |
| Completeness | 3 | Editable model and integrated JAR delivered; exact 1:1 materials and hidden surfaces are not reproduced. |
| Clarity | 4 | Current source and export workflow documented; historical verification pages retain older models. |
| Actionability | 4 | Ready JAR and saved source; the screenshot-size check needs a controlled 1280 × 720 rerun. |
| Conciseness | 4 | Geometry builder and small exporter; detailed lettering accounts for 1,116 elements in its group. |

Mean: **3.8/5**. Highest-impact improvements: match material wear and panel proportions
against higher-resolution orthographic references; rerun capture QA at its required
window dimensions. A user requesting literal 1:1 fidelity may reasonably judge the
visual match more strictly than the technical integration.

### Verification failure diagnosis

The client driver logged `STELLAR_MOON_SMOKE_PASS`, but `contact_sheet()` then raised
at `scripts/smoke-moon.py:109` because the framebuffer dimensions differed. Reading
the actual PNG confirmed 1920 × 1111; the reason the window size changed was not
established. Recovery was limited to inspecting the original image and preserving
the failed report. Do not mark this run fully passed or resize its evidence to pass
the assertion. For future runs, control the window size before starting the driver.
