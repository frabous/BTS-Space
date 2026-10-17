# Reference reconstruction brief — SES destroyer

## Target

Rework the orbital ship against the four screenshots supplied by the user, using
the Blockbench MCP. Images 1 and 2 establish the SES Legislator of Supremacy design;
image 3 clarifies the dorsal layout and image 4 the bow-on silhouette.

## Shape hierarchy

1. Long, low twin-bow silhouette with an inset longitudinal channel.
2. Broad sloping outer armor, thin upper rails, open rectangular bow terminations.
3. Wider stepped aft shoulders and swept upper/lower stabilizers.
4. Deep ventral machinery housing and small defense batteries.
5. Low command deck with fine antennas, rather than a tall tower.
6. Cool gray armor, dark mechanical recesses, restrained yellow registry/stripes,
   and small circular cyan side-thrusters.

## Implementation

Build named native Blockbench cubes/meshes in separate functional groups. Use an
embedded palette, with baked directional colors compatible with the existing SOD1
renderer. Encode lettering and small markings as geometry so they survive its
untextured runtime. Preserve the ship's +X forward convention and export the saved
`.bbmodel` as the sole runtime geometry source.

## Acceptance and evidence limits

Inspect dorsal, ventral, stern and bow views in Blockbench. Verify saved-source /
runtime position and color parity, material tags, nondegenerate geometry and the
100,000-vertex budget. Build the mod and inspect its real orbital frame.

The references do not supply orthographic measurements or complete hidden surfaces.
The result is a reconstruction, not a verified exact 1:1 asset. Material weathering
and fine mechanical contours remain simplified. See `orbital_destroyer.md` for
counts, checks, screenshots and the capture-size QA failure.
