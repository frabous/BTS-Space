"""Verify the distributable archive rather than trusting only the Java compiler."""
import hashlib
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
artifact = root / "build/libs/beyond-the-space-1.1.0.jar"
with zipfile.ZipFile(artifact) as jar:
    entries = set(jar.namelist())
    metadata = json.loads(jar.read("mcmod.info"))[0]
    assert metadata["modid"] == "stellar" and metadata["mcversion"] == "1.12.2"
    assert metadata["version"] == "1.1.0"
    assert metadata["name"] == "Beyond The Space"
    assert b"Implementation-Title: Beyond The Space" in jar.read("META-INF/MANIFEST.MF")
    classes = [name for name in entries if name.endswith(".class")]
    for name in classes:
        assert name.startswith("fr/frabous/stellar/"), "Unexpected bundled library: " + name
        assert "SmokeMod" not in name, "Development driver leaked into the artifact"
        major = struct.unpack(">H", jar.read(name)[6:8])[0]
        assert major == 52, (name, major)
    assert b"func_77659_a" in jar.read("fr/frabous/stellar/common/ItemStellarRemote.class"), "Item was not reobfuscated"
    for name in entries:
        if name.endswith(".json") or name.endswith(".mcmeta"):
            json.loads(jar.read(name))
    sounds = json.loads(jar.read("assets/stellar/sounds.json"))
    for event in sounds.values():
        for sound in event["sounds"]:
            name = sound if isinstance(sound, str) else sound["name"]
            namespace, path = name.split(":", 1) if ":" in name else ("stellar", name)
            if namespace == "stellar":
                assert "assets/stellar/sounds/" + path + ".ogg" in entries, "Missing sound: " + name
    for helper in ("SkillMenu", "RemoteMenuLayout", "RemoteMenuAudio"):
        assert f"fr/frabous/stellar/client/{helper}.class" in entries, "Missing remote UI class: " + helper
    for cue in ("open", "hover", "select", "details", "close"):
        assert sounds[f"remote.{cue}"]["sounds"] == [f"stellar:remote_{cue}"]
        name = f"assets/stellar/sounds/remote_{cue}.ogg"
        assert jar.read(name) == (root / "src/main/resources" / name).read_bytes(), "Stale remote sound: " + cue
    ss03 = [event[len("cinematic."):] for event in sounds
            if event.startswith(("cinematic.carrier_", "cinematic.lance_", "cinematic.tether_", "cinematic.asteroid_"))]
    assert len(ss03) == 17, "SS-03 synthesized sounds are not all registered"
    for cue in ss03:
        name = f"assets/stellar/sounds/{cue}.ogg"
        assert jar.read(name) == (root / "src/main/resources" / name).read_bytes(), "Stale SS-03 sound: " + cue
    mesh = jar.read("assets/stellar/models/orbital_railgun.mesh")
    magic, vertices = struct.unpack(">ii", mesh[:8])
    assert magic == 0x53524731 and len(mesh) == 8 + vertices * 17
    destroyer = jar.read("assets/stellar/models/orbital_destroyer.mesh")
    magic, destroyer_vertices = struct.unpack(">II", destroyer[:8])
    assert magic == 0x534F4431 and len(destroyer) == 8 + destroyer_vertices * 17
    assert destroyer == (root / "src/main/resources/assets/stellar/models/orbital_destroyer.mesh").read_bytes()
    assert "fr/frabous/stellar/client/OrbitalDestroyerModel.class" in entries
    assert {destroyer[offset] for offset in range(24,len(destroyer),17)} == {0,1,2}
    assert "assets/stellar/models/graviton_cannon.mesh" not in entries, "Legacy AG-03 mesh is still packaged"
    for carrier in ("CarrierFlight", "CarrierRenderer", "RigMesh", "GravitonLanceModel", "AsteroidModel",
                    "AsteroidPullerScene", "AsteroidPullerCamera", "AsteroidPullerCinema", "AsteroidPullerSoundtrack"):
        assert f"fr/frabous/stellar/client/{carrier}.class" in entries, "Missing SS-03 carrier class: " + carrier
    for shared in ("AttackFamily", "AsteroidPullerTiming", "AsteroidTrajectory"):
        assert f"fr/frabous/stellar/api/{shared}.class" in entries, "Missing SS-03 contract class: " + shared
    assert b"SS-03 Carrier Ops" in jar.read("assets/stellar/lang/en_us.lang")
    for name, dimensions in {"regolith": (1024, 512), "glow": (256, 256),
                             "smoke": (256, 256), "fire": (256, 256)}.items():
        texture = jar.read(f"assets/stellar/textures/cinema/moon_{name}.png")
        assert texture[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", texture[16:24]) == dimensions
        assert texture == (root / f"src/main/resources/assets/stellar/textures/cinema/moon_{name}.png").read_bytes()
    print(f"PASS: {len(classes)} Java 8 classes, reobfuscated item, valid JSON, sound files and {vertices} railgun vertices.")
    print("PASS: Holographic remote classes and all five original UI sounds are packaged; no smoke drivers.")
    print("PASS: Textured Moon surface and three soft VFX atlases are packaged at their declared dimensions.")
    print(f"PASS: Destroyer {destroyer_vertices} vertices, procedural AG-07 Graviton Lance and SS-03 carrier family classes.")

total = failed = skipped = 0
for report in (root / "build/test-results/test").glob("TEST-*.xml"):
    suite = ET.parse(report).getroot()
    total += int(suite.get("tests", 0))
    failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    skipped += int(suite.get("skipped", 0))
assert total > 0 and failed == 0 and skipped == 0
print(f"PASS: {total} JUnit tests; {failed} failures; {skipped} skipped.")
print(f"Artifact: {artifact}")
print(f"Size: {artifact.stat().st_size} bytes")
print(f"SHA-256: {hashlib.sha256(artifact.read_bytes()).hexdigest()}")
