# Stellar orbital railgun

Editable source: `orbital_railgun.bbmodel` (Blockbench **Generic Model**, embedded palette).
The model uses eight segmented armored rails, an open beam chamber, silver collars,
parallel white rods, four open barrel trusses, and four swept solar-like stabilizers
with white tip beacons. The small rear command tower includes illuminated windows
and an antenna. Geometry was interpreted from the supplied 11.200 s and 11.750 s
reference frames.

## Renderer contract

```java
OrbitalRailgunModel.render(double ageTicks, double charge, double firing);
```

- Local longitudinal axis: **X**, firing toward **+X**.
- Rear: **X = -65**; muzzle: **X = 58.95**.
- Barrel exterior radius: approximately **17** including the open trusses.
- Central corridor: radius **6**, open from **X = -39** through the muzzle.
- Full model bounds, including stabilizers: **[-65, -33.070, -33.070]** to
  **[58.95, 33.070, 33.070]**. Rear antenna reaches **Y = 29.5**.
- `charge` and `firing` are clamped to **[0, 1]**. `ageTicks` animates chamber arcs
  and charge bolts. The renderer supplies no camera transform or outgoing beam.
- Forward rail parts open radially during charge by **0.55 × charge** times their
  local cuboid-center radius. This exposes the bore in the close-up. The bounds
  above describe the full model; the barrel widens within the stabilizer envelope.
- Hardware is drawn in one opaque batch; charge strips and chamber effects in
  one additive batch. White markers are full-bright. Face shading is baked into
  the palette, so the renderer needs no texture or world lighting.
- The current matrix is used without modification. Changed GL attributes and
  the incoming active texture unit are restored in a `finally` block. All GL
  calls must be made on the client render thread, outside an active tessellation.

The binary mesh is loaded once from
`src/main/resources/assets/stellar/models/orbital_railgun.mesh`. Header: big-endian
`SRG1` magic and vertex count, followed by quad vertices containing three floats,
one RGB integer, and one emission byte (0 = hardware, 1 = white, 2 = charge red).
The mesh contains **26,904 vertices / 6,726 quads**. Geometry and per-face palette
colors match the editable file; animated charge effects are runtime-only.

## Reproduction and verification

```powershell
python scripts/generate-orbital-railgun.py
python scripts/preview-orbital-railgun.py
python scripts/preview-orbital-railgun.py --output "existing-directory/railgun.png"
```

Generation requires Python 3 and no additional packages. Preview/verification
requires NumPy and Pillow. The deterministic generator owns only its explicitly
named `.bbmodel` and `.mesh` outputs; edits to the `.bbmodel` should be reflected
in the generator before regenerating.

Verification checks element IDs, dimensions, embedded PNG, vertex count, file
length, transformed vertex/palette parity, finite coordinates, and axial bore
clearance. The optional four-view software contact sheet is a geometry preview;
it does not validate Minecraft playback, additive glow, or shader-pack behavior.
