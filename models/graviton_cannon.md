# AG-03 Compact Graviton Cannon

Native Blockbench MCP model for **SS-03 Asteroide Attract**. Editable source:
`models/graviton_cannon.bbmodel`; builder: `scripts/build-graviton-cannon.js`.

## Design and scale

Gray sloped receiver, yellow identification strips, twin armored rails, recessed
rectangular muzzle, cyan coils, low chassis and four sliding landing pads. The
source contains **141 mesh elements / 846 quads / 3,384 vertices** in six groups:
`base`, `barrel`, `leg_0` through `leg_3`, with an embedded 64 × 64 palette.

Authoring scale is **16 model units per world block**. The receiver/muzzle span is
about **8.3 blocks**; barrel pivot is `(0,48,0)` and muzzle center `(96,48,0)` in the
source. In game the pivot is three blocks above the base, and the aperture extends
six blocks from it. This replaces the old cannon's 18-block pivot / 15.5-block barrel.
The pads move one block outward on each horizontal axis after touchdown. Runtime
telescoping links connect those moving pads to the chassis; they are procedural
parts in `MoonAttractModel.cannonBody`, rather than additional source meshes.

## Export

Build in an empty Generic Model through the Blockbench MCP, then export to the
source path. The builder is undoable and refuses a populated project.

```powershell
python scripts/export-graviton-cannon.py
```

The exporter validates palette UVs and materials using the existing source-mesh
exporter, and writes `assets/stellar/models/graviton_cannon.mesh`. AGC1 contains a
big-endian magic/count followed by XYZ floats, RGB integer, material byte and part
byte per vertex (18 bytes): hardware/emission 0/1; base/barrel/legs 0/1/2–5.

`GravitonCannonModel.append` applies scale, pad extension, barrel pitch/yaw and
recoil. Muzzle and beam calculations use the same pivot, length and recoil. Emissive
geometry participates in the existing selective bloom. The model is visual and
does not add a placed block or collision entity.

See [the sequence verification](../docs/asteroide-attract-verification.md) for real
Minecraft captures, the timeline, checks and artifact.
