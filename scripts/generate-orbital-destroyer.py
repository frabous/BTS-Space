"""Export the MCP-authored Blockbench destroyer to the existing SOD1 runtime format.

The .bbmodel is authoritative. Rebuild geometry inside Blockbench with
build-reference-destroyer.js, export the project, then run this script.
"""
import base64
import hashlib
import io
import json
import math
import struct
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "models/orbital_destroyer.bbmodel"
MESH = ROOT / "src/main/resources/assets/stellar/models/orbital_destroyer.mesh"
FACES = {"north": (0, 3, 2, 1), "south": (4, 5, 6, 7),
         "down": (0, 1, 5, 4), "up": (3, 7, 6, 2),
         "west": (0, 4, 7, 3), "east": (1, 2, 6, 5)}
# Rendered at runtime as articulated turrets (DestroyerBatteryModel) on the mounts authored here;
# BroadsideBatteriesTest keeps the Java ring centres in sync with this group.
ARTICULATED = {"07_ventral_defense_batteries"}


def export_model(model, expected_tags=frozenset({0, 1, 2})):
    elements = {e["uuid"]: e for e in model["elements"]}
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    assert len(elements) == len(model["elements"]), "Duplicate element UUID"
    texture = Image.open(io.BytesIO(base64.b64decode(model["textures"][0]["source"].split(",")[1]))).convert("RGBA")
    assert texture.size == (model["resolution"]["width"], model["resolution"]["height"])
    vertices, visited = [], set()

    def visit(nodes):
        for node in nodes:
            if isinstance(node, dict):
                g = groups.get(node["uuid"], node)
                assert not any(g.get("rotation", [0, 0, 0])), "Bake group rotations before export"
                if g.get("export", True) and g.get("name") not in ARTICULATED:
                    visit(node["children"])
                continue
            e = elements[node]
            assert node not in visited, "Repeated outliner element"
            visited.add(node)
            if not e.get("export", True):
                continue
            assert not any(e.get("rotation", [0, 0, 0])), "Bake element rotations before export"
            assert e.get("inflate", 0) == 0, "Bake cube inflation before export"
            tag = 1 if e["name"].startswith("emit__") else 2 if e["name"].startswith("glass__") else 0
            if e["type"] == "cube":
                x, y, z = e["from"]
                X, Y, Z = e["to"]
                assert x < X and y < Y and z < Z, e["name"]
                corners = [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
            else:
                assert e["type"] == "mesh" and not any(e["origin"]), "Bake mesh origin before export"
            for name, indices in FACES.items():
                face = e["faces"][name]
                assert face["texture"] == 0 and face["texture"] is not False
                if e["type"] == "cube":
                    points = [corners[i] for i in indices]
                    a, b, c, d = face["uv"]
                    uvs = [(a,b),(c,b),(c,d),(a,d)]
                else:
                    assert len(face["vertices"]) == 4
                    points = [e["vertices"][v] for v in face["vertices"]]
                    uvs = [face["uv"][v] for v in face["vertices"]]
                assert all(math.isfinite(v) for p in points for v in p)
                lo = [math.floor(min(p[i] for p in uvs)) for i in (0,1)]
                hi = [math.floor(max(p[i] for p in uvs)) for i in (0,1)]
                assert 0 <= lo[0] <= hi[0] < texture.width and 0 <= lo[1] <= hi[1] < texture.height
                samples = {texture.getpixel((x,y)) for x in range(lo[0],hi[0]+1) for y in range(lo[1],hi[1]+1)}
                assert len(samples) == 1, "SOD1 requires flat-color face UVs: " + e["name"]
                r, g, b, a = samples.pop()
                assert a == (80 if tag == 2 else 255), "Material alpha/tag mismatch: " + e["name"]
                vertices.extend((*p, r << 16 | g << 8 | b, tag) for p in points)

    visit(model["outliner"])
    assert 0 < len(vertices) <= 100000 and len(vertices) % 4 == 0
    assert set(v[4] for v in vertices) == expected_tags
    return struct.pack(">II", 0x534F4431, len(vertices)) + b"".join(struct.pack(">fffIB", *v) for v in vertices)


def main():
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    data = export_model(model)
    assert MESH.parent.is_dir()
    MESH.write_bytes(data)
    print(json.dumps({"elements": len(model["elements"]), "vertices": (len(data)-8)//17,
                      "mesh_sha256": hashlib.sha256(data).hexdigest()}, indent=2))


if __name__ == "__main__":
    main()
