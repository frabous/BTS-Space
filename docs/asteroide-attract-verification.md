# Asteroide Attract — compact cannon and warp deployment

## Delivered behavior

The server freezes the **aimed loaded block** as both the smoke beacon and cannon
deployment point. The visual effect is shared by clients receiving the launch state;
it is not attached to the caster's later look direction. Camera skip leaves the
server attack active; cancellation/end removes its deterministic visual layers.

| Time at 20 TPS | Action |
| --- | --- |
| 0–1.6 s | Amber smoke and small ground beacon; initial shot looks at the marked block. |
| 1.6–2.9 s | Carrier exits a blue warp aperture. A clip plane hides hull geometry still behind the aperture. |
| 2.9–4 s | Carrier stops; ventral cradle opens and lowers the compact cannon 18 world units. |
| 4–7 s | Continuous descent from the same release point, thermal trails and retro-thrusters. |
| 5.7 s | Short orbital/world camera transition before touchdown. |
| 7–8 s | Landing dust, four pads slide outward and barrel aligns with the celestial target. |
| 8–10 s | Cyan charge; maximum charge at 9 s. |
| 10 s | Barrel recoil and beam from the actual compact aperture. |
| 11–40 s | Existing celestial pull, atmospheric entry, impact and cleanup sequence. |

Visible names are now **Asteroide Attract** in the menu, details, controls, selection
messages, cinematic HUD and server status. Wire ID 5, enum/resource keys, sound
resource names and saved remote selection stay compatible. The rename does not
replace the pre-existing cratered celestial mesh with an irregular asteroid.

## Model

The [new cannon](../models/graviton_cannon.md) was created in the live Blockbench MCP:
141 editable elements, six groups, 3,384 exported vertices. It uses the destroyer's
gray/yellow/cyan design. Runtime linkage geometry joins the four movable pads.
The carrier's animated deployment cradle is implemented in the runtime renderer.

## Final verification

- Java 8 build/reobfuscation and client/server dependency check: **PASS**.
- **80 JUnit tests, zero failures or skipped tests**. Coverage includes extraction,
  monotone descent, carrier stop before release, camera framing, compact muzzle
  alignment, stable network identity and visible rename.
- Native-source export checks: flat-color UVs, material/part tags and vertex counts.
- Archive verification: cannon and destroyer meshes equal their source resources;
  new renderer included, smoke drivers excluded, Java 8 classes and assets valid.
- Final connected Minecraft run: **PASS**, 47 captures at **1280 × 720**. Natural
  completion, local skip preserving the launch, server cancellation, restored
  camera/HUD and stopped sounds verified; all captured `glError` values are zero.
- Visually inspected: smoke, warp, extraction/release, deployed cannon and menu.

Evidence: [`build/smoke-moon/20261009-182418-36572/`](../build/smoke-moon/20261009-182418-36572/).

- [Smoke beacon](../build/smoke-moon/20261009-182418-36572/screenshots/sequence-0-moon-16ticks.png)
- [Warp exit](../build/smoke-moon/20261009-182418-36572/screenshots/sequence-0-moon-48ticks.png)
- [Release](../build/smoke-moon/20261009-182418-36572/screenshots/sequence-0-moon-84ticks.png)
- [Deployed cannon](../build/smoke-moon/20261009-182418-36572/screenshots/sequence-0-moon-180ticks.png)
- [Full contact sheet](../build/smoke-moon/20261009-182418-36572/moon-contact-sheet.jpg)
- [Report](../build/smoke-moon/20261009-182418-36572/moon-report.json)

JAR: `build/libs/beyond-the-space-1.0.0.jar`, **5,784,885 bytes**.
SHA-256: `e68ed205d27b08b808d388c47ec4c9d423eb7af0777c2735e2792f2e4e75ff2a`.

The tests use a disposable flat-world development server. Other terrain, shader
packs and continuous frame-rate targets are not established by these captures.

## Corrections and failure diagnosis

First capture review revealed the initial camera inside the player's head, oversized
cradle clamps hiding the cannon, and detached-looking pads. The camera now starts
above/in front of the player's original eye position; clamps are narrow and pad
links extend with the feet. A subsequent run at a resized 1920 × 1111 framebuffer
missed the firing capture deadline (211.4 ticks versus requested 202). The smoke
driver now disables window resizing; final timing/dimension checks passed without
relaxing either assertion. This establishes a successful controlled run, not proof
that resizing was the sole cause of that missed frame.

## Self-evaluation

| Axis | /5 | Evidence / improvement |
| --- | --- | --- |
| Accuracy | 4 | Build, tests, native model and real frames verified; arbitrary terrain not visually tested. |
| Completeness | 4 | Compact replacement, target smoke, warp/release and rename delivered; source previews omit runtime telescoping links/cradle. |
| Clarity | 4 | Timeline and proof paths documented; technical keys intentionally retain the historical name. |
| Actionability | 4 | Built JAR and editable model available; final modpack/shader combination still needs its own check. |
| Conciseness | 4 | Reused atlas/export logic and removed old cannon geometry; delivery logic spans model, camera and scene as before. |

Mean: **4.0/5**. Useful follow-ups: inspect uneven-terrain landings and the intended
modpack; bring the procedural moving links into the authoring preview if future
model editing needs exact deployed-source inspection. The requested behavior is
supported by the model, tests and real screenshots, rather than preview-only claims.
