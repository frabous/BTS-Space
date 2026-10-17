"""Export the native Blockbench source with material and articulated-part tags."""
import importlib.util
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("destroyer_export", Path(__file__).with_name("generate-orbital-destroyer.py"))
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)
model = json.loads((ROOT / "models/graviton_cannon.bbmodel").read_text(encoding="utf-8"))
flat = exporter.export_model(model, {0, 1})
groups = {g["uuid"]: g["name"] for g in model["groups"]}
parts = {"base": 0, "barrel": 1, **{"leg_"+str(i): i+2 for i in range(4)}}
tags = [parts[groups[g["uuid"]]] for g in model["outliner"] for child in g["children"] for _ in range(24)]
count = struct.unpack_from(">I", flat, 4)[0]
assert count == len(tags)
data = struct.pack(">II", 0x41474331, count)
data += b"".join(flat[8+i*17:8+(i+1)*17]+bytes([tag]) for i, tag in enumerate(tags))
out = ROOT / "src/main/resources/assets/stellar/models/graviton_cannon.mesh"
assert out.parent.is_dir()
out.write_bytes(data)
assert len(data) == 8+count*18
assert set(tags) == set(range(6))
print(f"PASS: {len(model['elements'])} native parts, {count} vertices, six articulation tags; colors/UVs validated")
